package network.foodsharing.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.auth.domain.AuthSession;
import network.foodsharing.auth.domain.LoginMethod;
import network.foodsharing.auth.domain.SessionRevokeReason;
import network.foodsharing.auth.repository.AuthSessionRepository;
import network.foodsharing.auth.service.AuthSessionService;
import network.foodsharing.auth.service.CreatedSession;
import network.foodsharing.auth.service.IpHasher;
import network.foodsharing.auth.service.TokenHasher;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.MutableClock;
import network.foodsharing.history.ActorType;
import network.foodsharing.history.domain.HistoryEntry;
import network.foodsharing.history.repository.HistoryEntryRepository;
import network.foodsharing.security.SessionAuthenticationToken;
import network.foodsharing.security.SessionCookieWriter;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.testsupport.FixedClockConfig;
import network.foodsharing.testsupport.JpaSliceTest;
import network.foodsharing.testsupport.TestAuth;
import network.foodsharing.testsupport.TestUsers;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@JpaSliceTest
@Import({AuthSessionService.class, UserService.class, SessionCookieWriter.class, IpHasher.class})
class AuthSessionServiceTest {

    private static final Instant NOW = FixedClockConfig.SEED_NOW;
    private static final Duration VALIDITY = Duration.ofDays(365);

    @Autowired
    private AuthSessionService service;

    @Autowired
    private AuthSessionRepository sessions;

    @Autowired
    private HistoryEntryRepository history;

    @Autowired
    private TestEntityManager em;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    private User user;

    @BeforeEach
    void setUp() {
        clock.fixAt(NOW);
        user = em.persistFlushFind(TestUsers.user("session@local"));
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
        clock.fixAt(NOW);
    }

    @Test
    void createStoresOnlyTheTokenHashAndWritesTheCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        CreatedSession created = service.create(user, LoginMethod.EMAIL, request(), response);
        em.flush();

        String rawToken = cookieValue(response);
        assertThat(rawToken).hasSizeGreaterThanOrEqualTo(43); // 256 bit, base64url
        AuthSession session = created.session();
        assertThat(session.getTokenHash()).isEqualTo(TokenHasher.sha256Hex(rawToken)).hasSize(64).isNotEqualTo(rawToken);
        assertThat(session.getLastSeenAt()).isEqualTo(NOW);
        assertThat(session.getExpiresAt()).isEqualTo(NOW.plus(VALIDITY));
        assertThat(session.getUserAgent()).isEqualTo("JUnit Browser");
        assertThat(session.getIpHash()).hasSize(64).doesNotContain("203.0.113.7");

        // the raw token and the clear IP appear in no column of the stored row
        Map<String, Object> row = jdbc.queryForMap("select * from auth_session where id = ?", session.getId());
        assertThat(row.values()).noneMatch(value -> value != null && String.valueOf(value).contains(rawToken));
        assertThat(row.values()).noneMatch(value -> value != null && String.valueOf(value).contains("203.0.113.7"));

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith("fs_session=").contains("HttpOnly", "SameSite=Lax", "Path=/",
                "Max-Age=" + VALIDITY.toSeconds()).doesNotContain("Secure");
    }

    @Test
    void createAuthenticatesTheRequestMarksTheLoginAndRecordsIt() {
        CreatedSession created = service.create(user, LoginMethod.GOOGLE, request(), new MockHttpServletResponse());
        em.flush();

        SessionPrincipal principal = created.principal();
        assertThat(principal).isEqualTo(new SessionPrincipal(user.getId(), created.session().getId(), Role.FOODSAVER,
                "session@local"));
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isInstanceOf(SessionAuthenticationToken.class)
                .extracting(auth -> auth.getPrincipal()).isEqualTo(principal);
        // bulk update (no version bump, concurrent logins must not conflict): read it back from the database
        assertThat(em.getEntityManager().createQuery("select u.lastLoginAt from User u where u.id = :id", Instant.class)
                .setParameter("id", user.getId()).getSingleResult()).isEqualTo(NOW);

        HistoryEntry login = single("LOGIN");
        assertThat(login.getSubjectId()).isEqualTo(user.getId());
        assertThat(login.getActorType()).isEqualTo(ActorType.USER);
        assertThat(login.getActorId()).isEqualTo(user.getId());
        assertThat(login.getMetadata()).contains("\"method\":\"GOOGLE\"")
                .contains("\"sessionId\":\"" + created.session().getId() + "\"");
    }

    @Test
    void disabledAccountsCannotLogIn() {
        user.setActive(false);

        assertThatThrownBy(() -> service.create(user, LoginMethod.GOOGLE, request(), new MockHttpServletResponse()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getCode()).isEqualTo(ApiErrorCode.ACCOUNT_DISABLED);
                });
        assertThat(sessions.count()).isZero();
    }

    @Test
    void authenticateResolvesTheRawTokenOnly() {
        String token = login();

        Optional<SessionPrincipal> principal = authenticate(token);

        assertThat(principal).get().extracting(SessionPrincipal::userId).isEqualTo(user.getId());
        assertThat(authenticate(TokenHasher.sha256Hex(token))).isEmpty();
        assertThat(authenticate("unknown")).isEmpty();
    }

    @Test
    void slidingExpiryIsTouchedAtMostOncePerInterval() {
        String token = login();
        UUID sessionId = onlySession().getId();

        clock.fixAt(NOW.plus(Duration.ofMinutes(4)));
        assertThat(authenticate(token)).isPresent();
        AuthSession untouched = reload(sessionId);
        assertThat(untouched.getLastSeenAt()).isEqualTo(NOW);
        assertThat(untouched.getExpiresAt()).isEqualTo(NOW.plus(VALIDITY));

        Instant later = NOW.plus(Duration.ofMinutes(5));
        clock.fixAt(later);
        assertThat(authenticate(token)).isPresent();
        AuthSession touched = reload(sessionId);
        assertThat(touched.getLastSeenAt()).isEqualTo(later);
        assertThat(touched.getExpiresAt()).isEqualTo(later.plus(VALIDITY));
        assertThat(touched.getVersion()).as("bulk update, no optimistic lock bump").isEqualTo(untouched.getVersion());
    }

    @Test
    void activeSessionsStayValidBeyondTheInitialExpiryButIdleOnesExpire() {
        String token = login();

        // used once a month: the expiry keeps sliding
        for (int month = 1; month <= 14; month++) {
            clock.fixAt(NOW.plus(Duration.ofDays(30L * month)));
            assertThat(authenticate(token)).as("month " + month).isPresent();
        }
        Instant lastUse = NOW.plus(Duration.ofDays(30L * 14));

        clock.fixAt(lastUse.plus(VALIDITY).minusSeconds(1));
        assertThat(authenticate(token)).isPresent();
        clock.fixAt(clock.instant().plus(VALIDITY));
        assertThat(authenticate(token)).isEmpty();
    }

    @Test
    void expiredRevokedOrDisabledSessionsDoNotAuthenticate() {
        String token = login();

        clock.fixAt(NOW.plus(VALIDITY));
        assertThat(authenticate(token)).as("expired").isEmpty();

        clock.fixAt(NOW);
        AuthSession session = onlySession();
        session.setRevokedAt(NOW);
        em.flush();
        assertThat(authenticate(token)).as("revoked").isEmpty();

        String second = login();
        em.find(User.class, user.getId()).setActive(false);
        em.flush();
        assertThat(authenticate(second)).as("disabled user").isEmpty();
    }

    @Test
    void listReturnsTheActiveSessionsOfTheUserForThemselfOrOrga() {
        String first = login();
        clock.fixAt(NOW.plus(Duration.ofHours(1)));
        login();
        clock.fixAt(NOW.plus(Duration.ofHours(2)));
        String revoked = login();
        UUID revokedId = sessions.findByTokenHashWithUser(TokenHasher.sha256Hex(revoked)).orElseThrow().getId();
        TestAuth.login(principal(revokedId));
        service.revoke(revokedId, SessionRevokeReason.REVOKED_BY_USER);
        em.flush();

        TestAuth.login(TestUsers.principal(user));
        List<AuthSession> list = service.list(user.getId());
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getLastSeenAt()).isAfter(list.get(1).getLastSeenAt());
        assertThat(list).extracting(AuthSession::getId).doesNotContain(revokedId);
        assertThat(first).isNotBlank();

        User other = em.persistFlushFind(TestUsers.user("other@local"));
        TestAuth.login(TestUsers.principal(other));
        assertThatThrownBy(() -> service.list(user.getId()))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo(ApiErrorCode.ACCESS_DENIED));

        User orga = em.persistFlushFind(TestUsers.user("orga@local", Role.ORGA));
        TestAuth.login(TestUsers.principal(orga));
        assertThat(service.list(user.getId())).hasSize(2);
    }

    @Test
    void revokeEndsOneOwnSessionAndRecordsIt() {
        login();
        UUID currentId = onlySession().getId();
        login();
        UUID otherId = sessions.findActiveByUserId(user.getId(), NOW).stream()
                .map(AuthSession::getId).filter(id -> !id.equals(currentId)).findFirst().orElseThrow();
        TestAuth.login(principal(currentId));
        clock.fixAt(NOW.plus(Duration.ofMinutes(1)));

        assertThat(service.revoke(otherId, SessionRevokeReason.REVOKED_BY_USER)).isFalse();
        em.flush();

        AuthSession other = reload(otherId);
        assertThat(other.getRevokedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
        assertThat(other.getRevokedReason()).isEqualTo(SessionRevokeReason.REVOKED_BY_USER);
        assertThat(reload(currentId).getRevokedAt()).isNull();
        assertThat(single("SESSION_REVOKED").getMetadata()).contains(otherId.toString()).contains("REVOKED_BY_USER");

        assertThat(service.revoke(currentId, SessionRevokeReason.REVOKED_BY_USER)).as("current session").isTrue();
    }

    @Test
    void revokingUnknownForeignOrInactiveSessionsIsNotFound() {
        login();
        UUID ownId = onlySession().getId();
        User other = em.persistFlushFind(TestUsers.user("foreign@local"));
        TestAuth.login(TestUsers.principal(other));

        assertSessionNotFound(() -> service.revoke(ownId, SessionRevokeReason.REVOKED_BY_USER));
        assertSessionNotFound(() -> service.revoke(UUID.randomUUID(), SessionRevokeReason.REVOKED_BY_USER));

        TestAuth.login(principal(ownId));
        service.revoke(ownId, SessionRevokeReason.REVOKED_BY_USER);
        assertSessionNotFound(() -> service.revoke(ownId, SessionRevokeReason.REVOKED_BY_USER));
        assertThat(history.findAll()).filteredOn(entry -> entry.getAction().equals("SESSION_REVOKED")).hasSize(1);
    }

    @Test
    void revokeOthersKeepsTheCurrentSessionAndRecordsTheCount() {
        login();
        UUID currentId = onlySession().getId();
        login();
        login();
        TestAuth.login(principal(currentId));

        assertThat(service.revokeOthers()).isEqualTo(2);
        em.flush();

        assertThat(sessions.findActiveByUserId(user.getId(), NOW)).extracting(AuthSession::getId).containsExactly(currentId);
        assertThat(sessions.findAll()).filteredOn(session -> !session.getId().equals(currentId))
                .allSatisfy(session -> assertThat(session.getRevokedReason()).isEqualTo(SessionRevokeReason.LOGOUT_EVERYWHERE));
        assertThat(single("SESSIONS_REVOKED_ALL").getMetadata()).isEqualTo("{\"count\":2}");
    }

    @Test
    void logoutRevokesTheCurrentSessionClearsTheCookieAndRecordsIt() {
        String token = login();
        UUID sessionId = onlySession().getId();
        TestAuth.login(principal(sessionId));
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.logout(response);
        em.flush();

        assertThat(reload(sessionId).getRevokedReason()).isEqualTo(SessionRevokeReason.LOGOUT);
        assertThat(authenticate(token)).isEmpty();
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).startsWith("fs_session=;").contains("Max-Age=0");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        HistoryEntry logout = single("LOGOUT");
        assertThat(logout.getActorId()).isEqualTo(user.getId());
        assertThat(logout.getMetadata()).contains(sessionId.toString());
    }

    @Test
    void anonymousLogoutOnlyClearsTheCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.logout(response);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
        assertThat(history.findAll()).isEmpty();
    }

    /** Creates a session, returns the raw cookie token and leaves the SecurityContext empty. */
    private String login() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        service.create(user, LoginMethod.EMAIL, request(), response);
        em.flush();
        TestAuth.clear();
        return cookieValue(response);
    }

    /** Like a request: a fresh persistence context per authentication (open-in-view is off). */
    private Optional<SessionPrincipal> authenticate(String token) {
        em.flush();
        em.clear();
        return service.authenticate(token);
    }

    private AuthSession onlySession() {
        List<AuthSession> all = sessions.findAll();
        assertThat(all).hasSize(1);
        return all.get(0);
    }

    private AuthSession reload(UUID id) {
        em.flush();
        em.clear();
        return sessions.findById(id).orElseThrow();
    }

    private SessionPrincipal principal(UUID sessionId) {
        return new SessionPrincipal(user.getId(), sessionId, user.getRole(), user.getEmail());
    }

    private HistoryEntry single(String action) {
        List<HistoryEntry> entries = history.findAll().stream().filter(entry -> entry.getAction().equals(action)).toList();
        assertThat(entries).as(action).hasSize(1);
        return entries.get(0);
    }

    private static void assertSessionNotFound(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(ex.getCode()).isEqualTo(ApiErrorCode.SESSION_NOT_FOUND);
        });
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/magic-link/verify");
        request.addHeader(HttpHeaders.USER_AGENT, "JUnit Browser");
        request.setRemoteAddr("203.0.113.7");
        return request;
    }

    private static String cookieValue(MockHttpServletResponse response) {
        String header = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(header).startsWith("fs_session=");
        return header.substring("fs_session=".length(), header.indexOf(';'));
    }
}
