package com.smart.report.jasper;

import net.sf.jasperreports.engine.JRCommonText;
import net.sf.jasperreports.engine.JRElement;
import net.sf.jasperreports.engine.JRExpression;
import net.sf.jasperreports.engine.JRStaticText;
import net.sf.jasperreports.engine.JRTextField;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.engine.TabStop;
import net.sf.jasperreports.engine.fill.JRFillElement;
import net.sf.jasperreports.engine.fill.JRMeasuredText;
import net.sf.jasperreports.engine.fill.JRTextMeasurer;
import net.sf.jasperreports.engine.fill.TextLineWrapper;
import net.sf.jasperreports.engine.fill.TextMeasurer;
import net.sf.jasperreports.engine.type.TextAdjustEnum;
import net.sf.jasperreports.engine.util.AbstractTextMeasurerFactory;
import net.sf.jasperreports.engine.util.JRStyledText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * The JasperReports text measurer itself, with two additions for text whose first line is taller than its box:
 * an Arabic line that almost fits is drawn, and text that would print nothing logs a warning that names it.
 * {@link JasperReportProvider} uses it for every fill, and an application that fills with
 * {@code JasperFillManager} itself can use it with the property
 * {@code net.sf.jasperreports.text.measurer.factory=com.smart.report.jasper.ArabicTextMeasurerFactory}.
 *
 * <p><b>Why:</b> JasperReports prints only the lines of text whose full height (leading + ascent + descent) fits
 * inside the box, and a box whose {@code textAdjust} property is {@code CutText} (the default) does not grow. A
 * Noto Naskh Arabic line is about 1.7 times the font size (room for diacritics), and a default-font line about
 * 1.2; so an 18-point Arabic title in a 30-point box (it needs 31) disappeared entirely without an error, while an
 * English title in the same box was fine.</p>
 *
 * <p><b>What changes:</b> text with Arabic letters, in a box that does not grow, of which no line fits, and whose
 * first line is up to {@value #FIRST_LINE_ROOM} times the box's room: that line is drawn and the box keeps its
 * size. The baseline stays inside the box (on a Noto line the ascent is about 0.63 of it), and the descenders
 * below the line (ي، ج، ع) can pass the bottom edge of the box when the box is shorter than about 1.5 times the
 * font size. This is exactly the line that would be drawn if the box were taller, so HTML and XLSX get it like the
 * PDF.</p>
 *
 * <p><b>What does not change:</b> any text of which at least one line fits (cut exactly as JasperReports cuts it),
 * text with no Arabic, a box that grows ({@code StretchHeight}) because its text continues on the next page, and a
 * box that shrinks the font ({@code ScaleFont}) because JasperReports shrinks it until it fits. A report with no
 * Arabic comes out exactly as before; the only difference is that text that gets lost (in any language) now has a
 * warning in the log.</p>
 */
public final class ArabicTextMeasurerFactory extends AbstractTextMeasurerFactory {

    private static final Logger log = LoggerFactory.getLogger(ArabicTextMeasurerFactory.class);

    /**
     * A first Arabic line is drawn when its height is up to this many times the box's room: a box of at least 1.14
     * times the font size, which is less than a default-font line needs (1.16 on Linux and 1.26 on Windows). So a
     * box that holds the Latin line holds the Arabic one too.
     */
    static final float FIRST_LINE_ROOM = 1.5f;

    /** JasperReports creates the factory itself from its name. */
    public ArabicTextMeasurerFactory() {
    }

    @Override
    public JRTextMeasurer createMeasurer(JasperReportsContext jasperReportsContext, JRCommonText text) {
        return new FirstLineMeasurer(jasperReportsContext, text);
    }

    static boolean hasArabic(CharSequence text) {
        return text.codePoints().anyMatch(ArabicFonts::isArabic);
    }

    /** One measurer per text element per fill: the warning comes once per element, not once per row. */
    private static final class FirstLineMeasurer extends TextMeasurer {

        private boolean warned;

        /** During the rescue measurement: one line only. */
        private boolean firstLineOnly;

        FirstLineMeasurer(JasperReportsContext jasperReportsContext, JRCommonText textElement) {
            super(jasperReportsContext, textElement);
        }

        /**
         * In the rescue measurement, any line after the first "does not fit", as in a box exactly as tall as the
         * first line: JasperReports goes on from here as it does with any cut text (the truncation suffix
         * {@code net.sf.jasperreports.text.truncate.suffix}, cutting at a character, {@code isParagraphCut} and
         * {@code lineBreakOffsets}), so the result holds only the first line, with its position and height.
         * Redrawing the first line for the suffix starts from the state before it (height zero), so it passes.
         */
        @Override
        protected boolean renderNextLine(TextLineWrapper lineWrapper, List<Integer> tabIndexes,
                int[] currentTabHolder, TabStop[] nextTabStopHolder, boolean[] requireNextWordHolder) {
            if (firstLineOnly && measuredState.getTextHeight() > 0) {
                return false;
            }
            return super.renderNextLine(lineWrapper, tabIndexes, currentTabHolder, nextTabStopHolder,
                    requireNextWordHolder);
        }

        @Override
        public JRMeasuredText measure(JRStyledText styledText, int remainingTextStart, int availableStretchHeight,
                boolean indentFirstLine, boolean canOverflow) {
            JRMeasuredText measured = super.measure(
                    styledText, remainingTextStart, availableStretchHeight, indentFirstLine, canOverflow);
            if (canOverflow || measured.getTextOffset() > remainingTextStart || scalesFont()) {
                return measured;
            }
            String text = styledText.getText().substring(remainingTextStart);
            if (text.isBlank()) {
                return measured;
            }

            int box = maxHeight; // ارتفاع الخانة من غير الـ padding، اتحسب في super.measure
            boolean arabic = hasArabic(text);
            int room = (int) Math.ceil(box * FIRST_LINE_ROOM);
            if (arabic && room > box) {
                // نفس القياس بمساحة أكبر لأول سطر، ومن غير أي سطر بعده: سطر بخط أصغر (styled أو html) كان ممكن
                // يدخل في المساحة الزيادة، ويروح لـ HTML و XLSX والـ PDF ما يرسمهوش في ارتفاع الخانة
                JRMeasuredText firstLine;
                firstLineOnly = true;
                try {
                    firstLine = super.measure(styledText, remainingTextStart,
                            availableStretchHeight + room - box, indentFirstLine, canOverflow);
                } finally {
                    firstLineOnly = false;
                }
                if (firstLine.getTextOffset() > remainingTextStart) {
                    if (!warned) {
                        warned = true;
                        log.warn("{} has a first line {} pt high in a box with room for {} pt: the line is drawn,"
                                        + " and can reach past the bottom of the box. An Arabic line needs about"
                                        + " 1.7 times the font size.",
                                describe(), (int) Math.ceil(firstLine.getTextHeight()), box);
                    }
                    return new InBox(firstLine, box);
                }
            }
            if (!warned) {
                warned = true;
                log.warn("{} prints nothing: its first line is higher than the {} pt of room in its box.{}",
                        describe(), box, arabic ? " An Arabic line needs about 1.7 times the font size." : "");
            }
            return measured;
        }

        private boolean scalesFont() {
            return textElement instanceof JRTextField field && field.getTextAdjust() == TextAdjustEnum.SCALE_FONT;
        }

        /**
         * The element as the template designer sees it: the report, the expression or text, and the key and
         * position in the band.
         */
        private String describe() {
            StringBuilder out = new StringBuilder();
            if (textElement instanceof JRFillElement fillElement && fillElement.getFiller() != null) {
                JasperReport report = fillElement.getFiller().getJasperReport();
                if (report != null) {
                    out.append("Jasper report \"").append(report.getName()).append("\": ");
                }
            }
            if (textElement instanceof JRTextField field) {
                JRExpression expression = field.getExpression();
                out.append("text field ").append(expression == null ? "" : expression.getText());
            } else if (textElement instanceof JRStaticText staticText) {
                String text = staticText.getText();
                out.append("static text \"")
                        .append(text.length() > 40 ? text.substring(0, 40) + "..." : text).append('"');
            } else {
                out.append("text element");
            }
            if (textElement instanceof JRElement element) {
                if (element.getKey() != null) {
                    out.append(" (key ").append(element.getKey()).append(')');
                }
                out.append(" at x=").append(element.getX()).append(", y=").append(element.getY());
            }
            return out.toString();
        }
    }

    /**
     * The first line at the box height: the element keeps its design size (it does not grow, and does not grow the
     * band). The exporters draw the line when its baseline is within that height, and the line is at most one and
     * a half times that height, so the baseline is inside.
     */
    private record InBox(JRMeasuredText line, float box) implements JRMeasuredText {

        @Override
        public float getTextHeight() {
            return Math.min(line.getTextHeight(), box);
        }

        @Override
        public float getLeadingOffset() {
            return line.getLeadingOffset();
        }

        @Override
        public float getLineSpacingFactor() {
            return line.getLineSpacingFactor();
        }

        @Override
        public float getTextWidth() {
            return line.getTextWidth();
        }

        @Override
        public float getAverageCharWidth() {
            return line.getAverageCharWidth();
        }

        @Override
        public int getTextOffset() {
            return line.getTextOffset();
        }

        @Override
        public boolean isLeftToRight() {
            return line.isLeftToRight();
        }

        @Override
        public boolean isParagraphCut() {
            return line.isParagraphCut();
        }

        @Override
        public String getTextSuffix() {
            return line.getTextSuffix();
        }

        @Override
        public short[] getLineBreakOffsets() {
            return line.getLineBreakOffsets();
        }
    }
}
