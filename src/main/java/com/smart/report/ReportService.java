package com.smart.report;

import com.smart.report.excel.ExcelReportProvider;
import com.smart.report.jasper.JasperReportProvider;
import com.smart.report.pdf.PdfReportProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * نقطة الدخول الموحّدة لتوليد التقارير. اختر النوع المطلوب ({@link ReportType}) وحضّر
 * {@link ReportRequest}، والخدمة تتكفّل باختيار المزوّد المناسب (PDF عام / Excel عام / Jasper).
 *
 * <p>أي فشل أثناء التوليد بيوصل كـ {@link ReportException}، حتى لو أصله استثناء غير متوقّع
 * جاي من مكتبة تحتية؛ كده الـ {@code catch (ReportException)} عند المستخدم بيمسك كل الحالات فعلًا.
 * الاستثناء الوحيد هو التحقق من المعطيات نفسها، وده بيرمي {@link IllegalArgumentException}
 * لأنه غلطة برمجية عند المستدعي مش فشل في التوليد.</p>
 */
public final class ReportService {

    private ReportService() {}

    public static byte[] generate(ReportType type, ReportRequest<?> request) throws ReportException {
        if (type == null) {
            throw new IllegalArgumentException("Report type is required.");
        }
        if (request == null) {
            throw new IllegalArgumentException("Report request is required.");
        }
        try {
            return provider(type).generate(request);
        } catch (RuntimeException e) {
            // مثال: خاصية مش موجودة على الكائن، أو مسار قالب مش صالح كمسار ملف
            throw new ReportException("Failed to generate " + type + " report: " + e.getMessage(), e);
        }
    }

    public static void generateToFile(ReportType type, ReportRequest<?> request, Path outputPath) throws ReportException {
        if (outputPath == null) {
            throw new IllegalArgumentException("Output path is required.");
        }
        byte[] bytes = generate(type, request);
        try {
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }
            Files.write(outputPath, bytes);
        } catch (IOException e) {
            throw new ReportException("Failed to write report to file: " + outputPath, e);
        }
    }

    private static ReportProvider provider(ReportType type) {
        return switch (type) {
            case PDF -> new PdfReportProvider();
            case EXCEL -> new ExcelReportProvider();
            case JASPER -> new JasperReportProvider();
        };
    }
}
