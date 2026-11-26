package com.smart.report.excel;

import com.smart.report.Alignment;
import com.smart.report.ColumnDefinition;
import com.smart.report.ReportException;
import com.smart.report.ReportRequest;
import com.smart.report.ReportService;
import com.smart.report.ReportType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** خيارات العمود (النمط والمحاذاة) في الـ Excel زي الـ PDF. */
class ExcelColumnOptionsTest {

    public record Sale(String name, double amount, LocalDate day, LocalDateTime at) {}

    private static final Sale SALE = new Sale("Ahmed", 1234.4, LocalDate.of(2026, 11, 26),
            LocalDateTime.of(2026, 11, 26, 9, 5));

    /** أول صف بيانات (من غير عنوان: الرأس في 0). */
    private static Row firstRow(ColumnDefinition... columns) throws Exception {
        byte[] xlsx = ReportService.generate(ReportType.EXCEL, ReportRequest.<Sale>builder()
                .data(List.of(SALE)).columns(List.of(columns)).build());
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            return workbook.getSheetAt(0).getRow(1);
        }
    }

    private static String shown(Cell cell) {
        return new DataFormatter(Locale.ENGLISH).formatCellValue(cell);
    }

    @Test
    void numberAndDateFormatsAreAppliedAndTheValuesStayNative() throws Exception {
        Row row = firstRow(
                ColumnDefinition.of("amount", "Amount").withFormat("#,##0.00"),
                ColumnDefinition.of("day", "Day").withFormat("dd/MM/yyyy"),
                ColumnDefinition.of("at", "At").withFormat("d MMMM yyyy HH:mm"),
                ColumnDefinition.of("amount", "Plain"));

        assertEquals("#,##0.00", row.getCell(0).getCellStyle().getDataFormatString());
        assertEquals("1,234.40", shown(row.getCell(0)));
        assertEquals(1234.4, row.getCell(0).getNumericCellValue(), 1e-9, "الرقم رقم، مش نص");
        assertEquals("dd/mm/yyyy", row.getCell(1).getCellStyle().getDataFormatString());
        assertEquals("26/11/2026", shown(row.getCell(1)));
        assertEquals("26 November 2026 09:05", shown(row.getCell(2)));
        assertEquals("1234.4", shown(row.getCell(3)), "العمود من غير نمط زي ما كان");
    }

    /** نفس نمط الـ PDF بيتحوّل لصيغة Excel. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "#,##0.00         | #,##0.00",
            "0.0%             | 0.0%",
            "#,##0 'EGP'      | #,##0\" EGP\"",
            "#,##0 جنيه       | #,##0\" جنيه\"",
            "0.###E0          | 0.###E+0",
            // الفاصلة في آخر صيغة Excel بتقسم على ألف: المكتوبة بين علامتي تنصيص بتفضل كده.
            "0','             | 0\",\"",
            "#,##0'.-'        | #,##0\".-\"",
    })
    void numberPatternsBecomeExcelFormats(String java, String excel) {
        assertEquals(excel, ExcelFormats.number(java));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "dd/MM/yyyy             | dd/mm/yyyy",
            "yyyy-MM-dd HH:mm:ss    | yyyy-mm-dd hh:mm:ss",
            "d MMM yy               | d mmm yy",
            "EEEE d MMMM            | dddd d mmmm",
            "hh:mm a                | hh:mm AM/PM",
            "HH:mm:ss.SSS           | hh:mm:ss.000",
            "yyyy-MM-dd'T'HH:mm     | yyyy-mm-dd\"T\"hh:mm",
            "dd 'of' MMMM           | dd\" of \"mmmm",
            "HH 'h' mm              | hh\" h \"mm",
    })
    void datePatternsBecomeExcelFormats(String java, String excel) {
        assertEquals(excel, ExcelFormats.date(java));
    }

    /**
     * Excel بيعرض الساعة بنظام 24 إلا مع AM/PM: الأنماط اللي كانت هتعرض ساعة غير اللي Java بتطبعها
     * ({@code hh:mm} لـ 13:05 كانت هتطلع 13:05 بدل 01:05) بتترفض.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"hh:mm", "h:mm", "HH:mm a", "kk:mm", "KK:mm a"})
    void clockPatternsExcelWouldShowDifferentlyAreRejected(String java) {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> ExcelFormats.date(java));
        assertTrue(thrown.getMessage().contains(java), thrown.getMessage());
    }

    /** نفس الوقت في Java وفي الـ Excel بعد التحويل. */
    @Test
    void twelveHourClockShowsTheSameTimeInExcel() throws Exception {
        Row row = firstRow(ColumnDefinition.of("at", "At").withFormat("hh:mm a"));
        assertEquals("09:05 AM", shown(row.getCell(0)));
    }

    @Test
    void patternWithNoExcelEquivalentIsReportedWithTheColumnName() {
        ReportException thrown = assertThrows(ReportException.class, () -> firstRow(
                ColumnDefinition.of("at", "At").withFormat("HH:mm z")));
        assertTrue(thrown.getMessage().contains("'at'") && thrown.getMessage().contains("'z'"), thrown.getMessage());

        assertThrows(IllegalArgumentException.class, () -> ExcelFormats.number("#,##0‰"));
        assertThrows(IllegalArgumentException.class, () -> ExcelFormats.date("dd 'unclosed"));
    }

    /** نمط التاريخ في عمود أرقام ما بيتحوّلش: بيتحوّل حسب نوع القيمة. */
    @Test
    void patternIsConvertedForTheKindOfValueOnly() throws Exception {
        Row row = firstRow(ColumnDefinition.of("name", "Name").withFormat("#,##0 جنيه"));
        assertEquals("Ahmed", shown(row.getCell(0)));
    }

    /**
     * المحاذاة منسوبة للاتجاه، و Excel بيحاذي على الورقة نفسها: البداية يمين في الورقة العربية
     * وشمال في الإنجليزية.
     */
    @Test
    void alignmentFollowsTheSheetsDirection() throws Exception {
        Row ltr = firstRow(
                ColumnDefinition.of("name", "Name").withAlign(Alignment.START),
                ColumnDefinition.of("amount", "Amount").withAlign(Alignment.END),
                ColumnDefinition.of("day", "Day").withAlign(Alignment.CENTER),
                ColumnDefinition.of("name", "Plain"));
        assertEquals(HorizontalAlignment.LEFT, ltr.getCell(0).getCellStyle().getAlignment());
        assertEquals(HorizontalAlignment.RIGHT, ltr.getCell(1).getCellStyle().getAlignment());
        assertEquals(HorizontalAlignment.CENTER, ltr.getCell(2).getCellStyle().getAlignment());
        assertEquals("yyyy-mm-dd", ltr.getCell(2).getCellStyle().getDataFormatString(), "التاريخ بصيغته الافتراضية");
        assertEquals(HorizontalAlignment.GENERAL, ltr.getCell(3).getCellStyle().getAlignment());

        Row rtl = firstRow(
                ColumnDefinition.of("name", "الاسم").withAlign(Alignment.START),
                ColumnDefinition.of("amount", "المبلغ").withAlign(Alignment.END));
        assertEquals(HorizontalAlignment.RIGHT, rtl.getCell(0).getCellStyle().getAlignment());
        assertEquals(HorizontalAlignment.LEFT, rtl.getCell(1).getCellStyle().getAlignment());
    }

    @Test
    void weightIsIgnoredInExcel() throws Exception {
        Row row = firstRow(ColumnDefinition.of("name", "Name").withWeight(5));
        assertEquals("Ahmed", shown(row.getCell(0)));
        assertEquals(HorizontalAlignment.GENERAL, row.getCell(0).getCellStyle().getAlignment());
    }
}
