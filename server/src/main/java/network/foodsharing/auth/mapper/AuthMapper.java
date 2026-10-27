package network.foodsharing.auth.mapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import network.foodsharing.api.model.AuthIdentityView;
import network.foodsharing.api.model.AuthMethods;
import network.foodsharing.api.model.AuthProvider;
import network.foodsharing.api.model.LegalDocumentKind;
import network.foodsharing.api.model.OnboardingState;
import network.foodsharing.api.model.PasskeyView;
import network.foodsharing.api.model.SessionInfo;
import network.foodsharing.api.model.SessionUser;
import network.foodsharing.api.model.SessionView;
import network.foodsharing.api.model.UnitMembership;
import network.foodsharing.api.model.UnitType;
import network.foodsharing.api.model.WorkingGroupFunction;
import network.foodsharing.auth.domain.AuthIdentity;
import network.foodsharing.auth.domain.AuthSession;
import network.foodsharing.auth.domain.PasskeyCredential;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.security.SessionInfoBuilder;
import network.foodsharing.security.SessionInfoEnricher;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Session models. {@link #sessionInfo} is also used by the dev login and the login handlers; it applies every
 * {@link SessionInfoEnricher} bean (docs/conventions.md A11).
 */
@Component
public class AuthMapper {

    private static final Logger log = LoggerFactory.getLogger(AuthMapper.class);

    private final ObjectProvider<SessionInfoEnricher> enrichers;

    public AuthMapper(ObjectProvider<SessionInfoEnricher> enrichers) {
        this.enrichers = enrichers;
    }

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
        sessionUser.setFoodsaverNumber(user.getFoodsaverNumber());
        sessionUser.setQuizRole(user.getQuizRole());
        sessionUser.setPhone(user.getMobilePhone());
        sessionUser.setHomeRegionId(user.getHomeRegionId());
        SessionInfoBuilder builder = new SessionInfoBuilder().homeRegionId(user.getHomeRegionId());
        enrichers.orderedStream().forEach(enricher -> {
            try {
                enricher.enrich(principal, builder);
            } catch (RuntimeException e) {
                // the session bootstrap must never fail because one module cannot add its data
                log.warn("SessionInfoEnricher {} failed", enricher.getClass().getSimpleName(), e);
            }
        });
        List<String> allPermissions = new ArrayList<>(permissions);
        builder.extraPermissions().stream().filter(p -> !allPermissions.contains(p)).forEach(allPermissions::add);
        if (builder.homeRegionId() != null) {
            sessionUser.setHomeRegionId(builder.homeRegionId());
        }
        SessionInfo info = new SessionInfo();
        info.setAuthenticated(true);
        info.setUser(sessionUser);
        info.setPermissions(List.copyOf(allPermissions));
        info.setSessionId(principal.sessionId());
        info.setUnits(builder.units().stream().map(AuthMapper::toUnit).toList());
        info.setPendingConsents(builder.pendingConsents().stream().map(LegalDocumentKind::fromValue).toList());
        if (builder.onboardingComplete() != null) {
            OnboardingState onboarding = new OnboardingState();
            onboarding.setComplete(builder.onboardingComplete());
            onboarding.setCurrentStep(builder.onboardingCurrentStep());
            info.setOnboarding(onboarding);
        }
        return info;
    }

    private static UnitMembership toUnit(SessionInfoBuilder.Unit unit) {
        UnitMembership membership = new UnitMembership();
        membership.setId(unit.id());
        membership.setName(unit.name());
        membership.setSlug(unit.slug());
        membership.setType(UnitType.fromValue(unit.type()));
        membership.setParentId(unit.parentId());
        membership.setMember(unit.member());
        membership.setAdmin(unit.admin());
        membership.setHomeRegion(unit.homeRegion());
        membership.setFunction(unit.function() == null ? null : WorkingGroupFunction.fromValue(unit.function()));
        return membership;
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
