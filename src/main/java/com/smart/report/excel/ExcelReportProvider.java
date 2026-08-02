package com.smart.report.excel;

import com.smart.report.ColumnDefinition;
import com.smart.report.ReportException;
import com.smart.report.ReportProvider;
import com.smart.report.ReportRequest;
import com.smart.report.util.BeanPropertyReader;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
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
import java.util.List;

/**
 * يولّد تقرير Excel (.xlsx) عام (عنوان + رأس أعمدة منسّق + صفوف بيانات) بدون الحاجة لأي قالب،
 * انطلاقًا من أي List من الكائنات + قائمة ColumnDefinition.
 *
 * <p>الأرقام والمنطقيات والتواريخ بتتكتب بأنواعها الأصلية في Excel (مش كنصوص)، والتواريخ بياخدوا
 * تنسيق تاريخ فعلي عشان يظهروا كتواريخ بدل الرقم التسلسلي الخام.</p>
 */
public final class ExcelReportProvider implements ReportProvider {

    private static final String DATE_FORMAT = "yyyy-mm-dd";
    private static final String DATE_TIME_FORMAT = "yyyy-mm-dd hh:mm:ss";
    private static final String TIME_FORMAT = "hh:mm:ss";
    private static final double SECONDS_PER_DAY = 86_400d;

    /** عدد الصفوف المحفوظة في الذاكرة قبل ما تتكتب على القرص. */
    private static final int ROW_WINDOW = 200;

    /** أقصى عدد صفوف في ورقة xlsx واحدة. */
    private static final int MAX_ROWS = 1_048_576;

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
            // بيتابع عرض الأعمدة والصفوف بتتكتب، فـ autoSizeColumn تفضل شغالة من غير
            // ما نحتفظ بكل الصفوف في الذاكرة
            sheet.trackAllColumnsForAutoSizing();
            Styles styles = createStyles(workbook);

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
                    writeCell(row.createCell(i), value, styles);
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
    private void writeCell(Cell cell, Object value, Styles styles) {
        switch (value) {
            case null -> cell.setBlank();
            case Number number -> cell.setCellValue(number.doubleValue());
            case Boolean bool -> cell.setCellValue(bool);
            // أنواع java.sql بترث java.util.Date، فلازم تتفحص قبلها
            case java.sql.Date date -> {
                cell.setCellValue(date.toLocalDate());
                cell.setCellStyle(styles.date());
            }
            case java.sql.Time time -> {
                cell.setCellValue(fractionOfDay(time.toLocalTime()));
                cell.setCellStyle(styles.time());
            }
            case java.sql.Timestamp timestamp -> {
                cell.setCellValue(timestamp.toLocalDateTime());
                cell.setCellStyle(styles.dateTime());
            }
            case Date date -> {
                cell.setCellValue(date);
                cell.setCellStyle(styles.dateTime());
            }
            case Calendar calendar -> {
                cell.setCellValue(calendar);
                cell.setCellStyle(styles.dateTime());
            }
            case LocalDate date -> {
                cell.setCellValue(date);
                cell.setCellStyle(styles.date());
            }
            case LocalDateTime dateTime -> {
                cell.setCellValue(dateTime);
                cell.setCellStyle(styles.dateTime());
            }
            case LocalTime time -> {
                cell.setCellValue(fractionOfDay(time));
                cell.setCellStyle(styles.time());
            }
            case Instant instant -> {
                cell.setCellValue(LocalDateTime.ofInstant(instant, ZoneId.systemDefault()));
                cell.setCellStyle(styles.dateTime());
            }
            case ZonedDateTime zoned -> {
                cell.setCellValue(zoned.toLocalDateTime());
                cell.setCellStyle(styles.dateTime());
            }
            case OffsetDateTime offset -> {
                cell.setCellValue(offset.toLocalDateTime());
                cell.setCellStyle(styles.dateTime());
            }
            default -> cell.setCellValue(String.valueOf(value));
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

        return new Styles(title, header,
                dateStyle(workbook, DATE_FORMAT),
                dateStyle(workbook, DATE_TIME_FORMAT),
                dateStyle(workbook, TIME_FORMAT));
    }

    private CellStyle dateStyle(Workbook workbook, String format) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat(format));
        return style;
    }

    private String sheetName(String title) {
        if (title == null || title.isBlank()) return "Report";
        String sanitized = title.replaceAll("[\\\\/*\\[\\]:?]", " ").trim();
        if (sanitized.isEmpty()) return "Report";
        return sanitized.length() > 31 ? sanitized.substring(0, 31) : sanitized;
    }

    private record Styles(CellStyle title, CellStyle header, CellStyle date, CellStyle dateTime, CellStyle time) {}
}
