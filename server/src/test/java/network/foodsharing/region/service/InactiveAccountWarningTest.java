package network.foodsharing.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import network.foodsharing.config.AppProperties;
import network.foodsharing.notification.mail.service.MailDraft;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** A13: region maintenance warns inactive accounts by mail, disabled with {@code inactive-days = 0}. */
class InactiveAccountWarningTest {

    private final UserService users = mock(UserService.class);
    private final MailOutboxService outbox = mock(MailOutboxService.class);
    private final AppProperties properties = new AppProperties();
    private final Instant now = Instant.parse("2026-06-01T00:00:00Z");
    private final InactiveAccountWarning warning = new InactiveAccountWarning(users, outbox, properties,
            Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void disabledByDefault() {
        assertThat(warning.warn()).isZero();
        verify(users, never()).inactiveUnwarned(any());
    }

    @Test
    void mailsAndMarksEveryDueAccount() {
        properties.getRegion().setInactiveDays(180);
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("lea@test.local");
        when(users.inactiveUnwarned(now.minusSeconds(180L * 86400))).thenReturn(List.of(user));

        assertThat(warning.warn()).isEqualTo(1);

        ArgumentCaptor<MailDraft> mail = ArgumentCaptor.forClass(MailDraft.class);
        verify(outbox).enqueue(mail.capture());
        assertThat(mail.getValue().template()).isEqualTo("user/inactivity-warning");
        assertThat(mail.getValue().to()).isEqualTo("lea@test.local");
        assertThat(mail.getValue().variables()).containsEntry("days", 180);
        verify(users).markInactivityWarned(user.getId());
    }
}
