package com.smart.report.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Amount in words («التفقيط»): the amount written out in Arabic words, as on invoices and receipts.
 *
 * <pre>{@code
 * AmountInWords.arabic(new BigDecimal("7200"), AmountInWords.Currency.EGP)
 *     // فقط سبعة آلاف ومائتا جنيه لا غير
 * AmountInWords.arabic(new BigDecimal("1234.50"), AmountInWords.Currency.EGP)
 *     // فقط ألف ومائتان وأربعة وثلاثون جنيهًا وخمسون قرشًا لا غير
 * AmountInWords.arabicWords(new BigDecimal("0.13"), AmountInWords.Currency.SAR)
 *     // ثلاث عشرة هللةً
 * }</pre>
 *
 * <p><b>Rules:</b> the counted noun after the number is in the nominative case, as it is written after «فقط»
 * ("only"):</p>
 * <ul>
 *     <li>1 and 2 use the noun's own form: «جنيه واحد», «جنيهان», «ألف», «ألفان».</li>
 *     <li>3 to 10 take the plural, and the number takes the opposite gender of the noun: «ثلاثة جنيهات»,
 *         «ثلاث هللات».</li>
 *     <li>11 to 99 take the accusative singular: «أحد عشر جنيهًا», «إحدى عشرة هللةً», «خمسون قرشًا».</li>
 *     <li>Hundred, thousand and their multiples take the genitive singular, and the dual in a construct
 *         (idafa) drops its final «ن»: «مائة جنيه», «مائتا جنيه», «ألفا جنيه», «أحد عشر ألف جنيه».</li>
 *     <li>Hundreds followed by one or two use the form common on invoices: «مائة وواحد جنيه».</li>
 * </ul>
 *
 * <p>«مائة» is spelled with an alif, as on Egyptian invoices. The amount is rounded to the currency's fraction
 * digits (half up), and an amount of a thousand billion or more throws {@link IllegalArgumentException}. The
 * class is stateless and thread-safe.</p>
 *
 * <p><b>A negative amount</b> (a credit balance, for example) throws {@link IllegalArgumentException}, except
 * with {@link Negative}: the words are written for the absolute value, with the program's word before or after
 * them:</p>
 *
 * <pre>{@code
 * AmountInWords.arabic(new BigDecimal("-150"), Currency.EGP, Negative.suffix("(دائن)"))
 *     // فقط مائة وخمسون جنيهًا لا غير (دائن)
 * }</pre>
 *
 * <p><b>In English</b> for bilingual invoices, with a currency from {@link EnglishCurrency}:</p>
 *
 * <pre>{@code
 * AmountInWords.english(new BigDecimal("7200"), AmountInWords.EnglishCurrency.EGP)
 *     // Seven thousand two hundred Egyptian pounds only
 * AmountInWords.english(new BigDecimal("7200.50"), AmountInWords.EnglishCurrency.EGP)
 *     // Seven thousand two hundred Egyptian pounds and fifty piastres only
 * }</pre>
 *
 * <p>Ready-made currencies with both names: EGP, SAR, AED, KWD, USD, QAR, BHD, OMR, JOD, SDG, GBP, YER, MAD,
 * IQD, LYD, TND, DZD, SYP, LBP, TRY and EUR. Fraction digits follow ISO 4217: 3 for the Kuwaiti, Bahraini,
 * Jordanian, Iraqi, Libyan and Tunisian dinars and the Omani rial, and 2 for the rest.
 * {@link Currency#forCode} and {@link EnglishCurrency#forCode} find a currency by its code and return empty for
 * an unknown code, so the program does not print a wrong line.</p>
 */
public final class AmountInWords {

    private AmountInWords() {}

    /**
     * A counted noun in its four forms.
     *
     * @param singular   the singular: «جنيه»
     * @param dual       the nominative dual: «جنيهان»
     * @param plural     the plural after 3..10: «جنيهات»
     * @param accusative the accusative singular after 11..99: «جنيهًا»
     * @param feminine   feminine («هللة»): changes the form of the number
     */
    public record Noun(String singular, String dual, String plural, String accusative, boolean feminine) {

        public Noun {
            for (String form : new String[]{singular, dual, plural, accusative}) {
                if (form == null || form.isBlank()) {
                    throw new IllegalArgumentException("Every form of the noun is required.");
                }
            }
        }

        /** The dual in a construct (idafa): «مائتا», «ألفا جنيه», without the final «ن». */
        String dualInConstruct() {
            return dual.endsWith("ان") ? dual.substring(0, dual.length() - 1) : dual;
        }
    }

    /**
     * A currency: the unit, its fraction, and the number of fraction digits.
     *
     * @param unit           «جنيه» (pound)
     * @param fraction       «قرش» (piastre); {@code null} if {@code fractionDigits} is zero
     * @param fractionDigits 2 for the piastre and the halala, 3 for the Kuwaiti fils, 0 for no fraction
     */
    public record Currency(Noun unit, Noun fraction, int fractionDigits) {

        public static final Currency EGP = new Currency(
                new Noun("جنيه", "جنيهان", "جنيهات", "جنيهًا", false),
                new Noun("قرش", "قرشان", "قروش", "قرشًا", false), 2);
        public static final Currency SAR = new Currency(
                new Noun("ريال", "ريالان", "ريالات", "ريالًا", false),
                new Noun("هللة", "هللتان", "هللات", "هللةً", true), 2);
        public static final Currency AED = new Currency(
                new Noun("درهم", "درهمان", "دراهم", "درهمًا", false),
                new Noun("فلس", "فلسان", "فلوس", "فلسًا", false), 2);
        public static final Currency KWD = new Currency(
                new Noun("دينار", "ديناران", "دنانير", "دينارًا", false),
                new Noun("فلس", "فلسان", "فلوس", "فلسًا", false), 3);
        public static final Currency USD = new Currency(
                new Noun("دولار", "دولاران", "دولارات", "دولارًا", false),
                new Noun("سنت", "سنتان", "سنتات", "سنتًا", false), 2);
        /** The Qatari riyal: 100 dirhams. */
        public static final Currency QAR = new Currency(
                new Noun("ريال", "ريالان", "ريالات", "ريالًا", false),
                new Noun("درهم", "درهمان", "دراهم", "درهمًا", false), 2);
        /** The Bahraini dinar: 1000 fils, with the same words as {@link #KWD}. */
        public static final Currency BHD = new Currency(
                new Noun("دينار", "ديناران", "دنانير", "دينارًا", false),
                new Noun("فلس", "فلسان", "فلوس", "فلسًا", false), 3);
        /** The Omani rial: 1000 baisa; the baisa is feminine: «ثلاث بيسات», «إحدى عشرة بيسةً». */
        public static final Currency OMR = new Currency(
                new Noun("ريال", "ريالان", "ريالات", "ريالًا", false),
                new Noun("بيسة", "بيستان", "بيسات", "بيسةً", true), 3);
        /**
         * The Jordanian dinar: amounts are written with three digits (1000 fils), with the same words as
         * {@link #KWD}.
         */
        public static final Currency JOD = new Currency(
                new Noun("دينار", "ديناران", "دنانير", "دينارًا", false),
                new Noun("فلس", "فلسان", "فلوس", "فلسًا", false), 3);
        /** The Sudanese pound: 100 piastres, with the same words as {@link #EGP}. */
        public static final Currency SDG = new Currency(
                new Noun("جنيه", "جنيهان", "جنيهات", "جنيهًا", false),
                new Noun("قرش", "قرشان", "قروش", "قرشًا", false), 2);
        /** The pound sterling: 100 pence. */
        public static final Currency GBP = new Currency(
                new Noun("جنيه", "جنيهان", "جنيهات", "جنيهًا", false),
                new Noun("بنس", "بنسان", "بنسات", "بنسًا", false), 2);
        /** The Yemeni rial: 100 fils. */
        public static final Currency YER = new Currency(
                new Noun("ريال", "ريالان", "ريالات", "ريالًا", false),
                new Noun("فلس", "فلسان", "فلوس", "فلسًا", false), 2);
        /** The Moroccan dirham: 100 centimes. */
        public static final Currency MAD = new Currency(
                new Noun("درهم", "درهمان", "دراهم", "درهمًا", false),
                new Noun("سنتيم", "سنتيمان", "سنتيمات", "سنتيمًا", false), 2);
        /** The Iraqi dinar: 1000 fils. */
        public static final Currency IQD = new Currency(
                new Noun("دينار", "ديناران", "دنانير", "دينارًا", false),
                new Noun("فلس", "فلسان", "فلوس", "فلسًا", false), 3);
        /** The Libyan dinar: 1000 dirhams. */
        public static final Currency LYD = new Currency(
                new Noun("دينار", "ديناران", "دنانير", "دينارًا", false),
                new Noun("درهم", "درهمان", "دراهم", "درهمًا", false), 3);
        /** The Tunisian dinar: 1000 millimes. */
        public static final Currency TND = new Currency(
                new Noun("دينار", "ديناران", "دنانير", "دينارًا", false),
                new Noun("مليم", "مليمان", "مليمات", "مليمًا", false), 3);
        /** The Algerian dinar: 100 centimes. */
        public static final Currency DZD = new Currency(
                new Noun("دينار", "ديناران", "دنانير", "دينارًا", false),
                new Noun("سنتيم", "سنتيمان", "سنتيمات", "سنتيمًا", false), 2);
        /** The Syrian pound (lira): 100 piastres; the lira is feminine: «ثلاث ليرات», «إحدى عشرة ليرةً». */
        public static final Currency SYP = new Currency(
                new Noun("ليرة", "ليرتان", "ليرات", "ليرةً", true),
                new Noun("قرش", "قرشان", "قروش", "قرشًا", false), 2);
        /** The Lebanese pound (lira): 100 piastres, with the same words as {@link #SYP}. */
        public static final Currency LBP = new Currency(
                new Noun("ليرة", "ليرتان", "ليرات", "ليرةً", true),
                new Noun("قرش", "قرشان", "قروش", "قرشًا", false), 2);
        /** The Turkish lira: 100 kuruş («قرش»), with the same words as {@link #SYP}. */
        public static final Currency TRY = new Currency(
                new Noun("ليرة", "ليرتان", "ليرات", "ليرةً", true),
                new Noun("قرش", "قرشان", "قروش", "قرشًا", false), 2);
        /** The euro: 100 cents. «يورو» takes no tanween (no case ending): «أحد عشر يورو». */
        public static final Currency EUR = new Currency(
                new Noun("يورو", "يوروان", "يوروهات", "يورو", false),
                new Noun("سنت", "سنتان", "سنتات", "سنتًا", false), 2);

        private static final Map<String, Currency> BY_CODE = Map.ofEntries(
                Map.entry("EGP", EGP), Map.entry("SAR", SAR), Map.entry("AED", AED), Map.entry("KWD", KWD),
                Map.entry("USD", USD), Map.entry("QAR", QAR), Map.entry("BHD", BHD), Map.entry("OMR", OMR),
                Map.entry("JOD", JOD), Map.entry("SDG", SDG), Map.entry("GBP", GBP), Map.entry("YER", YER),
                Map.entry("MAD", MAD), Map.entry("IQD", IQD), Map.entry("LYD", LYD), Map.entry("TND", TND),
                Map.entry("DZD", DZD), Map.entry("SYP", SYP), Map.entry("LBP", LBP), Map.entry("TRY", TRY),
                Map.entry("EUR", EUR));

        /**
         * The ready-made currency for an ISO 4217 code ({@code "EGP"}, {@code "sar"}); empty for an unknown code or
         * {@code null}, so the program decides: skip the line, or define its own currency.
         */
        public static Optional<Currency> forCode(String isoCode) {
            return isoCode == null ? Optional.empty()
                    : Optional.ofNullable(BY_CODE.get(isoCode.strip().toUpperCase(Locale.ROOT)));
        }

        public Currency {
            Objects.requireNonNull(unit, "unit");
            if (fractionDigits < 0 || fractionDigits > 3) {
                throw new IllegalArgumentException("fractionDigits must be between 0 and 3: " + fractionDigits);
            }
            if (fractionDigits > 0 && fraction == null) {
                throw new IllegalArgumentException("A currency with fraction digits needs a fraction noun.");
            }
        }
    }

    /**
     * A currency in English, for the amount in English words: «Seven thousand two hundred Egyptian pounds only».
     * It is a separate type from {@link Currency}, so the two names can differ without changing the shape of
     * {@code Currency}.
     *
     * <pre>{@code
     * new EnglishCurrency("Libyan dinar", "Libyan dinars", "dirham", "dirhams", 3)
     * }</pre>
     *
     * @param unit           the unit in the singular: «Egyptian pound»
     * @param units          the unit in the plural: «Egyptian pounds»
     * @param fraction       the fraction in the singular: «piastre»; {@code null} if {@code fractionDigits} is zero
     * @param fractions      the fraction in the plural: «piastres» (or the same as the singular: «fils»)
     * @param fractionDigits the fraction digits: 2, or 3 for the fils and the baisa, or 0
     */
    public record EnglishCurrency(String unit, String units, String fraction, String fractions, int fractionDigits) {

        public static final EnglishCurrency EGP =
                new EnglishCurrency("Egyptian pound", "Egyptian pounds", "piastre", "piastres", 2);
        public static final EnglishCurrency SAR =
                new EnglishCurrency("Saudi riyal", "Saudi riyals", "halala", "halalas", 2);
        public static final EnglishCurrency AED =
                new EnglishCurrency("UAE dirham", "UAE dirhams", "fils", "fils", 2);
        public static final EnglishCurrency KWD =
                new EnglishCurrency("Kuwaiti dinar", "Kuwaiti dinars", "fils", "fils", 3);
        public static final EnglishCurrency USD =
                new EnglishCurrency("US dollar", "US dollars", "cent", "cents", 2);
        public static final EnglishCurrency QAR =
                new EnglishCurrency("Qatari riyal", "Qatari riyals", "dirham", "dirhams", 2);
        public static final EnglishCurrency BHD =
                new EnglishCurrency("Bahraini dinar", "Bahraini dinars", "fils", "fils", 3);
        public static final EnglishCurrency OMR =
                new EnglishCurrency("Omani rial", "Omani rials", "baisa", "baisa", 3);
        public static final EnglishCurrency JOD =
                new EnglishCurrency("Jordanian dinar", "Jordanian dinars", "fils", "fils", 3);
        public static final EnglishCurrency SDG =
                new EnglishCurrency("Sudanese pound", "Sudanese pounds", "piastre", "piastres", 2);
        public static final EnglishCurrency GBP =
                new EnglishCurrency("pound sterling", "pounds sterling", "penny", "pence", 2);
        public static final EnglishCurrency YER =
                new EnglishCurrency("Yemeni rial", "Yemeni rials", "fils", "fils", 2);
        public static final EnglishCurrency MAD =
                new EnglishCurrency("Moroccan dirham", "Moroccan dirhams", "centime", "centimes", 2);
        public static final EnglishCurrency IQD =
                new EnglishCurrency("Iraqi dinar", "Iraqi dinars", "fils", "fils", 3);
        public static final EnglishCurrency LYD =
                new EnglishCurrency("Libyan dinar", "Libyan dinars", "dirham", "dirhams", 3);
        public static final EnglishCurrency TND =
                new EnglishCurrency("Tunisian dinar", "Tunisian dinars", "millime", "millimes", 3);
        public static final EnglishCurrency DZD =
                new EnglishCurrency("Algerian dinar", "Algerian dinars", "centime", "centimes", 2);
        public static final EnglishCurrency SYP =
                new EnglishCurrency("Syrian pound", "Syrian pounds", "piastre", "piastres", 2);
        public static final EnglishCurrency LBP =
                new EnglishCurrency("Lebanese pound", "Lebanese pounds", "piastre", "piastres", 2);
        /** The Turkish lira: the English plural is «lira», the same as the singular, and the fraction is «kuruş». */
        public static final EnglishCurrency TRY =
                new EnglishCurrency("Turkish lira", "Turkish lira", "kuruş", "kuruş", 2);
        public static final EnglishCurrency EUR =
                new EnglishCurrency("euro", "euros", "cent", "cents", 2);

        private static final Map<String, EnglishCurrency> BY_CODE = Map.ofEntries(
                Map.entry("EGP", EGP), Map.entry("SAR", SAR), Map.entry("AED", AED), Map.entry("KWD", KWD),
                Map.entry("USD", USD), Map.entry("QAR", QAR), Map.entry("BHD", BHD), Map.entry("OMR", OMR),
                Map.entry("JOD", JOD), Map.entry("SDG", SDG), Map.entry("GBP", GBP), Map.entry("YER", YER),
                Map.entry("MAD", MAD), Map.entry("IQD", IQD), Map.entry("LYD", LYD), Map.entry("TND", TND),
                Map.entry("DZD", DZD), Map.entry("SYP", SYP), Map.entry("LBP", LBP), Map.entry("TRY", TRY),
                Map.entry("EUR", EUR));

        /** The ready-made currency for an ISO 4217 code; empty for an unknown code or {@code null}. */
        public static Optional<EnglishCurrency> forCode(String isoCode) {
            return isoCode == null ? Optional.empty()
                    : Optional.ofNullable(BY_CODE.get(isoCode.strip().toUpperCase(Locale.ROOT)));
        }

        public EnglishCurrency {
            if (unit == null || unit.isBlank() || units == null || units.isBlank()) {
                throw new IllegalArgumentException("The unit's singular and plural are required.");
            }
            if (fractionDigits < 0 || fractionDigits > 3) {
                throw new IllegalArgumentException("fractionDigits must be between 0 and 3: " + fractionDigits);
            }
            if (fractionDigits > 0 && (fraction == null || fraction.isBlank()
                    || fractions == null || fractions.isBlank())) {
                throw new IllegalArgumentException(
                        "A currency with fraction digits needs the fraction's singular and plural.");
            }
        }
    }

    /**
     * A negative amount: the words are for the absolute value, with {@code prefix} before them and {@code suffix}
     * after them, separated by a space. At least one word must be set, so a negative amount is not printed as if it
     * were positive. An amount that rounds to zero is written as «صفر» ("zero") without them.
     *
     * @param prefix before the whole text: «سالب», «Minus»; {@code null} or empty = none
     * @param suffix after the whole text: «(دائن)», «(credit)»; {@code null} or empty = none
     */
    public record Negative(String prefix, String suffix) {

        public Negative {
            prefix = prefix == null ? "" : prefix.strip();
            suffix = suffix == null ? "" : suffix.strip();
            if (prefix.isEmpty() && suffix.isEmpty()) {
                throw new IllegalArgumentException("A negative amount needs a prefix or a suffix, such as «(دائن)».");
            }
        }

        /** A word before the amount: {@code Negative.prefix("سالب")}. */
        public static Negative prefix(String prefix) {
            return new Negative(prefix, null);
        }

        /** A word after the amount: {@code Negative.suffix("(دائن)")}. */
        public static Negative suffix(String suffix) {
            return new Negative(null, suffix);
        }

        String around(String text) {
            return (prefix.isEmpty() ? "" : prefix + " ") + text + (suffix.isEmpty() ? "" : " " + suffix);
        }
    }

    private static final Noun THOUSAND = new Noun("ألف", "ألفان", "آلاف", "ألفًا", false);
    private static final Noun MILLION = new Noun("مليون", "مليونان", "ملايين", "مليونًا", false);
    private static final Noun BILLION = new Noun("مليار", "ملياران", "مليارات", "مليارًا", false);

    private static final long LIMIT = 1_000_000_000_000L;

    private static final String[] ONES_FOR_MASCULINE =
            {"", "واحد", "اثنان", "ثلاثة", "أربعة", "خمسة", "ستة", "سبعة", "ثمانية", "تسعة", "عشرة"};
    private static final String[] ONES_FOR_FEMININE =
            {"", "واحدة", "اثنتان", "ثلاث", "أربع", "خمس", "ست", "سبع", "ثماني", "تسع", "عشر"};
    private static final String[] TENS =
            {"", "عشرة", "عشرون", "ثلاثون", "أربعون", "خمسون", "ستون", "سبعون", "ثمانون", "تسعون"};
    private static final String[] HUNDREDS =
            {"", "مائة", "مائتان", "ثلاثمائة", "أربعمائة", "خمسمائة", "ستمائة", "سبعمائة", "ثمانمائة", "تسعمائة"};

    private static final String[] ENGLISH_ONES = {"", "one", "two", "three", "four", "five", "six", "seven", "eight",
            "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen",
            "nineteen"};
    private static final String[] ENGLISH_TENS =
            {"", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"};
    private static final String[] ENGLISH_SCALES = {"billion", "million", "thousand"};

    /** The amount as written on an invoice: «فقط ... لا غير» ("only ... nothing else"). */
    public static String arabic(BigDecimal amount, Currency currency) {
        return "فقط " + arabicWords(amount, currency) + " لا غير";
    }

    /**
     * Like {@link #arabic(BigDecimal, Currency)}, with the {@code negative} word around the whole text for a negative
     * amount.
     */
    public static String arabic(BigDecimal amount, Currency currency, Negative negative) {
        Objects.requireNonNull(currency, "currency");
        return signed(amount, currency.fractionDigits(), negative, value -> arabic(value, currency));
    }

    /** Like {@link #arabicWords(BigDecimal, Currency)}, with the {@code negative} word around a negative amount. */
    public static String arabicWords(BigDecimal amount, Currency currency, Negative negative) {
        Objects.requireNonNull(currency, "currency");
        return signed(amount, currency.fractionDigits(), negative, value -> arabicWords(value, currency));
    }

    /**
     * Like {@link #english(BigDecimal, EnglishCurrency)}, with the {@code negative} word around the whole text for a
     * negative amount, and the capital letter at the start of the whole text: «Minus one hundred US dollars only».
     */
    public static String english(BigDecimal amount, EnglishCurrency currency, Negative negative) {
        Objects.requireNonNull(currency, "currency");
        String text = signed(amount, currency.fractionDigits(), negative,
                value -> englishWords(value, currency) + " only");
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /**
     * Like {@link #englishWords(BigDecimal, EnglishCurrency)}, with the {@code negative} word around a negative amount.
     */
    public static String englishWords(BigDecimal amount, EnglishCurrency currency, Negative negative) {
        Objects.requireNonNull(currency, "currency");
        return signed(amount, currency.fractionDigits(), negative, value -> englishWords(value, currency));
    }

    /**
     * The words for the absolute value, with the word around them if the amount is negative and does not round to
     * zero.
     */
    private static String signed(BigDecimal amount, int fractionDigits, Negative negative,
                                 Function<BigDecimal, String> words) {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(negative, "negative");
        BigDecimal magnitude = amount.abs();
        String text = words.apply(magnitude);
        boolean zero = magnitude.setScale(fractionDigits, RoundingMode.HALF_UP).signum() == 0;
        return amount.signum() < 0 && !zero ? negative.around(text) : text;
    }

    /** The amount in words without «فقط» and «لا غير». */
    public static String arabicWords(BigDecimal amount, Currency currency) {
        Objects.requireNonNull(currency, "currency");
        Split split = split(amount, currency.fractionDigits());
        if (split.units() == 0 && split.fraction() == 0) {
            return "صفر " + currency.unit().singular();
        }
        List<String> parts = new ArrayList<>(2);
        if (split.units() > 0) {
            parts.add(whole(split.units(), currency.unit()));
        }
        if (split.fraction() > 0) {
            parts.add(counted(split.fraction(), currency.fraction(), false));
        }
        return String.join(" و", parts);
    }

    /**
     * The amount in English as written on an invoice: «Seven thousand two hundred Egyptian pounds only», and with
     * a fraction «One thousand two hundred thirty-four Egyptian pounds and fifty piastres only».
     */
    public static String english(BigDecimal amount, EnglishCurrency currency) {
        String words = englishWords(amount, currency);
        return Character.toUpperCase(words.charAt(0)) + words.substring(1) + " only";
    }

    /**
     * The amount in English without the leading capital letter or «only»: «seven thousand two hundred Egyptian pounds».
     * Numbers use the form common on invoices and cheques: «two hundred fifty», with «and» only before the fraction.
     */
    public static String englishWords(BigDecimal amount, EnglishCurrency currency) {
        Objects.requireNonNull(currency, "currency");
        Split split = split(amount, currency.fractionDigits());
        if (split.units() == 0 && split.fraction() == 0) {
            return "zero " + currency.units();
        }
        List<String> parts = new ArrayList<>(2);
        if (split.units() > 0) {
            parts.add(englishNumber(split.units()) + " " + (split.units() == 1 ? currency.unit() : currency.units()));
        }
        if (split.fraction() > 0) {
            parts.add(englishNumber(split.fraction()) + " "
                    + (split.fraction() == 1 ? currency.fraction() : currency.fractions()));
        }
        return String.join(" and ", parts);
    }

    /** The amount after rounding to the currency's digits: the units, and the fraction as a whole number. */
    private record Split(long units, int fraction) {}

    private static Split split(BigDecimal amount, int fractionDigits) {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("The amount must not be negative: " + amount
                    + ". Pass a Negative, such as Negative.suffix(\"(credit)\"), to write it with a word of its own.");
        }
        BigDecimal rounded = amount.setScale(fractionDigits, RoundingMode.HALF_UP);
        BigDecimal whole = rounded.setScale(0, RoundingMode.DOWN);
        if (whole.compareTo(BigDecimal.valueOf(LIMIT)) >= 0) {
            throw new IllegalArgumentException("Amounts of a trillion or more are not written in words: " + amount);
        }
        return new Split(whole.longValueExact(),
                rounded.subtract(whole).movePointRight(fractionDigits).intValueExact());
    }

    /** 1 up to below a thousand billion, in English: «one million two hundred fifty thousand». */
    private static String englishNumber(long number) {
        int[] groups = {
                (int) (number / 1_000_000_000L),
                (int) (number / 1_000_000L % 1000),
                (int) (number / 1000 % 1000),
                (int) (number % 1000)
        };
        List<String> parts = new ArrayList<>(4);
        for (int i = 0; i < groups.length; i++) {
            if (groups[i] > 0) {
                parts.add(englishBelowThousand(groups[i]) + (i < ENGLISH_SCALES.length ? " " + ENGLISH_SCALES[i] : ""));
            }
        }
        return String.join(" ", parts);
    }

    /** 1..999: «nine hundred ninety-nine». */
    private static String englishBelowThousand(int number) {
        int hundreds = number / 100;
        int rest = number % 100;
        String below = rest < 20 ? ENGLISH_ONES[rest]
                : ENGLISH_TENS[rest / 10] + (rest % 10 > 0 ? "-" + ENGLISH_ONES[rest % 10] : "");
        if (hundreds == 0) {
            return below;
        }
        return ENGLISH_ONES[hundreds] + " hundred" + (rest > 0 ? " " + below : "");
    }

    /** A whole number below a thousand billion, with its counted noun. */
    private static String whole(long number, Noun noun) {
        int[] groups = {
                (int) (number / 1_000_000_000L),
                (int) (number / 1_000_000L % 1000),
                (int) (number / 1000 % 1000),
                (int) (number % 1000)
        };
        Noun[] scales = {BILLION, MILLION, THOUSAND};
        int last = groups.length - 1;
        while (groups[last] == 0) {
            last--;
        }
        List<String> parts = new ArrayList<>(4);
        for (int i = 0; i < scales.length; i++) {
            if (groups[i] == 0) {
                continue;
            }
            if (i == last) {
                // آخر جزء: «ألف» مضافة للعملة، فالمعدود مفرد مجرور والمثنى من غير نون — «أحد عشر ألف جنيه».
                parts.add(counted(groups[i], scales[i], true) + " " + noun.singular());
            } else {
                parts.add(counted(groups[i], scales[i], false));
            }
        }
        if (groups[3] > 0) {
            parts.add(counted(groups[3], noun, false));
        }
        return String.join(" و", parts);
    }

    /**
     * A number from 1 to 999 with its counted noun.
     *
     * @param construct the counted noun is in a construct with a noun after it (thousand before the currency): the
     *                  singular instead of the accusative, and the dual without the final «ن»
     */
    private static String counted(int number, Noun noun, boolean construct) {
        boolean scale = noun == THOUSAND || noun == MILLION || noun == BILLION;
        if (number == 1) {
            return scale ? noun.singular() : noun.singular() + " " + (noun.feminine() ? "واحدة" : "واحد");
        }
        if (number == 2) {
            return construct ? noun.dualInConstruct() : noun.dual();
        }
        int rest = number % 100;
        String words = belowThousand(number, noun.feminine());
        if (rest == 0 || rest == 1 || rest == 2) {
            return words + " " + noun.singular();
        }
        if (rest <= 10) {
            return words + " " + noun.plural();
        }
        return words + " " + (construct ? noun.singular() : noun.accusative());
    }

    /** 3..999 in words; hundreds in a construct with their noun ({@code rest == 0}): «مائتا». */
    private static String belowThousand(int number, boolean feminine) {
        int hundreds = number / 100;
        int rest = number % 100;
        if (hundreds == 0) {
            return belowHundred(rest, feminine);
        }
        if (rest == 0) {
            return hundreds == 2 ? "مائتا" : HUNDREDS[hundreds];
        }
        return HUNDREDS[hundreds] + " و" + belowHundred(rest, feminine);
    }

    /**
     * 1..99 in words, by the gender of the counted noun: 3..10 take the opposite gender, while 1, 2 and «عشر» in
     * compound numbers take the same gender.
     */
    private static String belowHundred(int number, boolean feminine) {
        String[] ones = feminine ? ONES_FOR_FEMININE : ONES_FOR_MASCULINE;
        if (number <= 10) {
            return ones[number];
        }
        if (number == 11) {
            return feminine ? "إحدى عشرة" : "أحد عشر";
        }
        if (number == 12) {
            return feminine ? "اثنتا عشرة" : "اثنا عشر";
        }
        if (number < 20) {
            return ones[number - 10] + (feminine ? " عشرة" : " عشر");
        }
        int unit = number % 10;
        String tens = TENS[number / 10];
        if (unit == 0) {
            return tens;
        }
        String first = unit == 1 ? (feminine ? "إحدى" : "واحد") : ones[unit];
        return first + " و" + tens;
    }
}
