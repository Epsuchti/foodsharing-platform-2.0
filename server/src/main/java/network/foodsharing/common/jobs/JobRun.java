package network.foodsharing.common.jobs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** One run of a {@link RunnableJob} ({@code job_run}, A25), written by {@link JobRunner}. */
@Getter
@Setter
@Entity
@Table(name = "job_run")
public class JobRun extends BaseEntity {

    /** Outcome of a run; RUNNING until it finished. */
    public enum Outcome { RUNNING, OK, FAILED }

    /** What started the run. */
    public enum Trigger { SCHEDULE, MANUAL }

    @Column(name = "job_name", nullable = false, length = 64)
    private String jobName;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20)
    private Outcome outcome;

    @Column(name = "message", length = 2000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_trigger", nullable = false, length = 20)
    private Trigger trigger;

    @Column(name = "actor_id")
    private UUID actorId;
}
