package com.smart.report.pdf;

import com.smart.report.ReportException;
import com.smart.report.text.ArabicTextShaper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * النص سطورًا جاهزة للرسم، مقاسة بالخط اللي هتترسم بيه.
 *
 * <p><b>السطر بيتكسر قبل ما يتشكّل، مش بعده.</b> التشكيل بيحط السطر بترتيب الرسم؛ لو اتكسر بعدها، آخر الجملة
 * يطلع في السطر الأول. النص المنطقي بيتكسر عند مسافاته بقياس كل سطر مرشّح، وكل سطر بيتشكّل لوحده.</p>
 */
final class Typesetter {

    /** ارتفاع السطر مضروبًا في حجم الخط. خط النسخ بيعلن سطرًا أطول بكتير من حروفه (1.7)، فالارتفاع ثابت. */
    static final float LEADING = 1.45f;
    /** بُعد خط الكتابة عن أعلى السطر، مضروبًا في حجم الخط. */
    static final float BASELINE = 0.98f;

    /** سطر جاهز للرسم: مقاطعه بترتيب الرسم وعرضه. */
    record Line(List<FontSet.Run> runs, float width) {
    }

    private final FontSet fonts;
    private final boolean rtl;
    /** عرض الكلمة مرسومة، بالوجه والحجم والكلمة: التقرير بيكرر كلماته في كل صف. */
    private final Map<String, Float> wordWidths = new HashMap<>();

    /** الأرقام بتترسم ٠١٢٣ بدل 0123 ({@link PdfDocument#arabicIndicDigits}). */
    private final boolean arabicDigits;

    Typesetter(FontSet fonts, boolean rtl) {
        this(fonts, rtl, false);
    }

    Typesetter(FontSet fonts, boolean rtl, boolean arabicDigits) {
        this.fonts = fonts;
        this.rtl = rtl;
        this.arabicDigits = arabicDigits;
    }

    /**
     * الأرقام اللاتينية بأرقام عربية، بعد ترتيب العرض: عزل الأرقام وترتيبها بيشتغل على الأرقام اللاتينية زي ما هو،
     * والرقم العربي بيتقري من الشمال لليمين زيه، فكل رقم بيفضل مكانه وبس شكله بيتغير.
     */
    static String arabicIndic(String visual) {
        StringBuilder out = null;
        for (int i = 0; i < visual.length(); i++) {
            char c = visual.charAt(i);
            if (c >= '0' && c <= '9') {
                if (out == null) {
                    out = new StringBuilder(visual);
                }
                out.setCharAt(i, (char) ('\u0660' + (c - '0')));
            }
        }
        return out == null ? visual : out.toString();
    }

    /** محارف التحكم (tab وغيره) لمسافات؛ السطر الجديد بيفضل، لأنه كسر سطر مقصود. */
    static String clean(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\r') {
                continue;
            }
            out.append(c != '\n' && Character.isISOControl(c) ? ' ' : c);
        }
        return out.toString();
    }

    /**
     * سطر منطقي واحد جاهزًا للرسم: متشكّلًا ومترتبًا بترتيب العرض في اتجاه النص اللي اتقطع منه،
     * ومقسّمًا على الخطوط. مع Helvetica المدمج مفيش تشكيل: الخط ما بيرسمش غير Latin-1 أصلًا.
     */
    Line line(String logical, String paragraph, boolean bold, float size) throws IOException, ReportException {
        return line(logical, paragraph, paragraph, bold, size);
    }

    /** @param source النص اللي السطر جاي منه زي ما اتكتب (الخلية كلها مثلًا)، لرسالة الحرف الناقص */
    Line line(String logical, String paragraph, String source, boolean bold, float size)
            throws IOException, ReportException {
        String visual = fonts.unicode() ? ArabicTextShaper.forDisplay(logical, paragraph, rtl) : logical;
        if (arabicDigits && fonts.unicode()) {
            visual = arabicIndic(visual);
        }
        List<FontSet.Run> runs = fonts.runs(visual, bold, source == null ? logical : source);
        return new Line(runs, FontSet.width(runs, size));
    }

    /** النص زي ما هو، من غير تشكيل ولا ترتيب اتجاه: نص الباركود. */
    Line plain(String text, float size) throws IOException, ReportException {
        List<FontSet.Run> runs = fonts.runs(text, false);
        return new Line(runs, FontSet.width(runs, size));
    }

    float measure(String logical, String paragraph, boolean bold, float size) throws IOException, ReportException {
        return line(logical, paragraph, bold, size).width();
    }

    /**
     * النص مكسورًا للسطور اللي تكفيها {@code maxWidth}. الكسر على النص المنطقي عند مسافاته، وكل سطر
     * بيتشكّل لوحده؛ الكلمة الأطول من العرض كله بتتكسر على حروفها. {@code maxWidth} صفر أو أقل
     * معناه بلا كسر غير السطور الجديدة المكتوبة.
     */
    List<Line> wrap(String text, boolean bold, float size, float maxWidth) throws IOException, ReportException {
        String logical = clean(text);
        List<Line> lines = new ArrayList<>(1);
        for (String hard : logical.split("\n", -1)) {
            Line whole = line(hard, logical, bold, size);
            if (maxWidth <= 0 || whole.width() <= maxWidth) {
                lines.add(whole);
                continue;
            }
            StringBuilder current = new StringBuilder();
            for (String word : hard.strip().split(" +")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (measure(candidate, logical, bold, size) <= maxWidth) {
                    current.setLength(0);
                    current.append(candidate);
                    continue;
                }
                if (!current.isEmpty()) {
                    lines.add(line(current.toString(), logical, bold, size));
                    current.setLength(0);
                }
                String rest = word;
                while (measure(rest, logical, bold, size) > maxWidth) {
                    int cut = fittingPrefix(rest, logical, bold, size, maxWidth);
                    lines.add(line(rest.substring(0, cut), logical, bold, size));
                    rest = rest.substring(cut);
                }
                current.append(rest);
            }
            if (!current.isEmpty()) {
                lines.add(line(current.toString(), logical, bold, size));
            }
        }
        return lines;
    }

    /** أطول بداية من {@code word} تكفيها {@code maxWidth}، وحرف واحد على الأقل. */
    private int fittingPrefix(String word, String paragraph, boolean bold, float size, float maxWidth)
            throws IOException, ReportException {
        int[] ends = word.codePoints().map(Character::charCount).toArray();
        int low = 1;
        int high = ends.length;
        while (low < high) {
            int middle = (low + high + 1) / 2;
            if (measure(word.substring(0, offset(ends, middle)), paragraph, bold, size) <= maxWidth) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return offset(ends, low);
    }

    private static int offset(int[] charCounts, int codePoints) {
        int offset = 0;
        for (int i = 0; i < codePoints; i++) {
            offset += charCounts[i];
        }
        return offset;
    }

    /** عرض أطول كلمة في النص بالخط والحجم اللي هيترسم بيهم؛ صفر للنص الفاضي. */
    float widestWord(String text, boolean bold, float size) throws IOException, ReportException {
        if (text == null || text.isBlank()) {
            return 0;
        }
        float widest = 0;
        String cell = clean(text);
        for (String word : cell.strip().split("[ \n]+")) {
            if (word.isEmpty()) {
                continue;
            }
            String key = (bold ? "b" : "r") + size + '|' + word;
            Float known = wordWidths.get(key);
            if (known == null) {
                known = line(word, word, cell, bold, size).width();
                wordWidths.put(key, known);
            }
            widest = Math.max(widest, known);
        }
        return widest;
    }
}
