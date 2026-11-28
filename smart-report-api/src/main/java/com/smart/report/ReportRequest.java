package com.smart.report;


import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One container for the data of any report (generic PDF / generic Excel / Jasper).
 * <ul>
 *     <li>For the generic PDF/Excel reports: use {@code data} + {@code columns}.</li>
 *     <li>For Jasper reports: use {@code data} + {@code template} (+ optional {@code parameters} and
 *     {@code exportFormat}).</li>
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
    private final String groupBy;
    private final String subtotalLabel;
    private final boolean groupHeader;
    private final boolean pageBreakPerGroup;

    private ReportRequest(Builder<T> builder) {
        this.title = builder.title;
        this.data = immutableCopy(builder.data, "data");
        this.columns = immutableCopy(builder.columns, "columns");
        // القيم الفاضية مسموحة هنا: باراميترات Jasper بتتبعت null عن قصد أحيانًا
        this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(builder.parameters));
        this.template = builder.template;
        this.exportFormat = builder.exportFormat;
        this.totalsLabel = builder.totalsLabel;
        this.groupBy = builder.groupBy;
        this.subtotalLabel = builder.subtotalLabel;
        this.groupHeader = builder.groupHeader;
        this.pageBreakPerGroup = builder.pageBreakPerGroup;
    }

    /**
     * An unmodifiable copy, with a clear message when an element is null.
     * {@code List.copyOf} threw a NullPointerException with no message, so the user
     * could not tell which row in their data caused it.
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
     * The word at the start of the total row, as the program set it; {@code null} = the default for the report's
     * direction («الإجمالى» or «Total», see {@link com.smart.report.util.Totals#label}).
     */
    public String totalsLabel() {
        return totalsLabel;
    }

    /**
     * The property that splits the rows of the generic report (PDF and Excel) into groups, with a subtotal row
     * under each group; {@code null} = no groups (see {@link Builder#groupBy(String)}).
     */
    public String groupBy() {
        return groupBy;
    }

    /**
     * The text before the group value in the subtotal row, as the program set it; {@code null} = the default
     * for the report's direction (see {@link com.smart.report.util.Totals#subtotalLabel}).
     */
    public String subtotalLabel() {
        return subtotalLabel;
    }

    /** A line with the group value above the group's rows ({@link Builder#groupHeader}). */
    public boolean groupHeader() {
        return groupHeader;
    }

    /** Each group on a new page ({@link Builder#pageBreakPerGroup}). */
    public boolean pageBreakPerGroup() {
        return pageBreakPerGroup;
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
        private String groupBy;
        private String subtotalLabel;
        private boolean groupHeader;
        private boolean pageBreakPerGroup;

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

        /** A column with options: {@code addColumn(ColumnDefinition.of("amount", "المبلغ").withFormat("#,##0.00"))}. */
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

        /**
         * The Jasper template path: either a file path on disk or a resource path on the classpath (ending in
         * .jrxml or .jasper).
         */
        public Builder<T> template(String template) {
            this.template = template;
            return this;
        }

        public Builder<T> exportFormat(JasperExportFormat exportFormat) {
            this.exportFormat = exportFormat == null ? JasperExportFormat.PDF : exportFormat;
            return this;
        }

        /**
         * The word at the start of the total row, under the first column that is not summed
         * ({@link ColumnDefinition#withTotal()}): for example {@code "المجموع"} or {@code "Grand total"}.
         * {@code null} restores the default for the report's direction («الإجمالى» or «Total»), and an empty
         * string leaves the cell empty.
         */
        public Builder<T> totalsLabel(String label) {
            this.totalsLabel = label;
            return this;
        }

        /**
         * Splits the rows of the generic report (PDF and Excel) into groups by a property. Under each group comes
         * a subtotal row for the summed columns ({@link ColumnDefinition#withTotal()}), and the grand total stays
         * at the end as before. For example, {@code groupBy("customer")} for a statement per customer.
         *
         * <p>The rows are not sorted: they come in the order of {@link #data}, and a new group starts wherever the
         * value differs from the previous row (numbers compare by value, so {@code 1.0} and {@code 1.00} are one
         * group). Sort the data by this property first. The property does not have to be a column in the report.
         * A report with groups must have at least one summed column. Jasper reports do not use this option.</p>
         *
         * @param fieldName the property, read like any column; {@code null} or an empty string = no groups
         */
        public Builder<T> groupBy(String fieldName) {
            this.groupBy = fieldName == null || fieldName.isBlank() ? null : fieldName;
            return this;
        }

        /**
         * The text before the group value in the subtotal row ({@link #groupBy}): {@code "مجموع "} prints
         * «مجموع أحمد». {@code null} restores the default for the report's direction («إجمالى: » or «Total: »).
         */
        public Builder<T> subtotalLabel(String label) {
            this.subtotalLabel = label;
            return this;
        }

        /**
         * A line with the group value above each group's rows ({@link #groupBy}). In the PDF it is a heading that
         * is never left alone at the bottom of a page; in Excel it is a bold row. Requires {@link #groupBy}.
         */
        public Builder<T> groupHeader(boolean value) {
            this.groupHeader = value;
            return this;
        }

        /**
         * Each group ({@link #groupBy}) starts on a new page, so each customer's statement can be sent on its own;
         * the grand total comes under the last group. In Excel this is a print page break after each subtotal.
         * Requires {@link #groupBy}.
         */
        public Builder<T> pageBreakPerGroup(boolean value) {
            this.pageBreakPerGroup = value;
            return this;
        }

        public ReportRequest<T> build() {
            return new ReportRequest<>(this);
        }
    }
}
