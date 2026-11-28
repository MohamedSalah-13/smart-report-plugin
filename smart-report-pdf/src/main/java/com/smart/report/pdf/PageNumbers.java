package com.smart.report.pdf;

/** When the page number is printed at the bottom of the page ("صفحة 1 من 3" / "Page 1 of 3"). */
public enum PageNumbers {
    /** No page numbers. */
    NONE,
    /** The default: printed only when the document has more than one page. */
    WHEN_SEVERAL,
    /** Always printed, even on a single page. */
    ALWAYS
}
