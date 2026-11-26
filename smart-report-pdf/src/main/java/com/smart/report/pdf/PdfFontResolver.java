package com.smart.report.pdf;

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
 * يحدّد خط TrueType يدعم Unicode لاستخدامه في تقارير الـ PDF العامة، لأن خطوط PDF المدمجة
 * (Standard 14 مثل Helvetica) بتدعم Latin-1 فقط فبترمي استثناء مع أي نص عربي.
 *
 * <p>ترتيب البحث:</p>
 * <ol>
 *     <li>الخاصية {@code smart.report.pdf.font} (ومعها {@code smart.report.pdf.font.bold} اختياريًا):
 *         مسار ملف {@code .ttf} على القرص.</li>
 *     <li>مورد على الـ classpath باسم {@code fonts/report-font.ttf} (و{@code fonts/report-font-bold.ttf}):
 *         حطّه في {@code src/main/resources} لو عايز الـ jar يبقى مكتفيًا بذاته.</li>
 *     <li>الخط المضمَّن في المكتبة: Noto Naskh Arabic (عادي وعريض، رخصة SIL OFL) — فالناتج واحد
 *         على كل جهاز من غير أي إعداد.</li>
 *     <li>خطوط النظام المعروفة على Windows/Linux/macOS، لو الخط المضمَّن اتشال من الـ jar.</li>
 * </ol>
 *
 * <p>خط النظام بيتستخدم كمان كاحتياطي للحروف اللي مش في الخط المختار ({@link #systemFallback()}):
 * علامة ³ مثلًا مش في Noto Naskh.</p>
 *
 * <p>لو مفيش أي خط اتلقى، المكتبة بترجع لـ Helvetica وبترمي {@link com.smart.report.ReportException}
 * برسالة واضحة أول ما يجيلها نص خارج Latin-1.</p>
 *
 * <p>بايتات الخط بتتقرأ مرة واحدة وتتخزّن، فمش بنقرأ الملف من القرص مع كل تقرير.</p>
 */
public final class PdfFontResolver {

    private PdfFontResolver() {}

    private static final Logger log = LoggerFactory.getLogger(PdfFontResolver.class);

    public static final String FONT_PROPERTY = "smart.report.pdf.font";
    public static final String BOLD_FONT_PROPERTY = "smart.report.pdf.font.bold";

    static final String REGULAR_RESOURCE = "fonts/report-font.ttf";
    static final String BOLD_RESOURCE = "fonts/report-font-bold.ttf";

    /** الخط المضمَّن، بجانب هذا الصنف في الـ jar. */
    private static final String BUNDLED_REGULAR = "fonts/NotoNaskhArabic-Regular.ttf";
    private static final String BUNDLED_BOLD = "fonts/NotoNaskhArabic-Bold.ttf";

    /** مرشحات خطوط النظام (عادي، عريض)، والأقدر على تغطية العربي أولًا. */
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

    private static final AtomicReference<Fonts> CACHE = new AtomicReference<>();
    /** خط النظام الاحتياطي: مصفوفة فاضية معناها "اتدوّر عليه ومش موجود". */
    private static final AtomicReference<byte[]> FALLBACK = new AtomicReference<>();

    /** بايتات الخط العادي والعريض، أو {@code null} في أي منهما لو مش متاح. */
    public record Fonts(byte[] regular, byte[] bold) {

        public boolean available() {
            return regular != null;
        }
    }

    /** يرجّع بايتات الخط (من الكاش بعد أول نداء)؛ {@link Fonts#available()} تكون false لو مفيش خط. */
    public static Fonts resolve() {
        Fonts cached = CACHE.get();
        if (cached != null) {
            return cached;
        }
        Fonts loaded = load();
        CACHE.compareAndSet(null, loaded);
        return CACHE.get();
    }

    /** يمسح الكاش؛ للاختبارات ولإعادة القراءة بعد تغيير خصائص النظام. */
    public static void clearCache() {
        CACHE.set(null);
        FALLBACK.set(null);
    }

    /**
     * أول خط نظام متاح، للحروف اللي مش في الخط المختار؛ {@code null} لو مفيش. بيتقرا مرة واحدة،
     * وما بيتحمّلش في المستند غير لما حرف ناقص يحتاجه.
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

    /** مورد بجانب هذا الصنف: بيتقرا من الصنف نفسه، فيشتغل على الـ classpath والـ module path. */
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
