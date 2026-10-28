package network.foodsharing.admin.web;

import java.util.UUID;
import network.foodsharing.admin.mapper.AdminSettingsMapper;
import network.foodsharing.admin.service.UploadAdminService;
import network.foodsharing.api.AdminUploadsApi;
import network.foodsharing.api.model.UploadAdminPage;
import network.foodsharing.api.model.UploadAdminSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Upload administration (C10, ORGA). */
@RestController
public class AdminUploadsController implements AdminUploadsApi {

    private final UploadAdminService uploads;
    private final AdminSettingsMapper mapper;

    public AdminUploadsController(UploadAdminService uploads, AdminSettingsMapper mapper) {
        this.uploads = uploads;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<UploadAdminPage> listAdminUploads(Boolean unclaimed, String module, Integer page,
                                                            Integer size) {
        return ResponseEntity.ok(mapper.toUploadPage(uploads.list(unclaimed, module, page, size)));
    }

    @Override
    public ResponseEntity<UploadAdminSummary> getUploadSummary() {
        return ResponseEntity.ok(mapper.toUploadSummary(uploads.summary()));
    }

    @Override
    public ResponseEntity<Void> deleteAdminUpload(UUID uploadId) {
        uploads.delete(uploadId);
        return ResponseEntity.noContent().build();
    }
}
