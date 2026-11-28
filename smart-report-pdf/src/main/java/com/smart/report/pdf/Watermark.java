package com.smart.report.pdf;

import com.smart.report.ReportException;
import org.apache.pdfbox.util.Matrix;

import java.io.IOException;

import static com.smart.report.pdf.Typesetter.BASELINE;
import static com.smart.report.pdf.Typesetter.LEADING;

/**
 * العلامة المائية ({@link PdfDocument#watermark(String, int)}): كلمة كبيرة بلون فاتح في كل صفحة، تحت المحتوى.
 *
 * <ul>
 *     <li>بتترسم أول حاجة في الصفحة ({@link PageWriter}، أول ما الصفحة تتفتح)، فكل اللي بعدها فوقها وما بتغطيش
 *         نص. ومش محتوى: ما بتعلّمش الصفحة ({@link Canvas#marked()})، فالصفحة اللي فيها العلامة بس فاضية.</li>
 *     <li>على الورق العادي مايلة على قطر الصفحة في نصها. على اللفة الحرارية الصفحة ضيقة وطويلة وقطرها قريب
 *         من الرأسي، فالعلامة بالعرض ومتكررة على طول الإيصال: نسخة لكل حوالي مرة ونص عرض اللفة، والإيصال
 *         القصير نسخة واحدة في نصه.</li>
 *     <li>الحجم على قد الكلمة والورقة: الكلمة كلها (بارتفاع سطرها) جوه هوامش الصفحة، ومن غير ما تكبر عن
 *         {@link #MAX_SHARE} من أقصر ضلع.</li>
 *     <li>اللون {@link #TINT} من اللون المطلوب والباقي أبيض، من غير شفافية: على الورق الأبيض زي اللون
 *         الشفاف بالظبط، وبيتطبع زي ما هو على أي طابعة، ومن غير حبر يُذكر في {@link PdfStyle#inkSaver()}.</li>
 * </ul>
 */
final class Watermark {

    /** اللون لو ما اتحدّدش: أسود، يعني رمادي فاتح بعد {@link #TINT}. */
    static final int DEFAULT_COLOR = 0x000000;

    /** نصيب اللون المطلوب في لون العلامة، والباقي أبيض. */
    static final float TINT = 0.15f;

    /** أكبر حجم للخط نسبةً لأقصر ضلع في الورقة: «COPY» على A4 حوالي 130 نقطة. */
    static final float MAX_SHARE = 0.22f;

    /** على اللفة: نسخة لكل المسافة دي مضروبة في عرض اللفة. */
    static final float ROLL_REPEAT = 1.5f;

    private static final float REFERENCE_SIZE = 100f;

    private final Typesetter.Line line;
    private final float size;
    private final int color;
    private final double angle;
    private final float width;
    private final float height;

    private Watermark(Typesetter.Line line, float size, int color, double angle, float width, float height) {
        this.line = line;
        this.size = size;
        this.color = color;
        this.angle = angle;
        this.width = width;
        this.height = height;
    }

    /**
     * العلامة مقاسة على {@code paper}، أو {@code null} لو النص فاضي. السطور الجديدة والمسافات الكتير مسافة
     * واحدة: العلامة سطر واحد.
     */
    static Watermark of(String raw, int color, Typesetter text, Paper paper, float margin)
            throws IOException, ReportException {
        String word = Typesetter.clean(raw).replace('\n', ' ').strip().replaceAll(" {2,}", " ");
        if (word.isEmpty()) {
            return null;
        }
        float perPoint = text.line(word, word, false, REFERENCE_SIZE).width() / REFERENCE_SIZE;
        if (!(perPoint > 0)) {
            return null;
        }
        float roomWidth = paper.width() - 2 * margin;
        float roomHeight = paper.height() - 2 * margin;
        double angle = paper.roll() ? 0 : Math.atan2(paper.height(), paper.width());
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        // السطر مستطيل عرضه perPoint × الحجم وارتفاعه LEADING × الحجم، ومايل بالزاوية: الصندوق حواليه جوه الهوامش.
        double fit = Math.min(roomWidth / (perPoint * cos + LEADING * sin),
                paper.roll() ? Double.MAX_VALUE : roomHeight / (perPoint * sin + LEADING * cos));
        if (paper.roll()) {
            // نسخة واحدة على الأقل بارتفاع سطرها جوه الورقة.
            fit = Math.min(fit, roomHeight / LEADING);
        }
        float size = (float) Math.min(fit, MAX_SHARE * Math.min(paper.width(), paper.height()));
        if (!(size > 0)) {
            return null;
        }
        return new Watermark(text.line(word, word, false, size), size, tint(color), angle,
                paper.width(), paper.height());
    }

    /** {@link #TINT} من {@code rgb} والباقي أبيض. */
    static int tint(int rgb) {
        int red = mix((rgb >> 16) & 0xFF);
        int green = mix((rgb >> 8) & 0xFF);
        int blue = mix(rgb & 0xFF);
        return red << 16 | green << 8 | blue;
    }

    private static int mix(int channel) {
        return Math.round(255 - (255 - channel) * TINT);
    }

    float size() {
        return size;
    }

    /** يرسم العلامة على الصفحة اللي لسه متفتحة، قبل أي حاجة فيها. */
    void draw(Canvas canvas) throws IOException {
        if (angle != 0) {
            drawAt(canvas, width / 2, height / 2);
            return;
        }
        int copies = Math.max(1, (int) (height / (ROLL_REPEAT * width)));
        for (int k = 0; k < copies; k++) {
            drawAt(canvas, width / 2, height - height * (k + 0.5f) / copies);
        }
    }

    /** السطر ونصه في {@code (x, y)}: نص عرضه، ونص ارتفاع سطره. */
    private void drawAt(Canvas canvas, float x, float y) throws IOException {
        // من نص السطر لبدايته على خط الكتابة، في اتجاه السطر.
        double along = -line.width() / 2.0;
        double across = -(BASELINE - LEADING / 2) * size;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        float startX = (float) (x + along * cos - across * sin);
        float startY = (float) (y + along * sin + across * cos);
        canvas.stamp(line, size, color, Matrix.getRotateInstance(angle, startX, startY));
    }
}
