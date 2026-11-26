package com.aimes.modules.quality.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.CodeUtil;
import com.aimes.modules.finance.entity.BizPayable;
import com.aimes.modules.finance.entity.BizWoCost;
import com.aimes.modules.finance.mapper.BizWoCostMapper;
import com.aimes.modules.finance.service.BizFinanceService;
import com.aimes.modules.inventory.service.BizInvTaskService;
import com.aimes.modules.inventory.service.InventoryService;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.entity.BizSupplier;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.master.mapper.BizSupplierMapper;
import com.aimes.modules.order.entity.BizSalesOrder;
import com.aimes.modules.order.entity.BizSalesOrderItem;
import com.aimes.modules.order.mapper.BizSalesOrderItemMapper;
import com.aimes.modules.order.mapper.BizSalesOrderMapper;
import com.aimes.modules.outsource.entity.BizOutsourceOrder;
import com.aimes.modules.outsource.entity.BizOutsourceReceipt;
import com.aimes.modules.outsource.mapper.BizOutsourceOrderMapper;
import com.aimes.modules.outsource.mapper.BizOutsourceReceiptMapper;
import com.aimes.modules.outsource.service.OutsourceChainService;
import com.aimes.modules.production.entity.BizReworkOrder;
import com.aimes.modules.production.entity.BizWorkOrder;
import com.aimes.modules.production.mapper.BizReworkOrderMapper;
import com.aimes.modules.production.mapper.BizWorkOrderMapper;
import com.aimes.modules.workreport.service.WorkReportService;
import com.aimes.modules.purchase.entity.BizPurchaseOrder;
import com.aimes.modules.purchase.entity.BizPurchaseOrderItem;
import com.aimes.modules.purchase.entity.BizPurchaseReceipt;
import com.aimes.modules.purchase.mapper.BizPurchaseOrderItemMapper;
import com.aimes.modules.purchase.mapper.BizPurchaseOrderMapper;
import com.aimes.modules.purchase.mapper.BizPurchaseReceiptMapper;
import com.aimes.modules.production.entity.BizWoProcessTask;
import com.aimes.modules.production.mapper.BizWoProcessTaskMapper;
import com.aimes.modules.quality.entity.BizDefectRecord;
import com.aimes.modules.quality.entity.BizQcResult;
import com.aimes.modules.quality.entity.BizQcTask;
import com.aimes.modules.quality.entity.BizQcTemplate;
import com.aimes.modules.quality.entity.BizQcTemplateItem;
import com.aimes.modules.quality.mapper.BizDefectRecordMapper;
import com.aimes.modules.quality.mapper.BizQcResultMapper;
import com.aimes.modules.quality.mapper.BizQcTaskMapper;
import com.aimes.modules.quality.mapper.BizQcTemplateMapper;
import com.aimes.modules.quality.mapper.BizQcTemplateItemMapper;
import com.aimes.modules.workreport.entity.BizWorkReport;
import com.aimes.modules.workreport.mapper.BizWorkReportMapper;
import com.aimes.modules.system.entity.SysUser;
import com.aimes.modules.system.mapper.SysUserMapper;
import com.aimes.modules.system.service.SysBizConfigService;
import com.aimes.modules.system.service.SysNotificationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BizQcTaskService extends ServiceImpl<BizQcTaskMapper, BizQcTask> {

    private final BizQcResultMapper resultMapper;
    private final BizDefectRecordMapper defectRecordMapper;
    private final BizWoProcessTaskMapper woProcessTaskMapper;
    private final BizWorkReportMapper workReportMapper;
    private final InventoryService inventoryService;
    private final BizFinanceService financeService;
    private final BizMaterialMapper materialMapper;
    private final BizSupplierMapper supplierMapper;
    private final BizWorkOrderMapper workOrderMapper;
    private final BizReworkOrderMapper reworkOrderMapper;
    private final BizSalesOrderItemMapper salesOrderItemMapper;
    private final BizSalesOrderMapper salesOrderMapper;
    private final BizPurchaseOrderMapper purchaseOrderMapper;
    private final BizPurchaseOrderItemMapper purchaseOrderItemMapper;
    private final BizPurchaseReceiptMapper purchaseReceiptMapper;
    private final BizOutsourceOrderMapper outsourceOrderMapper;
    private final BizOutsourceReceiptMapper outsourceReceiptMapper;
    private final SysUserMapper userMapper;
    private final SysNotificationService notificationService;
    private final OutsourceChainService outsourceChainService;
    private final BizInvTaskService invTaskService;
    private final BizQcTemplateMapper templateMapper;
    private final BizQcTemplateItemMapper templateItemMapper;
    private final BizWoCostMapper woCostMapper;
    private final SysBizConfigService bizConfigService;

    private final org.springframework.context.ApplicationContext applicationContext;

    @Autowired
    @Lazy
    private WorkReportService workReportService;

    /**
     * 分页查询质检任务 - 按类型返回追溯所需的完整字段
     * incoming(来料检): 物料名称、供应商、采购单号、批次号
     * process(过程检): 工单号、产品名称、工序名称、报工人
     * final(成品检): 工单号、产品名称、批次号
     * outsource(外协检): 工单号、产品名称、外协单号、供应商、工序名称
     */
    private static final Map<String, String> QC_TYPE_NAME_MAP = Map.of(
            "incoming",  "来料检验",
            "process",   "过程检验",
            "final",     "成品检验",
            "outsource", "外协检验"
    );
    private static final Map<String, String> QC_STATUS_NAME_MAP = new java.util.HashMap<>(Map.of(
            "pending",    "待检验",
            "inspecting", "检验中",
            "completed",  "合格",
            "pass",       "合格",
            "passed",     "合格",
            "fail",       "不合格",
            "failed",     "不合格"
    ));

    public PageResult<Map<String, Object>> getPage(String qcType, String result, String keyword, int pageNum, int pageSize) {
        return getPage(qcType, null, result, keyword, null, null, pageNum, pageSize);
    }

    public PageResult<Map<String, Object>> getPage(String qcType, String relatedOrderType, String result, String keyword, int pageNum, int pageSize) {
        return getPage(qcType, relatedOrderType, result, keyword, null, null, pageNum, pageSize);
    }

    public PageResult<Map<String, Object>> getPage(String qcType, String relatedOrderType, String result, String keyword,
                                                    String taskNo, String workOrderNo, int pageNum, int pageSize) {
        // workOrderNo 过滤：先按 woNo 反查 woId 集合
        Set<Long> filterWoIds = null;
        if (StringUtils.hasText(workOrderNo)) {
            filterWoIds = workOrderMapper.selectList(
                            new LambdaQueryWrapper<BizWorkOrder>()
                                    .like(BizWorkOrder::getWoNo, workOrderNo)
                                    .eq(BizWorkOrder::getDeleted, 0))
                    .stream().map(BizWorkOrder::getId).collect(Collectors.toSet());
            if (filterWoIds.isEmpty()) {
                return new PageResult<>(Collections.emptyList(), 0L, pageNum, pageSize);
            }
        }

        LambdaQueryWrapper<BizQcTask> wrapper = new LambdaQueryWrapper<BizQcTask>()
                .eq(StringUtils.hasText(qcType), BizQcTask::getQcType, qcType)
                .eq(StringUtils.hasText(relatedOrderType), BizQcTask::getRelatedOrderType, relatedOrderType)
                .eq(StringUtils.hasText(result), BizQcTask::getResult, result)
                .like(StringUtils.hasText(keyword), BizQcTask::getTaskNo, keyword)
                .like(StringUtils.hasText(taskNo), BizQcTask::getTaskNo, taskNo)
                .in(filterWoIds != null, BizQcTask::getWorkOrderId, filterWoIds == null ? Collections.emptySet() : filterWoIds)
                .orderByDesc(BizQcTask::getCreateTime);

        Page<BizQcTask> page = page(new Page<>(pageNum, pageSize), wrapper);
        List<BizQcTask> records = page.getRecords();
        if (records.isEmpty()) {
            return new PageResult<>(Collections.emptyList(), 0L, pageNum, pageSize);
        }

        // ---- 收集所有需要的ID ----
        Set<Long> allMatIds = new HashSet<>();
        Set<Long> inspectorIds = new HashSet<>();
        Set<Long> woIds = new HashSet<>();
        Set<Long> taskIds = new HashSet<>();
        Set<Long> templateIds = new HashSet<>();
        Set<Long> incomingOrderIds = new HashSet<>();   // 来料检：旧流程采购单ID
        Set<Long> purchaseReceiptIds = new HashSet<>();  // 来料检：新流程采购收料单ID
        Set<Long> outsourceOrderIds = new HashSet<>();   // 外协检：旧流程外协单ID
        Set<Long> outsourceReceiptIds = new HashSet<>(); // 外协检：新流程外协收料单ID
        Set<Long> reworkOrderIds = new HashSet<>();      // 返修检：返修工单ID

        for (BizQcTask t : records) {
            if (t.getProductId() != null) allMatIds.add(t.getProductId());
            if (t.getMaterialId() != null) allMatIds.add(t.getMaterialId());
            if (t.getInspectorId() != null) inspectorIds.add(t.getInspectorId());
            if (t.getWorkOrderId() != null) woIds.add(t.getWorkOrderId());
            if (t.getTaskId() != null) taskIds.add(t.getTaskId());
            if (t.getTemplateId() != null) templateIds.add(t.getTemplateId());
            if ("incoming".equals(t.getQcType()) && t.getRelatedOrderId() != null) {
                if ("purchase_receipt".equals(t.getRelatedOrderType())) {
                    purchaseReceiptIds.add(t.getRelatedOrderId());
                } else {
                    incomingOrderIds.add(t.getRelatedOrderId());
                }
            }
            if ("outsource".equals(t.getQcType()) && t.getRelatedOrderId() != null) {
                if ("outsource_receipt".equals(t.getRelatedOrderType())) {
                    outsourceReceiptIds.add(t.getRelatedOrderId());
                } else {
                    outsourceOrderIds.add(t.getRelatedOrderId());
                }
            }
            if ("rework".equals(t.getQcType()) && t.getRelatedOrderId() != null) {
                reworkOrderIds.add(t.getRelatedOrderId());
            }
        }

        // ---- 批量查询关联数据 ----
        Map<Long, String> matNameMap = allMatIds.isEmpty() ? new HashMap<>() :
                materialMapper.selectBatchIds(allMatIds).stream()
                        .collect(Collectors.toMap(BizMaterial::getId, BizMaterial::getMaterialName, (a, b) -> a));
        Map<Long, String> inspectorNameMap = inspectorIds.isEmpty() ? new HashMap<>() :
                userMapper.selectBatchIds(inspectorIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));
        Map<Long, String> woNoMap = woIds.isEmpty() ? new HashMap<>() :
                workOrderMapper.selectBatchIds(woIds).stream()
                        .collect(Collectors.toMap(BizWorkOrder::getId, BizWorkOrder::getWoNo, (a, b) -> a));
        Map<Long, String> stepNameMap = taskIds.isEmpty() ? new HashMap<>() :
                woProcessTaskMapper.selectBatchIds(taskIds).stream()
                        .collect(Collectors.toMap(BizWoProcessTask::getId, BizWoProcessTask::getStepName, (a, b) -> a));
        Map<Long, String> templateNameMap = templateIds.isEmpty() ? new HashMap<>() :
                templateMapper.selectBatchIds(templateIds).stream()
                        .collect(Collectors.toMap(BizQcTemplate::getId, BizQcTemplate::getTemplateName, (a, b) -> a));

        // 过程检：指派人/班组（从工序任务的指派信息中获取）
        Map<Long, String> assigneeNameMap = new HashMap<>();
        if (!taskIds.isEmpty()) {
            woProcessTaskMapper.selectBatchIds(taskIds).forEach(pt -> {
                if ("team".equals(pt.getAssignType()) && pt.getAssignedTeamName() != null) {
                    assigneeNameMap.put(pt.getId(), pt.getAssignedTeamName() + "(班组)");
                } else if (pt.getAssignedUserName() != null) {
                    assigneeNameMap.put(pt.getId(), pt.getAssignedUserName());
                }
            });
        }

        // 过程检：报工人(具体到人) — 取工序任务最新一条报工记录的报工人
        Map<Long, String> reporterNameMap = new HashMap<>();
        if (!taskIds.isEmpty()) {
            List<BizWorkReport> reports = workReportMapper.selectList(
                    new LambdaQueryWrapper<BizWorkReport>()
                            .in(BizWorkReport::getTaskId, taskIds)
                            .isNotNull(BizWorkReport::getReporterId)
                            .orderByDesc(BizWorkReport::getCreateTime));
            Set<Long> reporterIds = reports.stream().map(BizWorkReport::getReporterId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, String> reporterUserMap = reporterIds.isEmpty() ? new HashMap<>() :
                    userMapper.selectBatchIds(reporterIds).stream()
                            .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));
            for (BizWorkReport r : reports) {
                reporterNameMap.computeIfAbsent(r.getTaskId(), k ->
                        reporterUserMap.getOrDefault(r.getReporterId(), ""));
            }
        }

        // 来料检（旧流程）：采购单号 + 供应商
        Map<Long, String> poNoMap = new HashMap<>();
        Map<Long, Long> poSupplierMap = new HashMap<>();
        if (!incomingOrderIds.isEmpty()) {
            purchaseOrderMapper.selectBatchIds(incomingOrderIds).forEach(po -> {
                poNoMap.put(po.getId(), po.getPoNo());
                if (po.getSupplierId() != null) poSupplierMap.put(po.getId(), po.getSupplierId());
            });
        }

        // 来料检（新流程）：收料单号 + 通过收料单→采购订单→供应商
        Map<Long, String> purchaseReceiptNoMap = new HashMap<>();
        Map<Long, Long> purchaseReceiptSupplierMap = new HashMap<>();
        if (!purchaseReceiptIds.isEmpty()) {
            purchaseReceiptMapper.selectBatchIds(purchaseReceiptIds).forEach(r -> {
                purchaseReceiptNoMap.put(r.getId(), r.getReceiptNo());
                if (r.getPurchaseOrderId() != null) {
                    BizPurchaseOrder po = purchaseOrderMapper.selectById(r.getPurchaseOrderId());
                    if (po != null && po.getSupplierId() != null) {
                        purchaseReceiptSupplierMap.put(r.getId(), po.getSupplierId());
                    }
                }
            });
        }

        // 外协检（旧流程）：外协单号 + 供应商 + 工序名
        Map<Long, String> ooNoMap = new HashMap<>();
        Map<Long, Long> ooSupplierMap = new HashMap<>();
        Map<Long, String> ooStepNameMap = new HashMap<>();
        if (!outsourceOrderIds.isEmpty()) {
            outsourceOrderMapper.selectBatchIds(outsourceOrderIds).forEach(oo -> {
                ooNoMap.put(oo.getId(), oo.getOrderNo());
                if (oo.getSupplierId() != null) ooSupplierMap.put(oo.getId(), oo.getSupplierId());
                if (oo.getStepName() != null) ooStepNameMap.put(oo.getId(), oo.getStepName());
            });
        }

        // 外协检（新流程）：收料单号 + 通过收料单→外协订单→供应商/工序
        Map<Long, String> outsourceReceiptNoMap = new HashMap<>();
        Map<Long, Long> outsourceReceiptSupplierMap = new HashMap<>();
        Map<Long, String> outsourceReceiptStepMap = new HashMap<>();
        if (!outsourceReceiptIds.isEmpty()) {
            outsourceReceiptMapper.selectBatchIds(outsourceReceiptIds).forEach(r -> {
                outsourceReceiptNoMap.put(r.getId(), r.getReceiptNo());
                if (r.getOutsourceOrderId() != null) {
                    com.aimes.modules.outsource.entity.BizOutsourceOrder oo = outsourceOrderMapper.selectById(r.getOutsourceOrderId());
                    if (oo != null) {
                        if (oo.getSupplierId() != null) outsourceReceiptSupplierMap.put(r.getId(), oo.getSupplierId());
                        if (oo.getStepName() != null) outsourceReceiptStepMap.put(r.getId(), oo.getStepName());
                    }
                }
            });
        }

        // 批量查供应商名称
        Set<Long> supplierIds = new HashSet<>();
        supplierIds.addAll(poSupplierMap.values());
        supplierIds.addAll(purchaseReceiptSupplierMap.values());
        supplierIds.addAll(ooSupplierMap.values());
        supplierIds.addAll(outsourceReceiptSupplierMap.values());
        Map<Long, String> supplierNameMap = supplierIds.isEmpty() ? new HashMap<>() :
                supplierMapper.selectBatchIds(supplierIds).stream()
                        .collect(Collectors.toMap(BizSupplier::getId, BizSupplier::getSupplierName, (a, b) -> a));

        // 返修检：返修工单号
        Map<Long, String> reworkOrderNoMap = reworkOrderIds.isEmpty() ? new HashMap<>() :
                reworkOrderMapper.selectBatchIds(reworkOrderIds).stream()
                        .collect(Collectors.toMap(BizReworkOrder::getId, BizReworkOrder::getReworkNo, (a, b) -> a));

        // ---- 构建返回数据 ----
        List<Map<String, Object>> list = records.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            String type = t.getQcType() != null ? t.getQcType() : "";

            // 通用字段
            m.put("id", t.getId());
            m.put("taskNo", t.getTaskNo());
            m.put("qcType", type);
            m.put("inspectType", type);                                                      // 兼容前端 inspectType
            m.put("inspectTypeName", QC_TYPE_NAME_MAP.getOrDefault(type, type));            // 兼容前端 inspectTypeName
            m.put("status", t.getResult());                                                  // 兼容前端 status
            m.put("statusName", QC_STATUS_NAME_MAP.getOrDefault(t.getResult(), t.getResult())); // 兼容前端 statusName
            m.put("sampleQty", t.getSampleQty() != null ? t.getSampleQty() : t.getPlanQty()); // 送检数量
            m.put("quantity", t.getSampleQty() != null ? t.getSampleQty() : t.getPlanQty()); // 兼容前端 quantity
            m.put("planDate", t.getCreateTime());                                            // 兼容前端 planDate
            m.put("templateId", t.getTemplateId());
            m.put("templateName", t.getTemplateId() != null ? templateNameMap.getOrDefault(t.getTemplateId(), "") : "");
            m.put("batchNo", t.getBatchNo());
            m.put("planQty", t.getPlanQty());
            m.put("passQty", t.getPassQty());
            m.put("failQty", t.getFailQty());
            m.put("result", t.getResult());
            m.put("disposalStatus", t.getDisposalStatus());
            m.put("inspectorId", t.getInspectorId());
            m.put("inspectorName", t.getInspectorId() != null ? inspectorNameMap.getOrDefault(t.getInspectorId(), "") : "");
            m.put("inspectedAt", t.getInspectedAt());
            m.put("conclusion", t.getConclusion());
            m.put("remark", t.getRemark());
            m.put("createTime", t.getCreateTime());

            // 类型特有字段
            switch (type) {
                case "incoming":
                    // 来料检：物料、供应商、关联单号
                    m.put("materialId", t.getMaterialId());
                    m.put("materialName", t.getMaterialId() != null ? matNameMap.getOrDefault(t.getMaterialId(), "") : "");
                    m.put("relatedOrderId", t.getRelatedOrderId());
                    m.put("relatedOrderType", t.getRelatedOrderType());
                    if ("purchase_receipt".equals(t.getRelatedOrderType())) {
                        // 新流程：关联收料单号
                        m.put("purchaseOrderNo", t.getRelatedOrderId() != null ? purchaseReceiptNoMap.getOrDefault(t.getRelatedOrderId(), "") : "");
                        Long prSupplierId = t.getRelatedOrderId() != null ? purchaseReceiptSupplierMap.get(t.getRelatedOrderId()) : null;
                        m.put("supplierName", prSupplierId != null ? supplierNameMap.getOrDefault(prSupplierId, "") : "");
                    } else {
                        // 旧流程：关联采购订单号
                        m.put("purchaseOrderNo", t.getRelatedOrderId() != null ? poNoMap.getOrDefault(t.getRelatedOrderId(), "") : "");
                        Long poSupplierId = t.getRelatedOrderId() != null ? poSupplierMap.get(t.getRelatedOrderId()) : null;
                        m.put("supplierName", poSupplierId != null ? supplierNameMap.getOrDefault(poSupplierId, "") : "");
                    }
                    break;
                case "process":
                    // 过程检：工单号、产品、工序、指派人/班组
                    m.put("workOrderId", t.getWorkOrderId());
                    m.put("workOrderNo", t.getWorkOrderId() != null ? woNoMap.getOrDefault(t.getWorkOrderId(), "") : "");
                    m.put("productId", t.getProductId());
                    m.put("productName", t.getProductId() != null ? matNameMap.getOrDefault(t.getProductId(), "") : "");
                    m.put("taskId", t.getTaskId());
                    m.put("stepName", t.getTaskId() != null ? stepNameMap.getOrDefault(t.getTaskId(), "") : "");
                    m.put("assigneeName", t.getTaskId() != null ? assigneeNameMap.getOrDefault(t.getTaskId(), "") : "");
                    m.put("reporterName", t.getTaskId() != null ? reporterNameMap.getOrDefault(t.getTaskId(), "") : "");
                    break;
                case "final":
                    // 成品检：工单号、产品
                    m.put("workOrderId", t.getWorkOrderId());
                    m.put("workOrderNo", t.getWorkOrderId() != null ? woNoMap.getOrDefault(t.getWorkOrderId(), "") : "");
                    m.put("productId", t.getProductId());
                    m.put("productName", t.getProductId() != null ? matNameMap.getOrDefault(t.getProductId(), "") : "");
                    break;
                case "outsource":
                    // 外协检：工单号、产品、关联单号、供应商、工序
                    m.put("workOrderId", t.getWorkOrderId());
                    m.put("workOrderNo", t.getWorkOrderId() != null ? woNoMap.getOrDefault(t.getWorkOrderId(), "") : "");
                    m.put("productId", t.getProductId());
                    m.put("productName", t.getProductId() != null ? matNameMap.getOrDefault(t.getProductId(), "") : "");
                    m.put("relatedOrderId", t.getRelatedOrderId());
                    m.put("relatedOrderType", t.getRelatedOrderType());
                    if ("outsource_receipt".equals(t.getRelatedOrderType())) {
                        // 新流程：关联收料单号
                        m.put("outsourceOrderNo", t.getRelatedOrderId() != null ? outsourceReceiptNoMap.getOrDefault(t.getRelatedOrderId(), "") : "");
                        Long orSupplierId = t.getRelatedOrderId() != null ? outsourceReceiptSupplierMap.get(t.getRelatedOrderId()) : null;
                        m.put("supplierName", orSupplierId != null ? supplierNameMap.getOrDefault(orSupplierId, "") : "");
                        m.put("stepName", t.getRelatedOrderId() != null ? outsourceReceiptStepMap.getOrDefault(t.getRelatedOrderId(), "") : "");
                    } else {
                        // 旧流程：关联外协单号
                        m.put("outsourceOrderNo", t.getRelatedOrderId() != null ? ooNoMap.getOrDefault(t.getRelatedOrderId(), "") : "");
                        Long ooSupplierId = t.getRelatedOrderId() != null ? ooSupplierMap.get(t.getRelatedOrderId()) : null;
                        m.put("supplierName", ooSupplierId != null ? supplierNameMap.getOrDefault(ooSupplierId, "") : "");
                        m.put("stepName", t.getRelatedOrderId() != null ? ooStepNameMap.getOrDefault(t.getRelatedOrderId(), "") : "");
                    }
                    break;
                case "rework":
                    // 返修检：返修工单号、原工单号、物料、工序
                    m.put("relatedOrderId", t.getRelatedOrderId());
                    m.put("reworkOrderNo", t.getRelatedOrderId() != null ? reworkOrderNoMap.getOrDefault(t.getRelatedOrderId(), "") : "");
                    m.put("workOrderId", t.getWorkOrderId());
                    m.put("workOrderNo", t.getWorkOrderId() != null ? woNoMap.getOrDefault(t.getWorkOrderId(), "") : "");
                    m.put("materialId", t.getMaterialId());
                    m.put("materialName", t.getMaterialId() != null ? matNameMap.getOrDefault(t.getMaterialId(), "") : "");
                    m.put("taskId", t.getTaskId());
                    m.put("stepName", t.getTaskId() != null ? stepNameMap.getOrDefault(t.getTaskId(), "") : "");
                    break;
                default:
                    // 兜底：返回所有可能的字段
                    m.put("materialId", t.getMaterialId());
                    m.put("materialName", t.getMaterialId() != null ? matNameMap.getOrDefault(t.getMaterialId(), "") : "");
                    m.put("workOrderId", t.getWorkOrderId());
                    m.put("workOrderNo", t.getWorkOrderId() != null ? woNoMap.getOrDefault(t.getWorkOrderId(), "") : "");
                    m.put("productId", t.getProductId());
                    m.put("productName", t.getProductId() != null ? matNameMap.getOrDefault(t.getProductId(), "") : "");
                    break;
            }

            return m;
        }).collect(Collectors.toList());

        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    /**
     * 质检任务详情（含检验结果 + 类型特有追溯信息）
     * 如果尚未提交检验结果，则从质检模板自动加载检验项目
     */
    public Map<String, Object> getDetail(Long id) {
        BizQcTask task = getById(id);
        if (task == null) {
            throw new BusinessException("质检任务不存在");
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        String type = task.getQcType() != null ? task.getQcType() : "";

        // ---- 通用字段 ----
        detail.put("id", task.getId());
        detail.put("taskNo", task.getTaskNo());
        detail.put("qcType", type);
        detail.put("templateId", task.getTemplateId());
        if (task.getTemplateId() != null) {
            BizQcTemplate tpl = templateMapper.selectById(task.getTemplateId());
            detail.put("templateName", tpl != null ? tpl.getTemplateName() : "");
        }
        detail.put("batchNo", task.getBatchNo());
        detail.put("planQty", task.getPlanQty());
        detail.put("sampleQty", task.getSampleQty());
        detail.put("passQty", task.getPassQty());
        detail.put("failQty", task.getFailQty());
        detail.put("result", task.getResult());
        detail.put("disposalStatus", task.getDisposalStatus());
        detail.put("inspectorId", task.getInspectorId());
        if (task.getInspectorId() != null) {
            SysUser inspector = userMapper.selectById(task.getInspectorId());
            detail.put("inspectorName", inspector != null ? inspector.getRealName() : "");
        }
        detail.put("inspectedAt", task.getInspectedAt());
        detail.put("conclusion", task.getConclusion());
        detail.put("photoUrls", task.getPhotoUrls());
        detail.put("remark", task.getRemark());
        detail.put("createTime", task.getCreateTime());

        // ---- 类型特有追溯字段 ----
        switch (type) {
            case "incoming":
                detail.put("materialId", task.getMaterialId());
                if (task.getMaterialId() != null) {
                    BizMaterial mat = materialMapper.selectById(task.getMaterialId());
                    detail.put("materialName", mat != null ? mat.getMaterialName() : "");
                    detail.put("materialNo", mat != null ? mat.getMaterialNo() : "");
                }
                if (task.getRelatedOrderId() != null) {
                    BizPurchaseOrder po = purchaseOrderMapper.selectById(task.getRelatedOrderId());
                    if (po != null) {
                        detail.put("purchaseOrderNo", po.getPoNo());
                        if (po.getSupplierId() != null) {
                            BizSupplier supplier = supplierMapper.selectById(po.getSupplierId());
                            detail.put("supplierName", supplier != null ? supplier.getSupplierName() : "");
                        }
                    }
                }
                break;
            case "process":
                detail.put("workOrderId", task.getWorkOrderId());
                if (task.getWorkOrderId() != null) {
                    BizWorkOrder wo = workOrderMapper.selectById(task.getWorkOrderId());
                    detail.put("workOrderNo", wo != null ? wo.getWoNo() : "");
                }
                detail.put("productId", task.getProductId());
                if (task.getProductId() != null) {
                    BizMaterial product = materialMapper.selectById(task.getProductId());
                    detail.put("productName", product != null ? product.getMaterialName() : "");
                }
                detail.put("taskId", task.getTaskId());
                if (task.getTaskId() != null) {
                    BizWoProcessTask pt = woProcessTaskMapper.selectById(task.getTaskId());
                    detail.put("stepName", pt != null ? pt.getStepName() : "");
                    // 指派人/班组
                    if (pt != null) {
                        if ("team".equals(pt.getAssignType()) && pt.getAssignedTeamName() != null) {
                            detail.put("assigneeName", pt.getAssignedTeamName() + "(班组)");
                        } else if (pt.getAssignedUserName() != null) {
                            detail.put("assigneeName", pt.getAssignedUserName());
                        }
                    }
                }
                break;
            case "final":
                detail.put("workOrderId", task.getWorkOrderId());
                if (task.getWorkOrderId() != null) {
                    BizWorkOrder wo = workOrderMapper.selectById(task.getWorkOrderId());
                    detail.put("workOrderNo", wo != null ? wo.getWoNo() : "");
                }
                detail.put("productId", task.getProductId());
                if (task.getProductId() != null) {
                    BizMaterial product = materialMapper.selectById(task.getProductId());
                    detail.put("productName", product != null ? product.getMaterialName() : "");
                }
                break;
            case "outsource":
                detail.put("workOrderId", task.getWorkOrderId());
                if (task.getWorkOrderId() != null) {
                    BizWorkOrder wo = workOrderMapper.selectById(task.getWorkOrderId());
                    detail.put("workOrderNo", wo != null ? wo.getWoNo() : "");
                }
                detail.put("productId", task.getProductId());
                if (task.getProductId() != null) {
                    BizMaterial product = materialMapper.selectById(task.getProductId());
                    detail.put("productName", product != null ? product.getMaterialName() : "");
                }
                if (task.getRelatedOrderId() != null) {
                    BizOutsourceOrder oo = outsourceOrderMapper.selectById(task.getRelatedOrderId());
                    if (oo != null) {
                        detail.put("outsourceOrderNo", oo.getOrderNo());
                        detail.put("stepName", oo.getStepName());
                        if (oo.getSupplierId() != null) {
                            BizSupplier supplier = supplierMapper.selectById(oo.getSupplierId());
                            detail.put("supplierName", supplier != null ? supplier.getSupplierName() : "");
                        }
                    }
                }
                break;
            default:
                detail.put("productId", task.getProductId());
                if (task.getProductId() != null) {
                    BizMaterial product = materialMapper.selectById(task.getProductId());
                    detail.put("productName", product != null ? product.getMaterialName() : "");
                }
                detail.put("materialId", task.getMaterialId());
                if (task.getMaterialId() != null) {
                    BizMaterial mat = materialMapper.selectById(task.getMaterialId());
                    detail.put("materialName", mat != null ? mat.getMaterialName() : "");
                }
                break;
        }

        // ---- 检验项目列表 ----
        List<BizQcResult> results = resultMapper.selectList(
                new LambdaQueryWrapper<BizQcResult>()
                        .eq(BizQcResult::getQcTaskId, id)
                        .orderByAsc(BizQcResult::getId));

        if (!results.isEmpty()) {
            // 检查是否有结果缺少标准值等字段（旧数据兼容），如果缺少则从工序任务qcRequirements或模板项补充
            boolean needSupplement = results.stream().anyMatch(r ->
                    r.getItemType() == null || (r.getStandardValue() == null && r.getUnit() == null));
            Map<String, Map<String, Object>> supplementMap = new HashMap<>();
            if (needSupplement) {
                // 优先从工序任务的 qcRequirements 补充
                if (task.getTaskId() != null) {
                    BizWoProcessTask pt = woProcessTaskMapper.selectById(task.getTaskId());
                    if (pt != null && pt.getQcRequirements() != null && !pt.getQcRequirements().isBlank()) {
                        try {
                            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                            List<Map<String, Object>> planItems = om.readValue(pt.getQcRequirements(),
                                    om.getTypeFactory().constructCollectionType(List.class, Map.class));
                            for (Map<String, Object> pi : planItems) {
                                String name = (String) pi.get("itemName");
                                if (name != null) supplementMap.put(name, pi);
                            }
                        } catch (Exception ignored) {}
                    }
                }
                // 兜底：从模板项补充
                if (supplementMap.isEmpty() && task.getTemplateId() != null) {
                    templateItemMapper.selectList(
                            new LambdaQueryWrapper<BizQcTemplateItem>()
                                    .eq(BizQcTemplateItem::getTemplateId, task.getTemplateId()))
                            .forEach(item -> {
                                Map<String, Object> m = new HashMap<>();
                                m.put("standardValue", item.getStandardValue());
                                m.put("toleranceMin", item.getToleranceMin());
                                m.put("toleranceMax", item.getToleranceMax());
                                m.put("unit", item.getUnit());
                                m.put("method", item.getMethod());
                                m.put("itemType", item.getItemType());
                                m.put("defaultValue", item.getDefaultValue());
                                m.put("isRequired", item.getIsRequired());
                                m.put("isCritical", item.getIsCritical());
                                if (item.getItemName() != null) supplementMap.put(item.getItemName(), m);
                            });
                }
            }

            List<Map<String, Object>> resultList = results.stream().map(r -> {
                Map<String, Object> rm = new LinkedHashMap<>();
                rm.put("id", r.getId());
                rm.put("qcTaskId", r.getQcTaskId());
                rm.put("templateItemId", r.getTemplateItemId());
                rm.put("itemName", r.getItemName());

                // 补充缺失的字段
                Map<String, Object> sup = r.getItemName() != null ? supplementMap.get(r.getItemName()) : null;
                rm.put("itemType", r.getItemType() != null ? r.getItemType() : (sup != null ? sup.get("itemType") : null));
                rm.put("standardValue", r.getStandardValue() != null ? r.getStandardValue() : (sup != null ? sup.get("standardValue") : null));
                rm.put("standard", rm.get("standardValue"));
                rm.put("toleranceMin", r.getToleranceMin() != null ? r.getToleranceMin() : (sup != null ? sup.get("toleranceMin") : null));
                rm.put("toleranceMax", r.getToleranceMax() != null ? r.getToleranceMax() : (sup != null ? sup.get("toleranceMax") : null));
                rm.put("unit", r.getUnit() != null ? r.getUnit() : (sup != null ? sup.get("unit") : null));
                String method = r.getMethod();
                if (method == null && sup != null) {
                    method = sup.get("requirement") != null ? (String) sup.get("requirement") : (String) sup.get("method");
                }
                rm.put("method", method);
                rm.put("defaultValue", r.getDefaultValue() != null ? r.getDefaultValue() : (sup != null ? sup.get("defaultValue") : null));
                rm.put("isRequired", r.getIsRequired() != null ? r.getIsRequired() : (sup != null ? sup.get("isRequired") : null));
                rm.put("isCritical", r.getIsCritical() != null ? r.getIsCritical() : (sup != null ? sup.get("isCritical") : null));
                rm.put("actualValue", r.getActualValue());
                rm.put("isPass", r.getIsPass());
                rm.put("photoUrl", r.getPhotoUrl());
                rm.put("remark", r.getRemark());
                return rm;
            }).collect(Collectors.toList());
            detail.put("results", resultList);
        } else if (task.getTemplateId() != null) {
            // 兜底：模板项目（正常不会走到这里，因为createTask已自动生成）
            List<BizQcTemplateItem> items = templateItemMapper.selectList(
                    new LambdaQueryWrapper<BizQcTemplateItem>()
                            .eq(BizQcTemplateItem::getTemplateId, task.getTemplateId())
                            .orderByAsc(BizQcTemplateItem::getSortOrder));
            List<Map<String, Object>> itemList = items.stream().map(item -> {
                Map<String, Object> im = new LinkedHashMap<>();
                im.put("templateItemId", item.getId());
                im.put("itemName", item.getItemName());
                im.put("itemType", item.getItemType());
                im.put("standardValue", item.getStandardValue());
                im.put("standard", item.getStandardValue());
                im.put("toleranceMin", item.getToleranceMin());
                im.put("toleranceMax", item.getToleranceMax());
                im.put("unit", item.getUnit());
                im.put("method", item.getMethod());
                im.put("defaultValue", item.getDefaultValue());
                im.put("isRequired", item.getIsRequired());
                im.put("isCritical", item.getIsCritical());
                im.put("actualValue", null);
                im.put("isPass", null);
                return im;
            }).collect(Collectors.toList());
            detail.put("results", itemList);
        } else {
            detail.put("results", Collections.emptyList());
        }

        // 检验记录（合格/不合格维度）
        com.aimes.modules.quality.mapper.BizQcRecordMapper qcRecordMapper =
                applicationContext.getBean(com.aimes.modules.quality.mapper.BizQcRecordMapper.class);
        List<com.aimes.modules.quality.entity.BizQcRecord> qcRecords = qcRecordMapper.selectList(
                new LambdaQueryWrapper<com.aimes.modules.quality.entity.BizQcRecord>()
                        .eq(com.aimes.modules.quality.entity.BizQcRecord::getQcTaskId, id)
                        .orderByAsc(com.aimes.modules.quality.entity.BizQcRecord::getCreateTime));
        List<Map<String, Object>> recordList = qcRecords.stream().map(r -> {
            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("id", r.getId());
            rm.put("recordType", r.getRecordType());
            rm.put("quantity", r.getQuantity());
            rm.put("batchNo", r.getBatchNo());
            rm.put("equipment", r.getEquipment());
            rm.put("defectItems", r.getDefectItems());
            rm.put("remark", r.getRemark());
            rm.put("createTime", r.getCreateTime());
            return rm;
        }).collect(Collectors.toList());
        detail.put("records", recordList);

        // 处置记录
        com.aimes.modules.quality.mapper.BizQcDisposalMapper disposalMapper =
                applicationContext.getBean(com.aimes.modules.quality.mapper.BizQcDisposalMapper.class);
        List<com.aimes.modules.quality.entity.BizQcDisposal> disposals = disposalMapper.selectList(
                new LambdaQueryWrapper<com.aimes.modules.quality.entity.BizQcDisposal>()
                        .eq(com.aimes.modules.quality.entity.BizQcDisposal::getQcTaskId, id)
                        .orderByDesc(com.aimes.modules.quality.entity.BizQcDisposal::getCreateTime));
        List<Map<String, Object>> disposalList = disposals.stream().map(d -> {
            Map<String, Object> dm = new LinkedHashMap<>();
            dm.put("id", d.getId());
            dm.put("disposalNo", d.getDisposalNo());
            dm.put("disposalType", d.getDisposalType());
            dm.put("quantity", d.getQuantity());
            dm.put("disposalReason", d.getDisposalReason());
            dm.put("status", d.getStatus());
            dm.put("processedAt", d.getProcessedAt());
            dm.put("remark", d.getRemark());
            return dm;
        }).collect(Collectors.toList());
        detail.put("disposalRecords", disposalList);

        return detail;
    }

    /**
     * 创建质检任务（自动生成编号 + 根据模板自动生成检验项目）
     */
    public void createTask(BizQcTask task) {
        task.setTaskNo(CodeUtil.generate("QC"));
        if (task.getResult() == null) {
            task.setResult("pending");
        }
        task.setPassQty(BigDecimal.ZERO);
        task.setFailQty(BigDecimal.ZERO);

        // 根据模板关联的质检方案计算sampleQty
        if (task.getTemplateId() != null && task.getSampleQty() == null) {
            try {
                BizQcTemplate tpl = templateMapper.selectById(task.getTemplateId());
                if (tpl != null && tpl.getSchemeId() != null) {
                    BizQcSchemeService schemeService = applicationContext.getBean(BizQcSchemeService.class);
                    int planQty = task.getPlanQty() != null ? task.getPlanQty().intValue() : 0;
                    int sampleQty = schemeService.calculateSampleQty(tpl.getSchemeId(), planQty);
                    task.setSampleQty(BigDecimal.valueOf(sampleQty));
                    log.info("根据质检方案计算抽检数量: templateId={}, schemeId={}, planQty={}, sampleQty={}",
                            task.getTemplateId(), tpl.getSchemeId(), planQty, sampleQty);
                } else {
                    log.warn("质检模板未关联抽检方案,默认按全检处理: templateId={}", task.getTemplateId());
                }
            } catch (Exception e) {
                log.warn("计算质检数量失败: templateId={}", task.getTemplateId(), e);
            }
        }

        save(task);

        // 自动生成检验项目（BizQcResult 待填写记录）
        // 优先从工序任务的 qcRequirements（生产计划维护的质检要求）生成，其次从模板项生成
        if (task.getTemplateId() != null) {
            try {
                boolean generated = false;

                // 1. 尝试从工序任务的 qcRequirements 生成（包含生产计划中用户自定义的标准值、公差等）
                if (task.getTaskId() != null) {
                    BizWoProcessTask processTask = woProcessTaskMapper.selectById(task.getTaskId());
                    if (processTask != null && processTask.getQcRequirements() != null && !processTask.getQcRequirements().isBlank()) {
                        try {
                            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            List<Map<String, Object>> planItems = objectMapper.readValue(processTask.getQcRequirements(),
                                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
                            if (planItems != null && !planItems.isEmpty()) {
                                for (Map<String, Object> pi : planItems) {
                                    BizQcResult result = new BizQcResult();
                                    result.setQcTaskId(task.getId());
                                    result.setItemName((String) pi.get("itemName"));
                                    result.setItemType((String) pi.get("itemType"));
                                    result.setStandardValue((String) pi.get("standardValue"));
                                    result.setToleranceMin((String) pi.get("toleranceMin"));
                                    result.setToleranceMax((String) pi.get("toleranceMax"));
                                    result.setUnit((String) pi.get("unit"));
                                    result.setMethod((String) pi.get("requirement"));
                                    result.setDefaultValue((String) pi.get("defaultValue"));
                                    Object reqObj = pi.get("isRequired");
                                    if (reqObj instanceof Number) result.setIsRequired(((Number) reqObj).intValue());
                                    Object critical = pi.get("isCritical");
                                    if (critical instanceof Number) {
                                        result.setIsCritical(((Number) critical).intValue());
                                    } else if (critical instanceof Boolean) {
                                        result.setIsCritical(Boolean.TRUE.equals(critical) ? 1 : 0);
                                    }
                                    resultMapper.insert(result);
                                }
                                generated = true;
                                log.info("质检任务从生产计划qcRequirements生成{}项检验项目: taskId={}", planItems.size(), task.getId());
                            }
                        } catch (Exception e) {
                            log.warn("解析qcRequirements失败，将使用模板项: taskId={}", task.getId(), e);
                        }
                    }
                }

                // 2. 兜底：从模板项生成
                if (!generated) {
                    List<BizQcTemplateItem> items = templateItemMapper.selectList(
                            new LambdaQueryWrapper<BizQcTemplateItem>()
                                    .eq(BizQcTemplateItem::getTemplateId, task.getTemplateId())
                                    .orderByAsc(BizQcTemplateItem::getSortOrder));
                    for (BizQcTemplateItem item : items) {
                        BizQcResult result = new BizQcResult();
                        result.setQcTaskId(task.getId());
                        result.setTemplateItemId(item.getId());
                        result.setItemName(item.getItemName());
                        result.setItemType(item.getItemType());
                        result.setStandardValue(item.getStandardValue());
                        result.setToleranceMin(item.getToleranceMin());
                        result.setToleranceMax(item.getToleranceMax());
                        result.setUnit(item.getUnit());
                        result.setMethod(item.getMethod());
                        result.setDefaultValue(item.getDefaultValue());
                        result.setIsRequired(item.getIsRequired());
                        result.setIsCritical(item.getIsCritical());
                        resultMapper.insert(result);
                    }
                    log.info("质检任务从模板生成{}项检验项目: taskId={}, templateId={}",
                            items.size(), task.getId(), task.getTemplateId());
                }
            } catch (Exception e) {
                log.warn("自动生成检验项目失败: taskId={}, templateId={}", task.getId(), task.getTemplateId(), e);
            }
        }
    }

    /**
     * 提交检验结果（兼容旧接口）
     */
    @Transactional
    public void submitResult(Long taskId, List<BizQcResult> results, String conclusion) {
        submitResult(taskId, results, conclusion, null, null, null);
    }

    @Transactional
    public void submitResult(Long taskId, List<BizQcResult> results, String conclusion,
                             BigDecimal inputPassQty, BigDecimal inputFailQty) {
        submitResult(taskId, results, conclusion, inputPassQty, inputFailQty, null);
    }

    /**
     * 提交检验结果（支持直接传入合格/不合格数量 + 检验记录）
     * @param inputPassQty 前端直接录入的合格数量（可null，null时按检验项计数）
     * @param inputFailQty 前端直接录入的不合格数量（可null）
     * @param records 检验记录列表（合格/不合格维度）
     */
    @Transactional
    public void submitResult(Long taskId, List<BizQcResult> results, String conclusion,
                             BigDecimal inputPassQty, BigDecimal inputFailQty,
                             List<Map<String, Object>> records) {
        submitResult(taskId, results, conclusion, inputPassQty, inputFailQty, records, null);
    }

    public void submitResult(Long taskId, List<BizQcResult> results, String conclusion,
                             BigDecimal inputPassQty, BigDecimal inputFailQty,
                             List<Map<String, Object>> records, Long templateId) {
        submitResult(taskId, results, conclusion, inputPassQty, inputFailQty, records, templateId, null);
    }

    public void submitResult(Long taskId, List<BizQcResult> results, String conclusion,
                             BigDecimal inputPassQty, BigDecimal inputFailQty,
                             List<Map<String, Object>> records, Long templateId,
                             BigDecimal inputSampleQty) {
        submitResult(taskId, results, conclusion, inputPassQty, inputFailQty, records, templateId, inputSampleQty, null);
    }

    @SuppressWarnings("unchecked")
    public void submitResult(Long taskId, List<BizQcResult> results, String conclusion,
                             BigDecimal inputPassQty, BigDecimal inputFailQty,
                             List<Map<String, Object>> records, Long templateId,
                             BigDecimal inputSampleQty, String inputRemark) {
        BizQcTask task = getById(taskId);
        if (task == null) {
            throw new BusinessException("质检任务不存在");
        }
        // 保存质检数量（由质检方案计算得出）
        if (inputSampleQty != null && task.getSampleQty() == null) {
            task.setSampleQty(inputSampleQty);
        }
        // 保存模板ID（检验时选择的模板）
        if (templateId != null && task.getTemplateId() == null) {
            task.setTemplateId(templateId);
        }
        // 保存检验员为当前用户
        if (task.getInspectorId() == null) {
            task.setInspectorId(com.aimes.common.utils.SecurityUtil.getCurrentUserId());
        }
        // 保存检测备注（覆盖式：本次提交有传就更新）
        if (inputRemark != null) {
            task.setRemark(inputRemark);
        }

        // 空结果时：仅更新状态为 inspecting（开始检验），不做判定
        if (results == null || results.isEmpty()) {
            if ("pending".equals(task.getResult())) {
                task.setResult("inspecting");
                updateById(task);
            }
            return;
        }

        // 已有自动生成的检验项目 → 更新已有记录；否则插入新记录
        List<BizQcResult> existingResults = resultMapper.selectList(
                new LambdaQueryWrapper<BizQcResult>()
                        .eq(BizQcResult::getQcTaskId, taskId));
        Map<Long, BizQcResult> existingByTemplateItemId = new HashMap<>();
        Map<Long, BizQcResult> existingById = new HashMap<>();
        for (BizQcResult er : existingResults) {
            existingById.put(er.getId(), er);
            if (er.getTemplateItemId() != null) {
                existingByTemplateItemId.put(er.getTemplateItemId(), er);
            }
        }

        BigDecimal passQty = BigDecimal.ZERO;
        BigDecimal failQty = BigDecimal.ZERO;
        boolean allPass = true;

        for (BizQcResult result : results) {
            result.setQcTaskId(taskId);
            // 尝试匹配已有记录进行更新（优先按templateItemId，其次按记录ID）
            BizQcResult existing = null;
            if (result.getTemplateItemId() != null) {
                existing = existingByTemplateItemId.get(result.getTemplateItemId());
                if (existing == null) {
                    existing = existingById.get(result.getTemplateItemId());
                }
            }
            if (existing != null) {
                existing.setActualValue(result.getActualValue());
                existing.setIsPass(result.getIsPass());
                existing.setPhotoUrl(result.getPhotoUrl());
                existing.setRemark(result.getRemark());
                // 补全初次未保存的字段
                if (existing.getItemType() == null && result.getItemType() != null) {
                    existing.setItemType(result.getItemType());
                }
                if (existing.getStandardValue() == null && result.getStandardValue() != null) {
                    existing.setStandardValue(result.getStandardValue());
                }
                if (existing.getToleranceMin() == null && result.getToleranceMin() != null) {
                    existing.setToleranceMin(result.getToleranceMin());
                }
                if (existing.getToleranceMax() == null && result.getToleranceMax() != null) {
                    existing.setToleranceMax(result.getToleranceMax());
                }
                if (existing.getUnit() == null && result.getUnit() != null) {
                    existing.setUnit(result.getUnit());
                }
                if (existing.getMethod() == null && result.getMethod() != null) {
                    existing.setMethod(result.getMethod());
                }
                if (existing.getDefaultValue() == null && result.getDefaultValue() != null) {
                    existing.setDefaultValue(result.getDefaultValue());
                }
                if (existing.getIsRequired() == null && result.getIsRequired() != null) {
                    existing.setIsRequired(result.getIsRequired());
                }
                resultMapper.updateById(existing);
            } else {
                resultMapper.insert(result);
            }
            if (result.getIsPass() != null && result.getIsPass() == 0) {
                allPass = false;
            }
        }

        // 计算通过/不通过数量
        if (inputPassQty != null || inputFailQty != null) {
            // 模式1: 前端直接传入合格/不合格数量（采购收料、外协收料、生产报工场景）
            BigDecimal actualFailQty = inputFailQty != null ? inputFailQty : BigDecimal.ZERO;
            BigDecimal actualPassQty = inputPassQty != null ? inputPassQty
                    : (task.getPlanQty() != null ? task.getPlanQty().subtract(actualFailQty) : BigDecimal.ZERO);
            task.setPassQty(actualPassQty);
            task.setFailQty(actualFailQty);
            allPass = actualFailQty.compareTo(BigDecimal.ZERO) == 0;
        } else if (task.getSampleQty() != null) {
            // 模式2: 按检验项计数（兼容旧逻辑）
            long passCount = results.stream()
                    .filter(r -> r.getIsPass() != null && r.getIsPass() == 1)
                    .count();
            long failCount = results.stream()
                    .filter(r -> r.getIsPass() != null && r.getIsPass() == 0)
                    .count();
            passQty = BigDecimal.valueOf(passCount);
            failQty = BigDecimal.valueOf(failCount);
            if (allPass) {
                task.setPassQty(task.getPlanQty() != null ? task.getPlanQty() : passQty);
                task.setFailQty(BigDecimal.ZERO);
            } else {
                task.setPassQty(passQty);
                task.setFailQty(failQty);
            }
        } else {
            // 模式3: 无抽检数量，全检场景
            if (allPass) {
                task.setPassQty(task.getPlanQty() != null ? task.getPlanQty() : BigDecimal.ZERO);
                task.setFailQty(BigDecimal.ZERO);
            } else {
                task.setPassQty(BigDecimal.ZERO);
                task.setFailQty(task.getPlanQty() != null ? task.getPlanQty() : BigDecimal.ZERO);
            }
        }
        // 检查检验记录总数是否等于质检数量 → 自动设为completed
        BigDecimal recordTotal = BigDecimal.ZERO;
        if (records != null) {
            for (Map<String, Object> recMap : records) {
                BigDecimal qty = recMap.get("quantity") != null ? new BigDecimal(recMap.get("quantity").toString()) : BigDecimal.ZERO;
                recordTotal = recordTotal.add(qty);
            }
        }
        BigDecimal sampleQty = task.getSampleQty() != null ? task.getSampleQty()
                : (task.getPlanQty() != null ? task.getPlanQty() : BigDecimal.ZERO);
        boolean recordsComplete = records != null && !records.isEmpty()
                && sampleQty.compareTo(BigDecimal.ZERO) > 0
                && recordTotal.compareTo(sampleQty) >= 0;

        if (recordsComplete) {
            // 检验记录总数 >= 质检数量 → 检测完成
            task.setResult("completed");
            conclusion = "检测完成";
            // 全部合格→已处置，存在不合格→未处置
            boolean hasDefect = task.getFailQty() != null && task.getFailQty().compareTo(BigDecimal.ZERO) > 0;
            task.setDisposalStatus(hasDefect ? "undisposed" : "disposed");
        } else if (records != null && !records.isEmpty()) {
            // 有记录但还没检完 → 保持检验中
            task.setResult("inspecting");
            conclusion = "检验中";
        } else {
            // 旧流程兼容（无检验记录时按检验项判定）
            task.setResult(allPass ? "pass" : "fail");
            if (conclusion == null || conclusion.isBlank()
                    || conclusion.equalsIgnoreCase("pass") || conclusion.equalsIgnoreCase("passed")) {
                conclusion = allPass ? "合格" : "不合格";
            } else if (conclusion.equalsIgnoreCase("fail") || conclusion.equalsIgnoreCase("failed")) {
                conclusion = "不合格";
            }
        }
        task.setConclusion(conclusion);
        task.setInspectedAt(LocalDateTime.now());
        updateById(task);

        // 保存检验记录（合格/不合格维度）— 先删旧记录再插入，避免重复
        if (records != null && !records.isEmpty()) {
            com.aimes.modules.quality.mapper.BizQcRecordMapper qcRecordMapper =
                    applicationContext.getBean(com.aimes.modules.quality.mapper.BizQcRecordMapper.class);
            qcRecordMapper.delete(new LambdaQueryWrapper<com.aimes.modules.quality.entity.BizQcRecord>()
                    .eq(com.aimes.modules.quality.entity.BizQcRecord::getQcTaskId, taskId));
            for (Map<String, Object> recMap : records) {
                com.aimes.modules.quality.entity.BizQcRecord record = new com.aimes.modules.quality.entity.BizQcRecord();
                record.setQcTaskId(taskId);
                record.setRecordType(recMap.get("recordType") != null ? recMap.get("recordType").toString() : "qualified");
                record.setQuantity(recMap.get("quantity") != null ? new BigDecimal(recMap.get("quantity").toString()) : BigDecimal.ZERO);
                record.setRemark(recMap.get("remark") != null ? recMap.get("remark").toString() : null);
                // 保存检验项填写值(JSON)
                Object itemsObj = recMap.get("defectItems");
                if (itemsObj != null) {
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                        record.setDefectItems(om.writeValueAsString(itemsObj));
                    } catch (Exception ignored) {}
                }
                qcRecordMapper.insert(record);
            }
        }

        // 质检联动处理 — 仅在检测完成时触发
        if (recordsComplete) {
            String qcType = task.getQcType() != null ? task.getQcType() : "";
            boolean isReceiptSource = "incoming".equals(qcType) || "outsource".equals(qcType);

            if (isReceiptSource) {
                // 采购收料/外协收料：只走pass分支（onQcComplete内一次性处理合格+不合格）
                processQcPass(task);
            } else {
                // 生产报工(process)类型
                boolean hasDefect = task.getFailQty() != null && task.getFailQty().compareTo(BigDecimal.ZERO) > 0;
                if (hasDefect) {
                    // 有不合格：只走fail分支（生成处理单），不累加合格数（等处置后才累加）
                    processQcFail(task);
                } else {
                    // 全部合格：走pass分支，累加合格数
                    processQcPass(task);
                }
            }
        } else if (records == null || records.isEmpty()) {
            // 旧流程兼容（无检验记录，按检验项判定）
            if (allPass) {
                processQcPass(task);
            } else {
                processQcFail(task);
            }
        }
        // records不为空但未完成时不触发联动，等下次提交补齐
    }

    /**
     * 质检通过后根据 qcType 分支处理
     */
    private void processQcPass(BizQcTask task) {
        try {
            switch (task.getQcType() != null ? task.getQcType() : "") {
                case "final":
                    handleFinalQcPass(task);
                    break;
                case "incoming":
                    handleIncomingQcPass(task);
                    break;
                case "outsource":
                    handleOutsourceQcPass(task);
                    break;
                case "process":
                    handleProcessQcPass(task);
                    break;
                case "rework":
                    // 返修质检通过 → 推进对应返修工序到 completed, 解锁下一道工序
                    applicationContext.getBean(com.aimes.modules.production.service.BizReworkOrderService.class)
                            .onReworkStepQcCompleted(task.getId());
                    break;
                default:
                    log.info("质检通过: qcType={}, 无特殊联动", task.getQcType());
            }
        } catch (Exception e) {
            log.error("质检通过联动处理失败: taskId={}, qcType={}", task.getId(), task.getQcType(), e);
        }
    }

    /**
     * 成品检验通过 → 标记工单待入库，自动创建入库任务
     */
    private void handleFinalQcPass(BizQcTask task) {
        BigDecimal passQty = task.getPassQty();
        if (passQty == null || passQty.compareTo(BigDecimal.ZERO) <= 0) return;

        // 查询工单信息，标记为生产完成
        BizWorkOrder wo = task.getWorkOrderId() != null ? workOrderMapper.selectById(task.getWorkOrderId()) : null;
        if (wo != null) {
            wo.setStatus("completed");
            workOrderMapper.updateById(wo);

            // 自动创建入库任务
            try {
                BizMaterial product = wo.getProductId() != null ? materialMapper.selectById(wo.getProductId()) : null;
                Long whId = product != null ? product.getDefaultWarehouseId() : null;
                invTaskService.createFromWorkOrder(
                        wo.getId(), wo.getWoNo(), wo.getProductId(),
                        product != null ? product.getMaterialNo() : null,
                        product != null ? product.getMaterialName() : null,
                        product != null ? product.getSpec() : null,
                        product != null ? product.getUnit() : null,
                        passQty, whId,
                        com.aimes.common.utils.SecurityUtil.getCurrentUserId());
            } catch (Exception e) {
                log.error("自动创建入库任务失败: workOrderId={}", wo.getId(), e);
            }
        }

        notificationService.notifyRole("warehouse", "成品入库通知",
                "工单[" + (wo != null ? wo.getWoNo() : "") + "]成品检验通过(" + passQty + "件), 请安排确认入库",
                "approval", "work_order", task.getWorkOrderId());
        log.info("成品检验通过,待仓库确认入库: workOrderId={}, passQty={}", task.getWorkOrderId(), passQty);
    }

    /**
     * 来料检完成 → 通过收料单服务回调创建库存任务（合格+不合格分开）
     * 新流程: relatedOrderType=purchase_receipt → 调用 PurchaseReceiptService.onQcComplete()
     * 旧流程: relatedOrderType=purchase_order → 保留旧逻辑
     */
    private void handleIncomingQcPass(BizQcTask task) {
        BigDecimal passQty = task.getPassQty() != null ? task.getPassQty() : BigDecimal.ZERO;
        BigDecimal failQty = task.getFailQty() != null ? task.getFailQty() : BigDecimal.ZERO;

        // 新流程：通过收料单服务处理
        if ("purchase_receipt".equals(task.getRelatedOrderType()) && task.getRelatedOrderId() != null) {
            try {
                com.aimes.modules.purchase.service.PurchaseReceiptService receiptService =
                        applicationContext.getBean(com.aimes.modules.purchase.service.PurchaseReceiptService.class);
                receiptService.onQcComplete(task.getRelatedOrderId(), passQty, failQty);
                log.info("来料检完成(新流程): receiptId={}, passQty={}, failQty={}", task.getRelatedOrderId(), passQty, failQty);
            } catch (Exception e) {
                log.error("来料检完成回调收料单失败: receiptId={}", task.getRelatedOrderId(), e);
            }
            return;
        }

        // ========= 旧流程兼容 (relatedOrderType=purchase_order) =========
        if (passQty.compareTo(BigDecimal.ZERO) <= 0) return;
        Long materialId = task.getMaterialId();
        if (materialId == null) return;

        BizMaterial material = materialMapper.selectById(materialId);
        Long warehouseId = material != null && material.getDefaultWarehouseId() != null
                ? material.getDefaultWarehouseId() : 1L;
        inventoryService.stockIn(materialId, warehouseId, passQty,
                "purchase_in", task.getBatchNo(), "purchase_order",
                task.getRelatedOrderId());

        List<BizPurchaseReceipt> receipts = purchaseReceiptMapper.selectList(
                new LambdaQueryWrapper<BizPurchaseReceipt>()
                        .eq(BizPurchaseReceipt::getQcTaskId, task.getId()));
        for (BizPurchaseReceipt receipt : receipts) {
            receipt.setQcStatus("passed");
            receipt.setQualifiedQty(passQty);
            purchaseReceiptMapper.updateById(receipt);
            invTaskService.autoProcessFromStockIn(receipt.getId(), passQty, warehouseId);
            if (receipt.getOrderItemId() != null) {
                BizPurchaseOrderItem item = purchaseOrderItemMapper.selectById(receipt.getOrderItemId());
                if (item != null) {
                    BigDecimal qualified = item.getQualifiedQty() != null ? item.getQualifiedQty() : BigDecimal.ZERO;
                    item.setQualifiedQty(qualified.add(passQty));
                    purchaseOrderItemMapper.updateById(item);
                }
            }
        }
        log.info("来料检通过联动完成(旧流程): materialId={}, qty={}", materialId, passQty);
    }

    /**
     * 外协检完成 → 新流程通过收料单回调，旧流程保留兼容
     */
    private void handleOutsourceQcPass(BizQcTask task) {
        BigDecimal passQty = task.getPassQty() != null ? task.getPassQty() : BigDecimal.ZERO;
        BigDecimal failQty = task.getFailQty() != null ? task.getFailQty() : BigDecimal.ZERO;

        // 新流程：relatedOrderType=outsource_receipt → 通过收料单服务处理
        if ("outsource_receipt".equals(task.getRelatedOrderType()) && task.getRelatedOrderId() != null) {
            try {
                com.aimes.modules.outsource.service.OutsourceReceiptService outsourceReceiptService =
                        applicationContext.getBean(com.aimes.modules.outsource.service.OutsourceReceiptService.class);
                outsourceReceiptService.onQcComplete(task.getRelatedOrderId(), passQty, failQty);
                log.info("外协检完成(新流程): receiptId={}, passQty={}, failQty={}", task.getRelatedOrderId(), passQty, failQty);
            } catch (Exception e) {
                log.error("外协检完成回调收料单失败: receiptId={}", task.getRelatedOrderId(), e);
            }
            return;
        }

        // ========= 旧流程兼容 (relatedOrderType=outsource_order) =========
        if (passQty.compareTo(BigDecimal.ZERO) <= 0) return;

        Long productId = task.getProductId();
        if (productId != null) {
            BizMaterial material = materialMapper.selectById(productId);
            Long warehouseId = material != null && material.getDefaultWarehouseId() != null
                    ? material.getDefaultWarehouseId() : 1L;
            inventoryService.stockIn(productId, warehouseId, passQty,
                    "outsource_in", null, "outsource_order",
                    task.getRelatedOrderId());
        }

        if (task.getRelatedOrderId() != null) {
            List<BizOutsourceReceipt> receipts = outsourceReceiptMapper.selectList(
                    new LambdaQueryWrapper<BizOutsourceReceipt>()
                            .eq(BizOutsourceReceipt::getQcTaskId, task.getId()));
            for (BizOutsourceReceipt receipt : receipts) {
                receipt.setQcStatus("passed");
                receipt.setQualifiedQty(passQty);
                outsourceReceiptMapper.updateById(receipt);
            }

            BizOutsourceOrder oo = outsourceOrderMapper.selectById(task.getRelatedOrderId());
            if (oo != null) {
                BigDecimal qualified = oo.getQualifiedQty() != null ? oo.getQualifiedQty() : BigDecimal.ZERO;
                oo.setQualifiedQty(qualified.add(passQty));
                if (oo.getQualifiedQty().compareTo(oo.getTotalQty()) >= 0) {
                    oo.setStatus("completed");
                }
                outsourceOrderMapper.updateById(oo);

                BigDecimal payAmount = oo.getUnitPrice() != null
                        ? oo.getUnitPrice().multiply(passQty) : BigDecimal.ZERO;
                if (payAmount.compareTo(BigDecimal.ZERO) > 0) {
                    BizPayable payable = new BizPayable();
                    payable.setSupplierId(oo.getSupplierId());
                    payable.setAmount(payAmount);
                    payable.setSourceType("outsource_order");
                    payable.setSourceId(oo.getId());
                    payable.setDueDate(LocalDate.now().plusDays(30));
                    payable.setRemark("外协入库自动生成应付-OO:" + oo.getOrderNo());
                    financeService.createPayable(payable);
                }

                if (task.getWorkOrderId() != null && payAmount.compareTo(BigDecimal.ZERO) > 0) {
                    BizWoCost woCost = financeService.getWoCost(task.getWorkOrderId());
                    woCost.setOutsourceCost(woCost.getOutsourceCost().add(payAmount));
                    financeService.updateWoCost(woCost);
                }
            }

            try {
                outsourceChainService.onOutsourceReceiptQcPassed(task.getRelatedOrderId(), passQty);
            } catch (Exception e) {
                log.warn("外协链路回调失败: outsourceOrderId={}", task.getRelatedOrderId(), e);
            }
        }
        log.info("外协检通过联动完成(旧流程): productId={}, qty={}", productId, passQty);
    }

    /**
     * 工序检验通过 → 回调报工服务，传入本批次合格数以支持分批报工
     */
    private void handleProcessQcPass(BizQcTask task) {
        if (task.getTaskId() != null) {
            workReportService.onProcessQcPassed(task.getTaskId(), task.getPlanQty());
            log.info("工序质检通过: taskId={}, qcTaskId={}, batchQty={}", task.getTaskId(), task.getId(), task.getPlanQty());
        }
    }

    /**
     * 统计指定工序任务下尚未完成（非pass/fail）的质检批次数量
     * 用于分批报工场景判断是否所有批次均已质检完毕
     */
    public long countPendingByProcessTask(Long processTaskId) {
        if (processTaskId == null) return 0;
        return count(new LambdaQueryWrapper<BizQcTask>()
                .eq(BizQcTask::getTaskId, processTaskId)
                .notIn(BizQcTask::getResult, "pass", "fail", "completed"));
    }

    /**
     * 质检不通过后根据 qcType 分支处理
     * 若配置"不良品自动隔离"=true，自动生成隔离状态的缺陷记录
     */
    private void processQcFail(BizQcTask task) {
        try {
            // 不良品自动隔离：质检不通过时，创建自动隔离的缺陷记录
            boolean autoQuarantine = bizConfigService.getBoolValue("quality.defect.auto_quarantine", true);
            if (autoQuarantine) {
                BigDecimal failQty = task.getFailQty() != null && task.getFailQty().compareTo(BigDecimal.ZERO) > 0
                        ? task.getFailQty() : task.getPlanQty();
                if (failQty != null && failQty.compareTo(BigDecimal.ZERO) > 0) {
                    BizDefectRecord defect = new BizDefectRecord();
                    defect.setDefectNo(CodeUtil.generate("DF"));
                    defect.setSourceType("qc_task");
                    defect.setSourceId(task.getId());
                    defect.setProductId(task.getProductId() != null ? task.getProductId() : task.getMaterialId());
                    defect.setWorkOrderId(task.getWorkOrderId());
                    defect.setQuantity(failQty);
                    defect.setDefectType(task.getQcType());
                    defect.setHandleStatus("quarantined"); // 自动隔离
                    defect.setHandleRemark("质检不通过自动隔离，质检任务: " + task.getTaskNo());
                    defectRecordMapper.insert(defect);
                    log.info("质检不通过，不良品已自动隔离: qcTaskId={}, qty={}", task.getId(), failQty);
                }
            }

            switch (task.getQcType() != null ? task.getQcType() : "") {
                case "incoming":
                    // 新流程：来料检不通过也需要通过收料单回调(passQty可能>0的部分)
                    if ("purchase_receipt".equals(task.getRelatedOrderType()) && task.getRelatedOrderId() != null) {
                        BigDecimal passQty = task.getPassQty() != null ? task.getPassQty() : BigDecimal.ZERO;
                        BigDecimal failQty2 = task.getFailQty() != null ? task.getFailQty() : BigDecimal.ZERO;
                        try {
                            com.aimes.modules.purchase.service.PurchaseReceiptService receiptService =
                                    applicationContext.getBean(com.aimes.modules.purchase.service.PurchaseReceiptService.class);
                            receiptService.onQcComplete(task.getRelatedOrderId(), passQty, failQty2);
                            log.info("来料检不通过(新流程): receiptId={}, passQty={}, failQty={}", task.getRelatedOrderId(), passQty, failQty2);
                        } catch (Exception e) {
                            log.error("来料检不通过回调收料单失败: receiptId={}", task.getRelatedOrderId(), e);
                        }
                    }
                    break;
                case "outsource":
                    // 新流程：外协检不通过也通过收料单回调
                    if ("outsource_receipt".equals(task.getRelatedOrderType()) && task.getRelatedOrderId() != null) {
                        BigDecimal passQty3 = task.getPassQty() != null ? task.getPassQty() : BigDecimal.ZERO;
                        BigDecimal failQty3 = task.getFailQty() != null ? task.getFailQty() : BigDecimal.ZERO;
                        try {
                            com.aimes.modules.outsource.service.OutsourceReceiptService outsourceReceiptService =
                                    applicationContext.getBean(com.aimes.modules.outsource.service.OutsourceReceiptService.class);
                            outsourceReceiptService.onQcComplete(task.getRelatedOrderId(), passQty3, failQty3);
                        } catch (Exception e) {
                            log.error("外协检不通过回调收料单失败: receiptId={}", task.getRelatedOrderId(), e);
                        }
                    }
                    break;
                case "process":
                    if (task.getTaskId() != null) {
                        workReportService.onProcessQcFailed(task.getTaskId());
                        log.info("工序质检不通过: taskId={}, qcTaskId={}", task.getTaskId(), task.getId());
                    }
                    // 自动生成质检处理单（需求：不合格数量每条自动生成一条质检处理单）
                    if (task.getFailQty() != null && task.getFailQty().compareTo(java.math.BigDecimal.ZERO) > 0) {
                        try {
                            com.aimes.modules.quality.entity.BizQcDisposal disposal = new com.aimes.modules.quality.entity.BizQcDisposal();
                            disposal.setQcTaskId(task.getId());
                            disposal.setWorkOrderId(task.getWorkOrderId());
                            disposal.setProcessTaskId(task.getTaskId());
                            disposal.setMaterialId(task.getProductId() != null ? task.getProductId() : task.getMaterialId());
                            // 获取工序名称
                            if (task.getTaskId() != null) {
                                com.aimes.modules.production.entity.BizWoProcessTask pt =
                                        applicationContext.getBean(com.aimes.modules.production.mapper.BizWoProcessTaskMapper.class)
                                                .selectById(task.getTaskId());
                                if (pt != null) disposal.setStepName(pt.getStepName());
                            }
                            disposal.setQuantity(task.getFailQty());
                            com.aimes.modules.quality.service.BizQcDisposalService disposalService =
                                    applicationContext.getBean(com.aimes.modules.quality.service.BizQcDisposalService.class);
                            disposalService.createDisposal(disposal);
                            log.info("工序质检不通过，自动生成质检处理单: qcTaskId={}, failQty={}", task.getId(), task.getFailQty());
                        } catch (Exception e) {
                            log.error("自动生成质检处理单失败: qcTaskId={}", task.getId(), e);
                        }
                    }
                    break;
                case "final":
                    handleFinalQcFail(task);
                    break;
                case "rework":
                    // 返修质检失败: 推进 step 到 completed，让合格部分(passQty)能 restore
                    applicationContext.getBean(com.aimes.modules.production.service.BizReworkOrderService.class)
                            .onReworkStepQcCompleted(task.getId());
                    // 为 failQty 自动生成质检处理单
                    if (task.getFailQty() != null && task.getFailQty().compareTo(java.math.BigDecimal.ZERO) > 0) {
                        try {
                            com.aimes.modules.quality.entity.BizQcDisposal disposal = new com.aimes.modules.quality.entity.BizQcDisposal();
                            disposal.setQcTaskId(task.getId());
                            disposal.setWorkOrderId(task.getWorkOrderId());
                            disposal.setProcessTaskId(task.getTaskId());
                            disposal.setMaterialId(task.getProductId() != null ? task.getProductId() : task.getMaterialId());
                            if (task.getTaskId() != null) {
                                com.aimes.modules.production.entity.BizWoProcessTask pt =
                                        applicationContext.getBean(com.aimes.modules.production.mapper.BizWoProcessTaskMapper.class)
                                                .selectById(task.getTaskId());
                                if (pt != null) disposal.setStepName(pt.getStepName());
                            }
                            disposal.setQuantity(task.getFailQty());
                            applicationContext.getBean(com.aimes.modules.quality.service.BizQcDisposalService.class)
                                    .createDisposal(disposal);
                            log.info("返修质检不通过，自动生成处理单: qcTaskId={}, failQty={}", task.getId(), task.getFailQty());
                        } catch (Exception e) {
                            log.error("返修质检自动生成处理单失败: qcTaskId={}", task.getId(), e);
                        }
                    }
                    break;
                default:
                    log.info("质检不通过: qcType={}, 无特殊联动", task.getQcType());
            }
        } catch (Exception e) {
            log.error("质检不通过联动处理失败: taskId={}, qcType={}", task.getId(), task.getQcType(), e);
        }
    }

    /**
     * 成品检验不通过 → 工单状态恢复为completed（需返工），通知生产
     */
    private void handleFinalQcFail(BizQcTask task) {
        if (task.getWorkOrderId() == null) return;
        BizWorkOrder wo = workOrderMapper.selectById(task.getWorkOrderId());
        if (wo != null) {
            wo.setStatus("completed"); // 恢复为已完工，允许返工后重新触发质检
            workOrderMapper.updateById(wo);

            notificationService.notifyRole("production", "成品检验不通过",
                    "工单[" + wo.getWoNo() + "]成品检验不通过，请安排返工处理",
                    "alert", "work_order", wo.getId());
            log.info("成品检验不通过: workOrderId={}, 工单恢复为completed状态", wo.getId());
        }
    }

    // ==================== 质检不合格处置 ====================

    /**
     * 质检不合格处置: 返修/返工/报废
     * @param qcTaskId 质检任务ID
     * @param body { handlePath, quantity, remark, repairSteps, assignedTeamId, assignedUserId, reworkStepNo, scrapReason, scrapCost }
     * @return 处置结果信息
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> disposeFailedTask(Long qcTaskId, Map<String, Object> body) {
        BizQcTask qcTask = getById(qcTaskId);
        if (qcTask == null) {
            throw new BusinessException("质检任务不存在");
        }
        if (!"fail".equals(qcTask.getResult()) && !"completed".equals(qcTask.getResult())) {
            throw new BusinessException("仅已完成或不合格的质检任务可以处置");
        }

        String handlePath = (String) body.get("handlePath");
        if (handlePath == null || handlePath.isEmpty()) {
            throw new BusinessException("请选择处置方式");
        }

        BigDecimal quantity = body.get("quantity") != null
                ? new BigDecimal(body.get("quantity").toString()) : BigDecimal.ONE;
        String remark = body.get("remark") != null ? body.get("remark").toString() : null;

        // 查找关联的工单和工序任务
        BizWorkOrder sourceWo = qcTask.getWorkOrderId() != null
                ? workOrderMapper.selectById(qcTask.getWorkOrderId()) : null;
        BizWoProcessTask processTask = qcTask.getTaskId() != null
                ? woProcessTaskMapper.selectById(qcTask.getTaskId()) : null;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("handlePath", handlePath);

        // 处置范围
        String scope = body.get("scope") != null ? body.get("scope").toString() : "defect";

        BigDecimal planQty = qcTask.getPlanQty() != null ? qcTask.getPlanQty() : BigDecimal.ZERO;
        // 剩余合格数 = 总数量 - 处置数量（累加到工序现有合格数上）
        BigDecimal remainQualified = planQty.subtract(quantity).max(BigDecimal.ZERO);
        BigDecimal currentQualified = processTask != null && processTask.getQualifiedQty() != null
                ? processTask.getQualifiedQty() : BigDecimal.ZERO;

        switch (handlePath) {
            case "repair":
                // 返修：处置数量去返修工单，剩余累加到合格数
                handleRepairDispose(qcTask, sourceWo, processTask, quantity, remark, body, result);
                if (processTask != null) {
                    processTask.setQualifiedQty(currentQualified.add(remainQualified));
                    woProcessTaskMapper.updateById(processTask);
                }
                result.put("message", "返修工单已创建，本次新增合格数: " + remainQualified);
                break;
            case "concession":
                // 让步接收：全部数量视为合格，累加到工序合格数
                String concessionReason = body.get("concessionReason") != null ? body.get("concessionReason").toString() : remark;
                if (processTask != null) {
                    processTask.setQualifiedQty(currentQualified.add(planQty));
                    woProcessTaskMapper.updateById(processTask);
                }
                createDefectRecord(qcTask, sourceWo, processTask, "concession", quantity, concessionReason);
                result.put("message", "让步接收完成，本次新增合格数: " + planQty);
                break;
            case "scrap":
                // 报废：处置数量生成报废库存任务，剩余累加到合格数
                handleScrapDispose(qcTask, sourceWo, processTask, quantity, remark, body, result);
                if (processTask != null) {
                    processTask.setQualifiedQty(currentQualified.add(remainQualified));
                    woProcessTaskMapper.updateById(processTask);
                }
                result.put("message", "报废完成，本次新增合格数: " + remainQualified);
                break;
            default:
                throw new BusinessException("不支持的处置方式: " + handlePath);
        }

        // 处置完成，更新处置状态
        qcTask.setDisposalStatus("disposed");
        updateById(qcTask);

        // 处置完成后推进工序任务：如果合格数已达计划数，标记完成；否则保持可继续报工
        if (processTask != null) {
            BigDecimal newQualified = processTask.getQualifiedQty() != null ? processTask.getQualifiedQty() : BigDecimal.ZERO;
            BigDecimal taskPlanQty = processTask.getPlanQty() != null ? processTask.getPlanQty() : BigDecimal.ZERO;
            long pendingQcCount = countPendingByProcessTask(processTask.getId());
            if (newQualified.compareTo(taskPlanQty) >= 0 && pendingQcCount == 0) {
                processTask.setStatus("completed");
                processTask.setQcStatus("pass");
                processTask.setEndTime(java.time.LocalDateTime.now());
                woProcessTaskMapper.updateById(processTask);
                // 推进下一工序 & 更新工单进度 + 触发入库重评估
                try {
                    workReportService.onProcessQcPassed(processTask.getId(), BigDecimal.ZERO);
                } catch (Exception e) {
                    log.warn("处置后推进工序失败: taskId={}", processTask.getId(), e);
                }
            } else if ("pending_qc".equals(processTask.getStatus())) {
                // 合格数不足但处置完成 → 回到可报工状态
                processTask.setStatus("in_progress");
                processTask.setQcStatus("pending_disposal");
                woProcessTaskMapper.updateById(processTask);
            }
            // 未走 onProcessQcPassed 的路径（工序未完工）：手动触发外协链路
            // onProcessQcPassed 内部已含 handlePrevStepReported，此处仅补非完工分支
            boolean wentThroughQcPassed = newQualified.compareTo(taskPlanQty) >= 0 && pendingQcCount == 0;
            if (!wentThroughQcPassed) {
                BigDecimal delta = newQualified.subtract(currentQualified);
                if (delta.compareTo(BigDecimal.ZERO) > 0) {
                    try {
                        outsourceChainService.handlePrevStepReported(
                                processTask.getWorkOrderId(), processTask, delta);
                    } catch (Exception e) {
                        log.warn("处置后外协链路触发失败(非完工路径): taskId={}", processTask.getId(), e);
                    }
                }
            }
            // 不论工序是否完成,只要 qualifiedQty 在处置过程中增加(让步接收/报废时累加合格数),
            // 都可能让产品瓶颈上升,需要:
            //   1) 重算工单 completedQty(完成进度条),否则会出现"已入库但完成数为 0"
            //   2) 重新评估是否生成/追加入库任务
            try {
                workReportService.updateWorkOrderProgress(processTask.getWorkOrderId());
            } catch (Exception e) {
                log.warn("处置后工单进度更新失败: workOrderId={}", processTask.getWorkOrderId(), e);
            }
            try {
                workReportService.tryCreateLastStepStockIn(processTask.getWorkOrderId(), processTask.getStepNo());
            } catch (Exception e) {
                log.warn("处置后入库评估失败: workOrderId={}, taskId={}", processTask.getWorkOrderId(), processTask.getId(), e);
            }
        }

        // 同步更新关联的质检处理单
        try {
            com.aimes.modules.quality.mapper.BizQcDisposalMapper disposalMapper =
                    applicationContext.getBean(com.aimes.modules.quality.mapper.BizQcDisposalMapper.class);
            List<com.aimes.modules.quality.entity.BizQcDisposal> disposals = disposalMapper.selectList(
                    new LambdaQueryWrapper<com.aimes.modules.quality.entity.BizQcDisposal>()
                            .eq(com.aimes.modules.quality.entity.BizQcDisposal::getQcTaskId, qcTaskId)
                            .eq(com.aimes.modules.quality.entity.BizQcDisposal::getStatus, "pending"));
            // 根据处置方式取正确的原因
            String reason = remark;
            if ("concession".equals(handlePath) && body.get("concessionReason") != null) {
                reason = body.get("concessionReason").toString();
            } else if ("scrap".equals(handlePath) && body.get("scrapReason") != null) {
                reason = body.get("scrapReason").toString();
            }
            for (com.aimes.modules.quality.entity.BizQcDisposal d : disposals) {
                d.setDisposalType(handlePath);
                d.setDisposalReason(reason);
                d.setQuantity(quantity);
                d.setStatus("processed");
                d.setProcessedBy(com.aimes.common.utils.SecurityUtil.getCurrentUserId());
                d.setProcessedAt(java.time.LocalDateTime.now());
                disposalMapper.updateById(d);
            }
        } catch (Exception e) {
            log.warn("更新质检处理单失败: qcTaskId={}", qcTaskId, e);
        }

        // 回写关联的缺陷记录状态为已处理
        try {
            List<BizDefectRecord> defects = defectRecordMapper.selectList(
                    new LambdaQueryWrapper<BizDefectRecord>()
                            .eq(BizDefectRecord::getSourceType, "qc_task")
                            .eq(BizDefectRecord::getSourceId, qcTaskId)
                            .ne(BizDefectRecord::getHandleStatus, "completed"));
            for (BizDefectRecord d : defects) {
                d.setHandleStatus("completed");
                d.setHandlePath(handlePath);
                d.setHandleRemark("质检处置完成: " + handlePath);
                defectRecordMapper.updateById(d);
            }
        } catch (Exception e) {
            log.warn("回写缺陷记录状态失败: qcTaskId={}", qcTaskId, e);
        }

        return result;
    }

    /**
     * 返修处置: 创建返修工单
     */
    @SuppressWarnings("unchecked")
    private void handleRepairDispose(BizQcTask qcTask, BizWorkOrder sourceWo,
                                      BizWoProcessTask processTask, BigDecimal quantity,
                                      String remark, Map<String, Object> body,
                                      Map<String, Object> result) {
        if (sourceWo == null) {
            throw new BusinessException("未找到关联工单，无法创建返修工单");
        }

        // 1. 创建缺陷记录
        createDefectRecord(qcTask, sourceWo, processTask, "repair", quantity, remark);

        // 2. 使用 BizReworkOrderService 创建返修工单（不是生产工单）
        com.aimes.modules.production.service.BizReworkOrderService reworkOrderService =
                applicationContext.getBean(com.aimes.modules.production.service.BizReworkOrderService.class);

        com.aimes.modules.production.entity.BizReworkOrder reworkOrder = new com.aimes.modules.production.entity.BizReworkOrder();
        reworkOrder.setQcTaskId(qcTask.getId());
        reworkOrder.setWorkOrderId(sourceWo.getId());
        reworkOrder.setProcessTaskId(qcTask.getTaskId());
        reworkOrder.setProductId(sourceWo.getProductId());
        reworkOrder.setQuantity(quantity);
        reworkOrder.setRemark(remark != null ? remark : "质检不合格返修");

        // 解析返修工序
        List<Map<String, Object>> steps = null;
        Object stepsObj = body.get("repairSteps");
        if (stepsObj instanceof List) {
            steps = ((List<Map<String, Object>>) stepsObj).stream()
                    .filter(s -> s.get("stepName") != null && !s.get("stepName").toString().isEmpty())
                    .map(s -> {
                        Map<String, Object> m = new java.util.LinkedHashMap<>();
                        m.put("processId", s.get("processId"));
                        m.put("stepName", s.get("stepName"));
                        m.put("assignedUserId", body.get("assignedUserId"));
                        return m;
                    }).collect(java.util.stream.Collectors.toList());
        }

        Long reworkId = reworkOrderService.createReworkOrder(reworkOrder, steps);

        result.put("reworkOrderId", reworkId);
        result.put("reworkNo", reworkOrder.getReworkNo());
        log.info("返修处置完成: qcTaskId={}, 创建返修工单 reworkId={}, reworkNo={}",
                qcTask.getId(), reworkId, reworkOrder.getReworkNo());
    }

    /**
     * 返工处置: 扣除目标工序到当前质检工序之间的报工数量，重置状态
     */
    private void handleReworkDispose(BizQcTask qcTask, BizWorkOrder sourceWo,
                                      BizWoProcessTask processTask, BigDecimal quantity,
                                      String remark, Map<String, Object> body,
                                      Map<String, Object> result) {
        if (sourceWo == null) {
            throw new BusinessException("未找到关联工单，无法执行返工");
        }

        Integer reworkStepNo = body.get("reworkStepNo") != null
                ? Integer.valueOf(body.get("reworkStepNo").toString()) : null;
        if (reworkStepNo == null && processTask != null) {
            reworkStepNo = processTask.getStepNo();
        }
        if (reworkStepNo == null) {
            throw new BusinessException("请指定返工目标工序");
        }

        // 确定当前质检工序的stepNo作为上界
        Integer qcStepNo = processTask != null ? processTask.getStepNo() : null;
        if (qcStepNo != null && reworkStepNo > qcStepNo) {
            throw new BusinessException("返工目标工序不能在当前质检工序之后");
        }

        // 1. 查找目标工序到当前质检工序之间的所有工序任务
        LambdaQueryWrapper<BizWoProcessTask> wrapper = new LambdaQueryWrapper<BizWoProcessTask>()
                .eq(BizWoProcessTask::getWorkOrderId, sourceWo.getId())
                .ge(BizWoProcessTask::getStepNo, reworkStepNo);
        if (qcStepNo != null) {
            wrapper.le(BizWoProcessTask::getStepNo, qcStepNo);
        }
        wrapper.orderByAsc(BizWoProcessTask::getStepNo);
        List<BizWoProcessTask> tasks = woProcessTaskMapper.selectList(wrapper);

        if (tasks.isEmpty()) {
            throw new BusinessException("未找到目标工序(stepNo=" + reworkStepNo + ")");
        }

        String reworkStepName = tasks.get(0).getStepName();

        // 2. 创建缺陷记录
        BizDefectRecord defect = createDefectRecord(qcTask, sourceWo, processTask,
                "rework", quantity, remark);
        defect.setReworkStepNo(reworkStepNo);
        defect.setReworkStepName(reworkStepName);
        defectRecordMapper.updateById(defect);

        // 3. 扣除数量并重置状态（从目标工序到质检工序）
        int affectedSteps = 0;
        for (int i = 0; i < tasks.size(); i++) {
            BizWoProcessTask t = tasks.get(i);
            // 扣除合格数量
            BigDecimal currentQualified = t.getQualifiedQty() != null ? t.getQualifiedQty() : BigDecimal.ZERO;
            BigDecimal newQualified = currentQualified.subtract(quantity).max(BigDecimal.ZERO);
            t.setQualifiedQty(newQualified);

            // 重置状态: 目标工序设为ready，其他设为pending
            if (i == 0) {
                t.setStatus("ready");
            } else {
                t.setStatus("pending");
            }

            // 清除质检状态
            t.setQcStatus(null);
            t.setQcTaskId(null);
            t.setEndTime(null);

            woProcessTaskMapper.updateById(t);
            affectedSteps++;
        }

        // 4. 确保工单状态为 in_progress
        if (!"in_progress".equals(sourceWo.getStatus())) {
            sourceWo.setStatus("in_progress");
            workOrderMapper.updateById(sourceWo);
        }

        // 5. 通知生产
        notificationService.notifyRole("production", "质检返工",
                "质检不合格处置: 工单[" + sourceWo.getWoNo() + "]返工至工序[" + reworkStepName + "]，扣除数量" + quantity,
                "alert", "work_order", sourceWo.getId());

        result.put("reworkStepNo", reworkStepNo);
        result.put("reworkStepName", reworkStepName);
        result.put("affectedSteps", affectedSteps);
        log.info("返工处置完成: qcTaskId={}, 工单={}, 返工至工序 stepNo={}, 扣除数量={}, 影响{}道工序",
                qcTask.getId(), sourceWo.getWoNo(), reworkStepNo, quantity, affectedSteps);
    }

    /**
     * 报废处置: 记录报废数量和损失
     */
    private void handleScrapDispose(BizQcTask qcTask, BizWorkOrder sourceWo,
                                     BizWoProcessTask processTask, BigDecimal quantity,
                                     String remark, Map<String, Object> body,
                                     Map<String, Object> result) {
        String scrapReason = body.get("scrapReason") != null ? body.get("scrapReason").toString() : null;
        BigDecimal scrapCost = body.get("scrapCost") != null
                ? new BigDecimal(body.get("scrapCost").toString()) : BigDecimal.ZERO;

        // 1. 创建缺陷记录
        BizDefectRecord defect = createDefectRecord(qcTask, sourceWo, processTask,
                "scrap", quantity, remark);
        defect.setScrapQty(quantity);
        defect.setScrapCost(scrapCost);
        defect.setScrapReason(scrapReason);
        defectRecordMapper.updateById(defect);

        // 更新工序任务的不良数（仅用于展示，不影响报工数量计算）
        if (processTask != null) {
            BigDecimal currentDefect = processTask.getDefectQty() != null ? processTask.getDefectQty() : BigDecimal.ZERO;
            processTask.setDefectQty(currentDefect.add(quantity));
            woProcessTaskMapper.updateById(processTask);
        }

        // 3. 更新工单成本: 报废损失
        if (sourceWo != null && scrapCost.compareTo(BigDecimal.ZERO) > 0) {
            BizWoCost cost = woCostMapper.selectOne(
                    new LambdaQueryWrapper<BizWoCost>()
                            .eq(BizWoCost::getWorkOrderId, sourceWo.getId())
                            .last("LIMIT 1"));
            if (cost == null) {
                cost = new BizWoCost();
                cost.setWorkOrderId(sourceWo.getId());
                cost.setMaterialCost(BigDecimal.ZERO);
                cost.setLaborCost(BigDecimal.ZERO);
                cost.setOutsourceCost(BigDecimal.ZERO);
                cost.setReworkCost(BigDecimal.ZERO);
                cost.setScrapLoss(scrapCost);
                cost.setOverhead(BigDecimal.ZERO);
                cost.setTotalCost(scrapCost);
                woCostMapper.insert(cost);
            } else {
                BigDecimal currentScrap = cost.getScrapLoss() != null ? cost.getScrapLoss() : BigDecimal.ZERO;
                cost.setScrapLoss(currentScrap.add(scrapCost));
                // 重算总成本
                cost.setTotalCost(
                        (cost.getMaterialCost() != null ? cost.getMaterialCost() : BigDecimal.ZERO)
                        .add(cost.getLaborCost() != null ? cost.getLaborCost() : BigDecimal.ZERO)
                        .add(cost.getOutsourceCost() != null ? cost.getOutsourceCost() : BigDecimal.ZERO)
                        .add(cost.getReworkCost() != null ? cost.getReworkCost() : BigDecimal.ZERO)
                        .add(cost.getScrapLoss())
                        .add(cost.getOverhead() != null ? cost.getOverhead() : BigDecimal.ZERO)
                );
                woCostMapper.updateById(cost);
            }
        }

        // 4. 通知
        if (sourceWo != null) {
            notificationService.notifyRole("production", "质检报废",
                    "质检不合格处置: 工单[" + sourceWo.getWoNo() + "]报废" + quantity + "件，损失" + scrapCost + "元",
                    "alert", "work_order", sourceWo.getId());
        }

        // 5. 生成报废入库库存任务（类型=scrap_in，来源=质检任务）
        try {
            BizInvTaskService invTaskService = applicationContext.getBean(BizInvTaskService.class);
            Long productId = qcTask.getProductId() != null ? qcTask.getProductId() : qcTask.getMaterialId();
            BizMaterial mat = productId != null ? materialMapper.selectById(productId) : null;
            Long warehouseId = mat != null && mat.getDefaultWarehouseId() != null ? mat.getDefaultWarehouseId() : 1L;
            invTaskService.createFromQcTask(
                    qcTask.getId(), qcTask.getTaskNo(), "", null,
                    warehouseId, com.aimes.common.utils.SecurityUtil.getCurrentUserId(),
                    mat != null ? mat.getId() : 0L,
                    mat != null ? mat.getMaterialNo() : "",
                    mat != null ? mat.getMaterialName() : "",
                    mat != null ? mat.getSpec() : "",
                    mat != null ? mat.getUnit() : "",
                    quantity, BigDecimal.ZERO,
                    "scrap_in", qcTask.getTaskNo());
            log.info("报废处置: 已生成报废入库库存任务, qcTaskId={}, qty={}", qcTask.getId(), quantity);
        } catch (Exception e) {
            log.error("生成报废入库库存任务失败: qcTaskId={}", qcTask.getId(), e);
        }

        result.put("scrapQty", quantity);
        result.put("scrapCost", scrapCost);
        log.info("报废处置完成: qcTaskId={}, 报废数量={}, 报废损失={}",
                qcTask.getId(), quantity, scrapCost);
    }

    /**
     * 创建缺陷记录（处置公用）
     */
    private BizDefectRecord createDefectRecord(BizQcTask qcTask, BizWorkOrder sourceWo,
                                                BizWoProcessTask processTask,
                                                String handlePath, BigDecimal quantity, String remark) {
        BizDefectRecord defect = new BizDefectRecord();
        defect.setDefectNo(CodeUtil.generate("DF"));
        defect.setSourceType("qc_task");
        defect.setSourceId(qcTask.getId());
        defect.setProductId(qcTask.getProductId());
        defect.setMaterialId(qcTask.getMaterialId());
        defect.setWorkOrderId(qcTask.getWorkOrderId());
        defect.setBatchNo(qcTask.getBatchNo());
        defect.setQuantity(quantity);
        defect.setDefectType("qc_fail");
        defect.setDefectReason("质检不合格");
        defect.setHandlePath(handlePath);
        defect.setHandleStatus("completed");
        defect.setHandleResult(handlePath);
        defect.setHandledAt(LocalDateTime.now());
        defect.setQcTaskId(qcTask.getId());
        defect.setProcessTaskId(processTask != null ? processTask.getId() : null);
        defect.setHandleRemark(remark);
        defectRecordMapper.insert(defect);
        return defect;
    }

    /**
     * 质检记录查询（已完成的任务）
     */
    public PageResult<Map<String, Object>> getRecords(String keyword, String qcType, String result,
                                                       String startDate, String endDate,
                                                       int pageNum, int pageSize) {
        return getRecords(keyword, null, null, qcType, result, startDate, endDate, pageNum, pageSize);
    }

    /**
     * 质检记录全局统计 - 按相同的过滤条件返回总数 / 合格 / 不合格 / 合格率
     */
    public Map<String, Object> getRecordStats(String keyword, String taskNo, String batchNo,
                                                String qcType, String startDate, String endDate) {
        LambdaQueryWrapper<BizQcTask> base = new LambdaQueryWrapper<BizQcTask>()
                .in(BizQcTask::getResult, "pass", "fail", "completed")
                .eq(StringUtils.hasText(qcType), BizQcTask::getQcType, qcType);
        if (StringUtils.hasText(startDate)) {
            base.ge(BizQcTask::getInspectedAt, LocalDate.parse(startDate).atStartOfDay());
        }
        if (StringUtils.hasText(endDate)) {
            base.le(BizQcTask::getInspectedAt, LocalDate.parse(endDate).plusDays(1).atStartOfDay());
        }
        if (StringUtils.hasText(keyword)) {
            base.and(w -> w.like(BizQcTask::getTaskNo, keyword)
                    .or().like(BizQcTask::getBatchNo, keyword));
        }
        if (StringUtils.hasText(taskNo)) base.like(BizQcTask::getTaskNo, taskNo);
        if (StringUtils.hasText(batchNo)) base.like(BizQcTask::getBatchNo, batchNo);

        long total = count(base);
        // 复制 wrapper 加 result 过滤 - LambdaQueryWrapper 不能直接复用,只能重建
        long pass = count(buildStatsWrapper(keyword, taskNo, batchNo, qcType, startDate, endDate, "pass"))
                + count(buildCompletedPassWrapper(keyword, taskNo, batchNo, qcType, startDate, endDate));
        long fail = count(buildStatsWrapper(keyword, taskNo, batchNo, qcType, startDate, endDate, "fail"))
                + count(buildCompletedFailWrapper(keyword, taskNo, batchNo, qcType, startDate, endDate));
        int passRate = total > 0 ? (int) Math.round(pass * 100.0 / total) : 0;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("pass", pass);
        stats.put("fail", fail);
        stats.put("passRate", passRate);
        return stats;
    }

    /** completed + failQty=0 → 算合格 */
    private LambdaQueryWrapper<BizQcTask> buildCompletedPassWrapper(String keyword, String taskNo, String batchNo,
                                                                     String qcType, String startDate, String endDate) {
        LambdaQueryWrapper<BizQcTask> w = new LambdaQueryWrapper<BizQcTask>()
                .eq(BizQcTask::getResult, "completed")
                .and(x -> x.isNull(BizQcTask::getFailQty).or().eq(BizQcTask::getFailQty, BigDecimal.ZERO))
                .eq(StringUtils.hasText(qcType), BizQcTask::getQcType, qcType);
        if (StringUtils.hasText(startDate)) w.ge(BizQcTask::getInspectedAt, LocalDate.parse(startDate).atStartOfDay());
        if (StringUtils.hasText(endDate)) w.le(BizQcTask::getInspectedAt, LocalDate.parse(endDate).plusDays(1).atStartOfDay());
        if (StringUtils.hasText(keyword)) w.and(x -> x.likeRight(BizQcTask::getTaskNo, keyword).or().likeRight(BizQcTask::getBatchNo, keyword));
        if (StringUtils.hasText(taskNo)) w.like(BizQcTask::getTaskNo, taskNo);
        if (StringUtils.hasText(batchNo)) w.like(BizQcTask::getBatchNo, batchNo);
        return w;
    }

    /** completed + failQty>0 → 算不合格 */
    private LambdaQueryWrapper<BizQcTask> buildCompletedFailWrapper(String keyword, String taskNo, String batchNo,
                                                                     String qcType, String startDate, String endDate) {
        LambdaQueryWrapper<BizQcTask> w = new LambdaQueryWrapper<BizQcTask>()
                .eq(BizQcTask::getResult, "completed")
                .gt(BizQcTask::getFailQty, BigDecimal.ZERO)
                .eq(StringUtils.hasText(qcType), BizQcTask::getQcType, qcType);
        if (StringUtils.hasText(startDate)) w.ge(BizQcTask::getInspectedAt, LocalDate.parse(startDate).atStartOfDay());
        if (StringUtils.hasText(endDate)) w.le(BizQcTask::getInspectedAt, LocalDate.parse(endDate).plusDays(1).atStartOfDay());
        if (StringUtils.hasText(keyword)) w.and(x -> x.likeRight(BizQcTask::getTaskNo, keyword).or().likeRight(BizQcTask::getBatchNo, keyword));
        if (StringUtils.hasText(taskNo)) w.like(BizQcTask::getTaskNo, taskNo);
        if (StringUtils.hasText(batchNo)) w.like(BizQcTask::getBatchNo, batchNo);
        return w;
    }

    private LambdaQueryWrapper<BizQcTask> buildStatsWrapper(String keyword, String taskNo, String batchNo,
                                                              String qcType, String startDate, String endDate,
                                                              String result) {
        LambdaQueryWrapper<BizQcTask> w = new LambdaQueryWrapper<BizQcTask>()
                .eq(BizQcTask::getResult, result)
                .eq(StringUtils.hasText(qcType), BizQcTask::getQcType, qcType);
        if (StringUtils.hasText(startDate)) {
            w.ge(BizQcTask::getInspectedAt, LocalDate.parse(startDate).atStartOfDay());
        }
        if (StringUtils.hasText(endDate)) {
            w.le(BizQcTask::getInspectedAt, LocalDate.parse(endDate).plusDays(1).atStartOfDay());
        }
        if (StringUtils.hasText(keyword)) {
            w.and(x -> x.like(BizQcTask::getTaskNo, keyword)
                    .or().like(BizQcTask::getBatchNo, keyword));
        }
        if (StringUtils.hasText(taskNo)) w.like(BizQcTask::getTaskNo, taskNo);
        if (StringUtils.hasText(batchNo)) w.like(BizQcTask::getBatchNo, batchNo);
        return w;
    }

    public PageResult<Map<String, Object>> getRecords(String keyword, String taskNo, String batchNo,
                                                       String qcType, String result,
                                                       String startDate, String endDate,
                                                       int pageNum, int pageSize) {
        LambdaQueryWrapper<BizQcTask> wrapper = new LambdaQueryWrapper<BizQcTask>()
                .in(BizQcTask::getResult, "pass", "fail", "completed")
                .eq(StringUtils.hasText(qcType), BizQcTask::getQcType, qcType);
        // result 兼容 completed: pass = result='pass' OR (completed AND failQty 为空/0)
        //                       fail = result='fail' OR (completed AND failQty > 0)
        if ("pass".equals(result)) {
            wrapper.and(w -> w.eq(BizQcTask::getResult, "pass")
                    .or(o -> o.eq(BizQcTask::getResult, "completed")
                            .and(a -> a.isNull(BizQcTask::getFailQty).or().eq(BizQcTask::getFailQty, java.math.BigDecimal.ZERO))));
        } else if ("fail".equals(result)) {
            wrapper.and(w -> w.eq(BizQcTask::getResult, "fail")
                    .or(o -> o.eq(BizQcTask::getResult, "completed")
                            .gt(BizQcTask::getFailQty, java.math.BigDecimal.ZERO)));
        } else if (StringUtils.hasText(result)) {
            wrapper.eq(BizQcTask::getResult, result);
        }

        if (StringUtils.hasText(startDate)) {
            wrapper.ge(BizQcTask::getInspectedAt, LocalDate.parse(startDate).atStartOfDay());
        }
        if (StringUtils.hasText(endDate)) {
            wrapper.le(BizQcTask::getInspectedAt, LocalDate.parse(endDate).plusDays(1).atStartOfDay());
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(BizQcTask::getTaskNo, keyword)
                    .or().like(BizQcTask::getBatchNo, keyword));
        }
        if (StringUtils.hasText(taskNo)) {
            wrapper.like(BizQcTask::getTaskNo, taskNo);
        }
        if (StringUtils.hasText(batchNo)) {
            wrapper.like(BizQcTask::getBatchNo, batchNo);
        }

        wrapper.orderByDesc(BizQcTask::getInspectedAt);
        Page<BizQcTask> page = page(new Page<>(pageNum, pageSize), wrapper);

        List<BizQcTask> records = page.getRecords();
        Set<Long> matIds = new HashSet<>();
        Set<Long> inspIds = new HashSet<>();
        Set<Long> woIds = new HashSet<>();
        Set<Long> incomingOrderIds = new HashSet<>();
        Set<Long> outsourceOrderIds = new HashSet<>();
        records.forEach(t -> {
            if (t.getProductId() != null) matIds.add(t.getProductId());
            if (t.getMaterialId() != null) matIds.add(t.getMaterialId());
            if (t.getInspectorId() != null) inspIds.add(t.getInspectorId());
            if (t.getWorkOrderId() != null) woIds.add(t.getWorkOrderId());
            if ("incoming".equals(t.getQcType()) && t.getRelatedOrderId() != null) incomingOrderIds.add(t.getRelatedOrderId());
            if ("outsource".equals(t.getQcType()) && t.getRelatedOrderId() != null) outsourceOrderIds.add(t.getRelatedOrderId());
        });

        Map<Long, String> matNameMap = matIds.isEmpty() ? new HashMap<>() :
                materialMapper.selectBatchIds(matIds).stream().collect(Collectors.toMap(BizMaterial::getId, BizMaterial::getMaterialName, (a, b) -> a));
        Map<Long, String> inspNameMap = inspIds.isEmpty() ? new HashMap<>() :
                userMapper.selectBatchIds(inspIds).stream().collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));
        Map<Long, String> woNoMap = woIds.isEmpty() ? new HashMap<>() :
                workOrderMapper.selectBatchIds(woIds).stream().collect(Collectors.toMap(BizWorkOrder::getId, BizWorkOrder::getWoNo, (a, b) -> a));

        Map<Long, String> poNoMap = new HashMap<>();
        Map<Long, Long> poSupplierMap = new HashMap<>();
        if (!incomingOrderIds.isEmpty()) {
            purchaseOrderMapper.selectBatchIds(incomingOrderIds).forEach(po -> {
                poNoMap.put(po.getId(), po.getPoNo());
                if (po.getSupplierId() != null) poSupplierMap.put(po.getId(), po.getSupplierId());
            });
        }

        Map<Long, String> ooNoMap = new HashMap<>();
        Map<Long, Long> ooSupplierMap = new HashMap<>();
        if (!outsourceOrderIds.isEmpty()) {
            outsourceOrderMapper.selectBatchIds(outsourceOrderIds).forEach(oo -> {
                ooNoMap.put(oo.getId(), oo.getOrderNo());
                if (oo.getSupplierId() != null) ooSupplierMap.put(oo.getId(), oo.getSupplierId());
            });
        }

        Set<Long> supplierIds = new HashSet<>();
        supplierIds.addAll(poSupplierMap.values());
        supplierIds.addAll(ooSupplierMap.values());
        Map<Long, String> supplierNameMap = supplierIds.isEmpty() ? new HashMap<>() :
                supplierMapper.selectBatchIds(supplierIds).stream().collect(Collectors.toMap(BizSupplier::getId, BizSupplier::getSupplierName, (a, b) -> a));

        List<Map<String, Object>> list = records.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            String type = t.getQcType() != null ? t.getQcType() : "";
            m.put("id", t.getId());
            m.put("taskNo", t.getTaskNo());
            m.put("qcType", type);
            m.put("productName", t.getProductId() != null ? matNameMap.getOrDefault(t.getProductId(), "") : "");
            m.put("materialName", t.getMaterialId() != null ? matNameMap.getOrDefault(t.getMaterialId(), "") : "");
            m.put("batchNo", t.getBatchNo());
            m.put("planQty", t.getPlanQty());
            m.put("sampleQty", t.getSampleQty());
            m.put("passQty", t.getPassQty());
            m.put("failQty", t.getFailQty());
            m.put("result", t.getResult());
            m.put("inspectorName", t.getInspectorId() != null ? inspNameMap.getOrDefault(t.getInspectorId(), "") : "");
            m.put("inspectedAt", t.getInspectedAt());
            m.put("conclusion", t.getConclusion());
            m.put("remark", t.getRemark());
            m.put("createTime", t.getCreateTime());
            // 关联单号
            switch (type) {
                case "incoming":
                    m.put("materialId", t.getMaterialId());
                    m.put("relatedOrderId", t.getRelatedOrderId());
                    m.put("purchaseOrderNo", t.getRelatedOrderId() != null ? poNoMap.getOrDefault(t.getRelatedOrderId(), "") : "");
                    Long poSuppId = t.getRelatedOrderId() != null ? poSupplierMap.get(t.getRelatedOrderId()) : null;
                    m.put("supplierName", poSuppId != null ? supplierNameMap.getOrDefault(poSuppId, "") : "");
                    break;
                case "process":
                case "final":
                    m.put("workOrderId", t.getWorkOrderId());
                    m.put("workOrderNo", t.getWorkOrderId() != null ? woNoMap.getOrDefault(t.getWorkOrderId(), "") : "");
                    m.put("productId", t.getProductId());
                    break;
                case "outsource":
                    m.put("workOrderId", t.getWorkOrderId());
                    m.put("workOrderNo", t.getWorkOrderId() != null ? woNoMap.getOrDefault(t.getWorkOrderId(), "") : "");
                    m.put("productId", t.getProductId());
                    m.put("relatedOrderId", t.getRelatedOrderId());
                    m.put("outsourceOrderNo", t.getRelatedOrderId() != null ? ooNoMap.getOrDefault(t.getRelatedOrderId(), "") : "");
                    Long ooSuppId = t.getRelatedOrderId() != null ? ooSupplierMap.get(t.getRelatedOrderId()) : null;
                    m.put("supplierName", ooSuppId != null ? supplierNameMap.getOrDefault(ooSuppId, "") : "");
                    break;
                default:
                    break;
            }
            return m;
        }).collect(Collectors.toList());

        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    /**
     * 质量追溯（前端入口）：按工单号/产品名称/批次号查询，返回前端期望的时间线格式
     */
    public Map<String, Object> getTraceabilityByNo(String workOrderNo, String productName, String batchNo) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. 工单
        BizWorkOrder wo = null;
        if (StringUtils.hasText(workOrderNo)) {
            wo = workOrderMapper.selectOne(new LambdaQueryWrapper<BizWorkOrder>()
                    .eq(BizWorkOrder::getWoNo, workOrderNo).last("LIMIT 1"));
        }

        // 2. 产品
        Long productId = null;
        BizMaterial product = null;
        if (wo != null && wo.getProductId() != null) {
            productId = wo.getProductId();
            product = materialMapper.selectById(productId);
        } else if (StringUtils.hasText(productName)) {
            product = materialMapper.selectOne(new LambdaQueryWrapper<BizMaterial>()
                    .like(BizMaterial::getMaterialName, productName).last("LIMIT 1"));
            if (product != null) productId = product.getId();
        }

        // 基本信息
        result.put("workOrderNo", wo != null ? wo.getWoNo() : "");
        result.put("productName", product != null ? product.getMaterialName() : productName);
        result.put("batchNo", batchNo);
        result.put("quantity", wo != null ? wo.getPlanQty() : null);

        // 3. 订单信息
        if (wo != null && wo.getOrderId() != null) {
            BizSalesOrder order = salesOrderMapper.selectById(wo.getOrderId());
            if (order != null) {
                result.put("orderNo", order.getOrderNo());
                Map<String, Object> orderMap = new LinkedHashMap<>();
                orderMap.put("orderNo", order.getOrderNo());
                orderMap.put("createTime", order.getCreateTime());
                result.put("order", orderMap);
            }
        }

        // 4. 工单信息块
        if (wo != null) {
            Map<String, Object> woMap = new LinkedHashMap<>();
            woMap.put("workOrderNo", wo.getWoNo());
            woMap.put("planQty", wo.getPlanQty());
            woMap.put("planStartTime", wo.getCreateTime());
            woMap.put("planEndTime", wo.getDeliveryDate());
            result.put("workOrder", woMap);
        }

        // 5. 工序步骤
        if (wo != null) {
            List<com.aimes.modules.production.entity.BizWoProcessTask> tasks =
                    woProcessTaskMapper.selectList(new LambdaQueryWrapper<com.aimes.modules.production.entity.BizWoProcessTask>()
                            .eq(com.aimes.modules.production.entity.BizWoProcessTask::getWorkOrderId, wo.getId())
                            .orderByAsc(com.aimes.modules.production.entity.BizWoProcessTask::getStepNo));
            Set<Long> userIds = tasks.stream()
                    .map(com.aimes.modules.production.entity.BizWoProcessTask::getAssignedUserId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, String> userNames = userIds.isEmpty() ? Map.of() :
                    userMapper.selectBatchIds(userIds).stream()
                            .collect(Collectors.toMap(SysUser::getId,
                                    u -> u.getRealName() != null ? u.getRealName() : u.getUsername(), (a, b) -> a));
            List<Map<String, Object>> steps = tasks.stream().map(t -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("stepNo", t.getStepNo());
                m.put("stepName", t.getStepName());
                m.put("operator", userNames.getOrDefault(t.getAssignedUserId(), ""));
                m.put("completeTime", t.getEndTime());
                return m;
            }).toList();
            result.put("processSteps", steps);
        }

        // 6. 质检记录
        boolean hasFilter = wo != null || productId != null || StringUtils.hasText(batchNo);
        if (hasFilter) {
            LambdaQueryWrapper<BizQcTask> qcWrapper = new LambdaQueryWrapper<>();
            if (wo != null) qcWrapper.eq(BizQcTask::getWorkOrderId, wo.getId());
            else if (productId != null) qcWrapper.eq(BizQcTask::getProductId, productId);
            if (StringUtils.hasText(batchNo)) qcWrapper.eq(BizQcTask::getBatchNo, batchNo);
            qcWrapper.orderByDesc(BizQcTask::getCreateTime);
            List<BizQcTask> qcTasks = list(qcWrapper);

            Set<Long> inspIds = qcTasks.stream().map(BizQcTask::getInspectorId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, String> inspNames = inspIds.isEmpty() ? Map.of() :
                    userMapper.selectBatchIds(inspIds).stream()
                            .collect(Collectors.toMap(SysUser::getId,
                                    u -> u.getRealName() != null ? u.getRealName() : u.getUsername(), (a, b) -> a));
            List<Map<String, Object>> qcResults = qcTasks.stream().map(t -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("taskNo", t.getTaskNo());
                m.put("inspectType", t.getQcType());
                m.put("inspector", inspNames.getOrDefault(t.getInspectorId(), ""));
                String r = t.getResult();
                boolean pass = r != null && (r.startsWith("pass") || "completed".equals(r));
                m.put("result", pass ? "PASS" : "FAIL");
                m.put("inspectTime", t.getInspectedAt());
                return m;
            }).toList();
            result.put("qcResults", qcResults);
        }

        return result;
    }

    /**
     * 质量追溯：按工单/产品/批次号多维度追溯
     * 返回：工序记录、报工记录、检验记录、缺陷记录
     */
    public Map<String, Object> getTraceability(Long workOrderId, Long productId, String batchNo) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. 工序追溯 - 按工单查工序任务
        if (workOrderId != null) {
            List<BizWoProcessTask> tasks = woProcessTaskMapper.selectList(
                    new LambdaQueryWrapper<BizWoProcessTask>()
                            .eq(BizWoProcessTask::getWorkOrderId, workOrderId)
                            .orderByAsc(BizWoProcessTask::getStepNo));
            result.put("processTasks", tasks);

            // 工单信息
            BizWorkOrder wo = workOrderMapper.selectById(workOrderId);
            if (wo != null) {
                result.put("workOrderNo", wo.getWoNo());
                result.put("productId", wo.getProductId());
                if (productId == null) productId = wo.getProductId();
            }
        }

        // 2. 人员追溯 - 报工记录
        LambdaQueryWrapper<BizWorkReport> reportWrapper = new LambdaQueryWrapper<>();
        if (workOrderId != null) reportWrapper.eq(BizWorkReport::getWorkOrderId, workOrderId);
        if (batchNo != null) reportWrapper.eq(BizWorkReport::getBatchNo, batchNo);
        reportWrapper.orderByDesc(BizWorkReport::getCreateTime);
        List<BizWorkReport> reports = workReportMapper.selectList(reportWrapper);
        result.put("workReports", reports);

        // 3. 检验追溯 - 质检任务
        LambdaQueryWrapper<BizQcTask> qcWrapper = new LambdaQueryWrapper<>();
        if (workOrderId != null) qcWrapper.eq(BizQcTask::getWorkOrderId, workOrderId);
        if (productId != null) qcWrapper.eq(BizQcTask::getProductId, productId);
        if (batchNo != null) qcWrapper.eq(BizQcTask::getBatchNo, batchNo);
        qcWrapper.orderByDesc(BizQcTask::getCreateTime);
        List<BizQcTask> qcTasks = list(qcWrapper);
        result.put("qcTasks", qcTasks);

        // 4. 缺陷追溯 - 缺陷记录
        LambdaQueryWrapper<BizDefectRecord> defectWrapper = new LambdaQueryWrapper<>();
        if (workOrderId != null) defectWrapper.eq(BizDefectRecord::getWorkOrderId, workOrderId);
        if (productId != null) defectWrapper.eq(BizDefectRecord::getProductId, productId);
        if (batchNo != null) defectWrapper.eq(BizDefectRecord::getBatchNo, batchNo);
        defectWrapper.orderByDesc(BizDefectRecord::getCreateTime);
        List<BizDefectRecord> defects = defectRecordMapper.selectList(defectWrapper);
        result.put("defectRecords", defects);

        // 5. 物料批次追溯 - 按批次号查关联质检
        if (batchNo != null) {
            result.put("batchNo", batchNo);
        }

        return result;
    }

    // ─── 质量分析 ──────────────────────────────────────────────────────────────

    /** 根据 period 计算起始日期 */
    private LocalDateTime getPeriodStart(String period) {
        LocalDate today = LocalDate.now();
        return switch (period) {
            case "year"    -> today.withDayOfYear(1).atStartOfDay();
            case "quarter" -> {
                int q = (today.getMonthValue() - 1) / 3;
                yield today.withMonth(q * 3 + 1).withDayOfMonth(1).atStartOfDay();
            }
            default        -> today.withDayOfMonth(1).atStartOfDay(); // month
        };
    }

    /**
     * 质量分析总览
     * 返回：totalTasks / passCount / failCount / passRate / defectCount
     */
    public Map<String, Object> getAnalysisOverview(String period) {
        LocalDateTime start = getPeriodStart(period);
        List<BizQcTask> tasks = list(new LambdaQueryWrapper<BizQcTask>()
                .ge(BizQcTask::getCreateTime, start));

        long total = tasks.size();
        long pass  = tasks.stream().filter(t -> "pass".equals(t.getResult()) || "passed".equals(t.getResult())
                || ("completed".equals(t.getResult()) && (t.getFailQty() == null || t.getFailQty().compareTo(BigDecimal.ZERO) == 0))).count();
        long fail  = tasks.stream().filter(t -> "fail".equals(t.getResult()) || "failed".equals(t.getResult())
                || ("completed".equals(t.getResult()) && t.getFailQty() != null && t.getFailQty().compareTo(BigDecimal.ZERO) > 0)).count();

        long defectCount = defectRecordMapper.selectCount(new LambdaQueryWrapper<BizDefectRecord>()
                .ge(BizDefectRecord::getCreateTime, start));

        BigDecimal passRate = total > 0
                ? BigDecimal.valueOf(pass * 100.0 / total).setScale(1, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalTasks",  total);
        result.put("passCount",   pass);
        result.put("failCount",   fail);
        result.put("passRate",    passRate);
        result.put("defectCount", defectCount);
        return result;
    }

    /**
     * 合格率趋势（按日统计，最近 N 天）
     * 返回列表：[{ date, total, pass, passRate }]
     */
    public List<Map<String, Object>> getPassRateTrend(String period) {
        LocalDate today = LocalDate.now();
        int days = switch (period) {
            case "year"    -> 12; // 按月，返回近12个月
            case "quarter" -> 13; // 按周，返回近13周
            default        -> 30; // 按天，返回近30天
        };

        List<Map<String, Object>> trend = new ArrayList<>();

        if ("year".equals(period)) {
            // 按月统计
            for (int i = days - 1; i >= 0; i--) {
                LocalDate monthStart = today.withDayOfMonth(1).minusMonths(i);
                LocalDate monthEnd   = monthStart.plusMonths(1);
                String label = monthStart.toString().substring(0, 7); // yyyy-MM
                addTrendEntry(trend, label,
                        monthStart.atStartOfDay(), monthEnd.atStartOfDay());
            }
        } else if ("quarter".equals(period)) {
            // 按周统计
            for (int i = days - 1; i >= 0; i--) {
                LocalDate weekStart = today.minusWeeks(i).with(java.time.DayOfWeek.MONDAY);
                LocalDate weekEnd   = weekStart.plusWeeks(1);
                String label = weekStart.toString();
                addTrendEntry(trend, label,
                        weekStart.atStartOfDay(), weekEnd.atStartOfDay());
            }
        } else {
            // 按天统计
            for (int i = days - 1; i >= 0; i--) {
                LocalDate day = today.minusDays(i);
                String label = day.toString();
                addTrendEntry(trend, label,
                        day.atStartOfDay(), day.plusDays(1).atStartOfDay());
            }
        }
        return trend;
    }

    private void addTrendEntry(List<Map<String, Object>> trend, String label,
                               LocalDateTime from, LocalDateTime to) {
        List<BizQcTask> tasks = list(new LambdaQueryWrapper<BizQcTask>()
                .ge(BizQcTask::getCreateTime, from)
                .lt(BizQcTask::getCreateTime, to));
        long total = tasks.size();
        long pass  = tasks.stream().filter(t -> "pass".equals(t.getResult()) || "passed".equals(t.getResult())
                || ("completed".equals(t.getResult()) && (t.getFailQty() == null || t.getFailQty().compareTo(BigDecimal.ZERO) == 0))).count();
        BigDecimal rate = total > 0
                ? BigDecimal.valueOf(pass * 100.0 / total).setScale(1, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("date",     label);
        entry.put("total",    total);
        entry.put("pass",     pass);
        entry.put("passRate", rate);
        trend.add(entry);
    }

    /**
     * 不良类型分布
     * 返回列表：[{ defectType, defectTypeName, count, rate }]
     */
    public List<Map<String, Object>> getDefectDistribution(String period) {
        LocalDateTime start = getPeriodStart(period);
        List<BizDefectRecord> defects = defectRecordMapper.selectList(
                new LambdaQueryWrapper<BizDefectRecord>()
                        .ge(BizDefectRecord::getCreateTime, start));

        if (defects.isEmpty()) return new ArrayList<>();

        Map<String, Long> countMap = defects.stream()
                .collect(Collectors.groupingBy(
                        d -> d.getDefectType() != null ? d.getDefectType() : "other",
                        Collectors.counting()));

        long total = defects.size();
        return countMap.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("defectType",     e.getKey());
                    m.put("defectTypeName", getDefectTypeName(e.getKey()));
                    m.put("count",          e.getValue());
                    m.put("rate", BigDecimal.valueOf(e.getValue() * 100.0 / total)
                            .setScale(1, java.math.RoundingMode.HALF_UP));
                    return m;
                })
                .collect(Collectors.toList());
    }

    private String getDefectTypeName(String type) {
        if (type == null) return "其他";
        return switch (type) {
            case "size"       -> "尺寸偏差";
            case "appearance" -> "外观缺陷";
            case "function"   -> "功能异常";
            case "material"   -> "材料问题";
            case "process"    -> "工艺问题";
            case "assembly"   -> "装配问题";
            case "other"      -> "其他";
            default           -> type;
        };
    }
}
