package com.aimes.modules.quality.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.CodeUtil;
import com.aimes.common.utils.SecurityUtil;
import com.aimes.modules.inventory.service.BizInvTaskService;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.production.entity.BizReworkOrder;
import com.aimes.modules.production.entity.BizWoProcessTask;
import com.aimes.modules.production.entity.BizWorkOrder;
import com.aimes.modules.production.mapper.BizWoProcessTaskMapper;
import com.aimes.modules.production.mapper.BizWorkOrderMapper;
import com.aimes.modules.production.service.BizReworkOrderService;
import com.aimes.modules.quality.entity.BizQcDisposal;
import com.aimes.modules.quality.entity.BizQcTask;
import com.aimes.modules.quality.mapper.BizQcDisposalMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@lombok.extern.slf4j.Slf4j
@Service
@RequiredArgsConstructor
public class BizQcDisposalService extends ServiceImpl<BizQcDisposalMapper, BizQcDisposal> {

    private final BizQcDisposalMapper disposalMapper;
    private final BizWorkOrderMapper workOrderMapper;
    private final BizWoProcessTaskMapper processTaskMapper;
    private final BizMaterialMapper materialMapper;
    @Lazy
    private final BizInvTaskService invTaskService;
    @Lazy
    private final BizReworkOrderService reworkOrderService;
    private final com.aimes.modules.approval.service.BizApprovalService approvalService;
    @Lazy
    private final BizQcTaskService qcTaskService;
    @Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private com.aimes.modules.workreport.service.WorkReportService workReportService;
    @Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private com.aimes.modules.outsource.service.OutsourceChainService outsourceChainService;

    public PageResult<Map<String, Object>> getPage(String disposalNo, String status, String disposalType,
                                                    int pageNum, int pageSize) {
        LambdaQueryWrapper<BizQcDisposal> wrapper = new LambdaQueryWrapper<BizQcDisposal>()
                .like(StringUtils.hasText(disposalNo), BizQcDisposal::getDisposalNo, disposalNo)
                .eq(StringUtils.hasText(status), BizQcDisposal::getStatus, status)
                .eq(StringUtils.hasText(disposalType), BizQcDisposal::getDisposalType, disposalType)
                .orderByDesc(BizQcDisposal::getCreateTime);
        Page<BizQcDisposal> page = page(new Page<>(pageNum, pageSize), wrapper);

        Set<Long> woIds = page.getRecords().stream().map(BizQcDisposal::getWorkOrderId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> matIds = page.getRecords().stream().map(BizQcDisposal::getMaterialId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> qcIds = page.getRecords().stream().map(BizQcDisposal::getQcTaskId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> woNoMap = woIds.isEmpty() ? Map.of() :
                workOrderMapper.selectBatchIds(woIds).stream().collect(Collectors.toMap(BizWorkOrder::getId, BizWorkOrder::getWoNo, (a, b) -> a));
        Map<Long, String> matNameMap = matIds.isEmpty() ? Map.of() :
                materialMapper.selectBatchIds(matIds).stream().collect(Collectors.toMap(BizMaterial::getId, BizMaterial::getMaterialName, (a, b) -> a));
        Map<Long, String> qcNoMap = qcIds.isEmpty() ? Map.of() :
                qcTaskService.listByIds(qcIds).stream().collect(Collectors.toMap(BizQcTask::getId, BizQcTask::getTaskNo, (a, b) -> a));

        List<Map<String, Object>> list = page.getRecords().stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("disposalNo", d.getDisposalNo());
            m.put("qcTaskId", d.getQcTaskId());
            m.put("qcTaskNo", qcNoMap.getOrDefault(d.getQcTaskId(), ""));
            m.put("workOrderId", d.getWorkOrderId());
            m.put("woNo", woNoMap.getOrDefault(d.getWorkOrderId(), ""));
            m.put("processTaskId", d.getProcessTaskId());
            m.put("materialName", matNameMap.getOrDefault(d.getMaterialId(), ""));
            m.put("stepName", d.getStepName());
            m.put("quantity", d.getQuantity());
            m.put("disposalType", d.getDisposalType());
            m.put("disposalReason", d.getDisposalReason());
            m.put("status", d.getStatus());
            m.put("createTime", d.getCreateTime());
            return m;
        }).toList();
        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    public Map<String, Object> getDetail(Long id) {
        BizQcDisposal d = getById(id);
        if (d == null) throw new BusinessException("质检处理单不存在");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("disposalNo", d.getDisposalNo());
        m.put("qcTaskId", d.getQcTaskId());
        m.put("workOrderId", d.getWorkOrderId());
        m.put("processTaskId", d.getProcessTaskId());
        m.put("materialId", d.getMaterialId());
        m.put("stepName", d.getStepName());
        m.put("quantity", d.getQuantity());
        m.put("disposalType", d.getDisposalType());
        m.put("disposalReason", d.getDisposalReason());
        m.put("status", d.getStatus());
        m.put("reworkOrderId", d.getReworkOrderId());
        m.put("invTaskId", d.getInvTaskId());
        m.put("remark", d.getRemark());
        m.put("createTime", d.getCreateTime());

        BizWorkOrder wo = d.getWorkOrderId() != null ? workOrderMapper.selectById(d.getWorkOrderId()) : null;
        m.put("woNo", wo != null ? wo.getWoNo() : "");
        BizMaterial mat = d.getMaterialId() != null ? materialMapper.selectById(d.getMaterialId()) : null;
        m.put("materialName", mat != null ? mat.getMaterialName() : "");
        m.put("materialNo", mat != null ? mat.getMaterialNo() : "");
        return m;
    }

    /**
     * 创建质检处理单（由质检任务不合格记录自动触发）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createDisposal(BizQcDisposal disposal) {
        disposal.setDisposalNo(CodeUtil.generate("QD"));
        disposal.setStatus("pending");
        save(disposal);
        return disposal.getId();
    }

    /**
     * 处理质检处理单
     */
    @SuppressWarnings("unchecked")
    @Transactional(rollbackFor = Exception.class)
    public void processDisposal(Long id, Map<String, Object> body) {
        BizQcDisposal disposal = getById(id);
        if (disposal == null) throw new BusinessException("质检处理单不存在");
        if (!"pending".equals(disposal.getStatus())) throw new BusinessException("当前状态不允许处理");

        String disposalType = body.get("disposalType").toString();
        String reason = body.get("reason") != null ? body.get("reason").toString() : "";
        disposal.setDisposalType(disposalType);
        disposal.setDisposalReason(reason);
        disposal.setProcessedBy(SecurityUtil.getCurrentUserId());
        disposal.setProcessedAt(LocalDateTime.now());

        switch (disposalType) {
            case "scrap":
                handleScrap(disposal);
                disposal.setStatus("processed");
                updateById(disposal);
                break;
            case "concession": {
                // 让步接收: 有配审批流(businessType=qc_concession)就走审批, 没配就直接执行
                Long instanceId = approvalService.startApproval("qc_concession", disposal.getId(),
                        SecurityUtil.getCurrentUserId());
                if (instanceId != null) {
                    // 有配置流程, 挂起等审批通过事件 (onApprovalCompleted) 再执行 handleConcession
                    disposal.setStatus("pending_approval");
                    updateById(disposal);
                } else {
                    // 没配流程, 直接执行
                    handleConcession(disposal);
                    disposal.setStatus("processed");
                    updateById(disposal);
                }
                break;
            }
            case "rework":
                List<Map<String, Object>> steps = (List<Map<String, Object>>) body.get("steps");
                handleRework(disposal, steps);
                disposal.setStatus("processed");
                updateById(disposal);
                break;
            default:
                throw new BusinessException("不支持的处理方式: " + disposalType);
        }
        // 处置完成后同步 QC 任务的处置状态
        if ("processed".equals(disposal.getStatus()) && disposal.getQcTaskId() != null) {
            markQcTaskDisposed(disposal.getQcTaskId());
        }
    }

    /** 审批通过事件回调: 仅 businessType=qc_concession 时执行 handleConcession */
    @org.springframework.context.event.EventListener
    public void onApprovalCompleted(com.aimes.common.event.ApprovalCompletedEvent ev) {
        if (!"qc_concession".equals(ev.getBusinessType())) return;
        BizQcDisposal disposal = getById(ev.getBusinessId());
        if (disposal == null || !"pending_approval".equals(disposal.getStatus())) return;
        handleConcession(disposal);
        disposal.setStatus("processed");
        updateById(disposal);
        if (disposal.getQcTaskId() != null) markQcTaskDisposed(disposal.getQcTaskId());
    }

    private void markQcTaskDisposed(Long qcTaskId) {
        BizQcTask qcTask = qcTaskService.getById(qcTaskId);
        if (qcTask != null && !"disposed".equals(qcTask.getDisposalStatus())) {
            qcTask.setDisposalStatus("disposed");
            qcTaskService.updateById(qcTask);
        }
    }

    /**
     * 报废处理：生成报废入库库存任务
     */
    private void handleScrap(BizQcDisposal disposal) {
        BizMaterial mat = disposal.getMaterialId() != null ? materialMapper.selectById(disposal.getMaterialId()) : null;
        if (mat == null) throw new BusinessException("物料信息不存在");

        BizQcTask qcTask = qcTaskService.getById(disposal.getQcTaskId());
        String qcTaskNo = qcTask != null ? qcTask.getTaskNo() : "";

        Long warehouseId = mat.getDefaultWarehouseId() != null ? mat.getDefaultWarehouseId() : 1L;
        Long applicantId = SecurityUtil.getCurrentUserId();

        Long invTaskId = invTaskService.createFromQcTask(
                disposal.getQcTaskId(), qcTaskNo, "", null,
                warehouseId, applicantId,
                mat.getId(), mat.getMaterialNo(), mat.getMaterialName(),
                mat.getSpec(), mat.getUnit(), disposal.getQuantity(), BigDecimal.ZERO,
                "scrap_in", qcTaskNo);
        disposal.setInvTaskId(invTaskId);
    }

    /**
     * 让步接收：视为合格，修改关联工单工序的合格数量
     */
    private void handleConcession(BizQcDisposal disposal) {
        if (disposal.getProcessTaskId() == null) return;
        BizWoProcessTask task = processTaskMapper.selectById(disposal.getProcessTaskId());
        if (task == null) return;
        BigDecimal current = task.getQualifiedQty() != null ? task.getQualifiedQty() : BigDecimal.ZERO;
        BigDecimal increment = disposal.getQuantity() != null ? disposal.getQuantity() : java.math.BigDecimal.ZERO;
        task.setQualifiedQty(current.add(increment));
        // 若合格数已达计划数，推进工序状态
        BigDecimal planQty = task.getPlanQty() != null ? task.getPlanQty() : java.math.BigDecimal.ZERO;
        boolean taskBecameCompleted = false;
        if (!"completed".equals(task.getStatus()) && planQty.compareTo(java.math.BigDecimal.ZERO) > 0
                && task.getQualifiedQty().compareTo(planQty) >= 0) {
            task.setStatus("completed");
            task.setQcStatus("pass");
            task.setEndTime(java.time.LocalDateTime.now());
            taskBecameCompleted = true;
        }
        processTaskMapper.updateById(task);
        if (task.getWorkOrderId() != null) {
            if (taskBecameCompleted) {
                try {
                    workReportService.activateNextTask(task.getWorkOrderId(), task.getStepNo());
                } catch (Exception e) {
                    log.error("让步接收后激活下一工序失败: workOrderId={}", task.getWorkOrderId(), e);
                }
            }
            try {
                outsourceChainService.handlePrevStepReported(task.getWorkOrderId(), task, increment);
            } catch (Exception e) {
                log.error("让步接收后触发外协链路失败: workOrderId={}", task.getWorkOrderId(), e);
            }
            try {
                workReportService.updateWorkOrderProgress(task.getWorkOrderId());
            } catch (Exception e) {
                log.error("让步接收后更新工单进度失败: workOrderId={}", task.getWorkOrderId(), e);
            }
            try {
                workReportService.tryCreateLastStepStockIn(task.getWorkOrderId(), task.getStepNo());
            } catch (Exception e) {
                log.error("让步接收后评估入库失败: workOrderId={}", task.getWorkOrderId(), e);
            }
        }
    }

    /**
     * 返修处理：创建返修工单
     */
    private void handleRework(BizQcDisposal disposal, List<Map<String, Object>> steps) {
        BizReworkOrder order = new BizReworkOrder();
        order.setQcTaskId(disposal.getQcTaskId());
        order.setQcDisposalId(disposal.getId());
        order.setWorkOrderId(disposal.getWorkOrderId());
        order.setProcessTaskId(disposal.getProcessTaskId());
        order.setMaterialId(disposal.getMaterialId());
        order.setQuantity(disposal.getQuantity());

        Long reworkId = reworkOrderService.createReworkOrder(order, steps);
        disposal.setReworkOrderId(reworkId);
    }
}
