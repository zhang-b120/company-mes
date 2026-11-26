package com.aimes.modules.team.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_team")
public class BizTeam extends BaseEntity {
    private String teamName;
    private String workshop;
    private Long leaderId;
    private String shiftType;
    private LocalDate effectiveDate;
    private String status;
    private String remark;
}
