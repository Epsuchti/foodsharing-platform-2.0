package network.foodsharing.intropickup.mapper;

import static network.foodsharing.common.mapper.TimeMapper.toOffset;

import java.util.List;
import network.foodsharing.api.model.BookableIntroPickup;
import network.foodsharing.api.model.BookedIntroPickup;
import network.foodsharing.api.model.FunnelGap;
import network.foodsharing.api.model.FunnelStage;
import network.foodsharing.api.model.FunnelStep;
import network.foodsharing.api.model.GapSeverity;
import network.foodsharing.api.model.GuideBooking;
import network.foodsharing.api.model.GuideSummary;
import network.foodsharing.api.model.IcalCandidate;
import network.foodsharing.api.model.IntroPickupDetail;
import network.foodsharing.api.model.MyBooking;
import network.foodsharing.api.model.NewcomerFunnel;
import network.foodsharing.api.model.NewcomerNextStep;
import network.foodsharing.api.model.NewcomerProgress;
import network.foodsharing.api.model.PastImportCounts;
import network.foodsharing.api.model.PastImportMapping;
import network.foodsharing.api.model.PastImportPreview;
import network.foodsharing.api.model.PastImportReason;
import network.foodsharing.api.model.PastImportResult;
import network.foodsharing.api.model.PastImportRow;
import network.foodsharing.api.model.PastImportStatus;
import network.foodsharing.api.model.SlotComment;
import network.foodsharing.api.model.StoreAlias;
import network.foodsharing.intropickup.domain.IntroPickup;
import network.foodsharing.intropickup.domain.IntroPickupStoreAlias;
import network.foodsharing.intropickup.service.GuideProfileService;
import network.foodsharing.intropickup.service.IcalImportService;
import network.foodsharing.intropickup.service.IntroPickupAdminService;
import network.foodsharing.intropickup.service.IntroPickupBookingService;
import network.foodsharing.intropickup.service.IntroPickupGuideService;
import network.foodsharing.intropickup.service.IntroPickupGuideService.PickupInput;
import network.foodsharing.intropickup.service.IntroPickupViews.BookingView;
import network.foodsharing.intropickup.service.IntroPickupViews.PickupView;
import network.foodsharing.intropickup.service.IntroPickupViews.SlotView;
import network.foodsharing.intropickup.service.NewcomerProgressService;
import network.foodsharing.intropickup.service.PastImportService;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.mapper.RegionMapper;
import network.foodsharing.user.mapper.UserMapper;
import org.springframework.stereotype.Component;

/** EinAb models ↔ service views. Domain and API enums share their constant names. */
@Component
public class IntroPickupMapper {

    private final UserMapper users;
    private final RegionMapper regions;

    public IntroPickupMapper(UserMapper users, RegionMapper regions) {
        this.users = users;
        this.regions = regions;
    }

    // ------------------------------------------------------------------------------------------- enums

    public static network.foodsharing.api.model.IntroPickupCategory category(
            network.foodsharing.intropickup.domain.IntroPickupCategory category) {
        return category == null ? null : network.foodsharing.api.model.IntroPickupCategory.valueOf(category.name());
    }

    public static network.foodsharing.intropickup.domain.IntroPickupCategory category(
            network.foodsharing.api.model.IntroPickupCategory category) {
        return category == null ? null
                : network.foodsharing.intropickup.domain.IntroPickupCategory.valueOf(category.name());
    }

    public static network.foodsharing.api.model.SlotStatus status(
            network.foodsharing.intropickup.domain.SlotStatus status) {
        return network.foodsharing.api.model.SlotStatus.valueOf(status.name());
    }

    public static network.foodsharing.intropickup.domain.SubscriptionChannel channel(
            network.foodsharing.api.model.SubscriptionChannel channel) {
        return network.foodsharing.intropickup.domain.SubscriptionChannel.valueOf(channel.name());
    }

    // ---------------------------------------------------------------------------------------- bookings

    public BookableIntroPickup toBookable(IntroPickupBookingService.BookableView view) {
        IntroPickup pickup = view.pickup();
        BookableIntroPickup model = new BookableIntroPickup();
        model.setId(pickup.getId());
        model.setCategory(category(pickup.getCategory()));
        model.setStartAt(toOffset(pickup.getStartAt()));
        model.setGuide(view.guide() == null ? null : users.toSummary(view.guide()));
        model.setPublicLocation(pickup.getPublicGeneralLocation());
        model.setPublicGeneralLocation(pickup.getPublicGeneralLocation());
        model.setBusinessName(pickup.getBusinessName());
        model.setPublicInfo(pickup.getPublicInfo());
        model.setVisitFairteiler(pickup.isVisitFairteiler());
        model.setMinimumPickupCount(pickup.getMinimumPickupCount());
        model.setFreeSlots(view.freeSlots());
        model.setSlotId(view.firstFreeSlotId());
        model.setBookable(view.notBookableReason() == null);
        model.setNotBookableReason(view.notBookableReason());
        return model;
    }

    public MyBooking toMyBooking(BookingView view) {
        IntroPickup pickup = view.pickup().pickup();
        BookedIntroPickup booked = new BookedIntroPickup();
        booked.setId(pickup.getId());
        booked.setRegionId(pickup.getRegionId());
        booked.setCategory(category(pickup.getCategory()));
        booked.setStartAt(toOffset(pickup.getStartAt()));
        booked.setGuide(view.pickup().guide() == null ? null : users.toSummary(view.pickup().guide(), true));
        booked.setBusinessName(pickup.getBusinessName());
        booked.setPublicGeneralLocation(pickup.getPublicGeneralLocation());
        booked.setPrivateAddress(pickup.getPrivateAddress());
        booked.setPrivateInfo(pickup.getPrivateInfo());
        booked.setPublicInfo(pickup.getPublicInfo());
        booked.setOnlineCallLink(pickup.getOnlineCallLink());
        booked.setVisitFairteiler(pickup.isVisitFairteiler());
        MyBooking model = new MyBooking();
        model.setSlotId(view.slot().slot().getId());
        model.setIntroPickup(booked);
        model.setStatus(status(view.slot().slot().getStatus()));
        model.setBookedAt(toOffset(view.slot().slot().getBookedAt()));
        model.setDoneAt(toOffset(view.slot().slot().getDoneAt()));
        model.setCounted(view.counted());
        return model;
    }

    public GuideBooking toGuideBooking(BookingView view) {
        GuideBooking model = new GuideBooking();
        model.setSlotId(view.slot().slot().getId());
        model.setIntroPickup(toDetail(view.pickup()));
        model.setUser(view.slot().bookingUser() == null ? null : users.toSummary(view.slot().bookingUser()));
        model.setStatus(status(view.slot().slot().getStatus()));
        model.setBookedAt(toOffset(view.slot().slot().getBookedAt()));
        model.setDoneAt(toOffset(view.slot().slot().getDoneAt()));
        model.setCounted(view.counted());
        model.setCommentCount(view.slot().commentCount());
        return model;
    }

    // ----------------------------------------------------------------------------------------- EinAbs

    public IntroPickupDetail toDetail(PickupView view) {
        IntroPickup pickup = view.pickup();
        IntroPickupDetail model = new IntroPickupDetail();
        model.setId(pickup.getId());
        model.setRegionId(pickup.getRegionId());
        model.setCategory(category(pickup.getCategory()));
        model.setStartAt(toOffset(pickup.getStartAt()));
        model.setGuide(view.guide() == null ? null : users.toSummary(view.guide()));
        model.setSlotCount(pickup.getSlotCount());
        model.setMinimumPickupCount(pickup.getMinimumPickupCount());
        model.setBusinessName(pickup.getBusinessName());
        model.setStoreId(pickup.getStoreId());
        model.setPrivateAddress(pickup.getPrivateAddress());
        model.setPublicGeneralLocation(pickup.getPublicGeneralLocation());
        model.setOnlineCallLink(pickup.getOnlineCallLink());
        model.setPrivateInfo(pickup.getPrivateInfo());
        model.setPublicInfo(pickup.getPublicInfo());
        model.setVisitFairteiler(pickup.isVisitFairteiler());
        model.setImported(pickup.isImported());
        model.setSlots(view.slots().stream().map(this::toSlot).toList());
        model.setBooked(view.booked());
        return model;
    }

    private network.foodsharing.api.model.IntroPickupSlot toSlot(SlotView view) {
        network.foodsharing.api.model.IntroPickupSlot model = new network.foodsharing.api.model.IntroPickupSlot();
        model.setId(view.slot().getId());
        model.setStatus(status(view.slot().getStatus()));
        model.setBookingUser(view.bookingUser() == null ? null : users.toSummary(view.bookingUser()));
        model.setBookedAt(toOffset(view.slot().getBookedAt()));
        model.setDoneAt(toOffset(view.slot().getDoneAt()));
        model.setCommentCount(view.commentCount());
        return model;
    }

    public PickupInput toInput(network.foodsharing.api.model.IntroPickupCreate create) {
        return new PickupInput(category(create.getCategory()),
                create.getStartAt() == null ? null : create.getStartAt().toInstant(), create.getSlotCount(),
                create.getMinimumPickupCount(), create.getBusinessName(), create.getStoreId(),
                create.getPrivateAddress(), create.getPublicGeneralLocation(), create.getOnlineCallLink(),
                create.getPrivateInfo(), create.getPublicInfo(), create.getSavePrivateInfoAsTemplate(),
                create.getVisitFairteiler());
    }

    public PickupInput toInput(network.foodsharing.api.model.IntroPickupUpdate update) {
        return new PickupInput(category(update.getCategory()),
                update.getStartAt() == null ? null : update.getStartAt().toInstant(), update.getSlotCount(),
                update.getMinimumPickupCount(), update.getBusinessName(), update.getStoreId(),
                update.getPrivateAddress(), update.getPublicGeneralLocation(), update.getOnlineCallLink(),
                update.getPrivateInfo(), update.getPublicInfo(), update.getSavePrivateInfoAsTemplate(),
                update.getVisitFairteiler());
    }

    public SlotComment toComment(IntroPickupGuideService.CommentView view) {
        SlotComment model = new SlotComment();
        model.setId(view.comment().getId());
        model.setAuthor(view.author() == null ? null : users.toSummary(view.author()));
        model.setText(view.comment().getText());
        model.setAdminComment(view.comment().isAdminComment());
        model.setCreatedAt(toOffset(view.comment().getCreatedAt()));
        return model;
    }

    public network.foodsharing.api.model.PrivateInfoTemplate toTemplate(
            network.foodsharing.intropickup.domain.PrivateInfoTemplate template) {
        network.foodsharing.api.model.PrivateInfoTemplate model =
                new network.foodsharing.api.model.PrivateInfoTemplate();
        model.setCategory(category(template.getCategory()));
        model.setContent(template.getContent());
        return model;
    }

    // ---------------------------------------------------------------------------------- guide profile

    public network.foodsharing.api.model.GuideProfile toProfile(GuideProfileService.ProfileView view) {
        network.foodsharing.api.model.GuideProfile model = new network.foodsharing.api.model.GuideProfile();
        model.setIcalLink(view.icalLink());
        model.setPhone(view.phone());
        model.setFeedUrl(view.feedUrl());
        return model;
    }

    public IcalCandidate toCandidate(IcalImportService.Candidate candidate) {
        IcalCandidate model = new IcalCandidate();
        model.setStartAt(toOffset(candidate.startAt()));
        model.setEndAt(toOffset(candidate.endAt()));
        model.setSummary(candidate.summary());
        model.setLocation(candidate.location());
        return model;
    }

    // ------------------------------------------------------------------------------------- subscription

    public network.foodsharing.api.model.IntroPickupSubscription toSubscription(
            network.foodsharing.intropickup.domain.IntroPickupSubscription subscription, Region region) {
        network.foodsharing.api.model.IntroPickupSubscription model =
                new network.foodsharing.api.model.IntroPickupSubscription();
        model.setRegionId(subscription.getRegionId());
        model.setRegion(region == null ? null : regions.toRef(region));
        model.setChannel(network.foodsharing.api.model.SubscriptionChannel.valueOf(subscription.getChannel().name()));
        model.setDeliveryEmail(subscription.getDeliveryEmail());
        model.setLanguage(network.foodsharing.api.model.Language.fromValue(subscription.getLanguage()));
        model.setActive(subscription.isActive());
        return model;
    }

    // ------------------------------------------------------------------------------------------ funnel

    public NewcomerProgress toProgress(NewcomerProgressService.Progress progress) {
        NewcomerProgress model = new NewcomerProgress();
        model.setUser(progress.user() == null ? null : users.toSummary(progress.user()));
        model.setStage(FunnelStage.valueOf(progress.stage().name()));
        model.setCounted(progress.counted());
        model.setUpcoming(progress.upcoming());
        model.setTarget(progress.target());
        model.setFairteilerDone(progress.fairteilerDone());
        model.setFairteilerBooked(progress.fairteilerBooked());
        model.setRegisteredAt(toOffset(progress.user() == null ? null
                : progress.user().getRegisteredAt() != null ? progress.user().getRegisteredAt()
                : progress.user().getCreatedAt()));
        model.setLastActivityAt(toOffset(progress.lastActivityAt()));
        model.setDaysInStage(progress.daysInStage());
        model.setStalled(progress.stalled());
        model.setNextStep(NewcomerNextStep.valueOf(progress.nextStep().name()));
        return model;
    }

    public NewcomerFunnel toFunnel(NewcomerProgressService.Funnel funnel) {
        NewcomerFunnel model = new NewcomerFunnel();
        model.setTargetIntroPickups(funnel.target());
        model.setFairteilerRequired(funnel.fairteilerRequired());
        model.setDays(funnel.days());
        model.setTotalUsers(funnel.totalUsers());
        model.setInTrainingUsers(funnel.inTrainingUsers());
        model.setStalledUsers(funnel.stalledUsers());
        model.setIntroPickupsStillNeeded(funnel.introPickupsStillNeeded());
        model.setOpenSlots(funnel.openSlots());
        model.setFairteilerVisitsStillNeeded(funnel.fairteilerVisitsStillNeeded());
        model.setOpenFairteilerSlots(funnel.openFairteilerSlots());
        model.setGapHorizonDays(funnel.gapHorizonDays());
        model.setSteps(funnel.steps().stream().map(this::toStep).toList());
        model.setGaps(funnel.gaps().stream().map(IntroPickupMapper::toGap).toList());
        return model;
    }

    private FunnelStep toStep(NewcomerProgressService.FunnelStep step) {
        FunnelStep model = new FunnelStep();
        model.setStage(FunnelStage.valueOf(step.stage().name()));
        model.setIntroPickupCount(step.introPickupCount());
        model.setUserCount(step.userCount());
        model.setReachedCount(step.reachedCount());
        model.setStalledCount(step.stalledCount());
        model.setMedianDaysInStep(step.medianDaysInStep() == null ? null : step.medianDaysInStep().doubleValue());
        model.setUsers(step.users().stream().map(this::toProgress).toList());
        return model;
    }

    private static FunnelGap toGap(NewcomerProgressService.Gap gap) {
        FunnelGap model = new FunnelGap();
        model.setCategory(category(gap.category()));
        model.setWithFairteiler(gap.withFairteiler());
        model.setStudents(gap.students());
        model.setOpenSlots(gap.openSlots());
        model.setSeverity(GapSeverity.valueOf(gap.severity().name()));
        return model;
    }

    // -------------------------------------------------------------------------------------------- admin

    public GuideSummary toGuideSummary(IntroPickupAdminService.GuideSummaryView view) {
        GuideSummary model = new GuideSummary();
        model.setUser(users.toSummary(view.user()));
        model.setActive(view.active());
        model.setCanManageUsers(view.canManageUsers());
        model.setIntroPickupCount(view.introPickupCount());
        model.setLastIntroPickupAt(toOffset(view.lastIntroPickupAt()));
        model.setNextIntroPickupAt(toOffset(view.nextIntroPickupAt()));
        return model;
    }

    public StoreAlias toAlias(IntroPickupStoreAlias alias) {
        StoreAlias model = new StoreAlias();
        model.setId(alias.getId());
        model.setAliasKey(alias.getAliasKey());
        model.setStoreName(alias.getStoreName());
        return model;
    }

    public PastImportPreview toPreview(PastImportService.Preview preview) {
        PastImportCounts counts = new PastImportCounts();
        counts.setTotalRows(preview.counts().totalRows());
        counts.setWillCreate(preview.counts().willCreate());
        counts.setWillSkip(preview.counts().willSkip());
        counts.setNeedsAttention(preview.counts().needsAttention());
        counts.setKnownUsers(preview.counts().knownUsers());
        counts.setUnknownUsers(preview.counts().unknownUsers());
        PastImportPreview model = new PastImportPreview();
        model.setCounts(counts);
        model.setRows(preview.rows().stream().map(IntroPickupMapper::toRow).toList());
        model.setStoreMappings(toMappings(preview.storeMappings()));
        model.setCategoryMappings(toMappings(preview.categoryMappings()));
        return model;
    }

    private static PastImportRow toRow(PastImportService.Row row) {
        PastImportRow model = new PastImportRow();
        model.setLine(row.line());
        model.setFoodsaverNumber(row.foodsaverNumber());
        model.setUserId(row.userId());
        model.setUserDisplayName(row.userDisplayName());
        model.setRawDate(row.rawDate());
        model.setDate(row.date());
        model.setRawStore(row.rawStore());
        model.setStore(row.store());
        model.setRawCategory(row.rawCategory());
        model.setCategory(category(row.category()));
        model.setVisitFairteiler(row.visitFairteiler());
        model.setHasComment(row.hasComment());
        model.setStatus(PastImportStatus.valueOf(row.status().name()));
        model.setReasons(row.reasons().stream().map(r -> PastImportReason.valueOf(r.name())).toList());
        return model;
    }

    private static List<PastImportMapping> toMappings(List<PastImportService.Mapping> mappings) {
        return mappings.stream().map(m -> {
            PastImportMapping model = new PastImportMapping();
            model.setFrom(m.from());
            model.setTo(m.to());
            model.setCount(m.count());
            return model;
        }).toList();
    }

    public PastImportResult toResult(PastImportService.Result result) {
        PastImportResult model = new PastImportResult();
        model.setCreated(result.created());
        model.setSkipped(result.skipped());
        model.setNeedsAttention(result.needsAttention());
        model.setJobId(result.jobId());
        return model;
    }
}
