package network.foodsharing.common.error;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import network.foodsharing.api.model.ApiError;
import network.foodsharing.api.model.ApiErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps every exception to an {@code ApiError} (docs/conventions.md A2). No stack traces reach the client; unexpected
 * errors are logged with the trace id that is also in the response.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException exception) {
        Integer retryAfter = exception.getRetryAfter() == null ? null
                : (int) Math.max(1, exception.getRetryAfter().toSeconds());
        if (exception.getStatus().is5xxServerError() && exception.getStatus() != HttpStatus.NOT_IMPLEMENTED) {
            log.error("API error {}: {}", exception.getCode(), exception.getMessage(), exception);
        }
        ApiError body = ApiErrors.build(exception.getStatus().value(), exception.getCode(), exception.getMessage(),
                exception.getFieldErrors(), exception.getArgs(), retryAfter);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(exception.getStatus()).contentType(MediaType.APPLICATION_JSON);
        if (retryAfter != null) {
            builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        }
        return builder.body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        List<ApiFieldError> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiFieldError(error.getField(), error.getCode(), error.getDefaultMessage()))
                .toList();
        return validationFailed(fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException exception) {
        List<ApiFieldError> fieldErrors = exception.getConstraintViolations().stream()
                .map(violation -> new ApiFieldError(lastNode(violation.getPropertyPath().toString()),
                        violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
                        violation.getMessage()))
                .toList();
        return validationFailed(fieldErrors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException exception) {
        List<ApiFieldError> fieldErrors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ApiFieldError(parameterName(result),
                                error.getCodes() == null || error.getCodes().length == 0 ? "Invalid"
                                        : lastNode(error.getCodes()[error.getCodes().length - 1]),
                                error.getDefaultMessage())))
                .toList();
        return validationFailed(fieldErrors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return validationFailed(List.of(new ApiFieldError(exception.getName(), "TypeMismatch",
                "Invalid value for " + exception.getName())));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException exception) {
        return validationFailed(List.of(new ApiFieldError(exception.getParameterName(), "NotNull",
                "Missing parameter " + exception.getParameterName())));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleNotReadable(HttpMessageNotReadableException exception) {
        return respond(HttpStatus.BAD_REQUEST, ApiErrorCode.MALFORMED_REQUEST, "Malformed request body");
    }

    @ExceptionHandler({HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, ApiErrorCode.MALFORMED_REQUEST, exception.getMessage());
    }

    @ExceptionHandler({HttpMediaTypeNotSupportedException.class, HttpMediaTypeNotAcceptableException.class})
    public ResponseEntity<ApiError> handleMediaType(Exception exception) {
        HttpStatus status = exception instanceof HttpMediaTypeNotSupportedException
                ? HttpStatus.UNSUPPORTED_MEDIA_TYPE : HttpStatus.NOT_ACCEPTABLE;
        return respond(status, ApiErrorCode.MALFORMED_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException exception) {
        return respond(HttpStatus.FORBIDDEN, ApiErrorCode.ACCESS_DENIED, "Access denied");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException exception) {
        return respond(HttpStatus.UNAUTHORIZED, ApiErrorCode.AUTHENTICATION_REQUIRED, "Authentication required");
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException exception) {
        return respond(HttpStatus.CONFLICT, ApiErrorCode.CONCURRENT_MODIFICATION, "Changed by someone else");
    }

    @ExceptionHandler({NoResourceFoundException.class, EntityNotFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(Exception exception) {
        return respond(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND, "Not found");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException exception) {
        HttpStatusCode status = exception.getStatusCode();
        ApiErrorCode code = status.value() == 404 ? ApiErrorCode.RESOURCE_NOT_FOUND
                : status.is4xxClientError() ? ApiErrorCode.VALIDATION_FAILED : ApiErrorCode.UNEXPECTED_ERROR;
        return respond(HttpStatus.valueOf(status.value()), code, exception.getReason());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        log.error("Unexpected error", exception);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCode.UNEXPECTED_ERROR, "Unexpected error");
    }

    private ResponseEntity<ApiError> validationFailed(List<ApiFieldError> fieldErrors) {
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
                .body(ApiErrors.build(400, ApiErrorCode.VALIDATION_FAILED, "Validation failed", fieldErrors, Map.of(), null));
    }

    private ResponseEntity<ApiError> respond(HttpStatus status, ApiErrorCode code, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(ApiErrors.build(status.value(), code, message));
    }

    private static String parameterName(ParameterValidationResult result) {
        String name = result.getMethodParameter().getParameterName();
        return name == null ? "arg" + result.getMethodParameter().getParameterIndex() : name;
    }

    private static String lastNode(String path) {
        int index = path.lastIndexOf('.');
        return index < 0 ? path : path.substring(index + 1);
    }
}
