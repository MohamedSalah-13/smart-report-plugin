package com.smart.report.util;

import com.smart.report.util.AmountInWords.Currency;
import com.smart.report.util.AmountInWords.EnglishCurrency;
import com.smart.report.util.AmountInWords.Negative;
import com.smart.report.util.AmountInWords.Noun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmountInWordsTest {

    /** العملات الجاهزة بكود ISO 4217. */
    private static final Map<String, Currency> ARABIC = Map.of(
            "EGP", Currency.EGP, "SAR", Currency.SAR, "AED", Currency.AED, "KWD", Currency.KWD,
            "USD", Currency.USD, "QAR", Currency.QAR, "BHD", Currency.BHD, "OMR", Currency.OMR,
            "JOD", Currency.JOD);
    private static final Map<String, EnglishCurrency> ENGLISH = Map.of(
            "EGP", EnglishCurrency.EGP, "SAR", EnglishCurrency.SAR, "AED", EnglishCurrency.AED,
            "KWD", EnglishCurrency.KWD, "USD", EnglishCurrency.USD, "QAR", EnglishCurrency.QAR,
            "BHD", EnglishCurrency.BHD, "OMR", EnglishCurrency.OMR, "JOD", EnglishCurrency.JOD);

    private static String egp(String amount) {
        return AmountInWords.arabicWords(new BigDecimal(amount), Currency.EGP);
    }

    private static String sar(String amount) {
        return AmountInWords.arabicWords(new BigDecimal(amount), Currency.SAR);
    }

    private static String english(String amount) {
        return AmountInWords.englishWords(new BigDecimal(amount), EnglishCurrency.EGP);
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

    /** خانات الكسر في كل عملة جاهزة زي ISO 4217 (جدول java.util.Currency). */
    @ParameterizedTest
    @CsvSource({"EGP", "SAR", "AED", "KWD", "USD", "QAR", "BHD", "OMR", "JOD", "SDG", "GBP", "YER", "MAD", "IQD",
            "LYD", "TND", "DZD", "SYP", "LBP", "TRY", "EUR"})
    void readyCurrenciesHaveTheirIsoFractionDigits(String code) {
        int iso = java.util.Currency.getInstance(code).getDefaultFractionDigits();
        assertEquals(iso, Currency.forCode(code).orElseThrow().fractionDigits());
        assertEquals(iso, EnglishCurrency.forCode(code).orElseThrow().fractionDigits());
    }

    /** البحث بالكود: الجاهزة ترجع نفسها، والكود المجهول فاضي، والحروف الصغيرة والمسافات مقبولة. */
    @Test
    void currenciesAreFoundByTheirIsoCode() {
        ARABIC.forEach((code, currency) -> assertSame(currency, Currency.forCode(code).orElseThrow()));
        ENGLISH.forEach((code, currency) -> assertSame(currency, EnglishCurrency.forCode(code).orElseThrow()));
        assertSame(Currency.EUR, Currency.forCode(" eur ").orElseThrow());
        assertSame(EnglishCurrency.TRY, EnglishCurrency.forCode("try").orElseThrow());
        assertTrue(Currency.forCode("XYZ").isEmpty());
        assertTrue(Currency.forCode("").isEmpty());
        assertTrue(Currency.forCode(null).isEmpty());
        assertTrue(EnglishCurrency.forCode("JPY").isEmpty());
        assertTrue(EnglishCurrency.forCode(null).isEmpty());
    }

    /** العملات المضافة: واحد، اثنان، الجمع، المنصوب، والكسر. */
    @ParameterizedTest
    @CsvSource({
            "SDG, 1,        جنيه واحد",
            "SDG, 2,        جنيهان",
            "SDG, 5,        خمسة جنيهات",
            "SDG, 0.25,     خمسة وعشرون قرشًا",
            "GBP, 1,        جنيه واحد",
            "GBP, 2.02,     جنيهان وبنسان",
            "GBP, 0.07,     سبعة بنسات",
            "GBP, 12.50,    اثنا عشر جنيهًا وخمسون بنسًا",
            "YER, 1,        ريال واحد",
            "YER, 2,        ريالان",
            "YER, 300,      ثلاثمائة ريال",
            "YER, 0.05,     خمسة فلوس",
            "MAD, 1,        درهم واحد",
            "MAD, 2,        درهمان",
            "MAD, 4,        أربعة دراهم",
            "MAD, 0.01,     سنتيم واحد",
            "MAD, 0.20,     عشرون سنتيمًا",
            "MAD, 0.03,     ثلاثة سنتيمات",
            "IQD, 1,        دينار واحد",
            "IQD, 2,        ديناران",
            "IQD, 25000,    خمسة وعشرون ألف دينار",
            "IQD, 0.250,    مائتان وخمسون فلسًا",
            "LYD, 1,        دينار واحد",
            "LYD, 3.500,    ثلاثة دنانير وخمسمائة درهم",
            "LYD, 0.002,    درهمان",
            "LYD, 0.015,    خمسة عشر درهمًا",
            "TND, 1,        دينار واحد",
            "TND, 2.001,    ديناران ومليم واحد",
            "TND, 0.005,    خمسة مليمات",
            "TND, 0.750,    سبعمائة وخمسون مليمًا",
            "DZD, 1,        دينار واحد",
            "DZD, 10,       عشرة دنانير",
            "DZD, 0.02,     سنتيمان",
            "DZD, 0.50,     خمسون سنتيمًا",
            "SYP, 1,        ليرة واحدة",
            "SYP, 2,        ليرتان",
            "SYP, 3,        ثلاث ليرات",
            "SYP, 11,       إحدى عشرة ليرةً",
            "SYP, 21,       إحدى وعشرون ليرةً",
            "SYP, 1000,     ألف ليرة",
            "SYP, 0.50,     خمسون قرشًا",
            "LBP, 1,        ليرة واحدة",
            "LBP, 2,        ليرتان",
            "LBP, 101,      مائة وواحدة ليرة",
            "LBP, 1500000,  مليون وخمسمائة ألف ليرة",
            "TRY, 1,        ليرة واحدة",
            "TRY, 2.02,     ليرتان وقرشان",
            "TRY, 12,       اثنتا عشرة ليرةً",
            "TRY, 0.05,     خمسة قروش",
            "EUR, 1,        يورو واحد",
            "EUR, 2,        يوروان",
            "EUR, 3,        ثلاثة يوروهات",
            "EUR, 11,       أحد عشر يورو",
            "EUR, 100,      مائة يورو",
            "EUR, 0.99,     تسعة وتسعون سنتًا",
            "EUR, 0.05,     خمسة سنتات",
    })
    void addedCurrencies(String code, String amount, String words) {
        assertEquals(words, AmountInWords.arabicWords(new BigDecimal(amount), Currency.forCode(code).orElseThrow()));
    }

    @ParameterizedTest
    @CsvSource({
            "SDG, 1.01,     one Sudanese pound and one piastre",
            "SDG, 2,        two Sudanese pounds",
            "GBP, 1.01,     one pound sterling and one penny",
            "GBP, 2.50,     two pounds sterling and fifty pence",
            "YER, 1,        one Yemeni rial",
            "YER, 0.10,     ten fils",
            "MAD, 1.01,     one Moroccan dirham and one centime",
            "MAD, 0.20,     twenty centimes",
            "IQD, 2.250,    two Iraqi dinars and two hundred fifty fils",
            "LYD, 1.001,    one Libyan dinar and one dirham",
            "LYD, 0.500,    five hundred dirhams",
            "TND, 1.001,    one Tunisian dinar and one millime",
            "TND, 0.750,    seven hundred fifty millimes",
            "DZD, 3.01,     three Algerian dinars and one centime",
            "SYP, 1,        one Syrian pound",
            "SYP, 0.50,     fifty piastres",
            "LBP, 2,        two Lebanese pounds",
            "TRY, 1.01,     one Turkish lira and one kuruş",
            "TRY, 5.50,     five Turkish lira and fifty kuruş",
            "EUR, 1.01,     one euro and one cent",
            "EUR, 2.50,     two euros and fifty cents",
    })
    void addedCurrenciesInEnglish(String code, String amount, String words) {
        assertEquals(words,
                AmountInWords.englishWords(new BigDecimal(amount), EnglishCurrency.forCode(code).orElseThrow()));
    }

    /** المبلغ السالب: حروف القيمة المطلقة، والكلمة قبل النص كله أو بعده. */
    @Test
    void negativeAmountsTakeTheProgramsWord() {
        assertEquals("فقط مائة وخمسون جنيهًا لا غير (دائن)",
                AmountInWords.arabic(new BigDecimal("-150"), Currency.EGP, Negative.suffix("(دائن)")));
        assertEquals("سالب مائة وخمسون جنيهًا",
                AmountInWords.arabicWords(new BigDecimal("-150"), Currency.EGP, Negative.prefix("سالب")));
        assertEquals("Minus one hundred US dollars and one cent only",
                AmountInWords.english(new BigDecimal("-100.01"), EnglishCurrency.USD, Negative.prefix("minus")));
        assertEquals("Seven Egyptian pounds only (credit)",
                AmountInWords.english(new BigDecimal("-7"), EnglishCurrency.EGP, Negative.suffix(" (credit) ")));
        assertEquals("[ seven Egyptian pounds ]", AmountInWords.englishWords(new BigDecimal("-7"),
                EnglishCurrency.EGP, new Negative("[", "]")));
    }

    /** الموجب والصفر بيتكتبوا زي من غير {@link Negative}؛ والسالب اللي بيتقرّب لصفر من غير الكلمة. */
    @Test
    void negativeWordOnlyForAmountsBelowZero() {
        Negative credit = Negative.suffix("(دائن)");
        assertEquals(AmountInWords.arabic(new BigDecimal("7200"), Currency.EGP),
                AmountInWords.arabic(new BigDecimal("7200"), Currency.EGP, credit));
        assertEquals("صفر جنيه", AmountInWords.arabicWords(BigDecimal.ZERO, Currency.EGP, credit));
        assertEquals("صفر جنيه", AmountInWords.arabicWords(new BigDecimal("-0.004"), Currency.EGP, credit));
        assertEquals("قرش واحد (دائن)", AmountInWords.arabicWords(new BigDecimal("-0.005"), Currency.EGP, credit));
        assertEquals("zero Egyptian pounds", AmountInWords.englishWords(new BigDecimal("-0.00"),
                EnglishCurrency.EGP, Negative.prefix("minus")));
    }

    @Test
    void invalidNegativeOptionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Negative(null, " "));
        assertThrows(IllegalArgumentException.class, () -> Negative.prefix(""));
        assertThrows(NullPointerException.class,
                () -> AmountInWords.arabic(new BigDecimal("-1"), Currency.EGP, null));
        assertThrows(NullPointerException.class,
                () -> AmountInWords.arabic(null, Currency.EGP, Negative.prefix("سالب")));
        assertThrows(IllegalArgumentException.class, () -> AmountInWords.arabicWords(
                new BigDecimal("-1000000000000"), Currency.EGP, Negative.prefix("سالب")), "الحد بيتطبق على السالب");
    }

    /** العملات الجديدة: الريال القطري ودرهمه، والدينار البحريني والأردني وفلسهم (ثلاث خانات). */
    @ParameterizedTest
    @CsvSource({
            "QAR, 1,         ريال واحد",
            "QAR, 2,         ريالان",
            "QAR, 7,         سبعة ريالات",
            "QAR, 0.05,      خمسة دراهم",
            "QAR, 0.50,      خمسون درهمًا",
            "QAR, 15.25,     خمسة عشر ريالًا وخمسة وعشرون درهمًا",
            "QAR, 200,       مائتا ريال",
            "QAR, 1000.01,   ألف ريال ودرهم واحد",
            "BHD, 1,         دينار واحد",
            "BHD, 2.002,     ديناران وفلسان",
            "BHD, 3.500,     ثلاثة دنانير وخمسمائة فلس",
            "BHD, 0.005,     خمسة فلوس",
            "BHD, 12.250,    اثنا عشر دينارًا ومائتان وخمسون فلسًا",
            "BHD, 0.0005,    فلس واحد",
            "JOD, 1.250,     دينار واحد ومائتان وخمسون فلسًا",
            "JOD, 10,        عشرة دنانير",
            "JOD, 25.005,    خمسة وعشرون دينارًا وخمسة فلوس",
            "JOD, 100.100,   مائة دينار ومائة فلس",
            "JOD, 3000,      ثلاثة آلاف دينار",
    })
    void gulfAndJordanianCurrencies(String code, String amount, String words) {
        assertEquals(words, AmountInWords.arabicWords(new BigDecimal(amount), ARABIC.get(code)));
    }

    /** الريال العماني: ألف بيسة، والبيسة مؤنثة فالعدد بيتغيّر معاها زي الهللة. */
    @ParameterizedTest
    @CsvSource({
            "0.001,  بيسة واحدة",
            "0.002,  بيستان",
            "0.003,  ثلاث بيسات",
            "0.010,  عشر بيسات",
            "0.011,  إحدى عشرة بيسةً",
            "0.012,  اثنتا عشرة بيسةً",
            "0.025,  خمس وعشرون بيسةً",
            "0.100,  مائة بيسة",
            "0.103,  مائة وثلاث بيسات",
            "0.200,  مائتا بيسة",
            "0.250,  مائتان وخمسون بيسةً",
            "1.500,  ريال واحد وخمسمائة بيسة",
            "5.750,  خمسة ريالات وسبعمائة وخمسون بيسةً",
            "11,     أحد عشر ريالًا",
    })
    void omaniBaisaIsFeminine(String amount, String words) {
        assertEquals(words, AmountInWords.arabicWords(new BigDecimal(amount), Currency.OMR));
    }

    /** المثال اللي في README: حرف كبير في الأول و«only» في الآخر، و«and» قبل الكسر بس. */
    @Test
    void englishInvoiceForm() {
        assertEquals("Seven thousand two hundred Egyptian pounds only",
                AmountInWords.english(new BigDecimal("7200"), EnglishCurrency.EGP));
        assertEquals("One thousand two hundred thirty-four Egyptian pounds and fifty piastres only",
                AmountInWords.english(new BigDecimal("1234.50"), EnglishCurrency.EGP));
        assertEquals("Fifty piastres only", AmountInWords.english(new BigDecimal("0.50"), EnglishCurrency.EGP));
        assertEquals("Zero Egyptian pounds only", AmountInWords.english(BigDecimal.ZERO, EnglishCurrency.EGP));
    }

    @ParameterizedTest
    @CsvSource({
            "1,     one Egyptian pound",
            "2,     two Egyptian pounds",
            "3,     three Egyptian pounds",
            "10,    ten Egyptian pounds",
            "11,    eleven Egyptian pounds",
            "12,    twelve Egyptian pounds",
            "15,    fifteen Egyptian pounds",
            "19,    nineteen Egyptian pounds",
            "20,    twenty Egyptian pounds",
            "21,    twenty-one Egyptian pounds",
            "40,    forty Egyptian pounds",
            "99,    ninety-nine Egyptian pounds",
            "100,   one hundred Egyptian pounds",
            "101,   one hundred one Egyptian pounds",
            "110,   one hundred ten Egyptian pounds",
            "250,   two hundred fifty Egyptian pounds",
            "999,   nine hundred ninety-nine Egyptian pounds",
    })
    void englishUnitsBelowAThousand(String amount, String words) {
        assertEquals(words, english(amount));
    }

    @ParameterizedTest
    @CsvSource({
            "1000,        one thousand Egyptian pounds",
            "1001,        one thousand one Egyptian pounds",
            "2000,        two thousand Egyptian pounds",
            "7200,        seven thousand two hundred Egyptian pounds",
            "11500,       eleven thousand five hundred Egyptian pounds",
            "100000,      one hundred thousand Egyptian pounds",
            "250500,      two hundred fifty thousand five hundred Egyptian pounds",
            "1000000,     one million Egyptian pounds",
            "1000001,     one million one Egyptian pounds",
            "1250000,     one million two hundred fifty thousand Egyptian pounds",
            "3000000,     three million Egyptian pounds",
            "5000000000,  five billion Egyptian pounds",
    })
    void englishThousandsMillionsAndBillions(String amount, String words) {
        assertEquals(words, english(amount));
    }

    @Test
    void englishLargestAmountIsWrittenInFull() {
        assertEquals("nine hundred ninety-nine billion nine hundred ninety-nine million nine hundred ninety-nine"
                + " thousand nine hundred ninety-nine Egyptian pounds", english("999999999999"));
    }

    /** الكسر بعد «and»، أو لوحده؛ والصفر جمع: «zero Egyptian pounds»؛ والتقريب زي العربي. */
    @ParameterizedTest
    @CsvSource({
            "0,       zero Egyptian pounds",
            "0.01,    one piastre",
            "0.05,    five piastres",
            "0.50,    fifty piastres",
            "1.01,    one Egyptian pound and one piastre",
            "10.999,  eleven Egyptian pounds",
            "10.005,  ten Egyptian pounds and one piastre",
            "10.004,  ten Egyptian pounds",
    })
    void englishFractionZeroAndRounding(String amount, String words) {
        assertEquals(words, english(amount));
    }

    @ParameterizedTest
    @CsvSource({
            "SAR, 5.75,     five Saudi riyals and seventy-five halalas",
            "SAR, 0.01,     one halala",
            "AED, 1.01,     one UAE dirham and one fils",
            "AED, 0.25,     twenty-five fils",
            "KWD, 1.250,    one Kuwaiti dinar and two hundred fifty fils",
            "USD, 2.01,     two US dollars and one cent",
            "USD, 0.99,     ninety-nine cents",
            "QAR, 15.25,    fifteen Qatari riyals and twenty-five dirhams",
            "QAR, 1.01,     one Qatari riyal and one dirham",
            "BHD, 3.500,    three Bahraini dinars and five hundred fils",
            "OMR, 0.001,    one baisa",
            "OMR, 1.500,    one Omani rial and five hundred baisa",
            "JOD, 12.250,   twelve Jordanian dinars and two hundred fifty fils",
            "JOD, 0.0005,   one fils",
    })
    void englishReadyCurrencies(String code, String amount, String words) {
        assertEquals(words, AmountInWords.englishWords(new BigDecimal(amount), ENGLISH.get(code)));
    }

    @Test
    void programsCanDefineTheirOwnEnglishCurrency() {
        EnglishCurrency lyd = new EnglishCurrency("Libyan dinar", "Libyan dinars", "dirham", "dirhams", 3);
        assertEquals("Two Libyan dinars and five hundred dirhams only",
                AmountInWords.english(new BigDecimal("2.5"), lyd));

        EnglishCurrency points = new EnglishCurrency("point", "points", null, null, 0);
        assertEquals("three points", AmountInWords.englishWords(new BigDecimal("3.4"), points));
    }

    @Test
    void invalidEnglishInputIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> english("-1"));
        assertThrows(IllegalArgumentException.class, () -> english("1000000000000"));
        assertThrows(NullPointerException.class, () -> AmountInWords.english(null, EnglishCurrency.EGP));
        assertThrows(NullPointerException.class, () -> AmountInWords.english(BigDecimal.ONE, null));
        assertThrows(IllegalArgumentException.class,
                () -> new EnglishCurrency("pound", "pounds", null, null, 2), "كسور من غير اسم الكسر");
        assertThrows(IllegalArgumentException.class, () -> new EnglishCurrency("pound", " ", "piastre", "piastres", 2));
        assertThrows(IllegalArgumentException.class, () -> new EnglishCurrency("pound", "pounds", "p", "p", 4));
    }
}
