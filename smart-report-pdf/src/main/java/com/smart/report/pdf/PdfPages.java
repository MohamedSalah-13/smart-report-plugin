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
 * PDF مكتوب، بيترسم صفحة صفحة كصورة — اللي نافذة المعاينة بتعرضه.
 *
 * <p><b>الصفحة المطلوبة بس هي اللي بتترسم.</b> صفحة A4 مرسومة للشاشة كام ميجا من البكسلات، والتقرير
 * ممكن يوصل مئة صفحة؛ رسمها كلها مقدمًا يحجز مئات الميجات لنافذة أغلب الناس بتقفلها بعد أول صفحة.</p>
 *
 * <p>الرسم بـ PDFBox، نفس اللي {@link PdfPrinter} بيبعت بيه الصفحات للطابعة، فالمعاينة بتعرض اللي
 * الطابعة هتستلمه. مستند PDFBox مش آمن للرسم من أكتر من thread، فالطرق synchronized: النافذة بترسم
 * على thread جانبي، والصفحة البطيئة بتأخّر الطلب اللي بعدها بدل ما تبوّظه.</p>
 */
public final class PdfPages implements AutoCloseable {

    /** أقصى عدد بكسلات لكل نقطة: أكبر من كده الصورة بتاكل ذاكرة من غير ما العين تشوف فرق. */
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

    /** عرض الصفحة بالنقطة كما بتترسم — الصفحة المقلوبة بالدوران عريضة. */
    public synchronized float width(int index) {
        PDPage page = document.getPage(index);
        PDRectangle box = page.getCropBox();
        return turned(page) ? box.getHeight() : box.getWidth();
    }

    /** ارتفاع الصفحة بالنقطة كما بتترسم. */
    public synchronized float height(int index) {
        PDPage page = document.getPage(index);
        PDRectangle box = page.getCropBox();
        return turned(page) ? box.getWidth() : box.getHeight();
    }

    /**
     * بكسلات صفحة واحدة.
     *
     * @param scale بكسلات لكل نقطة (1 = 72 نقطة في البوصة)، بين عُشر و{@link #MAX_SCALE}
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
