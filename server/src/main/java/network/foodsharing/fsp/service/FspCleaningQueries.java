package network.foodsharing.fsp.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import network.foodsharing.common.schedule.SlotSchedule;
import network.foodsharing.fsp.domain.FoodSharePoint;
import network.foodsharing.fsp.domain.FspCleaningShift;
import network.foodsharing.fsp.domain.FspCleaningSignup;
import network.foodsharing.fsp.repository.FoodSharePointRepository;
import network.foodsharing.fsp.repository.FspCleaningShiftRepository;
import network.foodsharing.fsp.repository.FspCleaningSignupRepository;
import network.foodsharing.security.CurrentActorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only cleaning data for other modules (the pickup policy's cleaning rule, AU1/FS2; agenda and calendar feed,
 * decision 154). No permission checks: the callers check them or are automations.
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

    /** A cleaning shift seen from a person who signed up: Fairteiler, time, confirmation, text and address. */
    public record UserShift(UUID fspId, String fspName, String address, Instant shiftAt, boolean confirmed,
                            String description) {
    }

    private final FspCleaningRegionService regionLinks;
    private final FspCleaningScheduleService schedule;
    private final FspCleaningSignupRepository signups;
    private final FspCleaningShiftRepository shifts;
    private final FoodSharePointRepository fsps;
    private final CurrentActorService actors;
    private final Clock clock;

    public FspCleaningQueries(FspCleaningRegionService regionLinks, FspCleaningScheduleService schedule,
                              FspCleaningSignupRepository signups, FspCleaningShiftRepository shifts,
                              FoodSharePointRepository fsps, CurrentActorService actors, Clock clock) {
        this.regionLinks = regionLinks;
        this.schedule = schedule;
        this.signups = signups;
        this.shifts = shifts;
        this.fsps = fsps;
        this.actors = actors;
        this.clock = clock;
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

    /**
     * The user's cleaning shifts with {@code from <= shiftAt < to}, by time; shifts of deleted Fairteiler and
     * cancelled shifts (0 places) are left out.
     */
    public List<UserShift> shiftsOfUser(UUID userId, Instant from, Instant to) {
        List<FspCleaningSignup> rows = signups
                .findByUserIdAndShiftAtGreaterThanEqualAndShiftAtLessThanOrderByShiftAtAscIdAsc(userId, from, to);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, FoodSharePoint> byId = new HashMap<>();
        fsps.findByIdInAndDeletedAtIsNullOrderByNameAscIdAsc(rows.stream().map(FspCleaningSignup::getFspId)
                .collect(Collectors.toSet())).forEach(fsp -> byId.put(fsp.getId(), fsp));
        Map<String, FspCleaningShift> shiftRows = new HashMap<>();
        shifts.findInRange(byId.keySet(), from, to).forEach(s -> shiftRows.put(s.getFspId() + "@" + s.getShiftAt(), s));
        List<UserShift> result = new ArrayList<>();
        for (FspCleaningSignup row : rows) {
            FoodSharePoint fsp = byId.get(row.getFspId());
            FspCleaningShift shift = shiftRows.get(row.getFspId() + "@" + row.getShiftAt());
            if (fsp != null && (shift == null || shift.getSlotCount() > 0)) {
                result.add(new UserShift(fsp.getId(), fsp.getName(), address(fsp), row.getShiftAt(), row.isConfirmed(),
                        shift == null ? null : shift.getDescription()));
            }
        }
        return List.copyOf(result);
    }

    /** Upcoming cleaning shifts of the current user. */
    public List<UserShift> myShifts() {
        return shiftsOfUser(actors.requireUserId(), clock.instant(), Instant.parse("9999-12-31T00:00:00Z"));
    }

    private static String address(FoodSharePoint fsp) {
        String place = Stream.of(fsp.getPostcode(), fsp.getCity()).filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
        return Stream.of(fsp.getStreet(), place).filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(", "));
    }

    /** Path of the cleaning tab of a Fairteiler (without base URL). */
    public String cleaningPath(UUID fspId) {
        return FspCleaningScheduleService.link(fspId);
    }
}
