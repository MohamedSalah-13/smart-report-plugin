package com.smart.report;

import com.smart.report.jasper.JasperExportFormat;

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

    private ReportRequest(Builder<T> builder) {
        this.title = builder.title;
        this.data = List.copyOf(builder.data);
        this.columns = List.copyOf(builder.columns);
        this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(builder.parameters));
        this.template = builder.template;
        this.exportFormat = builder.exportFormat;
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

        public ReportRequest<T> build() {
            return new ReportRequest<>(this);
        }
    }
}
