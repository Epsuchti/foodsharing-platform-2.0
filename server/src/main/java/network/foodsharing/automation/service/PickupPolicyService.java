package network.foodsharing.automation.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.automation.domain.AutomationAction;
import network.foodsharing.automation.domain.LinkedStoreWindowMode;
import network.foodsharing.automation.domain.PickupGapGroup;
import network.foodsharing.automation.domain.PickupPolicy;
import network.foodsharing.automation.domain.PickupPolicyExcludedDate;
import network.foodsharing.automation.domain.PickupPolicyLinkedStore;
import network.foodsharing.automation.domain.PickupPolicyLinkedStoreMember;
import network.foodsharing.automation.repository.PickupGapGroupRepository;
import network.foodsharing.automation.repository.PickupPolicyExcludedDateRepository;
import network.foodsharing.automation.repository.PickupPolicyLinkedStoreMemberRepository;
import network.foodsharing.automation.repository.PickupPolicyLinkedStoreRepository;
import network.foodsharing.automation.repository.PickupPolicyRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.fsp.service.FspCleaningQueries;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StorePermissionService;
import network.foodsharing.store.service.StoreService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pickup policy CRUD (AU1). Validation ported from the reference: at most {@value #MAXIMUM_LINKS} link rules per store
 * (mirrors of bidirectional rules of other stores count), at most {@value #MAXIMUM_STORES_PER_LINK} stores per rule, no
 * self link, every linked store must be one the actor may automate, LAST_DAYS window 0..30 days with at least one day,
 * grace ≥ 20 minutes, message placeholders {@code {{name|storeName|linkedStoreName|pickupDate|requirement|graceMinutes}}}.
 * A bidirectional rule gets a mirror row on every linked store's policy (created disabled + dry run when missing).
 */
@Service
public class PickupPolicyService {

    static final int MAXIMUM_LINKS = 2;
    static final int MAXIMUM_STORES_PER_LINK = 5;
    static final int MAXIMUM_WINDOW_DAYS = 30;
    static final int MAXIMUM_GAP_DAYS = 60;
    /** Stores per gap list, the saving store included (decision 151). */
    static final int MAXIMUM_GAP_STORES = 10;
    static final Set<String> PLACEHOLDERS = Set.of("name", "storeName", "linkedStoreName", "pickupDate", "requirement",
            "graceMinutes");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([^}\\s]+)\\s*}}");

    public record LinkInput(List<UUID> storeIds, boolean bidirectional, LinkedStoreWindowMode windowMode,
                            int windowDaysBefore, int windowDaysAfter, int graceMinutes, String message,
                            UUID mirrorOf) {
    }

    /** {@code gapStoreIds}: the other stores of the shared gap list (decision 151); empty or missing = alone. */
    public record PolicyInput(boolean enabled, boolean dryRun, boolean gapRuleEnabled, int minimumGapDays,
                              boolean cleaningRuleEnabled, boolean experienceRuleEnabled,
                              boolean publicHolidayRuleEnabled, List<LinkInput> links, List<LocalDate> excludedDates,
                              Long version, List<UUID> gapStoreIds) {

        public PolicyInput {
            gapStoreIds = gapStoreIds == null ? List.of() : List.copyOf(gapStoreIds);
        }

        /** Without a gap list. */
        public PolicyInput(boolean enabled, boolean dryRun, boolean gapRuleEnabled, int minimumGapDays,
                           boolean cleaningRuleEnabled, boolean experienceRuleEnabled,
                           boolean publicHolidayRuleEnabled, List<LinkInput> links, List<LocalDate> excludedDates,
                           Long version) {
            this(enabled, dryRun, gapRuleEnabled, minimumGapDays, cleaningRuleEnabled, experienceRuleEnabled,
                    publicHolidayRuleEnabled, links, excludedDates, version, List.of());
        }
    }

    public record LinkView(PickupPolicyLinkedStore link, List<Store> stores) {
    }

    /** Another store of the gap list: whether it checks sign-ups itself and whether the actor may change it. */
    public record GapStoreView(Store store, boolean automationActive, boolean mayEdit) {
    }

    /** A store that can be put on the gap list, with its link rules (own and mirrored) for the D7 check. */
    public record GapCandidateView(Store store, boolean automationActive, boolean inOtherList,
                                   List<GapLinkConflicts.Link> links) {
    }

    public record PolicyView(PickupPolicy policy, List<LinkView> links, List<LocalDate> excludedDates,
                             List<GapStoreView> gapStores) {
    }

    private final PickupPolicyRepository policies;
    private final PickupPolicyLinkedStoreRepository links;
    private final PickupPolicyLinkedStoreMemberRepository members;
    private final PickupPolicyExcludedDateRepository excludedDates;
    private final StoreService stores;
    private final StorePermissionService permissions;
    private final CurrentActorService currentActor;
    private final HistoryService history;
    private final FspCleaningQueries cleaning;
    private final PickupGapGroupRepository gapGroups;
    private final RegionService regions;

    public PickupPolicyService(PickupPolicyRepository policies, PickupPolicyLinkedStoreRepository links,
                               PickupPolicyLinkedStoreMemberRepository members,
                               PickupPolicyExcludedDateRepository excludedDates, StoreService stores,
                               StorePermissionService permissions, CurrentActorService currentActor,
                               HistoryService history, FspCleaningQueries cleaning,
                               PickupGapGroupRepository gapGroups, RegionService regions) {
        this.gapGroups = gapGroups;
        this.regions = regions;
        this.cleaning = cleaning;
        this.policies = policies;
        this.links = links;
        this.members = members;
        this.excludedDates = excludedDates;
        this.stores = stores;
        this.permissions = permissions;
        this.currentActor = currentActor;
        this.history = history;
    }

    /** The Fairteiler of the store's region that count for the cleaning rule (FS2), by name. */
    @Transactional(readOnly = true)
    public List<FspCleaningQueries.CleaningFsp> cleaningFoodSharePointsOf(Store store) {
        return cleaning.cleaningFsps(store.getRegionId());
    }

    /** The store, after checking mayUseAutomations. @throws ApiException 404, 403 */
    @Transactional(readOnly = true)
    public Store requireAutomationStore(UUID storeId) {
        Store store = stores.require(storeId);
        if (!permissions.mayUseAutomations(currentActor.requirePrincipal(), store)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        return store;
    }

    @Transactional(readOnly = true)
    public Optional<PolicyView> find(UUID storeId) {
        return policies.findByStoreId(storeId).map(this::view);
    }

    /** @throws ApiException 404 POLICY_NOT_FOUND */
    @Transactional(readOnly = true)
    public PickupPolicy require(UUID storeId) {
        return policies.findByStoreId(storeId).orElseThrow(() -> ApiException.notFound(ApiErrorCode.POLICY_NOT_FOUND));
    }

    /**
     * Creates or replaces the policy of the store (mirror rules of other stores are kept; mirrors in the input are
     * ignored). @throws ApiException 400 (codes above), 403, 404, 409 CONCURRENT_MODIFICATION
     */
    @Transactional
    public PolicyView put(UUID storeId, PolicyInput input) {
        Store store = requireAutomationStore(storeId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        Optional<PickupPolicy> existing = policies.findByStoreId(storeId);
        if (existing.isPresent() && input.version() != null && input.version() != existing.get().getVersion()) {
            throw ApiException.conflict(ApiErrorCode.CONCURRENT_MODIFICATION);
        }
        if (input.minimumGapDays() < 0 || input.minimumGapDays() > MAXIMUM_GAP_DAYS) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "minimumGapDays");
        }
        List<LinkInput> own = input.links() == null ? List.of()
                : input.links().stream().filter(link -> link.mirrorOf() == null).toList();
        List<PickupPolicyLinkedStore> mirrorsHere = existing.map(p -> links.findByPolicyIdOrderByCreatedAtAscIdAsc(
                p.getId()).stream().filter(l -> l.getMirrorOf() != null).toList()).orElse(List.of());
        validateLinks(actor, storeId, own, mirrorsHere.size());
        Set<LocalDate> dates = new LinkedHashSet<>(input.excludedDates() == null ? List.of() : input.excludedDates());
        if (dates.size() > 366) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "excludedDates");
        }
        List<UUID> wantedGap = gapList(storeId, input);
        boolean grouped = input.gapRuleEnabled() && !wantedGap.isEmpty();
        PickupGapGroup group = existing.map(PickupPolicy::getGapGroupId).flatMap(gapGroups::lockById).orElse(null);
        Map<UUID, PickupPolicy> groupMembers = group == null ? Map.of() : policies.findByGapGroupId(group.getId())
                .stream().filter(p -> !p.getStoreId().equals(storeId))
                .collect(Collectors.toMap(PickupPolicy::getStoreId, p -> p));
        Set<UUID> liveOthers = stores.liveStores(groupMembers.keySet()).stream().map(Store::getId)
                .collect(Collectors.toSet());
        if (grouped) {
            validateGapList(actor, store, group, liveOthers, wantedGap, input.minimumGapDays());
            Set<UUID> list = new LinkedHashSet<>(wantedGap);
            list.add(storeId);
            GapLinkConflicts.firstBlocking(list, input.minimumGapDays(), linksOf(storeId, existing.orElse(null),
                    own, wantedGap)).ifPresent(conflict -> {
                        throw ApiException.badRequest(ApiErrorCode.GAP_LINK_CONFLICT)
                                .withArg("storeId", conflict.storeId().toString())
                                .withArg("otherStoreId", conflict.otherStoreId().toString());
                    });
        }
        PickupPolicy policy = existing.orElseGet(PickupPolicy::new);
        boolean created = existing.isEmpty();
        boolean gapChanged = grouped != (group != null) || (grouped && (group.getMinimumGapDays()
                != input.minimumGapDays() || !liveOthers.equals(new HashSet<>(wantedGap))));
        if (grouped && group == null) {
            group = new PickupGapGroup();
        }
        if (grouped) {
            group.setMinimumGapDays(input.minimumGapDays());
            group = gapGroups.saveAndFlush(group);
        }
        PickupGapGroup leftGroup = !grouped ? group : null;
        policy.setStoreId(storeId);
        policy.setRegionId(store.getRegionId());
        policy.setEnabled(input.enabled());
        policy.setDryRun(input.dryRun());
        policy.setGapRuleEnabled(input.gapRuleEnabled());
        policy.setMinimumGapDays(input.minimumGapDays());
        policy.setCleaningRuleEnabled(input.cleaningRuleEnabled());
        policy.setExperienceRuleEnabled(input.experienceRuleEnabled());
        policy.setPublicHolidayRuleEnabled(input.publicHolidayRuleEnabled());
        policy.setGapGroupId(grouped ? group.getId() : null);
        policy = policies.saveAndFlush(policy);
        replaceOwnLinks(policy, own);
        replaceExcludedDates(policy, dates, store);
        if (grouped) {
            writeGapList(policy, group, groupMembers, liveOthers, wantedGap, gapChanged);
        } else if (leftGroup != null) {
            leaveGapList(policy, leftGroup);
        }
        history.record(history.event(created ? AutomationAction.PICKUP_POLICY_CREATED
                : AutomationAction.PICKUP_POLICY_UPDATED, policy.getId()).region(store.getRegionId())
                .metadata("storeId", storeId.toString()).build());
        return view(policy);
    }

    /** The other stores of the requested gap list, without duplicates. @throws ApiException 400 */
    private static List<UUID> gapList(UUID storeId, PolicyInput input) {
        List<UUID> wanted = List.copyOf(new LinkedHashSet<>(input.gapStoreIds()));
        if (wanted.contains(storeId)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "gapStoreIds");
        }
        if (wanted.size() + 1 > MAXIMUM_GAP_STORES) {
            throw ApiException.badRequest(ApiErrorCode.GAP_LIST_TOO_LARGE);
        }
        return wanted;
    }

    /**
     * D1/D6: gap ≥ 1, every store live and a candidate (region subtree or already listed), in no other list, and the
     * actor may use automations on every store whose membership or gap changes. @throws ApiException 400, 403, 404
     */
    private void validateGapList(SessionPrincipal actor, Store store, PickupGapGroup group, Set<UUID> liveOthers,
                                 List<UUID> wanted, int minimumGapDays) {
        if (minimumGapDays < 1) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "minimumGapDays");
        }
        Set<UUID> candidates = stores.liveStoresInRegions(regions.subtreeIds(store.getRegionId())).stream()
                .map(Store::getId).collect(Collectors.toCollection(HashSet::new));
        candidates.addAll(liveOthers);
        Map<UUID, PickupPolicy> byStore = policies.findByStoreIdIn(wanted).stream()
                .collect(Collectors.toMap(PickupPolicy::getStoreId, p -> p));
        for (UUID id : wanted) {
            Store listed = stores.require(id);
            if (!candidates.contains(id)) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "gapStoreIds")
                        .withArg("storeId", id.toString());
            }
            PickupPolicy policy = byStore.get(id);
            if (policy != null && policy.getGapGroupId() != null
                    && (group == null || !policy.getGapGroupId().equals(group.getId()))) {
                throw ApiException.badRequest(ApiErrorCode.GAP_LIST_STORE_TAKEN).withArg("storeId", id.toString());
            }
            boolean changes = !liveOthers.contains(id) || policy == null || !policy.isGapRuleEnabled()
                    || policy.getMinimumGapDays() != minimumGapDays;
            if (changes && !permissions.mayUseAutomations(actor, listed)) {
                throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED).withArg("storeId", id.toString());
            }
        }
        for (UUID removed : liveOthers) {
            if (!wanted.contains(removed) && !permissions.mayUseAutomations(actor, stores.require(removed))) {
                throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED).withArg("storeId", removed.toString());
            }
        }
    }

    /**
     * The link rules that matter for the D7 check: this store's rules as they will be saved (with the mirrors they
     * create) and the mirrors it holds, plus every rule of the other listed stores except mirrors of this store's old
     * rules (they are replaced).
     */
    private List<GapLinkConflicts.Link> linksOf(UUID storeId, PickupPolicy existing, List<LinkInput> own,
                                                List<UUID> others) {
        List<GapLinkConflicts.Link> result = new ArrayList<>();
        for (LinkInput input : own) {
            LinkedStoreWindowMode mode = input.windowMode() == null ? LinkedStoreWindowMode.SAME_DAY
                    : input.windowMode();
            result.add(new GapLinkConflicts.Link(storeId, input.storeIds(), mode, input.windowDaysBefore(),
                    input.windowDaysAfter()));
            if (input.bidirectional()) {
                for (UUID target : input.storeIds()) {
                    result.add(new GapLinkConflicts.Link(target, List.of(storeId), mode, input.windowDaysAfter(),
                            input.windowDaysBefore()));
                }
            }
        }
        Set<UUID> ownRuleIds = existing == null ? Set.of() : links.findByPolicyIdOrderByCreatedAtAscIdAsc(
                existing.getId()).stream().filter(l -> l.getMirrorOf() == null).map(PickupPolicyLinkedStore::getId)
                .collect(Collectors.toSet());
        if (existing != null) {
            result.addAll(linkRules(storeId, links.findByPolicyIdOrderByCreatedAtAscIdAsc(existing.getId()).stream()
                    .filter(l -> l.getMirrorOf() != null).toList()));
        }
        for (PickupPolicy other : policies.findByStoreIdIn(others)) {
            result.addAll(linkRules(other.getStoreId(), links.findByPolicyIdOrderByCreatedAtAscIdAsc(other.getId())
                    .stream().filter(l -> l.getMirrorOf() == null || !ownRuleIds.contains(l.getMirrorOf())).toList()));
        }
        return result;
    }

    private List<GapLinkConflicts.Link> linkRules(UUID ownerStoreId, List<PickupPolicyLinkedStore> rules) {
        Map<UUID, List<UUID>> storeIds = members.findByLinkIdIn(rules.stream().map(PickupPolicyLinkedStore::getId)
                .toList()).stream().collect(Collectors.groupingBy(PickupPolicyLinkedStoreMember::getLinkId,
                Collectors.mapping(PickupPolicyLinkedStoreMember::getStoreId, Collectors.toList())));
        return rules.stream().map(rule -> new GapLinkConflicts.Link(ownerStoreId,
                storeIds.getOrDefault(rule.getId(), List.of()), rule.getWindowMode(), rule.getWindowDaysBefore(),
                rule.getWindowDaysAfter())).toList();
    }

    /**
     * Writes the list: every listed store gets the group, the gap and "rule on" (a store without a policy gets one,
     * off and in dry run); live stores taken off the list leave it and keep their gap. History on every affected
     * policy when the list or the gap changed.
     */
    private void writeGapList(PickupPolicy policy, PickupGapGroup group, Map<UUID, PickupPolicy> groupMembers,
                              Set<UUID> liveOthers, List<UUID> wanted, boolean changed) {
        List<PickupPolicy> affected = new ArrayList<>();
        affected.add(policy);
        for (UUID id : wanted) {
            PickupPolicy member = groupMembers.containsKey(id) ? groupMembers.get(id)
                    : policies.findByStoreId(id).orElseGet(() -> newPolicy(id));
            member.setRegionId(stores.require(id).getRegionId());
            member.setGapGroupId(group.getId());
            member.setGapRuleEnabled(true);
            member.setMinimumGapDays(group.getMinimumGapDays());
            affected.add(policies.saveAndFlush(member));
        }
        for (UUID id : liveOthers) {
            if (!wanted.contains(id)) {
                PickupPolicy member = groupMembers.get(id);
                member.setRegionId(stores.require(id).getRegionId());
                member.setGapGroupId(null);
                policies.saveAndFlush(member);
                recordGapList(member, List.of(), member.getMinimumGapDays());
            }
        }
        if (changed) {
            List<UUID> storeIds = affected.stream().map(PickupPolicy::getStoreId).toList();
            affected.forEach(member -> recordGapList(member, storeIds, group.getMinimumGapDays()));
        }
    }

    /** This store leaves its list; a store left alone drops out too and keeps its gap (D6). */
    private void leaveGapList(PickupPolicy policy, PickupGapGroup group) {
        recordGapList(policy, List.of(), policy.getMinimumGapDays());
        List<PickupPolicy> remaining = policies.findByGapGroupId(group.getId()).stream()
                .filter(p -> !p.getId().equals(policy.getId())).toList();
        if (remaining.size() > 1) {
            List<UUID> storeIds = remaining.stream().map(PickupPolicy::getStoreId).toList();
            remaining.forEach(member -> recordGapList(member, storeIds, group.getMinimumGapDays()));
            return;
        }
        for (PickupPolicy member : remaining) {
            member.setRegionId(stores.storesById(List.of(member.getStoreId())).stream().findFirst()
                    .map(Store::getRegionId).orElse(null));
            member.setGapGroupId(null);
            policies.saveAndFlush(member);
            recordGapList(member, List.of(), member.getMinimumGapDays());
        }
        gapGroups.delete(group);
        gapGroups.flush();
    }

    private void recordGapList(PickupPolicy policy, List<UUID> storeIds, int minimumGapDays) {
        history.record(history.event(AutomationAction.GAP_LIST_CHANGED, policy.getId()).region(policy.getRegionId())
                .metadata("storeId", policy.getStoreId().toString())
                .metadata("storeIds", String.join(",", storeIds.stream().map(UUID::toString).toList()))
                .metadata("minimumGapDays", String.valueOf(minimumGapDays)).build());
    }

    private PickupPolicy newPolicy(UUID storeId) {
        PickupPolicy created = new PickupPolicy();
        created.setStoreId(storeId);
        created.setEnabled(false);
        created.setDryRun(true);
        return created;
    }

    /** Stores that may join the gap list of the store (D6). @throws ApiException 403, 404 */
    @Transactional(readOnly = true)
    public List<GapCandidateView> gapCandidates(UUID storeId) {
        Store store = requireAutomationStore(storeId);
        UUID groupId = policies.findByStoreId(storeId).map(PickupPolicy::getGapGroupId).orElse(null);
        Set<UUID> ids = stores.liveStoresInRegions(regions.subtreeIds(store.getRegionId())).stream()
                .map(Store::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        if (groupId != null) {
            policies.findByGapGroupId(groupId).forEach(p -> ids.add(p.getStoreId()));
        }
        ids.remove(storeId);
        List<Store> candidates = stores.liveStores(ids);
        Map<UUID, PickupPolicy> byStore = policies.findByStoreIdIn(ids).stream()
                .collect(Collectors.toMap(PickupPolicy::getStoreId, p -> p));
        List<GapCandidateView> result = new ArrayList<>();
        for (Store candidate : candidates) {
            PickupPolicy policy = byStore.get(candidate.getId());
            boolean inOtherList = policy != null && policy.getGapGroupId() != null
                    && !policy.getGapGroupId().equals(groupId);
            List<GapLinkConflicts.Link> rules = policy == null ? List.of()
                    : linkRules(candidate.getId(), links.findByPolicyIdOrderByCreatedAtAscIdAsc(policy.getId()));
            result.add(new GapCandidateView(candidate, policy != null && policy.isEnabled(), inOtherList, rules));
        }
        return result;
    }

    /** Deletes the policy with its rules, their mirrors and excluded dates. @throws ApiException 403, 404 */
    @Transactional
    public void delete(UUID storeId) {
        Store store = requireAutomationStore(storeId);
        PickupPolicy policy = require(storeId);
        if (policy.getGapGroupId() != null) {
            // leaving is always allowed (D6)
            PickupGapGroup group = gapGroups.lockById(policy.getGapGroupId()).orElse(null);
            policy.setRegionId(store.getRegionId());
            policy.setGapGroupId(null);
            policy = policies.saveAndFlush(policy);
            if (group != null) {
                leaveGapList(policy, group);
            }
        }
        List<PickupPolicyLinkedStore> all = links.findByPolicyIdOrderByCreatedAtAscIdAsc(policy.getId());
        deleteLinks(all.stream().filter(l -> l.getMirrorOf() == null).toList());
        deleteLinkRows(all.stream().filter(l -> l.getMirrorOf() != null).toList());
        excludedDates.deleteAll(excludedDates.findByPolicyIdOrderByExcludedDate(policy.getId()));
        policies.delete(policy);
        history.record(history.event(AutomationAction.PICKUP_POLICY_DELETED, policy.getId())
                .region(store.getRegionId()).metadata("storeId", storeId.toString()).build());
    }

    void validateLinks(SessionPrincipal actor, UUID storeId, List<LinkInput> own, int mirrorsHere) {
        if (own.size() + mirrorsHere > MAXIMUM_LINKS) {
            throw ApiException.badRequest(ApiErrorCode.TOO_MANY_LINKED_STORES);
        }
        Set<UUID> all = new HashSet<>();
        for (LinkInput link : own) {
            List<UUID> ids = link.storeIds() == null ? List.of() : link.storeIds();
            if (ids.isEmpty()) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "storeIds");
            }
            if (ids.size() > MAXIMUM_STORES_PER_LINK) {
                throw ApiException.badRequest(ApiErrorCode.TOO_MANY_STORES_PER_LINK);
            }
            for (UUID id : ids) {
                if (id.equals(storeId)) {
                    throw ApiException.badRequest(ApiErrorCode.LINKED_STORE_SELF);
                }
                Store linked = stores.require(id);
                if (!permissions.mayUseAutomations(actor, linked)) {
                    throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED).withArg("storeId", id.toString());
                }
                if (!all.add(id)) {
                    throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "storeIds");
                }
            }
            LinkedStoreWindowMode mode = link.windowMode() == null ? LinkedStoreWindowMode.SAME_DAY
                    : link.windowMode();
            if (mode == LinkedStoreWindowMode.LAST_DAYS && (link.windowDaysBefore() < 0
                    || link.windowDaysBefore() > MAXIMUM_WINDOW_DAYS || link.windowDaysAfter() < 0
                    || link.windowDaysAfter() > MAXIMUM_WINDOW_DAYS
                    || link.windowDaysBefore() + link.windowDaysAfter() < 1)) {
                throw ApiException.badRequest(ApiErrorCode.LINKED_STORE_WINDOW_INVALID);
            }
            if (link.graceMinutes() < PickupPolicyEvaluator.MINIMUM_GRACE_MINUTES) {
                throw ApiException.badRequest(ApiErrorCode.GRACE_MINUTES_TOO_LOW);
            }
            validateMessage(link.message());
        }
        // A pair of stores has one rule: if a linked store already links here, that rule must become bidirectional.
        for (PickupPolicyLinkedStoreMember incoming : members.findByStoreId(storeId)) {
            links.findById(incoming.getLinkId()).filter(l -> l.getMirrorOf() == null)
                    .flatMap(l -> policies.findById(l.getPolicyId()))
                    .filter(source -> !source.getStoreId().equals(storeId) && all.contains(source.getStoreId()))
                    .ifPresent(source -> {
                        throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED)
                                .withArg("storeId", source.getStoreId().toString());
                    });
        }
        // Mirrors count towards the linked store's limit.
        for (LinkInput link : own) {
            if (!link.bidirectional()) {
                continue;
            }
            for (UUID target : link.storeIds()) {
                long used = policies.findByStoreId(target).map(p -> links.findByPolicyIdOrderByCreatedAtAscIdAsc(
                        p.getId()).stream().filter(l -> !isMirrorFrom(l, storeId)).count()).orElse(0L);
                if (used + 1 > MAXIMUM_LINKS) {
                    throw ApiException.badRequest(ApiErrorCode.TOO_MANY_LINKED_STORES)
                            .withArg("storeId", target.toString());
                }
            }
        }
    }

    private boolean isMirrorFrom(PickupPolicyLinkedStore link, UUID sourceStoreId) {
        return link.getMirrorOf() != null && links.findById(link.getMirrorOf())
                .flatMap(source -> policies.findById(source.getPolicyId()))
                .map(p -> p.getStoreId().equals(sourceStoreId)).orElse(false);
    }

    static void validateMessage(String message) {
        if (message == null) {
            return;
        }
        Matcher matcher = PLACEHOLDER.matcher(message);
        while (matcher.find()) {
            if (!PLACEHOLDERS.contains(matcher.group(1))) {
                throw ApiException.badRequest(ApiErrorCode.INVALID_TEMPLATE_VARIABLE)
                        .withArg("variable", matcher.group(1));
            }
        }
    }

    private void replaceOwnLinks(PickupPolicy policy, List<LinkInput> own) {
        deleteLinks(links.findByPolicyIdOrderByCreatedAtAscIdAsc(policy.getId()).stream()
                .filter(l -> l.getMirrorOf() == null).toList());
        for (LinkInput input : own) {
            boolean lastDays = input.windowMode() == LinkedStoreWindowMode.LAST_DAYS;
            PickupPolicyLinkedStore link = new PickupPolicyLinkedStore();
            link.setPolicyId(policy.getId());
            link.setBidirectional(input.bidirectional());
            link.setWindowMode(lastDays ? LinkedStoreWindowMode.LAST_DAYS : LinkedStoreWindowMode.SAME_DAY);
            link.setWindowDaysBefore(lastDays ? input.windowDaysBefore() : 0);
            link.setWindowDaysAfter(lastDays ? input.windowDaysAfter() : 0);
            link.setGraceMinutes(input.graceMinutes());
            link.setMessage(input.message() == null || input.message().isBlank() ? null : input.message().strip());
            link = links.saveAndFlush(link);
            addMembers(link, input.storeIds());
            if (input.bidirectional()) {
                for (UUID target : input.storeIds()) {
                    createMirror(link, policy.getStoreId(), target);
                }
            }
        }
    }

    private void createMirror(PickupPolicyLinkedStore source, UUID sourceStoreId, UUID targetStoreId) {
        PickupPolicy target = policies.findByStoreId(targetStoreId).orElseGet(() -> {
            PickupPolicy created = new PickupPolicy();
            created.setStoreId(targetStoreId);
            created.setEnabled(false);
            created.setDryRun(true);
            return policies.saveAndFlush(created);
        });
        PickupPolicyLinkedStore mirror = new PickupPolicyLinkedStore();
        mirror.setPolicyId(target.getId());
        mirror.setBidirectional(true);
        mirror.setWindowMode(source.getWindowMode());
        mirror.setWindowDaysBefore(source.getWindowDaysAfter());
        mirror.setWindowDaysAfter(source.getWindowDaysBefore());
        mirror.setGraceMinutes(source.getGraceMinutes());
        mirror.setMessage(source.getMessage());
        mirror.setMirrorOf(source.getId());
        mirror = links.saveAndFlush(mirror);
        addMembers(mirror, List.of(sourceStoreId));
    }

    private void addMembers(PickupPolicyLinkedStore link, Collection<UUID> storeIds) {
        for (UUID storeId : storeIds) {
            PickupPolicyLinkedStoreMember member = new PickupPolicyLinkedStoreMember();
            member.setLinkId(link.getId());
            member.setStoreId(storeId);
            members.save(member);
        }
    }

    /** Deletes own rules together with their mirrors on other stores. */
    private void deleteLinks(List<PickupPolicyLinkedStore> own) {
        if (own.isEmpty()) {
            return;
        }
        deleteLinkRows(links.findByMirrorOfIn(own.stream().map(PickupPolicyLinkedStore::getId).toList()));
        deleteLinkRows(own);
    }

    private void deleteLinkRows(List<PickupPolicyLinkedStore> rows) {
        if (rows.isEmpty()) {
            return;
        }
        members.deleteAll(members.findByLinkIdIn(rows.stream().map(PickupPolicyLinkedStore::getId).toList()));
        members.flush();
        links.deleteAll(rows);
        links.flush();
    }

    private void replaceExcludedDates(PickupPolicy policy, Set<LocalDate> wanted, Store store) {
        List<PickupPolicyExcludedDate> current = excludedDates.findByPolicyIdOrderByExcludedDate(policy.getId());
        Set<LocalDate> have = current.stream().map(PickupPolicyExcludedDate::getExcludedDate)
                .collect(Collectors.toSet());
        for (PickupPolicyExcludedDate row : current) {
            if (!wanted.contains(row.getExcludedDate())) {
                excludedDates.delete(row);
                history.record(history.event(AutomationAction.EXCLUDED_DATE_REMOVED, policy.getId())
                        .region(store.getRegionId()).metadata("storeId", store.getId().toString())
                        .metadata("date", row.getExcludedDate().toString()).build());
            }
        }
        excludedDates.flush();
        for (LocalDate date : wanted) {
            if (!have.contains(date)) {
                PickupPolicyExcludedDate row = new PickupPolicyExcludedDate();
                row.setPolicyId(policy.getId());
                row.setExcludedDate(date);
                excludedDates.save(row);
                history.record(history.event(AutomationAction.EXCLUDED_DATE_ADDED, policy.getId())
                        .region(store.getRegionId()).metadata("storeId", store.getId().toString())
                        .metadata("date", date.toString()).build());
            }
        }
    }

    private PolicyView view(PickupPolicy policy) {
        List<PickupPolicyLinkedStore> rules = links.findByPolicyIdOrderByCreatedAtAscIdAsc(policy.getId());
        Map<UUID, List<UUID>> storeIdsByLink = members.findByLinkIdIn(rules.stream().map(PickupPolicyLinkedStore::getId)
                .toList()).stream().collect(Collectors.groupingBy(PickupPolicyLinkedStoreMember::getLinkId,
                Collectors.mapping(PickupPolicyLinkedStoreMember::getStoreId, Collectors.toList())));
        List<UUID> allIds = storeIdsByLink.values().stream().flatMap(List::stream).distinct().toList();
        Map<UUID, Store> byId = stores.liveStores(allIds).stream().collect(Collectors.toMap(Store::getId, s -> s));
        List<LinkView> views = new ArrayList<>();
        for (PickupPolicyLinkedStore rule : rules) {
            views.add(new LinkView(rule, storeIdsByLink.getOrDefault(rule.getId(), List.of()).stream()
                    .map(byId::get).filter(java.util.Objects::nonNull).toList()));
        }
        return new PolicyView(policy, views, excludedDates.findByPolicyIdOrderByExcludedDate(policy.getId()).stream()
                .map(PickupPolicyExcludedDate::getExcludedDate).toList(), gapStores(policy));
    }

    /** The other live stores of the policy's gap list, by name. */
    private List<GapStoreView> gapStores(PickupPolicy policy) {
        if (policy.getGapGroupId() == null) {
            return List.of();
        }
        Map<UUID, PickupPolicy> byStore = policies.findByGapGroupId(policy.getGapGroupId()).stream()
                .filter(p -> !p.getStoreId().equals(policy.getStoreId()))
                .collect(Collectors.toMap(PickupPolicy::getStoreId, p -> p));
        SessionPrincipal actor = currentActor.principal().orElse(null);
        return stores.liveStores(byStore.keySet()).stream()
                .map(s -> new GapStoreView(s, byStore.get(s.getId()).isEnabled(),
                        permissions.mayUseAutomations(actor, s)))
                .toList();
    }
}
