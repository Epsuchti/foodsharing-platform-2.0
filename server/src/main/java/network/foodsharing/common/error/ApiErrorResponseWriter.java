package network.foodsharing.common.error;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import network.foodsharing.api.model.ApiErrorCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Writes an ApiError JSON body outside of Spring MVC (security entry point / access denied handler). */
@Component
public class ApiErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ApiErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, int status, ApiErrorCode code, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiErrors.build(status, code, message));
    }
}
