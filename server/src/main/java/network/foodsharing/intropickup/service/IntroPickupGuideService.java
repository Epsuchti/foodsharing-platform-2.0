package network.foodsharing.intropickup.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.intropickup.domain.IntroPickup;
import network.foodsharing.intropickup.domain.IntroPickupAction;
import network.foodsharing.intropickup.domain.IntroPickupCategory;
import network.foodsharing.intropickup.domain.IntroPickupComment;
import network.foodsharing.intropickup.domain.IntroPickupSlot;
import network.foodsharing.intropickup.domain.PrivateInfoTemplate;
import network.foodsharing.intropickup.domain.SlotStatus;
import network.foodsharing.intropickup.repository.IntroPickupCommentRepository;
import network.foodsharing.intropickup.repository.IntroPickupRepository;
import network.foodsharing.intropickup.repository.IntroPickupSlotRepository;
import network.foodsharing.intropickup.repository.PrivateInfoTemplateRepository;
import network.foodsharing.intropickup.service.IntroPickupViews.BookingView;
import network.foodsharing.intropickup.service.IntroPickupViews.PickupView;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionPermissionResolver;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.Permission;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What guides do (plan §14.1 EA10–EA15, reference {@code TeacherService}): create / update / delete / reassign their
 * EinAbs, book for users, cancel bookings, mark them done, count cancelled ones, comments and private-info templates.
 */
@Service
public class IntroPickupGuideService {

    static final Set<SlotStatus> BLOCKING = Set.of(SlotStatus.BOOKED, SlotStatus.DONE);

    /** Create / update input; on update null means unchanged. */
    public record PickupInput(IntroPickupCategory category, Instant startAt, Integer slotCount,
                              Integer minimumPickupCount, String businessName, UUID storeId, String privateAddress,
                              String publicGeneralLocation, String onlineCallLink, String privateInfo,
                              String publicInfo, Boolean savePrivateInfoAsTemplate, Boolean visitFairteiler) {
    }

    public record CommentView(IntroPickupComment comment, User author) {
    }

    public record IntroPickupCreated(UUID introPickupId) {
    }

    public record IntroPickupUpdated(UUID introPickupId) {
    }

    public record BookingCancelledByGuide(UUID introPickupId, UUID userId, String reason) {
    }

    private final IntroPickupRepository pickups;
    private final IntroPickupSlotRepository slots;
    private final IntroPickupCommentRepository comments;
    private final PrivateInfoTemplateRepository templates;
    private final IntroPickupViews views;
    private final RegionService regions;
    private final RegionPermissionResolver resolver;
    private final UserService users;
    private final CurrentActorService currentActor;
    private final PermissionService permissions;
    private final HistoryService history;
    private final ApplicationEventPublisher events;
    private final AppProperties properties;

    public IntroPickupGuideService(IntroPickupRepository pickups, IntroPickupSlotRepository slots,
                                   IntroPickupCommentRepository comments, PrivateInfoTemplateRepository templates,
                                   IntroPickupViews views, RegionService regions, RegionPermissionResolver resolver,
                                   UserService users, CurrentActorService currentActor, PermissionService permissions,
                                   HistoryService history, ApplicationEventPublisher events,
                                   AppProperties properties) {
        this.pickups = pickups;
        this.slots = slots;
        this.comments = comments;
        this.templates = templates;
        this.views = views;
        this.regions = regions;
        this.resolver = resolver;
        this.users = users;
        this.currentActor = currentActor;
        this.permissions = permissions;
        this.history = history;
        this.events = events;
        this.properties = properties;
    }

    // ------------------------------------------------------------------------------------------- read

    /** Regions in which the current user gives EinAbs. */
    @Transactional(readOnly = true)
    public List<Region> guideRegions() {
        UUID userId = currentActor.requireUserId();
        List<UUID> ids = resolver.permissionsByRegion(userId).entrySet().stream()
                .filter(e -> e.getValue().contains(Permission.CAN_GIVE_INTRO_PICKUPS)).map(Map.Entry::getKey).toList();
        return regions.findAll(ids).stream().sorted(Comparator.comparing(Region::getName)).toList();
    }

    /** Own EinAbs of the guide in the region: upcoming by start, or past newest first. */
    @Transactional(readOnly = true)
    public Page<PickupView> myPickups(UUID regionId, boolean past, Integer page, Integer size) {
        requireGuideIn(regionId);
        UUID userId = currentActor.requireUserId();
        Instant now = views.now();
        List<IntroPickup> own = pickups.findByGuide(userId).stream().filter(p -> p.getRegionId().equals(regionId))
                .filter(p -> p.getStartAt().isBefore(now) == past)
                .sorted(past ? Comparator.comparing(IntroPickup::getStartAt).reversed()
                        : Comparator.comparing(IntroPickup::getStartAt)).toList();
        Page<IntroPickup> paged = IntroPickupViews.page(own, page, size);
        List<PickupView> content = views.pickups(paged.getContent());
        return paged.map(p -> content.stream().filter(v -> v.pickup().getId().equals(p.getId())).findFirst()
                .orElseThrow());
    }

    /**
     * An EinAb with all slots and booking users: its guide, holders of CAN_MANAGE_USERS in the region, ORGA.
     *
     * @throws ApiException 404 INTRO_PICKUP_NOT_FOUND, 403 ACCESS_DENIED
     */
    @Transactional(readOnly = true)
    public PickupView get(UUID introPickupId) {
        IntroPickup pickup = views.requirePickup(introPickupId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        if (!actor.userId().equals(pickup.getGuideId())
                && !permissions.has(actor, Permission.CAN_MANAGE_USERS, pickup.getRegionId())) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        return views.pickup(pickup);
    }

    /** Active guides of the region (members holding CAN_GIVE_INTRO_PICKUPS), by name. */
    @Transactional(readOnly = true)
    public List<User> assignableGuides(UUID regionId) {
        requireGuideIn(regionId);
        return guidesOf(regionId);
    }

    List<User> guidesOf(UUID regionId) {
        return users.findActive(resolver.holders(Permission.CAN_GIVE_INTRO_PICKUPS, regionId)).stream()
                .filter(user -> resolver.isMember(user.getId(), regionId))
                .sorted(Comparator.comparing(User::getDisplayName, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    /** Bookings of the guide's EinAbs in the region (upcoming or past), including cancelled ones with a user. */
    @Transactional(readOnly = true)
    public Page<BookingView> myBookings(UUID regionId, boolean past, Integer page, Integer size) {
        requireGuideIn(regionId);
        UUID userId = currentActor.requireUserId();
        Instant now = views.now();
        List<BookingView> all = views.bookings(slots.findBookingsInRegion(regionId)).stream()
                .filter(b -> userId.equals(b.pickup().pickup().getGuideId())
                        || userId.equals(b.slot().slot().getGuideId()))
                .filter(b -> b.pickup().pickup().getStartAt().isBefore(now) == past)
                .sorted(past ? Comparator.comparing((BookingView b) -> b.pickup().pickup().getStartAt()).reversed()
                        : Comparator.comparing((BookingView b) -> b.pickup().pickup().getStartAt()))
                .toList();
        return IntroPickupViews.page(all, page, size);
    }

    // ------------------------------------------------------------------------------------------ write

    /**
     * Creates an EinAb with its slots (online: one place, refilled on every booking).
     *
     * @throws ApiException 404 REGION_NOT_FOUND, 403 ACCESS_DENIED, 400 validation codes
     */
    @Transactional
    public PickupView create(UUID regionId, PickupInput input) {
        regions.require(regionId);
        requireGuideIn(regionId);
        UUID guideId = currentActor.requireUserId();
        if (input.startAt() == null || input.category() == null) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        if (input.startAt().isBefore(views.now())) {
            throw ApiException.badRequest(ApiErrorCode.INTRO_PICKUP_START_IN_PAST);
        }
        IntroPickup pickup = new IntroPickup();
        pickup.setRegionId(regionId);
        pickup.setGuideId(guideId);
        apply(pickup, input);
        pickups.save(pickup);
        createSlots(pickup, pickup.getSlotCount());
        saveTemplateIfRequested(guideId, pickup, input);
        history.record(history.event(IntroPickupAction.CREATED, pickup.getId()).region(regionId)
                .metadata("category", pickup.getCategory().name()).metadata("startAt", pickup.getStartAt().toString())
                .build());
        events.publishEvent(new IntroPickupCreated(pickup.getId()));
        return views.pickup(pickup);
    }

    /**
     * Updates an own upcoming EinAb (null = unchanged); booked users are told after commit.
     *
     * @throws ApiException 404, 403 ONLY_OWN_INTRO_PICKUPS_MANAGEABLE, 409 PAST_INTRO_PICKUP_NOT_EDITABLE /
     *                      SLOT_COUNT_REDUCTION_NOT_SUPPORTED, 400 validation codes
     */
    @Transactional
    public PickupView update(UUID introPickupId, PickupInput input) {
        IntroPickup pickup = requireOwn(introPickupId);
        Instant now = views.now();
        if (pickup.getStartAt().isBefore(now)) {
            throw ApiException.conflict(ApiErrorCode.PAST_INTRO_PICKUP_NOT_EDITABLE);
        }
        if (input.startAt() != null && input.startAt().isBefore(now)) {
            throw ApiException.badRequest(ApiErrorCode.INTRO_PICKUP_START_IN_PAST);
        }
        Snapshot before = Snapshot.of(pickup);
        IntroPickupCategory category = input.category() == null ? pickup.getCategory() : input.category();
        PickupInput merged = new PickupInput(category,
                input.startAt() == null ? pickup.getStartAt() : input.startAt(),
                input.slotCount() == null ? pickup.getSlotCount() : input.slotCount(),
                input.minimumPickupCount() == null ? pickup.getMinimumPickupCount() : input.minimumPickupCount(),
                input.businessName() == null ? pickup.getBusinessName() : input.businessName(),
                input.storeId() == null ? pickup.getStoreId() : input.storeId(),
                input.privateAddress() == null ? pickup.getPrivateAddress() : input.privateAddress(),
                input.publicGeneralLocation() == null ? pickup.getPublicGeneralLocation()
                        : input.publicGeneralLocation(),
                input.onlineCallLink() == null ? pickup.getOnlineCallLink() : input.onlineCallLink(),
                input.privateInfo() == null ? pickup.getPrivateInfo() : input.privateInfo(),
                input.publicInfo() == null ? pickup.getPublicInfo() : input.publicInfo(),
                input.savePrivateInfoAsTemplate(),
                input.visitFairteiler() == null ? pickup.isVisitFairteiler() : input.visitFairteiler());
        List<IntroPickupSlot> existing = slots.findByPickupForUpdate(pickup.getId());
        int liveSlots = (int) existing.stream().filter(s -> s.getStatus() != SlotStatus.CANCELLED).count();
        validate(merged);
        if (category != IntroPickupCategory.ONLINE && merged.slotCount() < liveSlots) {
            throw ApiException.conflict(ApiErrorCode.SLOT_COUNT_REDUCTION_NOT_SUPPORTED);
        }
        apply(pickup, merged);
        if (pickup.isOnline()) {
            boolean kept = false;
            for (IntroPickupSlot slot : existing) {
                if (slot.getStatus() == SlotStatus.AVAILABLE) {
                    if (kept) {
                        slot.setStatus(SlotStatus.CANCELLED);
                    }
                    kept = true;
                }
            }
            if (!kept) {
                createSlots(pickup, 1);
            }
        } else if (merged.slotCount() > liveSlots) {
            createSlots(pickup, merged.slotCount() - liveSlots);
        }
        saveTemplateIfRequested(pickup.getGuideId(), pickup, merged);
        List<String> changed = before.changedFields(pickup);
        history.record(history.event(IntroPickupAction.UPDATED, pickup.getId()).region(pickup.getRegionId())
                .metadata("fields", String.join(",", changed)).build());
        if (!changed.isEmpty()) {
            events.publishEvent(new IntroPickupUpdated(pickup.getId()));
        }
        return views.pickup(pickup);
    }

    /**
     * Deletes an own upcoming EinAb without bookings (soft delete; free places are cancelled).
     *
     * @throws ApiException 404, 403 ONLY_OWN_INTRO_PICKUPS_MANAGEABLE, 409 PAST_INTRO_PICKUP_NOT_DELETABLE /
     *                      BOOKED_SLOTS_PREVENT_DELETE
     */
    @Transactional
    public void delete(UUID introPickupId) {
        IntroPickup pickup = requireOwn(introPickupId);
        softDelete(pickup);
        history.record(history.event(IntroPickupAction.DELETED, pickup.getId()).region(pickup.getRegionId()).build());
    }

    /** Shared by the guide and the admin delete: guards, then soft delete. */
    void softDelete(IntroPickup pickup) {
        if (pickup.getStartAt().isBefore(views.now())) {
            throw ApiException.conflict(ApiErrorCode.PAST_INTRO_PICKUP_NOT_DELETABLE);
        }
        if (slots.existsByIntroPickupIdAndStatusIn(pickup.getId(), BLOCKING)) {
            throw ApiException.conflict(ApiErrorCode.BOOKED_SLOTS_PREVENT_DELETE);
        }
        slots.findByPickupForUpdate(pickup.getId()).forEach(slot -> slot.setStatus(SlotStatus.CANCELLED));
        pickup.setDeletedAt(views.now());
    }

    /**
     * Hands an own upcoming EinAb to another active guide of the region.
     *
     * @throws ApiException 404, 403 ONLY_OWN_INTRO_PICKUPS_MANAGEABLE, 409 PAST_INTRO_PICKUP_NOT_REASSIGNABLE,
     *                      400 ASSIGNABLE_GUIDE_NOT_FOUND
     */
    @Transactional
    public PickupView assign(UUID introPickupId, UUID guideId) {
        IntroPickup pickup = requireOwn(introPickupId);
        if (pickup.getStartAt().isBefore(views.now())) {
            throw ApiException.conflict(ApiErrorCode.PAST_INTRO_PICKUP_NOT_REASSIGNABLE);
        }
        boolean assignable = guideId != null && guidesOf(pickup.getRegionId()).stream()
                .anyMatch(user -> user.getId().equals(guideId));
        if (!assignable) {
            throw ApiException.badRequest(ApiErrorCode.ASSIGNABLE_GUIDE_NOT_FOUND);
        }
        UUID previous = pickup.getGuideId();
        pickup.setGuideId(guideId);
        slots.findByPickupForUpdate(pickup.getId()).forEach(slot -> slot.setGuideId(guideId));
        history.record(history.event(IntroPickupAction.GUIDE_ASSIGNED, pickup.getId()).region(pickup.getRegionId())
                .change("guideId", previous, guideId).build());
        return views.pickup(pickup);
    }

    /**
     * The guide cancels a booking: upcoming → the place is free again and the user is told; past → CANCELLED (does
     * not count).
     *
     * @throws ApiException 404 SLOT_NOT_FOUND, 403 ACCESS_DENIED, 409 ONLY_BOOKED_APPOINTMENTS_CANCELLABLE
     */
    @Transactional
    public void cancelBooking(UUID slotId, String reason) {
        IntroPickupSlot slot = views.requireSlot(slotId, true);
        IntroPickup pickup = views.requirePickup(slot.getIntroPickupId());
        requireGuideOfSlot(pickup, slot);
        if (slot.getStatus() != SlotStatus.BOOKED || slot.getBookingUserId() == null) {
            throw ApiException.conflict(ApiErrorCode.ONLY_BOOKED_APPOINTMENTS_CANCELLABLE);
        }
        UUID userId = slot.getBookingUserId();
        boolean upcoming = pickup.getStartAt().isAfter(views.now());
        if (upcoming) {
            slot.setStatus(SlotStatus.AVAILABLE);
            slot.setBookingUserId(null);
            slot.setBookedAt(null);
            slot.setDoneAt(null);
        } else {
            slot.setStatus(SlotStatus.CANCELLED);
        }
        history.record(history.event(IntroPickupAction.BOOKING_CANCELLED_BY_GUIDE, pickup.getId())
                .secondarySubject(userId).region(pickup.getRegionId()).reason(blankToNull(reason))
                .metadata("upcoming", upcoming).build());
        if (upcoming) {
            events.publishEvent(new BookingCancelledByGuide(pickup.getId(), userId, blankToNull(reason)));
        }
    }

    /**
     * @throws ApiException 404, 403 ACCESS_DENIED, 409 ONLY_BOOKED_APPOINTMENTS_COMPLETABLE /
     *                      FUTURE_APPOINTMENT_NOT_COMPLETABLE
     */
    @Transactional
    public BookingView markDone(UUID slotId) {
        IntroPickupSlot slot = views.requireSlot(slotId, true);
        IntroPickup pickup = views.requirePickup(slot.getIntroPickupId());
        requireGuideOfSlot(pickup, slot);
        if (slot.getStatus() != SlotStatus.BOOKED || slot.getBookingUserId() == null) {
            throw ApiException.conflict(ApiErrorCode.ONLY_BOOKED_APPOINTMENTS_COMPLETABLE);
        }
        if (pickup.getStartAt().isAfter(views.now())) {
            throw ApiException.conflict(ApiErrorCode.FUTURE_APPOINTMENT_NOT_COMPLETABLE);
        }
        slot.setStatus(SlotStatus.DONE);
        slot.setDoneAt(views.now());
        history.record(history.event(IntroPickupAction.MARKED_DONE, pickup.getId())
                .secondarySubject(slot.getBookingUserId()).region(pickup.getRegionId()).build());
        return views.booking(slot);
    }

    /** @throws ApiException 404, 403 ACCESS_DENIED, 409 ONLY_CANCELLED_APPOINTMENTS_COUNTABLE */
    @Transactional
    public BookingView countCancelled(UUID slotId) {
        IntroPickupSlot slot = views.requireSlot(slotId, true);
        IntroPickup pickup = views.requirePickup(slot.getIntroPickupId());
        requireGuideOfSlot(pickup, slot);
        if (slot.getStatus() != SlotStatus.CANCELLED || slot.getBookingUserId() == null) {
            throw ApiException.conflict(ApiErrorCode.ONLY_CANCELLED_APPOINTMENTS_COUNTABLE);
        }
        slot.setStatus(SlotStatus.BOOKED);
        slot.setDoneAt(null);
        history.record(history.event(IntroPickupAction.COUNTED, pickup.getId())
                .secondarySubject(slot.getBookingUserId()).region(pickup.getRegionId()).build());
        return views.booking(slot);
    }

    /**
     * Comments on a booking: the guide of the slot, CAN_MANAGE_USERS holders of the region, ORGA.
     *
     * @throws ApiException 404 SLOT_NOT_FOUND, 403 ACCESS_DENIED, 409 BOOKING_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public List<CommentView> comments(UUID slotId) {
        IntroPickupSlot slot = views.requireSlot(slotId, false);
        IntroPickup pickup = views.requirePickup(slot.getIntroPickupId());
        SessionPrincipal actor = currentActor.requirePrincipal();
        if (!isGuideOfSlot(actor.userId(), pickup, slot)
                && !permissions.has(actor, Permission.CAN_MANAGE_USERS, pickup.getRegionId())) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        requireBooking(slot);
        return commentViews(comments.findBySlotIdOrderByCreatedAtAsc(slot.getId()));
    }

    List<CommentView> commentViews(List<IntroPickupComment> list) {
        Map<UUID, User> authors = views.usersById(list.stream().map(IntroPickupComment::getAuthorId)
                .filter(Objects::nonNull).toList());
        return list.stream().map(c -> new CommentView(c, c.getAuthorId() == null ? null : authors.get(c.getAuthorId())))
                .toList();
    }

    /** @throws ApiException 404 SLOT_NOT_FOUND, 403 ACCESS_DENIED, 409 BOOKING_NOT_FOUND */
    @Transactional
    public CommentView addComment(UUID slotId, String text) {
        IntroPickupSlot slot = views.requireSlot(slotId, false);
        IntroPickup pickup = views.requirePickup(slot.getIntroPickupId());
        requireGuideOfSlot(pickup, slot);
        IntroPickupComment comment = saveComment(slot, text, false);
        history.record(history.event(IntroPickupAction.COMMENT_ADDED, pickup.getId())
                .secondarySubject(slot.getBookingUserId()).region(pickup.getRegionId()).build());
        return new CommentView(comment, currentActor.requireUser());
    }

    IntroPickupComment saveComment(IntroPickupSlot slot, String text, boolean admin) {
        requireBooking(slot);
        if (text == null || text.isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        IntroPickupComment comment = new IntroPickupComment();
        comment.setSlotId(slot.getId());
        comment.setBookingUserId(slot.getBookingUserId());
        comment.setAuthorId(currentActor.requireUserId());
        comment.setAdminComment(admin);
        comment.setText(text.strip());
        return comments.save(comment);
    }

    /** Own private-info templates (any guide). */
    @Transactional(readOnly = true)
    public List<PrivateInfoTemplate> templates() {
        requireGuideAnywhere();
        return templates.findByGuideIdOrderByCategoryAsc(currentActor.requireUserId());
    }

    /** @throws ApiException 403 ACCESS_DENIED, 404 RESOURCE_NOT_FOUND */
    @Transactional
    public void deleteTemplate(IntroPickupCategory category) {
        requireGuideAnywhere();
        UUID guideId = currentActor.requireUserId();
        PrivateInfoTemplate template = templates.findByGuideIdAndCategory(guideId, category)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND));
        templates.delete(template);
        history.record(history.event(IntroPickupAction.TEMPLATE_DELETED, guideId)
                .metadata("category", category.name()).build());
    }

    // ---------------------------------------------------------------------------------------- helpers

    /** Normalises and validates the input (reference {@code validateEinAb}) and copies it onto the EinAb. */
    private void apply(IntroPickup pickup, PickupInput input) {
        validate(input);
        boolean online = input.category() == IntroPickupCategory.ONLINE;
        pickup.setCategory(input.category());
        pickup.setStartAt(input.startAt());
        pickup.setBusinessName(online ? null : trim(input.businessName()));
        pickup.setStoreId(online ? null : input.storeId());
        pickup.setPrivateAddress(online ? null : trim(input.privateAddress()));
        pickup.setPublicGeneralLocation(online ? null : trim(input.publicGeneralLocation()));
        pickup.setOnlineCallLink(online ? trim(input.onlineCallLink()) : null);
        pickup.setPrivateInfo(trim(input.privateInfo()));
        pickup.setPublicInfo(trim(input.publicInfo()));
        pickup.setVisitFairteiler(!online && Boolean.TRUE.equals(input.visitFairteiler()));
        pickup.setSlotCount(online ? 1 : input.slotCount());
        pickup.setMinimumPickupCount(online ? null : input.minimumPickupCount());
    }

    void validate(PickupInput input) {
        if (input.category() == IntroPickupCategory.ONLINE) {
            String link = trim(input.onlineCallLink());
            if (link == null || link.length() > 2000
                    || !(link.startsWith("https://") || link.startsWith("http://"))) {
                throw ApiException.badRequest(ApiErrorCode.ONLINE_CALL_LINK_REQUIRED);
            }
            return;
        }
        if (input.slotCount() == null || input.slotCount() < 1
                || input.slotCount() > properties.getIntropickup().getMaxSlotCount()) {
            throw ApiException.badRequest(ApiErrorCode.SLOT_COUNT_OUT_OF_RANGE);
        }
        if (trim(input.privateAddress()) == null) {
            throw ApiException.badRequest(ApiErrorCode.LOCATION_REQUIRED);
        }
        if (trim(input.publicGeneralLocation()) == null) {
            throw ApiException.badRequest(ApiErrorCode.PUBLIC_LOCATION_REQUIRED);
        }
        if (trim(input.businessName()) == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_NAME_REQUIRED);
        }
        if (input.minimumPickupCount() != null && input.minimumPickupCount() < 0) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
    }

    private void createSlots(IntroPickup pickup, int count) {
        List<IntroPickupSlot> created = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            IntroPickupSlot slot = new IntroPickupSlot();
            slot.setIntroPickupId(pickup.getId());
            slot.setGuideId(pickup.getGuideId());
            slot.setStatus(SlotStatus.AVAILABLE);
            created.add(slot);
        }
        slots.saveAll(created);
    }

    private void saveTemplateIfRequested(UUID guideId, IntroPickup pickup, PickupInput input) {
        if (!Boolean.TRUE.equals(input.savePrivateInfoAsTemplate())) {
            return;
        }
        if (pickup.getPrivateInfo() == null) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        PrivateInfoTemplate template = templates.findByGuideIdAndCategory(guideId, pickup.getCategory())
                .orElseGet(PrivateInfoTemplate::new);
        template.setGuideId(guideId);
        template.setCategory(pickup.getCategory());
        template.setContent(pickup.getPrivateInfo());
        templates.save(template);
        history.record(history.event(IntroPickupAction.TEMPLATE_SAVED, pickup.getId()).region(pickup.getRegionId())
                .metadata("category", pickup.getCategory().name()).build());
    }

    private IntroPickup requireOwn(UUID introPickupId) {
        IntroPickup pickup = views.requirePickup(introPickupId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        if (!actor.userId().equals(pickup.getGuideId()) && !permissions.isOrga(actor)) {
            throw ApiException.forbidden(ApiErrorCode.ONLY_OWN_INTRO_PICKUPS_MANAGEABLE);
        }
        return pickup;
    }

    private void requireGuideOfSlot(IntroPickup pickup, IntroPickupSlot slot) {
        SessionPrincipal actor = currentActor.requirePrincipal();
        if (!isGuideOfSlot(actor.userId(), pickup, slot) && !permissions.isOrga(actor)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
    }

    private static boolean isGuideOfSlot(UUID userId, IntroPickup pickup, IntroPickupSlot slot) {
        return userId.equals(pickup.getGuideId()) || userId.equals(slot.getGuideId());
    }

    private static void requireBooking(IntroPickupSlot slot) {
        if (slot.getBookingUserId() == null) {
            throw ApiException.conflict(ApiErrorCode.BOOKING_NOT_FOUND);
        }
    }

    void requireGuideIn(UUID regionId) {
        regions.require(regionId);
        permissions.require(currentActor.requirePrincipal(), Permission.CAN_GIVE_INTRO_PICKUPS, regionId);
    }

    void requireGuideAnywhere() {
        SessionPrincipal actor = currentActor.requirePrincipal();
        if (!permissions.hasAnywhere(actor, Permission.CAN_GIVE_INTRO_PICKUPS)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String blankToNull(String value) {
        return trim(value);
    }

    /** Fields whose change booked users are told about. */
    private record Snapshot(IntroPickupCategory category, Instant startAt, String businessName, String privateAddress,
                            String publicGeneralLocation, String onlineCallLink, String privateInfo,
                            String publicInfo, boolean visitFairteiler, int slotCount, Integer minimumPickupCount) {

        static Snapshot of(IntroPickup p) {
            return new Snapshot(p.getCategory(), p.getStartAt(), p.getBusinessName(), p.getPrivateAddress(),
                    p.getPublicGeneralLocation(), p.getOnlineCallLink(), p.getPrivateInfo(), p.getPublicInfo(),
                    p.isVisitFairteiler(), p.getSlotCount(), p.getMinimumPickupCount());
        }

        List<String> changedFields(IntroPickup p) {
            Snapshot after = of(p);
            List<String> changed = new ArrayList<>();
            add(changed, "category", category, after.category);
            add(changed, "startAt", startAt, after.startAt);
            add(changed, "businessName", businessName, after.businessName);
            add(changed, "privateAddress", privateAddress, after.privateAddress);
            add(changed, "publicGeneralLocation", publicGeneralLocation, after.publicGeneralLocation);
            add(changed, "onlineCallLink", onlineCallLink, after.onlineCallLink);
            add(changed, "privateInfo", privateInfo, after.privateInfo);
            add(changed, "publicInfo", publicInfo, after.publicInfo);
            add(changed, "visitFairteiler", visitFairteiler, after.visitFairteiler);
            add(changed, "slotCount", slotCount, after.slotCount);
            add(changed, "minimumPickupCount", minimumPickupCount, after.minimumPickupCount);
            return changed;
        }

        private static void add(List<String> changed, String field, Object before, Object after) {
            if (!Objects.equals(before, after)) {
                changed.add(field);
            }
        }
    }
}
