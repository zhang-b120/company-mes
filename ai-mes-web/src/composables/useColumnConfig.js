import { ref } from 'vue'
import { getColumnConfig, saveColumnConfig } from '@/api/columnConfig'

/**
 * 表格列配置 composable
 *
 * 使用方式：
 *   const { visibleColumns, loadColumnConfig, saveConfig, initColumns } = useColumnConfig('sales_order', defaultColumns)
 *
 * defaultColumns 格式：
 *   [{ key: 'orderNo', label: '订单号', type: 'standard' }, ...]
 *
 * @param {string} module         业务模块标识
 * @param {Array}  defaultColumns 该模块的默认列定义（作为首次未配置时的兜底）
 */
export function useColumnConfig(module, defaultColumns) {
  /** 当前生效的列列表（已排序、已过滤不可见列），直接用于 el-table 渲染 */
  const visibleColumns = ref([])
  /** 完整列列表（含不可见列），用于配置弹窗展示 */
  const allColumns = ref([])

  /**
   * 加载列配置
   * 有用户配置时按配置来，没有时使用 defaultColumns 全部显示
   * @param {Array} extColumns 扩展字段列（来自 useExtFields 的 listFieldConfigs），可选
   */
  async function loadColumnConfig(extColumns = []) {
    // 合并默认列 + 扩展字段列
    const merged = [
      ...defaultColumns,
      ...extColumns.map(f => ({
        key: `ext_${f.fieldKey}`,
        label: f.fieldLabel,
        type: 'extension',
        fieldKey: f.fieldKey
      }))
    ]

    try {
      const res = await getColumnConfig(module)
      const saved = res.data || []

      if (saved.length === 0) {
        // 未配置过，使用默认（全部显示）
        allColumns.value = merged.map((col, i) => ({
          ...col,
          visible: true,
          sortOrder: i
        }))
      } else {
        // 已有配置：以保存的配置为基准，补充新增的列（追加到末尾）
        const savedMap = new Map(saved.map(s => [s.columnKey, s]))
        const configured = []
        // 按保存的顺序还原
        for (const s of saved) {
          const def = merged.find(c => c.key === s.columnKey)
          if (def) {
            configured.push({ ...def, visible: s.visible === 1, sortOrder: s.sortOrder })
          }
        }
        // 补充配置中没有的新列（追加到末尾，默认显示）
        for (const col of merged) {
          if (!savedMap.has(col.key)) {
            configured.push({ ...col, visible: true, sortOrder: configured.length })
          }
        }
        allColumns.value = configured
      }
    } catch {
      // 请求失败降级为全部默认列
      allColumns.value = merged.map((col, i) => ({ ...col, visible: true, sortOrder: i }))
    }

    applyVisible()
  }

  /** 将 allColumns 中 visible=true 的列按顺序提取为 visibleColumns */
  function applyVisible() {
    visibleColumns.value = allColumns.value.filter(c => c.visible)
  }

  /**
   * 保存列配置到后端
   * @param {Array} columns allColumns 经用户调整后的数组
   */
  async function saveConfig(columns) {
    allColumns.value = columns
    applyVisible()
    const payload = columns.map((col, i) => ({
      columnKey: col.key,
      columnLabel: col.label,
      columnType: col.type || 'standard',
      visible: col.visible ? 1 : 0,
      sortOrder: i
    }))
    await saveColumnConfig(module, payload)
  }

  return {
    visibleColumns,
    allColumns,
    loadColumnConfig,
    saveConfig,
    applyVisible
  }
}
