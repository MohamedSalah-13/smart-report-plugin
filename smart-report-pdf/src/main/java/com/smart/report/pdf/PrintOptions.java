package com.smart.report.pdf;

import java.util.Objects;

/**
 * Print job options other than the printer and the paper: the number of copies, the page range, and two-sided
 * printing.
 *
 * <pre>{@code
 * PdfPrinter.print(pdf, printer, Paper.A4, PrintOptions.copies(2).pages(3, 5).duplex(PrintOptions.Duplex.LONG_EDGE));
 * }</pre>
 *
 * <p>The class is immutable.</p>
 */
public final class PrintOptions {

    /** Two-sided printing: a printer that cannot print on both sides prints on one side. */
    public enum Duplex {
        /** One side, even if the printer defaults to two sides. */
        ONE_SIDED,
        /** Both sides, flipping the sheet on the long edge: a book or a portrait statement. */
        LONG_EDGE,
        /** Both sides, flipping the sheet on the short edge: a landscape page. */
        SHORT_EDGE
    }

    /** One copy, all pages, and two-sided printing as the printer's default. */
    public static final PrintOptions DEFAULT = new PrintOptions(1, 0, 0, null);

    private final int copies;
    private final int firstPage;
    private final int lastPage;
    private final Duplex duplex;

    private PrintOptions(int copies, int firstPage, int lastPage, Duplex duplex) {
        if (copies < 1 || copies > PdfPrinter.MAX_COPIES) {
            throw new IllegalArgumentException("Copies must be between 1 and " + PdfPrinter.MAX_COPIES + ": " + copies);
        }
        this.copies = copies;
        this.firstPage = firstPage;
        this.lastPage = lastPage;
        this.duplex = duplex;
    }

    /** {@code count} copies, from 1 to {@value PdfPrinter#MAX_COPIES}. */
    public static PrintOptions copies(int count) {
        return new PrintOptions(count, 0, 0, null);
    }

    public PrintOptions withCopies(int count) {
        return new PrintOptions(count, firstPage, lastPage, duplex);
    }

    /**
     * Only the pages from {@code first} to {@code last}, counting from 1. A last page beyond the document's pages
     * means up to its end.
     *
     * @throws IllegalArgumentException if {@code first} is less than 1 or {@code last} is less than {@code first}
     */
    public PrintOptions pages(int first, int last) {
        if (first < 1 || last < first) {
            throw new IllegalArgumentException("A page range is from 1 up: " + first + " to " + last);
        }
        return new PrintOptions(copies, first, last, duplex);
    }

    /** All pages. */
    public PrintOptions allPages() {
        return new PrintOptions(copies, 0, 0, duplex);
    }

    /** Two-sided printing; {@code null} = the printer's default. */
    public PrintOptions duplex(Duplex value) {
        return new PrintOptions(copies, firstPage, lastPage, value);
    }

    public int copies() {
        return copies;
    }

    /** The first page, counting from 1, or zero = all pages. */
    public int firstPage() {
        return firstPage;
    }

    /** The last page, or zero = all pages. */
    public int lastPage() {
        return lastPage;
    }

    /** {@code null} = the printer's default. */
    public Duplex duplex() {
        return duplex;
    }

    public boolean allPagesChosen() {
        return firstPage == 0;
    }

    /**
     * Reads a page range as the user types it: {@code "3"}, {@code "2-5"}, {@code "2–5"}, in Latin or Arabic
     * digits; empty = all pages.
     *
     * @throws IllegalArgumentException if it is not a number or a valid range from 1 to {@code pageCount}
     */
    public PrintOptions pages(String range, int pageCount) {
        String text = range == null ? "" : range.strip();
        if (text.isEmpty()) {
            return allPages();
        }
        String[] parts = text.split("\\s*[-–—]\\s*", -1);
        if (parts.length > 2) {
            throw new IllegalArgumentException("Not a page range: " + range);
        }
        int first = number(parts[0], range);
        int last = parts.length == 2 ? number(parts[1], range) : first;
        if (first < 1 || last < first || last > pageCount) {
            throw new IllegalArgumentException("The pages must be from 1 to " + pageCount + ": " + range);
        }
        return pages(first, last);
    }

    private static int number(String digits, String range) {
        if (digits.isEmpty() || digits.length() > 6) {
            throw new IllegalArgumentException("Not a page range: " + range);
        }
        int value = 0;
        for (int i = 0; i < digits.length(); i++) {
            int digit = Character.digit(digits.charAt(i), 10);
            if (digit < 0) {
                throw new IllegalArgumentException("Not a page range: " + range);
            }
            value = value * 10 + digit;
        }
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PrintOptions that && copies == that.copies && firstPage == that.firstPage
                && lastPage == that.lastPage && duplex == that.duplex;
    }

    @Override
    public int hashCode() {
        return Objects.hash(copies, firstPage, lastPage, duplex);
    }

    @Override
    public String toString() {
        return "PrintOptions[copies=" + copies + ", pages=" + (firstPage == 0 ? "all" : firstPage + "-" + lastPage)
                + ", duplex=" + duplex + "]";
    }
}
