package com.smart.report.pdf;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.oned.Code128Reader;
import com.google.zxing.oned.EAN13Reader;
import com.smart.report.ReportException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ean13AndLabelsTest {

    private static final float MM = 72f / 25.4f;

    private static BufferedImage render(byte[] pdf, int page, int dpi) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFRenderer(document).renderImageWithDPI(page, dpi, ImageType.RGB);
        }
    }

    private static int pages(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        }
    }

    private static float[] pageSize(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new float[]{document.getPage(0).getMediaBox().getWidth(), document.getPage(0).getMediaBox().getHeight()};
        }
    }

    private static BinaryBitmap bitmap(BufferedImage image) {
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        return new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels)));
    }

    private static Result ean(BufferedImage image) throws Exception {
        return new EAN13Reader().decode(bitmap(image), Map.of(DecodeHintType.TRY_HARDER, true));
    }

    /** خانة الملصق من صورة الصفحة: {@code left} و {@code top} بالنقطة من أعلى يسار الصفحة. */
    private static BufferedImage cell(BufferedImage page, int dpi, float left, float top, float width, float height) {
        float scale = dpi / 72f;
        int x = Math.max(0, Math.round(left * scale));
        int y = Math.max(0, Math.round(top * scale));
        int w = Math.min(page.getWidth() - x, Math.round(width * scale));
        int h = Math.min(page.getHeight() - y, Math.round(height * scale));
        return page.getSubimage(x, y, w, h);
    }

    // ===================== EAN-13 في المستند =====================

    @Test
    void ean13InADocumentScans() throws Exception {
        byte[] pdf = PdfDocument.rtl(Paper.A5).title("صنف").ean13("622100012345").toBytes();
        Result result = ean(render(pdf, 0, 300));
        assertEquals(BarcodeFormat.EAN_13, result.getBarcodeFormat());
        assertEquals("6221000123457".substring(0, 12), result.getText().substring(0, 12));
        assertEquals("622100012345" + com.smart.report.util.DocumentCode.checkDigit("622100012345"), result.getText());

        // الأرقام مكتوبة تحت الخطوط: الأول، والستة، والستة.
        Glyphs glyphs = Glyphs.of(pdf);
        String digits = result.getText();
        assertTrue(glyphs.drawn(digits.substring(1, 7), 1));
        assertTrue(glyphs.drawn(digits.substring(7), 1));
    }

    @Test
    void thirteenDigitsAreCheckedAndScanTheSame() throws Exception {
        assertEquals("4006381333931", ean(render(PdfDocument.ltr(Paper.ROLL_80).ean13("4006381333931").toBytes(),
                0, 300)).getText());
        IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                () -> PdfDocument.ltr(Paper.A4).ean13("4006381333932"));
        assertTrue(wrong.getMessage().contains("should be 1"), wrong.getMessage());
        assertThrows(IllegalArgumentException.class, () -> PdfDocument.ltr(Paper.A4).ean13("40063813339"));
        assertThrows(IllegalArgumentException.class, () -> PdfDocument.ltr(Paper.A4).ean13("ABC006381333"));
        assertThrows(IllegalArgumentException.class, () -> PdfDocument.ltr(Paper.A4).ean13("٤٠٠٦٣٨١٣٣٣٩٣١"));
        assertThrows(IllegalArgumentException.class, () -> PdfDocument.ltr(Paper.A4).ean13("4006381333931", 0, null));
    }

    /** مع دقة الطابعة: كل خط وفراغ مضاعف صحيح للمربع، بالبكسل. */
    @Test
    void ean13InWholePrinterDots() throws Exception {
        byte[] pdf = PdfDocument.rtl(Paper.ROLL_80).printerDpi(203).ean13("4006381333931").toBytes();
        BufferedImage image = render(pdf, 0, 203);
        int module = Math.round(0.33f / 25.4f * 203);
        // صف قريب من فوق الخطوط: فوق الأرقام المكتوبة تحتها.
        int top = 0;
        while (top < image.getHeight() && runs(image, top).isEmpty()) {
            top++;
        }
        List<Integer> best = runs(image, top + 4);
        assertEquals(59, best.size(), "30 خط و29 فراغ في EAN-13");
        for (int run : best) {
            assertEquals(0, run % module, best.toString());
        }
        assertEquals(95 * module, best.stream().mapToInt(Integer::intValue).sum());
        assertEquals("4006381333931", ean(image).getText());
    }

    private static List<Integer> runs(BufferedImage image, int y) {
        boolean[] dark = new boolean[image.getWidth()];
        for (int x = 0; x < dark.length; x++) {
            dark[x] = (image.getRGB(x, y) & 0xFF) < 128;
        }
        int first = 0;
        while (first < dark.length && !dark[first]) {
            first++;
        }
        int last = dark.length - 1;
        while (last >= 0 && !dark[last]) {
            last--;
        }
        List<Integer> runs = new ArrayList<>();
        int x = first;
        while (x <= last) {
            int start = x;
            boolean colour = dark[x];
            while (x <= last && dark[x] == colour) {
                x++;
            }
            runs.add(x - start);
        }
        return runs;
    }

    // ===================== الملصقات =====================

    private static PdfLabels.Item pepsi() {
        return PdfLabels.Item.of("Pepsi 1L", "15.00", "4006381333931");
    }

    @Test
    void rollOfTwoAcrossMakesAPagePerRow() throws Exception {
        PdfLabels labels = PdfLabels.roll(40, 25).across(2, 2).rightToLeft(false);
        labels.add(pepsi(), 3).add(PdfLabels.Item.of("Tea 100g", "22.50", "TEA-100"), 2);
        assertEquals(5, labels.count());
        byte[] pdf = labels.toBytes();
        assertEquals(3, pages(pdf));
        float[] size = pageSize(pdf);
        assertEquals(82 * MM, size[0], 0.2f);
        assertEquals(25 * MM, size[1], 0.2f);
        assertEquals(82 * MM, labels.paper().width(), 0.2f);

        // الصفحة الأولى: ملصقين، الشمال والتاني بعد 40 + 2 مم.
        Glyphs glyphs = Glyphs.of(pdf);
        float firstName = glyphs.first("Pepsi", 1).x();
        assertTrue(firstName < 40 * MM, "الأول في الخانة الشمال");
        BufferedImage page = render(pdf, 0, 300);
        assertEquals("4006381333931", ean(cell(page, 300, 0, 0, 40 * MM, 25 * MM)).getText());
        assertEquals("4006381333931", ean(cell(page, 300, 42 * MM, 0, 40 * MM, 25 * MM)).getText());
        // الصفحة التالتة: ملصق واحد بـ Code 128.
        BufferedImage third = render(pdf, 2, 300);
        Result tea = new Code128Reader().decode(bitmap(cell(third, 300, 0, 0, 40 * MM, 25 * MM)),
                Map.of(DecodeHintType.TRY_HARDER, true));
        assertEquals("TEA-100", tea.getText());
        assertFalse(glyphs.drawn("Tea", 3) && glyphs.first("Tea", 3).x() > 40 * MM, "الخانة التانية فاضية");
    }

    /** ورقة A4 بـ 3 أعمدة و8 صفوف: 24 في الصفحة، وكل ملصق في خانته. */
    @Test
    void aSheetPlacesEveryLabelInItsCell() throws Exception {
        PdfLabels labels = PdfLabels.sheet(Paper.A4, 3, 8).margins(8.5f, 0).rightToLeft(false);
        for (int i = 1; i <= 30; i++) {
            labels.add(PdfLabels.Item.of("Item" + i, i + ".00", "CODE-" + i));
        }
        byte[] pdf = labels.toBytes();
        assertEquals(2, pages(pdf));
        float width = 210 * MM / 3;
        float height = (297 - 17) * MM / 8;
        Glyphs glyphs = Glyphs.of(pdf);
        for (int i = 1; i <= 30; i++) {
            int slot = (i - 1) % 24;
            int page = (i - 1) / 24 + 1;
            Glyphs.Glyph name = glyphs.first("Item" + i, page);
            float cellLeft = (slot % 3) * width;
            float cellTop = 8.5f * MM + (slot / 3) * height;
            assertTrue(name.x() > cellLeft && name.right() < cellLeft + width, "Item" + i + " column");
            assertTrue(name.y() > cellTop && name.y() < cellTop + height, "Item" + i + " row");
        }
        BufferedImage first = render(pdf, 0, 300);
        Result last = new Code128Reader().decode(bitmap(cell(first, 300, 2 * width, 8.5f * MM + 7 * height, width,
                height)), Map.of(DecodeHintType.TRY_HARDER, true));
        assertEquals("CODE-24", last.getText());
    }

    @Test
    void theOffsetMovesEverything() throws Exception {
        byte[] plain = PdfLabels.roll(50, 30).rightToLeft(false).add(pepsi()).toBytes();
        byte[] moved = PdfLabels.roll(50, 30).rightToLeft(false).offset(1.5f, -1f).add(pepsi()).toBytes();
        Glyphs.Glyph before = Glyphs.of(plain).first("Pepsi");
        Glyphs.Glyph after = Glyphs.of(moved).first("Pepsi");
        assertEquals(1.5f * MM, after.x() - before.x(), 0.05f);
        assertEquals(-1f * MM, after.y() - before.y(), 0.05f);
    }

    @Test
    void aLongNameIsCutShrunkOrHidden() throws Exception {
        String name = "Extra long product name that cannot fit on a small label";
        PdfLabels.Item item = PdfLabels.Item.of(name, "9.99", "4006381333931");
        String cut = new org.apache.pdfbox.text.PDFTextStripper().getText(Loader.loadPDF(
                PdfLabels.roll(40, 25).rightToLeft(false).add(item).toBytes()));
        assertTrue(cut.contains("…"), cut);
        assertFalse(cut.contains("small label"), cut);

        String hidden = new org.apache.pdfbox.text.PDFTextStripper().getText(Loader.loadPDF(
                PdfLabels.roll(40, 25).rightToLeft(false).longName(PdfLabels.LongName.HIDE).add(item).toBytes()));
        assertFalse(hidden.contains("Extra"), hidden);
        assertTrue(hidden.contains("9.99"));

        // أطول من السطر بالخط العادي، وبيكفّي بأصغر خط.
        PdfLabels.Item longish = PdfLabels.Item.of("Mineral water bottle 1.5 litre", "5.00", "4006381333931");
        String normal = new org.apache.pdfbox.text.PDFTextStripper().getText(Loader.loadPDF(
                PdfLabels.roll(40, 25).rightToLeft(false).add(longish).toBytes()));
        String shrunk = new org.apache.pdfbox.text.PDFTextStripper().getText(Loader.loadPDF(
                PdfLabels.roll(40, 25).rightToLeft(false).longName(PdfLabels.LongName.SHRINK).add(longish).toBytes()));
        assertTrue(normal.contains("…"), normal);
        assertTrue(shrunk.contains("Mineral water bottle 1.5 litre"), "صغر لحد ما كفّى: " + shrunk);
    }

    @Test
    void anOldPriceIsStruckThroughNextToThePrice() throws Exception {
        byte[] pdf = PdfLabels.roll(50, 30).rightToLeft(false)
                .add(pepsi().withOldPrice("17.50")).toBytes();
        Glyphs glyphs = Glyphs.of(pdf);
        assertTrue(glyphs.first("17.50").x() > glyphs.first("15.00").x(), "القديم بعد الجديد في الشمال لليمين");
        byte[] rtl = PdfLabels.roll(50, 30).rightToLeft(true).add(pepsi().withOldPrice("17.50")).toBytes();
        Glyphs arabic = Glyphs.of(rtl);
        assertTrue(arabic.first("17.50").x() < arabic.first("15.00").x(), "في العربي الجديد على اليمين");
    }

    @Test
    void switchesTurnPartsOff() throws Exception {
        byte[] pdf = PdfLabels.roll(40, 25).showName(false).showPrice(false).showDigits(false).add(pepsi()).toBytes();
        String text = new org.apache.pdfbox.text.PDFTextStripper().getText(Loader.loadPDF(pdf)).strip();
        assertEquals("", text);
        assertEquals("4006381333931", ean(render(pdf, 0, 300)).getText());
    }

    @Test
    void whatCannotBeALabelIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> PdfLabels.Item.of("x", "1", " "));
        assertThrows(IllegalArgumentException.class, () -> PdfLabels.Item.of("x", "1", null));
        assertThrows(IllegalArgumentException.class, () -> PdfLabels.Item.of("x", "1", "كود"));
        assertThrows(IllegalArgumentException.class, () -> PdfLabels.roll(10, 25));
        assertThrows(IllegalArgumentException.class, () -> PdfLabels.roll(40, 25).across(5, 2));
        assertThrows(IllegalStateException.class, () -> PdfLabels.roll(40, 25).margins(1, 1));
        assertThrows(IllegalStateException.class, () -> PdfLabels.sheet(Paper.A4, 3, 8).across(2, 1));
        assertThrows(IllegalArgumentException.class, () -> PdfLabels.sheet(Paper.ROLL_80, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> PdfLabels.roll(40, 25).offset(11, 0));
        assertThrows(ReportException.class, () -> PdfLabels.roll(40, 25).toBytes(), "مفيش ملصقات");
        assertThrows(ReportException.class, () -> PdfLabels.sheet(Paper.A4, 15, 30).add(pepsi()).toBytes(), "صغيرة");
        ReportException tooLong = assertThrows(ReportException.class, () -> PdfLabels.roll(20, 25)
                .add(PdfLabels.Item.of("x", "1", "4006381333931")).toBytes());
        assertTrue(tooLong.getMessage().contains("4006381333931"), tooLong.getMessage());
        assertThrows(ReportException.class, () -> PdfLabels.roll(40, 10)
                .add(pepsi()).toBytes(), "الاسم والسعر ما سابوش مكان للخطوط");
    }
}
