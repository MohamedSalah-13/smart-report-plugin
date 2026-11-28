package com.smart.report.pdf;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An account statement ready to draw ({@link #toPdf}): the statement header, key figures (opening balance, total
 * debit and total credit), the transactions with the balance after each one, and the closing balance. All values
 * are text formatted in the program's language; a negative balance is printed in red.
 *
 * <pre>{@code
 * AccountStatement statement = AccountStatement.builder(true)
 *         .letterhead("مؤسسة النور").title("كشف حساب عميل")
 *         .field("العميل", "أحمد علي").field("الفترة", "2026-11-01 : 2026-11-30")
 *         .figure("رصيد أول المدة", "1,000.00").figure("إجمالى المدين", "2,500.00")
 *         .line("2026-11-02", "فاتورة 1751", "2,500.00", "", "3,500.00")
 *         .line("2026-11-10", "سند قبض 88", "", "4,000.00", "-500.00")
 *         .closing("الرصيد الختامى", "-500.00")
 *         .build();
 * PdfDocument document = statement.toPdf(Paper.A4);
 * }</pre>
 */
public final class AccountStatement {

    private final boolean rightToLeft;
    private final String letterheadName;
    private final List<String> letterheadLines;
    private final String title;
    private final List<String[]> fields;
    private final List<String[]> figures;
    private final List<String[]> lines;
    private final String[] closing;
    private final String[] headers;

    private AccountStatement(Builder builder) {
        this.rightToLeft = builder.rightToLeft;
        this.letterheadName = builder.letterheadName;
        this.letterheadLines = List.copyOf(builder.letterheadLines);
        this.title = builder.title;
        this.fields = List.copyOf(builder.fields);
        this.figures = List.copyOf(builder.figures);
        this.lines = List.copyOf(builder.lines);
        this.closing = builder.closing;
        this.headers = builder.headers.clone();
    }

    public static Builder builder(boolean rightToLeft) {
        return new Builder(rightToLeft);
    }

    /** The number of transactions. */
    public int size() {
        return lines.size();
    }

    /**
     * The statement on the paper. On A4 / A5 it is a five-column table. On a thermal roll each transaction takes two
     * lines (the date and description, with debit, credit and balance under them) so it fits the width.
     */
    public PdfDocument toPdf(Paper paper) {
        Objects.requireNonNull(paper, "paper");
        PdfDocument document = rightToLeft ? PdfDocument.rtl(paper) : PdfDocument.ltr(paper);
        if (!letterheadName.isBlank() || !letterheadLines.isEmpty()) {
            document.letterhead(letterheadName, letterheadLines.toArray(String[]::new));
        }
        document.style(PdfStyle.forPaper(paper).withGrid(!paper.roll()));
        if (!title.isBlank()) {
            document.title(title);
        }
        for (String[] field : fields) {
            document.field(field[0], field[1]);
        }
        for (String[] figure : figures) {
            document.field(figure[0], figure[1]);
        }
        PdfTable table = PdfTable.create();
        if (paper.roll()) {
            table.column(headers[2], 1f, Align.END).column(headers[3], 1f, Align.END)
                    .column(headers[4], 1.2f, Align.END).negativeInRed(2);
            for (String[] line : lines) {
                table.rowJoinedToNext((line[0] + "  " + line[1]).strip(), "", "");
                table.row(line[2], line[3], line[4]);
            }
        } else {
            table.column(headers[0], 1.4f).column(headers[1], 3f).column(headers[2], 1.3f, Align.END)
                    .column(headers[3], 1.3f, Align.END).column(headers[4], 1.4f, Align.END).negativeInRed(4);
            for (String[] line : lines) {
                table.row(line);
            }
        }
        document.table(table);
        if (closing != null) {
            // الرصيد الختامي السالب بالأحمر زي الأرصدة اللي في الجدول.
            document.summaryTotalNegativeInRed(closing[0], closing[1]);
        }
        return document;
    }

    public static final class Builder {
        private final boolean rightToLeft;
        private String letterheadName = "";
        private final List<String> letterheadLines = new ArrayList<>();
        private String title = "";
        private final List<String[]> fields = new ArrayList<>();
        private final List<String[]> figures = new ArrayList<>();
        private final List<String[]> lines = new ArrayList<>();
        private String[] closing;
        private final String[] headers;

        private Builder(boolean rightToLeft) {
            this.rightToLeft = rightToLeft;
            this.headers = rightToLeft
                    ? new String[]{"التاريخ", "البيان", "مدين", "دائن", "الرصيد"}
                    : new String[]{"Date", "Description", "Debit", "Credit", "Balance"};
        }

        public Builder letterhead(String name, String... lines) {
            this.letterheadName = name == null ? "" : name;
            this.letterheadLines.clear();
            if (lines != null) {
                for (String line : lines) {
                    if (line != null && !line.isBlank()) {
                        letterheadLines.add(line);
                    }
                }
            }
            return this;
        }

        public Builder title(String value) {
            this.title = value == null ? "" : value;
            return this;
        }

        /** The statement header: the customer, the account number, the period. */
        public Builder field(String label, String value) {
            fields.add(new String[]{Objects.requireNonNull(label, "label"), value == null ? "" : value});
            return this;
        }

        /** A key figure above the transactions: the opening balance, the total debit, the total credit. */
        public Builder figure(String label, String value) {
            figures.add(new String[]{Objects.requireNonNull(label, "label"), value == null ? "" : value});
            return this;
        }

        /**
         * A transaction: the date, the description, the debit, the credit (one of them is usually empty), and the
         * balance after it.
         */
        public Builder line(String date, String description, String debit, String credit, String balance) {
            lines.add(new String[]{blank(date), blank(description), blank(debit), blank(credit), blank(balance)});
            return this;
        }

        public Builder closing(String label, String value) {
            this.closing = new String[]{Objects.requireNonNull(label, "label"), blank(value)};
            return this;
        }

        public Builder headers(String date, String description, String debit, String credit, String balance) {
            headers[0] = Objects.requireNonNull(date);
            headers[1] = Objects.requireNonNull(description);
            headers[2] = Objects.requireNonNull(debit);
            headers[3] = Objects.requireNonNull(credit);
            headers[4] = Objects.requireNonNull(balance);
            return this;
        }

        private static String blank(String value) {
            return value == null ? "" : value;
        }

        public AccountStatement build() {
            return new AccountStatement(this);
        }
    }
}
