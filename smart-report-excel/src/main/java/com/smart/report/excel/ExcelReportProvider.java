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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * يولّد تقرير Excel (.xlsx) عام (عنوان + رأس أعمدة منسّق + صفوف بيانات) بدون الحاجة لأي قالب،
 * انطلاقًا من أي List من الكائنات + قائمة ColumnDefinition.
 *
 * <p>الأرقام والمنطقيات والتواريخ بتتكتب بأنواعها الأصلية في Excel (مش كنصوص)، والتواريخ بياخدوا
 * تنسيق تاريخ فعلي عشان يظهروا كتواريخ بدل الرقم التسلسلي الخام.</p>
 *
 * <p>عرض كل عمود على قد رأسه ومحتواه (شوف {@link ColumnWidths})، ورأس الأعمدة ثابت فوق وعليه
 * أزرار الفلتر، فالورقة تتفرز وتتفلتر من غير أي تجهيز.</p>
 *
 * <p><b>الاتجاه:</b> الورقة من اليمين لليسار (أول عمود على اليمين) لو العنوان أو أي رأس عمود فيه
 * عربي — نفس قاعدة تقرير الـ PDF العام — إلا لو {@link #RIGHT_TO_LEFT} اتحدد في {@code parameters}.</p>
 *
 * <p><b>الإجمالي:</b> لو في عمود متجمّع ({@link ColumnDefinition#withTotal()})، صف إجمالي بخط عريض بعد
 * البيانات بسطر فاضي: تحت كل عمود متجمّع معادلة {@code SUM} حقيقية على صفوف البيانات (فالإجمالي بيتغير لو
 * المستخدم عدّل رقم) بنمط العمود، ومعاها قيمتها محسوبة ({@link Totals})، فالرقم بيظهر صح حتى في برنامج
 * ما بيحسبش المعادلات وهو بيفتح الملف. الكلمة ({@link Totals#label}) تحت أول عمود مش متجمّع. السطر الفاضي
 * بيفصل الإجمالي عن البيانات: Excel بيمد الفلتر والترتيب على الصفوف اللازقة في الجدول، فصف الإجمالي اللازق
 * كان ممكن يتفرز وسط البيانات. من غير بيانات، الإجمالي صفر (من غير معادلة: مفيش صفوف تتجمع).</p>
 */
public final class ExcelReportProvider implements ReportProvider {

    /** مفتاح في {@code ReportRequest.parameters()}: {@code Boolean} يحسم اتجاه الورقة. */
    public static final String RIGHT_TO_LEFT = "smart.report.excel.rightToLeft";

    private static final String DATE_FORMAT = "yyyy-mm-dd";
    private static final String DATE_TIME_FORMAT = "yyyy-mm-dd hh:mm:ss";
    private static final String TIME_FORMAT = "hh:mm:ss";
    private static final double SECONDS_PER_DAY = 86_400d;

    /** عدد الصفوف المحفوظة في الذاكرة قبل ما تتكتب على القرص. */
    private static final int ROW_WINDOW = 200;

    /** أقصى عدد صفوف في ورقة xlsx واحدة. */
    private static final int MAX_ROWS = 1_048_576;

    /** عدد صفوف البيانات اللي النصوص فيها بتتقاس لعرض العمود (مع رأس الأعمدة). */
    static final int WIDTH_SAMPLE_ROWS = 500;

    /** أقصى عرض لعمود في Excel: 255 حرف، بوحدات 1/256 من عرض الحرف. */
    private static final int MAX_COLUMN_WIDTH = 255 * 256;

    /** مكان زرار الفلتر في خلية الرأس، بعرض الحروف (حوالي 17 بكسل في Calibri 11). */
    private static final double FILTER_BUTTON_WIDTH = 2.5;

    /**
     * أعرض تاريخ ممكن: أطول اسم شهر (سبتمبر) وأطول اسم يوم (الأربعاء) ويوم وساعة برقمين، فأي تاريخ
     * في العمود ييجي على قده وما يطلعش {@code ####}.
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

        int headerRows = request.title() != null && !request.title().isBlank() ? 3 : 1;
        boolean totals = Totals.any(request);
        // صف الإجمالي وسطر فاضي قبله
        long totalRows = (long) request.data().size() + headerRows + (totals ? 2 : 0);
        if (totalRows > MAX_ROWS) {
            throw new ReportException("An .xlsx sheet holds at most " + MAX_ROWS + " rows, but this report needs "
                    + totalRows + ". Split the data across several reports.");
        }

        // SXSSF بيكتب الصفوف على القرص أول بأول، فتقرير فيه مئات الآلاف من الصفوف
        // ما بيفضلش كله في الذاكرة زي XSSF
        SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_WINDOW);
        try (workbook) {
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

            BigDecimal[] sums = new BigDecimal[columns.size()];
            Arrays.fill(sums, BigDecimal.ZERO);
            int dataRow = 0;
            for (Object item : request.data()) {
                Row row = sheet.createRow(rowIndex++);
                boolean sampled = dataRow < WIDTH_SAMPLE_ROWS;
                for (int i = 0; i < columns.size(); i++) {
                    ColumnDefinition column = columns.get(i);
                    Object value = BeanPropertyReader.read(item, column.fieldName());
                    if (column.total()) {
                        sums[i] = Totals.add(sums[i], value, column, dataRow);
                    }
                    Cell cell = row.createCell(i);
                    try {
                        writeCell(cell, value, columnStyles[i]);
                    } catch (IllegalArgumentException e) {
                        throw new ReportException("Column '" + column.fieldName() + "': " + e.getMessage(), e);
                    }
                    widths.cell(cell, i, sampled);
                }
                dataRow++;
            }
            int lastDataRowIndex = rowIndex - 1;

            if (totals) {
                rowIndex++; // سطر فاضي: الفلتر والترتيب ما يوصلوش للإجمالي
                writeTotals(sheet.createRow(rowIndex++), request, sums, headerRowIndex + 1, lastDataRowIndex,
                        columnStyles, widths, rightToLeft);
            }

            widths.apply(sheet);
            // رأس الأعمدة بيفضل ظاهر والورقة بتتنزل، وعليه أزرار الفلتر: الورقة تتفرز وتتفلتر على طول.
            // الفلتر على الرأس والبيانات بس، من غير صف الإجمالي.
            sheet.createFreezePane(0, headerRowIndex + 1);
            sheet.setAutoFilter(new CellRangeAddress(headerRowIndex, lastDataRowIndex, 0, columns.size() - 1));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ReportException("Failed to generate Excel report.", e);
        } finally {
            workbook.dispose(); // بيمسح الملفات المؤقتة اللي SXSSF عملها
        }
    }

    /**
     * صف الإجمالي: معادلة {@code SUM} تحت كل عمود متجمّع على صفوف بياناته، ومعاها قيمتها المحسوبة — Excel
     * بيعرض القيمة المحفوظة لحد ما يحسب المعادلة، والملف من غير قيمة محفوظة كان هيعرض صفر في أي برنامج ما
     * بيحسبش وهو بيفتح. الكلمة تحت أول عمود مش متجمّع، والخلايا الفاضية بنفس الشكل فخطوط الإجمالي بعرض
     * الجدول كله.
     *
     * @param firstDataRow أول صف بيانات (من صفر)
     * @param lastDataRow  آخر صف بيانات؛ أقل من {@code firstDataRow} لو مفيش بيانات
     */
    private static void writeTotals(Row row, ReportRequest<?> request, BigDecimal[] sums, int firstDataRow,
                                    int lastDataRow, ColumnStyles[] columnStyles, ColumnWidths widths,
                                    boolean rightToLeft) {
        List<ColumnDefinition> columns = request.columns();
        int labelAt = Totals.labelColumn(request);
        for (int i = 0; i < columns.size(); i++) {
            Cell cell = row.createCell(i);
            if (columns.get(i).total()) {
                if (lastDataRow >= firstDataRow) {
                    String column = CellReference.convertNumToColString(i);
                    cell.setCellFormula("SUM(" + column + (firstDataRow + 1) + ":" + column + (lastDataRow + 1) + ")");
                }
                // على خلية فيها معادلة دي القيمة المحسوبة المحفوظة معاها؛ من غير معادلة، صفر عادي.
                cell.setCellValue(sums[i].doubleValue());
                cell.setCellStyle(columnStyles[i].total(true));
            } else {
                if (i == labelAt) {
                    cell.setCellValue(Totals.label(request, rightToLeft));
                }
                cell.setCellStyle(columnStyles[i].total(false));
            }
            widths.totals(cell, i);
        }
    }

    /**
     * التواريخ في Excel أرقام تسلسلية، فبدون تنسيق تاريخ على الخلية بيظهروا للمستخدم كرقم
     * زي {@code 45245.01} بدل التاريخ نفسه؛ عشان كده كل نوع تاريخ بياخد الـ style المناسب له.
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
     * styles عمود واحد، بتتعمل أول ما قيمة محتاجاها تيجي: نمط العمود (لو فيه) بصيغة Excel ومحاذاته.
     * النمط بيتحوّل حسب نوع القيمة — أرقام أو تواريخ — فنمط تاريخ في عمود أرقام ما بيتلمسش.
     *
     * <p>Excel بيقف عند 64,000 style، فكل عمود بيعمل style واحد لكل صيغة، مش لكل خلية.</p>
     */
    private static final class ColumnStyles {

        private final Workbook workbook;
        private final String format;
        private final HorizontalAlignment alignment;
        private final Font bold;
        private final Map<String, CellStyle> made = new HashMap<>();

        /** @param bold خط صف الإجمالي، واحد للملف كله */
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

        /** نص أو قيمة منطقية: المحاذاة بس، و{@code null} لو مفيش (زي الأول بالظبط). */
        CellStyle text() {
            return alignment == null ? null : style("text", null);
        }

        CellStyle number() {
            return format == null ? text() : style("number", ExcelFormats.number(format));
        }

        /** تاريخ: نمط العمود لو فيه، وإلا الصيغة الافتراضية لنوعه. */
        CellStyle date(String defaultFormat) {
            String excel = format == null ? defaultFormat : ExcelFormats.date(format);
            return style("date|" + excel, excel);
        }

        /**
         * خلية في صف الإجمالي: خط عريض، وخط رفيع فوقها وخطين تحتها زي إجمالي الدفاتر، والمجموع بنمط العمود.
         *
         * @param number المجموع نفسه، مش الكلمة أو خلية فاضية
         */
        CellStyle total(boolean number) {
            String key = number ? "total|number" : "total|text";
            CellStyle known = made.get(key);
            if (known != null) {
                return known;
            }
            CellStyle created = style(key, number && format != null ? ExcelFormats.number(format) : null);
            created.setFont(bold);
            created.setBorderTop(BorderStyle.THIN);
            created.setBorderBottom(BorderStyle.DOUBLE);
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
     * عرض الأعمدة بنفس قياس {@code autoSizeColumn} (خط الخلية والنص زي ما هيظهر بنمطه، عربي أو أرقام
     * أو تواريخ)، بس من غير ما كل خلية في الورقة تتقاس: {@code trackAllColumnsForAutoSizing} كان بيعمل
     * كده فكان التقرير الكبير أبطأ حوالي 5 مرات.
     *
     * <ul>
     *     <li><b>رأس الأعمدة</b> بيتقاس ومعاه مكان زرار الفلتر.</li>
     *     <li><b>النصوص</b> من أول {@link #WIDTH_SAMPLE_ROWS} صف بس؛ النص الأطول بعدها بيتقص عند حافة
     *         العمود وبيبان كامل في الخلية نفسها.</li>
     *     <li><b>الأرقام</b> في كل الصفوف، بس الرقم بيتقاس لما نصه بنمطه يبقى أطول من اللي اتقاس قبله،
     *         عشان الرقم الأعرض ما يطلعش {@code ####} (شوف {@link #number}).</li>
     *     <li><b>التواريخ</b> مرة لكل نمط في العمود، بأعرض تاريخ ممكن ({@link #WIDEST_DATE}).</li>
     * </ul>
     *
     * <p>صف الإجمالي بيتقاس كله. العنوان مش منهم، والعرض ما بيعدّيش أقصى عرض في Excel (255 حرف).</p>
     */
    private static final class ColumnWidths {

        private final double[] widths;
        /** طول نص أطول رقم اتقاس في كل عمود (زي ما بيظهر بنمطه). */
        private final int[] longestNumber;
        /** أنماط التواريخ اللي اتقاست في كل عمود، برقم الـ style. */
        private final List<Set<Short>> datesMeasured;
        /** رقم الـ style ← هل صيغته تاريخ؟ (الخلية من غير style بيرجع لها كائن جديد كل مرة، فالمفتاح رقمه.) */
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

        /** خلية في صف الإجمالي بتتقاس دايمًا، بخطها العريض: المجموع عادةً أعرض رقم في عموده. */
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
         * الرقم بيتنسّق زي ما هيظهر (من غير قياس خط، فده أرخص بكتير) وبيتقاس بس لو نصه أطول من أطول رقم
         * اتقاس في عموده. حجم الرقم لوحده ما بيكفيش: بنمط {@code 0.########} الـ 1 والـ 1.12345678 في
         * نفس الخانات الصحيحة والتاني أعرض بكتير. النص اللي في نفس الطول فرقه بالكتير رقم مكان فاصلة أو علامة، والـ {@code 0} اللي القياس
         * بيزوّده على النص بيغطيه.
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

    /** الوقت في Excel كسر من اليوم (0.5 = الظهر). */
    private static double fractionOfDay(LocalTime time) {
        return time.toSecondOfDay() / SECONDS_PER_DAY;
    }

    /**
     * الـ styles بتتعمل مرة واحدة للملف كله، مش لكل خلية:
     * Excel بيقف عند 64,000 style وبيرفض يفتح الملف لو اتعدّاهم.
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

    private String sheetName(String title) {
        if (title == null || title.isBlank()) return "Report";
        String sanitized = title.replaceAll("[\\\\/*\\[\\]:?]", " ").trim();
        if (sanitized.isEmpty()) return "Report";
        return sanitized.length() > 31 ? sanitized.substring(0, 31) : sanitized;
    }

    private record Styles(CellStyle title, CellStyle header, Font bold) {}
}
