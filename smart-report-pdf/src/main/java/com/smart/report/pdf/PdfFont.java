package com.smart.report.pdf;

import com.smart.report.ReportException;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

/**
 * The font of one document (regular and bold), used instead of the global font of the whole application. For
 * example, an invoice in the company font and a statement in the bundled font, in the same application. A
 * document with no font set uses the global font as is ({@link PdfFontResolver}:
 * {@code -Dsmart.report.pdf.font} or the bundled Noto Naskh Arabic).
 *
 * <pre>{@code
 * PdfFont amiri = PdfFont.of(Path.of("fonts/Amiri-Regular.ttf"), Path.of("fonts/Amiri-Bold.ttf"));
 * PdfDocument.rtl(Paper.A4).font(amiri).title("فاتورة").save(Path.of("invoice.pdf"));
 * }</pre>
 *
 * <p><b>The file:</b> a TrueType font ({@code .ttf}, or {@code .otf} with TrueType outlines) that allows
 * embedding. Font collections ({@code .ttc}) and OpenType with CFF outlines (most {@code .otf} files) are not
 * supported by PDFBox, so they are rejected here with a message instead of failing at print time. Arabic is
 * drawn with the ready-made letter forms (U+FB50–U+FDFF and U+FE70–U+FEFF), so the font of an Arabic document
 * must contain those forms. Noto Naskh Arabic, Amiri, Arial and Tahoma have them. A font without them is
 * accepted (for a Latin document, for example), but any Arabic in it is drawn from a system font and a
 * warning is logged the first time.</p>
 *
 * <p>A character that is not in the font is looked up in the fallback fonts, as in any document (see
 * {@link PdfDocument#failOnMissingGlyphs(boolean)}).</p>
 *
 * <p>The bytes are read and checked once, at creation. The object is immutable and safe to share between
 * documents and threads, so create it once and use it in every document.</p>
 */
public final class PdfFont {

    private final PdfFontResolver.Fonts bytes;
    private final String name;

    private PdfFont(PdfFontResolver.Fonts bytes, String name) {
        this.bytes = bytes;
        this.name = name;
    }

    /** A regular font from a file; bold text is drawn from it with synthetic emboldening. */
    public static PdfFont of(Path regular) throws ReportException {
        return of(regular, null);
    }

    /**
     * A regular and a bold font from two files.
     *
     * @param bold {@code null} (or the same file as the regular one) means bold text is drawn from the regular
     *             font with synthetic emboldening
     * @throws ReportException if either file cannot be read, or is not a TrueType font that can be embedded in
     *                         a PDF
     */
    public static PdfFont of(Path regular, Path bold) throws ReportException {
        Objects.requireNonNull(regular, "regular");
        return create(read(regular), regular.toString(), bold == null ? null : read(bold),
                bold == null ? null : bold.toString());
    }

    /**
     * A regular font from its bytes (a classpath resource, for example); bold text is drawn from it with
     * synthetic emboldening.
     */
    public static PdfFont of(byte[] regular) throws ReportException {
        return of(regular, null);
    }

    /**
     * A regular and a bold font from their bytes. The bytes are copied, so changing them later has no effect.
     *
     * @param bold {@code null} means bold text is drawn from the regular font with synthetic emboldening
     * @throws ReportException if the bytes are not a TrueType font that can be embedded in a PDF
     */
    public static PdfFont of(byte[] regular, byte[] bold) throws ReportException {
        Objects.requireNonNull(regular, "regular");
        return create(regular.clone(), "the regular font bytes", bold == null ? null : bold.clone(),
                "the bold font bytes");
    }

    private static PdfFont create(byte[] regular, String regularSource, byte[] bold, String boldSource)
            throws ReportException {
        String name = check(regular, regularSource);
        if (bold == null || Arrays.equals(regular, bold)) {
            // من غير ملف عريض، أو بنفس الملف للاتنين: نفس المصفوفة، و FontSet بيفهم منها إن العريض تسميك مصطنع.
            return new PdfFont(new PdfFontResolver.Fonts(regular, regular), name);
        }
        check(bold, boldSource);
        return new PdfFont(new PdfFontResolver.Fonts(regular, bold), name);
    }

    private static byte[] read(Path file) throws ReportException {
        try {
            return Files.readAllBytes(file);
        } catch (IOException | RuntimeException e) {
            throw new ReportException("Cannot read the font file " + file + ": " + e, e);
        }
    }

    /**
     * Checks that the bytes are a font PDFBox can embed and draw Unicode characters with, and returns its name.
     */
    private static String check(byte[] bytes, String source) throws ReportException {
        try (TrueTypeFont ttf = new TTFParser().parse(new RandomAccessReadBuffer(bytes));
             PDDocument probe = new PDDocument()) {
            // نفس ما FontSet بيعمله وقت الرسم: بيرمي لو الخط ما فيهوش جدول حروف Unicode.
            ttf.getUnicodeCmapLookup();
            PDType0Font.load(probe, ttf, true);
            return ttf.getName();
        } catch (IOException | RuntimeException e) {
            throw new ReportException("Not a usable TrueType font: " + source + " (" + e.getMessage() + ")."
                    + " Use a .ttf file that allows embedding; font collections (.ttc) and OpenType fonts with"
                    + " CFF outlines are not supported.", e);
        }
    }

    /** The name of the regular font as written inside the file (its PostScript name), e.g. {@code Amiri-Regular}. */
    public String name() {
        return name;
    }

    PdfFontResolver.Fonts bytes() {
        return bytes;
    }

    @Override
    public String toString() {
        return "PdfFont[" + name + "]";
    }
}
