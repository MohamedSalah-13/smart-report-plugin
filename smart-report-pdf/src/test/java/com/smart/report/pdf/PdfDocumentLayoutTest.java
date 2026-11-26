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

    /**
     * خلية نصها أطول من صفحة كاملة: الصف بيتقسّم على الصفحات. كان بيترسم كله من مكانه، فباقيه ينزل
     * تحت حافة الورقة ويضيع في الطباعة من غير أي تحذير.
     */
    @Test
    void rowTallerThanThePageIsSplitAcrossPagesAndNothingLeavesThePaper() throws Exception {
        StringBuilder note = new StringBuilder();
        for (int word = 1; word <= 400; word++) {
            note.append('w').append(word).append(' ');
        }
        Glyphs glyphs = Glyphs.of(PdfDocument.ltr(Paper.A5).pageNumbers(PageNumbers.NONE)
                .table(t -> t.column("Note", 1).column("Amount", 3)
                        .row("before", "4").row(note.toString().trim(), "5").row("after", "6"))
                .toBytes());

        float lastLine = Paper.A5.height() - PdfStyle.forPaper(Paper.A5).margin();
        assertTrue(glyphs.lowest() <= lastLine, "حرف مرسوم تحت الهامش السفلي: " + glyphs.lowest());

        int pages = glyphs.pages();
        assertTrue(pages >= 3, "400 سطر على A5 أكتر من صفحتين: " + pages);
        assertTrue(glyphs.drawn("w1", 1), "الصف بيبدأ في الصفحة اللي وصلها، مش في صفحة جديدة");
        assertTrue(glyphs.drawn("w400", pages));
        assertTrue(glyphs.drawn("after", pages), "الصف التالي بعد آخر جزء");
        assertTrue(glyphs.first("w400", pages).y() < glyphs.first("after", pages).y());
        for (int page = 1; page <= pages; page++) {
            assertTrue(glyphs.drawn("Note", page), "رأس الجدول ناقص في صفحة " + page);
        }
    }

    /** نفس التقسيم في صفحة عربية ولصف إجمالي: كل جزء في عموده، وأول عمود على اليمين في كل صفحة. */
    @Test
    void splitTotalsRowKeepsItsColumnsInARightToLeftPage() throws Exception {
        StringBuilder note = new StringBuilder();
        for (int word = 1; word <= 400; word++) {
            note.append('w').append(word).append(' ');
        }
        Glyphs glyphs = Glyphs.of(PdfDocument.rtl(Paper.A5).pageNumbers(PageNumbers.NONE)
                .table(t -> t.column("Note", 1).column("Amount", 3)
                        .row("before", "AAA").totals(note.toString().trim(), "ZZZ"))
                .toBytes());

        float lastLine = Paper.A5.height() - PdfStyle.forPaper(Paper.A5).margin();
        assertTrue(glyphs.lowest() <= lastLine, "حرف مرسوم تحت الهامش السفلي: " + glyphs.lowest());

        int pages = glyphs.pages();
        assertTrue(pages >= 3, "400 سطر على A5 أكتر من صفحتين: " + pages);
        assertTrue(glyphs.drawn("w1", 1));
        assertTrue(glyphs.drawn("ZZZ", 1), "الخلية القصيرة بتترسم مرة واحدة، مع أول جزء");
        assertTrue(glyphs.drawn("w400", pages));
        for (int page = 2; page <= pages; page++) {
            assertFalse(glyphs.drawn("ZZZ", page), "الخلية القصيرة اتكررت في صفحة " + page);
        }
        float amountColumn = glyphs.first("ZZZ", 1).x();
        assertTrue(glyphs.first("w1", 1).x() > amountColumn, "أول عمود على اليمين");
        assertTrue(glyphs.first("w400", pages).x() > amountColumn, "وباقي الصف في نفس العمود");
    }

    /**
     * رأس جدول أطول من الصفحة ما بيسيبش مكان لأي سطر: المستند لازم يتبني برضه وكل صفوفه تترسم.
     * تقسيم الصف كان بيلف للأبد على باقٍ فاضي ويفتح صفحات لحد ما الذاكرة تخلص.
     */
    @Test
    void headerThatLeavesNoRoomForARowStillLetsTheDocumentFinish() {
        StringBuilder header = new StringBuilder();
        for (int word = 1; word <= 50; word++) {
            header.append('h').append(word).append('\n');
        }
        byte[] pdf = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(20),
                () -> PdfDocument.ltr(Paper.A5)
                        .table(t -> t.column(header.toString().trim(), 1).column("Amount", 1)
                                .row("first", "1").row("second", "2").totals("Total", "3"))
                        .toBytes());

        Glyphs glyphs = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> Glyphs.of(pdf));
        assertTrue(glyphs.pages() <= 3, "صفحة لكل صف على الأكثر: " + glyphs.pages());
        glyphs.first("first");
        glyphs.first("second");
        glyphs.first("Total");
    }

    /** رأس أطول من تلت الصفحة بيترسم مرة واحدة: لو اتكرر كان هياكل كل صفحة. */
    @Test
    void tallHeaderIsDrawnOnceInsteadOfOnEveryPage() throws Exception {
        StringBuilder header = new StringBuilder();
        for (int line = 1; line <= 15; line++) {
            header.append('h').append(line).append('\n');
        }
        PdfTable table = PdfTable.create().column(header.toString().trim(), 1).column("Amount", 1);
        for (int day = 1; day <= 120; day++) {
            table.row("row" + day, "5");
        }
        Glyphs glyphs = Glyphs.of(PdfDocument.ltr(Paper.A5).table(table).toBytes());

        assertTrue(glyphs.drawn("h15", 1));
        for (int page = 2; page <= glyphs.pages(); page++) {
            assertFalse(glyphs.drawn("h1", page), "الرأس الطويل اتكرر في صفحة " + page);
        }
        assertTrue(glyphs.drawn("row120", glyphs.pages()));
    }

    /** رأس أطول من الصفحة كلها بيتقسّم زي الصف، ومفيش سطر منه بيخرج عن الورقة. */
    @Test
    void headerTallerThanThePageIsSplitAndNothingLeavesThePaper() throws Exception {
        StringBuilder header = new StringBuilder();
        for (int line = 1; line <= 60; line++) {
            header.append('h').append(line).append('\n');
        }
        Glyphs glyphs = Glyphs.of(PdfDocument.ltr(Paper.A5).pageNumbers(PageNumbers.NONE)
                .table(t -> t.column(header.toString().trim(), 1).column("Amount", 1).row("first", "1"))
                .toBytes());

        assertTrue(glyphs.lowest() <= Paper.A5.height() - PdfStyle.forPaper(Paper.A5).margin(), "" + glyphs.lowest());
        assertTrue(glyphs.drawn("h1", 1));
        assertTrue(glyphs.pages() >= 2);
        glyphs.first("h60");
        glyphs.first("first");
    }

    /** بيان قيمته أطول من صفحة بيكمل سطرًا سطرًا: كان بيترسم كله من مكانه ويخرج عن الورقة. */
    @Test
    void fieldLongerThanThePageContinuesOnTheNextPage() throws Exception {
        StringBuilder value = new StringBuilder();
        for (int word = 1; word <= 600; word++) {
            value.append('w').append(word).append(' ');
        }
        Glyphs glyphs = Glyphs.of(PdfDocument.ltr(Paper.A5).pageNumbers(PageNumbers.NONE)
                .field("Notes", value.toString().trim()).field("Plate", "1234")
                .text("after")
                .toBytes());

        assertTrue(glyphs.lowest() <= Paper.A5.height() - PdfStyle.forPaper(Paper.A5).margin(), "" + glyphs.lowest());
        assertTrue(glyphs.pages() >= 2);
        assertTrue(glyphs.drawn("Plate:", 1), "البيان التاني جنب الأول");
        assertTrue(glyphs.drawn("w600", glyphs.pages()));
        assertTrue(glyphs.first("w600").y() < glyphs.first("after").y(), "اللي بعده تحته");
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
