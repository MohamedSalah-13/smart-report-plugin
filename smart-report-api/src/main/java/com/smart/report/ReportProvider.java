package com.smart.report;

/**
 * مزوّد نوع واحد من التقارير. كل وحدة (smart-report-pdf / -excel / -jasper) بتسجّل مزوّدها في
 * {@code META-INF/services/com.smart.report.ReportProvider}، و{@link ReportService} بيلاقيه
 * عن طريق {@link java.util.ServiceLoader} — فالمشروع بياخد الوحدات اللي محتاجها بس.
 */
public interface ReportProvider {

    /** نوع التقرير اللي المزوّد ده بيولّده. */
    ReportType type();

    byte[] generate(ReportRequest<?> request) throws ReportException;
}
