package com.smart.report.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
