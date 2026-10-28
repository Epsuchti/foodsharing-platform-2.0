package network.foodsharing.user.service;

import java.util.UUID;
import network.foodsharing.upload.service.UploadAccessRule;
import network.foodsharing.user.repository.UserRepository;
import org.springframework.stereotype.Component;

/** Profile photos are public (A41): everybody may read a claimed {@code USER_PHOTO} upload. */
@Component
public class UserPhotoAccessRule implements UploadAccessRule {

    private final UserRepository users;

    public UserPhotoAccessRule(UserRepository users) {
        this.users = users;
    }

    @Override
    public String module() {
        return UserAccountService.PHOTO_MODULE;
    }

    @Override
    public boolean mayRead(UUID userId, UUID usageOwnerId) {
        return true;
    }

    /** The photo of a deleted (anonymised) or vanished account can go. */
    @Override
    public boolean exists(UUID usageOwnerId) {
        return users.findById(usageOwnerId).filter(user -> user.getDeletedAt() == null).isPresent();
    }
}
