package com.aimes.modules.workreport.service;

import com.aimes.common.result.PageResult;
import com.aimes.modules.workreport.entity.BizWageRecord;
import com.aimes.modules.workreport.mapper.BizWageRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WageRecordService extends ServiceImpl<BizWageRecordMapper, BizWageRecord> {

    /**
     * 分页查询
     */
    public PageResult<BizWageRecord> getPage(Long userId, Long workOrderId, int pageNum, int pageSize) {
        LambdaQueryWrapper<BizWageRecord> wrapper = new LambdaQueryWrapper<BizWageRecord>()
                .eq(userId != null, BizWageRecord::getUserId, userId)
                .eq(workOrderId != null, BizWageRecord::getWorkOrderId, workOrderId)
                .orderByDesc(BizWageRecord::getCreateTime);

        Page<BizWageRecord> page = page(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }
}
