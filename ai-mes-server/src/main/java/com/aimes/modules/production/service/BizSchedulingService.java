package com.aimes.modules.production.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.modules.production.entity.BizScheduling;
import com.aimes.modules.production.entity.BizWorkOrder;
import com.aimes.modules.production.mapper.BizSchedulingMapper;
import com.aimes.modules.production.mapper.BizWorkOrderMapper;
import com.aimes.modules.system.entity.SysUser;
import com.aimes.modules.master.entity.BizProduct;
import com.aimes.modules.master.mapper.BizProductMapper;
import com.aimes.modules.system.mapper.SysUserMapper;
import com.aimes.modules.team.entity.BizTeam;
import com.aimes.modules.team.mapper.BizTeamMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BizSchedulingService extends ServiceImpl<BizSchedulingMapper, BizScheduling> {

    private final BizWorkOrderMapper workOrderMapper;
    private final BizProductMapper productMapper;
    private final SysUserMapper userMapper;
    private final BizTeamMapper teamMapper;

    /**
     * 分页查询排产记录（返回前端所需字段名）
     */
    public PageResult<Map<String, Object>> getPage(Long workOrderId, Long workerId, Long teamId,
                                              String status, LocalDate startDate, LocalDate endDate,
                                              int pageNum, int pageSize) {
        LambdaQueryWrapper<BizScheduling> wrapper = new LambdaQueryWrapper<BizScheduling>()
                .eq(workOrderId != null, BizScheduling::getWorkOrderId, workOrderId)
                .eq(workerId != null, BizScheduling::getWorkerId, workerId)
                .eq(teamId != null, BizScheduling::getTeamId, teamId)
                .eq(status != null && !status.isEmpty(), BizScheduling::getStatus, status)
                .ge(startDate != null, BizScheduling::getStartTime, startDate != null ? startDate.atStartOfDay() : null)
                .le(endDate != null, BizScheduling::getEndTime, endDate != null ? endDate.atTime(LocalTime.MAX) : null)
                .orderByDesc(BizScheduling::getStartTime);

        Page<BizScheduling> page = page(new Page<>(pageNum, pageSize), wrapper);
        List<Map<String, Object>> list = enrichSchedulingRecords(page.getRecords());
        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    /**
     * 查询排产记录列表（全部或按条件过滤），返回前端甘特图所需的enriched字段
     */
    public List<Map<String, Object>> getScheduleListEnriched(Long workOrderId, String status,
                                                              LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<BizScheduling> wrapper = new LambdaQueryWrapper<BizScheduling>()
                .eq(workOrderId != null, BizScheduling::getWorkOrderId, workOrderId)
                .eq(status != null && !status.isEmpty(), BizScheduling::getStatus, status)
                .ge(startDate != null, BizScheduling::getStartTime, startDate != null ? startDate.atStartOfDay() : null)
                .le(endDate != null, BizScheduling::getEndTime, endDate != null ? endDate.atTime(LocalTime.MAX) : null)
                .orderByAsc(BizScheduling::getStartTime);
        List<BizScheduling> records = list(wrapper);
        return enrichSchedulingRecords(records);
    }

    /**
     * 查询排产记录列表（按工单，返回原始实体）
     */
    public List<BizScheduling> getScheduleList(Long workOrderId) {
        return list(new LambdaQueryWrapper<BizScheduling>()
                .eq(BizScheduling::getWorkOrderId, workOrderId)
                .orderByAsc(BizScheduling::getStartTime));
    }

    /**
     * 将排产记录列表enriched为前端所需的Map格式（包含workOrderNo, productName, assigneeName, plannedStart, plannedEnd等）
     */
    private List<Map<String, Object>> enrichSchedulingRecords(List<BizScheduling> records) {
        if (records.isEmpty()) return List.of();

        // 批量加载关联名称
        Set<Long> woIds = records.stream().map(BizScheduling::getWorkOrderId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> workerIds = records.stream().map(BizScheduling::getWorkerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> teamIds = records.stream().map(BizScheduling::getTeamId).filter(Objects::nonNull).collect(Collectors.toSet());

        // 工单信息
        Map<Long, BizWorkOrder> woMap = woIds.isEmpty() ? Map.of() :
                workOrderMapper.selectBatchIds(woIds).stream().collect(Collectors.toMap(BizWorkOrder::getId, w -> w, (a, b) -> a));

        // 产品信息（通过工单的productId关联）
        Set<Long> productIds = woMap.values().stream().map(BizWorkOrder::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizProduct> productMap = productIds.isEmpty() ? Map.of() :
                productMapper.selectBatchIds(productIds).stream().collect(Collectors.toMap(BizProduct::getId, p -> p, (a, b) -> a));

        Map<Long, String> workerNameMap = workerIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(workerIds).stream().collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));
        Map<Long, String> teamNameMap = teamIds.isEmpty() ? Map.of() :
                teamMapper.selectBatchIds(teamIds).stream().collect(Collectors.toMap(BizTeam::getId, BizTeam::getTeamName, (a, b) -> a));

        return records.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("taskId", s.getTaskId());
            m.put("workOrderId", s.getWorkOrderId());

            // 工单号和产品名称
            BizWorkOrder wo = s.getWorkOrderId() != null ? woMap.get(s.getWorkOrderId()) : null;
            m.put("workOrderNo", wo != null ? wo.getWoNo() : "");
            String productName = "";
            java.math.BigDecimal planQty = null;
            if (wo != null) {
                planQty = wo.getPlanQty();
                if (wo.getProductId() != null) {
                    BizProduct product = productMap.get(wo.getProductId());
                    if (product != null) {
                        productName = product.getProductName();
                    }
                }
            }
            m.put("productName", productName);
            m.put("planQty", planQty);

            // 负责人（优先用workerName，备选teamName）
            String assigneeName = s.getWorkerId() != null ? workerNameMap.getOrDefault(s.getWorkerId(), "") : "";
            if (assigneeName.isEmpty() && s.getTeamId() != null) {
                assigneeName = teamNameMap.getOrDefault(s.getTeamId(), "");
            }
            m.put("assigneeName", assigneeName);

            // 前端期望的字段名: plannedStart / plannedEnd
            m.put("plannedStart", s.getStartTime());
            m.put("plannedEnd", s.getEndTime());

            // 保留原始字段供兼容
            m.put("startTime", s.getStartTime());
            m.put("endTime", s.getEndTime());

            m.put("durationMinutes", s.getDurationMinutes());
            m.put("status", s.getStatus());
            m.put("remark", "");
            m.put("createTime", s.getCreateTime());
            return m;
        }).collect(Collectors.toList());
    }

    /**
     * 创建排产记录
     */
    @Transactional
    public void schedule(BizScheduling scheduling) {
        if (scheduling.getStatus() == null) {
            scheduling.setStatus("scheduled");
        }
        save(scheduling);
    }

    /**
     * 更新排产记录
     */
    @Transactional
    public void update(Long id, BizScheduling scheduling) {
        BizScheduling existing = getById(id);
        if (existing == null) {
            throw new BusinessException("排产记录不存在");
        }
        scheduling.setId(id);
        updateById(scheduling);
    }

    /**
     * 删除排产记录
     */
    @Transactional
    public void delete(Long id) {
        BizScheduling existing = getById(id);
        if (existing == null) {
            throw new BusinessException("排产记录不存在");
        }
        removeById(id);
    }
}
