package com.aimes.modules.dataimport.service.simulation;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.utils.CodeUtil;
import com.aimes.modules.eam.entity.EamRepairManagement;
import com.aimes.modules.eam.entity.EamRepairRequest;
import com.aimes.modules.eam.mapper.EamRepairManagementMapper;
import com.aimes.modules.eam.mapper.EamRepairRequestMapper;
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
 * 维修工单生成器
 * 基于已生成的报修申请(ACCEPTED/IN_PROGRESS/COMPLETED),为每条申请生成一条维修工单
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RepairOrderGenerator {

    private final EamRepairRequestMapper repairRequestMapper;
    private final EamRepairManagementMapper repairOrderMapper;
    private final SysUserMapper userMapper;

    private static final String[] REPAIR_PLANS = {
            "1.停机断电 2.拆卸故障部件 3.检查磨损情况 4.更换密封件 5.重新装配 6.试运行验证",
            "1.排查电气线路 2.检测信号通路 3.更换故障模块 4.参数校准 5.系统复位测试",
            "1.清洗冷却管路 2.更换冷却液 3.检查水泵 4.试运行观察温度",
            "1.拆卸轴承座 2.清洗轴承位 3.更换新轴承 4.涂抹润滑脂 5.装配调试",
            "1.检查液压管路 2.更换破损软管 3.排空气 4.调整系统压力 5.检漏测试",
            "1.检查传动系统 2.调整皮带张力 3.更换磨损件 4.润滑保养 5.试运行",
            "1.排查PLC程序 2.检测IO模块 3.更换通讯卡 4.重新下载程序 5.联调测试",
            "1.校准传感器 2.更换探头 3.调整安装位置 4.标定零点 5.运行验证"
    };

    private static final String[] REPAIR_RESULTS = {
            "故障已排除，设备恢复正常运行",
            "已更换损坏部件，设备运行正常",
            "维修完成，设备各项指标正常",
            "故障排除，建议加强日常巡检",
            "已完成维修和调试，设备达标"
    };

    private static final String[][] REPLACED_PARTS = {
            {"O型圈×4", "Y型密封圈×2", "防尘圈×2"},
            {"SKF6205深沟球轴承×1", "轴承端盖×1"},
            {"接触器CJX2-1210×1", "热继电器JRS1-09×1"},
            {"液压软管DN20×2m", "快换接头×2", "卡箍×4"},
            {"润滑脂2kg", "液压油L-HM46 20L"},
            {"V带B1800×2", "张紧轮×1"},
            {"PLC通讯模块×1", "端子排×1组"},
            {"温度传感器PT100×1", "信号线缆5m"}
    };

    public Map<String, Object> generate(SimConfig cfg) {
        long reqCount = repairRequestMapper.selectCount(new LambdaQueryWrapper<EamRepairRequest>()
                .eq(EamRepairRequest::getDeleted, 0)
                .in(EamRepairRequest::getStatus, List.of("ACCEPTED", "IN_PROGRESS", "COMPLETED")));
        return Map.of("requests", reqCount, "estimatedOrders", reqCount, "totalRows", reqCount);
    }

    public void exportZip(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        throw new BusinessException("维修工单不需要导出，请直接执行导入");
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

        List<EamRepairRequest> requests = repairRequestMapper.selectList(
                new LambdaQueryWrapper<EamRepairRequest>()
                        .eq(EamRepairRequest::getDeleted, 0)
                        .in(EamRepairRequest::getStatus, List.of("ACCEPTED", "IN_PROGRESS", "COMPLETED"))
                        .orderByAsc(EamRepairRequest::getReportTime));

        for (EamRepairRequest req : requests) {
            try {
                // 检查重复
                long existing = repairOrderMapper.selectCount(new LambdaQueryWrapper<EamRepairManagement>()
                        .eq(EamRepairManagement::getRepairRequestId, req.getId())
                        .eq(EamRepairManagement::getDeleted, 0));
                if (existing > 0) continue;

                Long assigneeId = cfg.pickAssignee();
                if (assigneeId == null) assigneeId = cfg.pickCreator();
                if (assigneeId == null) assigneeId = 1L;
                String assigneeName = userNames.getOrDefault(assigneeId, "系统");

                // 工单状态跟随申请: ACCEPTED→PENDING, IN_PROGRESS→IN_PROGRESS, COMPLETED→COMPLETED
                String status = switch (req.getStatus()) {
                    case "ACCEPTED" -> "PENDING";
                    case "IN_PROGRESS" -> "IN_PROGRESS";
                    case "COMPLETED" -> "COMPLETED";
                    default -> "PENDING";
                };

                int idx = ThreadLocalRandom.current().nextInt(REPAIR_PLANS.length);

                EamRepairManagement order = new EamRepairManagement();
                order.setWorkOrderNo(CodeUtil.generate("RO"));
                order.setRepairRequestId(req.getId());
                order.setEquipmentId(req.getEquipmentId());
                order.setEquipmentNo(req.getEquipmentNo());
                order.setEquipmentName(req.getEquipmentName());
                order.setRepairType(req.getRepairType());
                order.setPriority(req.getPriority());
                order.setAssigneeId(assigneeId);
                order.setAssigneeName(assigneeName);
                order.setFaultDescription(req.getFaultDescription());
                order.setRepairPlan(REPAIR_PLANS[idx]);
                order.setPlannedStartTime(req.getPlannedStartTime());
                order.setPlannedEndTime(req.getPlannedEndTime());
                order.setStatus(status);

                if ("COMPLETED".equals(status)) {
                    // 实际开始 = 计划开始 + 小偏移
                    LocalDateTime actualStart = req.getPlannedStartTime() != null
                            ? req.getPlannedStartTime().plusMinutes(ThreadLocalRandom.current().nextInt(0, 61))
                            : (req.getAcceptTime() != null ? req.getAcceptTime().plusHours(1) : req.getReportTime().plusHours(2));
                    // 钳到工作时段
                    if (actualStart.getHour() >= cfg.getEndHour()) {
                        actualStart = addWorkingDays(actualStart.toLocalDate(), 1, cfg)
                                .atTime(cfg.getStartHour(), cfg.getStartMin())
                                .plusMinutes(ThreadLocalRandom.current().nextInt(0, 60));
                    }
                    order.setActualStartTime(actualStart);

                    // 维修时长 1~16 小时
                    int repairMinutes = ThreadLocalRandom.current().nextInt(60, 961);
                    LocalDateTime actualEnd = actualStart.plusMinutes(repairMinutes);
                    // 如果跨天,推到下一个工作日
                    if (actualEnd.getHour() >= cfg.getEndHour()) {
                        actualEnd = addWorkingDays(actualEnd.toLocalDate(), 1, cfg)
                                .atTime(cfg.getStartHour(), cfg.getStartMin())
                                .plusMinutes(ThreadLocalRandom.current().nextInt(60, 240));
                    }
                    if (actualEnd.isAfter(LocalDateTime.now())) {
                        actualEnd = LocalDateTime.now().minusMinutes(ThreadLocalRandom.current().nextInt(10, 60));
                        if (actualEnd.isBefore(actualStart)) actualEnd = actualStart.plusHours(2);
                    }
                    order.setActualEndTime(actualEnd);

                    BigDecimal actualHours = new BigDecimal(repairMinutes / 60.0).setScale(1, RoundingMode.HALF_UP);
                    order.setActualHours(actualHours);
                    order.setEstimatedHours(actualHours.multiply(new BigDecimal("1.1")).setScale(1, RoundingMode.HALF_UP));
                    order.setRepairResult(REPAIR_RESULTS[ThreadLocalRandom.current().nextInt(REPAIR_RESULTS.length)]);

                    // 备件
                    String[] parts = REPLACED_PARTS[idx];
                    order.setReplacedParts(String.join(", ", parts));

                    // 费用: LOW=200~2000, MEDIUM=1000~5000, HIGH=3000~10000, URGENT=5000~15000
                    int costBase = switch (req.getPriority()) {
                        case "LOW" -> ThreadLocalRandom.current().nextInt(200, 2001);
                        case "MEDIUM" -> ThreadLocalRandom.current().nextInt(1000, 5001);
                        case "HIGH" -> ThreadLocalRandom.current().nextInt(3000, 10001);
                        case "URGENT" -> ThreadLocalRandom.current().nextInt(5000, 15001);
                        default -> ThreadLocalRandom.current().nextInt(500, 5001);
                    };
                    order.setRepairCost(new BigDecimal(costBase));
                } else if ("IN_PROGRESS".equals(status)) {
                    LocalDateTime actualStart = req.getAcceptTime() != null
                            ? req.getAcceptTime().plusHours(ThreadLocalRandom.current().nextInt(1, 4))
                            : req.getReportTime().plusHours(3);
                    if (actualStart.getHour() >= cfg.getEndHour()) {
                        actualStart = addWorkingDays(actualStart.toLocalDate(), 1, cfg)
                                .atTime(cfg.getStartHour(), 0).plusMinutes(ThreadLocalRandom.current().nextInt(0, 60));
                    }
                    order.setActualStartTime(actualStart);
                    order.setEstimatedHours(new BigDecimal(ThreadLocalRandom.current().nextInt(2, 17)));
                }

                order.setCreateTime(req.getAcceptTime() != null ? req.getAcceptTime() : req.getReportTime());
                order.setUpdateTime(order.getActualEndTime() != null ? order.getActualEndTime() : order.getCreateTime());
                repairOrderMapper.insert(order);
                success++;
            } catch (Exception e) {
                errors.add("申请[" + req.getRequestNo() + "]: " + e.getMessage());
            }
        }
        log.info("维修工单生成完成: {} 条", success);
        return Map.of("success", success, "errors", errors);
    }
}
