package com.aimes.modules.order.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_order_followup")
public class BizOrderFollowup extends BaseEntity {
    /** 订单ID */
    private Long orderId;
    /** 跟进类型：phone/visit/online/other */
    private String followupType;
    /** 跟进内容 */
    private String content;
    /** 下次跟进日期 */
    private LocalDate nextFollowupDate;
    /** 跟进人姓名（冗余存储） */
    private String creatorName;
}
