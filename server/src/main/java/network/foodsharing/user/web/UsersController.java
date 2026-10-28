package network.foodsharing.user.web;

import network.foodsharing.api.model.AccountDeletion;
import network.foodsharing.api.model.ActivityFilters;
import network.foodsharing.api.model.EmailChangeRequest;
import network.foodsharing.api.model.EmailChangeStatus;
import network.foodsharing.api.model.JobRef;
import network.foodsharing.api.model.OAuthConsent;
import network.foodsharing.api.model.PhotoResult;
import network.foodsharing.api.model.PhotoUpdate;
import network.foodsharing.api.model.SleepModeUpdate;
import network.foodsharing.api.model.TokenBody;
import network.foodsharing.common.error.ApiException;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.UsersApi;
import network.foodsharing.api.model.UserProfile;
import network.foodsharing.api.model.UserProfileUpdate;
import network.foodsharing.api.model.UserSettingsUpdate;
import network.foodsharing.api.model.UserSummary;
import network.foodsharing.history.HistoryContext;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.mapper.UserMapper;
import network.foodsharing.user.service.SettingsChange;
import network.foodsharing.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UsersController implements UsersApi {

    private final UserService userService;
    private final UserMapper mapper;
    private final network.foodsharing.user.service.UserSearchService userSearch;

    public UsersController(UserService userService, UserMapper mapper,
                           network.foodsharing.user.service.UserSearchService userSearch) {
        this.userService = userService;
        this.mapper = mapper;
        this.userSearch = userSearch;
    }

    @Override
    public ResponseEntity<UserProfile> getCurrentUser() {
        return ResponseEntity.ok(mapper.toProfile(userService.getCurrent()));
    }

    @Override
    public ResponseEntity<UserProfile> updateMySettings(UserSettingsUpdate update) {
        var user = HistoryContext.withReason(update.getReason(),
                () -> userService.updateSettings(new SettingsChange(update.getLanguage(), update.getTheme(),
                        update.getPickupRemindersEnabled())));
        return ResponseEntity.ok(mapper.toProfile(user));
    }

    @Override
    public ResponseEntity<UserProfile> updateMyProfile(UserProfileUpdate update) {
        User user = HistoryContext.withReason(update.getReason(),
                () -> userService.updateMyProfile(mapper.toChange(update)));
        return ResponseEntity.ok(mapper.toProfile(user));
    }

    @Override
    public ResponseEntity<UserSummary> getUserSummary(UUID userId) {
        User user = userService.requireActive(userId);
        return ResponseEntity.ok(mapper.toSummary(user, userService.mayCurrentUserSeeContact(user)));
    }

    @Override
    public ResponseEntity<List<UserSummary>> searchUsers(String q, UUID regionId, Integer limit) {
        return ResponseEntity.ok(userSearch.search(q, regionId, limit == null ? 10 : limit).stream()
                .map(mapper::toSummary).toList());
    }

    // --- M6 contract stubs (docs/plan-m6.md B1) ---

    @Override
    public ResponseEntity<Void> abortEmailChange() {
        throw ApiException.notImplemented("abortEmailChange");
    }

    @Override
    public ResponseEntity<Void> clearEmailBounce(UUID userId) {
        throw ApiException.notImplemented("clearEmailBounce");
    }

    @Override
    public ResponseEntity<UserSummary> confirmEmailChange(TokenBody tokenBody) {
        throw ApiException.notImplemented("confirmEmailChange");
    }

    @Override
    public ResponseEntity<Void> deleteMyAccount(AccountDeletion accountDeletion) {
        throw ApiException.notImplemented("deleteMyAccount");
    }

    @Override
    public ResponseEntity<Void> deleteMyPhoto() {
        throw ApiException.notImplemented("deleteMyPhoto");
    }

    @Override
    public ResponseEntity<JobRef> exportMyData() {
        throw ApiException.notImplemented("exportMyData");
    }

    @Override
    public ResponseEntity<ActivityFilters> getActivityFilters() {
        throw ApiException.notImplemented("getActivityFilters");
    }

    @Override
    public ResponseEntity<org.springframework.core.io.Resource> getBusinessCard(String role, UUID regionId) {
        throw ApiException.notImplemented("getBusinessCard");
    }

    @Override
    public ResponseEntity<EmailChangeStatus> getEmailChange() {
        throw ApiException.notImplemented("getEmailChange");
    }

    @Override
    public ResponseEntity<List<OAuthConsent>> listMyConnections() {
        throw ApiException.notImplemented("listMyConnections");
    }

    @Override
    public ResponseEntity<Void> requestEmailChange(EmailChangeRequest emailChangeRequest) {
        throw ApiException.notImplemented("requestEmailChange");
    }

    @Override
    public ResponseEntity<Void> revokeMyConnection(String clientId) {
        throw ApiException.notImplemented("revokeMyConnection");
    }

    @Override
    public ResponseEntity<PhotoResult> setMyPhoto(PhotoUpdate photoUpdate) {
        throw ApiException.notImplemented("setMyPhoto");
    }

    @Override
    public ResponseEntity<ActivityFilters> updateActivityFilters(ActivityFilters activityFilters) {
        throw ApiException.notImplemented("updateActivityFilters");
    }

    @Override
    public ResponseEntity<UserSummary> updateSleepMode(SleepModeUpdate sleepModeUpdate) {
        User user = userService.updateSleepMode(
                network.foodsharing.user.domain.SleepStatus.valueOf(sleepModeUpdate.getStatus().name()),
                sleepModeUpdate.getFrom(), sleepModeUpdate.getUntil(), sleepModeUpdate.getMessage());
        return ResponseEntity.ok(mapper.toSummary(user, true));
    }
}
