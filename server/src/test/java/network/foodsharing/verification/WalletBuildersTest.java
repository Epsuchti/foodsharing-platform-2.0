package network.foodsharing.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import network.foodsharing.verification.service.AppleWalletPassBuilder;
import network.foodsharing.verification.service.GoogleWalletLinkBuilder;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Apple pkpass (manifest hashes, detached PKCS#7 signature) and Google save link (JWT claims and signature). */
class WalletBuildersTest {

    private static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    private final ObjectMapper json = new ObjectMapper();

    private static KeyPair keys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static X509Certificate selfSigned(KeyPair keys) throws Exception {
        X500Name name = new X500Name("CN=Pass Type ID: pass.network.foodsharing.test");
        var builder = new JcaX509v3CertificateBuilder(name, BigInteger.ONE, Date.from(Instant.parse("2020-01-01T00:00:00Z")),
                Date.from(Instant.parse("2040-01-01T00:00:00Z")), name, keys.getPublic());
        X509CertificateHolder holder = builder.build(new JcaContentSignerBuilder("SHA256withRSA")
                .build(keys.getPrivate()));
        return new JcaX509CertificateConverter().getCertificate(holder);
    }

    @Test
    void applePassIsSignedOverTheManifest() throws Exception {
        KeyPair keys = keys();
        X509Certificate certificate = selfSigned(keys);
        AppleWalletPassBuilder builder = new AppleWalletPassBuilder(keys.getPrivate(), certificate, null,
                "pass.network.foodsharing.test", "TEAM123456");
        byte[] pkpass = builder.build(new AppleWalletPassBuilder.PassData("serial-1", "token-1", "Lukas Meier",
                100006L, "Zürich Stadt", "01.03.2028", "https://foodsharing.network/users/1", null));
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(pkpass))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                files.put(entry.getName(), zip.readAllBytes());
            }
        }
        assertThat(files).containsKeys("pass.json", "icon.png", "manifest.json", "signature");
        JsonNode pass = json.readTree(files.get("pass.json"));
        assertThat(pass.get("serialNumber").asString()).isEqualTo("serial-1");
        assertThat(pass.get("teamIdentifier").asString()).isEqualTo("TEAM123456");
        assertThat(pass.get("barcodes").get(0).get("message").asString())
                .isEqualTo("https://foodsharing.network/users/1");
        JsonNode manifest = json.readTree(files.get("manifest.json"));
        for (String name : new String[] {"pass.json", "icon.png", "icon@2x.png", "logo.png"}) {
            assertThat(manifest.get(name).asString()).isEqualTo(HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-1").digest(files.get(name))));
        }
        CMSSignedData signed = new CMSSignedData(new CMSProcessableByteArray(files.get("manifest.json")),
                files.get("signature"));
        SignerInformation signer = signed.getSignerInfos().getSigners().iterator().next();
        assertThat(signer.verify(new JcaSimpleSignerInfoVerifierBuilder().build(certificate))).isTrue();
    }

    @Test
    void googleLinkCarriesASignedJwt() throws Exception {
        KeyPair keys = keys();
        GoogleWalletLinkBuilder builder = new GoogleWalletLinkBuilder("3388000000012345", "foodsaver-passport",
                "wallet@project.iam.gserviceaccount.com", keys.getPrivate(), "https://foodsharing.network");
        String url = builder.saveUrl(new GoogleWalletLinkBuilder.ObjectData("serial-1", "Lukas Meier", 100006L,
                "Zürich Stadt", "01.03.2028", "https://foodsharing.network/users/1"), NOW);
        assertThat(url).startsWith(GoogleWalletLinkBuilder.SAVE_URL);
        String[] parts = url.substring(GoogleWalletLinkBuilder.SAVE_URL.length()).split("\\.");
        assertThat(parts).hasSize(3);
        JsonNode header = json.readTree(Base64.getUrlDecoder().decode(parts[0]));
        assertThat(header.get("alg").asString()).isEqualTo("RS256");
        JsonNode claims = json.readTree(Base64.getUrlDecoder().decode(parts[1]));
        assertThat(claims.get("iss").asString()).isEqualTo("wallet@project.iam.gserviceaccount.com");
        assertThat(claims.get("aud").asString()).isEqualTo("google");
        assertThat(claims.get("typ").asString()).isEqualTo("savetowallet");
        assertThat(claims.get("iat").asLong()).isEqualTo(NOW.getEpochSecond());
        JsonNode object = claims.get("payload").get("genericObjects").get(0);
        assertThat(object.get("id").asString()).isEqualTo("3388000000012345.serial-1");
        assertThat(object.get("classId").asString()).isEqualTo("3388000000012345.foodsaver-passport");
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(keys.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
    }
}
