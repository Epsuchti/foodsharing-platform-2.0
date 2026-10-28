package network.foodsharing.notification.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.history.NoHistory;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.Bell;
import network.foodsharing.notification.domain.NotificationAction;
import network.foodsharing.notification.domain.NotificationChannelType;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one entry point for notifications (docs/conventions.md A12, A23): a bell per recipient, then the channels (mail
 * unless {@code bellOnly}, push, Telegram) per the recipient's category settings ({@link ChannelDispatcher}). Inactive
 * or unknown recipients are skipped. Cross-module callers trigger this from
 * {@code @TransactionalEventListener(AFTER_COMMIT)} listeners, never from the mutating service.
 */
@Service
public class NotificationService {

    private final BellService bells;
    private final UserService users;
    private final HistoryService history;
    private final ChannelDispatcher channels;

    public NotificationService(BellService bells, UserService users, HistoryService history,
                               ChannelDispatcher channels) {
        this.bells = bells;
        this.users = users;
        this.history = history;
        this.channels = channels;
    }

    /** @return the created bells (one per active recipient) */
    @Transactional
    public List<Bell> notify(NotificationRequest request) {
        List<Bell> created = new ArrayList<>();
        int mailed = 0;
        for (UUID recipientId : request.recipientIds()) {
            Optional<User> recipient = users.findActive(recipientId);
            if (recipient.isEmpty()) {
                continue;
            }
            Bell bell = bells.create(recipientId, request.type(), request.params(), request.link(),
                    request.identifier(), request.icon(), null);
            if (channels.dispatch(recipient.get(), bell, request).contains(NotificationChannelType.MAIL)) {
                mailed++;
            }
            created.add(bell);
        }
        history.record(history.event(NotificationAction.SENT, HistoryService.SYSTEM_SUBJECT_ID)
                .metadata(Map.of("type", request.type().name(), "recipients", created.size(), "mailed", mailed > 0))
                .build());
        return created;
    }

    /**
     * Sends the notification through the PUSH channel only (no bell, no mail; open-slot adverts with channel PUSH,
     * AU4). Respects the recipients' PUSH settings.
     *
     * @return how many recipients got a push
     */
    @Transactional
    public int pushOnly(NotificationRequest request) {
        int pushed = 0;
        for (UUID recipientId : request.recipientIds()) {
            Optional<User> recipient = users.findActive(recipientId);
            if (recipient.isEmpty()) {
                continue;
            }
            Bell transientBell = new Bell();
            transientBell.setUserId(recipientId);
            transientBell.setType(request.type());
            transientBell.setTitleKey(request.type().defaultTitleKey());
            if (channels.dispatchOnly(recipient.get(), transientBell, request, NotificationChannelType.PUSH)) {
                pushed++;
            }
        }
        history.record(history.event(NotificationAction.SENT, HistoryService.SYSTEM_SUBJECT_ID)
                .metadata(Map.of("type", request.type().name(), "recipients", pushed, "channel", "PUSH")).build());
        return pushed;
    }

    /** Removes bells created with this identifier. */
    @Transactional
    @NoHistory(reason = "bells are transient hints; the action that resolves them records its own history")
    public int removeBells(String identifier) {
        return bells.deleteByIdentifier(identifier);
    }

    /** Was a bell with this identifier created after {@code since} (e.g. "once per 30 days" reminders)? */
    @Transactional(readOnly = true)
    public boolean sentSince(String identifier, Instant since) {
        return bells.existsSince(identifier, since);
    }
}
