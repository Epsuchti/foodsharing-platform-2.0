package network.foodsharing.upload.service;

import java.time.Instant;
import network.foodsharing.common.jobs.CleanupTask;
import org.springframework.stereotype.Component;

/** Deletes uploads that were never claimed within {@code app.uploads.unclaimed-ttl-hours} (A34). */
@Component
public class UploadCleanup implements CleanupTask {

    private final UploadService uploads;

    public UploadCleanup(UploadService uploads) {
        this.uploads = uploads;
    }

    @Override
    public String name() {
        return "unclaimed-uploads";
    }

    @Override
    public int cleanup(Instant now) {
        return uploads.deleteExpired(now);
    }
}
