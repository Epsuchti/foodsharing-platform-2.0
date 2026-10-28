package network.foodsharing.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import network.foodsharing.notification.domain.Bell;
import network.foodsharing.notification.domain.NotificationChannelType;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** A23: a channel delivers only when the request uses it, the settings enable it and it reaches the user. */
class ChannelDispatcherTest {

    private final NotificationChannel mail = channel(NotificationChannelType.MAIL);
    private final NotificationChannel push = channel(NotificationChannelType.PUSH);
    private final NotificationSettingsService settings = mock(NotificationSettingsService.class);
    private final ChannelDispatcher dispatcher = new ChannelDispatcher(List.of(mail, push), settings);
    private final User user = new User();
    private final Bell bell = new Bell();

    @BeforeEach
    void setUp() {
        user.setId(UUID.randomUUID());
    }

    private static NotificationChannel channel(NotificationChannelType type) {
        NotificationChannel channel = mock(NotificationChannel.class);
        when(channel.type()).thenReturn(type);
        when(channel.applies(any())).thenReturn(true);
        return channel;
    }

    @ParameterizedTest
    @CsvSource({
        "true,  true,  true",
        "true,  false, false",
        "false, true,  false",
        "false, false, false"})
    void settingsAndAvailabilityMatrix(boolean enabled, boolean available, boolean delivered) {
        NotificationRequest request = NotificationRequest.to(List.of(user.getId()), NotificationType.PICKUP_CONFIRMED)
                .build();
        when(settings.isEnabled(user.getId(), NotificationType.PICKUP_CONFIRMED, NotificationChannelType.PUSH))
                .thenReturn(enabled);
        when(push.available(user)).thenReturn(available);

        assertThat(dispatcher.dispatch(user, bell, request).contains(NotificationChannelType.PUSH)).isEqualTo(delivered);
        verify(push, delivered ? org.mockito.Mockito.times(1) : never()).deliver(user, bell, request);
    }

    @Test
    void aFailingChannelDoesNotStopTheOthers() {
        NotificationRequest request = NotificationRequest.to(List.of(user.getId()), NotificationType.GENERIC).build();
        when(settings.isEnabled(eq(user.getId()), any(), any())).thenReturn(true);
        when(mail.available(user)).thenReturn(true);
        when(push.available(user)).thenReturn(true);
        doThrow(new IllegalStateException("smtp down")).when(mail).deliver(user, bell, request);

        assertThat(dispatcher.dispatch(user, bell, request)).containsExactly(NotificationChannelType.PUSH);
    }

    @Test
    void pickupRemindersNeedTheMasterSwitch() {
        user.setPickupRemindersEnabled(false);
        NotificationRequest request = NotificationRequest.to(List.of(user.getId()), NotificationType.PICKUP_REMINDER)
                .build();
        when(settings.isEnabled(eq(user.getId()), any(), any())).thenReturn(true);
        when(push.available(user)).thenReturn(true);

        assertThat(dispatcher.dispatch(user, bell, request)).isEmpty();
        verify(push, never()).deliver(any(), any(), any());
    }
}
