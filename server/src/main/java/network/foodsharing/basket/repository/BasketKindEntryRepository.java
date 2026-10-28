package network.foodsharing.basket.repository;

import java.util.List;
import java.util.UUID;
import network.foodsharing.basket.domain.BasketKindEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BasketKindEntryRepository extends JpaRepository<BasketKindEntry, UUID> {

    List<BasketKindEntry> findByBasketId(UUID basketId);

    void deleteByBasketId(UUID basketId);
}
