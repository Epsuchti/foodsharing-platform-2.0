package network.foodsharing.notification.mail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.api.model.Language;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.mail.repository.MailOutboxRepository;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * A18 (formerly asserted by AccountFlowIT): non-AUTH mails to a user whose current address hard-bounced are not
 * queued; sign-in mails still go out, and once the flag is cleared the mails flow again.
 */
class MailOutboxBounceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");

    private final MailOutboxRepository repository = mock(MailOutboxRepository.class);
    private final MailTemplateService templates = mock(MailTemplateService.class);
    private final UserService users = mock(UserService.class);
    private MailOutboxService outbox;
    private User bieb;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        MailRateLimiter limiter = mock(MailRateLimiter.class);
        when(limiter.check(any(), any())).thenReturn(MailRateLimiter.Decision.allow());
        outbox = new MailOutboxService(repository, templates, mock(MailDeliveryService.class),
                mock(HistoryService.class, RETURNS_DEEP_STUBS), new AppProperties(), Clock.fixed(NOW, ZoneOffset.UTC),
                limiter);
        ObjectProvider<UserService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(users);
        outbox.setUsers(provider);
        when(templates.render(any(), any(), any())).thenReturn(new RenderedMail("s", "t", null, Language.DE));
        when(repository.save(any())).thenAnswer(i -> {
            Object message = i.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(message, "id", UUID.randomUUID());
            return message;
        });
        bieb = new User();
        bieb.setId(UUID.randomUUID());
        bieb.setEmail("bieb@local");
        when(users.findAll(List.of(bieb.getId()))).thenReturn(List.of(bieb));
    }

    private MailDraft draft(String category) {
        return new MailDraft("Bieb@local", "forum-new-post", Language.DE, Map.of(), bieb.getId(), category, null);
    }

    @Test
    void aBouncedAddressGetsNoMailsButSignInMailsUntilTheFlagIsCleared() {
        bieb.setEmailBouncedAt(Instant.parse("2026-11-01T10:00:00Z"));
        assertThat(outbox.enqueue(draft("FORUM"))).isNull();
        verify(repository, never()).save(any());

        outbox.enqueue(draft(MailDraft.AUTH));
        verify(repository).save(any());

        bieb.setEmailBouncedAt(null);
        outbox.enqueue(draft("FORUM"));
        verify(repository, org.mockito.Mockito.times(2)).save(any());
    }
}
