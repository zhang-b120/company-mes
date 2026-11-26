package com.aimes.modules.quality.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.CodeUtil;
import com.aimes.common.utils.SecurityUtil;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.master.entity.BizProduct;
import com.aimes.modules.master.mapper.BizProductMapper;
import com.aimes.modules.production.entity.BizWorkOrder;
import com.aimes.modules.production.entity.BizWoProcessTask;
import com.aimes.modules.production.mapper.BizWorkOrderMapper;
import com.aimes.modules.production.mapper.BizWoProcessTaskMapper;
import com.aimes.modules.quality.entity.BizDefectRecord;
import com.aimes.modules.quality.mapper.BizDefectRecordMapper;
import com.aimes.modules.system.entity.SysUser;
import com.aimes.modules.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BizDefectRecordService extends ServiceImpl<BizDefectRecordMapper, BizDefectRecord> {

    private final BizProductMapper productMapper;
    private final BizMaterialMapper materialMapper;
    private final BizWorkOrderMapper workOrderMapper;
    private final BizWoProcessTaskMapper processTaskMapper;
    private final SysUserMapper userMapper;

    private static final Map<String, String> DEFECT_TYPE_NAME_MAP = Map.ofEntries(
            Map.entry("size", "尺寸偏差"), Map.entry("appearance", "外观缺陷"),
            Map.entry("function", "功能异常"), Map.entry("material", "材料问题"),
            Map.entry("process", "工艺问题"), Map.entry("assembly", "装配问题"),
            Map.entry("qc_fail", "质检不合格"), Map.entry("incoming", "来料不良"),
            Map.entry("final", "成品不良"), Map.entry("outsource", "外协不良"),
            Map.entry("other", "其他")
    );

    private static final Map<String, String> HANDLE_PATH_NAME_MAP = Map.ofEntries(
            Map.entry("repair", "返工"), Map.entry("rework", "返工"),
            Map.entry("scrap", "报废"), Map.entry("concession", "让步接收"),
            Map.entry("return", "退货"), Map.entry("REWORK", "返工"),
            Map.entry("SCRAP", "报废"), Map.entry("CONCESSION", "让步接收"),
            Map.entry("RETURN", "退货")
    );

    /**
     * 分页查询缺陷记录
     */
    public PageResult<Map<String, Object>> getPage(String defectNo, String batchNo, String handleStatus,
                                                    String defectType, String defectLevel,
                                                    int pageNum, int pageSize) {
        LambdaQueryWrapper<BizDefectRecord> wrapper = new LambdaQueryWrapper<BizDefectRecord>()
                .like(StringUtils.hasText(defectNo), BizDefectRecord::getDefectNo, defectNo)
                .like(StringUtils.hasText(batchNo), BizDefectRecord::getBatchNo, batchNo)
                .like(StringUtils.hasText(defectType), BizDefectRecord::getDefectType, defectType)
                .eq(StringUtils.hasText(defectLevel), BizDefectRecord::getDefectLevel, defectLevel)
                .orderByDesc(BizDefectRecord::getCreateTime);
        // handle_status 多值兼容: pending / quarantined 都算"待处理", completed / handled 都算"已处理"
        if (StringUtils.hasText(handleStatus)) {
            if ("pending".equals(handleStatus)) {
                wrapper.in(BizDefectRecord::getHandleStatus, java.util.List.of("pending", "quarantined"));
            } else if ("completed".equals(handleStatus) || "handled".equals(handleStatus)) {
                wrapper.in(BizDefectRecord::getHandleStatus, java.util.List.of("completed", "handled"));
            } else {
                wrapper.eq(BizDefectRecord::getHandleStatus, handleStatus);
            }
        }

        Page<BizDefectRecord> page = page(new Page<>(pageNum, pageSize), wrapper);

        List<BizDefectRecord> records = page.getRecords();

        // 批量加载产品/物料名称
        Set<Long> productIds = records.stream().map(BizDefectRecord::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> productNameMap = new HashMap<>();
        if (!productIds.isEmpty()) {
            // 先查产品表
            productMapper.selectBatchIds(productIds).forEach(p -> productNameMap.put(p.getId(), p.getProductName()));
            // 未找到的查物料表
            Set<Long> missing = productIds.stream().filter(id -> !productNameMap.containsKey(id)).collect(Collectors.toSet());
            if (!missing.isEmpty()) {
                materialMapper.selectBatchIds(missing).forEach(m -> productNameMap.put(m.getId(), m.getMaterialName()));
            }
        }

        // 批量加载工单号
        Set<Long> woIds = records.stream().map(BizDefectRecord::getWorkOrderId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> woNoMap = new HashMap<>();
        if (!woIds.isEmpty()) {
            workOrderMapper.selectBatchIds(woIds).forEach(wo -> woNoMap.put(wo.getId(), wo.getWoNo()));
        }

        // 批量加载工序名称
        Set<Long> ptIds = records.stream().map(BizDefectRecord::getProcessTaskId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> stepNameMap = new HashMap<>();
        if (!ptIds.isEmpty()) {
            processTaskMapper.selectBatchIds(ptIds).forEach(pt -> stepNameMap.put(pt.getId(), pt.getStepName()));
        }

        // 批量加载处理人姓名
        Set<Long> handlerIds = records.stream().map(BizDefectRecord::getHandlerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> handlerNameMap = new HashMap<>();
        if (!handlerIds.isEmpty()) {
            userMapper.selectBatchIds(handlerIds).forEach(u ->
                    handlerNameMap.put(u.getId(), u.getRealName() != null ? u.getRealName() : u.getUsername()));
        }

        List<Map<String, Object>> list = records.stream().map(r -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", r.getId());
            map.put("defectNo", r.getDefectNo());
            map.put("sourceType", r.getSourceType());
            map.put("sourceId", r.getSourceId());
            map.put("productId", r.getProductId());
            map.put("productName", r.getProductId() != null ? productNameMap.get(r.getProductId()) : null);
            map.put("materialId", r.getMaterialId());
            map.put("workOrderId", r.getWorkOrderId());
            map.put("workOrderNo", r.getWorkOrderId() != null ? woNoMap.get(r.getWorkOrderId()) : null);
            map.put("processTaskId", r.getProcessTaskId());
            map.put("stepName", r.getProcessTaskId() != null ? stepNameMap.get(r.getProcessTaskId()) : r.getReworkStepName());
            map.put("batchNo", r.getBatchNo());
            map.put("quantity", r.getQuantity());
            map.put("defectType", r.getDefectType() != null ? DEFECT_TYPE_NAME_MAP.getOrDefault(r.getDefectType(), r.getDefectType()) : null);
            map.put("defectTypeCode", r.getDefectType());
            map.put("defectReason", r.getDefectReason());
            map.put("defectLevel", r.getDefectLevel());
            map.put("handlePath", r.getHandlePath());
            map.put("handleStatus", r.getHandleStatus());
            map.put("handleResult", r.getHandlePath() != null ? HANDLE_PATH_NAME_MAP.getOrDefault(r.getHandlePath(), r.getHandlePath()) : null);
            map.put("handleResultCode", r.getHandleResult());
            map.put("handleRemark", r.getHandleRemark());
            map.put("handlerId", r.getHandlerId());
            map.put("handler", r.getHandlerId() != null ? handlerNameMap.get(r.getHandlerId()) : null);
            map.put("handleTime", r.getHandledAt());
            map.put("handledAt", r.getHandledAt());
            map.put("reworkWoId", r.getReworkWoId());
            map.put("reworkStepName", r.getReworkStepName());
            map.put("reworkStepNo", r.getReworkStepNo());
            map.put("scrapQty", r.getScrapQty());
            map.put("scrapCost", r.getScrapCost());
            map.put("scrapReason", r.getScrapReason());
            map.put("photoUrls", r.getPhotoUrls());
            map.put("images", parsePhotoUrls(r.getPhotoUrls()));
            map.put("createBy", r.getCreateBy());
            map.put("createTime", r.getCreateTime());
            map.put("updateBy", r.getUpdateBy());
            map.put("updateTime", r.getUpdateTime());
            return map;
        }).collect(Collectors.toList());

        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    /**
     * 统计各状态数量
     */
    public Map<String, Object> getStats() {
        List<BizDefectRecord> all = list(new LambdaQueryWrapper<BizDefectRecord>()
                .select(BizDefectRecord::getHandleStatus));
        long total = all.size();
        long pending = all.stream().filter(r -> "pending".equals(r.getHandleStatus()) || "quarantined".equals(r.getHandleStatus())).count();
        long handling = all.stream().filter(r -> "handling".equals(r.getHandleStatus())).count();
        long closed = all.stream().filter(r -> "completed".equals(r.getHandleStatus()) || "handled".equals(r.getHandleStatus())).count();
        return Map.of("total", total, "pending", pending, "handling", handling, "closed", closed);
    }

    /** photoUrls 字段兼容: JSON 数组串 / 逗号分隔 / 单条 URL 都能解析为 list */
    private List<String> parsePhotoUrls(String raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        String s = raw.trim();
        if (s.startsWith("[")) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(s, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            } catch (Exception ignore) { /* fall through */ }
        }
        return Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

    /**
     * 创建缺陷记录（自动生成编号）
     * 前端传 workOrderNo / stepName / images，此处转换为 workOrderId / processTaskId / photoUrls
     */
    public void create(Map<String, Object> body) {
        BizDefectRecord record = new BizDefectRecord();
        record.setDefectNo(CodeUtil.generate("DF"));
        record.setHandleStatus("pending");
        record.setSourceType("manual");

        Object workOrderNo = body.get("workOrderNo");
        if (workOrderNo != null && !workOrderNo.toString().isEmpty()) {
            BizWorkOrder wo = workOrderMapper.selectOne(new LambdaQueryWrapper<BizWorkOrder>()
                    .eq(BizWorkOrder::getWoNo, workOrderNo.toString()).last("LIMIT 1"));
            if (wo == null) throw new BusinessException("工单号不存在: " + workOrderNo);
            record.setWorkOrderId(wo.getId());
            record.setProductId(wo.getProductId());

            Object stepName = body.get("stepName");
            if (stepName != null && !stepName.toString().isEmpty()) {
                BizWoProcessTask task = processTaskMapper.selectOne(new LambdaQueryWrapper<BizWoProcessTask>()
                        .eq(BizWoProcessTask::getWorkOrderId, wo.getId())
                        .eq(BizWoProcessTask::getStepName, stepName.toString())
                        .last("LIMIT 1"));
                if (task != null) record.setProcessTaskId(task.getId());
            }
        }

        Object defectType = body.get("defectType");
        if (defectType != null) record.setDefectType(defectType.toString());
        Object defectLevel = body.get("defectLevel");
        if (defectLevel != null) record.setDefectLevel(defectLevel.toString());
        Object quantity = body.get("quantity");
        if (quantity != null) record.setQuantity(new BigDecimal(quantity.toString()));
        Object batchNo = body.get("batchNo");
        if (batchNo != null) record.setBatchNo(batchNo.toString());

        // 不良原因 + 备注合并到 defectReason 字段（实体无 remark 列）
        Object defectReason = body.get("defectReason");
        Object remark = body.get("remark");
        StringBuilder reason = new StringBuilder();
        if (defectReason != null && !defectReason.toString().isEmpty()) reason.append(defectReason);
        if (remark != null && !remark.toString().isEmpty()) {
            if (reason.length() > 0) reason.append(" | ");
            reason.append(remark);
        }
        if (reason.length() > 0) record.setDefectReason(reason.toString());

        // images 数组 → photoUrls JSON 字符串
        Object images = body.get("images");
        if (images instanceof List<?> imgList && !imgList.isEmpty()) {
            List<String> urls = imgList.stream()
                    .map(it -> it == null ? "" : it.toString())
                    .filter(s -> !s.isEmpty())
                    .toList();
            if (!urls.isEmpty()) {
                try {
                    record.setPhotoUrls(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(urls));
                } catch (Exception ignore) {}
            }
        }

        save(record);
    }

    /**
     * 处理缺陷
     */
    public void handle(Long id, Map<String, Object> body) {
        BizDefectRecord record = getById(id);
        if (record == null) throw new BusinessException("缺陷记录不存在");
        if ("completed".equals(record.getHandleStatus())) throw new BusinessException("该缺陷已处理完成");

        String handlePath = body.getOrDefault("handlePath", "").toString();
        Object handleResultObj = body.get("handleResult");
        String handleResult = handleResultObj != null ? handleResultObj.toString() : handlePath;

        record.setHandlePath(handlePath);
        record.setHandleResult(handleResult);
        record.setHandleStatus("completed");
        record.setHandledAt(LocalDateTime.now());
        record.setHandlerId(SecurityUtil.getCurrentUserId());

        Object handleRemark = body.get("handleRemark");
        if (handleRemark != null) record.setHandleRemark(handleRemark.toString());

        // 路径专属字段
        String path = handlePath.toUpperCase();
        if ("SCRAP".equals(path)) {
            Object scrapQty = body.get("scrapQty");
            if (scrapQty != null) record.setScrapQty(new BigDecimal(scrapQty.toString()));
            Object scrapCost = body.get("scrapCost");
            if (scrapCost != null) record.setScrapCost(new BigDecimal(scrapCost.toString()));
            Object scrapReason = body.get("scrapReason");
            if (scrapReason != null) record.setScrapReason(scrapReason.toString());
        } else if ("REWORK".equals(path)) {
            Object reworkStep = body.get("reworkStep");
            if (reworkStep != null) record.setReworkStepName(reworkStep.toString());
        }

        updateById(record);
    }
}
