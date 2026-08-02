package com.smart.report.pdf;

import com.smart.report.ReportException;
import com.smart.report.ReportRequest;
import com.smart.report.ReportService;
import com.smart.report.ReportType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * سلوك المكتبة لما ميبقاش فيه خط Unicode على الجهاز أصلًا: بترجع لـ Helvetica، والنصوص
 * اللاتينية تشتغل عادي، وأي نص خارج Latin-1 بيرمي {@link ReportException} برسالة بتقول
 * تعمل إيه — بدل {@code IllegalArgumentException} غير متوقّعة جاية من جوّه PDFBox.
 */
class PdfFontFallbackTest {

    public record Row(String label) {}

    /** بنجبر الـ resolver على حالة "مفيش خط" من غير ما نلمس خطوط النظام الحقيقية. */
    @BeforeEach
    @SuppressWarnings("unchecked")
    void forceNoUnicodeFont() throws Exception {
        Field cache = PdfFontResolver.class.getDeclaredField("CACHE");
        cache.setAccessible(true);
        ((AtomicReference<PdfFontResolver.Fonts>) cache.get(null))
                .set(new PdfFontResolver.Fonts(null, null));
    }

    @AfterEach
    void restoreResolver() {
        PdfFontResolver.clearCache();
    }

    @Test
    void latinReportsStillWorkWithTheBuiltInFont() throws Exception {
        ReportRequest<Row> request = ReportRequest.<Row>builder()
                .title("Employee Report")
                .data(List.of(new Row("Ahmed Ali")))
                .addColumn("label", "Name")
                .build();

        assertTrue(ReportService.generate(ReportType.PDF, request).length > 0);
    }

    @Test
    void arabicFailsWithAnActionableReportException() {
        ReportRequest<Row> request = ReportRequest.<Row>builder()
                .title("تقرير الموظفين")
                .data(List.of(new Row("أحمد علي")))
                .addColumn("label", "الاسم")
                .build();

        ReportException thrown = assertThrows(ReportException.class,
                () -> ReportService.generate(ReportType.PDF, request));

        String message = thrown.getMessage();
        assertTrue(message.contains("has no glyph for"), message);
        assertTrue(message.contains("U+"), "لازم تحدد الحرف المسبب: " + message);
        assertTrue(message.contains(PdfFontResolver.FONT_PROPERTY),
                "لازم تقول للمستخدم يظبط الخط إزاي: " + message);
    }
}
