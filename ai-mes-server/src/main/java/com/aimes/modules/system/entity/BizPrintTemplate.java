package com.aimes.modules.system.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_print_template")
public class BizPrintTemplate extends BaseEntity {
    /** 业务键 (如 purchase_receipt_reconciliation), 同 biz_type 下应当唯一 */
    private String templateCode;
    private String templateName;
    /** 关联业务类型 (purchase_receipt / work_order / sales_order / ...) */
    private String bizType;
    /** hiprint 模板 JSON */
    private String templateJson;
    /** 是否启用 (同 biz_type+code 一般只允许一条 active) */
    private Integer isActive;
    private String remark;
}
