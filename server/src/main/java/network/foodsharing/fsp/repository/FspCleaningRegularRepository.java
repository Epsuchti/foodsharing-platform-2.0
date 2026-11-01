package network.foodsharing.fsp.repository;

import java.util.List;
import java.util.UUID;
import network.foodsharing.fsp.domain.FspCleaningRegular;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FspCleaningRegularRepository extends JpaRepository<FspCleaningRegular, UUID> {

    List<FspCleaningRegular> findByFspId(UUID fspId);

    List<FspCleaningRegular> findByFspIdIn(java.util.Collection<UUID> fspIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from FspCleaningRegular r where r.fspId = :fspId")
    int deleteByFspId(@Param("fspId") UUID fspId);
}
