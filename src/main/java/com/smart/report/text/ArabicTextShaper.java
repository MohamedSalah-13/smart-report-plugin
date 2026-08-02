package com.smart.report.text;

import java.text.Bidi;
import java.util.ArrayList;
import java.util.List;

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

    private static final int ISOLATED = 0;
    private static final int FINAL = 1;
    private static final int INITIAL = 2;
    private static final int MEDIAL = 3;

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
            int index = shapeIndex(c);

            if (index < 0) { // علامة تشكيل أو حرف غير عربي: يمرّ كما هو
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
            out.append(selectForm(index, joinsPrevious, joinsNext));
            i++;
        }
        return out.toString();
    }

    /**
     * يعيد ترتيب النص من الترتيب المنطقي لترتيب العرض (من اليسار لليمين كما سيُرسم)،
     * فيعكس المقاطع العربية ويحافظ على المقاطع اللاتينية والأرقام في اتجاهها الطبيعي.
     */
    public static String toVisualOrder(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Bidi bidi = new Bidi(text, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
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
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= '؀' && c <= 'ۿ')   // Arabic
                    || (c >= 'ݐ' && c <= 'ݿ')   // Arabic Supplement
                    || (c >= 'ﭐ' && c <= '﷿')   // Presentation Forms-A
                    || (c >= 'ﹰ' && c <= '﻿')) { // Presentation Forms-B
                return true;
            }
        }
        return false;
    }

    private static char selectForm(int index, boolean joinsPrevious, boolean joinsNext) {
        char base = FORM_BASE[index];
        int count = FORM_COUNT[index];
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
        if (c == TATWEEL) return true;
        int index = shapeIndex(c);
        return index >= 0 && FORM_COUNT[index] == 4;
    }

    /** هل يستطيع الحرف الاتصال بما قبله (له صورة نهائية أو تطويل)؟ */
    private static boolean joinsBackward(char c) {
        if (c == TATWEEL) return true;
        int index = shapeIndex(c);
        return index >= 0 && FORM_COUNT[index] >= 2;
    }

    /** موضع الحرف في جداول الصور، أو -1 لو ليس حرفًا عربيًا قابلًا للتشكيل. */
    private static int shapeIndex(char c) {
        if (c < ARABIC_START || c > ARABIC_END) return -1;
        int index = c - ARABIC_START;
        return FORM_BASE[index] == 0 ? -1 : index;
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
