package com.smart.report.pdf;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.smart.report.util.ZatcaQr;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * رمز QR: الصفحة بتترسم صورة وZXing بيقرا الرمز منها، زي ما الموبايل هيقراه من الورقة. المقاس والمكان
 * بيتقاسوا من البكسلات الغامقة، لأن الرمز مربعات مش حروف ({@link Glyphs} ما بيشوفهوش).
 */
class QrCodeTest {

    private static final float POINTS_PER_MM = 72f / 25.4f;
    private static final String ZATCA = ZatcaQr.payload("مؤسسة الهدى للتجارة", "310122393500003",
            Instant.parse("2026-11-27T15:30:00Z"), new BigDecimal("1150.00"), new BigDecimal("150.00"));

    /** صفحة مرسومة: صورتها وكام بكسل في النقطة. */
    private record Page(BufferedImage image, float scale, float width, float height) {

        /** أبعاد كل اللي اترسم (بكسل أغمق من {@code threshold})، بالنقطة من أعلى يسار الصفحة. */
        Box ink(int threshold) {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = -1;
            int maxY = -1;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if (luminance(image.getRGB(x, y)) < threshold) {
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                    }
                }
            }
            assertTrue(maxX >= 0, "الصفحة فاضية");
            return new Box(minX / scale, minY / scale, (maxX + 1) / scale, (maxY + 1) / scale);
        }

        /** أوطى حاجة اترسمت أيًا كان لونها، بالنقطة من أعلى الصفحة. */
        float lowest() {
            return ink(250).bottom();
        }

        /** النص اللي الرمز المرسوم في الصفحة بيشيله. */
        String decode() throws Exception {
            int w = image.getWidth();
            int h = image.getHeight();
            int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(w, h, pixels)));
            return new QRCodeReader().decode(bitmap, Map.of(DecodeHintType.TRY_HARDER, Boolean.TRUE)).getText();
        }

        private static int luminance(int rgb) {
            return (((rgb >> 16) & 0xFF) * 299 + ((rgb >> 8) & 0xFF) * 587 + (rgb & 0xFF) * 114) / 1000;
        }
    }

    private record Box(float left, float top, float right, float bottom) {
        float width() {
            return right - left;
        }

        float height() {
            return bottom - top;
        }
    }

    private static List<Page> render(byte[] pdf, float scale) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(document);
            List<Page> pages = new ArrayList<>();
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                PDPage page = document.getPage(i);
                pages.add(new Page(renderer.renderImage(i, scale, ImageType.RGB), scale,
                        page.getMediaBox().getWidth(), page.getMediaBox().getHeight()));
            }
            return pages;
        }
    }

    private static Page only(PdfDocument document) throws Exception {
        List<Page> pages = render(document.toBytes(), 3f);
        assertEquals(1, pages.size());
        return pages.get(0);
    }

    /** ضلع المربع الواحد لرمز ضلعه {@code sizeMm}. */
    private static float module(String text, float sizeMm) {
        return sizeMm * POINTS_PER_MM / QrSymbol.encode(text).size();
    }

    @Test
    void decodesBackToTheSameText() throws Exception {
        for (String text : new String[]{
                "https://example.com/invoices/2026-0042",
                "فاتورة رقم 42 — مؤسسة الهدى للتجارة، الرياض",
                ZATCA}) {
            Page rtl = only(PdfDocument.rtl(Paper.A4).title("فاتورة ضريبية مبسطة").text("الإجمالى 1,150.00")
                    .qrCode(text));
            assertEquals(text, rtl.decode());
            Page ltr = only(PdfDocument.ltr(Paper.A5).title("Invoice").qrCode(text, 25, Align.START));
            assertEquals(text, ltr.decode());
        }
    }

    /** مربعات متجهة في محتوى الصفحة، مش صورة: حادة على أي طابعة، والملف صغير. */
    @Test
    void isDrawnAsVectorsNotAnImage() throws Exception {
        try (PDDocument pdf = Loader.loadPDF(PdfDocument.ltr(Paper.A5).qrCode(ZATCA).toBytes())) {
            assertFalse(pdf.getPage(0).getResources().getXObjectNames().iterator().hasNext());
        }
    }

    /** الضلع بالمليمتر للرمز نفسه، والمنطقة الفاضية (4 مربعات) زيادة عليه وجوّه الهامش. */
    @Test
    void sizeIsInMillimetresWithAQuietZoneAround() throws Exception {
        String text = "INV-2026-0042";
        Page page = only(PdfDocument.ltr(Paper.A4).qrCode(text, 40, Align.START));
        Box code = page.ink(128);
        float margin = PdfStyle.forPaper(Paper.A4).margin();
        float module = module(text, 40);

        assertEquals(40 * POINTS_PER_MM, code.width(), 0.5f);
        assertEquals(40 * POINTS_PER_MM, code.height(), 0.5f);
        assertEquals(margin + 4 * module, code.left(), 0.5f);
        assertEquals(margin + 4 * module, code.top(), 0.5f);
    }

    /** البداية يمين في المستند العربي وشمال في الإنجليزي، والنهاية عكسها. */
    @Test
    void placementFollowsTheReadingDirection() throws Exception {
        String text = "INV-2026-0042";
        float module = module(text, 30);
        float margin = PdfStyle.forPaper(Paper.A5).margin();
        float width = Paper.A5.width();

        assertEquals(width - margin - 4 * module, only(PdfDocument.rtl(Paper.A5).qrCode(text, Align.START))
                .ink(128).right(), 0.5f);
        assertEquals(margin + 4 * module, only(PdfDocument.rtl(Paper.A5).qrCode(text, Align.END))
                .ink(128).left(), 0.5f);
        assertEquals(margin + 4 * module, only(PdfDocument.ltr(Paper.A5).qrCode(text, Align.START))
                .ink(128).left(), 0.5f);
        assertEquals(width - margin - 4 * module, only(PdfDocument.ltr(Paper.A5).qrCode(text, Align.END))
                .ink(128).right(), 0.5f);
        Box centered = only(PdfDocument.rtl(Paper.A5).qrCode(text)).ink(128);
        assertEquals(width / 2, (centered.left() + centered.right()) / 2, 0.5f);
    }

    /** رمز أعرض من لفة 58 مم بيصغر لحد ما يكفي هو ومنطقته الفاضية، وبيتقرا. */
    @Test
    void codeWiderThanTheRollShrinksToFit() throws Exception {
        Page page = only(PdfDocument.rtl(Paper.ROLL_58).qrCode(ZATCA, 70, Align.START));
        Box code = page.ink(128);
        float margin = PdfStyle.forPaper(Paper.ROLL_58).margin();
        float module = code.width() / QrSymbol.encode(ZATCA).size();

        assertEquals(ZATCA, page.decode());
        assertTrue(code.width() < 70 * POINTS_PER_MM);
        assertEquals(margin + 4 * module, Paper.ROLL_58.width() - code.right(), 0.5f);
        assertEquals(margin + 4 * module, code.left(), 0.5f);
    }

    /**
     * الإيصال اللي آخره رمز QR: صفحة واحدة، الرمز كله فوق الهامش السفلي ومنطقته الفاضية تحته، ومفيش
     * ورق فاضي كتير بعده. القياس الأول للورقة الحرارية لازم يحسب الرمز زي باقي الأجزاء.
     */
    @Test
    void receiptEndingInAQrCodeStaysOnOnePage() throws Exception {
        for (Paper paper : new Paper[]{Paper.ROLL_80, Paper.ROLL_58}) {
            PdfDocument receipt = PdfDocument.rtl(paper).letterhead("مؤسسة الهدى للتجارة", "الرقم الضريبي 310122393500003")
                    .title("فاتورة ضريبية مبسطة")
                    .table(t -> t.column("الصنف", 3).column("الكمية", 1).column("السعر", 2, Align.END)
                            .row("أرز بسمتي", "2", "1,000.00")
                            .totals("الإجمالى", "2", "1,000.00"))
                    .summary("ضريبة القيمة المضافة 15%", "150.00")
                    .summaryTotal("الإجمالى شامل الضريبة", "1,150.00")
                    .qrCode(ZATCA);
            List<Page> pages = render(receipt.toBytes(), 4f);

            assertEquals(1, pages.size(), paper.name());
            Page page = pages.get(0);
            float margin = PdfStyle.forPaper(paper).margin();
            assertEquals(ZATCA, page.decode(), paper.name());
            assertTrue(page.lowest() <= page.height() - margin, paper.name() + ": " + page.lowest() + " من " + page.height());
            assertTrue(page.lowest() > page.height() - margin - 40, paper.name() + ": ورق فاضي كتير تحت الرمز");
            assertTrue(page.height() < 1000, paper.name() + ": " + page.height());
        }
    }

    /** الرمز ما بيتقسمش ولا بيخرج عن الورقة: أيًا كان مكانه في آخر الصفحة بيترسم كله في صفحة واحدة. */
    @Test
    void codeIsNeverSplitAtThePageEnd() throws Exception {
        String text = "INV-2026-0042";
        // من 10 صفوف الرمز بيكفي في أول صفحة بالعافية، ومن حوالي 20 بيتنقل للتانية، وبعد 28 الجدول نفسه بيكمل.
        for (int rows = 10; rows <= 30; rows += 2) {
            PdfTable table = PdfTable.create().column("Day", 1);
            for (int i = 1; i <= rows; i++) {
                table.row("row" + i);
            }
            List<Page> pages = render(PdfDocument.ltr(Paper.A5).pageNumbers(PageNumbers.NONE).table(table)
                    .qrCode(text, 40, Align.CENTER).toBytes(), 2f);
            float margin = PdfStyle.forPaper(Paper.A5).margin();
            for (Page page : pages) {
                assertTrue(page.lowest() <= page.height() - margin, rows + " صف: " + page.lowest());
            }
            assertEquals(text, pages.get(pages.size() - 1).decode(), rows + " صف");
        }
    }

    @Test
    void rejectsWhatCannotBeACode() {
        PdfDocument document = PdfDocument.ltr(Paper.A4);
        assertThrows(IllegalArgumentException.class, () -> document.qrCode(""));
        assertThrows(IllegalArgumentException.class, () -> document.qrCode(null));
        assertThrows(IllegalArgumentException.class, () -> document.qrCode("x", 0, Align.CENTER));
        assertThrows(IllegalArgumentException.class, () -> document.qrCode("x", Float.NaN, Align.CENTER));
        assertThrows(IllegalArgumentException.class, () -> document.qrCode("ش".repeat(2000)));
        assertTrue(document.blocks().isEmpty(), "الرمز المرفوض ما بيتضافش");
    }
}
