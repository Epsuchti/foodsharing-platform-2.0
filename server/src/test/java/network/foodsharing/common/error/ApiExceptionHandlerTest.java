package network.foodsharing.common.error;

import static org.hamcrest.Matchers.hasLength;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.config.TraceIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class ApiExceptionHandlerTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new ApiExceptionHandler())
            .addFilters(new TraceIdFilter())
            .build();

    @Test
    void apiExceptionKeepsStatusAndCodeAndCarriesTheTraceId() throws Exception {
        MvcResult result = mvc.perform(get("/test/api-exception"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(TraceIdFilter.HEADER, hasLength(12)))
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.retryable").value(false))
                .andReturn();
        String header = result.getResponse().getHeader(TraceIdFilter.HEADER);
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString()).contains(header);
    }

    @Test
    void tooManyRequestsIsRetryableWithRetryAfter() throws Exception {
        mvc.perform(get("/test/rate-limited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_LOGIN_REQUESTS"))
                .andExpect(jsonPath("$.retryable").value(true))
                .andExpect(jsonPath("$.retryAfterSeconds").value(60))
                .andExpect(jsonPath("$.args.limit").value("3"));
    }

    @Test
    void beanValidationBecomesValidationFailedWithFieldErrors() throws Exception {
        mvc.perform(post("/test/validated").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("NotBlank"));
    }

    @Test
    void unreadableBodyIsMalformedRequest() throws Exception {
        mvc.perform(post("/test/validated").contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void accessDeniedIs403() throws Exception {
        mvc.perform(get("/test/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void authenticationExceptionIs401() throws Exception {
        mvc.perform(get("/test/unauthenticated"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void optimisticLockIsConcurrentModification() throws Exception {
        mvc.perform(get("/test/optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void notFoundVariantsAreResourceNotFound() throws Exception {
        mvc.perform(get("/test/no-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(get("/test/entity-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void unexpectedErrorsAre500RetryableWithoutDetails() throws Exception {
        mvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("UNEXPECTED_ERROR"))
                .andExpect(jsonPath("$.message").value("Unexpected error"))
                .andExpect(jsonPath("$.retryable").value(true));
    }

    @Test
    void notImplementedPlaceholder() throws Exception {
        mvc.perform(get("/test/not-implemented"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.code").value("NOT_IMPLEMENTED"));
    }

    @Test
    void wrongMethodIsMalformedRequest() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(HttpMethod.DELETE, "/test/boom"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    record Payload(@NotBlank @Size(max = 10) String name) {
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/test/api-exception")
        void apiException() {
            throw ApiException.notFound(ApiErrorCode.USER_NOT_FOUND);
        }

        @GetMapping("/test/rate-limited")
        void rateLimited() {
            throw ApiException.tooManyRequests(ApiErrorCode.TOO_MANY_LOGIN_REQUESTS, Duration.ofMinutes(1))
                    .withArg("limit", "3");
        }

        @PostMapping("/test/validated")
        void validated(@Valid @RequestBody Payload payload) {
        }

        @GetMapping("/test/denied")
        void denied() {
            throw new AccessDeniedException("no");
        }

        @GetMapping("/test/unauthenticated")
        void unauthenticated() {
            throw new InsufficientAuthenticationException("no");
        }

        @GetMapping("/test/optimistic-lock")
        void optimisticLock() {
            throw new ObjectOptimisticLockingFailureException(Object.class, "id");
        }

        @GetMapping("/test/no-resource")
        void noResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/x", "x");
        }

        @GetMapping("/test/entity-not-found")
        void entityNotFound() {
            throw new EntityNotFoundException("x");
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("secret internals");
        }

        @GetMapping("/test/not-implemented")
        void notImplemented() {
            throw ApiException.notImplemented("op");
        }
    }

    @SuppressWarnings("unused")
    private static final HttpStatus UNUSED = HttpStatus.OK;
}
