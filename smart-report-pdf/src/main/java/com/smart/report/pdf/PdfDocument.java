package com.smart.report.pdf;

import com.smart.report.ReportException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A PDF document built part by part, in the order the parts will appear: letterhead, title, fields, tables,
 * summary, footer.
 *
 * <pre>{@code
 * PdfDocument document = PdfDocument.rtl(Paper.A5)
 *         .letterhead("شركة النقل", "ت: 0100 000 0000")
 *         .title("كشف حساب عربية")
 *         .field("رقم العربية", "1234").field("السائق", "أحمد")
 *         .table(t -> t.column("التاريخ", 22).column("العدد", 14).column("القيمة", 26, Align.END)
 *                 .row("2026-11-01", "3", "5,400.00")
 *                 .totals("الإجمالى", "3", "5,400.00"))
 *         .summary("المستحق", "5,400.00")
 *         .summaryTotal("الباقى", "1,400.00");
 *
 * document.save(Path.of("statement.pdf"));     // to a file
 * PdfPrinter.print(document, printerName, 1);  // or straight to the printer
 * }</pre>
 *
 * <p>The direction ({@link #rtl(Paper)} / {@link #ltr(Paper)}) sets where the first table column goes and how
 * text is aligned. Arabic text is shaped and ordered correctly in both directions, and numbers and dates read
 * as they were written.</p>
 *
 * <p>The object is mutable and not safe to use from more than one thread; build one document per print.</p>
 */
public final class PdfDocument {

    /** A part of the document body, in the order it was added. */
    sealed interface Block {
    }

    record TitleBlock(String text) implements Block {
    }

    record SubtitleBlock(String text) implements Block {
    }

    record HeadingBlock(String text) implements Block {
    }

    record TextBlock(String text, Align align) implements Block {
    }

    record SpaceBlock(float points) implements Block {
    }

    record TableBlock(PdfTable table) implements Block {
    }

    record FieldsBlock(List<String[]> fields) implements Block {
    }

    record SummaryBlock(List<SummaryLine> lines) implements Block {
    }

    /** @param negativeInRed a negative value is shown in red ({@link PdfTable#negativeInRed}) */
    record SummaryLine(String label, String value, boolean total, boolean negativeInRed) {
    }

    record SignaturesBlock(List<String> labels) implements Block {
    }

    /** @param size the side of the symbol in points, without the blank zone around it */
    record QrBlock(QrSymbol symbol, float size, Align align) implements Block {
    }

    /** @param height the height of the bars in points, without the text line under them */
    record BarcodeBlock(BarcodeSymbol symbol, float height, Align align) implements Block {
    }

    /** @param height the height of the bars in points, without the digits under them */
    record Ean13Block(Ean13Symbol symbol, float height, Align align) implements Block {
    }

    /**
     * @param bytes  PNG or JPEG, as given
     * @param width  the requested width in points
     * @param pixels the image width and height in pixels, for the aspect ratio
     */
    record ImageBlock(byte[] bytes, float width, int[] pixels, Align align) implements Block {
    }

    /** The «طُبع في ... بواسطة ...» ("printed on ... by ...") line under the title. */
    record PrintedBlock(String text) implements Block {
    }

    /** The part after it starts on a new page; the rules are in {@link #pageBreak()}. */
    record PageBreakBlock() implements Block {
    }

    /**
     * The side of a QR symbol when none is set: 30 mm. A phone reads it easily, and it fits the width of a 58 mm
     * roll.
     */
    static final float DEFAULT_QR_MM = 30f;

    /**
     * The bar height of a barcode when none is set: 12 mm. A handheld scanner reads it, and it does not take much
     * room on a receipt.
     */
    static final float DEFAULT_BARCODE_MM = 12f;

    /**
     * The bar height of an EAN-13 when none is set: 15 mm, close to the size on products (22.85 mm with the digits
     * at full size).
     */
    static final float DEFAULT_EAN13_MM = 15f;

    private static final float POINTS_PER_MM = 72f / 25.4f;

    private final Paper paper;
    private final boolean rightToLeft;
    private final List<Block> blocks = new ArrayList<>();
    private PdfStyle style;
    private PdfFont font;
    private boolean failOnMissingGlyphs;
    private String letterheadName = "";
    private List<String> letterheadLines = List.of();
    private byte[] logo;
    private String footer = "";
    private boolean letterheadOnEveryPage;
    private String pageFooter = "";
    private PageNumbers pageNumbers = PageNumbers.WHEN_SEVERAL;
    private String watermark = "";
    private int watermarkColor = Watermark.DEFAULT_COLOR;
    private int printerDpi;
    private boolean arabicIndicDigits;
    private final List<Attachment> attachments = new ArrayList<>();
    private boolean pdfA3;
    private java.time.LocalDateTime printedOn;
    private String printedBy;
    private PageNumberStyle pageNumberStyle = PageNumberStyle.WORDS;
    /** Paper with a pre-printed letterhead: the top space in points, or negative = plain paper. */
    private float prePrintedTop = -1;
    private float totalsFontSize;

    /** How an attached file relates to the document, as PDF/A-3 names it ({@code /AFRelationship}). */
    public enum Relationship {
        /** The source the document was made from. */
        SOURCE("Source"),
        /** The document's own data in another format: the XML of an e-invoice. */
        DATA("Data"),
        /** Another, alternative copy of the same content. */
        ALTERNATIVE("Alternative"),
        /** A supplementary file. */
        SUPPLEMENT("Supplement"),
        UNSPECIFIED("Unspecified");

        private final String pdfName;

        Relationship(String pdfName) {
            this.pdfName = pdfName;
        }

        String pdfName() {
            return pdfName;
        }
    }

    record Attachment(String name, byte[] data, String mimeType, Relationship relationship) {
    }

    private PdfDocument(Paper paper, boolean rightToLeft) {
        this.paper = Objects.requireNonNull(paper, "paper");
        this.rightToLeft = rightToLeft;
    }

    /** A right-to-left document: Arabic. */
    public static PdfDocument rtl(Paper paper) {
        return new PdfDocument(paper, true);
    }

    /** A left-to-right document. */
    public static PdfDocument ltr(Paper paper) {
        return new PdfDocument(paper, false);
    }

    /** The page style; without it, {@link PdfStyle#forPaper(Paper)} is used. */
    public PdfDocument style(PdfStyle style) {
        this.style = Objects.requireNonNull(style, "style");
        return this;
    }

    /**
     * The font of this document, instead of the global font ({@code -Dsmart.report.pdf.font} or the bundled Noto
     * Naskh Arabic), without affecting any other document; {@code null} goes back to the global font.
     */
    public PdfDocument font(PdfFont font) {
        this.font = font;
        return this;
    }

    /**
     * A character that is in no font (neither the document font nor the fallback fonts) stops writing the
     * document with a {@link ReportException} that names the character, its code and the text it is in, instead
     * of printing {@code ?}. This is for invoices and financial documents, where a question mark in place of a
     * character is an error. Off unless set.
     */
    public PdfDocument failOnMissingGlyphs(boolean value) {
        this.failOnMissingGlyphs = value;
        return this;
    }

    /**
     * A small line under the first title with the print time: «طُبع في 2026-11-28 10:30» ("printed on ...").
     * Together with {@link #printedBy} on the same line. {@code null} = no line.
     */
    public PdfDocument printedOn(java.time.LocalDateTime when) {
        this.printedOn = when == null ? null : when.truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        return this;
    }

    /**
     * The user name on the print line under the first title: «بواسطة أحمد» ("by Ahmed"). {@code null} or blank =
     * no name.
     */
    public PdfDocument printedBy(String user) {
        this.printedBy = user == null || user.isBlank() ? null : user.strip();
        return this;
    }

    /** The page number format: «صفحة 1 من 3» ({@link PageNumberStyle#WORDS}, the default) or «1 / 3». */
    public PdfDocument pageNumberStyle(PageNumberStyle value) {
        this.pageNumberStyle = Objects.requireNonNull(value, "value");
        return this;
    }

    /**
     * Paper with the shop's letterhead already printed on it. The letterhead ({@link #letterhead} and the logo) is
     * not drawn, and on every page the content starts {@code topMm} millimetres below the margin, under the
     * printed letterhead.
     *
     * @throws IllegalArgumentException if negative
     */
    public PdfDocument prePrinted(float topMm) {
        if (!(topMm >= 0) || Float.isInfinite(topMm)) {
            throw new IllegalArgumentException("The top space is 0 mm or more: " + topMm);
        }
        this.prePrintedTop = topMm * POINTS_PER_MM;
        return this;
    }

    /** Plain paper: the letterhead is drawn as usual (cancels {@link #prePrinted}). */
    public PdfDocument plainPaper() {
        this.prePrintedTop = -1;
        return this;
    }

    /**
     * The font size of the totals rows in tables, separate from the row font ({@link PdfStyle#bodySize}). Zero =
     * the same as the rows.
     *
     * @throws IllegalArgumentException if not zero and smaller than {@link PdfStyle#MIN_FONT_SIZE}
     */
    public PdfDocument totalsFontSize(float points) {
        if (points != 0 && !(points >= PdfStyle.MIN_FONT_SIZE && points <= 72)) {
            throw new IllegalArgumentException("The totals size is 0 or " + PdfStyle.MIN_FONT_SIZE + " to 72 points: "
                    + points);
        }
        this.totalsFontSize = points;
        return this;
    }

    /**
     * A file inside the PDF, for example the XML of a signed e-invoice, so the archived file holds both. Its
     * relationship is {@link Relationship#DATA}.
     *
     * @see #attach(String, byte[], String, Relationship)
     */
    public PdfDocument attach(String name, byte[] data, String mimeType) {
        return attach(name, data, mimeType, Relationship.DATA);
    }

    /**
     * A file inside the PDF, with its relationship to the document. It appears in the attachments list of the PDF
     * reader, and with {@link #pdfA3} it becomes a file "associated" with the document, as archiving requires.
     *
     * @param name     the file name as it will appear: {@code "invoice.xml"}; each name only once
     * @param mimeType {@code "application/xml"}, {@code "text/xml"}...
     * @throws IllegalArgumentException if the name is blank or repeated, or the type is not {@code type/subtype}
     */
    public PdfDocument attach(String name, byte[] data, String mimeType, Relationship relationship) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(relationship, "relationship");
        if (name == null || name.isBlank() || name.contains("/") || name.contains("\\")) {
            throw new IllegalArgumentException("An attachment needs a file name without a path: " + name);
        }
        if (mimeType == null || !mimeType.matches("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+")) {
            throw new IllegalArgumentException("A MIME type is type/subtype, such as application/xml: " + mimeType);
        }
        String clean = name.strip();
        if (attachments.stream().anyMatch(file -> file.name().equals(clean))) {
            throw new IllegalArgumentException("The attachment " + clean + " is already in the document.");
        }
        attachments.add(new Attachment(clean, data.clone(), mimeType, relationship));
        return this;
    }

    /**
     * Writes the document as PDF/A-3B, the archiving format: the colour profile (sRGB) and the XMP metadata are
     * inside it, the fonts are embedded anyway, and the attached files ({@link #attach}) are associated with the
     * document. For an invoice that must be kept for years together with its XML. Off unless set.
     */
    public PdfDocument pdfA3(boolean value) {
        this.pdfA3 = value;
        return this;
    }

    /**
     * Draws every number in the document with Arabic-Indic digits ({@code ١٢٣}) instead of {@code 123}: the text,
     * the tables, the summary, the letterhead and the page numbers. The number keeps its place in the line
     * (Arabic-Indic digits read from left to right, like Latin digits), and the comma and full stop stay as
     * written. The content of a QR symbol, a barcode and the code written under a barcode stay Latin: the scanner
     * and the reader need them that way. Off unless set.
     */
    public PdfDocument arabicIndicDigits(boolean value) {
        this.arabicIndicDigits = value;
        return this;
    }

    /**
     * The resolution of the printer the document goes to (203 for the usual thermal printer, 300, 600...), so
     * every module of a barcode and a QR symbol takes a whole number of printer dots and sits on a dot. Without it
     * the narrowest bar is 0.33 mm, that is 2.64 dots on a 203 dpi head, so the driver decides which bar gets 2
     * dots and which gets 3. Uneven bars are a known cause of a barcode that does not scan.
     *
     * <p>With the resolution set: the narrowest barcode bar is the whole number of dots closest to 0.33 mm, and a
     * QR module is the whole number of dots closest to the requested size. Both are at least two dots, and a
     * barcode bar is never narrower than 0.25 mm. If the symbol does not fit the line, it shrinks one dot at a time
     * down to that limit, and then fails with a {@link ReportException} instead of shrinking to fractions of a
     * dot. Without it (or with zero) drawing is unchanged.</p>
     *
     * @param dpi dots per inch, from 72 to 2400; zero = none
     * @throws IllegalArgumentException if out of range
     */
    public PdfDocument printerDpi(int dpi) {
        if (dpi != 0 && (dpi < 72 || dpi > 2400)) {
            throw new IllegalArgumentException("The printer resolution must be between 72 and 2400 dpi, or 0 for"
                    + " none: " + dpi);
        }
        this.printerDpi = dpi;
        return this;
    }

    /**
     * A letterhead at the top of the first page: the organisation name in bold, lines under it (address,
     * phone...), then a separator line.
     */
    public PdfDocument letterhead(String name, String... lines) {
        this.letterheadName = name == null ? "" : name;
        List<String> kept = new ArrayList<>();
        if (lines != null) {
            for (String line : lines) {
                if (line != null && !line.isBlank()) {
                    kept.add(line);
                }
            }
        }
        this.letterheadLines = List.copyOf(kept);
        return this;
    }

    /**
     * The letterhead (and the logo) at the top of every page, not only the first, for long statements. A table
     * header that continues on the new page is drawn under it.
     */
    public PdfDocument letterheadOnEveryPage(boolean value) {
        this.letterheadOnEveryPage = value;
        return this;
    }

    /**
     * A small line at the bottom of every page, above its number: the address and phone, or
     * «هذا المستند صادر من نظام ...» ("this document was issued by the ... system"). Unlike
     * {@link #footer(String)}, which is drawn once after the last part.
     */
    public PdfDocument pageFooter(String text) {
        this.pageFooter = text == null ? "" : text;
        return this;
    }

    /**
     * A light grey watermark on every page: «مسودة» (draft), «صورة» (copy), «ملغاة» (cancelled), «مدفوع» (paid),
     * {@code DRAFT}, {@code COPY}. For an invoice copy or a copy of a document. {@code null} or blank text = no
     * watermark.
     *
     * @see #watermark(String, int)
     */
    public PdfDocument watermark(String text) {
        return watermark(text, Watermark.DEFAULT_COLOR);
    }

    /**
     * A watermark on every page in the colour {@code color} ({@code 0xRRGGBB}), transparent. For example red for
     * «ملغاة» (cancelled) and green for «مدفوع» (paid). {@code null} or blank text = no watermark.
     *
     * <ul>
     *   <li>Large and slanted along the page diagonal, in the middle, <b>above everything and transparent</b>: it
     *       is drawn after the page content, so it is not hidden under the background of the table header, the
     *       totals or the summary box, and the text under it stays readable. One line in the document font
     *       ({@link #font(PdfFont)}); Arabic is shaped and runs right to left.</li>
     *   <li>It shrinks to fit the paper: a long word or small paper makes the font smaller until the watermark
     *       fits inside the margins.</li>
     *   <li>On thermal paper ({@link Paper#roll()}) the watermark is horizontal, not slanted, and repeats along
     *       the receipt (one copy about every one and a half roll widths; a short receipt gets one copy in the
     *       middle), so it shows on any part of the receipt.</li>
     *   <li>Its opacity is 15%: on white paper black comes out light grey and red comes out light pink. It stays
     *       light even with a dark colour, and uses little ink even with {@link PdfStyle#inkSaver()}.</li>
     *   <li>It is not content: it does not move any part or change the receipt length, and a page that holds only
     *       the watermark is empty, so a page break ({@link #pageBreak()}) does not leave a page for it. It is
     *       marked {@code /Artifact}, so a reader that respects that leaves it out of copying and search. In a
     *       {@link PdfBatch} each document has its own watermark.</li>
     * </ul>
     */
    public PdfDocument watermark(String text, int color) {
        this.watermark = text == null ? "" : text;
        this.watermarkColor = color & 0xFFFFFF;
        return this;
    }

    /**
     * Signature boxes side by side: each box has a label («المستلم» recipient, «المحاسب» accountant, «الختم»
     * stamp) with space for the signature and a line under it. The first box is at the start of the line, and the
     * boxes stay together on the same page.
     */
    public PdfDocument signatures(String... labels) {
        List<String> kept = new ArrayList<>();
        if (labels != null) {
            for (String label : labels) {
                kept.add(label == null ? "" : label);
            }
        }
        if (kept.isEmpty()) {
            throw new IllegalArgumentException("At least one signature label is required.");
        }
        return add(new SignaturesBlock(List.copyOf(kept)));
    }

    /**
     * A QR symbol in the middle of the line, 30 mm on each side: a link, an invoice number, or the ZATCA
     * (Saudi Zakat, Tax and Customs Authority) invoice code ({@link com.smart.report.util.ZatcaQr#payload}).
     *
     * @throws IllegalArgumentException if the text is blank or longer than a QR symbol can hold
     * @see #qrCode(String, float, Align)
     */
    public PdfDocument qrCode(String text) {
        return qrCode(text, DEFAULT_QR_MM, Align.CENTER);
    }

    /** A QR symbol 30 mm on each side, at the start, middle or end of the line, by the document direction. */
    public PdfDocument qrCode(String text, Align align) {
        return qrCode(text, DEFAULT_QR_MM, align);
    }

    /**
     * A QR symbol drawn as vector squares (not an image), so it stays sharp on any printer. Around it is a blank
     * zone 4 modules wide, in addition to {@code sizeMm}, which the scanner needs. The symbol is never split: if
     * the rest of the page has no room for it, it moves whole to the next page, and if it is wider than the page
     * (a 58 mm roll, for example) it shrinks until it fits together with its blank zone.
     *
     * @param text   the text the symbol holds; Arabic is encoded as UTF-8
     * @param sizeMm the side of the symbol in millimetres, without the blank zone
     * @param align  {@link Align#START} at the start of the line (the right side in an Arabic document),
     *               {@link Align#CENTER}, or {@link Align#END}
     * @throws IllegalArgumentException if the text is blank or longer than a QR symbol can hold (about 2300
     *                                  bytes), or the size is not positive
     */
    public PdfDocument qrCode(String text, float sizeMm, Align align) {
        if (!(sizeMm > 0) || Float.isInfinite(sizeMm)) {
            throw new IllegalArgumentException("The QR code size must be a positive number of millimetres.");
        }
        return add(new QrBlock(QrSymbol.encode(text), sizeMm * POINTS_PER_MM, align == null ? Align.CENTER : align));
    }

    /**
     * A Code 128 barcode in the middle of the line, with 12 mm bars and the text itself written under them: a
     * shipment number, a waybill, an invoice, or an item code.
     *
     * @throws IllegalArgumentException if the text is blank or contains a character other than English letters,
     *                                  digits and symbols
     * @see #barcode(String, float, Align)
     */
    public PdfDocument barcode(String text) {
        return barcode(text, DEFAULT_BARCODE_MM, Align.CENTER);
    }

    /** A Code 128 barcode with 12 mm bars, at the start, middle or end of the line, by the document direction. */
    public PdfDocument barcode(String text, Align align) {
        return barcode(text, DEFAULT_BARCODE_MM, align);
    }

    /**
     * A Code 128 barcode drawn as vector bars (not an image), with the text written under it from left to right,
     * even in an Arabic document, so a person reading it by eye reads it as it is. The narrowest bar is 0.33 mm,
     * with a blank zone 10 bars wide on each side. If that is wider than the line (a 58 mm roll, for example), the
     * bars get narrower until it fits. The barcode and its text line are never split across two pages.
     *
     * <p>Code 128 holds only English letters, digits and symbols, and a long number makes the barcode wider. Text
     * that would need bars narrower than 0.25 mm to fit the line (more than about 15 characters on a 58 mm roll)
     * fails when the document is written, with a {@link com.smart.report.ReportException}, instead of producing a
     * barcode that does not scan.</p>
     *
     * @param text     the text the barcode holds
     * @param heightMm the height of the bars in millimetres, without the text line under them
     * @param align    {@link Align#START} at the start of the line (the right side in an Arabic document),
     *                 {@link Align#CENTER}, or {@link Align#END}
     * @throws IllegalArgumentException if the text is blank or contains a character other than English letters,
     *                                  digits and symbols (an Arabic letter, for example), or the height is not
     *                                  positive
     */
    public PdfDocument barcode(String text, float heightMm, Align align) {
        if (!(heightMm > 0) || Float.isInfinite(heightMm)) {
            throw new IllegalArgumentException("The barcode height must be a positive number of millimetres.");
        }
        return add(new BarcodeBlock(BarcodeSymbol.encode(text), heightMm * POINTS_PER_MM,
                align == null ? Align.CENTER : align));
    }

    /**
     * An EAN-13 barcode in the middle of the line, with 15 mm bars and the 13 digits under them: the barcode on
     * products.
     *
     * @param digits 12 digits (the check digit is calculated) or 13 (the check digit is verified)
     * @throws IllegalArgumentException if not 12 or 13 digits, or the check digit is wrong
     * @see #ean13(String, float, Align)
     */
    public PdfDocument ean13(String digits) {
        return ean13(digits, DEFAULT_EAN13_MM, Align.CENTER);
    }

    /**
     * An EAN-13 barcode drawn as vector bars: the narrowest bar is 0.33 mm (the nominal size), the guard bars are
     * longer than the rest, the digits are under the bars as on products, and the first digit is in the blank
     * zone on the left. If it does not fit the line, the bars get narrower down to 0.264 mm (80% of the size, the
     * smallest the specification allows), and after that writing the document fails with a
     * {@link ReportException}. With {@link #printerDpi}, every bar is a whole number of printer dots.
     *
     * @param heightMm the height of the bars in millimetres, without the digits
     * @throws IllegalArgumentException if not 12 or 13 digits, or the check digit is wrong, or the height is not
     *                                  positive
     */
    public PdfDocument ean13(String digits, float heightMm, Align align) {
        if (!(heightMm > 0) || Float.isInfinite(heightMm)) {
            throw new IllegalArgumentException("The barcode height must be a positive number of millimetres.");
        }
        return add(new Ean13Block(Ean13Symbol.encode(digits), heightMm * POINTS_PER_MM,
                align == null ? Align.CENTER : align));
    }

    /**
     * An image in the document body: a stamp, a signature, or an item picture. The height is calculated from the
     * image aspect ratio, and the image is never split: if the rest of the page has no room for it, it moves whole
     * to the next page, and if it is wider than the line or taller than a full page, it shrinks, keeping its
     * aspect ratio.
     *
     * @param image   PNG or JPEG (PNG transparency is kept: a stamp does not cover what is under it)
     * @param widthMm the width in millimetres
     * @param align   {@link Align#START} at the start of the line (the right side in an Arabic document),
     *                {@link Align#CENTER}, or {@link Align#END}
     * @throws IllegalArgumentException if it is not a readable PNG or JPEG image, or the width is not positive
     */
    public PdfDocument image(byte[] image, float widthMm, Align align) {
        if (!(widthMm > 0) || Float.isInfinite(widthMm)) {
            throw new IllegalArgumentException("The image width must be a positive number of millimetres.");
        }
        Objects.requireNonNull(image, "image");
        int[] pixels = ImageParts.pixels(image);
        return add(new ImageBlock(image.clone(), widthMm * POINTS_PER_MM, pixels, align == null ? Align.CENTER : align));
    }

    /**
     * An image from a file; like {@link #image(byte[], float, Align)}.
     *
     * @throws java.io.UncheckedIOException if the file cannot be read
     */
    public PdfDocument image(java.nio.file.Path file, float widthMm, Align align) {
        try {
            return image(java.nio.file.Files.readAllBytes(file), widthMm, align);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("The image " + file + " could not be read: " + e.getMessage(), e);
        }
    }

    /** The organisation logo (PNG or JPEG) next to the letterhead, on the side where the page starts. */
    public PdfDocument logo(byte[] image) {
        this.logo = image == null || image.length == 0 ? null : image.clone();
        return this;
    }

    /** A title in the middle of the page, in bold. */
    public PdfDocument title(String text) {
        return add(new TitleBlock(text));
    }

    /** A line under the title, centred, in a softer colour; the period, for example. */
    public PdfDocument subtitle(String text) {
        return add(new SubtitleBlock(text));
    }

    /** A section heading in bold at the start of the line; above a table, for example. */
    public PdfDocument heading(String text) {
        return add(new HeadingBlock(text));
    }

    /** A paragraph at the start of the line, wrapped to the page width. */
    public PdfDocument text(String text) {
        return text(text, Align.START);
    }

    public PdfDocument text(String text, Align align) {
        return add(new TextBlock(text, align == null ? Align.START : align));
    }

    /** Vertical space in points. */
    public PdfDocument space(float points) {
        return add(new SpaceBlock(Math.max(0, points)));
    }

    /**
     * A label and its value ("رقم العربية: 1234"). Consecutive fields are laid out two per line, the first at the
     * start of the line and the second at the middle of the page.
     */
    public PdfDocument field(String label, String value) {
        String[] field = {label == null ? "" : label, value == null ? "" : value};
        if (!blocks.isEmpty() && blocks.get(blocks.size() - 1) instanceof FieldsBlock fields) {
            fields.fields().add(field);
        } else {
            List<String[]> fields = new ArrayList<>();
            fields.add(field);
            blocks.add(new FieldsBlock(fields));
        }
        return this;
    }

    public PdfDocument table(PdfTable table) {
        return add(new TableBlock(Objects.requireNonNull(table, "table")));
    }

    /** A table built in place: {@code table(t -> t.column(...).row(...))}. */
    public PdfDocument table(Consumer<PdfTable> build) {
        PdfTable table = PdfTable.create();
        build.accept(table);
        return table(table);
    }

    /**
     * A summary line: a label at the start of the line and its value next to it. Consecutive lines form one
     * box.
     */
    public PdfDocument summary(String label, String value) {
        return summaryLine(label, value, false);
    }

    /**
     * The line the reader is looking for («الباقى» balance due, «الإجمالى» total): larger bold text on a distinct
     * background.
     */
    public PdfDocument summaryTotal(String label, String value) {
        return summaryLine(label, value, true);
    }

    /**
     * The total line of the summary, with its value in red if negative: the closing balance of an account
     * statement.
     */
    PdfDocument summaryTotalNegativeInRed(String label, String value) {
        return summaryLine(label, value, true, true);
    }

    private PdfDocument summaryLine(String label, String value, boolean total) {
        return summaryLine(label, value, total, false);
    }

    private PdfDocument summaryLine(String label, String value, boolean total, boolean negativeInRed) {
        SummaryLine line = new SummaryLine(label == null ? "" : label, value == null ? "" : value, total,
                negativeInRed);
        if (!blocks.isEmpty() && blocks.get(blocks.size() - 1) instanceof SummaryBlock summary) {
            summary.lines().add(line);
        } else {
            List<SummaryLine> lines = new ArrayList<>();
            lines.add(line);
            blocks.add(new SummaryBlock(lines));
        }
        return this;
    }

    /**
     * The next part starts on a new page: a section per vehicle, or the appendices after the statement.
     *
     * <ul>
     *   <li>A page break never produces an empty page. If the current page has nothing under the letterhead yet
     *       (a break at the start of the document, or two breaks in a row), it does nothing. A break with nothing
     *       drawn after it (the end of the document, or only spaces and blank text after it) leaves no page, and
     *       no page that holds only the footer.</li>
     *   <li>The new page is like any page that long content opens: the letterhead is at its top if
     *       {@link #letterheadOnEveryPage(boolean)} is set, and the page numbering continues («صفحة 2 من 3»,
     *       "page 2 of 3").</li>
     *   <li>After a table that ran over several pages, the break comes after its last row (and the totals), and
     *       the table header is not repeated on the new page: the table is finished. To start each group of rows
     *       on its own page, make one table per group with a break between them.</li>
     *   <li>On thermal paper ({@link Paper#roll()}) the receipt is one page as long as its content, so a break
     *       <b>does nothing</b>. The same document builds for any paper without failing on the roll, and the
     *       receipt is exactly the same as without the break (the fields and summary lines around it stay
     *       together). To cut each receipt on its own, print them as a {@link PdfBatch}.</li>
     * </ul>
     *
     * <p>Sections each with their own numbering and letterhead (a statement per driver in one file) are a
     * {@link PdfBatch}, not a page break.</p>
     */
    public PdfDocument pageBreak() {
        // على اللفة ما بيتضافش خالص: لو اتضاف، البيانات وسطور الملخص اللي قبله وبعده كانت هتبقى صفين وصندوقين.
        return paper.roll() ? this : add(new PageBreakBlock());
    }

    /** A small closing sentence in the middle of the page after the last part. */
    public PdfDocument footer(String text) {
        this.footer = text == null ? "" : text;
        return this;
    }

    public PdfDocument pageNumbers(PageNumbers mode) {
        this.pageNumbers = Objects.requireNonNull(mode, "mode");
        return this;
    }

    private PdfDocument add(Block block) {
        blocks.add(block);
        return this;
    }

    public Paper paper() {
        return paper;
    }

    public boolean rightToLeft() {
        return rightToLeft;
    }

    PdfStyle resolvedStyle() {
        return style != null ? style : PdfStyle.forPaper(paper);
    }

    List<Block> blocks() {
        return blocks;
    }

    /** The file title (and the print job name): the text of the first non-{@code null} {@link #title(String)}. */
    String firstTitle() {
        for (Block block : blocks) {
            if (block instanceof TitleBlock title && title.text() != null) {
                return title.text();
            }
        }
        return null;
    }

    String letterheadName() {
        return letterheadName;
    }

    List<String> letterheadLines() {
        return letterheadLines;
    }

    byte[] logoBytes() {
        return logo;
    }

    String footerText() {
        return footer;
    }

    PageNumbers pageNumberMode() {
        return pageNumbers;
    }

    boolean letterheadOnEveryPage() {
        return letterheadOnEveryPage;
    }

    String pageFooterText() {
        return pageFooter;
    }

    String watermarkText() {
        return watermark;
    }

    /** The text of the «طُبع في ... بواسطة ...» ("printed on ... by ...") line, or {@code null} without one. */
    String printedLine() {
        if (printedOn == null && printedBy == null) {
            return null;
        }
        String when = printedOn == null ? null
                : printedOn.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        if (rightToLeft) {
            return (when == null ? "" : "طُبع في " + when) + (when != null && printedBy != null ? " " : "")
                    + (printedBy == null ? "" : "بواسطة " + printedBy);
        }
        String line = (when == null ? "" : "Printed on " + when) + (when != null && printedBy != null ? " " : "")
                + (printedBy == null ? "" : "by " + printedBy);
        return when == null ? Character.toUpperCase(line.charAt(0)) + line.substring(1) : line;
    }

    PageNumberStyle pageNumberStyleValue() {
        return pageNumberStyle;
    }

    boolean prePrinted() {
        return prePrintedTop >= 0;
    }

    float prePrintedTop() {
        return Math.max(0, prePrintedTop);
    }

    float totalsFontSize() {
        return totalsFontSize;
    }

    List<Attachment> attachments() {
        return attachments;
    }

    boolean pdfA3() {
        return pdfA3;
    }

    boolean arabicIndicDigits() {
        return arabicIndicDigits;
    }

    int printerDpi() {
        return printerDpi;
    }

    int watermarkColor() {
        return watermarkColor;
    }

    /** The document as PDF bytes. */
    public byte[] toBytes() throws ReportException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeTo(out);
        return out.toByteArray();
    }

    /** Writes the document to {@code out} and does not close it. */
    public void writeTo(OutputStream out) throws ReportException {
        Objects.requireNonNull(out, "out");
        PageWriter.write(this, font, failOnMissingGlyphs, out);
    }

    /** Saves the document to a file, and creates any missing folders in its path. */
    public void save(Path file) throws ReportException {
        Objects.requireNonNull(file, "file");
        byte[] bytes = toBytes();
        try {
            if (file.toAbsolutePath().getParent() != null) {
                Files.createDirectories(file.toAbsolutePath().getParent());
            }
            Files.write(file, bytes);
        } catch (IOException e) {
            throw new ReportException("Failed to write the PDF document to " + file, e);
        }
    }
}
