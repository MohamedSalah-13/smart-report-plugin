package com.smart.report.pdf;

import com.smart.report.ReportRequest;
import com.smart.report.ReportService;
import com.smart.report.ReportType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CellStyleAndImageTest {

    private static final float MM = 72f / 25.4f;
    private static final int DPI = 144;

    private static BufferedImage render(byte[] pdf, int page) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFRenderer(document).renderImageWithDPI(page, DPI, ImageType.RGB);
        }
    }

    /** عدد البكسلات الحمرا في صندوق الكلمة. */
    private static double redShare(byte[] pdf, String token) throws Exception {
        Glyphs.Glyph first = Glyphs.of(pdf).first(token);
        Glyphs.Glyph last = Glyphs.of(pdf).last(token);
        BufferedImage page = render(pdf, first.page() - 1);
        float scale = DPI / 72f;
        int x0 = Math.round(Math.min(first.x(), last.x()) * scale);
        int x1 = Math.round(Math.max(first.right(), last.right()) * scale);
        int y0 = Math.round((first.y() - 7) * scale);
        int y1 = Math.round(first.y() * scale);
        int red = 0;
        int ink = 0;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                Color c = new Color(page.getRGB(x, y));
                if (c.getRed() + c.getGreen() + c.getBlue() < 690) {
                    ink++;
                    // الأحمر غالب، حتى في حواف الحرف الفاتحة (الأسود حوافه رمادي).
                    if (c.getRed() - c.getGreen() > 50 && c.getRed() - c.getBlue() > 50) {
                        red++;
                    }
                }
            }
        }
        assertTrue(ink > 0, "nothing drawn at " + token);
        // عدد البكسلات الحمرا، مش نسبتها: صف الإجمالي على خلفية ملوّنة بتتحسب حبر.
        return red;
    }

    /**
     * عدد المرات اللي النص اترسم فيها عريض: الخط المضمَّن مالوش وجه عريض، فالعريض بيترسم بملء وتحديد ({@code 2 Tr}).
     */
    private static long boldRuns(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String content = new String(document.getPage(0).getContents().readAllBytes(),
                    java.nio.charset.StandardCharsets.ISO_8859_1);
            return content.lines().filter(line -> line.strip().equals("2 Tr")).count()
                    + content.lines().filter(line -> line.strip().endsWith("Tf") && line.contains("Bold")).count();
        }
    }

    /** اسم الخط اللي أول حرف من الكلمة اترسم بيه. */
    private static String fontOf(byte[] pdf, String token) throws Exception {
        List<TextPosition> positions = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            new PDFTextStripper() {
                @Override
                protected void processTextPosition(TextPosition text) {
                    positions.add(text);
                }
            }.getText(document);
        }
        StringBuilder all = new StringBuilder();
        for (TextPosition position : positions) {
            all.append(position.getUnicode());
        }
        int at = all.indexOf(token);
        assertTrue(at >= 0, token + " not drawn");
        int index = 0;
        for (TextPosition position : positions) {
            if (index >= at) {
                return position.getFont().getName();
            }
            index += position.getUnicode().length();
        }
        throw new AssertionError(token);
    }

    // ===================== #34 =====================

    private static PdfTable statement() {
        return PdfTable.create().column("Date", 2).column("Note", 3).column("Balance", 2, Align.END)
                .row("2026-11-01", "Opening", "1,200.00")
                .row("2026-11-02", "Refund", "-350.00")
                .row("2026-11-03", "Credit", "(75.50)")
                .row("2026-11-04", "Return", "40.00-")
                .totals("", "Total", "-1,500.00");
    }

    @Test
    void negativeBalancesAreRed() throws Exception {
        byte[] pdf = PdfDocument.ltr(Paper.A4).table(statement().negativeInRed(2)).toBytes();
        assertTrue(redShare(pdf, "350.00") > 20, "-350.00");
        assertTrue(redShare(pdf, "75.50") > 20, "(75.50)");
        assertTrue(redShare(pdf, "40.00") > 20, "40.00-");
        assertTrue(redShare(pdf, "1,500.00") > 20, "الإجمالي السالب كمان");
        assertTrue(redShare(pdf, "1,200.00") < 3, "الموجب زي ما هو");
        assertTrue(redShare(pdf, "Refund") < 3, "الأعمدة التانية زي ما هي");
        assertTrue(redShare(pdf, "Balance") < 3, "الرأس زي ما هو");
    }

    @Test
    void withoutColumnsEveryColumnIsChecked() throws Exception {
        byte[] pdf = PdfDocument.ltr(Paper.A4).table(PdfTable.create().column("A").column("B")
                .row("-5", "x-y").negativeInRed()).toBytes();
        assertTrue(redShare(pdf, "5") > 20);
        assertTrue(redShare(pdf, "x-y") < 3, "مفيش رقم: مش سالب");
    }

    @Test
    void aRuleCanMakeACellBold() throws Exception {
        PdfTable table = statement().cellStyle((row, column, text) ->
                row == 0 && column == 1 ? CellStyle.bold() : CellStyle.PLAIN);
        byte[] pdf = PdfDocument.ltr(Paper.A4).table(table).toBytes();
        long plain = boldRuns(PdfDocument.ltr(Paper.A4).table(statement()).toBytes());
        assertEquals(plain + 1, boldRuns(pdf), "خلية واحدة زيادة عريضة");
        assertTrue(fontOf(pdf, "Opening").contains("NotoNaskhArabic"), fontOf(pdf, "Opening"));
        // الإجمالي عريض أصلًا، واللون من غير bold ما بيغيرش ده.
        byte[] colouredTotal = PdfDocument.ltr(Paper.A4).table(statement().cellStyle((row, column, text) ->
                text.equals("Total") ? CellStyle.PLAIN.andColor(0x000000) : CellStyle.PLAIN)).toBytes();
        assertEquals(plain, boldRuns(colouredTotal));
    }

    @Test
    void laterRulesWinInWhatTheySet() {
        CellStyle red = CellStyle.color(CellStyle.RED);
        assertEquals(CellStyle.color(CellStyle.RED).andBold(), red.under(CellStyle.bold()));
        assertEquals(CellStyle.color(0x0000FF), red.under(CellStyle.color(0x0000FF)));
        assertEquals(red, red.under(CellStyle.PLAIN));
        assertThrows(IllegalArgumentException.class, () -> CellStyle.color(0x1000000));
        assertThrows(IllegalArgumentException.class, () -> CellStyle.color(-1));
    }

    @Test
    void negativeText() {
        for (String yes : new String[]{"-5", "−5", "5-", "(5)", " -1,200.00 ", "-٥"}) {
            assertTrue(PdfTable.negative(yes), yes);
        }
        for (String no : new String[]{"", "-", "5", "x-y", "(a)", "—", null, "2026-11-01"}) {
            assertFalse(PdfTable.negative(no), no);
        }
    }

    /** التاريخ فيه شرطة في نصه مش في أوله: مش سالب. */
    @Test
    void datesStayBlack() throws Exception {
        byte[] pdf = PdfDocument.ltr(Paper.A4).table(statement().negativeInRed()).toBytes();
        assertTrue(redShare(pdf, "2026-11-02") < 3);
    }

    public record Line(String note, BigDecimal balance) {}

    @Test
    void theGenericReportTakesItAsAParameter() throws Exception {
        ReportRequest<Line> request = ReportRequest.<Line>builder().title("Statement")
                .data(List.of(new Line("Opening", new BigDecimal("100")), new Line("Refund", new BigDecimal("-25.5"))))
                .addColumn("note", "Note").addColumn("balance", "Balance")
                .parameter(PdfReportProvider.NEGATIVE_IN_RED, true).build();
        byte[] pdf = ReportService.generate(ReportType.PDF, request);
        assertTrue(redShare(pdf, "25.5") > 20);
        assertTrue(redShare(pdf, "100") < 3);
    }

    // ===================== #35 =====================

    private static byte[] png(int width, int height, Color color) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] jpeg(int width, int height, Color color) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", out);
        return out.toByteArray();
    }

    /** صندوق البكسلات القريبة من اللون ده في الصفحة، بالنقطة: {يسار، فوق، عرض، طول}. */
    private static float[] box(byte[] pdf, int page, Color color) throws Exception {
        BufferedImage image = render(pdf, page);
        int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE, right = -1, bottom = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color c = new Color(image.getRGB(x, y));
                if (Math.abs(c.getRed() - color.getRed()) < 30 && Math.abs(c.getGreen() - color.getGreen()) < 30
                        && Math.abs(c.getBlue() - color.getBlue()) < 30) {
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
            }
        }
        if (right < 0) {
            return null;
        }
        float scale = DPI / 72f;
        return new float[]{left / scale, top / scale, (right - left + 1) / scale, (bottom - top + 1) / scale};
    }

    private static int pages(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        }
    }

    private static final Color STAMP = new Color(30, 60, 200);

    @Test
    void aStampPrintsAtTheSizeGiven() throws Exception {
        byte[] pdf = PdfDocument.rtl(Paper.A4).title("فاتورة").image(png(400, 200, STAMP), 40, Align.START).toBytes();
        float[] box = box(pdf, 0, STAMP);
        assertEquals(40 * MM, box[2], 1.5f);
        assertEquals(20 * MM, box[3], 1.5f);
        float right = box[0] + box[2];
        assertEquals(Paper.A4.width() - PdfStyle.forPaper(Paper.A4).margin(), right, 1.5f, "بداية السطر في العربي يمين");
    }

    @Test
    void jpegAndPathWorkToo(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("signature.jpg");
        Files.write(file, jpeg(300, 100, STAMP));
        byte[] pdf = PdfDocument.ltr(Paper.A5).image(file, 30, Align.END).toBytes();
        float[] box = box(pdf, 0, STAMP);
        assertEquals(30 * MM, box[2], 1.5f);
        assertEquals(10 * MM, box[3], 1.5f);
        assertEquals(Paper.A5.width() - PdfStyle.forPaper(Paper.A5).margin(), box[0] + box[2], 1.5f);
    }

    /** الصورة ما بتتقسمش: لو الباقي ما كفّاهاش بتنزل كلها للصفحة الجاية. */
    @Test
    void itMovesWholeToTheNextPage() throws Exception {
        PdfDocument document = PdfDocument.ltr(Paper.A5).title("Stamp");
        float room = Paper.A5.height() - 2 * PdfStyle.forPaper(Paper.A5).margin();
        document.space(room - 40 * MM);
        byte[] pdf = document.image(png(200, 200, STAMP), 50, Align.CENTER).toBytes();
        assertEquals(2, pages(pdf));
        assertEquals(null, box(pdf, 0, STAMP));
        float[] box = box(pdf, 1, STAMP);
        assertEquals(50 * MM, box[2], 1.5f);
        assertEquals(50 * MM, box[3], 1.5f);
    }

    @Test
    void tooWideOrTooTallShrinksKeepingItsShape() throws Exception {
        byte[] wide = PdfDocument.ltr(Paper.ROLL_58).image(png(300, 100, STAMP), 200, Align.CENTER).toBytes();
        float[] box = box(wide, 0, STAMP);
        float line = Paper.ROLL_58.width() - 2 * PdfStyle.forPaper(Paper.ROLL_58).margin();
        assertEquals(line, box[2], 1.5f);
        assertEquals(line / 3, box[3], 1.5f);

        byte[] tall = PdfDocument.ltr(Paper.A5).image(png(100, 1000, STAMP), 100, Align.CENTER).toBytes();
        assertEquals(1, pages(tall));
        float[] tallBox = box(tall, 0, STAMP);
        assertEquals(tallBox[3] / 10, tallBox[2], 1.5f);
    }

    @Test
    void notAnImageIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> PdfDocument.ltr(Paper.A4).image(new byte[]{1, 2, 3}, 20, null));
        assertThrows(IllegalArgumentException.class, () -> PdfDocument.ltr(Paper.A4).image(new byte[0], 20, null));
        assertThrows(IllegalArgumentException.class, () -> PdfDocument.ltr(Paper.A4).image(png(2, 2, STAMP), 0, null));
        assertThrows(NullPointerException.class, () -> PdfDocument.ltr(Paper.A4).image((byte[]) null, 20, null));
        assertThrows(java.io.UncheckedIOException.class,
                () -> PdfDocument.ltr(Paper.A4).image(Path.of("no-such-stamp.png"), 20, null));
    }

    /** الإيصال على اللفة بيتقص على قد المحتوى، والصورة جزء منه. */
    @Test
    void onARollTheImageIsPartOfTheCut() throws Exception {
        byte[] pdf = PdfDocument.rtl(Paper.ROLL_80).text("شكرا").image(png(100, 100, STAMP), 30, Align.CENTER).toBytes();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertEquals(1, document.getNumberOfPages());
            assertTrue(document.getPage(0).getMediaBox().getHeight() > 30 * MM);
        }
        assertTrue(box(pdf, 0, STAMP) != null);
    }
}
