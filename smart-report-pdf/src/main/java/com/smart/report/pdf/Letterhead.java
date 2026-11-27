package com.smart.report.pdf;

import com.smart.report.ReportException;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.IOException;
import java.util.List;

import static com.smart.report.pdf.Typesetter.BASELINE;
import static com.smart.report.pdf.Typesetter.LEADING;

/**
 * الترويسة في أعلى الصفحة: الشعار في الجهة اللي الصفحة بتبدأ منها، واسم الجهة وسطورها في المنتصف، وتحتهم خط.
 * بتترسم من غير قواعد الصفحات: هي أول حاجة في الصفحة، ومفيش فتح صفحات من جوّاها.
 */
final class Letterhead {

    private static final float LOGO_HEIGHT = 44f;

    private final PdfDocument document;
    private final Typesetter text;
    private final PdfStyle style;
    private final boolean rtl;
    private final float left;
    private final float right;
    private final float width;

    Letterhead(PdfDocument document, Typesetter text, PdfStyle style, float left, float right, float width) {
        this.document = document;
        this.text = text;
        this.style = style;
        this.rtl = document.rightToLeft();
        this.left = left;
        this.right = right;
        this.width = width;
    }

    /** يرسم الترويسة وأعلاها عند {@code top}؛ بيرجّع أول مكان للمحتوى تحتها. */
    float draw(Canvas canvas, float top) throws IOException, ReportException {
        String name = document.letterheadName();
        List<String> lines = document.letterheadLines();
        byte[] logo = document.logoBytes();
        if (name.isBlank() && lines.isEmpty() && logo == null) {
            return top;
        }
        float y = top;
        float logoBottom = y;
        if (logo != null) {
            PDImageXObject image;
            try {
                image = PDImageXObject.createFromByteArray(canvas.pdf(), logo, "logo");
            } catch (IllegalArgumentException e) {
                throw new ReportException("The logo is not an image PDFBox can read (PNG or JPEG expected).", e);
            }
            float scale = Math.min(LOGO_HEIGHT / image.getHeight(), (width * 0.3f) / image.getWidth());
            float imageWidth = image.getWidth() * scale;
            float imageHeight = image.getHeight() * scale;
            canvas.image(image, rtl ? right - imageWidth : left, y - imageHeight, imageWidth, imageHeight);
            logoBottom = y - imageHeight;
        }
        y = paragraph(canvas, y, name, true, style.titleSize() + 2, style.textColor());
        for (String line : lines) {
            y = paragraph(canvas, y, line, false, style.smallSize(), style.mutedColor());
        }
        y = Math.min(y, logoBottom);
        y -= 4;
        canvas.rule(left, right, y, style.accentColor(), 1f);
        return y - 8;
    }

    private float paragraph(Canvas canvas, float top, String paragraph, boolean bold, float size, int color)
            throws IOException, ReportException {
        if (paragraph == null || paragraph.isBlank()) {
            return top;
        }
        float y = top;
        for (Typesetter.Line line : text.wrap(paragraph, bold, size, width)) {
            canvas.text(line, size, color, left, width, Canvas.Side.CENTER, y - size * BASELINE);
            y -= size * LEADING;
        }
        return y;
    }
}
