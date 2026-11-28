package com.smart.report.pdf;

import com.smart.report.ReportException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.Orientation;
import org.apache.pdfbox.printing.PDFPageable;

import javax.print.DocFlavor;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.JobName;
import javax.print.attribute.standard.Media;
import javax.print.attribute.standard.MediaSize;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.PageRanges;
import javax.print.attribute.standard.SheetCollate;
import javax.print.attribute.standard.Sides;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * يبعت PDF جاهزًا لطابعة باسمها، من غير نافذة حفظ ولا نافذة طباعة: البرنامج هو اللي بيختار
 * الطابعة (من {@link #printerNames()}) والمقاس وعدد النسخ.
 *
 * <p>الصفحات بتتبعت بحجمها الفعلي، والطابعة بتتطلب منها ورقة المستند (A4 أو A5)، فاللي بيتطبع هو
 * اللي اترسم. الطباعة بتاخد وقتها؛ نادِها من thread غير thread الواجهة.</p>
 */
public final class PdfPrinter {

    /** أكتر من كده نسخ من مستند واحد غلطة كتابة، مش طلبية طباعة. */
    public static final int MAX_COPIES = 99;

    private PdfPrinter() {
    }

    /** الطابعات المعرّفة على الجهاز، بالاسم اللي {@code print} بياخده — بتتسأل من النظام كل مرة. */
    public static List<String> printerNames() {
        return Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
                .map(PrintService::getName)
                .sorted()
                .toList();
    }

    /** الطابعة الافتراضية في النظام، لو فيه. */
    public static Optional<String> defaultPrinter() {
        PrintService service = PrintServiceLookup.lookupDefaultPrintService();
        return service == null ? Optional.empty() : Optional.of(service.getName());
    }

    /** يبني المستند ويطبعه على ورقته. */
    public static void print(PdfDocument document, String printerName, int copies) throws ReportException {
        Objects.requireNonNull(document, "document");
        print(document.toBytes(), printerName, document.paper(), copies);
    }

    /**
     * يبني مستندات الدفعة ويبعتها للطابعة مهمة واحدة على ورقتها: النسخ بتطلع مجموعات كاملة (الدفعة كلها،
     * وبعدها الدفعة كلها تاني)، مش كل صفحة مكررة ورا بعضها.
     *
     * @throws ReportException لو المستندات مش على نفس الورقة ({@link PdfBatch})، أو الطابعة مش موجودة أو
     *                         رفضت المهمة
     */
    public static void print(PdfBatch batch, String printerName, int copies) throws ReportException {
        Objects.requireNonNull(batch, "batch");
        print(batch.toBytes(), printerName, batch.paper(), copies);
    }

    /** يبني المستند ويطبعه على ورقته بالطلبات دي (نسخ، ومدى صفحات، ووجهين). */
    public static void print(PdfDocument document, String printerName, PrintOptions options) throws ReportException {
        Objects.requireNonNull(document, "document");
        print(document.toBytes(), printerName, document.paper(), options);
    }

    /** يبني الدفعة ويطبعها مهمة واحدة بالطلبات دي؛ مدى الصفحات على صفحات الدفعة كلها. */
    public static void print(PdfBatch batch, String printerName, PrintOptions options) throws ReportException {
        Objects.requireNonNull(batch, "batch");
        print(batch.toBytes(), printerName, batch.paper(), options);
    }

    /**
     * زي {@link #print(byte[], String, Paper, int)} بطلبات المهمة كلها.
     *
     * @throws ReportException لو أول صفحة في المدى بعد آخر صفحة في المستند، أو الطابعة مش موجودة أو رفضت المهمة
     */
    public static void print(byte[] pdf, String printerName, Paper paper, PrintOptions options) throws ReportException {
        Objects.requireNonNull(pdf, "pdf");
        Objects.requireNonNull(options, "options");
        printOn(find(printerName), pdf, paper, options);
    }

    /** يطبع ملف PDF موجودًا. */
    public static void print(Path file, String printerName, Paper paper, int copies) throws ReportException {
        Objects.requireNonNull(file, "file");
        try {
            print(Files.readAllBytes(file), printerName, paper, copies);
        } catch (IOException e) {
            throw new ReportException("Failed to read the PDF to print: " + file, e);
        }
    }

    /**
     * @param printerName اسم الطابعة كما في {@link #printerNames()}؛ {@code null} أو فاضي = الافتراضية
     * @param paper       الورقة المطلوبة من الطابعة؛ {@code null} = الطابعة تختار
     * @param copies      عدد النسخ من المستند كله، بين 1 و{@link #MAX_COPIES}؛ المستند اللي أكتر من صفحة
     *                    بيطلع نسخًا كاملة مرتبة (1، 2، 3، 1، 2، 3)
     * @throws ReportException لو الطابعة مش موجودة على الجهاز، أو رفضت المهمة، أو الملف مش PDF
     */
    public static void print(byte[] pdf, String printerName, Paper paper, int copies) throws ReportException {
        Objects.requireNonNull(pdf, "pdf");
        printOn(find(printerName), pdf, paper, copies);
    }

    /** يطبع على خدمة طباعة بعينها؛ الاختبارات بتديها خدمة بتكتب في stream بدل طابعة من النظام. */
    static void printOn(PrintService printer, byte[] pdf, Paper paper, int copies) throws ReportException {
        printOn(printer, pdf, paper, PrintOptions.copies(Math.clamp(copies, 1, MAX_COPIES)));
    }

    static void printOn(PrintService printer, byte[] pdf, Paper paper, PrintOptions options) throws ReportException {
        if (paper != null && paper.roll() && options.copies() > 1) {
            // طابعة الإيصالات بتقص في آخر المهمة: نسختين في مهمة واحدة بيطلعوا شريط واحد من غير قص. كل نسخة مهمة.
            PrintOptions one = options.withCopies(1);
            for (int i = 0; i < options.copies(); i++) {
                printOn(printer, pdf, paper, one);
            }
            return;
        }
        int copies = options.copies();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setPrintService(printer);
            job.setPageable(new PDFPageable(document, Orientation.AUTO, false));
            PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
            MediaSizeName media = media(paper);
            if (media != null) {
                attributes.add(media);
            }
            int count = Math.clamp(copies, 1, MAX_COPIES);
            attributes.add(new Copies(count));
            if (count > 1 && document.getNumberOfPages() > 1) {
                // نسخ كاملة مرتبة: من غير الطلب ده الطابعة ممكن تكرر كل صفحة ورا بعضها (1، 1، 2، 2)، والدفعة
                // تطلع كشوفها متلخبطة. لو الدرايفر ما بيرتبش، Java بتبعت المستند كله كذا مرة في نفس المهمة.
                attributes.add(SheetCollate.COLLATED);
            }
            if (!options.allPagesChosen()) {
                int pages = document.getNumberOfPages();
                if (options.firstPage() > pages) {
                    throw new ReportException("The document has " + pages + " page" + (pages == 1 ? "" : "s")
                            + "; there is no page " + options.firstPage() + " to print.");
                }
                attributes.add(new PageRanges(options.firstPage(), Math.min(options.lastPage(), pages)));
            }
            if (options.duplex() != null) {
                attributes.add(switch (options.duplex()) {
                    case ONE_SIDED -> Sides.ONE_SIDED;
                    case LONG_EDGE -> Sides.TWO_SIDED_LONG_EDGE;
                    case SHORT_EDGE -> Sides.TWO_SIDED_SHORT_EDGE;
                });
            }
            String title = document.getDocumentInformation().getTitle();
            attributes.add(new JobName(title == null || title.isBlank() ? "Report" : title, null));
            job.print(attributes);
        } catch (IOException e) {
            throw new ReportException("The file to print is not a readable PDF: " + e.getMessage(), e);
        } catch (PrinterException e) {
            throw new ReportException("Printing on \"" + printer.getName() + "\" failed: " + e.getMessage(), e);
        }
    }

    /** سن درج النقدية في الطابعة: أغلب الأدراج على السن 2. */
    public enum DrawerPin {
        PIN_2, PIN_5
    }

    /** يفتح درج النقدية المتوصل بالطابعة: السن 2، ونبضة 100 ملّي ثانية. */
    public static void openCashDrawer(String printerName) throws ReportException {
        openCashDrawer(printerName, DrawerPin.PIN_2, 100);
    }

    /**
     * يفتح درج النقدية المتوصل بطابعة الإيصالات: أمر ESC/POS {@code ESC p} كبايتات خام، من غير PDF.
     *
     * @param pin     السن اللي الدرج عليه
     * @param pulseMs طول النبضة بالملّي ثانية، من 2 لـ 510 (أغلب الأدراج 50 لـ 200)
     * @throws ReportException لو الطابعة مش موجودة، أو الدرايفر بتاعها ما بياخدش بايتات خام (طابعة PDF أو طابعة
     *                         مكتب)، أو رفضت المهمة
     */
    public static void openCashDrawer(String printerName, DrawerPin pin, int pulseMs) throws ReportException {
        sendRaw(find(printerName), drawerPulse(pin, pulseMs));
    }

    /** {@code ESC p m t1 t2}: السن، ومدة النبضة ومدة الراحة بوحدات 2 ملّي ثانية. */
    static byte[] drawerPulse(DrawerPin pin, int pulseMs) {
        Objects.requireNonNull(pin, "pin");
        if (pulseMs < 2 || pulseMs > 510) {
            throw new IllegalArgumentException("The drawer pulse is 2 to 510 ms: " + pulseMs);
        }
        int on = Math.round(pulseMs / 2f);
        // الراحة بعد النبضة أطول منها، وعلى الأقل 100 ملّي ثانية، عشان الملف ما يسخنش.
        int off = Math.min(255, Math.max(50, on * 2));
        return new byte[]{0x1B, 0x70, (byte) (pin == DrawerPin.PIN_2 ? 0 : 1), (byte) on, (byte) off};
    }

    static void sendRaw(PrintService printer, byte[] bytes) throws ReportException {
        DocFlavor raw = DocFlavor.BYTE_ARRAY.AUTOSENSE;
        if (!printer.isDocFlavorSupported(raw)) {
            throw new ReportException("The driver of \"" + printer.getName() + "\" takes no raw bytes, so the cash"
                    + " drawer cannot be opened through it. Use the receipt printer's own driver (Generic / Text Only"
                    + " on Windows, raw on CUPS).");
        }
        try {
            printer.createPrintJob().print(new SimpleDoc(bytes, raw, null), new HashPrintRequestAttributeSet());
        } catch (PrintException e) {
            throw new ReportException("Sending to \"" + printer.getName() + "\" failed: " + e.getMessage(), e);
        }
    }

    /**
     * هل الطابعة بتقول إنها بتطبع على الورقة دي؟ عشان شاشة الإعدادات تنبّه قبل أول طباعة. اللفة الحرارية {@code true}
     * (ما بيتطلبش لها مقاس)، وكمان لو الطابعة ما بتعلنش أي مقاسات (ما نقدرش نعرف).
     *
     * @throws ReportException لو الطابعة مش موجودة
     */
    public static boolean declaresPaper(String printerName, Paper paper) throws ReportException {
        Objects.requireNonNull(paper, "paper");
        return declares(find(printerName), paper);
    }

    static boolean declares(PrintService printer, Paper paper) {
        MediaSizeName wanted = media(paper);
        if (wanted == null) {
            return true;
        }
        Object values = printer.getSupportedAttributeValues(Media.class, null, null);
        if (!(values instanceof Media[] media) || media.length == 0) {
            return true;
        }
        for (Media one : media) {
            if (wanted.equals(one)) {
                return true;
            }
        }
        return false;
    }

    private static PrintService find(String printerName) throws ReportException {
        if (printerName == null || printerName.isBlank()) {
            PrintService service = PrintServiceLookup.lookupDefaultPrintService();
            if (service == null) {
                throw new ReportException("No default printer is set on this computer.");
            }
            return service;
        }
        for (PrintService candidate : PrintServiceLookup.lookupPrintServices(null, null)) {
            if (candidate.getName().equals(printerName)) {
                return candidate;
            }
        }
        throw new ReportException("The printer \"" + printerName + "\" is not available on this computer."
                + " Available printers: " + printerNames());
    }

    /** اسم الورقة عند الطابعة: A4 و A5 باسميهما، وأي مقاس تاني بأقرب مقاس تعرفه Java. */
    static MediaSizeName media(Paper paper) {
        // اللفة الحرارية مالهاش مقاس في Java: الطابعة بتستخدم لفتها، والصفحة بتتبعت بطولها الفعلي.
        if (paper == null || paper.roll()) {
            return null;
        }
        Paper upright = paper.portrait();
        if (near(upright, Paper.A4)) {
            return MediaSizeName.ISO_A4;
        }
        if (near(upright, Paper.A5)) {
            return MediaSizeName.ISO_A5;
        }
        return MediaSize.findMedia(upright.widthMm(), upright.heightMm(), MediaSize.MM);
    }

    private static boolean near(Paper a, Paper b) {
        return Math.abs(a.width() - b.width()) < 1f && Math.abs(a.height() - b.height()) < 1f;
    }
}
