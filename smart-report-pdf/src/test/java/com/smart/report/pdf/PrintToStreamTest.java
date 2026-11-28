package com.smart.report.pdf;

import com.smart.report.ReportException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.ServiceUIFactory;
import javax.print.StreamPrintService;
import javax.print.StreamPrintServiceFactory;
import javax.print.attribute.Attribute;
import javax.print.attribute.AttributeSet;
import javax.print.attribute.PrintJobAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.PrintServiceAttribute;
import javax.print.attribute.PrintServiceAttributeSet;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.JobName;
import javax.print.attribute.standard.Media;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.SheetCollate;
import javax.print.event.PrintJobAttributeListener;
import javax.print.event.PrintJobListener;
import javax.print.event.PrintServiceAttributeListener;
import java.awt.print.PageFormat;
import java.awt.print.Pageable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * مسار الطباعة لحد الخدمة: {@link PdfPrinter} بيبعت لطابعة PostScript بتاعة Java بتكتب في الذاكرة، والاختبار
 * بيقرا اللي اتبعت — الطلبات (الورقة، النسخ، اسم المهمة) والصفحات زي ما الـ PostScript بيقولها (عددها ومقاس
 * كل ورقة). اللي بعد الخدمة — درايفر الطابعة ونظام التشغيل — مش هنا.
 */
class PrintToStreamTest {

    private static final Pattern PAGE = Pattern.compile("(?m)^%%Page: \\d+ \\d+$");
    private static final Pattern SHEET = Pattern.compile("/PageSize \\[([0-9.]+) ([0-9.]+)]");
    private static final Pattern COPIES = Pattern.compile("/NumCopies (\\d+)");

    private static byte[] statement(Paper paper, int rows) throws ReportException {
        PdfTable table = PdfTable.create().column("Day", 1).column("Amount", 1);
        for (int day = 1; day <= rows; day++) {
            table.row("row" + day, "5");
        }
        return PdfDocument.ltr(paper).title("Statement").table(table).toBytes();
    }

    private static List<PDRectangle> pdfPages(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            List<PDRectangle> pages = new ArrayList<>();
            document.getPages().forEach(page -> pages.add(page.getMediaBox()));
            return pages;
        }
    }

    /** اللي وصل الخدمة: عدد المهام، وطلبات المهمة، والصفحات زي ما Java سلّمتها، والـ PostScript اللي اتكتب. */
    private record Sent(int jobs, PrintRequestAttributeSet attributes, Pageable pageable, String postScript) {

        Attribute media() {
            return attributes.get(Media.class);
        }

        int pages() {
            return (int) PAGE.matcher(postScript).results().count();
        }

        /** مقاس الورقة اللي الـ PostScript بيطلبها: في أوله، ومع كل صفحة ورقتها مختلفة عن اللي قبلها. */
        List<float[]> sheets() {
            List<float[]> sheets = new ArrayList<>();
            Matcher sheet = SHEET.matcher(postScript);
            while (sheet.find()) {
                sheets.add(new float[] {Float.parseFloat(sheet.group(1)), Float.parseFloat(sheet.group(2))});
            }
            return sheets;
        }
    }

    private static Sent print(byte[] pdf, Paper paper, int copies) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RecordingService service = new RecordingService(out);
        PdfPrinter.printOn(service, pdf, paper, copies);
        assertNotNull(service.doc, "the job never reached the print service");
        String postScript = out.toString(StandardCharsets.ISO_8859_1);
        assertTrue(postScript.startsWith("%!PS"), "PostScript was written");
        return new Sent(service.jobs, service.attributes, (Pageable) service.doc.getPrintData(), postScript);
    }

    /** كل صفحات المستند وصلت، وكل ورقة طلبها الـ PostScript بالمقاس ده (بالنقطة). */
    private static void assertEverySheet(byte[] pdf, Sent sent, float width, float height) throws IOException {
        int pages = pdfPages(pdf).size();
        assertEquals(pages, sent.pageable().getNumberOfPages(), "pages handed to the print system");
        assertEquals(pages, sent.pages(), "pages in the PostScript");
        List<float[]> sheets = sent.sheets();
        assertFalse(sheets.isEmpty(), "the PostScript asks for a sheet size");
        for (float[] sheet : sheets) {
            assertEquals(width, sheet[0], 0.01f, "sheet width");
            assertEquals(height, sheet[1], 0.01f, "sheet height");
        }
    }

    private static Sent print(byte[] pdf, Paper paper, PrintOptions options) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RecordingService service = new RecordingService(out);
        PdfPrinter.printOn(service, pdf, paper, options);
        assertNotNull(service.doc, "the job never reached the print service");
        return new Sent(service.jobs, service.attributes, (Pageable) service.doc.getPrintData(),
                out.toString(StandardCharsets.ISO_8859_1));
    }

    /** مدى الصفحات والوجهين بيوصلوا المهمة، والصفحات اللي برّه المدى ما بتتطبعش. */
    @Test
    void aPageRangeAndBothSidesReachTheJob() throws Exception {
        byte[] pdf = statement(Paper.A4, 200);
        int pages = pdfPages(pdf).size();
        assertTrue(pages >= 4, "pages: " + pages);

        Sent sent = print(pdf, Paper.A4, PrintOptions.copies(1).pages(2, 3)
                .duplex(PrintOptions.Duplex.LONG_EDGE));
        javax.print.attribute.standard.PageRanges ranges =
                (javax.print.attribute.standard.PageRanges) sent.attributes().get(javax.print.attribute.standard.PageRanges.class);
        assertEquals("2-3", ranges.toString());
        assertEquals(javax.print.attribute.standard.Sides.TWO_SIDED_LONG_EDGE,
                sent.attributes().get(javax.print.attribute.standard.Sides.class));
        assertEquals(2, sent.pages(), "two pages in the PostScript");
        assertEquals(1, sent.jobs());

        Sent shortEdge = print(pdf, Paper.A4, PrintOptions.DEFAULT.duplex(PrintOptions.Duplex.SHORT_EDGE));
        assertEquals(javax.print.attribute.standard.Sides.TWO_SIDED_SHORT_EDGE,
                shortEdge.attributes().get(javax.print.attribute.standard.Sides.class));
        assertEquals(pages, shortEdge.pages());
        assertEquals(null, shortEdge.attributes().get(javax.print.attribute.standard.PageRanges.class));

        Sent oneSided = print(pdf, Paper.A4, PrintOptions.copies(2).duplex(PrintOptions.Duplex.ONE_SIDED));
        assertEquals(javax.print.attribute.standard.Sides.ONE_SIDED,
                oneSided.attributes().get(javax.print.attribute.standard.Sides.class));
        assertEquals(2, ((Copies) oneSided.attributes().get(Copies.class)).getValue());

        Sent defaults = print(pdf, Paper.A4, PrintOptions.DEFAULT);
        assertEquals(null, defaults.attributes().get(javax.print.attribute.standard.Sides.class), "افتراضي الطابعة");
    }

    /** المدى اللي آخره بعد آخر صفحة بيقف عند آخر صفحة؛ وأوله بعد آخر صفحة بيترفض. */
    @Test
    void aRangeBeyondTheDocument() throws Exception {
        byte[] pdf = statement(Paper.A4, 90);
        int pages = pdfPages(pdf).size();
        Sent sent = print(pdf, Paper.A4, PrintOptions.DEFAULT.pages(2, 99));
        assertEquals(pages - 1, sent.pages());
        assertThrows(com.smart.report.ReportException.class,
                () -> print(pdf, Paper.A4, PrintOptions.DEFAULT.pages(pages + 1, pages + 2)));
    }

    @Test
    void typedPageRanges() {
        assertEquals(PrintOptions.DEFAULT.pages(3, 3), PrintOptions.DEFAULT.pages("3", 10));
        assertEquals(PrintOptions.DEFAULT.pages(2, 5), PrintOptions.DEFAULT.pages(" 2 - 5 ", 10));
        assertEquals(PrintOptions.DEFAULT.pages(2, 5), PrintOptions.DEFAULT.pages("٢–٥", 10));
        assertTrue(PrintOptions.DEFAULT.pages("", 10).allPagesChosen());
        assertTrue(PrintOptions.DEFAULT.pages(null, 10).allPagesChosen());
        for (String bad : new String[]{"0", "5-2", "11", "2-11", "a", "2-3-4", "-3", "1,2", "1234567"}) {
            assertThrows(IllegalArgumentException.class, () -> PrintOptions.DEFAULT.pages(bad, 10), bad);
        }
        assertThrows(IllegalArgumentException.class, () -> PrintOptions.copies(0));
        assertThrows(IllegalArgumentException.class, () -> PrintOptions.copies(100));
        assertThrows(IllegalArgumentException.class, () -> PrintOptions.DEFAULT.pages(0, 3));
        assertThrows(IllegalArgumentException.class, () -> PrintOptions.DEFAULT.pages(3, 2));
    }

    @Test
    void a4IsSentOnA4WithEveryPage() throws Exception {
        byte[] pdf = statement(Paper.A4, 90);
        assertTrue(pdfPages(pdf).size() > 1, "more than one page");

        Sent sent = print(pdf, Paper.A4, 1);
        assertEquals(MediaSizeName.ISO_A4, sent.media());
        assertEverySheet(pdf, sent, Paper.A4.width(), Paper.A4.height());
    }

    /** العلامة المائية شفافة فوق كل صفحة، والمستند بيوصل الطابعة (PostScript) بكل صفحاته زي من غيرها. */
    @Test
    void aWatermarkedDocumentIsSentWithEveryPage() throws Exception {
        PdfTable table = PdfTable.create().column("Day", 1).column("Amount", 1);
        for (int day = 1; day <= 90; day++) {
            table.row("row" + day, "5");
        }
        byte[] pdf = PdfDocument.rtl(Paper.A4).title("كشف").table(table).watermark("صورة").toBytes();
        assertTrue(pdfPages(pdf).size() > 1, "more than one page");

        Sent sent = print(pdf, Paper.A4, 1);
        assertEverySheet(pdf, sent, Paper.A4.width(), Paper.A4.height());
    }

    @Test
    void a5IsSentOnA5WithEveryPage() throws Exception {
        byte[] pdf = statement(Paper.A5, 90);
        assertTrue(pdfPages(pdf).size() > 1, "more than one page");

        Sent sent = print(pdf, Paper.A5, 1);
        assertEquals(MediaSizeName.ISO_A5, sent.media());
        assertEverySheet(pdf, sent, Paper.A5.width(), Paper.A5.height());
    }

    /** A5 بالعرض بيتطلب له ورق A5، والصفحة بتتلف عليه: الورقة في الطابعة واقفة. */
    @Test
    void a5LandscapeIsSentOnA5TurnedOntoTheSheet() throws Exception {
        Paper wide = Paper.A5.landscape();
        byte[] pdf = statement(wide, 90);
        assertTrue(pdfPages(pdf).size() > 1, "more than one page");

        Sent sent = print(pdf, wide, 1);
        assertEquals(MediaSizeName.ISO_A5, sent.media());
        assertEverySheet(pdf, sent, Paper.A5.width(), Paper.A5.height());
        for (int page = 0; page < sent.pageable().getNumberOfPages(); page++) {
            assertEquals(PageFormat.LANDSCAPE, sent.pageable().getPageFormat(page).getOrientation(), "page " + page);
        }
    }

    /** اللفة الحرارية من غير ورق مطلوب (الطابعة بتستخدم لفتها)، والصفحة بطول الإيصال مش بأقصى طول. */
    @Test
    void receiptIsSentWithNoPaperAtItsOwnLength() throws Exception {
        byte[] pdf = statement(Paper.ROLL_80, 12);
        List<PDRectangle> pages = pdfPages(pdf);
        assertEquals(1, pages.size(), "a receipt is one page");
        float length = pages.getFirst().getHeight();
        assertTrue(length < Paper.ROLL_80.height(), "cut to its content: " + length);

        Sent sent = print(pdf, Paper.ROLL_80, 1);
        assertNull(sent.media(), "no paper is asked for a roll");
        assertEverySheet(pdf, sent, Paper.ROLL_80.width(), length);
        assertEquals(PageFormat.PORTRAIT, sent.pageable().getPageFormat(0).getOrientation());
    }

    @Test
    void copiesAreKeptInRangeAndTheJobIsNamedAfterTheTitle() throws Exception {
        byte[] pdf = PdfDocument.ltr(Paper.A5).title("Statement 7").text("one line").toBytes();

        Sent many = print(pdf, Paper.A5, 500);
        assertEquals(new Copies(PdfPrinter.MAX_COPIES), many.attributes().get(Copies.class));
        assertEquals(List.of(String.valueOf(PdfPrinter.MAX_COPIES)),
                COPIES.matcher(many.postScript()).results().map(copies -> copies.group(1)).toList());
        assertEquals(1, many.pages(), "the printer makes the copies; the page is sent once");
        assertEquals("Statement 7", ((JobName) many.attributes().get(JobName.class)).getValue());

        Sent none = print(pdf, Paper.A5, 0);
        assertEquals(new Copies(1), none.attributes().get(Copies.class));
        assertNull(many.attributes().get(SheetCollate.class), "صفحة واحدة: مفيش ترتيب، والطابعة بتعمل النسخ");
    }

    /** المستند اللي أكتر من صفحة بيطلع نسخًا كاملة مرتبة: الطابعة بتترتب، والصفحات بتتبعت مرة واحدة. */
    @Test
    void copiesOfSeveralPagesAreCollated() throws Exception {
        byte[] pdf = statement(Paper.A5, 90);
        assertTrue(pdfPages(pdf).size() > 1);

        Sent two = print(pdf, Paper.A5, 2);
        assertEquals(SheetCollate.COLLATED, two.attributes().get(SheetCollate.class));
        assertTrue(two.postScript().contains("/Collate true /NumCopies 2"), "الطابعة مطلوب منها ترتب النسخ");
        assertEverySheet(pdf, two, Paper.A5.width(), Paper.A5.height());

        assertNull(print(pdf, Paper.A5, 1).attributes().get(SheetCollate.class), "نسخة واحدة: مفيش ترتيب");
    }

    /**
     * الدفعة مهمة واحدة فيها صفحات كل المستندات بترتيبها، على ورقتهم، والنسخ مجموعات كاملة: الدفعة كلها،
     * وبعدها الدفعة كلها تاني.
     */
    @Test
    void aBatchIsOneJobWithEveryPageAndCollatedCopies() throws Exception {
        List<PdfDocument> statements = new ArrayList<>();
        int expected = 0;
        for (int rows : new int[] {90, 5, 60}) {
            PdfTable table = PdfTable.create().column("Day", 1).column("Amount", 1);
            for (int day = 1; day <= rows; day++) {
                table.row("row" + day, "5");
            }
            PdfDocument document = PdfDocument.rtl(Paper.A5).title("كشف " + rows).table(table);
            expected += pdfPages(document.toBytes()).size();
            statements.add(document);
        }
        PdfBatch batch = PdfBatch.of(statements);
        byte[] pdf = batch.toBytes();
        assertEquals(expected, pdfPages(pdf).size());

        Sent sent = print(pdf, batch.paper(), 3);
        assertEquals(1, sent.jobs(), "مهمة واحدة");
        assertEquals(MediaSizeName.ISO_A5, sent.media());
        assertEverySheet(pdf, sent, Paper.A5.width(), Paper.A5.height());
        assertEquals(SheetCollate.COLLATED, sent.attributes().get(SheetCollate.class));
        assertTrue(sent.postScript().contains("/Collate true /NumCopies 3"));
        assertEquals("كشف 90", ((JobName) sent.attributes().get(JobName.class)).getValue(), "باسم أول مستند");
    }

    /** دفعة إيصالات: من غير ورق مطلوب، وكل إيصال صفحة بطوله، فالطابعة بتقص بينهم. */
    @Test
    void aBatchOfReceiptsSendsEveryReceiptAtItsOwnLength() throws Exception {
        PdfBatch batch = PdfBatch.of(
                PdfDocument.ltr(Paper.ROLL_80).title("Receipt 1").text("one item"),
                PdfDocument.ltr(Paper.ROLL_80).title("Receipt 2").table(t -> {
                    t.column("Item", 1).column("Price", 1);
                    for (int i = 1; i <= 20; i++) {
                        t.row("item" + i, "10.00");
                    }
                }));
        byte[] pdf = batch.toBytes();
        List<PDRectangle> pages = pdfPages(pdf);
        assertEquals(2, pages.size());
        assertTrue(pages.get(1).getHeight() > pages.get(0).getHeight() + 100, "كل إيصال بطوله");

        Sent sent = print(pdf, batch.paper(), 1);
        assertEquals(1, sent.jobs());
        assertNull(sent.media(), "اللفة من غير ورق مطلوب");
        assertEquals(2, sent.pages());
        List<Float> lengths = sent.sheets().stream().map(sheet -> sheet[1]).distinct().toList();
        for (PDRectangle page : pages) {
            assertTrue(lengths.stream().anyMatch(length -> Math.abs(length - page.getHeight()) < 0.01f),
                    "ورقة بطول " + page.getHeight() + " في " + lengths);
        }
        for (float[] sheet : sent.sheets()) {
            assertEquals(Paper.ROLL_80.width(), sheet[0], 0.01f);
        }
    }

    /** A4 بالطول و A4 بالعرض في دفعة واحدة: ورقة A4 للكل، والصفحة العرضية بتتلف عليها. */
    @Test
    void aBatchMixingA4WaysRoundIsSentOnA4() throws Exception {
        PdfBatch batch = PdfBatch.of(PdfDocument.ltr(Paper.A4).title("Upright"),
                PdfDocument.ltr(Paper.A4.landscape()).title("Wide"));
        byte[] pdf = batch.toBytes();

        Sent sent = print(pdf, batch.paper(), 1);
        assertEquals(MediaSizeName.ISO_A4, sent.media());
        assertEverySheet(pdf, sent, Paper.A4.width(), Paper.A4.height());
        assertEquals(PageFormat.PORTRAIT, sent.pageable().getPageFormat(0).getOrientation());
        assertEquals(PageFormat.LANDSCAPE, sent.pageable().getPageFormat(1).getOrientation());
    }

    /**
     * طابعة PostScript اللي Java جاية بيها، بتكتب في stream، وبتسجّل المهمة اللي وصلتها قبل ما تكتبها:
     * المستند والطلبات. كل الباقي بيتحوّل لها زي ما هو.
     */
    private static final class RecordingService extends StreamPrintService {

        private final StreamPrintService postScript;
        private Doc doc;
        private PrintRequestAttributeSet attributes;
        private int jobs;

        RecordingService(ByteArrayOutputStream out) {
            super(out);
            StreamPrintServiceFactory[] factories = StreamPrintServiceFactory.lookupStreamPrintServiceFactories(
                    DocFlavor.SERVICE_FORMATTED.PAGEABLE, "application/postscript");
            assertTrue(factories.length > 0, "Java comes with a PostScript stream printer");
            this.postScript = factories[0].getPrintService(out);
        }

        @Override
        public DocPrintJob createPrintJob() {
            DocPrintJob job = postScript.createPrintJob();
            return new DocPrintJob() {
                @Override
                public void print(Doc sentDoc, PrintRequestAttributeSet sentAttributes) throws PrintException {
                    jobs++;
                    doc = sentDoc;
                    attributes = sentAttributes;
                    job.print(sentDoc, sentAttributes);
                }

                @Override
                public PrintService getPrintService() {
                    return RecordingService.this;
                }

                @Override
                public PrintJobAttributeSet getAttributes() {
                    return job.getAttributes();
                }

                @Override
                public void addPrintJobListener(PrintJobListener listener) {
                    job.addPrintJobListener(listener);
                }

                @Override
                public void removePrintJobListener(PrintJobListener listener) {
                    job.removePrintJobListener(listener);
                }

                @Override
                public void addPrintJobAttributeListener(PrintJobAttributeListener listener,
                                                         PrintJobAttributeSet attributes) {
                    job.addPrintJobAttributeListener(listener, attributes);
                }

                @Override
                public void removePrintJobAttributeListener(PrintJobAttributeListener listener) {
                    job.removePrintJobAttributeListener(listener);
                }
            };
        }

        @Override
        public String getOutputFormat() {
            return postScript.getOutputFormat();
        }

        @Override
        public String getName() {
            return postScript.getName();
        }

        @Override
        public void addPrintServiceAttributeListener(PrintServiceAttributeListener listener) {
            postScript.addPrintServiceAttributeListener(listener);
        }

        @Override
        public void removePrintServiceAttributeListener(PrintServiceAttributeListener listener) {
            postScript.removePrintServiceAttributeListener(listener);
        }

        @Override
        public PrintServiceAttributeSet getAttributes() {
            return postScript.getAttributes();
        }

        @Override
        public <T extends PrintServiceAttribute> T getAttribute(Class<T> category) {
            return postScript.getAttribute(category);
        }

        @Override
        public DocFlavor[] getSupportedDocFlavors() {
            return postScript.getSupportedDocFlavors();
        }

        @Override
        public boolean isDocFlavorSupported(DocFlavor flavor) {
            return postScript.isDocFlavorSupported(flavor);
        }

        @Override
        public Class<?>[] getSupportedAttributeCategories() {
            return postScript.getSupportedAttributeCategories();
        }

        @Override
        public boolean isAttributeCategorySupported(Class<? extends Attribute> category) {
            return postScript.isAttributeCategorySupported(category);
        }

        @Override
        public Object getDefaultAttributeValue(Class<? extends Attribute> category) {
            return postScript.getDefaultAttributeValue(category);
        }

        @Override
        public Object getSupportedAttributeValues(Class<? extends Attribute> category, DocFlavor flavor,
                                                  AttributeSet attributes) {
            return postScript.getSupportedAttributeValues(category, flavor, attributes);
        }

        @Override
        public boolean isAttributeValueSupported(Attribute attribute, DocFlavor flavor, AttributeSet attributes) {
            return postScript.isAttributeValueSupported(attribute, flavor, attributes);
        }

        @Override
        public AttributeSet getUnsupportedAttributes(DocFlavor flavor, AttributeSet attributes) {
            return postScript.getUnsupportedAttributes(flavor, attributes);
        }

        @Override
        public ServiceUIFactory getServiceUIFactory() {
            return postScript.getServiceUIFactory();
        }
    }
}
