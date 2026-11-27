package com.smart.report.jasper;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntPredicate;

/**
 * اللي اترسم فعلًا في PDF طالع من Jasper: النص، والخطوط اللي رسمته، ومواضع الحروف.
 *
 * <p>النص المستخرج بيقول الكلام <em>إيه</em> بس، و PDFBox بيرجّعه للترتيب المنطقي بنفسه. ترتيب الرسم
 * نفسه (أول الكلمة العربية على اليمين) ما بيبانش غير من مواضع الحروف: {@link #rightToLeftLines()}.</p>
 */
final class PdfText {

    /** حرف مرسوم: نصه (بعد فك صور الحروف العربية لحروفها الأصلية)، خطه، ومكانه بالنقطة. */
    record Glyph(String text, String font, float x, float y) {
    }

    private final String text;
    private final List<Glyph> glyphs;

    private PdfText(String text, List<Glyph> glyphs) {
        this.text = text;
        this.glyphs = glyphs;
    }

    static PdfText of(byte[] pdf) throws IOException {
        List<Glyph> glyphs = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void processTextPosition(TextPosition position) {
                    // صورة الحرف (ﺗ، ﻻ...) بترجع لحرفها الأصلي، والـ لا المركّبة لحرفين بترتيبهم
                    String font = position.getFont().getName();
                    glyphs.add(new Glyph(Normalizer.normalize(position.getUnicode(), Normalizer.Form.NFKC),
                            font.matches("[A-Z]{6}\\+.*") ? font.substring(7) : font,
                            position.getXDirAdj(), position.getYDirAdj()));
                    super.processTextPosition(position);
                }
            };
            return new PdfText(canonical(stripper.getText(document)), glyphs);
        }
    }

    /** نص المستند كله زي ما PDFBox بيستخرجه، بعد {@link #canonical(String)}. */
    String text() {
        return text;
    }

    /** الخطوط اللي رسمت حروفًا، من غير بادئة الـ subset ({@code ABCDEF+}). */
    Set<String> fonts() {
        return fontsOf(codePoint -> true);
    }

    /** الخطوط اللي رسمت الحروف العربية بس. */
    Set<String> fontsOfArabic() {
        return fontsOf(ArabicFonts::isArabic);
    }

    /** الخطوط اللي رسمت الحروف اللاتينية بس. */
    Set<String> fontsOfLatin() {
        return fontsOf(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN);
    }

    private Set<String> fontsOf(IntPredicate letter) {
        Set<String> fonts = new TreeSet<>();
        glyphs.stream().filter(glyph -> !glyph.text().isEmpty() && letter.test(glyph.text().codePointAt(0)))
                .forEach(glyph -> fonts.add(glyph.font()));
        return fonts;
    }

    /**
     * كل سطر مرسوم مقروءًا من اليمين للشمال، من غير مسافات، بعد {@link #canonical(String)}. كلمة عربية
     * اترسمت صح بتطلع فيه بترتيبها؛ لو اترسمت بالترتيب المنطقي من الشمال لليمين بتطلع مقلوبة.
     */
    List<String> rightToLeftLines() {
        List<List<Glyph>> lines = new ArrayList<>();
        glyphs.stream().sorted(Comparator.comparingDouble(Glyph::y)).forEach(glyph -> {
            List<Glyph> last = lines.isEmpty() ? null : lines.get(lines.size() - 1);
            if (last == null || glyph.y() - last.get(0).y() > 0.5f) {
                last = new ArrayList<>();
                lines.add(last);
            }
            last.add(glyph);
        });
        List<String> read = new ArrayList<>();
        for (List<Glyph> line : lines) {
            StringBuilder text = new StringBuilder();
            line.stream().sorted(Comparator.comparingDouble(Glyph::x).reversed())
                    .forEach(glyph -> text.append(glyph.text()));
            read.add(canonical(text.toString()).replace(" ", ""));
        }
        return read;
    }

    /** في سطر من {@link #rightToLeftLines()}، مقروءًا من اليمين. */
    boolean drawnRightToLeft(String words) {
        String wanted = canonical(words).replace(" ", "");
        return rightToLeftLines().stream().anyMatch(line -> line.contains(wanted));
    }

    /**
     * الحرف اللي بيرجع من الاستخراج بيختلف حسب جدول الـ cmap في الخط (ي فارسية بدل عربية، ه دوتشمي بدل
     * هاء...). التوحيد ده بيخلي الاختبار يقيس المحتوى والترتيب مش تفاصيل الخط.
     */
    static String canonical(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            out.append(switch (c) {
                case 'ي', 'ی', 'ى', 'ے', 'ې' -> 'ي';
                case 'ه', 'ھ', 'ہ', 'ە' -> 'ه';
                case 'ك', 'ک' -> 'ك';
                case 'ا', 'أ', 'إ', 'آ', 'ٱ' -> 'ا';
                default -> c;
            });
        }
        return out.toString();
    }
}
