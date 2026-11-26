package com.aimes.modules.eam.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.CodeUtil;
import com.aimes.modules.eam.entity.*;
import com.aimes.modules.eam.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EamMaintenancePlanService extends ServiceImpl<EamMaintenancePlanMapper, EamMaintenancePlan> {

    private final EamMaintenanceTaskMapper taskMapper;
    private final EamMaintenanceTaskDetailMapper taskDetailMapper;
    private final EamMaintenanceTemplateService templateService;

    public PageResult<EamMaintenancePlan> getPage(String planName, Integer maintenanceType, Integer planStatus,
                                                   int pageNum, int pageSize) {
        LambdaQueryWrapper<EamMaintenancePlan> wrapper = new LambdaQueryWrapper<EamMaintenancePlan>()
                .like(StringUtils.hasText(planName), EamMaintenancePlan::getPlanName, planName)
                .eq(maintenanceType != null, EamMaintenancePlan::getMaintenanceType, maintenanceType)
                .eq(planStatus != null, EamMaintenancePlan::getPlanStatus, planStatus)
                .orderByDesc(EamMaintenancePlan::getCreateTime);
        Page<EamMaintenancePlan> page = page(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    @Transactional(rollbackFor = Exception.class)
    public void create(EamMaintenancePlan plan) {
        if (!StringUtils.hasText(plan.getPlanName())) throw new BusinessException("计划名称不能为空");
        plan.setPlanCode(CodeUtil.generate("MP"));
        if (plan.getPlanStatus() == null) plan.setPlanStatus(0);
        if (plan.getExecStatus() == null) plan.setExecStatus(0);
        save(plan);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, EamMaintenancePlan plan) {
        EamMaintenancePlan existing = getById(id);
        if (existing == null) throw new BusinessException("计划不存在");
        if (existing.getPlanStatus() != 0 && existing.getPlanStatus() != 2) {
            throw new BusinessException("仅草稿或已暂停状态的计划可以编辑");
        }
        plan.setId(id);
        plan.setPlanCode(existing.getPlanCode());
        updateById(plan);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        long taskCount = taskMapper.selectCount(new LambdaQueryWrapper<EamMaintenanceTask>()
                .eq(EamMaintenanceTask::getPlanId, id));
        if (taskCount > 0) throw new BusinessException("该计划已有任务生成，无法删除");
        removeById(id);
    }

    /** 发布: 草稿 → 已发布 */
    public void publish(Long id) {
        EamMaintenancePlan plan = getById(id);
        if (plan == null) throw new BusinessException("计划不存在");
        if (plan.getPlanStatus() != 0 && plan.getPlanStatus() != 2) {
            throw new BusinessException("仅草稿或已暂停状态可发布");
        }
        plan.setPlanStatus(1);
        updateById(plan);
    }

    /** 暂停: 已发布 → 已暂停 */
    public void pause(Long id) {
        EamMaintenancePlan plan = getById(id);
        if (plan == null) throw new BusinessException("计划不存在");
        if (plan.getPlanStatus() != 1) throw new BusinessException("仅已发布状态可暂停");
        plan.setPlanStatus(2);
        updateById(plan);
    }

    /** 取消: 任意状态 → 已取消，同步取消该计划下所有未终态任务 */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        EamMaintenancePlan plan = getById(id);
        if (plan == null) throw new BusinessException("计划不存在");
        if (plan.getPlanStatus() == 3) throw new BusinessException("计划已取消");
        if (plan.getExecStatus() != null && plan.getExecStatus() == 3) {
            throw new BusinessException("该计划任务已全部完成，无法取消");
        }
        plan.setPlanStatus(3);
        // 全部任务连带取消后，将执行状态重置为待下达(0)
        plan.setExecStatus(0);
        updateById(plan);

        // 同步取消该计划下所有未完成/未取消的任务
        List<EamMaintenanceTask> activeTasks = taskMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EamMaintenanceTask>()
                .eq(EamMaintenanceTask::getPlanId, id)
                .notIn(EamMaintenanceTask::getTaskStatus, 2, 3));
        for (EamMaintenanceTask task : activeTasks) {
            task.setTaskStatus(3);
            taskMapper.updateById(task);
        }
    }

    /** 下达任务: 根据计划关联模板的设备集合生成任务 */
    @Transactional(rollbackFor = Exception.class)
    public List<String> assignTasks(Long id) {
        EamMaintenancePlan plan = getById(id);
        if (plan == null) throw new BusinessException("计划不存在");
        if (plan.getPlanStatus() != 1) throw new BusinessException("仅已发布状态的计划可以下达任务");

        // 仅允许下达一次：若已存在未取消的任务则拒绝；全部任务被取消后才允许重新下达
        long activeTaskCount = taskMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EamMaintenanceTask>()
                .eq(EamMaintenanceTask::getPlanId, id)
                .ne(EamMaintenanceTask::getTaskStatus, 3));
        if (activeTaskCount > 0) {
            throw new BusinessException("该计划已下达任务，请先取消所有任务后再重新下达");
        }

        List<Long> equipmentIds = plan.getTemplateId() != null
                ? templateService.getEquipmentIdsByTemplate(plan.getTemplateId())
                : Collections.emptyList();
        if (equipmentIds.isEmpty() && plan.getEquipmentId() != null) {
            equipmentIds = List.of(plan.getEquipmentId());
        }

        List<EamMaintenanceTemplateItem> templateItems = plan.getTemplateId() != null
                ? templateService.getItemsByTemplate(plan.getTemplateId())
                : Collections.emptyList();

        List<String> taskCodes = new ArrayList<>();
        if (equipmentIds.isEmpty()) {
            // 无设备 → 生成通用任务
            EamMaintenanceTask task = buildTask(plan, null);
            taskMapper.insert(task);
            taskCodes.add(task.getTaskCode());
            copyDetails(task.getId(), null, templateItems);
        } else {
            for (Long eid : equipmentIds) {
                EamMaintenanceTask task = buildTask(plan, eid);
                taskMapper.insert(task);
                taskCodes.add(task.getTaskCode());
                copyDetails(task.getId(), eid, templateItems);
            }
        }

        plan.setExecStatus(1);
        updateById(plan);
        return taskCodes;
    }

    private EamMaintenanceTask buildTask(EamMaintenancePlan plan, Long equipmentId) {
        EamMaintenanceTask task = new EamMaintenanceTask();
        task.setTaskCode(CodeUtil.generate("MT"));
        task.setPlanId(plan.getId());
        task.setEquipmentId(equipmentId);
        task.setMaintenanceContent(plan.getMaintenanceContent());
        task.setMaintenanceStandard(plan.getMaintenanceStandard());
        // 计划开始/结束时间：优先取自计划，缺省时回退到 now()/now()+周期
        LocalDateTime plannedStart = plan.getPlannedStartTime() != null ? plan.getPlannedStartTime() : LocalDateTime.now();
        LocalDateTime plannedEnd = plan.getPlannedEndTime();
        if (plannedEnd == null) {
            plannedEnd = plan.getExecutionCycle() != null ? plannedStart.plusDays(plan.getExecutionCycle()) : null;
        }
        task.setPlannedStartTime(plannedStart);
        task.setPlannedEndTime(plannedEnd);
        // 实际开始/结束时间默认取计划时间，完成时可修改
        task.setActualStartTime(plannedStart);
        task.setActualEndTime(plannedEnd);
        task.setTaskStatus(0);
        return task;
    }

    private void copyDetails(Long taskId, Long equipmentId, List<EamMaintenanceTemplateItem> items) {
        for (EamMaintenanceTemplateItem tplItem : items) {
            EamMaintenanceTaskDetail detail = new EamMaintenanceTaskDetail();
            detail.setTaskId(taskId);
            detail.setEquipmentId(equipmentId);
            detail.setTemplateItemId(tplItem.getId());
            detail.setItemGroup(tplItem.getItemGroup());
            detail.setItemName(tplItem.getItemName());
            detail.setMethod(tplItem.getMethod());
            detail.setStandard(tplItem.getStandard());
            detail.setUom(tplItem.getUom());
            detail.setRequired(tplItem.getRequired());
            detail.setNeedPhoto(tplItem.getNeedPhoto());
            detail.setSortNo(tplItem.getSortNo());
            detail.setStatus(0);
            taskDetailMapper.insert(detail);
        }
    }
}
