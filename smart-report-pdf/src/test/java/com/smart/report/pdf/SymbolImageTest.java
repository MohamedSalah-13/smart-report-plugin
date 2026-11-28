package com.smart.report.pdf;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.oned.Code128Reader;
import com.google.zxing.qrcode.QRCodeReader;
import com.smart.report.util.ZatcaQr;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SymbolImageTest {

    private static BufferedImage read(byte[] png) throws Exception {
        assertEquals((byte) 0x89, png[0]);
        assertEquals('P', png[1]);
        assertEquals('N', png[2]);
        assertEquals('G', png[3]);
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    private static BinaryBitmap bitmap(BufferedImage image) {
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        return new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels)));
    }

    private static String readQr(BufferedImage image) throws Exception {
        return new QRCodeReader().decode(bitmap(image), Map.of(DecodeHintType.PURE_BARCODE, Boolean.TRUE)).getText();
    }

    /** كل بكسل أسود أو أبيض بالظبط، من غير رمادي. */
    private static void blackAndWhite(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y) & 0xFFFFFF;
                assertTrue(rgb == 0 || rgb == 0xFFFFFF, "pixel " + x + "," + y + " is " + Integer.toHexString(rgb));
            }
        }
    }

    @Test
    void qrPngReadsBack() throws Exception {
        String zatca = ZatcaQr.payload("مؤسسة الهدى للتجارة", "310122393500003",
                Instant.parse("2026-11-28T10:30:00Z"), new BigDecimal("1150.00"), new BigDecimal("150.00"));
        for (String text : new String[]{"https://example.com/i/42", "فاتورة رقم 42", zatca}) {
            BufferedImage image = read(SymbolImage.qrPng(text, 6));
            assertEquals(text, readQr(image));
            blackAndWhite(image);
        }
    }

    /** ضلع الصورة = (مربعات الرمز + 4 على كل جنب) × البكسلات، والمنطقة الفاضية بيضا. */
    @Test
    void qrHasAWholeNumberOfPixelsPerModuleAndItsQuietZone() throws Exception {
        QrSymbol symbol = QrSymbol.encode("INV-42");
        BufferedImage image = read(SymbolImage.qrPng("INV-42", 5));
        int side = (symbol.size() + 2 * QrSymbol.QUIET_ZONE) * 5;
        assertEquals(side, image.getWidth());
        assertEquals(side, image.getHeight());
        for (int i = 0; i < side; i++) {
            for (int edge = 0; edge < QrSymbol.QUIET_ZONE * 5; edge++) {
                assertEquals(0xFFFFFF, image.getRGB(i, edge) & 0xFFFFFF, "top quiet zone");
                assertEquals(0xFFFFFF, image.getRGB(edge, i) & 0xFFFFFF, "left quiet zone");
            }
        }
        // كل مربع لون واحد: البكسلات الخمسة في ضلعه زي بعض، وزي الرمز المتشفّر.
        for (int row = 0; row < symbol.size(); row++) {
            for (int column = 0; column < symbol.size(); column++) {
                int expected = symbol.dark(row, column) ? 0 : 0xFFFFFF;
                for (int dy = 0; dy < 5; dy++) {
                    for (int dx = 0; dx < 5; dx++) {
                        int x = (column + QrSymbol.QUIET_ZONE) * 5 + dx;
                        int y = (row + QrSymbol.QUIET_ZONE) * 5 + dy;
                        assertEquals(expected, image.getRGB(x, y) & 0xFFFFFF);
                    }
                }
            }
        }
        assertEquals("INV-42", readQr(SymbolImage.qr("INV-42", 1)));
    }

    @Test
    void barcodePngReadsBack() throws Exception {
        for (String text : new String[]{"INV-2026-0042", "1234567890", "a b/c"}) {
            BufferedImage image = read(SymbolImage.barcodePng(text, 3, 60));
            Result result = new Code128Reader().decode(bitmap(image), Map.of(DecodeHintType.PURE_BARCODE, Boolean.TRUE));
            assertEquals(text, result.getText());
            assertEquals(BarcodeFormat.CODE_128, result.getBarcodeFormat());
            blackAndWhite(image);
        }
    }

    @Test
    void barcodeHasAWholeNumberOfPixelsPerModuleAndItsQuietZone() throws Exception {
        BarcodeSymbol symbol = BarcodeSymbol.encode("ABC-123");
        BufferedImage image = SymbolImage.barcode("ABC-123", 2, 40);
        assertEquals((symbol.modules() + 2 * BarcodeSymbol.QUIET_ZONE) * 2, image.getWidth());
        assertEquals(40, image.getHeight());
        for (int y = 0; y < 40; y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int module = x / 2 - BarcodeSymbol.QUIET_ZONE;
                boolean dark = module >= 0 && module < symbol.modules() && symbol.dark(module);
                assertEquals(dark ? 0 : 0xFFFFFF, image.getRGB(x, y) & 0xFFFFFF, "pixel " + x + "," + y);
            }
        }
    }

    @Test
    void invalidInputIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SymbolImage.qrPng("", 4));
        assertThrows(IllegalArgumentException.class, () -> SymbolImage.qrPng(null, 4));
        assertThrows(IllegalArgumentException.class, () -> SymbolImage.qrPng("x", 0));
        assertThrows(IllegalArgumentException.class, () -> SymbolImage.qrPng("x", 1000), "صورة أكبر من الحد");
        assertThrows(IllegalArgumentException.class, () -> SymbolImage.barcodePng("فاتورة", 2, 40));
        assertThrows(IllegalArgumentException.class, () -> SymbolImage.barcodePng("A1", 0, 40));
        assertThrows(IllegalArgumentException.class, () -> SymbolImage.barcodePng("A1", 2, 0));
        assertThrows(IllegalArgumentException.class, () -> SymbolImage.barcodePng("A1", 2, 20_000));
    }
}
