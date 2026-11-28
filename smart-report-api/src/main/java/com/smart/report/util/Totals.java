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
 * The total row in the generic report ({@link ColumnDefinition#withTotal()}): its label and where it goes, and
 * which values can be summed. A value that cannot be summed gives the same error in the PDF and in Excel
 * ({@link #check}).
 *
 * <p>The sum itself differs: the PDF prints the exact sum ({@link #add}), while Excel writes a {@code SUM} formula
 * whose value is computed by Excel itself ({@code double} numbers, in order) over the values in its cells.
 * Otherwise the saved value would change as soon as Excel recalculates.</p>
 *
 * <ul>
 *     <li><b>Exact sum:</b> each value is converted to {@link BigDecimal} and summed without rounding, so
 *         {@code BigDecimal} amounts with any number of digits come out right to the last cent.</li>
 *     <li><b>{@code double} and {@code float}</b> are summed by the decimal number that represents them in the cell
 *         (the shortest form, {@link BigDecimal#valueOf(double)}), not by their binary value: {@code 0.1 + 0.2} =
 *         {@code 0.3}, not {@code 0.30000000000000004}.</li>
 *     <li><b>An empty cell</b> ({@code null}) adds nothing.</li>
 *     <li><b>Any value that is not a number</b> (text, a date, a {@code Boolean}) or a number with no value
 *         ({@code NaN}, infinity) throws {@link IllegalArgumentException}, and the report names the column and the
 *         row number: a total that silently skips a value gives a wrong number that nobody will notice.</li>
 * </ul>
 *
 * <p>The subtotal under each group ({@link ReportRequest.Builder#groupBy}) follows the same rules:
 * {@link #sameGroup} tells where a group starts, and {@link #subtotalLabel} gives its label.</p>
 */
public final class Totals {

    /** The total row label in an Arabic report, unless the program sets another one. */
    public static final String ARABIC_LABEL = "الإجمالى";
    /** The total row label in an English report, unless the program sets another one. */
    public static final String ENGLISH_LABEL = "Total";

    /**
     * The text before the group value in the subtotal row of an Arabic report ({@link ReportRequest.Builder#groupBy}):
     * «إجمالى: أحمد».
     */
    public static final String ARABIC_SUBTOTAL_LABEL = "إجمالى: ";
    /** The text before the group value in the subtotal row of an English report: «Total: Ahmed». */
    public static final String ENGLISH_SUBTOTAL_LABEL = "Total: ";
    /** Stands in for the group value in its row label when the value is empty ({@code null} or a blank string). */
    public static final String NO_GROUP_VALUE = "—";

    private Totals() {}

    /** Whether the request has at least one summed column. */
    public static boolean any(ReportRequest<?> request) {
        for (ColumnDefinition column : request.columns()) {
            if (column.total()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The total row label: the one the program set ({@link ReportRequest.Builder#totalsLabel}) if any; otherwise
     * {@link #ARABIC_LABEL} in a right-to-left report and {@link #ENGLISH_LABEL} in a left-to-right one.
     */
    public static String label(ReportRequest<?> request, boolean rightToLeft) {
        if (request.totalsLabel() != null) {
            return request.totalsLabel();
        }
        return rightToLeft ? ARABIC_LABEL : ENGLISH_LABEL;
    }

    /**
     * Where the total label goes: the first column that is not summed, or {@code -1} if all columns are summed (the
     * row has no label).
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
     * The subtotal row label: the text the program set ({@link ReportRequest.Builder#subtotalLabel}) if any;
     * otherwise {@link #ARABIC_SUBTOTAL_LABEL} or {@link #ENGLISH_SUBTOTAL_LABEL} by direction. It is followed by the
     * group value as it appears in the report, or {@link #NO_GROUP_VALUE} if empty, so the label is not confused with
     * the grand total label.
     *
     * @param groupValue the group value as text in the report's format; {@code null} or blank = no value
     */
    public static String subtotalLabel(ReportRequest<?> request, String groupValue, boolean rightToLeft) {
        return subtotalPrefix(request, rightToLeft)
                + (groupValue == null || groupValue.isBlank() ? NO_GROUP_VALUE : groupValue);
    }

    /**
     * Only the text before the group value: the one the program set ({@link ReportRequest.Builder#subtotalLabel}) if
     * any; otherwise {@link #ARABIC_SUBTOTAL_LABEL} or {@link #ENGLISH_SUBTOTAL_LABEL} by direction. For a report that
     * writes the value in a cell of its own (Excel).
     */
    public static String subtotalPrefix(ReportRequest<?> request, boolean rightToLeft) {
        return request.subtotalLabel() != null ? request.subtotalLabel()
                : rightToLeft ? ARABIC_SUBTOTAL_LABEL : ENGLISH_SUBTOTAL_LABEL;
    }

    /**
     * Whether the two rows are in the same group: the same value ({@link Object#equals}), with decimals compared by
     * value: {@code 1.0} and {@code 1.00} ({@link BigDecimal}) are one group. {@code null} and {@code null} are one
     * group.
     */
    public static boolean sameGroup(Object previous, Object current) {
        if (previous instanceof BigDecimal a && current instanceof BigDecimal b) {
            return a.compareTo(b) == 0;
        }
        return java.util.Objects.equals(previous, current);
    }

    /** The index of the group column among the columns if the property is one of them; otherwise {@code -1}. */
    public static int groupColumn(ReportRequest<?> request) {
        if (request.groupBy() == null) {
            return -1;
        }
        for (int i = 0; i < request.columns().size(); i++) {
            if (request.groupBy().equals(request.columns().get(i).fieldName())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Groups need something to sum: {@link ReportRequest.Builder#groupBy} without any summed column would produce rows
     * of text with no numbers, so this throws instead of silently ignoring the option.
     *
     * @throws ReportException if there are groups and no {@link ColumnDefinition#withTotal()} column
     */
    public static void checkGroups(ReportRequest<?> request) throws ReportException {
        if ((request.groupHeader() || request.pageBreakPerGroup()) && request.groupBy() == null) {
            throw new ReportException("groupHeader and pageBreakPerGroup need groupBy: say which property the rows"
                    + " are grouped by.");
        }
        if (request.groupBy() != null && !any(request)) {
            throw new ReportException("groupBy(\"" + request.groupBy() + "\") puts a subtotal under each group, so"
                    + " at least one column needs withTotal().");
        }
    }

    /**
     * Adds a value from a row to its column's sum.
     *
     * @param row the element's index in the data (from zero); the message counts from 1
     * @throws ReportException with the column name and the row number, if the value cannot be summed ({@link #amount})
     */
    public static BigDecimal add(BigDecimal sum, Object value, ColumnDefinition column, int row) throws ReportException {
        try {
            return sum.add(amount(value));
        } catch (IllegalArgumentException e) {
            throw notANumber(column, row, e);
        }
    }

    /**
     * Checks that the value can be summed, without summing it. For a report that computes its sum its own way
     * (Excel), so a wrong value gives the same message in every type.
     *
     * @param row the element's index in the data (from zero); the message counts from 1
     * @throws ReportException with the column name and the row number, if the value cannot be summed ({@link #amount})
     */
    public static void check(Object value, ColumnDefinition column, int row) throws ReportException {
        try {
            amount(value);
        } catch (IllegalArgumentException e) {
            throw notANumber(column, row, e);
        }
    }

    private static ReportException notANumber(ColumnDefinition column, int row, IllegalArgumentException cause) {
        return new ReportException("Column '" + column.fieldName() + "', row " + (row + 1) + ": "
                + cause.getMessage(), cause);
    }

    /**
     * The value as a number that sums exactly; {@code null} = zero.
     *
     * @throws IllegalArgumentException if the value is not a number, or is {@code NaN} or infinity
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
