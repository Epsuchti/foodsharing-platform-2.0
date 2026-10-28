package network.foodsharing.oauthserver.service;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Map;
import network.foodsharing.admin.domain.ConfigurationKey;
import network.foodsharing.admin.service.ConfigurationService;
import network.foodsharing.config.AppProperties;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The RS256 signing key of the OAuth provider ({@code app.oauth.signing-key-pem}, PKCS#8 PEM; env
 * {@code OAUTH_SIGNING_KEY}). Without a configured key a 2048-bit key is generated per start (tokens then do not
 * survive a restart; fine for local/e2e/test, logged as a warning). {@code kid} = first 16 hex characters of the SHA-256
 * of the public modulus.
 */
@Component
public class OAuthKeys {

    private static final Logger log = LoggerFactory.getLogger(OAuthKeys.class);

    private final RsaJsonWebKey key;
    private final AppProperties properties;
    private final ConfigurationService configuration;

    public OAuthKeys(AppProperties properties, ConfigurationService configuration) {
        this.properties = properties;
        this.configuration = configuration;
        this.key = load(properties.getOauth().getSigningKeyPem(), properties.getOauth().isAllowGeneratedKey());
    }

    static RsaJsonWebKey load(String pem) {
        return load(pem, true);
    }

    /** Fails fast when no key is configured and generated keys are not allowed (prod). */
    static RsaJsonWebKey load(String pem, boolean allowGenerated) {
        if ((pem == null || pem.isBlank()) && !allowGenerated) {
            throw new IllegalStateException("app.oauth.signing-key-pem (OAUTH_SIGNING_KEY) must be configured");
        }
        try {
            RsaJsonWebKey jwk;
            if (pem == null || pem.isBlank()) {
                log.warn("app.oauth.signing-key-pem is empty: generating a temporary OAuth signing key");
                jwk = RsaJwkGenerator.generateJwk(2048);
            } else {
                String body = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
                KeyFactory factory = KeyFactory.getInstance("RSA");
                RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) factory.generatePrivate(
                        new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)));
                RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(
                        new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
                jwk = new RsaJsonWebKey(publicKey);
                jwk.setPrivateKey(privateKey);
            }
            jwk.setKeyId(OAuthCrypto.sha256Hex(jwk.getRsaPublicKey().getModulus().toString(16)).substring(0, 16));
            jwk.setAlgorithm("RS256");
            jwk.setUse("sig");
            return jwk;
        } catch (JoseException | java.security.GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Invalid app.oauth.signing-key-pem", e);
        }
    }

    public RsaJsonWebKey key() {
        return key;
    }

    /** The public JWK set ({@code /.well-known/jwks.json}). */
    public Map<String, Object> jwks() {
        return Map.of("keys", java.util.List.of(key.toParams(JsonWebKey.OutputControlLevel.PUBLIC_ONLY)));
    }

    /** {@code oauth.issuer} configuration, else {@code app.base-url}, without trailing slash. */
    public String issuer() {
        String configured = configuration.string(ConfigurationKey.OAUTH_ISSUER);
        String issuer = configured == null || configured.isBlank() ? properties.getBaseUrl() : configured;
        return issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
    }
}
