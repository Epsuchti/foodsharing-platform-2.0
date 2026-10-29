package network.foodsharing.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import network.foodsharing.auth.domain.IpBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IpBlockRepository extends JpaRepository<IpBlock, UUID> {

    @Query("select b from IpBlock b where b.untilAt is null or b.untilAt > :now")
    List<IpBlock> findActive(@Param("now") Instant now);

    /** Cleanup: blocks that ended before {@code before} (blocks without end stay). */
    @org.springframework.data.jpa.repository.Modifying
    @Query("delete from IpBlock b where b.untilAt < :before")
    int deleteEndedBefore(@Param("before") Instant before);
}
