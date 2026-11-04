package network.foodsharing.upload.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.config.AppProperties;
import network.foodsharing.testsupport.JpaSliceTest;
import network.foodsharing.upload.domain.Upload;
import network.foodsharing.upload.repository.UploadBlobRepository;
import network.foodsharing.upload.repository.UploadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * Decision 158: the one-time import of {@code /data/uploads} takes matching files (and their variant files when all are
 * there, else renders them), leaves missing and mismatching files alone and is idempotent.
 */
@JpaSliceTest
@Import({UploadService.class, DbUploadStorage.class, UploadBlobRepository.class})
class UploadDiskImportTest {

    @Autowired
    private UploadService uploads;
    @Autowired
    private UploadRepository repository;
    @Autowired
    private UploadBlobRepository blobs;
    @Autowired
    private UploadStorage storage;
    @Autowired
    private AppProperties properties;
    @Autowired
    private Clock clock;

    @TempDir
    Path dir;

    private UploadDiskImport importer;

    @BeforeEach
    void setUp() {
        importer = new UploadDiskImport(uploads, blobs, lock -> Optional.empty(), properties, clock);
    }

    private Upload row(String key, String mimeType, byte[] bytes) throws Exception {
        Upload upload = new Upload();
        upload.setFileName("f");
        upload.setMimeType(mimeType);
        upload.setSize(bytes.length);
        upload.setSha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        upload.setStorageKey(key);
        upload.setVariants(mimeType.startsWith("image/"));
        return repository.saveAndFlush(upload);
    }

    private void file(String key, byte[] bytes) throws Exception {
        Path path = dir.resolve("uploads").resolve(key);
        Files.createDirectories(path.getParent());
        Files.write(path, bytes);
    }

    private byte[] bytes(UUID id, String variant) throws Exception {
        try (InputStream in = storage.open(id, variant).orElseThrow().stream()) {
            return in.readAllBytes();
        }
    }

    @Test
    void importsMatchingFilesOnceAndLeavesTheRestAlone() throws Exception {
        byte[] pdf = "%PDF-1.4 import".getBytes();
        Upload plain = row("26/10/" + UUID.randomUUID(), "application/pdf", pdf);
        file(plain.getStorageKey(), pdf);

        byte[] png = ImageVariantsTest.image(200, 100, "png");
        Upload withVariants = row("26/10/" + UUID.randomUUID(), "image/png", png);
        file(withVariants.getStorageKey(), png);
        for (String size : uploads.variantSizes()) {
            file(ImageVariants.variantKey(withVariants.getStorageKey(), size), new byte[] {(byte) size.charAt(0)});
        }
        Upload rendered = row("26/11/" + UUID.randomUUID(), "image/png", png);
        file(rendered.getStorageKey(), png);
        file(ImageVariants.variantKey(rendered.getStorageKey(), "sm"), new byte[] {1}); // md and lg missing

        Upload missing = row("26/11/" + UUID.randomUUID(), "application/pdf", pdf);
        Upload mismatched = row("26/11/" + UUID.randomUUID(), "application/pdf", pdf);
        file(mismatched.getStorageKey(), "%PDF-1.4 changed".getBytes());
        Upload outside = row("../escape", "application/pdf", pdf);
        Files.write(dir.resolve("escape"), pdf);

        assertThat(importer.importFrom(dir.resolve("uploads"))).isEqualTo(new UploadDiskImport.Result(3, 2, 1));

        assertThat(bytes(plain.getId(), UploadStorage.ORIGINAL)).isEqualTo(pdf);
        assertThat(bytes(withVariants.getId(), UploadStorage.ORIGINAL)).isEqualTo(png);
        assertThat(bytes(withVariants.getId(), "md")).containsExactly('m');
        assertThat(bytes(rendered.getId(), "sm")).startsWith((byte) 0xFF, (byte) 0xD8); // rendered, not the file
        assertThat(rendered.isVariants()).isTrue();
        assertThat(storage.exists(missing.getId(), UploadStorage.ORIGINAL)).isFalse();
        assertThat(storage.exists(mismatched.getId(), UploadStorage.ORIGINAL)).isFalse();
        assertThat(storage.exists(outside.getId(), UploadStorage.ORIGINAL)).isFalse();
        assertThat(blobs.countMissingOriginals()).isEqualTo(3);

        file(missing.getStorageKey(), pdf); // copied later: a second run takes only it
        assertThat(importer.importFrom(dir.resolve("uploads"))).isEqualTo(new UploadDiskImport.Result(1, 1, 1));
        assertThat(blobs.countMissingOriginals()).isEqualTo(2);
    }

    @Test
    void aMissingDirectoryImportsNothing() {
        assertThat(importer.importFrom(dir.resolve("nope"))).isEqualTo(new UploadDiskImport.Result(0, 0, 0));
    }
}
