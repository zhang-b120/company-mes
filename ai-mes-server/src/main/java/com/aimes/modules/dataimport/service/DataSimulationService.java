package com.aimes.modules.dataimport.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.modules.dataimport.service.simulation.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据模拟生成协调器 — 将请求路由到各模块Generator
 */
@Service
@RequiredArgsConstructor
public class DataSimulationService {

    private final PlanGenerator planGenerator;
    private final WorkOrderGenerator workOrderGenerator;
    private final WorkReportGenerator workReportGenerator;
    private final ShipmentGenerator shipmentGenerator;
    private final QcTaskGenerator qcTaskGenerator;
    private final OutsourceGenerator outsourceGenerator;
    private final OutsourceReceiptGenerator outsourceReceiptGenerator;
    private final InventoryRebuildGenerator inventoryRebuildGenerator;
    private final MaintenancePlanGenerator maintenancePlanGenerator;
    private final MaintenanceTaskGenerator maintenanceTaskGenerator;
    private final InspectionPlanGenerator inspectionPlanGenerator;
    private final InspectionTaskGenerator inspectionTaskGenerator;
    private final RepairRequestGenerator repairRequestGenerator;
    private final RepairOrderGenerator repairOrderGenerator;

    /** 模块元数据 */
    public static final List<Map<String, Object>> MODULES = List.of(
            module("plan", "生产计划", 1, List.of()),
            module("work-order", "生产工单", 2, List.of("plan")),
            module("work-report", "报工+质检+处置", 3, List.of("work-order")),
            module("outsource", "整单外协", 4, List.of("work-report")),
            module("outsource-receipt", "外协收料", 5, List.of("outsource")),
            module("shipment", "销售发货", 6, List.of("outsource-receipt")),
            module("qc-task", "采购来料质检", 7, List.of()),
            module("inventory", "库存台账", 8, List.of("shipment")),
            module("maint-plan", "保养计划", 9, List.of()),
            module("maint-task", "保养任务", 10, List.of("maint-plan")),
            module("insp-plan", "点检计划", 11, List.of()),
            module("insp-task", "点检任务", 12, List.of("insp-plan")),
            module("repair-request", "报修申请", 13, List.of("insp-task")),
            module("repair-order", "维修工单", 14, List.of("repair-request"))
    );

    private static Map<String, Object> module(String key, String name, int order, List<String> deps) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("name", name);
        m.put("order", order);
        m.put("dependsOn", deps);
        return m;
    }

    /** 生成预览 */
    public Map<String, Object> generate(String module, Map<String, Object> configMap) {
        SimConfig cfg = SimConfig.fromMap(configMap);
        return switch (module) {
            case "plan" -> planGenerator.generate(cfg);
            case "work-order" -> workOrderGenerator.generate(cfg);
            case "work-report" -> workReportGenerator.generate(cfg);
            case "shipment" -> shipmentGenerator.generate(cfg);
            case "qc-task" -> qcTaskGenerator.generate(cfg);
            case "outsource" -> outsourceGenerator.generate(cfg);
            case "outsource-receipt" -> outsourceReceiptGenerator.generate(cfg);
            case "inventory" -> inventoryRebuildGenerator.generate(cfg);
            case "maint-plan" -> maintenancePlanGenerator.generate(cfg);
            case "maint-task" -> maintenanceTaskGenerator.generate(cfg);
            case "insp-plan" -> inspectionPlanGenerator.generate(cfg);
            case "insp-task" -> inspectionTaskGenerator.generate(cfg);
            case "repair-request" -> repairRequestGenerator.generate(cfg);
            case "repair-order" -> repairOrderGenerator.generate(cfg);
            default -> throw new BusinessException("不支持的模块: " + module);
        };
    }

    /** 导出ZIP */
    public void export(String module, HttpServletResponse response) throws IOException {
        switch (module) {
            case "plan" -> planGenerator.exportZip(response);
            case "work-order" -> workOrderGenerator.exportZip(response);
            case "work-report" -> workReportGenerator.exportZip(response);
            case "shipment" -> shipmentGenerator.exportZip(response);
            case "qc-task" -> qcTaskGenerator.exportZip(response);
            case "outsource" -> outsourceGenerator.exportZip(response);
            case "outsource-receipt" -> outsourceReceiptGenerator.exportZip(response);
            case "inventory" -> inventoryRebuildGenerator.exportZip(response);
            case "maint-plan" -> maintenancePlanGenerator.exportZip(response);
            case "maint-task" -> maintenanceTaskGenerator.exportZip(response);
            case "insp-plan" -> inspectionPlanGenerator.exportZip(response);
            case "insp-task" -> inspectionTaskGenerator.exportZip(response);
            case "repair-request" -> repairRequestGenerator.exportZip(response);
            case "repair-order" -> repairOrderGenerator.exportZip(response);
            default -> throw new BusinessException("不支持的模块: " + module);
        }
    }

    /** 导入数据 */
    public Map<String, Object> importData(String module, MultipartFile[] files) {
        return switch (module) {
            case "plan" -> {
                MultipartFile planFile = findFile(files, "plan.");
                MultipartFile itemFile = findFile(files, "plan_item");
                yield planGenerator.importData(planFile, itemFile);
            }
            case "work-order" -> {
                yield workOrderGenerator.importData(
                        findFile(files, "work_order"), findFiles(files, "process_task"),
                        findFiles(files, "bom_snapshot"),
                        findFile(files, "inv_task."), findFiles(files, "inv_task_item"),
                        findFiles(files, "inv_transaction"));
            }
            case "work-report" -> workReportGenerator.importData(
                    findFilesExclude(files, "work_report", "team_work"), findFile(files, "team_work_report"),
                    findFiles(files, "team_report_detail"), findFiles(files, "wage_record"),
                    findFile(files, "qc_task"), findFiles(files, "qc_record"),
                    findFile(files, "qc_disposal"), findFile(files, "defect_record"),
                    findFile(files, "rework_order"), findFile(files, "rework_step"));
            case "shipment" -> shipmentGenerator.importData();
            case "qc-task" -> qcTaskGenerator.importData(
                    findFile(files, "receipt"), findFile(files, "qc_task"), findFiles(files, "qc_record"),
                    findFile(files, "inv_task."), findFiles(files, "inv_task_item"),
                    findFiles(files, "inv_transaction"));
            case "outsource" -> outsourceGenerator.importData(
                    findFile(files, "outsource_request"), findFile(files, "outsource_order"),
                    findFile(files, "outsource_delivery"),
                    findFile(files, "inv_task."), findFiles(files, "inv_task_item"),
                    findFiles(files, "inv_transaction"));
            case "outsource-receipt" -> outsourceReceiptGenerator.importData(
                    findFile(files, "outsource_receipt"),
                    findFile(files, "inv_task."), findFile(files, "inv_task_item"),
                    findFile(files, "inv_transaction"));
            case "inventory" -> inventoryRebuildGenerator.importData();
            case "maint-plan" -> maintenancePlanGenerator.importData();
            case "maint-task" -> maintenanceTaskGenerator.importData();
            case "insp-plan" -> inspectionPlanGenerator.importData();
            case "insp-task" -> inspectionTaskGenerator.importData();
            case "repair-request" -> repairRequestGenerator.importData();
            case "repair-order" -> repairOrderGenerator.importData();
            default -> throw new BusinessException("不支持的模块: " + module);
        };
    }

    /**
     * 从上传的多个文件中按文件名关键字查找
     */
    private MultipartFile findFile(MultipartFile[] files, String keyword) {
        if (files == null || files.length == 0) return null;
        for (MultipartFile f : files) {
            String name = f.getOriginalFilename();
            if (name == null) continue;
            if (name.contains("_" + keyword) || name.contains("_" + keyword.replace(".", ".xlsx"))) {
                return f;
            }
        }
        for (MultipartFile f : files) {
            String name = f.getOriginalFilename();
            if (name != null && name.contains(keyword)) return f;
        }
        return null;
    }

    /** 查找包含keyword但不包含exclude的文件 */
    private MultipartFile findFileExclude(MultipartFile[] files, String keyword, String exclude) {
        if (files == null || files.length == 0) return null;
        for (MultipartFile f : files) {
            String name = f.getOriginalFilename();
            if (name == null) continue;
            if (name.contains(keyword) && !name.contains(exclude)) return f;
        }
        return null;
    }

    /** 查找所有匹配 keyword 的文件(支持分片导出: xxx_part_01.xlsx ~ xxx_part_NN.xlsx 一起返回) */
    private MultipartFile[] findFiles(MultipartFile[] files, String keyword) {
        if (files == null || files.length == 0) return new MultipartFile[0];
        return java.util.Arrays.stream(files)
                .filter(f -> f != null && f.getOriginalFilename() != null
                        && f.getOriginalFilename().contains(keyword))
                .toArray(MultipartFile[]::new);
    }

    /** 查找所有匹配 keyword 但不含 exclude 的文件 */
    private MultipartFile[] findFilesExclude(MultipartFile[] files, String keyword, String exclude) {
        if (files == null || files.length == 0) return new MultipartFile[0];
        return java.util.Arrays.stream(files)
                .filter(f -> f != null && f.getOriginalFilename() != null
                        && f.getOriginalFilename().contains(keyword)
                        && !f.getOriginalFilename().contains(exclude))
                .toArray(MultipartFile[]::new);
    }
}
