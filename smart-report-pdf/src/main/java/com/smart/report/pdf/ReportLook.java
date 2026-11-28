package com.smart.report.pdf;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The look of the reports, chosen once by the shop and followed by every document: the colour set, ink saving, the
 * grid and striped rows, the page number style, the «طُبع في ... بواسطة ...» line ("printed on ... by ..."),
 * paper with a pre-printed letterhead, and the font size of the totals.
 *
 * <pre>{@code
 * ReportLook look = ReportLook.decode(settings.get("report.look"));   // the saved look, or the default
 * PdfDocument document = look.applyTo(PdfDocument.rtl(Paper.A4), currentUser.name());
 *
 * settings.put("report.look", look.withColorSet(ColorSet.NAVY).withPageNumbers(PageNumberStyle.SLASH).encode());
 * PdfPreview.of(look.sample(Paper.A4, true, "أحمد")).show(owner);   // live preview in the settings screen
 * }</pre>
 *
 * <p>{@link #encode()} gives one line of text, and {@link #decode} reads it leniently: a value written by another
 * version of the program does not stop printing, and any part that cannot be read takes the default. The class is
 * immutable.</p>
 */
public final class ReportLook {

    /** The library's look with no choices made: grey, grid, «صفحة 1 من 3» ("page 1 of 3"), plain white paper. */
    public static final ReportLook DEFAULT = new ReportLook(ColorSet.GREY, false, true, false,
            PageNumberStyle.WORDS, false, false, -1, 0);

    private final ColorSet colors;
    private final boolean inkSaver;
    private final boolean grid;
    private final boolean stripes;
    private final PageNumberStyle pageNumbers;
    private final boolean printedDate;
    private final boolean printedUser;
    /** The top space for pre-printed paper in mm, or negative = plain white paper. */
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

    /** No fills at all ({@link PdfStyle#inkSaver()}), applied on top of the colour set. */
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

    /** The printed line under the title: the time and the user name, each on or off separately. */
    public ReportLook withPrintedLine(boolean date, boolean user) {
        return new ReportLook(colors, inkSaver, grid, stripes, pageNumbers, date, user, prePrintedTopMm,
                totalsFontSize);
    }

    /** Paper with a pre-printed letterhead: no letterhead is drawn, and the content starts after {@code topMm}. */
    public ReportLook withPrePrinted(float topMm) {
        if (!(topMm >= 0)) {
            throw new IllegalArgumentException("The top space is 0 mm or more: " + topMm);
        }
        return new ReportLook(colors, inkSaver, grid, stripes, pageNumbers, printedDate, printedUser, topMm,
                totalsFontSize);
    }

    /** Plain white paper with the program's letterhead. */
    public ReportLook withPlainPaper() {
        return new ReportLook(colors, inkSaver, grid, stripes, pageNumbers, printedDate, printedUser, -1,
                totalsFontSize);
    }

    /** The font size of the totals row; zero = the same as the rows. */
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

    /** The top space for pre-printed paper in mm; zero for plain white paper. */
    public float prePrintedTopMm() {
        return Math.max(0, prePrintedTopMm);
    }

    public float totalsFontSize() {
        return totalsFontSize;
    }

    /**
     * Applies the look to the document: a {@link PdfDocument#style} that suits its paper, with the colours; the page
     * number style; the printed line with the current time; the pre-printed paper; and the totals size. Returns the
     * same document.
     *
     * @param user the user name for the printed line; {@code null} = without it
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

    /**
     * A sample document in this look, for a live preview in the settings screen: a letterhead, a title, fields, and
     * a table with a total, over two pages.
     */
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

    /**
     * The look as one line of text:
     * {@code colors=navy;ink=false;grid=true;stripes=false;numbers=slash;date=true;user=true;...}.
     */
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
     * Reads {@link #encode()} leniently: a part that cannot be read or is out of range takes its value from
     * {@link #DEFAULT}, and unknown parts are ignored; {@code null} or empty = {@link #DEFAULT}.
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
