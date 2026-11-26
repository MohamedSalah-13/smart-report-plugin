package com.smart.report.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** الورق الحراري: عرض ثابت، والإيصال صفحة واحدة بطول المكتوب فيه. */
class RollPaperTest {

    private static PdfDocument receipt(Paper paper, int items) {
        PdfTable table = PdfTable.create().column("Item", 3).column("Qty", 1).column("Price", 2, Align.END);
        for (int i = 1; i <= items; i++) {
            table.row("item" + i, "1", "10.00");
        }
        return PdfDocument.ltr(paper).letterhead("Corner Shop").title("Receipt").table(table)
                .summaryTotal("Total", "123.00").footer("Thank you");
    }

    private static List<PDRectangle> pages(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            List<PDRectangle> boxes = new ArrayList<>();
            document.getPages().forEach(page -> boxes.add(page.getMediaBox()));
            return boxes;
        }
    }

    @Test
    void shortReceiptIsOnePageAsLongAsItsContent() throws Exception {
        byte[] pdf = receipt(Paper.ROLL_80, 3).toBytes();
        List<PDRectangle> boxes = pages(pdf);

        assertEquals(1, boxes.size());
        assertEquals(Paper.ROLL_80.width(), boxes.get(0).getWidth(), 0.05f);
        assertEquals(80f, Paper.ROLL_80.widthMm(), 0.01f);
        float height = boxes.get(0).getHeight();
        assertTrue(height < 400, "إيصال صغير مش صفحة A4: " + height);

        Glyphs glyphs = Glyphs.of(pdf);
        assertTrue(glyphs.drawn("Thank", 1), "آخر سطر مرسوم");
        float margin = PdfStyle.forPaper(Paper.ROLL_80).margin();
        assertTrue(glyphs.lowest() <= height - margin, "مفيش حاجة في الهامش: " + glyphs.lowest() + " من " + height);
        assertTrue(glyphs.lowest() > height - margin - 40, "ومفيش ورق فاضي كتير تحت آخر سطر");
    }

    @Test
    void longReceiptStaysOnOnePageAndGrows() throws Exception {
        List<PDRectangle> boxes = pages(receipt(Paper.ROLL_80, 150).toBytes());

        assertEquals(1, boxes.size(), "الإيصال ما بيتقسمش لصفحات");
        assertTrue(boxes.get(0).getHeight() > 1500, "" + boxes.get(0).getHeight());
        assertTrue(Glyphs.of(receipt(Paper.ROLL_80, 150).toBytes()).drawn("item150", 1));
    }

    /** أطول من أقصى صفحة PDF: بيتقسم على صفحات بالطول ده، ومفيش سطر بيضيع. */
    @Test
    void receiptLongerThanAPdfPageContinuesOnAnother() throws Exception {
        byte[] pdf = receipt(Paper.ROLL_80, 1300).toBytes();
        List<PDRectangle> boxes = pages(pdf);

        assertTrue(boxes.size() >= 2, "" + boxes.size());
        assertEquals(Paper.MAX_PAGE_POINTS, boxes.get(0).getHeight(), 0.5f);
        Glyphs glyphs = Glyphs.of(pdf);
        assertTrue(glyphs.drawn("item1300", glyphs.pages()));
    }

    /**
     * عنوان القسم بيحجز مكانًا لنفسه ولرأس جدول وصف بعده، أكتر من اللي بيرسمه. القياس كان بيحسب
     * المرسوم بس، فالورقة المقصوصة ما كانتش بتكفّي الحجز والعنوان كان بيتنقل لورقة تانية.
     */
    @Test
    void receiptEndingInAHeadingStaysOnOnePage() throws Exception {
        for (PdfDocument document : new PdfDocument[]{
                PdfDocument.ltr(Paper.ROLL_80).text("line\n".repeat(30)).heading("End"),
                PdfDocument.ltr(Paper.ROLL_80).text("line\n".repeat(30)).heading("End").text("one"),
                receipt(Paper.ROLL_80, 5).heading("End"),
                receipt(Paper.ROLL_58, 5).signatures("Cashier", "Customer")}) {
            byte[] pdf = document.toBytes();
            assertEquals(1, pages(pdf).size());
            float height = pages(pdf).get(0).getHeight();
            assertTrue(Glyphs.of(pdf).lowest() <= height - PdfStyle.forPaper(Paper.ROLL_80).margin(), "" + height);
        }
    }

    @Test
    void narrowRollTakesASmallerStyle() throws Exception {
        List<PDRectangle> boxes = pages(receipt(Paper.ROLL_58, 3).toBytes());

        assertEquals(Paper.ROLL_58.width(), boxes.get(0).getWidth(), 0.05f);
        assertTrue(PdfStyle.forPaper(Paper.ROLL_58).bodySize() < PdfStyle.forPaper(Paper.ROLL_80).bodySize());
        assertTrue(PdfStyle.forPaper(Paper.ROLL_80).margin() < PdfStyle.forPaper(Paper.A5).margin());
    }

    @Test
    void rollIsParsedAndNeverTurned() {
        assertEquals(Paper.ROLL_80, Paper.parse("80mm"));
        assertEquals(Paper.ROLL_80, Paper.parse(" roll-80mm "));
        assertEquals(Paper.ROLL_58, Paper.parse("58 MM"));
        assertThrows(IllegalArgumentException.class, () -> Paper.parse("76mm"));
        assertEquals(Paper.ROLL_80, Paper.ROLL_80.landscape(), "اللفة ما بتتقلبش");
        assertTrue(!Paper.ROLL_80.isLandscape());
        assertEquals(Paper.A4, new Paper("A4", Paper.A4.width(), Paper.A4.height()), "الورق العادي زي ما هو");
    }

    /** الطابعة بتستخدم لفتها: مفيش مقاس بيتطلب منها. */
    @Test
    void printerIsNotAskedForAPaperSizeForARoll() {
        assertNull(PdfPrinter.media(Paper.ROLL_80));
        assertNull(PdfPrinter.media(Paper.ROLL_58));
    }
}
