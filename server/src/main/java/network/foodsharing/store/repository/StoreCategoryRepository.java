package network.foodsharing.store.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.store.domain.StoreCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreCategoryRepository extends JpaRepository<StoreCategory, UUID> {

    List<StoreCategory> findAllByOrderByNameAscIdAsc();

    Optional<StoreCategory> findByNameIgnoreCase(String name);
}
