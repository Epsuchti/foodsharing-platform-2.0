package network.foodsharing.admin.web;

import java.util.UUID;
import network.foodsharing.api.AdminUploadsApi;
import network.foodsharing.api.model.UploadAdminPage;
import network.foodsharing.api.model.UploadAdminSummary;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Contract stub (docs/plan-m6.md B1): every operation answers 501 until implemented. */
@RestController
public class AdminUploadsController implements AdminUploadsApi {

    @Override
    public ResponseEntity<Void> deleteAdminUpload(UUID uploadId) {
        throw ApiException.notImplemented("deleteAdminUpload");
    }

    @Override
    public ResponseEntity<UploadAdminSummary> getUploadSummary() {
        throw ApiException.notImplemented("getUploadSummary");
    }

    @Override
    public ResponseEntity<UploadAdminPage> listAdminUploads(Boolean unclaimed, String module, Integer page, Integer size) {
        throw ApiException.notImplemented("listAdminUploads");
    }
}
