package network.foodsharing.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.ScopedPermissionResolver;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.upload.domain.Upload;
import network.foodsharing.upload.service.UploadService;
import network.foodsharing.upload.service.UploadUsage;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.domain.UserNote;
import network.foodsharing.user.domain.UserNoteKind;
import network.foodsharing.user.repository.UserNoteRepository;
import network.foodsharing.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * A3 profile photo and A7 admin notes (formerly asserted by AccountFlowIT): only an own image upload becomes the photo,
 * replacing it deletes the previous upload, removing it clears the avatar; notes are for the user's administrators,
 * deletable by the author or ORGA, and a deleted note is gone (404).
 */
class UserAccountPhotoAndNotesTest {

    final UserRepository users = mock(UserRepository.class);
    final UserNoteRepository notes = mock(UserNoteRepository.class);
    final UploadService uploads = mock(UploadService.class);
    final CurrentActorService actors = mock(CurrentActorService.class);
    final ScopedPermissionResolver resolver = mock(ScopedPermissionResolver.class);
    UserAccountService accounts;
    User guide;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ObjectProvider<ScopedPermissionResolver> resolvers = mock(ObjectProvider.class);
        when(resolvers.getIfAvailable()).thenReturn(resolver);
        accounts = new UserAccountService(users, notes, uploads, actors, new PermissionService(resolvers),
                mock(HistoryService.class, RETURNS_DEEP_STUBS));
        guide = new User();
        guide.setId(UUID.randomUUID());
        when(users.findById(guide.getId())).thenReturn(Optional.of(guide));
        signedIn(guide.getId(), Role.FOODSAVER);
    }

    private void signedIn(UUID userId, Role role) {
        when(actors.requireUserId()).thenReturn(userId);
        when(actors.requirePrincipal()).thenReturn(new SessionPrincipal(userId, UUID.randomUUID(), role, "x@local"));
    }

    private Upload upload(UUID owner, String mime) {
        Upload upload = new Upload();
        org.springframework.test.util.ReflectionTestUtils.setField(upload, "id", UUID.randomUUID());
        upload.setOwnerUserId(owner);
        upload.setMimeType(mime);
        when(uploads.find(upload.getId())).thenReturn(upload);
        return upload;
    }

    private static ApiErrorCode refusal(Runnable call) {
        ApiException ex = catchThrowableOfType(ApiException.class, call::run);
        return ex == null ? null : ex.getCode();
    }

    @Test
    void onlyAnOwnImageBecomesThePhotoAndReplacingItDeletesTheOldOne() {
        Upload text = upload(guide.getId(), "text/plain");
        assertThat(refusal(() -> accounts.setPhoto(text.getId()))).isEqualTo(ApiErrorCode.UPLOAD_NOT_IMAGE);
        Upload foreign = upload(UUID.randomUUID(), "image/png");
        assertThat(refusal(() -> accounts.setPhoto(foreign.getId()))).isEqualTo(ApiErrorCode.ACCESS_DENIED);
        verify(uploads, never()).claim(any(), any());

        Upload first = upload(guide.getId(), "image/png");
        accounts.setPhoto(first.getId());
        assertThat(guide.getAvatarUrl()).isEqualTo("/api/uploads/" + first.getId() + "?size=md");
        verify(uploads).claim(first.getId(), new UploadUsage(UserAccountService.PHOTO_MODULE, guide.getId()));

        Upload second = upload(guide.getId(), "image/jpeg");
        accounts.setPhoto(second.getId());
        verify(uploads).delete(first);
        assertThat(guide.getPhotoUploadId()).isEqualTo(second.getId());

        accounts.deletePhoto();
        verify(uploads).delete(second);
        assertThat(guide.getAvatarUrl()).isNull();
        assertThat(guide.getPhotoUploadId()).isNull();
    }

    @Test
    void notesAreForAdministratorsAndDeletableByTheAuthorOrOrga() {
        UUID orga = UUID.randomUUID();
        UUID ambassador = UUID.randomUUID();
        when(resolver.mayAdministrateUser(ambassador, guide.getId())).thenReturn(true);
        UserNote note = new UserNote();
        org.springframework.test.util.ReflectionTestUtils.setField(note, "id", UUID.randomUUID());
        note.setUserId(guide.getId());
        note.setKind(UserNoteKind.ADMIN_NOTE);
        note.setAuthorId(orga);
        when(notes.save(any())).thenReturn(note);

        signedIn(UUID.randomUUID(), Role.FOODSAVER);
        assertThat(refusal(() -> accounts.listNotes(guide.getId()))).isEqualTo(ApiErrorCode.ACCESS_DENIED);
        assertThat(refusal(() -> accounts.addNote(guide.getId(), "x"))).isEqualTo(ApiErrorCode.ACCESS_DENIED);

        signedIn(orga, Role.ORGA);
        assertThat(accounts.addNote(guide.getId(), " Hat zweimal kurzfristig abgesagt. ").mayDelete()).isTrue();

        when(notes.findById(note.getId())).thenReturn(Optional.of(note));
        signedIn(ambassador, Role.AMBASSADOR);
        assertThat(refusal(() -> accounts.deleteNote(guide.getId(), note.getId()))).isEqualTo(ApiErrorCode.ACCESS_DENIED);
        signedIn(orga, Role.ORGA);
        accounts.deleteNote(guide.getId(), note.getId());
        verify(notes).delete(note);

        when(notes.findById(note.getId())).thenReturn(Optional.empty());
        assertThat(refusal(() -> accounts.deleteNote(guide.getId(), note.getId())))
                .isEqualTo(ApiErrorCode.NOTE_NOT_FOUND);
    }
}
