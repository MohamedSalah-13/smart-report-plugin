package com.smart.report.pdf;

import com.smart.report.Alignment;
import com.smart.report.ColumnDefinition;
import com.smart.report.ReportException;
import com.smart.report.ReportProvider;
import com.smart.report.ReportRequest;
import com.smart.report.ReportType;
import com.smart.report.util.BeanPropertyReader;
import com.smart.report.util.TextDirection;
import com.smart.report.util.Totals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Generates a generic tabular PDF report (title + column header + data rows) from any List of objects plus a list
 * of ColumnDefinition, without any template. The drawing itself is done by {@link PdfDocument}: the table follows
 * the reading direction, long text wraps instead of being cut, and the column header repeats on every page.
 *
 * <p><b>Direction:</b> right to left if the title or any column header contains Arabic, otherwise left to right,
 * unless {@link #RIGHT_TO_LEFT} is set in {@code parameters}.</p>
 *
 * <p><b>Values:</b> numbers get thousands separators and dates use {@code yyyy-MM-dd} ({@link CellText}). A column
 * whose values are all numbers aligns to the end of the line, so the numbers line up under each other. The options
 * of each column in {@link ColumnDefinition} (format pattern, alignment, weight) override the defaults; a pattern
 * that does not fit a value throws a {@link ReportException} that names the column.</p>
 *
 * <p><b>Total:</b> if a column is totalled ({@link ColumnDefinition#withTotal()}), a totals row goes under the
 * table: each sum under its column in the column's pattern, and the label ({@link Totals#label}) under the first
 * column that is not totalled. The sum is exact ({@link java.math.BigDecimal}). A value that is not a number in a
 * totalled column throws a {@link ReportException} with the column name and the row number (see {@link Totals}).
 * With no data, the total is zero.</p>
 *
 * <p><b>Subtotal:</b> with {@link ReportRequest.Builder#groupBy}, a totals row goes under each run of consecutive
 * rows with the same value. It looks like the totals row and its label is {@link Totals#subtotalLabel}; the grand
 * total stays at the end as before. A subtotal never lands alone at the top of a page: the last row of its group
 * moves with it.</p>
 *
 * <p><b>Paper:</b> A4 portrait, unless {@link #PAPER} is set in {@code parameters} to a {@link Paper} object or its
 * name ({@code "A5"}, {@code "A4-landscape"}). With {@link #LANDSCAPE_FROM_COLUMNS}, the paper turns to landscape
 * when the number of columns reaches that number, so a report with many columns is not squeezed.</p>
 *
 * <p><b>Font:</b> the global font ({@link PdfFontResolver}), unless {@link #FONT} is set to a {@link PdfFont}
 * object. {@link #FAIL_ON_MISSING_GLYPHS} makes a character that is in no font throw a {@link ReportException}
 * instead of printing {@code ?}.</p>
 *
 * <p>For a document with more than one table, a letterhead or a summary, use {@link PdfDocument} directly. For
 * reports with a custom visual design, use the smart-report-jasper module.</p>
 */
public final class PdfReportProvider implements ReportProvider {

    /** Key in {@code ReportRequest.parameters()}: a {@link Paper} or its name. */
    public static final String PAPER = "smart.report.pdf.paper";
    /**
     * Key in {@code ReportRequest.parameters()}: a number of columns ({@code Integer}, or its text). If the report
     * has that many columns or more, the paper ({@link #PAPER} or A4) turns to landscape
     * ({@link Paper#landscape()}). Below that, the paper stays as it is, and a thermal roll never turns.
     */
    public static final String LANDSCAPE_FROM_COLUMNS = "smart.report.pdf.landscapeFromColumns";
    /** Key in {@code ReportRequest.parameters()}: a {@code Boolean} that fixes the report direction. */
    public static final String RIGHT_TO_LEFT = "smart.report.pdf.rightToLeft";
    /**
     * Key in {@code ReportRequest.parameters()}: a {@link PdfFont} for this report instead of the global font
     * ({@link PdfDocument#font(PdfFont)}).
     */
    public static final String FONT = "smart.report.pdf.documentFont";
    /**
     * Key in {@code ReportRequest.parameters()}: {@code Boolean.TRUE} = a character that is in no font throws a
     * {@link ReportException} instead of printing as {@code ?} ({@link PdfDocument#failOnMissingGlyphs(boolean)}).
     */
    public static final String FAIL_ON_MISSING_GLYPHS = "smart.report.pdf.failOnMissingGlyphs";
    /**
     * Key in {@code ReportRequest.parameters()}: {@code Boolean.TRUE} = negative amounts in red in all columns
     * ({@link PdfTable#negativeInRed}), for example an account statement with a credit balance.
     */
    public static final String NEGATIVE_IN_RED = "smart.report.pdf.negativeInRed";
    /**
     * Key in {@code ReportRequest.parameters()}: {@code Boolean.TRUE} = all numbers in Arabic-Indic digits
     * ({@code ١٢٣}), as in {@link PdfDocument#arabicIndicDigits}.
     */
    public static final String ARABIC_INDIC_DIGITS = "smart.report.pdf.arabicIndicDigits";

    @Override
    public ReportType type() {
        return ReportType.PDF;
    }

    @Override
    public byte[] generate(ReportRequest<?> request) throws ReportException {
        List<ColumnDefinition> columns = request.columns();
        if (columns.isEmpty()) {
            throw new ReportException("At least one column is required to generate a PDF report.");
        }

        Paper paper = paper(request.parameters().get(PAPER));
        Integer landscapeFrom = columnCount(request.parameters().get(LANDSCAPE_FROM_COLUMNS));
        if (landscapeFrom != null && columns.size() >= landscapeFrom) {
            paper = paper.landscape();
        }
        boolean rightToLeft = TextDirection.rightToLeft(request, RIGHT_TO_LEFT);
        PdfDocument document = rightToLeft ? PdfDocument.rtl(paper) : PdfDocument.ltr(paper);
        document.font(font(request.parameters().get(FONT)))
                .failOnMissingGlyphs(isTrue(request.parameters().get(FAIL_ON_MISSING_GLYPHS)))
                .arabicIndicDigits(isTrue(request.parameters().get(ARABIC_INDIC_DIGITS)));
        if (request.title() != null && !request.title().isBlank()) {
            document.title(singleLine(request.title()));
        }

        Totals.checkGroups(request);
        // القيم بتتقرا الأول: محاذاة العمود بتتحدد من نوع قيمه كلها.
        int count = columns.size();
        List<Object[]> rows = new ArrayList<>(request.data().size());
        boolean[] numeric = new boolean[count];
        boolean[] other = new boolean[count];
        BigDecimal[] sums = new BigDecimal[count];
        Arrays.fill(sums, BigDecimal.ZERO);
        List<Group> groups = new ArrayList<>();
        for (Object item : request.data()) {
            if (request.groupBy() != null) {
                Object value = BeanPropertyReader.read(item, request.groupBy());
                if (groups.isEmpty() || !Totals.sameGroup(groups.get(groups.size() - 1).value(), value)) {
                    groups.add(new Group(value, rows.size(), count));
                }
            }
            Group group = groups.isEmpty() ? null : groups.get(groups.size() - 1);
            Object[] values = new Object[count];
            for (int i = 0; i < count; i++) {
                ColumnDefinition column = columns.get(i);
                values[i] = BeanPropertyReader.read(item, column.fieldName());
                if (values[i] instanceof Number) {
                    numeric[i] = true;
                } else if (values[i] != null) {
                    other[i] = true;
                }
                if (column.total()) {
                    sums[i] = Totals.add(sums[i], values[i], column, rows.size());
                    if (group != null) {
                        group.sums()[i] = group.sums()[i].add(Totals.amount(values[i]));
                    }
                }
            }
            rows.add(values);
        }

        boolean negativeInRed = isTrue(request.parameters().get(NEGATIVE_IN_RED));
        PdfTable table = table(columns, numeric, other, negativeInRed);
        CellText text = new CellText(rightToLeft ? Locale.forLanguageTag("ar") : Locale.ENGLISH);
        // عنوان لكل مجموعة أو صفحة لكل مجموعة: جدول لكل مجموعة، ورأس أعمدته بيتكرر تحت عنوانها.
        boolean perGroup = request.groupHeader() || request.pageBreakPerGroup();
        int groupAt = 0;
        for (int r = 0; r < rows.size(); r++) {
            if (perGroup && groupAt < groups.size() && r == groups.get(groupAt).first()) {
                if (r > 0) {
                    document.table(table);
                    if (request.pageBreakPerGroup()) {
                        document.pageBreak();
                    }
                    table = table(columns, numeric, other, negativeInRed);
                }
                if (request.groupHeader()) {
                    document.heading(groupText(request, groups.get(groupAt).value(), text));
                }
            }
            Object[] values = rows.get(r);
            String[] cells = new String[count];
            for (int i = 0; i < count; i++) {
                ColumnDefinition column = columns.get(i);
                try {
                    cells[i] = singleLine(text.of(values[i], column.format()));
                } catch (IllegalArgumentException e) {
                    throw new ReportException("Column '" + column.fieldName() + "': " + e.getMessage(), e);
                }
            }
            table.row(cells);
            // آخر صف في مجموعته: إجماليها الفرعي تحته على طول.
            int groupEnd = groupAt + 1 < groups.size() ? groups.get(groupAt + 1).first() : rows.size();
            if (groupAt < groups.size() && r + 1 == groupEnd) {
                Group group = groups.get(groupAt++);
                table.subtotal(totals(request, group.sums(),
                        Totals.subtotalLabel(request, groupText(request, group.value(), text), rightToLeft), text));
            }
        }
        if (Totals.any(request)) {
            String[] cells = totals(request, sums, Totals.label(request, rightToLeft), text);
            // تحت إجماليات فرعية الإجمالي الكلي بشكل مختلف؛ من غير مجموعات زي ما كان بالظبط.
            if (groups.isEmpty()) {
                table.totals(cells);
            } else {
                table.grandTotal(cells);
            }
        }
        return document.table(table).toBytes();
    }

    /** A table with the report's columns and their alignment, without rows. */
    private static PdfTable table(List<ColumnDefinition> columns, boolean[] numeric, boolean[] other,
                                  boolean negativeInRed) {
        PdfTable table = PdfTable.create();
        if (negativeInRed) {
            table.negativeInRed();
        }
        for (int i = 0; i < columns.size(); i++) {
            ColumnDefinition column = columns.get(i);
            // العمود المتجمّع أرقام بس (غير كده بيرمي فوق)، فمجموعه تحت أرقامه حتى من غير بيانات.
            Align align = column.align() != null ? align(column.align())
                    : column.total() || numeric[i] && !other[i] ? Align.END : Align.START;
            table.column(singleLine(column.header()), column.weight(), align);
        }
        return table;
    }

    /**
     * A run of consecutive rows with the same value ({@link ReportRequest#groupBy()}): its first row and the sum of
     * each column.
     */
    private record Group(Object value, int first, BigDecimal[] sums) {

        Group(Object value, int first, int columns) {
            this(value, first, filled(columns));
        }

        private static BigDecimal[] filled(int columns) {
            BigDecimal[] sums = new BigDecimal[columns];
            Arrays.fill(sums, BigDecimal.ZERO);
            return sums;
        }
    }

    /**
     * The group value as it appears in its column if it is a column of the report (in that column's pattern),
     * otherwise in the default format.
     */
    private static String groupText(ReportRequest<?> request, Object value, CellText text) throws ReportException {
        int at = Totals.groupColumn(request);
        if (at < 0) {
            return singleLine(text.of(value));
        }
        ColumnDefinition column = request.columns().get(at);
        try {
            return singleLine(text.of(value, column.format()));
        } catch (IllegalArgumentException e) {
            throw new ReportException("Column '" + column.fieldName() + "': " + e.getMessage(), e);
        }
    }

    /**
     * The cells of a totals row (grand total or subtotal): each sum in its column's pattern, and the label under
     * the first column that is not totalled.
     */
    private static String[] totals(ReportRequest<?> request, BigDecimal[] sums, String label, CellText text)
            throws ReportException {
        List<ColumnDefinition> columns = request.columns();
        String[] cells = new String[columns.size()];
        for (int i = 0; i < cells.length; i++) {
            ColumnDefinition column = columns.get(i);
            if (column.total()) {
                try {
                    cells[i] = singleLine(text.of(sums[i], column.format()));
                } catch (IllegalArgumentException e) {
                    throw new ReportException("Column '" + column.fieldName() + "': " + e.getMessage(), e);
                }
            }
        }
        int labelAt = Totals.labelColumn(request);
        if (labelAt >= 0) {
            cells[labelAt] = singleLine(label);
        }
        return cells;
    }

    private static Align align(Alignment alignment) {
        return switch (alignment) {
            case START -> Align.START;
            case CENTER -> Align.CENTER;
            case END -> Align.END;
        };
    }

    /** {@code Boolean.TRUE} or the text {@code "true"}, for example a value that comes from a settings file. */
    private static boolean isTrue(Object value) {
        return Boolean.TRUE.equals(value) || value instanceof String text && Boolean.parseBoolean(text.trim());
    }

    private static PdfFont font(Object value) throws ReportException {
        if (value == null || value instanceof PdfFont) {
            return (PdfFont) value;
        }
        throw new ReportException("Invalid " + FONT + " parameter: expected a PdfFont, for example"
                + " PdfFont.of(Path.of(\"fonts/Amiri-Regular.ttf\")), but got " + value.getClass().getName() + ".");
    }

    private static Integer columnCount(Object value) throws ReportException {
        if (value == null) {
            return null;
        }
        try {
            int count = value instanceof Integer number ? number : Integer.parseInt(value.toString().strip());
            if (count >= 1) {
                return count;
            }
        } catch (NumberFormatException e) {
            // نفس الرسالة تحت.
        }
        throw new ReportException("Invalid " + LANDSCAPE_FROM_COLUMNS + " parameter: expected a number of columns"
                + " of at least 1, but got " + value + ".");
    }

    private static Paper paper(Object value) throws ReportException {
        if (value == null) {
            return Paper.A4;
        }
        if (value instanceof Paper paper) {
            return paper;
        }
        try {
            return Paper.parse(value.toString());
        } catch (IllegalArgumentException e) {
            throw new ReportException("Invalid " + PAPER + " parameter: " + e.getMessage(), e);
        }
    }

    /**
     * A data value on one line: any newline or tab inside the value becomes a space, so one data row stays one
     * row. The cell still wraps to its column width like any long text.
     */
    private static String singleLine(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            out.append(Character.isISOControl(c) ? ' ' : c);
        }
        return out.toString();
    }
}
