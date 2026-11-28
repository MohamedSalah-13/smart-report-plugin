package com.smart.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * The single entry point for generating reports. Pick the report type ({@link ReportType}) and prepare a
 * {@link ReportRequest}. The service picks the matching provider (generic PDF / generic Excel / Jasper).
 *
 * <p>Any failure during generation reaches the caller as a {@link ReportException}, even when it starts as
 * an unexpected exception from an underlying library. So a {@code catch (ReportException)} in the caller's
 * code really catches every case. The one exception is validation of the arguments themselves: it throws
 * {@link IllegalArgumentException}, because that is a programming error in the caller, not a generation
 * failure.</p>
 */
public final class ReportService {

    private ReportService() {}

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    public static byte[] generate(ReportType type, ReportRequest<?> request) throws ReportException {
        if (type == null) {
            throw new IllegalArgumentException("Report type is required.");
        }
        if (request == null) {
            throw new IllegalArgumentException("Report request is required.");
        }
        long startedAt = System.nanoTime();
        try {
            byte[] report = provider(type).generate(request);
            log.debug("Generated {} report: {} rows, {} bytes, {} ms",
                    type, request.data().size(), report.length, (System.nanoTime() - startedAt) / 1_000_000);
            return report;
        } catch (RuntimeException e) {
            // مثال: خاصية مش موجودة على الكائن، أو مسار قالب مش صالح كمسار ملف
            throw new ReportException("Failed to generate " + type + " report: " + e.getMessage(), e);
        }
    }

    public static void generateToFile(ReportType type, ReportRequest<?> request, Path outputPath) throws ReportException {
        if (outputPath == null) {
            throw new IllegalArgumentException("Output path is required.");
        }
        byte[] bytes = generate(type, request);
        try {
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }
            Files.write(outputPath, bytes);
        } catch (IOException e) {
            throw new ReportException("Failed to write report to file: " + outputPath, e);
        }
    }

    /**
     * The provider registered for this type. Each type lives in its own Maven module, so when the module is
     * not on the classpath the message names the missing dependency instead of throwing an unclear exception.
     */
    private static ReportProvider provider(ReportType type) throws ReportException {
        try {
            for (ReportProvider provider : ServiceLoader.load(ReportProvider.class, ReportService.class.getClassLoader())) {
                if (provider.type() == type) {
                    return provider;
                }
            }
        } catch (ServiceConfigurationError e) {
            throw new ReportException("Failed to load the " + type + " report provider: " + e.getMessage(), e);
        }
        throw new ReportException("No provider for " + type + " reports is on the classpath. Add the "
                + type.module() + " dependency to your project.");
    }
}
