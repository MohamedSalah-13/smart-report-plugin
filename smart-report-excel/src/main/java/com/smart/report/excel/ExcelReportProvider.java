package com.smart.report.excel;

import com.smart.report.ColumnDefinition;
import com.smart.report.ReportException;
import com.smart.report.ReportProvider;
import com.smart.report.ReportRequest;
import com.smart.report.ReportType;
import com.smart.report.util.BeanPropertyReader;
import com.smart.report.util.TextDirection;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
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
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * يولّد تقرير Excel (.xlsx) عام (عنوان + رأس أعمدة منسّق + صفوف بيانات) بدون الحاجة لأي قالب،
 * انطلاقًا من أي List من الكائنات + قائمة ColumnDefinition.
 *
 * <p>الأرقام والمنطقيات والتواريخ بتتكتب بأنواعها الأصلية في Excel (مش كنصوص)، والتواريخ بياخدوا
 * تنسيق تاريخ فعلي عشان يظهروا كتواريخ بدل الرقم التسلسلي الخام.</p>
 *
 * <p><b>الاتجاه:</b> الورقة من اليمين لليسار (أول عمود على اليمين) لو العنوان أو أي رأس عمود فيه
 * عربي — نفس قاعدة تقرير الـ PDF العام — إلا لو {@link #RIGHT_TO_LEFT} اتحدد في {@code parameters}.</p>
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
        long totalRows = (long) request.data().size() + headerRows;
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
            // بيتابع عرض الأعمدة والصفوف بتتكتب، فـ autoSizeColumn تفضل شغالة من غير
            // ما نحتفظ بكل الصفوف في الذاكرة
            sheet.trackAllColumnsForAutoSizing();
            Styles styles = createStyles(workbook);
            ColumnStyles[] columnStyles = new ColumnStyles[columns.size()];
            for (int i = 0; i < columns.size(); i++) {
                columnStyles[i] = new ColumnStyles(workbook, columns.get(i), rightToLeft);
            }

            int rowIndex = 0;
            if (headerRows == 3) {
                Row titleRow = sheet.createRow(rowIndex++);
                Cell titleCell = titleRow.createCell(0);
                titleCell.setCellValue(request.title());
                titleCell.setCellStyle(styles.title());
                rowIndex++; // سطر فاصل
            }

            Row headerRow = sheet.createRow(rowIndex++);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns.get(i).header());
                cell.setCellStyle(styles.header());
            }

            for (Object item : request.data()) {
                Row row = sheet.createRow(rowIndex++);
                for (int i = 0; i < columns.size(); i++) {
                    Object value = BeanPropertyReader.read(item, columns.get(i).fieldName());
                    try {
                        writeCell(row.createCell(i), value, columnStyles[i]);
                    } catch (IllegalArgumentException e) {
                        throw new ReportException("Column '" + columns.get(i).fieldName() + "': " + e.getMessage(), e);
                    }
                }
            }

            for (int i = 0; i < columns.size(); i++) {
                sheet.autoSizeColumn(i);
            }

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
        private final Map<String, CellStyle> made = new HashMap<>();

        ColumnStyles(Workbook workbook, ColumnDefinition column, boolean rightToLeft) {
            this.workbook = workbook;
            this.format = column.format();
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

        return new Styles(title, header);
    }

    private String sheetName(String title) {
        if (title == null || title.isBlank()) return "Report";
        String sanitized = title.replaceAll("[\\\\/*\\[\\]:?]", " ").trim();
        if (sanitized.isEmpty()) return "Report";
        return sanitized.length() > 31 ? sanitized.substring(0, 31) : sanitized;
    }

    private record Styles(CellStyle title, CellStyle header) {}
}
