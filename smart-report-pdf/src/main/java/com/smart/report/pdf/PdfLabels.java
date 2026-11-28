package com.smart.report.pdf;

import com.smart.report.ReportException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Item labels: the item name, its price (with a struck-through old price beside it during an offer), and its
 * barcode. Prints on a label roll (one or two labels across) or on an A4 label sheet in rows and columns.
 *
 * <pre>{@code
 * PdfLabels labels = PdfLabels.roll(40, 25)                      // a 40 × 25 mm label
 *         .across(2, 2)                                            // two side by side, 2 mm apart
 *         .rightToLeft(true)
 *         .printerDpi(203);
 * for (Unit unit : units) {
 *     labels.add(PdfLabels.Item.of(unit.name(), unit.priceText(), unit.barcode()), unit.copies());
 * }
 * PdfPrinter.print(labels.toBytes(), printerName, labels.paper(), 1);
 *
 * PdfLabels sheet = PdfLabels.sheet(Paper.A4, 3, 8).margins(8, 4); // 24 labels per sheet
 * }</pre>
 *
 * <p><b>Barcode:</b> 13 digits with a valid check digit are drawn as EAN-13 with the digits underneath. Any other
 * code is drawn as Code 128, so the scanner reads exactly the text stored in the program. On a narrow label the bars
 * shrink down to the smallest readable size; below that, {@link #toBytes()} fails with a {@link ReportException}
 * that names the item, instead of producing a label nobody can scan. With {@link #printerDpi}, every bar is a whole
 * number of printer dots.</p>
 *
 * <p><b>Long names:</b> {@link LongName#ELLIPSIS} (the default) cuts the name and ends it with «…».
 * {@link LongName#SHRINK} shrinks the font down to the smallest size, then cuts. {@link LongName#HIDE} leaves the
 * name out.</p>
 *
 * <p><b>Calibration:</b> {@link #offset} shifts everything by millimetres, for a printer whose roll feeds in slightly
 * askew; save it with the printer settings. A label sheet is a set of fixed-size pages, not a document that gets
 * split into pages, so the page rules of {@link PdfDocument} do not apply to it. This object is not thread-safe.</p>
 */
public final class PdfLabels {

    private static final float POINTS_PER_MM = 72f / 25.4f;

    /** What happens to a name that does not fit the label width. */
    public enum LongName {
        /** The name is cut and ends with «…». */
        ELLIPSIS,
        /** The font shrinks down to {@link PdfStyle#MIN_FONT_SIZE}, then the name is cut. */
        SHRINK,
        /** The name is not printed. */
        HIDE
    }

    /** One item label. The name and the price are ready-made text, in the program's language and format. */
    public static final class Item {
        private final String name;
        private final String price;
        private final String oldPrice;
        private final String code;

        private Item(String name, String price, String oldPrice, String code) {
            this.name = name == null ? "" : name.strip();
            this.price = price == null ? "" : price.strip();
            this.oldPrice = oldPrice == null ? "" : oldPrice.strip();
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("A label needs a barcode; \"" + this.name
                        + "\" has none. A unit without a barcode gets no label.");
            }
            this.code = code.strip();
            if (!Ean13Symbol.valid(this.code)) {
                BarcodeSymbol.encode(this.code);   // يرفض الحروف اللي Code 128 ما بيشيلهاش دلوقتي، مش وقت الطباعة
            }
        }

        /**
         * @param name  the item name
         * @param price the formatted price, for example «15.00 ج.م»
         * @param code  the barcode: 13 digits for EAN-13, or any ASCII code (Code 128)
         * @throws IllegalArgumentException if the code is blank or has a character outside ASCII
         */
        public static Item of(String name, String price, String code) {
            return new Item(name, price, null, code);
        }

        /** The price before the offer, printed struck through beside the price. */
        public Item withOldPrice(String value) {
            return new Item(name, price, value, code);
        }

        public String name() {
            return name;
        }

        public String price() {
            return price;
        }

        public String oldPrice() {
            return oldPrice;
        }

        public String code() {
            return code;
        }
    }

    private final Paper sheet;
    private final float labelWidth;
    private final float labelHeight;
    private final int columns;
    private final int rows;
    private int across = 1;
    private float gapX;
    private float gapY;
    private float marginTop;
    private float marginSide;
    private float offsetX;
    private float offsetY;
    private boolean rightToLeft = true;
    private boolean showName = true;
    private boolean showPrice = true;
    private boolean showDigits = true;
    private LongName longName = LongName.ELLIPSIS;
    private int printerDpi;
    private PdfFont font;
    private final List<Item> items = new ArrayList<>();

    private PdfLabels(Paper sheet, float labelWidth, float labelHeight, int columns, int rows) {
        this.sheet = sheet;
        this.labelWidth = labelWidth;
        this.labelHeight = labelHeight;
        this.columns = columns;
        this.rows = rows;
    }

    /**
     * A label roll: each row of labels is one page of its own size, and the label printer stops at its end.
     *
     * @param widthMm  the width of one label, 15 mm or more
     * @param heightMm its height, 10 mm or more
     */
    public static PdfLabels roll(float widthMm, float heightMm) {
        if (!(widthMm >= 15) || !(heightMm >= 10) || widthMm > 300 || heightMm > 300) {
            throw new IllegalArgumentException("A label is at least 15 × 10 mm and at most 300 mm a side: "
                    + widthMm + " × " + heightMm);
        }
        return new PdfLabels(null, widthMm * POINTS_PER_MM, heightMm * POINTS_PER_MM, 0, 0);
    }

    /**
     * A label sheet: {@code columns × rows} labels on the sheet. The label size is whatever space is left after
     * {@link #margins} and {@link #gaps}.
     */
    public static PdfLabels sheet(Paper paper, int columns, int rows) {
        Objects.requireNonNull(paper, "paper");
        if (paper.roll()) {
            throw new IllegalArgumentException("A label sheet is a cut sheet, not a thermal roll; use PdfLabels.roll.");
        }
        if (columns < 1 || rows < 1 || columns > 20 || rows > 40) {
            throw new IllegalArgumentException("A sheet has 1 to 20 columns and 1 to 40 rows: " + columns + " × " + rows);
        }
        return new PdfLabels(paper, 0, 0, columns, rows);
    }

    /** On a roll: how many labels sit side by side across the roll (1 to 4), and the gap between them in mm. */
    public PdfLabels across(int count, float gapMm) {
        if (sheet != null) {
            throw new IllegalStateException("across is for a roll; a sheet has columns.");
        }
        if (count < 1 || count > 4 || !(gapMm >= 0)) {
            throw new IllegalArgumentException("A roll has 1 to 4 labels across with a gap of 0 mm or more.");
        }
        this.across = count;
        this.gapX = gapMm * POINTS_PER_MM;
        return this;
    }

    /** On a sheet: the top and bottom margin, and the side margins, in mm. */
    public PdfLabels margins(float topMm, float sideMm) {
        if (sheet == null) {
            throw new IllegalStateException("margins are for a sheet.");
        }
        if (!(topMm >= 0) || !(sideMm >= 0)) {
            throw new IllegalArgumentException("Margins are 0 mm or more.");
        }
        this.marginTop = topMm * POINTS_PER_MM;
        this.marginSide = sideMm * POINTS_PER_MM;
        return this;
    }

    /** On a sheet: the gap between columns and between rows, in mm. */
    public PdfLabels gaps(float betweenColumnsMm, float betweenRowsMm) {
        if (sheet == null) {
            throw new IllegalStateException("gaps are for a sheet; a roll takes its gap in across.");
        }
        if (!(betweenColumnsMm >= 0) || !(betweenRowsMm >= 0)) {
            throw new IllegalArgumentException("Gaps are 0 mm or more.");
        }
        this.gapX = betweenColumnsMm * POINTS_PER_MM;
        this.gapY = betweenRowsMm * POINTS_PER_MM;
        return this;
    }

    /**
     * Printer calibration: shifts everything right (positive {@code xMm}) and down (positive {@code yMm}), up to
     * 10 mm.
     */
    public PdfLabels offset(float xMm, float yMm) {
        if (!(Math.abs(xMm) <= 10) || !(Math.abs(yMm) <= 10)) {
            throw new IllegalArgumentException("The offset is at most 10 mm each way: " + xMm + ", " + yMm);
        }
        this.offsetX = xMm * POINTS_PER_MM;
        this.offsetY = yMm * POINTS_PER_MM;
        return this;
    }

    /** The direction of the name and the price (Arabic or not). */
    public PdfLabels rightToLeft(boolean value) {
        this.rightToLeft = value;
        return this;
    }

    public PdfLabels showName(boolean value) {
        this.showName = value;
        return this;
    }

    public PdfLabels showPrice(boolean value) {
        this.showPrice = value;
        return this;
    }

    /** The digits or the code under the bars. */
    public PdfLabels showDigits(boolean value) {
        this.showDigits = value;
        return this;
    }

    public PdfLabels longName(LongName value) {
        this.longName = Objects.requireNonNull(value, "longName");
        return this;
    }

    /** Printer resolution, as in {@link PdfDocument#printerDpi}: every bar is a whole number of its dots. 0 = off. */
    public PdfLabels printerDpi(int dpi) {
        if (dpi != 0 && (dpi < 72 || dpi > 2400)) {
            throw new IllegalArgumentException("The printer resolution must be between 72 and 2400 dpi, or 0: " + dpi);
        }
        this.printerDpi = dpi;
        return this;
    }

    /** A font for the labels instead of the global font. */
    public PdfLabels font(PdfFont value) {
        this.font = value;
        return this;
    }

    /** One label for the item. */
    public PdfLabels add(Item item) {
        return add(item, 1);
    }

    /** {@code copies} labels for the item, one after another. */
    public PdfLabels add(Item item, int copies) {
        Objects.requireNonNull(item, "item");
        if (copies < 1 || copies > 10_000) {
            throw new IllegalArgumentException("Copies must be between 1 and 10000: " + copies);
        }
        for (int i = 0; i < copies; i++) {
            items.add(item);
        }
        return this;
    }

    /** The number of labels. */
    public int count() {
        return items.size();
    }

    /** The page size: the sheet, or one row of labels on the roll. */
    public Paper paper() {
        if (sheet != null) {
            return sheet;
        }
        return new Paper("labels-" + Math.round(labelWidth / POINTS_PER_MM) + "x"
                + Math.round(labelHeight / POINTS_PER_MM), across * labelWidth + (across - 1) * gapX, labelHeight);
    }

    /** The labels on one page. */
    int perPage() {
        return sheet != null ? columns * rows : across;
    }

    float cellWidth() {
        return sheet != null ? (sheet.width() - 2 * marginSide - (columns - 1) * gapX) / columns : labelWidth;
    }

    float cellHeight() {
        return sheet != null ? (sheet.height() - 2 * marginTop - (rows - 1) * gapY) / rows : labelHeight;
    }

    /**
     * @throws ReportException if there are no labels, a label is smaller than its barcode, or, in strict mode, a
     *                         character is in no font
     */
    public byte[] toBytes() throws ReportException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out);
        return out.toByteArray();
    }

    public void save(Path file) throws ReportException {
        try (OutputStream out = Files.newOutputStream(file)) {
            write(out);
        } catch (IOException e) {
            throw new ReportException("Failed to write the labels to " + file + ": " + e.getMessage(), e);
        }
    }

    private void write(OutputStream out) throws ReportException {
        if (items.isEmpty()) {
            throw new ReportException("There are no labels to write; add at least one item.");
        }
        float width = cellWidth();
        float height = cellHeight();
        if (width < 15 * POINTS_PER_MM || height < 10 * POINTS_PER_MM) {
            throw new ReportException(String.format(java.util.Locale.ROOT,
                    "The labels come out %.1f × %.1f mm, smaller than 15 × 10 mm. Use fewer columns or rows, or"
                            + " smaller margins and gaps.", width / POINTS_PER_MM, height / POINTS_PER_MM));
        }
        Paper page = paper();
        try (PDDocument pdf = new PDDocument(); FontSet fonts = FontSet.load(pdf, font, false)) {
            pdf.getDocumentInformation().setCreator("smart-report-pdf");
            Typesetter text = new Typesetter(fonts, rightToLeft);
            Canvas canvas = new Canvas(pdf);
            LabelPainter painter = new LabelPainter(text, canvas, width, height, page.height());
            for (int i = 0; i < items.size(); i++) {
                int slot = i % perPage();
                if (slot == 0) {
                    PDPage pdPage = new PDPage(new PDRectangle(page.width(), page.height()));
                    pdf.addPage(pdPage);
                    canvas.start(pdPage);
                }
                int column = sheet != null ? slot % columns : slot;
                int row = sheet != null ? slot / columns : 0;
                float left = (sheet != null ? marginSide : 0) + column * (width + gapX) + offsetX;
                float top = page.height() - (sheet != null ? marginTop : 0) - row * (height + gapY) - offsetY;
                painter.paint(items.get(i), left, top);
            }
            canvas.close();
            pdf.save(out);
        } catch (IOException e) {
            throw new ReportException("Failed to write the labels: " + e.getMessage(), e);
        }
    }

    /** Draws one label in its cell. */
    private final class LabelPainter {
        private final Typesetter text;
        private final Canvas canvas;
        private final float width;
        private final float height;
        private final float pageHeight;
        private final float pad;
        private final float nameSize;
        private final float priceSize;
        private final float dot;

        LabelPainter(Typesetter text, Canvas canvas, float width, float height, float pageHeight) {
            this.text = text;
            this.canvas = canvas;
            this.width = width;
            this.height = height;
            this.pageHeight = pageHeight;
            this.pad = Math.min(1.5f * POINTS_PER_MM, width * 0.05f);
            this.nameSize = Math.clamp(height * 0.11f, PdfStyle.MIN_FONT_SIZE, 10f);
            this.priceSize = Math.clamp(height * 0.13f, PdfStyle.MIN_FONT_SIZE, 12f);
            this.dot = printerDpi > 0 ? 72f / printerDpi : 0f;
        }

        void paint(Item item, float left, float top) throws IOException, ReportException {
            float inner = width - 2 * pad;
            float y = top - pad;
            if (showName && !item.name().isEmpty()) {
                Fitted name = fitName(item.name(), inner);
                if (name != null) {
                    canvas.text(name.line(), name.size(), 0x000000, left + pad, inner, Canvas.Side.CENTER,
                            y - name.size() * Typesetter.BASELINE);
                    y -= name.size() * Typesetter.LEADING;
                }
            }
            if (showPrice && !item.price().isEmpty()) {
                y = price(item, left + pad, inner, y);
            }
            barcode(item, left + pad, inner, y - pad * 0.5f, top - height + pad);
        }

        /** The price in bold, with the old price struck through beside it if it fits; both centred on the label. */
        private float price(Item item, float left, float inner, float y) throws IOException, ReportException {
            Typesetter.Line now = text.line(item.price(), item.price(), true, priceSize);
            Typesetter.Line before = item.oldPrice().isEmpty() ? null
                    : text.line(item.oldPrice(), item.oldPrice(), false, priceSize * 0.8f);
            float gap = priceSize * 0.6f;
            if (before != null && now.width() + gap + before.width() > inner) {
                before = null;
            }
            float baseline = y - priceSize * Typesetter.BASELINE;
            float total = now.width() + (before == null ? 0 : gap + before.width());
            float start = left + (inner - total) / 2;
            // السعر الجديد في أول السطر بالاتجاه: يمين في العربي.
            float nowLeft = rightToLeft && before != null ? start + before.width() + gap : start;
            canvas.text(now, priceSize, 0x000000, nowLeft, now.width(), Canvas.Side.LEFT, baseline);
            if (before != null) {
                float beforeLeft = rightToLeft ? start : start + now.width() + gap;
                float beforeSize = priceSize * 0.8f;
                canvas.text(before, beforeSize, 0x000000, beforeLeft, before.width(), Canvas.Side.LEFT, baseline);
                canvas.rule(beforeLeft, beforeLeft + before.width(), baseline + beforeSize * 0.3f, 0x000000,
                        Math.max(0.5f, beforeSize * 0.07f));
            }
            return y - priceSize * Typesetter.LEADING;
        }

        private void barcode(Item item, float left, float inner, float top, float bottom)
                throws IOException, ReportException {
            float room = top - bottom;
            if (Ean13Symbol.valid(item.code())) {
                Ean13Symbol symbol = Ean13Symbol.encode(item.code());
                int span = Ean13Symbol.QUIET_LEFT + Ean13Symbol.MODULES + Ean13Symbol.QUIET_RIGHT;
                float module = SymbolParts.fitModule(span, SymbolParts.EAN_MODULE, SymbolParts.MIN_EAN_MODULE, inner,
                        dot, printerDpi, "The label barcode " + item.code() + " (" + item.name() + ")");
                SymbolParts.EanDigits digits = showDigits ? SymbolParts.EanDigits.of(text, symbol,
                        SymbolParts.eanDigitSize(text, module, nameSize)) : null;
                float digitsHeight = digits == null ? 0 : SymbolParts.eanDigitsHeight(digits.size());
                float bars = room - digitsHeight;
                checkRoom(bars, item);
                float originX = onDots(left + (inner - span * module) / 2) + Ean13Symbol.QUIET_LEFT * module;
                SymbolParts.drawEan13(canvas, symbol, originX, topOnDots(top), module, bars, digits, 0x000000);
                return;
            }
            BarcodeSymbol symbol = BarcodeSymbol.encode(item.code());
            int span = symbol.modules() + 2 * BarcodeSymbol.QUIET_ZONE;
            float module = SymbolParts.fitModule(span, SymbolParts.BAR_MODULE, SymbolParts.MIN_BAR_MODULE, inner, dot,
                    printerDpi, "The label barcode " + item.code() + " (" + item.name() + ")");
            Typesetter.Line caption = showDigits ? text.plain(item.code(), nameSize) : null;
            float captionSize = nameSize;
            if (caption != null && caption.width() > inner) {
                captionSize = Math.max(PdfStyle.MIN_FONT_SIZE, nameSize * inner / caption.width());
                caption = text.plain(item.code(), captionSize);
            }
            float captionHeight = caption == null ? 0 : captionSize * Typesetter.LEADING;
            float bars = room - captionHeight;
            checkRoom(bars, item);
            float originX = onDots(left + (inner - span * module) / 2) + BarcodeSymbol.QUIET_ZONE * module;
            float barsTop = topOnDots(top);
            canvas.fillColor(0x000000);
            int column = 0;
            while (column < symbol.modules()) {
                if (!symbol.dark(column)) {
                    column++;
                    continue;
                }
                int start = column;
                while (column < symbol.modules() && symbol.dark(column)) {
                    column++;
                }
                canvas.rect(originX + start * module, barsTop - bars, (column - start) * module, bars);
            }
            canvas.fill();
            if (caption != null) {
                canvas.text(caption, captionSize, 0x000000, left, inner, Canvas.Side.CENTER,
                        barsTop - bars - captionSize * Typesetter.BASELINE);
            }
        }

        private void checkRoom(float bars, Item item) throws ReportException {
            if (bars < 4 * POINTS_PER_MM) {
                throw new ReportException(String.format(java.util.Locale.ROOT,
                        "The label for \"%s\" leaves %.1f mm for its barcode; at least 4 mm are needed. Use a taller"
                                + " label, or turn off the name or the price.", item.name(), bars / POINTS_PER_MM));
            }
        }

        private float onDots(float x) {
            return dot > 0 ? Math.round(x / dot) * dot : x;
        }

        private float topOnDots(float top) {
            return dot > 0 ? pageHeight - Math.round((pageHeight - top) / dot) * dot : top;
        }

        private record Fitted(Typesetter.Line line, float size) {}

        /** The name on one line, according to {@link #longName}; {@code null} = not printed. */
        private Fitted fitName(String name, float inner) throws IOException, ReportException {
            float size = nameSize;
            Typesetter.Line line = text.line(name, name, false, size);
            if (line.width() <= inner) {
                return new Fitted(line, size);
            }
            switch (longName) {
                case HIDE -> {
                    return null;
                }
                case SHRINK -> {
                    // أصغر بشعرة من المحسوب: التقريب كان بيخلي العرض أكبر من السطر بكسر ويقصه.
                    size = Math.max(PdfStyle.MIN_FONT_SIZE, size * inner / line.width() * 0.995f);
                    line = text.line(name, name, false, size);
                    if (line.width() <= inner) {
                        return new Fitted(line, size);
                    }
                }
                case ELLIPSIS -> {
                    // نفس المقاس، والقص تحت.
                }
            }
            int[] points = name.codePoints().toArray();
            for (int keep = points.length - 1; keep > 0; keep--) {
                String cut = new String(points, 0, keep).strip() + "…";
                Typesetter.Line shorter = text.line(cut, cut, false, size);
                if (shorter.width() <= inner) {
                    return new Fitted(shorter, size);
                }
            }
            return null;
        }
    }
}
