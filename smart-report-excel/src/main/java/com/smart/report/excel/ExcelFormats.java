package com.smart.report.excel;

/**
 * Converts a column format pattern (in Java syntax, as {@code ColumnDefinition.withFormat} takes it) to Excel
 * syntax, so the column is formatted the same way in Excel as in the PDF.
 *
 * <ul>
 *     <li><b>Numbers</b> ({@link java.text.DecimalFormat}): {@code # 0 , . % ;} stay as they are, the exponent
 *         {@code E0} becomes {@code E+0}, and any other letters or text (a currency, for example) go in quotes.</li>
 *     <li><b>Dates</b> ({@link java.time.format.DateTimeFormatter}): {@code yyyy MM dd HH mm ss EEEE a}
 *         becomes {@code yyyy mm dd hh mm ss dddd AM/PM}. Excel tells month from minute ({@code mm}) by position:
 *         after the hour or before the seconds it means minutes; anywhere else it means the month.</li>
 * </ul>
 *
 * <p>A letter that has no Excel equivalent (the time zone {@code z}, for example) throws
 * {@link IllegalArgumentException}, so the report names the column instead of showing a wrong date.</p>
 */
final class ExcelFormats {

    private ExcelFormats() {
    }

    /** Converts a Java number pattern to Excel syntax. */
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
     * Literal text in a number pattern always goes in quotes: in Excel the comma, the period and zero have a
     * meaning even outside their usual place (a trailing comma divides the number by a thousand), so text that is
     * quoted in Java must stay quoted.
     */
    private static void quote(StringBuilder literal, StringBuilder out) {
        if (!literal.isEmpty()) {
            out.append('"').append(literal.toString().replace("\"", "\\\"")).append('"');
            literal.setLength(0);
        }
    }

    /**
     * Converts a Java date pattern to Excel syntax.
     *
     * <p>Excel shows the hour on a 24-hour clock unless the format has AM/PM; then every hour in it uses a
     * 12-hour clock. So {@code hh} without {@code a}, and {@code HH} with {@code a}, would show a different hour
     * from the one Java prints ({@code 13:05} becomes {@code 01:05}), and {@code k}/{@code K} (the hour from 1 to
     * 24, or from 0 to 11) have no equivalent. All of them are rejected, instead of the sheet showing a different
     * time.</p>
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

    /** Rejects hour patterns that Excel would show on another clock than the one Java prints (see {@link #date}). */
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

    /** Reads text in single quotes in a Java pattern ({@code ''} = a quote mark); returns the index after it. */
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
     * Puts literal text in double quotes, except separators and spaces, which Excel shows as they are without
     * quotes (so {@code dd/mm/yyyy} stays readable).
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
