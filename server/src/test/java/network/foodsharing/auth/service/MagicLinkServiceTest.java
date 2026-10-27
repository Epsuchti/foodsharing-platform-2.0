package network.foodsharing.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Language;
import network.foodsharing.auth.domain.AuthAction;
import network.foodsharing.auth.domain.LoginMethod;
import network.foodsharing.auth.domain.LoginToken;
import network.foodsharing.auth.repository.LoginTokenRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.config.MutableClock;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.mail.service.MailDraft;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.testsupport.FixedClockConfig;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class MagicLinkServiceTest {

    private static final String IP = "198.51.100.20";

    private final LoginTokenRepository tokens = mock(LoginTokenRepository.class);
    private final UserService userService = mock(UserService.class);
    private final AccountService accountService = mock(AccountService.class);
    private final LoginService loginService = mock(LoginService.class);
    private final LoginRateLimiter rateLimiter = mock(LoginRateLimiter.class);
    private final EmailBlocklistService blocklist = mock(EmailBlocklistService.class);
    private final IpBlockService ipBlocks = mock(IpBlockService.class);
    private final RegistrationAttemptService attempts = mock(RegistrationAttemptService.class);
    private final MailOutboxService outbox = mock(MailOutboxService.class);
    private final HistoryService history = mock(HistoryService.class);
    private final MutableClock clock = new FixedClockConfig().clock();
    private final AppProperties properties = new AppProperties();
    private final List<LoginToken> stored = new ArrayList<>();
    private MagicLinkService service;

    @BeforeEach
    void setUp() {
        properties.setBaseUrl("https://foodsharing.test");
        when(history.event(any(), any())).thenAnswer(invocation ->
                HistoryEvent.builder(invocation.getArgument(0), invocation.getArgument(1)));
        when(tokens.save(any())).thenAnswer(invocation -> {
            LoginToken token = invocation.getArgument(0);
            token.setId(UUID.randomUUID());
            stored.add(token);
            return token;
        });
        when(tokens.findByTokenHash(any())).thenAnswer(invocation -> stored.stream()
                .filter(token -> token.getTokenHash().equals(invocation.getArgument(0))).findFirst());
        when(userService.findByEmail(any())).thenReturn(Optional.empty());
        service = new MagicLinkService(tokens, userService, accountService, loginService, rateLimiter,
                new ReturnUrlValidator(), blocklist, ipBlocks, attempts, outbox, new IpHasher(properties), history,
                properties, clock);
    }

    @Test
    void requestStoresOnlyTheHashAndMailsTheLink() {
        service.request(" New@Example.org ", "/account", Language.GWS, IP);

        LoginToken token = stored.getFirst();
        assertThat(token.getEmail()).isEqualTo("new@example.org");
        assertThat(token.getExpiresAt()).isEqualTo(FixedClockConfig.SEED_NOW.plus(Duration.ofMinutes(30)));
        assertThat(token.getReturnUrl()).isEqualTo("/account");
        assertThat(token.getLanguage()).isEqualTo(Language.GWS);
        assertThat(token.getIpHash()).hasSize(64).doesNotContain(IP);

        MailDraft draft = sentDraft();
        assertThat(draft.to()).isEqualTo("new@example.org");
        assertThat(draft.template()).isEqualTo("magic-link");
        assertThat(draft.language()).isEqualTo(Language.GWS);
        String rawToken = rawToken(draft);
        assertThat((String) draft.variables().get("link"))
                .isEqualTo("https://foodsharing.test/login/verify?token=" + rawToken + "&returnUrl=%2Faccount");
        assertThat(token.getTokenHash()).isEqualTo(TokenHasher.sha256Hex(rawToken)).isNotEqualTo(rawToken);
        verify(rateLimiter).check("new@example.org", IP);
        verify(history, never()).record(any());
    }

    @Test
    void existingAccountsGetTheirLanguageAndTheRequestIsRecorded() {
        User user = user(true, Language.IT);
        when(userService.findByEmail("known@example.org")).thenReturn(Optional.of(user));

        service.request("known@example.org", null, Language.EN, IP);

        assertThat(sentDraft().language()).isEqualTo(Language.IT);
        assertThat(sentDraft().variables()).containsEntry("displayName", "Known");
        verify(history).record(argThat(event -> event.action() == AuthAction.MAGIC_LINK_REQUESTED
                && event.subjectId().equals(user.getId())));
    }

    @Test
    void disabledAccountsGetNoMailButTheSameAnswer() {
        when(userService.findByEmail("off@example.org")).thenReturn(Optional.of(user(false, Language.DE)));
        service.request("off@example.org", null, null, IP);
        verify(outbox, never()).enqueue(any());
    }

    @Test
    void requestGuards() {
        assertThatThrownBy(() -> service.request("a@example.org", "https://evil.example", null, IP))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ApiErrorCode.INVALID_RETURN_URL));

        doThrow(ApiException.forbidden(ApiErrorCode.IP_BLOCKED)).when(ipBlocks).requireNotBlocked("10.6.6.6");
        assertThatThrownBy(() -> service.request("a@example.org", null, null, "10.6.6.6"))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ApiErrorCode.IP_BLOCKED));

        doThrow(ApiException.tooManyRequests(ApiErrorCode.TOO_MANY_LOGIN_REQUESTS, Duration.ofMinutes(15)))
                .when(rateLimiter).check("busy@example.org", IP);
        assertThatThrownBy(() -> service.request("busy@example.org", null, null, IP))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ApiErrorCode.TOO_MANY_LOGIN_REQUESTS));

        when(blocklist.isBlocked("x@mailinator.com")).thenReturn(true);
        assertThatThrownBy(() -> service.request("x@mailinator.com", null, null, IP))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ApiErrorCode.EMAIL_DOMAIN_BLOCKED);
                    assertThat(ex.getStatus().value()).isEqualTo(400);
                });
        verify(attempts).recordBlocked(eq("x@mailinator.com"), eq(IP), any(), any(),
                eq(ApiErrorCode.EMAIL_DOMAIN_BLOCKED));

        assertThat(stored).isEmpty();
        verify(outbox, never()).enqueue(any());
    }

    @Test
    void verifySignsInOnceAndReturnsTheStoredReturnUrl() {
        service.request("new@example.org", "/stores", Language.FR, IP);
        String rawToken = rawToken(sentDraft());
        User user = user(true, Language.FR);
        when(accountService.findOrCreateByVerifiedEmail(any(), any())).thenReturn(user);

        MagicLinkLogin login = service.verify(rawToken, request(), new MockHttpServletResponse());

        assertThat(login.user()).isSameAs(user);
        assertThat(login.returnUrl()).isEqualTo("/stores");
        assertThat(stored.getFirst().getUsedAt()).isEqualTo(FixedClockConfig.SEED_NOW);
        verify(accountService).findOrCreateByVerifiedEmail(argThat(identity ->
                identity.email().equals("new@example.org") && identity.language() == Language.FR), eq(IP));
        verify(loginService).login(eq(user.getId()), eq(LoginMethod.EMAIL), any(), any());

        assertThatThrownBy(() -> service.verify(rawToken, request(), new MockHttpServletResponse()))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ApiErrorCode.LOGIN_TOKEN_EXPIRED_OR_USED));
    }

    @Test
    void expiredAndUnknownTokensAreRejected() {
        User user = user(true, Language.DE);
        when(userService.findByEmail("late@example.org")).thenReturn(Optional.of(user));
        service.request("late@example.org", null, null, IP);
        String rawToken = rawToken(sentDraft());

        clock.fixAt(FixedClockConfig.SEED_NOW.plus(Duration.ofMinutes(30)));
        assertThatThrownBy(() -> service.verify(rawToken, request(), new MockHttpServletResponse()))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ApiErrorCode.LOGIN_TOKEN_EXPIRED_OR_USED));
        verify(history).recordInNewTransaction(argThat(event -> event.action() == AuthAction.LOGIN_FAILED));
        verify(accountService, never()).findOrCreateByVerifiedEmail(any(), any());

        assertThatThrownBy(() -> service.verify("unknown", request(), new MockHttpServletResponse()))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ApiErrorCode.INVALID_LOGIN_TOKEN));
    }

    @Test
    void disabledMagicLinksAreNotFound() {
        properties.getAuth().getMagicLink().setEnabled(false);
        assertThatThrownBy(() -> service.request("a@example.org", null, null, IP))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ApiErrorCode.RESOURCE_NOT_FOUND));
    }

    private MailDraft sentDraft() {
        ArgumentCaptor<MailDraft> draft = ArgumentCaptor.forClass(MailDraft.class);
        verify(outbox, org.mockito.Mockito.atLeastOnce()).enqueue(draft.capture());
        return draft.getValue();
    }

    private static String rawToken(MailDraft draft) {
        String link = (String) draft.variables().get("link");
        String afterToken = link.substring(link.indexOf("token=") + "token=".length());
        int end = afterToken.indexOf('&');
        return end < 0 ? afterToken : afterToken.substring(0, end);
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(IP);
        return request;
    }

    private static User user(boolean active, Language language) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("known@example.org");
        user.setDisplayName("Known");
        user.setPreferredLanguage(language);
        user.setActive(active);
        return user;
    }
}
