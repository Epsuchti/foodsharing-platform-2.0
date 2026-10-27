package network.foodsharing.verification.service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import network.foodsharing.config.AppProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Wallet signing material, loaded lazily and only when {@code app.wallet.apple.enabled} /
 * {@code app.wallet.google.enabled} are set (never in local / e2e). Disabled wallets answer 503 WALLET_NOT_CONFIGURED.
 */
@Component
public class WalletConfiguration {

    private final AppProperties properties;
    private volatile AppleWalletPassBuilder apple;
    private volatile GoogleWalletLinkBuilder google;

    public WalletConfiguration(AppProperties properties) {
        this.properties = properties;
    }

    public boolean appleEnabled() {
        return properties.getWallet().getApple().isEnabled();
    }

    public boolean googleEnabled() {
        return properties.getWallet().getGoogle().isEnabled();
    }

    public synchronized AppleWalletPassBuilder apple() {
        if (apple == null) {
            AppProperties.Wallet.Apple config = properties.getWallet().getApple();
            try (InputStream p12 = Files.newInputStream(Path.of(config.getCertificateP12()))) {
                char[] password = config.getCertificatePassword() == null ? new char[0]
                        : config.getCertificatePassword().toCharArray();
                KeyStore store = KeyStore.getInstance("PKCS12");
                store.load(p12, password);
                String alias = Collections.list(store.aliases()).stream().filter(a -> {
                    try {
                        return store.isKeyEntry(a);
                    } catch (Exception e) {
                        return false;
                    }
                }).findFirst().orElseThrow();
                X509Certificate wwdr = null;
                if (config.getWwdrCertificate() != null) {
                    try (InputStream in = Files.newInputStream(Path.of(config.getWwdrCertificate()))) {
                        wwdr = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
                    }
                }
                apple = new AppleWalletPassBuilder((PrivateKey) store.getKey(alias, password),
                        (X509Certificate) store.getCertificate(alias), wwdr, config.getPassTypeId(),
                        config.getTeamId());
            } catch (Exception e) {
                throw new IllegalStateException("Apple Wallet signing material could not be loaded", e);
            }
        }
        return apple;
    }

    public synchronized GoogleWalletLinkBuilder google() {
        if (google == null) {
            AppProperties.Wallet.Google config = properties.getWallet().getGoogle();
            try {
                JsonNode account = new ObjectMapper().readTree(Files.readString(Path.of(
                        config.getServiceAccountJson())));
                String pem = account.get("private_key").asString().replaceAll("-----[A-Z ]+-----", "")
                        .replaceAll("\\s", "");
                PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(
                        Base64.getDecoder().decode(pem)));
                google = new GoogleWalletLinkBuilder(config.getIssuerId(), config.getClassId(),
                        account.get("client_email").asString(), key, properties.getBaseUrl());
            } catch (Exception e) {
                throw new IllegalStateException("Google Wallet service account could not be loaded", e);
            }
        }
        return google;
    }
}
