package com.smart.report.fx;

import com.smart.report.pdf.Paper;
import javafx.stage.Stage;

/**
 * نتيجة {@link PdfPreview#printOrShow(javafx.stage.Window)}: المستند اتبعت للطابعة، أو فشل، أو نافذة
 * المعاينة اتفتحت بداله.
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

    /** اتبعتت نسخة واحدة للطابعة المحفوظة على {@code paper}. */
    record Sent(String printer, Paper paper) implements PrintOutcome {
    }

    /**
     * الطابعة المحفوظة موجودة على الجهاز لكن المستند ما اتبعتش: الدالة اللي بتبنيه رمت، أو الطابعة
     * رفضت المهمة. <b>النافذة ما بتتفتحش</b>؛ البرنامج يقول للمستخدم، ويقدر يفتحها بـ
     * {@link PdfPreview#show(javafx.stage.Window)} عشان يختار طابعة تانية.
     *
     * @param reason رسالته إنجليزي للسجل، مش للمستخدم
     */
    record Failed(String printer, Paper paper, Exception reason) implements PrintOutcome {
    }

    /** مفيش طابعة محفوظة، أو المحفوظة مش متعرّفة على الجهاز تاني: اتفتحت نافذة المعاينة. */
    record Shown(Stage window) implements PrintOutcome {
    }
}
