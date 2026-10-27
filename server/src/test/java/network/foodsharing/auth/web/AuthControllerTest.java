package network.foodsharing.auth.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Language;
import network.foodsharing.auth.domain.AuthIdentity;
import network.foodsharing.auth.domain.AuthSession;
import network.foodsharing.auth.domain.IdentityProvider;
import network.foodsharing.auth.domain.PasskeyCredential;
import network.foodsharing.auth.domain.SessionRevokeReason;
import network.foodsharing.auth.mapper.AuthMapper;
import network.foodsharing.auth.service.AuthMethodsService;
import network.foodsharing.auth.service.AuthSessionService;
import network.foodsharing.auth.service.CreatedSession;
import network.foodsharing.auth.service.MagicLinkLogin;
import network.foodsharing.auth.service.MagicLinkService;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.mapper.HistoryMapper;
import network.foodsharing.history.service.HistoryQueryService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.testsupport.FixedClockConfig;
import network.foodsharing.testsupport.SecurityTestConfig;
import network.foodsharing.testsupport.SeedUsers;
import network.foodsharing.testsupport.TestAuth;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Every documented response of the auth operations implemented in T4 (plus the T1 session error paths). */
@WebMvcTest(AuthController.class)
@Import({SecurityTestConfig.class, AuthMapper.class})
@ActiveProfiles("test")
class AuthControllerTest {

    private static final UUID ID = UUID.fromString("00000002-0005-4000-8000-000000000001");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AuthSessionService sessionService;

    @MockitoBean
    UserService userService;

    @MockitoBean
    HistoryQueryService historyQueryService;

    @MockitoBean
    HistoryMapper historyMapper;

    @MockitoBean
    MagicLinkService magicLinkService;

    @MockitoBean
    AuthMethodsService authMethodsService;

    @Nested
    class RequestMagicLink {

        @Test
        void acceptsAndAnswersTheSameForEveryone() throws Exception {
            mvc.perform(post("/api/auth/magic-link").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"a@example.org\",\"returnUrl\":\"/account\",\"language\":\"fr\"}"))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.message").value("MAGIC_LINK_SENT"));
            verify(magicLinkService).request(eq("a@example.org"), eq("/account"), eq(Language.FR), any());
        }

        @Test
        void validatesTheBody() throws Exception {
            mvc.perform(post("/api/auth/magic-link").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"not-an-email\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
            mvc.perform(post("/api/auth/magic-link").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        @Test
        void mapsServiceErrors() throws Exception {
            expectRequestError(ApiException.badRequest(ApiErrorCode.EMAIL_DOMAIN_BLOCKED), 400);
            expectRequestError(ApiException.badRequest(ApiErrorCode.INVALID_RETURN_URL), 400);
            expectRequestError(ApiException.forbidden(ApiErrorCode.IP_BLOCKED), 403);
            doThrow(ApiException.tooManyRequests(ApiErrorCode.TOO_MANY_LOGIN_REQUESTS, Duration.ofMinutes(15)))
                    .when(magicLinkService).request(any(), any(), any(), any());
            mvc.perform(post("/api/auth/magic-link").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"a@example.org\"}"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().string("Retry-After", "900"))
                    .andExpect(jsonPath("$.code").value("TOO_MANY_LOGIN_REQUESTS"))
                    .andExpect(jsonPath("$.retryable").value(true));
        }

        @Test
        void needsCsrf() throws Exception {
            mvc.perform(post("/api/auth/magic-link").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"a@example.org\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        private void expectRequestError(ApiException exception, int status) throws Exception {
            doThrow(exception).when(magicLinkService).request(any(), any(), any(), any());
            mvc.perform(post("/api/auth/magic-link").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"a@example.org\"}"))
                    .andExpect(status().is(status))
                    .andExpect(jsonPath("$.code").value(exception.getCode().getValue()));
        }
    }

    @Nested
    class VerifyMagicLink {

        @Test
        void returnsTheNewSession() throws Exception {
            User user = user();
            SessionPrincipal principal = new SessionPrincipal(user.getId(), ID, user.getRole(), user.getEmail());
            when(magicLinkService.verify(eq("tok"), any(), any()))
                    .thenReturn(new MagicLinkLogin(user, new CreatedSession(new AuthSession(), principal), "/x"));
            mvc.perform(post("/api/auth/magic-link/verify").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"tok\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.authenticated").value(true))
                    .andExpect(jsonPath("$.sessionId").value(ID.toString()))
                    .andExpect(jsonPath("$.user.email").value("lea@example.org"))
                    .andExpect(jsonPath("$.user.language").value("it"));
        }

        @Test
        void mapsTokenAndAccountErrors() throws Exception {
            expectVerifyError(ApiException.badRequest(ApiErrorCode.INVALID_LOGIN_TOKEN), 400);
            expectVerifyError(ApiException.badRequest(ApiErrorCode.LOGIN_TOKEN_EXPIRED_OR_USED), 400);
            expectVerifyError(ApiException.forbidden(ApiErrorCode.ACCOUNT_DISABLED), 403);
            expectVerifyError(ApiException.forbidden(ApiErrorCode.EMAIL_DOMAIN_BLOCKED), 403);
            expectVerifyError(ApiException.forbidden(ApiErrorCode.IP_BLOCKED), 403);
            mvc.perform(post("/api/auth/magic-link/verify").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        private void expectVerifyError(ApiException exception, int status) throws Exception {
            doThrow(exception).when(magicLinkService).verify(any(), any(), any());
            mvc.perform(post("/api/auth/magic-link/verify").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"tok\"}"))
                    .andExpect(status().is(status))
                    .andExpect(jsonPath("$.code").value(exception.getCode().getValue()));
        }
    }

    @Nested
    class LoginMethods {

        @Test
        void listsIdentitiesAndPasskeys() throws Exception {
            AuthIdentity identity = new AuthIdentity();
            identity.setId(ID);
            identity.setProvider(IdentityProvider.GOOGLE);
            identity.setSubject("sub");
            identity.setLabel("lea@gmail.com");
            identity.setCreatedAt(FixedClockConfig.SEED_NOW);
            when(authMethodsService.list()).thenReturn(
                    new AuthMethodsService.LoginMethods(List.of(identity), List.of(passkey())));

            mvc.perform(get("/api/auth/methods").with(TestAuth.as(SeedUsers.FOODSAVER)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.identities[0].provider").value("GOOGLE"))
                    .andExpect(jsonPath("$.identities[0].label").value("lea@gmail.com"))
                    .andExpect(jsonPath("$.passkeys[0].label").value("Laptop"))
                    .andExpect(jsonPath("$.passkeys[0].transports[1]").value("usb"));
        }

        @Test
        void needAuthentication() throws Exception {
            mvc.perform(get("/api/auth/methods")).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
            mvc.perform(delete("/api/auth/methods/" + ID).with(csrf())).andExpect(status().isUnauthorized());
            mvc.perform(patch("/api/auth/passkeys/" + ID).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"label\":\"x\"}")).andExpect(status().isUnauthorized());
            mvc.perform(delete("/api/auth/passkeys/" + ID).with(csrf())).andExpect(status().isUnauthorized());
        }

        @Test
        void unlinkResponses() throws Exception {
            mvc.perform(delete("/api/auth/methods/" + ID).with(csrf()).with(TestAuth.as(SeedUsers.FOODSAVER)))
                    .andExpect(status().isNoContent());
            verify(authMethodsService).unlinkIdentity(ID);

            doThrow(ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND)).when(authMethodsService)
                    .unlinkIdentity(ID);
            mvc.perform(delete("/api/auth/methods/" + ID).with(csrf()).with(TestAuth.as(SeedUsers.FOODSAVER)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

            doThrow(ApiException.conflict(ApiErrorCode.LAST_LOGIN_METHOD_CANNOT_BE_REMOVED)).when(authMethodsService)
                    .unlinkIdentity(ID);
            mvc.perform(delete("/api/auth/methods/" + ID).with(csrf()).with(TestAuth.as(SeedUsers.FOODSAVER)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("LAST_LOGIN_METHOD_CANNOT_BE_REMOVED"));
        }

        @Test
        void renamePasskeyResponses() throws Exception {
            when(authMethodsService.renamePasskey(ID, "Laptop")).thenReturn(passkey());
            mvc.perform(patch("/api/auth/passkeys/" + ID).with(csrf()).with(TestAuth.as(SeedUsers.FOODSAVER))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Laptop\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(ID.toString()))
                    .andExpect(jsonPath("$.label").value("Laptop"));

            mvc.perform(patch("/api/auth/passkeys/" + ID).with(csrf()).with(TestAuth.as(SeedUsers.FOODSAVER))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
            mvc.perform(patch("/api/auth/passkeys/" + ID).with(csrf()).with(TestAuth.as(SeedUsers.FOODSAVER))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"" + "x".repeat(101) + "\"}"))
                    .andExpect(status().isBadRequest());

            when(authMethodsService.renamePasskey(ID, "Other"))
                    .thenThrow(ApiException.notFound(ApiErrorCode.PASSKEY_NOT_FOUND));
            mvc.perform(patch("/api/auth/passkeys/" + ID).with(csrf()).with(TestAuth.as(SeedUsers.FOODSAVER))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Other\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PASSKEY_NOT_FOUND"));
        }

        @Test
        void deletePasskeyResponses() throws Exception {
            mvc.perform(delete("/api/auth/passkeys/" + ID).with(csrf()).with(TestAuth.as(SeedUsers.FOODSAVER)))
                    .andExpect(status().isNoContent());
            verify(authMethodsService).deletePasskey(ID);

            doThrow(ApiException.notFound(ApiErrorCode.PASSKEY_NOT_FOUND)).when(authMethodsService).deletePasskey(ID);
            mvc.perform(delete("/api/auth/passkeys/" + ID).with(csrf()).with(TestAuth.as(SeedUsers.FOODSAVER)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PASSKEY_NOT_FOUND"));

            doThrow(ApiException.conflict(ApiErrorCode.LAST_LOGIN_METHOD_CANNOT_BE_REMOVED)).when(authMethodsService)
                    .deletePasskey(ID);
            mvc.perform(delete("/api/auth/passkeys/" + ID).with(csrf()).with(TestAuth.as(SeedUsers.FOODSAVER)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("LAST_LOGIN_METHOD_CANNOT_BE_REMOVED"));
        }
    }

    @Nested
    class Sessions {

        @Test
        void sessionEndpointsNeedAuthentication() throws Exception {
            mvc.perform(get("/api/auth/sessions")).andExpect(status().isUnauthorized());
            mvc.perform(delete("/api/auth/sessions/" + ID).with(csrf())).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/auth/sessions/revoke-others").with(csrf())).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/auth/security-log")).andExpect(status().isUnauthorized());
        }

        @Test
        void unknownSessionIsNotFound() throws Exception {
            when(sessionService.revoke(ID, SessionRevokeReason.REVOKED_BY_USER))
                    .thenThrow(ApiException.notFound(ApiErrorCode.SESSION_NOT_FOUND));
            mvc.perform(delete("/api/auth/sessions/" + ID).with(csrf()).with(TestAuth.as(SeedUsers.FOODSAVER)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
        }

        @Test
        void securityLogValidatesPaging() throws Exception {
            mvc.perform(get("/api/auth/security-log?size=500").with(TestAuth.as(SeedUsers.FOODSAVER)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        @Test
        void logoutWithoutCsrfIsForbidden() throws Exception {
            mvc.perform(post("/api/auth/logout")).andExpect(status().is(HttpStatus.FORBIDDEN.value()));
            mvc.perform(post("/api/auth/logout").with(csrf())).andExpect(status().isNoContent());
            verify(sessionService).logout(any());
        }
    }

    private static PasskeyCredential passkey() {
        PasskeyCredential passkey = new PasskeyCredential();
        passkey.setId(ID);
        passkey.setLabel("Laptop");
        passkey.setTransports("internal,usb");
        passkey.setCreatedAt(FixedClockConfig.SEED_NOW);
        return passkey;
    }

    private static User user() {
        User user = new User();
        user.setId(SeedUsers.FOODSAVER.id());
        user.setEmail("lea@example.org");
        user.setDisplayName("Lea");
        user.setPreferredLanguage(Language.IT);
        user.setRole(SeedUsers.FOODSAVER.role());
        return user;
    }
}
