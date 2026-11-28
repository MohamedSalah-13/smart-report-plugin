package com.smart.report.fx;

import com.smart.report.pdf.Paper;
import javafx.stage.Stage;

/**
 * The result of {@link PdfPreview#printOrShow(javafx.stage.Window)}: the document was sent to the printer, or it
 * failed, or the preview window opened instead.
 *
 * <pre>{@code
 * preview.printOrShow(owner).thenAccept(outcome -> {
 *     switch (outcome) {
 *         case PrintOutcome.Sent sent -> status.setText("أُرسل إلى " + sent.printer());
 *         case PrintOutcome.Failed failed -> showError("تعذرت طباعة الإيصال", failed.reason());
 *         case PrintOutcome.Shown shown -> { }
 *     }
 * });
 * }</pre>
 */
public sealed interface PrintOutcome {

    /** One copy was sent to the saved printer on {@code paper}. */
    record Sent(String printer, Paper paper) implements PrintOutcome {
    }

    /**
     * The saved printer is on the computer but the document was not sent: the function that builds it threw, or
     * the printer rejected the job. <b>The window does not open</b>; the application tells the user, and can open
     * it with {@link PdfPreview#show(javafx.stage.Window)} so the user can pick another printer.
     *
     * @param reason its message is in English, for the log, not for the user
     */
    record Failed(String printer, Paper paper, Exception reason) implements PrintOutcome {
    }

    /** No printer is saved, or the saved one is no longer installed on the computer: the preview window opened. */
    record Shown(Stage window) implements PrintOutcome {
    }
}
