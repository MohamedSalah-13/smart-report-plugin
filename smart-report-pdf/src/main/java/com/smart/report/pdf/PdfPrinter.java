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
 * Sends a finished PDF to a printer by name, with no save dialog and no print dialog: the program picks the
 * printer (from {@link #printerNames()}), the paper size and the number of copies.
 *
 * <p>Pages are sent at their actual size, and the printer is asked for the document's paper (A4 or A5), so what
 * prints is what was drawn. Printing takes time; call it from a thread other than the UI thread.</p>
 */
public final class PdfPrinter {

    /** More copies than this of one document is a typing mistake, not a print order. */
    public static final int MAX_COPIES = 99;

    private PdfPrinter() {
    }

    /** The printers installed on this computer, by the name {@code print} takes. The system is asked every time. */
    public static List<String> printerNames() {
        return Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
                .map(PrintService::getName)
                .sorted()
                .toList();
    }

    /** The system's default printer, if there is one. */
    public static Optional<String> defaultPrinter() {
        PrintService service = PrintServiceLookup.lookupDefaultPrintService();
        return service == null ? Optional.empty() : Optional.of(service.getName());
    }

    /** Builds the document and prints it on its paper. */
    public static void print(PdfDocument document, String printerName, int copies) throws ReportException {
        Objects.requireNonNull(document, "document");
        print(document.toBytes(), printerName, document.paper(), copies);
    }

    /**
     * Builds the documents of the batch and sends them to the printer as one job on their paper. Copies come out
     * as complete sets (the whole batch, then the whole batch again), not each page repeated in a row.
     *
     * @throws ReportException if the documents are not on the same paper ({@link PdfBatch}), or the printer is not
     *                         found or rejects the job
     */
    public static void print(PdfBatch batch, String printerName, int copies) throws ReportException {
        Objects.requireNonNull(batch, "batch");
        print(batch.toBytes(), printerName, batch.paper(), copies);
    }

    /** Builds the document and prints it on its paper with these options (copies, page range, duplex). */
    public static void print(PdfDocument document, String printerName, PrintOptions options) throws ReportException {
        Objects.requireNonNull(document, "document");
        print(document.toBytes(), printerName, document.paper(), options);
    }

    /** Builds the batch and prints it as one job with these options; the page range counts the whole batch's pages. */
    public static void print(PdfBatch batch, String printerName, PrintOptions options) throws ReportException {
        Objects.requireNonNull(batch, "batch");
        print(batch.toBytes(), printerName, batch.paper(), options);
    }

    /**
     * Like {@link #print(byte[], String, Paper, int)}, with all the job options.
     *
     * @throws ReportException if the first page of the range is after the document's last page, or the printer is
     *                         not found or rejects the job
     */
    public static void print(byte[] pdf, String printerName, Paper paper, PrintOptions options) throws ReportException {
        Objects.requireNonNull(pdf, "pdf");
        Objects.requireNonNull(options, "options");
        printOn(find(printerName), pdf, paper, options);
    }

    /** Prints an existing PDF file. */
    public static void print(Path file, String printerName, Paper paper, int copies) throws ReportException {
        Objects.requireNonNull(file, "file");
        try {
            print(Files.readAllBytes(file), printerName, paper, copies);
        } catch (IOException e) {
            throw new ReportException("Failed to read the PDF to print: " + file, e);
        }
    }

    /**
     * @param printerName the printer name as in {@link #printerNames()}; {@code null} or blank = the default printer
     * @param paper       the paper to ask the printer for; {@code null} = the printer chooses
     * @param copies      the number of copies of the whole document, between 1 and {@link #MAX_COPIES}; a document
     *                    of more than one page comes out as complete, collated copies (1, 2, 3, 1, 2, 3)
     * @throws ReportException if the printer is not on this computer, rejects the job, or the file is not a PDF
     */
    public static void print(byte[] pdf, String printerName, Paper paper, int copies) throws ReportException {
        Objects.requireNonNull(pdf, "pdf");
        printOn(find(printerName), pdf, paper, copies);
    }

    /** Prints on a given print service; tests pass a service that writes to a stream instead of a system printer. */
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

    /** The cash drawer pin on the printer: most drawers are on pin 2. */
    public enum DrawerPin {
        PIN_2, PIN_5
    }

    /** Opens the cash drawer connected to the printer: pin 2, with a 100 ms pulse. */
    public static void openCashDrawer(String printerName) throws ReportException {
        openCashDrawer(printerName, DrawerPin.PIN_2, 100);
    }

    /**
     * Opens the cash drawer connected to the receipt printer: sends the ESC/POS command {@code ESC p} as raw bytes,
     * without a PDF.
     *
     * @param pin     the pin the drawer is on
     * @param pulseMs the pulse length in milliseconds, from 2 to 510 (most drawers take 50 to 200)
     * @throws ReportException if the printer is not found, its driver does not take raw bytes (a PDF printer or an
     *                         office printer), or it rejects the job
     */
    public static void openCashDrawer(String printerName, DrawerPin pin, int pulseMs) throws ReportException {
        sendRaw(find(printerName), drawerPulse(pin, pulseMs));
    }

    /** {@code ESC p m t1 t2}: the pin, then the pulse time and the rest time in units of 2 ms. */
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
     * Does the printer say it prints on this paper? Lets a settings screen warn before the first print. A thermal
     * roll gives {@code true} (no size is requested for it), and so does a printer that declares no sizes at all
     * (there is no way to know).
     *
     * @throws ReportException if the printer is not found
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
        if (!(values instanceof Media[] media)) {
            return true;
        }
        // القائمة ممكن تبقى أدراج بس (MediaTray) من غير مقاسات: ساعتها ما نقدرش نعرف، زي ما تكون فاضية.
        boolean sizesListed = false;
        for (Media one : media) {
            if (one instanceof MediaSizeName) {
                sizesListed = true;
                if (wanted.equals(one)) {
                    return true;
                }
            }
        }
        return !sizesListed;
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

    /** The paper name for the printer: A4 and A5 by their own names, any other size by the nearest size Java knows. */
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
