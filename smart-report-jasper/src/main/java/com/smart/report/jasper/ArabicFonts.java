package com.smart.report.jasper;

import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.engine.JRPropertiesMap;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.engine.fonts.FontFamily;
import net.sf.jasperreports.engine.fonts.FontSet;
import net.sf.jasperreports.engine.fonts.SimpleFontFace;
import net.sf.jasperreports.engine.fonts.SimpleFontFamily;
import net.sf.jasperreports.engine.fonts.SimpleFontSet;
import net.sf.jasperreports.engine.fonts.SimpleFontSetFamily;
import net.sf.jasperreports.extensions.ExtensionsRegistry;
import net.sf.jasperreports.extensions.ExtensionsRegistryFactory;

import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A font extension that gives the JasperReports default font Arabic letters: in a template that names no font,
 * Arabic letters are drawn with Noto Naskh Arabic, and every other character stays exactly as it was. Registered
 * in {@code jasperreports_extension.properties}.
 *
 * <p><b>Why:</b> the PDF draws each character from an embedded font. A template that names no font gets
 * {@value #JASPER_DEFAULT_FONT}, which is drawn in the PDF with Helvetica. Helvetica has no Arabic, so the letters
 * disappeared without any error.</p>
 *
 * <p><b>How:</b> a font set named {@value #JASPER_DEFAULT_FONT}, so text in the {@value #JASPER_DEFAULT_FONT}
 * font is split: each character takes the first family in the set that says it can draw it
 * ({@link Font#canDisplay(int)}). The first is Noto Naskh Arabic, which says it can draw Arabic letters only
 * ({@link #isArabic(int)}). The rest go to the {@value #JASPER_DEFAULT_FONT} families after it, which are the
 * JasperReports default font itself: measurement uses the Java logical font SansSerif, as before, and the PDF uses
 * the element's font (Helvetica) and its encoding, as before. There is one family per weight (regular, bold,
 * italic, bold italic) holding only that face, so JasperReports finds the weight in the face itself and does not
 * fake it by thickening the character.</p>
 *
 * <p><b>Why not Noto for all text:</b> a Noto Naskh line is about 1.7 times the font size (room for diacritics),
 * and JasperReports prints no text whose first line is taller than its box. An English template with boxes sized
 * for Helvetica would lose its text in every format. The script filtering that JasperReports offers in font sets
 * needs ICU4J (about 13 MB), and it would leave the Arabic comma and question mark ("common" characters in
 * Unicode) to Helvetica, which has no Arabic.</p>
 */
public final class ArabicFonts implements ExtensionsRegistryFactory {

    /**
     * The font JasperReports falls back to when a template names none
     * ({@code net.sf.jasperreports.default.font.name}).
     */
    static final String JASPER_DEFAULT_FONT = "SansSerif";

    /** The hidden family for Arabic letters. A template does not name it; it names "Noto Naskh Arabic" (fonts.xml). */
    static final String ARABIC_LETTERS_FAMILY = "Noto Naskh Arabic (Arabic letters)";

    private static final String FONTS = "com/smart/report/jasper/fonts/";

    /**
     * Formats that write the font name instead of drawing it (HTML, DOCX...) write the real name, and HTML gets
     * fallbacks with it.
     */
    private static final String EXPORT_FONT = "Noto Naskh Arabic";
    private static final String HTML_EXPORT_FONT = "'Noto Naskh Arabic', Tahoma, Arial, sans-serif";

    /**
     * The Arabic Unicode blocks: the letters, the diacritics, the Arabic-Indic digits, the comma, semicolon,
     * question mark and tatweel, and the presentation forms.
     */
    private static final Set<Character.UnicodeBlock> ARABIC_BLOCKS = Set.of(
            Character.UnicodeBlock.ARABIC,
            Character.UnicodeBlock.ARABIC_SUPPLEMENT,
            Character.UnicodeBlock.ARABIC_EXTENDED_A,
            Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A,
            Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B);

    @Override
    public ExtensionsRegistry createRegistry(String registryId, JRPropertiesMap properties) {
        JasperReportsContext context = DefaultJasperReportsContext.getInstance();
        List<FontFamily> families = new ArrayList<>();
        SimpleFontSet set = new SimpleFontSet();
        set.setName(JASPER_DEFAULT_FONT);

        SimpleFontFamily arabic = new SimpleFontFamily(context);
        arabic.setName(ARABIC_LETTERS_FAMILY);
        arabic.setVisible(false);
        arabic.setNormalFace(new ArabicLettersFace(context, FONTS + "NotoNaskhArabic-Regular.ttf"));
        arabic.setBoldFace(new ArabicLettersFace(context, FONTS + "NotoNaskhArabic-Bold.ttf"));
        arabic.setPdfEncoding("Identity-H");
        arabic.setPdfEmbedded(true);
        arabic.setDefaultExportFont(EXPORT_FONT);
        arabic.setExportFonts(Map.of("net.sf.jasperreports.html", HTML_EXPORT_FONT));
        families.add(arabic);
        set.addFamily(member(ARABIC_LETTERS_FAMILY, false));

        // من غير ترميز ولا تضمين: الـ PDF بياخد خط العنصر وترميزه، زي ما كان قبل المجموعة
        String[] weights = {"regular", "bold", "italic", "bold italic"};
        for (int style = Font.PLAIN; style <= (Font.BOLD | Font.ITALIC); style++) {
            String name = JASPER_DEFAULT_FONT + " (" + weights[style] + ")";
            SimpleFontFamily family = new SimpleFontFamily(context);
            family.setName(name);
            family.setVisible(false);
            DefaultFontFace face = new DefaultFontFace(context, style, name);
            switch (style) {
                case Font.PLAIN -> family.setNormalFace(face);
                case Font.BOLD -> family.setBoldFace(face);
                case Font.ITALIC -> family.setItalicFace(face);
                default -> family.setBoldItalicFace(face);
            }
            family.setDefaultExportFont(JASPER_DEFAULT_FONT);
            families.add(family);
            // الحرف اللي ولا خط في المجموعة بيعرفه بيروح للـ primary: الخط الافتراضي العادي، مش Noto
            set.addFamily(member(name, style == Font.PLAIN));
        }

        List<FontFamily> allFamilies = List.copyOf(families);
        List<FontSet> sets = List.of(set);
        return new ExtensionsRegistry() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> List<T> getExtensions(Class<T> extensionType) {
                if (FontFamily.class.equals(extensionType)) {
                    return (List<T>) allFamilies;
                }
                if (FontSet.class.equals(extensionType)) {
                    return (List<T>) sets;
                }
                return null;
            }
        };
    }

    private static SimpleFontSetFamily member(String familyName, boolean primary) {
        SimpleFontSetFamily member = new SimpleFontSetFamily();
        member.setFamilyName(familyName);
        member.setPrimary(primary);
        return member;
    }

    /** A character from the Arabic Unicode blocks. A character with no block (U+2FE0, for example) is not Arabic. */
    static boolean isArabic(int codePoint) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
        // Set.of بيرمي NullPointerException لو اتسأل عن null
        return block != null && ARABIC_BLOCKS.contains(block);
    }

    /** The Noto Naskh file, claiming Arabic letters only; measuring and drawing use the same file. */
    private static final class ArabicLettersFace extends SimpleFontFace {

        private Font arabicOnly;

        ArabicLettersFace(JasperReportsContext context, String ttf) {
            super(context);
            setTtf(ttf, false); // الملف بيتحمّل أول ما نص يحتاجه، مش وقت تحميل الـ extensions
        }

        @Override
        public synchronized Font getFont() {
            if (arabicOnly == null) {
                loadFont();
                arabicOnly = new ArabicLettersFont(super.getFont());
            }
            return arabicOnly;
        }
    }

    private static final class ArabicLettersFont extends Font {

        private static final long serialVersionUID = 1L;

        ArabicLettersFont(Font font) {
            super(font);
        }

        @Override
        public boolean canDisplay(int codePoint) {
            return isArabic(codePoint) && super.canDisplay(codePoint);
        }

        @Override
        public boolean canDisplay(char c) {
            return canDisplay((int) c);
        }
    }

    /**
     * The Java logical font SansSerif in this weight, for measuring the way JasperReports measures text with no
     * font. It has no file and no PDF name, so the PDF falls back to the element's own font ({@code pdfFontName},
     * and Helvetica if it names none).
     */
    private static final class DefaultFontFace extends SimpleFontFace {

        private final Font font;

        DefaultFontFace(JasperReportsContext context, int style, String family) {
            super(context);
            this.font = new DefaultFont(new Font(JASPER_DEFAULT_FONT, style, 10), family);
        }

        @Override
        public Font getFont() {
            return font;
        }
    }

    /**
     * SansSerif under its family name in the set, and without the Arabic letters.
     *
     * <p>The name: JasperReports treats any face whose Java family name is "SansSerif" as the SansSerif font
     * itself, so it would find it before the set and skip the set. The letters: Java's SansSerif can measure
     * Arabic, and JasperReports gives the whole run to the first font that knows all its characters; without this
     * exclusion, "أحمد علي" would all go to SansSerif (because of the space) and be drawn in Helvetica.</p>
     */
    private static final class DefaultFont extends Font {

        private static final long serialVersionUID = 1L;

        private final String family;

        DefaultFont(Font font, String family) {
            super(font);
            this.family = family;
        }

        @Override
        public String getFamily() {
            return family;
        }

        @Override
        public boolean canDisplay(int codePoint) {
            return !isArabic(codePoint) && super.canDisplay(codePoint);
        }

        @Override
        public boolean canDisplay(char c) {
            return canDisplay((int) c);
        }
    }
}
