package network.foodsharing.upload.repository;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Upload bytes in {@code upload_blob} + {@code upload_blob_chunk} (decision 158): {@value #CHUNK_SIZE} bytes per chunk
 * row, so neither a write nor a read ever holds more than one chunk (plus the caller's bytes) in memory. The only
 * place that reads or writes the {@code bytea} column; no entity maps it. Blobs go with their {@code upload} row
 * ({@code ON DELETE CASCADE}).
 */
@Repository
public class UploadBlobRepository {

    /** 1 MiB per chunk row. */
    public static final int CHUNK_SIZE = 1024 * 1024;

    /** Metadata of a stored blob. */
    public record BlobInfo(String mimeType, long size, String sha256, int chunkCount) {
    }

    private final JdbcClient jdbc;

    public UploadBlobRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Stores {@code in} as the blob of ({@code uploadId}, {@code variant}), replacing an existing one; splits it into
     * chunks and computes size and sha256 while reading. Joins the caller's transaction.
     */
    @Transactional
    public BlobInfo insert(UUID uploadId, String variant, String mimeType, InputStream in, Instant now)
            throws IOException {
        delete(uploadId, variant);
        jdbc.sql("insert into upload_blob (upload_id, variant, mime_type, size, sha256, chunk_count, created_at) "
                        + "values (:id, :variant, :mime, 0, '', 0, :now)")
                .param("id", uploadId).param("variant", variant).param("mime", mimeType)
                .param("now", Timestamp.from(now)).update();
        MessageDigest digest = sha256();
        long size = 0;
        int chunkNo = 0;
        byte[] chunk;
        while ((chunk = in.readNBytes(CHUNK_SIZE)).length > 0) {
            digest.update(chunk);
            size += chunk.length;
            jdbc.sql("insert into upload_blob_chunk (upload_id, variant, chunk_no, bytes) "
                            + "values (:id, :variant, :no, :bytes)")
                    .param("id", uploadId).param("variant", variant).param("no", chunkNo).param("bytes", chunk)
                    .update();
            chunkNo++;
        }
        String sha = HexFormat.of().formatHex(digest.digest());
        jdbc.sql("update upload_blob set size = :size, sha256 = :sha, chunk_count = :count "
                        + "where upload_id = :id and variant = :variant")
                .param("size", size).param("sha", sha).param("count", chunkNo)
                .param("id", uploadId).param("variant", variant).update();
        return new BlobInfo(mimeType, size, sha, chunkNo);
    }

    public Optional<BlobInfo> find(UUID uploadId, String variant) {
        return jdbc.sql("select mime_type, size, sha256, chunk_count from upload_blob "
                        + "where upload_id = :id and variant = :variant")
                .param("id", uploadId).param("variant", variant)
                .query((rs, n) -> new BlobInfo(rs.getString(1), rs.getLong(2), rs.getString(3), rs.getInt(4)))
                .optional();
    }

    /** Chunk {@code chunkNo} (from 0); empty when the blob was replaced or deleted meanwhile. */
    public Optional<byte[]> chunk(UUID uploadId, String variant, int chunkNo) {
        return jdbc.sql("select bytes from upload_blob_chunk where upload_id = :id and variant = :variant "
                        + "and chunk_no = :no")
                .param("id", uploadId).param("variant", variant).param("no", chunkNo)
                .query((rs, n) -> rs.getBytes(1)).optional();
    }

    public boolean exists(UUID uploadId, String variant) {
        return jdbc.sql("select count(*) from upload_blob where upload_id = :id and variant = :variant")
                .param("id", uploadId).param("variant", variant).query(Long.class).single() > 0;
    }

    /** Upload rows without an original blob (the cut-over check of the disk import must show 0). */
    public long countMissingOriginals() {
        return jdbc.sql("select count(*) from upload u where not exists (select 1 from upload_blob b "
                + "where b.upload_id = u.id and b.variant = 'orig')").query(Long.class).single();
    }

    /** Ids of upload rows without an original blob, ordered by id, after {@code after} (null = from the start). */
    public java.util.List<UUID> idsWithoutOriginal(UUID after, int limit) {
        String sql = "select u.id from upload u where not exists (select 1 from upload_blob b "
                + "where b.upload_id = u.id and b.variant = 'orig')" + (after == null ? "" : " and u.id > :after")
                + " order by u.id limit :limit";
        var statement = jdbc.sql(sql).param("limit", limit);
        if (after != null) {
            statement = statement.param("after", after);
        }
        return statement.query(UUID.class).list();
    }

    private void delete(UUID uploadId, String variant) {
        jdbc.sql("delete from upload_blob where upload_id = :id and variant = :variant")
                .param("id", uploadId).param("variant", variant).update();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
