package com.smart.report.jasper;

import com.smart.report.Employee;
import com.smart.report.JasperExportFormat;
import com.smart.report.ReportRequest;
import com.smart.report.ReportService;
import com.smart.report.ReportType;
import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.engine.JRFont;
import net.sf.jasperreports.engine.fill.NaiveTextMeasurerFactory;
import net.sf.jasperreports.engine.util.JRTextMeasurerUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    /** قالب الاختبار بعد تبديل كل {@code target} (موجود فيه مرة واحدة) باللي بعده: target، replacement، ... */
    private static String templateWith(Path dir, String... targetsAndReplacements) throws Exception {
        String jrxml;
        try (InputStream in = ArabicPdfTest.class.getClassLoader().getResourceAsStream("reports/employee.jrxml")) {
            jrxml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (int i = 0; i < targetsAndReplacements.length; i += 2) {
            assertTrue(jrxml.contains(targetsAndReplacements[i]), targetsAndReplacements[i]);
            jrxml = jrxml.replace(targetsAndReplacements[i], targetsAndReplacements[i + 1]);
        }
        Path template = dir.resolve("changed.jrxml");
        Files.writeString(template, jrxml);
        return template.toString();
    }

    /** خلايا أول ورقة في الـ XLSX ورا بعض. */
    private static String xlsxText(byte[] xlsx) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            StringBuilder cells = new StringBuilder();
            workbook.getSheetAt(0).forEach(row -> row.forEach(cell -> cells.append(cell).append(' ')));
            return cells.toString();
        }
    }

    private static int count(String text, String word) {
        int count = 0;
        for (int at = text.indexOf(word); at >= 0; at = text.indexOf(word, at + word.length())) {
            count++;
        }
        return count;
    }

    /** قالب الاختبار وخانة العنوان (18 نقطة عريض) بالارتفاع ده. */
    private static String titleBox(Path dir, int height) throws Exception {
        return templateWith(dir, "height=\"36\"", "height=\"" + height + "\"");
    }

    /** اللي اتكتب على System.err (slf4j-simple في الاختبارات) وقت التقرير. */
    private static String warningsOf(ReportCall call) throws Exception {
        PrintStream before = System.err;
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        try {
            call.run();
        } finally {
            System.setErr(before);
        }
        return err.toString(StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    private interface ReportCall {
        void run() throws Exception;
    }

    /** ارتفاع كلمة لاتينية في الصفحة: الـ y بيقول الـ band اللي تحت العنوان كبر ولا لأ. */
    private static float yOf(PdfText pdf, String word) {
        List<PdfText.Glyph> glyphs = pdf.glyphs();
        for (int start = 0; start + word.length() <= glyphs.size(); start++) {
            StringBuilder drawn = new StringBuilder();
            for (int i = start; i < start + word.length(); i++) {
                drawn.append(glyphs.get(i).text());
            }
            if (drawn.toString().equals(word)) {
                return glyphs.get(start).y();
            }
        }
        throw new AssertionError(word + " مش مرسوم");
    }

    /**
     * قالب v2.6.0 بالظبط: عنوان 18 نقطة في خانة 30. سطر Noto Naskh حوالي 1.7 من حجم الخط (31 نقطة)، و
     * JasperReports ما بيطبعش نصًا أول سطر فيه أطول من خانته، فالعنوان كان بيختفي من غير خطأ والصفوف سليمة.
     * دلوقتي بيترسم، والخانة بمقاسها: عناوين الأعمدة في نفس مكانها مع العنوان الإنجليزي.
     */
    @Test
    void arabicTitleInTheOriginalThirtyPointBoxIsDrawn(@TempDir Path dir) throws Exception {
        String template = titleBox(dir, 30);
        PdfText[] arabic = new PdfText[1];
        String warnings = warningsOf(() -> arabic[0] = pdf(template, "تقرير الموظفين"));
        PdfText pdf = arabic[0];

        assertTrue(pdf.text().contains(canonical("تقرير الموظفين")), "العنوان مش مرسوم: " + pdf.text());
        assertTrue(pdf.drawnRightToLeft("تقرير الموظفين"), "العنوان مش مرسوم من اليمين: " + pdf.rightToLeftLines());
        assertTrue(pdf.text().contains(canonical("أحمد علي")) && pdf.text().contains("Sara Youssef"), pdf.text());
        assertEquals(NOTO, pdf.fontsOfArabic());

        PdfText english = pdf(template, "Employee Report");
        assertEquals(yOf(english, "Department"), yOf(pdf, "Department"), 0.01f, "الـ band كبر");

        // مش صامت: التحذير بيسمّي التقرير والخانة، وبيقول المساحة اللي ناقصة
        assertTrue(warnings.contains("\"employee\"") && warnings.contains("$P{ReportTitle}")
                && warnings.contains("31 pt") && warnings.contains("30 pt"), warnings);
        assertFalse(warnings.contains("prints nothing"), warnings);
    }

    /**
     * عنوان أطول من سطر في نفس الخانة: أول سطر بس بيترسم (زي ما JasperReports بيقطع أي نص)، والخانة ما بتكبرش.
     */
    @Test
    void onlyTheFirstLineOfALongArabicTitleIsDrawnInATightBox(@TempDir Path dir) throws Exception {
        String template = titleBox(dir, 30);
        String title = "تقرير الموظفين " + "قسم الهندسة والمالية والمبيعات ".repeat(4) + "ختام";

        PdfText pdf = pdf(template, title);

        assertTrue(pdf.drawnRightToLeft("تقرير الموظفين"), "أول السطر مش مرسوم: " + pdf.rightToLeftLines());
        assertFalse(pdf.text().contains(canonical("ختام")), "سطر تاني اترسم بره الخانة: " + pdf.text());
        assertEquals(yOf(pdf(template, "Employee Report"), "Department"), yOf(pdf, "Department"), 0.01f);
    }

    /** الـ HTML والـ XLSX بياخدوا نفس النص اللي اتعبّى، فالعنوان بيظهر فيهم كمان. */
    @Test
    void arabicTitleInATightBoxReachesHtmlAndXlsx(@TempDir Path dir) throws Exception {
        String template = titleBox(dir, 30);

        String html = new String(report(template, "تقرير الموظفين", JasperExportFormat.HTML), StandardCharsets.UTF_8);
        assertTrue(html.contains(">تقرير<") && html.contains(">الموظفين<"), html);

        String cells = xlsxText(report(template, "تقرير الموظفين", JasperExportFormat.XLSX));
        assertTrue(cells.contains("تقرير الموظفين"), cells);
    }

    /**
     * سطر عربي 18 نقطة وبعده سطر 8 نقط (styled): السطرين يدخلوا في مرة ونص من خانة الـ 30، بس الإنقاذ بياخد أول
     * سطر بس، زي خانة على قده بالظبط. لو أخد التاني كان هيروح لـ HTML و XLSX والـ PDF ما يرسمهوش.
     */
    @Test
    void theRescueKeepsOnlyTheFirstLineOfStyledText(@TempDir Path dir) throws Exception {
        String template = templateWith(dir, "height=\"36\"", "height=\"30\"",
                "<textElement textAlignment=\"Center\">", "<textElement textAlignment=\"Center\" markup=\"styled\">");

        // سطر تاني في فقرة تانية
        String title = "تقرير الموظفين\n<style size=\"8\">ملاحظة صغيرة</style>";
        PdfText pdf = pdf(template, title);
        String html = new String(report(template, title, JasperExportFormat.HTML), StandardCharsets.UTF_8);
        String cells = xlsxText(report(template, title, JasperExportFormat.XLSX));

        assertTrue(pdf.drawnRightToLeft("تقرير الموظفين"), pdf.rightToLeftLines().toString());
        assertTrue(html.contains(">تقرير<") && cells.contains("تقرير الموظفين"), cells);
        assertFalse(pdf.text().contains(canonical("ملاحظة")), pdf.text());
        assertFalse(html.contains("ملاحظة"), html);
        assertFalse(cells.contains("ملاحظة"), cells);

        // سطر تاني في نفس الفقرة: الكلام الصغير اللي بيلف بعد أول سطر مش في أي صيغة
        title = "تقرير الموظفين <style size=\"8\">" + "كلمة ".repeat(150) + "</style>";
        pdf = pdf(template, title);
        html = new String(report(template, title, JasperExportFormat.HTML), StandardCharsets.UTF_8);
        cells = xlsxText(report(template, title, JasperExportFormat.XLSX));

        int drawn = count(pdf.text(), canonical("كلمة"));
        assertTrue(drawn > 0 && drawn < 150, "أول سطر ما فيهوش الكلام الصغير أو فيه كله: " + drawn);
        assertEquals(drawn, count(html, "كلمة"), "الـ HTML فيه سطر مش في الـ PDF");
        assertEquals(drawn, count(cells, "كلمة"), "الـ XLSX فيه سطر مش في الـ PDF");
        assertEquals(yOf(pdf(template, "Employee Report"), "Department"), yOf(pdf, "Department"), 0.01f);
    }

    /**
     * الخانة اللي اتنقذت بتطلع نفس نص خانة طولها على قد سطر واحد (36: السطر 31 والسطرين 44 على الأقل) في كل
     * الصيغ، ومعاه لاحقة القطع ({@code net.sf.jasperreports.text.truncate.suffix}) اللي JasperReports بيحطها
     * في آخر سطر بيدخل.
     */
    @ParameterizedTest
    @ValueSource(strings = {"plain|", "plain|…", "styled|", "styled|…"})
    void theRescueGivesTheTextOfABoxOneLineHigh(String variant, @TempDir Path dir) throws Exception {
        boolean styled = variant.startsWith("styled");
        String suffix = variant.substring(variant.indexOf('|') + 1);
        String title = styled
                ? "تقرير الموظفين <style size=\"8\">" + "كلمة ".repeat(150) + "</style>"
                : "تقرير الموظفين " + "قسم الهندسة والمالية والمبيعات ".repeat(4) + "ختام";

        String rescued = titleTemplate(Files.createDirectories(dir.resolve("tight")), 30, styled, suffix);
        String oneLine = titleTemplate(Files.createDirectories(dir.resolve("one-line")), 36, styled, suffix);

        PdfText pdf = pdf(rescued, title);
        assertEquals(pdf(oneLine, title).text(), pdf.text());
        assertEquals(htmlText(oneLine, title), htmlText(rescued, title));
        assertEquals(xlsxText(report(oneLine, title, JasperExportFormat.XLSX)),
                xlsxText(report(rescued, title, JasperExportFormat.XLSX)));
        assertTrue(pdf.drawnRightToLeft("تقرير الموظفين"), pdf.rightToLeftLines().toString());
        if (!suffix.isEmpty()) {
            assertTrue(pdf.text().contains(suffix), "اللاحقة مش مرسومة: " + pdf.text());
        }
    }

    /** خانة العنوان بالارتفاع ده، styled أو لأ، وبلاحقة قطع لو مش فاضية. */
    private static String titleTemplate(Path dir, int height, boolean styled, String suffix) throws Exception {
        String property = suffix.isEmpty() ? ""
                : "<property name=\"net.sf.jasperreports.text.truncate.suffix\" value=\"" + suffix + "\"/>";
        return templateWith(dir,
                "height=\"36\"/>", "height=\"" + height + "\">" + property + "</reportElement>",
                "<textElement textAlignment=\"Center\">",
                "<textElement textAlignment=\"Center\"" + (styled ? " markup=\"styled\"" : "") + ">");
    }

    /** نص الـ HTML من غير الوسوم (اللي فيها المقاسات). */
    private static String htmlText(String template, String title) throws Exception {
        return new String(report(template, title, JasperExportFormat.HTML), StandardCharsets.UTF_8)
                .replaceAll("<[^>]*>", "");
    }

    /**
     * خانة أقصر من كده (عنوان 18 نقطة في خانة 18) ما ينفعش يترسم فيها سطر من غير ما يغطي اللي تحته: النص ما
     * بيترسمش، زي العنوان الإنجليزي في نفس الخانة، بس بتحذير بيقول ليه وبيسمّي الخانة.
     */
    @Test
    void textThatCannotBeDrawnIsReported(@TempDir Path dir) throws Exception {
        String template = titleBox(dir, 18);
        PdfText[] arabic = new PdfText[1];
        String warnings = warningsOf(() -> arabic[0] = pdf(template, "تقرير الموظفين"));

        assertFalse(arabic[0].text().contains(canonical("تقرير")), arabic[0].text());
        assertTrue(arabic[0].text().contains(canonical("أحمد علي")), arabic[0].text());
        assertTrue(warnings.contains("$P{ReportTitle}") && warnings.contains("prints nothing")
                && warnings.contains("1.7 times the font size"), warnings);
    }

    /**
     * النص اللي مفيهوش عربي بيتقاس زي الأول بالظبط: العنوان الإنجليزي في خانة أقصر من سطره بيضيع زي ما
     * JasperReports بيعمل، بس مش في صمت.
     */
    @Test
    void latinTextKeepsJasperReportsCutButIsReported(@TempDir Path dir) throws Exception {
        String template = titleBox(dir, 18);
        PdfText[] english = new PdfText[1];
        String warnings = warningsOf(() -> english[0] = pdf(template, "Employee Report"));

        assertFalse(english[0].text().contains("Employee"), english[0].text());
        assertTrue(english[0].text().contains("Sara Youssef"), english[0].text());
        assertTrue(warnings.contains("$P{ReportTitle}") && warnings.contains("prints nothing"), warnings);
        assertFalse(warnings.contains("1.7 times"), warnings);
    }

    /** البرنامج اللي اختار مقياس نص لـ JasperReports بنفسه بيفضل باختياره. */
    @Test
    void programsOwnTextMeasurerIsKept() {
        DefaultJasperReportsContext global = DefaultJasperReportsContext.getInstance();
        String before = global.getProperty(JRTextMeasurerUtil.PROPERTY_TEXT_MEASURER_FACTORY);
        assertNotSame(global, JasperReportProvider.fillContext());
        global.setProperty(JRTextMeasurerUtil.PROPERTY_TEXT_MEASURER_FACTORY, NaiveTextMeasurerFactory.class.getName());
        try {
            assertSame(global, JasperReportProvider.fillContext());
        } finally {
            global.setProperty(JRTextMeasurerUtil.PROPERTY_TEXT_MEASURER_FACTORY, before);
        }
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

    /** حرف ما لوش كتلة Unicode (U+2FE0) كان بيوقّف التقرير كله بـ NullPointerException وقت اختيار الخط. */
    @Test
    void characterOutsideEveryUnicodeBlockDoesNotStopTheReport() throws Exception {
        assertFalse(ArabicFonts.isArabic(0x2FE0));

        PdfText pdf = pdf("reports/employee.jrxml", "تقرير ⿠ Report");
        assertTrue(pdf.text().contains(canonical("تقرير")) && pdf.text().contains("Report"), pdf.text());
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
