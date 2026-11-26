package com.smart.report.text;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.text.Normalizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArabicTextShaperTest {

    @Test
    void shapesLettersAccordingToTheirPosition() {
        // ا (منفصلة) ل (أولية) ع (وسطية) ر (نهائية) ب (أولية) ي (وسطية) ة (نهائية)
        assertEquals("ﺍﻟﻌﺮﺑﻴﺔ", ArabicTextShaper.shape("العربية"));
    }

    @Test
    void mergesLamAlefIntoASingleLigature() {
        assertEquals("ﻻ", ArabicTextShaper.shape("لا"));          // منفصلة
        assertEquals("ﻧﻼ", ArabicTextShaper.shape("نلا"));   // نهائية بعد نون متصلة
        assertEquals("ﻵ", ArabicTextShaper.shape("لآ"));
        assertEquals("ﻷ", ArabicTextShaper.shape("لأ"));
        assertEquals("ﻹ", ArabicTextShaper.shape("لإ"));
    }

    @Test
    void keepsDiacriticsAttachedAndTransparentForJoining() {
        // الحركات ما بتكسرش الاتصال: الحاء تفضل وسطية والميم الأخيرة تفضل وسطية
        assertEquals("ﻣُﺤَﻤَّﺪ", ArabicTextShaper.shape("مُحَمَّد"));
    }

    @Test
    void reversesArabicRunsForDisplayButKeepsLatinAndDigitsInOrder() {
        assertEquals("ﺔﻴﺑﺮﻌﻟﺍ", ArabicTextShaper.forDisplay("العربية"));
        assertEquals("Engineering 12 ﻢﺴﻗ", ArabicTextShaper.forDisplay("قسم 12 Engineering"));
    }

    @Test
    void keepsMarksAfterTheirBaseLetterWhenReversing() {
        String display = ArabicTextShaper.forDisplay("مُحَمَّد");
        assertEquals("ﺪﻤَّﺤَﻣُ", display);
    }

    @Test
    void mirrorsBracketsInsideRightToLeftRuns() {
        String display = ArabicTextShaper.forDisplay("(قسم)");
        assertTrue(display.startsWith("("), "القوس المفتوح لازم يفضل على الشمال بصريًا: " + display);
        assertTrue(display.endsWith(")"), "القوس المقفول لازم يفضل على اليمين بصريًا: " + display);
    }

    @Test
    void leavesNonArabicTextUntouched() {
        assertEquals("Employee Report", ArabicTextShaper.forDisplay("Employee Report"));
        assertEquals("", ArabicTextShaper.forDisplay(""));
        assertNull(ArabicTextShaper.forDisplay(null));
        assertFalse(ArabicTextShaper.containsArabic("Employee Report"));
        assertTrue(ArabicTextShaper.containsArabic("تقرير"));
    }

    @Test
    void displayFormsNormalizeBackToTheOriginalLetters() {
        String original = "تقرير الموظفين";
        String normalized = Normalizer.normalize(ArabicTextShaper.forDisplay(original), Normalizer.Form.NFKC);
        // بعد إلغاء صور العرض بترجع نفس الحروف، بس بالترتيب البصري (معكوس)
        assertEquals(new StringBuilder(original).reverse().toString(), normalized);
    }

    /**
     * حارس دائم على جداول الصور: كل صورة عرض لازم ترجع لحرفها الأساسي عبر NFKC حسب
     * بيانات اليونيكود في الـ JDK. لو حد عدّل رقمًا في الجدول غلط، الاختبار ده بيقع.
     */
    @Test
    void presentationFormTableMatchesUnicodeData() throws Exception {
        char[] base = (char[]) field("FORM_BASE").get(null);
        byte[] count = (byte[]) field("FORM_COUNT").get(null);

        assertEquals(42, base.length, "الجدول لازم يغطي ء..ي");
        assertEquals(base.length, count.length, "الجدولين لازم يبقوا بنفس الطول");

        for (int i = 0; i < base.length; i++) {
            char letter = (char) (0x0621 + i);
            for (int form = 0; form < count[i]; form++) {
                char presentationForm = (char) (base[i] + form);
                assertEquals(String.valueOf(letter),
                        Normalizer.normalize(String.valueOf(presentationForm), Normalizer.Form.NFKC),
                        () -> String.format("صورة خاطئة للحرف U+%04X", (int) letter));
            }
        }
    }

    @Test
    void lamAlefLigaturesMatchUnicodeData() {
        int[][] ligatures = {{0x0622, 0xFEF5}, {0x0623, 0xFEF7}, {0x0625, 0xFEF9}, {0x0627, 0xFEFB}};
        for (int[] ligature : ligatures) {
            for (int form = 0; form < 2; form++) {
                assertEquals("ل" + (char) ligature[0],
                        Normalizer.normalize(String.valueOf((char) (ligature[1] + form)), Normalizer.Form.NFKC),
                        () -> String.format("دمج لام-ألف خاطئ عند U+%04X", ligature[1]));
            }
        }
    }

    private static Field field(String name) throws Exception {
        Field field = ArabicTextShaper.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
