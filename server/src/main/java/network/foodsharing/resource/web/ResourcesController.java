package network.foodsharing.resource.web;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.ResourcesApi;
import network.foodsharing.api.model.Language;
import network.foodsharing.api.model.Resource;
import network.foodsharing.api.model.ResourceCategory;
import network.foodsharing.api.model.ResourceCreate;
import network.foodsharing.api.model.ResourcePermissions;
import network.foodsharing.api.model.ResourceUpdate;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Contract stub (docs/plan-m6.md B1): every operation answers 501 until implemented. */
@RestController
public class ResourcesController implements ResourcesApi {

    @Override
    public ResponseEntity<Resource> createResource(ResourceCreate resourceCreate) {
        throw ApiException.notImplemented("createResource");
    }

    @Override
    public ResponseEntity<Void> deleteResource(UUID resourceId) {
        throw ApiException.notImplemented("deleteResource");
    }

    @Override
    public ResponseEntity<Void> favouriteResource(UUID resourceId) {
        throw ApiException.notImplemented("favouriteResource");
    }

    @Override
    public ResponseEntity<ResourcePermissions> getResourcePermissions(UUID regionId) {
        throw ApiException.notImplemented("getResourcePermissions");
    }

    @Override
    public ResponseEntity<List<Resource>> listFavouriteResources() {
        throw ApiException.notImplemented("listFavouriteResources");
    }

    @Override
    public ResponseEntity<List<Resource>> listMyResources() {
        throw ApiException.notImplemented("listMyResources");
    }

    @Override
    public ResponseEntity<List<Resource>> listRegionResources(UUID regionId) {
        throw ApiException.notImplemented("listRegionResources");
    }

    @Override
    public ResponseEntity<List<ResourceCategory>> listResourceCategories(Language lang) {
        throw ApiException.notImplemented("listResourceCategories");
    }

    @Override
    public ResponseEntity<Void> unfavouriteResource(UUID resourceId) {
        throw ApiException.notImplemented("unfavouriteResource");
    }

    @Override
    public ResponseEntity<Resource> updateResource(UUID resourceId, ResourceUpdate resourceUpdate) {
        throw ApiException.notImplemented("updateResource");
    }
}
