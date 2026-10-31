package network.foodsharing.automation.service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.automation.domain.AutomationAction;
import network.foodsharing.automation.domain.PolicyRule;
import network.foodsharing.automation.domain.RequestDecision;
import network.foodsharing.automation.domain.RequestPolicy;
import network.foodsharing.automation.domain.RequestPolicyAudit;
import network.foodsharing.automation.repository.RequestPolicyAuditRepository;
import network.foodsharing.automation.repository.RequestPolicyRepository;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.domain.StoreApplicationReceivedEvent;
import network.foodsharing.store.service.StoreService;
import network.foodsharing.store.service.StoreTeamService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Request policy (AU2): accepts a team application automatically; with the distance rule the haversine distance
 * between the applicant's home and the store must not exceed the maximum (missing coordinates: SKIPPED, beyond the
 * maximum: declined with the reference text). Runs on {@link StoreApplicationReceivedEvent} and in the
 * {@code request-automation} job (at most {@code max-request-approvals-per-run} actions per run).
 */
@Service
public class RequestPolicyRunner {

    public static final String ACTOR = "request-policy";

    private static final Logger log = LoggerFactory.getLogger(RequestPolicyRunner.class);
    private static final Set<RequestDecision> REPEATABLE = EnumSet.of(RequestDecision.SKIPPED,
            RequestDecision.WOULD_APPROVE, RequestDecision.WOULD_DECLINE);

    private final RequestPolicyRepository policies;
    private final RequestPolicyAuditRepository audits;
    private final StoreService stores;
    private final StoreTeamService team;
    private final UserService users;
    private final HistoryService history;
    private final MessageSource messages;
    private final AppProperties properties;
    private final TransactionTemplate tx;

    public RequestPolicyRunner(RequestPolicyRepository policies, RequestPolicyAuditRepository audits,
                               StoreService stores, StoreTeamService team, UserService users, HistoryService history,
                               MessageSource messages, AppProperties properties,
                               PlatformTransactionManager transactionManager) {
        this.policies = policies;
        this.audits = audits;
        this.stores = stores;
        this.team = team;
        this.users = users;
        this.history = history;
        this.messages = messages;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onApplication(StoreApplicationReceivedEvent event) {
        if (!properties.getAutomation().isEnabled()) {
            return;
        }
        Optional<RequestPolicy> policy = tx.execute(status -> policies.findByStoreId(event.storeId())
                .filter(RequestPolicy::isEnabled));
        if (policy != null && policy.isPresent()) {
            process(policy.get(), event.userId(), false, false, new ArrayList<>());
        }
    }

    public PickupPolicyRunner.RunResult runStore(UUID storeId, boolean forcedDryRun) {
        Optional<RequestPolicy> policy = tx.execute(status -> policies.findByStoreId(storeId));
        return run(policy == null || policy.isEmpty() ? List.of() : List.of(policy.get()), forcedDryRun);
    }

    public PickupPolicyRunner.RunResult runAll() {
        List<RequestPolicy> enabled = tx.execute(status -> policies.findByEnabledTrue());
        return run(enabled == null ? List.of() : enabled, false);
    }

    private PickupPolicyRunner.RunResult run(List<RequestPolicy> list, boolean forcedDryRun) {
        List<String> runMessages = new ArrayList<>();
        boolean dryRun = forcedDryRun || properties.getAutomation().isDryRun();
        if (!properties.getAutomation().isEnabled()) {
            runMessages.add("Request automation skipped: global automation is disabled.");
            return new PickupPolicyRunner.RunResult(0, 0, 0, 0, 0, 0, true, runMessages);
        }
        int max = Math.max(0, properties.getAutomation().getMaxRequestApprovalsPerRun());
        int evaluated = 0;
        int approved = 0;
        int declined = 0;
        int skipped = 0;
        int failed = 0;
        outer:
        for (RequestPolicy policy : list) {
            dryRun = dryRun || policy.isDryRun();
            List<UUID> applicants = tx.execute(status -> team.applicantIds(policy.getStoreId()));
            for (UUID userId : applicants == null ? List.<UUID>of() : applicants) {
                if (approved + declined >= max) {
                    runMessages.add("Request automation limit reached (" + max + ").");
                    break outer;
                }
                evaluated++;
                switch (process(policy, userId, forcedDryRun, true, runMessages)) {
                    case APPROVED, WOULD_APPROVE -> approved++;
                    case DECLINED, WOULD_DECLINE -> declined++;
                    case FAILED -> failed++;
                    default -> skipped++;
                }
            }
        }
        return new PickupPolicyRunner.RunResult(evaluated, approved, declined, skipped, 0, failed, dryRun,
                runMessages);
    }

    private RequestDecision process(RequestPolicy policy, UUID userId, boolean forcedDryRun, boolean dedupe,
                                    List<String> runMessages) {
        boolean dryRun = forcedDryRun || policy.isDryRun() || properties.getAutomation().isDryRun();
        try {
            return tx.execute(status -> decideAndAct(policy, userId, dryRun, dedupe));
        } catch (RuntimeException ex) {
            log.error("Request policy failed for store {} user {}", policy.getStoreId(), userId, ex);
            runMessages.add(String.valueOf(ex.getMessage()));
            try {
                tx.executeWithoutResult(status -> audit(policy, userId, dryRun, RequestDecision.FAILED, null, null,
                        PickupPolicyRunner.truncate(String.valueOf(ex.getMessage()), 2000), false));
            } catch (RuntimeException auditFailure) {
                log.error("Could not write the FAILED request audit", auditFailure);
            }
            return RequestDecision.FAILED;
        }
    }

    private RequestDecision decideAndAct(RequestPolicy policy, UUID userId, boolean dryRun, boolean dedupe) {
        Store store = stores.require(policy.getStoreId());
        User user = users.require(userId);
        PolicyTexts t = new PolicyTexts(messages, Locale.forLanguageTag(user.getPreferredLanguage() == null ? "de"
                : user.getPreferredLanguage().getValue()));
        if (policy.isDistanceRuleEnabled()) {
            Double distance = distanceKm(user.getLatitude(), user.getLongitude(), store.getLatitude(),
                    store.getLongitude());
            if (distance == null) {
                audit(policy, userId, dryRun, RequestDecision.SKIPPED, PolicyRule.NO_ADDRESS,
                        t.text("request.no-address"), null, dedupe);
                return RequestDecision.SKIPPED;
            }
            if (distance > policy.getMaximumDistanceKm()) {
                String message = t.text("request.distance", formatDistance(distance),
                        formatDistance(policy.getMaximumDistanceKm()));
                if (!dryRun && !team.declineByAutomation(store.getId(), userId, ACTOR, message)) {
                    return RequestDecision.SKIPPED;
                }
                RequestDecision result = dryRun ? RequestDecision.WOULD_DECLINE : RequestDecision.DECLINED;
                audit(policy, userId, dryRun, result, PolicyRule.DISTANCE, message, null, dedupe);
                return result;
            }
        }
        if (!dryRun && !team.acceptByAutomation(store.getId(), userId, ACTOR)) {
            return RequestDecision.SKIPPED;
        }
        RequestDecision result = dryRun ? RequestDecision.WOULD_APPROVE : RequestDecision.APPROVED;
        audit(policy, userId, dryRun, result, PolicyRule.ALL_RULES_PASSED, t.text("request.approved"), null, dedupe);
        return result;
    }

    private void audit(RequestPolicy policy, UUID userId, boolean dryRun, RequestDecision status, PolicyRule rule,
                       String message, String error, boolean dedupe) {
        if (dedupe && REPEATABLE.contains(status) && audits.findFirstByStoreIdAndUserIdOrderByCreatedAtDescIdDesc(
                policy.getStoreId(), userId).map(previous -> previous.getStatus() == status).orElse(false)) {
            return;
        }
        UUID regionId = stores.require(policy.getStoreId()).getRegionId();
        RequestPolicyAudit audit = new RequestPolicyAudit();
        audit.setPolicyId(policy.getId());
        audit.setStoreId(policy.getStoreId());
        audit.setRegionId(regionId);
        audit.setUserId(userId);
        audit.setDryRun(dryRun);
        audit.setStatus(status);
        audit.setReason(rule == null ? null : rule.name());
        audit.setMessage(PickupPolicyRunner.truncate(message, 4000));
        audit.setError(error);
        audits.save(audit);
        history.record(history.event(AutomationAction.REQUEST_DECISION, policy.getId()).actorAutomation(ACTOR)
                .secondarySubject(userId).region(regionId).metadata("storeId", policy.getStoreId().toString())
                .metadata("decision", status.name()).build());
    }

    /** Haversine distance in km, or null when a coordinate is missing. */
    static Double distanceKm(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return null;
        }
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    static String formatDistance(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
