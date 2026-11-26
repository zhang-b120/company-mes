package com.aimes.modules.mrp.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_production_plan")
public class BizProductionPlan extends BaseEntity {
    private String planNo;
    private String planName;
    private LocalDate planDateFrom;
    private LocalDate planDateTo;
    private String status;
    private String createType;
    private String remark;
    /** 扩展字段(继承自销售订单) */
    private String extFields;
}
