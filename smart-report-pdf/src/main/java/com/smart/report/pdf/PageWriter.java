package com.smart.report.pdf;

import com.smart.report.ReportException;
import com.smart.report.text.ArabicTextShaper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * يرسم {@link PdfDocument} على صفحاته: بيمشي من أعلى الصفحة لأسفلها، وبيفتح صفحة جديدة لما الجزء
 * الجاي ما يكفيهوش الباقي.
 *
 * <p>ثلاث قواعد جاية من تجربة طباعة العربي على الورق، وكل واحدة منها كانت عيبًا حقيقيًا:</p>
 * <ul>
 *     <li><b>السطر بيتكسر قبل ما يتشكّل، مش بعده.</b> التشكيل بيحط السطر بترتيب الرسم؛ لو اتكسر
 *         بعدها، آخر الجملة يطلع في السطر الأول. النص المنطقي بيتكسر عند مسافاته بقياس كل سطر
 *         مرشّح، وكل سطر بيتشكّل لوحده.</li>
 *     <li><b>عرض الأعمدة بيتحدد قبل الرسم</b> ({@link ColumnFit})، ومفيش عمود أضيق من أطول كلمة
 *         فيه، فالسطور اللي اتقاست على عرض بتترسم في نفس العرض.</li>
 *     <li><b>الجدول بيتبني في اتجاه الصفحة:</b> أول عمود منطقي على اليمين في الصفحة العربية،
 *         وصف الإجمالي بنفس ترتيب باقي الصفوف، فكل رقم تحت عموده.</li>
 * </ul>
 */
final class PageWriter {

    /** ارتفاع السطر مضروبًا في حجم الخط. خط النسخ بيعلن سطرًا أطول بكتير من حروفه (1.7)، فالارتفاع ثابت. */
    private static final float LEADING = 1.45f;
    /** بُعد خط الكتابة عن أعلى السطر، مضروبًا في حجم الخط. */
    private static final float BASELINE = 0.98f;
    private static final float PAD_X = 4f;
    private static final float PAD_Y = 3f;
    /** بيتشال من عرض الخلية فوق حشوتها: حدّاها ونقطة احتياط. */
    private static final float SLACK = 1f;
    private static final float GRID_WIDTH = 0.5f;
    private static final float BLOCK_GAP = 8f;
    private static final float LOGO_HEIGHT = 44f;

    private enum Side { LEFT, CENTER, RIGHT }

    private enum RowKind { HEADER, NORMAL, TOTAL }

    /** سطر جاهز للرسم: مقاطعه بترتيب الرسم وعرضه. */
    private record Line(List<FontSet.Run> runs, float width) {
    }

    /** صف جدول اتقاس: سطور كل خلية وارتفاع الصف. */
    private record RowLayout(List<List<Line>> cells, float height, float size) {
    }

    private final PDDocument pdf;
    private final PdfDocument document;
    private final FontSet fonts;
    private final PdfStyle style;
    private final Paper paper;
    private final boolean rtl;
    private final float left;
    private final float right;
    private final float width;
    private final float top;
    private final float bottom;
    /** عرض الكلمة مرسومة، بالوجه والحجم والكلمة: التقرير بيكرر كلماته في كل صف. */
    private final Map<String, Float> wordWidths = new HashMap<>();

    private PDPageContentStream stream;
    private float y;

    PageWriter(PDDocument pdf, PdfDocument document, FontSet fonts) {
        this.pdf = pdf;
        this.document = document;
        this.fonts = fonts;
        this.style = document.resolvedStyle();
        this.paper = document.paper();
        this.rtl = document.rightToLeft();
        this.left = style.margin();
        this.right = paper.width() - style.margin();
        this.width = right - left;
        this.top = paper.height() - style.margin();
        // رقم الصفحة بيتكتب في الهامش السفلي، فالمحتوى بيقف قبله.
        float numbering = document.pageNumberMode() == PageNumbers.NONE ? 0 : style.smallSize() * LEADING;
        this.bottom = style.margin() + numbering;
        if (width < 60 || top - bottom < 60) {
            throw new IllegalArgumentException("The margins leave no room on the page for its content.");
        }
    }

    void write() throws IOException, ReportException {
        pdf.getDocumentInformation().setCreator("smart-report-pdf");
        newPage();
        try {
            letterhead();
            for (PdfDocument.Block block : document.blocks()) {
                draw(block);
            }
            footer();
        } finally {
            stream.close();
        }
        numberPages();
    }

    private void draw(PdfDocument.Block block) throws IOException, ReportException {
        switch (block) {
            case PdfDocument.TitleBlock title -> {
                if (pdf.getDocumentInformation().getTitle() == null && title.text() != null) {
                    pdf.getDocumentInformation().setTitle(title.text());
                }
                paragraph(title.text(), true, style.titleSize(), style.textColor(), Side.CENTER);
                y -= 3;
            }
            case PdfDocument.SubtitleBlock subtitle -> {
                paragraph(subtitle.text(), false, style.subtitleSize(), style.mutedColor(), Side.CENTER);
                y -= 3;
            }
            case PdfDocument.HeadingBlock heading -> {
                gap(BLOCK_GAP);
                // عنوان القسم ما يفضلش لوحده في آخر صفحة: بيتأكد إن بعده مكان لرأس جدول وصف.
                need(style.headingSize() * LEADING + 3 * (style.bodySize() * LEADING + 2 * PAD_Y));
                paragraph(heading.text(), true, style.headingSize(), style.textColor(), side(Align.START));
                y -= 2;
            }
            case PdfDocument.TextBlock text ->
                    paragraph(text.text(), false, style.bodySize(), style.textColor(), side(text.align()));
            case PdfDocument.SpaceBlock space -> gap(space.points());
            case PdfDocument.FieldsBlock fields -> fields(fields.fields());
            case PdfDocument.TableBlock table -> {
                gap(BLOCK_GAP);
                table(table.table());
            }
            case PdfDocument.SummaryBlock summary -> summary(summary.lines());
        }
    }

    // ===================== الصفحات =====================

    private void newPage() throws IOException {
        if (stream != null) {
            stream.close();
        }
        PDPage page = new PDPage(new PDRectangle(paper.width(), paper.height()));
        pdf.addPage(page);
        stream = new PDPageContentStream(pdf, page);
        y = top;
    }

    private boolean atTop() {
        return y >= top - 0.5f;
    }

    /** يفتح صفحة جديدة لو {@code height} ما يكفيهاش الباقي — إلا لو إحنا أصلًا في أول الصفحة. */
    private void need(float height) throws IOException {
        if (y - height < bottom && !atTop()) {
            newPage();
        }
    }

    /** مسافة رأسية، إلا في أول الصفحة. */
    private void gap(float points) {
        if (!atTop()) {
            y -= points;
        }
    }

    // ===================== النص =====================

    /** الجهة الفعلية على الورقة لمحاذاة منسوبة لاتجاه المستند. */
    private Side side(Align align) {
        return switch (align) {
            case CENTER -> Side.CENTER;
            case START -> rtl ? Side.RIGHT : Side.LEFT;
            case END -> rtl ? Side.LEFT : Side.RIGHT;
        };
    }

    /** محارف التحكم (tab وغيره) لمسافات؛ السطر الجديد بيفضل، لأنه كسر سطر مقصود. */
    private static String clean(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\r') {
                continue;
            }
            out.append(c != '\n' && Character.isISOControl(c) ? ' ' : c);
        }
        return out.toString();
    }

    /**
     * سطر منطقي واحد جاهزًا للرسم: متشكّلًا ومترتبًا بترتيب العرض في اتجاه النص اللي اتقطع منه،
     * ومقسّمًا على الخطوط. مع Helvetica المدمج مفيش تشكيل: الخط ما بيرسمش غير Latin-1 أصلًا.
     */
    private Line line(String logical, String paragraph, boolean bold, float size) throws IOException, ReportException {
        String visual = fonts.unicode() ? ArabicTextShaper.forDisplay(logical, paragraph, rtl) : logical;
        List<FontSet.Run> runs = fonts.runs(visual, bold);
        return new Line(runs, FontSet.width(runs, size));
    }

    private float measure(String logical, String paragraph, boolean bold, float size) throws IOException, ReportException {
        return line(logical, paragraph, bold, size).width();
    }

    /**
     * النص مكسورًا للسطور اللي تكفيها {@code maxWidth}. الكسر على النص المنطقي عند مسافاته، وكل سطر
     * بيتشكّل لوحده؛ الكلمة الأطول من العرض كله بتتكسر على حروفها. {@code maxWidth} صفر أو أقل
     * معناه بلا كسر غير السطور الجديدة المكتوبة.
     */
    private List<Line> wrap(String text, boolean bold, float size, float maxWidth) throws IOException, ReportException {
        String logical = clean(text);
        List<Line> lines = new ArrayList<>(1);
        for (String hard : logical.split("\n", -1)) {
            Line whole = line(hard, logical, bold, size);
            if (maxWidth <= 0 || whole.width() <= maxWidth) {
                lines.add(whole);
                continue;
            }
            StringBuilder current = new StringBuilder();
            for (String word : hard.strip().split(" +")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (measure(candidate, logical, bold, size) <= maxWidth) {
                    current.setLength(0);
                    current.append(candidate);
                    continue;
                }
                if (!current.isEmpty()) {
                    lines.add(line(current.toString(), logical, bold, size));
                    current.setLength(0);
                }
                String rest = word;
                while (measure(rest, logical, bold, size) > maxWidth) {
                    int cut = fittingPrefix(rest, logical, bold, size, maxWidth);
                    lines.add(line(rest.substring(0, cut), logical, bold, size));
                    rest = rest.substring(cut);
                }
                current.append(rest);
            }
            if (!current.isEmpty()) {
                lines.add(line(current.toString(), logical, bold, size));
            }
        }
        return lines;
    }

    /** أطول بداية من {@code word} تكفيها {@code maxWidth}، وحرف واحد على الأقل. */
    private int fittingPrefix(String word, String paragraph, boolean bold, float size, float maxWidth)
            throws IOException, ReportException {
        int[] ends = word.codePoints().map(Character::charCount).toArray();
        int low = 1;
        int high = ends.length;
        while (low < high) {
            int middle = (low + high + 1) / 2;
            if (measure(word.substring(0, offset(ends, middle)), paragraph, bold, size) <= maxWidth) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return offset(ends, low);
    }

    private static int offset(int[] charCounts, int codePoints) {
        int offset = 0;
        for (int i = 0; i < codePoints; i++) {
            offset += charCounts[i];
        }
        return offset;
    }

    /** فقرة على عرض الصفحة، بتكمل على الصفحة الجاية سطرًا سطرًا. */
    private void paragraph(String text, boolean bold, float size, int color, Side side)
            throws IOException, ReportException {
        if (text == null || text.isBlank()) {
            return;
        }
        float leading = size * LEADING;
        for (Line line : wrap(text, bold, size, width)) {
            need(leading);
            drawLine(line, size, color, left, width, side, y - size * BASELINE);
            y -= leading;
        }
    }

    private void drawLine(Line line, float size, int color, float boxLeft, float boxWidth, Side side, float baseline)
            throws IOException {
        float x = switch (side) {
            case LEFT -> boxLeft;
            case CENTER -> boxLeft + (boxWidth - line.width()) / 2;
            case RIGHT -> boxLeft + boxWidth - line.width();
        };
        for (FontSet.Run run : line.runs()) {
            stream.beginText();
            stream.setFont(run.face().font(), size);
            setFill(color);
            if (run.fakeBold()) {
                // تسميك مصطنع: الحرف بيتملى ويتحدد بنفس لونه.
                stream.setRenderingMode(RenderingMode.FILL_STROKE);
                setStroke(color);
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

    // ===================== الترويسة والتذييل =====================

    private void letterhead() throws IOException, ReportException {
        String name = document.letterheadName();
        List<String> lines = document.letterheadLines();
        byte[] logo = document.logoBytes();
        if (name.isBlank() && lines.isEmpty() && logo == null) {
            return;
        }
        float startY = y;
        float logoBottom = y;
        if (logo != null) {
            PDImageXObject image;
            try {
                image = PDImageXObject.createFromByteArray(pdf, logo, "logo");
            } catch (IllegalArgumentException e) {
                throw new ReportException("The logo is not an image PDFBox can read (PNG or JPEG expected).", e);
            }
            float scale = Math.min(LOGO_HEIGHT / image.getHeight(), (width * 0.3f) / image.getWidth());
            float imageWidth = image.getWidth() * scale;
            float imageHeight = image.getHeight() * scale;
            // الشعار في الجهة اللي الصفحة بتبدأ منها.
            stream.drawImage(image, rtl ? right - imageWidth : left, y - imageHeight, imageWidth, imageHeight);
            logoBottom = y - imageHeight;
        }
        paragraph(name, true, style.titleSize() + 2, style.textColor(), Side.CENTER);
        for (String line : lines) {
            paragraph(line, false, style.smallSize(), style.mutedColor(), Side.CENTER);
        }
        // لو الترويسة كملت على صفحة جديدة (ما بيحصلش عمليًا) الشعار ما بيتحسبش.
        if (y <= startY) {
            y = Math.min(y, logoBottom);
        }
        y -= 4;
        rule(left, right, y, style.accentColor(), 1f);
        y -= 8;
    }

    private void footer() throws IOException, ReportException {
        if (document.footerText().isBlank()) {
            return;
        }
        gap(BLOCK_GAP + 4);
        paragraph(document.footerText(), false, style.smallSize(), style.mutedColor(), Side.CENTER);
    }

    /**
     * رقم كل صفحة في منتصف هامشها السفلي. بيتكتب في الآخر لأنه بيقول عدد الصفحات كلها، وده ما
     * بيتعرفش غير بعد رسم آخر صفحة.
     */
    private void numberPages() throws IOException, ReportException {
        int pages = pdf.getNumberOfPages();
        PageNumbers mode = document.pageNumberMode();
        if (mode == PageNumbers.NONE || mode == PageNumbers.WHEN_SEVERAL && pages < 2) {
            return;
        }
        float size = style.smallSize();
        for (int number = 1; number <= pages; number++) {
            String text = rtl ? "صفحة " + number + " من " + pages : "Page " + number + " of " + pages;
            Line line = line(text, text, false, size);
            stream = new PDPageContentStream(pdf, pdf.getPage(number - 1),
                    PDPageContentStream.AppendMode.APPEND, true, true);
            try {
                drawLine(line, size, style.mutedColor(), left, width, Side.CENTER, style.margin() * 0.55f);
            } finally {
                stream.close();
            }
        }
    }

    // ===================== البيانات والملخص =====================

    /** بيانان في السطر: الأول عند بداية السطر والتاني في نصف الصفحة. */
    private void fields(List<String[]> fields) throws IOException, ReportException {
        int perRow = 2;
        float size = style.bodySize();
        float leading = size * LEADING;
        float gutter = 12f;
        float cell = (width - gutter * (perRow - 1)) / perRow;
        for (int start = 0; start < fields.size(); start += perRow) {
            List<List<Line>> row = new ArrayList<>(perRow);
            int tallest = 1;
            for (int i = start; i < Math.min(start + perRow, fields.size()); i++) {
                String[] field = fields.get(i);
                String text = field[0].isBlank() ? field[1] : field[0] + ": " + field[1];
                List<Line> lines = wrap(text, false, size, cell);
                tallest = Math.max(tallest, lines.size());
                row.add(lines);
            }
            need(tallest * leading);
            for (int column = 0; column < row.size(); column++) {
                float offset = column * (cell + gutter);
                float boxLeft = rtl ? right - offset - cell : left + offset;
                List<Line> lines = row.get(column);
                for (int k = 0; k < lines.size(); k++) {
                    drawLine(lines.get(k), size, style.textColor(), boxLeft, cell, side(Align.START),
                            y - k * leading - size * BASELINE);
                }
            }
            y -= tallest * leading + 1;
        }
    }

    /** الملخص: جدول بلا رأس من عمودين، وسطر الإجمالي فيه بخط أكبر. */
    private void summary(List<PdfDocument.SummaryLine> lines) throws IOException, ReportException {
        // عمود القيمة بعرض أطول قيمة فيه، فالرقم ما يلتفش على سطرين — بين 30% و55% من العرض.
        float widestValue = 0;
        for (PdfDocument.SummaryLine line : lines) {
            float size = style.bodySize() + (line.total() ? 2f : 0f);
            String value = clean(line.value()).replace('\n', ' ');
            widestValue = Math.max(widestValue, measure(value, value, line.total(), size));
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
        gap(BLOCK_GAP);
        // الملخص قصير: لو ما كفّاهوش الباقي بيتنقل كله للصفحة الجاية بدل ما يتقسم.
        float estimate = lines.size() * (style.bodySize() * LEADING + 2 * PAD_Y) + 4;
        if (estimate < top - bottom) {
            need(estimate);
        }
        table(table);
    }

    // ===================== الجدول =====================

    private void table(PdfTable table) throws IOException, ReportException {
        List<PdfTable.Column> columns = table.columns();
        int count = columns.size();
        if (count == 0) {
            return;
        }
        float bodySize = style.bodySize();
        float totalSize = bodySize + table.totalSizeBoost();
        float headerSize = style.headerSize();

        String[] headers = new String[count];
        float[] weights = new float[count];
        for (int i = 0; i < count; i++) {
            headers[i] = columns.get(i).header();
            weights[i] = columns.get(i).weight();
        }

        // أضيق عرض لكل عمود: أطول كلمة فيه بالخط والحجم اللي هتترسم بيهم.
        float[] narrowest = new float[count];
        if (table.headerShown()) {
            widen(narrowest, headers, true, headerSize);
        }
        for (PdfTable.Row row : table.rows()) {
            widen(narrowest, row.cells(), row.total(), row.total() ? totalSize : bodySize);
        }
        float[] widths = ColumnFit.fit(weights, narrowest, width);

        // مكان كل عمود منطقي على الورقة: الأول عند بداية السطر.
        float[] x = new float[count];
        float cursor = rtl ? right : left;
        for (int i = 0; i < count; i++) {
            if (rtl) {
                cursor -= widths[i];
                x[i] = cursor;
            } else {
                x[i] = cursor;
                cursor += widths[i];
            }
        }

        RowLayout header = table.headerShown() ? layout(headers, widths, true, headerSize) : null;
        boolean headerDrawn = false;
        int index = 0;
        for (PdfTable.Row row : table.rows()) {
            RowLayout layout = layout(row.cells(), widths, row.total(), row.total() ? totalSize : bodySize);
            float needed = layout.height() + (headerDrawn || header == null ? 0 : header.height());
            if (y - needed < bottom && !atTop()) {
                newPage();
                headerDrawn = false;
            }
            if (!headerDrawn && header != null) {
                paint(header, columns, x, widths, RowKind.HEADER, false);
            }
            headerDrawn = true;
            paint(layout, columns, x, widths, row.total() ? RowKind.TOTAL : RowKind.NORMAL,
                    style.stripes() && index % 2 == 1);
            if (!row.total()) {
                index++;
            }
        }
        if (!headerDrawn && header != null) {
            // جدول بلا صفوف: رأسه بيترسم برضه، عشان الورقة تقول إن الجدول فاضي.
            need(header.height());
            paint(header, columns, x, widths, RowKind.HEADER, false);
        }
    }

    /** يرفع أضيق عرض لكل عمود لعرض أطول كلمة في خليته مع حشوتها، بحد أقصى نصف عرض الجدول. */
    private void widen(float[] narrowest, String[] cells, boolean bold, float size) throws IOException, ReportException {
        for (int i = 0; i < Math.min(cells.length, narrowest.length); i++) {
            float word = widestWord(cells[i], bold, size);
            if (word > 0) {
                narrowest[i] = Math.max(narrowest[i], Math.min(word + 2 * PAD_X + SLACK, width / 2));
            }
        }
    }

    private float widestWord(String text, boolean bold, float size) throws IOException, ReportException {
        if (text == null || text.isBlank()) {
            return 0;
        }
        float widest = 0;
        for (String word : clean(text).strip().split("[ \n]+")) {
            if (word.isEmpty()) {
                continue;
            }
            String key = (bold ? "b" : "r") + size + '|' + word;
            Float known = wordWidths.get(key);
            if (known == null) {
                known = measure(word, word, bold, size);
                wordWidths.put(key, known);
            }
            widest = Math.max(widest, known);
        }
        return widest;
    }

    private RowLayout layout(String[] cells, float[] widths, boolean bold, float size) throws IOException, ReportException {
        List<List<Line>> lines = new ArrayList<>(cells.length);
        int tallest = 1;
        for (int i = 0; i < cells.length; i++) {
            List<Line> cell = wrap(cells[i], bold, size, widths[i] - 2 * PAD_X - SLACK);
            tallest = Math.max(tallest, cell.size());
            lines.add(cell);
        }
        return new RowLayout(lines, tallest * size * LEADING + 2 * PAD_Y, size);
    }

    private void paint(RowLayout row, List<PdfTable.Column> columns, float[] x, float[] widths, RowKind kind,
                       boolean striped) throws IOException {
        float tableLeft = rtl ? x[x.length - 1] : x[0];
        float tableWidth = 0;
        for (float w : widths) {
            tableWidth += w;
        }
        float height = row.height();
        float leading = row.size() * LEADING;

        int fill = switch (kind) {
            case HEADER -> style.headerFill();
            case TOTAL -> style.totalsFill();
            case NORMAL -> striped ? style.stripeFill() : PdfStyle.WHITE;
        };
        if (fill != PdfStyle.WHITE) {
            setFill(fill);
            stream.addRect(tableLeft, y - height, tableWidth, height);
            stream.fill();
        }

        int color = switch (kind) {
            case HEADER -> style.headerText();
            case TOTAL -> style.totalsText();
            case NORMAL -> style.textColor();
        };
        for (int i = 0; i < row.cells().size(); i++) {
            List<Line> lines = row.cells().get(i);
            Side side = kind == RowKind.HEADER ? Side.CENTER : side(columns.get(i).align());
            // الخلية الأقصر من صفها بتتوسّط رأسيًا.
            float firstTop = y - PAD_Y - (height - 2 * PAD_Y - lines.size() * leading) / 2;
            for (int k = 0; k < lines.size(); k++) {
                drawLine(lines.get(k), row.size(), color, x[i] + PAD_X, widths[i] - 2 * PAD_X, side,
                        firstTop - k * leading - row.size() * BASELINE);
            }
        }

        if (style.grid()) {
            setStroke(style.ruleColor());
            stream.setLineWidth(GRID_WIDTH);
            for (int i = 0; i < widths.length; i++) {
                stream.addRect(x[i], y - height, widths[i], height);
            }
            stream.stroke();
        } else {
            rule(tableLeft, tableLeft + tableWidth, y - height, style.ruleColor(), GRID_WIDTH);
        }
        // بلا تعبئة (توفير الحبر): الرأس بخط سميك تحته، والإجمالي بخطين فوقه وتحته.
        if (fill == PdfStyle.WHITE && kind != RowKind.NORMAL) {
            rule(tableLeft, tableLeft + tableWidth, y - height, color, 1.2f);
            if (kind == RowKind.TOTAL) {
                rule(tableLeft, tableLeft + tableWidth, y, color, 1.2f);
            }
        }
        y -= height;
    }

    // ===================== الرسم =====================

    private void rule(float x1, float x2, float atY, int color, float lineWidth) throws IOException {
        setStroke(color);
        stream.setLineWidth(lineWidth);
        stream.moveTo(x1, atY);
        stream.lineTo(x2, atY);
        stream.stroke();
    }

    private void setFill(int rgb) throws IOException {
        stream.setNonStrokingColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
    }

    private void setStroke(int rgb) throws IOException {
        stream.setStrokingColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
    }
}
