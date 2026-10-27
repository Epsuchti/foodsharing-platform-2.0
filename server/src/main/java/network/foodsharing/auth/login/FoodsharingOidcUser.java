package network.foodsharing.auth.login;

import java.util.UUID;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/** The Google principal after {@link GoogleOidcUserService} resolved (or created) the platform account. */
public class FoodsharingOidcUser extends DefaultOidcUser {

    private final UUID userId;

    public FoodsharingOidcUser(UUID userId, OidcUser oidcUser) {
        super(oidcUser.getAuthorities(), oidcUser.getIdToken(), oidcUser.getUserInfo(), IdTokenClaimNames.SUB);
        this.userId = userId;
    }

    public UUID userId() {
        return userId;
    }
}
