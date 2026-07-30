package com.smart.report;

/**
 * يصف عمود واحد في تقرير PDF/Excel العام: اسم الحقل (getter أو مكوّن record في الكائن)
 * والعنوان الظاهر للمستخدم في رأس الجدول.
 */
public record ColumnDefinition(String fieldName, String header) {

    public ColumnDefinition {
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName is required.");
        }
        if (header == null || header.isBlank()) {
            header = fieldName;
        }
    }

    public static ColumnDefinition of(String fieldName, String header) {
        return new ColumnDefinition(fieldName, header);
    }
}
