package com.smart.report;

/**
 * Thrown when generating any report type (PDF / Excel / Jasper) fails: a missing template, invalid data,
 * an IO error while writing, and so on. Using this type instead of a plain RuntimeException lets consuming
 * projects handle report generation errors separately from any other runtime errors.
 */
public class ReportException extends Exception {

    public ReportException(String message) {
        super(message);
    }

    public ReportException(String message, Throwable cause) {
        super(message, cause);
    }
}
