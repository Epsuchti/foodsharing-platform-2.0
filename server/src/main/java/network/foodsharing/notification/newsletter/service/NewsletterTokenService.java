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
 * Stateless one-click unsubscribe tokens (N7): {@code base64url(userId) + "." + base64url(HMAC-SHA256(secret,
 * userId)[0..16])}. They never expire; changing {@code app.newsletter.secret} invalidates all of them.
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
        return ENCODER.encodeToString(id) + "." + ENCODER.encodeToString(mac(id));
    }

    /** The user of a valid token. */
    public Optional<UUID> verify(String token) {
        if (token == null) {
            return Optional.empty();
        }
        int dot = token.indexOf('.');
        if (dot <= 0) {
            return Optional.empty();
        }
        try {
            byte[] id = DECODER.decode(token.substring(0, dot));
            byte[] signature = DECODER.decode(token.substring(dot + 1));
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
