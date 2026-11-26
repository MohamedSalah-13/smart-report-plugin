package com.smart.report.pdf;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** اللي بيتكرر في كل صفحة (الترويسة، تذييل الصفحات) وخانات التوقيع. */
class PageFurnitureTest {

    private static PdfTable longTable() {
        PdfTable table = PdfTable.create().column("Day", 1).column("Amount", 1);
        for (int day = 1; day <= 120; day++) {
            table.row("row" + day, "5");
        }
        return table;
    }

    private static PdfDocument statement() {
        return PdfDocument.ltr(Paper.A5).letterhead("Safa Haulage", "Cairo 0100 000 0000").title("Statement");
    }

    /** الكشف الطويل: الترويسة في أعلى كل صفحة، ورأس الجدول تحتها. */
    @Test
    void letterheadIsRepeatedOnEveryPageAboveTheTableHeader() throws Exception {
        Glyphs glyphs = Glyphs.of(statement().letterheadOnEveryPage(true).table(longTable()).toBytes());

        int pages = glyphs.pages();
        assertTrue(pages >= 3, "120 صف على A5 أكتر من صفحتين: " + pages);
        for (int page = 1; page <= pages; page++) {
            assertTrue(glyphs.drawn("Safa", page), "الترويسة ناقصة في صفحة " + page);
            assertTrue(glyphs.first("Safa", page).y() < glyphs.first("Day", page).y(),
                    "رأس الجدول تحت الترويسة في صفحة " + page);
        }
        assertTrue(glyphs.drawn("row120", pages));
        assertFalse(glyphs.drawn("Statement", 2), "العنوان مرة واحدة: الترويسة بس هي اللي بتتكرر");
    }

    @Test
    void letterheadIsOnTheFirstPageOnlyUnlessAsked() throws Exception {
        Glyphs glyphs = Glyphs.of(statement().table(longTable()).toBytes());

        assertTrue(glyphs.drawn("Safa", 1));
        for (int page = 2; page <= glyphs.pages(); page++) {
            assertFalse(glyphs.drawn("Safa", page), "الترويسة اتكررت في صفحة " + page);
        }
    }

    /** الترويسة المتكررة ما بتخليش الصف الأطول من صفحة يفتح صفحات من غير ما يرسم. */
    @Test
    void repeatedLetterheadLeavesRoomForAnOversizedRow() throws Exception {
        StringBuilder note = new StringBuilder();
        for (int word = 1; word <= 400; word++) {
            note.append('w').append(word).append(' ');
        }
        Glyphs glyphs = Glyphs.of(statement().letterheadOnEveryPage(true).pageNumbers(PageNumbers.NONE)
                .table(t -> t.column("Note", 1).column("Amount", 3).row(note.toString().trim(), "5"))
                .toBytes());

        assertTrue(glyphs.lowest() <= Paper.A5.height() - PdfStyle.forPaper(Paper.A5).margin(), "" + glyphs.lowest());
        assertTrue(glyphs.drawn("w400", glyphs.pages()));
        for (int page = 1; page <= glyphs.pages(); page++) {
            assertTrue(glyphs.drawn("Safa", page) && glyphs.drawn("Note", page), "صفحة " + page);
        }
    }

    /** تذييل الصفحات تحت آخر سطر من المحتوى وفوق رقم الصفحة، في كل صفحة. */
    @Test
    void pageFooterSitsBetweenTheContentAndThePageNumberOnEveryPage() throws Exception {
        Glyphs glyphs = Glyphs.of(statement().pageFooter("Printed by Naqlat").table(longTable()).toBytes());

        int pages = glyphs.pages();
        for (int page = 1; page <= pages; page++) {
            List<Float> lines = glyphs.lineYs(page);
            float number = glyphs.first("Page " + page + " of " + pages, page).y();
            float footer = glyphs.first("Printed", page).y();
            assertEquals(number, lines.get(lines.size() - 1), 0.5f, "رقم الصفحة آخر سطر في صفحة " + page);
            assertEquals(footer, lines.get(lines.size() - 2), 0.5f, "التذييل قبله في صفحة " + page);
            assertTrue(lines.get(lines.size() - 3) < footer - 3, "المحتوى فوق التذييل في صفحة " + page);
        }
    }

    @Test
    void pageFooterIsAlsoDrawnOnASinglePageWithNoNumber() throws Exception {
        Glyphs glyphs = Glyphs.of(statement().pageFooter("Printed by Naqlat").toBytes());

        assertEquals(1, glyphs.pages());
        assertTrue(glyphs.drawn("Printed", 1));
        assertFalse(glyphs.drawn("Page 1 of 1", 1));
    }

    /** الخانة الأولى عند بداية السطر: يمين في العربي، شمال في الإنجليزي، والكل على سطر واحد. */
    @Test
    void signatureBoxesFollowTheReadingOrderOnOneLine() throws Exception {
        Glyphs rtl = Glyphs.of(PdfDocument.rtl(Paper.A4).title("Receipt").signatures("AAA", "BBB", "CCC").toBytes());
        assertTrue(rtl.first("AAA").x() > rtl.first("BBB").x());
        assertTrue(rtl.first("BBB").x() > rtl.first("CCC").x());
        assertEquals(rtl.first("AAA").y(), rtl.first("CCC").y(), 0.5f);

        Glyphs ltr = Glyphs.of(PdfDocument.ltr(Paper.A4).title("Receipt").signatures("AAA", "BBB", "CCC").toBytes());
        assertTrue(ltr.first("AAA").x() < ltr.first("BBB").x());
        assertTrue(ltr.first("BBB").x() < ltr.first("CCC").x());
        assertTrue(ltr.first("Receipt").y() < ltr.first("AAA").y(), "الخانات بعد اللي قبلها");
    }

    /** الخانات ما بتتقسمش: أيًا كان مكانها في آخر الصفحة، التلاتة في نفس الصفحة. */
    @Test
    void signatureBoxesStayTogetherAtThePageEnd() throws Exception {
        for (int rows = 30; rows <= 44; rows++) {
            PdfTable table = PdfTable.create().column("Day", 1);
            for (int i = 1; i <= rows; i++) {
                table.row("row" + i);
            }
            Glyphs glyphs = Glyphs.of(PdfDocument.ltr(Paper.A5).table(table).signatures("AAA", "BBB", "CCC")
                    .pageNumbers(PageNumbers.NONE).toBytes());
            int page = glyphs.pages();
            assertTrue(glyphs.drawn("AAA", page) && glyphs.drawn("CCC", page), rows + " صف");
            assertTrue(glyphs.lowest() <= Paper.A5.height() - PdfStyle.forPaper(Paper.A5).margin(), rows + " صف");
        }
    }

    @Test
    void signaturesNeedALabel() {
        assertThrows(IllegalArgumentException.class, () -> PdfDocument.ltr(Paper.A4).signatures());
    }
}
