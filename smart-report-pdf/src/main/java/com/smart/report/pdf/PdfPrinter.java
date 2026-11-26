package com.smart.report.pdf;

import com.smart.report.ReportException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.Orientation;
import org.apache.pdfbox.printing.PDFPageable;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.JobName;
import javax.print.attribute.standard.MediaSize;
import javax.print.attribute.standard.MediaSizeName;
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
     * @param copies      عدد النسخ من المستند كله، بين 1 و{@link #MAX_COPIES}
     * @throws ReportException لو الطابعة مش موجودة على الجهاز، أو رفضت المهمة، أو الملف مش PDF
     */
    public static void print(byte[] pdf, String printerName, Paper paper, int copies) throws ReportException {
        Objects.requireNonNull(pdf, "pdf");
        PrintService printer = find(printerName);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setPrintService(printer);
            job.setPageable(new PDFPageable(document, Orientation.AUTO, false));
            PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
            MediaSizeName media = media(paper);
            if (media != null) {
                attributes.add(media);
            }
            attributes.add(new Copies(Math.clamp(copies, 1, MAX_COPIES)));
            String title = document.getDocumentInformation().getTitle();
            attributes.add(new JobName(title == null || title.isBlank() ? "Report" : title, null));
            job.print(attributes);
        } catch (IOException e) {
            throw new ReportException("The file to print is not a readable PDF: " + e.getMessage(), e);
        } catch (PrinterException e) {
            throw new ReportException("Printing on \"" + printer.getName() + "\" failed: " + e.getMessage(), e);
        }
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
