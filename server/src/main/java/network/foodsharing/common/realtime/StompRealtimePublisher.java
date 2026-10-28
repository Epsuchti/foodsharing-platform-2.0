package network.foodsharing.common.realtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * STOMP {@link RealtimePublisher} (A32): sends to {@code /user/<userId>/queue/events} after the surrounding
 * transaction commits (nothing on rollback; immediately without a transaction) and never fails the caller.
 */
@Primary
@Component
public class StompRealtimePublisher implements RealtimePublisher {

    public static final String DESTINATION = "/queue/events";
    private static final Logger LOG = LoggerFactory.getLogger(StompRealtimePublisher.class);

    private final SimpMessagingTemplate template;
    private final RealtimeSessionRegistry sessions;

    public StompRealtimePublisher(SimpMessagingTemplate template, RealtimeSessionRegistry sessions) {
        this.template = template;
        this.sessions = sessions;
    }

    @Override
    public void toUsers(Set<UUID> userIds, RealtimeEvent event) {
        if (userIds.isEmpty()) {
            return;
        }
        Set<UUID> recipients = Set.copyOf(userIds);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(recipients, event);
                }
            });
        } else {
            send(recipients, event);
        }
    }

    @Override
    public Set<UUID> connectedUsers() {
        return sessions.connectedUsers();
    }

    void send(Set<UUID> userIds, RealtimeEvent event) {
        Map<String, Object> frame = toFrame(event);
        for (UUID userId : userIds) {
            try {
                template.convertAndSendToUser(userId.toString(), DESTINATION, frame);
            } catch (RuntimeException e) {
                LOG.warn("Realtime {} to {} failed: {}", event.type(), userId, e.getMessage());
            }
        }
    }

    /** JSON shape of {@code RealtimeEvent} in the OpenAPI contract (ISO instant as string). */
    static Map<String, Object> toFrame(RealtimeEvent event) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", event.type().name());
        frame.put("at", event.at().toString());
        frame.put("payload", event.payload());
        return frame;
    }
}
