package network.foodsharing.basket.web;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.BasketsApi;
import network.foodsharing.api.model.Basket;
import network.foodsharing.api.model.BasketCreate;
import network.foodsharing.api.model.BasketRequest;
import network.foodsharing.api.model.BasketRequestAnswer;
import network.foodsharing.api.model.BasketRequestCreate;
import network.foodsharing.api.model.BasketSummary;
import network.foodsharing.api.model.BasketUpdate;
import network.foodsharing.basket.domain.BasketKind;
import network.foodsharing.basket.domain.BasketLocationType;
import network.foodsharing.basket.domain.BasketRequestStatus;
import network.foodsharing.basket.mapper.BasketMapper;
import network.foodsharing.basket.service.BasketQueryService;
import network.foodsharing.basket.service.BasketService;
import network.foodsharing.basket.service.BasketService.BasketInput;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Food baskets (B1, B2). */
@RestController
public class BasketsController implements BasketsApi {

    private final BasketService baskets;
    private final BasketQueryService queries;
    private final BasketMapper mapper;

    public BasketsController(BasketService baskets, BasketQueryService queries, BasketMapper mapper) {
        this.baskets = baskets;
        this.queries = queries;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<BasketSummary>> listNearbyBaskets(Double lat, Double lon, Integer distanceKm) {
        return ResponseEntity.ok(mapper.toSummaries(queries.nearby(lat, lon, distanceKm)));
    }

    @Override
    public ResponseEntity<List<BasketSummary>> listMyBaskets() {
        return ResponseEntity.ok(mapper.toSummaries(queries.mine()));
    }

    @Override
    public ResponseEntity<List<BasketSummary>> listRequestedBaskets() {
        return ResponseEntity.ok(mapper.toSummaries(queries.requested()));
    }

    @Override
    public ResponseEntity<List<BasketSummary>> listUserBaskets(UUID userId) {
        return ResponseEntity.ok(mapper.toSummaries(queries.ofUser(userId)));
    }

    @Override
    public ResponseEntity<List<network.foodsharing.api.model.Grocery>> listBasketGroceries() {
        return ResponseEntity.ok(queries.groceries().entrySet().stream()
                .map(e -> new network.foodsharing.api.model.Grocery().id(e.getKey()).name(e.getValue())).toList());
    }

    @Override
    public ResponseEntity<Basket> getBasket(UUID basketId) {
        var view = queries.get(basketId); // 404 / feature check first
        if (baskets.markRequestsRead(basketId) > 0) {
            view = queries.get(basketId);
        }
        return ResponseEntity.ok(mapper.toBasket(view));
    }

    @Override
    public ResponseEntity<Basket> createBasket(BasketCreate body) {
        var created = baskets.create(new BasketInput(body.getDescription(), location(body.getLocationType()),
                body.getContactMessage(), body.getContactPhone(), body.getContactMobile(), body.getLifetimeDays(),
                body.getWeightGrams(), body.getGroceryIds(), kinds(body.getKinds()), body.getLat(), body.getLon(),
                body.getPhone(), body.getMobile(), body.getPhotoUploadId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toBasket(queries.get(created.getId())));
    }

    @Override
    public ResponseEntity<Basket> updateBasket(UUID basketId, BasketUpdate body) {
        baskets.update(basketId, new BasketInput(body.getDescription(), location(body.getLocationType()),
                body.getContactMessage(), body.getContactPhone(), body.getContactMobile(), null,
                body.getWeightGrams(), body.getGroceryIds(), kinds(body.getKinds()), body.getLat(), body.getLon(),
                body.getPhone(), body.getMobile(), body.getPhotoUploadId()), Boolean.TRUE.equals(body.getRemovePhoto()));
        return ResponseEntity.ok(mapper.toBasket(queries.get(basketId)));
    }

    @Override
    public ResponseEntity<Void> deleteBasket(UUID basketId) {
        baskets.delete(basketId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<BasketRequest> requestBasket(UUID basketId, BasketRequestCreate body) {
        var request = baskets.request(basketId, body == null ? null : body.getMessage());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toRequest(request));
    }

    @Override
    public ResponseEntity<Void> withdrawBasketRequest(UUID basketId) {
        baskets.withdraw(basketId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<BasketRequest> answerBasketRequest(UUID basketId, UUID requesterId,
                                                             BasketRequestAnswer body) {
        var request = baskets.answer(basketId, requesterId,
                BasketRequestStatus.valueOf(body.getStatus().getValue()));
        return ResponseEntity.ok(mapper.toRequest(request));
    }

    private static BasketLocationType location(network.foodsharing.api.model.BasketLocationType type) {
        return type == null ? null : BasketLocationType.valueOf(type.name());
    }

    private static List<BasketKind> kinds(List<network.foodsharing.api.model.BasketKind> values) {
        return values == null ? null : values.stream().map(k -> BasketKind.valueOf(k.name())).toList();
    }
}
