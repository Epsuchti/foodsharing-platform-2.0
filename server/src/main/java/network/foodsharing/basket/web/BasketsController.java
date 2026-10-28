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
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Contract stub (docs/plan-m6.md B1): every operation answers 501 until implemented. */
@RestController
public class BasketsController implements BasketsApi {

    @Override
    public ResponseEntity<BasketRequest> answerBasketRequest(UUID basketId, UUID requesterId, BasketRequestAnswer basketRequestAnswer) {
        throw ApiException.notImplemented("answerBasketRequest");
    }

    @Override
    public ResponseEntity<Basket> createBasket(BasketCreate basketCreate) {
        throw ApiException.notImplemented("createBasket");
    }

    @Override
    public ResponseEntity<Void> deleteBasket(UUID basketId) {
        throw ApiException.notImplemented("deleteBasket");
    }

    @Override
    public ResponseEntity<Basket> getBasket(UUID basketId) {
        throw ApiException.notImplemented("getBasket");
    }

    @Override
    public ResponseEntity<List<BasketSummary>> listMyBaskets() {
        throw ApiException.notImplemented("listMyBaskets");
    }

    @Override
    public ResponseEntity<List<BasketSummary>> listNearbyBaskets(Double lat, Double lon, Integer distanceKm) {
        throw ApiException.notImplemented("listNearbyBaskets");
    }

    @Override
    public ResponseEntity<List<BasketSummary>> listRequestedBaskets() {
        throw ApiException.notImplemented("listRequestedBaskets");
    }

    @Override
    public ResponseEntity<List<BasketSummary>> listUserBaskets(UUID userId) {
        throw ApiException.notImplemented("listUserBaskets");
    }

    @Override
    public ResponseEntity<BasketRequest> requestBasket(UUID basketId, BasketRequestCreate basketRequestCreate) {
        throw ApiException.notImplemented("requestBasket");
    }

    @Override
    public ResponseEntity<Basket> updateBasket(UUID basketId, BasketUpdate basketUpdate) {
        throw ApiException.notImplemented("updateBasket");
    }

    @Override
    public ResponseEntity<Void> withdrawBasketRequest(UUID basketId) {
        throw ApiException.notImplemented("withdrawBasketRequest");
    }
}
