package network.foodsharing.user.web;

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
}
