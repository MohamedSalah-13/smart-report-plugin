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
 * مقياس النص بتاع JasperReports نفسه، ومعاه حاجتين للنص اللي أول سطر فيه أطول من خانته: السطر العربي اللي قرّب
 * يدخل بيترسم، والنص اللي مش هيطلع منه حاجة بيتسجّل تحذير باسمه. {@link JasperReportProvider} بيستخدمه في كل
 * تعبئة، والبرنامج اللي بيعبّي بـ {@code JasperFillManager} بنفسه يقدر يستخدمه بالخاصية
 * {@code net.sf.jasperreports.text.measurer.factory=com.smart.report.jasper.ArabicTextMeasurerFactory}.
 *
 * <p><b>ليه:</b> JasperReports بيطبع من النص السطور اللي ارتفاعها كله (leading + ascent + descent) جوّه الخانة بس،
 * والخانة اللي الخاصية {@code textAdjust} بتاعتها {@code CutText} (الافتراضي) ما بتكبرش. سطر Noto Naskh Arabic
 * حوالي 1.7 من حجم الخط (مكان للتشكيل)، وسطر الخط الافتراضي حوالي 1.2؛ فعنوان عربي 18 نقطة في خانة 30 نقطة
 * (محتاج 31) كان بيختفي كله من غير خطأ، والعنوان الإنجليزي في نفس الخانة سليم.</p>
 *
 * <p><b>اللي بيتغيّر:</b> نص فيه حروف عربية، في خانة ما بتكبرش، ما دخلش منه ولا سطر، وأول سطر فيه طوله لحد
 * {@value #FIRST_LINE_ROOM} من مساحة الخانة: السطر ده بيترسم والخانة بتفضل بمقاسها. الـ baseline بيفضل جوّه الخانة
 * (سطر Noto: الـ ascent حوالي 0.63 منه)، وذيول الحروف اللي تحت السطر (ي، ج، ع) ممكن تعدّي حافة الخانة التحتانية
 * لو الخانة أقصر من حوالي 1.5 من حجم الخط. السطر ده بالظبط هو اللي كان هيترسم لو الخانة أطول، فالـ HTML و XLSX
 * بياخدوه زي الـ PDF.</p>
 *
 * <p><b>اللي ما بيتغيّرش:</b> أي نص دخل منه سطر واحد على الأقل (زي ما JasperReports بيقطعه بالظبط)، والنص اللي
 * مفيهوش عربي، والخانة اللي بتكبر ({@code StretchHeight}) لأن نصها بيكمّل في الصفحة اللي بعدها، والخانة اللي بتصغّر
 * الخط ({@code ScaleFont}) لأن JasperReports بيصغّره لحد ما يدخل. التقرير اللي مفيهوش عربي بيطلع زي ما كان بالظبط؛
 * الفرق الوحيد إن النص اللي بيضيع منه (أي لغة) بقى له تحذير في الـ log.</p>
 */
public final class ArabicTextMeasurerFactory extends AbstractTextMeasurerFactory {

    private static final Logger log = LoggerFactory.getLogger(ArabicTextMeasurerFactory.class);

    /**
     * أول سطر عربي بيترسم لو طوله لحد المرات دي من مساحة الخانة: خانة على الأقل 1.14 من حجم الخط، وده أقل من
     * اللي سطر الخط الافتراضي محتاجه (1.16 في Linux و 1.26 في Windows). يعني الخانة اللي بتشيل السطر اللاتيني
     * بتشيل العربي كمان.
     */
    static final float FIRST_LINE_ROOM = 1.5f;

    /** JasperReports بيعمل المصنع بنفسه من اسمه. */
    public ArabicTextMeasurerFactory() {
    }

    @Override
    public JRTextMeasurer createMeasurer(JasperReportsContext jasperReportsContext, JRCommonText text) {
        return new FirstLineMeasurer(jasperReportsContext, text);
    }

    static boolean hasArabic(CharSequence text) {
        return text.codePoints().anyMatch(ArabicFonts::isArabic);
    }

    /** مقياس لكل عنصر نص في كل تعبئة: التحذير مرة واحدة للعنصر، مش مرة لكل صف. */
    private static final class FirstLineMeasurer extends TextMeasurer {

        private boolean warned;

        /** وقت قياس الإنقاذ: سطر واحد بس. */
        private boolean firstLineOnly;

        FirstLineMeasurer(JasperReportsContext jasperReportsContext, JRCommonText textElement) {
            super(jasperReportsContext, textElement);
        }

        /**
         * في قياس الإنقاذ، أي سطر بعد أول سطر "ما بيدخلش"، زي خانة طولها على قد أول سطر بالظبط: JasperReports بيكمّل
         * من هنا زي ما بيعمل مع أي نص اتقطع (لاحقة القطع {@code net.sf.jasperreports.text.truncate.suffix}، والقطع
         * عند حرف، و {@code isParagraphCut} و {@code lineBreakOffsets})، فالنتيجة فيها أول سطر بس، بمكانه وارتفاعه.
         * إعادة رسم أول سطر عشان اللاحقة بتبدأ من الحالة اللي قبله (ارتفاعها صفر)، فبتعدّي.
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

        /** العنصر زي ما مصمم القالب بيشوفه: التقرير، والتعبير أو النص، والمفتاح والمكان في الـ band. */
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
     * أول سطر بارتفاع الخانة: العنصر بيفضل بمقاسه في التصميم (ما بيكبرش ولا بيكبّر الـ band). الـ exporters بترسم
     * السطر لو الـ baseline بتاعه جوّه الارتفاع ده، والسطر طوله لحد مرة ونص منه فالـ baseline جوّه.
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
