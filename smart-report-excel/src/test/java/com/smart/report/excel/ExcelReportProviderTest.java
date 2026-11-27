package com.smart.report.excel;

import com.smart.report.ReportRequest;
import com.smart.report.ReportService;
import com.smart.report.ReportType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.PaneInformation;
import org.apache.poi.xssf.usermodel.XSSFName;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcelReportProviderTest {

    public static class Record {
        public Date getLegacyDate() {
            return new GregorianCalendar(2023, java.util.Calendar.NOVEMBER, 14, 10, 30, 0).getTime();
        }

        public LocalDate getLocalDate() {
            return LocalDate.of(2023, 11, 14);
        }

        public LocalDateTime getLocalDateTime() {
            return LocalDateTime.of(2023, 11, 14, 10, 30);
        }

        public java.sql.Date getSqlDate() {
            return java.sql.Date.valueOf(LocalDate.of(2023, 11, 14));
        }

        public double getAmount() {
            return 1234.5;
        }

        public String getName() {
            return "Ahmed";
        }
    }

    private Row dataRow() throws Exception {
        ReportRequest<Record> request = ReportRequest.<Record>builder()
                .title("Dates")
                .data(List.of(new Record()))
                .addColumn("legacyDate", "Legacy")
                .addColumn("localDate", "Local")
                .addColumn("localDateTime", "LocalDateTime")
                .addColumn("sqlDate", "SqlDate")
                .addColumn("amount", "Amount")
                .addColumn("name", "Name")
                .build();

        byte[] xlsx = ReportService.generate(ReportType.EXCEL, request);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheetAt(0);
            return sheet.getRow(3); // عنوان + سطر فاضي + رأس أعمدة
        }
    }

    private static boolean sheetRunsRightToLeft(ReportRequest<Record> request) throws Exception {
        byte[] xlsx = ReportService.generate(ReportType.EXCEL, request);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            return workbook.getSheetAt(0).isRightToLeft();
        }
    }

    /** نفس الطلب كان بيطلع PDF عربيًا من اليمين وورقة Excel أعمدتها من الشمال. */
    @Test
    void arabicReportSheetRunsRightToLeft() throws Exception {
        assertTrue(sheetRunsRightToLeft(ReportRequest.<Record>builder()
                .data(List.of(new Record())).addColumn("name", "الاسم").addColumn("amount", "المبلغ").build()));
        assertTrue(sheetRunsRightToLeft(ReportRequest.<Record>builder()
                .title("تقرير").data(List.of(new Record())).addColumn("name", "Name").build()),
                "العنوان العربي لوحده كفاية");
        assertFalse(sheetRunsRightToLeft(ReportRequest.<Record>builder()
                .title("Report").data(List.of(new Record())).addColumn("name", "Name").build()));
    }

    @Test
    void directionCanBeChosenThroughParameters() throws Exception {
        assertTrue(sheetRunsRightToLeft(ReportRequest.<Record>builder()
                .data(List.of(new Record())).addColumn("name", "Name")
                .parameter(ExcelReportProvider.RIGHT_TO_LEFT, true).build()));
        assertFalse(sheetRunsRightToLeft(ReportRequest.<Record>builder()
                .data(List.of(new Record())).addColumn("name", "الاسم")
                .parameter(ExcelReportProvider.RIGHT_TO_LEFT, false).build()));
    }

    /** كانت الخلايا بتطلع أرقامًا خامًا زي 45245.01 بدل التاريخ. */
    @Test
    void datesAreWrittenWithARealDateFormat() throws Exception {
        Row row = dataRow();
        for (int column = 0; column <= 3; column++) {
            Cell cell = row.getCell(column);
            assertEquals(CellType.NUMERIC, cell.getCellType(), "العمود " + column);
            assertTrue(DateUtil.isCellDateFormatted(cell),
                    "العمود " + column + " لازم يبقى متنسّق كتاريخ، التنسيق الحالي: "
                            + cell.getCellStyle().getDataFormatString());
        }
    }

    @Test
    void dateValuesRoundTripToTheOriginalInstant() throws Exception {
        Row row = dataRow();
        assertEquals(LocalDateTime.of(2023, 11, 14, 10, 30), row.getCell(0).getLocalDateTimeCellValue());
        assertEquals(LocalDate.of(2023, 11, 14), row.getCell(1).getLocalDateTimeCellValue().toLocalDate());
        assertEquals(LocalDateTime.of(2023, 11, 14, 10, 30), row.getCell(2).getLocalDateTimeCellValue());
        assertEquals(LocalDate.of(2023, 11, 14), row.getCell(3).getLocalDateTimeCellValue().toLocalDate());
    }

    @Test
    void datesUseDayLevelFormatWhenThereIsNoTimePart() throws Exception {
        Row row = dataRow();
        assertEquals("yyyy-mm-dd", row.getCell(1).getCellStyle().getDataFormatString());
        assertEquals("yyyy-mm-dd hh:mm:ss", row.getCell(2).getCellStyle().getDataFormatString());
    }

    @Test
    void numbersAndTextKeepTheirOwnTypes() throws Exception {
        Row row = dataRow();
        assertEquals(CellType.NUMERIC, row.getCell(4).getCellType());
        assertEquals(1234.5, row.getCell(4).getNumericCellValue(), 0.0001);
        assertEquals(CellType.STRING, row.getCell(5).getCellType());
        assertEquals("Ahmed", row.getCell(5).getStringCellValue());
    }

    public record Line(int index, String label) {}

    /** الصفوف بتتكتب على القرص أول بأول، فتقرير كبير ما بيفضلش كله في الذاكرة. */
    @Test
    void handlesLargeReportsAndKeepsEveryRow() throws Exception {
        int rows = 50_000;
        List<Line> data = new java.util.ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            data.add(new Line(i, "row-" + i));
        }

        byte[] xlsx = ReportService.generate(ReportType.EXCEL, ReportRequest.<Line>builder()
                .data(data)
                .addColumn("index", "Index")
                .addColumn("label", "Label")
                .build());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals(rows, sheet.getLastRowNum(), "لازم كل الصفوف تتكتب"); // بدون عنوان: رأس الأعمدة في 0
            assertEquals("row-0", sheet.getRow(1).getCell(1).getStringCellValue());
            assertEquals("row-" + (rows - 1), sheet.getRow(rows).getCell(1).getStringCellValue());
            assertEquals(rows - 1, (int) sheet.getRow(rows).getCell(0).getNumericCellValue());
        }
    }

    private static <T> XSSFWorkbook open(ReportRequest<T> request) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(ReportService.generate(ReportType.EXCEL, request)));
    }

    /** عرض كل عمود بوحدات Excel (1/256 من عرض الحرف). */
    private static <T> int[] columnWidths(ReportRequest<T> request) throws Exception {
        try (XSSFWorkbook workbook = open(request)) {
            Sheet sheet = workbook.getSheetAt(0);
            int[] widths = new int[request.columns().size()];
            for (int i = 0; i < widths.length; i++) {
                widths[i] = sheet.getColumnWidth(i);
            }
            return widths;
        }
    }

    private static ReportRequest<Line> lines(String title, List<Line> data) {
        return ReportRequest.<Line>builder()
                .title(title)
                .data(data)
                .addColumn("index", "الرقم")
                .addColumn("label", "البيان")
                .build();
    }

    /** العنوان كان بيتقاس مع العمود الأول: 15,959 وحدة مع عنوان طويل بدل 1,153. */
    @Test
    void longTitleDoesNotWidenTheFirstColumn() throws Exception {
        List<Line> data = List.of(new Line(1, "إيجار"), new Line(2, "كهرباء"));
        String title = "كشف حساب المصروفات الشهرية التفصيلي لكل فروع الشركة عن شهر نوفمبر 2026";

        assertArrayEquals(columnWidths(lines(null, data)), columnWidths(lines(title, data)));
        try (XSSFWorkbook workbook = open(lines(title, data))) {
            assertEquals(title, workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
        }
    }

    private record Frozen(int rows, int columns, String filter, String filterName) {}

    private static <T> Frozen frozenAndFiltered(ReportRequest<T> request) throws Exception {
        try (XSSFWorkbook workbook = open(request)) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            PaneInformation pane = sheet.getPaneInformation();
            assertNotNull(pane, "مفيش أي جزء ثابت في الورقة");
            assertTrue(pane.isFreezePane());
            List<XSSFName> names = workbook.getNames("_xlnm._FilterDatabase");
            assertEquals(1, names.size(), "Excel بيحتاج الاسم ده عشان يعرف نطاق الفلتر");
            return new Frozen(pane.getHorizontalSplitPosition(), pane.getVerticalSplitPosition(),
                    sheet.getCTWorksheet().getAutoFilter().getRef(), names.get(0).getRefersToFormula());
        }
    }

    /** الرأس بيفضل ظاهر وعليه فلتر من أول الأعمدة لآخرها ومن الرأس لآخر صف. */
    @Test
    void headerRowIsFrozenAndFiltered() throws Exception {
        List<Line> data = List.of(new Line(1, "إيجار"), new Line(2, "كهرباء"));

        assertEquals(new Frozen(1, 0, "A1:B3", "Report!$A$1:$B$3"), frozenAndFiltered(lines(null, data)));
        // العنوان والسطر الفاضي فوق الرأس بيفضلوا ظاهرين معاه؛ واسم الورقة فيه علامة تنصيص
        assertEquals(new Frozen(3, 0, "A3:B5", "'Ahmed''s مصروفات'!$A$3:$B$5"),
                frozenAndFiltered(lines("Ahmed's مصروفات", data)));
        assertEquals(new Frozen(1, 0, "A1:B1", "Report!$A$1:$B$1"), frozenAndFiltered(lines(null, List.of())),
                "من غير بيانات الفلتر على الرأس بس");
    }

    /** النص العربي الطويل بياخد عرضه، والعرض ما بيعدّيش أقصى عرض في Excel (255 حرف). */
    @Test
    void columnsFitLongArabicValues() throws Exception {
        String longText = "صرف مكافأة نهاية الخدمة للموظف حسب قرار مجلس الإدارة رقم ١٢";
        int shortWidth = columnWidths(lines(null, List.of(new Line(1, "إيجار"))))[1];
        int longWidth = columnWidths(lines(null, List.of(new Line(1, "إيجار"), new Line(2, longText))))[1];

        assertTrue(longWidth > shortWidth, longWidth + " مش أعرض من " + shortWidth);
        double characters = longWidth / 256.0;
        assertTrue(characters >= longText.length() * 0.5 && characters <= longText.length() * 1.5,
                "عرض " + characters + " حرف لنص طوله " + longText.length());

        String tooLong = "تفاصيل ".repeat(80);
        assertEquals(255 * 256, columnWidths(lines(null, List.of(new Line(1, tooLong))))[1]);
    }

    /**
     * النصوص بتتقاس في أول {@link ExcelReportProvider#WIDTH_SAMPLE_ROWS} صف بس: قياس كل خلية كان
     * بيبطّأ التقرير الكبير حوالي 5 مرات.
     */
    @Test
    void textPastTheSampleDoesNotWidenTheColumn() throws Exception {
        List<Line> data = new java.util.ArrayList<>();
        for (int i = 0; i < ExcelReportProvider.WIDTH_SAMPLE_ROWS; i++) {
            data.add(new Line(i, "إيجار"));
        }
        int sampled = columnWidths(lines(null, data))[1];
        data.add(new Line(0, "صرف مكافأة نهاية الخدمة للموظف حسب قرار مجلس الإدارة"));

        assertEquals(sampled, columnWidths(lines(null, data))[1]);
    }

    /** أكتر من طاقة الورقة كان بيرمي IllegalArgumentException من جوّه POI. */
    @Test
    void rejectsMoreRowsThanASheetCanHold() {
        List<Line> tooMany = java.util.Collections.nCopies(1_048_577, new Line(1, "x"));

        com.smart.report.ReportException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                com.smart.report.ReportException.class,
                () -> ReportService.generate(ReportType.EXCEL, ReportRequest.<Line>builder()
                        .data(tooMany)
                        .addColumn("label", "Label")
                        .build()));
        assertTrue(thrown.getMessage().contains("1048576"), thrown.getMessage());
    }
}
