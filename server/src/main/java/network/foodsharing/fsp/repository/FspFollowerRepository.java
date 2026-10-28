package network.foodsharing.fsp.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.fsp.domain.FspFollower;
import network.foodsharing.fsp.domain.FspFollowerRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FspFollowerRepository extends JpaRepository<FspFollower, UUID> {

    List<FspFollower> findByFspId(UUID fspId);

    List<FspFollower> findByFspIdAndRole(UUID fspId, FspFollowerRole role);

    Optional<FspFollower> findByFspIdAndUserId(UUID fspId, UUID userId);

    List<FspFollower> findByUserId(UUID userId);

    long countByFspId(UUID fspId);

    @Query("select f.fspId, count(f) from FspFollower f where f.fspId in :fspIds group by f.fspId")
    List<Object[]> countByFspIds(@Param("fspIds") Collection<UUID> fspIds);

    void deleteByUserId(UUID userId);
}
