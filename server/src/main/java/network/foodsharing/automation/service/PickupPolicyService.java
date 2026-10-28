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
import network.foodsharing.automation.domain.PickupPolicy;
import network.foodsharing.automation.domain.PickupPolicyExcludedDate;
import network.foodsharing.automation.domain.PickupPolicyLinkedStore;
import network.foodsharing.automation.domain.PickupPolicyLinkedStoreMember;
import network.foodsharing.automation.repository.PickupPolicyExcludedDateRepository;
import network.foodsharing.automation.repository.PickupPolicyLinkedStoreMemberRepository;
import network.foodsharing.automation.repository.PickupPolicyLinkedStoreRepository;
import network.foodsharing.automation.repository.PickupPolicyRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
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
    static final Set<String> PLACEHOLDERS = Set.of("name", "storeName", "linkedStoreName", "pickupDate", "requirement",
            "graceMinutes");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([^}\\s]+)\\s*}}");

    public record LinkInput(List<UUID> storeIds, boolean bidirectional, LinkedStoreWindowMode windowMode,
                            int windowDaysBefore, int windowDaysAfter, int graceMinutes, String message,
                            UUID mirrorOf) {
    }

    public record PolicyInput(boolean enabled, boolean dryRun, boolean gapRuleEnabled, int minimumGapDays,
                              boolean cleaningRuleEnabled, boolean experienceRuleEnabled,
                              boolean publicHolidayRuleEnabled, List<LinkInput> links, List<LocalDate> excludedDates,
                              Long version) {
    }

    public record LinkView(PickupPolicyLinkedStore link, List<Store> stores) {
    }

    public record PolicyView(PickupPolicy policy, List<LinkView> links, List<LocalDate> excludedDates) {
    }

    private final PickupPolicyRepository policies;
    private final PickupPolicyLinkedStoreRepository links;
    private final PickupPolicyLinkedStoreMemberRepository members;
    private final PickupPolicyExcludedDateRepository excludedDates;
    private final StoreService stores;
    private final StorePermissionService permissions;
    private final CurrentActorService currentActor;
    private final HistoryService history;

    public PickupPolicyService(PickupPolicyRepository policies, PickupPolicyLinkedStoreRepository links,
                               PickupPolicyLinkedStoreMemberRepository members,
                               PickupPolicyExcludedDateRepository excludedDates, StoreService stores,
                               StorePermissionService permissions, CurrentActorService currentActor,
                               HistoryService history) {
        this.policies = policies;
        this.links = links;
        this.members = members;
        this.excludedDates = excludedDates;
        this.stores = stores;
        this.permissions = permissions;
        this.currentActor = currentActor;
        this.history = history;
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
        PickupPolicy policy = existing.orElseGet(PickupPolicy::new);
        boolean created = existing.isEmpty();
        policy.setStoreId(storeId);
        policy.setRegionId(store.getRegionId());
        policy.setEnabled(input.enabled());
        policy.setDryRun(input.dryRun());
        policy.setGapRuleEnabled(input.gapRuleEnabled());
        policy.setMinimumGapDays(input.minimumGapDays());
        policy.setCleaningRuleEnabled(input.cleaningRuleEnabled());
        policy.setExperienceRuleEnabled(input.experienceRuleEnabled());
        policy.setPublicHolidayRuleEnabled(input.publicHolidayRuleEnabled());
        policy = policies.saveAndFlush(policy);
        replaceOwnLinks(policy, own);
        replaceExcludedDates(policy, dates, store);
        history.record(history.event(created ? AutomationAction.PICKUP_POLICY_CREATED
                : AutomationAction.PICKUP_POLICY_UPDATED, policy.getId()).region(store.getRegionId())
                .metadata("storeId", storeId.toString()).build());
        return view(policy);
    }

    /** Deletes the policy with its rules, their mirrors and excluded dates. @throws ApiException 403, 404 */
    @Transactional
    public void delete(UUID storeId) {
        Store store = requireAutomationStore(storeId);
        PickupPolicy policy = require(storeId);
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
                .map(PickupPolicyExcludedDate::getExcludedDate).toList());
    }
}
