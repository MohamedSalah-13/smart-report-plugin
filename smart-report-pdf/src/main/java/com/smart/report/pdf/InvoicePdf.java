package com.smart.report.pdf;

import java.util.Objects;

/**
 * Draws a finished {@link Invoice}: a page in one of the {@link InvoiceLayout} layouts on A4 or A5, or a receipt
 * on an 80 / 58 mm roll.
 *
 * <pre>{@code
 * PdfDocument page = InvoicePdf.of(invoice, Paper.A5, InvoiceLayout.MODERN);
 * PdfDocument receipt = InvoicePdf.of(invoice, Paper.ROLL_80, InvoiceLayout.MODERN);   // on a roll: a receipt
 * }</pre>
 *
 * <p>Returns a plain {@link PdfDocument}, so the program can add to it (a watermark, {@code ReportLook}) before
 * {@code toBytes()}. A long table splits across pages and its header repeats, and a long name wraps onto two lines
 * without its font getting smaller.</p>
 */
public final class InvoicePdf {

    private InvoicePdf() {
    }

    /** The invoice on the paper: a page in this layout, or a receipt if the paper is a roll. */
    public static PdfDocument of(Invoice invoice, Paper paper, InvoiceLayout layout) {
        Objects.requireNonNull(invoice, "invoice");
        Objects.requireNonNull(paper, "paper");
        Objects.requireNonNull(layout, "layout");
        return paper.roll() ? receipt(invoice, paper) : page(invoice, paper, layout);
    }

    private static PdfDocument start(Invoice invoice, Paper paper) {
        PdfDocument document = invoice.rightToLeft() ? PdfDocument.rtl(paper) : PdfDocument.ltr(paper);
        if (!invoice.letterheadName().isBlank() || !invoice.letterheadLines().isEmpty()) {
            document.letterhead(invoice.letterheadName(), invoice.letterheadLines().toArray(String[]::new));
        }
        if (invoice.logo() != null) {
            document.logo(invoice.logo());
        }
        String title = (invoice.title() + " " + invoice.number()).strip();
        if (!title.isEmpty()) {
            document.title(title);
        }
        if (!invoice.date().isBlank()) {
            document.field(invoice.rightToLeft() ? "التاريخ" : "Date", invoice.date());
        }
        for (String[] field : invoice.fields()) {
            document.field(field[0], field[1]);
        }
        return document;
    }

    private static void finish(Invoice invoice, PdfDocument document, boolean roll) {
        for (String[] total : invoice.totals()) {
            document.summary(total[0], total[1]);
        }
        if (invoice.grandTotal() != null) {
            document.summaryTotal(invoice.grandTotal()[0], invoice.grandTotal()[1]);
        }
        if (!invoice.amountInWords().isBlank()) {
            document.text(invoice.amountInWords());
        }
        if (!invoice.notes().isBlank()) {
            document.text(invoice.notes());
        }
        if (!invoice.qr().isBlank()) {
            document.qrCode(invoice.qr(), roll ? 30 : 28, roll ? Align.CENTER : Align.START);
        }
        if (!invoice.code().isBlank()) {
            document.barcode(invoice.code(), roll ? 10 : 12, roll ? Align.CENTER : Align.END);
        }
        if (!invoice.shopNote().isBlank()) {
            // على الصفحة جملة المحل في أسفل كل صفحة، فما تنزلش لوحدها في صفحة؛ وعلى اللفة في آخر الإيصال.
            if (roll) {
                document.footer(invoice.shopNote());
            } else {
                document.pageFooter(invoice.shopNote());
            }
        }
    }

    private static PdfDocument page(Invoice invoice, Paper paper, InvoiceLayout layout) {
        PdfDocument document = start(invoice, paper);
        PdfStyle base = PdfStyle.forPaper(paper);
        document.style(switch (layout) {
            case ECONOMY -> base.inkSaver().withGrid(false);
            case MODERN -> base.withColorSet(ColorSet.NAVY).withStripes(true).withGrid(false);
            case DETAILED -> base.withGrid(true);
        });
        boolean detailed = layout == InvoiceLayout.DETAILED;
        boolean codes = detailed && invoice.lines().stream().anyMatch(line -> !line.code().isBlank());
        boolean units = invoice.lines().stream().anyMatch(line -> !line.unit().isBlank());
        String[] h = invoice.headers();
        PdfTable table = PdfTable.create();
        if (detailed) {
            table.column(h[6], 0.6f, Align.CENTER);
        }
        if (codes) {
            table.column(h[4], 1.4f);
        }
        table.column(h[0], 4f);
        if (units) {
            table.column(h[5], 1f, Align.CENTER);
        }
        table.column(h[1], 1f, Align.END).column(h[2], 1.4f, Align.END).column(h[3], 1.6f, Align.END);
        int number = 0;
        for (InvoiceLine line : invoice.lines()) {
            number++;
            java.util.List<String> cells = new java.util.ArrayList<>(7);
            if (detailed) {
                cells.add(String.valueOf(number));
            }
            if (codes) {
                cells.add(line.code());
            }
            cells.add(line.name());
            if (units) {
                cells.add(line.unit());
            }
            cells.add(line.quantity());
            cells.add(line.price());
            cells.add(line.total());
            table.row(cells.toArray(String[]::new));
        }
        document.table(table);
        finish(invoice, document, false);
        return document;
    }

    /**
     * The receipt on a roll: the item name on a line of its own, about the width of the roll (it wraps and does
     * not shrink), with quantity × price and the line total under it. The two lines never separate.
     */
    private static PdfDocument receipt(Invoice invoice, Paper paper) {
        PdfDocument document = start(invoice, paper);
        document.style(PdfStyle.forPaper(paper).withGrid(false).withStripes(false));
        String[] h = invoice.headers();
        PdfTable table = PdfTable.create().column(h[0], 3f).column(h[3], 1.5f, Align.END);
        for (InvoiceLine line : invoice.lines()) {
            table.rowJoinedToNext(line.name(), "");
            String unit = line.unit().isBlank() ? "" : " " + line.unit();
            String detail = line.quantity().isBlank() && line.price().isBlank() ? ""
                    : line.quantity() + unit + " × " + line.price();
            table.row(detail, line.total());
        }
        document.table(table);
        finish(invoice, document, true);
        return document;
    }
}
