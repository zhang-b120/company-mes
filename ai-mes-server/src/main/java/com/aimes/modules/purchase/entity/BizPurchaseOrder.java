package com.aimes.modules.purchase.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "biz_purchase_order", autoResultMap = true)
public class BizPurchaseOrder extends BaseEntity {
    private String poNo;
    private Long supplierId;
    private String contactName;
    private BigDecimal totalAmount;
    private LocalDate deliveryDate;
    private String paymentTerms;
    private String status;
    @TableField(exist = false)
    private String invTaskGenStatus;
    @TableField(exist = false)
    private String invTaskProcessStatus;
    @TableField(exist = false)
    private String invReturnReason;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private String approveRemark;
    private BigDecimal paidAmount;
    private String remark;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Object> attachmentUrls;

    /** 扩展字段(JSON 字符串,key 来自 biz_ext_field_config.field_key) */
    private String extFields;
}
