package com.smart.report.pdf;

import com.google.zxing.oned.EAN13Writer;
import com.smart.report.util.DocumentCode;

/**
 * باركود EAN-13 متشفّر: الـ 95 مربع من أول خط حارس لآخر واحد، من غير المنطقة الفاضية. ده الباركود اللي على
 * المنتجات في المحلات، والقارئ بيقراه من أي اتجاه.
 *
 * <p>12 رقم بتاخد خانة التحقق محسوبة (GS1 modulo 10)، و13 رقم بتتراجع خانتها وبتترفض لو غلط: رقم اتكتب غلط
 * يطبع باركود لمنتج تاني.</p>
 */
final class Ean13Symbol {

    /** المربعات من أول خط لآخر خط. */
    static final int MODULES = 95;
    /** المنطقة الفاضية الشمال بعدد المربعات (أول رقم مكتوب فيها). */
    static final int QUIET_LEFT = 11;
    /** المنطقة الفاضية اليمين. */
    static final int QUIET_RIGHT = 7;

    private final String digits;
    private final boolean[] bars;

    private Ean13Symbol(String digits, boolean[] bars) {
        this.digits = digits;
        this.bars = bars;
    }

    /**
     * @throws IllegalArgumentException لو مش 12 أو 13 رقم لاتيني، أو خانة تحقق الـ 13 غلط
     */
    static Ean13Symbol encode(String text) {
        if (text == null || !text.matches("[0-9]{12,13}")) {
            throw new IllegalArgumentException("An EAN-13 barcode needs 12 digits, or 13 with the check digit: \""
                    + text + "\"");
        }
        int check = DocumentCode.checkDigit(text.substring(0, 12));
        if (text.length() == 13 && text.charAt(12) - '0' != check) {
            throw new IllegalArgumentException("The EAN-13 check digit of " + text + " should be " + check
                    + ": the number was probably typed wrong.");
        }
        String digits = text.length() == 13 ? text : text + check;
        return new Ean13Symbol(digits, new EAN13Writer().encode(digits));
    }

    /** هل النص ده EAN-13 صحيح بخانة تحققه؟ */
    static boolean valid(String text) {
        return text != null && text.matches("[0-9]{13}")
                && DocumentCode.checkDigit(text.substring(0, 12)) == text.charAt(12) - '0';
    }

    /** الـ 13 رقم بخانة التحقق. */
    String digits() {
        return digits;
    }

    boolean dark(int module) {
        return bars[module];
    }

    /** الخطوط الحارسة (البداية والنص والآخر) بتنزل أطول من الباقي، والأرقام بينها. */
    static boolean guard(int module) {
        return module < 3 || module >= 45 && module < 50 || module >= 92;
    }
}
