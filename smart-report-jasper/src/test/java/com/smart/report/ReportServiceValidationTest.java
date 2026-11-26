package com.smart.report;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportServiceValidationTest {

    public record Row(String label) {}

    private static ReportRequest<Row> validRequest() {
        return ReportRequest.<Row>builder()
                .title("T")
                .data(List.of(new Row("a")))
                .addColumn("label", "Label")
                .build();
    }

    @Test
    void rejectsNullArgumentsWithAClearMessage() {
        assertThrows(IllegalArgumentException.class, () -> ReportService.generate(null, validRequest()));
        assertThrows(IllegalArgumentException.class, () -> ReportService.generate(ReportType.PDF, null));
    }

    /** كانت بترمي NullPointerException فاضية من List.copyOf. */
    @Test
    void namesTheOffendingRowWhenDataContainsNull() {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("a"));
        rows.add(null);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> ReportRequest.<Row>builder().data(rows).addColumn("label", "L").build());
        assertTrue(thrown.getMessage().contains("data[1]"), thrown.getMessage());
    }

    @Test
    void namesTheOffendingColumnWhenColumnsContainNull() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> ReportRequest.<Row>builder()
                        .data(List.of(new Row("a")))
                        .columns(Arrays.asList(ColumnDefinition.of("label", "L"), null))
                        .build());
        assertTrue(thrown.getMessage().contains("columns[1]"), thrown.getMessage());
    }

    /**
     * عقد الـ API بيقول {@code throws ReportException}؛ اللي بيمسكه لازم يمسك كل حالات الفشل،
     * مش يتفاجئ باستثناء unchecked طالع من جوّه المكتبة.
     */
    @Test
    void unexpectedFailuresArriveAsReportExceptionNotUnchecked() {
        ReportRequest<Row> request = ReportRequest.<Row>builder()
                .title("T")
                .data(List.of(new Row("a")))
                .addColumn("thisPropertyDoesNotExist", "Missing")
                .build();

        ReportException thrown = assertThrows(ReportException.class,
                () -> ReportService.generate(ReportType.PDF, request));
        assertTrue(thrown.getMessage().contains("thisPropertyDoesNotExist"), thrown.getMessage());
        assertInstanceOf(IllegalStateException.class, thrown.getCause(), "السبب الأصلي لازم يفضل متاح");
    }

    @Test
    void jasperTemplatePathThatIsNotAValidFilePathIsReported() {
        ReportRequest<Row> request = ReportRequest.<Row>builder()
                .data(List.of(new Row("a")))
                .template("reports/does?not:exist.jrxml")
                .build();

        ReportException thrown = assertThrows(ReportException.class,
                () -> ReportService.generate(ReportType.JASPER, request));
        assertTrue(thrown.getMessage().contains("does?not:exist.jrxml"), thrown.getMessage());
    }

    @Test
    void validatesOutputPathBeforeDoingTheWork(@TempDir Path dir) {
        assertThrows(IllegalArgumentException.class,
                () -> ReportService.generateToFile(ReportType.PDF, validRequest(), null));
        assertTrue(dir.toFile().isDirectory());
    }

    @Test
    void writesToFileAndCreatesMissingParentDirectories(@TempDir Path dir) throws Exception {
        Path target = dir.resolve("nested/deeper/report.pdf");
        assertFalse(Files.exists(target.getParent()));

        ReportService.generateToFile(ReportType.PDF, validRequest(), target);

        assertTrue(Files.isRegularFile(target));
        assertEquals("%PDF", new String(Arrays.copyOf(Files.readAllBytes(target), 4)));
    }
}
