package com.smart.report.pdf;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * A table inside a {@link PdfDocument}: columns with their headers and weights, rows, and totals rows.
 *
 * <p>Columns and cells are written <b>in logical order</b> (the first column is the one the reader starts
 * with), and the document places the first column on the right of an Arabic page and on the left of an
 * English one. The table header repeats at the top of every page the table spans.</p>
 *
 * <p>A column weight is a share of the table width, but no column gets narrower than its longest word: a word
 * is never broken, and text longer than the column width wraps onto more than one line.</p>
 */
public final class PdfTable {

    record Column(String header, float weight, Align align) {
    }

    /**
     * @param withPrevious the row moves to a new page together with the row before it: a subtotal with the last
     *                     row of its group
     * @param grand        the grand total under subtotals, styled so it stands apart from them
     */
    /**
     * @param joinNext this row and the next one are one unit: no line between them, and they move together
     *                 ({@link #rowJoinedToNext})
     */
    record Row(int index, String[] cells, boolean total, boolean withPrevious, boolean grand, boolean joinNext) {
    }

    /**
     * A cell style rule. It is called for every cell in the data and totals rows (not the header), and returns
     * {@link CellStyle#PLAIN} for a cell that keeps its row style.
     */
    @FunctionalInterface
    public interface CellRule {

        /**
         * @param row    the zero-based row number in the order the rows were added, totals rows included
         * @param column the zero-based column number in logical order (the first column added)
         * @param text   the cell text as written
         */
        CellStyle style(int row, int column, String text);
    }

    private final List<Column> columns = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final List<CellRule> rules = new ArrayList<>();
    private boolean headerShown = true;
    private float totalSizeBoost;

    private PdfTable() {
    }

    public static PdfTable create() {
        return new PdfTable();
    }

    /** A column with weight 1, aligned to the start of the line. */
    public PdfTable column(String header) {
        return column(header, 1f, Align.START);
    }

    /** A column aligned to the start of the line. */
    public PdfTable column(String header, float weight) {
        return column(header, weight, Align.START);
    }

    /**
     * @param weight the column's share of the table width, relative to the other columns (22, 14 and 64, for
     *               example)
     * @param align  the alignment of the column cells; the column header is always centred
     */
    public PdfTable column(String header, float weight, Align align) {
        if (!(weight > 0)) {
            throw new IllegalArgumentException("Column weight must be positive.");
        }
        if (!rows.isEmpty()) {
            throw new IllegalStateException("Add every column before the first row.");
        }
        columns.add(new Column(header == null ? "" : header, weight, align == null ? Align.START : align));
        return this;
    }

    /**
     * A data row: one cell per column, in order. A shorter row is filled up with empty cells, and {@code null} is
     * an empty cell.
     */
    public PdfTable row(String... cells) {
        rows.add(new Row(rows.size(), fitted(cells), false, joined(), false, false));
        return this;
    }

    /**
     * One row per item; {@code cells} turns the item into its cells. This is where amounts and dates are
     * formatted.
     */
    public <T> PdfTable rows(Iterable<T> items, Function<? super T, String[]> cells) {
        for (T item : items) {
            row(cells.apply(item));
        }
        return this;
    }

    /** A totals row: bold text on a distinct background, with its cells exactly under their columns. */
    public PdfTable totals(String... cells) {
        rows.add(new Row(rows.size(), fitted(cells), true, joined(), false, false));
        return this;
    }

    /**
     * A subtotal row under a group of rows. It looks like {@link #totals}, but it never lands alone at the top of
     * a new page: if the rest of the page has no room for it, the last row before it moves with it.
     */
    PdfTable subtotal(String... cells) {
        if (rows.isEmpty()) {
            throw new IllegalStateException("A subtotal goes under the rows it adds up.");
        }
        rows.add(new Row(rows.size(), fitted(cells), true, true, false, false));
        return this;
    }

    /**
     * The grand total of a table that has subtotals ({@link #subtotal}). It looks like {@link #totals}, with a
     * thick line above it and a double line below it, as in an accounting ledger, so it is not confused with the
     * last subtotal right above it.
     */
    PdfTable grandTotal(String... cells) {
        rows.add(new Row(rows.size(), fitted(cells), true, joined(), true, false));
        return this;
    }

    /**
     * A style for specific cells: {@code (row, column, text) -> column == 2 && text.startsWith("-")
     * ? CellStyle.color(0xC62828) : CellStyle.PLAIN}. Several rules are combined in order, and a later rule wins
     * for whatever it sets.
     */
    public PdfTable cellStyle(CellRule rule) {
        rules.add(Objects.requireNonNull(rule, "rule"));
        return this;
    }

    /**
     * Negative amounts in red ({@link CellStyle#RED}) in these columns (zero-based, in logical order), or in all
     * columns when none are given. A negative is a number that starts with {@code -} or {@code −}, ends with
     * {@code -} ({@code 1,200.00-}), or is in brackets ({@code (1,200.00)}) as in accounting ledgers.
     */
    public PdfTable negativeInRed(int... columns) {
        int[] chosen = columns == null ? new int[0] : columns.clone();
        return cellStyle((row, column, text) -> {
            boolean wanted = chosen.length == 0;
            for (int c : chosen) {
                wanted |= c == column;
            }
            return wanted && negative(text) ? CellStyle.color(CellStyle.RED) : CellStyle.PLAIN;
        });
    }

    /** The text of a negative number: {@code -5}, {@code −5}, {@code 5-}, {@code (5)}, with Latin or Arabic digits. */
    static boolean negative(String text) {
        if (text == null) {
            return false;
        }
        String value = text.strip();
        if (value.length() < 2 || value.codePoints().noneMatch(Character::isDigit)) {
            return false;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        return first == '-' || first == '\u2212' || last == '-' || last == '\u2212' || first == '(' && last == ')';
    }

    /** The cell style from all the rules. */
    CellStyle styleOf(Row row, int column) {
        CellStyle style = CellStyle.PLAIN;
        for (CellRule rule : rules) {
            style = style.under(rule.style(row.index(), column, row.cells()[column]));
        }
        return style;
    }

    /**
     * A row with no line under it, always on the same page as the row after it. For example, on a receipt the
     * item name is on its own line, with the quantity, price and total under it.
     */
    PdfTable rowJoinedToNext(String... cells) {
        rows.add(new Row(rows.size(), fitted(cells), false, joined(), false, true));
        return this;
    }

    /** The next row moves with the row before it if that row is a {@link #rowJoinedToNext}. */
    private boolean joined() {
        return !rows.isEmpty() && rows.get(rows.size() - 1).joinNext();
    }

    /** A table with no header, for summaries (a label and its value), for example. */
    public PdfTable withoutHeader() {
        this.headerShown = false;
        return this;
    }

    /** Makes the totals row font larger by this many points, for the number the reader is looking for. */
    PdfTable totalSizeBoost(float points) {
        this.totalSizeBoost = points;
        return this;
    }

    private String[] fitted(String[] cells) {
        if (columns.isEmpty()) {
            throw new IllegalStateException("Add the table's columns before its rows.");
        }
        int given = cells == null ? 0 : cells.length;
        if (given > columns.size()) {
            throw new IllegalArgumentException(
                    "The row has " + given + " cells but the table has " + columns.size() + " columns.");
        }
        String[] out = new String[columns.size()];
        for (int i = 0; i < out.length; i++) {
            String cell = i < given ? cells[i] : null;
            out[i] = cell == null ? "" : cell;
        }
        return out;
    }

    List<Column> columns() {
        return columns;
    }

    List<Row> rows() {
        return rows;
    }

    boolean headerShown() {
        return headerShown;
    }

    float totalSizeBoost() {
        return totalSizeBoost;
    }
}
