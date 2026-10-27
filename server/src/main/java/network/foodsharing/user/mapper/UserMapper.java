package network.foodsharing.user.mapper;

import network.foodsharing.api.model.UserProfile;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.user.domain.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserProfile toProfile(User user) {
        UserProfile profile = new UserProfile();
        profile.setId(user.getId());
        profile.setEmail(user.getEmail());
        profile.setEmailVerifiedAt(TimeMapper.toOffset(user.getEmailVerifiedAt()));
        profile.setDisplayName(user.getDisplayName());
        profile.setFirstName(user.getFirstName());
        profile.setLastName(user.getLastName());
        profile.setLanguage(user.getPreferredLanguage());
        profile.setTheme(user.getTheme());
        profile.setRole(user.getRole());
        profile.setVerified(user.isVerified());
        profile.setAvatarUrl(user.getAvatarUrl());
        profile.setRegisteredAt(TimeMapper.toOffset(user.getRegisteredAt()));
        profile.setLastLoginAt(TimeMapper.toOffset(user.getLastLoginAt()));
        return profile;
    }
}
