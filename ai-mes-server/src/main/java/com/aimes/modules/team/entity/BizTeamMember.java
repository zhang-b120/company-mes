package com.aimes.modules.team.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_team_member")
public class BizTeamMember extends BaseEntity {
    private Long teamId;
    private Long userId;
    private BigDecimal weight;
    private LocalDateTime joinedAt;
}
