package com.smart.report.pdf;

import com.smart.report.ReportException;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * مستند PDF بيتبني جزءًا بعد جزء بالترتيب اللي هيظهر بيه: ترويسة، عنوان، بيانات، جداول، ملخص، تذييل.
 *
 * <pre>{@code
 * PdfDocument document = PdfDocument.rtl(Paper.A5)
 *         .letterhead("شركة النقل", "ت: 0100 000 0000")
 *         .title("كشف حساب عربية")
 *         .field("رقم العربية", "1234").field("السائق", "أحمد")
 *         .table(t -> t.column("التاريخ", 22).column("العدد", 14).column("القيمة", 26, Align.END)
 *                 .row("2026-11-01", "3", "5,400.00")
 *                 .totals("الإجمالى", "3", "5,400.00"))
 *         .summary("المستحق", "5,400.00")
 *         .summaryTotal("الباقى", "1,400.00");
 *
 * document.save(Path.of("statement.pdf"));     // ملف
 * PdfPrinter.print(document, printerName, 1);  // أو للطابعة مباشرة
 * }</pre>
 *
 * <p>الاتجاه ({@link #rtl(Paper)} / {@link #ltr(Paper)}) بيحدد مكان أول عمود في الجدول ومحاذاة
 * النص؛ النص العربي بيتشكّل ويترتب صح في الاتجاهين، والأرقام والتواريخ بتتقرا زي ما اتكتبت.</p>
 *
 * <p>الكائن قابل للتعديل وغير آمن للاستخدام من أكتر من thread؛ ابنِ مستندًا لكل طباعة.</p>
 */
public final class PdfDocument {

    /** جزء من جسم المستند، بالترتيب اللي اتضاف بيه. */
    sealed interface Block {
    }

    record TitleBlock(String text) implements Block {
    }

    record SubtitleBlock(String text) implements Block {
    }

    record HeadingBlock(String text) implements Block {
    }

    record TextBlock(String text, Align align) implements Block {
    }

    record SpaceBlock(float points) implements Block {
    }

    record TableBlock(PdfTable table) implements Block {
    }

    record FieldsBlock(List<String[]> fields) implements Block {
    }

    record SummaryBlock(List<SummaryLine> lines) implements Block {
    }

    record SummaryLine(String label, String value, boolean total) {
    }

    record SignaturesBlock(List<String> labels) implements Block {
    }

    /** @param size ضلع الرمز بالنقطة، من غير المنطقة الفاضية حواليه */
    record QrBlock(QrSymbol symbol, float size, Align align) implements Block {
    }

    /** @param height طول الخطوط بالنقطة، من غير السطر المكتوب تحتها */
    record BarcodeBlock(BarcodeSymbol symbol, float height, Align align) implements Block {
    }

    /** ضلع رمز QR لو ما اتحدّدش: 30 مم، بيتقرا بسهولة من الموبايل ويكفي في عرض لفة 58 مم. */
    static final float DEFAULT_QR_MM = 30f;

    /** طول خطوط الباركود لو ما اتحدّدش: 12 مم، بيتقرا بقارئ اليد من غير ما ياخد مكان كتير في الإيصال. */
    static final float DEFAULT_BARCODE_MM = 12f;

    private static final float POINTS_PER_MM = 72f / 25.4f;

    private final Paper paper;
    private final boolean rightToLeft;
    private final List<Block> blocks = new ArrayList<>();
    private PdfStyle style;
    private String letterheadName = "";
    private List<String> letterheadLines = List.of();
    private byte[] logo;
    private String footer = "";
    private boolean letterheadOnEveryPage;
    private String pageFooter = "";
    private PageNumbers pageNumbers = PageNumbers.WHEN_SEVERAL;

    private PdfDocument(Paper paper, boolean rightToLeft) {
        this.paper = Objects.requireNonNull(paper, "paper");
        this.rightToLeft = rightToLeft;
    }

    /** مستند من اليمين لليسار — العربي. */
    public static PdfDocument rtl(Paper paper) {
        return new PdfDocument(paper, true);
    }

    /** مستند من اليسار لليمين. */
    public static PdfDocument ltr(Paper paper) {
        return new PdfDocument(paper, false);
    }

    /** شكل الصفحة؛ من غيره بيتاخد {@link PdfStyle#forPaper(Paper)}. */
    public PdfDocument style(PdfStyle style) {
        this.style = Objects.requireNonNull(style, "style");
        return this;
    }

    /** ترويسة أعلى أول صفحة: اسم الجهة بخط عريض، وتحته سطور (عنوان، تليفون...)، ثم خط فاصل. */
    public PdfDocument letterhead(String name, String... lines) {
        this.letterheadName = name == null ? "" : name;
        List<String> kept = new ArrayList<>();
        if (lines != null) {
            for (String line : lines) {
                if (line != null && !line.isBlank()) {
                    kept.add(line);
                }
            }
        }
        this.letterheadLines = List.copyOf(kept);
        return this;
    }

    /**
     * الترويسة (والشعار) في أعلى كل صفحة، مش الأولى بس — للكشوف اللي بتطول. رأس الجدول اللي بيكمل
     * على الصفحة الجديدة بيترسم تحتها.
     */
    public PdfDocument letterheadOnEveryPage(boolean value) {
        this.letterheadOnEveryPage = value;
        return this;
    }

    /**
     * سطر صغير في أسفل كل صفحة فوق رقمها — العنوان والتليفون، أو «هذا المستند صادر من نظام ...».
     * غير {@link #footer(String)} اللي بيترسم مرة واحدة بعد آخر جزء.
     */
    public PdfDocument pageFooter(String text) {
        this.pageFooter = text == null ? "" : text;
        return this;
    }

    /**
     * خانات توقيع بالعرض: عنوان كل خانة («المستلم»، «المحاسب»، «الختم») وتحته مكان للتوقيع وخط.
     * الخانة الأولى عند بداية السطر، والخانات بتفضل مع بعض في نفس الصفحة.
     */
    public PdfDocument signatures(String... labels) {
        List<String> kept = new ArrayList<>();
        if (labels != null) {
            for (String label : labels) {
                kept.add(label == null ? "" : label);
            }
        }
        if (kept.isEmpty()) {
            throw new IllegalArgumentException("At least one signature label is required.");
        }
        return add(new SignaturesBlock(List.copyOf(kept)));
    }

    /**
     * رمز QR في منتصف السطر، ضلعه 30 مم — رابط، رقم فاتورة، أو رمز فاتورة الزكاة والضريبة
     * ({@link com.smart.report.util.ZatcaQr#payload}).
     *
     * @throws IllegalArgumentException لو النص فاضي أو أطول من اللي يشيله رمز QR
     * @see #qrCode(String, float, Align)
     */
    public PdfDocument qrCode(String text) {
        return qrCode(text, DEFAULT_QR_MM, Align.CENTER);
    }

    /** رمز QR ضلعه 30 مم، عند بداية السطر أو في منتصفه أو آخره حسب اتجاه المستند. */
    public PdfDocument qrCode(String text, Align align) {
        return qrCode(text, DEFAULT_QR_MM, align);
    }

    /**
     * رمز QR مرسوم مربعات متجهة (مش صورة)، فبيفضل حادًا على أي طابعة. حواليه منطقة فاضية بعرض 4
     * مربعات زيادة على {@code sizeMm}، والقارئ بيحتاجها. الرمز ما بيتقسمش: لو ما كفّاهوش الباقي بيتنقل
     * كله للصفحة الجاية، ولو أعرض من الصفحة (لفة 58 مم مثلًا) بيصغر لحد ما يكفي هو ومنطقته الفاضية.
     *
     * @param text   النص اللي الرمز بيشيله؛ العربي بيتشفّر UTF-8
     * @param sizeMm ضلع الرمز بالمليمتر، من غير المنطقة الفاضية
     * @param align  {@link Align#START} عند بداية السطر (يمين في المستند العربي)، {@link Align#CENTER}، أو
     *               {@link Align#END}
     * @throws IllegalArgumentException لو النص فاضي أو أطول من اللي يشيله رمز QR (حوالي 2300 بايت)، أو
     *                                  المقاس مش موجب
     */
    public PdfDocument qrCode(String text, float sizeMm, Align align) {
        if (!(sizeMm > 0) || Float.isInfinite(sizeMm)) {
            throw new IllegalArgumentException("The QR code size must be a positive number of millimetres.");
        }
        return add(new QrBlock(QrSymbol.encode(text), sizeMm * POINTS_PER_MM, align == null ? Align.CENTER : align));
    }

    /**
     * باركود Code 128 في منتصف السطر، خطوطه بطول 12 مم وتحتها النص نفسه مكتوب — رقم شحنة، بوليصة،
     * فاتورة، أو كود صنف.
     *
     * @throws IllegalArgumentException لو النص فاضي أو فيه حرف غير الإنجليزي والأرقام والعلامات
     * @see #barcode(String, float, Align)
     */
    public PdfDocument barcode(String text) {
        return barcode(text, DEFAULT_BARCODE_MM, Align.CENTER);
    }

    /** باركود Code 128 خطوطه بطول 12 مم، عند بداية السطر أو في منتصفه أو آخره حسب اتجاه المستند. */
    public PdfDocument barcode(String text, Align align) {
        return barcode(text, DEFAULT_BARCODE_MM, align);
    }

    /**
     * باركود Code 128 مرسوم خطوطًا متجهة (مش صورة)، وتحته النص مكتوب من الشمال لليمين حتى في المستند
     * العربي، عشان اللي بيقراه بعينه يقراه زي ما هو. أضيق خط 0.33 مم، وحواليه من الجنبين منطقة فاضية
     * بعرض 10 خطوط؛ لو ده أعرض من السطر (لفة 58 مم مثلًا) الخطوط بتضيق لحد ما يكفي. الباركود وسطره ما
     * بيتقسموش على صفحتين.
     *
     * <p>Code 128 بيشيل الإنجليزي والأرقام والعلامات بس، والرقم الطويل بيعرّض الباركود: النص اللي يحتاج
     * خطوطًا أضيق من 0.25 مم عشان يكفي في السطر (أكتر من حوالي 15 حرف على لفة 58 مم) بيفشل وقت كتابة
     * المستند بـ {@link com.smart.report.ReportException} بدل ما يطلع باركود ما بيتقريش.</p>
     *
     * @param text     النص اللي الباركود بيشيله
     * @param heightMm طول الخطوط بالمليمتر، من غير السطر المكتوب تحتها
     * @param align    {@link Align#START} عند بداية السطر (يمين في المستند العربي)، {@link Align#CENTER}، أو
     *                 {@link Align#END}
     * @throws IllegalArgumentException لو النص فاضي أو فيه حرف غير الإنجليزي والأرقام والعلامات (حرف عربي
     *                                  مثلًا)، أو الطول مش موجب
     */
    public PdfDocument barcode(String text, float heightMm, Align align) {
        if (!(heightMm > 0) || Float.isInfinite(heightMm)) {
            throw new IllegalArgumentException("The barcode height must be a positive number of millimetres.");
        }
        return add(new BarcodeBlock(BarcodeSymbol.encode(text), heightMm * POINTS_PER_MM,
                align == null ? Align.CENTER : align));
    }

    /** شعار الجهة (PNG أو JPEG) بجانب الترويسة، في الجهة اللي الصفحة بتبدأ منها. */
    public PdfDocument logo(byte[] image) {
        this.logo = image == null || image.length == 0 ? null : image.clone();
        return this;
    }

    /** عنوان في منتصف الصفحة بخط عريض. */
    public PdfDocument title(String text) {
        return add(new TitleBlock(text));
    }

    /** سطر تحت العنوان، في المنتصف بلون أهدأ — الفترة مثلًا. */
    public PdfDocument subtitle(String text) {
        return add(new SubtitleBlock(text));
    }

    /** عنوان قسم بخط عريض عند بداية السطر — فوق جدول مثلًا. */
    public PdfDocument heading(String text) {
        return add(new HeadingBlock(text));
    }

    /** فقرة عند بداية السطر، بتلتف على عرض الصفحة. */
    public PdfDocument text(String text) {
        return text(text, Align.START);
    }

    public PdfDocument text(String text, Align align) {
        return add(new TextBlock(text, align == null ? Align.START : align));
    }

    /** مسافة رأسية بالنقطة. */
    public PdfDocument space(float points) {
        return add(new SpaceBlock(Math.max(0, points)));
    }

    /**
     * بيان وقيمته ("رقم العربية: 1234"). البيانات المتتالية بتترص اثنين في السطر، الأول عند بداية
     * السطر والتاني في نصف الصفحة.
     */
    public PdfDocument field(String label, String value) {
        String[] field = {label == null ? "" : label, value == null ? "" : value};
        if (!blocks.isEmpty() && blocks.get(blocks.size() - 1) instanceof FieldsBlock fields) {
            fields.fields().add(field);
        } else {
            List<String[]> fields = new ArrayList<>();
            fields.add(field);
            blocks.add(new FieldsBlock(fields));
        }
        return this;
    }

    public PdfDocument table(PdfTable table) {
        return add(new TableBlock(Objects.requireNonNull(table, "table")));
    }

    /** جدول بيتبني في مكانه: {@code table(t -> t.column(...).row(...))}. */
    public PdfDocument table(Consumer<PdfTable> build) {
        PdfTable table = PdfTable.create();
        build.accept(table);
        return table(table);
    }

    /** سطر في الملخص: بيان عند بداية السطر وقيمته بجانبه. السطور المتتالية بتبقى صندوقًا واحدًا. */
    public PdfDocument summary(String label, String value) {
        return summaryLine(label, value, false);
    }

    /** السطر اللي القارئ بيدوّر عليه (الباقى، الإجمالى): بخط أكبر وعريض على خلفية مميّزة. */
    public PdfDocument summaryTotal(String label, String value) {
        return summaryLine(label, value, true);
    }

    private PdfDocument summaryLine(String label, String value, boolean total) {
        SummaryLine line = new SummaryLine(label == null ? "" : label, value == null ? "" : value, total);
        if (!blocks.isEmpty() && blocks.get(blocks.size() - 1) instanceof SummaryBlock summary) {
            summary.lines().add(line);
        } else {
            List<SummaryLine> lines = new ArrayList<>();
            lines.add(line);
            blocks.add(new SummaryBlock(lines));
        }
        return this;
    }

    /** جملة ختامية صغيرة في منتصف الصفحة بعد آخر جزء. */
    public PdfDocument footer(String text) {
        this.footer = text == null ? "" : text;
        return this;
    }

    public PdfDocument pageNumbers(PageNumbers mode) {
        this.pageNumbers = Objects.requireNonNull(mode, "mode");
        return this;
    }

    private PdfDocument add(Block block) {
        blocks.add(block);
        return this;
    }

    public Paper paper() {
        return paper;
    }

    public boolean rightToLeft() {
        return rightToLeft;
    }

    PdfStyle resolvedStyle() {
        return style != null ? style : PdfStyle.forPaper(paper);
    }

    List<Block> blocks() {
        return blocks;
    }

    String letterheadName() {
        return letterheadName;
    }

    List<String> letterheadLines() {
        return letterheadLines;
    }

    byte[] logoBytes() {
        return logo;
    }

    String footerText() {
        return footer;
    }

    PageNumbers pageNumberMode() {
        return pageNumbers;
    }

    boolean letterheadOnEveryPage() {
        return letterheadOnEveryPage;
    }

    String pageFooterText() {
        return pageFooter;
    }

    /** المستند كبايتات PDF. */
    public byte[] toBytes() throws ReportException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeTo(out);
        return out.toByteArray();
    }

    /** يكتب المستند في {@code out} ولا يقفله. */
    public void writeTo(OutputStream out) throws ReportException {
        Objects.requireNonNull(out, "out");
        if (paper.roll()) {
            // الورق الحراري بيتقص على قد المكتوب: المستند بيترسم مرة على ورقة بأقصى طول عشان يتقاس، وبعدين
            // على ورقة بالطول اللي احتاجه. لو ما كفّتهوش ورقة بأقصى طول بيفضل صفحات بالطول ده.
            float used = layOut(paper, null);
            if (used > 0) {
                // نقطتين زيادة: الجزء الأخير بيقف على الحد بالظبط، وفرق تقريب صغير كان هيفتح له صفحة.
                float height = Math.max((float) Math.ceil(used) + 2, paper.width());
                // القياس بيحسب المكان المحجوز كمان، فالمحاولة الأولى بتكفي؛ ولو حاجة لسه فتحت صفحة تانية
                // الورقة بتطول بوصة وتتجرّب تاني بدل ما الإيصال يطلع على ورقتين.
                for (int attempt = 0; attempt < 4 && height < paper.height(); attempt++) {
                    ByteArrayOutputStream cut = new ByteArrayOutputStream();
                    if (layOut(new Paper(paper.name(), paper.width(), height, true), cut) > 0) {
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
        layOut(paper, out);
    }

    /** يرسم المستند على {@code page} ويكتبه في {@code out} لو مش {@code null}؛ بيرجّع الطول المستخدم. */
    private float layOut(Paper page, OutputStream out) throws ReportException {
        try (PDDocument pdf = new PDDocument(); FontSet fonts = FontSet.load(pdf)) {
            PageWriter writer = new PageWriter(pdf, this, fonts, page);
            writer.write();
            if (out != null) {
                pdf.save(out);
            }
            return writer.usedHeight();
        } catch (IOException e) {
            throw new ReportException("Failed to write the PDF document: " + e.getMessage(), e);
        }
    }

    /** يحفظ المستند في ملف، وينشئ المجلدات الناقصة في مساره. */
    public void save(Path file) throws ReportException {
        Objects.requireNonNull(file, "file");
        byte[] bytes = toBytes();
        try {
            if (file.toAbsolutePath().getParent() != null) {
                Files.createDirectories(file.toAbsolutePath().getParent());
            }
            Files.write(file, bytes);
        } catch (IOException e) {
            throw new ReportException("Failed to write the PDF document to " + file, e);
        }
    }
}
