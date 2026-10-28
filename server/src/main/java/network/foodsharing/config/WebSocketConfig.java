package network.foodsharing.config;

import network.foodsharing.common.realtime.SubscriptionGuard;
import network.foodsharing.security.SessionHandshakeHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Realtime (A32): one plain WebSocket STOMP endpoint {@code /ws} (no SockJS), simple broker, user destinations,
 * heartbeats both ways ({@code app.realtime.heartbeat-ms}), only {@code /user/queue/events} subscriptions.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AppProperties properties;

    public WebSocketConfig(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setHandshakeHandler(new SessionHandshakeHandler())
                .setAllowedOriginPatterns(properties.getBaseUrl());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        ThreadPoolTaskScheduler heartbeats = new ThreadPoolTaskScheduler();
        heartbeats.setPoolSize(1);
        heartbeats.setThreadNamePrefix("ws-heartbeat-");
        heartbeats.initialize();
        long heartbeat = properties.getRealtime().getHeartbeatMs();
        registry.enableSimpleBroker("/queue").setHeartbeatValue(new long[] {heartbeat, heartbeat})
                .setTaskScheduler(heartbeats);
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new SubscriptionGuard());
    }
}
