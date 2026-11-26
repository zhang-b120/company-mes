package com.aimes.modules.bom.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_process_route")
public class BizProcessRoute extends BaseEntity {
    private String routeName;
    private Long productId;
    private String versionNo;
    private String status;
    private String remark;

    @TableField(exist = false)
    private List<BizProcessRouteStep> steps;
}
