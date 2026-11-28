package com.smart.report.pdf;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link PdfDocument#arabicIndicDigits}: كل رقم بيترسم ١٢٣، في نفس مكانه، والرموز لاتيني. */
class ArabicIndicDigitsTest {

    private static String text(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static PdfDocument invoice(boolean arabic) {
        PdfDocument document = PdfDocument.rtl(Paper.A5).arabicIndicDigits(arabic)
                .letterhead("شركة النور", "ت: 0100 000 0000")
                .title("فاتورة رقم 1751")
                .field("التاريخ", "2026-11-28")
                .table(t -> t.column("الصنف", 3).column("الكمية", 1, Align.END).column("القيمة", 2, Align.END)
                        .row("مسمار 10 مم", "12", "1,250.50")
                        .row("خصم", "1", "-50.00")
                        .totals("الإجمالى", "13", "1,200.50"))
                .summaryTotal("الصافى", "1,200.50");
        return document;
    }

    @Test
    void everyNumberIsArabicIndic() throws Exception {
        byte[] pdf = invoice(true).toBytes();
        // بترتيب الرسم: PDFTextStripper بيعيد ترتيب الأرقام العربية في السطر العربي وهو بيستخرج النص.
        Glyphs glyphs = Glyphs.of(pdf);
        for (String expected : List.of("١٧٥١", "٢٠٢٦-١١-٢٨", "١,٢٥٠.٥٠", "١,٢٠٠.٥٠", "-٥٠.٠٠", "١٢", "٠١٠٠", "١٠")) {
            assertTrue(glyphs.drawn(expected, 1), expected);
        }
        String text = text(pdf);
        assertFalse(text.chars().anyMatch(c -> c >= '0' && c <= '9'), "no Latin digit left:\n" + text);
    }

    /** الرقم في نفس مكانه وبنفس ترتيبه: نفس الحروف اللي من غير الخيار، شكل تاني بس. */
    @Test
    void numbersStayWhereTheyWere() throws Exception {
        Glyphs latin = Glyphs.of(invoice(false).toBytes());
        Glyphs arabic = Glyphs.of(invoice(true).toBytes());
        String[][] pairs = {{"1,250.50", "١,٢٥٠.٥٠"}, {"2026-11-28", "٢٠٢٦-١١-٢٨"}, {"50.00", "٥٠.٠٠"}};
        for (String[] pair : pairs) {
            Glyphs.Glyph before = latin.first(pair[0]);
            Glyphs.Glyph after = arabic.first(pair[1]);
            assertEquals(before.y(), after.y(), 0.5f, pair[0] + " same line");
            // الأرقام العربية أضيق شوية في الخط ده: الحافة اللي الخانة متحاذية عليها هي اللي بتفضل مكانها.
            boolean sameLeft = Math.abs(before.x() - after.x()) < 1f;
            boolean sameRight = Math.abs(latin.last(pair[0]).right() - arabic.last(pair[1]).right()) < 1f;
            assertTrue(sameLeft || sameRight, pair[0] + " keeps its aligned edge");
            assertTrue(arabic.last(pair[1]).x() > after.x(), pair[1] + " reads left to right");
        }
        // السالب بعلامته قبل الرقم زي ما هو.
        assertEquals(latin.first("-50.00").x(), arabic.first("-٥٠.٠٠").x(), 1f);
    }

    @Test
    void pageNumbersToo() throws Exception {
        PdfTable table = PdfTable.create().column("اليوم").column("القيمة");
        for (int i = 0; i < 80; i++) {
            table.row("يوم", "5");
        }
        String text = text(PdfDocument.rtl(Paper.A5).arabicIndicDigits(true).table(table).toBytes());
        assertTrue(text.contains("١") && text.contains("٢"), text);
        assertFalse(text.chars().anyMatch(c -> c >= '0' && c <= '9'), text);
    }

    /** الماسح محتاج لاتيني: محتوى QR والباركود والكود تحته زي ما هم. */
    @Test
    void symbolsStayLatin() throws Exception {
        byte[] pdf = PdfDocument.rtl(Paper.ROLL_80).arabicIndicDigits(true).text("إيصال 42")
                .barcode("SHP-2026-0042").qrCode("INV-42").ean13("4006381333931").toBytes();
        String text = text(pdf);
        assertTrue(text.contains("٤٢"), text);
        assertTrue(text.contains("SHP-2026-0042"), "barcode caption stays Latin: " + text);
        assertTrue(text.contains("006381") && text.contains("333931"), "EAN digits stay Latin: " + text);
        BufferedImage page;
        try (PDDocument document = Loader.loadPDF(pdf)) {
            page = new PDFRenderer(document).renderImageWithDPI(0, 300, ImageType.RGB);
        }
        int[] pixels = page.getRGB(0, 0, page.getWidth(), page.getHeight(), null, 0, page.getWidth());
        String qr = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(
                new RGBLuminanceSource(page.getWidth(), page.getHeight(), pixels))),
                Map.of(DecodeHintType.TRY_HARDER, true)).getText();
        assertEquals("INV-42", qr);
    }

    @Test
    void offByDefault() throws Exception {
        String text = text(invoice(false).toBytes());
        assertTrue(text.contains("1,250.50"));
        assertFalse(text.contains("١"));
    }

    @Test
    void theMapping() {
        assertEquals("٠١٢٣٤٥٦٧٨٩", Typesetter.arabicIndic("0123456789"));
        assertEquals("abc", Typesetter.arabicIndic("abc"));
        assertEquals("-٥.٢%", Typesetter.arabicIndic("-5.2%"));
    }

    public record Line(String item, java.math.BigDecimal amount) {}

    @Test
    void theGenericReportTakesItAsAParameter() throws Exception {
        com.smart.report.ReportRequest<Line> request = com.smart.report.ReportRequest.<Line>builder().title("كشف")
                .data(List.of(new Line("مسمار", new java.math.BigDecimal("1250.5"))))
                .addColumn("item", "الصنف").addColumn("amount", "القيمة")
                .parameter(PdfReportProvider.ARABIC_INDIC_DIGITS, true).build();
        byte[] pdf = com.smart.report.ReportService.generate(com.smart.report.ReportType.PDF, request);
        assertTrue(Glyphs.of(pdf).drawn("١,٢٥٠.٥", 1));
        assertFalse(text(pdf).chars().anyMatch(c -> c >= '0' && c <= '9'));
    }
}
