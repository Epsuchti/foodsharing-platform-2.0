package network.foodsharing.store.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.BellService;
import network.foodsharing.notification.service.NotificationRequest;
import org.springframework.context.ApplicationEventPublisher;
import network.foodsharing.quiz.service.QuizSessionService;
import network.foodsharing.region.service.MembershipService;
import network.foodsharing.region.service.RegionPermissionResolver;
import network.foodsharing.region.service.RegionReadService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.domain.StoreLogAction;
import network.foodsharing.store.domain.StoreMembership;
import network.foodsharing.store.domain.StoreMembershipStatus;
import network.foodsharing.store.domain.TeamSearchStatus;
import network.foodsharing.store.domain.UserTeamStatus;
import network.foodsharing.store.repository.StoreMembershipRepository;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Store team workflow (plan §12 S5–S9, upstream {@code StoreTransactions} / {@code mayJoinStore}): applications,
 * invitations, accepting onto team or waiting list, removing / leaving, standby, managers (A19 limits) and the team
 * list. Bells (and mails for messages) go to the affected users; requests go to the managers, else the region's
 * ambassadors.
 */
@Service
public class StoreTeamService {

    public record TeamMemberView(User user, StoreMembership membership, Instant hygieneUntil,
                                 StorePickupInfo.MemberStats stats, Double distanceKm, boolean withPhone) {
    }

    public record TeamView(List<TeamMemberView> members, List<TeamMemberView> jumpers, List<TeamMemberView> applications,
                           List<TeamMemberView> invitations, Map<UUID, User> inviters, boolean mayEditTeam) {
    }

    private final StoreService stores;
    private final StoreMembershipRepository memberships;
    private final StorePermissionService rights;
    private final StorePickupInfo pickupInfo;
    private final UserService users;
    private final QuizSessionService quizzes;
    private final MembershipService regionMemberships;
    private final RegionPermissionResolver resolver;
    private final RegionReadService regionReads;
    private final ApplicationEventPublisher events;
    private final CurrentActorService currentActor;
    private final HistoryService history;
    private final AppProperties properties;
    private final Clock clock;

    public StoreTeamService(StoreService stores, StoreMembershipRepository memberships, StorePermissionService rights,
                            StorePickupInfo pickupInfo, UserService users, QuizSessionService quizzes,
                            MembershipService regionMemberships, RegionPermissionResolver resolver,
                            RegionReadService regionReads, ApplicationEventPublisher events,
                            CurrentActorService currentActor, HistoryService history, AppProperties properties,
                            Clock clock) {
        this.stores = stores;
        this.memberships = memberships;
        this.rights = rights;
        this.pickupInfo = pickupInfo;
        this.users = users;
        this.quizzes = quizzes;
        this.regionMemberships = regionMemberships;
        this.resolver = resolver;
        this.regionReads = regionReads;
        this.events = events;
        this.currentActor = currentActor;
        this.history = history;
        this.properties = properties;
        this.clock = clock;
    }

    // --------------------------------------------------------------------------------------------- read

    /** @throws ApiException 404, 403 unless mayAccessStore */
    @Transactional(readOnly = true)
    public TeamView team(UUID storeId) {
        Store store = stores.require(storeId);
        StorePermissionService.Rights r = stores.requireAccess(store);
        List<StoreMembership> all = memberships.findByStoreId(storeId);
        Map<UUID, User> byId = users.findAll(all.stream().flatMap(m -> m.getInvitedBy() == null
                ? java.util.stream.Stream.of(m.getUserId()) : java.util.stream.Stream.of(m.getUserId(),
                m.getInvitedBy())).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, StorePickupInfo.MemberStats> stats = pickupInfo.memberStats(storeId);
        List<TeamMemberView> members = new ArrayList<>();
        List<TeamMemberView> jumpers = new ArrayList<>();
        List<TeamMemberView> applications = new ArrayList<>();
        List<TeamMemberView> invitations = new ArrayList<>();
        for (StoreMembership m : all) {
            User user = byId.get(m.getUserId());
            if (user == null) {
                continue;
            }
            TeamMemberView view = new TeamMemberView(user, m, quizzes.hygieneValidUntil(user.getId()).orElse(null),
                    stats.getOrDefault(user.getId(), new StorePickupInfo.MemberStats(0, null, null)),
                    r.maySeeMemberDistance() ? distance(user, store) : null, r.maySeePhoneNumbers());
            switch (m.getStatus()) {
                case MEMBER -> members.add(view);
                case JUMPER -> jumpers.add(view);
                case APPLIED -> applications.add(view);
                case INVITED -> invitations.add(view);
            }
        }
        Comparator<TeamMemberView> order = Comparator.comparing((TeamMemberView v) -> !v.membership().isManager())
                .thenComparing(v -> v.user().getDisplayName(), String.CASE_INSENSITIVE_ORDER);
        members.sort(order);
        jumpers.sort(order);
        return new TeamView(members, jumpers, r.mayEditTeam() ? applications : null,
                r.mayEditTeam() ? invitations : null, byId, r.mayEditTeam());
    }

    static Double distance(User user, Store store) {
        if (user.getLatitude() == null || user.getLongitude() == null || store.getLatitude() == null
                || store.getLongitude() == null) {
            return null;
        }
        double lat1 = Math.toRadians(user.getLatitude());
        double lat2 = Math.toRadians(store.getLatitude());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(store.getLongitude() - user.getLongitude());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return Math.round(6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)) * 10) / 10.0;
    }

    // ------------------------------------------------------------------------------------ applications

    /** Requirement chain of upstream {@code mayJoinStore} (team status, then user requirements). */
    void checkRequirements(Store store, User user, String text, boolean checkTeamOpen, boolean checkText) {
        if (checkTeamOpen && store.getTeamStatus() == TeamSearchStatus.CLOSED) {
            throw ApiException.conflict(ApiErrorCode.TEAM_CLOSED);
        }
        if (!PermissionService.isAtLeast(user.getRole(), Role.FOODSAVER)) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_TOO_LOW);
        }
        if (blank(user.getFirstName()) || blank(user.getLastName()) || blank(user.getStreet())
                || blank(user.getPostcode()) || blank(user.getCity())) {
            throw ApiException.conflict(ApiErrorCode.PROFILE_INCOMPLETE);
        }
        if (user.getHomeRegionId() == null) {
            throw ApiException.conflict(ApiErrorCode.NO_HOME_REGION);
        }
        if (!resolver.isMember(user.getId(), store.getRegionId())) {
            throw ApiException.conflict(ApiErrorCode.NOT_REGION_MEMBER);
        }
        if (store.isRequiresVerified() && !user.isVerified()) {
            throw ApiException.forbidden(ApiErrorCode.NOT_VERIFIED);
        }
        if (store.isRequiresPhone() && blank(user.getMobilePhone()) && blank(user.getLandlinePhone())) {
            throw ApiException.conflict(ApiErrorCode.PHONE_REQUIRED);
        }
        if (store.isRequiresHygiene() && quizzes.hygieneValidUntil(user.getId())
                .filter(until -> until.isAfter(clock.instant())).isEmpty()) {
            throw ApiException.conflict(ApiErrorCode.HYGIENE_CERTIFICATE_REQUIRED);
        }
        if (checkText && store.isRequiresApplicationText() && blank(text)) {
            throw ApiException.badRequest(ApiErrorCode.APPLICATION_TEXT_REQUIRED);
        }
    }

    /** @throws ApiException 404, 409 / 403 / 400 per {@link #checkRequirements} and ALREADY_IN_TEAM */
    @Transactional
    public void apply(UUID storeId, String text) {
        Store store = stores.require(storeId);
        User user = currentActor.requireUser();
        if (rights.teamStatus(user.getId(), storeId) != UserTeamStatus.NO_MEMBER) {
            throw ApiException.conflict(ApiErrorCode.ALREADY_IN_TEAM);
        }
        checkRequirements(store, user, text, true, true);
        StoreMembership m = new StoreMembership();
        m.setStoreId(storeId);
        m.setUserId(user.getId());
        m.setStatus(StoreMembershipStatus.APPLIED);
        m.setApplicationText(blank(text) ? null : text.strip());
        memberships.save(m);
        log(StoreLogAction.REQUEST_TO_JOIN, store, user.getId(), null);
        List<UUID> recipients = managerIds(storeId);
        if (recipients.isEmpty()) {
            recipients = regionReads.ambassadorsOf(store.getRegionId()).stream().map(User::getId).toList();
        }
        notify(recipients, NotificationType.STORE_NEW_REQUEST, store, null, user.getDisplayName(),
                BellService.identifier(NotificationType.STORE_NEW_REQUEST, storeId), null);
    }

    /** @throws ApiException 404, 409 NOT_APPLIED */
    @Transactional
    public void cancelApplication(UUID storeId) {
        Store store = stores.require(storeId);
        UUID userId = currentActor.requireUserId();
        StoreMembership m = require(storeId, userId, StoreMembershipStatus.APPLIED, ApiErrorCode.NOT_APPLIED);
        memberships.delete(m);
        log(StoreLogAction.REQUEST_CANCELLED, store, userId, null);
    }

    /** Accept onto the team (or waiting list); joins the store region when needed. */
    @Transactional
    public void accept(UUID storeId, UUID userId, boolean standby) {
        Store store = requireEditTeam(storeId);
        users.require(userId);
        StoreMembership m = require(storeId, userId, StoreMembershipStatus.APPLIED, ApiErrorCode.NOT_APPLIED);
        m.setStatus(standby ? StoreMembershipStatus.JUMPER : StoreMembershipStatus.MEMBER);
        m.setAddedAt(clock.instant());
        regionMemberships.addByMaintenance(userId, store.getRegionId());
        log(StoreLogAction.REQUEST_APPROVED, store, userId, null);
        if (standby) {
            log(StoreLogAction.MOVED_TO_JUMPER, store, userId, null);
        }
        notify(List.of(userId), NotificationType.STORE_REQUEST_ACCEPTED, store, null, null, null, null);
    }

    @Transactional
    public void decline(UUID storeId, UUID userId, String message) {
        Store store = requireEditTeam(storeId);
        String text = requireMessage(message);
        StoreMembership m = require(storeId, userId, StoreMembershipStatus.APPLIED, ApiErrorCode.NOT_APPLIED);
        memberships.delete(m);
        log(StoreLogAction.REQUEST_DECLINED, store, userId, text);
        notify(List.of(userId), NotificationType.STORE_REQUEST_DECLINED, store, text, null, null,
                "store/request-declined");
    }

    // ------------------------------------------------------------------------------------- invitations

    @Transactional
    public void invite(UUID storeId, UUID userId) {
        Store store = requireEditTeam(storeId);
        User user = users.requireActive(userId);
        if (rights.teamStatus(userId, storeId) != UserTeamStatus.NO_MEMBER) {
            throw ApiException.conflict(ApiErrorCode.ALREADY_IN_TEAM);
        }
        if (!PermissionService.isAtLeast(user.getRole(), Role.FOODSAVER)) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_TOO_LOW);
        }
        StoreMembership m = new StoreMembership();
        m.setStoreId(storeId);
        m.setUserId(userId);
        m.setStatus(StoreMembershipStatus.INVITED);
        m.setInvitedBy(currentActor.requireUserId());
        memberships.save(m);
        log(StoreLogAction.INVITED_TO_TEAM, store, userId, null);
        notify(List.of(userId), NotificationType.STORE_INVITATION, store, null, null,
                BellService.identifier(NotificationType.STORE_INVITATION, storeId, userId), "store/invitation");
    }

    @Transactional
    public void withdrawInvitation(UUID storeId, UUID userId) {
        Store store = requireEditTeam(storeId);
        StoreMembership m = require(storeId, userId, StoreMembershipStatus.INVITED, ApiErrorCode.NOT_INVITED);
        memberships.delete(m);
        StoreNotifications.removeBells(events, BellService.identifier(NotificationType.STORE_INVITATION, storeId, userId));
        log(StoreLogAction.INVITATION_WITHDRAWN, store, userId, null);
    }

    @Transactional
    public void acceptInvitation(UUID storeId) {
        Store store = stores.require(storeId);
        User user = currentActor.requireUser();
        StoreMembership m = require(storeId, user.getId(), StoreMembershipStatus.INVITED, ApiErrorCode.NOT_INVITED);
        checkRequirements(store, user, null, false, false);
        m.setStatus(StoreMembershipStatus.MEMBER);
        m.setAddedAt(clock.instant());
        StoreNotifications.removeBells(events, BellService.identifier(NotificationType.STORE_INVITATION, storeId, user.getId()));
        log(StoreLogAction.INVITATION_ACCEPTED, store, user.getId(), null);
        notify(managerIds(storeId), NotificationType.STORE_INVITATION_ACCEPTED, store, null, user.getDisplayName(),
                null, null);
    }

    @Transactional
    public void declineInvitation(UUID storeId) {
        Store store = stores.require(storeId);
        User user = currentActor.requireUser();
        StoreMembership m = require(storeId, user.getId(), StoreMembershipStatus.INVITED, ApiErrorCode.NOT_INVITED);
        memberships.delete(m);
        StoreNotifications.removeBells(events, BellService.identifier(NotificationType.STORE_INVITATION, storeId, user.getId()));
        log(StoreLogAction.INVITATION_DECLINED, store, user.getId(), null);
        notify(managerIds(storeId), NotificationType.STORE_INVITATION_DECLINED, store, null, user.getDisplayName(),
                null, null);
    }

    // ------------------------------------------------------------------------------------------ team

    /** Remove a member (message required) or leave (self); future sign-ups are deleted. */
    @Transactional
    public void remove(UUID storeId, UUID userId, String message) {
        Store store = stores.require(storeId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        boolean self = actor.userId().equals(userId);
        if (!self) {
            StorePermissionService.require(rights.rights(actor, store).mayEditTeam());
        }
        StoreMembership m = memberships.findByStoreIdAndUserId(storeId, userId)
                .filter(x -> x.getStatus() == StoreMembershipStatus.MEMBER || x.getStatus() == StoreMembershipStatus.JUMPER)
                .orElseThrow(() -> ApiException.conflict(ApiErrorCode.NOT_TEAM_MEMBER));
        if (m.isManager()) {
            throw ApiException.conflict(ApiErrorCode.MANAGER_CANNOT_LEAVE);
        }
        String text = self ? (blank(message) ? null : message.strip()) : requireMessage(message);
        pickupInfo.deleteFutureSignups(storeId, userId);
        memberships.delete(m);
        log(self ? StoreLogAction.LEFT_STORE : StoreLogAction.REMOVED_FROM_STORE, store, userId, text);
        if (!self) {
            notify(List.of(userId), NotificationType.STORE_MEMBER_REMOVED, store, text, null, null,
                    "store/member-removed");
        }
    }

    @Transactional
    public void addDirectly(UUID storeId, UUID userId, boolean standby) {
        Store store = requireEditTeam(storeId);
        User user = users.requireActive(userId);
        if (rights.teamStatus(userId, storeId) != UserTeamStatus.NO_MEMBER) {
            throw ApiException.conflict(ApiErrorCode.ALREADY_IN_TEAM);
        }
        if (!PermissionService.isAtLeast(user.getRole(), Role.FOODSAVER)) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_TOO_LOW);
        }
        StoreMembership m = new StoreMembership();
        m.setStoreId(storeId);
        m.setUserId(userId);
        m.setStatus(standby ? StoreMembershipStatus.JUMPER : StoreMembershipStatus.MEMBER);
        m.setAddedAt(clock.instant());
        memberships.save(m);
        regionMemberships.addByMaintenance(userId, store.getRegionId());
        log(StoreLogAction.ADDED_WITHOUT_REQUEST, store, userId, null);
        notify(List.of(userId), NotificationType.STORE_REQUEST_ACCEPTED, store, null, null, null, null);
    }

    @Transactional
    public void moveToStandby(UUID storeId, UUID userId, String message) {
        Store store = requireEditTeam(storeId);
        String text = requireMessage(message);
        StoreMembership m = require(storeId, userId, StoreMembershipStatus.MEMBER, ApiErrorCode.NOT_TEAM_MEMBER);
        if (m.isManager()) {
            throw ApiException.conflict(ApiErrorCode.MANAGER_CANNOT_LEAVE);
        }
        m.setStatus(StoreMembershipStatus.JUMPER);
        pickupInfo.deleteFutureSignups(storeId, userId);
        log(StoreLogAction.MOVED_TO_JUMPER, store, userId, text);
        notify(List.of(userId), NotificationType.STORE_MOVED_TO_STANDBY, store, text, null, null,
                "store/moved-to-standby");
    }

    @Transactional
    public void moveToTeam(UUID storeId, UUID userId) {
        Store store = requireEditTeam(storeId);
        StoreMembership m = require(storeId, userId, StoreMembershipStatus.JUMPER, ApiErrorCode.NOT_TEAM_MEMBER);
        m.setStatus(StoreMembershipStatus.MEMBER);
        log(StoreLogAction.MOVED_TO_TEAM, store, userId, null);
    }

    /** A19: max managers (coordinators exempt), appointee member / jumper with role ≥ STORE_MANAGER. */
    @Transactional
    public void appointManager(UUID storeId, UUID userId) {
        Store store = requireEditTeam(storeId);
        User user = users.require(userId);
        StoreMembership m = memberships.findByStoreIdAndUserId(storeId, userId)
                .filter(x -> x.getStatus() == StoreMembershipStatus.MEMBER || x.getStatus() == StoreMembershipStatus.JUMPER)
                .orElseThrow(() -> ApiException.conflict(ApiErrorCode.NOT_TEAM_MEMBER));
        if (m.isManager()) {
            throw ApiException.conflict(ApiErrorCode.ALREADY_MANAGER);
        }
        if (!PermissionService.isAtLeast(user.getRole(), Role.STORE_MANAGER)) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_TOO_LOW);
        }
        boolean coordinator = rights.mayCoordinateRegionStores(currentActor.requirePrincipal(), store.getRegionId());
        if (!coordinator && managerIds(storeId).size() >= properties.getStore().getMaxManagers()) {
            throw ApiException.conflict(ApiErrorCode.TOO_MANY_MANAGERS);
        }
        m.setStatus(StoreMembershipStatus.MEMBER);
        m.setManager(true);
        log(StoreLogAction.APPOINT_STORE_MANAGER, store, userId, null);
        notify(List.of(userId), NotificationType.STORE_MANAGER_APPOINTED, store, null, null, null, null);
    }

    @Transactional
    public void removeManager(UUID storeId, UUID userId, String message) {
        Store store = requireEditTeam(storeId);
        String text = requireMessage(message);
        StoreMembership m = memberships.findByStoreIdAndUserId(storeId, userId).filter(StoreMembership::isManager)
                .orElseThrow(() -> ApiException.conflict(ApiErrorCode.NOT_MANAGER));
        boolean coordinator = rights.mayCoordinateRegionStores(currentActor.requirePrincipal(), store.getRegionId());
        if (!coordinator && managerIds(storeId).size() <= 1) {
            throw ApiException.conflict(ApiErrorCode.LAST_MANAGER);
        }
        m.setManager(false);
        log(StoreLogAction.REMOVED_AS_STORE_MANAGER, store, userId, text);
        notify(List.of(userId), NotificationType.STORE_MANAGER_REMOVED, store, text, null, null,
                "store/manager-removed");
    }

    // ------------------------------------------------------------------------------------- helpers

    private Store requireEditTeam(UUID storeId) {
        Store store = stores.require(storeId);
        StorePermissionService.require(rights.rights(currentActor.requirePrincipal(), store).mayEditTeam());
        return store;
    }

    private StoreMembership require(UUID storeId, UUID userId, StoreMembershipStatus status, ApiErrorCode code) {
        return memberships.findByStoreIdAndUserId(storeId, userId).filter(m -> m.getStatus() == status)
                .orElseThrow(() -> ApiException.conflict(code));
    }

    List<UUID> managerIds(UUID storeId) {
        return memberships.findByStoreId(storeId).stream()
                .filter(m -> m.isManager() && m.getStatus() == StoreMembershipStatus.MEMBER)
                .map(StoreMembership::getUserId).toList();
    }

    private void log(StoreLogAction action, Store store, UUID userId, String reason) {
        history.record(history.event(action, store.getId()).secondarySubject(userId).region(store.getRegionId())
                .reason(reason).build());
    }

    private void notify(Collection<UUID> recipients, NotificationType type, Store store, String message,
                        String userName, String identifier, String mail) {
        if (recipients.isEmpty()) {
            return;
        }
        StoreNotifications.send(events, NotificationRequest.to(recipients, type).param("storeId", store.getId().toString())
                .param("storeName", store.getName()).param("message", message).param("userName", userName)
                .link("/stores/" + store.getId()).identifier(identifier).mail(mail).icon("store").build());
    }

    private static String requireMessage(String message) {
        if (blank(message)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        return message.strip();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
