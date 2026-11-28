# Changelog

## [2.11.0] - 2026-11-28

### Added
- Print settings per kind of document: each kind (a sales receipt, a statement...) keeps its paper, printer or
  printer role, copies and way out (print, preview, save a PDF, or ask), with a ready settings pane and a trial
  print. One call, `PdfPreview.print(settings, kind, owner)`, then does what the settings say; a printer that is not
  installed opens the preview with a notice instead of printing elsewhere. (#43, #44)
- Ready-made documents: an invoice in three layouts that also prints as a receipt on an 80 or 58 mm roll, an account
  statement and a receipt/payment voucher, filled with the program's own text. (#46)
- Item labels with EAN-13 or Code 128 barcodes, on a label roll or an A4 sheet, and `ean13(...)` in documents. (#33)
- Barcode and QR modules in whole printer dots (`printerDpi(203)`), for codes that scan from thermal printers. (#48)
- PDF/A-3B with the e-invoice XML attached inside the PDF, checked with veraPDF. (#52)
- A report look a shop sets once: colour sets, a "printed on ... by ..." line, `1 / 3` page numbers, paper with a
  pre-printed letterhead, and the totals row in its own size. (#45)
- Negative amounts in red and bold cells in tables, and a stamp or signature image in the document body. (#34, #35)
- Arabic-Indic digits (`١٢٣`) for a whole document; codes for scanners stay Latin. (#39)
- Printing a page range and on both sides, from code and from the preview window. (#41)
- The cash drawer of a receipt printer, and asking a printer whether it lists a paper size. (#47)
- A header line above each group, or each group on a new page, in grouped generic reports. (#40)
- The preview window shows any PDF (a Jasper report, a file) and takes the program's icon. (#51)
- `AmountInWords`: 12 more currencies, lookup by ISO code, and negative amounts with a word of the program's. (#50)
- A scannable document code with a check digit (`DocumentCode`), and QR codes and barcodes as PNG images. (#49, #53)
- A wide generic PDF report can turn the page sideways from a number of columns. (#54)
- A javadoc jar for every module, and the public API documented in English. (#36)

### Changed
- Several copies on a thermal roll go out as one job each, so the printer cuts between them. (#47)
- The file name suggested by the preview's "Save PDF" no longer holds characters Windows refuses (`/ : * ?`). (#54)

## [2.10.0] - 2026-11-28
## [2.10.0] - 2026-11-28

### Added
- `PdfDocument.watermark("صورة")`: a large light word over every page, such as «صورة» on a copy of an invoice,
  «ملغاة» or `DRAFT`. It is translucent, so the content under it stays readable, and `watermark(text, color)` takes
  a colour. On a thermal roll it is repeated down the receipt. (#31)
- Subtotals by group in the generic PDF and Excel report: `groupBy("customer")` adds a subtotal row under each
  group, and `subtotalLabel(...)` changes its words. In the PDF a subtotal never starts a page alone, and in Excel
  the subtotals and the grand total are `SUBTOTAL` formulas that follow the header filter. (#32)

## [2.9.1] - 2026-11-28

### Changed
- The PDF page layout was rewritten so that every part of a document follows the same page rules. Documents come
  out exactly as before. (#30)

### Fixed
- A generic Excel report whose title starts or ends with an apostrophe (`'Q1' sales`) no longer fails: the sheet
  name drops the apostrophes at its ends, and the title cell keeps them. (#29)

## [2.9.0] - 2026-11-27

### Added
- `PdfDocument.pageBreak()`: the next part starts on a new page. It never leaves a blank page, and does nothing on a
  thermal roll. (#27)
- `PdfBatch`: several documents as one PDF and one print job, each with its own letterhead and page numbers.
  `PdfPreview.ofBatch` previews a batch and prints it. (#27)
- `PdfDocument.font(PdfFont)`: a font for one document, from a file or from bytes; other documents keep the
  program's font. (#26)
- `failOnMissingGlyphs(true)`: a character no font has stops the document with an error naming it, instead of
  printing `?`. The generic report takes both as parameters. (#26)

### Changed
- Copies of a document with more than one page are printed collated, as whole sets. (#27)

### Fixed
- `²`, `³`, `½`, `±` and other common symbols missing from the bundled font no longer print as `?` on a computer
  without Arial or DejaVu: they are drawn from Liberation Sans, which ships inside PDFBox. (#26)
- Invisible characters, such as the one phones add after `©`, no longer print as `?`. (#26)

## [2.8.0] - 2026-11-27

### Upgrade notes
- `ColumnDefinition` has a sixth component, `total`, so a record pattern that takes it apart
  (`case ColumnDefinition(var field, var header, var format, var align, var weight)`) needs a sixth variable.
  Compiled programs and every other use of the class keep working. (#23)

### Added
- Totals in the generic PDF and Excel report: `ColumnDefinition.withTotal()` adds a column up in a row under the
  table, in the column's format, and `totalsLabel` changes the row's label. In Excel each total is a `SUM` formula.
  (#23)

### Fixed
- An Arabic text in a Jasper template whose box is a little shorter than its line no longer prints nothing: its
  first line is drawn. A text that still prints nothing, in any language, is logged as a warning naming the report
  and the field. (#24)

## [2.7.1] - 2026-11-27

### Fixed
- 2.7.0 could not be downloaded from JitPack: its build ran on an old Maven that the new API check does not support.
  2.7.1 is the same release, built with a Maven wrapper; use it instead of 2.7.0.

## [2.7.0] - 2026-11-27

### Added
- `PdfPreview.printOrShow(owner)`: prints straight on the remembered printer without opening the preview, and opens
  the preview when there is none. (#16)
- Ctrl+P prints and Ctrl+S saves in the preview window. (#16)
- `ZatcaQr.phase2(...)`: the QR code of a ZATCA phase 2 simplified tax invoice. (#19)
- Amount in words for QAR, BHD, OMR and JOD, and in English (`AmountInWords.english`). (#19)

### Changed
- The generic Excel report freezes its header row with filter buttons, and is much faster on large reports. (#18)

### Fixed
- Arabic text disappeared from PDFs exported from Jasper templates. It is now drawn with the bundled Noto Naskh
  Arabic; Arabic text boxes need about 1.7 times the font size in height (see the README). (#20)
- A title on the generic Excel report no longer stretches the first column. (#18)

## [2.6.0] - 2026-11-27

### Added
- `PdfDocument.barcode(text)` (and `barcode(text, align)`, `barcode(text, heightMm, align)`): a Code 128 barcode
  drawn as vector bars with the code written under it, at the start, centre or end of the line in the document's
  direction, kept on one page. Its narrowest bar is 0.33 mm and narrows to fit a thin roll down to 0.25 mm; a code
  too long for that fails with a `ReportException` naming it rather than printing unreadable bars. Encoded by the
  ZXing core that `smart-report-pdf` already brings. (#13)

### Fixed
- Signature boxes no longer land alone at the top of a new page, away from the figures they sign for. When they
  do not fit under the summary before them, the summary moves to the next page with them, together with any text
  and space between the two (such as the amount in words). Text written straight before the boxes (no summary)
  moves with them the same way. When the group cannot move (it is taller than a page, or nothing but the first
  page's letterhead is above it), the last line of the text before the boxes goes with them instead. A thermal
  roll is drawn as before. (#14)

## [2.5.0] - 2026-11-27

### Upgrade notes
- `smart-report-excel` now brings Apache POI 6.0.0 (from 5.3.0), with its new dependencies (`jakarta.xml.bind-api`,
  `xmlsec` 4). A program that uses POI itself as well gets 6.0.0 unless it pins its own version. (#9)

### Added
- `PdfDocument.qrCode(text)` (and `qrCode(text, align)`, `qrCode(text, sizeMm, align)`): a QR code drawn as vector
  squares with its quiet zone, at the start, centre or end of the line in the document's direction, and kept on one
  page. A code wider than a thermal roll shrinks to fit, and the receipt is still cut right under it. Arabic text is
  encoded as UTF-8. The encoding is done by ZXing core 3.5.4 (Apache License 2.0, no dependencies of its own), which
  `smart-report-pdf` now brings. (#12)
- `ZatcaQr.payload(sellerName, vatNumber, timestamp, totalWithVat, vatTotal)` in `smart-report-api`: the text of the
  QR code a simplified tax invoice carries in Saudi Arabia (ZATCA e-invoicing, phase 1): the Base64 of its five TLV
  fields, with lengths in UTF-8 bytes. (#12)

### Changed
- Build plugins (compiler, surefire, jar, source) updated, the JavaFX the preview compiles against is 21.0.13.1, and
  the tests run on JUnit 6.1.3. (#6, #7)
- JasperReports 6.21.5 (from 6.21.3). (#10)

### Fixed
- The preview window opened a long receipt fitted whole into the window, as a strip too narrow to read. A roll now
  opens as wide as the window allows, up to twice its size; "Whole page" and "Fit width" still do what they say,
  and the view the user picks stays. (#11)
- The preview draws a page into an image of at most 8192 pixels a side, so a very long receipt no longer asks for
  an image hundreds of megabytes large; past that it is shown a little softer. Printing and saving are not affected.
  (#11)

## [2.4.0] - 2026-11-26

### Added
- `PdfDocument.letterheadOnEveryPage(true)` draws the letterhead (and logo) at the top of every page of a long
  document, with a continuing table's header under it.
- `PdfDocument.pageFooter(text)`: a small line at the bottom of every page, above the page number.
- `PdfDocument.signatures("المستلم", "المحاسب", "الختم")`: signature boxes side by side in reading order, kept together.
- Thermal receipt paper: `Paper.ROLL_80` and `Paper.ROLL_58` (and `Paper.roll` for other widths). A receipt is one
  page as long as its content, with a narrow style, and the printer is not asked for a paper size.
- The Excel report now applies a column's format (converted to an Excel format, values stay numbers and dates) and
  alignment; the weight stays PDF-only. (#5)

### Fixed
- A table header taller than a third of the page is drawn once (split if taller than a page) instead of repeating on
  every page and running off the paper.
- A field whose value is taller than a page continues on the next page instead of running off the paper. (#5)

### Changed
- PDFBox 3.0.8 (from 3.0.3) and slf4j 2.0.20 (from 2.0.16). (#5)

## [2.3.0] - 2026-11-26

### Added
- Amount in words (تفقيط): `AmountInWords.arabic` writes an amount the way an invoice does — «فقط سبعة آلاف ومائتا
  جنيه لا غير» — with the counted noun in the right form (singular, dual, plural, accusative) and numbers that agree
  with its gender. Ready currencies are EGP, SAR, AED, KWD and USD, and a program can define its own. (#4)

## [2.2.0] - 2026-11-26

### Added
- Per-column options for the generic PDF report: `ColumnDefinition.withFormat` (a number or date pattern, such as
  `#,##0.00` or `d MMMM yyyy`, with month names in the report's language), `withAlign` and `withWeight`, and
  `ReportRequest.Builder.addColumn(ColumnDefinition)`. Existing code that builds columns keeps working as before.

### Fixed
- A generic Excel report with an Arabic title or column headers now runs right to left, like the PDF report built
  from the same request; its direction can be set with `ExcelReportProvider.RIGHT_TO_LEFT`.
- The generic PDF report no longer prints values as raw `toString`: numbers get thousands separators
  (`1,234,567.5`), dates read `2026-11-26` or `2026-11-26 10:30:00`, and a column of numbers is aligned to the
  end of the line.
- Letters outside the basic Arabic block — ڤ, چ, گ, پ, ک, ی, ژ and the Urdu letters ٹ, ڈ, ڑ, ھ, ہ, ے — now join
  their neighbours instead of being drawn apart and breaking the word.

## [2.1.1] - 2026-11-26

### Fixed
- A table row taller than a whole page (a cell whose text wraps over more lines than the page holds) is no
  longer drawn past the bottom of the paper, where the rest of it was lost in print without any warning.
  The row now continues on the next page under the repeated table header. Rows that fit on a page are laid
  out exactly as before. (#1)
