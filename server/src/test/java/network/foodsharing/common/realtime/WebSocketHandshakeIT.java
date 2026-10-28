package network.foodsharing.common.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import network.foodsharing.messaging.service.MessagingService;
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/** A32: cookie-authenticated handshake, subscription guard, per-user delivery of conversation messages, presence. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:websocket-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@ActiveProfiles("e2e")
class WebSocketHandshakeIT {

    @LocalServerPort
    private int port;

    @Autowired
    private MessagingService messaging;

    @Autowired
    private RealtimePublisher realtime;

    private final HttpClient http = HttpClient.newHttpClient();
    private final WebSocketStompClient stomp = new WebSocketStompClient(new StandardWebSocketClient());

    @AfterEach
    void stop() {
        stomp.stop();
    }

    private String cookie(SeedUsers user) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port
                        + "/api/dev/login-as")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"userId\":\"" + user.id() + "\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("fs_session="))
                .map(c -> c.substring(0, c.indexOf(';'))).findFirst().orElseThrow();
    }

    private StompSession connect(String cookie) throws Exception {
        stomp.setMessageConverter(new JacksonJsonMessageConverter());
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        if (cookie != null) {
            headers.add("Cookie", cookie);
        }
        return stomp.connectAsync(URI.create("ws://localhost:" + port + "/ws"), headers, new StompHeaders(),
                new StompSessionHandlerAdapter() { }).get(5, TimeUnit.SECONDS);
    }

    private BlockingQueue<Map<String, Object>> subscribe(StompSession session, String destination) {
        BlockingQueue<Map<String, Object>> frames = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                frames.add((Map<String, Object>) payload);
            }
        });
        return frames;
    }

    private void awaitConnected(UUID userId) throws InterruptedException {
        for (int i = 0; i < 50 && !realtime.connectedUsers().contains(userId); i++) {
            Thread.sleep(100);
        }
        assertThat(realtime.connectedUsers()).contains(userId);
        Thread.sleep(300); // let the SUBSCRIBE reach the broker
    }

    @Test
    void anonymousHandshakeIsRejected() {
        assertThatThrownBy(() -> connect(null)).isInstanceOf(ExecutionException.class);
    }

    @Test
    void membersGetConversationMessagesNonMembersDoNot() throws Exception {
        StompSession guide = connect(cookie(SeedUsers.GUIDE));
        StompSession jumper = connect(cookie(SeedUsers.JUMPER));
        BlockingQueue<Map<String, Object>> guideFrames = subscribe(guide, "/user/queue/events");
        BlockingQueue<Map<String, Object>> jumperFrames = subscribe(jumper, "/user/queue/events");
        awaitConnected(SeedUsers.GUIDE.id());
        awaitConnected(SeedUsers.JUMPER.id());

        UUID conversation = messaging.lookup(SeedUsers.WELCOME.id(), List.of(SeedUsers.GUIDE.id())).getId();
        messaging.send(SeedUsers.WELCOME.id(), conversation, "Live!", "ws-1");

        Map<String, Object> frame = guideFrames.poll(5, TimeUnit.SECONDS);
        assertThat(frame).isNotNull();
        assertThat(frame.get("type")).isEqualTo("CONVERSATION_MESSAGE");
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) frame.get("payload");
        assertThat(payload).containsEntry("conversationId", conversation.toString()).containsEntry("body", "Live!");
        assertThat(jumperFrames.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void otherSubscriptionsAreRefused() throws Exception {
        StompSession session = connect(cookie(SeedUsers.BIEB));
        subscribe(session, "/topic/x");
        for (int i = 0; i < 50 && session.isConnected(); i++) {
            Thread.sleep(100);
        }
        assertThat(session.isConnected()).isFalse();
    }
}
