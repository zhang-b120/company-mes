package com.aimes.modules.eam.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.common.utils.ExcelUtil;
import com.aimes.modules.eam.entity.EamEquipment;
import com.aimes.modules.eam.entity.EamEquipmentAlarmLog;
import com.aimes.modules.eam.entity.EamEquipmentStatus;
import com.aimes.modules.eam.service.EamEquipmentService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/eam/equipment")
@RequiredArgsConstructor
public class EamEquipmentController {

    private final EamEquipmentService service;

    @GetMapping("/list")
    public R<List<EamEquipment>> list(@RequestParam(required = false) String category) {
        return R.ok(service.listByCategory(category));
    }

    @GetMapping("/page")
    public R<PageResult<EamEquipment>> getPage(@RequestParam(required = false) String equipmentNo,
                                                @RequestParam(required = false) String equipmentName,
                                                @RequestParam(required = false) String category,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) Long deptId,
                                                @RequestParam(defaultValue = "1") int pageNum,
                                                @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(service.getPage(equipmentNo, equipmentName, category, status, deptId, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<EamEquipment> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @PostMapping
    public R<Void> create(@RequestBody EamEquipment equipment) {
        service.create(equipment);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody EamEquipment equipment) {
        service.update(id, equipment);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/enable")
    public R<Void> enable(@PathVariable Long id) {
        service.enable(id);
        return R.ok();
    }

    @PostMapping("/{id}/disable")
    public R<Void> disable(@PathVariable Long id, @RequestBody Map<String, String> body) {
        service.disable(id, body.get("targetStatus"));
        return R.ok();
    }

    @GetMapping("/reminder")
    public R<List<EamEquipment>> reminder(@RequestParam(defaultValue = "7") int days) {
        return R.ok(service.getMaintenanceReminder(days));
    }

    @GetMapping("/statistics")
    public R<Map<String, Object>> statistics() {
        return R.ok(service.getStatistics());
    }

    // ========== 运行状态 F2 ==========

    @GetMapping("/{id}/status")
    public R<EamEquipmentStatus> getStatus(@PathVariable Long id) {
        return R.ok(service.getStatus(id));
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody EamEquipmentStatus payload) {
        service.updateStatus(id, payload);
        return R.ok();
    }

    @GetMapping("/{id}/alarm-logs")
    public R<List<EamEquipmentAlarmLog>> alarmLogs(@PathVariable Long id) {
        return R.ok(service.getAlarmLogs(id));
    }

    // ========== 导入/模板 ==========

    private static final List<String> IMPORT_HEADERS = Arrays.asList(
            "设备编号", "设备名称", "型号", "设备类型", "位置", "购置日期(yyyy-MM-dd)",
            "启用日期(yyyy-MM-dd)", "维护周期(天)", "设备价值", "折旧年限",
            "规格", "参数", "备注");

    /** 必填列(模板表头会加 * 红色标记) */
    private static final java.util.Set<String> IMPORT_REQUIRED = java.util.Set.of("设备名称", "设备类型");

    /** 示例数据(模板蓝色背景,用户可参考后替换) */
    private static final List<String[]> IMPORT_SAMPLES = Arrays.asList(
            new String[]{"EQ20260101", "数控车床A1", "CK6140", "生产设备", "1号车间A区",
                    "2024-03-15", "2024-04-01", "180", "350000", "10",
                    "床身长1500mm 主轴转速8000rpm", "三相380V 7.5kW", "示例:可保留备注"},
            new String[]{"", "高精度三坐标测量仪", "ZEISS-Contura", "检测设备", "质检中心",
                    "2025-06-20", "2025-07-01", "365", "880000", "8",
                    "测量范围1000×1200×600", "精度±2μm", "编号留空自动生成"}
    );

    @GetMapping("/import-template")
    public void importTemplate(HttpServletResponse response) throws IOException {
        Map<String, List<String>> dropdowns = new java.util.HashMap<>();
        dropdowns.put("设备类型", EQUIPMENT_CATEGORY_OPTIONS);
        ExcelUtil.exportTemplateWithSample(response, "设备导入模板",
                IMPORT_HEADERS, IMPORT_SAMPLES, IMPORT_REQUIRED, dropdowns);
    }

    /** 设备类型下拉选项(与前端 EQUIPMENT_CATEGORY_OPTIONS 保持一致) */
    private static final List<String> EQUIPMENT_CATEGORY_OPTIONS = Arrays.asList(
            "生产设备", "检测设备", "包装设备", "运输设备",
            "辅助设备", "加工设备", "切割设备", "成型设备");

    @PostMapping("/import")
    public R<String> importEquipment(@RequestParam("file") MultipartFile file) {
        List<Map<String, String>> rows = ExcelUtil.parseImport(file, IMPORT_HEADERS);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        int success = 0;
        int rowNum = 1;
        StringBuilder errors = new StringBuilder();
        for (Map<String, String> row : rows) {
            rowNum++;
            try {
                EamEquipment e = new EamEquipment();
                e.setEquipmentNo(trimToNull(row.get("设备编号")));
                e.setEquipmentName(trimToNull(row.get("设备名称")));
                e.setModel(trimToNull(row.get("型号")));
                // 兼容旧模板"分类"列
                String category = trimToNull(row.get("设备类型"));
                if (category == null) category = trimToNull(row.get("分类"));
                if (category == null) {
                    throw new IllegalArgumentException("设备类型必填");
                }
                e.setCategory(category);
                e.setLocation(trimToNull(row.get("位置")));
                String purchaseDate = trimToNull(row.get("购置日期(yyyy-MM-dd)"));
                if (purchaseDate != null) e.setPurchaseDate(LocalDate.parse(purchaseDate, fmt));
                String enableDate = trimToNull(row.get("启用日期(yyyy-MM-dd)"));
                if (enableDate != null) e.setEnableDate(LocalDate.parse(enableDate, fmt));
                String cycle = trimToNull(row.get("维护周期(天)"));
                if (cycle != null) e.setMaintenanceCycle(Integer.parseInt(cycle));
                String value = trimToNull(row.get("设备价值"));
                if (value != null) e.setEquipmentValue(new BigDecimal(value));
                String depYears = trimToNull(row.get("折旧年限"));
                if (depYears != null) e.setDepreciationYears(Integer.parseInt(depYears));
                e.setSpecification(trimToNull(row.get("规格")));
                e.setParameters(trimToNull(row.get("参数")));
                e.setRemark(trimToNull(row.get("备注")));
                service.create(e);
                success++;
            } catch (Exception ex) {
                errors.append("第").append(rowNum).append("行: ").append(ex.getMessage()).append("; ");
            }
        }
        String msg = "成功导入 " + success + " 条";
        if (errors.length() > 0) {
            msg += "，失败: " + errors.toString();
        }
        return R.ok(msg);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
