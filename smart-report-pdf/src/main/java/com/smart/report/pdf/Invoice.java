package com.smart.report.pdf;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * فاتورة أو إيصال جاهز للرسم ({@link InvoicePdf}): كل حاجة نص منسّق ومترجم بلغة البرنامج، والمكتبة بترتّبه على
 * صفحة أو على لفة. نفس الكائن بيترسم على A4 أو A5 أو لفة 80 / 58 مم، فالفاتورة المحفوظة تتطبع بأي شكل.
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

    /** @param rightToLeft فاتورة عربية (عناوين الأعمدة الافتراضية بالعربي)، أو إنجليزي */
    public static Builder builder(boolean rightToLeft) {
        return new Builder(rightToLeft);
    }

    public boolean rightToLeft() {
        return rightToLeft;
    }

    /** الفاتورة على الورقة: صفحة بالشكل ده، أو إيصال لو الورقة لفة ({@link InvoicePdf#of}). */
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

    /** عناوين الأعمدة: الصنف، الكمية، السعر، الإجمالي، الكود، الوحدة، م. */
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

        /** ترويسة المحل: الاسم وسطور تحته. */
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

        /** شعار PNG أو JPEG جنب الترويسة. */
        public Builder logo(byte[] image) {
            this.logo = image == null || image.length == 0 ? null : image.clone();
            return this;
        }

        /** «فاتورة بيع»، «فاتورة شراء»، «مرتجع». */
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

        /** بيان تحت العنوان: العميل، الكاشير، طريقة الدفع. */
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

        /** سطر في الملخص تحت الجدول: الإجمالي، الخصم، الضريبة. */
        public Builder total(String label, String value) {
            totals.add(new String[]{Objects.requireNonNull(label, "label"), value == null ? "" : value});
            return this;
        }

        /** السطر اللي القارئ بيدوّر عليه، بخط أكبر: الصافي، المطلوب. */
        public Builder grandTotal(String label, String value) {
            this.grandTotal = new String[]{Objects.requireNonNull(label, "label"), value == null ? "" : value};
            return this;
        }

        /** المبلغ بالحروف ({@code AmountInWords}). */
        public Builder amountInWords(String value) {
            this.amountInWords = value == null ? "" : value;
            return this;
        }

        public Builder notes(String value) {
            this.notes = value == null ? "" : value;
            return this;
        }

        /** جملة المحل الثابتة في آخر الفاتورة: سياسة الاسترجاع، شكر. */
        public Builder shopNote(String value) {
            this.shopNote = value == null ? "" : value;
            return this;
        }

        /** كود يتقري بالماسح تحت الفاتورة (باركود Code 128)، مثلًا {@code DocumentCode.DEFAULT.encode(...)}. */
        public Builder code(String value) {
            this.code = value == null ? "" : value;
            return this;
        }

        /** نص رمز QR في آخر الفاتورة: رمز الفاتورة الضريبية مثلًا. */
        public Builder qr(String value) {
            this.qr = value == null ? "" : value;
            return this;
        }

        /** عناوين الأعمدة بمصطلحات البرنامج: الصنف، الكمية، السعر، الإجمالي (والكود والوحدة ورقم السطر اختياري). */
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
