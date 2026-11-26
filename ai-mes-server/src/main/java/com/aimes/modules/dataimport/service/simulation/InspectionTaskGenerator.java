package com.aimes.modules.dataimport.service.simulation;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.utils.CodeUtil;
import com.aimes.modules.eam.entity.*;
import com.aimes.modules.eam.mapper.*;
import com.aimes.modules.system.entity.SysUser;
import com.aimes.modules.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 设备点检任务生成器
 * 基于已生成的点检计划,为每个已完成/执行中的计划+关联设备生成点检任务 + 任务明细
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InspectionTaskGenerator {

    private final EamInspectionPlanMapper inspPlanMapper;
    private final EamInspectionTaskMapper inspTaskMapper;
    private final EamInspectionTaskDetailMapper inspTaskDetailMapper;
    private final EamInspectionTemplateMapper inspTemplateMapper;
    private final EamInspectionTemplateItemMapper inspTemplateItemMapper;
    private final EamInspectionTemplateEquipmentMapper inspTemplateEquipmentMapper;
    private final EamEquipmentMapper equipmentMapper;
    private final SysUserMapper userMapper;

    private static final String[] NORMAL_VALUES = {"正常", "合格", "无异常", "良好"};
    private static final String[] ABNORMAL_VALUES = {"温度偏高", "振动异常", "压力偏低", "松动", "异响", "泄漏", "磨损"};

    public Map<String, Object> generate(SimConfig cfg) {
        long planCount = inspPlanMapper.selectCount(new LambdaQueryWrapper<EamInspectionPlan>()
                .eq(EamInspectionPlan::getDeleted, 0)
                .eq(EamInspectionPlan::getPlanStatus, 1)
                .in(EamInspectionPlan::getExecStatus, List.of(1, 2)));
        return Map.of("plans", planCount, "estimatedTasks", planCount, "totalRows", planCount);
    }

    public void exportZip(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        throw new BusinessException("点检任务不需要导出，请直接执行导入");
    }

    @Transactional
    public Map<String, Object> importData() { return importData(new SimConfig()); }

    @Transactional
    public Map<String, Object> importData(SimConfig cfg) {
        int success = 0;
        List<String> errors = new ArrayList<>();
        int abnormalPct = cfg.getEamInspectionAbnormalPct();

        Map<Long, String> userNames = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getDeleted, 0))
                .stream().collect(Collectors.toMap(SysUser::getId,
                        u -> u.getRealName() != null ? u.getRealName() : u.getUsername(), (a, b) -> a));

        // 加载设备 map
        Map<Long, EamEquipment> eqMap = equipmentMapper.selectList(
                new LambdaQueryWrapper<EamEquipment>().eq(EamEquipment::getDeleted, 0))
                .stream().collect(Collectors.toMap(EamEquipment::getId, e -> e, (a, b) -> a));

        // 加载模板-设备关联
        List<EamInspectionTemplateEquipment> allRels = inspTemplateEquipmentMapper.selectList(
                new LambdaQueryWrapper<EamInspectionTemplateEquipment>());
        Map<Long, List<Long>> templateToEquipments = allRels.stream()
                .collect(Collectors.groupingBy(EamInspectionTemplateEquipment::getTemplateId,
                        Collectors.mapping(EamInspectionTemplateEquipment::getEquipmentId, Collectors.toList())));

        List<EamInspectionPlan> plans = inspPlanMapper.selectList(
                new LambdaQueryWrapper<EamInspectionPlan>()
                        .eq(EamInspectionPlan::getDeleted, 0)
                        .eq(EamInspectionPlan::getPlanStatus, 1)
                        .in(EamInspectionPlan::getExecStatus, List.of(1, 2))
                        .orderByAsc(EamInspectionPlan::getPlannedStartTime));

        for (EamInspectionPlan plan : plans) {
            try {
                Long templateId = plan.getTemplateId();
                if (templateId == null) continue;

                // 关联设备列表:优先用模板-设备关联,没有则用全部设备
                List<Long> eqIds = templateToEquipments.getOrDefault(templateId, List.of());
                if (eqIds.isEmpty()) eqIds = List.of(eqMap.keySet().iterator().next()); // 至少取一台

                // 模板项
                List<EamInspectionTemplateItem> tplItems = inspTemplateItemMapper.selectList(
                        new LambdaQueryWrapper<EamInspectionTemplateItem>()
                                .eq(EamInspectionTemplateItem::getTemplateId, templateId)
                                .orderByAsc(EamInspectionTemplateItem::getSortOrder));

                Long inspectorId = cfg.pickInspector();
                if (inspectorId == null) inspectorId = cfg.pickAssignee();
                if (inspectorId == null) inspectorId = 1L;
                String inspectorName = userNames.getOrDefault(inspectorId, "系统");

                boolean isCompleted = plan.getExecStatus() == 2;

                for (Long eqId : eqIds) {
                    if (!eqMap.containsKey(eqId)) continue;

                    // 检查重复
                    long existing = inspTaskMapper.selectCount(new LambdaQueryWrapper<EamInspectionTask>()
                            .eq(EamInspectionTask::getPlanId, plan.getId())
                            .eq(EamInspectionTask::getEquipmentId, eqId)
                            .eq(EamInspectionTask::getDeleted, 0));
                    if (existing > 0) continue;

                    LocalDateTime plannedStart = plan.getPlannedStartTime();
                    LocalDateTime plannedEnd = plan.getPlannedEndTime();
                    if (plannedStart == null) continue;

                    EamInspectionTask task = new EamInspectionTask();
                    task.setTaskCode(CodeUtil.generate("IT"));
                    task.setPlanId(plan.getId());
                    task.setTemplateId(templateId);
                    task.setEquipmentId(eqId);
                    task.setInspectorId(inspectorId);
                    task.setInspectorName(inspectorName);
                    task.setPlannedStartTime(plannedStart);
                    task.setPlannedEndTime(plannedEnd);

                    if (isCompleted) {
                        task.setTaskStatus(2);
                        int offsetMin = ThreadLocalRandom.current().nextInt(0, 16);
                        task.setActualStartTime(plannedStart.plusMinutes(offsetMin));
                        int durationMin = ThreadLocalRandom.current().nextInt(15, 46);
                        task.setActualEndTime(task.getActualStartTime().plusMinutes(durationMin));
                        // 钳到工作时段
                        if (task.getActualEndTime().getHour() >= cfg.getEndHour()) {
                            task.setActualEndTime(task.getActualEndTime().toLocalDate()
                                    .atTime(cfg.getEndHour(), cfg.getEndMin())
                                    .minusMinutes(ThreadLocalRandom.current().nextInt(1, 10)));
                        }
                        task.setStartTime(task.getActualStartTime());
                        task.setSubmitTime(task.getActualEndTime().plusMinutes(ThreadLocalRandom.current().nextInt(2, 10)));
                        if (task.getSubmitTime().getHour() >= cfg.getEndHour()) {
                            task.setSubmitTime(task.getActualEndTime().plusMinutes(2));
                        }
                    } else {
                        task.setTaskStatus(1);
                        task.setActualStartTime(plannedStart.plusMinutes(ThreadLocalRandom.current().nextInt(0, 10)));
                        task.setStartTime(task.getActualStartTime());
                    }

                    task.setCreateTime(plannedStart);
                    task.setUpdateTime(task.getSubmitTime() != null ? task.getSubmitTime() : plannedStart);
                    inspTaskMapper.insert(task);

                    // 生成任务明细
                    for (EamInspectionTemplateItem ti : tplItems) {
                        EamInspectionTaskDetail detail = new EamInspectionTaskDetail();
                        detail.setTaskId(task.getId());
                        detail.setTemplateItemId(ti.getId());
                        detail.setItemName(ti.getItemName());
                        detail.setStandard(ti.getStandard());
                        detail.setMethod(ti.getMethod());
                        detail.setIsRequired(ti.getIsRequired());
                        detail.setSortOrder(ti.getSortOrder());

                        if (isCompleted) {
                            boolean abnormal = ThreadLocalRandom.current().nextInt(100) < abnormalPct;
                            if (abnormal) {
                                detail.setStatus(2);
                                detail.setResultValue(ABNORMAL_VALUES[ThreadLocalRandom.current().nextInt(ABNORMAL_VALUES.length)]);
                            } else {
                                detail.setStatus(1);
                                detail.setResultValue(NORMAL_VALUES[ThreadLocalRandom.current().nextInt(NORMAL_VALUES.length)]);
                            }
                        } else {
                            detail.setStatus(0);
                        }
                        inspTaskDetailMapper.insert(detail);
                    }
                    success++;
                }
            } catch (Exception e) {
                errors.add("计划[" + plan.getPlanCode() + "]: " + e.getMessage());
            }
        }
        log.info("点检任务生成完成: {} 条", success);
        return Map.of("success", success, "errors", errors);
    }
}
