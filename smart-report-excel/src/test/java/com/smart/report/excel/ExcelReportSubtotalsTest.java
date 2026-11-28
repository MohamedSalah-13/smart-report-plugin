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
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** الإجمالي الفرعي تحت كل مجموعة في تقرير الـ Excel العام ({@link ReportRequest.Builder#groupBy}). */
class ExcelReportSubtotalsTest {

    public record Line(String customer, Object amount, Object qty, LocalDate date) {

        Line(String customer, Object amount, Object qty) {
            this(customer, amount, qty, null);
        }
    }

    /** أحمد صفين، سارة صف واحد (مجموعة من صف)، وعلي صفين. */
    private static final List<Line> LINES = List.of(
            new Line("Ahmed", new BigDecimal("1000.50"), 3),
            new Line("Ahmed", new BigDecimal("250.25"), 40),
            new Line("Sara", 0.1, null),
            new Line("Ali", 0.2, 5),
            new Line("Ali", new BigDecimal("10"), 1));

    private static final ColumnDefinition CUSTOMER = ColumnDefinition.of("customer", "Customer");
    private static final ColumnDefinition AMOUNT = ColumnDefinition.of("amount", "Amount").withFormat("#,##0.00").withTotal();
    private static final ColumnDefinition QTY = ColumnDefinition.of("qty", "Qty").withTotal();

    private static ReportRequest.Builder<Line> request(String title, List<Line> rows, ColumnDefinition... columns) {
        return ReportRequest.<Line>builder().title(title).data(rows).columns(List.of(columns));
    }

    private static XSSFWorkbook open(ReportRequest.Builder<Line> request) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(ReportService.generate(ReportType.EXCEL, request.build())));
    }

    private static String text(Cell cell) {
        return new DataFormatter(Locale.ENGLISH).formatCellValue(cell);
    }

    /**
     * تحت كل مجموعة صف فيه قيمتها في عمودها ومعادلة {@code SUBTOTAL(9, ...)} على صفوفها بس، ومعاها قيمتها؛
     * والإجمالي الكلي {@code SUBTOTAL} على كل الصفوف (الفرعية جوّاها) فما بيعدّش الإجماليات الفرعية مرتين.
     */
    @Test
    void subtotalsAreSubtotalFormulasWithTheirCachedValues() throws Exception {
        try (XSSFWorkbook workbook = open(request(null, LINES, CUSTOMER, AMOUNT, QTY).groupBy("customer"))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            // الرأس 0، أحمد 1-2 وإجماليه 3، سارة 4 وإجماليها 5، علي 6-7 وإجماليه 8، سطر فاضي 9، الإجمالي 10
            // عمود المجموعة هو العمود الوحيد اللي مش متجمّع: فيه القيمة، والصف من غير كلام.
            assertEquals("Ahmed", text(sheet.getRow(3).getCell(0)));
            assertEquals("Sara", text(sheet.getRow(5).getCell(0)));
            assertEquals("Ali", text(sheet.getRow(8).getCell(0)));
            assertNull(sheet.getRow(9), "سطر فاضي قبل الإجمالي الكلي");
            assertEquals(10, sheet.getLastRowNum());

            assertFormula(sheet.getRow(3).getCell(1), "SUBTOTAL(9,B2:B3)", 1250.75);
            assertFormula(sheet.getRow(3).getCell(2), "SUBTOTAL(9,C2:C3)", 43);
            assertFormula(sheet.getRow(5).getCell(1), "SUBTOTAL(9,B5:B5)", 0.1);
            assertFormula(sheet.getRow(5).getCell(2), "SUBTOTAL(9,C5:C5)", 0);
            assertFormula(sheet.getRow(8).getCell(1), "SUBTOTAL(9,B7:B8)", 0.2 + 10.0);

            Row total = sheet.getRow(10);
            assertEquals("Total", text(total.getCell(0)));
            assertFormula(total.getCell(1), "SUBTOTAL(9,B2:B9)", 1000.5 + 250.25 + 0.1 + 0.2 + 10.0);
            assertFormula(total.getCell(2), "SUBTOTAL(9,C2:C9)", 49);

            // بنمط عموده زي الإجمالي الكلي
            assertEquals("1,250.75", text(sheet.getRow(3).getCell(1)));
            assertEquals("1,261.05", text(total.getCell(1)));
        }
    }

    /** المعادلة، وقيمتها المحفوظة، وإن إعادة حسابها بتدّي نفس القيمة بالظبط. */
    private static void assertFormula(Cell cell, String formula, double value) {
        XSSFCell formulaCell = (XSSFCell) cell;
        assertEquals(CellType.FORMULA, formulaCell.getCellType());
        assertEquals(formula, formulaCell.getCellFormula());
        assertEquals(CellType.NUMERIC, formulaCell.getCachedFormulaResultType());
        assertEquals(value, formulaCell.getNumericCellValue(), 1e-9, formula);
        FormulaEvaluator evaluator = formulaCell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
        assertEquals(formulaCell.getNumericCellValue(), evaluator.evaluate(formulaCell).getNumberValue(), 0.0,
                formula + " بتحسب نفس الرقم المحفوظ");
    }

    /**
     * رأس الأعمدة ثابت، والفلتر بيوصل للسطر الفاضي بعد آخر إجمالي فرعي (الإجماليات الفرعية بين الصفوف): Excel
     * بيعتبر آخر صف في النطاق فيه {@code SUBTOTAL} صف إجمالي وما بيخبيهوش، فإجمالي آخر مجموعة كان بيفضل ظاهر
     * لو اخترت عميل تاني. (اتجرّب في Excel نفسه: الفلتر على عميل بيسيب صفوفه وإجماليه والإجمالي الكلي بمجموعه.)
     */
    @Test
    void headerStaysFrozenAndFilteredDownToTheLastSubtotal() throws Exception {
        try (XSSFWorkbook workbook = open(request("Statement", LINES, CUSTOMER, AMOUNT).groupBy("customer"))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            // العنوان 0، سطر 1، الرأس 2، البيانات والإجماليات الفرعية 3-10، سطر فاضي 11، الإجمالي 12
            assertEquals("A3:B12", sheet.getCTWorksheet().getAutoFilter().getRef());
            assertNull(sheet.getRow(11), "آخر صف في الفلتر فاضي");
            assertEquals(3, sheet.getPaneInformation().getHorizontalSplitPosition());
            assertEquals("Ali", text(sheet.getRow(10).getCell(0)));
            assertEquals("SUBTOTAL(9,B4:B11)", sheet.getRow(12).getCell(1).getCellFormula());
        }
    }

    /**
     * الإجمالي الفرعي بخط عريض وخط رفيع فوقه وتحته؛ الخطين تحت للإجمالي الكلي بس، فالرقم اللي القارئ بيدوّر
     * عليه باين.
     */
    @Test
    void subtotalsAreBoldWithASingleRule() throws Exception {
        try (XSSFWorkbook workbook = open(request(null, LINES, CUSTOMER, AMOUNT).groupBy("customer"))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            for (Cell cell : sheet.getRow(3)) {
                assertTrue(workbook.getFontAt(cell.getCellStyle().getFontIndex()).getBold());
                assertEquals(BorderStyle.THIN, cell.getCellStyle().getBorderTop());
                assertEquals(BorderStyle.THIN, cell.getCellStyle().getBorderBottom());
            }
            assertEquals(BorderStyle.DOUBLE, sheet.getRow(sheet.getLastRowNum()).getCell(1).getCellStyle().getBorderBottom(),
                    "الإجمالي الكلي");
        }
    }

    /** القيمة الفاضية مجموعة زي أي قيمة، وخليتها في صف الإجمالي فاضية زي صفوفها؛ صفين فاضيين ورا بعض مجموعة واحدة. */
    @Test
    void anEmptyGroupValueIsAGroupOfItsOwn() throws Exception {
        try (XSSFWorkbook workbook = open(request(null, List.of(
                        new Line("Ahmed", 10, 1), new Line(null, 5, 1), new Line(null, 6, 1), new Line("Sara", 7, 1)),
                CUSTOMER, AMOUNT).groupBy("customer"))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            // الرأس 0، أحمد 1 + 2، الفاضيين 3-4 + 5، سارة 6 + 7
            assertEquals(CellType.BLANK, sheet.getRow(5).getCell(0).getCellType(), "الفلتر على «(فارغ)» بيسيبه");
            assertFormula(sheet.getRow(5).getCell(1), "SUBTOTAL(9,B4:B5)", 11);
            assertEquals("Sara", text(sheet.getRow(7).getCell(0)));
        }
    }

    /**
     * التقرير العربي: الورقة من اليمين؛ قيمة المجموعة في عمودها بنوعها ونمطها (التاريخ تاريخ)؛ والخاصية اللي
     * مش عمود بتاخد الكلمة كاملة «مجموع أحمد» مكان كلمة الإجمالي.
     */
    @Test
    void arabicLabelsAndTheGroupValueAsShownInItsColumn() throws Exception {
        LocalDate day = LocalDate.of(2026, 1, 5);
        List<Line> rows = List.of(new Line("أحمد", 10, 1, day), new Line("أحمد", 5, 1, day),
                new Line("سارة", 7, 1, day.plusDays(1)));
        try (XSSFWorkbook workbook = open(request("كشف حساب", rows,
                ColumnDefinition.of("date", "التاريخ").withFormat("dd/MM/yyyy"),
                ColumnDefinition.of("amount", "المبلغ").withTotal()).groupBy("date"))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            assertTrue(sheet.isRightToLeft());
            // العنوان 0، سطر 1، الرأس 2، يوم 5 في 3-4 وإجماليه 5، يوم 6 في 6 وإجماليه 7
            Cell data = sheet.getRow(4).getCell(0);
            Cell subtotal = sheet.getRow(5).getCell(0);
            assertEquals(CellType.NUMERIC, subtotal.getCellType(), "تاريخ زي صفوفه، مش نص");
            assertEquals(data.getNumericCellValue(), subtotal.getNumericCellValue(), 0.0);
            assertEquals("05/01/2026", text(subtotal));
            assertEquals(data.getCellStyle().getDataFormatString(), subtotal.getCellStyle().getDataFormatString());
            assertTrue(workbook.getFontAt(subtotal.getCellStyle().getFontIndex()).getBold());
            assertEquals("06/01/2026", text(sheet.getRow(7).getCell(0)));
            assertEquals("الإجمالى", text(sheet.getRow(9).getCell(0)));
        }

        try (XSSFWorkbook workbook = open(request("كشف حساب", rows,
                ColumnDefinition.of("amount", "المبلغ").withTotal(), ColumnDefinition.of("qty", "العدد"))
                .groupBy("customer").subtotalLabel("مجموع "))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            assertEquals("مجموع أحمد", text(sheet.getRow(5).getCell(1)), "الكلمة تحت أول عمود مش متجمّع");
            assertFormula(sheet.getRow(5).getCell(0), "SUBTOTAL(9,A4:A5)", 15);
        }
    }

    /**
     * الفلتر على قيمة من عمود المجموعة (عميل واحد) بيسيب إجماليه ظاهر: خلية المجموعة في صف الإجمالي فيها نفس
     * قيمة صفوفه بنفس نوعها. الكلام («Total»، «إجمالى») تحت أول عمود تاني مش متجمّع، حتى لو قبل عمود المجموعة.
     */
    @Test
    void filteringOnTheGroupColumnKeepsTheSubtotal() throws Exception {
        ColumnDefinition qtyText = ColumnDefinition.of("qty", "Qty");
        try (XSSFWorkbook workbook = open(request(null, LINES, CUSTOMER, qtyText, AMOUNT).groupBy("customer"))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            for (int[] group : new int[][] {{1, 3}, {4, 5}, {6, 8}}) {
                Cell data = sheet.getRow(group[0]).getCell(0);
                Cell subtotal = sheet.getRow(group[1]).getCell(0);
                assertEquals(data.getCellType(), subtotal.getCellType());
                assertEquals(text(data), text(subtotal), "نفس القيمة اللي الفلتر بيختارها");
                assertEquals("Total", text(sheet.getRow(group[1]).getCell(1)), "الكلام في العمود التاني");
            }
            assertEquals("SUBTOTAL(9,C2:C3)", sheet.getRow(3).getCell(2).getCellFormula());
        }

        // عمود المجموعة تاني: الكلام تحت الأول، والقيمة تحت عمودها.
        try (XSSFWorkbook workbook = open(request("كشف", LINES, qtyText, CUSTOMER, AMOUNT).groupBy("customer"))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            assertEquals("إجمالى", text(sheet.getRow(5).getCell(0)));
            assertEquals("Ahmed", text(sheet.getRow(5).getCell(1)));
        }

        // كلام البرنامج من غير «:» في الآخر.
        try (XSSFWorkbook workbook = open(request(null, LINES, CUSTOMER, qtyText, AMOUNT).groupBy("customer")
                .subtotalLabel("Customer total: "))) {
            assertEquals("Customer total", text(workbook.getSheetAt(0).getRow(3).getCell(1)));
        }
    }

    /** من غير {@code groupBy} الإجمالي {@code SUM} زي الأول، ومفيش إجماليات فرعية. */
    @Test
    void withoutGroupsTheTotalIsStillASum() throws Exception {
        try (XSSFWorkbook workbook = open(request(null, LINES, CUSTOMER, AMOUNT))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            assertEquals(7, sheet.getLastRowNum());
            assertEquals("SUM(B2:B6)", sheet.getRow(7).getCell(1).getCellFormula());
            for (int r = 1; r <= 5; r++) {
                assertFalse(text(sheet.getRow(r).getCell(0)).startsWith("Total"));
            }
        }
    }

    /** من غير بيانات مفيش مجموعات: الإجمالي صفر من غير معادلة، زي التقرير من غير مجموعات. */
    @Test
    void emptyReportHasNoSubtotals() throws Exception {
        try (XSSFWorkbook workbook = open(request(null, List.of(), CUSTOMER, AMOUNT).groupBy("customer"))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            assertEquals(2, sheet.getLastRowNum());
            Cell total = sheet.getRow(2).getCell(1);
            assertEquals(CellType.NUMERIC, total.getCellType());
            assertEquals(0, total.getNumericCellValue(), 0.0);
        }
    }

    @Test
    void groupsWithoutATotalledColumnFail() {
        ReportException thrown = assertThrows(ReportException.class, () -> open(request(null, LINES,
                CUSTOMER, ColumnDefinition.of("amount", "Amount")).groupBy("customer")));
        assertTrue(thrown.getMessage().contains("withTotal()"), thrown.getMessage());
    }
}
