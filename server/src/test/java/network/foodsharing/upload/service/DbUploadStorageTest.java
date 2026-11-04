package network.foodsharing.upload.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Random;
import java.util.UUID;
import network.foodsharing.testsupport.JpaSliceTest;
import network.foodsharing.upload.domain.Upload;
import network.foodsharing.upload.repository.UploadBlobRepository;
import network.foodsharing.upload.repository.UploadRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** Decision 158: bytes split into 1 MiB chunk rows and joined again, with size and sha256; lazy chunk reads. */
@JpaSliceTest
@Import({DbUploadStorage.class, UploadBlobRepository.class})
class DbUploadStorageTest {

    private static final int MIB = UploadBlobRepository.CHUNK_SIZE;

    @Autowired
    private DbUploadStorage storage;

    @Autowired
    private UploadBlobRepository blobs;

    @Autowired
    private UploadRepository uploads;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void chunkColumnIsBinaryVaryingNotALobOnH2() {
        assertThat(jdbc.queryForObject("select data_type from information_schema.columns "
                + "where lower(table_name) = 'upload_blob_chunk' and lower(column_name) = 'bytes'", String.class))
                .isEqualTo("BINARY VARYING");
    }

    private UUID row() {
        Upload upload = new Upload();
        upload.setFileName("f.bin");
        upload.setMimeType("application/octet-stream");
        upload.setSha256("-");
        upload.setStorageKey("test/" + UUID.randomUUID());
        return uploads.saveAndFlush(upload).getId();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, MIB - 1, MIB, MIB + 1, 20 * 1024 * 1024})
    void splitsAndJoinsAtChunkBoundaries(int length) throws Exception {
        byte[] bytes = new byte[length];
        new Random(length).nextBytes(bytes);
        UUID id = row();
        storage.write(id, UploadStorage.ORIGINAL, "application/pdf", new ByteArrayInputStream(bytes));

        UploadBlobRepository.BlobInfo info = blobs.find(id, UploadStorage.ORIGINAL).orElseThrow();
        assertThat(info.size()).isEqualTo(length);
        assertThat(info.chunkCount()).isEqualTo((length + MIB - 1) / MIB);
        assertThat(info.sha256()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        assertThat(info.mimeType()).isEqualTo("application/pdf");
        UploadStorage.StoredBlob blob = storage.open(id, UploadStorage.ORIGINAL).orElseThrow();
        try (InputStream in = blob.stream()) {
            assertThat(in.readAllBytes()).isEqualTo(bytes);
        }
        if (length > MIB) {
            assertThat(blobs.chunk(id, UploadStorage.ORIGINAL, 0).orElseThrow()).hasSize(MIB);
        }
    }

    @Test
    void singleByteReadsAndReplacement() throws Exception {
        UUID id = row();
        storage.write(id, "sm", "image/jpeg", new ByteArrayInputStream(new byte[] {(byte) 0xFF, 2}));
        try (InputStream in = storage.open(id, "sm").orElseThrow().stream()) {
            assertThat(in.read()).isEqualTo(0xFF);
            assertThat(in.read()).isEqualTo(2);
            assertThat(in.read()).isEqualTo(-1);
        }
        storage.write(id, "sm", "image/jpeg", new ByteArrayInputStream(new byte[] {7}));
        assertThat(storage.open(id, "sm").orElseThrow().size()).isEqualTo(1);
        assertThat(storage.exists(id, "sm")).isTrue();
        assertThat(storage.exists(id, "md")).isFalse();
        assertThat(storage.open(id, "md")).isEmpty();
    }

    @Test
    void aStreamWhoseChunksVanishedFails() throws Exception {
        UUID id = row();
        storage.write(id, UploadStorage.ORIGINAL, "text/plain", new ByteArrayInputStream(new byte[] {1}));
        InputStream stream = storage.open(id, UploadStorage.ORIGINAL).orElseThrow().stream(); // reads nothing yet
        uploads.deleteById(id);
        uploads.flush();
        assertThatThrownBy(stream::read).isInstanceOf(IOException.class).hasMessageContaining("missing");
    }
}
