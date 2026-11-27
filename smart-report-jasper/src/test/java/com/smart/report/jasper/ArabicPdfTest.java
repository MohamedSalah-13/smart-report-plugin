package com.smart.report.jasper;

import com.smart.report.Employee;
import com.smart.report.JasperExportFormat;
import com.smart.report.ReportRequest;
import com.smart.report.ReportService;
import com.smart.report.ReportType;
import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.engine.JRFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static com.smart.report.jasper.PdfText.canonical;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** العربي في PDF طالع من قالب Jasper، وإزاي القالب بيختار خطه. */
class ArabicPdfTest {

    private static final Set<String> NOTO = Set.of("NotoNaskhArabic-Regular", "NotoNaskhArabic-Bold");

    private static final List<Employee> EMPLOYEES = List.of(
            new Employee(1, "أحمد علي", "الهندسة", 12000.0),
            new Employee(2, "Sara Youssef", "Finance", 9500.5));

    @BeforeEach
    void clearCache() {
        JasperReportProvider.clearTemplateCache();
    }

    private static byte[] report(String template, String title, JasperExportFormat format) throws Exception {
        return report(template, title, format, EMPLOYEES);
    }

    private static byte[] report(String template, String title, JasperExportFormat format, List<Employee> employees)
            throws Exception {
        return ReportService.generate(ReportType.JASPER, ReportRequest.<Employee>builder()
                .data(employees)
                .template(template)
                .parameter("ReportTitle", title)
                .exportFormat(format)
                .build());
    }

    private static PdfText pdf(String template, String title) throws Exception {
        return PdfText.of(report(template, title, JasperExportFormat.PDF));
    }

    /** قالب الاختبار ومعاه style افتراضي بالخصائص دي، يعني قالب بيسمّي خطه. */
    private static String templateWithDefaultStyle(Path dir, String fontAttributes) throws Exception {
        return templateWith(dir, "<parameter ",
                "<style name=\"Base\" isDefault=\"true\" " + fontAttributes + "/>\n    <parameter ");
    }

    /** قالب الاختبار بعد تبديل {@code target} (موجود فيه مرة واحدة). */
    private static String templateWith(Path dir, String target, String replacement) throws Exception {
        String jrxml;
        try (InputStream in = ArabicPdfTest.class.getClassLoader().getResourceAsStream("reports/employee.jrxml")) {
            jrxml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(jrxml.contains(target), target);
        Path template = dir.resolve("changed.jrxml");
        Files.writeString(template, jrxml.replace(target, replacement));
        return template.toString();
    }

    /**
     * سطر Noto Naskh طوله حوالي 1.7 من حجم الخط (مكان للتشكيل)، و JasperReports ما بيطبعش نصًا أول سطر فيه
     * أطول من خانته: عنوان 18 نقطة محتاج 31 نقطة، زي ما README بيقول. العنوان الإنجليزي في نفس الخانة بيفضل.
     */
    @Test
    void arabicNeedsABoxTallEnoughForItsLine(@TempDir Path dir) throws Exception {
        String template = templateWith(dir, "height=\"36\"", "height=\"30\"");

        assertFalse(pdf(template, "تقرير الموظفين").text().contains(canonical("تقرير")),
                "لو العربي بقى يتطبع في خانة أقصر من سطره، صحّح فقرة المساحة في README");
        assertTrue(pdf(template, "Employee Report").text().contains("Employee Report"));
    }

    /**
     * مثال README بالظبط: قالب ما بيسمّيش خطًا. العنوان والاسم والقسم العربية كانت بتطلع فاضية
     * (النص كله Helvetica) من غير استثناء ولا تحذير، والصف الإنجليزي سليم.
     */
    @Test
    void arabicInATemplateThatNamesNoFontIsDrawnRightToLeft() throws Exception {
        PdfText pdf = pdf("reports/employee.jrxml", "تقرير الموظفين");

        String text = pdf.text();
        assertTrue(text.contains(canonical("تقرير الموظفين")), "العنوان مش مرسوم: " + text);
        assertTrue(text.contains(canonical("أحمد علي")), "الاسم مش مرسوم: " + text);
        assertTrue(text.contains(canonical("الهندسة")), "القسم مش مرسوم: " + text);
        assertTrue(text.contains("Sara Youssef") && text.contains("Finance"), "الصف الإنجليزي ضاع: " + text);
        assertTrue(text.contains("Department") && text.contains("9500.5"), "عناوين الأعمدة أو الأرقام ضاعت: " + text);

        // أول الكلمة على اليمين: مقروءة من اليمين للشمال بتطلع بترتيبها، مش مقلوبة
        assertTrue(pdf.drawnRightToLeft("تقرير الموظفين"), "العنوان مش مرسوم من اليمين: " + pdf.rightToLeftLines());
        assertTrue(pdf.drawnRightToLeft("أحمد علي"), "الاسم مش مرسوم من اليمين: " + pdf.rightToLeftLines());
        assertTrue(pdf.drawnRightToLeft("الهندسة"), "القسم مش مرسوم من اليمين: " + pdf.rightToLeftLines());

        // العنوان العريض بالملف العريض، والباقي بالعادي، والملفين مضمَّنين
        assertEquals(NOTO, pdf.fontsOfArabic());
    }

    /**
     * الحروف العربية بس هي اللي بتاخد Noto Naskh: اللاتيني بيفضل بخط القالب الافتراضي، بنفس شكله ونفس
     * قياسه. سطر Noto أطول بكتير، فلو اللاتيني اتنقل له كانت الخانات الضيقة هتفقد نصها.
     */
    @Test
    void latinTextKeepsTheDefaultFont() throws Exception {
        PdfText pdf = pdf("reports/employee.jrxml", "تقرير Employees 2026");

        String text = pdf.text();
        assertTrue(text.contains("Employees") && text.contains("2026"), "اللاتيني جنب العربي ضاع: " + text);
        assertTrue(text.contains(canonical("تقرير")), "العربي العريض ضاع: " + text);
        assertEquals(NOTO, pdf.fontsOfArabic());
        assertEquals(Set.of("Helvetica"), pdf.fontsOfLatin());
    }

    /** تقرير إنجليزي بس زي ما كان بالظبط: Helvetica، ومفيش Noto حتى في المسافات والأرقام. */
    @Test
    void reportWithoutArabicIsUnchanged() throws Exception {
        PdfText pdf = PdfText.of(report("reports/employee.jrxml", "Employee Report", JasperExportFormat.PDF,
                List.of(new Employee(2, "Sara Youssef", "Finance", 9500.5))));

        assertTrue(pdf.text().contains("Employee Report"), pdf.text());
        assertEquals(Set.of("Helvetica"), pdf.fonts());
    }

    /** الفاصلة والفاصلة المنقوطة وعلامة الاستفهام والتطويل حروف "مشتركة" في Unicode، والأرقام الهندية كمان. */
    @Test
    void arabicPunctuationAndDigitsAreDrawn() throws Exception {
        PdfText pdf = pdf("reports/employee.jrxml", "القاهرة، مصر؛ كم؟ ٢٠٢٦ تـقـريـر");

        String text = pdf.text();
        for (String piece : List.of("،", "؛", "؟", "٢٠٢٦", "ـ")) {
            assertTrue(text.contains(piece), "\"" + piece + "\" مش مرسوم: " + text);
        }
        assertEquals(NOTO, pdf.fontsOfArabic());
    }

    /**
     * القالب اللي بيسمّي "SansSerif" (زي قوالب iReport القديمة) هو نفسه القالب اللي ما بيسمّيش خطًا. والقالب
     * اللي بيسمّي "Noto Naskh Arabic" بياخد النص كله بيه، اللاتيني كمان.
     */
    @ParameterizedTest
    @ValueSource(strings = {"SansSerif", "Noto Naskh Arabic"})
    void templateCanNameAFontThatHasArabic(String font, @TempDir Path dir) throws Exception {
        PdfText pdf = pdf(templateWithDefaultStyle(dir, "fontName=\"" + font + "\""), "تقرير Employees");

        String text = pdf.text();
        assertTrue(text.contains(canonical("تقرير")) && text.contains("Employees"), "العنوان ناقص: " + text);
        assertTrue(text.contains(canonical("أحمد علي")) && text.contains("Sara Youssef"), "الصفوف ناقصة: " + text);
        assertTrue(pdf.drawnRightToLeft("أحمد علي"), "الاسم مش مرسوم من اليمين: " + pdf.rightToLeftLines());
        assertTrue(NOTO.containsAll(pdf.fontsOfArabic()) && !pdf.fontsOfArabic().isEmpty(), pdf.fontsOfArabic().toString());
        if (font.equals("Noto Naskh Arabic")) {
            assertEquals(Set.of("NotoNaskhArabic-Regular"), pdf.fonts());
        }
    }

    /** القالب اللي سمّى خطًا تانيًا بيفضل بخطه: المكتبة ما بتغيّرش اختيار حد. */
    @Test
    void templateThatNamesItsOwnFontKeepsIt(@TempDir Path dir) throws Exception {
        PdfText pdf = pdf(templateWithDefaultStyle(dir, "fontName=\"Serif\" pdfFontName=\"Courier\""), "تقرير الموظفين");

        assertTrue(pdf.text().contains("Sara Youssef"), pdf.text());
        assertEquals(Set.of("Courier"), pdf.fonts());
    }

    /** البرنامج اللي غيّر خط JasperReports الافتراضي بنفسه بيفضل باختياره. */
    @Test
    void programsOwnDefaultFontIsKept() throws Exception {
        DefaultJasperReportsContext global = DefaultJasperReportsContext.getInstance();
        String before = global.getProperty(JRFont.DEFAULT_FONT_NAME);
        global.setProperty(JRFont.DEFAULT_FONT_NAME, "Serif");
        try {
            PdfText pdf = pdf("reports/employee.jrxml", "تقرير الموظفين");
            assertTrue(pdf.text().contains("Sara Youssef"), pdf.text());
            assertTrue(pdf.fontsOfArabic().isEmpty(), pdf.fontsOfArabic().toString());
        } finally {
            global.setProperty(JRFont.DEFAULT_FONT_NAME, before);
        }
    }

    /** الصيغ اللي بتكتب اسم الخط بتكتب اسمه الحقيقي، مش اسم العائلة المخفية. */
    @Test
    void otherFormatsKeepTheArabicAndNameTheRealFont() throws Exception {
        String html = new String(report("reports/employee.jrxml", "تقرير الموظفين", JasperExportFormat.HTML),
                StandardCharsets.UTF_8);
        // كل كلمة عربية في span بخطها، والمسافة بينهم بخط القالب
        for (String word : List.of("تقرير", "الموظفين", "أحمد", "علي", "الهندسة")) {
            assertTrue(html.contains(">" + word + "<"), word + " مش في الـ HTML: " + html);
        }
        assertTrue(html.contains("'Noto Naskh Arabic', Tahoma, Arial, sans-serif"), html);
        assertFalse(html.contains(ArabicFonts.ARABIC_LETTERS_FAMILY), html);

        byte[] xlsx = report("reports/employee.jrxml", "تقرير الموظفين", JasperExportFormat.XLSX);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            StringBuilder cells = new StringBuilder();
            workbook.getSheetAt(0).forEach(row -> row.forEach(cell -> cells.append(cell).append(' ')));
            assertTrue(cells.toString().contains("تقرير الموظفين") && cells.toString().contains("أحمد علي"),
                    cells.toString());
        }
    }

    /** الخطوط نسخة من خطوط smart-report-pdf ومعاها رخصتها؛ لو واحدة اتحدّثت التانية لازم تتحدّث. */
    @ParameterizedTest
    @ValueSource(strings = {"NotoNaskhArabic-Regular.ttf", "NotoNaskhArabic-Bold.ttf", "OFL.txt"})
    void fontFilesAreThoseOfThePdfModule(String file) throws Exception {
        assertArrayEquals(resource("com/smart/report/pdf/fonts/" + file),
                resource("com/smart/report/jasper/fonts/" + file), file);
    }

    private static byte[] resource(String name) throws Exception {
        try (InputStream in = ArabicPdfTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(in, name);
            return in.readAllBytes();
        }
    }
}
