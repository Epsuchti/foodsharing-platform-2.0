package network.foodsharing.user.service;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.persistence.UserRows;
import network.foodsharing.history.service.HistoryRetentionService;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.upload.service.UploadService;
import network.foodsharing.user.domain.SleepStatus;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.domain.UserAction;
import network.foodsharing.user.domain.UserArchive;
import network.foodsharing.user.repository.UserArchiveRepository;
import network.foodsharing.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Account deletion (A13, A40, upstream {@code deleteFoodsaver} + {@code fs_foodsaver_archive}): the user themself
 * (with an optional reason) or ORGA. ACCOUNT_DELETED is recorded first (with the reason), then every
 * {@link AccountDeletionContributor} removes its module's personal rows, then the user row is anonymised (authored
 * content stays attributed to "Gelöschtes Mitglied") and a snapshot goes to {@code user_archive}. The
 * {@code no_automatic_delete} flag ({@code mayAdministrateUser}) protects from the inactivity clean-up only.
 */
@Service
public class AccountDeletionService {

    public static final String DELETED_NAME = "Gelöschtes Mitglied";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int MAX_SNAPSHOT = 32600;

    private final UserRepository users;
    private final UserArchiveRepository archives;
    private final List<AccountDeletionContributor> contributors;
    private final UploadService uploads;
    private final CurrentActorService actors;
    private final PermissionService permissions;
    private final HistoryService history;
    private final HistoryRetentionService historyRetention;
    private final EntityManager em;
    private final Clock clock;

    public AccountDeletionService(UserRepository users, UserArchiveRepository archives,
                                  List<AccountDeletionContributor> contributors, UploadService uploads,
                                  CurrentActorService actors, PermissionService permissions, HistoryService history,
                                  HistoryRetentionService historyRetention,
                                  EntityManager em, Clock clock) {
        this.users = users;
        this.archives = archives;
        this.contributors = contributors;
        this.uploads = uploads;
        this.actors = actors;
        this.permissions = permissions;
        this.history = history;
        this.historyRetention = historyRetention;
        this.em = em;
        this.clock = clock;
    }

    public static String deletedEmail(UUID userId) {
        return "deleted-" + userId + "@deleted.invalid";
    }

    /** The current user deletes their own account. */
    @Transactional
    public void deleteSelf(String reason) {
        SessionPrincipal actor = actors.requirePrincipal();
        delete(actor, actor.userId(), reason);
    }

    /** ORGA deletes an account. @throws ApiException 403 unless ORGA, 404 USER_NOT_FOUND */
    @Transactional
    public void deleteByOrga(UUID userId, String reason) {
        SessionPrincipal actor = actors.requirePrincipal();
        if (!permissions.isOrga(actor)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        delete(actor, userId, reason);
    }

    /** @throws ApiException 403 unless mayAdministrateUser, 404 USER_NOT_FOUND */
    @Transactional
    public User setNoAutomaticDelete(UUID userId, boolean value) {
        SessionPrincipal actor = actors.requirePrincipal();
        User user = requireNotDeleted(userId);
        permissions.requireMayAdministrateUser(actor, userId);
        if (user.isNoAutomaticDelete() != value) {
            history.record(history.event(UserAction.NO_AUTOMATIC_DELETE_CHANGED, userId)
                    .change("noAutomaticDelete", user.isNoAutomaticDelete(), value).build());
            user.setNoAutomaticDelete(value);
        }
        return user;
    }

    private User requireNotDeleted(UUID userId) {
        return users.findById(userId).filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.USER_NOT_FOUND));
    }

    private void delete(SessionPrincipal actor, UUID userId, String rawReason) {
        User user = requireNotDeleted(userId);
        String reason = rawReason == null || rawReason.isBlank() ? null : rawReason.strip();
        if (reason != null && reason.length() > 1000) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "reason");
        }
        Instant now = clock.instant();
        // personal-data changes are kept while the account exists (plan §10.1): values go before the deletion entry
        historyRetention.anonymiseAccountLifetime(userId);
        history.record(history.event(UserAction.ACCOUNT_DELETED, userId).reason(reason)
                .metadata("self", actor.userId().equals(userId)).build());
        String snapshot = snapshot(user);
        contributors.forEach(contributor -> contributor.onAccountDeleted(userId));
        UserRows.delete(em, "UserNote", "userId", userId);
        UUID photo = user.getPhotoUploadId();
        anonymise(user, actor.userId(), reason, now);
        if (photo != null) {
            try {
                uploads.delete(uploads.find(photo));
            } catch (ApiException alreadyGone) {
                // nothing to delete
            }
        }
        UserArchive archive = new UserArchive();
        archive.setUserId(userId);
        archive.setSnapshotJson(snapshot);
        archive.setReason(reason);
        archive.setDeletedBy(actor.userId());
        archives.save(archive);
    }

    static String snapshot(User user) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("email", user.getEmail());
        data.put("displayName", user.getDisplayName());
        data.put("firstName", user.getFirstName());
        data.put("lastName", user.getLastName());
        data.put("foodsaverNumber", user.getFoodsaverNumber());
        data.put("role", user.getRole() == null ? null : user.getRole().name());
        data.put("verified", user.isVerified());
        data.put("homeRegionId", user.getHomeRegionId());
        data.put("mobilePhone", user.getMobilePhone());
        data.put("landlinePhone", user.getLandlinePhone());
        data.put("street", user.getStreet());
        data.put("postcode", user.getPostcode());
        data.put("city", user.getCity());
        data.put("country", user.getCountry());
        data.put("dateOfBirth", user.getDateOfBirth() == null ? null : user.getDateOfBirth().toString());
        data.put("registeredAt", user.getRegisteredAt() == null ? null : user.getRegisteredAt().toString());
        data.put("lastLoginAt", user.getLastLoginAt() == null ? null : user.getLastLoginAt().toString());
        String json = JSON.writeValueAsString(data);
        return json.length() <= MAX_SNAPSHOT ? json : json.substring(0, MAX_SNAPSHOT);
    }

    static void anonymise(User user, UUID deletedBy, String reason, Instant now) {
        user.setEmail(deletedEmail(user.getId()));
        user.setEmailVerifiedAt(null);
        user.setEmailBouncedAt(null);
        user.setDisplayName(DELETED_NAME);
        user.setFirstName(null);
        user.setLastName(null);
        user.setGender(network.foodsharing.user.domain.Gender.NOT_SELECTED);
        user.setDateOfBirth(null);
        user.setMobilePhone(null);
        user.setLandlinePhone(null);
        user.setHomepage(null);
        user.setStreet(null);
        user.setPostcode(null);
        user.setCity(null);
        user.setCountry(null);
        user.setLatitude(null);
        user.setLongitude(null);
        user.setHomeRegionId(null);
        user.setAboutMePublic(null);
        user.setAboutMeInternal(null);
        user.setPositionDescription(null);
        user.setAvatarUrl(null);
        user.setPhotoUploadId(null);
        user.setActivityFilters(null);
        user.setSleepStatus(SleepStatus.NONE);
        user.setSleepFrom(null);
        user.setSleepUntil(null);
        user.setSleepMessage(null);
        user.setTelegramChatId(null);
        user.setMailboxId(null);
        user.setNewsletterOptIn(false);
        user.setInfoMailMessage(false);
        user.setRole(Role.FOODSHARER);
        user.setQuizRole(Role.FOODSHARER);
        user.setVerified(false);
        user.setVerifiedAt(null);
        user.setActive(false);
        user.setDeletedAt(now);
        user.setDeletedBy(deletedBy);
        user.setDeleteReason(reason);
    }
}
