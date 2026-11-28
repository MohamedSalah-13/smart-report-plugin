# Smart Report Plugin

![CI](https://github.com/MohamedSalah-13/smart-report-plugin/actions/workflows/ci.yml/badge.svg) ![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white) ![JavaFX](https://img.shields.io/badge/JavaFX-21-2E7DAF?logo=java&logoColor=white) ![Apache PDFBox](https://img.shields.io/badge/Apache%20PDFBox-3.0.8-D22128?logo=apache&logoColor=white) ![Apache POI](https://img.shields.io/badge/Apache%20POI-6.0.0-D22128?logo=apache&logoColor=white) ![JasperReports](https://img.shields.io/badge/JasperReports-6.21.5-C2185B) ![Release](https://img.shields.io/github/v/tag/MohamedSalah-13/smart-report-plugin) ![License](https://img.shields.io/github/license/MohamedSalah-13/smart-report-plugin)

**English** | [العربية](README.ar.md)

A Java library for reports and printing, added to any project as a dependency:

- **Print-ready Arabic PDF documents** on A4 and A5 (portrait and landscape): letterhead, title, fields, several tables with a totals row, a summary, page numbers, a QR code, a barcode — and direct output to a printer. Apache PDFBox (and ZXing core to encode the QR code and the barcode).
- **A ready JavaFX preview window**: the pages as they will print, a choice of printer, paper and copies, print and save as PDF — or print straight to the printer last chosen, with no window.
- **A generic PDF or Excel report from a `List<POJO>`**, with no template.
- **JasperReports** (visually designed `.jrxml`/`.jasper` templates): export to PDF, XLSX, HTML or CSV.

<p align="center"><img src="docs/statement-a5.png" alt="An account statement on A5 paper" width="420"></p>

## What's New

### 2.10.0

- **New:** a watermark on every page (`watermark`), such as «صورة» on a copy of an invoice or «ملغاة», light enough to read the page under it. See [Watermark](#watermark-watermark).
- **New:** subtotals by group in the generic PDF and Excel report (`groupBy`), for a statement by customer or by month. See [Subtotals by group](#subtotals-by-group).

### 2.9.1

- **Fixed:** a generic Excel report whose title starts or ends with an apostrophe, such as `'Q1' sales`, no longer fails. See [Generic PDF or Excel](#generic-pdf-or-excel-no-template).
- **Changed:** the PDF page layout was rewritten internally so that every part follows the same page rules; documents come out exactly as before.

### 2.9.0

- **New:** `pageBreak()` starts the next part on a new page, and never leaves a blank one. See [Page breaks](#page-breaks-pagebreak).
- **New:** several documents in one PDF and one print job, each with its own page numbers (`PdfBatch`), with a preview for the batch. See [Several documents in one file](#several-documents-in-one-file-and-one-print-job-pdfbatch).
- **New:** a font for one document (`PdfDocument.font`), and `failOnMissingGlyphs(true)` to stop with an error instead of printing `?`. See [Font](#font).
- **Fixed:** `²`, `³`, `½` and `±` no longer print as `?` on a computer without Arial or DejaVu, and invisible characters such as the one after `©` print nothing. See [Font](#font).
- **Changed:** copies of a document with several pages print as whole sets.

Details of each release are in [CHANGELOG.md](CHANGELOG.md).

## Modules

Each kind of report is its own Maven module, so a program that only prints PDFs does not carry POI or Jasper:

| Module | What is in it | What it brings |
|---|---|---|
| `smart-report-pdf` | `PdfDocument`, `PdfBatch`, `PdfPrinter`, `PdfPages`, and the generic PDF report | PDFBox, ZXing core (QR code, barcode) |
| `smart-report-fx` | `PdfPreview`: the preview window | `smart-report-pdf`; and your program's own JavaFX (`provided`) |
| `smart-report-excel` | The generic Excel report | Apache POI |
| `smart-report-jasper` | Jasper template reports | JasperReports |
| `smart-report-api` | `ReportService`, `ReportRequest`, `AmountInWords` and `ZatcaQr` — comes along with any module above | slf4j-api only |

## Installation

The library is served by [JitPack](https://jitpack.io) straight from the repository's tags:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.MohamedSalah-13.smart-report-plugin</groupId>
    <artifactId>smart-report-pdf</artifactId>
    <version>v2.10.0</version>
</dependency>
```

Change `artifactId` to `smart-report-fx`, `smart-report-excel` or `smart-report-jasper` for what you need.
Built with Java 21. The `smart-report-fx` module exists from `v2.1.0`.

> The library uses `slf4j-api` only (no implementation), so to see its logs add a backend of your choice
> (logback, slf4j-simple, ...) to your project.

## PDF document (`PdfDocument`)

The document is built part by part, in the order the parts will appear:

```java
import com.smart.report.pdf.*;

PdfDocument document = PdfDocument.rtl(Paper.A5)            // or Paper.A4, or Paper.A4.landscape()
        .letterhead("شركة الصفا لنقل مواد البناء", "القاهرة - ت: 0100 000 0000")
        .title("كشف حساب عربية")
        .subtitle("الفترة: من 2026-11-01 إلى 2026-11-30")
        .field("رقم العربية", "ق ط ع 1234").field("السائق", "أحمد محمد علي")
        .table(t -> t
                .column("التاريخ", 22).column("العدد", 14, Align.CENTER).column("القيمة", 26, Align.CENTER)
                .rows(movements, m -> new String[]{date(m), trips(m), amount(m)})
                .totals("الإجمالى", "12 نقلة", "-7,200.00"))
        .heading("الواصل والتسويات")
        .table(payments)
        .summary("المستحق عن الفترة", "7,200.00")
        .summary("الواصل", "4,000.00")
        .summaryTotal("الباقى", "3,200.00")
        .footer("شكرًا لتعاملكم")
        .signatures("المستلم", "المحاسب", "الختم");          // signature boxes, kept with the summary above

document.save(Path.of("statement.pdf"));     // a file
byte[] pdf = document.toBytes();             // or bytes
```

- `PdfDocument.rtl(...)` for Arabic and `PdfDocument.ltr(...)` for English. The direction decides where the first column sits and how text is aligned.
- Columns and cells are written **in logical order**; the library is what puts the first column on the right in an Arabic page.
- A column's number (22, 14, 26) is its weight in the table's width, and no column goes below the width of its longest word.
- Text longer than its column **wraps** over several lines, the table header repeats on every page, and pages are numbered ("Page 2 of 3") once the document runs over one page.
- A row taller than a whole page continues on the next page, and so does a field whose value is taller than a page.
  A table header taller than a third of the page is drawn once instead of on every page.
- Negative numbers, dates, percentages and phone numbers read as they were written inside an Arabic sentence.

### Amount in words (`AmountInWords`)

The total written out in Arabic, as on an invoice or a receipt:

```java
import com.smart.report.util.AmountInWords;

document.text(AmountInWords.arabic(new BigDecimal("7200"), AmountInWords.Currency.EGP));
// فقط سبعة آلاف ومائتا جنيه لا غير

AmountInWords.arabic(new BigDecimal("1234.50"), AmountInWords.Currency.EGP);
// فقط ألف ومائتان وأربعة وثلاثون جنيهًا وخمسون قرشًا لا غير
AmountInWords.arabicWords(new BigDecimal("0.13"), AmountInWords.Currency.SAR);   // without «فقط ... لا غير»
// ثلاث عشرة هللةً

// In English, for a bilingual invoice:
AmountInWords.english(new BigDecimal("7200"), AmountInWords.EnglishCurrency.EGP);
// Seven thousand two hundred Egyptian pounds only
AmountInWords.english(new BigDecimal("7200.50"), AmountInWords.EnglishCurrency.EGP);
// Seven thousand two hundred Egyptian pounds and fifty piastres only
```

- The counted noun follows the number: `جنيه واحد`, `جنيهان`, `ثلاثة جنيهات`, `أحد عشر جنيهًا`, `مائة جنيه`, `مائتا جنيه`, `ألفا جنيه`.
- Numbers agree with the noun's gender: `ثلاثة جنيهات` but `ثلاث هللات`, and the Omani baisa is feminine too:
  `ثلاث بيسات`, `إحدى عشرة بيسةً`.
- Ready currencies: `EGP`, `SAR`, `AED`, `KWD`, `USD`, `QAR`, `BHD`, `OMR` and `JOD`, and from v2.11.0 `SDG`,
  `GBP`, `YER`, `MAD`, `IQD`, `LYD`, `TND`, `DZD`, `SYP`, `LBP`, `TRY` and `EUR`. The fraction digits follow
  ISO 4217: 3 for the Kuwaiti, Bahraini, Jordanian, Iraqi, Libyan and Tunisian dinar and the Omani rial, 2 for the
  others. The lira is feminine: `ثلاث ليرات`, `إحدى عشرة ليرةً`. Any other is a `new Currency(...)` with its noun forms.
- By code (v2.11.0): `Currency.forCode("EGP")` and `EnglishCurrency.forCode("EGP")` return an `Optional`, empty for
  a code they do not know, so a program prints no line rather than a wrong one.
- English: `english(...)` (with «only») and `englishWords(...)` take an `EnglishCurrency` with the same codes. The
  numbers are written as on invoices and cheques (`two hundred fifty`, `thirty-four`), with «and» only before the
  fraction. Any other currency is `new EnglishCurrency("Libyan dinar", "Libyan dinars", "dirham", "dirhams", 3)`.
- The amount is rounded to the currency's digits (half up). Amounts of a trillion or more are rejected, and so is a
  negative amount, unless a `Negative` says how to write it (v2.11.0): the words of its absolute value, with your word
  before or after the whole text.

```java
AmountInWords.arabic(new BigDecimal("-150"), AmountInWords.Currency.EGP, AmountInWords.Negative.suffix("(دائن)"));
// فقط مائة وخمسون جنيهًا لا غير (دائن)
AmountInWords.english(new BigDecimal("-150"), AmountInWords.EnglishCurrency.EGP, AmountInWords.Negative.prefix("minus"));
// Minus one hundred fifty Egyptian pounds only
```

- It lives in `smart-report-api`, so a Jasper or Excel report can use it too (as a parameter, for example).

### On every page

```java
document.letterheadOnEveryPage(true)                              // the letterhead (and logo) on top of every page
        .pageFooter("صادر من نظام نقلات - ت: 0100 000 0000");      // a small line at the bottom of every page
```

- `letterheadOnEveryPage(true)`: for long statements; a table that continues on a new page has its header under the letterhead.
- `pageFooter(text)`: above the page number on every page, including a single page. `footer(text)` is different:
  a closing sentence drawn once after the last part.
- `signatures(...)`: the boxes stay together, and stay with the summary before them (and any text between, such as
  the amount in words): when they do not fit under it, the summary moves to the next page with them. If that
  group cannot move, at least the last line of the text before the boxes goes with them.

### Page breaks (`pageBreak`)

```java
document.title("كشف حساب عربية")
        .table(movements)
        .pageBreak()                       // the next part starts on a new page
        .heading("ملحق: إيصالات الواصل")
        .table(receipts);
```

- A break never leaves a blank page. On a page with nothing on it yet below the letterhead (at the very top of the
  document, or right after another break) it does nothing. The page a break opens is kept only if something is
  drawn on it, so a break followed only by parts that draw nothing (spaces, empty text such as notes that were left
  blank, other breaks, or the end of the document in a loop that adds a section and a break) leaves no page, not
  even one for the footer alone.
- The new page is like any page the content opens: the letterhead is on top of it with
  `letterheadOnEveryPage(true)`, and the page numbers carry on ("Page 2 of 3").
- After a table that ran over several pages, the break comes after its last row and totals, and the table header
  is not repeated on the new page: the table has ended. To start each group of rows on its own page, make a table
  per group with a break between them.
- On a thermal roll a receipt is one page as long as its content, so `pageBreak()` **does nothing** there and the
  receipt is exactly as it is without it: the same code builds the document for A4 or a roll. To cut several
  receipts, print them as a `PdfBatch`.
- Sections that each need their own letterhead and their own page numbers (a statement per driver in one file)
  are separate documents in a `PdfBatch`, not breaks.

### Watermark (`watermark`)

From `v2.10.0`:

```java
invoice.watermark("صورة");                 // a copy of an invoice, in light grey
invoice.watermark("ملغاة", 0xC00000);      // cancelled, in light red
invoice.watermark("DRAFT");
```

- A large, light word on **every page**, diagonal across the middle of the page. It lies **over** everything on
  the page, including the table header, totals and summary fills, so none of them can hide it. It is translucent,
  so the text and fills under it stay readable.
- It is one line in the document's font; Arabic is shaped and right to left. It shrinks to fit inside the margins
  for long words and small paper.
- It is the colour given at 15% opacity: on white paper black comes out light grey and red light pink, so it
  stays light even when the colour given is dark, and it takes little ink, also with `PdfStyle.inkSaver()`.
  Without a colour it is light grey.
- On a thermal roll it is horizontal and repeated down the receipt, about one copy for every one and a half
  widths of the roll; a short receipt has one copy in its middle.
- It is not content: it moves no part of the document and does not lengthen a receipt, and a page with only the
  watermark is still empty, so a `pageBreak()` leaves no page for it. It is marked as an artifact, so PDF readers
  that respect this leave it out when text is copied or searched. In a `PdfBatch` each document keeps its own
  watermark, and the preview and the printer show it.
- `null` or blank text means no watermark.

### Thermal receipts (80 mm and 58 mm)

```java
PdfDocument receipt = PdfDocument.rtl(Paper.ROLL_80)          // or Paper.ROLL_58
        .letterhead("سوبر ماركت الهدى", "ت: 0100 000 0000")
        .table(items)
        .summaryTotal("الإجمالى", "85.00")
        .footer("شكرًا لزيارتكم");
```

- A receipt is **one page as long as its content**, not an A4 sheet: the roll is cut where the text ends.
- A smaller font and narrow margins are chosen for the roll. `PdfPrinter` asks the printer for no paper size, so it
  uses its own roll.
- `Paper.parse("80mm")` and the preview window know the rolls too. The preview opens a receipt at a readable size
  (as wide as the window allows, up to twice its size) instead of fitting the whole length into the window.
- Several copies on a roll go out as one job each (v2.11.0), because a receipt printer cuts at the end of a job:
  two copies in one job would come out as one uncut strip.

The cash drawer and what a printer supports (v2.11.0):

```java
PdfPrinter.openCashDrawer(receiptPrinter);                                  // pin 2, a 100 ms pulse
PdfPrinter.openCashDrawer(receiptPrinter, PdfPrinter.DrawerPin.PIN_5, 60);
boolean ok = PdfPrinter.declaresPaper(officePrinter, Paper.A5);           // warn before the first print
```

- The drawer is opened with the ESC/POS `ESC p` command sent as raw bytes. If the printer's driver takes no raw
  bytes (an office printer, a PDF printer), a `ReportException` says so instead of nothing happening.
- `declaresPaper` is `true` for a roll (no size is asked for) and when the printer lists no sizes at all.

### QR code (and the Saudi tax invoice QR)

```java
document.qrCode("https://example.com/invoices/2026-0042");   // 30 mm, centred
document.qrCode(invoiceNumber, 25, Align.START);              // 25 mm, at the start of the line (right in Arabic)
```

The QR code a simplified tax invoice carries in Saudi Arabia (ZATCA e-invoicing, phase 1; phase 2 below): the
seller's name, VAT number, the time, the total with VAT and the VAT, encoded as ZATCA specifies:

```java
import com.smart.report.util.ZatcaQr;

String qr = ZatcaQr.payload("مؤسسة الهدى للتجارة", "310122393500003",
        LocalDateTime.now().atZone(ZoneId.of("Asia/Riyadh")).toInstant(),
        new BigDecimal("1150.00"), new BigDecimal("150.00"));

PdfDocument receipt = PdfDocument.rtl(Paper.ROLL_80)
        .letterhead("مؤسسة الهدى للتجارة", "الرقم الضريبي 310122393500003")
        .title("فاتورة ضريبية مبسطة")
        .table(items)
        .summary("ضريبة القيمة المضافة 15%", "150.00")
        .summaryTotal("الإجمالى شامل الضريبة", "1,150.00")
        .qrCode(qr);
```

- Drawn as vector squares, not an image, so it stays sharp on any printer. A quiet zone of 4 modules is left
  around it, and it is never split across two pages.
- The size is the side of the code in millimetres. On a narrow roll a code too wide for the line shrinks to fit,
  and the receipt is still cut right under it.
- `Align.START` puts it at the start of the line (right in an Arabic document), `Align.END` at the end, and
  `Align.CENTER` (the default) in the middle. Arabic text is encoded as UTF-8.
- `ZatcaQr.payload` returns the Base64 of the TLV fields (tags 1 to 5, each value's length in UTF-8 bytes, so an
  Arabic name counts its bytes, not its letters). The time is written in UTC and the amounts with two decimals; a
  VAT number that is not 15 digits beginning and ending with 3 is rejected. It lives in `smart-report-api` and
  needs no library.
- The code is encoded by [ZXing](https://github.com/zxing/zxing) core (Apache License 2.0, no dependencies of its
  own), which `smart-report-pdf` brings.

**Phase 2 (integration).** A simplified tax invoice (and its credit and debit notes) adds four values that come from
signing it: the invoice hash, the ECDSA signature, the public key, and ZATCA's signature of the stamp certificate.
`ZatcaQr` does not sign anything: the values come from the program's signing code or ZATCA's SDK.

```java
String qr = ZatcaQr.phase2("مؤسسة الهدى للتجارة", "310122393500003", issued,
                new BigDecimal("1150.00"), new BigDecimal("150.00"))
        .invoiceHash(digestValue)        // ds:DigestValue of the signed invoice (Base64)
        .signature(signatureValue)       // ds:SignatureValue (Base64)
        .certificate(certificate)        // the device's stamp certificate (CSID) as an X509Certificate
        .payload();

receipt.qrCode(qr, 45, Align.CENTER);    // a phase 2 code is denser: give it 45 mm (see below)
```

- Tags 6 to 9 are written as in the worked example of ZATCA's
  [E-invoicing Detailed Technical Guidelines](https://zatca.gov.sa/en/E-Invoicing/Introduction/Guidelines/Documents/E-invoicing-Detailed-Technical-Guideline.pdf)
  (section 6.2): the hash and the signature as their Base64 text, the public key and the certificate's signature as
  DER bytes. Without an `X509Certificate`, `publicKey(byte[])` and `certificateSignature(byte[])` take those bytes.
- The values must match the signed XML: the same seller name, VAT number, amounts and issue time.
- A value in the wrong form (a hash in hex, a signature that is not Base64 DER) or a missing one fails at once, and
  a code longer than ZATCA's 700 characters is rejected (a very long seller name is the usual cause).
- A standard tax invoice (B2B) is stamped by ZATCA at clearance and comes back with its QR code: print that one as
  it is.
- **Size.** A phase 2 code is about 90 to 100 modules wide (QR version 18 to 21), against about 40 for phase 1. At
  the default 30 mm each module is only about 0.3 mm, 2 to 3 dots of a 203 dpi thermal printer, which is marginal
  for a scanner. At 45 mm a module is 0.45 to 0.5 mm (about 4 dots), and the code with its quiet zone (about 49 mm)
  still fits the 58 mm roll (52 mm of line) as well as the 80 mm one.

### An archivable invoice: PDF/A-3 with the e-invoice XML (`pdfA3`, `attach`)

The QR on the page covers what the customer scans; the file kept for years should carry the signed XML of the
e-invoice too (v2.11.0):

```java
PdfDocument invoice = PdfDocument.rtl(Paper.A4)
        /* ... the invoice ... */
        .attach("invoice.xml", signedXml, "application/xml")   // relationship Data by default
        .pdfA3(true);                                            // PDF/A-3B
```

- `pdfA3(true)` writes PDF/A-3B, the archive format: an sRGB output intent and XMP metadata matching the document's
  title and dates. The fonts are embedded already. Output from the library passes veraPDF, the reference validator,
  with Arabic text, tables, QR codes, barcodes, transparent stamps and the watermark.
- `attach(name, bytes, mimeType)` embeds a file. The reader shows it in its attachments list, and with PDF/A-3 it is
  associated with the document. `attach(name, bytes, mimeType, Relationship.ALTERNATIVE)` sets another relationship
  (`SOURCE`, `DATA`, `ALTERNATIVE`, `SUPPLEMENT`, `UNSPECIFIED`).

### Barcode (Code 128)

For a shipment, waybill, invoice or item number, read by a hand scanner:

```java
document.barcode("SHP-2026-0042");                    // bars 12 mm tall, centred, the code written under them
document.barcode(waybillNumber, 15, Align.START);     // 15 mm, at the start of the line (right in Arabic)
```

- Drawn as vector bars, not an image, with the code written under them left to right as it is, even on an Arabic
  page. A quiet zone of 10 bars is left on each side, and the bars and the code are never split across two pages.
- The narrowest bar is 0.33 mm (about 3 dots on a 203 dpi thermal printer). A code too wide for the line gets
  narrower bars, down to 0.25 mm; a code that would need thinner bars than that fails with a `ReportException`
  that names it, instead of printing bars no scanner reads. On a 58 mm roll that is about 15 characters; runs of
  digits are packed two to a bar pattern, so mostly-numeric codes are shorter.
- Code 128 holds English letters, digits and signs only: Arabic text, accented letters or tabs are rejected with an
  `IllegalArgumentException` when the barcode is added.

**Whole printer dots** (v2.11.0). 0.33 mm is 2.64 dots on a 203 dpi head, so the driver gives some bars 2 dots
and others 3, and uneven bars are a known cause of labels that do not scan. Tell the document the printer's
resolution and every bar and QR module becomes a whole number of dots, placed on the printer's dot grid:

```java
PdfDocument receipt = PdfDocument.rtl(Paper.ROLL_80).printerDpi(203);   // or 300, 600...
```

- The narrowest bar becomes the whole number of dots nearest to 0.33 mm (3 at 203 dpi, 4 at 300), and a QR module
  the nearest to the size asked for. If the symbol does not fit, it loses one dot at a time, down to 2 dots and no
  less than 0.25 mm for bars; below that it fails with a `ReportException` instead of shrinking to fractions.
- Without it, or with `printerDpi(0)`, nothing changes.

### EAN-13 and item labels (`ean13`, `PdfLabels`)

The barcode on products (v2.11.0):

```java
document.ean13("622100012345");                 // 12 digits: the check digit is computed
document.ean13("4006381333931", 15, Align.START); // 13 digits: the check digit is verified
```

- 0.33 mm bars (the standard size) with the longer guard bars and the digits under them, the first digit in the
  left quiet zone. It narrows down to 0.264 mm (80%, the smallest the standard allows) to fit, then fails with a
  `ReportException`. With `printerDpi` every bar is a whole number of printer dots.
- A 13-digit number with a wrong check digit is rejected: a mistyped number would print another product's code.

Item labels for a shop, on a label roll or an A4 label sheet:

```java
PdfLabels labels = PdfLabels.roll(40, 25)          // a 40 × 25 mm label
        .across(2, 2)                                // two side by side, 2 mm apart
        .rightToLeft(true)
        .printerDpi(203)
        .offset(0.5f, 0);                            // this printer's feed, in mm
for (Unit unit : units) {
    labels.add(PdfLabels.Item.of(unit.name(), unit.priceText(), unit.barcode())
            .withOldPrice(unit.priceBeforeOffer()), unit.labelCount());
}
PdfPrinter.print(labels.toBytes(), printer, labels.paper(), 1);

PdfLabels sheet = PdfLabels.sheet(Paper.A4, 3, 8).margins(8.5f, 0);   // 24 labels a sheet
```

- Each label has the name, the price (with the old price struck through beside it, if there is room) and the
  barcode. A valid 13-digit code is drawn as EAN-13 with its digits; any other code as Code 128, so the scanner
  reads back exactly the code stored in the program.
- A name too long for the label is cut with "…" (`LongName.ELLIPSIS`, the default), shrunk to the smallest font
  first (`SHRINK`), or left out (`HIDE`). `showName`, `showPrice` and `showDigits` turn parts off.
- On a roll each row of labels is a page of its own size; on a sheet the label size is what the margins and
  `gaps(...)` leave. A label too small for its barcode fails with a `ReportException` that names the item, and an
  item with no barcode is refused (a unit without a barcode gets no label).
- `offset(xMm, yMm)` moves everything for a printer whose roll feeds slightly off; keep it with the printer.

### QR code or barcode as an image (`SymbolImage`)

To show a code on a screen, or serve one from a web endpoint, without a PDF (v2.11.0):

```java
byte[] png = SymbolImage.qrPng(qrText, 8);                     // 8 pixels per module
byte[] png = SymbolImage.barcodePng("INV-2026-0042", 3, 80);   // 3 pixels for the narrowest bar, 80 pixels tall
BufferedImage image = SymbolImage.qr(qrText, 6);               // for SwingFXUtils.toFXImage, for example
```

- The same encoding as in the PDF (QR level M, Arabic as UTF-8; Code 128 for ASCII only), with the quiet zone
  inside the image: 4 modules around a QR, 10 on each side of a barcode. The code is not written under the bars.
- Black and white only, every module a whole number of pixels, no smoothing. To make it bigger, ask for more pixels
  per module rather than scaling the image: a fractional scale makes the modules uneven.

### A code to find a document by scanning it (`DocumentCode`)

So that a printed invoice can be found again by scanning it, for a return or in a list (v2.11.0, in
`smart-report-api`):

```java
DocumentCode codes = DocumentCode.DEFAULT;          // 13 digits: 2, the type, 10 for the number, a check digit
document.barcode(codes.encode(1, invoice.number()));   // type 1 = sale: "2100000000425" for invoice 42

codes.parse(scannedText)                            // Optional[Scanned[type=1, number=42]]
        .ifPresent(found -> openDocument(found.type(), found.number()));
```

- Digits only: a scanner types what it reads as key presses, and with an Arabic keyboard layout a Latin letter
  arrives as an Arabic one. `parse` also accepts Arabic-Indic digits (`٢١٠٠…`) and surrounding spaces.
- `parse` answers empty for a wrong length, a wrong first digit or a wrong check digit (GS1 modulo 10, the EAN-13
  one), so a search box can take either a typed invoice number or a scanned code.
- Nothing is stored: the code is computed from the type and the number, so a reprint carries the same code.
- `DocumentCode.of(firstDigit, numberDigits)` for another shape. The default starts with 2, so it is also a valid
  EAN-13 in the 20–29 range GS1 leaves for use inside a shop, and never matches a real product's barcode.

### Cell style, and an image in the body (`CellStyle`, `image`)

A negative balance in red, a bold cell, and a stamp or a signature (v2.11.0):

```java
document.table(t -> t.column("التاريخ", 2).column("البيان", 3).column("الرصيد", 2, Align.END)
        .rows(lines, l -> new String[]{l.date(), l.note(), l.balance()})
        .negativeInRed(2)                                       // column 2; none = every column
        .cellStyle((row, column, text) -> text.startsWith("مرتجع") ? CellStyle.bold() : CellStyle.PLAIN));

document.image(stampPng, 35, Align.END);                       // 35 mm wide, its height from the image
document.image(Path.of("signature.png"), 40, Align.START);
```

- A negative amount is a number with `-` or `−` before or after it, or in brackets (`(1,200.00)`). A date like
  `2026-11-02` is not. Data rows and totals rows take the style; the header does not.
- `cellStyle(rule)` takes the row (from 0, totals included), the column (from 0, in logical order) and the text,
  and returns `CellStyle.bold()`, `CellStyle.color(0xRRGGBB)`, both (`.andBold()`), or `CellStyle.PLAIN`.
  Several rules add up in order.
- The generic PDF report: `.parameter(PdfReportProvider.NEGATIVE_IN_RED, true)`.
- An image (PNG or JPEG; PNG transparency is kept, so a stamp does not hide what is under it) never splits: it
  moves whole to the next page, and shrinks, keeping its shape, when it is wider than the line or taller than a page.

### Arabic-Indic digits (`arabicIndicDigits`)

For official documents that write numbers as `١٢٣` (v2.11.0):

```java
PdfDocument.rtl(Paper.A4).arabicIndicDigits(true)        // the generic report: PdfReportProvider.ARABIC_INDIC_DIGITS
```

- Every number the document draws changes: text, tables, fields, the summary, the letterhead and the page numbers.
  Each number stays where it was in its line (Arabic-Indic digits read left to right too), and the minus sign, the
  comma and the dot stay as written.
- What a scanner reads stays Latin: the content of a QR code and a barcode, the code written under a barcode, and
  the digits of an EAN-13.

### Style (`PdfStyle`)

With nothing set, the style follows the paper (`PdfStyle.forPaper`): a smaller font and margins on A5. To change it:

```java
document.style(PdfStyle.forPaper(Paper.A4)
        .withHeaderColors(0x2C3E50, 0xFFFFFF)   // a dark table header with white text
        .withStripes(true)                      // alternate rows shaded
        .withMargin(28f));

document.style(PdfStyle.forPaper(Paper.A5).inkSaver());   // no fills at all: rules instead of backgrounds
```

### Printing (`PdfPrinter`)

```java
List<String> printers = PdfPrinter.printerNames();          // the printers set up on this computer
Optional<String> preferred = PdfPrinter.defaultPrinter();

PdfPrinter.print(document, printerName, 1);                 // the document on its own paper, one copy
PdfPrinter.print(pdfBytes, printerName, Paper.A5, 2);       // or a ready PDF

PdfPrinter.print(document, printerName,                     // pages 2 to 5, on both sides (v2.11.0)
        PrintOptions.copies(1).pages(2, 5).duplex(PrintOptions.Duplex.LONG_EDGE));
```

The printer is asked for the document's paper (A4 or A5) and pages are sent at their actual size. An empty
`printerName` means the default printer. Copies of a document with more than one page come out as whole sets
(1, 2, 3, 1, 2, 3). Printing takes its time, so call it off the UI thread. If the printer is not on the
computer, a `ReportException` names it and lists the printers that are available.

`PrintOptions` (v2.11.0) also takes a page range (a last page beyond the document means up to its end) and
`Duplex.ONE_SIDED`, `LONG_EDGE` or `SHORT_EDGE`; without it the printer's own setting applies. A printer that
cannot print on both sides prints on one. The preview window has the same two: a "Pages" field (`2-5`, empty for
all) and a "Both sides" box, for paper that is not a roll.

### Several documents in one file and one print job (`PdfBatch`)

For "print every driver's statement for the month":

```java
List<PdfDocument> statements = drivers.stream()
        .map(driver -> StatementPdf.document(driver, Paper.A4))
        .toList();
PdfBatch batch = PdfBatch.of(statements);

batch.save(Path.of("statements-2026-11.pdf"));   // one file
PdfPrinter.print(batch, printerName, 2);          // one job: every statement, then every statement again
byte[] pdf = batch.toBytes();                     // or bytes
```

- Each document keeps its pages as if it were printed alone: its own letterhead, page footer and page numbers
  ("Page 1 of 3" starts again for each document). Each document starts on a new page, in the order given.
- The printer gets **one job**, and copies come out as whole sets of the batch.
- A print job asks the printer for one paper, so every document must be on the same paper: the same size either
  way round (A4 with A4 landscape is fine, the landscape pages are turned onto the sheet), or the same roll width.
  A4 with A5, a sheet with a roll, or the 80 mm roll with the 58 mm one fails with a `ReportException` when the
  batch is written: make one batch per paper.
- A document that cannot be built fails the batch with an error that names its place ("Document 3 of 40 in the
  batch ..."), with the original error as its cause.
- On a roll each document is its own receipt, a page as long as its content, so a printer that cuts at the end of
  each page cuts between them.
- The batch holds the documents themselves, not copies: a change to one after `PdfBatch.of` shows in what is
  written next.

### Preview (`PdfPreview`)

The `smart-report-fx` module has a preview window for any JavaFX program:

<p align="center"><img src="docs/preview-window.png" alt="The print preview window" width="560"></p>

```java
import com.smart.report.fx.PdfPreview;

PdfPreview.of(paper -> StatementPdf.document(statement, companyName, paper))
        .papers(Paper.A4, Paper.A5)       // one paper = no paper choice in the window
        .title("كشف حساب عربية 1751")
        .fileName("كشف 1751")              // the name suggested by "Save PDF"; / : * ? and the like become -
        .rightToLeft(true)                 // an Arabic window; false = English
        .remember("statement")             // the last printer and paper, per kind of document
        .stylesheet(appStylesheetUrl)      // optional: your program's CSS
        .show(ownerWindow);

PdfPreview.of(document).show(ownerWindow);   // or a ready document on its own paper
PdfPreview.of(batch).show(ownerWindow);      // or a batch: all its documents, printed as one job
PdfPreview.ofBatch(paper -> PdfBatch.of(statementsOn(paper)))   // a batch built for the paper the user picks
        .papers(Paper.A4, Paper.A5)
        .show(ownerWindow);

PdfPreview.ofPdf(pdfBytes).show(ownerWindow);                  // any ready PDF: a Jasper report, a file (v2.11.0)
PdfPreview.ofPdf(paper -> jasperPdfOn(paper)).show(ownerWindow);  // or one built for the paper the user picks
```

- A ready PDF opens on the paper of its first page (A4 or A5, upright or landscape, when it is one). A Jasper report
  the program filled itself becomes PDF bytes with `JasperReportProvider.pdf(jasperPrint)`, in `smart-report-jasper`.
- The window takes its owner's icons, so it shows the program's icon in the task bar.

- The window shows the pages with the same drawing the printer receives, and does not block the program.
- A batch is shown and printed like one document: the window's page counter runs over the whole batch, while each
  printed page keeps its own document's number. A batch whose documents are on different papers shows that it
  could not be built.
- When the user changes the paper, the document is built again for the new paper.
- The function that builds the document is called **off the UI thread**: read your data before it and touch no JavaFX node inside it.
- The window's wording is Arabic or English by `rightToLeft`, and `texts(PreviewTexts)` replaces it with your program's terms.
- Keys: Ctrl+P prints (only when the Print button can), Ctrl+S saves, PageUp/PageDown turn the pages and Esc closes the window. On a Mac it is ⌘P and ⌘S.
- To draw pages as images yourself (a custom preview, a thumbnail): `PdfPages.open(pdf).render(pageIndex, scale)` in `smart-report-pdf`.

#### Print straight away (`printOrShow`)

A cashier should not have to open the preview for every receipt. With `remember(key)`, `printOrShow` sends
the document to the printer the user last printed on from the window, and opens the window only when there
is no such printer yet:

```java
PdfPreview.of(paper -> ReceiptPdf.document(sale, shop, paper))
        .papers(Paper.ROLL_80)
        .remember("receipt")
        .printOrShow(ownerWindow)
        .thenAccept(outcome -> {
            switch (outcome) {
                case PrintOutcome.Sent sent -> status.setText("Sent to " + sent.printer());
                case PrintOutcome.Failed failed -> showError("The receipt was not printed", failed.reason());
                case PrintOutcome.Shown shown -> { }   // no printer remembered yet: the window is open
            }
        });
```

- The first time, or when the remembered printer is no longer on the computer, the preview window opens. The
  printer the user prints on from it is remembered, and from then on the document goes straight to it.
- It prints one copy, on the paper the window would open on: the one given to `paper(...)`, else the last
  one printed, else the first of `papers(...)`.
- It returns at once: the printers are asked, and the document is built and printed, off the UI thread. The
  future completes on the JavaFX thread, so its handler may update your nodes.
- When the remembered printer is there but printing fails (the document could not be built, or the printer
  refused the job), the outcome is `Failed` and **no window opens**: a window popping up by itself in the
  middle of the next sale would take the keyboard from the till. `failed.reason()` is for the log; to let the
  user choose another printer, call `show(ownerWindow)` from that branch.
- To change the printer later, open the window with `show(ownerWindow)` and print once from it.

### Print settings per kind of document (`PrintSettings`)

A program prints several kinds of paper (a sales receipt, a statement, a voucher), and each needs its own paper,
printer, way out and copies (v2.11.0). Declare the kinds once and the library keeps the settings and gives a ready
settings pane:

```java
import com.smart.report.print.*;       // in smart-report-api

PrintSettings settings = PrintSettings.of(
        PrintSettingsStore.preferences(Preferences.userRoot().node("my-shop/print")),
        List.of("normal", "thermal"),                                     // printer roles your program names
        DocumentKind.of("sale-receipt", "إيصال البيع",
                PrintProfile.of("80mm", OutputMode.PRINT, PrinterTarget.role("thermal"), 1),
                "80mm", "58mm", "A5"),                                     // the papers it may take
        DocumentKind.of("statement", "كشف حساب",
                PrintProfile.of("A4", OutputMode.PREVIEW, PrinterTarget.role("normal"), 1), "A4", "A5"));

PrintProfile profile = settings.profile("sale-receipt");   // read at print time, never kept for a screen's life
Optional<String> printer = settings.printerName(profile);  // the role turned into a printer; empty = the default

settingsTab.setContent(PrintSettingsPane.of(settings)     // in smart-report-fx
        .rightToLeft(true)
        .roleLabel("thermal", "طابعة الإيصالات")
        .build());
```

- A profile is the paper (by name, as `Paper.parse` reads it), the output mode (`PRINT`, `PREVIEW`, `SAVE_PDF` or
  `ASK`), the printer and the copies. The printer is a role, an installed printer by name, or the system default.
  A role is turned into a printer name at print time, so changing the thermal printer once moves every paper on it.
- Nonsense is refused when it is built: a paper the kind does not take, a role the program did not declare, copies
  outside 1 to 99.
- What is stored is one line of text per kind (`paper=A5;mode=preview;printer=role:normal;copies=1`), read
  tolerantly: a value written by another version never stops a print, and any part that cannot be read falls back
  to the kind's default.
- `PrintSettingsStore` is three methods (get, put, remove): `preferences(node)`, `inMemory()` for tests, or your own
  over your program's settings table.
- The pane has a row per kind (paper, output, printer, copies, a reset to the default and a trial print) and a
  printer per role. Every change is saved at once. `.sample("statement", paper -> ...)` prints your own document
  as the trial page; without it the trial is a page with the kind, the paper, the printer and the time.
- `PdfPreview.remember(key)` keeps working as before.

**One call that does what the profile says** (v2.11.0):

```java
PdfPreview.of(paper -> ReceiptPdf.document(sale, paper))
        .fileName("إيصال " + sale.number())
        .print(settings, "sale-receipt", ownerWindow)           // the profile is read now
        .thenAccept(result -> {
            switch (result) {
                case PrintResult.Printed printed -> status.setText("أُرسل إلى " + printed.printer());
                case PrintResult.Saved saved -> status.setText("حُفظ في " + saved.file());
                case PrintResult.Failed failed -> showError("تعذرت الطباعة", failed.reason());
                case PrintResult.Shown shown -> { }
                case PrintResult.Cancelled cancelled -> { }
            }
        });

preview.print(settings, "sale-receipt", p -> p.withMode(OutputMode.PREVIEW), ownerWindow);   // this time only
```

- `PRINT` sends the profile's copies to its printer on its paper. `PREVIEW` opens the window on the profile's
  paper, printer and copies, with the kind's papers to choose from. `SAVE_PDF` asks where to save, with no
  window. `ASK` asks each time: print, preview or save.
- If the profile's printer is not on this computer, the window opens with a line above the page that says so.
  **The document is never sent to another printer silently.**
- The override changes one thing for this print only (another paper, preview first) and saves nothing.
- A ready document (`PdfPreview.of(document)`) keeps its own paper.
- The window can also be told where to start: `.printer(name)`, `.copies(n)`, and `.notice(text)` for a line
  of your own above the page.

### Font

The library bundles the **Noto Naskh Arabic** font (regular and bold), so the output is the same on every
computer with no setup. To change it for the whole program, in this order:

1. The `smart.report.pdf.font` property (and optionally `smart.report.pdf.font.bold`) — the path of a `.ttf` file.
2. A classpath resource named `fonts/report-font.ttf` (and `fonts/report-font-bold.ttf`).

**A font for one document.** `font(PdfFont)` gives one document its own font; every other document keeps the
program's font:

```java
// Make it once and reuse it: the files are read and checked here.
PdfFont amiri = PdfFont.of(Path.of("fonts/Amiri-Regular.ttf"), Path.of("fonts/Amiri-Bold.ttf"));

PdfDocument.rtl(Paper.A4)
        .font(amiri)
        .title("فاتورة ضريبية")
        // ...
        .save(Path.of("invoice.pdf"));
```

- With one file, `PdfFont.of(regular)`, bold text is drawn from it, thickened. `PdfFont.of(byte[])` and
  `PdfFont.of(byte[], byte[])` take the bytes instead, for a font inside the program's jar.
- The file must be a TrueType font (`.ttf`) that allows embedding. A font collection (`.ttc`) or an OpenType font
  with CFF outlines (most `.otf` files) fails with a `ReportException` when the `PdfFont` is made, not when printing.
- Arabic is drawn with the Arabic presentation forms (U+FB50–U+FDFF and U+FE70–U+FEFF), so an Arabic document
  needs a font that has them, as Noto Naskh Arabic, Amiri, Arial and Tahoma do. A font without them is accepted
  (a Latin document needs none), but its Arabic is drawn from a system font, which differs from computer to
  computer and is missing in a bare container; the log warns about it once per font.
- The generic report takes it as a parameter: `.parameter(PdfReportProvider.FONT, amiri)`.

**A character the font lacks** is drawn from **Liberation Sans**, which ships inside PDFBox (SIL Open Font
License), so it is the same on every computer: `²`, `³`, `½`, `±`, `µ`, `‰`, `≤`, `≥` and other symbols that Noto
Naskh lacks, so "م³" prints right. A character Liberation Sans lacks too is tried in a system font (Arial and
others), and as a last resort is printed as `?` with a warning in the log. Invisible characters, such as the
variation selector U+FE0F that phones add after `©` or `✔`, are skipped rather than printed as `?`.

**Fail instead of printing `?`.** On an invoice or a statement, a `?` in place of a character is a wrong
document. `failOnMissingGlyphs(true)` stops writing it with a `ReportException` that names the character, its
code point and the text it is in:

```java
PdfDocument.rtl(Paper.A4).failOnMissingGlyphs(true).text("رقم الصنف 中 12").toBytes();
// ReportException: No PDF font has a glyph for '中' (U+4E2D) in "رقم الصنف 中 12": neither the font
// NotoNaskhArabic-Regular nor the fallback fonts (Liberation Sans, the system font) draw it. ...
```

It is off unless set, so a document that does not ask for it prints `?` as before. In the generic report:
`.parameter(PdfReportProvider.FAIL_ON_MISSING_GLYPHS, true)`. Only a character that neither the document's font
nor Liberation Sans has depends on the computer: it passes where a system font has it.

**Turn it on for invoices, receipts, statements and other money documents.** The error comes before anything is
saved or printed, so no customer gets a document with a `?` in it:

```java
PdfDocument invoice = PdfDocument.rtl(Paper.A4).failOnMissingGlyphs(true);
```

> Arabic is prepared automatically before drawing: letters are joined and ordered right to left, with the
> lam-alef ligature, and letters such as ڤ, چ, گ, پ, ک and ی join as well. This is the work of `ArabicTextShaper`, and it needs no extra dependency (no ICU4J or
> anything else). Diacritics (tashkeel) are centred over their letters approximately — PDFBox does not apply
> the font's GPOS tables.

## Generic PDF or Excel (no template)

The same `ReportRequest` works for both; only `ReportType` changes:

```java
import com.smart.report.*;

ReportRequest<Employee> request = ReportRequest.<Employee>builder()
        .title("تقرير الموظفين")
        .data(employees)
        .addColumn("id", "الرقم")
        .addColumn("name", "الاسم")
        .addColumn("salary", "الراتب")
        .build();

byte[] pdf = ReportService.generate(ReportType.PDF, request);     // needs smart-report-pdf
byte[] xlsx = ReportService.generate(ReportType.EXCEL, request);  // needs smart-report-excel

ReportService.generateToFile(ReportType.PDF, request, Path.of("employees.pdf"));
```

Each column's name (`addColumn("id", ...)`) is read from the object by reflection: `getId()`, `isId()`, `id()`
(record components), and finally the field itself.

A column can carry its own options for the PDF report — a format pattern, an alignment and a width weight:

```java
.addColumn(ColumnDefinition.of("amount", "المبلغ").withFormat("#,##0.00").withAlign(Alignment.END).withWeight(2))
.addColumn(ColumnDefinition.of("date", "التاريخ").withFormat("d MMMM yyyy"))   // 26 نوفمبر 2026
```

- `withFormat`: a `DecimalFormat` pattern for numbers (rounded half up) or a `DateTimeFormatter` pattern for dates.
  Month and day names follow the report's language; digits stay Latin. A pattern that does not fit a value
  (`HH:mm` on a date with no time) fails with a `ReportException` naming the column.
- `withAlign`: `START`, `CENTER` or `END`, relative to the report's direction. Without it, a column of numbers goes
  to the end of the line and anything else to the start.
- `withWeight`: the column's share of the table width (2 = twice a normal column).
- `withTotal`: adds the column up in a totals row under the table, in both reports (see [Totals](#totals)).

The Excel report applies the format (turned into an Excel format, so a number stays a number and a date a date)
and the alignment. It ignores the weight: its columns are sized to their content. A date pattern letter Excel has no
format for (a time zone, `z`), or an hour Excel would show differently (`hh` without `a`), fails with a
`ReportException` naming the column.

Both reports run right to left if the title or any column header contains Arabic (in Excel the sheet itself
runs right to left). The PDF report is on portrait A4. To change that:

```java
.parameter(PdfReportProvider.PAPER, "A5-landscape")        // or a Paper object
.parameter(PdfReportProvider.LANDSCAPE_FROM_COLUMNS, 8)   // from 8 columns the paper turns sideways (v2.11.0)
.parameter(PdfReportProvider.RIGHT_TO_LEFT, true)
.parameter(ExcelReportProvider.RIGHT_TO_LEFT, true)       // the direction of the Excel sheet
```

In the PDF, numbers get thousands separators (`1,234,567.5`, `1,200.50`), dates are written `2026-11-26` and
`2026-11-26 10:30:00`, and a column holding only numbers (or totalled) is aligned to the end of the line so the
numbers line up.

In Excel, numbers, booleans and dates are written with their own types, not as text. The header row stays in view
as you scroll and has filter buttons, so the sheet can be sorted and filtered straight away. Each column is as wide as
its header and content, up to Excel's limit of 255 characters; the title does not count. To keep a large report fast,
text is measured in the first 500 rows only: a longer text further down is cut at the column's edge (the cell still
holds all of it). Numbers and dates fit in every row, so none shows as `####`.

If you ask for a type whose module is not on the classpath, a `ReportException` tells you which dependency
to add.

### Totals

Mark the columns to add up with `withTotal()`, and the report gets a totals row under the table:

```java
ReportRequest<Expense> request = ReportRequest.<Expense>builder()
        .title("المصروفات")
        .data(expenses)
        .addColumn("item", "البيان")
        .addColumn(ColumnDefinition.of("amount", "المبلغ").withFormat("#,##0.00").withTotal())
        .addColumn(ColumnDefinition.of("vat", "الضريبة").withFormat("#,##0.00").withTotal())
        .totalsLabel("إجمالي المصروفات")   // optional: "الإجمالى" by default, "Total" in a left-to-right report
        .build();
```

Each total sits under its own column, in that column's format. The label goes under the first column that is not
totalled (with every column totalled, the row has no label). An empty value (`null`) adds nothing. A value that is
not a number in a totalled column (text, a date, `NaN`) fails with a `ReportException` naming the column and the row,
in both reports: a total that skipped it would be wrong without anyone noticing. With no data the total is 0.

In the PDF the totals row looks like `PdfTable.totals`: bold, on a shaded background. Its sum is exact: the values
are added as `BigDecimal`, a `double` or `float` by the decimal it shows (`0.1 + 0.2` totals `0.3`).

In Excel it is a bold row with a line above and a double line below, after one empty row. Each total there is a real
`=SUM(...)` formula over the data rows, so it follows any change made to the sheet. The file also carries the value
of that formula, for programs that show a file without recalculating it. That value is Excel's own arithmetic: the
numbers as stored in the cells (`double`, about 15 significant digits) added from the first row down, so recalculating
never changes it. So the Excel total can differ from the exact PDF total in the last digits of a `float`
(`0.1f` is stored as `0.10000000149011612`) or of a number with more than about 15 digits. For amounts in two
decimals whose total stays under 15 digits, both show the same total. The filter covers the header and the data only, and the empty row keeps Excel
from taking the totals row into a sort. The two extra rows count towards the sheet's limit of 1,048,576 rows.

#### Subtotals by group

From `v2.10.0`, `groupBy` names a property to group the rows by, for an account statement by customer or by month.
Under each group comes a subtotal row of the `withTotal()` columns, labelled with the group's value, and the grand total
stays at the end:

```java
ReportRequest<Entry> request = ReportRequest.<Entry>builder()
        .title("كشف حساب العملاء")
        .data(entries)                       // already sorted by customer
        .addColumn("customer", "العميل")
        .addColumn("date", "التاريخ")
        .addColumn(ColumnDefinition.of("debit", "مدين").withFormat("#,##0.00").withTotal())
        .addColumn(ColumnDefinition.of("credit", "دائن").withFormat("#,##0.00").withTotal())
        .groupBy("customer")
        .subtotalLabel("إجمالى العميل: ")   // optional: "إجمالى: " by default, "Total: " in a left-to-right report
        .build();
```

The rows are not sorted: a new group starts wherever the value changes from the row before, so sort the data by that
property first. For a statement by month, group by a property that holds the month (a `YearMonth`, for example). The
property does not have to be a column; when it is, the label shows the value in that column's format. The label goes
where the grand total's does, under the first column that is not totalled, followed by the group's value
(«Total: Ahmed», «إجمالى: أحمد»); an empty value (`null`) is a group of its own, shown as «Total: —». Each subtotal
keeps its column's format, like the grand total. A report with `groupBy` needs at least one `withTotal()` column, or
it fails with a `ReportException`.

Make the group's column the first column, and the label sits under it. Two options (v2.11.0), for both reports:

```java
.groupHeader(true)          // a line with the group's value above its rows
.pageBreakPerGroup(true)    // each group starts on a new page: each customer's statement on its own
```

- In the PDF, `groupHeader` puts a heading with the value above each group, and each group gets its own table with
  the column header under it; the heading never stays alone at the foot of a page. With `pageBreakPerGroup` each
  group starts on a new page, and the grand total comes under the last group.
- In Excel, `groupHeader` adds a bold row with the value (in the group's column, so the filter on that column keeps
  it with its rows, or in the first column), and `pageBreakPerGroup` adds a printed page break after each subtotal.
- Both need `groupBy`, or the report fails with a `ReportException`.

In the PDF a subtotal row looks like the totals row, and it never starts a page on its own: when it does not fit under
the last row of its group, that row moves to the next page with it, under the repeated header. The grand total of a
grouped report gets a heavy rule above it and a double rule below, so it does not read as one more subtotal.

In Excel a subtotal is a bold row with a line above and below, holding `=SUBTOTAL(9, ...)` over the rows of its group,
with its computed value. The grand total is then `=SUBTOTAL(9, ...)` over all the rows: `SUBTOTAL` skips the subtotals
inside its range, so nothing is counted twice. When the group's property is a column, the subtotal row holds the
group's value itself in that column, with the same type and format as its rows, and the words (`Total`, `إجمالى`) go
under the next column that is not totalled; when it is not a column, the whole label goes where the grand total's
does. Each subtotal row counts towards the sheet's row limit.

Grouped sheets and the header's filter: the subtotals sit between the data rows, so the filter covers them too.
Filtering the group's column on one customer keeps that customer's rows and subtotal, and the grand total adds up only
what the filter leaves visible. Filtering another column keeps a subtotal only if its own cell matches, but every
visible subtotal still adds up only the visible rows of its group. Sorting a grouped sheet mixes the subtotal rows into
the data and breaks their ranges, as with Excel's own Subtotal command: undo the sort, or filter instead.

## JasperReports (a visually designed template)

```java
import com.smart.report.JasperExportFormat;

ReportRequest<Employee> request = ReportRequest.<Employee>builder()
        .data(employees)
        .template("reports/employee.jrxml") // a file on disk or a classpath resource
        .parameter("ReportTitle", "تقرير الموظفين")
        .exportFormat(JasperExportFormat.PDF) // or XLSX / HTML / CSV
        .build();

byte[] pdf = ReportService.generate(ReportType.JASPER, request);  // needs smart-report-jasper
```

`request.data()` is turned into a `JRBeanCollectionDataSource` automatically, and `request.parameters()` are
passed as report parameters (`$P{...}`). Compiled templates are cached and reused.

### Arabic in the PDF

A PDF draws every letter from a font embedded in the file. The font JasperReports uses when a template names none
(`SansSerif`, drawn as Helvetica) has no Arabic, so Arabic text used to vanish from the PDF without an error.
`smart-report-jasper` carries **Noto Naskh Arabic** (regular and bold, the font `smart-report-pdf` uses) as a
JasperReports font extension, and a template chooses its font like this:

- **A template that names no font** (or names `SansSerif`) needs nothing. Its Arabic letters, Arabic digits and
  Arabic punctuation (، ؛ ؟) are drawn with Noto Naskh Arabic, bold ones with the bold file, right to left. Every
  other character keeps JasperReports' default font, so a report without Arabic comes out exactly as before.
- **A template that names a font** keeps it, and so does a program that changed JasperReports' default font
  (`net.sf.jasperreports.default.font.name`). If that font has no Arabic, set `fontName="Noto Naskh Arabic"` on the
  fields that show Arabic: all of their text is then drawn with it, Latin included, and bold is drawn thickened.
- **Give Arabic room.** A line of Noto Naskh is about 1.7 times the font size high (room for the vowel marks): a
  10 pt field needs 18 pt, an 18 pt title 31 pt. JasperReports prints nothing of a text whose first line does not
  fit its box, so `ReportService` fills with `ArabicTextMeasurerFactory`, which changes only that case. In a box
  that does not grow (`textAdjust` `CutText`, the default), Arabic text with no line that fits gets its first line
  drawn anyway when the box is at least about 1.15 times the font size (a box that holds the same line in Latin):
  the box keeps its size, and in a box shorter than about 1.5 times the font size the tails of letters such as
  ي and ج can reach past its bottom edge. A text that still prints nothing, in any language, is logged as a
  warning naming the report and the field; so is a line drawn past its box. Text that fits, text without Arabic,
  `StretchHeight` and `ScaleFont` fields come out exactly as before. A program that fills with `JasperFillManager`
  itself can set `net.sf.jasperreports.text.measurer.factory=com.smart.report.jasper.ArabicTextMeasurerFactory`.

The extension is registered by `jasperreports_extension.properties` at the root of the jar. A fat jar has to merge
that file from all jars rather than keep one (maven-shade-plugin: `AppendingTransformer`). With
`jasperreports-fonts` (DejaVu) on the classpath, its own `SansSerif` entry wins and the default font has no Arabic
again: name `DejaVu Sans` or `Noto Naskh Arabic` in the template.

## With Claude Code

The `claude-skill/pdf-print` folder holds a skill that wires the library into any existing or new project:
the dependency, building the document from the program's data, the preview window, and the tests. Copy it
to `~/.claude/skills/` and then tell Claude: "add PDF printing for the invoice" or "add a preview before
printing".

## Upgrading from 1.0

- The library is now modules: replace `smart-report-plugin` with the module you use (`smart-report-pdf` / `-excel` / `-jasper`).
- `JasperExportFormat` moved from `com.smart.report.jasper` to `com.smart.report`.
- If you wrote your own `ReportProvider`: it gained `type()`, and it is registered in `META-INF/services/com.smart.report.ReportProvider`.
- The generic PDF report now wraps long text instead of cutting it with `...`, and an Arabic report's table runs from the right.

## Tests and build

```bash
./mvnw test       # every module
./mvnw verify     # the tests, and each module's API checked against the last release
./mvnw install    # builds the modules and installs them in the local repository
```

`./mvnw` (`mvnw.cmd` in a Windows command prompt) downloads and runs Maven 3.9.16, the Maven JitPack builds the
library with. How a release is made, and what to do when JitPack cannot build one: [RELEASING.md](RELEASING.md).

The PDF tests read the **positions** of the drawn glyphs, not only the extracted text: direction faults
(a column on the wrong side, a total under another column, the end of a sentence on the first line) do not
show in the text.

## Licenses

The project is licensed under the [MIT License](LICENSE). The bundled Noto Naskh Arabic font is licensed under the
[SIL Open Font License 1.1](smart-report-pdf/src/main/resources/com/smart/report/pdf/fonts/OFL.txt). Symbols it
lacks are drawn from Liberation Sans, which ships inside the PDFBox jar under the same license (see PDFBox's
`META-INF/LICENSE`); a PDF that uses them embeds a subset of it.
