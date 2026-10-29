package network.foodsharing.basket.service;

import network.foodsharing.admin.domain.FeatureToggle;
import network.foodsharing.admin.service.FeatureToggleService;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.basket.domain.Basket;
import network.foodsharing.basket.domain.BasketGrocery;
import network.foodsharing.basket.domain.BasketKind;
import network.foodsharing.basket.domain.BasketKindEntry;
import network.foodsharing.basket.domain.BasketRequestEntry;
import network.foodsharing.basket.domain.BasketStatus;
import network.foodsharing.basket.repository.BasketGroceryRepository;
import network.foodsharing.basket.repository.BasketKindEntryRepository;
import network.foodsharing.basket.repository.BasketRepository;
import network.foodsharing.basket.repository.BasketRequestEntryRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.service.StoreCategoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reading baskets (B1): details, nearby (haversine), own, requested, a user's open baskets. */
@Service
@Transactional(readOnly = true)
public class BasketQueryService {

    public static final int DEFAULT_DISTANCE_KM = 30;
    public static final int MAX_DISTANCE_KM = 50;

    /** A basket as one viewer sees it; {@code lat/lon} are rounded for HOME baskets of others. */
    public record BasketView(Basket basket, double lat, double lon, Double distanceKm, List<Grocery> groceries,
                             List<BasketKind> kinds, List<BasketRequestEntry> requests, BasketRequestEntry myRequest,
                             boolean owner, boolean mayEdit, boolean mayDelete, boolean mayRequest) {
    }

    public record Grocery(UUID id, String name) {
    }

    private final BasketRepository baskets;
    private final BasketGroceryRepository groceries;
    private final BasketKindEntryRepository kinds;
    private final BasketRequestEntryRepository requests;
    private final CurrentActorService actors;
    private final PermissionService permissions;
    private final StoreCategoryService groceryCatalog;
    private final Clock clock;

    private final FeatureToggleService toggles;

    public BasketQueryService(BasketRepository baskets, BasketGroceryRepository groceries,
                              BasketKindEntryRepository kinds, BasketRequestEntryRepository requests,
                              CurrentActorService actors, PermissionService permissions,
                              StoreCategoryService groceryCatalog, Clock clock,
            FeatureToggleService toggles) {
        this.toggles = toggles;
        this.baskets = baskets;
        this.groceries = groceries;
        this.kinds = kinds;
        this.requests = requests;
        this.actors = actors;
        this.permissions = permissions;
        this.groceryCatalog = groceryCatalog;
        this.clock = clock;
    }

    /** Groceries the basket form offers (id → name, by name): every signed-in user, unlike the store metadata. */
    public java.util.Map<UUID, String> groceries() {
        toggles.requireEnabled(FeatureToggle.BASKETS);
        actors.requirePrincipal();
        return groceryCatalog.groceryCatalog();
    }

    /** @throws ApiException 404 BASKET_NOT_FOUND (also DELETED) */
    public BasketView get(UUID basketId) {
        toggles.requireEnabled(FeatureToggle.BASKETS);
        SessionPrincipal actor = actors.requirePrincipal();
        Basket basket = baskets.findById(basketId).filter(b -> b.getStatus() != BasketStatus.DELETED)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.BASKET_NOT_FOUND));
        boolean owner = basket.getOwnerId().equals(actor.userId());
        boolean orga = permissions.isOrga(actor);
        List<BasketGrocery> rows = groceries.findByBasketId(basketId);
        Map<UUID, String> names = groceryCatalog.groceryNames(rows.stream().map(BasketGrocery::getGroceryId).toList());
        List<Grocery> groceryList = rows.stream().filter(row -> names.containsKey(row.getGroceryId()))
                .map(row -> new Grocery(row.getGroceryId(), names.get(row.getGroceryId())))
                .sorted(Comparator.comparing(Grocery::name)).toList();
        List<BasketKind> kindList = kinds.findByBasketId(basketId).stream().map(BasketKindEntry::getKind).sorted()
                .toList();
        List<BasketRequestEntry> all = requests.findByBasketIdOrderByRequestedAt(basketId);
        List<BasketRequestEntry> visible = owner || orga
                ? all.stream().filter(r -> BasketRules.isLive(r.getStatus())).toList() : List.of();
        BasketRequestEntry mine = all.stream().filter(r -> r.getRequesterId().equals(actor.userId())).findFirst()
                .orElse(null);
        Instant now = clock.instant();
        boolean mayRequest = BasketRules.mayRequest(basket, actor.userId(), now)
                && (mine == null || !BasketRules.isLive(mine.getStatus()));
        return new BasketView(basket, BasketRules.publicCoordinate(basket, basket.getLat(), owner),
                BasketRules.publicCoordinate(basket, basket.getLon(), owner), null, groceryList, kindList, visible,
                mine, owner, owner && BasketRules.isOpen(basket, now),
                BasketRules.mayDelete(basket, actor.userId(), orga), mayRequest);
    }

    /** Open baskets within {@code distanceKm} (1–50, default 30), nearest first. */
    public List<BasketView> nearby(double lat, double lon, Integer distanceKm) {
        toggles.requireEnabled(FeatureToggle.BASKETS);
        UUID me = actors.requireUserId();
        if (!BasketRules.validCoordinates(lat, lon)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "lat");
        }
        int radius = distanceKm == null ? DEFAULT_DISTANCE_KM : Math.max(1, Math.min(MAX_DISTANCE_KM, distanceKm));
        return baskets.findByStatusAndExpiresAtAfter(BasketStatus.ACTIVE, clock.instant()).stream()
                .map(basket -> summary(basket, me,
                        BasketRules.distanceKm(lat, lon, basket.getLat(), basket.getLon())))
                .filter(view -> view.distanceKm() <= radius)
                .sorted(Comparator.comparingDouble(BasketView::distanceKm)).toList();
    }

    /** The current user's baskets (not DELETED), newest first. */
    public List<BasketView> mine() {
        toggles.requireEnabled(FeatureToggle.BASKETS);
        UUID me = actors.requireUserId();
        return baskets.findByOwnerIdAndStatusNotOrderByCreatedAtDesc(me, BasketStatus.DELETED).stream()
                .map(basket -> summary(basket, me, null)).toList();
    }

    /** Baskets the current user has a live request for, newest request first. */
    public List<BasketView> requested() {
        toggles.requireEnabled(FeatureToggle.BASKETS);
        UUID me = actors.requireUserId();
        return requests.findByRequesterIdOrderByRequestedAtDesc(me).stream()
                .filter(r -> BasketRules.isLive(r.getStatus()))
                .map(r -> baskets.findById(r.getBasketId()).orElse(null))
                .filter(b -> b != null && b.getStatus() != BasketStatus.DELETED)
                .map(basket -> summary(basket, me, null)).toList();
    }

    /** Open baskets of a user (profile). */
    public List<BasketView> ofUser(UUID userId) {
        toggles.requireEnabled(FeatureToggle.BASKETS);
        UUID me = actors.requireUserId();
        Instant now = clock.instant();
        return baskets.findByOwnerIdAndStatusOrderByCreatedAtDesc(userId, BasketStatus.ACTIVE).stream()
                .filter(basket -> basket.getExpiresAt().isAfter(now))
                .map(basket -> summary(basket, me, null)).toList();
    }

    /**
     * Map layer (M1): every open basket; HOME locations are rounded (~100 m) for everybody, also anonymous viewers.
     */
    public List<BasketView> openForMap() {
        Instant now = clock.instant();
        return baskets.findByStatusAndExpiresAtAfter(BasketStatus.ACTIVE, now).stream()
                .map(basket -> summary(basket, null, null)).toList();
    }

    /** An open basket for a map bubble (rounded like {@link #openForMap()}). */
    public java.util.Optional<BasketView> openForMap(UUID basketId) {
        Instant now = clock.instant();
        return baskets.findById(basketId).filter(b -> BasketRules.isOpen(b, now)).map(b -> summary(b, null, null));
    }

    private BasketView summary(Basket basket, UUID me, Double distanceKm) {
        boolean owner = me != null && basket.getOwnerId().equals(me);
        return new BasketView(basket, BasketRules.publicCoordinate(basket, basket.getLat(), owner),
                BasketRules.publicCoordinate(basket, basket.getLon(), owner),
                distanceKm == null ? null : Math.round(distanceKm * 10) / 10.0, List.of(), List.of(), List.of(), null,
                owner, false, false, false);
    }
}
