package com.smart.report.fx;

import com.smart.report.ReportException;
import com.smart.report.pdf.Paper;
import com.smart.report.pdf.PdfPrinter;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.util.List;
import java.util.Optional;

/**
 * اللي المعاينة بتلمسه برّه البرنامج: طابعات الجهاز ونافذة اختيار مكان الحفظ. الاختبارات بتبدّله
 * بنسخة مزيفة على {@link PdfPreview}، فما فيش اختبار بيبعت ورقة لطابعة حقيقية ولا بيفتح نافذة حفظ
 * بتستنى حد يقفلها.
 */
interface PreviewSystem {

    /** الجهاز نفسه: {@link PdfPrinter} ونافذة الحفظ بتاعة النظام. */
    PreviewSystem REAL = new PreviewSystem() {
        @Override
        public List<String> printerNames() {
            return PdfPrinter.printerNames();
        }

        @Override
        public Optional<String> defaultPrinter() {
            return PdfPrinter.defaultPrinter();
        }

        @Override
        public void print(byte[] pdf, String printer, Paper paper, int copies) throws ReportException {
            PdfPrinter.print(pdf, printer, paper, copies);
        }

        @Override
        public File chooseSaveFile(FileChooser chooser, Window owner) {
            return chooser.showSaveDialog(owner);
        }
    };

    /** بتتنادى خارج thread الواجهة: مع طابعات الشبكة بتاخد ثواني. */
    List<String> printerNames();

    Optional<String> defaultPrinter();

    /** بتتنادى خارج thread الواجهة. */
    void print(byte[] pdf, String printer, Paper paper, int copies) throws ReportException;

    /** بتتنادى من thread الواجهة؛ {@code null} = المستخدم لغى. */
    File chooseSaveFile(FileChooser chooser, Window owner);

    /**
     * سؤال {@link com.smart.report.print.OutputMode#ASK}: طباعة، أو معاينة، أو حفظ؛ {@code null} = المستخدم لغى.
     * بتتنادى من thread الواجهة وبتستنى الرد.
     */
    default com.smart.report.print.OutputMode ask(Window owner, PrintTexts texts) {
        javafx.scene.control.ButtonType print = new javafx.scene.control.ButtonType(texts.print());
        javafx.scene.control.ButtonType preview = new javafx.scene.control.ButtonType(texts.preview());
        javafx.scene.control.ButtonType save = new javafx.scene.control.ButtonType(texts.save());
        javafx.scene.control.ButtonType cancel = new javafx.scene.control.ButtonType(texts.cancel(),
                javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION, texts.askHeader(), print, preview, save, cancel);
        alert.setTitle(texts.askTitle());
        alert.setHeaderText(null);
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.getDialogPane().setNodeOrientation(texts.rightToLeft()
                ? javafx.geometry.NodeOrientation.RIGHT_TO_LEFT : javafx.geometry.NodeOrientation.LEFT_TO_RIGHT);
        javafx.scene.control.ButtonType chosen = alert.showAndWait().orElse(cancel);
        return chosen == print ? com.smart.report.print.OutputMode.PRINT
                : chosen == preview ? com.smart.report.print.OutputMode.PREVIEW
                : chosen == save ? com.smart.report.print.OutputMode.SAVE_PDF : null;
    }
}
