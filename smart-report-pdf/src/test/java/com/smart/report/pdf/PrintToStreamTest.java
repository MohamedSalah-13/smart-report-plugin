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

    /** اللي وصل الخدمة: طلبات المهمة، والصفحات زي ما Java سلّمتها، والـ PostScript اللي اتكتب. */
    private record Sent(PrintRequestAttributeSet attributes, Pageable pageable, String postScript) {

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
        return new Sent(service.attributes, (Pageable) service.doc.getPrintData(), postScript);
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

    @Test
    void a4IsSentOnA4WithEveryPage() throws Exception {
        byte[] pdf = statement(Paper.A4, 90);
        assertTrue(pdfPages(pdf).size() > 1, "more than one page");

        Sent sent = print(pdf, Paper.A4, 1);
        assertEquals(MediaSizeName.ISO_A4, sent.media());
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
    }

    /**
     * طابعة PostScript اللي Java جاية بيها، بتكتب في stream، وبتسجّل المهمة اللي وصلتها قبل ما تكتبها:
     * المستند والطلبات. كل الباقي بيتحوّل لها زي ما هو.
     */
    private static final class RecordingService extends StreamPrintService {

        private final StreamPrintService postScript;
        private Doc doc;
        private PrintRequestAttributeSet attributes;

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
