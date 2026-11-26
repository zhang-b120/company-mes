package com.aimes.modules.ai.vo;

import lombok.Data;

import java.util.List;

/**
 * AI排产建议结果VO
 * 包含排产计划、阻塞任务和排产摘要
 */
@Data
public class AiSchedulingVO {

    /** 排产计划列表 */
    private List<SchedulingPlan> schedulingPlan;

    /** 无法排产的阻塞任务列表 */
    private List<BlockedTask> blockedTasks;

    /** 排产建议摘要说明 */
    private String summary;

    /**
     * 排产计划项
     */
    @Data
    public static class SchedulingPlan {
        /** 工单ID */
        private Long workOrderId;

        /** 工单编号 */
        private String woNo;

        /** 分配的班组/人员ID */
        private Long assigneeId;

        /** 分配的班组/人员名称 */
        private String assigneeName;

        /** 建议开始时间 */
        private String startTime;

        /** 建议结束时间 */
        private String endTime;

        /** 优先级 */
        private Integer priority;

        /** 排产理由 */
        private String reason;
    }

    /**
     * 阻塞任务项
     */
    @Data
    public static class BlockedTask {
        /** 工单ID */
        private Long workOrderId;

        /** 工单编号 */
        private String woNo;

        /** 阻塞原因 */
        private String blockReason;

        /** 建议处理措施 */
        private String suggestion;
    }
}
