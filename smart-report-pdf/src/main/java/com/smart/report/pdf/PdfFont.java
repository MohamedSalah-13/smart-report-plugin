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
 * خط مستند واحد (عادي وعريض) بدل الخط العام للبرنامج كله — فاتورة بخط الشركة وكشف بالخط المضمَّن
 * في نفس البرنامج. المستند اللي ما اتحدّدلوش خط بياخد الخط العام زي ما هو ({@link PdfFontResolver}:
 * {@code -Dsmart.report.pdf.font} أو Noto Naskh Arabic المضمَّن).
 *
 * <pre>{@code
 * PdfFont amiri = PdfFont.of(Path.of("fonts/Amiri-Regular.ttf"), Path.of("fonts/Amiri-Bold.ttf"));
 * PdfDocument.rtl(Paper.A4).font(amiri).title("فاتورة").save(Path.of("invoice.pdf"));
 * }</pre>
 *
 * <p><b>الملف:</b> خط TrueType ({@code .ttf}، أو {@code .otf} بحدود TrueType) مسموح بتضمينه. مجموعة الخطوط
 * ({@code .ttc}) و OpenType بحدود CFF (أغلب ملفات {@code .otf}) مش مدعومين في PDFBox، فبيترفضوا هنا برسالة
 * بدل ما يفشلوا وقت الطباعة. العربي بيترسم بصور الحروف الجاهزة (U+FB50–U+FDFF و U+FE70–U+FEFF)، فخط المستند
 * العربي لازم يكون فيه الصور دي — Noto Naskh Arabic و Amiri و Arial و Tahoma فيهم. خط من غيرها مقبول (لمستند
 * لاتيني مثلًا)، بس العربي فيه بيترسم من خط النظام وبيتسجّل تحذير في الـ log أول مرة.</p>
 *
 * <p>الحرف اللي مش في الخط بيتدوّر عليه في الخطوط الاحتياطية زي أي مستند (شوف
 * {@link PdfDocument#failOnMissingGlyphs(boolean)}).</p>
 *
 * <p>البايتات بتتقرا وتتفحص مرة واحدة وقت الإنشاء؛ الكائن ثابت وآمن للمشاركة بين المستندات والـ threads،
 * فاعمله مرة واحدة واستخدمه في كل مستند.</p>
 */
public final class PdfFont {

    private final PdfFontResolver.Fonts bytes;
    private final String name;

    private PdfFont(PdfFontResolver.Fonts bytes, String name) {
        this.bytes = bytes;
        this.name = name;
    }

    /** خط عادي من ملف؛ النص العريض بيترسم منه بتسميك مصطنع. */
    public static PdfFont of(Path regular) throws ReportException {
        return of(regular, null);
    }

    /**
     * خط عادي وعريض من ملفين.
     *
     * @param bold {@code null} (أو نفس ملف العادي) معناه النص العريض بيترسم من العادي بتسميك مصطنع
     * @throws ReportException لو ملف منهم ما اتقراش، أو مش خط TrueType ينفع يتضمّن في PDF
     */
    public static PdfFont of(Path regular, Path bold) throws ReportException {
        Objects.requireNonNull(regular, "regular");
        return create(read(regular), regular.toString(), bold == null ? null : read(bold),
                bold == null ? null : bold.toString());
    }

    /** خط عادي من بايتاته — مورد على الـ classpath مثلًا؛ النص العريض بيترسم منه بتسميك مصطنع. */
    public static PdfFont of(byte[] regular) throws ReportException {
        return of(regular, null);
    }

    /**
     * خط عادي وعريض من بايتاتهم. البايتات بتتنسخ، فتغييرها بعدين ما بيأثرش.
     *
     * @param bold {@code null} معناه النص العريض بيترسم من العادي بتسميك مصطنع
     * @throws ReportException لو البايتات مش خط TrueType ينفع يتضمّن في PDF
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

    /** يفحص إن البايتات خط PDFBox يقدر يضمّنه ويرسم بيه حروف Unicode، ويرجّع اسمه. */
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

    /** اسم الخط العادي زي ما هو مكتوب جوّه الملف (اسم PostScript) — {@code Amiri-Regular} مثلًا. */
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
