package com.smart.report.util;

import com.smart.report.util.AmountInWords.Currency;
import com.smart.report.util.AmountInWords.Noun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmountInWordsTest {

    private static String egp(String amount) {
        return AmountInWords.arabicWords(new BigDecimal(amount), Currency.EGP);
    }

    private static String sar(String amount) {
        return AmountInWords.arabicWords(new BigDecimal(amount), Currency.SAR);
    }

    /** المثال اللي في README والفواتير: «فقط ... لا غير». */
    @Test
    void invoiceFormWrapsTheWords() {
        assertEquals("فقط سبعة آلاف ومائتا جنيه لا غير", AmountInWords.arabic(new BigDecimal("7200"), Currency.EGP));
        assertEquals("فقط ألف ومائتان وأربعة وثلاثون جنيهًا وخمسون قرشًا لا غير",
                AmountInWords.arabic(new BigDecimal("1234.50"), Currency.EGP));
    }

    /** صيغة المعدود بتتغيّر مع العدد: مفرد، مثنى، جمع، منصوب. */
    @ParameterizedTest
    @CsvSource({
            "1,    جنيه واحد",
            "2,    جنيهان",
            "3,    ثلاثة جنيهات",
            "10,   عشرة جنيهات",
            "11,   أحد عشر جنيهًا",
            "12,   اثنا عشر جنيهًا",
            "15,   خمسة عشر جنيهًا",
            "20,   عشرون جنيهًا",
            "21,   واحد وعشرون جنيهًا",
            "99,   تسعة وتسعون جنيهًا",
            "100,  مائة جنيه",
            "101,  مائة وواحد جنيه",
            "103,  مائة وثلاثة جنيهات",
            "200,  مائتا جنيه",
            "250,  مائتان وخمسون جنيهًا",
            "999,  تسعمائة وتسعة وتسعون جنيهًا",
    })
    void unitsTakeTheRightNounForm(String amount, String words) {
        assertEquals(words, egp(amount));
    }

    /** الآلاف والملايين: آخر جزء مضاف للعملة («ألف جنيه»)، واللي بعده كلام تاني منصوب («ألفًا و...»). */
    @ParameterizedTest
    @CsvSource({
            "1000,        ألف جنيه",
            "1001,        ألف وجنيه واحد",
            "2000,        ألفا جنيه",
            "2500,        ألفان وخمسمائة جنيه",
            "3000,        ثلاثة آلاف جنيه",
            "11000,       أحد عشر ألف جنيه",
            "11500,       أحد عشر ألفًا وخمسمائة جنيه",
            "100000,      مائة ألف جنيه",
            "103000,      مائة وثلاثة آلاف جنيه",
            "200000,      مائتا ألف جنيه",
            "250500,      مائتان وخمسون ألفًا وخمسمائة جنيه",
            "1000001,     مليون وجنيه واحد",
            "1250000,     مليون ومائتان وخمسون ألف جنيه",
            "2000000,     مليونا جنيه",
            "3000000,     ثلاثة ملايين جنيه",
            "5000000000,  خمسة مليارات جنيه",
    })
    void thousandsAndMillionsAgreeWithWhatFollows(String amount, String words) {
        assertEquals(words, egp(amount));
    }

    @Test
    void largestAmountIsWrittenInFull() {
        assertEquals("تسعمائة وتسعة وتسعون مليارًا وتسعمائة وتسعة وتسعون مليونًا وتسعمائة وتسعة وتسعون ألفًا"
                + " وتسعمائة وتسعة وتسعون جنيهًا", egp("999999999999"));
    }

    @ParameterizedTest
    @CsvSource({
            "0.50,   خمسون قرشًا",
            "0.01,   قرش واحد",
            "0.05,   خمسة قروش",
            "0,      صفر جنيه",
            "1.01,   جنيه واحد وقرش واحد",
    })
    void fractionIsWrittenAfterTheUnitOrAlone(String amount, String words) {
        assertEquals(words, egp(amount));
    }

    /** الهللة مؤنثة: العدد بيتغيّر معاها. */
    @ParameterizedTest
    @CsvSource({
            "0.01,  هللة واحدة",
            "0.02,  هللتان",
            "0.03,  ثلاث هللات",
            "0.10,  عشر هللات",
            "0.11,  إحدى عشرة هللةً",
            "0.12,  اثنتا عشرة هللةً",
            "0.13,  ثلاث عشرة هللةً",
            "0.18,  ثماني عشرة هللةً",
            "0.21,  إحدى وعشرون هللةً",
            "0.22,  اثنتان وعشرون هللةً",
            "5.75,  خمسة ريالات وخمس وسبعون هللةً",
    })
    void feminineFractionChangesTheNumbers(String amount, String words) {
        assertEquals(words, sar(amount));
    }

    @Test
    void currencyWithThreeFractionDigits() {
        assertEquals("دينار واحد ومائتان وخمسون فلسًا",
                AmountInWords.arabicWords(new BigDecimal("1.250"), Currency.KWD));
        assertEquals("مائة وثلاثة فلوس", AmountInWords.arabicWords(new BigDecimal("0.103"), Currency.KWD));
    }

    /** المبلغ بيتقرّب لخانات العملة، والنص لفوق. */
    @Test
    void amountIsRoundedToTheCurrencysDigits() {
        assertEquals("أحد عشر جنيهًا", egp("10.999"));
        assertEquals("عشرة جنيهات وقرش واحد", egp("10.005"));
        assertEquals("عشرة جنيهات", egp("10.004"));
    }

    @Test
    void programsCanDefineTheirOwnCurrency() {
        Currency jod = new Currency(new Noun("دينار", "ديناران", "دنانير", "دينارًا", false),
                new Noun("فلس", "فلسان", "فلوس", "فلسًا", false), 3);
        assertEquals("ديناران وخمسمائة فلس", AmountInWords.arabicWords(new BigDecimal("2.5"), jod));

        Currency whole = new Currency(new Noun("نقطة", "نقطتان", "نقاط", "نقطةً", true), null, 0);
        assertEquals("ثلاث نقاط", AmountInWords.arabicWords(new BigDecimal("3.4"), whole));
    }

    @Test
    void invalidInputIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> egp("-1"));
        assertThrows(IllegalArgumentException.class, () -> egp("1000000000000"));
        assertThrows(IllegalArgumentException.class, () -> egp("999999999999.995"), "بعد التقريب بيعدّي الحد");
        assertThrows(NullPointerException.class, () -> AmountInWords.arabicWords(null, Currency.EGP));
        assertThrows(IllegalArgumentException.class,
                () -> new Currency(Currency.EGP.unit(), null, 2), "عملة بكسور من غير اسم الكسر");
        assertThrows(IllegalArgumentException.class, () -> new Noun("جنيه", "", "جنيهات", "جنيهًا", false));
        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class, () -> egp("-7200"));
        assertTrue(negative.getMessage().contains("-7200"), negative.getMessage());
    }
}
