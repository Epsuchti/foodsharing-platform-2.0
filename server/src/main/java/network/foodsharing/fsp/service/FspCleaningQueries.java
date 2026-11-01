package network.foodsharing.fsp.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.common.schedule.SlotSchedule;
import network.foodsharing.fsp.domain.FoodSharePoint;
import network.foodsharing.fsp.domain.FspCleaningSignup;
import network.foodsharing.fsp.repository.FoodSharePointRepository;
import network.foodsharing.fsp.repository.FspCleaningSignupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only cleaning data for other modules (the pickup policy's cleaning rule, AU1/FS2). No permission checks: the
 * callers are automations.
 */
@Service
@Transactional(readOnly = true)
public class FspCleaningQueries {

    /** A Fairteiler that counts for the cleaning rule. */
    public record CleaningFsp(UUID id, String name) {
    }

    /** A cleaning sign-up of a user. */
    public record CleaningSignup(UUID fspId, Instant shiftAt, boolean confirmed) {
    }

    /** A future shift with at least one free place. */
    public record FreeSlot(Instant at, UUID fspId, String fspName) {
    }

    private final FspCleaningRegionService regionLinks;
    private final FspCleaningScheduleService schedule;
    private final FspCleaningSignupRepository signups;
    private final FoodSharePointRepository fsps;

    public FspCleaningQueries(FspCleaningRegionService regionLinks, FspCleaningScheduleService schedule,
                              FspCleaningSignupRepository signups, FoodSharePointRepository fsps) {
        this.regionLinks = regionLinks;
        this.schedule = schedule;
        this.signups = signups;
        this.fsps = fsps;
    }

    /** The region's cleaning Fairteiler (ACTIVE, live, cleaning on), by name. */
    public List<CleaningFsp> cleaningFsps(UUID regionId) {
        return regionLinks.selected(regionId).stream().map(f -> new CleaningFsp(f.getId(), f.getName())).toList();
    }

    /** The user's latest confirmed shift in these Fairteiler before {@code before}. */
    public Optional<Instant> lastConfirmedCleaning(Collection<UUID> fspIds, UUID userId, Instant before) {
        if (fspIds.isEmpty()) {
            return Optional.empty();
        }
        return signups.findFirstByFspIdInAndUserIdAndConfirmedTrueAndShiftAtLessThanOrderByShiftAtDescIdDesc(fspIds,
                userId, before).map(FspCleaningSignup::getShiftAt);
    }

    /** The user's sign-ups (confirmed or not) in these Fairteiler with {@code from <= shiftAt < to}. */
    public List<CleaningSignup> cleaningSignupsOfUser(UUID userId, Collection<UUID> fspIds, Instant from, Instant to) {
        if (fspIds.isEmpty()) {
            return List.of();
        }
        return signups.findOfUserInFsps(userId, fspIds, from, to).stream()
                .map(s -> new CleaningSignup(s.getFspId(), s.getShiftAt(), s.isConfirmed())).toList();
    }

    /** Shifts with a free place and {@code from <= at < to} across these Fairteiler, by time, name and id. */
    public List<FreeSlot> freeCleaningSlots(Collection<UUID> fspIds, Instant from, Instant to) {
        if (fspIds.isEmpty()) {
            return List.of();
        }
        List<FoodSharePoint> list = fsps.findByIdInAndDeletedAtIsNullOrderByNameAscIdAsc(fspIds).stream()
                .filter(FoodSharePoint::isCleaningEnabled).toList();
        Map<UUID, List<SlotSchedule.Slot<FspCleaningSignup>>> byFsp = schedule.schedules(list, from, to);
        List<FreeSlot> result = new ArrayList<>();
        for (FoodSharePoint fsp : list) {
            for (SlotSchedule.Slot<FspCleaningSignup> slot : byFsp.getOrDefault(fsp.getId(), List.of())) {
                if (slot.slotCount() > 0 && slot.occupied() < slot.slotCount()) {
                    result.add(new FreeSlot(slot.at(), fsp.getId(), fsp.getName()));
                }
            }
        }
        result.sort(Comparator.comparing(FreeSlot::at).thenComparing(FreeSlot::fspName)
                .thenComparing(FreeSlot::fspId));
        return List.copyOf(result);
    }

    /** Path of the cleaning tab of a Fairteiler (without base URL). */
    public String cleaningPath(UUID fspId) {
        return FspCleaningScheduleService.link(fspId);
    }
}
