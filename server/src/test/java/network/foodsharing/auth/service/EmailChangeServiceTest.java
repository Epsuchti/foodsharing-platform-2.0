package network.foodsharing.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.auth.domain.EmailChange;
import network.foodsharing.auth.repository.AuthIdentityRepository;
import network.foodsharing.auth.repository.EmailChangeRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.Test;

/** Email change rules (A5): redacted history values, 24 h validity, single use. */
class EmailChangeServiceTest {

    @Test
    void historyValuesAreRedacted() {
        assertThat(EmailChangeService.redact("anna@example.org")).isEqualTo("a***@example.org");
        assertThat(EmailChangeService.redact("broken")).isEqualTo("***");
        assertThat(EmailChangeService.redact(null)).isNull();
    }

    @Test
    void aChangeIsPendingFor24HoursUntilCompletedOrAborted() {
        Instant requested = Instant.parse("2026-11-02T09:00:00Z");
        EmailChange change = new EmailChange();
        change.setExpiresAt(requested.plus(EmailChangeService.VALIDITY));
        assertThat(change.isPendingAt(requested.plusSeconds(23 * 3600))).isTrue();
        assertThat(change.isPendingAt(requested.plusSeconds(24 * 3600))).isFalse();
        change.setCompletedAt(requested);
        assertThat(change.isPendingAt(requested)).isFalse();
        change.setCompletedAt(null);
        change.setAbortedAt(requested);
        assertThat(change.isPendingAt(requested)).isFalse();
    }

    /** A3 (moved from the e2e spec a3-email-change): an address of another member, or the own one, is refused. */
    @Test
    void anAddressInUseIsRefusedBeforeAnythingIsStored() {
        UUID me = UUID.randomUUID();
        User sarah = new User();
        sarah.setId(me);
        sarah.setEmail("foodsaver@local");
        sarah.setActive(true);
        User nina = new User();
        nina.setId(UUID.randomUUID());
        nina.setEmail("bieb@local");
        EmailChangeRepository changes = mock(EmailChangeRepository.class);
        UserService users = mock(UserService.class);
        CurrentActorService actors = mock(CurrentActorService.class);
        when(actors.requirePrincipal()).thenReturn(new SessionPrincipal(me, UUID.randomUUID(), Role.FOODSAVER, "foodsaver@local"));
        when(users.requireActive(me)).thenReturn(sarah);
        when(users.findByEmail(any())).thenReturn(Optional.empty());
        when(users.findByEmail("bieb@local")).thenReturn(Optional.of(nina));
        AuthIdentityRepository identities = mock(AuthIdentityRepository.class);
        when(identities.findByProviderAndSubject(any(), any())).thenReturn(Optional.empty());
        EmailChangeService service = new EmailChangeService(changes, identities, mock(EmailBlocklistService.class),
                mock(AuthSessionService.class), users, actors, new PermissionService(), mock(MailOutboxService.class),
                mock(HistoryService.class), mock(AppProperties.class),
                Clock.fixed(Instant.parse("2026-11-02T09:00:00Z"), ZoneOffset.UTC));

        assertThat(code(() -> service.request(me, "Bieb@Local"))).isEqualTo(ApiErrorCode.EMAIL_TAKEN);
        assertThat(code(() -> service.request(me, "foodsaver@local"))).isEqualTo(ApiErrorCode.EMAIL_TAKEN);
        assertThat(code(() -> service.request(me, "not an address"))).isEqualTo(ApiErrorCode.EMAIL_INVALID);
        verify(changes, never()).save(any());
    }

    private static ApiErrorCode code(Runnable call) {
        ApiException ex = catchThrowableOfType(ApiException.class, call::run);
        return ex == null ? null : ex.getCode();
    }
}
