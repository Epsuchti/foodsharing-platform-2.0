package network.foodsharing.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** A23: every type has a category and an icon; only SYSTEM cannot be switched off. */
class NotificationTypeTest {

    @Test
    void everyTypeHasCategoryAndIcon() {
        assertThat(NotificationType.values()).allSatisfy(type -> {
            assertThat(type.category()).isNotNull();
            assertThat(type.icon()).isNotBlank();
            assertThat(type.name().length()).isLessThanOrEqualTo(60); // bell.type column
        });
    }

    @Test
    void onlySystemIsNotSwitchable() {
        assertThat(Arrays.stream(NotificationCategory.values()).filter(c -> !c.switchable()))
                .containsExactly(NotificationCategory.SYSTEM);
        assertThat(NotificationType.PICKUP_REMINDER.category()).isEqualTo(NotificationCategory.PICKUP_REMINDER);
        assertThat(NotificationType.PICKUP_AUTO_DECLINED.category()).isEqualTo(NotificationCategory.AUTOMATION);
    }
}
