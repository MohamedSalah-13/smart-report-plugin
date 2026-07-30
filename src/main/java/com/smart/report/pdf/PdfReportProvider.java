package com.smart.report.pdf;

import com.smart.report.ColumnDefinition;
import com.smart.report.ReportException;
import com.smart.report.ReportProvider;
import com.smart.report.ReportRequest;
import com.smart.report.util.BeanPropertyReader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * يولّد تقرير PDF جدولي عام (عنوان + رأس أعمدة + صفوف بيانات، مع ترقيم صفحات تلقائي)
 * بدون الحاجة لأي قالب، انطلاقًا من أي List من الكائنات + قائمة ColumnDefinition.
 * للتقارير ذات التصميم المخصّص بصريًا استخدم JasperReportProvider بدلًا منه.
 */
public final class PdfReportProvider implements ReportProvider {

    private static final float PAGE_MARGIN = 40f;
    private static final float ROW_HEIGHT = 20f;
    private static final float TITLE_FONT_SIZE = 16f;
    private static final float HEADER_FONT_SIZE = 11f;
    private static final float CELL_FONT_SIZE = 10f;

    private final PDFont regularFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDFont boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    @Override
    public byte[] generate(ReportRequest<?> request) throws ReportException {
        List<ColumnDefinition> columns = request.columns();
        if (columns.isEmpty()) {
            throw new ReportException("At least one column is required to generate a PDF report.");
        }

        try (PDDocument document = new PDDocument()) {
            float pageHeight = PDRectangle.A4.getHeight();
            float pageWidth = PDRectangle.A4.getWidth() - 2 * PAGE_MARGIN;
            float colWidth = pageWidth / columns.size();

            PageContext ctx = newPage(document);
            ctx.y = writeTitle(ctx.stream, request.title(), pageHeight);
            ctx.y = writeRow(ctx.stream, columns, colWidth, ctx.y, boldFont, HEADER_FONT_SIZE, true);

            for (Object row : request.data()) {
                if (ctx.y < PAGE_MARGIN + ROW_HEIGHT) {
                    ctx.stream.close();
                    ctx = newPage(document);
                    ctx.y = writeRow(ctx.stream, columns, colWidth, pageHeight - PAGE_MARGIN, boldFont, HEADER_FONT_SIZE, true);
                }
                List<String> values = columns.stream()
                        .map(column -> BeanPropertyReader.readAsString(row, column.fieldName()))
                        .toList();
                ctx.y = writeRow(values, ctx.stream, colWidth, ctx.y);
            }
            ctx.stream.close();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ReportException("Failed to generate PDF report.", e);
        }
    }

    private PageContext newPage(PDDocument document) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        return new PageContext(new PDPageContentStream(document, page));
    }

    private float writeTitle(PDPageContentStream stream, String title, float pageHeight) throws IOException {
        float y = pageHeight - PAGE_MARGIN;
        if (title != null && !title.isBlank()) {
            stream.beginText();
            stream.setFont(boldFont, TITLE_FONT_SIZE);
            stream.newLineAtOffset(PAGE_MARGIN, y);
            stream.showText(title);
            stream.endText();
            y -= ROW_HEIGHT * 1.5f;
        }
        return y;
    }

    /** يكتب صف الرؤوس (عناوين الأعمدة) مع خلفية رمادية فاتحة. */
    private float writeRow(PDPageContentStream stream, List<ColumnDefinition> columns, float colWidth, float y,
                            PDFont font, float fontSize, boolean header) throws IOException {
        if (header) {
            stream.setNonStrokingColor(0.85f, 0.85f, 0.85f);
            stream.addRect(PAGE_MARGIN, y - ROW_HEIGHT, colWidth * columns.size(), ROW_HEIGHT);
            stream.fill();
            stream.setNonStrokingColor(0f, 0f, 0f);
        }

        stream.beginText();
        stream.setFont(font, fontSize);
        stream.newLineAtOffset(PAGE_MARGIN + 4, y - ROW_HEIGHT + 6);
        boolean first = true;
        for (ColumnDefinition column : columns) {
            if (!first) {
                stream.newLineAtOffset(colWidth, 0);
            }
            first = false;
            stream.showText(truncate(column.header(), colWidth - 8, font, fontSize));
        }
        stream.endText();

        drawSeparatorLine(stream, columns.size(), colWidth, y - ROW_HEIGHT);
        return y - ROW_HEIGHT;
    }

    /** يكتب صف بيانات (قيم نصية جاهزة، عمود لكل قيمة). */
    private float writeRow(List<String> values, PDPageContentStream stream, float colWidth, float y) throws IOException {
        stream.beginText();
        stream.setFont(regularFont, CELL_FONT_SIZE);
        stream.newLineAtOffset(PAGE_MARGIN + 4, y - ROW_HEIGHT + 6);
        boolean first = true;
        for (String value : values) {
            if (!first) {
                stream.newLineAtOffset(colWidth, 0);
            }
            first = false;
            stream.showText(truncate(value, colWidth - 8, regularFont, CELL_FONT_SIZE));
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

    private String truncate(String text, float maxWidth, PDFont font, float fontSize) throws IOException {
        String value = text == null ? "" : text;
        if (font.getStringWidth(value) / 1000f * fontSize <= maxWidth) {
            return value;
        }
        String ellipsis = "...";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            String candidate = builder.toString() + value.charAt(i) + ellipsis;
            if (font.getStringWidth(candidate) / 1000f * fontSize > maxWidth) {
                break;
            }
            builder.append(value.charAt(i));
        }
        return builder + ellipsis;
    }

    private static final class PageContext {
        private final PDPageContentStream stream;
        private float y;

        private PageContext(PDPageContentStream stream) {
            this.stream = stream;
        }
    }
}
