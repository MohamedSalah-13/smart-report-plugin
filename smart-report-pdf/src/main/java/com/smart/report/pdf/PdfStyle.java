package com.smart.report.pdf;

/**
 * شكل الصفحة: الهوامش، أحجام الخط، والألوان. الألوان أرقام RGB ({@code 0xRRGGBB}).
 *
 * <p>الافتراضي ({@link #standard()}) هادئ ويصلح للطباعة أبيض وأسود: رأس الجدول رمادي فاتح بخط غامق،
 * وشبكة رفيعة حول الخلايا. {@link #forPaper(Paper)} بيصغّر الأحجام والهوامش للورق الضيّق (A5 طولي)،
 * وهو اللي {@link PdfDocument} بيستخدمه لو ما اتحدّدش شكل.</p>
 *
 * @param margin       هامش الصفحة من كل جهة، بالنقطة
 * @param titleSize    حجم العنوان
 * @param subtitleSize حجم العنوان الفرعي
 * @param headingSize  حجم عنوان القسم (فوق جدول مثلًا)
 * @param bodySize     حجم نص الصفوف والفقرات
 * @param headerSize   حجم رأس الجدول
 * @param smallSize    حجم التذييل ورقم الصفحة وسطور الترويسة
 * @param textColor    لون النص
 * @param mutedColor   لون النص الثانوي (العنوان الفرعي، التذييل)
 * @param accentColor  لون الخط تحت الترويسة
 * @param headerFill   خلفية رأس الجدول؛ الأبيض معناه بلا تعبئة وخط سميك تحته
 * @param headerText   لون نص رأس الجدول
 * @param totalsFill   خلفية صف الإجمالي؛ الأبيض معناه بلا تعبئة وخطان فوقه وتحته
 * @param totalsText   لون نص صف الإجمالي
 * @param stripeFill   خلفية الصفوف المتبادلة
 * @param ruleColor    لون خطوط الجدول
 * @param grid         شبكة كاملة حول كل خلية، أو خط أفقي تحت كل صف فقط
 * @param stripes      تلوين كل صف تانٍ
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

    /** أصغر حجم خط يفضل مقروءًا على الورق. */
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

    /** الشكل الافتراضي، مقاس على A4. */
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

    /** الشكل الافتراضي مناسبًا لمقاس الورقة: أصغر على الورق الضيّق، وبهامش أقل على الورق القصير. */
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

    /** كل أحجام الخط مضروبة في {@code factor}، ولا تنزل عن {@link #MIN_FONT_SIZE}. */
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

    /** خلفية رأس الجدول ولون نصه — مثلًا {@code withHeaderColors(0x2C3E50, 0xFFFFFF)} لرأس غامق. */
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
     * مجموعة ألوان جاهزة متناسقة: رأس الجدول، وصف الإجمالي، والخط تحت الترويسة، والصفوف المتبادلة، وخطوط الجدول؛
     * الأحجام والهوامش زي ما هي.
     */
    public PdfStyle withColorSet(ColorSet set) {
        java.util.Objects.requireNonNull(set, "set");
        return new PdfStyle(margin, titleSize, subtitleSize, headingSize, bodySize, headerSize, smallSize,
                textColor, mutedColor, set.accent(), set.headerFill(), set.headerText(), set.totalsFill(),
                set.totalsText(), set.stripe(), set.rule(), grid, stripes);
    }

    /** توفير الحبر: بلا أي تعبئة — الرأس والإجمالي بخطوط بدل الخلفيات، ولا صفوف ملوّنة. */
    public PdfStyle inkSaver() {
        return new PdfStyle(margin, titleSize, subtitleSize, headingSize, bodySize, headerSize, smallSize,
                textColor, mutedColor, accentColor, WHITE, textColor, WHITE, textColor,
                WHITE, ruleColor, grid, false);
    }
}
