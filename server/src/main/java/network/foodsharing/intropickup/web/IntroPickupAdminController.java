package network.foodsharing.intropickup.web;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.IntroPickupAdminApi;
import network.foodsharing.api.model.BookingCountedUpdate;
import network.foodsharing.api.model.GuideBooking;
import network.foodsharing.api.model.GuideBookingPage;
import network.foodsharing.api.model.GuideEnabledUpdate;
import network.foodsharing.api.model.GuideSummary;
import network.foodsharing.api.model.GuideSummaryPage;
import network.foodsharing.api.model.HistoricalIntroPickupCreate;
import network.foodsharing.api.model.IntroPickupDetailPage;
import network.foodsharing.api.model.PastImportPreview;
import network.foodsharing.api.model.PastImportRequest;
import network.foodsharing.api.model.PastImportResult;
import network.foodsharing.api.model.ReasonBody;
import network.foodsharing.api.model.SlotComment;
import network.foodsharing.api.model.SlotCommentCreate;
import network.foodsharing.api.model.StoreAlias;
import network.foodsharing.api.model.StoreAliasUpdate;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * EinAb admin API (implemented in T15).
 * Operations answer 501 NOT_IMPLEMENTED until their task implements them (docs/plan-m2.md).
 */
@RestController
public class IntroPickupAdminController implements IntroPickupAdminApi {

    @Override
    public ResponseEntity<SlotComment> addAdminComment(UUID slotId, SlotCommentCreate slotCommentCreate) {
        throw ApiException.notImplemented("addAdminComment");
    }

    @Override
    public ResponseEntity<PastImportResult> confirmPastImport(UUID regionId, PastImportRequest pastImportRequest) {
        throw ApiException.notImplemented("confirmPastImport");
    }

    @Override
    public ResponseEntity<GuideBooking> createHistoricalIntroPickup(UUID regionId, UUID userId, HistoricalIntroPickupCreate historicalIntroPickupCreate) {
        throw ApiException.notImplemented("createHistoricalIntroPickup");
    }

    @Override
    public ResponseEntity<StoreAlias> createStoreAlias(UUID regionId, StoreAliasUpdate storeAliasUpdate) {
        throw ApiException.notImplemented("createStoreAlias");
    }

    @Override
    public ResponseEntity<Void> deleteIntroPickupAsAdmin(UUID regionId, UUID introPickupId, ReasonBody reasonBody) {
        throw ApiException.notImplemented("deleteIntroPickupAsAdmin");
    }

    @Override
    public ResponseEntity<Void> deleteStoreAlias(UUID regionId, UUID aliasId) {
        throw ApiException.notImplemented("deleteStoreAlias");
    }

    @Override
    public ResponseEntity<GuideSummaryPage> listGuides(UUID regionId, Integer page, Integer size) {
        throw ApiException.notImplemented("listGuides");
    }

    @Override
    public ResponseEntity<GuideBookingPage> listRegionBookings(UUID regionId, Boolean past, UUID userId, Integer page, Integer size) {
        throw ApiException.notImplemented("listRegionBookings");
    }

    @Override
    public ResponseEntity<IntroPickupDetailPage> listRegionIntroPickups(UUID regionId, Boolean past, Integer page, Integer size) {
        throw ApiException.notImplemented("listRegionIntroPickups");
    }

    @Override
    public ResponseEntity<List<StoreAlias>> listStoreAliases(UUID regionId) {
        throw ApiException.notImplemented("listStoreAliases");
    }

    @Override
    public ResponseEntity<PastImportPreview> previewPastImport(UUID regionId, PastImportRequest pastImportRequest) {
        throw ApiException.notImplemented("previewPastImport");
    }

    @Override
    public ResponseEntity<GuideBooking> setBookingCounted(UUID slotId, BookingCountedUpdate bookingCountedUpdate) {
        throw ApiException.notImplemented("setBookingCounted");
    }

    @Override
    public ResponseEntity<GuideSummary> setGuideEnabled(UUID regionId, UUID userId, GuideEnabledUpdate guideEnabledUpdate) {
        throw ApiException.notImplemented("setGuideEnabled");
    }

    @Override
    public ResponseEntity<StoreAlias> updateStoreAlias(UUID regionId, UUID aliasId, StoreAliasUpdate storeAliasUpdate) {
        throw ApiException.notImplemented("updateStoreAlias");
    }
}
