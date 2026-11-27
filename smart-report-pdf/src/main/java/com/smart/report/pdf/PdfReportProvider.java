package com.smart.report.pdf;

import com.smart.report.Alignment;
import com.smart.report.ColumnDefinition;
import com.smart.report.ReportException;
import com.smart.report.ReportProvider;
import com.smart.report.ReportRequest;
import com.smart.report.ReportType;
import com.smart.report.util.BeanPropertyReader;
import com.smart.report.util.TextDirection;
import com.smart.report.util.Totals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * يولّد تقرير PDF جدولي عام (عنوان + رأس أعمدة + صفوف بيانات) من أي List من الكائنات + قائمة
 * ColumnDefinition، بدون أي قالب. الرسم نفسه شغل {@link PdfDocument}: الجدول في اتجاه القراءة،
 * النص الطويل بيلتف بدل ما يتقطع، ورأس الأعمدة بيتكرر في كل صفحة.
 *
 * <p><b>الاتجاه:</b> من اليمين لليسار لو العنوان أو أي رأس عمود فيه عربي، وإلا من اليسار لليمين —
 * إلا لو {@link #RIGHT_TO_LEFT} اتحدد في {@code parameters}.</p>
 *
 * <p><b>القيم:</b> الأرقام بفواصل الآلاف والتواريخ بصيغة {@code yyyy-MM-dd} ({@link CellText})، وعمود
 * قيمه كلها أرقام بيتحاذى لنهاية السطر فالأرقام تيجي تحت بعض. خيارات كل عمود (نمط التنسيق، المحاذاة،
 * الوزن) في {@link ColumnDefinition} بتغلب الافتراضي؛ النمط اللي ما ينفعش مع قيمة بيرمي
 * {@link ReportException} باسم العمود.</p>
 *
 * <p><b>الإجمالي:</b> لو في عمود متجمّع ({@link ColumnDefinition#withTotal()})، صف إجمالي تحت الجدول: كل
 * مجموع تحت عموده بنمط العمود، والكلمة ({@link Totals#label}) تحت أول عمود مش متجمّع. المجموع بالظبط
 * ({@link java.math.BigDecimal})، والقيمة اللي مش رقم في عمود متجمّع بترمي {@link ReportException} باسم
 * العمود ورقم الصف (شوف {@link Totals}). من غير بيانات، الإجمالي صفر.</p>
 *
 * <p><b>الورق:</b> A4 طولي، إلا لو {@link #PAPER} اتحدد في {@code parameters} بكائن {@link Paper}
 * أو باسمه ({@code "A5"}، {@code "A4-landscape"}).</p>
 *
 * <p><b>الخط:</b> الخط العام ({@link PdfFontResolver})، إلا لو {@link #FONT} اتحدد بكائن {@link PdfFont}؛
 * و {@link #FAIL_ON_MISSING_GLYPHS} بيخلّي الحرف اللي مش في أي خط يرمي {@link ReportException} بدل
 * {@code ?}.</p>
 *
 * <p>لمستند فيه أكتر من جدول أو ترويسة أو ملخص استخدم {@link PdfDocument} مباشرة؛ وللتقارير ذات
 * التصميم المخصّص بصريًا استخدم وحدة smart-report-jasper.</p>
 */
public final class PdfReportProvider implements ReportProvider {

    /** مفتاح في {@code ReportRequest.parameters()}: {@link Paper} أو اسمه. */
    public static final String PAPER = "smart.report.pdf.paper";
    /** مفتاح في {@code ReportRequest.parameters()}: {@code Boolean} يحسم اتجاه التقرير. */
    public static final String RIGHT_TO_LEFT = "smart.report.pdf.rightToLeft";
    /**
     * مفتاح في {@code ReportRequest.parameters()}: {@link PdfFont} للتقرير ده بدل الخط العام
     * ({@link PdfDocument#font(PdfFont)}).
     */
    public static final String FONT = "smart.report.pdf.documentFont";
    /**
     * مفتاح في {@code ReportRequest.parameters()}: {@code Boolean.TRUE} = حرف مش في أي خط بيرمي
     * {@link ReportException} بدل ما يتطبع {@code ?} ({@link PdfDocument#failOnMissingGlyphs(boolean)}).
     */
    public static final String FAIL_ON_MISSING_GLYPHS = "smart.report.pdf.failOnMissingGlyphs";

    @Override
    public ReportType type() {
        return ReportType.PDF;
    }

    @Override
    public byte[] generate(ReportRequest<?> request) throws ReportException {
        List<ColumnDefinition> columns = request.columns();
        if (columns.isEmpty()) {
            throw new ReportException("At least one column is required to generate a PDF report.");
        }

        Paper paper = paper(request.parameters().get(PAPER));
        boolean rightToLeft = TextDirection.rightToLeft(request, RIGHT_TO_LEFT);
        PdfDocument document = rightToLeft ? PdfDocument.rtl(paper) : PdfDocument.ltr(paper);
        document.font(font(request.parameters().get(FONT)))
                .failOnMissingGlyphs(isTrue(request.parameters().get(FAIL_ON_MISSING_GLYPHS)));
        if (request.title() != null && !request.title().isBlank()) {
            document.title(singleLine(request.title()));
        }

        // القيم بتتقرا الأول: محاذاة العمود بتتحدد من نوع قيمه كلها.
        int count = columns.size();
        List<Object[]> rows = new ArrayList<>(request.data().size());
        boolean[] numeric = new boolean[count];
        boolean[] other = new boolean[count];
        BigDecimal[] sums = new BigDecimal[count];
        Arrays.fill(sums, BigDecimal.ZERO);
        for (Object item : request.data()) {
            Object[] values = new Object[count];
            for (int i = 0; i < count; i++) {
                ColumnDefinition column = columns.get(i);
                values[i] = BeanPropertyReader.read(item, column.fieldName());
                if (values[i] instanceof Number) {
                    numeric[i] = true;
                } else if (values[i] != null) {
                    other[i] = true;
                }
                if (column.total()) {
                    sums[i] = Totals.add(sums[i], values[i], column, rows.size());
                }
            }
            rows.add(values);
        }

        PdfTable table = PdfTable.create();
        for (int i = 0; i < count; i++) {
            ColumnDefinition column = columns.get(i);
            // العمود المتجمّع أرقام بس (غير كده بيرمي فوق)، فمجموعه تحت أرقامه حتى من غير بيانات.
            Align align = column.align() != null ? align(column.align())
                    : column.total() || numeric[i] && !other[i] ? Align.END : Align.START;
            table.column(singleLine(column.header()), column.weight(), align);
        }
        CellText text = new CellText(rightToLeft ? Locale.forLanguageTag("ar") : Locale.ENGLISH);
        for (Object[] values : rows) {
            String[] cells = new String[count];
            for (int i = 0; i < count; i++) {
                ColumnDefinition column = columns.get(i);
                try {
                    cells[i] = singleLine(text.of(values[i], column.format()));
                } catch (IllegalArgumentException e) {
                    throw new ReportException("Column '" + column.fieldName() + "': " + e.getMessage(), e);
                }
            }
            table.row(cells);
        }
        if (Totals.any(request)) {
            table.totals(totals(request, sums, text, rightToLeft));
        }
        return document.table(table).toBytes();
    }

    /** خلايا صف الإجمالي: كل مجموع بنمط عموده، والكلمة تحت أول عمود مش متجمّع. */
    private static String[] totals(ReportRequest<?> request, BigDecimal[] sums, CellText text, boolean rightToLeft)
            throws ReportException {
        List<ColumnDefinition> columns = request.columns();
        String[] cells = new String[columns.size()];
        for (int i = 0; i < cells.length; i++) {
            ColumnDefinition column = columns.get(i);
            if (column.total()) {
                try {
                    cells[i] = singleLine(text.of(sums[i], column.format()));
                } catch (IllegalArgumentException e) {
                    throw new ReportException("Column '" + column.fieldName() + "': " + e.getMessage(), e);
                }
            }
        }
        int labelAt = Totals.labelColumn(request);
        if (labelAt >= 0) {
            cells[labelAt] = singleLine(Totals.label(request, rightToLeft));
        }
        return cells;
    }

    private static Align align(Alignment alignment) {
        return switch (alignment) {
            case START -> Align.START;
            case CENTER -> Align.CENTER;
            case END -> Align.END;
        };
    }

    /** {@code Boolean.TRUE} أو النص {@code "true"} — قيمة جاية من ملف إعدادات مثلًا. */
    private static boolean isTrue(Object value) {
        return Boolean.TRUE.equals(value) || value instanceof String text && Boolean.parseBoolean(text.trim());
    }

    private static PdfFont font(Object value) throws ReportException {
        if (value == null || value instanceof PdfFont) {
            return (PdfFont) value;
        }
        throw new ReportException("Invalid " + FONT + " parameter: expected a PdfFont, for example"
                + " PdfFont.of(Path.of(\"fonts/Amiri-Regular.ttf\")), but got " + value.getClass().getName() + ".");
    }

    private static Paper paper(Object value) throws ReportException {
        if (value == null) {
            return Paper.A4;
        }
        if (value instanceof Paper paper) {
            return paper;
        }
        try {
            return Paper.parse(value.toString());
        } catch (IllegalArgumentException e) {
            throw new ReportException("Invalid " + PAPER + " parameter: " + e.getMessage(), e);
        }
    }

    /**
     * قيمة من البيانات في سطر واحد: أي سطر جديد أو tab جوّه القيمة بيبقى مسافة، فالصف الواحد من
     * البيانات يفضل صفًا واحدًا — والخلية بتلتف على عرض عمودها زي أي نص طويل.
     */
    private static String singleLine(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            out.append(Character.isISOControl(c) ? ' ' : c);
        }
        return out.toString();
    }
}
