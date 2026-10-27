package network.foodsharing.region.service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.error.ApiFieldError;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.region.domain.ApplyType;
import network.foodsharing.region.domain.GroupAction;
import network.foodsharing.region.domain.GroupCategory;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.domain.RegionAction;
import network.foodsharing.region.domain.RegionAdmin;
import network.foodsharing.region.domain.RegionFunction;
import network.foodsharing.region.domain.RegionJoinMode;
import network.foodsharing.region.domain.RegionMembership;
import network.foodsharing.region.domain.UnitType;
import network.foodsharing.region.repository.RegionAdminRepository;
import network.foodsharing.region.repository.RegionFunctionRepository;
import network.foodsharing.region.repository.RegionMembershipRepository;
import network.foodsharing.region.repository.RegionRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.WorkingGroupFunction;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Region administration (plan §13.2 R7, ORGA only like upstream {@code mayAdministrateRegions}): create, edit, move,
 * delete regions and working groups, assign ambassadors and working-group functions. Slugs are generated from the
 * name and unique; email names match {@code ^[a-z0-9.-]{3,50}$} and are unique; a function is held by at most one
 * group per region.
 */
@Service
public class RegionAdminService {

    static final Pattern EMAIL_NAME = Pattern.compile("^[a-z0-9.-]{3,50}$");

    public record FunctionInput(WorkingGroupFunction function, UUID groupId) {
    }

    /** Create / update input; on update null = unchanged. */
    public record RegionInput(String name, String slug, UnitType type, UUID parentId, String emailName, String teaser,
                              String description, String timeZone, String country, RegionJoinMode joinMode,
                              Boolean moderated, List<UUID> ambassadorIds, List<FunctionInput> functions,
                              String reason) {
    }

    public record AdminItem(Region region, long childCount, long memberCount, List<User> ambassadors,
                            List<RegionReadService.FunctionGroup> functions) {
    }

    private final RegionRepository regions;
    private final RegionMembershipRepository memberships;
    private final RegionAdminRepository admins;
    private final RegionFunctionRepository functions;
    private final RegionTreeService tree;
    private final RegionReadService reads;
    private final UserService users;
    private final CurrentActorService currentActor;
    private final PermissionService permissions;
    private final HistoryService history;
    private final AppProperties properties;
    private final java.time.Clock clock;

    public RegionAdminService(RegionRepository regions, RegionMembershipRepository memberships,
                              RegionAdminRepository admins, RegionFunctionRepository functions, RegionTreeService tree,
                              RegionReadService reads, UserService users, CurrentActorService currentActor,
                              PermissionService permissions, HistoryService history, AppProperties properties,
                              java.time.Clock clock) {
        this.regions = regions;
        this.memberships = memberships;
        this.admins = admins;
        this.functions = functions;
        this.tree = tree;
        this.reads = reads;
        this.users = users;
        this.currentActor = currentActor;
        this.permissions = permissions;
        this.history = history;
        this.properties = properties;
        this.clock = clock;
    }

    /** Children of {@code parentId} (roots when null), or a name search when {@code q} is set. ORGA. */
    @Transactional(readOnly = true)
    public Page<AdminItem> list(UUID parentId, String q, int page, int size) {
        requireOrga();
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200),
                Sort.by("sortOrder", "name"));
        Page<Region> result;
        if (q != null && !q.isBlank()) {
            result = reads.search(q, null, page, size);
        } else if (parentId == null) {
            result = regions.findByParentIdIsNull(pageable);
        } else {
            result = regions.findByParentId(parentId, pageable);
        }
        return result.map(this::item);
    }

    /** @throws ApiException 403 unless ORGA, 404 REGION_NOT_FOUND */
    @Transactional(readOnly = true)
    public AdminItem get(UUID regionId) {
        requireOrga();
        return item(require(regionId));
    }

    public AdminItem item(Region region) {
        return new AdminItem(region, regions.countByParentId(region.getId()),
                memberships.countByRegionIdAndActiveTrue(region.getId()), reads.ambassadorsOf(region.getId()),
                reads.functionGroups(region.getId()));
    }

    /**
     * @throws ApiException 403 unless ORGA, 404 REGION_NOT_FOUND (parent) / GROUP_NOT_FOUND / USER_NOT_FOUND,
     *                      400 SLUG_TAKEN / EMAIL_NAME_TAKEN / DUPLICATE_FUNCTION_GROUP / INVALID_TIME_ZONE
     */
    @Transactional
    public AdminItem create(RegionInput input) {
        requireOrga();
        if (input.name() == null || input.name().isBlank() || input.type() == null) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        if (input.parentId() != null) {
            require(input.parentId());
        }
        Region region = new Region();
        region.setName(input.name().strip());
        region.setType(input.type());
        region.setParentId(input.parentId());
        region.setSlug(input.slug() == null || input.slug().isBlank()
                ? Slugs.unique(Slugs.slugify(input.name()), regions::existsBySlug) : checkedSlug(input.slug(), null));
        region.setEmailName(checkedEmailName(input.emailName(), null));
        region.setTeaser(input.teaser());
        region.setDescription(input.description());
        region.setTimeZone(checkedZone(input.timeZone() == null ? properties.getRegion().getDefaultTimeZone()
                : input.timeZone()));
        region.setCountry(input.country() == null ? null : input.country().toUpperCase(Locale.ROOT));
        region.setJoinMode(input.joinMode() == null ? RegionJoinMode.OPEN : input.joinMode());
        region.setModerated(Boolean.TRUE.equals(input.moderated()));
        if (region.isGroup()) {
            region.setApplyType(ApplyType.NOBODY);
            region.setGroupCategory(GroupCategory.ADMINISTRATIVE);
        }
        Region saved = regions.saveAndFlush(region);
        tree.attach(saved);
        history.record(history.event(region.isGroup() ? GroupAction.CREATED : RegionAction.CREATED, saved.getId())
                .region(saved.isGroup() ? saved.getParentId() : saved.getId()).reason(input.reason())
                .metadata(Map.of("name", saved.getName(), "type", saved.getType().name(), "slug", saved.getSlug()))
                .build());
        if (input.ambassadorIds() != null) {
            replaceAmbassadors(saved, input.ambassadorIds());
        }
        if (input.functions() != null) {
            replaceFunctions(saved, input.functions());
        }
        return item(saved);
    }

    /** @throws ApiException as {@link #create} */
    @Transactional
    public AdminItem update(UUID regionId, RegionInput input) {
        requireOrga();
        Region region = require(regionId);
        List<String> changed = new ArrayList<>();
        if (input.name() != null && !input.name().isBlank() && !input.name().strip().equals(region.getName())) {
            region.setName(input.name().strip());
            changed.add("name");
        }
        if (input.slug() != null && !input.slug().isBlank() && !input.slug().equals(region.getSlug())) {
            region.setSlug(checkedSlug(input.slug(), region.getId()));
            changed.add("slug");
        }
        if (input.type() != null && input.type() != region.getType()) {
            region.setType(input.type());
            changed.add("type");
        }
        if (input.emailName() != null && !Objects.equals(blankToNull(input.emailName()), region.getEmailName())) {
            region.setEmailName(checkedEmailName(input.emailName(), region.getId()));
            changed.add("emailName");
        }
        if (input.teaser() != null && !input.teaser().equals(region.getTeaser())) {
            region.setTeaser(blankToNull(input.teaser()));
            changed.add("teaser");
        }
        if (input.description() != null && !input.description().equals(region.getDescription())) {
            region.setDescription(blankToNull(input.description()));
            changed.add("description");
        }
        if (input.timeZone() != null && !input.timeZone().equals(region.getTimeZone())) {
            region.setTimeZone(checkedZone(input.timeZone()));
            changed.add("timeZone");
        }
        if (input.country() != null && !input.country().equalsIgnoreCase(String.valueOf(region.getCountry()))) {
            region.setCountry(blankToNull(input.country()) == null ? null : input.country().toUpperCase(Locale.ROOT));
            changed.add("country");
        }
        if (input.joinMode() != null && input.joinMode() != region.getJoinMode()) {
            region.setJoinMode(input.joinMode());
            changed.add("joinMode");
        }
        if (input.moderated() != null && input.moderated() != region.isModerated()) {
            region.setModerated(input.moderated());
            changed.add("moderated");
        }
        if (input.ambassadorIds() != null && replaceAmbassadors(region, input.ambassadorIds())) {
            changed.add("ambassadors");
        }
        if (input.functions() != null && replaceFunctions(region, input.functions())) {
            changed.add("functions");
        }
        if (input.parentId() != null && !input.parentId().equals(region.getParentId())) {
            tree.move(regionId, input.parentId(), input.reason());
            changed.add("parentId");
        }
        if (!changed.isEmpty()) {
            history.record(history.event(region.isGroup() ? GroupAction.UPDATED : RegionAction.UPDATED, regionId)
                    .region(region.isGroup() ? region.getParentId() : regionId).reason(input.reason())
                    .metadata("fields", String.join(",", changed)).build());
        }
        return item(region);
    }

    /** @throws ApiException 403 unless ORGA, 404 REGION_NOT_FOUND, 409 CANNOT_MOVE_INTO_DESCENDANT */
    @Transactional
    public AdminItem move(UUID regionId, UUID newParentId, String reason) {
        requireOrga();
        return item(tree.move(regionId, newParentId, reason));
    }

    /**
     * Deletes a region without children; with members only when forced (their memberships go with it, home regions
     * are cleared).
     *
     * @throws ApiException 403 unless ORGA, 404 REGION_NOT_FOUND, 409 REGION_HAS_CHILDREN / REGION_HAS_MEMBERS
     */
    @Transactional
    public void delete(UUID regionId, boolean force, String reason) {
        requireOrga();
        Region region = require(regionId);
        if (regions.countByParentId(regionId) > 0) {
            throw ApiException.conflict(ApiErrorCode.REGION_HAS_CHILDREN);
        }
        long members = memberships.countByRegionIdAndActiveTrue(regionId);
        if (members > 0 && !force) {
            throw ApiException.conflict(ApiErrorCode.REGION_HAS_MEMBERS).withArg("members", String.valueOf(members));
        }
        history.record(history.event(region.isGroup() ? GroupAction.ARCHIVED : RegionAction.DELETED, regionId)
                .region(region.isGroup() ? region.getParentId() : regionId).reason(reason)
                .metadata(Map.of("name", region.getName(), "slug", region.getSlug(), "members", members))
                .build());
        tree.detachAndDelete(region);
    }

    /**
     * Assigns a working group to a function of the region (replaces the previous group of that function).
     *
     * @throws ApiException 403 unless ORGA, 404 REGION_NOT_FOUND / GROUP_NOT_FOUND, 400 DUPLICATE_FUNCTION_GROUP
     */
    @Transactional
    public RegionReadService.FunctionGroup setFunction(UUID regionId, WorkingGroupFunction function, UUID groupId,
                                                       String reason) {
        requireOrga();
        Region region = require(regionId);
        Region group = requireGroup(groupId);
        functions.findByRegionId(regionId).stream()
                .filter(a -> a.getGroupId().equals(groupId) && a.getFunction() != function).findAny()
                .ifPresent(a -> {
                    throw ApiException.badRequest(ApiErrorCode.DUPLICATE_FUNCTION_GROUP)
                            .withArg("function", a.getFunction().name());
                });
        RegionFunction assignment = functions.findByRegionIdAndFunction(regionId, function).orElseGet(() -> {
            RegionFunction created = new RegionFunction();
            created.setRegionId(regionId);
            created.setFunction(function);
            return created;
        });
        UUID old = assignment.getGroupId();
        if (!groupId.equals(old)) {
            assignment.setGroupId(groupId);
            functions.save(assignment);
            history.record(history.event(RegionAction.FUNCTION_ASSIGNED, region.getId()).region(regionId)
                    .change(function.name(), old, groupId).reason(reason).build());
        }
        return new RegionReadService.FunctionGroup(function, group);
    }

    /** @throws ApiException 403 unless ORGA, 404 REGION_NOT_FOUND */
    @Transactional
    public void removeFunction(UUID regionId, WorkingGroupFunction function) {
        requireOrga();
        require(regionId);
        functions.findByRegionIdAndFunction(regionId, function).ifPresent(assignment -> {
            functions.delete(assignment);
            history.record(history.event(RegionAction.FUNCTION_REMOVED, regionId).region(regionId)
                    .change(function.name(), assignment.getGroupId(), null).build());
        });
    }

    private boolean replaceAmbassadors(Region region, List<UUID> ambassadorIds) {
        Set<UUID> wanted = new LinkedHashSet<>(ambassadorIds);
        Set<UUID> current = admins.findUserIds(region.getId());
        HistoryAction granted = region.isGroup() ? GroupAction.ADMIN_GRANTED : RegionAction.ADMIN_GRANTED;
        HistoryAction revoked = region.isGroup() ? GroupAction.ADMIN_REVOKED : RegionAction.ADMIN_REVOKED;
        boolean changed = false;
        for (UUID userId : wanted) {
            if (current.contains(userId)) {
                continue;
            }
            users.requireActive(userId);
            if (!memberships.existsByUserIdAndRegionIdAndActiveTrue(userId, region.getId())) {
                RegionMembership membership = memberships.findByUserIdAndRegionId(userId, region.getId())
                        .orElseGet(RegionMembership::new);
                membership.setUserId(userId);
                membership.setRegionId(region.getId());
                membership.setActive(true);
                membership.setJoinedAt(clock.instant());
                memberships.save(membership);
            }
            RegionAdmin admin = new RegionAdmin();
            admin.setUserId(userId);
            admin.setRegionId(region.getId());
            admins.save(admin);
            history.record(history.event(granted, region.getId()).secondarySubject(userId).build());
            changed = true;
        }
        for (UUID userId : current) {
            if (!wanted.contains(userId)) {
                admins.findByUserIdAndRegionId(userId, region.getId()).ifPresent(admins::delete);
                history.record(history.event(revoked, region.getId()).secondarySubject(userId).build());
                changed = true;
            }
        }
        return changed;
    }

    private boolean replaceFunctions(Region region, List<FunctionInput> requested) {
        Set<WorkingGroupFunction> seen = EnumSet.noneOf(WorkingGroupFunction.class);
        Set<UUID> groups = new java.util.HashSet<>();
        for (FunctionInput input : requested) {
            if (!seen.add(input.function()) || !groups.add(input.groupId())) {
                throw ApiException.badRequest(ApiErrorCode.DUPLICATE_FUNCTION_GROUP)
                        .withArg("function", input.function().name());
            }
            requireGroup(input.groupId());
        }
        boolean changed = false;
        for (RegionFunction existing : functions.findByRegionId(region.getId())) {
            if (!seen.contains(existing.getFunction())) {
                functions.delete(existing);
                history.record(history.event(RegionAction.FUNCTION_REMOVED, region.getId())
                        .change(existing.getFunction().name(), existing.getGroupId(), null).build());
                changed = true;
            }
        }
        functions.flush();
        for (FunctionInput input : requested) {
            RegionFunction assignment = functions.findByRegionIdAndFunction(region.getId(), input.function())
                    .orElseGet(RegionFunction::new);
            if (input.groupId().equals(assignment.getGroupId())) {
                continue;
            }
            UUID old = assignment.getGroupId();
            assignment.setRegionId(region.getId());
            assignment.setFunction(input.function());
            assignment.setGroupId(input.groupId());
            functions.save(assignment);
            history.record(history.event(RegionAction.FUNCTION_ASSIGNED, region.getId())
                    .change(input.function().name(), old, input.groupId()).build());
            changed = true;
        }
        return changed;
    }

    private String checkedSlug(String slug, UUID self) {
        String normalized = Slugs.slugify(slug);
        regions.findBySlug(normalized).filter(other -> !other.getId().equals(self)).ifPresent(other -> {
            throw ApiException.badRequest(ApiErrorCode.SLUG_TAKEN).withArg("slug", normalized);
        });
        return normalized;
    }

    private String checkedEmailName(String emailName, UUID self) {
        String value = blankToNull(emailName);
        if (value == null) {
            return null;
        }
        value = value.toLowerCase(Locale.ROOT);
        if (!EMAIL_NAME.matcher(value).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED,
                    List.of(new ApiFieldError("emailName", "Pattern", "3-50 of a-z 0-9 . -")), Map.of());
        }
        String checked = value;
        regions.findByEmailName(value).filter(other -> !other.getId().equals(self)).ifPresent(other -> {
            throw ApiException.badRequest(ApiErrorCode.EMAIL_NAME_TAKEN).withArg("emailName", checked);
        });
        return value;
    }

    private static String checkedZone(String zone) {
        if (!RegionService.isValidZone(zone)) {
            throw ApiException.badRequest(ApiErrorCode.INVALID_TIME_ZONE).withArg("timeZone", String.valueOf(zone));
        }
        return zone;
    }

    private Region require(UUID regionId) {
        return regions.findById(regionId).orElseThrow(() -> ApiException.notFound(ApiErrorCode.REGION_NOT_FOUND));
    }

    private Region requireGroup(UUID groupId) {
        return regions.findById(groupId).filter(Region::isGroup)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.GROUP_NOT_FOUND));
    }

    private void requireOrga() {
        permissions.requireOrga(currentActor.requirePrincipal());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
