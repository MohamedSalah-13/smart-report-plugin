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
        return provider(type).generate(request);
    }

    public static void generateToFile(ReportType type, ReportRequest<?> request, Path outputPath) throws ReportException {
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
