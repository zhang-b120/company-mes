package com.aimes.modules.production.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_rework_order_step")
public class BizReworkOrderStep extends BaseEntity {
    private Long reworkOrderId;
    private Long processId;
    /** 关联的原工单工序任务ID，用于继承质检模板 */
    private Long processTaskId;
    private String stepName;
    private Long assignedUserId;
    private Integer sortOrder;
    private String status;          // pending / qc_pending(待质检) / completed
    /** 本工序完工时建的质检任务 id (BizQcTask), 为空表示工序无质检模板 */
    private Long qcTaskId;
    private LocalDateTime completedAt;
    private String remark;
}
