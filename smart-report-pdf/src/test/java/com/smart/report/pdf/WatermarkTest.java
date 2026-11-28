package com.smart.report.pdf;

import com.smart.report.text.ArabicTextShaper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
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
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.smart.report.pdf.Typesetter.BASELINE;
import static com.smart.report.pdf.Typesetter.LEADING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * العلامة المائية ({@link PdfDocument#watermark(String, int)}) مقروءة من الـ PDF نفسه: حروفها في كل صفحة، فوق
 * المحتوى وشفافة، متشكّلة، جوه الهوامش، ومن غير ما تحرّك حرف واحد من المحتوى ولا تسيب صفحة فاضية.
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

    /** نسخ العلامة في الصفحة: حروف كل نسخة، ومكانها في حروف الصفحة بترتيب الرسم ({@code from} لحد قبل {@code to}). */
    private record Stamp(List<List<Mark>> copies, int from, int to) {
    }

    /**
     * أول مكان في الصفحة فيه {@code copies} نسخ ورا بعض من العلامة؛ الاختبار بيفشل لو مش موجودة. المسافات بين
     * الحروف ما بتتحسبش (المسافة ممكن ما تطلعش حرف).
     */
    private static Stamp stamp(List<Mark> page, String visual, int copies) {
        for (int from = 0; from < page.size(); from++) {
            List<List<Mark>> found = new ArrayList<>();
            int k = from;
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
                if (!visual.contentEquals(text)) {
                    break;
                }
                found.add(one);
            }
            if (found.size() == copies) {
                return new Stamp(found, from, k);
            }
        }
        throw new AssertionError(copies + " copies of \"" + visual + "\" are not drawn on the page");
    }

    /** حروف الصفحة من غير العلامة. */
    private static List<Mark> without(List<Mark> page, Stamp stamp) {
        List<Mark> rest = new ArrayList<>(page.subList(0, stamp.from()));
        rest.addAll(page.subList(stamp.to(), page.size()));
        return rest.stream().filter(mark -> !mark.text().isBlank()).toList();
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
     * على كل ورقة وبكل كلمة: العلامة آخر حاجة في الصفحة (فوق كل حاجة)، متشكّلة، كل حرف فيها بارتفاع سطره جوه الهوامش، في نص
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
        Stamp stamp = stamp(page, visual, copies);
        List<List<Mark>> found = stamp.copies();
        assertEquals(page.size(), stamp.to(), "the watermark is drawn last, over everything on the page");

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

        // اللي قبل العلامة هو المستند من غيرها، حرف حرف في نفس المكان.
        List<Mark> body = without(page, stamp);
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
        List<Mark> page = onPage(marks(invoice(paper, true).watermark(word).toBytes()), 1);
        return stamp(page, visual(word, true), 1).copies().getFirst().getFirst().size();
    }

    /** العربي متشكّل (أشكال الحروف الموصولة) ومن اليمين لليسار: أول حرف مرسوم آخر حرف في الكلمة. */
    @Test
    void arabicIsShapedAndReadsRightToLeft() throws Exception {
        for (boolean rightToLeft : new boolean[] {true, false}) {
            List<Mark> page = onPage(marks(invoice(Paper.A4, rightToLeft).watermark("مسودة").toBytes()), 1);
            String visual = visual("مسودة", rightToLeft);
            List<Mark> stamp = stamp(page, visual, 1).copies().getFirst();
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

    /**
     * في كل صفحة من مستند طويل: العلامة فوق المحتوى (بعدها رقم الصفحة بس)، بشفافية 15% من {@code ExtGState}،
     * ومحطوطة {@code /Artifact}، ومن غير ما تزوّد صفحة.
     */
    @Test
    void everyPageHasTheWatermarkOverItsContent() throws Exception {
        byte[] pdf = longStatement(Paper.A5).watermark("صورة").toBytes();
        List<Mark> marks = marks(pdf);
        float margin = PdfStyle.forPaper(Paper.A5).margin();
        int pages;
        try (PDDocument document = Loader.loadPDF(pdf)) {
            pages = document.getNumberOfPages();
            assertTrue(pages >= 3, "a long statement: " + pages);
            for (int i = 0; i < pages; i++) {
                PDPage page = document.getPage(i);
                List<Object> tokens = new PDFStreamParser(page).parse();
                int gs = operator(tokens, "gs", 0);
                PDExtendedGraphicsState state = page.getResources().getExtGState((COSName) tokens.get(gs - 1));
                assertEquals(Watermark.ALPHA, state.getNonStrokingAlphaConstant(), 1e-6f, "the watermark's opacity");
                int artifact = operator(tokens, "BMC", gs);
                assertEquals(COSName.ARTIFACT, tokens.get(artifact - 1));
                assertTrue(operator(tokens, "BT", artifact) > artifact, "its text is marked as an artifact");
            }
        }
        for (int page = 1; page <= pages; page++) {
            List<Mark> onPage = onPage(marks, page);
            Stamp stamp = stamp(onPage, visual("صورة", true), 1);
            assertTrue(stamp.from() > 0, "the page's content comes first");
            for (Mark after : onPage.subList(stamp.to(), onPage.size())) {
                assertTrue(after.at(0, 0).y < margin, "only the page number after the watermark: " + after.text());
            }
        }
        try (PDDocument document = Loader.loadPDF(longStatement(Paper.A5).toBytes())) {
            assertEquals(pages, document.getNumberOfPages(), "the watermark adds no page");
        }
    }

    /** مكان أول أمر {@code name} من {@code from}، أو الاختبار بيفشل. */
    private static int operator(List<Object> tokens, String name, int from) {
        for (int i = from; i < tokens.size(); i++) {
            if (tokens.get(i) instanceof Operator op && name.equals(op.getName())) {
                return i;
            }
        }
        throw new AssertionError("no " + name + " operator after token " + from);
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
        stamp(onPage(marks(pdf), 1), "COPY", copies);
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
        stamp(onPage(marks, 1), "COPY", 1);
        assertEquals("Corner", firstWord(onPage(marks, 2)), "the second document has no watermark");
        stamp(onPage(marks, 3), visual("مدفوع", true), 1);
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
     * الرسم اللي المعاينة والطابعة بيستخدموه ({@link PdfPages}) بيطلّع العلامة فاتحة على الورق الأبيض: 15% من
     * اللون، يعني رمادي {@code 0xD9D9D9} للأسود ووردي للأحمر.
     */
    @Test
    void thePreviewRendersALightWatermark() throws Exception {
        BufferedImage stamped = page(PdfDocument.ltr(Paper.A4).title("Invoice").watermark("DRAFT").toBytes());
        BufferedImage plain = page(PdfDocument.ltr(Paper.A4).title("Invoice").toBytes());
        assertTrue(count(stamped, 0xD9D9D9) > 2000, "light grey watermark pixels: " + count(stamped, 0xD9D9D9));
        assertEquals(0, below(plain, 0xD9D9D9, plain.getHeight() / 4), "nothing grey under the title without it");
        assertEquals(count(plain, 0x000000), count(stamped, 0x000000), "the title is drawn the same, in black");

        BufferedImage red = page(PdfDocument.ltr(Paper.A4).watermark("VOID", 0xC00000).toBytes());
        int lightRed = blend(0xFFFFFF, 0xC00000);
        assertTrue(count(red, lightRed) > 2000, "light red: " + count(red, lightRed));
    }

    /**
     * الخلفيات (رأس الجدول، الإجمالي، صندوق الملخص) ما بتغطيش العلامة: فاتورة ملغاة على A5 (صغيرة، الملخص في نص
     * الصفحة، وطويلة، الصفوف تحت العلامة)، والعلامة بتصبغ البكسلات الرمادية اللي تحتها بالأحمر، والنص الأسود تحتها
     * لسه غامق.
     */
    @ParameterizedTest(name = "{0} rows")
    @org.junit.jupiter.params.provider.ValueSource(ints = {8, 22})
    void theWatermarkShowsOverFilledAreasAndKeepsTextReadable(int rows) throws Exception {
        PdfTable table = PdfTable.create().column("الصنف", 3).column("الكمية", 1).column("القيمة", 2, Align.END);
        for (int i = 1; i <= rows; i++) {
            table.row("صنف " + i, "1", "50.00");
        }
        table.totals("الإجمالي", String.valueOf(rows), "400.00");
        Supplier<PdfDocument> invoice = () -> PdfDocument.rtl(Paper.A5).title("فاتورة")
                .field("رقم", "0042").field("التاريخ", "2026-11-28")
                .table(table).summaryTotal("الإجمالي", "450.00 ج.م");
        BufferedImage plain = page(invoice.get().toBytes(), 3f);
        BufferedImage stamped = page(invoice.get().watermark("ملغاة", 0xC00000).toBytes(), 3f);

        int tinted = 0;
        int dark = 0;
        int darkStillDark = 0;
        for (int y = 0; y < plain.getHeight(); y++) {
            for (int x = 0; x < plain.getWidth(); x++) {
                int before = plain.getRGB(x, y) & 0xFFFFFF;
                int after = stamped.getRGB(x, y) & 0xFFFFFF;
                if (before == after) {
                    continue;
                }
                int r = (before >> 16) & 0xFF;
                int g = (before >> 8) & 0xFF;
                int b = before & 0xFF;
                if (r > 200 && r < 245 && Math.abs(r - g) < 12 && Math.abs(g - b) < 12) {
                    // خلفية رمادية فاتحة تحت العلامة كلها (مش حرف بيلمس البكسل بطرفه): 15% أحمر فوق الرمادي.
                    if (near(after, blend(before, 0xC00000))) {
                        tinted++;
                    }
                } else if (r < 80 && g < 80 && b < 80) {
                    dark++;
                    if (((after >> 8) & 0xFF) < 100) {
                        darkStillDark++;
                    }
                }
            }
        }
        assertEquals(dark, darkStillDark, "text under the watermark stays dark");
        if (rows == 8) {
            // الإجمالي وصندوق الملخص في نص الصفحة، تحت أول حرف في العلامة.
            assertTrue(tinted > 4000, "filled pixels tinted red by the watermark: " + tinted);
        } else {
            // الصفوف الكتير بتعدّي تحت العلامة: فيه نص تحتها فعلًا.
            assertTrue(dark > 20, "text under the watermark: " + dark);
        }
    }

    /** {@code color} بعتامة {@link Watermark#ALPHA} فوق {@code under}. */
    private static int blend(int under, int color) {
        int rgb = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            float channel = ((under >> shift) & 0xFF) * (1 - Watermark.ALPHA) + ((color >> shift) & 0xFF) * Watermark.ALPHA;
            rgb |= Math.round(channel) << shift;
        }
        return rgb;
    }

    private static boolean near(int pixel, int rgb) {
        for (int shift = 16; shift >= 0; shift -= 8) {
            if (Math.abs(((pixel >> shift) & 0xFF) - ((rgb >> shift) & 0xFF)) > 2) {
                return false;
            }
        }
        return true;
    }

    private static BufferedImage page(byte[] pdf) throws Exception {
        return page(pdf, 1f);
    }

    private static BufferedImage page(byte[] pdf, float scale) throws Exception {
        try (PdfPages pages = PdfPages.open(pdf)) {
            return pages.render(0, scale);
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
                if (near(image.getRGB(x, y), rgb)) {
                    found++;
                }
            }
        }
        return found;
    }
}
