package com.smart.report.util;

import com.smart.report.ColumnDefinition;
import com.smart.report.ReportRequest;

/**
 * The direction of the generic report: right-to-left if the title or any column header contains Arabic,
 * unless the program sets it explicitly. The rule is the same for all types, so the same request produces a
 * PDF and an Excel file in the same direction.
 */
public final class TextDirection {

    private TextDirection() {}

    /** Whether the text has any character from the Arabic blocks (presentation forms included); {@code null} = no. */
    public static boolean containsArabic(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= '؀' && c <= 'ۿ')        // Arabic
                    || (c >= 'ݐ' && c <= 'ݿ')  // Arabic Supplement
                    || (c >= 'ﭐ' && c <= '﷿')  // Presentation Forms-A
                    || (c >= 'ﹰ' && c <= '﻿')) { // Presentation Forms-B
                return true;
            }
        }
        return false;
    }

    /**
     * The request's direction: the {@code Boolean} under the key {@code parameter} in {@code request.parameters()}
     * if present; otherwise right-to-left if the title or any column header contains Arabic.
     */
    public static boolean rightToLeft(ReportRequest<?> request, String parameter) {
        if (parameter != null && request.parameters().get(parameter) instanceof Boolean chosen) {
            return chosen;
        }
        if (containsArabic(request.title())) {
            return true;
        }
        for (ColumnDefinition column : request.columns()) {
            if (containsArabic(column.header())) {
                return true;
            }
        }
        return false;
    }
}
