package network.foodsharing.user.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.upload.domain.Upload;
import network.foodsharing.upload.service.UploadService;
import network.foodsharing.upload.service.UploadUsage;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.domain.UserAction;
import network.foodsharing.user.domain.UserNote;
import network.foodsharing.user.domain.UserNoteKind;
import network.foodsharing.user.repository.UserNoteRepository;
import network.foodsharing.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account pieces of M6 B3: profile photo (A3, C10), admin notes wall (A7, {@code mayAdministrateUser}), bounce
 * handling (A18) and the info-mail / mention settings (A20).
 */
@Service
public class UserAccountService {

    /** Upload usage module of profile photos; readable by every signed-in user. */
    public static final String PHOTO_MODULE = "USER_PHOTO";

    /** A note with the delete right of the current viewer. */
    public record NoteView(UserNote note, boolean mayDelete) {
    }

    private final UserRepository users;
    private final UserNoteRepository notes;
    private final UploadService uploads;
    private final CurrentActorService actors;
    private final PermissionService permissions;
    private final HistoryService history;

    public UserAccountService(UserRepository users, UserNoteRepository notes, UploadService uploads,
                              CurrentActorService actors, PermissionService permissions, HistoryService history) {
        this.users = users;
        this.notes = notes;
        this.uploads = uploads;
        this.actors = actors;
        this.permissions = permissions;
        this.history = history;
    }

    public static String photoUrl(UUID uploadId) {
        return "/api/uploads/" + uploadId + "?size=md";
    }

    /**
     * Sets an own (cropped, client-side) image upload as profile photo; the previous photo upload is deleted.
     *
     * @throws ApiException 404 UPLOAD_NOT_FOUND, 403 ACCESS_DENIED (not own), 400 UPLOAD_NOT_IMAGE
     */
    @Transactional
    public User setPhoto(UUID uploadId) {
        UUID me = actors.requireUserId();
        Upload upload = uploads.find(uploadId);
        if (!me.equals(upload.getOwnerUserId())) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        if (upload.getMimeType() == null || !upload.getMimeType().startsWith("image/")) {
            throw ApiException.badRequest(ApiErrorCode.UPLOAD_NOT_IMAGE);
        }
        User user = users.findById(me).orElseThrow();
        if (uploadId.equals(user.getPhotoUploadId())) {
            return user;
        }
        uploads.claim(uploadId, new UploadUsage(PHOTO_MODULE, me));
        releasePrevious(user);
        history.record(history.event(UserAction.PHOTO_CHANGED, me)
                .change("photoUploadId", user.getPhotoUploadId(), uploadId).build());
        user.setPhotoUploadId(uploadId);
        user.setAvatarUrl(photoUrl(uploadId));
        return user;
    }

    /** Removes the own photo (upload deleted, avatar cleared). */
    @Transactional
    public User deletePhoto() {
        UUID me = actors.requireUserId();
        User user = users.findById(me).orElseThrow();
        if (user.getPhotoUploadId() == null && user.getAvatarUrl() == null) {
            return user;
        }
        history.record(history.event(UserAction.PHOTO_CHANGED, me)
                .change("photoUploadId", user.getPhotoUploadId(), null).build());
        releasePrevious(user);
        user.setPhotoUploadId(null);
        user.setAvatarUrl(null);
        return user;
    }

    private void releasePrevious(User user) {
        if (user.getPhotoUploadId() != null) {
            try {
                uploads.delete(uploads.find(user.getPhotoUploadId()));
            } catch (ApiException alreadyGone) {
                // nothing to release
            }
        }
    }

    /** Admin notes about a user, newest first (A7). @throws ApiException 403 unless mayAdministrateUser, 404 */
    @Transactional(readOnly = true)
    public List<NoteView> listNotes(UUID userId) {
        SessionPrincipal actor = requireAdministers(userId);
        boolean orga = permissions.isOrga(actor);
        return notes.findByUserIdAndKindOrderByCreatedAtDescIdDesc(userId, UserNoteKind.ADMIN_NOTE).stream()
                .map(note -> new NoteView(note, orga || actor.userId().equals(note.getAuthorId()))).toList();
    }

    /** @throws ApiException 403 unless mayAdministrateUser, 404 USER_NOT_FOUND, 400 VALIDATION_FAILED */
    @Transactional
    public NoteView addNote(UUID userId, String body) {
        SessionPrincipal actor = requireAdministers(userId);
        if (body == null || body.isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        UserNote note = new UserNote();
        note.setUserId(userId);
        note.setKind(UserNoteKind.ADMIN_NOTE);
        note.setText(body.strip());
        note.setAuthorId(actor.userId());
        note = notes.save(note);
        history.record(history.event(UserAction.NOTE_ADDED, userId).metadata("noteId", note.getId().toString())
                .build());
        return new NoteView(note, true);
    }

    /** Author or ORGA. @throws ApiException 403, 404 NOTE_NOT_FOUND */
    @Transactional
    public void deleteNote(UUID userId, UUID noteId) {
        SessionPrincipal actor = requireAdministers(userId);
        UserNote note = notes.findById(noteId)
                .filter(n -> n.getUserId().equals(userId) && n.getKind() == UserNoteKind.ADMIN_NOTE)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.NOTE_NOT_FOUND));
        if (!permissions.isOrga(actor) && !actor.userId().equals(note.getAuthorId())) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        notes.delete(note);
        history.record(history.event(UserAction.NOTE_DELETED, userId).metadata("noteId", noteId.toString()).build());
    }

    /** Self, ORGA or {@code mayAdministrateUser} clear the bounce flag (A18). @throws ApiException 403, 404 */
    @Transactional
    public User clearBounce(UUID userId) {
        SessionPrincipal actor = actors.requirePrincipal();
        if (!actor.userId().equals(userId) && !permissions.mayAdministrateUser(actor, userId)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound(ApiErrorCode.USER_NOT_FOUND));
        if (user.getEmailBouncedAt() != null) {
            history.record(history.event(UserAction.BOUNCE_CLEARED, userId)
                    .change("emailBouncedAt", user.getEmailBouncedAt(), null).build());
            user.setEmailBouncedAt(null);
        }
        return user;
    }

    /**
     * Users whose address bounced: without region ORGA only (all users); with region the region administrators
     * (users whose home region it is).
     */
    @Transactional(readOnly = true)
    public Page<User> listBounced(UUID regionId, int page, int size) {
        SessionPrincipal actor = actors.requirePrincipal();
        if (regionId == null) {
            permissions.requireOrga(actor);
            return users.findByEmailBouncedAtIsNotNullOrderByEmailBouncedAtDescIdDesc(PageRequest.of(page, size));
        }
        permissions.requireMayAdministrateRegion(actor, regionId);
        return users.findByHomeRegionIdAndEmailBouncedAtIsNotNullOrderByEmailBouncedAtDescIdDesc(regionId,
                PageRequest.of(page, size));
    }

    /** Info mail and mention toggles of the current user (A20); INFO_MAIL_CHANGED / SETTINGS_CHANGED. */
    @Transactional
    public User updateMailSettings(Boolean infoMailMessage, Boolean mentionNotifications) {
        UUID me = actors.requireUserId();
        User user = users.findById(me).orElseThrow();
        if (infoMailMessage != null && infoMailMessage != user.isInfoMailMessage()) {
            history.record(history.event(UserAction.INFO_MAIL_CHANGED, me)
                    .change("infoMailMessage", user.isInfoMailMessage(), infoMailMessage).build());
            user.setInfoMailMessage(infoMailMessage);
        }
        if (mentionNotifications != null && !Objects.equals(mentionNotifications, user.isMentionNotifications())) {
            history.record(history.event(UserAction.SETTINGS_CHANGED, me)
                    .change("mentionNotifications", user.isMentionNotifications(), mentionNotifications).build());
            user.setMentionNotifications(mentionNotifications);
        }
        return user;
    }

    /** Does the user want {@code FORUM_MENTION} bells (A20)? Unknown users: false. */
    @Transactional(readOnly = true)
    public boolean mentionNotificationsEnabled(UUID userId) {
        return users.findById(userId).map(User::isMentionNotifications).orElse(false);
    }

    private SessionPrincipal requireAdministers(UUID userId) {
        SessionPrincipal actor = actors.requirePrincipal();
        users.findById(userId).orElseThrow(() -> ApiException.notFound(ApiErrorCode.USER_NOT_FOUND));
        permissions.requireMayAdministrateUser(actor, userId);
        return actor;
    }
}
