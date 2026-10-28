package network.foodsharing.region.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.web.SlidingWindowRateLimiter;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.region.domain.ApplyType;
import network.foodsharing.region.domain.GroupAction;
import network.foodsharing.region.domain.GroupApplication;
import network.foodsharing.region.domain.GroupApplicationStatus;
import network.foodsharing.region.domain.GroupCategory;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.domain.RegionAction;
import network.foodsharing.region.domain.RegionAdmin;
import network.foodsharing.region.domain.RegionFunction;
import network.foodsharing.region.domain.RegionMembership;
import network.foodsharing.region.domain.UnitType;
import network.foodsharing.region.repository.GroupApplicationRepository;
import network.foodsharing.region.repository.RegionAdminRepository;
import network.foodsharing.region.repository.RegionFunctionRepository;
import network.foodsharing.region.repository.RegionMembershipRepository;
import network.foodsharing.region.repository.RegionRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.security.WorkingGroupFunction;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Working groups (plan §13.2 R9–R12), modelled as regions of type WORKING_GROUP below their region.
 * <ul>
 *   <li>apply types: NOBODY (admins add members), EVERYBODY (application, optional requirements), OPEN (join directly);
 *       archived groups (category ARCHIVED) accept nobody;</li>
 *   <li>applying / joining a group of a region requires membership in that region; global groups (parent is a
 *       working group or none) are open to everyone;</li>
 *   <li>requirements: weeks since registration, pickups and bananas through {@link GroupRequirementContributor};</li>
 *   <li>editing: group admins and ORGA; groups holding a restricted function (REPORT, ARBITRATION, FSMANAGEMENT,
 *       BOARD) only ORGA (upstream {@code WorkGroupPermissions::mayEdit});</li>
 *   <li>creating: administrators of the region (restricted function: ORGA, 403 RESTRICTED_FUNCTION).</li>
 * </ul>
 * Notifications (application received / accepted / declined, group mail) are sent after commit by
 * {@link RegionNotificationListener}.
 */
@Service
public class GroupService {

    public record RequirementCheck(boolean pickupsOk, boolean bananasOk, boolean weeksOk, int pickups, int bananas,
                                   int weeks) {
        public boolean allOk() {
            return pickupsOk && bananasOk && weeksOk;
        }
    }

    public record GroupView(Region group, Region parent, long memberCount, List<User> admins, boolean member,
                            boolean admin, boolean applied, boolean mayApply, boolean mayJoin, boolean mayEdit,
                            WorkingGroupFunction function, List<Region> subGroups, RequirementCheck requirementCheck,
                            String conferenceUrl) {
    }

    public record GroupInput(String name, String teaser, String description, String photoUrl, GroupCategory category,
                             ApplyType applyType, Integer minPickups, Integer minBananas, Integer minWeeks,
                             WorkingGroupFunction function, String reason) {
    }

    public record ApplicationView(GroupApplication application, User user, User decidedBy) {
    }

    public record MemberView(User user, boolean admin, Instant joinedAt) {
    }

    /** Events for {@link RegionNotificationListener}. */
    public record ApplicationSubmitted(UUID groupId, UUID applicationId, UUID userId) {
    }

    public record ApplicationDecided(UUID groupId, UUID applicationId, UUID userId, boolean accepted, String message) {
    }

    public record GroupMailRequested(UUID groupId, UUID senderId, String subject, String body) {
    }

    private final RegionRepository regions;
    private final RegionMembershipRepository memberships;
    private final RegionAdminRepository admins;
    private final RegionFunctionRepository functions;
    private final GroupApplicationRepository applications;
    private final RegionTreeService tree;
    private final ConferenceLinkService conference;
    private final List<GroupRequirementContributor> requirementContributors;
    private final UserService users;
    private final CurrentActorService currentActor;
    private final PermissionService permissions;
    private final HistoryService history;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final SlidingWindowRateLimiter mailLimiter;

    public GroupService(RegionRepository regions, RegionMembershipRepository memberships, RegionAdminRepository admins,
                        RegionFunctionRepository functions, GroupApplicationRepository applications,
                        RegionTreeService tree, ConferenceLinkService conference,
                        List<GroupRequirementContributor> requirementContributors, UserService users,
                        CurrentActorService currentActor, PermissionService permissions, HistoryService history,
                        ApplicationEventPublisher events, Clock clock, AppProperties properties) {
        this.regions = regions;
        this.memberships = memberships;
        this.admins = admins;
        this.functions = functions;
        this.applications = applications;
        this.tree = tree;
        this.conference = conference;
        this.requirementContributors = requirementContributors;
        this.users = users;
        this.currentActor = currentActor;
        this.permissions = permissions;
        this.history = history;
        this.events = events;
        this.clock = clock;
        this.mailLimiter = new SlidingWindowRateLimiter(clock, properties.getRegion().getGroupMailsPerHour(),
                Duration.ofHours(1));
    }

    // ------------------------------------------------------------------------------------------------ reads

    /** Groups directly below a region (or a global group node). @throws ApiException 404 REGION_NOT_FOUND */
    @Transactional(readOnly = true)
    public List<GroupView> groupsIn(UUID regionId, boolean includeArchived) {
        regions.findById(regionId).orElseThrow(() -> ApiException.notFound(ApiErrorCode.REGION_NOT_FOUND));
        SessionPrincipal actor = currentActor.requirePrincipal();
        return regions.findByParentIdOrderBySortOrderAscNameAsc(regionId).stream()
                .filter(Region::isGroup)
                .filter(group -> includeArchived || group.getGroupCategory() != GroupCategory.ARCHIVED)
                .map(group -> view(group, actor, false)).toList();
    }

    @Transactional(readOnly = true)
    public List<GroupView> myGroups() {
        SessionPrincipal actor = currentActor.requirePrincipal();
        Set<UUID> ids = new LinkedHashSet<>(memberships.findActiveRegionIds(actor.userId()));
        ids.addAll(admins.findRegionIds(actor.userId()));
        return regions.findByIdIn(ids).stream().filter(Region::isGroup)
                .sorted(Comparator.comparing(Region::getName, String.CASE_INSENSITIVE_ORDER))
                .map(group -> view(group, actor, false)).toList();
    }

    /** @throws ApiException 404 GROUP_NOT_FOUND */
    @Transactional(readOnly = true)
    public GroupView detail(UUID groupId) {
        return view(requireGroup(groupId), currentActor.requirePrincipal(), true);
    }

    /** @throws ApiException 404 GROUP_NOT_FOUND */
    @Transactional(readOnly = true)
    public List<MemberView> members(UUID groupId) {
        requireGroup(groupId);
        currentActor.requirePrincipal();
        Set<UUID> adminIds = admins.findUserIds(groupId);
        Map<UUID, Instant> joined = memberships.findByRegionIdAndActiveTrue(groupId).stream()
                .collect(Collectors.toMap(RegionMembership::getUserId, RegionMembership::getJoinedAt, (a, b) -> a));
        Set<UUID> ids = new LinkedHashSet<>(joined.keySet());
        ids.addAll(adminIds);
        return users.findActive(ids).stream()
                .map(user -> new MemberView(user, adminIds.contains(user.getId()), joined.get(user.getId())))
                .sorted(Comparator.comparing((MemberView m) -> !m.admin())
                        .thenComparing(m -> m.user().getDisplayName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Applications of a group (default PENDING). @throws ApiException 403, 404 GROUP_NOT_FOUND */
    @Transactional(readOnly = true)
    public List<ApplicationView> applications(UUID groupId, GroupApplicationStatus status) {
        Region group = requireGroup(groupId);
        requireManager(group);
        List<GroupApplication> rows = applications.findByGroupIdAndStatusOrderByCreatedAtAsc(groupId,
                status == null ? GroupApplicationStatus.PENDING : status);
        return applicationViews(rows);
    }

    // ------------------------------------------------------------------------------------------------ admin

    /**
     * Creates a working group below a region; the creator becomes its admin.
     *
     * @throws ApiException 404 REGION_NOT_FOUND, 403 ACCESS_DENIED / RESTRICTED_FUNCTION, 409 DUPLICATE_FUNCTION_GROUP
     */
    @Transactional
    public GroupView create(UUID regionId, GroupInput input) {
        Region region = regions.findById(regionId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.REGION_NOT_FOUND));
        SessionPrincipal actor = currentActor.requirePrincipal();
        permissions.requireMayAdministrateRegion(actor, regionId);
        if (input.function() != null) {
            if (input.function().isRestricted() && !permissions.isOrga(actor)) {
                throw ApiException.forbidden(ApiErrorCode.RESTRICTED_FUNCTION);
            }
            if (functions.findByRegionIdAndFunction(regionId, input.function()).isPresent()) {
                throw ApiException.conflict(ApiErrorCode.DUPLICATE_FUNCTION_GROUP)
                        .withArg("function", input.function().name());
            }
        }
        if (input.name() == null || input.name().isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        Region group = new Region();
        group.setType(UnitType.WORKING_GROUP);
        group.setConversationEnabled(true);
        group.setParentId(region.getId());
        group.setName(input.name().strip());
        group.setSlug(Slugs.unique(Slugs.slugify(input.name()), regions::existsBySlug));
        group.setTeaser(input.teaser());
        group.setDescription(input.description());
        group.setPhotoUrl(input.photoUrl());
        group.setGroupCategory(input.category() == null ? GroupCategory.PROJECT : input.category());
        group.setApplyType(input.applyType() == null ? ApplyType.EVERYBODY : input.applyType());
        group.setMinPickups(orZero(input.minPickups()));
        group.setMinBananas(orZero(input.minBananas()));
        group.setMinWeeks(orZero(input.minWeeks()));
        group.setTimeZone(region.getTimeZone());
        Region saved = regions.saveAndFlush(group);
        tree.attach(saved);
        history.record(history.event(GroupAction.CREATED, saved.getId()).region(regionId).reason(input.reason())
                .metadata(Map.of("name", saved.getName(), "applyType", saved.getApplyType().name())).build());
        addMembership(saved.getId(), actor.userId());
        RegionAdmin admin = new RegionAdmin();
        admin.setUserId(actor.userId());
        admin.setRegionId(saved.getId());
        admins.save(admin);
        if (input.function() != null) {
            RegionFunction assignment = new RegionFunction();
            assignment.setRegionId(regionId);
            assignment.setFunction(input.function());
            assignment.setGroupId(saved.getId());
            functions.save(assignment);
            history.record(history.event(RegionAction.FUNCTION_ASSIGNED, regionId).region(regionId)
                    .change(input.function().name(), null, saved.getId()).build());
        }
        return view(saved, actor, true);
    }

    /** @throws ApiException 403 ACCESS_DENIED, 404 GROUP_NOT_FOUND */
    @Transactional
    public GroupView update(UUID groupId, GroupInput input) {
        Region group = requireGroup(groupId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        requireEdit(group, actor);
        List<String> changed = new ArrayList<>();
        changed.addAll(set(input.name() == null || input.name().isBlank() ? null : input.name().strip(),
                group.getName(), group::setName, "name"));
        changed.addAll(set(input.teaser(), group.getTeaser(), group::setTeaser, "teaser"));
        changed.addAll(set(input.description(), group.getDescription(), group::setDescription, "description"));
        changed.addAll(set(input.photoUrl(), group.getPhotoUrl(), group::setPhotoUrl, "photoUrl"));
        changed.addAll(set(input.category(), group.getGroupCategory(), group::setGroupCategory, "category"));
        changed.addAll(set(input.applyType(), group.getApplyType(), group::setApplyType, "applyType"));
        changed.addAll(set(input.minPickups(), group.getMinPickups(), group::setMinPickups, "minPickups"));
        changed.addAll(set(input.minBananas(), group.getMinBananas(), group::setMinBananas, "minBananas"));
        changed.addAll(set(input.minWeeks(), group.getMinWeeks(), group::setMinWeeks, "minWeeks"));
        if (!changed.isEmpty()) {
            history.record(history.event(GroupAction.UPDATED, groupId).region(group.getParentId())
                    .reason(input.reason()).metadata("fields", String.join(",", changed)).build());
        }
        return view(group, actor, true);
    }

    /** @throws ApiException 403 ACCESS_DENIED, 404 GROUP_NOT_FOUND */
    @Transactional
    public GroupView archive(UUID groupId, String reason) {
        Region group = requireGroup(groupId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        requireEdit(group, actor);
        if (group.getGroupCategory() != GroupCategory.ARCHIVED) {
            history.record(history.event(GroupAction.ARCHIVED, groupId).region(group.getParentId()).reason(reason)
                    .change("category", group.getGroupCategory(), GroupCategory.ARCHIVED).build());
            group.setGroupCategory(GroupCategory.ARCHIVED);
        }
        return view(group, actor, true);
    }

    // ------------------------------------------------------------------------------------------------ joining

    /** @throws ApiException 404 GROUP_NOT_FOUND, 409 GROUP_NOT_OPEN / ALREADY_MEMBER / NOT_A_MEMBER (parent) */
    @Transactional
    public GroupView joinOpen(UUID groupId) {
        Region group = requireGroup(groupId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        if (group.getApplyType() != ApplyType.OPEN || group.getGroupCategory() == GroupCategory.ARCHIVED) {
            throw ApiException.conflict(ApiErrorCode.GROUP_NOT_OPEN);
        }
        if (memberships.existsByUserIdAndRegionIdAndActiveTrue(actor.userId(), groupId)) {
            throw ApiException.conflict(ApiErrorCode.ALREADY_MEMBER);
        }
        requireParentMembership(group, actor.userId());
        addMembership(groupId, actor.userId());
        history.record(history.event(GroupAction.MEMBER_JOINED, groupId).secondarySubject(actor.userId())
                .region(group.getParentId()).build());
        return view(group, actor, true);
    }

    /**
     * @throws ApiException 404 GROUP_NOT_FOUND, 409 ALREADY_MEMBER / GROUP_APPLICATIONS_CLOSED / ALREADY_APPLIED /
     *                      NOT_A_MEMBER / GROUP_REQUIREMENTS_NOT_MET (args pickups, bananas, weeks = required values)
     */
    @Transactional
    public ApplicationView apply(UUID groupId, String text) {
        Region group = requireGroup(groupId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        if (memberships.existsByUserIdAndRegionIdAndActiveTrue(actor.userId(), groupId)) {
            throw ApiException.conflict(ApiErrorCode.ALREADY_MEMBER);
        }
        if (group.getApplyType() != ApplyType.EVERYBODY || group.getGroupCategory() == GroupCategory.ARCHIVED) {
            throw ApiException.conflict(ApiErrorCode.GROUP_APPLICATIONS_CLOSED);
        }
        if (applications.findFirstByGroupIdAndUserIdAndStatus(groupId, actor.userId(), GroupApplicationStatus.PENDING)
                .isPresent()) {
            throw ApiException.conflict(ApiErrorCode.ALREADY_APPLIED);
        }
        requireParentMembership(group, actor.userId());
        RequirementCheck check = requirementCheck(group, actor.userId());
        if (!check.allOk()) {
            throw ApiException.conflict(ApiErrorCode.GROUP_REQUIREMENTS_NOT_MET)
                    .withArg("pickups", String.valueOf(group.getMinPickups()))
                    .withArg("bananas", String.valueOf(group.getMinBananas()))
                    .withArg("weeks", String.valueOf(group.getMinWeeks()));
        }
        GroupApplication application = new GroupApplication();
        application.setGroupId(groupId);
        application.setUserId(actor.userId());
        application.setText(text);
        application.setStatus(GroupApplicationStatus.PENDING);
        GroupApplication saved = applications.save(application);
        history.record(history.event(GroupAction.APPLICATION_SUBMITTED, groupId).secondarySubject(actor.userId())
                .region(group.getParentId()).build());
        events.publishEvent(new ApplicationSubmitted(groupId, saved.getId(), actor.userId()));
        return applicationViews(List.of(saved)).getFirst();
    }

    /** @throws ApiException 403, 404 GROUP_NOT_FOUND / APPLICATION_NOT_FOUND, 409 APPLICATION_NOT_PENDING */
    @Transactional
    public ApplicationView decide(UUID groupId, UUID applicationId, boolean accept, String message) {
        Region group = requireGroup(groupId);
        SessionPrincipal actor = requireManager(group);
        GroupApplication application = applications.findByIdAndGroupId(applicationId, groupId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.APPLICATION_NOT_FOUND));
        if (application.getStatus() != GroupApplicationStatus.PENDING) {
            throw ApiException.conflict(ApiErrorCode.APPLICATION_NOT_PENDING);
        }
        application.setStatus(accept ? GroupApplicationStatus.ACCEPTED : GroupApplicationStatus.DECLINED);
        application.setDecidedBy(actor.userId());
        application.setDecidedAt(clock.instant());
        application.setDecisionMessage(message == null || message.isBlank() ? null : message.strip());
        if (accept && !memberships.existsByUserIdAndRegionIdAndActiveTrue(application.getUserId(), groupId)) {
            addMembership(groupId, application.getUserId());
        }
        history.record(history.event(accept ? GroupAction.APPLICATION_ACCEPTED : GroupAction.APPLICATION_DECLINED,
                        groupId).secondarySubject(application.getUserId()).region(group.getParentId())
                .reason(application.getDecisionMessage()).build());
        events.publishEvent(new ApplicationDecided(groupId, applicationId, application.getUserId(), accept,
                application.getDecisionMessage()));
        return applicationViews(List.of(application)).getFirst();
    }

    /** @throws ApiException 404 GROUP_NOT_FOUND / APPLICATION_NOT_FOUND */
    @Transactional
    public void cancelMine(UUID groupId) {
        Region group = requireGroup(groupId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        GroupApplication application = applications.findFirstByGroupIdAndUserIdAndStatus(groupId, actor.userId(),
                GroupApplicationStatus.PENDING).orElseThrow(() -> ApiException.notFound(ApiErrorCode.APPLICATION_NOT_FOUND));
        application.setStatus(GroupApplicationStatus.CANCELLED);
        history.record(history.event(GroupAction.APPLICATION_CANCELLED, groupId).secondarySubject(actor.userId())
                .region(group.getParentId()).build());
    }

    // ------------------------------------------------------------------------------------------------ members

    /** @throws ApiException 403, 404 GROUP_NOT_FOUND / USER_NOT_FOUND, 409 ALREADY_MEMBER */
    @Transactional
    public MemberView addMember(UUID groupId, UUID userId) {
        Region group = requireGroup(groupId);
        requireManager(group);
        User user = users.requireActive(userId);
        if (memberships.existsByUserIdAndRegionIdAndActiveTrue(userId, groupId)) {
            throw ApiException.conflict(ApiErrorCode.ALREADY_MEMBER);
        }
        RegionMembership membership = addMembership(groupId, userId);
        history.record(history.event(GroupAction.MEMBER_ADDED, groupId).secondarySubject(userId)
                .region(group.getParentId()).build());
        return new MemberView(user, admins.existsByUserIdAndRegionId(userId, groupId), membership.getJoinedAt());
    }

    /** Group admins, ORGA or the member themself. @throws ApiException 403, 404, 409 NOT_A_MEMBER */
    @Transactional
    public void removeMember(UUID groupId, UUID userId, String reason) {
        Region group = requireGroup(groupId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        if (!actor.userId().equals(userId)) {
            requireManager(group);
        }
        RegionMembership membership = memberships.findByUserIdAndRegionId(userId, groupId)
                .filter(RegionMembership::isActive)
                .orElseThrow(() -> ApiException.conflict(ApiErrorCode.NOT_A_MEMBER));
        memberships.delete(membership);
        admins.findByUserIdAndRegionId(userId, groupId).ifPresent(admins::delete);
        history.record(history.event(GroupAction.MEMBER_REMOVED, groupId).secondarySubject(userId)
                .region(group.getParentId()).reason(reason).build());
    }

    /** @throws ApiException 403, 404 GROUP_NOT_FOUND, 409 NOT_A_MEMBER */
    @Transactional
    public MemberView setAdmin(UUID groupId, UUID userId) {
        Region group = requireGroup(groupId);
        requireManager(group);
        RegionMembership membership = memberships.findByUserIdAndRegionId(userId, groupId)
                .filter(RegionMembership::isActive)
                .orElseThrow(() -> ApiException.conflict(ApiErrorCode.NOT_A_MEMBER));
        if (!admins.existsByUserIdAndRegionId(userId, groupId)) {
            RegionAdmin admin = new RegionAdmin();
            admin.setUserId(userId);
            admin.setRegionId(groupId);
            admins.save(admin);
            history.record(history.event(GroupAction.ADMIN_GRANTED, groupId).secondarySubject(userId)
                    .region(group.getParentId()).build());
        }
        return new MemberView(users.require(userId), true, membership.getJoinedAt());
    }

    /** @throws ApiException 403, 404 GROUP_NOT_FOUND */
    @Transactional
    public void removeAdmin(UUID groupId, UUID userId) {
        Region group = requireGroup(groupId);
        requireManager(group);
        admins.findByUserIdAndRegionId(userId, groupId).ifPresent(admin -> {
            admins.delete(admin);
            history.record(history.event(GroupAction.ADMIN_REVOKED, groupId).secondarySubject(userId)
                    .region(group.getParentId()).build());
        });
    }

    /**
     * Mails every member (rate limited per sender: {@code app.region.group-mails-per-hour}).
     *
     * @throws ApiException 403 unless member (or ORGA), 404 GROUP_NOT_FOUND, 429 TOO_MANY_REQUESTS
     */
    @Transactional
    public int sendMail(UUID groupId, String subject, String body) {
        Region group = requireGroup(groupId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        permissions.requireMember(actor, groupId);
        if (!mailLimiter.tryAcquire(actor.userId().toString())) {
            throw ApiException.tooManyRequests(ApiErrorCode.TOO_MANY_REQUESTS, mailLimiter.window());
        }
        int recipients = memberships.findActiveUserIds(groupId).size();
        history.record(history.event(GroupAction.MAIL_SENT, groupId).region(group.getParentId())
                .metadata(Map.of("subject", subject, "recipients", recipients)).build());
        events.publishEvent(new GroupMailRequested(groupId, actor.userId(), subject, body));
        return recipients;
    }

    /** @throws ApiException 403 unless member, 404 */
    @Transactional(readOnly = true)
    public String conferenceUrl(UUID groupId) {
        requireGroup(groupId);
        return conference.conferenceUrl(groupId);
    }

    // ------------------------------------------------------------------------------------------------ rules

    /** May the actor edit the group (admins / ORGA; restricted function groups only ORGA)? */
    public boolean mayEdit(Region group, SessionPrincipal actor) {
        if (permissions.isOrga(actor)) {
            return true;
        }
        if (!permissions.isAmbassador(actor, group.getId())) {
            return false;
        }
        return functionOf(group).map(fn -> !fn.isRestricted()).orElse(true);
    }

    /** Applying to a group of a region requires membership in that region (global groups: everyone). */
    public boolean parentMembershipOk(Region group, UUID userId) {
        if (group.getParentId() == null) {
            return true;
        }
        Region parent = regions.findById(group.getParentId()).orElse(null);
        return parent == null || parent.isGroup() || memberships.existsByUserIdAndRegionIdAndActiveTrue(userId,
                parent.getId());
    }

    public RequirementCheck requirementCheck(Region group, UUID userId) {
        int pickups = requirementContributors.stream().mapToInt(c -> c.pickups(userId)).sum();
        int bananas = requirementContributors.stream().mapToInt(c -> c.bananas(userId)).sum();
        Instant registered = users.findActive(userId).map(User::getRegisteredAt).orElse(clock.instant());
        int weeks = (int) (ChronoUnit.DAYS.between(registered, clock.instant()) / 7);
        return new RequirementCheck(pickups >= group.getMinPickups(), bananas >= group.getMinBananas(),
                weeks >= group.getMinWeeks(), pickups, bananas, weeks);
    }

    private GroupView view(Region group, SessionPrincipal actor, boolean detail) {
        UUID userId = actor.userId();
        boolean member = memberships.existsByUserIdAndRegionIdAndActiveTrue(userId, group.getId());
        boolean admin = admins.existsByUserIdAndRegionId(userId, group.getId());
        boolean applied = applications.findFirstByGroupIdAndUserIdAndStatus(group.getId(), userId,
                GroupApplicationStatus.PENDING).isPresent();
        boolean archived = group.getGroupCategory() == GroupCategory.ARCHIVED;
        boolean parentOk = parentMembershipOk(group, userId);
        boolean mayApply = !member && !applied && !archived && group.getApplyType() == ApplyType.EVERYBODY && parentOk;
        boolean mayJoin = !member && !archived && group.getApplyType() == ApplyType.OPEN && parentOk;
        Region parent = group.getParentId() == null ? null : regions.findById(group.getParentId()).orElse(null);
        List<User> groupAdmins = users.findActive(admins.findUserIds(group.getId())).stream()
                .sorted(Comparator.comparing(User::getDisplayName, String.CASE_INSENSITIVE_ORDER)).toList();
        List<Region> subGroups = regions.findByParentIdOrderBySortOrderAscNameAsc(group.getId()).stream()
                .filter(Region::isGroup).toList();
        RequirementCheck check = detail ? requirementCheck(group, userId) : null;
        String conferenceUrl = detail && (member || permissions.isOrga(actor)) ? conference.linkOf(group) : null;
        return new GroupView(group, parent, memberships.countByRegionIdAndActiveTrue(group.getId()), groupAdmins,
                member, admin, applied, mayApply, mayJoin, mayEdit(group, actor), functionOf(group).orElse(null),
                subGroups, check, conferenceUrl);
    }

    private Optional<WorkingGroupFunction> functionOf(Region group) {
        return functions.findFirstByGroupId(group.getId()).map(RegionFunction::getFunction);
    }

    private List<ApplicationView> applicationViews(List<GroupApplication> rows) {
        Set<UUID> ids = new LinkedHashSet<>();
        rows.forEach(row -> {
            ids.add(row.getUserId());
            if (row.getDecidedBy() != null) {
                ids.add(row.getDecidedBy());
            }
        });
        Map<UUID, User> byId = users.findActive(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return rows.stream().filter(row -> byId.containsKey(row.getUserId()))
                .map(row -> new ApplicationView(row, byId.get(row.getUserId()),
                        row.getDecidedBy() == null ? null : byId.get(row.getDecidedBy()))).toList();
    }

    private RegionMembership addMembership(UUID groupId, UUID userId) {
        RegionMembership membership = memberships.findByUserIdAndRegionId(userId, groupId)
                .orElseGet(RegionMembership::new);
        membership.setUserId(userId);
        membership.setRegionId(groupId);
        membership.setActive(true);
        membership.setJoinedAt(clock.instant());
        return memberships.save(membership);
    }

    private void requireParentMembership(Region group, UUID userId) {
        if (!parentMembershipOk(group, userId)) {
            throw ApiException.conflict(ApiErrorCode.NOT_A_MEMBER);
        }
    }

    private void requireEdit(Region group, SessionPrincipal actor) {
        if (!mayEdit(group, actor)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
    }

    /** Group admins and ORGA manage members and applications. */
    private SessionPrincipal requireManager(Region group) {
        SessionPrincipal actor = currentActor.requirePrincipal();
        permissions.requireAmbassadorOrOrga(actor, group.getId());
        return actor;
    }

    private Region requireGroup(UUID groupId) {
        return regions.findById(groupId).filter(Region::isGroup)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.GROUP_NOT_FOUND));
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private static <T> List<String> set(T requested, T current, java.util.function.Consumer<T> setter, String field) {
        if (requested == null || Objects.equals(requested, current)) {
            return List.of();
        }
        setter.accept(requested);
        return List.of(field);
    }
}
