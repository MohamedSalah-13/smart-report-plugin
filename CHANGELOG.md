# Changelog

## [Unreleased]

### Added
- Amount in words (تفقيط): `AmountInWords.arabic` writes an amount the way an invoice does — «فقط سبعة آلاف ومائتا
  جنيه لا غير» — with the counted noun in the right form (singular, dual, plural, accusative) and numbers that agree
  with its gender. Ready currencies are EGP, SAR, AED, KWD and USD, and a program can define its own.

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
