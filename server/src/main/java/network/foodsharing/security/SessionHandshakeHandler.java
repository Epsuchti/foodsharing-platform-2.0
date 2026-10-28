package network.foodsharing.security;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * A32: the WebSocket principal is the user of the {@code fs_session} cookie, already authenticated by
 * {@link SessionCookieAuthenticationFilter} (the security chain answers 401 for {@code /ws} without a valid session).
 * Its name is the user id, so {@code convertAndSendToUser(userId, …)} reaches every tab of that user.
 */
public class SessionHandshakeHandler extends DefaultHandshakeHandler {

    /** Principal of a realtime connection; {@link #getName()} = user id. */
    public record RealtimeUser(UUID userId) implements Principal {
        @Override
        public String getName() {
            return userId.toString();
        }
    }

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        if (request.getPrincipal() instanceof SessionAuthenticationToken token && token.getPrincipal() != null) {
            return new RealtimeUser(token.getPrincipal().userId());
        }
        return null;
    }
}
