package com.smart.report.excel;

import com.smart.report.ColumnDefinition;
import com.smart.report.ReportException;
import com.smart.report.ReportRequest;
import com.smart.report.ReportService;
import com.smart.report.ReportType;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.SheetUtil;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** صف الإجمالي في تقرير الـ Excel العام ({@link ColumnDefinition#withTotal()}). */
class ExcelReportTotalsTest {

    public record Line(String name, Object amount, Object qty) {}

    private static final List<Line> LINES = List.of(
            new Line("Rent", new BigDecimal("1000.50"), 3),
            new Line("Power", new BigDecimal("250.25"), 40),
            new Line("Water", 0.1, null));

    private static final ColumnDefinition NAME = ColumnDefinition.of("name", "Name");
    private static final ColumnDefinition AMOUNT = ColumnDefinition.of("amount", "Amount").withFormat("#,##0.00").withTotal();
    private static final ColumnDefinition QTY = ColumnDefinition.of("qty", "Qty").withTotal();

    private static ReportRequest.Builder<Line> request(String title, List<Line> rows, ColumnDefinition... columns) {
        return ReportRequest.<Line>builder().title(title).data(rows).columns(List.of(columns));
    }

    private static XSSFWorkbook open(ReportRequest.Builder<Line> request) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(ReportService.generate(ReportType.EXCEL, request.build())));
    }

    /**
     * تحت كل عمود متجمّع معادلة SUM على صفوف البيانات بالظبط، ومعاها قيمتها محفوظة — نفس اللي Excel
     * بيحسبه — فالرقم صح قبل ما أي برنامج يحسب المعادلة.
     */
    @Test
    void totalsAreRealSumFormulasWithTheirCachedValues() throws Exception {
        try (XSSFWorkbook workbook = open(request(null, LINES, NAME, AMOUNT, QTY))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            // الرأس في 0، البيانات 1-3، سطر فاضي 4، الإجمالي 5
            assertNull(sheet.getRow(4), "سطر فاضي بين البيانات والإجمالي");
            Row totals = sheet.getRow(5);
            assertEquals(5, sheet.getLastRowNum());

            XSSFCell amount = (XSSFCell) totals.getCell(1);
            assertEquals(CellType.FORMULA, amount.getCellType());
            assertEquals("SUM(B2:B4)", amount.getCellFormula());
            assertEquals(CellType.NUMERIC, amount.getCachedFormulaResultType());
            assertEquals(1250.85, amount.getNumericCellValue(), 1e-9, "القيمة المحفوظة");
            assertEquals("SUM(C2:C4)", totals.getCell(2).getCellFormula());
            assertEquals(43, totals.getCell(2).getNumericCellValue(), 1e-9, "الخلية الفاضية ما بتزوّدش حاجة");

            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            assertEquals(amount.getNumericCellValue(), evaluator.evaluate(amount).getNumberValue(), 0.0,
                    "المعادلة بتحسب نفس الرقم المحفوظ بالظبط");
            assertEquals(43, evaluator.evaluate(totals.getCell(2)).getNumberValue(), 0.0);

            assertEquals("1,250.85", new DataFormatter(Locale.ENGLISH).formatCellValue(amount),
                    "المجموع بنمط عموده");
        }
    }

    /** القيمة المحفوظة للإجمالي، وقيمة معادلته لو اتحسبت من جديد على نفس الورقة. */
    private static double[] cachedAndRecalculated(List<Line> rows) throws Exception {
        try (XSSFWorkbook workbook = open(request(null, rows, NAME, ColumnDefinition.of("amount", "Amount").withTotal()))) {
            Cell total = workbook.getSheetAt(0).getRow(rows.size() + 2).getCell(1);
            assertEquals(CellType.FORMULA, total.getCellType());
            double cached = total.getNumericCellValue();
            double recalculated = workbook.getCreationHelper().createFormulaEvaluator().evaluate(total).getNumberValue();
            return new double[]{cached, recalculated};
        }
    }

    /**
     * الـ float بيتكتب في الخلية double: 0.1f = 0.10000000149011612. القيمة المحفوظة كانت المجموع العشري
     * (0.3)، وأول ما Excel يعيد الحساب كانت هتبقى 0.30000000447034836.
     */
    @Test
    void cachedTotalOfFloatsIsWhatTheFormulaGives() throws Exception {
        double[] total = cachedAndRecalculated(List.of(new Line("a", 0.1f, 1), new Line("b", 0.1f, 1),
                new Line("c", 0.1f, 1)));
        assertEquals(total[1], total[0], 0.0, "إعادة الحساب ما بتغيرش الإجمالي");
        assertEquals((double) 0.1f + (double) 0.1f + (double) 0.1f, total[0], 0.0);
    }

    /**
     * أرقام أطول من خانات الـ double بتتقرّب وهي بتتكتب: 9007199254740993 بقت 9007199254740992 في خليتها.
     * المجموع بالظبط (1) كان بيتحفظ، والمعادلة على نفس الخلايا بتدّي 0.
     */
    @Test
    void cachedTotalOfDecimalsBeyondADoubleIsWhatTheFormulaGives() throws Exception {
        double[] total = cachedAndRecalculated(List.of(new Line("a", new BigDecimal("9007199254740993"), 1),
                new Line("b", new BigDecimal("-9007199254740992"), 1)));
        assertEquals(total[1], total[0], 0.0, "إعادة الحساب ما بتغيرش الإجمالي");
        assertEquals(0, total[0], 0.0);
    }

    /** الترتيب بيفرق في جمع الـ double: القيمة المحفوظة بنفس ترتيب SUM، من أول صف لآخره. */
    @Test
    void cachedTotalAddsTheCellsInTheFormulasOrder() throws Exception {
        double[] total = cachedAndRecalculated(List.of(new Line("a", 1e16, 1), new Line("b", 1.0, 1),
                new Line("c", 1.0, 1), new Line("d", -1e16, 1), new Line("e", 0.1, 1)));
        assertEquals(total[1], total[0], 0.0);
    }

    /** المعادلة بتتبع البيانات مهما كان مكانها: العنوان بيزق الجدول تلات صفوف. */
    @Test
    void formulaRangeFollowsTheTitle() throws Exception {
        try (XSSFWorkbook workbook = open(request("Expenses", LINES, NAME, AMOUNT))) {
            Row totals = workbook.getSheetAt(0).getRow(7); // عنوان، سطر، رأس، 3 صفوف، سطر فاضي
            assertEquals("SUM(B4:B6)", totals.getCell(1).getCellFormula());
            assertEquals("Total", totals.getCell(0).getStringCellValue());
        }
    }

    /** صف الإجمالي عريض بخط فوقه وخطين تحته، والمجموع بنمط عموده؛ الكلمة تحت أول عمود مش متجمّع. */
    @Test
    void totalsRowIsBoldWithTheColumnsFormat() throws Exception {
        try (XSSFWorkbook workbook = open(request(null, LINES, NAME, AMOUNT, QTY))) {
            Row totals = workbook.getSheetAt(0).getRow(5);
            for (int i = 0; i < 3; i++) {
                Cell cell = totals.getCell(i);
                assertTrue(workbook.getFontAt(cell.getCellStyle().getFontIndex()).getBold(), "الخلية " + i);
                assertEquals(BorderStyle.THIN, cell.getCellStyle().getBorderTop());
                assertEquals(BorderStyle.DOUBLE, cell.getCellStyle().getBorderBottom());
            }
            assertEquals("Total", totals.getCell(0).getStringCellValue());
            assertEquals("#,##0.00", totals.getCell(1).getCellStyle().getDataFormatString());
            assertEquals("General", totals.getCell(2).getCellStyle().getDataFormatString(),
                    "العمود من غير نمط زي بياناته");
            // البيانات نفسها مش عريضة
            Row data = workbook.getSheetAt(0).getRow(1);
            assertFalse(workbook.getFontAt(data.getCell(1).getCellStyle().getFontIndex()).getBold());
        }
    }

    /** الفلتر على الرأس والبيانات بس: الترتيب ما يحطش الإجمالي وسط الصفوف. */
    @Test
    void filterAndFrozenHeaderLeaveTheTotalsRowOut() throws Exception {
        try (XSSFWorkbook workbook = open(request(null, LINES, NAME, AMOUNT, QTY))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            assertEquals("A1:C4", sheet.getCTWorksheet().getAutoFilter().getRef());
            assertEquals("Report!$A$1:$C$4", workbook.getNames("_xlnm._FilterDatabase").get(0).getRefersToFormula());
            assertEquals(1, sheet.getPaneInformation().getHorizontalSplitPosition());
        }
    }

    /** المجموع عادةً أعرض رقم في عموده، وبخط عريض: العمود لازم ييجي على قده وما يطلعش ####. */
    @Test
    void columnIsWideEnoughForItsTotal() throws Exception {
        List<Line> rows = new ArrayList<>(Collections.nCopies(40, new Line("x", new BigDecimal("999999.99"), 1)));
        int plainWidth;
        try (XSSFWorkbook workbook = open(request(null, rows, NAME,
                ColumnDefinition.of("amount", "Amount").withFormat("#,##0.00")))) {
            plainWidth = workbook.getSheetAt(0).getColumnWidth(1);
        }
        try (XSSFWorkbook workbook = open(request(null, rows, NAME, AMOUNT))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            Cell total = sheet.getRow(rows.size() + 2).getCell(1);
            assertEquals("39,999,999.60", new DataFormatter(Locale.ENGLISH).formatCellValue(total));
            double needed = SheetUtil.getCellWidth(total, SheetUtil.getDefaultCharWidthAsFloat(workbook),
                    new DataFormatter(), false);
            assertTrue(sheet.getColumnWidth(1) / 256.0 >= needed - 0.01,
                    "عرض " + sheet.getColumnWidth(1) / 256.0 + " والمجموع محتاج " + needed);
            assertTrue(sheet.getColumnWidth(1) > plainWidth, "المجموع وسّع العمود");
        }
    }

    /** الورقة العربية: الكلمة الافتراضية بالعربي، وكلمة البرنامج بتغلبها. */
    @Test
    void labelFollowsTheDirectionUnlessTheRequestSetsOne() throws Exception {
        ColumnDefinition name = ColumnDefinition.of("name", "البيان");
        ColumnDefinition amount = ColumnDefinition.of("amount", "المبلغ").withTotal();
        try (XSSFWorkbook workbook = open(request(null, LINES, name, amount))) {
            assertTrue(workbook.getSheetAt(0).isRightToLeft());
            assertEquals("الإجمالى", workbook.getSheetAt(0).getRow(5).getCell(0).getStringCellValue());
        }
        try (XSSFWorkbook workbook = open(request(null, LINES, name, amount).totalsLabel("المجموع"))) {
            assertEquals("المجموع", workbook.getSheetAt(0).getRow(5).getCell(0).getStringCellValue());
        }
    }

    /** من غير بيانات: مفيش صفوف تتجمع، فالإجمالي صفر عادي من غير معادلة. */
    @Test
    void emptyReportTotalsZero() throws Exception {
        try (XSSFWorkbook workbook = open(request(null, List.of(), NAME, AMOUNT))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            Cell total = sheet.getRow(2).getCell(1);
            assertEquals(CellType.NUMERIC, total.getCellType());
            assertEquals(0, total.getNumericCellValue());
            assertEquals("0.00", new DataFormatter(Locale.ENGLISH).formatCellValue(total));
            assertEquals("Total", sheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals("A1:B1", sheet.getCTWorksheet().getAutoFilter().getRef());
        }
    }

    /** نفس قاعدة الـ PDF: القيمة اللي مش رقم في عمود متجمّع بتقول اسم العمود ورقم الصف. */
    @Test
    void valueThatIsNotANumberIsReportedWithTheColumnAndRow() {
        ReportException thrown = assertThrows(ReportException.class, () -> open(request(null, List.of(
                new Line("a", 1, 1), new Line("b", 2, 2), new Line("c", "n/a", 3)), NAME, AMOUNT)));
        assertTrue(thrown.getMessage().contains("'amount'") && thrown.getMessage().contains("row 3")
                && thrown.getMessage().contains("n/a"), thrown.getMessage());
    }

    /** صف الإجمالي والسطر اللي قبله داخلين في طاقة الورقة. */
    @Test
    void totalsRowCountsTowardsTheSheetsRowLimit() {
        // رأس + 1,048,574 صف = 1,048,575 بيكفّوا، ومعاهم سطر فاضي وصف إجمالي ما يكفوش.
        List<Line> rows = Collections.nCopies(1_048_574, new Line("x", 1, 1));
        ReportException thrown = assertThrows(ReportException.class,
                () -> ReportService.generate(ReportType.EXCEL, request(null, rows, NAME, AMOUNT).build()));
        assertTrue(thrown.getMessage().contains("1048577"), thrown.getMessage());
    }
}
