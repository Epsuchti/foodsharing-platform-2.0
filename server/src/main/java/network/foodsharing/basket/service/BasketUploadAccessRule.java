package network.foodsharing.basket.service;

import java.util.UUID;
import network.foodsharing.upload.service.UploadAccessRule;
import org.springframework.stereotype.Component;

/** Basket photos are public (A41, {@code BASKET_PHOTO}). */
@Component
public class BasketUploadAccessRule implements UploadAccessRule {

    @Override
    public String module() {
        return BasketService.PHOTO_MODULE;
    }

    @Override
    public boolean mayRead(UUID userId, UUID usageOwnerId) {
        return true;
    }
}
