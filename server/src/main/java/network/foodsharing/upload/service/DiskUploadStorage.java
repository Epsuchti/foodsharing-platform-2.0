package network.foodsharing.upload.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import network.foodsharing.config.AppProperties;
import org.springframework.stereotype.Component;

/** Stores upload bytes below {@code app.uploads.dir} as {@code <yy>/<mm>/<id>} (the key). */
@Component
public class DiskUploadStorage implements UploadStorage {

    private final Path root;

    public DiskUploadStorage(AppProperties properties) {
        this.root = Path.of(properties.getUploads().getDir()).toAbsolutePath().normalize();
    }

    @Override
    public void write(String key, byte[] bytes) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
    }

    @Override
    public InputStream open(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }
}
