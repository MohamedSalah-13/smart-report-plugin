package com.smart.report.util;

import com.smart.report.ColumnDefinition;
import com.smart.report.ReportRequest;

/**
 * اتجاه التقرير العام: من اليمين لليسار لو العنوان أو أي رأس عمود فيه عربي، إلا لو البرنامج حدّده
 * صراحة. القاعدة واحدة لكل الأنواع، فنفس الطلب بيطلع PDF و Excel في نفس الاتجاه.
 */
public final class TextDirection {

    private TextDirection() {}

    /** هل النص فيه أي حرف من كتل العربية (بما فيها صور العرض)؟ {@code null} = لا. */
    public static boolean containsArabic(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= '؀' && c <= 'ۿ')        // Arabic
                    || (c >= 'ݐ' && c <= 'ݿ')  // Arabic Supplement
                    || (c >= 'ﭐ' && c <= '﷿')  // Presentation Forms-A
                    || (c >= 'ﹰ' && c <= '﻿')) { // Presentation Forms-B
                return true;
            }
        }
        return false;
    }

    /**
     * اتجاه الطلب: {@code Boolean} تحت المفتاح {@code parameter} في {@code request.parameters()} لو
     * موجود، وإلا من اليمين لليسار لو العنوان أو أي رأس عمود فيه عربي.
     */
    public static boolean rightToLeft(ReportRequest<?> request, String parameter) {
        if (parameter != null && request.parameters().get(parameter) instanceof Boolean chosen) {
            return chosen;
        }
        if (containsArabic(request.title())) {
            return true;
        }
        for (ColumnDefinition column : request.columns()) {
            if (containsArabic(column.header())) {
                return true;
            }
        }
        return false;
    }
}
