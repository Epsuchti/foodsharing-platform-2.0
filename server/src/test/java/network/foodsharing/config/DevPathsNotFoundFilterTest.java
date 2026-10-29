package network.foodsharing.config;

import static org.assertj.core.api.Assertions.assertThat;

import network.foodsharing.common.error.ApiErrorResponseWriter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/** D19.1: outside local/e2e the dev paths answer 404 before security; everything else passes through. */
class DevPathsNotFoundFilterTest {

    private final DevPathsNotFoundFilter filter =
            new DevPathsNotFoundFilter(new ApiErrorResponseWriter(JsonMapper.builder().build()));

    @ParameterizedTest
    @ValueSource(strings = {"/api/dev/seed/reset", "/api/dev", "/dev/oidc", "/dev/oidc/jwks", "/h2-console",
            "/h2-console/login.do"})
    void devPathsAreNotFound(String path) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run(path, chain);
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsString()).contains("RESOURCE_NOT_FOUND");
        assertThat(chain.getRequest()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/api/public/app-info", "/api/devices", "/developer", "/dev/gallery", "/dev/oidc-info",
            "/login/verify"})
    void otherPathsPass(String path) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run(path, chain);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    private MockHttpServletResponse run(String path, MockFilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }
}
