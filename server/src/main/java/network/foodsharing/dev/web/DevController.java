package network.foodsharing.dev.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.DevApi;
import network.foodsharing.api.model.CapturedMail;
import network.foodsharing.api.model.DevClockRequest;
import network.foodsharing.api.model.DevClockState;
import network.foodsharing.api.model.DevLoginRequest;
import network.foodsharing.api.model.DevRoleRequest;
import network.foodsharing.api.model.DevUser;
import network.foodsharing.api.model.SessionInfo;
import network.foodsharing.auth.mapper.AuthMapper;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.common.web.HttpExchange;
import network.foodsharing.dev.mapper.DevMapper;
import network.foodsharing.dev.service.DevService;
import network.foodsharing.dev.service.DevService.DevLogin;
import network.foodsharing.security.PermissionService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Dev API ({@code /api/dev/**}, local/e2e only; open and CSRF-exempt via {@code DevSecurityConfig}). */
@RestController
@Profile({"local", "e2e"})
public class DevController implements DevApi {

    private final DevService devService;
    private final DevMapper mapper;
    private final AuthMapper authMapper;
    private final PermissionService permissionService;

    public DevController(DevService devService, DevMapper mapper, AuthMapper authMapper,
                         PermissionService permissionService) {
        this.devService = devService;
        this.mapper = mapper;
        this.authMapper = authMapper;
        this.permissionService = permissionService;
    }

    @Override
    public ResponseEntity<List<DevUser>> listDevUsers() {
        return ResponseEntity.ok(devService.listDevUsers().stream().map(mapper::toDevUser).toList());
    }

    @Override
    public ResponseEntity<SessionInfo> loginAsDevUser(DevLoginRequest devLoginRequest) {
        DevLogin login = devService.loginAs(devLoginRequest.getUserId(), HttpExchange.request(), HttpExchange.response());
        return ResponseEntity.ok(authMapper.sessionInfo(login.principal(), login.user(),
                permissionService.effectivePermissions(login.principal())));
    }

    @Override
    public ResponseEntity<Void> resetSeed() {
        devService.resetAll();
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<CapturedMail>> listCapturedMails(String to) {
        return ResponseEntity.ok(devService.capturedMails(to).stream().map(mapper::toCapturedMail).toList());
    }

    @Override
    public ResponseEntity<Void> clearCapturedMails() {
        devService.clearCapturedMails();
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<DevUser> setDevUserRole(UUID userId, DevRoleRequest devRoleRequest) {
        return ResponseEntity.ok(mapper.toDevUser(devService.changeRole(userId, devRoleRequest.getRole())));
    }

    @Override
    public ResponseEntity<DevClockState> setDevClock(DevClockRequest devClockRequest) {
        Instant now = devService.setClock(TimeMapper.toInstant(devClockRequest.getFixedAt()));
        return ResponseEntity.ok(mapper.toClockState(now, devService.isClockFixed()));
    }
}
