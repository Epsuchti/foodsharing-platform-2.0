package network.foodsharing.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Language;
import network.foodsharing.auth.domain.AuthAction;
import network.foodsharing.auth.domain.AuthIdentity;
import network.foodsharing.auth.domain.IdentityProvider;
import network.foodsharing.auth.domain.RegistrationOutcome;
import network.foodsharing.auth.repository.AuthIdentityRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.testsupport.FixedClockConfig;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.NewUser;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AccountServiceTest {

    private static final String IP = "203.0.113.5";

    private final AuthIdentityRepository identities = mock(AuthIdentityRepository.class);
    private final UserService userService = mock(UserService.class);
    private final EmailBlocklistService blocklist = mock(EmailBlocklistService.class);
    private final IpBlockService ipBlocks = mock(IpBlockService.class);
    private final RegistrationAttemptService attempts = mock(RegistrationAttemptService.class);
    private final HistoryService history = mock(HistoryService.class);
    private AccountService service;

    @BeforeEach
    void setUp() {
        when(history.event(any(), any())).thenAnswer(invocation ->
                HistoryEvent.builder(invocation.getArgument(0), invocation.getArgument(1)));
        when(identities.save(any())).thenAnswer(invocation -> {
            AuthIdentity identity = invocation.getArgument(0);
            identity.setId(UUID.randomUUID());
            return identity;
        });
        when(identities.findByProviderAndSubject(any(), any())).thenReturn(Optional.empty());
        when(userService.findByEmail(any())).thenReturn(Optional.empty());
        service = new AccountService(identities, userService, blocklist, ipBlocks, attempts, history,
                new FixedClockConfig().clock());
    }

    @Test
    void knownIdentityLogsInAndIsMarkedUsed() {
        User user = user(true);
        AuthIdentity identity = new AuthIdentity();
        identity.setUser(user);
        identity.setProvider(IdentityProvider.GOOGLE);
        identity.setSubject("sub-1");
        when(identities.findByProviderAndSubject(IdentityProvider.GOOGLE, "sub-1")).thenReturn(Optional.of(identity));

        assertThat(service.findOrCreateByVerifiedEmail(google("sub-1", "a@example.org"), IP)).isSameAs(user);
        assertThat(identity.getLastUsedAt()).isEqualTo(FixedClockConfig.SEED_NOW);
        assertThat(identity.getLabel()).isEqualTo("a@example.org");
        verify(userService, never()).create(any());
        verify(history, never()).record(any());
    }

    @Test
    void unknownIdentityWithKnownVerifiedEmailIsLinked() {
        User user = user(true);
        when(userService.findByEmail("a@example.org")).thenReturn(Optional.of(user));

        assertThat(service.findOrCreateByVerifiedEmail(google("sub-2", " A@Example.org "), IP)).isSameAs(user);

        ArgumentCaptor<AuthIdentity> saved = ArgumentCaptor.forClass(AuthIdentity.class);
        verify(identities).save(saved.capture());
        assertThat(saved.getValue().getProvider()).isEqualTo(IdentityProvider.GOOGLE);
        assertThat(saved.getValue().getSubject()).isEqualTo("sub-2");
        assertThat(saved.getValue().getUser()).isSameAs(user);
        verify(history).record(argThat(event -> event.action() == AuthAction.ACCOUNT_LINKED
                && event.subjectId().equals(user.getId()) && "GOOGLE".equals(event.metadata().get("provider"))));
        verify(userService, never()).create(any());
    }

    @Test
    void newEmailCreatesTheAccountWithPrefill() {
        User created = user(true);
        when(userService.create(any())).thenReturn(created);

        VerifiedIdentity identity = new VerifiedIdentity(IdentityProvider.GOOGLE, "sub-3", "New@Example.org",
                "New@Example.org", null, "Nora", "Neu", "https://img.test/a.png", Language.FR);
        assertThat(service.findOrCreateByVerifiedEmail(identity, IP)).isSameAs(created);

        ArgumentCaptor<NewUser> newUser = ArgumentCaptor.forClass(NewUser.class);
        verify(userService).create(newUser.capture());
        assertThat(newUser.getValue().email()).isEqualTo("new@example.org");
        assertThat(newUser.getValue().displayName()).isEqualTo("Nora Neu");
        assertThat(newUser.getValue().language()).isEqualTo(Language.FR);
        assertThat(newUser.getValue().avatarUrl()).isEqualTo("https://img.test/a.png");
        assertThat(newUser.getValue().emailVerifiedAt()).isEqualTo(FixedClockConfig.SEED_NOW);
        verify(identities).save(any());
        verify(attempts).recordCreated("new@example.org", IP, IdentityProvider.GOOGLE);
    }

    @Test
    void magicLinkSignUpUsesTheLocalPartAsDisplayNameAndGermanByDefault() {
        when(userService.create(any())).thenReturn(user(true));
        service.findOrCreateByVerifiedEmail(VerifiedIdentity.email("lena.muster@example.org", null), IP);

        ArgumentCaptor<NewUser> newUser = ArgumentCaptor.forClass(NewUser.class);
        verify(userService).create(newUser.capture());
        assertThat(newUser.getValue().displayName()).isEqualTo("lena.muster");
        assertThat(newUser.getValue().language()).isEqualTo(Language.DE);
    }

    @Test
    void blockedEmailDomainIsRecordedAndRejected() {
        when(blocklist.isBlocked("x@mailinator.com")).thenReturn(true);

        assertThatThrownBy(() -> service.findOrCreateByVerifiedEmail(google("sub-4", "x@mailinator.com"), IP))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ApiErrorCode.EMAIL_DOMAIN_BLOCKED));
        verify(attempts).recordBlocked("x@mailinator.com", IP, IdentityProvider.GOOGLE,
                RegistrationOutcome.BLOCKED_EMAIL, ApiErrorCode.EMAIL_DOMAIN_BLOCKED);
        verify(userService, never()).create(any());
    }

    @Test
    void blockedIpCannotRegister() {
        when(ipBlocks.isBlocked(IP, network.foodsharing.auth.domain.IpBlockContext.REGISTRATION)).thenReturn(true);

        assertThatThrownBy(() -> service.findOrCreateByVerifiedEmail(google("sub-5", "y@example.org"), IP))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ApiErrorCode.IP_BLOCKED));
        verify(attempts).recordBlocked(eq("y@example.org"), eq(IP), eq(IdentityProvider.GOOGLE),
                eq(RegistrationOutcome.BLOCKED_IP), eq(ApiErrorCode.IP_BLOCKED));
        verify(userService, never()).create(any());
    }

    @Test
    void disabledAccountCannotSignInAndTheAttemptIsRecorded() {
        User disabled = user(false);
        when(userService.findByEmail("off@example.org")).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> service.findOrCreateByVerifiedEmail(google("sub-6", "off@example.org"), IP))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ApiErrorCode.ACCOUNT_DISABLED);
                    assertThat(ex.getStatus().value()).isEqualTo(403);
                });
        verify(history).recordInNewTransaction(argThat(event -> event.action() == AuthAction.LOGIN_FAILED
                && event.subjectId().equals(disabled.getId())));
        verify(identities, never()).save(any());
    }

    private static VerifiedIdentity google(String subject, String email) {
        return new VerifiedIdentity(IdentityProvider.GOOGLE, subject, email, email, "Some Name", null, null, null,
                Language.EN);
    }

    private static User user(boolean active) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.org");
        user.setDisplayName("User");
        user.setActive(active);
        return user;
    }
}
