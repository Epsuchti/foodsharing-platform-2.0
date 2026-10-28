package network.foodsharing.upload.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.NoHistory;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.upload.domain.Upload;
import network.foodsharing.upload.domain.UploadAction;
import network.foodsharing.upload.repository.UploadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Minimal uploads (A34): size and mime-type limits, owner-only until claimed by a module, expiry of unclaimed files.
 */
@Service
public class UploadService {

    private static final Logger LOG = LoggerFactory.getLogger(UploadService.class);
    private static final DateTimeFormatter KEY_PREFIX = DateTimeFormatter.ofPattern("yy/MM").withZone(ZoneOffset.UTC);

    /** A file to store. */
    public record NewFile(String fileName, String mimeType, byte[] bytes) {
    }

    private final UploadRepository repository;
    private final UploadStorage storage;
    private final HistoryService history;
    private final AppProperties properties;
    private final Clock clock;
    private final Map<String, UploadAccessRule> rules;

    public UploadService(UploadRepository repository, UploadStorage storage, HistoryService history,
                         AppProperties properties, Clock clock, List<UploadAccessRule> rules) {
        this.repository = repository;
        this.storage = storage;
        this.history = history;
        this.properties = properties;
        this.clock = clock;
        this.rules = rules.stream().collect(Collectors.toMap(UploadAccessRule::module, Function.identity()));
    }

    /** Stores a new unclaimed upload of {@code ownerUserId} (null = system, e.g. inbound mail attachments). */
    @Transactional
    public Upload store(UUID ownerUserId, NewFile file) {
        long maxBytes = properties.getUploads().getMaxSizeMb() * 1024L * 1024L;
        if (file.bytes().length > maxBytes) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, ApiErrorCode.UPLOAD_TOO_LARGE)
                    .withArg("maxMb", String.valueOf(properties.getUploads().getMaxSizeMb()));
        }
        String mimeType = normaliseMimeType(file.mimeType());
        if (ownerUserId != null && !isAllowed(mimeType)) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ApiErrorCode.UPLOAD_TYPE_NOT_ALLOWED);
        }
        Instant now = clock.instant();
        Upload upload = new Upload();
        upload.setOwnerUserId(ownerUserId);
        upload.setFileName(sanitiseFileName(file.fileName()));
        upload.setMimeType(mimeType);
        upload.setSize(file.bytes().length);
        upload.setSha256(sha256(file.bytes()));
        upload.setStorageKey(KEY_PREFIX.format(now) + "/" + UUID.randomUUID());
        upload.setExpiresAt(now.plus(Duration.ofHours(properties.getUploads().getUnclaimedTtlHours())));
        try {
            storage.write(upload.getStorageKey(), file.bytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Upload saved = repository.save(upload);
        deleteBytesOnRollback(saved.getStorageKey());
        history.record(history.event(UploadAction.UPLOADED, saved.getId())
                .metadata(Map.of("mimeType", mimeType, "size", saved.getSize())).build());
        return saved;
    }

    /** Marks an upload as used by a module; it no longer expires and becomes readable through the module's rule. */
    @Transactional
    public Upload claim(UUID uploadId, UploadUsage usage) {
        Upload upload = find(uploadId);
        upload.setUsageCount(upload.getUsageCount() + 1);
        upload.setUsageModule(usage.module());
        upload.setUsageOwnerId(usage.ownerId());
        upload.setExpiresAt(null);
        history.record(history.event(UploadAction.CLAIMED, upload.getId())
                .metadata(Map.of("module", usage.module(), "ownerId", String.valueOf(usage.ownerId()))).build());
        return upload;
    }

    @Transactional(readOnly = true)
    public Upload find(UUID uploadId) {
        return repository.findById(uploadId).orElseThrow(() -> ApiException.notFound(ApiErrorCode.UPLOAD_NOT_FOUND));
    }

    /** Owner always; a claimed upload also through the claiming module's {@link UploadAccessRule}. */
    public boolean mayRead(UUID userId, Upload upload) {
        if (userId != null && userId.equals(upload.getOwnerUserId())) {
            return true;
        }
        if (upload.getUsageCount() == 0 || upload.getUsageModule() == null) {
            return false;
        }
        UploadAccessRule rule = rules.get(upload.getUsageModule());
        return rule != null && rule.mayRead(userId, upload.getUsageOwnerId());
    }

    /** Opens the bytes after the access check (ORGA may read everything). */
    @Transactional(readOnly = true)
    public Upload requireReadable(UUID userId, boolean orga, UUID uploadId) {
        Upload upload = find(uploadId);
        if (!orga && !mayRead(userId, upload)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        return upload;
    }

    public InputStream open(Upload upload) {
        try {
            return storage.open(upload.getStorageKey());
        } catch (IOException e) {
            throw ApiException.notFound(ApiErrorCode.UPLOAD_NOT_FOUND);
        }
    }

    /** The owner deletes an unclaimed upload. */
    @Transactional
    public void deleteOwn(UUID userId, UUID uploadId) {
        Upload upload = find(uploadId);
        if (!userId.equals(upload.getOwnerUserId())) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        if (upload.getUsageCount() > 0) {
            throw ApiException.conflict(ApiErrorCode.UPLOAD_IN_USE);
        }
        delete(upload);
    }

    /** Deletes an upload regardless of its usage (the owning module removes its object, e.g. a mail in TRASH). */
    @Transactional
    public void delete(Upload upload) {
        repository.delete(upload);
        deleteBytesAfterCommit(upload.getStorageKey());
        history.record(history.event(UploadAction.DELETED, upload.getId()).build());
    }

    /** Unclaimed uploads past their expiry (UploadCleanup). */
    @Transactional
    @NoHistory(reason = "delegates to delete(), which records UploadAction.DELETED per upload")
    public int deleteExpired(Instant now) {
        List<Upload> expired = repository.findByUsageCountAndExpiresAtBefore(0, now);
        expired.forEach(this::delete);
        return expired.size();
    }

    public boolean isAllowed(String mimeType) {
        return properties.getUploads().getAllowedTypes().stream()
                .anyMatch(allowed -> allowed.trim().equalsIgnoreCase(mimeType));
    }

    static String normaliseMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return "application/octet-stream";
        }
        int semicolon = mimeType.indexOf(';');
        return (semicolon >= 0 ? mimeType.substring(0, semicolon) : mimeType).trim().toLowerCase(Locale.ROOT);
    }

    static String sanitiseFileName(String fileName) {
        String name = fileName == null ? "" : fileName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (name.isEmpty()) {
            name = "file";
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void deleteBytesOnRollback(String key) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        deleteQuietly(key);
                    }
                }
            });
        }
    }

    private void deleteBytesAfterCommit(String key) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteQuietly(key);
                }
            });
        } else {
            deleteQuietly(key);
        }
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (IOException | RuntimeException e) {
            LOG.warn("Could not delete upload bytes {}: {}", key, e.getMessage());
        }
    }
}
