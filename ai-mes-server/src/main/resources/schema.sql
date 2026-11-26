-- AI-MES Database Schema
-- Generated from init111.sql
-- Contains ALL table DDL (biz_ and sys_) - NO INSERT data

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
-- Table structure for table `biz_approval_flow`
--

DROP TABLE IF EXISTS `biz_approval_flow`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_approval_flow` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `flow_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '流程名称',
  `flow_code` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '流程编码',
  `business_type` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT '业务类型:sales_order/purchase_order等',
  `enabled` tinyint(1) DEFAULT '1' COMMENT '是否启用',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_code` (`flow_code`),
  KEY `idx_business_type` (`business_type`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审批流程定义';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_approval_instance`
--

DROP TABLE IF EXISTS `biz_approval_instance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_approval_instance` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `flow_id` bigint NOT NULL,
  `business_type` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `business_id` bigint NOT NULL COMMENT '关联业务单据ID',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/CANCELLED',
  `current_node_id` bigint DEFAULT NULL COMMENT '当前审批节点',
  `applicant_id` bigint NOT NULL COMMENT '申请人',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_business` (`business_type`,`business_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审批实例';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_approval_node`
--

DROP TABLE IF EXISTS `biz_approval_node`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_approval_node` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `flow_id` bigint NOT NULL,
  `node_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '节点名称',
  `node_order` int NOT NULL COMMENT '节点顺序',
  `approval_mode` varchar(10) COLLATE utf8mb4_general_ci DEFAULT 'OR' COMMENT 'OR=或签/AND=会签',
  `remark` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_flow_id` (`flow_id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审批节点';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_approval_node_approver`
--

DROP TABLE IF EXISTS `biz_approval_node_approver`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_approval_node_approver` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `node_id` bigint NOT NULL,
  `approver_type` varchar(10) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'USER=指定用户/ROLE=指定角色',
  `approver_id` bigint NOT NULL COMMENT 'user_id或role_id',
  PRIMARY KEY (`id`),
  KEY `idx_node_id` (`node_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审批节点审批人';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_approval_record`
--

DROP TABLE IF EXISTS `biz_approval_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_approval_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `instance_id` bigint NOT NULL,
  `node_id` bigint NOT NULL,
  `approver_id` bigint NOT NULL COMMENT '实际审批人',
  `action` varchar(10) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'APPROVE/REJECT',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `action_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_instance_id` (`instance_id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审批记录';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_bom_item`
--

DROP TABLE IF EXISTS `biz_bom_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_bom_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `bom_version_id` bigint NOT NULL,
  `parent_item_id` bigint DEFAULT '0' COMMENT '0=一级',
  `item_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'raw_material/semi_finished/purchased',
  `material_id` bigint DEFAULT NULL,
  `product_id` bigint DEFAULT NULL COMMENT '半成品产品ID',
  `quantity` decimal(12,4) NOT NULL,
  `wastage_rate` decimal(5,2) DEFAULT '0.00',
  `actual_quantity` decimal(12,4) DEFAULT NULL COMMENT '=quantity*(1+wastage_rate/100)',
  `substitute_group` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `sort_order` int DEFAULT '0',
  `remark` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  `source_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'purchase' COMMENT '来源: manufacture(自制)/purchase(外购)',
  PRIMARY KEY (`id`),
  KEY `idx_bom_version` (`bom_version_id`)
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='BOM子件';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_bom_substitute`
--

DROP TABLE IF EXISTS `biz_bom_substitute`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_bom_substitute` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `group_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `bom_item_id` bigint NOT NULL,
  `material_id` bigint NOT NULL,
  `priority` int DEFAULT '1',
  `substitute_ratio` decimal(6,4) DEFAULT '1.0000',
  `remark` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_bom_item` (`bom_item_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='BOM替代料';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_bom_version`
--

DROP TABLE IF EXISTS `biz_bom_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_bom_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` bigint NOT NULL,
  `version_no` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'draft' COMMENT 'draft/active/deprecated',
  `total_material_cost` decimal(12,2) DEFAULT '0.00',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_product` (`product_id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='BOM版本';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_customer`
--

DROP TABLE IF EXISTS `biz_customer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_customer` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `customer_no` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'C+时间戳',
  `customer_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `contact_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `phone` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `email` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `address` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `settlement_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'cash' COMMENT 'cash/monthly_30/monthly_60/monthly_90',
  `credit_limit` decimal(12,2) DEFAULT '0.00' COMMENT '信用额度 0=不限',
  `tax_rate` decimal(5,2) DEFAULT '13.00',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'active',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_no` (`customer_no`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_customer_followup`
--

DROP TABLE IF EXISTS `biz_customer_followup`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_customer_followup` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `followup_type` varchar(30) DEFAULT NULL COMMENT '跟进类型：phone/visit/online/other',
  `content` varchar(2000) NOT NULL COMMENT '跟进内容',
  `next_followup_date` date DEFAULT NULL COMMENT '下次跟进日期',
  `creator_name` varchar(50) DEFAULT NULL COMMENT '跟进人姓名',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_customer_id` (`customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户跟进记录';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_defect_record`
--

DROP TABLE IF EXISTS `biz_defect_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_defect_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `defect_no` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `source_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `source_id` bigint DEFAULT NULL,
  `product_id` bigint DEFAULT NULL,
  `material_id` bigint DEFAULT NULL,
  `work_order_id` bigint DEFAULT NULL,
  `batch_no` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `quantity` decimal(12,4) NOT NULL,
  `defect_type` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `defect_reason` text COLLATE utf8mb4_general_ci,
  `defect_level` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `handle_path` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `handle_status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'pending',
  `handle_result` text COLLATE utf8mb4_general_ci,
  `rework_wo_id` bigint DEFAULT NULL,
  `scrap_qty` decimal(12,4) DEFAULT '0.0000',
  `scrap_cost` decimal(12,2) DEFAULT '0.00',
  `handler_id` bigint DEFAULT NULL,
  `handled_at` datetime DEFAULT NULL,
  `photo_urls` text COLLATE utf8mb4_general_ci,
  `qc_task_id` bigint DEFAULT NULL COMMENT '关联质检任务ID',
  `process_task_id` bigint DEFAULT NULL COMMENT '关联工序任务ID',
  `rework_step_no` int DEFAULT NULL COMMENT '返工目标工序号',
  `rework_step_name` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '返工目标工序名称',
  `scrap_reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '报废原因',
  `handle_remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '处置备注',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_defect_no` (`defect_no`),
  KEY `idx_work_order` (`work_order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='不良品记录';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_exception_report`
--

DROP TABLE IF EXISTS `biz_exception_report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_exception_report` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` bigint DEFAULT NULL,
  `work_order_id` bigint DEFAULT NULL,
  `reporter_id` bigint NOT NULL,
  `exception_type` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'equipment_fault/material_shortage/quality_issue/safety_hazard/other',
  `description` text COLLATE utf8mb4_general_ci NOT NULL,
  `photo_urls` text COLLATE utf8mb4_general_ci,
  `urgency` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'medium' COMMENT 'high/medium/low',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'pending' COMMENT 'pending/processing/resolved',
  `handler_id` bigint DEFAULT NULL,
  `handle_result` text COLLATE utf8mb4_general_ci,
  `is_escalated` tinyint(1) DEFAULT '0' COMMENT '是否已升级通知',
  `escalated_at` datetime DEFAULT NULL COMMENT '升级通知时间',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='异常上报';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_ext_field_config`
--

DROP TABLE IF EXISTS `biz_ext_field_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_ext_field_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `module` varchar(50) NOT NULL COMMENT '业务模块：sales_order/customer/supplier/purchase_order/work_order',
  `field_key` varchar(50) NOT NULL COMMENT '字段Key（英文，存入ext_fields的JSON key）',
  `field_label` varchar(100) NOT NULL COMMENT '字段显示名称',
  `field_type` varchar(20) NOT NULL DEFAULT 'text' COMMENT '类型：text/number/date/select',
  `field_options` text COMMENT '下拉选项，JSON数组，如["A","B"]',
  `show_in_list` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否在列表中显示为列',
  `show_in_form` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否在新增/编辑表单中显示',
  `show_in_search` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否作为查询条件',
  `required` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否必填',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_field_key` (`module`,`field_key`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通用扩展字段配置';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_finance_invoice`
--

DROP TABLE IF EXISTS `biz_finance_invoice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_finance_invoice` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `invoice_no` varchar(30) NOT NULL COMMENT '系统编号',
  `invoice_type` varchar(20) NOT NULL COMMENT 'input(进项)/output(销项)',
  `invoice_code` varchar(30) DEFAULT NULL COMMENT '发票代码',
  `invoice_number` varchar(30) DEFAULT NULL COMMENT '发票号码',
  `invoice_date` date DEFAULT NULL COMMENT '开票日期',
  `amount` decimal(12,2) DEFAULT NULL COMMENT '金额(不含税)',
  `tax_rate` decimal(5,2) DEFAULT NULL COMMENT '税率',
  `tax_amount` decimal(12,2) DEFAULT NULL COMMENT '税额',
  `total_amount` decimal(12,2) DEFAULT NULL COMMENT '价税合计',
  `buyer_name` varchar(100) DEFAULT NULL COMMENT '购方名称',
  `buyer_tax_no` varchar(50) DEFAULT NULL COMMENT '购方税号',
  `seller_name` varchar(100) DEFAULT NULL COMMENT '销方名称',
  `seller_tax_no` varchar(50) DEFAULT NULL COMMENT '销方税号',
  `related_type` varchar(30) DEFAULT NULL COMMENT '关联单据类型',
  `related_id` bigint DEFAULT NULL COMMENT '关联单据ID',
  `related_no` varchar(50) DEFAULT NULL COMMENT '关联单据号',
  `customer_id` bigint DEFAULT NULL COMMENT '客户ID(销项)',
  `supplier_id` bigint DEFAULT NULL COMMENT '供应商ID(进项)',
  `attachment_url` varchar(500) DEFAULT NULL COMMENT '发票附件URL',
  `items_json` text COMMENT '多产品明细 JSON',
  `status` varchar(20) DEFAULT 'draft' COMMENT 'draft/confirmed/cancelled',
  `remark` varchar(500) DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invoice_no` (`invoice_no`),
  KEY `idx_invoice_type` (`invoice_type`),
  KEY `idx_related` (`related_type`,`related_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='往来发票';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_inv_history`
--

DROP TABLE IF EXISTS `biz_inv_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_inv_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `history_no` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '历史单号 INH+时间戳',
  `task_id` bigint DEFAULT NULL,
  `applicant_id` bigint DEFAULT NULL COMMENT '申请人ID',
  `task_no` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `related_order_no` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `inv_type` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `batch_no` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `warehouse_id` bigint DEFAULT NULL,
  `location_id` bigint DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'normal' COMMENT 'normal/revoked',
  `operator_id` bigint DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='出入库历史';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_inv_history_item`
--

DROP TABLE IF EXISTS `biz_inv_history_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_inv_history_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `history_id` bigint NOT NULL,
  `material_id` bigint DEFAULT NULL,
  `material_no` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `material_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `spec` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `unit` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `quantity` decimal(12,4) DEFAULT NULL COMMENT '本次处理数量',
  `unit_price` decimal(12,4) DEFAULT NULL,
  `total_price` decimal(18,4) DEFAULT NULL,
  `warehouse_id` bigint DEFAULT NULL,
  `location_id` bigint DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'normal' COMMENT 'normal/revoked',
  `deleted` tinyint(1) DEFAULT '0',
  `task_item_id` bigint DEFAULT NULL COMMENT '关联库存任务明细ID，精确撤销还原',
  `secondary_unit` varchar(20) DEFAULT NULL COMMENT '辅助单位',
  `secondary_qty` decimal(18,4) DEFAULT NULL COMMENT '辅助单位数量',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='出入库历史明细';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_inv_request`
--

DROP TABLE IF EXISTS `biz_inv_request`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_inv_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `request_no` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '申请单号 INR+时间戳',
  `batch_no` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '批次号',
  `related_order_no` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '关联单据号',
  `inv_type` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT '类型',
  `customer_id` bigint DEFAULT NULL COMMENT '客户ID（退货入库）',
  `customer_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '客户名称（退货入库）',
  `warehouse_id` bigint DEFAULT NULL,
  `location_id` bigint DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'pending' COMMENT 'pending/approved/rejected/task_created',
  `applicant_id` bigint DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `attachment_urls` text COLLATE utf8mb4_general_ci COMMENT '附件 URL JSON 数组 [{name,url}]',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='入库申请';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_inv_request_item`
--

DROP TABLE IF EXISTS `biz_inv_request_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_inv_request_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `request_id` bigint NOT NULL,
  `material_id` bigint DEFAULT NULL,
  `material_no` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `material_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `spec` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `unit` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `quantity` decimal(12,4) DEFAULT NULL,
  `unit_price` decimal(12,4) DEFAULT NULL,
  `total_price` decimal(18,4) DEFAULT NULL,
  `warehouse_id` bigint DEFAULT NULL COMMENT '仓库ID',
  `location_id` bigint DEFAULT NULL COMMENT '库位ID',
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='入库申请明细';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_inv_reservation`
--

DROP TABLE IF EXISTS `biz_inv_reservation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_inv_reservation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `material_id` bigint NOT NULL,
  `warehouse_id` bigint NOT NULL,
  `reservation_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'wo_lock/order_lock/manual_lock',
  `locked_qty` decimal(12,4) NOT NULL,
  `consumed_qty` decimal(12,4) DEFAULT '0.0000',
  `remaining_qty` decimal(12,4) DEFAULT NULL,
  `related_order_type` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `related_order_id` bigint DEFAULT NULL,
  `related_order_no` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '来源单据号',
  `locked_by` bigint DEFAULT NULL,
  `expire_date` date DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'active',
  `reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `plan_item_id` bigint DEFAULT NULL COMMENT '生产计划行项ID',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_material` (`material_id`),
  KEY `idx_related` (`related_order_type`,`related_order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='锁库锁料';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_inv_snapshot`
--

DROP TABLE IF EXISTS `biz_inv_snapshot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_inv_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `snapshot_date` date NOT NULL COMMENT '快照日期（备份的是哪天的数据）',
  `record_count` int DEFAULT '0' COMMENT '记录条数',
  `snapshot_data` json DEFAULT NULL COMMENT '库存快照JSON数据',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_snapshot_date` (`snapshot_date`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='历史库存台账快照';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_inv_task`
--

DROP TABLE IF EXISTS `biz_inv_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_inv_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_no` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务编号 INK+时间戳',
  `batch_no` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `related_order_no` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `inv_type` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `warehouse_id` bigint DEFAULT NULL,
  `location_id` bigint DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'pending' COMMENT 'pending/partial/completed',
  `source_type` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'inv_request/purchase_order/manual',
  `source_id` bigint DEFAULT NULL,
  `applicant_id` bigint DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `return_reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '退回原因',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='库存任务';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_inv_task_item`
--

DROP TABLE IF EXISTS `biz_inv_task_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_inv_task_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` bigint NOT NULL,
  `material_id` bigint DEFAULT NULL,
  `material_no` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `material_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `spec` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `unit` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `quantity` decimal(12,4) DEFAULT NULL COMMENT '总数量',
  `unit_price` decimal(12,4) DEFAULT NULL,
  `total_price` decimal(18,4) DEFAULT NULL,
  `processed_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '已处理数量',
  `warehouse_id` bigint DEFAULT NULL COMMENT '物料级仓库',
  `location_id` bigint DEFAULT NULL COMMENT '物料级库位',
  `receipt_id` bigint DEFAULT NULL COMMENT '关联收货单行ID',
  `secondary_unit` varchar(20) DEFAULT NULL COMMENT '辅助单位',
  `secondary_qty` decimal(18,4) DEFAULT NULL COMMENT '辅助单位数量(总量)',
  `secondary_processed_qty` decimal(18,4) DEFAULT NULL COMMENT '辅助单位已处理数量',
  `substitute_for_material_id` bigint DEFAULT NULL COMMENT '替代料：被替代的主料ID',
  `substitute_ratio` decimal(6,4) DEFAULT NULL COMMENT '替代料：每1主料需领取的替代料数量',
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='库存任务明细';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_inv_transaction`
--

DROP TABLE IF EXISTS `biz_inv_transaction`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_inv_transaction` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `material_id` bigint NOT NULL,
  `warehouse_id` bigint NOT NULL,
  `transaction_type` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'purchase_in/production_in/semi_in/outsource_in/return_in/other_in/production_out/sales_out/outsource_out/scrap_out/other_out',
  `quantity` decimal(12,4) NOT NULL,
  `batch_no` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `related_order_type` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `related_order_id` bigint DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `secondary_unit` varchar(20) DEFAULT NULL COMMENT '辅助单位',
  `secondary_qty` decimal(18,4) DEFAULT NULL COMMENT '辅助单位数量',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_material` (`material_id`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='库存流水';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_inventory`
--

DROP TABLE IF EXISTS `biz_inventory`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_inventory` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `material_id` bigint NOT NULL,
  `warehouse_id` bigint NOT NULL COMMENT '仓库',
  `location_id` bigint DEFAULT NULL COMMENT '库位',
  `total_qty` decimal(12,4) DEFAULT '0.0000',
  `locked_qty` decimal(12,4) DEFAULT '0.0000',
  `available_qty` decimal(12,4) DEFAULT '0.0000',
  `safety_stock` decimal(12,4) DEFAULT '0.0000',
  `secondary_unit` varchar(20) DEFAULT NULL COMMENT '辅助单位',
  `secondary_total_qty` decimal(18,4) DEFAULT NULL COMMENT '辅助单位在库数量',
  `secondary_available_qty` decimal(18,4) DEFAULT NULL COMMENT '辅助单位可用数量',
  `secondary_locked_qty` decimal(18,4) DEFAULT NULL COMMENT '辅助单位锁定数量',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_material_warehouse` (`material_id`,`warehouse_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='库存台账';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_material`
--

DROP TABLE IF EXISTS `biz_material`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_material` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `material_no` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'M+时间戳',
  `material_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `spec` text COLLATE utf8mb4_general_ci COMMENT '规格,支持JSON多规格',
  `category` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'raw_material/auxiliary/packaging/purchased',
  `category_id` bigint DEFAULT NULL COMMENT '物料类别ID',
  `unit` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `unit_config` text COLLATE utf8mb4_general_ci COMMENT 'JSON多单位换算配置',
  `safety_stock` decimal(12,4) DEFAULT '0.0000',
  `max_stock` decimal(12,4) DEFAULT NULL COMMENT '最大库存',
  `min_stock` decimal(12,4) DEFAULT NULL COMMENT '最小库存',
  `standard_cost` decimal(12,2) DEFAULT NULL COMMENT '标准成本(含税)',
  `default_warehouse_id` bigint DEFAULT NULL,
  `default_supplier_id` bigint DEFAULT NULL,
  `shelf_life_days` int DEFAULT '0' COMMENT '0=不限',
  `allow_stock_lock` tinyint(1) DEFAULT '1' COMMENT '是否允许锁库',
  `material_attributes` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '物料属性:purchase,production,sales',
  `source_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'purchase' COMMENT '物料来源: purchase/manufacture/outsource',
  `purchase_lead_time` int DEFAULT '0' COMMENT '采购提前期(天)',
  `production_lead_time` int DEFAULT '0' COMMENT '生产提前期(天)',
  `min_order_qty` decimal(18,4) DEFAULT NULL COMMENT '最小采购/生产批量',
  `order_qty_multiple` decimal(18,4) DEFAULT NULL COMMENT '批量倍数',
  `mrp_enabled` tinyint DEFAULT '1' COMMENT '是否参与MRP运算',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'active',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  `is_virtual_semi` tinyint(1) DEFAULT '0' COMMENT '虚拟半成品(外协工序自动生成)',
  `drawing_no` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '图纸编号（成品类型使用）',
  `responsible_user_id` bigint DEFAULT NULL COMMENT '负责人用户ID',
  `image_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '首张图片URL',
  `images` text COLLATE utf8mb4_general_ci COMMENT '物料图片 JSON数组',
  `attachment_urls` text COLLATE utf8mb4_general_ci COMMENT '图纸附件 JSON数组',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_material_no` (`material_no`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='物料档案';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_material_category`
--

DROP TABLE IF EXISTS `biz_material_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_material_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint DEFAULT '0' COMMENT '父级ID,0为根节点',
  `category_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '类别名称',
  `category_code` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT '类别编码',
  `sort_order` int DEFAULT '0',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'active',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_code` (`category_code`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='物料类别';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_mrp_demand`
--

DROP TABLE IF EXISTS `biz_mrp_demand`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_mrp_demand` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `mrp_run_id` bigint NOT NULL COMMENT '关联MRP运行记录',
  `material_id` bigint DEFAULT NULL COMMENT '物料ID(原材料)',
  `product_id` bigint DEFAULT NULL COMMENT '产品ID(成品/半成品)',
  `demand_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '需求类型: independent/dependent',
  `item_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '物料类型: raw/semi_finished/finished',
  `gross_qty` decimal(18,4) DEFAULT '0.0000' COMMENT '毛需求',
  `on_hand_qty` decimal(18,4) DEFAULT '0.0000' COMMENT '库存可用量',
  `in_transit_qty` decimal(18,4) DEFAULT '0.0000' COMMENT '在途采购量',
  `in_production_qty` decimal(18,4) DEFAULT '0.0000' COMMENT '在制量',
  `net_qty` decimal(18,4) DEFAULT '0.0000' COMMENT '净需求',
  `adjusted_qty` decimal(18,4) DEFAULT '0.0000' COMMENT '批量修正后的数量',
  `required_date` date DEFAULT NULL COMMENT '需求日期',
  `source_order_id` bigint DEFAULT NULL COMMENT '来源销售订单ID',
  `source_order_item_id` bigint DEFAULT NULL COMMENT '来源订单行项ID',
  `parent_demand_id` bigint DEFAULT NULL COMMENT '上层需求ID(BOM展开追溯)',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_mrp_run_id` (`mrp_run_id`),
  KEY `idx_material_id` (`material_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='MRP需求明细';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_mrp_demand_order`
--

DROP TABLE IF EXISTS `biz_mrp_demand_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_mrp_demand_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL COMMENT '源销售订单ID',
  `order_no` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '订单号(快照)',
  `customer_id` bigint DEFAULT NULL COMMENT '客户ID',
  `customer_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '客户名称(快照)',
  `delivery_date` date DEFAULT NULL COMMENT '交期',
  `total_amount` decimal(12,2) DEFAULT NULL COMMENT '总金额',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'pending' COMMENT '状态: pending(待运算)/processed(已运算)',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='MRP需求订单池';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_mrp_production_suggestion`
--

DROP TABLE IF EXISTS `biz_mrp_production_suggestion`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_mrp_production_suggestion` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `mrp_run_id` bigint NOT NULL COMMENT '关联MRP运行记录',
  `product_id` bigint NOT NULL COMMENT '产品/半成品ID',
  `suggest_qty` decimal(18,4) NOT NULL COMMENT '建议生产数量',
  `required_date` date DEFAULT NULL COMMENT '需求完成日期',
  `suggest_start_date` date DEFAULT NULL COMMENT '建议开工日期',
  `gross_qty` decimal(18,4) DEFAULT NULL COMMENT '总需求量(毛需求)',
  `max_producible_qty` decimal(18,4) DEFAULT NULL COMMENT '最大可生产数量(受原材料约束)',
  `actual_qty` decimal(18,4) DEFAULT NULL COMMENT '实际生产数量(用户可调整)',
  `is_semi_finished` tinyint DEFAULT '0' COMMENT '是否半成品',
  `semi_handle_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'independent' COMMENT '半成品处理: independent/merged',
  `parent_suggestion_id` bigint DEFAULT NULL COMMENT '父级生产建议ID(半成品追溯)',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'pending' COMMENT '状态: pending/confirmed/transferred/ignored',
  `plan_item_id` bigint DEFAULT NULL COMMENT '转为生产计划行项后的ID',
  `source_summary` text COLLATE utf8mb4_general_ci COMMENT '来源追溯摘要(JSON)',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_mrp_run_id` (`mrp_run_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='MRP生产建议';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_mrp_purchase_suggestion`
--

DROP TABLE IF EXISTS `biz_mrp_purchase_suggestion`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_mrp_purchase_suggestion` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `mrp_run_id` bigint NOT NULL COMMENT '关联MRP运行记录',
  `material_id` bigint NOT NULL COMMENT '原材料ID',
  `suggest_qty` decimal(18,4) NOT NULL COMMENT '建议采购数量',
  `required_date` date DEFAULT NULL COMMENT '需求到货日期',
  `suggest_order_date` date DEFAULT NULL COMMENT '建议下单日期',
  `supplier_id` bigint DEFAULT NULL COMMENT '建议供应商ID',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'pending' COMMENT '状态: pending/confirmed/transferred/ignored',
  `purchase_request_id` bigint DEFAULT NULL COMMENT '转为采购申请后的ID',
  `source_summary` text COLLATE utf8mb4_general_ci COMMENT '来源追溯摘要(JSON)',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_mrp_run_id` (`mrp_run_id`),
  KEY `idx_material_id` (`material_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='MRP采购建议';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_mrp_run`
--

DROP TABLE IF EXISTS `biz_mrp_run`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_mrp_run` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `run_no` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '运行编号',
  `run_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '运行类型: scheduled/manual',
  `run_time` datetime NOT NULL COMMENT '运行时间',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'running' COMMENT '状态: running/completed/failed',
  `order_count` int DEFAULT '0' COMMENT '纳入运算的订单数',
  `result_count` int DEFAULT '0' COMMENT '产出建议总数',
  `error_msg` text COLLATE utf8mb4_general_ci COMMENT '错误信息',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_run_no` (`run_no`),
  KEY `idx_status` (`status`),
  KEY `idx_run_time` (`run_time`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='MRP运行记录';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_order_attachment`
--

DROP TABLE IF EXISTS `biz_order_attachment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_order_attachment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '订单号',
  `original_name` varchar(255) COLLATE utf8mb4_general_ci NOT NULL COMMENT '原始文件名',
  `server_name` varchar(255) COLLATE utf8mb4_general_ci NOT NULL COMMENT '服务器存储文件名',
  `file_path` varchar(500) COLLATE utf8mb4_general_ci NOT NULL COMMENT '磁盘绝对路径',
  `file_url` varchar(500) COLLATE utf8mb4_general_ci NOT NULL COMMENT '访问URL',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小(字节)',
  `file_type` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '文件扩展名',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单附件';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_order_change`
--

DROP TABLE IF EXISTS `biz_order_change`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_order_change` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `change_type` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'qty_increase/qty_decrease/date_change/cancel/product_replace/add_item',
  `change_content` text COLLATE utf8mb4_general_ci COMMENT 'JSON变更前后对比',
  `impact_assessment` text COLLATE utf8mb4_general_ci COMMENT 'AI评估结果JSON',
  `change_reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'pending',
  `approved_by` bigint DEFAULT NULL,
  `approved_at` datetime DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单变更';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_order_field_config`
--

DROP TABLE IF EXISTS `biz_order_field_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_order_field_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `field_key` varchar(50) NOT NULL COMMENT '字段Key（英文，存入ext_fields的JSON key）',
  `field_label` varchar(100) NOT NULL COMMENT '字段显示名称',
  `field_type` varchar(20) NOT NULL DEFAULT 'text' COMMENT '类型：text/number/date/select',
  `field_options` text COMMENT '下拉选项，JSON数组格式，如["选项A","选项B"]',
  `show_in_list` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否在列表中显示为列',
  `show_in_form` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否在新增/编辑表单中显示',
  `required` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否必填',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_field_key` (`field_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='销售订单扩展字段配置';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_order_followup`
--

DROP TABLE IF EXISTS `biz_order_followup`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_order_followup` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `followup_type` varchar(30) DEFAULT NULL COMMENT '跟进类型：phone/visit/online/other',
  `content` varchar(2000) NOT NULL COMMENT '跟进内容',
  `next_followup_date` date DEFAULT NULL COMMENT '下次跟进日期',
  `creator_name` varchar(50) DEFAULT NULL COMMENT '跟进人姓名',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单跟进记录';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_order_invoice`
--

DROP TABLE IF EXISTS `biz_order_invoice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_order_invoice` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `invoice_type` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'vat_normal/vat_special/receipt',
  `invoice_no` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `shipped_qty` decimal(12,4) DEFAULT NULL COMMENT '已发货数量(单行兼容)',
  `unit_price` decimal(12,4) DEFAULT NULL COMMENT '单价(单行兼容)',
  `tax_rate` decimal(5,2) DEFAULT '13.00' COMMENT '税率(%)',
  `items_json` text COLLATE utf8mb4_general_ci COMMENT '多产品明细 JSON',
  `invoice_amount` decimal(12,2) DEFAULT NULL,
  `tax_amount` decimal(12,2) DEFAULT NULL,
  `invoice_date` date DEFAULT NULL,
  `attachment_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'pending',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单发票';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_order_log`
--

DROP TABLE IF EXISTS `biz_order_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_order_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `operation` varchar(50) NOT NULL COMMENT '操作类型',
  `operation_desc` varchar(500) DEFAULT NULL COMMENT '操作描述',
  `operator_name` varchar(50) DEFAULT NULL COMMENT '操作人姓名',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单操作日志';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_outsource_delivery`
--

DROP TABLE IF EXISTS `biz_outsource_delivery`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_outsource_delivery` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `delivery_no` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'OD+时间戳',
  `outsource_order_id` bigint NOT NULL,
  `delivery_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'send/return',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'pending',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  `delivery_qty` decimal(12,4) DEFAULT NULL COMMENT '发料数量(虚拟半成品发料时使用)',
  PRIMARY KEY (`id`),
  KEY `idx_order` (`outsource_order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='外协发料单';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_outsource_delivery_item`
--

DROP TABLE IF EXISTS `biz_outsource_delivery_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_outsource_delivery_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `delivery_id` bigint NOT NULL,
  `material_id` bigint NOT NULL,
  `should_qty` decimal(12,4) DEFAULT NULL,
  `actual_qty` decimal(12,4) DEFAULT NULL,
  `batch_no` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `warehouse_id` bigint DEFAULT NULL,
  `remark` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_delivery` (`delivery_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='发料明细';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_outsource_order`
--

DROP TABLE IF EXISTS `biz_outsource_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_outsource_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'OO+时间戳',
  `supplier_id` bigint DEFAULT NULL COMMENT '供应商ID(草稿可为空)',
  `product_id` bigint NOT NULL,
  `step_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `total_qty` decimal(12,4) NOT NULL,
  `unit_price` decimal(10,2) DEFAULT NULL COMMENT '含税单价',
  `total_amount` decimal(12,2) DEFAULT NULL COMMENT '含税总金额(价税合计) = 数量 × 含税单价',
  `delivery_date` date DEFAULT NULL,
  `delivered_qty` decimal(12,4) DEFAULT '0.0000',
  `qualified_qty` decimal(12,4) DEFAULT '0.0000',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'draft' COMMENT 'draft/pending_approval/confirmed/processing/completed/cancelled',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  `tax_rate` decimal(5,2) DEFAULT '0.00' COMMENT '税率%',
  `tax_amount` decimal(12,2) DEFAULT '0.00' COMMENT '税额',
  `work_order_id` bigint DEFAULT NULL COMMENT '关联工单ID',
  `task_id` bigint DEFAULT NULL COMMENT '关联工序任务ID',
  `request_id` bigint DEFAULT NULL COMMENT '关联外协申请ID',
  `virtual_material_id` bigint DEFAULT NULL COMMENT '虚拟半成品物料ID',
  `receipt_material_id` bigint DEFAULT NULL COMMENT '收料物料ID(外协工序输出虚拟半成品)',
  `delivery_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '发货数量',
  `order_id` bigint DEFAULT NULL COMMENT '关联销售订单ID(整单外协)',
  `order_item_id` bigint DEFAULT NULL COMMENT '关联销售订单行ID(整单外协)',
  `approved_by` bigint DEFAULT NULL COMMENT '审批人ID',
  `approved_at` datetime DEFAULT NULL COMMENT '审批时间',
  `approve_remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '审批备注',
  `delivery_task_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '发料任务数',
  `delivery_out_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '发料出库数',
  `receipt_task_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '收货任务数',
  `receipt_in_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '收货入库数',
  `receipt_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '已生成收料单数量',
  `process_requirement` text COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '工艺要求',
  `drawing_urls` text COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '图纸URLs',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_supplier` (`supplier_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='外协加工单';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_outsource_order_request`
--

DROP TABLE IF EXISTS `biz_outsource_order_request`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_outsource_order_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `outsource_order_id` bigint NOT NULL,
  `outsource_request_id` bigint NOT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='加工单-申请单关联';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_outsource_receipt`
--

DROP TABLE IF EXISTS `biz_outsource_receipt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_outsource_receipt` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `receipt_no` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'OR+时间戳',
  `outsource_order_id` bigint NOT NULL,
  `received_qty` decimal(12,4) NOT NULL,
  `batch_no` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '批次号',
  `received_date` date DEFAULT NULL,
  `receiver_id` bigint DEFAULT NULL,
  `qc_task_id` bigint DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'pending_receive' COMMENT '收料状态: pending_receive/receiving/received/closed',
  `qc_status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'none' COMMENT '质检状态: none/pending_qc/qc_done',
  `qualified_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '合格数',
  `defect_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '不合格数',
  `inv_task_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '库存任务数',
  `stock_in_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '入库数',
  `warehouse_id` bigint DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_order` (`outsource_order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='外协收料单';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_outsource_request`
--

DROP TABLE IF EXISTS `biz_outsource_request`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_outsource_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `request_no` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'OR+时间戳',
  `work_order_id` bigint NOT NULL,
  `task_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `step_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `quantity` decimal(12,4) NOT NULL,
  `process_requirement` text COLLATE utf8mb4_general_ci,
  `drawing_urls` text COLLATE utf8mb4_general_ci,
  `required_date` date DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'pending' COMMENT 'pending/approved/rejected/ordered/completed/cancelled',
  `approved_by` bigint DEFAULT NULL,
  `approved_at` datetime DEFAULT NULL,
  `approve_remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  `max_qty` decimal(12,4) DEFAULT NULL COMMENT '最大可申请数量(=工单计划数)',
  `prev_qualified_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '前道工序累计合格数',
  `requested_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '已转为加工单数量(累计)',
  `virtual_material_id` bigint DEFAULT NULL COMMENT '虚拟半成品物料ID',
  `delivery_warehouse_id` bigint DEFAULT '999' COMMENT '发料仓库ID',
  `receipt_material_id` bigint DEFAULT NULL COMMENT '收料物料ID(外协工序输出虚拟半成品)',
  `is_auto_created` tinyint(1) DEFAULT '0' COMMENT '是否报工自动创建',
  `order_id` bigint DEFAULT NULL COMMENT '关联销售订单ID（整单外协）',
  `order_item_id` bigint DEFAULT NULL COMMENT '关联销售订单行ID（整单外协）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_request_no` (`request_no`),
  KEY `idx_work_order` (`work_order_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='外协申请单';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_payable`
--

DROP TABLE IF EXISTS `biz_payable`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_payable` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `payable_no` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `supplier_id` bigint NOT NULL,
  `amount` decimal(12,2) NOT NULL,
  `paid_amount` decimal(12,2) DEFAULT '0.00',
  `unpaid_amount` decimal(12,2) DEFAULT NULL,
  `due_date` date DEFAULT NULL,
  `source_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `source_id` bigint DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'pending',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payable_no` (`payable_no`),
  KEY `idx_supplier` (`supplier_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='应付账款';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_payment_record`
--

DROP TABLE IF EXISTS `biz_payment_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_payment_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `payment_no` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `related_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `related_id` bigint NOT NULL,
  `amount` decimal(12,2) NOT NULL,
  `payment_date` date NOT NULL,
  `payment_method` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `transaction_no` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `attachment_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_related` (`related_type`,`related_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='收付款记录';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_plan_item_material`
--

DROP TABLE IF EXISTS `biz_plan_item_material`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_plan_item_material` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `plan_id` bigint NOT NULL COMMENT '生产计划ID',
  `plan_item_id` bigint NOT NULL COMMENT '计划行项ID',
  `material_id` bigint NOT NULL COMMENT '物料ID',
  `warehouse_id` bigint DEFAULT NULL COMMENT '仓库ID',
  `required_qty` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '需求量',
  `locked_qty` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '已锁定量',
  `shortage_qty` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '缺料量',
  `released_to_wo_qty` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '已转工单量',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_plan_item_material` (`plan_item_id`,`material_id`),
  KEY `idx_plan_id` (`plan_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='计划行项物料需求';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_process`
--

DROP TABLE IF EXISTS `biz_process`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_process` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `process_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '工序名称',
  `standard_time` int DEFAULT NULL COMMENT '标准工时(分钟)',
  `piece_price` decimal(10,2) DEFAULT NULL COMMENT '计件单价',
  `equipment_ids` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'JSON数组',
  `team_id` bigint DEFAULT NULL COMMENT '绑定班组ID(旧字段,兼容)',
  `bind_type` varchar(20) DEFAULT 'none' COMMENT '绑定类型: none/team/employee',
  `bind_ids` varchar(1000) DEFAULT NULL COMMENT '绑定ID列表(JSON数组)',
  `sop_description` text COLLATE utf8mb4_general_ci,
  `sop_attachment_urls` text COLLATE utf8mb4_general_ci COMMENT 'JSON数组',
  `is_semi_stock` tinyint(1) DEFAULT '0' COMMENT '完成后入半成品库',
  `is_outsourceable` tinyint(1) DEFAULT '0' COMMENT '可外协',
  `qc_template_id` bigint DEFAULT NULL COMMENT '质检模板ID',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工序库';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_process_route`
--

DROP TABLE IF EXISTS `biz_process_route`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_process_route` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` bigint DEFAULT NULL COMMENT '关联产品ID（可为空，表示通用工艺路线）',
  `version_no` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `route_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '工艺路线名称',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'active',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_product` (`product_id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工艺路线';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_process_route_step`
--

DROP TABLE IF EXISTS `biz_process_route_step`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_process_route_step` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `route_id` bigint NOT NULL COMMENT '工艺路线ID',
  `process_id` bigint NOT NULL COMMENT '工序库ID',
  `step_no` int DEFAULT '0' COMMENT '工序序号',
  `step_group` int DEFAULT '1' COMMENT '工序组(同组并行执行)',
  `standard_time` int DEFAULT NULL COMMENT '覆盖标准工时(NULL=继承工序库)',
  `piece_price` decimal(10,2) DEFAULT NULL COMMENT '覆盖计件单价(NULL=继承工序库)',
  `team_id` bigint DEFAULT NULL COMMENT '覆盖绑定班组(NULL=继承工序库)',
  `sop_description` text COLLATE utf8mb4_general_ci COMMENT '覆盖SOP描述(NULL=继承工序库)',
  `is_semi_stock` tinyint(1) DEFAULT NULL COMMENT '覆盖入半成品库(NULL=继承工序库)',
  `is_outsourceable` tinyint(1) DEFAULT NULL COMMENT '覆盖可外协(NULL=继承工序库)',
  `qc_template_id` bigint DEFAULT NULL COMMENT '覆盖质检模板ID(NULL=继承工序库)',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_route` (`route_id`),
  KEY `idx_process` (`process_id`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工艺路线工序关联';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_process_step_backup`
--

DROP TABLE IF EXISTS `biz_process_step_backup`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_process_step_backup` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `route_id` bigint DEFAULT NULL COMMENT '工艺路线ID(NULL表示工序库独立工序)',
  `step_no` int DEFAULT '0',
  `step_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `standard_time` int DEFAULT NULL COMMENT '标准工时(分钟)',
  `piece_price` decimal(10,2) DEFAULT NULL COMMENT '计件单价',
  `equipment_ids` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'JSON数组',
  `team_id` bigint DEFAULT NULL COMMENT '绑定班组ID',
  `sop_description` text COLLATE utf8mb4_general_ci,
  `sop_attachment_urls` text COLLATE utf8mb4_general_ci COMMENT 'JSON数组',
  `is_semi_stock` tinyint(1) DEFAULT '0' COMMENT '完成后入半成品库',
  `is_outsourceable` tinyint(1) DEFAULT '0' COMMENT '可外协',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  `qc_template_id` bigint DEFAULT NULL COMMENT '质检模板ID',
  `step_group` int DEFAULT '1' COMMENT '工序组(同组并行执行)',
  PRIMARY KEY (`id`),
  KEY `idx_route` (`route_id`)
) ENGINE=InnoDB AUTO_INCREMENT=48 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工艺工序';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_product`
--

DROP TABLE IF EXISTS `biz_product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_product` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `product_no` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'P+时间戳',
  `product_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `spec` text COLLATE utf8mb4_general_ci COMMENT '规格,支持JSON多规格',
  `unit` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `unit_config` text COLLATE utf8mb4_general_ci COMMENT 'JSON多单位换算配置',
  `category` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `category_id` bigint DEFAULT NULL COMMENT '物料类别ID',
  `production_mode` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'MTO' COMMENT 'MTS/MTO/MIXED',
  `target_stock` decimal(12,4) DEFAULT NULL,
  `reorder_point` decimal(12,4) DEFAULT NULL,
  `reorder_qty` decimal(12,4) DEFAULT NULL,
  `max_stock` decimal(12,4) DEFAULT NULL COMMENT '最大库存',
  `min_stock` decimal(12,4) DEFAULT NULL COMMENT '最小库存',
  `standard_cost` decimal(12,2) DEFAULT NULL,
  `image_urls` text COLLATE utf8mb4_general_ci COMMENT 'JSON数组',
  `drawing_urls` text COLLATE utf8mb4_general_ci COMMENT 'JSON数组',
  `has_bom` tinyint(1) DEFAULT '0',
  `has_process` tinyint(1) DEFAULT '0',
  `allow_stock_lock` tinyint(1) DEFAULT '1' COMMENT '是否允许锁库',
  `material_attributes` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '物料属性:purchase,production,sales',
  `production_lead_time` int DEFAULT '0' COMMENT '生产提前期(天)',
  `min_production_qty` decimal(18,4) DEFAULT NULL COMMENT '最小生产批量',
  `production_qty_multiple` decimal(18,4) DEFAULT NULL COMMENT '生产批量倍数',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'active',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_no` (`product_no`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='产品档案';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_production_plan`
--

DROP TABLE IF EXISTS `biz_production_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_production_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `plan_no` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '计划编号',
  `plan_name` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '计划名称',
  `plan_date_from` date DEFAULT NULL COMMENT '计划开始日期',
  `plan_date_to` date DEFAULT NULL COMMENT '计划结束日期',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'draft' COMMENT '状态: draft/confirmed/in_progress/completed/cancelled',
  `create_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'mrp' COMMENT '创建方式: mrp/manual',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ext_fields` text COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '扩展字段(JSON)',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_plan_no` (`plan_no`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='生产计划';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_production_plan_attachment`
--

DROP TABLE IF EXISTS `biz_production_plan_attachment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_production_plan_attachment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `plan_id` bigint NOT NULL COMMENT '生产计划ID',
  `plan_no` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '计划编号',
  `original_name` varchar(255) COLLATE utf8mb4_general_ci NOT NULL COMMENT '原始文件名',
  `server_name` varchar(255) COLLATE utf8mb4_general_ci NOT NULL COMMENT '服务器文件名',
  `file_path` varchar(500) COLLATE utf8mb4_general_ci NOT NULL COMMENT '文件绝对路径',
  `file_url` varchar(500) COLLATE utf8mb4_general_ci NOT NULL COMMENT '文件访问URL',
  `file_size` bigint DEFAULT '0' COMMENT '文件大小(字节)',
  `file_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '文件类型',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_plan_id` (`plan_id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='生产计划附件';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_production_plan_item`
--

DROP TABLE IF EXISTS `biz_production_plan_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_production_plan_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `plan_id` bigint NOT NULL COMMENT '关联生产计划',
  `product_id` bigint NOT NULL COMMENT '产品/半成品ID',
  `plan_qty` decimal(18,4) NOT NULL COMMENT '计划生产数量',
  `released_qty` decimal(18,4) DEFAULT '0.0000' COMMENT '已下达工单的数量',
  `completed_qty` decimal(18,4) DEFAULT '0.0000' COMMENT '已完成的数量',
  `plan_start_date` date DEFAULT NULL COMMENT '计划开工日期',
  `plan_end_date` date DEFAULT NULL COMMENT '计划完工日期',
  `priority` int DEFAULT '0' COMMENT '优先级: 0低/1中/2高',
  `source_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'mrp' COMMENT '来源: mrp/manual/safety_stock',
  `mrp_suggestion_id` bigint DEFAULT NULL COMMENT '关联MRP生产建议ID',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'pending' COMMENT '状态: pending/partial_released/fully_released/completed',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `substitute_selections` text COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '主计划替代料选择 JSON {materialId:substituteId}',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_plan_id` (`plan_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='生产计划行项';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_production_plan_item_step`
--

DROP TABLE IF EXISTS `biz_production_plan_item_step`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_production_plan_item_step` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `plan_item_id` bigint NOT NULL COMMENT '关联计划行项ID',
  `step_id` bigint NOT NULL COMMENT '工序路线步骤ID',
  `step_no` int NOT NULL COMMENT '工序序号',
  `step_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '工序名称',
  `team_id` bigint DEFAULT NULL COMMENT '指派班组ID',
  `team_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '班组名称(冗余)',
  `assignee_id` bigint DEFAULT NULL COMMENT '指派人员ID',
  `assignee_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '人员姓名(冗余)',
  `need_qc` tinyint DEFAULT '0' COMMENT '是否需要质检',
  `standard_time` int DEFAULT '0' COMMENT '标准工时(分钟,冗余)',
  `piece_price` decimal(10,4) DEFAULT '0.0000' COMMENT '计件单价(冗余)',
  `handle_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'self' COMMENT '处理方式: self(自制)/outsource(外协)',
  `supplier_id` bigint DEFAULT NULL COMMENT '外协供应商ID',
  `supplier_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '外协供应商名称(冗余)',
  `wage_ratio` decimal(6,4) DEFAULT '1.0000' COMMENT '工资比例(默认1;三机模式下作为基础比例)',
  `assigned_member_ids` text COLLATE utf8mb4_general_ci COMMENT '班组指派时允许报工的班组成员userId JSON数组;空/null=全部成员',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted` int DEFAULT '0',
  `plan_start_time` datetime DEFAULT NULL COMMENT '工序计划开始时间',
  `plan_end_time` datetime DEFAULT NULL COMMENT '工序计划结束时间',
  `qc_template_id` bigint DEFAULT NULL COMMENT '质检模板ID',
  `qc_requirements` text COLLATE utf8mb4_general_ci COMMENT '质检要求JSON',
  `route_id` bigint DEFAULT NULL COMMENT '工艺路线ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_item_step` (`plan_item_id`,`step_id`),
  KEY `idx_plan_item_id` (`plan_item_id`)
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='生产计划行项-工序指派';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_production_plan_order_rel`
--

DROP TABLE IF EXISTS `biz_production_plan_order_rel`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_production_plan_order_rel` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `plan_item_id` bigint NOT NULL COMMENT '计划行项ID',
  `order_id` bigint NOT NULL COMMENT '销售订单ID',
  `order_item_id` bigint NOT NULL COMMENT '销售订单行项ID',
  `allocated_qty` decimal(18,4) NOT NULL COMMENT '分配给该订单的数量',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_plan_item_id` (`plan_item_id`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='生产计划-订单追溯关系';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_purchase_order`
--

DROP TABLE IF EXISTS `biz_purchase_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_purchase_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `po_no` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'PO+时间戳',
  `supplier_id` bigint DEFAULT NULL COMMENT '供应商ID(草稿允许为空,确认时必填)',
  `contact_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `total_amount` decimal(12,2) DEFAULT '0.00',
  `delivery_date` date DEFAULT NULL,
  `payment_terms` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'draft',
  `inv_task_gen_status` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '库存任务生成状态(partial_generated/fully_generated)',
  `inv_task_process_status` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '库存任务处理状态(partial_processed/fully_processed/returned)',
  `inv_return_reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '库存退回原因',
  `approved_by` bigint DEFAULT NULL COMMENT '审批人ID',
  `approved_at` datetime DEFAULT NULL COMMENT '审批时间',
  `approve_remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '审批意见',
  `paid_amount` decimal(12,2) DEFAULT '0.00' COMMENT '已付款金额',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `attachment_urls` text COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '附件列表 JSON数组',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_po_no` (`po_no`),
  KEY `idx_supplier` (`supplier_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='采购订单';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_purchase_order_item`
--

DROP TABLE IF EXISTS `biz_purchase_order_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_purchase_order_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `purchase_order_id` bigint NOT NULL,
  `material_id` bigint NOT NULL,
  `quantity` decimal(12,4) NOT NULL,
  `unit_price` decimal(12,2) DEFAULT NULL COMMENT '含税单价',
  `tax_rate` decimal(5,2) DEFAULT '13.00' COMMENT '税率(%)',
  `amount` decimal(12,2) DEFAULT NULL COMMENT '含税总价 = 数量 × 含税单价',
  `inv_task_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '库存任务数量',
  `stock_in_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '入库数量（相关库存任务的处理数量）',
  `return_initiated_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '发起退货数量',
  `return_out_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '已退货出库数量',
  `qualified_qty` decimal(12,4) DEFAULT '0.0000',
  `receipt_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '已生成收货单数量',
  `request_id` bigint DEFAULT NULL,
  `remark` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_order` (`purchase_order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='采购订单明细';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_purchase_receipt`
--

DROP TABLE IF EXISTS `biz_purchase_receipt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_purchase_receipt` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `receipt_no` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'PR+时间戳',
  `receipt_group_id` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '收料批次组ID(同一次批量收料的多条receipt共享一个group, 用于列表折叠/打印对账单)',
  `purchase_order_id` bigint NOT NULL,
  `order_item_id` bigint NOT NULL,
  `material_id` bigint NOT NULL,
  `expected_qty` decimal(12,4) DEFAULT NULL,
  `actual_qty` decimal(12,4) NOT NULL,
  `batch_no` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `received_date` date DEFAULT NULL,
  `receiver_id` bigint DEFAULT NULL,
  `warehouse_id` bigint DEFAULT NULL,
  `qc_task_id` bigint DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'pending_receive' COMMENT '收货状态: pending_receive/receiving/received/closed',
  `qc_status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'none' COMMENT '质检状态: none/pending_qc/qc_done',
  `qualified_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '合格数',
  `defect_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '不合格数',
  `inv_task_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '库存任务数',
  `stock_in_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '入库数',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_order` (`purchase_order_id`),
  KEY `idx_receipt_group` (`receipt_group_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='采购收料单';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_purchase_return`
--

DROP TABLE IF EXISTS `biz_purchase_return`;
CREATE TABLE `biz_purchase_return` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `return_no` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'PRT+日期+序号',
  `purchase_order_id` bigint DEFAULT NULL COMMENT '关联采购订单ID',
  `related_order_no` varchar(80) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '关联单据号',
  `supplier_id` bigint NOT NULL COMMENT '供应商ID',
  `return_reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '退货原因',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'draft' COMMENT 'draft/pending_approval/confirmed/rejected/closed',
  `inv_task_id` bigint DEFAULT NULL COMMENT '库存任务ID（只能生成一次）',
  `approved_by` bigint DEFAULT NULL,
  `approved_at` datetime DEFAULT NULL,
  `approve_remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_return_no` (`return_no`),
  KEY `idx_supplier` (`supplier_id`),
  KEY `idx_purchase_order` (`purchase_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='采购退货单';

-- Table structure for table `biz_purchase_return_item`
--

DROP TABLE IF EXISTS `biz_purchase_return_item`;
CREATE TABLE `biz_purchase_return_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `return_id` bigint NOT NULL COMMENT '退货单ID',
  `material_id` bigint NOT NULL COMMENT '物料ID',
  `quantity` decimal(12,4) NOT NULL COMMENT '退货数量',
  `unit_price` decimal(12,2) DEFAULT NULL COMMENT '单价',
  `remark` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_return_id` (`return_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='采购退货单明细';

-- Table structure for table `biz_purchase_request`
--

DROP TABLE IF EXISTS `biz_purchase_request`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_purchase_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `request_no` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'PR+时间戳',
  `material_id` bigint NOT NULL,
  `material_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `spec` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `unit` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `quantity` decimal(12,4) NOT NULL,
  `suggested_supplier_id` bigint DEFAULT NULL,
  `expected_date` date DEFAULT NULL,
  `source_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'manual/safety_trigger/ai_suggest',
  `source_no` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '来源单号(关联销售订单/生产计划/工单等单号, 选填)',
  `purpose` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '用途说明(例: 生产XX产品)',
  `estimated_price` decimal(12,2) DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'pending',
  `approved_by` bigint DEFAULT NULL,
  `approved_at` datetime DEFAULT NULL,
  `approve_remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_request_no` (`request_no`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='采购申请';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_qc_item`
--

DROP TABLE IF EXISTS `biz_qc_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_qc_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `item_code` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `item_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `category` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'dimension/appearance/performance/chemical/physical',
  `item_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'quantitative/qualitative',
  `standard_value` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `tolerance_min` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `tolerance_max` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `unit` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `method` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `is_critical` tinyint(1) DEFAULT '0',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'active',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_item_code` (`item_code`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='质检项目库';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_qc_result`
--

DROP TABLE IF EXISTS `biz_qc_result`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_qc_result` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `qc_task_id` bigint NOT NULL,
  `template_item_id` bigint DEFAULT NULL,
  `item_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `standard_value` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `tolerance_min` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `tolerance_max` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `unit` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '单位',
  `method` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '检验方法/质检要求',
  `item_type` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '检验类型',
  `default_value` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '默认值',
  `is_required` tinyint DEFAULT '0' COMMENT '是否必填: 0=否 1=是',
  `is_critical` tinyint DEFAULT NULL COMMENT '是否关键项 0=否 1=是',
  `actual_value` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `is_pass` tinyint(1) DEFAULT NULL,
  `photo_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `remark` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_task` (`qc_task_id`)
) ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='检验结果明细';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_qc_task`
--

DROP TABLE IF EXISTS `biz_qc_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_qc_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_no` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `qc_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `template_id` bigint DEFAULT NULL,
  `product_id` bigint DEFAULT NULL,
  `material_id` bigint DEFAULT NULL,
  `batch_no` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `related_order_type` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `related_order_id` bigint DEFAULT NULL,
  `work_order_id` bigint DEFAULT NULL,
  `plan_qty` decimal(12,4) DEFAULT NULL,
  `sample_qty` decimal(12,4) DEFAULT NULL,
  `pass_qty` decimal(12,4) DEFAULT '0.0000',
  `fail_qty` decimal(12,4) DEFAULT '0.0000',
  `result` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'pending',
  `inspector_id` bigint DEFAULT NULL,
  `inspected_at` datetime DEFAULT NULL,
  `conclusion` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `photo_urls` text COLLATE utf8mb4_general_ci,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  `task_id` bigint DEFAULT NULL COMMENT '关联工序任务ID(工序质检时)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_no` (`task_no`),
  KEY `idx_type` (`qc_type`),
  KEY `idx_result` (`result`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='检验任务';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_qc_template`
--

DROP TABLE IF EXISTS `biz_qc_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_qc_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `template_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `template_code` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `apply_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'incoming/process/final/outsource/all',
  `apply_product_id` bigint DEFAULT NULL,
  `apply_material_id` bigint DEFAULT NULL,
  `scheme_id` bigint DEFAULT NULL COMMENT '关联质检方案ID',
  `sample_mode` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'full',
  `sample_rate` decimal(5,2) DEFAULT NULL,
  `aql_level` varchar(10) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `is_default` tinyint(1) DEFAULT '0',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'active',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`template_code`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='检验模板';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_qc_template_item`
--

DROP TABLE IF EXISTS `biz_qc_template_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_qc_template_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `template_id` bigint NOT NULL,
  `item_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `item_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `standard_value` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `tolerance_min` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `tolerance_max` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `unit` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `method` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `default_value` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '默认值(计量型/文本型使用)',
  `is_required` tinyint DEFAULT '0' COMMENT '是否必填: 0=否 1=是',
  `is_critical` tinyint(1) DEFAULT '0',
  `sort_order` int DEFAULT '0',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_template` (`template_id`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='检验模板项目';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_qc_scheme`
--

DROP TABLE IF EXISTS `biz_qc_scheme`;
CREATE TABLE `biz_qc_scheme` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `scheme_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '方案名称',
  `scheme_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'sampling(抽检)/full(全检)',
  `sampling_mode` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'first_piece/last_piece/custom_qty/ratio',
  `sampling_qty` int DEFAULT NULL COMMENT '抽检数量',
  `sampling_ratio` decimal(5,2) DEFAULT NULL COMMENT '抽检比例(%)',
  `ratio_round_up` tinyint(1) DEFAULT '1' COMMENT '1=向上取整',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'active',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='质检方案';

-- Table structure for table `biz_qc_disposal`
--

DROP TABLE IF EXISTS `biz_qc_disposal`;
CREATE TABLE `biz_qc_disposal` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `disposal_no` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '处理单号',
  `qc_task_id` bigint NOT NULL COMMENT '关联质检任务ID',
  `qc_record_id` bigint DEFAULT NULL COMMENT '关联质检记录ID',
  `work_order_id` bigint DEFAULT NULL COMMENT '关联生产工单ID',
  `process_task_id` bigint DEFAULT NULL COMMENT '关联工序任务ID',
  `material_id` bigint DEFAULT NULL COMMENT '物料ID',
  `step_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '工序名称',
  `quantity` decimal(12,4) NOT NULL COMMENT '不合格数量',
  `disposal_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'scrap/concession/rework',
  `disposal_reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'pending' COMMENT 'pending/processed/closed',
  `rework_order_id` bigint DEFAULT NULL,
  `inv_task_id` bigint DEFAULT NULL,
  `processed_by` bigint DEFAULT NULL,
  `processed_at` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='质检处理单';

-- Table structure for table `biz_qc_record`
--

DROP TABLE IF EXISTS `biz_qc_record`;
CREATE TABLE `biz_qc_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `qc_task_id` bigint NOT NULL COMMENT '质检任务ID',
  `record_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qualified/unqualified',
  `quantity` decimal(12,4) NOT NULL COMMENT '数量',
  `batch_no` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `reporter_id` bigint DEFAULT NULL COMMENT '报工人ID',
  `equipment` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '设备',
  `defect_items` text COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '不合格检验项JSON',
  `photo_urls` text COLLATE utf8mb4_general_ci DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_qc_task` (`qc_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='质检结果记录';

-- Table structure for table `biz_rework_order`
--

DROP TABLE IF EXISTS `biz_rework_order`;
CREATE TABLE `biz_rework_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `rework_no` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '返修单号',
  `qc_task_id` bigint DEFAULT NULL,
  `qc_disposal_id` bigint DEFAULT NULL,
  `work_order_id` bigint NOT NULL COMMENT '关联生产工单ID',
  `process_task_id` bigint DEFAULT NULL,
  `product_id` bigint DEFAULT NULL,
  `material_id` bigint DEFAULT NULL,
  `quantity` decimal(12,4) NOT NULL COMMENT '返修数量',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'pending' COMMENT 'pending/in_progress/completed',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='返修工单';

-- Table structure for table `biz_rework_order_step`
--

DROP TABLE IF EXISTS `biz_rework_order_step`;
CREATE TABLE `biz_rework_order_step` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `rework_order_id` bigint NOT NULL,
  `process_id` bigint DEFAULT NULL COMMENT '工序ID',
  `process_task_id` bigint DEFAULT NULL COMMENT '关联原工单工序任务ID，用于继承质检模板',
  `step_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `assigned_user_id` bigint DEFAULT NULL COMMENT '指定报工人',
  `sort_order` int DEFAULT '0',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'pending' COMMENT 'pending/qc_pending(待质检)/completed',
  `qc_task_id` bigint DEFAULT NULL COMMENT '关联本工序完工质检任务ID(BizQcTask), 为空表示工序无需质检',
  `completed_at` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_rework_order` (`rework_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='返修工单工序';

-- Table structure for table `biz_qr_template`
--

DROP TABLE IF EXISTS `biz_qr_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_qr_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `template_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `label_width` int DEFAULT NULL,
  `label_height` int DEFAULT NULL,
  `qr_fields` text COLLATE utf8mb4_general_ci,
  `print_fields` text COLLATE utf8mb4_general_ci,
  `is_default` tinyint(1) DEFAULT '0',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='二维码标签模板';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_receivable`
--

DROP TABLE IF EXISTS `biz_receivable`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_receivable` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `receivable_no` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `order_id` bigint NOT NULL,
  `customer_id` bigint NOT NULL,
  `amount` decimal(12,2) NOT NULL,
  `received_amount` decimal(12,2) DEFAULT '0.00',
  `unreceived_amount` decimal(12,2) DEFAULT NULL,
  `due_date` date DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'pending',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_receivable_no` (`receivable_no`),
  KEY `idx_customer` (`customer_id`),
  KEY `idx_order` (`order_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='应收账款';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_return_order`
--

DROP TABLE IF EXISTS `biz_return_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_return_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `return_no` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT '退货单号',
  `order_id` bigint NOT NULL COMMENT '关联销售订单ID',
  `order_no` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '销售订单号(冗余)',
  `customer_id` bigint DEFAULT NULL COMMENT '客户ID',
  `customer_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '客户名称(冗余)',
  `return_date` date DEFAULT NULL COMMENT '申请退货日期',
  `return_reason` text COLLATE utf8mb4_general_ci COMMENT '退货原因',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'pending' COMMENT 'pending=待审批,approved=已审批,received=已收货,completed=已完成,rejected=已驳回',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `return_no` (`return_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='销售退货单';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_return_order_item`
--

DROP TABLE IF EXISTS `biz_return_order_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_return_order_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `return_id` bigint NOT NULL COMMENT '退货单ID',
  `order_item_id` bigint DEFAULT NULL COMMENT '销售订单明细ID',
  `product_id` bigint NOT NULL COMMENT '产品ID',
  `product_name` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '产品名称(冗余)',
  `spec` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '规格(冗余)',
  `unit` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '单位(冗余)',
  `return_qty` decimal(12,4) NOT NULL COMMENT '申请退货数量',
  `received_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '实际收货数量',
  `warehouse_id` bigint DEFAULT NULL COMMENT '退货入库仓库',
  `unit_price` decimal(12,4) DEFAULT NULL COMMENT '含税退货单价',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_return_id` (`return_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='销售退货单明细';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_sales_order`
--

DROP TABLE IF EXISTS `biz_sales_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_sales_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'SO+时间戳',
  `customer_id` bigint NOT NULL,
  `contact_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `shipping_address` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `settlement_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `total_amount` decimal(12,2) DEFAULT '0.00',
  `delivery_date` date DEFAULT NULL COMMENT '交货日期(订单级)',
  `original_delivery_date` date DEFAULT NULL COMMENT '原始交货日期(用于延期判定)',
  `payment_amount` decimal(12,2) DEFAULT '0.00' COMMENT '已回款金额',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'draft' COMMENT 'draft/pending_approval/confirmed/in_production/partial_shipped/shipped/completed/settled/voided/cancelled',
  `salesman_id` bigint DEFAULT NULL,
  `approved_by` bigint DEFAULT NULL,
  `approved_at` datetime DEFAULT NULL,
  `approve_remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `cancel_reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `attachment_urls` text COLLATE utf8mb4_general_ci,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ext_fields` text COLLATE utf8mb4_general_ci COMMENT '扩展字段JSON（由biz_order_field_config定义）',
  `change_unlocked` tinyint(1) DEFAULT '0' COMMENT '变更解锁编辑: 0=锁定 1=已解锁可编辑',
  `outsource_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '外协类型',
  `master_plan_added` tinyint(1) DEFAULT '0' COMMENT '是否已加入订单主计划: 0=未加入 1=已加入',
  `master_plan_added_at` datetime DEFAULT NULL COMMENT '加入主计划时间',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_customer` (`customer_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='销售订单';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_sales_order_item`
--

DROP TABLE IF EXISTS `biz_sales_order_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_sales_order_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `quantity` decimal(12,4) NOT NULL,
  `unit_price` decimal(12,2) NOT NULL COMMENT '含税单价',
  `amount` decimal(12,2) NOT NULL COMMENT '含税总价 = 数量 × 含税单价',
  `tax_rate` decimal(5,2) DEFAULT '13.00' COMMENT '税率%',
  `delivery_date` date DEFAULT NULL COMMENT '交货日期(兼容旧数据)',
  `produced_qty` decimal(12,4) DEFAULT '0.0000',
  `shipped_qty` decimal(12,4) DEFAULT '0.0000',
  `warehouse_in_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '入库数量',
  `remark` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  `task_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '已申请任务数',
  `ext_fields` text COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '行级扩展字段JSON(由 biz_ext_field_config module=sales_order_item 定义)',
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单明细';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_scheduling`
--

DROP TABLE IF EXISTS `biz_scheduling`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_scheduling` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` bigint NOT NULL COMMENT '工序任务ID',
  `work_order_id` bigint NOT NULL,
  `worker_id` bigint DEFAULT NULL,
  `team_id` bigint DEFAULT NULL,
  `start_time` datetime DEFAULT NULL,
  `end_time` datetime DEFAULT NULL,
  `duration_minutes` int DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'scheduled',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_task` (`task_id`),
  KEY `idx_worker` (`worker_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='排产记录';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_shipment_record`
--

DROP TABLE IF EXISTS `biz_shipment_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_shipment_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `order_item_id` bigint DEFAULT NULL,
  `product_id` bigint NOT NULL,
  `shipped_qty` decimal(12,4) NOT NULL,
  `shipment_no` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `logistics_company` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `tracking_no` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `shipped_at` datetime DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'shipped',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='发货记录';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_supplier`
--

DROP TABLE IF EXISTS `biz_supplier`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_supplier` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `supplier_no` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'S+时间戳',
  `supplier_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `supplier_type` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'material/outsource/both',
  `supplier_category_id` bigint DEFAULT NULL COMMENT '供应商类别ID',
  `contact_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `phone` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `address` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `settlement_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'monthly_30',
  `bank_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `bank_account` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `tax_no` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '税号',
  `invoice_type` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '发票类型: special/normal',
  `processing_ability` text COLLATE utf8mb4_general_ci COMMENT '加工能力描述',
  `score` decimal(3,1) DEFAULT '0.0',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'active',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_supplier_no` (`supplier_no`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='供应商/外协商';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_supplier_category`
--

DROP TABLE IF EXISTS `biz_supplier_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_supplier_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父级ID，0为根节点',
  `category_name` varchar(50) NOT NULL COMMENT '类别名称',
  `category_code` varchar(30) DEFAULT NULL COMMENT '类别编码',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `status` varchar(20) NOT NULL DEFAULT 'active' COMMENT '状态',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商类别';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_team`
--

DROP TABLE IF EXISTS `biz_team`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_team` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `team_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `workshop` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `leader_id` bigint DEFAULT NULL,
  `shift_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'morning/afternoon/night/flexible',
  `effective_date` date DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'active',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='班组';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_team_member`
--

DROP TABLE IF EXISTS `biz_team_member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_team_member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `team_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `weight` decimal(6,4) NOT NULL DEFAULT '0.0000',
  `joined_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_user` (`team_id`,`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='班组成员';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_team_report_detail`
--

DROP TABLE IF EXISTS `biz_team_report_detail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_team_report_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `team_report_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `weight` decimal(6,4) NOT NULL,
  `allocated_wage` decimal(10,2) DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=231 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='班组报工分配';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_team_work_report`
--

DROP TABLE IF EXISTS `biz_team_work_report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_team_work_report` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `report_no` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `task_id` bigint NOT NULL,
  `work_order_id` bigint NOT NULL,
  `team_id` bigint NOT NULL,
  `leader_id` bigint NOT NULL,
  `qualified_qty` decimal(12,4) NOT NULL,
  `defect_qty` decimal(12,4) DEFAULT '0.0000',
  `total_wage` decimal(10,2) DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_task` (`task_id`)
) ENGINE=InnoDB AUTO_INCREMENT=52 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='班组报工主记录';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_unit`
--

DROP TABLE IF EXISTS `biz_unit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_unit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `unit_name` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '单位名称',
  `unit_code` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '单位编码',
  `sort_order` int DEFAULT '0',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'active',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_unit_code` (`unit_code`),
  UNIQUE KEY `uk_unit_name` (`unit_name`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='计量单位';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_wage_monthly`
--

DROP TABLE IF EXISTS `biz_wage_monthly`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_wage_monthly` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `month` varchar(7) COLLATE utf8mb4_general_ci NOT NULL,
  `personal_piece_wage` decimal(10,2) DEFAULT '0.00',
  `team_allocated_wage` decimal(10,2) DEFAULT '0.00',
  `overtime_wage` decimal(10,2) DEFAULT '0.00',
  `deduction` decimal(10,2) DEFAULT '0.00',
  `bonus` decimal(10,2) DEFAULT '0.00',
  `total_wage` decimal(10,2) DEFAULT '0.00',
  `work_days` int DEFAULT '0',
  `total_qty` decimal(12,4) DEFAULT '0.0000',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'pending',
  `confirmed_by` bigint DEFAULT NULL,
  `confirmed_at` datetime DEFAULT NULL,
  `approved_by` bigint DEFAULT NULL,
  `approved_at` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_month` (`user_id`,`month`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='月度工资';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_wage_record`
--

DROP TABLE IF EXISTS `biz_wage_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_wage_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `work_order_id` bigint DEFAULT NULL,
  `task_id` bigint DEFAULT NULL,
  `report_id` bigint DEFAULT NULL,
  `wage_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'personal/team_allocated',
  `amount` decimal(10,2) NOT NULL,
  `report_date` date DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=288 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='计件工资记录';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_waste_record`
--

DROP TABLE IF EXISTS `biz_waste_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_waste_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `record_date` date NOT NULL COMMENT '记录日期',
  `waste_name` varchar(100) NOT NULL COMMENT '废料名称',
  `quantity` decimal(12,3) NOT NULL DEFAULT '0.000' COMMENT '废料数量',
  `unit` varchar(20) DEFAULT NULL COMMENT '单位',
  `defect_type` varchar(50) DEFAULT NULL COMMENT '缺陷类型',
  `reason` varchar(500) DEFAULT NULL COMMENT '废料原因',
  `operator_name` varchar(50) DEFAULT NULL COMMENT '操作人',
  `work_order_no` varchar(50) DEFAULT NULL COMMENT '关联工单号',
  `product_name` varchar(100) DEFAULT NULL COMMENT '关联产品名称',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_record_date` (`record_date`),
  KEY `idx_defect_type` (`defect_type`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='废料统计记录';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_wo_bom_snapshot`
--

DROP TABLE IF EXISTS `biz_wo_bom_snapshot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_wo_bom_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `work_order_id` bigint NOT NULL,
  `material_id` bigint NOT NULL,
  `bom_item_id` bigint DEFAULT NULL COMMENT 'BOM子件ID，便于查询替代料',
  `required_qty` decimal(12,4) NOT NULL,
  `issued_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '已完成领料数量(含替代料的主料等效量)',
  `issued_task_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '已生成领料任务数量(含替代料的主料等效量)',
  `returned_task_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '已生成退料任务数量',
  `returned_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '已完成退料数量',
  `preferred_substitute_id` bigint DEFAULT NULL COMMENT '主计划指定的优先替代料ID(BizBomSubstitute.id)',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_work_order` (`work_order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=58 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工单BOM快照';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_wo_change`
--

DROP TABLE IF EXISTS `biz_wo_change`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_wo_change` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `work_order_id` bigint NOT NULL,
  `change_type` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `change_content` text COLLATE utf8mb4_general_ci,
  `impact_assessment` text COLLATE utf8mb4_general_ci,
  `change_reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'pending',
  `approved_by` bigint DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_work_order` (`work_order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工单变更';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_wo_cost`
--

DROP TABLE IF EXISTS `biz_wo_cost`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_wo_cost` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `work_order_id` bigint NOT NULL,
  `material_cost` decimal(12,2) DEFAULT '0.00',
  `labor_cost` decimal(12,2) DEFAULT '0.00',
  `outsource_cost` decimal(12,2) DEFAULT '0.00',
  `rework_cost` decimal(12,2) DEFAULT '0.00',
  `scrap_loss` decimal(12,2) DEFAULT '0.00',
  `overhead` decimal(12,2) DEFAULT '0.00',
  `total_cost` decimal(12,2) DEFAULT '0.00',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_order` (`work_order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工单成本';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_wo_process_task`
--

DROP TABLE IF EXISTS `biz_wo_process_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_wo_process_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `work_order_id` bigint NOT NULL,
  `step_no` int NOT NULL,
  `step_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `standard_time` int DEFAULT NULL,
  `piece_price` decimal(10,2) DEFAULT NULL,
  `plan_qty` decimal(12,4) DEFAULT NULL,
  `qualified_qty` decimal(12,4) DEFAULT '0.0000',
  `defect_qty` decimal(12,4) DEFAULT '0.0000',
  `assigned_user_id` bigint DEFAULT NULL,
  `assigned_team_id` bigint DEFAULT NULL,
  `assign_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '指派方式: team=班组, person=个人',
  `assigned_team_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '指派班组名称(冗余)',
  `assigned_user_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '指派人姓名(冗余)',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'pending' COMMENT 'pending/ready/in_progress/completed',
  `is_outsourced` tinyint(1) DEFAULT '0',
  `is_semi_stock` tinyint(1) DEFAULT '0' COMMENT '是否入半成品库(来自工艺路线)',
  `is_outsourceable` tinyint(1) DEFAULT '0' COMMENT '是否可外协(来自工艺路线)',
  `start_time` datetime DEFAULT NULL,
  `end_time` datetime DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  `step_group` int DEFAULT '1' COMMENT '工序组(同组并行执行)',
  `wage_ratio` decimal(6,4) DEFAULT '1.0000' COMMENT '工资比例(默认1;三机模式下作为基础比例)',
  `assigned_member_ids` text COLLATE utf8mb4_general_ci COMMENT '班组指派时允许报工的班组成员userId JSON数组;空/null=全部成员',
  `virtual_material_id` bigint DEFAULT NULL COMMENT '虚拟半成品物料ID',
  `semi_material_id` bigint DEFAULT NULL COMMENT '惰性绑定的半成品物料ID(工序首次入半成品库时建档)',
  `semi_stocked_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '累计已入半成品库数量',
  `semi_issued_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '累计已被下道工序领走数量(=实际消耗)',
  `outsource_request_id` bigint DEFAULT NULL COMMENT '外协申请ID',
  `outsource_order_id` bigint DEFAULT NULL COMMENT '外协加工单ID',
  `outsource_status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '外协状态: requested/ordered/processing/completed',
  `qc_template_id` bigint DEFAULT NULL COMMENT '质检模板ID(来自工艺路线)',
  `qc_status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '质检状态: null=无需质检, pending=待检, pass=合格, fail=不合格',
  `qc_task_id` bigint DEFAULT NULL COMMENT '关联质检任务ID',
  `qc_requirements` text COLLATE utf8mb4_general_ci COMMENT '质检要求JSON(来自生产计划)',
  `reported_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '已报工数量(含待质检)',
  PRIMARY KEY (`id`),
  KEY `idx_work_order` (`work_order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=170 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工单工序任务';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_work_order`
--

DROP TABLE IF EXISTS `biz_work_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_work_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `wo_no` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'WO+时间戳',
  `order_id` bigint DEFAULT NULL COMMENT '关联销售订单',
  `order_item_id` bigint DEFAULT NULL COMMENT '关联订单明细行',
  `product_id` bigint NOT NULL,
  `plan_qty` decimal(12,4) NOT NULL,
  `completed_qty` decimal(12,4) DEFAULT '0.0000',
  `production_mode` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'MTO/MTS',
  `delivery_date` date DEFAULT NULL,
  `original_delivery_date` date DEFAULT NULL COMMENT '工单创建时的原始交货日期(延期判定:current > original 即为延期)',
  `priority` int DEFAULT '0',
  `plan_item_id` bigint DEFAULT NULL COMMENT '关联生产计划行项ID',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'pending' COMMENT 'pending/scheduled/in_progress/completed/cancelled',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `work_order_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'normal' COMMENT '工单类型: normal=正常, repair=返修',
  `source_work_order_id` bigint DEFAULT NULL COMMENT '源工单ID(返修工单关联)',
  `source_defect_id` bigint DEFAULT NULL COMMENT '源缺陷记录ID',
  `route_id` bigint DEFAULT NULL COMMENT '工艺路线ID',
  `ext_fields` text COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '扩展字段(继承自销售订单)',
  `stock_in_task_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '已生成成品入库任务数量',
  `stock_in_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '已确认入库数量',
  `inv_task_status` varchar(20) DEFAULT NULL COMMENT '库存任务状态: null=未生成, partial=部分生成, generated=全部生成',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wo_no` (`wo_no`),
  KEY `idx_order` (`order_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=50 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='生产工单';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_work_report`
--

DROP TABLE IF EXISTS `biz_work_report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_work_report` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `report_no` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `task_id` bigint NOT NULL,
  `work_order_id` bigint NOT NULL,
  `reporter_id` bigint NOT NULL,
  `report_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'personal/team',
  `qualified_qty` decimal(12,4) NOT NULL DEFAULT '0.0000',
  `defect_qty` decimal(12,4) DEFAULT '0.0000',
  `defect_reasons` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'JSON数组',
  `equipment_id` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `batch_no` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `work_hours` decimal(6,2) DEFAULT NULL,
  `photo_urls` text COLLATE utf8mb4_general_ci COMMENT 'JSON数组',
  `piece_wage` decimal(10,2) DEFAULT NULL,
  `wage_mode` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'normal' COMMENT '工资模式: normal=正常(比例1), three_machine=三机(按工序工资比例×0.9)',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'submitted' COMMENT 'submitted/rejected',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `reject_reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '驳回原因',
  `team_report_id` bigint DEFAULT NULL COMMENT '关联班组报工ID',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_task` (`task_id`),
  KEY `idx_reporter` (`reporter_id`)
) ENGINE=InnoDB AUTO_INCREMENT=59 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报工记录';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `sys_biz_config`
--

DROP TABLE IF EXISTS `sys_biz_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_biz_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `module` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '业务模块: sales/purchase/production/workreport/warehouse/quality',
  `config_key` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '全局唯一配置键',
  `config_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '配置项名称',
  `description` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '配置说明',
  `value_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'switch' COMMENT '值类型: switch/radio/checkbox/number/text',
  `options` json DEFAULT NULL COMMENT '选项列表，radio/checkbox时使用',
  `default_value` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '默认值',
  `config_value` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '当前值（null时使用default_value）',
  `sort_order` int NOT NULL DEFAULT '0',
  `update_by` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB AUTO_INCREMENT=40 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='业务模块配置';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `sys_config`
--

DROP TABLE IF EXISTS `sys_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `config_key` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `config_value` text COLLATE utf8mb4_general_ci,
  `config_group` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `remark` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统配置';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `biz_print_template`
-- 打印模板 (vue-plugin-hiprint): 可视化设计器输出 JSON, 业务页面按 biz_type 加载激活模板调 print().
DROP TABLE IF EXISTS `biz_print_template`;
CREATE TABLE `biz_print_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `template_code` varchar(60) COLLATE utf8mb4_general_ci NOT NULL COMMENT '业务键(如 purchase_receipt_reconciliation), 同 biz_type 下唯一',
  `template_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板名称',
  `biz_type` varchar(40) COLLATE utf8mb4_general_ci NOT NULL COMMENT '关联业务类型(purchase_receipt/work_order/...)',
  `template_json` longtext COLLATE utf8mb4_general_ci COMMENT 'hiprint 模板 JSON',
  `is_active` tinyint(1) DEFAULT '0' COMMENT '是否启用(同 biz_type+code 只允许一条 active)',
  `remark` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_biz_type` (`biz_type`),
  KEY `idx_template_code` (`template_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='打印模板';

-- Table structure for table `biz_service_order`
-- 售后订单: 关联销售订单 + 产品, 员工手动勾选保内/保外, 保外配件需录单价对客户收费.
DROP TABLE IF EXISTS `biz_service_order`;
CREATE TABLE `biz_service_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `service_no` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT '售后单号 (SVxxxx)',
  `sales_order_id` bigint DEFAULT NULL COMMENT '关联销售订单 id',
  `sales_order_no` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `customer_id` bigint DEFAULT NULL,
  `customer_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `product_id` bigint DEFAULT NULL,
  `product_no` varchar(60) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `product_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `spec` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `in_warranty` tinyint(1) DEFAULT '1' COMMENT '1=保内 0=保外, 员工手动勾选',
  `issue_description` text COLLATE utf8mb4_general_ci COMMENT '故障/问题描述',
  `handling_method` text COLLATE utf8mb4_general_ci COMMENT '处理方式',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'draft' COMMENT 'draft/processing/completed/cancelled',
  `handler_id` bigint DEFAULT NULL COMMENT '处理人',
  `handler_name` varchar(60) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `total_part_cost` decimal(12,2) DEFAULT '0.00' COMMENT '配件成本合计',
  `total_charge_amount` decimal(12,2) DEFAULT '0.00' COMMENT '向客户收费合计 (保外)',
  `complete_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_service_no` (`service_no`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_sales_order_id` (`sales_order_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='售后订单';

-- Table structure for table `biz_service_order_part`
-- 售后订单更换配件明细
DROP TABLE IF EXISTS `biz_service_order_part`;
CREATE TABLE `biz_service_order_part` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `service_order_id` bigint NOT NULL,
  `material_id` bigint NOT NULL,
  `material_no` varchar(60) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `material_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `spec` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `unit` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `quantity` decimal(12,4) NOT NULL DEFAULT '0.0000',
  `unit_price` decimal(12,2) DEFAULT '0.00' COMMENT '保外时对客户的单价',
  `total_price` decimal(12,2) DEFAULT '0.00' COMMENT 'quantity * unit_price',
  `inv_task_id` bigint DEFAULT NULL COMMENT '关联出库任务 biz_inv_task.id',
  `work_order_id` bigint DEFAULT NULL COMMENT '关联生产工单 biz_work_order.id (配件新做的场景)',
  `plan_item_id` bigint DEFAULT NULL COMMENT '关联车间计划行 biz_production_plan_item.id (配件转生产计划)',
  `issued` tinyint(1) DEFAULT '0' COMMENT '1=已出库',
  `remark` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_service_order_id` (`service_order_id`),
  KEY `idx_material_id` (`material_id`),
  KEY `idx_inv_task_id` (`inv_task_id`),
  KEY `idx_work_order_id` (`work_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='售后订单配件明细';

-- Table structure for table `sys_dept`
--

DROP TABLE IF EXISTS `sys_dept`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dept` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父部门ID，0=顶级',
  `dept_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '部门名称',
  `manager_id` bigint DEFAULT NULL COMMENT '部门负责人ID',
  `sort_order` int DEFAULT '0' COMMENT '显示排序',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'active' COMMENT 'active/disabled',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_parent` (`parent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='部门表';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `sys_menu`
--

DROP TABLE IF EXISTS `sys_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint DEFAULT '0' COMMENT '父菜单ID, 0为顶级',
  `menu_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '菜单名称',
  `menu_type` char(1) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'D目录 M菜单 B按钮',
  `platform` varchar(10) COLLATE utf8mb4_general_ci DEFAULT 'web' COMMENT 'web/h5/both',
  `path` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '路由地址',
  `component` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '组件路径',
  `icon` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '图标名',
  `permission` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '权限标识',
  `sort_order` int DEFAULT '0',
  `visible` tinyint(1) DEFAULT '1' COMMENT '1显示 0隐藏',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'active',
  `remark` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=150103 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='菜单(目录/菜单/按钮)';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `sys_menu_copy1`
--

DROP TABLE IF EXISTS `sys_menu_copy1`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_menu_copy1` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint DEFAULT '0' COMMENT '父菜单ID, 0为顶级',
  `menu_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '菜单名称',
  `menu_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'D目录 M菜单 B按钮',
  `platform` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'web' COMMENT 'web/h5/both',
  `path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '路由地址',
  `component` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '组件路径',
  `icon` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '图标名',
  `permission` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '权限标识',
  `sort_order` int DEFAULT '0',
  `visible` tinyint(1) DEFAULT '1' COMMENT '1显示 0隐藏',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'active',
  `remark` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=90203 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='菜单(目录/菜单/按钮)';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `sys_notification`
--

DROP TABLE IF EXISTS `sys_notification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_notification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `title` varchar(200) COLLATE utf8mb4_general_ci NOT NULL,
  `content` text COLLATE utf8mb4_general_ci,
  `notification_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'approval/alert/system',
  `related_type` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `related_id` bigint DEFAULT NULL,
  `is_read` tinyint(1) DEFAULT '0',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_read` (`is_read`)
) ENGINE=InnoDB AUTO_INCREMENT=102 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='消息通知';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `sys_operation_log`
--

DROP TABLE IF EXISTS `sys_operation_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `module` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `action` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `detail` text COLLATE utf8mb4_general_ci,
  `ip` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='操作日志';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `sys_role`
--

DROP TABLE IF EXISTS `sys_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `role_code` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `description` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `is_preset` tinyint(1) DEFAULT '0' COMMENT '1预设 0自定义',
  `sort_order` int DEFAULT '0',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'active',
  `data_scope` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'all' COMMENT 'all/dept/self/custom',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `sys_role_data_scope`
--

DROP TABLE IF EXISTS `sys_role_data_scope`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_data_scope` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL,
  `scope_type` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'workshop/user/team',
  `scope_value` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '车间名/用户ID/班组ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_scope` (`role_id`,`scope_type`,`scope_value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色数据权限范围';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `sys_role_menu`
--

DROP TABLE IF EXISTS `sys_role_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL,
  `menu_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_menu` (`role_id`,`menu_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1383 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色菜单关联';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '登录账号',
  `phone` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '手机号',
  `password` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'BCrypt加密',
  `real_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '真实姓名',
  `ic_card_no` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '工位机IC卡号(刷卡免密登录)',
  `avatar_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `sex` tinyint DEFAULT NULL COMMENT '0女 1男',
  `workshop` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '所属车间',
  `hire_date` date DEFAULT NULL COMMENT '入职日期',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'active' COMMENT 'active/disabled',
  `login_fail_count` int DEFAULT '0',
  `lock_until` datetime DEFAULT NULL COMMENT '锁定截止时间',
  `last_login_at` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  `dept_id` bigint DEFAULT NULL COMMENT '所属部门ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_ic_card_no` (`ic_card_no`),
  KEY `idx_phone` (`phone`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `sys_user_role`
--

DROP TABLE IF EXISTS `sys_user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_id`)
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户角色关联';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Table structure for table `sys_warehouse`
--

DROP TABLE IF EXISTS `sys_warehouse`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_warehouse` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `warehouse_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `warehouse_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'raw_material/semi_finished/finished/general',
  `address` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `manager_id` bigint DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'active',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父级id，0=顶级仓库，>0=库位',
  `manager_ids` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '管理员ids（逗号分隔）',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1000 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='仓库';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;
/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
-- 创建用户表格列配置表
CREATE TABLE IF NOT EXISTS biz_table_column_config (
                                                       id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
                                                       user_id      BIGINT       NOT NULL COMMENT '用户ID',
                                                       module       VARCHAR(50)  NOT NULL COMMENT '业务模块：sales_order/work_order/purchase_order/customer/supplier/...',
    column_key   VARCHAR(100) NOT NULL COMMENT '列标识（标准列如 orderNo，扩展列如 ext_customField）',
    column_label VARCHAR(100) NOT NULL COMMENT '列显示名称',
    column_type  VARCHAR(20)  NOT NULL DEFAULT 'standard' COMMENT '列类型：standard/extension',
    visible      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否显示',
    sort_order   INT          NOT NULL DEFAULT 0 COMMENT '列顺序',
    create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_module_col (user_id, module, column_key)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表格列配置';

-- 仓库调拨单
CREATE TABLE IF NOT EXISTS `biz_inv_transfer` (
                                                  `id` bigint NOT NULL AUTO_INCREMENT,
                                                  `transfer_no` varchar(50) NOT NULL COMMENT '调拨单号',
    `from_warehouse_id` bigint NOT NULL COMMENT '源仓库ID',
    `from_location_id` bigint DEFAULT NULL COMMENT '源库位ID',
    `to_warehouse_id` bigint NOT NULL COMMENT '目标仓库ID',
    `to_location_id` bigint DEFAULT NULL COMMENT '目标库位ID',
    `status` varchar(30) NOT NULL DEFAULT 'pending' COMMENT '状态: pending/approved/completed/rejected',
    `applicant_id` bigint DEFAULT NULL COMMENT '申请人ID',
    `approve_remark` varchar(500) DEFAULT NULL COMMENT '审批备注',
    `remark` varchar(500) DEFAULT NULL COMMENT '备注',
    `create_by` bigint DEFAULT NULL,
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_by` bigint DEFAULT NULL,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` tinyint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_transfer_no` (`transfer_no`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='仓库调拨单';

-- 仓库调拨单明细
CREATE TABLE IF NOT EXISTS `biz_inv_transfer_item` (
                                                       `id` bigint NOT NULL AUTO_INCREMENT,
                                                       `transfer_id` bigint NOT NULL COMMENT '调拨单ID',
                                                       `material_id` bigint NOT NULL COMMENT '物料ID',
                                                       `material_no` varchar(50) DEFAULT NULL COMMENT '物料编号',
    `material_name` varchar(100) DEFAULT NULL COMMENT '物料名称',
    `spec` varchar(200) DEFAULT NULL COMMENT '规格',
    `unit` varchar(20) DEFAULT NULL COMMENT '单位',
    `quantity` decimal(18,4) NOT NULL COMMENT '调拨数量',
    `create_by` bigint DEFAULT NULL,
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_by` bigint DEFAULT NULL,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` tinyint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_transfer_id` (`transfer_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='仓库调拨单明细';

-- 盘点单主表
CREATE TABLE IF NOT EXISTS `biz_inv_stocktaking` (
                                                     `id` bigint NOT NULL AUTO_INCREMENT,
                                                     `stocktaking_no` varchar(50) NOT NULL COMMENT '盘点单号',
    `warehouse_id` bigint NOT NULL COMMENT '盘点仓库ID',
    `status` varchar(30) NOT NULL DEFAULT 'pending' COMMENT '状态: pending-待审批, approved-已通过, rejected-已驳回',
    `applicant_id` bigint DEFAULT NULL COMMENT '申请人ID',
    `approve_remark` varchar(500) DEFAULT NULL COMMENT '审批备注',
    `remark` varchar(500) DEFAULT NULL COMMENT '备注',
    `create_by` bigint DEFAULT NULL,
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_by` bigint DEFAULT NULL,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` tinyint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_stocktaking_no` (`stocktaking_no`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='仓库盘点单';

-- 盘点单明细表
CREATE TABLE IF NOT EXISTS `biz_inv_stocktaking_item` (
                                                          `id` bigint NOT NULL AUTO_INCREMENT,
                                                          `stocktaking_id` bigint NOT NULL COMMENT '盘点单ID',
                                                          `material_id` bigint NOT NULL COMMENT '物料ID',
                                                          `material_no` varchar(50) DEFAULT NULL COMMENT '物料编号',
    `material_name` varchar(100) DEFAULT NULL COMMENT '物料名称',
    `spec` varchar(200) DEFAULT NULL COMMENT '规格型号',
    `unit` varchar(20) DEFAULT NULL COMMENT '单位',
    `system_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '在库数量(快照)',
    `locked_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '锁定数量(快照)',
    `available_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '可用数量(快照)',
    `actual_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '实盘数量',
    `diff_qty` decimal(12,4) DEFAULT '0.0000' COMMENT '差异数量(正=盘盈,负=盘亏)',
    `create_by` bigint DEFAULT NULL,
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_by` bigint DEFAULT NULL,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` tinyint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_stocktaking_id` (`stocktaking_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='仓库盘点单明细';

CREATE TABLE IF NOT EXISTS `sys_code_sequence` (
    `prefix`      varchar(20) NOT NULL COMMENT '编号前缀',
    `seq_date`    char(8)     NOT NULL COMMENT '日期(yyyyMMdd)',
    `current_seq` int         NOT NULL DEFAULT 0 COMMENT '当前序号',
    PRIMARY KEY (`prefix`, `seq_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='单据号序列表';

-- Table structure for table `acc_account`

CREATE TABLE IF NOT EXISTS `acc_account` (
    `id`              bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `account_code`    varchar(20)   NOT NULL                COMMENT '科目编码',
    `account_name`    varchar(60)   NOT NULL                COMMENT '科目名称',
    `account_type`    varchar(20)   NOT NULL                COMMENT '科目类型: asset/liability/equity/income/expense/cost',
    `direction`       varchar(10)   NOT NULL DEFAULT 'debit' COMMENT '余额方向: debit/credit',
    `parent_id`       bigint                                COMMENT '父科目ID',
    `level`           tinyint       NOT NULL DEFAULT 1      COMMENT '层级',
    `is_leaf`         tinyint(1)    NOT NULL DEFAULT 1      COMMENT '是否末级科目',
    `is_enabled`      tinyint(1)    NOT NULL DEFAULT 1      COMMENT '是否启用',
    `opening_balance` decimal(18,2) NOT NULL DEFAULT 0.00   COMMENT '期初余额',
    `remark`          varchar(200)                          COMMENT '备注',
    `create_by`       bigint DEFAULT NULL,
    `create_time`     datetime DEFAULT CURRENT_TIMESTAMP,
    `update_by`       bigint DEFAULT NULL,
    `update_time`     datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`         tinyint(1)    NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_account_code` (`account_code`),
    KEY `idx_parent` (`parent_id`),
    KEY `idx_type` (`account_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='会计科目';

-- Table structure for table `acc_voucher`

CREATE TABLE IF NOT EXISTS `acc_voucher` (
    `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `voucher_no`       varchar(30)   NOT NULL                COMMENT '凭证编号',
    `voucher_type`     varchar(20)   NOT NULL DEFAULT 'transfer' COMMENT '凭证类型: receipt/payment/transfer',
    `voucher_date`     date          NOT NULL                COMMENT '凭证日期',
    `period`           varchar(7)                            COMMENT '所属期间 YYYY-MM',
    `summary`          varchar(200)                          COMMENT '摘要',
    `total_debit`      decimal(18,2) NOT NULL DEFAULT 0.00   COMMENT '借方合计',
    `total_credit`     decimal(18,2) NOT NULL DEFAULT 0.00   COMMENT '贷方合计',
    `status`           varchar(20)   NOT NULL DEFAULT 'draft' COMMENT '状态: draft/posted',
    `is_reversed`      tinyint(1)    NOT NULL DEFAULT 0      COMMENT '是否已冲红',
    `reversed_by`      bigint                                COMMENT '冲红凭证ID',
    `auto_source_type` varchar(30)                           COMMENT '自动来源类型',
    `auto_source_id`   bigint                                COMMENT '自动来源业务单据ID',
    `remark`           varchar(200)                          COMMENT '备注',
    `create_by`        bigint DEFAULT NULL,
    `create_time`      datetime DEFAULT CURRENT_TIMESTAMP,
    `update_by`        bigint DEFAULT NULL,
    `update_time`      datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`          tinyint(1)    NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_voucher_no` (`voucher_no`),
    KEY `idx_date` (`voucher_date`),
    KEY `idx_period` (`period`),
    KEY `idx_status` (`status`),
    KEY `idx_source` (`auto_source_type`, `auto_source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='记账凭证';

-- Table structure for table `acc_voucher_line`

CREATE TABLE IF NOT EXISTS `acc_voucher_line` (
    `id`              bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `voucher_id`      bigint        NOT NULL                COMMENT '凭证ID',
    `line_no`         int           NOT NULL DEFAULT 1      COMMENT '行号',
    `summary`         varchar(200)                          COMMENT '摘要',
    `account_id`      bigint        NOT NULL                COMMENT '科目ID',
    `account_code`    varchar(20)                           COMMENT '科目编码（冗余）',
    `account_name`    varchar(60)                           COMMENT '科目名称（冗余）',
    `debit_amount`    decimal(18,2) NOT NULL DEFAULT 0.00   COMMENT '借方金额',
    `credit_amount`   decimal(18,2) NOT NULL DEFAULT 0.00   COMMENT '贷方金额',
    `create_by`       bigint DEFAULT NULL,
    `create_time`     datetime DEFAULT CURRENT_TIMESTAMP,
    `update_by`       bigint DEFAULT NULL,
    `update_time`     datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`         tinyint(1)    NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_voucher_id` (`voucher_id`),
    KEY `idx_account_id` (`account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='凭证明细行';

-- Table structure for table `acc_period_close`

CREATE TABLE IF NOT EXISTS `acc_period_close` (
    `id`          bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `period`      varchar(7)    NOT NULL                COMMENT '结账期间 YYYY-MM',
    `close_time`  datetime      NOT NULL                COMMENT '结账时间',
    `close_by`    bigint                                COMMENT '结账人',
    `voucher_id`  bigint                                COMMENT '损益结转凭证ID',
    `remark`      varchar(200)                          COMMENT '备注',
    `create_by`   bigint DEFAULT NULL,
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_by`   bigint DEFAULT NULL,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     tinyint(1)    NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_period` (`period`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='期末结账记录';

-- ----------------------------
-- Table structure for biz_material_spec
-- ----------------------------
CREATE TABLE IF NOT EXISTS `biz_material_spec` (
    `id`          bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `spec_name`   varchar(100)  NOT NULL                COMMENT '规格名称（如：材质、尺寸、颜色）',
    `sort_order`  int           DEFAULT 0               COMMENT '排序号',
    `status`      varchar(20)   DEFAULT 'active'        COMMENT '状态 active/disabled',
    `create_by`   bigint DEFAULT NULL,
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_by`   bigint DEFAULT NULL,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     tinyint(1)    NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_spec_name` (`spec_name`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='物料规格名称表';

-- ============================================================
-- EAM 设备管理模块
-- ============================================================

CREATE TABLE IF NOT EXISTS `eam_equipment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `equipment_no` VARCHAR(50) NOT NULL COMMENT '设备编号',
  `equipment_name` VARCHAR(100) NOT NULL COMMENT '设备名称',
  `model` VARCHAR(100) DEFAULT NULL,
  `category` VARCHAR(50) DEFAULT NULL,
  `supplier_id` BIGINT DEFAULT NULL,
  `purchase_date` DATE DEFAULT NULL,
  `enable_date` DATE DEFAULT NULL,
  `status` VARCHAR(20) DEFAULT 'NORMAL' COMMENT 'NORMAL/MAINTENANCE/RETIRED',
  `dept_id` BIGINT DEFAULT NULL,
  `location` VARCHAR(200) DEFAULT NULL,
  `specification` VARCHAR(500) DEFAULT NULL,
  `parameters` VARCHAR(1000) DEFAULT NULL,
  `image_url` VARCHAR(500) DEFAULT NULL,
  `attachment_urls` TEXT DEFAULT NULL COMMENT '附件JSON: [{name,url}]',
  `maintenance_cycle` INT DEFAULT NULL COMMENT '维护周期(天)',
  `next_maintenance_date` DATE DEFAULT NULL,
  `equipment_value` DECIMAL(15,2) DEFAULT NULL,
  `depreciation_years` INT DEFAULT NULL,
  `remark` VARCHAR(500) DEFAULT NULL,
  `create_by` BIGINT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_by` BIGINT DEFAULT NULL,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_equipment_no` (`equipment_no`),
  KEY `idx_category` (`category`),
  KEY `idx_dept` (`dept_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备台账';

CREATE TABLE IF NOT EXISTS `eam_equipment_status` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `equipment_id` BIGINT NOT NULL,
  `running_status` VARCHAR(20) DEFAULT 'STOPPED' COMMENT 'RUNNING/STOPPED/MAINTENANCE',
  `last_running_time` DATETIME DEFAULT NULL,
  `total_running_hours` DECIMAL(10,2) DEFAULT 0,
  `current_temperature` DECIMAL(10,2) DEFAULT NULL,
  `current_pressure` DECIMAL(10,2) DEFAULT NULL,
  `current_speed` DECIMAL(10,2) DEFAULT NULL,
  `current_load` DECIMAL(10,2) DEFAULT NULL,
  `alarm_status` VARCHAR(20) DEFAULT 'NORMAL' COMMENT 'NORMAL/WARNING/CRITICAL',
  `alarm_message` VARCHAR(500) DEFAULT NULL,
  `create_by` BIGINT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_by` BIGINT DEFAULT NULL,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_equipment_id` (`equipment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备运行状态';

CREATE TABLE IF NOT EXISTS `eam_maintenance_template` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `template_name` VARCHAR(100) NOT NULL,
  `equipment_type` VARCHAR(50) DEFAULT NULL,
  `cycle_type` VARCHAR(20) DEFAULT NULL COMMENT 'DAY/WEEK/MONTH/HOUR',
  `cycle_value` INT DEFAULT NULL,
  `remark` VARCHAR(500) DEFAULT NULL,
  `create_by` BIGINT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_by` BIGINT DEFAULT NULL,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_name` (`template_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保养模板';

CREATE TABLE IF NOT EXISTS `eam_maintenance_template_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `template_id` BIGINT NOT NULL,
  `item_group` VARCHAR(50) DEFAULT NULL,
  `item_name` VARCHAR(200) NOT NULL,
  `method` VARCHAR(500) DEFAULT NULL,
  `standard` VARCHAR(500) DEFAULT NULL,
  `uom` VARCHAR(20) DEFAULT NULL,
  `required` TINYINT DEFAULT 1,
  `need_photo` TINYINT DEFAULT 0,
  `sort_no` INT DEFAULT 0,
  `create_by` BIGINT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_by` BIGINT DEFAULT NULL,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_template` (`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保养模板项';

CREATE TABLE IF NOT EXISTS `eam_maintenance_template_equipment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `template_id` BIGINT NOT NULL,
  `equipment_id` BIGINT NOT NULL,
  `create_by` BIGINT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_by` BIGINT DEFAULT NULL,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_equipment` (`template_id`, `equipment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保养模板-设备关联';

CREATE TABLE IF NOT EXISTS `eam_maintenance_plan` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `plan_code` VARCHAR(50) NOT NULL,
  `plan_name` VARCHAR(100) NOT NULL,
  `maintenance_type` TINYINT DEFAULT 0 COMMENT '0周期 1预防性 2状态',
  `template_id` BIGINT DEFAULT NULL,
  `equipment_id` BIGINT DEFAULT NULL,
  `execution_cycle` INT DEFAULT NULL,
  `next_execution_time` DATETIME DEFAULT NULL,
  `planned_start_time` DATETIME DEFAULT NULL COMMENT '计划开始时间',
  `planned_end_time` DATETIME DEFAULT NULL COMMENT '计划结束时间',
  `maintenance_content` TEXT,
  `maintenance_standard` TEXT,
  `estimated_hours` DECIMAL(8,2) DEFAULT NULL,
  `required_spare_parts` TEXT,
  `plan_status` TINYINT DEFAULT 0 COMMENT '0草稿 1已发布 2已暂停 3已取消',
  `exec_status` TINYINT DEFAULT 0 COMMENT '0待下达 1已下达 2部分完成 3全部完成',
  `remark` VARCHAR(500) DEFAULT NULL,
  `create_by` BIGINT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_by` BIGINT DEFAULT NULL,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_code` (`plan_code`),
  KEY `idx_plan_status` (`plan_status`, `exec_status`),
  KEY `idx_template` (`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保养计划';

CREATE TABLE IF NOT EXISTS `eam_maintenance_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `task_code` VARCHAR(50) NOT NULL,
  `plan_id` BIGINT DEFAULT NULL,
  `equipment_id` BIGINT DEFAULT NULL,
  `maintenance_content` TEXT,
  `executor_id` BIGINT DEFAULT NULL,
  `executor_name` VARCHAR(100) DEFAULT NULL,
  `planned_start_time` DATETIME DEFAULT NULL,
  `planned_end_time` DATETIME DEFAULT NULL,
  `actual_start_time` DATETIME DEFAULT NULL,
  `actual_end_time` DATETIME DEFAULT NULL,
  `task_status` TINYINT DEFAULT 0 COMMENT '0待执行 1执行中 2已完成 3已取消 4已暂停',
  `maintenance_result` TEXT,
  `issues_found` TEXT,
  `consumables_used` TEXT,
  `spare_parts_replaced` TEXT,
  `actual_hours` DECIMAL(8,2) DEFAULT NULL,
  `maintenance_standard` TEXT,
  `remark` VARCHAR(500) DEFAULT NULL,
  `create_by` BIGINT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_by` BIGINT DEFAULT NULL,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_code` (`task_code`),
  KEY `idx_plan` (`plan_id`),
  KEY `idx_equipment` (`equipment_id`),
  KEY `idx_task_status` (`task_status`),
  KEY `idx_executor` (`executor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保养任务';

CREATE TABLE IF NOT EXISTS `eam_maintenance_task_detail` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `task_id` BIGINT NOT NULL,
  `equipment_id` BIGINT DEFAULT NULL,
  `template_item_id` BIGINT DEFAULT NULL,
  `item_group` VARCHAR(50) DEFAULT NULL,
  `item_name` VARCHAR(200) NOT NULL,
  `method` VARCHAR(500) DEFAULT NULL,
  `standard` VARCHAR(500) DEFAULT NULL,
  `uom` VARCHAR(20) DEFAULT NULL,
  `required` TINYINT DEFAULT 1,
  `need_photo` TINYINT DEFAULT 0,
  `sort_no` INT DEFAULT 0,
  `status` TINYINT DEFAULT 0 COMMENT '0待检 1已完成 2异常',
  `result_value` VARCHAR(500) DEFAULT NULL,
  `photo_url` VARCHAR(500) DEFAULT NULL,
  `remark` VARCHAR(500) DEFAULT NULL,
  `create_by` BIGINT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_by` BIGINT DEFAULT NULL,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_task` (`task_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保养任务明细';

CREATE TABLE IF NOT EXISTS `eam_inspection_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `item_code` VARCHAR(50) DEFAULT NULL,
  `item_name` VARCHAR(200) NOT NULL,
  `item_type` VARCHAR(50) DEFAULT NULL,
  `standard` VARCHAR(500) DEFAULT NULL,
  `method` VARCHAR(500) DEFAULT NULL,
  `is_required` TINYINT DEFAULT 1,
  `status` VARCHAR(20) DEFAULT 'active',
  `remark` VARCHAR(500) DEFAULT NULL,
  `create_by` BIGINT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_by` BIGINT DEFAULT NULL,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点检项目库';

CREATE TABLE IF NOT EXISTS `eam_inspection_template` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `template_name` VARCHAR(100) NOT NULL,
  `equipment_type` VARCHAR(50) DEFAULT NULL,
  `inspection_type` TINYINT DEFAULT 0 COMMENT '0日常 1定期 2专项',
  `status` VARCHAR(20) DEFAULT 'active',
  `remark` VARCHAR(500) DEFAULT NULL,
  `create_by` BIGINT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_by` BIGINT DEFAULT NULL,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tpl_name` (`template_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点检模板';

CREATE TABLE IF NOT EXISTS `eam_inspection_template_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `template_id` BIGINT NOT NULL,
  `item_id` BIGINT DEFAULT NULL,
  `item_name` VARCHAR(200) NOT NULL,
  `standard` VARCHAR(500) DEFAULT NULL,
  `method` VARCHAR(500) DEFAULT NULL,
  `is_required` TINYINT DEFAULT 1,
  `sort_order` INT DEFAULT 0,
  `create_by` BIGINT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_by` BIGINT DEFAULT NULL,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_template` (`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点检模板项';

CREATE TABLE IF NOT EXISTS `eam_inspection_template_equipment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `template_id` BIGINT NOT NULL,
  `equipment_id` BIGINT NOT NULL,
  `create_by` BIGINT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_by` BIGINT DEFAULT NULL,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tpl_equip` (`template_id`, `equipment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点检模板-设备关联';

CREATE TABLE IF NOT EXISTS `eam_inspection_plan` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `plan_code` VARCHAR(50) NOT NULL,
  `plan_name` VARCHAR(100) NOT NULL,
  `template_id` BIGINT DEFAULT NULL,
  `inspection_type` TINYINT DEFAULT 0,
  `execution_cycle` INT DEFAULT NULL,
  `next_execution_time` DATETIME DEFAULT NULL,
  `planned_start_time` DATETIME DEFAULT NULL COMMENT '计划开始时间',
  `planned_end_time` DATETIME DEFAULT NULL COMMENT '计划结束时间',
  `estimated_duration` INT DEFAULT NULL COMMENT '预估时长(分钟)',
  `assignee_id` BIGINT DEFAULT NULL,
  `assignee_name` VARCHAR(100) DEFAULT NULL,
  `plan_status` TINYINT DEFAULT 0 COMMENT '0草稿 1已发布 3已取消',
  `exec_status` TINYINT DEFAULT 0,
  `remark` VARCHAR(500) DEFAULT NULL,
  `create_by` BIGINT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_by` BIGINT DEFAULT NULL,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_code` (`plan_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点检计划';

CREATE TABLE IF NOT EXISTS `eam_inspection_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `task_code` VARCHAR(50) NOT NULL,
  `plan_id` BIGINT DEFAULT NULL,
  `template_id` BIGINT DEFAULT NULL,
  `equipment_id` BIGINT DEFAULT NULL,
  `inspector_id` BIGINT DEFAULT NULL,
  `inspector_name` VARCHAR(100) DEFAULT NULL,
  `planned_start_time` DATETIME DEFAULT NULL,
  `planned_end_time` DATETIME DEFAULT NULL,
  `start_time` DATETIME DEFAULT NULL,
  `submit_time` DATETIME DEFAULT NULL,
  `actual_start_time` DATETIME DEFAULT NULL COMMENT '实际开始时间',
  `actual_end_time` DATETIME DEFAULT NULL COMMENT '实际结束时间',
  `task_status` TINYINT DEFAULT 0,
  `remark` VARCHAR(500) DEFAULT NULL,
  `create_by` BIGINT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_by` BIGINT DEFAULT NULL,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_code` (`task_code`),
  KEY `idx_plan` (`plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点检任务';

CREATE TABLE IF NOT EXISTS `eam_inspection_task_detail` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `task_id` BIGINT NOT NULL,
  `template_item_id` BIGINT DEFAULT NULL,
  `item_name` VARCHAR(200) NOT NULL,
  `standard` VARCHAR(500) DEFAULT NULL,
  `method` VARCHAR(500) DEFAULT NULL,
  `is_required` TINYINT DEFAULT 1,
  `sort_order` INT DEFAULT 0,
  `status` TINYINT DEFAULT 0 COMMENT '0待检 1已完成 2异常',
  `result_value` VARCHAR(500) DEFAULT NULL,
  `photo_url` VARCHAR(500) DEFAULT NULL,
  `remark` VARCHAR(500) DEFAULT NULL,
  `create_by` BIGINT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_by` BIGINT DEFAULT NULL,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_task` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点检任务明细';

CREATE TABLE IF NOT EXISTS `eam_repair_request` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `request_no` VARCHAR(50) NOT NULL,
  `equipment_id` BIGINT DEFAULT NULL,
  `equipment_no` VARCHAR(50) DEFAULT NULL,
  `equipment_name` VARCHAR(100) DEFAULT NULL,
  `repair_type` VARCHAR(20) DEFAULT NULL COMMENT 'FAULT/PREVENTIVE/OVERHAUL',
  `priority` VARCHAR(20) DEFAULT NULL COMMENT 'LOW/MEDIUM/HIGH/URGENT',
  `planned_start_time` DATETIME DEFAULT NULL COMMENT '计划开始时间',
  `planned_end_time` DATETIME DEFAULT NULL COMMENT '计划结束时间',
  `fault_description` TEXT,
  `attachments` TEXT,
  `reporter_id` BIGINT DEFAULT NULL,
  `reporter_name` VARCHAR(100) DEFAULT NULL,
  `report_time` DATETIME DEFAULT NULL,
  `acceptor_id` BIGINT DEFAULT NULL,
  `acceptor_name` VARCHAR(100) DEFAULT NULL,
  `accept_time` DATETIME DEFAULT NULL,
  `status` VARCHAR(20) DEFAULT 'PENDING',
  `create_by` BIGINT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_by` BIGINT DEFAULT NULL,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_request_no` (`request_no`),
  KEY `idx_status` (`status`),
  KEY `idx_equipment` (`equipment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报修申请';

CREATE TABLE IF NOT EXISTS `eam_repair_management` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `work_order_no` VARCHAR(50) NOT NULL,
  `repair_request_id` BIGINT DEFAULT NULL,
  `equipment_id` BIGINT DEFAULT NULL,
  `equipment_no` VARCHAR(50) DEFAULT NULL,
  `equipment_name` VARCHAR(100) DEFAULT NULL,
  `repair_type` VARCHAR(20) DEFAULT NULL,
  `priority` VARCHAR(20) DEFAULT NULL,
  `assignee_id` BIGINT DEFAULT NULL,
  `assignee_name` VARCHAR(100) DEFAULT NULL,
  `planned_start_time` DATETIME DEFAULT NULL,
  `planned_end_time` DATETIME DEFAULT NULL,
  `actual_start_time` DATETIME DEFAULT NULL,
  `actual_end_time` DATETIME DEFAULT NULL,
  `estimated_hours` DECIMAL(8,2) DEFAULT NULL,
  `actual_hours` DECIMAL(8,2) DEFAULT NULL,
  `fault_description` TEXT,
  `repair_plan` TEXT,
  `repair_result` TEXT,
  `repair_cost` DECIMAL(15,2) DEFAULT NULL,
  `replaced_parts` TEXT,
  `status` VARCHAR(20) DEFAULT 'PENDING',
  `create_by` BIGINT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_by` BIGINT DEFAULT NULL,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_order_no` (`work_order_no`),
  KEY `idx_status` (`status`),
  KEY `idx_assignee` (`assignee_id`),
  KEY `idx_equipment` (`equipment_id`),
  KEY `idx_repair_request` (`repair_request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='维修工单';

CREATE TABLE IF NOT EXISTS `eam_equipment_alarm_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `equipment_id` BIGINT NOT NULL,
  `alarm_status` VARCHAR(20) DEFAULT NULL,
  `alarm_message` VARCHAR(500) DEFAULT NULL,
  `occurred_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `create_by` BIGINT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_by` BIGINT DEFAULT NULL,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_equipment` (`equipment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备报警历史';

CREATE TABLE IF NOT EXISTS `biz_process_drawing` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `product_id` BIGINT NOT NULL COMMENT '产品ID',
  `step_no` INT NOT NULL COMMENT '工序序号',
  `step_name` VARCHAR(100) DEFAULT NULL,
  `plan_item_step_id` BIGINT DEFAULT NULL,
  `process_task_id` BIGINT DEFAULT NULL,
  `work_order_id` BIGINT DEFAULT NULL,
  `version` INT NOT NULL DEFAULT 1,
  `file_name` VARCHAR(200) NOT NULL,
  `file_url` VARCHAR(500) NOT NULL,
  `file_size` BIGINT DEFAULT NULL,
  `remark` VARCHAR(500) DEFAULT NULL,
  `uploaded_by` BIGINT DEFAULT NULL,
  `uploaded_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `create_by` BIGINT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_by` BIGINT DEFAULT NULL,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_product_step` (`product_id`, `step_no`),
  KEY `idx_plan_item_step` (`plan_item_step_id`),
  KEY `idx_process_task` (`process_task_id`),
  KEY `idx_work_order` (`work_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工序图纸(版本化)';
