package com.smart.report;

public interface ReportProvider {

    byte[] generate(ReportRequest<?> request) throws ReportException;
}
