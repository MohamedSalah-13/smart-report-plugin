package com.smart.report;

/** أنواع التقارير، وكل نوع في وحدة Maven لوحده. */
public enum ReportType {
    PDF("smart-report-pdf"),
    EXCEL("smart-report-excel"),
    JASPER("smart-report-jasper");

    private final String module;

    ReportType(String module) {
        this.module = module;
    }

    /** اسم الوحدة (artifactId) اللي فيها مزوّد هذا النوع. */
    public String module() {
        return module;
    }
}
