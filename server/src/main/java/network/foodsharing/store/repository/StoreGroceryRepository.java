package network.foodsharing.store.repository;

import java.util.List;
import java.util.UUID;
import network.foodsharing.store.domain.StoreGrocery;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreGroceryRepository extends JpaRepository<StoreGrocery, UUID> {

    List<StoreGrocery> findByStoreId(UUID storeId);

    List<StoreGrocery> findByGroceryId(UUID groceryId);

    long countByGroceryId(UUID groceryId);
}
