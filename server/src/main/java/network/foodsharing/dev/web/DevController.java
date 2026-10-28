package network.foodsharing.dev.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.DevApi;
import network.foodsharing.api.model.CapturedMail;
import network.foodsharing.api.model.DevClockRequest;
import network.foodsharing.api.model.DevClockState;
import network.foodsharing.api.model.DevLoginRequest;
import network.foodsharing.api.model.DevNotification;
import network.foodsharing.api.model.DevNotificationChannel;
import network.foodsharing.api.model.DevTelegramUpdate;
import network.foodsharing.api.model.DevRoleRequest;
import network.foodsharing.api.model.DevUser;
import network.foodsharing.api.model.SessionInfo;
import network.foodsharing.auth.mapper.AuthMapper;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.common.web.HttpExchange;
import network.foodsharing.dev.mapper.DevMapper;
import network.foodsharing.dev.service.DevNotificationService;
import network.foodsharing.dev.service.DevService;
import network.foodsharing.dev.service.DevService.DevLogin;
import network.foodsharing.notification.domain.NotificationChannelType;
import network.foodsharing.notification.service.CapturedMessages;
import network.foodsharing.security.PermissionService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
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
    private final DevNotificationService notificationService;

    public DevController(DevService devService, DevMapper mapper, AuthMapper authMapper,
                         PermissionService permissionService, DevNotificationService notificationService) {
        this.devService = devService;
        this.mapper = mapper;
        this.authMapper = authMapper;
        this.permissionService = permissionService;
        this.notificationService = notificationService;
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

    @Override
    public ResponseEntity<List<DevNotification>> listDevNotifications(UUID userId, String type,
            DevNotificationChannel channel) {
        List<DevNotification> result = new java.util.ArrayList<>();
        if (channel == null || channel == DevNotificationChannel.BELL || channel == DevNotificationChannel.MAIL) {
            notificationService.notifications(userId, type).stream()
                    .flatMap(bell -> mapper.toDevNotifications(bell).stream())
                    .filter(notification -> channel == null || channel == notification.getChannel())
                    .forEach(result::add);
        }
        NotificationChannelType capturedChannel = channel == null || channel == DevNotificationChannel.BELL
                || channel == DevNotificationChannel.MAIL ? null : NotificationChannelType.valueOf(channel.name());
        if (channel == null || capturedChannel != null) {
            notificationService.captured(userId, type, capturedChannel).stream().map(DevController::toApi)
                    .forEach(result::add);
        }
        result.sort(java.util.Comparator.comparing(DevNotification::getCreatedAt).reversed());
        return ResponseEntity.ok(result);
    }

    private static DevNotification toApi(CapturedMessages.Captured captured) {
        return new DevNotification().id(captured.id()).userId(captured.userId()).type(captured.type())
                .channel(DevNotificationChannel.valueOf(captured.channel().name()))
                .params(new java.util.LinkedHashMap<>(captured.params())).link(captured.link())
                .identifier(captured.identifier()).body(captured.title() + (captured.body() == null
                        || captured.body().isBlank() ? "" : "\n" + captured.body()))
                .target(captured.target()).createdAt(TimeMapper.toOffset(captured.createdAt()));
    }

    @Override
    public ResponseEntity<Void> clearDevNotifications() {
        notificationService.clearCaptured();
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> simulateIntegrationFailure(network.foodsharing.api.model.Integration integration) {
        notificationService.simulateIntegrationFailure(
                network.foodsharing.common.integration.Integration.valueOf(integration.name()));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> simulateTelegramUpdate(DevTelegramUpdate devTelegramUpdate) {
        notificationService.simulateTelegram(devTelegramUpdate.getChatId(), devTelegramUpdate.getText(),
                devTelegramUpdate.getTitle());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> runDevJob(String name) {
        notificationService.runJob(name);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<String> getDevIcalFixture() {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"))
                .body(notificationService.icalFixture());
    }
}
