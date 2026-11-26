package com.smart.report;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * الوحدة دي ما فيهاش أي مزوّد على الـ classpath، وده بالظبط وضع مشروع نسي يضيف وحدة النوع اللي
 * طلبه: الرسالة لازم تقول له يضيف إيه.
 */
class ReportServiceProviderLookupTest {

    public record Row(String label) {}

    @Test
    void missingModuleIsNamedInTheError() {
        ReportRequest<Row> request = ReportRequest.<Row>builder()
                .data(List.of(new Row("a")))
                .addColumn("label", "Label")
                .build();

        for (ReportType type : ReportType.values()) {
            ReportException thrown = assertThrows(ReportException.class, () -> ReportService.generate(type, request));
            assertTrue(thrown.getMessage().contains(type.module()), thrown.getMessage());
        }
    }
}
