package network.foodsharing.notification.service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationAction;
import network.foodsharing.notification.domain.NotificationCategory;
import network.foodsharing.notification.domain.NotificationChannelType;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.domain.UserNotificationSetting;
import network.foodsharing.notification.repository.UserNotificationSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Channel switches per user and {@link NotificationCategory} (A23). Bells are always on; SYSTEM cannot be changed.
 * Without a row the defaults apply: mail on except FORUM and MESSAGES, push and Telegram on (they only reach users who
 * subscribed / linked).
 */
@Service
public class NotificationSettingsService {

    /** Switches of one category. */
    public record CategorySwitches(NotificationCategory category, boolean mail, boolean push, boolean telegram) {

        boolean enabled(NotificationChannelType channel) {
            return switch (channel) {
                case MAIL -> mail;
                case PUSH -> push;
                case TELEGRAM -> telegram;
            };
        }
    }

    private final UserNotificationSettingRepository repository;
    private final HistoryService history;

    public NotificationSettingsService(UserNotificationSettingRepository repository, HistoryService history) {
        this.repository = repository;
        this.history = history;
    }

    public static CategorySwitches defaults(NotificationCategory category) {
        boolean mail = category != NotificationCategory.FORUM && category != NotificationCategory.MESSAGES;
        return new CategorySwitches(category, mail, true, true);
    }

    /** All categories in declaration order, stored values over defaults. */
    @Transactional(readOnly = true)
    public List<CategorySwitches> settings(UUID userId) {
        Map<NotificationCategory, CategorySwitches> result = new EnumMap<>(NotificationCategory.class);
        for (NotificationCategory category : NotificationCategory.values()) {
            result.put(category, defaults(category));
        }
        for (UserNotificationSetting row : repository.findByUserId(userId)) {
            if (row.getCategory().switchable()) {
                result.put(row.getCategory(), new CategorySwitches(row.getCategory(), row.isMail(), row.isPush(),
                        row.isTelegram()));
            }
        }
        return new ArrayList<>(result.values());
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(UUID userId, NotificationType type, NotificationChannelType channel) {
        NotificationCategory category = type.category();
        if (!category.switchable()) {
            return true;
        }
        return repository.findByUserIdAndCategory(userId, category)
                .map(row -> new CategorySwitches(category, row.isMail(), row.isPush(), row.isTelegram()))
                .orElseGet(() -> defaults(category))
                .enabled(channel);
    }

    /**
     * Stores the given categories (others stay). SYSTEM must keep its defaults (400 VALIDATION_FAILED otherwise).
     * Records one SETTINGS_CHANGED entry with the changed categories.
     */
    @Transactional
    public List<CategorySwitches> update(UUID userId, List<CategorySwitches> changes) {
        Map<String, Object> changed = new LinkedHashMap<>();
        for (CategorySwitches change : changes) {
            if (!change.category().switchable()) {
                if (!change.equals(defaults(change.category()))) {
                    throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED)
                            .withArg("field", "categories." + change.category().name());
                }
                continue;
            }
            CategorySwitches before = repository.findByUserIdAndCategory(userId, change.category())
                    .map(row -> new CategorySwitches(change.category(), row.isMail(), row.isPush(), row.isTelegram()))
                    .orElseGet(() -> defaults(change.category()));
            if (before.equals(change)) {
                continue;
            }
            UserNotificationSetting row = repository.findByUserIdAndCategory(userId, change.category())
                    .orElseGet(() -> {
                        UserNotificationSetting created = new UserNotificationSetting();
                        created.setUserId(userId);
                        created.setCategory(change.category());
                        return created;
                    });
            row.setMail(change.mail());
            row.setPush(change.push());
            row.setTelegram(change.telegram());
            repository.save(row);
            changed.put(change.category().name(), "mail=" + change.mail() + ",push=" + change.push()
                    + ",telegram=" + change.telegram());
        }
        if (!changed.isEmpty()) {
            history.record(history.event(NotificationAction.SETTINGS_CHANGED, HistoryService.SYSTEM_SUBJECT_ID)
                    .secondarySubject(userId).metadata(changed).build());
        }
        return settings(userId);
    }
}
