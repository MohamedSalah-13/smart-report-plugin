package com.smart.report.pdf;

import com.smart.report.ReportException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** الدفعة: كل مستند بصفحاته زي ما لو اتطبع لوحده، ورا بعض في ملف واحد، وعلى ورقة واحدة. */
class PdfBatchTest {

    /** كشف بترويسة باسم الشركة، وصفوفه بأسماء بحروف صغيرة عشان ما تتلخبطش مع الترويسة في البحث. */
    private static PdfDocument statement(boolean rightToLeft, Paper paper, String company, int rows) {
        PdfTable table = PdfTable.create().column("Day", 1).column("Amount", 1);
        for (int day = 1; day <= rows; day++) {
            table.row(company.toLowerCase(Locale.ROOT) + day, "5");
        }
        PdfDocument document = rightToLeft ? PdfDocument.rtl(paper) : PdfDocument.ltr(paper);
        return document.letterhead(company + " Haulage").title("Statement " + company).table(table);
    }

    private static List<PDRectangle> pages(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            List<PDRectangle> boxes = new ArrayList<>();
            document.getPages().forEach(page -> boxes.add(page.getMediaBox()));
            return boxes;
        }
    }

    /**
     * كل مستند بترويسته وترقيمه («1 من 3» بيبدأ من الأول)، وكل حرف في صفحاته في نفس المكان اللي كان هيبقى
     * فيه لو اتطبع لوحده.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void everyDocumentKeepsItsOwnLetterheadAndNumbering(boolean rightToLeft) throws Exception {
        String[] companies = {"Alpha", "Beta", "Gamma"};
        List<PdfDocument> documents = List.of(
                statement(rightToLeft, Paper.A5, "Alpha", 60),
                statement(rightToLeft, Paper.A5, "Beta", 120),
                statement(rightToLeft, Paper.A5, "Gamma", 3));
        Glyphs batch = Glyphs.of(PdfBatch.of(documents).toBytes());

        int page = 0;
        for (int d = 0; d < documents.size(); d++) {
            Glyphs alone = Glyphs.of(documents.get(d).toBytes());
            int count = alone.pages();
            assertTrue(d != 1 || count >= 3, "الكشف الطويل على أكتر من صفحتين: " + count);
            for (int k = 1; k <= count; k++) {
                page++;
                assertEquals(k == 1, batch.drawn(companies[d], page),
                        "ترويسة " + companies[d] + " في أول صفحة من مستندها بس (صفحة " + page + ")");
                if (count > 1) {
                    assertArrayEquals(new int[] {k, count}, batch.pageNumber(page, rightToLeft),
                            "ترقيم " + companies[d] + " في صفحة " + page);
                }
                assertSamePage(alone, k, batch, page);
            }
        }
        assertEquals(page, batch.pages(), "مفيش صفحات زيادة");
    }

    private static void assertSamePage(Glyphs expected, int expectedPage, Glyphs actual, int actualPage) {
        List<Glyphs.Glyph> want = expected.onPage(expectedPage);
        List<Glyphs.Glyph> got = actual.onPage(actualPage);
        assertEquals(want.size(), got.size(), "عدد الحروف في صفحة " + actualPage);
        for (int i = 0; i < want.size(); i++) {
            Glyphs.Glyph a = want.get(i);
            Glyphs.Glyph b = got.get(i);
            assertEquals(a.text(), b.text(), "صفحة " + actualPage + " حرف " + i);
            assertEquals(a.x(), b.x(), 0.01f, "صفحة " + actualPage + " حرف " + i + " " + a.text());
            assertEquals(a.y(), b.y(), 0.01f, "صفحة " + actualPage + " حرف " + i + " " + a.text());
        }
    }

    /** على اللفة كل مستند إيصال لوحده: صفحة بطول المكتوب فيه، مش صفحة واحدة طويلة للكل. */
    @Test
    void onARollEveryDocumentIsItsOwnReceipt() throws Exception {
        List<PdfDocument> receipts = List.of(
                statement(true, Paper.ROLL_80, "Alpha", 2),
                statement(true, Paper.ROLL_80, "Beta", 15),
                statement(true, Paper.ROLL_80, "Gamma", 6));
        List<PDRectangle> boxes = pages(PdfBatch.of(receipts).toBytes());

        assertEquals(3, boxes.size());
        for (int i = 0; i < receipts.size(); i++) {
            PDRectangle alone = pages(receipts.get(i).toBytes()).getFirst();
            assertEquals(Paper.ROLL_80.width(), boxes.get(i).getWidth(), 0.01f);
            assertEquals(alone.getHeight(), boxes.get(i).getHeight(), 0.01f, "إيصال " + (i + 1) + " بطوله");
        }
        assertTrue(boxes.get(1).getHeight() > boxes.get(0).getHeight(), "الإيصال الأطول أطول");
    }

    /** مهمة الطباعة بتطلب ورقة واحدة: نفس المقاس بالطول أو بالعرض بس. */
    @Test
    void everyDocumentMustPrintOnTheSameSheet() throws Exception {
        ReportException mixed = assertThrows(ReportException.class, () -> PdfBatch.of(
                statement(false, Paper.A4, "Alpha", 3), statement(false, Paper.A5, "Beta", 3)).toBytes());
        assertTrue(mixed.getMessage().contains("same paper") && mixed.getMessage().contains("document 2 is on A5"),
                mixed.getMessage());
        assertThrows(ReportException.class, () -> PdfBatch.of(
                statement(false, Paper.ROLL_80, "Alpha", 3), statement(false, Paper.ROLL_58, "Beta", 3)).toBytes());
        assertThrows(ReportException.class, () -> PdfBatch.of(
                statement(false, Paper.A4, "Alpha", 3), statement(false, Paper.ROLL_80, "Beta", 3)).toBytes());
        assertThrows(ReportException.class, () -> PdfPrinter.print(PdfBatch.of(
                statement(false, Paper.A4, "Alpha", 3), statement(false, Paper.A5, "Beta", 3)), "any printer", 1),
                "الطباعة بتفشل قبل ما تدوّر على الطابعة");

        // A4 بالعرض على نفس الورقة: كل صفحة بمقاسها.
        List<PDRectangle> boxes = pages(PdfBatch.of(statement(false, Paper.A4, "Alpha", 3),
                statement(false, Paper.A4.landscape(), "Beta", 3)).toBytes());
        assertEquals(2, boxes.size());
        assertTrue(boxes.get(0).getHeight() > boxes.get(0).getWidth());
        assertTrue(boxes.get(1).getWidth() > boxes.get(1).getHeight());

        assertTrue(PdfBatch.sameSheet(Paper.A4, Paper.ofMillimetres("Letterhead A4", 210, 297)));
        assertTrue(PdfBatch.sameSheet(Paper.A5.landscape(), Paper.A5));
        assertTrue(PdfBatch.sameSheet(Paper.ROLL_80, Paper.roll("Till", 80)));
        assertFalse(PdfBatch.sameSheet(Paper.A4, Paper.A5));
        assertFalse(PdfBatch.sameSheet(Paper.ROLL_80, Paper.ROLL_58));
        assertFalse(PdfBatch.sameSheet(Paper.ROLL_80, Paper.ofMillimetres("80 long", 80, 200)));
    }

    @Test
    void aBatchNeedsDocuments() {
        assertThrows(IllegalArgumentException.class, PdfBatch::of);
        assertThrows(IllegalArgumentException.class, () -> PdfBatch.of(List.of()));
        NullPointerException missing = assertThrows(NullPointerException.class,
                () -> PdfBatch.of(PdfDocument.ltr(Paper.A4), null));
        assertEquals("document 2", missing.getMessage());
    }

    /** الملف بيتحفظ في مجلد جديد، وعنوانه (اسم مهمة الطباعة) عنوان أول مستند. */
    @Test
    void savesOneFileTitledAfterTheFirstDocument(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("2026-11").resolve("statements.pdf");
        PdfBatch batch = PdfBatch.of(PdfDocument.ltr(Paper.A5).text("no title"),
                statement(false, Paper.A5, "Alpha", 3), statement(false, Paper.A5, "Beta", 3));
        batch.save(file);

        try (PDDocument saved = Loader.loadPDF(Files.readAllBytes(file))) {
            assertEquals(3, saved.getNumberOfPages());
            assertEquals("Statement Alpha", saved.getDocumentInformation().getTitle());
            assertEquals("smart-report-pdf", saved.getDocumentInformation().getCreator());
        }
        assertEquals(Paper.A5, batch.paper());
        assertEquals(3, batch.documents().size());
    }
}
