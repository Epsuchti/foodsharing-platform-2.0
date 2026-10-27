package network.foodsharing.auth.mapper;

import java.util.Arrays;
import java.util.List;
import network.foodsharing.api.model.AuthIdentityView;
import network.foodsharing.api.model.AuthMethods;
import network.foodsharing.api.model.AuthProvider;
import network.foodsharing.api.model.PasskeyView;
import network.foodsharing.api.model.SessionInfo;
import network.foodsharing.api.model.SessionUser;
import network.foodsharing.api.model.SessionView;
import network.foodsharing.auth.domain.AuthIdentity;
import network.foodsharing.auth.domain.AuthSession;
import network.foodsharing.auth.domain.PasskeyCredential;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import org.springframework.stereotype.Component;

/** Session models. {@link #sessionInfo} is also used by the dev login and the login handlers. */
@Component
public class AuthMapper {

    public SessionInfo anonymous() {
        SessionInfo info = new SessionInfo();
        info.setAuthenticated(false);
        info.setPermissions(List.of());
        return info;
    }

    public SessionInfo sessionInfo(SessionPrincipal principal, User user, List<String> permissions) {
        SessionUser sessionUser = new SessionUser();
        sessionUser.setId(user.getId());
        sessionUser.setEmail(user.getEmail());
        sessionUser.setDisplayName(user.getDisplayName());
        sessionUser.setAvatarUrl(user.getAvatarUrl());
        sessionUser.setRole(user.getRole());
        sessionUser.setLanguage(user.getPreferredLanguage());
        sessionUser.setTheme(user.getTheme());
        sessionUser.setVerified(user.isVerified());
        SessionInfo info = new SessionInfo();
        info.setAuthenticated(true);
        info.setUser(sessionUser);
        info.setPermissions(List.copyOf(permissions));
        info.setSessionId(principal.sessionId());
        return info;
    }

    public AuthMethods toMethods(List<AuthIdentity> identities, List<PasskeyCredential> passkeys) {
        AuthMethods methods = new AuthMethods();
        methods.setIdentities(identities.stream().map(this::toView).toList());
        methods.setPasskeys(passkeys.stream().map(this::toView).toList());
        return methods;
    }

    public AuthIdentityView toView(AuthIdentity identity) {
        AuthIdentityView view = new AuthIdentityView();
        view.setId(identity.getId());
        view.setProvider(AuthProvider.fromValue(identity.getProvider().name()));
        view.setLabel(identity.getLabel() == null ? identity.getSubject() : identity.getLabel());
        view.setCreatedAt(TimeMapper.toOffset(identity.getCreatedAt()));
        view.setLastUsedAt(TimeMapper.toOffset(identity.getLastUsedAt()));
        return view;
    }

    public PasskeyView toView(PasskeyCredential passkey) {
        PasskeyView view = new PasskeyView();
        view.setId(passkey.getId());
        view.setLabel(passkey.getLabel());
        view.setCreatedAt(TimeMapper.toOffset(passkey.getCreatedAt()));
        view.setLastUsedAt(TimeMapper.toOffset(passkey.getLastUsedAt()));
        view.setTransports(passkey.getTransports() == null || passkey.getTransports().isBlank() ? List.of()
                : Arrays.stream(passkey.getTransports().split(",")).map(String::strip).toList());
        return view;
    }

    public SessionView toView(AuthSession session, SessionPrincipal current) {
        SessionView view = new SessionView();
        view.setId(session.getId());
        view.setCurrent(current != null && session.getId().equals(current.sessionId()));
        view.setCreatedAt(TimeMapper.toOffset(session.getCreatedAt()));
        view.setLastSeenAt(TimeMapper.toOffset(session.getLastSeenAt()));
        view.setUserAgent(session.getUserAgent());
        view.setIpHint(session.getIpHash() == null ? null : session.getIpHash().substring(0, 8));
        return view;
    }
}
