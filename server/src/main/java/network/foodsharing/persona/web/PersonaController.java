package network.foodsharing.persona.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import network.foodsharing.api.PersonasApi;
import network.foodsharing.api.model.Persona;
import network.foodsharing.api.model.PersonaLoginRequest;
import network.foodsharing.api.model.PersonaPanelRequest;
import network.foodsharing.api.model.SessionInfo;
import network.foodsharing.auth.mapper.AuthMapper;
import network.foodsharing.common.web.HttpExchange;
import network.foodsharing.config.PersonaPanelCondition;
import network.foodsharing.persona.mapper.PersonaMapper;
import network.foodsharing.persona.service.PersonaPanelService;
import network.foodsharing.persona.service.PersonaPanelService.PersonaLogin;
import network.foodsharing.security.PermissionService;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Persona panel API ({@code /api/personas/**}, staging with a panel password only; public like the magic link
 * endpoints, CSRF protected). The password comes in the JSON body, never in the URL.
 */
@RestController
@Profile("staging")
@Conditional(PersonaPanelCondition.class)
public class PersonaController implements PersonasApi {

    private final PersonaPanelService personas;
    private final PersonaMapper mapper;
    private final AuthMapper authMapper;
    private final PermissionService permissionService;

    public PersonaController(PersonaPanelService personas, PersonaMapper mapper, AuthMapper authMapper,
                             PermissionService permissionService) {
        this.personas = personas;
        this.mapper = mapper;
        this.authMapper = authMapper;
        this.permissionService = permissionService;
    }

    @Override
    public ResponseEntity<List<Persona>> listPersonas(PersonaPanelRequest body) {
        HttpServletRequest request = HttpExchange.request();
        return ResponseEntity.ok(personas.list(body.getPassword(), HttpExchange.clientIp(request)).stream()
                .map(mapper::toPersona).toList());
    }

    @Override
    public ResponseEntity<SessionInfo> loginAsPersona(PersonaLoginRequest body) {
        HttpServletRequest request = HttpExchange.request();
        PersonaLogin login = personas.login(body.getPassword(), body.getUserId(), HttpExchange.clientIp(request),
                request, HttpExchange.response());
        return ResponseEntity.ok(authMapper.sessionInfo(login.principal(), login.user(),
                permissionService.effectivePermissions(login.principal())));
    }
}
