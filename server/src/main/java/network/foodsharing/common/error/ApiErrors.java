package network.foodsharing.common.error;

import java.util.List;
import java.util.Map;
import network.foodsharing.api.model.ApiError;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.FieldError;
import org.slf4j.MDC;

/** Builds {@link ApiError} bodies; shared by the exception handler and the security entry points. */
public final class ApiErrors {

    public static final String TRACE_ID_MDC_KEY = "traceId";

    private ApiErrors() {
    }

    public static boolean isRetryable(int status) {
        return status >= 500 || status == 429 || status == 503;
    }

    public static ApiError build(int status, ApiErrorCode code, String message, List<ApiFieldError> fieldErrors,
                                 Map<String, String> args, Integer retryAfterSeconds) {
        ApiError error = new ApiError();
        error.setCode(code);
        error.setMessage(message == null ? code.getValue() : message);
        String traceId = MDC.get(TRACE_ID_MDC_KEY);
        error.setTraceId(traceId == null ? "none" : traceId);
        error.setRetryable(isRetryable(status));
        error.setRetryAfterSeconds(retryAfterSeconds);
        if (fieldErrors != null && !fieldErrors.isEmpty()) {
            error.setFieldErrors(fieldErrors.stream().map(ApiErrors::toModel).toList());
        }
        if (args != null && !args.isEmpty()) {
            error.setArgs(Map.copyOf(args));
        }
        return error;
    }

    public static ApiError build(int status, ApiErrorCode code, String message) {
        return build(status, code, message, List.of(), Map.of(), null);
    }

    private static FieldError toModel(ApiFieldError fieldError) {
        FieldError model = new FieldError();
        model.setField(fieldError.field());
        model.setCode(fieldError.code());
        model.setMessage(fieldError.message());
        return model;
    }
}
