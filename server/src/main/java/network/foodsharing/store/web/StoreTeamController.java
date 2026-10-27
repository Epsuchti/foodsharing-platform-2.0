package network.foodsharing.store.web;

import java.util.UUID;
import network.foodsharing.api.StoreTeamApi;
import network.foodsharing.api.model.RequiredMessage;
import network.foodsharing.api.model.StoreApplicationCreate;
import network.foodsharing.api.model.StoreTeam;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Store team API (B3).
 * Operations answer 501 NOT_IMPLEMENTED until their task implements them (docs/plan-m3.md).
 */
@RestController
public class StoreTeamController implements StoreTeamApi {

    @Override
    public ResponseEntity<Void> acceptApplication(UUID userId, UUID storeId, Boolean moveToStandby) {
        throw ApiException.notImplemented("acceptApplication");
    }

    @Override
    public ResponseEntity<Void> acceptInvitation(UUID storeId) {
        throw ApiException.notImplemented("acceptInvitation");
    }

    @Override
    public ResponseEntity<Void> addMemberDirectly(UUID userId, UUID storeId, Boolean standby) {
        throw ApiException.notImplemented("addMemberDirectly");
    }

    @Override
    public ResponseEntity<Void> applyToStore(UUID storeId, StoreApplicationCreate storeApplicationCreate) {
        throw ApiException.notImplemented("applyToStore");
    }

    @Override
    public ResponseEntity<Void> appointManager(UUID userId, UUID storeId) {
        throw ApiException.notImplemented("appointManager");
    }

    @Override
    public ResponseEntity<Void> cancelMyStoreApplication(UUID storeId) {
        throw ApiException.notImplemented("cancelMyStoreApplication");
    }

    @Override
    public ResponseEntity<Void> declineApplication(UUID userId, UUID storeId, RequiredMessage requiredMessage) {
        throw ApiException.notImplemented("declineApplication");
    }

    @Override
    public ResponseEntity<Void> declineInvitation(UUID storeId) {
        throw ApiException.notImplemented("declineInvitation");
    }

    @Override
    public ResponseEntity<StoreTeam> getStoreTeam(UUID storeId) {
        throw ApiException.notImplemented("getStoreTeam");
    }

    @Override
    public ResponseEntity<Void> inviteUser(UUID userId, UUID storeId) {
        throw ApiException.notImplemented("inviteUser");
    }

    @Override
    public ResponseEntity<Void> moveToRegularTeam(UUID userId, UUID storeId) {
        throw ApiException.notImplemented("moveToRegularTeam");
    }

    @Override
    public ResponseEntity<Void> moveToStandby(UUID userId, UUID storeId, RequiredMessage requiredMessage) {
        throw ApiException.notImplemented("moveToStandby");
    }

    @Override
    public ResponseEntity<Void> removeManager(UUID userId, UUID storeId, RequiredMessage requiredMessage) {
        throw ApiException.notImplemented("removeManager");
    }

    @Override
    public ResponseEntity<Void> removeMember(UUID userId, UUID storeId, RequiredMessage requiredMessage) {
        throw ApiException.notImplemented("removeMember");
    }

    @Override
    public ResponseEntity<Void> withdrawInvitation(UUID userId, UUID storeId) {
        throw ApiException.notImplemented("withdrawInvitation");
    }
}
