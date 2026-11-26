package com.smart.report.pdf;

import com.smart.report.ReportException;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfPagesTest {

    private static byte[] longDocument(Paper paper) throws Exception {
        PdfTable table = PdfTable.create().column("Day", 1).column("Amount", 1);
        for (int day = 1; day <= 120; day++) {
            table.row("row" + day, "5");
        }
        return PdfDocument.ltr(paper).title("Report").table(table).toBytes();
    }

    @Test
    void pagesAreCountedAndMeasuredInPoints() throws Exception {
        try (PdfPages pages = PdfPages.open(longDocument(Paper.A5))) {
            assertTrue(pages.count() >= 3);
            assertEquals(Paper.A5.width(), pages.width(0), 0.05f);
            assertEquals(Paper.A5.height(), pages.height(pages.count() - 1), 0.05f);
        }
    }

    @Test
    void landscapePageIsWiderThanItIsTall() throws Exception {
        try (PdfPages pages = PdfPages.open(longDocument(Paper.A4.landscape()))) {
            assertTrue(pages.width(0) > pages.height(0));
        }
    }

    @Test
    void pageIsDrawnAtTheScaleAskedFor() throws Exception {
        try (PdfPages pages = PdfPages.open(longDocument(Paper.A5))) {
            BufferedImage small = pages.render(0, 1f);
            BufferedImage large = pages.render(0, 2f);

            assertEquals(Math.round(Paper.A5.width()), small.getWidth(), 1);
            assertEquals(small.getWidth() * 2, large.getWidth(), 2);
            // الصفحة مش بيضاء: فيها حبر.
            assertNotEquals(0xFFFFFF, darkest(small) & 0xFFFFFF);
        }
    }

    /** مقياس أكبر من الحد ما بيحجزش صورة عملاقة. */
    @Test
    void scaleIsHeldAtItsLimit() throws Exception {
        try (PdfPages pages = PdfPages.open(longDocument(Paper.A5))) {
            BufferedImage image = pages.render(0, 50f);
            assertEquals(Math.round(Paper.A5.width() * PdfPages.MAX_SCALE), image.getWidth(), 2);
        }
    }

    @Test
    void notAPdfIsReported() {
        assertThrows(ReportException.class, () -> PdfPages.open("not a pdf".getBytes()));
    }

    private static int darkest(BufferedImage image) {
        int darkest = 0xFFFFFF;
        for (int y = 0; y < image.getHeight(); y += 3) {
            for (int x = 0; x < image.getWidth(); x += 3) {
                int rgb = image.getRGB(x, y) & 0xFFFFFF;
                if (rgb < darkest) {
                    darkest = rgb;
                }
            }
        }
        return darkest;
    }
}
