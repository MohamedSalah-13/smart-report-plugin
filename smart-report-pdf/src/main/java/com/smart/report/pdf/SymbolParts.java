package com.smart.report.pdf;

import com.smart.report.ReportException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.smart.report.pdf.Typesetter.BASELINE;
import static com.smart.report.pdf.Typesetter.LEADING;

/**
 * رمز QR والباركود: كل واحد قطعة واحدة ما بتتقسمش، بمنطقته الفاضية. الخطوط والمربعات الغامقة المتجاورة
 * بتترسم مستطيلًا واحدًا، والكل مسار واحد بيتملى مرة واحدة: مربعات منفصلة بتسيب خطوط رفيعة فاتحة بينها في
 * بعض البرامج وعلى بعض الطابعات. اللون أسود مهما كان لون النص: القارئ محتاج أعلى تباين.
 */
final class SymbolParts {

    private static final float POINTS_PER_MM = 72f / 25.4f;
    /** أضيق خط في الباركود: 0.33 مم، حوالي 3 نقط على الطابعة الحرارية (203 نقطة في البوصة). */
    static final float BAR_MODULE = 0.33f * POINTS_PER_MM;
    /** أضيق خط يتقري: 0.25 مم، نقطتين على الطابعة الحرارية. أضيق من كده الباركود بيفشل بدل ما يطلع بايظ. */
    static final float MIN_BAR_MODULE = 0.25f * POINTS_PER_MM;
    /** أقل عدد نقط طابعة للمربع مع {@link PdfDocument#printerDpi}: نقطة واحدة بتضيع أو بتتمد مع الحبر. */
    static final int MIN_DOTS = 2;
    /** المسافة بين الخطوط والنص المكتوب تحتها. */
    private static final float BARCODE_CAPTION_GAP = 2f;

    private SymbolParts() {
    }

    /** أقرب نقطة طابعة لـ {@code x} من حافة الورقة الشمال؛ زي ما هو من غير دقة. */
    private static float onDots(float x, float dot) {
        return dot > 0 ? Math.round(x / dot) * dot : x;
    }

    /** أقرب نقطة طابعة لـ {@code top} من حافة الورقة الفوقانية (الطابعة بتعدّ من فوق)؛ زي ما هو من غير دقة. */
    private static float topOnDots(float top, Frame frame) {
        float dot = frame.dot();
        return dot > 0 ? frame.pageHeight() - Math.round((frame.pageHeight() - top) / dot) * dot : top;
    }

    /** مكان صندوق عرضه {@code boxWidth} على السطر، حسب محاذاته. */
    private static float boxLeft(Frame frame, Align align, float boxWidth) {
        return switch (frame.side(align)) {
            case LEFT -> frame.left();
            case CENTER -> frame.left() + (frame.width() - boxWidth) / 2;
            case RIGHT -> frame.right() - boxWidth;
        };
    }

    /**
     * رمز QR بمنطقته الفاضية، كله في صفحة واحدة. المنطقة الفاضية جزء من مكانه، فمفيش حاجة قبله أو بعده
     * بتلمسه. لو هو ومنطقته أعرض من السطر أو أطول من صفحة جديدة بيصغر لحد ما يكفي.
     */
    static Part qrCode(PdfDocument.QrBlock qr, Frame frame) throws ReportException {
        QrSymbol symbol = qr.symbol();
        int modules = symbol.size();
        int span = modules + 2 * QrSymbol.QUIET_ZONE;
        float room = Math.min(frame.width(), frame.freshRoom());
        float fitted = Math.min(qr.size() / modules, room / span);
        float dot = frame.dot();
        if (dot > 0) {
            // عدد صحيح من نقط الطابعة: الأقرب للمقاس المطلوب، وأقل لو ما كفّاش، ونقطتين على الأقل.
            int dots = Math.max(MIN_DOTS, Math.round(qr.size() / modules / dot));
            while (dots > MIN_DOTS && span * dots * dot > room) {
                dots--;
            }
            if (span * dots * dot > room * 1.0001f) {
                throw new ReportException(String.format(Locale.ROOT,
                        "The QR code needs %.1f mm with %d printer dots per module at %d dpi, but there are only"
                                + " %.1f mm. Use less text or wider paper.",
                        span * dots * dot / POINTS_PER_MM, dots, frame.dpi(), room / POINTS_PER_MM));
            }
            fitted = dots * dot;
        }
        float module = fitted;
        float side = span * module;
        float boxLeft = onDots(boxLeft(frame, qr.align(), side), dot);
        Piece square = new Piece() {
            @Override
            public float height() {
                return side;
            }

            @Override
            public void draw(Canvas canvas, float top) throws IOException {
                float originX = boxLeft + QrSymbol.QUIET_ZONE * module;
                float originY = topOnDots(top, frame) - QrSymbol.QUIET_ZONE * module;
                canvas.fillColor(0x000000);
                for (int row = 0; row < modules; row++) {
                    int column = 0;
                    while (column < modules) {
                        if (!symbol.dark(row, column)) {
                            column++;
                            continue;
                        }
                        int start = column;
                        while (column < modules && symbol.dark(row, column)) {
                            column++;
                        }
                        canvas.rect(originX + start * module, originY - (row + 1) * module,
                                (column - start) * module, module);
                    }
                }
                canvas.fill();
            }
        };
        return Flow.of(List.of(square)).withGaps(Parts.BLOCK_GAP, 0);
    }

    /**
     * باركود Code 128 بمنطقته الفاضية من الجنبين، وتحته النص مكتوب؛ الاتنين في صفحة واحدة.
     *
     * <p>النص بيترسم من الشمال لليمين زي ما هو، من غير ترتيب الاتجاه العربي: الكود حروف وأرقام بترتيبها،
     * و«2026-0042-SHP» في سطر عربي كانت هتتقلب أجزاؤها. النص اللي أعرض من السطر (خط كبير في
     * {@link PdfStyle} وكود طويل) بيصغر لحد ما يكفي، لحد أصغر خط؛ ولو لسه أعرض بيكمل على سطر تاني محسوب
     * في مكان الباركود. وهو في منتصف الخطوط، من غير ما يخرج برّه الهامش.</p>
     */
    static Part barcode(PdfDocument.BarcodeBlock barcode, Frame frame) throws IOException, ReportException {
        BarcodeSymbol symbol = barcode.symbol();
        float width = frame.width();
        int span = symbol.modules() + 2 * BarcodeSymbol.QUIET_ZONE;
        float fitted = Math.min(BAR_MODULE, width / span);
        float dot = frame.dot();
        if (dot > 0) {
            // عدد صحيح من نقط الطابعة: الأقرب لـ 0.33 مم، وأقل لو ما كفّاش، لحد نقطتين ومش أضيق من 0.25 مم.
            int fewest = Math.max(MIN_DOTS, (int) Math.ceil(MIN_BAR_MODULE / dot - 0.001f));
            int dots = Math.max(fewest, Math.round(BAR_MODULE / dot));
            while (dots > fewest && span * dots * dot > width) {
                dots--;
            }
            if (span * dots * dot > width * 1.0001f) {
                throw new ReportException(String.format(Locale.ROOT,
                        "The barcode \"%s\" needs %.1f mm with its narrowest bars %d printer dots wide at %d dpi,"
                                + " but the line is %.1f mm wide. Use a shorter code or wider paper.",
                        symbol.text(), span * dots * dot / POINTS_PER_MM, dots, frame.dpi(), width / POINTS_PER_MM));
            }
            fitted = dots * dot;
        } else if (fitted < MIN_BAR_MODULE * 0.999f) {
            throw new ReportException(String.format(Locale.ROOT,
                    "The barcode \"%s\" needs %.1f mm at its narrowest readable bars, but the line is %.1f mm wide."
                            + " Use a shorter code or wider paper.",
                    symbol.text(), span * MIN_BAR_MODULE / POINTS_PER_MM, width / POINTS_PER_MM));
        }
        float module = fitted;
        float size = frame.style().bodySize();
        float natural = frame.text().plain(symbol.text(), size).width();
        if (natural > width) {
            size = Math.max(PdfStyle.MIN_FONT_SIZE, size * width / natural);
        }
        float captionSize = size;
        List<Typesetter.Line> captions = captionLines(frame, symbol.text(), size);
        float captionHeight = BARCODE_CAPTION_GAP + captions.size() * size * LEADING;
        float bars = Math.max(1f, Math.min(barcode.height(), frame.freshRoom() - captionHeight));
        float barsWidth = symbol.modules() * module;
        float originX = onDots(boxLeft(frame, barcode.align(), span * module), dot)
                + BarcodeSymbol.QUIET_ZONE * module;
        Piece code = new Piece() {
            @Override
            public float height() {
                return bars + captionHeight;
            }

            @Override
            public void draw(Canvas canvas, float top) throws IOException {
                canvas.fillColor(0x000000);
                int column = 0;
                while (column < symbol.modules()) {
                    if (!symbol.dark(column)) {
                        column++;
                        continue;
                    }
                    int start = column;
                    while (column < symbol.modules() && symbol.dark(column)) {
                        column++;
                    }
                    canvas.rect(originX + start * module, top - bars, (column - start) * module, bars);
                }
                canvas.fill();
                float y = top - (bars + BARCODE_CAPTION_GAP);
                float centre = originX + barsWidth / 2;
                for (Typesetter.Line caption : captions) {
                    float captionLeft = Math.clamp(centre - caption.width() / 2, frame.left(),
                            frame.right() - caption.width());
                    canvas.text(caption, captionSize, frame.style().textColor(), captionLeft, caption.width(),
                            Canvas.Side.LEFT, y - captionSize * BASELINE);
                    y -= captionSize * LEADING;
                }
            }
        };
        return Flow.of(List.of(code)).withGaps(Parts.BLOCK_GAP, 0);
    }

    /**
     * نص الباركود سطورًا تكفي عرض السطر بالخط {@code size}: سطر واحد في العادي، وبيتكسر على حروفه بس لو
     * أعرض من السطر حتى بأصغر خط.
     */
    private static List<Typesetter.Line> captionLines(Frame frame, String text, float size)
            throws IOException, ReportException {
        List<Typesetter.Line> lines = new ArrayList<>(1);
        int start = 0;
        while (start < text.length()) {
            int end = text.length();
            while (end > start + 1 && frame.text().plain(text.substring(start, end), size).width() > frame.width()) {
                end--;
            }
            lines.add(frame.text().plain(text.substring(start, end), size));
            start = end;
        }
        return lines;
    }
}
