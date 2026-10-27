package network.foodsharing.intropickup.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.intropickup.domain.IntroPickup;
import network.foodsharing.intropickup.domain.IntroPickupComment;
import network.foodsharing.intropickup.domain.IntroPickupSlot;
import network.foodsharing.intropickup.domain.SlotStatus;
import network.foodsharing.intropickup.repository.IntroPickupCommentRepository;
import network.foodsharing.intropickup.repository.IntroPickupRepository;
import network.foodsharing.intropickup.repository.IntroPickupSlotRepository;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loading helpers and read views shared by the EinAb services: EinAbs with their guide and slots, bookings with the
 * booking user and comment counts. "Counted" follows the reference {@code COUNTING_STATUSES}: DONE, and BOOKED once the
 * EinAb has started; CANCELLED never counts.
 */
@Component
public class IntroPickupViews {

    public record SlotView(IntroPickupSlot slot, User bookingUser, int commentCount) {
    }

    public record PickupView(IntroPickup pickup, User guide, List<SlotView> slots) {

        /** At least one slot is BOOKED or DONE. */
        public boolean booked() {
            return slots.stream().anyMatch(s -> s.slot().getStatus() == SlotStatus.BOOKED
                    || s.slot().getStatus() == SlotStatus.DONE);
        }
    }

    /** A booking (a slot with a user) together with its EinAb. */
    public record BookingView(SlotView slot, PickupView pickup, boolean counted) {
    }

    private final IntroPickupRepository pickups;
    private final IntroPickupSlotRepository slots;
    private final IntroPickupCommentRepository comments;
    private final UserService users;
    private final Clock clock;

    public IntroPickupViews(IntroPickupRepository pickups, IntroPickupSlotRepository slots,
                            IntroPickupCommentRepository comments, UserService users, Clock clock) {
        this.pickups = pickups;
        this.slots = slots;
        this.comments = comments;
        this.users = users;
        this.clock = clock;
    }

    public Instant now() {
        return clock.instant();
    }

    /** @throws ApiException 404 INTRO_PICKUP_NOT_FOUND (also for deleted EinAbs) */
    @Transactional(readOnly = true)
    public IntroPickup requirePickup(UUID id) {
        return pickups.findLive(id).orElseThrow(() -> ApiException.notFound(ApiErrorCode.INTRO_PICKUP_NOT_FOUND));
    }

    /** @throws ApiException 404 SLOT_NOT_FOUND (also when its EinAb was deleted) */
    @Transactional(readOnly = true)
    public IntroPickupSlot requireSlot(UUID slotId, boolean forUpdate) {
        IntroPickupSlot slot = (forUpdate ? slots.findForUpdate(slotId) : slots.findById(slotId))
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.SLOT_NOT_FOUND));
        if (pickups.findLive(slot.getIntroPickupId()).isEmpty()) {
            throw ApiException.notFound(ApiErrorCode.SLOT_NOT_FOUND);
        }
        return slot;
    }

    /** DONE, or BOOKED once the EinAb has started. */
    public static boolean counts(IntroPickupSlot slot, IntroPickup pickup, Instant now) {
        return slot.getStatus() == SlotStatus.DONE
                || (slot.getStatus() == SlotStatus.BOOKED && pickup.getStartAt().isBefore(now));
    }

    @Transactional(readOnly = true)
    public PickupView pickup(IntroPickup pickup) {
        return pickups(List.of(pickup)).getFirst();
    }

    @Transactional(readOnly = true)
    public List<PickupView> pickups(List<IntroPickup> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<IntroPickupSlot> allSlots = slots.findByPickups(list.stream().map(IntroPickup::getId).toList());
        Map<UUID, Integer> commentCounts = commentCounts(allSlots);
        Set<UUID> userIds = new HashSet<>();
        list.forEach(p -> userIds.add(p.getGuideId()));
        allSlots.stream().map(IntroPickupSlot::getBookingUserId).filter(java.util.Objects::nonNull)
                .forEach(userIds::add);
        Map<UUID, User> byId = usersById(userIds);
        Map<UUID, List<SlotView>> slotsByPickup = new HashMap<>();
        for (IntroPickupSlot slot : allSlots) {
            slotsByPickup.computeIfAbsent(slot.getIntroPickupId(), id -> new ArrayList<>())
                    .add(new SlotView(slot, slot.getBookingUserId() == null ? null : byId.get(slot.getBookingUserId()),
                            commentCounts.getOrDefault(slot.getId(), 0)));
        }
        return list.stream().map(p -> new PickupView(p, byId.get(p.getGuideId()),
                slotsByPickup.getOrDefault(p.getId(), List.of()))).toList();
    }

    /** Booking views of the given slots (each must have a booking user). */
    @Transactional(readOnly = true)
    public List<BookingView> bookings(Collection<IntroPickupSlot> bookingSlots) {
        if (bookingSlots.isEmpty()) {
            return List.of();
        }
        Map<UUID, PickupView> byPickup = pickups(pickups.findAllIn(bookingSlots.stream()
                .map(IntroPickupSlot::getIntroPickupId).distinct().toList())).stream()
                .collect(Collectors.toMap(v -> v.pickup().getId(), Function.identity()));
        Instant now = now();
        List<BookingView> result = new ArrayList<>();
        for (IntroPickupSlot slot : bookingSlots) {
            PickupView pickup = byPickup.get(slot.getIntroPickupId());
            if (pickup == null) {
                continue;
            }
            SlotView view = pickup.slots().stream().filter(s -> s.slot().getId().equals(slot.getId())).findFirst()
                    .orElseGet(() -> new SlotView(slot, null, 0));
            result.add(new BookingView(view, pickup, counts(slot, pickup.pickup(), now)));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public BookingView booking(IntroPickupSlot slot) {
        return bookings(List.of(slot)).getFirst();
    }

    public Map<UUID, User> usersById(Collection<UUID> ids) {
        return users.findAll(ids.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private Map<UUID, Integer> commentCounts(List<IntroPickupSlot> allSlots) {
        if (allSlots.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Integer> counts = new HashMap<>();
        for (IntroPickupComment comment : comments.findBySlotIdIn(allSlots.stream().map(IntroPickupSlot::getId)
                .toList())) {
            counts.merge(comment.getSlotId(), 1, Integer::sum);
        }
        return counts;
    }

    /** In-memory page of a sorted list (EinAb lists of one region are small). */
    public static <T> Page<T> page(List<T> all, Integer page, Integer size) {
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 20 : Math.min(Math.max(1, size), 100);
        int from = Math.min(all.size(), p * s);
        int to = Math.min(all.size(), from + s);
        return new PageImpl<>(all.subList(from, to), PageRequest.of(p, s), all.size());
    }
}
