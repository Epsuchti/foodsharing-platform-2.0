package network.foodsharing.common.jobs.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.common.jobs.JobRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRunRepository extends JpaRepository<JobRun, UUID> {

    Optional<JobRun> findFirstByJobNameOrderByStartedAtDesc(String jobName);

    @Modifying
    @Query("delete from JobRun r where r.startedAt < :before")
    int deleteStartedBefore(@Param("before") Instant before);
}
