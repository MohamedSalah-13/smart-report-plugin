package com.smart.report.pdf;

import com.google.zxing.EncodeHintType;
import com.google.zxing.oned.Code128Writer;

import java.util.Map;

/**
 * باركود Code 128 متشفّر: عرض كل خط وكل فراغ بعدد «المربعات» (أضيق خط = مربع واحد)، من غير المنطقة
 * الفاضية حواليه. ZXing بيشفّر بس، والرسم في {@link SymbolParts} مستطيلات متجهة، فالخطوط حادة بأي دقة.
 *
 * <p>Code 128 بيشيل حروف ASCII بس: الإنجليزي والأرقام والعلامات. التشفير بيختار أقصر صيغة (الأرقام
 * المتتالية بتتضغط اتنين في رمز)، فرقم الشحنة اللي أغلبه أرقام بيطلع أقصر.</p>
 */
final class BarcodeSymbol {

    /** المنطقة الفاضية على كل جنب بعدد المربعات: 10، أقل اللي المواصفة بتسمح بيه والقارئ محتاجه. */
    static final int QUIET_ZONE = 10;

    private final String text;
    /** {@code true} = خط غامق، لكل مربع من الشمال لليمين. */
    private final boolean[] bars;

    private BarcodeSymbol(String text, boolean[] bars) {
        this.text = text;
        this.bars = bars;
    }

    /**
     * @throws IllegalArgumentException لو النص فاضي، أو فيه حرف برّه ASCII المطبوع (حرف عربي، حرف بعلامة
     *                                  زي {@code é}، أو محرف تحكم)
     */
    static BarcodeSymbol encode(String text) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("Barcode text is required.");
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x20 || c > 0x7E) {
                throw new IllegalArgumentException("A Code 128 barcode holds English letters, digits and signs only; \""
                        + text + "\" has '" + c + "' (U+" + String.format("%04X", (int) c) + ").");
            }
        }
        boolean[] bars = new Code128Writer().encode(text, Map.of(EncodeHintType.CODE128_COMPACT, Boolean.TRUE));
        return new BarcodeSymbol(text, bars);
    }

    String text() {
        return text;
    }

    /** عدد المربعات من أول خط لآخر خط. */
    int modules() {
        return bars.length;
    }

    boolean dark(int module) {
        return bars[module];
    }
}
