package network.foodsharing.common.realtime;

import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/** Open STOMP sessions per user (A32): presence for "connected → no push" and the dev sessions endpoint. */
@Component
public class RealtimeSessionRegistry {

    private final Map<String, UUID> sessions = new ConcurrentHashMap<>();

    @EventListener
    public void on(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        UUID userId = userId(event.getUser());
        if (accessor.getSessionId() != null && userId != null) {
            sessions.put(accessor.getSessionId(), userId);
        }
    }

    @EventListener
    public void on(SessionDisconnectEvent event) {
        sessions.remove(event.getSessionId());
    }

    public Set<UUID> connectedUsers() {
        return sessions.values().stream().collect(Collectors.toUnmodifiableSet());
    }

    /** Registers a session directly (tests). */
    void register(String sessionId, UUID userId) {
        sessions.put(sessionId, userId);
    }

    static UUID userId(Principal principal) {
        if (principal == null) {
            return null;
        }
        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
