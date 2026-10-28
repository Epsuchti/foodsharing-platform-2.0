package network.foodsharing.notification.web;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.BellsApi;
import network.foodsharing.api.model.BellCount;
import network.foodsharing.api.model.BellIds;
import network.foodsharing.api.model.BellPage;
import network.foodsharing.api.model.CategorySetting;
import network.foodsharing.api.model.NotificationSettings;
import network.foodsharing.api.model.NotificationSettingsUpdate;
import network.foodsharing.common.mapper.PageMapper;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.config.AppProperties;
import network.foodsharing.notification.domain.Bell;
import network.foodsharing.notification.domain.NotificationCategory;
import network.foodsharing.notification.push.service.PushSubscriptionService;
import network.foodsharing.notification.service.BellService;
import network.foodsharing.notification.service.NotificationSettingsService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Bells (N3) and notification channel settings (N4) of the current user. */
@RestController
public class BellsController implements BellsApi {

    private final BellService bells;
    private final NotificationSettingsService settings;
    private final PushSubscriptionService pushSubscriptions;
    private final UserService users;
    private final CurrentActorService currentActor;
    private final AppProperties properties;

    public BellsController(BellService bells, NotificationSettingsService settings,
                           PushSubscriptionService pushSubscriptions, UserService users,
                           CurrentActorService currentActor, AppProperties properties) {
        this.bells = bells;
        this.settings = settings;
        this.pushSubscriptions = pushSubscriptions;
        this.users = users;
        this.currentActor = currentActor;
        this.properties = properties;
    }

    @Override
    public ResponseEntity<BellPage> listBells(Boolean unreadOnly, Integer page, Integer size) {
        UUID userId = currentActor.requireUserId();
        Page<Bell> result = bells.page(userId, Boolean.TRUE.equals(unreadOnly), page == null ? 0 : page,
                size == null ? 20 : size);
        return ResponseEntity.ok(new BellPage()
                .items(result.getContent().stream().map(this::toApi).toList())
                .page(PageMapper.toPageMeta(result)));
    }

    @Override
    public ResponseEntity<BellCount> countUnreadBells() {
        return ResponseEntity.ok(new BellCount().count((int) bells.unreadCount(currentActor.requireUserId())));
    }

    @Override
    public ResponseEntity<Void> markBellsRead(BellIds bellIds) {
        bells.markRead(currentActor.requireUserId(), bellIds.getIds());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> markAllBellsRead() {
        bells.markAllRead(currentActor.requireUserId());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> deleteBell(UUID bellId) {
        bells.delete(currentActor.requireUserId(), bellId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> deleteBells(Boolean readOnly) {
        bells.deleteAll(currentActor.requireUserId(), Boolean.TRUE.equals(readOnly));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<NotificationSettings> getNotificationSettings() {
        User user = currentActor.requireUser();
        return ResponseEntity.ok(toApi(user, settings.settings(user.getId())));
    }

    @Override
    public ResponseEntity<NotificationSettings> updateNotificationSettings(NotificationSettingsUpdate update) {
        User user = currentActor.requireUser();
        List<NotificationSettingsService.CategorySwitches> changes = update.getCategories().stream()
                .map(item -> new NotificationSettingsService.CategorySwitches(
                        NotificationCategory.valueOf(item.getCategory().name()), Boolean.TRUE.equals(item.getMail()),
                        Boolean.TRUE.equals(item.getPush()), Boolean.TRUE.equals(item.getTelegram())))
                .toList();
        List<NotificationSettingsService.CategorySwitches> result = settings.update(user.getId(), changes);
        if (update.getNewsletterOptIn() != null) {
            user = users.setNewsletterOptIn(update.getNewsletterOptIn());
        }
        return ResponseEntity.ok(toApi(user, result));
    }

    private NotificationSettings toApi(User user, List<NotificationSettingsService.CategorySwitches> switches) {
        AppProperties.Telegram telegram = properties.getTelegram();
        boolean telegramConfigured = "log".equals(telegram.getMode()) || !telegram.getBotToken().isBlank();
        return new NotificationSettings()
                .categories(switches.stream().map(s -> new CategorySetting()
                        .category(network.foodsharing.api.model.NotificationCategory.valueOf(s.category().name()))
                        .bell(true).mail(s.mail()).push(s.push()).telegram(s.telegram())).toList())
                .pushAvailable(pushSubscriptions.hasSubscription(user.getId()))
                .telegramLinked(user.getTelegramChatId() != null)
                .telegramBotName(telegramConfigured ? telegram.getBotName() : null)
                .newsletterOptIn(user.isNewsletterOptIn());
    }

    private network.foodsharing.api.model.Bell toApi(Bell bell) {
        return new network.foodsharing.api.model.Bell()
                .id(bell.getId())
                .type(bell.getType().name())
                .titleKey(bell.getTitleKey()).identifier(bell.getIdentifier())
                .params(new java.util.LinkedHashMap<>(bells.params(bell)))
                .link(bell.getLink())
                .icon(bell.getIcon() == null ? bell.getType().icon() : bell.getIcon())
                .createdAt(TimeMapper.toOffset(bell.getCreatedAt()))
                .readAt(bell.getReadAt() == null ? null : TimeMapper.toOffset(bell.getReadAt()))
                .mailed(bell.getMailOutboxId() != null)
                .pushed(bell.getPushedAt() != null);
    }
}
