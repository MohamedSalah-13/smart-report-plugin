package com.smart.report.util;

import com.smart.report.util.DocumentCode.Scanned;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentCodeTest {

    private static final DocumentCode CODES = DocumentCode.DEFAULT;

    /** المثال اللي في الـ Javadoc والـ README. */
    @Test
    void theDocumentedExample() {
        assertEquals("2100000000425", CODES.encode(1, 42));
        assertEquals(Optional.of(new Scanned(1, 42)), CODES.parse("2100000000425"));
        assertEquals(13, CODES.length());
    }

    /** خانة التحقق هي نفسها بتاعة EAN-13: أكواد منتجات حقيقية معروفة. */
    @ParameterizedTest
    @ValueSource(strings = {"4006381333931", "5901234123457", "9780201379624", "0012345678905"})
    void checkDigitIsTheEan13One(String ean) {
        assertEquals(ean.charAt(12) - '0', DocumentCode.checkDigit(ean.substring(0, 12)));
    }

    /** كل نوع، وأرقام من الصفر لأكبر رقم: الكود بيرجع نفس النوع والرقم. */
    @ParameterizedTest
    @CsvSource({"0, 0", "1, 1", "2, 42", "3, 1751", "5, 123456789", "9, 9999999999"})
    void roundTrip(int type, long number) {
        String code = CODES.encode(type, number);
        assertEquals(13, code.length());
        assertTrue(code.chars().allMatch(Character::isDigit), code);
        assertEquals('2', code.charAt(0));
        assertEquals(Optional.of(new Scanned(type, number)), CODES.parse(code));
    }

    @Test
    void roundTripForEveryTypeAndOtherLengths() {
        for (int type = 0; type <= 9; type++) {
            assertEquals(new Scanned(type, 7), CODES.parse(CODES.encode(type, 7)).orElseThrow());
        }
        DocumentCode shortCodes = DocumentCode.of(9, 6);
        assertEquals(9, shortCodes.length());
        String code = shortCodes.encode(4, 123456);
        assertEquals(new Scanned(4, 123456), shortCodes.parse(code).orElseThrow());
        assertTrue(CODES.parse(code).isEmpty(), "طول تاني مش كود بالشكل ده");
        DocumentCode longest = DocumentCode.of(1, 15);
        assertEquals(new Scanned(3, 999_999_999_999_999L),
                longest.parse(longest.encode(3, 999_999_999_999_999L)).orElseThrow());
    }

    /** أي رقم يتغيّر في الكود (غير خانة التحقق نفسها بقيمة تانية) بيتكشف. */
    @Test
    void everyChangedDigitIsRejected() {
        String code = CODES.encode(1, 1751);
        for (int i = 0; i < code.length(); i++) {
            for (char d = '0'; d <= '9'; d++) {
                if (d == code.charAt(i)) {
                    continue;
                }
                String changed = code.substring(0, i) + d + code.substring(i + 1);
                assertTrue(CODES.parse(changed).isEmpty(), changed);
            }
        }
    }

    /** رقمين جنب بعض اتبدلوا (الغلطة المعتادة في الكتابة باليد) بيتكشفوا، طالما الفرق بينهم مش 5. */
    @Test
    void swappedNeighboursAreRejected() {
        String code = CODES.encode(3, 9876543210L);
        for (int i = 1; i < code.length() - 2; i++) {
            char a = code.charAt(i);
            char b = code.charAt(i + 1);
            if (a == b || Math.abs(a - b) == 5) {
                continue;
            }
            String swapped = code.substring(0, i) + b + a + code.substring(i + 2);
            assertTrue(CODES.parse(swapped).isEmpty(), swapped);
        }
    }

    /** الماسح على لوحة مفاتيح عربية، أو رقم منسوخ من مستند عربي: الأرقام العربية والفارسية مقبولة. */
    @Test
    void arabicIndicDigitsAndSurroundingSpacesAreAccepted() {
        assertEquals(Optional.of(new Scanned(1, 42)), CODES.parse("٢١٠٠٠٠٠٠٠٠٤٢٥"));
        assertEquals(Optional.of(new Scanned(1, 42)), CODES.parse("۲۱۰۰۰۰۰۰۰۰۴۲۵"));
        assertEquals(Optional.of(new Scanned(1, 42)), CODES.parse("  2100000000425\r\n"));
        assertEquals(Optional.of(new Scanned(1, 42)), CODES.parse("21000٠٠٠٠٠425"), "خليط");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "42", "1751", "210000000042", "21000000004250", "3100000000428",
            "2100000000426", "21000000004a5", "2100 0000425", "-100000000425", "２１００００００００４２５"})
    void whatIsNotACodeIsEmpty(String text) {
        assertTrue(CODES.parse(text).isEmpty(), text);
    }

    @Test
    void nullIsNotACode() {
        assertTrue(CODES.parse(null).isEmpty());
    }

    /** رقم فاتورة اتكتب باليد في خانة البحث ما بيتاخدش على إنه كود، حتى لو طوله 13. */
    @Test
    void aTypedNumberIsNotTakenForACode() {
        int accepted = 0;
        for (long n = 2_100_000_000_000L; n < 2_100_000_000_100L; n++) {
            if (CODES.parse(Long.toString(n)).isPresent()) {
                accepted++;
            }
        }
        assertEquals(10, accepted, "رقم واحد من كل عشرة بالصدفة خانة تحققه صح، مش أكتر");
    }

    @Test
    void invalidArgumentsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> DocumentCode.of(0, 10));
        assertThrows(IllegalArgumentException.class, () -> DocumentCode.of(10, 10));
        assertThrows(IllegalArgumentException.class, () -> DocumentCode.of(2, 0));
        assertThrows(IllegalArgumentException.class, () -> DocumentCode.of(2, 16));
        assertThrows(IllegalArgumentException.class, () -> CODES.encode(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> CODES.encode(10, 1));
        assertThrows(IllegalArgumentException.class, () -> CODES.encode(1, -1));
        IllegalArgumentException tooBig = assertThrows(IllegalArgumentException.class,
                () -> CODES.encode(1, 10_000_000_000L));
        assertTrue(tooBig.getMessage().contains("9999999999"), tooBig.getMessage());
        assertThrows(IllegalArgumentException.class, () -> DocumentCode.checkDigit(""));
        assertThrows(IllegalArgumentException.class, () -> DocumentCode.checkDigit("12a"));
        assertThrows(IllegalArgumentException.class, () -> DocumentCode.checkDigit(null));
    }

    @Test
    void equalShapesAreEqual() {
        assertEquals(DocumentCode.DEFAULT, DocumentCode.of(2, 10));
        assertEquals(DocumentCode.DEFAULT.hashCode(), DocumentCode.of(2, 10).hashCode());
        assertNotEquals(DocumentCode.DEFAULT, DocumentCode.of(3, 10));
    }
}
