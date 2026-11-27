package com.smart.report.pdf;

/**
 * اللي الأجزاء بتتقاس عليه: عرض السطر ومكانه، الخط والشكل، الاتجاه، والمكان اللي صفحة جديدة بتديه للمحتوى
 * ({@code freshRoom}) — الرمز الأطول منه بيصغر، ورأس الجدول اللي ياخد أكتر من تلته ما بيتكررش.
 */
record Frame(Typesetter text, PdfStyle style, boolean rtl, float left, float right, float width, float freshRoom) {

    /** الجهة الفعلية على الورقة لمحاذاة منسوبة لاتجاه المستند. */
    Canvas.Side side(Align align) {
        return switch (align) {
            case CENTER -> Canvas.Side.CENTER;
            case START -> rtl ? Canvas.Side.RIGHT : Canvas.Side.LEFT;
            case END -> rtl ? Canvas.Side.LEFT : Canvas.Side.RIGHT;
        };
    }
}
