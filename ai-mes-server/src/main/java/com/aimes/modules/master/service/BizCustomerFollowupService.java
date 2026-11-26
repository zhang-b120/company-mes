package com.aimes.modules.master.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.utils.SecurityUtil;
import com.aimes.modules.master.entity.BizCustomerFollowup;
import com.aimes.modules.master.mapper.BizCustomerFollowupMapper;
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
public class BizCustomerFollowupService extends ServiceImpl<BizCustomerFollowupMapper, BizCustomerFollowup> {

    private final SysUserMapper userMapper;

    public List<BizCustomerFollowup> listByCustomer(Long customerId) {
        return list(new LambdaQueryWrapper<BizCustomerFollowup>()
                .eq(BizCustomerFollowup::getCustomerId, customerId)
                .orderByDesc(BizCustomerFollowup::getCreateTime));
    }

    public void create(Long customerId, BizCustomerFollowup followup) {
        if (!StringUtils.hasText(followup.getContent())) {
            throw new BusinessException("跟进内容不能为空");
        }
        followup.setCustomerId(customerId);
        // 填充操作人姓名
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId != null) {
            SysUser user = userMapper.selectById(userId);
            followup.setCreatorName(user != null ? user.getRealName() : "");
        }
        save(followup);
    }

    public void delete(Long id) {
        BizCustomerFollowup existing = getById(id);
        if (existing == null) throw new BusinessException("记录不存在");
        removeById(id);
    }
}
