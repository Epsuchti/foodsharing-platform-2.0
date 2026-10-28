package network.foodsharing.fsp.service;

import java.time.Clock;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.fsp.domain.FoodSharePoint;
import network.foodsharing.fsp.domain.FspAction;
import network.foodsharing.fsp.domain.FspFollower;
import network.foodsharing.fsp.domain.FspFollowerRole;
import network.foodsharing.fsp.domain.FspStatus;
import network.foodsharing.fsp.domain.FspWallPost;
import network.foodsharing.fsp.repository.FoodSharePointRepository;
import network.foodsharing.fsp.repository.FspFollowerRepository;
import network.foodsharing.fsp.repository.FspWallPostRepository;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.mail.service.MailDraft;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.region.service.GeocodingClient;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.upload.domain.Upload;
import network.foodsharing.upload.service.UploadService;
import network.foodsharing.upload.service.UploadUsage;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Food share points (FS1, upstream {@code FoodSharePointTransactions}): creation (members → INACTIVE with an
 * {@code FSP_ACTIVATE} bell), editing, activation, soft delete, managers, followers and the wall with
 * {@code FSP_NEW_POST} bells and mails.
 */
@Service
public class FspService {

    public static final String PHOTO_MODULE = "FSP_PHOTO";
    static final String MAIL_NEW_POST = "notification/FSP_NEW_POST";
    private static final Logger LOG = LoggerFactory.getLogger(FspService.class);

    /** Changes of an FSP; null = unchanged (update). */
    public record FspInput(String name, String description, String street, String postcode, String city, Double lat,
                           Double lon, UUID photoUploadId, List<UUID> managerIds) {
    }

    private final FoodSharePointRepository fsps;
    private final FspFollowerRepository followers;
    private final FspWallPostRepository posts;
    private final FspPermissionService access;
    private final CurrentActorService actors;
    private final RegionService regions;
    private final GeocodingClient geocoding;
    private final UploadService uploads;
    private final NotificationService notifications;
    private final MailOutboxService mails;
    private final UserService users;
    private final HistoryService history;
    private final AppProperties properties;
    private final Clock clock;

    public FspService(FoodSharePointRepository fsps, FspFollowerRepository followers, FspWallPostRepository posts,
                      FspPermissionService access, CurrentActorService actors, RegionService regions,
                      GeocodingClient geocoding, UploadService uploads, NotificationService notifications,
                      MailOutboxService mails, UserService users, HistoryService history, AppProperties properties,
                      Clock clock) {
        this.fsps = fsps;
        this.followers = followers;
        this.posts = posts;
        this.access = access;
        this.actors = actors;
        this.regions = regions;
        this.geocoding = geocoding;
        this.uploads = uploads;
        this.notifications = notifications;
        this.mails = mails;
        this.users = users;
        this.history = history;
        this.properties = properties;
        this.clock = clock;
    }

    public static String link(UUID fspId) {
        return "/food-share-points/" + fspId;
    }

    static String activateBell(UUID fspId) {
        return NotificationType.FSP_ACTIVATE.name() + "-" + fspId;
    }

    static String postBell(UUID fspId) {
        return NotificationType.FSP_NEW_POST.name() + "-" + fspId;
    }

    /**
     * Activators create ACTIVE FSPs; other region members create INACTIVE ones that wait for activation.
     *
     * @throws ApiException 404 REGION_NOT_FOUND, 403 ACCESS_DENIED, 400 VALIDATION_FAILED
     */
    @Transactional
    public FoodSharePoint create(UUID regionId, FspInput in) {
        SessionPrincipal actor = actors.requirePrincipal();
        regions.require(regionId);
        FspStatus status = FspRules.createStatus(access.mayAdd(actor, regionId), access.isMember(actor, regionId));
        FspPermissionService.require(status != null);
        FoodSharePoint fsp = new FoodSharePoint();
        fsp.setRegionId(regionId);
        fsp.setStatus(status);
        fsp.setCreatedByUserId(actor.userId());
        fsp.setName(required(in.name(), "name"));
        fsp.setDescription(strip(in.description()));
        fsp.setStreet(required(in.street(), "street"));
        fsp.setPostcode(required(in.postcode(), "postcode"));
        fsp.setCity(required(in.city(), "city"));
        fsp.setLat(in.lat());
        fsp.setLon(in.lon());
        locate(fsp);
        FoodSharePoint saved = fsps.save(fsp);
        if (in.photoUploadId() != null) {
            claimPhoto(saved, in.photoUploadId(), actor.userId());
        }
        List<UUID> managers = in.managerIds() == null || in.managerIds().isEmpty() ? List.of(actor.userId())
                : in.managerIds();
        history.record(event(FspAction.CREATED, saved).metadata("status", status.name()).build());
        applyManagers(saved, managers);
        if (status == FspStatus.INACTIVE) {
            Set<UUID> recipients = access.activators(regionId);
            recipients.remove(actor.userId());
            if (!recipients.isEmpty()) {
                notifications.notify(NotificationRequest.to(recipients, NotificationType.FSP_ACTIVATE)
                        .param("name", saved.getName()).link(link(saved.getId()))
                        .identifier(activateBell(saved.getId())).build());
            }
        }
        return saved;
    }

    /** Editors (mayAdd or manager). @throws ApiException 404, 403 ACCESS_DENIED, 400 */
    @Transactional
    public FoodSharePoint update(UUID fspId, FspInput in) {
        SessionPrincipal actor = actors.requirePrincipal();
        FoodSharePoint fsp = require(fspId);
        FspPermissionService.require(FspRules.mayEdit(access.requireVisible(actor, fsp)));
        HistoryEvent.Builder event = event(FspAction.UPDATED, fsp);
        boolean changed = false;
        changed |= set(event, "name", fsp.getName(), notBlank(in.name(), "name"), fsp::setName);
        changed |= set(event, "description", fsp.getDescription(), in.description() == null ? null
                : in.description().strip(), v -> fsp.setDescription(v.isEmpty() ? null : v));
        boolean moved = set(event, "street", fsp.getStreet(), notBlank(in.street(), "street"), fsp::setStreet);
        moved |= set(event, "postcode", fsp.getPostcode(), notBlank(in.postcode(), "postcode"), fsp::setPostcode);
        moved |= set(event, "city", fsp.getCity(), notBlank(in.city(), "city"), fsp::setCity);
        boolean located = set(event, "lat", fsp.getLat(), in.lat(), fsp::setLat);
        located |= set(event, "lon", fsp.getLon(), in.lon(), fsp::setLon);
        if (moved && !located) {
            fsp.setLat(null);
            fsp.setLon(null);
        }
        locate(fsp);
        changed |= moved || located;
        if (in.photoUploadId() != null && !in.photoUploadId().equals(fsp.getPhotoUploadId())) {
            UUID previous = fsp.getPhotoUploadId();
            claimPhoto(fsp, in.photoUploadId(), actor.userId());
            deletePhoto(previous);
            event.change("photo", previous, in.photoUploadId());
            changed = true;
        }
        if (changed) {
            history.record(event.build());
        }
        return fsp;
    }

    /** Soft delete (mayAdd). @throws ApiException 404, 403 ACCESS_DENIED */
    @Transactional
    public void delete(UUID fspId) {
        SessionPrincipal actor = actors.requirePrincipal();
        FoodSharePoint fsp = require(fspId);
        FspPermissionService.require(FspRules.mayDelete(access.requireVisible(actor, fsp)));
        history.record(event(FspAction.DELETED, fsp).build());
        fsp.setDeletedAt(clock.instant());
        notifications.removeBells(activateBell(fspId));
        notifications.removeBells(postBell(fspId));
    }

    /** Activate / deactivate (mayAdd). @throws ApiException 404, 403 ACCESS_DENIED */
    @Transactional
    public FoodSharePoint setStatus(UUID fspId, FspStatus status) {
        SessionPrincipal actor = actors.requirePrincipal();
        FoodSharePoint fsp = require(fspId);
        FspPermissionService.require(FspRules.mayActivate(access.requireVisible(actor, fsp)));
        if (status == null) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "status");
        }
        if (fsp.getStatus() != status) {
            history.record(event(status == FspStatus.ACTIVE ? FspAction.ACTIVATED : FspAction.FIELD_CHANGED, fsp)
                    .change("status", fsp.getStatus(), status).build());
            fsp.setStatus(status);
            if (status == FspStatus.ACTIVE) {
                notifications.removeBells(activateBell(fspId));
            }
        }
        return fsp;
    }

    /**
     * Replaces the managers (editors); they must be active members of the FSP's region. Former managers keep
     * following. @throws ApiException 404, 403 ACCESS_DENIED, 400 VALIDATION_FAILED
     */
    @Transactional
    public FoodSharePoint setManagers(UUID fspId, List<UUID> userIds) {
        SessionPrincipal actor = actors.requirePrincipal();
        FoodSharePoint fsp = require(fspId);
        FspPermissionService.require(FspRules.mayEdit(access.requireVisible(actor, fsp)));
        applyManagers(fsp, userIds);
        return fsp;
    }

    /** Follow (or change the bell/mail flags). @throws ApiException 404, 403 ACCESS_DENIED */
    @Transactional
    public FspFollower follow(UUID fspId, boolean bell, boolean mail) {
        SessionPrincipal actor = actors.requirePrincipal();
        FoodSharePoint fsp = require(fspId);
        FspRules.Facts facts = access.requireVisible(actor, fsp);
        FspFollower row = followers.findByFspIdAndUserId(fspId, actor.userId()).orElse(null);
        if (row == null) {
            FspPermissionService.require(FspRules.mayFollow(facts));
            row = new FspFollower();
            row.setFspId(fspId);
            row.setUserId(actor.userId());
            row.setRole(FspFollowerRole.FOLLOWER);
            history.record(event(FspAction.FOLLOWED, fsp).metadata("bell", bell).metadata("mail", mail).build());
        } else if (row.isBell() != bell || row.isMail() != mail) {
            history.record(event(FspAction.FIELD_CHANGED, fsp).field("follow")
                    .metadata("bell", bell).metadata("mail", mail).build());
        }
        row.setBell(bell);
        row.setMail(mail);
        return followers.save(row);
    }

    /** Unfollow; managers stay responsible and only switch their bell and mail off. */
    @Transactional
    public void unfollow(UUID fspId) {
        SessionPrincipal actor = actors.requirePrincipal();
        FoodSharePoint fsp = require(fspId);
        followers.findByFspIdAndUserId(fspId, actor.userId()).ifPresent(row -> {
            history.record(event(FspAction.UNFOLLOWED, fsp).build());
            if (row.getRole() == FspFollowerRole.MANAGER) {
                row.setBell(false);
                row.setMail(false);
            } else {
                followers.delete(row);
            }
        });
    }

    /**
     * Wall post; followers get a grouped bell and/or a mail per their flags (not the author).
     *
     * @throws ApiException 404, 403 ACCESS_DENIED, 400 VALIDATION_FAILED
     */
    @Transactional
    public FspWallPost post(UUID fspId, String body) {
        User author = actors.requireUser();
        SessionPrincipal actor = actors.requirePrincipal();
        FoodSharePoint fsp = require(fspId);
        FspPermissionService.require(FspRules.mayPost(access.requireVisible(actor, fsp)));
        if (body == null || body.isBlank() || body.strip().length() > 4000) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "body");
        }
        FspWallPost post = new FspWallPost();
        post.setFspId(fspId);
        post.setAuthorId(author.getId());
        post.setBody(body.strip());
        FspWallPost saved = posts.save(post);
        history.record(event(FspAction.WALL_POST_CREATED, fsp).metadata("postId", saved.getId().toString()).build());
        notifyFollowers(fsp, saved, author);
        return saved;
    }

    /** ORGA / FSP group admins / author. @throws ApiException 404 WALL_POST_NOT_FOUND, 403 ACCESS_DENIED */
    @Transactional
    public void deletePost(UUID fspId, UUID postId) {
        SessionPrincipal actor = actors.requirePrincipal();
        FoodSharePoint fsp = require(fspId);
        FspWallPost post = posts.findById(postId).filter(p -> p.getFspId().equals(fspId) && p.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.WALL_POST_NOT_FOUND));
        FspPermissionService.require(FspRules.mayDeletePost(access.facts(actor, fsp),
                post.getAuthorId().equals(actor.userId())));
        post.setDeletedAt(clock.instant());
        history.record(event(FspAction.WALL_POST_DELETED, fsp).metadata("postId", postId.toString()).build());
    }

    /** Account deletion (A40): follower and manager rows are removed; posts stay with the anonymised author. */
    @Transactional
    public int removeUser(UUID userId) {
        List<FspFollower> rows = followers.findByUserId(userId);
        for (FspFollower row : rows) {
            fsps.findById(row.getFspId()).ifPresent(fsp -> history.record(event(
                    row.getRole() == FspFollowerRole.MANAGER ? FspAction.MANAGER_REMOVED : FspAction.UNFOLLOWED, fsp)
                    .reason("account deleted").metadata("userId", userId.toString()).build()));
        }
        followers.deleteAll(rows);
        return rows.size();
    }

    private void applyManagers(FoodSharePoint fsp, List<UUID> userIds) {
        Set<UUID> wanted = new LinkedHashSet<>(userIds == null ? List.of() : userIds);
        if (wanted.isEmpty() || wanted.size() > 20) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "managerIds");
        }
        Set<UUID> members = regions.activeMembersAmong(Set.of(fsp.getRegionId()), wanted);
        if (!members.containsAll(wanted)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "managerIds");
        }
        Map<UUID, FspFollower> rows = new HashMap<>();
        followers.findByFspId(fsp.getId()).forEach(row -> rows.put(row.getUserId(), row));
        for (FspFollower row : rows.values()) {
            if (row.getRole() == FspFollowerRole.MANAGER && !wanted.contains(row.getUserId())) {
                row.setRole(FspFollowerRole.FOLLOWER);
                history.record(event(FspAction.MANAGER_REMOVED, fsp).secondarySubject(row.getUserId()).build());
            }
        }
        for (UUID userId : wanted) {
            FspFollower row = rows.get(userId);
            if (row != null && row.getRole() == FspFollowerRole.MANAGER) {
                continue;
            }
            if (row == null) {
                row = new FspFollower();
                row.setFspId(fsp.getId());
                row.setUserId(userId);
                row.setBell(true);
                row.setMail(false);
            }
            row.setRole(FspFollowerRole.MANAGER);
            followers.save(row);
            history.record(event(FspAction.MANAGER_ADDED, fsp).secondarySubject(userId).build());
        }
    }

    private void notifyFollowers(FoodSharePoint fsp, FspWallPost post, User author) {
        Set<UUID> bell = new LinkedHashSet<>();
        Set<UUID> mail = new LinkedHashSet<>();
        for (FspFollower row : followers.findByFspId(fsp.getId())) {
            if (row.getUserId().equals(author.getId())) {
                continue;
            }
            if (row.isBell()) {
                bell.add(row.getUserId());
            }
            if (row.isMail()) {
                mail.add(row.getUserId());
            }
        }
        String identifier = postBell(fsp.getId());
        if (!bell.isEmpty()) {
            notifications.removeBells(identifier);
            notifications.notify(NotificationRequest.to(bell, NotificationType.FSP_NEW_POST)
                    .param("name", fsp.getName()).param("author", author.getDisplayName())
                    .link(link(fsp.getId())).identifier(identifier).build());
        }
        if (mail.isEmpty()) {
            return;
        }
        String link = properties.getBaseUrl() + link(fsp.getId());
        for (User user : users.findActive(mail)) {
            if (user.getEmail() == null) {
                continue;
            }
            Map<String, Object> variables = new HashMap<>();
            variables.put("displayName", user.getDisplayName());
            variables.put("name", fsp.getName());
            variables.put("author", author.getDisplayName());
            variables.put("body", post.getBody().length() > 2000 ? post.getBody().substring(0, 2000) + "…"
                    : post.getBody());
            variables.put("link", link);
            mails.enqueue(new MailDraft(user.getEmail(), MAIL_NEW_POST, user.getPreferredLanguage(), variables,
                    user.getId(), NotificationType.FSP_NEW_POST.category().name(), identifier));
        }
    }

    private FoodSharePoint require(UUID fspId) {
        return fsps.findById(fspId).filter(f -> f.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.FSP_NOT_FOUND));
    }

    private void locate(FoodSharePoint fsp) {
        if (fsp.getLat() != null && fsp.getLon() != null) {
            if (Math.abs(fsp.getLat()) > 90 || Math.abs(fsp.getLon()) > 180) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "lat");
            }
            return;
        }
        fsp.setLat(null);
        fsp.setLon(null);
        try {
            geocoding.geocodePostcode("CH", fsp.getPostcode()).ifPresent(point -> {
                fsp.setLat(point.latitude());
                fsp.setLon(point.longitude());
            });
        } catch (GeocodingClient.GeocodingUnavailableException e) {
            LOG.info("Food share point not geocoded: {}", e.getMessage());
        }
    }

    private void claimPhoto(FoodSharePoint fsp, UUID uploadId, UUID actorId) {
        Upload upload = uploads.find(uploadId);
        if (!actorId.equals(upload.getOwnerUserId())) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        uploads.claim(uploadId, new UploadUsage(PHOTO_MODULE, fsp.getId()));
        fsp.setPhotoUploadId(uploadId);
    }

    private void deletePhoto(UUID uploadId) {
        if (uploadId == null) {
            return;
        }
        try {
            uploads.delete(uploads.find(uploadId));
        } catch (ApiException alreadyGone) {
            // nothing to delete
        }
    }

    private HistoryEvent.Builder event(FspAction action, FoodSharePoint fsp) {
        return history.event(action, fsp.getId()).region(fsp.getRegionId());
    }

    private static String required(String value, String field) {
        String stripped = strip(value);
        if (stripped == null) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", field);
        }
        return stripped;
    }

    private static String notBlank(String value, String field) {
        return value == null ? null : required(value, field);
    }

    private static String strip(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static <T> boolean set(HistoryEvent.Builder event, String field, T current, T next, Consumer<T> setter) {
        if (next == null || Objects.equals(current, next)) {
            return false;
        }
        event.change(field, "description".equals(field) ? null : current, "description".equals(field) ? null : next);
        setter.accept(next);
        return true;
    }
}
