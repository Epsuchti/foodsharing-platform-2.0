package network.foodsharing.common.realtime;

import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Default {@link RealtimePublisher} until the STOMP one exists: logs and drops (clients refetch anyway, A32). */
@Component
public class NoopRealtimePublisher implements RealtimePublisher {

    private static final Logger LOG = LoggerFactory.getLogger(NoopRealtimePublisher.class);

    @Override
    public void toUsers(Set<UUID> userIds, RealtimeEvent event) {
        LOG.debug("Realtime {} to {} users dropped (no realtime transport)", event.type(), userIds.size());
    }

    @Override
    public Set<UUID> connectedUsers() {
        return Set.of();
    }
}
