package com.smart.report.jasper;

import com.smart.report.JasperExportFormat;
import com.smart.report.ReportException;
import com.smart.report.ReportProvider;
import com.smart.report.ReportRequest;
import com.smart.report.ReportType;
import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.engine.SimpleJasperReportsContext;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.export.HtmlExporter;
import net.sf.jasperreports.engine.export.JRCsvExporter;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.engine.fill.TextMeasurerFactory;
import net.sf.jasperreports.engine.util.JRLoader;
import net.sf.jasperreports.engine.util.JRTextMeasurerFactory;
import net.sf.jasperreports.engine.util.JRTextMeasurerUtil;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleHtmlExporterOutput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimpleWriterExporterOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates reports from JasperReports templates (a .jrxml file compiled at run time, or a precompiled .jasper),
 * fills them with data from a List&lt;POJO&gt; through JRBeanCollectionDataSource, and exports the result to the
 * format requested through {@link JasperExportFormat} (PDF / XLSX / HTML / CSV).
 *
 * <p>The template path ({@code ReportRequest#template()}) is looked up first as a file on disk, then as a resource
 * on the classpath (useful for shipping templates inside the consuming project's jar under
 * src/main/resources).</p>
 *
 * <p><b>Cache:</b> compiling a .jrxml is expensive (it goes through the Java compiler), so the compiled template is
 * stored and reused. Templates on disk are recompiled automatically when the file changes, and classpath templates
 * are compiled once, because they do not change at run time.</p>
 *
 * <p><b>Arabic in PDF:</b> the module registers Noto Naskh Arabic as a font extension of JasperReports itself, not
 * here: Arabic letters in a template that names no font are drawn with it ({@link ArabicFonts}), and a template
 * can name "Noto Naskh Arabic" itself. Filling measures text with {@link ArabicTextMeasurerFactory}: an Arabic line
 * slightly taller than its box is drawn instead of disappearing, and text that would print nothing logs a warning
 * that names it.</p>
 */
public final class JasperReportProvider implements ReportProvider {

    private static final Logger log = LoggerFactory.getLogger(JasperReportProvider.class);

    /** A fixed timestamp for classpath templates: they cannot change while the application runs. */
    private static final long CLASSPATH_STAMP = -1L;

    private static final Map<String, CachedTemplate> CACHE = new ConcurrentHashMap<>();

    private static final JasperReportsContext ARABIC_FIT = arabicFitContext();

    private static JasperReportsContext arabicFitContext() {
        SimpleJasperReportsContext context = new SimpleJasperReportsContext(DefaultJasperReportsContext.getInstance());
        context.setProperty(JRTextMeasurerUtil.PROPERTY_TEXT_MEASURER_FACTORY, ArabicTextMeasurerFactory.class.getName());
        return context;
    }

    private record CachedTemplate(JasperReport report, long stamp) {}

    @Override
    public ReportType type() {
        return ReportType.JASPER;
    }

    @Override
    public byte[] generate(ReportRequest<?> request) throws ReportException {
        if (request.template() == null || request.template().isBlank()) {
            throw new ReportException("A Jasper template path is required.");
        }

        try {
            JasperReport jasperReport = compile(request.template());
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(request.data());
            Map<String, Object> parameters = new HashMap<>(request.parameters());
            JasperPrint print = JasperFillManager.getInstance(fillContext()).fill(jasperReport, parameters, dataSource);
            return export(print, request.exportFormat());
        } catch (JRException | IOException e) {
            throw new ReportException("Failed to generate Jasper report from template: " + request.template(), e);
        }
    }

    /**
     * The fill context: the global JasperReports context, with {@link ArabicTextMeasurerFactory} as its text
     * measurer when neither the application nor the template chose a measurer (a template or element property
     * overrides the context). One context for all fills, so the cache JasperReports keeps in the context survives;
     * all other properties are read from the global context at fill time.
     */
    static JasperReportsContext fillContext() {
        JasperReportsContext global = DefaultJasperReportsContext.getInstance();
        JRTextMeasurerFactory chosen = JRTextMeasurerUtil.getInstance(global).getFactory(null);
        return chosen.getClass() == TextMeasurerFactory.class ? ARABIC_FIT : global;
    }

    /** Empties the cache; useful in tests, and when templates are replaced at run time. */
    public static void clearTemplateCache() {
        CACHE.clear();
    }

    private JasperReport compile(String template) throws JRException, IOException {
        Path file = asExistingFile(template);
        long stamp = file == null ? CLASSPATH_STAMP : Files.getLastModifiedTime(file).toMillis();

        CachedTemplate cached = CACHE.get(template);
        if (cached != null && cached.stamp() == stamp) {
            log.debug("Reusing compiled Jasper template {}", template);
            return cached.report();
        }

        long startedAt = System.nanoTime();
        JasperReport compiled;
        try (InputStream in = openTemplate(template, file)) {
            compiled = template.endsWith(".jasper")
                    ? (JasperReport) JRLoader.loadObject(in)
                    : JasperCompileManager.compileReport(in);
        }
        CACHE.put(template, new CachedTemplate(compiled, stamp));
        log.debug("Compiled Jasper template {} in {} ms", template, (System.nanoTime() - startedAt) / 1_000_000);
        return compiled;
    }

    /**
     * The path as an existing file on disk, or {@code null} when it does not exist or is not even a valid path on
     * this system. Classpath resource names can contain characters that are not allowed in Windows paths, so the
     * lookup must go on to the classpath instead of throwing InvalidPathException.
     */
    private static Path asExistingFile(String template) {
        try {
            Path path = Path.of(template);
            return Files.isRegularFile(path) ? path : null;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private InputStream openTemplate(String template, Path file) throws IOException {
        if (file != null) {
            return new BufferedInputStream(Files.newInputStream(file));
        }
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = getClass().getClassLoader();
        }
        InputStream resource = loader.getResourceAsStream(template);
        if (resource == null) {
            throw new IOException("Jasper template not found (neither as a file nor as a classpath resource): " + template);
        }
        return resource;
    }

    /**
     * Exports a Jasper report that the application filled itself to PDF, with this module's Arabic fonts, so it
     * can be previewed in {@code PdfPreview.ofPdf} or printed with {@code PdfPrinter}.
     *
     * @throws ReportException if the export fails
     */
    public static byte[] pdf(JasperPrint print) throws ReportException {
        java.util.Objects.requireNonNull(print, "print");
        try {
            return JasperExportManager.exportReportToPdf(print);
        } catch (JRException e) {
            throw new ReportException("Failed to export the Jasper report to PDF: " + e.getMessage(), e);
        }
    }

    private byte[] export(JasperPrint print, JasperExportFormat format) throws JRException, IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        switch (format) {
            case PDF -> out.write(JasperExportManager.exportReportToPdf(print));
            case XLSX -> {
                JRXlsxExporter exporter = new JRXlsxExporter();
                exporter.setExporterInput(new SimpleExporterInput(print));
                exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
                exporter.exportReport();
            }
            case HTML -> {
                HtmlExporter exporter = new HtmlExporter();
                exporter.setExporterInput(new SimpleExporterInput(print));
                exporter.setExporterOutput(new SimpleHtmlExporterOutput(out));
                exporter.exportReport();
            }
            case CSV -> {
                JRCsvExporter exporter = new JRCsvExporter();
                exporter.setExporterInput(new SimpleExporterInput(print));
                exporter.setExporterOutput(new SimpleWriterExporterOutput(new OutputStreamWriter(out, StandardCharsets.UTF_8)));
                exporter.exportReport();
            }
        }
        return out.toByteArray();
    }
}
