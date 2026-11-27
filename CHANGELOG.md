# Changelog

## [Unreleased]

### Added
- `PdfDocument.barcode(text)` (and `barcode(text, align)`, `barcode(text, heightMm, align)`): a Code 128 barcode
  drawn as vector bars with the code written under it, at the start, centre or end of the line in the document's
  direction, kept on one page. Its narrowest bar is 0.33 mm and narrows to fit a thin roll down to 0.25 mm; a code
  too long for that fails with a `ReportException` naming it rather than printing unreadable bars. Encoded by the
  ZXing core that `smart-report-pdf` already brings.

### Fixed
- Signature boxes no longer land alone at the top of a new page, away from the figures they sign for. When they
  do not fit under the summary before them, the summary moves to the next page with them, together with any text
  and space between the two (such as the amount in words). Text written straight before the boxes (no summary)
  moves with them the same way. A group taller than a page, and a thermal roll, are drawn as before.

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
