package com.smart.report.pdf;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An invoice or receipt ready to draw ({@link InvoicePdf}). Everything is text already formatted and translated in
 * the program's language, and the library lays it out on a page or on a roll. The same object draws on A4, A5 or
 * an 80 / 58 mm roll, so a saved invoice can print in any form.
 *
 * <pre>{@code
 * Invoice invoice = Invoice.builder(true)
 *         .letterhead("مؤسسة النور", "ت: 0100 000 0000")
 *         .title("فاتورة بيع").number("1751").date("2026-11-28 10:30")
 *         .field("العميل", "أحمد علي").field("الكاشير", "منى")
 *         .line(InvoiceLine.of("مسمار 10 مم", "12", "2.50", "30.00"))
 *         .total("الإجمالى", "30.00").total("الضريبة", "4.20").grandTotal("الصافى", "34.20")
 *         .amountInWords(AmountInWords.arabic(net, Currency.EGP))
 *         .shopNote("البضاعة المباعة لا ترد بعد 14 يومًا")
 *         .code(DocumentCode.DEFAULT.encode(1, 1751))
 *         .build();
 * }</pre>
 */
public final class Invoice {

    private final boolean rightToLeft;
    private final String letterheadName;
    private final List<String> letterheadLines;
    private final byte[] logo;
    private final String title;
    private final String number;
    private final String date;
    private final List<String[]> fields;
    private final List<InvoiceLine> lines;
    private final List<String[]> totals;
    private final String[] grandTotal;
    private final String amountInWords;
    private final String notes;
    private final String shopNote;
    private final String code;
    private final String qr;
    private final String[] headers;

    private Invoice(Builder builder) {
        this.rightToLeft = builder.rightToLeft;
        this.letterheadName = builder.letterheadName;
        this.letterheadLines = List.copyOf(builder.letterheadLines);
        this.logo = builder.logo;
        this.title = builder.title;
        this.number = builder.number;
        this.date = builder.date;
        this.fields = List.copyOf(builder.fields);
        this.lines = List.copyOf(builder.lines);
        this.totals = List.copyOf(builder.totals);
        this.grandTotal = builder.grandTotal;
        this.amountInWords = builder.amountInWords;
        this.notes = builder.notes;
        this.shopNote = builder.shopNote;
        this.code = builder.code;
        this.qr = builder.qr;
        this.headers = builder.headers.clone();
    }

    /** @param rightToLeft an Arabic invoice (the default column headers in Arabic), or English */
    public static Builder builder(boolean rightToLeft) {
        return new Builder(rightToLeft);
    }

    public boolean rightToLeft() {
        return rightToLeft;
    }

    /** The invoice on the paper: a page in this layout, or a receipt if the paper is a roll ({@link InvoicePdf#of}). */
    public PdfDocument toPdf(Paper paper, InvoiceLayout layout) {
        return InvoicePdf.of(this, paper, layout);
    }

    String letterheadName() {
        return letterheadName;
    }

    List<String> letterheadLines() {
        return letterheadLines;
    }

    byte[] logo() {
        return logo;
    }

    String title() {
        return title;
    }

    String number() {
        return number;
    }

    String date() {
        return date;
    }

    List<String[]> fields() {
        return fields;
    }

    public List<InvoiceLine> lines() {
        return lines;
    }

    List<String[]> totals() {
        return totals;
    }

    String[] grandTotal() {
        return grandTotal;
    }

    String amountInWords() {
        return amountInWords;
    }

    String notes() {
        return notes;
    }

    String shopNote() {
        return shopNote;
    }

    String code() {
        return code;
    }

    String qr() {
        return qr;
    }

    /** The column headers: item, quantity, price, total, code, unit, line number. */
    String[] headers() {
        return headers;
    }

    public static final class Builder {
        private final boolean rightToLeft;
        private String letterheadName = "";
        private final List<String> letterheadLines = new ArrayList<>();
        private byte[] logo;
        private String title = "";
        private String number = "";
        private String date = "";
        private final List<String[]> fields = new ArrayList<>();
        private final List<InvoiceLine> lines = new ArrayList<>();
        private final List<String[]> totals = new ArrayList<>();
        private String[] grandTotal;
        private String amountInWords = "";
        private String notes = "";
        private String shopNote = "";
        private String code = "";
        private String qr = "";
        private String[] headers;

        private Builder(boolean rightToLeft) {
            this.rightToLeft = rightToLeft;
            this.headers = rightToLeft
                    ? new String[]{"الصنف", "الكمية", "السعر", "الإجمالى", "الكود", "الوحدة", "م"}
                    : new String[]{"Item", "Qty", "Price", "Total", "Code", "Unit", "#"};
        }

        /** The shop letterhead: the name and lines under it. */
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

        /** A PNG or JPEG logo beside the letterhead. */
        public Builder logo(byte[] image) {
            this.logo = image == null || image.length == 0 ? null : image.clone();
            return this;
        }

        /** For example «فاتورة بيع» (sales invoice), «فاتورة شراء» (purchase invoice), «مرتجع» (return). */
        public Builder title(String value) {
            this.title = value == null ? "" : value;
            return this;
        }

        public Builder number(String value) {
            this.number = value == null ? "" : value;
            return this;
        }

        public Builder date(String value) {
            this.date = value == null ? "" : value;
            return this;
        }

        /** A detail under the title: the customer, the cashier, the payment method. */
        public Builder field(String label, String value) {
            fields.add(new String[]{Objects.requireNonNull(label, "label"), value == null ? "" : value});
            return this;
        }

        public Builder line(InvoiceLine line) {
            lines.add(Objects.requireNonNull(line, "line"));
            return this;
        }

        public Builder lines(Iterable<InvoiceLine> all) {
            for (InvoiceLine line : all) {
                line(line);
            }
            return this;
        }

        /** A line in the summary under the table: the total, the discount, the tax. */
        public Builder total(String label, String value) {
            totals.add(new String[]{Objects.requireNonNull(label, "label"), value == null ? "" : value});
            return this;
        }

        /** The line the reader looks for, in a larger font: the net amount, the amount due. */
        public Builder grandTotal(String label, String value) {
            this.grandTotal = new String[]{Objects.requireNonNull(label, "label"), value == null ? "" : value};
            return this;
        }

        /** The amount in words ({@code AmountInWords}). */
        public Builder amountInWords(String value) {
            this.amountInWords = value == null ? "" : value;
            return this;
        }

        public Builder notes(String value) {
            this.notes = value == null ? "" : value;
            return this;
        }

        /** The shop's fixed sentence at the end of the invoice: a return policy, a thank-you. */
        public Builder shopNote(String value) {
            this.shopNote = value == null ? "" : value;
            return this;
        }

        /** A code to scan under the invoice (Code 128 barcode), e.g. {@code DocumentCode.DEFAULT.encode(...)}. */
        public Builder code(String value) {
            this.code = value == null ? "" : value;
            return this;
        }

        /** The text of a QR code at the end of the invoice, for example the tax invoice code. */
        public Builder qr(String value) {
            this.qr = value == null ? "" : value;
            return this;
        }

        /**
         * The column headers in the program's own terms: item, quantity, price, total (code, unit and line number
         * are optional).
         */
        public Builder headers(String item, String quantity, String price, String total) {
            headers[0] = Objects.requireNonNull(item);
            headers[1] = Objects.requireNonNull(quantity);
            headers[2] = Objects.requireNonNull(price);
            headers[3] = Objects.requireNonNull(total);
            return this;
        }

        public Builder codeUnitAndNumberHeaders(String code, String unit, String lineNumber) {
            headers[4] = Objects.requireNonNull(code);
            headers[5] = Objects.requireNonNull(unit);
            headers[6] = Objects.requireNonNull(lineNumber);
            return this;
        }

        public Invoice build() {
            return new Invoice(this);
        }
    }
}
