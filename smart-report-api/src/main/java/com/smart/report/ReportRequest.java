package com.smart.report;


import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * حاوية موحّدة لبيانات أي تقرير (PDF عام / Excel عام / Jasper).
 * <ul>
 *     <li>لتقارير PDF/Excel العامة: استخدم {@code data} + {@code columns}.</li>
 *     <li>لتقارير Jasper: استخدم {@code data} + {@code template} (+ {@code parameters} و{@code exportFormat} اختياريًا).</li>
 * </ul>
 */
public final class ReportRequest<T> {

    private final String title;
    private final List<T> data;
    private final List<ColumnDefinition> columns;
    private final Map<String, Object> parameters;
    private final String template;
    private final JasperExportFormat exportFormat;
    private final String totalsLabel;

    private ReportRequest(Builder<T> builder) {
        this.title = builder.title;
        this.data = immutableCopy(builder.data, "data");
        this.columns = immutableCopy(builder.columns, "columns");
        // القيم الفاضية مسموحة هنا: باراميترات Jasper بتتبعت null عن قصد أحيانًا
        this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(builder.parameters));
        this.template = builder.template;
        this.exportFormat = builder.exportFormat;
        this.totalsLabel = builder.totalsLabel;
    }

    /**
     * نسخة غير قابلة للتعديل مع رسالة واضحة عند وجود عنصر فاضي.
     * {@code List.copyOf} كانت بترمي NullPointerException بدون أي رسالة، فالمستخدم
     * ما كانش يعرف أنهي صف في بياناته هو السبب.
     */
    private static <E> List<E> immutableCopy(List<E> source, String name) {
        List<E> copy = new ArrayList<>(source.size());
        for (int i = 0; i < source.size(); i++) {
            E element = source.get(i);
            if (element == null) {
                throw new IllegalArgumentException(
                        name + "[" + i + "] is null; " + name + " must not contain null entries.");
            }
            copy.add(element);
        }
        return Collections.unmodifiableList(copy);
    }

    public String title() {
        return title;
    }

    public List<T> data() {
        return data;
    }

    public List<ColumnDefinition> columns() {
        return columns;
    }

    public Map<String, Object> parameters() {
        return parameters;
    }

    public String template() {
        return template;
    }

    public JasperExportFormat exportFormat() {
        return exportFormat;
    }

    /**
     * الكلمة اللي في أول صف الإجمالي، زي ما البرنامج حددها؛ {@code null} = الافتراضي حسب اتجاه التقرير
     * («الإجمالى» أو «Total»، شوف {@link com.smart.report.util.Totals#label}).
     */
    public String totalsLabel() {
        return totalsLabel;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static final class Builder<T> {
        private String title = "";
        private List<T> data = new ArrayList<>();
        private final List<ColumnDefinition> columns = new ArrayList<>();
        private final Map<String, Object> parameters = new LinkedHashMap<>();
        private String template;
        private JasperExportFormat exportFormat = JasperExportFormat.PDF;
        private String totalsLabel;

        public Builder<T> title(String title) {
            this.title = title == null ? "" : title;
            return this;
        }

        public Builder<T> data(List<T> data) {
            this.data = data == null ? new ArrayList<>() : new ArrayList<>(data);
            return this;
        }

        public Builder<T> columns(List<ColumnDefinition> columns) {
            this.columns.clear();
            if (columns != null) this.columns.addAll(columns);
            return this;
        }

        public Builder<T> addColumn(String fieldName, String header) {
            this.columns.add(ColumnDefinition.of(fieldName, header));
            return this;
        }

        /** عمود بخياراته: {@code addColumn(ColumnDefinition.of("amount", "المبلغ").withFormat("#,##0.00"))}. */
        public Builder<T> addColumn(ColumnDefinition column) {
            if (column == null) {
                throw new IllegalArgumentException("column is required.");
            }
            this.columns.add(column);
            return this;
        }

        public Builder<T> parameter(String key, Object value) {
            this.parameters.put(key, value);
            return this;
        }

        public Builder<T> parameters(Map<String, Object> parameters) {
            this.parameters.clear();
            if (parameters != null) this.parameters.putAll(parameters);
            return this;
        }

        /** مسار قالب Jasper: إما مسار ملف على القرص أو مسار مورد على الـ classpath (ينتهي بـ .jrxml أو .jasper). */
        public Builder<T> template(String template) {
            this.template = template;
            return this;
        }

        public Builder<T> exportFormat(JasperExportFormat exportFormat) {
            this.exportFormat = exportFormat == null ? JasperExportFormat.PDF : exportFormat;
            return this;
        }

        /**
         * الكلمة اللي في أول صف الإجمالي، تحت أول عمود مش متجمّع ({@link ColumnDefinition#withTotal()}):
         * {@code "المجموع"} أو {@code "Grand total"} مثلًا. {@code null} يرجّع الافتراضي حسب اتجاه التقرير
         * («الإجمالى» أو «Total»)، والنص الفاضي بيسيب الخانة فاضية.
         */
        public Builder<T> totalsLabel(String label) {
            this.totalsLabel = label;
            return this;
        }

        public ReportRequest<T> build() {
            return new ReportRequest<>(this);
        }
    }
}
