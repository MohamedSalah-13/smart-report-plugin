package com.smart.report.pdf;

import java.util.Objects;

/**
 * شكل خلية في {@link PdfTable}: خط عريض، أو لون للنص. اللي ما اتحددش بياخد شكل صفه (العادي أو صف الإجمالي).
 *
 * <pre>{@code
 * CellStyle.bold()
 * CellStyle.color(0xC62828).andBold()
 * }</pre>
 */
public final class CellStyle {

    /** الأحمر اللي {@link PdfTable#negativeInRed} بيستخدمه: غامق كفاية يتقري على الورق ويطلع رمادي غامق بالأبيض والأسود. */
    public static final int RED = 0xC62828;

    /** شكل الصف زي ما هو. */
    public static final CellStyle PLAIN = new CellStyle(null, null);

    private final Boolean bold;
    private final Integer color;

    private CellStyle(Boolean bold, Integer color) {
        this.bold = bold;
        this.color = color;
    }

    /** خط عريض. */
    public static CellStyle bold() {
        return new CellStyle(true, null);
    }

    /** نص بلون {@code 0xRRGGBB}. */
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

    /** الخط العريض لو اتحدد، أو شكل الصف. */
    boolean boldOr(boolean row) {
        return bold != null ? bold : row;
    }

    /** اللون لو اتحدد، أو لون الصف. */
    int colorOr(int row) {
        return color != null ? color : row;
    }

    /** الاتنين مع بعض: اللي في {@code over} بيغلب. */
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
