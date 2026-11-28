package com.smart.report.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** شكل التقرير اللي المحل بيختاره مرة ({@link ReportLook}) وأجزاؤه في {@link PdfDocument} و {@link PdfStyle}. */
class ReportLookTest {

    private static final float MM = 72f / 25.4f;
    private static final LocalDateTime WHEN = LocalDateTime.of(2026, 11, 28, 10, 30, 45);

    private static String text(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document).replace(String.valueOf((char) 13), "");
        }
    }

    private static int pages(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        }
    }

    private static PdfTable table(int rows) {
        PdfTable table = PdfTable.create().column("Item", 3).column("Amount", 2, Align.END);
        for (int i = 0; i < rows; i++) {
            table.row("row" + i, "5.00");
        }
        return table.totals("Total", "99.00");
    }

    @Test
    void aColorSetPaintsTheHeaderBand() throws Exception {
        byte[] pdf = PdfDocument.ltr(Paper.A4).style(PdfStyle.standard().withColorSet(ColorSet.NAVY))
                .table(table(3)).toBytes();
        Glyphs.Glyph header = Glyphs.of(pdf).first("Item");
        BufferedImage page;
        try (PDDocument document = Loader.loadPDF(pdf)) {
            page = new PDFRenderer(document).renderImageWithDPI(0, 72, ImageType.RGB);
        }
        Color band = new Color(page.getRGB(Math.round(header.x()) - 3, Math.round(header.y()) - 3));
        assertEquals(new Color(ColorSet.NAVY.headerFill()), band);
        PdfStyle navy = PdfStyle.standard().withColorSet(ColorSet.NAVY);
        assertEquals(ColorSet.NAVY.headerText(), navy.headerText());
        assertEquals(ColorSet.NAVY.totalsFill(), navy.totalsFill());
        assertEquals(PdfStyle.standard().bodySize(), navy.bodySize(), "الأحجام زي ما هي");
        assertEquals(PdfStyle.standard(), PdfStyle.standard().withColorSet(ColorSet.GREY), "الرمادي هو الافتراضي");
    }

    @Test
    void thePrintedLineSitsUnderTheTitle() throws Exception {
        byte[] pdf = PdfDocument.ltr(Paper.A4).title("Invoice").printedOn(WHEN).printedBy("Ahmed")
                .table(table(2)).toBytes();
        assertTrue(text(pdf).contains("Printed on 2026-11-28 10:30 by Ahmed"), text(pdf));
        Glyphs glyphs = Glyphs.of(pdf);
        assertTrue(glyphs.first("Printed").y() > glyphs.first("Invoice").y());
        assertTrue(glyphs.first("Printed").y() < glyphs.first("Item").y());

        assertTrue(text(PdfDocument.ltr(Paper.A4).title("x").printedBy("Mona").toBytes()).contains("By Mona"));
        assertTrue(text(PdfDocument.ltr(Paper.A4).title("x").printedOn(WHEN).toBytes())
                .contains("Printed on 2026-11-28 10:30"));
        String arabic = text(PdfDocument.rtl(Paper.A4).title("فاتورة").printedOn(WHEN).printedBy("أحمد").toBytes());
        assertTrue(arabic.contains("بواسطة"), arabic);
        // من غير عنوان السطر في أول المستند.
        byte[] untitled = PdfDocument.ltr(Paper.A4).printedBy("Mona").table(table(1)).toBytes();
        assertTrue(Glyphs.of(untitled).first("Mona").y() < Glyphs.of(untitled).first("Item").y());
    }

    @Test
    void pageNumbersAsOneSlashThree() throws Exception {
        String text = text(PdfDocument.ltr(Paper.A5).pageNumberStyle(PageNumberStyle.SLASH).table(table(120))
                .toBytes());
        assertTrue(text.contains("1 / "), text);
        assertFalse(text.contains("Page 1 of"));
        assertTrue(text(PdfDocument.ltr(Paper.A5).table(table(120)).toBytes()).contains("Page 1 of"));
    }

    /** الورق المطبوع: الترويسة ما بتترسمش، والمحتوى بيبدأ تحت المسافة في كل صفحة. */
    @Test
    void prePrintedPaperLeavesTheTopForTheLetterhead() throws Exception {
        byte[] plain = PdfDocument.ltr(Paper.A4).table(table(150)).toBytes();
        byte[] printed = PdfDocument.ltr(Paper.A4).letterhead("Shop name", "Street 1").logo(null)
                .prePrinted(40).table(table(150)).toBytes();
        String text = text(printed);
        assertFalse(text.contains("Shop name"), text);
        Glyphs before = Glyphs.of(plain);
        Glyphs after = Glyphs.of(printed);
        assertEquals(before.first("Item", 1).y() + 40 * MM, after.first("Item", 1).y(), 0.5f);
        assertEquals(before.first("Item", 2).y() + 40 * MM, after.first("Item", 2).y(), 0.5f, "كل صفحة");
        assertTrue(pages(printed) >= pages(plain));

        byte[] back = PdfDocument.ltr(Paper.A4).letterhead("Shop name").prePrinted(40).plainPaper().title("x").toBytes();
        assertTrue(text(back).contains("Shop name"));
        assertThrows(IllegalArgumentException.class, () -> PdfDocument.ltr(Paper.A4).prePrinted(-1));
    }

    @Test
    void theTotalsRowHasItsOwnSize() throws Exception {
        Glyphs normal = Glyphs.of(PdfDocument.ltr(Paper.A4).table(table(2)).toBytes());
        Glyphs bigger = Glyphs.of(PdfDocument.ltr(Paper.A4).totalsFontSize(15).table(table(2)).toBytes());
        float ratio = bigger.first("Total").width() / normal.first("Total").width();
        assertEquals(15f / PdfStyle.standard().bodySize(), ratio, 0.05f);
        assertEquals(normal.first("row0").width(), bigger.first("row0").width(), 0.01f, "الصفوف زي ما هي");
        assertThrows(IllegalArgumentException.class, () -> PdfDocument.ltr(Paper.A4).totalsFontSize(3));
    }

    @Test
    void theCodecRoundTrips() {
        ReportLook look = ReportLook.DEFAULT.withColorSet(ColorSet.MAROON).withInkSaver(true).withGrid(false)
                .withStripes(true).withPageNumbers(PageNumberStyle.SLASH).withPrintedLine(true, false)
                .withPrePrinted(37.5f).withTotalsFontSize(12);
        assertEquals("colors=maroon;ink=true;grid=false;stripes=true;numbers=slash;date=true;user=false;"
                + "preprinted=37.5;totals=12", look.encode());
        assertEquals(look, ReportLook.decode(look.encode()));
        assertEquals(ReportLook.DEFAULT, ReportLook.decode(ReportLook.DEFAULT.encode()));
        assertEquals(ReportLook.DEFAULT.withPlainPaper(), ReportLook.decode("preprinted=off"));
    }

    /** نسخة تانية كتبت حاجة: كل جزء ما يتقريش بياخد الافتراضي، والطباعة ما بتقفش. */
    @Test
    void theCodecReadsTolerantly() {
        assertEquals(ReportLook.DEFAULT, ReportLook.decode(null));
        assertEquals(ReportLook.DEFAULT, ReportLook.decode(""));
        assertEquals(ReportLook.DEFAULT, ReportLook.decode("{json:true}"));
        assertEquals(ReportLook.DEFAULT.withColorSet(ColorSet.TEAL),
                ReportLook.decode("colors=TEAL;numbers=roman;grid=maybe;totals=500;preprinted=-4;shadow=on"));
        assertEquals(ReportLook.DEFAULT.withPrePrinted(30), ReportLook.decode(" PREPRINTED = 30 ; totals = x "));
        assertEquals(ReportLook.DEFAULT, ReportLook.decode("preprinted=999"));
    }

    @Test
    void applyToSetsEveryPart() throws Exception {
        ReportLook look = ReportLook.DEFAULT.withColorSet(ColorSet.GREEN).withPageNumbers(PageNumberStyle.SLASH)
                .withPrintedLine(false, true).withPrePrinted(20);
        PdfDocument document = look.applyTo(PdfDocument.ltr(Paper.A5).letterhead("Shop").title("Bill")
                .table(table(120)), "Omar");
        assertEquals(ColorSet.GREEN.headerFill(), document.resolvedStyle().headerFill());
        assertEquals(PdfStyle.forPaper(Paper.A5).bodySize(), document.resolvedStyle().bodySize(), "مقاس الورقة");
        String text = text(document.toBytes());
        assertTrue(text.contains("By Omar"), text);
        assertTrue(text.contains("1 / "), text);
        assertFalse(text.contains("Shop"));
        assertEquals(0xFFFFFF, ReportLook.DEFAULT.withInkSaver(true).applyTo(PdfDocument.ltr(Paper.A4), null)
                .resolvedStyle().headerFill(), "توفير الحبر فوق الألوان");
    }

    @Test
    void aSampleForALivePreview() throws Exception {
        ReportLook look = ReportLook.DEFAULT.withColorSet(ColorSet.NAVY).withPrintedLine(true, true);
        byte[] a4 = look.sample(Paper.A4, true, "أحمد").toBytes();
        assertTrue(pages(a4) >= 2, "صفحتين عشان رقم الصفحة يبان");
        assertTrue(text(a4).contains("أحمد"));
        assertEquals(1, pages(look.sample(Paper.ROLL_80, true, null).toBytes()));
    }

    @Test
    void badValuesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> ReportLook.DEFAULT.withPrePrinted(-1));
        assertThrows(IllegalArgumentException.class, () -> ReportLook.DEFAULT.withPrePrinted(201));
        assertThrows(IllegalArgumentException.class, () -> ReportLook.DEFAULT.withTotalsFontSize(100));
        assertThrows(NullPointerException.class, () -> ReportLook.DEFAULT.withColorSet(null));
    }
}
