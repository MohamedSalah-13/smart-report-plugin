package com.smart.report.pdf;

/** متى يُكتب رقم الصفحة في أسفلها ("صفحة 1 من 3" / "Page 1 of 3"). */
public enum PageNumbers {
    /** لا ترقيم. */
    NONE,
    /** الافتراضي: يُكتب فقط لو المستند أكتر من صفحة. */
    WHEN_SEVERAL,
    /** يُكتب دائمًا، حتى على الصفحة الواحدة. */
    ALWAYS
}
