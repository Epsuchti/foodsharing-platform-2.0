package network.foodsharing.fsp.mapper;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.api.model.CleaningShift;
import network.foodsharing.api.model.CleaningTeam;
import network.foodsharing.api.model.CleaningTeamMember;
import network.foodsharing.api.model.FspRef;
import network.foodsharing.api.model.PickupEligibility;
import network.foodsharing.api.model.PickupEligibilityReason;
import network.foodsharing.api.model.PickupSlot;
import network.foodsharing.api.model.RegionCleaningFsps;
import network.foodsharing.api.model.RegularPickup;
import network.foodsharing.api.model.Weekday;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.common.schedule.SlotSchedule;
import network.foodsharing.fsp.domain.CleaningEligibilityReason;
import network.foodsharing.fsp.domain.FoodSharePoint;
import network.foodsharing.fsp.domain.FspCleaningRegular;
import network.foodsharing.fsp.domain.FspCleaningSignup;
import network.foodsharing.fsp.service.FspCleaningRegionService;
import network.foodsharing.fsp.service.FspCleaningScheduleService;
import network.foodsharing.fsp.service.FspCleaningSignupService;
import network.foodsharing.fsp.service.FspCleaningTeamService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.mapper.UserMapper;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Component;

/** Cleaning shifts (FS2) ↔ generated models; slots and the weekly schedule reuse the pickup models. */
@Component
public class FspCleaningMapper {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final UserService users;
    private final UserMapper userMapper;

    public FspCleaningMapper(UserService users, UserMapper userMapper) {
        this.users = users;
        this.userMapper = userMapper;
    }

    public static Instant instant(java.time.OffsetDateTime time) {
        return TimeMapper.toInstant(time);
    }

    public List<RegularPickup> toRegularModels(List<FspCleaningRegular> list) {
        return list.stream().map(r -> {
            RegularPickup model = new RegularPickup();
            model.setWeekday(Weekday.fromValue(r.getWeekday().name()));
            model.setStartTime(HH_MM.format(r.getStartTime()));
            model.setSlotCount(r.getSlotCount());
            model.setDescription(r.getDescription());
            return model;
        }).toList();
    }

    public static FspCleaningScheduleService.Entry toEntry(RegularPickup model) {
        return model == null ? null : new FspCleaningScheduleService.Entry(
                model.getWeekday() == null ? null : DayOfWeek.valueOf(model.getWeekday().getValue()),
                model.getStartTime(), model.getSlotCount() == null ? -1 : model.getSlotCount(),
                model.getDescription());
    }

    public List<CleaningShift> toShifts(UUID fspId, List<FspCleaningScheduleService.ShiftView> views) {
        Map<UUID, User> byId = usersOf(views.stream().map(FspCleaningScheduleService.ShiftView::slot).toList());
        return views.stream().map(v -> toShift(fspId, v.slot(), v.available(), v.reasons(), byId)).toList();
    }

    public CleaningShift toShift(UUID fspId, SlotSchedule.Slot<FspCleaningSignup> slot, boolean available) {
        return toShift(fspId, slot, available, null, usersOf(List.of(slot)));
    }

    public static PickupEligibilityReason reason(CleaningEligibilityReason reason) {
        return PickupEligibilityReason.fromValue(FspCleaningSignupService.contractReason(reason));
    }

    public CleaningTeam toTeam(FspCleaningTeamService.Team team) {
        return new CleaningTeam().totalShifts(team.totalShifts()).members(team.members().stream()
                .map(m -> new CleaningTeamMember().user(userMapper.toSummary(m.user())).manager(m.manager())
                        .shiftCount(m.shiftCount()).upcomingCount(m.upcomingCount())
                        .lastShiftAt(TimeMapper.toOffset(m.lastShiftAt())))
                .toList());
    }

    public RegionCleaningFsps toRegion(FspCleaningRegionService.Selection selection) {
        return new RegionCleaningFsps().selected(refs(selection.selected())).candidates(refs(selection.candidates()));
    }

    public static List<FspRef> refs(List<FoodSharePoint> fsps) {
        return fsps.stream().map(f -> new FspRef().id(f.getId()).name(f.getName())).toList();
    }

    private CleaningShift toShift(UUID fspId, SlotSchedule.Slot<FspCleaningSignup> slot, boolean available,
                                  List<CleaningEligibilityReason> reasons, Map<UUID, User> byId) {
        CleaningShift model = new CleaningShift().fspId(fspId).shiftAt(TimeMapper.toOffset(slot.at()))
                .slotCount(slot.slotCount()).description(slot.description()).isAvailable(available)
                .isRegular(slot.regular()).occupiedSlots(slot.signups().stream()
                        .filter(s -> byId.containsKey(s.getUserId()))
                        .map(s -> new PickupSlot().user(userMapper.toSummary(byId.get(s.getUserId())))
                                .confirmed(s.isConfirmed())).toList());
        if (reasons != null) {
            model.setEligibility(new PickupEligibility().eligible(reasons.isEmpty())
                    .reasons(reasons.stream().map(FspCleaningMapper::reason).toList()));
        }
        return model;
    }

    private Map<UUID, User> usersOf(Collection<SlotSchedule.Slot<FspCleaningSignup>> slots) {
        Set<UUID> ids = slots.stream().flatMap(s -> s.signups().stream()).map(FspCleaningSignup::getUserId)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return users.findAll(ids).stream().collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
    }
}
