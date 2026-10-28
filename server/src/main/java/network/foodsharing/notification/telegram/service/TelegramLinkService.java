package network.foodsharing.notification.telegram.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.NoHistory;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationAction;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.notification.telegram.domain.TelegramLinkToken;
import network.foodsharing.notification.telegram.repository.TelegramLinkTokenRepository;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Telegram linking (A28): {@link #createToken} → deep link {@code https://t.me/<bot>?start=<token>}; the bot receives
 * {@code /start <token>} ({@link #start}) and links the private chat (one-time token, 15 min), {@code /stop}
 * ({@link #stop}) or the settings page ({@link #unlink}) unlink it. Every outcome is answered in the chat.
 */
@Service
public class TelegramLinkService {

    /** Result of a deep-link token request. */
    public record LinkToken(String token, String deepLink, Instant expiresAt) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final TelegramLinkTokenRepository tokens;
    private final UserService users;
    private final TelegramDeliveryService delivery;
    private final NotificationService notifications;
    private final HistoryService history;
    private final AppProperties properties;
    private final Clock clock;

    public TelegramLinkService(TelegramLinkTokenRepository tokens, UserService users, TelegramDeliveryService delivery,
                               NotificationService notifications, HistoryService history, AppProperties properties,
                               Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.delivery = delivery;
        this.notifications = notifications;
        this.history = history;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * A new one-time token for the user; older unused tokens are discarded.
     *
     * @throws ApiException 409 TELEGRAM_NOT_CONFIGURED
     */
    @Transactional
    @NoHistory(reason = "a pending token changes nothing; linking records NOTIFICATION_TELEGRAM_LINKED")
    public LinkToken createToken(User user) {
        delivery.requireConfigured();
        tokens.deleteOpenByUserId(user.getId());
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        TelegramLinkToken token = new TelegramLinkToken();
        token.setToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        token.setUserId(user.getId());
        token.setExpiresAt(clock.instant().plus(Duration.ofMinutes(properties.getTelegram().getLinkTokenMinutes())));
        tokens.save(token);
        return new LinkToken(token.getToken(),
                "https://t.me/" + properties.getTelegram().getBotName() + "?start=" + token.getToken(),
                token.getExpiresAt());
    }

    /**
     * {@code /start <token>} from a private chat: links the chat when the token is valid (unused, not expired, user
     * active), records TELEGRAM_LINKED, creates the TELEGRAM_LINKED bell and confirms in the chat.
     *
     * @return the linked user, empty when the token was invalid (the chat is told so)
     */
    @Transactional
    public Optional<User> start(String chatId, String token) {
        Instant now = clock.instant();
        Optional<TelegramLinkToken> found = token == null ? Optional.empty() : tokens.findByToken(token.strip());
        Optional<User> user = found.filter(t -> t.getUsedAt() == null && t.getExpiresAt().isAfter(now))
                .flatMap(t -> users.findActive(t.getUserId()));
        if (user.isEmpty()) {
            delivery.sendText(chatId, delivery.text("telegram.invalid-token", TelegramDeliveryService.locale(null)),
                    null, "TELEGRAM_REPLY");
            return Optional.empty();
        }
        found.get().setUsedAt(now);
        User linked = users.setTelegramChatId(user.get().getId(), chatId);
        history.record(history.event(NotificationAction.TELEGRAM_LINKED, HistoryService.SYSTEM_SUBJECT_ID)
                .secondarySubject(linked.getId()).actorUser(linked.getId()).build());
        notifications.notify(NotificationRequest.to(List.of(linked.getId()), NotificationType.TELEGRAM_LINKED)
                .link("/settings/notifications").identifier("TELEGRAM_LINKED-" + linked.getId()).build());
        delivery.sendText(chatId, delivery.text("telegram.linked", TelegramDeliveryService.locale(linked)), linked,
                "TELEGRAM_REPLY");
        return Optional.of(linked);
    }

    /** {@code /stop}: unlinks whoever uses this chat (no-op otherwise) and confirms. */
    @Transactional
    public boolean stop(String chatId) {
        Optional<User> user = users.findByTelegramChatId(chatId);
        if (user.isPresent()) {
            doUnlink(user.get());
        }
        delivery.sendText(chatId, delivery.text("telegram.unlinked", TelegramDeliveryService.locale(user.orElse(null))),
                user.orElse(null), "TELEGRAM_REPLY");
        return user.isPresent();
    }

    /**
     * {@code DELETE /api/telegram/link}.
     *
     * @throws ApiException 409 TELEGRAM_NOT_LINKED
     */
    @Transactional
    public void unlink(User user) {
        User current = users.require(user.getId());
        if (current.getTelegramChatId() == null) {
            throw ApiException.conflict(ApiErrorCode.TELEGRAM_NOT_LINKED);
        }
        doUnlink(current);
    }

    /** Any other text in a private chat: a short help. */
    public void help(String chatId) {
        Optional<User> user = users.findByTelegramChatId(chatId);
        delivery.sendText(chatId, delivery.text("telegram.help", TelegramDeliveryService.locale(user.orElse(null))),
                user.orElse(null), "TELEGRAM_REPLY");
    }

    /** Cleanup job: drops expired tokens; @return how many. */
    @Transactional
    @NoHistory(reason = "housekeeping of expired one-time tokens")
    public int deleteExpiredTokens() {
        return tokens.deleteExpiredBefore(clock.instant());
    }

    private void doUnlink(User user) {
        users.setTelegramChatId(user.getId(), null);
        history.record(history.event(NotificationAction.TELEGRAM_UNLINKED, HistoryService.SYSTEM_SUBJECT_ID)
                .secondarySubject(user.getId()).metadata(Map.of("userId", user.getId().toString())).build());
    }
}
