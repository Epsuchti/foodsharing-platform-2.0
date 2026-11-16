package network.foodsharing.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Language;
import network.foodsharing.api.model.Role;
import network.foodsharing.auth.domain.AuthIdentity;
import network.foodsharing.auth.domain.EmailChange;
import network.foodsharing.auth.domain.IdentityProvider;
import network.foodsharing.auth.domain.SessionRevokeReason;
import network.foodsharing.auth.repository.AuthIdentityRepository;
import network.foodsharing.auth.repository.EmailChangeRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.mail.service.MailDraft;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.ScopedPermissionResolver;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

/**
 * A5 email change end to end on the service (formerly EmailChangeFlowIT): the request validates, normalises and mails
 * a confirm link to the new and an abort link to the old address; only the requesting user confirms, once; the
 * confirmation changes the address and every EMAIL login identity and revokes the user's other sessions; an
 * administrator may start a change for someone they administer, others may not; the mailed abort link and the own
 * abort end a pending change.
 */
class EmailChangeFlowTest {

    private static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    private static final Pattern TOKEN = Pattern.compile("token=([^\\s\"&<]+)");

    private final EmailChangeRepository changes = mock(EmailChangeRepository.class);
    private final AuthIdentityRepository identities = mock(AuthIdentityRepository.class);
    private final EmailBlocklistService blocklist = mock(EmailBlocklistService.class);
    private final AuthSessionService sessions = mock(AuthSessionService.class);
    private final UserService users = mock(UserService.class);
    private final CurrentActorService actors = mock(CurrentActorService.class);
    private final MailOutboxService outbox = mock(MailOutboxService.class);
    private final ScopedPermissionResolver resolver = mock(ScopedPermissionResolver.class);
    private final List<EmailChange> stored = new ArrayList<>();
    private EmailChangeService service;
    private User jonas;
    private User julien;
    private SessionPrincipal jonasSession;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ObjectProvider<ScopedPermissionResolver> resolvers = mock(ObjectProvider.class);
        when(resolvers.getIfAvailable()).thenReturn(resolver);
        service = new EmailChangeService(changes, identities, blocklist, sessions, users, actors,
                new PermissionService(resolvers), outbox, mock(HistoryService.class, RETURNS_DEEP_STUBS),
                new AppProperties(), Clock.fixed(NOW, ZoneOffset.UTC));
        jonas = user("jumper@local");
        julien = user("guide@local");
        jonasSession = new SessionPrincipal(jonas.getId(), UUID.randomUUID(), Role.FOODSAVER, jonas.getEmail());
        signedIn(jonasSession);
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());
        when(identities.findByProviderAndSubject(any(), any())).thenReturn(Optional.empty());
        when(blocklist.isBlocked(anyString())).thenReturn(false);
        when(blocklist.isBlocked("jonas@mailinator.com")).thenReturn(true);
        when(changes.save(any())).thenAnswer(i -> {
            EmailChange change = i.getArgument(0);
            change.setCreatedAt(NOW);
            stored.add(change);
            return change;
        });
        when(changes.findByUserIdOrderByCreatedAtDescIdDesc(any())).thenAnswer(i -> stored.stream()
                .filter(c -> c.getUserId().equals(i.getArgument(0))).toList());
        when(changes.findByTokenHash(anyString())).thenAnswer(i -> stored.stream()
                .filter(c -> c.getTokenHash().equals(i.getArgument(0))).findFirst());
        when(changes.findByAbortTokenHash(anyString())).thenAnswer(i -> stored.stream()
                .filter(c -> c.getAbortTokenHash().equals(i.getArgument(0))).findFirst());
        when(users.changeEmail(any(), anyString(), any())).thenAnswer(i -> {
            User user = i.getArgument(0).equals(jonas.getId()) ? jonas : julien;
            user.setEmail(i.getArgument(1));
            return user;
        });
    }

    private User user(String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setDisplayName(email);
        user.setActive(true);
        user.setPreferredLanguage(Language.DE);
        when(users.requireActive(user.getId())).thenReturn(user);
        return user;
    }

    private void signedIn(SessionPrincipal principal) {
        when(actors.requirePrincipal()).thenReturn(principal);
        when(actors.requireUserId()).thenReturn(principal.userId());
    }

    private static ApiErrorCode code(Runnable call) {
        ApiException ex = catchThrowableOfType(ApiException.class, call::run);
        return ex == null ? null : ex.getCode();
    }

    /** The token of the last mail with this template to this address. */
    private String tokenMailedTo(String address, String template) {
        ArgumentCaptor<MailDraft> mails = ArgumentCaptor.forClass(MailDraft.class);
        verify(outbox, org.mockito.Mockito.atLeastOnce()).enqueue(mails.capture());
        MailDraft mail = mails.getAllValues().stream()
                .filter(m -> address.equals(m.to()) && template.equals(m.template()))
                .reduce((first, second) -> second).orElseThrow();
        Matcher matcher = TOKEN.matcher(String.valueOf(mail.variables().get("link")));
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private AuthIdentity identity(User user, IdentityProvider provider, String subject) {
        AuthIdentity identity = new AuthIdentity();
        identity.setUser(user);
        identity.setProvider(provider);
        identity.setSubject(subject);
        identity.setLabel(subject);
        return identity;
    }

    @Test
    void requestValidatesAndMailsBothAddresses() {
        assertThat(code(() -> service.request(jonas.getId(), "kein-mail"))).isEqualTo(ApiErrorCode.EMAIL_INVALID);
        assertThat(code(() -> service.request(jonas.getId(), "jonas@mailinator.com")))
                .isEqualTo(ApiErrorCode.EMAIL_BLOCKED);
        verify(changes, never()).save(any());

        service.request(jonas.getId(), "Jonas.Neu@Example.org");

        assertThat(service.pending()).hasValueSatisfying(p -> assertThat(p.newEmail()).isEqualTo("jonas.neu@example.org"));
        assertThat(tokenMailedTo("jonas.neu@example.org", EmailChangeService.CONFIRM_TEMPLATE)).isNotBlank();
        assertThat(tokenMailedTo("jumper@local", EmailChangeService.NOTICE_TEMPLATE)).isNotBlank();
    }

    @Test
    void onlyTheRequesterConfirmsOnceAndTheLoginIdentityFollowsWhileOtherSessionsEnd() {
        service.request(jonas.getId(), "jonas.neu@example.org");
        String token = tokenMailedTo("jonas.neu@example.org", EmailChangeService.CONFIRM_TEMPLATE);
        AuthIdentity email = identity(jonas, IdentityProvider.EMAIL, "jumper@local");
        AuthIdentity google = identity(jonas, IdentityProvider.GOOGLE, "google-subject");
        when(identities.findByUserId(jonas.getId())).thenReturn(List.of(email, google));

        signedIn(new SessionPrincipal(julien.getId(), UUID.randomUUID(), Role.FOODSAVER, julien.getEmail()));
        assertThat(code(() -> service.confirm(token))).isEqualTo(ApiErrorCode.EMAIL_CHANGE_TOKEN_INVALID);

        signedIn(jonasSession);
        service.confirm(token);

        assertThat(jonas.getEmail()).isEqualTo("jonas.neu@example.org");
        assertThat(email.getSubject()).isEqualTo("jonas.neu@example.org");
        assertThat(google.getSubject()).isEqualTo("google-subject");
        verify(sessions).revokeAllExcept(jonas.getId(), jonasSession.sessionId(), SessionRevokeReason.EMAIL_CHANGED);
        assertThat(code(() -> service.confirm(token))).isEqualTo(ApiErrorCode.EMAIL_CHANGE_TOKEN_INVALID);
        assertThat(service.pending()).isEmpty();
    }

    @Test
    void anAdministratorStartsAChangeOthersMayNotAndTheMailedLinkAbortsItOnce() {
        signedIn(jonasSession);
        assertThat(code(() -> service.request(julien.getId(), "julien@example.org")))
                .isEqualTo(ApiErrorCode.ACCESS_DENIED);

        signedIn(new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), Role.ORGA, "orga@local"));
        service.request(julien.getId(), "julien@example.org");
        String abort = tokenMailedTo("guide@local", EmailChangeService.NOTICE_TEMPLATE);
        service.abortByToken(abort);
        assertThat(code(() -> service.abortByToken(abort))).isEqualTo(ApiErrorCode.EMAIL_CHANGE_TOKEN_INVALID);
        signedIn(new SessionPrincipal(julien.getId(), UUID.randomUUID(), Role.FOODSAVER, julien.getEmail()));
        assertThat(service.pending()).isEmpty();
    }

    @Test
    void theOwnAbortEndsThePendingChangeAndItsConfirmLink() {
        SessionPrincipal session = new SessionPrincipal(julien.getId(), UUID.randomUUID(), Role.FOODSAVER,
                julien.getEmail());
        signedIn(session);
        service.request(julien.getId(), "julien2@example.org");
        service.abort();
        assertThat(service.pending()).isEmpty();
        String confirm = tokenMailedTo("julien2@example.org", EmailChangeService.CONFIRM_TEMPLATE);
        assertThat(code(() -> service.confirm(confirm))).isEqualTo(ApiErrorCode.EMAIL_CHANGE_TOKEN_INVALID);
    }
}
