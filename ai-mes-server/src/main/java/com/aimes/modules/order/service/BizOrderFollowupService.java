package com.aimes.modules.order.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.utils.SecurityUtil;
import com.aimes.modules.order.entity.BizOrderFollowup;
import com.aimes.modules.order.mapper.BizOrderFollowupMapper;
import com.aimes.modules.system.entity.SysUser;
import com.aimes.modules.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BizOrderFollowupService extends ServiceImpl<BizOrderFollowupMapper, BizOrderFollowup> {

    private final SysUserMapper userMapper;

    public List<BizOrderFollowup> listByOrder(Long orderId) {
        return list(new LambdaQueryWrapper<BizOrderFollowup>()
                .eq(BizOrderFollowup::getOrderId, orderId)
                .orderByDesc(BizOrderFollowup::getCreateTime));
    }

    public void create(Long orderId, BizOrderFollowup followup) {
        if (!StringUtils.hasText(followup.getContent())) {
            throw new BusinessException("跟进内容不能为空");
        }
        followup.setOrderId(orderId);
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId != null) {
            SysUser user = userMapper.selectById(userId);
            followup.setCreatorName(user != null ? user.getRealName() : "");
        }
        save(followup);
    }

    public void delete(Long id) {
        BizOrderFollowup existing = getById(id);
        if (existing == null) throw new BusinessException("记录不存在");
        removeById(id);
    }
}
