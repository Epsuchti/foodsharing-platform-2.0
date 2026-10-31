package network.foodsharing.store.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.store.domain.PickupWeightCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PickupWeightCategoryRepository extends JpaRepository<PickupWeightCategory, UUID> {

    List<PickupWeightCategory> findAllByOrderBySortOrderAscIdAsc();

    Optional<PickupWeightCategory> findByCode(String code);
}
