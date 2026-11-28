package com.smart.report.pdf;

import java.util.Objects;

/**
 * طلبات مهمة الطباعة غير الطابعة والورقة: عدد النسخ، ومدى الصفحات، والطباعة على الوجهين.
 *
 * <pre>{@code
 * PdfPrinter.print(pdf, printer, Paper.A4, PrintOptions.copies(2).pages(3, 5).duplex(PrintOptions.Duplex.LONG_EDGE));
 * }</pre>
 *
 * <p>الصنف immutable.</p>
 */
public final class PrintOptions {

    /** الوجهين: الطابعة اللي ما بتطبعش على الوجهين بتطبع على وجه واحد. */
    public enum Duplex {
        /** وجه واحد، حتى لو الطابعة افتراضيها الوجهين. */
        ONE_SIDED,
        /** الوجهين، والورقة بتتقلب على الضلع الطويل: الكتاب والكشف الطولي. */
        LONG_EDGE,
        /** الوجهين، والورقة بتتقلب على الضلع القصير: الصفحة بالعرض. */
        SHORT_EDGE
    }

    /** نسخة واحدة، كل الصفحات، والوجهين زي افتراضي الطابعة. */
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

    /** {@code count} نسخة، من 1 لـ {@value PdfPrinter#MAX_COPIES}. */
    public static PrintOptions copies(int count) {
        return new PrintOptions(count, 0, 0, null);
    }

    public PrintOptions withCopies(int count) {
        return new PrintOptions(count, firstPage, lastPage, duplex);
    }

    /**
     * الصفحات من {@code first} لـ {@code last} بس، من 1. آخر أكبر من صفحات المستند = لحد آخره.
     *
     * @throws IllegalArgumentException لو {@code first} أقل من 1 أو {@code last} أقل منه
     */
    public PrintOptions pages(int first, int last) {
        if (first < 1 || last < first) {
            throw new IllegalArgumentException("A page range is from 1 up: " + first + " to " + last);
        }
        return new PrintOptions(copies, first, last, duplex);
    }

    /** كل الصفحات. */
    public PrintOptions allPages() {
        return new PrintOptions(copies, 0, 0, duplex);
    }

    /** الوجهين؛ {@code null} = افتراضي الطابعة. */
    public PrintOptions duplex(Duplex value) {
        return new PrintOptions(copies, firstPage, lastPage, value);
    }

    public int copies() {
        return copies;
    }

    /** أول صفحة من 1، أو صفر = كل الصفحات. */
    public int firstPage() {
        return firstPage;
    }

    /** آخر صفحة، أو صفر = كل الصفحات. */
    public int lastPage() {
        return lastPage;
    }

    /** {@code null} = افتراضي الطابعة. */
    public Duplex duplex() {
        return duplex;
    }

    public boolean allPagesChosen() {
        return firstPage == 0;
    }

    /**
     * يقرا مدى صفحات زي ما المستخدم بيكتبه: {@code "3"}، {@code "2-5"}، {@code "2–5"}، بأرقام لاتيني أو عربي؛ فاضي =
     * كل الصفحات.
     *
     * @throws IllegalArgumentException لو مش رقم أو مدى صحيح من 1 لـ {@code pageCount}
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
