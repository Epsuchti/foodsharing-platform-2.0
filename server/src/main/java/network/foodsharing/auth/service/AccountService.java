package network.foodsharing.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Language;
import network.foodsharing.auth.domain.AuthAction;
import network.foodsharing.auth.domain.AuthIdentity;
import network.foodsharing.auth.domain.IpBlockContext;
import network.foodsharing.auth.domain.IdentityProvider;
import network.foodsharing.auth.domain.RegistrationOutcome;
import network.foodsharing.auth.repository.AuthIdentityRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.NewUser;
import network.foodsharing.user.service.UserService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accounts are keyed by verified email (plan §8.1): a known identity logs in, an unknown identity whose email
 * belongs to an account is linked to it (ACCOUNT_LINKED), otherwise a new account is created (ACCOUNT_CREATED) —
 * unless the email domain is blocked (EMAIL_DOMAIN_BLOCKED) or the IP is blocked (IP_BLOCKED); both record
 * REGISTRATION_BLOCKED and a registration attempt.
 */
@Service
public class AccountService {

    private final AuthIdentityRepository identities;
    private final UserService userService;
    private final EmailBlocklistService emailBlocklist;
    private final IpBlockService ipBlocks;
    private final RegistrationAttemptService registrationAttempts;
    private final HistoryService history;
    private final Clock clock;

    public AccountService(AuthIdentityRepository identities, UserService userService,
                          EmailBlocklistService emailBlocklist, IpBlockService ipBlocks,
                          RegistrationAttemptService registrationAttempts, HistoryService history, Clock clock) {
        this.identities = identities;
        this.userService = userService;
        this.emailBlocklist = emailBlocklist;
        this.ipBlocks = ipBlocks;
        this.registrationAttempts = registrationAttempts;
        this.history = history;
        this.clock = clock;
    }

    /**
     * Resolves (or creates) the account for a verified identity. Does not create a session.
     *
     * @throws ApiException 403 ACCOUNT_DISABLED, 403 EMAIL_DOMAIN_BLOCKED, 403 IP_BLOCKED
     */
    @Transactional
    public User findOrCreateByVerifiedEmail(VerifiedIdentity identity, String clientIp) {
        Instant now = clock.instant();
        String email = UserService.normalizeEmail(identity.email());
        String subject = identity.provider() == IdentityProvider.EMAIL
                ? UserService.normalizeEmail(identity.subject()) : identity.subject();

        Optional<AuthIdentity> known = identities.findByProviderAndSubject(identity.provider(), subject);
        if (known.isPresent()) {
            AuthIdentity existing = known.get();
            User user = requireActive(existing.getUser(), identity);
            existing.setLastUsedAt(now);
            if (identity.label() != null && !identity.label().equals(existing.getLabel())) {
                existing.setLabel(StringUtils.abbreviate(identity.label(), 200));
            }
            return user;
        }

        Optional<User> byEmail = userService.findByEmail(email);
        if (byEmail.isPresent()) {
            User user = requireActive(byEmail.get(), identity);
            AuthIdentity linked = link(user, identity, subject, now);
            history.record(history.event(AuthAction.ACCOUNT_LINKED, user.getId())
                    .actorUser(user.getId())
                    .metadata(Map.of("provider", identity.provider().name(), "identityId", linked.getId().toString()))
                    .build());
            return user;
        }

        if (emailBlocklist.isBlocked(email)) {
            registrationAttempts.recordBlocked(email, clientIp, identity.provider(), RegistrationOutcome.BLOCKED_EMAIL,
                    ApiErrorCode.EMAIL_DOMAIN_BLOCKED);
            throw ApiException.forbidden(ApiErrorCode.EMAIL_DOMAIN_BLOCKED);
        }
        if (ipBlocks.isBlocked(clientIp, IpBlockContext.REGISTRATION)) {
            registrationAttempts.recordBlocked(email, clientIp, identity.provider(), RegistrationOutcome.BLOCKED_IP,
                    ApiErrorCode.IP_BLOCKED);
            throw ApiException.forbidden(ApiErrorCode.IP_BLOCKED);
        }
        User created = userService.create(new NewUser(email, now, displayName(identity, email),
                StringUtils.abbreviate(identity.firstName(), 100), StringUtils.abbreviate(identity.lastName(), 100),
                identity.language() == null ? Language.DE : identity.language(),
                StringUtils.abbreviate(identity.avatarUrl(), 500)));
        link(created, identity, subject, now);
        registrationAttempts.recordCreated(email, clientIp, identity.provider());
        return created;
    }

    private AuthIdentity link(User user, VerifiedIdentity identity, String subject, Instant now) {
        AuthIdentity linked = new AuthIdentity();
        linked.setUser(user);
        linked.setProvider(identity.provider());
        linked.setSubject(subject);
        linked.setLabel(StringUtils.abbreviate(identity.label() == null ? user.getEmail() : identity.label(), 200));
        linked.setLastUsedAt(now);
        return identities.save(linked);
    }

    /** A disabled account cannot sign in; records LOGIN_FAILED in its own transaction (the caller rolls back). */
    private User requireActive(User user, VerifiedIdentity identity) {
        if (!user.isActive()) {
            history.recordInNewTransaction(history.event(AuthAction.LOGIN_FAILED, user.getId())
                    .metadata(Map.of("method", identity.provider().name(),
                            "reason", ApiErrorCode.ACCOUNT_DISABLED.getValue()))
                    .build());
            throw ApiException.forbidden(ApiErrorCode.ACCOUNT_DISABLED);
        }
        return user;
    }

    static String displayName(VerifiedIdentity identity, String email) {
        String name = StringUtils.firstNonBlank(identity.displayName(),
                StringUtils.joinWith(" ", StringUtils.defaultString(identity.firstName()),
                        StringUtils.defaultString(identity.lastName())).strip(),
                email.substring(0, Math.max(email.indexOf('@'), 0)), email);
        return StringUtils.abbreviate(name.strip(), 100);
    }
}
