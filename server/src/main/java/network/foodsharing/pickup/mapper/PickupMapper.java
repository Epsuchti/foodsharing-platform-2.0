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
    private final network.foodsharing.store.service.StoreService stores;

    public PickupMapper(UserMapper userMapper, UserService users, network.foodsharing.store.service.StoreService stores) {
        this.userMapper = userMapper;
        this.users = users;
        this.stores = stores;
    }

    /** The linked pickups of a sign-up request (decision 167 S3); none without a body. */
    public static List<network.foodsharing.pickup.service.PickupRuleGate.Planned> linkedPickups(
            network.foodsharing.api.model.SignupRequest request) {
        if (request == null || request.getLinkedPickups() == null) {
            return List.of();
        }
        return request.getLinkedPickups().stream().map(choice -> new network.foodsharing.pickup.service.PickupRuleGate
                .Planned(choice.getStoreId(), instant(choice.getPickupAt()))).toList();
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
        List<UUID> extraUsers = views.stream().flatMap(v -> v.extraSlots().stream())
                .map(network.foodsharing.pickup.domain.PickupExtraSlot::getUserId)
                .filter(id -> !byId.containsKey(id)).distinct().toList();
        Map<UUID, User> all = new java.util.HashMap<>(byId);
        if (!extraUsers.isEmpty()) {
            users.findAll(extraUsers).forEach(u -> all.put(u.getId(), u));
        }
        Map<UUID, network.foodsharing.store.domain.Store> blockStores = blockStores(views.stream()
                .map(PickupScheduleService.PickupView::eligibility).filter(java.util.Objects::nonNull).toList());
        return views.stream().map(v -> {
            Pickup model = toModel(v.slot(), v.available(), v.eligibility(), all, blockStores);
            model.setMayAddExtraSlot(v.mayAddExtraSlot());
            if (!v.linkedIntroPickups().isEmpty()) {
                for (PickupSlot slot : model.getOccupiedSlots()) {
                    var info = v.linkedIntroPickups().get(slot.getUser().getId());
                    if (info != null) {
                        slot.setLinkedIntroPickup(new network.foodsharing.api.model.LinkedIntroPickup()
                                .introPickupId(info.introPickupId()).bookedCount(info.bookedCount()));
                    }
                }
            }
            if (!v.linkMissing().isEmpty()) {
                for (PickupSlot slot : model.getOccupiedSlots()) {
                    Instant until = v.linkMissing().get(slot.getUser().getId());
                    if (until != null) {
                        slot.setLinkMissingUntil(time(until));
                    }
                }
            }
            model.setExtraSlots(v.extraSlots().stream().filter(e -> all.containsKey(e.getUserId())).map(e -> {
                network.foodsharing.api.model.PickupExtraSlot extra = new network.foodsharing.api.model.PickupExtraSlot();
                extra.setByUser(userMapper.toSummary(all.get(e.getUserId())));
                extra.setAddedAt(time(e.getCreatedAt()));
                return extra;
            }).toList());
            return model;
        }).toList();
    }

    public network.foodsharing.api.model.PickupSignupDetail toDetail(
            network.foodsharing.pickup.service.PickupSignupDetailService.SignupDetail d) {
        return new network.foodsharing.api.model.PickupSignupDetail().user(userMapper.toSummary(d.user()))
                .confirmed(d.confirmed()).signedUpAt(time(d.signedUpAt())).phone(d.phone())
                .pickupCount(d.pickupCount()).lastPickupAt(time(d.lastPickupAt()))
                .upcomingSlots(d.upcomingSlots().stream().map(PickupMapper::time).toList());
    }

    public Pickup toModel(PickupSchedule.Slot slot, boolean available) {
        return toModel(slot, available, null, usersOf(List.of(slot)), Map.of());
    }

    /** The stores named in rule blocks (one query for all pickups). */
    private Map<UUID, network.foodsharing.store.domain.Store> blockStores(
            Collection<PickupEligibilityRules.Result> results) {
        List<UUID> ids = results.stream().flatMap(r -> r.ruleBlocks().stream())
                .flatMap(b -> b.params().storeIds().stream()).distinct().toList();
        return ids.isEmpty() ? Map.of() : stores.storesById(ids).stream()
                .collect(Collectors.toMap(network.foodsharing.store.domain.Store::getId, Function.identity()));
    }

    private Map<UUID, User> usersOf(Collection<PickupSchedule.Slot> slots) {
        List<UUID> ids = slots.stream().flatMap(s -> s.signups().stream()).map(su -> su.getUserId()).distinct()
                .toList();
        return ids.isEmpty() ? Map.of() : users.findAll(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private Pickup toModel(PickupSchedule.Slot slot, boolean available, PickupEligibilityRules.Result eligibility,
                           Map<UUID, User> byId, Map<UUID, network.foodsharing.store.domain.Store> blockStores) {
        Pickup model = new Pickup();
        model.setStoreId(slot.storeId());
        model.setPickupAt(time(slot.pickupAt()));
        model.setSlotCount(slot.slotCount());
        model.setDescription(slot.description());
        model.setOccupiedSlots(slots(slot, byId));
        model.setIsAvailable(available);
        model.setIsRegular(slot.regular());
        model.setEligibility(eligibility == null ? null : toModel(eligibility, blockStores));
        return model;
    }

    public PickupEligibility toModel(PickupEligibilityRules.Result result) {
        return toModel(result, blockStores(List.of(result)));
    }

    private PickupEligibility toModel(PickupEligibilityRules.Result result,
                                      Map<UUID, network.foodsharing.store.domain.Store> blockStores) {
        PickupEligibility model = new PickupEligibility();
        if (!result.ruleBlocks().isEmpty()) {
            model.setRuleBlocks(result.ruleBlocks().stream().map(block -> toBlock(block, blockStores)).toList());
        }
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

    public static network.foodsharing.api.model.PolicyBlock toBlock(
            network.foodsharing.pickup.service.PickupRuleGate.RuleBlock block,
            Map<UUID, network.foodsharing.store.domain.Store> blockStores) {
        var p = block.params();
        var params = new network.foodsharing.api.model.PolicyBlockParams()
                .lastPickupAt(time(p.lastPickupAt())).nextPossibleAt(time(p.nextPossibleAt())).gapDays(p.gapDays())
                .windowMode(p.windowMode() == null ? null
                        : network.foodsharing.api.model.LinkedStoreWindowMode.fromValue(p.windowMode()))
                .before(p.before()).after(p.after()).from(time(p.from())).to(time(p.to()))
                .monthsAgo(p.monthsAgo()).monthsRequired(p.monthsRequired()).fspId(p.fspId());
        if (!p.storeIds().isEmpty()) {
            params.setStores(p.storeIds().stream().map(blockStores::get).filter(java.util.Objects::nonNull)
                    .map(store -> new network.foodsharing.api.model.StoreRef().id(store.getId()).name(store.getName())
                            .regionId(store.getRegionId())).toList());
        }
        return new network.foodsharing.api.model.PolicyBlock()
                .rule(network.foodsharing.api.model.PolicyRule.fromValue(block.rule())).params(params);
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

    public network.foodsharing.api.model.UserCleaningShift toCleaningShift(
            network.foodsharing.fsp.service.FspCleaningQueries.UserShift shift) {
        return new network.foodsharing.api.model.UserCleaningShift()
                .fsp(new network.foodsharing.api.model.FspRef().id(shift.fspId()).name(shift.fspName()))
                .shiftAt(network.foodsharing.common.mapper.TimeMapper.toOffset(shift.shiftAt())).confirmed(shift.confirmed())
                .description(shift.description());
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
            model.setMayAddExtraSlot(v.mayAddExtraSlot());
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
            // same filter as occupiedSlots, so the count never exceeds it
            model.setConfirmedCount((int) v.slot().signups().stream()
                    .filter(s -> byId.containsKey(s.getUserId()) && s.isConfirmed()).count());
            model.setIsConfirmed(v.confirmed());
            return model;
        }).toList();
    }

    public List<Pickup> toPastModels(List<PickupSchedule.Slot> slots) {
        Map<UUID, User> byId = usersOf(slots);
        return slots.stream().map(s -> toModel(s, false, null, byId, Map.of())).toList();
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
