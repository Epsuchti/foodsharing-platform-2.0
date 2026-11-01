package network.foodsharing.admin.service;

import java.util.LinkedHashMap;
import java.util.Map;
import network.foodsharing.admin.domain.AdminAction;
import network.foodsharing.common.web.RateLimitBlockRecorder;
import network.foodsharing.common.web.RateLimitBlockRegistry;
import network.foodsharing.common.web.RateLimitBlockRegistry.Block;
import network.foodsharing.history.service.HistoryService;
import org.springframework.stereotype.Service;

/**
 * History of the automatic API rate-limit blocks (D17.2): one {@link AdminAction#RATE_LIMIT_BLOCKED} entry per block
 * (never per rejected request), with the raw IP or the user id, the level and the end time; kept 90 days.
 */
@Service
public class RateLimitBlockService implements RateLimitBlockRecorder {

    private final RateLimitBlockRegistry registry;
    private final HistoryService history;

    public RateLimitBlockService(RateLimitBlockRegistry registry, HistoryService history) {
        this.registry = registry;
        this.history = history;
    }

    /** Called by the filter outside any transaction: the entry gets its own, with actor SYSTEM. */
    @Override
    public void recordBlocked(Block block) {
        history.recordInNewTransaction(history.event(AdminAction.RATE_LIMIT_BLOCKED, block.id()).actorSystem()
                .metadata(metadata(block)).build());
    }

    /** Blocks an IP or account at its next level and records it (dev endpoint for e2e). */
    public Block create(RateLimitBlockRegistry.Scope scope, String subject) {
        Block block = registry.create(scope, subject);
        recordBlocked(block);
        return block;
    }

    /** History metadata of a block: scope, ip or userId, level, until. */
    static Map<String, Object> metadata(Block block) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("scope", block.scope().name());
        metadata.put(block.scope() == RateLimitBlockRegistry.Scope.ACCOUNT ? "userId" : "ip", block.subject());
        metadata.put("level", block.level());
        metadata.put("until", block.expiresAt().toString());
        return metadata;
    }
}
