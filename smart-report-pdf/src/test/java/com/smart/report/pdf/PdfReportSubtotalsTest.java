package com.smart.report.pdf;

import com.smart.report.ColumnDefinition;
import com.smart.report.ReportException;
import com.smart.report.ReportRequest;
import com.smart.report.ReportService;
import com.smart.report.ReportType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** الإجمالي الفرعي تحت كل مجموعة في تقرير الـ PDF العام ({@link ReportRequest.Builder#groupBy}). */
class PdfReportSubtotalsTest {

    public record Line(String customer, String item, Object amount, Object qty, LocalDate date) {

        Line(String customer, String item, Object amount, Object qty) {
            this(customer, item, amount, qty, null);
        }
    }

    /** عميلين: الأول صفين، والتاني صف واحد (مجموعة من صف). */
    private static final List<Line> LINES = List.of(
            new Line("Ahmed", "alpha", new BigDecimal("1000.50"), 3),
            new Line("Ahmed", "beta", new BigDecimal("250.25"), 40),
            new Line("Sara", "gamma", new BigDecimal("99.00"), 7));

    private static final ColumnDefinition AMOUNT = ColumnDefinition.of("amount", "Amount")
            .withFormat("#,##0.00").withTotal();
    private static final ColumnDefinition QTY = ColumnDefinition.of("qty", "Qty").withTotal();

    private static ReportRequest.Builder<Line> request(String title, List<Line> rows, ColumnDefinition... columns) {
        return ReportRequest.<Line>builder().title(title).data(rows).columns(List.of(columns));
    }

    private static byte[] pdf(ReportRequest.Builder<Line> request) throws Exception {
        return ReportService.generate(ReportType.PDF, request.build());
    }

    private static String textOf(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static void requireUnicodeFont() {
        Assumptions.assumeTrue(PdfFontResolver.resolve().available(),
                "مفيش خط Unicode متاح على الجهاز ده؛ حدّد -D" + PdfFontResolver.FONT_PROPERTY);
    }

    /** نفس توحيد الحروف اللي في PdfReportTotalsTest: الاستخراج بيرجّع الحرف حسب جدول الخط. */
    private static String canonical(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            out.append(switch (c) {
                case 'ي', 'ی', 'ى', 'ے', 'ې' -> 'ي';
                case 'ه', 'ھ', 'ہ', 'ە' -> 'ه';
                case 'ا', 'أ', 'إ', 'آ', 'ٱ' -> 'ا';
                default -> c;
            });
        }
        return out.toString();
    }

    private static int count(String text, String part) {
        int found = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) {
            found++;
        }
        return found;
    }

    /**
     * التقرير الإنجليزي: تحت كل مجموعة صف بكلمتها من أول العمود الأول، وكل مجموع تحت أرقام عموده من آخرها.
     * المجموعة اللي صف واحد ليها إجماليها برضه، والإجمالي الكلي في الآخر على البيانات بس (مش مرتين).
     */
    @Test
    void subtotalsLineUpUnderTheirColumnsLeftToRight() throws Exception {
        Glyphs glyphs = Glyphs.of(pdf(request("Statement", LINES,
                ColumnDefinition.of("customer", "Customer"), ColumnDefinition.of("item", "Item"), AMOUNT, QTY)
                .groupBy("customer").subtotalLabel("Sub-")));

        Glyphs.Glyph ahmed = glyphs.first("Sub-Ahmed");
        assertEquals(glyphs.first("Ahmed").x(), ahmed.x(), 0.5f, "الكلمة من أول العمود الأول");
        assertEquals(ahmed.y(), glyphs.first("1,250.75").y(), 0.5f, "الكلمة والمجموع في سطر واحد");
        assertEquals(ahmed.y(), glyphs.first("43").y(), 0.5f);
        assertEquals(glyphs.last("250.25").right(), glyphs.last("1,250.75").right(), 0.5f, "المجموع تحت المبالغ");
        assertEquals(glyphs.last("40").right(), glyphs.last("43").right(), 0.5f, "عمود من غير نمط برضه");
        assertTrue(ahmed.y() > glyphs.first("beta").y() && ahmed.y() < glyphs.first("gamma").y(),
                "تحت آخر صف في المجموعة وقبل المجموعة الجاية");

        // مجموعة من صف واحد: إجماليها نفس رقمه، في السطر اللي تحته.
        Glyphs.Glyph sara = glyphs.first("Sub-Sara");
        List<Glyphs.Glyph> ninetyNine = glyphs.every("99.00");
        assertEquals(2, ninetyNine.size(), "في الصف وفي إجماليه");
        assertEquals(sara.y(), ninetyNine.get(1).y(), 0.5f);
        List<Float> lines = glyphs.lineYs(1);
        assertEquals(lines.indexOf(glyphs.first("gamma").y()) + 1, lines.indexOf(sara.y()), "على طول تحت صفه");

        // الإجمالي الكلي: مجموع البيانات بس، تحت آخر إجمالي فرعي.
        Glyphs.Glyph total = glyphs.first("Total");
        assertTrue(total.y() > sara.y(), "الإجمالي الكلي في الآخر");
        assertEquals(total.y(), glyphs.first("1,349.75").y(), 0.5f);
        assertEquals(total.y(), glyphs.every("50").getLast().y(), 0.5f, "آخر 50: الكمية (250.25 فيها 50 برضه)");
        assertEquals(1, glyphs.every("1,349.75").size());
        assertFalse(glyphs.drawn("2,699.50", 1), "الإجمالي ما عدّش الإجماليات الفرعية");
    }

    /**
     * التقرير العربي: العمود الأول على اليمين، فالكلمة من يمينه والمجموع تحت المبالغ من شمالها (نهاية السطر)،
     * وعلى يمين المجموع.
     */
    @Test
    void subtotalsLineUpUnderTheirColumnsRightToLeft() throws Exception {
        requireUnicodeFont();

        Glyphs glyphs = Glyphs.of(pdf(request("كشف حساب", LINES,
                ColumnDefinition.of("customer", "العميل"), ColumnDefinition.of("item", "الصنف"),
                ColumnDefinition.of("amount", "المبلغ").withFormat("#,##0.00").withTotal(),
                ColumnDefinition.of("qty", "الكمية").withTotal())
                .groupBy("customer").subtotalLabel("Sub-")));

        Glyphs.Glyph ahmed = glyphs.first("Sub-Ahmed");
        assertEquals(glyphs.last("Ahmed").right(), glyphs.last("Sub-Ahmed").right(), 0.5f, "الكلمة من أول العمود (يمينه)");
        assertEquals(glyphs.first("250.25").x(), glyphs.first("1,250.75").x(), 0.5f, "المجموع تحت المبالغ من شمالها");
        assertEquals(glyphs.first("40").x(), glyphs.first("43").x(), 0.5f);
        assertEquals(ahmed.y(), glyphs.first("1,250.75").y(), 0.5f);
        assertTrue(ahmed.y() > glyphs.first("beta").y() && ahmed.y() < glyphs.first("gamma").y());
        assertTrue(ahmed.x() > glyphs.last("1,250.75").right()
                && glyphs.first("1,250.75").x() > glyphs.last("43").right(), "الأعمدة من اليمين للشمال");

        Glyphs.Glyph sara = glyphs.first("Sub-Sara");
        assertEquals(sara.y(), glyphs.every("99.00").get(1).y(), 0.5f);
        assertTrue(glyphs.first("1,349.75").y() > sara.y(), "الإجمالي الكلي في الآخر");
    }

    /** الكلمة الافتراضية حسب الاتجاه: «Total: Ahmed»، و«إجمالى: أحمد» في التقرير العربي. */
    @Test
    void defaultLabelsFollowTheReportsDirection() throws Exception {
        String english = textOf(pdf(request("Statement", LINES,
                ColumnDefinition.of("customer", "Customer"), AMOUNT).groupBy("customer")));
        assertTrue(english.contains("Total: Ahmed") && english.contains("Total: Sara"), english);
        assertEquals(3, count(english, "Total"), "إجماليين فرعيين وإجمالي كلي:\n" + english);

        requireUnicodeFont();
        List<Line> arabicRows = List.of(
                new Line("أحمد", "a", 10, 1), new Line("أحمد", "b", 5, 1), new Line("سارة", "c", 7, 1));
        String arabic = canonical(textOf(pdf(request("كشف حساب", arabicRows,
                ColumnDefinition.of("customer", "العميل"),
                ColumnDefinition.of("amount", "المبلغ").withTotal()).groupBy("customer"))));
        // «الإجمالى» الكلي فيها «إجمالى» برضه: اتنين فرعيين وواحد كلي.
        assertEquals(3, count(arabic, canonical("إجمالى")), arabic);
        assertTrue(arabic.contains(canonical("الإجمالى")), arabic);
        assertTrue(arabic.contains("15") && arabic.contains("22"), arabic);
    }

    /**
     * القيمة الفاضية مجموعة زي أي قيمة: صفين فاضيين ورا بعض مجموعة واحدة، وكلمتها علامة بدل الاسم — من غيرها
     * كانت هتبقى «Total:» وتتلخبط مع الإجمالي الكلي.
     */
    @Test
    void anEmptyGroupValueIsAGroupOfItsOwn() throws Exception {
        String text = textOf(pdf(request("Statement", List.of(
                        new Line("Ahmed", "item-a", 10, 1), new Line(null, "item-b", 5, 1),
                        new Line(null, "item-c", 6, 1), new Line("Sara", "item-d", 7, 1),
                        new Line("Sara", "item-e", 8, 1)),
                ColumnDefinition.of("customer", "Customer"), ColumnDefinition.of("item", "Item"),
                ColumnDefinition.of("amount", "Amount").withTotal()).groupBy("customer")));

        assertTrue(text.contains("Total: Ahmed") && text.contains("Total: —") && text.contains("Total: Sara"), text);
        assertEquals(1, count(text, "Total: —"), "الصفين الفاضيين مجموعة واحدة:\n" + text);
        assertTrue(text.indexOf("item-c") < text.indexOf("Total: —")
                && text.indexOf("Total: —") < text.indexOf("item-d"), text);
        assertTrue(text.contains("11"), "مجموع الصفين الفاضيين:\n" + text);
        // آخر مجموعة: إجماليها بعد آخر صف فيها، مش بعد أولها.
        assertTrue(text.indexOf("Total: Sara") > text.indexOf("item-e") && text.contains("15"), text);
    }

    /** قيمة المجموعة في كلمتها بنمط عمودها، والخاصية مش لازم تكون عمود في التقرير. */
    @Test
    void groupValueIsShownAsInItsColumn() throws Exception {
        LocalDate day = LocalDate.of(2026, 1, 5);
        List<Line> rows = List.of(new Line("Ahmed", "a", 10, 1, day), new Line("Ahmed", "b", 5, 1, day),
                new Line("Sara", "c", 7, 1, day.plusDays(1)));

        String byDate = textOf(pdf(request("Statement", rows,
                ColumnDefinition.of("date", "Date").withFormat("dd/MM/yyyy"),
                ColumnDefinition.of("amount", "Amount").withTotal()).groupBy("date")));
        assertTrue(byDate.contains("Total: 05/01/2026") && byDate.contains("Total: 06/01/2026"), byDate);

        String hidden = textOf(pdf(request("Statement", rows,
                ColumnDefinition.of("item", "Item"), ColumnDefinition.of("amount", "Amount").withTotal())
                .groupBy("customer")));
        assertTrue(hidden.contains("Total: Ahmed") && hidden.contains("15"), hidden);
    }

    /** الإجمالي الفرعي ما بينزلش لوحده في أول صفحة جديدة: آخر صف في مجموعته بيتنقل معاه، تحت رأس الجدول. */
    @Test
    void aSubtotalStaysWithTheLastRowOfItsGroupAcrossAPageBreak() throws Exception {
        // آخر صف بيكفّيه مكان في الصفحة الأولى في تقرير من غير مجموعات.
        Glyphs plain = Glyphs.of(pdf(statement(120, false)));
        int lastOnFirstPage = 0;
        for (int row = 1; row <= 120; row++) {
            if (plain.drawn(name(row), 1)) {
                lastOnFirstPage = row;
            }
        }
        assertTrue(lastOnFirstPage > 10 && lastOnFirstPage < 120, "الجدول على أكتر من صفحة");

        boolean moved = false;
        for (int last = lastOnFirstPage - 2; last <= lastOnFirstPage + 1; last++) {
            Glyphs glyphs = Glyphs.of(pdf(statement(last, true)));
            Glyphs.Glyph lastRow = glyphs.first(name(last));
            Glyphs.Glyph subtotal = glyphs.first("Sub-G1");
            assertEquals(lastRow.page(), subtotal.page(), "مجموعة بـ " + last + " صف: الإجمالي مع آخر صف");
            List<Float> lines = glyphs.lineYs(subtotal.page());
            assertEquals(lines.indexOf(lastRow.y()) + 1, lines.indexOf(subtotal.y()), "على طول تحته");

            Glyphs.Glyph g2 = glyphs.first("Sub-G2");
            Glyphs.Glyph lastOfG2 = glyphs.first(name(last + 5));
            assertEquals(lastOfG2.page(), g2.page());
            List<Float> g2Lines = glyphs.lineYs(g2.page());
            assertEquals(g2Lines.indexOf(lastOfG2.y()) + 1, g2Lines.indexOf(g2.y()), "آخر مجموعة: تحت آخر صف فيها");

            if (last == lastOnFirstPage) {
                // من غير الإجمالي الفرعي الصف ده كان في الصفحة الأولى؛ اتنقل معاه للتانية.
                assertEquals(2, lastRow.page(), "آخر صف اتنقل مع إجماليه");
                assertTrue(glyphs.drawn(name(last - 1), 1), "اللي قبله فضل مكانه");
                assertEquals(lines.get(0), glyphs.first("Amount", 2).y(), 0.01f, "رأس الجدول في أول الصفحة");
                assertEquals(lines.get(1), lastRow.y(), 0.01f, "والصف تحته على طول");
                moved = true;
            }
        }
        assertTrue(moved);
    }

    /** مجموعة G1 بـ {@code first} صف، وبعدها G2 بخمسة صفوف. */
    private static ReportRequest.Builder<Line> statement(int first, boolean grouped) {
        List<Line> rows = new ArrayList<>();
        for (int row = 1; row <= first + 5; row++) {
            rows.add(new Line(row <= first ? "G1" : "G2", name(row), new BigDecimal(row), 1));
        }
        ReportRequest.Builder<Line> request = request("Statement", rows,
                ColumnDefinition.of("customer", "Customer"), ColumnDefinition.of("item", "Item"), AMOUNT);
        return grouped ? request.groupBy("customer").subtotalLabel("Sub-") : request;
    }

    private static String name(int row) {
        return String.format("row%03d", row);
    }

    /**
     * الإجمالي الكلي تحت آخر إجمالي فرعي على طول وبنفس خلفيته، فبيتفرق عنه بخط سميك فوقه وخطين تحته زي إجمالي
     * الدفاتر؛ الإجماليات الفرعية من غير الخطوط دي، والتقرير من غير مجموعات ما اتغيرش.
     */
    @Test
    void theGrandTotalLooksDifferentFromTheSubtotals() throws Exception {
        byte[] grouped = pdf(request("Statement", LINES,
                ColumnDefinition.of("customer", "Customer"), ColumnDefinition.of("item", "Item"), AMOUNT, QTY)
                .groupBy("customer").subtotalLabel("Sub-"));
        List<float[]> rules = rules(grouped);
        List<float[]> heavy = rules.stream().filter(rule -> rule[1] == 1.2f).toList();
        List<float[]> thin = rules.stream().filter(rule -> rule[1] == 0.6f).toList();
        assertEquals(1, heavy.size(), "خط سميك واحد: فوق الإجمالي الكلي بس");
        assertEquals(2, thin.size(), "وخطين تحته");

        float height;
        try (PDDocument document = Loader.loadPDF(grouped)) {
            height = document.getPage(0).getMediaBox().getHeight();
        }
        Glyphs glyphs = Glyphs.of(grouped);
        float subtotal = height - glyphs.first("Sub-Sara").y();
        float total = height - glyphs.first("Total").y();
        assertTrue(heavy.get(0)[0] < subtotal && heavy.get(0)[0] > total, "السميك بين آخر إجمالي فرعي والكلي");
        for (float[] rule : thin) {
            assertTrue(rule[0] < total, "الخطين تحت الإجمالي الكلي");
        }
        assertEquals(1.8f, Math.abs(thin.get(0)[0] - thin.get(1)[0]), 0.01f, "خطين منفصلين");

        byte[] plain = pdf(request("Statement", LINES,
                ColumnDefinition.of("customer", "Customer"), ColumnDefinition.of("item", "Item"), AMOUNT, QTY));
        assertTrue(rules(plain).stream().noneMatch(rule -> rule[1] == 1.2f || rule[1] == 0.6f),
                "من غير مجموعات الإجمالي زي ما كان");
    }

    /** كل خط أفقي مرسوم ({@code x y m x y l S}): مكانه من تحت الصفحة وسُمكه. */
    private static List<float[]> rules(byte[] pdf) {
        List<float[]> rules = new ArrayList<>();
        float width = 1;
        float[] start = null;
        for (String line : LayoutSnapshot.full(pdf).split("\n")) {
            String[] op = line.trim().split(" ");
            if (op.length < 2 || !op[0].equals("op")) {
                continue;
            }
            switch (op[op.length - 1]) {
                case "w" -> width = Float.parseFloat(op[1]);
                case "m" -> start = new float[] {Float.parseFloat(op[1]), Float.parseFloat(op[2])};
                case "l" -> {
                    if (start != null && Math.abs(Float.parseFloat(op[2]) - start[1]) < 0.01f) {
                        rules.add(new float[] {start[1], width});
                    }
                    start = null;
                }
                default -> {
                }
            }
        }
        return rules;
    }

    @Test
    void groupsWithoutATotalledColumnFail() {
        ReportException thrown = assertThrows(ReportException.class, () -> pdf(request("Statement", LINES,
                ColumnDefinition.of("customer", "Customer"), ColumnDefinition.of("amount", "Amount"))
                .groupBy("customer")));
        assertTrue(thrown.getMessage().contains("withTotal()"), thrown.getMessage());
    }

    /** من غير بيانات مفيش مجموعات: الرأس وتحته الإجمالي صفر، زي التقرير من غير {@code groupBy}. */
    @Test
    void emptyReportHasNoSubtotals() throws Exception {
        String text = textOf(pdf(request("Statement", List.of(),
                ColumnDefinition.of("customer", "Customer"), AMOUNT).groupBy("customer")));
        assertEquals(1, count(text, "Total"), text);
        assertTrue(text.contains("0.00"), text);
    }
}
