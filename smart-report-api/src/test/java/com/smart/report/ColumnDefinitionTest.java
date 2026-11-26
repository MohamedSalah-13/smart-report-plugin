package com.smart.report;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ColumnDefinitionTest {

    /** الكود اللي اتكتب قبل الخيارات بيفضل شغال وبنفس السلوك. */
    @Test
    void plainColumnHasNoOptionsAndWeightOne() {
        ColumnDefinition column = new ColumnDefinition("amount", "Amount");

        assertEquals(column, ColumnDefinition.of("amount", "Amount"));
        assertNull(column.format());
        assertNull(column.align());
        assertEquals(1f, column.weight());
        assertEquals("amount", ColumnDefinition.of("amount", " ").header(), "العنوان الفاضي بياخد اسم الحقل");
    }

    @Test
    void optionsAreSetWithoutTouchingTheRest() {
        ColumnDefinition column = ColumnDefinition.of("amount", "Amount")
                .withFormat("#,##0.00").withAlign(Alignment.CENTER).withWeight(2.5f);

        assertEquals(new ColumnDefinition("amount", "Amount", "#,##0.00", Alignment.CENTER, 2.5f), column);
        assertNull(column.withFormat(" ").format(), "النمط الفاضي = الافتراضي");
        assertNull(column.withAlign(null).align());
        assertEquals("#,##0.00", column.withWeight(1).format());
    }

    @Test
    void weightMustBePositive() {
        ColumnDefinition column = ColumnDefinition.of("amount", "Amount");
        for (float bad : new float[]{0f, -1f, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> column.withWeight(bad), "الوزن " + bad);
        }
    }

    @Test
    void builderTakesAColumnWithItsOptions() {
        ReportRequest<Object> request = ReportRequest.builder()
                .addColumn("name", "Name")
                .addColumn(ColumnDefinition.of("amount", "Amount").withWeight(3))
                .build();

        assertEquals(List.of(ColumnDefinition.of("name", "Name"), ColumnDefinition.of("amount", "Amount").withWeight(3)),
                request.columns());
        assertThrows(IllegalArgumentException.class, () -> ReportRequest.builder().addColumn((ColumnDefinition) null));
    }
}
