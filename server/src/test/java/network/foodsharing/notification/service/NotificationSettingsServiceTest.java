package network.foodsharing.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationCategory;
import network.foodsharing.notification.domain.NotificationChannelType;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.domain.UserNotificationSetting;
import network.foodsharing.notification.repository.UserNotificationSettingRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** A23: defaults per category, stored rows override them, SYSTEM is not switchable. */
class NotificationSettingsServiceTest {

    private final UserNotificationSettingRepository repository = mock(UserNotificationSettingRepository.class);
    private final HistoryService history = mock(HistoryService.class, Mockito.RETURNS_DEEP_STUBS);
    private final NotificationSettingsService service = new NotificationSettingsService(repository, history);
    private final UUID userId = UUID.randomUUID();

    @Test
    void defaultsMailEverywhereExceptForumAndMessages() {
        assertThat(service.settings(userId)).hasSize(NotificationCategory.values().length)
                .allSatisfy(s -> assertThat(s.mail()).isEqualTo(s.category() != NotificationCategory.FORUM
                        && s.category() != NotificationCategory.MESSAGES))
                .allSatisfy(s -> assertThat(s.push() && s.telegram()).isTrue());
        assertThat(service.isEnabled(userId, NotificationType.FORUM_POST, NotificationChannelType.MAIL)).isFalse();
        assertThat(service.isEnabled(userId, NotificationType.PICKUP_CONFIRMED, NotificationChannelType.MAIL)).isTrue();
    }

    @Test
    void storedRowsWinAndSystemIsAlwaysOn() {
        UserNotificationSetting row = new UserNotificationSetting();
        row.setUserId(userId);
        row.setCategory(NotificationCategory.PICKUPS);
        row.setMail(false);
        row.setPush(true);
        when(repository.findByUserIdAndCategory(userId, NotificationCategory.PICKUPS)).thenReturn(Optional.of(row));

        assertThat(service.isEnabled(userId, NotificationType.PICKUP_CONFIRMED, NotificationChannelType.MAIL)).isFalse();
        assertThat(service.isEnabled(userId, NotificationType.PICKUP_CONFIRMED, NotificationChannelType.TELEGRAM))
                .isFalse();
        assertThat(service.isEnabled(userId, NotificationType.USER_VERIFIED, NotificationChannelType.MAIL)).isTrue();
    }

    @Test
    void systemCannotBeSwitchedOff() {
        var off = new NotificationSettingsService.CategorySwitches(NotificationCategory.SYSTEM, false, true, true);
        assertThatThrownBy(() -> service.update(userId, List.of(off))).isInstanceOf(ApiException.class)
                .hasMessageContaining("VALIDATION_FAILED");
        verify(repository, never()).save(any());
    }

    @Test
    void onlyChangedCategoriesAreStored() {
        when(repository.findByUserIdAndCategory(any(), any())).thenReturn(Optional.empty());
        service.update(userId, List.of(NotificationSettingsService.defaults(NotificationCategory.REGION),
                new NotificationSettingsService.CategorySwitches(NotificationCategory.FORUM, true, false, false),
                NotificationSettingsService.defaults(NotificationCategory.SYSTEM)));
        verify(repository, Mockito.times(1)).save(any());
    }
}
