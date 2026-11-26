package com.smart.report.fx;

/**
 * كلام نافذة المعاينة. {@link #arabic()} و{@link #english()} جاهزين، والبرنامج اللي ليه مصطلحاته
 * يبني نسخته.
 *
 * @param title       عنوان النافذة لو البرنامج ما حدّدش واحدًا
 * @param printer     عنوان اختيار الطابعة
 * @param paper       عنوان اختيار مقاس الورق
 * @param copies      عنوان عدد النسخ
 * @param print       زر الطباعة
 * @param savePdf     زر حفظ الملف
 * @param saveTitle   عنوان نافذة اختيار مكان الحفظ
 * @param pageOf      "صفحة %d من %d"
 * @param previous    تلميح زر الصفحة السابقة
 * @param next        تلميح زر الصفحة التالية
 * @param zoomIn      تلميح زر التكبير
 * @param zoomOut     تلميح زر التصغير
 * @param fitWidth    زر ملء العرض
 * @param fitPage     زر عرض الصفحة كاملة
 * @param landscape   وصف الورقة المقلوبة بالعرض فى قائمة المقاسات
 * @param preparing   والصفحة بتتجهّز
 * @param noPrinter   مفيش طابعة معرّفة على الجهاز
 * @param sent        "أُرسل إلى %s"
 * @param saved       "حُفظ فى %s"
 * @param printFailed الطباعة فشلت
 * @param saveFailed  الحفظ فشل
 * @param buildFailed المستند نفسه ما اتبناش
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
