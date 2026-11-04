package network.foodsharing.upload.web;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import network.foodsharing.api.UploadsApi;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.upload.domain.Upload;
import network.foodsharing.upload.service.UploadService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import network.foodsharing.common.error.ApiException;

/** Uploads (A34) with image variants and public reads (A41). */
@RestController
public class UploadsController implements UploadsApi {

    private final UploadService uploads;
    private final CurrentActorService currentActor;

    public UploadsController(UploadService uploads, CurrentActorService currentActor) {
        this.uploads = uploads;
        this.currentActor = currentActor;
    }

    @Override
    public ResponseEntity<network.foodsharing.api.model.Upload> createUpload(MultipartFile file) {
        UUID userId = currentActor.requireUserId();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Upload saved = uploads.store(userId, new UploadService.NewFile(file.getOriginalFilename(), file.getContentType(), bytes));
        return ResponseEntity.status(HttpStatus.CREATED).body(toApi(saved));
    }

    /** Public uploads (A41): browsers and proxies keep them for a week; content per URL never changes. */
    static final String CACHE_PUBLIC = "public, max-age=604800, immutable";
    /** Everything else: only the browser keeps it and revalidates (ETag) on every use. */
    static final String CACHE_PRIVATE = "private, no-cache";

    @Override
    public ResponseEntity<Resource> downloadUpload(UUID uploadId, String size, String ifNoneMatch) {
        UUID userId = currentActor.currentUserId().orElse(null);
        if (userId == null && !uploads.find(uploadId).isPublicReadable()) {
            throw ApiException.unauthenticated();
        }
        Upload upload = uploads.requireReadable(userId, userId != null && currentActor.isOrga(), uploadId);
        UploadService.Download download = uploads.open(upload, size);
        String etag = etag(download.sha256());
        String cacheControl = cacheControl(upload.isPublicReadable());
        if (notModified(ifNoneMatch, etag)) { // nothing of the blob is read
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag)
                    .header(HttpHeaders.CACHE_CONTROL, cacheControl).build();
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .header(HttpHeaders.CACHE_CONTROL, cacheControl)
                .contentType(MediaType.parseMediaType(download.mimeType()))
                .contentLength(download.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(upload.getFileName(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(download.stream()));
    }

    /** Strong ETag of a blob: its quoted sha256 (decision 158). */
    static String etag(String sha256) {
        return "\"" + sha256 + "\"";
    }

    static String cacheControl(boolean publicReadable) {
        return publicReadable ? CACHE_PUBLIC : CACHE_PRIVATE;
    }

    /** Whether {@code If-None-Match} (a list, {@code *} or weak tags) matches {@code etag} (RFC 9110 weak comparison). */
    static boolean notModified(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        for (String candidate : ifNoneMatch.split(",")) {
            String tag = candidate.strip();
            if (tag.equals("*")) {
                return true;
            }
            if (tag.startsWith("W/")) {
                tag = tag.substring(2);
            }
            if (tag.equals(etag)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ResponseEntity<Void> deleteUpload(UUID uploadId) {
        uploads.deleteOwn(currentActor.requireUserId(), uploadId);
        return ResponseEntity.noContent().build();
    }

    static network.foodsharing.api.model.Upload toApi(Upload upload) {
        return new network.foodsharing.api.model.Upload().id(upload.getId()).fileName(upload.getFileName())
                .mimeType(upload.getMimeType()).size(upload.getSize()).url("/api/uploads/" + upload.getId())
                .width(upload.getWidth()).height(upload.getHeight())
                .publicUrl(upload.isPublicReadable() ? "/api/uploads/" + upload.getId() : null);
    }
}
