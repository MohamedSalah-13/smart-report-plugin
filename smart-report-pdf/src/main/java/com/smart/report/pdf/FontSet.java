package com.smart.report.pdf;

import com.smart.report.ReportException;
import org.apache.fontbox.ttf.CmapLookup;
import org.apache.fontbox.ttf.GlyphData;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * خطوط مستند واحد: العادي والعريض وخط احتياطي من النظام، مع تقسيم أي نص لمقاطع كل مقطع منها
 * بيترسم بالخط اللي فيه حروفه.
 *
 * <p><b>كل حرف بياخد أول خط بيعرف يرسمه.</b> الخط العريض المضمَّن (Noto Naskh Bold) ما فيهوش
 * علامة الطرح ولا الحروف اللاتينية؛ لو النص اترسم بيه كله، الرقم السالب في صف الإجمالي يطلع من
 * غير إشارته ويتقرا موجبًا. الحرف الناقص من العريض بيترسم بالعادي مع تسميك مصطنع، والناقص من
 * الاتنين بيتجرّب في خط النظام، وآخر حل علامة استفهام مع تحذير في الـ log بدل وقوف الطباعة.</p>
 */
final class FontSet implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(FontSet.class);

    /** خط واحد ومعاه جدول حروفه. */
    static final class Face {
        private final PDFont font;
        /** null مع خطوط PDF المدمجة (Helvetica)، اللي بتتسأل عن الحرف بمحاولة ترميزه. */
        private final CmapLookup cmap;
        /** ملف الخط نفسه، لمقاسات الحروف؛ null مع الخطوط المدمجة. */
        private final TrueTypeFont ttf;
        private final Map<Integer, Boolean> known = new HashMap<>();
        private final Map<Long, Float> markShifts = new HashMap<>();

        Face(PDFont font, TrueTypeFont ttf) throws IOException {
            this.font = font;
            this.ttf = ttf;
            this.cmap = ttf == null ? null : ttf.getUnicodeCmapLookup();
        }

        /**
         * كام (بوحدة 1/1000 من حجم الخط) تترجع الحركة لورا عشان تتوسّط فوق حرفها.
         *
         * <p>مكان الحركة الصح في جدول GPOS في الخط، و PDFBox ما بيطبّقهوش: من غيره الحركة بتترسم
         * عند آخر حرفها، يعني بجانبه مش فوقه. هنا منتصف رسمة الحركة بيتحط على منتصف عرض الحرف —
         * تقريب أفقي كفاية للطباعة؛ الارتفاع بيفضل ارتفاع الخط الافتراضي.</p>
         */
        float markShift(int base, int mark) {
            if (ttf == null) {
                return 0;
            }
            return markShifts.computeIfAbsent(((long) base << 32) | mark, key -> {
                try {
                    float scale = 1000f / ttf.getUnitsPerEm();
                    float advance = ttf.getAdvanceWidth(cmap.getGlyphId(base)) * scale;
                    GlyphData glyph = ttf.getGlyph().getGlyph(cmap.getGlyphId(mark));
                    if (glyph == null || glyph.getBoundingBox() == null) {
                        return 0f;
                    }
                    float centre = (glyph.getBoundingBox().getLowerLeftX()
                            + glyph.getBoundingBox().getUpperRightX()) / 2 * scale;
                    return advance / 2 + centre;
                } catch (IOException | RuntimeException e) {
                    return 0f;
                }
            });
        }

        PDFont font() {
            return font;
        }

        boolean has(int codePoint) {
            if (cmap != null) {
                return cmap.getGlyphId(codePoint) != 0;
            }
            return known.computeIfAbsent(codePoint, cp -> {
                try {
                    font.encode(new String(Character.toChars(cp)));
                    return true;
                } catch (IllegalArgumentException | IOException | UnsupportedOperationException e) {
                    return false;
                }
            });
        }
    }

    /**
     * مقطع من سطر بيترسم بخط واحد.
     *
     * @param fakeBold الخط ده مش عريض والنص مطلوب عريضًا: بيترسم بتسميك مصطنع
     */
    record Run(Face face, String text, boolean fakeBold) {

        /**
         * المقطع كمصفوفة TJ (نصوص وبينها إزاحات) لو فيه حركات محتاجة تتوسّط فوق حروفها، وإلا
         * {@code null} والمقطع بيترسم نصًا واحدًا. الإزاحة الموجبة بترجّع القلم لليسار، وكل إزاحة
         * بتترد بعد حركتها، فعرض المقطع ما بيتغيرش.
         */
        Object[] positioned() {
            List<Object> parts = null;
            StringBuilder segment = new StringBuilder();
            int base = -1;
            for (int i = 0; i < text.length(); ) {
                int codePoint = text.codePointAt(i);
                i += Character.charCount(codePoint);
                boolean mark = Character.getType(codePoint) == Character.NON_SPACING_MARK;
                float shift = mark && base >= 0 ? face.markShift(base, codePoint) : 0;
                if (shift == 0) {
                    segment.appendCodePoint(codePoint);
                    if (!mark) {
                        base = codePoint;
                    }
                    continue;
                }
                if (parts == null) {
                    parts = new ArrayList<>();
                }
                if (segment.length() > 0) {
                    parts.add(segment.toString());
                    segment.setLength(0);
                }
                parts.add(shift);
                parts.add(new String(Character.toChars(codePoint)));
                parts.add(-shift);
            }
            if (parts == null) {
                return null;
            }
            if (segment.length() > 0) {
                parts.add(segment.toString());
            }
            return parts.toArray();
        }
    }

    private final PDDocument pdf;
    private final Face regular;
    private final Face bold;
    private final boolean unicode;
    private final List<TrueTypeFont> opened = new ArrayList<>();
    private final Set<Integer> reportedMissing = new HashSet<>();
    private Face fallback;
    private boolean fallbackTried;

    private FontSet(PDDocument pdf, PdfFontResolver.Fonts bytes) throws IOException {
        this.pdf = pdf;
        if (bytes.available()) {
            this.unicode = true;
            this.regular = load(bytes.regular());
            // PdfFontResolver بيرجّع نفس البايتات للعريض لو مفيش ملف عريض: يبقى التسميك مصطنعًا.
            this.bold = bytes.bold() == null || bytes.bold() == bytes.regular() ? regular : load(bytes.bold());
        } else {
            this.unicode = false;
            this.regular = new Face(new PDType1Font(Standard14Fonts.FontName.HELVETICA), null);
            this.bold = new Face(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), null);
        }
    }

    static FontSet load(PDDocument pdf) throws IOException {
        return new FontSet(pdf, PdfFontResolver.resolve());
    }

    private Face load(byte[] bytes) throws IOException {
        TrueTypeFont ttf = new TTFParser().parse(new RandomAccessReadBuffer(bytes));
        opened.add(ttf);
        return new Face(PDType0Font.load(pdf, ttf, true), ttf);
    }

    /** هل فيه خط Unicode، ولا رجعنا لـ Helvetica المدمج (Latin-1 فقط)؟ */
    boolean unicode() {
        return unicode;
    }

    /** النص مقسّمًا لمقاطع، كل مقطع بالخط اللي بيرسم حروفه. */
    List<Run> runs(String text, boolean wantBold) throws ReportException {
        List<Run> runs = new ArrayList<>(2);
        StringBuilder buffer = new StringBuilder();
        Face current = null;
        boolean currentFake = false;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            Face face;
            boolean fake;
            int drawn = codePoint;
            if (wantBold && bold.has(codePoint)) {
                face = bold;
                fake = bold == regular;
            } else if (regular.has(codePoint)) {
                face = regular;
                fake = wantBold;
            } else {
                Face system = fallback();
                if (system != null && system.has(codePoint)) {
                    face = system;
                } else {
                    missing(codePoint);
                    face = regular;
                    drawn = '?';
                }
                fake = wantBold;
            }
            if (face != current || fake != currentFake) {
                if (buffer.length() > 0) {
                    runs.add(new Run(current, buffer.toString(), currentFake));
                    buffer.setLength(0);
                }
                current = face;
                currentFake = fake;
            }
            buffer.appendCodePoint(drawn);
        }
        if (buffer.length() > 0) {
            runs.add(new Run(current, buffer.toString(), currentFake));
        }
        return runs;
    }

    /** عرض المقاطع بالنقطة عند حجم {@code size}. */
    static float width(List<Run> runs, float size) throws IOException {
        float width = 0;
        for (Run run : runs) {
            width += run.face().font().getStringWidth(run.text()) / 1000f * size;
        }
        return width;
    }

    private Face fallback() {
        if (!fallbackTried) {
            fallbackTried = true;
            if (unicode) {
                byte[] bytes = PdfFontResolver.systemFallback();
                if (bytes != null) {
                    try {
                        fallback = load(bytes);
                    } catch (IOException e) {
                        log.debug("The system fallback font could not be loaded.", e);
                    }
                }
            }
        }
        return fallback;
    }

    private void missing(int codePoint) throws ReportException {
        if (!unicode) {
            throw new ReportException(String.format(
                    "The built-in PDF font (Helvetica) has no glyph for: U+%04X. Only the Standard-14 fonts were"
                            + " available, and they support Latin-1 only. Set -D%s=/path/to/font.ttf (optionally"
                            + " -D%s=/path/to/font-bold.ttf), or put %s on the classpath.",
                    codePoint, PdfFontResolver.FONT_PROPERTY, PdfFontResolver.BOLD_FONT_PROPERTY,
                    PdfFontResolver.REGULAR_RESOURCE));
        }
        if (reportedMissing.add(codePoint)) {
            log.warn(String.format("No PDF font has a glyph for U+%04X; it is printed as '?'. Choose a font that"
                    + " covers it with -D%s=/path/to/font.ttf", codePoint, PdfFontResolver.FONT_PROPERTY));
        }
    }

    /** بعد حفظ المستند: PDFBox بيقرا من ملفات الخطوط لحد لحظة الحفظ، فما تتقفلش قبلها. */
    @Override
    public void close() throws IOException {
        IOException first = null;
        for (TrueTypeFont ttf : opened) {
            try {
                ttf.close();
            } catch (IOException e) {
                if (first == null) {
                    first = e;
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }
}
