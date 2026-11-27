package com.smart.report.util;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Objects;

/**
 * نص رمز QR للفاتورة الضريبية المبسطة في السعودية (الفوترة الإلكترونية، المرحلة الأولى، هيئة الزكاة
 * والضريبة والجمارك). الرمز بيشيل خمس قيم بترميز TLV، وبعدها Base64:
 *
 * <ol>
 *     <li>اسم البائع</li>
 *     <li>الرقم الضريبي للبائع</li>
 *     <li>وقت إصدار الفاتورة (ISO 8601 بتوقيت UTC: {@code 2022-04-25T15:30:00Z})</li>
 *     <li>إجمالي الفاتورة شامل الضريبة</li>
 *     <li>إجمالي ضريبة القيمة المضافة</li>
 * </ol>
 *
 * <p>كل قيمة: رقمها في بايت، وطولها في بايت، وبعدين بايتاتها UTF-8. الطول بعدد البايتات مش الحروف:
 * الحرف العربي بايتين، فـ«شركة الهدى» طولها 19 مش 10.</p>
 *
 * <pre>{@code
 * String qr = ZatcaQr.payload("شركة الهدى", "310122393500003",
 *         LocalDateTime.of(2026, 11, 27, 18, 30).atZone(ZoneId.of("Asia/Riyadh")).toInstant(),
 *         new BigDecimal("1150.00"), new BigDecimal("150.00"));
 * document.qrCode(qr);   // smart-report-pdf
 * }</pre>
 *
 * <p>الصنف بلا حالة وآمن بين الـ threads، وما بيحتاجش أي مكتبة.</p>
 */
public final class ZatcaQr {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_INSTANT;
    /** أقصى طول لقيمة: الطول بايت واحد. */
    private static final int MAX_VALUE_BYTES = 255;

    private ZatcaQr() {}

    /**
     * @param sellerName   اسم البائع كما في السجل
     * @param vatNumber    الرقم الضريبي: 15 رقمًا أوله وآخره 3
     * @param timestamp    وقت إصدار الفاتورة؛ بيتكتب بتوقيت UTC لأقرب ثانية
     * @param totalWithVat إجمالي الفاتورة شامل الضريبة؛ بيتكتب بخانتين عشريتين ({@code 1150.00})
     * @param vatTotal     إجمالي الضريبة؛ بخانتين عشريتين
     * @return نص الرمز: Base64 لترميز الـ TLV
     * @throws IllegalArgumentException لو الاسم فاضي أو أطول من 255 بايت، أو الرقم الضريبي مش 15 رقمًا
     *                                  أوله وآخره 3، أو مبلغ سالب
     */
    public static String payload(String sellerName, String vatNumber, Instant timestamp,
                                 BigDecimal totalWithVat, BigDecimal vatTotal) {
        Objects.requireNonNull(sellerName, "sellerName");
        Objects.requireNonNull(vatNumber, "vatNumber");
        Objects.requireNonNull(timestamp, "timestamp");
        if (sellerName.isBlank()) {
            throw new IllegalArgumentException("The seller name is required.");
        }
        if (!vatNumber.matches("3\\d{13}3")) {
            throw new IllegalArgumentException(
                    "A VAT registration number is 15 digits that begin and end with 3: " + vatNumber);
        }
        ByteArrayOutputStream tlv = new ByteArrayOutputStream();
        field(tlv, 1, sellerName);
        field(tlv, 2, vatNumber);
        field(tlv, 3, TIMESTAMP.format(timestamp.truncatedTo(ChronoUnit.SECONDS)));
        field(tlv, 4, amount(totalWithVat, "totalWithVat"));
        field(tlv, 5, amount(vatTotal, "vatTotal"));
        return Base64.getEncoder().encodeToString(tlv.toByteArray());
    }

    private static String amount(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        if (value.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative: " + value);
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static void field(ByteArrayOutputStream tlv, int tag, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_VALUE_BYTES) {
            throw new IllegalArgumentException("Field " + tag + " is " + bytes.length
                    + " bytes in UTF-8; a ZATCA QR field holds at most " + MAX_VALUE_BYTES + ".");
        }
        tlv.write(tag);
        tlv.write(bytes.length);
        tlv.writeBytes(bytes);
    }
}
