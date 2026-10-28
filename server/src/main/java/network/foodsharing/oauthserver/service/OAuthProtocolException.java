package network.foodsharing.oauthserver.service;

/**
 * An RFC 6749 / 6750 protocol error of the framework endpoints ({@code /oauth/token}, {@code /oauth/userinfo},
 * {@code /oauth/authorize} before the redirect URI is trusted). Answered as {@code {"error": ..., "error_description":
 * ...}} with {@link #status()}.
 */
public class OAuthProtocolException extends RuntimeException {

    private final String error;
    private final int status;

    public OAuthProtocolException(int status, String error, String description) {
        super(description);
        this.status = status;
        this.error = error;
    }

    public static OAuthProtocolException invalidRequest(String description) {
        return new OAuthProtocolException(400, "invalid_request", description);
    }

    public static OAuthProtocolException invalidGrant(String description) {
        return new OAuthProtocolException(400, "invalid_grant", description);
    }

    public static OAuthProtocolException invalidClient(String description) {
        return new OAuthProtocolException(401, "invalid_client", description);
    }

    public static OAuthProtocolException invalidToken(String description) {
        return new OAuthProtocolException(401, "invalid_token", description);
    }

    public String error() {
        return error;
    }

    public int status() {
        return status;
    }
}
