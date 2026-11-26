# Changelog

## [2.1.1] - 2026-11-26

### Fixed
- A table row taller than a whole page (a cell whose text wraps over more lines than the page holds) is no
  longer drawn past the bottom of the paper, where the rest of it was lost in print without any warning.
  The row now continues on the next page under the repeated table header. Rows that fit on a page are laid
  out exactly as before. (#1)
