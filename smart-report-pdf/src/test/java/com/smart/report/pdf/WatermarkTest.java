package com.smart.report.pdf;

import com.smart.report.text.ArabicTextShaper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.smart.report.pdf.Typesetter.BASELINE;
import static com.smart.report.pdf.Typesetter.LEADING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * العلامة المائية ({@link PdfDocument#watermark(String, int)}) مقروءة من الـ PDF نفسه: حروفها في كل صفحة، أول حاجة
 * في الصفحة، متشكّلة، جوه الهوامش، ومن غير ما تحرّك حرف واحد من المحتوى ولا تسيب صفحة فاضية.
 */
class WatermarkTest {

    private static final String LONG = "نسخة طبق الأصل غير صالحة للاستخدام";

    /** حرف مرسوم بمصفوفة رسمه (فيها الحجم والميل)، وعرضه بوحدة الحجم. */
    private record Mark(int page, String text, Matrix matrix, float advance) {

        Point2D.Float at(float along, float up) {
            return matrix.transformPoint(along, up);
        }

        float size() {
            return (float) Math.hypot(matrix.getValue(0, 0), matrix.getValue(0, 1));
        }

        double angle() {
            return Math.atan2(matrix.getValue(0, 1), matrix.getValue(0, 0));
        }
    }

    private static List<Mark> marks(byte[] pdf) throws IOException {
        List<Mark> marks = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void processTextPosition(TextPosition text) {
                    float advance = 0;
                    try {
                        for (int code : text.getCharacterCodes()) {
                            advance += text.getFont().getWidth(code) / 1000f;
                        }
                    } catch (IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                    marks.add(new Mark(getCurrentPageNo(), text.getUnicode(), text.getTextMatrix(), advance));
                    super.processTextPosition(text);
                }
            };
            stripper.getText(document);
        }
        return marks;
    }

    private static List<Mark> onPage(List<Mark> marks, int page) {
        return marks.stream().filter(mark -> mark.page() == page).toList();
    }

    /** العلامة زي ما هتترسم: متشكّلة وبترتيب الرسم، من غير مسافات (المسافة ممكن ما تطلعش حرف). */
    private static String visual(String word, boolean rightToLeft) {
        return ArabicTextShaper.forDisplay(word, word, rightToLeft).replace(" ", "");
    }

    /**
     * أول حروف الصفحة لحد ما تكمّل {@code copies} نسخ من العلامة؛ الاختبار بيفشل لو أول الصفحة مش العلامة. بترجع
     * حروف النسخ دي، وكل نسخة لوحدها.
     */
    private static List<List<Mark>> leadingCopies(List<Mark> page, String visual, int copies) {
        List<List<Mark>> found = new ArrayList<>();
        int k = 0;
        for (int copy = 0; copy < copies; copy++) {
            List<Mark> one = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            while (text.length() < visual.length() && k < page.size()) {
                Mark mark = page.get(k++);
                if (mark.text().isBlank()) {
                    continue;
                }
                text.append(mark.text());
                one.add(mark);
            }
            assertEquals(visual, text.toString(), "copy " + (copy + 1) + " of the watermark opens the page");
            found.add(one);
        }
        return found;
    }

    private static int copiesOnRoll(PDRectangle page) {
        return Math.max(1, (int) (page.getHeight() / (Watermark.ROLL_REPEAT * page.getWidth())));
    }

    private static PdfDocument invoice(Paper paper, boolean rightToLeft) {
        PdfDocument document = rightToLeft ? PdfDocument.rtl(paper) : PdfDocument.ltr(paper);
        return document.letterhead("Corner Shop", "Main St 12").title("Invoice").field("No", "0042")
                .table(t -> t.column("Item", 3).column("Qty", 1).column("Amount", 2, Align.END)
                        .row("Tea", "2", "10.00").row("Sugar", "1", "5.00").totals("Total", "3", "15.00"))
                .summaryTotal("Due", "15.00");
    }

    private static PdfDocument longStatement(Paper paper) {
        PdfTable table = PdfTable.create().column("Day", 1).column("Amount", 1);
        for (int day = 1; day <= 120; day++) {
            table.row("row" + day, "5");
        }
        return PdfDocument.rtl(paper).title("كشف حساب").table(table.totals("Total", "600"));
    }

    static Stream<Arguments> papersAndWords() {
        List<Arguments> cases = new ArrayList<>();
        Paper[] papers = {Paper.A4, Paper.A5, Paper.A4.landscape(), Paper.A5.landscape(), Paper.ROLL_80,
                Paper.ROLL_58};
        for (Paper paper : papers) {
            for (String word : new String[] {"مسودة", "DRAFT", "COPY", LONG}) {
                cases.add(Arguments.of(paper, word));
            }
        }
        return cases.stream();
    }

    /**
     * على كل ورقة وبكل كلمة: العلامة أول حاجة في الصفحة، متشكّلة، كل حرف فيها بارتفاع سطره جوه الهوامش، في نص
     * الصفحة، على القطر في الورق وبالعرض على اللفة — والمحتوى بعدها في مكانه بالظبط زي من غيرها.
     */
    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("papersAndWords")
    void theWatermarkFitsInsideTheMarginsAtTheMiddleOfThePage(Paper paper, String word) throws Exception {
        byte[] plain = invoice(paper, true).toBytes();
        byte[] stamped = invoice(paper, true).watermark(word).toBytes();
        List<Mark> marks = marks(stamped);
        float margin = PdfStyle.forPaper(paper).margin();
        PDRectangle box;
        try (PDDocument document = Loader.loadPDF(stamped)) {
            assertEquals(1, document.getNumberOfPages());
            box = document.getPage(0).getMediaBox();
        }
        int copies = paper.roll() ? copiesOnRoll(box) : 1;
        String visual = visual(word, true);
        List<Mark> page = onPage(marks, 1);
        List<List<Mark>> found = leadingCopies(page, visual, copies);

        double expectedAngle = paper.roll() ? 0 : Math.atan2(box.getHeight(), box.getWidth());
        float size = found.getFirst().getFirst().size();
        assertTrue(size <= Watermark.MAX_SHARE * Math.min(box.getWidth(), box.getHeight()) + 0.01f, "size " + size);
        for (int copy = 0; copy < copies; copy++) {
            List<Mark> one = found.get(copy);
            for (Mark mark : one) {
                assertEquals(expectedAngle, mark.angle(), 1e-3, "angle");
                assertEquals(size, mark.size(), 0.01f, "one size for the whole watermark");
                for (float along : new float[] {0, mark.advance()}) {
                    for (float up : new float[] {-(LEADING - BASELINE), BASELINE}) {
                        Point2D.Float corner = mark.at(along, up);
                        assertTrue(corner.x >= margin - 0.5f && corner.x <= box.getWidth() - margin + 0.5f,
                                word + " leaves the margins at x " + corner.x);
                        assertTrue(corner.y >= margin - 0.5f && corner.y <= box.getHeight() - margin + 0.5f,
                                word + " leaves the margins at y " + corner.y);
                    }
                }
            }
            // نص الكلمة على خط كتابتها، وفوقه بنص ارتفاع السطر: نص الصفحة (أو نص مكان النسخة على اللفة).
            Mark first = one.getFirst();
            Mark last = one.getLast();
            Point2D.Float start = first.at(0, 0);
            Point2D.Float end = last.at(last.advance(), 0);
            double lift = (BASELINE - LEADING / 2) * size;
            double centreX = (start.x + end.x) / 2 - lift * Math.sin(expectedAngle);
            double centreY = (start.y + end.y) / 2 + lift * Math.cos(expectedAngle);
            assertEquals(box.getWidth() / 2, centreX, 1, "horizontal centre");
            float expectedY = box.getHeight() - box.getHeight() * (copy + 0.5f) / copies;
            assertEquals(expectedY, centreY, 1, "vertical centre");
        }

        // اللي بعد العلامة هو المستند من غيرها، حرف حرف في نفس المكان.
        Mark lastStamp = found.getLast().getLast();
        int afterStamp = 0;
        while (page.get(afterStamp) != lastStamp) {
            afterStamp++;
        }
        List<Mark> body = page.subList(afterStamp + 1, page.size()).stream()
                .filter(mark -> !mark.text().isBlank()).toList();
        List<Mark> alone = onPage(marks(plain), 1).stream().filter(mark -> !mark.text().isBlank()).toList();
        assertEquals(alone.size(), body.size(), "the body has the same glyphs");
        for (int i = 0; i < alone.size(); i++) {
            assertEquals(alone.get(i).text(), body.get(i).text());
            Point2D.Float was = alone.get(i).at(0, 0);
            Point2D.Float is = body.get(i).at(0, 0);
            assertEquals(was.x, is.x, 0.01f, "glyph " + i + " x");
            assertEquals(was.y, is.y, 0.01f, "glyph " + i + " y");
        }
    }

    /** الكلمة الطويلة بتصغر على قد الورقة، والورقة الأصغر بتصغّر العلامة. */
    @Test
    void longWordsAndSmallPaperShrinkTheWatermark() throws Exception {
        float shortOnA4 = sizeOf(Paper.A4, "مسودة");
        float longOnA4 = sizeOf(Paper.A4, LONG);
        float shortOnA5 = sizeOf(Paper.A5, "مسودة");
        assertTrue(longOnA4 < shortOnA4, longOnA4 + " < " + shortOnA4);
        assertTrue(shortOnA5 < shortOnA4, shortOnA5 + " < " + shortOnA4);
        assertTrue(sizeOf(Paper.ROLL_58, "مسودة") < sizeOf(Paper.ROLL_80, "مسودة"));
    }

    private static float sizeOf(Paper paper, String word) throws Exception {
        return marks(invoice(paper, true).watermark(word).toBytes()).getFirst().size();
    }

    /** العربي متشكّل (أشكال الحروف الموصولة) ومن اليمين لليسار: أول حرف مرسوم آخر حرف في الكلمة. */
    @Test
    void arabicIsShapedAndReadsRightToLeft() throws Exception {
        for (boolean rightToLeft : new boolean[] {true, false}) {
            List<Mark> page = onPage(marks(invoice(Paper.A4, rightToLeft).watermark("مسودة").toBytes()), 1);
            String visual = visual("مسودة", rightToLeft);
            List<Mark> stamp = leadingCopies(page, visual, 1).getFirst();
            assertTrue(visual.chars().allMatch(c -> c >= 0xFE70 && c <= 0xFEFF), "presentation forms: " + visual);
            assertEquals("ﺓ", stamp.getFirst().text(), "the last letter, alone, is drawn first (leftmost)");
            assertEquals("ﻣ", stamp.getLast().text(), "the first letter, initial, is drawn last (rightmost)");
            // الحروف ورا بعض على اتجاه السطر.
            double angle = stamp.getFirst().angle();
            double previous = Double.NEGATIVE_INFINITY;
            for (Mark mark : stamp) {
                Point2D.Float at = mark.at(0, 0);
                double along = at.x * Math.cos(angle) + at.y * Math.sin(angle);
                assertTrue(along > previous, "glyphs follow each other along the line");
                previous = along;
            }
        }
    }

    /** في كل صفحة من مستند طويل، أول حاجة في الصفحة، وأول أمر في محتواها. */
    @Test
    void everyPageOpensWithTheWatermark() throws Exception {
        byte[] pdf = longStatement(Paper.A5).letterheadOnEveryPage(false).watermark("صورة").toBytes();
        List<Mark> marks = marks(pdf);
        int pages;
        try (PDDocument document = Loader.loadPDF(pdf)) {
            pages = document.getNumberOfPages();
            assertTrue(pages >= 3, "a long statement: " + pages);
            for (int i = 0; i < pages; i++) {
                List<Object> tokens = new PDFStreamParser(document.getPage(i)).parse();
                Operator first = (Operator) tokens.stream().filter(Operator.class::isInstance).findFirst().orElseThrow();
                assertEquals("q", first.getName(), "page " + (i + 1) + " starts with the watermark's own state");
                assertTrue(tokens.stream().anyMatch(t -> t instanceof Operator op && "BT".equals(op.getName())));
            }
        }
        for (int page = 1; page <= pages; page++) {
            leadingCopies(onPage(marks, page), visual("صورة", true), 1);
        }
        try (PDDocument document = Loader.loadPDF(longStatement(Paper.A5).toBytes())) {
            assertEquals(pages, document.getNumberOfPages(), "the watermark adds no page");
        }
    }

    /** على اللفة الطويلة نسخ بطول الإيصال، والقصيرة نسخة واحدة. */
    @Test
    void aLongReceiptRepeatsTheWatermarkDownTheRoll() throws Exception {
        PdfTable items = PdfTable.create().column("Item", 2).column("Amount", 1, Align.END);
        for (int i = 1; i <= 80; i++) {
            items.row("item " + i, "1.00");
        }
        byte[] pdf = PdfDocument.ltr(Paper.ROLL_80).title("Receipt").table(items).watermark("COPY").toBytes();
        PDRectangle box;
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertEquals(1, document.getNumberOfPages());
            box = document.getPage(0).getMediaBox();
        }
        int copies = copiesOnRoll(box);
        assertTrue(copies >= 3, "copies on a long receipt: " + copies);
        leadingCopies(onPage(marks(pdf), 1), "COPY", copies);
        // نفس طول الإيصال من غير العلامة: العلامة ما بتطوّلش اللفة.
        try (PDDocument document = Loader.loadPDF(PdfDocument.ltr(Paper.ROLL_80).title("Receipt").table(items)
                .toBytes())) {
            assertEquals(box.getHeight(), document.getPage(0).getMediaBox().getHeight(), 0.01f);
        }
    }

    /** الصفحة اللي فيها العلامة بس فاضية: الفاصل في الآخر ما بيسيبش صفحة عشانها. */
    @Test
    void aPageWithOnlyTheWatermarkIsStillEmpty() throws Exception {
        PdfDocument document = PdfDocument.rtl(Paper.A5).watermark("مسودة").title("كشف").text("first part")
                .pageBreak().space(12).text("").pageBreak();
        byte[] pdf = document.footer("نهاية الكشف").toBytes();
        try (PDDocument loaded = Loader.loadPDF(pdf)) {
            assertEquals(1, loaded.getNumberOfPages(), "no page kept for the watermark alone");
        }
        Glyphs glyphs = Glyphs.of(pdf);
        assertTrue(glyphs.drawn("first", 1));

        // والفاصل في أول المستند (الصفحة فيها العلامة بس) ما بيفتحش صفحة.
        byte[] leading = PdfDocument.ltr(Paper.A5).watermark("DRAFT").pageBreak().title("Statement").toBytes();
        try (PDDocument loaded = Loader.loadPDF(leading)) {
            assertEquals(1, loaded.getNumberOfPages());
        }
    }

    /** من غيرها، أو بنص فاضي أو {@code null}: المستند زي ما كان بالظبط. */
    @Test
    void noWatermarkWithoutText() throws Exception {
        String plain = LayoutSnapshot.full(invoice(Paper.A4, true).toBytes());
        assertEquals(plain, LayoutSnapshot.full(invoice(Paper.A4, true).watermark(null).toBytes()));
        assertEquals(plain, LayoutSnapshot.full(invoice(Paper.A4, true).watermark(" \n\t ").toBytes()));
        assertEquals(plain, LayoutSnapshot.full(invoice(Paper.A4, true).watermark("DRAFT").watermark("").toBytes()));
        String roll = LayoutSnapshot.full(invoice(Paper.ROLL_80, true).toBytes());
        assertEquals(roll, LayoutSnapshot.full(invoice(Paper.ROLL_80, true).watermark("").toBytes()));
    }

    /** في الدفعة كل مستند بعلامته، واللي من غير علامة من غيرها. */
    @Test
    void eachDocumentInABatchKeepsItsOwnWatermark() throws Exception {
        byte[] pdf = PdfBatch.of(invoice(Paper.A5, false).watermark("COPY"), invoice(Paper.A5, false),
                invoice(Paper.A5, true).watermark("مدفوع", 0x008000)).toBytes();
        List<Mark> marks = marks(pdf);
        leadingCopies(onPage(marks, 1), "COPY", 1);
        assertEquals("Corner", firstWord(onPage(marks, 2)), "the second document has no watermark");
        leadingCopies(onPage(marks, 3), visual("مدفوع", true), 1);
        assertFalse(onPage(marks, 2).stream().anyMatch(mark -> mark.size() > 30), "nothing large on page 2");
    }

    private static String firstWord(List<Mark> page) {
        StringBuilder word = new StringBuilder();
        for (Mark mark : page) {
            if (mark.text().isBlank()) {
                break;
            }
            word.append(mark.text());
        }
        return word.toString();
    }

    /**
     * الرسم اللي المعاينة والطابعة بيستخدموه ({@link PdfPages}) بيطلّع العلامة رمادي فاتح ورا النص: لونها 15% من
     * اللون المطلوب، والنص الأسود فوقها لسه أسود.
     */
    @Test
    void thePreviewRendersALightWatermarkBehindTheText() throws Exception {
        assertEquals(0xD9D9D9, Watermark.tint(0x000000));
        assertEquals(0xFFD9D9, Watermark.tint(0xFF0000));
        assertEquals(0xFFFFFF, Watermark.tint(0xFFFFFF));

        BufferedImage stamped = page(PdfDocument.ltr(Paper.A4).title("Invoice").watermark("DRAFT").toBytes());
        BufferedImage plain = page(PdfDocument.ltr(Paper.A4).title("Invoice").toBytes());
        assertTrue(count(stamped, 0xD9D9D9) > 2000, "light grey watermark pixels: " + count(stamped, 0xD9D9D9));
        assertEquals(0, below(plain, 0xD9D9D9, plain.getHeight() / 4), "nothing grey under the title without it");
        assertEquals(count(plain, 0x000000), count(stamped, 0x000000), "the title is drawn the same, in black");

        BufferedImage red = page(PdfDocument.ltr(Paper.A4).watermark("VOID", 0xC00000).toBytes());
        assertTrue(count(red, Watermark.tint(0xC00000)) > 2000, "light red");
    }

    private static BufferedImage page(byte[] pdf) throws Exception {
        try (PdfPages pages = PdfPages.open(pdf)) {
            return pages.render(0, 1f);
        }
    }

    private static int count(BufferedImage image, int rgb) {
        return below(image, rgb, 0);
    }

    /** بكسلات قريبة من اللون (فرق 2 في كل قناة للتنعيم) من الصف {@code top} لتحت. */
    private static int below(BufferedImage image, int rgb, int top) {
        int found = 0;
        for (int y = top; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y);
                if (Math.abs(((pixel >> 16) & 0xFF) - ((rgb >> 16) & 0xFF)) <= 2
                        && Math.abs(((pixel >> 8) & 0xFF) - ((rgb >> 8) & 0xFF)) <= 2
                        && Math.abs((pixel & 0xFF) - (rgb & 0xFF)) <= 2) {
                    found++;
                }
            }
        }
        return found;
    }
}
