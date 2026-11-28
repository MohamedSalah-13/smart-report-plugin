package com.smart.report.pdf;

import com.smart.report.ReportRequest;
import com.smart.report.ReportService;
import com.smart.report.ReportType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfReportProviderTest {

    public record Employee(String name, String department, String notes) {}

    private static String textOf(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static void requireUnicodeFont() {
        Assumptions.assumeTrue(PdfFontResolver.resolve().available(),
                "مفيش خط Unicode متاح على الجهاز ده؛ حدّد -D" + PdfFontResolver.FONT_PROPERTY);
    }

    /**
     * استخراج النص من PDF بيرجّع صور العرض لحروف أساسية، لكن الحرف اللي بيرجع بيختلف حسب جدول
     * الـ cmap في الخط (ي فارسية بدل عربية، ه دوتشمي بدل هاء...). التوحيد ده بيخلي الاختبار
     * يقيس المحتوى والترتيب مش تفاصيل الخط.
     */
    private static String canonical(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            out.append(switch (c) {
                case 'ي', 'ی', 'ى', 'ے', 'ې' -> 'ي';
                case 'ه', 'ھ', 'ہ', 'ە' -> 'ه';
                case 'ك', 'ک' -> 'ك';
                case 'ا', 'أ', 'إ', 'آ', 'ٱ' -> 'ا';
                default -> c;
            });
        }
        return out.toString();
    }

    /** المثال الرئيسي في README: كان بيرمي IllegalArgumentException قبل كده. */
    @Test
    void generatesArabicReportWithoutThrowing() throws Exception {
        requireUnicodeFont();

        ReportRequest<Employee> request = ReportRequest.<Employee>builder()
                .title("تقرير الموظفين")
                .data(List.of(new Employee("أحمد علي", "الهندسة", "ملاحظة")))
                .addColumn("name", "الاسم")
                .addColumn("department", "القسم")
                .build();

        byte[] pdf = ReportService.generate(ReportType.PDF, request);
        assertTrue(pdf.length > 0);

        String text = canonical(textOf(pdf));
        assertTrue(text.contains(canonical("تقرير الموظفين")), "العنوان مش موجود: " + text);
        assertTrue(text.contains(canonical("أحمد علي")), "اسم الموظف مش موجود: " + text);
        assertTrue(text.contains(canonical("الهندسة")), "القسم مش موجود: " + text);
    }

    /**
     * PDFBox بيطبّق bidi وقت الاستخراج، فبيرجّع النص للترتيب المنطقي. يعني لو كنا رسمنا الحروف
     * بالترتيب المنطقي (بدون عكس)، الاستخراج كان هيطلع مقلوبًا. طلوعه سليمًا دليل إننا رسمنا
     * الترتيب البصري الصحيح من اليمين لليسار.
     */
    @Test
    void arabicIsDrawnInRightToLeftVisualOrder() throws Exception {
        requireUnicodeFont();

        ReportRequest<Employee> request = ReportRequest.<Employee>builder()
                .title("العربية")
                .data(List.of(new Employee("أحمد", "الهندسة", "")))
                .addColumn("name", "الاسم")
                .build();

        String text = canonical(textOf(ReportService.generate(ReportType.PDF, request)));
        assertTrue(text.contains(canonical("العربية")), "العنوان لازم يظهر بترتيبه الصحيح: " + text);
        assertFalse(text.contains(canonical("ةيبرعلا")), "النص اتكتب مقلوبًا: " + text);
    }

    /** قيمة فيها سطر جديد كانت بتوقّف التقرير كله. */
    @Test
    void valuesContainingNewlinesDoNotBreakTheReport() throws Exception {
        ReportRequest<Employee> request = ReportRequest.<Employee>builder()
                .title("Notes")
                .data(List.of(new Employee("Ahmed Ali", "Engineering", "first line\nsecond line\ttabbed")))
                .addColumn("name", "Name")
                .addColumn("notes", "Notes")
                .build();

        String text = textOf(ReportService.generate(ReportType.PDF, request));
        assertTrue(text.contains("Ahmed Ali"), text);
        assertTrue(text.contains("first line"), "محتوى السطر الأول لازم يفضل موجود: " + text);
        // السطر الجديد بقى مسافة جوه الخلية، فالصف الواحد يفضل صفًا واحدًا
        assertFalse(text.contains("first line\nsecond"), "السطر الجديد المفروض اتحوّل لمسافة: " + text);
    }

    /** الخلايا الطويلة بتلتف على عرض عمودها — حتى كلمة واحدة أطول من العمود — ومفيش حاجة بتتقطع. */
    @Test
    void wrapsLongArabicValuesWithoutLosingAnyOfThem() throws Exception {
        requireUnicodeFont();

        ReportRequest<Employee> request = ReportRequest.<Employee>builder()
                .title("تقرير")
                .data(List.of(new Employee("محمد".repeat(80), "الهندسة", ""),
                        new Employee("أول الاسم ثم باقي الاسم الطويل جدا الذي لا يكفيه سطر واحد في عمود ضيق آخر الاسم",
                                "القسم", "")))
                .addColumn("name", "الاسم")
                .addColumn("department", "القسم")
                .addColumn("notes", "ملاحظات")
                .build();

        String text = canonical(textOf(ReportService.generate(ReportType.PDF, request)));
        assertFalse(text.contains("..."), "مفيش تقطيع: " + text);
        assertTrue(text.contains(canonical("أول الاسم")), "أول القيمة الطويلة لازم يتطبع: " + text);
        assertTrue(text.contains(canonical("آخر الاسم")), "وآخرها كمان: " + text);
    }

    /** التقرير العربي جدوله من اليمين: أول عمود على يمين التاني. */
    @Test
    void arabicReportRunsRightToLeft() throws Exception {
        requireUnicodeFont();

        ReportRequest<Employee> request = ReportRequest.<Employee>builder()
                .title("تقرير")
                .data(List.of(new Employee("AAA", "BBB", "")))
                .addColumn("name", "الاسم")
                .addColumn("department", "القسم")
                .build();

        Glyphs glyphs = Glyphs.of(ReportService.generate(ReportType.PDF, request));
        assertTrue(glyphs.first("AAA").x() > glyphs.first("BBB").x());
    }

    public record Sale(String name, Object amount, Object when) {}

    private static byte[] sales(List<Sale> rows) throws Exception {
        return ReportService.generate(ReportType.PDF, ReportRequest.<Sale>builder()
                .title("Sales").data(rows)
                .addColumn("name", "Name").addColumn("amount", "Amount").addColumn("when", "When")
                .build());
    }

    /** toString كان بيطبع "Thu Jan 01 02:00:00 EET 1970" و"1234567.5" من غير فواصل. */
    @Test
    void valuesAreFormattedByTheirType() throws Exception {
        java.util.Date legacy = new java.util.GregorianCalendar(2026, java.util.Calendar.NOVEMBER, 26, 10, 30).getTime();
        String text = textOf(sales(List.of(
                new Sale("double", 1234567.5, java.time.LocalDate.of(2026, 11, 26)),
                new Sale("decimal", new java.math.BigDecimal("1200.50"), legacy),
                new Sale("whole", 7200.0, java.time.LocalDateTime.of(2026, 11, 26, 9, 5, 7)),
                new Sale("long", 1000000L, java.sql.Date.valueOf("2026-11-25")),
                new Sale("none", null, null))));

        for (String expected : new String[]{"1,234,567.5", "1,200.50", "7,200", "1,000,000",
                "2026-11-26", "2026-11-26 10:30:00", "2026-11-26 09:05:07", "2026-11-25"}) {
            assertTrue(text.contains(expected), expected + " مش موجود في: " + text);
        }
        assertFalse(text.contains("7,200.0"), "الـ double بأقصر صورة: " + text);
        assertFalse(text.contains("null"), text);
        assertFalse(text.contains("1970") || text.contains("GMT") || text.contains("EET"), text);
    }

    /** عمود قيمه كلها أرقام بيتحاذى لنهاية السطر، فالرقم القصير بيبدأ أبعد من الطويل. */
    @Test
    void numberColumnIsAlignedToTheEndAndTextColumnToTheStart() throws Exception {
        Glyphs glyphs = Glyphs.of(sales(List.of(new Sale("a", 9, "x"), new Sale("abcdef", 1000, "xyzxyz"))));

        assertTrue(glyphs.first("9").x() > glyphs.first("1,000").x() + 5, "الأرقام تحت بعض من آخرها");
        assertTrue(Math.abs(glyphs.first("x").x() - glyphs.first("xyzxyz").x()) < 0.5f, "النص من أوله");
    }

    /** عمود فيه رقم ونص مع بعض بيفضل نصًا عاديًا. */
    @Test
    void mixedColumnStaysAlignedToTheStart() throws Exception {
        Glyphs glyphs = Glyphs.of(sales(List.of(new Sale("a", 9, "x"), new Sale("b", "n/a", "y"))));

        assertTrue(Math.abs(glyphs.first("9").x() - glyphs.first("n/a").x()) < 0.5f);
    }

    private static byte[] sales(List<Sale> rows, com.smart.report.ColumnDefinition... columns) throws Exception {
        return ReportService.generate(ReportType.PDF, ReportRequest.<Sale>builder()
                .title("Sales").data(rows).columns(List.of(columns)).build());
    }

    /** خيارات العمود بتغلب الافتراضي: النمط، والمحاذاة حتى في عمود أرقام. */
    @Test
    void columnFormatAndAlignOverrideTheDefaults() throws Exception {
        byte[] pdf = sales(List.of(new Sale("a", 9, java.time.LocalDate.of(2026, 11, 26)),
                        new Sale("b", 1000, java.time.LocalDate.of(2026, 1, 2))),
                com.smart.report.ColumnDefinition.of("name", "Name"),
                com.smart.report.ColumnDefinition.of("amount", "Amount").withFormat("#,##0.00")
                        .withAlign(com.smart.report.Alignment.START),
                com.smart.report.ColumnDefinition.of("when", "When").withFormat("dd/MM/yyyy"));

        String text = textOf(pdf);
        for (String expected : new String[]{"9.00", "1,000.00", "26/11/2026", "02/01/2026"}) {
            assertTrue(text.contains(expected), expected + " مش موجود في: " + text);
        }
        Glyphs glyphs = Glyphs.of(pdf);
        assertTrue(Math.abs(glyphs.first("9.00").x() - glyphs.first("1,000.00").x()) < 0.5f,
                "المحاذاة المطلوبة لبداية السطر غلبت محاذاة الأرقام التلقائية");
    }

    /** العمود بوزن 3 عرضه تلات أضعاف العمود العادي. */
    @Test
    void columnWeightSharesTheTableWidth() throws Exception {
        Glyphs glyphs = Glyphs.of(sales(List.of(new Sale("a", "b", "c")),
                com.smart.report.ColumnDefinition.of("name", "H1").withWeight(3),
                com.smart.report.ColumnDefinition.of("amount", "H2"),
                com.smart.report.ColumnDefinition.of("when", "H3")));

        // رءوس بنفس العرض ومتوسّطة في أعمدتها، فالمسافة بين بداياتها هي المسافة بين منتصفات الأعمدة.
        float wide = glyphs.first("H2").x() - glyphs.first("H1").x();
        float normal = glyphs.first("H3").x() - glyphs.first("H2").x();
        assertTrue(Math.abs(wide / normal - 2f) < 0.3f,
                "منتصف رأس لمنتصف رأس: (3+1)/2 ÷ (1+1)/2 = 2؛ الفعلي " + wide / normal);
    }

    /** النمط اللي ما ينفعش مع القيمة بيقول اسم العمود، مش استثناء غامض من جوّه الـ JDK. */
    @Test
    void patternThatDoesNotFitTheValueIsReportedWithTheColumnName() {
        com.smart.report.ReportException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                com.smart.report.ReportException.class,
                () -> sales(List.of(new Sale("a", 1, java.time.LocalDate.of(2026, 11, 26))),
                        com.smart.report.ColumnDefinition.of("when", "When").withFormat("HH:mm")));
        assertTrue(thrown.getMessage().contains("'when'") && thrown.getMessage().contains("HH:mm"),
                thrown.getMessage());
    }

    /** الورقة والاتجاه بيتحددوا من parameters. */
    @Test
    void paperAndDirectionCanBeChosenThroughParameters() throws Exception {
        ReportRequest<Employee> request = ReportRequest.<Employee>builder()
                .title("Report")
                .data(List.of(new Employee("AAA", "BBB", "")))
                .addColumn("name", "Name")
                .addColumn("department", "Department")
                .parameter(PdfReportProvider.PAPER, "A5-landscape")
                .parameter(PdfReportProvider.RIGHT_TO_LEFT, true)
                .build();

        byte[] pdf = ReportService.generate(ReportType.PDF, request);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertTrue(document.getPage(0).getMediaBox().getWidth() > document.getPage(0).getMediaBox().getHeight());
            assertTrue(Math.abs(document.getPage(0).getMediaBox().getHeight() - Paper.A5.width()) < 0.1f);
        }
        Glyphs glyphs = Glyphs.of(pdf);
        assertTrue(glyphs.first("AAA").x() > glyphs.first("BBB").x(), "الاتجاه المطلوب من اليمين");
    }

    private static ReportRequest.Builder<Employee> columns(int count) {
        ReportRequest.Builder<Employee> builder = ReportRequest.<Employee>builder()
                .title("Wide").data(List.of(new Employee("AAA", "BBB", "CCC")));
        for (int i = 0; i < count; i++) {
            builder.addColumn(List.of("name", "department", "notes").get(i % 3), "C" + i);
        }
        return builder;
    }

    private static boolean landscape(ReportRequest<Employee> request) throws Exception {
        try (PDDocument document = Loader.loadPDF(ReportService.generate(ReportType.PDF, request))) {
            return document.getPage(0).getMediaBox().getWidth() > document.getPage(0).getMediaBox().getHeight();
        }
    }

    /** تقرير 10 أعمدة بيطلع بالعرض مع الخيار، وبالطول من غيره أو تحت العدد. */
    @Test
    void manyColumnsTurnThePageSideways() throws Exception {
        assertTrue(landscape(columns(10).parameter(PdfReportProvider.LANDSCAPE_FROM_COLUMNS, 8).build()));
        assertTrue(landscape(columns(10).parameter(PdfReportProvider.LANDSCAPE_FROM_COLUMNS, "10").build()));
        assertFalse(landscape(columns(10).build()));
        assertFalse(landscape(columns(7).parameter(PdfReportProvider.LANDSCAPE_FROM_COLUMNS, 8).build()));
        assertTrue(landscape(columns(10).parameter(PdfReportProvider.PAPER, "A5")
                .parameter(PdfReportProvider.LANDSCAPE_FROM_COLUMNS, 8).build()));
        assertFalse(landscape(columns(10).parameter(PdfReportProvider.PAPER, Paper.ROLL_80)
                .parameter(PdfReportProvider.LANDSCAPE_FROM_COLUMNS, 8).build()), "اللفة ما بتتقلبش");
    }

    @Test
    void anInvalidColumnCountIsRefused() {
        for (Object bad : List.of(0, "many", 2.5)) {
            com.smart.report.ReportException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                    com.smart.report.ReportException.class, () -> ReportService.generate(ReportType.PDF,
                            columns(3).parameter(PdfReportProvider.LANDSCAPE_FROM_COLUMNS, bad).build()));
            assertTrue(thrown.getMessage().contains(PdfReportProvider.LANDSCAPE_FROM_COLUMNS), thrown.getMessage());
        }
    }
}
