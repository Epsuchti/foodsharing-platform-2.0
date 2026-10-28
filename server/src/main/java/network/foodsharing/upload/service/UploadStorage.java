package network.foodsharing.upload.service;

import java.io.IOException;
import java.io.InputStream;

/** Where upload bytes live (A34). {@link DiskUploadStorage} is the only implementation for now. */
public interface UploadStorage {

    void write(String key, byte[] bytes) throws IOException;

    InputStream open(String key) throws IOException;

    void delete(String key) throws IOException;
}
