package com.aimes.modules.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Base64;

/**
 * 文件预处理器 — 从各类文件中提取文本内容供AI分析
 * 支持: Excel(.xlsx/.xls), Word(.docx), PDF(.pdf), 图片(.jpg/.png)
 */
@Slf4j
@Component
public class FilePreprocessor {

    /**
     * 统一入口：根据文件类型提取文本
     */
    public String extractText(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "";
        }
        String filename = file.getOriginalFilename();
        if (filename == null) {
            return "";
        }
        String lower = filename.toLowerCase();

        try {
            if (lower.endsWith(".xlsx")) {
                return extractFromExcelXlsx(file);
            } else if (lower.endsWith(".xls")) {
                return extractFromExcelXls(file);
            } else if (lower.endsWith(".docx")) {
                return extractFromWordDocx(file);
            } else if (lower.endsWith(".pdf")) {
                return extractFromPdf(file);
            } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".bmp")) {
                return extractFromImage(file);
            } else {
                log.warn("不支持的文件类型: {}", filename);
                return "[不支持的文件类型: " + filename + "]";
            }
        } catch (Exception e) {
            log.error("文件解析失败: {}", filename, e);
            return "[文件解析失败: " + filename + ", 错误: " + e.getMessage() + "]";
        }
    }

    /**
     * Excel .xlsx 解析
     */
    private String extractFromExcelXlsx(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream(); XSSFWorkbook workbook = new XSSFWorkbook(is)) {
            return extractWorkbook(workbook);
        }
    }

    /**
     * Excel .xls 解析
     */
    private String extractFromExcelXls(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream(); HSSFWorkbook workbook = new HSSFWorkbook(is)) {
            return extractWorkbook(workbook);
        }
    }

    private String extractWorkbook(Workbook workbook) {
        StringBuilder sb = new StringBuilder();
        DataFormatter formatter = new DataFormatter();
        // 公式求值器: DataFormatter 对 FORMULA cell 直接返回公式文本而非计算结果
        // 必须先 evaluator 求值, 否则 "数量" 列若是 =B5 之类的引用, AI 看到的是公式字符串而非数字
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
            Sheet sheet = workbook.getSheetAt(s);
            sb.append("=== Sheet: ").append(sheet.getSheetName()).append(" ===\n");
            for (Row row : sheet) {
                StringBuilder rowSb = new StringBuilder();
                for (Cell cell : row) {
                    if (rowSb.length() > 0) rowSb.append(" | ");
                    rowSb.append(safeCellValue(cell, formatter, evaluator));
                }
                sb.append(rowSb).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /** 取 cell 显示值: 公式 cell 先求值再格式化, 其他直接 formatCellValue */
    private String safeCellValue(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) return "";
        try {
            if (cell.getCellType() == CellType.FORMULA) {
                CellValue v = evaluator.evaluate(cell);
                if (v == null) return "";
                switch (v.getCellType()) {
                    case NUMERIC:
                        // 整数不要带 .0
                        double d = v.getNumberValue();
                        return d == Math.floor(d) && !Double.isInfinite(d)
                                ? String.valueOf((long) d) : String.valueOf(d);
                    case STRING:  return v.getStringValue();
                    case BOOLEAN: return String.valueOf(v.getBooleanValue());
                    case ERROR:   return "";
                    default:      return formatter.formatCellValue(cell);
                }
            }
            return formatter.formatCellValue(cell);
        } catch (Exception e) {
            return formatter.formatCellValue(cell);
        }
    }

    /**
     * Word .docx 解析
     */
    private String extractFromWordDocx(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream(); XWPFDocument doc = new XWPFDocument(is)) {
            StringBuilder sb = new StringBuilder();
            // 段落
            for (XWPFParagraph para : doc.getParagraphs()) {
                String text = para.getText();
                if (text != null && !text.isBlank()) {
                    sb.append(text).append("\n");
                }
            }
            // 表格
            for (XWPFTable table : doc.getTables()) {
                sb.append("\n[表格]\n");
                for (XWPFTableRow row : table.getRows()) {
                    StringBuilder rowSb = new StringBuilder();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        if (rowSb.length() > 0) rowSb.append(" | ");
                        rowSb.append(cell.getText());
                    }
                    sb.append(rowSb).append("\n");
                }
            }
            return sb.toString();
        }
    }

    /**
     * PDF 解析
     */
    private String extractFromPdf(MultipartFile file) throws Exception {
        try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    /**
     * 图片处理 — 转为base64描述（后续可接OCR）
     */
    private String extractFromImage(MultipartFile file) {
        String base64 = Base64.getEncoder().encodeToString(safeGetBytes(file));
        String filename = file.getOriginalFilename();
        return "[图片文件: " + filename + ", 大小: " + file.getSize() + "字节]\n" +
                "[BASE64:" + base64.substring(0, Math.min(100, base64.length())) + "...]\n" +
                "请根据图片文件名和上下文推断内容，如果无法识别请提示用户使用文字描述。";
    }

    private byte[] safeGetBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (Exception e) {
            return new byte[0];
        }
    }
}
