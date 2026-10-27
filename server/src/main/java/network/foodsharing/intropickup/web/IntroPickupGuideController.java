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
import network.foodsharing.common.mapper.PageMapper;
import network.foodsharing.intropickup.mapper.IntroPickupMapper;
import network.foodsharing.intropickup.service.GuideProfileService;
import network.foodsharing.intropickup.service.IntroPickupBookingService;
import network.foodsharing.intropickup.service.IntroPickupGuideService;
import network.foodsharing.intropickup.service.IntroPickupViews;
import network.foodsharing.intropickup.service.NewcomerProgressService;
import network.foodsharing.region.mapper.RegionMapper;
import network.foodsharing.user.mapper.UserMapper;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Guide API (plan §14.1 EA10–EA16, funnel EA21). */
@RestController
public class IntroPickupGuideController implements IntroPickupGuideApi {

    private final IntroPickupGuideService guides;
    private final IntroPickupBookingService booking;
    private final GuideProfileService profiles;
    private final NewcomerProgressService progress;
    private final IntroPickupMapper mapper;
    private final RegionMapper regionMapper;
    private final UserMapper userMapper;

    public IntroPickupGuideController(IntroPickupGuideService guides, IntroPickupBookingService booking,
                                      GuideProfileService profiles, NewcomerProgressService progress,
                                      IntroPickupMapper mapper, RegionMapper regionMapper, UserMapper userMapper) {
        this.guides = guides;
        this.booking = booking;
        this.profiles = profiles;
        this.progress = progress;
        this.mapper = mapper;
        this.regionMapper = regionMapper;
        this.userMapper = userMapper;
    }

    @Override
    public ResponseEntity<SlotComment> addSlotComment(UUID slotId, SlotCommentCreate create) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toComment(guides.addComment(slotId,
                create.getComment())));
    }

    @Override
    public ResponseEntity<IntroPickupDetail> assignGuide(UUID introPickupId, AssignGuideRequest request) {
        return ResponseEntity.ok(mapper.toDetail(guides.assign(introPickupId, request.getGuideId())));
    }

    @Override
    public ResponseEntity<GuideBooking> bookSlotForUser(UUID slotId, BookForUserRequest request) {
        return ResponseEntity.ok(mapper.toGuideBooking(booking.bookFor(slotId, request.getUserId())));
    }

    @Override
    public ResponseEntity<Void> cancelBookingAsGuide(UUID slotId, OptionalReasonBody body) {
        guides.cancelBooking(slotId, body == null ? null : body.getReason());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<GuideBooking> countCancelledBooking(UUID slotId) {
        return ResponseEntity.ok(mapper.toGuideBooking(guides.countCancelled(slotId)));
    }

    @Override
    public ResponseEntity<IntroPickupDetail> createIntroPickup(UUID regionId, IntroPickupCreate create) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDetail(guides.create(regionId,
                mapper.toInput(create))));
    }

    @Override
    public ResponseEntity<Void> deleteIntroPickup(UUID introPickupId) {
        guides.delete(introPickupId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> deletePrivateInfoTemplate(IntroPickupCategory category) {
        guides.deleteTemplate(IntroPickupMapper.category(category));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<FeedLink> getGuideFeedLink() {
        FeedLink link = new FeedLink();
        link.setUrl(profiles.feedUrl());
        return ResponseEntity.ok(link);
    }

    @Override
    public ResponseEntity<GuideProfile> getGuideProfile() {
        return ResponseEntity.ok(mapper.toProfile(profiles.profile()));
    }

    @Override
    public ResponseEntity<List<IcalCandidate>> getIcalCandidates() {
        return ResponseEntity.ok(profiles.candidates().stream().map(mapper::toCandidate).toList());
    }

    @Override
    public ResponseEntity<IntroPickupDetail> getIntroPickup(UUID introPickupId) {
        return ResponseEntity.ok(mapper.toDetail(guides.get(introPickupId)));
    }

    @Override
    public ResponseEntity<NewcomerFunnel> getNewcomerFunnel(UUID regionId, Integer days) {
        return ResponseEntity.ok(mapper.toFunnel(progress.funnel(regionId, days)));
    }

    @Override
    public ResponseEntity<List<UserSummary>> listAssignableGuides(UUID regionId) {
        return ResponseEntity.ok(guides.assignableGuides(regionId).stream().map(userMapper::toSummary).toList());
    }

    @Override
    public ResponseEntity<GuideBookingPage> listGuideBookings(UUID regionId, Boolean past, Integer page,
                                                              Integer size) {
        Page<IntroPickupViews.BookingView> result = guides.myBookings(regionId, Boolean.TRUE.equals(past), page,
                size);
        GuideBookingPage model = new GuideBookingPage();
        model.setItems(result.getContent().stream().map(mapper::toGuideBooking).toList());
        model.setPage(PageMapper.toPageMeta(result));
        return ResponseEntity.ok(model);
    }

    @Override
    public ResponseEntity<IntroPickupDetailPage> listGuideIntroPickups(UUID regionId, Boolean past, Integer page,
                                                                      Integer size) {
        Page<IntroPickupViews.PickupView> result = guides.myPickups(regionId, Boolean.TRUE.equals(past), page, size);
        IntroPickupDetailPage model = new IntroPickupDetailPage();
        model.setItems(result.getContent().stream().map(mapper::toDetail).toList());
        model.setPage(PageMapper.toPageMeta(result));
        return ResponseEntity.ok(model);
    }

    @Override
    public ResponseEntity<List<RegionRef>> listGuideRegions() {
        return ResponseEntity.ok(guides.guideRegions().stream().map(regionMapper::toRef).toList());
    }

    @Override
    public ResponseEntity<List<PrivateInfoTemplate>> listPrivateInfoTemplates() {
        return ResponseEntity.ok(guides.templates().stream().map(mapper::toTemplate).toList());
    }

    @Override
    public ResponseEntity<List<SlotComment>> listSlotComments(UUID slotId) {
        return ResponseEntity.ok(guides.comments(slotId).stream().map(mapper::toComment).toList());
    }

    @Override
    public ResponseEntity<GuideBooking> markBookingDone(UUID slotId) {
        return ResponseEntity.ok(mapper.toGuideBooking(guides.markDone(slotId)));
    }

    @Override
    public ResponseEntity<GuideProfile> updateGuideProfile(GuideProfileUpdate update) {
        return ResponseEntity.ok(mapper.toProfile(profiles.update(update.getIcalLink(), update.getPhone())));
    }

    @Override
    public ResponseEntity<IntroPickupDetail> updateIntroPickup(UUID introPickupId, IntroPickupUpdate update) {
        return ResponseEntity.ok(mapper.toDetail(guides.update(introPickupId, mapper.toInput(update))));
    }
}
