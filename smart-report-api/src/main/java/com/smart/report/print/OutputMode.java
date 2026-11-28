package com.smart.report.print;

/** How the document comes out when the user presses «طباعة» ("Print") on the program's screen. */
public enum OutputMode {

    /** Straight to the printer, with no window: for cashiers and receipts. */
    PRINT,
    /** The preview window first; the user prints from it. */
    PREVIEW,
    /** A PDF file, with no preview window. */
    SAVE_PDF,
    /** Asks the user every time: print, preview, or save. */
    ASK
}
