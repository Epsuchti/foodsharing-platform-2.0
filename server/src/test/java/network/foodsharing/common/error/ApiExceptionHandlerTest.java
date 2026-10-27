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

    @Test
    void constraintViolationsBecomeValidationFailedWithTheLastPathNodeAsField() throws Exception {
        mvc.perform(get("/test/constraint-violation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("NotBlank"))
                .andExpect(jsonPath("$.fieldErrors[0].message").isNotEmpty());
    }

    @Test
    void parameterValidationBecomesValidationFailedWithTheParameterName() throws Exception {
        mvc.perform(get("/test/param-validated").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("size"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("Max"));
    }

    @Test
    void typeMismatchAndMissingParametersAreValidationFailed() throws Exception {
        mvc.perform(get("/test/param-validated").param("size", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("size"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("TypeMismatch"));
        mvc.perform(get("/test/param-validated"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("size"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("NotNull"));
    }

    @Test
    void apiExceptionCarriesFieldErrorsAndArgs() throws Exception {
        mvc.perform(get("/test/api-field-errors"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("Blocked"))
                .andExpect(jsonPath("$.args.domain").value("example.org"))
                .andExpect(jsonPath("$.retryAfterSeconds").doesNotExist())
                .andExpect(header().doesNotExist("Retry-After"));
    }

    @Test
    void retryAfterIsRoundedUpToAtLeastOneSecond() throws Exception {
        mvc.perform(get("/test/rate-limited-short"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(1));
    }

    @Test
    void serviceUnavailableIsRetryable() throws Exception {
        mvc.perform(get("/test/unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.retryable").value(true))
                .andExpect(jsonPath("$.retryAfterSeconds").value(30));
    }

    @Test
    void clientErrorsAreNotRetryable() throws Exception {
        for (String path : new String[] {"/test/api-exception", "/test/denied", "/test/unauthenticated",
                "/test/optimistic-lock", "/test/no-resource", "/test/entity-not-found"}) {
            mvc.perform(get(path)).andExpect(jsonPath("$.retryable").value(false));
        }
    }

    @Test
    void responseStatusExceptionsAreMapped() throws Exception {
        mvc.perform(get("/test/response-status").param("status", "404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(get("/test/response-status").param("status", "409"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/test/response-status").param("status", "502"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("UNEXPECTED_ERROR"))
                .andExpect(jsonPath("$.retryable").value(true));
    }

    @Test
    void unsupportedMediaTypeIsMalformedRequest() throws Exception {
        mvc.perform(post("/test/validated").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void everyErrorBodyCarriesTheTraceIdOfTheResponseHeader() throws Exception {
        for (String path : new String[] {"/test/api-exception", "/test/rate-limited", "/test/denied",
                "/test/unauthenticated", "/test/optimistic-lock", "/test/no-resource", "/test/entity-not-found",
                "/test/boom", "/test/not-implemented", "/test/constraint-violation", "/test/param-validated"}) {
            MvcResult result = mvc.perform(get(path)).andReturn();
            String traceId = result.getResponse().getHeader(TraceIdFilter.HEADER);
            org.assertj.core.api.Assertions.assertThat(traceId).as(path).matches("[0-9a-f]{12}");
            org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString()).as(path)
                    .contains("\"traceId\":\"" + traceId + "\"");
        }
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

        @GetMapping("/test/constraint-violation")
        void constraintViolation() {
            try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
                var violations = factory.getValidator().validate(new Payload(""));
                throw new jakarta.validation.ConstraintViolationException(violations);
            }
        }

        @GetMapping("/test/param-validated")
        void paramValidated(@org.springframework.web.bind.annotation.RequestParam("size")
                            @jakarta.validation.constraints.Max(100) Integer size) {
        }

        @GetMapping("/test/api-field-errors")
        void apiFieldErrors() {
            throw new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED,
                    java.util.List.of(new ApiFieldError("email", "Blocked", "blocked domain")),
                    java.util.Map.of("domain", "example.org"));
        }

        @GetMapping("/test/rate-limited-short")
        void rateLimitedShort() {
            throw ApiException.tooManyRequests(ApiErrorCode.TOO_MANY_REQUESTS, Duration.ofMillis(200));
        }

        @GetMapping("/test/unavailable")
        void unavailable() {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.UNEXPECTED_ERROR)
                    .withRetryAfter(Duration.ofSeconds(30));
        }

        @GetMapping("/test/response-status")
        void responseStatus(@org.springframework.web.bind.annotation.RequestParam("status") int status) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.valueOf(status));
        }
    }

    @SuppressWarnings("unused")
    private static final HttpStatus UNUSED = HttpStatus.OK;
}
