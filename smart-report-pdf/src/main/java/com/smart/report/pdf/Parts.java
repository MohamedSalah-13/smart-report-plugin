package com.smart.report.pdf;

import com.smart.report.ReportException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.smart.report.pdf.Typesetter.BASELINE;
import static com.smart.report.pdf.Typesetter.LEADING;

/**
 * كل جزء في {@link PdfDocument} متقاسًا ({@link Part}): المكان الوحيد اللي بيعرف أنواع الأجزاء. اللي هنا
 * الأجزاء الصغيرة؛ الجدول والملخص في {@link TablePart}، والرموز في {@link SymbolParts}.
 */
final class Parts {

    /** المسافة قبل الجدول والملخص والرمز وعنوان القسم. */
    static final float BLOCK_GAP = 8f;
    /** المكان الفاضي للتوقيع بين عنوان الخانة وخطها. */
    private static final float SIGNATURE_SPACE = 34f;

    /** الجزء اللي بعده يبدأ في صفحة جديدة؛ القواعد في {@link PageWriter}. */
    private static final Part PAGE_BREAK = new Part() {
        @Override
        public Pieces pieces() {
            return () -> null;
        }

        @Override
        public boolean startsPage() {
            return true;
        }
    };

    private Parts() {
    }

    /** الجزء متقاسًا؛ بيانات كتير ورا بعض بتبقى جزء لكل سطر منها. */
    static List<Part> of(PdfDocument.Block block, Frame frame) throws IOException, ReportException {
        PdfStyle style = frame.style();
        return switch (block) {
            case PdfDocument.TitleBlock title -> List.of(Flow.of(
                    lines(frame, title.text(), true, style.titleSize(), style.textColor(), Canvas.Side.CENTER))
                    .withGaps(0, 3));
            case PdfDocument.PrintedBlock printed -> List.of(Flow.of(
                    lines(frame, printed.text(), false, style.smallSize(), style.mutedColor(), Canvas.Side.CENTER))
                    .withGaps(0, 3));
            case PdfDocument.SubtitleBlock subtitle -> List.of(Flow.of(
                    lines(frame, subtitle.text(), false, style.subtitleSize(), style.mutedColor(),
                            Canvas.Side.CENTER)).withGaps(0, 3));
            case PdfDocument.HeadingBlock heading -> List.of(Flow.of(
                    lines(frame, heading.text(), true, style.headingSize(), style.textColor(),
                            frame.side(Align.START)))
                    .withGaps(BLOCK_GAP, 2)
                    // عنوان القسم ما يفضلش لوحده في آخر صفحة: بيتأكد إن بعده مكان لرأس جدول وصف.
                    .withKeep(Keep.room(style.headingSize() * LEADING
                            + 3 * (style.bodySize() * LEADING + 2 * TablePart.PAD_Y))));
            case PdfDocument.TextBlock text -> List.of(Flow.of(
                    lines(frame, text.text(), false, style.bodySize(), style.textColor(), frame.side(text.align())))
                    .withJoin(Join.THROUGH));
            case PdfDocument.SpaceBlock space -> List.of(Flow.of(List.of())
                    .withGaps(space.points(), 0).withJoin(Join.GAP));
            case PdfDocument.FieldsBlock fields -> fields(frame, fields.fields());
            case PdfDocument.TableBlock table -> List.of(TablePart.table(table.table(), frame));
            case PdfDocument.SummaryBlock summary -> List.of(TablePart.summary(summary.lines(), frame));
            case PdfDocument.SignaturesBlock signatures -> List.of(signatures(frame, signatures.labels()));
            case PdfDocument.QrBlock qr -> List.of(SymbolParts.qrCode(qr, frame));
            case PdfDocument.BarcodeBlock barcode -> List.of(SymbolParts.barcode(barcode, frame));
            case PdfDocument.Ean13Block ean -> List.of(SymbolParts.ean13(ean, frame));
            case PdfDocument.ImageBlock image -> List.of(ImageParts.image(image, frame));
            case PdfDocument.PageBreakBlock ignored -> List.of(PAGE_BREAK);
        };
    }

    /** الجملة الختامية بعد آخر جزء. */
    static Part footer(Frame frame, String text) throws IOException, ReportException {
        PdfStyle style = frame.style();
        return Flow.of(lines(frame, text, false, style.smallSize(), style.mutedColor(), Canvas.Side.CENTER))
                .withGaps(BLOCK_GAP + 4, 0);
    }

    // ===================== الفقرة =====================

    /** سطر واحد من فقرة على عرض الصفحة: الفقرة بتكمل على الصفحة الجاية سطرًا سطرًا. */
    record TextLine(Typesetter.Line line, float size, int color, float boxLeft, float boxWidth, Canvas.Side side)
            implements Piece {

        @Override
        public float height() {
            return size * LEADING;
        }

        @Override
        public void draw(Canvas canvas, float top) throws IOException {
            canvas.text(line, size, color, boxLeft, boxWidth, side, top - size * BASELINE);
        }
    }

    /** فقرة على عرض الصفحة سطورًا؛ النص الفاضي ما لوش سطور. */
    static List<Piece> lines(Frame frame, String text, boolean bold, float size, int color, Canvas.Side side)
            throws IOException, ReportException {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<Piece> lines = new ArrayList<>();
        for (Typesetter.Line line : frame.text().wrap(text, bold, size, frame.width())) {
            lines.add(new TextLine(line, size, color, frame.left(), frame.width(), side));
        }
        return lines;
    }

    // ===================== البيانات =====================

    /** سطر واحد من سطر بيانات: السطر رقم {@code k} من كل بيان فيه، كل واحد في خانته. */
    record FieldsLine(List<Typesetter.Line> lines, float size, int color, float[] boxLeft, float cell,
                      Canvas.Side side) implements Piece {

        @Override
        public float height() {
            return size * LEADING;
        }

        @Override
        public void draw(Canvas canvas, float top) throws IOException {
            for (int column = 0; column < lines.size(); column++) {
                Typesetter.Line line = lines.get(column);
                if (line != null) {
                    canvas.text(line, size, color, boxLeft[column], cell, side, top - size * BASELINE);
                }
            }
        }
    }

    /**
     * بيانين في السطر: الأول عند بداية السطر والتاني في نصف الصفحة، وكل سطر جزء لوحده بنقطة تحته. البيانان
     * بيفضلوا مع بعض لو صفحة تكفيهم؛ البيان الأطول من صفحة بيكمل سطرًا سطرًا على اللي بعدها — كان بيترسم كله
     * من مكانه ويخرج عن الورقة.
     */
    private static List<Part> fields(Frame frame, List<String[]> fields) throws IOException, ReportException {
        int perRow = 2;
        PdfStyle style = frame.style();
        float size = style.bodySize();
        float leading = size * LEADING;
        float gutter = 12f;
        float cell = (frame.width() - gutter * (perRow - 1)) / perRow;
        List<Part> rows = new ArrayList<>();
        for (int start = 0; start < fields.size(); start += perRow) {
            List<List<Typesetter.Line>> row = new ArrayList<>(perRow);
            float[] boxLeft = new float[perRow];
            int tallest = 1;
            for (int i = start; i < Math.min(start + perRow, fields.size()); i++) {
                String[] field = fields.get(i);
                String text = field[0].isBlank() ? field[1] : field[0] + ": " + field[1];
                List<Typesetter.Line> lines = frame.text().wrap(text, false, size, cell);
                tallest = Math.max(tallest, lines.size());
                row.add(lines);
                float offset = (i - start) * (cell + gutter);
                boxLeft[i - start] = frame.rtl() ? frame.right() - offset - cell : frame.left() + offset;
            }
            List<Piece> lines = new ArrayList<>(tallest);
            for (int k = 0; k < tallest; k++) {
                List<Typesetter.Line> line = new ArrayList<>(row.size());
                for (List<Typesetter.Line> column : row) {
                    line.add(k < column.size() ? column.get(k) : null);
                }
                lines.add(new FieldsLine(line, size, style.textColor(), boxLeft, cell, frame.side(Align.START)));
            }
            rows.add(new Flow(0, Keep.whole(tallest * leading), lines, 1, Join.NONE));
        }
        return rows;
    }

    // ===================== خانات التوقيع =====================

    /**
     * خانات التوقيع: عنوان كل خانة («المستلم»، «المحاسب») وتحته مكان للتوقيع وخط. الخانات بالعرض بترتيب
     * القراءة — الأولى عند بداية السطر — ومع بعض في نفس الصفحة، والأجزاء اللي بيتوقّع عليها بتتنقل معاها
     * ({@link Join#ANCHOR}).
     */
    private static Part signatures(Frame frame, List<String> labels) throws IOException, ReportException {
        int count = labels.size();
        if (count == 0) {
            return Flow.of(List.of()).withJoin(Join.ANCHOR);
        }
        PdfStyle style = frame.style();
        float size = style.bodySize();
        float cell = frame.width() / count;
        float inset = Math.min(12f, cell * 0.1f);
        List<List<Typesetter.Line>> wrapped = new ArrayList<>(count);
        int tallest = 1;
        for (String label : labels) {
            List<Typesetter.Line> lines = frame.text().wrap(label, true, size, cell - 2 * inset);
            tallest = Math.max(tallest, lines.size());
            wrapped.add(lines);
        }
        float height = tallest * size * LEADING + SIGNATURE_SPACE;
        Piece boxes = new Piece() {
            @Override
            public float height() {
                return height + 2;
            }

            @Override
            public void draw(Canvas canvas, float top) throws IOException {
                float leading = size * LEADING;
                for (int i = 0; i < count; i++) {
                    float boxLeft = frame.rtl() ? frame.right() - (i + 1) * cell : frame.left() + i * cell;
                    List<Typesetter.Line> lines = wrapped.get(i);
                    for (int k = 0; k < lines.size(); k++) {
                        canvas.text(lines.get(k), size, style.textColor(), boxLeft, cell, Canvas.Side.CENTER,
                                top - k * leading - size * BASELINE);
                    }
                    canvas.rule(boxLeft + inset, boxLeft + cell - inset, top - height, style.textColor(), 0.6f);
                }
            }
        };
        return Flow.of(List.of(boxes)).withGaps(BLOCK_GAP * 2, 0).withJoin(Join.ANCHOR);
    }
}
