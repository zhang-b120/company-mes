package com.aimes.modules.master.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_customer")
public class BizCustomer extends BaseEntity {
    private String customerNo;
    private String customerName;
    private String contactName;
    private String phone;
    private String email;
    private String address;
    private String settlementType;
    private BigDecimal creditLimit;
    private BigDecimal taxRate;
    private String status;
    private String remark;
    /** 扩展字段(JSON 字符串,key 来自 biz_ext_field_config.field_key) */
    private String extFields;
}
