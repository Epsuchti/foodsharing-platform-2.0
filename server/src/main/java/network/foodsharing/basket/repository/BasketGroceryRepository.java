package network.foodsharing.basket.repository;

import java.util.List;
import java.util.UUID;
import network.foodsharing.basket.domain.BasketGrocery;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BasketGroceryRepository extends JpaRepository<BasketGrocery, UUID> {

    List<BasketGrocery> findByBasketId(UUID basketId);

    void deleteByBasketId(UUID basketId);
}
