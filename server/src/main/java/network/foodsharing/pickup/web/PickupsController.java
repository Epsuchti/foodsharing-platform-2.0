package network.foodsharing.pickup.web;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.PickupsApi;
import network.foodsharing.api.model.AgendaDay;
import network.foodsharing.api.model.OptionalMessage;
import network.foodsharing.api.model.Pickup;
import network.foodsharing.api.model.PickupEdit;
import network.foodsharing.api.model.PickupEligibility;
import network.foodsharing.api.model.PickupHistoryPage;
import network.foodsharing.api.model.PickupOption;
import network.foodsharing.api.model.ReasonBody;
import network.foodsharing.api.model.RegularPickup;
import network.foodsharing.api.model.SignupResult;
import network.foodsharing.api.model.UserPickup;
import network.foodsharing.api.model.UserPickupPage;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pickup API (B4, B5).
 * Operations answer 501 NOT_IMPLEMENTED until their task implements them (docs/plan-m3.md).
 */
@RestController
public class PickupsController implements PickupsApi {

    @Override
    public ResponseEntity<PickupEligibility> checkEligibility(UUID storeId, OffsetDateTime pickupAt) {
        throw ApiException.notImplemented("checkEligibility");
    }

    @Override
    public ResponseEntity<Void> confirmSignup(UUID userId, UUID storeId, OffsetDateTime pickupAt) {
        throw ApiException.notImplemented("confirmSignup");
    }

    @Override
    public ResponseEntity<Void> deleteAllFuturePickups(UUID userId, ReasonBody reasonBody) {
        throw ApiException.notImplemented("deleteAllFuturePickups");
    }

    @Override
    public ResponseEntity<Pickup> editPickup(UUID storeId, OffsetDateTime pickupAt, PickupEdit pickupEdit) {
        throw ApiException.notImplemented("editPickup");
    }

    @Override
    public ResponseEntity<AgendaDay> getAgenda(UUID userId, LocalDate date) {
        throw ApiException.notImplemented("getAgenda");
    }

    @Override
    public ResponseEntity<List<RegularPickup>> getRegularPickups(UUID storeId) {
        throw ApiException.notImplemented("getRegularPickups");
    }

    @Override
    public ResponseEntity<PickupHistoryPage> getStorePickupHistory(UUID storeId, OffsetDateTime from, OffsetDateTime to, Integer page, Integer size) {
        throw ApiException.notImplemented("getStorePickupHistory");
    }

    @Override
    public ResponseEntity<List<UserPickup>> listMyPickups() {
        throw ApiException.notImplemented("listMyPickups");
    }

    @Override
    public ResponseEntity<UserPickupPage> listPastPickups(UUID userId, Integer page, Integer size) {
        throw ApiException.notImplemented("listPastPickups");
    }

    @Override
    public ResponseEntity<List<PickupOption>> listPickupOptions() {
        throw ApiException.notImplemented("listPickupOptions");
    }

    @Override
    public ResponseEntity<List<Pickup>> listPickups(UUID storeId, OffsetDateTime from, OffsetDateTime to) {
        throw ApiException.notImplemented("listPickups");
    }

    @Override
    public ResponseEntity<List<RegularPickup>> replaceRegularPickups(UUID storeId, List<RegularPickup> regularPickup) {
        throw ApiException.notImplemented("replaceRegularPickups");
    }

    @Override
    public ResponseEntity<Void> signOut(UUID userId, UUID storeId, OffsetDateTime pickupAt, OptionalMessage optionalMessage) {
        throw ApiException.notImplemented("signOut");
    }

    @Override
    public ResponseEntity<SignupResult> signUp(UUID storeId, OffsetDateTime pickupAt) {
        throw ApiException.notImplemented("signUp");
    }
}
