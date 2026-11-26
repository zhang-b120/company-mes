import { ref, computed } from 'vue'
import { getExtFieldConfigs } from '@/api/extField'

/**
 * 通用扩展字段 composable
 *
 * 使用方式：
 *   const { fieldConfigs, listFieldConfigs, formFieldConfigs, getExtFieldValue, parseOptions, initExtFields } = useExtFields('sales_order')
 *
 * @param {string} module  业务模块标识，如 'sales_order' / 'customer' / 'supplier' 等
 */
export function useExtFields(module) {
  const fieldConfigs = ref([])

  /** 需要在列表中展示的字段 */
  const listFieldConfigs = computed(() => fieldConfigs.value.filter(f => f.showInList && f.enabled))

  /** 需要在表单中展示的字段 */
  const formFieldConfigs = computed(() => fieldConfigs.value.filter(f => f.showInForm && f.enabled))

  /** 需要作为查询条件展示的字段 */
  const searchFieldConfigs = computed(() => fieldConfigs.value.filter(f => f.showInSearch && f.enabled))

  /** 加载该模块的启用字段配置 */
  async function loadFieldConfigs() {
    try {
      const res = await getExtFieldConfigs(module)
      fieldConfigs.value = res.data || []
    } catch {}
  }

  /**
   * 从行数据中读取某个扩展字段的值
   * @param {object} row   列表行数据（含 extFields 字段）
   * @param {string} key   字段Key
   */
  function getExtFieldValue(row, key) {
    if (!row.extFields) return '-'
    try {
      const obj = typeof row.extFields === 'string' ? JSON.parse(row.extFields) : row.extFields
      const val = obj[key]
      return val !== undefined && val !== null && val !== '' ? val : '-'
    } catch { return '-' }
  }

  /**
   * 解析下拉选项字符串为数组
   * @param {string} optStr  JSON 数组字符串
   */
  function parseOptions(optStr) {
    if (!optStr) return []
    try { return JSON.parse(optStr) } catch { return [] }
  }

  /**
   * 将 extFields 字符串初始化为响应式对象，挂载到 formData
   * @param {object} formData  响应式表单对象（需含 extFields 属性）
   * @param {string|object} extFieldsRaw  后端返回的 ext_fields（字符串或对象）
   */
  function initExtFields(formData, extFieldsRaw) {
    if (!extFieldsRaw) {
      formData.extFields = {}
    } else {
      formData.extFields = typeof extFieldsRaw === 'string' ? JSON.parse(extFieldsRaw) : extFieldsRaw
    }
  }

  /**
   * 将 formData.extFields 序列化为 JSON 字符串，用于提交
   * @param {object} extFields
   * @returns {string|undefined}
   */
  function serializeExtFields(extFields) {
    if (!extFields || !Object.keys(extFields).length) return undefined
    // 过滤掉空值
    const cleaned = {}
    Object.entries(extFields).forEach(([k, v]) => {
      if (v !== null && v !== undefined && v !== '') cleaned[k] = v
    })
    return Object.keys(cleaned).length ? JSON.stringify(cleaned) : undefined
  }

  return {
    fieldConfigs,
    listFieldConfigs,
    formFieldConfigs,
    searchFieldConfigs,
    loadFieldConfigs,
    getExtFieldValue,
    parseOptions,
    initExtFields,
    serializeExtFields
  }
}
