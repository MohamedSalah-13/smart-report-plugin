package com.smart.report.excel;

/**
 * نمط تنسيق عمود (بصيغة Java، زي ما {@code ColumnDefinition.withFormat} بياخده) بصيغة Excel، عشان
 * العمود يتنسّق في الـ Excel زي الـ PDF.
 *
 * <ul>
 *     <li><b>الأرقام</b> ({@link java.text.DecimalFormat}): {@code # 0 , . % ;} زي ما هي، والأس
 *         {@code E0} ← {@code E+0}، وأي حروف أو كلام تاني (العملة مثلًا) بين علامتي تنصيص.</li>
 *     <li><b>التواريخ</b> ({@link java.time.format.DateTimeFormatter}): {@code yyyy MM dd HH mm ss EEEE a}
 *         ← {@code yyyy mm dd hh mm ss dddd AM/PM}. Excel بيفرّق الشهر من الدقيقة ({@code mm}) بمكانها:
 *         بعد الساعة أو قبل الثواني دقايق، وغير كده شهر.</li>
 * </ul>
 *
 * <p>الحرف اللي مالوش مقابل في Excel (المنطقة الزمنية {@code z} مثلًا) بيرمي
 * {@link IllegalArgumentException}، فالتقرير يقول أنهي عمود بدل ما يطلع تاريخ غلط.</p>
 */
final class ExcelFormats {

    private ExcelFormats() {
    }

    /** نمط أرقام Java بصيغة Excel. */
    static String number(String pattern) {
        StringBuilder out = new StringBuilder(pattern.length() + 8);
        StringBuilder literal = new StringBuilder();
        int i = 0;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            if (c == '\'') {
                i = quoted(pattern, i, literal);
                continue;
            }
            if ("#0,.%;-".indexOf(c) >= 0) {
                quote(literal, out);
                out.append(c);
            } else if (c == 'E') {
                quote(literal, out);
                out.append("E+");
            } else if (c == '‰' || c == '¤') {
                throw new IllegalArgumentException("The number pattern \"" + pattern + "\" uses '" + c
                        + "', which Excel has no format for.");
            } else {
                literal.append(c);
            }
            i++;
        }
        quote(literal, out);
        return out.toString();
    }

    /**
     * الكلام الثابت في نمط أرقام بين علامتي تنصيص دايمًا: الفاصلة والنقطة والصفر في Excel ليهم معنى
     * حتى برّه مكانهم — الفاصلة في الآخر بتقسم الرقم على ألف — فالمكتوب بين علامتي تنصيص في Java
     * لازم يفضل كده.
     */
    private static void quote(StringBuilder literal, StringBuilder out) {
        if (!literal.isEmpty()) {
            out.append('"').append(literal.toString().replace("\"", "\\\"")).append('"');
            literal.setLength(0);
        }
    }

    /**
     * نمط تواريخ Java بصيغة Excel.
     *
     * <p>Excel بيعرض الساعة بنظام 24 ساعة إلا لو الصيغة فيها AM/PM، وساعتها كل ساعة فيها بنظام 12.
     * فـ {@code hh} من غير {@code a} و{@code HH} مع {@code a} كانوا هيطلعوا ساعة غير اللي Java بتطبعها
     * ({@code 13:05} ← {@code 01:05})، و{@code k}/{@code K} (الساعة من 1 لـ 24 أو من 0 لـ 11) مالهمش
     * مقابل؛ كلهم بيترفضوا بدل ما الورقة تعرض وقت تاني.</p>
     */
    static String date(String pattern) {
        clockMatches(pattern);
        StringBuilder out = new StringBuilder(pattern.length() + 8);
        StringBuilder literal = new StringBuilder();
        int i = 0;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            if (c == '\'') {
                i = quoted(pattern, i, literal);
                continue;
            }
            if (!Character.isLetter(c)) {
                literal.append(c);
                i++;
                continue;
            }
            int run = 1;
            while (i + run < pattern.length() && pattern.charAt(i + run) == c) {
                run++;
            }
            flush(literal, out);
            out.append(switch (c) {
                case 'y', 'u' -> run == 2 ? "yy" : "yyyy";
                case 'M', 'L' -> "m".repeat(Math.min(run, 5));
                case 'd' -> run >= 2 ? "dd" : "d";
                case 'E' -> run >= 4 ? "dddd" : "ddd";
                case 'H', 'h' -> run >= 2 ? "hh" : "h";
                case 'm' -> run >= 2 ? "mm" : "m";
                case 's' -> run >= 2 ? "ss" : "s";
                case 'S' -> "0".repeat(Math.min(run, 3));
                case 'a' -> "AM/PM";
                default -> throw new IllegalArgumentException("The date pattern \"" + pattern + "\" uses '" + c
                        + "', which Excel has no format for.");
            });
            i += run;
        }
        flush(literal, out);
        return out.toString();
    }

    /** يرفض أنماط الساعة اللي Excel هيعرضها بنظام غير اللي Java بتطبع بيه (شوف {@link #date}). */
    private static void clockMatches(String pattern) {
        boolean twelve = false;
        boolean twentyFour = false;
        boolean marker = false;
        boolean quotedText = false;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '\'') {
                quotedText = !quotedText;
                continue;
            }
            if (quotedText) {
                continue;
            }
            switch (c) {
                case 'k', 'K' -> throw new IllegalArgumentException("The date pattern \"" + pattern + "\" uses '" + c
                        + "' (an hour counted from " + (c == 'k' ? "1 to 24" : "0 to 11")
                        + "), which Excel has no format for. Use HH, or hh with a.");
                case 'h' -> twelve = true;
                case 'H' -> twentyFour = true;
                case 'a' -> marker = true;
                default -> {
                }
            }
        }
        if (twelve && !marker) {
            throw new IllegalArgumentException("The date pattern \"" + pattern + "\" uses hh without a: Excel shows"
                    + " hours from 0 to 23 unless the format has AM/PM. Add a, or use HH.");
        }
        if (twentyFour && marker) {
            throw new IllegalArgumentException("The date pattern \"" + pattern + "\" uses HH with a: Excel shows"
                    + " hours from 1 to 12 once the format has AM/PM. Use hh with a, or drop a.");
        }
    }

    /** نص بين علامتي تنصيص مفردة في نمط Java ({@code ''} = علامة تنصيص)؛ بيرجّع مكان اللي بعده. */
    private static int quoted(String pattern, int start, StringBuilder literal) {
        if (start + 1 < pattern.length() && pattern.charAt(start + 1) == '\'') {
            literal.append('\'');
            return start + 2;
        }
        int i = start + 1;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            if (c == '\'') {
                if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '\'') {
                    literal.append('\'');
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            literal.append(c);
            i++;
        }
        throw new IllegalArgumentException("The pattern \"" + pattern + "\" has a quote that is not closed.");
    }

    /**
     * الكلام الثابت بين علامتي تنصيص مزدوجة، إلا الفواصل والمسافات اللي Excel بيعرضها زي ما هي
     * من غير تنصيص (عشان {@code dd/mm/yyyy} تفضل مقروءة).
     */
    private static void flush(StringBuilder literal, StringBuilder out) {
        if (literal.isEmpty()) {
            return;
        }
        String text = literal.toString();
        if (text.chars().allMatch(c -> " /:-.,()".indexOf(c) >= 0)) {
            out.append(text);
        } else {
            out.append('"').append(text.replace("\"", "\\\"")).append('"');
        }
        literal.setLength(0);
    }
}
