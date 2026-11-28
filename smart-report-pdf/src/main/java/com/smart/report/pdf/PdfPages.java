package com.smart.report.pdf;

import com.smart.report.ReportException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Objects;

/**
 * A written PDF, drawn page by page as an image. This is what the preview window shows.
 *
 * <p><b>Only the requested page is drawn.</b> An A4 page drawn for the screen is a few megapixels, and a report
 * can reach a hundred pages. Drawing them all up front would reserve hundreds of megabytes for a window that
 * most people close after the first page.</p>
 *
 * <p>Drawing uses PDFBox, the same library {@link PdfPrinter} uses to send the pages to the printer, so the
 * preview shows what the printer will receive. A PDFBox document is not safe to draw from more than one thread,
 * so the methods are synchronized: the window draws on a background thread, and a slow page delays the next
 * request instead of breaking it.</p>
 */
public final class PdfPages implements AutoCloseable {

    /** The maximum number of pixels per point. Above this, the image uses memory with no visible difference. */
    public static final float MAX_SCALE = 4f;
    private static final float MIN_SCALE = 0.1f;

    private final PDDocument document;
    private final PDFRenderer renderer;

    private PdfPages(PDDocument document) {
        this.document = document;
        this.renderer = new PDFRenderer(document);
    }

    public static PdfPages open(byte[] pdf) throws ReportException {
        Objects.requireNonNull(pdf, "pdf");
        try {
            return new PdfPages(Loader.loadPDF(pdf));
        } catch (IOException e) {
            throw new ReportException("The file to preview is not a readable PDF: " + e.getMessage(), e);
        }
    }

    public synchronized int count() {
        return document.getNumberOfPages();
    }

    /** The page width in points, as drawn. A page turned by its rotation is wide. */
    public synchronized float width(int index) {
        PDPage page = document.getPage(index);
        PDRectangle box = page.getCropBox();
        return turned(page) ? box.getHeight() : box.getWidth();
    }

    /** The page height in points, as drawn. */
    public synchronized float height(int index) {
        PDPage page = document.getPage(index);
        PDRectangle box = page.getCropBox();
        return turned(page) ? box.getWidth() : box.getHeight();
    }

    /**
     * The pixels of one page.
     *
     * @param scale pixels per point (1 = 72 dots per inch), between one tenth and {@link #MAX_SCALE}
     */
    public synchronized BufferedImage render(int index, float scale) throws ReportException {
        try {
            return renderer.renderImage(index, Math.clamp(scale, MIN_SCALE, MAX_SCALE));
        } catch (IOException | RuntimeException e) {
            throw new ReportException("Page " + (index + 1) + " could not be drawn: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void close() throws ReportException {
        try {
            document.close();
        } catch (IOException e) {
            throw new ReportException("The previewed PDF could not be closed.", e);
        }
    }

    private static boolean turned(PDPage page) {
        int rotation = ((page.getRotation() % 360) + 360) % 360;
        return rotation == 90 || rotation == 270;
    }
}
