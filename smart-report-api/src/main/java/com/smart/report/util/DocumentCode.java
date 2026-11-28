package com.smart.report.util;

import java.util.Optional;

/**
 * A document code that a scanner can read: a printed invoice is found by scanning its paper (for a return, or in a
 * list). The code is digits only, has a fixed length, and ends with a check digit:
 *
 * <pre>
 *   2   1   0000000042   7
 *   │   │   │            └ check digit (GS1 modulo 10, like EAN-13)
 *   │   │   └ document number, fixed length
 *   │   └ document type: a digit from 0 to 9 that the program sets (1 sale, 2 return...)
 *   └ a fixed digit at the start of every code
 * </pre>
 *
 * <pre>{@code
 * DocumentCode codes = DocumentCode.DEFAULT;          // 13 digits: 2, the type, 10 for the number, check digit
 * String code = codes.encode(1, 42);                  // "2100000000425"
 * document.barcode(code);
 *
 * codes.parse(scannedText)                            // Optional[Scanned[type=1, number=42]]
 *         .ifPresent(found -> openInvoice(found.type(), found.number()));
 * }</pre>
 *
 * <p><b>Digits only:</b> a scanner types what it reads as key presses, and with an Arabic keyboard layout a Latin
 * letter arrives as an Arabic letter; digits arrive unchanged. {@link #parse} also accepts Arabic-Indic digits
 * ({@code ٠١٢}) and Persian digits, and leading and trailing whitespace (such as the Enter after a scan).</p>
 *
 * <p><b>Nothing to store:</b> the code is computed from the type and the number, so a reprint gives the same code,
 * and old documents need no migration.</p>
 *
 * <p><b>A number typed by hand is not a code:</b> {@link #parse} returns empty for a wrong length, a wrong first
 * digit, or a wrong check digit (a changed digit or two adjacent digits swapped), so a search field can take either
 * the invoice number or the scanned code.</p>
 *
 * <p>{@link #DEFAULT} starts with 2 and is 13 digits long: this is a valid EAN-13 code in the range 20–29 that GS1
 * reserves for in-store use, so it does not clash with a real product barcode. The class is immutable and
 * thread-safe.</p>
 */
public final class DocumentCode {

    /** First digit 2, and 10 digits for the number: 13 digits in the whole code. */
    public static final DocumentCode DEFAULT = of(2, 10);

    /** The document type and number, from a scanned code. */
    public record Scanned(int type, long number) {}

    private final int lead;
    private final int numberDigits;
    private final long limit;

    private DocumentCode(int lead, int numberDigits) {
        this.lead = lead;
        this.numberDigits = numberDigits;
        this.limit = (long) Math.pow(10, numberDigits);
    }

    /**
     * A code layout: a fixed first digit, and the number of digits in the document number. The code length is
     * {@code numberDigits + 3}.
     *
     * @param lead         from 1 to 9 (a leading zero is lost if the code is opened in Excel)
     * @param numberDigits from 1 to 15
     */
    public static DocumentCode of(int lead, int numberDigits) {
        if (lead < 1 || lead > 9) {
            throw new IllegalArgumentException("The first digit must be between 1 and 9: " + lead);
        }
        if (numberDigits < 1 || numberDigits > 15) {
            throw new IllegalArgumentException("The number must have between 1 and 15 digits: " + numberDigits);
        }
        return new DocumentCode(lead, numberDigits);
    }

    /** The number of digits in the whole code. */
    public int length() {
        return numberDigits + 3;
    }

    /**
     * The code for a type and a number.
     *
     * @param type   from 0 to 9
     * @param number from 0 to below {@code 10^numberDigits}
     * @throws IllegalArgumentException if the type or the number is out of range
     */
    public String encode(int type, long number) {
        if (type < 0 || type > 9) {
            throw new IllegalArgumentException("The document type must be a single digit, 0 to 9: " + type);
        }
        if (number < 0 || number >= limit) {
            throw new IllegalArgumentException("The document number must be between 0 and " + (limit - 1)
                    + " for a code of " + length() + " digits: " + number);
        }
        StringBuilder code = new StringBuilder(length());
        code.append(lead).append(type);
        String digits = Long.toString(number);
        code.append("0".repeat(numberDigits - digits.length())).append(digits);
        return code.append(checkDigit(code)).toString();
    }

    /**
     * The document type and number from a scanned or typed code; empty if the text is not a code of this layout
     * (wrong length, wrong first digit, a character that is not a digit, or a wrong check digit). Arabic-Indic and
     * Persian digits are accepted, and leading and trailing whitespace is removed.
     */
    public Optional<Scanned> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String code = text.strip();
        if (code.length() != length()) {
            return Optional.empty();
        }
        int[] digits = new int[code.length()];
        for (int i = 0; i < digits.length; i++) {
            digits[i] = digit(code.charAt(i));
            if (digits[i] < 0) {
                return Optional.empty();
            }
        }
        if (digits[0] != lead) {
            return Optional.empty();
        }
        StringBuilder data = new StringBuilder(digits.length - 1);
        for (int i = 0; i < digits.length - 1; i++) {
            data.append(digits[i]);
        }
        if (checkDigit(data) != digits[digits.length - 1]) {
            return Optional.empty();
        }
        return Optional.of(new Scanned(digits[1], Long.parseLong(data.substring(2))));
    }

    /**
     * The GS1 modulo 10 check digit (EAN-13, UPC and GTIN): from the last digit to the first, the weights alternate
     * 3 then 1, and the check digit is the one that brings the sum up to a multiple of 10.
     *
     * @param digits Latin digits only, without the check digit
     * @throws IllegalArgumentException if empty or if it contains a character that is not a digit
     */
    public static int checkDigit(CharSequence digits) {
        if (digits == null || digits.length() == 0) {
            throw new IllegalArgumentException("Digits are required.");
        }
        int sum = 0;
        for (int i = digits.length() - 1, weight = 3; i >= 0; i--, weight = 4 - weight) {
            char c = digits.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("Only the digits 0 to 9 are allowed: \"" + digits + "\"");
            }
            sum += (c - '0') * weight;
        }
        return (10 - sum % 10) % 10;
    }

    /** A Latin, Arabic-Indic ({@code ٠..٩}) or Persian ({@code ۰..۹}) digit; -1 for any other character. */
    private static int digit(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= '٠' && c <= '٩') {
            return c - '٠';
        }
        if (c >= '۰' && c <= '۹') {
            return c - '۰';
        }
        return -1;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DocumentCode code && code.lead == lead && code.numberDigits == numberDigits;
    }

    @Override
    public int hashCode() {
        return 31 * lead + numberDigits;
    }

    @Override
    public String toString() {
        return "DocumentCode[lead=" + lead + ", numberDigits=" + numberDigits + "]";
    }
}
