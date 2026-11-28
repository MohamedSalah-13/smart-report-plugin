package com.smart.report.pdf;

import com.smart.report.ReportException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Many documents in one PDF file and one print job: each driver's statement for the month, or all of the day's
 * invoices.
 *
 * <pre>{@code
 * List<PdfDocument> statements = drivers.stream()
 *         .map(driver -> StatementPdf.document(driver, Paper.A4))
 *         .toList();
 * PdfBatch batch = PdfBatch.of(statements);
 *
 * batch.save(Path.of("statements-2026-11.pdf"));   // one file
 * PdfPrinter.print(batch, printerName, 2);          // one job: all the statements, then all of them again
 * }</pre>
 *
 * <p><b>Each document keeps its pages as if printed alone:</b> its letterhead, its page footers and its page
 * numbering («صفحة 1 من 3», "page 1 of 3", starts over in each document). A new document always starts on a new
 * page, and the documents keep their order in the batch.</p>
 *
 * <p><b>Paper:</b> a print job asks the printer for one paper, so all documents must print on the same paper: the
 * same size in portrait or landscape (A4 with landscape A4 is fine; the landscape page is rotated onto the sheet),
 * or the same thermal roll width. A4 with A5, paper with a roll, or an 80 roll with a 58 roll fails with a
 * {@link ReportException} when the batch is written: make one batch per paper. On a roll, each document is its own
 * receipt, one page as long as its content, so a printer that cuts at the end of every page cuts between the
 * receipts.</p>
 *
 * <p>The batch holds the documents themselves, not copies: a change to a document after {@link #of} shows up in
 * anything written after it. Like {@link PdfDocument}, it is not thread-safe.</p>
 */
public final class PdfBatch {

    private final List<PdfDocument> documents;

    private PdfBatch(List<PdfDocument> documents) {
        this.documents = documents;
    }

    /** @throws IllegalArgumentException if there are no documents */
    public static PdfBatch of(PdfDocument... documents) {
        Objects.requireNonNull(documents, "documents");
        return of(Arrays.asList(documents));
    }

    /**
     * The documents, in the order of the collection.
     *
     * @throws IllegalArgumentException if there are no documents
     */
    public static PdfBatch of(Collection<PdfDocument> documents) {
        Objects.requireNonNull(documents, "documents");
        List<PdfDocument> kept = new ArrayList<>(documents.size());
        for (PdfDocument document : documents) {
            kept.add(Objects.requireNonNull(document, "document " + (kept.size() + 1)));
        }
        if (kept.isEmpty()) {
            throw new IllegalArgumentException("A batch needs at least one document.");
        }
        return new PdfBatch(List.copyOf(kept));
    }

    /** The documents, in order. */
    public List<PdfDocument> documents() {
        return documents;
    }

    /** The first document's paper: the batch prints on it, and the others must print on the same paper. */
    public Paper paper() {
        return documents.getFirst().paper();
    }

    /** The batch as PDF bytes. */
    public byte[] toBytes() throws ReportException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeTo(out);
        return out.toByteArray();
    }

    /**
     * Writes the batch to {@code out} and does not close it.
     *
     * @throws ReportException if the documents are not on the same paper, or one of them could not be built (the
     *                         message gives its number in the batch, and the original error is the cause)
     */
    public void writeTo(OutputStream out) throws ReportException {
        Objects.requireNonNull(out, "out");
        checkOneSheet();
        // كل مستند بيتبني لوحده، فترقيمه وترويسته وطول إيصاله زي ما هم؛ وبعدين صفحاته بتتنسخ ورا بعض.
        // المستندات بتفضل مفتوحة لحد ما الملف يتكتب: الصفحات المنسوخة ممكن تقرا منها.
        List<PDDocument> parts = new ArrayList<>(documents.size());
        try (PDDocument merged = new PDDocument()) {
            PDFMergerUtility merger = new PDFMergerUtility();
            for (int i = 0; i < documents.size(); i++) {
                PDDocument part = Loader.loadPDF(build(i));
                parts.add(part);
                merger.appendDocument(merged, part);
            }
            // اسم مهمة الطباعة من عنوان الملف: عنوان أول مستند ليه عنوان.
            merged.getDocumentInformation().setCreator("smart-report-pdf");
            merged.getDocumentInformation().setTitle(parts.stream()
                    .map(part -> part.getDocumentInformation().getTitle())
                    .filter(title -> title != null && !title.isBlank())
                    .findFirst().orElse(null));
            merged.save(out);
        } catch (IOException e) {
            throw new ReportException("Failed to write the PDF batch: " + e.getMessage(), e);
        } finally {
            for (PDDocument part : parts) {
                try {
                    part.close();
                } catch (IOException ignored) {
                    // مستند في الذاكرة: مفيش ملف يفضل مفتوح.
                }
            }
        }
    }

    /**
     * Document number {@code index} as bytes. The error gives its number in the batch (from 1) and carries the
     * original error: in a batch of 40 statements, "the logo is not an image" without a number does not say which
     * statement. The type stays the same as the document alone would throw: {@link ReportException} or
     * {@link IllegalArgumentException} (margins that leave no room).
     */
    private byte[] build(int index) throws ReportException {
        String which = "Document " + (index + 1) + " of " + documents.size() + " in the batch";
        try {
            return documents.get(index).toBytes();
        } catch (ReportException e) {
            throw new ReportException(which + " could not be built: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(which + ": " + e.getMessage(), e);
        }
    }

    /** Saves the batch to a file, and creates any missing folders on its path. */
    public void save(Path file) throws ReportException {
        Objects.requireNonNull(file, "file");
        byte[] bytes = toBytes();
        try {
            if (file.toAbsolutePath().getParent() != null) {
                Files.createDirectories(file.toAbsolutePath().getParent());
            }
            Files.write(file, bytes);
        } catch (IOException e) {
            throw new ReportException("Failed to write the PDF batch to " + file, e);
        }
    }

    /** All documents on the first document's paper: the printer is asked for one paper for the whole job. */
    private void checkOneSheet() throws ReportException {
        Paper first = paper();
        for (int i = 1; i < documents.size(); i++) {
            Paper other = documents.get(i).paper();
            if (!sameSheet(first, other)) {
                throw new ReportException("Every document in a batch must print on the same paper, but document "
                        + (i + 1) + " is on " + other.name() + " and the first is on " + first.name()
                        + ". A print job asks the printer for one paper: make one batch per paper.");
            }
        }
    }

    /** The same paper in the printer: the same size in portrait or landscape, or two rolls of the same width. */
    static boolean sameSheet(Paper a, Paper b) {
        if (a.roll() || b.roll()) {
            return a.roll() && b.roll() && Math.abs(a.width() - b.width()) < 1f;
        }
        Paper first = a.portrait();
        Paper second = b.portrait();
        return Math.abs(first.width() - second.width()) < 1f && Math.abs(first.height() - second.height()) < 1f;
    }
}
