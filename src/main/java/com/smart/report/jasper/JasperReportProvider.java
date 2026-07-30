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

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * يولّد تقارير من قوالب JasperReports (ملف .jrxml يُصرَّف وقت التشغيل، أو .jasper مُصرَّف مسبقًا)،
 * مع تعبئة البيانات من List&lt;POJO&gt; عبر JRBeanCollectionDataSource، وتصدير النتيجة للصيغة
 * المطلوبة عبر {@link JasperExportFormat} (PDF / XLSX / HTML / CSV).
 *
 * <p>مسار القالب ({@code ReportRequest#template()}) يُبحث عنه أولًا كملف على القرص، ثم كمورد
 * على الـ classpath (مناسب لتضمين القوالب داخل jar المشروع المستهلك تحت src/main/resources).</p>
 */
public final class JasperReportProvider implements ReportProvider {

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

    private JasperReport compile(String template) throws JRException, IOException {
        try (InputStream in = openTemplate(template)) {
            if (template.endsWith(".jasper")) {
                return (JasperReport) JRLoader.loadObject(in);
            }
            return JasperCompileManager.compileReport(in);
        }
    }

    private InputStream openTemplate(String template) throws IOException {
        Path path = Path.of(template);
        if (Files.exists(path)) {
            return new BufferedInputStream(new FileInputStream(path.toFile()));
        }
        InputStream resource = getClass().getClassLoader().getResourceAsStream(template);
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
