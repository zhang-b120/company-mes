package com.aimes.modules.inventory.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.CodeUtil;
import com.aimes.common.utils.SecurityUtil;
import com.aimes.modules.inventory.entity.BizInvRequest;
import com.aimes.modules.inventory.entity.BizInvRequestItem;
import com.aimes.modules.inventory.entity.BizInvTask;
import com.aimes.modules.inventory.entity.BizInvTaskItem;
import com.aimes.modules.inventory.mapper.BizInvRequestItemMapper;
import com.aimes.modules.inventory.mapper.BizInvRequestMapper;
import com.aimes.modules.inventory.mapper.BizInvTaskItemMapper;
import com.aimes.modules.inventory.mapper.BizInvTaskMapper;
import com.aimes.modules.system.entity.SysWarehouse;
import com.aimes.modules.system.mapper.SysUserMapper;
import com.aimes.modules.system.mapper.SysWarehouseMapper;
import com.aimes.modules.system.service.SysBizConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BizInvRequestService extends ServiceImpl<BizInvRequestMapper, BizInvRequest> {

    private final BizInvRequestItemMapper itemMapper;
    private final BizInvTaskMapper taskMapper;
    private final BizInvTaskItemMapper taskItemMapper;
    private final BizInvTaskService invTaskService;
    private final SysBizConfigService bizConfigService;
    private final SysWarehouseMapper warehouseMapper;
    private final SysUserMapper userMapper;

    private static final Set<String> INBOUND_TYPES = Set.of(
            "purchase_in", "production_in", "semi_in", "outsource_in", "return_in", "other_in", "transfer_in");
    private static final Set<String> OUTBOUND_TYPES = Set.of(
            "production_out", "sales_out", "outsource_out", "scrap_out", "other_out", "transfer_out");

    public PageResult<Map<String, Object>> getPage(String requestNo, String invType, String invTypeGroup,
                                                    String status, String relatedOrderNo, String keyword,
                                                    String customerName, Long customerId,
                                                    String dateStart, String dateEnd,
                                                    int pageNum, int pageSize) {
        LambdaQueryWrapper<BizInvRequest> wrapper = new LambdaQueryWrapper<BizInvRequest>()
                .like(StringUtils.hasText(requestNo), BizInvRequest::getRequestNo, requestNo)
                .in("out".equals(invTypeGroup), BizInvRequest::getInvType, OUTBOUND_TYPES)
                .in("in".equals(invTypeGroup), BizInvRequest::getInvType, INBOUND_TYPES)
                .eq(StringUtils.hasText(invType), BizInvRequest::getInvType, invType)
                .eq(StringUtils.hasText(status), BizInvRequest::getStatus, status)
                .like(StringUtils.hasText(relatedOrderNo), BizInvRequest::getRelatedOrderNo, relatedOrderNo)
                .eq(customerId != null, BizInvRequest::getCustomerId, customerId)
                .like(StringUtils.hasText(customerName), BizInvRequest::getCustomerName, customerName)
                .ge(StringUtils.hasText(dateStart), BizInvRequest::getCreateTime, dateStart + " 00:00:00")
                .le(StringUtils.hasText(dateEnd), BizInvRequest::getCreateTime, dateEnd + " 23:59:59")
                .orderByDesc(BizInvRequest::getCreateTime);

        Page<BizInvRequest> page = page(new Page<>(pageNum, pageSize), wrapper);
        List<BizInvRequest> records = page.getRecords();

        // 批量加载仓库名/库位名/申请人名
        Set<Long> whIds = records.stream().map(BizInvRequest::getWarehouseId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> locIds = records.stream().map(BizInvRequest::getLocationId).filter(Objects::nonNull).collect(Collectors.toSet());
        whIds.addAll(locIds);
        Map<Long, String> whNameMap = whIds.isEmpty() ? new HashMap<>() :
                warehouseMapper.selectBatchIds(whIds).stream()
                        .collect(Collectors.toMap(SysWarehouse::getId, SysWarehouse::getWarehouseName, (a, b) -> a));
        Set<Long> userIds = records.stream().map(BizInvRequest::getApplicantId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> userNameMap = userIds.isEmpty() ? new HashMap<>() :
                userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(com.aimes.modules.system.entity.SysUser::getId,
                                com.aimes.modules.system.entity.SysUser::getRealName, (a, b) -> a));

        List<Map<String, Object>> list = records.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("requestNo", r.getRequestNo());
            m.put("invType", r.getInvType());
            m.put("customerId", r.getCustomerId());
            m.put("customerName", r.getCustomerName());
            m.put("relatedOrderNo", r.getRelatedOrderNo());
            m.put("batchNo", r.getBatchNo());
            m.put("warehouseId", r.getWarehouseId());
            m.put("warehouseName", whNameMap.getOrDefault(r.getWarehouseId(), ""));
            m.put("locationId", r.getLocationId());
            m.put("locationName", whNameMap.getOrDefault(r.getLocationId(), ""));
            m.put("status", r.getStatus());
            m.put("applicantId", r.getApplicantId());
            m.put("applicantName", userNameMap.getOrDefault(r.getApplicantId(), ""));
            m.put("remark", r.getRemark());
            m.put("createTime", r.getCreateTime());
            return m;
        }).collect(Collectors.toList());

        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    /**
     * 导出出入库申请（按搜索条件,最多 10000 条）
     */
    public void export(String requestNo, String invType, String invTypeGroup, String status,
                       String relatedOrderNo, String customerName, Long customerId,
                       String dateStart, String dateEnd,
                       jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        PageResult<Map<String, Object>> all = getPage(requestNo, invType, invTypeGroup, status, relatedOrderNo,
                null, customerName, customerId, dateStart, dateEnd, 1, 10000);
        List<String> headers = List.of("申请单号", "出库类型", "客户名称", "关联单据号", "批次号",
                "出库仓库", "库位", "状态", "申请人", "创建时间", "备注");
        List<java.util.function.Function<Map<String, Object>, Object>> extractors = List.of(
                r -> r.get("requestNo"), r -> invTypeLabel((String) r.get("invType")),
                r -> r.get("customerName"), r -> r.get("relatedOrderNo"),
                r -> r.get("batchNo"), r -> r.get("warehouseName"),
                r -> r.get("locationName"), r -> statusLabel((String) r.get("status")),
                r -> r.get("applicantName"), r -> r.get("createTime"), r -> r.get("remark"));
        com.aimes.common.utils.ExcelUtil.export(response, "出库申请", headers, all.getRecords(), extractors);
    }

    private static String invTypeLabel(String v) {
        if (v == null) return "";
        return switch (v) {
            case "production_out" -> "生产领料";
            case "sales_out" -> "销售发货";
            case "outsource_out" -> "外协发料";
            case "scrap_out" -> "报废出库";
            case "other_out" -> "其他出库";
            case "transfer_out" -> "调拨出库";
            case "purchase_in" -> "采购入库";
            case "production_in" -> "生产入库";
            case "semi_in" -> "半成品入库";
            case "outsource_in" -> "外协收货";
            case "return_in" -> "退货入库";
            case "other_in" -> "其他入库";
            case "transfer_in" -> "调拨入库";
            default -> v;
        };
    }

    private static String statusLabel(String s) {
        if (s == null) return "";
        return switch (s) {
            case "pending" -> "待审核";
            case "approved" -> "已审批";
            case "rejected" -> "已驳回";
            case "task_created" -> "已生成任务";
            default -> s;
        };
    }

    public Map<String, Object> getDetail(Long id) {
        BizInvRequest request = getById(id);
        if (request == null) throw new BusinessException("入库申请不存在");

        List<BizInvRequestItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<BizInvRequestItem>().eq(BizInvRequestItem::getRequestId, id));

        // 收集主表+明细中的仓库/库位id
        Set<Long> whIds = new HashSet<>();
        if (request.getWarehouseId() != null) whIds.add(request.getWarehouseId());
        if (request.getLocationId() != null) whIds.add(request.getLocationId());
        items.forEach(i -> {
            if (i.getWarehouseId() != null) whIds.add(i.getWarehouseId());
            if (i.getLocationId() != null) whIds.add(i.getLocationId());
        });
        Map<Long, String> whNameMap = whIds.isEmpty() ? new HashMap<>() :
                warehouseMapper.selectBatchIds(whIds).stream()
                        .collect(Collectors.toMap(SysWarehouse::getId, SysWarehouse::getWarehouseName, (a, b) -> a));

        // 明细转 map，附带 warehouseName/locationName
        List<Map<String, Object>> itemMaps = items.stream().map(i -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i.getId());
            m.put("materialId", i.getMaterialId());
            m.put("materialNo", i.getMaterialNo());
            m.put("materialName", i.getMaterialName());
            m.put("spec", i.getSpec());
            m.put("unit", i.getUnit());
            m.put("quantity", i.getQuantity());
            m.put("unitPrice", i.getUnitPrice());
            m.put("totalPrice", i.getTotalPrice());
            m.put("warehouseId", i.getWarehouseId());
            m.put("warehouseName", whNameMap.getOrDefault(i.getWarehouseId(), ""));
            m.put("locationId", i.getLocationId());
            m.put("locationName", whNameMap.getOrDefault(i.getLocationId(), ""));
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", request.getId());
        detail.put("requestNo", request.getRequestNo());
        detail.put("invType", request.getInvType());
        detail.put("customerId", request.getCustomerId());
        detail.put("customerName", request.getCustomerName());
        detail.put("relatedOrderNo", request.getRelatedOrderNo());
        detail.put("batchNo", request.getBatchNo());
        detail.put("warehouseId", request.getWarehouseId());
        detail.put("warehouseName", whNameMap.getOrDefault(request.getWarehouseId(), ""));
        detail.put("locationId", request.getLocationId());
        detail.put("locationName", whNameMap.getOrDefault(request.getLocationId(), ""));
        detail.put("status", request.getStatus());
        detail.put("remark", request.getRemark());
        detail.put("attachmentUrls", request.getAttachmentUrls());
        detail.put("applicantId", request.getApplicantId());
        detail.put("createTime", request.getCreateTime());
        detail.put("items", itemMaps);
        return detail;
    }

    /**
     * 根据申请单号查询详情
     */
    public Map<String, Object> getDetailByNo(String requestNo) {
        BizInvRequest request = getOne(new LambdaQueryWrapper<BizInvRequest>()
                .eq(BizInvRequest::getRequestNo, requestNo));
        if (request == null) return null;
        return getDetail(request.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void create(Map<String, Object> body) {
        BizInvRequest request = new BizInvRequest();
        // 支持自定义申请单号：填写则校验唯一性，否则自动生成
        String customRequestNo = body.get("requestNo") != null ? body.get("requestNo").toString().trim() : "";
        if (!customRequestNo.isEmpty()) {
            long count = count(new LambdaQueryWrapper<BizInvRequest>()
                    .eq(BizInvRequest::getRequestNo, customRequestNo));
            if (count > 0) throw new BusinessException("申请单号[" + customRequestNo + "]已存在，请使用其他单号");
            request.setRequestNo(customRequestNo);
        } else {
            request.setRequestNo(CodeUtil.generate("INR"));
        }
        String invType = body.get("invType") != null ? body.get("invType").toString() : null;
        request.setInvType(invType);
        if ("return_in".equals(invType) || "sales_out".equals(invType)) {
            request.setCustomerId(body.get("customerId") != null ? Long.valueOf(body.get("customerId").toString()) : null);
            request.setCustomerName(body.get("customerName") != null ? body.get("customerName").toString() : null);
        }
        request.setRelatedOrderNo(body.get("relatedOrderNo") != null ? body.get("relatedOrderNo").toString() : null);
        // 批次号：填写则使用，否则按入/出库类型自动生成
        String batchNo = body.get("batchNo") != null ? body.get("batchNo").toString().trim() : "";
        if (batchNo.isEmpty()) {
            if (INBOUND_TYPES.contains(invType)) {
                batchNo = generateBatchNo("IT");
            } else if (OUTBOUND_TYPES.contains(invType)) {
                batchNo = generateBatchNo("OT");
            }
        }
        request.setBatchNo(batchNo.isEmpty() ? null : batchNo);
        request.setWarehouseId(body.get("warehouseId") != null ? Long.valueOf(body.get("warehouseId").toString()) : null);
        request.setLocationId(body.get("locationId") != null ? Long.valueOf(body.get("locationId").toString()) : null);
        request.setRemark(body.get("remark") != null ? body.get("remark").toString() : null);
        request.setAttachmentUrls(body.get("attachmentUrls") != null ? body.get("attachmentUrls").toString() : null);
        request.setStatus("pending");
        Long userId = SecurityUtil.getCurrentUserId();
        request.setApplicantId(userId);
        save(request);

        // 保存明细
        List<?> rawItems = body.get("items") instanceof List ? (List<?>) body.get("items") : List.of();
        for (Object rawItem : rawItems) {
            if (!(rawItem instanceof Map)) continue;
            Map<?, ?> itemMap = (Map<?, ?>) rawItem;
            BizInvRequestItem item = new BizInvRequestItem();
            item.setRequestId(request.getId());
            if (itemMap.get("materialId") != null) item.setMaterialId(Long.valueOf(itemMap.get("materialId").toString()));
            if (itemMap.get("materialNo") != null) item.setMaterialNo(itemMap.get("materialNo").toString());
            if (itemMap.get("materialName") != null) item.setMaterialName(itemMap.get("materialName").toString());
            if (itemMap.get("spec") != null) item.setSpec(itemMap.get("spec").toString());
            if (itemMap.get("unit") != null) item.setUnit(itemMap.get("unit").toString());
            if (itemMap.get("quantity") != null) item.setQuantity(new BigDecimal(itemMap.get("quantity").toString()));
            if (itemMap.get("unitPrice") != null) item.setUnitPrice(new BigDecimal(itemMap.get("unitPrice").toString()));
            if (item.getQuantity() != null && item.getUnitPrice() != null) {
                item.setTotalPrice(item.getQuantity().multiply(item.getUnitPrice()));
            }
            if (itemMap.get("warehouseId") != null) item.setWarehouseId(Long.valueOf(itemMap.get("warehouseId").toString()));
            if (itemMap.get("locationId") != null) item.setLocationId(Long.valueOf(itemMap.get("locationId").toString()));
            itemMapper.insert(item);
        }
    }

    /**
     * 编辑申请(仅 rejected/pending 状态可编辑)。保存后状态重置为 pending 等待重新审批。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, Map<String, Object> body) {
        BizInvRequest request = getById(id);
        if (request == null) throw new BusinessException("申请不存在");
        if (!"rejected".equals(request.getStatus()) && !"pending".equals(request.getStatus())) {
            throw new BusinessException("仅待审核或已驳回状态可编辑");
        }

        // 头信息更新(申请单号 / invType / batchNo 不允许修改)
        if (body.containsKey("relatedOrderNo")) {
            Object v = body.get("relatedOrderNo");
            request.setRelatedOrderNo(v != null ? v.toString() : null);
        }
        String invType = request.getInvType();
        if ("return_in".equals(invType) || "sales_out".equals(invType)) {
            if (body.containsKey("customerId")) {
                Object v = body.get("customerId");
                request.setCustomerId(v != null ? Long.valueOf(v.toString()) : null);
            }
            if (body.containsKey("customerName")) {
                Object v = body.get("customerName");
                request.setCustomerName(v != null ? v.toString() : null);
            }
        }
        if (body.containsKey("warehouseId")) {
            Object v = body.get("warehouseId");
            request.setWarehouseId(v != null ? Long.valueOf(v.toString()) : null);
        }
        if (body.containsKey("locationId")) {
            Object v = body.get("locationId");
            request.setLocationId(v != null ? Long.valueOf(v.toString()) : null);
        }
        if (body.containsKey("remark")) {
            Object v = body.get("remark");
            request.setRemark(v != null ? v.toString() : null);
        }
        if (body.containsKey("attachmentUrls")) {
            Object v = body.get("attachmentUrls");
            request.setAttachmentUrls(v != null ? v.toString() : null);
        }
        request.setStatus("pending");
        updateById(request);

        // 明细全替换
        itemMapper.delete(new LambdaQueryWrapper<BizInvRequestItem>().eq(BizInvRequestItem::getRequestId, id));
        List<?> rawItems = body.get("items") instanceof List ? (List<?>) body.get("items") : List.of();
        for (Object rawItem : rawItems) {
            if (!(rawItem instanceof Map)) continue;
            Map<?, ?> itemMap = (Map<?, ?>) rawItem;
            BizInvRequestItem item = new BizInvRequestItem();
            item.setRequestId(id);
            if (itemMap.get("materialId") != null) item.setMaterialId(Long.valueOf(itemMap.get("materialId").toString()));
            if (itemMap.get("materialNo") != null) item.setMaterialNo(itemMap.get("materialNo").toString());
            if (itemMap.get("materialName") != null) item.setMaterialName(itemMap.get("materialName").toString());
            if (itemMap.get("spec") != null) item.setSpec(itemMap.get("spec").toString());
            if (itemMap.get("unit") != null) item.setUnit(itemMap.get("unit").toString());
            if (itemMap.get("quantity") != null) item.setQuantity(new BigDecimal(itemMap.get("quantity").toString()));
            if (itemMap.get("unitPrice") != null) item.setUnitPrice(new BigDecimal(itemMap.get("unitPrice").toString()));
            if (item.getQuantity() != null && item.getUnitPrice() != null) {
                item.setTotalPrice(item.getQuantity().multiply(item.getUnitPrice()));
            }
            if (itemMap.get("warehouseId") != null) item.setWarehouseId(Long.valueOf(itemMap.get("warehouseId").toString()));
            if (itemMap.get("locationId") != null) item.setLocationId(Long.valueOf(itemMap.get("locationId").toString()));
            itemMapper.insert(item);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        BizInvRequest request = getById(id);
        if (request == null) throw new BusinessException("入库申请不存在");
        if (!"pending".equals(request.getStatus())) throw new BusinessException("仅待审核状态可审批通过");
        request.setStatus("approved");
        updateById(request);
        generateTask(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String remark) {
        BizInvRequest request = getById(id);
        if (request == null) throw new BusinessException("入库申请不存在");
        if (!"pending".equals(request.getStatus())) throw new BusinessException("仅待审核状态可驳回");
        request.setStatus("rejected");
        if (StringUtils.hasText(remark)) request.setRemark(remark);
        updateById(request);
    }

    private void generateTask(Long requestId) {
        BizInvRequest request = getById(requestId);
        List<BizInvRequestItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<BizInvRequestItem>().eq(BizInvRequestItem::getRequestId, requestId));

        BizInvTask task = new BizInvTask();
        task.setTaskNo(CodeUtil.generate("INK"));
        task.setBatchNo(request.getBatchNo());
        task.setRelatedOrderNo(request.getRequestNo());
        task.setInvType(request.getInvType());
        task.setWarehouseId(request.getWarehouseId());
        task.setLocationId(request.getLocationId());
        task.setStatus("pending");
        task.setSourceType("inv_request");
        task.setSourceId(requestId);
        task.setApplicantId(request.getApplicantId());
        task.setRemark(request.getRemark());
        taskMapper.insert(task);

        for (BizInvRequestItem ri : items) {
            BizInvTaskItem ti = new BizInvTaskItem();
            ti.setTaskId(task.getId());
            ti.setMaterialId(ri.getMaterialId());
            ti.setMaterialNo(ri.getMaterialNo());
            ti.setMaterialName(ri.getMaterialName());
            ti.setSpec(ri.getSpec());
            ti.setUnit(ri.getUnit());
            ti.setQuantity(ri.getQuantity());
            ti.setUnitPrice(ri.getUnitPrice());
            ti.setTotalPrice(ri.getTotalPrice());
            // 行级仓库优先, 行级没填回退到申请单的仓库 — 出库申请通常只填申请级仓库,
            // 不回退会导致 task_item.warehouseId=null, 处理任务弹窗仓库下拉空着, 业务员要再选一次
            ti.setWarehouseId(ri.getWarehouseId() != null ? ri.getWarehouseId() : request.getWarehouseId());
            ti.setLocationId(ri.getLocationId() != null ? ri.getLocationId() : request.getLocationId());
            ti.setProcessedQty(BigDecimal.ZERO);
            taskItemMapper.insert(ti);
        }

        request.setStatus("task_created");
        updateById(request);

        // 根据业务配置判断是否需要自动处理
        String invType = request.getInvType();
        boolean needProcess = true;
        if ("purchase_in".equals(invType)) {
            needProcess = bizConfigService.getBoolValue("warehouse.purchase_in.need_process", true);
        } else if ("sales_out".equals(invType)) {
            needProcess = bizConfigService.getBoolValue("warehouse.sales_out.need_process", true);
        }
        if (!needProcess) {
            invTaskService.tryAutoProcess(task.getId());
        }
    }

    /**
     * 自动生成批次号：{prefix}+yyyyMMdd+三位序号（当日递增）
     * prefix="IT" 入库批次，prefix="OT" 出库批次
     */
    synchronized String generateBatchNo(String prefix) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String dayPrefix = prefix + today;
        List<BizInvRequest> todayRequests = list(new LambdaQueryWrapper<BizInvRequest>()
                .likeRight(BizInvRequest::getBatchNo, dayPrefix)
                .select(BizInvRequest::getBatchNo));
        int maxSeq = todayRequests.stream()
                .map(BizInvRequest::getBatchNo)
                .filter(no -> no != null && no.length() == dayPrefix.length() + 3)
                .mapToInt(no -> {
                    try { return Integer.parseInt(no.substring(dayPrefix.length())); }
                    catch (NumberFormatException e) { return 0; }
                })
                .max().orElse(0);
        return dayPrefix + String.format("%03d", maxSeq + 1);
    }
}
