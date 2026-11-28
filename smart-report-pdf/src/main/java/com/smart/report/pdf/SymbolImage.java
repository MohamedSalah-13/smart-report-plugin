package com.smart.report.pdf;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * A QR code or a Code 128 barcode as an image, for a program that shows it on screen or sends it from a web
 * server, with the same encoding that {@link PdfDocument#qrCode(String)} and {@link PdfDocument#barcode(String)}
 * draw in the PDF.
 *
 * <pre>{@code
 * byte[] png = SymbolImage.qrPng(ZatcaQr.payload(...), 8);       // 8 pixels per module
 * byte[] png = SymbolImage.barcodePng("INV-2026-0042", 3, 80);     // 3 pixels for the narrowest bar, 80 pixels high
 * BufferedImage image = SymbolImage.qr("https://example.com", 6);  // for SwingFXUtils.toFXImage, for example
 * }</pre>
 *
 * <p>Every module is a whole number of pixels, black and white only with no anti-aliasing, and the quiet zone is
 * inside the image (4 modules around a QR code, 10 on each side of a barcode), so the image scans as it is. Make it
 * bigger with more pixels per module, not by scaling the image itself: scaling by a fraction makes the modules
 * unequal. The class is stateless and thread-safe.</p>
 */
public final class SymbolImage {

    /** The largest image side in pixels, so a wrong number does not allocate gigabytes of memory. */
    static final int MAX_SIDE = 10_000;

    private SymbolImage() {}

    /**
     * A QR code with error correction level M, with Arabic text in UTF-8 as in the PDF.
     *
     * @param pixelsPerModule the pixels on one side of a module, 1 or more
     * @throws IllegalArgumentException if the text is empty or longer than the code can hold, the number is less
     *                                  than 1, or the image would exceed {@value #MAX_SIDE} pixels
     */
    public static BufferedImage qr(String text, int pixelsPerModule) {
        QrSymbol symbol = QrSymbol.encode(text);
        int modules = symbol.size() + 2 * QrSymbol.QUIET_ZONE;
        int side = side(modules, pixelsPerModule, "pixelsPerModule");
        BufferedImage image = white(side, side);
        for (int row = 0; row < symbol.size(); row++) {
            for (int column = 0; column < symbol.size(); column++) {
                if (symbol.dark(row, column)) {
                    black(image, (column + QrSymbol.QUIET_ZONE) * pixelsPerModule,
                            (row + QrSymbol.QUIET_ZONE) * pixelsPerModule, pixelsPerModule, pixelsPerModule);
                }
            }
        }
        return image;
    }

    /** {@link #qr} as a PNG file. */
    public static byte[] qrPng(String text, int pixelsPerModule) {
        return png(qr(text, pixelsPerModule));
    }

    /**
     * A Code 128 barcode (printable ASCII characters only), without the text under it.
     *
     * @param pixelsPerModule the width of the narrowest bar in pixels, 1 or more
     * @param heightPixels    the height of the bars in pixels, 1 or more
     * @throws IllegalArgumentException if the text is empty or has a character outside printable ASCII, a number is
     *                                  less than 1, or the image would exceed {@value #MAX_SIDE} pixels
     */
    public static BufferedImage barcode(String text, int pixelsPerModule, int heightPixels) {
        BarcodeSymbol symbol = BarcodeSymbol.encode(text);
        int width = side(symbol.modules() + 2 * BarcodeSymbol.QUIET_ZONE, pixelsPerModule, "pixelsPerModule");
        int height = side(1, heightPixels, "heightPixels");
        BufferedImage image = white(width, height);
        for (int module = 0; module < symbol.modules(); module++) {
            if (symbol.dark(module)) {
                black(image, (module + BarcodeSymbol.QUIET_ZONE) * pixelsPerModule, 0, pixelsPerModule, height);
            }
        }
        return image;
    }

    /** {@link #barcode} as a PNG file. */
    public static byte[] barcodePng(String text, int pixelsPerModule, int heightPixels) {
        return png(barcode(text, pixelsPerModule, heightPixels));
    }

    private static int side(int modules, int pixels, String name) {
        if (pixels < 1) {
            throw new IllegalArgumentException(name + " must be at least 1: " + pixels);
        }
        long side = (long) modules * pixels;
        if (side > MAX_SIDE) {
            throw new IllegalArgumentException("The image would be " + side + " pixels wide; at most " + MAX_SIDE
                    + " are allowed. Use fewer " + name + ".");
        }
        return (int) side;
    }

    private static BufferedImage white(int width, int height) {
        // صورة ببت واحد للبكسل: أسود وأبيض بس، فما فيش رمادي ولا تنعيم، والـ PNG صغير.
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        java.awt.Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void black(BufferedImage image, int x, int y, int width, int height) {
        int black = 0xFF000000;
        for (int row = y; row < y + height; row++) {
            for (int column = x; column < x + width; column++) {
                image.setRGB(column, row, black);
            }
        }
    }

    private static byte[] png(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, "png", out)) {
                throw new IllegalStateException("No PNG writer is available in this Java runtime.");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
