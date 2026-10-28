package network.foodsharing.user.web;

import network.foodsharing.api.model.AccountDeletion;
import network.foodsharing.api.model.BooleanValue;
import network.foodsharing.api.model.BouncedUserPage;
import network.foodsharing.api.model.EmailChangeRequest;
import network.foodsharing.api.model.UserNote;
import network.foodsharing.api.model.UserNoteCreate;
import network.foodsharing.common.error.ApiException;
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
    private final network.foodsharing.user.service.UserAccountService account;

    public UserAdminController(UserService userService, UserAdminService adminService, UserMapper mapper,
                               HistoryQueryService historyQueryService, HistoryMapper historyMapper,
                               network.foodsharing.user.service.UserAccountService account) {
        this.account = account;
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

    // --- M6 contract stubs (docs/plan-m6.md B1) ---

    @Override
    public ResponseEntity<UserNote> addUserNote(UUID userId, UserNoteCreate userNoteCreate) {
        var view = account.addNote(userId, userNoteCreate.getBody());
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(mapper.toNote(view.note(),
                view.mayDelete(), userService.findAll(java.util.List.of(view.note().getAuthorId())).stream()
                        .findFirst().orElse(null)));
    }

    @Override
    public ResponseEntity<Void> adminDeleteUser(UUID userId, AccountDeletion accountDeletion) {
        throw ApiException.notImplemented("adminDeleteUser");
    }

    @Override
    public ResponseEntity<Void> adminRequestEmailChange(UUID userId, EmailChangeRequest emailChangeRequest) {
        throw ApiException.notImplemented("adminRequestEmailChange");
    }

    @Override
    public ResponseEntity<Void> deleteUserNote(UUID userId, UUID noteId) {
        account.deleteNote(userId, noteId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<BouncedUserPage> listBouncedUsers(UUID regionId, Integer page, Integer size) {
        var result = account.listBounced(regionId, page == null ? 0 : page, size == null ? 20 : size);
        return ResponseEntity.ok(new BouncedUserPage()
                .items(result.getContent().stream().map(u -> new network.foodsharing.api.model.BouncedUser()
                        .user(mapper.toSummary(u)).bouncedAt(network.foodsharing.common.mapper.TimeMapper
                                .toOffset(u.getEmailBouncedAt()))).toList())
                .page(network.foodsharing.common.mapper.PageMapper.toPageMeta(result)));
    }

    @Override
    public ResponseEntity<List<UserNote>> listUserNotes(UUID userId) {
        var views = account.listNotes(userId);
        var authors = new java.util.HashMap<UUID, network.foodsharing.user.domain.User>();
        userService.findAll(views.stream().map(v -> v.note().getAuthorId()).filter(java.util.Objects::nonNull)
                .distinct().toList()).forEach(u -> authors.put(u.getId(), u));
        return ResponseEntity.ok(views.stream().map(v -> mapper.toNote(v.note(), v.mayDelete(),
                authors.get(v.note().getAuthorId()))).toList());
    }

    @Override
    public ResponseEntity<Void> setNoAutomaticDelete(UUID userId, BooleanValue booleanValue) {
        throw ApiException.notImplemented("setNoAutomaticDelete");
    }
}
