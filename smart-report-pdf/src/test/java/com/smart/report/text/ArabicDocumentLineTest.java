package com.smart.report.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ArabicTextShaper#forDisplay(String, String, boolean)}: السطر زي ما هيترسم في مستند.
 * الناتج بترتيب العرض من اليسار لليمين، فأول حرف في السلسلة هو أقصى اليسار على الورقة.
 */
class ArabicDocumentLineTest {

    private static String rtl(String line) {
        return ArabicTextShaper.forDisplay(line, line, true);
    }

    private static String ltr(String line) {
        return ArabicTextShaper.forDisplay(line, line, false);
    }

    /** بقواعد Unicode السالب في صفحة عربية بيروح آخر الرقم: "1,800.00-". */
    @Test
    void negativeAmountKeepsItsSignInFront() {
        assertEquals("-1,800.00", rtl("-1,800.00"));
        assertEquals("-7,200.00", ltr("-7,200.00"));
    }

    /** التاريخ بعد كلمة عربية كان بيتقلب "01-10-2025". */
    @Test
    void dateInsideAnArabicSentenceReadsAsWritten() {
        String display = rtl("من 2025-10-01");
        assertTrue(display.startsWith("2025-10-01 "), display);
    }

    /** علامة النسبة كانت بتسبق رقمها "%113.44". */
    @Test
    void percentageKeepsItsSignAfterTheNumber() {
        assertTrue(rtl("نسبة 113.44%").startsWith("113.44% "), rtl("نسبة 113.44%"));
        assertEquals("12.5%", rtl("12.5%"));
    }

    /** مجموعات التليفون كانت بتترص من اليمين لليسار: "0000 000 0100". */
    @Test
    void phoneNumberWrittenInGroupsStaysWhole() {
        assertTrue(rtl("ت: 0100 000 0000").startsWith("0100 000 0000 "), rtl("ت: 0100 000 0000"));
        assertTrue(rtl("موبايل +20 100 000 0000").startsWith("+20 100 000 0000 "),
                rtl("موبايل +20 100 000 0000"));
        assertTrue(rtl("ت: 0100-000-0000").startsWith("0100-000-0000 "), rtl("ت: 0100-000-0000"));
        assertEquals("0100 000 0000", rtl("0100 000 0000"));
    }

    /** أرقام قليلة بينها مسافات مش تليفون: بتتقرا باتجاه السطر، زي أي شاشة. */
    @Test
    void shortNumbersApartAreNotTakenForAPhone() {
        assertEquals("3 2 1", rtl("1 2 3"));
        assertEquals("1 2 3", ltr("1 2 3"));
    }

    /** بعد حرف لاتيني الأرقام اللي بعده من نفس القطعة: "Pepsi 330" مش "330 Pepsi". */
    @Test
    void latinWordKeepsTheNumberThatFollowsIt() {
        String display = rtl("رمل Pepsi 330");
        assertTrue(display.startsWith("Pepsi 330 "), display);
    }

    @Test
    void productCodeIsNotTurnedRound() {
        assertTrue(rtl("صنف NC7013").startsWith("NC7013 "), rtl("صنف NC7013"));
    }

    /** السطر التاني من فقرة عربية بيفضل باتجاهها حتى لو بدأ بكلمة لاتينية. */
    @Test
    void lineTakesTheDirectionOfItsParagraph() {
        String paragraph = "توريد زلط لموقع Cairo Festival بالكامل";
        String second = "Cairo Festival بالكامل";

        String asPartOfParagraph = ArabicTextShaper.forDisplay(second, paragraph, true);
        assertTrue(asPartOfParagraph.endsWith("Cairo Festival"), "الكلمة اللاتينية أول السطر = أقصى اليمين: "
                + asPartOfParagraph);
    }

    /** نص مفيهوش حروف بياخد اتجاه الصفحة: في الإنجليزية يتقرا زي ما اتكتب. */
    @Test
    void textWithNoLettersFollowsThePage() {
        assertEquals("2026-09-23 09:01", ltr("2026-09-23 09:01"));
        assertEquals("09:01 2026-09-23", rtl("2026-09-23 09:01"));
    }

    @Test
    void arabicIsShapedAndReversedAndControlsAreRemoved() {
        String display = rtl("العربية 5");
        assertEquals("5 " + ArabicTextShaper.forDisplay("العربية"), display);
        for (char c : display.toCharArray()) {
            assertTrue(Character.getType(c) != Character.FORMAT, "علامة تحكم فضلت في الناتج");
        }
    }

    @Test
    void emptyAndNullLinesAreEmpty() {
        assertEquals("", ArabicTextShaper.forDisplay("", "", true));
        assertEquals("", ArabicTextShaper.forDisplay(null, null, true));
    }
}
