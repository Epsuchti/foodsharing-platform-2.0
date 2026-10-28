package network.foodsharing.store.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.CooperationStatus;
import network.foodsharing.store.domain.Grocery;
import network.foodsharing.store.domain.PickupWeightCategory;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.domain.StoreCategory;
import network.foodsharing.store.domain.StoreChain;
import network.foodsharing.store.domain.StoreMembership;
import network.foodsharing.store.domain.StoreMembershipStatus;
import network.foodsharing.store.domain.TeamSearchStatus;
import network.foodsharing.store.domain.UserTeamStatus;
import network.foodsharing.store.repository.GroceryRepository;
import network.foodsharing.store.repository.PickupWeightCategoryRepository;
import network.foodsharing.store.repository.StoreCategoryRepository;
import network.foodsharing.store.repository.StoreChainRepository;
import network.foodsharing.store.repository.StoreGroceryRepository;
import network.foodsharing.store.repository.StoreMembershipRepository;
import network.foodsharing.store.repository.StoreRepository;
import network.foodsharing.user.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of stores (S1, S2, S16): lists, detail view, metadata. */
@Service
@Transactional(readOnly = true)
public class StoreQueryService {

    public static final Set<CooperationStatus> DEFAULT_STATUSES = Set.of(CooperationStatus.UNCLEAR,
            CooperationStatus.NO_CONTACT, CooperationStatus.IN_NEGOTIATION, CooperationStatus.COOPERATION_ESTABLISHED);

    public record ListItem(Store store, StoreCategory category, StoreChain chain, Region region,
                           UserTeamStatus membership, StorePickupInfo.SlotInfo slots) {
    }

    public record Detail(Store store, StoreCategory category, StoreChain chain, List<Grocery> groceries, Region region,
                         String timeZone, List<User> managers, Map<StoreMembershipStatus, Long> teamCounts,
                         StorePermissionService.Rights rights) {
    }

    public record Metadata(List<StoreCategory> categories, Map<UUID, Long> categoryUsage, List<Grocery> groceries,
                           List<PickupWeightCategory> weights, List<StoreChain> chains) {
    }

    private final StoreRepository stores;
    private final StoreMembershipRepository memberships;
    private final StoreCategoryRepository categories;
    private final StoreChainRepository chains;
    private final GroceryRepository groceries;
    private final StoreGroceryRepository storeGroceries;
    private final PickupWeightCategoryRepository weights;
    private final StoreService storeService;
    private final StorePermissionService rights;
    private final StorePickupInfo pickupInfo;
    private final RegionService regions;
    private final CurrentActorService currentActor;
    private final PermissionService permissions;

    public StoreQueryService(StoreRepository stores, StoreMembershipRepository memberships,
                             StoreCategoryRepository categories, StoreChainRepository chains,
                             GroceryRepository groceries, StoreGroceryRepository storeGroceries,
                             PickupWeightCategoryRepository weights, StoreService storeService,
                             StorePermissionService rights, StorePickupInfo pickupInfo, RegionService regions,
                             CurrentActorService currentActor, PermissionService permissions) {
        this.stores = stores;
        this.memberships = memberships;
        this.categories = categories;
        this.chains = chains;
        this.groceries = groceries;
        this.storeGroceries = storeGroceries;
        this.weights = weights;
        this.storeService = storeService;
        this.rights = rights;
        this.pickupInfo = pickupInfo;
        this.regions = regions;
        this.currentActor = currentActor;
        this.permissions = permissions;
    }

    /** FOODSAVER+. */
    public Metadata metadata() {
        SessionPrincipal actor = currentActor.requirePrincipal();
        StorePermissionService.require(PermissionService.isAtLeast(actor.role(),
                network.foodsharing.api.model.Role.FOODSAVER));
        return new Metadata(categories.findAllByOrderByNameAsc(), Map.of(), groceries.findAllByOrderByNameAsc(),
                weights.findAllByOrderBySortOrderAsc(), chains.findAllByOrderByNameAsc());
    }

    /**
     * Stores of a region (members of the region and store coordinators).
     *
     * @throws ApiException 404 REGION_NOT_FOUND, 403 ACCESS_DENIED
     */
    public Page<ListItem> regionStores(UUID regionId, Collection<CooperationStatus> statuses,
                                       Collection<TeamSearchStatus> teamStatuses, String search,
                                       boolean includeSubregions, Integer page, Integer size) {
        regions.require(regionId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        StorePermissionService.require(permissions.isMemberOf(actor, regionId)
                || rights.mayCoordinateRegionStores(actor, regionId));
        Set<CooperationStatus> wanted = statuses == null || statuses.isEmpty() ? DEFAULT_STATUSES : Set.copyOf(statuses);
        String needle = search == null || search.isBlank() ? null : search.strip().toLowerCase(Locale.ROOT);
        List<UUID> regionIds = includeSubregions ? regions.subtreeIds(regionId) : List.of(regionId);
        List<Store> list = stores.findLiveInRegions(regionIds).stream()
                .filter(s -> wanted.contains(s.getCooperationStatus()))
                .filter(s -> teamStatuses == null || teamStatuses.isEmpty() || teamStatuses.contains(s.getTeamStatus()))
                .filter(s -> needle == null || matches(s, needle)).toList();
        int p = page == null ? 0 : Math.max(0, page);
        int sz = size == null ? 50 : Math.min(Math.max(1, size), 200);
        int from = Math.min(list.size(), p * sz);
        List<ListItem> items = items(list.subList(from, Math.min(list.size(), from + sz)), actor);
        return new PageImpl<>(items, PageRequest.of(p, sz), list.size());
    }

    /**
     * Stores the current user is in (any membership status, incl. applications and invitations); excludeInactive
     * drops stores whose cooperation is permanently closed (deleted stores are never listed).
     */
    public List<ListItem> myStores(boolean excludeInactive) {
        SessionPrincipal actor = currentActor.requirePrincipal();
        List<UUID> ids = memberships.findByUserId(actor.userId()).stream()
                .map(StoreMembership::getStoreId).toList();
        return ids.isEmpty() ? List.of() : items(stores.findLiveIn(ids).stream()
                .filter(s -> !excludeInactive || s.getCooperationStatus() != CooperationStatus.PERMANENTLY_CLOSED)
                .toList(), actor);
    }

    /** Map layer (M1): live stores with coordinates (permanently closed ones are left out). */
    public List<Store> mapStores() {
        return stores.findAllLive().stream().filter(s -> s.getLatitude() != null && s.getLongitude() != null
                && s.getCooperationStatus() != CooperationStatus.PERMANENTLY_CLOSED).toList();
    }

    /** Ids of the stores whose team (member, coordinator or jumper) the user is in. */
    public java.util.Set<UUID> teamStoreIds(UUID userId) {
        return memberships.findByUserId(userId).stream()
                .filter(m -> m.getStatus() == StoreMembershipStatus.MEMBER
                        || m.getStatus() == StoreMembershipStatus.JUMPER)
                .map(StoreMembership::getStoreId).collect(Collectors.toSet());
    }

    /** A live store by id (map bubble). */
    public java.util.Optional<Store> findLive(UUID storeId) {
        return stores.findLiveIn(List.of(storeId)).stream().findFirst();
    }

    /** Stores of a chain (no permission check; the chain service checks). */
    public List<ListItem> chainStores(UUID chainId) {
        return items(stores.findLiveByChain(chainId), currentActor.requirePrincipal());
    }

    public List<ListItem> items(List<Store> list, SessionPrincipal actor) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<UUID, StoreCategory> cats = categories.findAll().stream()
                .collect(Collectors.toMap(StoreCategory::getId, Function.identity()));
        Map<UUID, StoreChain> chainMap = chains.findAll().stream()
                .collect(Collectors.toMap(StoreChain::getId, Function.identity()));
        Map<UUID, Region> regionMap = regions.findAll(list.stream().map(Store::getRegionId).distinct().toList())
                .stream().collect(Collectors.toMap(Region::getId, Function.identity()));
        Map<UUID, StoreMembership> mine = memberships.findByUserId(actor.userId()).stream()
                .collect(Collectors.toMap(StoreMembership::getStoreId, Function.identity()));
        Map<UUID, StorePickupInfo.SlotInfo> slots = pickupInfo.slotInfo(list.stream().map(Store::getId).toList());
        List<ListItem> result = new ArrayList<>();
        for (Store store : list) {
            result.add(new ListItem(store, store.getCategoryId() == null ? null : cats.get(store.getCategoryId()),
                    store.getChainId() == null ? null : chainMap.get(store.getChainId()),
                    regionMap.get(store.getRegionId()),
                    StorePermissionService.status(java.util.Optional.ofNullable(mine.get(store.getId()))),
                    slots.get(store.getId())));
        }
        return result;
    }

    /** @throws ApiException 404 STORE_NOT_FOUND / STORE_DELETED, 403 ACCESS_DENIED */
    public Detail detail(UUID storeId) {
        Store store = storeService.require(storeId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        StorePermissionService.Rights r = rights.rights(actor, store);
        // Non-members of the store's region get the limited public view (sensitive fields hidden by the mapper).
        StorePermissionService.require(r.mayAccess() || rights.mayViewPublic(actor, store));
        return detail(store, r);
    }

    public Detail detail(Store store, StorePermissionService.Rights r) {
        List<UUID> groceryIds = storeService.groceryIds(store.getId());
        return new Detail(store, store.getCategoryId() == null ? null : categories.findById(store.getCategoryId())
                .orElse(null), store.getChainId() == null ? null : chains.findById(store.getChainId()).orElse(null),
                groceryIds.isEmpty() ? List.of() : groceries.findAllById(groceryIds).stream()
                        .sorted(Comparator.comparing(Grocery::getName)).toList(),
                regions.require(store.getRegionId()), regions.zoneOf(store.getRegionId()).getId(),
                storeService.managers(store.getId()), storeService.teamCounts(store.getId()), r);
    }

    /** Rights of the current user (any authenticated user; 404 for unknown stores). */
    public StorePermissionService.Rights permissionsOf(UUID storeId) {
        return rights.rights(currentActor.requirePrincipal(), storeService.require(storeId));
    }

    private static boolean matches(Store s, String needle) {
        return contains(s.getName(), needle) || contains(s.getStreet(), needle) || contains(s.getCity(), needle)
                || contains(s.getPostcode(), needle);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    /** Usage counts for the category admin (B7). */
    public long storeCountOfGrocery(UUID groceryId) {
        return storeGroceries.countByGroceryId(groceryId);
    }
}
