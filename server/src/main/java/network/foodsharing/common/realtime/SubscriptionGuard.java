package network.foodsharing.common.realtime;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;

/**
 * A32: clients may only SUBSCRIBE to {@code /user/queue/events} (server-side fan-out, no topic authorization) and
 * never SEND; anything else is answered with an ERROR frame.
 */
public class SubscriptionGuard implements ChannelInterceptor {

    public static final String ALLOWED = "/user" + StompRealtimePublisher.DESTINATION;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        if (accessor.getCommand() == StompCommand.SUBSCRIBE && !ALLOWED.equals(accessor.getDestination())) {
            throw new AccessDeniedException("Subscription to " + accessor.getDestination() + " is not allowed");
        }
        if (accessor.getCommand() == StompCommand.SEND) {
            throw new AccessDeniedException("Sending over the realtime connection is not allowed");
        }
        if (accessor.getCommand() == StompCommand.CONNECT && accessor.getUser() == null) {
            throw new AccessDeniedException("Authentication required");
        }
        return message;
    }
}
