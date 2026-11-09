package network.foodsharing.persona.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.auth.domain.LoginMethod;
import network.foodsharing.auth.service.AuthSessionService;
import network.foodsharing.auth.service.CreatedSession;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.seed.SeedPersonas;
import network.foodsharing.config.PersonaPanelCondition;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The persona panel of the test environment (decision 173): lists the seed personas and logs in as one of them, both
 * behind {@link PersonaPanelGate}. Unlike the dev API it never logs in as an arbitrary user: only ids of
 * {@link SeedPersonas} whose account exists, is active and still has its {@code @local} seed address.
 */
@Service
@Profile("staging")
@Conditional(PersonaPanelCondition.class)
public class PersonaPanelService {

    private final PersonaPanelGate gate;
    private final UserService userService;
    private final AuthSessionService sessionService;

    public PersonaPanelService(PersonaPanelGate gate, UserService userService, AuthSessionService sessionService) {
        this.gate = gate;
        this.userService = userService;
        this.sessionService = sessionService;
    }

    /** A persona: its seed entry and the current account. */
    public record PersonaView(SeedPersonas.Entry entry, User user) {
    }

    /** The result of {@link #login}: the session cookie is already written. */
    public record PersonaLogin(User user, SessionPrincipal principal) {
    }

    /**
     * The personas that exist (active, seed address) in journey order.
     *
     * @throws ApiException 401 PERSONA_PANEL_PASSWORD_INVALID, 429 TOO_MANY_REQUESTS
     */
    @Transactional(readOnly = true)
    public List<PersonaView> list(String password, String clientIp) {
        gate.verify(password, clientIp);
        return SeedPersonas.entries().stream()
                .flatMap(entry -> findPersona(entry).stream())
                .toList();
    }

    /**
     * Logs in as a persona with {@link LoginMethod#DEV}; {@link AuthSessionService#create} records
     * {@code AuthAction.LOGIN {method: DEV}}.
     *
     * @throws ApiException 401 PERSONA_PANEL_PASSWORD_INVALID, 429 TOO_MANY_REQUESTS, 404 PERSONA_NOT_FOUND
     */
    @Transactional
    public PersonaLogin login(String password, UUID userId, String clientIp, HttpServletRequest request,
                              HttpServletResponse response) {
        gate.verify(password, clientIp);
        PersonaView persona = SeedPersonas.byId(userId)
                .flatMap(this::findPersona)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.PERSONA_NOT_FOUND));
        CreatedSession session = sessionService.create(persona.user(), LoginMethod.DEV, request, response);
        return new PersonaLogin(persona.user(), session.principal());
    }

    private java.util.Optional<PersonaView> findPersona(SeedPersonas.Entry entry) {
        return userService.findActive(entry.id())
                .filter(user -> user.getEmail() != null
                        && user.getEmail().toLowerCase(java.util.Locale.ROOT).endsWith("@" + SeedPersonas.EMAIL_DOMAIN))
                .map(user -> new PersonaView(entry, user));
    }
}
