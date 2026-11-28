package com.smart.report.pdf;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Paper size in points (1/72 inch) and orientation. {@link #A4} and {@link #A5} are portrait, and
 * {@link #landscape()} turns either of them to landscape. The page is drawn on this size, and the same size
 * is requested from the printer in {@link PdfPrinter}.
 *
 * <p><b>Thermal paper</b> ({@link #ROLL_80}, {@link #ROLL_58}) is a roll with a fixed width: the receipt is
 * cut to fit what is written, so the page is as long as its content. Its {@code height} is the maximum length
 * of one page (the PDF file limit, about 5 metres).</p>
 *
 * @param name   the display name ("A4", "A5", "80mm"...)
 * @param width  the width in points
 * @param height the height in points; for thermal paper, the maximum length
 * @param roll   thermal paper: the length fits the content
 */
public record Paper(String name, float width, float height, boolean roll) {

    private static final float POINTS_PER_MM = 72f / 25.4f;

    /** The longest page a PDF file allows: 14400 points (200 inches). */
    static final float MAX_PAGE_POINTS = 14_400f;

    /** 210 × 297 mm. */
    public static final Paper A4 = ofMillimetres("A4", 210, 297);
    /** 148 × 210 mm. */
    public static final Paper A5 = ofMillimetres("A5", 148, 210);
    /** 80 mm thermal paper, used by the receipt printers at most points of sale. */
    public static final Paper ROLL_80 = roll("80mm", 80);
    /** 58 mm thermal paper, used by small and portable printers. */
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

    /** A regular sheet with a fixed height. */
    public Paper(String name, float width, float height) {
        this(name, width, height, false);
    }

    /** A custom size in millimetres. */
    public static Paper ofMillimetres(String name, float widthMm, float heightMm) {
        return new Paper(name, widthMm * POINTS_PER_MM, heightMm * POINTS_PER_MM);
    }

    /** A thermal paper roll, given its width in millimetres: each receipt is one page, as long as its content. */
    public static Paper roll(String name, float widthMm) {
        return new Paper(name, widthMm * POINTS_PER_MM, MAX_PAGE_POINTS, true);
    }

    /** The same paper in landscape: the long side is horizontal. A thermal roll is not turned. */
    public Paper landscape() {
        return roll || isLandscape() ? this : new Paper(name, height, width);
    }

    /** The same paper in portrait: the long side is vertical. */
    public Paper portrait() {
        return isLandscape() ? new Paper(name, height, width) : this;
    }

    public boolean isLandscape() {
        return !roll && width > height;
    }

    /** The width in millimetres, for the printer. */
    public float widthMm() {
        return width / POINTS_PER_MM;
    }

    /** The height in millimetres, for the printer. */
    public float heightMm() {
        return height / POINTS_PER_MM;
    }

    /**
     * Reads "A4", "A5", "A4-landscape", "a5 landscape", "80mm", "roll-58mm"... and throws
     * {@link IllegalArgumentException} for any unknown name instead of silently falling back to a default size.
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
