package network.foodsharing.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryRetentionService;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.ScopedPermissionResolver;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.upload.service.UploadService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.repository.UserArchiveRepository;
import network.foodsharing.user.repository.UserNoteRepository;
import network.foodsharing.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Who may administer an account (moved from the e2e specs a13-delete, a18-bounce, a7-notes and a6-profile, decision
 * 180): only ORGA deletes another account, the global bounce list is ORGA's and a region's list its administrators',
 * the bounce flag is cleared by the member or an administrator, internal notes are read by administrators only (never
 * by the member herself), and a deleted (inactive) account has no profile.
 */
class AccountAdminRulesTest {

    static final UUID MEMBER = UUID.fromString("00000000-0000-4000-8000-00000000000a");
    static final UUID AMBASSADOR = UUID.fromString("00000000-0000-4000-8000-00000000000b");
    static final UUID ORGA = UUID.fromString("00000000-0000-4000-8000-00000000000c");
    static final UUID STRANGER = UUID.fromString("00000000-0000-4000-8000-00000000000e");
    static final UUID REGION = UUID.fromString("00000000-0000-4000-8000-0000000000d1");
    static final UUID OTHER_REGION = UUID.fromString("00000000-0000-4000-8000-0000000000d2");

    final UserRepository users = mock(UserRepository.class);
    final UserNoteRepository notes = mock(UserNoteRepository.class);
    final CurrentActorService actors = mock(CurrentActorService.class);
    final ScopedPermissionResolver resolver = mock(ScopedPermissionResolver.class);
    PermissionService permissions;
    UserAccountService accounts;
    AccountDeletionService deletion;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ObjectProvider<ScopedPermissionResolver> resolvers = mock(ObjectProvider.class);
        when(resolvers.getIfAvailable()).thenReturn(resolver);
        // the ambassador administers the member and her home region, nothing else
        when(resolver.mayAdministrateUser(AMBASSADOR, MEMBER)).thenReturn(true);
        when(resolver.mayAdministrateRegion(AMBASSADOR, REGION)).thenReturn(true);
        permissions = new PermissionService(resolvers);
        accounts = new UserAccountService(users, notes, mock(UploadService.class), actors, permissions,
                mock(HistoryService.class, RETURNS_DEEP_STUBS));
        deletion = new AccountDeletionService(users, mock(UserArchiveRepository.class), List.of(),
                mock(UploadService.class), actors, permissions, mock(HistoryService.class),
                mock(HistoryRetentionService.class), mock(EntityManager.class),
                Clock.fixed(Instant.parse("2026-11-02T09:00:00Z"), ZoneOffset.UTC));
        User member = new User();
        member.setId(MEMBER);
        member.setActive(true);
        when(users.findById(MEMBER)).thenReturn(Optional.of(member));
        when(users.findByEmailBouncedAtIsNotNullOrderByEmailBouncedAtDescIdDesc(any(Pageable.class)))
                .thenReturn(Page.empty());
        when(users.findByHomeRegionIdAndEmailBouncedAtIsNotNullOrderByEmailBouncedAtDescIdDesc(any(), any(Pageable.class)))
                .thenReturn(Page.empty());
    }

    private void signedInAs(UUID userId, Role role) {
        when(actors.requirePrincipal()).thenReturn(new SessionPrincipal(userId, UUID.randomUUID(), role, "x@local"));
    }

    private static ApiErrorCode refusal(Runnable call) {
        ApiException ex = catchThrowableOfType(ApiException.class, call::run);
        return ex == null ? null : ex.getCode();
    }

    @Test
    void onlyOrgaDeletesAnotherAccount() {
        signedInAs(AMBASSADOR, Role.AMBASSADOR);
        assertThat(refusal(() -> deletion.deleteByOrga(MEMBER, "Wunsch"))).isEqualTo(ApiErrorCode.ACCESS_DENIED);
        verifyNoInteractions(users);
    }

    @Test
    void theGlobalBounceListIsOrgasAndARegionListItsAdministrators() {
        signedInAs(AMBASSADOR, Role.AMBASSADOR);
        assertThat(refusal(() -> accounts.listBounced(null, 0, 20))).isEqualTo(ApiErrorCode.ACCESS_DENIED);
        assertThat(refusal(() -> accounts.listBounced(OTHER_REGION, 0, 20))).isEqualTo(ApiErrorCode.ACCESS_DENIED);
        accounts.listBounced(REGION, 0, 20);
        verify(users).findByHomeRegionIdAndEmailBouncedAtIsNotNullOrderByEmailBouncedAtDescIdDesc(eq(REGION),
                any(Pageable.class));
        verify(users, never()).findByEmailBouncedAtIsNotNullOrderByEmailBouncedAtDescIdDesc(any(Pageable.class));

        signedInAs(MEMBER, Role.FOODSAVER);
        assertThat(refusal(() -> accounts.listBounced(null, 0, 20))).isEqualTo(ApiErrorCode.ACCESS_DENIED);

        signedInAs(ORGA, Role.ORGA);
        accounts.listBounced(null, 0, 20);
        verify(users).findByEmailBouncedAtIsNotNullOrderByEmailBouncedAtDescIdDesc(any(Pageable.class));
    }

    @Test
    void theBounceFlagIsClearedByTheMemberOrAnAdministrator() {
        users.findById(MEMBER).orElseThrow().setEmailBouncedAt(Instant.parse("2026-11-01T08:00:00Z"));
        signedInAs(STRANGER, Role.FOODSAVER);
        assertThat(refusal(() -> accounts.clearBounce(MEMBER))).isEqualTo(ApiErrorCode.ACCESS_DENIED);
        signedInAs(MEMBER, Role.FOODSAVER);
        assertThat(accounts.clearBounce(MEMBER).getEmailBouncedAt()).isNull();
        users.findById(MEMBER).orElseThrow().setEmailBouncedAt(Instant.parse("2026-11-01T08:00:00Z"));
        signedInAs(AMBASSADOR, Role.AMBASSADOR);
        assertThat(accounts.clearBounce(MEMBER).getEmailBouncedAt()).isNull();
    }

    @Test
    void internalNotesAreForAdministratorsNeverForTheMember() {
        signedInAs(MEMBER, Role.FOODSAVER);
        assertThat(refusal(() -> accounts.listNotes(MEMBER))).isEqualTo(ApiErrorCode.ACCESS_DENIED);
        signedInAs(STRANGER, Role.FOODSAVER);
        assertThat(refusal(() -> accounts.listNotes(MEMBER))).isEqualTo(ApiErrorCode.ACCESS_DENIED);
        verifyNoInteractions(notes);
        signedInAs(AMBASSADOR, Role.AMBASSADOR);
        assertThat(accounts.listNotes(MEMBER)).isEmpty();
        signedInAs(ORGA, Role.ORGA);
        assertThat(accounts.listNotes(MEMBER)).isEmpty();
    }

    @Test
    void aDeletedAccountHasNoProfile() {
        User deleted = new User();
        deleted.setId(AMBASSADOR);
        AccountDeletionService.anonymise(deleted, ORGA, "Wunsch", Instant.parse("2026-11-02T09:00:00Z"));
        when(users.findById(AMBASSADOR)).thenReturn(Optional.of(deleted));
        UserService service = new UserService(users, actors, mock(HistoryService.class), Clock.fixed(Instant.parse("2026-11-02T09:00:00Z"), ZoneOffset.UTC),
                permissions, mock(ObjectProvider.class));
        assertThat(refusal(() -> service.requireActive(AMBASSADOR))).isEqualTo(ApiErrorCode.USER_NOT_FOUND);
        assertThat(service.requireActive(MEMBER).getId()).isEqualTo(MEMBER);
    }
}
