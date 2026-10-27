package network.foodsharing.verification.service;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

/**
 * Google Wallet "save" link: a JWT (RS256, signed with the service account key) carrying a generic object of the
 * issuer's class; {@code https://pay.google.com/gp/v/save/<jwt>}.
 */
public class GoogleWalletLinkBuilder {

    public static final String SAVE_URL = "https://pay.google.com/gp/v/save/";

    public record ObjectData(String objectSuffix, String name, Long foodsaverNumber, String regionName,
                             String validUntil, String qrUrl) {
    }

    private final String issuerId;
    private final String classId;
    private final String serviceAccountEmail;
    private final PrivateKey key;
    private final String origin;
    private final ObjectMapper json = new ObjectMapper();

    public GoogleWalletLinkBuilder(String issuerId, String classId, String serviceAccountEmail, PrivateKey key,
                                   String origin) {
        this.issuerId = issuerId;
        this.classId = classId;
        this.serviceAccountEmail = serviceAccountEmail;
        this.key = key;
        this.origin = origin;
    }

    public String objectId(String objectSuffix) {
        return issuerId + "." + objectSuffix;
    }

    public String saveUrl(ObjectData data, Instant now) {
        return SAVE_URL + jwt(data, now);
    }

    String jwt(ObjectData data, Instant now) {
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("id", objectId(data.objectSuffix()));
        object.put("classId", issuerId + "." + classId);
        object.put("state", "ACTIVE");
        object.put("cardTitle", Map.of("defaultValue", Map.of("language", "de", "value", "Foodsaver-Ausweis")));
        object.put("header", Map.of("defaultValue", Map.of("language", "de", "value", data.name())));
        object.put("subheader", Map.of("defaultValue", Map.of("language", "de", "value",
                data.regionName() == null ? "foodsharing" : data.regionName())));
        object.put("textModulesData", List.of(
                Map.of("id", "number", "header", "Nr.", "body", data.foodsaverNumber() == null ? "-"
                        : data.foodsaverNumber().toString()),
                Map.of("id", "validUntil", "header", "Gültig bis", "body", data.validUntil())));
        object.put("barcode", Map.of("type", "QR_CODE", "value", data.qrUrl()));
        object.put("hexBackgroundColor", "#533a20");
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", serviceAccountEmail);
        claims.put("aud", "google");
        claims.put("typ", "savetowallet");
        claims.put("iat", now.getEpochSecond());
        claims.put("origins", origin == null ? List.of() : List.of(origin));
        claims.put("payload", Map.of("genericObjects", List.of(object)));
        try {
            String header = encode(json.writeValueAsBytes(Map.of("alg", "RS256", "typ", "JWT")));
            String payload = encode(json.writeValueAsBytes(claims));
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(key);
            signature.update((header + "." + payload).getBytes(StandardCharsets.US_ASCII));
            return header + "." + payload + "." + encode(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("Google Wallet link could not be signed", e);
        }
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
