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
import network.foodsharing.common.mapper.PageMapper;
import network.foodsharing.intropickup.mapper.IntroPickupMapper;
import network.foodsharing.intropickup.service.IntroPickupAdminService;
import network.foodsharing.intropickup.service.IntroPickupViews;
import network.foodsharing.intropickup.service.PastImportService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** EinAb administration of a region (plan §14.1 EA17–EA19). */
@RestController
public class IntroPickupAdminController implements IntroPickupAdminApi {

    private final IntroPickupAdminService admin;
    private final PastImportService pastImport;
    private final IntroPickupMapper mapper;

    public IntroPickupAdminController(IntroPickupAdminService admin, PastImportService pastImport,
                                      IntroPickupMapper mapper) {
        this.admin = admin;
        this.pastImport = pastImport;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<SlotComment> addAdminComment(UUID slotId, SlotCommentCreate create) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toComment(admin.addAdminComment(slotId,
                create.getComment())));
    }

    @Override
    public ResponseEntity<PastImportResult> confirmPastImport(UUID regionId, PastImportRequest request) {
        return ResponseEntity.ok(mapper.toResult(pastImport.confirm(regionId, request.getCsv())));
    }

    @Override
    public ResponseEntity<GuideBooking> createHistoricalIntroPickup(UUID regionId, UUID userId,
                                                                    HistoricalIntroPickupCreate create) {
        var booking = admin.createHistorical(regionId, userId, new IntroPickupAdminService.HistoricalInput(
                IntroPickupMapper.category(create.getCategory()),
                create.getStartAt() == null ? null : create.getStartAt().toInstant(), create.getBusinessName(),
                Boolean.TRUE.equals(create.getVisitFairteiler()), create.getComment()));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toGuideBooking(booking));
    }

    @Override
    public ResponseEntity<StoreAlias> createStoreAlias(UUID regionId, StoreAliasUpdate update) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toAlias(admin.createAlias(regionId,
                update.getAliasKey(), update.getStoreName())));
    }

    @Override
    public ResponseEntity<Void> deleteIntroPickupAsAdmin(UUID regionId, UUID introPickupId, ReasonBody body) {
        admin.delete(regionId, introPickupId, body == null ? null : body.getReason());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> deleteStoreAlias(UUID regionId, UUID aliasId) {
        admin.deleteAlias(regionId, aliasId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<GuideSummaryPage> listGuides(UUID regionId, Integer page, Integer size) {
        Page<IntroPickupAdminService.GuideSummaryView> result = admin.guides(regionId, page, size);
        GuideSummaryPage model = new GuideSummaryPage();
        model.setItems(result.getContent().stream().map(mapper::toGuideSummary).toList());
        model.setPage(PageMapper.toPageMeta(result));
        return ResponseEntity.ok(model);
    }

    @Override
    public ResponseEntity<GuideBookingPage> listRegionBookings(UUID regionId, Boolean past, UUID userId, Integer page,
                                                               Integer size) {
        Page<IntroPickupViews.BookingView> result = admin.bookings(regionId, Boolean.TRUE.equals(past), userId, page,
                size);
        GuideBookingPage model = new GuideBookingPage();
        model.setItems(result.getContent().stream().map(mapper::toGuideBooking).toList());
        model.setPage(PageMapper.toPageMeta(result));
        return ResponseEntity.ok(model);
    }

    @Override
    public ResponseEntity<IntroPickupDetailPage> listRegionIntroPickups(UUID regionId, Boolean past, Integer page,
                                                                       Integer size) {
        Page<IntroPickupViews.PickupView> result = admin.pickups(regionId, Boolean.TRUE.equals(past), page, size);
        IntroPickupDetailPage model = new IntroPickupDetailPage();
        model.setItems(result.getContent().stream().map(mapper::toDetail).toList());
        model.setPage(PageMapper.toPageMeta(result));
        return ResponseEntity.ok(model);
    }

    @Override
    public ResponseEntity<List<StoreAlias>> listStoreAliases(UUID regionId) {
        return ResponseEntity.ok(admin.aliases(regionId).stream().map(mapper::toAlias).toList());
    }

    @Override
    public ResponseEntity<PastImportPreview> previewPastImport(UUID regionId, PastImportRequest request) {
        return ResponseEntity.ok(mapper.toPreview(pastImport.preview(regionId, request.getCsv())));
    }

    @Override
    public ResponseEntity<GuideBooking> setBookingCounted(UUID slotId, BookingCountedUpdate update) {
        return ResponseEntity.ok(mapper.toGuideBooking(admin.setCounted(slotId,
                Boolean.TRUE.equals(update.getCounted()), update.getReason())));
    }

    @Override
    public ResponseEntity<GuideSummary> setGuideEnabled(UUID regionId, UUID userId, GuideEnabledUpdate update) {
        return ResponseEntity.ok(mapper.toGuideSummary(admin.setGuideEnabled(regionId, userId,
                Boolean.TRUE.equals(update.getEnabled()), update.getReason())));
    }

    @Override
    public ResponseEntity<StoreAlias> updateStoreAlias(UUID regionId, UUID aliasId, StoreAliasUpdate update) {
        return ResponseEntity.ok(mapper.toAlias(admin.updateAlias(regionId, aliasId, update.getAliasKey(),
                update.getStoreName())));
    }
}
