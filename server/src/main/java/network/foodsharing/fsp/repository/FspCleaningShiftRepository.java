package network.foodsharing.fsp.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.fsp.domain.FspCleaningShift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FspCleaningShiftRepository extends JpaRepository<FspCleaningShift, UUID> {

    Optional<FspCleaningShift> findByFspIdAndShiftAt(UUID fspId, Instant shiftAt);

    @Query("select s from FspCleaningShift s where s.fspId in :fspIds and s.shiftAt >= :from and s.shiftAt < :to")
    List<FspCleaningShift> findInRange(@Param("fspIds") Collection<UUID> fspIds, @Param("from") Instant from,
                                       @Param("to") Instant to);
}
