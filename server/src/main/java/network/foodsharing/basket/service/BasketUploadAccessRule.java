package network.foodsharing.basket.service;

import java.util.UUID;
import network.foodsharing.basket.domain.BasketStatus;
import network.foodsharing.basket.repository.BasketRepository;
import network.foodsharing.upload.service.UploadAccessRule;
import org.springframework.stereotype.Component;

/** Basket photos are public (A41, {@code BASKET_PHOTO}). */
@Component
public class BasketUploadAccessRule implements UploadAccessRule {

    private final BasketRepository baskets;

    public BasketUploadAccessRule(BasketRepository baskets) {
        this.baskets = baskets;
    }

    @Override
    public String module() {
        return BasketService.PHOTO_MODULE;
    }

    @Override
    public boolean mayRead(UUID userId, UUID usageOwnerId) {
        return true;
    }

    /** A deleted basket is no longer shown, so its photo can go. */
    @Override
    public boolean exists(UUID usageOwnerId) {
        return baskets.findById(usageOwnerId).filter(basket -> basket.getStatus() != BasketStatus.DELETED).isPresent();
    }
}
