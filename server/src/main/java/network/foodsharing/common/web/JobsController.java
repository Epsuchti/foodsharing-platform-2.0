package network.foodsharing.common.web;

import java.util.UUID;
import network.foodsharing.api.JobsApi;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.JobProgress;
import network.foodsharing.api.model.JobStatus;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.jobs.ProgressTracker;
import network.foodsharing.common.jobs.ProgressTracker.JobState;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

/**
 * Progress and results of {@link ProgressTracker} jobs (docs/conventions.md A17). Only the user who started a job
 * sees it; everybody else gets 404 JOB_NOT_FOUND. The user id is the authentication name (the session principal's
 * id), so this shared package does not depend on {@code security}.
 */
@RestController
public class JobsController implements JobsApi {

    private final ProgressTracker tracker;

    public JobsController(ProgressTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public ResponseEntity<JobProgress> getJob(UUID jobId) {
        JobState job = ownJob(jobId);
        JobProgress progress = new JobProgress();
        progress.setId(job.id());
        progress.setStatus(JobStatus.fromValue(job.status().name()));
        progress.setTotal(job.total());
        progress.setDone(job.done());
        progress.setMessage(job.message());
        progress.setResultAvailable(job.result() != null);
        progress.setResultContentType(job.result() == null ? null : job.result().contentType());
        return ResponseEntity.ok(progress);
    }

    @Override
    public ResponseEntity<Resource> downloadJobResult(UUID jobId) {
        JobState job = ownJob(jobId);
        if (job.status() != ProgressTracker.Status.DONE || job.result() == null) {
            throw ApiException.conflict(ApiErrorCode.JOB_RESULT_NOT_AVAILABLE);
        }
        ProgressTracker.JobResult result = job.result();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(result.fileName() == null ? "result" : result.fileName()).build().toString())
                .body(new ByteArrayResource(result.content()));
    }

    private JobState ownJob(UUID jobId) {
        UUID userId = currentUserId();
        return tracker.find(jobId)
                .filter(job -> job.ownerId() != null && job.ownerId().equals(userId))
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.JOB_NOT_FOUND));
    }

    private static UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw ApiException.unauthenticated();
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException e) {
            throw ApiException.unauthenticated();
        }
    }
}
