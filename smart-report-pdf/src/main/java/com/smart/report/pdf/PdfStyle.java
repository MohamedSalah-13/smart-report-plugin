package com.smart.report.pdf;

/**
 * The look of the page: margins, font sizes and colours. Colours are RGB numbers ({@code 0xRRGGBB}).
 *
 * <p>The default ({@link #standard()}) is calm and suits black-and-white printing: a light grey table header
 * with dark text, and a thin grid around the cells. {@link #forPaper(Paper)} makes the sizes and margins
 * smaller for narrow paper (A5 portrait), and {@link PdfDocument} uses it when no style is set.</p>
 *
 * @param margin       the page margin on each side, in points
 * @param titleSize    the title size
 * @param subtitleSize the subtitle size
 * @param headingSize  the size of a section heading (above a table, for example)
 * @param bodySize     the size of row and paragraph text
 * @param headerSize   the size of the table header
 * @param smallSize    the size of the footer, the page number and the letterhead lines
 * @param textColor    the text colour
 * @param mutedColor   the colour of secondary text (the subtitle, the footer)
 * @param accentColor  the colour of the line under the letterhead
 * @param headerFill   the table header background; white means no fill and a thick line under it
 * @param headerText   the table header text colour
 * @param totalsFill   the totals row background; white means no fill and two lines, above and below it
 * @param totalsText   the totals row text colour
 * @param stripeFill   the background of the alternating rows
 * @param ruleColor    the colour of the table lines
 * @param grid         a full grid around every cell, or only a horizontal line under each row
 * @param stripes      shade every second row
 */
public record PdfStyle(float margin,
                       float titleSize, float subtitleSize, float headingSize,
                       float bodySize, float headerSize, float smallSize,
                       int textColor, int mutedColor, int accentColor,
                       int headerFill, int headerText,
                       int totalsFill, int totalsText,
                       int stripeFill, int ruleColor,
                       boolean grid, boolean stripes) {

    static final int WHITE = 0xFFFFFF;

    /** The smallest font size that stays readable on paper. */
    public static final float MIN_FONT_SIZE = 6f;

    public PdfStyle {
        if (margin < 0) {
            throw new IllegalArgumentException("margin must not be negative.");
        }
        for (float size : new float[]{titleSize, subtitleSize, headingSize, bodySize, headerSize, smallSize}) {
            if (size < MIN_FONT_SIZE) {
                throw new IllegalArgumentException("Font sizes must be at least " + MIN_FONT_SIZE + " points.");
            }
        }
    }

    /** The default style, sized for A4. */
    public static PdfStyle standard() {
        return new PdfStyle(36f,
                16f, 11f, 11f,
                10f, 10f, 8f,
                0x000000, 0x5F6B76, 0x2C3E50,
                0xE6EAED, 0x1F2D3A,
                0xDDE3E8, 0x1F2D3A,
                0xF5F7F8, 0x8A949E,
                true, false);
    }

    /** The default style fitted to the paper size: smaller on narrow paper, with a smaller margin on short paper. */
    public static PdfStyle forPaper(Paper paper) {
        PdfStyle style = standard();
        if (paper.roll()) {
            // لفة الإيصالات ضيقة: خط أصغر، وهامش صغير لأن الطابعة نفسها سايبة هامش من الجنبين.
            return style.scaled(paper.width() < 200f ? 0.78f : 0.85f).withMargin(8f);
        }
        if (paper.width() < 500f) {
            style = style.scaled(0.88f);
        }
        return Math.min(paper.width(), paper.height()) < 450f ? style.withMargin(26f) : style;
    }

    /** All font sizes multiplied by {@code factor}, never below {@link #MIN_FONT_SIZE}. */
    public PdfStyle scaled(float factor) {
        return new PdfStyle(margin,
                scale(titleSize, factor), scale(subtitleSize, factor), scale(headingSize, factor),
                scale(bodySize, factor), scale(headerSize, factor), scale(smallSize, factor),
                textColor, mutedColor, accentColor, headerFill, headerText, totalsFill, totalsText,
                stripeFill, ruleColor, grid, stripes);
    }

    private static float scale(float size, float factor) {
        return Math.max(MIN_FONT_SIZE, size * factor);
    }

    public PdfStyle withMargin(float points) {
        return new PdfStyle(points, titleSize, subtitleSize, headingSize, bodySize, headerSize, smallSize,
                textColor, mutedColor, accentColor, headerFill, headerText, totalsFill, totalsText,
                stripeFill, ruleColor, grid, stripes);
    }

    public PdfStyle withGrid(boolean value) {
        return new PdfStyle(margin, titleSize, subtitleSize, headingSize, bodySize, headerSize, smallSize,
                textColor, mutedColor, accentColor, headerFill, headerText, totalsFill, totalsText,
                stripeFill, ruleColor, value, stripes);
    }

    public PdfStyle withStripes(boolean value) {
        return new PdfStyle(margin, titleSize, subtitleSize, headingSize, bodySize, headerSize, smallSize,
                textColor, mutedColor, accentColor, headerFill, headerText, totalsFill, totalsText,
                stripeFill, ruleColor, grid, value);
    }

    /**
     * The table header background and text colour, for example {@code withHeaderColors(0x2C3E50, 0xFFFFFF)} for a
     * dark header.
     */
    public PdfStyle withHeaderColors(int fill, int text) {
        return new PdfStyle(margin, titleSize, subtitleSize, headingSize, bodySize, headerSize, smallSize,
                textColor, mutedColor, accentColor, fill, text, totalsFill, totalsText,
                stripeFill, ruleColor, grid, stripes);
    }

    public PdfStyle withTotalsColors(int fill, int text) {
        return new PdfStyle(margin, titleSize, subtitleSize, headingSize, bodySize, headerSize, smallSize,
                textColor, mutedColor, accentColor, headerFill, headerText, fill, text,
                stripeFill, ruleColor, grid, stripes);
    }

    public PdfStyle withAccentColor(int color) {
        return new PdfStyle(margin, titleSize, subtitleSize, headingSize, bodySize, headerSize, smallSize,
                textColor, mutedColor, color, headerFill, headerText, totalsFill, totalsText,
                stripeFill, ruleColor, grid, stripes);
    }

    /**
     * A ready set of matching colours: the table header, the totals row, the line under the letterhead, the
     * alternating rows and the table lines. The sizes and margins stay as they are.
     */
    public PdfStyle withColorSet(ColorSet set) {
        java.util.Objects.requireNonNull(set, "set");
        return new PdfStyle(margin, titleSize, subtitleSize, headingSize, bodySize, headerSize, smallSize,
                textColor, mutedColor, set.accent(), set.headerFill(), set.headerText(), set.totalsFill(),
                set.totalsText(), set.stripe(), set.rule(), grid, stripes);
    }

    /**
     * Saves ink: no fill at all. The header and the totals use lines instead of backgrounds, and no rows are
     * shaded.
     */
    public PdfStyle inkSaver() {
        return new PdfStyle(margin, titleSize, subtitleSize, headingSize, bodySize, headerSize, smallSize,
                textColor, mutedColor, accentColor, WHITE, textColor, WHITE, textColor,
                WHITE, ruleColor, grid, false);
    }
}
