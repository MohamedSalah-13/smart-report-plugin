package com.smart.report.pdf;

import com.smart.report.util.AmountInWords;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** التفقيط مرسومًا في صفحة عربية: كل حروفه في الخط المضمَّن، والتنوين ما بيكسرش الكلمة. */
class AmountInWordsPdfTest {

    /**
     * النص المستخرج بلا حركات وبحروف موحّدة: الحرف اللي بيرجع من صورة العرض بيختلف حسب جدول الخط،
     * ومكان التنوين في النص المستخرج مش ثابت.
     */
    private static String canonical(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.getType(c) == Character.NON_SPACING_MARK) {
                continue;
            }
            out.append(switch (c) {
                case 'ي', 'ی', 'ى' -> 'ي';
                case 'ه', 'ھ', 'ہ' -> 'ه';
                case 'ا', 'أ', 'إ', 'آ' -> 'ا';
                default -> c;
            });
        }
        return out.toString().replaceAll("\\s+", " ");
    }

    @Test
    void amountInWordsIsDrawnWholeInAnArabicPage() throws Exception {
        String total = AmountInWords.arabic(new BigDecimal("1234.50"), AmountInWords.Currency.EGP);
        String round = AmountInWords.arabic(new BigDecimal("7200"), AmountInWords.Currency.EGP);
        byte[] pdf = PdfDocument.rtl(Paper.A5)
                .summaryTotal("الإجمالى", "1,234.50")
                .text(total)
                .text(round)
                .toBytes();

        String text;
        try (PDDocument document = Loader.loadPDF(pdf)) {
            text = new PDFTextStripper().getText(document);
        }
        assertFalse(text.contains("?"), "حرف مش في الخط اترسم ؟: " + text);
        String drawn = canonical(text);
        assertTrue(drawn.contains(canonical(total)), "التفقيط ناقص أو مقلوب: " + drawn);
        assertTrue(drawn.contains(canonical(round)), "التفقيط ناقص أو مقلوب: " + drawn);
    }
}
