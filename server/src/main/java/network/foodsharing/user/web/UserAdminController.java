package network.foodsharing.user.web;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.AdminUsersApi;
import network.foodsharing.api.model.ActiveUpdate;
import network.foodsharing.api.model.AdminUserProfileUpdate;
import network.foodsharing.api.model.HistoryEntryPage;
import network.foodsharing.api.model.UserProfile;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.history.HistoryFilter;
import network.foodsharing.history.SubjectType;
import network.foodsharing.history.mapper.HistoryMapper;
import network.foodsharing.history.service.HistoryQueryService;
import network.foodsharing.user.mapper.UserMapper;
import network.foodsharing.user.service.UserAdminService;
import network.foodsharing.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** User management for ambassadors, FS management and orga (plan §14.4 AD1, §10.2 profile change history). */
@RestController
public class UserAdminController implements AdminUsersApi {

    /** Actions shown in the profile change history unless the client asks for others. */
    static final List<String> PROFILE_HISTORY_ACTIONS = List.of("ACCOUNT_CREATED", "PROFILE_UPDATED",
            "PROFILE_UPDATED_BY_ADMIN", "PHONE_CHANGED", "HOME_REGION_CHANGED", "FIELD_CHANGED", "ROLE_CHANGED",
            "ACTIVATED", "DEACTIVATED", "VERIFIED", "UNVERIFIED", "IDENTITY_CONFIRMED",
            "IDENTITY_CONFIRMATION_REVERTED", "SETTINGS_CHANGED");

    private final UserService userService;
    private final UserAdminService adminService;
    private final UserMapper mapper;
    private final HistoryQueryService historyQueryService;
    private final HistoryMapper historyMapper;

    public UserAdminController(UserService userService, UserAdminService adminService, UserMapper mapper,
                               HistoryQueryService historyQueryService, HistoryMapper historyMapper) {
        this.userService = userService;
        this.adminService = adminService;
        this.mapper = mapper;
        this.historyQueryService = historyQueryService;
        this.historyMapper = historyMapper;
    }

    @Override
    public ResponseEntity<UserProfile> getUserProfileAsAdmin(UUID userId) {
        return ResponseEntity.ok(mapper.toProfile(adminService.getForAdmin(userId)));
    }

    @Override
    public ResponseEntity<UserProfile> updateUserProfileAsAdmin(UUID userId, AdminUserProfileUpdate update) {
        return ResponseEntity.ok(mapper.toProfile(
                userService.updateProfileAsAdmin(userId, mapper.toChange(update), update.getReason())));
    }

    @Override
    public ResponseEntity<UserProfile> setUserActive(UUID userId, ActiveUpdate update) {
        return ResponseEntity.ok(mapper.toProfile(
                userService.setActive(userId, Boolean.TRUE.equals(update.getActive()), update.getReason())));
    }

    @Override
    public ResponseEntity<HistoryEntryPage> getUserChangeHistory(UUID userId, List<String> actions,
                                                                 OffsetDateTime from, OffsetDateTime to,
                                                                 Integer page, Integer size) {
        adminService.requireReadableUser(userId);
        HistoryFilter filter = new HistoryFilter(SubjectType.USER, userId, null, null,
                actions == null || actions.isEmpty() ? PROFILE_HISTORY_ACTIONS : actions,
                TimeMapper.toInstant(from), TimeMapper.toInstant(to), page == null ? 0 : page,
                size == null ? 20 : size);
        return ResponseEntity.ok(historyMapper.toPage(historyQueryService.list(filter)));
    }
}
