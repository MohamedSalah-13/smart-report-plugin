package com.smart.report.pdf;

import com.smart.report.ReportException;
import com.smart.report.ReportRequest;
import com.smart.report.ReportService;
import com.smart.report.ReportType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * خط لكل مستند، والخط الاحتياطي للرموز، والوضع الصارم للحرف الناقص. الخط اللي كل حرف اترسم بيه بيتقرا
 * من الملف نفسه (PDFBox بيدي خط كل حرف وهو بيستخرج النص)، مش من الإعدادات.
 *
 * <p>الخطوط المستخدمة موجودة على أي جهاز: Noto Naskh Arabic المضمَّن (العادي والعريض كخطين مختلفين)
 * و Liberation Sans اللي جوّه PDFBox.</p>
 */
class DocumentFontTest {

    /** حرف مرسوم وخطه. */
    record Drawn(String text, String font, float x) {
    }

    @AfterEach
    void restoreResolver() {
        PdfFontResolver.clearCache();
    }

    @Test
    void documentDrawsItsTextWithItsOwnFont() throws Exception {
        byte[] pdf = PdfDocument.ltr(Paper.A5).font(liberation()).title("Invoice").text("Total 120").toBytes();

        List<Drawn> glyphs = drawn(pdf);
        assertFalse(glyphs.isEmpty());
        for (Drawn glyph : glyphs) {
            assertTrue(glyph.font().contains("LiberationSans"), glyph.text() + " drawn with " + glyph.font());
        }
    }

    /** المستند اللي ما اتحدّدلوش خط بيرسم بالخط المضمَّن زي الأول: العادي للنص، والعريض للعنوان. */
    @Test
    void documentWithoutAFontKeepsTheBundledOne() throws Exception {
        byte[] pdf = PdfDocument.rtl(Paper.A5).title("فاتورة").text("رقم 120").toBytes();

        List<Drawn> glyphs = drawn(pdf);
        assertEquals("NotoNaskhArabic-Bold", fontName(fontOf(glyphs, "ﻓ"))); // فـ في العنوان
        assertEquals("NotoNaskhArabic-Regular", fontName(fontOf(glyphs, "1")));
    }

    /** خطوط مختلفة في نفس الـ JVM وفي نفس الوقت: كل مستند بخطه، والخط العام ما بيتغيرش. */
    @Test
    void documentsWithDifferentFontsDoNotTouchEachOther() throws Exception {
        PdfFont liberation = liberation();
        PdfFont notoBold = PdfFont.of(bundled("NotoNaskhArabic-Bold.ttf"));
        PdfFontResolver.Fonts global = PdfFontResolver.resolve();

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<String>> fonts = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                PdfFont font = switch (i % 3) {
                    case 0 -> liberation;
                    case 1 -> notoBold;
                    default -> null;
                };
                fonts.add(pool.submit(() -> fontOf(drawn(PdfDocument.ltr(Paper.A5).font(font)
                        .text("Total 120").toBytes()), "1")));
            }
            for (int i = 0; i < fonts.size(); i++) {
                String expected = switch (i % 3) {
                    case 0 -> "LiberationSans";
                    case 1 -> "NotoNaskhArabic-Bold";
                    default -> "NotoNaskhArabic-Regular";
                };
                assertEquals(expected, fontName(fonts.get(i).get()), "document " + i);
            }
        } finally {
            pool.shutdownNow();
        }
        assertSame(global, PdfFontResolver.resolve(), "الخط العام زي ما هو");
    }

    /** خط بملفين: النص العريض من الملف العريض. الملفين هنا مقلوبين عمدًا عشان الفرق يبان. */
    @Test
    void boldTextComesFromTheBoldFile(@TempDir Path dir) throws Exception {
        Path regular = Files.write(dir.resolve("regular.ttf"), bundled("NotoNaskhArabic-Bold.ttf"));
        Path bold = Files.write(dir.resolve("bold.ttf"), bundled("NotoNaskhArabic-Regular.ttf"));

        List<Drawn> glyphs = drawn(PdfDocument.rtl(Paper.A5).font(PdfFont.of(regular, bold))
                .title("فاتورة").text("رقم 120").toBytes());
        assertEquals("NotoNaskhArabic-Regular", fontName(fontOf(glyphs, "ﻓ")));
        assertEquals("NotoNaskhArabic-Bold", fontName(fontOf(glyphs, "1")));

        // من غير ملف عريض: العنوان من الملف العادي (بتسميك مصطنع).
        glyphs = drawn(PdfDocument.rtl(Paper.A5).font(PdfFont.of(regular)).title("فاتورة").toBytes());
        assertEquals("NotoNaskhArabic-Bold", fontName(fontOf(glyphs, "ﻓ")));
    }

    /** خط المستند ما بيغيرش التشكيل ولا الاتجاه: الحروف متصلة، وأول الكلمة على اليمين. */
    @Test
    void arabicIsStillShapedAndRightToLeftInADocumentFont() throws Exception {
        PdfFont font = PdfFont.of(bundled("NotoNaskhArabic-Bold.ttf"));
        List<Drawn> glyphs = drawn(PdfDocument.rtl(Paper.A5).font(font).text("مرحبا 2026").toBytes());

        Drawn meem = glyph(glyphs, "ﻣ"); // م في أول الكلمة
        Drawn alef = glyph(glyphs, "ﺎ"); // ا في آخرها
        Drawn year = glyph(glyphs, "2");
        assertEquals("NotoNaskhArabic-Bold", fontName(meem.font()));
        assertTrue(meem.x() > alef.x(), "أول الكلمة على اليمين");
        assertTrue(alef.x() > year.x(), "والرقم بعدها على الشمال");
        assertTrue(glyphs.stream().noneMatch(glyph -> glyph.text().equals("م")), "مفيش حرف منفصل غير متشكّل");
    }

    /** ² و ³ و ½ و ± مش في Noto Naskh: بتترسم من Liberation Sans اللي جوّه PDFBox، على أي جهاز. */
    @Test
    void symbolsTheFontLacksComeFromLiberationSans() throws Exception {
        assertNotNull(PdfFontResolver.symbolFallback(), "PDFBox لازم يفضل فيه Liberation Sans");
        List<Drawn> glyphs = drawn(PdfDocument.rtl(Paper.A5).text("الحجم 12 م³ ± ½ و 4 م²").toBytes());

        for (String symbol : List.of("³", "±", "½", "²")) {
            assertEquals("LiberationSans", fontName(fontOf(glyphs, symbol)), symbol);
        }
        assertEquals("NotoNaskhArabic-Regular", fontName(fontOf(glyphs, "ﻡ")));
        assertTrue(glyphs.stream().noneMatch(glyph -> glyph.text().equals("?")), "ولا علامة استفهام");
    }

    @Test
    void missingCharacterIsPrintedAsQuestionMarkByDefault() throws Exception {
        withoutSystemFont();
        byte[] pdf = PdfDocument.rtl(Paper.A5).text("رقم الصنف 中 12").toBytes();

        assertTrue(drawn(pdf).stream().anyMatch(glyph -> glyph.text().equals("?")));
    }

    @Test
    void strictModeNamesTheCharacterAndTheTextItIsIn() {
        withoutSystemFont();
        PdfDocument document = PdfDocument.rtl(Paper.A5).failOnMissingGlyphs(true)
                .title("فاتورة").text("رقم الصنف 中 12");

        String message = assertThrows(ReportException.class, document::toBytes).getMessage();
        assertTrue(message.contains("'中' (U+4E2D)"), message);
        assertTrue(message.contains("\"رقم الصنف 中 12\""), "النص زي ما اتكتب، مش بترتيب الرسم: " + message);
        assertTrue(message.contains("NotoNaskhArabic"), message);
        assertTrue(message.contains("failOnMissingGlyphs"), message);
    }

    /** خط مالوش صور الحروف العربية: الرسالة بتقول الحرف اللي اتكتب في النص، مش بس الصورة اللي اترسمت. */
    @Test
    void strictModeNamesTheArabicLetterBehindAShapedForm() throws Exception {
        withoutSystemFont();
        PdfDocument document = PdfDocument.rtl(Paper.A5).font(liberation()).failOnMissingGlyphs(true)
                .text("فاتورة");

        // أول حرف ناقص بترتيب الرسم، يعني من آخر الكلمة: ة (U+0629) في صورتها المنفصلة (U+FE93).
        String message = assertThrows(ReportException.class, document::toBytes).getMessage();
        assertTrue(message.contains("'ﺓ' (U+FE93, the shaped form of 'ة' U+0629)"), message);
        assertTrue(message.contains("\"فاتورة\""), message);
        assertTrue(message.contains("LiberationSans"), message);
    }

    @Test
    void strictModeDoesNotFailWhenAFallbackDrawsTheCharacter() throws Exception {
        assertTrue(PdfDocument.rtl(Paper.A5).failOnMissingGlyphs(true).text("12 م³").toBytes().length > 0);
    }

    /** النص الطويل بيتقص في الرسالة حوالين الحرف. */
    @Test
    void longTextIsCutAroundTheMissingCharacter() {
        String text = "أ".repeat(200) + "中" + "ب".repeat(200);
        String excerpt = FontSet.excerpt(text, "中".codePointAt(0));

        assertTrue(excerpt.contains("中"), excerpt);
        assertTrue(excerpt.startsWith("…") && excerpt.endsWith("…"), excerpt);
        assertTrue(excerpt.length() < 90, excerpt);
        assertEquals("قصير", FontSet.excerpt("قصير", 'x'));
    }

    @Test
    void fileThatIsNotAFontIsRejectedWhenTheFontIsMade(@TempDir Path dir) throws Exception {
        Path text = Files.writeString(dir.resolve("font.ttf"), "not a font");
        String message = assertThrows(ReportException.class, () -> PdfFont.of(text)).getMessage();
        assertTrue(message.contains("Not a usable TrueType font") && message.contains(text.toString()), message);

        Path missing = dir.resolve("missing.ttf");
        message = assertThrows(ReportException.class, () -> PdfFont.of(missing)).getMessage();
        assertTrue(message.contains(missing.toString()), message);

        assertThrows(ReportException.class, () -> PdfFont.of(new byte[] {1, 2, 3}));
    }

    @Test
    void fontKeepsItsOwnCopyOfTheBytes() throws Exception {
        byte[] bytes = PdfFontResolver.symbolFallback().clone();
        PdfFont font = PdfFont.of(bytes);
        java.util.Arrays.fill(bytes, (byte) 0);

        assertEquals("LiberationSans", font.name());
        assertEquals("LiberationSans", fontName(fontOf(drawn(PdfDocument.ltr(Paper.A5).font(font)
                .text("Total").toBytes()), "T")));
    }

    public record Row(String name) {
    }

    @Test
    void genericReportTakesTheFontAndStrictModeFromItsParameters() throws Exception {
        ReportRequest<Row> request = ReportRequest.<Row>builder()
                .title("Items")
                .data(List.of(new Row("Bolt")))
                .addColumn("name", "Name")
                .parameter(PdfReportProvider.FONT, liberation())
                .build();
        assertEquals("LiberationSans", fontName(fontOf(drawn(ReportService.generate(ReportType.PDF, request)), "B")));

        withoutSystemFont();
        ReportRequest<Row> strict = ReportRequest.<Row>builder()
                .data(List.of(new Row("Bolt 中")))
                .addColumn("name", "Name")
                .parameter(PdfReportProvider.FAIL_ON_MISSING_GLYPHS, true)
                .build();
        ReportException thrown = assertThrows(ReportException.class,
                () -> ReportService.generate(ReportType.PDF, strict));
        assertTrue(thrown.getMessage().contains("U+4E2D"), thrown.getMessage());

        ReportRequest<Row> wrong = ReportRequest.<Row>builder()
                .data(List.of(new Row("Bolt")))
                .addColumn("name", "Name")
                .parameter(PdfReportProvider.FONT, "fonts/Amiri-Regular.ttf")
                .build();
        thrown = assertThrows(ReportException.class, () -> ReportService.generate(ReportType.PDF, wrong));
        assertTrue(thrown.getMessage().contains("PdfFont"), thrown.getMessage());
    }

    // ---------------------------------------------------------------------------------------------

    private static PdfFont liberation() throws ReportException {
        return PdfFont.of(PdfFontResolver.symbolFallback());
    }

    private static byte[] bundled(String file) throws IOException {
        try (InputStream in = PdfFontResolver.class.getResourceAsStream("fonts/" + file)) {
            assertNotNull(in, file);
            return in.readAllBytes();
        }
    }

    /** يقفل خط النظام الاحتياطي، فالحرف اللي مش في الخطوط المعروفة يبقى ناقصًا على أي جهاز. */
    @SuppressWarnings("unchecked")
    private static void withoutSystemFont() {
        try {
            Field fallback = PdfFontResolver.class.getDeclaredField("FALLBACK");
            fallback.setAccessible(true);
            ((AtomicReference<byte[]>) fallback.get(null)).set(new byte[0]);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    static List<Drawn> drawn(byte[] pdf) throws IOException {
        List<Drawn> found = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void processTextPosition(TextPosition text) {
                    found.add(new Drawn(text.getUnicode(), text.getFont().getName(), text.getXDirAdj()));
                    super.processTextPosition(text);
                }
            };
            stripper.getText(document);
        }
        return found;
    }

    private static Drawn glyph(List<Drawn> glyphs, String text) {
        return glyphs.stream().filter(glyph -> glyph.text().equals(text)).findFirst()
                .orElseThrow(() -> new AssertionError("\"" + text + "\" is not drawn in " + glyphs));
    }

    private static String fontOf(List<Drawn> glyphs, String text) {
        return glyph(glyphs, text).font();
    }

    /** اسم الخط من غير بادئة الجزء المضمَّن ({@code ABCDEF+}). */
    private static String fontName(String font) {
        int plus = font.indexOf('+');
        return plus == 6 ? font.substring(plus + 1) : font;
    }
}
