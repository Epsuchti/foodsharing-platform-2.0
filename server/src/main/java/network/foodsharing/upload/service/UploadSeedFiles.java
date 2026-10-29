package network.foodsharing.upload.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import network.foodsharing.common.seed.InMemoryState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Seeded uploads (context {@code seed}, storage keys {@code seed/<file>}) need their bytes on disk: in
 * {@code local}/{@code e2e} every classpath file under {@code db/seed/upload/files/} is written to the upload store
 * when it is missing or differs (a store shared with an older build, or bytes a test deleted by replacing a seed
 * photo), and the variants of the seeded image rows are marked or rendered (docs/seed-ids.md module 0040).
 * Runs at startup and after every dev seed reset, which re-inserts the upload rows with {@code variants=false}.
 */
@Component
@Profile({"local", "e2e"})
public class UploadSeedFiles implements InMemoryState {

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
                byte[] bytes;
                try (InputStream in = file.getInputStream()) {
                    bytes = in.readAllBytes();
                }
                if (Arrays.equals(bytes, stored(key))) {
                    continue;
                }
                storage.write(key, bytes);
                for (String size : uploads.variantSizes()) { // rendered from other bytes
                    storage.delete(ImageVariants.variantKey(key, size));
                }
            }
            uploads.renderMissingVariants();
        } catch (IOException ex) {
            log.warn("Could not write seeded upload files: {}", ex.getMessage());
        }
    }

    /** The dev seed reset re-inserted the seeded upload rows: restore their bytes and variants. */
    @Override
    public void resetInMemoryState() {
        writeMissing();
    }

    private byte[] stored(String key) {
        try (InputStream in = storage.open(key)) {
            return in.readAllBytes();
        } catch (IOException ex) {
            return null;
        }
    }
}
