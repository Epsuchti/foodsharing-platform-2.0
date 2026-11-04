package network.foodsharing.upload.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.NoHistory;
import network.foodsharing.upload.domain.Upload;
import network.foodsharing.upload.repository.UploadBlobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * One-time import of the upload files of the old {@code /data/uploads} volume into the database (decision 158): with
 * {@code app.uploads.import-dir} set, every {@code upload} row without an original blob gets the bytes of
 * {@code <import-dir>/<storage_key>} when their sha256 matches the row (missing or different files are logged and the
 * row is left alone), plus the variants {@code <storage_key>_<size>} when all of them are there, else they are
 * rendered. Idempotent, batches of {@value #BATCH}, one transaction per upload, ShedLock against a second replica.
 * Removed in the release after the hosting cut-over.
 */
@Component
@Profile("prod")
public class UploadDiskImport {

    private static final Logger LOG = LoggerFactory.getLogger(UploadDiskImport.class);
    static final String LOCK = "upload-disk-import";
    static final int BATCH = 50;

    /** Counts of one run. */
    public record Result(int imported, int missing, int mismatched) {
    }

    private final UploadService uploads;
    private final UploadBlobRepository blobs;
    private final LockProvider locks;
    private final AppProperties properties;
    private final Clock clock;

    public UploadDiskImport(UploadService uploads, UploadBlobRepository blobs, LockProvider locks,
                            AppProperties properties, Clock clock) {
        this.uploads = uploads;
        this.blobs = blobs;
        this.locks = locks;
        this.properties = properties;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void importOnStartup() {
        String dir = properties.getUploads().getImportDir();
        if (dir == null || dir.isBlank()) {
            return;
        }
        Optional<SimpleLock> lock = locks.lock(new LockConfiguration(clock.instant(), LOCK, Duration.ofHours(2),
                Duration.ZERO));
        if (lock.isEmpty()) {
            LOG.info("Upload disk import runs on another instance");
            return;
        }
        try {
            importFrom(Path.of(dir));
        } finally {
            lock.get().unlock();
        }
    }

    /** Imports every upload without an original blob from {@code dir}. */
    @NoHistory(reason = "moves bytes of existing uploads")
    public Result importFrom(Path dir) {
        Path root = dir.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            LOG.warn("Upload import dir {} does not exist; nothing imported", root);
            return new Result(0, 0, 0);
        }
        int imported = 0;
        int missing = 0;
        int mismatched = 0;
        UUID after = null;
        List<UUID> batch;
        do {
            batch = blobs.idsWithoutOriginal(after, BATCH);
            for (UUID id : batch) {
                after = id;
                switch (importOne(root, uploads.find(id))) {
                    case IMPORTED -> imported++;
                    case MISSING -> missing++;
                    case MISMATCHED -> mismatched++;
                }
            }
        } while (batch.size() == BATCH);
        Result result = new Result(imported, missing, mismatched);
        LOG.info("Upload disk import: imported={} missing={} mismatched={} still without bytes={}", imported, missing,
                mismatched, blobs.countMissingOriginals());
        return result;
    }

    enum Outcome { IMPORTED, MISSING, MISMATCHED }

    private Outcome importOne(Path root, Upload upload) {
        Optional<byte[]> original = read(root, upload.getStorageKey());
        if (original.isEmpty()) {
            LOG.warn("Upload {}: file {} is missing", upload.getId(), upload.getStorageKey());
            return Outcome.MISSING;
        }
        if (!sha256(original.get()).equals(upload.getSha256())) {
            LOG.warn("Upload {}: file {} does not match the stored sha256", upload.getId(), upload.getStorageKey());
            return Outcome.MISMATCHED;
        }
        Map<String, byte[]> variants = new HashMap<>();
        for (String size : uploads.variantSizes()) {
            read(root, ImageVariants.variantKey(upload.getStorageKey(), size)).ifPresent(b -> variants.put(size, b));
        }
        uploads.restoreBytes(upload.getId(), original.get(), variants);
        return Outcome.IMPORTED;
    }

    private static Optional<byte[]> read(Path root, String key) {
        Path file = root.resolve(key).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(file));
        } catch (IOException e) {
            LOG.warn("Could not read {}: {}", file, e.getMessage());
            return Optional.empty();
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
