package com.smart.report.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FontSetTest {

    @Test
    void bundledFontIsUsedWithoutAnySetup() {
        PdfFontResolver.clearCache();
        PdfFontResolver.Fonts fonts = PdfFontResolver.resolve();
        assertTrue(fonts.available(), "الخط المضمَّن لازم يتلاقى من غير أي إعداد");
        assertTrue(fonts.bold() != fonts.regular(), "وله وجه عريض حقيقي");
    }

    /**
     * الخط العريض المضمَّن ما فيهوش علامة الطرح. لو الرقم السالب اترسم بيه كله، صف الإجمالي يطبع
     * "-7,825.00" من غير إشارته فيتقرا موجبًا — والنص المستخرج من الملف يفضل يقول "-".
     */
    @Test
    void boldNegativeNumberKeepsItsMinusSign() throws Exception {
        try (PDDocument pdf = new PDDocument(); FontSet fonts = FontSet.load(pdf)) {
            List<FontSet.Run> runs = fonts.runs("-7,825.00", true);

            assertEquals("-7,825.00", String.join("", runs.stream().map(FontSet.Run::text).toList()));
            FontSet.Run minus = runs.get(0);
            assertEquals("-", minus.text());
            assertTrue(minus.fakeBold(), "الإشارة بتترسم بالخط العادي مسمّكًا");
            assertFalse(runs.get(1).fakeBold(), "والأرقام بالخط العريض نفسه");
            assertTrue(FontSet.width(runs, 10f) > 0);
        }
    }

    /** حرف مش في أي خط ما يوقّفش الطباعة: بيتطبع علامة استفهام. */
    @Test
    void glyphNoFontHasIsPrintedAsQuestionMark() throws Exception {
        try (PDDocument pdf = new PDDocument(); FontSet fonts = FontSet.load(pdf)) {
            // منطقة الاستخدام الخاص: مفيش خط بيرسمها.
            List<FontSet.Run> runs = fonts.runs("a\uE123b", false);
            assertEquals("a?b", String.join("", runs.stream().map(FontSet.Run::text).toList()));
        }
    }

    /** الحركة بتتوسّط فوق حرفها بإزاحة، وعرض المقطع ما بيتغيرش. */
    @Test
    void diacriticIsShiftedBackOverItsBaseLetter() throws Exception {
        try (PDDocument pdf = new PDDocument(); FontSet fonts = FontSet.load(pdf)) {
            // ط منفصلة (صورة عرض) وبعدها ضمة، بترتيب الرسم.
            FontSet.Run marked = fonts.runs("\uFEC1\u064F", false).get(0);
            Object[] parts = marked.positioned();
            assertNotNull(parts);
            assertEquals(4, parts.length);
            assertTrue((Float) parts[1] > 0, "الإزاحة لورا");
            assertEquals(-(Float) parts[1], (Float) parts[3], 0.001f, "وبتترد بعد الحركة");

            assertNull(fonts.runs("plain", false).get(0).positioned(), "نص بلا حركات بيترسم قطعة واحدة");
        }
    }
}
