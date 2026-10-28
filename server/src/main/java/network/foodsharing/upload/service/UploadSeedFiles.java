package network.foodsharing.upload.service;

import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Seeded uploads (context {@code seed}, storage keys {@code seed/<file>}) need their bytes on disk: at startup in
 * {@code local}/{@code e2e} every classpath file under {@code db/seed/upload/files/} is written to the upload store
 * when it is missing (docs/seed-ids.md module 0040).
 */
@Component
@Profile({"local", "e2e"})
public class UploadSeedFiles {

    private static final Logger log = LoggerFactory.getLogger(UploadSeedFiles.class);

    private final UploadStorage storage;

    private final UploadService uploads;

    public UploadSeedFiles(UploadStorage storage, UploadService uploads) {
        this.storage = storage;
        this.uploads = uploads;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void writeMissing() {
        try {
            Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath:db/seed/upload/files/*");
            for (Resource file : files) {
                String key = "seed/" + file.getFilename();
                if (exists(key)) {
                    continue;
                }
                try (InputStream in = file.getInputStream()) {
                    storage.write(key, in.readAllBytes());
                }
            }
            uploads.renderMissingVariants();
        } catch (IOException ex) {
            log.warn("Could not write seeded upload files: {}", ex.getMessage());
        }
    }

    private boolean exists(String key) {
        try (InputStream ignored = storage.open(key)) {
            return true;
        } catch (IOException ex) {
            return false;
        }
    }
}
