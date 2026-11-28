---
name: pdf-print
description: Adds PDF output and printing to a Java program with the shared smart-report library (com.github.MohamedSalah-13.smart-report-plugin on JitPack) instead of new print code - an Arabic-ready document on A4, A5 or an 80/58 mm thermal roll with letterhead, tables with totals, a summary, the amount in words (تفقيط), signature boxes, a QR code (and the Saudi ZATCA invoice QR), a barcode, page numbers, page breaks, a JavaFX preview, saving and printing, and many documents (every driver's statement for the month) as one file and one print job. Use when the user wants to print or produce a PDF from Java - an invoice, a statement, a receipt, a report, a list - including "print", "PDF", "receipt printer", "طباعة", "اطبع الكشف", "اطبع كل الكشوف", "صفحة جديدة", "فاتورة PDF", "إيصال", "طابعة حرارية", "التفقيط", "رمز QR", "باركود", "فاتورة ضريبية مبسطة", "معاينة قبل الطباعة", "زي الطباعة اللى فى naqlat" - and when a printed Arabic page comes out wrong (table reversed, text cut with "...", a minus sign or date on the wrong side, letters not joined), a long page is shrunk onto one sheet, or a program still prints a JavaFX node or uses iText.
---

# PDF print

Give a Java program a printed page by describing it - letterhead, fields, tables, a summary - and let the
`smart-report` library write the PDF, show it, save it and print it. A finished integration is one class
that builds the document from the program's data, one call that opens the preview, and one dependency.

Library source and README: `https://github.com/MohamedSalah-13/smart-report-plugin` (a local clone is usually
at `F:\git\smart-report-plugin`). Read its README when you need an API detail this file does not cover. A
finished integration to read as an example: `StatementPdf` and `StatementPrint` in the `naqlat` project
(usually `F:\git\naqlat`, under `desktop/src/main/java/app/naqlat/desktop/ui/statement/`).

## What the library is, in one paragraph

Four small Maven modules served from JitPack. `smart-report-pdf` writes the page with PDFBox alone and
carries its own Arabic font (Noto Naskh), so the output is the same on every computer; it also sends a PDF to
a printer by name. `smart-report-fx` adds a JavaFX preview window on top of it. `smart-report-excel` and
`smart-report-jasper` are separate and only for a program that needs them. The program never shapes Arabic,
never reverses a table and never measures text: it writes columns and cells in the order a reader reads them
and says whether the page runs right to left.

## Before you touch anything

1. Look at `git status`. If the tree has someone else's uncommitted work, or another session is active in the
   repository, say so and agree with the user where the change goes before editing.
2. Find, and note for the steps below:
   - what is printed and from which screen or service, and the data object behind it;
   - how the program already prints, if it does - a JavaFX `PrinterJob` on a node, iText, Jasper, a hand-made
     `Graphics2D` page. That code is what this replaces; read it for the content and the order of the page,
     which the customer has usually already approved;
   - how the program formats money, quantities and dates (a `Ui.amount`, a `NumberFormat`...). The library
     prints the strings it is given, so the page must use the same formatters as the screen;
   - whether the program is JavaFX, and whether it has a `module-info.java`;
   - the packaging script (`jpackage ...`) if there is one;
   - the paper the customer prints on. Ask if nothing says: A4 and A5 are both common, and a statement that
     fits A5 upright saves half the paper. A cashier's receipt usually goes to a thermal printer with an
     80 mm roll (58 mm on small ones) - see "Receipts on a thermal printer" below.
3. If the program uses **iText**, tell the user before going on: iText 7 and 8 are AGPL, which a closed
   program cannot ship without a commercial licence. This library exists partly to avoid that. Do not add
   iText to make something work.

## The steps

### 1. Dependency

Use the newest tag of the library:
`gh api repos/MohamedSalah-13/smart-report-plugin/tags --jq '.[0].name'`. What needs which version: the
preview window (`smart-report-fx`) `v2.1.0`; column formats in the generic report `v2.2.0`; `AmountInWords`
`v2.3.0`; thermal rolls, the letterhead on every page, `pageFooter` and `signatures` `v2.4.0`; `qrCode`,
`ZatcaQr` and the readable receipt preview `v2.5.0`; `barcode`, and signature boxes that stay with the summary
above them, `v2.6.0`; `printOrShow` and the preview's Ctrl+P / Ctrl+S, `ZatcaQr.phase2`, the Gulf currencies and
amounts in English words, and Arabic in Jasper PDFs `v2.7.1`; totals in the generic report, and the first line of
Arabic in a Jasper box a little too short for it, `v2.8.0`; `pageBreak()`, `PdfBatch`, a font per document,
`failOnMissingGlyphs` and the symbols drawn from Liberation Sans, `v2.9.0`; a generic Excel report whose
title starts or ends with an apostrophe, `v2.9.1`; `watermark` and subtotals by group (`groupBy`), `v2.10.0`. A program on an older tag that needs one of these
moves to the newest tag.

```xml
<repositories>
    <!-- Named so that it is asked before JitPack: JitPack answers some coordinates it does not have with an
         empty file, which breaks the build on a machine with an empty local repository. -->
    <repository>
        <id>central</id>
        <url>https://repo.maven.apache.org/maven2</url>
        <snapshots>
            <enabled>false</enabled>
        </snapshots>
    </repository>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<!-- The page itself, saving and printing. Brings PDFBox and ZXing core (for the QR code). -->
<dependency>
    <groupId>com.github.MohamedSalah-13.smart-report-plugin</groupId>
    <artifactId>smart-report-pdf</artifactId>
    <version>v2.10.0</version>
</dependency>
<!-- Only for a JavaFX program that wants the preview window. JavaFX itself is the program's own. -->
<dependency>
    <groupId>com.github.MohamedSalah-13.smart-report-plugin</groupId>
    <artifactId>smart-report-fx</artifactId>
    <version>v2.10.0</version>
</dependency>
```

In a multi-module project put the version in the parent's `<properties>` and `<dependencyManagement>`, the
way the project already manages its other versions. A backend that only writes PDFs takes `smart-report-pdf`
alone.

### 2. Build the document

Write one class that turns the program's data into a `PdfDocument` and nothing else - no dialogs, no JavaFX,
no database calls. That is what makes it testable, and what lets the preview rebuild it for another paper.

```java
public final class InvoicePdf {

    private InvoicePdf() {
    }

    public static PdfDocument document(Invoice invoice, Company company, Paper paper) {
        PdfDocument document = PdfDocument.rtl(paper)                 // PdfDocument.ltr(paper) for English
                .letterhead(company.name(), company.address(), company.phone())
                .title("فاتورة مبيعات")
                .field("رقم الفاتورة", invoice.number())
                .field("التاريخ", Ui.date(invoice.date()))
                .field("العميل", invoice.customerName());

        PdfTable lines = PdfTable.create()
                .column("الصنف", 40)                                   // weight; text starts at the line's start
                .column("الكمية", 14, Align.CENTER)
                .column("السعر", 20, Align.CENTER)
                .column("الإجمالى", 26, Align.CENTER);
        for (InvoiceLine line : invoice.lines()) {
            lines.row(line.itemName(), Ui.number(line.quantity()), Ui.amount(line.price()), Ui.amount(line.total()));
        }
        lines.totals("الإجمالى", "", "", Ui.amount(invoice.total()));

        return document.table(lines)
                .summary("الخصم", Ui.amount(invoice.discount()))
                .summaryTotal("الصافى", Ui.amount(invoice.net()))
                .footer(company.footerLine());
    }
}
```

What each part is:

- `letterhead(name, lines...)` - the company on top of the first page, with a rule under it; `.logo(bytes)`
  puts a PNG or JPEG beside it.
- `title`, `subtitle` - centred. `heading` - a bold line over a section. `text` - a paragraph.
- `field(label, value)` - "label: value"; consecutive fields sit two to a row.
- **Negative amounts in red / a bold cell** (from `v2.11.0`): `table.negativeInRed(balanceColumn)` on a
  statement, or `table.cellStyle((row, column, text) -> ... ? CellStyle.bold() : CellStyle.PLAIN)`; the generic
  report takes `.parameter(PdfReportProvider.NEGATIVE_IN_RED, true)`. Do not prefix numbers with symbols to
  mark them.
- **A stamp or signature image** in the body (from `v2.11.0`): `document.image(pngBytes, widthMm, Align.END)` or
  `image(path, widthMm, align)`; it keeps its shape, never splits, and moves whole to the next page.
- `table(...)` - columns with weights, rows, and `totals(...)` rows drawn as a band with one cell under each
  column. Several tables are fine; the column headings repeat on every page a table runs onto.
- `summary(label, value)` lines and one `summaryTotal(label, value)` - the figure the reader looks for.
- `footer(text)` - a closing sentence drawn once, after the last part; and page numbers by themselves once
  there is more than one page.
- `letterheadOnEveryPage(true)` - the letterhead (and logo) on top of every page, for long statements; a table
  that continues has its header under it. Without it the letterhead is on the first page only.
- `pageFooter(text)` - a small line at the bottom of **every** page, above the page number (the company's
  address or a "printed from" line). Not the same as `footer`.
- `signatures("المستلم", "المحاسب", "الختم")` - boxes side by side in reading order, each with its label and
  room to sign, kept together on one page. Put it last, where the old paper had its signature lines. From
  `v2.6.0` the boxes also stay with the summary before them (and any text between, such as the
  amount in words): the summary moves to the next page with them instead of the boxes landing there alone; if
  that group cannot move, the last line of the text before the boxes goes with them.
- `pageBreak()` - the next part starts on a new page (from `v2.9.0`): an annex after a
  statement, a section per truck in one report. It never leaves a blank page: at the top of a page (nothing
  drawn yet below the letterhead) it does nothing, and the page it opens is kept only if something is drawn on
  it, so a loop may add a section and a break each time, and a break before notes that turn out empty is fine. The new page has the letterhead only with `letterheadOnEveryPage(true)`,
  and the page numbers carry on. On a thermal roll it does nothing (a receipt is one page). Several customers'
  or drivers' statements are **not** one document with breaks - their page numbers would run on from one to
  the next - but a `PdfBatch` (see "Variants").
- `watermark("صورة")` - a large light word over the content of every page (from `v2.10.0`): «صورة» on a
  copy of an invoice, «مسودة» or `DRAFT` before it is final, «ملغاة», «مدفوع», `COPY`. Use it where the old
  program printed such a word or stamp, or kept a second "copy" print. `watermark(text, 0xC00000)` takes a
  colour, drawn at 15% opacity so it stays light. It lies over everything, fills included, and the text under it
  stays readable. It is diagonal on paper and horizontal, repeated down the receipt, on a roll; it shrinks to
  fit, and a page with only the watermark is still empty. `null` or blank
  means none, so `document.watermark(isCopy ? "صورة" : null)` is fine.
- The amount in words, when the old paper had it ("فقط ... لا غير"):
  `document.text(AmountInWords.arabic(invoice.net(), AmountInWords.Currency.EGP))` (from
  `com.smart.report.util`, in `smart-report-api`, which `smart-report-pdf` brings). Ready currencies: `EGP`,
  `SAR`, `AED`, `KWD`, `USD`, from `v2.7.1` `QAR`, `BHD`, `OMR`, `JOD`, and from `v2.11.0` `SDG`, `GBP`, `YER`,
  `MAD`, `IQD`, `LYD`, `TND`, `DZD`, `SYP`, `LBP`, `TRY`, `EUR`, also by code with `Currency.forCode(iso)` (an
  `Optional`, empty for an unknown code: print no line then); another is a
  `new AmountInWords.Currency(...)` with its noun forms (see the README). `arabicWords` gives the words without
  "فقط ... لا غير". For a bilingual invoice, `AmountInWords.english(amount, AmountInWords.EnglishCurrency.EGP)`
  gives "Seven thousand two hundred Egyptian pounds only" (from `v2.7.1`). Pass the `BigDecimal` itself, not a
  formatted string. A negative amount is rejected unless a `Negative` is passed (from `v2.11.0`):
  `AmountInWords.arabic(balance, Currency.EGP, Negative.suffix("(دائن)"))` writes the absolute value with the word
  after the whole text.
- `qrCode(text)` - a QR code, 30 mm and centred; `qrCode(text, Align.START)` puts it at the start of the line
  (right on an Arabic page), and `qrCode(text, sizeMm, align)` sets its side in millimetres. It is drawn as
  vector squares with its empty border, never split across pages, and shrinks if the line is too narrow. Arabic
  text is fine. Use it for a link, an invoice number, or the ZATCA code below - not an image made with another
  library and passed to `logo`.
- `barcode(text)` - a Code 128 barcode for a shipment, waybill, invoice or item number that a hand scanner reads:
  bars 12 mm tall, centred, with the code written under them; `barcode(text, heightMm, align)` sets the height
  and the place. English letters, digits and signs only (Arabic is rejected). The narrowest bar is 0.33 mm and
  narrows to 0.25 mm to fit; a code too long for the line even then makes `toBytes`/`save` throw a
  `ReportException` - keep codes short on a 58 mm roll (about 15 characters; digits pack two to a pattern).
- **Cash drawer** (from `v2.11.0`): `PdfPrinter.openCashDrawer(receiptPrinter)` (ESC/POS `ESC p`, pin 2,
  100 ms; or `(printer, DrawerPin.PIN_5, ms)`). Do not write `javax.print` raw-byte code for it. Copies on a roll
  already go out one job each, so each is cut. `PdfPrinter.declaresPaper(printer, Paper.A5)` lets a settings
  screen warn before the first print.
- **On a thermal printer, give the document its resolution** (from `v2.11.0`): `.printerDpi(203)` (or 300...).
  Every bar and QR module then is a whole number of printer dots on the dot grid; without it a 0.33 mm bar is
  2.64 dots and the driver makes bars uneven. A symbol that cannot fit with at least 2 dots (and 0.25 mm for bars)
  throws a `ReportException` from `toBytes`/`save`.
- **EAN-13** (product barcodes, from `v2.11.0`): `document.ean13("622100012345")` (12 digits, check digit
  computed; 13 digits are verified and a wrong check digit is rejected).
- **Item labels** (from `v2.11.0`): `PdfLabels.roll(widthMm, heightMm).across(2, gapMm)` or
  `PdfLabels.sheet(Paper.A4, columns, rows).margins(topMm, sideMm)`, then
  `add(PdfLabels.Item.of(name, priceText, barcode).withOldPrice(old), copies)` and `toBytes()`; print with
  `PdfPrinter.print(bytes, printer, labels.paper(), 1)`. A valid 13-digit code prints as EAN-13, anything else as
  Code 128. `longName(ELLIPSIS|SHRINK|HIDE)`, `offset(xMm, yMm)` per printer, `printerDpi(203)`. Skip units with
  no barcode: `Item.of` refuses them.
- A QR code or barcode **as an image**, for a screen or a web endpoint (from `v2.11.0`):
  `SymbolImage.qrPng(text, pixelsPerModule)`, `SymbolImage.barcodePng(text, pixelsPerModule, heightPixels)`, or
  `SymbolImage.qr(...)` / `barcode(...)` for a `BufferedImage`. Quiet zone included, black and white, whole pixels
  per module: do not write the ZXing-to-image loop by hand, and do not scale the image by a fraction.
- A **code to find a printed document by scanning it** (a return, a search box), from `v2.11.0`:
  `DocumentCode.DEFAULT.encode(type, number)` (`com.smart.report.util`) gives 13 digits with a check digit;
  print it with `barcode(...)`. `DocumentCode.DEFAULT.parse(scanned)` gives `Optional<Scanned(type, number)>`,
  empty for anything that is not such a code, and accepts Arabic-Indic digits. Digits only, never letters: an
  Arabic keyboard layout turns a scanned Latin letter into an Arabic one. Store nothing: compute it again on reprint.
- **The Saudi simplified tax invoice QR** (ZATCA e-invoicing, phase 1) - when the program issues invoices in
  Saudi Arabia, the receipt or invoice ends with this code:

  ```java
  String qr = ZatcaQr.payload(company.legalName(), company.vatNumber(),     // com.smart.report.util
          invoice.issuedAt().atZone(ZoneId.of("Asia/Riyadh")).toInstant(),  // a LocalDateTime made an Instant
          invoice.totalWithVat(), invoice.vatTotal());                       // BigDecimal, not formatted text
  document.qrCode(qr);
  ```

  It returns the Base64 of the five TLV fields (seller name, VAT number, time, total with VAT, VAT) with each
  length in UTF-8 bytes, so an Arabic seller name is right. The VAT number must be 15 digits beginning and
  ending with 3, and the amounts must not be negative; otherwise it throws `IllegalArgumentException` -
  validate where the company's settings are saved, not at the till. Use the seller name exactly as registered,
  and the same totals the page prints. **Phase 2 (integration)** invoices carry four more fields (from
  `v2.7.1`): the invoice hash, its signature, and the public key and ZATCA's signature from the device's
  certificate (CSID). They come from signing the invoice's XML with the program's ZATCA certificate, which
  `ZatcaQr` does not do; the program passes them in:

  ```java
  String qr = ZatcaQr.phase2(company.legalName(), company.vatNumber(), issuedAt,
                  invoice.totalWithVat(), invoice.vatTotal())
          .invoiceHash(signed.digestValue())        // ds:DigestValue, Base64 text
          .signature(signed.signatureValue())       // ds:SignatureValue, Base64 text
          .certificate(signed.certificate())        // the X509Certificate the invoice was signed with
          .payload();
  document.qrCode(qr, 45, Align.CENTER);            // dense: 45 mm, which still fits a 58 mm roll
  ```

  The values must match the signed XML (the same name, VAT number, totals and issue time). If the program has
  no signing step yet, tell the user that phase 2 needs it rather than printing a phase 1 code for a phase 2
  business. A standard (B2B) invoice gets its QR from ZATCA at clearance: print that one as it is.

Things that matter and are easy to get wrong:

- **Write columns and cells in reading order.** The first column is the one the reader starts with. The
  library puts it on the right of an Arabic page; reversing arrays yourself puts every figure under the
  wrong heading.
- **Give the library plain text.** Do not shape Arabic, add direction marks or pad with spaces. Negative
  amounts, dates, percentages and phone numbers written in groups already read the way they are written
  inside an Arabic sentence.
- **Format every number and date with the program's own formatter**, the same one the screen uses. A page
  that says `16632.00` beside a screen that says `16,632` looks like a different program.
- **Leave out what is empty** - a payments table with no payments, a discount line of zero - the way the
  existing page did. Follow the old page's content and order unless the user asks for a change.
- **Do not truncate long text.** It wraps inside its column; give the column that holds free text the
  largest weight.
- A balance that can be negative is clearer in words ("زيادة مدفوعة") than with a minus sign, if the
  customer's paper writes it that way.

Appearance, only when asked: `document.style(PdfStyle.forPaper(paper).withHeaderColors(0x2C3E50, 0xFFFFFF))`,
`.withStripes(true)`, `.withGrid(false)`, `.withMargin(28f)`, or `.inkSaver()` for no fills at all. Without a
style the page gets smaller type and margins on A5 by itself.

### 3. Show it - preview, print, save

In a JavaFX program, open the preview where the old print button was:

```java
print.setOnAction(event -> {
    Invoice invoice = currentInvoice();                 // read the data here, on the JavaFX thread
    Company company = services.company().current();
    PdfPreview.of(paper -> InvoicePdf.document(invoice, company, paper))
            .papers(Paper.A4, Paper.A5)                 // one paper = no paper choice in the window
            .title("فاتورة " + invoice.number())
            .fileName("فاتورة " + invoice.number())   // from v2.11.0: / : * ? become -, no cleaning needed
            .rightToLeft(true)                          // Arabic window; false for English
            .remember("invoice")                        // last printer and paper, per kind of document
            .stylesheet(Ui.STYLESHEET)                  // the program's CSS, if it has one
            .show(getScene().getWindow());
});
```

The window shows the pages as the printer will receive them, lets the person choose the printer, the paper
and the copies, and has "طباعة" and "حفظ PDF". It does not block the program.

**Several kinds of paper, each with its own settings** (from `v2.11.0`): do not write a settings table or a
preferences class. Declare the kinds with `PrintSettings.of(store, roles, DocumentKind.of(key, label, defaults,
papers...)...)` (`com.smart.report.print`, in `smart-report-api`), read `settings.profile(key)` **at print time**
(never cache it in a screen), turn its printer into a name with `settings.printerName(profile)` (empty = the
default printer), and put `PrintSettingsPane.of(settings).rightToLeft(true).build()` in the program's settings
screen. Use printer roles (`"normal"`, `"thermal"`) rather than printer names in the defaults.

Then the print button is one call: `PdfPreview.of(paper -> ...).fileName(...).print(settings, "sale-receipt",
owner)` returns a `CompletableFuture<PrintResult>` (`Printed`, `Shown`, `Saved`, `Cancelled`, `Failed`). It prints,
previews, saves or asks as the profile says. A missing printer opens the preview with a notice instead of printing
elsewhere. A "Preview" button next to "Print" is `print(settings, kind, p -> p.withMode(OutputMode.PREVIEW),
owner)`, which saves nothing. Do not write the switch over `OutputMode` in the program.

The function given to `PdfPreview.of` runs **off the JavaFX thread**, once per paper the person chooses. Read
everything it needs before the call, as above, and keep JavaFX nodes and lazy-loaded entities out of it -
otherwise the preview fails only sometimes, which is the worst way to fail.

In the window Ctrl+P prints and Ctrl+S saves (⌘ on a Mac); PageUp/PageDown turn the pages and Esc closes it.

For a document printed many times a day (a cashier's receipt), print straight to the remembered printer
instead of opening the window each time. This needs `v2.7.1` or newer:

```java
PdfPreview.of(paper -> ReceiptPdf.document(sale, shop, paper))
        .papers(Paper.ROLL_80)
        .remember("receipt")                            // required: the printer is remembered per key
        .printOrShow(getScene().getWindow())
        .thenAccept(outcome -> {                        // runs on the JavaFX thread
            switch (outcome) {
                case PrintOutcome.Sent sent -> { }
                case PrintOutcome.Failed failed -> Ui.error("تعذرت طباعة الإيصال", failed.reason());
                case PrintOutcome.Shown shown -> { }    // no printer remembered yet: the window opened
            }
        });
```

The first time (or when that printer is no longer installed) the window opens; the printer the person prints
on from it is remembered, and later receipts go straight to it, one copy, on the paper the window would open
on. A failure on an installed printer is `Failed` and opens **no** window, so the program must handle it -
show its own message, and offer `show(owner)` if the person should pick another printer. Give the person a
way to change the printer: a button that calls `show(owner)`, where they print once.

Without the preview (a backend, or a program without JavaFX):

```java
byte[] pdf = InvoicePdf.document(invoice, company, Paper.A5).toBytes();      // or .save(Path)

List<String> printers = PdfPrinter.printerNames();
PdfPrinter.print(document, printerName, copies);      // blank name = the system's default printer
PdfPrinter.print(document, printerName,               // from v2.11.0: a page range and both sides
        PrintOptions.copies(1).pages(2, 5).duplex(PrintOptions.Duplex.LONG_EDGE));
```

`PdfPrinter.print` takes as long as the printer does: call it from a background thread and show failures
with the program's own error dialog. `ReportException` is checked; its message is English and meant for the
log, so show the user a sentence in the program's language instead.

### 4. Remove what it replaces

Delete the old print code and whatever only it used (a `sheet()` node builder, a Jasper template for that
page, scaling code). Search for its callers first - developer tools and screenshot generators often call it
too. If the program drew the page with iText, remove the iText dependency once nothing else uses it.

### 5. Test

The document class is plain Java, so test it without JavaFX. Read the PDF back with PDFBox, which the
dependency already brings:

```java
try (PDDocument pdf = Loader.loadPDF(InvoicePdf.document(invoice, company, Paper.A5).toBytes())) {
    assertThat(pdf.getPage(0).getMediaBox().getWidth()).isCloseTo(419.53f, offset(0.1f));   // A5
    assertThat(new PDFTextStripper().getText(pdf)).contains("1751", "16,632", "12,500");
}
```

- Assert on **figures, codes and dates**, not on Arabic words: extracted Arabic comes back as presentation
  forms that differ between fonts, so such an assertion breaks without anything being wrong.
- Cover what used to go wrong: a document long enough to need several pages (assert the page count and that
  the last row and the total are on the last page), one with an empty optional section, and the largest
  figures the program can produce.
- Text extraction says *what* is on the page, not *where*. To see the page, draw it:
  `ImageIO.write(new PDFRenderer(pdf).renderImageWithDPI(0, 110), "png", file)` - and look at the picture
  before telling the user it is right. Send it to them too; it is what they will judge.
- A QR code is squares, not text, so the text stripper does not see it. Read it back from the drawn page with
  ZXing, which `smart-report-pdf` already brings, and compare it with what was given:

  ```java
  BufferedImage page = new PDFRenderer(pdf).renderImage(0, 3f, ImageType.RGB);
  int w = page.getWidth(), h = page.getHeight();
  BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(
          new RGBLuminanceSource(w, h, page.getRGB(0, 0, w, h, null, 0, w))));
  assertThat(new QRCodeReader().decode(bitmap, Map.of(DecodeHintType.TRY_HARDER, true)).getText()).isEqualTo(qr);
  ```

### 6. Packaging

A program packaged with `jpackage` on the classpath names its runtime modules by hand. Printing and the
remembered printer need `java.desktop`, `java.prefs` and `java.logging`; add any that the `--add-modules`
list lacks. A missing one shows only in the packaged program, as a `NoClassDefFoundError` when the print
button is pressed.

### 7. Verify

Run the project's tests, then say plainly what was and was not tried. Three things cannot be checked from
here and are the user's: printing on the real printer, on the real paper (A5 especially - printers disagree
about trays), and the packaged program. If the project keeps a status file, record there that the new page
has not been on a real printer yet; the old one usually had.

## Variants

- **A plain list, no layout to design** - a table of objects straight to a file:

  ```java
  ReportRequest<Employee> request = ReportRequest.<Employee>builder()
          .title("تقرير الموظفين").data(employees)
          .addColumn("name", "الاسم").addColumn("salary", "الراتب")
          .parameter(PdfReportProvider.PAPER, "A5-landscape")
          .parameter(PdfReportProvider.LANDSCAPE_FROM_COLUMNS, 8)   // from v2.11.0: sideways from 8 columns
          .build();
  byte[] pdf = ReportService.generate(ReportType.PDF, request);
  ```

  Columns are read from the objects by name. A column can carry a format, an alignment and a width weight
  (from `v2.2.0`):
  `.addColumn(ColumnDefinition.of("salary", "الراتب").withFormat("#,##0.00").withAlign(Alignment.END).withWeight(2))`
  - a `DecimalFormat` pattern for numbers, a `DateTimeFormatter` pattern for dates (`d MMMM yyyy` gives
  Arabic month names in an Arabic report). For a total under the table (from `v2.8.0`), add
  `.withTotal()` to each column to sum - `ColumnDefinition.of("amount", "المبلغ").withFormat("#,##0.00").withTotal()`:
  each sum sits under its column in the column's format, exact in the PDF (`BigDecimal`; a `double` adds as the
  decimal it shows; `null` adds nothing), with the label `الإجمالى` (`Total` in a left-to-right report) under the first
  column that is not summed; `.totalsLabel("...")` on the request changes it. A value that is not a number in
  a summed column fails with a `ReportException` naming the column and row. Do not add the total as a fake data
  row. For a subtotal under each group (from `v2.10.0`) - a statement by customer or by month - add
  `.groupBy("customer")` to the request and pass the data already sorted by that property: a new group starts
  wherever the value changes, under it comes a row `إجمالى: <value>` (`Total: <value>`; `.subtotalLabel("...")`
  changes the words) with each summed column in its format, and the grand total stays at the end. In the PDF the
  subtotal never starts a page alone: the last row of its group moves with it. It needs a `withTotal()` column.
  From `v2.11.0`, `.groupHeader(true)` adds a line with the group's value above its rows, and
  `.pageBreakPerGroup(true)` starts each group on a new page (one customer's statement per page; in Excel a
  printed page break).
  Do not insert subtotal rows into the data yourself. Use `PdfDocument` instead as soon as the page needs fields, a summary or more than one table.
- **The same list as Excel** - add `smart-report-excel` and ask for `ReportType.EXCEL`. It applies each
  column's format (as an Excel format, so numbers stay numbers) and alignment, and ignores the weight. A
  `withTotal()` column gets a bold totals row, one empty row below the data, holding a real `SUM` formula with
  its computed value, outside the filter. With `groupBy` (from `v2.10.0`) every group gets a subtotal row with
  `=SUBTOTAL(9, ...)`, and the grand total becomes `=SUBTOTAL(9, ...)` too, so it does not count the subtotals
  twice and follows the header filter. That total is Excel's own `double` arithmetic over the stored cells,
  so it can differ from the exact PDF total in the last digits of a `float` or a number past ~15 digits; for
  two-decimal money they match. A date
  pattern Excel would show differently - `hh` without `a`, `HH` with `a`, `k`, or a time zone `z` - fails
  with a `ReportException` naming the column: use `HH:mm`, or `hh:mm a`. The Excel module brings Apache POI
  6 from `v2.5.0`; a program that uses POI itself should know that.
- **A page designed in Jaspersoft Studio** - add `smart-report-jasper` and ask for `ReportType.JASPER` with
  the template's path. Only for a program that already has templates; a new page is quicker as a
  `PdfDocument`. From `v2.7.1` Arabic in a template that names no font is drawn with Noto Naskh Arabic; give
  each Arabic text box about 1.7 times its font size in height so the line sits inside it, and merge
  `jasperreports_extension.properties` in a fat jar (see the README). From `v2.8.0` a shorter Arabic box, down to
  about 1.15 times the font size, still shows its first line, running past the box, and a text that prints nothing is
  logged as a warning naming the report and the field: when the program's log has these warnings, make the
  boxes they name taller.
- **A program with `module-info.java`** - the jars have no module descriptor, only stable automatic names:
  `requires com.smart.report.pdf;`, `requires com.smart.report.fx;` and `requires com.smart.report.api;`
  (for `ReportException`). This has not been tried in a modular program; if the build or the launch
  complains, say so to the user rather than working round it silently.
- **Another font** - for the whole program, the program's own TTF as the classpath resource
  `fonts/report-font.ttf` (and `fonts/report-font-bold.ttf`), or `-Dsmart.report.pdf.font=/path/to/font.ttf`.
  For one document (from `v2.9.0`), `document.font(PdfFont.of(regularPath, boldPath))`, or
  `PdfFont.of(bytes)` for a font inside the jar; other documents keep the program's font. Make the `PdfFont`
  once and reuse it: it reads and checks the files, and throws `ReportException` for a `.ttc`, a CFF `.otf` or
  a font that forbids embedding. An Arabic document needs a font with the Arabic presentation forms (Noto Naskh
  Arabic, Amiri, Arial and Tahoma have them); with another font its Arabic comes from a system font and the log
  warns once - check the drawn PDF, not only that it was written. The generic report takes it as
  `.parameter(PdfReportProvider.FONT, font)`.
- **Invoices, statements and other money documents** - add `.failOnMissingGlyphs(true)` (from `v2.9.0`):
  a character no font has then stops the document with a `ReportException` naming the
  character, its code point and the text it is in, instead of printing `?`. Show that message where the
  program shows its errors. It is off unless set; the generic report takes
  `.parameter(PdfReportProvider.FAIL_ON_MISSING_GLYPHS, true)`.
- **Receipts on a thermal printer** - the same `PdfDocument` on a roll (from `v2.4.0`):

  ```java
  PdfDocument receipt = PdfDocument.rtl(Paper.ROLL_80)            // Paper.ROLL_58 for a 58 mm printer
          .letterhead(shop.name(), shop.phone())
          .field("رقم الإيصال", sale.number())
          .field("التاريخ", Ui.dateTime(sale.at()))
          .table(items)                                           // three columns at most: الصنف، الكمية، المبلغ
          .summaryTotal("الإجمالى", Ui.amount(sale.total()))
          .text(AmountInWords.arabic(sale.total(), AmountInWords.Currency.EGP))
          .footer("شكرًا لزيارتكم");
  ```

  - A receipt is **one page as long as its content**, cut where the text ends - not an A4 sheet and not
    several pages (only a receipt longer than about five metres continues on a second page).
  - The roll gets a smaller font and narrow margins by itself; do not give it an A4 style.
  - `PdfPrinter.print(receipt, printerName, 1)` asks the printer for no paper size, so it uses its roll. The
    printer must have its system driver installed: if it is not in `PdfPrinter.printerNames()`, the program
    cannot print a PDF to it, and the user has to install the driver - say so rather than writing ESC/POS
    code.
  - In the preview give the roll alone, `.papers(Paper.ROLL_80)`, so there is no paper choice. From
    `v2.5.0` the window opens a receipt at a readable size by itself. On `v2.4.0` it opens with
    the whole page fitted ("الصفحة كاملة"), which shows a long receipt as a narrow strip, and "ملء العرض"
    makes it readable - tell the user, or move to the newer tag.
  - At the till, print each receipt with `printOrShow(owner)` and `.remember("receipt")` (see step 3), so
    the cashier chooses the printer once instead of opening the preview for every sale.
  - `Paper.parse("80mm")` / `"58mm"` reads a saved setting; `Paper.roll("76mm", 76)` is another width.
  - A receipt can end with `.qrCode(...)` (the ZATCA code in Saudi Arabia, or a link): the default 30 mm fits
    the 58 mm roll, a larger size shrinks to the roll's width, and the paper is cut just under the code.
    `.barcode(...)` works the same way for a shipment or waybill number.
  - Test that it is one page, about 226.8 points wide (80 mm), with the last line and the total on it. Its
    height follows the number of lines, so assert a bound, not an exact height.
- **Many documents at once** - "print every driver's statement for the month", the day's invoices: from
  `v2.9.0`, a `PdfBatch` of the documents the program already builds, as one file and one print
  job:

  ```java
  List<PdfDocument> statements = drivers.stream()
          .map(driver -> StatementPdf.document(driver, company, Paper.A4))
          .toList();
  PdfBatch batch = PdfBatch.of(statements);
  PdfPrinter.print(batch, printerName, copies);      // one job; copies come out as whole sets of the batch
  batch.save(file);                                  // or one PDF file

  PdfPreview.ofBatch(paper -> PdfBatch.of(drivers.stream()          // the preview, with a paper choice
                  .map(driver -> StatementPdf.document(driver, company, paper)).toList()))
          .papers(Paper.A4, Paper.A5)
          .show(getScene().getWindow());
  ```

  - Each document keeps its own letterhead, page footer and page numbers ("صفحة 1 من 3" starts again), exactly
    as if printed alone. Do not merge them into one `PdfDocument` with `pageBreak()`.
  - Every document must be on the same paper: A4 with A4 landscape is fine, but A4 with A5, a sheet with a roll,
    or two roll widths make `toBytes`, `save` and `print` throw a `ReportException`. Build all of a batch's
    documents from the same `paper` variable.
  - On a roll each document is its own receipt (a page as long as its content); the printer cuts between them
    when its driver cuts at the end of each page.
  - Read the data first and build the documents off the JavaFX thread, as for the preview: a month of statements
    takes a moment.
  - Test that the batch has as many pages as its documents together, and that each document's first page
    carries its letterhead.

## When it does not work

- **The dependency does not resolve.** JitPack builds a tag the first time it is asked, which takes a minute
  or two: open `https://jitpack.io/com/github/MohamedSalah-13/smart-report-plugin/<tag>/build.log`. Check
  that the group id carries the repository name (`...MohamedSalah-13.smart-report-plugin`) and that the
  version has its `v`.
- **A `?` on the page.** A character no font has. The log names it (`No PDF font has a glyph for ...`).
  Use a font that covers it, as under "Variants", and `failOnMissingGlyphs(true)` where a `?` must never be
  printed. Up to `v2.8.0`, `²`, `³`, `½` and `±` (as in "م³") come out as `?` on a computer without Arial or
  DejaVu; from `v2.9.0` they are drawn from Liberation Sans, which ships inside PDFBox.
- **Text in the wrong order.** Almost always text that was shaped or reversed before it reached the library,
  or cells written in visual order. Give it logical text and logical columns.
- **A table too wide.** Too many columns for an upright page: use `paper.landscape()`, or fewer columns.
  Draw the page and look - whether it reads well depends on what the columns hold.
- **Nothing happens on print.** The printer name is not one of `PdfPrinter.printerNames()`, or the call threw
  on a background thread that nobody listens to. Catch `ReportException` there and show it.
- **The preview opens empty or says the document could not be prepared.** The function given to
  `PdfPreview.of` threw. It runs off the JavaFX thread; the log has the stack trace. For a batch, the usual
  cause is documents on different papers.
- **Diacritics sit a little off.** They are centred over their letter without the font's fine positioning;
  this is a known limit, not a bug in the program.

## What not to do

- Do not print a JavaFX node. It cannot span pages, it depends on the screen's fonts, and it cannot be saved.
- Do not add iText, and do not copy PDF code from another project of the user - that code is what this
  library replaced.
- Do not build the document inside a controller or a dialog; keep it a pure function of the data.
- Do not send anything to a printer to "test" it. Draw the page to a picture instead, and leave the real
  printer to the user.
- Do not tell the user the page is correct from the extracted text alone. Look at the rendered picture.
- Do not make a QR or barcode image with another library (or ZXing's `javase`) to put on the page, and do not hand-build
  the ZATCA TLV: `qrCode` and `ZatcaQr` already do it, and a length counted in characters instead of bytes
  breaks every Arabic seller name.
