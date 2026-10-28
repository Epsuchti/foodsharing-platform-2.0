package network.foodsharing.basket.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import network.foodsharing.basket.domain.Basket;
import network.foodsharing.basket.domain.BasketStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BasketRepository extends JpaRepository<Basket, UUID> {

    List<Basket> findByStatusAndExpiresAtAfter(BasketStatus status, Instant now);

    List<Basket> findByStatusAndExpiresAtLessThanEqual(BasketStatus status, Instant now);

    List<Basket> findByOwnerIdAndStatusNotOrderByCreatedAtDesc(UUID ownerId, BasketStatus status);

    List<Basket> findByOwnerIdAndStatusOrderByCreatedAtDesc(UUID ownerId, BasketStatus status);

    List<Basket> findByOwnerId(UUID ownerId);

    List<Basket> findByOwnerIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(UUID ownerId, Instant from, Instant to);
}
