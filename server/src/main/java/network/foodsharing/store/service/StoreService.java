package network.foodsharing.store.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionPermissionResolver;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.ConvinceStatus;
import network.foodsharing.store.domain.CooperationStatus;
import network.foodsharing.store.domain.PublicPickupTime;
import network.foodsharing.store.domain.PublicityStatus;
import network.foodsharing.store.domain.StickerStatus;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.domain.StoreGrocery;
import network.foodsharing.store.domain.StoreLogAction;
import network.foodsharing.store.domain.StoreMembership;
import network.foodsharing.store.domain.StoreMembershipStatus;
import network.foodsharing.store.domain.TeamSearchStatus;
import network.foodsharing.store.domain.WallMilestone;
import network.foodsharing.store.repository.GroceryRepository;
import network.foodsharing.store.repository.PickupWeightCategoryRepository;
import network.foodsharing.store.repository.StoreCategoryRepository;
import network.foodsharing.store.repository.StoreChainRepository;
import network.foodsharing.store.repository.StoreGroceryRepository;
import network.foodsharing.store.repository.StoreMembershipRepository;
import network.foodsharing.store.repository.StoreRepository;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores (plan §12 S1, S2, S15): create (creator = member + manager), update with region move guard, cooperation /
 * team status milestones, and lookups. Rights come from {@link StorePermissionService}.
 */
@Service
public class StoreService {

    /** Create / update input; on update null means unchanged. */
    public record StoreInput(String name, String street, String postcode, String city, String country, Double latitude,
                             Double longitude, UUID categoryId, UUID chainId, List<UUID> groceryIds,
                             CooperationStatus cooperationStatus, LocalDate cooperationStart,
                             ConvinceStatus convinceStatus, String contactPerson, String contactPhone,
                             String contactFax, String contactEmail, PublicityStatus publicity, StickerStatus sticker,
                             String publicInfo, PublicPickupTime publicPickupTime, String weightCategory,
                             Integer prefetchDays, Boolean useRegionPickupRule, Boolean requiresHygiene,
                             Boolean requiresVerified, Boolean requiresPhone, Boolean requiresApplicationText,
                             TeamSearchStatus teamStatus, String specialNotes, String description, UUID regionId,
                             String firstWallPost, String reason) {
    }

    /** After commit: wall milestone / first post (B6) and the team-reopened bell. */
    public record StoreChanged(UUID storeId, WallMilestone milestone, String text, UUID authorId) {
    }

    public record TeamReopened(UUID storeId, UUID regionId) {
    }

    private final StoreRepository stores;
    private final StoreMembershipRepository memberships;
    private final StoreCategoryRepository categories;
    private final StoreChainRepository chains;
    private final GroceryRepository groceries;
    private final StoreGroceryRepository storeGroceries;
    private final PickupWeightCategoryRepository weights;
    private final StorePermissionService rights;
    private final RegionService regions;
    private final RegionPermissionResolver resolver;
    private final UserService users;
    private final CurrentActorService currentActor;
    private final HistoryService history;
    private final ApplicationEventPublisher events;
    private final AppProperties properties;
    private final Clock clock;
    private final org.springframework.beans.factory.ObjectProvider<StorePickupInfo> pickupInfo;
    private final org.springframework.beans.factory.ObjectProvider<StoreWallService> wall;

    public StoreService(StoreRepository stores, StoreMembershipRepository memberships,
                        StoreCategoryRepository categories, StoreChainRepository chains, GroceryRepository groceries,
                        StoreGroceryRepository storeGroceries, PickupWeightCategoryRepository weights,
                        StorePermissionService rights, RegionService regions, RegionPermissionResolver resolver,
                        UserService users, CurrentActorService currentActor, HistoryService history,
                        ApplicationEventPublisher events, AppProperties properties, Clock clock,
                        org.springframework.beans.factory.ObjectProvider<StorePickupInfo> pickupInfo,
                        org.springframework.beans.factory.ObjectProvider<StoreWallService> wall) {
        this.stores = stores;
        this.memberships = memberships;
        this.categories = categories;
        this.chains = chains;
        this.groceries = groceries;
        this.storeGroceries = storeGroceries;
        this.weights = weights;
        this.rights = rights;
        this.regions = regions;
        this.resolver = resolver;
        this.users = users;
        this.currentActor = currentActor;
        this.history = history;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
        this.pickupInfo = pickupInfo;
        this.wall = wall;
    }

    /** @throws ApiException 404 STORE_NOT_FOUND, 404 STORE_DELETED for deleted stores */
    @Transactional(readOnly = true)
    public Store require(UUID storeId) {
        Store store = stores.findById(storeId).orElseThrow(() -> ApiException.notFound(ApiErrorCode.STORE_NOT_FOUND));
        if (store.getDeletedAt() != null) {
            throw ApiException.notFound(ApiErrorCode.STORE_DELETED);
        }
        return store;
    }

    /** The store and the actor's rights; 403 unless mayAccessStore. */
    @Transactional(readOnly = true)
    public StorePermissionService.Rights requireAccess(Store store) {
        StorePermissionService.Rights r = rights.rights(currentActor.requirePrincipal(), store);
        StorePermissionService.require(r.mayAccess());
        return r;
    }

    @Transactional(readOnly = true)
    public List<UUID> groceryIds(UUID storeId) {
        return storeGroceries.findByStoreId(storeId).stream().map(StoreGrocery::getGroceryId).toList();
    }

    @Transactional(readOnly = true)
    public List<User> managers(UUID storeId) {
        return users.findAll(memberships.findByStoreId(storeId).stream()
                .filter(m -> m.isManager() && m.getStatus() == StoreMembershipStatus.MEMBER)
                .map(StoreMembership::getUserId).toList()).stream()
                .sorted(Comparator.comparing(User::getDisplayName, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    @Transactional(readOnly = true)
    public Map<StoreMembershipStatus, Long> teamCounts(UUID storeId) {
        return memberships.findByStoreId(storeId).stream()
                .collect(Collectors.groupingBy(StoreMembership::getStatus, Collectors.counting()));
    }

    /**
     * @throws ApiException 404 REGION_NOT_FOUND / CATEGORY_NOT_FOUND / CHAIN_NOT_FOUND, 403, 400 VALIDATION_FAILED
     */
    @Transactional
    public Store create(UUID regionId, StoreInput input) {
        Region region = regions.require(regionId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        StorePermissionService.require(rights.mayCreateStore(actor, region.getId()));
        if (input.name() == null || input.name().isBlank() || input.cooperationStatus() == null) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        Store store = new Store();
        store.setRegionId(region.getId());
        store.setPrefetchDays(properties.getStore().getDefaultPrefetchDays());
        apply(store, input);
        store.setStatusDate(LocalDate.now(clock));
        stores.save(store);
        setGroceries(store, input.groceryIds());
        StoreMembership creator = new StoreMembership();
        creator.setStoreId(store.getId());
        creator.setUserId(actor.userId());
        creator.setStatus(StoreMembershipStatus.MEMBER);
        creator.setManager(true);
        creator.setAddedAt(clock.instant());
        memberships.save(creator);
        history.record(history.event(StoreLogAction.STORE_CREATED, store.getId()).region(regionId)
                .metadata("name", store.getName()).build());
        events.publishEvent(new StoreChanged(store.getId(), WallMilestone.STORE_CREATED, null, actor.userId()));
        if (input.firstWallPost() != null && !input.firstWallPost().isBlank()) {
            events.publishEvent(new StoreChanged(store.getId(), WallMilestone.NONE, input.firstWallPost().strip(),
                    actor.userId()));
        }
        return store;
    }

    /**
     * Changes the given fields; region move needs all team members to be members of the target region.
     *
     * @throws ApiException 404, 403, 409 TEAM_MEMBERS_NOT_IN_TARGET_REGION (args.names)
     */
    @Transactional
    public Store update(UUID storeId, StoreInput input) {
        Store store = require(storeId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        StorePermissionService.require(rights.rights(actor, store).mayEditStore());
        CooperationStatus oldStatus = store.getCooperationStatus();
        TeamSearchStatus oldTeam = store.getTeamStatus();
        boolean oldRule = store.isUseRegionPickupRule();
        int oldPrefetch = store.getPrefetchDays();
        LocalDate oldStart = store.getCooperationStart();
        if (input.regionId() != null && !input.regionId().equals(store.getRegionId())) {
            move(store, input.regionId());
        }
        apply(store, input);
        if (input.groceryIds() != null) {
            setGroceries(store, input.groceryIds());
        }
        String reason = input.reason() == null || input.reason().isBlank() ? null : input.reason().strip();
        history.record(history.event(StoreLogAction.STORE_UPDATED, store.getId()).region(store.getRegionId())
                .reason(reason).build());
        if (oldStatus != store.getCooperationStatus()) {
            store.setStatusDate(LocalDate.now(clock));
            history.record(history.event(StoreLogAction.COOPERATION_STATUS_CHANGED, store.getId())
                    .region(store.getRegionId()).change("cooperationStatus", oldStatus, store.getCooperationStatus())
                    .build());
            events.publishEvent(new StoreChanged(store.getId(),
                    store.getCooperationStatus() == CooperationStatus.COOPERATION_ESTABLISHED
                            ? WallMilestone.COOPERATION_STARTED : WallMilestone.STATUS_CHANGED,
                    store.getCooperationStatus().name(), actor.userId()));
        } else if (!Objects.equals(oldStart, store.getCooperationStart()) && store.getCooperationStart() != null) {
            events.publishEvent(new StoreChanged(store.getId(), WallMilestone.COOPERATION_STARTED,
                    store.getCooperationStart().toString(), actor.userId()));
        }
        if (oldTeam != store.getTeamStatus()) {
            history.record(history.event(StoreLogAction.TEAM_STATUS_CHANGED, store.getId()).region(store.getRegionId())
                    .change("teamStatus", oldTeam, store.getTeamStatus()).build());
            events.publishEvent(new StoreChanged(store.getId(), WallMilestone.TEAM_STATUS_CHANGED,
                    store.getTeamStatus().name(), actor.userId()));
            if (oldTeam == TeamSearchStatus.CLOSED) {
                events.publishEvent(new TeamReopened(store.getId(), store.getRegionId()));
            }
        }
        if (oldRule != store.isUseRegionPickupRule()) {
            history.record(history.event(store.isUseRegionPickupRule() ? StoreLogAction.REGION_PICKUP_RULE_ENABLED
                    : StoreLogAction.REGION_PICKUP_RULE_DISABLED, store.getId()).region(store.getRegionId()).build());
        }
        if (oldPrefetch != store.getPrefetchDays()) {
            history.record(history.event(StoreLogAction.UPDATE_REGULAR_PICKUP_INTERVAL, store.getId())
                    .region(store.getRegionId()).change("prefetchDays", oldPrefetch, store.getPrefetchDays()).build());
        }
        return store;
    }

    /**
     * Soft-deletes (archives) a store that never had a pickup sign-up: memberships stay for the history, wall posts
     * are soft-deleted, team / jumpers / applicants get a {@code STORE_DELETED} bell after commit.
     *
     * @throws ApiException 404, 403 unless mayDeleteStore, 409 STORE_HAS_PICKUPS
     */
    @Transactional
    public void delete(UUID storeId, String reason) {
        Store store = require(storeId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        StorePermissionService.require(rights.rights(actor, store).mayDelete());
        if (pickupInfo.getObject().hasSignups(storeId)) {
            throw ApiException.conflict(ApiErrorCode.STORE_HAS_PICKUPS);
        }
        store.setDeletedAt(clock.instant());
        wall.getObject().deleteAllOf(storeId, actor.userId());
        String why = reason == null || reason.isBlank() ? null : reason.strip();
        history.record(history.event(StoreLogAction.DELETE_STORE, store.getId()).region(store.getRegionId())
                .reason(why).metadata("name", store.getName()).build());
        List<UUID> recipients = memberships.findByStoreId(storeId).stream()
                .filter(m -> m.getStatus() != StoreMembershipStatus.INVITED)
                .map(StoreMembership::getUserId).filter(id -> !id.equals(actor.userId())).toList();
        StoreNotifications.send(events, network.foodsharing.notification.service.NotificationRequest
                .to(recipients, network.foodsharing.notification.domain.NotificationType.STORE_DELETED)
                .param("storeId", store.getId().toString()).param("storeName", store.getName())
                .param("message", why).link("/stores").icon("store").build());
    }

    private void move(Store store, UUID targetRegionId) {
        Region target = regions.require(targetRegionId);
        List<String> outside = new ArrayList<>();
        Set<UUID> team = memberships.findByStoreId(store.getId()).stream()
                .filter(m -> m.getStatus() == StoreMembershipStatus.MEMBER
                        || m.getStatus() == StoreMembershipStatus.JUMPER)
                .map(StoreMembership::getUserId).collect(Collectors.toSet());
        for (User user : users.findAll(team)) {
            if (!resolver.isMember(user.getId(), target.getId())) {
                outside.add(user.getDisplayName());
            }
        }
        if (!outside.isEmpty()) {
            outside.sort(String.CASE_INSENSITIVE_ORDER);
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT,
                    ApiErrorCode.TEAM_MEMBERS_NOT_IN_TARGET_REGION, List.of(), Map.of("names", String.join(", ", outside)));
        }
        UUID previous = store.getRegionId();
        store.setRegionId(target.getId());
        history.record(history.event(StoreLogAction.MOVED_TO_REGION, store.getId()).region(target.getId())
                .change("regionId", previous, target.getId()).build());
    }

    private void apply(Store store, StoreInput in) {
        if (in.name() != null) {
            if (in.name().isBlank()) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
            }
            store.setName(in.name().strip());
        }
        if (in.street() != null) {
            store.setStreet(blank(in.street()));
        }
        if (in.postcode() != null) {
            store.setPostcode(blank(in.postcode()));
        }
        if (in.city() != null) {
            store.setCity(blank(in.city()));
        }
        if (in.country() != null) {
            store.setCountry(blank(in.country()));
        }
        if (in.latitude() != null) {
            store.setLatitude(in.latitude());
        }
        if (in.longitude() != null) {
            store.setLongitude(in.longitude());
        }
        if (in.categoryId() != null) {
            categories.findById(in.categoryId()).orElseThrow(() -> ApiException.notFound(ApiErrorCode.CATEGORY_NOT_FOUND));
            store.setCategoryId(in.categoryId());
        }
        if (in.chainId() != null) {
            chains.findById(in.chainId()).orElseThrow(() -> ApiException.notFound(ApiErrorCode.CHAIN_NOT_FOUND));
            store.setChainId(in.chainId());
        }
        if (in.cooperationStatus() != null) {
            store.setCooperationStatus(in.cooperationStatus());
        }
        if (in.cooperationStart() != null) {
            store.setCooperationStart(in.cooperationStart());
        }
        if (in.convinceStatus() != null) {
            store.setConvinceStatus(in.convinceStatus());
        }
        if (in.contactPerson() != null) {
            store.setContactPerson(blank(in.contactPerson()));
        }
        if (in.contactPhone() != null) {
            store.setContactPhone(blank(in.contactPhone()));
        }
        if (in.contactFax() != null) {
            store.setContactFax(blank(in.contactFax()));
        }
        if (in.contactEmail() != null) {
            store.setContactEmail(blank(in.contactEmail()));
        }
        if (in.publicity() != null) {
            store.setPublicity(in.publicity());
        }
        if (in.sticker() != null) {
            store.setSticker(in.sticker());
        }
        if (in.publicInfo() != null) {
            store.setPublicInfo(blank(in.publicInfo()));
        }
        if (in.publicPickupTime() != null) {
            store.setPublicPickupTime(in.publicPickupTime());
        }
        if (in.weightCategory() != null) {
            weights.findByCode(in.weightCategory()).orElseThrow(() -> ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED));
            store.setWeightCategory(in.weightCategory());
        }
        if (in.prefetchDays() != null) {
            if (in.prefetchDays() < 1 || in.prefetchDays() > 42) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
            }
            store.setPrefetchDays(in.prefetchDays());
        }
        if (in.useRegionPickupRule() != null) {
            store.setUseRegionPickupRule(in.useRegionPickupRule());
        }
        if (in.requiresHygiene() != null) {
            store.setRequiresHygiene(in.requiresHygiene());
        }
        if (in.requiresVerified() != null) {
            store.setRequiresVerified(in.requiresVerified());
        }
        if (in.requiresPhone() != null) {
            store.setRequiresPhone(in.requiresPhone());
        }
        if (in.requiresApplicationText() != null) {
            store.setRequiresApplicationText(in.requiresApplicationText());
        }
        if (in.teamStatus() != null) {
            store.setTeamStatus(in.teamStatus());
        }
        if (in.specialNotes() != null) {
            store.setSpecialNotes(blank(in.specialNotes()));
        }
        if (in.description() != null) {
            store.setDescription(blank(in.description()));
        }
    }

    private void setGroceries(Store store, List<UUID> ids) {
        if (ids == null) {
            return;
        }
        Set<UUID> wanted = Set.copyOf(ids);
        for (UUID id : wanted) {
            groceries.findById(id).orElseThrow(() -> ApiException.notFound(ApiErrorCode.GROCERY_NOT_FOUND));
        }
        List<StoreGrocery> current = storeGroceries.findByStoreId(store.getId());
        current.stream().filter(g -> !wanted.contains(g.getGroceryId())).forEach(storeGroceries::delete);
        Set<UUID> have = current.stream().map(StoreGrocery::getGroceryId).collect(Collectors.toSet());
        for (UUID id : wanted) {
            if (!have.contains(id)) {
                StoreGrocery link = new StoreGrocery();
                link.setStoreId(store.getId());
                link.setGroceryId(id);
                storeGroceries.save(link);
            }
        }
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** Does the store exist (intro pickup validation)? */
    @Transactional(readOnly = true)
    public boolean exists(UUID storeId) {
        return storeId != null && stores.findLive(storeId).isPresent();
    }

    /**
     * Locks the store row for the rest of the caller's transaction (pickup sign-ups are serialised per store).
     *
     * @throws ApiException 404 STORE_NOT_FOUND / STORE_DELETED
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    @network.foodsharing.history.NoHistory(reason = "takes a row lock only; the caller records history")
    public Store lock(UUID storeId) {
        Store store = stores.lockById(storeId).orElseThrow(() -> ApiException.notFound(ApiErrorCode.STORE_NOT_FOUND));
        if (store.getDeletedAt() != null) {
            throw ApiException.notFound(ApiErrorCode.STORE_DELETED);
        }
        return store;
    }

    /** Live stores of the region that use the region pickup rule. */
    @Transactional(readOnly = true)
    public List<UUID> pickupRuleStoreIds(UUID regionId) {
        return stores.findPickupRuleStoreIds(regionId);
    }

    /** Managers of the store (bell recipients). */
    @Transactional(readOnly = true)
    public List<UUID> managerIds(UUID storeId) {
        return memberships.findByStoreId(storeId).stream()
                .filter(m -> m.isManager() && m.getStatus() == StoreMembershipStatus.MEMBER)
                .map(StoreMembership::getUserId).toList();
    }

    /** Regular team members incl. managers (bell recipients). */
    @Transactional(readOnly = true)
    public List<UUID> teamIds(UUID storeId) {
        return memberships.findByStoreId(storeId).stream().filter(m -> m.getStatus() == StoreMembershipStatus.MEMBER)
                .map(StoreMembership::getUserId).toList();
    }

    /** Live stores with these ids (pickup slot info). */
    @Transactional(readOnly = true)
    public List<Store> liveStores(java.util.Collection<UUID> storeIds) {
        return storeIds.isEmpty() ? List.of() : stores.findLiveIn(storeIds);
    }

    /** Live stores in which the user is a regular team member (pickup options, B5). */
    @Transactional(readOnly = true)
    public List<Store> teamStoresOf(UUID userId) {
        List<UUID> ids = memberships.findByUserId(userId).stream()
                .filter(m -> m.getStatus() == StoreMembershipStatus.MEMBER).map(StoreMembership::getStoreId).toList();
        return ids.isEmpty() ? List.of() : stores.findLiveIn(ids);
    }

    /** Live stores of the given regions (region statistics, B5). */
    @Transactional(readOnly = true)
    public List<Store> liveStoresInRegions(java.util.Collection<UUID> regionIds) {
        return regionIds.isEmpty() ? List.of() : stores.findLiveInRegions(regionIds);
    }

    /** Stores by id including deleted ones (history lists). */
    @Transactional(readOnly = true)
    public List<Store> storesById(java.util.Collection<UUID> storeIds) {
        return storeIds.isEmpty() ? List.of() : stores.findAllById(storeIds);
    }

    /** Average kg per pickup of a weight category code (0 when unknown). */
    @Transactional(readOnly = true)
    public double averageKg(String weightCategory) {
        return weightCategory == null ? 0 : weights.findByCode(weightCategory)
                .map(w -> w.getAverageKg() == null ? 0.0 : w.getAverageKg().doubleValue()).orElse(0.0);
    }
}
