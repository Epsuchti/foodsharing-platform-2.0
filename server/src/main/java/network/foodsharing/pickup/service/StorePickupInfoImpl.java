package network.foodsharing.pickup.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.NoHistory;
import network.foodsharing.pickup.domain.PickupSignup;
import network.foodsharing.pickup.repository.PickupSignupRepository;
import network.foodsharing.store.domain.FreeSlotStatus;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StorePickupInfo;
import network.foodsharing.store.service.StoreService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The pickup side of store lists and team lists (replaces the no-op default of the store module): next pickup, free
 * slot traffic light, member pickup numbers, removing future sign-ups of leaving members, the delete guard.
 */
@Service
@Primary
public class StorePickupInfoImpl implements StorePickupInfo {

    private final PickupScheduleService schedule;
    private final PickupSignupRepository signups;
    private final StoreService storeQueries;
    private final AppProperties properties;
    private final Clock clock;

    public StorePickupInfoImpl(PickupScheduleService schedule, PickupSignupRepository signups,
                               StoreService storeQueries, AppProperties properties, Clock clock) {
        this.schedule = schedule;
        this.signups = signups;
        this.storeQueries = storeQueries;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, SlotInfo> slotInfo(Collection<UUID> storeIds) {
        List<Store> stores = storeQueries.liveStores(storeIds);
        if (stores.isEmpty()) {
            return Map.of();
        }
        Instant now = clock.instant();
        int maxPrefetch = stores.stream().mapToInt(Store::getPrefetchDays).max().orElse(14);
        Map<UUID, List<PickupSchedule.Slot>> all = schedule.schedules(stores, now,
                now.plus(Duration.ofDays(maxPrefetch)));
        Map<UUID, SlotInfo> result = new HashMap<>();
        for (Store store : stores) {
            Instant horizon = now.plus(Duration.ofDays(store.getPrefetchDays()));
            List<PickupSchedule.Slot> slots = all.getOrDefault(store.getId(), List.of()).stream()
                    .filter(s -> s.pickupAt().isAfter(now) && !s.pickupAt().isAfter(horizon)).toList();
            result.put(store.getId(), slotInfo(store, slots, now));
        }
        return result;
    }

    SlotInfo slotInfo(Store store, List<PickupSchedule.Slot> futureSlots, Instant now) {
        Instant next = futureSlots.stream().filter(s -> s.slotCount() > 0).map(PickupSchedule.Slot::pickupAt)
                .findFirst().orElse(null);
        int free = futureSlots.stream().mapToInt(s -> Math.max(0, s.slotCount() - s.occupied())).sum();
        Instant firstFree = futureSlots.stream().filter(s -> s.isAvailable(now)).map(PickupSchedule.Slot::pickupAt)
                .findFirst().orElse(null);
        return new SlotInfo(next, freeSlotStatus(firstFree, now, schedule.zoneOf(store),
                properties.getPickup().getFreeSlotStatusDays()), free);
    }

    /** RED: a free slot today or tomorrow; ORANGE: within 3 days; YELLOW: within {@code days}; else GREEN. */
    static FreeSlotStatus freeSlotStatus(Instant firstFree, Instant now, java.time.ZoneId zone, int days) {
        if (firstFree == null) {
            return FreeSlotStatus.GREEN;
        }
        long diff = ChronoUnit.DAYS.between(now.atZone(zone).toLocalDate(), firstFree.atZone(zone).toLocalDate());
        if (diff <= 1) {
            return FreeSlotStatus.RED;
        }
        if (diff <= 3) {
            return FreeSlotStatus.ORANGE;
        }
        return diff <= days ? FreeSlotStatus.YELLOW : FreeSlotStatus.GREEN;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, MemberStats> memberStats(UUID storeId) {
        return signups.memberStats(storeId, clock.instant()).stream().collect(Collectors.toMap(
                PickupSignupRepository.MemberStatsRow::getUserId,
                r -> new MemberStats((int) r.getPickupCount(), r.getFirstPickupAt(), r.getLastPickupAt())));
    }

    @Override
    @Transactional
    @NoHistory(reason = "called by StoreTeamService.remove, which records LEFT_STORE / REMOVED_FROM_STORE")
    public int deleteFutureSignups(UUID storeId, UUID userId) {
        List<PickupSignup> future = signups.findFutureOfUser(storeId, userId, clock.instant());
        signups.deleteAll(future);
        return future.size();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasSignups(UUID storeId) {
        return signups.existsByStoreId(storeId);
    }

    @Override
    public List<PastSignup> confirmedSignups(UUID storeId, Instant from, Instant to) {
        return signups.findConfirmedInStores(List.of(storeId), from, to).stream()
                .map(s -> new PastSignup(s.getUserId(), s.getPickupAt())).toList();
    }
}
