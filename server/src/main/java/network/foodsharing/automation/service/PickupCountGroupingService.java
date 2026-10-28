package network.foodsharing.automation.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.pickup.domain.PickupSignup;
import network.foodsharing.pickup.service.PickupAutomationQueries;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.Permission;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StoreService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Future pickup sign-ups of a region's users grouped by their count (reference "my pickups" grouping, AU7). */
@Service
public class PickupCountGroupingService {

    static final int DEFAULT_DAYS = 7;
    static final int MAX_DAYS = 60;

    public record UserCount(User user, List<Store> stores) {
    }

    public record Group(int pickupCount, List<UserCount> users) {
    }

    private final PickupAutomationQueries pickups;
    private final StoreService stores;
    private final RegionService regions;
    private final UserService users;
    private final PermissionService permissions;
    private final CurrentActorService currentActor;
    private final Clock clock;

    public PickupCountGroupingService(PickupAutomationQueries pickups, StoreService stores, RegionService regions,
                                      UserService users, PermissionService permissions,
                                      CurrentActorService currentActor, Clock clock) {
        this.pickups = pickups;
        this.stores = stores;
        this.regions = regions;
        this.users = users;
        this.permissions = permissions;
        this.currentActor = currentActor;
        this.clock = clock;
    }

    /**
     * @throws ApiException 400 VALIDATION_FAILED (days 1..60), 403 without CAN_SEE_USER_PICKUP_COUNT_GROUPING (or
     *                      region admin), 404 REGION_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public List<Group> groups(UUID regionId, Integer days) {
        regions.require(regionId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        if (!permissions.has(actor, Permission.CAN_SEE_USER_PICKUP_COUNT_GROUPING, regionId)
                && !permissions.mayAdministrateRegion(actor, regionId)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        int window = days == null ? DEFAULT_DAYS : days;
        if (window < 1 || window > MAX_DAYS) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "days");
        }
        Map<UUID, Store> storeById = stores.liveStoresInRegions(regions.subtreeIds(regionId)).stream()
                .collect(Collectors.toMap(Store::getId, Function.identity()));
        Instant now = clock.instant();
        List<PickupSignup> signups = pickups.signups(storeById.keySet(), now, now.plus(Duration.ofDays(window)));
        return group(signups, storeById, ids -> users.findAll(ids));
    }

    static List<Group> group(List<PickupSignup> signups, Map<UUID, Store> storeById,
                             Function<List<UUID>, List<User>> loadUsers) {
        Map<UUID, List<PickupSignup>> byUser = signups.stream().collect(Collectors.groupingBy(PickupSignup::getUserId,
                LinkedHashMap::new, Collectors.toList()));
        Map<UUID, User> userById = loadUsers.apply(new ArrayList<>(byUser.keySet())).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        TreeMap<Integer, List<UserCount>> groups = new TreeMap<>(Comparator.reverseOrder());
        byUser.forEach((userId, list) -> {
            User user = userById.get(userId);
            if (user == null) {
                return;
            }
            Set<UUID> storeIds = new LinkedHashSet<>();
            list.stream().sorted(Comparator.comparing(PickupSignup::getPickupAt))
                    .forEach(s -> storeIds.add(s.getStoreId()));
            groups.computeIfAbsent(list.size(), k -> new ArrayList<>()).add(new UserCount(user,
                    storeIds.stream().map(storeById::get).filter(java.util.Objects::nonNull).toList()));
        });
        List<Group> result = new ArrayList<>();
        groups.forEach((count, list) -> {
            list.sort(Comparator.comparing(u -> u.user().getDisplayName(), String.CASE_INSENSITIVE_ORDER));
            result.add(new Group(count, list));
        });
        return result;
    }
}
