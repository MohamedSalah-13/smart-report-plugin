package com.smart.report.pdf;

/**
 * مقاس الورقة بالنقطة (1/72 بوصة) واتجاهها. {@link #A4} و{@link #A5} طوليان، و{@link #landscape()}
 * يقلب أي منهما عرضيًا. المقاس ده هو اللي الصفحة بتترسم عليه، وهو نفسه اللي الطابعة بتتطلب منه
 * في {@link PdfPrinter}.
 *
 * @param name   الاسم المعروض ("A4"، "A5"...)
 * @param width  العرض بالنقطة
 * @param height الارتفاع بالنقطة
 */
public record Paper(String name, float width, float height) {

    private static final float POINTS_PER_MM = 72f / 25.4f;

    /** 210 × 297 مم. */
    public static final Paper A4 = ofMillimetres("A4", 210, 297);
    /** 148 × 210 مم. */
    public static final Paper A5 = ofMillimetres("A5", 148, 210);

    public Paper {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Paper name is required.");
        }
        if (!(width > 0) || !(height > 0)) {
            throw new IllegalArgumentException("Paper width and height must be positive.");
        }
    }

    /** مقاس مخصّص بالمليمتر. */
    public static Paper ofMillimetres(String name, float widthMm, float heightMm) {
        return new Paper(name, widthMm * POINTS_PER_MM, heightMm * POINTS_PER_MM);
    }

    /** نفس الورقة بالعرض: الضلع الطويل أفقي. */
    public Paper landscape() {
        return isLandscape() ? this : new Paper(name, height, width);
    }

    /** نفس الورقة بالطول: الضلع الطويل رأسي. */
    public Paper portrait() {
        return isLandscape() ? new Paper(name, height, width) : this;
    }

    public boolean isLandscape() {
        return width > height;
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
     * يقرأ "A4"، "A5"، "A4-landscape"، "a5 landscape"... ويرمي {@link IllegalArgumentException}
     * لأي اسم غير معروف بدل ما يرجع لمقاس افتراضي في صمت.
     */
    public static Paper parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Paper name is required.");
        }
        String value = text.trim().toUpperCase(java.util.Locale.ROOT);
        boolean landscape = value.endsWith("LANDSCAPE");
        String size = landscape ? value.substring(0, value.length() - "LANDSCAPE".length()) : value;
        size = size.replaceAll("[\\s_-]+$", "");
        Paper paper = switch (size) {
            case "A4" -> A4;
            case "A5" -> A5;
            default -> throw new IllegalArgumentException("Unknown paper: " + text + " (expected A4 or A5).");
        };
        return landscape ? paper.landscape() : paper;
    }
}
