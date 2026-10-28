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
import network.foodsharing.api.model.Holiday;
import network.foodsharing.api.model.HolidayScope;
import network.foodsharing.api.model.LinkedStoreRule;
import network.foodsharing.api.model.LinkedStoreWindowMode;
import network.foodsharing.api.model.PickupPolicyDecision;
import network.foodsharing.api.model.PolicyDecisionPreview;
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
import org.springframework.stereotype.Component;

/** Automation domain ↔ generated models. */
@Component
public class AutomationMapper {

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
        model.setDryRun(p.isDryRun());
        model.setGapRuleEnabled(p.isGapRuleEnabled());
        model.setMinimumGapDays(p.getMinimumGapDays());
        model.setCleaningRuleEnabled(p.isCleaningRuleEnabled());
        model.setExperienceRuleEnabled(p.isExperienceRuleEnabled());
        model.setPublicHolidayRuleEnabled(p.isPublicHolidayRuleEnabled());
        model.setLinkedStores(view.links().stream().map(this::toModel).toList());
        model.setExcludedDates(view.excludedDates());
        model.setVersion(p.getVersion());
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
                !Boolean.FALSE.equals(model.getDryRun()), Boolean.TRUE.equals(model.getGapRuleEnabled()),
                value(model.getMinimumGapDays()), Boolean.TRUE.equals(model.getCleaningRuleEnabled()),
                Boolean.TRUE.equals(model.getExperienceRuleEnabled()),
                Boolean.TRUE.equals(model.getPublicHolidayRuleEnabled()), links,
                model.getExcludedDates() == null ? List.of() : model.getExcludedDates(), model.getVersion());
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    public RequestPolicy toModel(network.foodsharing.automation.domain.RequestPolicy policy) {
        RequestPolicy model = new RequestPolicy();
        model.setEnabled(policy.isEnabled());
        model.setDryRun(policy.isDryRun());
        model.setDistanceRuleEnabled(policy.isDistanceRuleEnabled());
        model.setMaximumDistanceKm(policy.getMaximumDistanceKm());
        model.setVersion(policy.getVersion());
        return model;
    }

    public RequestPolicyService.RequestPolicyInput toInput(RequestPolicy model) {
        return new RequestPolicyService.RequestPolicyInput(Boolean.TRUE.equals(model.getEnabled()),
                !Boolean.FALSE.equals(model.getDryRun()), Boolean.TRUE.equals(model.getDistanceRuleEnabled()),
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
        model.setDryRun(result.dryRun());
        model.setMessages(result.messages());
        return model;
    }

    public PolicyDecisionPreview toModel(PickupPolicyEvaluator.Decision decision) {
        PolicyDecisionPreview model = new PolicyDecisionPreview();
        model.setDecision(decision.deferred() ? PickupPolicyDecision.DEFERRED
                : decision.skipped() ? PickupPolicyDecision.SKIPPED
                : decision.allowed() ? PickupPolicyDecision.WOULD_CONFIRM : PickupPolicyDecision.WOULD_DECLINE);
        model.setReasons(reasons(decision.reasons()));
        model.setUserMessage(decision.userMessage());
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
        model.setDryRun(overview.pickupPolicy() != null && overview.pickupPolicy().isDryRun());
        model.setRequestPolicyEnabled(overview.requestPolicy() != null && overview.requestPolicy().isEnabled());
        model.setAdvertCount(0);
        model.setSignoutAlertEnabled(false);
        return model;
    }

    public static StoreRef toStoreRef(Store store) {
        StoreRef ref = new StoreRef();
        ref.setId(store.getId());
        ref.setName(store.getName());
        ref.setRegionId(store.getRegionId());
        return ref;
    }
}
