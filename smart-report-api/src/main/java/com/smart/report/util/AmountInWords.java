package com.smart.report.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * التفقيط: المبلغ مكتوبًا بالحروف العربية كما في الفواتير والإيصالات.
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
 * <p><b>القواعد:</b> المعدود بعد العدد بحالة الرفع، زي ما بيتكتب بعد «فقط»:</p>
 * <ul>
 *     <li>1 و 2 بصيغة المعدود نفسه: «جنيه واحد»، «جنيهان»، «ألف»، «ألفان».</li>
 *     <li>3 إلى 10 بالجمع، والعدد بعكس جنس المعدود: «ثلاثة جنيهات»، «ثلاث هللات».</li>
 *     <li>11 إلى 99 بالمفرد المنصوب: «أحد عشر جنيهًا»، «إحدى عشرة هللةً»، «خمسون قرشًا».</li>
 *     <li>المئة والألف ومضاعفاتهم بالمفرد المجرور، والمثنى بيتضاف فيفقد النون: «مائة جنيه»،
 *         «مائتا جنيه»، «ألفا جنيه»، «أحد عشر ألف جنيه».</li>
 *     <li>المئات اللي بعدها واحد أو اثنان بالشكل المعتاد في الفواتير: «مائة وواحد جنيه».</li>
 * </ul>
 *
 * <p>«مائة» بالألف زي ما بتتكتب في الفواتير المصرية. المبلغ بيتقرّب لخانات العملة (النص لفوق)، والسالب
 * أو اللي من ألف مليار وأكتر بيرمي {@link IllegalArgumentException}. الصنف بلا حالة وآمن بين الـ threads.</p>
 *
 * <p><b>بالإنجليزي</b> للفواتير اللي بلغتين، بعملة من {@link EnglishCurrency}:</p>
 *
 * <pre>{@code
 * AmountInWords.english(new BigDecimal("7200"), AmountInWords.EnglishCurrency.EGP)
 *     // Seven thousand two hundred Egyptian pounds only
 * AmountInWords.english(new BigDecimal("7200.50"), AmountInWords.EnglishCurrency.EGP)
 *     // Seven thousand two hundred Egyptian pounds and fifty piastres only
 * }</pre>
 *
 * <p>العملات الجاهزة بالاسمين: EGP وSAR وAED وKWD وUSD وQAR وBHD وOMR وJOD. خانات الكسر زي ISO 4217:
 * 3 للدينار الكويتي والبحريني والأردني والريال العماني، و2 للباقي.</p>
 */
public final class AmountInWords {

    private AmountInWords() {}

    /**
     * اسم معدود بصيغه الأربع.
     *
     * @param singular   المفرد: «جنيه»
     * @param dual       المثنى المرفوع: «جنيهان»
     * @param plural     الجمع بعد 3..10: «جنيهات»
     * @param accusative المفرد المنصوب بعد 11..99: «جنيهًا»
     * @param feminine   مؤنث («هللة»): بيغيّر صيغة العدد
     */
    public record Noun(String singular, String dual, String plural, String accusative, boolean feminine) {

        public Noun {
            for (String form : new String[]{singular, dual, plural, accusative}) {
                if (form == null || form.isBlank()) {
                    throw new IllegalArgumentException("Every form of the noun is required.");
                }
            }
        }

        /** المثنى مضافًا: «مائتا»، «ألفا جنيه» — من غير النون. */
        String dualInConstruct() {
            return dual.endsWith("ان") ? dual.substring(0, dual.length() - 1) : dual;
        }
    }

    /**
     * عملة: الوحدة، وكسرها، وعدد خانات الكسر.
     *
     * @param unit           «جنيه»
     * @param fraction       «قرش»؛ {@code null} لو {@code fractionDigits} صفر
     * @param fractionDigits 2 للقرش والهللة، 3 للفلس الكويتي، 0 بلا كسر
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
        /** الريال القطري: 100 درهم. */
        public static final Currency QAR = new Currency(
                new Noun("ريال", "ريالان", "ريالات", "ريالًا", false),
                new Noun("درهم", "درهمان", "دراهم", "درهمًا", false), 2);
        /** الدينار البحريني: 1000 فلس، بنفس كلمات {@link #KWD}. */
        public static final Currency BHD = new Currency(
                new Noun("دينار", "ديناران", "دنانير", "دينارًا", false),
                new Noun("فلس", "فلسان", "فلوس", "فلسًا", false), 3);
        /** الريال العماني: 1000 بيسة، والبيسة مؤنثة: «ثلاث بيسات»، «إحدى عشرة بيسةً». */
        public static final Currency OMR = new Currency(
                new Noun("ريال", "ريالان", "ريالات", "ريالًا", false),
                new Noun("بيسة", "بيستان", "بيسات", "بيسةً", true), 3);
        /** الدينار الأردني: المبالغ بتتكتب بثلاث خانات (1000 فلس)، بنفس كلمات {@link #KWD}. */
        public static final Currency JOD = new Currency(
                new Noun("دينار", "ديناران", "دنانير", "دينارًا", false),
                new Noun("فلس", "فلسان", "فلوس", "فلسًا", false), 3);

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
     * عملة بالإنجليزي، للمبلغ بالحروف الإنجليزية: «Seven thousand two hundred Egyptian pounds only». نوع منفصل عن
     * {@link Currency} عشان الاسمين بيختلفوا من غير ما يتغيّر شكل {@code Currency}.
     *
     * <pre>{@code
     * new EnglishCurrency("Libyan dinar", "Libyan dinars", "dirham", "dirhams", 3)
     * }</pre>
     *
     * @param unit           الوحدة مفردة: «Egyptian pound»
     * @param units          الوحدة جمعًا: «Egyptian pounds»
     * @param fraction       الكسر مفردًا: «piastre»؛ {@code null} لو {@code fractionDigits} صفر
     * @param fractions      الكسر جمعًا: «piastres» (أو نفس المفرد: «fils»)
     * @param fractionDigits خانات الكسر: 2، أو 3 للفلس والبيسة، أو 0
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

    /** المبلغ كما يُكتب في الفاتورة: «فقط ... لا غير». */
    public static String arabic(BigDecimal amount, Currency currency) {
        return "فقط " + arabicWords(amount, currency) + " لا غير";
    }

    /** المبلغ بالحروف من غير «فقط» و«لا غير». */
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
     * المبلغ بالإنجليزي كما يُكتب في الفاتورة: «Seven thousand two hundred Egyptian pounds only»، ومع الكسر
     * «One thousand two hundred thirty-four Egyptian pounds and fifty piastres only».
     */
    public static String english(BigDecimal amount, EnglishCurrency currency) {
        String words = englishWords(amount, currency);
        return Character.toUpperCase(words.charAt(0)) + words.substring(1) + " only";
    }

    /**
     * المبلغ بالإنجليزي من غير الحرف الكبير في أوله ولا «only»: «seven thousand two hundred Egyptian pounds».
     * الأعداد بالشكل المعتاد في الفواتير والشيكات: «two hundred fifty»، و«and» بس قبل الكسر.
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

    /** المبلغ بعد التقريب لخانات العملة: الوحدات، والكسر بعدد صحيح. */
    private record Split(long units, int fraction) {}

    private static Split split(BigDecimal amount, int fractionDigits) {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("The amount must not be negative: " + amount
                    + ". Write its absolute value and say in the label whether it is owed.");
        }
        BigDecimal rounded = amount.setScale(fractionDigits, RoundingMode.HALF_UP);
        BigDecimal whole = rounded.setScale(0, RoundingMode.DOWN);
        if (whole.compareTo(BigDecimal.valueOf(LIMIT)) >= 0) {
            throw new IllegalArgumentException("Amounts of a trillion or more are not written in words: " + amount);
        }
        return new Split(whole.longValueExact(),
                rounded.subtract(whole).movePointRight(fractionDigits).intValueExact());
    }

    /** 1 إلى أقل من ألف مليار بالإنجليزي: «one million two hundred fifty thousand». */
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

    /** عدد صحيح أقل من ألف مليار ومعه معدوده. */
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
     * عدد من 1 إلى 999 ومعدوده.
     *
     * @param construct المعدود مضاف لاسم بعده (ألف قبل العملة): المفرد بدل المنصوب، والمثنى من غير نون
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

    /** 3..999 بالحروف؛ المئات المضافة لمعدودها ({@code rest == 0}): «مائتا». */
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

    /** 1..99 بالحروف بجنس المعدود: 3..10 بعكسه، و1 و2 و«عشر» في المركّب بنفسه. */
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
