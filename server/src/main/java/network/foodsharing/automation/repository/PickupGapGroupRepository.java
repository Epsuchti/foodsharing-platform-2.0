package network.foodsharing.automation.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.automation.domain.PickupGapGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PickupGapGroupRepository extends JpaRepository<PickupGapGroup, UUID> {

    /** Serialises changes of one gap list (decision 151). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from PickupGapGroup g where g.id = :id")
    Optional<PickupGapGroup> lockById(@Param("id") UUID id);
}
