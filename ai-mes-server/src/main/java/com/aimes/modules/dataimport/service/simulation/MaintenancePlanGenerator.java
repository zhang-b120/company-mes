package com.aimes.modules.dataimport.service.simulation;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.utils.CodeUtil;
import com.aimes.modules.eam.entity.*;
import com.aimes.modules.eam.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static com.aimes.modules.dataimport.service.simulation.SimTimeHelper.*;

/**
 * 设备保养计划生成器
 * 根据设备台账 + 保养模板 + 模板-设备关联,按周期生成过去 N 个月的保养计划
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MaintenancePlanGenerator {

    private final EamEquipmentMapper equipmentMapper;
    private final EamMaintenanceTemplateMapper maintTemplateMapper;
    private final EamMaintenanceTemplateEquipmentMapper maintTemplateEquipmentMapper;
    private final EamMaintenancePlanMapper maintPlanMapper;

    public Map<String, Object> generate(SimConfig cfg) {
        List<EamEquipment> equipments = equipmentMapper.selectList(
                new LambdaQueryWrapper<EamEquipment>().eq(EamEquipment::getDeleted, 0)
                        .ne(EamEquipment::getStatus, "RETIRED"));
        List<EamMaintenanceTemplate> templates = maintTemplateMapper.selectList(
                new LambdaQueryWrapper<EamMaintenanceTemplate>().eq(EamMaintenanceTemplate::getDeleted, 0));
        List<EamMaintenanceTemplateEquipment> rels = maintTemplateEquipmentMapper.selectList(
                new LambdaQueryWrapper<EamMaintenanceTemplateEquipment>());

        LocalDate today = cfg.getCutoffDate() != null ? cfg.getCutoffDate() : LocalDate.now();
        int estimatedPlans = 0;
        for (EamMaintenanceTemplate tpl : templates) {
            List<Long> eqIds = rels.stream().filter(r -> r.getTemplateId().equals(tpl.getId()))
                    .map(EamMaintenanceTemplateEquipment::getEquipmentId).toList();
            if (eqIds.isEmpty()) eqIds = equipments.stream().map(EamEquipment::getId).toList();
            int cycleDays = getCycleDays(tpl);
            if (cycleDays <= 0) cycleDays = 30;
            // 从模板创建时间到今天的天数
            LocalDate tplStart = tpl.getCreateTime() != null
                    ? tpl.getCreateTime().toLocalDate() : today.minusMonths(cfg.getEamPlanMonths());
            long totalDays = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(tplStart, today));
            int planCount = (int) (totalDays / cycleDays);
            estimatedPlans += planCount * eqIds.size();
        }

        return Map.of("equipment", equipments.size(), "templates", templates.size(),
                "estimatedPlans", estimatedPlans, "totalRows", estimatedPlans);
    }

    public void exportZip(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        throw new BusinessException("保养计划不需要导出，请直接执行导入");
    }

    @Transactional
    public Map<String, Object> importData() { return importData(new SimConfig()); }

    @Transactional
    public Map<String, Object> importData(SimConfig cfg) {
        int success = 0;
        List<String> errors = new ArrayList<>();

        List<EamEquipment> equipments = equipmentMapper.selectList(
                new LambdaQueryWrapper<EamEquipment>().eq(EamEquipment::getDeleted, 0)
                        .ne(EamEquipment::getStatus, "RETIRED"));
        Map<Long, EamEquipment> eqMap = equipments.stream()
                .collect(Collectors.toMap(EamEquipment::getId, e -> e, (a, b) -> a));

        List<EamMaintenanceTemplate> templates = maintTemplateMapper.selectList(
                new LambdaQueryWrapper<EamMaintenanceTemplate>().eq(EamMaintenanceTemplate::getDeleted, 0));
        List<EamMaintenanceTemplateEquipment> rels = maintTemplateEquipmentMapper.selectList(
                new LambdaQueryWrapper<EamMaintenanceTemplateEquipment>());

        LocalDate today = cfg.getCutoffDate() != null ? cfg.getCutoffDate() : LocalDate.now();
        LocalDate fallbackStart = today.minusMonths(cfg.getEamPlanMonths());

        for (EamMaintenanceTemplate tpl : templates) {
            try {
                List<Long> eqIds = rels.stream().filter(r -> r.getTemplateId().equals(tpl.getId()))
                        .map(EamMaintenanceTemplateEquipment::getEquipmentId).toList();
                if (eqIds.isEmpty()) eqIds = equipments.stream().map(EamEquipment::getId).toList();

                int cycleDays = getCycleDays(tpl);
                if (cycleDays <= 0) cycleDays = 30;

                // 从模板创建时间开始生成计划
                LocalDate startDate = tpl.getCreateTime() != null
                        ? tpl.getCreateTime().toLocalDate() : fallbackStart;
                if (startDate.isAfter(today)) continue;

                for (Long eqId : eqIds) {
                    EamEquipment eq = eqMap.get(eqId);
                    if (eq == null) continue;

                    LocalDate planDate = startDate;
                    while (!planDate.isAfter(today)) {
                        planDate = snapToWorkingDay(planDate, cfg);
                        if (cfg.isAfterCutoff(planDate)) break;

                        // 检查重复
                        long existing = maintPlanMapper.selectCount(new LambdaQueryWrapper<EamMaintenancePlan>()
                                .eq(EamMaintenancePlan::getTemplateId, tpl.getId())
                                .eq(EamMaintenancePlan::getEquipmentId, eqId)
                                .ge(EamMaintenancePlan::getPlannedStartTime, planDate.atStartOfDay())
                                .lt(EamMaintenancePlan::getPlannedStartTime, planDate.plusDays(1).atStartOfDay())
                                .eq(EamMaintenancePlan::getDeleted, 0));
                        if (existing > 0) { planDate = addWorkingDays(planDate, cycleDays, cfg); continue; }

                        LocalDateTime plannedStart = randomTime(planDate, cfg);
                        if (plannedStart == null) { planDate = addWorkingDays(planDate, cycleDays, cfg); continue; }

                        int estHours = ThreadLocalRandom.current().nextInt(2, 9);
                        LocalDateTime plannedEnd = planDate.atTime(cfg.getEndHour(), cfg.getEndMin());

                        boolean isPast = planDate.isBefore(today);
                        int execStatus = isPast ? 2 : (planDate.isEqual(today) ? 1 : 0);

                        EamMaintenancePlan plan = new EamMaintenancePlan();
                        plan.setPlanCode(CodeUtil.generate("MP"));
                        plan.setPlanName(tpl.getTemplateName() + " - " + eq.getEquipmentName());
                        plan.setMaintenanceType(getMaintenanceType(tpl));
                        plan.setTemplateId(tpl.getId());
                        plan.setEquipmentId(eqId);
                        plan.setExecutionCycle(cycleDays);
                        plan.setPlannedStartTime(plannedStart);
                        plan.setPlannedEndTime(plannedEnd);
                        plan.setMaintenanceContent(tpl.getTemplateName());
                        plan.setEstimatedHours(new BigDecimal(estHours));
                        plan.setPlanStatus(1);
                        plan.setExecStatus(execStatus);
                        plan.setCreateTime(plannedStart.minusDays(1).isBefore(startDate.atStartOfDay())
                                ? startDate.atTime(9, 0) : plannedStart.minusHours(ThreadLocalRandom.current().nextInt(8, 48)));
                        plan.setUpdateTime(plan.getCreateTime());
                        // 下次执行时间
                        LocalDate nextDate = addWorkingDays(planDate, cycleDays, cfg);
                        plan.setNextExecutionTime(nextDate.atTime(cfg.getStartHour(), cfg.getStartMin()));
                        maintPlanMapper.insert(plan);
                        success++;

                        planDate = addWorkingDays(planDate, cycleDays, cfg);
                    }
                }
            } catch (Exception e) {
                errors.add("模板[" + tpl.getTemplateName() + "]: " + e.getMessage());
            }
        }
        log.info("保养计划生成完成: {} 条", success);
        return Map.of("success", success, "errors", errors);
    }

    private int getCycleDays(EamMaintenanceTemplate tpl) {
        String type = tpl.getCycleType();
        int val = tpl.getCycleValue() != null ? tpl.getCycleValue() : 1;
        if (type == null) return 30;
        return switch (type.toUpperCase()) {
            case "DAY" -> val;
            case "WEEK" -> val * 7;
            case "MONTH" -> val * 30;
            case "QUARTER" -> val * 90;
            case "YEAR" -> val * 365;
            case "HOUR" -> Math.max(1, val / 8); // 按工时换算天
            default -> 30;
        };
    }

    private Integer getMaintenanceType(EamMaintenanceTemplate tpl) {
        String type = tpl.getCycleType();
        if (type == null) return 0;
        return switch (type.toUpperCase()) {
            case "DAY" -> 0;   // 日常保养
            case "WEEK" -> 1;  // 周保
            case "MONTH" -> 2; // 月保
            case "QUARTER" -> 3; // 季保
            case "YEAR" -> 4;  // 年保
            default -> 0;
        };
    }
}
