package com.aimes.common.utils;

import com.aimes.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Excel导入导出工具类（基于Apache POI）
 */
public class ExcelUtil {

    private static final com.fasterxml.jackson.databind.ObjectMapper OM =
            new com.fasterxml.jackson.databind.ObjectMapper();

    static {
        // POI 5.2+ 给单条记录字节数加了 100MB 硬上限,大数据模拟场景下 SST/行流容易超过。
        // 调到 500MB 兜底;非常大的导入再考虑流式 XSSF (SAX) 方案。
        org.apache.poi.util.IOUtils.setByteArrayMaxOverride(500 * 1024 * 1024);
    }

    /**
     * 将规格 JSON 字符串格式化为可读形式,例如:
     *   [{"name":"材质","value":"NBR"},{"name":"尺寸","values":["Ø15mm"]}]
     *   → "材质:NBR 尺寸:Ø15mm"
     * 非规格 JSON 的字符串原样返回。
     */
    public static String formatSpec(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (!trimmed.startsWith("[")) return raw;
        try {
            com.fasterxml.jackson.databind.JsonNode arr = OM.readTree(trimmed);
            if (!arr.isArray() || arr.isEmpty()) return raw;
            StringBuilder sb = new StringBuilder();
            for (com.fasterxml.jackson.databind.JsonNode node : arr) {
                com.fasterxml.jackson.databind.JsonNode nameNode = node.get("name");
                if (nameNode == null) return raw; // 不是规格结构,原样返回
                if (sb.length() > 0) sb.append(' ');
                sb.append(nameNode.asText()).append(':');
                com.fasterxml.jackson.databind.JsonNode valuesNode = node.get("values");
                if (valuesNode != null && valuesNode.isArray()) {
                    List<String> vs = new ArrayList<>();
                    valuesNode.forEach(v -> { if (!v.isNull() && !v.asText().isEmpty()) vs.add(v.asText()); });
                    sb.append(String.join("/", vs));
                } else {
                    com.fasterxml.jackson.databind.JsonNode valueNode = node.get("value");
                    if (valueNode != null && !valueNode.isNull()) sb.append(valueNode.asText());
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return raw;
        }
    }

    /**
     * 导出Excel
     * @param response HTTP响应
     * @param fileName 文件名（不含扩展名）
     * @param headers 表头列表
     * @param data 数据列表
     * @param extractors 每列对应的数据提取函数
     */
    public static <T> void export(HttpServletResponse response, String fileName,
                                   List<String> headers, List<T> data,
                                   List<Function<T, Object>> extractors) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Sheet1");

        // 表头样式
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderBottom(BorderStyle.THIN);

        // 写表头
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 5000);
        }

        // 写数据
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (int rowIdx = 0; rowIdx < data.size(); rowIdx++) {
            Row row = sheet.createRow(rowIdx + 1);
            T item = data.get(rowIdx);
            for (int colIdx = 0; colIdx < extractors.size(); colIdx++) {
                Cell cell = row.createCell(colIdx);
                Object val = extractors.get(colIdx).apply(item);
                if (val == null) {
                    cell.setCellValue("");
                } else if (val instanceof Number) {
                    cell.setCellValue(((Number) val).doubleValue());
                } else if (val instanceof LocalDateTime) {
                    cell.setCellValue(((LocalDateTime) val).format(dtf));
                } else if (val instanceof LocalDate) {
                    cell.setCellValue(((LocalDate) val).format(df));
                } else if (val instanceof Boolean) {
                    cell.setCellValue((Boolean) val ? "是" : "否");
                } else {
                    String s = val.toString();
                    // 自动识别规格 JSON 并格式化（[{"name":...}] 结构）
                    if (s.length() > 2 && s.charAt(0) == '[' && s.contains("\"name\"")) {
                        s = formatSpec(s);
                    }
                    cell.setCellValue(s);
                }
            }
        }

        // 输出
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + URLEncoder.encode(fileName + ".xlsx", StandardCharsets.UTF_8));
        workbook.write(response.getOutputStream());
        workbook.close();
    }

    /**
     * 解析导入Excel
     * @param file 上传的文件
     * @param headers 期望的表头（用于校验）
     * @return 每行数据的Map列表（key=列名, value=单元格值）
     */
    public static List<Map<String, String>> parseImport(MultipartFile file, List<String> headers) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要导入的文件");
        }
        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() < 2) {
                throw new BusinessException("文件中没有数据");
            }

            // 校验表头（兼容带 * 必填标记的表头）
            Row headerRow = sheet.getRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.getCell(i);
                String cellVal = cell != null ? cell.getStringCellValue().trim() : "";
                String expected = headers.get(i);
                String actual = stripRequired(cellVal);
                if (!expected.equals(actual) && !expected.equals(cellVal)) {
                    throw new BusinessException("表头不匹配，第" + (i + 1) + "列期望'" + expected + "'，实际'" + cellVal + "'");
                }
            }

            // 解析数据行（始终使用不带 * 的原始表头名作为 key）
            List<Map<String, String>> result = new ArrayList<>();
            DataFormatter formatter = new DataFormatter();
            for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;
                boolean hasUserInput = false;
                Map<String, String> rowData = new LinkedHashMap<>();
                for (int colIdx = 0; colIdx < headers.size(); colIdx++) {
                    Cell cell = row.getCell(colIdx);
                    String val = readCellValue(cell, formatter);
                    rowData.put(headers.get(colIdx), val);
                    // 只有非公式单元格的非空值才视为用户输入
                    if (!val.isEmpty() && cell != null && cell.getCellType() != CellType.FORMULA) {
                        hasUserInput = true;
                    }
                }
                if (hasUserInput) {
                    result.add(rowData);
                }
            }
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("文件解析失败: " + e.getMessage());
        }
    }

    /**
     * 导出模板（只有表头没有数据）
     */
    public static void exportTemplate(HttpServletResponse response, String fileName, List<String> headers) throws IOException {
        exportTemplate(response, fileName, headers, Collections.emptyMap());
    }

    /**
     * 导出模板，并为指定列设置下拉数据校验
     * @param dropdowns key=列名(headers中的某项),value=下拉可选项
     */
    public static void exportTemplate(HttpServletResponse response, String fileName,
                                       List<String> headers, Map<String, List<String>> dropdowns) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Sheet1");

        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderBottom(BorderStyle.THIN);

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 5000);
        }

        applyDropdowns(sheet, headers, dropdowns);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + URLEncoder.encode(fileName + ".xlsx", StandardCharsets.UTF_8));
        workbook.write(response.getOutputStream());
        workbook.close();
    }

    /**
     * 在指定列上加 Excel 下拉数据校验。POI 单元格 explicit list 长度有限(255 字符),
     * 当选项数量较多或内容较长时,自动使用隐藏 Sheet + Name 引用方式。
     */
    private static void applyDropdowns(Sheet sheet, List<String> headers, Map<String, List<String>> dropdowns) {
        if (dropdowns == null || dropdowns.isEmpty()) return;
        Workbook wb = sheet.getWorkbook();
        DataValidationHelper helper = sheet.getDataValidationHelper();
        Sheet hiddenSheet = null;
        int hiddenSheetColIndex = 0;
        for (Map.Entry<String, List<String>> e : dropdowns.entrySet()) {
            int colIdx = headers.indexOf(e.getKey());
            if (colIdx < 0) continue;
            List<String> opts = e.getValue();
            if (opts == null || opts.isEmpty()) continue;
            // 应用到 1~1000 行
            org.apache.poi.ss.util.CellRangeAddressList range =
                    new org.apache.poi.ss.util.CellRangeAddressList(1, 1000, colIdx, colIdx);
            DataValidationConstraint constraint;
            String joined = String.join(",", opts);
            if (joined.length() <= 240) {
                constraint = helper.createExplicitListConstraint(opts.toArray(new String[0]));
            } else {
                if (hiddenSheet == null) {
                    hiddenSheet = wb.createSheet("_dict");
                    wb.setSheetHidden(wb.getSheetIndex(hiddenSheet), true);
                }
                int col = hiddenSheetColIndex++;
                for (int i = 0; i < opts.size(); i++) {
                    Row r = hiddenSheet.getRow(i);
                    if (r == null) r = hiddenSheet.createRow(i);
                    r.createCell(col).setCellValue(opts.get(i));
                }
                String colLetter = org.apache.poi.ss.util.CellReference.convertNumToColString(col);
                String formula = "_dict!$" + colLetter + "$1:$" + colLetter + "$" + opts.size();
                constraint = helper.createFormulaListConstraint(formula);
            }
            DataValidation validation = helper.createValidation(constraint, range);
            validation.setShowErrorBox(true);
            validation.setSuppressDropDownArrow(true);
            sheet.addValidationData(validation);
        }
    }

    /**
     * 导出带示例数据的模板（无必填标记）
     */
    public static void exportTemplateWithSample(HttpServletResponse response, String fileName,
                                                 List<String> headers, List<String[]> sampleRows) throws IOException {
        exportTemplateWithSample(response, fileName, headers, sampleRows, Collections.emptySet(), Collections.emptyMap());
    }

    /**
     * 导出带示例数据的模板（无下拉）
     */
    public static void exportTemplateWithSample(HttpServletResponse response, String fileName,
                                                 List<String> headers, List<String[]> sampleRows,
                                                 Set<String> requiredColumns) throws IOException {
        exportTemplateWithSample(response, fileName, headers, sampleRows, requiredColumns, Collections.emptyMap());
    }

    /**
     * 导出带示例数据 + 必填星标 + 列下拉校验 的完整模板
     * @param requiredColumns 必填列名集合,模板中这些列表头加 * 标记并标红
     * @param dropdowns key=列名,value=该列下拉可选项
     */
    public static void exportTemplateWithSample(HttpServletResponse response, String fileName,
                                                 List<String> headers, List<String[]> sampleRows,
                                                 Set<String> requiredColumns,
                                                 Map<String, List<String>> dropdowns) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Sheet1");

        // 普通表头样式
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderBottom(BorderStyle.THIN);

        // 必填列表头样式（红色加粗字体）
        CellStyle requiredHeaderStyle = workbook.createCellStyle();
        Font requiredFont = workbook.createFont();
        requiredFont.setBold(true);
        requiredFont.setColor(IndexedColors.RED.getIndex());
        requiredHeaderStyle.setFont(requiredFont);
        requiredHeaderStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        requiredHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        requiredHeaderStyle.setBorderBottom(BorderStyle.THIN);

        // 示例数据样式（浅蓝色背景 + 灰色字体，便于用户识别并替换）
        CellStyle sampleStyle = workbook.createCellStyle();
        Font sampleFont = workbook.createFont();
        sampleFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        sampleStyle.setFont(sampleFont);
        sampleStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        sampleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // 写表头
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = headerRow.createCell(i);
            String headerName = headers.get(i);
            boolean isRequired = requiredColumns != null && requiredColumns.contains(headerName);
            cell.setCellValue(isRequired ? "*" + headerName : headerName);
            cell.setCellStyle(isRequired ? requiredHeaderStyle : headerStyle);
            sheet.setColumnWidth(i, 5000);
        }

        // 写示例数据
        for (int rowIdx = 0; rowIdx < sampleRows.size(); rowIdx++) {
            Row row = sheet.createRow(rowIdx + 1);
            String[] vals = sampleRows.get(rowIdx);
            for (int colIdx = 0; colIdx < vals.length && colIdx < headers.size(); colIdx++) {
                Cell cell = row.createCell(colIdx);
                cell.setCellValue(vals[colIdx] != null ? vals[colIdx] : "");
                cell.setCellStyle(sampleStyle);
            }
        }

        // 应用列下拉数据校验
        applyDropdowns(sheet, headers, dropdowns);

        // 输出
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + URLEncoder.encode(fileName + ".xlsx", StandardCharsets.UTF_8));
        workbook.write(response.getOutputStream());
        workbook.close();
    }

    /**
     * 把模板写入 OutputStream(供 zip 打包使用,不直接写到 response)
     */
    public static void writeTemplateToStream(java.io.OutputStream out, List<String> headers,
                                              List<String[]> sampleRows, Set<String> requiredColumns) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Sheet1");

        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderBottom(BorderStyle.THIN);

        CellStyle requiredHeaderStyle = workbook.createCellStyle();
        Font requiredFont = workbook.createFont();
        requiredFont.setBold(true);
        requiredFont.setColor(IndexedColors.RED.getIndex());
        requiredHeaderStyle.setFont(requiredFont);
        requiredHeaderStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        requiredHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        requiredHeaderStyle.setBorderBottom(BorderStyle.THIN);

        CellStyle sampleStyle = workbook.createCellStyle();
        Font sampleFont = workbook.createFont();
        sampleFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        sampleStyle.setFont(sampleFont);
        sampleStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        sampleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = headerRow.createCell(i);
            String headerName = headers.get(i);
            boolean isRequired = requiredColumns != null && requiredColumns.contains(headerName);
            cell.setCellValue(isRequired ? "*" + headerName : headerName);
            cell.setCellStyle(isRequired ? requiredHeaderStyle : headerStyle);
            sheet.setColumnWidth(i, 5000);
        }

        if (sampleRows != null) {
            for (int rowIdx = 0; rowIdx < sampleRows.size(); rowIdx++) {
                Row row = sheet.createRow(rowIdx + 1);
                String[] vals = sampleRows.get(rowIdx);
                for (int colIdx = 0; colIdx < vals.length && colIdx < headers.size(); colIdx++) {
                    Cell cell = row.createCell(colIdx);
                    cell.setCellValue(vals[colIdx] != null ? vals[colIdx] : "");
                    cell.setCellStyle(sampleStyle);
                }
            }
        }

        workbook.write(out);
        workbook.close();
    }

    /**
     * 清除表头中的必填标记 *，用于导入时匹配
     */
    public static String stripRequired(String header) {
        if (header == null) return null;
        return header.startsWith("*") ? header.substring(1) : header;
    }

    private static final DateTimeFormatter CELL_DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter CELL_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 智能读取单元格值
     * 对日期类型的单元格直接输出标准格式 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss，
     * 避免因 Excel 本地化格式（如"2026年4月9日"或数字序列号）导致后续解析失败。
     */
    private static String readCellValue(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";

        // 公式单元格：取缓存的计算结果值，忽略公式本身
        if (cell.getCellType() == CellType.FORMULA) {
            try {
                CellType cachedType = cell.getCachedFormulaResultType();
                if (cachedType == CellType.STRING) {
                    String val = cell.getStringCellValue();
                    return val != null ? val.trim() : "";
                } else if (cachedType == CellType.NUMERIC) {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        LocalDateTime dt = cell.getLocalDateTimeCellValue();
                        if (dt.getHour() == 0 && dt.getMinute() == 0 && dt.getSecond() == 0) {
                            return dt.toLocalDate().format(CELL_DATE_FMT);
                        }
                        return dt.format(CELL_DATETIME_FMT);
                    }
                    // 直接取缓存的数值结果, 不走 formatter — 没有 FormulaEvaluator 时
                    // formatter 对 FORMULA cell 返回的是公式字符串 "=1/1000",而不是结果 0.001
                    double d = cell.getNumericCellValue();
                    if (d == Math.floor(d) && !Double.isInfinite(d)) {
                        return Long.toString((long) d);
                    }
                    return java.math.BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
                } else if (cachedType == CellType.BOOLEAN) {
                    return cell.getBooleanCellValue() ? "是" : "否";
                } else if (cachedType == CellType.ERROR || cachedType == CellType.BLANK) {
                    return "";
                }
            } catch (Exception e) {
                return "";
            }
            return formatter.formatCellValue(cell).trim();
        }

        // 日期类型单元格
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            try {
                LocalDateTime dt = cell.getLocalDateTimeCellValue();
                if (dt.getHour() == 0 && dt.getMinute() == 0 && dt.getSecond() == 0) {
                    return dt.toLocalDate().format(CELL_DATE_FMT);
                }
                return dt.format(CELL_DATETIME_FMT);
            } catch (Exception e) {
                return formatter.formatCellValue(cell).trim();
            }
        }

        // 数字类型单元格: 直接取真实数值, 绕开显示格式(0 位小数/百分比/千分位/货币 等)
        // 否则 Excel 单元格被设为"整数"格式时, 真实值 0.001 会被显示为 "0",
        // formatCellValue 也只能拿到 "0", 业务侧无法判断是真 0 还是被四舍五入成 0
        if (cell.getCellType() == CellType.NUMERIC) {
            double d = cell.getNumericCellValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                // 整数, 用 long 输出避免 1.0 这种尾巴
                return Long.toString((long) d);
            }
            // 小数: BigDecimal 保留原始精度避免 0.1+0.2 这类浮点漂移
            return java.math.BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
        }

        return formatter.formatCellValue(cell).trim();
    }

    /**
     * 解析导入Excel（动态表头版本）
     * 不校验表头，读取Excel中实际的表头和所有数据行
     * @param file 上传的文件
     * @param fixedHeaderCount 固定列数量（前N列按位置读取，之后的列视为动态扩展列）
     * @return ParseResult 包含实际表头列表和每行数据的Map列表
     */
    public static ParseResult parseImportDynamic(MultipartFile file, int fixedHeaderCount) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要导入的文件");
        }
        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() < 2) {
                throw new BusinessException("文件中没有数据");
            }

            // 读取表头（自动去除必填标记 *）
            Row headerRow = sheet.getRow(0);
            List<String> allHeaders = new ArrayList<>();
            int lastCol = headerRow.getLastCellNum();
            for (int i = 0; i < lastCol; i++) {
                Cell cell = headerRow.getCell(i);
                String val = cell != null ? cell.getStringCellValue().trim() : "";
                allHeaders.add(stripRequired(val));
            }

            // 识别动态列：固定列之后、表头非空的列
            List<String> dynamicHeaders = new ArrayList<>();
            for (int i = fixedHeaderCount; i < allHeaders.size(); i++) {
                String h = allHeaders.get(i);
                if (!h.isEmpty()) {
                    dynamicHeaders.add(h);
                }
            }

            // 解析数据行
            List<Map<String, String>> rows = new ArrayList<>();
            DataFormatter formatter = new DataFormatter();
            for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;
                boolean hasUserInput = false;
                Map<String, String> rowData = new LinkedHashMap<>();
                // 读取所有列（包含固定列和动态列）
                for (int colIdx = 0; colIdx < allHeaders.size(); colIdx++) {
                    String header = allHeaders.get(colIdx);
                    if (header.isEmpty()) continue;
                    Cell cell = row.getCell(colIdx);
                    String val = readCellValue(cell, formatter);
                    rowData.put(header, val);
                    if (!val.isEmpty() && cell != null && cell.getCellType() != CellType.FORMULA) {
                        hasUserInput = true;
                    }
                }
                if (hasUserInput) {
                    rows.add(rowData);
                }
            }
            return new ParseResult(allHeaders, dynamicHeaders, rows);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("文件解析失败: " + e.getMessage());
        }
    }

    /**
     * 动态表头解析结果
     */
    public static class ParseResult {
        private final List<String> allHeaders;
        private final List<String> dynamicHeaders;
        private final List<Map<String, String>> rows;

        public ParseResult(List<String> allHeaders, List<String> dynamicHeaders, List<Map<String, String>> rows) {
            this.allHeaders = allHeaders;
            this.dynamicHeaders = dynamicHeaders;
            this.rows = rows;
        }

        public List<String> getAllHeaders() { return allHeaders; }
        public List<String> getDynamicHeaders() { return dynamicHeaders; }
        public List<Map<String, String>> getRows() { return rows; }
    }
}
