package com.aimes.modules.workreport.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_exception_report")
public class BizExceptionReport extends BaseEntity {
    private Long taskId;
    private Long workOrderId;
    private Long reporterId;
    private String exceptionType;
    private String description;
    private String photoUrls;
    private String urgency;
    private String status;
    private Long handlerId;
    private String handleResult;
    private Integer isEscalated;
    private java.time.LocalDateTime escalatedAt;
}
