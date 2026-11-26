package com.aimes.modules.system.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_notification")
public class SysNotification extends BaseEntity {
    private Long userId;
    private String title;
    private String content;
    private String notificationType;
    private String relatedType;
    private Long relatedId;
    private Integer isRead;
}
