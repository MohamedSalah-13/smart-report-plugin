package com.smart.report.pdf;

import com.smart.report.ReportException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.smart.report.pdf.Typesetter.LEADING;

/**
 * يرسم {@link PdfDocument} على صفحاته: بيمشي من أعلى الصفحة لأسفلها، وبيفتح صفحة جديدة لما الجزء الجاي ما
 * يكفيهوش الباقي.
 *
 * <p>قواعد الصفحات كلها هنا، مكتوبة مرة واحدة على عقد {@link Part}، ومفيش فيها اسم نوع جزء واحد: كل جزء بيتقاس
 * ({@link Parts}) ويقول قطعه وطولها وهل تتقسم وفين ودوره مع خانات التوقيع، والقرار من ده ومن اللي اترسم فعلًا
 * على الورقة ({@link Canvas#marked()}).</p>
 * <ul>
 *     <li>القطعة اللي ما تكفيهاش الباقي بتتنقل لصفحة جديدة لو صفحة تكفيها، وإلا بتتقسم على قد المكان
 *         ({@link #place(Piece, Piece, float)}).</li>
 *     <li>رأس الجزء (رأس الجدول) بيترسم فوق أول قطعة في كل صفحة، ومكانه محسوب معاها.</li>
 *     <li>الترويسة في أعلى الصفحة الأولى أو كل صفحة، وتذييل الصفحات ورقمها في الهامش السفلي ومكانهم محجوز.</li>
 *     <li>العلامة المائية ({@link Watermark}) آخر حاجة في كل صفحة قبل رقمها وتذييلها، فوق المحتوى وشفافة،
 *         ومش محتوى: بتترسم بعد ما كل قرارات الصفحات اتاخدت، فما بتأثرش على أي قرار منها.</li>
 *     <li>خانات التوقيع بتتنقل مع اللي بيتوقّع عليه ({@link Join}).</li>
 *     <li>الفاصل بيفتح صفحة، والصفحة اللي فتحها بتتشال لو ما اترسمش فيها حاجة.</li>
 *     <li>الورق الحراري صفحة واحدة بطول المحتوى: بيترسم مرة عشان يتقاس، وبعدين على ورقة بالطول ده
 *         ({@link #write(PdfDocument, PdfFont, boolean, OutputStream)}).</li>
 * </ul>
 */
final class PageWriter {

    /** أضيق خط في الباركود ({@link SymbolParts#BAR_MODULE}). */
    static final float BAR_MODULE = SymbolParts.BAR_MODULE;

    private final PDDocument pdf;
    private final PdfDocument document;
    private final PdfStyle style;
    private final Paper paper;
    private final boolean rtl;
    private final float left;
    private final float right;
    private final float width;
    private final float top;
    /** آخر نقطة للمحتوى من تحت: الهامش، ومكان رقم الصفحة وتذييل الصفحات لو موجودين. */
    private float bottom;
    private final Typesetter text;
    private final Canvas canvas;
    private final Letterhead letterhead;
    /** تذييل الصفحات متكسّرًا لسطوره، بيترسم في الآخر مع أرقام الصفحات. */
    private List<Typesetter.Line> pageFooter = List.of();
    /** العلامة المائية مقاسة على الورقة، أو {@code null} = مفيش. */
    private Watermark watermark;

    private float y;
    private int pages;
    /** أول مكان للمحتوى في الصفحة الحالية: تحت الترويسة لو اترسمت فيها. */
    private float pageTop;
    /** أول مكان للمحتوى في أي صفحة جديدة؛ منه بيتحسب «هل الجزء ده يكفيه صفحة كاملة؟». */
    private float freshTop;
    /** الصفحة اللي آخر فاصل سابها، عشان لو الصفحة اللي فتحها فضلت فاضية لحد آخر جزء نرجع لها. */
    private BeforeBreak beforeBreak;
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
    /** رقم الصفحة اللي رأس الجزء الحالي اترسم فيها؛ في أي صفحة غيرها بيترسم قبل أول قطعة. */
    private int headerPage;

    /** @param page الورقة اللي الصفحات بتترسم عليها؛ في الورق الحراري ارتفاعها بيتحدد من المحتوى */
    PageWriter(PDDocument pdf, PdfDocument document, FontSet fonts, Paper page) {
        this.pdf = pdf;
        this.document = document;
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
        this.text = new Typesetter(fonts, rtl, document.arabicIndicDigits());
        this.canvas = new Canvas(pdf);
        this.letterhead = new Letterhead(document, text, style, left, right, width);
    }

    /**
     * يكتب المستند في {@code out}. الورق الحراري بيتقص على قد المكتوب: المستند بيترسم مرة على ورقة بأقصى طول
     * عشان يتقاس، وبعدين على ورقة بالطول اللي احتاجه. لو ما كفّتهوش ورقة بأقصى طول بيفضل صفحات بالطول ده.
     */
    static void write(PdfDocument document, PdfFont font, boolean strict, OutputStream out) throws ReportException {
        Paper paper = document.paper();
        if (paper.roll()) {
            float used = layOut(document, font, strict, paper, null);
            if (used > 0) {
                // نقطتين زيادة: الجزء الأخير بيقف على الحد بالظبط، وفرق تقريب صغير كان هيفتح له صفحة.
                float height = Math.max((float) Math.ceil(used) + 2, paper.width());
                // القياس بيحسب المكان المحجوز كمان، فالمحاولة الأولى بتكفي؛ ولو حاجة لسه فتحت صفحة تانية
                // الورقة بتطول بوصة وتتجرّب تاني بدل ما الإيصال يطلع على ورقتين.
                for (int attempt = 0; attempt < 4 && height < paper.height(); attempt++) {
                    ByteArrayOutputStream cut = new ByteArrayOutputStream();
                    if (layOut(document, font, strict, new Paper(paper.name(), paper.width(), height, true), cut) > 0) {
                        try {
                            cut.writeTo(out);
                        } catch (IOException e) {
                            throw new ReportException("Failed to write the PDF document: " + e.getMessage(), e);
                        }
                        return;
                    }
                    height += 72;
                }
            }
        }
        layOut(document, font, strict, paper, out);
    }

    /** يرسم المستند على {@code page} ويكتبه في {@code out} لو مش {@code null}؛ بيرجّع الطول المستخدم. */
    private static float layOut(PdfDocument document, PdfFont font, boolean strict, Paper page, OutputStream out)
            throws ReportException {
        try (PDDocument pdf = new PDDocument(); FontSet fonts = FontSet.load(pdf, font, strict)) {
            PageWriter writer = new PageWriter(pdf, document, fonts, page);
            writer.write();
            if (out != null) {
                pdf.save(out);
            }
            return writer.usedHeight();
        } catch (IOException e) {
            throw new ReportException("Failed to write the PDF document: " + e.getMessage(), e);
        }
    }

    void write() throws IOException, ReportException {
        pdf.getDocumentInformation().setCreator("smart-report-pdf");
        pdf.getDocumentInformation().setTitle(document.firstTitle());
        if (!document.pageFooterText().isBlank()) {
            pageFooter = text.wrap(document.pageFooterText(), false, style.smallSize(), width);
            bottom += pageFooter.size() * style.smallSize() * LEADING;
            if (top - bottom < 60) {
                throw new IllegalArgumentException("The page footer leaves no room on the page for its content.");
            }
        }
        watermark = Watermark.of(document.watermarkText(), document.watermarkColor(), text, paper, style.margin());
        newPage();
        try {
            Frame frame = new Frame(text, style, rtl, left, right, width, freshRoom(), paper.height(),
                    document.printerDpi());
            List<Part> parts = new ArrayList<>();
            for (PdfDocument.Block block : document.blocks()) {
                parts.addAll(Parts.of(block, frame));
            }
            // الورق الحراري صفحة واحدة بطول المحتوى: مفيش آخر صفحة يتنقل منها حاجة.
            Map<Integer, Integer> kept = paper.roll() ? Map.of() : signatureGroups(parts);
            for (int i = 0; i < parts.size(); i++) {
                Integer last = kept.get(i);
                if (last != null) {
                    keepTogether(parts.subList(i, last + 1));
                }
                place(parts.get(i), paper.roll() ? 0 : anchorBelow(parts, i));
                groupMoved = false;
            }
            dropEmptyBreakPage();
            if (!document.footerText().isBlank()) {
                place(Parts.footer(frame, document.footerText()), 0);
            }
        } finally {
            canvas.close();
        }
        finishPages();
    }

    /**
     * الارتفاع اللي المحتوى احتاجه، لورقة حرارية بتتقص على قد المكتوب: من أعلى الورقة لآخر سطر،
     * والهامش ومكان رقم الصفحة والتذييل تحته. {@code -1} لو المحتوى ما كفّتهوش صفحة واحدة.
     */
    float usedHeight() {
        return pages == 1 ? paper.height() - Math.min(y, lowestReserved) + bottom : -1;
    }

    // ===================== الجزء وقطعه =====================

    /**
     * الجزء في مكانه: الفاصل، المسافة قبله، المكان اللي بيتأكد منه، قطعه برأسه، والمسافة بعده.
     *
     * @param reserve المكان اللي آخر قطعة بتحجزه تحتها (خانات التوقيع اللي بعد النص على طول)
     */
    private void place(Part part, float reserve) throws IOException, ReportException {
        if (part.startsPage()) {
            pageBreak();
        }
        gap(part.gapBefore());
        Keep keep = part.keep();
        if (keep.height() > 0 && (!keep.onlyIfAPageHoldsIt() || keep.height() <= freshRoom())) {
            need(keep.height());
        }
        Piece header = part.header();
        headerPage = 0;
        Pieces pieces = part.pieces();
        Piece piece = pieces.next();
        while (piece != null) {
            Piece next = pieces.next();
            place(piece, header, next == null ? reserve : 0);
            piece = next;
        }
        y -= part.gapAfter();
    }

    /**
     * قطعة واحدة: بتترسم كلها في مكانها لو كفّاها الباقي، وإلا في صفحة جديدة لو تكفيها. القطعة الأطول من صفحة
     * كاملة بتتقسّم على سطورها على قد المكان؛ والقطعة اللي ما بتتقسمش بتترسم كلها في أول الصفحة الجديدة.
     *
     * @param header قطعة بتترسم قبلها في أول كل صفحة (رأس الجدول)؛ {@code null} = مفيش
     * @param extra  المكان اللي بتحجزه تحتها في نفس الصفحة
     */
    private void place(Piece piece, Piece header, float extra) throws IOException, ReportException {
        float headerHeight = header == null ? 0 : header.height();
        Piece rest = piece;
        while (true) {
            float pending = headerPage == pages ? 0 : headerHeight;
            if (!(y - (rest.height() + pending + extra) < bottom)) {
                break;
            }
            // نفس حساب الفحص اللي فوق في أول صفحة جديدة، فالقطعة اللي تكفيها بتخرج منه بعدها.
            boolean fitsFresh = rest.height() <= freshRoom() - headerHeight - extra;
            int fitting = rest.fitting(y - bottom - pending - extra);
            if (!atTop() && (fitsFresh || fitting < 1)) {
                newPage();
                continue;
            }
            fitting = Math.max(1, fitting);
            drawHeader(header);
            draw(rest.slice(0, fitting), extra);
            if (fitting >= rest.lineCount()) {
                // ما فضلش في الصفحة مكان لسطر واحد (الرأس أخده مثلًا): السطر اترسم غصبًا والقطعة خلصت. من
                // غير الوقفة دي الباقي الفاضي كان بيفضل «ما يكفيهوش المكان» والحلقة ما بتنتهيش.
                return;
            }
            rest = rest.slice(fitting, rest.lineCount());
            newPage();
        }
        drawHeader(header);
        draw(rest, extra);
    }

    private void drawHeader(Piece header) throws IOException {
        if (header != null && headerPage != pages) {
            draw(header, 0);
            headerPage = pages;
        }
    }

    private void draw(Piece piece, float extra) throws IOException {
        lowestReserved = Math.min(lowestReserved, y - (piece.height() + extra));
        piece.draw(canvas, y);
        y -= piece.height();
    }

    /** مسافة رأسية، إلا في أعلى الورقة نفسها (تحت الترويسة المسافة بتفضل، زي ما كانت في أول صفحة). */
    private void gap(float points) {
        if (y < top - 0.5f && !groupMoved) {
            y -= points;
        }
    }

    /** يفتح صفحة جديدة لو {@code height} ما يكفيهاش الباقي — إلا لو إحنا أصلًا في أول الصفحة. */
    private void need(float height) throws IOException, ReportException {
        if (y - height < bottom && !atTop()) {
            newPage();
        }
        lowestReserved = Math.min(lowestReserved, y - height);
    }

    // ===================== خانات التوقيع =====================

    /**
     * المجموعات اللي بتتنقل كلها لصفحة جديدة: كل جزء بيشد اللي قبله ({@link Join#ANCHOR})، والنصوص
     * والمسافات اللي قبله على طول ({@link Join#THROUGH}، {@link Join#GAP})، والجزء اللي قبلهم لو هو
     * أول المجموعة ({@link Join#LEAD}). المسافة في أول المجموعة مش منها. المفتاح أول جزء والقيمة آخر جزء.
     */
    private static Map<Integer, Integer> signatureGroups(List<Part> parts) {
        Map<Integer, Integer> groups = new HashMap<>();
        for (int last = 0; last < parts.size(); last++) {
            if (parts.get(last).join() != Join.ANCHOR) {
                continue;
            }
            int first = last;
            while (first > 0 && (parts.get(first - 1).join() == Join.THROUGH
                    || parts.get(first - 1).join() == Join.GAP)) {
                first--;
            }
            if (first > 0 && parts.get(first - 1).join() == Join.LEAD) {
                first--;
            }
            while (first < last && parts.get(first).join() == Join.GAP) {
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
    private void keepTogether(List<Part> group) throws IOException, ReportException {
        float height = 0;
        for (Part part : group) {
            height += span(part);
        }
        if (height <= freshRoom()) {
            int before = pages;
            need(height);
            groupMoved = pages > before;
        }
    }

    /**
     * لو الجزء {@code i} نص ({@link Join#THROUGH}) وبعده على طول (أو بعد مسافات بس) جزء بيشد اللي قبله:
     * طول المسافات والجزء ده، وآخر سطر في النص بيحجزه. لو المجموعة ما اتنقلتش كلها (أطول من صفحة، أو مفيش
     * فوقها غير ترويسة أول صفحة)، آخر سطر بينزل مع الخانات بدل ما تنزل هي لوحدها. غير كده صفر.
     */
    private static float anchorBelow(List<Part> parts, int i) throws IOException, ReportException {
        if (parts.get(i).join() != Join.THROUGH) {
            return 0;
        }
        float space = 0;
        int next = i + 1;
        while (next < parts.size() && parts.get(next).join() == Join.GAP) {
            space += span(parts.get(next));
            next++;
        }
        return next < parts.size() && parts.get(next).join() == Join.ANCHOR ? space + span(parts.get(next)) : 0;
    }

    /** الطول اللي الجزء بياخده كله في صفحة واحدة: المسافتين، وقطعه أو المكان اللي بيتأكد منه لو أكبر. */
    private static float span(Part part) throws IOException, ReportException {
        float drawn = 0;
        Pieces pieces = part.pieces();
        for (Piece piece = pieces.next(); piece != null; piece = pieces.next()) {
            drawn += piece.height();
        }
        return part.gapBefore() + Math.max(part.keep().height(), drawn) + part.gapAfter();
    }

    // ===================== الصفحات =====================

    /** مكان الكتابة في الصفحة اللي فاصل سابها. */
    private record BeforeBreak(PDPage page, int pages, float y, float pageTop, float lowestReserved) {
    }

    /**
     * فاصل الصفحات ({@link PdfDocument#pageBreak()}): صفحة جديدة، إلا لو الصفحة الحالية لسه مفيهاش حاجة
     * تحت الترويسة. الصفحة الجديدة بتفضل بس لو اترسم فيها حاجة ({@link #dropEmptyBreakPage()}). الورقة الحرارية
     * صفحة واحدة بطول المحتوى، و{@link PdfDocument} أصلًا ما بيضيفش فاصل على اللفة.
     */
    private void pageBreak() throws IOException, ReportException {
        if (paper.roll() || !canvas.marked()) {
            return;
        }
        beforeBreak = new BeforeBreak(pdf.getPage(pages - 1), pages, y, pageTop, lowestReserved);
        newPage();
    }

    /**
     * الصفحة اللي فتحها آخر فاصل وما اترسمش فيها حاجة لحد آخر جزء بتتشال، والتذييل بيكمل في الصفحة اللي
     * قبلها زي ما لو الفاصل ما كانش موجود. كده الفاصل اللي مفيش بعده غير أجزاء ما بترسمش (مسافة، نص فاضي،
     * عنوان {@code null}، فاصل تاني) ما بيسيبش صفحة فاضية ولا صفحة فيها التذييل لوحده. الحكم من الرسم نفسه
     * ({@link Canvas#marked()})، مش من نوع الجزء.
     */
    private void dropEmptyBreakPage() throws IOException {
        BeforeBreak left = beforeBreak;
        if (left == null || canvas.marked() || pages != left.pages() + 1) {
            return;
        }
        canvas.close();
        pdf.removePage(pages - 1);
        pages--;
        canvas.resume(left.page());
        y = left.y();
        pageTop = left.pageTop();
        lowestReserved = left.lowestReserved();
        canvas.marked(true);
        beforeBreak = null;
    }

    /**
     * صفحة جديدة، والترويسة في أعلاها لو دي أول صفحة أو لو المستند طالبها في كل صفحة. بعدها
     * {@link #atTop()} بيقيس من تحت الترويسة، فالجزء اللي ما كفّاهوش الباقي ما يفتحش صفحة ورا صفحة.
     */
    private void newPage() throws IOException, ReportException {
        canvas.close();
        PDPage page = new PDPage(new PDRectangle(paper.width(), paper.height()));
        pdf.addPage(page);
        canvas.start(page);
        y = top;
        lowestReserved = Float.MAX_VALUE;
        pages++;
        if (pages == 1 || document.letterheadOnEveryPage()) {
            y = letterhead.draw(canvas, y);
        }
        pageTop = y;
        canvas.marked(false);
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

    /**
     * العلامة المائية فوق محتوى كل صفحة، ورقم كل صفحة في منتصف هامشها السفلي وتذييل الصفحات فوقه. بيتكتبوا في
     * الآخر: العلامة فوق كل اللي اترسم في الصفحة، والرقم بيقول عدد الصفحات كلها، وده ما بيتعرفش غير بعد رسم آخر
     * صفحة.
     */
    private void finishPages() throws IOException, ReportException {
        int count = pdf.getNumberOfPages();
        PageNumbers mode = document.pageNumberMode();
        boolean numbered = mode == PageNumbers.ALWAYS || mode == PageNumbers.WHEN_SEVERAL && count >= 2;
        if (!numbered && pageFooter.isEmpty() && watermark == null) {
            return;
        }
        float size = style.smallSize();
        float leading = size * LEADING;
        float numberBaseline = style.margin() * 0.55f;
        // التذييل فوق مكان الرقم المحجوز (حتى لو الرقم ما اتكتبش)، فبيقف في نفس المكان في كل مستند.
        float footerBaseline = numberBaseline + (mode == PageNumbers.NONE ? 0 : leading)
                + (pageFooter.size() - 1) * leading;
        for (int number = 1; number <= count; number++) {
            canvas.resume(pdf.getPage(number - 1));
            try {
                if (watermark != null) {
                    watermark.draw(canvas);
                }
                if (numbered) {
                    String label = rtl ? "صفحة " + number + " من " + count : "Page " + number + " of " + count;
                    canvas.text(text.line(label, label, false, size), size, style.mutedColor(), left, width,
                            Canvas.Side.CENTER, numberBaseline);
                }
                for (int k = 0; k < pageFooter.size(); k++) {
                    canvas.text(pageFooter.get(k), size, style.mutedColor(), left, width, Canvas.Side.CENTER,
                            footerBaseline - k * leading);
                }
            } finally {
                canvas.close();
            }
        }
    }
}
