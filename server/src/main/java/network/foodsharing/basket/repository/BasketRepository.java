package network.foodsharing.basket.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import network.foodsharing.basket.domain.Basket;
import network.foodsharing.basket.domain.BasketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BasketRepository extends JpaRepository<Basket, UUID> {

    List<Basket> findByStatusAndExpiresAtAfter(BasketStatus status, Instant now);

    List<Basket> findByStatusAndExpiresAtLessThanEqual(BasketStatus status, Instant now);

    List<Basket> findByOwnerIdAndStatusNotOrderByCreatedAtDesc(UUID ownerId, BasketStatus status);

    List<Basket> findByOwnerIdAndStatusOrderByCreatedAtDesc(UUID ownerId, BasketStatus status);

    List<Basket> findByOwnerId(UUID ownerId);

    List<Basket> findByOwnerIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(UUID ownerId, Instant from, Instant to);

    /** One owner id per basket created in {@code [from, to)} (region statistics). */
    @Query("select b.ownerId from Basket b where b.createdAt >= :from and b.createdAt < :to")
    List<UUID> findOwnerIdsCreatedBetween(@Param("from") Instant from, @Param("to") Instant to);
}
