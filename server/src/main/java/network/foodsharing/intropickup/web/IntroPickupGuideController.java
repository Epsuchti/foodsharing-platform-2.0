package network.foodsharing.intropickup.web;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.IntroPickupGuideApi;
import network.foodsharing.api.model.AssignGuideRequest;
import network.foodsharing.api.model.BookForUserRequest;
import network.foodsharing.api.model.FeedLink;
import network.foodsharing.api.model.GuideBooking;
import network.foodsharing.api.model.GuideBookingPage;
import network.foodsharing.api.model.GuideProfile;
import network.foodsharing.api.model.GuideProfileUpdate;
import network.foodsharing.api.model.IcalCandidate;
import network.foodsharing.api.model.IntroPickupCategory;
import network.foodsharing.api.model.IntroPickupCreate;
import network.foodsharing.api.model.IntroPickupDetail;
import network.foodsharing.api.model.IntroPickupDetailPage;
import network.foodsharing.api.model.IntroPickupUpdate;
import network.foodsharing.api.model.NewcomerFunnel;
import network.foodsharing.api.model.OptionalReasonBody;
import network.foodsharing.api.model.PrivateInfoTemplate;
import network.foodsharing.api.model.RegionRef;
import network.foodsharing.api.model.SlotComment;
import network.foodsharing.api.model.SlotCommentCreate;
import network.foodsharing.api.model.UserSummary;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * EinAb guide API (implemented in T15).
 * Operations answer 501 NOT_IMPLEMENTED until their task implements them (docs/plan-m2.md).
 */
@RestController
public class IntroPickupGuideController implements IntroPickupGuideApi {

    @Override
    public ResponseEntity<SlotComment> addSlotComment(UUID slotId, SlotCommentCreate slotCommentCreate) {
        throw ApiException.notImplemented("addSlotComment");
    }

    @Override
    public ResponseEntity<IntroPickupDetail> assignGuide(UUID introPickupId, AssignGuideRequest assignGuideRequest) {
        throw ApiException.notImplemented("assignGuide");
    }

    @Override
    public ResponseEntity<GuideBooking> bookSlotForUser(UUID slotId, BookForUserRequest bookForUserRequest) {
        throw ApiException.notImplemented("bookSlotForUser");
    }

    @Override
    public ResponseEntity<Void> cancelBookingAsGuide(UUID slotId, OptionalReasonBody optionalReasonBody) {
        throw ApiException.notImplemented("cancelBookingAsGuide");
    }

    @Override
    public ResponseEntity<GuideBooking> countCancelledBooking(UUID slotId) {
        throw ApiException.notImplemented("countCancelledBooking");
    }

    @Override
    public ResponseEntity<IntroPickupDetail> createIntroPickup(UUID regionId, IntroPickupCreate introPickupCreate) {
        throw ApiException.notImplemented("createIntroPickup");
    }

    @Override
    public ResponseEntity<Void> deleteIntroPickup(UUID introPickupId) {
        throw ApiException.notImplemented("deleteIntroPickup");
    }

    @Override
    public ResponseEntity<Void> deletePrivateInfoTemplate(IntroPickupCategory category) {
        throw ApiException.notImplemented("deletePrivateInfoTemplate");
    }

    @Override
    public ResponseEntity<FeedLink> getGuideFeedLink() {
        throw ApiException.notImplemented("getGuideFeedLink");
    }

    @Override
    public ResponseEntity<GuideProfile> getGuideProfile() {
        throw ApiException.notImplemented("getGuideProfile");
    }

    @Override
    public ResponseEntity<List<IcalCandidate>> getIcalCandidates() {
        throw ApiException.notImplemented("getIcalCandidates");
    }

    @Override
    public ResponseEntity<IntroPickupDetail> getIntroPickup(UUID introPickupId) {
        throw ApiException.notImplemented("getIntroPickup");
    }

    @Override
    public ResponseEntity<NewcomerFunnel> getNewcomerFunnel(UUID regionId, Integer days) {
        throw ApiException.notImplemented("getNewcomerFunnel");
    }

    @Override
    public ResponseEntity<List<UserSummary>> listAssignableGuides(UUID regionId) {
        throw ApiException.notImplemented("listAssignableGuides");
    }

    @Override
    public ResponseEntity<GuideBookingPage> listGuideBookings(UUID regionId, Boolean past, Integer page, Integer size) {
        throw ApiException.notImplemented("listGuideBookings");
    }

    @Override
    public ResponseEntity<IntroPickupDetailPage> listGuideIntroPickups(UUID regionId, Boolean past, Integer page, Integer size) {
        throw ApiException.notImplemented("listGuideIntroPickups");
    }

    @Override
    public ResponseEntity<List<RegionRef>> listGuideRegions() {
        throw ApiException.notImplemented("listGuideRegions");
    }

    @Override
    public ResponseEntity<List<PrivateInfoTemplate>> listPrivateInfoTemplates() {
        throw ApiException.notImplemented("listPrivateInfoTemplates");
    }

    @Override
    public ResponseEntity<List<SlotComment>> listSlotComments(UUID slotId) {
        throw ApiException.notImplemented("listSlotComments");
    }

    @Override
    public ResponseEntity<GuideBooking> markBookingDone(UUID slotId) {
        throw ApiException.notImplemented("markBookingDone");
    }

    @Override
    public ResponseEntity<GuideProfile> updateGuideProfile(GuideProfileUpdate guideProfileUpdate) {
        throw ApiException.notImplemented("updateGuideProfile");
    }

    @Override
    public ResponseEntity<IntroPickupDetail> updateIntroPickup(UUID introPickupId, IntroPickupUpdate introPickupUpdate) {
        throw ApiException.notImplemented("updateIntroPickup");
    }
}
