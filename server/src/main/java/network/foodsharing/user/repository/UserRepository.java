package network.foodsharing.user.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    List<User> findByIdIn(Collection<UUID> ids);

    List<User> findByHomeRegionIdInAndActiveTrue(Collection<UUID> regionIds);

    @Query(value = "select nextval('foodsaver_number_seq')", nativeQuery = true)
    long nextFoodsaverNumber();
}
