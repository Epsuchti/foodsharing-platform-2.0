package network.foodsharing.dev.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import network.foodsharing.dev.service.FakeOidcProvider;
import network.foodsharing.dev.service.FakeOidcProvider.Account;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * The fake Google's authorize step (was FakeOidcFlowIT): redirects only go back to this host or localhost, cancel
 * answers access_denied, the chosen account gets a code and the state comes back.
 */
class FakeOidcAuthorizeTest {

    private static final String REDIRECT = "http://localhost:8081/login/oauth2/code/google";

    private final FakeOidcProvider provider = mock(FakeOidcProvider.class);
    private final FakeOidcController controller = new FakeOidcController(provider);
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @Test
    void thePageRefusesForeignRedirectUris() {
        ExtendedModelMap model = new ExtendedModelMap();
        when(provider.seedAccounts()).thenReturn(List.of());
        assertThat(controller.authorizePage("code", "dev-client", "https://evil.example/cb", null, null, null, null,
                request, model)).isEqualTo("dev/oidc-authorize");
        assertThat(model.getAttribute("error")).isEqualTo("redirect_uri must point to this host or localhost");

        controller.authorizePage(null, "dev-client", REDIRECT, "openid", "s", "n", null, request, model);
        assertThat(model.getAttribute("error")).isNull();
        controller.authorizePage("token", "dev-client", REDIRECT, null, null, null, null, request, model);
        assertThat(model.getAttribute("error")).isEqualTo("Only response_type=code is supported");
    }

    @Test
    void submittingToAForeignRedirectUriIsABadRequest() {
        MultiValueMap<String, String> form = form("javascript:alert(1)");
        form.add("choice", "cancel");
        assertThat(controller.authorize(form, request).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.authorize(form("ftp://localhost/cb"), request).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void cancelRedirectsWithAccessDeniedAndTheState() {
        MultiValueMap<String, String> form = form(REDIRECT);
        form.add("choice", "cancel");
        ResponseEntity<String> response = controller.authorize(form, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation().toString()).startsWith(REDIRECT)
                .contains("error=access_denied").contains("state=st-1");
        verify(provider, never()).issueCode(any(), any(), any(), any(), any());
    }

    @Test
    void aNewUserGetsACodeUnlessTheEmailIsInvalid() {
        Account account = new Account("new", "fake-google-new-1", "anna@example.ch", false, "Anna", "Anna", null, null,
                "fr");
        when(provider.newAccount("anna@example.ch", "Anna", "fr", false)).thenReturn(account);
        when(provider.issueCode(eq("dev-client"), eq(REDIRECT), eq("n-1"), any(), eq(account))).thenReturn("the-code");

        MultiValueMap<String, String> form = form(REDIRECT);
        form.add("choice", "new");
        form.add("email", "anna@example.ch");
        form.add("name", "Anna");
        form.add("locale", "fr");
        form.add("unverified", "on");
        ResponseEntity<String> response = controller.authorize(form, request);
        assertThat(response.getHeaders().getLocation().toString()).isEqualTo(REDIRECT + "?code=the-code&state=st-1");

        form.set("email", "no-at-sign");
        assertThat(controller.authorize(form, request).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void anUnknownSeedAccountIsABadRequest() {
        MultiValueMap<String, String> form = form(REDIRECT);
        form.add("choice", "account");
        form.add("account", "fake-google-nobody");
        when(provider.seedAccount("fake-google-nobody")).thenReturn(java.util.Optional.empty());
        assertThat(controller.authorize(form, request).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private static MultiValueMap<String, String> form(String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", "dev-client");
        form.add("redirect_uri", redirectUri);
        form.add("scope", "openid email profile");
        form.add("state", "st-1");
        form.add("nonce", "n-1");
        return form;
    }
}
