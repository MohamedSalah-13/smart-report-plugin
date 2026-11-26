package com.smart.report.pdf;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CellTextTest {

    private final CellText english = new CellText(Locale.ENGLISH);
    private final CellText arabic = new CellText(Locale.forLanguageTag("ar"));

    @Test
    void numberPatternRoundsHalfUpAndGroups() {
        assertEquals("1,234.50", english.of(1234.5, "#,##0.00"));
        assertEquals("2.35", english.of(new BigDecimal("2.345"), "0.00"), "النص 5 لفوق، مش لأقرب زوجي");
        assertEquals("1,000,000", english.of(1_000_000L, "#,##0"));
        assertEquals("12.5%", english.of(0.125, "0.0%"));
    }

    @Test
    void datePatternIsApplied() {
        assertEquals("26/11/2026", english.of(LocalDate.of(2026, 11, 26), "dd/MM/yyyy"));
        assertEquals("26/11/2026 09:05", english.of(LocalDateTime.of(2026, 11, 26, 9, 5), "dd/MM/yyyy HH:mm"));
        assertEquals("25/11/2026", english.of(java.sql.Date.valueOf("2026-11-25"), "dd/MM/yyyy"));
    }

    /** أسماء الشهور بلغة التقرير، والأرقام لاتينية في الاتنين. */
    @Test
    void monthNamesFollowTheReportLanguageButDigitsStayLatin() {
        assertEquals("26 November 2026", english.of(LocalDate.of(2026, 11, 26), "d MMMM yyyy"));
        assertEquals("26 نوفمبر 2026", arabic.of(LocalDate.of(2026, 11, 26), "d MMMM yyyy"));
        assertEquals("1,234.50", arabic.of(1234.5, "#,##0.00"));
    }

    @Test
    void valuesThePatternDoesNotApplyToAreLeftAsTheyAre() {
        assertEquals("n/a", english.of("n/a", "#,##0.00"));
        assertEquals("true", english.of(true, "dd/MM/yyyy"));
        assertEquals("", english.of(null, "#,##0.00"));
        assertEquals("1,234.5", english.of(1234.5, " "), "النمط الفاضي = الافتراضي");
    }

    @Test
    void patternThatCannotFormatTheValueNamesItAndTheValue() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> english.of(LocalDate.of(2026, 11, 26), "HH:mm"));
        assertTrue(thrown.getMessage().contains("HH:mm") && thrown.getMessage().contains("2026-11-26"),
                thrown.getMessage());
    }
}
