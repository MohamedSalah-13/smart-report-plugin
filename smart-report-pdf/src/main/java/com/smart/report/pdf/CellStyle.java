package com.smart.report.pdf;

import java.util.Objects;

/**
 * The style of one cell in a {@link PdfTable}: bold text, or a text colour. Anything not set takes the style of
 * its row (a normal row or the totals row).
 *
 * <pre>{@code
 * CellStyle.bold()
 * CellStyle.color(0xC62828).andBold()
 * }</pre>
 */
public final class CellStyle {

    /**
     * The red that {@link PdfTable#negativeInRed} uses: dark enough to read on paper, and it prints as dark grey in
     * black and white.
     */
    public static final int RED = 0xC62828;

    /** The row style, unchanged. */
    public static final CellStyle PLAIN = new CellStyle(null, null);

    private final Boolean bold;
    private final Integer color;

    private CellStyle(Boolean bold, Integer color) {
        this.bold = bold;
        this.color = color;
    }

    /** Bold text. */
    public static CellStyle bold() {
        return new CellStyle(true, null);
    }

    /** Text in the colour {@code 0xRRGGBB}. */
    public static CellStyle color(int rgb) {
        return new CellStyle(null, check(rgb));
    }

    public CellStyle andBold() {
        return new CellStyle(true, color);
    }

    public CellStyle andColor(int rgb) {
        return new CellStyle(bold, check(rgb));
    }

    private static int check(int rgb) {
        if (rgb < 0 || rgb > 0xFFFFFF) {
            throw new IllegalArgumentException("A colour is 0xRRGGBB: " + Integer.toHexString(rgb));
        }
        return rgb;
    }

    /** The bold setting if one was set, otherwise the row style. */
    boolean boldOr(boolean row) {
        return bold != null ? bold : row;
    }

    /** The colour if one was set, otherwise the row colour. */
    int colorOr(int row) {
        return color != null ? color : row;
    }

    /** Both styles combined: what is set in {@code over} wins. */
    CellStyle under(CellStyle over) {
        if (over == null || over == PLAIN) {
            return this;
        }
        return new CellStyle(over.bold != null ? over.bold : bold, over.color != null ? over.color : color);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CellStyle that && Objects.equals(bold, that.bold) && Objects.equals(color, that.color);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bold, color);
    }

    @Override
    public String toString() {
        return "CellStyle[bold=" + bold + ", color=" + (color == null ? null : String.format("#%06X", color)) + "]";
    }
}
