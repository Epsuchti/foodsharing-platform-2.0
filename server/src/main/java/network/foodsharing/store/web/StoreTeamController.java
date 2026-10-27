package network.foodsharing.store.web;

import network.foodsharing.store.service.StoreTeamService;
import network.foodsharing.store.mapper.StoreMapper;
import java.util.UUID;
import network.foodsharing.api.StoreTeamApi;
import network.foodsharing.api.model.RequiredMessage;
import network.foodsharing.api.model.StoreApplicationCreate;
import network.foodsharing.api.model.StoreTeam;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Store team API (B3).
 * Operations answer 501 NOT_IMPLEMENTED until their task implements them (docs/plan-m3.md).
 */
@RestController
public class StoreTeamController implements StoreTeamApi {

    private final StoreTeamService team;
    private final StoreMapper mapper;

    public StoreTeamController(StoreTeamService team, StoreMapper mapper) {
        this.team = team;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<Void> acceptApplication(UUID userId, UUID storeId, Boolean moveToStandby) {
        team.accept(storeId, userId, Boolean.TRUE.equals(moveToStandby));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> acceptInvitation(UUID storeId) {
        team.acceptInvitation(storeId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> addMemberDirectly(UUID userId, UUID storeId, Boolean standby) {
        team.addDirectly(storeId, userId, Boolean.TRUE.equals(standby));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> applyToStore(UUID storeId, StoreApplicationCreate storeApplicationCreate) {
        team.apply(storeId, storeApplicationCreate == null ? null : storeApplicationCreate.getText());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> appointManager(UUID userId, UUID storeId) {
        team.appointManager(storeId, userId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> cancelMyStoreApplication(UUID storeId) {
        team.cancelApplication(storeId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> declineApplication(UUID userId, UUID storeId, RequiredMessage requiredMessage) {
        team.decline(storeId, userId, requiredMessage == null ? null : requiredMessage.getMessage());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> declineInvitation(UUID storeId) {
        team.declineInvitation(storeId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<StoreTeam> getStoreTeam(UUID storeId) {
        return ResponseEntity.ok(mapper.toTeam(team.team(storeId)));
    }

    @Override
    public ResponseEntity<Void> inviteUser(UUID userId, UUID storeId) {
        team.invite(storeId, userId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> moveToRegularTeam(UUID userId, UUID storeId) {
        team.moveToTeam(storeId, userId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> moveToStandby(UUID userId, UUID storeId, RequiredMessage requiredMessage) {
        team.moveToStandby(storeId, userId, requiredMessage == null ? null : requiredMessage.getMessage());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> removeManager(UUID userId, UUID storeId, RequiredMessage requiredMessage) {
        team.removeManager(storeId, userId, requiredMessage == null ? null : requiredMessage.getMessage());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> removeMember(UUID userId, UUID storeId, RequiredMessage requiredMessage) {
        team.remove(storeId, userId, requiredMessage == null ? null : requiredMessage.getMessage());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> withdrawInvitation(UUID userId, UUID storeId) {
        team.withdrawInvitation(storeId, userId);
        return ResponseEntity.noContent().build();
    }
}
