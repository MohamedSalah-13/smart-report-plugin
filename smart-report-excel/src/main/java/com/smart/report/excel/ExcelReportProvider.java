package com.smart.report.excel;

import com.smart.report.ColumnDefinition;
import com.smart.report.ReportException;
import com.smart.report.ReportProvider;
import com.smart.report.ReportRequest;
import com.smart.report.ReportType;
import com.smart.report.util.BeanPropertyReader;
import com.smart.report.util.TextDirection;
import com.smart.report.util.Totals;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.ss.util.SheetUtil;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates a generic Excel (.xlsx) report (a title, a formatted column header and data rows) without any
 * template, from any List of objects and a list of ColumnDefinition.
 *
 * <p>Numbers, booleans and dates are written with their native Excel types (not as text). Dates get a real
 * date format, so they show as dates instead of the raw serial number.</p>
 *
 * <p>Each column is as wide as its header and content (see {@link ColumnWidths}). The column header is frozen
 * at the top and carries filter buttons, so the sheet can be sorted and filtered without any setup.</p>
 *
 * <p><b>Direction:</b> the sheet runs right to left (first column on the right) when the title or any column
 * header contains Arabic (the same rule as the generic PDF report), unless {@link #RIGHT_TO_LEFT} is set in
 * {@code parameters}.</p>
 *
 * <p><b>Totals:</b> when a column is totalled ({@link ColumnDefinition#withTotal()}), a bold totals row follows
 * the data after one blank row. Under each totalled column it holds a real {@code SUM} formula over the data rows
 * (so the total changes when the user edits a number), in the column's format, together with its computed value,
 * so the number shows correctly even in a program that does not calculate formulas when it opens the file. That
 * value uses Excel's own arithmetic: {@code double} numbers as in the cells, summed in order, so recalculating
 * does not change it. It can differ from the PDF's exact total for numbers longer than 15-17 digits, or for
 * {@code float}. A value that cannot be totalled throws the same error as the PDF ({@link Totals#check}). The
 * word ({@link Totals#label}) goes under the first column that is not totalled. The blank row separates the
 * total from the data: Excel extends filtering and sorting to the rows that touch the table, so a touching totals
 * row could be sorted into the data. With no data, the total is zero (with no formula: there are no rows to sum).
 * </p>
 *
 * <p><b>Subtotals:</b> with {@link ReportRequest#groupBy()}, a row under each group holds a
 * {@code SUBTOTAL(9, ...)} formula over that group's rows, and the grand total is also {@code SUBTOTAL(9, ...)}
 * over all rows: it skips the subtotals in its range, so it does not count them twice, and it sums only the rows
 * the filter leaves visible. The group cell in a group's subtotal row holds the group value, like the group's rows,
 * so filtering on a customer keeps that customer's subtotal; the filter range reaches the blank row after the last
 * subtotal. Sorting mixes the subtotals into the data, as with Excel's own Subtotal command.</p>
 */
public final class ExcelReportProvider implements ReportProvider {

    /** Key in {@code ReportRequest.parameters()}: a {@code Boolean} that decides the sheet direction. */
    public static final String RIGHT_TO_LEFT = "smart.report.excel.rightToLeft";

    private static final String DATE_FORMAT = "yyyy-mm-dd";
    private static final String DATE_TIME_FORMAT = "yyyy-mm-dd hh:mm:ss";
    private static final String TIME_FORMAT = "hh:mm:ss";
    private static final double SECONDS_PER_DAY = 86_400d;

    /** Number of rows kept in memory before they are written to disk. */
    private static final int ROW_WINDOW = 200;

    /** Maximum number of rows in one xlsx sheet. */
    private static final int MAX_ROWS = 1_048_576;

    /** Number of data rows whose text is measured for the column width (together with the column header). */
    static final int WIDTH_SAMPLE_ROWS = 500;

    /** Sheet name when there is no title, or when nothing usable is left of the title (see {@link #sheetName}). */
    private static final String DEFAULT_SHEET_NAME = "Report";

    /** Maximum length of a sheet name in Excel. */
    private static final int MAX_SHEET_NAME = 31;

    /** Maximum column width in Excel: 255 characters, in units of 1/256 of a character width. */
    private static final int MAX_COLUMN_WIDTH = 255 * 256;

    /** Room for the filter button in a header cell, in character widths (about 17 pixels in Calibri 11). */
    private static final double FILTER_BUTTON_WIDTH = 2.5;

    /**
     * The widest possible date: the longest month name (September, "سبتمبر") and the longest day name
     * (Wednesday, "الأربعاء"), with a two-digit day and hour, so any date in the column fits and does not show as
     * {@code ####}.
     */
    private static final double WIDEST_DATE = DateUtil.getExcelDate(LocalDateTime.of(2026, 9, 30, 22, 59, 59));

    @Override
    public ReportType type() {
        return ReportType.EXCEL;
    }

    @Override
    public byte[] generate(ReportRequest<?> request) throws ReportException {
        List<ColumnDefinition> columns = request.columns();
        if (columns.isEmpty()) {
            throw new ReportException("At least one column is required to generate an Excel report.");
        }

        Totals.checkGroups(request);
        int headerRows = request.title() != null && !request.title().isBlank() ? 3 : 1;
        boolean totals = Totals.any(request);
        // قيمة المجموعة لكل صف بتتقرا الأول: صفوف الإجمالي الفرعي داخلة في حد الورقة.
        Object[] groupValues = groupValues(request);
        int groups = 0;
        for (int k = 0; groupValues != null && k < groupValues.length; k++) {
            if (k == 0 || !Totals.sameGroup(groupValues[k - 1], groupValues[k])) {
                groups++;
            }
        }
        // صف الإجمالي وسطر فاضي قبله، وصف لكل مجموعة (واتنين مع عنوانها)
        long totalRows = (long) request.data().size() + headerRows + groups * (request.groupHeader() ? 2L : 1L)
                + (totals ? 2 : 0);
        if (totalRows > MAX_ROWS) {
            throw new ReportException("An .xlsx sheet holds at most " + MAX_ROWS + " rows, but this report needs "
                    + totalRows + ". Split the data across several reports.");
        }

        // SXSSF بيكتب الصفوف على القرص أول بأول، فتقرير فيه مئات الآلاف من الصفوف
        // ما بيفضلش كله في الذاكرة زي XSSF. close() بيمسح الملفات المؤقتة دي (في POI 6 بينادي dispose()
        // بنفسه، و dispose() بقت deprecated)، فالـ try بيمسحها حتى لو حصل خطأ.
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_WINDOW)) {
            workbook.setCompressTempFiles(true);
            SXSSFSheet sheet = workbook.createSheet(sheetName(request.title()));
            boolean rightToLeft = TextDirection.rightToLeft(request, RIGHT_TO_LEFT);
            sheet.setRightToLeft(rightToLeft);
            Styles styles = createStyles(workbook);
            ColumnStyles[] columnStyles = new ColumnStyles[columns.size()];
            for (int i = 0; i < columns.size(); i++) {
                columnStyles[i] = new ColumnStyles(workbook, columns.get(i), rightToLeft, styles.bold());
            }

            ColumnWidths widths = new ColumnWidths(workbook, columns.size());

            int rowIndex = 0;
            if (headerRows == 3) {
                // العنوان في خلية لوحده من غير دمج، وما بيتقاسش مع الأعمدة: بيكمّل في الخلايا الفاضية
                // جنبه، والخلية المدموجة كانت هتقصّه لو الجدول أضيق منه
                Row titleRow = sheet.createRow(rowIndex++);
                Cell titleCell = titleRow.createCell(0);
                titleCell.setCellValue(request.title());
                titleCell.setCellStyle(styles.title());
                rowIndex++; // سطر فاصل
            }

            int headerRowIndex = rowIndex;
            Row headerRow = sheet.createRow(rowIndex++);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns.get(i).header());
                cell.setCellStyle(styles.header());
                widths.header(cell, i);
            }

            // مجموع كل عمود متجمّع زي ما Excel هيحسبه: الأرقام اللي اتكتبت في خلاياه فعلًا (double)، بالترتيب
            // من فوق لتحت. المجموع بالظبط (Totals.add) كان ممكن يختلف عنها — float زي 0.1f بيتكتب
            // 0.10000000149011612، وBigDecimal أطول من خانات الـ double بيتقرّب — فالقيمة المحفوظة كانت هتتغير
            // أول ما Excel يعيد الحساب.
            double[] sums = new double[columns.size()];
            double[] groupSums = new double[columns.size()];
            int groupFirstRow = rowIndex;
            int dataRow = 0;
            CellStyle groupHeaderStyle = request.groupHeader() ? groupHeaderStyle(workbook) : null;
            for (Object item : request.data()) {
                if (groupValues != null && dataRow > 0
                        && !Totals.sameGroup(groupValues[dataRow - 1], groupValues[dataRow])) {
                    writeSubtotal(sheet.createRow(rowIndex), request, groupSums, groupFirstRow, rowIndex - 1,
                            groupValues[dataRow - 1], columnStyles, widths, rightToLeft);
                    if (request.pageBreakPerGroup()) {
                        // فاصل صفحات للطباعة: كل مجموعة تبدأ في ورقة.
                        sheet.setRowBreak(rowIndex);
                    }
                    rowIndex++;
                    groupFirstRow = rowIndex;
                    groupSums = new double[columns.size()];
                }
                if (groupValues != null && request.groupHeader()
                        && (dataRow == 0 || !Totals.sameGroup(groupValues[dataRow - 1], groupValues[dataRow]))) {
                    writeGroupHeader(sheet.createRow(rowIndex++), request, groupValues[dataRow], groupHeaderStyle,
                            widths);
                    groupFirstRow = rowIndex;
                }
                Row row = sheet.createRow(rowIndex++);
                boolean sampled = dataRow < WIDTH_SAMPLE_ROWS;
                for (int i = 0; i < columns.size(); i++) {
                    ColumnDefinition column = columns.get(i);
                    Object value = BeanPropertyReader.read(item, column.fieldName());
                    if (column.total()) {
                        Totals.check(value, column, dataRow);
                    }
                    Cell cell = row.createCell(i);
                    try {
                        writeCell(cell, value, columnStyles[i]);
                    } catch (IllegalArgumentException e) {
                        throw new ReportException("Column '" + column.fieldName() + "': " + e.getMessage(), e);
                    }
                    if (column.total() && cell.getCellType() == CellType.NUMERIC) {
                        sums[i] += cell.getNumericCellValue();
                        groupSums[i] += cell.getNumericCellValue();
                    }
                    widths.cell(cell, i, sampled);
                }
                dataRow++;
            }
            if (groupValues != null && dataRow > 0) {
                writeSubtotal(sheet.createRow(rowIndex), request, groupSums, groupFirstRow, rowIndex - 1,
                        groupValues[dataRow - 1], columnStyles, widths, rightToLeft);
                rowIndex++;
            }
            int lastDataRowIndex = rowIndex - 1;

            if (totals) {
                rowIndex++; // سطر فاضي: الفلتر والترتيب ما يوصلوش للإجمالي
                // مع المجموعات الإجمالي SUBTOTAL برضه: بيسيب صفوف الإجمالي الفرعي اللي في نطاقه، والصفوف اللي
                // الفلتر مخبيها.
                writeTotals(sheet.createRow(rowIndex++), request, sums, headerRowIndex + 1, lastDataRowIndex,
                        groupValues != null, Totals.label(request, rightToLeft), Totals.labelColumn(request), false,
                        columnStyles, widths);
            }

            widths.apply(sheet);
            // رأس الأعمدة بيفضل ظاهر والورقة بتتنزل، وعليه أزرار الفلتر: الورقة تتفرز وتتفلتر على طول.
            // الفلتر على الرأس والبيانات بس، من غير صف الإجمالي.
            sheet.createFreezePane(0, headerRowIndex + 1);
            // مع المجموعات الفلتر بيوصل للسطر الفاضي بعد آخر إجمالي فرعي: Excel بيعتبر آخر صف في نطاق الفلتر فيه
            // SUBTOTAL صف إجمالي وما بيخبيهوش أبدًا، فإجمالي آخر مجموعة كان بيفضل ظاهر (بصفر) لو اخترت عميل تاني.
            int filterEnd = groupValues != null && dataRow > 0 ? lastDataRowIndex + 1 : lastDataRowIndex;
            sheet.setAutoFilter(new CellRangeAddress(headerRowIndex, filterEnd, 0, columns.size() - 1));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ReportException("Failed to generate Excel report.", e);
        }
    }

    /**
     * Writes the totals row: a {@code SUM} formula under each totalled column over its data rows, together with
     * its computed value. Excel shows the stored value until it calculates the formula, and a file without a
     * stored value would show zero in any program that does not calculate on open. The word goes under the first
     * column that is not totalled, and the empty cells get the same style, so the totals lines span the whole
     * table.
     *
     * @param sums         the sum of the numbers written in each column, with Excel's arithmetic, so
     *                     recalculating does not change it
     * @param firstDataRow the first data row (zero-based)
     * @param lastDataRow  the last data row; less than {@code firstDataRow} when there is no data
     */
    private static void writeTotals(Row row, ReportRequest<?> request, double[] sums, int firstDataRow,
                                    int lastDataRow, boolean subtotalFunction, String label, int labelAt,
                                    boolean subtotal, ColumnStyles[] columnStyles, ColumnWidths widths) {
        List<ColumnDefinition> columns = request.columns();
        for (int i = 0; i < columns.size(); i++) {
            Cell cell = row.createCell(i);
            if (columns.get(i).total()) {
                if (lastDataRow >= firstDataRow) {
                    String column = CellReference.convertNumToColString(i);
                    String range = column + (firstDataRow + 1) + ":" + column + (lastDataRow + 1);
                    cell.setCellFormula(subtotalFunction ? "SUBTOTAL(9," + range + ")" : "SUM(" + range + ")");
                }
                // على خلية فيها معادلة دي القيمة المحسوبة المحفوظة معاها؛ من غير معادلة، صفر عادي.
                cell.setCellValue(sums[i]);
                cell.setCellStyle(columnStyles[i].total(true, subtotal));
            } else {
                if (i == labelAt) {
                    cell.setCellValue(label);
                }
                cell.setCellStyle(columnStyles[i].total(false, subtotal));
            }
            widths.totals(cell, i);
        }
    }

    /**
     * Writes the group header row: the group value in bold, in its own column when it is a column of the report
     * (so a filter on that column keeps it with its rows), otherwise in the first column.
     */
    private static void writeGroupHeader(Row row, ReportRequest<?> request, Object value, CellStyle style,
                                         ColumnWidths widths) {
        int at = Math.max(0, Totals.groupColumn(request));
        Cell cell = row.createCell(at);
        cell.setCellValue(value == null ? Totals.NO_GROUP_VALUE : String.valueOf(value));
        cell.setCellStyle(style);
        widths.cell(cell, at, true);
    }

    private static CellStyle groupHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    /**
     * Writes the subtotal row under the last row of its group: a {@code SUBTOTAL(9, ...)} formula over the group's
     * rows, so the grand total ({@code SUBTOTAL} as well) does not count it twice, and its value computed with
     * Excel's arithmetic.
     *
     * <p>When the group is a column of the report, that column's cell in the subtotal row holds the group value
     * itself, with its type and format, exactly like the group's rows: a filter on that customer keeps the
     * customer's subtotal visible under its rows (the text «إجمالى: أحمد», "Total: Ahmed", in that cell used to hide
     * it). The words («إجمالى», "Total") go under the first other column that is not totalled; if there is none, the
     * row has no words, and its bold font and borders show that it is a total. When the group is not a column, the
     * full text («إجمالى: أحمد») takes the place of the grand total's word.</p>
     */
    private void writeSubtotal(Row row, ReportRequest<?> request, double[] sums, int firstRow, int lastRow,
                               Object groupValue, ColumnStyles[] columnStyles, ColumnWidths widths,
                               boolean rightToLeft) {
        List<ColumnDefinition> columns = request.columns();
        int groupColumn = Totals.groupColumn(request);
        if (groupColumn < 0 || columns.get(groupColumn).total()) {
            writeTotals(row, request, sums, firstRow, lastRow, true,
                    Totals.subtotalLabel(request, groupValue == null ? "" : String.valueOf(groupValue), rightToLeft),
                    Totals.labelColumn(request), true, columnStyles, widths);
            return;
        }
        int wordsAt = -1;
        for (int i = 0; i < columns.size(); i++) {
            if (i != groupColumn && !columns.get(i).total()) {
                wordsAt = i;
                break;
            }
        }
        writeTotals(row, request, sums, firstRow, lastRow, true, words(request, rightToLeft), wordsAt, true,
                columnStyles, widths);
        Cell cell = row.getCell(groupColumn);
        writeCell(cell, groupValue, columnStyles[groupColumn]);
        cell.setCellStyle(columnStyles[groupColumn].subtotalValue(cell.getCellStyle()));
        widths.totals(cell, groupColumn);
    }

    /** The subtotal words without the group value: «إجمالى: » becomes «إجمالى» ("Total"). */
    private static String words(ReportRequest<?> request, boolean rightToLeft) {
        return Totals.subtotalPrefix(request, rightToLeft).strip().replaceAll("[:：\\s]+$", "");
    }

    /** The {@link ReportRequest#groupBy()} value of each row, or {@code null} when the report has no groups. */
    private static Object[] groupValues(ReportRequest<?> request) {
        if (request.groupBy() == null) {
            return null;
        }
        Object[] values = new Object[request.data().size()];
        for (int k = 0; k < values.length; k++) {
            values[k] = BeanPropertyReader.read(request.data().get(k), request.groupBy());
        }
        return values;
    }


    /**
     * Dates in Excel are serial numbers, so without a date format on the cell the user sees a number such as
     * {@code 45245.01} instead of the date itself. That is why each date type gets its matching style.
     */
    private void writeCell(Cell cell, Object value, ColumnStyles styles) {
        CellStyle style = switch (value) {
            case null -> {
                cell.setBlank();
                yield styles.text();
            }
            case Number number -> {
                cell.setCellValue(number.doubleValue());
                yield styles.number();
            }
            case Boolean bool -> {
                cell.setCellValue(bool);
                yield styles.text();
            }
            // أنواع java.sql بترث java.util.Date، فلازم تتفحص قبلها
            case java.sql.Date date -> {
                cell.setCellValue(date.toLocalDate());
                yield styles.date(DATE_FORMAT);
            }
            case java.sql.Time time -> {
                cell.setCellValue(fractionOfDay(time.toLocalTime()));
                yield styles.date(TIME_FORMAT);
            }
            case java.sql.Timestamp timestamp -> {
                cell.setCellValue(timestamp.toLocalDateTime());
                yield styles.date(DATE_TIME_FORMAT);
            }
            case Date date -> {
                cell.setCellValue(date);
                yield styles.date(DATE_TIME_FORMAT);
            }
            case Calendar calendar -> {
                cell.setCellValue(calendar);
                yield styles.date(DATE_TIME_FORMAT);
            }
            case LocalDate date -> {
                cell.setCellValue(date);
                yield styles.date(DATE_FORMAT);
            }
            case LocalDateTime dateTime -> {
                cell.setCellValue(dateTime);
                yield styles.date(DATE_TIME_FORMAT);
            }
            case LocalTime time -> {
                cell.setCellValue(fractionOfDay(time));
                yield styles.date(TIME_FORMAT);
            }
            case Instant instant -> {
                cell.setCellValue(LocalDateTime.ofInstant(instant, ZoneId.systemDefault()));
                yield styles.date(DATE_TIME_FORMAT);
            }
            case ZonedDateTime zoned -> {
                cell.setCellValue(zoned.toLocalDateTime());
                yield styles.date(DATE_TIME_FORMAT);
            }
            case OffsetDateTime offset -> {
                cell.setCellValue(offset.toLocalDateTime());
                yield styles.date(DATE_TIME_FORMAT);
            }
            default -> {
                cell.setCellValue(String.valueOf(value));
                yield styles.text();
            }
        };
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    /**
     * The styles of one column, created when the first value that needs them arrives: the column's format (if
     * any) in Excel syntax, and its alignment. The format is converted by the value type (numbers or dates), so a
     * date pattern in a number column is left untouched.
     *
     * <p>Excel stops at 64,000 styles, so each column creates one style per format, not one per cell.</p>
     */
    private static final class ColumnStyles {

        private final Workbook workbook;
        private final String format;
        private final HorizontalAlignment alignment;
        private final Font bold;
        private final Map<String, CellStyle> made = new HashMap<>();

        /** @param bold the font of the totals row, one for the whole file */
        ColumnStyles(Workbook workbook, ColumnDefinition column, boolean rightToLeft, Font bold) {
            this.workbook = workbook;
            this.format = column.format();
            this.bold = bold;
            // Excel بيحاذي يمين وشمال على الورقة نفسها حتى في الورقة العربية، فالبداية بتتحسب من اتجاهها.
            this.alignment = column.align() == null ? null : switch (column.align()) {
                case START -> rightToLeft ? HorizontalAlignment.RIGHT : HorizontalAlignment.LEFT;
                case CENTER -> HorizontalAlignment.CENTER;
                case END -> rightToLeft ? HorizontalAlignment.LEFT : HorizontalAlignment.RIGHT;
            };
        }

        /** Text or a boolean: the alignment only, or {@code null} when there is none (exactly as before). */
        CellStyle text() {
            return alignment == null ? null : style("text", null);
        }

        CellStyle number() {
            return format == null ? text() : style("number", ExcelFormats.number(format));
        }

        /** A date: the column's format if it has one, otherwise the default format for the date type. */
        CellStyle date(String defaultFormat) {
            String excel = format == null ? defaultFormat : ExcelFormats.date(format);
            return style("date|" + excel, excel);
        }

        /**
         * A cell in the totals row: bold font, a thin line above and a double line below, as in ledger totals, and
         * the sum in the column's format. A subtotal has a thin line below instead of the double line: the double
         * line is for the grand total only.
         *
         * @param number   the sum itself, not the word or an empty cell
         * @param subtotal a subtotal row under a group
         */
        CellStyle total(boolean number, boolean subtotal) {
            String key = (subtotal ? "subtotal|" : "total|") + (number ? "number" : "text");
            CellStyle known = made.get(key);
            if (known != null) {
                return known;
            }
            CellStyle created = style(key, number && format != null ? ExcelFormats.number(format) : null);
            created.setFont(bold);
            created.setBorderTop(BorderStyle.THIN);
            created.setBorderBottom(subtotal ? BorderStyle.THIN : BorderStyle.DOUBLE);
            return created;
        }

        /**
         * The group value cell in the group's subtotal row: the same style as the value in the group's rows (the
         * date format, for example, so the filter sees it like them), with the subtotal font and borders.
         *
         * @param value the style of the value as written; {@code null} = no style
         */
        CellStyle subtotalValue(CellStyle value) {
            if (value == null) {
                return total(false, true);
            }
            String key = "subtotal|value|" + value.getIndex();
            CellStyle known = made.get(key);
            if (known != null) {
                return known;
            }
            CellStyle created = workbook.createCellStyle();
            created.cloneStyleFrom(value);
            created.setFont(bold);
            created.setBorderTop(BorderStyle.THIN);
            created.setBorderBottom(BorderStyle.THIN);
            made.put(key, created);
            return created;
        }

        private CellStyle style(String key, String dataFormat) {
            CellStyle known = made.get(key);
            if (known != null) {
                return known;
            }
            CellStyle created = workbook.createCellStyle();
            if (alignment != null) {
                created.setAlignment(alignment);
            }
            if (dataFormat != null) {
                created.setDataFormat(workbook.createDataFormat().getFormat(dataFormat));
            }
            made.put(key, created);
            return created;
        }
    }

    /**
     * Column widths with the same measurement as {@code autoSizeColumn} (the cell's font and the text as it will
     * show in its format, whether Arabic, numbers or dates), but without measuring every cell in the sheet:
     * {@code trackAllColumnsForAutoSizing} did that, and it made a large report about 5 times slower.
     *
     * <ul>
     *     <li><b>The column header</b> is measured together with room for the filter button.</li>
     *     <li><b>Text</b> is measured from the first {@link #WIDTH_SAMPLE_ROWS} rows only; longer text after them is
     *         cut at the column edge and shows in full in the cell itself.</li>
     *     <li><b>Numbers</b> are checked in all rows, but a number is measured only when its formatted text is
     *         longer than the ones measured before it, so the widest number does not show as {@code ####} (see
     *         {@link #number}).</li>
     *     <li><b>Dates</b> are measured once per format in the column, with the widest possible date
     *         ({@link #WIDEST_DATE}).</li>
     * </ul>
     *
     * <p>The totals row is measured in full. The title is not measured, and the width never exceeds Excel's
     * maximum width (255 characters).</p>
     */
    private static final class ColumnWidths {

        private final double[] widths;
        /** The text length of the longest number measured in each column (as it shows in its format). */
        private final int[] longestNumber;
        /** The date formats already measured in each column, by style index. */
        private final List<Set<Short>> datesMeasured;
        /**
         * Style index to "is its format a date?" (a cell without a style returns a new object each time, so the
         * key is the index).
         */
        private final Map<Short, Boolean> dateStyles = new HashMap<>();
        private final float charWidth;
        private final DataFormatter formatter = new DataFormatter();

        ColumnWidths(Workbook workbook, int columns) {
            widths = new double[columns];
            longestNumber = new int[columns];
            datesMeasured = new ArrayList<>(columns);
            for (int i = 0; i < columns; i++) {
                datesMeasured.add(new HashSet<>());
            }
            charWidth = SheetUtil.getDefaultCharWidthAsFloat(workbook);
        }

        void header(Cell cell, int column) {
            measure(cell, column, FILTER_BUTTON_WIDTH);
        }

        /** A totals cell is always measured, in its bold font: the sum is usually the column's widest number. */
        void totals(Cell cell, int column) {
            measure(cell, column, 0);
        }

        void cell(Cell cell, int column, boolean sampled) {
            switch (cell.getCellType()) {
                case STRING, BOOLEAN -> {
                    if (sampled) {
                        measure(cell, column, 0);
                    }
                }
                case NUMERIC -> {
                    CellStyle style = cell.getCellStyle();
                    if (isDate(style)) {
                        if (datesMeasured.get(column).add(style.getIndex())) {
                            double value = cell.getNumericCellValue();
                            cell.setCellValue(WIDEST_DATE);
                            measure(cell, column, 0);
                            cell.setCellValue(value);
                        }
                    } else {
                        number(cell, column, sampled);
                    }
                }
                default -> {
                }
            }
        }

        /**
         * Formats the number as it will show (without measuring the font, which is much cheaper) and measures it
         * only when its text is longer than the longest number measured in its column. The size of the number
         * alone is not enough: with the format {@code 0.########}, 1 and 1.12345678 have the same integer digits,
         * but the second is much wider. Text of the same length differs at most by a digit in place of a separator
         * or a sign, and the {@code 0} that the measurement adds to the text covers that.
         */
        private void number(Cell cell, int column, boolean sampled) {
            int length = formatter.formatCellValue(cell).length();
            if (sampled || length > longestNumber[column]) {
                measure(cell, column, 0);
                longestNumber[column] = Math.max(longestNumber[column], length);
            }
        }

        private boolean isDate(CellStyle style) {
            return dateStyles.computeIfAbsent(style.getIndex(),
                    index -> DateUtil.isADateFormat(style.getDataFormat(), style.getDataFormatString()));
        }

        private void measure(Cell cell, int column, double extra) {
            double width = SheetUtil.getCellWidth(cell, charWidth, formatter, false, List.of());
            if (width > 0) {
                widths[column] = Math.max(widths[column], width + extra);
            }
        }

        void apply(Sheet sheet) {
            for (int i = 0; i < widths.length; i++) {
                if (widths[i] > 0) {
                    sheet.setColumnWidth(i, (int) Math.min(MAX_COLUMN_WIDTH, Math.round(widths[i] * 256)));
                }
            }
        }
    }

    /** A time in Excel is a fraction of a day (0.5 = noon). */
    private static double fractionOfDay(LocalTime time) {
        return time.toSecondOfDay() / SECONDS_PER_DAY;
    }

    /**
     * Creates the styles once for the whole file, not once per cell:
     * Excel stops at 64,000 styles and refuses to open a file that has more.
     */
    private Styles createStyles(Workbook workbook) {
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        CellStyle header = workbook.createCellStyle();
        header.setFont(headerFont);
        header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        CellStyle title = workbook.createCellStyle();
        title.setFont(titleFont);

        // رأس الأعمدة والإجمالي بنفس الخط العريض
        return new Styles(title, header, headerFont);
    }

    /**
     * Derives the sheet name from the title, following Excel's rules (POI throws
     * {@code IllegalArgumentException} when they are broken, so a title used to bring down the whole report): the
     * characters {@code \ / * [ ] : ?} and control characters become a space, apostrophes and spaces are removed
     * from both ends ({@code 'Q1' sales} becomes {@code Q1' sales}), and the name is at most 31 characters. If no
     * name is left, or what is left is {@code History}, which Excel reserves for itself (POI does not block it, but
     * Excel does not accept it as a sheet name), the name is {@link #DEFAULT_SHEET_NAME}. The title in the cell
     * itself stays as it is.
     */
    private static String sheetName(String title) {
        if (title == null) return DEFAULT_SHEET_NAME;
        String name = trimSheetName(title.replaceAll("[\\\\/*\\[\\]:?\\p{Cntrl}]", " "));
        if (name.length() > MAX_SHEET_NAME) {
            // من غير ما يتقسم حرف من حرفين UTF-16 (إيموجي مثلًا)
            int end = Character.isHighSurrogate(name.charAt(MAX_SHEET_NAME - 1)) ? MAX_SHEET_NAME - 1 : MAX_SHEET_NAME;
            // القص ممكن يسيب فاصلة عليا أو مسافة في الآخر
            name = trimSheetName(name.substring(0, end));
        }
        return name.isEmpty() || name.equalsIgnoreCase("History") ? DEFAULT_SHEET_NAME : name;
    }

    private static String trimSheetName(String name) {
        return name.replaceAll("^[\\s']+|[\\s']+$", "");
    }

    private record Styles(CellStyle title, CellStyle header, Font bold) {}
}
