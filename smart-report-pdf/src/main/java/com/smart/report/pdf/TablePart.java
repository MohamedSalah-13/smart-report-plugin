package com.smart.report.pdf;

import com.smart.report.ReportException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.smart.report.pdf.Typesetter.BASELINE;
import static com.smart.report.pdf.Typesetter.LEADING;

/**
 * الجدول والملخص: كل صف قطعة ({@link Piece})، والرأس بيتكرر في أول كل صفحة.
 *
 * <ul>
 *     <li><b>عرض الأعمدة بيتحدد قبل الرسم</b> ({@link ColumnFit})، ومفيش عمود أضيق من أطول كلمة فيه، فالسطور
 *         اللي اتقاست على عرض بتترسم في نفس العرض.</li>
 *     <li><b>الجدول بيتبني في اتجاه الصفحة:</b> أول عمود منطقي على اليمين في الصفحة العربية، وصف الإجمالي
 *         بنفس ترتيب باقي الصفوف، فكل رقم تحت عموده.</li>
 *     <li>الصف الأطول من صفحة كاملة بيتقسّم على سطوره: كان بيترسم تحت حافة الورقة ويضيع باقيه في الطباعة.</li>
 * </ul>
 */
final class TablePart implements Part {

    static final float PAD_X = 4f;
    static final float PAD_Y = 3f;
    /** بيتشال من عرض الخلية فوق حشوتها: حدّاها ونقطة احتياط. */
    private static final float SLACK = 1f;
    private static final float GRID_WIDTH = 0.5f;
    /** خطّي الإجمالي الكلي تحت إجماليات فرعية: سُمك كل خط والمسافة بينهم. */
    private static final float GRAND_RULE = 0.6f;
    private static final float GRAND_GAP = 1.8f;

    /** {@code GRAND}: الإجمالي الكلي تحت إجماليات فرعية ({@link PdfTable#grandTotal}). */
    private enum RowKind { HEADER, NORMAL, TOTAL, GRAND }

    /** صف جدول اتقاس: سطور كل خلية وارتفاع الصف. */
    /** @param colors لون كل خلية، أو -1 = لون الصف */
    private record RowLayout(List<List<Typesetter.Line>> cells, float height, float size, boolean split, int[] colors) {
    }

    private final PdfTable table;
    private final Frame frame;
    private final float[] widths;
    /** مكان كل عمود منطقي على الورقة: الأول عند بداية السطر. */
    private final float[] x;
    /** الرأس اللي بيتكرر في أول كل صفحة، أو {@code null}. */
    private final Piece repeated;
    /** الرأس اللي بيترسم مرة واحدة قبل الصفوف (الأطول من تلت صفحة)، أو {@code null}. */
    private final Piece once;

    private TablePart(PdfTable table, Frame frame) throws IOException, ReportException {
        this.table = table;
        this.frame = frame;
        this.widths = columnWidths(table, frame);
        int count = widths.length;
        this.x = new float[count];
        float cursor = frame.rtl() ? frame.right() : frame.left();
        for (int i = 0; i < count; i++) {
            if (frame.rtl()) {
                cursor -= widths[i];
                x[i] = cursor;
            } else {
                x[i] = cursor;
                cursor += widths[i];
            }
        }
        Row header = table.headerShown() ? new Row(layout(headers(table), null, true, frame.style().headerSize()),
                RowKind.HEADER, false) : null;
        // الرأس بيتكرر في أول كل صفحة لو مش واخد أكتر من تلت الصفحة. الأطول من كده بيترسم مرة واحدة
        // (ومتقسّم لو أطول من صفحة): كان بيتكرر في كل صفحة ويخرج عن الورقة أو ياكلها كلها.
        boolean repeats = header != null && header.height() <= frame.freshRoom() / 3;
        this.repeated = repeats ? header : null;
        this.once = repeats ? null : header;
    }

    /** جدول بمسافة قبله؛ الجدول اللي من غير أعمدة ما بيرسمش حاجة. */
    static Part table(PdfTable table, Frame frame) throws IOException, ReportException {
        if (table.columns().isEmpty()) {
            return Flow.of(List.of()).withGaps(Parts.BLOCK_GAP, 0);
        }
        return new TablePart(table, frame);
    }

    /**
     * الملخص: جدول بلا رأس من عمودين، وسطر الإجمالي فيه بخط أكبر. الملخص قصير: لو ما كفّاهوش الباقي بيتنقل
     * كله للصفحة الجاية بدل ما يتقسم، وهو أول اللي بيتنقل مع خانات التوقيع.
     */
    static Part summary(List<PdfDocument.SummaryLine> lines, Frame frame) throws IOException, ReportException {
        PdfStyle style = frame.style();
        float width = frame.width();
        // عمود القيمة بعرض أطول قيمة فيه، فالرقم ما يلتفش على سطرين — بين 30% و55% من العرض.
        float widestValue = 0;
        for (PdfDocument.SummaryLine line : lines) {
            float size = style.bodySize() + (line.total() ? 2f : 0f);
            String value = Typesetter.clean(line.value()).replace('\n', ' ');
            widestValue = Math.max(widestValue, frame.text().measure(value, value, line.total(), size));
        }
        float valueWidth = Math.clamp(widestValue + 2 * PAD_X + SLACK + 8, width * 0.30f, width * 0.55f);
        PdfTable table = PdfTable.create().withoutHeader().totalSizeBoost(2f)
                .column("", width - valueWidth, Align.START)
                .column("", valueWidth, Align.CENTER);
        for (PdfDocument.SummaryLine line : lines) {
            if (line.total()) {
                table.totals(line.label(), line.value());
            } else {
                table.row(line.label(), line.value());
            }
        }
        float estimate = lines.size() * (style.bodySize() * LEADING + 2 * PAD_Y) + 4;
        TablePart rows = new TablePart(table, frame);
        return new Part() {
            @Override
            public float gapBefore() {
                return Parts.BLOCK_GAP;
            }

            @Override
            public Keep keep() {
                return Keep.whole(estimate);
            }

            @Override
            public Pieces pieces() {
                return rows.pieces();
            }

            @Override
            public Join join() {
                return Join.LEAD;
            }
        };
    }

    @Override
    public float gapBefore() {
        return Parts.BLOCK_GAP;
    }

    /** الرأس اللي بيتكرر في أول كل صفحة الجدول بيكمل عليها؛ جدول بلا صفوف رأسه قطعته الوحيدة. */
    @Override
    public Piece header() {
        return table.rows().isEmpty() ? null : repeated;
    }

    /**
     * الرأس اللي بيترسم مرة لو أطول من تلت صفحة، والصفوف بالترتيب. جدول بلا صفوف رأسه بيترسم برضه، عشان
     * الورقة تقول إن الجدول فاضي.
     */
    @Override
    public Pieces pieces() {
        if (table.rows().isEmpty()) {
            Piece header = repeated != null ? whole(repeated) : once;
            return Pieces.of(header == null ? List.of() : List.of(header));
        }
        Iterator<PdfTable.Row> rows = table.rows().iterator();
        return new Pieces() {
            private boolean headerLeft = once != null;
            /** ترتيب الصف بين الصفوف العادية، للتظليل المتبادل. */
            private int index;
            /** الصف الجاي لو اتقرا عشان نعرف هل بيتنقل مع اللي قبله. */
            private PdfTable.Row ahead;

            @Override
            public Piece next() throws IOException, ReportException {
                if (headerLeft) {
                    headerLeft = false;
                    return once;
                }
                PdfTable.Row row = take();
                if (row == null) {
                    return null;
                }
                // الإجمالي الفرعي قطعة واحدة مع الصف اللي فوقه، فما بينزلش لوحده في أول صفحة.
                Piece piece = piece(row);
                for (PdfTable.Row following = take(); following != null; following = take()) {
                    if (!following.withPrevious()) {
                        ahead = following;
                        break;
                    }
                    piece = new Together(piece, piece(following));
                }
                return piece;
            }

            private PdfTable.Row take() {
                PdfTable.Row row = ahead != null ? ahead : rows.hasNext() ? rows.next() : null;
                ahead = null;
                return row;
            }

            private Piece piece(PdfTable.Row row) throws IOException, ReportException {
                float bodySize = frame.style().bodySize();
                float size = row.total() ? bodySize + table.totalSizeBoost() : bodySize;
                boolean striped = frame.style().stripes() && index % 2 == 1;
                if (!row.total()) {
                    index++;
                }
                RowKind kind = row.grand() ? RowKind.GRAND : row.total() ? RowKind.TOTAL : RowKind.NORMAL;
                return new Row(layout(row.cells(), row, row.total(), size), kind,
                        striped);
            }
        };
    }

    /**
     * قطعتين ورا بعض بيتنقلوا مع بعض: آخر صف في مجموعة وإجماليها الفرعي. لو الاتنين أطول من صفحة بيتقسموا على
     * سطورهم، وآخر سطر في الصف بينزل مع الإجمالي لو الإجمالي ما كفّاهوش الباقي.
     */
    private record Together(Piece above, Piece below) implements Piece {

        @Override
        public float height() {
            return above.height() + below.height();
        }

        @Override
        public void draw(Canvas canvas, float top) throws IOException {
            above.draw(canvas, top);
            below.draw(canvas, top - above.height());
        }

        private int aboveLines() {
            return Math.max(1, above.lineCount());
        }

        @Override
        public int lineCount() {
            return aboveLines() + Math.max(1, below.lineCount());
        }

        @Override
        public int fitting(float room) {
            if (room < above.height()) {
                return Math.min(above.fitting(room), aboveLines() - 1);
            }
            int below = this.below.fitting(room - above.height());
            return below >= Math.max(1, this.below.lineCount()) ? lineCount() : aboveLines() - 1;
        }

        @Override
        public Piece slice(int from, int to) {
            int split = aboveLines();
            if (to <= split) {
                return part(above, from, to, split);
            }
            if (from >= split) {
                return part(below, from - split, to - split, lineCount() - split);
            }
            return new Together(part(above, from, split, split), part(below, 0, to - split, lineCount() - split));
        }

        /** القطعة كلها زي ما هي لو السطور كلها منها، فالصف اللي ما اتقسمش بيترسم زي الأول بالظبط. */
        private static Piece part(Piece piece, int from, int to, int lines) {
            return from == 0 && to >= lines ? piece : piece.slice(from, to);
        }
    }

    /** الرأس قطعة واحدة ما بتتقسمش: رأس جدول فاضي بيترسم كله في مكان واحد. */
    private static Piece whole(Piece piece) {
        return new Piece() {
            @Override
            public float height() {
                return piece.height();
            }

            @Override
            public void draw(Canvas canvas, float top) throws IOException {
                piece.draw(canvas, top);
            }
        };
    }

    private static String[] headers(PdfTable table) {
        return table.columns().stream().map(PdfTable.Column::header).toArray(String[]::new);
    }

    /** عرض كل عمود: أطول كلمة فيه بالخط والحجم اللي هتترسم بيهم حد أدنى، والباقي بالأوزان. */
    private static float[] columnWidths(PdfTable table, Frame frame) throws IOException, ReportException {
        List<PdfTable.Column> columns = table.columns();
        float bodySize = frame.style().bodySize();
        float totalSize = bodySize + table.totalSizeBoost();
        float[] weights = new float[columns.size()];
        for (int i = 0; i < weights.length; i++) {
            weights[i] = columns.get(i).weight();
        }
        float[] narrowest = new float[columns.size()];
        if (table.headerShown()) {
            widen(narrowest, headers(table), true, frame.style().headerSize(), frame);
        }
        for (PdfTable.Row row : table.rows()) {
            widen(narrowest, row.cells(), bolds(table, row), row.total() ? totalSize : bodySize, frame);
        }
        return ColumnFit.fit(weights, narrowest, frame.width());
    }

    /** يرفع أضيق عرض لكل عمود لعرض أطول كلمة في خليته مع حشوتها، بحد أقصى نصف عرض الجدول. */
    /** خط كل خلية في الصف: عريض في الإجمالي، إلا لو قاعدة {@link PdfTable#cellStyle} قالت غير كده. */
    private static boolean[] bolds(PdfTable table, PdfTable.Row row) {
        boolean[] bold = new boolean[row.cells().length];
        for (int i = 0; i < bold.length; i++) {
            bold[i] = table.styleOf(row, i).boldOr(row.total());
        }
        return bold;
    }

    private static void widen(float[] narrowest, String[] cells, boolean bold, float size, Frame frame)
            throws IOException, ReportException {
        boolean[] all = new boolean[cells.length];
        java.util.Arrays.fill(all, bold);
        widen(narrowest, cells, all, size, frame);
    }

    private static void widen(float[] narrowest, String[] cells, boolean[] bold, float size, Frame frame)
            throws IOException, ReportException {
        for (int i = 0; i < Math.min(cells.length, narrowest.length); i++) {
            float word = frame.text().widestWord(cells[i], bold[i], size);
            if (word > 0) {
                narrowest[i] = Math.max(narrowest[i], Math.min(word + 2 * PAD_X + SLACK, frame.width() / 2));
            }
        }
    }

    /** @param source صف البيانات عشان شكل خلاياه، أو {@code null} للرأس */
    private RowLayout layout(String[] cells, PdfTable.Row source, boolean bold, float size)
            throws IOException, ReportException {
        List<List<Typesetter.Line>> lines = new ArrayList<>(cells.length);
        int[] colors = new int[cells.length];
        int tallest = 1;
        for (int i = 0; i < cells.length; i++) {
            CellStyle style = source == null ? CellStyle.PLAIN : table.styleOf(source, i);
            colors[i] = style.colorOr(-1);
            List<Typesetter.Line> cell = frame.text().wrap(cells[i], style.boldOr(bold), size,
                    widths[i] - 2 * PAD_X - SLACK);
            tallest = Math.max(tallest, cell.size());
            lines.add(cell);
        }
        return new RowLayout(lines, tallest * size * LEADING + 2 * PAD_Y, size, false, colors);
    }

    /** صف واحد: بيترسم كله في مكانه، أو بيتقسم على سطوره لو أطول من صفحة كاملة. */
    private final class Row implements Piece {

        private final RowLayout row;
        private final RowKind kind;
        private final boolean striped;

        Row(RowLayout row, RowKind kind, boolean striped) {
            this.row = row;
            this.kind = kind;
            this.striped = striped;
        }

        @Override
        public float height() {
            return row.height();
        }

        /** عدد سطور أطول خلية في الصف. */
        @Override
        public int lineCount() {
            int tallest = 0;
            for (List<Typesetter.Line> cell : row.cells()) {
                tallest = Math.max(tallest, cell.size());
            }
            return tallest;
        }

        @Override
        public int fitting(float room) {
            return (int) ((room - 2 * PAD_Y) / (row.size() * LEADING));
        }

        /** السطور من {@code from} لحد {@code to} من كل خلية: جزء من صف اتقسّم على صفحتين، سطوره من أعلاه. */
        @Override
        public Piece slice(int from, int to) {
            List<List<Typesetter.Line>> cells = new ArrayList<>(row.cells().size());
            int tallest = 1;
            for (List<Typesetter.Line> cell : row.cells()) {
                List<Typesetter.Line> part = from >= cell.size() ? List.of()
                        : cell.subList(from, Math.min(to, cell.size()));
                tallest = Math.max(tallest, part.size());
                cells.add(part);
            }
            return new Row(new RowLayout(cells, tallest * row.size() * LEADING + 2 * PAD_Y, row.size(), true,
                    row.colors()), kind,
                    striped);
        }

        @Override
        public void draw(Canvas canvas, float top) throws IOException {
            PdfStyle style = frame.style();
            List<PdfTable.Column> columns = table.columns();
            float tableLeft = frame.rtl() ? x[x.length - 1] : x[0];
            float tableWidth = 0;
            for (float w : widths) {
                tableWidth += w;
            }
            float height = row.height();
            float leading = row.size() * LEADING;

            int fill = switch (kind) {
                case HEADER -> style.headerFill();
                case TOTAL, GRAND -> style.totalsFill();
                case NORMAL -> striped ? style.stripeFill() : PdfStyle.WHITE;
            };
            if (fill != PdfStyle.WHITE) {
                canvas.fillColor(fill);
                canvas.rect(tableLeft, top - height, tableWidth, height);
                canvas.fill();
            }

            int color = switch (kind) {
                case HEADER -> style.headerText();
                case TOTAL, GRAND -> style.totalsText();
                case NORMAL -> style.textColor();
            };
            for (int i = 0; i < row.cells().size(); i++) {
                List<Typesetter.Line> lines = row.cells().get(i);
                Canvas.Side side = kind == RowKind.HEADER ? Canvas.Side.CENTER : frame.side(columns.get(i).align());
                // الخلية الأقصر من صفها بتتوسّط رأسيًا — إلا في صف اتقسّم على صفحتين، سطوره بتكمل من أعلاه.
                float firstTop = row.split() ? top - PAD_Y
                        : top - PAD_Y - (height - 2 * PAD_Y - lines.size() * leading) / 2;
                int cellColor = row.colors()[i] >= 0 ? row.colors()[i] : color;
                for (int k = 0; k < lines.size(); k++) {
                    canvas.text(lines.get(k), row.size(), cellColor, x[i] + PAD_X, widths[i] - 2 * PAD_X, side,
                            firstTop - k * leading - row.size() * BASELINE);
                }
            }

            if (style.grid()) {
                canvas.strokeColor(style.ruleColor());
                canvas.lineWidth(GRID_WIDTH);
                for (int i = 0; i < widths.length; i++) {
                    canvas.rect(x[i], top - height, widths[i], height);
                }
                canvas.stroke();
            } else {
                canvas.rule(tableLeft, tableLeft + tableWidth, top - height, style.ruleColor(), GRID_WIDTH);
            }
            // بلا تعبئة (توفير الحبر): الرأس بخط سميك تحته، والإجمالي بخطين فوقه وتحته.
            if (fill == PdfStyle.WHITE && kind != RowKind.NORMAL) {
                canvas.rule(tableLeft, tableLeft + tableWidth, top - height, color, 1.2f);
                if (kind == RowKind.TOTAL || kind == RowKind.GRAND) {
                    canvas.rule(tableLeft, tableLeft + tableWidth, top, color, 1.2f);
                }
            }
            // الإجمالي الكلي تحت إجماليات فرعية بنفس شكلها: خط سميك فوقه وخطين تحته (بلا تعبئة الخطوط دي موجودة
            // أصلًا، فبيتزوّد الخط التاني بس).
            if (kind == RowKind.GRAND) {
                if (fill != PdfStyle.WHITE) {
                    canvas.rule(tableLeft, tableLeft + tableWidth, top, color, 1.2f);
                    canvas.rule(tableLeft, tableLeft + tableWidth, top - height, color, GRAND_RULE);
                }
                canvas.rule(tableLeft, tableLeft + tableWidth, top - height + GRAND_GAP, color, GRAND_RULE);
            }
        }
    }
}
