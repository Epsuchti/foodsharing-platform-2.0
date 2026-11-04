package network.foodsharing.fsp.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.admin.domain.FeatureToggle;
import network.foodsharing.admin.service.FeatureToggleService;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.schedule.SlotSchedule;
import network.foodsharing.fsp.domain.CleaningEligibilityReason;
import network.foodsharing.fsp.domain.FoodSharePoint;
import network.foodsharing.fsp.domain.FspAction;
import network.foodsharing.fsp.domain.FspCleaningRegular;
import network.foodsharing.fsp.domain.FspCleaningShift;
import network.foodsharing.fsp.domain.FspCleaningSignup;
import network.foodsharing.fsp.repository.FoodSharePointRepository;
import network.foodsharing.fsp.repository.FspCleaningRegularRepository;
import network.foodsharing.fsp.repository.FspCleaningShiftRepository;
import network.foodsharing.fsp.repository.FspCleaningSignupRepository;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cleaning shifts of a Fairteiler (FS2): the schedule in a range (regular occurrences in the region's zone, one-time
 * rows, sign-ups beyond the range) with the caller's eligibility, the weekly schedule and single-shift edits. The
 * arithmetic is {@link SlotSchedule}, shared with store pickups.
 */
@Service
public class FspCleaningScheduleService {

    /** Default list range: now .. now + 8 weeks. */
    static final Duration DEFAULT_RANGE = Duration.ofDays(56);
    static final Duration MAX_RANGE = Duration.ofDays(400);
    /** Open end of "sign-ups after the range" (not Instant.MAX: PostgreSQL rejects it, D17.1). */
    static final Instant OPEN_END = Instant.parse("9999-12-31T23:59:59Z");
    static final int MAX_SLOTS = 50;
    static final int MAX_DESCRIPTION = 100;
    private static final Comparator<FspCleaningRegular> ORDER = Comparator.comparing(FspCleaningRegular::getWeekday)
            .thenComparing(FspCleaningRegular::getStartTime);

    /** A weekly row as entered. */
    public record Entry(DayOfWeek weekday, String startTime, int slotCount, String description) {
    }

    /** A shift with the caller's eligibility ({@code reasons} null for past shifts). */
    public record ShiftView(SlotSchedule.Slot<FspCleaningSignup> slot, boolean available,
                            List<CleaningEligibilityReason> reasons) {
    }

    private final FoodSharePointRepository fsps;
    private final FspCleaningRegularRepository regular;
    private final FspCleaningShiftRepository shifts;
    private final FspCleaningSignupRepository signups;
    private final FspPermissionService access;
    private final CurrentActorService actors;
    private final UserService users;
    private final HistoryService history;
    private final FeatureToggleService toggles;
    private final Clock clock;

    public FspCleaningScheduleService(FoodSharePointRepository fsps, FspCleaningRegularRepository regular,
                                      FspCleaningShiftRepository shifts, FspCleaningSignupRepository signups,
                                      FspPermissionService access, CurrentActorService actors,
                                      UserService users, HistoryService history, FeatureToggleService toggles,
                                      Clock clock) {
        this.fsps = fsps;
        this.regular = regular;
        this.shifts = shifts;
        this.signups = signups;
        this.access = access;
        this.actors = actors;
        this.users = users;
        this.history = history;
        this.toggles = toggles;
        this.clock = clock;
    }

    public static String link(UUID fspId) {
        return FspService.link(fspId) + "/cleaning";
    }

    /** A live Fairteiler with cleaning switched on. @throws ApiException 404 FSP_NOT_FOUND, 409 CLEANING_NOT_ENABLED */
    FoodSharePoint requireCleaning(UUID fspId) {
        toggles.requireEnabled(FeatureToggle.FOOD_SHARE_POINTS);
        FoodSharePoint fsp = fsps.findById(fspId).filter(f -> f.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.FSP_NOT_FOUND));
        return requireEnabled(fsp);
    }

    static FoodSharePoint requireEnabled(FoodSharePoint fsp) {
        if (!fsp.isCleaningEnabled()) {
            throw ApiException.conflict(ApiErrorCode.CLEANING_NOT_ENABLED);
        }
        return fsp;
    }

    ZoneId zoneOf(FoodSharePoint fsp) {
        return ZoneId.of(fsp.getTimeZone());
    }

    /** The Fairteiler's shifts with {@code from <= shiftAt < to} (no permission check). */
    List<SlotSchedule.Slot<FspCleaningSignup>> schedule(FoodSharePoint fsp, Instant from, Instant to) {
        return schedules(List.of(fsp), from, to).getOrDefault(fsp.getId(), List.of());
    }

    /** Batch variant; one query per table. */
    Map<UUID, List<SlotSchedule.Slot<FspCleaningSignup>>> schedules(Collection<FoodSharePoint> list, Instant from,
                                                                    Instant to) {
        if (list.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = list.stream().map(FoodSharePoint::getId).toList();
        Map<UUID, List<FspCleaningRegular>> regularByFsp = regular.findByFspIdIn(ids).stream()
                .collect(Collectors.groupingBy(FspCleaningRegular::getFspId));
        Map<UUID, List<FspCleaningShift>> shiftsByFsp = shifts.findInRange(ids, from, to).stream()
                .collect(Collectors.groupingBy(FspCleaningShift::getFspId));
        Map<UUID, List<FspCleaningSignup>> signupsByFsp = signups.findInRange(ids, from, to).stream()
                .collect(Collectors.groupingBy(FspCleaningSignup::getFspId));
        Map<UUID, List<SlotSchedule.Slot<FspCleaningSignup>>> result = new HashMap<>();
        for (FoodSharePoint fsp : list) {
            List<SlotSchedule.Slot<FspCleaningSignup>> occurrences = SlotSchedule.regularOccurrences(
                    weekly(regularByFsp.getOrDefault(fsp.getId(), List.of())), zoneOf(fsp), from, to);
            result.put(fsp.getId(), merge(occurrences, shiftsByFsp.getOrDefault(fsp.getId(), List.of()),
                    signupsByFsp.getOrDefault(fsp.getId(), List.of())));
        }
        return result;
    }

    /** The shift at exactly this time, if the schedule has it. */
    Optional<SlotSchedule.Slot<FspCleaningSignup>> find(FoodSharePoint fsp, Instant shiftAt) {
        return schedule(fsp, shiftAt, shiftAt.plusMillis(1)).stream().filter(s -> s.at().equals(shiftAt)).findFirst();
    }

    /** The weekly schedule. @throws ApiException 404, 409 CLEANING_NOT_ENABLED */
    @Transactional(readOnly = true)
    public List<FspCleaningRegular> regular(UUID fspId) {
        FoodSharePoint fsp = requireCleaning(fspId);
        access.requireVisible(actors.requirePrincipal(), fsp);
        return regular.findByFspId(fspId).stream().sorted(ORDER).toList();
    }

    /**
     * Replaces the weekly schedule; history CLEANING_SCHEDULE_CHANGED (old / new as text) when something changed.
     *
     * @throws ApiException 404, 403, 409 CLEANING_NOT_ENABLED, 400 VALIDATION_FAILED / SLOT_COUNT_OUT_OF_RANGE /
     *                      DESCRIPTION_TOO_LONG / DUPLICATE_PICKUP_DAY_TIME
     */
    @Transactional
    public List<FspCleaningRegular> replaceRegular(UUID fspId, List<Entry> entries) {
        FoodSharePoint fsp = requireCleaning(fspId);
        FspPermissionService.require(FspCleaningRules.mayManage(access.requireVisible(actors.requirePrincipal(), fsp)));
        List<FspCleaningRegular> next = validate(fspId, entries == null ? List.of() : entries);
        List<FspCleaningRegular> previous = regular.findByFspId(fspId).stream().sorted(ORDER).toList();
        String before = describe(previous);
        String after = describe(next);
        if (before.equals(after)) {
            return previous;
        }
        UUID regionId = fsp.getRegionId();
        regular.deleteByFspId(fspId);
        List<FspCleaningRegular> saved = regular.saveAll(next).stream().sorted(ORDER).toList();
        history.record(history.event(FspAction.CLEANING_SCHEDULE_CHANGED, fspId).region(regionId)
                .change("regularShifts", before, after).build());
        return saved;
    }

    /**
     * Shifts of a Fairteiler; default range now .. now + 8 weeks plus sign-ups after the range. Future shifts carry the
     * caller's eligibility.
     *
     * @throws ApiException 404, 409 CLEANING_NOT_ENABLED, 400 VALIDATION_FAILED (from ≥ to) / RANGE_TOO_LARGE
     */
    @Transactional(readOnly = true)
    public List<ShiftView> list(UUID fspId, Instant from, Instant to) {
        FoodSharePoint fsp = requireCleaning(fspId);
        SessionPrincipal actor = actors.requirePrincipal();
        access.requireVisible(actor, fsp);
        Instant now = clock.instant();
        Instant start = from == null ? now : from;
        Instant end = to == null ? start.plus(DEFAULT_RANGE) : to;
        if (!start.isBefore(end)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        if (Duration.between(start, end).compareTo(MAX_RANGE) > 0) {
            throw ApiException.badRequest(ApiErrorCode.RANGE_TOO_LARGE);
        }
        List<SlotSchedule.Slot<FspCleaningSignup>> slots = new ArrayList<>(schedule(fsp, start, end));
        if (to == null) {
            List<FspCleaningSignup> later = signups.findInRange(List.of(fspId), end, OPEN_END);
            if (!later.isEmpty()) {
                Set<Instant> times = later.stream().map(FspCleaningSignup::getShiftAt).collect(Collectors.toSet());
                List<FspCleaningShift> laterRows = shifts.findInRange(List.of(fspId), end, OPEN_END).stream()
                        .filter(s -> times.contains(s.getShiftAt())).toList();
                slots.addAll(merge(List.of(), laterRows, later));
            }
        }
        User user = users.require(actor.userId());
        ZoneId zone = zoneOf(fsp);
        return slots.stream().map(s -> new ShiftView(s, s.isAvailable(now), s.at().isBefore(now) ? null
                : FspCleaningRules.eligibility(person(user, s.at(), zone), s.at(), now, s))).toList();
    }

    FspCleaningRules.Person person(User user, Instant shiftAt, ZoneId zone) {
        return new FspCleaningRules.Person(user.getId(), user.isVerified(),
                user.isSleeping(shiftAt.atZone(zone).toLocalDate()));
    }

    /**
     * Sets slot count / description of one future shift; creates the one-time row when there is none (also adds a
     * shift at a time without occurrence). 0 slots cancel it. History CLEANING_SCHEDULE_CHANGED.
     *
     * @throws ApiException 404, 403, 409 CLEANING_NOT_ENABLED / MORE_OCCUPIED_SLOTS, 400 PICKUP_IN_PAST /
     *                      SLOT_COUNT_OUT_OF_RANGE / DESCRIPTION_TOO_LONG
     */
    @Transactional
    public SlotSchedule.Slot<FspCleaningSignup> edit(UUID fspId, Instant shiftAt, int slotCount, String description) {
        requireCleaning(fspId);
        FoodSharePoint fsp = lock(fspId);
        FspPermissionService.require(FspCleaningRules.mayManage(access.requireVisible(actors.requirePrincipal(), fsp)));
        if (shiftAt.isBefore(clock.instant())) {
            throw ApiException.badRequest(ApiErrorCode.PICKUP_IN_PAST);
        }
        if (slotCount < 0 || slotCount > MAX_SLOTS) {
            throw ApiException.badRequest(ApiErrorCode.SLOT_COUNT_OUT_OF_RANGE);
        }
        String text = strip(description);
        if (text != null && text.length() > MAX_DESCRIPTION) {
            throw ApiException.badRequest(ApiErrorCode.DESCRIPTION_TOO_LONG);
        }
        SlotSchedule.Slot<FspCleaningSignup> current = find(fsp, shiftAt).orElse(null);
        if (current != null && current.occupied() > slotCount) {
            throw ApiException.conflict(ApiErrorCode.MORE_OCCUPIED_SLOTS);
        }
        FspCleaningShift row = shifts.findByFspIdAndShiftAt(fspId, shiftAt).orElseGet(() -> {
            FspCleaningShift s = new FspCleaningShift();
            s.setFspId(fspId);
            s.setShiftAt(shiftAt);
            return s;
        });
        int previousCount = current == null ? 0 : current.slotCount();
        String previousDescription = current == null ? null : current.description();
        row.setSlotCount(slotCount);
        row.setDescription(text);
        shifts.save(row);
        if (current == null || previousCount != slotCount || !Objects.equals(previousDescription, text)) {
            history.record(history.event(FspAction.CLEANING_SCHEDULE_CHANGED, fspId).region(fsp.getRegionId())
                    .metadata("shiftAt", shiftAt.toString()).metadata("previousCount", previousCount)
                    .metadata("newCount", slotCount).build());
        }
        shifts.flush();
        return find(fsp, shiftAt).orElseGet(() -> new SlotSchedule.Slot<>(shiftAt, 0, text,
                current != null && current.regular(), List.of()));
    }

    /** Row lock on the Fairteiler (serialises sign-ups and slot edits). */
    FoodSharePoint lock(UUID fspId) {
        return fsps.lockById(fspId).orElseThrow(() -> ApiException.notFound(ApiErrorCode.FSP_NOT_FOUND));
    }

    static List<SlotSchedule.Weekly> weekly(Collection<FspCleaningRegular> rows) {
        return rows.stream().map(r -> new SlotSchedule.Weekly(r.getWeekday(), r.getStartTime(), r.getSlotCount(),
                r.getDescription())).toList();
    }

    static List<SlotSchedule.Slot<FspCleaningSignup>> merge(List<SlotSchedule.Slot<FspCleaningSignup>> occurrences,
                                                            Collection<FspCleaningShift> rows,
                                                            Collection<FspCleaningSignup> signupRows) {
        return SlotSchedule.merge(occurrences, rows.stream().map(r -> new SlotSchedule.OneTime(r.getShiftAt(),
                        r.getSlotCount(), r.getDescription())).toList(), signupRows, FspCleaningSignup::getShiftAt,
                FspCleaningSignup::getCreatedAt, FspCleaningSignup::getId);
    }

    static List<FspCleaningRegular> validate(UUID fspId, List<Entry> entries) {
        if (entries.size() > MAX_SLOTS) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        List<FspCleaningRegular> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Entry e : entries) {
            if (e == null || e.weekday() == null || e.startTime() == null) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
            }
            LocalTime time;
            try {
                time = LocalTime.parse(e.startTime().strip());
            } catch (DateTimeParseException ex) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
            }
            if (time.getSecond() != 0 || time.getNano() != 0) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
            }
            if (e.slotCount() < 0 || e.slotCount() > MAX_SLOTS) {
                throw ApiException.badRequest(ApiErrorCode.SLOT_COUNT_OUT_OF_RANGE);
            }
            String description = strip(e.description());
            if (description != null && description.length() > MAX_DESCRIPTION) {
                throw ApiException.badRequest(ApiErrorCode.DESCRIPTION_TOO_LONG);
            }
            if (!seen.add(e.weekday() + "@" + time)) {
                throw ApiException.badRequest(ApiErrorCode.DUPLICATE_PICKUP_DAY_TIME);
            }
            FspCleaningRegular r = new FspCleaningRegular();
            r.setFspId(fspId);
            r.setWeekday(e.weekday());
            r.setStartTime(time);
            r.setSlotCount(e.slotCount());
            r.setDescription(description);
            result.add(r);
        }
        result.sort(ORDER);
        return result;
    }

    static String describe(List<FspCleaningRegular> list) {
        return String.join("; ", list.stream().sorted(ORDER).map(r -> r.getWeekday() + " " + r.getStartTime() + " ×"
                + r.getSlotCount() + (r.getDescription() == null ? "" : " (" + r.getDescription() + ")")).toList());
    }

    private static String strip(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
