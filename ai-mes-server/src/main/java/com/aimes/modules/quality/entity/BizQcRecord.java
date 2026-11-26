package com.aimes.modules.quality.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_qc_record")
public class BizQcRecord extends BaseEntity {
    private Long qcTaskId;
    private String recordType;      // qualified / unqualified
    private BigDecimal quantity;
    private String batchNo;
    private Long reporterId;
    private String equipment;
    private String defectItems;     // JSON: [{itemName, standardValue, actualValue, remark}]
    private String photoUrls;
    private String remark;
}
