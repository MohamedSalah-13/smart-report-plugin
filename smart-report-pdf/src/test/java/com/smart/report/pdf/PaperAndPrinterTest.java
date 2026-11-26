package com.smart.report.pdf;

import com.smart.report.ReportException;
import org.junit.jupiter.api.Test;

import javax.print.attribute.standard.MediaSizeName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperAndPrinterTest {

    @Test
    void paperSizesAreTheIsoOnes() {
        assertEquals(210f, Paper.A4.widthMm(), 0.01f);
        assertEquals(297f, Paper.A4.heightMm(), 0.01f);
        assertEquals(148f, Paper.A5.widthMm(), 0.01f);
        assertEquals(210f, Paper.A5.heightMm(), 0.01f);
    }

    @Test
    void landscapeTurnsThePaperAndPortraitTurnsItBack() {
        Paper wide = Paper.A5.landscape();
        assertTrue(wide.isLandscape());
        assertEquals(Paper.A5.height(), wide.width());
        assertEquals(Paper.A5, wide.portrait());
        assertEquals(wide, wide.landscape());
    }

    @Test
    void paperIsParsedFromItsName() {
        assertEquals(Paper.A4, Paper.parse("a4"));
        assertEquals(Paper.A5, Paper.parse(" A5 "));
        assertEquals(Paper.A4.landscape(), Paper.parse("A4-landscape"));
        assertEquals(Paper.A5.landscape(), Paper.parse("a5 landscape"));
        assertThrows(IllegalArgumentException.class, () -> Paper.parse("letter"));
    }

    @Test
    void compactStyleIsChosenForNarrowPaper() {
        assertTrue(PdfStyle.forPaper(Paper.A5).bodySize() < PdfStyle.forPaper(Paper.A4).bodySize());
        assertTrue(PdfStyle.forPaper(Paper.A5).margin() < PdfStyle.forPaper(Paper.A4).margin());
        // A5 بالعرض عرضه عرض A4: نفس حجم الخط، بهامش الورقة القصيرة.
        assertEquals(PdfStyle.forPaper(Paper.A4).bodySize(), PdfStyle.forPaper(Paper.A5.landscape()).bodySize());
    }

    /** الطابعة بتتطلب منها ورقة المستند، بالطول أو بالعرض. */
    @Test
    void printerIsAskedForTheDocumentsPaper() {
        assertEquals(MediaSizeName.ISO_A4, PdfPrinter.media(Paper.A4));
        assertEquals(MediaSizeName.ISO_A5, PdfPrinter.media(Paper.A5));
        assertEquals(MediaSizeName.ISO_A5, PdfPrinter.media(Paper.A5.landscape()));
        assertNull(PdfPrinter.media(null));
    }

    @Test
    void printersAreListedWithoutFailing() {
        assertNotNull(PdfPrinter.printerNames());
        assertNotNull(PdfPrinter.defaultPrinter());
    }

    /** طابعة مش على الجهاز: رسالة باسمها وبالطابعات المتاحة، ومفيش حاجة بتتبعت لأي طابعة تانية. */
    @Test
    void unknownPrinterIsReportedByName() throws Exception {
        byte[] pdf = PdfDocument.ltr(Paper.A5).title("Report").toBytes();

        ReportException thrown = assertThrows(ReportException.class,
                () -> PdfPrinter.print(pdf, "no-such-printer-7f3a", Paper.A5, 1));
        assertTrue(thrown.getMessage().contains("no-such-printer-7f3a"), thrown.getMessage());
    }
}
