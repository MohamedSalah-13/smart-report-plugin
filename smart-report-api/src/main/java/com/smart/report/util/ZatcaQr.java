package com.smart.report.util;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Objects;

/**
 * نص رمز QR للفاتورة الضريبية المبسطة في السعودية (الفوترة الإلكترونية، هيئة الزكاة والضريبة والجمارك).
 * الرمز بيشيل قيمًا بترميز TLV، وبعدها Base64. المرحلة الأولى ({@link #payload}) خمس قيم:
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
 * <p><b>المرحلة الثانية (الربط والتكامل)</b> ({@link #phase2}) بتزوّد أربع قيم من توقيع الفاتورة: البصمة،
 * والتوقيع، والمفتاح العام، وتوقيع الهيئة على الشهادة. الصنف ما بيوقّعش حاجة: القيم دي بتيجي من برنامج
 * التوقيع أو من SDK الهيئة. التفاصيل في {@link Phase2}.</p>
 *
 * <p>المصادر: «E-invoicing Detailed Technical Guidelines» الإصدار 2 (نوفمبر 2022) القسم 6، و«Security Features
 * Implementation Standards» الإصدار 1.2 (2023-05-19) القسم 4.1، الاتنين من موقع الهيئة zatca.gov.sa.</p>
 *
 * <p>الدوال الثابتة بلا حالة وآمنة بين الـ threads، والصنف ما بيحتاجش أي مكتبة.</p>
 */
public final class ZatcaQr {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_INSTANT;
    /** أقصى طول لقيمة: الطول بايت واحد. */
    private static final int MAX_VALUE_BYTES = 255;
    /** أقصى طول لنص الرمز: «up to 700 characters» (Security Features Implementation Standards، 4.1). */
    static final int MAX_PAYLOAD_CHARS = 700;
    /** بصمة الفاتورة SHA-256. */
    private static final int HASH_BYTES = 32;

    private ZatcaQr() {}

    /**
     * رمز المرحلة الأولى: القيم الخمس.
     *
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
        return Base64.getEncoder().encodeToString(
                invoiceFields(sellerName, vatNumber, timestamp, totalWithVat, vatTotal).toByteArray());
    }

    /**
     * رمز المرحلة الثانية لفاتورة ضريبية مبسطة (أو إشعار دائن أو مدين تابع لها): نفس القيم الخمس، وبعدها
     * قيم التوقيع من {@link Phase2}.
     *
     * <pre>{@code
     * String qr = ZatcaQr.phase2("شركة الهدى", "310122393500003", issued,
     *                 new BigDecimal("1150.00"), new BigDecimal("150.00"))
     *         .invoiceHash(digestValue)          // ds:DigestValue من الفاتورة الموقّعة
     *         .signature(signatureValue)         // ds:SignatureValue
     *         .certificate(certificate)          // شهادة الجهاز (CSID): المفتاح العام وتوقيع الهيئة
     *         .payload();
     * }</pre>
     *
     * <p>القيم دي لازم تطابق الفاتورة الـ XML الموقّعة: الاسم والرقم الضريبي والمبلغين ووقت الإصدار
     * ({@code cbc:IssueDate} و{@code cbc:IssueTime}).</p>
     *
     * @throws IllegalArgumentException بنفس شروط {@link #payload}
     */
    public static Phase2 phase2(String sellerName, String vatNumber, Instant timestamp,
                                BigDecimal totalWithVat, BigDecimal vatTotal) {
        return new Phase2(invoiceFields(sellerName, vatNumber, timestamp, totalWithVat, vatTotal).toByteArray());
    }

    /**
     * قيم التوقيع اللي بتتزوّد على رمز المرحلة الثانية، بالترتيب ده وبالشكل اللي في مثال دليل الهيئة
     * (E-invoicing Detailed Technical Guidelines، القسم 6.2):
     *
     * <table>
     *     <caption>القيم من 6 لـ 9</caption>
     *     <tr><th>رقم</th><th>القيمة</th><th>بتتكتب</th><th>مكانها في الفاتورة</th></tr>
     *     <tr><td>6</td><td>بصمة الفاتورة (SHA-256)</td><td>نص Base64 كما هو (44 حرف)</td>
     *         <td>{@code ds:SignedInfo/ds:Reference/ds:DigestValue}</td></tr>
     *     <tr><td>7</td><td>توقيع ECDSA على البصمة</td><td>نص Base64 كما هو</td>
     *         <td>{@code ds:SignatureValue}</td></tr>
     *     <tr><td>8</td><td>المفتاح العام</td><td>بايتات DER (SubjectPublicKeyInfo)</td>
     *         <td>من {@code ds:X509Certificate}</td></tr>
     *     <tr><td>9</td><td>توقيع الهيئة على شهادة الختم</td><td>بايتات DER للتوقيع</td>
     *         <td>من {@code ds:X509Certificate}</td></tr>
     * </table>
     *
     * <p>معيار ملامح الأمان (الإصدار 1.2، القسم 4.1) بيوصف القيمة 6 بأنها الـ 32 بايت بتوع البصمة، لكن المثال
     * المحلول في الدليل (بالـ hex وبالـ Base64) بيكتب نص الـ Base64 بطول 44، وده اللي بنعمله.</p>
     *
     * <p>القيمة 9 للفاتورة المبسطة وإشعاراتها بس، فكل القيم الأربع مطلوبة هنا. الفاتورة الضريبية (B2B) الهيئة
     * هي اللي بتختمها وبترجّع رمزها في الفاتورة المعتمدة (clearance)، فالبرنامج بيطبع الرمز ده كما هو.</p>
     *
     * <p>النص الناتج بحد أقصى 700 حرف («up to 700 characters»، معيار ملامح الأمان 4.1)؛ الاسم الطويل جدًا هو
     * اللي بيعدّيه عادةً. الكائن ده بيتملا من thread واحد.</p>
     */
    public static final class Phase2 {

        private final byte[] invoiceFields;
        private String invoiceHash;
        private String signature;
        private byte[] publicKey;
        private byte[] certificateSignature;

        private Phase2(byte[] invoiceFields) {
            this.invoiceFields = invoiceFields;
        }

        /**
         * القيمة 6: بصمة الفاتورة كما في {@code ds:DigestValue}، نص Base64 لـ SHA-256 (44 حرف).
         *
         * @throws IllegalArgumentException لو مش Base64 لـ 32 بايت (البصمة بالـ hex مثلًا)
         */
        public Phase2 invoiceHash(String digestValue) {
            Objects.requireNonNull(digestValue, "digestValue");
            byte[] hash = base64(digestValue, "invoice hash");
            if (hash.length != HASH_BYTES) {
                throw new IllegalArgumentException("The invoice hash is the Base64 text of a 32-byte SHA-256 digest"
                        + " (ds:DigestValue, 44 characters); this one is " + hash.length + " bytes: " + digestValue);
            }
            this.invoiceHash = digestValue;
            return this;
        }

        /**
         * القيمة 7: توقيع ECDSA كما في {@code ds:SignatureValue}، نص Base64.
         *
         * @throws IllegalArgumentException لو مش Base64 لتوقيع DER
         */
        public Phase2 signature(String signatureValue) {
            Objects.requireNonNull(signatureValue, "signatureValue");
            if (!derSequence(base64(signatureValue, "signature"))) {
                throw new IllegalArgumentException("The signature is the Base64 text of a DER-encoded ECDSA signature"
                        + " (ds:SignatureValue): " + signatureValue);
            }
            this.signature = signatureValue;
            return this;
        }

        /**
         * القيمة 8: المفتاح العام بترميز DER (SubjectPublicKeyInfo، اللي بيرجّعه
         * {@code PublicKey.getEncoded()}؛ 88 بايت لمفتاح secp256k1).
         *
         * @throws IllegalArgumentException لو البايتات مش DER
         */
        public Phase2 publicKey(byte[] subjectPublicKeyInfo) {
            this.publicKey = der(subjectPublicKeyInfo, "public key");
            return this;
        }

        /**
         * القيمة 9: توقيع الجهة المُصدِرة (الهيئة) على شهادة الختم، بايتات DER زي ما بيرجّعها
         * {@link X509Certificate#getSignature()}.
         *
         * @throws IllegalArgumentException لو البايتات مش DER
         */
        public Phase2 certificateSignature(byte[] signature) {
            this.certificateSignature = der(signature, "certificate signature");
            return this;
        }

        /**
         * القيمتين 8 و9 من شهادة الختم (CSID) اللي اتوقّعت بيها الفاتورة، وهي نفسها اللي في
         * {@code ds:X509Certificate}.
         */
        public Phase2 certificate(X509Certificate certificate) {
            Objects.requireNonNull(certificate, "certificate");
            return publicKey(certificate.getPublicKey().getEncoded())
                    .certificateSignature(certificate.getSignature());
        }

        /**
         * @return نص الرمز: Base64 لقيم الـ TLV من 1 لـ 9
         * @throws IllegalStateException    لو قيمة من الأربعة ناقصة
         * @throws IllegalArgumentException لو قيمة أطول من 255 بايت أو النص أطول من 700 حرف
         */
        public String payload() {
            require(invoiceHash, "invoiceHash");
            require(signature, "signature");
            require(publicKey, "publicKey (or certificate)");
            require(certificateSignature, "certificateSignature (or certificate)");
            ByteArrayOutputStream tlv = new ByteArrayOutputStream();
            tlv.writeBytes(invoiceFields);
            field(tlv, 6, invoiceHash.getBytes(StandardCharsets.US_ASCII));
            field(tlv, 7, signature.getBytes(StandardCharsets.US_ASCII));
            field(tlv, 8, publicKey);
            field(tlv, 9, certificateSignature);
            String payload = Base64.getEncoder().encodeToString(tlv.toByteArray());
            if (payload.length() > MAX_PAYLOAD_CHARS) {
                throw new IllegalArgumentException("The QR text is " + payload.length() + " characters; ZATCA allows at"
                        + " most " + MAX_PAYLOAD_CHARS + ". The seller name is usually what to shorten.");
            }
            return payload;
        }

        private static void require(Object value, String name) {
            if (value == null) {
                throw new IllegalStateException("A phase 2 QR code needs the " + name + ".");
            }
        }
    }

    private static ByteArrayOutputStream invoiceFields(String sellerName, String vatNumber, Instant timestamp,
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
        return tlv;
    }

    private static String amount(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        if (value.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative: " + value);
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static void field(ByteArrayOutputStream tlv, int tag, String value) {
        field(tlv, tag, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void field(ByteArrayOutputStream tlv, int tag, byte[] bytes) {
        if (bytes.length > MAX_VALUE_BYTES) {
            throw new IllegalArgumentException("Field " + tag + " is " + bytes.length
                    + " bytes; a ZATCA QR field holds at most " + MAX_VALUE_BYTES + ".");
        }
        tlv.write(tag);
        tlv.write(bytes.length);
        tlv.writeBytes(bytes);
    }

    private static byte[] base64(String text, String name) {
        try {
            return Base64.getDecoder().decode(text);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The " + name + " is not Base64 text: " + text, e);
        }
    }

    private static byte[] der(byte[] bytes, String name) {
        Objects.requireNonNull(bytes, name);
        if (!derSequence(bytes)) {
            throw new IllegalArgumentException("The " + name + " must be DER-encoded bytes (an ASN.1 SEQUENCE).");
        }
        return bytes.clone();
    }

    /** بايتات DER لـ SEQUENCE واحد بطوله بالظبط: المفتاح والتوقيع الاتنين كده. */
    private static boolean derSequence(byte[] der) {
        if (der.length < 2 || der[0] != 0x30) {
            return false;
        }
        int length = der[1] & 0xFF;
        int header = 2;
        if (length == 0x80 || length > 0x82) {
            return false;
        }
        if (length > 0x80) {
            int count = length & 0x7F;
            if (der.length < 2 + count) {
                return false;
            }
            length = 0;
            for (int i = 0; i < count; i++) {
                length = length << 8 | der[2 + i] & 0xFF;
            }
            header += count;
        }
        return header + length == der.length;
    }
}
