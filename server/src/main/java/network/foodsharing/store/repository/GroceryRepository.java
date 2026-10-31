package network.foodsharing.store.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.store.domain.Grocery;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroceryRepository extends JpaRepository<Grocery, UUID> {

    List<Grocery> findAllByOrderByNameAscIdAsc();

    Optional<Grocery> findByNameIgnoreCase(String name);
}
