package com.aimes.modules.ai.chat;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 表结构上下文构建器 — 根据用户问题选取相关表的DDL摘要给LLM
 * <p>
 * 按业务领域分组注册表结构，避免将全部50+张表都发给LLM浪费token。
 * 根据用户问题中的关键词匹配相关领域，只发送相关表的精简DDL。
 */
@Component
public class SchemaContextBuilder {

    /** 领域 -> 表DDL列表 */
    private final Map<String, List<String>> domainSchemas = new LinkedHashMap<>();

    /** 关键词 -> 领域 */
    private static final Map<String, String> KEYWORD_DOMAIN = new LinkedHashMap<>();

    static {
        // 订单
        KEYWORD_DOMAIN.put("订单", "order");
        KEYWORD_DOMAIN.put("销售", "order");
        KEYWORD_DOMAIN.put("客户", "order");
        KEYWORD_DOMAIN.put("回款", "order");
        KEYWORD_DOMAIN.put("发货", "order");
        // 生产
        KEYWORD_DOMAIN.put("工单", "production");
        KEYWORD_DOMAIN.put("生产", "production");
        KEYWORD_DOMAIN.put("排产", "production");
        KEYWORD_DOMAIN.put("工序", "production");
        KEYWORD_DOMAIN.put("报工", "production");
        // 库存
        KEYWORD_DOMAIN.put("库存", "inventory");
        KEYWORD_DOMAIN.put("入库", "inventory");
        KEYWORD_DOMAIN.put("出库", "inventory");
        KEYWORD_DOMAIN.put("仓库", "inventory");
        // 采购
        KEYWORD_DOMAIN.put("采购", "purchase");
        KEYWORD_DOMAIN.put("供应商", "purchase");
        KEYWORD_DOMAIN.put("到货", "purchase");
        // 质量
        KEYWORD_DOMAIN.put("质检", "quality");
        KEYWORD_DOMAIN.put("质量", "quality");
        KEYWORD_DOMAIN.put("不良", "quality");
        KEYWORD_DOMAIN.put("缺陷", "quality");
        KEYWORD_DOMAIN.put("合格率", "quality");
        // 财务
        KEYWORD_DOMAIN.put("应收", "finance");
        KEYWORD_DOMAIN.put("应付", "finance");
        KEYWORD_DOMAIN.put("工资", "finance");
        KEYWORD_DOMAIN.put("成本", "finance");
        KEYWORD_DOMAIN.put("利润", "finance");
        // 基础
        KEYWORD_DOMAIN.put("物料", "master");
        KEYWORD_DOMAIN.put("产品", "master");
        KEYWORD_DOMAIN.put("BOM", "master");
        // 人员
        KEYWORD_DOMAIN.put("员工", "system");
        KEYWORD_DOMAIN.put("用户", "system");
        KEYWORD_DOMAIN.put("班组", "system");
    }

    @PostConstruct
    void init() {
        // ===== 订单领域 =====
        domainSchemas.put("order", List.of(
                "-- biz_sales_order 销售订单\n" +
                "-- id BIGINT 主键\n" +
                "-- order_no VARCHAR(30) 订单编号(SO+时间戳)\n" +
                "-- customer_id BIGINT 客户ID→biz_customer.id\n" +
                "-- contact_name VARCHAR(50) 联系人\n" +
                "-- total_amount DECIMAL(12,2) 订单总金额\n" +
                "-- delivery_date DATE 交货日期\n" +
                "-- payment_amount DECIMAL(12,2) 已回款金额\n" +
                "-- status VARCHAR(20) 状态(draft/pending_approval/confirmed/in_production/partial_shipped/shipped/completed/settled/voided/cancelled)\n" +
                "-- salesman_id BIGINT 业务员ID→sys_user.id\n" +
                "-- create_time DATETIME 创建时间\n" +
                "-- deleted TINYINT 删除标记(0未删1已删)",

                "-- biz_sales_order_item 订单明细\n" +
                "-- id BIGINT 主键\n" +
                "-- order_id BIGINT 订单ID→biz_sales_order.id\n" +
                "-- product_id BIGINT 产品ID→biz_product.id\n" +
                "-- quantity DECIMAL(12,4) 数量\n" +
                "-- unit_price DECIMAL(12,2) 单价\n" +
                "-- amount DECIMAL(12,2) 金额(数量×单价)\n" +
                "-- delivery_date DATE 交货日期\n" +
                "-- produced_qty DECIMAL(12,4) 已生产数量\n" +
                "-- shipped_qty DECIMAL(12,4) 已发货数量\n" +
                "-- deleted TINYINT 删除标记",

                "-- biz_customer 客户\n" +
                "-- id BIGINT 主键\n" +
                "-- customer_no VARCHAR(30) 客户编号\n" +
                "-- customer_name VARCHAR(100) 客户名称\n" +
                "-- contact_name VARCHAR(50) 联系人\n" +
                "-- phone VARCHAR(20) 电话\n" +
                "-- status VARCHAR(20) 状态(active/disabled)\n" +
                "-- deleted TINYINT 删除标记"
        ));

        // ===== 生产领域 =====
        domainSchemas.put("production", List.of(
                "-- biz_work_order 生产工单\n" +
                "-- id BIGINT 主键\n" +
                "-- wo_no VARCHAR(30) 工单编号(WO+时间戳)\n" +
                "-- order_id BIGINT 销售订单ID→biz_sales_order.id\n" +
                "-- product_id BIGINT 产品ID→biz_product.id\n" +
                "-- plan_qty DECIMAL(12,4) 计划数量\n" +
                "-- completed_qty DECIMAL(12,4) 完成数量\n" +
                "-- delivery_date DATE 交货日期\n" +
                "-- priority INT 优先级(0-5)\n" +
                "-- status VARCHAR(20) 状态(pending/scheduled/in_progress/completed/cancelled)\n" +
                "-- create_time DATETIME 创建时间\n" +
                "-- deleted TINYINT 删除标记",

                "-- biz_wo_process_task 工单工序任务\n" +
                "-- id BIGINT 主键\n" +
                "-- work_order_id BIGINT 工单ID→biz_work_order.id\n" +
                "-- step_no INT 工序序号\n" +
                "-- step_name VARCHAR(50) 工序名称\n" +
                "-- standard_time INT 标准工时(分钟)\n" +
                "-- piece_price DECIMAL(10,2) 计件单价\n" +
                "-- plan_qty DECIMAL(12,4) 计划数量\n" +
                "-- qualified_qty DECIMAL(12,4) 合格数量\n" +
                "-- defect_qty DECIMAL(12,4) 不良数量\n" +
                "-- assigned_user_id BIGINT 指派人员\n" +
                "-- assigned_team_id BIGINT 指派班组\n" +
                "-- status VARCHAR(20) 状态(pending/ready/in_progress/completed)\n" +
                "-- is_outsourced TINYINT 是否外协\n" +
                "-- deleted TINYINT 删除标记",

                "-- biz_work_report 报工记录\n" +
                "-- id BIGINT 主键\n" +
                "-- task_id BIGINT 工序任务ID→biz_wo_process_task.id\n" +
                "-- work_order_id BIGINT 工单ID\n" +
                "-- reporter_id BIGINT 报工人→sys_user.id\n" +
                "-- qualified_qty DECIMAL(12,4) 合格数量\n" +
                "-- defect_qty DECIMAL(12,4) 不良数量\n" +
                "-- work_hours DECIMAL(6,2) 工时\n" +
                "-- piece_wage DECIMAL(10,2) 计件工资\n" +
                "-- status VARCHAR(20) 状态(submitted/approved/rejected)\n" +
                "-- deleted TINYINT 删除标记"
        ));

        // ===== 库存领域 =====
        domainSchemas.put("inventory", List.of(
                "-- biz_inventory 库存\n" +
                "-- id BIGINT 主键\n" +
                "-- material_id BIGINT 物料ID→biz_material.id(也可能是产品)\n" +
                "-- material_name VARCHAR(100) 物料名称(冗余)\n" +
                "-- warehouse_id BIGINT 仓库ID→sys_warehouse.id\n" +
                "-- quantity DECIMAL(12,4) 库存数量\n" +
                "-- locked_qty DECIMAL(12,4) 锁定数量\n" +
                "-- deleted TINYINT 删除标记",

                "-- biz_inv_transaction 库存流水\n" +
                "-- id BIGINT 主键\n" +
                "-- material_id BIGINT 物料ID\n" +
                "-- warehouse_id BIGINT 仓库ID\n" +
                "-- tx_type VARCHAR(20) 类型(in_purchase/in_production/in_return/out_production/out_sales/out_adjust等)\n" +
                "-- quantity DECIMAL(12,4) 数量(正数入库负数出库)\n" +
                "-- source_type VARCHAR(30) 来源类型\n" +
                "-- source_no VARCHAR(50) 来源单号\n" +
                "-- create_time DATETIME 交易时间\n" +
                "-- deleted TINYINT 删除标记"
        ));

        // ===== 采购领域 =====
        domainSchemas.put("purchase", List.of(
                "-- biz_purchase_request 采购申请\n" +
                "-- id BIGINT 主键\n" +
                "-- request_no VARCHAR(30) 申请编号\n" +
                "-- material_id BIGINT 物料ID\n" +
                "-- quantity DECIMAL(12,4) 申请数量\n" +
                "-- status VARCHAR(20) 状态(draft/pending/approved/rejected/ordered)\n" +
                "-- deleted TINYINT 删除标记",

                "-- biz_purchase_order 采购订单\n" +
                "-- id BIGINT 主键\n" +
                "-- order_no VARCHAR(30) 采购单号\n" +
                "-- supplier_id BIGINT 供应商ID→biz_supplier.id\n" +
                "-- total_amount DECIMAL(12,2) 总金额\n" +
                "-- delivery_date DATE 交货日期\n" +
                "-- status VARCHAR(20) 状态(draft/confirmed/partial_received/completed/cancelled)\n" +
                "-- deleted TINYINT 删除标记",

                "-- biz_purchase_order_item 采购明细\n" +
                "-- id BIGINT 主键\n" +
                "-- purchase_order_id BIGINT 采购单ID\n" +
                "-- material_id BIGINT 物料ID\n" +
                "-- quantity DECIMAL(12,4) 采购数量\n" +
                "-- unit_price DECIMAL(12,2) 单价\n" +
                "-- amount DECIMAL(12,2) 金额\n" +
                "-- received_qty DECIMAL(12,4) 已收货数量\n" +
                "-- deleted TINYINT 删除标记",

                "-- biz_supplier 供应商\n" +
                "-- id BIGINT 主键\n" +
                "-- supplier_no VARCHAR(30) 供应商编号\n" +
                "-- supplier_name VARCHAR(100) 供应商名称\n" +
                "-- supplier_type VARCHAR(50) 类型(material/outsource/both)\n" +
                "-- contact_name VARCHAR(50) 联系人\n" +
                "-- phone VARCHAR(20) 电话\n" +
                "-- status VARCHAR(20) 状态\n" +
                "-- deleted TINYINT 删除标记"
        ));

        // ===== 质量领域 =====
        domainSchemas.put("quality", List.of(
                "-- biz_qc_task 质检任务\n" +
                "-- id BIGINT 主键\n" +
                "-- task_no VARCHAR(30) 任务编号\n" +
                "-- inspection_type VARCHAR(20) 检验类型(incoming来料检/process过程检/final成品检/outsource外协检)\n" +
                "-- material_id BIGINT 物料/产品ID\n" +
                "-- batch_no VARCHAR(50) 批次号\n" +
                "-- sample_qty DECIMAL(12,4) 抽检数量\n" +
                "-- pass_qty DECIMAL(12,4) 合格数量\n" +
                "-- fail_qty DECIMAL(12,4) 不合格数量\n" +
                "-- result VARCHAR(20) 结果(pass/fail/conditional_pass)\n" +
                "-- inspector_id BIGINT 检验员\n" +
                "-- create_time DATETIME\n" +
                "-- deleted TINYINT 删除标记",

                "-- biz_defect_record 不良品记录\n" +
                "-- id BIGINT 主键\n" +
                "-- defect_no VARCHAR(30) 不良品编号\n" +
                "-- material_id BIGINT 物料ID\n" +
                "-- batch_no VARCHAR(50) 批次号\n" +
                "-- defect_qty DECIMAL(12,4) 不良数量\n" +
                "-- defect_type VARCHAR(50) 不良类型(外观/尺寸/功能/材质等)\n" +
                "-- defect_level VARCHAR(20) 等级(critical/major/minor)\n" +
                "-- unit_cost DECIMAL(12,2) 单位成本\n" +
                "-- total_loss DECIMAL(12,2) 总损失金额\n" +
                "-- status VARCHAR(20) 状态(pending/processed/scrapped/reworked)\n" +
                "-- create_time DATETIME\n" +
                "-- deleted TINYINT 删除标记"
        ));

        // ===== 财务领域 =====
        domainSchemas.put("finance", List.of(
                "-- biz_receivable 应收账款\n" +
                "-- id BIGINT 主键\n" +
                "-- customer_id BIGINT 客户ID→biz_customer.id\n" +
                "-- customer_name VARCHAR(100) 客户名称(冗余)\n" +
                "-- order_id BIGINT 关联订单ID\n" +
                "-- total_amount DECIMAL(12,2) 应收金额\n" +
                "-- received_amount DECIMAL(12,2) 已收金额\n" +
                "-- due_date DATE 到期日\n" +
                "-- status VARCHAR(20) 状态(pending/partial/completed/overdue)\n" +
                "-- deleted TINYINT 删除标记",

                "-- biz_payable 应付账款\n" +
                "-- id BIGINT 主键\n" +
                "-- supplier_id BIGINT 供应商ID→biz_supplier.id\n" +
                "-- supplier_name VARCHAR(100) 供应商名称(冗余)\n" +
                "-- purchase_order_id BIGINT 关联采购单ID\n" +
                "-- total_amount DECIMAL(12,2) 应付金额\n" +
                "-- paid_amount DECIMAL(12,2) 已付金额\n" +
                "-- due_date DATE 到期日\n" +
                "-- status VARCHAR(20) 状态(pending/partial/completed/overdue)\n" +
                "-- deleted TINYINT 删除标记",

                "-- biz_wage_record 工资记录\n" +
                "-- id BIGINT 主键\n" +
                "-- user_id BIGINT 员工ID→sys_user.id\n" +
                "-- work_order_id BIGINT 工单ID\n" +
                "-- task_id BIGINT 工序任务ID\n" +
                "-- wage_type VARCHAR(20) 类型(piece/hourly/overtime)\n" +
                "-- amount DECIMAL(10,2) 金额\n" +
                "-- create_time DATETIME\n" +
                "-- deleted TINYINT 删除标记"
        ));

        // ===== 基础数据领域 =====
        domainSchemas.put("master", List.of(
                "-- biz_product 产品\n" +
                "-- id BIGINT 主键\n" +
                "-- product_no VARCHAR(30) 产品编号\n" +
                "-- product_name VARCHAR(100) 产品名称\n" +
                "-- spec TEXT 规格\n" +
                "-- unit VARCHAR(20) 单位\n" +
                "-- category VARCHAR(50) 类别\n" +
                "-- standard_cost DECIMAL(12,2) 标准成本\n" +
                "-- status VARCHAR(20) 状态\n" +
                "-- deleted TINYINT 删除标记",

                "-- biz_material 物料\n" +
                "-- id BIGINT 主键\n" +
                "-- material_no VARCHAR(30) 物料编号\n" +
                "-- material_name VARCHAR(100) 物料名称\n" +
                "-- spec TEXT 规格\n" +
                "-- category VARCHAR(50) 类别(raw_material/auxiliary/packaging/purchased)\n" +
                "-- unit VARCHAR(20) 单位\n" +
                "-- safety_stock DECIMAL(12,4) 安全库存\n" +
                "-- status VARCHAR(20) 状态\n" +
                "-- deleted TINYINT 删除标记"
        ));

        // ===== 系统/人员领域 =====
        domainSchemas.put("system", List.of(
                "-- sys_user 用户\n" +
                "-- id BIGINT 主键\n" +
                "-- username VARCHAR(50) 登录账号\n" +
                "-- real_name VARCHAR(50) 真实姓名\n" +
                "-- phone VARCHAR(20) 手机号\n" +
                "-- dept_id BIGINT 部门ID\n" +
                "-- workshop VARCHAR(50) 所属车间\n" +
                "-- status VARCHAR(20) 状态(active/disabled)\n" +
                "-- deleted TINYINT 删除标记",

                "-- biz_team 班组\n" +
                "-- id BIGINT 主键\n" +
                "-- team_name VARCHAR(50) 班组名称\n" +
                "-- workshop VARCHAR(50) 车间\n" +
                "-- leader_id BIGINT 组长ID→sys_user.id\n" +
                "-- status VARCHAR(20) 状态\n" +
                "-- deleted TINYINT 删除标记",

                "-- biz_team_member 班组成员\n" +
                "-- id BIGINT 主键\n" +
                "-- team_id BIGINT 班组ID\n" +
                "-- user_id BIGINT 用户ID\n" +
                "-- deleted TINYINT 删除标记"
        ));
    }

    /**
     * 根据用户问题构建相关的表结构上下文
     */
    public String buildForQuestion(String question) {
        Set<String> domains = new LinkedHashSet<>();

        for (Map.Entry<String, String> entry : KEYWORD_DOMAIN.entrySet()) {
            if (question.contains(entry.getKey())) {
                domains.add(entry.getValue());
            }
        }

        // 默认：订单 + 基础数据
        if (domains.isEmpty()) {
            domains.add("order");
            domains.add("master");
        }

        // 总是包含基础数据（JOIN时常需要产品名/客户名）
        domains.add("master");

        StringBuilder sb = new StringBuilder();
        sb.append("## 数据库表结构 (MySQL)\n\n");
        sb.append("重要约定:\n");
        sb.append("- 所有业务表有 deleted 字段，查询时必须加 WHERE deleted=0\n");
        sb.append("- ID字段是BIGINT自增主键\n");
        sb.append("- 需要显示名称时JOIN关联表(如客户名JOIN biz_customer, 产品名JOIN biz_product)\n\n");

        for (String domain : domains) {
            List<String> schemas = domainSchemas.get(domain);
            if (schemas != null) {
                for (String schema : schemas) {
                    sb.append(schema).append("\n\n");
                }
            }
        }

        return sb.toString();
    }
}
