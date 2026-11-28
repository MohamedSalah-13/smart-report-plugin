package com.smart.report.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/** الصورة في جسم المستند ({@link PdfDocument#image}): قطعة واحدة ما بتتقسمش. */
final class ImageParts {

    private ImageParts() {
    }

    /**
     * عرض الصورة وطولها بالبكسل، من غير ما تتفك كلها.
     *
     * @throws IllegalArgumentException لو مش PNG أو JPEG بيتقري
     */
    static int[] pixels(byte[] image) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(image))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers != null && readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    String format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                    if (format.equals("png") || format.equals("jpeg") || format.equals("jpg")) {
                        reader.setInput(in);
                        int width = reader.getWidth(0);
                        int height = reader.getHeight(0);
                        if (width > 0 && height > 0) {
                            return new int[]{width, height};
                        }
                    }
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("The image is not a PNG or JPEG that can be read: " + e.getMessage(), e);
        }
        throw new IllegalArgumentException("The image is not a PNG or JPEG that can be read.");
    }

    /**
     * الصورة بالعرض المطلوب ونسبتها، أصغر لو أعرض من السطر أو أطول من صفحة جديدة، في مكانها على السطر.
     */
    static Part image(PdfDocument.ImageBlock block, Frame frame) {
        float ratio = (float) block.pixels()[1] / block.pixels()[0];
        float width = Math.min(block.width(), frame.width());
        float height = width * ratio;
        if (height > frame.freshRoom()) {
            height = frame.freshRoom();
            width = height / ratio;
        }
        float boxWidth = width;
        float boxHeight = height;
        float left = switch (frame.side(block.align())) {
            case LEFT -> frame.left();
            case CENTER -> frame.left() + (frame.width() - boxWidth) / 2;
            case RIGHT -> frame.right() - boxWidth;
        };
        Piece picture = new Piece() {
            private PDDocument owner;
            private PDImageXObject image;

            @Override
            public float height() {
                return boxHeight;
            }

            @Override
            public void draw(Canvas canvas, float top) throws IOException {
                if (image == null || owner != canvas.pdf()) {
                    owner = canvas.pdf();
                    image = PDImageXObject.createFromByteArray(owner, block.bytes(), "image");
                }
                canvas.image(image, left, top - boxHeight, boxWidth, boxHeight);
            }
        };
        return Flow.of(List.of(picture)).withGaps(Parts.BLOCK_GAP, 0);
    }
}
