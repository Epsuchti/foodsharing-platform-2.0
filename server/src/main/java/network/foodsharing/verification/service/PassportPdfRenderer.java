package network.foodsharing.verification.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Passport PDFs with OpenPDF (layout: docs/passport-layout.md): an ID-1 card (85.6 × 54 mm) with header band, photo
 * area, name, foodsaver number, home region, validity, QR code (vector, zxing) and signature line; as a single card
 * page or as A4 sheets with 2 × 5 cards and cut marks. Standard Helvetica (WinAnsi covers the five UI languages).
 */
@Component
public class PassportPdfRenderer {

    public enum Paper { A4, CARD }

    public record Card(String name, Long foodsaverNumber, String regionName, LocalDate validFrom, LocalDate validUntil,
                       String qrUrl, String language) {
    }

    static final float MM = 72f / 25.4f;
    static final float CARD_WIDTH = 85.6f * MM;
    static final float CARD_HEIGHT = 54f * MM;
    static final int COLUMNS = 2;
    static final int ROWS = 5;
    private static final Color GREEN = new Color(0x53, 0x3a, 0x20);
    private static final Color ACCENT = new Color(0x64, 0xae, 0x24);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Map<String, String[]> LABELS = Map.of(
            "de", new String[] {"Foodsaver-Ausweis", "Nr.", "Gültig", "Unterschrift"},
            "en", new String[] {"Foodsaver passport", "No.", "Valid", "Signature"},
            "gws", new String[] {"Foodsaver-Uswiis", "Nr.", "Gültig", "Unterschrift"},
            "fr", new String[] {"Carte de foodsaver", "N°", "Valable", "Signature"},
            "it", new String[] {"Tessera foodsaver", "N.", "Valida", "Firma"});

    public byte[] render(List<Card> cards, Paper paper) {
        Rectangle pageSize = paper == Paper.CARD ? new Rectangle(CARD_WIDTH, CARD_HEIGHT) : PageSize.A4;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(pageSize, 0, 0, 0, 0);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.addTitle("foodsharing passport");
            document.addCreator("foodsharing platform");
            document.open();
            BaseFont regular = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            BaseFont bold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            PdfContentByte canvas = writer.getDirectContent();
            if (paper == Paper.CARD) {
                for (int i = 0; i < cards.size(); i++) {
                    if (i > 0) {
                        document.newPage();
                    }
                    drawCard(canvas, cards.get(i), 0, 0, regular, bold);
                }
            } else {
                float marginX = (pageSize.getWidth() - COLUMNS * CARD_WIDTH) / 2;
                float marginY = (pageSize.getHeight() - ROWS * CARD_HEIGHT) / 2;
                int perPage = COLUMNS * ROWS;
                for (int i = 0; i < cards.size(); i++) {
                    if (i > 0 && i % perPage == 0) {
                        document.newPage();
                    }
                    int slot = i % perPage;
                    float x = marginX + (slot % COLUMNS) * CARD_WIDTH;
                    float y = pageSize.getHeight() - marginY - (slot / COLUMNS + 1) * CARD_HEIGHT;
                    drawCutMarks(canvas, x, y);
                    drawCard(canvas, cards.get(i), x, y, regular, bold);
                }
            }
            if (cards.isEmpty()) {
                canvas.beginText();
                canvas.setFontAndSize(regular, 8);
                canvas.showTextAligned(PdfContentByte.ALIGN_LEFT, "-", 10, 10, 0);
                canvas.endText();
            }
            document.close();
        } catch (Exception e) {
            throw new IllegalStateException("Passport PDF could not be rendered", e);
        }
        return out.toByteArray();
    }

    /** Page count of an A4 batch. */
    public static int a4Pages(int cards) {
        return Math.max(1, (cards + COLUMNS * ROWS - 1) / (COLUMNS * ROWS));
    }

    private void drawCard(PdfContentByte canvas, Card card, float x, float y, BaseFont regular, BaseFont bold)
            throws WriterException {
        String[] labels = LABELS.getOrDefault(card.language(), LABELS.get("de"));
        // frame
        canvas.setLineWidth(0.5f);
        canvas.setColorStroke(Color.GRAY);
        canvas.roundRectangle(x + 1, y + 1, CARD_WIDTH - 2, CARD_HEIGHT - 2, 3 * MM);
        canvas.stroke();
        // header band
        float band = 11 * MM;
        canvas.setColorFill(GREEN);
        canvas.rectangle(x + 1, y + CARD_HEIGHT - band - 1, CARD_WIDTH - 2, band);
        canvas.fill();
        text(canvas, bold, 11, Color.WHITE, labels[0], x + 4 * MM, y + CARD_HEIGHT - 7.5f * MM);
        text(canvas, regular, 7, ACCENT, "foodsharing", x + CARD_WIDTH - 24 * MM, y + CARD_HEIGHT - 7 * MM);
        // photo placeholder
        float photoW = 19 * MM;
        float photoH = 24 * MM;
        float photoX = x + 4 * MM;
        float photoY = y + CARD_HEIGHT - band - photoH - 3 * MM;
        canvas.setColorFill(new Color(0xdd, 0xdd, 0xdd));
        canvas.rectangle(photoX, photoY, photoW, photoH);
        canvas.fill();
        // texts
        float textX = photoX + photoW + 3 * MM;
        float lineY = y + CARD_HEIGHT - band - 7 * MM;
        text(canvas, bold, 10, Color.BLACK, card.name(), textX, lineY);
        text(canvas, regular, 8, Color.BLACK, labels[1] + " " + (card.foodsaverNumber() == null ? "-"
                : card.foodsaverNumber()), textX, lineY - 5 * MM);
        if (card.regionName() != null) {
            text(canvas, regular, 8, Color.BLACK, card.regionName(), textX, lineY - 9.5f * MM);
        }
        text(canvas, regular, 7, Color.DARK_GRAY, labels[2] + " " + DATE.format(card.validFrom()) + " – "
                + DATE.format(card.validUntil()), textX, lineY - 14 * MM);
        // signature line
        canvas.setColorStroke(Color.BLACK);
        canvas.moveTo(photoX, y + 7 * MM);
        canvas.lineTo(photoX + 35 * MM, y + 7 * MM);
        canvas.stroke();
        text(canvas, regular, 6, Color.DARK_GRAY, labels[3], photoX, y + 4 * MM);
        // QR code (vector modules)
        float qrSize = 19 * MM;
        drawQr(canvas, card.qrUrl(), x + CARD_WIDTH - qrSize - 4 * MM, y + 3 * MM, qrSize);
    }

    private static void text(PdfContentByte canvas, BaseFont font, float size, Color color, String value, float x,
                             float y) {
        canvas.beginText();
        canvas.setFontAndSize(font, size);
        canvas.setColorFill(color);
        canvas.showTextAligned(PdfContentByte.ALIGN_LEFT, value == null ? "" : value, x, y, 0);
        canvas.endText();
    }

    static void drawQr(PdfContentByte canvas, String content, float x, float y, float size) throws WriterException {
        BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0,
                Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 0));
        float module = size / matrix.getWidth();
        canvas.setColorFill(Color.BLACK);
        for (int row = 0; row < matrix.getHeight(); row++) {
            for (int column = 0; column < matrix.getWidth(); column++) {
                if (matrix.get(column, row)) {
                    canvas.rectangle(x + column * module, y + size - (row + 1) * module, module, module);
                }
            }
        }
        canvas.fill();
    }

    private static void drawCutMarks(PdfContentByte canvas, float x, float y) {
        float length = 3 * MM;
        canvas.setLineWidth(0.25f);
        canvas.setColorStroke(Color.LIGHT_GRAY);
        float[][] corners = {{x, y}, {x + CARD_WIDTH, y}, {x, y + CARD_HEIGHT}, {x + CARD_WIDTH, y + CARD_HEIGHT}};
        for (float[] corner : corners) {
            canvas.moveTo(corner[0] - length, corner[1]);
            canvas.lineTo(corner[0] - 0.5f * MM, corner[1]);
            canvas.moveTo(corner[0] + 0.5f * MM, corner[1]);
            canvas.lineTo(corner[0] + length, corner[1]);
            canvas.moveTo(corner[0], corner[1] - length);
            canvas.lineTo(corner[0], corner[1] - 0.5f * MM);
            canvas.moveTo(corner[0], corner[1] + 0.5f * MM);
            canvas.lineTo(corner[0], corner[1] + length);
        }
        canvas.stroke();
    }
}
