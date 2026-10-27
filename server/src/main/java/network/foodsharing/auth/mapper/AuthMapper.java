package network.foodsharing.auth.mapper;

import java.util.List;
import network.foodsharing.api.model.SessionInfo;
import network.foodsharing.api.model.SessionUser;
import network.foodsharing.api.model.SessionView;
import network.foodsharing.auth.domain.AuthSession;
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
