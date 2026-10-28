package network.foodsharing.upload.service;

import java.io.IOException;
import java.io.InputStream;

/** Where upload bytes live (A34). {@link DiskUploadStorage} is the only implementation for now. */
public interface UploadStorage {

    void write(String key, byte[] bytes) throws IOException;

    InputStream open(String key) throws IOException;

    void delete(String key) throws IOException;

    /** A stored file: its key and last modification. */
    record StoredFile(String key, java.time.Instant modifiedAt) {
    }

    /** Every stored file (orphan clean-up); keys use {@code /} as separator. */
    java.util.List<StoredFile> list() throws IOException;
}
