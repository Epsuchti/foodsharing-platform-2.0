package network.foodsharing.upload.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.upload.repository.UploadBlobRepository;
import org.springframework.stereotype.Component;

/** Upload bytes in PostgreSQL ({@code upload_blob}/{@code upload_blob_chunk}, decision 158). */
@Component
public class DbUploadStorage implements UploadStorage {

    private final UploadBlobRepository blobs;
    private final Clock clock;

    public DbUploadStorage(UploadBlobRepository blobs, Clock clock) {
        this.blobs = blobs;
        this.clock = clock;
    }

    @Override
    public void write(UUID uploadId, String variant, String mimeType, InputStream bytes) throws IOException {
        blobs.insert(uploadId, variant, mimeType, bytes, clock.instant());
    }

    @Override
    public Optional<StoredBlob> open(UUID uploadId, String variant) {
        return blobs.find(uploadId, variant).map(info -> new StoredBlob(info.mimeType(), info.size(), info.sha256(),
                new ChunkedBlobInputStream(blobs, uploadId, variant, info.chunkCount())));
    }

    @Override
    public boolean exists(UUID uploadId, String variant) {
        return blobs.exists(uploadId, variant);
    }
}
