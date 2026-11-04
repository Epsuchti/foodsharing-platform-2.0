package network.foodsharing.pickup.service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SPI implemented by the automation module (pickup must not depend on it, {@code ModulesTest}): the store rules that
 * block a person's sign-up, checked before the sign-up is stored (decision 165 S2). A no-op default
 * ({@link PickupIntegrations}) blocks nothing. Callers skip store managers (pre-confirmed sign-ups).
 */
public interface PickupRuleGate {

    /**
     * The person's own data behind a block (see {@code PolicyBlockParams} in the contract); {@code windowMode} is the
     * {@code LinkedStoreWindowMode} name.
     */
    record BlockParams(Instant lastPickupAt, Instant nextPossibleAt, Integer gapDays, List<UUID> storeIds,
                       String windowMode, Integer before, Integer after, Instant from, Instant to,
                       Integer monthsAgo, Integer monthsRequired, UUID fspId) {

        public BlockParams {
            storeIds = storeIds == null ? List.of() : List.copyOf(storeIds);
        }
    }

    /**
     * One blocking rule: {@code rule} is the {@code PolicyRule} name, {@code text} the rendered sentence in the
     * person's language (audits only; clients render their own text from the params).
     */
    record RuleBlock(String rule, BlockParams params, String text) {
    }

    /**
     * The blocks per pickup for {@code userId} in the store (pickups without blocks are missing). Evaluates every
     * pickup with one set of queries.
     */
    Map<Instant, List<RuleBlock>> blocks(UUID storeId, UUID userId, Collection<Instant> pickupAts);

    /**
     * Batch variant over several stores (pickup options): the blocks per store and pickup; stores without active rules
     * cost no evaluation.
     */
    default Map<UUID, Map<Instant, List<RuleBlock>>> blocks(UUID userId, Map<UUID, ? extends Collection<Instant>> pickupsByStore) {
        Map<UUID, Map<Instant, List<RuleBlock>>> result = new java.util.HashMap<>();
        pickupsByStore.forEach((storeId, pickupAts) -> {
            Map<Instant, List<RuleBlock>> blocks = blocks(storeId, userId, pickupAts);
            if (!blocks.isEmpty()) {
                result.put(storeId, blocks);
            }
        });
        return result;
    }

    /**
     * A sign-up was refused because of {@code blocks}: the implementation records the attempt once the current
     * transaction has ended (the refusal rolls it back).
     */
    default void refused(UUID storeId, UUID userId, Instant pickupAt, List<RuleBlock> blocks) {
    }
}
