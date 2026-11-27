package com.smart.report.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * مواضع الحروف المرسومة في PDF. النص المستخرج بيقول الكلام <em>إيه</em> بس؛ عيوب الاتجاه (عمود في
 * الناحية الغلط، إجمالي تحت عمود تاني، آخر الجملة في السطر الأول) ما بتظهرش غير في المواضع.
 */
final class Glyphs {

    /** حرف مرسوم: صفحته (من 1)، ومكانه بالنقطة من أعلى يسار الصفحة، وعرضه. */
    record Glyph(String text, int page, float x, float y, float width) {

        /** الحافة اليمين للحرف. */
        float right() {
            return x + width;
        }
    }

    private final List<Glyph> glyphs = new ArrayList<>();

    static Glyphs of(byte[] pdf) throws IOException {
        Glyphs found = new Glyphs();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void processTextPosition(TextPosition text) {
                    found.glyphs.add(new Glyph(text.getUnicode(), getCurrentPageNo(),
                            text.getXDirAdj(), text.getYDirAdj(), text.getWidthDirAdj()));
                    super.processTextPosition(text);
                }
            };
            stripper.getText(document);
        }
        return found;
    }

    /** أول ظهور للكلمة (حروف لاتينية أو أرقام، بترتيب رسمها) في الصفحة، أو استثناء لو مش مرسومة. */
    Glyph first(String token, int page) {
        Glyph found = find(token, page);
        if (found == null) {
            throw new AssertionError("\"" + token + "\" is not drawn on page " + page);
        }
        return found;
    }

    Glyph first(String token) {
        for (int page = 1; page <= pages(); page++) {
            Glyph found = find(token, page);
            if (found != null) {
                return found;
            }
        }
        throw new AssertionError("\"" + token + "\" is not drawn anywhere");
    }

    /**
     * آخر حرف في أول ظهور للكلمة في أي صفحة: حافته اليمين نهاية الكلمة على الورقة، فرقمين متحاذيين
     * لليمين حافتهم اليمين واحدة حتى لو واحد فيهم بخط عريض.
     */
    Glyph last(String token) {
        for (int page = 1; page <= pages(); page++) {
            int start = start(token, page);
            if (start >= 0) {
                int length = 0;
                int k = start;
                while (true) {
                    length += glyphs.get(k).text().length();
                    if (length >= token.length()) {
                        return glyphs.get(k);
                    }
                    k++;
                }
            }
        }
        throw new AssertionError("\"" + token + "\" is not drawn anywhere");
    }

    boolean drawn(String token, int page) {
        return find(token, page) != null;
    }

    /** بُعد أوطى حرف مرسوم عن أعلى صفحته، بالنقطة. */
    float lowest() {
        return (float) glyphs.stream().mapToDouble(Glyph::y).max().orElse(0);
    }

    /** مواضع السطور المرسومة في الصفحة من فوق لتحت (حروف بنفس الارتفاع تقريبًا = سطر واحد). */
    List<Float> lineYs(int page) {
        List<Float> lines = new ArrayList<>();
        glyphs.stream().filter(glyph -> glyph.page() == page).map(Glyph::y).sorted().forEach(y -> {
            if (lines.isEmpty() || y - lines.get(lines.size() - 1) > 0.5f) {
                lines.add(y);
            }
        });
        return lines;
    }

    /** حروف أوطى سطر في الصفحة من الشمال لليمين: سطر رقم الصفحة لو مفيش تذييل صفحات. */
    List<Glyph> bottomLine(int page) {
        List<Float> lines = lineYs(page);
        if (lines.isEmpty()) {
            return List.of();
        }
        float line = lines.get(lines.size() - 1);
        return glyphs.stream()
                .filter(glyph -> glyph.page() == page && glyph.y() >= line - 0.01f && glyph.y() - line <= 0.5f)
                .sorted(java.util.Comparator.comparingDouble(Glyph::x))
                .toList();
    }

    /**
     * رقم الصفحة وعدد الصفحات المكتوبين في أوطى سطر، من مكانهم على الورقة: في الصفحة الإنجليزية الرقم على
     * الشمال («Page 2 of 3»)، وفي العربية على اليمين («صفحة 2 من 3» بتتقري من اليمين).
     */
    int[] pageNumber(int page, boolean rightToLeft) {
        List<String> numbers = new ArrayList<>();
        Glyph previous = null;
        for (Glyph glyph : bottomLine(page)) {
            if (glyph.text().isEmpty() || !glyph.text().chars().allMatch(Character::isDigit)) {
                previous = null;
                continue;
            }
            if (previous != null && glyph.x() - previous.right() < 0.5f) {
                numbers.set(numbers.size() - 1, numbers.get(numbers.size() - 1) + glyph.text());
            } else {
                numbers.add(glyph.text());
            }
            previous = glyph;
        }
        if (numbers.size() != 2) {
            throw new AssertionError("Page " + page + " has no \"n of count\" line at its bottom: " + numbers);
        }
        int left = Integer.parseInt(numbers.get(0));
        int right = Integer.parseInt(numbers.get(1));
        return rightToLeft ? new int[] {right, left} : new int[] {left, right};
    }

    /** كل الحروف المرسومة في الصفحة، بترتيب رسمها. */
    List<Glyph> onPage(int page) {
        return glyphs.stream().filter(glyph -> glyph.page() == page).toList();
    }

    int pages() {
        return glyphs.stream().mapToInt(Glyph::page).max().orElse(0);
    }

    private Glyph find(String token, int page) {
        int start = start(token, page);
        return start < 0 ? null : glyphs.get(start);
    }

    /** مكان أول حرف في أول ظهور للكلمة في الصفحة، أو {@code -1}. */
    private int start(String token, int page) {
        for (int i = 0; i < glyphs.size(); i++) {
            if (glyphs.get(i).page() != page) {
                continue;
            }
            StringBuilder run = new StringBuilder();
            for (int k = i; k < glyphs.size() && run.length() < token.length(); k++) {
                Glyph glyph = glyphs.get(k);
                if (glyph.page() != page || Math.abs(glyph.y() - glyphs.get(i).y()) > 0.5f) {
                    break;
                }
                run.append(glyph.text());
            }
            if (run.toString().equals(token)) {
                return i;
            }
        }
        return -1;
    }
}
