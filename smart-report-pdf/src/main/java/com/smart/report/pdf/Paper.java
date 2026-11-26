package com.smart.report.pdf;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * مقاس الورقة بالنقطة (1/72 بوصة) واتجاهها. {@link #A4} و{@link #A5} طوليان، و{@link #landscape()}
 * يقلب أي منهما عرضيًا. المقاس ده هو اللي الصفحة بتترسم عليه، وهو نفسه اللي الطابعة بتتطلب منه
 * في {@link PdfPrinter}.
 *
 * <p><b>الورق الحراري</b> ({@link #ROLL_80}، {@link #ROLL_58}) لفة بعرض ثابت: الإيصال بيتقص على قد المكتوب،
 * فالصفحة طولها طول المحتوى. {@code height} فيه هو أقصى طول لصفحة واحدة (حد ملف الـ PDF، حوالي 5 متر).</p>
 *
 * @param name   الاسم المعروض ("A4"، "A5"، "80mm"...)
 * @param width  العرض بالنقطة
 * @param height الارتفاع بالنقطة؛ في الورق الحراري أقصى طول
 * @param roll   ورق حراري: الطول على قد المحتوى
 */
public record Paper(String name, float width, float height, boolean roll) {

    private static final float POINTS_PER_MM = 72f / 25.4f;

    /** أطول صفحة يسمح بيها ملف PDF: 14400 نقطة (200 بوصة). */
    static final float MAX_PAGE_POINTS = 14_400f;

    /** 210 × 297 مم. */
    public static final Paper A4 = ofMillimetres("A4", 210, 297);
    /** 148 × 210 مم. */
    public static final Paper A5 = ofMillimetres("A5", 148, 210);
    /** ورق حراري 80 مم — طابعات الإيصالات في أغلب نقاط البيع. */
    public static final Paper ROLL_80 = roll("80mm", 80);
    /** ورق حراري 58 مم — الطابعات الصغيرة والمحمولة. */
    public static final Paper ROLL_58 = roll("58mm", 58);

    private static final Pattern ROLL_NAME = Pattern.compile("(?:ROLL[\\s_-]*)?(\\d+)[\\s_-]*MM");

    public Paper {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Paper name is required.");
        }
        if (!(width > 0) || !(height > 0)) {
            throw new IllegalArgumentException("Paper width and height must be positive.");
        }
    }

    /** ورقة عادية بطول ثابت. */
    public Paper(String name, float width, float height) {
        this(name, width, height, false);
    }

    /** مقاس مخصّص بالمليمتر. */
    public static Paper ofMillimetres(String name, float widthMm, float heightMm) {
        return new Paper(name, widthMm * POINTS_PER_MM, heightMm * POINTS_PER_MM);
    }

    /** لفة ورق حراري بعرضها بالمليمتر: كل إيصال صفحة واحدة بطول المكتوب فيه. */
    public static Paper roll(String name, float widthMm) {
        return new Paper(name, widthMm * POINTS_PER_MM, MAX_PAGE_POINTS, true);
    }

    /** نفس الورقة بالعرض: الضلع الطويل أفقي. اللفة الحرارية ما بتتقلبش. */
    public Paper landscape() {
        return roll || isLandscape() ? this : new Paper(name, height, width);
    }

    /** نفس الورقة بالطول: الضلع الطويل رأسي. */
    public Paper portrait() {
        return isLandscape() ? new Paper(name, height, width) : this;
    }

    public boolean isLandscape() {
        return !roll && width > height;
    }

    /** العرض بالمليمتر، للطابعة. */
    public float widthMm() {
        return width / POINTS_PER_MM;
    }

    /** الارتفاع بالمليمتر، للطابعة. */
    public float heightMm() {
        return height / POINTS_PER_MM;
    }

    /**
     * يقرأ "A4"، "A5"، "A4-landscape"، "a5 landscape"، "80mm"، "roll-58mm"... ويرمي
     * {@link IllegalArgumentException} لأي اسم غير معروف بدل ما يرجع لمقاس افتراضي في صمت.
     */
    public static Paper parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Paper name is required.");
        }
        String value = text.trim().toUpperCase(Locale.ROOT);
        Matcher rollName = ROLL_NAME.matcher(value);
        if (rollName.matches()) {
            return switch (rollName.group(1)) {
                case "80" -> ROLL_80;
                case "58" -> ROLL_58;
                default -> throw new IllegalArgumentException(
                        "Unknown roll paper: " + text + " (expected 80mm or 58mm).");
            };
        }
        boolean landscape = value.endsWith("LANDSCAPE");
        String size = landscape ? value.substring(0, value.length() - "LANDSCAPE".length()) : value;
        size = size.replaceAll("[\\s_-]+$", "");
        Paper paper = switch (size) {
            case "A4" -> A4;
            case "A5" -> A5;
            default -> throw new IllegalArgumentException(
                    "Unknown paper: " + text + " (expected A4, A5, 80mm or 58mm).");
        };
        return landscape ? paper.landscape() : paper;
    }
}
