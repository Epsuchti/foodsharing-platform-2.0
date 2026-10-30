package network.foodsharing.resource.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.admin.domain.FeatureToggle;
import network.foodsharing.admin.service.FeatureToggleService;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.resource.domain.Resource;
import network.foodsharing.resource.domain.ResourceAction;
import network.foodsharing.resource.domain.ResourceLinks;
import network.foodsharing.resource.domain.ResourceOpenness;
import network.foodsharing.resource.repository.ResourceBuddyLookup;
import network.foodsharing.resource.repository.ResourceCategoryLinkRepository;
import network.foodsharing.resource.repository.ResourceCategoryNameRepository;
import network.foodsharing.resource.repository.ResourceCategoryRepository;
import network.foodsharing.resource.repository.ResourceFavouriteRepository;
import network.foodsharing.resource.repository.ResourceImageRepository;
import network.foodsharing.resource.repository.ResourceRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.security.WorkingGroupFunction;
import network.foodsharing.upload.domain.Upload;
import network.foodsharing.upload.service.UploadService;
import network.foodsharing.upload.service.UploadUsage;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resource mosaic (C6, upstream ResourceMosaic): members lend things to other members of their city / working group;
 * commons belong to a region and are managed by its RESOURCES function group. Every operation needs the RESOURCES
 * toggle (404 FEATURE_DISABLED).
 */
@Service
public class ResourceService {

    public static final String IMAGE_MODULE = "RESOURCE_IMAGE";
    static final int MAX_IMAGES = 5;

    public record Category(UUID id, String name, String icon) {
    }

    public record Image(UUID uploadId, String url, String fileName) {
    }

    /** A resource prepared for display to one viewer. */
    public record View(Resource resource, List<Category> categories, User owner, List<Image> images, boolean favourite,
                       boolean mayEdit, boolean mayDelete) {
    }

    public record Input(String name, String description, List<UUID> categoryIds, Boolean privateResource,
                        ResourceOpenness openness, UUID regionId, List<UUID> imageUploadIds, boolean commons) {
    }

    public record Permissions(boolean mayAdd, boolean mayEditCommons, int maxPerUser) {
    }

    private final ResourceRepository resources;
    private final ResourceCategoryRepository categories;
    private final ResourceCategoryNameRepository categoryNames;
    private final ResourceCategoryLinkRepository categoryLinks;
    private final ResourceImageRepository images;
    private final ResourceFavouriteRepository favourites;
    private final ResourceBuddyLookup buddies;
    private final RegionService regions;
    private final UserService users;
    private final UploadService uploads;
    private final NotificationService notifications;
    private final FeatureToggleService toggles;
    private final CurrentActorService currentActor;
    private final PermissionService permissions;
    private final HistoryService history;
    private final AppProperties properties;
    private final Clock clock;

    public ResourceService(ResourceRepository resources, ResourceCategoryRepository categories,
                           ResourceCategoryNameRepository categoryNames, ResourceCategoryLinkRepository categoryLinks,
                           ResourceImageRepository images, ResourceFavouriteRepository favourites,
                           ResourceBuddyLookup buddies, RegionService regions, UserService users,
                           UploadService uploads, NotificationService notifications, FeatureToggleService toggles,
                           CurrentActorService currentActor, PermissionService permissions, HistoryService history,
                           AppProperties properties, Clock clock) {
        this.resources = resources;
        this.categories = categories;
        this.categoryNames = categoryNames;
        this.categoryLinks = categoryLinks;
        this.images = images;
        this.favourites = favourites;
        this.buddies = buddies;
        this.regions = regions;
        this.users = users;
        this.uploads = uploads;
        this.notifications = notifications;
        this.toggles = toggles;
        this.currentActor = currentActor;
        this.permissions = permissions;
        this.history = history;
        this.properties = properties;
        this.clock = clock;
    }

    /** Categories in the language (fallback German). */
    @Transactional(readOnly = true)
    public List<Category> categories(String language) {
        toggles.requireEnabled(FeatureToggle.RESOURCES);
        currentActor.requirePrincipal();
        return categoryList(language);
    }

    /** @throws ApiException 404 REGION_NOT_FOUND, 403 RESOURCES_NOT_AVAILABLE / ACCESS_DENIED */
    @Transactional(readOnly = true)
    public List<View> regionResources(UUID regionId) {
        SessionPrincipal principal = requireViewer(regionId);
        Set<UUID> regionScope = new HashSet<>();
        regions.ancestors(regionId).forEach(r -> regionScope.add(r.getId()));
        regionScope.add(regionId);
        List<UUID> members = regions.activeMemberIds(regionId);
        Set<UUID> buddyIds = buddies.confirmedBuddies(principal.userId());
        List<Resource> visible = new ArrayList<>(resources.findByOwnerIdIsNullAndRegionIdAndDeletedAtIsNullOrderByName(
                regionId));
        if (!members.isEmpty()) {
            resources.findOwnedIn(members, regionScope).stream()
                    .filter(r -> ResourceRules.visibleTo(r, principal.userId(), buddyIds)).forEach(visible::add);
        }
        return views(visible, principal);
    }

    /** @throws ApiException 404 REGION_NOT_FOUND, 403 RESOURCES_NOT_AVAILABLE / ACCESS_DENIED */
    @Transactional(readOnly = true)
    public Permissions permissionsIn(UUID regionId) {
        SessionPrincipal principal = requireViewer(regionId);
        return new Permissions(ResourceRules.mayAdd(resources.countByOwnerIdAndDeletedAtIsNull(principal.userId()),
                properties.getResources().getMaxPerUser()), mayEditCommons(principal, regionId),
                properties.getResources().getMaxPerUser());
    }

    @Transactional(readOnly = true)
    public List<View> mine() {
        toggles.requireEnabled(FeatureToggle.RESOURCES);
        SessionPrincipal principal = currentActor.requirePrincipal();
        return views(resources.findByOwnerIdAndDeletedAtIsNullOrderByName(principal.userId()), principal);
    }

    @Transactional(readOnly = true)
    public List<View> favouriteResources() {
        toggles.requireEnabled(FeatureToggle.RESOURCES);
        SessionPrincipal principal = currentActor.requirePrincipal();
        Set<UUID> ids = favourites.findByUserId(principal.userId()).stream()
                .map(ResourceLinks.Favourite::getResourceId).collect(Collectors.toSet());
        Set<UUID> buddyIds = buddies.confirmedBuddies(principal.userId());
        return views(ids.isEmpty() ? List.of() : resources.findByIdInAndDeletedAtIsNullOrderByName(ids).stream()
                .filter(r -> ResourceRules.visibleTo(r, principal.userId(), buddyIds)).toList(), principal);
    }

    /**
     * @throws ApiException 404 FEATURE_DISABLED / REGION_NOT_FOUND, 409 RESOURCE_LIMIT_REACHED, 403 ACCESS_DENIED
     *                      (commons without the RESOURCES function, foreign upload) / RESOURCES_NOT_AVAILABLE, 400
     *                      VALIDATION_FAILED (unknown category, commons without region)
     */
    @Transactional
    public View create(Input in) {
        toggles.requireEnabled(FeatureToggle.RESOURCES);
        SessionPrincipal principal = currentActor.requirePrincipal();
        Resource resource = new Resource();
        if (in.commons()) {
            if (in.regionId() == null) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
            }
            requireViewer(in.regionId());
            if (!mayEditCommons(principal, in.regionId())) {
                throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
            }
        } else {
            if (!ResourceRules.mayAdd(resources.countByOwnerIdAndDeletedAtIsNull(principal.userId()),
                    properties.getResources().getMaxPerUser())) {
                throw ApiException.conflict(ApiErrorCode.RESOURCE_LIMIT_REACHED);
            }
            if (in.regionId() != null) {
                requireViewer(in.regionId());
            }
            resource.setOwnerId(principal.userId());
        }
        resource.setRegionId(in.regionId());
        resource.setName(in.name().trim());
        resource.setDescription(blankToNull(in.description()));
        resource.setPrivateResource(!in.commons() && Boolean.TRUE.equals(in.privateResource()));
        resource.setOpenness(in.openness() == null ? ResourceOpenness.ASK_FIRST : in.openness());
        Resource saved = resources.save(resource);
        setCategories(saved, in.categoryIds());
        setImages(saved, in.imageUploadIds() == null ? List.of() : in.imageUploadIds(), principal.userId());
        history.record(history.event(ResourceAction.CREATED, saved.getId()).region(saved.getRegionId())
                .metadata(Map.of("name", saved.getName(), "commons", saved.isCommons())).build());
        if (saved.isCommons()) {
            notifyCommons(saved, principal.userId());
        }
        return view(saved, principal);
    }

    /** @throws ApiException 404 RESOURCE_NOT_FOUND / FEATURE_DISABLED, 403 ACCESS_DENIED, 400 VALIDATION_FAILED */
    @Transactional
    public View update(UUID resourceId, Input in) {
        toggles.requireEnabled(FeatureToggle.RESOURCES);
        SessionPrincipal principal = currentActor.requirePrincipal();
        Resource resource = require(resourceId);
        if (!ResourceRules.mayEdit(resource, principal.userId(), resource.isCommons()
                && mayEditCommons(principal, resource.getRegionId()))) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        if (in.name() != null && !in.name().equals(resource.getName())) {
            history.record(history.event(ResourceAction.UPDATED, resource.getId()).region(resource.getRegionId())
                    .change("name", resource.getName(), in.name().trim()).build());
            resource.setName(in.name().trim());
        }
        if (in.description() != null) {
            resource.setDescription(blankToNull(in.description()));
        }
        if (in.privateResource() != null && !resource.isCommons()) {
            resource.setPrivateResource(in.privateResource());
        }
        if (in.openness() != null && in.openness() != resource.getOpenness()) {
            history.record(history.event(ResourceAction.UPDATED, resource.getId()).region(resource.getRegionId())
                    .change("openness", resource.getOpenness(), in.openness()).build());
            resource.setOpenness(in.openness());
        }
        if (in.categoryIds() != null) {
            Set<UUID> before = categoryLinks.findByResourceId(resourceId).stream()
                    .map(ResourceLinks.CategoryLink::getCategoryId).collect(Collectors.toSet());
            if (!before.equals(new HashSet<>(in.categoryIds()))) {
                setCategories(resource, in.categoryIds());
                history.record(history.event(ResourceAction.CATEGORY_CHANGED, resource.getId())
                        .region(resource.getRegionId()).build());
            }
        }
        if (in.imageUploadIds() != null) {
            setImages(resource, in.imageUploadIds(), principal.userId());
        }
        history.record(history.event(ResourceAction.UPDATED, resource.getId()).region(resource.getRegionId())
                .build());
        return view(resources.save(resource), principal);
    }

    /** Soft delete; images go with the upload cleanup. @throws ApiException 404, 403 ACCESS_DENIED */
    @Transactional
    public void delete(UUID resourceId) {
        toggles.requireEnabled(FeatureToggle.RESOURCES);
        SessionPrincipal principal = currentActor.requirePrincipal();
        Resource resource = require(resourceId);
        if (!ResourceRules.mayDelete(resource, principal.userId(), permissions.isOrga(principal),
                resource.isCommons() && mayEditCommons(principal, resource.getRegionId()))) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        resource.setDeletedAt(clock.instant());
        favourites.deleteAll(favourites.findByResourceId(resourceId));
        history.record(history.event(ResourceAction.DELETED, resource.getId()).region(resource.getRegionId())
                .metadata(Map.of("name", resource.getName())).build());
    }

    /** Idempotent. @throws ApiException 404 RESOURCE_NOT_FOUND (also for invisible private resources) */
    @Transactional
    public void favourite(UUID resourceId) {
        toggles.requireEnabled(FeatureToggle.RESOURCES);
        SessionPrincipal principal = currentActor.requirePrincipal();
        Resource resource = require(resourceId);
        if (!ResourceRules.visibleTo(resource, principal.userId(), buddies.confirmedBuddies(principal.userId()))) {
            throw ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND);
        }
        if (favourites.findByUserIdAndResourceId(principal.userId(), resourceId).isEmpty()) {
            favourites.save(new ResourceLinks.Favourite(principal.userId(), resourceId));
            history.record(history.event(ResourceAction.FAVOURITED, resourceId).region(resource.getRegionId())
                    .build());
        }
    }

    /** Idempotent. @throws ApiException 404 RESOURCE_NOT_FOUND */
    @Transactional
    public void unfavourite(UUID resourceId) {
        toggles.requireEnabled(FeatureToggle.RESOURCES);
        SessionPrincipal principal = currentActor.requirePrincipal();
        Resource resource = require(resourceId);
        favourites.findByUserIdAndResourceId(principal.userId(), resourceId).ifPresent(f -> {
            favourites.delete(f);
            history.record(history.event(ResourceAction.UNFAVOURITED, resourceId).region(resource.getRegionId())
                    .build());
        });
    }

    private SessionPrincipal requireViewer(UUID regionId) {
        toggles.requireEnabled(FeatureToggle.RESOURCES);
        SessionPrincipal principal = currentActor.requirePrincipal();
        Region region = regions.require(regionId);
        if (!ResourceRules.hasResources(region.getType())) {
            throw ApiException.forbidden(ApiErrorCode.RESOURCES_NOT_AVAILABLE);
        }
        if (!ResourceRules.maySee(region.getType(), permissions.isMemberOf(principal, regionId))
                && !permissions.isOrga(principal)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        return principal;
    }

    private boolean mayEditCommons(SessionPrincipal principal, UUID regionId) {
        return regionId != null && permissions.hasFunction(principal, WorkingGroupFunction.RESOURCES, regionId);
    }

    private Resource require(UUID resourceId) {
        return resources.findByIdAndDeletedAtIsNull(resourceId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND));
    }

    private void setCategories(Resource resource, List<UUID> categoryIds) {
        Set<UUID> wanted = new LinkedHashSet<>(categoryIds == null ? List.of() : categoryIds);
        if (wanted.isEmpty() || wanted.size() > 5 || categories.findAllById(wanted).size() != wanted.size()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        categoryLinks.deleteAll(categoryLinks.findByResourceId(resource.getId()));
        categoryLinks.flush();
        wanted.forEach(id -> categoryLinks.save(new ResourceLinks.CategoryLink(resource.getId(), id)));
    }

    private void setImages(Resource resource, List<UUID> uploadIds, UUID actorId) {
        List<UUID> wanted = new ArrayList<>(new LinkedHashSet<>(uploadIds));
        if (wanted.size() > MAX_IMAGES) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        List<ResourceLinks.Image> current = images.findByResourceId(resource.getId());
        Set<UUID> kept = new HashSet<>();
        for (ResourceLinks.Image image : current) {
            if (wanted.contains(image.getUploadId())) {
                kept.add(image.getUploadId());
                image.setSortOrder(wanted.indexOf(image.getUploadId()));
            } else {
                images.delete(image);
                try {
                    uploads.delete(uploads.find(image.getUploadId()));
                } catch (ApiException alreadyGone) {
                    // nothing to delete
                }
            }
        }
        for (int i = 0; i < wanted.size(); i++) {
            UUID uploadId = wanted.get(i);
            if (kept.contains(uploadId)) {
                continue;
            }
            Upload upload = uploads.find(uploadId);
            if (!actorId.equals(upload.getOwnerUserId())) {
                throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
            }
            uploads.claim(uploadId, new UploadUsage(IMAGE_MODULE, resource.getId()));
            images.save(new ResourceLinks.Image(resource.getId(), uploadId, i));
        }
    }

    private void notifyCommons(Resource resource, UUID actorId) {
        Set<UUID> recipients = new HashSet<>(regions.activeMemberIds(resource.getRegionId()));
        recipients.remove(actorId);
        if (recipients.isEmpty()) {
            return;
        }
        String identifier = "NEW_RESOURCE-" + resource.getRegionId();
        Region region = regions.require(resource.getRegionId());
        notifications.removeBells(identifier);
        notifications.notify(NotificationRequest.to(recipients, NotificationType.NEW_RESOURCE)
                .param("name", resource.getName()).param("region", region.getName())
                .link("/regions/" + resource.getRegionId() + "/resources").identifier(identifier).build());
    }

    private View view(Resource resource, SessionPrincipal principal) {
        return views(List.of(resource), principal).getFirst();
    }

    private List<View> views(List<Resource> list, SessionPrincipal principal) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = list.stream().map(Resource::getId).toList();
        String language = users.findActive(principal.userId()).map(u -> u.getPreferredLanguage().getValue())
                .orElse("de");
        Map<UUID, Category> categoryById = categoryList(language).stream()
                .collect(Collectors.toMap(Category::id, Function.identity()));
        Map<UUID, List<UUID>> categoryIds = categoryLinks.findByResourceIdIn(ids).stream().collect(Collectors.groupingBy(
                ResourceLinks.CategoryLink::getResourceId,
                Collectors.mapping(ResourceLinks.CategoryLink::getCategoryId, Collectors.toList())));
        List<ResourceLinks.Image> imageRows = images.findByResourceIdInOrderBySortOrder(ids);
        Map<UUID, String> fileNames = uploads.fileNames(imageRows.stream().map(ResourceLinks.Image::getUploadId)
                .collect(Collectors.toSet()));
        Map<UUID, List<Image>> imageMap = imageRows.stream().collect(
                Collectors.groupingBy(ResourceLinks.Image::getResourceId, Collectors.mapping(
                        i -> new Image(i.getUploadId(), "/api/uploads/" + i.getUploadId(),
                                fileNames.get(i.getUploadId())), Collectors.toList())));
        Set<UUID> favs = favourites.findByUserIdAndResourceIdIn(principal.userId(), ids).stream()
                .map(ResourceLinks.Favourite::getResourceId).collect(Collectors.toSet());
        Map<UUID, User> owners = users.findAll(list.stream().map(Resource::getOwnerId)
                        .filter(java.util.Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        boolean orga = permissions.isOrga(principal);
        List<View> result = new ArrayList<>();
        for (Resource r : list) {
            boolean commonsEditor = r.isCommons() && mayEditCommons(principal, r.getRegionId());
            List<Category> cats = categoryIds.getOrDefault(r.getId(), List.of()).stream().map(categoryById::get)
                    .filter(java.util.Objects::nonNull).toList();
            result.add(new View(r, cats, r.getOwnerId() == null ? null : owners.get(r.getOwnerId()),
                    imageMap.getOrDefault(r.getId(), List.of()), favs.contains(r.getId()),
                    ResourceRules.mayEdit(r, principal.userId(), commonsEditor),
                    ResourceRules.mayDelete(r, principal.userId(), orga, commonsEditor)));
        }
        return result;
    }

    private List<Category> categoryList(String language) {
        List<ResourceLinks.Category> all = categories.findAllByOrderBySortOrder();
        Map<UUID, Map<String, String>> names = categoryNames.findByCategoryIdIn(all.stream()
                        .map(ResourceLinks.Category::getId).toList()).stream()
                .collect(Collectors.groupingBy(ResourceLinks.CategoryName::getCategoryId,
                        Collectors.toMap(ResourceLinks.CategoryName::getLanguage, ResourceLinks.CategoryName::getName)));
        return all.stream().map(c -> {
            Map<String, String> byLang = names.getOrDefault(c.getId(), Map.of());
            String name = byLang.getOrDefault(language, byLang.getOrDefault("de", ""));
            return new Category(c.getId(), name, c.getIcon());
        }).toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** A40: the account's resources are deleted (soft) and its favourites removed. */
    @Transactional
    public int removeUser(UUID userId) {
        int count = 0;
        for (Resource resource : resources.findByOwnerId(userId)) {
            if (resource.getDeletedAt() == null) {
                resource.setDeletedAt(clock.instant());
                favourites.deleteAll(favourites.findByResourceId(resource.getId()));
                history.record(history.event(ResourceAction.DELETED, resource.getId()).region(resource.getRegionId())
                        .reason("account deleted").build());
                count++;
            }
        }
        for (ResourceLinks.Favourite favourite : favourites.findByUserId(userId)) {
            favourites.delete(favourite);
            history.record(history.event(ResourceAction.UNFAVOURITED, favourite.getResourceId())
                    .reason("account deleted").build());
        }
        return count;
    }
}
