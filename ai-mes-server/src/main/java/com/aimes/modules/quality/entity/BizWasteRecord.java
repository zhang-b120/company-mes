package com.aimes.modules.quality.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_waste_record")
public class BizWasteRecord extends BaseEntity {
    /** 记录日期 */
    private LocalDate recordDate;
    /** 废料名称 */
    private String wasteName;
    /** 废料数量 */
    private BigDecimal quantity;
    /** 单位 */
    private String unit;
    /** 缺陷类型/原因分类 */
    private String defectType;
    /** 废料原因描述 */
    private String reason;
    /** 操作人 */
    private String operatorName;
    /** 关联工单号 */
    private String workOrderNo;
    /** 关联产品名称 */
    private String productName;
    /** 备注 */
    private String remark;
}
