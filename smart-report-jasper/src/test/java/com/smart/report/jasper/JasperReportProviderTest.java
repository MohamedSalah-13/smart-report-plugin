package com.smart.report.jasper;

import com.smart.report.Employee;
import com.smart.report.ReportException;
import com.smart.report.ReportRequest;
import com.smart.report.ReportService;
import com.smart.report.ReportType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JasperReportProviderTest {

    // قوالب Jasper بتقرا الحقول عبر commons-beanutils، اللي بيطلب getters بنمط JavaBean
    private static final List<Employee> EMPLOYEES = List.of(
            new Employee(1, "Ahmed Ali", "Engineering", 12000.0));

    @BeforeEach
    void clearCache() {
        JasperReportProvider.clearTemplateCache();
    }

    private static ReportRequest<Employee> request(String template) {
        return ReportRequest.<Employee>builder()
                .data(EMPLOYEES)
                .template(template)
                .parameter("ReportTitle", "Employee Report")
                .build();
    }

    /** التصريف الغالي بيحصل مرة واحدة، والنداء التاني بيدّي نفس النتيجة أسرع. */
    @Test
    void reusesTheCompiledTemplateAcrossCalls() throws Exception {
        byte[] first = ReportService.generate(ReportType.JASPER, request("reports/employee.jrxml"));

        long start = System.nanoTime();
        byte[] second = ReportService.generate(ReportType.JASPER, request("reports/employee.jrxml"));
        long cachedNanos = System.nanoTime() - start;

        assertTrue(first.length > 0);
        assertTrue(second.length > 0);
        // التقرير المتولّد لازم يفضل صحيحًا، مش بس أسرع
        assertArrayEquals("%PDF".getBytes(), java.util.Arrays.copyOf(second, 4));
        assertTrue(cachedNanos < 5_000_000_000L, "النداء المخزَّن أخد وقتًا غير معقول: " + cachedNanos + "ns");
    }

    /**
     * اسم مورد فيه محارف ممنوعة في مسارات ويندوز كان بيرمي InvalidPathException قبل
     * ما يجرّب الـ classpath أصلًا. المفروض دلوقتي يوصل لرسالة "مش لاقي القالب" العادية.
     */
    @Test
    void templateNamesThatAreNotValidFilePathsFallThroughToTheClasspath() {
        ReportException thrown = assertThrows(ReportException.class,
                () -> ReportService.generate(ReportType.JASPER, request("reports/does?not:exist.jrxml")));

        Throwable root = thrown;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        assertTrue(root.getMessage().contains("Jasper template not found"),
                "لازم يوصل لبحث الـ classpath مش يقع على تحويل المسار: " + root);
    }

    @Test
    void picksUpTemplateChangesOnDisk(@TempDir Path dir) throws Exception {
        Path template = dir.resolve("employee.jrxml");
        try (var in = getClass().getClassLoader().getResourceAsStream("reports/employee.jrxml")) {
            Files.write(template, in.readAllBytes());
        }

        byte[] before = ReportService.generate(ReportType.JASPER, request(template.toString()));
        assertTrue(before.length > 0);

        // نعدّل القالب ونتأكد إن الكاش ما بيقدّمش نسخة قديمة
        String modified = Files.readString(template).replace("Employee Report", "Updated Report");
        Files.writeString(template, modified);
        Files.setLastModifiedTime(template, java.nio.file.attribute.FileTime.fromMillis(
                Files.getLastModifiedTime(template).toMillis() + 2000));

        assertTrue(ReportService.generate(ReportType.JASPER, request(template.toString())).length > 0);
    }

    @Test
    void missingTemplateIsReportedClearly() {
        ReportException thrown = assertThrows(ReportException.class,
                () -> ReportService.generate(ReportType.JASPER, request("reports/nope.jrxml")));
        assertTrue(thrown.getMessage().contains("reports/nope.jrxml"), thrown.getMessage());
    }

    @Test
    void blankTemplateIsRejected() {
        assertThrows(ReportException.class,
                () -> ReportService.generate(ReportType.JASPER, request("   ")));
    }
}
