package network.foodsharing.auth.service;

import network.foodsharing.common.metrics.OperationsMetrics;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Language;
import network.foodsharing.auth.domain.AuthAction;
import network.foodsharing.auth.domain.IdentityProvider;
import network.foodsharing.auth.domain.LoginMethod;
import network.foodsharing.auth.domain.LoginToken;
import network.foodsharing.auth.domain.RegistrationOutcome;
import network.foodsharing.auth.repository.LoginTokenRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.web.HttpExchange;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.mail.service.MailDraft;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Email magic links (plan §8.1): sign-up and login with one link, valid 30 minutes, single use, rate limited per
 * email and IP. Requests always look the same to the caller (202) so account existence is not leaked.
 */
@Service
public class MagicLinkService {

    public static final String MAIL_TEMPLATE = "magic-link";

    private final LoginTokenRepository tokens;
    private final UserService userService;
    private final AccountService accountService;
    private final LoginService loginService;
    private final LoginRateLimiter rateLimiter;
    private final ReturnUrlValidator returnUrlValidator;
    private final EmailBlocklistService emailBlocklist;
    private final IpBlockService ipBlocks;
    private final RegistrationAttemptService registrationAttempts;
    private final MailOutboxService outbox;
    private final IpHasher ipHasher;
    private final HistoryService history;
    private final AppProperties properties;
    private final Clock clock;

    public MagicLinkService(LoginTokenRepository tokens, UserService userService, AccountService accountService,
                            LoginService loginService, LoginRateLimiter rateLimiter,
                            ReturnUrlValidator returnUrlValidator, EmailBlocklistService emailBlocklist,
                            IpBlockService ipBlocks, RegistrationAttemptService registrationAttempts,
                            MailOutboxService outbox, IpHasher ipHasher, HistoryService history,
                            AppProperties properties, Clock clock) {
        this.tokens = tokens;
        this.userService = userService;
        this.accountService = accountService;
        this.loginService = loginService;
        this.rateLimiter = rateLimiter;
        this.returnUrlValidator = returnUrlValidator;
        this.emailBlocklist = emailBlocklist;
        this.ipBlocks = ipBlocks;
        this.registrationAttempts = registrationAttempts;
        this.outbox = outbox;
        this.ipHasher = ipHasher;
        this.history = history;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Stores a login token and queues the mail {@code <base-url><verify-path>?token=…[&returnUrl=…]}. Records
     * MAGIC_LINK_REQUESTED for existing accounts. Disabled accounts silently get no mail.
     *
     * @throws ApiException 400 INVALID_RETURN_URL, 400 EMAIL_DOMAIN_BLOCKED, 403 IP_BLOCKED,
     *                      429 TOO_MANY_LOGIN_REQUESTS, 404 RESOURCE_NOT_FOUND when magic links are disabled
     */
    @Transactional
    public void request(String rawEmail, String rawReturnUrl, Language language, String clientIp) {
        requireEnabled();
        String returnUrl = returnUrlValidator.require(rawReturnUrl);
        ipBlocks.requireNotBlocked(clientIp);
        String email = UserService.normalizeEmail(rawEmail);
        rateLimiter.check(email, clientIp);
        if (emailBlocklist.isBlocked(email)) {
            registrationAttempts.recordBlocked(email, clientIp, IdentityProvider.EMAIL,
                    RegistrationOutcome.BLOCKED_EMAIL, ApiErrorCode.EMAIL_DOMAIN_BLOCKED);
            throw ApiException.badRequest(ApiErrorCode.EMAIL_DOMAIN_BLOCKED);
        }

        Optional<User> user = userService.findByEmail(email);
        Instant now = clock.instant();
        String rawToken = TokenHasher.newToken();
        LoginToken token = new LoginToken();
        token.setEmail(email);
        token.setTokenHash(TokenHasher.sha256Hex(rawToken));
        token.setExpiresAt(now.plus(validity()));
        token.setReturnUrl(returnUrl);
        token.setLanguage(language);
        token.setIpHash(ipHasher.hash(clientIp));
        tokens.save(token);

        if (user.isPresent() && !user.get().isActive()) {
            return;
        }
        Language mailLanguage = user.map(User::getPreferredLanguage)
                .orElse(language == null ? Language.DE : language);
        Map<String, Object> variables = new HashMap<>();
        variables.put("link", link(rawToken, returnUrl));
        variables.put("validityMinutes", properties.getAuth().getLoginTokenValidityMinutes());
        variables.put("displayName", user.map(User::getDisplayName).orElse(null));
        outbox.enqueue(new MailDraft(email, MAIL_TEMPLATE, mailLanguage, variables));

        user.ifPresent(existing -> history.record(history.event(AuthAction.MAGIC_LINK_REQUESTED, existing.getId())
                .metadata(Map.of("tokenId", token.getId().toString(), "language", mailLanguage.getValue()))
                .build()));
    }

    /**
     * Uses a token: marks it used, signs in (creating the account on first use, linking the EMAIL identity) and
     * writes the session cookie; LOGIN {method: EMAIL} is recorded by the session service.
     *
     * @throws ApiException 400 INVALID_LOGIN_TOKEN, 400 LOGIN_TOKEN_EXPIRED_OR_USED (recorded as LOGIN_FAILED for
     *                      existing accounts), 403 ACCOUNT_DISABLED / EMAIL_DOMAIN_BLOCKED / IP_BLOCKED
     */
    @Transactional
    public MagicLinkLogin verify(String rawToken, HttpServletRequest request, HttpServletResponse response) {
        requireEnabled();
        LoginToken token = tokens.findByTokenHash(TokenHasher.sha256Hex(rawToken.strip()))
                .orElseThrow(() -> {
                    OperationsMetrics.loginFailed("invalid-token");
                    return ApiException.badRequest(ApiErrorCode.INVALID_LOGIN_TOKEN);
                });
        Instant now = clock.instant();
        if (!token.isUsableAt(now)) {
            OperationsMetrics.loginFailed("expired-token");
            userService.findByEmail(token.getEmail()).ifPresent(user -> history.recordInNewTransaction(
                    history.event(AuthAction.LOGIN_FAILED, user.getId())
                            .metadata(Map.of("method", LoginMethod.EMAIL.name(),
                                    "reason", ApiErrorCode.LOGIN_TOKEN_EXPIRED_OR_USED.getValue()))
                            .build()));
            throw ApiException.badRequest(ApiErrorCode.LOGIN_TOKEN_EXPIRED_OR_USED);
        }
        token.setUsedAt(now);
        User user = accountService.findOrCreateByVerifiedEmail(
                VerifiedIdentity.email(token.getEmail(), token.getLanguage()), HttpExchange.clientIp(request));
        CreatedSession session = loginService.login(user.getId(), LoginMethod.EMAIL, request, response);
        return new MagicLinkLogin(user, session, token.getReturnUrl());
    }

    String link(String rawToken, String returnUrl) {
        StringBuilder link = new StringBuilder(properties.getBaseUrl())
                .append(properties.getAuth().getMagicLink().getVerifyPath())
                .append("?token=").append(rawToken);
        if (returnUrl != null) {
            link.append("&returnUrl=").append(URLEncoder.encode(returnUrl, StandardCharsets.UTF_8));
        }
        return link.toString();
    }

    private Duration validity() {
        return Duration.ofMinutes(properties.getAuth().getLoginTokenValidityMinutes());
    }

    private void requireEnabled() {
        if (!properties.getAuth().getMagicLink().isEnabled()) {
            throw ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND);
        }
    }
}
