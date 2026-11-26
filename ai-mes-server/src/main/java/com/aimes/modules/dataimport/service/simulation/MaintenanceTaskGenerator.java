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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static com.aimes.modules.dataimport.service.simulation.SimTimeHelper.*;

/**
 * 设备保养任务生成器
 * 基于已生成的保养计划,为每个已完成/执行中的计划生成一条保养任务 + 任务明细
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MaintenanceTaskGenerator {

    private final EamMaintenancePlanMapper maintPlanMapper;
    private final EamMaintenanceTaskMapper maintTaskMapper;
    private final EamMaintenanceTaskDetailMapper maintTaskDetailMapper;
    private final EamMaintenanceTemplateMapper maintTemplateMapper;
    private final EamMaintenanceTemplateItemMapper maintTemplateItemMapper;
    private final SysUserMapper userMapper;

    private static final String[] NORMAL_RESULTS = {"正常", "合格", "良好", "无异常"};
    private static final String[] ABNORMAL_RESULTS = {"磨损超差", "需更换", "轻微泄漏", "松动", "异响", "老化", "锈蚀"};

    public Map<String, Object> generate(SimConfig cfg) {
        long planCount = maintPlanMapper.selectCount(new LambdaQueryWrapper<EamMaintenancePlan>()
                .eq(EamMaintenancePlan::getDeleted, 0).in(EamMaintenancePlan::getExecStatus, List.of(1, 2)));
        return Map.of("plans", planCount, "estimatedTasks", planCount, "totalRows", planCount);
    }

    public void exportZip(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        throw new BusinessException("保养任务不需要导出，请直接执行导入");
    }

    @Transactional
    public Map<String, Object> importData() { return importData(new SimConfig()); }

    @Transactional
    public Map<String, Object> importData(SimConfig cfg) {
        int success = 0;
        List<String> errors = new ArrayList<>();

        Map<Long, String> userNames = loadUserNames();

        List<EamMaintenancePlan> plans = maintPlanMapper.selectList(
                new LambdaQueryWrapper<EamMaintenancePlan>()
                        .eq(EamMaintenancePlan::getDeleted, 0)
                        .eq(EamMaintenancePlan::getPlanStatus, 1)
                        .in(EamMaintenancePlan::getExecStatus, List.of(1, 2))
                        .orderByAsc(EamMaintenancePlan::getPlannedStartTime));

        for (EamMaintenancePlan plan : plans) {
            try {
                // 检查重复
                long existing = maintTaskMapper.selectCount(new LambdaQueryWrapper<EamMaintenanceTask>()
                        .eq(EamMaintenanceTask::getPlanId, plan.getId())
                        .eq(EamMaintenanceTask::getDeleted, 0));
                if (existing > 0) continue;

                Long executorId = cfg.pickAssignee();
                if (executorId == null) executorId = cfg.pickCreator();
                if (executorId == null) executorId = 1L;
                String executorName = userNames.getOrDefault(executorId, "系统");

                boolean isCompleted = plan.getExecStatus() == 2;
                BigDecimal estHours = plan.getEstimatedHours() != null ? plan.getEstimatedHours() : new BigDecimal("4");

                LocalDateTime plannedStart = plan.getPlannedStartTime();
                LocalDateTime plannedEnd = plan.getPlannedEndTime();
                if (plannedStart == null) continue;

                EamMaintenanceTask task = new EamMaintenanceTask();
                task.setTaskCode(CodeUtil.generate("MT"));
                task.setPlanId(plan.getId());
                task.setEquipmentId(plan.getEquipmentId());
                task.setMaintenanceContent(plan.getMaintenanceContent());
                task.setMaintenanceStandard(plan.getMaintenanceStandard());
                task.setExecutorId(executorId);
                task.setExecutorName(executorName);
                task.setPlannedStartTime(plannedStart);
                task.setPlannedEndTime(plannedEnd);

                if (isCompleted) {
                    task.setTaskStatus(2);
                    // 实际时间 = 计划时间 + 小偏移
                    int offsetMin = ThreadLocalRandom.current().nextInt(0, 31);
                    task.setActualStartTime(plannedStart.plusMinutes(offsetMin));
                    double variance = 0.8 + ThreadLocalRandom.current().nextDouble() * 0.4; // 0.8~1.2
                    int actualMinutes = (int) (estHours.doubleValue() * 60 * variance);
                    task.setActualEndTime(task.getActualStartTime().plusMinutes(actualMinutes));
                    // 钳到工作时段
                    if (task.getActualEndTime().getHour() >= cfg.getEndHour()) {
                        task.setActualEndTime(task.getActualEndTime().toLocalDate()
                                .atTime(cfg.getEndHour(), cfg.getEndMin()).minusMinutes(ThreadLocalRandom.current().nextInt(1, 30)));
                    }
                    task.setActualHours(new BigDecimal(actualMinutes / 60.0).setScale(1, RoundingMode.HALF_UP));
                    task.setMaintenanceResult("保养完成，设备运行正常");
                } else {
                    task.setTaskStatus(1);
                    task.setActualStartTime(plannedStart.plusMinutes(ThreadLocalRandom.current().nextInt(0, 15)));
                }

                task.setCreateTime(plannedStart);
                task.setUpdateTime(task.getActualEndTime() != null ? task.getActualEndTime() : plannedStart);
                maintTaskMapper.insert(task);

                // 生成任务明细(从模板项复制)
                if (plan.getTemplateId() != null) {
                    List<EamMaintenanceTemplateItem> tplItems = maintTemplateItemMapper.selectList(
                            new LambdaQueryWrapper<EamMaintenanceTemplateItem>()
                                    .eq(EamMaintenanceTemplateItem::getTemplateId, plan.getTemplateId())
                                    .orderByAsc(EamMaintenanceTemplateItem::getSortNo));

                    int abnormalCount = 0;
                    int maxAbnormal = Math.max(1, tplItems.size() * 5 / 100); // ~5% 异常

                    for (EamMaintenanceTemplateItem ti : tplItems) {
                        EamMaintenanceTaskDetail detail = new EamMaintenanceTaskDetail();
                        detail.setTaskId(task.getId());
                        detail.setEquipmentId(plan.getEquipmentId());
                        detail.setTemplateItemId(ti.getId());
                        detail.setItemGroup(ti.getItemGroup());
                        detail.setItemName(ti.getItemName());
                        detail.setMethod(ti.getMethod());
                        detail.setStandard(ti.getStandard());
                        detail.setUom(ti.getUom());
                        detail.setRequired(ti.getRequired());
                        detail.setNeedPhoto(ti.getNeedPhoto());
                        detail.setSortNo(ti.getSortNo());

                        if (isCompleted) {
                            boolean abnormal = abnormalCount < maxAbnormal
                                    && ThreadLocalRandom.current().nextInt(100) < 5;
                            if (abnormal) {
                                detail.setStatus(2);
                                detail.setResultValue(ABNORMAL_RESULTS[ThreadLocalRandom.current().nextInt(ABNORMAL_RESULTS.length)]);
                                abnormalCount++;
                            } else {
                                detail.setStatus(1);
                                detail.setResultValue(NORMAL_RESULTS[ThreadLocalRandom.current().nextInt(NORMAL_RESULTS.length)]);
                            }
                        } else {
                            detail.setStatus(0);
                        }
                        maintTaskDetailMapper.insert(detail);
                    }

                    // 回写任务的 issuesFound
                    if (isCompleted && abnormalCount > 0) {
                        task.setMaintenanceResult("保养完成，发现 " + abnormalCount + " 项异常");
                        task.setIssuesFound("共 " + abnormalCount + " 项异常，已记录待处理");
                        maintTaskMapper.updateById(task);
                    }
                }
                success++;
            } catch (Exception e) {
                errors.add("计划[" + plan.getPlanCode() + "]: " + e.getMessage());
            }
        }
        log.info("保养任务生成完成: {} 条", success);
        return Map.of("success", success, "errors", errors);
    }

    private Map<Long, String> loadUserNames() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>().eq(SysUser::getDeleted, 0))
                .stream().collect(Collectors.toMap(SysUser::getId,
                        u -> u.getRealName() != null ? u.getRealName() : u.getUsername(), (a, b) -> a));
    }
}
