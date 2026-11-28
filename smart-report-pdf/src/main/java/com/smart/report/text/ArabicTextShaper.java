package com.smart.report.text;

import com.smart.report.util.TextDirection;

import java.text.Bidi;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns Arabic text stored in logical order into the form that is ready to draw in a PDF:
 * <ol>
 *     <li><b>Shaping:</b> replaces each letter with its contextual form (isolated / initial / medial / final)
 *         from the Arabic Presentation Forms-B block, and merges lam-alef into one character.</li>
 *     <li><b>Display order (bidi):</b> reorders the right-to-left runs with the JDK {@link Bidi}, reversing the
 *         Arabic runs and mirroring the brackets.</li>
 * </ol>
 *
 * <p>This class is needed because PDFBox draws the characters exactly as it receives them, from left to right.
 * Without these two steps, Arabic comes out as separate, reversed letters even when the font contains the
 * letters.</p>
 *
 * <p>{@link #forDisplay(String)} orders a single text in its own direction.
 * {@link #forDisplay(String, String, boolean)} is the one the documents use: a line of a paragraph, on a page
 * with a direction, with numbers and dates isolated so they read as they were written.</p>
 *
 * <p>The class is stateless and safe to use from more than one thread.</p>
 */
public final class ArabicTextShaper {

    private ArabicTextShaper() {}

    private static final char ARABIC_START = 'ء'; // ء
    private static final char ARABIC_END = 'ي';   // ي
    private static final char TATWEEL = 'ـ';      // ـ
    private static final char LAM = 'ل';          // ل

    /**
     * For each letter from {@code ء} to {@code ي}: its first form in the Presentation Forms-B block.
     * Inside the block the forms are ordered: isolated, final, then (initial, medial) for dual-joining letters.
     * The value 0 means a code point that Unicode does not use.
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

    /**
     * The number of forms per letter: 4 = dual-joining, 2 = joins the previous letter only, 1 = always isolated,
     * 0 = tatweel/unused.
     */
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
     * Letters outside {@code ء..ي} that appear in Arabic names and words ("ڤيلا", "چيهان", "جوجل" written with
     * "گ") and in Persian and Urdu. Their forms are in the Presentation Forms-A block, in the same order:
     * isolated, final, initial, medial. Without them the letter was drawn isolated and broke the word.
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
     * A number with its sign and separators: an amount, a date, a time, a percentage. It must end with a digit
     * or a percent sign, so the full stop at the end of a sentence stays with the sentence. It must not touch a
     * Latin letter: the digits of a code such as "NC7013" are part of a Latin word that bidi leaves intact, and
     * isolating them on their own printed "7013NC".
     */
    private static final Pattern NUMBER =
            Pattern.compile("(?<![A-Za-z\\d])[-+]?\\d(?:[\\d,.:/\\-]*\\d)?%?(?![A-Za-z\\d])");

    /**
     * A phone number written in groups separated by spaces or hyphens ("0100 000 0000", "+20 100 000 0000"). It
     * starts with zero or +, has at least two groups and at least seven digits. Under the Unicode rules each group
     * is a separate number, so an Arabic line lays the groups out from right to left and the phone reads
     * "0000 000 0100". Here the whole number is isolated as one piece.
     */
    private static final Pattern PHONE = Pattern.compile(
            "(?<![A-Za-z\\d+])(?=[+0])(?=\\+?(?:\\d[ -]?){7,})\\+?\\d+(?:[ -]\\d+)+(?![A-Za-z\\d])");

    /**
     * A hyphen between two digits. After an Arabic letter each number is isolated on its own ("مقاس 20-24-28" is a
     * list that reads in the line direction).
     */
    private static final Pattern HYPHEN_BETWEEN_DIGITS = Pattern.compile("(?<=\\d)-(?=\\d)");

    /** A real date (year-month or year-month-day) stays one piece even after an Arabic word: "من 2025-10-01". */
    private static final Pattern DATE =
            Pattern.compile("(?:19|20)\\d{2}-(?:0[1-9]|1[0-2])(?:-(?:0[1-9]|[12]\\d|3[01]))?");

    /** The start of a Latin run: a Latin letter not preceded by a Latin letter or a digit. */
    private static final Pattern LATIN_RUN_START = Pattern.compile("(?<![A-Za-z\\d])[A-Za-z]");

    // مكتوبين كـ escapes: الاتنين غير مرئيين، والحرف الحرفي بيضيع مع أول تعديل.
    private static final char LEFT_TO_RIGHT_ISOLATE = '\u2066';
    private static final char POP_DIRECTIONAL_ISOLATE = '\u2069';

    /**
     * One line of a paragraph, ready to draw on a page whose direction is {@code rightToLeft}.
     *
     * <p><b>Numbers are isolated before bidi, and this is the part that is easy to break.</b> Under the Unicode
     * rules, a minus sign with no Arabic next to it takes the paragraph direction and moves to the end of the
     * number ({@code 11,995.00-}), a date after an Arabic word is reversed ({@code 01-10-2025}), and the percent
     * sign comes before its number ({@code %113.44}). Every number, date, time and percentage, and every Latin
     * run, is put in a left-to-right isolate (LRI ... PDI), and the isolate marks are removed from the output,
     * so it reads as it was written.</p>
     *
     * <p><b>Wrapping text must be split into its lines before it comes here, not after.</b> The output is in
     * drawing order; if it is split afterwards, the end of the sentence ends up on the first line. That is why
     * the direction comes from {@code paragraph} (the whole text): a second line that happens to start with a
     * Latin word is not reversed on its own.</p>
     *
     * @param line        the line to draw, in logical order
     * @param paragraph   the whole text the line was cut from, which sets the direction; {@code null} = the line
     *                    itself
     * @param rightToLeft the page direction; it decides only text that has no letters (a date and time, a numeric
     *                    code)
     * @return the line in left-to-right display order, with no direction control marks
     */
    public static String forDisplay(String line, String paragraph, boolean rightToLeft) {
        if (line == null || line.isEmpty()) {
            return "";
        }
        String shaped = shape(isolateNumbers(line));
        boolean rtl = paragraphIsRightToLeft(paragraph == null ? line : paragraph, rightToLeft);
        return stripFormatChars(reorder(shaped, rtl ? Bidi.DIRECTION_RIGHT_TO_LEFT : Bidi.DIRECTION_LEFT_TO_RIGHT));
    }

    /** The direction of the first strong character in the text; text with no letters takes the page direction. */
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

    /**
     * Puts every Latin run, and every number outside one, in a left-to-right isolate, so bidi moves each one as a
     * single piece.
     */
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
     * The end of the Latin run that starts at {@code start}: the last letter, digit or percent sign before the
     * first right-to-left character. After a Latin letter, the digits that follow are left-to-right too, so
     * "Pepsi 330" is one piece; the spaces and punctuation after it belong to the Arabic line.
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

    /** The numbers between two Latin runs, each one isolated. */
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

    /**
     * Is the first letter before {@code index} Arabic? This is the condition that makes bidi treat the numbers
     * after it as "Arabic numbers".
     */
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

    /** The direction control marks have done their job after bidi, and most fonts do not contain them. */
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

    /** Turns the text into its final form, ready to draw: shaping, then display order. */
    public static String forDisplay(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return toVisualOrder(shape(text));
    }

    /** Replaces the Arabic letters with their contextual forms and leaves the rest of the text unchanged. */
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
     * Reorders the text from logical order to display order (left to right, as it will be drawn). It reverses
     * the Arabic runs and keeps the Latin runs and the numbers in their natural direction.
     */
    public static String toVisualOrder(String text) {
        return reorder(text, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
    }

    /** Display order with a given paragraph direction ({@code Bidi.DIRECTION_*}). */
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

    /** Does the text contain any character from the Arabic blocks (including the presentation forms)? */
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

    /** The first form of the lam-alef ligature, or 0 if the next letter is not an alef. */
    private static char lamAlefLigature(char alef) {
        return switch (alef) {
            case 'آ' -> 'ﻵ'; // لآ
            case 'أ' -> 'ﻷ'; // لأ
            case 'إ' -> 'ﻹ'; // لإ
            case 'ا' -> 'ﻻ'; // لا
            default -> 0;
        };
    }

    /** Can the letter join the letter after it (dual-joining, or tatweel)? */
    private static boolean joinsForward(char c) {
        return c == TATWEEL || formCount(c) == 4;
    }

    /** Can the letter join the letter before it (it has a final form, or it is tatweel)? */
    private static boolean joinsBackward(char c) {
        return c == TATWEEL || formCount(c) >= 2;
    }

    /**
     * The first presentation form of the letter, or 0 if it is not an Arabic letter that can be shaped. Tatweel
     * returns itself.
     */
    private static char formBase(char c) {
        if (c >= ARABIC_START && c <= ARABIC_END) {
            return FORM_BASE[c - ARABIC_START];
        }
        int extra = EXTRA_LETTERS.indexOf(c);
        return extra < 0 ? 0 : EXTRA_BASE[extra];
    }

    /**
     * The number of forms of the letter: 4 dual-joining, 2 joins the previous letter only, 1 always isolated,
     * 0 tatweel or not a letter.
     */
    private static int formCount(char c) {
        if (c >= ARABIC_START && c <= ARABIC_END) {
            return FORM_COUNT[c - ARABIC_START];
        }
        int extra = EXTRA_LETTERS.indexOf(c);
        return extra < 0 ? 0 : EXTRA_COUNT[extra];
    }

    /** Diacritics are transparent: they do not break the joining between the two letters around them. */
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
     * Reverses the text and keeps each diacritic attached to its base letter,
     * so the vowel marks do not come off their letters when the text is reversed.
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

    /** Brackets and similar characters are mirrored inside right-to-left runs. */
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
