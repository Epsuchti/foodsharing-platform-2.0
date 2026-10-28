package network.foodsharing.upload.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import network.foodsharing.config.AppProperties;

/**
 * Image variants (A41): {@code sm} 96, {@code md} 320, {@code lg} 1280 px longest edge ({@code app.uploads.image-sizes}),
 * JPEG quality 0.85 on a white background, rendered with {@code javax.imageio} + Java2D. GIFs use their first frame.
 * Images are never upscaled; EXIF orientation is ignored (the client crop normalises it).
 */
public class ImageVariants {

    /** Mime types that get variants. */
    public static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    public static final String VARIANT_MIME_TYPE = "image/jpeg";
    private static final float QUALITY = 0.85f;

    /** Original dimensions and the encoded variants by size name. */
    public record Rendered(int width, int height, Map<String, byte[]> variants) {
    }

    private final Map<String, Integer> sizes;

    public ImageVariants(AppProperties properties) {
        this(properties.getUploads().getImageSizes());
    }

    ImageVariants(Map<String, Integer> sizes) {
        this.sizes = new LinkedHashMap<>(sizes);
    }

    public boolean isSize(String size) {
        return size != null && sizes.containsKey(size);
    }

    public static String variantKey(String storageKey, String size) {
        return storageKey + "_" + size;
    }

    public Set<String> sizeNames() {
        return sizes.keySet();
    }

    /** Empty for non-images and for bytes no image reader understands. */
    public Optional<Rendered> render(String mimeType, byte[] bytes) {
        if (mimeType == null || !IMAGE_TYPES.contains(mimeType)) {
            return Optional.empty();
        }
        BufferedImage source;
        try {
            source = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
        if (source == null) {
            return Optional.empty();
        }
        Map<String, byte[]> variants = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, Integer> size : sizes.entrySet()) {
                variants.put(size.getKey(), jpeg(scale(source, size.getValue())));
            }
        } catch (IOException ex) {
            return Optional.empty();
        }
        return Optional.of(new Rendered(source.getWidth(), source.getHeight(), variants));
    }

    static BufferedImage scale(BufferedImage source, int longestEdge) {
        int width = source.getWidth();
        int height = source.getHeight();
        double factor = Math.min(1.0, (double) longestEdge / Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * factor));
        int targetHeight = Math.max(1, (int) Math.round(height * factor));
        BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static byte[] jpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
