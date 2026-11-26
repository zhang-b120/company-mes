package com.aimes.modules.production.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 工序图纸（版本化）。
 * - 排产时上传：plan_item_step_id 有值
 * - 工单中再上传：plan_item_step_id 留空，process_task_id + work_order_id 有值
 * - 按 product_id + step_no 聚合查询所有版本（跨计划/工单）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_process_drawing")
public class BizProcessDrawing extends BaseEntity {
    private Long productId;
    private Integer stepNo;
    private String stepName;
    private Long planItemStepId;
    private Long processTaskId;
    private Long workOrderId;
    private Integer version;
    private String fileName;
    private String fileUrl;
    private Long fileSize;
    private String remark;
    private Long uploadedBy;
    private LocalDateTime uploadedAt;
}
