package com.smart.report.pdf;

/**
 * اللي الأجزاء بتتقاس عليه: عرض السطر ومكانه، الخط والشكل، الاتجاه، والمكان اللي صفحة جديدة بتديه للمحتوى
 * ({@code freshRoom}) — الرمز الأطول منه بيصغر، ورأس الجدول اللي ياخد أكتر من تلته ما بيتكررش. ومعاهم طول
 * الورقة ودقة الطابعة ({@link PdfDocument#printerDpi}، صفر = من غير)، عشان مربعات الرموز تقع على نقط الطابعة.
 */
record Frame(Typesetter text, PdfStyle style, boolean rtl, float left, float right, float width, float freshRoom,
             float pageHeight, int dpi, float totalsSize) {

    /** نقطة الطابعة بالـ point (1/72 بوصة)؛ صفر لو الدقة ما اتحددتش. */
    float dot() {
        return dpi > 0 ? 72f / dpi : 0f;
    }

    /** الجهة الفعلية على الورقة لمحاذاة منسوبة لاتجاه المستند. */
    Canvas.Side side(Align align) {
        return switch (align) {
            case CENTER -> Canvas.Side.CENTER;
            case START -> rtl ? Canvas.Side.RIGHT : Canvas.Side.LEFT;
            case END -> rtl ? Canvas.Side.LEFT : Canvas.Side.RIGHT;
        };
    }
}
