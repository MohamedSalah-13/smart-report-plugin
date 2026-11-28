package com.smart.report.pdf;

import com.smart.report.ReportException;

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

    /** @param negativeInRed القيمة السالبة بالأحمر ({@link PdfTable#negativeInRed}) */
    record SummaryLine(String label, String value, boolean total, boolean negativeInRed) {
    }

    record SignaturesBlock(List<String> labels) implements Block {
    }

    /** @param size ضلع الرمز بالنقطة، من غير المنطقة الفاضية حواليه */
    record QrBlock(QrSymbol symbol, float size, Align align) implements Block {
    }

    /** @param height طول الخطوط بالنقطة، من غير السطر المكتوب تحتها */
    record BarcodeBlock(BarcodeSymbol symbol, float height, Align align) implements Block {
    }

    /** @param height طول الخطوط بالنقطة، من غير الأرقام المكتوبة تحتها */
    record Ean13Block(Ean13Symbol symbol, float height, Align align) implements Block {
    }

    /**
     * @param bytes  PNG أو JPEG زي ما اتدّت
     * @param width  العرض المطلوب بالنقطة
     * @param pixels عرض الصورة وطولها بالبكسل، للنسبة بينهم
     */
    record ImageBlock(byte[] bytes, float width, int[] pixels, Align align) implements Block {
    }

    /** سطر «طُبع في ... بواسطة ...» تحت العنوان. */
    record PrintedBlock(String text) implements Block {
    }

    /** الجزء اللي بعده يبدأ في صفحة جديدة؛ القواعد في {@link #pageBreak()}. */
    record PageBreakBlock() implements Block {
    }

    /** ضلع رمز QR لو ما اتحدّدش: 30 مم، بيتقرا بسهولة من الموبايل ويكفي في عرض لفة 58 مم. */
    static final float DEFAULT_QR_MM = 30f;

    /** طول خطوط الباركود لو ما اتحدّدش: 12 مم، بيتقرا بقارئ اليد من غير ما ياخد مكان كتير في الإيصال. */
    static final float DEFAULT_BARCODE_MM = 12f;

    /** طول خطوط EAN-13 لو ما اتحدّدش: 15 مم، قريب من حجم المنتجات (22.85 مم بالأرقام في الحجم الكامل). */
    static final float DEFAULT_EAN13_MM = 15f;

    private static final float POINTS_PER_MM = 72f / 25.4f;

    private final Paper paper;
    private final boolean rightToLeft;
    private final List<Block> blocks = new ArrayList<>();
    private PdfStyle style;
    private PdfFont font;
    private boolean failOnMissingGlyphs;
    private String letterheadName = "";
    private List<String> letterheadLines = List.of();
    private byte[] logo;
    private String footer = "";
    private boolean letterheadOnEveryPage;
    private String pageFooter = "";
    private PageNumbers pageNumbers = PageNumbers.WHEN_SEVERAL;
    private String watermark = "";
    private int watermarkColor = Watermark.DEFAULT_COLOR;
    private int printerDpi;
    private boolean arabicIndicDigits;
    private final List<Attachment> attachments = new ArrayList<>();
    private boolean pdfA3;
    private java.time.LocalDateTime printedOn;
    private String printedBy;
    private PageNumberStyle pageNumberStyle = PageNumberStyle.WORDS;
    /** الورق المطبوع عليه ترويسة: المسافة فوق بالنقطة، أو سالب = ورق أبيض. */
    private float prePrintedTop = -1;
    private float totalsFontSize;

    /** علاقة الملف المرفق بالمستند، زي ما PDF/A-3 بيسميها ({@code /AFRelationship}). */
    public enum Relationship {
        /** المصدر اللي المستند اتعمل منه. */
        SOURCE("Source"),
        /** بيانات المستند نفسه في صيغة تانية: XML الفاتورة الإلكترونية. */
        DATA("Data"),
        /** نسخة تانية بديلة من نفس المحتوى. */
        ALTERNATIVE("Alternative"),
        /** ملف مكمّل. */
        SUPPLEMENT("Supplement"),
        UNSPECIFIED("Unspecified");

        private final String pdfName;

        Relationship(String pdfName) {
            this.pdfName = pdfName;
        }

        String pdfName() {
            return pdfName;
        }
    }

    record Attachment(String name, byte[] data, String mimeType, Relationship relationship) {
    }

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

    /**
     * خط المستند ده بدل الخط العام ({@code -Dsmart.report.pdf.font} أو Noto Naskh Arabic المضمَّن)، من غير
     * ما يأثر على أي مستند تاني؛ {@code null} يرجّعه للخط العام.
     */
    public PdfDocument font(PdfFont font) {
        this.font = font;
        return this;
    }

    /**
     * حرف مش في أي خط (لا خط المستند ولا الخطوط الاحتياطية) يوقّف كتابة المستند بـ
     * {@link ReportException} فيها الحرف وكوده والنص اللي هو فيه، بدل ما يتطبع {@code ?} — للفواتير
     * والمستندات المالية اللي علامة استفهام مكان حرف فيها غلط. مقفول من غير ما يتحدد.
     */
    public PdfDocument failOnMissingGlyphs(boolean value) {
        this.failOnMissingGlyphs = value;
        return this;
    }

    /**
     * سطر صغير تحت أول عنوان بوقت الطباعة: «طُبع في 2026-11-28 10:30». مع {@link #printedBy} في نفس السطر.
     * {@code null} = من غيره.
     */
    public PdfDocument printedOn(java.time.LocalDateTime when) {
        this.printedOn = when == null ? null : when.truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        return this;
    }

    /** اسم المستخدم في سطر الطباعة تحت أول عنوان: «بواسطة أحمد». {@code null} أو فاضي = من غيره. */
    public PdfDocument printedBy(String user) {
        this.printedBy = user == null || user.isBlank() ? null : user.strip();
        return this;
    }

    /** شكل رقم الصفحة: «صفحة 1 من 3» ({@link PageNumberStyle#WORDS}، الافتراضي) أو «1 / 3». */
    public PdfDocument pageNumberStyle(PageNumberStyle value) {
        this.pageNumberStyle = Objects.requireNonNull(value, "value");
        return this;
    }

    /**
     * ورق مطبوع عليه ترويسة المحل: الترويسة ({@link #letterhead} والشعار) ما بتترسمش، والمحتوى بيبدأ بعد
     * {@code topMm} مليمتر زيادة على الهامش في كل صفحة، تحت الترويسة المطبوعة.
     *
     * @throws IllegalArgumentException لو سالب
     */
    public PdfDocument prePrinted(float topMm) {
        if (!(topMm >= 0) || Float.isInfinite(topMm)) {
            throw new IllegalArgumentException("The top space is 0 mm or more: " + topMm);
        }
        this.prePrintedTop = topMm * POINTS_PER_MM;
        return this;
    }

    /** ورق أبيض: الترويسة بتترسم زي العادي (بيلغي {@link #prePrinted}). */
    public PdfDocument plainPaper() {
        this.prePrintedTop = -1;
        return this;
    }

    /**
     * حجم خط صفوف الإجمالي في الجداول، منفصل عن خط الصفوف ({@link PdfStyle#bodySize}). صفر = زي الصفوف.
     *
     * @throws IllegalArgumentException لو مش صفر وأصغر من {@link PdfStyle#MIN_FONT_SIZE}
     */
    public PdfDocument totalsFontSize(float points) {
        if (points != 0 && !(points >= PdfStyle.MIN_FONT_SIZE && points <= 72)) {
            throw new IllegalArgumentException("The totals size is 0 or " + PdfStyle.MIN_FONT_SIZE + " to 72 points: "
                    + points);
        }
        this.totalsFontSize = points;
        return this;
    }

    /**
     * ملف جوّه الـ PDF: XML الفاتورة الإلكترونية الموقّعة مثلًا، فالملف المؤرشف يشيل الاتنين. علاقته
     * {@link Relationship#DATA}.
     *
     * @see #attach(String, byte[], String, Relationship)
     */
    public PdfDocument attach(String name, byte[] data, String mimeType) {
        return attach(name, data, mimeType, Relationship.DATA);
    }

    /**
     * ملف جوّه الـ PDF، بعلاقته بالمستند. بيظهر في قائمة المرفقات في قارئ الـ PDF، ومع {@link #pdfA3} بيبقى ملف
     * «مرتبط» بالمستند زي ما الأرشفة بتطلب.
     *
     * @param name     اسم الملف زي ما هيظهر: {@code "invoice.xml"}؛ كل اسم مرة واحدة
     * @param mimeType {@code "application/xml"}، {@code "text/xml"}...
     * @throws IllegalArgumentException لو الاسم فاضي أو متكرر، أو النوع مش {@code type/subtype}
     */
    public PdfDocument attach(String name, byte[] data, String mimeType, Relationship relationship) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(relationship, "relationship");
        if (name == null || name.isBlank() || name.contains("/") || name.contains("\\")) {
            throw new IllegalArgumentException("An attachment needs a file name without a path: " + name);
        }
        if (mimeType == null || !mimeType.matches("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+")) {
            throw new IllegalArgumentException("A MIME type is type/subtype, such as application/xml: " + mimeType);
        }
        String clean = name.strip();
        if (attachments.stream().anyMatch(file -> file.name().equals(clean))) {
            throw new IllegalArgumentException("The attachment " + clean + " is already in the document.");
        }
        attachments.add(new Attachment(clean, data.clone(), mimeType, relationship));
        return this;
    }

    /**
     * يكتب المستند PDF/A-3B، صيغة الأرشفة: ملف الألوان (sRGB) والبيانات الوصفية XMP جوّاه، والخطوط مضمّنة أصلًا، والملفات
     * المرفقة ({@link #attach}) مرتبطة بالمستند. للفاتورة اللي لازم تتحفظ سنين ومعاها الـ XML بتاعها. مقفول من غير ما
     * يتحدد.
     */
    public PdfDocument pdfA3(boolean value) {
        this.pdfA3 = value;
        return this;
    }

    /**
     * كل رقم في المستند بيترسم بالأرقام العربية ({@code ١٢٣}) بدل {@code 123}: النص، والجداول، والملخص، والترويسة،
     * وأرقام الصفحات. الرقم بيفضل مكانه في السطر زي ما هو (الأرقام العربية بتتقري من الشمال لليمين زي اللاتينية)،
     * والفاصلة والنقطة زي ما اتكتبوا. اللي جوّه رمز QR والباركود والكود المكتوب تحت الباركود بيفضلوا لاتيني: الماسح
     * والقارئ محتاجينهم كده. مقفول من غير ما يتحدد.
     */
    public PdfDocument arabicIndicDigits(boolean value) {
        this.arabicIndicDigits = value;
        return this;
    }

    /**
     * دقة الطابعة اللي المستند رايح لها (203 للطابعة الحرارية المعتادة، 300، 600...)، عشان كل مربع في الباركود
     * ورمز QR ياخد عدد صحيح من نقطها، ومكانه على نقطة منها. من غيرها أضيق خط 0.33 مم، أي 2.64 نقطة على رأس
     * 203، فالدرايفر بيقرر أنهي خط ياخد 2 وأنهي 3، والخطوط المش متساوية سبب معروف لباركود ما بيتقريش.
     *
     * <p>بالدقة: أضيق خط في الباركود أقرب عدد نقط لـ 0.33 مم، ومربع QR أقرب عدد نقط للمقاس المطلوب، والاتنين
     * نقطتين على الأقل، والباركود مش أضيق من 0.25 مم. لو الرمز ما كفّاش السطر بيقل نقطة نقطة لحد الحد ده،
     * وبعدها بيفشل بـ {@link ReportException} بدل ما يصغر لكسور. من غيرها (أو بصفر) الرسم زي ما هو.</p>
     *
     * @param dpi نقط في البوصة، من 72 لـ 2400؛ صفر = من غير
     * @throws IllegalArgumentException لو برّه المدى
     */
    public PdfDocument printerDpi(int dpi) {
        if (dpi != 0 && (dpi < 72 || dpi > 2400)) {
            throw new IllegalArgumentException("The printer resolution must be between 72 and 2400 dpi, or 0 for"
                    + " none: " + dpi);
        }
        this.printerDpi = dpi;
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
     * علامة مائية رمادية فاتحة في كل صفحة — «مسودة»، «صورة»، «ملغاة»، «مدفوع»، {@code DRAFT}، {@code COPY}:
     * لنسخة الفاتورة وصورة المستند. {@code null} أو نص فاضي = من غير علامة.
     *
     * @see #watermark(String, int)
     */
    public PdfDocument watermark(String text) {
        return watermark(text, Watermark.DEFAULT_COLOR);
    }

    /**
     * علامة مائية في كل صفحة بلون {@code color} ({@code 0xRRGGBB}) شفاف — أحمر لـ «ملغاة» وأخضر لـ «مدفوع»
     * مثلًا. {@code null} أو نص فاضي = من غير علامة.
     *
     * <ul>
     *   <li>كبيرة ومايلة على قطر الصفحة في نصها، <b>فوق كل حاجة وبشفافية</b>: بتترسم بعد محتوى الصفحة، فما
     *       بتستخباش تحت خلفية رأس الجدول أو الإجمالي أو صندوق الملخص، والنص اللي تحتها بيفضل مقروء. سطر واحد
     *       بخط المستند ({@link #font(PdfFont)})، والعربي متشكّل ومن اليمين لليسار.</li>
     *   <li>بتصغر على قد الورقة: الكلمة الطويلة والورق الصغير بيصغّروا الخط لحد ما العلامة تكفي جوه الهوامش.</li>
     *   <li>على الورق الحراري ({@link Paper#roll()}) العلامة بالعرض مش مايلة، ومتكررة على طول الإيصال (نسخة
     *       لكل حوالي مرة ونص عرض اللفة؛ الإيصال القصير نسخة واحدة في نصه)، عشان أي جزء من الإيصال يبان فيه.</li>
     *   <li>عتامتها 15%: على الورق الأبيض الأسود رمادي فاتح والأحمر وردي فاتح، فاتحة حتى لو اللون غامق، وحبرها
     *       قليل حتى مع {@link PdfStyle#inkSaver()}.</li>
     *   <li>مش محتوى: ما بتغيرش مكان أي جزء ولا طول الإيصال، والصفحة اللي فيها العلامة بس فاضية، فالفاصل
     *       ({@link #pageBreak()}) ما بيسيبش صفحة عشانها. ومحطوطة {@code /Artifact}، فالقارئ اللي بيحترمها بيسيبها
     *       في النسخ والبحث. وفي {@link PdfBatch} كل مستند بعلامته.</li>
     * </ul>
     */
    public PdfDocument watermark(String text, int color) {
        this.watermark = text == null ? "" : text;
        this.watermarkColor = color & 0xFFFFFF;
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

    /**
     * باركود EAN-13 في منتصف السطر، خطوطه بطول 15 مم وتحتها الـ 13 رقم: الباركود اللي على المنتجات.
     *
     * @param digits 12 رقم (خانة التحقق بتتحسب) أو 13 (خانتها بتتراجع)
     * @throws IllegalArgumentException لو مش 12 أو 13 رقم، أو خانة التحقق غلط
     * @see #ean13(String, float, Align)
     */
    public PdfDocument ean13(String digits) {
        return ean13(digits, DEFAULT_EAN13_MM, Align.CENTER);
    }

    /**
     * باركود EAN-13 مرسوم خطوطًا متجهة: أضيق خط 0.33 مم (الحجم الأساسي)، والخطوط الحارسة أطول من الباقي، والأرقام
     * تحتها زي المنتجات، وأول رقم في المنطقة الفاضية على الشمال. لو ما كفّاش السطر الخطوط بتضيق لحد 0.264 مم (80%
     * من الحجم، أقل ما المواصفة بتسمح بيه)، وبعدها كتابة المستند بتفشل بـ {@link ReportException}. ومع
     * {@link #printerDpi} كل خط عدد صحيح من نقط الطابعة.
     *
     * @param heightMm طول الخطوط بالمليمتر، من غير الأرقام
     * @throws IllegalArgumentException لو مش 12 أو 13 رقم، أو خانة التحقق غلط، أو الطول مش موجب
     */
    public PdfDocument ean13(String digits, float heightMm, Align align) {
        if (!(heightMm > 0) || Float.isInfinite(heightMm)) {
            throw new IllegalArgumentException("The barcode height must be a positive number of millimetres.");
        }
        return add(new Ean13Block(Ean13Symbol.encode(digits), heightMm * POINTS_PER_MM,
                align == null ? Align.CENTER : align));
    }

    /**
     * صورة في جسم المستند: ختم، أو توقيع، أو صورة صنف. الطول بيتحسب من نسبة الصورة، والصورة ما بتتقسمش: لو
     * ما كفّاهاش الباقي بتتنقل كلها للصفحة الجاية، ولو أعرض من السطر أو أطول من صفحة كاملة بتصغر بنفس نسبتها.
     *
     * @param image   PNG أو JPEG (الشفافية في PNG بتفضل: الختم ما بيغطيش اللي تحته)
     * @param widthMm العرض بالمليمتر
     * @param align   {@link Align#START} عند بداية السطر (يمين في المستند العربي)، {@link Align#CENTER}، أو
     *                {@link Align#END}
     * @throws IllegalArgumentException لو مش صورة PNG أو JPEG تتقري، أو العرض مش موجب
     */
    public PdfDocument image(byte[] image, float widthMm, Align align) {
        if (!(widthMm > 0) || Float.isInfinite(widthMm)) {
            throw new IllegalArgumentException("The image width must be a positive number of millimetres.");
        }
        Objects.requireNonNull(image, "image");
        int[] pixels = ImageParts.pixels(image);
        return add(new ImageBlock(image.clone(), widthMm * POINTS_PER_MM, pixels, align == null ? Align.CENTER : align));
    }

    /**
     * صورة من ملف؛ زي {@link #image(byte[], float, Align)}.
     *
     * @throws java.io.UncheckedIOException لو الملف ما اتقراش
     */
    public PdfDocument image(java.nio.file.Path file, float widthMm, Align align) {
        try {
            return image(java.nio.file.Files.readAllBytes(file), widthMm, align);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("The image " + file + " could not be read: " + e.getMessage(), e);
        }
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

    /** سطر الإجمالي في الملخص، وقيمته بالأحمر لو سالبة: الرصيد الختامي في كشف الحساب. */
    PdfDocument summaryTotalNegativeInRed(String label, String value) {
        return summaryLine(label, value, true, true);
    }

    private PdfDocument summaryLine(String label, String value, boolean total) {
        return summaryLine(label, value, total, false);
    }

    private PdfDocument summaryLine(String label, String value, boolean total, boolean negativeInRed) {
        SummaryLine line = new SummaryLine(label == null ? "" : label, value == null ? "" : value, total,
                negativeInRed);
        if (!blocks.isEmpty() && blocks.get(blocks.size() - 1) instanceof SummaryBlock summary) {
            summary.lines().add(line);
        } else {
            List<SummaryLine> lines = new ArrayList<>();
            lines.add(line);
            blocks.add(new SummaryBlock(lines));
        }
        return this;
    }

    /**
     * الجزء اللي بعده يبدأ في صفحة جديدة — قسم لكل عربية، أو الملاحق بعد الكشف.
     *
     * <ul>
     *   <li>الفاصل ما بيطلّعش صفحة فاضية أبدًا: لو الصفحة الحالية لسه مفيهاش حاجة تحت الترويسة (الفاصل
     *       في أول المستند، أو فاصلين ورا بعض) ما بيعملش حاجة، والفاصل اللي مفيش بعده حاجة بتترسم (آخر
     *       المستند، أو بعده مسافات ونص فاضي بس) ما بيسيبش صفحة، ولا صفحة فيها التذييل لوحده.</li>
     *   <li>الصفحة الجديدة زي أي صفحة بيفتحها المحتوى الطويل: الترويسة في أعلاها لو
     *       {@link #letterheadOnEveryPage(boolean)}، وترقيم الصفحات بيكمل («صفحة 2 من 3»).</li>
     *   <li>بعد جدول كمل على كذا صفحة، الفاصل بييجي بعد آخر صف فيه (والإجمالي)، ورأس الجدول ما بيتكررش في
     *       الصفحة الجديدة: الجدول خلص. عشان كل مجموعة صفوف تبدأ في صفحة، اعمل جدول لكل مجموعة وبينهم فاصل.</li>
     *   <li>على الورق الحراري ({@link Paper#roll()}) الإيصال صفحة واحدة بطول المحتوى، فالفاصل
     *       <b>ما بيعملش حاجة</b>: نفس المستند بيتبني لأي ورق من غير ما يفشل على اللفة، والإيصال زي ما هو من
     *       غيره بالظبط (البيانات وسطور الملخص اللي حواليه بتفضل مع بعض). عشان كل إيصال يتقص لوحده، اطبعهم
     *       {@link PdfBatch}.</li>
     * </ul>
     *
     * <p>كل قسم بترقيمه وترويسته (كشف لكل سواق في ملف واحد) ده {@link PdfBatch}، مش فاصل.</p>
     */
    public PdfDocument pageBreak() {
        // على اللفة ما بيتضافش خالص: لو اتضاف، البيانات وسطور الملخص اللي قبله وبعده كانت هتبقى صفين وصندوقين.
        return paper.roll() ? this : add(new PageBreakBlock());
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

    /** عنوان الملف (واسم مهمة الطباعة): نص أول {@link #title(String)} مش {@code null}. */
    String firstTitle() {
        for (Block block : blocks) {
            if (block instanceof TitleBlock title && title.text() != null) {
                return title.text();
            }
        }
        return null;
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

    String watermarkText() {
        return watermark;
    }

    /** نص سطر «طُبع في ... بواسطة ...»، أو {@code null} من غيره. */
    String printedLine() {
        if (printedOn == null && printedBy == null) {
            return null;
        }
        String when = printedOn == null ? null
                : printedOn.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        if (rightToLeft) {
            return (when == null ? "" : "طُبع في " + when) + (when != null && printedBy != null ? " " : "")
                    + (printedBy == null ? "" : "بواسطة " + printedBy);
        }
        String line = (when == null ? "" : "Printed on " + when) + (when != null && printedBy != null ? " " : "")
                + (printedBy == null ? "" : "by " + printedBy);
        return when == null ? Character.toUpperCase(line.charAt(0)) + line.substring(1) : line;
    }

    PageNumberStyle pageNumberStyleValue() {
        return pageNumberStyle;
    }

    boolean prePrinted() {
        return prePrintedTop >= 0;
    }

    float prePrintedTop() {
        return Math.max(0, prePrintedTop);
    }

    float totalsFontSize() {
        return totalsFontSize;
    }

    List<Attachment> attachments() {
        return attachments;
    }

    boolean pdfA3() {
        return pdfA3;
    }

    boolean arabicIndicDigits() {
        return arabicIndicDigits;
    }

    int printerDpi() {
        return printerDpi;
    }

    int watermarkColor() {
        return watermarkColor;
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
        PageWriter.write(this, font, failOnMissingGlyphs, out);
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
