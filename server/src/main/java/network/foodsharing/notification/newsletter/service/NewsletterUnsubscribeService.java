package network.foodsharing.notification.newsletter.service;

import java.util.Map;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationAction;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public one-click unsubscribe (N7): works without a session, the HMAC token identifies the user. */
@Service
public class NewsletterUnsubscribeService {

    private final NewsletterTokenService tokens;
    private final UserService users;
    private final HistoryService history;

    public NewsletterUnsubscribeService(NewsletterTokenService tokens, UserService users, HistoryService history) {
        this.tokens = tokens;
        this.users = users;
        this.history = history;
    }

    /** The masked address the token belongs to. @throws ApiException 404 INVALID_UNSUBSCRIBE_TOKEN */
    @Transactional(readOnly = true)
    public String maskedEmail(String token) {
        return mask(user(token).getEmail());
    }

    /** Opts the user out (idempotent). @throws ApiException 404 INVALID_UNSUBSCRIBE_TOKEN */
    @Transactional
    public void unsubscribe(String token) {
        User user = user(token);
        if (users.optOutOfNewsletter(user.getId())) {
            history.record(history.event(NotificationAction.NEWSLETTER_OPT_OUT, user.getId()).actorUser(user.getId())
                    .metadata(Map.of("via", "unsubscribe-link")).build());
        }
    }

    private User user(String token) {
        UUID userId = tokens.verify(token)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.INVALID_UNSUBSCRIBE_TOKEN));
        return users.findAll(java.util.List.of(userId)).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.INVALID_UNSUBSCRIBE_TOKEN));
    }

    /** {@code sarah@example.org} → {@code s***h@example.org}. */
    static String mask(String email) {
        if (email == null) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String masked = local.length() <= 2 ? local.charAt(0) + "***"
                : local.charAt(0) + "***" + local.charAt(local.length() - 1);
        return masked + email.substring(at);
    }
}
