package network.foodsharing.notification.newsletter.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import network.foodsharing.config.AppProperties;
import org.springframework.stereotype.Component;

/**
 * Stateless one-click unsubscribe tokens (N7): {@code base64url(userId ‖ HMAC-SHA256(secret, userId)[0..16])}, 43
 * characters without a dot, so the link {@code /newsletter/unsubscribe/<token>} is an SPA route (paths with dots are
 * treated as files). Tokens of the first format {@code base64url(userId) + "." + base64url(mac)} still verify. They
 * never expire; changing {@code app.newsletter.secret} invalidates all of them.
 */
@Component
public class NewsletterTokenService {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final AppProperties properties;

    public NewsletterTokenService(AppProperties properties) {
        this.properties = properties;
    }

    public String token(UUID userId) {
        byte[] id = bytes(userId);
        return ENCODER.encodeToString(ByteBuffer.allocate(32).put(id).put(mac(id)).array());
    }

    /** The user of a valid token. */
    public Optional<UUID> verify(String token) {
        if (token == null) {
            return Optional.empty();
        }
        int dot = token.indexOf('.');
        try {
            byte[] id;
            byte[] signature;
            if (dot < 0) {
                byte[] raw = DECODER.decode(token);
                if (raw.length != 32) {
                    return Optional.empty();
                }
                id = Arrays.copyOf(raw, 16);
                signature = Arrays.copyOfRange(raw, 16, 32);
            } else if (dot > 0) {
                // legacy format (links in newsletters sent before the dot-free tokens)
                id = DECODER.decode(token.substring(0, dot));
                signature = DECODER.decode(token.substring(dot + 1));
            } else {
                return Optional.empty();
            }
            if (id.length != 16 || !MessageDigest.isEqual(mac(id), signature)) {
                return Optional.empty();
            }
            ByteBuffer buffer = ByteBuffer.wrap(id);
            return Optional.of(new UUID(buffer.getLong(), buffer.getLong()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public String unsubscribeUrl(UUID userId) {
        return properties.getBaseUrl() + "/newsletter/unsubscribe/" + token(userId);
    }

    private static byte[] bytes(UUID id) {
        return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits())
                .array();
    }

    private byte[] mac(byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getNewsletter().getSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            return Arrays.copyOf(mac.doFinal(data), 16);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 unavailable", ex);
        }
    }
}
