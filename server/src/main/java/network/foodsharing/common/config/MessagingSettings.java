package network.foodsharing.common.config;

/**
 * Messaging settings that ORGA edits on the admin settings page (decision 146). Lives in {@code common} so that
 * {@code messaging} does not depend on {@code admin}; the admin module implements it on top of its configuration
 * table ({@code admin.service.MessagingSettingsAdapter}).
 */
public interface MessagingSettings {

    /** Hours after sending in which the sender may edit or delete a chat message ({@code messaging.edit-window-hours}). */
    int editWindowHours();
}
