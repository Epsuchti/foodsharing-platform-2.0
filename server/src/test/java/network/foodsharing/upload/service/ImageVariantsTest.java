package network.foodsharing.upload.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Image variants (A41): three sizes, aspect ratio kept, never upscaled, GIF first frame, non-images untouched. */
public class ImageVariantsTest {

    private final ImageVariants variants = new ImageVariants(new LinkedHashMap<>(Map.of("sm", 96, "md", 320,
            "lg", 1280)));

    public static byte[] image(int width, int height, String format) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x25834a);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private static BufferedImage read(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    @Test
    void rendersThreeJpegSizesKeepingTheAspectRatio() throws IOException {
        var rendered = variants.render("image/png", image(2000, 1000, "png")).orElseThrow();
        assertThat(rendered.width()).isEqualTo(2000);
        assertThat(rendered.height()).isEqualTo(1000);
        assertThat(rendered.variants()).containsOnlyKeys("sm", "md", "lg");
        BufferedImage sm = read(rendered.variants().get("sm"));
        assertThat(sm.getWidth()).isEqualTo(96);
        assertThat(sm.getHeight()).isEqualTo(48);
        assertThat(read(rendered.variants().get("md")).getWidth()).isEqualTo(320);
        BufferedImage lg = read(rendered.variants().get("lg"));
        assertThat(lg.getWidth()).isEqualTo(1280);
        assertThat(lg.getHeight()).isEqualTo(640);
        assertThat(rendered.variants().get("sm")[0]).isEqualTo((byte) 0xFF); // JPEG SOI marker
    }

    @Test
    void smallImagesAreNotUpscaled() throws IOException {
        var rendered = variants.render("image/jpeg", image(200, 400, "jpg")).orElseThrow();
        assertThat(read(rendered.variants().get("sm")).getHeight()).isEqualTo(96);
        assertThat(read(rendered.variants().get("md")).getHeight()).isEqualTo(320);
        assertThat(read(rendered.variants().get("lg")).getWidth()).isEqualTo(200);
    }

    @Test
    void gifUsesTheFirstFrame() throws IOException {
        var rendered = variants.render("image/gif", image(500, 500, "gif")).orElseThrow();
        assertThat(read(rendered.variants().get("md")).getWidth()).isEqualTo(320);
    }

    @Test
    void nonImagesAndBrokenImagesGetNoVariants() throws IOException {
        assertThat(variants.render("application/pdf", image(10, 10, "png"))).isEmpty();
        assertThat(variants.render("image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2})).isEmpty();
        assertThat(variants.render(null, new byte[0])).isEmpty();
        assertThat(variants.isSize("md")).isTrue();
        assertThat(variants.isSize("xl")).isFalse();
    }
}
