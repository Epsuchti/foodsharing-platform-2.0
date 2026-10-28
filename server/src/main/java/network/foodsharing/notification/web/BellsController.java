package network.foodsharing.notification.web;

import java.util.UUID;
import network.foodsharing.api.BellsApi;
import network.foodsharing.api.model.BellCount;
import network.foodsharing.api.model.BellIds;
import network.foodsharing.api.model.BellPage;
import network.foodsharing.api.model.NotificationSettings;
import network.foodsharing.api.model.NotificationSettingsUpdate;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bells and notification settings API (M4 B2).
 * Operations answer 501 NOT_IMPLEMENTED until their task implements them (docs/plan-m4.md).
 */
@RestController
public class BellsController implements BellsApi {

    @Override
    public ResponseEntity<BellCount> countUnreadBells() {
        throw ApiException.notImplemented("countUnreadBells");
    }

    @Override
    public ResponseEntity<Void> deleteBell(UUID bellId) {
        throw ApiException.notImplemented("deleteBell");
    }

    @Override
    public ResponseEntity<Void> deleteBells(Boolean readOnly) {
        throw ApiException.notImplemented("deleteBells");
    }

    @Override
    public ResponseEntity<NotificationSettings> getNotificationSettings() {
        throw ApiException.notImplemented("getNotificationSettings");
    }

    @Override
    public ResponseEntity<BellPage> listBells(Boolean unreadOnly, Integer page, Integer size) {
        throw ApiException.notImplemented("listBells");
    }

    @Override
    public ResponseEntity<Void> markAllBellsRead() {
        throw ApiException.notImplemented("markAllBellsRead");
    }

    @Override
    public ResponseEntity<Void> markBellsRead(BellIds bellIds) {
        throw ApiException.notImplemented("markBellsRead");
    }

    @Override
    public ResponseEntity<NotificationSettings> updateNotificationSettings(NotificationSettingsUpdate update) {
        throw ApiException.notImplemented("updateNotificationSettings");
    }
}
