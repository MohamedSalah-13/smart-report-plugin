package com.smart.report.pdf;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * رمز QR أو باركود Code 128 كصورة، لبرنامج بيعرضه على الشاشة أو بيبعته من خادم ويب، بنفس التشفير اللي
 * {@link PdfDocument#qrCode(String)} و {@link PdfDocument#barcode(String)} بيرسموه في الـ PDF.
 *
 * <pre>{@code
 * byte[] png = SymbolImage.qrPng(ZatcaQr.payload(...), 8);       // 8 بكسل لكل مربع
 * byte[] png = SymbolImage.barcodePng("INV-2026-0042", 3, 80);     // 3 بكسل لأضيق خط، و 80 بكسل ارتفاع
 * BufferedImage image = SymbolImage.qr("https://example.com", 6);  // لـ SwingFXUtils.toFXImage مثلًا
 * }</pre>
 *
 * <p>كل مربع عدد صحيح من البكسلات، أبيض وأسود بس من غير تنعيم، والمنطقة الفاضية جوّه الصورة (4 مربعات حوالين
 * QR، و 10 على كل جنب من الباركود)، فالصورة تتقري زي ما هي. كبّرها بعدد بكسلات أكبر، مش بتكبير الصورة نفسها:
 * التكبير بكسر بيخلي المربعات مش متساوية. الصنف بلا حالة وآمن بين الـ threads.</p>
 */
public final class SymbolImage {

    /** أكبر ضلع للصورة بالبكسل، عشان رقم غلط ما يحجزش ذاكرة بالجيجا. */
    static final int MAX_SIDE = 10_000;

    private SymbolImage() {}

    /**
     * رمز QR بمستوى تصحيح M، والنص العربي بـ UTF-8 زي اللي في الـ PDF.
     *
     * @param pixelsPerModule بكسلات ضلع المربع الواحد، 1 أو أكتر
     * @throws IllegalArgumentException لو النص فاضي أو أطول من اللي يشيله الرمز، أو العدد أقل من 1، أو الصورة
     *                                  هتعدّي {@value #MAX_SIDE} بكسل
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

    /** {@link #qr} كملف PNG. */
    public static byte[] qrPng(String text, int pixelsPerModule) {
        return png(qr(text, pixelsPerModule));
    }

    /**
     * باركود Code 128 (حروف ASCII المطبوعة بس)، من غير النص تحته.
     *
     * @param pixelsPerModule بكسلات عرض أضيق خط، 1 أو أكتر
     * @param heightPixels    ارتفاع الخطوط بالبكسل، 1 أو أكتر
     * @throws IllegalArgumentException لو النص فاضي أو فيه حرف برّه ASCII المطبوع، أو عدد أقل من 1، أو الصورة
     *                                  هتعدّي {@value #MAX_SIDE} بكسل
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

    /** {@link #barcode} كملف PNG. */
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
