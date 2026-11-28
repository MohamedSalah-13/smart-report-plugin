package com.smart.report;

/**
 * Provider for one report type. Each module (smart-report-pdf / -excel / -jasper) registers its provider in
 * {@code META-INF/services/com.smart.report.ReportProvider}, and {@link ReportService} finds it
 * through {@link java.util.ServiceLoader}. So a project only takes the modules it needs.
 */
public interface ReportProvider {

    /** The report type this provider generates. */
    ReportType type();

    byte[] generate(ReportRequest<?> request) throws ReportException;
}
