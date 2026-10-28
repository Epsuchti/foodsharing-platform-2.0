package network.foodsharing.admin.service;

import java.util.UUID;
import network.foodsharing.admin.domain.AdminAction;
import network.foodsharing.api.model.Role;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.upload.domain.Upload;
import network.foodsharing.upload.service.UploadService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Upload administration (C10 admin): ORGA lists, summarises and deletes any upload (also its variant files). */
@Service
public class UploadAdminService {

    private final UploadService uploads;
    private final CurrentActorService actors;
    private final HistoryService history;

    public UploadAdminService(UploadService uploads, CurrentActorService actors, HistoryService history) {
        this.uploads = uploads;
        this.actors = actors;
        this.history = history;
    }

    @Transactional(readOnly = true)
    public Page<Upload> list(Boolean unclaimed, String module, Integer page, Integer size) {
        actors.requireRole(Role.ORGA);
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 50 : Math.min(200, Math.max(1, size));
        return uploads.adminPage(unclaimed, module, p, s);
    }

    @Transactional(readOnly = true)
    public UploadService.Summary summary() {
        actors.requireRole(Role.ORGA);
        return uploads.summary();
    }

    /** @throws network.foodsharing.common.error.ApiException 404 UPLOAD_NOT_FOUND */
    @Transactional
    public void delete(UUID uploadId) {
        actors.requireRole(Role.ORGA);
        Upload upload = uploads.find(uploadId);
        uploads.delete(upload);
        history.record(history.event(AdminAction.UPLOAD_DELETED_BY_ADMIN, uploadId)
                .metadata("fileName", upload.getFileName())
                .metadata("module", upload.getUsageModule() == null ? "" : upload.getUsageModule())
                .metadata("ownerUserId", upload.getOwnerUserId() == null ? "" : upload.getOwnerUserId().toString())
                .build());
    }
}
