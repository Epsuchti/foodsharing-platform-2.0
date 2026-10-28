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

    @Override
    public java.util.List<StoredFile> list() throws IOException {
        if (!Files.isDirectory(root)) {
            return java.util.List.of();
        }
        try (java.util.stream.Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).map(path -> {
                try {
                    return new StoredFile(root.relativize(path).toString().replace(java.io.File.separatorChar, '/'),
                            Files.getLastModifiedTime(path).toInstant());
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }).toList();
        }
    }

    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }
}
