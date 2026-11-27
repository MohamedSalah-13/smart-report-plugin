package com.smart.report.pdf;

/** المكان اللي جزء بيتأكد منه قبل ما يبدأ: لو ما كفّاهوش الباقي الجزء بيبدأ في صفحة جديدة. */
record Keep(float height, boolean onlyIfAPageHoldsIt) {

    static final Keep NONE = new Keep(0, false);

    /** صفحة جديدة لو {@code height} ما يكفيهاش الباقي: عنوان القسم مع رأس الجدول وصف بعده. */
    static Keep room(float height) {
        return new Keep(height, false);
    }

    /**
     * الجزء كله في صفحة واحدة لو صفحة جديدة تكفيه (الملخص)؛ الأطول من صفحة بيبدأ من مكانه وبيكمل على
     * اللي بعدها.
     */
    static Keep whole(float height) {
        return new Keep(height, true);
    }
}
