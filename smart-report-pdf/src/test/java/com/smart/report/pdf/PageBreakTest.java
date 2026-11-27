package com.smart.report.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** فاصل الصفحات: الجزء اللي بعده في صفحة جديدة، من غير صفحات فاضية، ومن غير ما يعمل حاجة على اللفة. */
class PageBreakTest {

    private static PdfDocument document(boolean rightToLeft, Paper paper) {
        return rightToLeft ? PdfDocument.rtl(paper) : PdfDocument.ltr(paper);
    }

    private static PdfTable longTable() {
        PdfTable table = PdfTable.create().column("Day", 1).column("Amount", 1);
        for (int day = 1; day <= 120; day++) {
            table.row("row" + day, "5");
        }
        return table.totals("Total", "600");
    }

    private static int pages(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        }
    }

    /** الجزء اللي بعد الفاصل أول سطر في صفحة جديدة، عند بداية السطر، والترقيم بيكمل. */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void theNextPartStartsAtTheTopOfANewPage(boolean rightToLeft) throws Exception {
        Glyphs glyphs = Glyphs.of(document(rightToLeft, Paper.A5).title("Statement").text("first part")
                .pageBreak().heading("Annex").text("second part").toBytes());

        assertEquals(2, glyphs.pages());
        assertTrue(glyphs.drawn("first", 1));
        assertFalse(glyphs.drawn("Annex", 1));
        assertEquals(glyphs.lineYs(2).getFirst(), glyphs.first("Annex", 2).y(), 0.01f, "أول سطر في الصفحة");
        // بنفس مكانه لو كان أول جزء في المستند.
        Glyphs alone = Glyphs.of(document(rightToLeft, Paper.A5).heading("Annex").toBytes());
        assertEquals(alone.first("Annex").y(), glyphs.first("Annex", 2).y(), 0.01f);
        assertEquals(alone.first("Annex").x(), glyphs.first("Annex", 2).x(), 0.01f, "عند بداية السطر");

        assertArrayEquals(new int[] {1, 2}, glyphs.pageNumber(1, rightToLeft));
        assertArrayEquals(new int[] {2, 2}, glyphs.pageNumber(2, rightToLeft));
    }

    /**
     * الفاصل على صفحة لسه مفيهاش حاجة تحت الترويسة ما بيعملش حاجة: في أول المستند (حتى بعد مسافة)، وفاصلين
     * ورا بعض فاصل واحد.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aBreakOnAnEmptyPageDoesNothing(boolean rightToLeft) throws Exception {
        byte[] pdf = document(rightToLeft, Paper.A5).letterhead("Safa Haulage").space(30).pageBreak()
                .title("One").pageBreak().pageBreak().text("Two").toBytes();
        Glyphs glyphs = Glyphs.of(pdf);

        assertEquals(2, glyphs.pages());
        assertTrue(glyphs.drawn("Safa", 1) && glyphs.drawn("One", 1));
        assertTrue(glyphs.drawn("Two", 2));
        float unbroken = Glyphs.of(document(rightToLeft, Paper.A5).letterhead("Safa Haulage").space(30)
                .title("One").toBytes()).first("One").y();
        assertEquals(unbroken, glyphs.first("One").y(), 0.01f, "الفاصل الأولاني ما حرّكش حاجة");
    }

    /** مستند بيتبني في حلقة بفاصل بعد كل قسم: الفاصل الأخير ما بيفتحش صفحة للتذييل لوحده. */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void trailingBreaksLeaveNoEmptyPage(boolean rightToLeft) throws Exception {
        PdfDocument document = document(rightToLeft, Paper.A5).footer("Thank you");
        for (int section = 1; section <= 3; section++) {
            document.heading("Section" + section).text("body" + section).pageBreak();
        }
        document.space(12).pageBreak();
        Glyphs glyphs = Glyphs.of(document.toBytes());

        assertEquals(3, glyphs.pages());
        for (int section = 1; section <= 3; section++) {
            assertTrue(glyphs.drawn("Section" + section, section), "قسم " + section + " في صفحته");
            assertArrayEquals(new int[] {section, 3}, glyphs.pageNumber(section, rightToLeft));
        }
        assertTrue(glyphs.drawn("Thank", 3), "التذييل تحت آخر قسم");
        assertTrue(glyphs.first("body3", 3).y() < glyphs.first("Thank", 3).y());
    }

    /** الصفحة الجديدة زي أي صفحة: الترويسة في أعلاها لو المستند طالبها في كل صفحة، والجزء تحتها. */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void theNewPageFollowsTheLetterheadRule(boolean rightToLeft) throws Exception {
        Glyphs every = Glyphs.of(document(rightToLeft, Paper.A5).letterhead("Safa Haulage", "Cairo")
                .letterheadOnEveryPage(true).title("Statement").text("first").pageBreak().heading("Annex").toBytes());
        Glyphs firstUnder = Glyphs.of(document(rightToLeft, Paper.A5).letterhead("Safa Haulage", "Cairo")
                .letterheadOnEveryPage(true).heading("Annex").toBytes());

        assertTrue(every.drawn("Safa", 2), "الترويسة في الصفحة الجديدة");
        assertTrue(every.first("Safa", 2).y() < every.first("Annex", 2).y());
        assertEquals(firstUnder.first("Annex").y(), every.first("Annex", 2).y(), 0.01f,
                "تحت الترويسة زي أول جزء في الصفحة الأولى");

        Glyphs once = Glyphs.of(document(rightToLeft, Paper.A5).letterhead("Safa Haulage")
                .title("Statement").text("first").pageBreak().heading("Annex").toBytes());
        assertFalse(once.drawn("Safa", 2), "الترويسة في الصفحة الأولى بس لو ما اتطلبتش في كل صفحة");
        assertEquals(Glyphs.of(document(rightToLeft, Paper.A5).heading("Annex").toBytes()).first("Annex").y(),
                once.first("Annex", 2).y(), 0.01f, "من أعلى الصفحة");
    }

    /** بعد جدول كمل على كذا صفحة: الفاصل بعد آخر صف والإجمالي، ورأس الجدول ما بيتكررش في الصفحة الجديدة. */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aBreakAfterATableThatRanOnComesAfterItsLastRow(boolean rightToLeft) throws Exception {
        Glyphs glyphs = Glyphs.of(document(rightToLeft, Paper.A5).title("Statement").table(longTable())
                .pageBreak().heading("Annex").text("notes").toBytes());

        int last = glyphs.pages() - 1;
        assertTrue(last >= 3, "الجدول على أكتر من صفحتين: " + last);
        assertTrue(glyphs.drawn("row120", last) && glyphs.drawn("Total", last), "آخر صف والإجمالي قبل الفاصل");
        assertTrue(glyphs.drawn("Day", last), "رأس الجدول مكرر في صفحاته");
        assertTrue(glyphs.drawn("Annex", last + 1));
        assertFalse(glyphs.drawn("Day", last + 1), "الجدول خلص: رأسه مش في صفحة الملحق");
    }

    /** الإيصال على اللفة صفحة واحدة بطول المحتوى: الفاصل ما بيعملش حاجة، والإيصال زي ما هو من غيره. */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aBreakOnARollDoesNothing(boolean wide) throws Exception {
        Paper roll = wide ? Paper.ROLL_80 : Paper.ROLL_58;
        byte[] broken = document(true, roll).letterhead("Corner Shop").title("Receipt").text("first")
                .pageBreak().text("second").pageBreak().summaryTotal("Total", "123.00").toBytes();
        byte[] plain = document(true, roll).letterhead("Corner Shop").title("Receipt").text("first")
                .text("second").summaryTotal("Total", "123.00").toBytes();

        assertEquals(1, pages(broken), "الإيصال صفحة واحدة");
        Glyphs withBreaks = Glyphs.of(broken);
        Glyphs without = Glyphs.of(plain);
        assertEquals(without.lineYs(1), withBreaks.lineYs(1), "نفس السطور في نفس أماكنها");
        try (PDDocument a = Loader.loadPDF(broken); PDDocument b = Loader.loadPDF(plain)) {
            assertEquals(b.getPage(0).getMediaBox().getHeight(), a.getPage(0).getMediaBox().getHeight(), 0.01f,
                    "نفس الطول");
        }
    }
}
