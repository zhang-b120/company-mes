package com.aimes.modules.production.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.modules.mrp.entity.BizProductionPlanItemStep;
import com.aimes.modules.mrp.mapper.BizProductionPlanItemStepMapper;
import com.aimes.modules.production.entity.BizProcessDrawing;
import com.aimes.modules.production.entity.BizWoProcessTask;
import com.aimes.modules.production.entity.BizWorkOrder;
import com.aimes.modules.production.mapper.BizProcessDrawingMapper;
import com.aimes.modules.production.mapper.BizWoProcessTaskMapper;
import com.aimes.modules.production.mapper.BizWorkOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工序图纸服务（版本化）
 *
 * 数据流：
 * 1) 排产阶段上传：drawing.plan_item_step_id 有值，version 固定=1，不限数量
 * 2) 派发(下达工单)：调用 copyOnDispatch，把 plan_item_step 下所有图纸复制为 process_task 关联，version 重置为1
 * 3) 工单中再上传：drawing.process_task_id 有值，version = 该 task 当前 max + 1（前端可手改但不能小于）
 *
 * 校验：
 * - 计划已派发后，禁止再修改/上传/删除该计划工序的图纸
 * - 工单图纸"图纸变更"角标判定：max(version) > 1
 */
@Service
@RequiredArgsConstructor
public class BizProcessDrawingService extends ServiceImpl<BizProcessDrawingMapper, BizProcessDrawing> {

    private final BizProductionPlanItemStepMapper planItemStepMapper;
    private final BizWorkOrderMapper workOrderMapper;
    private final BizWoProcessTaskMapper woProcessTaskMapper;

    // ========== 列表查询 ==========

    /** 计划工序(planItemStepId)下的图纸（排产场景） */
    public List<BizProcessDrawing> listByPlanItemStep(Long planItemStepId) {
        if (planItemStepId == null) return List.of();
        return list(new LambdaQueryWrapper<BizProcessDrawing>()
                .eq(BizProcessDrawing::getPlanItemStepId, planItemStepId)
                .orderByDesc(BizProcessDrawing::getId));
    }

    /** 计划行项下所有计划工序的图纸（按 planItemStepId 分组返回 Map） */
    public Map<Long, List<BizProcessDrawing>> listByPlanItem(Long planItemId) {
        Map<Long, List<BizProcessDrawing>> result = new LinkedHashMap<>();
        if (planItemId == null) return result;
        // 先查该行项下所有工序ID
        List<Long> stepIds = planItemStepMapper.selectList(
                new LambdaQueryWrapper<BizProductionPlanItemStep>()
                        .eq(BizProductionPlanItemStep::getPlanItemId, planItemId))
                .stream().map(BizProductionPlanItemStep::getId).toList();
        if (stepIds.isEmpty()) return result;
        List<BizProcessDrawing> drawings = list(new LambdaQueryWrapper<BizProcessDrawing>()
                .in(BizProcessDrawing::getPlanItemStepId, stepIds)
                .orderByDesc(BizProcessDrawing::getId));
        return drawings.stream().collect(Collectors.groupingBy(BizProcessDrawing::getPlanItemStepId));
    }

    /** 工单工序任务(processTaskId)下的图纸（工单场景） */
    public List<BizProcessDrawing> listByProcessTask(Long processTaskId) {
        if (processTaskId == null) return List.of();
        return list(new LambdaQueryWrapper<BizProcessDrawing>()
                .eq(BizProcessDrawing::getProcessTaskId, processTaskId)
                .orderByDesc(BizProcessDrawing::getVersion)
                .orderByDesc(BizProcessDrawing::getId));
    }

    // ========== 上传 ==========

    /**
     * 上传新图纸 - 按 scope 自动分流：
     * - 排产场景：必须传 planItemStepId，version 强制=1
     * - 工单场景：必须传 processTaskId，version = 该 task 最大版本+1（可由前端覆盖但不能小于）
     */
    @Transactional(rollbackFor = Exception.class)
    public BizProcessDrawing upload(BizProcessDrawing payload) {
        if (!StringUtils.hasText(payload.getFileUrl())) throw new BusinessException("文件URL不能为空");

        Long planItemStepId = payload.getPlanItemStepId();
        Long processTaskId = payload.getProcessTaskId();
        if (planItemStepId == null && processTaskId == null) {
            throw new BusinessException("planItemStepId 与 processTaskId 必须至少传一个");
        }
        if (planItemStepId != null && processTaskId != null) {
            throw new BusinessException("planItemStepId 与 processTaskId 不能同时传入");
        }

        if (planItemStepId != null) {
            // 排产场景：检查计划是否已派发
            BizProductionPlanItemStep step = planItemStepMapper.selectById(planItemStepId);
            if (step == null) throw new BusinessException("计划工序不存在");
            if (isPlanItemDispatched(step.getPlanItemId())) {
                throw new BusinessException("该计划已派发工单，请前往工单详情管理图纸");
            }
            // version 强制 = 1，不允许修改
            payload.setVersion(1);
            // 冗余字段
            if (payload.getStepNo() == null) payload.setStepNo(step.getStepNo());
            if (!StringUtils.hasText(payload.getStepName())) payload.setStepName(step.getStepName());
            payload.setProcessTaskId(null);
            payload.setWorkOrderId(null);
        } else {
            // 工单场景
            BizWoProcessTask task = woProcessTaskMapper.selectById(processTaskId);
            if (task == null) throw new BusinessException("工单工序不存在");
            Integer maxVersion = list(new LambdaQueryWrapper<BizProcessDrawing>()
                    .eq(BizProcessDrawing::getProcessTaskId, processTaskId)
                    .orderByDesc(BizProcessDrawing::getVersion).last("LIMIT 1"))
                    .stream().findFirst().map(BizProcessDrawing::getVersion).orElse(0);
            if (payload.getVersion() == null) {
                payload.setVersion(maxVersion + 1);
            } else if (payload.getVersion() <= maxVersion) {
                throw new BusinessException("版本号必须大于当前最大版本(" + maxVersion + ")");
            }
            // 冗余字段
            if (payload.getStepNo() == null) payload.setStepNo(task.getStepNo());
            if (!StringUtils.hasText(payload.getStepName())) payload.setStepName(task.getStepName());
            if (payload.getWorkOrderId() == null) payload.setWorkOrderId(task.getWorkOrderId());
            payload.setPlanItemStepId(null);
        }
        payload.setUploadedAt(LocalDateTime.now());
        save(payload);
        return payload;
    }

    // ========== 删除 ==========

    @Transactional(rollbackFor = Exception.class)
    public void deleteDrawing(Long id) {
        BizProcessDrawing d = getById(id);
        if (d == null) throw new BusinessException("图纸不存在");
        if (d.getPlanItemStepId() != null) {
            BizProductionPlanItemStep step = planItemStepMapper.selectById(d.getPlanItemStepId());
            if (step != null && isPlanItemDispatched(step.getPlanItemId())) {
                throw new BusinessException("该计划已派发工单，计划图纸不可删除");
            }
        }
        removeById(id);
    }

    // ========== 派发同步（在生成工单后调用） ==========

    /**
     * 把计划工序的所有图纸复制到对应工单工序任务上。
     * - 按 stepNo 匹配
     * - 复制后 version 重置为1（同一计划全部 v1，工单复制后也是 v1）
     * - 原 plan_item_step 上的图纸保留作为 baseline，但派发后不可再编辑
     */
    @Transactional(rollbackFor = Exception.class)
    public int copyOnDispatch(Long planItemId, Long workOrderId) {
        if (planItemId == null || workOrderId == null) return 0;
        // 计划工序
        List<BizProductionPlanItemStep> planSteps = planItemStepMapper.selectList(
                new LambdaQueryWrapper<BizProductionPlanItemStep>()
                        .eq(BizProductionPlanItemStep::getPlanItemId, planItemId));
        if (planSteps.isEmpty()) return 0;
        Map<Long, BizProductionPlanItemStep> planStepById = planSteps.stream()
                .collect(Collectors.toMap(BizProductionPlanItemStep::getId, s -> s, (a, b) -> a));

        // 工单工序
        List<BizWoProcessTask> woTasks = woProcessTaskMapper.selectList(
                new LambdaQueryWrapper<BizWoProcessTask>()
                        .eq(BizWoProcessTask::getWorkOrderId, workOrderId));
        if (woTasks.isEmpty()) return 0;

        // 计划工序图纸
        List<Long> planStepIds = planSteps.stream().map(BizProductionPlanItemStep::getId).toList();
        List<BizProcessDrawing> planDrawings = list(new LambdaQueryWrapper<BizProcessDrawing>()
                .in(BizProcessDrawing::getPlanItemStepId, planStepIds));
        if (planDrawings.isEmpty()) return 0;

        // 按 stepNo 索引计划图纸
        Map<Integer, List<BizProcessDrawing>> planDrawingsByStepNo = new LinkedHashMap<>();
        for (BizProcessDrawing d : planDrawings) {
            BizProductionPlanItemStep step = planStepById.get(d.getPlanItemStepId());
            if (step == null || step.getStepNo() == null) continue;
            planDrawingsByStepNo.computeIfAbsent(step.getStepNo(), k -> new java.util.ArrayList<>()).add(d);
        }

        // 复制到 wo_process_task
        int copied = 0;
        for (BizWoProcessTask task : woTasks) {
            List<BizProcessDrawing> sourceDrawings = task.getStepNo() != null ? planDrawingsByStepNo.get(task.getStepNo()) : null;
            if (sourceDrawings == null || sourceDrawings.isEmpty()) continue;
            for (BizProcessDrawing src : sourceDrawings) {
                BizProcessDrawing copy = new BizProcessDrawing();
                copy.setProductId(src.getProductId());
                copy.setStepNo(task.getStepNo());
                copy.setStepName(task.getStepName());
                copy.setProcessTaskId(task.getId());
                copy.setWorkOrderId(workOrderId);
                copy.setPlanItemStepId(null);
                copy.setVersion(1);
                copy.setFileName(src.getFileName());
                copy.setFileUrl(src.getFileUrl());
                copy.setFileSize(src.getFileSize());
                copy.setRemark(src.getRemark());
                copy.setUploadedBy(src.getUploadedBy());
                copy.setUploadedAt(LocalDateTime.now());
                save(copy);
                copied++;
            }
        }
        return copied;
    }

    // ========== 工单图纸摘要（用于"图纸变更"角标） ==========

    /**
     * 按 process_task_id 列表返回每个 task 的图纸摘要：
     * { processTaskId → { latest, total, hasNewVersion } }
     * - latest: 最新版本图纸
     * - total: 该 task 版本数
     * - hasNewVersion: max(version) > 1（即派发后又重新上传过）
     */
    public Map<Long, Map<String, Object>> summaryByTasks(List<Long> processTaskIds) {
        Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
        if (processTaskIds == null || processTaskIds.isEmpty()) return result;
        List<BizProcessDrawing> all = list(new LambdaQueryWrapper<BizProcessDrawing>()
                .in(BizProcessDrawing::getProcessTaskId, processTaskIds)
                .orderByDesc(BizProcessDrawing::getVersion));
        Map<Long, List<BizProcessDrawing>> byTask = all.stream()
                .collect(Collectors.groupingBy(BizProcessDrawing::getProcessTaskId));
        for (Long taskId : processTaskIds) {
            List<BizProcessDrawing> drawings = byTask.getOrDefault(taskId, List.of());
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("latest", drawings.isEmpty() ? null : drawings.get(0));
            info.put("total", drawings.size());
            int maxV = drawings.stream().mapToInt(d -> d.getVersion() == null ? 0 : d.getVersion()).max().orElse(0);
            info.put("hasNewVersion", maxV > 1);
            result.put(taskId, info);
        }
        return result;
    }

    // ========== 内部工具 ==========

    /** 判断某 plan_item 是否已经派发过工单（用于禁用计划侧图纸编辑） */
    private boolean isPlanItemDispatched(Long planItemId) {
        if (planItemId == null) return false;
        long count = workOrderMapper.selectCount(new LambdaQueryWrapper<BizWorkOrder>()
                .eq(BizWorkOrder::getPlanItemId, planItemId));
        return count > 0;
    }

    /** 计划工序是否可编辑图纸 */
    public boolean planItemStepEditable(Long planItemStepId) {
        if (planItemStepId == null) return false;
        BizProductionPlanItemStep step = planItemStepMapper.selectById(planItemStepId);
        if (step == null) return false;
        return !isPlanItemDispatched(step.getPlanItemId());
    }
}
