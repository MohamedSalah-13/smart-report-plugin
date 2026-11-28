package com.smart.report.util;

import java.util.Optional;

/**
 * كود مستند يتقري بالماسح: الفاتورة المطبوعة تتلاقى بمسح ورقتها (لمرتجع، أو في قائمة). الكود أرقام بس، بطول ثابت،
 * وفي آخره خانة تحقق:
 *
 * <pre>
 *   2   1   0000000042   7
 *   │   │   │            └ خانة التحقق (GS1 modulo 10، زي EAN-13)
 *   │   │   └ رقم المستند بطول ثابت
 *   │   └ نوع المستند: رقم من 0 لـ 9 البرنامج بيحدده (1 بيع، 2 مرتجع...)
 *   └ رقم ثابت في أول كل كود
 * </pre>
 *
 * <pre>{@code
 * DocumentCode codes = DocumentCode.DEFAULT;          // 13 رقم: 2، النوع، 10 للرقم، خانة التحقق
 * String code = codes.encode(1, 42);                  // "2100000000425"
 * document.barcode(code);
 *
 * codes.parse(scannedText)                            // Optional[Scanned[type=1, number=42]]
 *         .ifPresent(found -> openInvoice(found.type(), found.number()));
 * }</pre>
 *
 * <p><b>أرقام بس:</b> الماسح بيكتب اللي بيقراه كضغطات زرار، ومع لوحة مفاتيح عربية الحرف اللاتيني بيوصل حرف عربي؛
 * الأرقام بتوصل زي ما هي. و{@link #parse} بيقبل الأرقام العربية ({@code ٠١٢}) والفارسية كمان، والمسافات في الأول
 * والآخر (زي Enter بعد المسح).</p>
 *
 * <p><b>من غير تخزين:</b> الكود بيتحسب من النوع والرقم، فإعادة الطباعة بتطلع نفس الكود، والمستندات القديمة ما
 * تحتاجش ترحيل.</p>
 *
 * <p><b>رقم اتكتب باليد مش كود:</b> {@link #parse} بيرجع فاضي للطول الغلط، أو أول رقم غلط، أو خانة تحقق غلط (رقم
 * اتغيّر أو رقمين اتبدلوا جنب بعض)، فخانة البحث تقدر تاخد رقم الفاتورة أو الكود الممسوح.</p>
 *
 * <p>{@link #DEFAULT} بيبدأ بـ 2 وطوله 13: ده كود EAN-13 صحيح في النطاق 20–29 اللي GS1 سايباه للاستخدام جوّه المحل،
 * فما بيتلخبطش مع باركود منتج حقيقي. الصنف immutable وآمن بين الـ threads.</p>
 */
public final class DocumentCode {

    /** أول رقم 2، و 10 خانات للرقم: 13 رقم في الكود كله. */
    public static final DocumentCode DEFAULT = of(2, 10);

    /** نوع المستند ورقمه، من كود اتمسح. */
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
     * شكل كود: أول رقم ثابت، وعدد خانات رقم المستند. طول الكود = {@code numberDigits + 3}.
     *
     * @param lead         من 1 لـ 9 (الصفر في الأول بيضيع لو الكود اتفتح في Excel)
     * @param numberDigits من 1 لـ 15
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

    /** عدد الأرقام في الكود كله. */
    public int length() {
        return numberDigits + 3;
    }

    /**
     * الكود لنوع ورقم.
     *
     * @param type   من 0 لـ 9
     * @param number من 0 لأقل من {@code 10^numberDigits}
     * @throws IllegalArgumentException لو النوع أو الرقم برّه المدى
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
     * نوع المستند ورقمه من كود اتمسح أو اتكتب؛ فاضي لو النص مش كود بالشكل ده (طول، أو أول رقم، أو حرف مش رقم، أو
     * خانة تحقق غلط). الأرقام العربية والفارسية مقبولة، والمسافات في الأول والآخر بتتشال.
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
     * خانة التحقق GS1 modulo 10 (EAN-13 و UPC و GTIN): من آخر رقم لأوله الأوزان 3 ثم 1 بالتبادل، والخانة هي اللي
     * بتكمّل المجموع لمضاعف 10.
     *
     * @param digits أرقام لاتينية بس، من غير خانة التحقق
     * @throws IllegalArgumentException لو فاضي أو فيه حرف مش رقم
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

    /** رقم لاتيني أو عربي ({@code ٠..٩}) أو فارسي ({@code ۰..۹})؛ -1 لأي حرف تاني. */
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
