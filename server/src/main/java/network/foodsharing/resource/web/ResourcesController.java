package network.foodsharing.resource.web;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.ResourcesApi;
import network.foodsharing.api.model.Language;
import network.foodsharing.api.model.MyResourceLimits;
import network.foodsharing.api.model.Resource;
import network.foodsharing.api.model.ResourceCategory;
import network.foodsharing.api.model.ResourceCreate;
import network.foodsharing.api.model.ResourceImage;
import network.foodsharing.api.model.ResourceOpenness;
import network.foodsharing.api.model.ResourcePermissions;
import network.foodsharing.api.model.ResourceUpdate;
import network.foodsharing.resource.service.ResourceService;
import network.foodsharing.user.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Resource mosaic (C6). */
@RestController
public class ResourcesController implements ResourcesApi {

    private final ResourceService resources;
    private final UserMapper users;

    public ResourcesController(ResourceService resources, UserMapper users) {
        this.resources = resources;
        this.users = users;
    }

    @Override
    public ResponseEntity<Resource> createResource(ResourceCreate in) {
        ResourceService.View created = resources.create(new ResourceService.Input(in.getName(), in.getDescription(),
                in.getCategoryIds(), in.getPrivate(), openness(in.getOpenness()), in.getRegionId(),
                in.getImageUploadIds(), Boolean.TRUE.equals(in.getCommons())));
        return ResponseEntity.status(HttpStatus.CREATED).body(toApi(created));
    }

    @Override
    public ResponseEntity<Void> deleteResource(UUID resourceId) {
        resources.delete(resourceId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> favouriteResource(UUID resourceId) {
        resources.favourite(resourceId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ResourcePermissions> getResourcePermissions(UUID regionId) {
        ResourceService.Permissions p = resources.permissionsIn(regionId);
        return ResponseEntity.ok(new ResourcePermissions().mayAdd(p.mayAdd()).mayEditCommons(p.mayEditCommons())
                .maxPerUser(p.maxPerUser()));
    }

    @Override
    public ResponseEntity<List<Resource>> listFavouriteResources() {
        return ResponseEntity.ok(resources.favouriteResources().stream().map(this::toApi).toList());
    }

    @Override
    public ResponseEntity<MyResourceLimits> getMyResourceLimits() {
        ResourceService.Limits limits = resources.myLimits();
        return ResponseEntity.ok(new MyResourceLimits().ownCount((int) limits.ownCount())
                .maxPerUser(limits.maxPerUser()).mayAdd(limits.mayAdd()));
    }

    @Override
    public ResponseEntity<List<Resource>> listMyResources() {
        return ResponseEntity.ok(resources.mine().stream().map(this::toApi).toList());
    }

    @Override
    public ResponseEntity<List<Resource>> listRegionResources(UUID regionId) {
        return ResponseEntity.ok(resources.regionResources(regionId).stream().map(this::toApi).toList());
    }

    @Override
    public ResponseEntity<List<ResourceCategory>> listResourceCategories(Language lang) {
        return ResponseEntity.ok(resources.categories(lang == null ? "de" : lang.getValue()).stream()
                .map(ResourcesController::toApi).toList());
    }

    @Override
    public ResponseEntity<Void> unfavouriteResource(UUID resourceId) {
        resources.unfavourite(resourceId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Resource> updateResource(UUID resourceId, ResourceUpdate in) {
        return ResponseEntity.ok(toApi(resources.update(resourceId, new ResourceService.Input(in.getName(),
                in.getDescription(), in.getCategoryIds(), in.getPrivate(), openness(in.getOpenness()), null,
                in.getImageUploadIds(), false))));
    }

    private static network.foodsharing.resource.domain.ResourceOpenness openness(ResourceOpenness value) {
        return value == null ? null : network.foodsharing.resource.domain.ResourceOpenness.valueOf(value.getValue());
    }

    private static ResourceCategory toApi(ResourceService.Category c) {
        return new ResourceCategory().id(c.id()).name(c.name()).icon(c.icon());
    }

    private Resource toApi(ResourceService.View v) {
        var r = v.resource();
        return new Resource().id(r.getId()).name(r.getName()).description(r.getDescription())
                .categories(v.categories().stream().map(ResourcesController::toApi).toList())
                .owner(v.owner() == null ? null : users.toSummary(v.owner())).regionId(r.getRegionId())
                ._private(r.isPrivateResource()).openness(ResourceOpenness.fromValue(r.getOpenness().name()))
                .images(v.images().stream().map(i -> new ResourceImage().uploadId(i.uploadId()).url(i.url())
                        .fileName(i.fileName())).toList())
                .favourite(v.favourite()).mayEdit(v.mayEdit()).mayDelete(v.mayDelete());
    }
}
