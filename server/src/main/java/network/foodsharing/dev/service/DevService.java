package network.foodsharing.dev.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.auth.domain.LoginMethod;
import network.foodsharing.auth.service.AuthSessionService;
import network.foodsharing.auth.service.CreatedSession;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.config.MutableClock;
import network.foodsharing.notification.mail.CapturedMail;
import network.foodsharing.notification.mail.CapturingMailTransport;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The dev API's operations (local/e2e only): seed users for the switcher, login-as, role changes, server clock,
 * captured mails and the full reset used by the e2e tests before each test file.
 */
@Service
@Profile({"local", "e2e"})
public class DevService {

    private final DevUserCatalog catalog;
    private final UserService userService;
    private final AuthSessionService sessionService;
    private final SeedResetService seedResetService;
    private final ObjectProvider<CapturingMailTransport> capturingTransport;
    private final MutableClock clock;
    private final AppProperties properties;

    public DevService(DevUserCatalog catalog, UserService userService, AuthSessionService sessionService,
                      SeedResetService seedResetService, ObjectProvider<CapturingMailTransport> capturingTransport,
                      MutableClock clock, AppProperties properties) {
        this.catalog = catalog;
        this.userService = userService;
        this.sessionService = sessionService;
        this.seedResetService = seedResetService;
        this.capturingTransport = capturingTransport;
        this.clock = clock;
        this.properties = properties;
    }

    /** A user together with its catalog entry (empty for users that are not seed users). */
    public record DevUserView(User user, Optional<DevUserCatalog.Entry> entry) {
    }

    /** The result of {@link #loginAs}: the session cookie is already written. */
    public record DevLogin(User user, SessionPrincipal principal) {
    }

    /** Seed users that exist (and are active) in the database, in journey order. */
    @Transactional(readOnly = true)
    public List<DevUserView> listDevUsers() {
        return catalog.entries().stream()
                .flatMap(entry -> userService.findActive(entry.id())
                        .map(user -> new DevUserView(user, Optional.of(entry))).stream())
                .toList();
    }

    /**
     * Logs in as any active user (seed users and users created during a test) and records
     * {@code AuthAction.LOGIN {method: DEV}}.
     *
     * @throws ApiException 404 DEV_USER_NOT_FOUND
     */
    @Transactional
    public DevLogin loginAs(UUID userId, HttpServletRequest request, HttpServletResponse response) {
        User user = requireUser(userId);
        CreatedSession session = sessionService.create(user, LoginMethod.DEV, request, response);
        return new DevLogin(user, session.principal());
    }

    /**
     * Changes the global role (records {@code UserAction.ROLE_CHANGED}).
     *
     * @throws ApiException 404 DEV_USER_NOT_FOUND
     */
    @Transactional
    public DevUserView changeRole(UUID userId, Role role) {
        requireUser(userId);
        User user = userService.changeRole(userId, role);
        return new DevUserView(user, catalog.byId(userId));
    }

    /** Fixes the application clock at {@code fixedAt}, or returns to the system clock when {@code null}. */
    public Instant setClock(Instant fixedAt) {
        if (fixedAt == null) {
            clock.release();
        } else {
            clock.fixAt(fixedAt);
        }
        return clock.instant();
    }

    public boolean isClockFixed() {
        return clock.isFixed();
    }

    /** Captured mails, newest first (empty when mails go to SMTP, e.g. Mailpit in the local profile). */
    public List<CapturedMail> capturedMails(String to) {
        CapturingMailTransport transport = capturingTransport.getIfAvailable();
        return transport == null ? List.of() : transport.list(to);
    }

    public void clearCapturedMails() {
        capturingTransport.ifAvailable(CapturingMailTransport::clear);
    }

    /**
     * Back to the start state of a test: seed database, no captured mails, clock as configured
     * ({@code app.clock.fixed}, the system clock when unset).
     *
     * @throws ApiException 409 when the database is not H2
     */
    public void resetAll() {
        seedResetService.reset();
        clearCapturedMails();
        setClock(properties.getClock().getFixed());
    }

    private User requireUser(UUID userId) {
        return userService.findActive(userId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.DEV_USER_NOT_FOUND));
    }
}
