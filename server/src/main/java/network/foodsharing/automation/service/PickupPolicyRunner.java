package network.foodsharing.automation.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.automation.domain.AutomationAction;
import network.foodsharing.automation.domain.PickupPolicy;
import network.foodsharing.automation.domain.PickupPolicyAudit;
import network.foodsharing.automation.domain.PickupPolicyDecision;
import network.foodsharing.automation.repository.PickupPolicyAuditRepository;
import network.foodsharing.automation.repository.PickupPolicyRepository;
import network.foodsharing.automation.service.PickupPolicyEvaluator.Decision;
import network.foodsharing.automation.service.PickupPolicyEvaluator.Reason;
import network.foodsharing.automation.service.PickupPolicyEvaluator.Signup;
import network.foodsharing.automation.service.PickupPolicyEvaluator.Spec;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.pickup.domain.PickupEvents;
import network.foodsharing.pickup.service.PickupAutomationQueries;
import network.foodsharing.pickup.service.PickupSignupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Applies pickup policies (A24): right after a sign-up commits ({@link PickupEvents.PickupSignedUpEvent}, unconfirmed
 * only), from the {@code automation-evaluate} job (deferred cases) and from "run now". Each sign-up is decided in its
 * own transaction; a failure writes a FAILED audit. {@code effectiveDryRun = global || forced || policy.dryRun}.
 * DEFERRED writes no audit; on the job path a repeated SKIPPED / WOULD_* decision is not audited again.
 */
@Service
public class PickupPolicyRunner {

    public static final String ACTOR = "pickup-policy";

    private static final Logger log = LoggerFactory.getLogger(PickupPolicyRunner.class);
    private static final Set<PickupPolicyDecision> REPEATABLE = EnumSet.of(PickupPolicyDecision.SKIPPED,
            PickupPolicyDecision.WOULD_CONFIRM, PickupPolicyDecision.WOULD_DECLINE);

    /** Counts of one run. */
    public record RunResult(int evaluated, int confirmed, int declined, int skipped, int deferred, int failed,
                            boolean dryRun, List<String> messages) {
    }

    private final PickupPolicyRepository policies;
    private final PickupPolicyAuditRepository audits;
    private final PickupPolicyEvaluator evaluator;
    private final PickupPolicyData data;
    private final PickupSignupService pickups;
    private final PickupAutomationQueries queries;
    private final HistoryService history;
    private final AppProperties properties;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public PickupPolicyRunner(PickupPolicyRepository policies, PickupPolicyAuditRepository audits,
                              PickupPolicyEvaluator evaluator, PickupPolicyData data, PickupSignupService pickups,
                              PickupAutomationQueries queries, HistoryService history, AppProperties properties,
                              ObjectMapper json, PlatformTransactionManager transactionManager) {
        this.policies = policies;
        this.audits = audits;
        this.evaluator = evaluator;
        this.data = data;
        this.pickups = pickups;
        this.queries = queries;
        this.history = history;
        this.properties = properties;
        this.json = json;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Instant evaluation of a new unconfirmed sign-up (decision 33: after commit, own transaction). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSignedUp(PickupEvents.PickupSignedUpEvent event) {
        if (event.confirmed() || !properties.getAutomation().isEnabled()) {
            return;
        }
        Optional<PickupPolicy> policy = tx.execute(status -> policies.findByStoreId(event.storeId())
                .filter(PickupPolicy::isEnabled));
        if (policy == null || policy.isEmpty()) {
            return;
        }
        process(policy.get(), new Signup(event.storeId(), event.userId(), event.pickupAt(), false,
                event.signedUpAt()), false, false, new ArrayList<>());
    }

    /** "Run now" for one store (no permission check here). */
    public RunResult runStore(UUID storeId, boolean forcedDryRun) {
        Optional<PickupPolicy> policy = tx.execute(status -> policies.findByStoreId(storeId));
        if (policy == null || policy.isEmpty()) {
            return new RunResult(0, 0, 0, 0, 0, 0, forcedDryRun, List.of());
        }
        return run(List.of(policy.get()), forcedDryRun);
    }

    /** Job path: every unconfirmed future sign-up of every enabled policy. */
    public RunResult runAll() {
        List<PickupPolicy> enabled = tx.execute(status -> policies.findByEnabledTrue());
        return run(enabled == null ? List.of() : enabled, false);
    }

    private RunResult run(List<PickupPolicy> policyList, boolean forcedDryRun) {
        boolean dryRun = forcedDryRun || properties.getAutomation().isDryRun();
        List<String> messages = new ArrayList<>();
        if (!properties.getAutomation().isEnabled()) {
            messages.add("Pickup automation skipped: global automation is disabled.");
            return new RunResult(0, 0, 0, 0, 0, 0, true, messages);
        }
        Map<UUID, PickupPolicy> byStore = policyList.stream()
                .collect(Collectors.toMap(PickupPolicy::getStoreId, Function.identity(), (a, b) -> a));
        List<Signup> open = tx.execute(status -> queries.unconfirmedFuture(byStore.keySet(), data.now()).stream()
                .map(PickupPolicyData::signup).toList());
        int[] counts = new int[PickupPolicyDecision.values().length];
        int evaluated = 0;
        for (Signup signup : open == null ? List.<Signup>of() : open) {
            PickupPolicy policy = byStore.get(signup.storeId());
            evaluated++;
            PickupPolicyDecision decision = process(policy, signup, forcedDryRun, true, messages);
            counts[decision.ordinal()]++;
            dryRun = dryRun || policy.isDryRun();
        }
        return new RunResult(evaluated,
                counts[PickupPolicyDecision.CONFIRMED.ordinal()] + counts[PickupPolicyDecision.WOULD_CONFIRM.ordinal()],
                counts[PickupPolicyDecision.DECLINED.ordinal()] + counts[PickupPolicyDecision.WOULD_DECLINE.ordinal()],
                counts[PickupPolicyDecision.SKIPPED.ordinal()], counts[PickupPolicyDecision.DEFERRED.ordinal()],
                counts[PickupPolicyDecision.FAILED.ordinal()], dryRun, messages);
    }

    private PickupPolicyDecision process(PickupPolicy policy, Signup signup, boolean forcedDryRun, boolean dedupe,
                                         List<String> messages) {
        try {
            return tx.execute(status -> decideAndAct(policy, signup, forcedDryRun, dedupe));
        } catch (RuntimeException ex) {
            log.error("Pickup policy failed for store {} pickupAt={} user={}", signup.storeId(), signup.pickupAt(),
                    signup.userId(), ex);
            messages.add(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            try {
                tx.executeWithoutResult(status -> audit(policy, signup,
                        forcedDryRun || policy.isDryRun() || properties.getAutomation().isDryRun(),
                        PickupPolicyDecision.FAILED, List.of(), null, truncate(String.valueOf(ex.getMessage()), 2000),
                        false));
            } catch (RuntimeException auditFailure) {
                log.error("Could not write the FAILED audit", auditFailure);
            }
            return PickupPolicyDecision.FAILED;
        }
    }

    private PickupPolicyDecision decideAndAct(PickupPolicy policy, Signup signup, boolean forcedDryRun,
                                              boolean dedupe) {
        boolean dryRun = forcedDryRun || policy.isDryRun() || properties.getAutomation().isDryRun();
        Spec spec = data.spec(policy);
        Decision decision = evaluator.evaluate(spec, signup.pickupAt(), signup.userId(), data);
        if (decision.deferred()) {
            log.debug("Pickup policy deferred store={} pickupAt={} user={}: {}", signup.storeId(),
                    signup.pickupAt(), signup.userId(), decision.reasonText());
            return PickupPolicyDecision.DEFERRED;
        }
        if (decision.skipped()) {
            audit(policy, signup, dryRun, PickupPolicyDecision.SKIPPED, decision.reasons(), null, null, dedupe);
            return PickupPolicyDecision.SKIPPED;
        }
        if (decision.allowed()) {
            if (!dryRun) {
                pickups.confirmByAutomation(signup.storeId(), signup.pickupAt(), signup.userId(), ACTOR,
                        decision.userMessage());
            }
            PickupPolicyDecision result = dryRun ? PickupPolicyDecision.WOULD_CONFIRM : PickupPolicyDecision.CONFIRMED;
            audit(policy, signup, dryRun, result, decision.reasons(), decision.userMessage(), null, dedupe);
            return result;
        }
        String footer = evaluator.texts(data.user(signup.userId()).locale()).text("decline-footer");
        String message = decision.reasonText() + "\n\n" + footer;
        if (!dryRun) {
            pickups.removeByAutomation(signup.storeId(), signup.pickupAt(), signup.userId(), ACTOR,
                    truncate(message, 4000));
        }
        PickupPolicyDecision result = dryRun ? PickupPolicyDecision.WOULD_DECLINE : PickupPolicyDecision.DECLINED;
        audit(policy, signup, dryRun, result, decision.reasons(),
                decision.userMessage() != null ? decision.userMessage() : message, null, dedupe);
        return result;
    }

    private void audit(PickupPolicy policy, Signup signup, boolean dryRun, PickupPolicyDecision decision,
                       List<Reason> reasons, String userMessage, String error, boolean dedupe) {
        if (dedupe && REPEATABLE.contains(decision) && audits.findFirstByStoreIdAndUserIdAndPickupAtOrderByCreatedAtDescIdDesc(
                signup.storeId(), signup.userId(), signup.pickupAt())
                .map(previous -> previous.getDecision() == decision).orElse(false)) {
            return;
        }
        UUID regionId = data.regionId(signup.storeId());
        PickupPolicyAudit audit = new PickupPolicyAudit();
        audit.setPolicyId(policy.getId());
        audit.setStoreId(signup.storeId());
        audit.setRegionId(regionId);
        audit.setUserId(signup.userId());
        audit.setPickupAt(signup.pickupAt());
        audit.setDryRun(dryRun);
        audit.setDecision(decision);
        audit.setReasons(truncate(json.writeValueAsString(reasons), 32600));
        audit.setUserMessage(truncate(userMessage, 4000));
        audit.setError(error);
        audits.save(audit);
        history.record(history.event(AutomationAction.PICKUP_DECISION, policy.getId()).actorAutomation(ACTOR)
                .secondarySubject(signup.userId()).region(regionId).metadata("storeId", signup.storeId().toString())
                .metadata("pickupAt", signup.pickupAt().toString()).metadata("decision", decision.name())
                .build());
    }

    /** Evaluates without acting (preview endpoint). */
    public Decision preview(PickupPolicy policy, Instant pickupAt, UUID userId) {
        return evaluator.evaluate(data.spec(policy), pickupAt, userId, data);
    }

    static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
