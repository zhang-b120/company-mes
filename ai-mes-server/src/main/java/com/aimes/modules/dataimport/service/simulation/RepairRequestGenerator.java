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
 * 设备报修申请生成器
 * 来源 A(~30%):从点检任务的异常发现触发报修
 * 来源 B(~70%):直接设备故障报修
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RepairRequestGenerator {

    private final EamEquipmentMapper equipmentMapper;
    private final EamRepairRequestMapper repairRequestMapper;
    private final EamInspectionTaskMapper inspTaskMapper;
    private final EamInspectionTaskDetailMapper inspTaskDetailMapper;
    private final SysUserMapper userMapper;

    private static final String[] FAULT_DESCRIPTIONS = {
            "液压缸密封泄漏，需更换密封件",
            "电机运行异响，疑似轴承磨损",
            "焊接电流波动大，焊接质量不稳定",
            "导轨面磨损严重，定位精度下降",
            "冷却循环系统管路堵塞，温度异常升高",
            "PLC通讯模块故障，设备无法启动",
            "主轴跳动超差，加工精度不达标",
            "气动阀组密封不良，气缸动作迟缓",
            "传感器信号漂移，自动定位不准",
            "减速箱异响，润滑油乳化变质",
            "液压油温过高，系统压力不稳",
            "安全阀失灵，无法正常泄压",
            "电气接触不良，频繁跳闸",
            "皮带打滑，输送效率下降"
    };

    private static final String[] PRIORITIES = {"LOW", "MEDIUM", "MEDIUM", "HIGH", "HIGH", "HIGH", "URGENT"};

    public Map<String, Object> generate(SimConfig cfg) {
        long eqCount = equipmentMapper.selectCount(new LambdaQueryWrapper<EamEquipment>()
                .eq(EamEquipment::getDeleted, 0).ne(EamEquipment::getStatus, "RETIRED"));
        long abnormalDetails = inspTaskDetailMapper.selectCount(new LambdaQueryWrapper<EamInspectionTaskDetail>()
                .eq(EamInspectionTaskDetail::getStatus, 2));
        int fromInspection = (int) Math.min(abnormalDetails, 10);
        int direct = ThreadLocalRandom.current().nextInt(15, 30);
        return Map.of("equipment", eqCount, "abnormalFindings", abnormalDetails,
                "estimatedFromInspection", fromInspection, "estimatedDirect", direct,
                "totalRows", fromInspection + direct);
    }

    public void exportZip(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        throw new BusinessException("报修申请不需要导出，请直接执行导入");
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
        if (equipments.isEmpty()) return Map.of("success", 0, "errors", List.of("无可用设备"));

        Map<Long, EamEquipment> eqMap = equipments.stream()
                .collect(Collectors.toMap(EamEquipment::getId, e -> e, (a, b) -> a));

        LocalDate today = cfg.getCutoffDate() != null ? cfg.getCutoffDate() : LocalDate.now();
        // 从最早设备创建时间开始，无则回退 eamPlanMonths
        LocalDate startDate = equipments.stream()
                .map(e -> e.getCreateTime() != null ? e.getCreateTime().toLocalDate() : today.minusMonths(cfg.getEamPlanMonths()))
                .min(LocalDate::compareTo).orElse(today.minusMonths(cfg.getEamPlanMonths()));
        List<LocalDate> workDays = getWorkingDays(startDate, today, cfg);
        if (workDays.isEmpty()) return Map.of("success", 0, "errors", List.of("无工作日"));

        // ========== 来源 A: 从点检异常发现 ==========
        List<EamInspectionTaskDetail> abnormals = inspTaskDetailMapper.selectList(
                new LambdaQueryWrapper<EamInspectionTaskDetail>().eq(EamInspectionTaskDetail::getStatus, 2));
        // 去重:同一设备只取一次(通过 task → equipment)
        Set<Long> processedEquipments = new HashSet<>();
        int fromInspCount = 0;
        int maxFromInsp = Math.max(5, abnormals.size() * cfg.getEamRepairFromInspectionPct() / 100);

        for (EamInspectionTaskDetail detail : abnormals) {
            if (fromInspCount >= maxFromInsp) break;
            EamInspectionTask task = inspTaskMapper.selectById(detail.getTaskId());
            if (task == null || task.getEquipmentId() == null) continue;
            if (processedEquipments.contains(task.getEquipmentId())) continue;

            EamEquipment eq = eqMap.get(task.getEquipmentId());
            if (eq == null) continue;
            processedEquipments.add(eq.getId());

            // 报修时间 = 点检提交时间后 1~4 小时
            LocalDateTime reportTime = task.getSubmitTime() != null ? task.getSubmitTime() : task.getActualEndTime();
            if (reportTime == null) continue;
            reportTime = reportTime.plusHours(ThreadLocalRandom.current().nextInt(1, 5));
            // 钳到工作时段
            if (reportTime.getHour() >= cfg.getEndHour()) {
                LocalDate nextDay = addWorkingDays(reportTime.toLocalDate(), 1, cfg);
                reportTime = nextDay.atTime(cfg.getStartHour(), cfg.getStartMin())
                        .plusMinutes(ThreadLocalRandom.current().nextInt(0, 60));
            }
            if (reportTime.isAfter(LocalDateTime.now())) continue;

            EamRepairRequest req = buildRequest(eq, "FAULT",
                    "点检发现：" + detail.getItemName() + " " + detail.getResultValue(),
                    reportTime, cfg, userNames);
            repairRequestMapper.insert(req);
            success++;
            fromInspCount++;
        }

        // ========== 来源 B: 直接设备故障 ==========
        int directCount = ThreadLocalRandom.current().nextInt(15, 30);
        for (int i = 0; i < directCount; i++) {
            try {
                EamEquipment eq = equipments.get(ThreadLocalRandom.current().nextInt(equipments.size()));

                LocalDate reportDate = workDays.get(ThreadLocalRandom.current().nextInt(workDays.size()));
                LocalDateTime reportTime = randomTime(reportDate, cfg);
                if (reportTime == null) continue;

                String fault = FAULT_DESCRIPTIONS[ThreadLocalRandom.current().nextInt(FAULT_DESCRIPTIONS.length)];
                String repairType = ThreadLocalRandom.current().nextInt(100) < 80 ? "FAULT" : "PREVENTIVE";

                EamRepairRequest req = buildRequest(eq, repairType, fault, reportTime, cfg, userNames);
                repairRequestMapper.insert(req);
                success++;
            } catch (Exception e) {
                errors.add("直接报修#" + i + ": " + e.getMessage());
            }
        }

        log.info("报修申请生成完成: {} 条(点检{} + 直接{})", success, fromInspCount, success - fromInspCount);
        return Map.of("success", success, "errors", errors);
    }

    private EamRepairRequest buildRequest(EamEquipment eq, String repairType, String faultDesc,
                                          LocalDateTime reportTime, SimConfig cfg, Map<Long, String> userNames) {
        Long reporterId = cfg.pickCreator();
        if (reporterId == null) reporterId = 1L;

        String priority = PRIORITIES[ThreadLocalRandom.current().nextInt(PRIORITIES.length)];

        // 状态分布: COMPLETED 60%, IN_PROGRESS 15%, ACCEPTED 15%, PENDING 10%
        int r = ThreadLocalRandom.current().nextInt(100);
        String status;
        if (r < 60) status = "COMPLETED";
        else if (r < 75) status = "IN_PROGRESS";
        else if (r < 90) status = "ACCEPTED";
        else status = "PENDING";

        EamRepairRequest req = new EamRepairRequest();
        req.setRequestNo(CodeUtil.generate("RR"));
        req.setEquipmentId(eq.getId());
        req.setEquipmentNo(eq.getEquipmentNo());
        req.setEquipmentName(eq.getEquipmentName());
        req.setRepairType(repairType);
        req.setPriority(priority);
        req.setFaultDescription(faultDesc);
        req.setReporterId(reporterId);
        req.setReporterName(userNames.getOrDefault(reporterId, "系统"));
        req.setReportTime(reportTime);
        req.setStatus(status);
        req.setCreateTime(reportTime);
        req.setUpdateTime(reportTime);

        // ACCEPTED+ 状态有受理信息
        if (!"PENDING".equals(status)) {
            Long acceptorId = cfg.pickApprover();
            if (acceptorId == null) acceptorId = cfg.pickCreator();
            if (acceptorId == null) acceptorId = 1L;
            req.setAcceptorId(acceptorId);
            req.setAcceptorName(userNames.getOrDefault(acceptorId, "系统"));
            int acceptDelayMin = ThreadLocalRandom.current().nextInt(30, 121);
            LocalDateTime acceptTime = reportTime.plusMinutes(acceptDelayMin);
            if (acceptTime.getHour() >= cfg.getEndHour()) {
                acceptTime = reportTime.toLocalDate().atTime(cfg.getEndHour(), 0)
                        .minusMinutes(ThreadLocalRandom.current().nextInt(1, 30));
            }
            if (acceptTime.isBefore(reportTime)) acceptTime = reportTime.plusMinutes(10);
            req.setAcceptTime(acceptTime);
            req.setPlannedStartTime(acceptTime.plusHours(ThreadLocalRandom.current().nextInt(1, 5)));
            req.setPlannedEndTime(req.getPlannedStartTime().plusHours(ThreadLocalRandom.current().nextInt(2, 9)));
            req.setUpdateTime(acceptTime);
        }

        return req;
    }
}
