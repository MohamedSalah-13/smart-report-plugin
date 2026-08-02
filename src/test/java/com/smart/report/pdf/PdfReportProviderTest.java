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

    /** الخلايا الطويلة بتتقصّر، والتقصير ما يوقعش مع النصوص غير اللاتينية. */
    @Test
    void truncatesLongArabicValuesWithoutFailing() throws Exception {
        requireUnicodeFont();

        ReportRequest<Employee> request = ReportRequest.<Employee>builder()
                .title("تقرير")
                .data(List.of(new Employee("محمد".repeat(80), "الهندسة", "")))
                .addColumn("name", "الاسم")
                .addColumn("department", "القسم")
                .build();

        assertTrue(ReportService.generate(ReportType.PDF, request).length > 0);
    }
}
