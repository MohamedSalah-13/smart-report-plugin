package com.smart.report.util;

import com.smart.report.ColumnDefinition;
import com.smart.report.ReportRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotalsTest {

    private static BigDecimal sum(Object... values) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Object value : values) {
            sum = sum.add(Totals.amount(value));
        }
        return sum;
    }

    /** المبالغ بتتجمع بالظبط مهما كان عدد خاناتها: الـ double كان هيضيّع آخر قرش. */
    @Test
    void decimalsAreAddedExactly() {
        assertEquals(new BigDecimal("12345678901234567.90"),
                sum(new BigDecimal("12345678901234567.89"), new BigDecimal("0.01")));
        assertEquals(0, new BigDecimal("1.00").compareTo(sum(new BigDecimal("0.10"), null, new BigDecimal("0.90"))),
                "الخلية الفاضية ما بتزوّدش حاجة");
    }

    /** الـ double بيتجمع بالرقم اللي بيظهر في خليته، مش بقيمته الثنائية. */
    @Test
    void doublesAndFloatsAreAddedByTheirShortestDecimalForm() {
        assertEquals(0, new BigDecimal("0.3").compareTo(sum(0.1, 0.2)));
        assertEquals(0, new BigDecimal("0.3").compareTo(sum(0.1f, 0.2f)));
        assertEquals("7300", sum(7200.0, 100.0).toPlainString(), "من غير خانة عشرية زيادة");
    }

    @Test
    void everyKindOfNumberIsAccepted() {
        DoubleAdder adder = new DoubleAdder();
        adder.add(0.5);
        assertEquals(0, new BigDecimal("16.5").compareTo(
                sum((byte) 1, (short) 2, 3, 4L, BigInteger.TWO, new AtomicLong(4), adder)));
    }

    @Test
    void anythingButAFiniteNumberIsRejected() {
        for (Object bad : new Object[]{"12", java.time.LocalDate.of(2026, 11, 27), true, Double.NaN,
                Float.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> Totals.amount(bad), String.valueOf(bad));
        }
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> Totals.amount("n/a"));
        assertTrue(thrown.getMessage().contains("String") && thrown.getMessage().contains("n/a"), thrown.getMessage());
    }

    /** نفس الرسالة في الـ PDF والـ Excel: اسم العمود، ورقم الصف من 1، والقيمة. */
    @Test
    void addNamesTheColumnAndTheRow() throws Exception {
        ColumnDefinition amount = ColumnDefinition.of("amount", "Amount").withTotal();
        assertEquals(new BigDecimal("3.5"), Totals.add(new BigDecimal("1.5"), 2, amount, 0));

        com.smart.report.ReportException thrown = assertThrows(com.smart.report.ReportException.class,
                () -> Totals.add(BigDecimal.ZERO, "n/a", amount, 4));
        assertEquals("Column 'amount', row 5: only numbers can be totalled, but the value is the String \"n/a\".",
                thrown.getMessage());
    }

    @Test
    void labelFollowsTheDirectionUnlessTheRequestSetsOne() {
        ReportRequest<Object> plain = ReportRequest.builder().build();
        assertEquals("الإجمالى", Totals.label(plain, true));
        assertEquals("Total", Totals.label(plain, false));
        assertEquals("المجموع", Totals.label(ReportRequest.builder().totalsLabel("المجموع").build(), false));
    }

    @Test
    void labelGoesUnderTheFirstColumnThatIsNotTotalled() {
        ReportRequest<Object> request = ReportRequest.builder()
                .addColumn(ColumnDefinition.of("amount", "Amount").withTotal())
                .addColumn("name", "Name")
                .addColumn("note", "Note")
                .build();
        assertTrue(Totals.any(request));
        assertEquals(1, Totals.labelColumn(request));

        ReportRequest<Object> allTotalled = ReportRequest.builder()
                .addColumn(ColumnDefinition.of("amount", "Amount").withTotal())
                .build();
        assertEquals(-1, Totals.labelColumn(allTotalled), "كل الأعمدة متجمّعة: الصف من غير كلمة");
        assertFalse(Totals.any(ReportRequest.builder().addColumn("name", "Name").build()));
    }
}
