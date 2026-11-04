package network.foodsharing.automation.service;

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
 * only), and from the {@code automation-evaluate} job (deferred cases). Each sign-up is decided in its
 * own transaction; a failure writes a FAILED audit. No dry run (decision 165): audits are written with
 * {@code dry_run = false}. DEFERRED writes no audit; on the job path a repeated SKIPPED decision is not audited again.
 * Rule violations are refused before the sign-up is stored ({@link PickupRuleGateImpl}, decision 165 S2), so the
 * after-commit path only confirms or skips; a decline it would make (a race between two sign-ups) is left to the job,
 * which keeps the decline path for sign-ups that existed before the rules were switched on.
 */
@Service
public class PickupPolicyRunner {

    public static final String ACTOR = "pickup-policy";

    private static final Logger log = LoggerFactory.getLogger(PickupPolicyRunner.class);
    private static final Set<PickupPolicyDecision> REPEATABLE = EnumSet.of(PickupPolicyDecision.SKIPPED);

    /** Counts of one run. */
    public record RunResult(int evaluated, int confirmed, int declined, int skipped, int deferred, int failed,
                            List<String> messages) {
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
                event.signedUpAt()), false, true, new ArrayList<>());
    }

    /** Job path: every unconfirmed future sign-up of every enabled policy. */
    public RunResult runAll() {
        List<PickupPolicy> enabled = tx.execute(status -> policies.findByEnabledTrue());
        return run(enabled == null ? List.of() : enabled);
    }

    private RunResult run(List<PickupPolicy> policyList) {
        List<String> messages = new ArrayList<>();
        if (!properties.getAutomation().isEnabled()) {
            messages.add("Pickup automation skipped: global automation is disabled.");
            return new RunResult(0, 0, 0, 0, 0, 0, messages);
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
            PickupPolicyDecision decision = process(policy, signup, true, false, messages);
            counts[decision.ordinal()]++;
        }
        return new RunResult(evaluated, counts[PickupPolicyDecision.CONFIRMED.ordinal()],
                counts[PickupPolicyDecision.DECLINED.ordinal()], counts[PickupPolicyDecision.SKIPPED.ordinal()],
                counts[PickupPolicyDecision.DEFERRED.ordinal()], counts[PickupPolicyDecision.FAILED.ordinal()],
                messages);
    }

    private PickupPolicyDecision process(PickupPolicy policy, Signup signup, boolean dedupe, boolean confirmOnly,
                                         List<String> messages) {
        try {
            return tx.execute(status -> decideAndAct(policy, signup, dedupe, confirmOnly));
        } catch (RuntimeException ex) {
            log.error("Pickup policy failed for store {} pickupAt={} user={}", signup.storeId(), signup.pickupAt(),
                    signup.userId(), ex);
            messages.add(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            try {
                tx.executeWithoutResult(status -> audit(policy, signup, PickupPolicyDecision.FAILED, List.of(), null,
                        truncate(String.valueOf(ex.getMessage()), 2000), false));
            } catch (RuntimeException auditFailure) {
                log.error("Could not write the FAILED audit", auditFailure);
            }
            return PickupPolicyDecision.FAILED;
        }
    }

    private PickupPolicyDecision decideAndAct(PickupPolicy policy, Signup signup, boolean dedupe,
                                              boolean confirmOnly) {
        Spec spec = data.spec(policy);
        Decision decision = evaluator.evaluate(spec, signup.pickupAt(), signup.userId(), data);
        if (decision.deferred()) {
            log.debug("Pickup policy deferred store={} pickupAt={} user={}: {}", signup.storeId(),
                    signup.pickupAt(), signup.userId(), decision.reasonText());
            return PickupPolicyDecision.DEFERRED;
        }
        if (decision.skipped()) {
            audit(policy, signup, PickupPolicyDecision.SKIPPED, decision.reasons(), null, null, dedupe);
            return PickupPolicyDecision.SKIPPED;
        }
        if (decision.allowed()) {
            pickups.confirmByAutomation(signup.storeId(), signup.pickupAt(), signup.userId(), ACTOR,
                    decision.userMessage());
            audit(policy, signup, PickupPolicyDecision.CONFIRMED, decision.reasons(), decision.userMessage(), null,
                    dedupe);
            return PickupPolicyDecision.CONFIRMED;
        }
        if (confirmOnly) {
            log.debug("Pickup policy leaves a declined sign-up to the job store={} pickupAt={} user={}",
                    signup.storeId(), signup.pickupAt(), signup.userId());
            return PickupPolicyDecision.DEFERRED;
        }
        String footer = evaluator.texts(data.user(signup.userId()).locale()).text("decline-footer");
        String message = decision.reasonText() + "\n\n" + footer;
        pickups.removeByAutomation(signup.storeId(), signup.pickupAt(), signup.userId(), ACTOR,
                truncate(message, 4000));
        audit(policy, signup, PickupPolicyDecision.DECLINED, decision.reasons(),
                decision.userMessage() != null ? decision.userMessage() : message, null, dedupe);
        return PickupPolicyDecision.DECLINED;
    }

    private void audit(PickupPolicy policy, Signup signup, PickupPolicyDecision decision,
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
        audit.setDryRun(false);
        audit.setDecision(decision);
        audit.setReasons(truncate(json.writeValueAsString(storedReasons(reasons)), 32600));
        audit.setUserMessage(truncate(userMessage, 4000));
        audit.setError(error);
        audits.save(audit);
        history.record(history.event(AutomationAction.PICKUP_DECISION, policy.getId()).actorAutomation(ACTOR)
                .secondarySubject(signup.userId()).region(regionId).metadata("storeId", signup.storeId().toString())
                .metadata("pickupAt", signup.pickupAt().toString()).metadata("decision", decision.name())
                .build());
    }

    /** How a reason is stored in an audit: rule and text (the params serve the UI only). */
    record StoredReason(network.foodsharing.automation.domain.PolicyRule rule, String text) {
    }

    static List<StoredReason> storedReasons(List<Reason> reasons) {
        return reasons.stream().map(reason -> new StoredReason(reason.rule(), reason.text())).toList();
    }

    static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
