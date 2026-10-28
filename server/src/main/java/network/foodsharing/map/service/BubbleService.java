package network.foodsharing.map.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.basket.service.BasketQueryService;
import network.foodsharing.basket.service.BasketQueryService.BasketView;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.event.service.EventService;
import network.foodsharing.fsp.domain.FoodSharePoint;
import network.foodsharing.fsp.service.FspQueryService;
import network.foodsharing.map.domain.MapMarkerKind;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StoreQueryService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marker bubbles (M1). Lines carry data only (addresses, names, counts, ISO dates); the client renders labels.
 * Anonymous viewers get FSP, basket and region bubbles only.
 */
@Service
@Transactional(readOnly = true)
public class BubbleService {

    /** A bubble; {@code lines} are plain data lines. */
    public record Bubble(String title, String subtitle, String imageUrl, String link, List<String> lines) {
    }

    static final Set<MapMarkerKind> PUBLIC = Set.of(MapMarkerKind.FSP, MapMarkerKind.BASKET, MapMarkerKind.REGION,
            MapMarkerKind.REGION_PIN);

    private final StoreQueryService stores;
    private final FspQueryService fsps;
    private final BasketQueryService baskets;
    private final EventService events;
    private final RegionService regions;
    private final UserService users;
    private final CurrentActorService actors;
    private final PermissionService permissions;

    public BubbleService(StoreQueryService stores, FspQueryService fsps, BasketQueryService baskets,
                         EventService events, RegionService regions, UserService users, CurrentActorService actors,
                         PermissionService permissions) {
        this.stores = stores;
        this.fsps = fsps;
        this.baskets = baskets;
        this.events = events;
        this.regions = regions;
        this.users = users;
        this.actors = actors;
        this.permissions = permissions;
    }

    /** @throws ApiException 404 NOT_FOUND for unknown kinds / ids, 403 for user bubbles of non-administrators */
    public Bubble bubble(String kind, UUID id, boolean anonymous) {
        MapMarkerKind type = parse(kind);
        if (anonymous && !PUBLIC.contains(type)) {
            throw notFound();
        }
        return switch (type) {
            case STORE -> store(id);
            case FSP -> fsp(id);
            case BASKET -> basket(id);
            case REGION, REGION_PIN -> region(id);
            case EVENT -> event(id);
            case USER -> user(id);
        };
    }

    private Bubble store(UUID id) {
        actors.requirePrincipal();
        Store store = stores.findLive(id).orElseThrow(BubbleService::notFound);
        return new Bubble(store.getName(), store.getCooperationStatus().name(), null, "/stores/" + id,
                address(store.getStreet(), store.getPostcode(), store.getCity(), store.getTeamStatus().name()));
    }

    private Bubble fsp(UUID id) {
        FoodSharePoint fsp = fsps.findActive(id).orElseThrow(BubbleService::notFound);
        return new Bubble(fsp.getName(), fsp.getCity(), fsp.getPhotoUploadId() == null ? null
                : "/api/uploads/" + fsp.getPhotoUploadId() + "?size=md", "/food-share-points/" + id,
                address(fsp.getStreet(), fsp.getPostcode(), fsp.getCity(), String.valueOf(fsps.followerCount(id))));
    }

    private Bubble basket(UUID id) {
        BasketView view = baskets.openForMap(id).orElseThrow(BubbleService::notFound);
        String owner = users.findActive(view.basket().getOwnerId()).map(User::getDisplayName).orElse(null);
        return new Bubble(view.basket().getDescription(), owner, view.basket().getPhotoUploadId() == null ? null
                : "/api/uploads/" + view.basket().getPhotoUploadId() + "?size=md", "/baskets/" + id,
                List.of(view.basket().getExpiresAt().toString()));
    }

    private Bubble region(UUID id) {
        Region region = regions.find(id).orElseThrow(BubbleService::notFound);
        return new Bubble(region.getName(), region.getType().name(), null, "/regions/" + region.getId(),
                List.of(String.valueOf(regions.activeMemberCount(id))));
    }

    private Bubble event(UUID id) {
        var item = events.item(id);
        var location = item.location();
        List<String> lines = new ArrayList<>();
        lines.add(item.event().getStartsAt().toString());
        if (location != null) {
            lines.addAll(address(location.getStreet(), location.getZip(), location.getCity(), null));
        }
        return new Bubble(item.event().getName(), location == null ? null : location.getName(), null,
                "/events/" + id, lines);
    }

    private Bubble user(UUID id) {
        SessionPrincipal actor = actors.requirePrincipal();
        permissions.requireMayAdministrateUser(actor, id);
        User user = users.findActive(id).orElseThrow(BubbleService::notFound);
        return new Bubble(user.getDisplayName(), user.getRole().name(), null, "/profile/" + id, List.of());
    }

    private static List<String> address(String street, String postcode, String city, String extra) {
        List<String> lines = new ArrayList<>();
        if (street != null) {
            lines.add(street);
        }
        String place = ((postcode == null ? "" : postcode) + " " + (city == null ? "" : city)).strip();
        if (!place.isEmpty()) {
            lines.add(place);
        }
        if (extra != null) {
            lines.add(extra);
        }
        return lines;
    }

    private static MapMarkerKind parse(String kind) {
        try {
            return MapMarkerKind.valueOf(kind.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "kind");
        }
    }

    private static ApiException notFound() {
        return ApiException.notFound(ApiErrorCode.MARKER_NOT_FOUND);
    }
}
