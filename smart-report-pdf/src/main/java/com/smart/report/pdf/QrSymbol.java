package com.smart.report.pdf;

import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * رمز QR متشفّر: مربعاته الغامقة، من غير المنطقة الفاضية حواليه. ZXing بيشفّر بس، والرسم في
 * {@link PageWriter} مربعات متجهة، فالرمز حاد بأي دقة طباعة.
 *
 * <p>مستوى تصحيح الأخطاء M (حوالي 15%): بيستحمل بقعة أو خدش على الورق الحراري من غير ما الرمز
 * يكبر كتير. النص الإنجليزي بس (ASCII) بيتشفّر زي ما هو، وأي نص تاني بـ UTF-8 ومعاه علامة ECI
 * اللي بتقول للقارئ إنه UTF-8 — من غيرها العربي بيطلع علامات استفهام.</p>
 */
final class QrSymbol {

    /** المنطقة الفاضية حوالين الرمز بعدد المربعات: 4، زي ما المواصفة طالبة، والقارئ بيحتاجها عشان يلاقيه. */
    static final int QUIET_ZONE = 4;

    /** {@code [صف][عمود]}، من أعلى يسار الرمز. */
    private final boolean[][] dark;

    private QrSymbol(boolean[][] dark) {
        this.dark = dark;
    }

    /**
     * @throws IllegalArgumentException لو النص فاضي أو أطول من اللي يشيله رمز QR (حوالي 2300 بايت)
     */
    static QrSymbol encode(String text) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("QR code text is required.");
        }
        Map<EncodeHintType, Object> hints = ascii(text)
                ? null
                : Map.of(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
        ByteMatrix matrix;
        try {
            matrix = Encoder.encode(text, ErrorCorrectionLevel.M, hints).getMatrix();
        } catch (WriterException e) {
            throw new IllegalArgumentException("The text does not fit in a QR code ("
                    + text.getBytes(StandardCharsets.UTF_8).length + " bytes): " + e.getMessage(), e);
        }
        boolean[][] dark = new boolean[matrix.getHeight()][matrix.getWidth()];
        for (int row = 0; row < dark.length; row++) {
            for (int column = 0; column < dark[row].length; column++) {
                dark[row][column] = matrix.get(column, row) == 1;
            }
        }
        return new QrSymbol(dark);
    }

    private static boolean ascii(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 0x7F) {
                return false;
            }
        }
        return true;
    }

    /** عدد المربعات في ضلع الرمز (21 لأصغر رمز، وبيزيد 4 مع كل إصدار). */
    int size() {
        return dark.length;
    }

    boolean dark(int row, int column) {
        return dark[row][column];
    }
}
