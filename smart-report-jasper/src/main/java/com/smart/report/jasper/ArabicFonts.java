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
 * Font extension بيدّي خط JasperReports الافتراضي حروفًا عربية: في القالب اللي ما بيسمّيش خطًا، الحروف العربية
 * بتترسم بـ Noto Naskh Arabic وكل حرف تاني بيفضل زي ما كان بالظبط. متسجّلة في
 * {@code jasperreports_extension.properties}.
 *
 * <p><b>ليه:</b> الـ PDF بيرسم كل حرف من خط مضمَّن. القالب اللي ما بيسمّيش خطًا بياخد {@value #JASPER_DEFAULT_FONT}،
 * واللي بيترسم في الـ PDF بـ Helvetica: ما فيهوش عربي، فالحروف كانت بتختفي من غير أي خطأ.</p>
 *
 * <p><b>إزاي:</b> مجموعة خطوط (font set) اسمها {@value #JASPER_DEFAULT_FONT}، فالنص اللي خطه
 * {@value #JASPER_DEFAULT_FONT} بيتقسّم: كل حرف بياخد أول عائلة في المجموعة بتقول إنها بتعرف ترسمه
 * ({@link Font#canDisplay(int)}). الأولى Noto Naskh Arabic وبتقول إنها بتعرف الحروف العربية بس
 * ({@link #isArabic(int)})، والباقي بياخد عائلات {@value #JASPER_DEFAULT_FONT} اللي بعدها، وهي خط JasperReports
 * الافتراضي نفسه: القياس بخط Java المنطقي SansSerif زي الأول، والـ PDF بخط العنصر (Helvetica) وترميزه زي الأول.
 * عائلة لكل وزن (عادي، عريض، مائل، عريض مائل) فيها الوجه ده بس، عشان JasperReports يلاقي الوزن في الوجه نفسه
 * وما يسمّكش الحرف بنفسه.</p>
 *
 * <p><b>ليه مش Noto لكل النص:</b> سطر Noto Naskh طوله حوالي 1.7 من حجم الخط (مكان للتشكيل)، و JasperReports
 * ما بيطبعش نصًا أول سطر فيه أطول من خانته. قالب إنجليزي بخانات على قد Helvetica كان هيفقد نصه في كل الصيغ.
 * والفلترة بالـ script اللي JasperReports بيوفّرها في المجموعات محتاجة ICU4J (حوالي 13 ميجا)، وكانت هتسيب الفاصلة
 * وعلامة الاستفهام العربية (حروف "مشتركة" في Unicode) لـ Helvetica اللي ما فيهوش عربي.</p>
 */
public final class ArabicFonts implements ExtensionsRegistryFactory {

    /** الخط اللي JasperReports بيرجعله لما القالب ما يسمّيش خطًا ({@code net.sf.jasperreports.default.font.name}). */
    static final String JASPER_DEFAULT_FONT = "SansSerif";

    /** العائلة المخفية للحروف العربية. القالب ما بيسمّيهاش؛ بيسمّي "Noto Naskh Arabic" (fonts.xml). */
    static final String ARABIC_LETTERS_FAMILY = "Noto Naskh Arabic (Arabic letters)";

    private static final String FONTS = "com/smart/report/jasper/fonts/";

    /** الصيغ اللي بتكتب اسم الخط بدل ما ترسمه (HTML، DOCX...) بتكتب الاسم الحقيقي، و HTML معاه بدائل. */
    private static final String EXPORT_FONT = "Noto Naskh Arabic";
    private static final String HTML_EXPORT_FONT = "'Noto Naskh Arabic', Tahoma, Arial, sans-serif";

    /**
     * كتل Unicode العربية: الحروف، والتشكيل، والأرقام الهندية، والفاصلة والفاصلة المنقوطة وعلامة الاستفهام
     * والتطويل، وصور الحروف.
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

    /** حرف من كتل Unicode العربية. */
    static boolean isArabic(int codePoint) {
        return ARABIC_BLOCKS.contains(Character.UnicodeBlock.of(codePoint));
    }

    /** ملف Noto Naskh بيقول إنه بيعرف الحروف العربية بس؛ القياس والرسم بيستخدموا الملف نفسه. */
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
     * خط Java المنطقي SansSerif بالوزن ده، للقياس زي ما JasperReports بيقيس النص اللي ما لوش خط. من غير ملف ولا
     * اسم PDF، فالـ PDF بيرجع لخط العنصر نفسه ({@code pdfFontName}، و Helvetica لو ما سمّاش).
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
     * SansSerif باسم عائلته في المجموعة، ومن غير الحروف العربية.
     *
     * <p>الاسم: JasperReports بيعتبر أي وجه اسم عائلته في Java "SansSerif" هو الخط SansSerif نفسه، فكان هيلاقيه
     * قبل المجموعة ويتخطّاها. الحروف: SansSerif في Java بيعرف يقيس العربي، و JasperReports بيدّي المقطع كله لأول
     * خط بيعرف كل حروفه؛ من غير الاستثناء ده "أحمد علي" كانت هتروح كلها لـ SansSerif (عشان المسافة) وتترسم
     * Helvetica.</p>
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
