package network.foodsharing.common.error;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import network.foodsharing.api.model.ApiErrorCode;
import org.springframework.http.HttpStatus;

/**
 * The one exception services throw for expected failures. {@link ApiExceptionHandler} turns it into an
 * {@code ApiError}. Codes come from the generated {@link ApiErrorCode} (single source: openapi/schemas/common.yaml).
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ApiErrorCode code;
    private final List<ApiFieldError> fieldErrors;
    private final Map<String, String> args;
    private final Duration retryAfter;

    public ApiException(HttpStatus status, ApiErrorCode code) {
        this(status, code, code.getValue(), List.of(), Map.of(), null);
    }

    public ApiException(HttpStatus status, ApiErrorCode code, String message) {
        this(status, code, message, List.of(), Map.of(), null);
    }

    public ApiException(HttpStatus status, ApiErrorCode code, List<ApiFieldError> fieldErrors, Map<String, String> args) {
        this(status, code, code.getValue(), fieldErrors, args, null);
    }

    private ApiException(HttpStatus status, ApiErrorCode code, String message, List<ApiFieldError> fieldErrors,
                         Map<String, String> args, Duration retryAfter) {
        super(message == null ? code.getValue() : message);
        this.status = status;
        this.code = code;
        this.fieldErrors = List.copyOf(fieldErrors);
        this.args = Map.copyOf(args);
        this.retryAfter = retryAfter;
    }

    public static ApiException notFound(ApiErrorCode code) {
        return new ApiException(HttpStatus.NOT_FOUND, code);
    }

    public static ApiException forbidden(ApiErrorCode code) {
        return new ApiException(HttpStatus.FORBIDDEN, code);
    }

    public static ApiException badRequest(ApiErrorCode code) {
        return new ApiException(HttpStatus.BAD_REQUEST, code);
    }

    public static ApiException conflict(ApiErrorCode code) {
        return new ApiException(HttpStatus.CONFLICT, code);
    }

    public static ApiException unauthenticated() {
        return new ApiException(HttpStatus.UNAUTHORIZED, ApiErrorCode.AUTHENTICATION_REQUIRED);
    }

    public static ApiException tooManyRequests(ApiErrorCode code, Duration retryAfter) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, code).withRetryAfter(retryAfter);
    }

    /** Placeholder for contract operations whose implementation lands in a later task. */
    public static ApiException notImplemented(String operationId) {
        return new ApiException(HttpStatus.NOT_IMPLEMENTED, ApiErrorCode.NOT_IMPLEMENTED,
                operationId + " is not implemented yet");
    }

    public ApiException withRetryAfter(Duration duration) {
        return new ApiException(status, code, getMessage(), fieldErrors, args, duration);
    }

    public ApiException withArg(String name, String value) {
        Map<String, String> copy = new LinkedHashMap<>(args);
        copy.put(name, value);
        return new ApiException(status, code, getMessage(), fieldErrors, copy, retryAfter);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ApiErrorCode getCode() {
        return code;
    }

    public List<ApiFieldError> getFieldErrors() {
        return fieldErrors;
    }

    public Map<String, String> getArgs() {
        return args;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
