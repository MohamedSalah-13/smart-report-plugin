package com.smart.report.fx.manual;

import com.smart.report.util.DocumentCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The hardware check in a dry run: every print goes to a file, and the answers are typed by the test. */
class HardwareCheckTest {

    private static String arabicDigits(String digits) {
        StringBuilder out = new StringBuilder();
        for (char c : digits.toCharArray()) {
            out.append((char) ('٠' + (c - '0')));
        }
        return out.toString();
    }

    @Test
    void everyCheckRunsAndTheReportIsWritten(@TempDir Path folder) throws Exception {
        String documentCode = DocumentCode.DEFAULT.encode(1, 1751);
        List<String> answers = List.of(
                // setup: office printer, receipt printer, roll, dpi
                "Office", "Receipt", "80", "203",
                "all",
                // A. invoice, B. two copies
                "y", "y",
                // C. the scanner types what it reads; the document code arrives in Arabic-Indic digits
                "SHP-2026-0042", "4006381333931", "WRONG", arabicDigits(documentCode),
                // D. drawer on pin 2 and pin 5
                "y", "n", "the drawer is on pin 2",
                // E. roll labels: the receipt printer, 40x25, two across, off by 1 mm right, then fine
                "", "40x25", "2", "n", "1 0.5", "y",
                // F. sheet labels
                "3x8", "y",
                // G. pages 2-3 on both sides
                "y",
                // H. preview window: skipped in a dry run; I. papers: no question
                // J. watermark copied
                "n", "Edge kept the word",
                // K. grouped Excel
                "y");
        ByteArrayOutputStream console = new ByteArrayOutputStream();
        HardwareCheck check = new HardwareCheck(
                new ByteArrayInputStream(String.join("\n", answers).concat("\n").getBytes(StandardCharsets.UTF_8)),
                new PrintStream(console, true, StandardCharsets.UTF_8), folder, true);
        Path report = check.run();

        String text = Files.readString(report, StandardCharsets.UTF_8);
        assertTrue(text.contains("(dry run)"));
        assertTrue(text.contains("| A. Office printer: an Arabic A4 invoice | pass |"), text);
        assertTrue(text.contains("Code 128 | pass"), text);
        assertTrue(text.contains("EAN-13 | pass"), text);
        assertTrue(text.contains("QR | fail | read \"WRONG\""), text);
        assertTrue(text.contains("document code | pass"), "Arabic-Indic digits are accepted: " + text);
        assertTrue(text.contains("PIN_5 | fail | the drawer is on pin 2"), text);
        assertTrue(text.contains("40.0x25.0 mm, 2 across, offset x -1.0 y -0.5 mm"), text);
        assertTrue(text.contains("H. Preview window: pages and both sides | skipped"), text);
        assertTrue(text.contains("J. Watermark left out when the text is copied | fail | Edge kept the word"), text);
        assertTrue(text.contains("K. Grouped Excel report opened in Excel | pass"), text);

        for (String file : List.of("a4-invoice.pdf", "receipt-two-copies.pdf", "scanner-codes.pdf",
                "roll-labels-1.pdf", "roll-labels-2.pdf", "sheet-labels.pdf", "pages-2-3-duplex.pdf",
                "watermark.pdf", "grouped.xlsx")) {
            assertTrue(Files.size(folder.resolve("hardware-check-" + file)) > 0, file);
        }
        assertEquals(0, check.results().stream().filter(r -> r.outcome().equals("error")).count(),
                () -> check.results().toString());
    }
}
