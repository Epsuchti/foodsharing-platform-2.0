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

    @Override
    public ResponseEntity<Resource> downloadUpload(UUID uploadId, String size) {
        UUID userId = currentActor.currentUserId().orElse(null);
        if (userId == null && !uploads.find(uploadId).isPublicReadable()) {
            throw ApiException.unauthenticated();
        }
        Upload upload = uploads.requireReadable(userId, userId != null && currentActor.isOrga(), uploadId);
        UploadService.Download download = uploads.open(upload, size);
        var response = ResponseEntity.ok();
        if (upload.isPublicReadable()) {
            response.header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400");
        }
        return response
                .contentType(MediaType.parseMediaType(download.mimeType()))
                .contentLength(download.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(upload.getFileName(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(download.stream()));
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
