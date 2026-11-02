package network.foodsharing.admin.service;

import network.foodsharing.admin.domain.ConfigurationKey;
import network.foodsharing.common.config.MessagingSettings;
import org.springframework.stereotype.Component;

/** {@link MessagingSettings} from the configuration table (editable on the admin settings page). */
@Component
public class MessagingSettingsAdapter implements MessagingSettings {

    private final ConfigurationService configuration;

    public MessagingSettingsAdapter(ConfigurationService configuration) {
        this.configuration = configuration;
    }

    @Override
    public int editWindowHours() {
        return Math.max(0, configuration.integer(ConfigurationKey.MESSAGING_EDIT_WINDOW_HOURS));
    }
}
