package com.aimes.modules.eam.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.modules.eam.entity.EamEquipment;
import com.aimes.modules.eam.entity.EamMaintenancePlan;
import com.aimes.modules.eam.entity.EamMaintenanceTask;
import com.aimes.modules.eam.entity.EamMaintenanceTaskDetail;
import com.aimes.modules.eam.mapper.EamEquipmentMapper;
import com.aimes.modules.eam.mapper.EamMaintenancePlanMapper;
import com.aimes.modules.eam.mapper.EamMaintenanceTaskDetailMapper;
import com.aimes.modules.eam.mapper.EamMaintenanceTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EamMaintenanceTaskService extends ServiceImpl<EamMaintenanceTaskMapper, EamMaintenanceTask> {

    private final EamMaintenanceTaskDetailMapper detailMapper;
    private final EamMaintenancePlanMapper planMapper;
    private final EamEquipmentMapper equipmentMapper;

    public PageResult<Map<String, Object>> getPage(String taskCode, Long planId, Long equipmentId, Integer taskStatus,
                                                    int pageNum, int pageSize) {
        LambdaQueryWrapper<EamMaintenanceTask> wrapper = new LambdaQueryWrapper<EamMaintenanceTask>()
                .like(StringUtils.hasText(taskCode), EamMaintenanceTask::getTaskCode, taskCode)
                .eq(planId != null, EamMaintenanceTask::getPlanId, planId)
                .eq(equipmentId != null, EamMaintenanceTask::getEquipmentId, equipmentId)
                .eq(taskStatus != null, EamMaintenanceTask::getTaskStatus, taskStatus)
                .orderByDesc(EamMaintenanceTask::getCreateTime);
        Page<EamMaintenanceTask> page = page(new Page<>(pageNum, pageSize), wrapper);

        List<EamMaintenanceTask> records = page.getRecords();
        // 批量查询关联的计划编号 + 设备编号/名称
        Set<Long> planIds = records.stream().map(EamMaintenanceTask::getPlanId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> equipmentIds = records.stream().map(EamMaintenanceTask::getEquipmentId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> planCodeMap = planIds.isEmpty() ? Map.of() :
                planMapper.selectBatchIds(planIds).stream()
                        .collect(Collectors.toMap(EamMaintenancePlan::getId, EamMaintenancePlan::getPlanCode, (a, b) -> a));
        Map<Long, String> planNameMap = planIds.isEmpty() ? Map.of() :
                planMapper.selectBatchIds(planIds).stream()
                        .collect(Collectors.toMap(EamMaintenancePlan::getId, EamMaintenancePlan::getPlanName, (a, b) -> a));
        Map<Long, EamEquipment> equipMap = equipmentIds.isEmpty() ? Map.of() :
                equipmentMapper.selectBatchIds(equipmentIds).stream()
                        .collect(Collectors.toMap(EamEquipment::getId, e -> e, (a, b) -> a));

        List<Map<String, Object>> list = records.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("taskCode", t.getTaskCode());
            m.put("planId", t.getPlanId());
            m.put("planCode", t.getPlanId() != null ? planCodeMap.get(t.getPlanId()) : null);
            m.put("planName", t.getPlanId() != null ? planNameMap.get(t.getPlanId()) : null);
            m.put("equipmentId", t.getEquipmentId());
            EamEquipment eq = t.getEquipmentId() != null ? equipMap.get(t.getEquipmentId()) : null;
            m.put("equipmentNo", eq != null ? eq.getEquipmentNo() : null);
            m.put("equipmentName", eq != null ? eq.getEquipmentName() : null);
            m.put("executorName", t.getExecutorName());
            m.put("plannedStartTime", t.getPlannedStartTime());
            m.put("plannedEndTime", t.getPlannedEndTime());
            m.put("actualStartTime", t.getActualStartTime());
            m.put("actualEndTime", t.getActualEndTime());
            m.put("taskStatus", t.getTaskStatus());
            m.put("actualHours", t.getActualHours());
            m.put("remark", t.getRemark());
            m.put("createTime", t.getCreateTime());
            return m;
        }).collect(Collectors.toList());
        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    public Map<String, Object> getDetail(Long id) {
        EamMaintenanceTask task = getById(id);
        if (task == null) throw new BusinessException("任务不存在");
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("task", task);
        detail.put("details", detailMapper.selectList(new LambdaQueryWrapper<EamMaintenanceTaskDetail>()
                .eq(EamMaintenanceTaskDetail::getTaskId, id)
                .orderByAsc(EamMaintenanceTaskDetail::getSortNo)));
        return detail;
    }

    /** 开始任务 */
    public void start(Long id) {
        EamMaintenanceTask task = getById(id);
        if (task == null) throw new BusinessException("任务不存在");
        if (task.getTaskStatus() != 0 && task.getTaskStatus() != 4) {
            throw new BusinessException("仅待执行或已暂停状态可开始");
        }
        task.setTaskStatus(1);
        if (task.getActualStartTime() == null) {
            task.setActualStartTime(LocalDateTime.now());
        }
        updateById(task);
    }

    /** 暂停任务 */
    public void pause(Long id) {
        EamMaintenanceTask task = getById(id);
        if (task == null) throw new BusinessException("任务不存在");
        if (task.getTaskStatus() != 1) throw new BusinessException("仅执行中状态可暂停");
        task.setTaskStatus(4);
        updateById(task);
    }

    /** 取消任务 */
    public void cancel(Long id) {
        EamMaintenanceTask task = getById(id);
        if (task == null) throw new BusinessException("任务不存在");
        if (task.getTaskStatus() == 2 || task.getTaskStatus() == 3) {
            throw new BusinessException("已完成或已取消的任务不可取消");
        }
        task.setTaskStatus(3);
        updateById(task);
        refreshPlanExecStatus(task.getPlanId());
    }

    /** 完成任务 */
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id, EamMaintenanceTask payload) {
        EamMaintenanceTask task = getById(id);
        if (task == null) throw new BusinessException("任务不存在");
        if (task.getTaskStatus() != 1) throw new BusinessException("仅执行中状态可完成");

        // 校验：所有检查项必须已完成（不能存在待检状态）
        List<EamMaintenanceTaskDetail> details = detailMapper.selectList(
                new LambdaQueryWrapper<EamMaintenanceTaskDetail>().eq(EamMaintenanceTaskDetail::getTaskId, id));
        for (EamMaintenanceTaskDetail d : details) {
            if (d.getStatus() == null || d.getStatus() == 0) {
                throw new BusinessException("检查项[" + d.getItemName() + "]仍处于待检状态，无法完成任务");
            }
            if (d.getNeedPhoto() != null && d.getNeedPhoto() == 1 && !StringUtils.hasText(d.getPhotoUrl())) {
                throw new BusinessException("检查项[" + d.getItemName() + "]需要上传照片");
            }
        }

        task.setTaskStatus(2);
        if (payload != null) {
            // 完成时允许修改实际开始/结束时间（默认值已在下达任务时填充）
            if (payload.getActualStartTime() != null) task.setActualStartTime(payload.getActualStartTime());
            if (payload.getActualEndTime() != null) task.setActualEndTime(payload.getActualEndTime());
            task.setMaintenanceResult(payload.getMaintenanceResult());
            task.setIssuesFound(payload.getIssuesFound());
            task.setConsumablesUsed(payload.getConsumablesUsed());
            task.setSparePartsReplaced(payload.getSparePartsReplaced());
            task.setActualHours(payload.getActualHours());
        }
        // 兜底：如果仍未设置实际结束时间，使用当前时间
        if (task.getActualEndTime() == null) task.setActualEndTime(LocalDateTime.now());
        updateById(task);
        refreshPlanExecStatus(task.getPlanId());
    }

    /** 更新明细项 */
    public void updateDetail(Long detailId, EamMaintenanceTaskDetail payload) {
        EamMaintenanceTaskDetail existing = detailMapper.selectById(detailId);
        if (existing == null) throw new BusinessException("明细不存在");
        // 任务已完成或已取消时不允许修改明细
        EamMaintenanceTask task = getById(existing.getTaskId());
        if (task != null && (task.getTaskStatus() == 2 || task.getTaskStatus() == 3)) {
            throw new BusinessException("任务已" + (task.getTaskStatus() == 2 ? "完成" : "取消") + "，明细不可修改");
        }
        if (payload.getStatus() != null) existing.setStatus(payload.getStatus());
        if (payload.getResultValue() != null) existing.setResultValue(payload.getResultValue());
        if (payload.getPhotoUrl() != null) existing.setPhotoUrl(payload.getPhotoUrl());
        if (payload.getRemark() != null) existing.setRemark(payload.getRemark());
        detailMapper.updateById(existing);
    }

    /** 任务完成/取消后回写计划 exec_status */
    private void refreshPlanExecStatus(Long planId) {
        if (planId == null) return;
        long total = count(new LambdaQueryWrapper<EamMaintenanceTask>().eq(EamMaintenanceTask::getPlanId, planId));
        if (total == 0) return;
        long active = count(new LambdaQueryWrapper<EamMaintenanceTask>()
                .eq(EamMaintenanceTask::getPlanId, planId)
                .ne(EamMaintenanceTask::getTaskStatus, 3));
        long completed = count(new LambdaQueryWrapper<EamMaintenanceTask>()
                .eq(EamMaintenanceTask::getPlanId, planId).eq(EamMaintenanceTask::getTaskStatus, 2));
        EamMaintenancePlan plan = planMapper.selectById(planId);
        if (plan == null) return;
        if (active == 0) {
            // 全部任务已取消：重置为待下达，允许计划再次下达
            plan.setExecStatus(0);
        } else if (completed == active) {
            plan.setExecStatus(3);
        } else if (completed > 0) {
            plan.setExecStatus(2);
        } else {
            plan.setExecStatus(1);
        }
        planMapper.updateById(plan);
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", count());
        stats.put("pending", count(new LambdaQueryWrapper<EamMaintenanceTask>().eq(EamMaintenanceTask::getTaskStatus, 0)));
        stats.put("inProgress", count(new LambdaQueryWrapper<EamMaintenanceTask>().eq(EamMaintenanceTask::getTaskStatus, 1)));
        stats.put("completed", count(new LambdaQueryWrapper<EamMaintenanceTask>().eq(EamMaintenanceTask::getTaskStatus, 2)));
        stats.put("cancelled", count(new LambdaQueryWrapper<EamMaintenanceTask>().eq(EamMaintenanceTask::getTaskStatus, 3)));
        // 逾期: 计划结束时间 < 当前时间且未完成
        stats.put("overdue", count(new LambdaQueryWrapper<EamMaintenanceTask>()
                .lt(EamMaintenanceTask::getPlannedEndTime, LocalDateTime.now())
                .notIn(EamMaintenanceTask::getTaskStatus, 2, 3)));
        return stats;
    }
}
