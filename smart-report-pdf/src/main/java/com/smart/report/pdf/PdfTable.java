package com.smart.report.pdf;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

    /**
     * @param withPrevious الصف بيتنقل مع اللي قبله لصفحة جديدة: الإجمالي الفرعي مع آخر صف في مجموعته
     * @param grand        الإجمالي الكلي تحت إجماليات فرعية، بشكل يفرّقه عنها
     */
    /** @param joinNext الصف ده واللي بعده واحد: من غير خط بينهم، وبيتنقلوا مع بعض ({@link #rowJoinedToNext}) */
    record Row(int index, String[] cells, boolean total, boolean withPrevious, boolean grand, boolean joinNext) {
    }

    /**
     * قاعدة شكل الخلايا: بتتسأل لكل خلية في صفوف البيانات والإجمالي (مش الرأس)، وبترجع {@link CellStyle#PLAIN}
     * للخلية اللي تفضل زي صفها.
     */
    @FunctionalInterface
    public interface CellRule {

        /**
         * @param row    رقم الصف من صفر بترتيب الإضافة، صفوف الإجمالي معاهم
         * @param column رقم العمود من صفر بالترتيب المنطقي (أول عمود اتضاف)
         * @param text   نص الخلية زي ما اتكتب
         */
        CellStyle style(int row, int column, String text);
    }

    private final List<Column> columns = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final List<CellRule> rules = new ArrayList<>();
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
        rows.add(new Row(rows.size(), fitted(cells), false, joined(), false, false));
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
        rows.add(new Row(rows.size(), fitted(cells), true, joined(), false, false));
        return this;
    }

    /**
     * صف إجمالي فرعي تحت مجموعة صفوف: نفس شكل {@link #totals}، بس ما بينزلش لوحده في أول صفحة جديدة — لو ما
     * كفّاهوش الباقي، آخر صف قبله بيتنقل معاه.
     */
    PdfTable subtotal(String... cells) {
        if (rows.isEmpty()) {
            throw new IllegalStateException("A subtotal goes under the rows it adds up.");
        }
        rows.add(new Row(rows.size(), fitted(cells), true, true, false, false));
        return this;
    }

    /**
     * الإجمالي الكلي لجدول فيه إجماليات فرعية ({@link #subtotal}): نفس شكل {@link #totals} وفوقه خط سميك وتحته
     * خطين زي إجمالي الدفاتر، فما يتلخبطش مع آخر إجمالي فرعي اللي فوقه على طول.
     */
    PdfTable grandTotal(String... cells) {
        rows.add(new Row(rows.size(), fitted(cells), true, joined(), true, false));
        return this;
    }

    /**
     * شكل لخلايا بعينها: {@code (row, column, text) -> column == 2 && text.startsWith("-") ? CellStyle.color(0xC62828)
     * : CellStyle.PLAIN}. أكتر من قاعدة بيتجمعوا بالترتيب، واللي بعدها بتغلب في اللي حددته.
     */
    public PdfTable cellStyle(CellRule rule) {
        rules.add(Objects.requireNonNull(rule, "rule"));
        return this;
    }

    /**
     * المبالغ السالبة بالأحمر ({@link CellStyle#RED}) في الأعمدة دي (من صفر بالترتيب المنطقي)، أو في كل الأعمدة
     * من غيرها. السالب: رقم في أوله {@code -} أو {@code −}، أو في آخره {@code -} ({@code 1,200.00-})، أو بين قوسين
     * ({@code (1,200.00)}) زي الدفاتر.
     */
    public PdfTable negativeInRed(int... columns) {
        int[] chosen = columns == null ? new int[0] : columns.clone();
        return cellStyle((row, column, text) -> {
            boolean wanted = chosen.length == 0;
            for (int c : chosen) {
                wanted |= c == column;
            }
            return wanted && negative(text) ? CellStyle.color(CellStyle.RED) : CellStyle.PLAIN;
        });
    }

    /** نص رقم سالب: {@code -5}، {@code −5}، {@code 5-}، {@code (5)}، بأرقام لاتيني أو عربي. */
    static boolean negative(String text) {
        if (text == null) {
            return false;
        }
        String value = text.strip();
        if (value.length() < 2 || value.codePoints().noneMatch(Character::isDigit)) {
            return false;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        return first == '-' || first == '\u2212' || last == '-' || last == '\u2212' || first == '(' && last == ')';
    }

    /** شكل الخلية من القواعد كلها. */
    CellStyle styleOf(Row row, int column) {
        CellStyle style = CellStyle.PLAIN;
        for (CellRule rule : rules) {
            style = style.under(rule.style(row.index(), column, row.cells()[column]));
        }
        return style;
    }

    /**
     * صف من غير خط تحته، والصف اللي بعده معاه دايمًا في نفس الصفحة: اسم الصنف في سطر لوحده في الإيصال، وتحته الكمية
     * والسعر والإجمالي.
     */
    PdfTable rowJoinedToNext(String... cells) {
        rows.add(new Row(rows.size(), fitted(cells), false, joined(), false, true));
        return this;
    }

    /** الصف الجاي بيتنقل مع اللي قبله لو اللي قبله {@link #rowJoinedToNext}. */
    private boolean joined() {
        return !rows.isEmpty() && rows.get(rows.size() - 1).joinNext();
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
