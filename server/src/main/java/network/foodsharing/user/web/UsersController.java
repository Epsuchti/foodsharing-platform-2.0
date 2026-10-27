package network.foodsharing.user.web;

import network.foodsharing.api.UsersApi;
import network.foodsharing.api.model.UserProfile;
import network.foodsharing.api.model.UserSettingsUpdate;
import network.foodsharing.history.HistoryContext;
import network.foodsharing.user.mapper.UserMapper;
import network.foodsharing.user.service.SettingsChange;
import network.foodsharing.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UsersController implements UsersApi {

    private final UserService userService;
    private final UserMapper mapper;

    public UsersController(UserService userService, UserMapper mapper) {
        this.userService = userService;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<UserProfile> getCurrentUser() {
        return ResponseEntity.ok(mapper.toProfile(userService.getCurrent()));
    }

    @Override
    public ResponseEntity<UserProfile> updateMySettings(UserSettingsUpdate update) {
        var user = HistoryContext.withReason(update.getReason(),
                () -> userService.updateSettings(new SettingsChange(update.getLanguage(), update.getTheme())));
        return ResponseEntity.ok(mapper.toProfile(user));
    }
}
