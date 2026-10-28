package network.foodsharing.user.service;

import java.util.UUID;
import network.foodsharing.upload.service.UploadAccessRule;
import org.springframework.stereotype.Component;

/** Profile photos are public (A41): everybody may read a claimed {@code USER_PHOTO} upload. */
@Component
public class UserPhotoAccessRule implements UploadAccessRule {

    @Override
    public String module() {
        return UserAccountService.PHOTO_MODULE;
    }

    @Override
    public boolean mayRead(UUID userId, UUID usageOwnerId) {
        return true;
    }
}
