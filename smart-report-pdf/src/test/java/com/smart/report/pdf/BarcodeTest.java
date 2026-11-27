package com.smart.report.pdf;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.smart.report.ReportException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * الباركود: الصفحة بتترسم صورة وZXing بيقراه منها، بدقة الطابعة الحرارية (203 نقطة في البوصة، أبيض
 * وأسود بس) زي ما قارئ اليد هيقراه من الورقة. المكان والعرض بيتقاسوا من البكسلات الغامقة.
 */
class BarcodeTest {

    private static final float POINTS_PER_MM = 72f / 25.4f;
    /** بكسلات لكل نقطة في طابعة 203 نقطة في البوصة. */
    private static final float THERMAL = 203f / 72f;

    private record Page(BufferedImage image, float scale, float width, float height) {

        /** أبعاد الخطوط (بكسل أغمق من 128) في الصفوف اللي بين {@code fromY} و{@code toY} بالنقطة. */
        float[] ink(float fromY, float toY) {
            int minX = Integer.MAX_VALUE;
            int maxX = -1;
            for (int y = (int) (fromY * scale); y < Math.min(image.getHeight(), (int) (toY * scale)); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getRGB(x, y) & 0xFF) < 128) {
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                    }
                }
            }
            assertTrue(maxX >= 0, "مفيش خطوط");
            return new float[]{minX / scale, (maxX + 1) / scale};
        }

        /** أوطى بكسل اترسم، بالنقطة من أعلى الصفحة. */
        float lowest() {
            for (int y = image.getHeight() - 1; y >= 0; y--) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getRGB(x, y) & 0xFF) < 250) {
                        return (y + 1) / scale;
                    }
                }
            }
            return 0;
        }

        String decode() throws Exception {
            int w = image.getWidth();
            int h = image.getHeight();
            int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(w, h, pixels)));
            return new MultiFormatReader().decode(bitmap, Map.of(
                    DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.CODE_128),
                    DecodeHintType.TRY_HARDER, Boolean.TRUE)).getText();
        }
    }

    private static List<Page> render(byte[] pdf, float scale, ImageType type) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(document);
            List<Page> pages = new ArrayList<>();
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                pages.add(new Page(renderer.renderImage(i, scale, type), scale,
                        document.getPage(i).getMediaBox().getWidth(), document.getPage(i).getMediaBox().getHeight()));
            }
            return pages;
        }
    }

    private static Page only(PdfDocument document, float scale, ImageType type) throws Exception {
        List<Page> pages = render(document.toBytes(), scale, type);
        assertEquals(1, pages.size());
        return pages.get(0);
    }

    private static String text(PdfDocument document) throws Exception {
        try (PDDocument pdf = Loader.loadPDF(document.toBytes())) {
            return new PDFTextStripper().getText(pdf);
        }
    }

    /** المسافة من أول خط لآخر خط بالنقطة، لأضيق خط بالعرض الافتراضي. */
    private static float barsWidth(String text) {
        return BarcodeSymbol.encode(text).modules() * PageWriter.BAR_MODULE;
    }

    @Test
    void decodesAtThermalPrinterResolution() throws Exception {
        for (String code : new String[]{"SHP-2026-0042", "123456789012", "inv-42/a b"}) {
            for (Paper paper : new Paper[]{Paper.ROLL_80, Paper.ROLL_58, Paper.A5}) {
                Page rtl = only(PdfDocument.rtl(paper).title("بوليصة شحن").barcode(code), THERMAL, ImageType.BINARY);
                assertEquals(code, rtl.decode(), code + " على " + paper.name());
            }
            Page ltr = only(PdfDocument.ltr(Paper.A4).title("Waybill").barcode(code, 15, Align.START), 2f, ImageType.RGB);
            assertEquals(code, ltr.decode(), code);
        }
    }

    /** النص تحت الخطوط بترتيبه، حتى في المستند العربي وحتى لو أوله أرقام. */
    @Test
    void captionIsTheCodeInItsOwnOrder() throws Exception {
        for (String code : new String[]{"SHP-2026-0042", "2026-0042-SHP"}) {
            assertTrue(text(PdfDocument.rtl(Paper.A5).text("رقم الشحنة").barcode(code)).contains(code), code);
            assertTrue(text(PdfDocument.ltr(Paper.A5).barcode(code)).contains(code), code);
        }
    }

    /** أضيق خط 0.33 مم، والبداية يمين في المستند العربي وشمال في الإنجليزي، بعد منطقة فاضية 10 خطوط. */
    @Test
    void placementAndWidthFollowTheDirection() throws Exception {
        String code = "SHP-2026-0042";
        float bars = barsWidth(code);
        float quiet = BarcodeSymbol.QUIET_ZONE * PageWriter.BAR_MODULE;
        float margin = PdfStyle.forPaper(Paper.A4).margin();
        float width = Paper.A4.width();
        // الخطوط بس: من أعلى الصفحة لحد قبل السطر المكتوب.
        float from = margin + 2;
        float to = margin + 12 * POINTS_PER_MM;

        float[] start = only(PdfDocument.ltr(Paper.A4).barcode(code, Align.START), 3f, ImageType.RGB).ink(from, to);
        assertEquals(margin + quiet, start[0], 0.5f);
        assertEquals(bars, start[1] - start[0], 0.5f);
        float[] rtlStart = only(PdfDocument.rtl(Paper.A4).barcode(code, Align.START), 3f, ImageType.RGB).ink(from, to);
        assertEquals(width - margin - quiet, rtlStart[1], 0.5f);
        float[] rtlEnd = only(PdfDocument.rtl(Paper.A4).barcode(code, Align.END), 3f, ImageType.RGB).ink(from, to);
        assertEquals(margin + quiet, rtlEnd[0], 0.5f);
        float[] centre = only(PdfDocument.rtl(Paper.A4).barcode(code), 3f, ImageType.RGB).ink(from, to);
        assertEquals(width / 2, (centre[0] + centre[1]) / 2, 0.5f);
    }

    /** على لفة 58 مم الكود الأعرض من السطر بيضيق لحد ما يكفي هو ومنطقته الفاضية، وبيتقرا. */
    @Test
    void codeWiderThanTheRollNarrowsToFit() throws Exception {
        String code = "SHP-2026-0042";
        Page page = only(PdfDocument.rtl(Paper.ROLL_58).barcode(code), THERMAL, ImageType.BINARY);
        float margin = PdfStyle.forPaper(Paper.ROLL_58).margin();
        float line = Paper.ROLL_58.width() - 2 * margin;
        int span = BarcodeSymbol.encode(code).modules() + 2 * BarcodeSymbol.QUIET_ZONE;

        assertTrue(span * PageWriter.BAR_MODULE > line, "الكود فعلًا أعرض من اللفة بالعرض الافتراضي");
        assertEquals(code, page.decode());
    }

    /** الكود اللي يحتاج خطوطًا أضيق من 0.25 مم بيفشل بالكود وبالمقاس، بدل باركود ما بيتقريش. */
    @Test
    void codeTooLongForTheLineFailsInsteadOfPrintingUnreadableBars() {
        String code = "SHIPMENT-2026-ALEXANDRIA-0042";
        ReportException thrown = assertThrows(ReportException.class,
                () -> PdfDocument.rtl(Paper.ROLL_58).barcode(code).toBytes());
        assertTrue(thrown.getMessage().contains(code) && thrown.getMessage().contains("mm"), thrown.getMessage());
    }

    /**
     * الإيصال اللي آخره باركود: صفحة واحدة، والباركود وسطره فوق الهامش، ومفيش ورق فاضي كتير بعده. الإيصال
     * أطول من عرض اللفة: الأقصر منه بيتقص مربع بعرضها، فالورق الفاضي تحته مقصود.
     */
    @Test
    void receiptEndingInABarcodeStaysOnOnePage() throws Exception {
        for (Paper paper : new Paper[]{Paper.ROLL_80, Paper.ROLL_58}) {
            PdfTable parcels = PdfTable.create().column("الطرد", 3).column("الوزن", 1, Align.END);
            for (int i = 1; i <= 8; i++) {
                parcels.row("كرتونة " + i, "2.5");
            }
            PdfDocument receipt = PdfDocument.rtl(paper).letterhead("نقلات للشحن", "ت: 0100 000 0000")
                    .title("بوليصة شحن")
                    .field("المرسل إليه", "أحمد محمود")
                    .field("المدينة", "الإسكندرية")
                    .table(parcels)
                    .barcode("SHP-2026-0042");
            List<Page> pages = render(receipt.toBytes(), THERMAL, ImageType.BINARY);

            assertEquals(1, pages.size(), paper.name());
            Page page = pages.get(0);
            float margin = PdfStyle.forPaper(paper).margin();
            assertEquals("SHP-2026-0042", page.decode(), paper.name());
            assertTrue(page.lowest() <= page.height() - margin + 0.5f, paper.name() + ": " + page.lowest() + " من " + page.height());
            assertTrue(page.height() > paper.width(), paper.name() + ": الإيصال أطول من عرض اللفة");
            assertTrue(page.lowest() > page.height() - margin - 30, paper.name() + ": ورق فاضي كتير تحت الباركود: "
                    + page.lowest() + " من " + page.height());
        }
    }

    /** الباركود وسطره ما بيتقسموش: أيًا كان مكانه في آخر الصفحة بيترسم كله في صفحة واحدة. */
    @Test
    void barcodeIsNeverSplitAtThePageEnd() throws Exception {
        String code = "SHP-2026-0042";
        for (int rows = 14; rows <= 34; rows += 2) {
            PdfTable table = PdfTable.create().column("Day", 1);
            for (int i = 1; i <= rows; i++) {
                table.row("row" + i);
            }
            PdfDocument document = PdfDocument.ltr(Paper.A5).pageNumbers(PageNumbers.NONE).table(table).barcode(code, 20, Align.CENTER);
            List<Page> pages = render(document.toBytes(), 2f, ImageType.RGB);
            float margin = PdfStyle.forPaper(Paper.A5).margin();
            for (Page page : pages) {
                assertTrue(page.lowest() <= page.height() - margin + 0.5f, rows + " صف: " + page.lowest());
            }
            assertEquals(code, pages.get(pages.size() - 1).decode(), rows + " صف");
            assertTrue(text(document).contains(code), rows + " صف: السطر المكتوب موجود");
        }
    }

    @Test
    void isDrawnAsVectorsNotAnImage() throws Exception {
        try (PDDocument pdf = Loader.loadPDF(PdfDocument.ltr(Paper.A5).barcode("SHP-2026-0042").toBytes())) {
            assertFalse(pdf.getPage(0).getResources().getXObjectNames().iterator().hasNext());
        }
    }

    @Test
    void rejectsWhatCannotBeACode128() {
        PdfDocument document = PdfDocument.ltr(Paper.A4);
        assertThrows(IllegalArgumentException.class, () -> document.barcode(""));
        assertThrows(IllegalArgumentException.class, () -> document.barcode(null));
        IllegalArgumentException arabic = assertThrows(IllegalArgumentException.class, () -> document.barcode("شحنة-42"));
        assertTrue(arabic.getMessage().contains("U+0634"), arabic.getMessage());
        assertThrows(IllegalArgumentException.class, () -> document.barcode("café"));
        assertThrows(IllegalArgumentException.class, () -> document.barcode("a\tb"));
        assertThrows(IllegalArgumentException.class, () -> document.barcode("x", 0, Align.CENTER));
        assertThrows(IllegalArgumentException.class, () -> document.barcode("x", Float.NaN, Align.CENTER));
        assertTrue(document.blocks().isEmpty(), "الباركود المرفوض ما بيتضافش");
    }
}
