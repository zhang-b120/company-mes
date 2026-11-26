package com.aimes.modules.eam.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.CodeUtil;
import com.aimes.modules.eam.entity.EamEquipment;
import com.aimes.modules.eam.entity.EamEquipmentAlarmLog;
import com.aimes.modules.eam.entity.EamEquipmentStatus;
import com.aimes.modules.eam.entity.EamMaintenanceTask;
import com.aimes.modules.eam.entity.EamRepairManagement;
import com.aimes.modules.eam.mapper.EamEquipmentAlarmLogMapper;
import com.aimes.modules.eam.mapper.EamEquipmentMapper;
import com.aimes.modules.eam.mapper.EamEquipmentStatusMapper;
import com.aimes.modules.eam.mapper.EamMaintenanceTaskMapper;
import com.aimes.modules.eam.mapper.EamRepairManagementMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EamEquipmentService extends ServiceImpl<EamEquipmentMapper, EamEquipment> {

    private final EamEquipmentStatusMapper statusMapper;
    private final EamEquipmentAlarmLogMapper alarmLogMapper;
    private final EamMaintenanceTaskMapper maintenanceTaskMapper;
    private final EamRepairManagementMapper repairMapper;

    /** 简单列表(用于下拉选择)：按设备类型筛选，仅返回正常状态 */
    public List<EamEquipment> listByCategory(String category) {
        return list(new LambdaQueryWrapper<EamEquipment>()
                .eq(StringUtils.hasText(category), EamEquipment::getCategory, category)
                .ne(EamEquipment::getStatus, "RETIRED")
                .orderByAsc(EamEquipment::getEquipmentNo));
    }

    public PageResult<EamEquipment> getPage(String equipmentNo, String equipmentName, String category,
                                             String status, Long deptId, int pageNum, int pageSize) {
        LambdaQueryWrapper<EamEquipment> wrapper = new LambdaQueryWrapper<EamEquipment>()
                .like(StringUtils.hasText(equipmentNo), EamEquipment::getEquipmentNo, equipmentNo)
                .like(StringUtils.hasText(equipmentName), EamEquipment::getEquipmentName, equipmentName)
                .like(StringUtils.hasText(category), EamEquipment::getCategory, category)
                .eq(StringUtils.hasText(status), EamEquipment::getStatus, status)
                .eq(deptId != null, EamEquipment::getDeptId, deptId)
                .orderByDesc(EamEquipment::getCreateTime);
        Page<EamEquipment> page = page(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    @Transactional(rollbackFor = Exception.class)
    public void create(EamEquipment equipment) {
        if (!StringUtils.hasText(equipment.getEquipmentName())) {
            throw new BusinessException("设备名称不能为空");
        }
        if (!StringUtils.hasText(equipment.getCategory())) {
            throw new BusinessException("设备类型不能为空");
        }
        // 设备编号为空时自动生成(EQ+yyyyMMdd+4位序号)
        if (!StringUtils.hasText(equipment.getEquipmentNo())) {
            equipment.setEquipmentNo(CodeUtil.generate("EQ"));
        } else {
            equipment.setEquipmentNo(equipment.getEquipmentNo().trim());
        }
        long cnt = count(new LambdaQueryWrapper<EamEquipment>()
                .eq(EamEquipment::getEquipmentNo, equipment.getEquipmentNo()));
        if (cnt > 0) throw new BusinessException("设备编号[" + equipment.getEquipmentNo() + "]已存在");
        if (equipment.getStatus() == null) equipment.setStatus("NORMAL");
        // 计算下次维护日期
        if (equipment.getMaintenanceCycle() != null && equipment.getEnableDate() != null) {
            equipment.setNextMaintenanceDate(equipment.getEnableDate().plusDays(equipment.getMaintenanceCycle()));
        }
        save(equipment);

        // 同步创建运行状态记录
        EamEquipmentStatus status = new EamEquipmentStatus();
        status.setEquipmentId(equipment.getId());
        status.setRunningStatus("STOPPED");
        status.setAlarmStatus("NORMAL");
        statusMapper.insert(status);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, EamEquipment equipment) {
        EamEquipment existing = getById(id);
        if (existing == null) throw new BusinessException("设备不存在");
        // 编号不允许修改
        equipment.setId(id);
        equipment.setEquipmentNo(existing.getEquipmentNo());
        // 重算下次维护日期
        if (equipment.getMaintenanceCycle() != null) {
            LocalDate base = equipment.getEnableDate() != null ? equipment.getEnableDate() : LocalDate.now();
            equipment.setNextMaintenanceDate(base.plusDays(equipment.getMaintenanceCycle()));
        }
        updateById(equipment);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 前置校验: 存在未完成的保养任务或维修工单时禁止删除
        long pendingTask = maintenanceTaskMapper.selectCount(new LambdaQueryWrapper<EamMaintenanceTask>()
                .eq(EamMaintenanceTask::getEquipmentId, id)
                .in(EamMaintenanceTask::getTaskStatus, 0, 1, 4));
        if (pendingTask > 0) throw new BusinessException("该设备存在未完成的保养任务，无法删除");
        long pendingOrder = repairMapper.selectCount(new LambdaQueryWrapper<EamRepairManagement>()
                .eq(EamRepairManagement::getEquipmentId, id)
                .in(EamRepairManagement::getStatus, "PENDING", "IN_PROGRESS", "PAUSED"));
        if (pendingOrder > 0) throw new BusinessException("该设备存在未完成的维修工单，无法删除");
        removeById(id);
    }

    public void enable(Long id) {
        EamEquipment existing = getById(id);
        if (existing == null) throw new BusinessException("设备不存在");
        existing.setStatus("NORMAL");
        updateById(existing);
    }

    public void disable(Long id, String targetStatus) {
        EamEquipment existing = getById(id);
        if (existing == null) throw new BusinessException("设备不存在");
        if (!"MAINTENANCE".equals(targetStatus) && !"RETIRED".equals(targetStatus)) {
            throw new BusinessException("停用目标状态必须为 MAINTENANCE 或 RETIRED");
        }
        existing.setStatus(targetStatus);
        updateById(existing);
    }

    /** 到期保养提醒: 距下次维护 <= N 天 */
    public List<EamEquipment> getMaintenanceReminder(int days) {
        LocalDate deadline = LocalDate.now().plusDays(days);
        return list(new LambdaQueryWrapper<EamEquipment>()
                .isNotNull(EamEquipment::getNextMaintenanceDate)
                .le(EamEquipment::getNextMaintenanceDate, deadline)
                .ne(EamEquipment::getStatus, "RETIRED"));
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", count());
        stats.put("normal", count(new LambdaQueryWrapper<EamEquipment>().eq(EamEquipment::getStatus, "NORMAL")));
        stats.put("maintenance", count(new LambdaQueryWrapper<EamEquipment>().eq(EamEquipment::getStatus, "MAINTENANCE")));
        stats.put("retired", count(new LambdaQueryWrapper<EamEquipment>().eq(EamEquipment::getStatus, "RETIRED")));
        return stats;
    }

    // ========== 运行状态 F2 ==========

    public EamEquipmentStatus getStatus(Long equipmentId) {
        EamEquipmentStatus status = statusMapper.selectOne(new LambdaQueryWrapper<EamEquipmentStatus>()
                .eq(EamEquipmentStatus::getEquipmentId, equipmentId));
        if (status == null) {
            status = new EamEquipmentStatus();
            status.setEquipmentId(equipmentId);
            status.setRunningStatus("STOPPED");
            status.setAlarmStatus("NORMAL");
            statusMapper.insert(status);
        }
        return status;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long equipmentId, EamEquipmentStatus payload) {
        EamEquipmentStatus existing = getStatus(equipmentId);
        String oldAlarm = existing.getAlarmStatus();
        if (payload.getRunningStatus() != null) existing.setRunningStatus(payload.getRunningStatus());
        if (payload.getLastRunningTime() != null) existing.setLastRunningTime(payload.getLastRunningTime());
        if (payload.getTotalRunningHours() != null) existing.setTotalRunningHours(payload.getTotalRunningHours());
        if (payload.getCurrentTemperature() != null) existing.setCurrentTemperature(payload.getCurrentTemperature());
        if (payload.getCurrentPressure() != null) existing.setCurrentPressure(payload.getCurrentPressure());
        if (payload.getCurrentSpeed() != null) existing.setCurrentSpeed(payload.getCurrentSpeed());
        if (payload.getCurrentLoad() != null) existing.setCurrentLoad(payload.getCurrentLoad());
        if (payload.getAlarmStatus() != null) existing.setAlarmStatus(payload.getAlarmStatus());
        if (payload.getAlarmMessage() != null) existing.setAlarmMessage(payload.getAlarmMessage());
        statusMapper.updateById(existing);

        // 报警状态变更时写入报警历史
        if (payload.getAlarmStatus() != null && !payload.getAlarmStatus().equals(oldAlarm)) {
            EamEquipmentAlarmLog log = new EamEquipmentAlarmLog();
            log.setEquipmentId(equipmentId);
            log.setAlarmStatus(payload.getAlarmStatus());
            log.setAlarmMessage(payload.getAlarmMessage());
            log.setOccurredAt(java.time.LocalDateTime.now());
            alarmLogMapper.insert(log);
        }
    }

    public List<EamEquipmentAlarmLog> getAlarmLogs(Long equipmentId) {
        return alarmLogMapper.selectList(new LambdaQueryWrapper<EamEquipmentAlarmLog>()
                .eq(EamEquipmentAlarmLog::getEquipmentId, equipmentId)
                .orderByDesc(EamEquipmentAlarmLog::getOccurredAt));
    }
}
