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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** صف الإجمالي في تقرير الـ PDF العام ({@link ColumnDefinition#withTotal()}). */
class PdfReportTotalsTest {

    public record Line(String name, Object amount, Object qty, String note) {}

    private static final List<Line> LINES = List.of(
            new Line("alpha", new BigDecimal("1000.50"), 3, "x"),
            new Line("beta", new BigDecimal("250.25"), 40, "yy"));

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

    /** نفس توحيد الحروف اللي في PdfReportProviderTest: الاستخراج بيرجّع الحرف حسب جدول الخط. */
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

    /**
     * التقرير الإنجليزي: الكلمة تحت أول عمود من أوله، وكل مجموع تحت أرقام عموده من آخرها (حافتهم اليمين
     * واحدة رغم إن المجموع بخط عريض)، وكلهم في سطر واحد تحت البيانات.
     */
    @Test
    void totalsLineUpUnderTheirColumnsLeftToRight() throws Exception {
        Glyphs glyphs = Glyphs.of(pdf(request("Sales", LINES,
                ColumnDefinition.of("name", "Name"),
                ColumnDefinition.of("amount", "Amount").withFormat("#,##0.00").withTotal(),
                ColumnDefinition.of("qty", "Qty").withTotal(),
                ColumnDefinition.of("note", "Note"))));

        assertEquals(glyphs.first("alpha").x(), glyphs.first("Total").x(), 0.5f, "الكلمة من أول العمود الأول");
        // أرقام بأطوال مختلفة: لو كانت متحاذية من أولها، حافتهم اليمين ما كانتش هتبقى واحدة.
        assertEquals(glyphs.last("250.25").right(), glyphs.last("1,250.75").right(), 0.5f,
                "المجموع تحت المبالغ من آخرها");
        assertEquals(glyphs.last("3").right(), glyphs.last("43").right(), 0.5f, "عمود من غير نمط برضه");

        float totalsLine = glyphs.first("Total").y();
        assertEquals(totalsLine, glyphs.first("1,250.75").y(), 0.5f);
        assertEquals(totalsLine, glyphs.first("43").y(), 0.5f);
        assertTrue(totalsLine > glyphs.first("beta").y(), "صف الإجمالي تحت آخر صف");
        assertTrue(glyphs.first("Total").x() < glyphs.first("1,250.75").x()
                && glyphs.last("1,250.75").right() < glyphs.first("43").x(), "كل مجموع في عموده بالترتيب");
    }

    /**
     * التقرير العربي: العمود الأول على اليمين، فالكلمة على يمين المجموع؛ الأرقام لنهاية السطر يعني من
     * شمالها، والكلمة لبدايته يعني من يمينها.
     */
    @Test
    void totalsLineUpUnderTheirColumnsRightToLeft() throws Exception {
        requireUnicodeFont();

        Glyphs glyphs = Glyphs.of(pdf(request("المبيعات", LINES,
                ColumnDefinition.of("name", "الاسم"),
                ColumnDefinition.of("amount", "المبلغ").withFormat("#,##0.00").withTotal(),
                ColumnDefinition.of("qty", "الكمية").withTotal(),
                ColumnDefinition.of("note", "ملاحظات"))
                .totalsLabel("SUM")));

        assertEquals(glyphs.last("alpha").right(), glyphs.last("SUM").right(), 0.5f, "الكلمة من أول العمود (يمينه)");
        assertEquals(glyphs.first("250.25").x(), glyphs.first("1,250.75").x(), 0.5f, "المجموع تحت المبالغ من شمالها");
        assertEquals(glyphs.first("3").x(), glyphs.first("43").x(), 0.5f);

        assertEquals(glyphs.first("SUM").y(), glyphs.first("1,250.75").y(), 0.5f);
        assertTrue(glyphs.first("SUM").y() > glyphs.first("beta").y());
        assertTrue(glyphs.first("SUM").x() > glyphs.last("1,250.75").right()
                && glyphs.first("1,250.75").x() > glyphs.last("43").right(), "الأعمدة من اليمين للشمال");
    }

    /** الكلمة الافتراضية حسب اتجاه التقرير. */
    @Test
    void defaultLabelFollowsTheReportsDirection() throws Exception {
        requireUnicodeFont();

        String arabic = canonical(textOf(pdf(request("المبيعات", LINES,
                ColumnDefinition.of("name", "الاسم"), ColumnDefinition.of("amount", "المبلغ").withTotal()))));
        assertTrue(arabic.contains(canonical("الإجمالى")), arabic);

        String english = textOf(pdf(request("Sales", LINES,
                ColumnDefinition.of("name", "Name"), ColumnDefinition.of("amount", "Amount").withTotal())));
        assertTrue(english.contains("Total"), english);
        assertTrue(english.contains("1,250.75"), english);
    }

    /** الكلمة تحت أول عمود مش متجمّع، حتى لو قبله عمود متجمّع؛ وكلمة البرنامج بتغلب الافتراضي. */
    @Test
    void labelGoesUnderTheFirstColumnThatIsNotTotalled() throws Exception {
        Glyphs glyphs = Glyphs.of(pdf(request("Sales", LINES,
                ColumnDefinition.of("amount", "Amount").withTotal(),
                ColumnDefinition.of("name", "Name"))
                .totalsLabel("Grand")));

        assertEquals(glyphs.first("alpha").x(), glyphs.first("Grand").x(), 0.5f);
        assertTrue(glyphs.first("Grand").x() > glyphs.last("1,250.75").right());
    }

    @Test
    void everyColumnTotalledLeavesNoRoomForTheLabel() throws Exception {
        String text = textOf(pdf(request("Sales", LINES,
                ColumnDefinition.of("amount", "Amount").withTotal(), ColumnDefinition.of("qty", "Qty").withTotal())));
        assertTrue(text.contains("1,250.75") && text.contains("43"), text);
        assertFalse(text.contains("Total"), text);
    }

    @Test
    void noTotalledColumnMeansNoTotalsRow() throws Exception {
        String text = textOf(pdf(request("Sales", LINES,
                ColumnDefinition.of("name", "Name"), ColumnDefinition.of("amount", "Amount"))));
        assertFalse(text.contains("Total") || text.contains("1,250.75"), text);
    }

    /** {@code BigDecimal} بيتجمع لآخر خانة؛ مجموع {@code double} كان هيطلع 12,345,678,901,234,568. */
    @Test
    void decimalsAreTotalledExactly() throws Exception {
        List<Line> rows = List.of(
                new Line("a", new BigDecimal("12345678901234567.89"), 1, ""),
                new Line("b", new BigDecimal("0.01"), 1, ""),
                new Line("c", 0.1, 1, ""),
                new Line("d", 0.2, 1, ""));

        String plain = textOf(pdf(request("Sales", rows,
                ColumnDefinition.of("name", "Name"), ColumnDefinition.of("amount", "Amount").withTotal())));
        assertTrue(plain.contains("12,345,678,901,234,568.20"), plain);

        String formatted = textOf(pdf(request("Sales", rows,
                ColumnDefinition.of("name", "Name"),
                ColumnDefinition.of("amount", "Amount").withFormat("#,##0.000").withTotal())));
        assertTrue(formatted.contains("12,345,678,901,234,568.200"), formatted);
    }

    /** الـ double بيتجمع بالرقم اللي في خليته، والخلية الفاضية ما بتزوّدش حاجة. */
    @Test
    void doublesAddUpAsShownAndNullsAreSkipped() throws Exception {
        String text = textOf(pdf(request("Sales", List.of(
                        new Line("a", 0.1, null, ""), new Line("b", null, 7, ""), new Line("c", 0.2, 5L, "")),
                ColumnDefinition.of("name", "Name"),
                ColumnDefinition.of("amount", "Amount").withTotal(),
                ColumnDefinition.of("qty", "Qty").withTotal())));

        assertTrue(text.contains("0.3"), text);
        assertFalse(text.contains("0.3000"), text);
        assertTrue(text.contains("12"), text);
    }

    /** من غير بيانات: رأس الجدول وتحته الإجمالي صفر بنمط العمود. */
    @Test
    void emptyReportTotalsZero() throws Exception {
        byte[] pdf = pdf(request("Sales", List.of(),
                ColumnDefinition.of("name", "Name"),
                ColumnDefinition.of("amount", "Amount").withFormat("#,##0.00").withTotal()));

        String text = textOf(pdf);
        assertTrue(text.contains("Total") && text.contains("0.00"), text);
        Glyphs glyphs = Glyphs.of(pdf);
        assertTrue(glyphs.first("Total").y() > glyphs.first("Amount").y(), "الإجمالي تحت الرأس");
        assertTrue(glyphs.first("0.00").x() > glyphs.first("Amount").x(), "الصفر لنهاية عموده");
    }

    /** القيمة اللي مش رقم في عمود متجمّع بتقول اسم العمود ورقم الصف، زي النمط الغلط. */
    @Test
    void valueThatIsNotANumberIsReportedWithTheColumnAndRow() {
        ReportException thrown = assertThrows(ReportException.class, () -> pdf(request("Sales", List.of(
                        new Line("a", 100, 1, ""), new Line("b", "n/a", 1, "")),
                ColumnDefinition.of("name", "Name"), ColumnDefinition.of("amount", "Amount").withTotal())));
        assertTrue(thrown.getMessage().contains("'amount'") && thrown.getMessage().contains("row 2")
                && thrown.getMessage().contains("n/a"), thrown.getMessage());

        ReportException nan = assertThrows(ReportException.class, () -> pdf(request("Sales", List.of(
                        new Line("a", Double.NaN, 1, "")),
                ColumnDefinition.of("name", "Name"), ColumnDefinition.of("amount", "Amount").withTotal())));
        assertTrue(nan.getMessage().contains("'amount'") && nan.getMessage().contains("NaN"), nan.getMessage());
    }

    /** نفس العمود من غير إجمالي بيطبع أي قيمة زي الأول. */
    @Test
    void textInAColumnThatIsNotTotalledStillPrints() throws Exception {
        String text = textOf(pdf(request("Sales", List.of(new Line("a", "n/a", 1, "")),
                ColumnDefinition.of("name", "Name"), ColumnDefinition.of("amount", "Amount"),
                ColumnDefinition.of("qty", "Qty").withTotal())));
        assertTrue(text.contains("n/a"), text);
    }
}
