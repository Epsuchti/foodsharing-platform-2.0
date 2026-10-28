package network.foodsharing.pickup.mapper;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.api.model.Pickup;
import network.foodsharing.api.model.PickupEligibility;
import network.foodsharing.api.model.PickupSlot;
import network.foodsharing.api.model.RegionPickupRuleState;
import network.foodsharing.api.model.RegularPickup;
import network.foodsharing.api.model.Weekday;
import network.foodsharing.pickup.service.PickupEligibilityRules;
import network.foodsharing.pickup.service.PickupSchedule;
import network.foodsharing.pickup.service.PickupScheduleService;
import network.foodsharing.pickup.service.RegularPickupService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.mapper.UserMapper;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Component;

/** Pickup domain ↔ generated API models. */
@Component
public class PickupMapper {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final UserMapper userMapper;
    private final UserService users;

    public PickupMapper(UserMapper userMapper, UserService users) {
        this.userMapper = userMapper;
        this.users = users;
    }

    public static OffsetDateTime time(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    public static Instant instant(OffsetDateTime time) {
        return time == null ? null : time.toInstant();
    }

    public RegularPickup toModel(network.foodsharing.pickup.domain.RegularPickup r) {
        RegularPickup model = new RegularPickup();
        model.setWeekday(Weekday.fromValue(r.getWeekday().name()));
        model.setStartTime(HH_MM.format(r.getStartTime()));
        model.setSlotCount(r.getSlotCount());
        model.setDescription(r.getDescription());
        return model;
    }

    public List<RegularPickup> toRegularModels(List<network.foodsharing.pickup.domain.RegularPickup> list) {
        return list.stream().map(this::toModel).toList();
    }

    public static RegularPickupService.Entry toEntry(RegularPickup model) {
        return model == null ? null : new RegularPickupService.Entry(
                model.getWeekday() == null ? null : DayOfWeek.valueOf(model.getWeekday().getValue()),
                model.getStartTime(), model.getSlotCount() == null ? -1 : model.getSlotCount(),
                model.getDescription());
    }

    public List<Pickup> toModels(List<PickupScheduleService.PickupView> views) {
        Map<UUID, User> byId = usersOf(views.stream().map(PickupScheduleService.PickupView::slot).toList());
        return views.stream().map(v -> toModel(v.slot(), v.available(), v.eligibility(), byId)).toList();
    }

    public Pickup toModel(PickupSchedule.Slot slot, boolean available) {
        return toModel(slot, available, null, usersOf(List.of(slot)));
    }

    private Map<UUID, User> usersOf(Collection<PickupSchedule.Slot> slots) {
        List<UUID> ids = slots.stream().flatMap(s -> s.signups().stream()).map(su -> su.getUserId()).distinct()
                .toList();
        return ids.isEmpty() ? Map.of() : users.findAll(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private Pickup toModel(PickupSchedule.Slot slot, boolean available, PickupEligibilityRules.Result eligibility,
                           Map<UUID, User> byId) {
        Pickup model = new Pickup();
        model.setStoreId(slot.storeId());
        model.setPickupAt(time(slot.pickupAt()));
        model.setSlotCount(slot.slotCount());
        model.setDescription(slot.description());
        model.setOccupiedSlots(slots(slot, byId));
        model.setIsAvailable(available);
        model.setIsRegular(slot.regular());
        model.setEligibility(eligibility == null ? null : toModel(eligibility));
        return model;
    }

    public PickupEligibility toModel(PickupEligibilityRules.Result result) {
        PickupEligibility model = new PickupEligibility();
        model.setEligible(result.eligible());
        model.setReasons(result.reasons().stream()
                .map(r -> network.foodsharing.api.model.PickupEligibilityReason.fromValue(r.name())).toList());
        PickupEligibilityRules.RuleState rule = result.rule();
        if (rule != null) {
            RegionPickupRuleState state = new RegionPickupRuleState();
            state.setActive(rule.active());
            if (rule.active()) {
                state.setTimespanDays(rule.timespanDays());
                state.setLimitNumber(rule.limitNumber());
                state.setLimitDayNumber(rule.limitDayNumber());
                state.setInactiveHours(rule.inactiveHours());
                state.setPickupsInTimespan(rule.pickupsInTimespan());
                state.setPickupsSameDay(rule.pickupsSameDay());
            }
            model.setRegionRule(state);
        }
        return model;
    }

    public network.foodsharing.api.model.StoreRef toStoreRef(network.foodsharing.store.domain.Store store,
                                                            java.time.ZoneId zone) {
        network.foodsharing.api.model.StoreRef ref = new network.foodsharing.api.model.StoreRef();
        ref.setId(store.getId());
        ref.setName(store.getName());
        ref.setRegionId(store.getRegionId());
        ref.setTimeZone(zone.getId());
        return ref;
    }

    public List<network.foodsharing.api.model.UserPickup> toUserPickups(
            List<network.foodsharing.pickup.service.PickupUserService.UserPickupView> views) {
        Map<UUID, User> byId = usersOf(views.stream().map(v -> v.slot()).toList());
        return views.stream().map(v -> {
            network.foodsharing.api.model.UserPickup model = new network.foodsharing.api.model.UserPickup();
            model.setStore(toStoreRef(v.store(), v.zone()));
            model.setPickupAt(time(v.slot().pickupAt()));
            model.setConfirmed(Boolean.TRUE.equals(v.confirmed()));
            model.setSlotCount(v.slot().slotCount());
            model.setDescription(v.slot().description());
            model.setOccupiedSlots(slots(v.slot(), byId));
            return model;
        }).toList();
    }

    public List<network.foodsharing.api.model.PickupOption> toOptions(
            List<network.foodsharing.pickup.service.PickupUserService.UserPickupView> views) {
        Map<UUID, User> byId = usersOf(views.stream().map(v -> v.slot()).toList());
        return views.stream().map(v -> {
            network.foodsharing.api.model.PickupOption model = new network.foodsharing.api.model.PickupOption();
            model.setStore(toStoreRef(v.store(), v.zone()));
            model.setPickupAt(time(v.slot().pickupAt()));
            model.setSlotCount(v.slot().slotCount());
            model.setDescription(v.slot().description());
            model.setOccupiedSlots(v.slot().signups().stream().filter(s -> byId.containsKey(s.getUserId()))
                    .map(s -> userMapper.toSummary(byId.get(s.getUserId()))).toList());
            model.setIsConfirmed(v.confirmed());
            return model;
        }).toList();
    }

    public List<Pickup> toPastModels(List<PickupSchedule.Slot> slots) {
        Map<UUID, User> byId = usersOf(slots);
        return slots.stream().map(s -> toModel(s, false, null, byId)).toList();
    }

    private List<PickupSlot> slots(PickupSchedule.Slot slot, Map<UUID, User> byId) {
        return slot.signups().stream().filter(s -> byId.containsKey(s.getUserId())).map(s -> {
            PickupSlot ps = new PickupSlot();
            ps.setUser(userMapper.toSummary(byId.get(s.getUserId())));
            ps.setConfirmed(s.isConfirmed());
            return ps;
        }).toList();
    }
}
