package com.smart.report.jasper;

import com.smart.report.ReportException;
import com.smart.report.ReportProvider;
import com.smart.report.ReportRequest;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.export.HtmlExporter;
import net.sf.jasperreports.engine.export.JRCsvExporter;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.engine.util.JRLoader;
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
 * يولّد تقارير من قوالب JasperReports (ملف .jrxml يُصرَّف وقت التشغيل، أو .jasper مُصرَّف مسبقًا)،
 * مع تعبئة البيانات من List&lt;POJO&gt; عبر JRBeanCollectionDataSource، وتصدير النتيجة للصيغة
 * المطلوبة عبر {@link JasperExportFormat} (PDF / XLSX / HTML / CSV).
 *
 * <p>مسار القالب ({@code ReportRequest#template()}) يُبحث عنه أولًا كملف على القرص، ثم كمورد
 * على الـ classpath (مناسب لتضمين القوالب داخل jar المشروع المستهلك تحت src/main/resources).</p>
 *
 * <p><b>الكاش:</b> تصريف الـ .jrxml عملية غالية (بتعدّي على مصرّف جافا)، فالقالب المصرَّف
 * بيتخزّن ويُعاد استخدامه. قوالب القرص بيتعاد تصريفها لوحدها لو الملف اتعدّل، وقوالب الـ classpath
 * بتتصرّف مرة واحدة لأنها ما بتتغيّرش وقت التشغيل.</p>
 */
public final class JasperReportProvider implements ReportProvider {

    private static final Logger log = LoggerFactory.getLogger(JasperReportProvider.class);

    /** ختم زمني ثابت لقوالب الـ classpath: مش ممكن تتغيّر وإحنا شغالين. */
    private static final long CLASSPATH_STAMP = -1L;

    private static final Map<String, CachedTemplate> CACHE = new ConcurrentHashMap<>();

    private record CachedTemplate(JasperReport report, long stamp) {}

    @Override
    public byte[] generate(ReportRequest<?> request) throws ReportException {
        if (request.template() == null || request.template().isBlank()) {
            throw new ReportException("A Jasper template path is required.");
        }

        try {
            JasperReport jasperReport = compile(request.template());
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(request.data());
            Map<String, Object> parameters = new HashMap<>(request.parameters());
            JasperPrint print = JasperFillManager.fillReport(jasperReport, parameters, dataSource);
            return export(print, request.exportFormat());
        } catch (JRException | IOException e) {
            throw new ReportException("Failed to generate Jasper report from template: " + request.template(), e);
        }
    }

    /** يفضّي الكاش؛ مفيد في الاختبارات ولو القوالب بتتبدّل وقت التشغيل. */
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
     * المسار كملف موجود على القرص، أو {@code null} لو مش موجود أو أصلًا مش اسم مسار صالح
     * على النظام ده. أسماء موارد الـ classpath ممكن تحتوي محارف ممنوعة في مسارات ويندوز،
     * فلازم نكمّل للبحث في الـ classpath بدل ما نرمي InvalidPathException.
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
