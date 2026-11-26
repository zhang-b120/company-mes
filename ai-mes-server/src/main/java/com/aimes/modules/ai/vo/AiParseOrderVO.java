package com.aimes.modules.ai.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI智能接单解析结果VO
 * 包含识别出的客户信息、订单明细和总金额
 */
@Data
public class AiParseOrderVO {

    /** 多订单分组 (按 订单编号+客户名 划分) — 主流程使用 */
    private List<OrderGroup> orders;

    /** 兼容旧版单订单字段: 当只识别出一个订单时, 等同 orders[0] 的展开 */
    private CustomerInfo customer;
    private List<OrderItem> items;
    private BigDecimal totalAmount;

    /**
     * 一组订单 = 一张订单 (一个客户, 一组明细)
     */
    @Data
    public static class OrderGroup {
        /** 原始订单号(可空, 空则系统自动生成) */
        private String orderNo;
        /** 客户信息 */
        private CustomerInfo customer;
        /** 联系人 */
        private String contactName;
        /** 收货地址 */
        private String shippingAddress;
        /** 订单交货日期(订单头) */
        private String deliveryDate;
        /** 订单明细列表 */
        private List<OrderItem> items;
        /** 订单含税总金额 */
        private BigDecimal totalAmount;
        /** 备注 */
        private String remark;
    }

    /**
     * 客户信息
     */
    @Data
    public static class CustomerInfo {
        /** 客户名称（原始文本中识别的名称） */
        private String name;

        /** 系统匹配到的客户ID（可能为空表示未匹配到） */
        private Long matchedId;

        /** 匹配置信度（0.0~1.0） */
        private BigDecimal confidence;
    }

    /**
     * 订单明细项
     */
    @Data
    public static class OrderItem {
        /** 产品名称 */
        private String productName;

        /** 匹配到的产品ID（可能为空） */
        private Long matchedProductId;

        /** 规格型号 */
        private String spec;

        /** 数量 */
        private BigDecimal qty;

        /** 单价 */
        private BigDecimal unitPrice;

        /** 交货日期 */
        private String deliveryDate;

        /** 备注 */
        private String remark;
    }
}
