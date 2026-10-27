package network.foodsharing.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import network.foodsharing.verification.service.PassportPdfRenderer;
import network.foodsharing.verification.service.PassportPdfRenderer.Card;
import network.foodsharing.verification.service.PassportPdfRenderer.Paper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/** Passport PDF content and pagination (pdfbox); also renders target/passport-sample.png for docs/passport-layout.md. */
class PassportPdfRendererTest {

    private final PassportPdfRenderer renderer = new PassportPdfRenderer();

    private static Card card(int i, String language) {
        return new Card("Lukas Meier " + i, 100000L + i, "Zürich Stadt", LocalDate.of(2025, 3, 1),
                LocalDate.of(2028, 3, 1), "https://foodsharing.network/users/" + i, language);
    }

    private static List<Card> cards(int count) {
        List<Card> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            list.add(card(i, "de"));
        }
        return list;
    }

    @Test
    void cardContainsNameNumberRegionAndValidity() throws Exception {
        byte[] pdf = renderer.render(List.of(card(6, "de")), Paper.CARD);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Foodsaver-Ausweis", "Lukas Meier 6", "Nr. 100006", "Zürich Stadt",
                    "01.03.2025", "01.03.2028", "Unterschrift");
            BufferedImage image = new PDFRenderer(document).renderImageWithDPI(0, 200);
            new File("target").mkdirs();
            ImageIO.write(image, "png", new File("target/passport-sample.png"));
        }
        try (PDDocument document = Loader.loadPDF(renderer.render(List.of(card(1, "fr")), Paper.CARD))) {
            assertThat(new PDFTextStripper().getText(document)).contains("Carte de foodsaver", "Valable");
        }
    }

    @Test
    void a4SheetsHoldTenCards() throws Exception {
        for (int[] expectation : new int[][] {{1, 1}, {10, 1}, {11, 2}, {25, 3}}) {
            try (PDDocument document = Loader.loadPDF(renderer.render(cards(expectation[0]), Paper.A4))) {
                assertThat(document.getNumberOfPages()).as("%d cards", expectation[0]).isEqualTo(expectation[1]);
                assertThat(PassportPdfRenderer.a4Pages(expectation[0])).isEqualTo(expectation[1]);
            }
        }
        try (PDDocument document = Loader.loadPDF(renderer.render(cards(3), Paper.CARD))) {
            assertThat(document.getNumberOfPages()).isEqualTo(3);
        }
    }
}
