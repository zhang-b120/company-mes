const fs = require('fs');
const content = fs.readFileSync('biz_material.sql', 'utf8');
const lines = content.split(/\r?\n/).filter(l => l.includes('VALUES'));

const records = [];
for (const line of lines) {
  const m = line.match(/VALUES\s*\((.+)\);\s*$/);
  if (!m) continue;
  const raw = m[1];
  const fields = [];
  let cur = '', inQ = false;
  for (let i = 0; i < raw.length; i++) {
    const ch = raw[i];
    if (ch === "'" && !inQ) { inQ = true; cur += ch; }
    else if (ch === "'" && inQ) {
      if (i + 1 < raw.length && raw[i + 1] === "'") { cur += "''"; i++; }
      else { inQ = false; cur += ch; }
    }
    else if (ch === ',' && !inQ) { fields.push(cur.trim()); cur = ''; }
    else { cur += ch; }
  }
  fields.push(cur.trim());
  const c = v => { if (!v) return null; v = v.trim(); if (v === 'NULL') return null; if (v.startsWith("'") && v.endsWith("'")) return v.slice(1, -1); return v; };
  if (fields.length >= 34) {
    records.push({
      id: +c(fields[0]), no: c(fields[1]), name: c(fields[2]), spec: c(fields[3]),
      cat: c(fields[4]), catId: c(fields[5]), unit: c(fields[6]), src: c(fields[17]),
      attrs: c(fields[16]), st: c(fields[27]), del: +(c(fields[33]) || 0)
    });
  }
}
console.log('Total records:', records.length);

// Build lookup: materialNo -> record
const byNo = {};
records.forEach(r => { if (r.no) byNo[r.no] = r; });

// ========================================================================
// 核心分组逻辑：只通过 materialNo <-> spec 交叉引用 来识别真正的重复
// 条件：记录A的spec == 记录B的materialNo（或反向），且A和B名称相同
// 排除：仅靠 "同名+同spec" 的巧合（如两个槽钢都spec=14 但是不同物料）
// ========================================================================

const processed = new Set();
const groups = [];

for (const r of records) {
  if (processed.has(r.id)) continue;

  const group = new Map(); // id -> record
  group.set(r.id, r);
  processed.add(r.id);
  const queue = [r];

  while (queue.length > 0) {
    const cur = queue.shift();

    // 关键链接1: cur.spec 是否等于某条记录的 materialNo?
    if (cur.spec && byNo[cur.spec]) {
      const linked = byNo[cur.spec];
      if (!processed.has(linked.id) && linked.name === cur.name) {
        group.set(linked.id, linked);
        processed.add(linked.id);
        queue.push(linked);
      }
    }

    // 关键链接2: cur.materialNo 是否等于某条记录的 spec?
    for (const other of records) {
      if (!processed.has(other.id) && other.spec === cur.no && other.name === cur.name) {
        group.set(other.id, other);
        processed.add(other.id);
        queue.push(other);
      }
    }

    // 关键链接3: ZG记录通过 spec 和 ORIGINAL 的 spec 匹配
    // 如果 cur 是 ZG 记录 (no starts with ZG)，它的 spec 连接到同 spec 的 ORIGINAL
    if (cur.spec) {
      for (const other of records) {
        if (!processed.has(other.id) && other.spec === cur.spec && other.name === cur.name) {
          // 但只有当这个 spec 值同时也是某条记录的 materialNo 时才算真正交叉引用
          if (byNo[cur.spec]) {
            group.set(other.id, other);
            processed.add(other.id);
            queue.push(other);
          }
        }
      }
    }
  }

  if (group.size > 1) {
    groups.push([...group.values()]);
  }
}

console.log('True cross-reference dup groups:', groups.length);

// For each group determine primary and correct materialNo
const mergeOps = [];
for (const g of groups) {
  // Find correct materialNo = the one that appears as BOTH a materialNo AND a spec
  let correctNo = null;
  for (const r of g) {
    if (r.no && g.some(o => o.id !== r.id && o.spec === r.no)) {
      correctNo = r.no;
      break;
    }
  }

  // Pick primary: richest data
  const scored = g.map(r => {
    let s = 0;
    if (r.cat && r.cat !== '') s += 3;
    if (r.catId) s += 1;
    if (r.attrs && r.attrs.includes(',')) s += 2;
    if (r.src === 'manufacture') s += 2;
    if (r.no && !r.no.startsWith('ZG')) s += 1;
    if (r.spec && r.spec !== '') s += 1;
    s -= r.id * 0.0001;
    return { r, s };
  }).sort((a, b) => b.s - a.s);

  const primary = scored[0].r;
  const others = g.filter(r => r.id !== primary.id);

  mergeOps.push({
    primaryId: primary.id, primaryNo: primary.no,
    correctNo: correctNo || primary.no, name: primary.name,
    mergeIds: others.map(o => o.id), others
  });
}

console.log('Merge ops:', mergeOps.length, '  Total to merge:', mergeOps.reduce((s, m) => s + m.mergeIds.length, 0));

// Generate SQL
const esc = s => s ? s.replace(/'/g, "''") : '';
let sql = '';
sql += '-- =====================================================\n';
sql += '-- 物料重复数据修复脚本 (修正版v2)\n';
sql += '-- 仅处理 materialNo<->spec 交叉引用的真正重复\n';
sql += '-- 以情况1数据格式为准: materialNo 使用带横杠格式\n';
sql += '-- 合并组数: ' + mergeOps.length + '\n';
sql += '-- 合并记录数: ' + mergeOps.reduce((s, m) => s + m.mergeIds.length, 0) + '\n';
sql += '-- =====================================================\n\n';
sql += '-- 第1步: 请先备份数据库!\n\n';
sql += 'SET FOREIGN_KEY_CHECKS = 0;\nSTART TRANSACTION;\n\n';

const matTables = [
  'biz_inv_transaction', 'biz_inv_task_item', 'biz_inv_history_item',
  'biz_inv_request_item', 'biz_inv_reservation', 'biz_inv_transfer_item',
  'biz_inv_stocktaking_item', 'biz_purchase_request', 'biz_purchase_order_item',
  'biz_purchase_receipt', 'biz_purchase_return_item', 'biz_wo_bom_snapshot',
  'biz_plan_item_material', 'biz_outsource_delivery_item', 'biz_mrp_purchase_suggestion'
];
const prodTables = [
  'biz_sales_order_item', 'biz_work_order', 'biz_return_order_item',
  'biz_mrp_production_suggestion', 'biz_process_route'
];
const bothTables = [
  'biz_bom_item', 'biz_qc_task', 'biz_defect_record', 'biz_mrp_demand', 'biz_outsource_request'
];

for (const m of mergeOps) {
  if (m.mergeIds.length === 0) continue;
  const ids = m.mergeIds.join(',');
  const pid = m.primaryId;

  sql += '-- ============================================================\n';
  sql += '-- ' + m.name + ': 主ID=' + pid + '(' + m.primaryNo + ') -> 正确编码=' + m.correctNo + '\n';
  sql += '-- 合并: ' + m.others.map(o => 'id=' + o.id + '(' + o.no + ')').join(', ') + '\n';
  sql += '-- ============================================================\n\n';

  // A: Update primary materialNo to correct dashed format + ensure not deleted
  if (m.correctNo && m.correctNo !== m.primaryNo) {
    // UNIQUE约束: 先把占用此编码的记录改掉, 再更新主物料
    sql += "-- 先释放编码(避免UNIQUE冲突)\n";
    sql += "UPDATE biz_material SET material_no = CONCAT(material_no, '_dup') WHERE material_no = '" + esc(m.correctNo) + "' AND id != " + pid + ";\n";
    sql += "UPDATE biz_material SET material_no = '" + esc(m.correctNo) + "', deleted = 0, status = 'active' WHERE id = " + pid + ";\n";
  } else {
    sql += "UPDATE biz_material SET deleted = 0, status = 'active' WHERE id = " + pid + ";\n";
  }
  sql += '\n';

  // B: Update business table references
  for (const t of matTables) {
    sql += 'UPDATE `' + t + '` SET material_id = ' + pid + ' WHERE material_id IN (' + ids + ');\n';
  }
  for (const t of prodTables) {
    sql += 'UPDATE `' + t + '` SET product_id = ' + pid + ' WHERE product_id IN (' + ids + ');\n';
  }
  for (const t of bothTables) {
    sql += 'UPDATE `' + t + '` SET material_id = ' + pid + ' WHERE material_id IN (' + ids + ');\n';
    sql += 'UPDATE `' + t + '` SET product_id = ' + pid + ' WHERE product_id IN (' + ids + ');\n';
  }

  // C: Merge inventory
  sql += '\n-- 库存合并\n';
  sql += 'UPDATE biz_inventory pri INNER JOIN (\n';
  sql += '  SELECT warehouse_id, SUM(total_qty) t, SUM(locked_qty) l, SUM(available_qty) a\n';
  sql += '  FROM biz_inventory WHERE material_id IN (' + ids + ') GROUP BY warehouse_id\n';
  sql += ') dup ON pri.warehouse_id = dup.warehouse_id AND pri.material_id = ' + pid + '\n';
  sql += 'SET pri.total_qty = pri.total_qty + dup.t, pri.locked_qty = pri.locked_qty + dup.l, pri.available_qty = pri.available_qty + dup.a;\n';
  sql += 'UPDATE biz_inventory SET material_id = ' + pid + ' WHERE material_id IN (' + ids + ')\n';
  sql += '  AND warehouse_id NOT IN (SELECT wid FROM (SELECT warehouse_id wid FROM biz_inventory WHERE material_id = ' + pid + ') tmp);\n';
  sql += 'DELETE FROM biz_inventory WHERE material_id IN (' + ids + ');\n';

  // D: Soft-delete merged
  sql += "\nUPDATE biz_material SET deleted = 1, status = 'inactive' WHERE id IN (" + ids + ');\n\n';
}

sql += 'COMMIT;\nSET FOREIGN_KEY_CHECKS = 1;\n\n';

// Verification
const allIds = mergeOps.flatMap(m => m.mergeIds);
if (allIds.length > 0) {
  const idStr = allIds.join(',');
  sql += '-- ============= 验证: cnt 应全部为 0 =============\n';
  for (const t of [...matTables, 'biz_inventory']) {
    sql += "SELECT '" + t + "' tbl, COUNT(*) cnt FROM `" + t + "` WHERE material_id IN (" + idStr + ');\n';
  }
  for (const t of prodTables) {
    sql += "SELECT '" + t + "' tbl, COUNT(*) cnt FROM `" + t + "` WHERE product_id IN (" + idStr + ');\n';
  }
}

fs.writeFileSync('material_dedup_fix.sql', sql);
console.log('\nGenerated material_dedup_fix.sql (' + sql.split('\n').length + ' lines)');
console.log('\nSample (first 5):');
mergeOps.slice(0, 5).forEach(m => {
  console.log('  ' + m.name + ': primary=id' + m.primaryId + '(' + m.primaryNo + ') -> correctNo=' + m.correctNo);
  m.others.forEach(o => console.log('    merge: id' + o.id + '(' + o.no + ')'));
});
