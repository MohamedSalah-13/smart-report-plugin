package com.smart.report.pdf;

import com.smart.report.ColumnDefinition;
import com.smart.report.ReportException;
import com.smart.report.ReportProvider;
import com.smart.report.ReportRequest;
import com.smart.report.ReportType;
import com.smart.report.util.BeanPropertyReader;
import com.smart.report.util.TextDirection;

import java.util.ArrayList;
import java.util.List;

/**
 * يولّد تقرير PDF جدولي عام (عنوان + رأس أعمدة + صفوف بيانات) من أي List من الكائنات + قائمة
 * ColumnDefinition، بدون أي قالب. الرسم نفسه شغل {@link PdfDocument}: الجدول في اتجاه القراءة،
 * النص الطويل بيلتف بدل ما يتقطع، ورأس الأعمدة بيتكرر في كل صفحة.
 *
 * <p><b>الاتجاه:</b> من اليمين لليسار لو العنوان أو أي رأس عمود فيه عربي، وإلا من اليسار لليمين —
 * إلا لو {@link #RIGHT_TO_LEFT} اتحدد في {@code parameters}.</p>
 *
 * <p><b>القيم:</b> الأرقام بفواصل الآلاف والتواريخ بصيغة {@code yyyy-MM-dd} ({@link CellText})، وعمود
 * قيمه كلها أرقام بيتحاذى لنهاية السطر فالأرقام تيجي تحت بعض.</p>
 *
 * <p><b>الورق:</b> A4 طولي، إلا لو {@link #PAPER} اتحدد في {@code parameters} بكائن {@link Paper}
 * أو باسمه ({@code "A5"}، {@code "A4-landscape"}).</p>
 *
 * <p>لمستند فيه أكتر من جدول أو ترويسة أو ملخص استخدم {@link PdfDocument} مباشرة؛ وللتقارير ذات
 * التصميم المخصّص بصريًا استخدم وحدة smart-report-jasper.</p>
 */
public final class PdfReportProvider implements ReportProvider {

    /** مفتاح في {@code ReportRequest.parameters()}: {@link Paper} أو اسمه. */
    public static final String PAPER = "smart.report.pdf.paper";
    /** مفتاح في {@code ReportRequest.parameters()}: {@code Boolean} يحسم اتجاه التقرير. */
    public static final String RIGHT_TO_LEFT = "smart.report.pdf.rightToLeft";

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
        PdfDocument document = TextDirection.rightToLeft(request, RIGHT_TO_LEFT)
                ? PdfDocument.rtl(paper) : PdfDocument.ltr(paper);
        if (request.title() != null && !request.title().isBlank()) {
            document.title(singleLine(request.title()));
        }

        // القيم بتتقرا الأول: محاذاة العمود بتتحدد من نوع قيمه كلها.
        int count = columns.size();
        List<Object[]> rows = new ArrayList<>(request.data().size());
        boolean[] numeric = new boolean[count];
        boolean[] other = new boolean[count];
        for (Object item : request.data()) {
            Object[] values = new Object[count];
            for (int i = 0; i < count; i++) {
                values[i] = BeanPropertyReader.read(item, columns.get(i).fieldName());
                if (values[i] instanceof Number) {
                    numeric[i] = true;
                } else if (values[i] != null) {
                    other[i] = true;
                }
            }
            rows.add(values);
        }

        PdfTable table = PdfTable.create();
        for (int i = 0; i < count; i++) {
            table.column(singleLine(columns.get(i).header()), 1f, numeric[i] && !other[i] ? Align.END : Align.START);
        }
        CellText text = new CellText();
        for (Object[] values : rows) {
            String[] cells = new String[count];
            for (int i = 0; i < count; i++) {
                cells[i] = singleLine(text.of(values[i]));
            }
            table.row(cells);
        }
        return document.table(table).toBytes();
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
