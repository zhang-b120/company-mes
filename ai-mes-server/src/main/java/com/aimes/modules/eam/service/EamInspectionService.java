package com.aimes.modules.eam.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.CodeUtil;
import com.aimes.modules.eam.entity.*;
import com.aimes.modules.eam.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 点检综合服务 - 项目库 / 模板 / 计划 / 任务
 */
@Service
@RequiredArgsConstructor
public class EamInspectionService {

    private final EamInspectionItemMapper itemMapper;
    private final EamInspectionTemplateMapper templateMapper;
    private final EamInspectionTemplateItemMapper templateItemMapper;
    private final EamInspectionTemplateEquipmentMapper templateEquipmentMapper;
    private final EamInspectionPlanMapper planMapper;
    private final EamInspectionTaskMapper taskMapper;
    private final EamInspectionTaskDetailMapper taskDetailMapper;
    private final EamEquipmentMapper equipmentMapper;

    // ========== 点检项目库 ==========

    public PageResult<EamInspectionItem> itemPage(String itemName, String itemType, int pageNum, int pageSize) {
        LambdaQueryWrapper<EamInspectionItem> wrapper = new LambdaQueryWrapper<EamInspectionItem>()
                .like(StringUtils.hasText(itemName), EamInspectionItem::getItemName, itemName)
                .eq(StringUtils.hasText(itemType), EamInspectionItem::getItemType, itemType)
                .orderByDesc(EamInspectionItem::getCreateTime);
        Page<EamInspectionItem> page = itemMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public List<EamInspectionItem> itemList() {
        return itemMapper.selectList(new LambdaQueryWrapper<EamInspectionItem>()
                .eq(EamInspectionItem::getStatus, "active"));
    }

    public void createItem(EamInspectionItem item) {
        if (item.getStatus() == null) item.setStatus("active");
        itemMapper.insert(item);
    }

    public void updateItem(Long id, EamInspectionItem item) {
        item.setId(id);
        itemMapper.updateById(item);
    }

    public void deleteItem(Long id) { itemMapper.deleteById(id); }

    // ========== 点检模板 ==========

    public PageResult<EamInspectionTemplate> templatePage(String templateName, Integer inspectionType, int pageNum, int pageSize) {
        LambdaQueryWrapper<EamInspectionTemplate> wrapper = new LambdaQueryWrapper<EamInspectionTemplate>()
                .like(StringUtils.hasText(templateName), EamInspectionTemplate::getTemplateName, templateName)
                .eq(inspectionType != null, EamInspectionTemplate::getInspectionType, inspectionType)
                .orderByDesc(EamInspectionTemplate::getCreateTime);
        Page<EamInspectionTemplate> page = templateMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public Map<String, Object> templateDetail(Long id) {
        EamInspectionTemplate tpl = templateMapper.selectById(id);
        if (tpl == null) throw new BusinessException("点检模板不存在");
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("template", tpl);
        detail.put("items", templateItemMapper.selectList(new LambdaQueryWrapper<EamInspectionTemplateItem>()
                .eq(EamInspectionTemplateItem::getTemplateId, id)
                .orderByAsc(EamInspectionTemplateItem::getSortOrder)));
        detail.put("equipmentIds", templateEquipmentMapper.selectList(new LambdaQueryWrapper<EamInspectionTemplateEquipment>()
                .eq(EamInspectionTemplateEquipment::getTemplateId, id))
                .stream().map(EamInspectionTemplateEquipment::getEquipmentId).toList());
        return detail;
    }

    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public void createTemplate(Map<String, Object> body) {
        EamInspectionTemplate tpl = parseTemplate(body);
        long cnt = templateMapper.selectCount(new LambdaQueryWrapper<EamInspectionTemplate>()
                .eq(EamInspectionTemplate::getTemplateName, tpl.getTemplateName()));
        if (cnt > 0) throw new BusinessException("模板名称已存在");
        if (tpl.getStatus() == null) tpl.setStatus("active");
        templateMapper.insert(tpl);
        saveTemplateItems(tpl.getId(), (List<Map<String, Object>>) body.get("items"));
        saveTemplateEquipment(tpl.getId(), (List<Number>) body.get("equipmentIds"));
    }

    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public void updateTemplate(Long id, Map<String, Object> body) {
        EamInspectionTemplate existing = templateMapper.selectById(id);
        if (existing == null) throw new BusinessException("模板不存在");
        EamInspectionTemplate tpl = parseTemplate(body);
        tpl.setId(id);
        templateMapper.updateById(tpl);
        templateItemMapper.delete(new LambdaQueryWrapper<EamInspectionTemplateItem>()
                .eq(EamInspectionTemplateItem::getTemplateId, id));
        templateEquipmentMapper.delete(new LambdaQueryWrapper<EamInspectionTemplateEquipment>()
                .eq(EamInspectionTemplateEquipment::getTemplateId, id));
        saveTemplateItems(id, (List<Map<String, Object>>) body.get("items"));
        saveTemplateEquipment(id, (List<Number>) body.get("equipmentIds"));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteTemplate(Long id) {
        templateItemMapper.delete(new LambdaQueryWrapper<EamInspectionTemplateItem>()
                .eq(EamInspectionTemplateItem::getTemplateId, id));
        templateEquipmentMapper.delete(new LambdaQueryWrapper<EamInspectionTemplateEquipment>()
                .eq(EamInspectionTemplateEquipment::getTemplateId, id));
        templateMapper.deleteById(id);
    }

    private EamInspectionTemplate parseTemplate(Map<String, Object> body) {
        EamInspectionTemplate tpl = new EamInspectionTemplate();
        tpl.setTemplateName((String) body.get("templateName"));
        tpl.setEquipmentType((String) body.get("equipmentType"));
        Object it = body.get("inspectionType");
        if (it != null) tpl.setInspectionType(it instanceof Number ? ((Number) it).intValue() : Integer.parseInt(it.toString()));
        tpl.setStatus((String) body.get("status"));
        tpl.setRemark((String) body.get("remark"));
        return tpl;
    }

    private void saveTemplateItems(Long templateId, List<Map<String, Object>> items) {
        if (items == null) return;
        int order = 0;
        for (Map<String, Object> m : items) {
            EamInspectionTemplateItem item = new EamInspectionTemplateItem();
            item.setTemplateId(templateId);
            Object iid = m.get("itemId");
            if (iid instanceof Number) item.setItemId(((Number) iid).longValue());
            item.setItemName((String) m.get("itemName"));
            item.setStandard((String) m.get("standard"));
            item.setMethod((String) m.get("method"));
            Object req = m.get("isRequired");
            item.setIsRequired(req instanceof Number ? ((Number) req).intValue() : 1);
            Object so = m.get("sortOrder");
            item.setSortOrder(so instanceof Number ? ((Number) so).intValue() : order++);
            templateItemMapper.insert(item);
        }
    }

    private void saveTemplateEquipment(Long templateId, List<Number> equipmentIds) {
        if (equipmentIds == null) return;
        for (Number eid : equipmentIds) {
            EamInspectionTemplateEquipment te = new EamInspectionTemplateEquipment();
            te.setTemplateId(templateId);
            te.setEquipmentId(eid.longValue());
            templateEquipmentMapper.insert(te);
        }
    }

    // ========== 点检计划 ==========

    public PageResult<EamInspectionPlan> planPage(String planName, Integer planStatus, int pageNum, int pageSize) {
        LambdaQueryWrapper<EamInspectionPlan> wrapper = new LambdaQueryWrapper<EamInspectionPlan>()
                .like(StringUtils.hasText(planName), EamInspectionPlan::getPlanName, planName)
                .eq(planStatus != null, EamInspectionPlan::getPlanStatus, planStatus)
                .orderByDesc(EamInspectionPlan::getCreateTime);
        Page<EamInspectionPlan> page = planMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public void createPlan(EamInspectionPlan plan) {
        if (!StringUtils.hasText(plan.getPlanName())) throw new BusinessException("计划名称不能为空");
        plan.setPlanCode(CodeUtil.generate("IP"));
        if (plan.getPlanStatus() == null) plan.setPlanStatus(0);
        if (plan.getExecStatus() == null) plan.setExecStatus(0);
        planMapper.insert(plan);
    }

    public void updatePlan(Long id, EamInspectionPlan plan) {
        EamInspectionPlan existing = planMapper.selectById(id);
        if (existing == null) throw new BusinessException("计划不存在");
        if (existing.getPlanStatus() != 0) throw new BusinessException("仅草稿状态的计划可编辑");
        plan.setId(id);
        plan.setPlanCode(existing.getPlanCode());
        planMapper.updateById(plan);
    }

    public void deletePlan(Long id) {
        long cnt = taskMapper.selectCount(new LambdaQueryWrapper<EamInspectionTask>()
                .eq(EamInspectionTask::getPlanId, id));
        if (cnt > 0) throw new BusinessException("该计划已有任务生成，无法删除");
        planMapper.deleteById(id);
    }

    public void publishPlan(Long id) {
        EamInspectionPlan plan = planMapper.selectById(id);
        if (plan == null) throw new BusinessException("计划不存在");
        plan.setPlanStatus(1);
        planMapper.updateById(plan);
    }

    /** 取消: 任意状态 → 已取消，同步取消该计划下所有未终态任务 */
    @Transactional(rollbackFor = Exception.class)
    public void cancelPlan(Long id) {
        EamInspectionPlan plan = planMapper.selectById(id);
        if (plan == null) throw new BusinessException("计划不存在");
        if (plan.getPlanStatus() != null && plan.getPlanStatus() == 3) throw new BusinessException("计划已取消");
        if (plan.getExecStatus() != null && plan.getExecStatus() == 3) {
            throw new BusinessException("该计划任务已全部完成，无法取消");
        }
        plan.setPlanStatus(3);
        plan.setExecStatus(0);
        planMapper.updateById(plan);

        // 同步取消该计划下所有未完成/未取消的任务
        List<EamInspectionTask> activeTasks = taskMapper.selectList(new LambdaQueryWrapper<EamInspectionTask>()
                .eq(EamInspectionTask::getPlanId, id)
                .notIn(EamInspectionTask::getTaskStatus, 2, 3));
        for (EamInspectionTask task : activeTasks) {
            task.setTaskStatus(3);
            taskMapper.updateById(task);
        }
    }

    /** 下达点检任务 */
    @Transactional(rollbackFor = Exception.class)
    public List<String> assignPlanTasks(Long id) {
        EamInspectionPlan plan = planMapper.selectById(id);
        if (plan == null) throw new BusinessException("计划不存在");
        if (plan.getPlanStatus() != 1) throw new BusinessException("仅已发布状态的计划可以下达任务");

        // 仅允许下达一次：若已存在未取消的任务则拒绝；全部任务被取消后才允许重新下达
        long activeTaskCount = taskMapper.selectCount(new LambdaQueryWrapper<EamInspectionTask>()
                .eq(EamInspectionTask::getPlanId, id)
                .ne(EamInspectionTask::getTaskStatus, 3));
        if (activeTaskCount > 0) {
            throw new BusinessException("该计划已下达任务，请先取消所有任务后再重新下达");
        }

        List<Long> equipmentIds = Collections.emptyList();
        List<EamInspectionTemplateItem> items = Collections.emptyList();
        if (plan.getTemplateId() != null) {
            equipmentIds = templateEquipmentMapper.selectList(new LambdaQueryWrapper<EamInspectionTemplateEquipment>()
                    .eq(EamInspectionTemplateEquipment::getTemplateId, plan.getTemplateId()))
                    .stream().map(EamInspectionTemplateEquipment::getEquipmentId).toList();
            items = templateItemMapper.selectList(new LambdaQueryWrapper<EamInspectionTemplateItem>()
                    .eq(EamInspectionTemplateItem::getTemplateId, plan.getTemplateId())
                    .orderByAsc(EamInspectionTemplateItem::getSortOrder));
        }

        List<String> taskCodes = new ArrayList<>();
        if (equipmentIds.isEmpty()) {
            EamInspectionTask task = buildTask(plan, null);
            taskMapper.insert(task);
            taskCodes.add(task.getTaskCode());
            copyDetails(task.getId(), items);
        } else {
            for (Long eid : equipmentIds) {
                EamInspectionTask task = buildTask(plan, eid);
                taskMapper.insert(task);
                taskCodes.add(task.getTaskCode());
                copyDetails(task.getId(), items);
            }
        }
        plan.setExecStatus(1);
        planMapper.updateById(plan);
        return taskCodes;
    }

    private EamInspectionTask buildTask(EamInspectionPlan plan, Long equipmentId) {
        EamInspectionTask task = new EamInspectionTask();
        task.setTaskCode(CodeUtil.generate("IT"));
        task.setPlanId(plan.getId());
        task.setTemplateId(plan.getTemplateId());
        task.setEquipmentId(equipmentId);
        task.setInspectorId(plan.getAssigneeId());
        task.setInspectorName(plan.getAssigneeName());
        // 计划开始/结束时间：优先取自计划，缺省时回退
        LocalDateTime plannedStart = plan.getPlannedStartTime() != null ? plan.getPlannedStartTime() : LocalDateTime.now();
        LocalDateTime plannedEnd = plan.getPlannedEndTime();
        if (plannedEnd == null) {
            plannedEnd = plan.getExecutionCycle() != null ? plannedStart.plusDays(plan.getExecutionCycle()) : null;
        }
        task.setPlannedStartTime(plannedStart);
        task.setPlannedEndTime(plannedEnd);
        // 实际开始/结束时间默认取计划时间
        task.setActualStartTime(plannedStart);
        task.setActualEndTime(plannedEnd);
        task.setTaskStatus(0);
        return task;
    }

    private void copyDetails(Long taskId, List<EamInspectionTemplateItem> items) {
        for (EamInspectionTemplateItem item : items) {
            EamInspectionTaskDetail detail = new EamInspectionTaskDetail();
            detail.setTaskId(taskId);
            detail.setTemplateItemId(item.getId());
            detail.setItemName(item.getItemName());
            detail.setStandard(item.getStandard());
            detail.setMethod(item.getMethod());
            detail.setIsRequired(item.getIsRequired());
            detail.setSortOrder(item.getSortOrder());
            detail.setStatus(0);
            taskDetailMapper.insert(detail);
        }
    }

    // ========== 点检任务 ==========

    public PageResult<Map<String, Object>> taskPage(String taskCode, Long planId, Long equipmentId, Integer taskStatus,
                                                     int pageNum, int pageSize) {
        LambdaQueryWrapper<EamInspectionTask> wrapper = new LambdaQueryWrapper<EamInspectionTask>()
                .like(StringUtils.hasText(taskCode), EamInspectionTask::getTaskCode, taskCode)
                .eq(planId != null, EamInspectionTask::getPlanId, planId)
                .eq(equipmentId != null, EamInspectionTask::getEquipmentId, equipmentId)
                .eq(taskStatus != null, EamInspectionTask::getTaskStatus, taskStatus)
                .orderByDesc(EamInspectionTask::getCreateTime);
        Page<EamInspectionTask> page = taskMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        List<EamInspectionTask> records = page.getRecords();
        Set<Long> planIds = records.stream().map(EamInspectionTask::getPlanId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> equipmentIds = records.stream().map(EamInspectionTask::getEquipmentId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, EamInspectionPlan> planMap = planIds.isEmpty() ? Map.of() :
                planMapper.selectBatchIds(planIds).stream()
                        .collect(Collectors.toMap(EamInspectionPlan::getId, p -> p, (a, b) -> a));
        Map<Long, EamEquipment> equipMap = equipmentIds.isEmpty() ? Map.of() :
                equipmentMapper.selectBatchIds(equipmentIds).stream()
                        .collect(Collectors.toMap(EamEquipment::getId, e -> e, (a, b) -> a));

        List<Map<String, Object>> list = records.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("taskCode", t.getTaskCode());
            m.put("planId", t.getPlanId());
            EamInspectionPlan plan = t.getPlanId() != null ? planMap.get(t.getPlanId()) : null;
            m.put("planCode", plan != null ? plan.getPlanCode() : null);
            m.put("planName", plan != null ? plan.getPlanName() : null);
            m.put("templateId", t.getTemplateId());
            m.put("equipmentId", t.getEquipmentId());
            EamEquipment eq = t.getEquipmentId() != null ? equipMap.get(t.getEquipmentId()) : null;
            m.put("equipmentNo", eq != null ? eq.getEquipmentNo() : null);
            m.put("equipmentName", eq != null ? eq.getEquipmentName() : null);
            m.put("inspectorName", t.getInspectorName());
            m.put("plannedStartTime", t.getPlannedStartTime());
            m.put("plannedEndTime", t.getPlannedEndTime());
            m.put("startTime", t.getStartTime());
            m.put("submitTime", t.getSubmitTime());
            m.put("actualStartTime", t.getActualStartTime());
            m.put("actualEndTime", t.getActualEndTime());
            m.put("taskStatus", t.getTaskStatus());
            m.put("remark", t.getRemark());
            m.put("createTime", t.getCreateTime());
            return m;
        }).collect(Collectors.toList());
        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    public Map<String, Object> taskDetail(Long id) {
        EamInspectionTask task = taskMapper.selectById(id);
        if (task == null) throw new BusinessException("任务不存在");
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("task", task);
        detail.put("details", taskDetailMapper.selectList(new LambdaQueryWrapper<EamInspectionTaskDetail>()
                .eq(EamInspectionTaskDetail::getTaskId, id)
                .orderByAsc(EamInspectionTaskDetail::getSortOrder)));
        return detail;
    }

    public void startTask(Long id) {
        EamInspectionTask task = taskMapper.selectById(id);
        if (task == null) throw new BusinessException("任务不存在");
        if (task.getTaskStatus() != 0 && task.getTaskStatus() != 4) throw new BusinessException("状态不允许开始");
        task.setTaskStatus(1);
        task.setStartTime(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    public void cancelTask(Long id) {
        EamInspectionTask task = taskMapper.selectById(id);
        if (task == null) throw new BusinessException("任务不存在");
        if (task.getTaskStatus() == 2 || task.getTaskStatus() == 3) {
            throw new BusinessException("已完成或已取消的任务不可取消");
        }
        task.setTaskStatus(3);
        taskMapper.updateById(task);
        refreshPlanExecStatus(task.getPlanId());
    }

    /** 任务完成/取消后回写计划 exec_status */
    private void refreshPlanExecStatus(Long planId) {
        if (planId == null) return;
        long total = taskMapper.selectCount(new LambdaQueryWrapper<EamInspectionTask>()
                .eq(EamInspectionTask::getPlanId, planId));
        if (total == 0) return;
        long active = taskMapper.selectCount(new LambdaQueryWrapper<EamInspectionTask>()
                .eq(EamInspectionTask::getPlanId, planId)
                .ne(EamInspectionTask::getTaskStatus, 3));
        long completed = taskMapper.selectCount(new LambdaQueryWrapper<EamInspectionTask>()
                .eq(EamInspectionTask::getPlanId, planId)
                .eq(EamInspectionTask::getTaskStatus, 2));
        EamInspectionPlan plan = planMapper.selectById(planId);
        if (plan == null) return;
        if (active == 0) {
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

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> completeTask(Long id, EamInspectionTask payload) {
        EamInspectionTask task = taskMapper.selectById(id);
        if (task == null) throw new BusinessException("任务不存在");
        if (task.getTaskStatus() != 1) throw new BusinessException("仅执行中状态可完成");
        List<EamInspectionTaskDetail> details = taskDetailMapper.selectList(
                new LambdaQueryWrapper<EamInspectionTaskDetail>().eq(EamInspectionTaskDetail::getTaskId, id));
        // 校验：所有点检项必须已完成（不能存在待检状态）
        for (EamInspectionTaskDetail d : details) {
            if (d.getStatus() == null || d.getStatus() == 0) {
                throw new BusinessException("点检项[" + d.getItemName() + "]仍处于待检状态，无法完成任务");
            }
        }
        task.setTaskStatus(2);
        task.setSubmitTime(LocalDateTime.now());
        if (payload != null) {
            if (payload.getActualStartTime() != null) task.setActualStartTime(payload.getActualStartTime());
            if (payload.getActualEndTime() != null) task.setActualEndTime(payload.getActualEndTime());
        }
        if (task.getActualEndTime() == null) task.setActualEndTime(LocalDateTime.now());
        taskMapper.updateById(task);
        refreshPlanExecStatus(task.getPlanId());

        // 收集异常项，供前端决定是否创建报修
        List<EamInspectionTaskDetail> abnormals = details.stream()
                .filter(d -> d.getStatus() != null && d.getStatus() == 2).toList();
        Map<String, Object> result = new HashMap<>();
        result.put("hasAbnormal", !abnormals.isEmpty());
        result.put("abnormals", abnormals);
        result.put("equipmentId", task.getEquipmentId());
        return result;
    }

    public void updateTaskDetail(Long detailId, EamInspectionTaskDetail payload) {
        EamInspectionTaskDetail existing = taskDetailMapper.selectById(detailId);
        if (existing == null) throw new BusinessException("明细不存在");
        // 任务已完成或已取消时不允许修改明细
        EamInspectionTask task = taskMapper.selectById(existing.getTaskId());
        if (task != null && (task.getTaskStatus() == 2 || task.getTaskStatus() == 3)) {
            throw new BusinessException("任务已" + (task.getTaskStatus() == 2 ? "完成" : "取消") + "，明细不可修改");
        }
        if (payload.getStatus() != null) existing.setStatus(payload.getStatus());
        if (payload.getResultValue() != null) existing.setResultValue(payload.getResultValue());
        if (payload.getPhotoUrl() != null) existing.setPhotoUrl(payload.getPhotoUrl());
        if (payload.getRemark() != null) existing.setRemark(payload.getRemark());
        taskDetailMapper.updateById(existing);
    }
}
