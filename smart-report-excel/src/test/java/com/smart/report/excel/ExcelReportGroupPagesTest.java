package com.smart.report.excel;

import com.smart.report.ColumnDefinition;
import com.smart.report.ReportRequest;
import com.smart.report.ReportService;
import com.smart.report.ReportType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** عنوان لكل مجموعة، وفاصل صفحات بعد كل مجموعة، في تقرير الـ Excel العام. */
class ExcelReportGroupPagesTest {

    public record Line(String customer, String item, BigDecimal amount) {}

    private static final List<Line> LINES = List.of(
            new Line("Ahmed", "Bolt", new BigDecimal("10")),
            new Line("Ahmed", "Nut", new BigDecimal("5")),
            new Line("Mona", "Screw", new BigDecimal("7")),
            new Line("Omar", "Washer", new BigDecimal("3")));

    private static XSSFWorkbook excel(ReportRequest.Builder<Line> request) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(ReportService.generate(ReportType.EXCEL, request.build())));
    }

    private static ReportRequest.Builder<Line> request() {
        return ReportRequest.<Line>builder().data(LINES)
                .addColumn(ColumnDefinition.of("customer", "Customer"))
                .addColumn(ColumnDefinition.of("item", "Item"))
                .addColumn(ColumnDefinition.of("amount", "Amount").withTotal())
                .groupBy("customer");
    }

    private static String text(Row row, int column) {
        Cell cell = row == null ? null : row.getCell(column);
        return cell == null ? "" : cell.getCellType() == CellType.STRING ? cell.getStringCellValue() : "#";
    }

    @Test
    void aBoldHeaderRowAboveEveryGroup() throws Exception {
        try (XSSFWorkbook workbook = excel(request().groupHeader(true))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            List<String> first = new ArrayList<>();
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                first.add(text(sheet.getRow(r), 1).isEmpty() ? text(sheet.getRow(r), 0) : "·" + text(sheet.getRow(r), 1));
            }
            // رأس الأعمدة، وبعدين لكل مجموعة: عنوانها، صفوفها، إجماليها.
            // (صفوف الإجمالي زي ما هي من غير العنوان: الكلمة في عمود Item.)
            assertEquals(List.of("·Item", "Ahmed", "·Bolt", "·Nut", "·Total", "Mona", "·Screw", "·Total",
                    "Omar", "·Washer", "·Total", "", "·#"), first);
            assertEquals("Ahmed", sheet.getRow(4).getCell(0).getStringCellValue(), "الإجمالي الفرعي باسم المجموعة");
            Row header = sheet.getRow(1);
            assertTrue(workbook.getFontAt(header.getCell(0).getCellStyle().getFontIndex()).getBold());
            assertEquals(null, header.getCell(2), "مفيش أرقام في صف العنوان");

            // الإجماليات الفرعية على صفوف المجموعة بس، والقيم زي حساب Excel.
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            assertEquals("SUBTOTAL(9,C3:C4)", sheet.getRow(4).getCell(2).getCellFormula());
            assertEquals(15.0, evaluator.evaluate(sheet.getRow(4).getCell(2)).getNumberValue());
            assertEquals("SUBTOTAL(9,C7:C7)", sheet.getRow(7).getCell(2).getCellFormula());
            assertEquals(25.0, evaluator.evaluate(sheet.getRow(12).getCell(2)).getNumberValue());
        }
    }

    @Test
    void aPageBreakAfterEveryGroup() throws Exception {
        try (XSSFWorkbook workbook = excel(request().pageBreakPerGroup(true))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            // صف 0 الرأس؛ Ahmed صفين وإجماليه في 3، Mona في 4 وإجماليه 5، Omar في 6 وإجماليه 7.
            assertArrayEquals(new int[]{3, 5}, sheet.getRowBreaks());
        }
        try (XSSFWorkbook workbook = excel(request())) {
            assertEquals(0, workbook.getSheetAt(0).getRowBreaks().length);
        }
    }
}
