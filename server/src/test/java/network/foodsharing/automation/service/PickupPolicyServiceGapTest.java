package network.foodsharing.automation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.automation.domain.AutomationAction;
import network.foodsharing.automation.domain.LinkedStoreWindowMode;
import network.foodsharing.automation.domain.PickupGapGroup;
import network.foodsharing.automation.domain.PickupPolicy;
import network.foodsharing.automation.domain.PickupPolicyLinkedStore;
import network.foodsharing.automation.domain.PickupPolicyLinkedStoreMember;
import network.foodsharing.automation.repository.PickupGapGroupRepository;
import network.foodsharing.automation.repository.PickupPolicyExcludedDateRepository;
import network.foodsharing.automation.repository.PickupPolicyLinkedStoreMemberRepository;
import network.foodsharing.automation.repository.PickupPolicyLinkedStoreRepository;
import network.foodsharing.automation.repository.PickupPolicyRepository;
import network.foodsharing.automation.service.PickupPolicyService.LinkInput;
import network.foodsharing.automation.service.PickupPolicyService.PolicyInput;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.fsp.service.FspCleaningQueries;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StorePermissionService;
import network.foodsharing.store.service.StoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Shared gap list of {@link PickupPolicyService} (decisions 151 D1/D6/D7 and 152) on in-memory repositories: limits,
 * group gap, taken stores, permissions (403 naming the store), leaving, dissolving and the link conflict on both save
 * paths.
 */
class PickupPolicyServiceGapTest {

    static final UUID REGION = UUID.randomUUID();
    static final SessionPrincipal ACTOR = new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), Role.STORE_MANAGER,
            "manager@test.local");

    final Map<UUID, Store> storesById = new HashMap<>();
    final Map<UUID, PickupPolicy> policiesByStore = new HashMap<>();
    final Map<UUID, PickupGapGroup> groups = new HashMap<>();
    final Map<UUID, List<PickupPolicyLinkedStore>> linksByPolicy = new HashMap<>();
    final Map<UUID, List<UUID>> linkMembers = new HashMap<>();
    final Set<UUID> allowed = new HashSet<>();

    PickupPolicyRepository policies;
    PickupPolicyLinkedStoreRepository links;
    PickupGapGroupRepository gapGroups;
    HistoryService history;
    PickupPolicyService service;

    UUID a;
    UUID b;
    UUID c;
    UUID d;

    @BeforeEach
    void setUp() {
        policies = mock(PickupPolicyRepository.class);
        links = mock(PickupPolicyLinkedStoreRepository.class);
        PickupPolicyLinkedStoreMemberRepository members = mock(PickupPolicyLinkedStoreMemberRepository.class);
        gapGroups = mock(PickupGapGroupRepository.class);
        history = mock(HistoryService.class, RETURNS_DEEP_STUBS);
        StoreService stores = mock(StoreService.class);
        StorePermissionService permissions = mock(StorePermissionService.class);
        CurrentActorService actor = mock(CurrentActorService.class);
        RegionService regions = mock(RegionService.class);

        when(actor.requirePrincipal()).thenReturn(ACTOR);
        when(actor.principal()).thenReturn(Optional.of(ACTOR));
        when(regions.subtreeIds(REGION)).thenReturn(List.of(REGION));
        when(stores.require(any())).thenAnswer(inv -> {
            Store store = storesById.get(inv.<UUID>getArgument(0));
            if (store == null) {
                throw ApiException.notFound(ApiErrorCode.STORE_NOT_FOUND);
            }
            return store;
        });
        when(stores.liveStores(anyCollection())).thenAnswer(inv -> sorted(inv.<Collection<UUID>>getArgument(0)));
        when(stores.liveStoresInRegions(anyCollection())).thenAnswer(inv -> sorted(storesById.keySet()));
        when(stores.storesById(anyCollection())).thenAnswer(inv -> sorted(inv.<Collection<UUID>>getArgument(0)));
        when(permissions.mayEditStore(eq(ACTOR), any())).thenAnswer(inv ->
                allowed.contains(inv.<Store>getArgument(1).getId()));

        when(policies.findByStoreId(any())).thenAnswer(inv ->
                Optional.ofNullable(policiesByStore.get(inv.<UUID>getArgument(0))));
        when(policies.findByStoreIdIn(anyCollection())).thenAnswer(inv -> inv.<Collection<UUID>>getArgument(0)
                .stream().map(policiesByStore::get).filter(java.util.Objects::nonNull).toList());
        when(policies.findByGapGroupId(any())).thenAnswer(inv -> policiesByStore.values().stream()
                .filter(p -> inv.getArgument(0).equals(p.getGapGroupId())).toList());
        when(policies.saveAndFlush(any())).thenAnswer(inv -> {
            PickupPolicy policy = inv.getArgument(0);
            if (policy.getId() == null) {
                policy.setId(UUID.randomUUID());
            }
            policiesByStore.put(policy.getStoreId(), policy);
            return policy;
        });
        when(gapGroups.lockById(any())).thenAnswer(inv -> Optional.ofNullable(groups.get(inv.<UUID>getArgument(0))));
        when(gapGroups.findById(any())).thenAnswer(inv -> Optional.ofNullable(groups.get(inv.<UUID>getArgument(0))));
        when(gapGroups.saveAndFlush(any())).thenAnswer(inv -> {
            PickupGapGroup group = inv.getArgument(0);
            if (group.getId() == null) {
                group.setId(UUID.randomUUID());
            }
            groups.put(group.getId(), group);
            return group;
        });
        org.mockito.Mockito.doAnswer(inv -> groups.remove(inv.<PickupGapGroup>getArgument(0).getId()))
                .when(gapGroups).delete(any());
        when(links.findByPolicyIdOrderByCreatedAtAscIdAsc(any())).thenAnswer(inv ->
                linksByPolicy.getOrDefault(inv.<UUID>getArgument(0), List.of()));
        when(links.saveAndFlush(any())).thenAnswer(inv -> {
            PickupPolicyLinkedStore link = inv.getArgument(0);
            link.setId(UUID.randomUUID());
            return link;
        });
        when(members.findByLinkIdIn(anyList())).thenAnswer(inv -> {
            List<PickupPolicyLinkedStoreMember> result = new ArrayList<>();
            for (UUID linkId : inv.<List<UUID>>getArgument(0)) {
                for (UUID storeId : linkMembers.getOrDefault(linkId, List.of())) {
                    PickupPolicyLinkedStoreMember member = new PickupPolicyLinkedStoreMember();
                    member.setLinkId(linkId);
                    member.setStoreId(storeId);
                    result.add(member);
                }
            }
            return result;
        });

        service = new PickupPolicyService(policies, links, members, mock(PickupPolicyExcludedDateRepository.class),
                stores, permissions, actor, history, mock(FspCleaningQueries.class), gapGroups, regions, mock(PickupPolicyData.class));

        a = store("A Store");
        b = store("B Store");
        c = store("C Store");
        d = store("D Store");
        allowed.addAll(List.of(a, b, c, d));
    }

    UUID store(String name) {
        Store store = new Store();
        store.setId(UUID.randomUUID());
        store.setName(name);
        store.setRegionId(REGION);
        storesById.put(store.getId(), store);
        return store.getId();
    }

    List<Store> sorted(Collection<UUID> ids) {
        return ids.stream().map(storesById::get).filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparing(Store::getName)).toList();
    }

    PickupPolicy policy(UUID storeId, boolean enabled, int gap, boolean gapOn) {
        PickupPolicy policy = new PickupPolicy();
        policy.setId(UUID.randomUUID());
        policy.setStoreId(storeId);
        policy.setEnabled(enabled);
        policy.setMinimumGapDays(gap);
        policy.setGapRuleEnabled(gapOn);
        policiesByStore.put(storeId, policy);
        return policy;
    }

    /** A gap list with these stores (gap 2, rule on everywhere). */
    UUID group(UUID... storeIds) {
        PickupGapGroup group = new PickupGapGroup();
        group.setId(UUID.randomUUID());
        group.setMinimumGapDays(2);
        groups.put(group.getId(), group);
        for (UUID id : storeIds) {
            PickupPolicy policy = policiesByStore.containsKey(id) ? policiesByStore.get(id) : policy(id, true, 2, true);
            policy.setMinimumGapDays(2);
            policy.setGapRuleEnabled(true);
            policy.setGapGroupId(group.getId());
        }
        return group.getId();
    }

    void link(UUID owner, LinkedStoreWindowMode mode, int before, int after, UUID... storeIds) {
        PickupPolicy policy = policiesByStore.containsKey(owner) ? policiesByStore.get(owner)
                : policy(owner, true, 0, false);
        PickupPolicyLinkedStore link = new PickupPolicyLinkedStore();
        link.setId(UUID.randomUUID());
        link.setPolicyId(policy.getId());
        link.setWindowMode(mode);
        link.setWindowDaysBefore(before);
        link.setWindowDaysAfter(after);
        linksByPolicy.computeIfAbsent(policy.getId(), k -> new ArrayList<>()).add(link);
        linkMembers.put(link.getId(), List.of(storeIds));
    }

    /** Own gap and group gap both {@code gap} (the state right after migration 182). */
    static PolicyInput input(boolean gapOn, int gap, List<UUID> list, List<LinkInput> links) {
        return input(gapOn, gap, list.isEmpty() ? null : gap, list, links);
    }

    static PolicyInput input(boolean ownOn, int ownGap, Integer groupGap, List<UUID> list, List<LinkInput> links) {
        return new PolicyInput(true, ownOn, ownGap, false, false, false, links, List.of(), null, list, groupGap);
    }

    static PolicyInput input(boolean ownOn, int ownGap, Integer groupGap, List<UUID> list) {
        return input(ownOn, ownGap, groupGap, list, List.of());
    }

    static PolicyInput input(boolean gapOn, int gap, List<UUID> list) {
        return input(gapOn, gap, list, List.of());
    }

    static LinkInput sameDay(UUID... storeIds) {
        return new LinkInput(List.of(storeIds), false, LinkedStoreWindowMode.SAME_DAY, 0, 0, 20, null, null);
    }

    static ApiException error(Runnable call) {
        try {
            call.run();
        } catch (ApiException ex) {
            return ex;
        }
        throw new AssertionError("no ApiException");
    }

    PickupPolicy policyOf(UUID storeId) {
        return policiesByStore.get(storeId);
    }

    // ------------------------------------------------------------------ D1 limits

    @Test
    void atMostTenStoresIncludingThisOne() {
        List<UUID> nine = new ArrayList<>(List.of(b, c, d));
        while (nine.size() < 9) {
            UUID id = store("Store " + nine.size());
            allowed.add(id);
            nine.add(id);
        }
        assertThatCode(() -> service.put(a, input(true, 2, nine))).doesNotThrowAnyException();
        List<UUID> ten = new ArrayList<>(nine);
        ten.add(store("Store 10"));
        ApiException ex = error(() -> service.put(a, input(true, 2, ten)));
        assertThat(ex.getCode()).isEqualTo(ApiErrorCode.GAP_LIST_TOO_LARGE);
    }

    @Test
    void aListNeedsAGroupGapOfOneToSixtyDays() {
        for (Integer groupGap : java.util.Arrays.asList(null, 0, 61)) {
            ApiException ex = error(() -> service.put(a, input(true, 2, groupGap, List.of(b))));
            assertThat(ex.getCode()).as("group gap %s", groupGap).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
            assertThat(ex.getArgs()).containsEntry("field", "groupMinimumGapDays");
        }
        // the own gap may stay 0 with a list (decision 152), and without a list 0 stays allowed
        assertThatCode(() -> service.put(a, input(true, 0, 60, List.of(b)))).doesNotThrowAnyException();
        assertThatCode(() -> service.put(c, input(true, 0, List.of()))).doesNotThrowAnyException();
        // the own gap keeps its own range
        assertThat(error(() -> service.put(d, input(true, 61, 2, List.of()))).getArgs())
                .containsEntry("field", "minimumGapDays");
    }

    @Test
    void thisStoreCannotBeOnItsOwnList() {
        ApiException ex = error(() -> service.put(a, input(true, 2, List.of(a, b))));
        assertThat(ex.getArgs()).containsEntry("field", "gapStoreIds");
    }

    @Test
    void aStoreOutsideTheRegionIsNoCandidate() {
        UUID elsewhere = UUID.randomUUID();
        Store store = new Store();
        store.setId(elsewhere);
        store.setName("Elsewhere");
        store.setRegionId(UUID.randomUUID());
        storesById.put(elsewhere, store);
        allowed.add(elsewhere);
        RegionService regions = mock(RegionService.class);
        // liveStoresInRegions answers with every store, so narrow it for this case
        StoreService stores = mock(StoreService.class);
        when(stores.require(any())).thenAnswer(inv -> storesById.get(inv.<UUID>getArgument(0)));
        when(stores.liveStores(anyCollection())).thenAnswer(inv -> sorted(inv.<Collection<UUID>>getArgument(0)));
        when(stores.liveStoresInRegions(anyCollection())).thenReturn(sorted(List.of(a, b, c, d)));
        when(regions.subtreeIds(REGION)).thenReturn(List.of(REGION));
        StorePermissionService permissions = mock(StorePermissionService.class);
        when(permissions.mayEditStore(any(), any())).thenReturn(true);
        CurrentActorService actor = mock(CurrentActorService.class);
        when(actor.requirePrincipal()).thenReturn(ACTOR);
        PickupPolicyService narrow = new PickupPolicyService(policies, links,
                mock(PickupPolicyLinkedStoreMemberRepository.class), mock(PickupPolicyExcludedDateRepository.class),
                stores, permissions, actor, history, mock(FspCleaningQueries.class), gapGroups, regions, mock(PickupPolicyData.class));
        ApiException ex = error(() -> narrow.put(a, input(true, 2, List.of(elsewhere))));
        assertThat(ex.getCode()).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        assertThat(ex.getArgs()).containsEntry("storeId", elsewhere.toString());
    }

    // ------------------------------------------------------------------ create, write, taken

    @Test
    void savingWritesOnlyTheGroupRowAndMembershipWithHistory() {
        policy(b, true, 5, false);
        var view = service.put(a, input(true, 4, 3, List.of(b, c)));
        UUID groupId = policyOf(a).getGapGroupId();
        assertThat(groupId).isNotNull();
        assertThat(groups.get(groupId).getMinimumGapDays()).isEqualTo(3);
        assertThat(view.groupMinimumGapDays()).isEqualTo(3);
        for (UUID id : List.of(b, c)) {
            assertThat(policyOf(id).getGapGroupId()).isEqualTo(groupId);
        }
        // every store keeps its own gap and switch (decision 152)
        assertThat(policyOf(a).getMinimumGapDays()).isEqualTo(4);
        assertThat(policyOf(a).isGapRuleEnabled()).isTrue();
        assertThat(policyOf(b).getMinimumGapDays()).isEqualTo(5);
        assertThat(policyOf(b).isGapRuleEnabled()).isFalse();
        // B keeps its own switches; C had no policy: created off and with its own rule off
        assertThat(policyOf(b).isEnabled()).isTrue();
        assertThat(policyOf(c).isEnabled()).isFalse();
        assertThat(policyOf(c).isGapRuleEnabled()).isFalse();
        assertThat(policyOf(c).getMinimumGapDays()).isZero();
        assertThat(view.gapStores()).extracting(g -> g.store().getId()).containsExactly(b, c);
        assertThat(view.gapStores()).extracting(PickupPolicyService.GapStoreView::automationActive)
                .containsExactly(true, false);
        for (UUID id : List.of(a, b, c)) {
            verify(history, atLeastOnce()).event(AutomationAction.GAP_LIST_CHANGED, policyOf(id).getId());
        }
    }

    @Test
    void aStoreInAnotherListIsTaken() {
        group(c, d);
        ApiException ex = error(() -> service.put(a, input(true, 2, List.of(b, c))));
        assertThat(ex.getCode()).isEqualTo(ApiErrorCode.GAP_LIST_STORE_TAKEN);
        assertThat(ex.getArgs()).containsEntry("storeId", c.toString());
    }

    @Test
    void addingAStoreWithoutPermissionIs403NamingIt() {
        allowed.remove(c);
        ApiException ex = error(() -> service.put(a, input(true, 2, List.of(b, c))));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getCode()).isEqualTo(ApiErrorCode.ACCESS_DENIED);
        assertThat(ex.getArgs()).containsEntry("storeId", c.toString());
        assertThat(policyOf(b)).isNull();
    }

    @Test
    void aLockedMemberMayStayButItsGapAndMembershipCannotChange() {
        UUID groupId = group(a, b);
        allowed.remove(b);
        // unchanged gap and list: allowed (other settings of this store change)
        assertThatCode(() -> service.put(a, input(true, 2, List.of(b)))).doesNotThrowAnyException();
        assertThat(policyOf(a).getGapGroupId()).isEqualTo(groupId);
        // a new gap would change B
        assertThat(error(() -> service.put(a, input(true, 3, List.of(b)))).getArgs())
                .containsEntry("storeId", b.toString());
        // removing B while adding C changes B's membership
        assertThat(error(() -> service.put(a, input(true, 2, List.of(c)))).getArgs())
                .containsEntry("storeId", b.toString());
    }

    @Test
    void permissionOnTheOthersIsNeededOnlyWhenTheGroupGapOrMembershipChanges() {
        group(a, b);
        allowed.remove(b);
        // this store's own gap and switch change: B is not touched
        assertThatCode(() -> service.put(a, input(false, 7, 2, List.of(b)))).doesNotThrowAnyException();
        assertThat(policyOf(a).getMinimumGapDays()).isEqualTo(7);
        assertThat(policyOf(b).getMinimumGapDays()).isEqualTo(2);
        // the group gap changes B's checks
        ApiException ex = error(() -> service.put(a, input(false, 7, 3, List.of(b))));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getArgs()).containsEntry("storeId", b.toString());
    }

    @Test
    void existingMembersStayAndAddedOnesJoin() {
        UUID groupId = group(a, b);
        service.put(a, input(true, 2, List.of(b, c)));
        assertThat(policyOf(c).getGapGroupId()).isEqualTo(groupId);
        assertThat(groups).hasSize(1);
    }

    // ------------------------------------------------------------------ leaving

    @Test
    void ruleOffKeepsTheGroupWithoutPermissionOnTheOthers() {
        UUID groupId = group(a, b, c);
        allowed.removeAll(List.of(b, c));
        var view = service.put(a, input(false, 2, List.of(b, c)));
        assertThat(policyOf(a).getGapGroupId()).isEqualTo(groupId);
        assertThat(policyOf(a).isGapRuleEnabled()).isFalse();
        assertThat(policyOf(a).getMinimumGapDays()).isEqualTo(2);
        assertThat(policyOf(b).getGapGroupId()).isEqualTo(groupId);
        assertThat(policyOf(c).getGapGroupId()).isEqualTo(groupId);
        assertThat(groups.get(groupId).getMinimumGapDays()).isEqualTo(2);
        assertThat(view.gapStores()).hasSize(2);
        verify(history, never()).event(AutomationAction.GAP_LIST_CHANGED, policyOf(b).getId());
    }

    @Test
    void anEmptyListWithALockedMemberLeavesAndKeepsEveryOwnRule() {
        UUID groupId = group(a, b, c);
        allowed.removeAll(List.of(b, c));
        policyOf(a).setGapRuleEnabled(false);
        service.put(a, input(false, 0, List.of()));
        assertThat(policyOf(a).getGapGroupId()).isNull();
        assertThat(policyOf(b).getGapGroupId()).isEqualTo(groupId);
        assertThat(policyOf(b).isGapRuleEnabled()).isTrue();
        assertThat(policyOf(b).getMinimumGapDays()).isEqualTo(2);
        verify(history, atLeastOnce()).event(AutomationAction.GAP_LIST_CHANGED, policyOf(b).getId());
    }

    @Test
    void anEmptyListLeavesAndAStoreLeftAloneKeepsItsGap() {
        UUID groupId = group(a, b);
        allowed.remove(b);
        service.put(a, input(true, 2, List.of()));
        assertThat(policyOf(a).getGapGroupId()).isNull();
        assertThat(policyOf(a).isGapRuleEnabled()).isTrue();
        assertThat(policyOf(b).getGapGroupId()).isNull();
        assertThat(policyOf(b).getMinimumGapDays()).isEqualTo(2);
        assertThat(policyOf(b).isGapRuleEnabled()).isTrue();
        assertThat(groups).doesNotContainKey(groupId);
    }

    @Test
    void deletingThePolicyLeavesTheList() {
        UUID groupId = group(a, b, c);
        allowed.removeAll(List.of(b, c));
        PickupPolicy own = policyOf(a);
        service.delete(a);
        verify(policies).delete(own);
        assertThat(own.getGapGroupId()).isNull();
        assertThat(policyOf(b).getGapGroupId()).isEqualTo(groupId);
    }

    @Test
    void removingTheLastOtherStoreDissolvesTheList() {
        UUID groupId = group(a, b, c);
        service.put(a, input(true, 2, List.of(b)));
        assertThat(policyOf(c).getGapGroupId()).isNull();
        assertThat(policyOf(c).getMinimumGapDays()).isEqualTo(2);
        assertThat(policyOf(b).getGapGroupId()).isEqualTo(groupId);
    }

    // ------------------------------------------------------------------ D7 link conflict

    @Test
    void reachIsZeroOnTheSameDayAndMaxMinusOneOtherwise() {
        assertThat(GapLinkConflicts.reach(LinkedStoreWindowMode.SAME_DAY, 5, 5)).isZero();
        assertThat(GapLinkConflicts.reach(LinkedStoreWindowMode.LAST_DAYS, 3, 1)).isEqualTo(2);
        assertThat(GapLinkConflicts.reach(LinkedStoreWindowMode.LAST_DAYS, 0, 1)).isZero();
    }

    @Test
    void conflictMatrix() {
        Set<UUID> list = Set.of(a, b);
        var sameDay = new GapLinkConflicts.Link(a, List.of(b), LinkedStoreWindowMode.SAME_DAY, 0, 0);
        assertThat(GapLinkConflicts.find(list, 1, List.of(sameDay))).singleElement()
                .satisfies(conflict -> assertThat(conflict.blocking()).isTrue());
        // reach 2 = gap 2: no conflict; gap 3: conflict
        var wide = new GapLinkConflicts.Link(a, List.of(b), LinkedStoreWindowMode.LAST_DAYS, 3, 0);
        assertThat(GapLinkConflicts.find(list, 2, List.of(wide))).isEmpty();
        assertThat(GapLinkConflicts.find(list, 3, List.of(wide))).hasSize(1);
        // a partly listed rule only warns
        var partial = new GapLinkConflicts.Link(a, List.of(b, c), LinkedStoreWindowMode.SAME_DAY, 0, 0);
        assertThat(GapLinkConflicts.find(list, 2, List.of(partial))).singleElement()
                .satisfies(conflict -> assertThat(conflict.blocking()).isFalse());
        // owner outside the list: nothing
        var outside = new GapLinkConflicts.Link(c, List.of(a), LinkedStoreWindowMode.SAME_DAY, 0, 0);
        assertThat(GapLinkConflicts.find(list, 2, List.of(outside))).isEmpty();
    }

    @Test
    void gapListSaveIsBlockedByAnOwnLinkToAListedStore() {
        link(a, LinkedStoreWindowMode.SAME_DAY, 0, 0, b);
        ApiException ex = error(() -> service.put(a, input(true, 1, List.of(b), List.of(sameDay(b)))));
        assertThat(ex.getCode()).isEqualTo(ApiErrorCode.GAP_LINK_CONFLICT);
        assertThat(ex.getArgs()).containsEntry("storeId", a.toString()).containsEntry("otherStoreId", b.toString());
    }

    @Test
    void gapListSaveIsBlockedByTheOtherStoresRuleAndAMirror() {
        // B's own rule asks for a pickup in A within 2 days around: reach 1 < gap 2
        link(b, LinkedStoreWindowMode.LAST_DAYS, 2, 2, a);
        ApiException ex = error(() -> service.put(a, input(true, 2, List.of(b))));
        assertThat(ex.getCode()).isEqualTo(ApiErrorCode.GAP_LINK_CONFLICT);
        assertThat(ex.getArgs()).containsEntry("storeId", b.toString()).containsEntry("otherStoreId", a.toString());
        // the same rule is fine with gap 1
        assertThatCode(() -> service.put(a, input(true, 1, List.of(b)))).doesNotThrowAnyException();
    }

    @Test
    void linkConflictUsesTheGroupGapNotTheOwnGap() {
        // B's own rule asks for a pickup in A within 2 days around: reach 1
        link(b, LinkedStoreWindowMode.LAST_DAYS, 2, 2, a);
        // group gap 2 with the own rule off and own gap 0: blocks
        ApiException ex = error(() -> service.put(a, input(false, 0, 2, List.of(b))));
        assertThat(ex.getCode()).isEqualTo(ApiErrorCode.GAP_LINK_CONFLICT);
        // own gap 5 with group gap 1: no conflict
        assertThatCode(() -> service.put(a, input(true, 5, 1, List.of(b)))).doesNotThrowAnyException();
    }

    @Test
    void aPartlyListedLinkOnlyWarns() {
        assertThatCode(() -> service.put(a, input(true, 2, List.of(b), List.of(sameDay(b, d)))))
                .doesNotThrowAnyException();
    }

    @Test
    void linkSaveIsBlockedInsideAList() {
        group(a, b);
        ApiException ex = error(() -> service.put(a, input(true, 2, List.of(b), List.of(sameDay(b)))));
        assertThat(ex.getCode()).isEqualTo(ApiErrorCode.GAP_LINK_CONFLICT);
        verify(links, never()).saveAndFlush(any());
    }

    @Test
    void bidirectionalLinkMirrorCountsForTheListedTarget() {
        // A → [B, D] both ways creates a mirror B → [A], fully listed with B on the list
        LinkInput both = new LinkInput(List.of(b, d), true, LinkedStoreWindowMode.SAME_DAY, 0, 0, 20, null, null);
        ApiException ex = error(() -> service.put(a, input(true, 2, List.of(b), List.of(both))));
        assertThat(ex.getArgs()).containsEntry("storeId", b.toString()).containsEntry("otherStoreId", a.toString());
    }

    @Test
    void aOneWayLinkNeedsNoRightInTheLinkedStoreABidirectionalOneDoes() {
        // decision 167 Q3: D is a store the actor may know but not edit (e.g. another region)
        allowed.remove(d);
        LinkInput oneWay = new LinkInput(List.of(d), false, LinkedStoreWindowMode.SAME_DAY, 0, 0, 120, null, null);
        service.put(a, input(true, 2, List.of(), List.of(oneWay)));
        verify(links, atLeastOnce()).saveAndFlush(any());

        LinkInput both = new LinkInput(List.of(d), true, LinkedStoreWindowMode.SAME_DAY, 0, 0, 120, null, null);
        ApiException ex = error(() -> service.put(a, input(true, 2, List.of(), List.of(both))));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getCode()).isEqualTo(ApiErrorCode.ACCESS_DENIED);
        assertThat(ex.getArgs()).containsEntry("storeId", d.toString());
    }

    // ------------------------------------------------------------------ bidirectional links need Y only when Y changes

    /** A saved bidirectional own rule of {@code owner} to {@code target} (20 minutes grace, no message). */
    void bidirectionalLink(UUID owner, LinkedStoreWindowMode mode, int before, int after, UUID target) {
        link(owner, mode, before, after, target);
        PickupPolicyLinkedStore saved = linksByPolicy.get(policiesByStore.get(owner).getId()).getLast();
        saved.setBidirectional(true);
        saved.setGraceMinutes(20);
    }

    static LinkInput bidirectional(LinkedStoreWindowMode mode, int before, int after, int grace, String message,
                                   UUID... storeIds) {
        return new LinkInput(List.of(storeIds), true, mode, before, after, grace, message, null);
    }

    static PolicyInput withLinks(int gap, LinkInput... rules) {
        return input(true, gap, List.of(), List.of(rules));
    }

    @Test
    void unchangedBidirectionalRuleSavesWithoutRightsOnTheLinkedStore() {
        policy(a, true, 0, false);
        bidirectionalLink(a, LinkedStoreWindowMode.SAME_DAY, 0, 0, b);
        allowed.remove(b);
        assertThatCode(() -> service.put(a, withLinks(3,
                bidirectional(LinkedStoreWindowMode.SAME_DAY, 0, 0, 20, null, b)))).doesNotThrowAnyException();
    }

    @Test
    void sameDayIgnoresStrayWindowDaysAndBlankMessageCountsAsNone() {
        policy(a, true, 0, false);
        bidirectionalLink(a, LinkedStoreWindowMode.SAME_DAY, 0, 0, b);
        allowed.remove(b);
        assertThatCode(() -> service.put(a, withLinks(3,
                bidirectional(LinkedStoreWindowMode.SAME_DAY, 4, 5, 20, "  ", b)))).doesNotThrowAnyException();
    }

    @Test
    void newBidirectionalRuleStillNeedsRightsOnTheLinkedStore() {
        policy(a, true, 0, false);
        allowed.remove(b);
        ApiException ex = error(() -> service.put(a, withLinks(0,
                bidirectional(LinkedStoreWindowMode.SAME_DAY, 0, 0, 20, null, b))));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getArgs()).containsEntry("storeId", b.toString());
    }

    @Test
    void makingAOneWayRuleBidirectionalStillNeedsRights() {
        policy(a, true, 0, false);
        link(a, LinkedStoreWindowMode.SAME_DAY, 0, 0, b);
        allowed.remove(b);
        ApiException ex = error(() -> service.put(a, withLinks(0,
                bidirectional(LinkedStoreWindowMode.SAME_DAY, 0, 0, 20, null, b))));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void addingAStoreToABidirectionalRuleNeedsRightsOnlyOnTheNewOne() {
        policy(a, true, 0, false);
        bidirectionalLink(a, LinkedStoreWindowMode.SAME_DAY, 0, 0, b);
        allowed.remove(b);
        allowed.remove(c);
        ApiException ex = error(() -> service.put(a, withLinks(0,
                bidirectional(LinkedStoreWindowMode.SAME_DAY, 0, 0, 20, null, b, c))));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getArgs()).containsEntry("storeId", c.toString());
    }

    @Test
    void changingWhatTheMirrorIsBuiltFromNeedsRights() {
        policy(a, true, 0, false);
        bidirectionalLink(a, LinkedStoreWindowMode.LAST_DAYS, 1, 2, b);
        allowed.remove(b);
        List<LinkInput> changed = List.of(
                bidirectional(LinkedStoreWindowMode.SAME_DAY, 0, 0, 20, null, b),
                bidirectional(LinkedStoreWindowMode.LAST_DAYS, 2, 2, 20, null, b),
                bidirectional(LinkedStoreWindowMode.LAST_DAYS, 1, 3, 20, null, b),
                bidirectional(LinkedStoreWindowMode.LAST_DAYS, 1, 2, 30, null, b),
                bidirectional(LinkedStoreWindowMode.LAST_DAYS, 1, 2, 20, "Hallo", b));
        for (LinkInput rule : changed) {
            ApiException ex = error(() -> service.put(a, withLinks(0, rule)));
            assertThat(ex.getStatus()).as("%s", rule).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(ex.getArgs()).containsEntry("storeId", b.toString());
        }
    }

    @Test
    void removingTheStoreOrSwitchingBidirectionalOffNeedsNoRightsOnIt() {
        policy(a, true, 0, false);
        bidirectionalLink(a, LinkedStoreWindowMode.SAME_DAY, 0, 0, b);
        allowed.remove(b);
        assertThatCode(() -> service.put(a, withLinks(0, sameDay(b)))).doesNotThrowAnyException();
        assertThatCode(() -> service.put(a, withLinks(0))).doesNotThrowAnyException();
    }
}
