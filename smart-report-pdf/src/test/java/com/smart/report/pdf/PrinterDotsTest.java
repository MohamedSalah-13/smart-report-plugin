package com.smart.report.pdf;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.oned.Code128Reader;
import com.google.zxing.qrcode.QRCodeReader;
import com.smart.report.ReportException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PdfDocument#printerDpi}: الصفحة بتترسم بدقة الطابعة نفسها، وكل خط وفراغ في الباركود وكل مربع في QR لازم
 * يطلع عدد صحيح من البكسلات، نفس العدد للمربع الواحد في الرمز كله.
 */
class PrinterDotsTest {

    /** الصفحة الأولى بدقة {@code dpi}، بكسل غامق = {@code true}. */
    private static boolean[][] render(byte[] pdf, int dpi) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            BufferedImage image = new PDFRenderer(document).renderImageWithDPI(0, dpi, ImageType.GRAY);
            boolean[][] dark = new boolean[image.getHeight()][image.getWidth()];
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    dark[y][x] = (image.getRGB(x, y) & 0xFF) < 128;
                }
            }
            return dark;
        }
    }

    private static BufferedImage image(boolean[][] dark) {
        BufferedImage image = new BufferedImage(dark[0].length, dark.length, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < dark.length; y++) {
            for (int x = 0; x < dark[y].length; x++) {
                image.setRGB(x, y, dark[y][x] ? 0 : 0xFFFFFF);
            }
        }
        return image;
    }

    private static BinaryBitmap bitmap(boolean[][] dark) {
        BufferedImage image = image(dark);
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        return new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels)));
    }

    /** أطوال الغامق والفاتح ورا بعض في الصف، من أول بكسل غامق لآخر واحد. */
    private static List<Integer> runs(boolean[] row) {
        int first = 0;
        while (first < row.length && !row[first]) {
            first++;
        }
        int last = row.length - 1;
        while (last >= 0 && !row[last]) {
            last--;
        }
        List<Integer> runs = new ArrayList<>();
        int x = first;
        while (x <= last) {
            int start = x;
            boolean colour = row[x];
            while (x <= last && row[x] == colour) {
                x++;
            }
            runs.add(x - start);
        }
        return runs;
    }

    /** الصف اللي فيه أكتر تغييرات: نص الخطوط، مش النص المكتوب تحتها. */
    private static boolean[] barsRow(boolean[][] dark) {
        boolean[] best = dark[0];
        int most = -1;
        for (boolean[] row : dark) {
            int changes = runs(row).size();
            if (changes > most) {
                most = changes;
                best = row;
            }
        }
        return best;
    }

    private static int dotsOf(float millimetres, int dpi) {
        return Math.round(millimetres / 25.4f * dpi);
    }

    @ParameterizedTest
    @ValueSource(ints = {203, 300})
    void barcodeBarsAreAWholeNumberOfDots(int dpi) throws Exception {
        String code = "SHP-2026-0042";
        byte[] pdf = PdfDocument.rtl(Paper.ROLL_80).printerDpi(dpi).barcode(code).toBytes();
        boolean[][] dark = render(pdf, dpi);
        List<Integer> runs = runs(barsRow(dark));
        int module = dotsOf(0.33f, dpi);
        assertEquals(module, runs.stream().mapToInt(Integer::intValue).min().orElseThrow(), "أضيق خط");
        for (int run : runs) {
            assertEquals(0, run % module, "every bar and space is a whole number of modules: " + runs);
        }
        BarcodeSymbol symbol = BarcodeSymbol.encode(code);
        assertEquals(symbol.modules() * module, runs.stream().mapToInt(Integer::intValue).sum());
        assertEquals(code, new Code128Reader().decode(bitmap(dark), Map.of(DecodeHintType.TRY_HARDER, true)).getText());
    }

    /** من غير الدقة، 0.33 مم على 203 = 2.64 نقطة: الخطوط بتطلع 2 و 3 بكسل. ده اللي الخيار بيصلّحه. */
    @Test
    void withoutTheResolutionBarsAreUneven() throws Exception {
        byte[] pdf = PdfDocument.rtl(Paper.ROLL_80).barcode("SHP-2026-0042").toBytes();
        List<Integer> runs = runs(barsRow(render(pdf, 203)));
        int narrowest = runs.stream().mapToInt(Integer::intValue).min().orElseThrow();
        assertTrue(runs.stream().anyMatch(run -> run % narrowest != 0), runs.toString());
    }

    @ParameterizedTest
    @ValueSource(ints = {203, 300})
    void qrModulesAreAWholeNumberOfDots(int dpi) throws Exception {
        String text = "https://example.com/i/42";
        byte[] pdf = PdfDocument.rtl(Paper.ROLL_80).printerDpi(dpi).qrCode(text).toBytes();
        boolean[][] dark = render(pdf, dpi);
        QrSymbol symbol = QrSymbol.encode(text);
        int module = Math.round(PdfDocument.DEFAULT_QR_MM / symbol.size() / 25.4f * dpi);

        // أول صف وعمود غامقين: ركن الرمز. كل مربع هناك لازم لون واحد على مساحته كلها.
        int top = -1;
        int left = Integer.MAX_VALUE;
        for (int y = 0; y < dark.length; y++) {
            for (int x = 0; x < dark[y].length; x++) {
                if (dark[y][x]) {
                    top = top < 0 ? y : top;
                    left = Math.min(left, x);
                }
            }
        }
        for (int row = 0; row < symbol.size(); row++) {
            for (int column = 0; column < symbol.size(); column++) {
                for (int dy = 0; dy < module; dy++) {
                    for (int dx = 0; dx < module; dx++) {
                        assertEquals(symbol.dark(row, column), dark[top + row * module + dy][left + column * module + dx],
                                "module " + row + "," + column + " pixel " + dx + "," + dy);
                    }
                }
            }
        }
        assertEquals(text, new QRCodeReader().decode(bitmap(dark), Map.of(DecodeHintType.TRY_HARDER, true)).getText());
    }

    /** على لفة 58 مم الكود الطويل بيقل نقطة نقطة لحد نقطتين (0.25 مم على 203)، وأطول من كده بيفشل. */
    @Test
    void aBarcodeThatDoesNotFitIsRefusedNotShrunk() throws Exception {
        // 14 حرف: على 58 مم ما بيكفّوش 3 نقط، فبيطلعوا نقطتين.
        String fits = "AB-CDEF-GHIJ-K";
        List<Integer> runs = runs(barsRow(render(PdfDocument.rtl(Paper.ROLL_58).printerDpi(203).barcode(fits)
                .toBytes(), 203)));
        assertEquals(2, runs.stream().mapToInt(Integer::intValue).min().orElseThrow());
        for (int run : runs) {
            assertEquals(0, run % 2, runs.toString());
        }

        ReportException refused = assertThrows(ReportException.class, () -> PdfDocument.rtl(Paper.ROLL_58)
                .printerDpi(203).barcode("ABCDEFGHIJKLMNOPQRSTUVWXYZ").toBytes());
        assertTrue(refused.getMessage().contains("203 dpi"), refused.getMessage());
    }

    /** على 300 أضيق خط 3 نقط (0.25 مم)، مش نقطتين (0.17 مم). */
    @Test
    void barsNeverGoBelowAQuarterMillimetre() {
        assertThrows(ReportException.class, () -> PdfDocument.rtl(Paper.ROLL_58).printerDpi(300)
                .barcode("ABCDEFGHIJKLMNOPQR").toBytes());
    }

    /** من غير الدقة أو بصفر، المستند بالظبط زي ما كان. */
    @Test
    void zeroMeansNone() throws Exception {
        PdfDocument plain = PdfDocument.rtl(Paper.A5).barcode("INV-42").qrCode("INV-42");
        PdfDocument zero = PdfDocument.rtl(Paper.A5).printerDpi(0).barcode("INV-42").qrCode("INV-42");
        assertTrue(java.util.Arrays.deepEquals(render(plain.toBytes(), 150), render(zero.toBytes(), 150)));
    }

    @Test
    void invalidResolutionIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> PdfDocument.rtl(Paper.A4).printerDpi(-1));
        assertThrows(IllegalArgumentException.class, () -> PdfDocument.rtl(Paper.A4).printerDpi(50));
        assertThrows(IllegalArgumentException.class, () -> PdfDocument.rtl(Paper.A4).printerDpi(5000));
    }
}
