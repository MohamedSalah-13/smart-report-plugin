package com.smart.report.fx;

import com.smart.report.pdf.Paper;
import javafx.stage.Stage;

import java.nio.file.Path;

/**
 * نتيجة {@link PdfPreview#print(com.smart.report.print.PrintSettings, String, javafx.stage.Window)}: المستند طلع
 * زي ما إعداد نوعه بيقول، أو نافذة المعاينة اتفتحت، أو اتحفظ، أو المستخدم لغى، أو فشل.
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

    /** اتبعت للطابعة: {@code copies} نسخة على {@code paper}. */
    record Printed(String printer, Paper paper, int copies) implements PrintResult {
    }

    /**
     * نافذة المعاينة اتفتحت: الإعداد بيقول معاينة، أو طابعة الإعداد مش موجودة على الجهاز (وساعتها فوق الصفحة سطر
     * بيقول كده، والمستند ما اتبعتش لطابعة تانية).
     */
    record Shown(Stage window) implements PrintResult {
    }

    /** اتحفظ ملف PDF. */
    record Saved(Path file) implements PrintResult {
    }

    /** المستخدم لغى: قفل سؤال «اسأل كل مرة» أو نافذة الحفظ. */
    record Cancelled() implements PrintResult {
    }

    /**
     * المستند ما اتبناش، أو الطابعة رفضت المهمة، أو الملف ما اتكتبش. ما فيش نافذة بتتفتح لوحدها.
     *
     * @param reason رسالته إنجليزي للسجل، مش للمستخدم
     */
    record Failed(Exception reason) implements PrintResult {
    }
}
