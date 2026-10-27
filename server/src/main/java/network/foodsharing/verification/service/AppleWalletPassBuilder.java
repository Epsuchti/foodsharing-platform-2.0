package network.foodsharing.verification.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import tools.jackson.databind.ObjectMapper;

/**
 * Apple Wallet generic pass (.pkpass): {@code pass.json}, icons, {@code manifest.json} (SHA-1 of every file) and a
 * detached PKCS#7 {@code signature} of the manifest made with the pass type certificate (plus the WWDR intermediate).
 */
public class AppleWalletPassBuilder {

    public record PassData(String serial, String authToken, String name, Long foodsaverNumber, String regionName,
                           String validUntil, String qrUrl, String webServiceUrl) {
    }

    private final PrivateKey key;
    private final X509Certificate certificate;
    private final X509Certificate wwdr;
    private final String passTypeId;
    private final String teamId;
    private final ObjectMapper json = new ObjectMapper();

    public AppleWalletPassBuilder(PrivateKey key, X509Certificate certificate, X509Certificate wwdr,
                                  String passTypeId, String teamId) {
        this.key = key;
        this.certificate = certificate;
        this.wwdr = wwdr;
        this.passTypeId = passTypeId;
        this.teamId = teamId;
    }

    public byte[] build(PassData data) {
        try {
            Map<String, byte[]> files = new LinkedHashMap<>();
            files.put("pass.json", json.writeValueAsBytes(passJson(data)));
            byte[] icon = icon(29);
            files.put("icon.png", icon);
            files.put("icon@2x.png", icon(58));
            files.put("logo.png", icon);
            byte[] manifest = manifest(files);
            files.put("manifest.json", manifest);
            files.put("signature", sign(manifest));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                for (Map.Entry<String, byte[]> file : files.entrySet()) {
                    zip.putNextEntry(new ZipEntry(file.getKey()));
                    zip.write(file.getValue());
                    zip.closeEntry();
                }
            }
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Apple Wallet pass could not be built", e);
        }
    }

    Map<String, Object> passJson(PassData data) {
        Map<String, Object> pass = new LinkedHashMap<>();
        pass.put("formatVersion", 1);
        pass.put("passTypeIdentifier", passTypeId);
        pass.put("teamIdentifier", teamId);
        pass.put("serialNumber", data.serial());
        pass.put("organizationName", "foodsharing");
        pass.put("description", "Foodsaver passport");
        pass.put("foregroundColor", "rgb(255, 255, 255)");
        pass.put("backgroundColor", "rgb(83, 58, 32)");
        if (data.webServiceUrl() != null) {
            pass.put("webServiceURL", data.webServiceUrl());
            pass.put("authenticationToken", data.authToken());
        }
        List<Map<String, Object>> primary = List.of(field("name", "Name", data.name()));
        List<Map<String, Object>> secondary = new ArrayList<>();
        secondary.add(field("number", "No.", data.foodsaverNumber() == null ? "-" : data.foodsaverNumber().toString()));
        secondary.add(field("validUntil", "Valid until", data.validUntil()));
        List<Map<String, Object>> auxiliary = data.regionName() == null ? List.of()
                : List.of(field("region", "Region", data.regionName()));
        pass.put("generic", Map.of("primaryFields", primary, "secondaryFields", secondary,
                "auxiliaryFields", auxiliary));
        pass.put("barcodes", List.of(Map.of("format", "PKBarcodeFormatQR", "message", data.qrUrl(),
                "messageEncoding", "iso-8859-1")));
        return pass;
    }

    private static Map<String, Object> field(String key, String label, String value) {
        return Map.of("key", key, "label", label, "value", value == null ? "" : value);
    }

    byte[] manifest(Map<String, byte[]> files) throws NoSuchAlgorithmException {
        Map<String, String> hashes = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            hashes.put(file.getKey(), sha1(file.getValue()));
        }
        return json.writeValueAsString(hashes).getBytes(StandardCharsets.UTF_8);
    }

    static String sha1(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
    }

    byte[] sign(byte[] manifest) throws Exception {
        CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(key);
        generator.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                new JcaDigestCalculatorProviderBuilder().build()).build(signer, certificate));
        List<X509Certificate> chain = new ArrayList<>(List.of(certificate));
        if (wwdr != null) {
            chain.add(wwdr);
        }
        generator.addCertificates(new JcaCertStore(chain));
        CMSSignedData signed = generator.generate(new CMSProcessableByteArray(manifest), false);
        return signed.getEncoded();
    }

    private static byte[] icon(int size) throws IOException {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x64, 0xae, 0x24));
        g.fillOval(0, 0, size, size);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
