package com.smart.report;

/**
 * يُرمى عند فشل توليد أي نوع تقرير (PDF / Excel / Jasper): قالب غير موجود، بيانات غير صالحة،
 * خطأ IO أثناء الكتابة... استبدال RuntimeException العامة بهذا النوع يسمح للمشاريع المستهلكة
 * بالتعامل مع أخطاء توليد التقارير بشكل مميز عن أي أخطاء runtime أخرى.
 */
public class ReportException extends Exception {

    public ReportException(String message) {
        super(message);
    }

    public ReportException(String message, Throwable cause) {
        super(message, cause);
    }
}
