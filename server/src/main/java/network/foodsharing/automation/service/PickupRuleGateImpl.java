package network.foodsharing.automation.service;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.automation.domain.PickupLinkPending;
import network.foodsharing.automation.domain.PickupPolicy;
import network.foodsharing.automation.domain.PickupPolicyAudit;
import network.foodsharing.automation.domain.PickupPolicyDecision;
import network.foodsharing.automation.domain.PolicyRule;
import network.foodsharing.automation.repository.PickupLinkPendingRepository;
import network.foodsharing.automation.repository.PickupPolicyAuditRepository;
import network.foodsharing.automation.repository.PickupPolicyRepository;
import network.foodsharing.automation.service.PickupPolicyEvaluator.Decision;
import network.foodsharing.automation.service.PickupPolicyEvaluator.Params;
import network.foodsharing.automation.service.PickupPolicyEvaluator.Reason;
import network.foodsharing.automation.service.PickupPolicyEvaluator.Spec;
import network.foodsharing.config.AppProperties;
import network.foodsharing.pickup.service.PickupRuleGate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * The pickup policy as a gate before the sign-up is stored (decision 165 S2): the same {@link PickupPolicyEvaluator}
 * as the runner, without audits, over a {@link CachingPolicyData} so a whole pickup list costs one set of queries. A
 * pickup is blocked when the evaluation declines it; skipped days (excluded date, holiday) and the last two days before a
 * pickup are not blocked (the runner decides those after the sign-up). A missing linked-store pickup blocks unless it
 * is booked in the same request ({@link #check}, decision 165 S3). A
 * refused sign-up gets a DECLINED audit once its transaction has rolled back (audit only, no history: no state
 * changed).
 */
@Service
@Primary
public class PickupRuleGateImpl implements PickupRuleGate {

    private static final Logger log = LoggerFactory.getLogger(PickupRuleGateImpl.class);

    private final PickupPolicyRepository policies;
    private final PickupPolicyAuditRepository audits;
    private final PickupPolicyEvaluator evaluator;
    private final PickupPolicyData data;
    private final AppProperties properties;
    private final ObjectMapper json;
    private final TransactionTemplate readTx;
    private final TransactionTemplate writeTx;
    private final PickupLinkPendingRepository linkPending;

    public PickupRuleGateImpl(PickupPolicyRepository policies, PickupPolicyAuditRepository audits,
                              PickupPolicyEvaluator evaluator, PickupPolicyData data, AppProperties properties,
                              ObjectMapper json, PlatformTransactionManager transactionManager,
                              PickupLinkPendingRepository linkPending) {
        this.linkPending = linkPending;
        this.policies = policies;
        this.audits = audits;
        this.evaluator = evaluator;
        this.data = data;
        this.properties = properties;
        this.json = json;
        this.readTx = new TransactionTemplate(transactionManager);
        this.writeTx = new TransactionTemplate(transactionManager);
        this.writeTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public Map<Instant, List<RuleBlock>> blocks(UUID storeId, UUID userId, Collection<Instant> pickupAts) {
        if (pickupAts.isEmpty() || !properties.getAutomation().isEnabled()) {
            return Map.of();
        }
        Map<Instant, List<RuleBlock>> result = readTx.execute(status -> policies.findByStoreId(storeId)
                .filter(PickupPolicy::isEnabled).map(policy -> evaluate(policy, userId, pickupAts)).orElse(Map.of()));
        return result == null ? Map.of() : result;
    }

    @Override
    public Map<UUID, Map<Instant, List<RuleBlock>>> blocks(UUID userId,
                                                          Map<UUID, ? extends Collection<Instant>> pickupsByStore) {
        if (pickupsByStore.isEmpty() || !properties.getAutomation().isEnabled()) {
            return Map.of();
        }
        Map<UUID, Map<Instant, List<RuleBlock>>> result = readTx.execute(status -> {
            Map<UUID, Map<Instant, List<RuleBlock>>> byStore = new HashMap<>();
            for (PickupPolicy policy : policies.findByStoreIdIn(pickupsByStore.keySet())) {
                if (!policy.isEnabled()) {
                    continue;
                }
                Map<Instant, List<RuleBlock>> blocks = evaluate(policy, userId,
                        pickupsByStore.get(policy.getStoreId()));
                if (!blocks.isEmpty()) {
                    byStore.put(policy.getStoreId(), blocks);
                }
            }
            return byStore;
        });
        return result == null ? Map.of() : result;
    }

    @Override
    public List<RuleBlock> check(UUID storeId, UUID userId, Instant pickupAt, Collection<Planned> planned) {
        return blocks(storeId, userId, List.of(pickupAt), planned).getOrDefault(pickupAt, List.of());
    }

    /** {@link #blocks(UUID, UUID, Collection)} with {@code planned} pickups counted as booked (decision 165 S3). */
    Map<Instant, List<RuleBlock>> blocks(UUID storeId, UUID userId, Collection<Instant> pickupAts,
                                         Collection<Planned> planned) {
        if (pickupAts.isEmpty() || !properties.getAutomation().isEnabled()) {
            return Map.of();
        }
        Map<Instant, List<RuleBlock>> result = readTx.execute(status -> policies.findByStoreId(storeId)
                .filter(PickupPolicy::isEnabled).map(policy -> evaluate(policy, userId, pickupAts, planned))
                .orElse(Map.of()));
        return result == null ? Map.of() : result;
    }

    @Override
    public Map<Instant, Map<UUID, Instant>> linkMissing(UUID storeId, Instant from, Instant to) {
        Map<Instant, Map<UUID, Instant>> result = readTx.execute(status -> {
            Map<Instant, Map<UUID, Instant>> byPickup = new HashMap<>();
            for (PickupLinkPending row : linkPending.findByStoreIdAndPickupAtGreaterThanEqualAndPickupAtLessThan(
                    storeId, from, to)) {
                byPickup.computeIfAbsent(row.getPickupAt(), at -> new HashMap<>()).put(row.getUserId(),
                        data.extendDeadline(row.getCreatedAt(), row.getDeadlineAt()));
            }
            return byPickup;
        });
        return result == null ? Map.of() : result;
    }

    private Map<Instant, List<RuleBlock>> evaluate(PickupPolicy policy, UUID userId, Collection<Instant> pickupAts) {
        return evaluate(policy, userId, pickupAts, List.of());
    }

    private Map<Instant, List<RuleBlock>> evaluate(PickupPolicy policy, UUID userId, Collection<Instant> pickupAts,
                                                   Collection<Planned> planned) {
        Spec spec = data.spec(policy);
        Instant signedUpAt = data.now();
        CachingPolicyData cached = new CachingPolicyData(data, planned.stream()
                .filter(p -> !p.storeId().equals(policy.getStoreId()) || !pickupAts.contains(p.pickupAt()))
                .map(p -> new PickupPolicyEvaluator.Signup(p.storeId(), userId, p.pickupAt(), false, signedUpAt))
                .toList());
        Instant now = cached.now();
        List<Instant> future = pickupAts.stream().filter(at -> at.isAfter(now)).distinct().sorted().toList();
        if (future.isEmpty()) {
            return Map.of();
        }
        if (spec.experienceRuleEnabled()) {
            cached.primeStoreSignups(spec.storeId(), future);
            List<UUID> people = new java.util.ArrayList<>(cached.signups(spec.storeId(), future.getFirst(),
                    future.getLast().plusMillis(1)).stream().map(PickupPolicyEvaluator.Signup::userId).toList());
            people.add(userId);
            cached.primeExperience(spec.storeId(), people.stream().distinct().toList());
        }
        Map<Instant, List<RuleBlock>> result = new HashMap<>();
        for (Instant pickupAt : future) {
            List<RuleBlock> blocks = blocksOf(evaluator.evaluate(spec, pickupAt, userId, cached));
            if (!blocks.isEmpty()) {
                result.put(pickupAt, blocks);
            }
        }
        return result;
    }

    /** The reasons of a decline, without those that allow (cleaning reminder, new user); nothing otherwise. */
    static List<RuleBlock> blocksOf(Decision decision) {
        if (decision.allowed() || decision.skipped() || decision.deferred()) {
            return List.of();
        }
        return decision.reasons().stream().filter(reason -> !PickupPolicyEvaluator.ALLOWED.contains(reason.rule()))
                .map(PickupRuleGateImpl::toBlock).toList();
    }

    private static RuleBlock toBlock(Reason reason) {
        Params p = reason.params();
        return new RuleBlock(reason.rule().name(), new BlockParams(p.lastPickupAt(), p.nextPossibleAt(), p.gapDays(),
                p.storeIds(), p.windowMode() == null ? null : p.windowMode().name(), p.before(), p.after(), p.from(),
                p.to(), p.monthsAgo(), p.monthsRequired(), p.fspId()), reason.text());
    }

    @Override
    public void refused(UUID storeId, UUID userId, Instant pickupAt, List<RuleBlock> blocks) {
        Runnable write = () -> {
            try {
                writeTx.executeWithoutResult(status -> audit(storeId, userId, pickupAt, blocks));
            } catch (RuntimeException ex) {
                log.error("Could not write the audit of a refused sign-up store={} pickupAt={} user={}", storeId,
                        pickupAt, userId, ex);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // the refusal rolls the sign-up's transaction back (and it holds the store lock): write afterwards
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    write.run();
                }
            });
        } else {
            write.run();
        }
    }

    private void audit(UUID storeId, UUID userId, Instant pickupAt, List<RuleBlock> blocks) {
        Optional<PickupPolicy> policy = policies.findByStoreId(storeId);
        if (policy.isEmpty()) {
            return;
        }
        String reasons = json.writeValueAsString(blocks.stream()
                .map(block -> new PickupPolicyRunner.StoredReason(PolicyRule.valueOf(block.rule()), block.text()))
                .collect(Collectors.toList()));
        PickupPolicyAudit audit = new PickupPolicyAudit();
        audit.setPolicyId(policy.get().getId());
        audit.setStoreId(storeId);
        audit.setRegionId(data.regionId(storeId));
        audit.setUserId(userId);
        audit.setPickupAt(pickupAt);
        audit.setDryRun(false);
        audit.setDecision(PickupPolicyDecision.DECLINED);
        audit.setReasons(PickupPolicyRunner.truncate(reasons, 32600));
        audits.save(audit);
    }
}
