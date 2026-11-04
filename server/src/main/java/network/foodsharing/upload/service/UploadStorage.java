package network.foodsharing.upload.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

/**
 * Where upload bytes live (A34, decision 158): one blob per upload and variant ({@link #ORIGINAL} or an image size
 * {@code sm|md|lg}). {@link DbUploadStorage} (PostgreSQL) is the only implementation; a later S3 backend replaces it.
 * Blobs are removed with their {@code upload} row.
 */
public interface UploadStorage {

    /** Variant name of the uploaded bytes themselves. */
    String ORIGINAL = "orig";

    /** Stored bytes: content type, length, sha256 (the ETag) and a lazily read stream. */
    record StoredBlob(String mimeType, long size, String sha256, InputStream stream) {
    }

    /** Stores (or replaces) the bytes of {@code variant}; the upload row must already be written. */
    void write(UUID uploadId, String variant, String mimeType, InputStream bytes) throws IOException;

    /** The blob, its stream reading nothing until first used; empty when none is stored. */
    Optional<StoredBlob> open(UUID uploadId, String variant);

    boolean exists(UUID uploadId, String variant);
}
