package network.foodsharing.resource.service;

import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.common.persistence.UserRows;
import network.foodsharing.history.HistoryAccessRule;
import network.foodsharing.history.SubjectType;
import network.foodsharing.resource.repository.ResourceRepository;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.upload.service.UploadAccessRule;
import network.foodsharing.user.service.AccountDeletionContributor;
import network.foodsharing.user.service.DataExportContributor;
import org.springframework.stereotype.Component;

/** SPI implementations of the resource mosaic (A40, A41, history access). */
final class ResourceContributors {

    private ResourceContributors() {
    }

    /** A40: own resources are soft-deleted, favourites removed; the export lists both. */
    @Component
    static class AccountData implements AccountDeletionContributor, DataExportContributor {

        private final ResourceService service;
        private final EntityManager em;

        AccountData(ResourceService service, EntityManager em) {
            this.service = service;
            this.em = em;
        }

        @Override
        public void onAccountDeleted(UUID userId) {
            service.removeUser(userId);
        }

        @Override
        public String section() {
            return "resources";
        }

        @Override
        public Object export(UUID userId) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("own", UserRows.read(em, "Resource", "ownerId", userId, "id", "name", "description",
                    "privateResource", "openness", "regionId", "createdAt", "deletedAt"));
            data.put("favourites", UserRows.read(em, "ResourceFavourite", "userId", userId, "resourceId",
                    "createdAt"));
            return data;
        }
    }

    /** Images of resources are public while the resource exists (A41, {@code RESOURCE_IMAGE}). */
    @Component
    static class Images implements UploadAccessRule {

        private final ResourceRepository resources;

        Images(ResourceRepository resources) {
            this.resources = resources;
        }

        @Override
        public String module() {
            return ResourceService.IMAGE_MODULE;
        }

        @Override
        public boolean mayRead(UUID userId, UUID usageOwnerId) {
            return true;
        }

        @Override
        public boolean exists(UUID usageOwnerId) {
            return resources.findByIdAndDeletedAtIsNull(usageOwnerId).isPresent();
        }
    }

    /** A resource's history: ORGA and the owner; commons: members of the region. */
    @Component
    static class HistoryAccess implements HistoryAccessRule {

        private final ResourceRepository resources;
        private final PermissionService permissions;

        HistoryAccess(ResourceRepository resources, PermissionService permissions) {
            this.resources = resources;
            this.permissions = permissions;
        }

        @Override
        public SubjectType subjectType() {
            return SubjectType.RESOURCE;
        }

        @Override
        public boolean canRead(SessionPrincipal actor, UUID subjectId) {
            return permissions.isOrga(actor) || resources.findById(subjectId).map(r -> r.isCommons()
                    ? r.getRegionId() != null && permissions.isMemberOf(actor, r.getRegionId())
                    : actor.userId().equals(r.getOwnerId())).orElse(false);
        }
    }
}
