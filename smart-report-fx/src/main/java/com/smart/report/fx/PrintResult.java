package com.smart.report.fx;

import com.smart.report.pdf.Paper;
import javafx.stage.Stage;

import java.nio.file.Path;

/**
 * The result of {@link PdfPreview#print(com.smart.report.print.PrintSettings, String, javafx.stage.Window)}: the
 * document was output as its type's profile says, or the preview window opened, or it was saved, or the user
 * cancelled, or it failed.
 *
 * <pre>{@code
 * preview.print(settings, "sale-receipt", owner).thenAccept(result -> {
 *     switch (result) {
 *         case PrintResult.Printed printed -> status.setText("أُرسل إلى " + printed.printer());
 *         case PrintResult.Saved saved -> status.setText("حُفظ في " + saved.file());
 *         case PrintResult.Failed failed -> showError("تعذرت الطباعة", failed.reason());
 *         case PrintResult.Shown shown -> { }
 *         case PrintResult.Cancelled cancelled -> { }
 *     }
 * });
 * }</pre>
 */
public sealed interface PrintResult {

    /** Sent to the printer: {@code copies} copies on {@code paper}. */
    record Printed(String printer, Paper paper, int copies) implements PrintResult {
    }

    /**
     * The preview window opened: the profile says preview, or the profile's printer is not on the computer (then a
     * line above the page says so, and the document was not sent to another printer).
     */
    record Shown(Stage window) implements PrintResult {
    }

    /** A PDF file was saved. */
    record Saved(Path file) implements PrintResult {
    }

    /** The user cancelled: closed the «اسأل كل مرة» ("Ask every time") question or the save dialog. */
    record Cancelled() implements PrintResult {
    }

    /**
     * The document was not built, or the printer rejected the job, or the file was not written. No window opens
     * by itself.
     *
     * @param reason its message is in English, for the log, not for the user
     */
    record Failed(Exception reason) implements PrintResult {
    }
}
