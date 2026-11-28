package com.smart.report.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.util.Matrix;

import java.io.IOException;

/**
 * الرسم على الصفحة اللي {@link PageWriter} فاتحها دلوقتي. كل علامة على الورقة (حرف، خط، خلفية، رمز، صورة)
 * بتعدّي من هنا، فبيتعرف إن الصفحة اترسم فيها حاجة ({@link #marked()}) من الرسم نفسه، من غير ما حد يعرف نوع
 * الجزء اللي رسم.
 */
final class Canvas {

    /** الجهة الفعلية على الورقة. */
    enum Side { LEFT, CENTER, RIGHT }

    private final PDDocument pdf;
    private PDPageContentStream stream;
    private boolean marked;

    Canvas(PDDocument pdf) {
        this.pdf = pdf;
    }

    PDDocument pdf() {
        return pdf;
    }

    /** يبدأ صفحة جديدة لسه ما اترسمش فيها حاجة. */
    void start(PDPage page) throws IOException {
        close();
        stream = new PDPageContentStream(pdf, page);
    }

    /** يكمل على صفحة فيها رسم: بعد آخر حاجة اترسمت فيها. */
    void resume(PDPage page) throws IOException {
        close();
        stream = new PDPageContentStream(pdf, page, PDPageContentStream.AppendMode.APPEND, true, true);
    }

    void close() throws IOException {
        if (stream != null) {
            stream.close();
            stream = null;
        }
    }

    /** اترسمت علامة على الصفحة من آخر مرة اتقال إنها فاضية. */
    boolean marked() {
        return marked;
    }

    void marked(boolean value) {
        marked = value;
    }

    /** سطر في صندوق عرضه {@code boxWidth}، على جهته منه، وخط كتابته عند {@code baseline}. */
    void text(Typesetter.Line line, float size, int color, float boxLeft, float boxWidth, Side side, float baseline)
            throws IOException {
        float x = switch (side) {
            case LEFT -> boxLeft;
            case CENTER -> boxLeft + (boxWidth - line.width()) / 2;
            case RIGHT -> boxLeft + boxWidth - line.width();
        };
        for (FontSet.Run run : line.runs()) {
            stream.beginText();
            stream.setFont(run.face().font(), size);
            fillColor(color);
            if (run.fakeBold()) {
                // تسميك مصطنع: الحرف بيتملى ويتحدد بنفس لونه.
                stream.setRenderingMode(RenderingMode.FILL_STROKE);
                strokeColor(color);
                stream.setLineWidth(size * 0.03f);
            }
            stream.newLineAtOffset(x, baseline);
            Object[] positioned = run.positioned();
            if (positioned == null) {
                stream.showText(run.text());
            } else {
                stream.showTextWithPositioning(positioned);
            }
            if (run.fakeBold()) {
                stream.setRenderingMode(RenderingMode.FILL);
            }
            stream.endText();
            x += run.face().font().getStringWidth(run.text()) / 1000f * size;
        }
    }

    /**
     * سطر العلامة المائية ({@link Watermark}): بيبدأ عند {@code matrix} (مكان بداية خط الكتابة وميله)،
     * ومقاطعه ورا بعض في نفس الاتجاه. <b>مش علامة على الصفحة</b>: ما بيغيرش {@link #marked()}، فالصفحة اللي
     * فيها العلامة بس فاضية. اللون والمصفوفة جوه {@code q ... Q}، فاللي بعده بيبدأ من نفس حالة الرسم.
     */
    void stamp(Typesetter.Line line, float size, int rgb, Matrix matrix) throws IOException {
        stream.saveGraphicsState();
        stream.setNonStrokingColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
        stream.beginText();
        stream.setTextMatrix(matrix);
        for (FontSet.Run run : line.runs()) {
            stream.setFont(run.face().font(), size);
            Object[] positioned = run.positioned();
            if (positioned == null) {
                stream.showText(run.text());
            } else {
                stream.showTextWithPositioning(positioned);
            }
        }
        stream.endText();
        stream.restoreGraphicsState();
    }

    void rule(float x1, float x2, float atY, int color, float lineWidth) throws IOException {
        strokeColor(color);
        stream.setLineWidth(lineWidth);
        stream.moveTo(x1, atY);
        stream.lineTo(x2, atY);
        stream.stroke();
    }

    void image(PDImageXObject image, float x, float y, float width, float height) throws IOException {
        marked = true;
        stream.drawImage(image, x, y, width, height);
    }

    void fillColor(int rgb) throws IOException {
        marked = true;
        stream.setNonStrokingColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
    }

    void strokeColor(int rgb) throws IOException {
        marked = true;
        stream.setStrokingColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
    }

    void lineWidth(float width) throws IOException {
        stream.setLineWidth(width);
    }

    /** مستطيل في المسار اللي بيتملى أو بيتحدد بعدين مرة واحدة. */
    void rect(float x, float y, float width, float height) throws IOException {
        stream.addRect(x, y, width, height);
    }

    void fill() throws IOException {
        stream.fill();
    }

    void stroke() throws IOException {
        stream.stroke();
    }
}
