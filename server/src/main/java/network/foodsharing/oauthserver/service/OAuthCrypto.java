package network.foodsharing.oauthserver.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Hashing, random secrets and PKCE (RFC 7636, S256 only) for the OAuth provider (A44). */
public final class OAuthCrypto {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private OAuthCrypto() {
    }

    public static String sha256Hex(String value) {
        return HexFormat.of().formatHex(sha256(value));
    }

    /** URL-safe random token with {@code bytes} bytes of entropy. */
    public static String randomToken(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    /** Client secret of {@code length} characters from an unambiguous alphabet. */
    public static String randomSecret(int length) {
        StringBuilder out = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            out.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return out.toString();
    }

    /** BASE64URL(SHA256(verifier)) == challenge; verifier 43–128 characters of the unreserved set. */
    public static boolean verifyS256(String verifier, String challenge) {
        if (verifier == null || challenge == null || !verifier.matches("[A-Za-z0-9\\-._~]{43,128}")) {
            return false;
        }
        String computed = Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(verifier));
        return MessageDigest.isEqual(computed.getBytes(StandardCharsets.US_ASCII),
                challenge.getBytes(StandardCharsets.US_ASCII));
    }

    /** Constant-time comparison of two hex hashes. */
    public static boolean sameHash(String a, String b) {
        return a != null && b != null && MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII),
                b.getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
