package com.smart.report;

/**
 * يصف عمود واحد في تقرير PDF/Excel العام: اسم الحقل (getter أو مكوّن record في الكائن) والعنوان
 * الظاهر للمستخدم في رأس الجدول، ومعاهم ثلاث خيارات اختيارية:
 *
 * <pre>{@code
 * ColumnDefinition.of("amount", "المبلغ").withFormat("#,##0.00").withAlign(Alignment.END).withWeight(2)
 * ColumnDefinition.of("date", "التاريخ").withFormat("dd/MM/yyyy")
 * }</pre>
 *
 * <p>تقرير الـ Excel بياخد النمط (محوَّلًا لصيغة Excel، والقيمة بتفضل رقم أو تاريخ حقيقي) والمحاذاة،
 * وبيتجاهل الوزن: عرض أعمدته على قد محتواها.</p>
 *
 * @param fieldName اسم الحقل في الكائن
 * @param header    عنوان العمود؛ الفاضي بياخد اسم الحقل
 * @param format    نمط تنسيق للأرقام ({@link java.text.DecimalFormat}: {@code "#,##0.00"}) أو للتواريخ
 *                  ({@link java.time.format.DateTimeFormatter}: {@code "dd/MM/yyyy"})؛ {@code null} = التنسيق
 *                  الافتراضي (فواصل الآلاف، و{@code yyyy-MM-dd}). باقي أنواع القيم بتتطبع زي ما هي.
 * @param align     محاذاة قيم العمود؛ {@code null} = تلقائي (الأرقام لنهاية السطر، والباقي لبدايته)
 * @param weight    نصيب العمود من عرض الجدول منسوبًا لباقي الأعمدة؛ الافتراضي 1
 */
public record ColumnDefinition(String fieldName, String header, String format, Alignment align, float weight) {

    public ColumnDefinition {
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName is required.");
        }
        if (header == null || header.isBlank()) {
            header = fieldName;
        }
        if (format != null && format.isBlank()) {
            format = null;
        }
        if (!(weight > 0) || Float.isInfinite(weight)) {
            throw new IllegalArgumentException("Column weight must be a positive number: " + weight);
        }
    }

    /** عمود بلا خيارات: التنسيق والمحاذاة تلقائيين ووزنه 1. */
    public ColumnDefinition(String fieldName, String header) {
        this(fieldName, header, null, null, 1f);
    }

    public static ColumnDefinition of(String fieldName, String header) {
        return new ColumnDefinition(fieldName, header);
    }

    /** نفس العمود بنمط تنسيق للأرقام أو التواريخ؛ {@code null} يرجّع التنسيق الافتراضي. */
    public ColumnDefinition withFormat(String pattern) {
        return new ColumnDefinition(fieldName, header, pattern, align, weight);
    }

    /** نفس العمود بمحاذاة محددة؛ {@code null} يرجّعها تلقائية. */
    public ColumnDefinition withAlign(Alignment alignment) {
        return new ColumnDefinition(fieldName, header, format, alignment, weight);
    }

    /** نفس العمود بوزن تاني من عرض الجدول (2 = ضعف العمود العادي). */
    public ColumnDefinition withWeight(float value) {
        return new ColumnDefinition(fieldName, header, format, align, value);
    }
}
