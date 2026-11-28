package com.smart.report.pdf;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * سند قبض أو صرف جاهز للرسم ({@link #toPdf}): الرقم والتاريخ، والطرف، والمبلغ بخط كبير وبالحروف، والسبب، وخانات
 * التوقيع. كل القيم نص منسّق بلغة البرنامج.
 *
 * <pre>{@code
 * Voucher voucher = Voucher.builder(true)
 *         .letterhead("مؤسسة النور").title("سند قبض").number("88").date("2026-11-10")
 *         .party("استلمنا من السيد", "أحمد علي")
 *         .amount("مبلغ وقدره", "4,000.00 ج.م")
 *         .amountInWords(AmountInWords.arabic(amount, Currency.EGP))
 *         .purpose("وذلك عن", "سداد فاتورة 1751")
 *         .signatures("المستلم", "المحاسب")
 *         .build();
 * }</pre>
 */
public final class Voucher {

    private final boolean rightToLeft;
    private final String letterheadName;
    private final List<String> letterheadLines;
    private final String title;
    private final String number;
    private final String date;
    private final String[] party;
    private final String[] amount;
    private final String amountInWords;
    private final String[] purpose;
    private final List<String[]> fields;
    private final List<String> signatures;

    private Voucher(Builder builder) {
        this.rightToLeft = builder.rightToLeft;
        this.letterheadName = builder.letterheadName;
        this.letterheadLines = List.copyOf(builder.letterheadLines);
        this.title = builder.title;
        this.number = builder.number;
        this.date = builder.date;
        this.party = builder.party;
        this.amount = builder.amount;
        this.amountInWords = builder.amountInWords;
        this.purpose = builder.purpose;
        this.fields = List.copyOf(builder.fields);
        this.signatures = List.copyOf(builder.signatures);
    }

    public static Builder builder(boolean rightToLeft) {
        return new Builder(rightToLeft);
    }

    /** السند على الورقة: A5 هو المعتاد، و A4 ولفة حرارية كمان. */
    public PdfDocument toPdf(Paper paper) {
        Objects.requireNonNull(paper, "paper");
        PdfDocument document = rightToLeft ? PdfDocument.rtl(paper) : PdfDocument.ltr(paper);
        if (!letterheadName.isBlank() || !letterheadLines.isEmpty()) {
            document.letterhead(letterheadName, letterheadLines.toArray(String[]::new));
        }
        String heading = (title + " " + number).strip();
        if (!heading.isEmpty()) {
            document.title(heading);
        }
        if (!date.isBlank()) {
            document.field(rightToLeft ? "التاريخ" : "Date", date);
        }
        for (String[] field : fields) {
            document.field(field[0], field[1]);
        }
        if (party != null) {
            document.field(party[0], party[1]);
        }
        if (amount != null) {
            document.summaryTotal(amount[0], amount[1]);
        }
        if (!amountInWords.isBlank()) {
            document.text(amountInWords);
        }
        if (purpose != null) {
            document.field(purpose[0], purpose[1]);
        }
        if (!signatures.isEmpty()) {
            document.signatures(signatures.toArray(String[]::new));
        }
        return document;
    }

    public static final class Builder {
        private final boolean rightToLeft;
        private String letterheadName = "";
        private final List<String> letterheadLines = new ArrayList<>();
        private String title = "";
        private String number = "";
        private String date = "";
        private String[] party;
        private String[] amount;
        private String amountInWords = "";
        private String[] purpose;
        private final List<String[]> fields = new ArrayList<>();
        private final List<String> signatures = new ArrayList<>();

        private Builder(boolean rightToLeft) {
            this.rightToLeft = rightToLeft;
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

        /** «سند قبض»، «سند صرف». */
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

        /** «استلمنا من السيد» / «صُرف إلى»، والاسم. */
        public Builder party(String label, String name) {
            this.party = pair(label, name);
            return this;
        }

        /** المبلغ بخط كبير. */
        public Builder amount(String label, String value) {
            this.amount = pair(label, value);
            return this;
        }

        public Builder amountInWords(String value) {
            this.amountInWords = value == null ? "" : value;
            return this;
        }

        /** «وذلك عن»، والسبب. */
        public Builder purpose(String label, String value) {
            this.purpose = pair(label, value);
            return this;
        }

        /** بيان تاني: طريقة الدفع، رقم الشيك. */
        public Builder field(String label, String value) {
            fields.add(pair(label, value));
            return this;
        }

        /** خانات التوقيع في آخر السند. */
        public Builder signatures(String... labels) {
            signatures.clear();
            for (String label : labels) {
                signatures.add(Objects.requireNonNull(label, "label"));
            }
            return this;
        }

        private static String[] pair(String label, String value) {
            return new String[]{Objects.requireNonNull(label, "label"), value == null ? "" : value};
        }

        public Voucher build() {
            return new Voucher(this);
        }
    }
}
