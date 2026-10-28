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
    private final network.foodsharing.user.service.UserAccountService account;
    private final network.foodsharing.user.service.EmailChangePort emailChange;
    private final network.foodsharing.user.service.AccountDeletionService deletion;
    private final network.foodsharing.user.service.DataExportService export;
    private final network.foodsharing.user.service.BusinessCardPort businessCards;

    public UsersController(UserService userService, UserMapper mapper,
                           network.foodsharing.user.service.UserSearchService userSearch,
                           network.foodsharing.user.service.UserAccountService account,
                           network.foodsharing.user.service.EmailChangePort emailChange,
                           network.foodsharing.user.service.AccountDeletionService deletion,
                           network.foodsharing.user.service.DataExportService export,
                           network.foodsharing.user.service.BusinessCardPort businessCards) {
        this.deletion = deletion;
        this.export = export;
        this.businessCards = businessCards;
        this.account = account;
        this.emailChange = emailChange;
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
        if (update.getInfoMailMessage() != null || update.getMentionNotifications() != null) {
            user = HistoryContext.withReason(update.getReason(),
                    () -> account.updateMailSettings(update.getInfoMailMessage(), update.getMentionNotifications()));
        }
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
        emailChange.abort();
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> clearEmailBounce(UUID userId) {
        account.clearBounce(userId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<UserSummary> confirmEmailChange(TokenBody tokenBody) {
        User user = emailChange.confirm(tokenBody.getToken());
        return ResponseEntity.ok(mapper.toSummary(user, true));
    }

    @Override
    public ResponseEntity<Void> deleteMyAccount(AccountDeletion accountDeletion) {
        deletion.deleteSelf(accountDeletion == null ? null : accountDeletion.getReason());
        return ResponseEntity.noContent()
                .header(org.springframework.http.HttpHeaders.SET_COOKIE, "fs_session=; Path=/; Max-Age=0; HttpOnly; Secure; SameSite=Lax")
                .build();
    }

    @Override
    public ResponseEntity<Void> deleteMyPhoto() {
        account.deletePhoto();
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<JobRef> exportMyData() {
        JobRef ref = new JobRef();
        ref.setJobId(export.exportMine());
        return ResponseEntity.status(org.springframework.http.HttpStatus.ACCEPTED).body(ref);
    }

    @Override
    public ResponseEntity<ActivityFilters> getActivityFilters() {
        return ResponseEntity.ok(new ActivityFilters().excluded(userService.activityFilters().stream()
                .map(network.foodsharing.api.model.ActivityType::fromValue).toList()));
    }

    @Override
    public ResponseEntity<org.springframework.core.io.Resource> getBusinessCard(String role, UUID regionId) {
        byte[] pdf = businessCards.render(role, regionId);
        return ResponseEntity.ok().contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"foodsharing-visitenkarte.pdf\"")
                .body(new org.springframework.core.io.ByteArrayResource(pdf));
    }

    @Override
    public ResponseEntity<EmailChangeStatus> getEmailChange() {
        return ResponseEntity.ok(emailChange.pending()
                .map(p -> new EmailChangeStatus().pendingEmail(p.newEmail())
                        .requestedAt(network.foodsharing.common.mapper.TimeMapper.toOffset(p.requestedAt())))
                .orElseGet(EmailChangeStatus::new));
    }

    @Override
    public ResponseEntity<List<OAuthConsent>> listMyConnections() {
        throw ApiException.notImplemented("listMyConnections");
    }

    @Override
    public ResponseEntity<Void> requestEmailChange(EmailChangeRequest emailChangeRequest) {
        emailChange.request(userService.getCurrent().getId(), emailChangeRequest.getNewEmail());
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<Void> revokeMyConnection(String clientId) {
        throw ApiException.notImplemented("revokeMyConnection");
    }

    @Override
    public ResponseEntity<PhotoResult> setMyPhoto(PhotoUpdate photoUpdate) {
        User user = account.setPhoto(photoUpdate.getUploadId());
        return ResponseEntity.ok(new network.foodsharing.api.model.PhotoResult().photoUrl(user.getAvatarUrl()));
    }

    @Override
    public ResponseEntity<ActivityFilters> updateActivityFilters(ActivityFilters activityFilters) {
        List<String> saved = userService.updateActivityFilters(activityFilters.getExcluded() == null ? List.of()
                : activityFilters.getExcluded().stream().map(network.foodsharing.api.model.ActivityType::getValue)
                .toList());
        return ResponseEntity.ok(new ActivityFilters().excluded(saved.stream()
                .map(network.foodsharing.api.model.ActivityType::fromValue).toList()));
    }

    @Override
    public ResponseEntity<UserSummary> updateSleepMode(SleepModeUpdate sleepModeUpdate) {
        User user = userService.updateSleepMode(
                network.foodsharing.user.domain.SleepStatus.valueOf(sleepModeUpdate.getStatus().name()),
                sleepModeUpdate.getFrom(), sleepModeUpdate.getUntil(), sleepModeUpdate.getMessage());
        return ResponseEntity.ok(mapper.toSummary(user, true));
    }
}
