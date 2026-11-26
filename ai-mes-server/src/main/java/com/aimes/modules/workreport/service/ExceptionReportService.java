package com.aimes.modules.workreport.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.modules.system.entity.SysUser;
import com.aimes.modules.system.mapper.SysUserMapper;
import com.aimes.modules.workreport.entity.BizExceptionReport;
import com.aimes.modules.workreport.mapper.BizExceptionReportMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExceptionReportService extends ServiceImpl<BizExceptionReportMapper, BizExceptionReport> {

    private final SysUserMapper sysUserMapper;

    /**
     * 创建异常上报
     */
    public void create(BizExceptionReport report) {
        report.setStatus("pending");
        save(report);
    }

    /**
     * 分页查询
     */
    public PageResult<Map<String, Object>> getPage(String keyword, String status, String exceptionType,
                                                   int pageNum, int pageSize) {
        LambdaQueryWrapper<BizExceptionReport> wrapper = new LambdaQueryWrapper<BizExceptionReport>()
                .like(StringUtils.hasText(keyword), BizExceptionReport::getDescription, keyword)
                .eq(StringUtils.hasText(status), BizExceptionReport::getStatus, status)
                .eq(StringUtils.hasText(exceptionType), BizExceptionReport::getExceptionType, exceptionType)
                .orderByDesc(BizExceptionReport::getCreateTime);

        Page<BizExceptionReport> page = page(new Page<>(pageNum, pageSize), wrapper);
        return enrichPage(page, pageNum, pageSize);
    }

    /**
     * 分页查询（支持紧急程度筛选）
     */
    public PageResult<Map<String, Object>> getPageWithUrgency(String keyword, String status, String exceptionType,
                                                              String urgency, int pageNum, int pageSize) {
        LambdaQueryWrapper<BizExceptionReport> wrapper = new LambdaQueryWrapper<BizExceptionReport>()
                .like(StringUtils.hasText(keyword), BizExceptionReport::getDescription, keyword)
                .eq(StringUtils.hasText(status), BizExceptionReport::getStatus, status)
                .eq(StringUtils.hasText(exceptionType), BizExceptionReport::getExceptionType, exceptionType)
                .eq(StringUtils.hasText(urgency), BizExceptionReport::getUrgency, urgency)
                .orderByDesc(BizExceptionReport::getCreateTime);

        Page<BizExceptionReport> page = page(new Page<>(pageNum, pageSize), wrapper);
        return enrichPage(page, pageNum, pageSize);
    }

    /**
     * 将分页结果enriched为Map列表，附带reporterName和handlerName
     */
    private PageResult<Map<String, Object>> enrichPage(Page<BizExceptionReport> page, int pageNum, int pageSize) {
        List<BizExceptionReport> records = page.getRecords();

        // 收集所有用户ID（reporterId + handlerId）
        Set<Long> userIds = new HashSet<>();
        records.forEach(r -> {
            if (r.getReporterId() != null) userIds.add(r.getReporterId());
            if (r.getHandlerId() != null) userIds.add(r.getHandlerId());
        });

        // 批量加载用户名称
        Map<Long, String> userNameMap = userIds.isEmpty() ? Map.of() :
                sysUserMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));

        // 转换为Map列表，附加名称字段
        List<Map<String, Object>> list = records.stream().map(r -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", r.getId());
            map.put("taskId", r.getTaskId());
            map.put("workOrderId", r.getWorkOrderId());
            map.put("reporterId", r.getReporterId());
            map.put("reporterName", r.getReporterId() != null ? userNameMap.get(r.getReporterId()) : null);
            map.put("exceptionType", r.getExceptionType());
            map.put("description", r.getDescription());
            map.put("photoUrls", r.getPhotoUrls());
            map.put("urgency", r.getUrgency());
            map.put("status", r.getStatus());
            map.put("handlerId", r.getHandlerId());
            map.put("handlerName", r.getHandlerId() != null ? userNameMap.get(r.getHandlerId()) : null);
            map.put("handleResult", r.getHandleResult());
            map.put("isEscalated", r.getIsEscalated());
            map.put("escalatedAt", r.getEscalatedAt());
            map.put("createTime", r.getCreateTime());
            map.put("updateTime", r.getUpdateTime());
            return map;
        }).collect(Collectors.toList());

        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    /**
     * 处理异常
     */
    public void handle(Long id, Long handlerId, String handleResult) {
        BizExceptionReport report = getById(id);
        if (report == null) {
            throw new BusinessException("异常记录不存在");
        }
        if (!"pending".equals(report.getStatus())) {
            throw new BusinessException("该异常已处理");
        }
        report.setHandlerId(handlerId);
        report.setHandleResult(handleResult);
        report.setStatus("handled");
        updateById(report);
    }
}
