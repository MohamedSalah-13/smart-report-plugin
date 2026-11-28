package com.smart.report.print;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * إعداد طباعة نوع مستند: الورقة، وطريقة الإخراج، والطابعة، وعدد النسخ.
 *
 * <pre>{@code
 * PrintProfile receipt = PrintProfile.of("80mm", OutputMode.PRINT, PrinterTarget.role("thermal"), 1);
 * PrintProfile copy = receipt.withCopies(2);
 * }</pre>
 *
 * <p>الورقة باسمها زي ما {@code Paper.parse} في smart-report-pdf بيقراه ({@code "A4"}، {@code "A5-landscape"}،
 * {@code "80mm"})، عشان الوحدة دي ما تعتمدش على PDFBox. الصنف immutable؛ و {@link #encode()} و {@link #decode}
 * بيحوّلوه لنص يتحفظ في إعدادات البرنامج.</p>
 */
public final class PrintProfile {

    /** أكتر عدد نسخ، زي {@code PdfPrinter.MAX_COPIES}. */
    public static final int MAX_COPIES = 99;

    private final String paper;
    private final OutputMode mode;
    private final PrinterTarget printer;
    private final int copies;

    private PrintProfile(String paper, OutputMode mode, PrinterTarget printer, int copies) {
        Objects.requireNonNull(paper, "paper");
        if (paper.isBlank()) {
            throw new IllegalArgumentException("A paper name is required, such as \"A4\" or \"80mm\".");
        }
        if (copies < 1 || copies > MAX_COPIES) {
            throw new IllegalArgumentException("Copies must be between 1 and " + MAX_COPIES + ": " + copies);
        }
        this.paper = paper.strip();
        this.mode = Objects.requireNonNull(mode, "mode");
        this.printer = Objects.requireNonNull(printer, "printer");
        this.copies = copies;
    }

    /**
     * @throws IllegalArgumentException لو الورقة فاضية أو النسخ برّه 1..{@value #MAX_COPIES}
     */
    public static PrintProfile of(String paper, OutputMode mode, PrinterTarget printer, int copies) {
        return new PrintProfile(paper, mode, printer, copies);
    }

    public String paper() {
        return paper;
    }

    public OutputMode mode() {
        return mode;
    }

    public PrinterTarget printer() {
        return printer;
    }

    public int copies() {
        return copies;
    }

    public PrintProfile withPaper(String value) {
        return new PrintProfile(value, mode, printer, copies);
    }

    public PrintProfile withMode(OutputMode value) {
        return new PrintProfile(paper, value, printer, copies);
    }

    public PrintProfile withPrinter(PrinterTarget value) {
        return new PrintProfile(paper, mode, value, copies);
    }

    public PrintProfile withCopies(int value) {
        return new PrintProfile(paper, mode, printer, value);
    }

    /**
     * الإعداد كنص سطر واحد: {@code paper=A5;mode=preview;printer=role:thermal;copies=2}. القيم بترميز URL، فاسم
     * طابعة فيه {@code ;} أو {@code =} بيرجع زي ما هو.
     */
    public String encode() {
        String target = switch (printer) {
            case PrinterTarget.SystemDefault ignored -> "default";
            case PrinterTarget.Role role -> "role:" + role.name();
            case PrinterTarget.Named named -> "name:" + escape(named.printer());
        };
        return "paper=" + escape(paper) + ";mode=" + mode.name().toLowerCase(Locale.ROOT)
                + ";printer=" + target + ";copies=" + copies;
    }

    /**
     * يقرا {@link #encode()} بتسامح: القيمة اللي مكتوبة بنسخة تانية من البرنامج ما توقفش الطباعة أبدًا. أي جزء
     * ما يتقريش، أو ورقة مش من ورق النوع، بياخد قيمته من {@link DocumentKind#defaults()}؛ والأجزاء المجهولة
     * بتتجاهل. {@code null} أو نص فاضي = الإعداد الافتراضي كله. ({@link PrintSettings#profile} كمان بيرجّع الدور
     * اللي البرنامج ما بقاش بيعرّفه للافتراضي.)
     */
    public static PrintProfile decode(String text, DocumentKind kind) {
        Objects.requireNonNull(kind, "kind");
        PrintProfile fallback = kind.defaults();
        if (text == null || text.isBlank()) {
            return fallback;
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : text.split(";")) {
            int equals = pair.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            try {
                values.putIfAbsent(pair.substring(0, equals).strip().toLowerCase(Locale.ROOT),
                        URLDecoder.decode(pair.substring(equals + 1).strip(), StandardCharsets.UTF_8));
            } catch (IllegalArgumentException unreadable) {
                // ترميز بايظ: الجزء ده بياخد القيمة الافتراضية.
            }
        }
        String paper = values.get("paper");
        if (paper == null || !kind.papers().contains(paper.strip())) {
            paper = fallback.paper();
        }
        OutputMode mode = fallback.mode();
        try {
            if (values.containsKey("mode")) {
                mode = OutputMode.valueOf(values.get("mode").strip().toUpperCase(Locale.ROOT));
            }
        } catch (IllegalArgumentException unknown) {
            // طريقة من نسخة أحدث: الافتراضية.
        }
        PrinterTarget printer = target(values.get("printer"), kind, fallback.printer());
        int copies = fallback.copies();
        try {
            if (values.containsKey("copies")) {
                int read = Integer.parseInt(values.get("copies").strip());
                copies = read >= 1 && read <= MAX_COPIES ? read : copies;
            }
        } catch (NumberFormatException unreadable) {
            // الافتراضي.
        }
        return new PrintProfile(paper, mode, printer, copies);
    }

    private static PrinterTarget target(String value, DocumentKind kind, PrinterTarget fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            if (value.equals("default")) {
                return PrinterTarget.systemDefault();
            }
            if (value.startsWith("role:")) {
                return new PrinterTarget.Role(value.substring(5));
            }
            if (value.startsWith("name:")) {
                return new PrinterTarget.Named(value.substring(5));
            }
        } catch (IllegalArgumentException unreadable) {
            // اسم فاضي أو دور بحروف مش مقبولة.
        }
        return fallback;
    }

    private static String escape(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PrintProfile that && copies == that.copies && paper.equals(that.paper)
                && mode == that.mode && printer.equals(that.printer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(paper, mode, printer, copies);
    }

    @Override
    public String toString() {
        return "PrintProfile[" + encode() + "]";
    }
}
