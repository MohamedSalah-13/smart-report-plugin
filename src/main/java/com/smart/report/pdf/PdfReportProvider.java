package com.smart.report.pdf;

import com.smart.report.ColumnDefinition;
import com.smart.report.ReportException;
import com.smart.report.ReportProvider;
import com.smart.report.ReportRequest;
import com.smart.report.text.ArabicTextShaper;
import com.smart.report.util.BeanPropertyReader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * يولّد تقرير PDF جدولي عام (عنوان + رأس أعمدة + صفوف بيانات، مع تقسيم تلقائي للصفحات وتكرار
 * رأس الأعمدة في كل صفحة) بدون الحاجة لأي قالب، انطلاقًا من أي List من الكائنات + قائمة ColumnDefinition.
 * للتقارير ذات التصميم المخصّص بصريًا استخدم JasperReportProvider بدلًا منه.
 *
 * <p><b>النصوص غير اللاتينية:</b> بيحاول يحمّل خط TrueType يدعم Unicode عبر {@link PdfFontResolver}،
 * وبيمرّر العربي على {@link ArabicTextShaper} عشان الحروف تتصل وتترتب من اليمين لليسار صح.
 * لو مفيش خط متاح بيرجع لـ Helvetica وبيرمي {@link ReportException} برسالة واضحة أول ما يجيله
 * نص خارج Latin-1، بدل ما يرمي {@code IllegalArgumentException} غير متوقّعة.</p>
 */
public final class PdfReportProvider implements ReportProvider {

    private static final float PAGE_MARGIN = 40f;
    private static final float ROW_HEIGHT = 20f;
    private static final float TITLE_FONT_SIZE = 16f;
    private static final float HEADER_FONT_SIZE = 11f;
    private static final float CELL_FONT_SIZE = 10f;

    @Override
    public byte[] generate(ReportRequest<?> request) throws ReportException {
        List<ColumnDefinition> columns = request.columns();
        if (columns.isEmpty()) {
            throw new ReportException("At least one column is required to generate a PDF report.");
        }

        try (PDDocument document = new PDDocument()) {
            Fonts fonts = loadFonts(document);
            float pageHeight = PDRectangle.A4.getHeight();
            float pageWidth = PDRectangle.A4.getWidth() - 2 * PAGE_MARGIN;
            float colWidth = pageWidth / columns.size();

            PageContext ctx = newPage(document);
            try {
                ctx.y = writeTitle(ctx.stream, fonts, request.title(), pageHeight);
                ctx.y = writeHeaderRow(ctx.stream, fonts, columns, colWidth, ctx.y);

                for (Object row : request.data()) {
                    if (ctx.y < PAGE_MARGIN + ROW_HEIGHT) {
                        ctx.stream.close();
                        ctx = newPage(document);
                        ctx.y = writeHeaderRow(ctx.stream, fonts, columns, colWidth, pageHeight - PAGE_MARGIN);
                    }
                    List<String> values = new ArrayList<>(columns.size());
                    for (ColumnDefinition column : columns) {
                        values.add(fonts.display(BeanPropertyReader.readAsString(row, column.fieldName())));
                    }
                    ctx.y = writeDataRow(ctx.stream, fonts, values, colWidth, ctx.y);
                }
            } finally {
                // لازم يتقفل حتى لو رمينا في نص الصفحة، وإلا الـ content stream يفضل مفتوح
                ctx.stream.close();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ReportException("Failed to generate PDF report.", e);
        }
    }

    private Fonts loadFonts(PDDocument document) throws IOException {
        PdfFontResolver.Fonts bytes = PdfFontResolver.resolve();
        if (bytes.available()) {
            return new Fonts(
                    PDType0Font.load(document, new ByteArrayInputStream(bytes.regular()), true),
                    PDType0Font.load(document, new ByteArrayInputStream(bytes.bold()), true),
                    true);
        }
        return new Fonts(
                new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD),
                false);
    }

    private PageContext newPage(PDDocument document) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        return new PageContext(new PDPageContentStream(document, page));
    }

    private float writeTitle(PDPageContentStream stream, Fonts fonts, String rawTitle, float pageHeight)
            throws IOException, ReportException {
        float y = pageHeight - PAGE_MARGIN;
        String title = fonts.display(rawTitle);
        if (!title.isBlank()) {
            fonts.width(fonts.bold(), title, TITLE_FONT_SIZE); // يتحقق إن الخط يقدر يرسم النص
            stream.beginText();
            stream.setFont(fonts.bold(), TITLE_FONT_SIZE);
            stream.newLineAtOffset(PAGE_MARGIN, y);
            stream.showText(title);
            stream.endText();
            y -= ROW_HEIGHT * 1.5f;
        }
        return y;
    }

    /** يكتب صف الرؤوس (عناوين الأعمدة) مع خلفية رمادية فاتحة. */
    private float writeHeaderRow(PDPageContentStream stream, Fonts fonts, List<ColumnDefinition> columns,
                                 float colWidth, float y) throws IOException, ReportException {
        stream.setNonStrokingColor(0.85f, 0.85f, 0.85f);
        stream.addRect(PAGE_MARGIN, y - ROW_HEIGHT, colWidth * columns.size(), ROW_HEIGHT);
        stream.fill();
        stream.setNonStrokingColor(0f, 0f, 0f);

        List<String> headers = new ArrayList<>(columns.size());
        for (ColumnDefinition column : columns) {
            headers.add(fonts.display(column.header()));
        }
        return writeCells(stream, fonts, headers, colWidth, y, fonts.bold(), HEADER_FONT_SIZE);
    }

    /** يكتب صف بيانات (قيم نصية جاهزة للرسم، عمود لكل قيمة). */
    private float writeDataRow(PDPageContentStream stream, Fonts fonts, List<String> values, float colWidth, float y)
            throws IOException, ReportException {
        return writeCells(stream, fonts, values, colWidth, y, fonts.regular(), CELL_FONT_SIZE);
    }

    private float writeCells(PDPageContentStream stream, Fonts fonts, List<String> values, float colWidth, float y,
                             PDFont font, float fontSize) throws IOException, ReportException {
        stream.beginText();
        stream.setFont(font, fontSize);
        stream.newLineAtOffset(PAGE_MARGIN + 4, y - ROW_HEIGHT + 6);
        boolean first = true;
        for (String value : values) {
            if (!first) {
                stream.newLineAtOffset(colWidth, 0);
            }
            first = false;
            stream.showText(truncate(fonts, value, colWidth - 8, font, fontSize));
        }
        stream.endText();

        drawSeparatorLine(stream, values.size(), colWidth, y - ROW_HEIGHT);
        return y - ROW_HEIGHT;
    }

    private void drawSeparatorLine(PDPageContentStream stream, int columnCount, float colWidth, float y) throws IOException {
        stream.setStrokingColor(0.75f, 0.75f, 0.75f);
        stream.moveTo(PAGE_MARGIN, y);
        stream.lineTo(PAGE_MARGIN + colWidth * columnCount, y);
        stream.stroke();
        stream.setStrokingColor(0f, 0f, 0f);
    }

    private String truncate(Fonts fonts, String text, float maxWidth, PDFont font, float fontSize)
            throws IOException, ReportException {
        String value = text == null ? "" : text;
        if (fonts.width(font, value, fontSize) <= maxWidth) {
            return value;
        }
        String ellipsis = "...";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            String candidate = builder.toString() + value.charAt(i) + ellipsis;
            if (fonts.width(font, candidate, fontSize) > maxWidth) {
                break;
            }
            builder.append(value.charAt(i));
        }
        return builder + ellipsis;
    }

    /**
     * الخطوط المستخدمة في مستند واحد، ومعها تجهيز النصوص قبل الرسم.
     *
     * @param unicode هل الخط المحمّل يدعم Unicode (TrueType) ولا هو Helvetica المدمج (Latin-1 فقط)؟
     */
    private record Fonts(PDFont regular, PDFont bold, boolean unicode) {

        /** ينظّف النص ويجهّزه للرسم: محارف التحكم لمسافات، وتشكيل/ترتيب العربي لو الخط يسمح. */
        String display(String raw) {
            String text = sanitize(raw);
            if (unicode) {
                text = ArabicTextShaper.forDisplay(text);
            }
            return stripFormatChars(text);
        }

        /** عرض النص بالنقاط، مع تحويل فشل الترميز لـ {@link ReportException} برسالة مفيدة. */
        float width(PDFont font, String text, float fontSize) throws IOException, ReportException {
            try {
                return font.getStringWidth(text) / 1000f * fontSize;
            } catch (IllegalArgumentException e) {
                throw new ReportException(missingGlyphMessage(font, text), e);
            }
        }

        private String missingGlyphMessage(PDFont font, String text) {
            StringBuilder missing = new StringBuilder();
            int shown = 0;
            for (int i = 0; i < text.length() && shown < 5; i++) {
                char c = text.charAt(i);
                try {
                    font.getStringWidth(String.valueOf(c));
                } catch (Exception e) {
                    missing.append(String.format("U+%04X ", (int) c));
                    shown++;
                }
            }
            String base = "The PDF font in use (" + font.getName() + ") has no glyph for: " + missing.toString().trim() + ".";
            if (unicode) {
                return base + " Choose a font that covers this script via -D" + PdfFontResolver.FONT_PROPERTY + "=/path/to/font.ttf";
            }
            return base + " Only the built-in Standard-14 fonts were available, and they support Latin-1 only."
                    + " Set -D" + PdfFontResolver.FONT_PROPERTY + "=/path/to/font.ttf"
                    + " (optionally -D" + PdfFontResolver.BOLD_FONT_PROPERTY + "=/path/to/font-bold.ttf),"
                    + " or put " + PdfFontResolver.REGULAR_RESOURCE + " on the classpath.";
        }

        /** أسطر جديدة وtabs بترمي في PDFBox، فبتتحوّل لمسافات بدل ما توقّف التقرير كله. */
        private static String sanitize(String raw) {
            if (raw == null || raw.isEmpty()) {
                return "";
            }
            StringBuilder out = new StringBuilder(raw.length());
            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
                out.append(Character.isISOControl(c) ? ' ' : c);
            }
            return out.toString();
        }

        /** محارف التحكم في الاتجاه خلصت شغلها بعد الـ bidi ومش موجودة في أغلب الخطوط. */
        private static String stripFormatChars(String text) {
            StringBuilder out = new StringBuilder(text.length());
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (Character.getType(c) != Character.FORMAT) {
                    out.append(c);
                }
            }
            return out.toString();
        }
    }

    private static final class PageContext {
        private final PDPageContentStream stream;
        private float y;

        private PageContext(PDPageContentStream stream) {
            this.stream = stream;
        }
    }
}
