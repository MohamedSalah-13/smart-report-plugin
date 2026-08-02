package com.smart.report.excel;

import com.smart.report.ReportRequest;
import com.smart.report.ReportService;
import com.smart.report.ReportType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
