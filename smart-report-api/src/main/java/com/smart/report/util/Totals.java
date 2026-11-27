package com.smart.report.util;

import com.smart.report.ColumnDefinition;
import com.smart.report.ReportException;
import com.smart.report.ReportRequest;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

/**
 * صف الإجمالي في التقرير العام ({@link ColumnDefinition#withTotal()}). القاعدة واحدة لكل الأنواع، فنفس
 * الطلب بيطلع نفس الإجمالي في الـ PDF والـ Excel، أو نفس الخطأ.
 *
 * <ul>
 *     <li><b>المجموع بالظبط:</b> كل قيمة بتتحوّل لـ {@link BigDecimal} وبتتجمع من غير تقريب، فمبالغ
 *         {@code BigDecimal} بأي عدد خانات بتطلع صح لآخر قرش.</li>
 *     <li><b>{@code double} و{@code float}</b> بيتجمعوا بالرقم العشري اللي بيمثلهم في الخلية (أقصر صورة،
 *         {@link BigDecimal#valueOf(double)})، مش بقيمتهم الثنائية: {@code 0.1 + 0.2} = {@code 0.3}، مش
 *         {@code 0.30000000000000004}.</li>
 *     <li><b>الخلية الفاضية</b> ({@code null}) ما بتزوّدش حاجة.</li>
 *     <li><b>أي قيمة مش رقم</b> (نص، تاريخ، {@code Boolean}) أو رقم ملوش قيمة ({@code NaN}، ما لا نهاية)
 *         بترمي {@link IllegalArgumentException}، والتقرير بيقول اسم العمود ورقم الصف: الإجمالي اللي
 *         بيتخطى قيمة في صمت بيطلع رقم غلط محدش هياخد باله منه.</li>
 * </ul>
 */
public final class Totals {

    /** كلمة صف الإجمالي في التقرير العربي، لو البرنامج ما حددش غيرها. */
    public static final String ARABIC_LABEL = "الإجمالى";
    /** كلمة صف الإجمالي في التقرير الإنجليزي، لو البرنامج ما حددش غيرها. */
    public static final String ENGLISH_LABEL = "Total";

    private Totals() {}

    /** هل في الطلب عمود واحد على الأقل متجمّع؟ */
    public static boolean any(ReportRequest<?> request) {
        for (ColumnDefinition column : request.columns()) {
            if (column.total()) {
                return true;
            }
        }
        return false;
    }

    /**
     * كلمة صف الإجمالي: اللي البرنامج حددها ({@link ReportRequest.Builder#totalsLabel}) لو موجودة، وإلا
     * {@link #ARABIC_LABEL} في التقرير اللي من اليمين و{@link #ENGLISH_LABEL} في اللي من الشمال.
     */
    public static String label(ReportRequest<?> request, boolean rightToLeft) {
        if (request.totalsLabel() != null) {
            return request.totalsLabel();
        }
        return rightToLeft ? ARABIC_LABEL : ENGLISH_LABEL;
    }

    /**
     * مكان كلمة الإجمالي: أول عمود مش متجمّع، أو {@code -1} لو كل الأعمدة متجمّعة (الصف من غير كلمة).
     */
    public static int labelColumn(ReportRequest<?> request) {
        for (int i = 0; i < request.columns().size(); i++) {
            if (!request.columns().get(i).total()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * يزوّد قيمة من صف على مجموع عمودها.
     *
     * @param row مكان العنصر في البيانات (من صفر)؛ الرسالة بتعدّ من 1
     * @throws ReportException باسم العمود ورقم الصف، لو القيمة ما تتجمعش ({@link #amount})
     */
    public static BigDecimal add(BigDecimal sum, Object value, ColumnDefinition column, int row) throws ReportException {
        try {
            return sum.add(amount(value));
        } catch (IllegalArgumentException e) {
            throw new ReportException("Column '" + column.fieldName() + "', row " + (row + 1) + ": "
                    + e.getMessage(), e);
        }
    }

    /**
     * القيمة كرقم يتجمع بالظبط؛ {@code null} = صفر.
     *
     * @throws IllegalArgumentException لو القيمة مش رقم، أو {@code NaN} أو ما لا نهاية
     */
    public static BigDecimal amount(Object value) {
        return switch (value) {
            case null -> BigDecimal.ZERO;
            case BigDecimal decimal -> decimal;
            case BigInteger integer -> new BigDecimal(integer);
            case Double number -> {
                finite(number);
                // نفس تحويل خلية الـ PDF: 7200.0 بيتجمع 7200 مش 7200.0، فالإجمالي ما ياخدش خانة زيادة.
                yield BigDecimal.valueOf(number).stripTrailingZeros();
            }
            case Float number -> {
                finite(number.doubleValue());
                yield new BigDecimal(number.toString()).stripTrailingZeros();
            }
            case Byte number -> BigDecimal.valueOf(number);
            case Short number -> BigDecimal.valueOf(number);
            case Integer number -> BigDecimal.valueOf(number);
            case Long number -> BigDecimal.valueOf(number);
            case AtomicInteger number -> BigDecimal.valueOf(number.get());
            case AtomicLong number -> BigDecimal.valueOf(number.get());
            case LongAdder number -> BigDecimal.valueOf(number.sum());
            case LongAccumulator number -> BigDecimal.valueOf(number.get());
            case Number number -> {
                // نوع أرقام تاني (DoubleAdder، أو نوع خاص بالبرنامج): نصه لو رقم عشري، وإلا قيمته كـ double.
                try {
                    yield new BigDecimal(number.toString());
                } catch (NumberFormatException e) {
                    double asDouble = number.doubleValue();
                    finite(asDouble);
                    yield BigDecimal.valueOf(asDouble).stripTrailingZeros();
                }
            }
            default -> throw new IllegalArgumentException("only numbers can be totalled, but the value is the "
                    + value.getClass().getSimpleName() + " \"" + value + "\".");
        };
    }

    private static void finite(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("the value " + value + " cannot be totalled.");
        }
    }
}
