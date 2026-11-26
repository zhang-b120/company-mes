package com.aimes.modules.bom.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 工艺路线工序关联（工艺路线 vs 工序的多对多关系）
 * 覆盖字段为 NULL 时继承工序库的默认值
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_process_route_step")
public class BizProcessRouteStep extends BaseEntity {
    private Long routeId;
    private Long processId;
    private Integer stepNo;
    private Integer stepGroup;
    /** 覆盖标准工时（NULL=继承工序库） */
    private Integer standardTime;
    /** 覆盖计件单价（NULL=继承工序库） */
    private BigDecimal piecePrice;
    /** 覆盖绑定班组（NULL=继承工序库） */
    private Long teamId;
    /** 覆盖SOP描述（NULL=继承工序库） */
    private String sopDescription;
    /** 覆盖入半成品库（NULL=继承工序库） */
    private Integer isSemiStock;
    /** 覆盖可外协（NULL=继承工序库） */
    private Integer isOutsourceable;
    /** 覆盖质检模板ID（NULL=继承工序库） */
    private Long qcTemplateId;

    // ===== 非持久化字段（服务层填充） =====

    @TableField(exist = false)
    private String processName;

    @TableField(exist = false)
    private String equipmentIds;

    @TableField(exist = false)
    private String sopAttachmentUrls;
}
