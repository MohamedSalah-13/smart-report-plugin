package com.smart.report.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** مكان كل حاجة على الورقة: الاتجاه، المقاس، الالتفاف، تكرار الرأس، وترقيم الصفحات. */
class PdfDocumentLayoutTest {

    private static PdfTable twoColumns() {
        return PdfTable.create().column("Alpha", 1).column("Beta", 1)
                .row("A1", "B1")
                .totals("T1", "T2");
    }

    @Test
    void rightToLeftTablePutsItsFirstColumnOnTheRight() throws Exception {
        Glyphs glyphs = Glyphs.of(PdfDocument.rtl(Paper.A4).table(twoColumns()).toBytes());

        assertTrue(glyphs.first("Alpha").x() > glyphs.first("Beta").x(), "أول عمود لازم يبقى على اليمين");
        assertTrue(glyphs.first("A1").x() > glyphs.first("B1").x(), "خلايا الصف بنفس ترتيب الرأس");
    }

    @Test
    void leftToRightTablePutsItsFirstColumnOnTheLeft() throws Exception {
        Glyphs glyphs = Glyphs.of(PdfDocument.ltr(Paper.A4).table(twoColumns()).toBytes());

        assertTrue(glyphs.first("Alpha").x() < glyphs.first("Beta").x());
        assertTrue(glyphs.first("A1").x() < glyphs.first("B1").x());
    }

    /** صف الإجمالي كان بيترص عكس الجدول في مكتبات تانية: عنوانه تحت آخر عمود وكل رقم تحت عمود غيره. */
    @Test
    void totalsRowSitsUnderItsOwnColumnsInBothDirections() throws Exception {
        Glyphs rtl = Glyphs.of(PdfDocument.rtl(Paper.A4).table(twoColumns()).toBytes());
        assertTrue(rtl.first("T1").x() > rtl.first("T2").x());
        assertTrue(rtl.first("T1").x() > rtl.first("Beta").x(), "إجمالي العمود الأول تحت العمود الأول");

        Glyphs ltr = Glyphs.of(PdfDocument.ltr(Paper.A4).table(twoColumns()).toBytes());
        assertTrue(ltr.first("T1").x() < ltr.first("T2").x());
    }

    @Test
    void pagesAreDrawnOnThePaperAskedFor() throws Exception {
        assertPage(Paper.A4, 595.28f, 841.89f);
        assertPage(Paper.A5, 419.53f, 595.28f);
        assertPage(Paper.A5.landscape(), 595.28f, 419.53f);
        assertPage(Paper.A4.landscape(), 841.89f, 595.28f);
    }

    private static void assertPage(Paper paper, float width, float height) throws Exception {
        try (PDDocument document = Loader.loadPDF(PdfDocument.rtl(paper).title("Report").toBytes())) {
            PDRectangle box = document.getPage(0).getMediaBox();
            assertEquals(width, box.getWidth(), 0.05f);
            assertEquals(height, box.getHeight(), 0.05f);
        }
    }

    /**
     * النص الطويل بيلتف، وأوله في السطر الأول. العيبان اللي الاختبار بيمنعهم: التقطيع بـ "..." (كان
     * بيضيّع أول الجملة العربية ويسيب آخرها)، وكسر السطر بعد التشكيل (بيحط آخر الجملة فوق أولها).
     */
    @Test
    void longArabicCellWrapsWithItsBeginningOnTheFirstLine() throws Exception {
        String longText = "111 توريد زلط لموقع العاصمة الادارية الجديدة المرحلة الثانية والثالثة والرابعة 999";
        byte[] pdf = PdfDocument.rtl(Paper.A5)
                .table(t -> t.column("البيان", 1).column("القيمة", 1).column("التاريخ", 1).row(longText, "5", "7"))
                .toBytes();

        Glyphs glyphs = Glyphs.of(pdf);
        assertTrue(glyphs.first("111").y() < glyphs.first("999").y(), "أول الجملة فوق آخرها");
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertFalse(new PDFTextStripper().getText(document).contains("..."), "مفيش تقطيع");
        }
    }

    @Test
    void wordLongerThanItsColumnIsBrokenAndNothingIsLost() throws Exception {
        String endless = "A".repeat(150) + "Z";
        byte[] pdf = PdfDocument.ltr(Paper.A5)
                .table(t -> t.column("Name", 1).column("Other", 1).row(endless, "x"))
                .toBytes();

        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document).replaceAll("\\s", "");
            assertTrue(text.contains(endless), "كل حروف الكلمة لازم تتطبع");
        }
    }

    @Test
    void longTableRunsOverSeveralPagesWithItsHeaderAndPageNumbersOnEach() throws Exception {
        PdfTable table = PdfTable.create().column("Day", 1).column("Amount", 1);
        for (int day = 1; day <= 120; day++) {
            table.row("row" + day, "5");
        }
        table.totals("Total", "600");
        Glyphs glyphs = Glyphs.of(PdfDocument.ltr(Paper.A5).table(table).toBytes());

        int pages = glyphs.pages();
        assertTrue(pages >= 3, "120 صف على A5 أكتر من صفحتين: " + pages);
        for (int page = 1; page <= pages; page++) {
            assertTrue(glyphs.drawn("Day", page), "رأس الجدول ناقص في صفحة " + page);
            assertTrue(glyphs.drawn("Page " + page + " of " + pages, page), "رقم الصفحة ناقص في " + page);
        }
        assertTrue(glyphs.drawn("row1", 1));
        assertTrue(glyphs.drawn("row120", pages));
        assertTrue(glyphs.drawn("Total", pages), "الإجمالي في آخر صفحة");
    }

    @Test
    void singlePageIsNotNumberedUnlessAsked() throws Exception {
        assertFalse(Glyphs.of(PdfDocument.ltr(Paper.A4).title("One").toBytes()).drawn("Page 1 of 1", 1));
        assertTrue(Glyphs.of(PdfDocument.ltr(Paper.A4).title("One").pageNumbers(PageNumbers.ALWAYS).toBytes())
                .drawn("Page 1 of 1", 1));
    }

    /** الصفحة العربية بتترقّم بالعربي: الكلمة عربية والأرقام بترتيبها. */
    @Test
    void rightToLeftPagesAreNumberedInArabic() throws Exception {
        byte[] pdf = PdfDocument.rtl(Paper.A4).title("تقرير").pageNumbers(PageNumbers.ALWAYS).toBytes();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("1") && !text.contains("Page"), text);
        }
    }

    @Test
    void wholeStatementIsBuiltFromItsParts() throws Exception {
        byte[] pdf = PdfDocument.ltr(Paper.A5)
                .letterhead("Safa Haulage", "Cairo - 0100 000 0000")
                .title("Truck statement").subtitle("November")
                .field("Plate", "1234").field("Driver", "Ahmed")
                .heading("Movements")
                .table(twoColumns())
                .text("A closing note")
                .summary("Due", "7200").summaryTotal("Remaining", "3950")
                .footer("Printed by Naqlat")
                .toBytes();

        Glyphs glyphs = Glyphs.of(pdf);
        for (String part : new String[]{"Safa", "0100", "Truck", "November", "Plate:", "Driver:", "Movements",
                "Alpha", "closing", "Due", "Remaining", "3950", "Printed"}) {
            assertTrue(glyphs.drawn(part, 1), part + " مش مرسوم");
        }
        // الترويسة فوق العنوان، والعنوان فوق الجدول، والملخص تحته.
        assertTrue(glyphs.first("Safa").y() < glyphs.first("Truck").y());
        assertTrue(glyphs.first("Truck").y() < glyphs.first("Alpha").y());
        assertTrue(glyphs.first("Alpha").y() < glyphs.first("Remaining").y());
        // البيانان في سطر واحد: الأول عند بداية السطر.
        assertEquals(glyphs.first("Plate:").y(), glyphs.first("Driver:").y(), 0.5f);
        assertTrue(glyphs.first("Plate:").x() < glyphs.first("Driver:").x());
    }
}
