package com.smart.report.pdf;

/**
 * Text alignment inside its cell, relative to the document direction rather than left and right:
 * {@link #START} is the right side in an Arabic document and the left side in an English one, so the
 * same definition works in both directions.
 */
public enum Align {
    /** The side where the line starts. */
    START,
    CENTER,
    /** The side where the line ends. Use it for a column of numbers so the numbers line up. */
    END
}
