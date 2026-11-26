-- =====================================================
-- 物料重复数据修复脚本 (修正版v2)
-- 仅处理 materialNo<->spec 交叉引用的真正重复
-- 以情况1数据格式为准: materialNo 使用带横杠格式
-- 合并组数: 136
-- 合并记录数: 217
-- =====================================================

-- 第1步: 请先备份数据库!

SET FOREIGN_KEY_CHECKS = 0;
START TRANSACTION;

-- ============================================================
-- 底座: 主ID=55(SZ100500Y28001DCF) -> 正确编码=SZ-100-500-Y280-01DCF
-- 合并: id=805(SZ-100-500-Y280-01DCF), id=533(ZG0052)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'SZ-100-500-Y280-01DCF' AND id != 55;
UPDATE biz_material SET material_no = 'SZ-100-500-Y280-01DCF', deleted = 0, status = 'active' WHERE id = 55;

UPDATE `biz_inv_transaction` SET material_id = 55 WHERE material_id IN (805,533);
UPDATE `biz_inv_task_item` SET material_id = 55 WHERE material_id IN (805,533);
UPDATE `biz_inv_history_item` SET material_id = 55 WHERE material_id IN (805,533);
UPDATE `biz_inv_request_item` SET material_id = 55 WHERE material_id IN (805,533);
UPDATE `biz_inv_reservation` SET material_id = 55 WHERE material_id IN (805,533);
UPDATE `biz_inv_transfer_item` SET material_id = 55 WHERE material_id IN (805,533);
UPDATE `biz_inv_stocktaking_item` SET material_id = 55 WHERE material_id IN (805,533);
UPDATE `biz_purchase_request` SET material_id = 55 WHERE material_id IN (805,533);
UPDATE `biz_purchase_order_item` SET material_id = 55 WHERE material_id IN (805,533);
UPDATE `biz_purchase_receipt` SET material_id = 55 WHERE material_id IN (805,533);
UPDATE `biz_purchase_return_item` SET material_id = 55 WHERE material_id IN (805,533);
UPDATE `biz_wo_bom_snapshot` SET material_id = 55 WHERE material_id IN (805,533);
UPDATE `biz_plan_item_material` SET material_id = 55 WHERE material_id IN (805,533);
UPDATE `biz_outsource_delivery_item` SET material_id = 55 WHERE material_id IN (805,533);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 55 WHERE material_id IN (805,533);
UPDATE `biz_sales_order_item` SET product_id = 55 WHERE product_id IN (805,533);
UPDATE `biz_work_order` SET product_id = 55 WHERE product_id IN (805,533);
UPDATE `biz_return_order_item` SET product_id = 55 WHERE product_id IN (805,533);
UPDATE `biz_mrp_production_suggestion` SET product_id = 55 WHERE product_id IN (805,533);
UPDATE `biz_process_route` SET product_id = 55 WHERE product_id IN (805,533);
UPDATE `biz_bom_item` SET material_id = 55 WHERE material_id IN (805,533);
UPDATE `biz_bom_item` SET product_id = 55 WHERE product_id IN (805,533);
UPDATE `biz_qc_task` SET material_id = 55 WHERE material_id IN (805,533);
UPDATE `biz_qc_task` SET product_id = 55 WHERE product_id IN (805,533);
UPDATE `biz_defect_record` SET material_id = 55 WHERE material_id IN (805,533);
UPDATE `biz_defect_record` SET product_id = 55 WHERE product_id IN (805,533);
UPDATE `biz_mrp_demand` SET material_id = 55 WHERE material_id IN (805,533);
UPDATE `biz_mrp_demand` SET product_id = 55 WHERE product_id IN (805,533);
UPDATE `biz_outsource_request` SET material_id = 55 WHERE material_id IN (805,533);
UPDATE `biz_outsource_request` SET product_id = 55 WHERE product_id IN (805,533);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (805,533) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 55
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 55 WHERE material_id IN (805,533)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 55) tmp);
DELETE FROM biz_inventory WHERE material_id IN (805,533);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (805,533);

-- ============================================================
-- 底座: 主ID=58(TZJD0902HJ) -> 正确编码=TZJD-0902HJ
-- 合并: id=719(TZJD-0902HJ), id=487(ZG0006)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJD-0902HJ' AND id != 58;
UPDATE biz_material SET material_no = 'TZJD-0902HJ', deleted = 0, status = 'active' WHERE id = 58;

UPDATE `biz_inv_transaction` SET material_id = 58 WHERE material_id IN (719,487);
UPDATE `biz_inv_task_item` SET material_id = 58 WHERE material_id IN (719,487);
UPDATE `biz_inv_history_item` SET material_id = 58 WHERE material_id IN (719,487);
UPDATE `biz_inv_request_item` SET material_id = 58 WHERE material_id IN (719,487);
UPDATE `biz_inv_reservation` SET material_id = 58 WHERE material_id IN (719,487);
UPDATE `biz_inv_transfer_item` SET material_id = 58 WHERE material_id IN (719,487);
UPDATE `biz_inv_stocktaking_item` SET material_id = 58 WHERE material_id IN (719,487);
UPDATE `biz_purchase_request` SET material_id = 58 WHERE material_id IN (719,487);
UPDATE `biz_purchase_order_item` SET material_id = 58 WHERE material_id IN (719,487);
UPDATE `biz_purchase_receipt` SET material_id = 58 WHERE material_id IN (719,487);
UPDATE `biz_purchase_return_item` SET material_id = 58 WHERE material_id IN (719,487);
UPDATE `biz_wo_bom_snapshot` SET material_id = 58 WHERE material_id IN (719,487);
UPDATE `biz_plan_item_material` SET material_id = 58 WHERE material_id IN (719,487);
UPDATE `biz_outsource_delivery_item` SET material_id = 58 WHERE material_id IN (719,487);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 58 WHERE material_id IN (719,487);
UPDATE `biz_sales_order_item` SET product_id = 58 WHERE product_id IN (719,487);
UPDATE `biz_work_order` SET product_id = 58 WHERE product_id IN (719,487);
UPDATE `biz_return_order_item` SET product_id = 58 WHERE product_id IN (719,487);
UPDATE `biz_mrp_production_suggestion` SET product_id = 58 WHERE product_id IN (719,487);
UPDATE `biz_process_route` SET product_id = 58 WHERE product_id IN (719,487);
UPDATE `biz_bom_item` SET material_id = 58 WHERE material_id IN (719,487);
UPDATE `biz_bom_item` SET product_id = 58 WHERE product_id IN (719,487);
UPDATE `biz_qc_task` SET material_id = 58 WHERE material_id IN (719,487);
UPDATE `biz_qc_task` SET product_id = 58 WHERE product_id IN (719,487);
UPDATE `biz_defect_record` SET material_id = 58 WHERE material_id IN (719,487);
UPDATE `biz_defect_record` SET product_id = 58 WHERE product_id IN (719,487);
UPDATE `biz_mrp_demand` SET material_id = 58 WHERE material_id IN (719,487);
UPDATE `biz_mrp_demand` SET product_id = 58 WHERE product_id IN (719,487);
UPDATE `biz_outsource_request` SET material_id = 58 WHERE material_id IN (719,487);
UPDATE `biz_outsource_request` SET product_id = 58 WHERE product_id IN (719,487);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (719,487) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 58
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 58 WHERE material_id IN (719,487)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 58) tmp);
DELETE FROM biz_inventory WHERE material_id IN (719,487);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (719,487);

-- ============================================================
-- 底座: 主ID=59(TZJEY28001DC) -> 正确编码=TZJE-Y280-01DC
-- 合并: id=777(TZJE-Y280-01DC), id=605(ZG0124)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJE-Y280-01DC' AND id != 59;
UPDATE biz_material SET material_no = 'TZJE-Y280-01DC', deleted = 0, status = 'active' WHERE id = 59;

UPDATE `biz_inv_transaction` SET material_id = 59 WHERE material_id IN (777,605);
UPDATE `biz_inv_task_item` SET material_id = 59 WHERE material_id IN (777,605);
UPDATE `biz_inv_history_item` SET material_id = 59 WHERE material_id IN (777,605);
UPDATE `biz_inv_request_item` SET material_id = 59 WHERE material_id IN (777,605);
UPDATE `biz_inv_reservation` SET material_id = 59 WHERE material_id IN (777,605);
UPDATE `biz_inv_transfer_item` SET material_id = 59 WHERE material_id IN (777,605);
UPDATE `biz_inv_stocktaking_item` SET material_id = 59 WHERE material_id IN (777,605);
UPDATE `biz_purchase_request` SET material_id = 59 WHERE material_id IN (777,605);
UPDATE `biz_purchase_order_item` SET material_id = 59 WHERE material_id IN (777,605);
UPDATE `biz_purchase_receipt` SET material_id = 59 WHERE material_id IN (777,605);
UPDATE `biz_purchase_return_item` SET material_id = 59 WHERE material_id IN (777,605);
UPDATE `biz_wo_bom_snapshot` SET material_id = 59 WHERE material_id IN (777,605);
UPDATE `biz_plan_item_material` SET material_id = 59 WHERE material_id IN (777,605);
UPDATE `biz_outsource_delivery_item` SET material_id = 59 WHERE material_id IN (777,605);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 59 WHERE material_id IN (777,605);
UPDATE `biz_sales_order_item` SET product_id = 59 WHERE product_id IN (777,605);
UPDATE `biz_work_order` SET product_id = 59 WHERE product_id IN (777,605);
UPDATE `biz_return_order_item` SET product_id = 59 WHERE product_id IN (777,605);
UPDATE `biz_mrp_production_suggestion` SET product_id = 59 WHERE product_id IN (777,605);
UPDATE `biz_process_route` SET product_id = 59 WHERE product_id IN (777,605);
UPDATE `biz_bom_item` SET material_id = 59 WHERE material_id IN (777,605);
UPDATE `biz_bom_item` SET product_id = 59 WHERE product_id IN (777,605);
UPDATE `biz_qc_task` SET material_id = 59 WHERE material_id IN (777,605);
UPDATE `biz_qc_task` SET product_id = 59 WHERE product_id IN (777,605);
UPDATE `biz_defect_record` SET material_id = 59 WHERE material_id IN (777,605);
UPDATE `biz_defect_record` SET product_id = 59 WHERE product_id IN (777,605);
UPDATE `biz_mrp_demand` SET material_id = 59 WHERE material_id IN (777,605);
UPDATE `biz_mrp_demand` SET product_id = 59 WHERE product_id IN (777,605);
UPDATE `biz_outsource_request` SET material_id = 59 WHERE material_id IN (777,605);
UPDATE `biz_outsource_request` SET product_id = 59 WHERE product_id IN (777,605);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (777,605) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 59
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 59 WHERE material_id IN (777,605)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 59) tmp);
DELETE FROM biz_inventory WHERE material_id IN (777,605);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (777,605);

-- ============================================================
-- 发货底座: 主ID=96(FHDZD100) -> 正确编码=FHDZ-D-100
-- 合并: id=656(FHDZ-D-100), id=559(ZG0078)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'FHDZ-D-100' AND id != 96;
UPDATE biz_material SET material_no = 'FHDZ-D-100', deleted = 0, status = 'active' WHERE id = 96;

UPDATE `biz_inv_transaction` SET material_id = 96 WHERE material_id IN (656,559);
UPDATE `biz_inv_task_item` SET material_id = 96 WHERE material_id IN (656,559);
UPDATE `biz_inv_history_item` SET material_id = 96 WHERE material_id IN (656,559);
UPDATE `biz_inv_request_item` SET material_id = 96 WHERE material_id IN (656,559);
UPDATE `biz_inv_reservation` SET material_id = 96 WHERE material_id IN (656,559);
UPDATE `biz_inv_transfer_item` SET material_id = 96 WHERE material_id IN (656,559);
UPDATE `biz_inv_stocktaking_item` SET material_id = 96 WHERE material_id IN (656,559);
UPDATE `biz_purchase_request` SET material_id = 96 WHERE material_id IN (656,559);
UPDATE `biz_purchase_order_item` SET material_id = 96 WHERE material_id IN (656,559);
UPDATE `biz_purchase_receipt` SET material_id = 96 WHERE material_id IN (656,559);
UPDATE `biz_purchase_return_item` SET material_id = 96 WHERE material_id IN (656,559);
UPDATE `biz_wo_bom_snapshot` SET material_id = 96 WHERE material_id IN (656,559);
UPDATE `biz_plan_item_material` SET material_id = 96 WHERE material_id IN (656,559);
UPDATE `biz_outsource_delivery_item` SET material_id = 96 WHERE material_id IN (656,559);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 96 WHERE material_id IN (656,559);
UPDATE `biz_sales_order_item` SET product_id = 96 WHERE product_id IN (656,559);
UPDATE `biz_work_order` SET product_id = 96 WHERE product_id IN (656,559);
UPDATE `biz_return_order_item` SET product_id = 96 WHERE product_id IN (656,559);
UPDATE `biz_mrp_production_suggestion` SET product_id = 96 WHERE product_id IN (656,559);
UPDATE `biz_process_route` SET product_id = 96 WHERE product_id IN (656,559);
UPDATE `biz_bom_item` SET material_id = 96 WHERE material_id IN (656,559);
UPDATE `biz_bom_item` SET product_id = 96 WHERE product_id IN (656,559);
UPDATE `biz_qc_task` SET material_id = 96 WHERE material_id IN (656,559);
UPDATE `biz_qc_task` SET product_id = 96 WHERE product_id IN (656,559);
UPDATE `biz_defect_record` SET material_id = 96 WHERE material_id IN (656,559);
UPDATE `biz_defect_record` SET product_id = 96 WHERE product_id IN (656,559);
UPDATE `biz_mrp_demand` SET material_id = 96 WHERE material_id IN (656,559);
UPDATE `biz_mrp_demand` SET product_id = 96 WHERE product_id IN (656,559);
UPDATE `biz_outsource_request` SET material_id = 96 WHERE material_id IN (656,559);
UPDATE `biz_outsource_request` SET product_id = 96 WHERE product_id IN (656,559);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (656,559) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 96
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 96 WHERE material_id IN (656,559)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 96) tmp);
DELETE FROM biz_inventory WHERE material_id IN (656,559);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (656,559);

-- ============================================================
-- 发货底座: 主ID=97(FHDZE100) -> 正确编码=FHDZ-E-100
-- 合并: id=649(FHDZ-E-100), id=552(ZG0071)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'FHDZ-E-100' AND id != 97;
UPDATE biz_material SET material_no = 'FHDZ-E-100', deleted = 0, status = 'active' WHERE id = 97;

UPDATE `biz_inv_transaction` SET material_id = 97 WHERE material_id IN (649,552);
UPDATE `biz_inv_task_item` SET material_id = 97 WHERE material_id IN (649,552);
UPDATE `biz_inv_history_item` SET material_id = 97 WHERE material_id IN (649,552);
UPDATE `biz_inv_request_item` SET material_id = 97 WHERE material_id IN (649,552);
UPDATE `biz_inv_reservation` SET material_id = 97 WHERE material_id IN (649,552);
UPDATE `biz_inv_transfer_item` SET material_id = 97 WHERE material_id IN (649,552);
UPDATE `biz_inv_stocktaking_item` SET material_id = 97 WHERE material_id IN (649,552);
UPDATE `biz_purchase_request` SET material_id = 97 WHERE material_id IN (649,552);
UPDATE `biz_purchase_order_item` SET material_id = 97 WHERE material_id IN (649,552);
UPDATE `biz_purchase_receipt` SET material_id = 97 WHERE material_id IN (649,552);
UPDATE `biz_purchase_return_item` SET material_id = 97 WHERE material_id IN (649,552);
UPDATE `biz_wo_bom_snapshot` SET material_id = 97 WHERE material_id IN (649,552);
UPDATE `biz_plan_item_material` SET material_id = 97 WHERE material_id IN (649,552);
UPDATE `biz_outsource_delivery_item` SET material_id = 97 WHERE material_id IN (649,552);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 97 WHERE material_id IN (649,552);
UPDATE `biz_sales_order_item` SET product_id = 97 WHERE product_id IN (649,552);
UPDATE `biz_work_order` SET product_id = 97 WHERE product_id IN (649,552);
UPDATE `biz_return_order_item` SET product_id = 97 WHERE product_id IN (649,552);
UPDATE `biz_mrp_production_suggestion` SET product_id = 97 WHERE product_id IN (649,552);
UPDATE `biz_process_route` SET product_id = 97 WHERE product_id IN (649,552);
UPDATE `biz_bom_item` SET material_id = 97 WHERE material_id IN (649,552);
UPDATE `biz_bom_item` SET product_id = 97 WHERE product_id IN (649,552);
UPDATE `biz_qc_task` SET material_id = 97 WHERE material_id IN (649,552);
UPDATE `biz_qc_task` SET product_id = 97 WHERE product_id IN (649,552);
UPDATE `biz_defect_record` SET material_id = 97 WHERE material_id IN (649,552);
UPDATE `biz_defect_record` SET product_id = 97 WHERE product_id IN (649,552);
UPDATE `biz_mrp_demand` SET material_id = 97 WHERE material_id IN (649,552);
UPDATE `biz_mrp_demand` SET product_id = 97 WHERE product_id IN (649,552);
UPDATE `biz_outsource_request` SET material_id = 97 WHERE material_id IN (649,552);
UPDATE `biz_outsource_request` SET product_id = 97 WHERE product_id IN (649,552);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (649,552) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 97
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 97 WHERE material_id IN (649,552)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 97) tmp);
DELETE FROM biz_inventory WHERE material_id IN (649,552);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (649,552);

-- ============================================================
-- 发货底座: 主ID=100(FHDZS200) -> 正确编码=FHDZ-S-200
-- 合并: id=646(FHDZ-S-200), id=549(ZG0068)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'FHDZ-S-200' AND id != 100;
UPDATE biz_material SET material_no = 'FHDZ-S-200', deleted = 0, status = 'active' WHERE id = 100;

UPDATE `biz_inv_transaction` SET material_id = 100 WHERE material_id IN (646,549);
UPDATE `biz_inv_task_item` SET material_id = 100 WHERE material_id IN (646,549);
UPDATE `biz_inv_history_item` SET material_id = 100 WHERE material_id IN (646,549);
UPDATE `biz_inv_request_item` SET material_id = 100 WHERE material_id IN (646,549);
UPDATE `biz_inv_reservation` SET material_id = 100 WHERE material_id IN (646,549);
UPDATE `biz_inv_transfer_item` SET material_id = 100 WHERE material_id IN (646,549);
UPDATE `biz_inv_stocktaking_item` SET material_id = 100 WHERE material_id IN (646,549);
UPDATE `biz_purchase_request` SET material_id = 100 WHERE material_id IN (646,549);
UPDATE `biz_purchase_order_item` SET material_id = 100 WHERE material_id IN (646,549);
UPDATE `biz_purchase_receipt` SET material_id = 100 WHERE material_id IN (646,549);
UPDATE `biz_purchase_return_item` SET material_id = 100 WHERE material_id IN (646,549);
UPDATE `biz_wo_bom_snapshot` SET material_id = 100 WHERE material_id IN (646,549);
UPDATE `biz_plan_item_material` SET material_id = 100 WHERE material_id IN (646,549);
UPDATE `biz_outsource_delivery_item` SET material_id = 100 WHERE material_id IN (646,549);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 100 WHERE material_id IN (646,549);
UPDATE `biz_sales_order_item` SET product_id = 100 WHERE product_id IN (646,549);
UPDATE `biz_work_order` SET product_id = 100 WHERE product_id IN (646,549);
UPDATE `biz_return_order_item` SET product_id = 100 WHERE product_id IN (646,549);
UPDATE `biz_mrp_production_suggestion` SET product_id = 100 WHERE product_id IN (646,549);
UPDATE `biz_process_route` SET product_id = 100 WHERE product_id IN (646,549);
UPDATE `biz_bom_item` SET material_id = 100 WHERE material_id IN (646,549);
UPDATE `biz_bom_item` SET product_id = 100 WHERE product_id IN (646,549);
UPDATE `biz_qc_task` SET material_id = 100 WHERE material_id IN (646,549);
UPDATE `biz_qc_task` SET product_id = 100 WHERE product_id IN (646,549);
UPDATE `biz_defect_record` SET material_id = 100 WHERE material_id IN (646,549);
UPDATE `biz_defect_record` SET product_id = 100 WHERE product_id IN (646,549);
UPDATE `biz_mrp_demand` SET material_id = 100 WHERE material_id IN (646,549);
UPDATE `biz_mrp_demand` SET product_id = 100 WHERE product_id IN (646,549);
UPDATE `biz_outsource_request` SET material_id = 100 WHERE material_id IN (646,549);
UPDATE `biz_outsource_request` SET product_id = 100 WHERE product_id IN (646,549);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (646,549) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 100
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 100 WHERE material_id IN (646,549)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 100) tmp);
DELETE FROM biz_inventory WHERE material_id IN (646,549);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (646,549);

-- ============================================================
-- 联轴器罩: 主ID=104(HFXS3501201) -> 正确编码=HFXS350-1201
-- 合并: id=796(HFXS350-1201), id=523(ZG0042)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'HFXS350-1201' AND id != 104;
UPDATE biz_material SET material_no = 'HFXS350-1201', deleted = 0, status = 'active' WHERE id = 104;

UPDATE `biz_inv_transaction` SET material_id = 104 WHERE material_id IN (796,523);
UPDATE `biz_inv_task_item` SET material_id = 104 WHERE material_id IN (796,523);
UPDATE `biz_inv_history_item` SET material_id = 104 WHERE material_id IN (796,523);
UPDATE `biz_inv_request_item` SET material_id = 104 WHERE material_id IN (796,523);
UPDATE `biz_inv_reservation` SET material_id = 104 WHERE material_id IN (796,523);
UPDATE `biz_inv_transfer_item` SET material_id = 104 WHERE material_id IN (796,523);
UPDATE `biz_inv_stocktaking_item` SET material_id = 104 WHERE material_id IN (796,523);
UPDATE `biz_purchase_request` SET material_id = 104 WHERE material_id IN (796,523);
UPDATE `biz_purchase_order_item` SET material_id = 104 WHERE material_id IN (796,523);
UPDATE `biz_purchase_receipt` SET material_id = 104 WHERE material_id IN (796,523);
UPDATE `biz_purchase_return_item` SET material_id = 104 WHERE material_id IN (796,523);
UPDATE `biz_wo_bom_snapshot` SET material_id = 104 WHERE material_id IN (796,523);
UPDATE `biz_plan_item_material` SET material_id = 104 WHERE material_id IN (796,523);
UPDATE `biz_outsource_delivery_item` SET material_id = 104 WHERE material_id IN (796,523);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 104 WHERE material_id IN (796,523);
UPDATE `biz_sales_order_item` SET product_id = 104 WHERE product_id IN (796,523);
UPDATE `biz_work_order` SET product_id = 104 WHERE product_id IN (796,523);
UPDATE `biz_return_order_item` SET product_id = 104 WHERE product_id IN (796,523);
UPDATE `biz_mrp_production_suggestion` SET product_id = 104 WHERE product_id IN (796,523);
UPDATE `biz_process_route` SET product_id = 104 WHERE product_id IN (796,523);
UPDATE `biz_bom_item` SET material_id = 104 WHERE material_id IN (796,523);
UPDATE `biz_bom_item` SET product_id = 104 WHERE product_id IN (796,523);
UPDATE `biz_qc_task` SET material_id = 104 WHERE material_id IN (796,523);
UPDATE `biz_qc_task` SET product_id = 104 WHERE product_id IN (796,523);
UPDATE `biz_defect_record` SET material_id = 104 WHERE material_id IN (796,523);
UPDATE `biz_defect_record` SET product_id = 104 WHERE product_id IN (796,523);
UPDATE `biz_mrp_demand` SET material_id = 104 WHERE material_id IN (796,523);
UPDATE `biz_mrp_demand` SET product_id = 104 WHERE product_id IN (796,523);
UPDATE `biz_outsource_request` SET material_id = 104 WHERE material_id IN (796,523);
UPDATE `biz_outsource_request` SET product_id = 104 WHERE product_id IN (796,523);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (796,523) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 104
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 104 WHERE material_id IN (796,523)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 104) tmp);
DELETE FROM biz_inventory WHERE material_id IN (796,523);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (796,523);

-- ============================================================
-- 皮带罩: 主ID=106(HJC2001ZV) -> 正确编码=HJC-20-01ZV
-- 合并: id=802(HJC-20-01ZV), id=529(ZG0048)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'HJC-20-01ZV' AND id != 106;
UPDATE biz_material SET material_no = 'HJC-20-01ZV', deleted = 0, status = 'active' WHERE id = 106;

UPDATE `biz_inv_transaction` SET material_id = 106 WHERE material_id IN (802,529);
UPDATE `biz_inv_task_item` SET material_id = 106 WHERE material_id IN (802,529);
UPDATE `biz_inv_history_item` SET material_id = 106 WHERE material_id IN (802,529);
UPDATE `biz_inv_request_item` SET material_id = 106 WHERE material_id IN (802,529);
UPDATE `biz_inv_reservation` SET material_id = 106 WHERE material_id IN (802,529);
UPDATE `biz_inv_transfer_item` SET material_id = 106 WHERE material_id IN (802,529);
UPDATE `biz_inv_stocktaking_item` SET material_id = 106 WHERE material_id IN (802,529);
UPDATE `biz_purchase_request` SET material_id = 106 WHERE material_id IN (802,529);
UPDATE `biz_purchase_order_item` SET material_id = 106 WHERE material_id IN (802,529);
UPDATE `biz_purchase_receipt` SET material_id = 106 WHERE material_id IN (802,529);
UPDATE `biz_purchase_return_item` SET material_id = 106 WHERE material_id IN (802,529);
UPDATE `biz_wo_bom_snapshot` SET material_id = 106 WHERE material_id IN (802,529);
UPDATE `biz_plan_item_material` SET material_id = 106 WHERE material_id IN (802,529);
UPDATE `biz_outsource_delivery_item` SET material_id = 106 WHERE material_id IN (802,529);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 106 WHERE material_id IN (802,529);
UPDATE `biz_sales_order_item` SET product_id = 106 WHERE product_id IN (802,529);
UPDATE `biz_work_order` SET product_id = 106 WHERE product_id IN (802,529);
UPDATE `biz_return_order_item` SET product_id = 106 WHERE product_id IN (802,529);
UPDATE `biz_mrp_production_suggestion` SET product_id = 106 WHERE product_id IN (802,529);
UPDATE `biz_process_route` SET product_id = 106 WHERE product_id IN (802,529);
UPDATE `biz_bom_item` SET material_id = 106 WHERE material_id IN (802,529);
UPDATE `biz_bom_item` SET product_id = 106 WHERE product_id IN (802,529);
UPDATE `biz_qc_task` SET material_id = 106 WHERE material_id IN (802,529);
UPDATE `biz_qc_task` SET product_id = 106 WHERE product_id IN (802,529);
UPDATE `biz_defect_record` SET material_id = 106 WHERE material_id IN (802,529);
UPDATE `biz_defect_record` SET product_id = 106 WHERE product_id IN (802,529);
UPDATE `biz_mrp_demand` SET material_id = 106 WHERE material_id IN (802,529);
UPDATE `biz_mrp_demand` SET product_id = 106 WHERE product_id IN (802,529);
UPDATE `biz_outsource_request` SET material_id = 106 WHERE material_id IN (802,529);
UPDATE `biz_outsource_request` SET product_id = 106 WHERE product_id IN (802,529);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (802,529) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 106
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 106 WHERE material_id IN (802,529)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 106) tmp);
DELETE FROM biz_inventory WHERE material_id IN (802,529);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (802,529);

-- ============================================================
-- 联轴器罩: 主ID=179(JGTZJS2501201B230710) -> 正确编码=JGTZJS-250-1201B-230710
-- 合并: id=776(JGTZJS-250-1201B-230710), id=604(ZG0123)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'JGTZJS-250-1201B-230710' AND id != 179;
UPDATE biz_material SET material_no = 'JGTZJS-250-1201B-230710', deleted = 0, status = 'active' WHERE id = 179;

UPDATE `biz_inv_transaction` SET material_id = 179 WHERE material_id IN (776,604);
UPDATE `biz_inv_task_item` SET material_id = 179 WHERE material_id IN (776,604);
UPDATE `biz_inv_history_item` SET material_id = 179 WHERE material_id IN (776,604);
UPDATE `biz_inv_request_item` SET material_id = 179 WHERE material_id IN (776,604);
UPDATE `biz_inv_reservation` SET material_id = 179 WHERE material_id IN (776,604);
UPDATE `biz_inv_transfer_item` SET material_id = 179 WHERE material_id IN (776,604);
UPDATE `biz_inv_stocktaking_item` SET material_id = 179 WHERE material_id IN (776,604);
UPDATE `biz_purchase_request` SET material_id = 179 WHERE material_id IN (776,604);
UPDATE `biz_purchase_order_item` SET material_id = 179 WHERE material_id IN (776,604);
UPDATE `biz_purchase_receipt` SET material_id = 179 WHERE material_id IN (776,604);
UPDATE `biz_purchase_return_item` SET material_id = 179 WHERE material_id IN (776,604);
UPDATE `biz_wo_bom_snapshot` SET material_id = 179 WHERE material_id IN (776,604);
UPDATE `biz_plan_item_material` SET material_id = 179 WHERE material_id IN (776,604);
UPDATE `biz_outsource_delivery_item` SET material_id = 179 WHERE material_id IN (776,604);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 179 WHERE material_id IN (776,604);
UPDATE `biz_sales_order_item` SET product_id = 179 WHERE product_id IN (776,604);
UPDATE `biz_work_order` SET product_id = 179 WHERE product_id IN (776,604);
UPDATE `biz_return_order_item` SET product_id = 179 WHERE product_id IN (776,604);
UPDATE `biz_mrp_production_suggestion` SET product_id = 179 WHERE product_id IN (776,604);
UPDATE `biz_process_route` SET product_id = 179 WHERE product_id IN (776,604);
UPDATE `biz_bom_item` SET material_id = 179 WHERE material_id IN (776,604);
UPDATE `biz_bom_item` SET product_id = 179 WHERE product_id IN (776,604);
UPDATE `biz_qc_task` SET material_id = 179 WHERE material_id IN (776,604);
UPDATE `biz_qc_task` SET product_id = 179 WHERE product_id IN (776,604);
UPDATE `biz_defect_record` SET material_id = 179 WHERE material_id IN (776,604);
UPDATE `biz_defect_record` SET product_id = 179 WHERE product_id IN (776,604);
UPDATE `biz_mrp_demand` SET material_id = 179 WHERE material_id IN (776,604);
UPDATE `biz_mrp_demand` SET product_id = 179 WHERE product_id IN (776,604);
UPDATE `biz_outsource_request` SET material_id = 179 WHERE material_id IN (776,604);
UPDATE `biz_outsource_request` SET product_id = 179 WHERE product_id IN (776,604);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (776,604) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 179
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 179 WHERE material_id IN (776,604)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 179) tmp);
DELETE FROM biz_inventory WHERE material_id IN (776,604);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (776,604);

-- ============================================================
-- 集中水箱: 主ID=192(JZSX0.8×0.8) -> 正确编码=JZSX-0.8×0.8
-- 合并: id=763(JZSX-0.8×0.8), id=591(ZG0110)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'JZSX-0.8×0.8' AND id != 192;
UPDATE biz_material SET material_no = 'JZSX-0.8×0.8', deleted = 0, status = 'active' WHERE id = 192;

UPDATE `biz_inv_transaction` SET material_id = 192 WHERE material_id IN (763,591);
UPDATE `biz_inv_task_item` SET material_id = 192 WHERE material_id IN (763,591);
UPDATE `biz_inv_history_item` SET material_id = 192 WHERE material_id IN (763,591);
UPDATE `biz_inv_request_item` SET material_id = 192 WHERE material_id IN (763,591);
UPDATE `biz_inv_reservation` SET material_id = 192 WHERE material_id IN (763,591);
UPDATE `biz_inv_transfer_item` SET material_id = 192 WHERE material_id IN (763,591);
UPDATE `biz_inv_stocktaking_item` SET material_id = 192 WHERE material_id IN (763,591);
UPDATE `biz_purchase_request` SET material_id = 192 WHERE material_id IN (763,591);
UPDATE `biz_purchase_order_item` SET material_id = 192 WHERE material_id IN (763,591);
UPDATE `biz_purchase_receipt` SET material_id = 192 WHERE material_id IN (763,591);
UPDATE `biz_purchase_return_item` SET material_id = 192 WHERE material_id IN (763,591);
UPDATE `biz_wo_bom_snapshot` SET material_id = 192 WHERE material_id IN (763,591);
UPDATE `biz_plan_item_material` SET material_id = 192 WHERE material_id IN (763,591);
UPDATE `biz_outsource_delivery_item` SET material_id = 192 WHERE material_id IN (763,591);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 192 WHERE material_id IN (763,591);
UPDATE `biz_sales_order_item` SET product_id = 192 WHERE product_id IN (763,591);
UPDATE `biz_work_order` SET product_id = 192 WHERE product_id IN (763,591);
UPDATE `biz_return_order_item` SET product_id = 192 WHERE product_id IN (763,591);
UPDATE `biz_mrp_production_suggestion` SET product_id = 192 WHERE product_id IN (763,591);
UPDATE `biz_process_route` SET product_id = 192 WHERE product_id IN (763,591);
UPDATE `biz_bom_item` SET material_id = 192 WHERE material_id IN (763,591);
UPDATE `biz_bom_item` SET product_id = 192 WHERE product_id IN (763,591);
UPDATE `biz_qc_task` SET material_id = 192 WHERE material_id IN (763,591);
UPDATE `biz_qc_task` SET product_id = 192 WHERE product_id IN (763,591);
UPDATE `biz_defect_record` SET material_id = 192 WHERE material_id IN (763,591);
UPDATE `biz_defect_record` SET product_id = 192 WHERE product_id IN (763,591);
UPDATE `biz_mrp_demand` SET material_id = 192 WHERE material_id IN (763,591);
UPDATE `biz_mrp_demand` SET product_id = 192 WHERE product_id IN (763,591);
UPDATE `biz_outsource_request` SET material_id = 192 WHERE material_id IN (763,591);
UPDATE `biz_outsource_request` SET product_id = 192 WHERE product_id IN (763,591);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (763,591) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 192
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 192 WHERE material_id IN (763,591)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 192) tmp);
DELETE FROM biz_inventory WHERE material_id IN (763,591);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (763,591);

-- ============================================================
-- 集中水箱盖: 主ID=193(JZSX0.8×0.815) -> 正确编码=JZSX-0.8×0.8-15
-- 合并: id=764(JZSX-0.8×0.8-15), id=592(ZG0111)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'JZSX-0.8×0.8-15' AND id != 193;
UPDATE biz_material SET material_no = 'JZSX-0.8×0.8-15', deleted = 0, status = 'active' WHERE id = 193;

UPDATE `biz_inv_transaction` SET material_id = 193 WHERE material_id IN (764,592);
UPDATE `biz_inv_task_item` SET material_id = 193 WHERE material_id IN (764,592);
UPDATE `biz_inv_history_item` SET material_id = 193 WHERE material_id IN (764,592);
UPDATE `biz_inv_request_item` SET material_id = 193 WHERE material_id IN (764,592);
UPDATE `biz_inv_reservation` SET material_id = 193 WHERE material_id IN (764,592);
UPDATE `biz_inv_transfer_item` SET material_id = 193 WHERE material_id IN (764,592);
UPDATE `biz_inv_stocktaking_item` SET material_id = 193 WHERE material_id IN (764,592);
UPDATE `biz_purchase_request` SET material_id = 193 WHERE material_id IN (764,592);
UPDATE `biz_purchase_order_item` SET material_id = 193 WHERE material_id IN (764,592);
UPDATE `biz_purchase_receipt` SET material_id = 193 WHERE material_id IN (764,592);
UPDATE `biz_purchase_return_item` SET material_id = 193 WHERE material_id IN (764,592);
UPDATE `biz_wo_bom_snapshot` SET material_id = 193 WHERE material_id IN (764,592);
UPDATE `biz_plan_item_material` SET material_id = 193 WHERE material_id IN (764,592);
UPDATE `biz_outsource_delivery_item` SET material_id = 193 WHERE material_id IN (764,592);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 193 WHERE material_id IN (764,592);
UPDATE `biz_sales_order_item` SET product_id = 193 WHERE product_id IN (764,592);
UPDATE `biz_work_order` SET product_id = 193 WHERE product_id IN (764,592);
UPDATE `biz_return_order_item` SET product_id = 193 WHERE product_id IN (764,592);
UPDATE `biz_mrp_production_suggestion` SET product_id = 193 WHERE product_id IN (764,592);
UPDATE `biz_process_route` SET product_id = 193 WHERE product_id IN (764,592);
UPDATE `biz_bom_item` SET material_id = 193 WHERE material_id IN (764,592);
UPDATE `biz_bom_item` SET product_id = 193 WHERE product_id IN (764,592);
UPDATE `biz_qc_task` SET material_id = 193 WHERE material_id IN (764,592);
UPDATE `biz_qc_task` SET product_id = 193 WHERE product_id IN (764,592);
UPDATE `biz_defect_record` SET material_id = 193 WHERE material_id IN (764,592);
UPDATE `biz_defect_record` SET product_id = 193 WHERE product_id IN (764,592);
UPDATE `biz_mrp_demand` SET material_id = 193 WHERE material_id IN (764,592);
UPDATE `biz_mrp_demand` SET product_id = 193 WHERE product_id IN (764,592);
UPDATE `biz_outsource_request` SET material_id = 193 WHERE material_id IN (764,592);
UPDATE `biz_outsource_request` SET product_id = 193 WHERE product_id IN (764,592);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (764,592) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 193
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 193 WHERE material_id IN (764,592)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 193) tmp);
DELETE FROM biz_inventory WHERE material_id IN (764,592);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (764,592);

-- ============================================================
-- 底座: 主ID=200(SZ091120001ZVH500) -> 正确编码=SZ-0911-200-01ZV-H500
-- 合并: id=713(SZ-0911-200-01ZV-H500), id=570(ZG0089)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'SZ-0911-200-01ZV-H500' AND id != 200;
UPDATE biz_material SET material_no = 'SZ-0911-200-01ZV-H500', deleted = 0, status = 'active' WHERE id = 200;

UPDATE `biz_inv_transaction` SET material_id = 200 WHERE material_id IN (713,570);
UPDATE `biz_inv_task_item` SET material_id = 200 WHERE material_id IN (713,570);
UPDATE `biz_inv_history_item` SET material_id = 200 WHERE material_id IN (713,570);
UPDATE `biz_inv_request_item` SET material_id = 200 WHERE material_id IN (713,570);
UPDATE `biz_inv_reservation` SET material_id = 200 WHERE material_id IN (713,570);
UPDATE `biz_inv_transfer_item` SET material_id = 200 WHERE material_id IN (713,570);
UPDATE `biz_inv_stocktaking_item` SET material_id = 200 WHERE material_id IN (713,570);
UPDATE `biz_purchase_request` SET material_id = 200 WHERE material_id IN (713,570);
UPDATE `biz_purchase_order_item` SET material_id = 200 WHERE material_id IN (713,570);
UPDATE `biz_purchase_receipt` SET material_id = 200 WHERE material_id IN (713,570);
UPDATE `biz_purchase_return_item` SET material_id = 200 WHERE material_id IN (713,570);
UPDATE `biz_wo_bom_snapshot` SET material_id = 200 WHERE material_id IN (713,570);
UPDATE `biz_plan_item_material` SET material_id = 200 WHERE material_id IN (713,570);
UPDATE `biz_outsource_delivery_item` SET material_id = 200 WHERE material_id IN (713,570);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 200 WHERE material_id IN (713,570);
UPDATE `biz_sales_order_item` SET product_id = 200 WHERE product_id IN (713,570);
UPDATE `biz_work_order` SET product_id = 200 WHERE product_id IN (713,570);
UPDATE `biz_return_order_item` SET product_id = 200 WHERE product_id IN (713,570);
UPDATE `biz_mrp_production_suggestion` SET product_id = 200 WHERE product_id IN (713,570);
UPDATE `biz_process_route` SET product_id = 200 WHERE product_id IN (713,570);
UPDATE `biz_bom_item` SET material_id = 200 WHERE material_id IN (713,570);
UPDATE `biz_bom_item` SET product_id = 200 WHERE product_id IN (713,570);
UPDATE `biz_qc_task` SET material_id = 200 WHERE material_id IN (713,570);
UPDATE `biz_qc_task` SET product_id = 200 WHERE product_id IN (713,570);
UPDATE `biz_defect_record` SET material_id = 200 WHERE material_id IN (713,570);
UPDATE `biz_defect_record` SET product_id = 200 WHERE product_id IN (713,570);
UPDATE `biz_mrp_demand` SET material_id = 200 WHERE material_id IN (713,570);
UPDATE `biz_mrp_demand` SET product_id = 200 WHERE product_id IN (713,570);
UPDATE `biz_outsource_request` SET material_id = 200 WHERE material_id IN (713,570);
UPDATE `biz_outsource_request` SET product_id = 200 WHERE product_id IN (713,570);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (713,570) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 200
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 200 WHERE material_id IN (713,570)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 200) tmp);
DELETE FROM biz_inventory WHERE material_id IN (713,570);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (713,570);

-- ============================================================
-- 联轴器罩: 主ID=207(SZ10050012016) -> 正确编码=SZ-100-500-1201-6
-- 合并: id=633(SZ-100-500-1201-6), id=518(ZG0037)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'SZ-100-500-1201-6' AND id != 207;
UPDATE biz_material SET material_no = 'SZ-100-500-1201-6', deleted = 0, status = 'active' WHERE id = 207;

UPDATE `biz_inv_transaction` SET material_id = 207 WHERE material_id IN (633,518);
UPDATE `biz_inv_task_item` SET material_id = 207 WHERE material_id IN (633,518);
UPDATE `biz_inv_history_item` SET material_id = 207 WHERE material_id IN (633,518);
UPDATE `biz_inv_request_item` SET material_id = 207 WHERE material_id IN (633,518);
UPDATE `biz_inv_reservation` SET material_id = 207 WHERE material_id IN (633,518);
UPDATE `biz_inv_transfer_item` SET material_id = 207 WHERE material_id IN (633,518);
UPDATE `biz_inv_stocktaking_item` SET material_id = 207 WHERE material_id IN (633,518);
UPDATE `biz_purchase_request` SET material_id = 207 WHERE material_id IN (633,518);
UPDATE `biz_purchase_order_item` SET material_id = 207 WHERE material_id IN (633,518);
UPDATE `biz_purchase_receipt` SET material_id = 207 WHERE material_id IN (633,518);
UPDATE `biz_purchase_return_item` SET material_id = 207 WHERE material_id IN (633,518);
UPDATE `biz_wo_bom_snapshot` SET material_id = 207 WHERE material_id IN (633,518);
UPDATE `biz_plan_item_material` SET material_id = 207 WHERE material_id IN (633,518);
UPDATE `biz_outsource_delivery_item` SET material_id = 207 WHERE material_id IN (633,518);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 207 WHERE material_id IN (633,518);
UPDATE `biz_sales_order_item` SET product_id = 207 WHERE product_id IN (633,518);
UPDATE `biz_work_order` SET product_id = 207 WHERE product_id IN (633,518);
UPDATE `biz_return_order_item` SET product_id = 207 WHERE product_id IN (633,518);
UPDATE `biz_mrp_production_suggestion` SET product_id = 207 WHERE product_id IN (633,518);
UPDATE `biz_process_route` SET product_id = 207 WHERE product_id IN (633,518);
UPDATE `biz_bom_item` SET material_id = 207 WHERE material_id IN (633,518);
UPDATE `biz_bom_item` SET product_id = 207 WHERE product_id IN (633,518);
UPDATE `biz_qc_task` SET material_id = 207 WHERE material_id IN (633,518);
UPDATE `biz_qc_task` SET product_id = 207 WHERE product_id IN (633,518);
UPDATE `biz_defect_record` SET material_id = 207 WHERE material_id IN (633,518);
UPDATE `biz_defect_record` SET product_id = 207 WHERE product_id IN (633,518);
UPDATE `biz_mrp_demand` SET material_id = 207 WHERE material_id IN (633,518);
UPDATE `biz_mrp_demand` SET product_id = 207 WHERE product_id IN (633,518);
UPDATE `biz_outsource_request` SET material_id = 207 WHERE material_id IN (633,518);
UPDATE `biz_outsource_request` SET product_id = 207 WHERE product_id IN (633,518);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (633,518) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 207
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 207 WHERE material_id IN (633,518)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 207) tmp);
DELETE FROM biz_inventory WHERE material_id IN (633,518);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (633,518);

-- ============================================================
-- 底座: 主ID=214(SZ100500Y31501DCJZ) -> 正确编码=SZ-100-500-Y315-01DCJZ
-- 合并: id=760(SZ-100-500-Y315-01DCJZ), id=520(ZG0039)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'SZ-100-500-Y315-01DCJZ' AND id != 214;
UPDATE biz_material SET material_no = 'SZ-100-500-Y315-01DCJZ', deleted = 0, status = 'active' WHERE id = 214;

UPDATE `biz_inv_transaction` SET material_id = 214 WHERE material_id IN (760,520);
UPDATE `biz_inv_task_item` SET material_id = 214 WHERE material_id IN (760,520);
UPDATE `biz_inv_history_item` SET material_id = 214 WHERE material_id IN (760,520);
UPDATE `biz_inv_request_item` SET material_id = 214 WHERE material_id IN (760,520);
UPDATE `biz_inv_reservation` SET material_id = 214 WHERE material_id IN (760,520);
UPDATE `biz_inv_transfer_item` SET material_id = 214 WHERE material_id IN (760,520);
UPDATE `biz_inv_stocktaking_item` SET material_id = 214 WHERE material_id IN (760,520);
UPDATE `biz_purchase_request` SET material_id = 214 WHERE material_id IN (760,520);
UPDATE `biz_purchase_order_item` SET material_id = 214 WHERE material_id IN (760,520);
UPDATE `biz_purchase_receipt` SET material_id = 214 WHERE material_id IN (760,520);
UPDATE `biz_purchase_return_item` SET material_id = 214 WHERE material_id IN (760,520);
UPDATE `biz_wo_bom_snapshot` SET material_id = 214 WHERE material_id IN (760,520);
UPDATE `biz_plan_item_material` SET material_id = 214 WHERE material_id IN (760,520);
UPDATE `biz_outsource_delivery_item` SET material_id = 214 WHERE material_id IN (760,520);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 214 WHERE material_id IN (760,520);
UPDATE `biz_sales_order_item` SET product_id = 214 WHERE product_id IN (760,520);
UPDATE `biz_work_order` SET product_id = 214 WHERE product_id IN (760,520);
UPDATE `biz_return_order_item` SET product_id = 214 WHERE product_id IN (760,520);
UPDATE `biz_mrp_production_suggestion` SET product_id = 214 WHERE product_id IN (760,520);
UPDATE `biz_process_route` SET product_id = 214 WHERE product_id IN (760,520);
UPDATE `biz_bom_item` SET material_id = 214 WHERE material_id IN (760,520);
UPDATE `biz_bom_item` SET product_id = 214 WHERE product_id IN (760,520);
UPDATE `biz_qc_task` SET material_id = 214 WHERE material_id IN (760,520);
UPDATE `biz_qc_task` SET product_id = 214 WHERE product_id IN (760,520);
UPDATE `biz_defect_record` SET material_id = 214 WHERE material_id IN (760,520);
UPDATE `biz_defect_record` SET product_id = 214 WHERE product_id IN (760,520);
UPDATE `biz_mrp_demand` SET material_id = 214 WHERE material_id IN (760,520);
UPDATE `biz_mrp_demand` SET product_id = 214 WHERE product_id IN (760,520);
UPDATE `biz_outsource_request` SET material_id = 214 WHERE material_id IN (760,520);
UPDATE `biz_outsource_request` SET product_id = 214 WHERE product_id IN (760,520);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (760,520) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 214
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 214 WHERE material_id IN (760,520)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 214) tmp);
DELETE FROM biz_inventory WHERE material_id IN (760,520);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (760,520);

-- ============================================================
-- 底座: 主ID=221(SZ2006000911Y35501CL) -> 正确编码=SZ-200-600-0911-Y355-01CL
-- 合并: id=787(SZ-200-600-0911-Y355-01CL), id=615(ZG0134)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'SZ-200-600-0911-Y355-01CL' AND id != 221;
UPDATE biz_material SET material_no = 'SZ-200-600-0911-Y355-01CL', deleted = 0, status = 'active' WHERE id = 221;

UPDATE `biz_inv_transaction` SET material_id = 221 WHERE material_id IN (787,615);
UPDATE `biz_inv_task_item` SET material_id = 221 WHERE material_id IN (787,615);
UPDATE `biz_inv_history_item` SET material_id = 221 WHERE material_id IN (787,615);
UPDATE `biz_inv_request_item` SET material_id = 221 WHERE material_id IN (787,615);
UPDATE `biz_inv_reservation` SET material_id = 221 WHERE material_id IN (787,615);
UPDATE `biz_inv_transfer_item` SET material_id = 221 WHERE material_id IN (787,615);
UPDATE `biz_inv_stocktaking_item` SET material_id = 221 WHERE material_id IN (787,615);
UPDATE `biz_purchase_request` SET material_id = 221 WHERE material_id IN (787,615);
UPDATE `biz_purchase_order_item` SET material_id = 221 WHERE material_id IN (787,615);
UPDATE `biz_purchase_receipt` SET material_id = 221 WHERE material_id IN (787,615);
UPDATE `biz_purchase_return_item` SET material_id = 221 WHERE material_id IN (787,615);
UPDATE `biz_wo_bom_snapshot` SET material_id = 221 WHERE material_id IN (787,615);
UPDATE `biz_plan_item_material` SET material_id = 221 WHERE material_id IN (787,615);
UPDATE `biz_outsource_delivery_item` SET material_id = 221 WHERE material_id IN (787,615);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 221 WHERE material_id IN (787,615);
UPDATE `biz_sales_order_item` SET product_id = 221 WHERE product_id IN (787,615);
UPDATE `biz_work_order` SET product_id = 221 WHERE product_id IN (787,615);
UPDATE `biz_return_order_item` SET product_id = 221 WHERE product_id IN (787,615);
UPDATE `biz_mrp_production_suggestion` SET product_id = 221 WHERE product_id IN (787,615);
UPDATE `biz_process_route` SET product_id = 221 WHERE product_id IN (787,615);
UPDATE `biz_bom_item` SET material_id = 221 WHERE material_id IN (787,615);
UPDATE `biz_bom_item` SET product_id = 221 WHERE product_id IN (787,615);
UPDATE `biz_qc_task` SET material_id = 221 WHERE material_id IN (787,615);
UPDATE `biz_qc_task` SET product_id = 221 WHERE product_id IN (787,615);
UPDATE `biz_defect_record` SET material_id = 221 WHERE material_id IN (787,615);
UPDATE `biz_defect_record` SET product_id = 221 WHERE product_id IN (787,615);
UPDATE `biz_mrp_demand` SET material_id = 221 WHERE material_id IN (787,615);
UPDATE `biz_mrp_demand` SET product_id = 221 WHERE product_id IN (787,615);
UPDATE `biz_outsource_request` SET material_id = 221 WHERE material_id IN (787,615);
UPDATE `biz_outsource_request` SET product_id = 221 WHERE product_id IN (787,615);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (787,615) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 221
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 221 WHERE material_id IN (787,615)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 221) tmp);
DELETE FROM biz_inventory WHERE material_id IN (787,615);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (787,615);

-- ============================================================
-- 底座: 主ID=229(SZ250750Y35501DC) -> 正确编码=SZ-250-750-Y355-01DC
-- 合并: id=772(SZ-250-750-Y355-01DC), id=522(ZG0041)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'SZ-250-750-Y355-01DC' AND id != 229;
UPDATE biz_material SET material_no = 'SZ-250-750-Y355-01DC', deleted = 0, status = 'active' WHERE id = 229;

UPDATE `biz_inv_transaction` SET material_id = 229 WHERE material_id IN (772,522);
UPDATE `biz_inv_task_item` SET material_id = 229 WHERE material_id IN (772,522);
UPDATE `biz_inv_history_item` SET material_id = 229 WHERE material_id IN (772,522);
UPDATE `biz_inv_request_item` SET material_id = 229 WHERE material_id IN (772,522);
UPDATE `biz_inv_reservation` SET material_id = 229 WHERE material_id IN (772,522);
UPDATE `biz_inv_transfer_item` SET material_id = 229 WHERE material_id IN (772,522);
UPDATE `biz_inv_stocktaking_item` SET material_id = 229 WHERE material_id IN (772,522);
UPDATE `biz_purchase_request` SET material_id = 229 WHERE material_id IN (772,522);
UPDATE `biz_purchase_order_item` SET material_id = 229 WHERE material_id IN (772,522);
UPDATE `biz_purchase_receipt` SET material_id = 229 WHERE material_id IN (772,522);
UPDATE `biz_purchase_return_item` SET material_id = 229 WHERE material_id IN (772,522);
UPDATE `biz_wo_bom_snapshot` SET material_id = 229 WHERE material_id IN (772,522);
UPDATE `biz_plan_item_material` SET material_id = 229 WHERE material_id IN (772,522);
UPDATE `biz_outsource_delivery_item` SET material_id = 229 WHERE material_id IN (772,522);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 229 WHERE material_id IN (772,522);
UPDATE `biz_sales_order_item` SET product_id = 229 WHERE product_id IN (772,522);
UPDATE `biz_work_order` SET product_id = 229 WHERE product_id IN (772,522);
UPDATE `biz_return_order_item` SET product_id = 229 WHERE product_id IN (772,522);
UPDATE `biz_mrp_production_suggestion` SET product_id = 229 WHERE product_id IN (772,522);
UPDATE `biz_process_route` SET product_id = 229 WHERE product_id IN (772,522);
UPDATE `biz_bom_item` SET material_id = 229 WHERE material_id IN (772,522);
UPDATE `biz_bom_item` SET product_id = 229 WHERE product_id IN (772,522);
UPDATE `biz_qc_task` SET material_id = 229 WHERE material_id IN (772,522);
UPDATE `biz_qc_task` SET product_id = 229 WHERE product_id IN (772,522);
UPDATE `biz_defect_record` SET material_id = 229 WHERE material_id IN (772,522);
UPDATE `biz_defect_record` SET product_id = 229 WHERE product_id IN (772,522);
UPDATE `biz_mrp_demand` SET material_id = 229 WHERE material_id IN (772,522);
UPDATE `biz_mrp_demand` SET product_id = 229 WHERE product_id IN (772,522);
UPDATE `biz_outsource_request` SET material_id = 229 WHERE material_id IN (772,522);
UPDATE `biz_outsource_request` SET product_id = 229 WHERE product_id IN (772,522);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (772,522) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 229
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 229 WHERE material_id IN (772,522)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 229) tmp);
DELETE FROM biz_inventory WHERE material_id IN (772,522);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (772,522);

-- ============================================================
-- 支架: 主ID=234(SZ3000108900CHJ) -> 正确编码=SZ-300-0108-900CHJ
-- 合并: id=765(SZ-300-0108-900CHJ), id=593(ZG0112)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'SZ-300-0108-900CHJ' AND id != 234;
UPDATE biz_material SET material_no = 'SZ-300-0108-900CHJ', deleted = 0, status = 'active' WHERE id = 234;

UPDATE `biz_inv_transaction` SET material_id = 234 WHERE material_id IN (765,593);
UPDATE `biz_inv_task_item` SET material_id = 234 WHERE material_id IN (765,593);
UPDATE `biz_inv_history_item` SET material_id = 234 WHERE material_id IN (765,593);
UPDATE `biz_inv_request_item` SET material_id = 234 WHERE material_id IN (765,593);
UPDATE `biz_inv_reservation` SET material_id = 234 WHERE material_id IN (765,593);
UPDATE `biz_inv_transfer_item` SET material_id = 234 WHERE material_id IN (765,593);
UPDATE `biz_inv_stocktaking_item` SET material_id = 234 WHERE material_id IN (765,593);
UPDATE `biz_purchase_request` SET material_id = 234 WHERE material_id IN (765,593);
UPDATE `biz_purchase_order_item` SET material_id = 234 WHERE material_id IN (765,593);
UPDATE `biz_purchase_receipt` SET material_id = 234 WHERE material_id IN (765,593);
UPDATE `biz_purchase_return_item` SET material_id = 234 WHERE material_id IN (765,593);
UPDATE `biz_wo_bom_snapshot` SET material_id = 234 WHERE material_id IN (765,593);
UPDATE `biz_plan_item_material` SET material_id = 234 WHERE material_id IN (765,593);
UPDATE `biz_outsource_delivery_item` SET material_id = 234 WHERE material_id IN (765,593);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 234 WHERE material_id IN (765,593);
UPDATE `biz_sales_order_item` SET product_id = 234 WHERE product_id IN (765,593);
UPDATE `biz_work_order` SET product_id = 234 WHERE product_id IN (765,593);
UPDATE `biz_return_order_item` SET product_id = 234 WHERE product_id IN (765,593);
UPDATE `biz_mrp_production_suggestion` SET product_id = 234 WHERE product_id IN (765,593);
UPDATE `biz_process_route` SET product_id = 234 WHERE product_id IN (765,593);
UPDATE `biz_bom_item` SET material_id = 234 WHERE material_id IN (765,593);
UPDATE `biz_bom_item` SET product_id = 234 WHERE product_id IN (765,593);
UPDATE `biz_qc_task` SET material_id = 234 WHERE material_id IN (765,593);
UPDATE `biz_qc_task` SET product_id = 234 WHERE product_id IN (765,593);
UPDATE `biz_defect_record` SET material_id = 234 WHERE material_id IN (765,593);
UPDATE `biz_defect_record` SET product_id = 234 WHERE product_id IN (765,593);
UPDATE `biz_mrp_demand` SET material_id = 234 WHERE material_id IN (765,593);
UPDATE `biz_mrp_demand` SET product_id = 234 WHERE product_id IN (765,593);
UPDATE `biz_outsource_request` SET material_id = 234 WHERE material_id IN (765,593);
UPDATE `biz_outsource_request` SET product_id = 234 WHERE product_id IN (765,593);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (765,593) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 234
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 234 WHERE material_id IN (765,593)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 234) tmp);
DELETE FROM biz_inventory WHERE material_id IN (765,593);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (765,593);

-- ============================================================
-- 底座: 主ID=235(SZ3000901900C) -> 正确编码=SZ-300-0901-900C
-- 合并: id=766(SZ-300-0901-900C), id=594(ZG0113)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'SZ-300-0901-900C' AND id != 235;
UPDATE biz_material SET material_no = 'SZ-300-0901-900C', deleted = 0, status = 'active' WHERE id = 235;

UPDATE `biz_inv_transaction` SET material_id = 235 WHERE material_id IN (766,594);
UPDATE `biz_inv_task_item` SET material_id = 235 WHERE material_id IN (766,594);
UPDATE `biz_inv_history_item` SET material_id = 235 WHERE material_id IN (766,594);
UPDATE `biz_inv_request_item` SET material_id = 235 WHERE material_id IN (766,594);
UPDATE `biz_inv_reservation` SET material_id = 235 WHERE material_id IN (766,594);
UPDATE `biz_inv_transfer_item` SET material_id = 235 WHERE material_id IN (766,594);
UPDATE `biz_inv_stocktaking_item` SET material_id = 235 WHERE material_id IN (766,594);
UPDATE `biz_purchase_request` SET material_id = 235 WHERE material_id IN (766,594);
UPDATE `biz_purchase_order_item` SET material_id = 235 WHERE material_id IN (766,594);
UPDATE `biz_purchase_receipt` SET material_id = 235 WHERE material_id IN (766,594);
UPDATE `biz_purchase_return_item` SET material_id = 235 WHERE material_id IN (766,594);
UPDATE `biz_wo_bom_snapshot` SET material_id = 235 WHERE material_id IN (766,594);
UPDATE `biz_plan_item_material` SET material_id = 235 WHERE material_id IN (766,594);
UPDATE `biz_outsource_delivery_item` SET material_id = 235 WHERE material_id IN (766,594);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 235 WHERE material_id IN (766,594);
UPDATE `biz_sales_order_item` SET product_id = 235 WHERE product_id IN (766,594);
UPDATE `biz_work_order` SET product_id = 235 WHERE product_id IN (766,594);
UPDATE `biz_return_order_item` SET product_id = 235 WHERE product_id IN (766,594);
UPDATE `biz_mrp_production_suggestion` SET product_id = 235 WHERE product_id IN (766,594);
UPDATE `biz_process_route` SET product_id = 235 WHERE product_id IN (766,594);
UPDATE `biz_bom_item` SET material_id = 235 WHERE material_id IN (766,594);
UPDATE `biz_bom_item` SET product_id = 235 WHERE product_id IN (766,594);
UPDATE `biz_qc_task` SET material_id = 235 WHERE material_id IN (766,594);
UPDATE `biz_qc_task` SET product_id = 235 WHERE product_id IN (766,594);
UPDATE `biz_defect_record` SET material_id = 235 WHERE material_id IN (766,594);
UPDATE `biz_defect_record` SET product_id = 235 WHERE product_id IN (766,594);
UPDATE `biz_mrp_demand` SET material_id = 235 WHERE material_id IN (766,594);
UPDATE `biz_mrp_demand` SET product_id = 235 WHERE product_id IN (766,594);
UPDATE `biz_outsource_request` SET material_id = 235 WHERE material_id IN (766,594);
UPDATE `biz_outsource_request` SET product_id = 235 WHERE product_id IN (766,594);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (766,594) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 235
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 235 WHERE material_id IN (766,594)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 235) tmp);
DELETE FROM biz_inventory WHERE material_id IN (766,594);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (766,594);

-- ============================================================
-- 皮带罩: 主ID=252(TZJ130145001ZV) -> 正确编码=TZJ-1301-450-01ZV
-- 合并: id=629(TZJ-1301-450-01ZV), id=482(ZG0001)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJ-1301-450-01ZV' AND id != 252;
UPDATE biz_material SET material_no = 'TZJ-1301-450-01ZV', deleted = 0, status = 'active' WHERE id = 252;

UPDATE `biz_inv_transaction` SET material_id = 252 WHERE material_id IN (629,482);
UPDATE `biz_inv_task_item` SET material_id = 252 WHERE material_id IN (629,482);
UPDATE `biz_inv_history_item` SET material_id = 252 WHERE material_id IN (629,482);
UPDATE `biz_inv_request_item` SET material_id = 252 WHERE material_id IN (629,482);
UPDATE `biz_inv_reservation` SET material_id = 252 WHERE material_id IN (629,482);
UPDATE `biz_inv_transfer_item` SET material_id = 252 WHERE material_id IN (629,482);
UPDATE `biz_inv_stocktaking_item` SET material_id = 252 WHERE material_id IN (629,482);
UPDATE `biz_purchase_request` SET material_id = 252 WHERE material_id IN (629,482);
UPDATE `biz_purchase_order_item` SET material_id = 252 WHERE material_id IN (629,482);
UPDATE `biz_purchase_receipt` SET material_id = 252 WHERE material_id IN (629,482);
UPDATE `biz_purchase_return_item` SET material_id = 252 WHERE material_id IN (629,482);
UPDATE `biz_wo_bom_snapshot` SET material_id = 252 WHERE material_id IN (629,482);
UPDATE `biz_plan_item_material` SET material_id = 252 WHERE material_id IN (629,482);
UPDATE `biz_outsource_delivery_item` SET material_id = 252 WHERE material_id IN (629,482);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 252 WHERE material_id IN (629,482);
UPDATE `biz_sales_order_item` SET product_id = 252 WHERE product_id IN (629,482);
UPDATE `biz_work_order` SET product_id = 252 WHERE product_id IN (629,482);
UPDATE `biz_return_order_item` SET product_id = 252 WHERE product_id IN (629,482);
UPDATE `biz_mrp_production_suggestion` SET product_id = 252 WHERE product_id IN (629,482);
UPDATE `biz_process_route` SET product_id = 252 WHERE product_id IN (629,482);
UPDATE `biz_bom_item` SET material_id = 252 WHERE material_id IN (629,482);
UPDATE `biz_bom_item` SET product_id = 252 WHERE product_id IN (629,482);
UPDATE `biz_qc_task` SET material_id = 252 WHERE material_id IN (629,482);
UPDATE `biz_qc_task` SET product_id = 252 WHERE product_id IN (629,482);
UPDATE `biz_defect_record` SET material_id = 252 WHERE material_id IN (629,482);
UPDATE `biz_defect_record` SET product_id = 252 WHERE product_id IN (629,482);
UPDATE `biz_mrp_demand` SET material_id = 252 WHERE material_id IN (629,482);
UPDATE `biz_mrp_demand` SET product_id = 252 WHERE product_id IN (629,482);
UPDATE `biz_outsource_request` SET material_id = 252 WHERE material_id IN (629,482);
UPDATE `biz_outsource_request` SET product_id = 252 WHERE product_id IN (629,482);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (629,482) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 252
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 252 WHERE material_id IN (629,482)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 252) tmp);
DELETE FROM biz_inventory WHERE material_id IN (629,482);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (629,482);

-- ============================================================
-- 皮带罩: 主ID=253(TZJ130155001ZV) -> 正确编码=TZJ-1301-550-01ZV
-- 合并: id=630(TZJ-1301-550-01ZV), id=483(ZG0002)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJ-1301-550-01ZV' AND id != 253;
UPDATE biz_material SET material_no = 'TZJ-1301-550-01ZV', deleted = 0, status = 'active' WHERE id = 253;

UPDATE `biz_inv_transaction` SET material_id = 253 WHERE material_id IN (630,483);
UPDATE `biz_inv_task_item` SET material_id = 253 WHERE material_id IN (630,483);
UPDATE `biz_inv_history_item` SET material_id = 253 WHERE material_id IN (630,483);
UPDATE `biz_inv_request_item` SET material_id = 253 WHERE material_id IN (630,483);
UPDATE `biz_inv_reservation` SET material_id = 253 WHERE material_id IN (630,483);
UPDATE `biz_inv_transfer_item` SET material_id = 253 WHERE material_id IN (630,483);
UPDATE `biz_inv_stocktaking_item` SET material_id = 253 WHERE material_id IN (630,483);
UPDATE `biz_purchase_request` SET material_id = 253 WHERE material_id IN (630,483);
UPDATE `biz_purchase_order_item` SET material_id = 253 WHERE material_id IN (630,483);
UPDATE `biz_purchase_receipt` SET material_id = 253 WHERE material_id IN (630,483);
UPDATE `biz_purchase_return_item` SET material_id = 253 WHERE material_id IN (630,483);
UPDATE `biz_wo_bom_snapshot` SET material_id = 253 WHERE material_id IN (630,483);
UPDATE `biz_plan_item_material` SET material_id = 253 WHERE material_id IN (630,483);
UPDATE `biz_outsource_delivery_item` SET material_id = 253 WHERE material_id IN (630,483);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 253 WHERE material_id IN (630,483);
UPDATE `biz_sales_order_item` SET product_id = 253 WHERE product_id IN (630,483);
UPDATE `biz_work_order` SET product_id = 253 WHERE product_id IN (630,483);
UPDATE `biz_return_order_item` SET product_id = 253 WHERE product_id IN (630,483);
UPDATE `biz_mrp_production_suggestion` SET product_id = 253 WHERE product_id IN (630,483);
UPDATE `biz_process_route` SET product_id = 253 WHERE product_id IN (630,483);
UPDATE `biz_bom_item` SET material_id = 253 WHERE material_id IN (630,483);
UPDATE `biz_bom_item` SET product_id = 253 WHERE product_id IN (630,483);
UPDATE `biz_qc_task` SET material_id = 253 WHERE material_id IN (630,483);
UPDATE `biz_qc_task` SET product_id = 253 WHERE product_id IN (630,483);
UPDATE `biz_defect_record` SET material_id = 253 WHERE material_id IN (630,483);
UPDATE `biz_defect_record` SET product_id = 253 WHERE product_id IN (630,483);
UPDATE `biz_mrp_demand` SET material_id = 253 WHERE material_id IN (630,483);
UPDATE `biz_mrp_demand` SET product_id = 253 WHERE product_id IN (630,483);
UPDATE `biz_outsource_request` SET material_id = 253 WHERE material_id IN (630,483);
UPDATE `biz_outsource_request` SET product_id = 253 WHERE product_id IN (630,483);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (630,483) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 253
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 253 WHERE material_id IN (630,483)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 253) tmp);
DELETE FROM biz_inventory WHERE material_id IN (630,483);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (630,483);

-- ============================================================
-- 皮带罩: 主ID=254(TZJ130155002ZV) -> 正确编码=TZJ-1301-550-02ZV
-- 合并: id=803(TZJ-1301-550-02ZV), id=531(ZG0050)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJ-1301-550-02ZV' AND id != 254;
UPDATE biz_material SET material_no = 'TZJ-1301-550-02ZV', deleted = 0, status = 'active' WHERE id = 254;

UPDATE `biz_inv_transaction` SET material_id = 254 WHERE material_id IN (803,531);
UPDATE `biz_inv_task_item` SET material_id = 254 WHERE material_id IN (803,531);
UPDATE `biz_inv_history_item` SET material_id = 254 WHERE material_id IN (803,531);
UPDATE `biz_inv_request_item` SET material_id = 254 WHERE material_id IN (803,531);
UPDATE `biz_inv_reservation` SET material_id = 254 WHERE material_id IN (803,531);
UPDATE `biz_inv_transfer_item` SET material_id = 254 WHERE material_id IN (803,531);
UPDATE `biz_inv_stocktaking_item` SET material_id = 254 WHERE material_id IN (803,531);
UPDATE `biz_purchase_request` SET material_id = 254 WHERE material_id IN (803,531);
UPDATE `biz_purchase_order_item` SET material_id = 254 WHERE material_id IN (803,531);
UPDATE `biz_purchase_receipt` SET material_id = 254 WHERE material_id IN (803,531);
UPDATE `biz_purchase_return_item` SET material_id = 254 WHERE material_id IN (803,531);
UPDATE `biz_wo_bom_snapshot` SET material_id = 254 WHERE material_id IN (803,531);
UPDATE `biz_plan_item_material` SET material_id = 254 WHERE material_id IN (803,531);
UPDATE `biz_outsource_delivery_item` SET material_id = 254 WHERE material_id IN (803,531);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 254 WHERE material_id IN (803,531);
UPDATE `biz_sales_order_item` SET product_id = 254 WHERE product_id IN (803,531);
UPDATE `biz_work_order` SET product_id = 254 WHERE product_id IN (803,531);
UPDATE `biz_return_order_item` SET product_id = 254 WHERE product_id IN (803,531);
UPDATE `biz_mrp_production_suggestion` SET product_id = 254 WHERE product_id IN (803,531);
UPDATE `biz_process_route` SET product_id = 254 WHERE product_id IN (803,531);
UPDATE `biz_bom_item` SET material_id = 254 WHERE material_id IN (803,531);
UPDATE `biz_bom_item` SET product_id = 254 WHERE product_id IN (803,531);
UPDATE `biz_qc_task` SET material_id = 254 WHERE material_id IN (803,531);
UPDATE `biz_qc_task` SET product_id = 254 WHERE product_id IN (803,531);
UPDATE `biz_defect_record` SET material_id = 254 WHERE material_id IN (803,531);
UPDATE `biz_defect_record` SET product_id = 254 WHERE product_id IN (803,531);
UPDATE `biz_mrp_demand` SET material_id = 254 WHERE material_id IN (803,531);
UPDATE `biz_mrp_demand` SET product_id = 254 WHERE product_id IN (803,531);
UPDATE `biz_outsource_request` SET material_id = 254 WHERE material_id IN (803,531);
UPDATE `biz_outsource_request` SET product_id = 254 WHERE product_id IN (803,531);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (803,531) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 254
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 254 WHERE material_id IN (803,531)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 254) tmp);
DELETE FROM biz_inventory WHERE material_id IN (803,531);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (803,531);

-- ============================================================
-- 皮带罩: 主ID=255(TZJ130168001ZV) -> 正确编码=TZJ-1301-680-01ZV
-- 合并: id=714(TZJ-1301-680-01ZV), id=530(ZG0049)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJ-1301-680-01ZV' AND id != 255;
UPDATE biz_material SET material_no = 'TZJ-1301-680-01ZV', deleted = 0, status = 'active' WHERE id = 255;

UPDATE `biz_inv_transaction` SET material_id = 255 WHERE material_id IN (714,530);
UPDATE `biz_inv_task_item` SET material_id = 255 WHERE material_id IN (714,530);
UPDATE `biz_inv_history_item` SET material_id = 255 WHERE material_id IN (714,530);
UPDATE `biz_inv_request_item` SET material_id = 255 WHERE material_id IN (714,530);
UPDATE `biz_inv_reservation` SET material_id = 255 WHERE material_id IN (714,530);
UPDATE `biz_inv_transfer_item` SET material_id = 255 WHERE material_id IN (714,530);
UPDATE `biz_inv_stocktaking_item` SET material_id = 255 WHERE material_id IN (714,530);
UPDATE `biz_purchase_request` SET material_id = 255 WHERE material_id IN (714,530);
UPDATE `biz_purchase_order_item` SET material_id = 255 WHERE material_id IN (714,530);
UPDATE `biz_purchase_receipt` SET material_id = 255 WHERE material_id IN (714,530);
UPDATE `biz_purchase_return_item` SET material_id = 255 WHERE material_id IN (714,530);
UPDATE `biz_wo_bom_snapshot` SET material_id = 255 WHERE material_id IN (714,530);
UPDATE `biz_plan_item_material` SET material_id = 255 WHERE material_id IN (714,530);
UPDATE `biz_outsource_delivery_item` SET material_id = 255 WHERE material_id IN (714,530);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 255 WHERE material_id IN (714,530);
UPDATE `biz_sales_order_item` SET product_id = 255 WHERE product_id IN (714,530);
UPDATE `biz_work_order` SET product_id = 255 WHERE product_id IN (714,530);
UPDATE `biz_return_order_item` SET product_id = 255 WHERE product_id IN (714,530);
UPDATE `biz_mrp_production_suggestion` SET product_id = 255 WHERE product_id IN (714,530);
UPDATE `biz_process_route` SET product_id = 255 WHERE product_id IN (714,530);
UPDATE `biz_bom_item` SET material_id = 255 WHERE material_id IN (714,530);
UPDATE `biz_bom_item` SET product_id = 255 WHERE product_id IN (714,530);
UPDATE `biz_qc_task` SET material_id = 255 WHERE material_id IN (714,530);
UPDATE `biz_qc_task` SET product_id = 255 WHERE product_id IN (714,530);
UPDATE `biz_defect_record` SET material_id = 255 WHERE material_id IN (714,530);
UPDATE `biz_defect_record` SET product_id = 255 WHERE product_id IN (714,530);
UPDATE `biz_mrp_demand` SET material_id = 255 WHERE material_id IN (714,530);
UPDATE `biz_mrp_demand` SET product_id = 255 WHERE product_id IN (714,530);
UPDATE `biz_outsource_request` SET material_id = 255 WHERE material_id IN (714,530);
UPDATE `biz_outsource_request` SET product_id = 255 WHERE product_id IN (714,530);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (714,530) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 255
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 255 WHERE material_id IN (714,530)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 255) tmp);
DELETE FROM biz_inventory WHERE material_id IN (714,530);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (714,530);

-- ============================================================
-- 支架: 主ID=257(TZJ160101ZV) -> 正确编码=TZJ-1601-01ZV
-- 合并: id=715(TZJ-1601-01ZV), id=571(ZG0090)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJ-1601-01ZV' AND id != 257;
UPDATE biz_material SET material_no = 'TZJ-1601-01ZV', deleted = 0, status = 'active' WHERE id = 257;

UPDATE `biz_inv_transaction` SET material_id = 257 WHERE material_id IN (715,571);
UPDATE `biz_inv_task_item` SET material_id = 257 WHERE material_id IN (715,571);
UPDATE `biz_inv_history_item` SET material_id = 257 WHERE material_id IN (715,571);
UPDATE `biz_inv_request_item` SET material_id = 257 WHERE material_id IN (715,571);
UPDATE `biz_inv_reservation` SET material_id = 257 WHERE material_id IN (715,571);
UPDATE `biz_inv_transfer_item` SET material_id = 257 WHERE material_id IN (715,571);
UPDATE `biz_inv_stocktaking_item` SET material_id = 257 WHERE material_id IN (715,571);
UPDATE `biz_purchase_request` SET material_id = 257 WHERE material_id IN (715,571);
UPDATE `biz_purchase_order_item` SET material_id = 257 WHERE material_id IN (715,571);
UPDATE `biz_purchase_receipt` SET material_id = 257 WHERE material_id IN (715,571);
UPDATE `biz_purchase_return_item` SET material_id = 257 WHERE material_id IN (715,571);
UPDATE `biz_wo_bom_snapshot` SET material_id = 257 WHERE material_id IN (715,571);
UPDATE `biz_plan_item_material` SET material_id = 257 WHERE material_id IN (715,571);
UPDATE `biz_outsource_delivery_item` SET material_id = 257 WHERE material_id IN (715,571);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 257 WHERE material_id IN (715,571);
UPDATE `biz_sales_order_item` SET product_id = 257 WHERE product_id IN (715,571);
UPDATE `biz_work_order` SET product_id = 257 WHERE product_id IN (715,571);
UPDATE `biz_return_order_item` SET product_id = 257 WHERE product_id IN (715,571);
UPDATE `biz_mrp_production_suggestion` SET product_id = 257 WHERE product_id IN (715,571);
UPDATE `biz_process_route` SET product_id = 257 WHERE product_id IN (715,571);
UPDATE `biz_bom_item` SET material_id = 257 WHERE material_id IN (715,571);
UPDATE `biz_bom_item` SET product_id = 257 WHERE product_id IN (715,571);
UPDATE `biz_qc_task` SET material_id = 257 WHERE material_id IN (715,571);
UPDATE `biz_qc_task` SET product_id = 257 WHERE product_id IN (715,571);
UPDATE `biz_defect_record` SET material_id = 257 WHERE material_id IN (715,571);
UPDATE `biz_defect_record` SET product_id = 257 WHERE product_id IN (715,571);
UPDATE `biz_mrp_demand` SET material_id = 257 WHERE material_id IN (715,571);
UPDATE `biz_mrp_demand` SET product_id = 257 WHERE product_id IN (715,571);
UPDATE `biz_outsource_request` SET material_id = 257 WHERE material_id IN (715,571);
UPDATE `biz_outsource_request` SET product_id = 257 WHERE product_id IN (715,571);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (715,571) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 257
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 257 WHERE material_id IN (715,571)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 257) tmp);
DELETE FROM biz_inventory WHERE material_id IN (715,571);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (715,571);

-- ============================================================
-- 支架: 主ID=259(TZJ160102ZV) -> 正确编码=TZJ-1601-02ZV
-- 合并: id=751(TZJ-1601-02ZV), id=581(ZG0100)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJ-1601-02ZV' AND id != 259;
UPDATE biz_material SET material_no = 'TZJ-1601-02ZV', deleted = 0, status = 'active' WHERE id = 259;

UPDATE `biz_inv_transaction` SET material_id = 259 WHERE material_id IN (751,581);
UPDATE `biz_inv_task_item` SET material_id = 259 WHERE material_id IN (751,581);
UPDATE `biz_inv_history_item` SET material_id = 259 WHERE material_id IN (751,581);
UPDATE `biz_inv_request_item` SET material_id = 259 WHERE material_id IN (751,581);
UPDATE `biz_inv_reservation` SET material_id = 259 WHERE material_id IN (751,581);
UPDATE `biz_inv_transfer_item` SET material_id = 259 WHERE material_id IN (751,581);
UPDATE `biz_inv_stocktaking_item` SET material_id = 259 WHERE material_id IN (751,581);
UPDATE `biz_purchase_request` SET material_id = 259 WHERE material_id IN (751,581);
UPDATE `biz_purchase_order_item` SET material_id = 259 WHERE material_id IN (751,581);
UPDATE `biz_purchase_receipt` SET material_id = 259 WHERE material_id IN (751,581);
UPDATE `biz_purchase_return_item` SET material_id = 259 WHERE material_id IN (751,581);
UPDATE `biz_wo_bom_snapshot` SET material_id = 259 WHERE material_id IN (751,581);
UPDATE `biz_plan_item_material` SET material_id = 259 WHERE material_id IN (751,581);
UPDATE `biz_outsource_delivery_item` SET material_id = 259 WHERE material_id IN (751,581);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 259 WHERE material_id IN (751,581);
UPDATE `biz_sales_order_item` SET product_id = 259 WHERE product_id IN (751,581);
UPDATE `biz_work_order` SET product_id = 259 WHERE product_id IN (751,581);
UPDATE `biz_return_order_item` SET product_id = 259 WHERE product_id IN (751,581);
UPDATE `biz_mrp_production_suggestion` SET product_id = 259 WHERE product_id IN (751,581);
UPDATE `biz_process_route` SET product_id = 259 WHERE product_id IN (751,581);
UPDATE `biz_bom_item` SET material_id = 259 WHERE material_id IN (751,581);
UPDATE `biz_bom_item` SET product_id = 259 WHERE product_id IN (751,581);
UPDATE `biz_qc_task` SET material_id = 259 WHERE material_id IN (751,581);
UPDATE `biz_qc_task` SET product_id = 259 WHERE product_id IN (751,581);
UPDATE `biz_defect_record` SET material_id = 259 WHERE material_id IN (751,581);
UPDATE `biz_defect_record` SET product_id = 259 WHERE product_id IN (751,581);
UPDATE `biz_mrp_demand` SET material_id = 259 WHERE material_id IN (751,581);
UPDATE `biz_mrp_demand` SET product_id = 259 WHERE product_id IN (751,581);
UPDATE `biz_outsource_request` SET material_id = 259 WHERE material_id IN (751,581);
UPDATE `biz_outsource_request` SET product_id = 259 WHERE product_id IN (751,581);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (751,581) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 259
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 259 WHERE material_id IN (751,581)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 259) tmp);
DELETE FROM biz_inventory WHERE material_id IN (751,581);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (751,581);

-- ============================================================
-- 支架: 主ID=260(TZJ160103ZV) -> 正确编码=TZJ-1601-03ZV
-- 合并: id=750(TZJ-1601-03ZV), id=580(ZG0099)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJ-1601-03ZV' AND id != 260;
UPDATE biz_material SET material_no = 'TZJ-1601-03ZV', deleted = 0, status = 'active' WHERE id = 260;

UPDATE `biz_inv_transaction` SET material_id = 260 WHERE material_id IN (750,580);
UPDATE `biz_inv_task_item` SET material_id = 260 WHERE material_id IN (750,580);
UPDATE `biz_inv_history_item` SET material_id = 260 WHERE material_id IN (750,580);
UPDATE `biz_inv_request_item` SET material_id = 260 WHERE material_id IN (750,580);
UPDATE `biz_inv_reservation` SET material_id = 260 WHERE material_id IN (750,580);
UPDATE `biz_inv_transfer_item` SET material_id = 260 WHERE material_id IN (750,580);
UPDATE `biz_inv_stocktaking_item` SET material_id = 260 WHERE material_id IN (750,580);
UPDATE `biz_purchase_request` SET material_id = 260 WHERE material_id IN (750,580);
UPDATE `biz_purchase_order_item` SET material_id = 260 WHERE material_id IN (750,580);
UPDATE `biz_purchase_receipt` SET material_id = 260 WHERE material_id IN (750,580);
UPDATE `biz_purchase_return_item` SET material_id = 260 WHERE material_id IN (750,580);
UPDATE `biz_wo_bom_snapshot` SET material_id = 260 WHERE material_id IN (750,580);
UPDATE `biz_plan_item_material` SET material_id = 260 WHERE material_id IN (750,580);
UPDATE `biz_outsource_delivery_item` SET material_id = 260 WHERE material_id IN (750,580);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 260 WHERE material_id IN (750,580);
UPDATE `biz_sales_order_item` SET product_id = 260 WHERE product_id IN (750,580);
UPDATE `biz_work_order` SET product_id = 260 WHERE product_id IN (750,580);
UPDATE `biz_return_order_item` SET product_id = 260 WHERE product_id IN (750,580);
UPDATE `biz_mrp_production_suggestion` SET product_id = 260 WHERE product_id IN (750,580);
UPDATE `biz_process_route` SET product_id = 260 WHERE product_id IN (750,580);
UPDATE `biz_bom_item` SET material_id = 260 WHERE material_id IN (750,580);
UPDATE `biz_bom_item` SET product_id = 260 WHERE product_id IN (750,580);
UPDATE `biz_qc_task` SET material_id = 260 WHERE material_id IN (750,580);
UPDATE `biz_qc_task` SET product_id = 260 WHERE product_id IN (750,580);
UPDATE `biz_defect_record` SET material_id = 260 WHERE material_id IN (750,580);
UPDATE `biz_defect_record` SET product_id = 260 WHERE product_id IN (750,580);
UPDATE `biz_mrp_demand` SET material_id = 260 WHERE material_id IN (750,580);
UPDATE `biz_mrp_demand` SET product_id = 260 WHERE product_id IN (750,580);
UPDATE `biz_outsource_request` SET material_id = 260 WHERE material_id IN (750,580);
UPDATE `biz_outsource_request` SET product_id = 260 WHERE product_id IN (750,580);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (750,580) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 260
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 260 WHERE material_id IN (750,580)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 260) tmp);
DELETE FROM biz_inventory WHERE material_id IN (750,580);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (750,580);

-- ============================================================
-- 电机底座: 主ID=266(TZJ170101ZV) -> 正确编码=TZJ-1701-01ZV
-- 合并: id=716(TZJ-1701-01ZV), id=572(ZG0091)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJ-1701-01ZV' AND id != 266;
UPDATE biz_material SET material_no = 'TZJ-1701-01ZV', deleted = 0, status = 'active' WHERE id = 266;

UPDATE `biz_inv_transaction` SET material_id = 266 WHERE material_id IN (716,572);
UPDATE `biz_inv_task_item` SET material_id = 266 WHERE material_id IN (716,572);
UPDATE `biz_inv_history_item` SET material_id = 266 WHERE material_id IN (716,572);
UPDATE `biz_inv_request_item` SET material_id = 266 WHERE material_id IN (716,572);
UPDATE `biz_inv_reservation` SET material_id = 266 WHERE material_id IN (716,572);
UPDATE `biz_inv_transfer_item` SET material_id = 266 WHERE material_id IN (716,572);
UPDATE `biz_inv_stocktaking_item` SET material_id = 266 WHERE material_id IN (716,572);
UPDATE `biz_purchase_request` SET material_id = 266 WHERE material_id IN (716,572);
UPDATE `biz_purchase_order_item` SET material_id = 266 WHERE material_id IN (716,572);
UPDATE `biz_purchase_receipt` SET material_id = 266 WHERE material_id IN (716,572);
UPDATE `biz_purchase_return_item` SET material_id = 266 WHERE material_id IN (716,572);
UPDATE `biz_wo_bom_snapshot` SET material_id = 266 WHERE material_id IN (716,572);
UPDATE `biz_plan_item_material` SET material_id = 266 WHERE material_id IN (716,572);
UPDATE `biz_outsource_delivery_item` SET material_id = 266 WHERE material_id IN (716,572);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 266 WHERE material_id IN (716,572);
UPDATE `biz_sales_order_item` SET product_id = 266 WHERE product_id IN (716,572);
UPDATE `biz_work_order` SET product_id = 266 WHERE product_id IN (716,572);
UPDATE `biz_return_order_item` SET product_id = 266 WHERE product_id IN (716,572);
UPDATE `biz_mrp_production_suggestion` SET product_id = 266 WHERE product_id IN (716,572);
UPDATE `biz_process_route` SET product_id = 266 WHERE product_id IN (716,572);
UPDATE `biz_bom_item` SET material_id = 266 WHERE material_id IN (716,572);
UPDATE `biz_bom_item` SET product_id = 266 WHERE product_id IN (716,572);
UPDATE `biz_qc_task` SET material_id = 266 WHERE material_id IN (716,572);
UPDATE `biz_qc_task` SET product_id = 266 WHERE product_id IN (716,572);
UPDATE `biz_defect_record` SET material_id = 266 WHERE material_id IN (716,572);
UPDATE `biz_defect_record` SET product_id = 266 WHERE product_id IN (716,572);
UPDATE `biz_mrp_demand` SET material_id = 266 WHERE material_id IN (716,572);
UPDATE `biz_mrp_demand` SET product_id = 266 WHERE product_id IN (716,572);
UPDATE `biz_outsource_request` SET material_id = 266 WHERE material_id IN (716,572);
UPDATE `biz_outsource_request` SET product_id = 266 WHERE product_id IN (716,572);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (716,572) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 266
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 266 WHERE material_id IN (716,572)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 266) tmp);
DELETE FROM biz_inventory WHERE material_id IN (716,572);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (716,572);

-- ============================================================
-- 电机座: 主ID=267(TZJ170102ZV) -> 正确编码=TZJ-1701-02ZV
-- 合并: id=754(TZJ-1701-02ZV), id=584(ZG0103)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJ-1701-02ZV' AND id != 267;
UPDATE biz_material SET material_no = 'TZJ-1701-02ZV', deleted = 0, status = 'active' WHERE id = 267;

UPDATE `biz_inv_transaction` SET material_id = 267 WHERE material_id IN (754,584);
UPDATE `biz_inv_task_item` SET material_id = 267 WHERE material_id IN (754,584);
UPDATE `biz_inv_history_item` SET material_id = 267 WHERE material_id IN (754,584);
UPDATE `biz_inv_request_item` SET material_id = 267 WHERE material_id IN (754,584);
UPDATE `biz_inv_reservation` SET material_id = 267 WHERE material_id IN (754,584);
UPDATE `biz_inv_transfer_item` SET material_id = 267 WHERE material_id IN (754,584);
UPDATE `biz_inv_stocktaking_item` SET material_id = 267 WHERE material_id IN (754,584);
UPDATE `biz_purchase_request` SET material_id = 267 WHERE material_id IN (754,584);
UPDATE `biz_purchase_order_item` SET material_id = 267 WHERE material_id IN (754,584);
UPDATE `biz_purchase_receipt` SET material_id = 267 WHERE material_id IN (754,584);
UPDATE `biz_purchase_return_item` SET material_id = 267 WHERE material_id IN (754,584);
UPDATE `biz_wo_bom_snapshot` SET material_id = 267 WHERE material_id IN (754,584);
UPDATE `biz_plan_item_material` SET material_id = 267 WHERE material_id IN (754,584);
UPDATE `biz_outsource_delivery_item` SET material_id = 267 WHERE material_id IN (754,584);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 267 WHERE material_id IN (754,584);
UPDATE `biz_sales_order_item` SET product_id = 267 WHERE product_id IN (754,584);
UPDATE `biz_work_order` SET product_id = 267 WHERE product_id IN (754,584);
UPDATE `biz_return_order_item` SET product_id = 267 WHERE product_id IN (754,584);
UPDATE `biz_mrp_production_suggestion` SET product_id = 267 WHERE product_id IN (754,584);
UPDATE `biz_process_route` SET product_id = 267 WHERE product_id IN (754,584);
UPDATE `biz_bom_item` SET material_id = 267 WHERE material_id IN (754,584);
UPDATE `biz_bom_item` SET product_id = 267 WHERE product_id IN (754,584);
UPDATE `biz_qc_task` SET material_id = 267 WHERE material_id IN (754,584);
UPDATE `biz_qc_task` SET product_id = 267 WHERE product_id IN (754,584);
UPDATE `biz_defect_record` SET material_id = 267 WHERE material_id IN (754,584);
UPDATE `biz_defect_record` SET product_id = 267 WHERE product_id IN (754,584);
UPDATE `biz_mrp_demand` SET material_id = 267 WHERE material_id IN (754,584);
UPDATE `biz_mrp_demand` SET product_id = 267 WHERE product_id IN (754,584);
UPDATE `biz_outsource_request` SET material_id = 267 WHERE material_id IN (754,584);
UPDATE `biz_outsource_request` SET product_id = 267 WHERE product_id IN (754,584);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (754,584) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 267
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 267 WHERE material_id IN (754,584)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 267) tmp);
DELETE FROM biz_inventory WHERE material_id IN (754,584);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (754,584);

-- ============================================================
-- 电机座: 主ID=268(TZJ170103ZV) -> 正确编码=TZJ-1701-03ZV
-- 合并: id=756(TZJ-1701-03ZV), id=586(ZG0105)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJ-1701-03ZV' AND id != 268;
UPDATE biz_material SET material_no = 'TZJ-1701-03ZV', deleted = 0, status = 'active' WHERE id = 268;

UPDATE `biz_inv_transaction` SET material_id = 268 WHERE material_id IN (756,586);
UPDATE `biz_inv_task_item` SET material_id = 268 WHERE material_id IN (756,586);
UPDATE `biz_inv_history_item` SET material_id = 268 WHERE material_id IN (756,586);
UPDATE `biz_inv_request_item` SET material_id = 268 WHERE material_id IN (756,586);
UPDATE `biz_inv_reservation` SET material_id = 268 WHERE material_id IN (756,586);
UPDATE `biz_inv_transfer_item` SET material_id = 268 WHERE material_id IN (756,586);
UPDATE `biz_inv_stocktaking_item` SET material_id = 268 WHERE material_id IN (756,586);
UPDATE `biz_purchase_request` SET material_id = 268 WHERE material_id IN (756,586);
UPDATE `biz_purchase_order_item` SET material_id = 268 WHERE material_id IN (756,586);
UPDATE `biz_purchase_receipt` SET material_id = 268 WHERE material_id IN (756,586);
UPDATE `biz_purchase_return_item` SET material_id = 268 WHERE material_id IN (756,586);
UPDATE `biz_wo_bom_snapshot` SET material_id = 268 WHERE material_id IN (756,586);
UPDATE `biz_plan_item_material` SET material_id = 268 WHERE material_id IN (756,586);
UPDATE `biz_outsource_delivery_item` SET material_id = 268 WHERE material_id IN (756,586);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 268 WHERE material_id IN (756,586);
UPDATE `biz_sales_order_item` SET product_id = 268 WHERE product_id IN (756,586);
UPDATE `biz_work_order` SET product_id = 268 WHERE product_id IN (756,586);
UPDATE `biz_return_order_item` SET product_id = 268 WHERE product_id IN (756,586);
UPDATE `biz_mrp_production_suggestion` SET product_id = 268 WHERE product_id IN (756,586);
UPDATE `biz_process_route` SET product_id = 268 WHERE product_id IN (756,586);
UPDATE `biz_bom_item` SET material_id = 268 WHERE material_id IN (756,586);
UPDATE `biz_bom_item` SET product_id = 268 WHERE product_id IN (756,586);
UPDATE `biz_qc_task` SET material_id = 268 WHERE material_id IN (756,586);
UPDATE `biz_qc_task` SET product_id = 268 WHERE product_id IN (756,586);
UPDATE `biz_defect_record` SET material_id = 268 WHERE material_id IN (756,586);
UPDATE `biz_defect_record` SET product_id = 268 WHERE product_id IN (756,586);
UPDATE `biz_mrp_demand` SET material_id = 268 WHERE material_id IN (756,586);
UPDATE `biz_mrp_demand` SET product_id = 268 WHERE product_id IN (756,586);
UPDATE `biz_outsource_request` SET material_id = 268 WHERE material_id IN (756,586);
UPDATE `biz_outsource_request` SET product_id = 268 WHERE product_id IN (756,586);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (756,586) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 268
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 268 WHERE material_id IN (756,586)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 268) tmp);
DELETE FROM biz_inventory WHERE material_id IN (756,586);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (756,586);

-- ============================================================
-- 底座: 主ID=275(TZJC091114001CVA) -> 正确编码=TZJC-0911-140-01CVA
-- 合并: id=651(TZJC-0911-140-01CVA), id=554(ZG0073)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJC-0911-140-01CVA' AND id != 275;
UPDATE biz_material SET material_no = 'TZJC-0911-140-01CVA', deleted = 0, status = 'active' WHERE id = 275;

UPDATE `biz_inv_transaction` SET material_id = 275 WHERE material_id IN (651,554);
UPDATE `biz_inv_task_item` SET material_id = 275 WHERE material_id IN (651,554);
UPDATE `biz_inv_history_item` SET material_id = 275 WHERE material_id IN (651,554);
UPDATE `biz_inv_request_item` SET material_id = 275 WHERE material_id IN (651,554);
UPDATE `biz_inv_reservation` SET material_id = 275 WHERE material_id IN (651,554);
UPDATE `biz_inv_transfer_item` SET material_id = 275 WHERE material_id IN (651,554);
UPDATE `biz_inv_stocktaking_item` SET material_id = 275 WHERE material_id IN (651,554);
UPDATE `biz_purchase_request` SET material_id = 275 WHERE material_id IN (651,554);
UPDATE `biz_purchase_order_item` SET material_id = 275 WHERE material_id IN (651,554);
UPDATE `biz_purchase_receipt` SET material_id = 275 WHERE material_id IN (651,554);
UPDATE `biz_purchase_return_item` SET material_id = 275 WHERE material_id IN (651,554);
UPDATE `biz_wo_bom_snapshot` SET material_id = 275 WHERE material_id IN (651,554);
UPDATE `biz_plan_item_material` SET material_id = 275 WHERE material_id IN (651,554);
UPDATE `biz_outsource_delivery_item` SET material_id = 275 WHERE material_id IN (651,554);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 275 WHERE material_id IN (651,554);
UPDATE `biz_sales_order_item` SET product_id = 275 WHERE product_id IN (651,554);
UPDATE `biz_work_order` SET product_id = 275 WHERE product_id IN (651,554);
UPDATE `biz_return_order_item` SET product_id = 275 WHERE product_id IN (651,554);
UPDATE `biz_mrp_production_suggestion` SET product_id = 275 WHERE product_id IN (651,554);
UPDATE `biz_process_route` SET product_id = 275 WHERE product_id IN (651,554);
UPDATE `biz_bom_item` SET material_id = 275 WHERE material_id IN (651,554);
UPDATE `biz_bom_item` SET product_id = 275 WHERE product_id IN (651,554);
UPDATE `biz_qc_task` SET material_id = 275 WHERE material_id IN (651,554);
UPDATE `biz_qc_task` SET product_id = 275 WHERE product_id IN (651,554);
UPDATE `biz_defect_record` SET material_id = 275 WHERE material_id IN (651,554);
UPDATE `biz_defect_record` SET product_id = 275 WHERE product_id IN (651,554);
UPDATE `biz_mrp_demand` SET material_id = 275 WHERE material_id IN (651,554);
UPDATE `biz_mrp_demand` SET product_id = 275 WHERE product_id IN (651,554);
UPDATE `biz_outsource_request` SET material_id = 275 WHERE material_id IN (651,554);
UPDATE `biz_outsource_request` SET product_id = 275 WHERE product_id IN (651,554);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (651,554) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 275
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 275 WHERE material_id IN (651,554)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 275) tmp);
DELETE FROM biz_inventory WHERE material_id IN (651,554);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (651,554);

-- ============================================================
-- 皮带罩: 主ID=280(TZJC130140001CV) -> 正确编码=TZJC-1301-400-01CV
-- 合并: id=654(TZJC-1301-400-01CV), id=557(ZG0076)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJC-1301-400-01CV' AND id != 280;
UPDATE biz_material SET material_no = 'TZJC-1301-400-01CV', deleted = 0, status = 'active' WHERE id = 280;

UPDATE `biz_inv_transaction` SET material_id = 280 WHERE material_id IN (654,557);
UPDATE `biz_inv_task_item` SET material_id = 280 WHERE material_id IN (654,557);
UPDATE `biz_inv_history_item` SET material_id = 280 WHERE material_id IN (654,557);
UPDATE `biz_inv_request_item` SET material_id = 280 WHERE material_id IN (654,557);
UPDATE `biz_inv_reservation` SET material_id = 280 WHERE material_id IN (654,557);
UPDATE `biz_inv_transfer_item` SET material_id = 280 WHERE material_id IN (654,557);
UPDATE `biz_inv_stocktaking_item` SET material_id = 280 WHERE material_id IN (654,557);
UPDATE `biz_purchase_request` SET material_id = 280 WHERE material_id IN (654,557);
UPDATE `biz_purchase_order_item` SET material_id = 280 WHERE material_id IN (654,557);
UPDATE `biz_purchase_receipt` SET material_id = 280 WHERE material_id IN (654,557);
UPDATE `biz_purchase_return_item` SET material_id = 280 WHERE material_id IN (654,557);
UPDATE `biz_wo_bom_snapshot` SET material_id = 280 WHERE material_id IN (654,557);
UPDATE `biz_plan_item_material` SET material_id = 280 WHERE material_id IN (654,557);
UPDATE `biz_outsource_delivery_item` SET material_id = 280 WHERE material_id IN (654,557);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 280 WHERE material_id IN (654,557);
UPDATE `biz_sales_order_item` SET product_id = 280 WHERE product_id IN (654,557);
UPDATE `biz_work_order` SET product_id = 280 WHERE product_id IN (654,557);
UPDATE `biz_return_order_item` SET product_id = 280 WHERE product_id IN (654,557);
UPDATE `biz_mrp_production_suggestion` SET product_id = 280 WHERE product_id IN (654,557);
UPDATE `biz_process_route` SET product_id = 280 WHERE product_id IN (654,557);
UPDATE `biz_bom_item` SET material_id = 280 WHERE material_id IN (654,557);
UPDATE `biz_bom_item` SET product_id = 280 WHERE product_id IN (654,557);
UPDATE `biz_qc_task` SET material_id = 280 WHERE material_id IN (654,557);
UPDATE `biz_qc_task` SET product_id = 280 WHERE product_id IN (654,557);
UPDATE `biz_defect_record` SET material_id = 280 WHERE material_id IN (654,557);
UPDATE `biz_defect_record` SET product_id = 280 WHERE product_id IN (654,557);
UPDATE `biz_mrp_demand` SET material_id = 280 WHERE material_id IN (654,557);
UPDATE `biz_mrp_demand` SET product_id = 280 WHERE product_id IN (654,557);
UPDATE `biz_outsource_request` SET material_id = 280 WHERE material_id IN (654,557);
UPDATE `biz_outsource_request` SET product_id = 280 WHERE product_id IN (654,557);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (654,557) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 280
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 280 WHERE material_id IN (654,557)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 280) tmp);
DELETE FROM biz_inventory WHERE material_id IN (654,557);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (654,557);

-- ============================================================
-- 电机座: 主ID=284(TZJC170101CV) -> 正确编码=TZJC-1701-01CV
-- 合并: id=770(TZJC-1701-01CV), id=599(ZG0118)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJC-1701-01CV' AND id != 284;
UPDATE biz_material SET material_no = 'TZJC-1701-01CV', deleted = 0, status = 'active' WHERE id = 284;

UPDATE `biz_inv_transaction` SET material_id = 284 WHERE material_id IN (770,599);
UPDATE `biz_inv_task_item` SET material_id = 284 WHERE material_id IN (770,599);
UPDATE `biz_inv_history_item` SET material_id = 284 WHERE material_id IN (770,599);
UPDATE `biz_inv_request_item` SET material_id = 284 WHERE material_id IN (770,599);
UPDATE `biz_inv_reservation` SET material_id = 284 WHERE material_id IN (770,599);
UPDATE `biz_inv_transfer_item` SET material_id = 284 WHERE material_id IN (770,599);
UPDATE `biz_inv_stocktaking_item` SET material_id = 284 WHERE material_id IN (770,599);
UPDATE `biz_purchase_request` SET material_id = 284 WHERE material_id IN (770,599);
UPDATE `biz_purchase_order_item` SET material_id = 284 WHERE material_id IN (770,599);
UPDATE `biz_purchase_receipt` SET material_id = 284 WHERE material_id IN (770,599);
UPDATE `biz_purchase_return_item` SET material_id = 284 WHERE material_id IN (770,599);
UPDATE `biz_wo_bom_snapshot` SET material_id = 284 WHERE material_id IN (770,599);
UPDATE `biz_plan_item_material` SET material_id = 284 WHERE material_id IN (770,599);
UPDATE `biz_outsource_delivery_item` SET material_id = 284 WHERE material_id IN (770,599);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 284 WHERE material_id IN (770,599);
UPDATE `biz_sales_order_item` SET product_id = 284 WHERE product_id IN (770,599);
UPDATE `biz_work_order` SET product_id = 284 WHERE product_id IN (770,599);
UPDATE `biz_return_order_item` SET product_id = 284 WHERE product_id IN (770,599);
UPDATE `biz_mrp_production_suggestion` SET product_id = 284 WHERE product_id IN (770,599);
UPDATE `biz_process_route` SET product_id = 284 WHERE product_id IN (770,599);
UPDATE `biz_bom_item` SET material_id = 284 WHERE material_id IN (770,599);
UPDATE `biz_bom_item` SET product_id = 284 WHERE product_id IN (770,599);
UPDATE `biz_qc_task` SET material_id = 284 WHERE material_id IN (770,599);
UPDATE `biz_qc_task` SET product_id = 284 WHERE product_id IN (770,599);
UPDATE `biz_defect_record` SET material_id = 284 WHERE material_id IN (770,599);
UPDATE `biz_defect_record` SET product_id = 284 WHERE product_id IN (770,599);
UPDATE `biz_mrp_demand` SET material_id = 284 WHERE material_id IN (770,599);
UPDATE `biz_mrp_demand` SET product_id = 284 WHERE product_id IN (770,599);
UPDATE `biz_outsource_request` SET material_id = 284 WHERE material_id IN (770,599);
UPDATE `biz_outsource_request` SET product_id = 284 WHERE product_id IN (770,599);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (770,599) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 284
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 284 WHERE material_id IN (770,599)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 284) tmp);
DELETE FROM biz_inventory WHERE material_id IN (770,599);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (770,599);

-- ============================================================
-- 电机支座: 主ID=285(TZJC170101CVA) -> 正确编码=TZJC-1701-01CVA
-- 合并: id=655(TZJC-1701-01CVA), id=558(ZG0077)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJC-1701-01CVA' AND id != 285;
UPDATE biz_material SET material_no = 'TZJC-1701-01CVA', deleted = 0, status = 'active' WHERE id = 285;

UPDATE `biz_inv_transaction` SET material_id = 285 WHERE material_id IN (655,558);
UPDATE `biz_inv_task_item` SET material_id = 285 WHERE material_id IN (655,558);
UPDATE `biz_inv_history_item` SET material_id = 285 WHERE material_id IN (655,558);
UPDATE `biz_inv_request_item` SET material_id = 285 WHERE material_id IN (655,558);
UPDATE `biz_inv_reservation` SET material_id = 285 WHERE material_id IN (655,558);
UPDATE `biz_inv_transfer_item` SET material_id = 285 WHERE material_id IN (655,558);
UPDATE `biz_inv_stocktaking_item` SET material_id = 285 WHERE material_id IN (655,558);
UPDATE `biz_purchase_request` SET material_id = 285 WHERE material_id IN (655,558);
UPDATE `biz_purchase_order_item` SET material_id = 285 WHERE material_id IN (655,558);
UPDATE `biz_purchase_receipt` SET material_id = 285 WHERE material_id IN (655,558);
UPDATE `biz_purchase_return_item` SET material_id = 285 WHERE material_id IN (655,558);
UPDATE `biz_wo_bom_snapshot` SET material_id = 285 WHERE material_id IN (655,558);
UPDATE `biz_plan_item_material` SET material_id = 285 WHERE material_id IN (655,558);
UPDATE `biz_outsource_delivery_item` SET material_id = 285 WHERE material_id IN (655,558);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 285 WHERE material_id IN (655,558);
UPDATE `biz_sales_order_item` SET product_id = 285 WHERE product_id IN (655,558);
UPDATE `biz_work_order` SET product_id = 285 WHERE product_id IN (655,558);
UPDATE `biz_return_order_item` SET product_id = 285 WHERE product_id IN (655,558);
UPDATE `biz_mrp_production_suggestion` SET product_id = 285 WHERE product_id IN (655,558);
UPDATE `biz_process_route` SET product_id = 285 WHERE product_id IN (655,558);
UPDATE `biz_bom_item` SET material_id = 285 WHERE material_id IN (655,558);
UPDATE `biz_bom_item` SET product_id = 285 WHERE product_id IN (655,558);
UPDATE `biz_qc_task` SET material_id = 285 WHERE material_id IN (655,558);
UPDATE `biz_qc_task` SET product_id = 285 WHERE product_id IN (655,558);
UPDATE `biz_defect_record` SET material_id = 285 WHERE material_id IN (655,558);
UPDATE `biz_defect_record` SET product_id = 285 WHERE product_id IN (655,558);
UPDATE `biz_mrp_demand` SET material_id = 285 WHERE material_id IN (655,558);
UPDATE `biz_mrp_demand` SET product_id = 285 WHERE product_id IN (655,558);
UPDATE `biz_outsource_request` SET material_id = 285 WHERE material_id IN (655,558);
UPDATE `biz_outsource_request` SET product_id = 285 WHERE product_id IN (655,558);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (655,558) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 285
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 285 WHERE material_id IN (655,558)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 285) tmp);
DELETE FROM biz_inventory WHERE material_id IN (655,558);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (655,558);

-- ============================================================
-- 底座: 主ID=289(TZJCY16002DCJZ) -> 正确编码=TZJC-Y160-02DCJZ
-- 合并: id=778(TZJC-Y160-02DCJZ), id=535(ZG0054)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJC-Y160-02DCJZ' AND id != 289;
UPDATE biz_material SET material_no = 'TZJC-Y160-02DCJZ', deleted = 0, status = 'active' WHERE id = 289;

UPDATE `biz_inv_transaction` SET material_id = 289 WHERE material_id IN (778,535);
UPDATE `biz_inv_task_item` SET material_id = 289 WHERE material_id IN (778,535);
UPDATE `biz_inv_history_item` SET material_id = 289 WHERE material_id IN (778,535);
UPDATE `biz_inv_request_item` SET material_id = 289 WHERE material_id IN (778,535);
UPDATE `biz_inv_reservation` SET material_id = 289 WHERE material_id IN (778,535);
UPDATE `biz_inv_transfer_item` SET material_id = 289 WHERE material_id IN (778,535);
UPDATE `biz_inv_stocktaking_item` SET material_id = 289 WHERE material_id IN (778,535);
UPDATE `biz_purchase_request` SET material_id = 289 WHERE material_id IN (778,535);
UPDATE `biz_purchase_order_item` SET material_id = 289 WHERE material_id IN (778,535);
UPDATE `biz_purchase_receipt` SET material_id = 289 WHERE material_id IN (778,535);
UPDATE `biz_purchase_return_item` SET material_id = 289 WHERE material_id IN (778,535);
UPDATE `biz_wo_bom_snapshot` SET material_id = 289 WHERE material_id IN (778,535);
UPDATE `biz_plan_item_material` SET material_id = 289 WHERE material_id IN (778,535);
UPDATE `biz_outsource_delivery_item` SET material_id = 289 WHERE material_id IN (778,535);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 289 WHERE material_id IN (778,535);
UPDATE `biz_sales_order_item` SET product_id = 289 WHERE product_id IN (778,535);
UPDATE `biz_work_order` SET product_id = 289 WHERE product_id IN (778,535);
UPDATE `biz_return_order_item` SET product_id = 289 WHERE product_id IN (778,535);
UPDATE `biz_mrp_production_suggestion` SET product_id = 289 WHERE product_id IN (778,535);
UPDATE `biz_process_route` SET product_id = 289 WHERE product_id IN (778,535);
UPDATE `biz_bom_item` SET material_id = 289 WHERE material_id IN (778,535);
UPDATE `biz_bom_item` SET product_id = 289 WHERE product_id IN (778,535);
UPDATE `biz_qc_task` SET material_id = 289 WHERE material_id IN (778,535);
UPDATE `biz_qc_task` SET product_id = 289 WHERE product_id IN (778,535);
UPDATE `biz_defect_record` SET material_id = 289 WHERE material_id IN (778,535);
UPDATE `biz_defect_record` SET product_id = 289 WHERE product_id IN (778,535);
UPDATE `biz_mrp_demand` SET material_id = 289 WHERE material_id IN (778,535);
UPDATE `biz_mrp_demand` SET product_id = 289 WHERE product_id IN (778,535);
UPDATE `biz_outsource_request` SET material_id = 289 WHERE material_id IN (778,535);
UPDATE `biz_outsource_request` SET product_id = 289 WHERE product_id IN (778,535);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (778,535) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 289
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 289 WHERE material_id IN (778,535)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 289) tmp);
DELETE FROM biz_inventory WHERE material_id IN (778,535);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (778,535);

-- ============================================================
-- 底座: 主ID=291(TZJCY18002DCJZ) -> 正确编码=TZJC-Y180-02DCJZ
-- 合并: id=789(TZJC-Y180-02DCJZ), id=536(ZG0055)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJC-Y180-02DCJZ' AND id != 291;
UPDATE biz_material SET material_no = 'TZJC-Y180-02DCJZ', deleted = 0, status = 'active' WHERE id = 291;

UPDATE `biz_inv_transaction` SET material_id = 291 WHERE material_id IN (789,536);
UPDATE `biz_inv_task_item` SET material_id = 291 WHERE material_id IN (789,536);
UPDATE `biz_inv_history_item` SET material_id = 291 WHERE material_id IN (789,536);
UPDATE `biz_inv_request_item` SET material_id = 291 WHERE material_id IN (789,536);
UPDATE `biz_inv_reservation` SET material_id = 291 WHERE material_id IN (789,536);
UPDATE `biz_inv_transfer_item` SET material_id = 291 WHERE material_id IN (789,536);
UPDATE `biz_inv_stocktaking_item` SET material_id = 291 WHERE material_id IN (789,536);
UPDATE `biz_purchase_request` SET material_id = 291 WHERE material_id IN (789,536);
UPDATE `biz_purchase_order_item` SET material_id = 291 WHERE material_id IN (789,536);
UPDATE `biz_purchase_receipt` SET material_id = 291 WHERE material_id IN (789,536);
UPDATE `biz_purchase_return_item` SET material_id = 291 WHERE material_id IN (789,536);
UPDATE `biz_wo_bom_snapshot` SET material_id = 291 WHERE material_id IN (789,536);
UPDATE `biz_plan_item_material` SET material_id = 291 WHERE material_id IN (789,536);
UPDATE `biz_outsource_delivery_item` SET material_id = 291 WHERE material_id IN (789,536);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 291 WHERE material_id IN (789,536);
UPDATE `biz_sales_order_item` SET product_id = 291 WHERE product_id IN (789,536);
UPDATE `biz_work_order` SET product_id = 291 WHERE product_id IN (789,536);
UPDATE `biz_return_order_item` SET product_id = 291 WHERE product_id IN (789,536);
UPDATE `biz_mrp_production_suggestion` SET product_id = 291 WHERE product_id IN (789,536);
UPDATE `biz_process_route` SET product_id = 291 WHERE product_id IN (789,536);
UPDATE `biz_bom_item` SET material_id = 291 WHERE material_id IN (789,536);
UPDATE `biz_bom_item` SET product_id = 291 WHERE product_id IN (789,536);
UPDATE `biz_qc_task` SET material_id = 291 WHERE material_id IN (789,536);
UPDATE `biz_qc_task` SET product_id = 291 WHERE product_id IN (789,536);
UPDATE `biz_defect_record` SET material_id = 291 WHERE material_id IN (789,536);
UPDATE `biz_defect_record` SET product_id = 291 WHERE product_id IN (789,536);
UPDATE `biz_mrp_demand` SET material_id = 291 WHERE material_id IN (789,536);
UPDATE `biz_mrp_demand` SET product_id = 291 WHERE product_id IN (789,536);
UPDATE `biz_outsource_request` SET material_id = 291 WHERE material_id IN (789,536);
UPDATE `biz_outsource_request` SET product_id = 291 WHERE product_id IN (789,536);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (789,536) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 291
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 291 WHERE material_id IN (789,536)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 291) tmp);
DELETE FROM biz_inventory WHERE material_id IN (789,536);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (789,536);

-- ============================================================
-- 底座: 主ID=296(TZJD091116001CV) -> 正确编码=TZJD-0911-160-01CV
-- 合并: id=639(TZJD-0911-160-01CV), id=542(ZG0061)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJD-0911-160-01CV' AND id != 296;
UPDATE biz_material SET material_no = 'TZJD-0911-160-01CV', deleted = 0, status = 'active' WHERE id = 296;

UPDATE `biz_inv_transaction` SET material_id = 296 WHERE material_id IN (639,542);
UPDATE `biz_inv_task_item` SET material_id = 296 WHERE material_id IN (639,542);
UPDATE `biz_inv_history_item` SET material_id = 296 WHERE material_id IN (639,542);
UPDATE `biz_inv_request_item` SET material_id = 296 WHERE material_id IN (639,542);
UPDATE `biz_inv_reservation` SET material_id = 296 WHERE material_id IN (639,542);
UPDATE `biz_inv_transfer_item` SET material_id = 296 WHERE material_id IN (639,542);
UPDATE `biz_inv_stocktaking_item` SET material_id = 296 WHERE material_id IN (639,542);
UPDATE `biz_purchase_request` SET material_id = 296 WHERE material_id IN (639,542);
UPDATE `biz_purchase_order_item` SET material_id = 296 WHERE material_id IN (639,542);
UPDATE `biz_purchase_receipt` SET material_id = 296 WHERE material_id IN (639,542);
UPDATE `biz_purchase_return_item` SET material_id = 296 WHERE material_id IN (639,542);
UPDATE `biz_wo_bom_snapshot` SET material_id = 296 WHERE material_id IN (639,542);
UPDATE `biz_plan_item_material` SET material_id = 296 WHERE material_id IN (639,542);
UPDATE `biz_outsource_delivery_item` SET material_id = 296 WHERE material_id IN (639,542);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 296 WHERE material_id IN (639,542);
UPDATE `biz_sales_order_item` SET product_id = 296 WHERE product_id IN (639,542);
UPDATE `biz_work_order` SET product_id = 296 WHERE product_id IN (639,542);
UPDATE `biz_return_order_item` SET product_id = 296 WHERE product_id IN (639,542);
UPDATE `biz_mrp_production_suggestion` SET product_id = 296 WHERE product_id IN (639,542);
UPDATE `biz_process_route` SET product_id = 296 WHERE product_id IN (639,542);
UPDATE `biz_bom_item` SET material_id = 296 WHERE material_id IN (639,542);
UPDATE `biz_bom_item` SET product_id = 296 WHERE product_id IN (639,542);
UPDATE `biz_qc_task` SET material_id = 296 WHERE material_id IN (639,542);
UPDATE `biz_qc_task` SET product_id = 296 WHERE product_id IN (639,542);
UPDATE `biz_defect_record` SET material_id = 296 WHERE material_id IN (639,542);
UPDATE `biz_defect_record` SET product_id = 296 WHERE product_id IN (639,542);
UPDATE `biz_mrp_demand` SET material_id = 296 WHERE material_id IN (639,542);
UPDATE `biz_mrp_demand` SET product_id = 296 WHERE product_id IN (639,542);
UPDATE `biz_outsource_request` SET material_id = 296 WHERE material_id IN (639,542);
UPDATE `biz_outsource_request` SET product_id = 296 WHERE product_id IN (639,542);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (639,542) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 296
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 296 WHERE material_id IN (639,542)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 296) tmp);
DELETE FROM biz_inventory WHERE material_id IN (639,542);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (639,542);

-- ============================================================
-- 底座: 主ID=297(TZJD091116002CV) -> 正确编码=TZJD-0911-160-02CV
-- 合并: id=725(TZJD-0911-160-02CV), id=494(ZG0013)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJD-0911-160-02CV' AND id != 297;
UPDATE biz_material SET material_no = 'TZJD-0911-160-02CV', deleted = 0, status = 'active' WHERE id = 297;

UPDATE `biz_inv_transaction` SET material_id = 297 WHERE material_id IN (725,494);
UPDATE `biz_inv_task_item` SET material_id = 297 WHERE material_id IN (725,494);
UPDATE `biz_inv_history_item` SET material_id = 297 WHERE material_id IN (725,494);
UPDATE `biz_inv_request_item` SET material_id = 297 WHERE material_id IN (725,494);
UPDATE `biz_inv_reservation` SET material_id = 297 WHERE material_id IN (725,494);
UPDATE `biz_inv_transfer_item` SET material_id = 297 WHERE material_id IN (725,494);
UPDATE `biz_inv_stocktaking_item` SET material_id = 297 WHERE material_id IN (725,494);
UPDATE `biz_purchase_request` SET material_id = 297 WHERE material_id IN (725,494);
UPDATE `biz_purchase_order_item` SET material_id = 297 WHERE material_id IN (725,494);
UPDATE `biz_purchase_receipt` SET material_id = 297 WHERE material_id IN (725,494);
UPDATE `biz_purchase_return_item` SET material_id = 297 WHERE material_id IN (725,494);
UPDATE `biz_wo_bom_snapshot` SET material_id = 297 WHERE material_id IN (725,494);
UPDATE `biz_plan_item_material` SET material_id = 297 WHERE material_id IN (725,494);
UPDATE `biz_outsource_delivery_item` SET material_id = 297 WHERE material_id IN (725,494);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 297 WHERE material_id IN (725,494);
UPDATE `biz_sales_order_item` SET product_id = 297 WHERE product_id IN (725,494);
UPDATE `biz_work_order` SET product_id = 297 WHERE product_id IN (725,494);
UPDATE `biz_return_order_item` SET product_id = 297 WHERE product_id IN (725,494);
UPDATE `biz_mrp_production_suggestion` SET product_id = 297 WHERE product_id IN (725,494);
UPDATE `biz_process_route` SET product_id = 297 WHERE product_id IN (725,494);
UPDATE `biz_bom_item` SET material_id = 297 WHERE material_id IN (725,494);
UPDATE `biz_bom_item` SET product_id = 297 WHERE product_id IN (725,494);
UPDATE `biz_qc_task` SET material_id = 297 WHERE material_id IN (725,494);
UPDATE `biz_qc_task` SET product_id = 297 WHERE product_id IN (725,494);
UPDATE `biz_defect_record` SET material_id = 297 WHERE material_id IN (725,494);
UPDATE `biz_defect_record` SET product_id = 297 WHERE product_id IN (725,494);
UPDATE `biz_mrp_demand` SET material_id = 297 WHERE material_id IN (725,494);
UPDATE `biz_mrp_demand` SET product_id = 297 WHERE product_id IN (725,494);
UPDATE `biz_outsource_request` SET material_id = 297 WHERE material_id IN (725,494);
UPDATE `biz_outsource_request` SET product_id = 297 WHERE product_id IN (725,494);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (725,494) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 297
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 297 WHERE material_id IN (725,494)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 297) tmp);
DELETE FROM biz_inventory WHERE material_id IN (725,494);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (725,494);

-- ============================================================
-- 底座: 主ID=299(TZJD091118002ZV) -> 正确编码=TZJD-0911-180-02ZV
-- 合并: id=755(TZJD-0911-180-02ZV), id=585(ZG0104)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJD-0911-180-02ZV' AND id != 299;
UPDATE biz_material SET material_no = 'TZJD-0911-180-02ZV', deleted = 0, status = 'active' WHERE id = 299;

UPDATE `biz_inv_transaction` SET material_id = 299 WHERE material_id IN (755,585);
UPDATE `biz_inv_task_item` SET material_id = 299 WHERE material_id IN (755,585);
UPDATE `biz_inv_history_item` SET material_id = 299 WHERE material_id IN (755,585);
UPDATE `biz_inv_request_item` SET material_id = 299 WHERE material_id IN (755,585);
UPDATE `biz_inv_reservation` SET material_id = 299 WHERE material_id IN (755,585);
UPDATE `biz_inv_transfer_item` SET material_id = 299 WHERE material_id IN (755,585);
UPDATE `biz_inv_stocktaking_item` SET material_id = 299 WHERE material_id IN (755,585);
UPDATE `biz_purchase_request` SET material_id = 299 WHERE material_id IN (755,585);
UPDATE `biz_purchase_order_item` SET material_id = 299 WHERE material_id IN (755,585);
UPDATE `biz_purchase_receipt` SET material_id = 299 WHERE material_id IN (755,585);
UPDATE `biz_purchase_return_item` SET material_id = 299 WHERE material_id IN (755,585);
UPDATE `biz_wo_bom_snapshot` SET material_id = 299 WHERE material_id IN (755,585);
UPDATE `biz_plan_item_material` SET material_id = 299 WHERE material_id IN (755,585);
UPDATE `biz_outsource_delivery_item` SET material_id = 299 WHERE material_id IN (755,585);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 299 WHERE material_id IN (755,585);
UPDATE `biz_sales_order_item` SET product_id = 299 WHERE product_id IN (755,585);
UPDATE `biz_work_order` SET product_id = 299 WHERE product_id IN (755,585);
UPDATE `biz_return_order_item` SET product_id = 299 WHERE product_id IN (755,585);
UPDATE `biz_mrp_production_suggestion` SET product_id = 299 WHERE product_id IN (755,585);
UPDATE `biz_process_route` SET product_id = 299 WHERE product_id IN (755,585);
UPDATE `biz_bom_item` SET material_id = 299 WHERE material_id IN (755,585);
UPDATE `biz_bom_item` SET product_id = 299 WHERE product_id IN (755,585);
UPDATE `biz_qc_task` SET material_id = 299 WHERE material_id IN (755,585);
UPDATE `biz_qc_task` SET product_id = 299 WHERE product_id IN (755,585);
UPDATE `biz_defect_record` SET material_id = 299 WHERE material_id IN (755,585);
UPDATE `biz_defect_record` SET product_id = 299 WHERE product_id IN (755,585);
UPDATE `biz_mrp_demand` SET material_id = 299 WHERE material_id IN (755,585);
UPDATE `biz_mrp_demand` SET product_id = 299 WHERE product_id IN (755,585);
UPDATE `biz_outsource_request` SET material_id = 299 WHERE material_id IN (755,585);
UPDATE `biz_outsource_request` SET product_id = 299 WHERE product_id IN (755,585);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (755,585) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 299
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 299 WHERE material_id IN (755,585)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 299) tmp);
DELETE FROM biz_inventory WHERE material_id IN (755,585);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (755,585);

-- ============================================================
-- 发货支架: 主ID=306(TZJD100430FHZJ150) -> 正确编码=TZJD-100-430-FHZJ-150
-- 合并: id=657(TZJD-100-430-FHZJ-150), id=560(ZG0079)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJD-100-430-FHZJ-150' AND id != 306;
UPDATE biz_material SET material_no = 'TZJD-100-430-FHZJ-150', deleted = 0, status = 'active' WHERE id = 306;

UPDATE `biz_inv_transaction` SET material_id = 306 WHERE material_id IN (657,560);
UPDATE `biz_inv_task_item` SET material_id = 306 WHERE material_id IN (657,560);
UPDATE `biz_inv_history_item` SET material_id = 306 WHERE material_id IN (657,560);
UPDATE `biz_inv_request_item` SET material_id = 306 WHERE material_id IN (657,560);
UPDATE `biz_inv_reservation` SET material_id = 306 WHERE material_id IN (657,560);
UPDATE `biz_inv_transfer_item` SET material_id = 306 WHERE material_id IN (657,560);
UPDATE `biz_inv_stocktaking_item` SET material_id = 306 WHERE material_id IN (657,560);
UPDATE `biz_purchase_request` SET material_id = 306 WHERE material_id IN (657,560);
UPDATE `biz_purchase_order_item` SET material_id = 306 WHERE material_id IN (657,560);
UPDATE `biz_purchase_receipt` SET material_id = 306 WHERE material_id IN (657,560);
UPDATE `biz_purchase_return_item` SET material_id = 306 WHERE material_id IN (657,560);
UPDATE `biz_wo_bom_snapshot` SET material_id = 306 WHERE material_id IN (657,560);
UPDATE `biz_plan_item_material` SET material_id = 306 WHERE material_id IN (657,560);
UPDATE `biz_outsource_delivery_item` SET material_id = 306 WHERE material_id IN (657,560);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 306 WHERE material_id IN (657,560);
UPDATE `biz_sales_order_item` SET product_id = 306 WHERE product_id IN (657,560);
UPDATE `biz_work_order` SET product_id = 306 WHERE product_id IN (657,560);
UPDATE `biz_return_order_item` SET product_id = 306 WHERE product_id IN (657,560);
UPDATE `biz_mrp_production_suggestion` SET product_id = 306 WHERE product_id IN (657,560);
UPDATE `biz_process_route` SET product_id = 306 WHERE product_id IN (657,560);
UPDATE `biz_bom_item` SET material_id = 306 WHERE material_id IN (657,560);
UPDATE `biz_bom_item` SET product_id = 306 WHERE product_id IN (657,560);
UPDATE `biz_qc_task` SET material_id = 306 WHERE material_id IN (657,560);
UPDATE `biz_qc_task` SET product_id = 306 WHERE product_id IN (657,560);
UPDATE `biz_defect_record` SET material_id = 306 WHERE material_id IN (657,560);
UPDATE `biz_defect_record` SET product_id = 306 WHERE product_id IN (657,560);
UPDATE `biz_mrp_demand` SET material_id = 306 WHERE material_id IN (657,560);
UPDATE `biz_mrp_demand` SET product_id = 306 WHERE product_id IN (657,560);
UPDATE `biz_outsource_request` SET material_id = 306 WHERE material_id IN (657,560);
UPDATE `biz_outsource_request` SET product_id = 306 WHERE product_id IN (657,560);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (657,560) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 306
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 306 WHERE material_id IN (657,560)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 306) tmp);
DELETE FROM biz_inventory WHERE material_id IN (657,560);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (657,560);

-- ============================================================
-- 皮带罩: 主ID=307(TZJD1301130002CL/CR) -> 正确编码=TZJD-1301-1300-02CL/CR
-- 合并: id=720(TZJD-1301-1300-02CL/CR), id=488(ZG0007)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJD-1301-1300-02CL/CR' AND id != 307;
UPDATE biz_material SET material_no = 'TZJD-1301-1300-02CL/CR', deleted = 0, status = 'active' WHERE id = 307;

UPDATE `biz_inv_transaction` SET material_id = 307 WHERE material_id IN (720,488);
UPDATE `biz_inv_task_item` SET material_id = 307 WHERE material_id IN (720,488);
UPDATE `biz_inv_history_item` SET material_id = 307 WHERE material_id IN (720,488);
UPDATE `biz_inv_request_item` SET material_id = 307 WHERE material_id IN (720,488);
UPDATE `biz_inv_reservation` SET material_id = 307 WHERE material_id IN (720,488);
UPDATE `biz_inv_transfer_item` SET material_id = 307 WHERE material_id IN (720,488);
UPDATE `biz_inv_stocktaking_item` SET material_id = 307 WHERE material_id IN (720,488);
UPDATE `biz_purchase_request` SET material_id = 307 WHERE material_id IN (720,488);
UPDATE `biz_purchase_order_item` SET material_id = 307 WHERE material_id IN (720,488);
UPDATE `biz_purchase_receipt` SET material_id = 307 WHERE material_id IN (720,488);
UPDATE `biz_purchase_return_item` SET material_id = 307 WHERE material_id IN (720,488);
UPDATE `biz_wo_bom_snapshot` SET material_id = 307 WHERE material_id IN (720,488);
UPDATE `biz_plan_item_material` SET material_id = 307 WHERE material_id IN (720,488);
UPDATE `biz_outsource_delivery_item` SET material_id = 307 WHERE material_id IN (720,488);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 307 WHERE material_id IN (720,488);
UPDATE `biz_sales_order_item` SET product_id = 307 WHERE product_id IN (720,488);
UPDATE `biz_work_order` SET product_id = 307 WHERE product_id IN (720,488);
UPDATE `biz_return_order_item` SET product_id = 307 WHERE product_id IN (720,488);
UPDATE `biz_mrp_production_suggestion` SET product_id = 307 WHERE product_id IN (720,488);
UPDATE `biz_process_route` SET product_id = 307 WHERE product_id IN (720,488);
UPDATE `biz_bom_item` SET material_id = 307 WHERE material_id IN (720,488);
UPDATE `biz_bom_item` SET product_id = 307 WHERE product_id IN (720,488);
UPDATE `biz_qc_task` SET material_id = 307 WHERE material_id IN (720,488);
UPDATE `biz_qc_task` SET product_id = 307 WHERE product_id IN (720,488);
UPDATE `biz_defect_record` SET material_id = 307 WHERE material_id IN (720,488);
UPDATE `biz_defect_record` SET product_id = 307 WHERE product_id IN (720,488);
UPDATE `biz_mrp_demand` SET material_id = 307 WHERE material_id IN (720,488);
UPDATE `biz_mrp_demand` SET product_id = 307 WHERE product_id IN (720,488);
UPDATE `biz_outsource_request` SET material_id = 307 WHERE material_id IN (720,488);
UPDATE `biz_outsource_request` SET product_id = 307 WHERE product_id IN (720,488);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (720,488) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 307
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 307 WHERE material_id IN (720,488)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 307) tmp);
DELETE FROM biz_inventory WHERE material_id IN (720,488);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (720,488);

-- ============================================================
-- 皮带罩: 主ID=308(TZJD1301150002CL/CR) -> 正确编码=TZJD-1301-1500-02CL/CR
-- 合并: id=658(TZJD-1301-1500-02CL/CR), id=561(ZG0080)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJD-1301-1500-02CL/CR' AND id != 308;
UPDATE biz_material SET material_no = 'TZJD-1301-1500-02CL/CR', deleted = 0, status = 'active' WHERE id = 308;

UPDATE `biz_inv_transaction` SET material_id = 308 WHERE material_id IN (658,561);
UPDATE `biz_inv_task_item` SET material_id = 308 WHERE material_id IN (658,561);
UPDATE `biz_inv_history_item` SET material_id = 308 WHERE material_id IN (658,561);
UPDATE `biz_inv_request_item` SET material_id = 308 WHERE material_id IN (658,561);
UPDATE `biz_inv_reservation` SET material_id = 308 WHERE material_id IN (658,561);
UPDATE `biz_inv_transfer_item` SET material_id = 308 WHERE material_id IN (658,561);
UPDATE `biz_inv_stocktaking_item` SET material_id = 308 WHERE material_id IN (658,561);
UPDATE `biz_purchase_request` SET material_id = 308 WHERE material_id IN (658,561);
UPDATE `biz_purchase_order_item` SET material_id = 308 WHERE material_id IN (658,561);
UPDATE `biz_purchase_receipt` SET material_id = 308 WHERE material_id IN (658,561);
UPDATE `biz_purchase_return_item` SET material_id = 308 WHERE material_id IN (658,561);
UPDATE `biz_wo_bom_snapshot` SET material_id = 308 WHERE material_id IN (658,561);
UPDATE `biz_plan_item_material` SET material_id = 308 WHERE material_id IN (658,561);
UPDATE `biz_outsource_delivery_item` SET material_id = 308 WHERE material_id IN (658,561);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 308 WHERE material_id IN (658,561);
UPDATE `biz_sales_order_item` SET product_id = 308 WHERE product_id IN (658,561);
UPDATE `biz_work_order` SET product_id = 308 WHERE product_id IN (658,561);
UPDATE `biz_return_order_item` SET product_id = 308 WHERE product_id IN (658,561);
UPDATE `biz_mrp_production_suggestion` SET product_id = 308 WHERE product_id IN (658,561);
UPDATE `biz_process_route` SET product_id = 308 WHERE product_id IN (658,561);
UPDATE `biz_bom_item` SET material_id = 308 WHERE material_id IN (658,561);
UPDATE `biz_bom_item` SET product_id = 308 WHERE product_id IN (658,561);
UPDATE `biz_qc_task` SET material_id = 308 WHERE material_id IN (658,561);
UPDATE `biz_qc_task` SET product_id = 308 WHERE product_id IN (658,561);
UPDATE `biz_defect_record` SET material_id = 308 WHERE material_id IN (658,561);
UPDATE `biz_defect_record` SET product_id = 308 WHERE product_id IN (658,561);
UPDATE `biz_mrp_demand` SET material_id = 308 WHERE material_id IN (658,561);
UPDATE `biz_mrp_demand` SET product_id = 308 WHERE product_id IN (658,561);
UPDATE `biz_outsource_request` SET material_id = 308 WHERE material_id IN (658,561);
UPDATE `biz_outsource_request` SET product_id = 308 WHERE product_id IN (658,561);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (658,561) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 308
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 308 WHERE material_id IN (658,561)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 308) tmp);
DELETE FROM biz_inventory WHERE material_id IN (658,561);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (658,561);

-- ============================================================
-- 皮带罩: 主ID=309(TZJD130155001CV) -> 正确编码=TZJD-1301-550-01CV
-- 合并: id=642(TZJD-1301-550-01CV), id=545(ZG0064)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJD-1301-550-01CV' AND id != 309;
UPDATE biz_material SET material_no = 'TZJD-1301-550-01CV', deleted = 0, status = 'active' WHERE id = 309;

UPDATE `biz_inv_transaction` SET material_id = 309 WHERE material_id IN (642,545);
UPDATE `biz_inv_task_item` SET material_id = 309 WHERE material_id IN (642,545);
UPDATE `biz_inv_history_item` SET material_id = 309 WHERE material_id IN (642,545);
UPDATE `biz_inv_request_item` SET material_id = 309 WHERE material_id IN (642,545);
UPDATE `biz_inv_reservation` SET material_id = 309 WHERE material_id IN (642,545);
UPDATE `biz_inv_transfer_item` SET material_id = 309 WHERE material_id IN (642,545);
UPDATE `biz_inv_stocktaking_item` SET material_id = 309 WHERE material_id IN (642,545);
UPDATE `biz_purchase_request` SET material_id = 309 WHERE material_id IN (642,545);
UPDATE `biz_purchase_order_item` SET material_id = 309 WHERE material_id IN (642,545);
UPDATE `biz_purchase_receipt` SET material_id = 309 WHERE material_id IN (642,545);
UPDATE `biz_purchase_return_item` SET material_id = 309 WHERE material_id IN (642,545);
UPDATE `biz_wo_bom_snapshot` SET material_id = 309 WHERE material_id IN (642,545);
UPDATE `biz_plan_item_material` SET material_id = 309 WHERE material_id IN (642,545);
UPDATE `biz_outsource_delivery_item` SET material_id = 309 WHERE material_id IN (642,545);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 309 WHERE material_id IN (642,545);
UPDATE `biz_sales_order_item` SET product_id = 309 WHERE product_id IN (642,545);
UPDATE `biz_work_order` SET product_id = 309 WHERE product_id IN (642,545);
UPDATE `biz_return_order_item` SET product_id = 309 WHERE product_id IN (642,545);
UPDATE `biz_mrp_production_suggestion` SET product_id = 309 WHERE product_id IN (642,545);
UPDATE `biz_process_route` SET product_id = 309 WHERE product_id IN (642,545);
UPDATE `biz_bom_item` SET material_id = 309 WHERE material_id IN (642,545);
UPDATE `biz_bom_item` SET product_id = 309 WHERE product_id IN (642,545);
UPDATE `biz_qc_task` SET material_id = 309 WHERE material_id IN (642,545);
UPDATE `biz_qc_task` SET product_id = 309 WHERE product_id IN (642,545);
UPDATE `biz_defect_record` SET material_id = 309 WHERE material_id IN (642,545);
UPDATE `biz_defect_record` SET product_id = 309 WHERE product_id IN (642,545);
UPDATE `biz_mrp_demand` SET material_id = 309 WHERE material_id IN (642,545);
UPDATE `biz_mrp_demand` SET product_id = 309 WHERE product_id IN (642,545);
UPDATE `biz_outsource_request` SET material_id = 309 WHERE material_id IN (642,545);
UPDATE `biz_outsource_request` SET product_id = 309 WHERE product_id IN (642,545);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (642,545) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 309
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 309 WHERE material_id IN (642,545)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 309) tmp);
DELETE FROM biz_inventory WHERE material_id IN (642,545);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (642,545);

-- ============================================================
-- 皮带罩: 主ID=310(TZJD130170001CV) -> 正确编码=TZJD-1301-700-01CV
-- 合并: id=726(TZJD-1301-700-01CV), id=495(ZG0014)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJD-1301-700-01CV' AND id != 310;
UPDATE biz_material SET material_no = 'TZJD-1301-700-01CV', deleted = 0, status = 'active' WHERE id = 310;

UPDATE `biz_inv_transaction` SET material_id = 310 WHERE material_id IN (726,495);
UPDATE `biz_inv_task_item` SET material_id = 310 WHERE material_id IN (726,495);
UPDATE `biz_inv_history_item` SET material_id = 310 WHERE material_id IN (726,495);
UPDATE `biz_inv_request_item` SET material_id = 310 WHERE material_id IN (726,495);
UPDATE `biz_inv_reservation` SET material_id = 310 WHERE material_id IN (726,495);
UPDATE `biz_inv_transfer_item` SET material_id = 310 WHERE material_id IN (726,495);
UPDATE `biz_inv_stocktaking_item` SET material_id = 310 WHERE material_id IN (726,495);
UPDATE `biz_purchase_request` SET material_id = 310 WHERE material_id IN (726,495);
UPDATE `biz_purchase_order_item` SET material_id = 310 WHERE material_id IN (726,495);
UPDATE `biz_purchase_receipt` SET material_id = 310 WHERE material_id IN (726,495);
UPDATE `biz_purchase_return_item` SET material_id = 310 WHERE material_id IN (726,495);
UPDATE `biz_wo_bom_snapshot` SET material_id = 310 WHERE material_id IN (726,495);
UPDATE `biz_plan_item_material` SET material_id = 310 WHERE material_id IN (726,495);
UPDATE `biz_outsource_delivery_item` SET material_id = 310 WHERE material_id IN (726,495);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 310 WHERE material_id IN (726,495);
UPDATE `biz_sales_order_item` SET product_id = 310 WHERE product_id IN (726,495);
UPDATE `biz_work_order` SET product_id = 310 WHERE product_id IN (726,495);
UPDATE `biz_return_order_item` SET product_id = 310 WHERE product_id IN (726,495);
UPDATE `biz_mrp_production_suggestion` SET product_id = 310 WHERE product_id IN (726,495);
UPDATE `biz_process_route` SET product_id = 310 WHERE product_id IN (726,495);
UPDATE `biz_bom_item` SET material_id = 310 WHERE material_id IN (726,495);
UPDATE `biz_bom_item` SET product_id = 310 WHERE product_id IN (726,495);
UPDATE `biz_qc_task` SET material_id = 310 WHERE material_id IN (726,495);
UPDATE `biz_qc_task` SET product_id = 310 WHERE product_id IN (726,495);
UPDATE `biz_defect_record` SET material_id = 310 WHERE material_id IN (726,495);
UPDATE `biz_defect_record` SET product_id = 310 WHERE product_id IN (726,495);
UPDATE `biz_mrp_demand` SET material_id = 310 WHERE material_id IN (726,495);
UPDATE `biz_mrp_demand` SET product_id = 310 WHERE product_id IN (726,495);
UPDATE `biz_outsource_request` SET material_id = 310 WHERE material_id IN (726,495);
UPDATE `biz_outsource_request` SET product_id = 310 WHERE product_id IN (726,495);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (726,495) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 310
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 310 WHERE material_id IN (726,495)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 310) tmp);
DELETE FROM biz_inventory WHERE material_id IN (726,495);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (726,495);

-- ============================================================
-- 支架: 主ID=311(TZJD160101CV) -> 正确编码=TZJD-1601-01CV
-- 合并: id=640(TZJD-1601-01CV), id=543(ZG0062)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJD-1601-01CV' AND id != 311;
UPDATE biz_material SET material_no = 'TZJD-1601-01CV', deleted = 0, status = 'active' WHERE id = 311;

UPDATE `biz_inv_transaction` SET material_id = 311 WHERE material_id IN (640,543);
UPDATE `biz_inv_task_item` SET material_id = 311 WHERE material_id IN (640,543);
UPDATE `biz_inv_history_item` SET material_id = 311 WHERE material_id IN (640,543);
UPDATE `biz_inv_request_item` SET material_id = 311 WHERE material_id IN (640,543);
UPDATE `biz_inv_reservation` SET material_id = 311 WHERE material_id IN (640,543);
UPDATE `biz_inv_transfer_item` SET material_id = 311 WHERE material_id IN (640,543);
UPDATE `biz_inv_stocktaking_item` SET material_id = 311 WHERE material_id IN (640,543);
UPDATE `biz_purchase_request` SET material_id = 311 WHERE material_id IN (640,543);
UPDATE `biz_purchase_order_item` SET material_id = 311 WHERE material_id IN (640,543);
UPDATE `biz_purchase_receipt` SET material_id = 311 WHERE material_id IN (640,543);
UPDATE `biz_purchase_return_item` SET material_id = 311 WHERE material_id IN (640,543);
UPDATE `biz_wo_bom_snapshot` SET material_id = 311 WHERE material_id IN (640,543);
UPDATE `biz_plan_item_material` SET material_id = 311 WHERE material_id IN (640,543);
UPDATE `biz_outsource_delivery_item` SET material_id = 311 WHERE material_id IN (640,543);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 311 WHERE material_id IN (640,543);
UPDATE `biz_sales_order_item` SET product_id = 311 WHERE product_id IN (640,543);
UPDATE `biz_work_order` SET product_id = 311 WHERE product_id IN (640,543);
UPDATE `biz_return_order_item` SET product_id = 311 WHERE product_id IN (640,543);
UPDATE `biz_mrp_production_suggestion` SET product_id = 311 WHERE product_id IN (640,543);
UPDATE `biz_process_route` SET product_id = 311 WHERE product_id IN (640,543);
UPDATE `biz_bom_item` SET material_id = 311 WHERE material_id IN (640,543);
UPDATE `biz_bom_item` SET product_id = 311 WHERE product_id IN (640,543);
UPDATE `biz_qc_task` SET material_id = 311 WHERE material_id IN (640,543);
UPDATE `biz_qc_task` SET product_id = 311 WHERE product_id IN (640,543);
UPDATE `biz_defect_record` SET material_id = 311 WHERE material_id IN (640,543);
UPDATE `biz_defect_record` SET product_id = 311 WHERE product_id IN (640,543);
UPDATE `biz_mrp_demand` SET material_id = 311 WHERE material_id IN (640,543);
UPDATE `biz_mrp_demand` SET product_id = 311 WHERE product_id IN (640,543);
UPDATE `biz_outsource_request` SET material_id = 311 WHERE material_id IN (640,543);
UPDATE `biz_outsource_request` SET product_id = 311 WHERE product_id IN (640,543);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (640,543) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 311
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 311 WHERE material_id IN (640,543)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 311) tmp);
DELETE FROM biz_inventory WHERE material_id IN (640,543);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (640,543);

-- ============================================================
-- 支架: 主ID=312(TZJD160102CV) -> 正确编码=TZJD-1601-02CV
-- 合并: id=727(TZJD-1601-02CV), id=496(ZG0015)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJD-1601-02CV' AND id != 312;
UPDATE biz_material SET material_no = 'TZJD-1601-02CV', deleted = 0, status = 'active' WHERE id = 312;

UPDATE `biz_inv_transaction` SET material_id = 312 WHERE material_id IN (727,496);
UPDATE `biz_inv_task_item` SET material_id = 312 WHERE material_id IN (727,496);
UPDATE `biz_inv_history_item` SET material_id = 312 WHERE material_id IN (727,496);
UPDATE `biz_inv_request_item` SET material_id = 312 WHERE material_id IN (727,496);
UPDATE `biz_inv_reservation` SET material_id = 312 WHERE material_id IN (727,496);
UPDATE `biz_inv_transfer_item` SET material_id = 312 WHERE material_id IN (727,496);
UPDATE `biz_inv_stocktaking_item` SET material_id = 312 WHERE material_id IN (727,496);
UPDATE `biz_purchase_request` SET material_id = 312 WHERE material_id IN (727,496);
UPDATE `biz_purchase_order_item` SET material_id = 312 WHERE material_id IN (727,496);
UPDATE `biz_purchase_receipt` SET material_id = 312 WHERE material_id IN (727,496);
UPDATE `biz_purchase_return_item` SET material_id = 312 WHERE material_id IN (727,496);
UPDATE `biz_wo_bom_snapshot` SET material_id = 312 WHERE material_id IN (727,496);
UPDATE `biz_plan_item_material` SET material_id = 312 WHERE material_id IN (727,496);
UPDATE `biz_outsource_delivery_item` SET material_id = 312 WHERE material_id IN (727,496);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 312 WHERE material_id IN (727,496);
UPDATE `biz_sales_order_item` SET product_id = 312 WHERE product_id IN (727,496);
UPDATE `biz_work_order` SET product_id = 312 WHERE product_id IN (727,496);
UPDATE `biz_return_order_item` SET product_id = 312 WHERE product_id IN (727,496);
UPDATE `biz_mrp_production_suggestion` SET product_id = 312 WHERE product_id IN (727,496);
UPDATE `biz_process_route` SET product_id = 312 WHERE product_id IN (727,496);
UPDATE `biz_bom_item` SET material_id = 312 WHERE material_id IN (727,496);
UPDATE `biz_bom_item` SET product_id = 312 WHERE product_id IN (727,496);
UPDATE `biz_qc_task` SET material_id = 312 WHERE material_id IN (727,496);
UPDATE `biz_qc_task` SET product_id = 312 WHERE product_id IN (727,496);
UPDATE `biz_defect_record` SET material_id = 312 WHERE material_id IN (727,496);
UPDATE `biz_defect_record` SET product_id = 312 WHERE product_id IN (727,496);
UPDATE `biz_mrp_demand` SET material_id = 312 WHERE material_id IN (727,496);
UPDATE `biz_mrp_demand` SET product_id = 312 WHERE product_id IN (727,496);
UPDATE `biz_outsource_request` SET material_id = 312 WHERE material_id IN (727,496);
UPDATE `biz_outsource_request` SET product_id = 312 WHERE product_id IN (727,496);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (727,496) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 312
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 312 WHERE material_id IN (727,496)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 312) tmp);
DELETE FROM biz_inventory WHERE material_id IN (727,496);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (727,496);

-- ============================================================
-- 电机座: 主ID=313(TZJD170102CV) -> 正确编码=TZJD-1701-02CV
-- 合并: id=641(TZJD-1701-02CV), id=544(ZG0063)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJD-1701-02CV' AND id != 313;
UPDATE biz_material SET material_no = 'TZJD-1701-02CV', deleted = 0, status = 'active' WHERE id = 313;

UPDATE `biz_inv_transaction` SET material_id = 313 WHERE material_id IN (641,544);
UPDATE `biz_inv_task_item` SET material_id = 313 WHERE material_id IN (641,544);
UPDATE `biz_inv_history_item` SET material_id = 313 WHERE material_id IN (641,544);
UPDATE `biz_inv_request_item` SET material_id = 313 WHERE material_id IN (641,544);
UPDATE `biz_inv_reservation` SET material_id = 313 WHERE material_id IN (641,544);
UPDATE `biz_inv_transfer_item` SET material_id = 313 WHERE material_id IN (641,544);
UPDATE `biz_inv_stocktaking_item` SET material_id = 313 WHERE material_id IN (641,544);
UPDATE `biz_purchase_request` SET material_id = 313 WHERE material_id IN (641,544);
UPDATE `biz_purchase_order_item` SET material_id = 313 WHERE material_id IN (641,544);
UPDATE `biz_purchase_receipt` SET material_id = 313 WHERE material_id IN (641,544);
UPDATE `biz_purchase_return_item` SET material_id = 313 WHERE material_id IN (641,544);
UPDATE `biz_wo_bom_snapshot` SET material_id = 313 WHERE material_id IN (641,544);
UPDATE `biz_plan_item_material` SET material_id = 313 WHERE material_id IN (641,544);
UPDATE `biz_outsource_delivery_item` SET material_id = 313 WHERE material_id IN (641,544);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 313 WHERE material_id IN (641,544);
UPDATE `biz_sales_order_item` SET product_id = 313 WHERE product_id IN (641,544);
UPDATE `biz_work_order` SET product_id = 313 WHERE product_id IN (641,544);
UPDATE `biz_return_order_item` SET product_id = 313 WHERE product_id IN (641,544);
UPDATE `biz_mrp_production_suggestion` SET product_id = 313 WHERE product_id IN (641,544);
UPDATE `biz_process_route` SET product_id = 313 WHERE product_id IN (641,544);
UPDATE `biz_bom_item` SET material_id = 313 WHERE material_id IN (641,544);
UPDATE `biz_bom_item` SET product_id = 313 WHERE product_id IN (641,544);
UPDATE `biz_qc_task` SET material_id = 313 WHERE material_id IN (641,544);
UPDATE `biz_qc_task` SET product_id = 313 WHERE product_id IN (641,544);
UPDATE `biz_defect_record` SET material_id = 313 WHERE material_id IN (641,544);
UPDATE `biz_defect_record` SET product_id = 313 WHERE product_id IN (641,544);
UPDATE `biz_mrp_demand` SET material_id = 313 WHERE material_id IN (641,544);
UPDATE `biz_mrp_demand` SET product_id = 313 WHERE product_id IN (641,544);
UPDATE `biz_outsource_request` SET material_id = 313 WHERE material_id IN (641,544);
UPDATE `biz_outsource_request` SET product_id = 313 WHERE product_id IN (641,544);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (641,544) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 313
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 313 WHERE material_id IN (641,544)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 313) tmp);
DELETE FROM biz_inventory WHERE material_id IN (641,544);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (641,544);

-- ============================================================
-- 底座: 主ID=323(TZJDY20001DCJZ) -> 正确编码=TZJD-Y200-01DCJZ
-- 合并: id=735(TZJD-Y200-01DCJZ), id=507(ZG0026)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJD-Y200-01DCJZ' AND id != 323;
UPDATE biz_material SET material_no = 'TZJD-Y200-01DCJZ', deleted = 0, status = 'active' WHERE id = 323;

UPDATE `biz_inv_transaction` SET material_id = 323 WHERE material_id IN (735,507);
UPDATE `biz_inv_task_item` SET material_id = 323 WHERE material_id IN (735,507);
UPDATE `biz_inv_history_item` SET material_id = 323 WHERE material_id IN (735,507);
UPDATE `biz_inv_request_item` SET material_id = 323 WHERE material_id IN (735,507);
UPDATE `biz_inv_reservation` SET material_id = 323 WHERE material_id IN (735,507);
UPDATE `biz_inv_transfer_item` SET material_id = 323 WHERE material_id IN (735,507);
UPDATE `biz_inv_stocktaking_item` SET material_id = 323 WHERE material_id IN (735,507);
UPDATE `biz_purchase_request` SET material_id = 323 WHERE material_id IN (735,507);
UPDATE `biz_purchase_order_item` SET material_id = 323 WHERE material_id IN (735,507);
UPDATE `biz_purchase_receipt` SET material_id = 323 WHERE material_id IN (735,507);
UPDATE `biz_purchase_return_item` SET material_id = 323 WHERE material_id IN (735,507);
UPDATE `biz_wo_bom_snapshot` SET material_id = 323 WHERE material_id IN (735,507);
UPDATE `biz_plan_item_material` SET material_id = 323 WHERE material_id IN (735,507);
UPDATE `biz_outsource_delivery_item` SET material_id = 323 WHERE material_id IN (735,507);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 323 WHERE material_id IN (735,507);
UPDATE `biz_sales_order_item` SET product_id = 323 WHERE product_id IN (735,507);
UPDATE `biz_work_order` SET product_id = 323 WHERE product_id IN (735,507);
UPDATE `biz_return_order_item` SET product_id = 323 WHERE product_id IN (735,507);
UPDATE `biz_mrp_production_suggestion` SET product_id = 323 WHERE product_id IN (735,507);
UPDATE `biz_process_route` SET product_id = 323 WHERE product_id IN (735,507);
UPDATE `biz_bom_item` SET material_id = 323 WHERE material_id IN (735,507);
UPDATE `biz_bom_item` SET product_id = 323 WHERE product_id IN (735,507);
UPDATE `biz_qc_task` SET material_id = 323 WHERE material_id IN (735,507);
UPDATE `biz_qc_task` SET product_id = 323 WHERE product_id IN (735,507);
UPDATE `biz_defect_record` SET material_id = 323 WHERE material_id IN (735,507);
UPDATE `biz_defect_record` SET product_id = 323 WHERE product_id IN (735,507);
UPDATE `biz_mrp_demand` SET material_id = 323 WHERE material_id IN (735,507);
UPDATE `biz_mrp_demand` SET product_id = 323 WHERE product_id IN (735,507);
UPDATE `biz_outsource_request` SET material_id = 323 WHERE material_id IN (735,507);
UPDATE `biz_outsource_request` SET product_id = 323 WHERE product_id IN (735,507);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (735,507) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 323
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 323 WHERE material_id IN (735,507)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 323) tmp);
DELETE FROM biz_inventory WHERE material_id IN (735,507);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (735,507);

-- ============================================================
-- 底座: 主ID=326(TZJDY22501DCJZ) -> 正确编码=TZJD-Y225-01DCJZ
-- 合并: id=806(TZJD-Y225-01DCJZ), id=534(ZG0053)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJD-Y225-01DCJZ' AND id != 326;
UPDATE biz_material SET material_no = 'TZJD-Y225-01DCJZ', deleted = 0, status = 'active' WHERE id = 326;

UPDATE `biz_inv_transaction` SET material_id = 326 WHERE material_id IN (806,534);
UPDATE `biz_inv_task_item` SET material_id = 326 WHERE material_id IN (806,534);
UPDATE `biz_inv_history_item` SET material_id = 326 WHERE material_id IN (806,534);
UPDATE `biz_inv_request_item` SET material_id = 326 WHERE material_id IN (806,534);
UPDATE `biz_inv_reservation` SET material_id = 326 WHERE material_id IN (806,534);
UPDATE `biz_inv_transfer_item` SET material_id = 326 WHERE material_id IN (806,534);
UPDATE `biz_inv_stocktaking_item` SET material_id = 326 WHERE material_id IN (806,534);
UPDATE `biz_purchase_request` SET material_id = 326 WHERE material_id IN (806,534);
UPDATE `biz_purchase_order_item` SET material_id = 326 WHERE material_id IN (806,534);
UPDATE `biz_purchase_receipt` SET material_id = 326 WHERE material_id IN (806,534);
UPDATE `biz_purchase_return_item` SET material_id = 326 WHERE material_id IN (806,534);
UPDATE `biz_wo_bom_snapshot` SET material_id = 326 WHERE material_id IN (806,534);
UPDATE `biz_plan_item_material` SET material_id = 326 WHERE material_id IN (806,534);
UPDATE `biz_outsource_delivery_item` SET material_id = 326 WHERE material_id IN (806,534);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 326 WHERE material_id IN (806,534);
UPDATE `biz_sales_order_item` SET product_id = 326 WHERE product_id IN (806,534);
UPDATE `biz_work_order` SET product_id = 326 WHERE product_id IN (806,534);
UPDATE `biz_return_order_item` SET product_id = 326 WHERE product_id IN (806,534);
UPDATE `biz_mrp_production_suggestion` SET product_id = 326 WHERE product_id IN (806,534);
UPDATE `biz_process_route` SET product_id = 326 WHERE product_id IN (806,534);
UPDATE `biz_bom_item` SET material_id = 326 WHERE material_id IN (806,534);
UPDATE `biz_bom_item` SET product_id = 326 WHERE product_id IN (806,534);
UPDATE `biz_qc_task` SET material_id = 326 WHERE material_id IN (806,534);
UPDATE `biz_qc_task` SET product_id = 326 WHERE product_id IN (806,534);
UPDATE `biz_defect_record` SET material_id = 326 WHERE material_id IN (806,534);
UPDATE `biz_defect_record` SET product_id = 326 WHERE product_id IN (806,534);
UPDATE `biz_mrp_demand` SET material_id = 326 WHERE material_id IN (806,534);
UPDATE `biz_mrp_demand` SET product_id = 326 WHERE product_id IN (806,534);
UPDATE `biz_outsource_request` SET material_id = 326 WHERE material_id IN (806,534);
UPDATE `biz_outsource_request` SET product_id = 326 WHERE product_id IN (806,534);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (806,534) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 326
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 326 WHERE material_id IN (806,534)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 326) tmp);
DELETE FROM biz_inventory WHERE material_id IN (806,534);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (806,534);

-- ============================================================
-- 底座: 主ID=329(TZJDY25001DCJZ) -> 正确编码=TZJD-Y250-01DCJZ
-- 合并: id=774(TZJD-Y250-01DCJZ), id=602(ZG0121)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJD-Y250-01DCJZ' AND id != 329;
UPDATE biz_material SET material_no = 'TZJD-Y250-01DCJZ', deleted = 0, status = 'active' WHERE id = 329;

UPDATE `biz_inv_transaction` SET material_id = 329 WHERE material_id IN (774,602);
UPDATE `biz_inv_task_item` SET material_id = 329 WHERE material_id IN (774,602);
UPDATE `biz_inv_history_item` SET material_id = 329 WHERE material_id IN (774,602);
UPDATE `biz_inv_request_item` SET material_id = 329 WHERE material_id IN (774,602);
UPDATE `biz_inv_reservation` SET material_id = 329 WHERE material_id IN (774,602);
UPDATE `biz_inv_transfer_item` SET material_id = 329 WHERE material_id IN (774,602);
UPDATE `biz_inv_stocktaking_item` SET material_id = 329 WHERE material_id IN (774,602);
UPDATE `biz_purchase_request` SET material_id = 329 WHERE material_id IN (774,602);
UPDATE `biz_purchase_order_item` SET material_id = 329 WHERE material_id IN (774,602);
UPDATE `biz_purchase_receipt` SET material_id = 329 WHERE material_id IN (774,602);
UPDATE `biz_purchase_return_item` SET material_id = 329 WHERE material_id IN (774,602);
UPDATE `biz_wo_bom_snapshot` SET material_id = 329 WHERE material_id IN (774,602);
UPDATE `biz_plan_item_material` SET material_id = 329 WHERE material_id IN (774,602);
UPDATE `biz_outsource_delivery_item` SET material_id = 329 WHERE material_id IN (774,602);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 329 WHERE material_id IN (774,602);
UPDATE `biz_sales_order_item` SET product_id = 329 WHERE product_id IN (774,602);
UPDATE `biz_work_order` SET product_id = 329 WHERE product_id IN (774,602);
UPDATE `biz_return_order_item` SET product_id = 329 WHERE product_id IN (774,602);
UPDATE `biz_mrp_production_suggestion` SET product_id = 329 WHERE product_id IN (774,602);
UPDATE `biz_process_route` SET product_id = 329 WHERE product_id IN (774,602);
UPDATE `biz_bom_item` SET material_id = 329 WHERE material_id IN (774,602);
UPDATE `biz_bom_item` SET product_id = 329 WHERE product_id IN (774,602);
UPDATE `biz_qc_task` SET material_id = 329 WHERE material_id IN (774,602);
UPDATE `biz_qc_task` SET product_id = 329 WHERE product_id IN (774,602);
UPDATE `biz_defect_record` SET material_id = 329 WHERE material_id IN (774,602);
UPDATE `biz_defect_record` SET product_id = 329 WHERE product_id IN (774,602);
UPDATE `biz_mrp_demand` SET material_id = 329 WHERE material_id IN (774,602);
UPDATE `biz_mrp_demand` SET product_id = 329 WHERE product_id IN (774,602);
UPDATE `biz_outsource_request` SET material_id = 329 WHERE material_id IN (774,602);
UPDATE `biz_outsource_request` SET product_id = 329 WHERE product_id IN (774,602);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (774,602) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 329
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 329 WHERE material_id IN (774,602)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 329) tmp);
DELETE FROM biz_inventory WHERE material_id IN (774,602);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (774,602);

-- ============================================================
-- 底座: 主ID=333(TZJDY31501DCJZ) -> 正确编码=TZJD-Y315-01DCJZ
-- 合并: id=801(TZJD-Y315-01DCJZ), id=528(ZG0047)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJD-Y315-01DCJZ' AND id != 333;
UPDATE biz_material SET material_no = 'TZJD-Y315-01DCJZ', deleted = 0, status = 'active' WHERE id = 333;

UPDATE `biz_inv_transaction` SET material_id = 333 WHERE material_id IN (801,528);
UPDATE `biz_inv_task_item` SET material_id = 333 WHERE material_id IN (801,528);
UPDATE `biz_inv_history_item` SET material_id = 333 WHERE material_id IN (801,528);
UPDATE `biz_inv_request_item` SET material_id = 333 WHERE material_id IN (801,528);
UPDATE `biz_inv_reservation` SET material_id = 333 WHERE material_id IN (801,528);
UPDATE `biz_inv_transfer_item` SET material_id = 333 WHERE material_id IN (801,528);
UPDATE `biz_inv_stocktaking_item` SET material_id = 333 WHERE material_id IN (801,528);
UPDATE `biz_purchase_request` SET material_id = 333 WHERE material_id IN (801,528);
UPDATE `biz_purchase_order_item` SET material_id = 333 WHERE material_id IN (801,528);
UPDATE `biz_purchase_receipt` SET material_id = 333 WHERE material_id IN (801,528);
UPDATE `biz_purchase_return_item` SET material_id = 333 WHERE material_id IN (801,528);
UPDATE `biz_wo_bom_snapshot` SET material_id = 333 WHERE material_id IN (801,528);
UPDATE `biz_plan_item_material` SET material_id = 333 WHERE material_id IN (801,528);
UPDATE `biz_outsource_delivery_item` SET material_id = 333 WHERE material_id IN (801,528);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 333 WHERE material_id IN (801,528);
UPDATE `biz_sales_order_item` SET product_id = 333 WHERE product_id IN (801,528);
UPDATE `biz_work_order` SET product_id = 333 WHERE product_id IN (801,528);
UPDATE `biz_return_order_item` SET product_id = 333 WHERE product_id IN (801,528);
UPDATE `biz_mrp_production_suggestion` SET product_id = 333 WHERE product_id IN (801,528);
UPDATE `biz_process_route` SET product_id = 333 WHERE product_id IN (801,528);
UPDATE `biz_bom_item` SET material_id = 333 WHERE material_id IN (801,528);
UPDATE `biz_bom_item` SET product_id = 333 WHERE product_id IN (801,528);
UPDATE `biz_qc_task` SET material_id = 333 WHERE material_id IN (801,528);
UPDATE `biz_qc_task` SET product_id = 333 WHERE product_id IN (801,528);
UPDATE `biz_defect_record` SET material_id = 333 WHERE material_id IN (801,528);
UPDATE `biz_defect_record` SET product_id = 333 WHERE product_id IN (801,528);
UPDATE `biz_mrp_demand` SET material_id = 333 WHERE material_id IN (801,528);
UPDATE `biz_mrp_demand` SET product_id = 333 WHERE product_id IN (801,528);
UPDATE `biz_outsource_request` SET material_id = 333 WHERE material_id IN (801,528);
UPDATE `biz_outsource_request` SET product_id = 333 WHERE product_id IN (801,528);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (801,528) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 333
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 333 WHERE material_id IN (801,528)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 333) tmp);
DELETE FROM biz_inventory WHERE material_id IN (801,528);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (801,528);

-- ============================================================
-- 底座: 主ID=335(TZJE0901HJ) -> 正确编码=TZJE-0901HJ
-- 合并: id=667(TZJE-0901HJ), id=505(ZG0024)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJE-0901HJ' AND id != 335;
UPDATE biz_material SET material_no = 'TZJE-0901HJ', deleted = 0, status = 'active' WHERE id = 335;

UPDATE `biz_inv_transaction` SET material_id = 335 WHERE material_id IN (667,505);
UPDATE `biz_inv_task_item` SET material_id = 335 WHERE material_id IN (667,505);
UPDATE `biz_inv_history_item` SET material_id = 335 WHERE material_id IN (667,505);
UPDATE `biz_inv_request_item` SET material_id = 335 WHERE material_id IN (667,505);
UPDATE `biz_inv_reservation` SET material_id = 335 WHERE material_id IN (667,505);
UPDATE `biz_inv_transfer_item` SET material_id = 335 WHERE material_id IN (667,505);
UPDATE `biz_inv_stocktaking_item` SET material_id = 335 WHERE material_id IN (667,505);
UPDATE `biz_purchase_request` SET material_id = 335 WHERE material_id IN (667,505);
UPDATE `biz_purchase_order_item` SET material_id = 335 WHERE material_id IN (667,505);
UPDATE `biz_purchase_receipt` SET material_id = 335 WHERE material_id IN (667,505);
UPDATE `biz_purchase_return_item` SET material_id = 335 WHERE material_id IN (667,505);
UPDATE `biz_wo_bom_snapshot` SET material_id = 335 WHERE material_id IN (667,505);
UPDATE `biz_plan_item_material` SET material_id = 335 WHERE material_id IN (667,505);
UPDATE `biz_outsource_delivery_item` SET material_id = 335 WHERE material_id IN (667,505);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 335 WHERE material_id IN (667,505);
UPDATE `biz_sales_order_item` SET product_id = 335 WHERE product_id IN (667,505);
UPDATE `biz_work_order` SET product_id = 335 WHERE product_id IN (667,505);
UPDATE `biz_return_order_item` SET product_id = 335 WHERE product_id IN (667,505);
UPDATE `biz_mrp_production_suggestion` SET product_id = 335 WHERE product_id IN (667,505);
UPDATE `biz_process_route` SET product_id = 335 WHERE product_id IN (667,505);
UPDATE `biz_bom_item` SET material_id = 335 WHERE material_id IN (667,505);
UPDATE `biz_bom_item` SET product_id = 335 WHERE product_id IN (667,505);
UPDATE `biz_qc_task` SET material_id = 335 WHERE material_id IN (667,505);
UPDATE `biz_qc_task` SET product_id = 335 WHERE product_id IN (667,505);
UPDATE `biz_defect_record` SET material_id = 335 WHERE material_id IN (667,505);
UPDATE `biz_defect_record` SET product_id = 335 WHERE product_id IN (667,505);
UPDATE `biz_mrp_demand` SET material_id = 335 WHERE material_id IN (667,505);
UPDATE `biz_mrp_demand` SET product_id = 335 WHERE product_id IN (667,505);
UPDATE `biz_outsource_request` SET material_id = 335 WHERE material_id IN (667,505);
UPDATE `biz_outsource_request` SET product_id = 335 WHERE product_id IN (667,505);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (667,505) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 335
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 335 WHERE material_id IN (667,505)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 335) tmp);
DELETE FROM biz_inventory WHERE material_id IN (667,505);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (667,505);

-- ============================================================
-- 底座: 主ID=337(TZJE091120001CVYC) -> 正确编码=TZJE-0911-200-01CV-YC
-- 合并: id=732(TZJE-0911-200-01CV-YC), id=502(ZG0021)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJE-0911-200-01CV-YC' AND id != 337;
UPDATE biz_material SET material_no = 'TZJE-0911-200-01CV-YC', deleted = 0, status = 'active' WHERE id = 337;

UPDATE `biz_inv_transaction` SET material_id = 337 WHERE material_id IN (732,502);
UPDATE `biz_inv_task_item` SET material_id = 337 WHERE material_id IN (732,502);
UPDATE `biz_inv_history_item` SET material_id = 337 WHERE material_id IN (732,502);
UPDATE `biz_inv_request_item` SET material_id = 337 WHERE material_id IN (732,502);
UPDATE `biz_inv_reservation` SET material_id = 337 WHERE material_id IN (732,502);
UPDATE `biz_inv_transfer_item` SET material_id = 337 WHERE material_id IN (732,502);
UPDATE `biz_inv_stocktaking_item` SET material_id = 337 WHERE material_id IN (732,502);
UPDATE `biz_purchase_request` SET material_id = 337 WHERE material_id IN (732,502);
UPDATE `biz_purchase_order_item` SET material_id = 337 WHERE material_id IN (732,502);
UPDATE `biz_purchase_receipt` SET material_id = 337 WHERE material_id IN (732,502);
UPDATE `biz_purchase_return_item` SET material_id = 337 WHERE material_id IN (732,502);
UPDATE `biz_wo_bom_snapshot` SET material_id = 337 WHERE material_id IN (732,502);
UPDATE `biz_plan_item_material` SET material_id = 337 WHERE material_id IN (732,502);
UPDATE `biz_outsource_delivery_item` SET material_id = 337 WHERE material_id IN (732,502);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 337 WHERE material_id IN (732,502);
UPDATE `biz_sales_order_item` SET product_id = 337 WHERE product_id IN (732,502);
UPDATE `biz_work_order` SET product_id = 337 WHERE product_id IN (732,502);
UPDATE `biz_return_order_item` SET product_id = 337 WHERE product_id IN (732,502);
UPDATE `biz_mrp_production_suggestion` SET product_id = 337 WHERE product_id IN (732,502);
UPDATE `biz_process_route` SET product_id = 337 WHERE product_id IN (732,502);
UPDATE `biz_bom_item` SET material_id = 337 WHERE material_id IN (732,502);
UPDATE `biz_bom_item` SET product_id = 337 WHERE product_id IN (732,502);
UPDATE `biz_qc_task` SET material_id = 337 WHERE material_id IN (732,502);
UPDATE `biz_qc_task` SET product_id = 337 WHERE product_id IN (732,502);
UPDATE `biz_defect_record` SET material_id = 337 WHERE material_id IN (732,502);
UPDATE `biz_defect_record` SET product_id = 337 WHERE product_id IN (732,502);
UPDATE `biz_mrp_demand` SET material_id = 337 WHERE material_id IN (732,502);
UPDATE `biz_mrp_demand` SET product_id = 337 WHERE product_id IN (732,502);
UPDATE `biz_outsource_request` SET material_id = 337 WHERE material_id IN (732,502);
UPDATE `biz_outsource_request` SET product_id = 337 WHERE product_id IN (732,502);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (732,502) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 337
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 337 WHERE material_id IN (732,502)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 337) tmp);
DELETE FROM biz_inventory WHERE material_id IN (732,502);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (732,502);

-- ============================================================
-- 底座: 主ID=338(TZJE091120001ZV) -> 正确编码=TZJE-0911-200-01ZV
-- 合并: id=759(TZJE-0911-200-01ZV), id=589(ZG0108)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJE-0911-200-01ZV' AND id != 338;
UPDATE biz_material SET material_no = 'TZJE-0911-200-01ZV', deleted = 0, status = 'active' WHERE id = 338;

UPDATE `biz_inv_transaction` SET material_id = 338 WHERE material_id IN (759,589);
UPDATE `biz_inv_task_item` SET material_id = 338 WHERE material_id IN (759,589);
UPDATE `biz_inv_history_item` SET material_id = 338 WHERE material_id IN (759,589);
UPDATE `biz_inv_request_item` SET material_id = 338 WHERE material_id IN (759,589);
UPDATE `biz_inv_reservation` SET material_id = 338 WHERE material_id IN (759,589);
UPDATE `biz_inv_transfer_item` SET material_id = 338 WHERE material_id IN (759,589);
UPDATE `biz_inv_stocktaking_item` SET material_id = 338 WHERE material_id IN (759,589);
UPDATE `biz_purchase_request` SET material_id = 338 WHERE material_id IN (759,589);
UPDATE `biz_purchase_order_item` SET material_id = 338 WHERE material_id IN (759,589);
UPDATE `biz_purchase_receipt` SET material_id = 338 WHERE material_id IN (759,589);
UPDATE `biz_purchase_return_item` SET material_id = 338 WHERE material_id IN (759,589);
UPDATE `biz_wo_bom_snapshot` SET material_id = 338 WHERE material_id IN (759,589);
UPDATE `biz_plan_item_material` SET material_id = 338 WHERE material_id IN (759,589);
UPDATE `biz_outsource_delivery_item` SET material_id = 338 WHERE material_id IN (759,589);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 338 WHERE material_id IN (759,589);
UPDATE `biz_sales_order_item` SET product_id = 338 WHERE product_id IN (759,589);
UPDATE `biz_work_order` SET product_id = 338 WHERE product_id IN (759,589);
UPDATE `biz_return_order_item` SET product_id = 338 WHERE product_id IN (759,589);
UPDATE `biz_mrp_production_suggestion` SET product_id = 338 WHERE product_id IN (759,589);
UPDATE `biz_process_route` SET product_id = 338 WHERE product_id IN (759,589);
UPDATE `biz_bom_item` SET material_id = 338 WHERE material_id IN (759,589);
UPDATE `biz_bom_item` SET product_id = 338 WHERE product_id IN (759,589);
UPDATE `biz_qc_task` SET material_id = 338 WHERE material_id IN (759,589);
UPDATE `biz_qc_task` SET product_id = 338 WHERE product_id IN (759,589);
UPDATE `biz_defect_record` SET material_id = 338 WHERE material_id IN (759,589);
UPDATE `biz_defect_record` SET product_id = 338 WHERE product_id IN (759,589);
UPDATE `biz_mrp_demand` SET material_id = 338 WHERE material_id IN (759,589);
UPDATE `biz_mrp_demand` SET product_id = 338 WHERE product_id IN (759,589);
UPDATE `biz_outsource_request` SET material_id = 338 WHERE material_id IN (759,589);
UPDATE `biz_outsource_request` SET product_id = 338 WHERE product_id IN (759,589);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (759,589) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 338
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 338 WHERE material_id IN (759,589)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 338) tmp);
DELETE FROM biz_inventory WHERE material_id IN (759,589);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (759,589);

-- ============================================================
-- 底座: 主ID=340(TZJE091120003ZV) -> 正确编码=TZJE-0911-200-03ZV
-- 合并: id=758(TZJE-0911-200-03ZV), id=588(ZG0107)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJE-0911-200-03ZV' AND id != 340;
UPDATE biz_material SET material_no = 'TZJE-0911-200-03ZV', deleted = 0, status = 'active' WHERE id = 340;

UPDATE `biz_inv_transaction` SET material_id = 340 WHERE material_id IN (758,588);
UPDATE `biz_inv_task_item` SET material_id = 340 WHERE material_id IN (758,588);
UPDATE `biz_inv_history_item` SET material_id = 340 WHERE material_id IN (758,588);
UPDATE `biz_inv_request_item` SET material_id = 340 WHERE material_id IN (758,588);
UPDATE `biz_inv_reservation` SET material_id = 340 WHERE material_id IN (758,588);
UPDATE `biz_inv_transfer_item` SET material_id = 340 WHERE material_id IN (758,588);
UPDATE `biz_inv_stocktaking_item` SET material_id = 340 WHERE material_id IN (758,588);
UPDATE `biz_purchase_request` SET material_id = 340 WHERE material_id IN (758,588);
UPDATE `biz_purchase_order_item` SET material_id = 340 WHERE material_id IN (758,588);
UPDATE `biz_purchase_receipt` SET material_id = 340 WHERE material_id IN (758,588);
UPDATE `biz_purchase_return_item` SET material_id = 340 WHERE material_id IN (758,588);
UPDATE `biz_wo_bom_snapshot` SET material_id = 340 WHERE material_id IN (758,588);
UPDATE `biz_plan_item_material` SET material_id = 340 WHERE material_id IN (758,588);
UPDATE `biz_outsource_delivery_item` SET material_id = 340 WHERE material_id IN (758,588);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 340 WHERE material_id IN (758,588);
UPDATE `biz_sales_order_item` SET product_id = 340 WHERE product_id IN (758,588);
UPDATE `biz_work_order` SET product_id = 340 WHERE product_id IN (758,588);
UPDATE `biz_return_order_item` SET product_id = 340 WHERE product_id IN (758,588);
UPDATE `biz_mrp_production_suggestion` SET product_id = 340 WHERE product_id IN (758,588);
UPDATE `biz_process_route` SET product_id = 340 WHERE product_id IN (758,588);
UPDATE `biz_bom_item` SET material_id = 340 WHERE material_id IN (758,588);
UPDATE `biz_bom_item` SET product_id = 340 WHERE product_id IN (758,588);
UPDATE `biz_qc_task` SET material_id = 340 WHERE material_id IN (758,588);
UPDATE `biz_qc_task` SET product_id = 340 WHERE product_id IN (758,588);
UPDATE `biz_defect_record` SET material_id = 340 WHERE material_id IN (758,588);
UPDATE `biz_defect_record` SET product_id = 340 WHERE product_id IN (758,588);
UPDATE `biz_mrp_demand` SET material_id = 340 WHERE material_id IN (758,588);
UPDATE `biz_mrp_demand` SET product_id = 340 WHERE product_id IN (758,588);
UPDATE `biz_outsource_request` SET material_id = 340 WHERE material_id IN (758,588);
UPDATE `biz_outsource_request` SET product_id = 340 WHERE product_id IN (758,588);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (758,588) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 340
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 340 WHERE material_id IN (758,588)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 340) tmp);
DELETE FROM biz_inventory WHERE material_id IN (758,588);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (758,588);

-- ============================================================
-- 皮带罩: 主ID=343(TZJE1301150003CL/CR) -> 正确编码=TZJE-1301-1500-03CL/CR
-- 合并: id=717(TZJE-1301-1500-03CL/CR), id=519(ZG0038)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJE-1301-1500-03CL/CR' AND id != 343;
UPDATE biz_material SET material_no = 'TZJE-1301-1500-03CL/CR', deleted = 0, status = 'active' WHERE id = 343;

UPDATE `biz_inv_transaction` SET material_id = 343 WHERE material_id IN (717,519);
UPDATE `biz_inv_task_item` SET material_id = 343 WHERE material_id IN (717,519);
UPDATE `biz_inv_history_item` SET material_id = 343 WHERE material_id IN (717,519);
UPDATE `biz_inv_request_item` SET material_id = 343 WHERE material_id IN (717,519);
UPDATE `biz_inv_reservation` SET material_id = 343 WHERE material_id IN (717,519);
UPDATE `biz_inv_transfer_item` SET material_id = 343 WHERE material_id IN (717,519);
UPDATE `biz_inv_stocktaking_item` SET material_id = 343 WHERE material_id IN (717,519);
UPDATE `biz_purchase_request` SET material_id = 343 WHERE material_id IN (717,519);
UPDATE `biz_purchase_order_item` SET material_id = 343 WHERE material_id IN (717,519);
UPDATE `biz_purchase_receipt` SET material_id = 343 WHERE material_id IN (717,519);
UPDATE `biz_purchase_return_item` SET material_id = 343 WHERE material_id IN (717,519);
UPDATE `biz_wo_bom_snapshot` SET material_id = 343 WHERE material_id IN (717,519);
UPDATE `biz_plan_item_material` SET material_id = 343 WHERE material_id IN (717,519);
UPDATE `biz_outsource_delivery_item` SET material_id = 343 WHERE material_id IN (717,519);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 343 WHERE material_id IN (717,519);
UPDATE `biz_sales_order_item` SET product_id = 343 WHERE product_id IN (717,519);
UPDATE `biz_work_order` SET product_id = 343 WHERE product_id IN (717,519);
UPDATE `biz_return_order_item` SET product_id = 343 WHERE product_id IN (717,519);
UPDATE `biz_mrp_production_suggestion` SET product_id = 343 WHERE product_id IN (717,519);
UPDATE `biz_process_route` SET product_id = 343 WHERE product_id IN (717,519);
UPDATE `biz_bom_item` SET material_id = 343 WHERE material_id IN (717,519);
UPDATE `biz_bom_item` SET product_id = 343 WHERE product_id IN (717,519);
UPDATE `biz_qc_task` SET material_id = 343 WHERE material_id IN (717,519);
UPDATE `biz_qc_task` SET product_id = 343 WHERE product_id IN (717,519);
UPDATE `biz_defect_record` SET material_id = 343 WHERE material_id IN (717,519);
UPDATE `biz_defect_record` SET product_id = 343 WHERE product_id IN (717,519);
UPDATE `biz_mrp_demand` SET material_id = 343 WHERE material_id IN (717,519);
UPDATE `biz_mrp_demand` SET product_id = 343 WHERE product_id IN (717,519);
UPDATE `biz_outsource_request` SET material_id = 343 WHERE material_id IN (717,519);
UPDATE `biz_outsource_request` SET product_id = 343 WHERE product_id IN (717,519);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (717,519) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 343
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 343 WHERE material_id IN (717,519)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 343) tmp);
DELETE FROM biz_inventory WHERE material_id IN (717,519);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (717,519);

-- ============================================================
-- 皮带罩: 主ID=347(TZJE1301180001CL/CR) -> 正确编码=TZJE-1301-1800-01CL/CR
-- 合并: id=668(TZJE-1301-1800-01CL/CR), id=567(ZG0086)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJE-1301-1800-01CL/CR' AND id != 347;
UPDATE biz_material SET material_no = 'TZJE-1301-1800-01CL/CR', deleted = 0, status = 'active' WHERE id = 347;

UPDATE `biz_inv_transaction` SET material_id = 347 WHERE material_id IN (668,567);
UPDATE `biz_inv_task_item` SET material_id = 347 WHERE material_id IN (668,567);
UPDATE `biz_inv_history_item` SET material_id = 347 WHERE material_id IN (668,567);
UPDATE `biz_inv_request_item` SET material_id = 347 WHERE material_id IN (668,567);
UPDATE `biz_inv_reservation` SET material_id = 347 WHERE material_id IN (668,567);
UPDATE `biz_inv_transfer_item` SET material_id = 347 WHERE material_id IN (668,567);
UPDATE `biz_inv_stocktaking_item` SET material_id = 347 WHERE material_id IN (668,567);
UPDATE `biz_purchase_request` SET material_id = 347 WHERE material_id IN (668,567);
UPDATE `biz_purchase_order_item` SET material_id = 347 WHERE material_id IN (668,567);
UPDATE `biz_purchase_receipt` SET material_id = 347 WHERE material_id IN (668,567);
UPDATE `biz_purchase_return_item` SET material_id = 347 WHERE material_id IN (668,567);
UPDATE `biz_wo_bom_snapshot` SET material_id = 347 WHERE material_id IN (668,567);
UPDATE `biz_plan_item_material` SET material_id = 347 WHERE material_id IN (668,567);
UPDATE `biz_outsource_delivery_item` SET material_id = 347 WHERE material_id IN (668,567);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 347 WHERE material_id IN (668,567);
UPDATE `biz_sales_order_item` SET product_id = 347 WHERE product_id IN (668,567);
UPDATE `biz_work_order` SET product_id = 347 WHERE product_id IN (668,567);
UPDATE `biz_return_order_item` SET product_id = 347 WHERE product_id IN (668,567);
UPDATE `biz_mrp_production_suggestion` SET product_id = 347 WHERE product_id IN (668,567);
UPDATE `biz_process_route` SET product_id = 347 WHERE product_id IN (668,567);
UPDATE `biz_bom_item` SET material_id = 347 WHERE material_id IN (668,567);
UPDATE `biz_bom_item` SET product_id = 347 WHERE product_id IN (668,567);
UPDATE `biz_qc_task` SET material_id = 347 WHERE material_id IN (668,567);
UPDATE `biz_qc_task` SET product_id = 347 WHERE product_id IN (668,567);
UPDATE `biz_defect_record` SET material_id = 347 WHERE material_id IN (668,567);
UPDATE `biz_defect_record` SET product_id = 347 WHERE product_id IN (668,567);
UPDATE `biz_mrp_demand` SET material_id = 347 WHERE material_id IN (668,567);
UPDATE `biz_mrp_demand` SET product_id = 347 WHERE product_id IN (668,567);
UPDATE `biz_outsource_request` SET material_id = 347 WHERE material_id IN (668,567);
UPDATE `biz_outsource_request` SET product_id = 347 WHERE product_id IN (668,567);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (668,567) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 347
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 347 WHERE material_id IN (668,567)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 347) tmp);
DELETE FROM biz_inventory WHERE material_id IN (668,567);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (668,567);

-- ============================================================
-- 皮带罩: 主ID=348(TZJE130162001CV) -> 正确编码=TZJE-1301-620-01CV
-- 合并: id=739(TZJE-1301-620-01CV), id=511(ZG0030)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJE-1301-620-01CV' AND id != 348;
UPDATE biz_material SET material_no = 'TZJE-1301-620-01CV', deleted = 0, status = 'active' WHERE id = 348;

UPDATE `biz_inv_transaction` SET material_id = 348 WHERE material_id IN (739,511);
UPDATE `biz_inv_task_item` SET material_id = 348 WHERE material_id IN (739,511);
UPDATE `biz_inv_history_item` SET material_id = 348 WHERE material_id IN (739,511);
UPDATE `biz_inv_request_item` SET material_id = 348 WHERE material_id IN (739,511);
UPDATE `biz_inv_reservation` SET material_id = 348 WHERE material_id IN (739,511);
UPDATE `biz_inv_transfer_item` SET material_id = 348 WHERE material_id IN (739,511);
UPDATE `biz_inv_stocktaking_item` SET material_id = 348 WHERE material_id IN (739,511);
UPDATE `biz_purchase_request` SET material_id = 348 WHERE material_id IN (739,511);
UPDATE `biz_purchase_order_item` SET material_id = 348 WHERE material_id IN (739,511);
UPDATE `biz_purchase_receipt` SET material_id = 348 WHERE material_id IN (739,511);
UPDATE `biz_purchase_return_item` SET material_id = 348 WHERE material_id IN (739,511);
UPDATE `biz_wo_bom_snapshot` SET material_id = 348 WHERE material_id IN (739,511);
UPDATE `biz_plan_item_material` SET material_id = 348 WHERE material_id IN (739,511);
UPDATE `biz_outsource_delivery_item` SET material_id = 348 WHERE material_id IN (739,511);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 348 WHERE material_id IN (739,511);
UPDATE `biz_sales_order_item` SET product_id = 348 WHERE product_id IN (739,511);
UPDATE `biz_work_order` SET product_id = 348 WHERE product_id IN (739,511);
UPDATE `biz_return_order_item` SET product_id = 348 WHERE product_id IN (739,511);
UPDATE `biz_mrp_production_suggestion` SET product_id = 348 WHERE product_id IN (739,511);
UPDATE `biz_process_route` SET product_id = 348 WHERE product_id IN (739,511);
UPDATE `biz_bom_item` SET material_id = 348 WHERE material_id IN (739,511);
UPDATE `biz_bom_item` SET product_id = 348 WHERE product_id IN (739,511);
UPDATE `biz_qc_task` SET material_id = 348 WHERE material_id IN (739,511);
UPDATE `biz_qc_task` SET product_id = 348 WHERE product_id IN (739,511);
UPDATE `biz_defect_record` SET material_id = 348 WHERE material_id IN (739,511);
UPDATE `biz_defect_record` SET product_id = 348 WHERE product_id IN (739,511);
UPDATE `biz_mrp_demand` SET material_id = 348 WHERE material_id IN (739,511);
UPDATE `biz_mrp_demand` SET product_id = 348 WHERE product_id IN (739,511);
UPDATE `biz_outsource_request` SET material_id = 348 WHERE material_id IN (739,511);
UPDATE `biz_outsource_request` SET product_id = 348 WHERE product_id IN (739,511);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (739,511) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 348
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 348 WHERE material_id IN (739,511)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 348) tmp);
DELETE FROM biz_inventory WHERE material_id IN (739,511);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (739,511);

-- ============================================================
-- 皮带罩: 主ID=349(TZJE130162002CV) -> 正确编码=TZJE-1301-620-02CV
-- 合并: id=731(TZJE-1301-620-02CV), id=501(ZG0020)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJE-1301-620-02CV' AND id != 349;
UPDATE biz_material SET material_no = 'TZJE-1301-620-02CV', deleted = 0, status = 'active' WHERE id = 349;

UPDATE `biz_inv_transaction` SET material_id = 349 WHERE material_id IN (731,501);
UPDATE `biz_inv_task_item` SET material_id = 349 WHERE material_id IN (731,501);
UPDATE `biz_inv_history_item` SET material_id = 349 WHERE material_id IN (731,501);
UPDATE `biz_inv_request_item` SET material_id = 349 WHERE material_id IN (731,501);
UPDATE `biz_inv_reservation` SET material_id = 349 WHERE material_id IN (731,501);
UPDATE `biz_inv_transfer_item` SET material_id = 349 WHERE material_id IN (731,501);
UPDATE `biz_inv_stocktaking_item` SET material_id = 349 WHERE material_id IN (731,501);
UPDATE `biz_purchase_request` SET material_id = 349 WHERE material_id IN (731,501);
UPDATE `biz_purchase_order_item` SET material_id = 349 WHERE material_id IN (731,501);
UPDATE `biz_purchase_receipt` SET material_id = 349 WHERE material_id IN (731,501);
UPDATE `biz_purchase_return_item` SET material_id = 349 WHERE material_id IN (731,501);
UPDATE `biz_wo_bom_snapshot` SET material_id = 349 WHERE material_id IN (731,501);
UPDATE `biz_plan_item_material` SET material_id = 349 WHERE material_id IN (731,501);
UPDATE `biz_outsource_delivery_item` SET material_id = 349 WHERE material_id IN (731,501);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 349 WHERE material_id IN (731,501);
UPDATE `biz_sales_order_item` SET product_id = 349 WHERE product_id IN (731,501);
UPDATE `biz_work_order` SET product_id = 349 WHERE product_id IN (731,501);
UPDATE `biz_return_order_item` SET product_id = 349 WHERE product_id IN (731,501);
UPDATE `biz_mrp_production_suggestion` SET product_id = 349 WHERE product_id IN (731,501);
UPDATE `biz_process_route` SET product_id = 349 WHERE product_id IN (731,501);
UPDATE `biz_bom_item` SET material_id = 349 WHERE material_id IN (731,501);
UPDATE `biz_bom_item` SET product_id = 349 WHERE product_id IN (731,501);
UPDATE `biz_qc_task` SET material_id = 349 WHERE material_id IN (731,501);
UPDATE `biz_qc_task` SET product_id = 349 WHERE product_id IN (731,501);
UPDATE `biz_defect_record` SET material_id = 349 WHERE material_id IN (731,501);
UPDATE `biz_defect_record` SET product_id = 349 WHERE product_id IN (731,501);
UPDATE `biz_mrp_demand` SET material_id = 349 WHERE material_id IN (731,501);
UPDATE `biz_mrp_demand` SET product_id = 349 WHERE product_id IN (731,501);
UPDATE `biz_outsource_request` SET material_id = 349 WHERE material_id IN (731,501);
UPDATE `biz_outsource_request` SET product_id = 349 WHERE product_id IN (731,501);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (731,501) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 349
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 349 WHERE material_id IN (731,501)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 349) tmp);
DELETE FROM biz_inventory WHERE material_id IN (731,501);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (731,501);

-- ============================================================
-- 支架: 主ID=356(TZJE160101CVYC) -> 正确编码=TZJE-1601-01CV-YC
-- 合并: id=740(TZJE-1601-01CV-YC), id=512(ZG0031)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJE-1601-01CV-YC' AND id != 356;
UPDATE biz_material SET material_no = 'TZJE-1601-01CV-YC', deleted = 0, status = 'active' WHERE id = 356;

UPDATE `biz_inv_transaction` SET material_id = 356 WHERE material_id IN (740,512);
UPDATE `biz_inv_task_item` SET material_id = 356 WHERE material_id IN (740,512);
UPDATE `biz_inv_history_item` SET material_id = 356 WHERE material_id IN (740,512);
UPDATE `biz_inv_request_item` SET material_id = 356 WHERE material_id IN (740,512);
UPDATE `biz_inv_reservation` SET material_id = 356 WHERE material_id IN (740,512);
UPDATE `biz_inv_transfer_item` SET material_id = 356 WHERE material_id IN (740,512);
UPDATE `biz_inv_stocktaking_item` SET material_id = 356 WHERE material_id IN (740,512);
UPDATE `biz_purchase_request` SET material_id = 356 WHERE material_id IN (740,512);
UPDATE `biz_purchase_order_item` SET material_id = 356 WHERE material_id IN (740,512);
UPDATE `biz_purchase_receipt` SET material_id = 356 WHERE material_id IN (740,512);
UPDATE `biz_purchase_return_item` SET material_id = 356 WHERE material_id IN (740,512);
UPDATE `biz_wo_bom_snapshot` SET material_id = 356 WHERE material_id IN (740,512);
UPDATE `biz_plan_item_material` SET material_id = 356 WHERE material_id IN (740,512);
UPDATE `biz_outsource_delivery_item` SET material_id = 356 WHERE material_id IN (740,512);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 356 WHERE material_id IN (740,512);
UPDATE `biz_sales_order_item` SET product_id = 356 WHERE product_id IN (740,512);
UPDATE `biz_work_order` SET product_id = 356 WHERE product_id IN (740,512);
UPDATE `biz_return_order_item` SET product_id = 356 WHERE product_id IN (740,512);
UPDATE `biz_mrp_production_suggestion` SET product_id = 356 WHERE product_id IN (740,512);
UPDATE `biz_process_route` SET product_id = 356 WHERE product_id IN (740,512);
UPDATE `biz_bom_item` SET material_id = 356 WHERE material_id IN (740,512);
UPDATE `biz_bom_item` SET product_id = 356 WHERE product_id IN (740,512);
UPDATE `biz_qc_task` SET material_id = 356 WHERE material_id IN (740,512);
UPDATE `biz_qc_task` SET product_id = 356 WHERE product_id IN (740,512);
UPDATE `biz_defect_record` SET material_id = 356 WHERE material_id IN (740,512);
UPDATE `biz_defect_record` SET product_id = 356 WHERE product_id IN (740,512);
UPDATE `biz_mrp_demand` SET material_id = 356 WHERE material_id IN (740,512);
UPDATE `biz_mrp_demand` SET product_id = 356 WHERE product_id IN (740,512);
UPDATE `biz_outsource_request` SET material_id = 356 WHERE material_id IN (740,512);
UPDATE `biz_outsource_request` SET product_id = 356 WHERE product_id IN (740,512);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (740,512) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 356
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 356 WHERE material_id IN (740,512)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 356) tmp);
DELETE FROM biz_inventory WHERE material_id IN (740,512);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (740,512);

-- ============================================================
-- 支架: 主ID=357(TZJE160102CV) -> 正确编码=TZJE-1601-02CV
-- 合并: id=733(TZJE-1601-02CV), id=503(ZG0022)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJE-1601-02CV' AND id != 357;
UPDATE biz_material SET material_no = 'TZJE-1601-02CV', deleted = 0, status = 'active' WHERE id = 357;

UPDATE `biz_inv_transaction` SET material_id = 357 WHERE material_id IN (733,503);
UPDATE `biz_inv_task_item` SET material_id = 357 WHERE material_id IN (733,503);
UPDATE `biz_inv_history_item` SET material_id = 357 WHERE material_id IN (733,503);
UPDATE `biz_inv_request_item` SET material_id = 357 WHERE material_id IN (733,503);
UPDATE `biz_inv_reservation` SET material_id = 357 WHERE material_id IN (733,503);
UPDATE `biz_inv_transfer_item` SET material_id = 357 WHERE material_id IN (733,503);
UPDATE `biz_inv_stocktaking_item` SET material_id = 357 WHERE material_id IN (733,503);
UPDATE `biz_purchase_request` SET material_id = 357 WHERE material_id IN (733,503);
UPDATE `biz_purchase_order_item` SET material_id = 357 WHERE material_id IN (733,503);
UPDATE `biz_purchase_receipt` SET material_id = 357 WHERE material_id IN (733,503);
UPDATE `biz_purchase_return_item` SET material_id = 357 WHERE material_id IN (733,503);
UPDATE `biz_wo_bom_snapshot` SET material_id = 357 WHERE material_id IN (733,503);
UPDATE `biz_plan_item_material` SET material_id = 357 WHERE material_id IN (733,503);
UPDATE `biz_outsource_delivery_item` SET material_id = 357 WHERE material_id IN (733,503);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 357 WHERE material_id IN (733,503);
UPDATE `biz_sales_order_item` SET product_id = 357 WHERE product_id IN (733,503);
UPDATE `biz_work_order` SET product_id = 357 WHERE product_id IN (733,503);
UPDATE `biz_return_order_item` SET product_id = 357 WHERE product_id IN (733,503);
UPDATE `biz_mrp_production_suggestion` SET product_id = 357 WHERE product_id IN (733,503);
UPDATE `biz_process_route` SET product_id = 357 WHERE product_id IN (733,503);
UPDATE `biz_bom_item` SET material_id = 357 WHERE material_id IN (733,503);
UPDATE `biz_bom_item` SET product_id = 357 WHERE product_id IN (733,503);
UPDATE `biz_qc_task` SET material_id = 357 WHERE material_id IN (733,503);
UPDATE `biz_qc_task` SET product_id = 357 WHERE product_id IN (733,503);
UPDATE `biz_defect_record` SET material_id = 357 WHERE material_id IN (733,503);
UPDATE `biz_defect_record` SET product_id = 357 WHERE product_id IN (733,503);
UPDATE `biz_mrp_demand` SET material_id = 357 WHERE material_id IN (733,503);
UPDATE `biz_mrp_demand` SET product_id = 357 WHERE product_id IN (733,503);
UPDATE `biz_outsource_request` SET material_id = 357 WHERE material_id IN (733,503);
UPDATE `biz_outsource_request` SET product_id = 357 WHERE product_id IN (733,503);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (733,503) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 357
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 357 WHERE material_id IN (733,503)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 357) tmp);
DELETE FROM biz_inventory WHERE material_id IN (733,503);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (733,503);

-- ============================================================
-- 电机座: 主ID=359(TZJE170102CV) -> 正确编码=TZJE-1701-02CV
-- 合并: id=734(TZJE-1701-02CV), id=504(ZG0023)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJE-1701-02CV' AND id != 359;
UPDATE biz_material SET material_no = 'TZJE-1701-02CV', deleted = 0, status = 'active' WHERE id = 359;

UPDATE `biz_inv_transaction` SET material_id = 359 WHERE material_id IN (734,504);
UPDATE `biz_inv_task_item` SET material_id = 359 WHERE material_id IN (734,504);
UPDATE `biz_inv_history_item` SET material_id = 359 WHERE material_id IN (734,504);
UPDATE `biz_inv_request_item` SET material_id = 359 WHERE material_id IN (734,504);
UPDATE `biz_inv_reservation` SET material_id = 359 WHERE material_id IN (734,504);
UPDATE `biz_inv_transfer_item` SET material_id = 359 WHERE material_id IN (734,504);
UPDATE `biz_inv_stocktaking_item` SET material_id = 359 WHERE material_id IN (734,504);
UPDATE `biz_purchase_request` SET material_id = 359 WHERE material_id IN (734,504);
UPDATE `biz_purchase_order_item` SET material_id = 359 WHERE material_id IN (734,504);
UPDATE `biz_purchase_receipt` SET material_id = 359 WHERE material_id IN (734,504);
UPDATE `biz_purchase_return_item` SET material_id = 359 WHERE material_id IN (734,504);
UPDATE `biz_wo_bom_snapshot` SET material_id = 359 WHERE material_id IN (734,504);
UPDATE `biz_plan_item_material` SET material_id = 359 WHERE material_id IN (734,504);
UPDATE `biz_outsource_delivery_item` SET material_id = 359 WHERE material_id IN (734,504);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 359 WHERE material_id IN (734,504);
UPDATE `biz_sales_order_item` SET product_id = 359 WHERE product_id IN (734,504);
UPDATE `biz_work_order` SET product_id = 359 WHERE product_id IN (734,504);
UPDATE `biz_return_order_item` SET product_id = 359 WHERE product_id IN (734,504);
UPDATE `biz_mrp_production_suggestion` SET product_id = 359 WHERE product_id IN (734,504);
UPDATE `biz_process_route` SET product_id = 359 WHERE product_id IN (734,504);
UPDATE `biz_bom_item` SET material_id = 359 WHERE material_id IN (734,504);
UPDATE `biz_bom_item` SET product_id = 359 WHERE product_id IN (734,504);
UPDATE `biz_qc_task` SET material_id = 359 WHERE material_id IN (734,504);
UPDATE `biz_qc_task` SET product_id = 359 WHERE product_id IN (734,504);
UPDATE `biz_defect_record` SET material_id = 359 WHERE material_id IN (734,504);
UPDATE `biz_defect_record` SET product_id = 359 WHERE product_id IN (734,504);
UPDATE `biz_mrp_demand` SET material_id = 359 WHERE material_id IN (734,504);
UPDATE `biz_mrp_demand` SET product_id = 359 WHERE product_id IN (734,504);
UPDATE `biz_outsource_request` SET material_id = 359 WHERE material_id IN (734,504);
UPDATE `biz_outsource_request` SET product_id = 359 WHERE product_id IN (734,504);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (734,504) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 359
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 359 WHERE material_id IN (734,504)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 359) tmp);
DELETE FROM biz_inventory WHERE material_id IN (734,504);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (734,504);

-- ============================================================
-- 电机座: 主ID=360(TZJE170103CVYC) -> 正确编码=TZJE-1701-03CV-YC
-- 合并: id=741(TZJE-1701-03CV-YC), id=513(ZG0032)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJE-1701-03CV-YC' AND id != 360;
UPDATE biz_material SET material_no = 'TZJE-1701-03CV-YC', deleted = 0, status = 'active' WHERE id = 360;

UPDATE `biz_inv_transaction` SET material_id = 360 WHERE material_id IN (741,513);
UPDATE `biz_inv_task_item` SET material_id = 360 WHERE material_id IN (741,513);
UPDATE `biz_inv_history_item` SET material_id = 360 WHERE material_id IN (741,513);
UPDATE `biz_inv_request_item` SET material_id = 360 WHERE material_id IN (741,513);
UPDATE `biz_inv_reservation` SET material_id = 360 WHERE material_id IN (741,513);
UPDATE `biz_inv_transfer_item` SET material_id = 360 WHERE material_id IN (741,513);
UPDATE `biz_inv_stocktaking_item` SET material_id = 360 WHERE material_id IN (741,513);
UPDATE `biz_purchase_request` SET material_id = 360 WHERE material_id IN (741,513);
UPDATE `biz_purchase_order_item` SET material_id = 360 WHERE material_id IN (741,513);
UPDATE `biz_purchase_receipt` SET material_id = 360 WHERE material_id IN (741,513);
UPDATE `biz_purchase_return_item` SET material_id = 360 WHERE material_id IN (741,513);
UPDATE `biz_wo_bom_snapshot` SET material_id = 360 WHERE material_id IN (741,513);
UPDATE `biz_plan_item_material` SET material_id = 360 WHERE material_id IN (741,513);
UPDATE `biz_outsource_delivery_item` SET material_id = 360 WHERE material_id IN (741,513);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 360 WHERE material_id IN (741,513);
UPDATE `biz_sales_order_item` SET product_id = 360 WHERE product_id IN (741,513);
UPDATE `biz_work_order` SET product_id = 360 WHERE product_id IN (741,513);
UPDATE `biz_return_order_item` SET product_id = 360 WHERE product_id IN (741,513);
UPDATE `biz_mrp_production_suggestion` SET product_id = 360 WHERE product_id IN (741,513);
UPDATE `biz_process_route` SET product_id = 360 WHERE product_id IN (741,513);
UPDATE `biz_bom_item` SET material_id = 360 WHERE material_id IN (741,513);
UPDATE `biz_bom_item` SET product_id = 360 WHERE product_id IN (741,513);
UPDATE `biz_qc_task` SET material_id = 360 WHERE material_id IN (741,513);
UPDATE `biz_qc_task` SET product_id = 360 WHERE product_id IN (741,513);
UPDATE `biz_defect_record` SET material_id = 360 WHERE material_id IN (741,513);
UPDATE `biz_defect_record` SET product_id = 360 WHERE product_id IN (741,513);
UPDATE `biz_mrp_demand` SET material_id = 360 WHERE material_id IN (741,513);
UPDATE `biz_mrp_demand` SET product_id = 360 WHERE product_id IN (741,513);
UPDATE `biz_outsource_request` SET material_id = 360 WHERE material_id IN (741,513);
UPDATE `biz_outsource_request` SET product_id = 360 WHERE product_id IN (741,513);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (741,513) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 360
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 360 WHERE material_id IN (741,513)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 360) tmp);
DELETE FROM biz_inventory WHERE material_id IN (741,513);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (741,513);

-- ============================================================
-- 底座: 主ID=364(TZJEY25001DCJZ) -> 正确编码=TZJE-Y250-01DCJZ
-- 合并: id=771(TZJE-Y250-01DCJZ), id=600(ZG0119)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJE-Y250-01DCJZ' AND id != 364;
UPDATE biz_material SET material_no = 'TZJE-Y250-01DCJZ', deleted = 0, status = 'active' WHERE id = 364;

UPDATE `biz_inv_transaction` SET material_id = 364 WHERE material_id IN (771,600);
UPDATE `biz_inv_task_item` SET material_id = 364 WHERE material_id IN (771,600);
UPDATE `biz_inv_history_item` SET material_id = 364 WHERE material_id IN (771,600);
UPDATE `biz_inv_request_item` SET material_id = 364 WHERE material_id IN (771,600);
UPDATE `biz_inv_reservation` SET material_id = 364 WHERE material_id IN (771,600);
UPDATE `biz_inv_transfer_item` SET material_id = 364 WHERE material_id IN (771,600);
UPDATE `biz_inv_stocktaking_item` SET material_id = 364 WHERE material_id IN (771,600);
UPDATE `biz_purchase_request` SET material_id = 364 WHERE material_id IN (771,600);
UPDATE `biz_purchase_order_item` SET material_id = 364 WHERE material_id IN (771,600);
UPDATE `biz_purchase_receipt` SET material_id = 364 WHERE material_id IN (771,600);
UPDATE `biz_purchase_return_item` SET material_id = 364 WHERE material_id IN (771,600);
UPDATE `biz_wo_bom_snapshot` SET material_id = 364 WHERE material_id IN (771,600);
UPDATE `biz_plan_item_material` SET material_id = 364 WHERE material_id IN (771,600);
UPDATE `biz_outsource_delivery_item` SET material_id = 364 WHERE material_id IN (771,600);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 364 WHERE material_id IN (771,600);
UPDATE `biz_sales_order_item` SET product_id = 364 WHERE product_id IN (771,600);
UPDATE `biz_work_order` SET product_id = 364 WHERE product_id IN (771,600);
UPDATE `biz_return_order_item` SET product_id = 364 WHERE product_id IN (771,600);
UPDATE `biz_mrp_production_suggestion` SET product_id = 364 WHERE product_id IN (771,600);
UPDATE `biz_process_route` SET product_id = 364 WHERE product_id IN (771,600);
UPDATE `biz_bom_item` SET material_id = 364 WHERE material_id IN (771,600);
UPDATE `biz_bom_item` SET product_id = 364 WHERE product_id IN (771,600);
UPDATE `biz_qc_task` SET material_id = 364 WHERE material_id IN (771,600);
UPDATE `biz_qc_task` SET product_id = 364 WHERE product_id IN (771,600);
UPDATE `biz_defect_record` SET material_id = 364 WHERE material_id IN (771,600);
UPDATE `biz_defect_record` SET product_id = 364 WHERE product_id IN (771,600);
UPDATE `biz_mrp_demand` SET material_id = 364 WHERE material_id IN (771,600);
UPDATE `biz_mrp_demand` SET product_id = 364 WHERE product_id IN (771,600);
UPDATE `biz_outsource_request` SET material_id = 364 WHERE material_id IN (771,600);
UPDATE `biz_outsource_request` SET product_id = 364 WHERE product_id IN (771,600);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (771,600) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 364
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 364 WHERE material_id IN (771,600)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 364) tmp);
DELETE FROM biz_inventory WHERE material_id IN (771,600);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (771,600);

-- ============================================================
-- 底座: 主ID=369(TZJF0901HJ) -> 正确编码=TZJF-0901HJ
-- 合并: id=669(TZJF-0901HJ), id=499(ZG0018)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJF-0901HJ' AND id != 369;
UPDATE biz_material SET material_no = 'TZJF-0901HJ', deleted = 0, status = 'active' WHERE id = 369;

UPDATE `biz_inv_transaction` SET material_id = 369 WHERE material_id IN (669,499);
UPDATE `biz_inv_task_item` SET material_id = 369 WHERE material_id IN (669,499);
UPDATE `biz_inv_history_item` SET material_id = 369 WHERE material_id IN (669,499);
UPDATE `biz_inv_request_item` SET material_id = 369 WHERE material_id IN (669,499);
UPDATE `biz_inv_reservation` SET material_id = 369 WHERE material_id IN (669,499);
UPDATE `biz_inv_transfer_item` SET material_id = 369 WHERE material_id IN (669,499);
UPDATE `biz_inv_stocktaking_item` SET material_id = 369 WHERE material_id IN (669,499);
UPDATE `biz_purchase_request` SET material_id = 369 WHERE material_id IN (669,499);
UPDATE `biz_purchase_order_item` SET material_id = 369 WHERE material_id IN (669,499);
UPDATE `biz_purchase_receipt` SET material_id = 369 WHERE material_id IN (669,499);
UPDATE `biz_purchase_return_item` SET material_id = 369 WHERE material_id IN (669,499);
UPDATE `biz_wo_bom_snapshot` SET material_id = 369 WHERE material_id IN (669,499);
UPDATE `biz_plan_item_material` SET material_id = 369 WHERE material_id IN (669,499);
UPDATE `biz_outsource_delivery_item` SET material_id = 369 WHERE material_id IN (669,499);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 369 WHERE material_id IN (669,499);
UPDATE `biz_sales_order_item` SET product_id = 369 WHERE product_id IN (669,499);
UPDATE `biz_work_order` SET product_id = 369 WHERE product_id IN (669,499);
UPDATE `biz_return_order_item` SET product_id = 369 WHERE product_id IN (669,499);
UPDATE `biz_mrp_production_suggestion` SET product_id = 369 WHERE product_id IN (669,499);
UPDATE `biz_process_route` SET product_id = 369 WHERE product_id IN (669,499);
UPDATE `biz_bom_item` SET material_id = 369 WHERE material_id IN (669,499);
UPDATE `biz_bom_item` SET product_id = 369 WHERE product_id IN (669,499);
UPDATE `biz_qc_task` SET material_id = 369 WHERE material_id IN (669,499);
UPDATE `biz_qc_task` SET product_id = 369 WHERE product_id IN (669,499);
UPDATE `biz_defect_record` SET material_id = 369 WHERE material_id IN (669,499);
UPDATE `biz_defect_record` SET product_id = 369 WHERE product_id IN (669,499);
UPDATE `biz_mrp_demand` SET material_id = 369 WHERE material_id IN (669,499);
UPDATE `biz_mrp_demand` SET product_id = 369 WHERE product_id IN (669,499);
UPDATE `biz_outsource_request` SET material_id = 369 WHERE material_id IN (669,499);
UPDATE `biz_outsource_request` SET product_id = 369 WHERE product_id IN (669,499);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (669,499) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 369
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 369 WHERE material_id IN (669,499)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 369) tmp);
DELETE FROM biz_inventory WHERE material_id IN (669,499);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (669,499);

-- ============================================================
-- 泵支架: 主ID=370(TZJF1000108720HJ) -> 正确编码=TZJF-100-0108-720HJ
-- 合并: id=744(TZJF-100-0108-720HJ), id=574(ZG0093)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJF-100-0108-720HJ' AND id != 370;
UPDATE biz_material SET material_no = 'TZJF-100-0108-720HJ', deleted = 0, status = 'active' WHERE id = 370;

UPDATE `biz_inv_transaction` SET material_id = 370 WHERE material_id IN (744,574);
UPDATE `biz_inv_task_item` SET material_id = 370 WHERE material_id IN (744,574);
UPDATE `biz_inv_history_item` SET material_id = 370 WHERE material_id IN (744,574);
UPDATE `biz_inv_request_item` SET material_id = 370 WHERE material_id IN (744,574);
UPDATE `biz_inv_reservation` SET material_id = 370 WHERE material_id IN (744,574);
UPDATE `biz_inv_transfer_item` SET material_id = 370 WHERE material_id IN (744,574);
UPDATE `biz_inv_stocktaking_item` SET material_id = 370 WHERE material_id IN (744,574);
UPDATE `biz_purchase_request` SET material_id = 370 WHERE material_id IN (744,574);
UPDATE `biz_purchase_order_item` SET material_id = 370 WHERE material_id IN (744,574);
UPDATE `biz_purchase_receipt` SET material_id = 370 WHERE material_id IN (744,574);
UPDATE `biz_purchase_return_item` SET material_id = 370 WHERE material_id IN (744,574);
UPDATE `biz_wo_bom_snapshot` SET material_id = 370 WHERE material_id IN (744,574);
UPDATE `biz_plan_item_material` SET material_id = 370 WHERE material_id IN (744,574);
UPDATE `biz_outsource_delivery_item` SET material_id = 370 WHERE material_id IN (744,574);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 370 WHERE material_id IN (744,574);
UPDATE `biz_sales_order_item` SET product_id = 370 WHERE product_id IN (744,574);
UPDATE `biz_work_order` SET product_id = 370 WHERE product_id IN (744,574);
UPDATE `biz_return_order_item` SET product_id = 370 WHERE product_id IN (744,574);
UPDATE `biz_mrp_production_suggestion` SET product_id = 370 WHERE product_id IN (744,574);
UPDATE `biz_process_route` SET product_id = 370 WHERE product_id IN (744,574);
UPDATE `biz_bom_item` SET material_id = 370 WHERE material_id IN (744,574);
UPDATE `biz_bom_item` SET product_id = 370 WHERE product_id IN (744,574);
UPDATE `biz_qc_task` SET material_id = 370 WHERE material_id IN (744,574);
UPDATE `biz_qc_task` SET product_id = 370 WHERE product_id IN (744,574);
UPDATE `biz_defect_record` SET material_id = 370 WHERE material_id IN (744,574);
UPDATE `biz_defect_record` SET product_id = 370 WHERE product_id IN (744,574);
UPDATE `biz_mrp_demand` SET material_id = 370 WHERE material_id IN (744,574);
UPDATE `biz_mrp_demand` SET product_id = 370 WHERE product_id IN (744,574);
UPDATE `biz_outsource_request` SET material_id = 370 WHERE material_id IN (744,574);
UPDATE `biz_outsource_request` SET product_id = 370 WHERE product_id IN (744,574);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (744,574) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 370
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 370 WHERE material_id IN (744,574)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 370) tmp);
DELETE FROM biz_inventory WHERE material_id IN (744,574);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (744,574);

-- ============================================================
-- 水箱部: 主ID=371(TZJF1500604) -> 正确编码=TZJF-150-0604
-- 合并: id=737(TZJF-150-0604), id=509(ZG0028)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJF-150-0604' AND id != 371;
UPDATE biz_material SET material_no = 'TZJF-150-0604', deleted = 0, status = 'active' WHERE id = 371;

UPDATE `biz_inv_transaction` SET material_id = 371 WHERE material_id IN (737,509);
UPDATE `biz_inv_task_item` SET material_id = 371 WHERE material_id IN (737,509);
UPDATE `biz_inv_history_item` SET material_id = 371 WHERE material_id IN (737,509);
UPDATE `biz_inv_request_item` SET material_id = 371 WHERE material_id IN (737,509);
UPDATE `biz_inv_reservation` SET material_id = 371 WHERE material_id IN (737,509);
UPDATE `biz_inv_transfer_item` SET material_id = 371 WHERE material_id IN (737,509);
UPDATE `biz_inv_stocktaking_item` SET material_id = 371 WHERE material_id IN (737,509);
UPDATE `biz_purchase_request` SET material_id = 371 WHERE material_id IN (737,509);
UPDATE `biz_purchase_order_item` SET material_id = 371 WHERE material_id IN (737,509);
UPDATE `biz_purchase_receipt` SET material_id = 371 WHERE material_id IN (737,509);
UPDATE `biz_purchase_return_item` SET material_id = 371 WHERE material_id IN (737,509);
UPDATE `biz_wo_bom_snapshot` SET material_id = 371 WHERE material_id IN (737,509);
UPDATE `biz_plan_item_material` SET material_id = 371 WHERE material_id IN (737,509);
UPDATE `biz_outsource_delivery_item` SET material_id = 371 WHERE material_id IN (737,509);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 371 WHERE material_id IN (737,509);
UPDATE `biz_sales_order_item` SET product_id = 371 WHERE product_id IN (737,509);
UPDATE `biz_work_order` SET product_id = 371 WHERE product_id IN (737,509);
UPDATE `biz_return_order_item` SET product_id = 371 WHERE product_id IN (737,509);
UPDATE `biz_mrp_production_suggestion` SET product_id = 371 WHERE product_id IN (737,509);
UPDATE `biz_process_route` SET product_id = 371 WHERE product_id IN (737,509);
UPDATE `biz_bom_item` SET material_id = 371 WHERE material_id IN (737,509);
UPDATE `biz_bom_item` SET product_id = 371 WHERE product_id IN (737,509);
UPDATE `biz_qc_task` SET material_id = 371 WHERE material_id IN (737,509);
UPDATE `biz_qc_task` SET product_id = 371 WHERE product_id IN (737,509);
UPDATE `biz_defect_record` SET material_id = 371 WHERE material_id IN (737,509);
UPDATE `biz_defect_record` SET product_id = 371 WHERE product_id IN (737,509);
UPDATE `biz_mrp_demand` SET material_id = 371 WHERE material_id IN (737,509);
UPDATE `biz_mrp_demand` SET product_id = 371 WHERE product_id IN (737,509);
UPDATE `biz_outsource_request` SET material_id = 371 WHERE material_id IN (737,509);
UPDATE `biz_outsource_request` SET product_id = 371 WHERE product_id IN (737,509);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (737,509) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 371
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 371 WHERE material_id IN (737,509)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 371) tmp);
DELETE FROM biz_inventory WHERE material_id IN (737,509);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (737,509);

-- ============================================================
-- 底座: 主ID=379(TZJS090120001DC) -> 正确编码=TZJS-0901-200-01DC
-- 合并: id=779(TZJS-0901-200-01DC), id=606(ZG0125)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJS-0901-200-01DC' AND id != 379;
UPDATE biz_material SET material_no = 'TZJS-0901-200-01DC', deleted = 0, status = 'active' WHERE id = 379;

UPDATE `biz_inv_transaction` SET material_id = 379 WHERE material_id IN (779,606);
UPDATE `biz_inv_task_item` SET material_id = 379 WHERE material_id IN (779,606);
UPDATE `biz_inv_history_item` SET material_id = 379 WHERE material_id IN (779,606);
UPDATE `biz_inv_request_item` SET material_id = 379 WHERE material_id IN (779,606);
UPDATE `biz_inv_reservation` SET material_id = 379 WHERE material_id IN (779,606);
UPDATE `biz_inv_transfer_item` SET material_id = 379 WHERE material_id IN (779,606);
UPDATE `biz_inv_stocktaking_item` SET material_id = 379 WHERE material_id IN (779,606);
UPDATE `biz_purchase_request` SET material_id = 379 WHERE material_id IN (779,606);
UPDATE `biz_purchase_order_item` SET material_id = 379 WHERE material_id IN (779,606);
UPDATE `biz_purchase_receipt` SET material_id = 379 WHERE material_id IN (779,606);
UPDATE `biz_purchase_return_item` SET material_id = 379 WHERE material_id IN (779,606);
UPDATE `biz_wo_bom_snapshot` SET material_id = 379 WHERE material_id IN (779,606);
UPDATE `biz_plan_item_material` SET material_id = 379 WHERE material_id IN (779,606);
UPDATE `biz_outsource_delivery_item` SET material_id = 379 WHERE material_id IN (779,606);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 379 WHERE material_id IN (779,606);
UPDATE `biz_sales_order_item` SET product_id = 379 WHERE product_id IN (779,606);
UPDATE `biz_work_order` SET product_id = 379 WHERE product_id IN (779,606);
UPDATE `biz_return_order_item` SET product_id = 379 WHERE product_id IN (779,606);
UPDATE `biz_mrp_production_suggestion` SET product_id = 379 WHERE product_id IN (779,606);
UPDATE `biz_process_route` SET product_id = 379 WHERE product_id IN (779,606);
UPDATE `biz_bom_item` SET material_id = 379 WHERE material_id IN (779,606);
UPDATE `biz_bom_item` SET product_id = 379 WHERE product_id IN (779,606);
UPDATE `biz_qc_task` SET material_id = 379 WHERE material_id IN (779,606);
UPDATE `biz_qc_task` SET product_id = 379 WHERE product_id IN (779,606);
UPDATE `biz_defect_record` SET material_id = 379 WHERE material_id IN (779,606);
UPDATE `biz_defect_record` SET product_id = 379 WHERE product_id IN (779,606);
UPDATE `biz_mrp_demand` SET material_id = 379 WHERE material_id IN (779,606);
UPDATE `biz_mrp_demand` SET product_id = 379 WHERE product_id IN (779,606);
UPDATE `biz_outsource_request` SET material_id = 379 WHERE material_id IN (779,606);
UPDATE `biz_outsource_request` SET product_id = 379 WHERE product_id IN (779,606);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (779,606) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 379
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 379 WHERE material_id IN (779,606)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 379) tmp);
DELETE FROM biz_inventory WHERE material_id IN (779,606);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (779,606);

-- ============================================================
-- 底座: 主ID=380(TZJS090130001DC) -> 正确编码=TZJS-0901-300-01DC
-- 合并: id=721(TZJS-0901-300-01DC), id=490(ZG0009)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJS-0901-300-01DC' AND id != 380;
UPDATE biz_material SET material_no = 'TZJS-0901-300-01DC', deleted = 0, status = 'active' WHERE id = 380;

UPDATE `biz_inv_transaction` SET material_id = 380 WHERE material_id IN (721,490);
UPDATE `biz_inv_task_item` SET material_id = 380 WHERE material_id IN (721,490);
UPDATE `biz_inv_history_item` SET material_id = 380 WHERE material_id IN (721,490);
UPDATE `biz_inv_request_item` SET material_id = 380 WHERE material_id IN (721,490);
UPDATE `biz_inv_reservation` SET material_id = 380 WHERE material_id IN (721,490);
UPDATE `biz_inv_transfer_item` SET material_id = 380 WHERE material_id IN (721,490);
UPDATE `biz_inv_stocktaking_item` SET material_id = 380 WHERE material_id IN (721,490);
UPDATE `biz_purchase_request` SET material_id = 380 WHERE material_id IN (721,490);
UPDATE `biz_purchase_order_item` SET material_id = 380 WHERE material_id IN (721,490);
UPDATE `biz_purchase_receipt` SET material_id = 380 WHERE material_id IN (721,490);
UPDATE `biz_purchase_return_item` SET material_id = 380 WHERE material_id IN (721,490);
UPDATE `biz_wo_bom_snapshot` SET material_id = 380 WHERE material_id IN (721,490);
UPDATE `biz_plan_item_material` SET material_id = 380 WHERE material_id IN (721,490);
UPDATE `biz_outsource_delivery_item` SET material_id = 380 WHERE material_id IN (721,490);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 380 WHERE material_id IN (721,490);
UPDATE `biz_sales_order_item` SET product_id = 380 WHERE product_id IN (721,490);
UPDATE `biz_work_order` SET product_id = 380 WHERE product_id IN (721,490);
UPDATE `biz_return_order_item` SET product_id = 380 WHERE product_id IN (721,490);
UPDATE `biz_mrp_production_suggestion` SET product_id = 380 WHERE product_id IN (721,490);
UPDATE `biz_process_route` SET product_id = 380 WHERE product_id IN (721,490);
UPDATE `biz_bom_item` SET material_id = 380 WHERE material_id IN (721,490);
UPDATE `biz_bom_item` SET product_id = 380 WHERE product_id IN (721,490);
UPDATE `biz_qc_task` SET material_id = 380 WHERE material_id IN (721,490);
UPDATE `biz_qc_task` SET product_id = 380 WHERE product_id IN (721,490);
UPDATE `biz_defect_record` SET material_id = 380 WHERE material_id IN (721,490);
UPDATE `biz_defect_record` SET product_id = 380 WHERE product_id IN (721,490);
UPDATE `biz_mrp_demand` SET material_id = 380 WHERE material_id IN (721,490);
UPDATE `biz_mrp_demand` SET product_id = 380 WHERE product_id IN (721,490);
UPDATE `biz_outsource_request` SET material_id = 380 WHERE material_id IN (721,490);
UPDATE `biz_outsource_request` SET product_id = 380 WHERE product_id IN (721,490);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (721,490) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 380
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 380 WHERE material_id IN (721,490)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 380) tmp);
DELETE FROM biz_inventory WHERE material_id IN (721,490);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (721,490);

-- ============================================================
-- 皮带罩: 主ID=388(TZJS1301200001CL/CR) -> 正确编码=TZJS-1301-2000-01CL/CR
-- 合并: id=712(TZJS-1301-2000-01CL/CR), id=569(ZG0088), id=818(M20260326090452189)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJS-1301-2000-01CL/CR' AND id != 388;
UPDATE biz_material SET material_no = 'TZJS-1301-2000-01CL/CR', deleted = 0, status = 'active' WHERE id = 388;

UPDATE `biz_inv_transaction` SET material_id = 388 WHERE material_id IN (712,569,818);
UPDATE `biz_inv_task_item` SET material_id = 388 WHERE material_id IN (712,569,818);
UPDATE `biz_inv_history_item` SET material_id = 388 WHERE material_id IN (712,569,818);
UPDATE `biz_inv_request_item` SET material_id = 388 WHERE material_id IN (712,569,818);
UPDATE `biz_inv_reservation` SET material_id = 388 WHERE material_id IN (712,569,818);
UPDATE `biz_inv_transfer_item` SET material_id = 388 WHERE material_id IN (712,569,818);
UPDATE `biz_inv_stocktaking_item` SET material_id = 388 WHERE material_id IN (712,569,818);
UPDATE `biz_purchase_request` SET material_id = 388 WHERE material_id IN (712,569,818);
UPDATE `biz_purchase_order_item` SET material_id = 388 WHERE material_id IN (712,569,818);
UPDATE `biz_purchase_receipt` SET material_id = 388 WHERE material_id IN (712,569,818);
UPDATE `biz_purchase_return_item` SET material_id = 388 WHERE material_id IN (712,569,818);
UPDATE `biz_wo_bom_snapshot` SET material_id = 388 WHERE material_id IN (712,569,818);
UPDATE `biz_plan_item_material` SET material_id = 388 WHERE material_id IN (712,569,818);
UPDATE `biz_outsource_delivery_item` SET material_id = 388 WHERE material_id IN (712,569,818);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 388 WHERE material_id IN (712,569,818);
UPDATE `biz_sales_order_item` SET product_id = 388 WHERE product_id IN (712,569,818);
UPDATE `biz_work_order` SET product_id = 388 WHERE product_id IN (712,569,818);
UPDATE `biz_return_order_item` SET product_id = 388 WHERE product_id IN (712,569,818);
UPDATE `biz_mrp_production_suggestion` SET product_id = 388 WHERE product_id IN (712,569,818);
UPDATE `biz_process_route` SET product_id = 388 WHERE product_id IN (712,569,818);
UPDATE `biz_bom_item` SET material_id = 388 WHERE material_id IN (712,569,818);
UPDATE `biz_bom_item` SET product_id = 388 WHERE product_id IN (712,569,818);
UPDATE `biz_qc_task` SET material_id = 388 WHERE material_id IN (712,569,818);
UPDATE `biz_qc_task` SET product_id = 388 WHERE product_id IN (712,569,818);
UPDATE `biz_defect_record` SET material_id = 388 WHERE material_id IN (712,569,818);
UPDATE `biz_defect_record` SET product_id = 388 WHERE product_id IN (712,569,818);
UPDATE `biz_mrp_demand` SET material_id = 388 WHERE material_id IN (712,569,818);
UPDATE `biz_mrp_demand` SET product_id = 388 WHERE product_id IN (712,569,818);
UPDATE `biz_outsource_request` SET material_id = 388 WHERE material_id IN (712,569,818);
UPDATE `biz_outsource_request` SET product_id = 388 WHERE product_id IN (712,569,818);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (712,569,818) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 388
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 388 WHERE material_id IN (712,569,818)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 388) tmp);
DELETE FROM biz_inventory WHERE material_id IN (712,569,818);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (712,569,818);

-- ============================================================
-- 皮带罩: 主ID=390(TZJS1301220001CL/CR) -> 正确编码=TZJS-1301-2200-01CL/CR
-- 合并: id=738(TZJS-1301-2200-01CL/CR), id=510(ZG0029)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJS-1301-2200-01CL/CR' AND id != 390;
UPDATE biz_material SET material_no = 'TZJS-1301-2200-01CL/CR', deleted = 0, status = 'active' WHERE id = 390;

UPDATE `biz_inv_transaction` SET material_id = 390 WHERE material_id IN (738,510);
UPDATE `biz_inv_task_item` SET material_id = 390 WHERE material_id IN (738,510);
UPDATE `biz_inv_history_item` SET material_id = 390 WHERE material_id IN (738,510);
UPDATE `biz_inv_request_item` SET material_id = 390 WHERE material_id IN (738,510);
UPDATE `biz_inv_reservation` SET material_id = 390 WHERE material_id IN (738,510);
UPDATE `biz_inv_transfer_item` SET material_id = 390 WHERE material_id IN (738,510);
UPDATE `biz_inv_stocktaking_item` SET material_id = 390 WHERE material_id IN (738,510);
UPDATE `biz_purchase_request` SET material_id = 390 WHERE material_id IN (738,510);
UPDATE `biz_purchase_order_item` SET material_id = 390 WHERE material_id IN (738,510);
UPDATE `biz_purchase_receipt` SET material_id = 390 WHERE material_id IN (738,510);
UPDATE `biz_purchase_return_item` SET material_id = 390 WHERE material_id IN (738,510);
UPDATE `biz_wo_bom_snapshot` SET material_id = 390 WHERE material_id IN (738,510);
UPDATE `biz_plan_item_material` SET material_id = 390 WHERE material_id IN (738,510);
UPDATE `biz_outsource_delivery_item` SET material_id = 390 WHERE material_id IN (738,510);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 390 WHERE material_id IN (738,510);
UPDATE `biz_sales_order_item` SET product_id = 390 WHERE product_id IN (738,510);
UPDATE `biz_work_order` SET product_id = 390 WHERE product_id IN (738,510);
UPDATE `biz_return_order_item` SET product_id = 390 WHERE product_id IN (738,510);
UPDATE `biz_mrp_production_suggestion` SET product_id = 390 WHERE product_id IN (738,510);
UPDATE `biz_process_route` SET product_id = 390 WHERE product_id IN (738,510);
UPDATE `biz_bom_item` SET material_id = 390 WHERE material_id IN (738,510);
UPDATE `biz_bom_item` SET product_id = 390 WHERE product_id IN (738,510);
UPDATE `biz_qc_task` SET material_id = 390 WHERE material_id IN (738,510);
UPDATE `biz_qc_task` SET product_id = 390 WHERE product_id IN (738,510);
UPDATE `biz_defect_record` SET material_id = 390 WHERE material_id IN (738,510);
UPDATE `biz_defect_record` SET product_id = 390 WHERE product_id IN (738,510);
UPDATE `biz_mrp_demand` SET material_id = 390 WHERE material_id IN (738,510);
UPDATE `biz_mrp_demand` SET product_id = 390 WHERE product_id IN (738,510);
UPDATE `biz_outsource_request` SET material_id = 390 WHERE material_id IN (738,510);
UPDATE `biz_outsource_request` SET product_id = 390 WHERE product_id IN (738,510);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (738,510) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 390
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 390 WHERE material_id IN (738,510)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 390) tmp);
DELETE FROM biz_inventory WHERE material_id IN (738,510);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (738,510);

-- ============================================================
-- 皮带罩: 主ID=392(TZJS1301230001CL/CR) -> 正确编码=TZJS-1301-2300-01CL/CR
-- 合并: id=722(TZJS-1301-2300-01CL/CR), id=491(ZG0010)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJS-1301-2300-01CL/CR' AND id != 392;
UPDATE biz_material SET material_no = 'TZJS-1301-2300-01CL/CR', deleted = 0, status = 'active' WHERE id = 392;

UPDATE `biz_inv_transaction` SET material_id = 392 WHERE material_id IN (722,491);
UPDATE `biz_inv_task_item` SET material_id = 392 WHERE material_id IN (722,491);
UPDATE `biz_inv_history_item` SET material_id = 392 WHERE material_id IN (722,491);
UPDATE `biz_inv_request_item` SET material_id = 392 WHERE material_id IN (722,491);
UPDATE `biz_inv_reservation` SET material_id = 392 WHERE material_id IN (722,491);
UPDATE `biz_inv_transfer_item` SET material_id = 392 WHERE material_id IN (722,491);
UPDATE `biz_inv_stocktaking_item` SET material_id = 392 WHERE material_id IN (722,491);
UPDATE `biz_purchase_request` SET material_id = 392 WHERE material_id IN (722,491);
UPDATE `biz_purchase_order_item` SET material_id = 392 WHERE material_id IN (722,491);
UPDATE `biz_purchase_receipt` SET material_id = 392 WHERE material_id IN (722,491);
UPDATE `biz_purchase_return_item` SET material_id = 392 WHERE material_id IN (722,491);
UPDATE `biz_wo_bom_snapshot` SET material_id = 392 WHERE material_id IN (722,491);
UPDATE `biz_plan_item_material` SET material_id = 392 WHERE material_id IN (722,491);
UPDATE `biz_outsource_delivery_item` SET material_id = 392 WHERE material_id IN (722,491);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 392 WHERE material_id IN (722,491);
UPDATE `biz_sales_order_item` SET product_id = 392 WHERE product_id IN (722,491);
UPDATE `biz_work_order` SET product_id = 392 WHERE product_id IN (722,491);
UPDATE `biz_return_order_item` SET product_id = 392 WHERE product_id IN (722,491);
UPDATE `biz_mrp_production_suggestion` SET product_id = 392 WHERE product_id IN (722,491);
UPDATE `biz_process_route` SET product_id = 392 WHERE product_id IN (722,491);
UPDATE `biz_bom_item` SET material_id = 392 WHERE material_id IN (722,491);
UPDATE `biz_bom_item` SET product_id = 392 WHERE product_id IN (722,491);
UPDATE `biz_qc_task` SET material_id = 392 WHERE material_id IN (722,491);
UPDATE `biz_qc_task` SET product_id = 392 WHERE product_id IN (722,491);
UPDATE `biz_defect_record` SET material_id = 392 WHERE material_id IN (722,491);
UPDATE `biz_defect_record` SET product_id = 392 WHERE product_id IN (722,491);
UPDATE `biz_mrp_demand` SET material_id = 392 WHERE material_id IN (722,491);
UPDATE `biz_mrp_demand` SET product_id = 392 WHERE product_id IN (722,491);
UPDATE `biz_outsource_request` SET material_id = 392 WHERE material_id IN (722,491);
UPDATE `biz_outsource_request` SET product_id = 392 WHERE product_id IN (722,491);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (722,491) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 392
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 392 WHERE material_id IN (722,491)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 392) tmp);
DELETE FROM biz_inventory WHERE material_id IN (722,491);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (722,491);

-- ============================================================
-- 泵支架: 主ID=394(TZJS2500108750K) -> 正确编码=TZJS-250-0108-750K
-- 合并: id=723(TZJS-250-0108-750K), id=492(ZG0011)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJS-250-0108-750K' AND id != 394;
UPDATE biz_material SET material_no = 'TZJS-250-0108-750K', deleted = 0, status = 'active' WHERE id = 394;

UPDATE `biz_inv_transaction` SET material_id = 394 WHERE material_id IN (723,492);
UPDATE `biz_inv_task_item` SET material_id = 394 WHERE material_id IN (723,492);
UPDATE `biz_inv_history_item` SET material_id = 394 WHERE material_id IN (723,492);
UPDATE `biz_inv_request_item` SET material_id = 394 WHERE material_id IN (723,492);
UPDATE `biz_inv_reservation` SET material_id = 394 WHERE material_id IN (723,492);
UPDATE `biz_inv_transfer_item` SET material_id = 394 WHERE material_id IN (723,492);
UPDATE `biz_inv_stocktaking_item` SET material_id = 394 WHERE material_id IN (723,492);
UPDATE `biz_purchase_request` SET material_id = 394 WHERE material_id IN (723,492);
UPDATE `biz_purchase_order_item` SET material_id = 394 WHERE material_id IN (723,492);
UPDATE `biz_purchase_receipt` SET material_id = 394 WHERE material_id IN (723,492);
UPDATE `biz_purchase_return_item` SET material_id = 394 WHERE material_id IN (723,492);
UPDATE `biz_wo_bom_snapshot` SET material_id = 394 WHERE material_id IN (723,492);
UPDATE `biz_plan_item_material` SET material_id = 394 WHERE material_id IN (723,492);
UPDATE `biz_outsource_delivery_item` SET material_id = 394 WHERE material_id IN (723,492);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 394 WHERE material_id IN (723,492);
UPDATE `biz_sales_order_item` SET product_id = 394 WHERE product_id IN (723,492);
UPDATE `biz_work_order` SET product_id = 394 WHERE product_id IN (723,492);
UPDATE `biz_return_order_item` SET product_id = 394 WHERE product_id IN (723,492);
UPDATE `biz_mrp_production_suggestion` SET product_id = 394 WHERE product_id IN (723,492);
UPDATE `biz_process_route` SET product_id = 394 WHERE product_id IN (723,492);
UPDATE `biz_bom_item` SET material_id = 394 WHERE material_id IN (723,492);
UPDATE `biz_bom_item` SET product_id = 394 WHERE product_id IN (723,492);
UPDATE `biz_qc_task` SET material_id = 394 WHERE material_id IN (723,492);
UPDATE `biz_qc_task` SET product_id = 394 WHERE product_id IN (723,492);
UPDATE `biz_defect_record` SET material_id = 394 WHERE material_id IN (723,492);
UPDATE `biz_defect_record` SET product_id = 394 WHERE product_id IN (723,492);
UPDATE `biz_mrp_demand` SET material_id = 394 WHERE material_id IN (723,492);
UPDATE `biz_mrp_demand` SET product_id = 394 WHERE product_id IN (723,492);
UPDATE `biz_outsource_request` SET material_id = 394 WHERE material_id IN (723,492);
UPDATE `biz_outsource_request` SET product_id = 394 WHERE product_id IN (723,492);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (723,492) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 394
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 394 WHERE material_id IN (723,492)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 394) tmp);
DELETE FROM biz_inventory WHERE material_id IN (723,492);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (723,492);

-- ============================================================
-- 联轴器罩: 主ID=396(TZJS2501201BK18136) -> 正确编码=TZJS-250-1201B-K18136
-- 合并: id=631(TZJS-250-1201B-K18136), id=516(ZG0035)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJS-250-1201B-K18136' AND id != 396;
UPDATE biz_material SET material_no = 'TZJS-250-1201B-K18136', deleted = 0, status = 'active' WHERE id = 396;

UPDATE `biz_inv_transaction` SET material_id = 396 WHERE material_id IN (631,516);
UPDATE `biz_inv_task_item` SET material_id = 396 WHERE material_id IN (631,516);
UPDATE `biz_inv_history_item` SET material_id = 396 WHERE material_id IN (631,516);
UPDATE `biz_inv_request_item` SET material_id = 396 WHERE material_id IN (631,516);
UPDATE `biz_inv_reservation` SET material_id = 396 WHERE material_id IN (631,516);
UPDATE `biz_inv_transfer_item` SET material_id = 396 WHERE material_id IN (631,516);
UPDATE `biz_inv_stocktaking_item` SET material_id = 396 WHERE material_id IN (631,516);
UPDATE `biz_purchase_request` SET material_id = 396 WHERE material_id IN (631,516);
UPDATE `biz_purchase_order_item` SET material_id = 396 WHERE material_id IN (631,516);
UPDATE `biz_purchase_receipt` SET material_id = 396 WHERE material_id IN (631,516);
UPDATE `biz_purchase_return_item` SET material_id = 396 WHERE material_id IN (631,516);
UPDATE `biz_wo_bom_snapshot` SET material_id = 396 WHERE material_id IN (631,516);
UPDATE `biz_plan_item_material` SET material_id = 396 WHERE material_id IN (631,516);
UPDATE `biz_outsource_delivery_item` SET material_id = 396 WHERE material_id IN (631,516);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 396 WHERE material_id IN (631,516);
UPDATE `biz_sales_order_item` SET product_id = 396 WHERE product_id IN (631,516);
UPDATE `biz_work_order` SET product_id = 396 WHERE product_id IN (631,516);
UPDATE `biz_return_order_item` SET product_id = 396 WHERE product_id IN (631,516);
UPDATE `biz_mrp_production_suggestion` SET product_id = 396 WHERE product_id IN (631,516);
UPDATE `biz_process_route` SET product_id = 396 WHERE product_id IN (631,516);
UPDATE `biz_bom_item` SET material_id = 396 WHERE material_id IN (631,516);
UPDATE `biz_bom_item` SET product_id = 396 WHERE product_id IN (631,516);
UPDATE `biz_qc_task` SET material_id = 396 WHERE material_id IN (631,516);
UPDATE `biz_qc_task` SET product_id = 396 WHERE product_id IN (631,516);
UPDATE `biz_defect_record` SET material_id = 396 WHERE material_id IN (631,516);
UPDATE `biz_defect_record` SET product_id = 396 WHERE product_id IN (631,516);
UPDATE `biz_mrp_demand` SET material_id = 396 WHERE material_id IN (631,516);
UPDATE `biz_mrp_demand` SET product_id = 396 WHERE product_id IN (631,516);
UPDATE `biz_outsource_request` SET material_id = 396 WHERE material_id IN (631,516);
UPDATE `biz_outsource_request` SET product_id = 396 WHERE product_id IN (631,516);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (631,516) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 396
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 396 WHERE material_id IN (631,516)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 396) tmp);
DELETE FROM biz_inventory WHERE material_id IN (631,516);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (631,516);

-- ============================================================
-- 泵支架: 主ID=403(TZJS3000108600K) -> 正确编码=TZJS-300-0108-600K
-- 合并: id=648(TZJS-300-0108-600K), id=551(ZG0070)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJS-300-0108-600K' AND id != 403;
UPDATE biz_material SET material_no = 'TZJS-300-0108-600K', deleted = 0, status = 'active' WHERE id = 403;

UPDATE `biz_inv_transaction` SET material_id = 403 WHERE material_id IN (648,551);
UPDATE `biz_inv_task_item` SET material_id = 403 WHERE material_id IN (648,551);
UPDATE `biz_inv_history_item` SET material_id = 403 WHERE material_id IN (648,551);
UPDATE `biz_inv_request_item` SET material_id = 403 WHERE material_id IN (648,551);
UPDATE `biz_inv_reservation` SET material_id = 403 WHERE material_id IN (648,551);
UPDATE `biz_inv_transfer_item` SET material_id = 403 WHERE material_id IN (648,551);
UPDATE `biz_inv_stocktaking_item` SET material_id = 403 WHERE material_id IN (648,551);
UPDATE `biz_purchase_request` SET material_id = 403 WHERE material_id IN (648,551);
UPDATE `biz_purchase_order_item` SET material_id = 403 WHERE material_id IN (648,551);
UPDATE `biz_purchase_receipt` SET material_id = 403 WHERE material_id IN (648,551);
UPDATE `biz_purchase_return_item` SET material_id = 403 WHERE material_id IN (648,551);
UPDATE `biz_wo_bom_snapshot` SET material_id = 403 WHERE material_id IN (648,551);
UPDATE `biz_plan_item_material` SET material_id = 403 WHERE material_id IN (648,551);
UPDATE `biz_outsource_delivery_item` SET material_id = 403 WHERE material_id IN (648,551);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 403 WHERE material_id IN (648,551);
UPDATE `biz_sales_order_item` SET product_id = 403 WHERE product_id IN (648,551);
UPDATE `biz_work_order` SET product_id = 403 WHERE product_id IN (648,551);
UPDATE `biz_return_order_item` SET product_id = 403 WHERE product_id IN (648,551);
UPDATE `biz_mrp_production_suggestion` SET product_id = 403 WHERE product_id IN (648,551);
UPDATE `biz_process_route` SET product_id = 403 WHERE product_id IN (648,551);
UPDATE `biz_bom_item` SET material_id = 403 WHERE material_id IN (648,551);
UPDATE `biz_bom_item` SET product_id = 403 WHERE product_id IN (648,551);
UPDATE `biz_qc_task` SET material_id = 403 WHERE material_id IN (648,551);
UPDATE `biz_qc_task` SET product_id = 403 WHERE product_id IN (648,551);
UPDATE `biz_defect_record` SET material_id = 403 WHERE material_id IN (648,551);
UPDATE `biz_defect_record` SET product_id = 403 WHERE product_id IN (648,551);
UPDATE `biz_mrp_demand` SET material_id = 403 WHERE material_id IN (648,551);
UPDATE `biz_mrp_demand` SET product_id = 403 WHERE product_id IN (648,551);
UPDATE `biz_outsource_request` SET material_id = 403 WHERE material_id IN (648,551);
UPDATE `biz_outsource_request` SET product_id = 403 WHERE product_id IN (648,551);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (648,551) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 403
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 403 WHERE material_id IN (648,551)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 403) tmp);
DELETE FROM biz_inventory WHERE material_id IN (648,551);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (648,551);

-- ============================================================
-- 泵支架: 主ID=404(TZJS3000108700K) -> 正确编码=TZJS-300-0108-700K
-- 合并: id=753(TZJS-300-0108-700K), id=583(ZG0102)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJS-300-0108-700K' AND id != 404;
UPDATE biz_material SET material_no = 'TZJS-300-0108-700K', deleted = 0, status = 'active' WHERE id = 404;

UPDATE `biz_inv_transaction` SET material_id = 404 WHERE material_id IN (753,583);
UPDATE `biz_inv_task_item` SET material_id = 404 WHERE material_id IN (753,583);
UPDATE `biz_inv_history_item` SET material_id = 404 WHERE material_id IN (753,583);
UPDATE `biz_inv_request_item` SET material_id = 404 WHERE material_id IN (753,583);
UPDATE `biz_inv_reservation` SET material_id = 404 WHERE material_id IN (753,583);
UPDATE `biz_inv_transfer_item` SET material_id = 404 WHERE material_id IN (753,583);
UPDATE `biz_inv_stocktaking_item` SET material_id = 404 WHERE material_id IN (753,583);
UPDATE `biz_purchase_request` SET material_id = 404 WHERE material_id IN (753,583);
UPDATE `biz_purchase_order_item` SET material_id = 404 WHERE material_id IN (753,583);
UPDATE `biz_purchase_receipt` SET material_id = 404 WHERE material_id IN (753,583);
UPDATE `biz_purchase_return_item` SET material_id = 404 WHERE material_id IN (753,583);
UPDATE `biz_wo_bom_snapshot` SET material_id = 404 WHERE material_id IN (753,583);
UPDATE `biz_plan_item_material` SET material_id = 404 WHERE material_id IN (753,583);
UPDATE `biz_outsource_delivery_item` SET material_id = 404 WHERE material_id IN (753,583);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 404 WHERE material_id IN (753,583);
UPDATE `biz_sales_order_item` SET product_id = 404 WHERE product_id IN (753,583);
UPDATE `biz_work_order` SET product_id = 404 WHERE product_id IN (753,583);
UPDATE `biz_return_order_item` SET product_id = 404 WHERE product_id IN (753,583);
UPDATE `biz_mrp_production_suggestion` SET product_id = 404 WHERE product_id IN (753,583);
UPDATE `biz_process_route` SET product_id = 404 WHERE product_id IN (753,583);
UPDATE `biz_bom_item` SET material_id = 404 WHERE material_id IN (753,583);
UPDATE `biz_bom_item` SET product_id = 404 WHERE product_id IN (753,583);
UPDATE `biz_qc_task` SET material_id = 404 WHERE material_id IN (753,583);
UPDATE `biz_qc_task` SET product_id = 404 WHERE product_id IN (753,583);
UPDATE `biz_defect_record` SET material_id = 404 WHERE material_id IN (753,583);
UPDATE `biz_defect_record` SET product_id = 404 WHERE product_id IN (753,583);
UPDATE `biz_mrp_demand` SET material_id = 404 WHERE material_id IN (753,583);
UPDATE `biz_mrp_demand` SET product_id = 404 WHERE product_id IN (753,583);
UPDATE `biz_outsource_request` SET material_id = 404 WHERE material_id IN (753,583);
UPDATE `biz_outsource_request` SET product_id = 404 WHERE product_id IN (753,583);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (753,583) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 404
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 404 WHERE material_id IN (753,583)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 404) tmp);
DELETE FROM biz_inventory WHERE material_id IN (753,583);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (753,583);

-- ============================================================
-- 水箱部: 主ID=405(TZJS3000604) -> 正确编码=TZJS-300-0604
-- 合并: id=724(TZJS-300-0604), id=493(ZG0012)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJS-300-0604' AND id != 405;
UPDATE biz_material SET material_no = 'TZJS-300-0604', deleted = 0, status = 'active' WHERE id = 405;

UPDATE `biz_inv_transaction` SET material_id = 405 WHERE material_id IN (724,493);
UPDATE `biz_inv_task_item` SET material_id = 405 WHERE material_id IN (724,493);
UPDATE `biz_inv_history_item` SET material_id = 405 WHERE material_id IN (724,493);
UPDATE `biz_inv_request_item` SET material_id = 405 WHERE material_id IN (724,493);
UPDATE `biz_inv_reservation` SET material_id = 405 WHERE material_id IN (724,493);
UPDATE `biz_inv_transfer_item` SET material_id = 405 WHERE material_id IN (724,493);
UPDATE `biz_inv_stocktaking_item` SET material_id = 405 WHERE material_id IN (724,493);
UPDATE `biz_purchase_request` SET material_id = 405 WHERE material_id IN (724,493);
UPDATE `biz_purchase_order_item` SET material_id = 405 WHERE material_id IN (724,493);
UPDATE `biz_purchase_receipt` SET material_id = 405 WHERE material_id IN (724,493);
UPDATE `biz_purchase_return_item` SET material_id = 405 WHERE material_id IN (724,493);
UPDATE `biz_wo_bom_snapshot` SET material_id = 405 WHERE material_id IN (724,493);
UPDATE `biz_plan_item_material` SET material_id = 405 WHERE material_id IN (724,493);
UPDATE `biz_outsource_delivery_item` SET material_id = 405 WHERE material_id IN (724,493);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 405 WHERE material_id IN (724,493);
UPDATE `biz_sales_order_item` SET product_id = 405 WHERE product_id IN (724,493);
UPDATE `biz_work_order` SET product_id = 405 WHERE product_id IN (724,493);
UPDATE `biz_return_order_item` SET product_id = 405 WHERE product_id IN (724,493);
UPDATE `biz_mrp_production_suggestion` SET product_id = 405 WHERE product_id IN (724,493);
UPDATE `biz_process_route` SET product_id = 405 WHERE product_id IN (724,493);
UPDATE `biz_bom_item` SET material_id = 405 WHERE material_id IN (724,493);
UPDATE `biz_bom_item` SET product_id = 405 WHERE product_id IN (724,493);
UPDATE `biz_qc_task` SET material_id = 405 WHERE material_id IN (724,493);
UPDATE `biz_qc_task` SET product_id = 405 WHERE product_id IN (724,493);
UPDATE `biz_defect_record` SET material_id = 405 WHERE material_id IN (724,493);
UPDATE `biz_defect_record` SET product_id = 405 WHERE product_id IN (724,493);
UPDATE `biz_mrp_demand` SET material_id = 405 WHERE material_id IN (724,493);
UPDATE `biz_mrp_demand` SET product_id = 405 WHERE product_id IN (724,493);
UPDATE `biz_outsource_request` SET material_id = 405 WHERE material_id IN (724,493);
UPDATE `biz_outsource_request` SET product_id = 405 WHERE product_id IN (724,493);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (724,493) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 405
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 405 WHERE material_id IN (724,493)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 405) tmp);
DELETE FROM biz_inventory WHERE material_id IN (724,493);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (724,493);

-- ============================================================
-- 底座: 主ID=406(TZJST090126002DC) -> 正确编码=TZJST-0901-260-02DC
-- 合并: id=664(TZJST-0901-260-02DC), id=484(ZG0003)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJST-0901-260-02DC' AND id != 406;
UPDATE biz_material SET material_no = 'TZJST-0901-260-02DC', deleted = 0, status = 'active' WHERE id = 406;

UPDATE `biz_inv_transaction` SET material_id = 406 WHERE material_id IN (664,484);
UPDATE `biz_inv_task_item` SET material_id = 406 WHERE material_id IN (664,484);
UPDATE `biz_inv_history_item` SET material_id = 406 WHERE material_id IN (664,484);
UPDATE `biz_inv_request_item` SET material_id = 406 WHERE material_id IN (664,484);
UPDATE `biz_inv_reservation` SET material_id = 406 WHERE material_id IN (664,484);
UPDATE `biz_inv_transfer_item` SET material_id = 406 WHERE material_id IN (664,484);
UPDATE `biz_inv_stocktaking_item` SET material_id = 406 WHERE material_id IN (664,484);
UPDATE `biz_purchase_request` SET material_id = 406 WHERE material_id IN (664,484);
UPDATE `biz_purchase_order_item` SET material_id = 406 WHERE material_id IN (664,484);
UPDATE `biz_purchase_receipt` SET material_id = 406 WHERE material_id IN (664,484);
UPDATE `biz_purchase_return_item` SET material_id = 406 WHERE material_id IN (664,484);
UPDATE `biz_wo_bom_snapshot` SET material_id = 406 WHERE material_id IN (664,484);
UPDATE `biz_plan_item_material` SET material_id = 406 WHERE material_id IN (664,484);
UPDATE `biz_outsource_delivery_item` SET material_id = 406 WHERE material_id IN (664,484);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 406 WHERE material_id IN (664,484);
UPDATE `biz_sales_order_item` SET product_id = 406 WHERE product_id IN (664,484);
UPDATE `biz_work_order` SET product_id = 406 WHERE product_id IN (664,484);
UPDATE `biz_return_order_item` SET product_id = 406 WHERE product_id IN (664,484);
UPDATE `biz_mrp_production_suggestion` SET product_id = 406 WHERE product_id IN (664,484);
UPDATE `biz_process_route` SET product_id = 406 WHERE product_id IN (664,484);
UPDATE `biz_bom_item` SET material_id = 406 WHERE material_id IN (664,484);
UPDATE `biz_bom_item` SET product_id = 406 WHERE product_id IN (664,484);
UPDATE `biz_qc_task` SET material_id = 406 WHERE material_id IN (664,484);
UPDATE `biz_qc_task` SET product_id = 406 WHERE product_id IN (664,484);
UPDATE `biz_defect_record` SET material_id = 406 WHERE material_id IN (664,484);
UPDATE `biz_defect_record` SET product_id = 406 WHERE product_id IN (664,484);
UPDATE `biz_mrp_demand` SET material_id = 406 WHERE material_id IN (664,484);
UPDATE `biz_mrp_demand` SET product_id = 406 WHERE product_id IN (664,484);
UPDATE `biz_outsource_request` SET material_id = 406 WHERE material_id IN (664,484);
UPDATE `biz_outsource_request` SET product_id = 406 WHERE product_id IN (664,484);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (664,484) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 406
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 406 WHERE material_id IN (664,484)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 406) tmp);
DELETE FROM biz_inventory WHERE material_id IN (664,484);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (664,484);

-- ============================================================
-- 泵支架: 主ID=411(TZJST300/35001081000HJ) -> 正确编码=TZJST-300/350-0108-1000HJ
-- 合并: id=634(TZJST-300/350-0108-1000HJ), id=486(ZG0005)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJST-300/350-0108-1000HJ' AND id != 411;
UPDATE biz_material SET material_no = 'TZJST-300/350-0108-1000HJ', deleted = 0, status = 'active' WHERE id = 411;

UPDATE `biz_inv_transaction` SET material_id = 411 WHERE material_id IN (634,486);
UPDATE `biz_inv_task_item` SET material_id = 411 WHERE material_id IN (634,486);
UPDATE `biz_inv_history_item` SET material_id = 411 WHERE material_id IN (634,486);
UPDATE `biz_inv_request_item` SET material_id = 411 WHERE material_id IN (634,486);
UPDATE `biz_inv_reservation` SET material_id = 411 WHERE material_id IN (634,486);
UPDATE `biz_inv_transfer_item` SET material_id = 411 WHERE material_id IN (634,486);
UPDATE `biz_inv_stocktaking_item` SET material_id = 411 WHERE material_id IN (634,486);
UPDATE `biz_purchase_request` SET material_id = 411 WHERE material_id IN (634,486);
UPDATE `biz_purchase_order_item` SET material_id = 411 WHERE material_id IN (634,486);
UPDATE `biz_purchase_receipt` SET material_id = 411 WHERE material_id IN (634,486);
UPDATE `biz_purchase_return_item` SET material_id = 411 WHERE material_id IN (634,486);
UPDATE `biz_wo_bom_snapshot` SET material_id = 411 WHERE material_id IN (634,486);
UPDATE `biz_plan_item_material` SET material_id = 411 WHERE material_id IN (634,486);
UPDATE `biz_outsource_delivery_item` SET material_id = 411 WHERE material_id IN (634,486);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 411 WHERE material_id IN (634,486);
UPDATE `biz_sales_order_item` SET product_id = 411 WHERE product_id IN (634,486);
UPDATE `biz_work_order` SET product_id = 411 WHERE product_id IN (634,486);
UPDATE `biz_return_order_item` SET product_id = 411 WHERE product_id IN (634,486);
UPDATE `biz_mrp_production_suggestion` SET product_id = 411 WHERE product_id IN (634,486);
UPDATE `biz_process_route` SET product_id = 411 WHERE product_id IN (634,486);
UPDATE `biz_bom_item` SET material_id = 411 WHERE material_id IN (634,486);
UPDATE `biz_bom_item` SET product_id = 411 WHERE product_id IN (634,486);
UPDATE `biz_qc_task` SET material_id = 411 WHERE material_id IN (634,486);
UPDATE `biz_qc_task` SET product_id = 411 WHERE product_id IN (634,486);
UPDATE `biz_defect_record` SET material_id = 411 WHERE material_id IN (634,486);
UPDATE `biz_defect_record` SET product_id = 411 WHERE product_id IN (634,486);
UPDATE `biz_mrp_demand` SET material_id = 411 WHERE material_id IN (634,486);
UPDATE `biz_mrp_demand` SET product_id = 411 WHERE product_id IN (634,486);
UPDATE `biz_outsource_request` SET material_id = 411 WHERE material_id IN (634,486);
UPDATE `biz_outsource_request` SET product_id = 411 WHERE product_id IN (634,486);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (634,486) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 411
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 411 WHERE material_id IN (634,486)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 411) tmp);
DELETE FROM biz_inventory WHERE material_id IN (634,486);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (634,486);

-- ============================================================
-- 联轴器罩: 主ID=416(TZJST3001201A) -> 正确编码=TZJST-300-1201A
-- 合并: id=632(TZJST-300-1201A), id=517(ZG0036)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJST-300-1201A' AND id != 416;
UPDATE biz_material SET material_no = 'TZJST-300-1201A', deleted = 0, status = 'active' WHERE id = 416;

UPDATE `biz_inv_transaction` SET material_id = 416 WHERE material_id IN (632,517);
UPDATE `biz_inv_task_item` SET material_id = 416 WHERE material_id IN (632,517);
UPDATE `biz_inv_history_item` SET material_id = 416 WHERE material_id IN (632,517);
UPDATE `biz_inv_request_item` SET material_id = 416 WHERE material_id IN (632,517);
UPDATE `biz_inv_reservation` SET material_id = 416 WHERE material_id IN (632,517);
UPDATE `biz_inv_transfer_item` SET material_id = 416 WHERE material_id IN (632,517);
UPDATE `biz_inv_stocktaking_item` SET material_id = 416 WHERE material_id IN (632,517);
UPDATE `biz_purchase_request` SET material_id = 416 WHERE material_id IN (632,517);
UPDATE `biz_purchase_order_item` SET material_id = 416 WHERE material_id IN (632,517);
UPDATE `biz_purchase_receipt` SET material_id = 416 WHERE material_id IN (632,517);
UPDATE `biz_purchase_return_item` SET material_id = 416 WHERE material_id IN (632,517);
UPDATE `biz_wo_bom_snapshot` SET material_id = 416 WHERE material_id IN (632,517);
UPDATE `biz_plan_item_material` SET material_id = 416 WHERE material_id IN (632,517);
UPDATE `biz_outsource_delivery_item` SET material_id = 416 WHERE material_id IN (632,517);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 416 WHERE material_id IN (632,517);
UPDATE `biz_sales_order_item` SET product_id = 416 WHERE product_id IN (632,517);
UPDATE `biz_work_order` SET product_id = 416 WHERE product_id IN (632,517);
UPDATE `biz_return_order_item` SET product_id = 416 WHERE product_id IN (632,517);
UPDATE `biz_mrp_production_suggestion` SET product_id = 416 WHERE product_id IN (632,517);
UPDATE `biz_process_route` SET product_id = 416 WHERE product_id IN (632,517);
UPDATE `biz_bom_item` SET material_id = 416 WHERE material_id IN (632,517);
UPDATE `biz_bom_item` SET product_id = 416 WHERE product_id IN (632,517);
UPDATE `biz_qc_task` SET material_id = 416 WHERE material_id IN (632,517);
UPDATE `biz_qc_task` SET product_id = 416 WHERE product_id IN (632,517);
UPDATE `biz_defect_record` SET material_id = 416 WHERE material_id IN (632,517);
UPDATE `biz_defect_record` SET product_id = 416 WHERE product_id IN (632,517);
UPDATE `biz_mrp_demand` SET material_id = 416 WHERE material_id IN (632,517);
UPDATE `biz_mrp_demand` SET product_id = 416 WHERE product_id IN (632,517);
UPDATE `biz_outsource_request` SET material_id = 416 WHERE material_id IN (632,517);
UPDATE `biz_outsource_request` SET product_id = 416 WHERE product_id IN (632,517);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (632,517) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 416
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 416 WHERE material_id IN (632,517)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 416) tmp);
DELETE FROM biz_inventory WHERE material_id IN (632,517);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (632,517);

-- ============================================================
-- 泵支架: 主ID=419(TZJST3500108800BHJ) -> 正确编码=TZJST-350-0108-800BHJ
-- 合并: id=665(TZJST-350-0108-800BHJ), id=565(ZG0084)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJST-350-0108-800BHJ' AND id != 419;
UPDATE biz_material SET material_no = 'TZJST-350-0108-800BHJ', deleted = 0, status = 'active' WHERE id = 419;

UPDATE `biz_inv_transaction` SET material_id = 419 WHERE material_id IN (665,565);
UPDATE `biz_inv_task_item` SET material_id = 419 WHERE material_id IN (665,565);
UPDATE `biz_inv_history_item` SET material_id = 419 WHERE material_id IN (665,565);
UPDATE `biz_inv_request_item` SET material_id = 419 WHERE material_id IN (665,565);
UPDATE `biz_inv_reservation` SET material_id = 419 WHERE material_id IN (665,565);
UPDATE `biz_inv_transfer_item` SET material_id = 419 WHERE material_id IN (665,565);
UPDATE `biz_inv_stocktaking_item` SET material_id = 419 WHERE material_id IN (665,565);
UPDATE `biz_purchase_request` SET material_id = 419 WHERE material_id IN (665,565);
UPDATE `biz_purchase_order_item` SET material_id = 419 WHERE material_id IN (665,565);
UPDATE `biz_purchase_receipt` SET material_id = 419 WHERE material_id IN (665,565);
UPDATE `biz_purchase_return_item` SET material_id = 419 WHERE material_id IN (665,565);
UPDATE `biz_wo_bom_snapshot` SET material_id = 419 WHERE material_id IN (665,565);
UPDATE `biz_plan_item_material` SET material_id = 419 WHERE material_id IN (665,565);
UPDATE `biz_outsource_delivery_item` SET material_id = 419 WHERE material_id IN (665,565);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 419 WHERE material_id IN (665,565);
UPDATE `biz_sales_order_item` SET product_id = 419 WHERE product_id IN (665,565);
UPDATE `biz_work_order` SET product_id = 419 WHERE product_id IN (665,565);
UPDATE `biz_return_order_item` SET product_id = 419 WHERE product_id IN (665,565);
UPDATE `biz_mrp_production_suggestion` SET product_id = 419 WHERE product_id IN (665,565);
UPDATE `biz_process_route` SET product_id = 419 WHERE product_id IN (665,565);
UPDATE `biz_bom_item` SET material_id = 419 WHERE material_id IN (665,565);
UPDATE `biz_bom_item` SET product_id = 419 WHERE product_id IN (665,565);
UPDATE `biz_qc_task` SET material_id = 419 WHERE material_id IN (665,565);
UPDATE `biz_qc_task` SET product_id = 419 WHERE product_id IN (665,565);
UPDATE `biz_defect_record` SET material_id = 419 WHERE material_id IN (665,565);
UPDATE `biz_defect_record` SET product_id = 419 WHERE product_id IN (665,565);
UPDATE `biz_mrp_demand` SET material_id = 419 WHERE material_id IN (665,565);
UPDATE `biz_mrp_demand` SET product_id = 419 WHERE product_id IN (665,565);
UPDATE `biz_outsource_request` SET material_id = 419 WHERE material_id IN (665,565);
UPDATE `biz_outsource_request` SET product_id = 419 WHERE product_id IN (665,565);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (665,565) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 419
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 419 WHERE material_id IN (665,565)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 419) tmp);
DELETE FROM biz_inventory WHERE material_id IN (665,565);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (665,565);

-- ============================================================
-- 泵支架: 主ID=420(TZJST3500108870GAHJ) -> 正确编码=TZJST-350-0108-870GAHJ
-- 合并: id=745(TZJST-350-0108-870GAHJ), id=575(ZG0094)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJST-350-0108-870GAHJ' AND id != 420;
UPDATE biz_material SET material_no = 'TZJST-350-0108-870GAHJ', deleted = 0, status = 'active' WHERE id = 420;

UPDATE `biz_inv_transaction` SET material_id = 420 WHERE material_id IN (745,575);
UPDATE `biz_inv_task_item` SET material_id = 420 WHERE material_id IN (745,575);
UPDATE `biz_inv_history_item` SET material_id = 420 WHERE material_id IN (745,575);
UPDATE `biz_inv_request_item` SET material_id = 420 WHERE material_id IN (745,575);
UPDATE `biz_inv_reservation` SET material_id = 420 WHERE material_id IN (745,575);
UPDATE `biz_inv_transfer_item` SET material_id = 420 WHERE material_id IN (745,575);
UPDATE `biz_inv_stocktaking_item` SET material_id = 420 WHERE material_id IN (745,575);
UPDATE `biz_purchase_request` SET material_id = 420 WHERE material_id IN (745,575);
UPDATE `biz_purchase_order_item` SET material_id = 420 WHERE material_id IN (745,575);
UPDATE `biz_purchase_receipt` SET material_id = 420 WHERE material_id IN (745,575);
UPDATE `biz_purchase_return_item` SET material_id = 420 WHERE material_id IN (745,575);
UPDATE `biz_wo_bom_snapshot` SET material_id = 420 WHERE material_id IN (745,575);
UPDATE `biz_plan_item_material` SET material_id = 420 WHERE material_id IN (745,575);
UPDATE `biz_outsource_delivery_item` SET material_id = 420 WHERE material_id IN (745,575);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 420 WHERE material_id IN (745,575);
UPDATE `biz_sales_order_item` SET product_id = 420 WHERE product_id IN (745,575);
UPDATE `biz_work_order` SET product_id = 420 WHERE product_id IN (745,575);
UPDATE `biz_return_order_item` SET product_id = 420 WHERE product_id IN (745,575);
UPDATE `biz_mrp_production_suggestion` SET product_id = 420 WHERE product_id IN (745,575);
UPDATE `biz_process_route` SET product_id = 420 WHERE product_id IN (745,575);
UPDATE `biz_bom_item` SET material_id = 420 WHERE material_id IN (745,575);
UPDATE `biz_bom_item` SET product_id = 420 WHERE product_id IN (745,575);
UPDATE `biz_qc_task` SET material_id = 420 WHERE material_id IN (745,575);
UPDATE `biz_qc_task` SET product_id = 420 WHERE product_id IN (745,575);
UPDATE `biz_defect_record` SET material_id = 420 WHERE material_id IN (745,575);
UPDATE `biz_defect_record` SET product_id = 420 WHERE product_id IN (745,575);
UPDATE `biz_mrp_demand` SET material_id = 420 WHERE material_id IN (745,575);
UPDATE `biz_mrp_demand` SET product_id = 420 WHERE product_id IN (745,575);
UPDATE `biz_outsource_request` SET material_id = 420 WHERE material_id IN (745,575);
UPDATE `biz_outsource_request` SET product_id = 420 WHERE product_id IN (745,575);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (745,575) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 420
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 420 WHERE material_id IN (745,575)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 420) tmp);
DELETE FROM biz_inventory WHERE material_id IN (745,575);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (745,575);

-- ============================================================
-- 皮带罩: 主ID=485(ZG0004) -> 正确编码=TZJST-1301-3000-01CL/CR
-- 合并: id=718(TZJST-1301-3000-01CL/CR)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJST-1301-3000-01CL/CR' AND id != 485;
UPDATE biz_material SET material_no = 'TZJST-1301-3000-01CL/CR', deleted = 0, status = 'active' WHERE id = 485;

UPDATE `biz_inv_transaction` SET material_id = 485 WHERE material_id IN (718);
UPDATE `biz_inv_task_item` SET material_id = 485 WHERE material_id IN (718);
UPDATE `biz_inv_history_item` SET material_id = 485 WHERE material_id IN (718);
UPDATE `biz_inv_request_item` SET material_id = 485 WHERE material_id IN (718);
UPDATE `biz_inv_reservation` SET material_id = 485 WHERE material_id IN (718);
UPDATE `biz_inv_transfer_item` SET material_id = 485 WHERE material_id IN (718);
UPDATE `biz_inv_stocktaking_item` SET material_id = 485 WHERE material_id IN (718);
UPDATE `biz_purchase_request` SET material_id = 485 WHERE material_id IN (718);
UPDATE `biz_purchase_order_item` SET material_id = 485 WHERE material_id IN (718);
UPDATE `biz_purchase_receipt` SET material_id = 485 WHERE material_id IN (718);
UPDATE `biz_purchase_return_item` SET material_id = 485 WHERE material_id IN (718);
UPDATE `biz_wo_bom_snapshot` SET material_id = 485 WHERE material_id IN (718);
UPDATE `biz_plan_item_material` SET material_id = 485 WHERE material_id IN (718);
UPDATE `biz_outsource_delivery_item` SET material_id = 485 WHERE material_id IN (718);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 485 WHERE material_id IN (718);
UPDATE `biz_sales_order_item` SET product_id = 485 WHERE product_id IN (718);
UPDATE `biz_work_order` SET product_id = 485 WHERE product_id IN (718);
UPDATE `biz_return_order_item` SET product_id = 485 WHERE product_id IN (718);
UPDATE `biz_mrp_production_suggestion` SET product_id = 485 WHERE product_id IN (718);
UPDATE `biz_process_route` SET product_id = 485 WHERE product_id IN (718);
UPDATE `biz_bom_item` SET material_id = 485 WHERE material_id IN (718);
UPDATE `biz_bom_item` SET product_id = 485 WHERE product_id IN (718);
UPDATE `biz_qc_task` SET material_id = 485 WHERE material_id IN (718);
UPDATE `biz_qc_task` SET product_id = 485 WHERE product_id IN (718);
UPDATE `biz_defect_record` SET material_id = 485 WHERE material_id IN (718);
UPDATE `biz_defect_record` SET product_id = 485 WHERE product_id IN (718);
UPDATE `biz_mrp_demand` SET material_id = 485 WHERE material_id IN (718);
UPDATE `biz_mrp_demand` SET product_id = 485 WHERE product_id IN (718);
UPDATE `biz_outsource_request` SET material_id = 485 WHERE material_id IN (718);
UPDATE `biz_outsource_request` SET product_id = 485 WHERE product_id IN (718);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (718) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 485
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 485 WHERE material_id IN (718)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 485) tmp);
DELETE FROM biz_inventory WHERE material_id IN (718);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (718);

-- ============================================================
-- 发货支架: 主ID=489(ZG0008) -> 正确编码=TZJD-80-430-FHZJ-150
-- 合并: id=662(TZJD-80-430-FHZJ-150)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJD-80-430-FHZJ-150' AND id != 489;
UPDATE biz_material SET material_no = 'TZJD-80-430-FHZJ-150', deleted = 0, status = 'active' WHERE id = 489;

UPDATE `biz_inv_transaction` SET material_id = 489 WHERE material_id IN (662);
UPDATE `biz_inv_task_item` SET material_id = 489 WHERE material_id IN (662);
UPDATE `biz_inv_history_item` SET material_id = 489 WHERE material_id IN (662);
UPDATE `biz_inv_request_item` SET material_id = 489 WHERE material_id IN (662);
UPDATE `biz_inv_reservation` SET material_id = 489 WHERE material_id IN (662);
UPDATE `biz_inv_transfer_item` SET material_id = 489 WHERE material_id IN (662);
UPDATE `biz_inv_stocktaking_item` SET material_id = 489 WHERE material_id IN (662);
UPDATE `biz_purchase_request` SET material_id = 489 WHERE material_id IN (662);
UPDATE `biz_purchase_order_item` SET material_id = 489 WHERE material_id IN (662);
UPDATE `biz_purchase_receipt` SET material_id = 489 WHERE material_id IN (662);
UPDATE `biz_purchase_return_item` SET material_id = 489 WHERE material_id IN (662);
UPDATE `biz_wo_bom_snapshot` SET material_id = 489 WHERE material_id IN (662);
UPDATE `biz_plan_item_material` SET material_id = 489 WHERE material_id IN (662);
UPDATE `biz_outsource_delivery_item` SET material_id = 489 WHERE material_id IN (662);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 489 WHERE material_id IN (662);
UPDATE `biz_sales_order_item` SET product_id = 489 WHERE product_id IN (662);
UPDATE `biz_work_order` SET product_id = 489 WHERE product_id IN (662);
UPDATE `biz_return_order_item` SET product_id = 489 WHERE product_id IN (662);
UPDATE `biz_mrp_production_suggestion` SET product_id = 489 WHERE product_id IN (662);
UPDATE `biz_process_route` SET product_id = 489 WHERE product_id IN (662);
UPDATE `biz_bom_item` SET material_id = 489 WHERE material_id IN (662);
UPDATE `biz_bom_item` SET product_id = 489 WHERE product_id IN (662);
UPDATE `biz_qc_task` SET material_id = 489 WHERE material_id IN (662);
UPDATE `biz_qc_task` SET product_id = 489 WHERE product_id IN (662);
UPDATE `biz_defect_record` SET material_id = 489 WHERE material_id IN (662);
UPDATE `biz_defect_record` SET product_id = 489 WHERE product_id IN (662);
UPDATE `biz_mrp_demand` SET material_id = 489 WHERE material_id IN (662);
UPDATE `biz_mrp_demand` SET product_id = 489 WHERE product_id IN (662);
UPDATE `biz_outsource_request` SET material_id = 489 WHERE material_id IN (662);
UPDATE `biz_outsource_request` SET product_id = 489 WHERE product_id IN (662);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (662) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 489
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 489 WHERE material_id IN (662)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 489) tmp);
DELETE FROM biz_inventory WHERE material_id IN (662);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (662);

-- ============================================================
-- 电机座: 主ID=497(ZG0016) -> 正确编码=TZJD-1701-04CV
-- 合并: id=728(TZJD-1701-04CV)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJD-1701-04CV' AND id != 497;
UPDATE biz_material SET material_no = 'TZJD-1701-04CV', deleted = 0, status = 'active' WHERE id = 497;

UPDATE `biz_inv_transaction` SET material_id = 497 WHERE material_id IN (728);
UPDATE `biz_inv_task_item` SET material_id = 497 WHERE material_id IN (728);
UPDATE `biz_inv_history_item` SET material_id = 497 WHERE material_id IN (728);
UPDATE `biz_inv_request_item` SET material_id = 497 WHERE material_id IN (728);
UPDATE `biz_inv_reservation` SET material_id = 497 WHERE material_id IN (728);
UPDATE `biz_inv_transfer_item` SET material_id = 497 WHERE material_id IN (728);
UPDATE `biz_inv_stocktaking_item` SET material_id = 497 WHERE material_id IN (728);
UPDATE `biz_purchase_request` SET material_id = 497 WHERE material_id IN (728);
UPDATE `biz_purchase_order_item` SET material_id = 497 WHERE material_id IN (728);
UPDATE `biz_purchase_receipt` SET material_id = 497 WHERE material_id IN (728);
UPDATE `biz_purchase_return_item` SET material_id = 497 WHERE material_id IN (728);
UPDATE `biz_wo_bom_snapshot` SET material_id = 497 WHERE material_id IN (728);
UPDATE `biz_plan_item_material` SET material_id = 497 WHERE material_id IN (728);
UPDATE `biz_outsource_delivery_item` SET material_id = 497 WHERE material_id IN (728);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 497 WHERE material_id IN (728);
UPDATE `biz_sales_order_item` SET product_id = 497 WHERE product_id IN (728);
UPDATE `biz_work_order` SET product_id = 497 WHERE product_id IN (728);
UPDATE `biz_return_order_item` SET product_id = 497 WHERE product_id IN (728);
UPDATE `biz_mrp_production_suggestion` SET product_id = 497 WHERE product_id IN (728);
UPDATE `biz_process_route` SET product_id = 497 WHERE product_id IN (728);
UPDATE `biz_bom_item` SET material_id = 497 WHERE material_id IN (728);
UPDATE `biz_bom_item` SET product_id = 497 WHERE product_id IN (728);
UPDATE `biz_qc_task` SET material_id = 497 WHERE material_id IN (728);
UPDATE `biz_qc_task` SET product_id = 497 WHERE product_id IN (728);
UPDATE `biz_defect_record` SET material_id = 497 WHERE material_id IN (728);
UPDATE `biz_defect_record` SET product_id = 497 WHERE product_id IN (728);
UPDATE `biz_mrp_demand` SET material_id = 497 WHERE material_id IN (728);
UPDATE `biz_mrp_demand` SET product_id = 497 WHERE product_id IN (728);
UPDATE `biz_outsource_request` SET material_id = 497 WHERE material_id IN (728);
UPDATE `biz_outsource_request` SET product_id = 497 WHERE product_id IN (728);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (728) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 497
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 497 WHERE material_id IN (728)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 497) tmp);
DELETE FROM biz_inventory WHERE material_id IN (728);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (728);

-- ============================================================
-- 皮带罩: 主ID=500(ZG0019) -> 正确编码=TZJS-1301-2000-02CL/CR
-- 合并: id=730(TZJS-1301-2000-02CL/CR)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJS-1301-2000-02CL/CR' AND id != 500;
UPDATE biz_material SET material_no = 'TZJS-1301-2000-02CL/CR', deleted = 0, status = 'active' WHERE id = 500;

UPDATE `biz_inv_transaction` SET material_id = 500 WHERE material_id IN (730);
UPDATE `biz_inv_task_item` SET material_id = 500 WHERE material_id IN (730);
UPDATE `biz_inv_history_item` SET material_id = 500 WHERE material_id IN (730);
UPDATE `biz_inv_request_item` SET material_id = 500 WHERE material_id IN (730);
UPDATE `biz_inv_reservation` SET material_id = 500 WHERE material_id IN (730);
UPDATE `biz_inv_transfer_item` SET material_id = 500 WHERE material_id IN (730);
UPDATE `biz_inv_stocktaking_item` SET material_id = 500 WHERE material_id IN (730);
UPDATE `biz_purchase_request` SET material_id = 500 WHERE material_id IN (730);
UPDATE `biz_purchase_order_item` SET material_id = 500 WHERE material_id IN (730);
UPDATE `biz_purchase_receipt` SET material_id = 500 WHERE material_id IN (730);
UPDATE `biz_purchase_return_item` SET material_id = 500 WHERE material_id IN (730);
UPDATE `biz_wo_bom_snapshot` SET material_id = 500 WHERE material_id IN (730);
UPDATE `biz_plan_item_material` SET material_id = 500 WHERE material_id IN (730);
UPDATE `biz_outsource_delivery_item` SET material_id = 500 WHERE material_id IN (730);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 500 WHERE material_id IN (730);
UPDATE `biz_sales_order_item` SET product_id = 500 WHERE product_id IN (730);
UPDATE `biz_work_order` SET product_id = 500 WHERE product_id IN (730);
UPDATE `biz_return_order_item` SET product_id = 500 WHERE product_id IN (730);
UPDATE `biz_mrp_production_suggestion` SET product_id = 500 WHERE product_id IN (730);
UPDATE `biz_process_route` SET product_id = 500 WHERE product_id IN (730);
UPDATE `biz_bom_item` SET material_id = 500 WHERE material_id IN (730);
UPDATE `biz_bom_item` SET product_id = 500 WHERE product_id IN (730);
UPDATE `biz_qc_task` SET material_id = 500 WHERE material_id IN (730);
UPDATE `biz_qc_task` SET product_id = 500 WHERE product_id IN (730);
UPDATE `biz_defect_record` SET material_id = 500 WHERE material_id IN (730);
UPDATE `biz_defect_record` SET product_id = 500 WHERE product_id IN (730);
UPDATE `biz_mrp_demand` SET material_id = 500 WHERE material_id IN (730);
UPDATE `biz_mrp_demand` SET product_id = 500 WHERE product_id IN (730);
UPDATE `biz_outsource_request` SET material_id = 500 WHERE material_id IN (730);
UPDATE `biz_outsource_request` SET product_id = 500 WHERE product_id IN (730);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (730) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 500
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 500 WHERE material_id IN (730)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 500) tmp);
DELETE FROM biz_inventory WHERE material_id IN (730);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (730);

-- ============================================================
-- 发货支架: 主ID=506(ZG0025) -> 正确编码=TZJE-150-500-FHZJ-155
-- 合并: id=659(TZJE-150-500-FHZJ-155)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJE-150-500-FHZJ-155' AND id != 506;
UPDATE biz_material SET material_no = 'TZJE-150-500-FHZJ-155', deleted = 0, status = 'active' WHERE id = 506;

UPDATE `biz_inv_transaction` SET material_id = 506 WHERE material_id IN (659);
UPDATE `biz_inv_task_item` SET material_id = 506 WHERE material_id IN (659);
UPDATE `biz_inv_history_item` SET material_id = 506 WHERE material_id IN (659);
UPDATE `biz_inv_request_item` SET material_id = 506 WHERE material_id IN (659);
UPDATE `biz_inv_reservation` SET material_id = 506 WHERE material_id IN (659);
UPDATE `biz_inv_transfer_item` SET material_id = 506 WHERE material_id IN (659);
UPDATE `biz_inv_stocktaking_item` SET material_id = 506 WHERE material_id IN (659);
UPDATE `biz_purchase_request` SET material_id = 506 WHERE material_id IN (659);
UPDATE `biz_purchase_order_item` SET material_id = 506 WHERE material_id IN (659);
UPDATE `biz_purchase_receipt` SET material_id = 506 WHERE material_id IN (659);
UPDATE `biz_purchase_return_item` SET material_id = 506 WHERE material_id IN (659);
UPDATE `biz_wo_bom_snapshot` SET material_id = 506 WHERE material_id IN (659);
UPDATE `biz_plan_item_material` SET material_id = 506 WHERE material_id IN (659);
UPDATE `biz_outsource_delivery_item` SET material_id = 506 WHERE material_id IN (659);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 506 WHERE material_id IN (659);
UPDATE `biz_sales_order_item` SET product_id = 506 WHERE product_id IN (659);
UPDATE `biz_work_order` SET product_id = 506 WHERE product_id IN (659);
UPDATE `biz_return_order_item` SET product_id = 506 WHERE product_id IN (659);
UPDATE `biz_mrp_production_suggestion` SET product_id = 506 WHERE product_id IN (659);
UPDATE `biz_process_route` SET product_id = 506 WHERE product_id IN (659);
UPDATE `biz_bom_item` SET material_id = 506 WHERE material_id IN (659);
UPDATE `biz_bom_item` SET product_id = 506 WHERE product_id IN (659);
UPDATE `biz_qc_task` SET material_id = 506 WHERE material_id IN (659);
UPDATE `biz_qc_task` SET product_id = 506 WHERE product_id IN (659);
UPDATE `biz_defect_record` SET material_id = 506 WHERE material_id IN (659);
UPDATE `biz_defect_record` SET product_id = 506 WHERE product_id IN (659);
UPDATE `biz_mrp_demand` SET material_id = 506 WHERE material_id IN (659);
UPDATE `biz_mrp_demand` SET product_id = 506 WHERE product_id IN (659);
UPDATE `biz_outsource_request` SET material_id = 506 WHERE material_id IN (659);
UPDATE `biz_outsource_request` SET product_id = 506 WHERE product_id IN (659);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (659) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 506
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 506 WHERE material_id IN (659)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 506) tmp);
DELETE FROM biz_inventory WHERE material_id IN (659);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (659);

-- ============================================================
-- 发货支架: 主ID=508(ZG0027) -> 正确编码=TZJF/K-250-550-FHZJ-160
-- 合并: id=736(TZJF/K-250-550-FHZJ-160)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJF/K-250-550-FHZJ-160' AND id != 508;
UPDATE biz_material SET material_no = 'TZJF/K-250-550-FHZJ-160', deleted = 0, status = 'active' WHERE id = 508;

UPDATE `biz_inv_transaction` SET material_id = 508 WHERE material_id IN (736);
UPDATE `biz_inv_task_item` SET material_id = 508 WHERE material_id IN (736);
UPDATE `biz_inv_history_item` SET material_id = 508 WHERE material_id IN (736);
UPDATE `biz_inv_request_item` SET material_id = 508 WHERE material_id IN (736);
UPDATE `biz_inv_reservation` SET material_id = 508 WHERE material_id IN (736);
UPDATE `biz_inv_transfer_item` SET material_id = 508 WHERE material_id IN (736);
UPDATE `biz_inv_stocktaking_item` SET material_id = 508 WHERE material_id IN (736);
UPDATE `biz_purchase_request` SET material_id = 508 WHERE material_id IN (736);
UPDATE `biz_purchase_order_item` SET material_id = 508 WHERE material_id IN (736);
UPDATE `biz_purchase_receipt` SET material_id = 508 WHERE material_id IN (736);
UPDATE `biz_purchase_return_item` SET material_id = 508 WHERE material_id IN (736);
UPDATE `biz_wo_bom_snapshot` SET material_id = 508 WHERE material_id IN (736);
UPDATE `biz_plan_item_material` SET material_id = 508 WHERE material_id IN (736);
UPDATE `biz_outsource_delivery_item` SET material_id = 508 WHERE material_id IN (736);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 508 WHERE material_id IN (736);
UPDATE `biz_sales_order_item` SET product_id = 508 WHERE product_id IN (736);
UPDATE `biz_work_order` SET product_id = 508 WHERE product_id IN (736);
UPDATE `biz_return_order_item` SET product_id = 508 WHERE product_id IN (736);
UPDATE `biz_mrp_production_suggestion` SET product_id = 508 WHERE product_id IN (736);
UPDATE `biz_process_route` SET product_id = 508 WHERE product_id IN (736);
UPDATE `biz_bom_item` SET material_id = 508 WHERE material_id IN (736);
UPDATE `biz_bom_item` SET product_id = 508 WHERE product_id IN (736);
UPDATE `biz_qc_task` SET material_id = 508 WHERE material_id IN (736);
UPDATE `biz_qc_task` SET product_id = 508 WHERE product_id IN (736);
UPDATE `biz_defect_record` SET material_id = 508 WHERE material_id IN (736);
UPDATE `biz_defect_record` SET product_id = 508 WHERE product_id IN (736);
UPDATE `biz_mrp_demand` SET material_id = 508 WHERE material_id IN (736);
UPDATE `biz_mrp_demand` SET product_id = 508 WHERE product_id IN (736);
UPDATE `biz_outsource_request` SET material_id = 508 WHERE material_id IN (736);
UPDATE `biz_outsource_request` SET product_id = 508 WHERE product_id IN (736);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (736) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 508
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 508 WHERE material_id IN (736)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 508) tmp);
DELETE FROM biz_inventory WHERE material_id IN (736);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (736);

-- ============================================================
-- 发货支架: 主ID=514(ZG0033) -> 正确编码=TZJF-150-700-FHZJ-160
-- 合并: id=742(TZJF-150-700-FHZJ-160)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJF-150-700-FHZJ-160' AND id != 514;
UPDATE biz_material SET material_no = 'TZJF-150-700-FHZJ-160', deleted = 0, status = 'active' WHERE id = 514;

UPDATE `biz_inv_transaction` SET material_id = 514 WHERE material_id IN (742);
UPDATE `biz_inv_task_item` SET material_id = 514 WHERE material_id IN (742);
UPDATE `biz_inv_history_item` SET material_id = 514 WHERE material_id IN (742);
UPDATE `biz_inv_request_item` SET material_id = 514 WHERE material_id IN (742);
UPDATE `biz_inv_reservation` SET material_id = 514 WHERE material_id IN (742);
UPDATE `biz_inv_transfer_item` SET material_id = 514 WHERE material_id IN (742);
UPDATE `biz_inv_stocktaking_item` SET material_id = 514 WHERE material_id IN (742);
UPDATE `biz_purchase_request` SET material_id = 514 WHERE material_id IN (742);
UPDATE `biz_purchase_order_item` SET material_id = 514 WHERE material_id IN (742);
UPDATE `biz_purchase_receipt` SET material_id = 514 WHERE material_id IN (742);
UPDATE `biz_purchase_return_item` SET material_id = 514 WHERE material_id IN (742);
UPDATE `biz_wo_bom_snapshot` SET material_id = 514 WHERE material_id IN (742);
UPDATE `biz_plan_item_material` SET material_id = 514 WHERE material_id IN (742);
UPDATE `biz_outsource_delivery_item` SET material_id = 514 WHERE material_id IN (742);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 514 WHERE material_id IN (742);
UPDATE `biz_sales_order_item` SET product_id = 514 WHERE product_id IN (742);
UPDATE `biz_work_order` SET product_id = 514 WHERE product_id IN (742);
UPDATE `biz_return_order_item` SET product_id = 514 WHERE product_id IN (742);
UPDATE `biz_mrp_production_suggestion` SET product_id = 514 WHERE product_id IN (742);
UPDATE `biz_process_route` SET product_id = 514 WHERE product_id IN (742);
UPDATE `biz_bom_item` SET material_id = 514 WHERE material_id IN (742);
UPDATE `biz_bom_item` SET product_id = 514 WHERE product_id IN (742);
UPDATE `biz_qc_task` SET material_id = 514 WHERE material_id IN (742);
UPDATE `biz_qc_task` SET product_id = 514 WHERE product_id IN (742);
UPDATE `biz_defect_record` SET material_id = 514 WHERE material_id IN (742);
UPDATE `biz_defect_record` SET product_id = 514 WHERE product_id IN (742);
UPDATE `biz_mrp_demand` SET material_id = 514 WHERE material_id IN (742);
UPDATE `biz_mrp_demand` SET product_id = 514 WHERE product_id IN (742);
UPDATE `biz_outsource_request` SET material_id = 514 WHERE material_id IN (742);
UPDATE `biz_outsource_request` SET product_id = 514 WHERE product_id IN (742);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (742) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 514
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 514 WHERE material_id IN (742)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 514) tmp);
DELETE FROM biz_inventory WHERE material_id IN (742);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (742);

-- ============================================================
-- 皮带罩: 主ID=515(ZG0034) -> 正确编码=TZJS-1301-2300-06CR
-- 合并: id=743(TZJS-1301-2300-06CR)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJS-1301-2300-06CR' AND id != 515;
UPDATE biz_material SET material_no = 'TZJS-1301-2300-06CR', deleted = 0, status = 'active' WHERE id = 515;

UPDATE `biz_inv_transaction` SET material_id = 515 WHERE material_id IN (743);
UPDATE `biz_inv_task_item` SET material_id = 515 WHERE material_id IN (743);
UPDATE `biz_inv_history_item` SET material_id = 515 WHERE material_id IN (743);
UPDATE `biz_inv_request_item` SET material_id = 515 WHERE material_id IN (743);
UPDATE `biz_inv_reservation` SET material_id = 515 WHERE material_id IN (743);
UPDATE `biz_inv_transfer_item` SET material_id = 515 WHERE material_id IN (743);
UPDATE `biz_inv_stocktaking_item` SET material_id = 515 WHERE material_id IN (743);
UPDATE `biz_purchase_request` SET material_id = 515 WHERE material_id IN (743);
UPDATE `biz_purchase_order_item` SET material_id = 515 WHERE material_id IN (743);
UPDATE `biz_purchase_receipt` SET material_id = 515 WHERE material_id IN (743);
UPDATE `biz_purchase_return_item` SET material_id = 515 WHERE material_id IN (743);
UPDATE `biz_wo_bom_snapshot` SET material_id = 515 WHERE material_id IN (743);
UPDATE `biz_plan_item_material` SET material_id = 515 WHERE material_id IN (743);
UPDATE `biz_outsource_delivery_item` SET material_id = 515 WHERE material_id IN (743);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 515 WHERE material_id IN (743);
UPDATE `biz_sales_order_item` SET product_id = 515 WHERE product_id IN (743);
UPDATE `biz_work_order` SET product_id = 515 WHERE product_id IN (743);
UPDATE `biz_return_order_item` SET product_id = 515 WHERE product_id IN (743);
UPDATE `biz_mrp_production_suggestion` SET product_id = 515 WHERE product_id IN (743);
UPDATE `biz_process_route` SET product_id = 515 WHERE product_id IN (743);
UPDATE `biz_bom_item` SET material_id = 515 WHERE material_id IN (743);
UPDATE `biz_bom_item` SET product_id = 515 WHERE product_id IN (743);
UPDATE `biz_qc_task` SET material_id = 515 WHERE material_id IN (743);
UPDATE `biz_qc_task` SET product_id = 515 WHERE product_id IN (743);
UPDATE `biz_defect_record` SET material_id = 515 WHERE material_id IN (743);
UPDATE `biz_defect_record` SET product_id = 515 WHERE product_id IN (743);
UPDATE `biz_mrp_demand` SET material_id = 515 WHERE material_id IN (743);
UPDATE `biz_mrp_demand` SET product_id = 515 WHERE product_id IN (743);
UPDATE `biz_outsource_request` SET material_id = 515 WHERE material_id IN (743);
UPDATE `biz_outsource_request` SET product_id = 515 WHERE product_id IN (743);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (743) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 515
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 515 WHERE material_id IN (743)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 515) tmp);
DELETE FROM biz_inventory WHERE material_id IN (743);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (743);

-- ============================================================
-- 底座: 主ID=521(ZG0040) -> 正确编码=ZGB-100-Y315-01DC
-- 合并: id=795(ZGB-100-Y315-01DC)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'ZGB-100-Y315-01DC' AND id != 521;
UPDATE biz_material SET material_no = 'ZGB-100-Y315-01DC', deleted = 0, status = 'active' WHERE id = 521;

UPDATE `biz_inv_transaction` SET material_id = 521 WHERE material_id IN (795);
UPDATE `biz_inv_task_item` SET material_id = 521 WHERE material_id IN (795);
UPDATE `biz_inv_history_item` SET material_id = 521 WHERE material_id IN (795);
UPDATE `biz_inv_request_item` SET material_id = 521 WHERE material_id IN (795);
UPDATE `biz_inv_reservation` SET material_id = 521 WHERE material_id IN (795);
UPDATE `biz_inv_transfer_item` SET material_id = 521 WHERE material_id IN (795);
UPDATE `biz_inv_stocktaking_item` SET material_id = 521 WHERE material_id IN (795);
UPDATE `biz_purchase_request` SET material_id = 521 WHERE material_id IN (795);
UPDATE `biz_purchase_order_item` SET material_id = 521 WHERE material_id IN (795);
UPDATE `biz_purchase_receipt` SET material_id = 521 WHERE material_id IN (795);
UPDATE `biz_purchase_return_item` SET material_id = 521 WHERE material_id IN (795);
UPDATE `biz_wo_bom_snapshot` SET material_id = 521 WHERE material_id IN (795);
UPDATE `biz_plan_item_material` SET material_id = 521 WHERE material_id IN (795);
UPDATE `biz_outsource_delivery_item` SET material_id = 521 WHERE material_id IN (795);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 521 WHERE material_id IN (795);
UPDATE `biz_sales_order_item` SET product_id = 521 WHERE product_id IN (795);
UPDATE `biz_work_order` SET product_id = 521 WHERE product_id IN (795);
UPDATE `biz_return_order_item` SET product_id = 521 WHERE product_id IN (795);
UPDATE `biz_mrp_production_suggestion` SET product_id = 521 WHERE product_id IN (795);
UPDATE `biz_process_route` SET product_id = 521 WHERE product_id IN (795);
UPDATE `biz_bom_item` SET material_id = 521 WHERE material_id IN (795);
UPDATE `biz_bom_item` SET product_id = 521 WHERE product_id IN (795);
UPDATE `biz_qc_task` SET material_id = 521 WHERE material_id IN (795);
UPDATE `biz_qc_task` SET product_id = 521 WHERE product_id IN (795);
UPDATE `biz_defect_record` SET material_id = 521 WHERE material_id IN (795);
UPDATE `biz_defect_record` SET product_id = 521 WHERE product_id IN (795);
UPDATE `biz_mrp_demand` SET material_id = 521 WHERE material_id IN (795);
UPDATE `biz_mrp_demand` SET product_id = 521 WHERE product_id IN (795);
UPDATE `biz_outsource_request` SET material_id = 521 WHERE material_id IN (795);
UPDATE `biz_outsource_request` SET product_id = 521 WHERE product_id IN (795);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (795) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 521
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 521 WHERE material_id IN (795)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 521) tmp);
DELETE FROM biz_inventory WHERE material_id IN (795);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (795);

-- ============================================================
-- 底座: 主ID=524(ZG0043) -> 正确编码=ZGB-350-0901HJ
-- 合并: id=797(ZGB-350-0901HJ)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'ZGB-350-0901HJ' AND id != 524;
UPDATE biz_material SET material_no = 'ZGB-350-0901HJ', deleted = 0, status = 'active' WHERE id = 524;

UPDATE `biz_inv_transaction` SET material_id = 524 WHERE material_id IN (797);
UPDATE `biz_inv_task_item` SET material_id = 524 WHERE material_id IN (797);
UPDATE `biz_inv_history_item` SET material_id = 524 WHERE material_id IN (797);
UPDATE `biz_inv_request_item` SET material_id = 524 WHERE material_id IN (797);
UPDATE `biz_inv_reservation` SET material_id = 524 WHERE material_id IN (797);
UPDATE `biz_inv_transfer_item` SET material_id = 524 WHERE material_id IN (797);
UPDATE `biz_inv_stocktaking_item` SET material_id = 524 WHERE material_id IN (797);
UPDATE `biz_purchase_request` SET material_id = 524 WHERE material_id IN (797);
UPDATE `biz_purchase_order_item` SET material_id = 524 WHERE material_id IN (797);
UPDATE `biz_purchase_receipt` SET material_id = 524 WHERE material_id IN (797);
UPDATE `biz_purchase_return_item` SET material_id = 524 WHERE material_id IN (797);
UPDATE `biz_wo_bom_snapshot` SET material_id = 524 WHERE material_id IN (797);
UPDATE `biz_plan_item_material` SET material_id = 524 WHERE material_id IN (797);
UPDATE `biz_outsource_delivery_item` SET material_id = 524 WHERE material_id IN (797);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 524 WHERE material_id IN (797);
UPDATE `biz_sales_order_item` SET product_id = 524 WHERE product_id IN (797);
UPDATE `biz_work_order` SET product_id = 524 WHERE product_id IN (797);
UPDATE `biz_return_order_item` SET product_id = 524 WHERE product_id IN (797);
UPDATE `biz_mrp_production_suggestion` SET product_id = 524 WHERE product_id IN (797);
UPDATE `biz_process_route` SET product_id = 524 WHERE product_id IN (797);
UPDATE `biz_bom_item` SET material_id = 524 WHERE material_id IN (797);
UPDATE `biz_bom_item` SET product_id = 524 WHERE product_id IN (797);
UPDATE `biz_qc_task` SET material_id = 524 WHERE material_id IN (797);
UPDATE `biz_qc_task` SET product_id = 524 WHERE product_id IN (797);
UPDATE `biz_defect_record` SET material_id = 524 WHERE material_id IN (797);
UPDATE `biz_defect_record` SET product_id = 524 WHERE product_id IN (797);
UPDATE `biz_mrp_demand` SET material_id = 524 WHERE material_id IN (797);
UPDATE `biz_mrp_demand` SET product_id = 524 WHERE product_id IN (797);
UPDATE `biz_outsource_request` SET material_id = 524 WHERE material_id IN (797);
UPDATE `biz_outsource_request` SET product_id = 524 WHERE product_id IN (797);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (797) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 524
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 524 WHERE material_id IN (797)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 524) tmp);
DELETE FROM biz_inventory WHERE material_id IN (797);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (797);

-- ============================================================
-- 电机底座: 主ID=525(ZG0044) -> 正确编码=DJDZ-Y560-200-02JMS
-- 合并: id=798(DJDZ-Y560-200-02JMS)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'DJDZ-Y560-200-02JMS' AND id != 525;
UPDATE biz_material SET material_no = 'DJDZ-Y560-200-02JMS', deleted = 0, status = 'active' WHERE id = 525;

UPDATE `biz_inv_transaction` SET material_id = 525 WHERE material_id IN (798);
UPDATE `biz_inv_task_item` SET material_id = 525 WHERE material_id IN (798);
UPDATE `biz_inv_history_item` SET material_id = 525 WHERE material_id IN (798);
UPDATE `biz_inv_request_item` SET material_id = 525 WHERE material_id IN (798);
UPDATE `biz_inv_reservation` SET material_id = 525 WHERE material_id IN (798);
UPDATE `biz_inv_transfer_item` SET material_id = 525 WHERE material_id IN (798);
UPDATE `biz_inv_stocktaking_item` SET material_id = 525 WHERE material_id IN (798);
UPDATE `biz_purchase_request` SET material_id = 525 WHERE material_id IN (798);
UPDATE `biz_purchase_order_item` SET material_id = 525 WHERE material_id IN (798);
UPDATE `biz_purchase_receipt` SET material_id = 525 WHERE material_id IN (798);
UPDATE `biz_purchase_return_item` SET material_id = 525 WHERE material_id IN (798);
UPDATE `biz_wo_bom_snapshot` SET material_id = 525 WHERE material_id IN (798);
UPDATE `biz_plan_item_material` SET material_id = 525 WHERE material_id IN (798);
UPDATE `biz_outsource_delivery_item` SET material_id = 525 WHERE material_id IN (798);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 525 WHERE material_id IN (798);
UPDATE `biz_sales_order_item` SET product_id = 525 WHERE product_id IN (798);
UPDATE `biz_work_order` SET product_id = 525 WHERE product_id IN (798);
UPDATE `biz_return_order_item` SET product_id = 525 WHERE product_id IN (798);
UPDATE `biz_mrp_production_suggestion` SET product_id = 525 WHERE product_id IN (798);
UPDATE `biz_process_route` SET product_id = 525 WHERE product_id IN (798);
UPDATE `biz_bom_item` SET material_id = 525 WHERE material_id IN (798);
UPDATE `biz_bom_item` SET product_id = 525 WHERE product_id IN (798);
UPDATE `biz_qc_task` SET material_id = 525 WHERE material_id IN (798);
UPDATE `biz_qc_task` SET product_id = 525 WHERE product_id IN (798);
UPDATE `biz_defect_record` SET material_id = 525 WHERE material_id IN (798);
UPDATE `biz_defect_record` SET product_id = 525 WHERE product_id IN (798);
UPDATE `biz_mrp_demand` SET material_id = 525 WHERE material_id IN (798);
UPDATE `biz_mrp_demand` SET product_id = 525 WHERE product_id IN (798);
UPDATE `biz_outsource_request` SET material_id = 525 WHERE material_id IN (798);
UPDATE `biz_outsource_request` SET product_id = 525 WHERE product_id IN (798);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (798) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 525
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 525 WHERE material_id IN (798)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 525) tmp);
DELETE FROM biz_inventory WHERE material_id IN (798);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (798);

-- ============================================================
-- 联轴器罩: 主ID=526(ZG0045) -> 正确编码=TD-1260-1201A
-- 合并: id=799(TD-1260-1201A)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TD-1260-1201A' AND id != 526;
UPDATE biz_material SET material_no = 'TD-1260-1201A', deleted = 0, status = 'active' WHERE id = 526;

UPDATE `biz_inv_transaction` SET material_id = 526 WHERE material_id IN (799);
UPDATE `biz_inv_task_item` SET material_id = 526 WHERE material_id IN (799);
UPDATE `biz_inv_history_item` SET material_id = 526 WHERE material_id IN (799);
UPDATE `biz_inv_request_item` SET material_id = 526 WHERE material_id IN (799);
UPDATE `biz_inv_reservation` SET material_id = 526 WHERE material_id IN (799);
UPDATE `biz_inv_transfer_item` SET material_id = 526 WHERE material_id IN (799);
UPDATE `biz_inv_stocktaking_item` SET material_id = 526 WHERE material_id IN (799);
UPDATE `biz_purchase_request` SET material_id = 526 WHERE material_id IN (799);
UPDATE `biz_purchase_order_item` SET material_id = 526 WHERE material_id IN (799);
UPDATE `biz_purchase_receipt` SET material_id = 526 WHERE material_id IN (799);
UPDATE `biz_purchase_return_item` SET material_id = 526 WHERE material_id IN (799);
UPDATE `biz_wo_bom_snapshot` SET material_id = 526 WHERE material_id IN (799);
UPDATE `biz_plan_item_material` SET material_id = 526 WHERE material_id IN (799);
UPDATE `biz_outsource_delivery_item` SET material_id = 526 WHERE material_id IN (799);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 526 WHERE material_id IN (799);
UPDATE `biz_sales_order_item` SET product_id = 526 WHERE product_id IN (799);
UPDATE `biz_work_order` SET product_id = 526 WHERE product_id IN (799);
UPDATE `biz_return_order_item` SET product_id = 526 WHERE product_id IN (799);
UPDATE `biz_mrp_production_suggestion` SET product_id = 526 WHERE product_id IN (799);
UPDATE `biz_process_route` SET product_id = 526 WHERE product_id IN (799);
UPDATE `biz_bom_item` SET material_id = 526 WHERE material_id IN (799);
UPDATE `biz_bom_item` SET product_id = 526 WHERE product_id IN (799);
UPDATE `biz_qc_task` SET material_id = 526 WHERE material_id IN (799);
UPDATE `biz_qc_task` SET product_id = 526 WHERE product_id IN (799);
UPDATE `biz_defect_record` SET material_id = 526 WHERE material_id IN (799);
UPDATE `biz_defect_record` SET product_id = 526 WHERE product_id IN (799);
UPDATE `biz_mrp_demand` SET material_id = 526 WHERE material_id IN (799);
UPDATE `biz_mrp_demand` SET product_id = 526 WHERE product_id IN (799);
UPDATE `biz_outsource_request` SET material_id = 526 WHERE material_id IN (799);
UPDATE `biz_outsource_request` SET product_id = 526 WHERE product_id IN (799);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (799) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 526
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 526 WHERE material_id IN (799)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 526) tmp);
DELETE FROM biz_inventory WHERE material_id IN (799);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (799);

-- ============================================================
-- 泵支架: 主ID=527(ZG0046) -> 正确编码=ZGB-350-1100TD-0108HJ
-- 合并: id=800(ZGB-350-1100TD-0108HJ)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'ZGB-350-1100TD-0108HJ' AND id != 527;
UPDATE biz_material SET material_no = 'ZGB-350-1100TD-0108HJ', deleted = 0, status = 'active' WHERE id = 527;

UPDATE `biz_inv_transaction` SET material_id = 527 WHERE material_id IN (800);
UPDATE `biz_inv_task_item` SET material_id = 527 WHERE material_id IN (800);
UPDATE `biz_inv_history_item` SET material_id = 527 WHERE material_id IN (800);
UPDATE `biz_inv_request_item` SET material_id = 527 WHERE material_id IN (800);
UPDATE `biz_inv_reservation` SET material_id = 527 WHERE material_id IN (800);
UPDATE `biz_inv_transfer_item` SET material_id = 527 WHERE material_id IN (800);
UPDATE `biz_inv_stocktaking_item` SET material_id = 527 WHERE material_id IN (800);
UPDATE `biz_purchase_request` SET material_id = 527 WHERE material_id IN (800);
UPDATE `biz_purchase_order_item` SET material_id = 527 WHERE material_id IN (800);
UPDATE `biz_purchase_receipt` SET material_id = 527 WHERE material_id IN (800);
UPDATE `biz_purchase_return_item` SET material_id = 527 WHERE material_id IN (800);
UPDATE `biz_wo_bom_snapshot` SET material_id = 527 WHERE material_id IN (800);
UPDATE `biz_plan_item_material` SET material_id = 527 WHERE material_id IN (800);
UPDATE `biz_outsource_delivery_item` SET material_id = 527 WHERE material_id IN (800);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 527 WHERE material_id IN (800);
UPDATE `biz_sales_order_item` SET product_id = 527 WHERE product_id IN (800);
UPDATE `biz_work_order` SET product_id = 527 WHERE product_id IN (800);
UPDATE `biz_return_order_item` SET product_id = 527 WHERE product_id IN (800);
UPDATE `biz_mrp_production_suggestion` SET product_id = 527 WHERE product_id IN (800);
UPDATE `biz_process_route` SET product_id = 527 WHERE product_id IN (800);
UPDATE `biz_bom_item` SET material_id = 527 WHERE material_id IN (800);
UPDATE `biz_bom_item` SET product_id = 527 WHERE product_id IN (800);
UPDATE `biz_qc_task` SET material_id = 527 WHERE material_id IN (800);
UPDATE `biz_qc_task` SET product_id = 527 WHERE product_id IN (800);
UPDATE `biz_defect_record` SET material_id = 527 WHERE material_id IN (800);
UPDATE `biz_defect_record` SET product_id = 527 WHERE product_id IN (800);
UPDATE `biz_mrp_demand` SET material_id = 527 WHERE material_id IN (800);
UPDATE `biz_mrp_demand` SET product_id = 527 WHERE product_id IN (800);
UPDATE `biz_outsource_request` SET material_id = 527 WHERE material_id IN (800);
UPDATE `biz_outsource_request` SET product_id = 527 WHERE product_id IN (800);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (800) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 527
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 527 WHERE material_id IN (800)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 527) tmp);
DELETE FROM biz_inventory WHERE material_id IN (800);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (800);

-- ============================================================
-- 联轴器罩: 主ID=532(ZG0051) -> 正确编码=ZGB-910-1201A
-- 合并: id=804(ZGB-910-1201A)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'ZGB-910-1201A' AND id != 532;
UPDATE biz_material SET material_no = 'ZGB-910-1201A', deleted = 0, status = 'active' WHERE id = 532;

UPDATE `biz_inv_transaction` SET material_id = 532 WHERE material_id IN (804);
UPDATE `biz_inv_task_item` SET material_id = 532 WHERE material_id IN (804);
UPDATE `biz_inv_history_item` SET material_id = 532 WHERE material_id IN (804);
UPDATE `biz_inv_request_item` SET material_id = 532 WHERE material_id IN (804);
UPDATE `biz_inv_reservation` SET material_id = 532 WHERE material_id IN (804);
UPDATE `biz_inv_transfer_item` SET material_id = 532 WHERE material_id IN (804);
UPDATE `biz_inv_stocktaking_item` SET material_id = 532 WHERE material_id IN (804);
UPDATE `biz_purchase_request` SET material_id = 532 WHERE material_id IN (804);
UPDATE `biz_purchase_order_item` SET material_id = 532 WHERE material_id IN (804);
UPDATE `biz_purchase_receipt` SET material_id = 532 WHERE material_id IN (804);
UPDATE `biz_purchase_return_item` SET material_id = 532 WHERE material_id IN (804);
UPDATE `biz_wo_bom_snapshot` SET material_id = 532 WHERE material_id IN (804);
UPDATE `biz_plan_item_material` SET material_id = 532 WHERE material_id IN (804);
UPDATE `biz_outsource_delivery_item` SET material_id = 532 WHERE material_id IN (804);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 532 WHERE material_id IN (804);
UPDATE `biz_sales_order_item` SET product_id = 532 WHERE product_id IN (804);
UPDATE `biz_work_order` SET product_id = 532 WHERE product_id IN (804);
UPDATE `biz_return_order_item` SET product_id = 532 WHERE product_id IN (804);
UPDATE `biz_mrp_production_suggestion` SET product_id = 532 WHERE product_id IN (804);
UPDATE `biz_process_route` SET product_id = 532 WHERE product_id IN (804);
UPDATE `biz_bom_item` SET material_id = 532 WHERE material_id IN (804);
UPDATE `biz_bom_item` SET product_id = 532 WHERE product_id IN (804);
UPDATE `biz_qc_task` SET material_id = 532 WHERE material_id IN (804);
UPDATE `biz_qc_task` SET product_id = 532 WHERE product_id IN (804);
UPDATE `biz_defect_record` SET material_id = 532 WHERE material_id IN (804);
UPDATE `biz_defect_record` SET product_id = 532 WHERE product_id IN (804);
UPDATE `biz_mrp_demand` SET material_id = 532 WHERE material_id IN (804);
UPDATE `biz_mrp_demand` SET product_id = 532 WHERE product_id IN (804);
UPDATE `biz_outsource_request` SET material_id = 532 WHERE material_id IN (804);
UPDATE `biz_outsource_request` SET product_id = 532 WHERE product_id IN (804);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (804) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 532
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 532 WHERE material_id IN (804)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 532) tmp);
DELETE FROM biz_inventory WHERE material_id IN (804);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (804);

-- ============================================================
-- 底座: 主ID=537(ZG0056) -> 正确编码=SZ-100-500-Y250-01DC
-- 合并: id=807(SZ-100-500-Y250-01DC)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'SZ-100-500-Y250-01DC' AND id != 537;
UPDATE biz_material SET material_no = 'SZ-100-500-Y250-01DC', deleted = 0, status = 'active' WHERE id = 537;

UPDATE `biz_inv_transaction` SET material_id = 537 WHERE material_id IN (807);
UPDATE `biz_inv_task_item` SET material_id = 537 WHERE material_id IN (807);
UPDATE `biz_inv_history_item` SET material_id = 537 WHERE material_id IN (807);
UPDATE `biz_inv_request_item` SET material_id = 537 WHERE material_id IN (807);
UPDATE `biz_inv_reservation` SET material_id = 537 WHERE material_id IN (807);
UPDATE `biz_inv_transfer_item` SET material_id = 537 WHERE material_id IN (807);
UPDATE `biz_inv_stocktaking_item` SET material_id = 537 WHERE material_id IN (807);
UPDATE `biz_purchase_request` SET material_id = 537 WHERE material_id IN (807);
UPDATE `biz_purchase_order_item` SET material_id = 537 WHERE material_id IN (807);
UPDATE `biz_purchase_receipt` SET material_id = 537 WHERE material_id IN (807);
UPDATE `biz_purchase_return_item` SET material_id = 537 WHERE material_id IN (807);
UPDATE `biz_wo_bom_snapshot` SET material_id = 537 WHERE material_id IN (807);
UPDATE `biz_plan_item_material` SET material_id = 537 WHERE material_id IN (807);
UPDATE `biz_outsource_delivery_item` SET material_id = 537 WHERE material_id IN (807);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 537 WHERE material_id IN (807);
UPDATE `biz_sales_order_item` SET product_id = 537 WHERE product_id IN (807);
UPDATE `biz_work_order` SET product_id = 537 WHERE product_id IN (807);
UPDATE `biz_return_order_item` SET product_id = 537 WHERE product_id IN (807);
UPDATE `biz_mrp_production_suggestion` SET product_id = 537 WHERE product_id IN (807);
UPDATE `biz_process_route` SET product_id = 537 WHERE product_id IN (807);
UPDATE `biz_bom_item` SET material_id = 537 WHERE material_id IN (807);
UPDATE `biz_bom_item` SET product_id = 537 WHERE product_id IN (807);
UPDATE `biz_qc_task` SET material_id = 537 WHERE material_id IN (807);
UPDATE `biz_qc_task` SET product_id = 537 WHERE product_id IN (807);
UPDATE `biz_defect_record` SET material_id = 537 WHERE material_id IN (807);
UPDATE `biz_defect_record` SET product_id = 537 WHERE product_id IN (807);
UPDATE `biz_mrp_demand` SET material_id = 537 WHERE material_id IN (807);
UPDATE `biz_mrp_demand` SET product_id = 537 WHERE product_id IN (807);
UPDATE `biz_outsource_request` SET material_id = 537 WHERE material_id IN (807);
UPDATE `biz_outsource_request` SET product_id = 537 WHERE product_id IN (807);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (807) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 537
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 537 WHERE material_id IN (807)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 537) tmp);
DELETE FROM biz_inventory WHERE material_id IN (807);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (807);

-- ============================================================
-- 皮带罩: 主ID=538(ZG0057) -> 正确编码=ZGB-350-3000-01CR
-- 合并: id=635(ZGB-350-3000-01CR)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'ZGB-350-3000-01CR' AND id != 538;
UPDATE biz_material SET material_no = 'ZGB-350-3000-01CR', deleted = 0, status = 'active' WHERE id = 538;

UPDATE `biz_inv_transaction` SET material_id = 538 WHERE material_id IN (635);
UPDATE `biz_inv_task_item` SET material_id = 538 WHERE material_id IN (635);
UPDATE `biz_inv_history_item` SET material_id = 538 WHERE material_id IN (635);
UPDATE `biz_inv_request_item` SET material_id = 538 WHERE material_id IN (635);
UPDATE `biz_inv_reservation` SET material_id = 538 WHERE material_id IN (635);
UPDATE `biz_inv_transfer_item` SET material_id = 538 WHERE material_id IN (635);
UPDATE `biz_inv_stocktaking_item` SET material_id = 538 WHERE material_id IN (635);
UPDATE `biz_purchase_request` SET material_id = 538 WHERE material_id IN (635);
UPDATE `biz_purchase_order_item` SET material_id = 538 WHERE material_id IN (635);
UPDATE `biz_purchase_receipt` SET material_id = 538 WHERE material_id IN (635);
UPDATE `biz_purchase_return_item` SET material_id = 538 WHERE material_id IN (635);
UPDATE `biz_wo_bom_snapshot` SET material_id = 538 WHERE material_id IN (635);
UPDATE `biz_plan_item_material` SET material_id = 538 WHERE material_id IN (635);
UPDATE `biz_outsource_delivery_item` SET material_id = 538 WHERE material_id IN (635);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 538 WHERE material_id IN (635);
UPDATE `biz_sales_order_item` SET product_id = 538 WHERE product_id IN (635);
UPDATE `biz_work_order` SET product_id = 538 WHERE product_id IN (635);
UPDATE `biz_return_order_item` SET product_id = 538 WHERE product_id IN (635);
UPDATE `biz_mrp_production_suggestion` SET product_id = 538 WHERE product_id IN (635);
UPDATE `biz_process_route` SET product_id = 538 WHERE product_id IN (635);
UPDATE `biz_bom_item` SET material_id = 538 WHERE material_id IN (635);
UPDATE `biz_bom_item` SET product_id = 538 WHERE product_id IN (635);
UPDATE `biz_qc_task` SET material_id = 538 WHERE material_id IN (635);
UPDATE `biz_qc_task` SET product_id = 538 WHERE product_id IN (635);
UPDATE `biz_defect_record` SET material_id = 538 WHERE material_id IN (635);
UPDATE `biz_defect_record` SET product_id = 538 WHERE product_id IN (635);
UPDATE `biz_mrp_demand` SET material_id = 538 WHERE material_id IN (635);
UPDATE `biz_mrp_demand` SET product_id = 538 WHERE product_id IN (635);
UPDATE `biz_outsource_request` SET material_id = 538 WHERE material_id IN (635);
UPDATE `biz_outsource_request` SET product_id = 538 WHERE product_id IN (635);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (635) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 538
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 538 WHERE material_id IN (635)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 538) tmp);
DELETE FROM biz_inventory WHERE material_id IN (635);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (635);

-- ============================================================
-- 发货支架: 主ID=539(ZG0058) -> 正确编码=TZJE-200-550-FHZJ-155
-- 合并: id=636(TZJE-200-550-FHZJ-155)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJE-200-550-FHZJ-155' AND id != 539;
UPDATE biz_material SET material_no = 'TZJE-200-550-FHZJ-155', deleted = 0, status = 'active' WHERE id = 539;

UPDATE `biz_inv_transaction` SET material_id = 539 WHERE material_id IN (636);
UPDATE `biz_inv_task_item` SET material_id = 539 WHERE material_id IN (636);
UPDATE `biz_inv_history_item` SET material_id = 539 WHERE material_id IN (636);
UPDATE `biz_inv_request_item` SET material_id = 539 WHERE material_id IN (636);
UPDATE `biz_inv_reservation` SET material_id = 539 WHERE material_id IN (636);
UPDATE `biz_inv_transfer_item` SET material_id = 539 WHERE material_id IN (636);
UPDATE `biz_inv_stocktaking_item` SET material_id = 539 WHERE material_id IN (636);
UPDATE `biz_purchase_request` SET material_id = 539 WHERE material_id IN (636);
UPDATE `biz_purchase_order_item` SET material_id = 539 WHERE material_id IN (636);
UPDATE `biz_purchase_receipt` SET material_id = 539 WHERE material_id IN (636);
UPDATE `biz_purchase_return_item` SET material_id = 539 WHERE material_id IN (636);
UPDATE `biz_wo_bom_snapshot` SET material_id = 539 WHERE material_id IN (636);
UPDATE `biz_plan_item_material` SET material_id = 539 WHERE material_id IN (636);
UPDATE `biz_outsource_delivery_item` SET material_id = 539 WHERE material_id IN (636);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 539 WHERE material_id IN (636);
UPDATE `biz_sales_order_item` SET product_id = 539 WHERE product_id IN (636);
UPDATE `biz_work_order` SET product_id = 539 WHERE product_id IN (636);
UPDATE `biz_return_order_item` SET product_id = 539 WHERE product_id IN (636);
UPDATE `biz_mrp_production_suggestion` SET product_id = 539 WHERE product_id IN (636);
UPDATE `biz_process_route` SET product_id = 539 WHERE product_id IN (636);
UPDATE `biz_bom_item` SET material_id = 539 WHERE material_id IN (636);
UPDATE `biz_bom_item` SET product_id = 539 WHERE product_id IN (636);
UPDATE `biz_qc_task` SET material_id = 539 WHERE material_id IN (636);
UPDATE `biz_qc_task` SET product_id = 539 WHERE product_id IN (636);
UPDATE `biz_defect_record` SET material_id = 539 WHERE material_id IN (636);
UPDATE `biz_defect_record` SET product_id = 539 WHERE product_id IN (636);
UPDATE `biz_mrp_demand` SET material_id = 539 WHERE material_id IN (636);
UPDATE `biz_mrp_demand` SET product_id = 539 WHERE product_id IN (636);
UPDATE `biz_outsource_request` SET material_id = 539 WHERE material_id IN (636);
UPDATE `biz_outsource_request` SET product_id = 539 WHERE product_id IN (636);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (636) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 539
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 539 WHERE material_id IN (636)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 539) tmp);
DELETE FROM biz_inventory WHERE material_id IN (636);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (636);

-- ============================================================
-- 皮带罩: 主ID=540(ZG0059) -> 正确编码=TZJE-1301-1700-01CL/CR
-- 合并: id=637(TZJE-1301-1700-01CL/CR)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJE-1301-1700-01CL/CR' AND id != 540;
UPDATE biz_material SET material_no = 'TZJE-1301-1700-01CL/CR', deleted = 0, status = 'active' WHERE id = 540;

UPDATE `biz_inv_transaction` SET material_id = 540 WHERE material_id IN (637);
UPDATE `biz_inv_task_item` SET material_id = 540 WHERE material_id IN (637);
UPDATE `biz_inv_history_item` SET material_id = 540 WHERE material_id IN (637);
UPDATE `biz_inv_request_item` SET material_id = 540 WHERE material_id IN (637);
UPDATE `biz_inv_reservation` SET material_id = 540 WHERE material_id IN (637);
UPDATE `biz_inv_transfer_item` SET material_id = 540 WHERE material_id IN (637);
UPDATE `biz_inv_stocktaking_item` SET material_id = 540 WHERE material_id IN (637);
UPDATE `biz_purchase_request` SET material_id = 540 WHERE material_id IN (637);
UPDATE `biz_purchase_order_item` SET material_id = 540 WHERE material_id IN (637);
UPDATE `biz_purchase_receipt` SET material_id = 540 WHERE material_id IN (637);
UPDATE `biz_purchase_return_item` SET material_id = 540 WHERE material_id IN (637);
UPDATE `biz_wo_bom_snapshot` SET material_id = 540 WHERE material_id IN (637);
UPDATE `biz_plan_item_material` SET material_id = 540 WHERE material_id IN (637);
UPDATE `biz_outsource_delivery_item` SET material_id = 540 WHERE material_id IN (637);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 540 WHERE material_id IN (637);
UPDATE `biz_sales_order_item` SET product_id = 540 WHERE product_id IN (637);
UPDATE `biz_work_order` SET product_id = 540 WHERE product_id IN (637);
UPDATE `biz_return_order_item` SET product_id = 540 WHERE product_id IN (637);
UPDATE `biz_mrp_production_suggestion` SET product_id = 540 WHERE product_id IN (637);
UPDATE `biz_process_route` SET product_id = 540 WHERE product_id IN (637);
UPDATE `biz_bom_item` SET material_id = 540 WHERE material_id IN (637);
UPDATE `biz_bom_item` SET product_id = 540 WHERE product_id IN (637);
UPDATE `biz_qc_task` SET material_id = 540 WHERE material_id IN (637);
UPDATE `biz_qc_task` SET product_id = 540 WHERE product_id IN (637);
UPDATE `biz_defect_record` SET material_id = 540 WHERE material_id IN (637);
UPDATE `biz_defect_record` SET product_id = 540 WHERE product_id IN (637);
UPDATE `biz_mrp_demand` SET material_id = 540 WHERE material_id IN (637);
UPDATE `biz_mrp_demand` SET product_id = 540 WHERE product_id IN (637);
UPDATE `biz_outsource_request` SET material_id = 540 WHERE material_id IN (637);
UPDATE `biz_outsource_request` SET product_id = 540 WHERE product_id IN (637);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (637) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 540
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 540 WHERE material_id IN (637)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 540) tmp);
DELETE FROM biz_inventory WHERE material_id IN (637);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (637);

-- ============================================================
-- 发货支架: 主ID=541(ZG0060) -> 正确编码=TZJD-100-370-FHZJ-150
-- 合并: id=638(TZJD-100-370-FHZJ-150)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJD-100-370-FHZJ-150' AND id != 541;
UPDATE biz_material SET material_no = 'TZJD-100-370-FHZJ-150', deleted = 0, status = 'active' WHERE id = 541;

UPDATE `biz_inv_transaction` SET material_id = 541 WHERE material_id IN (638);
UPDATE `biz_inv_task_item` SET material_id = 541 WHERE material_id IN (638);
UPDATE `biz_inv_history_item` SET material_id = 541 WHERE material_id IN (638);
UPDATE `biz_inv_request_item` SET material_id = 541 WHERE material_id IN (638);
UPDATE `biz_inv_reservation` SET material_id = 541 WHERE material_id IN (638);
UPDATE `biz_inv_transfer_item` SET material_id = 541 WHERE material_id IN (638);
UPDATE `biz_inv_stocktaking_item` SET material_id = 541 WHERE material_id IN (638);
UPDATE `biz_purchase_request` SET material_id = 541 WHERE material_id IN (638);
UPDATE `biz_purchase_order_item` SET material_id = 541 WHERE material_id IN (638);
UPDATE `biz_purchase_receipt` SET material_id = 541 WHERE material_id IN (638);
UPDATE `biz_purchase_return_item` SET material_id = 541 WHERE material_id IN (638);
UPDATE `biz_wo_bom_snapshot` SET material_id = 541 WHERE material_id IN (638);
UPDATE `biz_plan_item_material` SET material_id = 541 WHERE material_id IN (638);
UPDATE `biz_outsource_delivery_item` SET material_id = 541 WHERE material_id IN (638);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 541 WHERE material_id IN (638);
UPDATE `biz_sales_order_item` SET product_id = 541 WHERE product_id IN (638);
UPDATE `biz_work_order` SET product_id = 541 WHERE product_id IN (638);
UPDATE `biz_return_order_item` SET product_id = 541 WHERE product_id IN (638);
UPDATE `biz_mrp_production_suggestion` SET product_id = 541 WHERE product_id IN (638);
UPDATE `biz_process_route` SET product_id = 541 WHERE product_id IN (638);
UPDATE `biz_bom_item` SET material_id = 541 WHERE material_id IN (638);
UPDATE `biz_bom_item` SET product_id = 541 WHERE product_id IN (638);
UPDATE `biz_qc_task` SET material_id = 541 WHERE material_id IN (638);
UPDATE `biz_qc_task` SET product_id = 541 WHERE product_id IN (638);
UPDATE `biz_defect_record` SET material_id = 541 WHERE material_id IN (638);
UPDATE `biz_defect_record` SET product_id = 541 WHERE product_id IN (638);
UPDATE `biz_mrp_demand` SET material_id = 541 WHERE material_id IN (638);
UPDATE `biz_mrp_demand` SET product_id = 541 WHERE product_id IN (638);
UPDATE `biz_outsource_request` SET material_id = 541 WHERE material_id IN (638);
UPDATE `biz_outsource_request` SET product_id = 541 WHERE product_id IN (638);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (638) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 541
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 541 WHERE material_id IN (638)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 541) tmp);
DELETE FROM biz_inventory WHERE material_id IN (638);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (638);

-- ============================================================
-- 发货底座: 主ID=546(ZG0065) -> 正确编码=FHDZ-ST-200
-- 合并: id=643(FHDZ-ST-200)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'FHDZ-ST-200' AND id != 546;
UPDATE biz_material SET material_no = 'FHDZ-ST-200', deleted = 0, status = 'active' WHERE id = 546;

UPDATE `biz_inv_transaction` SET material_id = 546 WHERE material_id IN (643);
UPDATE `biz_inv_task_item` SET material_id = 546 WHERE material_id IN (643);
UPDATE `biz_inv_history_item` SET material_id = 546 WHERE material_id IN (643);
UPDATE `biz_inv_request_item` SET material_id = 546 WHERE material_id IN (643);
UPDATE `biz_inv_reservation` SET material_id = 546 WHERE material_id IN (643);
UPDATE `biz_inv_transfer_item` SET material_id = 546 WHERE material_id IN (643);
UPDATE `biz_inv_stocktaking_item` SET material_id = 546 WHERE material_id IN (643);
UPDATE `biz_purchase_request` SET material_id = 546 WHERE material_id IN (643);
UPDATE `biz_purchase_order_item` SET material_id = 546 WHERE material_id IN (643);
UPDATE `biz_purchase_receipt` SET material_id = 546 WHERE material_id IN (643);
UPDATE `biz_purchase_return_item` SET material_id = 546 WHERE material_id IN (643);
UPDATE `biz_wo_bom_snapshot` SET material_id = 546 WHERE material_id IN (643);
UPDATE `biz_plan_item_material` SET material_id = 546 WHERE material_id IN (643);
UPDATE `biz_outsource_delivery_item` SET material_id = 546 WHERE material_id IN (643);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 546 WHERE material_id IN (643);
UPDATE `biz_sales_order_item` SET product_id = 546 WHERE product_id IN (643);
UPDATE `biz_work_order` SET product_id = 546 WHERE product_id IN (643);
UPDATE `biz_return_order_item` SET product_id = 546 WHERE product_id IN (643);
UPDATE `biz_mrp_production_suggestion` SET product_id = 546 WHERE product_id IN (643);
UPDATE `biz_process_route` SET product_id = 546 WHERE product_id IN (643);
UPDATE `biz_bom_item` SET material_id = 546 WHERE material_id IN (643);
UPDATE `biz_bom_item` SET product_id = 546 WHERE product_id IN (643);
UPDATE `biz_qc_task` SET material_id = 546 WHERE material_id IN (643);
UPDATE `biz_qc_task` SET product_id = 546 WHERE product_id IN (643);
UPDATE `biz_defect_record` SET material_id = 546 WHERE material_id IN (643);
UPDATE `biz_defect_record` SET product_id = 546 WHERE product_id IN (643);
UPDATE `biz_mrp_demand` SET material_id = 546 WHERE material_id IN (643);
UPDATE `biz_mrp_demand` SET product_id = 546 WHERE product_id IN (643);
UPDATE `biz_outsource_request` SET material_id = 546 WHERE material_id IN (643);
UPDATE `biz_outsource_request` SET product_id = 546 WHERE product_id IN (643);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (643) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 546
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 546 WHERE material_id IN (643)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 546) tmp);
DELETE FROM biz_inventory WHERE material_id IN (643);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (643);

-- ============================================================
-- 泵支架: 主ID=547(ZG0066) -> 正确编码=JGTZJST-300-0108-251132
-- 合并: id=644(JGTZJST-300-0108-251132)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'JGTZJST-300-0108-251132' AND id != 547;
UPDATE biz_material SET material_no = 'JGTZJST-300-0108-251132', deleted = 0, status = 'active' WHERE id = 547;

UPDATE `biz_inv_transaction` SET material_id = 547 WHERE material_id IN (644);
UPDATE `biz_inv_task_item` SET material_id = 547 WHERE material_id IN (644);
UPDATE `biz_inv_history_item` SET material_id = 547 WHERE material_id IN (644);
UPDATE `biz_inv_request_item` SET material_id = 547 WHERE material_id IN (644);
UPDATE `biz_inv_reservation` SET material_id = 547 WHERE material_id IN (644);
UPDATE `biz_inv_transfer_item` SET material_id = 547 WHERE material_id IN (644);
UPDATE `biz_inv_stocktaking_item` SET material_id = 547 WHERE material_id IN (644);
UPDATE `biz_purchase_request` SET material_id = 547 WHERE material_id IN (644);
UPDATE `biz_purchase_order_item` SET material_id = 547 WHERE material_id IN (644);
UPDATE `biz_purchase_receipt` SET material_id = 547 WHERE material_id IN (644);
UPDATE `biz_purchase_return_item` SET material_id = 547 WHERE material_id IN (644);
UPDATE `biz_wo_bom_snapshot` SET material_id = 547 WHERE material_id IN (644);
UPDATE `biz_plan_item_material` SET material_id = 547 WHERE material_id IN (644);
UPDATE `biz_outsource_delivery_item` SET material_id = 547 WHERE material_id IN (644);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 547 WHERE material_id IN (644);
UPDATE `biz_sales_order_item` SET product_id = 547 WHERE product_id IN (644);
UPDATE `biz_work_order` SET product_id = 547 WHERE product_id IN (644);
UPDATE `biz_return_order_item` SET product_id = 547 WHERE product_id IN (644);
UPDATE `biz_mrp_production_suggestion` SET product_id = 547 WHERE product_id IN (644);
UPDATE `biz_process_route` SET product_id = 547 WHERE product_id IN (644);
UPDATE `biz_bom_item` SET material_id = 547 WHERE material_id IN (644);
UPDATE `biz_bom_item` SET product_id = 547 WHERE product_id IN (644);
UPDATE `biz_qc_task` SET material_id = 547 WHERE material_id IN (644);
UPDATE `biz_qc_task` SET product_id = 547 WHERE product_id IN (644);
UPDATE `biz_defect_record` SET material_id = 547 WHERE material_id IN (644);
UPDATE `biz_defect_record` SET product_id = 547 WHERE product_id IN (644);
UPDATE `biz_mrp_demand` SET material_id = 547 WHERE material_id IN (644);
UPDATE `biz_mrp_demand` SET product_id = 547 WHERE product_id IN (644);
UPDATE `biz_outsource_request` SET material_id = 547 WHERE material_id IN (644);
UPDATE `biz_outsource_request` SET product_id = 547 WHERE product_id IN (644);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (644) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 547
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 547 WHERE material_id IN (644)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 547) tmp);
DELETE FROM biz_inventory WHERE material_id IN (644);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (644);

-- ============================================================
-- 皮带罩: 主ID=548(ZG0067) -> 正确编码=TZJST-1301-2600-02CR
-- 合并: id=645(TZJST-1301-2600-02CR)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJST-1301-2600-02CR' AND id != 548;
UPDATE biz_material SET material_no = 'TZJST-1301-2600-02CR', deleted = 0, status = 'active' WHERE id = 548;

UPDATE `biz_inv_transaction` SET material_id = 548 WHERE material_id IN (645);
UPDATE `biz_inv_task_item` SET material_id = 548 WHERE material_id IN (645);
UPDATE `biz_inv_history_item` SET material_id = 548 WHERE material_id IN (645);
UPDATE `biz_inv_request_item` SET material_id = 548 WHERE material_id IN (645);
UPDATE `biz_inv_reservation` SET material_id = 548 WHERE material_id IN (645);
UPDATE `biz_inv_transfer_item` SET material_id = 548 WHERE material_id IN (645);
UPDATE `biz_inv_stocktaking_item` SET material_id = 548 WHERE material_id IN (645);
UPDATE `biz_purchase_request` SET material_id = 548 WHERE material_id IN (645);
UPDATE `biz_purchase_order_item` SET material_id = 548 WHERE material_id IN (645);
UPDATE `biz_purchase_receipt` SET material_id = 548 WHERE material_id IN (645);
UPDATE `biz_purchase_return_item` SET material_id = 548 WHERE material_id IN (645);
UPDATE `biz_wo_bom_snapshot` SET material_id = 548 WHERE material_id IN (645);
UPDATE `biz_plan_item_material` SET material_id = 548 WHERE material_id IN (645);
UPDATE `biz_outsource_delivery_item` SET material_id = 548 WHERE material_id IN (645);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 548 WHERE material_id IN (645);
UPDATE `biz_sales_order_item` SET product_id = 548 WHERE product_id IN (645);
UPDATE `biz_work_order` SET product_id = 548 WHERE product_id IN (645);
UPDATE `biz_return_order_item` SET product_id = 548 WHERE product_id IN (645);
UPDATE `biz_mrp_production_suggestion` SET product_id = 548 WHERE product_id IN (645);
UPDATE `biz_process_route` SET product_id = 548 WHERE product_id IN (645);
UPDATE `biz_bom_item` SET material_id = 548 WHERE material_id IN (645);
UPDATE `biz_bom_item` SET product_id = 548 WHERE product_id IN (645);
UPDATE `biz_qc_task` SET material_id = 548 WHERE material_id IN (645);
UPDATE `biz_qc_task` SET product_id = 548 WHERE product_id IN (645);
UPDATE `biz_defect_record` SET material_id = 548 WHERE material_id IN (645);
UPDATE `biz_defect_record` SET product_id = 548 WHERE product_id IN (645);
UPDATE `biz_mrp_demand` SET material_id = 548 WHERE material_id IN (645);
UPDATE `biz_mrp_demand` SET product_id = 548 WHERE product_id IN (645);
UPDATE `biz_outsource_request` SET material_id = 548 WHERE material_id IN (645);
UPDATE `biz_outsource_request` SET product_id = 548 WHERE product_id IN (645);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (645) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 548
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 548 WHERE material_id IN (645)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 548) tmp);
DELETE FROM biz_inventory WHERE material_id IN (645);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (645);

-- ============================================================
-- 皮带罩: 主ID=550(ZG0069) -> 正确编码=TZJS-1301-2300-03CL/CR
-- 合并: id=647(TZJS-1301-2300-03CL/CR)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJS-1301-2300-03CL/CR' AND id != 550;
UPDATE biz_material SET material_no = 'TZJS-1301-2300-03CL/CR', deleted = 0, status = 'active' WHERE id = 550;

UPDATE `biz_inv_transaction` SET material_id = 550 WHERE material_id IN (647);
UPDATE `biz_inv_task_item` SET material_id = 550 WHERE material_id IN (647);
UPDATE `biz_inv_history_item` SET material_id = 550 WHERE material_id IN (647);
UPDATE `biz_inv_request_item` SET material_id = 550 WHERE material_id IN (647);
UPDATE `biz_inv_reservation` SET material_id = 550 WHERE material_id IN (647);
UPDATE `biz_inv_transfer_item` SET material_id = 550 WHERE material_id IN (647);
UPDATE `biz_inv_stocktaking_item` SET material_id = 550 WHERE material_id IN (647);
UPDATE `biz_purchase_request` SET material_id = 550 WHERE material_id IN (647);
UPDATE `biz_purchase_order_item` SET material_id = 550 WHERE material_id IN (647);
UPDATE `biz_purchase_receipt` SET material_id = 550 WHERE material_id IN (647);
UPDATE `biz_purchase_return_item` SET material_id = 550 WHERE material_id IN (647);
UPDATE `biz_wo_bom_snapshot` SET material_id = 550 WHERE material_id IN (647);
UPDATE `biz_plan_item_material` SET material_id = 550 WHERE material_id IN (647);
UPDATE `biz_outsource_delivery_item` SET material_id = 550 WHERE material_id IN (647);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 550 WHERE material_id IN (647);
UPDATE `biz_sales_order_item` SET product_id = 550 WHERE product_id IN (647);
UPDATE `biz_work_order` SET product_id = 550 WHERE product_id IN (647);
UPDATE `biz_return_order_item` SET product_id = 550 WHERE product_id IN (647);
UPDATE `biz_mrp_production_suggestion` SET product_id = 550 WHERE product_id IN (647);
UPDATE `biz_process_route` SET product_id = 550 WHERE product_id IN (647);
UPDATE `biz_bom_item` SET material_id = 550 WHERE material_id IN (647);
UPDATE `biz_bom_item` SET product_id = 550 WHERE product_id IN (647);
UPDATE `biz_qc_task` SET material_id = 550 WHERE material_id IN (647);
UPDATE `biz_qc_task` SET product_id = 550 WHERE product_id IN (647);
UPDATE `biz_defect_record` SET material_id = 550 WHERE material_id IN (647);
UPDATE `biz_defect_record` SET product_id = 550 WHERE product_id IN (647);
UPDATE `biz_mrp_demand` SET material_id = 550 WHERE material_id IN (647);
UPDATE `biz_mrp_demand` SET product_id = 550 WHERE product_id IN (647);
UPDATE `biz_outsource_request` SET material_id = 550 WHERE material_id IN (647);
UPDATE `biz_outsource_request` SET product_id = 550 WHERE product_id IN (647);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (647) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 550
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 550 WHERE material_id IN (647)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 550) tmp);
DELETE FROM biz_inventory WHERE material_id IN (647);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (647);

-- ============================================================
-- 皮带罩: 主ID=553(ZG0072) -> 正确编码=TZJE-1301-1700-04CL/CR
-- 合并: id=650(TZJE-1301-1700-04CL/CR)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJE-1301-1700-04CL/CR' AND id != 553;
UPDATE biz_material SET material_no = 'TZJE-1301-1700-04CL/CR', deleted = 0, status = 'active' WHERE id = 553;

UPDATE `biz_inv_transaction` SET material_id = 553 WHERE material_id IN (650);
UPDATE `biz_inv_task_item` SET material_id = 553 WHERE material_id IN (650);
UPDATE `biz_inv_history_item` SET material_id = 553 WHERE material_id IN (650);
UPDATE `biz_inv_request_item` SET material_id = 553 WHERE material_id IN (650);
UPDATE `biz_inv_reservation` SET material_id = 553 WHERE material_id IN (650);
UPDATE `biz_inv_transfer_item` SET material_id = 553 WHERE material_id IN (650);
UPDATE `biz_inv_stocktaking_item` SET material_id = 553 WHERE material_id IN (650);
UPDATE `biz_purchase_request` SET material_id = 553 WHERE material_id IN (650);
UPDATE `biz_purchase_order_item` SET material_id = 553 WHERE material_id IN (650);
UPDATE `biz_purchase_receipt` SET material_id = 553 WHERE material_id IN (650);
UPDATE `biz_purchase_return_item` SET material_id = 553 WHERE material_id IN (650);
UPDATE `biz_wo_bom_snapshot` SET material_id = 553 WHERE material_id IN (650);
UPDATE `biz_plan_item_material` SET material_id = 553 WHERE material_id IN (650);
UPDATE `biz_outsource_delivery_item` SET material_id = 553 WHERE material_id IN (650);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 553 WHERE material_id IN (650);
UPDATE `biz_sales_order_item` SET product_id = 553 WHERE product_id IN (650);
UPDATE `biz_work_order` SET product_id = 553 WHERE product_id IN (650);
UPDATE `biz_return_order_item` SET product_id = 553 WHERE product_id IN (650);
UPDATE `biz_mrp_production_suggestion` SET product_id = 553 WHERE product_id IN (650);
UPDATE `biz_process_route` SET product_id = 553 WHERE product_id IN (650);
UPDATE `biz_bom_item` SET material_id = 553 WHERE material_id IN (650);
UPDATE `biz_bom_item` SET product_id = 553 WHERE product_id IN (650);
UPDATE `biz_qc_task` SET material_id = 553 WHERE material_id IN (650);
UPDATE `biz_qc_task` SET product_id = 553 WHERE product_id IN (650);
UPDATE `biz_defect_record` SET material_id = 553 WHERE material_id IN (650);
UPDATE `biz_defect_record` SET product_id = 553 WHERE product_id IN (650);
UPDATE `biz_mrp_demand` SET material_id = 553 WHERE material_id IN (650);
UPDATE `biz_mrp_demand` SET product_id = 553 WHERE product_id IN (650);
UPDATE `biz_outsource_request` SET material_id = 553 WHERE material_id IN (650);
UPDATE `biz_outsource_request` SET product_id = 553 WHERE product_id IN (650);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (650) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 553
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 553 WHERE material_id IN (650)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 553) tmp);
DELETE FROM biz_inventory WHERE material_id IN (650);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (650);

-- ============================================================
-- 发货支架: 主ID=555(ZG0074) -> 正确编码=TZJC-50-230-FHZJ-130
-- 合并: id=652(TZJC-50-230-FHZJ-130)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJC-50-230-FHZJ-130' AND id != 555;
UPDATE biz_material SET material_no = 'TZJC-50-230-FHZJ-130', deleted = 0, status = 'active' WHERE id = 555;

UPDATE `biz_inv_transaction` SET material_id = 555 WHERE material_id IN (652);
UPDATE `biz_inv_task_item` SET material_id = 555 WHERE material_id IN (652);
UPDATE `biz_inv_history_item` SET material_id = 555 WHERE material_id IN (652);
UPDATE `biz_inv_request_item` SET material_id = 555 WHERE material_id IN (652);
UPDATE `biz_inv_reservation` SET material_id = 555 WHERE material_id IN (652);
UPDATE `biz_inv_transfer_item` SET material_id = 555 WHERE material_id IN (652);
UPDATE `biz_inv_stocktaking_item` SET material_id = 555 WHERE material_id IN (652);
UPDATE `biz_purchase_request` SET material_id = 555 WHERE material_id IN (652);
UPDATE `biz_purchase_order_item` SET material_id = 555 WHERE material_id IN (652);
UPDATE `biz_purchase_receipt` SET material_id = 555 WHERE material_id IN (652);
UPDATE `biz_purchase_return_item` SET material_id = 555 WHERE material_id IN (652);
UPDATE `biz_wo_bom_snapshot` SET material_id = 555 WHERE material_id IN (652);
UPDATE `biz_plan_item_material` SET material_id = 555 WHERE material_id IN (652);
UPDATE `biz_outsource_delivery_item` SET material_id = 555 WHERE material_id IN (652);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 555 WHERE material_id IN (652);
UPDATE `biz_sales_order_item` SET product_id = 555 WHERE product_id IN (652);
UPDATE `biz_work_order` SET product_id = 555 WHERE product_id IN (652);
UPDATE `biz_return_order_item` SET product_id = 555 WHERE product_id IN (652);
UPDATE `biz_mrp_production_suggestion` SET product_id = 555 WHERE product_id IN (652);
UPDATE `biz_process_route` SET product_id = 555 WHERE product_id IN (652);
UPDATE `biz_bom_item` SET material_id = 555 WHERE material_id IN (652);
UPDATE `biz_bom_item` SET product_id = 555 WHERE product_id IN (652);
UPDATE `biz_qc_task` SET material_id = 555 WHERE material_id IN (652);
UPDATE `biz_qc_task` SET product_id = 555 WHERE product_id IN (652);
UPDATE `biz_defect_record` SET material_id = 555 WHERE material_id IN (652);
UPDATE `biz_defect_record` SET product_id = 555 WHERE product_id IN (652);
UPDATE `biz_mrp_demand` SET material_id = 555 WHERE material_id IN (652);
UPDATE `biz_mrp_demand` SET product_id = 555 WHERE product_id IN (652);
UPDATE `biz_outsource_request` SET material_id = 555 WHERE material_id IN (652);
UPDATE `biz_outsource_request` SET product_id = 555 WHERE product_id IN (652);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (652) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 555
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 555 WHERE material_id IN (652)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 555) tmp);
DELETE FROM biz_inventory WHERE material_id IN (652);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (652);

-- ============================================================
-- 电机底座: 主ID=556(ZG0075) -> 正确编码=TZJC-1701-03CVA
-- 合并: id=653(TZJC-1701-03CVA)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJC-1701-03CVA' AND id != 556;
UPDATE biz_material SET material_no = 'TZJC-1701-03CVA', deleted = 0, status = 'active' WHERE id = 556;

UPDATE `biz_inv_transaction` SET material_id = 556 WHERE material_id IN (653);
UPDATE `biz_inv_task_item` SET material_id = 556 WHERE material_id IN (653);
UPDATE `biz_inv_history_item` SET material_id = 556 WHERE material_id IN (653);
UPDATE `biz_inv_request_item` SET material_id = 556 WHERE material_id IN (653);
UPDATE `biz_inv_reservation` SET material_id = 556 WHERE material_id IN (653);
UPDATE `biz_inv_transfer_item` SET material_id = 556 WHERE material_id IN (653);
UPDATE `biz_inv_stocktaking_item` SET material_id = 556 WHERE material_id IN (653);
UPDATE `biz_purchase_request` SET material_id = 556 WHERE material_id IN (653);
UPDATE `biz_purchase_order_item` SET material_id = 556 WHERE material_id IN (653);
UPDATE `biz_purchase_receipt` SET material_id = 556 WHERE material_id IN (653);
UPDATE `biz_purchase_return_item` SET material_id = 556 WHERE material_id IN (653);
UPDATE `biz_wo_bom_snapshot` SET material_id = 556 WHERE material_id IN (653);
UPDATE `biz_plan_item_material` SET material_id = 556 WHERE material_id IN (653);
UPDATE `biz_outsource_delivery_item` SET material_id = 556 WHERE material_id IN (653);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 556 WHERE material_id IN (653);
UPDATE `biz_sales_order_item` SET product_id = 556 WHERE product_id IN (653);
UPDATE `biz_work_order` SET product_id = 556 WHERE product_id IN (653);
UPDATE `biz_return_order_item` SET product_id = 556 WHERE product_id IN (653);
UPDATE `biz_mrp_production_suggestion` SET product_id = 556 WHERE product_id IN (653);
UPDATE `biz_process_route` SET product_id = 556 WHERE product_id IN (653);
UPDATE `biz_bom_item` SET material_id = 556 WHERE material_id IN (653);
UPDATE `biz_bom_item` SET product_id = 556 WHERE product_id IN (653);
UPDATE `biz_qc_task` SET material_id = 556 WHERE material_id IN (653);
UPDATE `biz_qc_task` SET product_id = 556 WHERE product_id IN (653);
UPDATE `biz_defect_record` SET material_id = 556 WHERE material_id IN (653);
UPDATE `biz_defect_record` SET product_id = 556 WHERE product_id IN (653);
UPDATE `biz_mrp_demand` SET material_id = 556 WHERE material_id IN (653);
UPDATE `biz_mrp_demand` SET product_id = 556 WHERE product_id IN (653);
UPDATE `biz_outsource_request` SET material_id = 556 WHERE material_id IN (653);
UPDATE `biz_outsource_request` SET product_id = 556 WHERE product_id IN (653);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (653) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 556
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 556 WHERE material_id IN (653)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 556) tmp);
DELETE FROM biz_inventory WHERE material_id IN (653);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (653);

-- ============================================================
-- 泵底座: 主ID=562(ZG0081) -> 正确编码=JGTZJD-0902HJ-251175
-- 合并: id=660(JGTZJD-0902HJ-251175)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'JGTZJD-0902HJ-251175' AND id != 562;
UPDATE biz_material SET material_no = 'JGTZJD-0902HJ-251175', deleted = 0, status = 'active' WHERE id = 562;

UPDATE `biz_inv_transaction` SET material_id = 562 WHERE material_id IN (660);
UPDATE `biz_inv_task_item` SET material_id = 562 WHERE material_id IN (660);
UPDATE `biz_inv_history_item` SET material_id = 562 WHERE material_id IN (660);
UPDATE `biz_inv_request_item` SET material_id = 562 WHERE material_id IN (660);
UPDATE `biz_inv_reservation` SET material_id = 562 WHERE material_id IN (660);
UPDATE `biz_inv_transfer_item` SET material_id = 562 WHERE material_id IN (660);
UPDATE `biz_inv_stocktaking_item` SET material_id = 562 WHERE material_id IN (660);
UPDATE `biz_purchase_request` SET material_id = 562 WHERE material_id IN (660);
UPDATE `biz_purchase_order_item` SET material_id = 562 WHERE material_id IN (660);
UPDATE `biz_purchase_receipt` SET material_id = 562 WHERE material_id IN (660);
UPDATE `biz_purchase_return_item` SET material_id = 562 WHERE material_id IN (660);
UPDATE `biz_wo_bom_snapshot` SET material_id = 562 WHERE material_id IN (660);
UPDATE `biz_plan_item_material` SET material_id = 562 WHERE material_id IN (660);
UPDATE `biz_outsource_delivery_item` SET material_id = 562 WHERE material_id IN (660);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 562 WHERE material_id IN (660);
UPDATE `biz_sales_order_item` SET product_id = 562 WHERE product_id IN (660);
UPDATE `biz_work_order` SET product_id = 562 WHERE product_id IN (660);
UPDATE `biz_return_order_item` SET product_id = 562 WHERE product_id IN (660);
UPDATE `biz_mrp_production_suggestion` SET product_id = 562 WHERE product_id IN (660);
UPDATE `biz_process_route` SET product_id = 562 WHERE product_id IN (660);
UPDATE `biz_bom_item` SET material_id = 562 WHERE material_id IN (660);
UPDATE `biz_bom_item` SET product_id = 562 WHERE product_id IN (660);
UPDATE `biz_qc_task` SET material_id = 562 WHERE material_id IN (660);
UPDATE `biz_qc_task` SET product_id = 562 WHERE product_id IN (660);
UPDATE `biz_defect_record` SET material_id = 562 WHERE material_id IN (660);
UPDATE `biz_defect_record` SET product_id = 562 WHERE product_id IN (660);
UPDATE `biz_mrp_demand` SET material_id = 562 WHERE material_id IN (660);
UPDATE `biz_mrp_demand` SET product_id = 562 WHERE product_id IN (660);
UPDATE `biz_outsource_request` SET material_id = 562 WHERE material_id IN (660);
UPDATE `biz_outsource_request` SET product_id = 562 WHERE product_id IN (660);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (660) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 562
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 562 WHERE material_id IN (660)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 562) tmp);
DELETE FROM biz_inventory WHERE material_id IN (660);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (660);

-- ============================================================
-- 泵底座: 主ID=563(ZG0082) -> 正确编码=JGTZJD-0902HJ-251176
-- 合并: id=661(JGTZJD-0902HJ-251176)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'JGTZJD-0902HJ-251176' AND id != 563;
UPDATE biz_material SET material_no = 'JGTZJD-0902HJ-251176', deleted = 0, status = 'active' WHERE id = 563;

UPDATE `biz_inv_transaction` SET material_id = 563 WHERE material_id IN (661);
UPDATE `biz_inv_task_item` SET material_id = 563 WHERE material_id IN (661);
UPDATE `biz_inv_history_item` SET material_id = 563 WHERE material_id IN (661);
UPDATE `biz_inv_request_item` SET material_id = 563 WHERE material_id IN (661);
UPDATE `biz_inv_reservation` SET material_id = 563 WHERE material_id IN (661);
UPDATE `biz_inv_transfer_item` SET material_id = 563 WHERE material_id IN (661);
UPDATE `biz_inv_stocktaking_item` SET material_id = 563 WHERE material_id IN (661);
UPDATE `biz_purchase_request` SET material_id = 563 WHERE material_id IN (661);
UPDATE `biz_purchase_order_item` SET material_id = 563 WHERE material_id IN (661);
UPDATE `biz_purchase_receipt` SET material_id = 563 WHERE material_id IN (661);
UPDATE `biz_purchase_return_item` SET material_id = 563 WHERE material_id IN (661);
UPDATE `biz_wo_bom_snapshot` SET material_id = 563 WHERE material_id IN (661);
UPDATE `biz_plan_item_material` SET material_id = 563 WHERE material_id IN (661);
UPDATE `biz_outsource_delivery_item` SET material_id = 563 WHERE material_id IN (661);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 563 WHERE material_id IN (661);
UPDATE `biz_sales_order_item` SET product_id = 563 WHERE product_id IN (661);
UPDATE `biz_work_order` SET product_id = 563 WHERE product_id IN (661);
UPDATE `biz_return_order_item` SET product_id = 563 WHERE product_id IN (661);
UPDATE `biz_mrp_production_suggestion` SET product_id = 563 WHERE product_id IN (661);
UPDATE `biz_process_route` SET product_id = 563 WHERE product_id IN (661);
UPDATE `biz_bom_item` SET material_id = 563 WHERE material_id IN (661);
UPDATE `biz_bom_item` SET product_id = 563 WHERE product_id IN (661);
UPDATE `biz_qc_task` SET material_id = 563 WHERE material_id IN (661);
UPDATE `biz_qc_task` SET product_id = 563 WHERE product_id IN (661);
UPDATE `biz_defect_record` SET material_id = 563 WHERE material_id IN (661);
UPDATE `biz_defect_record` SET product_id = 563 WHERE product_id IN (661);
UPDATE `biz_mrp_demand` SET material_id = 563 WHERE material_id IN (661);
UPDATE `biz_mrp_demand` SET product_id = 563 WHERE product_id IN (661);
UPDATE `biz_outsource_request` SET material_id = 563 WHERE material_id IN (661);
UPDATE `biz_outsource_request` SET product_id = 563 WHERE product_id IN (661);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (661) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 563
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 563 WHERE material_id IN (661)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 563) tmp);
DELETE FROM biz_inventory WHERE material_id IN (661);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (661);

-- ============================================================
-- 电机导轨垫板: 主ID=564(ZG0083) -> 正确编码=DB-251176
-- 合并: id=663(DB-251176)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'DB-251176' AND id != 564;
UPDATE biz_material SET material_no = 'DB-251176', deleted = 0, status = 'active' WHERE id = 564;

UPDATE `biz_inv_transaction` SET material_id = 564 WHERE material_id IN (663);
UPDATE `biz_inv_task_item` SET material_id = 564 WHERE material_id IN (663);
UPDATE `biz_inv_history_item` SET material_id = 564 WHERE material_id IN (663);
UPDATE `biz_inv_request_item` SET material_id = 564 WHERE material_id IN (663);
UPDATE `biz_inv_reservation` SET material_id = 564 WHERE material_id IN (663);
UPDATE `biz_inv_transfer_item` SET material_id = 564 WHERE material_id IN (663);
UPDATE `biz_inv_stocktaking_item` SET material_id = 564 WHERE material_id IN (663);
UPDATE `biz_purchase_request` SET material_id = 564 WHERE material_id IN (663);
UPDATE `biz_purchase_order_item` SET material_id = 564 WHERE material_id IN (663);
UPDATE `biz_purchase_receipt` SET material_id = 564 WHERE material_id IN (663);
UPDATE `biz_purchase_return_item` SET material_id = 564 WHERE material_id IN (663);
UPDATE `biz_wo_bom_snapshot` SET material_id = 564 WHERE material_id IN (663);
UPDATE `biz_plan_item_material` SET material_id = 564 WHERE material_id IN (663);
UPDATE `biz_outsource_delivery_item` SET material_id = 564 WHERE material_id IN (663);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 564 WHERE material_id IN (663);
UPDATE `biz_sales_order_item` SET product_id = 564 WHERE product_id IN (663);
UPDATE `biz_work_order` SET product_id = 564 WHERE product_id IN (663);
UPDATE `biz_return_order_item` SET product_id = 564 WHERE product_id IN (663);
UPDATE `biz_mrp_production_suggestion` SET product_id = 564 WHERE product_id IN (663);
UPDATE `biz_process_route` SET product_id = 564 WHERE product_id IN (663);
UPDATE `biz_bom_item` SET material_id = 564 WHERE material_id IN (663);
UPDATE `biz_bom_item` SET product_id = 564 WHERE product_id IN (663);
UPDATE `biz_qc_task` SET material_id = 564 WHERE material_id IN (663);
UPDATE `biz_qc_task` SET product_id = 564 WHERE product_id IN (663);
UPDATE `biz_defect_record` SET material_id = 564 WHERE material_id IN (663);
UPDATE `biz_defect_record` SET product_id = 564 WHERE product_id IN (663);
UPDATE `biz_mrp_demand` SET material_id = 564 WHERE material_id IN (663);
UPDATE `biz_mrp_demand` SET product_id = 564 WHERE product_id IN (663);
UPDATE `biz_outsource_request` SET material_id = 564 WHERE material_id IN (663);
UPDATE `biz_outsource_request` SET product_id = 564 WHERE product_id IN (663);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (663) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 564
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 564 WHERE material_id IN (663)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 564) tmp);
DELETE FROM biz_inventory WHERE material_id IN (663);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (663);

-- ============================================================
-- 皮带罩: 主ID=566(ZG0085) -> 正确编码=TZJST-1301-2600-01CL
-- 合并: id=666(TZJST-1301-2600-01CL)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJST-1301-2600-01CL' AND id != 566;
UPDATE biz_material SET material_no = 'TZJST-1301-2600-01CL', deleted = 0, status = 'active' WHERE id = 566;

UPDATE `biz_inv_transaction` SET material_id = 566 WHERE material_id IN (666);
UPDATE `biz_inv_task_item` SET material_id = 566 WHERE material_id IN (666);
UPDATE `biz_inv_history_item` SET material_id = 566 WHERE material_id IN (666);
UPDATE `biz_inv_request_item` SET material_id = 566 WHERE material_id IN (666);
UPDATE `biz_inv_reservation` SET material_id = 566 WHERE material_id IN (666);
UPDATE `biz_inv_transfer_item` SET material_id = 566 WHERE material_id IN (666);
UPDATE `biz_inv_stocktaking_item` SET material_id = 566 WHERE material_id IN (666);
UPDATE `biz_purchase_request` SET material_id = 566 WHERE material_id IN (666);
UPDATE `biz_purchase_order_item` SET material_id = 566 WHERE material_id IN (666);
UPDATE `biz_purchase_receipt` SET material_id = 566 WHERE material_id IN (666);
UPDATE `biz_purchase_return_item` SET material_id = 566 WHERE material_id IN (666);
UPDATE `biz_wo_bom_snapshot` SET material_id = 566 WHERE material_id IN (666);
UPDATE `biz_plan_item_material` SET material_id = 566 WHERE material_id IN (666);
UPDATE `biz_outsource_delivery_item` SET material_id = 566 WHERE material_id IN (666);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 566 WHERE material_id IN (666);
UPDATE `biz_sales_order_item` SET product_id = 566 WHERE product_id IN (666);
UPDATE `biz_work_order` SET product_id = 566 WHERE product_id IN (666);
UPDATE `biz_return_order_item` SET product_id = 566 WHERE product_id IN (666);
UPDATE `biz_mrp_production_suggestion` SET product_id = 566 WHERE product_id IN (666);
UPDATE `biz_process_route` SET product_id = 566 WHERE product_id IN (666);
UPDATE `biz_bom_item` SET material_id = 566 WHERE material_id IN (666);
UPDATE `biz_bom_item` SET product_id = 566 WHERE product_id IN (666);
UPDATE `biz_qc_task` SET material_id = 566 WHERE material_id IN (666);
UPDATE `biz_qc_task` SET product_id = 566 WHERE product_id IN (666);
UPDATE `biz_defect_record` SET material_id = 566 WHERE material_id IN (666);
UPDATE `biz_defect_record` SET product_id = 566 WHERE product_id IN (666);
UPDATE `biz_mrp_demand` SET material_id = 566 WHERE material_id IN (666);
UPDATE `biz_mrp_demand` SET product_id = 566 WHERE product_id IN (666);
UPDATE `biz_outsource_request` SET material_id = 566 WHERE material_id IN (666);
UPDATE `biz_outsource_request` SET product_id = 566 WHERE product_id IN (666);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (666) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 566
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 566 WHERE material_id IN (666)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 566) tmp);
DELETE FROM biz_inventory WHERE material_id IN (666);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (666);

-- ============================================================
-- 支架（毛坯 ）: 主ID=576(ZG0095) -> 正确编码=XS24-1089.20-4-01
-- 合并: id=746(XS24-1089.20-4-01)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'XS24-1089.20-4-01' AND id != 576;
UPDATE biz_material SET material_no = 'XS24-1089.20-4-01', deleted = 0, status = 'active' WHERE id = 576;

UPDATE `biz_inv_transaction` SET material_id = 576 WHERE material_id IN (746);
UPDATE `biz_inv_task_item` SET material_id = 576 WHERE material_id IN (746);
UPDATE `biz_inv_history_item` SET material_id = 576 WHERE material_id IN (746);
UPDATE `biz_inv_request_item` SET material_id = 576 WHERE material_id IN (746);
UPDATE `biz_inv_reservation` SET material_id = 576 WHERE material_id IN (746);
UPDATE `biz_inv_transfer_item` SET material_id = 576 WHERE material_id IN (746);
UPDATE `biz_inv_stocktaking_item` SET material_id = 576 WHERE material_id IN (746);
UPDATE `biz_purchase_request` SET material_id = 576 WHERE material_id IN (746);
UPDATE `biz_purchase_order_item` SET material_id = 576 WHERE material_id IN (746);
UPDATE `biz_purchase_receipt` SET material_id = 576 WHERE material_id IN (746);
UPDATE `biz_purchase_return_item` SET material_id = 576 WHERE material_id IN (746);
UPDATE `biz_wo_bom_snapshot` SET material_id = 576 WHERE material_id IN (746);
UPDATE `biz_plan_item_material` SET material_id = 576 WHERE material_id IN (746);
UPDATE `biz_outsource_delivery_item` SET material_id = 576 WHERE material_id IN (746);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 576 WHERE material_id IN (746);
UPDATE `biz_sales_order_item` SET product_id = 576 WHERE product_id IN (746);
UPDATE `biz_work_order` SET product_id = 576 WHERE product_id IN (746);
UPDATE `biz_return_order_item` SET product_id = 576 WHERE product_id IN (746);
UPDATE `biz_mrp_production_suggestion` SET product_id = 576 WHERE product_id IN (746);
UPDATE `biz_process_route` SET product_id = 576 WHERE product_id IN (746);
UPDATE `biz_bom_item` SET material_id = 576 WHERE material_id IN (746);
UPDATE `biz_bom_item` SET product_id = 576 WHERE product_id IN (746);
UPDATE `biz_qc_task` SET material_id = 576 WHERE material_id IN (746);
UPDATE `biz_qc_task` SET product_id = 576 WHERE product_id IN (746);
UPDATE `biz_defect_record` SET material_id = 576 WHERE material_id IN (746);
UPDATE `biz_defect_record` SET product_id = 576 WHERE product_id IN (746);
UPDATE `biz_mrp_demand` SET material_id = 576 WHERE material_id IN (746);
UPDATE `biz_mrp_demand` SET product_id = 576 WHERE product_id IN (746);
UPDATE `biz_outsource_request` SET material_id = 576 WHERE material_id IN (746);
UPDATE `biz_outsource_request` SET product_id = 576 WHERE product_id IN (746);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (746) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 576
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 576 WHERE material_id IN (746)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 576) tmp);
DELETE FROM biz_inventory WHERE material_id IN (746);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (746);

-- ============================================================
-- 底座（毛坯 ）: 主ID=577(ZG0096) -> 正确编码=XS24-1089.20-4-02
-- 合并: id=747(XS24-1089.20-4-02)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'XS24-1089.20-4-02' AND id != 577;
UPDATE biz_material SET material_no = 'XS24-1089.20-4-02', deleted = 0, status = 'active' WHERE id = 577;

UPDATE `biz_inv_transaction` SET material_id = 577 WHERE material_id IN (747);
UPDATE `biz_inv_task_item` SET material_id = 577 WHERE material_id IN (747);
UPDATE `biz_inv_history_item` SET material_id = 577 WHERE material_id IN (747);
UPDATE `biz_inv_request_item` SET material_id = 577 WHERE material_id IN (747);
UPDATE `biz_inv_reservation` SET material_id = 577 WHERE material_id IN (747);
UPDATE `biz_inv_transfer_item` SET material_id = 577 WHERE material_id IN (747);
UPDATE `biz_inv_stocktaking_item` SET material_id = 577 WHERE material_id IN (747);
UPDATE `biz_purchase_request` SET material_id = 577 WHERE material_id IN (747);
UPDATE `biz_purchase_order_item` SET material_id = 577 WHERE material_id IN (747);
UPDATE `biz_purchase_receipt` SET material_id = 577 WHERE material_id IN (747);
UPDATE `biz_purchase_return_item` SET material_id = 577 WHERE material_id IN (747);
UPDATE `biz_wo_bom_snapshot` SET material_id = 577 WHERE material_id IN (747);
UPDATE `biz_plan_item_material` SET material_id = 577 WHERE material_id IN (747);
UPDATE `biz_outsource_delivery_item` SET material_id = 577 WHERE material_id IN (747);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 577 WHERE material_id IN (747);
UPDATE `biz_sales_order_item` SET product_id = 577 WHERE product_id IN (747);
UPDATE `biz_work_order` SET product_id = 577 WHERE product_id IN (747);
UPDATE `biz_return_order_item` SET product_id = 577 WHERE product_id IN (747);
UPDATE `biz_mrp_production_suggestion` SET product_id = 577 WHERE product_id IN (747);
UPDATE `biz_process_route` SET product_id = 577 WHERE product_id IN (747);
UPDATE `biz_bom_item` SET material_id = 577 WHERE material_id IN (747);
UPDATE `biz_bom_item` SET product_id = 577 WHERE product_id IN (747);
UPDATE `biz_qc_task` SET material_id = 577 WHERE material_id IN (747);
UPDATE `biz_qc_task` SET product_id = 577 WHERE product_id IN (747);
UPDATE `biz_defect_record` SET material_id = 577 WHERE material_id IN (747);
UPDATE `biz_defect_record` SET product_id = 577 WHERE product_id IN (747);
UPDATE `biz_mrp_demand` SET material_id = 577 WHERE material_id IN (747);
UPDATE `biz_mrp_demand` SET product_id = 577 WHERE product_id IN (747);
UPDATE `biz_outsource_request` SET material_id = 577 WHERE material_id IN (747);
UPDATE `biz_outsource_request` SET product_id = 577 WHERE product_id IN (747);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (747) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 577
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 577 WHERE material_id IN (747)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 577) tmp);
DELETE FROM biz_inventory WHERE material_id IN (747);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (747);

-- ============================================================
-- 支架（毛坯 ）: 主ID=578(ZG0097) -> 正确编码=XS24-1090.20-4-01
-- 合并: id=748(XS24-1090.20-4-01)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'XS24-1090.20-4-01' AND id != 578;
UPDATE biz_material SET material_no = 'XS24-1090.20-4-01', deleted = 0, status = 'active' WHERE id = 578;

UPDATE `biz_inv_transaction` SET material_id = 578 WHERE material_id IN (748);
UPDATE `biz_inv_task_item` SET material_id = 578 WHERE material_id IN (748);
UPDATE `biz_inv_history_item` SET material_id = 578 WHERE material_id IN (748);
UPDATE `biz_inv_request_item` SET material_id = 578 WHERE material_id IN (748);
UPDATE `biz_inv_reservation` SET material_id = 578 WHERE material_id IN (748);
UPDATE `biz_inv_transfer_item` SET material_id = 578 WHERE material_id IN (748);
UPDATE `biz_inv_stocktaking_item` SET material_id = 578 WHERE material_id IN (748);
UPDATE `biz_purchase_request` SET material_id = 578 WHERE material_id IN (748);
UPDATE `biz_purchase_order_item` SET material_id = 578 WHERE material_id IN (748);
UPDATE `biz_purchase_receipt` SET material_id = 578 WHERE material_id IN (748);
UPDATE `biz_purchase_return_item` SET material_id = 578 WHERE material_id IN (748);
UPDATE `biz_wo_bom_snapshot` SET material_id = 578 WHERE material_id IN (748);
UPDATE `biz_plan_item_material` SET material_id = 578 WHERE material_id IN (748);
UPDATE `biz_outsource_delivery_item` SET material_id = 578 WHERE material_id IN (748);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 578 WHERE material_id IN (748);
UPDATE `biz_sales_order_item` SET product_id = 578 WHERE product_id IN (748);
UPDATE `biz_work_order` SET product_id = 578 WHERE product_id IN (748);
UPDATE `biz_return_order_item` SET product_id = 578 WHERE product_id IN (748);
UPDATE `biz_mrp_production_suggestion` SET product_id = 578 WHERE product_id IN (748);
UPDATE `biz_process_route` SET product_id = 578 WHERE product_id IN (748);
UPDATE `biz_bom_item` SET material_id = 578 WHERE material_id IN (748);
UPDATE `biz_bom_item` SET product_id = 578 WHERE product_id IN (748);
UPDATE `biz_qc_task` SET material_id = 578 WHERE material_id IN (748);
UPDATE `biz_qc_task` SET product_id = 578 WHERE product_id IN (748);
UPDATE `biz_defect_record` SET material_id = 578 WHERE material_id IN (748);
UPDATE `biz_defect_record` SET product_id = 578 WHERE product_id IN (748);
UPDATE `biz_mrp_demand` SET material_id = 578 WHERE material_id IN (748);
UPDATE `biz_mrp_demand` SET product_id = 578 WHERE product_id IN (748);
UPDATE `biz_outsource_request` SET material_id = 578 WHERE material_id IN (748);
UPDATE `biz_outsource_request` SET product_id = 578 WHERE product_id IN (748);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (748) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 578
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 578 WHERE material_id IN (748)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 578) tmp);
DELETE FROM biz_inventory WHERE material_id IN (748);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (748);

-- ============================================================
-- 底座（毛坯 ）: 主ID=579(ZG0098) -> 正确编码=XS24-1090.20-4-02
-- 合并: id=749(XS24-1090.20-4-02)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'XS24-1090.20-4-02' AND id != 579;
UPDATE biz_material SET material_no = 'XS24-1090.20-4-02', deleted = 0, status = 'active' WHERE id = 579;

UPDATE `biz_inv_transaction` SET material_id = 579 WHERE material_id IN (749);
UPDATE `biz_inv_task_item` SET material_id = 579 WHERE material_id IN (749);
UPDATE `biz_inv_history_item` SET material_id = 579 WHERE material_id IN (749);
UPDATE `biz_inv_request_item` SET material_id = 579 WHERE material_id IN (749);
UPDATE `biz_inv_reservation` SET material_id = 579 WHERE material_id IN (749);
UPDATE `biz_inv_transfer_item` SET material_id = 579 WHERE material_id IN (749);
UPDATE `biz_inv_stocktaking_item` SET material_id = 579 WHERE material_id IN (749);
UPDATE `biz_purchase_request` SET material_id = 579 WHERE material_id IN (749);
UPDATE `biz_purchase_order_item` SET material_id = 579 WHERE material_id IN (749);
UPDATE `biz_purchase_receipt` SET material_id = 579 WHERE material_id IN (749);
UPDATE `biz_purchase_return_item` SET material_id = 579 WHERE material_id IN (749);
UPDATE `biz_wo_bom_snapshot` SET material_id = 579 WHERE material_id IN (749);
UPDATE `biz_plan_item_material` SET material_id = 579 WHERE material_id IN (749);
UPDATE `biz_outsource_delivery_item` SET material_id = 579 WHERE material_id IN (749);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 579 WHERE material_id IN (749);
UPDATE `biz_sales_order_item` SET product_id = 579 WHERE product_id IN (749);
UPDATE `biz_work_order` SET product_id = 579 WHERE product_id IN (749);
UPDATE `biz_return_order_item` SET product_id = 579 WHERE product_id IN (749);
UPDATE `biz_mrp_production_suggestion` SET product_id = 579 WHERE product_id IN (749);
UPDATE `biz_process_route` SET product_id = 579 WHERE product_id IN (749);
UPDATE `biz_bom_item` SET material_id = 579 WHERE material_id IN (749);
UPDATE `biz_bom_item` SET product_id = 579 WHERE product_id IN (749);
UPDATE `biz_qc_task` SET material_id = 579 WHERE material_id IN (749);
UPDATE `biz_qc_task` SET product_id = 579 WHERE product_id IN (749);
UPDATE `biz_defect_record` SET material_id = 579 WHERE material_id IN (749);
UPDATE `biz_defect_record` SET product_id = 579 WHERE product_id IN (749);
UPDATE `biz_mrp_demand` SET material_id = 579 WHERE material_id IN (749);
UPDATE `biz_mrp_demand` SET product_id = 579 WHERE product_id IN (749);
UPDATE `biz_outsource_request` SET material_id = 579 WHERE material_id IN (749);
UPDATE `biz_outsource_request` SET product_id = 579 WHERE product_id IN (749);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (749) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 579
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 579 WHERE material_id IN (749)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 579) tmp);
DELETE FROM biz_inventory WHERE material_id IN (749);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (749);

-- ============================================================
-- 底座: 主ID=582(ZG0101) -> 正确编码=JGSZ-100-500-Y315DC-HK241445
-- 合并: id=752(JGSZ-100-500-Y315DC-HK241445)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'JGSZ-100-500-Y315DC-HK241445' AND id != 582;
UPDATE biz_material SET material_no = 'JGSZ-100-500-Y315DC-HK241445', deleted = 0, status = 'active' WHERE id = 582;

UPDATE `biz_inv_transaction` SET material_id = 582 WHERE material_id IN (752);
UPDATE `biz_inv_task_item` SET material_id = 582 WHERE material_id IN (752);
UPDATE `biz_inv_history_item` SET material_id = 582 WHERE material_id IN (752);
UPDATE `biz_inv_request_item` SET material_id = 582 WHERE material_id IN (752);
UPDATE `biz_inv_reservation` SET material_id = 582 WHERE material_id IN (752);
UPDATE `biz_inv_transfer_item` SET material_id = 582 WHERE material_id IN (752);
UPDATE `biz_inv_stocktaking_item` SET material_id = 582 WHERE material_id IN (752);
UPDATE `biz_purchase_request` SET material_id = 582 WHERE material_id IN (752);
UPDATE `biz_purchase_order_item` SET material_id = 582 WHERE material_id IN (752);
UPDATE `biz_purchase_receipt` SET material_id = 582 WHERE material_id IN (752);
UPDATE `biz_purchase_return_item` SET material_id = 582 WHERE material_id IN (752);
UPDATE `biz_wo_bom_snapshot` SET material_id = 582 WHERE material_id IN (752);
UPDATE `biz_plan_item_material` SET material_id = 582 WHERE material_id IN (752);
UPDATE `biz_outsource_delivery_item` SET material_id = 582 WHERE material_id IN (752);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 582 WHERE material_id IN (752);
UPDATE `biz_sales_order_item` SET product_id = 582 WHERE product_id IN (752);
UPDATE `biz_work_order` SET product_id = 582 WHERE product_id IN (752);
UPDATE `biz_return_order_item` SET product_id = 582 WHERE product_id IN (752);
UPDATE `biz_mrp_production_suggestion` SET product_id = 582 WHERE product_id IN (752);
UPDATE `biz_process_route` SET product_id = 582 WHERE product_id IN (752);
UPDATE `biz_bom_item` SET material_id = 582 WHERE material_id IN (752);
UPDATE `biz_bom_item` SET product_id = 582 WHERE product_id IN (752);
UPDATE `biz_qc_task` SET material_id = 582 WHERE material_id IN (752);
UPDATE `biz_qc_task` SET product_id = 582 WHERE product_id IN (752);
UPDATE `biz_defect_record` SET material_id = 582 WHERE material_id IN (752);
UPDATE `biz_defect_record` SET product_id = 582 WHERE product_id IN (752);
UPDATE `biz_mrp_demand` SET material_id = 582 WHERE material_id IN (752);
UPDATE `biz_mrp_demand` SET product_id = 582 WHERE product_id IN (752);
UPDATE `biz_outsource_request` SET material_id = 582 WHERE material_id IN (752);
UPDATE `biz_outsource_request` SET product_id = 582 WHERE product_id IN (752);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (752) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 582
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 582 WHERE material_id IN (752)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 582) tmp);
DELETE FROM biz_inventory WHERE material_id IN (752);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (752);

-- ============================================================
-- 底座: 主ID=587(ZG0106) -> 正确编码=JGF-0901HJ-HK24-0765
-- 合并: id=757(JGF-0901HJ-HK24-0765)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'JGF-0901HJ-HK24-0765' AND id != 587;
UPDATE biz_material SET material_no = 'JGF-0901HJ-HK24-0765', deleted = 0, status = 'active' WHERE id = 587;

UPDATE `biz_inv_transaction` SET material_id = 587 WHERE material_id IN (757);
UPDATE `biz_inv_task_item` SET material_id = 587 WHERE material_id IN (757);
UPDATE `biz_inv_history_item` SET material_id = 587 WHERE material_id IN (757);
UPDATE `biz_inv_request_item` SET material_id = 587 WHERE material_id IN (757);
UPDATE `biz_inv_reservation` SET material_id = 587 WHERE material_id IN (757);
UPDATE `biz_inv_transfer_item` SET material_id = 587 WHERE material_id IN (757);
UPDATE `biz_inv_stocktaking_item` SET material_id = 587 WHERE material_id IN (757);
UPDATE `biz_purchase_request` SET material_id = 587 WHERE material_id IN (757);
UPDATE `biz_purchase_order_item` SET material_id = 587 WHERE material_id IN (757);
UPDATE `biz_purchase_receipt` SET material_id = 587 WHERE material_id IN (757);
UPDATE `biz_purchase_return_item` SET material_id = 587 WHERE material_id IN (757);
UPDATE `biz_wo_bom_snapshot` SET material_id = 587 WHERE material_id IN (757);
UPDATE `biz_plan_item_material` SET material_id = 587 WHERE material_id IN (757);
UPDATE `biz_outsource_delivery_item` SET material_id = 587 WHERE material_id IN (757);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 587 WHERE material_id IN (757);
UPDATE `biz_sales_order_item` SET product_id = 587 WHERE product_id IN (757);
UPDATE `biz_work_order` SET product_id = 587 WHERE product_id IN (757);
UPDATE `biz_return_order_item` SET product_id = 587 WHERE product_id IN (757);
UPDATE `biz_mrp_production_suggestion` SET product_id = 587 WHERE product_id IN (757);
UPDATE `biz_process_route` SET product_id = 587 WHERE product_id IN (757);
UPDATE `biz_bom_item` SET material_id = 587 WHERE material_id IN (757);
UPDATE `biz_bom_item` SET product_id = 587 WHERE product_id IN (757);
UPDATE `biz_qc_task` SET material_id = 587 WHERE material_id IN (757);
UPDATE `biz_qc_task` SET product_id = 587 WHERE product_id IN (757);
UPDATE `biz_defect_record` SET material_id = 587 WHERE material_id IN (757);
UPDATE `biz_defect_record` SET product_id = 587 WHERE product_id IN (757);
UPDATE `biz_mrp_demand` SET material_id = 587 WHERE material_id IN (757);
UPDATE `biz_mrp_demand` SET product_id = 587 WHERE product_id IN (757);
UPDATE `biz_outsource_request` SET material_id = 587 WHERE material_id IN (757);
UPDATE `biz_outsource_request` SET product_id = 587 WHERE product_id IN (757);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (757) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 587
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 587 WHERE material_id IN (757)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 587) tmp);
DELETE FROM biz_inventory WHERE material_id IN (757);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (757);

-- ============================================================
-- 底座: 主ID=590(ZG0109) -> 正确编码=JGTZJE-Y315-02DCF-220781
-- 合并: id=761(JGTZJE-Y315-02DCF-220781)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'JGTZJE-Y315-02DCF-220781' AND id != 590;
UPDATE biz_material SET material_no = 'JGTZJE-Y315-02DCF-220781', deleted = 0, status = 'active' WHERE id = 590;

UPDATE `biz_inv_transaction` SET material_id = 590 WHERE material_id IN (761);
UPDATE `biz_inv_task_item` SET material_id = 590 WHERE material_id IN (761);
UPDATE `biz_inv_history_item` SET material_id = 590 WHERE material_id IN (761);
UPDATE `biz_inv_request_item` SET material_id = 590 WHERE material_id IN (761);
UPDATE `biz_inv_reservation` SET material_id = 590 WHERE material_id IN (761);
UPDATE `biz_inv_transfer_item` SET material_id = 590 WHERE material_id IN (761);
UPDATE `biz_inv_stocktaking_item` SET material_id = 590 WHERE material_id IN (761);
UPDATE `biz_purchase_request` SET material_id = 590 WHERE material_id IN (761);
UPDATE `biz_purchase_order_item` SET material_id = 590 WHERE material_id IN (761);
UPDATE `biz_purchase_receipt` SET material_id = 590 WHERE material_id IN (761);
UPDATE `biz_purchase_return_item` SET material_id = 590 WHERE material_id IN (761);
UPDATE `biz_wo_bom_snapshot` SET material_id = 590 WHERE material_id IN (761);
UPDATE `biz_plan_item_material` SET material_id = 590 WHERE material_id IN (761);
UPDATE `biz_outsource_delivery_item` SET material_id = 590 WHERE material_id IN (761);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 590 WHERE material_id IN (761);
UPDATE `biz_sales_order_item` SET product_id = 590 WHERE product_id IN (761);
UPDATE `biz_work_order` SET product_id = 590 WHERE product_id IN (761);
UPDATE `biz_return_order_item` SET product_id = 590 WHERE product_id IN (761);
UPDATE `biz_mrp_production_suggestion` SET product_id = 590 WHERE product_id IN (761);
UPDATE `biz_process_route` SET product_id = 590 WHERE product_id IN (761);
UPDATE `biz_bom_item` SET material_id = 590 WHERE material_id IN (761);
UPDATE `biz_bom_item` SET product_id = 590 WHERE product_id IN (761);
UPDATE `biz_qc_task` SET material_id = 590 WHERE material_id IN (761);
UPDATE `biz_qc_task` SET product_id = 590 WHERE product_id IN (761);
UPDATE `biz_defect_record` SET material_id = 590 WHERE material_id IN (761);
UPDATE `biz_defect_record` SET product_id = 590 WHERE product_id IN (761);
UPDATE `biz_mrp_demand` SET material_id = 590 WHERE material_id IN (761);
UPDATE `biz_mrp_demand` SET product_id = 590 WHERE product_id IN (761);
UPDATE `biz_outsource_request` SET material_id = 590 WHERE material_id IN (761);
UPDATE `biz_outsource_request` SET product_id = 590 WHERE product_id IN (761);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (761) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 590
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 590 WHERE material_id IN (761)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 590) tmp);
DELETE FROM biz_inventory WHERE material_id IN (761);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (761);

-- ============================================================
-- 底座: 主ID=595(ZG0114) -> 正确编码=ZGTX400-0901-250-01DC
-- 合并: id=767(ZGTX400-0901-250-01DC)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'ZGTX400-0901-250-01DC' AND id != 595;
UPDATE biz_material SET material_no = 'ZGTX400-0901-250-01DC', deleted = 0, status = 'active' WHERE id = 595;

UPDATE `biz_inv_transaction` SET material_id = 595 WHERE material_id IN (767);
UPDATE `biz_inv_task_item` SET material_id = 595 WHERE material_id IN (767);
UPDATE `biz_inv_history_item` SET material_id = 595 WHERE material_id IN (767);
UPDATE `biz_inv_request_item` SET material_id = 595 WHERE material_id IN (767);
UPDATE `biz_inv_reservation` SET material_id = 595 WHERE material_id IN (767);
UPDATE `biz_inv_transfer_item` SET material_id = 595 WHERE material_id IN (767);
UPDATE `biz_inv_stocktaking_item` SET material_id = 595 WHERE material_id IN (767);
UPDATE `biz_purchase_request` SET material_id = 595 WHERE material_id IN (767);
UPDATE `biz_purchase_order_item` SET material_id = 595 WHERE material_id IN (767);
UPDATE `biz_purchase_receipt` SET material_id = 595 WHERE material_id IN (767);
UPDATE `biz_purchase_return_item` SET material_id = 595 WHERE material_id IN (767);
UPDATE `biz_wo_bom_snapshot` SET material_id = 595 WHERE material_id IN (767);
UPDATE `biz_plan_item_material` SET material_id = 595 WHERE material_id IN (767);
UPDATE `biz_outsource_delivery_item` SET material_id = 595 WHERE material_id IN (767);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 595 WHERE material_id IN (767);
UPDATE `biz_sales_order_item` SET product_id = 595 WHERE product_id IN (767);
UPDATE `biz_work_order` SET product_id = 595 WHERE product_id IN (767);
UPDATE `biz_return_order_item` SET product_id = 595 WHERE product_id IN (767);
UPDATE `biz_mrp_production_suggestion` SET product_id = 595 WHERE product_id IN (767);
UPDATE `biz_process_route` SET product_id = 595 WHERE product_id IN (767);
UPDATE `biz_bom_item` SET material_id = 595 WHERE material_id IN (767);
UPDATE `biz_bom_item` SET product_id = 595 WHERE product_id IN (767);
UPDATE `biz_qc_task` SET material_id = 595 WHERE material_id IN (767);
UPDATE `biz_qc_task` SET product_id = 595 WHERE product_id IN (767);
UPDATE `biz_defect_record` SET material_id = 595 WHERE material_id IN (767);
UPDATE `biz_defect_record` SET product_id = 595 WHERE product_id IN (767);
UPDATE `biz_mrp_demand` SET material_id = 595 WHERE material_id IN (767);
UPDATE `biz_mrp_demand` SET product_id = 595 WHERE product_id IN (767);
UPDATE `biz_outsource_request` SET material_id = 595 WHERE material_id IN (767);
UPDATE `biz_outsource_request` SET product_id = 595 WHERE product_id IN (767);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (767) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 595
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 595 WHERE material_id IN (767)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 595) tmp);
DELETE FROM biz_inventory WHERE material_id IN (767);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (767);

-- ============================================================
-- 支架: 主ID=596(ZG0115) -> 正确编码=ZGTX400-650-014
-- 合并: id=768(ZGTX400-650-014)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'ZGTX400-650-014' AND id != 596;
UPDATE biz_material SET material_no = 'ZGTX400-650-014', deleted = 0, status = 'active' WHERE id = 596;

UPDATE `biz_inv_transaction` SET material_id = 596 WHERE material_id IN (768);
UPDATE `biz_inv_task_item` SET material_id = 596 WHERE material_id IN (768);
UPDATE `biz_inv_history_item` SET material_id = 596 WHERE material_id IN (768);
UPDATE `biz_inv_request_item` SET material_id = 596 WHERE material_id IN (768);
UPDATE `biz_inv_reservation` SET material_id = 596 WHERE material_id IN (768);
UPDATE `biz_inv_transfer_item` SET material_id = 596 WHERE material_id IN (768);
UPDATE `biz_inv_stocktaking_item` SET material_id = 596 WHERE material_id IN (768);
UPDATE `biz_purchase_request` SET material_id = 596 WHERE material_id IN (768);
UPDATE `biz_purchase_order_item` SET material_id = 596 WHERE material_id IN (768);
UPDATE `biz_purchase_receipt` SET material_id = 596 WHERE material_id IN (768);
UPDATE `biz_purchase_return_item` SET material_id = 596 WHERE material_id IN (768);
UPDATE `biz_wo_bom_snapshot` SET material_id = 596 WHERE material_id IN (768);
UPDATE `biz_plan_item_material` SET material_id = 596 WHERE material_id IN (768);
UPDATE `biz_outsource_delivery_item` SET material_id = 596 WHERE material_id IN (768);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 596 WHERE material_id IN (768);
UPDATE `biz_sales_order_item` SET product_id = 596 WHERE product_id IN (768);
UPDATE `biz_work_order` SET product_id = 596 WHERE product_id IN (768);
UPDATE `biz_return_order_item` SET product_id = 596 WHERE product_id IN (768);
UPDATE `biz_mrp_production_suggestion` SET product_id = 596 WHERE product_id IN (768);
UPDATE `biz_process_route` SET product_id = 596 WHERE product_id IN (768);
UPDATE `biz_bom_item` SET material_id = 596 WHERE material_id IN (768);
UPDATE `biz_bom_item` SET product_id = 596 WHERE product_id IN (768);
UPDATE `biz_qc_task` SET material_id = 596 WHERE material_id IN (768);
UPDATE `biz_qc_task` SET product_id = 596 WHERE product_id IN (768);
UPDATE `biz_defect_record` SET material_id = 596 WHERE material_id IN (768);
UPDATE `biz_defect_record` SET product_id = 596 WHERE product_id IN (768);
UPDATE `biz_mrp_demand` SET material_id = 596 WHERE material_id IN (768);
UPDATE `biz_mrp_demand` SET product_id = 596 WHERE product_id IN (768);
UPDATE `biz_outsource_request` SET material_id = 596 WHERE material_id IN (768);
UPDATE `biz_outsource_request` SET product_id = 596 WHERE product_id IN (768);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (768) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 596
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 596 WHERE material_id IN (768)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 596) tmp);
DELETE FROM biz_inventory WHERE material_id IN (768);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (768);

-- ============================================================
-- 底座: 主ID=597(ZG0116) -> 正确编码=ZGB-100-0911-200-01ZV
-- 合并: id=762(ZGB-100-0911-200-01ZV)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'ZGB-100-0911-200-01ZV' AND id != 597;
UPDATE biz_material SET material_no = 'ZGB-100-0911-200-01ZV', deleted = 0, status = 'active' WHERE id = 597;

UPDATE `biz_inv_transaction` SET material_id = 597 WHERE material_id IN (762);
UPDATE `biz_inv_task_item` SET material_id = 597 WHERE material_id IN (762);
UPDATE `biz_inv_history_item` SET material_id = 597 WHERE material_id IN (762);
UPDATE `biz_inv_request_item` SET material_id = 597 WHERE material_id IN (762);
UPDATE `biz_inv_reservation` SET material_id = 597 WHERE material_id IN (762);
UPDATE `biz_inv_transfer_item` SET material_id = 597 WHERE material_id IN (762);
UPDATE `biz_inv_stocktaking_item` SET material_id = 597 WHERE material_id IN (762);
UPDATE `biz_purchase_request` SET material_id = 597 WHERE material_id IN (762);
UPDATE `biz_purchase_order_item` SET material_id = 597 WHERE material_id IN (762);
UPDATE `biz_purchase_receipt` SET material_id = 597 WHERE material_id IN (762);
UPDATE `biz_purchase_return_item` SET material_id = 597 WHERE material_id IN (762);
UPDATE `biz_wo_bom_snapshot` SET material_id = 597 WHERE material_id IN (762);
UPDATE `biz_plan_item_material` SET material_id = 597 WHERE material_id IN (762);
UPDATE `biz_outsource_delivery_item` SET material_id = 597 WHERE material_id IN (762);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 597 WHERE material_id IN (762);
UPDATE `biz_sales_order_item` SET product_id = 597 WHERE product_id IN (762);
UPDATE `biz_work_order` SET product_id = 597 WHERE product_id IN (762);
UPDATE `biz_return_order_item` SET product_id = 597 WHERE product_id IN (762);
UPDATE `biz_mrp_production_suggestion` SET product_id = 597 WHERE product_id IN (762);
UPDATE `biz_process_route` SET product_id = 597 WHERE product_id IN (762);
UPDATE `biz_bom_item` SET material_id = 597 WHERE material_id IN (762);
UPDATE `biz_bom_item` SET product_id = 597 WHERE product_id IN (762);
UPDATE `biz_qc_task` SET material_id = 597 WHERE material_id IN (762);
UPDATE `biz_qc_task` SET product_id = 597 WHERE product_id IN (762);
UPDATE `biz_defect_record` SET material_id = 597 WHERE material_id IN (762);
UPDATE `biz_defect_record` SET product_id = 597 WHERE product_id IN (762);
UPDATE `biz_mrp_demand` SET material_id = 597 WHERE material_id IN (762);
UPDATE `biz_mrp_demand` SET product_id = 597 WHERE product_id IN (762);
UPDATE `biz_outsource_request` SET material_id = 597 WHERE material_id IN (762);
UPDATE `biz_outsource_request` SET product_id = 597 WHERE product_id IN (762);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (762) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 597
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 597 WHERE material_id IN (762)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 597) tmp);
DELETE FROM biz_inventory WHERE material_id IN (762);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (762);

-- ============================================================
-- 轴承箱底座(毛坯): 主ID=598(ZG0117) -> 正确编码=ZST16.05A-1
-- 合并: id=769(ZST16.05A-1)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'ZST16.05A-1' AND id != 598;
UPDATE biz_material SET material_no = 'ZST16.05A-1', deleted = 0, status = 'active' WHERE id = 598;

UPDATE `biz_inv_transaction` SET material_id = 598 WHERE material_id IN (769);
UPDATE `biz_inv_task_item` SET material_id = 598 WHERE material_id IN (769);
UPDATE `biz_inv_history_item` SET material_id = 598 WHERE material_id IN (769);
UPDATE `biz_inv_request_item` SET material_id = 598 WHERE material_id IN (769);
UPDATE `biz_inv_reservation` SET material_id = 598 WHERE material_id IN (769);
UPDATE `biz_inv_transfer_item` SET material_id = 598 WHERE material_id IN (769);
UPDATE `biz_inv_stocktaking_item` SET material_id = 598 WHERE material_id IN (769);
UPDATE `biz_purchase_request` SET material_id = 598 WHERE material_id IN (769);
UPDATE `biz_purchase_order_item` SET material_id = 598 WHERE material_id IN (769);
UPDATE `biz_purchase_receipt` SET material_id = 598 WHERE material_id IN (769);
UPDATE `biz_purchase_return_item` SET material_id = 598 WHERE material_id IN (769);
UPDATE `biz_wo_bom_snapshot` SET material_id = 598 WHERE material_id IN (769);
UPDATE `biz_plan_item_material` SET material_id = 598 WHERE material_id IN (769);
UPDATE `biz_outsource_delivery_item` SET material_id = 598 WHERE material_id IN (769);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 598 WHERE material_id IN (769);
UPDATE `biz_sales_order_item` SET product_id = 598 WHERE product_id IN (769);
UPDATE `biz_work_order` SET product_id = 598 WHERE product_id IN (769);
UPDATE `biz_return_order_item` SET product_id = 598 WHERE product_id IN (769);
UPDATE `biz_mrp_production_suggestion` SET product_id = 598 WHERE product_id IN (769);
UPDATE `biz_process_route` SET product_id = 598 WHERE product_id IN (769);
UPDATE `biz_bom_item` SET material_id = 598 WHERE material_id IN (769);
UPDATE `biz_bom_item` SET product_id = 598 WHERE product_id IN (769);
UPDATE `biz_qc_task` SET material_id = 598 WHERE material_id IN (769);
UPDATE `biz_qc_task` SET product_id = 598 WHERE product_id IN (769);
UPDATE `biz_defect_record` SET material_id = 598 WHERE material_id IN (769);
UPDATE `biz_defect_record` SET product_id = 598 WHERE product_id IN (769);
UPDATE `biz_mrp_demand` SET material_id = 598 WHERE material_id IN (769);
UPDATE `biz_mrp_demand` SET product_id = 598 WHERE product_id IN (769);
UPDATE `biz_outsource_request` SET material_id = 598 WHERE material_id IN (769);
UPDATE `biz_outsource_request` SET product_id = 598 WHERE product_id IN (769);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (769) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 598
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 598 WHERE material_id IN (769)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 598) tmp);
DELETE FROM biz_inventory WHERE material_id IN (769);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (769);

-- ============================================================
-- 底座: 主ID=601(ZG0120) -> 正确编码=SZ-100-500-0911-Y315-01CR
-- 合并: id=773(SZ-100-500-0911-Y315-01CR)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'SZ-100-500-0911-Y315-01CR' AND id != 601;
UPDATE biz_material SET material_no = 'SZ-100-500-0911-Y315-01CR', deleted = 0, status = 'active' WHERE id = 601;

UPDATE `biz_inv_transaction` SET material_id = 601 WHERE material_id IN (773);
UPDATE `biz_inv_task_item` SET material_id = 601 WHERE material_id IN (773);
UPDATE `biz_inv_history_item` SET material_id = 601 WHERE material_id IN (773);
UPDATE `biz_inv_request_item` SET material_id = 601 WHERE material_id IN (773);
UPDATE `biz_inv_reservation` SET material_id = 601 WHERE material_id IN (773);
UPDATE `biz_inv_transfer_item` SET material_id = 601 WHERE material_id IN (773);
UPDATE `biz_inv_stocktaking_item` SET material_id = 601 WHERE material_id IN (773);
UPDATE `biz_purchase_request` SET material_id = 601 WHERE material_id IN (773);
UPDATE `biz_purchase_order_item` SET material_id = 601 WHERE material_id IN (773);
UPDATE `biz_purchase_receipt` SET material_id = 601 WHERE material_id IN (773);
UPDATE `biz_purchase_return_item` SET material_id = 601 WHERE material_id IN (773);
UPDATE `biz_wo_bom_snapshot` SET material_id = 601 WHERE material_id IN (773);
UPDATE `biz_plan_item_material` SET material_id = 601 WHERE material_id IN (773);
UPDATE `biz_outsource_delivery_item` SET material_id = 601 WHERE material_id IN (773);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 601 WHERE material_id IN (773);
UPDATE `biz_sales_order_item` SET product_id = 601 WHERE product_id IN (773);
UPDATE `biz_work_order` SET product_id = 601 WHERE product_id IN (773);
UPDATE `biz_return_order_item` SET product_id = 601 WHERE product_id IN (773);
UPDATE `biz_mrp_production_suggestion` SET product_id = 601 WHERE product_id IN (773);
UPDATE `biz_process_route` SET product_id = 601 WHERE product_id IN (773);
UPDATE `biz_bom_item` SET material_id = 601 WHERE material_id IN (773);
UPDATE `biz_bom_item` SET product_id = 601 WHERE product_id IN (773);
UPDATE `biz_qc_task` SET material_id = 601 WHERE material_id IN (773);
UPDATE `biz_qc_task` SET product_id = 601 WHERE product_id IN (773);
UPDATE `biz_defect_record` SET material_id = 601 WHERE material_id IN (773);
UPDATE `biz_defect_record` SET product_id = 601 WHERE product_id IN (773);
UPDATE `biz_mrp_demand` SET material_id = 601 WHERE material_id IN (773);
UPDATE `biz_mrp_demand` SET product_id = 601 WHERE product_id IN (773);
UPDATE `biz_outsource_request` SET material_id = 601 WHERE material_id IN (773);
UPDATE `biz_outsource_request` SET product_id = 601 WHERE product_id IN (773);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (773) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 601
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 601 WHERE material_id IN (773)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 601) tmp);
DELETE FROM biz_inventory WHERE material_id IN (773);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (773);

-- ============================================================
-- 底座: 主ID=603(ZG0122) -> 正确编码=K150-560TD-280DC-100-50
-- 合并: id=775(K150-560TD-280DC-100-50)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'K150-560TD-280DC-100-50' AND id != 603;
UPDATE biz_material SET material_no = 'K150-560TD-280DC-100-50', deleted = 0, status = 'active' WHERE id = 603;

UPDATE `biz_inv_transaction` SET material_id = 603 WHERE material_id IN (775);
UPDATE `biz_inv_task_item` SET material_id = 603 WHERE material_id IN (775);
UPDATE `biz_inv_history_item` SET material_id = 603 WHERE material_id IN (775);
UPDATE `biz_inv_request_item` SET material_id = 603 WHERE material_id IN (775);
UPDATE `biz_inv_reservation` SET material_id = 603 WHERE material_id IN (775);
UPDATE `biz_inv_transfer_item` SET material_id = 603 WHERE material_id IN (775);
UPDATE `biz_inv_stocktaking_item` SET material_id = 603 WHERE material_id IN (775);
UPDATE `biz_purchase_request` SET material_id = 603 WHERE material_id IN (775);
UPDATE `biz_purchase_order_item` SET material_id = 603 WHERE material_id IN (775);
UPDATE `biz_purchase_receipt` SET material_id = 603 WHERE material_id IN (775);
UPDATE `biz_purchase_return_item` SET material_id = 603 WHERE material_id IN (775);
UPDATE `biz_wo_bom_snapshot` SET material_id = 603 WHERE material_id IN (775);
UPDATE `biz_plan_item_material` SET material_id = 603 WHERE material_id IN (775);
UPDATE `biz_outsource_delivery_item` SET material_id = 603 WHERE material_id IN (775);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 603 WHERE material_id IN (775);
UPDATE `biz_sales_order_item` SET product_id = 603 WHERE product_id IN (775);
UPDATE `biz_work_order` SET product_id = 603 WHERE product_id IN (775);
UPDATE `biz_return_order_item` SET product_id = 603 WHERE product_id IN (775);
UPDATE `biz_mrp_production_suggestion` SET product_id = 603 WHERE product_id IN (775);
UPDATE `biz_process_route` SET product_id = 603 WHERE product_id IN (775);
UPDATE `biz_bom_item` SET material_id = 603 WHERE material_id IN (775);
UPDATE `biz_bom_item` SET product_id = 603 WHERE product_id IN (775);
UPDATE `biz_qc_task` SET material_id = 603 WHERE material_id IN (775);
UPDATE `biz_qc_task` SET product_id = 603 WHERE product_id IN (775);
UPDATE `biz_defect_record` SET material_id = 603 WHERE material_id IN (775);
UPDATE `biz_defect_record` SET product_id = 603 WHERE product_id IN (775);
UPDATE `biz_mrp_demand` SET material_id = 603 WHERE material_id IN (775);
UPDATE `biz_mrp_demand` SET product_id = 603 WHERE product_id IN (775);
UPDATE `biz_outsource_request` SET material_id = 603 WHERE material_id IN (775);
UPDATE `biz_outsource_request` SET product_id = 603 WHERE product_id IN (775);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (775) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 603
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 603 WHERE material_id IN (775)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 603) tmp);
DELETE FROM biz_inventory WHERE material_id IN (775);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (775);

-- ============================================================
-- 泵底座: 主ID=607(ZG0126) -> 正确编码=JG-KREBS-251044
-- 合并: id=780(JG-KREBS-251044)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'JG-KREBS-251044' AND id != 607;
UPDATE biz_material SET material_no = 'JG-KREBS-251044', deleted = 0, status = 'active' WHERE id = 607;

UPDATE `biz_inv_transaction` SET material_id = 607 WHERE material_id IN (780);
UPDATE `biz_inv_task_item` SET material_id = 607 WHERE material_id IN (780);
UPDATE `biz_inv_history_item` SET material_id = 607 WHERE material_id IN (780);
UPDATE `biz_inv_request_item` SET material_id = 607 WHERE material_id IN (780);
UPDATE `biz_inv_reservation` SET material_id = 607 WHERE material_id IN (780);
UPDATE `biz_inv_transfer_item` SET material_id = 607 WHERE material_id IN (780);
UPDATE `biz_inv_stocktaking_item` SET material_id = 607 WHERE material_id IN (780);
UPDATE `biz_purchase_request` SET material_id = 607 WHERE material_id IN (780);
UPDATE `biz_purchase_order_item` SET material_id = 607 WHERE material_id IN (780);
UPDATE `biz_purchase_receipt` SET material_id = 607 WHERE material_id IN (780);
UPDATE `biz_purchase_return_item` SET material_id = 607 WHERE material_id IN (780);
UPDATE `biz_wo_bom_snapshot` SET material_id = 607 WHERE material_id IN (780);
UPDATE `biz_plan_item_material` SET material_id = 607 WHERE material_id IN (780);
UPDATE `biz_outsource_delivery_item` SET material_id = 607 WHERE material_id IN (780);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 607 WHERE material_id IN (780);
UPDATE `biz_sales_order_item` SET product_id = 607 WHERE product_id IN (780);
UPDATE `biz_work_order` SET product_id = 607 WHERE product_id IN (780);
UPDATE `biz_return_order_item` SET product_id = 607 WHERE product_id IN (780);
UPDATE `biz_mrp_production_suggestion` SET product_id = 607 WHERE product_id IN (780);
UPDATE `biz_process_route` SET product_id = 607 WHERE product_id IN (780);
UPDATE `biz_bom_item` SET material_id = 607 WHERE material_id IN (780);
UPDATE `biz_bom_item` SET product_id = 607 WHERE product_id IN (780);
UPDATE `biz_qc_task` SET material_id = 607 WHERE material_id IN (780);
UPDATE `biz_qc_task` SET product_id = 607 WHERE product_id IN (780);
UPDATE `biz_defect_record` SET material_id = 607 WHERE material_id IN (780);
UPDATE `biz_defect_record` SET product_id = 607 WHERE product_id IN (780);
UPDATE `biz_mrp_demand` SET material_id = 607 WHERE material_id IN (780);
UPDATE `biz_mrp_demand` SET product_id = 607 WHERE product_id IN (780);
UPDATE `biz_outsource_request` SET material_id = 607 WHERE material_id IN (780);
UPDATE `biz_outsource_request` SET product_id = 607 WHERE product_id IN (780);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (780) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 607
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 607 WHERE material_id IN (780)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 607) tmp);
DELETE FROM biz_inventory WHERE material_id IN (780);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (780);

-- ============================================================
-- 底座: 主ID=609(ZG0128) -> 正确编码=TZJF-0911-280-02CL
-- 合并: id=781(TZJF-0911-280-02CL)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJF-0911-280-02CL' AND id != 609;
UPDATE biz_material SET material_no = 'TZJF-0911-280-02CL', deleted = 0, status = 'active' WHERE id = 609;

UPDATE `biz_inv_transaction` SET material_id = 609 WHERE material_id IN (781);
UPDATE `biz_inv_task_item` SET material_id = 609 WHERE material_id IN (781);
UPDATE `biz_inv_history_item` SET material_id = 609 WHERE material_id IN (781);
UPDATE `biz_inv_request_item` SET material_id = 609 WHERE material_id IN (781);
UPDATE `biz_inv_reservation` SET material_id = 609 WHERE material_id IN (781);
UPDATE `biz_inv_transfer_item` SET material_id = 609 WHERE material_id IN (781);
UPDATE `biz_inv_stocktaking_item` SET material_id = 609 WHERE material_id IN (781);
UPDATE `biz_purchase_request` SET material_id = 609 WHERE material_id IN (781);
UPDATE `biz_purchase_order_item` SET material_id = 609 WHERE material_id IN (781);
UPDATE `biz_purchase_receipt` SET material_id = 609 WHERE material_id IN (781);
UPDATE `biz_purchase_return_item` SET material_id = 609 WHERE material_id IN (781);
UPDATE `biz_wo_bom_snapshot` SET material_id = 609 WHERE material_id IN (781);
UPDATE `biz_plan_item_material` SET material_id = 609 WHERE material_id IN (781);
UPDATE `biz_outsource_delivery_item` SET material_id = 609 WHERE material_id IN (781);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 609 WHERE material_id IN (781);
UPDATE `biz_sales_order_item` SET product_id = 609 WHERE product_id IN (781);
UPDATE `biz_work_order` SET product_id = 609 WHERE product_id IN (781);
UPDATE `biz_return_order_item` SET product_id = 609 WHERE product_id IN (781);
UPDATE `biz_mrp_production_suggestion` SET product_id = 609 WHERE product_id IN (781);
UPDATE `biz_process_route` SET product_id = 609 WHERE product_id IN (781);
UPDATE `biz_bom_item` SET material_id = 609 WHERE material_id IN (781);
UPDATE `biz_bom_item` SET product_id = 609 WHERE product_id IN (781);
UPDATE `biz_qc_task` SET material_id = 609 WHERE material_id IN (781);
UPDATE `biz_qc_task` SET product_id = 609 WHERE product_id IN (781);
UPDATE `biz_defect_record` SET material_id = 609 WHERE material_id IN (781);
UPDATE `biz_defect_record` SET product_id = 609 WHERE product_id IN (781);
UPDATE `biz_mrp_demand` SET material_id = 609 WHERE material_id IN (781);
UPDATE `biz_mrp_demand` SET product_id = 609 WHERE product_id IN (781);
UPDATE `biz_outsource_request` SET material_id = 609 WHERE material_id IN (781);
UPDATE `biz_outsource_request` SET product_id = 609 WHERE product_id IN (781);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (781) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 609
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 609 WHERE material_id IN (781)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 609) tmp);
DELETE FROM biz_inventory WHERE material_id IN (781);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (781);

-- ============================================================
-- 底座: 主ID=610(ZG0129) -> 正确编码=TZJE-0911-210-02CL
-- 合并: id=782(TZJE-0911-210-02CL)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJE-0911-210-02CL' AND id != 610;
UPDATE biz_material SET material_no = 'TZJE-0911-210-02CL', deleted = 0, status = 'active' WHERE id = 610;

UPDATE `biz_inv_transaction` SET material_id = 610 WHERE material_id IN (782);
UPDATE `biz_inv_task_item` SET material_id = 610 WHERE material_id IN (782);
UPDATE `biz_inv_history_item` SET material_id = 610 WHERE material_id IN (782);
UPDATE `biz_inv_request_item` SET material_id = 610 WHERE material_id IN (782);
UPDATE `biz_inv_reservation` SET material_id = 610 WHERE material_id IN (782);
UPDATE `biz_inv_transfer_item` SET material_id = 610 WHERE material_id IN (782);
UPDATE `biz_inv_stocktaking_item` SET material_id = 610 WHERE material_id IN (782);
UPDATE `biz_purchase_request` SET material_id = 610 WHERE material_id IN (782);
UPDATE `biz_purchase_order_item` SET material_id = 610 WHERE material_id IN (782);
UPDATE `biz_purchase_receipt` SET material_id = 610 WHERE material_id IN (782);
UPDATE `biz_purchase_return_item` SET material_id = 610 WHERE material_id IN (782);
UPDATE `biz_wo_bom_snapshot` SET material_id = 610 WHERE material_id IN (782);
UPDATE `biz_plan_item_material` SET material_id = 610 WHERE material_id IN (782);
UPDATE `biz_outsource_delivery_item` SET material_id = 610 WHERE material_id IN (782);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 610 WHERE material_id IN (782);
UPDATE `biz_sales_order_item` SET product_id = 610 WHERE product_id IN (782);
UPDATE `biz_work_order` SET product_id = 610 WHERE product_id IN (782);
UPDATE `biz_return_order_item` SET product_id = 610 WHERE product_id IN (782);
UPDATE `biz_mrp_production_suggestion` SET product_id = 610 WHERE product_id IN (782);
UPDATE `biz_process_route` SET product_id = 610 WHERE product_id IN (782);
UPDATE `biz_bom_item` SET material_id = 610 WHERE material_id IN (782);
UPDATE `biz_bom_item` SET product_id = 610 WHERE product_id IN (782);
UPDATE `biz_qc_task` SET material_id = 610 WHERE material_id IN (782);
UPDATE `biz_qc_task` SET product_id = 610 WHERE product_id IN (782);
UPDATE `biz_defect_record` SET material_id = 610 WHERE material_id IN (782);
UPDATE `biz_defect_record` SET product_id = 610 WHERE product_id IN (782);
UPDATE `biz_mrp_demand` SET material_id = 610 WHERE material_id IN (782);
UPDATE `biz_mrp_demand` SET product_id = 610 WHERE product_id IN (782);
UPDATE `biz_outsource_request` SET material_id = 610 WHERE material_id IN (782);
UPDATE `biz_outsource_request` SET product_id = 610 WHERE product_id IN (782);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (782) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 610
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 610 WHERE material_id IN (782)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 610) tmp);
DELETE FROM biz_inventory WHERE material_id IN (782);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (782);

-- ============================================================
-- 底座: 主ID=611(ZG0130) -> 正确编码=TZJD-0911-240-02CL
-- 合并: id=783(TZJD-0911-240-02CL)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJD-0911-240-02CL' AND id != 611;
UPDATE biz_material SET material_no = 'TZJD-0911-240-02CL', deleted = 0, status = 'active' WHERE id = 611;

UPDATE `biz_inv_transaction` SET material_id = 611 WHERE material_id IN (783);
UPDATE `biz_inv_task_item` SET material_id = 611 WHERE material_id IN (783);
UPDATE `biz_inv_history_item` SET material_id = 611 WHERE material_id IN (783);
UPDATE `biz_inv_request_item` SET material_id = 611 WHERE material_id IN (783);
UPDATE `biz_inv_reservation` SET material_id = 611 WHERE material_id IN (783);
UPDATE `biz_inv_transfer_item` SET material_id = 611 WHERE material_id IN (783);
UPDATE `biz_inv_stocktaking_item` SET material_id = 611 WHERE material_id IN (783);
UPDATE `biz_purchase_request` SET material_id = 611 WHERE material_id IN (783);
UPDATE `biz_purchase_order_item` SET material_id = 611 WHERE material_id IN (783);
UPDATE `biz_purchase_receipt` SET material_id = 611 WHERE material_id IN (783);
UPDATE `biz_purchase_return_item` SET material_id = 611 WHERE material_id IN (783);
UPDATE `biz_wo_bom_snapshot` SET material_id = 611 WHERE material_id IN (783);
UPDATE `biz_plan_item_material` SET material_id = 611 WHERE material_id IN (783);
UPDATE `biz_outsource_delivery_item` SET material_id = 611 WHERE material_id IN (783);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 611 WHERE material_id IN (783);
UPDATE `biz_sales_order_item` SET product_id = 611 WHERE product_id IN (783);
UPDATE `biz_work_order` SET product_id = 611 WHERE product_id IN (783);
UPDATE `biz_return_order_item` SET product_id = 611 WHERE product_id IN (783);
UPDATE `biz_mrp_production_suggestion` SET product_id = 611 WHERE product_id IN (783);
UPDATE `biz_process_route` SET product_id = 611 WHERE product_id IN (783);
UPDATE `biz_bom_item` SET material_id = 611 WHERE material_id IN (783);
UPDATE `biz_bom_item` SET product_id = 611 WHERE product_id IN (783);
UPDATE `biz_qc_task` SET material_id = 611 WHERE material_id IN (783);
UPDATE `biz_qc_task` SET product_id = 611 WHERE product_id IN (783);
UPDATE `biz_defect_record` SET material_id = 611 WHERE material_id IN (783);
UPDATE `biz_defect_record` SET product_id = 611 WHERE product_id IN (783);
UPDATE `biz_mrp_demand` SET material_id = 611 WHERE material_id IN (783);
UPDATE `biz_mrp_demand` SET product_id = 611 WHERE product_id IN (783);
UPDATE `biz_outsource_request` SET material_id = 611 WHERE material_id IN (783);
UPDATE `biz_outsource_request` SET product_id = 611 WHERE product_id IN (783);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (783) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 611
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 611 WHERE material_id IN (783)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 611) tmp);
DELETE FROM biz_inventory WHERE material_id IN (783);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (783);

-- ============================================================
-- 底座: 主ID=612(ZG0131) -> 正确编码=TZJE-0911-210-03CL
-- 合并: id=784(TZJE-0911-210-03CL)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJE-0911-210-03CL' AND id != 612;
UPDATE biz_material SET material_no = 'TZJE-0911-210-03CL', deleted = 0, status = 'active' WHERE id = 612;

UPDATE `biz_inv_transaction` SET material_id = 612 WHERE material_id IN (784);
UPDATE `biz_inv_task_item` SET material_id = 612 WHERE material_id IN (784);
UPDATE `biz_inv_history_item` SET material_id = 612 WHERE material_id IN (784);
UPDATE `biz_inv_request_item` SET material_id = 612 WHERE material_id IN (784);
UPDATE `biz_inv_reservation` SET material_id = 612 WHERE material_id IN (784);
UPDATE `biz_inv_transfer_item` SET material_id = 612 WHERE material_id IN (784);
UPDATE `biz_inv_stocktaking_item` SET material_id = 612 WHERE material_id IN (784);
UPDATE `biz_purchase_request` SET material_id = 612 WHERE material_id IN (784);
UPDATE `biz_purchase_order_item` SET material_id = 612 WHERE material_id IN (784);
UPDATE `biz_purchase_receipt` SET material_id = 612 WHERE material_id IN (784);
UPDATE `biz_purchase_return_item` SET material_id = 612 WHERE material_id IN (784);
UPDATE `biz_wo_bom_snapshot` SET material_id = 612 WHERE material_id IN (784);
UPDATE `biz_plan_item_material` SET material_id = 612 WHERE material_id IN (784);
UPDATE `biz_outsource_delivery_item` SET material_id = 612 WHERE material_id IN (784);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 612 WHERE material_id IN (784);
UPDATE `biz_sales_order_item` SET product_id = 612 WHERE product_id IN (784);
UPDATE `biz_work_order` SET product_id = 612 WHERE product_id IN (784);
UPDATE `biz_return_order_item` SET product_id = 612 WHERE product_id IN (784);
UPDATE `biz_mrp_production_suggestion` SET product_id = 612 WHERE product_id IN (784);
UPDATE `biz_process_route` SET product_id = 612 WHERE product_id IN (784);
UPDATE `biz_bom_item` SET material_id = 612 WHERE material_id IN (784);
UPDATE `biz_bom_item` SET product_id = 612 WHERE product_id IN (784);
UPDATE `biz_qc_task` SET material_id = 612 WHERE material_id IN (784);
UPDATE `biz_qc_task` SET product_id = 612 WHERE product_id IN (784);
UPDATE `biz_defect_record` SET material_id = 612 WHERE material_id IN (784);
UPDATE `biz_defect_record` SET product_id = 612 WHERE product_id IN (784);
UPDATE `biz_mrp_demand` SET material_id = 612 WHERE material_id IN (784);
UPDATE `biz_mrp_demand` SET product_id = 612 WHERE product_id IN (784);
UPDATE `biz_outsource_request` SET material_id = 612 WHERE material_id IN (784);
UPDATE `biz_outsource_request` SET product_id = 612 WHERE product_id IN (784);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (784) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 612
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 612 WHERE material_id IN (784)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 612) tmp);
DELETE FROM biz_inventory WHERE material_id IN (784);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (784);

-- ============================================================
-- 底座: 主ID=613(ZG0132) -> 正确编码=TZJD-0911-190-03CL
-- 合并: id=785(TZJD-0911-190-03CL)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJD-0911-190-03CL' AND id != 613;
UPDATE biz_material SET material_no = 'TZJD-0911-190-03CL', deleted = 0, status = 'active' WHERE id = 613;

UPDATE `biz_inv_transaction` SET material_id = 613 WHERE material_id IN (785);
UPDATE `biz_inv_task_item` SET material_id = 613 WHERE material_id IN (785);
UPDATE `biz_inv_history_item` SET material_id = 613 WHERE material_id IN (785);
UPDATE `biz_inv_request_item` SET material_id = 613 WHERE material_id IN (785);
UPDATE `biz_inv_reservation` SET material_id = 613 WHERE material_id IN (785);
UPDATE `biz_inv_transfer_item` SET material_id = 613 WHERE material_id IN (785);
UPDATE `biz_inv_stocktaking_item` SET material_id = 613 WHERE material_id IN (785);
UPDATE `biz_purchase_request` SET material_id = 613 WHERE material_id IN (785);
UPDATE `biz_purchase_order_item` SET material_id = 613 WHERE material_id IN (785);
UPDATE `biz_purchase_receipt` SET material_id = 613 WHERE material_id IN (785);
UPDATE `biz_purchase_return_item` SET material_id = 613 WHERE material_id IN (785);
UPDATE `biz_wo_bom_snapshot` SET material_id = 613 WHERE material_id IN (785);
UPDATE `biz_plan_item_material` SET material_id = 613 WHERE material_id IN (785);
UPDATE `biz_outsource_delivery_item` SET material_id = 613 WHERE material_id IN (785);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 613 WHERE material_id IN (785);
UPDATE `biz_sales_order_item` SET product_id = 613 WHERE product_id IN (785);
UPDATE `biz_work_order` SET product_id = 613 WHERE product_id IN (785);
UPDATE `biz_return_order_item` SET product_id = 613 WHERE product_id IN (785);
UPDATE `biz_mrp_production_suggestion` SET product_id = 613 WHERE product_id IN (785);
UPDATE `biz_process_route` SET product_id = 613 WHERE product_id IN (785);
UPDATE `biz_bom_item` SET material_id = 613 WHERE material_id IN (785);
UPDATE `biz_bom_item` SET product_id = 613 WHERE product_id IN (785);
UPDATE `biz_qc_task` SET material_id = 613 WHERE material_id IN (785);
UPDATE `biz_qc_task` SET product_id = 613 WHERE product_id IN (785);
UPDATE `biz_defect_record` SET material_id = 613 WHERE material_id IN (785);
UPDATE `biz_defect_record` SET product_id = 613 WHERE product_id IN (785);
UPDATE `biz_mrp_demand` SET material_id = 613 WHERE material_id IN (785);
UPDATE `biz_mrp_demand` SET product_id = 613 WHERE product_id IN (785);
UPDATE `biz_outsource_request` SET material_id = 613 WHERE material_id IN (785);
UPDATE `biz_outsource_request` SET product_id = 613 WHERE product_id IN (785);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (785) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 613
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 613 WHERE material_id IN (785)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 613) tmp);
DELETE FROM biz_inventory WHERE material_id IN (785);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (785);

-- ============================================================
-- 底座: 主ID=614(ZG0133) -> 正确编码=TZJD-0911-190-03CR
-- 合并: id=786(TZJD-0911-190-03CR)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJD-0911-190-03CR' AND id != 614;
UPDATE biz_material SET material_no = 'TZJD-0911-190-03CR', deleted = 0, status = 'active' WHERE id = 614;

UPDATE `biz_inv_transaction` SET material_id = 614 WHERE material_id IN (786);
UPDATE `biz_inv_task_item` SET material_id = 614 WHERE material_id IN (786);
UPDATE `biz_inv_history_item` SET material_id = 614 WHERE material_id IN (786);
UPDATE `biz_inv_request_item` SET material_id = 614 WHERE material_id IN (786);
UPDATE `biz_inv_reservation` SET material_id = 614 WHERE material_id IN (786);
UPDATE `biz_inv_transfer_item` SET material_id = 614 WHERE material_id IN (786);
UPDATE `biz_inv_stocktaking_item` SET material_id = 614 WHERE material_id IN (786);
UPDATE `biz_purchase_request` SET material_id = 614 WHERE material_id IN (786);
UPDATE `biz_purchase_order_item` SET material_id = 614 WHERE material_id IN (786);
UPDATE `biz_purchase_receipt` SET material_id = 614 WHERE material_id IN (786);
UPDATE `biz_purchase_return_item` SET material_id = 614 WHERE material_id IN (786);
UPDATE `biz_wo_bom_snapshot` SET material_id = 614 WHERE material_id IN (786);
UPDATE `biz_plan_item_material` SET material_id = 614 WHERE material_id IN (786);
UPDATE `biz_outsource_delivery_item` SET material_id = 614 WHERE material_id IN (786);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 614 WHERE material_id IN (786);
UPDATE `biz_sales_order_item` SET product_id = 614 WHERE product_id IN (786);
UPDATE `biz_work_order` SET product_id = 614 WHERE product_id IN (786);
UPDATE `biz_return_order_item` SET product_id = 614 WHERE product_id IN (786);
UPDATE `biz_mrp_production_suggestion` SET product_id = 614 WHERE product_id IN (786);
UPDATE `biz_process_route` SET product_id = 614 WHERE product_id IN (786);
UPDATE `biz_bom_item` SET material_id = 614 WHERE material_id IN (786);
UPDATE `biz_bom_item` SET product_id = 614 WHERE product_id IN (786);
UPDATE `biz_qc_task` SET material_id = 614 WHERE material_id IN (786);
UPDATE `biz_qc_task` SET product_id = 614 WHERE product_id IN (786);
UPDATE `biz_defect_record` SET material_id = 614 WHERE material_id IN (786);
UPDATE `biz_defect_record` SET product_id = 614 WHERE product_id IN (786);
UPDATE `biz_mrp_demand` SET material_id = 614 WHERE material_id IN (786);
UPDATE `biz_mrp_demand` SET product_id = 614 WHERE product_id IN (786);
UPDATE `biz_outsource_request` SET material_id = 614 WHERE material_id IN (786);
UPDATE `biz_outsource_request` SET product_id = 614 WHERE product_id IN (786);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (786) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 614
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 614 WHERE material_id IN (786)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 614) tmp);
DELETE FROM biz_inventory WHERE material_id IN (786);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (786);

-- ============================================================
-- 底座: 主ID=616(ZG0135) -> 正确编码=TZJD-0911-240-02CLA
-- 合并: id=788(TZJD-0911-240-02CLA)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TZJD-0911-240-02CLA' AND id != 616;
UPDATE biz_material SET material_no = 'TZJD-0911-240-02CLA', deleted = 0, status = 'active' WHERE id = 616;

UPDATE `biz_inv_transaction` SET material_id = 616 WHERE material_id IN (788);
UPDATE `biz_inv_task_item` SET material_id = 616 WHERE material_id IN (788);
UPDATE `biz_inv_history_item` SET material_id = 616 WHERE material_id IN (788);
UPDATE `biz_inv_request_item` SET material_id = 616 WHERE material_id IN (788);
UPDATE `biz_inv_reservation` SET material_id = 616 WHERE material_id IN (788);
UPDATE `biz_inv_transfer_item` SET material_id = 616 WHERE material_id IN (788);
UPDATE `biz_inv_stocktaking_item` SET material_id = 616 WHERE material_id IN (788);
UPDATE `biz_purchase_request` SET material_id = 616 WHERE material_id IN (788);
UPDATE `biz_purchase_order_item` SET material_id = 616 WHERE material_id IN (788);
UPDATE `biz_purchase_receipt` SET material_id = 616 WHERE material_id IN (788);
UPDATE `biz_purchase_return_item` SET material_id = 616 WHERE material_id IN (788);
UPDATE `biz_wo_bom_snapshot` SET material_id = 616 WHERE material_id IN (788);
UPDATE `biz_plan_item_material` SET material_id = 616 WHERE material_id IN (788);
UPDATE `biz_outsource_delivery_item` SET material_id = 616 WHERE material_id IN (788);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 616 WHERE material_id IN (788);
UPDATE `biz_sales_order_item` SET product_id = 616 WHERE product_id IN (788);
UPDATE `biz_work_order` SET product_id = 616 WHERE product_id IN (788);
UPDATE `biz_return_order_item` SET product_id = 616 WHERE product_id IN (788);
UPDATE `biz_mrp_production_suggestion` SET product_id = 616 WHERE product_id IN (788);
UPDATE `biz_process_route` SET product_id = 616 WHERE product_id IN (788);
UPDATE `biz_bom_item` SET material_id = 616 WHERE material_id IN (788);
UPDATE `biz_bom_item` SET product_id = 616 WHERE product_id IN (788);
UPDATE `biz_qc_task` SET material_id = 616 WHERE material_id IN (788);
UPDATE `biz_qc_task` SET product_id = 616 WHERE product_id IN (788);
UPDATE `biz_defect_record` SET material_id = 616 WHERE material_id IN (788);
UPDATE `biz_defect_record` SET product_id = 616 WHERE product_id IN (788);
UPDATE `biz_mrp_demand` SET material_id = 616 WHERE material_id IN (788);
UPDATE `biz_mrp_demand` SET product_id = 616 WHERE product_id IN (788);
UPDATE `biz_outsource_request` SET material_id = 616 WHERE material_id IN (788);
UPDATE `biz_outsource_request` SET product_id = 616 WHERE product_id IN (788);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (788) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 616
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 616 WHERE material_id IN (788)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 616) tmp);
DELETE FROM biz_inventory WHERE material_id IN (788);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (788);

-- ============================================================
-- 支架: 主ID=617(ZG0136) -> 正确编码=TD-16/14TU-0108HJ
-- 合并: id=790(TD-16/14TU-0108HJ)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'TD-16/14TU-0108HJ' AND id != 617;
UPDATE biz_material SET material_no = 'TD-16/14TU-0108HJ', deleted = 0, status = 'active' WHERE id = 617;

UPDATE `biz_inv_transaction` SET material_id = 617 WHERE material_id IN (790);
UPDATE `biz_inv_task_item` SET material_id = 617 WHERE material_id IN (790);
UPDATE `biz_inv_history_item` SET material_id = 617 WHERE material_id IN (790);
UPDATE `biz_inv_request_item` SET material_id = 617 WHERE material_id IN (790);
UPDATE `biz_inv_reservation` SET material_id = 617 WHERE material_id IN (790);
UPDATE `biz_inv_transfer_item` SET material_id = 617 WHERE material_id IN (790);
UPDATE `biz_inv_stocktaking_item` SET material_id = 617 WHERE material_id IN (790);
UPDATE `biz_purchase_request` SET material_id = 617 WHERE material_id IN (790);
UPDATE `biz_purchase_order_item` SET material_id = 617 WHERE material_id IN (790);
UPDATE `biz_purchase_receipt` SET material_id = 617 WHERE material_id IN (790);
UPDATE `biz_purchase_return_item` SET material_id = 617 WHERE material_id IN (790);
UPDATE `biz_wo_bom_snapshot` SET material_id = 617 WHERE material_id IN (790);
UPDATE `biz_plan_item_material` SET material_id = 617 WHERE material_id IN (790);
UPDATE `biz_outsource_delivery_item` SET material_id = 617 WHERE material_id IN (790);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 617 WHERE material_id IN (790);
UPDATE `biz_sales_order_item` SET product_id = 617 WHERE product_id IN (790);
UPDATE `biz_work_order` SET product_id = 617 WHERE product_id IN (790);
UPDATE `biz_return_order_item` SET product_id = 617 WHERE product_id IN (790);
UPDATE `biz_mrp_production_suggestion` SET product_id = 617 WHERE product_id IN (790);
UPDATE `biz_process_route` SET product_id = 617 WHERE product_id IN (790);
UPDATE `biz_bom_item` SET material_id = 617 WHERE material_id IN (790);
UPDATE `biz_bom_item` SET product_id = 617 WHERE product_id IN (790);
UPDATE `biz_qc_task` SET material_id = 617 WHERE material_id IN (790);
UPDATE `biz_qc_task` SET product_id = 617 WHERE product_id IN (790);
UPDATE `biz_defect_record` SET material_id = 617 WHERE material_id IN (790);
UPDATE `biz_defect_record` SET product_id = 617 WHERE product_id IN (790);
UPDATE `biz_mrp_demand` SET material_id = 617 WHERE material_id IN (790);
UPDATE `biz_mrp_demand` SET product_id = 617 WHERE product_id IN (790);
UPDATE `biz_outsource_request` SET material_id = 617 WHERE material_id IN (790);
UPDATE `biz_outsource_request` SET product_id = 617 WHERE product_id IN (790);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (790) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 617
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 617 WHERE material_id IN (790)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 617) tmp);
DELETE FROM biz_inventory WHERE material_id IN (790);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (790);

-- ============================================================
-- 底座: 主ID=618(ZG0137) -> 正确编码=ZGB-300-0901HJ
-- 合并: id=791(ZGB-300-0901HJ)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'ZGB-300-0901HJ' AND id != 618;
UPDATE biz_material SET material_no = 'ZGB-300-0901HJ', deleted = 0, status = 'active' WHERE id = 618;

UPDATE `biz_inv_transaction` SET material_id = 618 WHERE material_id IN (791);
UPDATE `biz_inv_task_item` SET material_id = 618 WHERE material_id IN (791);
UPDATE `biz_inv_history_item` SET material_id = 618 WHERE material_id IN (791);
UPDATE `biz_inv_request_item` SET material_id = 618 WHERE material_id IN (791);
UPDATE `biz_inv_reservation` SET material_id = 618 WHERE material_id IN (791);
UPDATE `biz_inv_transfer_item` SET material_id = 618 WHERE material_id IN (791);
UPDATE `biz_inv_stocktaking_item` SET material_id = 618 WHERE material_id IN (791);
UPDATE `biz_purchase_request` SET material_id = 618 WHERE material_id IN (791);
UPDATE `biz_purchase_order_item` SET material_id = 618 WHERE material_id IN (791);
UPDATE `biz_purchase_receipt` SET material_id = 618 WHERE material_id IN (791);
UPDATE `biz_purchase_return_item` SET material_id = 618 WHERE material_id IN (791);
UPDATE `biz_wo_bom_snapshot` SET material_id = 618 WHERE material_id IN (791);
UPDATE `biz_plan_item_material` SET material_id = 618 WHERE material_id IN (791);
UPDATE `biz_outsource_delivery_item` SET material_id = 618 WHERE material_id IN (791);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 618 WHERE material_id IN (791);
UPDATE `biz_sales_order_item` SET product_id = 618 WHERE product_id IN (791);
UPDATE `biz_work_order` SET product_id = 618 WHERE product_id IN (791);
UPDATE `biz_return_order_item` SET product_id = 618 WHERE product_id IN (791);
UPDATE `biz_mrp_production_suggestion` SET product_id = 618 WHERE product_id IN (791);
UPDATE `biz_process_route` SET product_id = 618 WHERE product_id IN (791);
UPDATE `biz_bom_item` SET material_id = 618 WHERE material_id IN (791);
UPDATE `biz_bom_item` SET product_id = 618 WHERE product_id IN (791);
UPDATE `biz_qc_task` SET material_id = 618 WHERE material_id IN (791);
UPDATE `biz_qc_task` SET product_id = 618 WHERE product_id IN (791);
UPDATE `biz_defect_record` SET material_id = 618 WHERE material_id IN (791);
UPDATE `biz_defect_record` SET product_id = 618 WHERE product_id IN (791);
UPDATE `biz_mrp_demand` SET material_id = 618 WHERE material_id IN (791);
UPDATE `biz_mrp_demand` SET product_id = 618 WHERE product_id IN (791);
UPDATE `biz_outsource_request` SET material_id = 618 WHERE material_id IN (791);
UPDATE `biz_outsource_request` SET product_id = 618 WHERE product_id IN (791);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (791) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 618
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 618 WHERE material_id IN (791)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 618) tmp);
DELETE FROM biz_inventory WHERE material_id IN (791);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (791);

-- ============================================================
-- 电机底座: 主ID=619(ZG0138) -> 正确编码=DJDZ-Y450-200-450JSJ
-- 合并: id=792(DJDZ-Y450-200-450JSJ)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'DJDZ-Y450-200-450JSJ' AND id != 619;
UPDATE biz_material SET material_no = 'DJDZ-Y450-200-450JSJ', deleted = 0, status = 'active' WHERE id = 619;

UPDATE `biz_inv_transaction` SET material_id = 619 WHERE material_id IN (792);
UPDATE `biz_inv_task_item` SET material_id = 619 WHERE material_id IN (792);
UPDATE `biz_inv_history_item` SET material_id = 619 WHERE material_id IN (792);
UPDATE `biz_inv_request_item` SET material_id = 619 WHERE material_id IN (792);
UPDATE `biz_inv_reservation` SET material_id = 619 WHERE material_id IN (792);
UPDATE `biz_inv_transfer_item` SET material_id = 619 WHERE material_id IN (792);
UPDATE `biz_inv_stocktaking_item` SET material_id = 619 WHERE material_id IN (792);
UPDATE `biz_purchase_request` SET material_id = 619 WHERE material_id IN (792);
UPDATE `biz_purchase_order_item` SET material_id = 619 WHERE material_id IN (792);
UPDATE `biz_purchase_receipt` SET material_id = 619 WHERE material_id IN (792);
UPDATE `biz_purchase_return_item` SET material_id = 619 WHERE material_id IN (792);
UPDATE `biz_wo_bom_snapshot` SET material_id = 619 WHERE material_id IN (792);
UPDATE `biz_plan_item_material` SET material_id = 619 WHERE material_id IN (792);
UPDATE `biz_outsource_delivery_item` SET material_id = 619 WHERE material_id IN (792);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 619 WHERE material_id IN (792);
UPDATE `biz_sales_order_item` SET product_id = 619 WHERE product_id IN (792);
UPDATE `biz_work_order` SET product_id = 619 WHERE product_id IN (792);
UPDATE `biz_return_order_item` SET product_id = 619 WHERE product_id IN (792);
UPDATE `biz_mrp_production_suggestion` SET product_id = 619 WHERE product_id IN (792);
UPDATE `biz_process_route` SET product_id = 619 WHERE product_id IN (792);
UPDATE `biz_bom_item` SET material_id = 619 WHERE material_id IN (792);
UPDATE `biz_bom_item` SET product_id = 619 WHERE product_id IN (792);
UPDATE `biz_qc_task` SET material_id = 619 WHERE material_id IN (792);
UPDATE `biz_qc_task` SET product_id = 619 WHERE product_id IN (792);
UPDATE `biz_defect_record` SET material_id = 619 WHERE material_id IN (792);
UPDATE `biz_defect_record` SET product_id = 619 WHERE product_id IN (792);
UPDATE `biz_mrp_demand` SET material_id = 619 WHERE material_id IN (792);
UPDATE `biz_mrp_demand` SET product_id = 619 WHERE product_id IN (792);
UPDATE `biz_outsource_request` SET material_id = 619 WHERE material_id IN (792);
UPDATE `biz_outsource_request` SET product_id = 619 WHERE product_id IN (792);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (792) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 619
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 619 WHERE material_id IN (792)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 619) tmp);
DELETE FROM biz_inventory WHERE material_id IN (792);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (792);

-- ============================================================
-- 泵支架: 主ID=620(ZG0139) -> 正确编码=ZGB-300-1000PM-0108HJ
-- 合并: id=793(ZGB-300-1000PM-0108HJ)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'ZGB-300-1000PM-0108HJ' AND id != 620;
UPDATE biz_material SET material_no = 'ZGB-300-1000PM-0108HJ', deleted = 0, status = 'active' WHERE id = 620;

UPDATE `biz_inv_transaction` SET material_id = 620 WHERE material_id IN (793);
UPDATE `biz_inv_task_item` SET material_id = 620 WHERE material_id IN (793);
UPDATE `biz_inv_history_item` SET material_id = 620 WHERE material_id IN (793);
UPDATE `biz_inv_request_item` SET material_id = 620 WHERE material_id IN (793);
UPDATE `biz_inv_reservation` SET material_id = 620 WHERE material_id IN (793);
UPDATE `biz_inv_transfer_item` SET material_id = 620 WHERE material_id IN (793);
UPDATE `biz_inv_stocktaking_item` SET material_id = 620 WHERE material_id IN (793);
UPDATE `biz_purchase_request` SET material_id = 620 WHERE material_id IN (793);
UPDATE `biz_purchase_order_item` SET material_id = 620 WHERE material_id IN (793);
UPDATE `biz_purchase_receipt` SET material_id = 620 WHERE material_id IN (793);
UPDATE `biz_purchase_return_item` SET material_id = 620 WHERE material_id IN (793);
UPDATE `biz_wo_bom_snapshot` SET material_id = 620 WHERE material_id IN (793);
UPDATE `biz_plan_item_material` SET material_id = 620 WHERE material_id IN (793);
UPDATE `biz_outsource_delivery_item` SET material_id = 620 WHERE material_id IN (793);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 620 WHERE material_id IN (793);
UPDATE `biz_sales_order_item` SET product_id = 620 WHERE product_id IN (793);
UPDATE `biz_work_order` SET product_id = 620 WHERE product_id IN (793);
UPDATE `biz_return_order_item` SET product_id = 620 WHERE product_id IN (793);
UPDATE `biz_mrp_production_suggestion` SET product_id = 620 WHERE product_id IN (793);
UPDATE `biz_process_route` SET product_id = 620 WHERE product_id IN (793);
UPDATE `biz_bom_item` SET material_id = 620 WHERE material_id IN (793);
UPDATE `biz_bom_item` SET product_id = 620 WHERE product_id IN (793);
UPDATE `biz_qc_task` SET material_id = 620 WHERE material_id IN (793);
UPDATE `biz_qc_task` SET product_id = 620 WHERE product_id IN (793);
UPDATE `biz_defect_record` SET material_id = 620 WHERE material_id IN (793);
UPDATE `biz_defect_record` SET product_id = 620 WHERE product_id IN (793);
UPDATE `biz_mrp_demand` SET material_id = 620 WHERE material_id IN (793);
UPDATE `biz_mrp_demand` SET product_id = 620 WHERE product_id IN (793);
UPDATE `biz_outsource_request` SET material_id = 620 WHERE material_id IN (793);
UPDATE `biz_outsource_request` SET product_id = 620 WHERE product_id IN (793);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (793) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 620
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 620 WHERE material_id IN (793)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 620) tmp);
DELETE FROM biz_inventory WHERE material_id IN (793);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (793);

-- ============================================================
-- 底座: 主ID=621(ZG0140) -> 正确编码=SZ-250-650-0911-Y315-01CR
-- 合并: id=794(SZ-250-650-0911-Y315-01CR)
-- ============================================================

-- 先释放编码(避免UNIQUE冲突)
UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = 'SZ-250-650-0911-Y315-01CR' AND id != 621;
UPDATE biz_material SET material_no = 'SZ-250-650-0911-Y315-01CR', deleted = 0, status = 'active' WHERE id = 621;

UPDATE `biz_inv_transaction` SET material_id = 621 WHERE material_id IN (794);
UPDATE `biz_inv_task_item` SET material_id = 621 WHERE material_id IN (794);
UPDATE `biz_inv_history_item` SET material_id = 621 WHERE material_id IN (794);
UPDATE `biz_inv_request_item` SET material_id = 621 WHERE material_id IN (794);
UPDATE `biz_inv_reservation` SET material_id = 621 WHERE material_id IN (794);
UPDATE `biz_inv_transfer_item` SET material_id = 621 WHERE material_id IN (794);
UPDATE `biz_inv_stocktaking_item` SET material_id = 621 WHERE material_id IN (794);
UPDATE `biz_purchase_request` SET material_id = 621 WHERE material_id IN (794);
UPDATE `biz_purchase_order_item` SET material_id = 621 WHERE material_id IN (794);
UPDATE `biz_purchase_receipt` SET material_id = 621 WHERE material_id IN (794);
UPDATE `biz_purchase_return_item` SET material_id = 621 WHERE material_id IN (794);
UPDATE `biz_wo_bom_snapshot` SET material_id = 621 WHERE material_id IN (794);
UPDATE `biz_plan_item_material` SET material_id = 621 WHERE material_id IN (794);
UPDATE `biz_outsource_delivery_item` SET material_id = 621 WHERE material_id IN (794);
UPDATE `biz_mrp_purchase_suggestion` SET material_id = 621 WHERE material_id IN (794);
UPDATE `biz_sales_order_item` SET product_id = 621 WHERE product_id IN (794);
UPDATE `biz_work_order` SET product_id = 621 WHERE product_id IN (794);
UPDATE `biz_return_order_item` SET product_id = 621 WHERE product_id IN (794);
UPDATE `biz_mrp_production_suggestion` SET product_id = 621 WHERE product_id IN (794);
UPDATE `biz_process_route` SET product_id = 621 WHERE product_id IN (794);
UPDATE `biz_bom_item` SET material_id = 621 WHERE material_id IN (794);
UPDATE `biz_bom_item` SET product_id = 621 WHERE product_id IN (794);
UPDATE `biz_qc_task` SET material_id = 621 WHERE material_id IN (794);
UPDATE `biz_qc_task` SET product_id = 621 WHERE product_id IN (794);
UPDATE `biz_defect_record` SET material_id = 621 WHERE material_id IN (794);
UPDATE `biz_defect_record` SET product_id = 621 WHERE product_id IN (794);
UPDATE `biz_mrp_demand` SET material_id = 621 WHERE material_id IN (794);
UPDATE `biz_mrp_demand` SET product_id = 621 WHERE product_id IN (794);
UPDATE `biz_outsource_request` SET material_id = 621 WHERE material_id IN (794);
UPDATE `biz_outsource_request` SET product_id = 621 WHERE product_id IN (794);

-- 库存合并
UPDATE biz_inventory pri INNER JOIN (
  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a
  FROM biz_inventory WHERE material_id IN (794) GROUP BY warehouse_id
) dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = 621
SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;
UPDATE biz_inventory SET material_id = 621 WHERE material_id IN (794)
  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = 621) tmp);
DELETE FROM biz_inventory WHERE material_id IN (794);

UPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (794);

COMMIT;
SET FOREIGN_KEY_CHECKS = 1;

-- ============= 验证: cnt 应全部为 0 =============
SELECT 'biz_inv_transaction' tbl, COUNT(*) cnt FROM `biz_inv_transaction` WHERE material_id IN (805,533,719,487,777,605,656,559,649,552,646,549,796,523,802,529,776,604,763,591,764,592,713,570,633,518,760,520,787,615,772,522,765,593,766,594,629,482,630,483,803,531,714,530,715,571,751,581,750,580,716,572,754,584,756,586,651,554,654,557,770,599,655,558,778,535,789,536,639,542,725,494,755,585,657,560,720,488,658,561,642,545,726,495,640,543,727,496,641,544,735,507,806,534,774,602,801,528,667,505,732,502,759,589,758,588,717,519,668,567,739,511,731,501,740,512,733,503,734,504,741,513,771,600,669,499,744,574,737,509,779,606,721,490,712,569,818,738,510,722,491,723,492,631,516,648,551,753,583,724,493,664,484,634,486,632,517,665,565,745,575,718,662,728,730,659,736,742,743,795,797,798,799,800,804,807,635,636,637,638,643,644,645,647,650,652,653,660,661,663,666,746,747,748,749,752,757,761,767,768,762,769,773,775,780,781,782,783,784,785,786,788,790,791,792,793,794);
SELECT 'biz_inv_task_item' tbl, COUNT(*) cnt FROM `biz_inv_task_item` WHERE material_id IN (805,533,719,487,777,605,656,559,649,552,646,549,796,523,802,529,776,604,763,591,764,592,713,570,633,518,760,520,787,615,772,522,765,593,766,594,629,482,630,483,803,531,714,530,715,571,751,581,750,580,716,572,754,584,756,586,651,554,654,557,770,599,655,558,778,535,789,536,639,542,725,494,755,585,657,560,720,488,658,561,642,545,726,495,640,543,727,496,641,544,735,507,806,534,774,602,801,528,667,505,732,502,759,589,758,588,717,519,668,567,739,511,731,501,740,512,733,503,734,504,741,513,771,600,669,499,744,574,737,509,779,606,721,490,712,569,818,738,510,722,491,723,492,631,516,648,551,753,583,724,493,664,484,634,486,632,517,665,565,745,575,718,662,728,730,659,736,742,743,795,797,798,799,800,804,807,635,636,637,638,643,644,645,647,650,652,653,660,661,663,666,746,747,748,749,752,757,761,767,768,762,769,773,775,780,781,782,783,784,785,786,788,790,791,792,793,794);
SELECT 'biz_inv_history_item' tbl, COUNT(*) cnt FROM `biz_inv_history_item` WHERE material_id IN (805,533,719,487,777,605,656,559,649,552,646,549,796,523,802,529,776,604,763,591,764,592,713,570,633,518,760,520,787,615,772,522,765,593,766,594,629,482,630,483,803,531,714,530,715,571,751,581,750,580,716,572,754,584,756,586,651,554,654,557,770,599,655,558,778,535,789,536,639,542,725,494,755,585,657,560,720,488,658,561,642,545,726,495,640,543,727,496,641,544,735,507,806,534,774,602,801,528,667,505,732,502,759,589,758,588,717,519,668,567,739,511,731,501,740,512,733,503,734,504,741,513,771,600,669,499,744,574,737,509,779,606,721,490,712,569,818,738,510,722,491,723,492,631,516,648,551,753,583,724,493,664,484,634,486,632,517,665,565,745,575,718,662,728,730,659,736,742,743,795,797,798,799,800,804,807,635,636,637,638,643,644,645,647,650,652,653,660,661,663,666,746,747,748,749,752,757,761,767,768,762,769,773,775,780,781,782,783,784,785,786,788,790,791,792,793,794);
SELECT 'biz_inv_request_item' tbl, COUNT(*) cnt FROM `biz_inv_request_item` WHERE material_id IN (805,533,719,487,777,605,656,559,649,552,646,549,796,523,802,529,776,604,763,591,764,592,713,570,633,518,760,520,787,615,772,522,765,593,766,594,629,482,630,483,803,531,714,530,715,571,751,581,750,580,716,572,754,584,756,586,651,554,654,557,770,599,655,558,778,535,789,536,639,542,725,494,755,585,657,560,720,488,658,561,642,545,726,495,640,543,727,496,641,544,735,507,806,534,774,602,801,528,667,505,732,502,759,589,758,588,717,519,668,567,739,511,731,501,740,512,733,503,734,504,741,513,771,600,669,499,744,574,737,509,779,606,721,490,712,569,818,738,510,722,491,723,492,631,516,648,551,753,583,724,493,664,484,634,486,632,517,665,565,745,575,718,662,728,730,659,736,742,743,795,797,798,799,800,804,807,635,636,637,638,643,644,645,647,650,652,653,660,661,663,666,746,747,748,749,752,757,761,767,768,762,769,773,775,780,781,782,783,784,785,786,788,790,791,792,793,794);
SELECT 'biz_inv_reservation' tbl, COUNT(*) cnt FROM `biz_inv_reservation` WHERE material_id IN (805,533,719,487,777,605,656,559,649,552,646,549,796,523,802,529,776,604,763,591,764,592,713,570,633,518,760,520,787,615,772,522,765,593,766,594,629,482,630,483,803,531,714,530,715,571,751,581,750,580,716,572,754,584,756,586,651,554,654,557,770,599,655,558,778,535,789,536,639,542,725,494,755,585,657,560,720,488,658,561,642,545,726,495,640,543,727,496,641,544,735,507,806,534,774,602,801,528,667,505,732,502,759,589,758,588,717,519,668,567,739,511,731,501,740,512,733,503,734,504,741,513,771,600,669,499,744,574,737,509,779,606,721,490,712,569,818,738,510,722,491,723,492,631,516,648,551,753,583,724,493,664,484,634,486,632,517,665,565,745,575,718,662,728,730,659,736,742,743,795,797,798,799,800,804,807,635,636,637,638,643,644,645,647,650,652,653,660,661,663,666,746,747,748,749,752,757,761,767,768,762,769,773,775,780,781,782,783,784,785,786,788,790,791,792,793,794);
SELECT 'biz_inv_transfer_item' tbl, COUNT(*) cnt FROM `biz_inv_transfer_item` WHERE material_id IN (805,533,719,487,777,605,656,559,649,552,646,549,796,523,802,529,776,604,763,591,764,592,713,570,633,518,760,520,787,615,772,522,765,593,766,594,629,482,630,483,803,531,714,530,715,571,751,581,750,580,716,572,754,584,756,586,651,554,654,557,770,599,655,558,778,535,789,536,639,542,725,494,755,585,657,560,720,488,658,561,642,545,726,495,640,543,727,496,641,544,735,507,806,534,774,602,801,528,667,505,732,502,759,589,758,588,717,519,668,567,739,511,731,501,740,512,733,503,734,504,741,513,771,600,669,499,744,574,737,509,779,606,721,490,712,569,818,738,510,722,491,723,492,631,516,648,551,753,583,724,493,664,484,634,486,632,517,665,565,745,575,718,662,728,730,659,736,742,743,795,797,798,799,800,804,807,635,636,637,638,643,644,645,647,650,652,653,660,661,663,666,746,747,748,749,752,757,761,767,768,762,769,773,775,780,781,782,783,784,785,786,788,790,791,792,793,794);
SELECT 'biz_inv_stocktaking_item' tbl, COUNT(*) cnt FROM `biz_inv_stocktaking_item` WHERE material_id IN (805,533,719,487,777,605,656,559,649,552,646,549,796,523,802,529,776,604,763,591,764,592,713,570,633,518,760,520,787,615,772,522,765,593,766,594,629,482,630,483,803,531,714,530,715,571,751,581,750,580,716,572,754,584,756,586,651,554,654,557,770,599,655,558,778,535,789,536,639,542,725,494,755,585,657,560,720,488,658,561,642,545,726,495,640,543,727,496,641,544,735,507,806,534,774,602,801,528,667,505,732,502,759,589,758,588,717,519,668,567,739,511,731,501,740,512,733,503,734,504,741,513,771,600,669,499,744,574,737,509,779,606,721,490,712,569,818,738,510,722,491,723,492,631,516,648,551,753,583,724,493,664,484,634,486,632,517,665,565,745,575,718,662,728,730,659,736,742,743,795,797,798,799,800,804,807,635,636,637,638,643,644,645,647,650,652,653,660,661,663,666,746,747,748,749,752,757,761,767,768,762,769,773,775,780,781,782,783,784,785,786,788,790,791,792,793,794);
SELECT 'biz_purchase_request' tbl, COUNT(*) cnt FROM `biz_purchase_request` WHERE material_id IN (805,533,719,487,777,605,656,559,649,552,646,549,796,523,802,529,776,604,763,591,764,592,713,570,633,518,760,520,787,615,772,522,765,593,766,594,629,482,630,483,803,531,714,530,715,571,751,581,750,580,716,572,754,584,756,586,651,554,654,557,770,599,655,558,778,535,789,536,639,542,725,494,755,585,657,560,720,488,658,561,642,545,726,495,640,543,727,496,641,544,735,507,806,534,774,602,801,528,667,505,732,502,759,589,758,588,717,519,668,567,739,511,731,501,740,512,733,503,734,504,741,513,771,600,669,499,744,574,737,509,779,606,721,490,712,569,818,738,510,722,491,723,492,631,516,648,551,753,583,724,493,664,484,634,486,632,517,665,565,745,575,718,662,728,730,659,736,742,743,795,797,798,799,800,804,807,635,636,637,638,643,644,645,647,650,652,653,660,661,663,666,746,747,748,749,752,757,761,767,768,762,769,773,775,780,781,782,783,784,785,786,788,790,791,792,793,794);
SELECT 'biz_purchase_order_item' tbl, COUNT(*) cnt FROM `biz_purchase_order_item` WHERE material_id IN (805,533,719,487,777,605,656,559,649,552,646,549,796,523,802,529,776,604,763,591,764,592,713,570,633,518,760,520,787,615,772,522,765,593,766,594,629,482,630,483,803,531,714,530,715,571,751,581,750,580,716,572,754,584,756,586,651,554,654,557,770,599,655,558,778,535,789,536,639,542,725,494,755,585,657,560,720,488,658,561,642,545,726,495,640,543,727,496,641,544,735,507,806,534,774,602,801,528,667,505,732,502,759,589,758,588,717,519,668,567,739,511,731,501,740,512,733,503,734,504,741,513,771,600,669,499,744,574,737,509,779,606,721,490,712,569,818,738,510,722,491,723,492,631,516,648,551,753,583,724,493,664,484,634,486,632,517,665,565,745,575,718,662,728,730,659,736,742,743,795,797,798,799,800,804,807,635,636,637,638,643,644,645,647,650,652,653,660,661,663,666,746,747,748,749,752,757,761,767,768,762,769,773,775,780,781,782,783,784,785,786,788,790,791,792,793,794);
SELECT 'biz_purchase_receipt' tbl, COUNT(*) cnt FROM `biz_purchase_receipt` WHERE material_id IN (805,533,719,487,777,605,656,559,649,552,646,549,796,523,802,529,776,604,763,591,764,592,713,570,633,518,760,520,787,615,772,522,765,593,766,594,629,482,630,483,803,531,714,530,715,571,751,581,750,580,716,572,754,584,756,586,651,554,654,557,770,599,655,558,778,535,789,536,639,542,725,494,755,585,657,560,720,488,658,561,642,545,726,495,640,543,727,496,641,544,735,507,806,534,774,602,801,528,667,505,732,502,759,589,758,588,717,519,668,567,739,511,731,501,740,512,733,503,734,504,741,513,771,600,669,499,744,574,737,509,779,606,721,490,712,569,818,738,510,722,491,723,492,631,516,648,551,753,583,724,493,664,484,634,486,632,517,665,565,745,575,718,662,728,730,659,736,742,743,795,797,798,799,800,804,807,635,636,637,638,643,644,645,647,650,652,653,660,661,663,666,746,747,748,749,752,757,761,767,768,762,769,773,775,780,781,782,783,784,785,786,788,790,791,792,793,794);
SELECT 'biz_purchase_return_item' tbl, COUNT(*) cnt FROM `biz_purchase_return_item` WHERE material_id IN (805,533,719,487,777,605,656,559,649,552,646,549,796,523,802,529,776,604,763,591,764,592,713,570,633,518,760,520,787,615,772,522,765,593,766,594,629,482,630,483,803,531,714,530,715,571,751,581,750,580,716,572,754,584,756,586,651,554,654,557,770,599,655,558,778,535,789,536,639,542,725,494,755,585,657,560,720,488,658,561,642,545,726,495,640,543,727,496,641,544,735,507,806,534,774,602,801,528,667,505,732,502,759,589,758,588,717,519,668,567,739,511,731,501,740,512,733,503,734,504,741,513,771,600,669,499,744,574,737,509,779,606,721,490,712,569,818,738,510,722,491,723,492,631,516,648,551,753,583,724,493,664,484,634,486,632,517,665,565,745,575,718,662,728,730,659,736,742,743,795,797,798,799,800,804,807,635,636,637,638,643,644,645,647,650,652,653,660,661,663,666,746,747,748,749,752,757,761,767,768,762,769,773,775,780,781,782,783,784,785,786,788,790,791,792,793,794);
SELECT 'biz_wo_bom_snapshot' tbl, COUNT(*) cnt FROM `biz_wo_bom_snapshot` WHERE material_id IN (805,533,719,487,777,605,656,559,649,552,646,549,796,523,802,529,776,604,763,591,764,592,713,570,633,518,760,520,787,615,772,522,765,593,766,594,629,482,630,483,803,531,714,530,715,571,751,581,750,580,716,572,754,584,756,586,651,554,654,557,770,599,655,558,778,535,789,536,639,542,725,494,755,585,657,560,720,488,658,561,642,545,726,495,640,543,727,496,641,544,735,507,806,534,774,602,801,528,667,505,732,502,759,589,758,588,717,519,668,567,739,511,731,501,740,512,733,503,734,504,741,513,771,600,669,499,744,574,737,509,779,606,721,490,712,569,818,738,510,722,491,723,492,631,516,648,551,753,583,724,493,664,484,634,486,632,517,665,565,745,575,718,662,728,730,659,736,742,743,795,797,798,799,800,804,807,635,636,637,638,643,644,645,647,650,652,653,660,661,663,666,746,747,748,749,752,757,761,767,768,762,769,773,775,780,781,782,783,784,785,786,788,790,791,792,793,794);
SELECT 'biz_plan_item_material' tbl, COUNT(*) cnt FROM `biz_plan_item_material` WHERE material_id IN (805,533,719,487,777,605,656,559,649,552,646,549,796,523,802,529,776,604,763,591,764,592,713,570,633,518,760,520,787,615,772,522,765,593,766,594,629,482,630,483,803,531,714,530,715,571,751,581,750,580,716,572,754,584,756,586,651,554,654,557,770,599,655,558,778,535,789,536,639,542,725,494,755,585,657,560,720,488,658,561,642,545,726,495,640,543,727,496,641,544,735,507,806,534,774,602,801,528,667,505,732,502,759,589,758,588,717,519,668,567,739,511,731,501,740,512,733,503,734,504,741,513,771,600,669,499,744,574,737,509,779,606,721,490,712,569,818,738,510,722,491,723,492,631,516,648,551,753,583,724,493,664,484,634,486,632,517,665,565,745,575,718,662,728,730,659,736,742,743,795,797,798,799,800,804,807,635,636,637,638,643,644,645,647,650,652,653,660,661,663,666,746,747,748,749,752,757,761,767,768,762,769,773,775,780,781,782,783,784,785,786,788,790,791,792,793,794);
SELECT 'biz_outsource_delivery_item' tbl, COUNT(*) cnt FROM `biz_outsource_delivery_item` WHERE material_id IN (805,533,719,487,777,605,656,559,649,552,646,549,796,523,802,529,776,604,763,591,764,592,713,570,633,518,760,520,787,615,772,522,765,593,766,594,629,482,630,483,803,531,714,530,715,571,751,581,750,580,716,572,754,584,756,586,651,554,654,557,770,599,655,558,778,535,789,536,639,542,725,494,755,585,657,560,720,488,658,561,642,545,726,495,640,543,727,496,641,544,735,507,806,534,774,602,801,528,667,505,732,502,759,589,758,588,717,519,668,567,739,511,731,501,740,512,733,503,734,504,741,513,771,600,669,499,744,574,737,509,779,606,721,490,712,569,818,738,510,722,491,723,492,631,516,648,551,753,583,724,493,664,484,634,486,632,517,665,565,745,575,718,662,728,730,659,736,742,743,795,797,798,799,800,804,807,635,636,637,638,643,644,645,647,650,652,653,660,661,663,666,746,747,748,749,752,757,761,767,768,762,769,773,775,780,781,782,783,784,785,786,788,790,791,792,793,794);
SELECT 'biz_mrp_purchase_suggestion' tbl, COUNT(*) cnt FROM `biz_mrp_purchase_suggestion` WHERE material_id IN (805,533,719,487,777,605,656,559,649,552,646,549,796,523,802,529,776,604,763,591,764,592,713,570,633,518,760,520,787,615,772,522,765,593,766,594,629,482,630,483,803,531,714,530,715,571,751,581,750,580,716,572,754,584,756,586,651,554,654,557,770,599,655,558,778,535,789,536,639,542,725,494,755,585,657,560,720,488,658,561,642,545,726,495,640,543,727,496,641,544,735,507,806,534,774,602,801,528,667,505,732,502,759,589,758,588,717,519,668,567,739,511,731,501,740,512,733,503,734,504,741,513,771,600,669,499,744,574,737,509,779,606,721,490,712,569,818,738,510,722,491,723,492,631,516,648,551,753,583,724,493,664,484,634,486,632,517,665,565,745,575,718,662,728,730,659,736,742,743,795,797,798,799,800,804,807,635,636,637,638,643,644,645,647,650,652,653,660,661,663,666,746,747,748,749,752,757,761,767,768,762,769,773,775,780,781,782,783,784,785,786,788,790,791,792,793,794);
SELECT 'biz_inventory' tbl, COUNT(*) cnt FROM `biz_inventory` WHERE material_id IN (805,533,719,487,777,605,656,559,649,552,646,549,796,523,802,529,776,604,763,591,764,592,713,570,633,518,760,520,787,615,772,522,765,593,766,594,629,482,630,483,803,531,714,530,715,571,751,581,750,580,716,572,754,584,756,586,651,554,654,557,770,599,655,558,778,535,789,536,639,542,725,494,755,585,657,560,720,488,658,561,642,545,726,495,640,543,727,496,641,544,735,507,806,534,774,602,801,528,667,505,732,502,759,589,758,588,717,519,668,567,739,511,731,501,740,512,733,503,734,504,741,513,771,600,669,499,744,574,737,509,779,606,721,490,712,569,818,738,510,722,491,723,492,631,516,648,551,753,583,724,493,664,484,634,486,632,517,665,565,745,575,718,662,728,730,659,736,742,743,795,797,798,799,800,804,807,635,636,637,638,643,644,645,647,650,652,653,660,661,663,666,746,747,748,749,752,757,761,767,768,762,769,773,775,780,781,782,783,784,785,786,788,790,791,792,793,794);
SELECT 'biz_sales_order_item' tbl, COUNT(*) cnt FROM `biz_sales_order_item` WHERE product_id IN (805,533,719,487,777,605,656,559,649,552,646,549,796,523,802,529,776,604,763,591,764,592,713,570,633,518,760,520,787,615,772,522,765,593,766,594,629,482,630,483,803,531,714,530,715,571,751,581,750,580,716,572,754,584,756,586,651,554,654,557,770,599,655,558,778,535,789,536,639,542,725,494,755,585,657,560,720,488,658,561,642,545,726,495,640,543,727,496,641,544,735,507,806,534,774,602,801,528,667,505,732,502,759,589,758,588,717,519,668,567,739,511,731,501,740,512,733,503,734,504,741,513,771,600,669,499,744,574,737,509,779,606,721,490,712,569,818,738,510,722,491,723,492,631,516,648,551,753,583,724,493,664,484,634,486,632,517,665,565,745,575,718,662,728,730,659,736,742,743,795,797,798,799,800,804,807,635,636,637,638,643,644,645,647,650,652,653,660,661,663,666,746,747,748,749,752,757,761,767,768,762,769,773,775,780,781,782,783,784,785,786,788,790,791,792,793,794);
SELECT 'biz_work_order' tbl, COUNT(*) cnt FROM `biz_work_order` WHERE product_id IN (805,533,719,487,777,605,656,559,649,552,646,549,796,523,802,529,776,604,763,591,764,592,713,570,633,518,760,520,787,615,772,522,765,593,766,594,629,482,630,483,803,531,714,530,715,571,751,581,750,580,716,572,754,584,756,586,651,554,654,557,770,599,655,558,778,535,789,536,639,542,725,494,755,585,657,560,720,488,658,561,642,545,726,495,640,543,727,496,641,544,735,507,806,534,774,602,801,528,667,505,732,502,759,589,758,588,717,519,668,567,739,511,731,501,740,512,733,503,734,504,741,513,771,600,669,499,744,574,737,509,779,606,721,490,712,569,818,738,510,722,491,723,492,631,516,648,551,753,583,724,493,664,484,634,486,632,517,665,565,745,575,718,662,728,730,659,736,742,743,795,797,798,799,800,804,807,635,636,637,638,643,644,645,647,650,652,653,660,661,663,666,746,747,748,749,752,757,761,767,768,762,769,773,775,780,781,782,783,784,785,786,788,790,791,792,793,794);
SELECT 'biz_return_order_item' tbl, COUNT(*) cnt FROM `biz_return_order_item` WHERE product_id IN (805,533,719,487,777,605,656,559,649,552,646,549,796,523,802,529,776,604,763,591,764,592,713,570,633,518,760,520,787,615,772,522,765,593,766,594,629,482,630,483,803,531,714,530,715,571,751,581,750,580,716,572,754,584,756,586,651,554,654,557,770,599,655,558,778,535,789,536,639,542,725,494,755,585,657,560,720,488,658,561,642,545,726,495,640,543,727,496,641,544,735,507,806,534,774,602,801,528,667,505,732,502,759,589,758,588,717,519,668,567,739,511,731,501,740,512,733,503,734,504,741,513,771,600,669,499,744,574,737,509,779,606,721,490,712,569,818,738,510,722,491,723,492,631,516,648,551,753,583,724,493,664,484,634,486,632,517,665,565,745,575,718,662,728,730,659,736,742,743,795,797,798,799,800,804,807,635,636,637,638,643,644,645,647,650,652,653,660,661,663,666,746,747,748,749,752,757,761,767,768,762,769,773,775,780,781,782,783,784,785,786,788,790,791,792,793,794);
SELECT 'biz_mrp_production_suggestion' tbl, COUNT(*) cnt FROM `biz_mrp_production_suggestion` WHERE product_id IN (805,533,719,487,777,605,656,559,649,552,646,549,796,523,802,529,776,604,763,591,764,592,713,570,633,518,760,520,787,615,772,522,765,593,766,594,629,482,630,483,803,531,714,530,715,571,751,581,750,580,716,572,754,584,756,586,651,554,654,557,770,599,655,558,778,535,789,536,639,542,725,494,755,585,657,560,720,488,658,561,642,545,726,495,640,543,727,496,641,544,735,507,806,534,774,602,801,528,667,505,732,502,759,589,758,588,717,519,668,567,739,511,731,501,740,512,733,503,734,504,741,513,771,600,669,499,744,574,737,509,779,606,721,490,712,569,818,738,510,722,491,723,492,631,516,648,551,753,583,724,493,664,484,634,486,632,517,665,565,745,575,718,662,728,730,659,736,742,743,795,797,798,799,800,804,807,635,636,637,638,643,644,645,647,650,652,653,660,661,663,666,746,747,748,749,752,757,761,767,768,762,769,773,775,780,781,782,783,784,785,786,788,790,791,792,793,794);
SELECT 'biz_process_route' tbl, COUNT(*) cnt FROM `biz_process_route` WHERE product_id IN (805,533,719,487,777,605,656,559,649,552,646,549,796,523,802,529,776,604,763,591,764,592,713,570,633,518,760,520,787,615,772,522,765,593,766,594,629,482,630,483,803,531,714,530,715,571,751,581,750,580,716,572,754,584,756,586,651,554,654,557,770,599,655,558,778,535,789,536,639,542,725,494,755,585,657,560,720,488,658,561,642,545,726,495,640,543,727,496,641,544,735,507,806,534,774,602,801,528,667,505,732,502,759,589,758,588,717,519,668,567,739,511,731,501,740,512,733,503,734,504,741,513,771,600,669,499,744,574,737,509,779,606,721,490,712,569,818,738,510,722,491,723,492,631,516,648,551,753,583,724,493,664,484,634,486,632,517,665,565,745,575,718,662,728,730,659,736,742,743,795,797,798,799,800,804,807,635,636,637,638,643,644,645,647,650,652,653,660,661,663,666,746,747,748,749,752,757,761,767,768,762,769,773,775,780,781,782,783,784,785,786,788,790,791,792,793,794);
