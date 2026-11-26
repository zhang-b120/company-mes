-- =====================================================
-- 根据生产工单批量生成「已完成」质检任务
-- 适用场景：补录历史质检数据、客户资料缺失补全
--
-- 使用步骤：
--   1. 修改下方「参数配置」区的值
--   2. 执行「第0步」预览，确认待处理工单
--   3. 如无质检模板，执行「第1步」创建默认模板
--   4. 执行「第2步」生成质检任务
--   5. 执行「第3步」生成检验项明细
-- =====================================================

-- 统一字符集校对规则，解决 collation 冲突
SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

-- ===================================================
-- 参数配置（按实际情况修改）
-- ===================================================
SET @customer_id  = NULL;               -- 客户ID；NULL = 不按客户过滤
                                        -- 查询：SELECT id,customer_name FROM biz_customer;

SET @date_from    = '2025-01-01';       -- 工单完成时间起（含）
SET @date_to      = '2025-12-31';       -- 工单完成时间止（含）

SET @inspector_id = 7;                  -- 质检员用户ID
                                        -- 查询：SELECT id,real_name FROM sys_user WHERE deleted=0;

SET @template_id  = NULL;              -- 质检模板ID，执行第1步后会自动赋值
                                        -- 也可手动指定：SELECT id,template_name FROM biz_qc_template WHERE deleted=0;

SET @qc_type      = 'final_inspection'; -- 质检类型：
                                        --   final_inspection = 成品检验
                                        --   incoming         = 来料检验
                                        --   process          = 过程检验

SET @sample_rate  = 1.0;               -- 抽检比例：1.0=全检，0.1=10%抽检
SET @pass_rate    = 1.0;               -- 合格率：1.0=全部合格，0.95=95%合格

-- ===================================================
-- 【清理】如需删除已生成数据重新跑，取消注释执行
-- 顺序不能反：先删明细，再删任务
-- ===================================================
-- DELETE qr FROM biz_qc_result qr
-- JOIN biz_qc_task qt          ON qr.qc_task_id = qt.id
-- JOIN biz_work_order wo       ON qt.work_order_id = wo.id AND wo.deleted = 0
-- LEFT JOIN biz_sales_order so ON wo.order_id = so.id     AND so.deleted = 0
-- WHERE qt.qc_type = @qc_type
--   AND (@customer_id IS NULL OR so.customer_id = @customer_id)
--   AND (@date_from   IS NULL OR wo.update_time >= @date_from)
--   AND (@date_to     IS NULL OR wo.update_time <= CONCAT(@date_to, ' 23:59:59'));
--
-- DELETE qt FROM biz_qc_task qt
-- JOIN biz_work_order wo       ON qt.work_order_id = wo.id AND wo.deleted = 0
-- LEFT JOIN biz_sales_order so ON wo.order_id = so.id     AND so.deleted = 0
-- WHERE qt.qc_type = @qc_type
--   AND (@customer_id IS NULL OR so.customer_id = @customer_id)
--   AND (@date_from   IS NULL OR wo.update_time >= @date_from)
--   AND (@date_to     IS NULL OR wo.update_time <= CONCAT(@date_to, ' 23:59:59'));

-- ===================================================
-- 第0步：预览 — 查看将要处理的工单及模板情况
-- （只查询，不修改数据）
-- ===================================================

-- 0a：查看可用质检模板
SELECT id, template_name, apply_type, status FROM biz_qc_template WHERE deleted = 0;

-- 0b：查看将要生成质检任务的工单
SELECT
    wo.id                                                        AS 工单ID,
    wo.wo_no                                                     AS 工单号,
    m.material_name                                              AS 产品名称,
    wo.plan_qty                                                  AS 计划数量,
    wo.completed_qty                                             AS 完工数量,
    COALESCE(so.order_no, '-')                                   AS 来源订单,
    COALESCE(c.customer_name, '-')                               AS 客户名称,
    wo.update_time                                               AS 完成时间,
    IF(qt.id IS NULL, '待生成', CONCAT('已有(', qt.result, ')')) AS 质检状态
FROM biz_work_order wo
LEFT JOIN biz_sales_order so ON wo.order_id    = so.id AND so.deleted = 0
LEFT JOIN biz_customer    c  ON so.customer_id = c.id  AND c.deleted  = 0
LEFT JOIN biz_material    m  ON wo.product_id  = m.id  AND m.deleted  = 0
LEFT JOIN biz_qc_task     qt ON qt.work_order_id = wo.id
                             AND qt.qc_type = @qc_type
                             AND qt.deleted = 0
WHERE wo.deleted = 0
  AND wo.status = 'completed'
  AND (@customer_id IS NULL OR so.customer_id = @customer_id)
  AND (@date_from   IS NULL OR wo.update_time >= @date_from)
  AND (@date_to     IS NULL OR wo.update_time <= CONCAT(@date_to, ' 23:59:59'))
ORDER BY wo.update_time;

-- ===================================================
-- 第1步（按需）：创建默认成品检验模板
-- 如果第0步查到已有合适模板，跳过此步，
-- 直接设置 SET @template_id = <已有模板ID>; 即可
-- ===================================================
INSERT INTO biz_qc_template (
    template_name, template_code, apply_type,
    sample_mode, is_default, status,
    create_by, create_time, update_by, update_time, deleted
)
SELECT
    '成品检验模板', 'QCT-FINAL-001', 'final_inspection',
    'full', 1, 'active',
    1, NOW(), 1, NOW(), 0
WHERE NOT EXISTS (
    SELECT 1 FROM biz_qc_template
    WHERE template_code = 'QCT-FINAL-001' AND deleted = 0
);

SET @template_id = (
    SELECT id FROM biz_qc_template
    WHERE template_code = 'QCT-FINAL-001' AND deleted = 0
    LIMIT 1
);

-- 创建检验项（已存在则跳过）
INSERT INTO biz_qc_template_item (
    template_id, item_name, item_type, standard_value,
    unit, method, is_critical, sort_order,
    create_by, create_time, update_by, update_time, deleted
)
SELECT t.id, v.item_name, v.item_type, v.standard_value, v.unit, v.method, v.is_critical, v.sort_order,
       1, NOW(), 1, NOW(), 0
FROM biz_qc_template t
JOIN (
    SELECT 1 AS sort_order, '外观检验' AS item_name, 'qualitative' AS item_type, '无划伤、变形、污染' AS standard_value, '' AS unit, '目视检查' AS method, 1 AS is_critical UNION ALL
    SELECT 2, '尺寸检验',   'quantitative', '符合图纸要求',       'mm',  '卡尺测量', 1 UNION ALL
    SELECT 3, '数量核对',   'quantitative', '与工单数量一致',      '件',  '清点核对', 0 UNION ALL
    SELECT 4, '标识检查',   'qualitative',  '标识清晰、完整',      '',    '目视检查', 0 UNION ALL
    SELECT 5, '包装检验',   'qualitative',  '包装完好、防护到位',  '',    '目视检查', 0
) v ON 1=1
WHERE t.id = @template_id
  AND NOT EXISTS (
      SELECT 1 FROM biz_qc_template_item
      WHERE template_id = t.id AND item_name = v.item_name AND deleted = 0
  );

SELECT CONCAT('模板ID: ', @template_id, '，检验项已就绪') AS 执行结果;

-- ===================================================
-- 第2步：生成质检任务（biz_qc_task）
-- ===================================================
INSERT INTO biz_qc_task (
    task_no, qc_type, template_id,
    product_id, work_order_id,
    related_order_type, related_order_id,
    batch_no, plan_qty, sample_qty, pass_qty, fail_qty,
    result, inspector_id, inspected_at, conclusion,
    create_by, create_time, update_by, update_time, deleted
)
SELECT
    CONCAT('QC', DATE_FORMAT(wo.update_time, '%Y%m%d'), LPAD(wo.id, 5, '0')) AS task_no,
    @qc_type,
    @template_id,
    wo.product_id,
    wo.id,
    'work_order',
    wo.id,
    wo.wo_no                                                                AS batch_no,
    wo.plan_qty,
    GREATEST(1, CEIL(wo.plan_qty * @sample_rate))                           AS sample_qty,
    FLOOR(GREATEST(1, CEIL(wo.plan_qty * @sample_rate)) * @pass_rate)       AS pass_qty,
    GREATEST(1, CEIL(wo.plan_qty * @sample_rate))
        - FLOOR(GREATEST(1, CEIL(wo.plan_qty * @sample_rate)) * @pass_rate) AS fail_qty,
    IF(
        FLOOR(GREATEST(1, CEIL(wo.plan_qty * @sample_rate)) * @pass_rate)
            = GREATEST(1, CEIL(wo.plan_qty * @sample_rate)),
        'pass', 'fail'
    )                                                                       AS result,
    @inspector_id,
    -- 检验时间：工单完成当天随机 15:00-17:00
    DATE_ADD(DATE(wo.update_time), INTERVAL (15*60 + FLOOR(RAND(wo.id) * 121)) MINUTE) AS inspected_at,
    '经检验，产品符合质量标准，予以放行。',
    @inspector_id,
    DATE_ADD(DATE(wo.update_time), INTERVAL (15*60 + FLOOR(RAND(wo.id) * 121)) MINUTE),
    @inspector_id,
    DATE_ADD(DATE(wo.update_time), INTERVAL (15*60 + FLOOR(RAND(wo.id) * 121)) MINUTE),
    0
FROM biz_work_order wo
LEFT JOIN biz_sales_order so ON wo.order_id = so.id AND so.deleted = 0
WHERE wo.deleted = 0
  AND wo.status = 'completed'
  AND (@customer_id IS NULL OR so.customer_id = @customer_id)
  AND (@date_from   IS NULL OR wo.update_time >= @date_from)
  AND (@date_to     IS NULL OR wo.update_time <= CONCAT(@date_to, ' 23:59:59'))
  AND NOT EXISTS (
      SELECT 1 FROM biz_qc_task qt
      WHERE qt.work_order_id = wo.id
        AND qt.qc_type = @qc_type
        AND qt.deleted = 0
  );

SELECT CONCAT('已生成质检任务 ', ROW_COUNT(), ' 条') AS 执行结果;

-- ===================================================
-- 第3步：生成检验项明细（biz_qc_result）
-- 依赖 @template_id，第1步或手动设置后执行
-- ===================================================

-- 3a：如果任务的 template_id 为 NULL，先补上
UPDATE biz_qc_task qt
JOIN biz_work_order wo       ON qt.work_order_id = wo.id AND wo.deleted = 0
LEFT JOIN biz_sales_order so ON wo.order_id = so.id      AND so.deleted = 0
SET qt.template_id = @template_id,
    qt.update_time = NOW()
WHERE @template_id IS NOT NULL
  AND qt.template_id IS NULL
  AND qt.qc_type = @qc_type
  AND qt.deleted = 0
  AND (@customer_id IS NULL OR so.customer_id = @customer_id)
  AND (@date_from   IS NULL OR wo.update_time >= @date_from)
  AND (@date_to     IS NULL OR wo.update_time <= CONCAT(@date_to, ' 23:59:59'));

SELECT CONCAT('已补充模板关联 ', ROW_COUNT(), ' 条') AS 执行结果;

-- 3b：按模板项逐条插入检验明细
INSERT INTO biz_qc_result (
    qc_task_id, template_item_id,
    item_name, standard_value, tolerance_min, tolerance_max,
    unit, method, item_type, is_critical,
    actual_value, is_pass,
    create_by, create_time, update_by, update_time, deleted
)
SELECT
    qt.id,
    ti.id,
    ti.item_name,
    ti.standard_value,
    ti.tolerance_min,
    ti.tolerance_max,
    ti.unit,
    ti.method,
    ti.item_type,
    ti.is_critical,
    ti.standard_value AS actual_value,  -- 实测值 = 标准值（合格）
    1                 AS is_pass,
    @inspector_id,
    qt.inspected_at,
    @inspector_id,
    qt.inspected_at,
    0
FROM biz_qc_task qt
JOIN biz_work_order wo       ON qt.work_order_id = wo.id AND wo.deleted = 0
LEFT JOIN biz_sales_order so ON wo.order_id = so.id      AND so.deleted = 0
JOIN biz_qc_template_item ti ON ti.template_id = @template_id AND ti.deleted = 0
WHERE @template_id IS NOT NULL
  AND qt.template_id = @template_id
  AND qt.qc_type    = @qc_type
  AND qt.deleted    = 0
  AND (@customer_id IS NULL OR so.customer_id = @customer_id)
  AND (@date_from   IS NULL OR wo.update_time >= @date_from)
  AND (@date_to     IS NULL OR wo.update_time <= CONCAT(@date_to, ' 23:59:59'))
  AND NOT EXISTS (
      SELECT 1 FROM biz_qc_result qr
      WHERE qr.qc_task_id      = qt.id
        AND qr.template_item_id = ti.id
        AND qr.deleted          = 0
  );

SELECT CONCAT('已生成检验明细 ', ROW_COUNT(), ' 条') AS 执行结果;
