package com.aimes.modules.master.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_supplier")
public class BizSupplier extends BaseEntity {
    private String supplierNo;
    private String supplierName;
    private String supplierType;
    private Long supplierCategoryId;
    private String contactName;
    private String phone;
    private String address;
    private String settlementType;
    private String bankName;
    private String bankAccount;
    private String taxNo;
    private String invoiceType;
    private String processingAbility;
    private BigDecimal score;
    private String status;
    private String remark;
    /** 扩展字段(JSON 字符串,key 来自 biz_ext_field_config.field_key) */
    private String extFields;
}
