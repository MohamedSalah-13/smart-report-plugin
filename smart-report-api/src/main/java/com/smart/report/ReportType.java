package com.smart.report;

/** The report types. Each type lives in its own Maven module. */
public enum ReportType {
    PDF("smart-report-pdf"),
    EXCEL("smart-report-excel"),
    JASPER("smart-report-jasper");

    private final String module;

    ReportType(String module) {
        this.module = module;
    }

    /** The name (artifactId) of the module that holds the provider for this type. */
    public String module() {
        return module;
    }
}
