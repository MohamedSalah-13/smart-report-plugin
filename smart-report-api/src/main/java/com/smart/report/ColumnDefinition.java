package com.smart.report;

/**
 * Describes one column in the generic PDF/Excel report: the field name (a getter or a record component on
 * the object) and the title the user sees in the table header, plus four optional settings:
 *
 * <pre>{@code
 * ColumnDefinition.of("amount", "المبلغ").withFormat("#,##0.00").withAlign(Alignment.END).withWeight(2)
 * ColumnDefinition.of("date", "التاريخ").withFormat("dd/MM/yyyy")
 * ColumnDefinition.of("amount", "المبلغ").withFormat("#,##0.00").withTotal()
 * }</pre>
 *
 * <p>The Excel report uses the pattern (converted to an Excel format; the value stays a real number or
 * date), the alignment and the total. It ignores the weight: its columns are as wide as their content.</p>
 *
 * <p><b>Compatibility:</b> the {@code total} component was added last. The five-argument constructor (which
 * used to be the canonical one) still exists with the same signature and makes a column without a total, so
 * programs compiled against the previous version run without recompiling. The only thing that needs a code
 * change is deconstructing the record with a pattern
 * ({@code case ColumnDefinition(var f, var h, var fmt, var a, var w)}): it now has six components.</p>
 *
 * @param fieldName the field name on the object
 * @param header    the column title; a blank one takes the field name
 * @param format    a format pattern for numbers ({@link java.text.DecimalFormat}: {@code "#,##0.00"}) or for
 *                  dates ({@link java.time.format.DateTimeFormatter}: {@code "dd/MM/yyyy"}); {@code null} = the
 *                  default format (thousands separators, and {@code yyyy-MM-dd}). Other value types print as is.
 * @param align     the alignment of the column's values; {@code null} = automatic (numbers to the end of the
 *                  line, everything else to the start)
 * @param weight    the column's share of the table width, relative to the other columns; the default is 1
 * @param total     whether the column is summed in a total row under the table. The values must be numbers
 *                  (empty ones add nothing); see {@link com.smart.report.util.Totals}
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
     * A column without a total. This was the canonical constructor before {@code total} was added. It keeps
     * the same signature for programs compiled against it.
     */
    public ColumnDefinition(String fieldName, String header, String format, Alignment align, float weight) {
        this(fieldName, header, format, align, weight, false);
    }

    /** A column with no options: automatic format and alignment, and a weight of 1. */
    public ColumnDefinition(String fieldName, String header) {
        this(fieldName, header, null, null, 1f, false);
    }

    public static ColumnDefinition of(String fieldName, String header) {
        return new ColumnDefinition(fieldName, header);
    }

    /** The same column with a format pattern for numbers or dates; {@code null} restores the default format. */
    public ColumnDefinition withFormat(String pattern) {
        return new ColumnDefinition(fieldName, header, pattern, align, weight, total);
    }

    /** The same column with a set alignment; {@code null} makes it automatic again. */
    public ColumnDefinition withAlign(Alignment alignment) {
        return new ColumnDefinition(fieldName, header, format, alignment, weight, total);
    }

    /** The same column with a different share of the table width (2 = twice a normal column). */
    public ColumnDefinition withWeight(float value) {
        return new ColumnDefinition(fieldName, header, format, align, value, total);
    }

    /**
     * The same column, summed in a total row under the table with the column's own format. In the PDF the sum
     * is computed exactly ({@link java.math.BigDecimal}); in Excel it is a {@code SUM} formula over the data rows.
     */
    public ColumnDefinition withTotal() {
        return new ColumnDefinition(fieldName, header, format, align, weight, true);
    }
}
