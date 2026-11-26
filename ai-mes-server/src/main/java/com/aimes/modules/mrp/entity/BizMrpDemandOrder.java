package com.aimes.modules.mrp.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MRP需求订单池
 * 销售订单"转计划单"时写入此表，MRP从此表读取待运算订单
 * 订单取消/作废/完成时从此表删除
 * 只保留关联字段，其余数据实时从 biz_sales_order 查询
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_mrp_demand_order")
public class BizMrpDemandOrder extends BaseEntity {
    private Long orderId;     // 源销售订单ID
    private String orderNo;   // 订单号（不可变，保留避免每次JOIN）
}
