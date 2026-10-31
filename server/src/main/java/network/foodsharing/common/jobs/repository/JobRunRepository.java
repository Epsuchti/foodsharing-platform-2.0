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

    Optional<JobRun> findFirstByJobNameOrderByStartedAtDescIdDesc(String jobName);

    org.springframework.data.domain.Page<JobRun> findByJobNameOrderByStartedAtDescIdDesc(String jobName,
            org.springframework.data.domain.Pageable pageable);

    @Modifying
    @Query("delete from JobRun r where r.startedAt < :before")
    int deleteStartedBefore(@Param("before") Instant before);
}
