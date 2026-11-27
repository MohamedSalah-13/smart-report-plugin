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
}
