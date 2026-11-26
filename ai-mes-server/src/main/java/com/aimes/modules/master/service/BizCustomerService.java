package com.aimes.modules.master.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.CodeUtil;
import com.aimes.modules.master.entity.BizCustomer;
import com.aimes.modules.master.mapper.BizCustomerMapper;
import com.aimes.modules.system.mapper.SysUserMapper;
import com.aimes.modules.system.entity.SysUser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BizCustomerService extends ServiceImpl<BizCustomerMapper, BizCustomer> {

    private final ApplicationContext applicationContext;

    public PageResult<Map<String, Object>> getPageAdvanced(String customerName, String status,
            String customerNo, String contactName, String phone, String email,
            String address, String settlementType,
            BigDecimal creditLimitMin, BigDecimal creditLimitMax,
            String createTimeStart, String createTimeEnd,
            String createByName,
            int pageNum, int pageSize) {

        LambdaQueryWrapper<BizCustomer> wrapper = new LambdaQueryWrapper<BizCustomer>()
                .like(StringUtils.hasText(customerName), BizCustomer::getCustomerName, customerName)
                .eq(StringUtils.hasText(status), BizCustomer::getStatus, status)
                .like(StringUtils.hasText(customerNo), BizCustomer::getCustomerNo, customerNo)
                .like(StringUtils.hasText(contactName), BizCustomer::getContactName, contactName)
                .like(StringUtils.hasText(phone), BizCustomer::getPhone, phone)
                .like(StringUtils.hasText(email), BizCustomer::getEmail, email)
                .like(StringUtils.hasText(address), BizCustomer::getAddress, address)
                .eq(StringUtils.hasText(settlementType), BizCustomer::getSettlementType, settlementType)
                .ge(creditLimitMin != null, BizCustomer::getCreditLimit, creditLimitMin)
                .le(creditLimitMax != null, BizCustomer::getCreditLimit, creditLimitMax)
                .orderByDesc(BizCustomer::getCreateTime);

        // 时间范围
        if (StringUtils.hasText(createTimeStart)) {
            wrapper.ge(BizCustomer::getCreateTime, LocalDateTime.parse(createTimeStart + "T00:00:00"));
        }
        if (StringUtils.hasText(createTimeEnd)) {
            wrapper.le(BizCustomer::getCreateTime, LocalDateTime.parse(createTimeEnd + "T23:59:59"));
        }

        // 创建人名称筛选
        Set<Long> createByIds = null;
        if (StringUtils.hasText(createByName)) {
            try {
                SysUserMapper userMapper = applicationContext.getBean(SysUserMapper.class);
                List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .like(SysUser::getRealName, createByName)
                        .select(SysUser::getId));
                createByIds = users.stream().map(SysUser::getId).collect(Collectors.toSet());
                if (createByIds.isEmpty()) {
                    return new PageResult<>(Collections.emptyList(), 0L, pageNum, pageSize);
                }
                wrapper.in(BizCustomer::getCreateBy, createByIds);
            } catch (Exception e) {
                // skip filter
            }
        }

        Page<BizCustomer> page = page(new Page<>(pageNum, pageSize), wrapper);
        List<BizCustomer> records = page.getRecords();

        // 批量查 createByName
        Set<Long> allCreateByIds = records.stream()
                .map(BizCustomer::getCreateBy).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> userNameMap = new HashMap<>();
        if (!allCreateByIds.isEmpty()) {
            try {
                SysUserMapper userMapper = applicationContext.getBean(SysUserMapper.class);
                List<SysUser> users = userMapper.selectBatchIds(allCreateByIds);
                for (SysUser u : users) {
                    userNameMap.put(u.getId(), u.getRealName());
                }
            } catch (Exception e) {
                // skip
            }
        }

        List<Map<String, Object>> resultList = records.stream().map(c -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", c.getId());
            map.put("customerNo", c.getCustomerNo());
            map.put("customerName", c.getCustomerName());
            map.put("contactName", c.getContactName());
            map.put("phone", c.getPhone());
            map.put("email", c.getEmail());
            map.put("address", c.getAddress());
            map.put("settlementType", c.getSettlementType());
            map.put("creditLimit", c.getCreditLimit());
            map.put("taxRate", c.getTaxRate());
            map.put("status", c.getStatus());
            map.put("remark", c.getRemark());
            // 扩展字段(给列表展示扩展列 + 编辑时回填用)
            map.put("extFields", c.getExtFields());
            map.put("createTime", c.getCreateTime());
            map.put("createBy", c.getCreateBy());
            map.put("createByName", userNameMap.getOrDefault(c.getCreateBy(), ""));
            return map;
        }).collect(Collectors.toList());

        return new PageResult<>(resultList, page.getTotal(), pageNum, pageSize);
    }

    /** 兼容旧调用 */
    public PageResult<Map<String, Object>> getPage(String customerName, String status, int pageNum, int pageSize) {
        return getPageAdvanced(customerName, status, null, null, null, null, null, null, null, null, null, null, null, pageNum, pageSize);
    }

    public void create(BizCustomer entity) {
        entity.setCustomerNo(CodeUtil.generate("C"));
        entity.setStatus("active");
        save(entity);
    }

    public void update(Long id, BizCustomer entity) {
        BizCustomer existing = getById(id);
        if (existing == null) {
            throw new BusinessException("客户不存在");
        }
        entity.setId(id);
        entity.setCustomerNo(null);
        updateById(entity);
    }

    public void updateStatus(Long id, String status) {
        update(new LambdaUpdateWrapper<BizCustomer>()
                .eq(BizCustomer::getId, id)
                .set(BizCustomer::getStatus, status));
    }

    public void batchUpdateStatus(List<Long> ids, String status) {
        if (ids == null || ids.isEmpty()) return;
        update(new LambdaUpdateWrapper<BizCustomer>()
                .in(BizCustomer::getId, ids)
                .set(BizCustomer::getStatus, status));
    }

    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        // 检查引用
        for (Long id : ids) {
            BizCustomer c = getById(id);
            if (c != null) {
                checkCustomerReferences(id, c.getCustomerName());
            }
        }
        baseMapper.deleteBatchIds(ids);
    }

    private void checkCustomerReferences(Long customerId, String customerName) {
        try {
            var orderMapper = applicationContext.getBean(com.aimes.modules.order.mapper.BizSalesOrderMapper.class);
            long orderCount = orderMapper.selectCount(new LambdaQueryWrapper<com.aimes.modules.order.entity.BizSalesOrder>()
                    .eq(com.aimes.modules.order.entity.BizSalesOrder::getCustomerId, customerId));
            if (orderCount > 0) throw new BusinessException("客户「" + customerName + "」存在订单记录，无法删除");

            var receivableMapper = applicationContext.getBean(com.aimes.modules.finance.mapper.BizReceivableMapper.class);
            long recCount = receivableMapper.selectCount(new LambdaQueryWrapper<com.aimes.modules.finance.entity.BizReceivable>()
                    .eq(com.aimes.modules.finance.entity.BizReceivable::getCustomerId, customerId));
            if (recCount > 0) throw new BusinessException("客户「" + customerName + "」存在应收记录，无法删除");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // Mapper not available, skip
        }
    }

    public List<Map<String, Object>> getSelectList() {
        List<BizCustomer> list = list(new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getStatus, "active")
                .select(BizCustomer::getId, BizCustomer::getCustomerName,
                        BizCustomer::getSettlementType, BizCustomer::getCreditLimit)
                .orderByAsc(BizCustomer::getCustomerName));

        return list.stream().map(c -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", c.getId());
            map.put("name", c.getCustomerName());
            map.put("customerName", c.getCustomerName());
            map.put("settlementType", c.getSettlementType());
            map.put("creditLimit", c.getCreditLimit());
            return map;
        }).collect(Collectors.toList());
    }
}
