package network.foodsharing.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import network.foodsharing.admin.domain.ConfigurationKey;
import org.junit.jupiter.api.Test;

/** Decision 146: the chat edit window comes from the admin configuration (default 24 h, never negative). */
class MessagingSettingsAdapterTest {

    private final ConfigurationService configuration = mock(ConfigurationService.class);
    private final MessagingSettingsAdapter settings = new MessagingSettingsAdapter(configuration);

    @Test
    void readsTheEditWindowFromTheConfiguration() {
        assertThat(ConfigurationKey.MESSAGING_EDIT_WINDOW_HOURS.key()).isEqualTo("messaging.edit-window-hours");
        assertThat(ConfigurationKey.MESSAGING_EDIT_WINDOW_HOURS.defaultValue()).isEqualTo("24");
        when(configuration.integer(ConfigurationKey.MESSAGING_EDIT_WINDOW_HOURS)).thenReturn(48);
        assertThat(settings.editWindowHours()).isEqualTo(48);
        when(configuration.integer(ConfigurationKey.MESSAGING_EDIT_WINDOW_HOURS)).thenReturn(-3);
        assertThat(settings.editWindowHours()).isZero();
    }
}
