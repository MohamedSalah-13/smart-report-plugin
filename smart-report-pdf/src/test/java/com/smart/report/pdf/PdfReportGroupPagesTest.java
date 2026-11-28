package com.smart.report.pdf;

import com.smart.report.ColumnDefinition;
import com.smart.report.ReportException;
import com.smart.report.ReportRequest;
import com.smart.report.ReportService;
import com.smart.report.ReportType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** عنوان لكل مجموعة، وصفحة لكل مجموعة، في تقرير الـ PDF العام. */
class PdfReportGroupPagesTest {

    public record Line(String customer, String item, BigDecimal amount) {}

    private static final List<Line> LINES = List.of(
            new Line("Ahmed", "Bolt", new BigDecimal("10")),
            new Line("Ahmed", "Nut", new BigDecimal("5")),
            new Line("Mona", "Screw", new BigDecimal("7")),
            new Line("Omar", "Washer", new BigDecimal("3")),
            new Line("Omar", "Pin", new BigDecimal("2")));

    private static ReportRequest.Builder<Line> request() {
        return ReportRequest.<Line>builder().title("Statement").data(LINES)
                .addColumn(ColumnDefinition.of("item", "Item"))
                .addColumn(ColumnDefinition.of("amount", "Amount").withFormat("#,##0.00").withTotal())
                .groupBy("customer");
    }

    private static List<String> pageTexts(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            java.util.ArrayList<String> pages = new java.util.ArrayList<>();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pages.add(stripper.getText(document).replace(String.valueOf((char) 13), ""));
            }
            return pages;
        }
    }

    private static int count(String text, String part) {
        int found = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) {
            found++;
        }
        return found;
    }

    @Test
    void aHeadingAboveEveryGroup() throws Exception {
        byte[] pdf = ReportService.generate(ReportType.PDF, request().groupHeader(true).build());
        List<String> pages = pageTexts(pdf);
        assertEquals(1, pages.size());
        String text = pages.get(0);
        // العنوان قبل صفوف مجموعته، وبعد إجمالي المجموعة اللي قبلها.
        assertTrue(text.indexOf("Ahmed") < text.indexOf("Bolt"), text);
        assertTrue(text.indexOf("Total: Ahmed") < text.indexOf("Mona\n"), text);
        assertTrue(text.indexOf("Mona\n") < text.indexOf("Screw"), text);
        assertTrue(text.indexOf("Omar\n") < text.indexOf("Washer"), text);
        assertEquals(3, count(text, "Item"), "رأس الأعمدة تحت كل عنوان");
        assertEquals(1, count(text, "27.00"), "الإجمالي الكلي مرة في الآخر");
        assertTrue(text.lastIndexOf("27.00") > text.indexOf("Total: Omar"));

        Glyphs glyphs = Glyphs.of(pdf);
        assertTrue(glyphs.first("Mona").y() < glyphs.first("Screw").y(), "العنوان فوق الصف");
    }

    @Test
    void eachGroupOnANewPage() throws Exception {
        byte[] pdf = ReportService.generate(ReportType.PDF, request().pageBreakPerGroup(true).build());
        List<String> pages = pageTexts(pdf);
        assertEquals(3, pages.size());
        assertTrue(pages.get(0).contains("Bolt") && pages.get(0).contains("Total: Ahmed"), pages.get(0));
        assertTrue(!pages.get(0).contains("Screw"));
        assertTrue(pages.get(1).contains("Screw") && pages.get(1).contains("Total: Mona"), pages.get(1));
        assertTrue(pages.get(2).contains("Washer") && pages.get(2).contains("27.00"), "الإجمالي الكلي في آخر صفحة");
        for (String page : pages) {
            assertEquals(1, count(page, "Item"), "كل صفحة برأس أعمدتها");
        }
    }

    @Test
    void bothTogether() throws Exception {
        List<String> pages = pageTexts(ReportService.generate(ReportType.PDF,
                request().groupHeader(true).pageBreakPerGroup(true).build()));
        assertEquals(3, pages.size());
        assertTrue(pages.get(1).indexOf("Mona") < pages.get(1).indexOf("Screw"));
    }

    @Test
    void withoutTheOptionsNothingChanges() throws Exception {
        List<String> pages = pageTexts(ReportService.generate(ReportType.PDF, request().build()));
        assertEquals(1, pages.size());
        assertEquals(1, count(pages.get(0), "Item"));
    }

    @Test
    void theOptionsNeedGroupBy() {
        assertThrows(ReportException.class, () -> ReportService.generate(ReportType.PDF,
                request().groupBy(null).groupHeader(true).build()));
        assertThrows(ReportException.class, () -> ReportService.generate(ReportType.EXCEL,
                request().groupBy(null).pageBreakPerGroup(true).build()));
    }
}
