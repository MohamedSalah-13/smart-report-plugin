package com.smart.report;

/**
 * Alignment of a column's values in the generic report. It is relative to the report's direction, not to
 * left and right: {@link #START} is the right side in an Arabic report and the left side in an English one.
 */
public enum Alignment {
    /** The side where a line starts. */
    START,
    CENTER,
    /** The side where a line ends. Numbers line up under each other by their last digit. */
    END
}
