package network.foodsharing.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.realtime.RealtimePublisher;
import network.foodsharing.config.MutableClock;
import network.foodsharing.messaging.service.MessagingService;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.testsupport.JpaSliceTest;
import network.foodsharing.testsupport.TestUsers;
import network.foodsharing.user.repository.UserRepository;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Messages per user and minute are limited (429 MESSAGE_RATE_LIMITED); idempotent resends do not count. */
@JpaSliceTest
@Import({MessagingService.class, UserService.class})
@TestPropertySource(properties = "app.messaging.max-messages-per-minute=2")
class MessagingRateLimitTest {

    @Autowired
    private MessagingService messaging;

    @Autowired
    private UserRepository users;

    @Autowired
    private MutableClock clock;

    @MockitoBean
    private NotificationService notifications;

    @MockitoBean
    private RealtimePublisher realtime;

    @Test
    void theThirdMessageWithinAMinuteIsRejected() {
        when(realtime.connectedUsers()).thenReturn(Set.of());
        when(notifications.mailOnly(any())).thenAnswer(i -> ((NotificationRequest) i.getArgument(0)).recipientIds());
        UUID me = users.saveAndFlush(TestUsers.user("rl-me@test.local", Role.FOODSAVER)).getId();
        UUID bob = users.saveAndFlush(TestUsers.user("rl-bob@test.local", Role.FOODSAVER)).getId();
        UUID id = messaging.lookup(me, List.of(bob)).getId();

        messaging.send(me, id, "one", "k1");
        messaging.send(me, id, "two", "k2");
        assertThat(messaging.send(me, id, "one", "k1").getBody()).isEqualTo("one");
        assertThatThrownBy(() -> messaging.send(me, id, "three", "k3"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ApiErrorCode.MESSAGE_RATE_LIMITED);
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                });
        messaging.send(bob, id, "bob is not limited by me", null);

        clock.fixAt(clock.instant().plus(Duration.ofSeconds(61)));
        messaging.send(me, id, "three", "k3");
    }
}
