package com.smart.report.fx;

/**
 * The preview window texts. {@link #arabic()} and {@link #english()} are ready to use; an application with its
 * own terms builds its own copy.
 *
 * @param title       the window title when the application does not set one
 * @param printer     the label of the printer choice
 * @param paper       the label of the paper size choice
 * @param copies      the label of the number of copies
 * @param print       the print button
 * @param savePdf     the save-file button
 * @param saveTitle   the title of the save dialog
 * @param pageOf      "صفحة %d من %d" ("Page %d of %d")
 * @param previous    the tooltip of the previous-page button
 * @param next        the tooltip of the next-page button
 * @param zoomIn      the tooltip of the zoom-in button
 * @param zoomOut     the tooltip of the zoom-out button
 * @param fitWidth    the fit-width button
 * @param fitPage     the whole-page button
 * @param landscape   the description of a paper turned to landscape in the size list
 * @param preparing   shown while the page is being prepared
 * @param noPrinter   no printer is set up on the computer
 * @param sent        "أُرسل إلى %s" ("Sent to %s")
 * @param saved       "حُفظ فى %s" ("Saved to %s")
 * @param printFailed printing failed
 * @param saveFailed  saving failed
 * @param buildFailed the document itself was not built
 */
public record PreviewTexts(String title, String printer, String paper, String copies,
                           String print, String savePdf, String saveTitle,
                           String pageOf, String previous, String next,
                           String zoomIn, String zoomOut, String fitWidth, String fitPage, String landscape,
                           String preparing, String noPrinter,
                           String sent, String saved,
                           String printFailed, String saveFailed, String buildFailed) {

    public static PreviewTexts arabic() {
        return new PreviewTexts("معاينة الطباعة", "الطابعة", "مقاس الورق", "عدد النسخ",
                "طباعة", "حفظ PDF", "حفظ الملف",
                "صفحة %d من %d", "الصفحة السابقة", "الصفحة التالية",
                "تكبير", "تصغير", "ملء العرض", "الصفحة كاملة", "بالعرض",
                "جارٍ تجهيز الصفحة…", "لا توجد طابعة معرّفة على هذا الجهاز. يمكن حفظ الملف PDF.",
                "أُرسل إلى الطابعة %s", "حُفظ فى %s",
                "تعذرت الطباعة. تأكد من الطابعة وحاول مرة ثانية.",
                "تعذر حفظ الملف. تأكد أنه غير مفتوح فى برنامج آخر وحاول مرة ثانية.",
                "تعذر تجهيز المستند للمعاينة.");
    }

    public static PreviewTexts english() {
        return new PreviewTexts("Print preview", "Printer", "Paper", "Copies",
                "Print", "Save PDF", "Save the file",
                "Page %d of %d", "Previous page", "Next page",
                "Zoom in", "Zoom out", "Fit width", "Whole page", "landscape",
                "Preparing the page…", "No printer is set up on this computer. The file can be saved as a PDF.",
                "Sent to the printer %s", "Saved to %s",
                "Printing failed. Check the printer and try again.",
                "The file could not be saved. Make sure it is not open in another program and try again.",
                "The document could not be prepared for the preview.");
    }
}
