package network.foodsharing.automation.mapper;

import static network.foodsharing.common.mapper.TimeMapper.toOffset;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.api.model.AuditKind;
import network.foodsharing.api.model.AutomationAudit;
import network.foodsharing.api.model.AutomationRunResult;
import network.foodsharing.api.model.CleaningExemption;
import network.foodsharing.api.model.GapCandidate;
import network.foodsharing.api.model.GapCandidateLink;
import network.foodsharing.api.model.GapStore;
import network.foodsharing.api.model.Holiday;
import network.foodsharing.api.model.HolidayScope;
import network.foodsharing.api.model.LinkedStoreRule;
import network.foodsharing.api.model.LinkedStoreWindowMode;
import network.foodsharing.api.model.MetricKey;
import network.foodsharing.api.model.PolicyReason;
import network.foodsharing.api.model.PolicyRule;
import network.foodsharing.api.model.RegionAutomationStore;
import network.foodsharing.api.model.RequestPolicy;
import network.foodsharing.api.model.StoreRef;
import network.foodsharing.api.model.UserSummary;
import network.foodsharing.automation.domain.CleaningRuleExemption;
import network.foodsharing.automation.service.AutomationAuditService.AuditView;
import network.foodsharing.automation.service.AutomationOverviewService.StoreOverview;
import network.foodsharing.automation.service.HolidayCalendarService;
import network.foodsharing.automation.service.PickupPolicyEvaluator;
import network.foodsharing.automation.service.PickupPolicyRunner;
import network.foodsharing.automation.service.PickupPolicyService;
import network.foodsharing.automation.service.RequestPolicyService;
import network.foodsharing.store.domain.Store;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.mapper.UserMapper;
import network.foodsharing.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Automation domain ↔ generated models. */
@Component
public class AutomationMapper {

    private static final Logger log = LoggerFactory.getLogger(AutomationMapper.class);

    private final UserMapper userMapper;
    private final UserService users;

    public AutomationMapper(UserMapper userMapper, UserService users) {
        this.userMapper = userMapper;
        this.users = users;
    }

    public network.foodsharing.api.model.PickupPolicy toModel(PickupPolicyService.PolicyView view) {
        var p = view.policy();
        var model = new network.foodsharing.api.model.PickupPolicy();
        model.setEnabled(p.isEnabled());
        model.setGapRuleEnabled(p.isGapRuleEnabled());
        model.setMinimumGapDays(p.getMinimumGapDays());
        model.setCleaningRuleEnabled(p.isCleaningRuleEnabled());
        model.setExperienceRuleEnabled(p.isExperienceRuleEnabled());
        model.setPublicHolidayRuleEnabled(p.isPublicHolidayRuleEnabled());
        model.setLinkedStores(view.links().stream().map(this::toModel).toList());
        model.setExcludedDates(view.excludedDates());
        model.setGapStoreIds(view.gapStores().stream().map(g -> g.store().getId()).toList());
        model.setGroupMinimumGapDays(view.groupMinimumGapDays());
        model.setGapStores(view.gapStores().stream().map(g -> {
            GapStore gap = new GapStore();
            gap.setStore(toStoreRef(g.store()));
            gap.setAutomationActive(g.automationActive());
            gap.setMayEdit(g.mayEdit());
            return gap;
        }).toList());
        model.setVersion(p.getVersion());
        return model;
    }

    public GapCandidate toModel(PickupPolicyService.GapCandidateView view) {
        GapCandidate model = new GapCandidate();
        model.setStore(toStoreRef(view.store()));
        model.setAutomationActive(view.automationActive());
        model.setInOtherList(view.inOtherList());
        model.setMayEdit(view.mayEdit());
        model.setLinks(view.links().stream().map(link -> {
            GapCandidateLink rule = new GapCandidateLink();
            rule.setStoreIds(link.storeIds());
            rule.setWindowMode(LinkedStoreWindowMode.valueOf(link.windowMode().name()));
            rule.setWindowDaysBefore(link.windowDaysBefore());
            rule.setWindowDaysAfter(link.windowDaysAfter());
            return rule;
        }).toList());
        return model;
    }

    private LinkedStoreRule toModel(PickupPolicyService.LinkView view) {
        var link = view.link();
        LinkedStoreRule model = new LinkedStoreRule();
        model.setId(link.getId());
        model.setStoreIds(view.stores().stream().map(Store::getId).toList());
        model.setStores(view.stores().stream().map(AutomationMapper::toStoreRef).toList());
        model.setBidirectional(link.isBidirectional());
        model.setWindowMode(LinkedStoreWindowMode.valueOf(link.getWindowMode().name()));
        model.setWindowDaysBefore(link.getWindowDaysBefore());
        model.setWindowDaysAfter(link.getWindowDaysAfter());
        model.setGraceMinutes(link.getGraceMinutes());
        model.setMessage(link.getMessage());
        model.setMirrorOf(link.getMirrorOf());
        return model;
    }

    public PickupPolicyService.PolicyInput toInput(network.foodsharing.api.model.PickupPolicy model) {
        List<PickupPolicyService.LinkInput> links = model.getLinkedStores() == null ? List.of()
                : model.getLinkedStores().stream().map(rule -> new PickupPolicyService.LinkInput(
                        rule.getStoreIds() == null ? List.of() : rule.getStoreIds(),
                        Boolean.TRUE.equals(rule.getBidirectional()),
                        rule.getWindowMode() == null ? null
                                : network.foodsharing.automation.domain.LinkedStoreWindowMode.valueOf(
                                        rule.getWindowMode().name()),
                        value(rule.getWindowDaysBefore()), value(rule.getWindowDaysAfter()),
                        rule.getGraceMinutes() == null ? 20 : rule.getGraceMinutes(), rule.getMessage(),
                        rule.getMirrorOf())).toList();
        return new PickupPolicyService.PolicyInput(Boolean.TRUE.equals(model.getEnabled()),
                Boolean.TRUE.equals(model.getGapRuleEnabled()),
                value(model.getMinimumGapDays()), Boolean.TRUE.equals(model.getCleaningRuleEnabled()),
                Boolean.TRUE.equals(model.getExperienceRuleEnabled()),
                Boolean.TRUE.equals(model.getPublicHolidayRuleEnabled()), links,
                model.getExcludedDates() == null ? List.of() : model.getExcludedDates(), model.getVersion(),
                model.getGapStoreIds(), model.getGroupMinimumGapDays());
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    public RequestPolicy toModel(network.foodsharing.automation.domain.RequestPolicy policy) {
        RequestPolicy model = new RequestPolicy();
        model.setEnabled(policy.isEnabled());
        model.setDistanceRuleEnabled(policy.isDistanceRuleEnabled());
        model.setMaximumDistanceKm(policy.getMaximumDistanceKm());
        model.setVersion(policy.getVersion());
        return model;
    }

    public RequestPolicyService.RequestPolicyInput toInput(RequestPolicy model) {
        return new RequestPolicyService.RequestPolicyInput(Boolean.TRUE.equals(model.getEnabled()),
                Boolean.TRUE.equals(model.getDistanceRuleEnabled()),
                model.getMaximumDistanceKm() == null ? 0 : model.getMaximumDistanceKm(), model.getVersion());
    }

    public AutomationRunResult toModel(PickupPolicyRunner.RunResult result) {
        AutomationRunResult model = new AutomationRunResult();
        model.setEvaluated(result.evaluated());
        model.setConfirmed(result.confirmed());
        model.setDeclined(result.declined());
        model.setSkipped(result.skipped());
        model.setDeferred(result.deferred());
        model.setFailed(result.failed());
        model.setMessages(result.messages());
        return model;
    }

    private static List<PolicyReason> reasons(List<PickupPolicyEvaluator.Reason> reasons) {
        return reasons.stream().map(reason -> {
            PolicyReason model = new PolicyReason();
            model.setRule(PolicyRule.valueOf(reason.rule().name()));
            model.setText(reason.text());
            return model;
        }).toList();
    }

    public List<AutomationAudit> toAudits(List<AuditView> audits, Map<UUID, Store> stores) {
        Map<UUID, User> byId = users.findAll(audits.stream().map(AuditView::userId)
                .filter(java.util.Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return audits.stream().map(audit -> {
            AutomationAudit model = new AutomationAudit();
            model.setId(audit.id());
            model.setKind(AuditKind.valueOf(audit.kind().name()));
            model.setStoreId(audit.storeId());
            Store store = stores.get(audit.storeId());
            model.setStoreName(store == null ? "" : store.getName());
            model.setPickupAt(toOffset(audit.pickupAt()));
            User user = audit.userId() == null ? null : byId.get(audit.userId());
            model.setUser(user == null ? null : userMapper.toSummary(user));
            model.setStatus(audit.status());
            model.setDryRun(audit.dryRun());
            model.setReasons(reasons(audit.reasons()));
            model.setUserMessage(audit.userMessage());
            model.setError(audit.error());
            model.setCreatedAt(toOffset(audit.createdAt()));
            return model;
        }).toList();
    }

    public List<CleaningExemption> toExemptions(List<CleaningRuleExemption> exemptions) {
        Map<UUID, User> byId = users.findAll(exemptions.stream().map(CleaningRuleExemption::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return exemptions.stream().filter(e -> byId.containsKey(e.getUserId())).map(e -> {
            CleaningExemption model = new CleaningExemption();
            model.setId(e.getId());
            UserSummary summary = userMapper.toSummary(byId.get(e.getUserId()));
            model.setUser(summary);
            model.setReason(e.getReason());
            return model;
        }).toList();
    }

    public List<Holiday> toHolidays(List<HolidayCalendarService.Holiday> holidays) {
        return holidays.stream().map(h -> {
            Holiday model = new Holiday();
            model.setDate(h.date());
            model.setName(h.name());
            model.setScope(h.canton() == null ? HolidayScope.NATIONAL : HolidayScope.CANTON);
            return model;
        }).toList();
    }

    public RegionAutomationStore toModel(StoreOverview overview) {
        RegionAutomationStore model = new RegionAutomationStore();
        model.setStore(toStoreRef(overview.store()));
        model.setPickupPolicyEnabled(overview.pickupPolicy() != null && overview.pickupPolicy().isEnabled());
        model.setRequestPolicyEnabled(overview.requestPolicy() != null && overview.requestPolicy().isEnabled());
        model.setAdvertCount(overview.advertCount());
        model.setSignoutAlertEnabled(overview.signoutAlertEnabled());
        return model;
    }

    public static network.foodsharing.api.model.FspRef toFspRef(
            network.foodsharing.fsp.service.FspCleaningQueries.CleaningFsp fsp) {
        return new network.foodsharing.api.model.FspRef().id(fsp.id()).name(fsp.name());
    }

    /** The member summary of a store's rules (decision 165 S2). */
    public network.foodsharing.api.model.StoreRuleSummary toModel(PickupPolicyService.RuleSummary summary) {
        var model = new network.foodsharing.api.model.StoreRuleSummary()
                .enabled(summary.enabled()).gapDays(summary.gapDays()).experience(summary.experience())
                .cleaningMonths(summary.cleaningMonths()).manualDates(summary.manualDates())
                .holidays(summary.holidays())
                .links(summary.links().stream().map(link -> new network.foodsharing.api.model.StoreRuleLink()
                        .stores(link.stores().stream().map(AutomationMapper::toStoreRef).toList())
                        .windowMode(LinkedStoreWindowMode.fromValue(link.windowMode().name()))
                        .before(link.before()).after(link.after())).toList());
        if (summary.groupDays() != null) {
            model.setGroup(new network.foodsharing.api.model.StoreRuleGroup().days(summary.groupDays())
                    .stores(summary.groupStores().stream().map(AutomationMapper::toStoreRef).toList()));
        }
        return model;
    }

    /** The linked-store step of the sign-up dialog (decision 165 S3). */
    public network.foodsharing.api.model.LinkedOptions toLinkedOptions(
            network.foodsharing.automation.service.LinkedOptionsService.View view) {
        Map<UUID, Store> named = view.blockStores();
        return new network.foodsharing.api.model.LinkedOptions().rules(view.rules().stream().map(rule -> {
            boolean lastDays = rule.rule().windowMode()
                    == network.foodsharing.automation.domain.LinkedStoreWindowMode.LAST_DAYS;
            var model = new network.foodsharing.api.model.LinkedOptionRule()
                    .stores(rule.stores().stream().map(AutomationMapper::toStoreRef).toList())
                    .windowMode(LinkedStoreWindowMode.fromValue(rule.rule().windowMode().name()))
                    .before(lastDays ? rule.rule().windowDaysBefore() : 0)
                    .after(lastDays ? rule.rule().windowDaysAfter() : 0)
                    .from(network.foodsharing.pickup.mapper.PickupMapper.time(rule.from()))
                    .to(network.foodsharing.pickup.mapper.PickupMapper.time(rule.to()))
                    .notMember(rule.notMember().stream().map(AutomationMapper::toStoreRef).toList())
                    .options(rule.options().stream().map(o -> new network.foodsharing.api.model.LinkedOption()
                            .store(toStoreRef(o.store())).pickupAt(network.foodsharing.pickup.mapper.PickupMapper.time(o.pickupAt())).freeCount(o.freeCount())
                            .reasons(o.reasons().stream().map(r -> network.foodsharing.api.model.PickupEligibilityReason
                                    .fromValue(r.name())).toList())
                            .ruleBlocks(o.blocks().stream().map(b -> network.foodsharing.pickup.mapper.PickupMapper
                                    .toBlock(b, named)).toList())).toList());
            if (rule.satisfiedStore() != null) {
                model.setSatisfiedBy(new network.foodsharing.api.model.LinkedPickupRef()
                        .store(toStoreRef(rule.satisfiedStore())).pickupAt(network.foodsharing.pickup.mapper.PickupMapper.time(rule.satisfiedAt())));
            }
            return model;
        }).toList());
    }

    public static StoreRef toStoreRef(Store store) {
        StoreRef ref = new StoreRef();
        ref.setId(store.getId());
        ref.setName(store.getName());
        ref.setRegionId(store.getRegionId());
        return ref;
    }

    public network.foodsharing.api.model.OpenSlotAdvert toModel(
            network.foodsharing.automation.service.OpenSlotAdvertService.AdvertView view) {
        var model = new network.foodsharing.api.model.OpenSlotAdvert();
        model.setAdvertNumber(view.advertNumber());
        model.setEnabled(view.enabled());
        model.setTriggerHoursBefore(view.triggerHoursBefore());
        model.setChannels(view.channels().stream()
                .map(c -> network.foodsharing.api.model.AdvertChannel.valueOf(c.name())).toList());
        model.setTelegramChatId(view.telegramChatId());
        model.setStoreMessages(new java.util.ArrayList<>(view.storeMessages()));
        model.setPushMessages(new java.util.ArrayList<>(view.pushMessages()));
        model.setTelegramMessages(new java.util.ArrayList<>(view.telegramMessages()));
        model.setSendLateCancellationMessage(view.sendLateCancellationMessage());
        model.setLateCancellationMessage(view.lateCancellationMessage());
        model.setLateCancellationMaxHoursBefore(view.lateCancellationMaxHours());
        model.setResendLatestAdvertAfterLateCancellation(view.resendLatestAdvertAfterLateCancellation());
        return model;
    }

    public network.foodsharing.automation.service.OpenSlotAdvertService.AdvertInput toInput(
            network.foodsharing.api.model.OpenSlotAdvert model) {
        java.util.Set<network.foodsharing.automation.domain.AdvertChannel> channels =
                java.util.EnumSet.noneOf(network.foodsharing.automation.domain.AdvertChannel.class);
        if (model.getChannels() != null) {
            model.getChannels().forEach(c -> channels.add(
                    network.foodsharing.automation.domain.AdvertChannel.valueOf(c.name())));
        }
        return new network.foodsharing.automation.service.OpenSlotAdvertService.AdvertInput(
                Boolean.TRUE.equals(model.getEnabled()),
                model.getTriggerHoursBefore() == null ? 0 : model.getTriggerHoursBefore(), channels,
                model.getTelegramChatId(), model.getStoreMessages(), model.getPushMessages(),
                model.getTelegramMessages(), Boolean.TRUE.equals(model.getSendLateCancellationMessage()),
                model.getLateCancellationMessage(),
                model.getLateCancellationMaxHoursBefore() == null ? 0 : model.getLateCancellationMaxHoursBefore(),
                Boolean.TRUE.equals(model.getResendLatestAdvertAfterLateCancellation()));
    }

    public List<network.foodsharing.api.model.PlannedAdvert> toPlanned(
            List<network.foodsharing.automation.service.OpenSlotAdvertService.Planned> planned) {
        return planned.stream().map(p -> {
            var model = new network.foodsharing.api.model.PlannedAdvert();
            model.setPickupAt(toOffset(p.pickupAt()));
            model.setAdvertNumber(p.advertNumber());
            model.setTriggerAt(toOffset(p.triggerAt()));
            model.setChannels(p.channels().stream()
                    .map(c -> network.foodsharing.api.model.AdvertChannel.valueOf(c.name())).toList());
            model.setFreeSlots(p.freeSlots());
            return model;
        }).toList();
    }

    public network.foodsharing.api.model.SignoutAlertSetting toModel(
            network.foodsharing.automation.service.SignoutAlertService.SettingView view) {
        var model = new network.foodsharing.api.model.SignoutAlertSetting();
        model.setEnabled(view.enabled());
        model.setCutoffEnabled(view.cutoffEnabled());
        model.setMaximumHoursBeforePickup(view.maximumHoursBeforePickup());
        model.setChannels(view.channels().stream()
                .map(c -> network.foodsharing.api.model.SignoutAlertChannel.valueOf(c.name())).toList());
        model.setTelegramChatIds(view.telegramChatIds());
        model.setActiveSince(toOffset(view.activeSince()));
        return model;
    }

    public network.foodsharing.automation.service.SignoutAlertService.SettingInput toInput(
            network.foodsharing.api.model.SignoutAlertSetting model) {
        java.util.Set<network.foodsharing.automation.domain.SignoutAlertChannel> channels =
                java.util.EnumSet.noneOf(network.foodsharing.automation.domain.SignoutAlertChannel.class);
        if (model.getChannels() != null) {
            model.getChannels().forEach(c -> channels.add(
                    network.foodsharing.automation.domain.SignoutAlertChannel.valueOf(c.name())));
        }
        return new network.foodsharing.automation.service.SignoutAlertService.SettingInput(
                Boolean.TRUE.equals(model.getEnabled()), Boolean.TRUE.equals(model.getCutoffEnabled()),
                model.getMaximumHoursBeforePickup() == null ? 0 : model.getMaximumHoursBeforePickup(), channels,
                model.getTelegramChatIds());
    }

    public List<network.foodsharing.api.model.Cancellation> toCancellations(
            List<network.foodsharing.automation.domain.SignoutCancellation> rows) {
        Map<UUID, User> byId = users.findAll(rows.stream().map(r -> r.getUserId()).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return rows.stream().filter(r -> byId.containsKey(r.getUserId())).map(r -> {
            var model = new network.foodsharing.api.model.Cancellation();
            model.setId(r.getId());
            model.setPickupAt(toOffset(r.getPickupAt()));
            model.setUser(userMapper.toSummary(byId.get(r.getUserId())));
            model.setCancelledAt(toOffset(r.getCancelledAt()));
            model.setStatus(network.foodsharing.api.model.CancellationStatus.valueOf(r.getStatus().name()));
            model.setTotalSlots(r.getTotalSlots());
            model.setOccupiedSlots(r.getOccupiedSlots());
            model.setNotifiedAt(toOffset(r.getFinalNotifiedAt() != null ? r.getFinalNotifiedAt()
                    : r.getImmediateNotifiedAt()));
            return model;
        }).toList();
    }

    public List<network.foodsharing.api.model.PickupCountGroup> toGroups(
            List<network.foodsharing.automation.service.PickupCountGroupingService.Group> groups) {
        return groups.stream().map(g -> {
            var model = new network.foodsharing.api.model.PickupCountGroup();
            model.setPickupCount(g.pickupCount());
            model.setUsers(g.users().stream().map(u -> {
                var user = new network.foodsharing.api.model.PickupCountUser();
                user.setUser(userMapper.toSummary(u.user()));
                user.setStores(u.stores().stream().map(AutomationMapper::toStoreRef).toList());
                return user;
            }).toList());
            return model;
        }).toList();
    }

    /**
     * One series per known key. A key that is not in {@link MetricKey} (e.g. an old archived key) is left out with a
     * warning; it never fails the response.
     */
    public List<network.foodsharing.api.model.MetricSeries> toSeries(Map<String, Map<java.time.LocalDate, Long>> series) {
        List<network.foodsharing.api.model.MetricSeries> result = new java.util.ArrayList<>();
        series.forEach((key, points) -> {
            MetricKey metricKey;
            try {
                metricKey = MetricKey.fromValue(key);
            } catch (IllegalArgumentException e) {
                log.warn("Unknown metric key {} left out of the metrics response", key);
                return;
            }
            var model = new network.foodsharing.api.model.MetricSeries();
            model.setKey(metricKey);
            model.setPoints(points.entrySet().stream().map(p -> {
                var point = new network.foodsharing.api.model.MetricPoint();
                point.setDay(p.getKey());
                point.setValue(p.getValue());
                return point;
            }).toList());
            result.add(model);
        });
        return result;
    }
}
