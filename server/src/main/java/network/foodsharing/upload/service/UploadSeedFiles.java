package network.foodsharing.upload.service;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import network.foodsharing.common.seed.InMemoryState;
import network.foodsharing.upload.domain.Upload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Seeded uploads (context {@code seed}, storage keys {@code seed/<file>}) need their bytes in the database: in
 * {@code local}/{@code e2e} every classpath file under {@code db/seed/upload/files/} is written as the original blob of
 * its seeded row when the blob is missing or its sha256 differs (a test replaced it), and the variants of the seeded
 * image rows are written too (docs/seed-ids.md module 0040, decision 158). Runs at startup and after every dev seed
 * reset, which restores a snapshot without any blobs; rendered variants are kept in memory so a reset does not render
 * them again.
 */
@Component
@Profile({"local", "e2e"})
public class UploadSeedFiles implements InMemoryState {

    private static final Logger log = LoggerFactory.getLogger(UploadSeedFiles.class);

    private final UploadService uploads;

    /** Variants by sha256 of the seed file. */
    private final Map<String, Map<String, byte[]>> variants = new ConcurrentHashMap<>();

    public UploadSeedFiles(UploadService uploads) {
        this.uploads = uploads;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void writeMissing() {
        try {
            Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath:db/seed/upload/files/*");
            for (Resource file : files) {
                Optional<Upload> row = uploads.findByStorageKey("seed/" + file.getFilename());
                if (row.isEmpty()) {
                    continue;
                }
                byte[] bytes;
                try (InputStream in = file.getInputStream()) {
                    bytes = in.readAllBytes();
                }
                String sha = sha256(bytes);
                if (uploads.storedSha256(row.get().getId()).filter(sha::equals).isPresent()) {
                    continue;
                }
                Map<String, byte[]> stored = uploads.restoreBytes(row.get().getId(), bytes,
                        variants.getOrDefault(sha, Map.of()));
                if (!stored.isEmpty()) {
                    variants.put(sha, stored);
                }
            }
            uploads.renderMissingVariants();
        } catch (IOException ex) {
            log.warn("Could not write seeded upload files: {}", ex.getMessage());
        }
    }

    /** The dev seed reset re-inserted the seeded upload rows without bytes: restore their bytes and variants. */
    @Override
    public void resetInMemoryState() {
        writeMissing();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
