package com.aimes.modules.quality.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.modules.quality.entity.BizWasteRecord;
import com.aimes.modules.quality.mapper.BizWasteRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BizWasteRecordService extends ServiceImpl<BizWasteRecordMapper, BizWasteRecord> {

    public PageResult<BizWasteRecord> getPage(String wasteName, String operatorName,
                                               String productName, String workOrderNo,
                                               String defectType,
                                               LocalDate dateStart, LocalDate dateEnd,
                                               int pageNum, int pageSize) {
        LambdaQueryWrapper<BizWasteRecord> wrapper = new LambdaQueryWrapper<BizWasteRecord>()
                .like(StringUtils.hasText(wasteName), BizWasteRecord::getWasteName, wasteName)
                .like(StringUtils.hasText(operatorName), BizWasteRecord::getOperatorName, operatorName)
                .like(StringUtils.hasText(productName), BizWasteRecord::getProductName, productName)
                .like(StringUtils.hasText(workOrderNo), BizWasteRecord::getWorkOrderNo, workOrderNo)
                .eq(StringUtils.hasText(defectType), BizWasteRecord::getDefectType, defectType)
                .ge(dateStart != null, BizWasteRecord::getRecordDate, dateStart)
                .le(dateEnd != null, BizWasteRecord::getRecordDate, dateEnd)
                .orderByDesc(BizWasteRecord::getRecordDate)
                .orderByDesc(BizWasteRecord::getCreateTime);

        Page<BizWasteRecord> page = page(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public void create(BizWasteRecord record) {
        if (!StringUtils.hasText(record.getWasteName())) {
            throw new BusinessException("废料名称不能为空");
        }
        if (record.getQuantity() == null || record.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("废料数量必须大于0");
        }
        if (record.getRecordDate() == null) {
            record.setRecordDate(LocalDate.now());
        }
        save(record);
    }

    public void update(Long id, BizWasteRecord record) {
        BizWasteRecord existing = getById(id);
        if (existing == null) throw new BusinessException("记录不存在");
        record.setId(id);
        updateById(record);
    }

    public void delete(Long id) {
        BizWasteRecord existing = getById(id);
        if (existing == null) throw new BusinessException("记录不存在");
        removeById(id);
    }

    /**
     * 统计分析：按时间和缺陷类型汇总
     */
    public Map<String, Object> getStats(LocalDate dateStart, LocalDate dateEnd) {
        LambdaQueryWrapper<BizWasteRecord> wrapper = new LambdaQueryWrapper<BizWasteRecord>()
                .ge(dateStart != null, BizWasteRecord::getRecordDate, dateStart)
                .le(dateEnd != null, BizWasteRecord::getRecordDate, dateEnd)
                .orderByAsc(BizWasteRecord::getRecordDate);

        List<BizWasteRecord> records = list(wrapper);

        // 按日期汇总
        Map<String, BigDecimal> byDate = new LinkedHashMap<>();
        for (BizWasteRecord r : records) {
            String date = r.getRecordDate() != null ? r.getRecordDate().toString() : "未知";
            byDate.merge(date, r.getQuantity() != null ? r.getQuantity() : BigDecimal.ZERO, BigDecimal::add);
        }

        // 按缺陷类型汇总
        Map<String, BigDecimal> byDefectType = new LinkedHashMap<>();
        for (BizWasteRecord r : records) {
            String type = StringUtils.hasText(r.getDefectType()) ? r.getDefectType() : "未分类";
            byDefectType.merge(type, r.getQuantity() != null ? r.getQuantity() : BigDecimal.ZERO, BigDecimal::add);
        }

        // 按废料名称汇总
        Map<String, BigDecimal> byWasteName = new LinkedHashMap<>();
        for (BizWasteRecord r : records) {
            String name = StringUtils.hasText(r.getWasteName()) ? r.getWasteName() : "未知";
            byWasteName.merge(name, r.getQuantity() != null ? r.getQuantity() : BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal totalQuantity = records.stream()
                .map(r -> r.getQuantity() != null ? r.getQuantity() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCount", records.size());
        result.put("totalQuantity", totalQuantity);
        result.put("byDate", byDate);
        result.put("byDefectType", byDefectType);
        result.put("byWasteName", byWasteName);
        return result;
    }
}
