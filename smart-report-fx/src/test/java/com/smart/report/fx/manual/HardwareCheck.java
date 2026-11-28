package com.smart.report.fx.manual;

import com.smart.report.ColumnDefinition;
import com.smart.report.ReportException;
import com.smart.report.ReportRequest;
import com.smart.report.ReportService;
import com.smart.report.ReportType;
import com.smart.report.fx.PdfPreview;
import com.smart.report.pdf.Invoice;
import com.smart.report.pdf.InvoiceLayout;
import com.smart.report.pdf.InvoiceLine;
import com.smart.report.pdf.Paper;
import com.smart.report.pdf.PdfDocument;
import com.smart.report.pdf.PdfLabels;
import com.smart.report.pdf.PdfPrinter;
import com.smart.report.pdf.PrintOptions;
import com.smart.report.util.DocumentCode;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;

/**
 * The checks the automated tests cannot make (issue #42), run by hand on real printers, a scanner and a cash drawer.
 * Each check prints something, then asks what came out. A scan is checked by itself: the scanner types what it
 * reads into this console. At the end a Markdown report is written to post on the issue.
 *
 * <p>Run it from the repository root (see docs/HARDWARE-CHECK.md):</p>
 * <pre>
 * ./mvnw -q install -DskipTests
 * ./mvnw -q -pl smart-report-fx org.codehaus.mojo:exec-maven-plugin:3.7.0:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=com.smart.report.fx.manual.HardwareCheck
 * </pre>
 *
 * <p>{@code --dry-run} sends nothing to a printer or drawer: every print is written as a PDF file next to the report
 * instead. The automated test uses it.</p>
 */
public final class HardwareCheck {

    /** Where prints and drawer pulses go: the real printers, or files for a dry run. */
    interface Output {
        void print(byte[] pdf, String printer, Paper paper, PrintOptions options, String name) throws ReportException;

        void drawer(String printer, PdfPrinter.DrawerPin pin) throws ReportException;
    }

    record Result(String check, String outcome, String note) {
    }

    /** A row of the grouped Excel check; public, because the report reads its properties by reflection. */
    public record Line(String customer, String item, BigDecimal amount) {
    }

    private final Scanner in;
    private final PrintStream out;
    private final Output output;
    private final Path folder;
    private final boolean dryRun;
    private final List<Result> results = new ArrayList<>();
    private String office;
    private String thermal;
    private Paper roll = Paper.ROLL_80;
    private int dpi = 203;

    HardwareCheck(InputStream input, PrintStream out, Path folder, boolean dryRun) {
        this.in = new Scanner(input, StandardCharsets.UTF_8);
        this.out = out;
        this.folder = folder;
        this.dryRun = dryRun;
        this.output = dryRun ? new Output() {
            @Override
            public void print(byte[] pdf, String printer, Paper paper, PrintOptions options, String name)
                    throws ReportException {
                write(folder.resolve("hardware-check-" + name + ".pdf"), pdf);
                out.println("  (dry run) " + name + " written for " + printer + ", " + options);
            }

            @Override
            public void drawer(String printer, PdfPrinter.DrawerPin pin) {
                out.println("  (dry run) the drawer pulse on " + pin + " is not sent");
            }
        } : new Output() {
            @Override
            public void print(byte[] pdf, String printer, Paper paper, PrintOptions options, String name)
                    throws ReportException {
                PdfPrinter.print(pdf, printer, paper, options);
            }

            @Override
            public void drawer(String printer, PdfPrinter.DrawerPin pin) throws ReportException {
                PdfPrinter.openCashDrawer(printer, pin, 100);
            }
        };
    }

    public static void main(String[] args) throws Exception {
        boolean dryRun = List.of(args).contains("--dry-run");
        Path report = new HardwareCheck(System.in, System.out, Path.of("").toAbsolutePath(), dryRun).run();
        System.out.println();
        System.out.println("Report written to " + report);
    }

    /** Runs the setup and the menu; returns the report file. */
    Path run() throws IOException {
        out.println("smart-report hardware check" + (dryRun ? " (dry run: nothing is sent to a printer)" : ""));
        out.println("Answer y (yes), n (no) or s (skip). An empty answer means skip.");
        setup();
        String[] names = {"Office printer: an Arabic A4 invoice", "Receipt printer: two copies of a receipt",
                "Scanner: Code 128, EAN-13, QR and the document code", "Cash drawer: pin 2 and pin 5",
                "Labels on a roll, and the offset", "Labels on an A4 sheet",
                "Office printer: pages 2-3 on both sides (code)", "Preview window: pages and both sides",
                "Which papers each printer lists", "Watermark left out when the text is copied",
                "Grouped Excel report opened in Excel"};
        while (true) {
            out.println();
            for (int i = 0; i < names.length; i++) {
                String prefix = (char) ('A' + i) + ".";
                boolean done = results.stream().anyMatch(r -> r.check().startsWith(prefix));
                out.printf("  %s. %s%s%n", (char) ('A' + i), names[i], done ? "  [done]" : "");
            }
            out.println("  all. Run every check in order    q. Write the report and quit");
            String choice = ask("Check").toUpperCase(Locale.ROOT);
            if (choice.equals("Q") || choice.isEmpty() && !in.hasNextLine()) {
                break;
            }
            if (choice.equals("ALL")) {
                for (int i = 0; i < names.length; i++) {
                    runCheck((char) ('A' + i), names[i]);
                }
                break;
            }
            if (choice.length() == 1 && choice.charAt(0) >= 'A' && choice.charAt(0) < 'A' + names.length) {
                runCheck(choice.charAt(0), names[choice.charAt(0) - 'A']);
            }
        }
        return writeReport();
    }

    private void setup() {
        List<String> printers = PdfPrinter.printerNames();
        out.println();
        if (printers.isEmpty()) {
            out.println("No printer is set up on this computer.");
        }
        for (int i = 0; i < printers.size(); i++) {
            out.printf("  %d. %s%n", i + 1, printers.get(i));
        }
        office = pickPrinter("The office (A4) printer, by number or name", printers);
        thermal = pickPrinter("The receipt (thermal) printer, by number or name", printers);
        if (thermal != null) {
            String width = ask("Its roll: 80 or 58 mm [80]");
            roll = width.startsWith("58") ? Paper.ROLL_58 : Paper.ROLL_80;
            String resolution = ask("Its resolution in dpi [203]");
            try {
                dpi = resolution.isBlank() ? 203 : Integer.parseInt(resolution.strip());
            } catch (NumberFormatException e) {
                dpi = 203;
            }
        }
    }

    private String pickPrinter(String question, List<String> printers) {
        String answer = ask(question + " (empty = none)");
        if (answer.isBlank()) {
            return null;
        }
        try {
            int index = Integer.parseInt(answer.strip()) - 1;
            if (index >= 0 && index < printers.size()) {
                return printers.get(index);
            }
        } catch (NumberFormatException byName) {
            // the name itself
        }
        return answer.strip();
    }

    private void runCheck(char letter, String name) {
        out.println();
        out.println("== " + letter + ". " + name);
        String check = letter + ". " + name;
        try {
            switch (letter) {
                case 'A' -> officeInvoice(check);
                case 'B' -> receiptCopies(check);
                case 'C' -> scanner(check);
                case 'D' -> drawer(check);
                case 'E' -> rollLabels(check);
                case 'F' -> sheetLabels(check);
                case 'G' -> pageRange(check);
                case 'H' -> previewWindow(check);
                case 'I' -> declaredPapers(check);
                case 'J' -> watermark(check);
                case 'K' -> groupedExcel(check);
                default -> { }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            results.add(new Result(check, "error", "interrupted"));
        } catch (ReportException | IOException | RuntimeException e) {
            out.println("  Failed: " + e.getMessage());
            results.add(new Result(check, "error", e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    // ===================== the checks =====================

    private static Invoice invoice(boolean arabic) {
        Invoice.Builder builder = Invoice.builder(arabic)
                .letterhead(arabic ? "فحص الطابعات" : "Printer check", "smart-report")
                .title(arabic ? "فاتورة تجربة" : "Test invoice").number("42")
                .date(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .field(arabic ? "العميل" : "Customer", arabic ? "أحمد علي" : "Ahmed Ali")
                .line(InvoiceLine.of(arabic ? "مسمار حديد مجلفن برأس مسطح مقاس عشرة مليمتر علبة مائة قطعة"
                        : "Galvanised flat head steel nail, ten millimetres, box of one hundred", "12", "2.50", "30.00"))
                .line(InvoiceLine.of(arabic ? "شاكوش" : "Hammer", "1", "85.00", "85.00"));
        return builder.total(arabic ? "الإجمالى" : "Subtotal", "115.00")
                .grandTotal(arabic ? "الصافى" : "Net", "115.00")
                .shopNote(arabic ? "ورقة فحص من smart-report" : "A check sheet from smart-report")
                .build();
    }

    private void officeInvoice(String check) throws ReportException {
        if (office == null) {
            skip(check, "no office printer chosen");
            return;
        }
        byte[] pdf = invoice(true).toPdf(Paper.A4, InvoiceLayout.DETAILED).toBytes();
        output.print(pdf, office, Paper.A4, PrintOptions.DEFAULT, "a4-invoice");
        verdict(check, "Is the Arabic joined and right to left, the table whole, and nothing cut at the edges?");
    }

    private void receiptCopies(String check) throws ReportException {
        if (thermal == null) {
            skip(check, "no receipt printer chosen");
            return;
        }
        byte[] pdf = invoice(true).toPdf(roll, InvoiceLayout.MODERN).toBytes();
        output.print(pdf, thermal, roll, PrintOptions.copies(2), "receipt-two-copies");
        verdict(check, "Did two receipts come out, cut apart (not one long strip)?");
    }

    private void scanner(String check) throws ReportException {
        if (thermal == null && office == null) {
            skip(check, "no printer chosen");
            return;
        }
        String printer = thermal != null ? thermal : office;
        Paper paper = thermal != null ? roll : Paper.A5;
        String code128 = "SHP-2026-0042";
        String ean = "4006381333931";
        String qr = "INV-42-" + DocumentCode.DEFAULT.length();
        String document = DocumentCode.DEFAULT.encode(1, 1751);
        PdfDocument sheet = PdfDocument.ltr(paper).printerDpi(thermal != null ? dpi : 0)
                .title("Scanner check").text("1. Code 128").barcode(code128)
                .text("2. EAN-13").ean13(ean)
                .text("3. QR").qrCode(qr)
                .text("4. Document code (type 1, number 1751)").barcode(document);
        output.print(sheet.toBytes(), printer, paper, PrintOptions.DEFAULT, "scanner-codes");
        out.println("  Click in this window, then scan each code; the scanner types it here and presses Enter.");
        out.println("  (An empty line means the code did not scan.)");
        scan(check + " - Code 128", "Scan code 1", code128);
        scan(check + " - EAN-13", "Scan code 2", ean);
        scan(check + " - QR", "Scan code 3", qr);
        String read = ask("Scan code 4");
        boolean ok = DocumentCode.DEFAULT.parse(read).map(found -> found.type() == 1 && found.number() == 1751)
                .orElse(false);
        results.add(new Result(check + " - document code", ok ? "pass" : "fail",
                "read \"" + read + "\"" + (ok ? " → type 1, number 1751" : "")));
        out.println(ok ? "  OK" : "  Not the code: read \"" + read + "\"");
    }

    private void scan(String check, String question, String expected) {
        String read = ask(question);
        boolean ok = read.strip().equals(expected);
        results.add(new Result(check, ok ? "pass" : "fail", "read \"" + read + "\", expected \"" + expected + "\""));
        out.println(ok ? "  OK" : "  Not the same: expected " + expected);
    }

    private void drawer(String check) throws ReportException {
        if (thermal == null) {
            skip(check, "no receipt printer chosen");
            return;
        }
        for (PdfPrinter.DrawerPin pin : PdfPrinter.DrawerPin.values()) {
            try {
                output.drawer(thermal, pin);
                verdict(check + " - " + pin, "Did the drawer open on " + pin + "?");
            } catch (ReportException e) {
                out.println("  " + e.getMessage());
                results.add(new Result(check + " - " + pin, "fail", e.getMessage()));
                return;
            }
        }
    }

    private PdfLabels labels(PdfLabels labels) {
        labels.add(PdfLabels.Item.of("مسمار 10 مم", "15.00 ج.م", "4006381333931").withOldPrice("17.50"), 2)
                .add(PdfLabels.Item.of("شاكوش حديد بيد خشب طويلة جدًا", "85.00 ج.م", "HM-001"), 2);
        return labels;
    }

    private void rollLabels(String check) throws ReportException {
        String printer = pickPrinter("The label printer (number or name; empty = the receipt printer)",
                PdfPrinter.printerNames());
        printer = printer != null ? printer : thermal;
        if (printer == null) {
            skip(check, "no label printer");
            return;
        }
        String size = ask("Label size in mm, width x height [40x25]");
        float width = 40;
        float height = 25;
        if (size.matches("\\s*\\d+(\\.\\d+)?\\s*[xX×]\\s*\\d+(\\.\\d+)?\\s*")) {
            String[] parts = size.split("[xX×]");
            width = Float.parseFloat(parts[0].strip());
            height = Float.parseFloat(parts[1].strip());
        }
        int across = ask("Labels across the roll, 1 or 2 [1]").strip().equals("2") ? 2 : 1;
        float x = 0;
        float y = 0;
        for (int attempt = 1; attempt <= 3; attempt++) {
            PdfLabels labels = labels(PdfLabels.roll(width, height).across(across, 2).printerDpi(dpi).offset(x, y));
            output.print(labels.toBytes(), printer, labels.paper(), PrintOptions.DEFAULT, "roll-labels-" + attempt);
            String answer = ask("Is each label inside its paper label, and do the barcodes scan? (y/n/s)");
            if (!answer.toLowerCase(Locale.ROOT).startsWith("n")) {
                results.add(new Result(check, outcome(answer), String.format(Locale.ROOT,
                        "%sx%s mm, %d across, offset x %.1f y %.1f mm", width, height, across, x, y)));
                return;
            }
            String shift = ask("How far off is the print, in mm: right down (e.g. 1.5 -0.5; empty = give up)");
            if (shift.isBlank()) {
                break;
            }
            try {
                String[] parts = shift.strip().split("\\s+");
                x -= Float.parseFloat(parts[0]);
                y -= parts.length > 1 ? Float.parseFloat(parts[1]) : 0;
            } catch (NumberFormatException e) {
                break;
            }
        }
        results.add(new Result(check, "fail", String.format(Locale.ROOT, "last offset x %.1f y %.1f mm", x, y)));
    }

    private void sheetLabels(String check) throws ReportException {
        if (office == null) {
            skip(check, "no office printer chosen");
            return;
        }
        String layout = ask("Columns x rows on the A4 sheet [3x8]");
        int columns = 3;
        int rows = 8;
        if (layout.matches("\\s*\\d+\\s*[xX×]\\s*\\d+\\s*")) {
            String[] parts = layout.split("[xX×]");
            columns = Integer.parseInt(parts[0].strip());
            rows = Integer.parseInt(parts[1].strip());
        }
        PdfLabels sheet = PdfLabels.sheet(Paper.A4, columns, rows);
        for (int i = 0; i < columns * rows / 4; i++) {
            labels(sheet);
        }
        output.print(sheet.toBytes(), office, Paper.A4, PrintOptions.DEFAULT, "sheet-labels");
        verdict(check, "Does every label sit inside its cut label on the sheet?");
    }

    private static PdfDocument fourPages() {
        PdfDocument document = PdfDocument.ltr(Paper.A4).title("Pages check");
        for (int page = 1; page <= 4; page++) {
            if (page > 1) {
                document.pageBreak();
            }
            document.title("PAGE " + page).text("This sheet should show page " + page + ".");
        }
        return document;
    }

    private void pageRange(String check) throws ReportException {
        if (office == null) {
            skip(check, "no office printer chosen");
            return;
        }
        output.print(fourPages().toBytes(), office, Paper.A4,
                PrintOptions.copies(1).pages(2, 3).duplex(PrintOptions.Duplex.LONG_EDGE), "pages-2-3-duplex");
        verdict(check, "Did one sheet come out, with PAGE 2 on the front and PAGE 3 on the back?");
    }

    private void previewWindow(String check) throws InterruptedException {
        if (dryRun) {
            skip(check, "needs the window: not in a dry run");
            return;
        }
        out.println("  The preview window opens. Type 2-3 in Pages, tick Both sides, choose the office printer,");
        out.println("  press Print, then close the window.");
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // started by an earlier run
        }
        Platform.setImplicitExit(false);
        CountDownLatch closed = new CountDownLatch(1);
        Platform.runLater(() -> {
            Stage stage = PdfPreview.of(fourPages()).rightToLeft(false).title("Pages check").show(null);
            stage.setOnHidden(event -> closed.countDown());
        });
        closed.await();
        verdict(check, "Did one sheet come out, with PAGE 2 on the front and PAGE 3 on the back?");
    }

    private void declaredPapers(String check) {
        for (String printer : PdfPrinter.printerNames()) {
            StringBuilder line = new StringBuilder();
            for (Paper paper : new Paper[]{Paper.A4, Paper.A5}) {
                try {
                    line.append(paper.name()).append(PdfPrinter.declaresPaper(printer, paper) ? " yes  " : " NO  ");
                } catch (ReportException e) {
                    line.append(paper.name()).append(" ?  ");
                }
            }
            out.println("  " + printer + ": " + line.toString().strip());
            results.add(new Result(check + " - " + printer, "info", line.toString().strip()));
        }
    }

    private void watermark(String check) throws ReportException, IOException {
        Path file = folder.resolve("hardware-check-watermark.pdf");
        write(file, PdfDocument.rtl(Paper.A4).title("نسخة للعميل").text("هذه الصفحة عليها علامة مائية.")
                .watermark("صورة").toBytes());
        out.println("  Open " + file + " in Acrobat Reader and in a browser.");
        out.println("  Select all the text (Ctrl+A), copy it (Ctrl+C) and paste it into Notepad.");
        verdict(check, "Was the pasted text WITHOUT the watermark word «صورة» in both readers?");
    }

    private void groupedExcel(String check) throws ReportException, IOException {
        List<Line> lines = List.of(new Line("Ahmed", "Bolt", new BigDecimal("10")),
                new Line("Ahmed", "Nut", new BigDecimal("5")), new Line("Mona", "Screw", new BigDecimal("7")),
                new Line("Omar", "Washer", new BigDecimal("3")));
        byte[] xlsx = ReportService.generate(ReportType.EXCEL, ReportRequest.<Line>builder().title("Grouped check")
                .data(lines).addColumn(ColumnDefinition.of("customer", "Customer"))
                .addColumn(ColumnDefinition.of("item", "Item"))
                .addColumn(ColumnDefinition.of("amount", "Amount").withTotal())
                .groupBy("customer").groupHeader(true).build());
        Path file = folder.resolve("hardware-check-grouped.xlsx");
        write(file, xlsx);
        out.println("  Open " + file + " in Excel. Filter Customer to Mona only, then Item to Bolt only.");
        verdict(check, "Did the subtotals and the grand total follow each filter (Mona: 7, then 0)?");
    }

    // ===================== answers and the report =====================

    private String ask(String question) {
        out.print(question + ": ");
        out.flush();
        return in.hasNextLine() ? in.nextLine() : "";
    }

    private void verdict(String check, String question) {
        String answer = ask(question + " (y/n/s)");
        String note = "";
        if (answer.toLowerCase(Locale.ROOT).startsWith("n")) {
            note = ask("What went wrong? (one line)");
        }
        results.add(new Result(check, outcome(answer), note));
    }

    private static String outcome(String answer) {
        String a = answer.strip().toLowerCase(Locale.ROOT);
        return a.startsWith("y") ? "pass" : a.startsWith("n") ? "fail" : "skipped";
    }

    private void skip(String check, String why) {
        out.println("  Skipped: " + why);
        results.add(new Result(check, "skipped", why));
    }

    private static void write(Path file, byte[] bytes) throws ReportException {
        try {
            Files.write(file, bytes);
        } catch (IOException e) {
            throw new ReportException("Could not write " + file + ": " + e.getMessage(), e);
        }
    }

    private Path writeReport() throws IOException {
        StringBuilder md = new StringBuilder();
        md.append("## Hardware check, ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern(
                "yyyy-MM-dd HH:mm"))).append(dryRun ? " (dry run)" : "").append("\n\n");
        md.append("- Java ").append(System.getProperty("java.version")).append(", ")
                .append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version"))
                .append("\n");
        md.append("- Office printer: ").append(office == null ? "none" : "`" + office + "`").append("\n");
        md.append("- Receipt printer: ").append(thermal == null ? "none" : "`" + thermal + "`, " + roll.name()
                + ", " + dpi + " dpi").append("\n\n");
        md.append("| Check | Result | Note |\n|---|---|---|\n");
        for (Result result : results) {
            md.append("| ").append(result.check()).append(" | ").append(result.outcome()).append(" | ")
                    .append(result.note().replace("|", "\\|")).append(" |\n");
        }
        Path file = folder.resolve("hardware-check-report.md");
        Files.writeString(file, md.toString(), StandardCharsets.UTF_8);
        return file;
    }

    /** For the automated test: the results so far. */
    List<Result> results() {
        return results;
    }
}
