package com.smart.report.excel;

import com.smart.report.ColumnDefinition;
import com.smart.report.ReportException;
import com.smart.report.ReportProvider;
import com.smart.report.ReportRequest;
import com.smart.report.util.BeanPropertyReader;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * يولّد تقرير Excel (.xlsx) عام (عنوان + رأس أعمدة منسّق + صفوف بيانات) بدون الحاجة لأي قالب،
 * انطلاقًا من أي List من الكائنات + قائمة ColumnDefinition.
 */
public final class ExcelReportProvider implements ReportProvider {

    @Override
    public byte[] generate(ReportRequest<?> request) throws ReportException {
        List<ColumnDefinition> columns = request.columns();
        if (columns.isEmpty()) {
            throw new ReportException("At least one column is required to generate an Excel report.");
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(sheetName(request.title()));

            int rowIndex = 0;
            if (request.title() != null && !request.title().isBlank()) {
                Row titleRow = sheet.createRow(rowIndex++);
                Cell titleCell = titleRow.createCell(0);
                titleCell.setCellValue(request.title());
                titleCell.setCellStyle(titleStyle(workbook));
                rowIndex++; // سطر فاصل
            }

            Row headerRow = sheet.createRow(rowIndex++);
            CellStyle headerStyle = headerStyle(workbook);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns.get(i).header());
                cell.setCellStyle(headerStyle);
            }

            for (Object item : request.data()) {
                Row row = sheet.createRow(rowIndex++);
                for (int i = 0; i < columns.size(); i++) {
                    Object value = BeanPropertyReader.read(item, columns.get(i).fieldName());
                    writeCell(row.createCell(i), value);
                }
            }

            for (int i = 0; i < columns.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ReportException("Failed to generate Excel report.", e);
        }
    }

    private void writeCell(Cell cell, Object value) {
        switch (value) {
            case null -> cell.setBlank();
            case Number number -> cell.setCellValue(number.doubleValue());
            case Boolean bool -> cell.setCellValue(bool);
            case Date date -> cell.setCellValue(date);
            default -> cell.setCellValue(String.valueOf(value));
        }
    }

    private CellStyle headerStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle titleStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private String sheetName(String title) {
        if (title == null || title.isBlank()) return "Report";
        String sanitized = title.replaceAll("[\\\\/*\\[\\]:?]", " ").trim();
        if (sanitized.isEmpty()) return "Report";
        return sanitized.length() > 31 ? sanitized.substring(0, 31) : sanitized;
    }
}
