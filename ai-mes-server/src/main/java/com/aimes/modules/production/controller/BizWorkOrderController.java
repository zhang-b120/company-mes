package com.aimes.modules.production.controller;

import com.aimes.common.result.R;
import com.aimes.common.result.PageResult;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.mrp.entity.BizProductionPlanAttachment;
import com.aimes.modules.mrp.entity.BizProductionPlanItem;
import com.aimes.modules.mrp.mapper.BizProductionPlanAttachmentMapper;
import com.aimes.modules.mrp.mapper.BizProductionPlanItemMapper;
import com.aimes.modules.outsource.service.OutsourceChainService;
import com.aimes.modules.production.entity.BizWoBomSnapshot;
import com.aimes.modules.production.entity.BizWoChange;
import com.aimes.modules.production.entity.BizWoProcessTask;
import com.aimes.modules.production.entity.BizWorkOrder;
import com.aimes.modules.production.mapper.BizWoBomSnapshotMapper;
import com.aimes.modules.production.mapper.BizWoProcessTaskMapper;
import com.aimes.modules.production.mapper.BizWorkOrderMapper;
import com.aimes.modules.inventory.service.ProductionStockInService;
import com.aimes.modules.production.service.BizWorkOrderService;
import com.aimes.modules.quality.entity.BizQcTemplate;
import com.aimes.modules.quality.entity.BizQcTemplateItem;
import com.aimes.modules.quality.mapper.BizQcTemplateItemMapper;
import com.aimes.modules.quality.mapper.BizQcTemplateMapper;
import com.aimes.modules.system.entity.SysUser;
import com.aimes.modules.system.entity.SysWarehouse;
import com.aimes.modules.system.mapper.SysUserMapper;
import com.aimes.modules.system.mapper.SysWarehouseMapper;
import com.aimes.modules.workreport.entity.BizTeamReportDetail;
import com.aimes.modules.workreport.entity.BizTeamWorkReport;
import com.aimes.modules.workreport.entity.BizWorkReport;
import com.aimes.modules.workreport.service.WorkReportService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/production/work-orders")
@RequiredArgsConstructor
public class BizWorkOrderController {

    private final BizWorkOrderService workOrderService;
    private final WorkReportService workReportService;
    private final BizWoBomSnapshotMapper bomSnapshotMapper;
    private final BizWoProcessTaskMapper processTaskMapper;
    private final BizMaterialMapper materialMapper;
    private final SysWarehouseMapper warehouseMapper;
    private final OutsourceChainService outsourceChainService;
    private final SysUserMapper userMapper;
    private final BizQcTemplateMapper qcTemplateMapper;
    private final BizQcTemplateItemMapper qcTemplateItemMapper;
    private final BizWorkOrderMapper workOrderMapper;
    private final BizProductionPlanItemMapper planItemMapper;
    private final BizProductionPlanAttachmentMapper planAttachmentMapper;
    private final ProductionStockInService productionStockInService;

    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String woNo,
            @RequestParam(required = false) String productNo,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String spec,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam java.util.Map<String, String> allParams) {
        // 数据权限：self 范围只查看被指派到自己（工序任务中 assignedUserId = 当前用户）的工单
        Long assignedUserId = null;
        com.aimes.common.utils.SecurityUtil.DataScopeContext ctx = com.aimes.common.utils.SecurityUtil.getDataScopeContext();
        if (ctx != null && "self".equals(ctx.getScope())) {
            assignedUserId = com.aimes.common.utils.SecurityUtil.getCurrentUserId();
        }
        java.util.Map<String, String> extFieldFilters = new java.util.HashMap<>();
        allParams.forEach((k, v) -> {
            if (k.startsWith("ext_") && org.springframework.util.StringUtils.hasText(v)) {
                extFieldFilters.put(k.substring(4), v);
            }
        });
        return R.ok(workOrderService.getPage(keyword, woNo, productNo, productName, status, spec, orderNo, orderId, overdue, assignedUserId, extFieldFilters, pageNum, pageSize));
    }

    @GetMapping("/statistics")
    public R<Map<String, Object>> statistics() {
        Long assignedUserId = null;
        com.aimes.common.utils.SecurityUtil.DataScopeContext ctx = com.aimes.common.utils.SecurityUtil.getDataScopeContext();
        if (ctx != null && "self".equals(ctx.getScope())) {
            assignedUserId = com.aimes.common.utils.SecurityUtil.getCurrentUserId();
        }
        return R.ok(workOrderService.getStatistics(assignedUserId));
    }

    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        Long assignedUserId = null;
        com.aimes.common.utils.SecurityUtil.DataScopeContext ctx = com.aimes.common.utils.SecurityUtil.getDataScopeContext();
        if (ctx != null && "self".equals(ctx.getScope())) {
            assignedUserId = com.aimes.common.utils.SecurityUtil.getCurrentUserId();
        }
        return R.ok(workOrderService.getStatistics(assignedUserId));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(workOrderService.getDetail(id));
    }

    /**
     * 通过工单编号查询详情（供关联单据跳转使用）
     */
    @GetMapping("/by-no")
    public R<Map<String, Object>> getDetailByWoNo(@RequestParam String woNo) {
        return R.ok(workOrderService.getDetailByWoNo(woNo));
    }

    @PostMapping
    public R<Map<String, Object>> create(@RequestBody BizWorkOrder wo) {
        Map<String, Object> result = workOrderService.createWorkOrder(wo);
        return R.ok(result);
    }

    @PostMapping("/{id}/append")
    public R<Long> appendWorkOrder(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BigDecimal planQty = body.get("planQty") != null ? new BigDecimal(body.get("planQty").toString()) : null;
        Long newId = workOrderService.appendWorkOrder(id, planQty);
        return R.ok(newId);
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        workOrderService.updateStatus(id, body.get("status"));
        return R.ok();
    }

    @PostMapping("/{id}/start")
    public R<Void> startProduction(@PathVariable Long id) {
        workOrderService.startProduction(id);
        return R.ok();
    }

    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        workOrderService.cancelWorkOrder(id, reason);
        return R.ok();
    }

    @GetMapping("/{woId}/tasks")
    public R<List<BizWoProcessTask>> getTasks(@PathVariable Long woId) {
        return R.ok(workOrderService.getTasks(woId));
    }

    @PutMapping("/{woId}/tasks/{taskId}/assign")
    public R<Void> assignTask(@PathVariable Long woId, @PathVariable Long taskId,
                               @RequestBody Map<String, Long> body) {
        Long userId = body.get("userId");
        Long teamId = body.get("teamId");
        workOrderService.assignTask(taskId, userId, teamId);
        return R.ok();
    }

    @PostMapping("/{woId}/tasks")
    public R<Void> addTask(@PathVariable Long woId, @RequestBody BizWoProcessTask task) {
        workOrderService.addTask(woId, task);
        return R.ok();
    }

    @PutMapping("/{woId}/tasks/{taskId}")
    public R<Void> updateTask(@PathVariable Long woId, @PathVariable Long taskId,
                               @RequestBody BizWoProcessTask task) {
        workOrderService.updateTask(taskId, task);
        return R.ok();
    }

    @DeleteMapping("/{woId}/tasks/{taskId}")
    public R<Void> deleteTask(@PathVariable Long woId, @PathVariable Long taskId) {
        workOrderService.deleteTask(taskId);
        return R.ok();
    }

    @PutMapping("/{woId}/tasks/reorder")
    public R<Void> reorderTasks(@PathVariable Long woId, @RequestBody List<Long> taskIds) {
        workOrderService.reorderTasks(woId, taskIds);
        return R.ok();
    }

    @GetMapping("/{woId}/bom")
    public R<List<Map<String, Object>>> getBom(@PathVariable Long woId) {
        List<BizWoBomSnapshot> snapshots = bomSnapshotMapper.selectList(
                new LambdaQueryWrapper<BizWoBomSnapshot>()
                        .eq(BizWoBomSnapshot::getWorkOrderId, woId)
                        .orderByAsc(BizWoBomSnapshot::getId));
        // 批量加载物料信息,避免前端拿不到名称/规格/单位
        java.util.Set<Long> matIds = snapshots.stream()
                .map(BizWoBomSnapshot::getMaterialId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Map<Long, com.aimes.modules.master.entity.BizMaterial> matMap = matIds.isEmpty()
                ? new java.util.HashMap<>()
                : materialMapper.selectBatchIds(matIds).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                com.aimes.modules.master.entity.BizMaterial::getId, m -> m, (a, b) -> a));
        List<Map<String, Object>> result = snapshots.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            com.aimes.modules.master.entity.BizMaterial mat = s.getMaterialId() != null ? matMap.get(s.getMaterialId()) : null;
            m.put("id", s.getId());
            m.put("workOrderId", s.getWorkOrderId());
            m.put("materialId", s.getMaterialId());
            m.put("materialNo", mat != null ? mat.getMaterialNo() : "");
            m.put("materialName", mat != null ? mat.getMaterialName() : "");
            m.put("spec", mat != null ? mat.getSpec() : "");
            m.put("unit", mat != null ? mat.getUnit() : "");
            m.put("requiredQty", s.getRequiredQty());
            m.put("issuedQty", s.getIssuedQty());
            m.put("issuedTaskQty", s.getIssuedTaskQty());
            m.put("returnedQty", s.getReturnedQty());
            m.put("returnedTaskQty", s.getReturnedTaskQty());
            return m;
        }).collect(java.util.stream.Collectors.toList());
        return R.ok(result);
    }

    @GetMapping("/{woId}/changes")
    public R<List<BizWoChange>> getChanges(@PathVariable Long woId) {
        return R.ok(workOrderService.getChanges(woId));
    }

    @PostMapping("/{woId}/changes")
    public R<Void> createChange(@PathVariable Long woId, @RequestBody Map<String, Object> body) {
        BizWoChange change = new BizWoChange();
        change.setWorkOrderId(woId);
        change.setChangeType(body.get("changeType") != null ? body.get("changeType").toString() : null);
        change.setChangeReason(body.get("reason") != null ? body.get("reason").toString() : null);
        // 把变更详情(新值)整体序列化进 changeContent,供 service 解析
        try {
            change.setChangeContent(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body));
        } catch (Exception ignore) {}
        workOrderService.createChange(change);
        return R.ok();
    }

    /**
     * 获取领料状态(BOM快照 + 物料信息 + 仓库信息)
     */
    @GetMapping("/{woId}/issue-status")
    public R<Map<String, Object>> getIssueStatus(@PathVariable Long woId) {
        return R.ok(workOrderService.getIssueStatus(woId));
    }

    /**
     * 领料：生成 production_out 库存任务
     */
    @PostMapping("/{woId}/issue-material")
    public R<Void> issueMaterial(@PathVariable Long woId, @RequestBody List<Map<String, Object>> items) {
        workOrderService.issueMaterial(woId, items);
        return R.ok();
    }

    /**
     * 无 BOM 工单领料前的"缺料预检": 不写库, 只返回缺料明细 (前端用来弹请购对话框).
     */
    @PostMapping("/{woId}/issue-shortage-check")
    public R<List<Map<String, Object>>> checkIssueShortage(@PathVariable Long woId,
                                                            @RequestBody List<Map<String, Object>> items) {
        return R.ok(workOrderService.checkIssueShortage(woId, items));
    }

    /**
     * 无 BOM 工单缺料后, 由生产人员确认需求日期 + 用途, 批量生成采购申请.
     * body = { items: [...缺料行], expectedDate: "YYYY-MM-DD", purpose: "用途说明" }
     */
    @PostMapping("/{woId}/generate-purchase-requests")
    public R<Integer> generatePurchaseRequestsForShortage(@PathVariable Long woId,
                                                          @RequestBody Map<String, Object> body) {
        return R.ok(workOrderService.generatePurchaseRequestsForShortage(woId, body));
    }

    /**
     * 采购进度: 该工单触发的所有采购申请, 各自关联的采购订单和收料情况.
     */
    @GetMapping("/{woId}/purchase-progress")
    public R<List<Map<String, Object>>> getPurchaseProgress(@PathVariable Long woId) {
        return R.ok(workOrderService.getPurchaseProgress(woId));
    }

    /**
     * 退料：生成 production_return 库存任务
     */
    @PostMapping("/{woId}/return-material")
    public R<Void> returnMaterial(@PathVariable Long woId, @RequestBody List<Map<String, Object>> items) {
        workOrderService.returnMaterial(woId, items);
        return R.ok();
    }

    /**
     * 工序报工(Web端入口)
     * 支持个人报工和班组报工:
     * - 个人: { qualifiedQty, defectQty, workHours, remark }
     * - 班组: { qualifiedQty, defectQty, workHours, remark, assignType:"team", teamId, members:[{userId,weight}] }
     */
    @PostMapping("/{woId}/tasks/{taskId}/report")
    public R<Void> reportTask(@PathVariable Long woId, @PathVariable Long taskId,
                               @RequestBody Map<String, Object> body) {
        java.math.BigDecimal qualifiedQty = body.get("qualifiedQty") != null
                ? new java.math.BigDecimal(body.get("qualifiedQty").toString()) : java.math.BigDecimal.ZERO;
        java.math.BigDecimal defectQty = body.get("defectQty") != null
                ? new java.math.BigDecimal(body.get("defectQty").toString()) : java.math.BigDecimal.ZERO;

        boolean isTeam = "team".equals(body.get("assignType"));

        // 1. 提交个人报工记录（处理任务进度、工序流转、质检等）
        BizWorkReport report = new BizWorkReport();
        report.setWorkOrderId(woId);
        report.setTaskId(taskId);
        report.setQualifiedQty(qualifiedQty);
        report.setDefectQty(defectQty);
        if (body.get("workHours") != null) {
            report.setWorkHours(new java.math.BigDecimal(body.get("workHours").toString()));
        }
        report.setRemark(body.get("remark") != null ? body.get("remark").toString() : null);
        report.setReporterId(com.aimes.common.utils.SecurityUtil.getCurrentUserId());
        report.setReportType(isTeam ? "team" : "personal");
        // 报工模式: normal(正常) / three_machine(三机), 默认 normal
        String wageMode = body.get("wageMode") != null ? body.get("wageMode").toString() : "normal";
        report.setWageMode(wageMode);
        workReportService.submitPersonalReport(report);

        // 2. 班组模式: 额外提交班组报工记录（按权重分配工资）
        if (isTeam && body.get("teamId") != null) {
            BizWoProcessTask task = processTaskMapper.selectById(taskId);
            java.math.BigDecimal totalWage = java.math.BigDecimal.ZERO;
            if (task != null && task.getPiecePrice() != null && qualifiedQty.compareTo(java.math.BigDecimal.ZERO) > 0) {
                totalWage = qualifiedQty.multiply(task.getPiecePrice());
            }

            BizTeamWorkReport teamReport = new BizTeamWorkReport();
            teamReport.setTaskId(taskId);
            teamReport.setWorkOrderId(woId);
            teamReport.setTeamId(Long.valueOf(body.get("teamId").toString()));
            teamReport.setLeaderId(com.aimes.common.utils.SecurityUtil.getCurrentUserId());
            teamReport.setQualifiedQty(qualifiedQty);
            teamReport.setDefectQty(defectQty);
            teamReport.setTotalWage(totalWage);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> memberMaps = (List<Map<String, Object>>) body.get("members");
            java.util.ArrayList<BizTeamReportDetail> details = new java.util.ArrayList<>();
            if (memberMaps != null) {
                for (Map<String, Object> mm : memberMaps) {
                    BizTeamReportDetail d = new BizTeamReportDetail();
                    d.setUserId(Long.valueOf(mm.get("userId").toString()));
                    d.setWeight(new java.math.BigDecimal(mm.get("weight").toString()));
                    details.add(d);
                }
            }
            workReportService.submitTeamReport(teamReport, details);
        }
        return R.ok();
    }

    /**
     * 获取报工数量限制
     * 首道工序: 根据BOM已领料比值计算最大可报工数
     * 非首道工序: 不能超过上道工序已报工合格数
     */
    @GetMapping("/{woId}/tasks/{taskId}/report-limit")
    public R<Map<String, Object>> getReportLimit(@PathVariable Long woId, @PathVariable Long taskId) {
        return R.ok(workReportService.getReportLimit(woId, taskId));
    }

    /**
     * 获取工序质检要求（质检模板 + 检验项）
     * 优先使用工序任务上从生产计划同步过来的 qcRequirements，其次用模板默认值
     */
    @GetMapping("/{woId}/tasks/{taskId}/qc-requirements")
    @SuppressWarnings("unchecked")
    public R<Map<String, Object>> getQcRequirements(@PathVariable Long woId, @PathVariable Long taskId) {
        BizWoProcessTask task = processTaskMapper.selectById(taskId);
        if (task == null || task.getQcTemplateId() == null) {
            return R.ok(Map.of("hasQc", false));
        }
        BizQcTemplate template = qcTemplateMapper.selectById(task.getQcTemplateId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hasQc", true);
        if (template != null) {
            result.put("templateName", template.getTemplateName());
            result.put("templateCode", template.getTemplateCode());
            result.put("sampleMode", template.getSampleMode());
            result.put("sampleRate", template.getSampleRate());
            result.put("aqlLevel", template.getAqlLevel());
        }

        // 优先使用生产计划同步过来的质检要求（含用户自定义的标准值、公差等）
        List<Map<String, Object>> itemList = null;
        if (task.getQcRequirements() != null && !task.getQcRequirements().isBlank()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                List<Map<String, Object>> planItems = objectMapper.readValue(task.getQcRequirements(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
                if (planItems != null && !planItems.isEmpty()) {
                    itemList = planItems;
                }
            } catch (Exception ignored) {
            }
        }

        // 如果没有计划级别的质检要求，则从模板项读取
        if (itemList == null) {
            List<BizQcTemplateItem> items = qcTemplateItemMapper.selectList(
                    new LambdaQueryWrapper<BizQcTemplateItem>()
                            .eq(BizQcTemplateItem::getTemplateId, task.getQcTemplateId())
                            .orderByAsc(BizQcTemplateItem::getSortOrder));
            itemList = items.stream().map(i -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("itemName", i.getItemName());
                m.put("itemType", i.getItemType());
                m.put("standardValue", i.getStandardValue());
                m.put("toleranceMin", i.getToleranceMin());
                m.put("toleranceMax", i.getToleranceMax());
                m.put("unit", i.getUnit());
                m.put("method", i.getMethod());
                m.put("defaultValue", i.getDefaultValue());
                m.put("isRequired", i.getIsRequired());
                m.put("isCritical", i.getIsCritical());
                return m;
            }).collect(Collectors.toList());
        }

        result.put("items", itemList);
        return R.ok(result);
    }

    /**
     * 获取工单关联的生产计划附件
     */
    @GetMapping("/{woId}/plan-attachments")
    public R<List<Map<String, Object>>> getPlanAttachments(@PathVariable Long woId) {
        BizWorkOrder wo = workOrderMapper.selectById(woId);
        if (wo == null || wo.getPlanItemId() == null) {
            return R.ok(List.of());
        }
        BizProductionPlanItem planItem = planItemMapper.selectById(wo.getPlanItemId());
        if (planItem == null || planItem.getPlanId() == null) {
            return R.ok(List.of());
        }
        List<BizProductionPlanAttachment> attachments = planAttachmentMapper.selectList(
                new LambdaQueryWrapper<BizProductionPlanAttachment>()
                        .eq(BizProductionPlanAttachment::getPlanId, planItem.getPlanId())
                        .orderByAsc(BizProductionPlanAttachment::getId));
        List<Map<String, Object>> result = attachments.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("originalName", a.getOriginalName());
            m.put("fileUrl", a.getFileUrl());
            m.put("fileSize", a.getFileSize());
            m.put("fileType", a.getFileType());
            return m;
        }).collect(Collectors.toList());
        return R.ok(result);
    }

    /**
     * 工序进度卡：按工序分组，展示所有未完成工单的工序生产信息
     */
    @GetMapping("/process-progress")
    public R<java.util.List<java.util.Map<String, Object>>> getProcessProgress(
            @RequestParam(required = false) String stepName) {
        return R.ok(workOrderService.getProcessProgress(stepName));
    }

    /**
     * 获取工单外协状态概览
     */
    @GetMapping("/{woId}/outsource-status")
    public R<Map<String, Object>> getOutsourceStatus(@PathVariable Long woId) {
        List<BizWoProcessTask> tasks = processTaskMapper.selectList(
                new LambdaQueryWrapper<BizWoProcessTask>()
                        .eq(BizWoProcessTask::getWorkOrderId, woId)
                        .orderByAsc(BizWoProcessTask::getStepGroup)
                        .orderByAsc(BizWoProcessTask::getStepNo));

        List<Map<String, Object>> outsourceTasks = tasks.stream()
                .filter(t -> t.getIsOutsourceable() != null && t.getIsOutsourceable() == 1)
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("taskId", t.getId());
                    m.put("stepName", t.getStepName());
                    m.put("stepGroup", t.getStepGroup());
                    m.put("stepNo", t.getStepNo());
                    m.put("status", t.getStatus());
                    m.put("outsourceStatus", t.getOutsourceStatus());
                    m.put("outsourceRequestId", t.getOutsourceRequestId());
                    m.put("outsourceOrderId", t.getOutsourceOrderId());
                    m.put("virtualMaterialId", t.getVirtualMaterialId());
                    m.put("planQty", t.getPlanQty());
                    m.put("qualifiedQty", t.getQualifiedQty());
                    // 虚拟半成品可用库存
                    if (t.getVirtualMaterialId() != null) {
                        m.put("virtualSemiQty", outsourceChainService.getMaxReportableQty(t));
                    }
                    return m;
                }).collect(Collectors.toList());

        long totalOutsource = outsourceTasks.size();
        long completedOutsource = outsourceTasks.stream()
                .filter(m -> "completed".equals(m.get("outsourceStatus")))
                .count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalOutsource", totalOutsource);
        result.put("completedOutsource", completedOutsource);
        result.put("outsourceTasks", outsourceTasks);
        return R.ok(result);
    }

    /**
     * 创建成品批次入库任务
     */
    @PostMapping("/{woId}/stock-in-task")
    public R<Long> createStockInTask(@PathVariable Long woId, @RequestBody Map<String, Object> body) {
        java.math.BigDecimal qty = new java.math.BigDecimal(body.get("qty").toString());
        Long warehouseId = body.get("warehouseId") != null ? Long.valueOf(body.get("warehouseId").toString()) : null;
        Long taskId = productionStockInService.createBatchStockInTask(woId, qty, warehouseId);
        return R.ok(taskId);
    }
}
