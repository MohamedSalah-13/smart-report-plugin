---
name: pdf-print
description: Adds PDF output and printing to a Java program using the shared smart-report library (com.github.MohamedSalah-13.smart-report-plugin on JitPack) instead of writing print code again - an Arabic-ready document on A4 or A5 with letterhead, tables with totals rows, a summary and page numbers, a JavaFX preview window, saving as a file and sending to a chosen printer. Use this whenever the user wants to print something or produce a PDF from a desktop or backend Java program - an invoice, a statement, a receipt on a full page, a report, a list - including phrasings like "print", "PDF", "preview before printing", "A5", "طباعة", "اطبع الكشف", "فاتورة PDF", "معاينة قبل الطباعة", "حفظ PDF", "تقرير يتطبع", "زي الطباعة اللى فى naqlat" - and also when a program's printed page comes out wrong in Arabic (table running the wrong way, text cut with "...", a minus sign or a date on the wrong side, letters not joined), when a long page is shrunk onto one sheet, or when a program still prints a JavaFX node or uses iText.
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
     fits A5 upright saves half the paper.
3. If the program uses **iText**, tell the user before going on: iText 7 and 8 are AGPL, which a closed
   program cannot ship without a commercial licence. This library exists partly to avoid that. Do not add
   iText to make something work.

## The steps

### 1. Dependency

Use the newest tag of the library:
`gh api repos/MohamedSalah-13/smart-report-plugin/tags --jq '.[0].name'`. The preview window
(`smart-report-fx`) exists from `v2.1.0`.

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

<!-- The page itself, saving and printing. Brings PDFBox only. -->
<dependency>
    <groupId>com.github.MohamedSalah-13.smart-report-plugin</groupId>
    <artifactId>smart-report-pdf</artifactId>
    <version>v2.1.0</version>
</dependency>
<!-- Only for a JavaFX program that wants the preview window. JavaFX itself is the program's own. -->
<dependency>
    <groupId>com.github.MohamedSalah-13.smart-report-plugin</groupId>
    <artifactId>smart-report-fx</artifactId>
    <version>v2.1.0</version>
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
- `table(...)` - columns with weights, rows, and `totals(...)` rows drawn as a band with one cell under each
  column. Several tables are fine; the column headings repeat on every page a table runs onto.
- `summary(label, value)` lines and one `summaryTotal(label, value)` - the figure the reader looks for.
- `footer(text)`, and page numbers by themselves once there is more than one page.

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
            .fileName("فاتورة " + invoice.number())
            .rightToLeft(true)                          // Arabic window; false for English
            .remember("invoice")                        // last printer and paper, per kind of document
            .stylesheet(Ui.STYLESHEET)                  // the program's CSS, if it has one
            .show(getScene().getWindow());
});
```

The window shows the pages as the printer will receive them, lets the person choose the printer, the paper
and the copies, and has "طباعة" and "حفظ PDF". It does not block the program.

The function given to `PdfPreview.of` runs **off the JavaFX thread**, once per paper the person chooses. Read
everything it needs before the call, as above, and keep JavaFX nodes and lazy-loaded entities out of it -
otherwise the preview fails only sometimes, which is the worst way to fail.

Without the preview (a backend, or a "print now" button):

```java
byte[] pdf = InvoicePdf.document(invoice, company, Paper.A5).toBytes();      // or .save(Path)

List<String> printers = PdfPrinter.printerNames();
PdfPrinter.print(document, printerName, copies);      // blank name = the system's default printer
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
          .build();
  byte[] pdf = ReportService.generate(ReportType.PDF, request);
  ```

  Columns are read from the objects by name. Use `PdfDocument` instead as soon as figures need formatting
  or the page needs more than one table.
- **The same list as Excel** - add `smart-report-excel` and ask for `ReportType.EXCEL`.
- **A page designed in Jaspersoft Studio** - add `smart-report-jasper` and ask for `ReportType.JASPER` with
  the template's path. Only for a program that already has templates; a new page is quicker as a
  `PdfDocument`.
- **A program with `module-info.java`** - the jars have no module descriptor, only stable automatic names:
  `requires com.smart.report.pdf;`, `requires com.smart.report.fx;` and `requires com.smart.report.api;`
  (for `ReportException`). This has not been tried in a modular program; if the build or the launch
  complains, say so to the user rather than working round it silently.
- **Another font** - the program's own TTF as the classpath resource `fonts/report-font.ttf` (and
  `fonts/report-font-bold.ttf`), or `-Dsmart.report.pdf.font=/path/to/font.ttf`.
- **Receipts on a roll (80 mm)** - not what this library lays out. `Paper.ofMillimetres("80mm", 80, 200)`
  gives a page of that size, but a receipt has no fixed height; say this to the user before building one.

## When it does not work

- **The dependency does not resolve.** JitPack builds a tag the first time it is asked, which takes a minute
  or two: open `https://jitpack.io/com/github/MohamedSalah-13/smart-report-plugin/<tag>/build.log`. Check
  that the group id carries the repository name (`...MohamedSalah-13.smart-report-plugin`) and that the
  version has its `v`.
- **A `?` on the page.** A character no font has. The log names it (`No PDF font has a glyph for U+....`).
  Use a font that covers it, as under "Variants".
- **Text in the wrong order.** Almost always text that was shaped or reversed before it reached the library,
  or cells written in visual order. Give it logical text and logical columns.
- **A table too wide.** Too many columns for an upright page: use `paper.landscape()`, or fewer columns.
  Draw the page and look - whether it reads well depends on what the columns hold.
- **Nothing happens on print.** The printer name is not one of `PdfPrinter.printerNames()`, or the call threw
  on a background thread that nobody listens to. Catch `ReportException` there and show it.
- **The preview opens empty or says the document could not be prepared.** The function given to
  `PdfPreview.of` threw. It runs off the JavaFX thread; the log has the stack trace.
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
