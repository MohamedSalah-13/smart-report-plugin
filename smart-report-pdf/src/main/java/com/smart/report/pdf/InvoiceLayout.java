package com.smart.report.pdf;

/** The layout of an invoice page ({@link InvoicePdf#of}). On a thermal roll, the invoice is always a receipt. */
public enum InvoiceLayout {
    /** Economy: no fills at all, lines only, for black-and-white laser printers and to save toner. */
    ECONOMY,
    /** Modern: a coloured table header and alternating rows, without a grid. */
    MODERN,
    /** Detailed: a full grid, the line number, and the item code and unit if present. */
    DETAILED
}
