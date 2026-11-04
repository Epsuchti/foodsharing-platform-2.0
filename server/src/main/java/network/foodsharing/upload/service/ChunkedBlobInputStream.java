package network.foodsharing.upload.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;
import network.foodsharing.upload.repository.UploadBlobRepository;

/**
 * Reads a blob chunk by chunk (decision 158): one query per chunk, nothing before the first read, at most one chunk
 * in memory. Outside a transaction every query borrows a connection only for itself, so a slow download holds none.
 */
final class ChunkedBlobInputStream extends InputStream {

    private final UploadBlobRepository blobs;
    private final UUID uploadId;
    private final String variant;
    private final int chunkCount;
    private int nextChunk;
    private byte[] current = new byte[0];
    private int position;
    private boolean closed;

    ChunkedBlobInputStream(UploadBlobRepository blobs, UUID uploadId, String variant, int chunkCount) {
        this.blobs = blobs;
        this.uploadId = uploadId;
        this.variant = variant;
        this.chunkCount = chunkCount;
    }

    @Override
    public int read() throws IOException {
        if (!fill()) {
            return -1;
        }
        return current[position++] & 0xFF;
    }

    @Override
    public int read(byte[] target, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, target.length);
        if (length == 0) {
            return 0;
        }
        if (!fill()) {
            return -1;
        }
        int count = Math.min(length, current.length - position);
        System.arraycopy(current, position, target, offset, count);
        position += count;
        return count;
    }

    @Override
    public int available() {
        return closed ? 0 : current.length - position;
    }

    @Override
    public void close() {
        closed = true;
        current = new byte[0];
        position = 0;
    }

    /** Loads the next chunk when the current one is used up; false at the end. */
    private boolean fill() throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }
        while (position >= current.length) {
            if (nextChunk >= chunkCount) {
                return false;
            }
            int chunkNo = nextChunk++;
            current = blobs.chunk(uploadId, variant, chunkNo).orElseThrow(() -> new IOException(
                    "Chunk " + chunkNo + " of upload " + uploadId + "/" + variant + " is missing"));
            position = 0;
        }
        return true;
    }
}
