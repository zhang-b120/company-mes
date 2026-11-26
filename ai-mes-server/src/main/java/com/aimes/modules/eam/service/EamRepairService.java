package com.aimes.modules.eam.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.CodeUtil;
import com.aimes.modules.eam.entity.EamRepairManagement;
import com.aimes.modules.eam.entity.EamRepairRequest;
import com.aimes.modules.eam.mapper.EamRepairManagementMapper;
import com.aimes.modules.eam.mapper.EamRepairRequestMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EamRepairService {

    private final EamRepairRequestMapper requestMapper;
    private final EamRepairManagementMapper managementMapper;

    // ========== 报修申请 F9 ==========

    public PageResult<EamRepairRequest> requestPage(String requestNo, String status, Long equipmentId,
                                                      int pageNum, int pageSize) {
        LambdaQueryWrapper<EamRepairRequest> wrapper = new LambdaQueryWrapper<EamRepairRequest>()
                .like(StringUtils.hasText(requestNo), EamRepairRequest::getRequestNo, requestNo)
                .eq(StringUtils.hasText(status), EamRepairRequest::getStatus, status)
                .eq(equipmentId != null, EamRepairRequest::getEquipmentId, equipmentId)
                .orderByDesc(EamRepairRequest::getCreateTime);
        Page<EamRepairRequest> page = requestMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public EamRepairRequest requestDetail(Long id) {
        EamRepairRequest r = requestMapper.selectById(id);
        if (r == null) throw new BusinessException("报修申请不存在");
        return r;
    }

    public void createRequest(EamRepairRequest request) {
        if (request.getEquipmentId() == null) throw new BusinessException("请选择设备");
        if (!StringUtils.hasText(request.getRepairType())) throw new BusinessException("报修类型不能为空");
        if (!StringUtils.hasText(request.getFaultDescription())) throw new BusinessException("故障描述不能为空");
        request.setRequestNo(CodeUtil.generate("RR"));
        if (request.getReportTime() == null) request.setReportTime(LocalDateTime.now());
        request.setStatus("PENDING");
        requestMapper.insert(request);
    }

    public void acceptRequest(Long id, Long acceptorId, String acceptorName) {
        EamRepairRequest r = requestDetail(id);
        if (!"PENDING".equals(r.getStatus())) throw new BusinessException("仅待处理状态可受理");
        r.setStatus("ACCEPTED");
        r.setAcceptorId(acceptorId);
        r.setAcceptorName(acceptorName);
        r.setAcceptTime(LocalDateTime.now());
        requestMapper.updateById(r);
    }

    /** 提交: 自动创建工单 */
    @Transactional(rollbackFor = Exception.class)
    public Long submitRequest(Long id) {
        EamRepairRequest r = requestDetail(id);
        if (!Arrays.asList("PENDING", "ACCEPTED").contains(r.getStatus())) {
            throw new BusinessException("当前状态不可提交");
        }
        r.setStatus("IN_PROGRESS");
        requestMapper.updateById(r);

        EamRepairManagement order = new EamRepairManagement();
        order.setWorkOrderNo(CodeUtil.generate("WO"));
        order.setRepairRequestId(r.getId());
        order.setEquipmentId(r.getEquipmentId());
        order.setEquipmentNo(r.getEquipmentNo());
        order.setEquipmentName(r.getEquipmentName());
        order.setRepairType(r.getRepairType());
        order.setPriority(r.getPriority());
        order.setFaultDescription(r.getFaultDescription());
        // 计划时间作为工单的默认实际开始/结束时间，完成时可修改
        order.setPlannedStartTime(r.getPlannedStartTime());
        order.setPlannedEndTime(r.getPlannedEndTime());
        order.setActualStartTime(r.getPlannedStartTime());
        order.setActualEndTime(r.getPlannedEndTime());
        order.setStatus("PENDING");
        managementMapper.insert(order);
        return order.getId();
    }

    public void cancelRequest(Long id) {
        EamRepairRequest r = requestDetail(id);
        if ("COMPLETED".equals(r.getStatus()) || "CANCELLED".equals(r.getStatus())) {
            throw new BusinessException("终态申请不可取消");
        }
        r.setStatus("CANCELLED");
        requestMapper.updateById(r);
    }

    public void deleteRequest(Long id) {
        EamRepairRequest r = requestDetail(id);
        if (!Arrays.asList("COMPLETED", "CANCELLED").contains(r.getStatus())) {
            throw new BusinessException("仅终态申请可删除");
        }
        requestMapper.deleteById(id);
    }

    public List<EamRepairManagement> getRelatedOrders(Long requestId) {
        return managementMapper.selectList(new LambdaQueryWrapper<EamRepairManagement>()
                .eq(EamRepairManagement::getRepairRequestId, requestId));
    }

    // ========== 维修工单 F10 ==========

    public PageResult<EamRepairManagement> orderPage(String workOrderNo, String status, Long equipmentId,
                                                      Long assigneeId, String priority,
                                                      int pageNum, int pageSize) {
        LambdaQueryWrapper<EamRepairManagement> wrapper = new LambdaQueryWrapper<EamRepairManagement>()
                .like(StringUtils.hasText(workOrderNo), EamRepairManagement::getWorkOrderNo, workOrderNo)
                .eq(StringUtils.hasText(status), EamRepairManagement::getStatus, status)
                .eq(equipmentId != null, EamRepairManagement::getEquipmentId, equipmentId)
                .eq(assigneeId != null, EamRepairManagement::getAssigneeId, assigneeId)
                .eq(StringUtils.hasText(priority), EamRepairManagement::getPriority, priority)
                .orderByDesc(EamRepairManagement::getCreateTime);
        Page<EamRepairManagement> page = managementMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public EamRepairManagement orderDetail(Long id) {
        EamRepairManagement o = managementMapper.selectById(id);
        if (o == null) throw new BusinessException("工单不存在");
        return o;
    }

    public void createOrder(EamRepairManagement order) {
        if (order.getEquipmentId() == null) throw new BusinessException("请选择设备");
        order.setWorkOrderNo(CodeUtil.generate("WO"));
        if (order.getStatus() == null) order.setStatus("PENDING");
        managementMapper.insert(order);
    }

    public void updateOrder(Long id, EamRepairManagement order) {
        EamRepairManagement existing = orderDetail(id);
        order.setId(id);
        order.setWorkOrderNo(existing.getWorkOrderNo());
        managementMapper.updateById(order);
    }

    public void startOrder(Long id) {
        EamRepairManagement o = orderDetail(id);
        if (!"PENDING".equals(o.getStatus()) && !"PAUSED".equals(o.getStatus())) {
            throw new BusinessException("状态不允许开始");
        }
        o.setStatus("IN_PROGRESS");
        if (o.getActualStartTime() == null) o.setActualStartTime(LocalDateTime.now());
        managementMapper.updateById(o);
    }

    public void pauseOrder(Long id) {
        EamRepairManagement o = orderDetail(id);
        if (!"IN_PROGRESS".equals(o.getStatus())) throw new BusinessException("仅进行中工单可暂停");
        o.setStatus("PAUSED");
        managementMapper.updateById(o);
    }

    public void resumeOrder(Long id) {
        EamRepairManagement o = orderDetail(id);
        if (!"PAUSED".equals(o.getStatus())) throw new BusinessException("仅暂停工单可恢复");
        o.setStatus("IN_PROGRESS");
        managementMapper.updateById(o);
    }

    @Transactional(rollbackFor = Exception.class)
    public void completeOrder(Long id, EamRepairManagement payload) {
        EamRepairManagement o = orderDetail(id);
        if (!"IN_PROGRESS".equals(o.getStatus())) throw new BusinessException("仅进行中工单可完成");
        if (!StringUtils.hasText(payload.getRepairPlan())) throw new BusinessException("维修方案必填");
        if (!StringUtils.hasText(payload.getRepairResult())) throw new BusinessException("维修结果必填");
        if (payload.getActualHours() == null) throw new BusinessException("实际工时必填");
        o.setStatus("COMPLETED");
        // 完成时允许修改实际开始/结束时间（默认值由提交报修申请时复制自申请的计划时间）
        if (payload.getActualStartTime() != null) o.setActualStartTime(payload.getActualStartTime());
        if (payload.getActualEndTime() != null) o.setActualEndTime(payload.getActualEndTime());
        if (o.getActualEndTime() == null) o.setActualEndTime(LocalDateTime.now());
        o.setRepairPlan(payload.getRepairPlan());
        o.setRepairResult(payload.getRepairResult());
        o.setActualHours(payload.getActualHours());
        if (payload.getRepairCost() != null) o.setRepairCost(payload.getRepairCost());
        if (payload.getReplacedParts() != null) o.setReplacedParts(payload.getReplacedParts());
        managementMapper.updateById(o);

        // 回写关联申请
        if (o.getRepairRequestId() != null) {
            EamRepairRequest r = requestMapper.selectById(o.getRepairRequestId());
            if (r != null) {
                r.setStatus("COMPLETED");
                requestMapper.updateById(r);
            }
        }
    }

    public void cancelOrder(Long id) {
        EamRepairManagement o = orderDetail(id);
        if ("COMPLETED".equals(o.getStatus()) || "CANCELLED".equals(o.getStatus())) {
            throw new BusinessException("终态工单不可取消");
        }
        o.setStatus("CANCELLED");
        managementMapper.updateById(o);
    }

    public void batchStart(List<Long> ids) {
        for (Long id : ids) {
            try { startOrder(id); } catch (Exception ignored) {}
        }
    }

    public void batchPause(List<Long> ids) {
        for (Long id : ids) {
            try { pauseOrder(id); } catch (Exception ignored) {}
        }
    }
}
