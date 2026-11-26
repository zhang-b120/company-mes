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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static com.aimes.modules.dataimport.service.simulation.SimTimeHelper.*;

/**
 * 设备点检计划生成器
 * 根据点检模板 + 模板-设备关联,按类型(日常/定期)生成过去 N 个月的点检计划
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InspectionPlanGenerator {

    private final EamEquipmentMapper equipmentMapper;
    private final EamInspectionTemplateMapper inspTemplateMapper;
    private final EamInspectionTemplateEquipmentMapper inspTemplateEquipmentMapper;
    private final EamInspectionPlanMapper inspPlanMapper;
    private final SysUserMapper userMapper;

    public Map<String, Object> generate(SimConfig cfg) {
        List<EamEquipment> equipments = equipmentMapper.selectList(
                new LambdaQueryWrapper<EamEquipment>().eq(EamEquipment::getDeleted, 0)
                        .ne(EamEquipment::getStatus, "RETIRED"));
        List<EamInspectionTemplate> templates = inspTemplateMapper.selectList(
                new LambdaQueryWrapper<EamInspectionTemplate>().eq(EamInspectionTemplate::getDeleted, 0)
                        .eq(EamInspectionTemplate::getStatus, "active"));

        LocalDate today = cfg.getCutoffDate() != null ? cfg.getCutoffDate() : LocalDate.now();
        int estimatedPlans = 0;
        for (EamInspectionTemplate tpl : templates) {
            int cycleDays = tpl.getInspectionType() != null && tpl.getInspectionType() == 0 ? 1 : 7;
            LocalDate tplStart = tpl.getCreateTime() != null
                    ? tpl.getCreateTime().toLocalDate() : today.minusMonths(cfg.getEamPlanMonths());
            long totalDays = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(tplStart, today));
            estimatedPlans += (int) (totalDays / cycleDays);
        }
        return Map.of("equipment", equipments.size(), "templates", templates.size(),
                "estimatedPlans", estimatedPlans, "totalRows", estimatedPlans);
    }

    public void exportZip(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        throw new BusinessException("点检计划不需要导出，请直接执行导入");
    }

    @Transactional
    public Map<String, Object> importData() { return importData(new SimConfig()); }

    @Transactional
    public Map<String, Object> importData(SimConfig cfg) {
        int success = 0;
        List<String> errors = new ArrayList<>();

        Map<Long, String> userNames = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getDeleted, 0))
                .stream().collect(Collectors.toMap(SysUser::getId,
                        u -> u.getRealName() != null ? u.getRealName() : u.getUsername(), (a, b) -> a));

        List<EamEquipment> equipments = equipmentMapper.selectList(
                new LambdaQueryWrapper<EamEquipment>().eq(EamEquipment::getDeleted, 0)
                        .ne(EamEquipment::getStatus, "RETIRED"));

        List<EamInspectionTemplate> templates = inspTemplateMapper.selectList(
                new LambdaQueryWrapper<EamInspectionTemplate>().eq(EamInspectionTemplate::getDeleted, 0)
                        .eq(EamInspectionTemplate::getStatus, "active"));

        List<EamInspectionTemplateEquipment> rels = inspTemplateEquipmentMapper.selectList(
                new LambdaQueryWrapper<EamInspectionTemplateEquipment>());

        LocalDate today = cfg.getCutoffDate() != null ? cfg.getCutoffDate() : LocalDate.now();
        LocalDate fallbackStart = today.minusMonths(cfg.getEamPlanMonths());

        for (EamInspectionTemplate tpl : templates) {
            try {
                // 日常=每天, 定期=每 executionCycle 天, 默认7天
                int cycleDays = 1;
                if (tpl.getInspectionType() != null && tpl.getInspectionType() != 0) {
                    cycleDays = 7; // 定期默认周检
                }

                Long assigneeId = cfg.pickAssignee();
                if (assigneeId == null) assigneeId = cfg.pickInspector();
                if (assigneeId == null) assigneeId = 1L;
                String assigneeName = userNames.getOrDefault(assigneeId, "系统");

                // 从模板创建时间开始生成计划
                LocalDate startDate = tpl.getCreateTime() != null
                        ? tpl.getCreateTime().toLocalDate() : fallbackStart;
                if (startDate.isAfter(today)) continue;

                LocalDate planDate = startDate;
                while (!planDate.isAfter(today)) {
                    planDate = snapToWorkingDay(planDate, cfg);
                    if (cfg.isAfterCutoff(planDate)) break;

                    // 检查重复
                    long existing = inspPlanMapper.selectCount(new LambdaQueryWrapper<EamInspectionPlan>()
                            .eq(EamInspectionPlan::getTemplateId, tpl.getId())
                            .ge(EamInspectionPlan::getPlannedStartTime, planDate.atStartOfDay())
                            .lt(EamInspectionPlan::getPlannedStartTime, planDate.plusDays(1).atStartOfDay())
                            .eq(EamInspectionPlan::getDeleted, 0));
                    if (existing > 0) { planDate = addWorkingDays(planDate, cycleDays, cfg); continue; }

                    LocalDateTime plannedStart = planDate.atTime(cfg.getStartHour(), cfg.getStartMin());
                    int duration = tpl.getInspectionType() != null && tpl.getInspectionType() == 0 ? 30 : 60;
                    LocalDateTime plannedEnd = plannedStart.plusMinutes(duration);

                    boolean isPast = planDate.isBefore(today);
                    int execStatus = isPast ? 2 : (planDate.isEqual(today) ? 1 : 0);

                    EamInspectionPlan plan = new EamInspectionPlan();
                    plan.setPlanCode(CodeUtil.generate("IP"));
                    plan.setPlanName(tpl.getTemplateName() + " " + planDate);
                    plan.setTemplateId(tpl.getId());
                    plan.setInspectionType(tpl.getInspectionType() != null ? tpl.getInspectionType() : 0);
                    plan.setExecutionCycle(cycleDays);
                    plan.setPlannedStartTime(plannedStart);
                    plan.setPlannedEndTime(plannedEnd);
                    plan.setEstimatedDuration(duration);
                    plan.setAssigneeId(assigneeId);
                    plan.setAssigneeName(assigneeName);
                    plan.setPlanStatus(1);
                    plan.setExecStatus(execStatus);
                    // 下次执行
                    LocalDate nextDate = addWorkingDays(planDate, cycleDays, cfg);
                    plan.setNextExecutionTime(nextDate.atTime(cfg.getStartHour(), cfg.getStartMin()));
                    plan.setCreateTime(plannedStart.minusHours(ThreadLocalRandom.current().nextInt(1, 24)));
                    plan.setUpdateTime(plan.getCreateTime());
                    inspPlanMapper.insert(plan);
                    success++;

                    planDate = addWorkingDays(planDate, cycleDays, cfg);
                }
            } catch (Exception e) {
                errors.add("模板[" + tpl.getTemplateName() + "]: " + e.getMessage());
            }
        }
        log.info("点检计划生成完成: {} 条", success);
        return Map.of("success", success, "errors", errors);
    }
}
