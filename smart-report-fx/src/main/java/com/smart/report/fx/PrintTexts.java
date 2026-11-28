package com.smart.report.fx;

/** كلام طريق الطباعة بالإعداد ({@link PdfPreview#print}): سؤال «اسأل كل مرة» وتنبيه الطابعة الغايبة. */
record PrintTexts(boolean rightToLeft, String askTitle, String askHeader, String print, String preview, String save,
                  String cancel, String missingPrinter, String noPrinter) {

    static final PrintTexts ARABIC = new PrintTexts(true, "طباعة", "كيف تريد إخراج المستند؟",
            "طباعة", "معاينة", "حفظ PDF", "إلغاء",
            "الطابعة «%s» غير موجودة على هذا الجهاز، فلم يُرسَل المستند إليها. اختر طابعة واطبع من هنا.",
            "لا توجد طابعة افتراضية على هذا الجهاز، فلم يُرسَل المستند. اختر طابعة واطبع من هنا.");
    static final PrintTexts ENGLISH = new PrintTexts(false, "Print", "How do you want to output the document?",
            "Print", "Preview", "Save PDF", "Cancel",
            "The printer \"%s\" is not on this computer, so the document was not sent to it. Choose a printer and"
                    + " print from here.",
            "This computer has no default printer, so the document was not sent. Choose a printer and print from"
                    + " here.");

    static PrintTexts of(boolean rightToLeft) {
        return rightToLeft ? ARABIC : ENGLISH;
    }
}
