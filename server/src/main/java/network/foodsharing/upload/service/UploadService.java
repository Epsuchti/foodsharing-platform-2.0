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

    /** Usages whose uploads are readable by everybody, also without login (A41). */
    public static final java.util.Set<String> PUBLIC_MODULES = java.util.Set.of("USER_PHOTO", "FSP_PHOTO",
            "BASKET_PHOTO", "BLOG_PICTURE", "CONTENT_IMAGE", "RESOURCE_IMAGE");

    private final ImageVariants images;

    public UploadService(UploadRepository repository, UploadStorage storage, HistoryService history,
                         AppProperties properties, Clock clock, List<UploadAccessRule> rules) {
        this.images = new ImageVariants(properties);
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
        if (ownerUserId != null && (!isAllowed(mimeType) || !contentMatches(mimeType, file.bytes()))) {
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
        writeVariants(upload, file.bytes());
        Upload saved = repository.save(upload);
        deleteBytesOnRollback(saved.getStorageKey());
        if (saved.isVariants()) {
            images.sizeNames().forEach(size ->
                    deleteBytesOnRollback(ImageVariants.variantKey(saved.getStorageKey(), size)));
        }
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
        upload.setPublicReadable(upload.isPublicReadable() || PUBLIC_MODULES.contains(usage.module()));
        history.record(history.event(UploadAction.CLAIMED, upload.getId())
                .metadata(Map.of("module", usage.module(), "ownerId", String.valueOf(usage.ownerId()))).build());
        return upload;
    }

    /** Uploads owned by a user, oldest first (GDPR export, A21). */
    @Transactional(readOnly = true)
    public List<Upload> ownedBy(UUID ownerUserId) {
        return repository.findByOwnerUserIdOrderByCreatedAt(ownerUserId);
    }

    @Transactional(readOnly = true)
    public Upload find(UUID uploadId) {
        return repository.findById(uploadId).orElseThrow(() -> ApiException.notFound(ApiErrorCode.UPLOAD_NOT_FOUND));
    }

    /** Owner always; a claimed upload also through the claiming module's {@link UploadAccessRule}. */
    public boolean mayRead(UUID userId, Upload upload) {
        if (upload.isPublicReadable()) {
            return true;
        }
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

    /** A variant ({@code sm|md|lg}) when rendered, else the original. @throws ApiException 400 unknown size */
    public Download open(Upload upload, String size) {
        if (size != null && !images.isSize(size)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        if (size != null && upload.isVariants()) {
            try (InputStream in = storage.open(ImageVariants.variantKey(upload.getStorageKey(), size))) {
                byte[] bytes = in.readAllBytes();
                return new Download(ImageVariants.VARIANT_MIME_TYPE, bytes.length,
                        new java.io.ByteArrayInputStream(bytes));
            } catch (IOException missingVariant) {
                LOG.warn("Variant {} of upload {} is missing; serving the original", size, upload.getId());
            }
        }
        return new Download(upload.getMimeType(), upload.getSize(), open(upload));
    }

    /** Bytes to send: content type, length and stream. */
    public record Download(String mimeType, long size, InputStream stream) {
    }

    /**
     * Renders missing variants of image uploads whose bytes exist (seeded uploads, uploads from before A41); called by
     * {@link UploadSeedFiles} at startup.
     */
    @Transactional
    @NoHistory(reason = "derived files of existing uploads; nothing user-visible changes")
    public int renderMissingVariants() {
        int rendered = 0;
        for (Upload upload : repository.findByVariantsFalseAndMimeTypeIn(ImageVariants.IMAGE_TYPES)) {
            if (variantsStored(upload)) { // e.g. seeded rows re-inserted by a seed reset: the files are still there
                upload.setVariants(true);
                rendered++;
                continue;
            }
            try (InputStream in = storage.open(upload.getStorageKey())) {
                writeVariants(upload, in.readAllBytes());
                rendered += upload.isVariants() ? 1 : 0;
            } catch (IOException missing) {
                LOG.debug("Upload {} has no bytes yet", upload.getId());
            }
        }
        return rendered;
    }

    /** Names of the image variants ({@code sm|md|lg}). */
    public java.util.Set<String> variantSizes() {
        return images.sizeNames();
    }

    private boolean variantsStored(Upload upload) {
        for (String size : images.sizeNames()) {
            try (InputStream ignored = storage.open(ImageVariants.variantKey(upload.getStorageKey(), size))) {
                // present
            } catch (IOException missing) {
                return false;
            }
        }
        return true;
    }

    private void writeVariants(Upload upload, byte[] bytes) {
        images.render(upload.getMimeType(), bytes).ifPresent(rendered -> {
            try {
                for (Map.Entry<String, byte[]> variant : rendered.variants().entrySet()) {
                    storage.write(ImageVariants.variantKey(upload.getStorageKey(), variant.getKey()),
                            variant.getValue());
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            upload.setWidth(rendered.width());
            upload.setHeight(rendered.height());
            upload.setVariants(true);
        });
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
        if (upload.isVariants()) {
            images.sizeNames().forEach(size ->
                    deleteBytesAfterCommit(ImageVariants.variantKey(upload.getStorageKey(), size)));
        }
        history.record(history.event(UploadAction.DELETED, upload.getId()).build());
    }

    // ------------------------------------------------------------------------- administration (C10, ORGA via admin)

    public record ModuleCount(String module, long count, long bytes) {
    }

    public record Summary(long count, long bytes, long unclaimedCount, List<ModuleCount> byModule) {
    }

    /** Uploads newest first; {@code module} wins over {@code unclaimed}. */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Upload> adminPage(Boolean unclaimed, String module, int page,
                                                                  int size) {
        var pageable = org.springframework.data.domain.PageRequest.of(page, size);
        if (module != null && !module.isBlank()) {
            return repository.findByUsageModuleOrderByCreatedAtDesc(module.strip(), pageable);
        }
        if (unclaimed != null) {
            return unclaimed ? repository.findByUsageCountOrderByCreatedAtDesc(0, pageable)
                    : repository.findByUsageCountGreaterThanOrderByCreatedAtDesc(0, pageable);
        }
        return repository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        List<ModuleCount> modules = repository.countByModule().stream()
                .map(row -> new ModuleCount(row[0] == null ? "UNCLAIMED" : (String) row[0], ((Number) row[1]).longValue(),
                        row[2] == null ? 0 : ((Number) row[2]).longValue()))
                .sorted(java.util.Comparator.comparing(ModuleCount::module)).toList();
        long count = modules.stream().mapToLong(ModuleCount::count).sum();
        long bytes = modules.stream().mapToLong(ModuleCount::bytes).sum();
        return new Summary(count, bytes, repository.countByUsageCount(0), modules);
    }

    /**
     * Claimed uploads whose usage owner no longer exists according to the module's {@link UploadAccessRule#exists}
     * (UploadCleanup); deleted like any other upload.
     */
    @Transactional
    @NoHistory(reason = "delegates to delete(), which records UploadAction.DELETED per upload")
    public int deleteOwnerless() {
        int deleted = 0;
        for (UploadAccessRule rule : rules.values()) {
            for (Upload upload : repository.findByUsageModuleAndUsageCountGreaterThan(rule.module(), 0)) {
                if (upload.getUsageOwnerId() != null && !rule.exists(upload.getUsageOwnerId())) {
                    delete(upload);
                    deleted++;
                }
            }
        }
        return deleted;
    }

    /** Unclaimed uploads past their expiry (UploadCleanup). */
    @Transactional
    @NoHistory(reason = "delegates to delete(), which records UploadAction.DELETED per upload")
    public int deleteExpired(Instant now) {
        List<Upload> expired = repository.findByUsageCountAndExpiresAtBefore(0, now);
        expired.forEach(this::delete);
        return expired.size();
    }

    /**
     * Removes variant files ({@code <storage_key>_<size>}) whose upload row is gone (A41, UploadCleanup); files younger
     * than an hour are left alone so a variant written before its row commits is never removed.
     */
    @Transactional(readOnly = true)
    public int deleteOrphanVariants(Instant now) {
        List<UploadStorage.StoredFile> files;
        try {
            files = storage.list();
        } catch (IOException | UncheckedIOException e) {
            LOG.warn("Could not list upload files: {}", e.getMessage());
            return 0;
        }
        Instant cutoff = now.minus(Duration.ofHours(1));
        int deleted = 0;
        for (UploadStorage.StoredFile file : files) {
            int underscore = file.key().lastIndexOf('_');
            if (underscore < 0 || !images.isSize(file.key().substring(underscore + 1))
                    || !file.modifiedAt().isBefore(cutoff)
                    || repository.existsByStorageKey(file.key().substring(0, underscore))) {
                continue;
            }
            deleteQuietly(file.key());
            deleted++;
        }
        return deleted;
    }

    public boolean isAllowed(String mimeType) {
        return properties.getUploads().getAllowedTypes().stream()
                .anyMatch(allowed -> allowed.trim().equalsIgnoreCase(mimeType));
    }

    /**
     * The declared type must match the file's magic bytes for images and PDFs (images are served publicly and
     * rendered into variants; a renamed HTML/SVG/script must not pass as one). Other allowed types are not sniffed.
     */
    public static boolean contentMatches(String mimeType, byte[] bytes) {
        return switch (mimeType) {
            case "image/jpeg" -> startsWith(bytes, 0xFF, 0xD8, 0xFF);
            case "image/png" -> startsWith(bytes, 0x89, 'P', 'N', 'G');
            case "image/gif" -> startsWith(bytes, 'G', 'I', 'F', '8');
            case "image/webp" -> startsWith(bytes, 'R', 'I', 'F', 'F') && bytes.length >= 12 && bytes[8] == 'W'
                    && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            case "application/pdf" -> startsWith(bytes, '%', 'P', 'D', 'F');
            default -> true;
        };
    }

    private static boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
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
