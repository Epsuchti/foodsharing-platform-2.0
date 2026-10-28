package network.foodsharing.map.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import network.foodsharing.api.model.Role;
import network.foodsharing.basket.service.BasketQueryService;
import network.foodsharing.event.service.EventService;
import network.foodsharing.fsp.service.FspQueryService;
import network.foodsharing.map.domain.MapMarkerKind;
import network.foodsharing.map.domain.StoreMarkerHelp;
import network.foodsharing.map.domain.StoreMarkerScope;
import network.foodsharing.map.domain.StoreMarkerStatus;
import network.foodsharing.map.domain.UserMarkerActivity;
import network.foodsharing.map.domain.UserMarkerMember;
import network.foodsharing.map.domain.UserMarkerRole;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.CooperationStatus;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.domain.TeamSearchStatus;
import network.foodsharing.store.service.StoreQueryService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Map layers (M1, upstream {@code MapRestController}): stores, FSPs, baskets, regions, events and — for region
 * administrators only — users. Private locations (HOME baskets, users) are rounded to 3 decimals (~100 m).
 */
@Service
@Transactional(readOnly = true)
public class MarkerService {

    /** One marker; {@code status} is kind specific (FSP/basket/event/region: empty, pins: pin status). */
    public record Marker(UUID id, MapMarkerKind kind, double lat, double lon, String status) {
    }

    /** A store marker with its filterable fields. */
    public record StoreMarker(UUID id, double lat, double lon, String name, CooperationStatus cooperationStatus,
                              TeamSearchStatus teamStatus, UUID categoryId, UUID regionId) {
    }

    private final StoreQueryService stores;
    private final FspQueryService fsps;
    private final BasketQueryService baskets;
    private final EventService events;
    private final RegionService regions;
    private final UserService users;
    private final CurrentActorService actors;
    private final PermissionService permissions;
    private final Clock clock;

    public MarkerService(StoreQueryService stores, FspQueryService fsps, BasketQueryService baskets,
                         EventService events, RegionService regions, UserService users, CurrentActorService actors,
                         PermissionService permissions, Clock clock) {
        this.stores = stores;
        this.fsps = fsps;
        this.baskets = baskets;
        this.events = events;
        this.regions = regions;
        this.users = users;
        this.actors = actors;
        this.permissions = permissions;
        this.clock = clock;
    }

    /** Rounds a private coordinate to 3 decimals (~100 m). */
    public static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }

    /** Store filter (upstream {@code StoreMarkerStatusType}): COOPERATING = established cooperation. */
    public static boolean matches(StoreMarker store, StoreMarkerStatus status, StoreMarkerHelp help) {
        boolean cooperating = store.cooperationStatus() == CooperationStatus.COOPERATION_ESTABLISHED;
        if (status == StoreMarkerStatus.COOPERATING && !cooperating
                || status == StoreMarkerStatus.NOT_COOPERATING && cooperating) {
            return false;
        }
        return help == null || help == StoreMarkerHelp.ALL
                || help == StoreMarkerHelp.OPEN && store.teamStatus() != TeamSearchStatus.CLOSED
                || help == StoreMarkerHelp.SEARCHING && store.teamStatus() == TeamSearchStatus.OPEN_SEARCHING;
    }

    /** Signed-in users see every live store with coordinates, filtered. */
    public List<StoreMarker> stores(StoreMarkerStatus status, StoreMarkerHelp help, StoreMarkerScope scope,
                                    UUID categoryId) {
        SessionPrincipal actor = actors.requirePrincipal();
        Set<UUID> mine = scope == StoreMarkerScope.MEMBER ? stores.teamStoreIds(actor.userId()) : Set.of();
        Set<UUID> myRegions = scope == StoreMarkerScope.REGION ? regions.activeRegionIdsOf(actor.userId()) : Set.of();
        return stores.mapStores().stream().map(MarkerService::toMarker)
                .filter(m -> matches(m, status, help))
                .filter(m -> categoryId == null || categoryId.equals(m.categoryId()))
                .filter(m -> scope != StoreMarkerScope.MEMBER || mine.contains(m.id()))
                .filter(m -> scope != StoreMarkerScope.REGION || myRegions.contains(m.regionId()))
                .toList();
    }

    public List<Marker> fsps() {
        return fsps.activeWithCoordinates().stream()
                .map(f -> new Marker(f.getId(), MapMarkerKind.FSP, f.getLat(), f.getLon(), null)).toList();
    }

    public List<Marker> baskets() {
        return baskets.openForMap().stream()
                .map(v -> new Marker(v.basket().getId(), MapMarkerKind.BASKET, v.lat(), v.lon(), null)).toList();
    }

    /** Active region pins and regions with coordinates. */
    public List<Marker> regions() {
        return Stream.concat(regions.activePins().stream().map(p -> new Marker(p.getRegionId(),
                        MapMarkerKind.REGION_PIN, p.getLatitude(), p.getLongitude(), p.getStatus().name())),
                regions.regionsWithCoordinates().stream().map(r -> new Marker(r.getId(), MapMarkerKind.REGION,
                        r.getLatitude(), r.getLongitude(), null))).toList();
    }

    public List<Marker> events(Instant from) {
        return events.listForMap(from).stream().filter(i -> i.location() != null && i.location().getLat() != null
                        && i.location().getLon() != null)
                .map(i -> new Marker(i.event().getId(), MapMarkerKind.EVENT, i.location().getLat(),
                        i.location().getLon(), null)).toList();
    }

    /**
     * Members of a region with coordinates, for its administrators only (rounded positions).
     *
     * @throws network.foodsharing.common.error.ApiException 404 REGION_NOT_FOUND, 403 ACCESS_DENIED
     */
    public List<Marker> users(UUID regionId, UserMarkerRole role, UserMarkerActivity activity,
                              UserMarkerMember member) {
        SessionPrincipal actor = actors.requirePrincipal();
        regions.require(regionId);
        permissions.requireMayAdministrateRegion(actor, regionId);
        Instant since = activitySince(activity);
        return users.findActive(regions.activeMemberIds(regionId)).stream()
                .filter(u -> u.getLatitude() != null && u.getLongitude() != null)
                .filter(u -> matches(u, regionId, role, since, member))
                .map(u -> new Marker(u.getId(), MapMarkerKind.USER, round(u.getLatitude()), round(u.getLongitude()),
                        u.getRole().name())).toList();
    }

    /** User filters: role, last login within the window, home region or other. */
    public static boolean matches(User user, UUID regionId, UserMarkerRole role, Instant since,
                                  UserMarkerMember member) {
        if (role == UserMarkerRole.FOODSAVER && user.getRole() != Role.FOODSAVER
                || role == UserMarkerRole.STORE_MANAGER && !PermissionService.isAtLeast(user.getRole(),
                Role.STORE_MANAGER)) {
            return false;
        }
        if (since != null && (user.getLastLoginAt() == null || user.getLastLoginAt().isBefore(since))) {
            return false;
        }
        boolean home = regionId.equals(user.getHomeRegionId());
        return member == null || member == UserMarkerMember.ALL || member == UserMarkerMember.HOMEREGION && home
                || member == UserMarkerMember.OTHER && !home;
    }

    private Instant activitySince(UserMarkerActivity activity) {
        Instant now = clock.instant();
        if (activity == null) {
            return null;
        }
        return switch (activity) {
            case ALL -> null;
            case WEEK -> now.minus(Duration.ofDays(7));
            case MONTH -> now.minus(Duration.ofDays(30));
            case THREE_MONTHS -> now.minus(Duration.ofDays(90));
            case SIX_MONTHS -> now.minus(Duration.ofDays(180));
        };
    }

    private static StoreMarker toMarker(Store s) {
        return new StoreMarker(s.getId(), s.getLatitude(), s.getLongitude(), s.getName(), s.getCooperationStatus(),
                s.getTeamStatus(), s.getCategoryId(), s.getRegionId());
    }
}
