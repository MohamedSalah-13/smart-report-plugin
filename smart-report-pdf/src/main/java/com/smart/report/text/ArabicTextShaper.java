package com.smart.report.text;

import com.smart.report.util.TextDirection;

import java.text.Bidi;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * يحوّل نصًا عربيًا مخزَّنًا بالترتيب المنطقي إلى الصورة الجاهزة للرسم في PDF:
 * <ol>
 *     <li><b>التشكيل (shaping):</b> استبدال كل حرف بصورته السياقية (منفصلة / أولية / وسطية / نهائية)
 *         من كتلة Arabic Presentation Forms-B، مع دمج لام-ألف في حرف واحد.</li>
 *     <li><b>ترتيب العرض (bidi):</b> إعادة ترتيب المقاطع من اليمين لليسار عبر {@link Bidi} من الـ JDK،
 *         مع عكس المقاطع العربية ومرآة الأقواس.</li>
 * </ol>
 *
 * <p>الحاجة لهذا الصنف أن PDFBox يرسم الأحرف كما تُسلَّم له بالضبط ومن اليسار لليمين، فبدون الخطوتين
 * دول العربي بيطلع حروفًا مقطّعة ومقلوبة حتى لو الخط بيحتوي على الحروف.</p>
 *
 * <p>{@link #forDisplay(String)} بيرتّب نصًا واحدًا باتجاهه هو. {@link #forDisplay(String, String, boolean)}
 * هو اللي المستندات بتستخدمه: سطر من فقرة، في صفحة ليها اتجاه، مع عزل الأرقام والتواريخ عشان
 * تتقرا زي ما اتكتبت.</p>
 *
 * <p>الصنف بلا حالة (stateless) وآمن للاستخدام من أكثر من thread.</p>
 */
public final class ArabicTextShaper {

    private ArabicTextShaper() {}

    private static final char ARABIC_START = 'ء'; // ء
    private static final char ARABIC_END = 'ي';   // ي
    private static final char TATWEEL = 'ـ';      // ـ
    private static final char LAM = 'ل';          // ل

    /**
     * لكل حرف من {@code ء}..{@code ي}: أول صورة له في كتلة Presentation Forms-B.
     * الصور مرتّبة داخل الكتلة هكذا: منفصلة، نهائية، ثم (أولية، وسطية) للحروف ثنائية الاتصال.
     * القيمة 0 تعني موضعًا غير مستخدم في اليونيكود.
     */
    private static final char[] FORM_BASE = {
            /* 0621 ء */ 'ﺀ', /* 0622 آ */ 'ﺁ', /* 0623 أ */ 'ﺃ', /* 0624 ؤ */ 'ﺅ',
            /* 0625 إ */ 'ﺇ', /* 0626 ئ */ 'ﺉ', /* 0627 ا */ 'ﺍ', /* 0628 ب */ 'ﺏ',
            /* 0629 ة */ 'ﺓ', /* 062A ت */ 'ﺕ', /* 062B ث */ 'ﺙ', /* 062C ج */ 'ﺝ',
            /* 062D ح */ 'ﺡ', /* 062E خ */ 'ﺥ', /* 062F د */ 'ﺩ', /* 0630 ذ */ 'ﺫ',
            /* 0631 ر */ 'ﺭ', /* 0632 ز */ 'ﺯ', /* 0633 س */ 'ﺱ', /* 0634 ش */ 'ﺵ',
            /* 0635 ص */ 'ﺹ', /* 0636 ض */ 'ﺽ', /* 0637 ط */ 'ﻁ', /* 0638 ظ */ 'ﻅ',
            /* 0639 ع */ 'ﻉ', /* 063A غ */ 'ﻍ',
            /* 063B..063F غير مستخدمة */ 0, 0, 0, 0, 0,
            /* 0640 ـ  */ TATWEEL,
            /* 0641 ف */ 'ﻑ', /* 0642 ق */ 'ﻕ', /* 0643 ك */ 'ﻙ', /* 0644 ل */ 'ﻝ',
            /* 0645 م */ 'ﻡ', /* 0646 ن */ 'ﻥ', /* 0647 ه */ 'ﻩ', /* 0648 و */ 'ﻭ',
            /* 0649 ى */ 'ﻯ', /* 064A ي */ 'ﻱ'
    };

    /** عدد الصور لكل حرف: 4 = ثنائي الاتصال، 2 = يتصل بما قبله فقط، 1 = منفصل دائمًا، 0 = تطويل/غير مستخدم. */
    private static final byte[] FORM_COUNT = {
            /* ء */ 1, /* آ */ 2, /* أ */ 2, /* ؤ */ 2,
            /* إ */ 2, /* ئ */ 4, /* ا */ 2, /* ب */ 4,
            /* ة */ 2, /* ت */ 4, /* ث */ 4, /* ج */ 4,
            /* ح */ 4, /* خ */ 4, /* د */ 2, /* ذ */ 2,
            /* ر */ 2, /* ز */ 2, /* س */ 4, /* ش */ 4,
            /* ص */ 4, /* ض */ 4, /* ط */ 4, /* ظ */ 4,
            /* ع */ 4, /* غ */ 4,
            0, 0, 0, 0, 0,
            /* ـ */ 0,
            /* ف */ 4, /* ق */ 4, /* ك */ 4, /* ل */ 4,
            /* م */ 4, /* ن */ 4, /* ه */ 4, /* و */ 2,
            /* ى */ 2, /* ي */ 4
    };

    /**
     * حروف خارج {@code ء..ي} بتتكتب في أسماء وكلمات عربية (ڤيلا، چيهان، جوجل بـ گ) وفي الفارسية والأردية.
     * صورها في كتلة Presentation Forms-A، بنفس الترتيب: منفصلة، نهائية، أولية، وسطية. من غيرها الحرف
     * كان بيترسم منفصلًا ويقطع الكلمة.
     */
    private static final String EXTRA_LETTERS =
            "ٱٹپچڈڑژڤکگھہیے";
    private static final char[] EXTRA_BASE = {
            /* ٱ */ 'ﭐ', /* ٹ */ 'ﭦ', /* پ */ 'ﭖ', /* چ */ 'ﭺ', /* ڈ */ 'ﮈ',
            /* ڑ */ 'ﮌ', /* ژ */ 'ﮊ', /* ڤ */ 'ﭪ', /* ک */ 'ﮎ', /* گ */ 'ﮒ',
            /* ھ */ 'ﮪ', /* ہ */ 'ﮦ', /* ی */ 'ﯼ', /* ے */ 'ﮮ'
    };
    private static final byte[] EXTRA_COUNT = {
            /* ٱ */ 2, /* ٹ */ 4, /* پ */ 4, /* چ */ 4, /* ڈ */ 2,
            /* ڑ */ 2, /* ژ */ 2, /* ڤ */ 4, /* ک */ 4, /* گ */ 4,
            /* ھ */ 4, /* ہ */ 4, /* ی */ 4, /* ے */ 2
    };

    private static final int ISOLATED = 0;
    private static final int FINAL = 1;
    private static final int INITIAL = 2;
    private static final int MEDIAL = 3;

    /**
     * رقم بإشارته وفواصله: مبلغ، تاريخ، وقت، نسبة. لازم ينتهي برقم أو علامة نسبة، فالنقطة اللي في
     * آخر الجملة تفضل للجملة. ومش ملاصق لحرف لاتيني: أرقام كود زي "NC7013" جزء من كلمة لاتينية
     * الـ bidi بيسيبها سليمة، وعزلها لوحدها كان بيطبعها "7013NC".
     */
    private static final Pattern NUMBER =
            Pattern.compile("(?<![A-Za-z\\d])[-+]?\\d(?:[\\d,.:/\\-]*\\d)?%?(?![A-Za-z\\d])");

    /**
     * رقم تليفون مكتوب مجموعات بينها مسافات أو شرطات ("0100 000 0000"، "+20 100 000 0000"): بيبدأ
     * بصفر أو +، مجموعتين على الأقل، وسبعة أرقام على الأقل. بقواعد Unicode كل مجموعة رقم لوحده،
     * فالسطر العربي بيرصّها من اليمين لليسار والتليفون يتقرا "0000 000 0100"؛ هنا بيتعزل كله قطعة واحدة.
     */
    private static final Pattern PHONE = Pattern.compile(
            "(?<![A-Za-z\\d+])(?=[+0])(?=\\+?(?:\\d[ -]?){7,})\\+?\\d+(?:[ -]\\d+)+(?![A-Za-z\\d])");

    /** شرطة بين رقمين. بعد حرف عربي كل رقم بيتعزل لوحده ("مقاس 20-24-28" قائمة بتتقرا باتجاه السطر). */
    private static final Pattern HYPHEN_BETWEEN_DIGITS = Pattern.compile("(?<=\\d)-(?=\\d)");

    /** تاريخ حقيقي (سنة-شهر أو سنة-شهر-يوم) بيفضل قطعة واحدة حتى بعد كلمة عربية: "من 2025-10-01". */
    private static final Pattern DATE =
            Pattern.compile("(?:19|20)\\d{2}-(?:0[1-9]|1[0-2])(?:-(?:0[1-9]|[12]\\d|3[01]))?");

    /** بداية مقطع لاتيني: حرف لاتيني مش مسبوق بحرف لاتيني أو رقم. */
    private static final Pattern LATIN_RUN_START = Pattern.compile("(?<![A-Za-z\\d])[A-Za-z]");

    // مكتوبين كـ escapes: الاتنين غير مرئيين، والحرف الحرفي بيضيع مع أول تعديل.
    private static final char LEFT_TO_RIGHT_ISOLATE = '\u2066';
    private static final char POP_DIRECTIONAL_ISOLATE = '\u2069';

    /**
     * سطر واحد من فقرة، جاهزًا للرسم في صفحة اتجاهها {@code rightToLeft}.
     *
     * <p><b>الأرقام بتتعزل قبل الـ bidi، وده الجزء السهل كسره.</b> بقواعد Unicode، السالب اللي مفيش
     * جنبه عربي بياخد اتجاه الفقرة ويروح آخر الرقم ({@code 11,995.00-})، والتاريخ بعد كلمة عربية
     * بيتقلب ({@code 01-10-2025})، وعلامة النسبة بتسبق رقمها ({@code %113.44}). كل رقم وتاريخ
     * ووقت ونسبة وكل مقطع لاتيني بيتحط في عزل من اليسار لليمين (LRI ... PDI) وعلامات العزل
     * بتتشال من الناتج، فيتقرا زي ما اتكتب.</p>
     *
     * <p><b>النص اللي بيلتف لازم يتكسر لسطوره قبل ما ييجي هنا، مش بعد.</b> الناتج بترتيب الرسم؛
     * لو اتكسر بعدها، آخر الجملة يطلع في السطر الأول. عشان كده الاتجاه بييجي من {@code paragraph}
     * (النص كله): السطر التاني اللي بالصدفة بيبدأ بكلمة لاتينية ما يتقلبش لوحده.</p>
     *
     * @param line        السطر المطلوب رسمه، بالترتيب المنطقي
     * @param paragraph   النص الكامل اللي السطر مقطوع منه، وهو اللي بيحدد الاتجاه؛ {@code null} = السطر نفسه
     * @param rightToLeft اتجاه الصفحة، وبيحسم فقط النص اللي مفيهوش حروف (تاريخ ووقت، كود أرقام)
     * @return السطر بترتيب العرض من اليسار لليمين، بلا أي علامات تحكم في الاتجاه
     */
    public static String forDisplay(String line, String paragraph, boolean rightToLeft) {
        if (line == null || line.isEmpty()) {
            return "";
        }
        String shaped = shape(isolateNumbers(line));
        boolean rtl = paragraphIsRightToLeft(paragraph == null ? line : paragraph, rightToLeft);
        return stripFormatChars(reorder(shaped, rtl ? Bidi.DIRECTION_RIGHT_TO_LEFT : Bidi.DIRECTION_LEFT_TO_RIGHT));
    }

    /** اتجاه أول حرف قوي في النص؛ النص اللي مفيهوش حروف بياخد اتجاه الصفحة. */
    private static boolean paragraphIsRightToLeft(String text, boolean whenNeutral) {
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            byte direction = Character.getDirectionality(codePoint);
            if (direction == Character.DIRECTIONALITY_LEFT_TO_RIGHT) {
                return false;
            }
            if (direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                    || direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) {
                return true;
            }
        }
        return whenNeutral;
    }

    /** يحط كل مقطع لاتيني وكل رقم خارجه في عزل من اليسار لليمين، فالـ bidi يحرّك كل واحد قطعة واحدة. */
    static String isolateNumbers(String text) {
        StringBuilder out = new StringBuilder(text.length() + 8);
        Matcher start = LATIN_RUN_START.matcher(text);
        int from = 0;
        while (from < text.length() && start.find(from)) {
            int end = endOfLatinRun(text, start.start());
            isolateEachNumber(text, from, start.start(), out);
            isolate(text.substring(start.start(), end), out);
            from = end;
        }
        isolateEachNumber(text, from, text.length(), out);
        return out.toString();
    }

    /**
     * نهاية المقطع اللاتيني اللي بيبدأ عند {@code start}: آخر حرف أو رقم أو علامة نسبة قبل أول حرف
     * من اليمين لليسار. بعد حرف لاتيني الأرقام اللي بعده لاتينية الاتجاه برضه، فـ "Pepsi 330"
     * قطعة واحدة؛ المسافات وعلامات الترقيم اللي بعدها بتاعة السطر العربي.
     */
    private static int endOfLatinRun(String text, int start) {
        int end = start + 1;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            byte direction = Character.getDirectionality(c);
            if (direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                    || direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
                    || direction == Character.DIRECTIONALITY_ARABIC_NUMBER) {
                break;
            }
            if (direction == Character.DIRECTIONALITY_LEFT_TO_RIGHT
                    || direction == Character.DIRECTIONALITY_EUROPEAN_NUMBER || c == '%') {
                end = i + 1;
            }
        }
        return end;
    }

    /** الأرقام اللي بين مقطعين لاتينيين، كل واحد معزول. */
    private static void isolateEachNumber(String text, int start, int end, StringBuilder out) {
        Matcher number = NUMBER.matcher(text).region(start, end).useTransparentBounds(true);
        Matcher phone = PHONE.matcher(text).region(start, end).useTransparentBounds(true);
        boolean phoneAhead = phone.find();
        int last = start;
        while (number.find()) {
            if (number.start() < last) {
                continue; // مجموعة جوّه تليفون اتعزل بالفعل
            }
            while (phoneAhead && phone.start() < number.start()) {
                phoneAhead = phone.find();
            }
            out.append(text, last, number.start());
            // التليفون بيبدأ من مكان أول مجموعة فيه، وبياخد مجموعاته كلها قطعة واحدة.
            if (phoneAhead && phone.start() == number.start()) {
                isolate(phone.group(), out);
                last = phone.end();
                continue;
            }
            String found = number.group();
            if (arabicLetterBefore(text, number.start()) && !DATE.matcher(found).matches()) {
                Matcher hyphen = HYPHEN_BETWEEN_DIGITS.matcher(found);
                int piece = 0;
                while (hyphen.find()) {
                    isolate(found.substring(piece, hyphen.start()), out);
                    out.append('-');
                    piece = hyphen.end();
                }
                isolate(found.substring(piece), out);
            } else {
                isolate(found, out);
            }
            last = number.end();
        }
        out.append(text, last, end);
    }

    private static void isolate(String piece, StringBuilder out) {
        out.append(LEFT_TO_RIGHT_ISOLATE).append(piece).append(POP_DIRECTIONAL_ISOLATE);
    }

    /** هل أول حرف قبل {@code index} عربي؟ ده الشرط اللي بيخلي الـ bidi يعتبر الأرقام اللي بعده "أرقامًا عربية". */
    private static boolean arabicLetterBefore(String text, int index) {
        for (int i = index - 1; i >= 0; i--) {
            byte direction = Character.getDirectionality(text.charAt(i));
            if (direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) {
                return true;
            }
            if (direction == Character.DIRECTIONALITY_LEFT_TO_RIGHT
                    || direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT) {
                return false;
            }
        }
        return false;
    }

    /** علامات التحكم في الاتجاه خلصت شغلها بعد الـ bidi ومش موجودة في أغلب الخطوط. */
    private static String stripFormatChars(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.getType(c) != Character.FORMAT) {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** يحوّل النص للصورة النهائية الجاهزة للرسم: تشكيل ثم ترتيب عرض. */
    public static String forDisplay(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return toVisualOrder(shape(text));
    }

    /** يستبدل الحروف العربية بصورها السياقية، مع إبقاء باقي النص كما هو. */
    public static String shape(String text) {
        if (text == null || text.isEmpty() || !containsArabic(text)) {
            return text;
        }

        StringBuilder out = new StringBuilder(text.length());
        int length = text.length();
        int i = 0;

        while (i < length) {
            char c = text.charAt(i);

            if (formBase(c) == 0) { // علامة تشكيل أو حرف غير عربي: يمرّ كما هو
                out.append(c);
                i++;
                continue;
            }

            int previous = previousBase(text, i);
            boolean joinsPrevious = previous >= 0 && joinsForward(text.charAt(previous));
            int next = nextBase(text, i);

            // لام + ألف: دمج إجباري في حرف واحد قبل أي تشكيل عادي
            if (c == LAM && next >= 0) {
                char ligature = lamAlefLigature(text.charAt(next));
                if (ligature != 0) {
                    out.append((char) (ligature + (joinsPrevious ? 1 : 0)));
                    for (int m = i + 1; m < next; m++) { // علامات تشكيل واقعة بين اللام والألف
                        out.append(text.charAt(m));
                    }
                    i = next + 1;
                    continue;
                }
            }

            boolean joinsNext = joinsForward(c) && next >= 0 && joinsBackward(text.charAt(next));
            out.append(selectForm(c, joinsPrevious, joinsNext));
            i++;
        }
        return out.toString();
    }

    /**
     * يعيد ترتيب النص من الترتيب المنطقي لترتيب العرض (من اليسار لليمين كما سيُرسم)،
     * فيعكس المقاطع العربية ويحافظ على المقاطع اللاتينية والأرقام في اتجاهها الطبيعي.
     */
    public static String toVisualOrder(String text) {
        return reorder(text, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
    }

    /** ترتيب العرض باتجاه فقرة محدد ({@code Bidi.DIRECTION_*}). */
    private static String reorder(String text, int direction) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Bidi bidi = new Bidi(text, direction);
        if (bidi.isLeftToRight()) {
            return text;
        }

        int runCount = bidi.getRunCount();
        Run[] runs = new Run[runCount];
        byte[] levels = new byte[runCount];
        for (int i = 0; i < runCount; i++) {
            byte level = (byte) bidi.getRunLevel(i);
            runs[i] = new Run(text.substring(bidi.getRunStart(i), bidi.getRunLimit(i)), level);
            levels[i] = level;
        }
        Bidi.reorderVisually(levels, 0, runs, 0, runCount);

        StringBuilder out = new StringBuilder(text.length());
        for (Run run : runs) {
            out.append(run.rightToLeft() ? reverseClusters(mirror(run.text())) : run.text());
        }
        return out.toString();
    }

    /** هل النص يحتوي على أي حرف من كتل العربية (بما فيها صور العرض)؟ */
    public static boolean containsArabic(String text) {
        return TextDirection.containsArabic(text);
    }

    private static char selectForm(char c, boolean joinsPrevious, boolean joinsNext) {
        char base = formBase(c);
        int count = formCount(c);
        if (count == 4) {
            if (joinsPrevious && joinsNext) return (char) (base + MEDIAL);
            if (joinsPrevious) return (char) (base + FINAL);
            if (joinsNext) return (char) (base + INITIAL);
            return (char) (base + ISOLATED);
        }
        if (count == 2) {
            return (char) (base + (joinsPrevious ? FINAL : ISOLATED));
        }
        return base; // الهمزة (منفصلة دائمًا) أو التطويل
    }

    /** أول صورة لدمج لام-ألف، أو 0 لو الحرف التالي ليس ألفًا. */
    private static char lamAlefLigature(char alef) {
        return switch (alef) {
            case 'آ' -> 'ﻵ'; // لآ
            case 'أ' -> 'ﻷ'; // لأ
            case 'إ' -> 'ﻹ'; // لإ
            case 'ا' -> 'ﻻ'; // لا
            default -> 0;
        };
    }

    /** هل يستطيع الحرف الاتصال بما بعده (ثنائي الاتصال أو تطويل)؟ */
    private static boolean joinsForward(char c) {
        return c == TATWEEL || formCount(c) == 4;
    }

    /** هل يستطيع الحرف الاتصال بما قبله (له صورة نهائية أو تطويل)؟ */
    private static boolean joinsBackward(char c) {
        return c == TATWEEL || formCount(c) >= 2;
    }

    /** أول صورة عرض للحرف، أو 0 لو ليس حرفًا عربيًا قابلًا للتشكيل. التطويل بيرجع نفسه. */
    private static char formBase(char c) {
        if (c >= ARABIC_START && c <= ARABIC_END) {
            return FORM_BASE[c - ARABIC_START];
        }
        int extra = EXTRA_LETTERS.indexOf(c);
        return extra < 0 ? 0 : EXTRA_BASE[extra];
    }

    /** عدد صور الحرف: 4 ثنائي الاتصال، 2 يتصل بما قبله فقط، 1 منفصل دائمًا، 0 تطويل أو مش حرف. */
    private static int formCount(char c) {
        if (c >= ARABIC_START && c <= ARABIC_END) {
            return FORM_COUNT[c - ARABIC_START];
        }
        int extra = EXTRA_LETTERS.indexOf(c);
        return extra < 0 ? 0 : EXTRA_COUNT[extra];
    }

    /** علامات التشكيل شفّافة: لا تكسر الاتصال بين الحرفين المحيطين بها. */
    private static boolean isTransparent(char c) {
        return Character.getType(c) == Character.NON_SPACING_MARK;
    }

    private static int previousBase(String text, int from) {
        for (int i = from - 1; i >= 0; i--) {
            if (!isTransparent(text.charAt(i))) return i;
        }
        return -1;
    }

    private static int nextBase(String text, int from) {
        for (int i = from + 1; i < text.length(); i++) {
            if (!isTransparent(text.charAt(i))) return i;
        }
        return -1;
    }

    /**
     * يعكس النص مع إبقاء كل علامة تشكيل ملتصقة بحرفها الأساسي،
     * فلا تنفصل الحركات عن حروفها عند العكس.
     */
    private static String reverseClusters(String text) {
        List<String> clusters = new ArrayList<>(text.length());
        int i = 0;
        while (i < text.length()) {
            int start = i++;
            while (i < text.length() && isTransparent(text.charAt(i))) {
                i++;
            }
            clusters.add(text.substring(start, i));
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int c = clusters.size() - 1; c >= 0; c--) {
            out.append(clusters.get(c));
        }
        return out.toString();
    }

    /** الأقواس وما شابهها تنقلب اتجاهًا داخل المقاطع من اليمين لليسار. */
    private static String mirror(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            out.append(switch (c) {
                case '(' -> ')';
                case ')' -> '(';
                case '[' -> ']';
                case ']' -> '[';
                case '{' -> '}';
                case '}' -> '{';
                case '<' -> '>';
                case '>' -> '<';
                case '«' -> '»'; // «  »
                case '»' -> '«';
                default -> c;
            });
        }
        return out.toString();
    }

    private record Run(String text, byte level) {
        boolean rightToLeft() {
            return (level & 1) == 1;
        }
    }
}
