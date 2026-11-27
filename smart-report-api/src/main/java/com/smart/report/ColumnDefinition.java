package com.smart.report;

/**
 * يصف عمود واحد في تقرير PDF/Excel العام: اسم الحقل (getter أو مكوّن record في الكائن) والعنوان
 * الظاهر للمستخدم في رأس الجدول، ومعاهم أربع خيارات اختيارية:
 *
 * <pre>{@code
 * ColumnDefinition.of("amount", "المبلغ").withFormat("#,##0.00").withAlign(Alignment.END).withWeight(2)
 * ColumnDefinition.of("date", "التاريخ").withFormat("dd/MM/yyyy")
 * ColumnDefinition.of("amount", "المبلغ").withFormat("#,##0.00").withTotal()
 * }</pre>
 *
 * <p>تقرير الـ Excel بياخد النمط (محوَّلًا لصيغة Excel، والقيمة بتفضل رقم أو تاريخ حقيقي) والمحاذاة
 * والإجمالي، وبيتجاهل الوزن: عرض أعمدته على قد محتواها.</p>
 *
 * <p><b>التوافق:</b> المكوّن {@code total} اتضاف في الآخر، والـ constructor اللي بخمس قيم (اللي كان هو
 * الأساسي) لسه موجود بنفس توقيعه وبيعمل عمود من غير إجمالي، فالبرامج المترجمة على النسخة اللي قبله
 * بتشتغل من غير إعادة ترجمة. الحاجة الوحيدة اللي بتحتاج تعديل في الكود هي فك الـ record بنمط
 * ({@code case ColumnDefinition(var f, var h, var fmt, var a, var w)}): بقى ليه ست مكوّنات.</p>
 *
 * @param fieldName اسم الحقل في الكائن
 * @param header    عنوان العمود؛ الفاضي بياخد اسم الحقل
 * @param format    نمط تنسيق للأرقام ({@link java.text.DecimalFormat}: {@code "#,##0.00"}) أو للتواريخ
 *                  ({@link java.time.format.DateTimeFormatter}: {@code "dd/MM/yyyy"})؛ {@code null} = التنسيق
 *                  الافتراضي (فواصل الآلاف، و{@code yyyy-MM-dd}). باقي أنواع القيم بتتطبع زي ما هي.
 * @param align     محاذاة قيم العمود؛ {@code null} = تلقائي (الأرقام لنهاية السطر، والباقي لبدايته)
 * @param weight    نصيب العمود من عرض الجدول منسوبًا لباقي الأعمدة؛ الافتراضي 1
 * @param total     هل العمود بيتجمع في صف إجمالي تحت الجدول؟ القيم لازم تكون أرقام (الفاضية ما بتزوّدش
 *                  حاجة)؛ شوف {@link com.smart.report.util.Totals}
 */
public record ColumnDefinition(String fieldName, String header, String format, Alignment align, float weight,
                               boolean total) {

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

    /**
     * عمود من غير إجمالي. ده كان الـ constructor الأساسي قبل ما {@code total} يتضاف، وفاضل بنفس توقيعه
     * عشان البرامج المترجمة عليه.
     */
    public ColumnDefinition(String fieldName, String header, String format, Alignment align, float weight) {
        this(fieldName, header, format, align, weight, false);
    }

    /** عمود بلا خيارات: التنسيق والمحاذاة تلقائيين ووزنه 1. */
    public ColumnDefinition(String fieldName, String header) {
        this(fieldName, header, null, null, 1f, false);
    }

    public static ColumnDefinition of(String fieldName, String header) {
        return new ColumnDefinition(fieldName, header);
    }

    /** نفس العمود بنمط تنسيق للأرقام أو التواريخ؛ {@code null} يرجّع التنسيق الافتراضي. */
    public ColumnDefinition withFormat(String pattern) {
        return new ColumnDefinition(fieldName, header, pattern, align, weight, total);
    }

    /** نفس العمود بمحاذاة محددة؛ {@code null} يرجّعها تلقائية. */
    public ColumnDefinition withAlign(Alignment alignment) {
        return new ColumnDefinition(fieldName, header, format, alignment, weight, total);
    }

    /** نفس العمود بوزن تاني من عرض الجدول (2 = ضعف العمود العادي). */
    public ColumnDefinition withWeight(float value) {
        return new ColumnDefinition(fieldName, header, format, align, value, total);
    }

    /**
     * نفس العمود متجمّع في صف إجمالي تحت الجدول، بنمط العمود نفسه. في الـ PDF المجموع بيتحسب بالظبط
     * ({@link java.math.BigDecimal})، وفي الـ Excel بيبقى معادلة {@code SUM} على صفوف البيانات.
     */
    public ColumnDefinition withTotal() {
        return new ColumnDefinition(fieldName, header, format, align, weight, true);
    }
}
