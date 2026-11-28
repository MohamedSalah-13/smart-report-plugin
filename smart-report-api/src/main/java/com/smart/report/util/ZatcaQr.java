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
 * The QR code text for a Saudi simplified tax invoice (e-invoicing, Zakat, Tax and Customs Authority, ZATCA).
 * The code holds TLV-encoded values, then Base64. Phase 1 ({@link #payload}) has five values:
 *
 * <ol>
 *     <li>The seller name</li>
 *     <li>The seller's VAT registration number</li>
 *     <li>The invoice issue time (ISO 8601 in UTC: {@code 2022-04-25T15:30:00Z})</li>
 *     <li>The invoice total including VAT</li>
 *     <li>The VAT total</li>
 * </ol>
 *
 * <p>Each value is: its tag in one byte, its length in one byte, then its UTF-8 bytes. The length counts
 * bytes, not characters: an Arabic letter is two bytes, so «شركة الهدى» has length 19, not 10.</p>
 *
 * <pre>{@code
 * String qr = ZatcaQr.payload("شركة الهدى", "310122393500003",
 *         LocalDateTime.of(2026, 11, 27, 18, 30).atZone(ZoneId.of("Asia/Riyadh")).toInstant(),
 *         new BigDecimal("1150.00"), new BigDecimal("150.00"));
 * document.qrCode(qr);   // smart-report-pdf
 * }</pre>
 *
 * <p><b>Phase 2 (integration)</b> ({@link #phase2}) adds four values from the invoice signature: the hash,
 * the signature, the public key, and ZATCA's signature on the certificate. This class signs nothing: these
 * values come from the signing software or from the ZATCA SDK. Details are in {@link Phase2}.</p>
 *
 * <p>Sources: «E-invoicing Detailed Technical Guidelines» version 2 (November 2022) section 6, and «Security
 * Features Implementation Standards» version 1.2 (2023-05-19) section 4.1, both from the ZATCA site
 * zatca.gov.sa.</p>
 *
 * <p>The static methods are stateless and thread-safe, and the class needs no library.</p>
 */
public final class ZatcaQr {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_INSTANT;
    /** The maximum length of a value: the length is one byte. */
    private static final int MAX_VALUE_BYTES = 255;
    /** Maximum length of the code text: «up to 700 characters» (Security Features Implementation Standards, 4.1). */
    static final int MAX_PAYLOAD_CHARS = 700;
    /** The SHA-256 invoice hash. */
    private static final int HASH_BYTES = 32;

    private ZatcaQr() {}

    /**
     * The phase 1 code: the five values.
     *
     * @param sellerName   the seller name as registered
     * @param vatNumber    the VAT registration number: 15 digits that begin and end with 3
     * @param timestamp    the invoice issue time; written in UTC to the nearest second
     * @param totalWithVat the invoice total including VAT; written with two decimal places ({@code 1150.00})
     * @param vatTotal     the VAT total; with two decimal places
     * @return the code text: Base64 of the TLV encoding
     * @throws IllegalArgumentException if the name is blank or longer than 255 bytes, the VAT number is not 15
     *                                  digits that begin and end with 3, or an amount is negative
     */
    public static String payload(String sellerName, String vatNumber, Instant timestamp,
                                 BigDecimal totalWithVat, BigDecimal vatTotal) {
        return Base64.getEncoder().encodeToString(
                invoiceFields(sellerName, vatNumber, timestamp, totalWithVat, vatTotal).toByteArray());
    }

    /**
     * The phase 2 code for a simplified tax invoice (or a credit or debit note for one): the same five values,
     * followed by the signature values from {@link Phase2}.
     *
     * <pre>{@code
     * String qr = ZatcaQr.phase2("شركة الهدى", "310122393500003", issued,
     *                 new BigDecimal("1150.00"), new BigDecimal("150.00"))
     *         .invoiceHash(digestValue)          // ds:DigestValue from the signed invoice
     *         .signature(signatureValue)         // ds:SignatureValue
     *         .certificate(certificate)          // the device certificate (CSID): public key and ZATCA signature
     *         .payload();
     * }</pre>
     *
     * <p>These values must match the signed XML invoice: the name, the VAT number, the two amounts and the issue
     * time ({@code cbc:IssueDate} and {@code cbc:IssueTime}).</p>
     *
     * @throws IllegalArgumentException under the same conditions as {@link #payload}
     */
    public static Phase2 phase2(String sellerName, String vatNumber, Instant timestamp,
                                BigDecimal totalWithVat, BigDecimal vatTotal) {
        return new Phase2(invoiceFields(sellerName, vatNumber, timestamp, totalWithVat, vatTotal).toByteArray());
    }

    /**
     * The signature values added to the phase 2 code, in this order and in the form used in the ZATCA guide's
     * example (E-invoicing Detailed Technical Guidelines, section 6.2):
     *
     * <table>
     *     <caption>Values 6 to 9</caption>
     *     <tr><th>Tag</th><th>Value</th><th>Written as</th><th>Where it is in the invoice</th></tr>
     *     <tr><td>6</td><td>Invoice hash (SHA-256)</td><td>The Base64 text as is (44 characters)</td>
     *         <td>{@code ds:SignedInfo/ds:Reference/ds:DigestValue}</td></tr>
     *     <tr><td>7</td><td>ECDSA signature on the hash</td><td>The Base64 text as is</td>
     *         <td>{@code ds:SignatureValue}</td></tr>
     *     <tr><td>8</td><td>Public key</td><td>DER bytes (SubjectPublicKeyInfo)</td>
     *         <td>From {@code ds:X509Certificate}</td></tr>
     *     <tr><td>9</td><td>ZATCA's signature on the stamp certificate</td><td>DER bytes of the signature</td>
     *         <td>From {@code ds:X509Certificate}</td></tr>
     * </table>
     *
     * <p>The Security Features standard (version 1.2, section 4.1) describes value 6 as the 32 bytes of the hash,
     * but the worked example in the guide (in hex and in Base64) writes the 44-character Base64 text, and that is
     * what we do.</p>
     *
     * <p>Value 9 applies only to the simplified invoice and its notes, so all four values are required here. A
     * standard tax invoice (B2B) is stamped by ZATCA, which returns its code in the cleared invoice (clearance),
     * so the program prints that code as is.</p>
     *
     * <p>The resulting text is at most 700 characters («up to 700 characters», Security Features standard 4.1);
     * a very long seller name is what usually goes over it. Fill this object from a single thread.</p>
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
         * Value 6: the invoice hash as in {@code ds:DigestValue}, the Base64 text of a SHA-256 digest (44 characters).
         *
         * @throws IllegalArgumentException if it is not Base64 of 32 bytes (for example, the hash in hex)
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
         * Value 7: the ECDSA signature as in {@code ds:SignatureValue}, as Base64 text.
         *
         * @throws IllegalArgumentException if it is not Base64 of a DER signature
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
         * Value 8: the DER-encoded public key (SubjectPublicKeyInfo, as returned by
         * {@code PublicKey.getEncoded()}; 88 bytes for a secp256k1 key).
         *
         * @throws IllegalArgumentException if the bytes are not DER
         */
        public Phase2 publicKey(byte[] subjectPublicKeyInfo) {
            this.publicKey = der(subjectPublicKeyInfo, "public key");
            return this;
        }

        /**
         * Value 9: the issuer's (ZATCA's) signature on the stamp certificate, as DER bytes as returned by
         * {@link X509Certificate#getSignature()}.
         *
         * @throws IllegalArgumentException if the bytes are not DER
         */
        public Phase2 certificateSignature(byte[] signature) {
            this.certificateSignature = der(signature, "certificate signature");
            return this;
        }

        /**
         * Values 8 and 9 from the stamp certificate (CSID) that signed the invoice, which is the same one as in
         * {@code ds:X509Certificate}.
         */
        public Phase2 certificate(X509Certificate certificate) {
            Objects.requireNonNull(certificate, "certificate");
            return publicKey(certificate.getPublicKey().getEncoded())
                    .certificateSignature(certificate.getSignature());
        }

        /**
         * @return the code text: Base64 of TLV values 1 to 9
         * @throws IllegalStateException    if one of the four values is missing
         * @throws IllegalArgumentException if a value is longer than 255 bytes or the text is longer than 700
         *                                  characters
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

    /** DER bytes of exactly one SEQUENCE with its full length: both the key and the signature look like this. */
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
