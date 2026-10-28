package network.foodsharing.common.realtime;

import java.util.Set;
import java.util.UUID;

/**
 * The only way modules send realtime events (A32). Implementations deliver after the surrounding transaction commits
 * (immediately without one) and never fail the caller. {@link NoopRealtimePublisher} is the default; the STOMP
 * implementation (B3) is {@code @Primary} (decision 48).
 */
public interface RealtimePublisher {

    void toUsers(Set<UUID> userIds, RealtimeEvent event);

    /** Users with at least one open realtime connection (empty when realtime is off). */
    Set<UUID> connectedUsers();
}
