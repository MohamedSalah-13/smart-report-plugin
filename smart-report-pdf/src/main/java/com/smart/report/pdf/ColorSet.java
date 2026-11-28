package com.smart.report.pdf;

/**
 * مجموعة ألوان جاهزة متناسقة لرأس الجدول وصف الإجمالي والخط تحت الترويسة والصفوف المتبادلة وخطوط الجدول
 * ({@link PdfStyle#withColorSet}). اللون الغامق مع النص الأبيض بتباين يتقري، والفاتح بيطلع رمادي فاتح لو الطابعة
 * أبيض وأسود.
 */
public enum ColorSet {

    /** الافتراضي: رمادي هادئ، رأس فاتح بنص غامق. */
    GREY(0xE6EAED, 0x1F2D3A, 0xDDE3E8, 0x1F2D3A, 0x2C3E50, 0xF5F7F8, 0x8A949E),
    /** كحلي: رأس غامق بنص أبيض. */
    NAVY(0x1F3A5F, 0xFFFFFF, 0xD6E0EC, 0x1F3A5F, 0x1F3A5F, 0xF2F5F9, 0x9AA8B8),
    /** أخضر غامق. */
    GREEN(0x1E5631, 0xFFFFFF, 0xD9EADF, 0x1E5631, 0x1E5631, 0xF3F8F4, 0x9DB5A4),
    /** نبيتي. */
    MAROON(0x6D1F2C, 0xFFFFFF, 0xEEDADD, 0x6D1F2C, 0x6D1F2C, 0xF9F3F4, 0xB59AA0),
    /** فيروزي غامق. */
    TEAL(0x0F5E63, 0xFFFFFF, 0xD5E9EA, 0x0F5E63, 0x0F5E63, 0xF1F7F7, 0x93B3B5);

    private final int headerFill;
    private final int headerText;
    private final int totalsFill;
    private final int totalsText;
    private final int accent;
    private final int stripe;
    private final int rule;

    ColorSet(int headerFill, int headerText, int totalsFill, int totalsText, int accent, int stripe, int rule) {
        this.headerFill = headerFill;
        this.headerText = headerText;
        this.totalsFill = totalsFill;
        this.totalsText = totalsText;
        this.accent = accent;
        this.stripe = stripe;
        this.rule = rule;
    }

    public int headerFill() {
        return headerFill;
    }

    public int headerText() {
        return headerText;
    }

    public int totalsFill() {
        return totalsFill;
    }

    public int totalsText() {
        return totalsText;
    }

    public int accent() {
        return accent;
    }

    public int stripe() {
        return stripe;
    }

    public int rule() {
        return rule;
    }
}
