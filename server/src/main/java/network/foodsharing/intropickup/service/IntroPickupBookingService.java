package network.foodsharing.intropickup.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.intropickup.domain.IntroPickup;
import network.foodsharing.intropickup.domain.IntroPickupAction;
import network.foodsharing.intropickup.domain.IntroPickupCategory;
import network.foodsharing.intropickup.domain.IntroPickupSlot;
import network.foodsharing.intropickup.domain.SlotStatus;
import network.foodsharing.intropickup.repository.IntroPickupRepository;
import network.foodsharing.intropickup.repository.IntroPickupSlotRepository;
import network.foodsharing.intropickup.service.IntroPickupViews.BookingView;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionPermissionResolver;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.region.service.RegionSettingsService;
import network.foodsharing.region.service.RegionSettingsService.IntroPickupSettings;
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
 * Booking of EinAb slots by newcomers (plan §14.1 EA5–EA8): the bookable list with the reason a user may not book,
 * booking (by the user or by the guide for a user), own bookings and cancelling them.
 */
@Service
public class IntroPickupBookingService {

    /** After commit: confirmation to the user and the guide. */
    public record IntroPickupBooked(UUID slotId, UUID introPickupId, UUID userId, UUID guideId, boolean byGuide) {
    }

    /** After commit: message to the guide. */
    public record BookingCancelledByUser(UUID introPickupId, UUID userId, UUID guideId) {
    }

    public record BookableView(IntroPickup pickup, User guide, int freeSlots, UUID firstFreeSlotId,
                               ApiErrorCode notBookableReason) {
    }

    private final IntroPickupRepository pickups;
    private final IntroPickupSlotRepository slots;
    private final IntroPickupViews views;
    private final RegionService regions;
    private final RegionSettingsService settings;
    private final RegionPermissionResolver resolver;
    private final UserService users;
    private final CurrentActorService currentActor;
    private final PermissionService permissions;
    private final HistoryService history;
    private final ApplicationEventPublisher events;

    public IntroPickupBookingService(IntroPickupRepository pickups, IntroPickupSlotRepository slots,
                                     IntroPickupViews views, RegionService regions, RegionSettingsService settings,
                                     RegionPermissionResolver resolver, UserService users,
                                     CurrentActorService currentActor, PermissionService permissions,
                                     HistoryService history, ApplicationEventPublisher events) {
        this.pickups = pickups;
        this.slots = slots;
        this.views = views;
        this.regions = regions;
        this.settings = settings;
        this.resolver = resolver;
        this.users = users;
        this.currentActor = currentActor;
        this.permissions = permissions;
        this.history = history;
        this.events = events;
    }

    /**
     * Upcoming EinAbs of the region with free places, each with whether the current user may book it.
     *
     * @throws ApiException 404 REGION_NOT_FOUND, 403 NOT_REGION_MEMBER
     */
    @Transactional(readOnly = true)
    public Page<BookableView> bookable(UUID regionId, String search, IntroPickupCategory category,
                                       Boolean visitFairteiler, Integer page, Integer size) {
        Region region = regions.require(regionId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        if (!permissions.isOrga(actor) && !permissions.isMemberOf(actor, regionId)) {
            throw ApiException.forbidden(ApiErrorCode.NOT_REGION_MEMBER);
        }
        User user = users.require(actor.userId());
        IntroPickupSettings rules = settings.read(region).introPickups();
        List<BookingRules.Booking> bookings = bookingsOf(user.getId(), regionId);
        BookingRules.Booker booker = booker(user, regionId);
        String needle = search == null || search.isBlank() ? null : search.trim().toLowerCase(Locale.ROOT);
        List<BookableView> result = new ArrayList<>();
        for (IntroPickupViews.PickupView view : views.pickups(pickups.findUpcoming(regionId, views.now()))) {
            IntroPickup pickup = view.pickup();
            List<IntroPickupSlot> free = view.slots().stream().map(IntroPickupViews.SlotView::slot)
                    .filter(slot -> slot.getStatus() == SlotStatus.AVAILABLE).toList();
            if (free.isEmpty()
                    || (category != null && pickup.getCategory() != category)
                    || (visitFairteiler != null && pickup.isVisitFairteiler() != visitFairteiler)
                    || (needle != null && !matches(view, needle))) {
                continue;
            }
            ApiErrorCode reason = BookingRules.check(booker, true, pickup, bookings, rules).orElse(null);
            result.add(new BookableView(pickup, view.guide(), pickup.isOnline() ? 1 : free.size(),
                    free.getFirst().getId(), reason));
        }
        return IntroPickupViews.page(result, page, size);
    }

    /** The first failing booking rule for a user and a (free) EinAb, or empty (notifications use this). */
    @Transactional(readOnly = true)
    public Optional<ApiErrorCode> notBookableReason(UUID userId, IntroPickup pickup) {
        Region region = regions.require(pickup.getRegionId());
        User user = users.require(userId);
        return BookingRules.check(booker(user, region.getId()), true, pickup, bookingsOf(userId, region.getId()),
                settings.read(region).introPickups());
    }

    /**
     * The current user books a slot.
     *
     * @throws ApiException 404 SLOT_NOT_FOUND, 403 / 409 for the booking rules ({@link BookingRules})
     */
    @Transactional
    public BookingView book(UUID slotId) {
        UUID userId = currentActor.requireUserId();
        IntroPickupSlot slot = bookSlot(slotId, userId, false);
        history.record(history.event(IntroPickupAction.SLOT_BOOKED, slot.getIntroPickupId()).secondarySubject(userId)
                .region(pickups.findById(slot.getIntroPickupId()).map(IntroPickup::getRegionId).orElse(null))
                .metadata("slotId", slot.getId().toString()).build());
        return views.booking(slot);
    }

    /**
     * The guide of the EinAb books a slot for a user (same rules).
     *
     * @throws ApiException 404 SLOT_NOT_FOUND / USER_NOT_FOUND, 403 ACCESS_DENIED, 403 / 409 booking rules
     */
    @Transactional
    public BookingView bookFor(UUID slotId, UUID userId) {
        IntroPickupSlot existing = views.requireSlot(slotId, false);
        IntroPickup pickup = views.requirePickup(existing.getIntroPickupId());
        requireGuideOf(pickup, existing);
        users.require(userId);
        IntroPickupSlot slot = bookSlot(slotId, userId, true);
        history.record(history.event(IntroPickupAction.SLOT_BOOKED_BY_GUIDE, pickup.getId()).secondarySubject(userId)
                .region(pickup.getRegionId()).metadata("slotId", slot.getId().toString()).build());
        return views.booking(slot);
    }

    private IntroPickupSlot bookSlot(UUID slotId, UUID userId, boolean byGuide) {
        IntroPickupSlot slot = views.requireSlot(slotId, true);
        IntroPickup pickup = views.requirePickup(slot.getIntroPickupId());
        Region region = regions.require(pickup.getRegionId());
        User user = users.require(userId);
        Optional<ApiErrorCode> violation = BookingRules.check(booker(user, region.getId()),
                slot.getStatus() == SlotStatus.AVAILABLE && !pickup.getStartAt().isBefore(views.now()), pickup,
                bookingsOf(userId, region.getId()), settings.read(region).introPickups());
        if (violation.isPresent()) {
            throw BookingRules.toException(violation.get());
        }
        slot.setBookingUserId(userId);
        slot.setStatus(SlotStatus.BOOKED);
        slot.setBookedAt(views.now());
        slot.setDoneAt(null);
        if (pickup.isOnline()) {
            // Online EinAbs have unlimited participants: a fresh place replaces the booked one.
            IntroPickupSlot next = new IntroPickupSlot();
            next.setIntroPickupId(pickup.getId());
            next.setGuideId(pickup.getGuideId());
            next.setStatus(SlotStatus.AVAILABLE);
            slots.save(next);
        }
        events.publishEvent(new IntroPickupBooked(slot.getId(), pickup.getId(), userId, pickup.getGuideId(), byGuide));
        return slot;
    }

    /** Booked EinAbs of a user starting in [from, to) (pickup agenda, B5; the caller checks access). */
    @Transactional(readOnly = true)
    public List<BookingView> bookingsOf(UUID userId, Instant from, Instant to) {
        return views.bookings(slots.findBookingsOfUser(userId)).stream()
                .filter(b -> b.slot().slot().getStatus() == SlotStatus.BOOKED)
                .filter(b -> !b.pickup().pickup().getStartAt().isBefore(from)
                        && b.pickup().pickup().getStartAt().isBefore(to))
                .sorted(Comparator.comparing((BookingView b) -> b.pickup().pickup().getStartAt())).toList();
    }

    /** Own bookings of the current user (upcoming or past), all regions. */
    @Transactional(readOnly = true)
    public List<BookingView> myBookings(boolean past) {
        UUID userId = currentActor.requireUserId();
        Instant now = views.now();
        return views.bookings(slots.findBookingsOfUser(userId)).stream()
                .filter(b -> b.pickup().pickup().getStartAt().isBefore(now) == past)
                .filter(b -> past || b.slot().slot().getStatus() == SlotStatus.BOOKED)
                .sorted(past ? Comparator.comparing((BookingView b) -> b.pickup().pickup().getStartAt()).reversed()
                        : Comparator.comparing((BookingView b) -> b.pickup().pickup().getStartAt()))
                .toList();
    }

    /**
     * The user cancels an own upcoming booking; the place becomes available again (an extra online place is removed).
     *
     * @throws ApiException 404 SLOT_NOT_FOUND, 403 ONLY_OWN_BOOKINGS_CANCELLABLE, 409 ONLY_BOOKED_APPOINTMENTS_CANCELLABLE
     *                      / PAST_BOOKING_NOT_CANCELLABLE
     */
    @Transactional
    public void cancelMine(UUID slotId) {
        UUID userId = currentActor.requireUserId();
        IntroPickupSlot slot = views.requireSlot(slotId, true);
        if (!userId.equals(slot.getBookingUserId())) {
            throw ApiException.forbidden(ApiErrorCode.ONLY_OWN_BOOKINGS_CANCELLABLE);
        }
        if (slot.getStatus() != SlotStatus.BOOKED) {
            throw ApiException.conflict(ApiErrorCode.ONLY_BOOKED_APPOINTMENTS_CANCELLABLE);
        }
        IntroPickup pickup = views.requirePickup(slot.getIntroPickupId());
        if (pickup.getStartAt().isBefore(views.now())) {
            throw ApiException.conflict(ApiErrorCode.PAST_BOOKING_NOT_CANCELLABLE);
        }
        boolean otherFreeOnlineSlot = pickup.isOnline() && slots.findByPickup(pickup.getId()).stream()
                .anyMatch(other -> !other.getId().equals(slot.getId()) && other.getStatus() == SlotStatus.AVAILABLE);
        if (otherFreeOnlineSlot) {
            slots.delete(slot);
        } else {
            slot.setStatus(SlotStatus.AVAILABLE);
            slot.setBookingUserId(null);
            slot.setBookedAt(null);
            slot.setDoneAt(null);
        }
        history.record(history.event(IntroPickupAction.BOOKING_CANCELLED_BY_USER, pickup.getId())
                .secondarySubject(userId).region(pickup.getRegionId()).build());
        events.publishEvent(new BookingCancelledByUser(pickup.getId(), userId, pickup.getGuideId()));
    }

    // ------------------------------------------------------------------------------------------------ helpers

    /** Active (BOOKED / DONE) and other bookings of the user in the region, as rule input. */
    List<BookingRules.Booking> bookingsOf(UUID userId, UUID regionId) {
        List<IntroPickupSlot> booked = slots.findBookingsOfUserInRegion(userId, regionId);
        if (booked.isEmpty()) {
            return List.of();
        }
        Map<UUID, IntroPickup> byId = pickups.findAllIn(booked.stream().map(IntroPickupSlot::getIntroPickupId)
                .distinct().toList()).stream().collect(Collectors.toMap(IntroPickup::getId, Function.identity()));
        return booked.stream().filter(slot -> byId.containsKey(slot.getIntroPickupId())).map(slot -> {
            IntroPickup pickup = byId.get(slot.getIntroPickupId());
            return new BookingRules.Booking(pickup.getId(), pickup.getCategory(), pickup.getGuideId(),
                    pickup.isVisitFairteiler(), slot.getStatus());
        }).toList();
    }

    private BookingRules.Booker booker(User user, UUID regionId) {
        return new BookingRules.Booker(user.isActive(), resolver.isMember(user.getId(), regionId),
                resolver.hasRegionPermission(user.getId(), Permission.CAN_GIVE_INTRO_PICKUPS, regionId));
    }

    /** The guide of the EinAb (or of the slot); ORGA always. */
    void requireGuideOf(IntroPickup pickup, IntroPickupSlot slot) {
        SessionPrincipal actor = currentActor.requirePrincipal();
        boolean guide = actor.userId().equals(pickup.getGuideId())
                || (slot != null && actor.userId().equals(slot.getGuideId()));
        if (!guide && !permissions.isOrga(actor)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
    }

    private static boolean matches(IntroPickupViews.PickupView view, String needle) {
        return contains(view.pickup().getBusinessName(), needle)
                || contains(view.pickup().getPublicGeneralLocation(), needle)
                || contains(view.pickup().getPublicInfo(), needle)
                || (view.guide() != null && contains(view.guide().getDisplayName(), needle));
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }
}
