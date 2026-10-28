package network.foodsharing.notification.telegram.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.notification.telegram.domain.TelegramLinkToken;
import network.foodsharing.notification.telegram.repository.TelegramLinkTokenRepository;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A28: one-time token with 15 min expiry, reuse rejected, /stop, unlink without link, not configured. */
class TelegramLinkServiceTest {

    static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");

    private TelegramLinkTokenRepository tokens;
    private UserService users;
    private TelegramDeliveryService delivery;
    private NotificationService notifications;
    private TelegramLinkService service;
    private User user;

    @BeforeEach
    void setUp() {
        tokens = mock(TelegramLinkTokenRepository.class);
        users = mock(UserService.class);
        delivery = mock(TelegramDeliveryService.class);
        notifications = mock(NotificationService.class);
        HistoryService history = mock(HistoryService.class);
        when(history.event(any(), any())).thenAnswer(i -> HistoryEvent.builder(i.getArgument(0), i.getArgument(1)));
        when(delivery.text(anyString(), any())).thenAnswer(i -> i.getArgument(0));
        AppProperties properties = new AppProperties();
        properties.getTelegram().setBotName("bot");
        service = new TelegramLinkService(tokens, users, delivery, notifications, history, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
        user = new User();
        user.setId(UUID.randomUUID());
        when(users.findActive(user.getId())).thenReturn(Optional.of(user));
        when(users.setTelegramChatId(eq(user.getId()), any())).thenAnswer(i -> {
            user.setTelegramChatId(i.getArgument(1));
            return user;
        });
        when(tokens.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private TelegramLinkToken token(Instant expiresAt, Instant usedAt) {
        TelegramLinkToken token = new TelegramLinkToken();
        token.setToken("tok");
        token.setUserId(user.getId());
        token.setExpiresAt(expiresAt);
        token.setUsedAt(usedAt);
        when(tokens.findByToken("tok")).thenReturn(Optional.of(token));
        return token;
    }

    @Test
    void createTokenExpiresAfter15MinutesAndBuildsTheDeepLink() {
        TelegramLinkService.LinkToken link = service.createToken(user);
        assertThat(link.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(link.deepLink()).isEqualTo("https://t.me/bot?start=" + link.token());
        assertThat(link.token()).matches("[A-Za-z0-9_-]{32}");
        verify(tokens).deleteOpenByUserId(user.getId());
    }

    @Test
    void validTokenLinksOnceAndConfirms() {
        TelegramLinkToken token = token(NOW.plusSeconds(60), null);
        assertThat(service.start("42", "tok")).contains(user);
        assertThat(user.getTelegramChatId()).isEqualTo("42");
        assertThat(token.getUsedAt()).isEqualTo(NOW);
        verify(notifications).notify(any());
        verify(delivery).sendText(eq("42"), eq("telegram.linked"), eq(user), anyString());
    }

    @Test
    void expiredOrReusedTokenIsRejected() {
        token(NOW.minusSeconds(1), null);
        assertThat(service.start("42", "tok")).isEmpty();
        token(NOW.plusSeconds(60), NOW.minusSeconds(5));
        assertThat(service.start("42", "tok")).isEmpty();
        assertThat(service.start("42", "unknown")).isEmpty();
        verify(users, never()).setTelegramChatId(any(), any());
        verify(delivery, org.mockito.Mockito.times(3)).sendText(eq("42"), eq("telegram.invalid-token"), isNull(),
                anyString());
    }

    @Test
    void stopUnlinksAndUnlinkWithoutLinkFails() {
        user.setTelegramChatId("42");
        when(users.findByTelegramChatId("42")).thenReturn(Optional.of(user));
        assertThat(service.stop("42")).isTrue();
        verify(users).setTelegramChatId(user.getId(), null);
        when(users.require(user.getId())).thenReturn(user);
        assertThatThrownBy(() -> service.unlink(user)).isInstanceOf(ApiException.class)
                .hasMessageContaining("TELEGRAM_NOT_LINKED");
        when(users.findByTelegramChatId("77")).thenReturn(Optional.empty());
        assertThat(service.stop("77")).isFalse();
    }

    @Test
    void notConfiguredBotRefusesTokens() {
        doThrow(new ApiException(org.springframework.http.HttpStatus.CONFLICT,
                network.foodsharing.api.model.ApiErrorCode.TELEGRAM_NOT_CONFIGURED)).when(delivery).requireConfigured();
        assertThatThrownBy(() -> service.createToken(user)).isInstanceOf(ApiException.class);
        verify(tokens, never()).save(any());
    }
}
