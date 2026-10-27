package network.foodsharing.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Access to the current servlet request/response for controllers whose generated interface signature has no
 * request parameter (cookies, client IP, user agent).
 */
public final class HttpExchange {

    private HttpExchange() {
    }

    public static HttpServletRequest request() {
        return attributes().getRequest();
    }

    public static HttpServletResponse response() {
        HttpServletResponse response = attributes().getResponse();
        if (response == null) {
            throw new IllegalStateException("No current HTTP response");
        }
        return response;
    }

    /** Client IP as seen after forward-headers processing ({@code server.forward-headers-strategy=framework}). */
    public static String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private static ServletRequestAttributes attributes() {
        if (RequestContextHolder.currentRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes;
        }
        throw new IllegalStateException("Not in a servlet request");
    }
}
