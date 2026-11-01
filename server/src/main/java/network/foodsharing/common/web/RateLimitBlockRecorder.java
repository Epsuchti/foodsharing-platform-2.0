package network.foodsharing.common.web;

/**
 * Records a new block of the API rate limit in the history (D17.2). Implemented by the admin module, which owns the
 * history action; {@code common} cannot depend on it.
 */
public interface RateLimitBlockRecorder {

    void recordBlocked(RateLimitBlockRegistry.Block block);
}
