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
    /** المكان الفاضي للتوقيع بين عنوان الخانة وخطها. */
    private static final float SIGNATURE_SPACE = 34f;
    private static final float POINTS_PER_MM = 72f / 25.4f;
    /** أضيق خط في الباركود: 0.33 مم، حوالي 3 نقط على الطابعة الحرارية (203 نقطة في البوصة). */
    static final float BAR_MODULE = 0.33f * POINTS_PER_MM;
    /** أضيق خط يتقري: 0.25 مم، نقطتين على الطابعة الحرارية. أضيق من كده الباركود بيفشل بدل ما يطلع بايظ. */
    static final float MIN_BAR_MODULE = 0.25f * POINTS_PER_MM;
    /** المسافة بين الخطوط والنص المكتوب تحتها. */
    private static final float BARCODE_CAPTION_GAP = 2f;

    private enum Side { LEFT, CENTER, RIGHT }

    private enum RowKind { HEADER, NORMAL, TOTAL }

    /** سطر جاهز للرسم: مقاطعه بترتيب الرسم وعرضه. */
    private record Line(List<FontSet.Run> runs, float width) {
    }

    /** صف جدول اتقاس: سطور كل خلية وارتفاع الصف. */
    private record RowLayout(List<List<Line>> cells, float height, float size, boolean split) {
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
    /** آخر نقطة للمحتوى من تحت: الهامش، ومكان رقم الصفحة وتذييل الصفحات لو موجودين. */
    private float bottom;
    /** عرض الكلمة مرسومة، بالوجه والحجم والكلمة: التقرير بيكرر كلماته في كل صف. */
    private final Map<String, Float> wordWidths = new HashMap<>();
    /** تذييل الصفحات متكسّرًا لسطوره، بيترسم في الآخر مع أرقام الصفحات. */
    private List<Line> pageFooter = List.of();

    private PDPageContentStream stream;
    private float y;
    private int pages;
    /** أول مكان للمحتوى في الصفحة الحالية: تحت الترويسة لو اترسمت فيها. */
    private float pageTop;
    /** أول مكان للمحتوى في أي صفحة جديدة؛ منه بيتحسب «هل الجزء ده يكفيه صفحة كاملة؟». */
    private float freshTop;
    /** الترويسة بتترسم دلوقتي: مفيش فتح صفحات من جوّاها. */
    private boolean inLetterhead;
    /**
     * أوطى نقطة اتحجزت في الصفحة الحالية: الجزء اللي بيحجز مكانه قبل ما يترسم (عنوان القسم بيحجز لنفسه
     * ولرأس جدول وصف بعده) ممكن يحجز أكتر من اللي رسمه، والورقة الحرارية لازم تكفّي الحجز ده كمان.
     */
    private float lowestReserved = Float.MAX_VALUE;
    /**
     * مجموعة خانات التوقيع اتنقلت لصفحة جديدة دلوقتي: أول جزء فيها بيبدأ من أعلى الصفحة من غير المسافة
     * اللي قبله، زي أي جزء بيتنقل لوحده.
     */
    private boolean groupMoved;
    /**
     * المكان اللي آخر سطر في الفقرة الحالية بيحجزه بعده: خانات التوقيع اللي بعدها على طول. لو مجموعة الخانات
     * ما اتنقلتش كلها (أطول من صفحة، أو مفيش فوقها غير ترويسة أول صفحة)، آخر سطر بينزل مع الخانات بدل ما
     * تنزل هي لوحدها.
     */
    private float lastLineReserve;

    /** @param page الورقة اللي الصفحات بتترسم عليها؛ في الورق الحراري ارتفاعها بيتحدد من المحتوى */
    PageWriter(PDDocument pdf, PdfDocument document, FontSet fonts, Paper page) {
        this.pdf = pdf;
        this.document = document;
        this.fonts = fonts;
        this.style = document.resolvedStyle();
        this.paper = page;
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
        if (!document.pageFooterText().isBlank()) {
            pageFooter = wrap(document.pageFooterText(), false, style.smallSize(), width);
            bottom += pageFooter.size() * style.smallSize() * LEADING;
            if (top - bottom < 60) {
                throw new IllegalArgumentException("The page footer leaves no room on the page for its content.");
            }
        }
        newPage();
        try {
            List<PdfDocument.Block> blocks = document.blocks();
            // الورق الحراري صفحة واحدة بطول المحتوى: مفيش آخر صفحة يتنقل منها حاجة.
            Map<Integer, Integer> kept = paper.roll() ? Map.of() : signatureGroups(blocks);
            for (int i = 0; i < blocks.size(); i++) {
                Integer last = kept.get(i);
                if (last != null) {
                    keepTogether(blocks.subList(i, last + 1));
                }
                lastLineReserve = paper.roll() ? 0 : signaturesAfter(blocks, i);
                draw(blocks.get(i));
                groupMoved = false;
                lastLineReserve = 0;
            }
            footer();
        } finally {
            stream.close();
        }
        numberPages();
    }

    /**
     * الارتفاع اللي المحتوى احتاجه، لورقة حرارية بتتقص على قد المكتوب: من أعلى الورقة لآخر سطر،
     * والهامش ومكان رقم الصفحة والتذييل تحته. {@code -1} لو المحتوى ما كفّتهوش صفحة واحدة.
     */
    float usedHeight() {
        return pages == 1 ? paper.height() - Math.min(y, lowestReserved) + bottom : -1;
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
            case PdfDocument.SignaturesBlock signatures -> signatures(signatures.labels());
            case PdfDocument.QrBlock qr -> qrCode(qr);
            case PdfDocument.BarcodeBlock barcode -> barcode(barcode);
        }
    }

    // ===================== الصفحات =====================

    /**
     * صفحة جديدة، والترويسة في أعلاها لو دي أول صفحة أو لو المستند طالبها في كل صفحة. بعدها
     * {@link #atTop()} بيقيس من تحت الترويسة، فالجزء اللي ما كفّاهوش الباقي ما يفتحش صفحة ورا صفحة.
     */
    private void newPage() throws IOException, ReportException {
        if (stream != null) {
            stream.close();
        }
        PDPage page = new PDPage(new PDRectangle(paper.width(), paper.height()));
        pdf.addPage(page);
        stream = new PDPageContentStream(pdf, page);
        y = top;
        lowestReserved = Float.MAX_VALUE;
        pages++;
        if (pages == 1 || document.letterheadOnEveryPage()) {
            inLetterhead = true;
            try {
                letterhead();
            } finally {
                inLetterhead = false;
            }
        }
        pageTop = y;
        if (pages == 1) {
            freshTop = document.letterheadOnEveryPage() ? pageTop : top;
        }
    }

    private boolean atTop() {
        return y >= pageTop - 0.5f;
    }

    /** المكان اللي صفحة جديدة بتديه للمحتوى. */
    private float freshRoom() {
        return freshTop - bottom;
    }

    /** يفتح صفحة جديدة لو {@code height} ما يكفيهاش الباقي — إلا لو إحنا أصلًا في أول الصفحة. */
    private void need(float height) throws IOException, ReportException {
        if (!inLetterhead && y - height < bottom && !atTop()) {
            newPage();
        }
        if (!inLetterhead) {
            lowestReserved = Math.min(lowestReserved, y - height);
        }
    }

    /**
     * الأجزاء اللي بتتشال مع خانات التوقيع: الخانات، والملخص اللي قبلها على طول، وأي نص أو مسافة بينهم (زي
     * التفقيط تحت الإجمالي). المفتاح أول جزء في المجموعة والقيمة مكان الخانات. خانات بعد جدول أو أي جزء
     * تاني على طول ما لهاش مجموعة، والمسافة في أول المجموعة مش منها.
     */
    private static Map<Integer, Integer> signatureGroups(List<PdfDocument.Block> blocks) {
        Map<Integer, Integer> groups = new HashMap<>();
        for (int last = 0; last < blocks.size(); last++) {
            if (!(blocks.get(last) instanceof PdfDocument.SignaturesBlock)) {
                continue;
            }
            int first = last;
            while (first > 0 && (blocks.get(first - 1) instanceof PdfDocument.TextBlock
                    || blocks.get(first - 1) instanceof PdfDocument.SpaceBlock)) {
                first--;
            }
            if (first > 0 && blocks.get(first - 1) instanceof PdfDocument.SummaryBlock) {
                first--;
            }
            while (first < last && blocks.get(first) instanceof PdfDocument.SpaceBlock) {
                first++;
            }
            if (first < last) {
                groups.put(first, last);
            }
        }
        return groups;
    }

    /**
     * صفحة جديدة قبل المجموعة لو ما كفّاهاش الباقي وتكفيها صفحة جديدة: خانات التوقيع ما تنزلش لوحدها في
     * صفحة من غير الأرقام اللي بيتوقّع عليها. المجموعة الأطول من صفحة بتترسم زي ما كانت.
     */
    private void keepTogether(List<PdfDocument.Block> group) throws IOException, ReportException {
        float height = 0;
        for (PdfDocument.Block block : group) {
            height += switch (block) {
                case PdfDocument.SummaryBlock summary -> BLOCK_GAP + summaryHeight(summary.lines());
                case PdfDocument.TextBlock text -> textHeight(text.text());
                case PdfDocument.SpaceBlock space -> space.points();
                case PdfDocument.SignaturesBlock signatures -> signaturesBlockHeight(signatures.labels());
                default -> 0;
            };
        }
        if (height <= freshRoom()) {
            int before = pages;
            need(height);
            groupMoved = pages > before;
        }
    }

    /**
     * لو الجزء {@code i} نص وبعده على طول (أو بعد مسافات بس) خانات توقيع: ارتفاع المسافات والخانات اللي آخر
     * سطر فيه بيحجزه. غير كده صفر.
     */
    private float signaturesAfter(List<PdfDocument.Block> blocks, int i) throws IOException, ReportException {
        if (!(blocks.get(i) instanceof PdfDocument.TextBlock)) {
            return 0;
        }
        float space = 0;
        int next = i + 1;
        while (next < blocks.size() && blocks.get(next) instanceof PdfDocument.SpaceBlock blank) {
            space += blank.points();
            next++;
        }
        return next < blocks.size() && blocks.get(next) instanceof PdfDocument.SignaturesBlock signatures
                ? space + signaturesBlockHeight(signatures.labels()) : 0;
    }

    /** الخانات بالمسافة اللي قبلها، زي ما {@link #signatures} هيرسمها. */
    private float signaturesBlockHeight(List<String> labels) throws IOException, ReportException {
        return labels.isEmpty() ? 0 : BLOCK_GAP * 2 + signaturesHeight(signatureLabels(labels)) + 2;
    }

    /** مسافة رأسية، إلا في أعلى الورقة نفسها (تحت الترويسة المسافة بتفضل، زي ما كانت في أول صفحة). */
    private void gap(float points) {
        if (y < top - 0.5f && !groupMoved) {
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
        List<FontSet.Run> runs = fonts.runs(visual, bold, paragraph == null ? logical : paragraph);
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

    /** ارتفاع فقرة {@link PdfDocument.TextBlock} زي ما {@link #paragraph} هيرسمها. */
    private float textHeight(String text) throws IOException, ReportException {
        if (text == null || text.isBlank()) {
            return 0;
        }
        float size = style.bodySize();
        return wrap(text, false, size, width).size() * size * LEADING;
    }

    /** فقرة على عرض الصفحة، بتكمل على الصفحة الجاية سطرًا سطرًا. */
    private void paragraph(String text, boolean bold, float size, int color, Side side)
            throws IOException, ReportException {
        if (text == null || text.isBlank()) {
            return;
        }
        float leading = size * LEADING;
        List<Line> lines = wrap(text, bold, size, width);
        for (int k = 0; k < lines.size(); k++) {
            need(leading + (k == lines.size() - 1 ? lastLineReserve : 0));
            drawLine(lines.get(k), size, color, left, width, side, y - size * BASELINE);
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
     * رقم كل صفحة في منتصف هامشها السفلي، وتذييل الصفحات فوقه. بيتكتبوا في الآخر لأن الرقم بيقول عدد
     * الصفحات كلها، وده ما بيتعرفش غير بعد رسم آخر صفحة.
     */
    private void numberPages() throws IOException, ReportException {
        int count = pdf.getNumberOfPages();
        PageNumbers mode = document.pageNumberMode();
        boolean numbered = mode == PageNumbers.ALWAYS || mode == PageNumbers.WHEN_SEVERAL && count >= 2;
        if (!numbered && pageFooter.isEmpty()) {
            return;
        }
        float size = style.smallSize();
        float leading = size * LEADING;
        float numberBaseline = style.margin() * 0.55f;
        // التذييل فوق مكان الرقم المحجوز (حتى لو الرقم ما اتكتبش)، فبيقف في نفس المكان في كل مستند.
        float footerBaseline = numberBaseline + (mode == PageNumbers.NONE ? 0 : leading)
                + (pageFooter.size() - 1) * leading;
        for (int number = 1; number <= count; number++) {
            stream = new PDPageContentStream(pdf, pdf.getPage(number - 1),
                    PDPageContentStream.AppendMode.APPEND, true, true);
            try {
                if (numbered) {
                    String text = rtl ? "صفحة " + number + " من " + count : "Page " + number + " of " + count;
                    drawLine(line(text, text, false, size), size, style.mutedColor(), left, width, Side.CENTER,
                            numberBaseline);
                }
                for (int k = 0; k < pageFooter.size(); k++) {
                    drawLine(pageFooter.get(k), size, style.mutedColor(), left, width, Side.CENTER,
                            footerBaseline - k * leading);
                }
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
            // البيانان بيفضلوا مع بعض لو صفحة تكفيهم؛ البيان الأطول من صفحة بيكمل سطرًا سطرًا على اللي
            // بعدها — كان بيترسم كله من مكانه ويخرج عن الورقة.
            if (tallest * leading <= freshRoom()) {
                need(tallest * leading);
            }
            for (int k = 0; k < tallest; k++) {
                need(leading);
                for (int column = 0; column < row.size(); column++) {
                    List<Line> lines = row.get(column);
                    if (k >= lines.size()) {
                        continue;
                    }
                    float offset = column * (cell + gutter);
                    float boxLeft = rtl ? right - offset - cell : left + offset;
                    drawLine(lines.get(k), size, style.textColor(), boxLeft, cell, side(Align.START),
                            y - size * BASELINE);
                }
                y -= leading;
            }
            y -= 1;
        }
    }

    /**
     * خانات التوقيع: عنوان كل خانة («المستلم»، «المحاسب») وتحته مكان للتوقيع وخط. الخانات بالعرض
     * بترتيب القراءة — الأولى عند بداية السطر — ومع بعض في نفس الصفحة.
     */
    private void signatures(List<String> labels) throws IOException, ReportException {
        int count = labels.size();
        if (count == 0) {
            return;
        }
        float size = style.bodySize();
        float leading = size * LEADING;
        float cell = width / count;
        float inset = signatureInset(cell);
        List<List<Line>> wrapped = signatureLabels(labels);
        float height = signaturesHeight(wrapped);
        gap(BLOCK_GAP * 2);
        need(height + 2);
        for (int i = 0; i < count; i++) {
            float boxLeft = rtl ? right - (i + 1) * cell : left + i * cell;
            List<Line> lines = wrapped.get(i);
            for (int k = 0; k < lines.size(); k++) {
                drawLine(lines.get(k), size, style.textColor(), boxLeft, cell, Side.CENTER,
                        y - k * leading - size * BASELINE);
            }
            rule(boxLeft + inset, boxLeft + cell - inset, y - height, style.textColor(), 0.6f);
        }
        y -= height + 2;
    }

    /** المسافة بين خط التوقيع وحافتي خانته. */
    private static float signatureInset(float cell) {
        return Math.min(12f, cell * 0.1f);
    }

    /** عنوان كل خانة مكسورًا على عرضها. */
    private List<List<Line>> signatureLabels(List<String> labels) throws IOException, ReportException {
        float cell = width / labels.size();
        float inset = signatureInset(cell);
        List<List<Line>> wrapped = new ArrayList<>(labels.size());
        for (String label : labels) {
            wrapped.add(wrap(label, true, style.bodySize(), cell - 2 * inset));
        }
        return wrapped;
    }

    /** ارتفاع الخانات من غير المسافة اللي قبلها: أطول عنوان ومكان التوقيع تحته. */
    private float signaturesHeight(List<List<Line>> wrapped) {
        int tallest = 1;
        for (List<Line> lines : wrapped) {
            tallest = Math.max(tallest, lines.size());
        }
        return tallest * style.bodySize() * LEADING + SIGNATURE_SPACE;
    }

    /**
     * رمز QR بمنطقته الفاضية، كله في صفحة واحدة. المنطقة الفاضية جزء من مكانه، فمفيش حاجة قبله أو بعده
     * بتلمسه. لو هو ومنطقته أعرض من السطر أو أطول من صفحة جديدة بيصغر لحد ما يكفي.
     *
     * <p>المربعات الغامقة المتجاورة في الصف بتترسم مستطيلًا واحدًا، والكل مسار واحد بيتملى مرة واحدة: مربعات
     * منفصلة بتسيب خطوط رفيعة فاتحة بينها في بعض البرامج وعلى بعض الطابعات.</p>
     */
    private void qrCode(PdfDocument.QrBlock qr) throws IOException, ReportException {
        QrSymbol symbol = qr.symbol();
        int modules = symbol.size();
        int span = modules + 2 * QrSymbol.QUIET_ZONE;
        float module = Math.min(qr.size() / modules, Math.min(width, freshRoom()) / span);
        float side = span * module;
        gap(BLOCK_GAP);
        need(side);
        float boxLeft = switch (side(qr.align())) {
            case LEFT -> left;
            case CENTER -> left + (width - side) / 2;
            case RIGHT -> right - side;
        };
        float originX = boxLeft + QrSymbol.QUIET_ZONE * module;
        float originY = y - QrSymbol.QUIET_ZONE * module;
        // أسود مهما كان لون النص: القارئ محتاج أعلى تباين.
        setFill(0x000000);
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
                stream.addRect(originX + start * module, originY - (row + 1) * module, (column - start) * module,
                        module);
            }
        }
        stream.fill();
        y -= side;
    }

    /**
     * باركود Code 128 بمنطقته الفاضية من الجنبين، وتحته النص مكتوب؛ الاتنين في صفحة واحدة. الخطوط
     * المتجاورة بتترسم مستطيلًا واحدًا، والكل مسار واحد بيتملى مرة واحدة زي رمز QR.
     *
     * <p>النص بيترسم من الشمال لليمين زي ما هو، من غير ترتيب الاتجاه العربي: الكود حروف وأرقام بترتيبها،
     * و«2026-0042-SHP» في سطر عربي كانت هتتقلب أجزاؤها. النص اللي أعرض من السطر (خط كبير في
     * {@link PdfStyle} وكود طويل) بيصغر لحد ما يكفي، لحد أصغر خط؛ ولو لسه أعرض بيكمل على سطر تاني محسوب
     * في مكان الباركود. وهو في منتصف الخطوط، من غير ما يخرج برّه الهامش.</p>
     */
    private void barcode(PdfDocument.BarcodeBlock barcode) throws IOException, ReportException {
        BarcodeSymbol symbol = barcode.symbol();
        int span = symbol.modules() + 2 * BarcodeSymbol.QUIET_ZONE;
        float module = Math.min(BAR_MODULE, width / span);
        if (module < MIN_BAR_MODULE * 0.999f) {
            throw new ReportException(String.format(java.util.Locale.ROOT,
                    "The barcode \"%s\" needs %.1f mm at its narrowest readable bars, but the line is %.1f mm wide."
                            + " Use a shorter code or wider paper.",
                    symbol.text(), span * MIN_BAR_MODULE / POINTS_PER_MM, width / POINTS_PER_MM));
        }
        float size = style.bodySize();
        float natural = FontSet.width(fonts.runs(symbol.text(), false), size);
        if (natural > width) {
            size = Math.max(PdfStyle.MIN_FONT_SIZE, size * width / natural);
        }
        List<Line> captions = captionLines(symbol.text(), size);
        float captionHeight = BARCODE_CAPTION_GAP + captions.size() * size * LEADING;
        float bars = Math.max(1f, Math.min(barcode.height(), freshRoom() - captionHeight));
        gap(BLOCK_GAP);
        need(bars + captionHeight);

        float barsWidth = symbol.modules() * module;
        float boxWidth = span * module;
        float boxLeft = switch (side(barcode.align())) {
            case LEFT -> left;
            case CENTER -> left + (width - boxWidth) / 2;
            case RIGHT -> right - boxWidth;
        };
        float originX = boxLeft + BarcodeSymbol.QUIET_ZONE * module;
        // أسود مهما كان لون النص: القارئ محتاج أعلى تباين.
        setFill(0x000000);
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
            stream.addRect(originX + start * module, y - bars, (column - start) * module, bars);
        }
        stream.fill();
        y -= bars + BARCODE_CAPTION_GAP;
        float centre = originX + barsWidth / 2;
        for (Line caption : captions) {
            float captionLeft = Math.clamp(centre - caption.width() / 2, left, right - caption.width());
            drawLine(caption, size, style.textColor(), captionLeft, caption.width(), Side.LEFT, y - size * BASELINE);
            y -= size * LEADING;
        }
    }

    /**
     * نص الباركود سطورًا تكفي عرض السطر بالخط {@code size}: سطر واحد في العادي، وبيتكسر على حروفه بس لو
     * أعرض من السطر حتى بأصغر خط.
     */
    private List<Line> captionLines(String text, float size) throws IOException, ReportException {
        List<Line> lines = new ArrayList<>(1);
        int start = 0;
        while (start < text.length()) {
            int end = text.length();
            while (end > start + 1 && FontSet.width(fonts.runs(text.substring(start, end), false), size) > width) {
                end--;
            }
            List<FontSet.Run> runs = fonts.runs(text.substring(start, end), false);
            lines.add(new Line(runs, FontSet.width(runs, size)));
            start = end;
        }
        return lines;
    }

    /** الملخص: جدول بلا رأس من عمودين، وسطر الإجمالي فيه بخط أكبر. */
    private void summary(List<PdfDocument.SummaryLine> lines) throws IOException, ReportException {
        PdfTable table = summaryTable(lines);
        gap(BLOCK_GAP);
        // الملخص قصير: لو ما كفّاهوش الباقي بيتنقل كله للصفحة الجاية بدل ما يتقسم.
        float estimate = summaryEstimate(lines.size());
        if (estimate < freshRoom()) {
            need(estimate);
        }
        table(table);
    }

    private float summaryEstimate(int lines) {
        return lines * (style.bodySize() * LEADING + 2 * PAD_Y) + 4;
    }

    /** ارتفاع الملخص من غير المسافة اللي قبله: صفوفه زي ما هتترسم، أو تقديره لو أكبر. */
    private float summaryHeight(List<PdfDocument.SummaryLine> lines) throws IOException, ReportException {
        return Math.max(summaryEstimate(lines.size()), rowsHeight(summaryTable(lines)));
    }

    private PdfTable summaryTable(List<PdfDocument.SummaryLine> lines) throws IOException, ReportException {
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
        return table;
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
        String[] headers = headers(table);
        float[] widths = columnWidths(table);

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

        Grid grid = new Grid(columns, x, widths);
        RowLayout header = table.headerShown() ? layout(headers, widths, true, headerSize) : null;
        // الرأس بيتكرر في أول كل صفحة لو مش واخد أكتر من تلت الصفحة. الأطول من كده بيترسم مرة واحدة
        // (ومتقسّم لو أطول من صفحة): كان بيتكرر في كل صفحة ويخرج عن الورقة أو ياكلها كلها.
        RowLayout repeated = header != null && header.height() <= freshRoom() / 3 ? header : null;
        boolean headerDrawn = false;
        if (header != null && repeated == null) {
            row(header, RowKind.HEADER, false, null, false, grid);
        }
        int index = 0;
        for (PdfTable.Row row : table.rows()) {
            RowLayout layout = layout(row.cells(), widths, row.total(), row.total() ? totalSize : bodySize);
            headerDrawn = row(layout, row.total() ? RowKind.TOTAL : RowKind.NORMAL,
                    style.stripes() && index % 2 == 1, repeated, headerDrawn, grid);
            if (!row.total()) {
                index++;
            }
        }
        if (!headerDrawn && repeated != null) {
            // جدول بلا صفوف: رأسه بيترسم برضه، عشان الورقة تقول إن الجدول فاضي.
            need(repeated.height());
            paint(repeated, grid, RowKind.HEADER, false);
        }
    }

    private static String[] headers(PdfTable table) {
        return table.columns().stream().map(PdfTable.Column::header).toArray(String[]::new);
    }

    /** عرض كل عمود: أطول كلمة فيه بالخط والحجم اللي هتترسم بيهم حد أدنى، والباقي بالأوزان. */
    private float[] columnWidths(PdfTable table) throws IOException, ReportException {
        List<PdfTable.Column> columns = table.columns();
        float bodySize = style.bodySize();
        float totalSize = bodySize + table.totalSizeBoost();
        float[] weights = new float[columns.size()];
        for (int i = 0; i < weights.length; i++) {
            weights[i] = columns.get(i).weight();
        }
        float[] narrowest = new float[columns.size()];
        if (table.headerShown()) {
            widen(narrowest, headers(table), true, style.headerSize());
        }
        for (PdfTable.Row row : table.rows()) {
            widen(narrowest, row.cells(), row.total(), row.total() ? totalSize : bodySize);
        }
        return ColumnFit.fit(weights, narrowest, width);
    }

    /** ارتفاع صفوف الجدول لو اترسمت كلها في صفحة واحدة، من غير رأسه. */
    private float rowsHeight(PdfTable table) throws IOException, ReportException {
        if (table.columns().isEmpty()) {
            return 0;
        }
        float[] widths = columnWidths(table);
        float bodySize = style.bodySize();
        float totalSize = bodySize + table.totalSizeBoost();
        float height = 0;
        for (PdfTable.Row row : table.rows()) {
            height += layout(row.cells(), widths, row.total(), row.total() ? totalSize : bodySize).height();
        }
        return height;
    }

    /** أعمدة جدول على الورقة: مكان كل عمود منطقي وعرضه. */
    private record Grid(List<PdfTable.Column> columns, float[] x, float[] widths) {
    }

    /**
     * صف واحد: بيترسم كله في مكانه لو كفّاه الباقي، وإلا في صفحة جديدة لو تكفيه. الصف الأطول من صفحة
     * كاملة بيتقسّم على سطوره: كان بيترسم تحت حافة الورقة ويضيع باقيه في الطباعة.
     *
     * @param pageHeader  رأس الجدول اللي بيترسم قبل الصف في أول كل صفحة؛ {@code null} = مفيش
     * @param headerDrawn الرأس اترسم بالفعل في الصفحة الحالية
     * @return هل الرأس مرسوم في الصفحة اللي الصف خلص فيها
     */
    private boolean row(RowLayout row, RowKind kind, boolean striped, RowLayout pageHeader, boolean headerDrawn,
                        Grid grid) throws IOException, ReportException {
        float headerHeight = pageHeader == null ? 0 : pageHeader.height();
        RowLayout rest = row;
        while (true) {
            float room = y - bottom - (headerDrawn ? 0 : headerHeight);
            if (rest.height() <= room) {
                break;
            }
            // نفس حساب room في أول صفحة جديدة، فالصف اللي تكفيه بيخرج من الفحص اللي فوق بعدها.
            boolean fitsFreshPage = rest.height() <= freshRoom() - headerHeight;
            int fitting = (int) ((room - 2 * PAD_Y) / (rest.size() * LEADING));
            if (!atTop() && (fitsFreshPage || fitting < 1)) {
                newPage();
                headerDrawn = false;
                continue;
            }
            fitting = Math.max(1, fitting);
            if (!headerDrawn && pageHeader != null) {
                paint(pageHeader, grid, RowKind.HEADER, false);
            }
            headerDrawn = true;
            paint(slice(rest, 0, fitting), grid, kind, striped);
            if (fitting >= lineCount(rest)) {
                // الرأس ما سابش في الصفحة مكان لسطر واحد: السطر اترسم غصبًا والصف خلص. من غير
                // الوقفة دي الباقي الفاضي كان بيفضل «ما يكفيهوش المكان» والحلقة ما بتنتهيش.
                return true;
            }
            rest = slice(rest, fitting, Integer.MAX_VALUE);
            newPage();
            headerDrawn = false;
        }
        if (!headerDrawn && pageHeader != null) {
            paint(pageHeader, grid, RowKind.HEADER, false);
        }
        paint(rest, grid, kind, striped);
        return true;
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
        return new RowLayout(lines, tallest * size * LEADING + 2 * PAD_Y, size, false);
    }

    /** عدد سطور أطول خلية في الصف. */
    private static int lineCount(RowLayout row) {
        int tallest = 0;
        for (List<Line> cell : row.cells()) {
            tallest = Math.max(tallest, cell.size());
        }
        return tallest;
    }

    /** السطور من {@code from} لحد {@code to} من كل خلية: جزء من صف اتقسّم على صفحتين، سطوره من أعلاه. */
    private static RowLayout slice(RowLayout row, int from, int to) {
        List<List<Line>> cells = new ArrayList<>(row.cells().size());
        int tallest = 1;
        for (List<Line> cell : row.cells()) {
            List<Line> part = from >= cell.size() ? List.of() : cell.subList(from, Math.min(to, cell.size()));
            tallest = Math.max(tallest, part.size());
            cells.add(part);
        }
        return new RowLayout(cells, tallest * row.size() * LEADING + 2 * PAD_Y, row.size(), true);
    }

    private void paint(RowLayout row, Grid grid, RowKind kind, boolean striped) throws IOException {
        List<PdfTable.Column> columns = grid.columns();
        float[] x = grid.x();
        float[] widths = grid.widths();
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
            // الخلية الأقصر من صفها بتتوسّط رأسيًا — إلا في صف اتقسّم على صفحتين، سطوره بتكمل من أعلاه.
            float firstTop = row.split() ? y - PAD_Y
                    : y - PAD_Y - (height - 2 * PAD_Y - lines.size() * leading) / 2;
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
