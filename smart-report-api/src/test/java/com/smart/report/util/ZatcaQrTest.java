package com.smart.report.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZatcaQrTest {

    private static final Instant TIME = Instant.parse("2022-04-25T15:30:00Z");

    /** قيمة واحدة من الـ TLV: رقمها، وطولها المكتوب في الرمز، وبايتاتها. */
    private record Field(int tag, int length, byte[] value) {
        String text() {
            return new String(value, StandardCharsets.UTF_8);
        }
    }

    /** بيفك الـ TLV بنفسه، من غير الكود اللي بيبنيه. */
    private static List<Field> fields(String payload) {
        byte[] bytes = Base64.getDecoder().decode(payload);
        List<Field> fields = new ArrayList<>();
        int i = 0;
        while (i < bytes.length) {
            int tag = bytes[i] & 0xFF;
            int length = bytes[i + 1] & 0xFF;
            byte[] value = new byte[length];
            System.arraycopy(bytes, i + 2, value, 0, length);
            fields.add(new Field(tag, length, value));
            i += 2 + length;
        }
        assertEquals(bytes.length, i, "مفيش بايتات زيادة بعد آخر قيمة");
        return fields;
    }

    /** عكس {@link #fields}: القيم دي متشفّرة TLV وبعدها Base64. */
    private static String encode(List<Field> fields) {
        ByteArrayOutputStream tlv = new ByteArrayOutputStream();
        for (Field field : fields) {
            tlv.write(field.tag());
            tlv.write(field.length());
            tlv.writeBytes(field.value());
        }
        return Base64.getEncoder().encodeToString(tlv.toByteArray());
    }

    /*
     * المثال المحلول للمرحلة الثانية في دليل الهيئة: «E-invoicing Detailed Technical Guidelines» الإصدار 2
     * (نوفمبر 2022)، القسم 6.2 «Creation of TLV QR code»، منشور بالـ hex وبالـ Base64 (الاتنين بنفس البايتات).
     * https://zatca.gov.sa/en/E-Invoicing/Introduction/Guidelines/Documents/E-invoicing-Detailed-Technical-Guideline.pdf
     */
    private static final String GUIDELINE_PHASE2 = "ARdBaG1lZCBNb2hhbWVkIEFMIEFobWFkeQIPMzAxMTIxOTcxNTAwMDAzAxQyMDIy"
            + "LTAzLTEzVDE0OjQwOjQwWgQHMTEwOC45MAUFMTQ0LjkGLFFuVkVleFc0bld2NENhRTM5YS82NkpwL09YTy9ldkhROHBEbEc3d2VxLzQ9"
            + "B2BNRVVDSVFENXp4eVhPQjdOdldmNjJyVkVaQVlVNzFqcHk5SEVFblowcTlPOTZ3ckw2UUlnUUp6Q0dIYnc2WUJITFlWZE8xd25VaEJn"
            + "S204ak1UeXZjazlNK3JQOXhZWT0IWDBWMBAGByqGSM49AgEGBSuBBAAKA0IABGGDDKDmhWAITDv7LXqLX2cmr6+qddUkpcLCvWs5rC2O"
            + "29W/hS4ajAK4Qdnahym6MaijX75Cg3j4aao7ouYXJ9EJSDBGAiEA7mHT6yg85jtQGWp3M7tPT7Jk2+zsvVHGs3bU5Z7YE68CIQD60ebQ"
            + "amYjYvdebnFjNfx4X4dop7LsEBFCNSsLY0IFaQ==";
    private static final String GUIDELINE_HASH = "QnVEexW4nWv4CaE39a/66Jp/OXO/evHQ8pDlG7weq/4=";
    private static final String GUIDELINE_SIGNATURE = "MEUCIQD5zxyXOB7NvWf62rVEZAYU71jpy9HEEnZ0q9O96wrL6QIgQJzCGHbw6Y"
            + "BHLYVdO1wnUhBgKm8jMTyvck9M+rP9xYY=";
    private static final byte[] GUIDELINE_PUBLIC_KEY = HexFormat.of().parseHex(
            "3056301006072a8648ce3d020106052b8104000a0342000461830ca0e68560084c3bfb2d7a8b5f6726afafaa75d524a5c2c2"
                    + "bd6b39ac2d8edbd5bf852e1a8c02b841d9da8729ba31a8a35fbe428378f869aa3ba2e61727d1");
    private static final byte[] GUIDELINE_CERTIFICATE_SIGNATURE = HexFormat.of().parseHex(
            "3046022100ee61d3eb283ce63b50196a7733bb4f4fb264dbececbd51c6b376d4e59ed813af022100fad1e6d06a662362f75e"
                    + "6e716335fc785f8768a7b2ec101142352b0b63420569");

    private static ZatcaQr.Phase2 guidelineInvoice(String sellerName) {
        return ZatcaQr.phase2(sellerName, "301121971500003", Instant.parse("2022-03-13T14:40:40Z"),
                new BigDecimal("1108.90"), new BigDecimal("144.90"));
    }

    private static ZatcaQr.Phase2 guidelineStamp(ZatcaQr.Phase2 invoice) {
        return invoice.invoiceHash(GUIDELINE_HASH)
                .signature(GUIDELINE_SIGNATURE)
                .publicKey(GUIDELINE_PUBLIC_KEY)
                .certificateSignature(GUIDELINE_CERTIFICATE_SIGNATURE);
    }

    /**
     * شهادة تجريبية ذاتية التوقيع بمفتاح secp256k1 زي شهادات الهيئة (openssl، من غير المفتاح الخاص). المفتاح
     * العام وتوقيع الشهادة تحتها طالعين من openssl نفسه ({@code pkey -pubin -outform DER} و{@code asn1parse})،
     * مش من Java.
     */
    private static final String TEST_CERTIFICATE = """
            -----BEGIN CERTIFICATE-----
            MIIBzDCCAXKgAwIBAgIUI8skgbXMEcuP5S5/4KHKIYrz1nswCgYIKoZIzj0EAwIw
            PDEeMBwGA1UEAwwVc21hcnQtcmVwb3J0IHRlc3QgRUdTMQ0wCwYDVQQKDARUZXN0
            MQswCQYDVQQGEwJTQTAgFw0yNjExMjcwODEzMjNaGA8yMTI2MTEwMzA4MTMyM1ow
            PDEeMBwGA1UEAwwVc21hcnQtcmVwb3J0IHRlc3QgRUdTMQ0wCwYDVQQKDARUZXN0
            MQswCQYDVQQGEwJTQTBWMBAGByqGSM49AgEGBSuBBAAKA0IABNUsLEJrZ+8PA8WN
            Jfl2pgmd1naIgzxn2EetXG7ET4hl2aztqrMBDKq1R02lP33ss/FiKKxyHJfjgOJ9
            MEYxDeCjUzBRMB0GA1UdDgQWBBRrnZuNjciL5Q4uUYmPMlH+g3TFtDAfBgNVHSME
            GDAWgBRrnZuNjciL5Q4uUYmPMlH+g3TFtDAPBgNVHRMBAf8EBTADAQH/MAoGCCqG
            SM49BAMCA0gAMEUCIGx1oejj//aSABe0mO9BgUQ0VkJeSDGkGcCfaJKqv1h4AiEA
            uK/wWeUOLc02CnqnVdXIBnbKk8ePRLRgkZ3rLszr5T8=
            -----END CERTIFICATE-----
            """;
    private static final byte[] TEST_CERTIFICATE_PUBLIC_KEY = HexFormat.of().parseHex(
            "3056301006072a8648ce3d020106052b8104000a03420004d52c2c426b67ef0f03c58d25f976a6099dd67688833c67d847ad"
                    + "5c6ec44f8865d9acedaab3010caab5474da53f7decb3f16228ac721c97e380e27d3046310de0");
    private static final byte[] TEST_CERTIFICATE_SIGNATURE = HexFormat.of().parseHex(
            "304502206c75a1e8e3fff6920017b498ef4181443456425e4831a419c09f6892aabf5878022100b8aff059e50e2dcd360a7a"
                    + "a755d5c80676ca93c78f44b460919deb2eccebe53f");

    /** المثال المنشور («Bobs Records») اللي بتتقارن بيه تطبيقات رمز الهيئة، ونصه Base64 المعروف. */
    @Test
    void matchesThePublishedExample() {
        String payload = ZatcaQr.payload("Bobs Records", "310122393500003", TIME,
                new BigDecimal("1000.00"), new BigDecimal("150.00"));

        assertEquals("AQxCb2JzIFJlY29yZHMCDzMxMDEyMjM5MzUwMDAwMwMUMjAyMi0wNC0yNVQxNTozMDowMFoEBzEwMDAuMDAFBjE1MC4wMA==",
                payload);
        List<Field> fields = fields(payload);
        assertEquals(5, fields.size());
        String[] expected = {"Bobs Records", "310122393500003", "2022-04-25T15:30:00Z", "1000.00", "150.00"};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(i + 1, fields.get(i).tag());
            assertEquals(expected[i].length(), fields.get(i).length());
            assertEquals(expected[i], fields.get(i).text());
        }
    }

    /** طول الاسم العربي بالبايت: الحرف بايتين في UTF-8، فـ«شركة الهدى» (10 حروف) طولها 19. */
    @Test
    void arabicSellerNameLengthIsItsUtf8ByteCount() {
        String name = "شركة الهدى";
        Field seller = fields(ZatcaQr.payload(name, "310122393500003", TIME,
                new BigDecimal("1150"), new BigDecimal("150"))).get(0);

        assertEquals(1, seller.tag());
        assertEquals(10, name.length());
        assertEquals(19, seller.length());
        assertEquals(name, seller.text());
    }

    /** الوقت بتوقيت UTC لأقرب ثانية، والمبالغ بخانتين عشريتين. */
    @Test
    void timeIsWrittenInUtcAndAmountsWithTwoDecimals() {
        Instant riyadh = LocalDateTime.of(2026, 11, 27, 18, 30, 15, 900_000_000)
                .atZone(ZoneId.of("Asia/Riyadh")).toInstant();
        List<Field> fields = fields(ZatcaQr.payload("Shop", "300000000000003", riyadh,
                new BigDecimal("1150"), new BigDecimal("149.995")));

        assertEquals("2026-11-27T15:30:15Z", fields.get(2).text());
        assertEquals("1150.00", fields.get(3).text());
        assertEquals("150.00", fields.get(4).text());
    }

    @Test
    void rejectsWhatWouldMakeAnInvalidCode() {
        BigDecimal ten = BigDecimal.TEN;
        assertThrows(IllegalArgumentException.class, () -> ZatcaQr.payload(" ", "310122393500003", TIME, ten, ten));
        assertThrows(IllegalArgumentException.class, () -> ZatcaQr.payload("Shop", "31012239350000", TIME, ten, ten));
        assertThrows(IllegalArgumentException.class, () -> ZatcaQr.payload("Shop", "310122393500001", TIME, ten, ten));
        assertThrows(IllegalArgumentException.class, () -> ZatcaQr.payload("Shop", "3101223935000a3", TIME, ten, ten));
        assertThrows(IllegalArgumentException.class,
                () -> ZatcaQr.payload("Shop", "310122393500003", TIME, ten.negate(), ten));
        // 128 حرف عربي = 256 بايت: أطول من اللي يكتبه بايت الطول.
        assertThrows(IllegalArgumentException.class,
                () -> ZatcaQr.payload("ش".repeat(128), "310122393500003", TIME, ten, ten));
        assertEquals(255, fields(ZatcaQr.payload("ش".repeat(127) + "x", "310122393500003", TIME, ten, ten))
                .get(0).length());
        assertThrows(NullPointerException.class, () -> ZatcaQr.payload("Shop", "310122393500003", null, ten, ten));
    }

    /**
     * نفس المثال المحلول في الدليل: كل القيم بنفس البايتات، ما عدا الضريبة (5) اللي المثال كاتبها «144.9»
     * وإحنا بنكتبها بخانتين زي ما هي في الفاتورة الـ XML («144.90»).
     */
    @Test
    void phase2MatchesTheGuidelinesWorkedExample() {
        String payload = guidelineStamp(guidelineInvoice("Ahmed Mohamed AL Ahmady")).payload();

        List<Field> published = fields(GUIDELINE_PHASE2);
        List<Field> ours = fields(payload);
        assertEquals(9, ours.size());
        assertEquals("144.9", published.get(4).text());
        assertEquals("144.90", ours.get(4).text());
        List<Field> expected = new ArrayList<>(published);
        expected.set(4, ours.get(4));
        assertEquals(encode(expected), payload);

        // 6 و7 نص Base64، و8 و9 بايتات DER، بالأطوال اللي في الدليل.
        int[] lengths = {44, 96, 88, 72};
        for (int i = 0; i < lengths.length; i++) {
            assertEquals(6 + i, ours.get(5 + i).tag());
            assertEquals(lengths[i], ours.get(5 + i).length());
        }
        assertEquals(GUIDELINE_HASH, ours.get(5).text());
        assertEquals(GUIDELINE_SIGNATURE, ours.get(6).text());
    }

    /** الحقول من 1 لـ 5 في المرحلة الثانية هي هي رمز المرحلة الأولى. */
    @Test
    void phase2StartsWithThePhase1Fields() {
        String name = "شركة الهدى";
        List<Field> phase1 = fields(ZatcaQr.payload(name, "301121971500003", Instant.parse("2022-03-13T14:40:40Z"),
                new BigDecimal("1108.90"), new BigDecimal("144.90")));
        List<Field> phase2 = fields(guidelineStamp(guidelineInvoice(name)).payload());

        assertEquals(5, phase1.size());
        for (int i = 0; i < phase1.size(); i++) {
            assertEquals(phase1.get(i).tag(), phase2.get(i).tag());
            assertArrayEquals(phase1.get(i).value(), phase2.get(i).value());
        }
    }

    /** المفتاح العام (8) وتوقيع الشهادة (9) من الشهادة نفسها، بنفس البايتات اللي بيطلّعها openssl. */
    @Test
    void certificateGivesThePublicKeyAndItsSignature() throws Exception {
        X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(TEST_CERTIFICATE.getBytes(StandardCharsets.US_ASCII)));
        List<Field> fields = fields(guidelineInvoice("Shop")
                .invoiceHash(GUIDELINE_HASH)
                .signature(GUIDELINE_SIGNATURE)
                .certificate(certificate)
                .payload());

        assertEquals(8, fields.get(7).tag());
        assertArrayEquals(TEST_CERTIFICATE_PUBLIC_KEY, fields.get(7).value());
        assertEquals(9, fields.get(8).tag());
        assertArrayEquals(TEST_CERTIFICATE_SIGNATURE, fields.get(8).value());
    }

    /** النص كله 700 حرف بالكتير: بقية القيم هنا 366 بايت، فالاسم لحد 159 بايت يدّي 525 بايت = 700 حرف Base64. */
    @Test
    void phase2TextIsAtMost700Characters() {
        assertEquals(700, guidelineStamp(guidelineInvoice("x".repeat(159))).payload().length());
        ZatcaQr.Phase2 tooLong = guidelineStamp(guidelineInvoice("x".repeat(160)));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, tooLong::payload);
        assertTrue(e.getMessage().contains("704"), e.getMessage());
    }

    @Test
    void phase2RejectsValuesInTheWrongForm() {
        ZatcaQr.Phase2 invoice = guidelineInvoice("Shop");
        // البصمة بالـ hex (64 حرف) بدل Base64، أو مش Base64 خالص، أو مش 32 بايت.
        String hexHash = HexFormat.of().formatHex(Base64.getDecoder().decode(GUIDELINE_HASH));
        assertThrows(IllegalArgumentException.class, () -> invoice.invoiceHash(hexHash));
        assertThrows(IllegalArgumentException.class, () -> invoice.invoiceHash("not Base64!"));
        assertThrows(IllegalArgumentException.class,
                () -> invoice.invoiceHash(Base64.getEncoder().encodeToString(new byte[20])));
        assertThrows(NullPointerException.class, () -> invoice.invoiceHash(null));
        // التوقيع لازم Base64 لـ DER، مش hex ولا نص.
        assertThrows(IllegalArgumentException.class,
                () -> invoice.signature(HexFormat.of().formatHex(GUIDELINE_CERTIFICATE_SIGNATURE)));
        assertThrows(IllegalArgumentException.class, () -> invoice.signature(
                Base64.getEncoder().encodeToString("signature".getBytes(StandardCharsets.US_ASCII))));
        // المفتاح وتوقيع الشهادة بايتات DER كاملة.
        assertThrows(IllegalArgumentException.class,
                () -> invoice.publicKey(GUIDELINE_HASH.getBytes(StandardCharsets.US_ASCII)));
        byte[] truncated = new byte[GUIDELINE_PUBLIC_KEY.length - 1];
        System.arraycopy(GUIDELINE_PUBLIC_KEY, 0, truncated, 0, truncated.length);
        assertThrows(IllegalArgumentException.class, () -> invoice.publicKey(truncated));
        assertThrows(IllegalArgumentException.class, () -> invoice.certificateSignature(new byte[0]));
        // نفس شروط المرحلة الأولى على أول خمس قيم.
        assertThrows(IllegalArgumentException.class,
                () -> ZatcaQr.phase2("Shop", "310122393500001", TIME, BigDecimal.TEN, BigDecimal.ONE));
    }

    /** القيم الأربع كلها مطلوبة: ناقصة واحدة يبقى الرمز ناقص. */
    @Test
    void phase2NeedsAllFourSignatureValues() {
        assertThrows(IllegalStateException.class, () -> guidelineInvoice("Shop").payload());
        assertThrows(IllegalStateException.class, () -> guidelineInvoice("Shop")
                .invoiceHash(GUIDELINE_HASH).signature(GUIDELINE_SIGNATURE).publicKey(GUIDELINE_PUBLIC_KEY)
                .payload());
    }

    /** البايتات بتتنسخ: تغيير المصفوفة بعد ما اتدّت ما بيغيّرش الرمز. */
    @Test
    void phase2CopiesTheBytesItIsGiven() {
        byte[] key = GUIDELINE_PUBLIC_KEY.clone();
        ZatcaQr.Phase2 invoice = guidelineStamp(guidelineInvoice("Shop")).publicKey(key);
        key[key.length - 1] ^= 1;
        assertArrayEquals(GUIDELINE_PUBLIC_KEY, fields(invoice.payload()).get(7).value());
    }
}
