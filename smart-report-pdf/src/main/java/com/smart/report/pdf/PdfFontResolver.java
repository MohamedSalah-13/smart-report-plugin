package com.smart.report.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Finds a Unicode TrueType font for the generic PDF reports. The built-in PDF fonts (the Standard 14, such as
 * Helvetica) support Latin-1 only, so they throw an exception on any Arabic text.
 *
 * <p>Search order:</p>
 * <ol>
 *     <li>The property {@code smart.report.pdf.font} (with {@code smart.report.pdf.font.bold}, optional):
 *         the path of a {@code .ttf} file on disk.</li>
 *     <li>A classpath resource named {@code fonts/report-font.ttf} (and {@code fonts/report-font-bold.ttf}):
 *         put it in {@code src/main/resources} if you want the jar to be self-contained.</li>
 *     <li>The font bundled with the library: Noto Naskh Arabic (regular and bold, SIL OFL licence), so the
 *         output is the same on every machine with no setup.</li>
 *     <li>Known system fonts on Windows/Linux/macOS, if the bundled font was removed from the jar.</li>
 * </ol>
 *
 * <p>This is the global font of the whole application. A document that needs another font sets its own with
 * {@link PdfDocument#font(PdfFont)}, without affecting the other documents.</p>
 *
 * <p>A character that is not in the document font is looked up in two fallback fonts, in order: Liberation Sans,
 * which ships inside PDFBox itself ({@link #symbolFallback()}). It has ², ³, ½, ±, µ, ‰, ≤ and ≥, which Noto
 * Naskh lacks, and it is the same on every machine. Then the system font ({@link #systemFallback()}) for the
 * rest.</p>
 *
 * <p>If no font is found at all, the library falls back to Helvetica and throws
 * {@link com.smart.report.ReportException} with a clear message as soon as it gets text outside Latin-1.</p>
 *
 * <p>The font bytes are read once and cached, so the file is not read from disk for every report.</p>
 */
public final class PdfFontResolver {

    private PdfFontResolver() {}

    private static final Logger log = LoggerFactory.getLogger(PdfFontResolver.class);

    public static final String FONT_PROPERTY = "smart.report.pdf.font";
    public static final String BOLD_FONT_PROPERTY = "smart.report.pdf.font.bold";

    static final String REGULAR_RESOURCE = "fonts/report-font.ttf";
    static final String BOLD_RESOURCE = "fonts/report-font-bold.ttf";

    /** The bundled font, next to this class in the jar. */
    private static final String BUNDLED_REGULAR = "fonts/NotoNaskhArabic-Regular.ttf";
    private static final String BUNDLED_BOLD = "fonts/NotoNaskhArabic-Bold.ttf";

    /** Candidate system fonts (regular, bold), with the best Arabic coverage first. */
    private static final List<String[]> SYSTEM_FONTS = List.of(
            // Windows
            new String[] {"C:/Windows/Fonts/arial.ttf", "C:/Windows/Fonts/arialbd.ttf"},
            new String[] {"C:/Windows/Fonts/tahoma.ttf", "C:/Windows/Fonts/tahomabd.ttf"},
            new String[] {"C:/Windows/Fonts/segoeui.ttf", "C:/Windows/Fonts/segoeuib.ttf"},
            // Linux
            new String[] {"/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                          "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"},
            new String[] {"/usr/share/fonts/truetype/noto/NotoNaskhArabic-Regular.ttf",
                          "/usr/share/fonts/truetype/noto/NotoNaskhArabic-Bold.ttf"},
            new String[] {"/usr/share/fonts/truetype/freefont/FreeSans.ttf",
                          "/usr/share/fonts/truetype/freefont/FreeSansBold.ttf"},
            new String[] {"/usr/share/fonts/TTF/Amiri-Regular.ttf", "/usr/share/fonts/TTF/Amiri-Bold.ttf"},
            // macOS
            new String[] {"/System/Library/Fonts/Supplemental/Arial.ttf",
                          "/System/Library/Fonts/Supplemental/Arial Bold.ttf"},
            new String[] {"/Library/Fonts/Arial.ttf", "/Library/Fonts/Arial Bold.ttf"}
    );

    /**
     * Liberation Sans (SIL OFL licence) inside the PDFBox jar, which the library already depends on. PDFBox uses
     * it for the same purpose, so it is not an extra font file in the library.
     */
    private static final String SYMBOL_FONT = "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf";

    private static final AtomicReference<Fonts> CACHE = new AtomicReference<>();
    /** The fallback system font. An empty array means "looked for and not found". */
    private static final AtomicReference<byte[]> FALLBACK = new AtomicReference<>();
    /** The fallback symbol font; an empty array has the same meaning. */
    private static final AtomicReference<byte[]> SYMBOLS = new AtomicReference<>();

    /** The bytes of the regular and the bold font, or {@code null} in either one if it is not available. */
    public record Fonts(byte[] regular, byte[] bold) {

        public boolean available() {
            return regular != null;
        }
    }

    /**
     * Returns the font bytes (from the cache after the first call); {@link Fonts#available()} is false if there
     * is no font.
     */
    public static Fonts resolve() {
        Fonts cached = CACHE.get();
        if (cached != null) {
            return cached;
        }
        Fonts loaded = load();
        CACHE.compareAndSet(null, loaded);
        return CACHE.get();
    }

    /** Clears the cache; for tests, and to read the fonts again after the system properties change. */
    public static void clearCache() {
        CACHE.set(null);
        FALLBACK.set(null);
        SYMBOLS.set(null);
    }

    /**
     * The first fallback font for characters that are not in the document font: Liberation Sans from PDFBox;
     * {@code null} if the PDFBox version does not contain the file. It is read once, and it is loaded into a
     * document only when a missing character needs it.
     */
    static byte[] symbolFallback() {
        byte[] cached = SYMBOLS.get();
        if (cached == null) {
            byte[] found;
            try (InputStream in = PDDocument.class.getResourceAsStream(SYMBOL_FONT)) {
                found = in == null ? new byte[0] : in.readAllBytes();
            } catch (IOException e) {
                found = new byte[0];
            }
            if (found.length == 0) {
                log.debug("PDFBox carries no {}; glyphs the report font lacks go to the system font", SYMBOL_FONT);
            }
            SYMBOLS.compareAndSet(null, found);
            cached = SYMBOLS.get();
        }
        return cached.length == 0 ? null : cached;
    }

    /**
     * The first available system font, for characters that are not in the chosen font; {@code null} if there is
     * none. It is read once, and it is loaded into a document only when a missing character needs it.
     */
    static byte[] systemFallback() {
        byte[] cached = FALLBACK.get();
        if (cached == null) {
            byte[] found = new byte[0];
            for (String[] candidate : SYSTEM_FONTS) {
                byte[] bytes = readFile(candidate[0]);
                if (bytes != null) {
                    log.debug("Using system font {} for glyphs the report font lacks", candidate[0]);
                    found = bytes;
                    break;
                }
            }
            FALLBACK.compareAndSet(null, found);
            cached = FALLBACK.get();
        }
        return cached.length == 0 ? null : cached;
    }

    private static Fonts load() {
        byte[] regular = readFile(System.getProperty(FONT_PROPERTY));
        byte[] bold = readFile(System.getProperty(BOLD_FONT_PROPERTY));
        if (regular != null) {
            log.debug("Using PDF font from -D{}={}", FONT_PROPERTY, System.getProperty(FONT_PROPERTY));
            return new Fonts(regular, bold != null ? bold : regular);
        }
        if (System.getProperty(FONT_PROPERTY) != null) {
            log.warn("-D{}={} could not be read; falling back to the other font sources.",
                    FONT_PROPERTY, System.getProperty(FONT_PROPERTY));
        }

        regular = readResource(REGULAR_RESOURCE);
        if (regular != null) {
            log.debug("Using PDF font from classpath resource {}", REGULAR_RESOURCE);
            bold = readResource(BOLD_RESOURCE);
            return new Fonts(regular, bold != null ? bold : regular);
        }

        regular = readBundled(BUNDLED_REGULAR);
        if (regular != null) {
            log.debug("Using the bundled PDF font Noto Naskh Arabic");
            bold = readBundled(BUNDLED_BOLD);
            return new Fonts(regular, bold != null ? bold : regular);
        }

        for (String[] candidate : SYSTEM_FONTS) {
            regular = readFile(candidate[0]);
            if (regular != null) {
                log.debug("Using system PDF font {}", candidate[0]);
                bold = readFile(candidate[1]);
                return new Fonts(regular, bold != null ? bold : regular);
            }
        }

        log.warn("No Unicode TrueType font found; generic PDF reports fall back to Helvetica and support "
                + "Latin-1 only. Set -D{} to a .ttf, or put {} on the classpath.", FONT_PROPERTY, REGULAR_RESOURCE);
        return new Fonts(null, null);
    }

    private static byte[] readFile(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            Path file = Path.of(path);
            return Files.isReadable(file) ? Files.readAllBytes(file) : null;
        } catch (InvalidPathException | IOException e) {
            return null; // مرشّح غير صالح: نجرّب اللي بعده
        }
    }

    /**
     * A resource next to this class. It is read through the class itself, so it works on the classpath and on the
     * module path.
     */
    private static byte[] readBundled(String resource) {
        try (InputStream in = PdfFontResolver.class.getResourceAsStream(resource)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] readResource(String resource) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = PdfFontResolver.class.getClassLoader();
        }
        try (InputStream in = loader.getResourceAsStream(resource)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }
}
