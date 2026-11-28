package com.smart.report.pdf;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * شكل التقارير زي ما المحل بيختاره مرة وكل المستندات بتمشي عليه: مجموعة الألوان، توفير الحبر، الشبكة والصفوف
 * الملونة، شكل رقم الصفحة، سطر «طُبع في ... بواسطة ...»، الورق المطبوع عليه ترويسة، وحجم خط الإجمالي.
 *
 * <pre>{@code
 * ReportLook look = ReportLook.decode(settings.get("report.look"));   // اللي اتحفظ، أو الافتراضي
 * PdfDocument document = look.applyTo(PdfDocument.rtl(Paper.A4), currentUser.name());
 *
 * settings.put("report.look", look.withColorSet(ColorSet.NAVY).withPageNumbers(PageNumberStyle.SLASH).encode());
 * PdfPreview.of(look.sample(Paper.A4, true, "أحمد")).show(owner);   // معاينة حية في شاشة الإعدادات
 * }</pre>
 *
 * <p>{@link #encode()} سطر نص، و {@link #decode} بيقراه بتسامح: قيمة كتبتها نسخة تانية من البرنامج ما توقفش
 * الطباعة، وأي جزء ما يتقريش بياخد الافتراضي. الصنف immutable.</p>
 */
public final class ReportLook {

    /** شكل المكتبة من غير أي اختيار: رمادي، شبكة، «صفحة 1 من 3»، ورق أبيض. */
    public static final ReportLook DEFAULT = new ReportLook(ColorSet.GREY, false, true, false,
            PageNumberStyle.WORDS, false, false, -1, 0);

    private final ColorSet colors;
    private final boolean inkSaver;
    private final boolean grid;
    private final boolean stripes;
    private final PageNumberStyle pageNumbers;
    private final boolean printedDate;
    private final boolean printedUser;
    /** المسافة فوق للورق المطبوع بالمليمتر، أو سالب = ورق أبيض. */
    private final float prePrintedTopMm;
    private final float totalsFontSize;

    private ReportLook(ColorSet colors, boolean inkSaver, boolean grid, boolean stripes, PageNumberStyle pageNumbers,
                       boolean printedDate, boolean printedUser, float prePrintedTopMm, float totalsFontSize) {
        this.colors = Objects.requireNonNull(colors, "colors");
        this.inkSaver = inkSaver;
        this.grid = grid;
        this.stripes = stripes;
        this.pageNumbers = Objects.requireNonNull(pageNumbers, "pageNumbers");
        this.printedDate = printedDate;
        this.printedUser = printedUser;
        if (prePrintedTopMm > 200) {
            throw new IllegalArgumentException("The top space of pre-printed paper is at most 200 mm: " + prePrintedTopMm);
        }
        this.prePrintedTopMm = prePrintedTopMm < 0 ? -1 : prePrintedTopMm;
        if (totalsFontSize != 0 && !(totalsFontSize >= PdfStyle.MIN_FONT_SIZE && totalsFontSize <= 72)) {
            throw new IllegalArgumentException("The totals size is 0 or " + PdfStyle.MIN_FONT_SIZE + " to 72 points: "
                    + totalsFontSize);
        }
        this.totalsFontSize = totalsFontSize;
    }

    public ReportLook withColorSet(ColorSet value) {
        return new ReportLook(value, inkSaver, grid, stripes, pageNumbers, printedDate, printedUser, prePrintedTopMm,
                totalsFontSize);
    }

    /** بلا أي تعبئة ({@link PdfStyle#inkSaver()})، فوق مجموعة الألوان. */
    public ReportLook withInkSaver(boolean value) {
        return new ReportLook(colors, value, grid, stripes, pageNumbers, printedDate, printedUser, prePrintedTopMm,
                totalsFontSize);
    }

    public ReportLook withGrid(boolean value) {
        return new ReportLook(colors, inkSaver, value, stripes, pageNumbers, printedDate, printedUser, prePrintedTopMm,
                totalsFontSize);
    }

    public ReportLook withStripes(boolean value) {
        return new ReportLook(colors, inkSaver, grid, value, pageNumbers, printedDate, printedUser, prePrintedTopMm,
                totalsFontSize);
    }

    public ReportLook withPageNumbers(PageNumberStyle value) {
        return new ReportLook(colors, inkSaver, grid, stripes, value, printedDate, printedUser, prePrintedTopMm,
                totalsFontSize);
    }

    /** سطر الطباعة تحت العنوان: الوقت، واسم المستخدم، كل واحد لوحده. */
    public ReportLook withPrintedLine(boolean date, boolean user) {
        return new ReportLook(colors, inkSaver, grid, stripes, pageNumbers, date, user, prePrintedTopMm,
                totalsFontSize);
    }

    /** ورق مطبوع عليه ترويسة: من غير ترويسة، والمحتوى بيبدأ بعد {@code topMm}. */
    public ReportLook withPrePrinted(float topMm) {
        if (!(topMm >= 0)) {
            throw new IllegalArgumentException("The top space is 0 mm or more: " + topMm);
        }
        return new ReportLook(colors, inkSaver, grid, stripes, pageNumbers, printedDate, printedUser, topMm,
                totalsFontSize);
    }

    /** ورق أبيض بترويسة البرنامج. */
    public ReportLook withPlainPaper() {
        return new ReportLook(colors, inkSaver, grid, stripes, pageNumbers, printedDate, printedUser, -1,
                totalsFontSize);
    }

    /** حجم خط صف الإجمالي؛ صفر = زي الصفوف. */
    public ReportLook withTotalsFontSize(float points) {
        return new ReportLook(colors, inkSaver, grid, stripes, pageNumbers, printedDate, printedUser, prePrintedTopMm,
                points);
    }

    public ColorSet colorSet() {
        return colors;
    }

    public boolean inkSaver() {
        return inkSaver;
    }

    public boolean grid() {
        return grid;
    }

    public boolean stripes() {
        return stripes;
    }

    public PageNumberStyle pageNumbers() {
        return pageNumbers;
    }

    public boolean printedDate() {
        return printedDate;
    }

    public boolean printedUser() {
        return printedUser;
    }

    public boolean prePrinted() {
        return prePrintedTopMm >= 0;
    }

    /** المسافة فوق للورق المطبوع بالمليمتر؛ صفر للورق الأبيض. */
    public float prePrintedTopMm() {
        return Math.max(0, prePrintedTopMm);
    }

    public float totalsFontSize() {
        return totalsFontSize;
    }

    /**
     * يطبّق الشكل على المستند: {@link PdfDocument#style} مناسب لورقته بالألوان، وشكل رقم الصفحة، وسطر الطباعة بالوقت
     * دلوقتي، والورق المطبوع، وحجم الإجمالي. بيرجّع نفس المستند.
     *
     * @param user اسم المستخدم لسطر الطباعة؛ {@code null} = من غيره
     */
    public PdfDocument applyTo(PdfDocument document, String user) {
        Objects.requireNonNull(document, "document");
        PdfStyle style = PdfStyle.forPaper(document.paper()).withColorSet(colors).withGrid(grid).withStripes(stripes);
        document.style(inkSaver ? style.inkSaver() : style)
                .pageNumberStyle(pageNumbers)
                .printedOn(printedDate ? LocalDateTime.now() : null)
                .printedBy(printedUser ? user : null)
                .totalsFontSize(totalsFontSize);
        return prePrinted() ? document.prePrinted(prePrintedTopMm) : document.plainPaper();
    }

    /** مستند تجربة بالشكل ده، لمعاينة حية في شاشة الإعدادات: ترويسة، وعنوان، وحقول، وجدول بإجمالي، على صفحتين. */
    public PdfDocument sample(Paper paper, boolean rightToLeft, String user) {
        PdfDocument document = rightToLeft ? PdfDocument.rtl(paper) : PdfDocument.ltr(paper);
        PdfTable table = PdfTable.create()
                .column(rightToLeft ? "الصنف" : "Item", 3)
                .column(rightToLeft ? "الكمية" : "Qty", 1, Align.END)
                .column(rightToLeft ? "القيمة" : "Amount", 2, Align.END);
        int rows = paper.roll() ? 4 : 45;
        for (int i = 1; i <= rows; i++) {
            table.row((rightToLeft ? "صنف " : "Item ") + i, String.valueOf(i % 5 + 1),
                    String.format(Locale.ROOT, "%,.2f", 12.5 * i));
        }
        table.totals(rightToLeft ? "الإجمالى" : "Total", "", "1,234.50");
        document.letterhead(rightToLeft ? "اسم المحل" : "Shop name", rightToLeft ? "العنوان والتليفون" : "Address, phone")
                .title(rightToLeft ? "فاتورة بيع 1751" : "Sales invoice 1751")
                .field(rightToLeft ? "العميل" : "Customer", rightToLeft ? "أحمد علي" : "Ahmed Ali")
                .table(table)
                .pageNumbers(paper.roll() ? PageNumbers.NONE : PageNumbers.ALWAYS);
        return applyTo(document, user);
    }

    /** الشكل كسطر نص: {@code colors=navy;ink=false;grid=true;stripes=false;numbers=slash;date=true;user=true;...}. */
    public String encode() {
        return "colors=" + colors.name().toLowerCase(Locale.ROOT) + ";ink=" + inkSaver + ";grid=" + grid
                + ";stripes=" + stripes + ";numbers=" + pageNumbers.name().toLowerCase(Locale.ROOT)
                + ";date=" + printedDate + ";user=" + printedUser
                + ";preprinted=" + (prePrinted() ? format(prePrintedTopMm) : "off")
                + ";totals=" + format(totalsFontSize);
    }

    private static String format(float value) {
        return value == Math.rint(value) ? String.valueOf((int) value) : String.valueOf(value);
    }

    /**
     * يقرا {@link #encode()} بتسامح: الجزء اللي ما يتقريش أو برّه المدى بياخد قيمته من {@link #DEFAULT}، والأجزاء
     * المجهولة بتتجاهل؛ {@code null} أو فاضي = {@link #DEFAULT}.
     */
    public static ReportLook decode(String text) {
        if (text == null || text.isBlank()) {
            return DEFAULT;
        }
        Map<String, String> values = new HashMap<>();
        for (String pair : text.split(";")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                values.putIfAbsent(pair.substring(0, equals).strip().toLowerCase(Locale.ROOT),
                        pair.substring(equals + 1).strip().toLowerCase(Locale.ROOT));
            }
        }
        ReportLook look = DEFAULT;
        try {
            look = look.withColorSet(ColorSet.valueOf(values.getOrDefault("colors", "grey").toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            // الافتراضي.
        }
        look = look.withInkSaver(flag(values.get("ink"), look.inkSaver))
                .withGrid(flag(values.get("grid"), look.grid))
                .withStripes(flag(values.get("stripes"), look.stripes))
                .withPrintedLine(flag(values.get("date"), false), flag(values.get("user"), false));
        try {
            look = look.withPageNumbers(PageNumberStyle.valueOf(
                    values.getOrDefault("numbers", "words").toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            // الافتراضي.
        }
        String preprinted = values.get("preprinted");
        if (preprinted != null && !preprinted.equals("off")) {
            try {
                float top = Float.parseFloat(preprinted);
                if (top >= 0 && top <= 200) {
                    look = look.withPrePrinted(top);
                }
            } catch (NumberFormatException unreadable) {
                // ورق أبيض.
            }
        }
        String totals = values.get("totals");
        if (totals != null) {
            try {
                look = look.withTotalsFontSize(Float.parseFloat(totals));
            } catch (IllegalArgumentException unreadable) {
                // NumberFormatException أو برّه المدى: زي الصفوف.
            }
        }
        return look;
    }

    private static boolean flag(String value, boolean fallback) {
        return "true".equals(value) ? true : "false".equals(value) ? false : fallback;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ReportLook that && colors == that.colors && inkSaver == that.inkSaver
                && grid == that.grid && stripes == that.stripes && pageNumbers == that.pageNumbers
                && printedDate == that.printedDate && printedUser == that.printedUser
                && Float.compare(prePrintedTopMm, that.prePrintedTopMm) == 0
                && Float.compare(totalsFontSize, that.totalsFontSize) == 0;
    }

    @Override
    public int hashCode() {
        return encode().hashCode();
    }

    @Override
    public String toString() {
        return "ReportLook[" + encode() + "]";
    }
}
