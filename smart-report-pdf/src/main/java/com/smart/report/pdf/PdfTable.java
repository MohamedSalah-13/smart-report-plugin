package com.smart.report.pdf;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * جدول داخل {@link PdfDocument}: أعمدة بعناوينها وأوزانها، صفوف، وصفوف إجمالي.
 *
 * <p>الأعمدة والخلايا بتتكتب <b>بالترتيب المنطقي</b> — أول عمود هو اللي القارئ بيبدأ بيه — والمستند
 * هو اللي بيحط أول عمود على اليمين في الصفحة العربية وعلى اليسار في الإنجليزية. رأس الجدول بيتكرر
 * في أول كل صفحة يمتد عليها.</p>
 *
 * <p>وزن العمود نسبة من عرض الجدول، لكن أي عمود ما ينزلش عن عرض أطول كلمة فيه: الكلمة ما تتكسرش
 * والنص الأطول من عرض العمود بيلتف على أكتر من سطر.</p>
 */
public final class PdfTable {

    record Column(String header, float weight, Align align) {
    }

    record Row(String[] cells, boolean total) {
    }

    private final List<Column> columns = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private boolean headerShown = true;
    private float totalSizeBoost;

    private PdfTable() {
    }

    public static PdfTable create() {
        return new PdfTable();
    }

    /** عمود بوزن 1 ومحاذاة لبداية السطر. */
    public PdfTable column(String header) {
        return column(header, 1f, Align.START);
    }

    /** عمود بمحاذاة لبداية السطر. */
    public PdfTable column(String header, float weight) {
        return column(header, weight, Align.START);
    }

    /**
     * @param weight نصيب العمود من عرض الجدول منسوبًا لباقي الأعمدة (22 و14 و64 مثلًا)
     * @param align  محاذاة خلايا العمود؛ رأس العمود في المنتصف دائمًا
     */
    public PdfTable column(String header, float weight, Align align) {
        if (!(weight > 0)) {
            throw new IllegalArgumentException("Column weight must be positive.");
        }
        if (!rows.isEmpty()) {
            throw new IllegalStateException("Add every column before the first row.");
        }
        columns.add(new Column(header == null ? "" : header, weight, align == null ? Align.START : align));
        return this;
    }

    /** صف بيانات: خلية لكل عمود بالترتيب. الصف الأقصر بيتكمّل بخلايا فاضية، و{@code null} خلية فاضية. */
    public PdfTable row(String... cells) {
        rows.add(new Row(fitted(cells), false));
        return this;
    }

    /** صف لكل عنصر، و{@code cells} بتحوّل العنصر لخلاياه — مكان تنسيق المبالغ والتواريخ. */
    public <T> PdfTable rows(Iterable<T> items, Function<? super T, String[]> cells) {
        for (T item : items) {
            row(cells.apply(item));
        }
        return this;
    }

    /** صف إجمالي: بخط عريض على خلفية مميّزة، وخلاياه تحت أعمدتها بالظبط. */
    public PdfTable totals(String... cells) {
        rows.add(new Row(fitted(cells), true));
        return this;
    }

    /** جدول بلا رأس — للملخصات (بيان وقيمته) مثلًا. */
    public PdfTable withoutHeader() {
        this.headerShown = false;
        return this;
    }

    /** يكبّر خط صفوف الإجمالي بهذا القدر من النقط — للرقم اللي القارئ بيدوّر عليه. */
    PdfTable totalSizeBoost(float points) {
        this.totalSizeBoost = points;
        return this;
    }

    private String[] fitted(String[] cells) {
        if (columns.isEmpty()) {
            throw new IllegalStateException("Add the table's columns before its rows.");
        }
        int given = cells == null ? 0 : cells.length;
        if (given > columns.size()) {
            throw new IllegalArgumentException(
                    "The row has " + given + " cells but the table has " + columns.size() + " columns.");
        }
        String[] out = new String[columns.size()];
        for (int i = 0; i < out.length; i++) {
            String cell = i < given ? cells[i] : null;
            out[i] = cell == null ? "" : cell;
        }
        return out;
    }

    List<Column> columns() {
        return columns;
    }

    List<Row> rows() {
        return rows;
    }

    boolean headerShown() {
        return headerShown;
    }

    float totalSizeBoost() {
        return totalSizeBoost;
    }
}
