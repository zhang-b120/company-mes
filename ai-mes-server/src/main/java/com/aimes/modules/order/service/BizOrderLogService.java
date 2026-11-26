package com.aimes.modules.order.service;

import com.aimes.common.utils.SecurityUtil;
import com.aimes.modules.order.entity.BizOrderLog;
import com.aimes.modules.order.mapper.BizOrderLogMapper;
import com.aimes.modules.system.entity.SysUser;
import com.aimes.modules.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BizOrderLogService extends ServiceImpl<BizOrderLogMapper, BizOrderLog> {

    private final SysUserMapper userMapper;

    public List<BizOrderLog> listByOrder(Long orderId) {
        return list(new LambdaQueryWrapper<BizOrderLog>()
                .eq(BizOrderLog::getOrderId, orderId)
                .orderByDesc(BizOrderLog::getCreateTime));
    }

    public void addLog(Long orderId, String operation, String operationDesc) {
        BizOrderLog log = new BizOrderLog();
        log.setOrderId(orderId);
        log.setOperation(operation);
        log.setOperationDesc(operationDesc);
        try {
            Long userId = SecurityUtil.getCurrentUserId();
            if (userId != null) {
                SysUser user = userMapper.selectById(userId);
                log.setOperatorName(user != null ? user.getRealName() : "");
            }
        } catch (Exception ignored) {}
        save(log);
    }
}
