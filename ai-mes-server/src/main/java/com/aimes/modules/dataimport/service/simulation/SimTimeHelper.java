package com.aimes.modules.dataimport.service.simulation;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 工作日/时间/人员选取工具
 */
public class SimTimeHelper {

    private SimTimeHelper() {}

    /**
     * 获取 [from, to] 之间的工作日列表
     */
    public static List<LocalDate> getWorkingDays(LocalDate from, LocalDate to, SimConfig cfg) {
        if (from.isAfter(to)) to = from.plusDays(14);
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (cfg.isSkipWeekends() && (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY))
                continue;
            if (cfg.isSkipHolidays() && cfg.getHolidays().contains(d))
                continue;
            days.add(d);
        }
        return days;
    }

    /**
     * 在指定日期之后推迟 N 个工作日（N为负数时向前推）
     */
    public static LocalDate addWorkingDays(LocalDate from, int days, SimConfig cfg) {
        if (days == 0) return from;
        int step = days > 0 ? 1 : -1;
        int absDays = Math.abs(days);
        LocalDate d = from;
        int count = 0;
        while (count < absDays) {
            d = d.plusDays(step);
            if (cfg.isSkipWeekends() && (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY))
                continue;
            if (cfg.isSkipHolidays() && cfg.getHolidays().contains(d))
                continue;
            count++;
        }
        return d;
    }

    /**
     * 将日期调到最近的工作日（向后）
     */
    public static LocalDate snapToWorkingDay(LocalDate date, SimConfig cfg) {
        LocalDate d = date;
        for (int i = 0; i < 30; i++) {
            if (cfg.isSkipWeekends() && (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY)) {
                d = d.plusDays(1);
                continue;
            }
            if (cfg.isSkipHolidays() && cfg.getHolidays().contains(d)) {
                d = d.plusDays(1);
                continue;
            }
            return d;
        }
        return date; // fallback
    }

    /**
     * 在指定日期的工作时间范围内生成随机时间。
     * 硬约束:不生成晚于"现在"的时间。无法产生合法值时返回 null,调用方必须跳过该记录。
     */
    public static LocalDateTime randomTime(LocalDate date, SimConfig cfg) {
        if (date == null) return null;
        java.time.LocalDate today = LocalDate.now();
        // 未来日期直接 null
        if (date.isAfter(today)) return null;

        int s = cfg.getStartHour() * 60 + cfg.getStartMin();
        int e = cfg.getEndHour() * 60 + cfg.getEndMin();
        // 如果 date 是今天,用 min(工作结束, 当前时刻) 作为上界
        if (date.isEqual(today)) {
            java.time.LocalTime now = java.time.LocalTime.now();
            int nowMinutes = now.getHour() * 60 + now.getMinute();
            e = Math.min(e, nowMinutes);
        }
        if (e <= s) return null; // 工作开始时间都还没到 → 今天无法产生合法值
        int m = ThreadLocalRandom.current().nextInt(s, e);
        LocalDateTime result = date.atTime(m / 60, m % 60, ThreadLocalRandom.current().nextInt(60));
        if (result.isAfter(LocalDateTime.now())) return null;
        return cfg.clampToCutoff(result);
    }

    /**
     * 在 baseTime 之后同一天内生成一个稍晚的时间（间隔10~60分钟）。
     * 硬约束:不生成晚于"现在"的时间。无法产生合法值时返回 null。
     */
    public static LocalDateTime laterTime(LocalDateTime baseTime, SimConfig cfg) {
        if (baseTime == null) return null;
        int minutes = ThreadLocalRandom.current().nextInt(10, 61);
        LocalDateTime later = baseTime.plusMinutes(minutes);
        LocalDateTime dayEnd = baseTime.toLocalDate().atTime(cfg.getEndHour(), cfg.getEndMin());
        if (later.isAfter(dayEnd)) later = dayEnd.minusMinutes(ThreadLocalRandom.current().nextInt(1, 10));
        if (later.isBefore(baseTime)) later = baseTime.plusMinutes(5);
        if (later.isAfter(LocalDateTime.now())) return null;
        return cfg.clampToCutoff(later);
    }

    /**
     * 格式化 LocalDateTime 为 Excel 字符串
     */
    public static String fmtDateTime(LocalDateTime dt) {
        return dt != null ? dt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "";
    }

    /**
     * 格式化 LocalDate 为 Excel 字符串
     */
    public static String fmtDate(LocalDate d) {
        return d != null ? d.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE) : "";
    }

    /**
     * Sheet 描述: entryName + headers + rows + 可选下拉框配置
     */
    public static class SheetSpec {
        public final String entryName;
        public final java.util.List<String> headers;
        public final java.util.List<java.util.List<Object>> rows;
        public final java.util.Map<Integer, java.util.List<String>> dropdowns;
        public SheetSpec(String entryName, java.util.List<String> headers,
                         java.util.List<java.util.List<Object>> rows) {
            this(entryName, headers, rows, null);
        }
        public SheetSpec(String entryName, java.util.List<String> headers,
                         java.util.List<java.util.List<Object>> rows,
                         java.util.Map<Integer, java.util.List<String>> dropdowns) {
            this.entryName = entryName; this.headers = headers; this.rows = rows; this.dropdowns = dropdowns;
        }
    }

    /**
     * 并行 + 大表分片写 ZIP: 行数 > threshold 的 sheet 切 parts 段, 命名 xxx_part_NN.xlsx;
     * 所有段并行写到 ByteArrayOutputStream, 主线程按顺序灌进 ZipOutputStream
     */
    public static void parallelSplitWriteToZip(java.util.zip.ZipOutputStream zos,
                                               java.util.List<SheetSpec> sheets,
                                               int splitThreshold, int splitParts) throws java.io.IOException {
        // 切片
        java.util.List<SheetSpec> all = new ArrayList<>();
        for (SheetSpec s : sheets) {
            if (s == null || s.rows == null) continue;
            if (s.rows.size() > splitThreshold) {
                int per = (s.rows.size() + splitParts - 1) / splitParts;
                String base = s.entryName.endsWith(".xlsx")
                        ? s.entryName.substring(0, s.entryName.length() - 5) : s.entryName;
                for (int i = 0; i < splitParts; i++) {
                    int from = i * per;
                    int to = Math.min(from + per, s.rows.size());
                    if (from >= to) break;
                    String partName = String.format("%s_part_%02d.xlsx", base, i + 1);
                    all.add(new SheetSpec(partName, s.headers, s.rows.subList(from, to), s.dropdowns));
                }
            } else {
                all.add(s);
            }
        }
        if (all.isEmpty()) return;

        // 并行
        int parallelism = Math.min(all.size(),
                Math.max(4, Runtime.getRuntime().availableProcessors()));
        java.util.concurrent.ExecutorService executor =
                java.util.concurrent.Executors.newFixedThreadPool(parallelism);
        java.util.List<java.util.concurrent.Future<byte[]>> futures = new ArrayList<>();
        for (SheetSpec s : all) {
            futures.add(executor.submit(() -> {
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                if (s.dropdowns != null && !s.dropdowns.isEmpty()) {
                    writeExcelWithDropdowns(bos, s.headers, s.rows, s.dropdowns);
                } else {
                    writeExcelToStream(bos, s.headers, s.rows);
                }
                return bos.toByteArray();
            }));
        }
        executor.shutdown();

        for (int i = 0; i < all.size(); i++) {
            byte[] data;
            try { data = futures.get(i).get(); }
            catch (Exception e) { throw new java.io.IOException("并行写xlsx失败: " + e.getMessage(), e); }
            zos.putNextEntry(new java.util.zip.ZipEntry(all.get(i).entryName));
            zos.write(data);
            zos.closeEntry();
        }
    }

    /**
     * 将数据写入Excel输出流（供ZIP导出使用）
     */
    public static void writeExcelToStream(java.io.OutputStream os, java.util.List<String> headers,
                                           java.util.List<java.util.List<Object>> data) throws java.io.IOException {
        var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        var sheet = workbook.createSheet("Sheet1");

        var headerStyle = workbook.createCellStyle();
        var headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

        var headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            var cell = headerRow.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 5000);
        }

        for (int ri = 0; ri < data.size(); ri++) {
            var row = sheet.createRow(ri + 1);
            var rowData = data.get(ri);
            for (int ci = 0; ci < rowData.size(); ci++) {
                var cell = row.createCell(ci);
                Object val = rowData.get(ci);
                if (val == null) cell.setCellValue("");
                else if (val instanceof Number) cell.setCellValue(((Number) val).doubleValue());
                else cell.setCellValue(val.toString());
            }
        }

        workbook.write(os);
        workbook.close();
    }

    /**
     * 带下拉框数据验证的Excel写入
     * @param dropdowns key=列索引(0-based), value=下拉选项列表
     */
    public static void writeExcelWithDropdowns(java.io.OutputStream os, java.util.List<String> headers,
                                                java.util.List<java.util.List<Object>> data,
                                                java.util.Map<Integer, java.util.List<String>> dropdowns) throws java.io.IOException {
        var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        var sheet = workbook.createSheet("Sheet1");

        var headerStyle = workbook.createCellStyle();
        var headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

        var headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            var cell = headerRow.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 5000);
        }

        for (int ri = 0; ri < data.size(); ri++) {
            var row = sheet.createRow(ri + 1);
            var rowData = data.get(ri);
            for (int ci = 0; ci < rowData.size(); ci++) {
                var cell = row.createCell(ci);
                Object val = rowData.get(ci);
                if (val == null) cell.setCellValue("");
                else if (val instanceof Number) cell.setCellValue(((Number) val).doubleValue());
                else cell.setCellValue(val.toString());
            }
        }

        // 添加下拉框数据验证（用隐藏Sheet作为数据源，突破255字符限制）
        if (dropdowns != null && !dropdowns.isEmpty()) {
            for (var entry : dropdowns.entrySet()) {
                int colIdx = entry.getKey();
                java.util.List<String> options = entry.getValue();
                if (options == null || options.isEmpty()) continue;

                // 创建隐藏的数据源Sheet
                String refSheetName = "_ref_col" + colIdx;
                var refSheet = workbook.createSheet(refSheetName);
                for (int i = 0; i < options.size(); i++) {
                    refSheet.createRow(i).createCell(0).setCellValue(options.get(i));
                }
                // 隐藏数据源Sheet
                workbook.setSheetHidden(workbook.getSheetIndex(refSheetName), true);

                // 创建名称引用
                var name = workbook.createName();
                name.setNameName("_list_col" + colIdx);
                name.setRefersToFormula(refSheetName + "!$A$1:$A$" + options.size());

                // 对数据行添加验证
                var dvHelper = new org.apache.poi.xssf.usermodel.XSSFDataValidationHelper(
                        (org.apache.poi.xssf.usermodel.XSSFSheet) sheet);
                var dvConstraint = dvHelper.createFormulaListConstraint("_list_col" + colIdx);
                var addressList = new org.apache.poi.ss.util.CellRangeAddressList(
                        1, Math.max(1, data.size()), colIdx, colIdx);
                var dv = dvHelper.createValidation(dvConstraint, addressList);
                dv.setShowErrorBox(true);
                dv.setErrorStyle(org.apache.poi.ss.usermodel.DataValidation.ErrorStyle.WARNING);
                dv.createErrorBox("输入有误", "请从下拉列表中选择");
                sheet.addValidationData(dv);
            }
        }

        workbook.write(os);
        workbook.close();
    }
}
