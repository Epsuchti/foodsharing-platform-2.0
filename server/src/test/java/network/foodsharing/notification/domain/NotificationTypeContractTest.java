package network.foodsharing.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** The contract's {@code NotificationType} enum lists exactly the domain types (the client tests i18n against it). */
class NotificationTypeContractTest {

    @Test
    void contractEnumMatchesDomainEnum() {
        assertThat(Arrays.stream(network.foodsharing.api.model.NotificationType.values()).map(Enum::name))
                .containsExactlyElementsOf(Arrays.stream(NotificationType.values()).map(Enum::name).toList());
    }
}
