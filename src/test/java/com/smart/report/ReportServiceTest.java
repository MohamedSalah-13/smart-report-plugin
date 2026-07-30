package com.smart.report;

import com.smart.report.jasper.JasperExportFormat;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportServiceTest {

    private final List<Employee> employees = List.of(
            new Employee(1, "Ahmed Ali", "Engineering", 12000.0),
            new Employee(2, "Sara Youssef", "Finance", 9500.5),
            new Employee(3, "Omar Khaled", "Engineering", 11000.0)
    );

    @Test
    void generatesGenericPdfReport() throws Exception {
        ReportRequest<Employee> request = ReportRequest.<Employee>builder()
                .title("Employee Report")
                .data(employees)
                .addColumn("id", "ID")
                .addColumn("name", "Name")
                .addColumn("department", "Department")
                .addColumn("salary", "Salary")
                .build();

        byte[] pdf = ReportService.generate(ReportType.PDF, request);
        assertTrue(pdf.length > 0);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertEquals(1, document.getNumberOfPages());
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Employee Report"));
            assertTrue(text.contains("Ahmed Ali"));
            assertTrue(text.contains("Engineering"));
        }
    }

    @Test
    void generatesGenericExcelReport() throws Exception {
        ReportRequest<Employee> request = ReportRequest.<Employee>builder()
                .title("Employee Report")
                .data(employees)
                .addColumn("id", "ID")
                .addColumn("name", "Name")
                .addColumn("department", "Department")
                .addColumn("salary", "Salary")
                .build();

        byte[] xlsx = ReportService.generate(ReportType.EXCEL, request);
        assertTrue(xlsx.length > 0);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(2);
            assertEquals("ID", headerRow.getCell(0).getStringCellValue());
            assertEquals("Name", headerRow.getCell(1).getStringCellValue());

            Row firstDataRow = sheet.getRow(3);
            assertEquals("Ahmed Ali", firstDataRow.getCell(1).getStringCellValue());
        }
    }

    @Test
    void generatesJasperPdfReport() throws Exception {
        ReportRequest<Employee> request = ReportRequest.<Employee>builder()
                .data(employees)
                .template("reports/employee.jrxml")
                .parameter("ReportTitle", "Employee Report")
                .exportFormat(JasperExportFormat.PDF)
                .build();

        byte[] pdf = ReportService.generate(ReportType.JASPER, request);
        assertTrue(pdf.length > 0);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Ahmed Ali"));
            assertTrue(text.contains("Employee Report"));
        }
    }

    @Test
    void generatesJasperExcelReport() throws Exception {
        ReportRequest<Employee> request = ReportRequest.<Employee>builder()
                .data(employees)
                .template("reports/employee.jrxml")
                .parameter("ReportTitle", "Employee Report")
                .exportFormat(JasperExportFormat.XLSX)
                .build();

        byte[] xlsx = ReportService.generate(ReportType.JASPER, request);
        assertTrue(xlsx.length > 0);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertTrue(workbook.getNumberOfSheets() > 0);
        }
    }
}
