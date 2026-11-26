<template>
  <div class="biz-config-page">
    <!-- 顶部说明 -->
    <div class="page-header">
      <div class="page-title">业务配置</div>
      <div class="page-desc">按业务模块管理系统功能开关与参数，修改后实时生效</div>
    </div>

    <div class="config-layout">
      <!-- 左侧模块导航 -->
      <div class="module-nav">
        <div
          v-for="mod in modules"
          :key="mod.key"
          class="module-item"
          :class="{ active: activeModule === mod.key }"
          @click="switchModule(mod.key)"
        >
          <el-icon class="module-icon"><component :is="mod.icon" /></el-icon>
          <span class="module-name">{{ mod.label }}</span>
          <el-badge
            v-if="getModifiedCount(mod.key) > 0"
            :value="getModifiedCount(mod.key)"
            class="module-badge"
          />
        </div>
      </div>

      <!-- 右侧配置区 -->
      <div class="config-content">
        <!-- 搜索 + 保存 -->
        <div class="content-toolbar">
          <el-input
            v-model="keyword"
            placeholder="搜索配置项..."
            prefix-icon="Search"
            clearable
            style="width: 260px"
          />
          <el-button
            type="primary"
            :loading="saving"
            :disabled="pendingCount === 0"
            @click="saveAll"
          >
            保存修改
            <span v-if="pendingCount > 0" class="save-count">（{{ pendingCount }}项）</span>
          </el-button>
        </div>

        <!-- 配置卡片网格 -->
        <div class="cards-grid" v-loading="loading">
          <div
            v-for="cfg in filteredConfigs"
            :key="cfg.configKey"
            class="config-card"
            :class="{ modified: isDirty(cfg.configKey) }"
          >
            <!-- 卡片头 -->
            <div class="card-head">
              <span class="card-title">{{ cfg.configName }}</span>
              <el-tag
                :type="typeTagMap[cfg.valueType]?.type || 'info'"
                size="small"
                effect="plain"
              >
                {{ typeTagMap[cfg.valueType]?.label || cfg.valueType }}
              </el-tag>
            </div>
            <!-- 描述 -->
            <div v-if="cfg.description" class="card-desc">{{ cfg.description }}</div>
            <!-- 控件 -->
            <div class="card-control">
              <!-- 开关 -->
              <el-switch
                v-if="cfg.valueType === 'switch'"
                :model-value="getVal(cfg) === 'true'"
                @change="(v) => setVal(cfg.configKey, v ? 'true' : 'false')"
              />
              <!-- 单选 -->
              <el-radio-group
                v-else-if="cfg.valueType === 'radio'"
                :model-value="getVal(cfg)"
                @change="(v) => setVal(cfg.configKey, v)"
              >
                <el-radio
                  v-for="opt in parseOptions(cfg.options)"
                  :key="opt.value"
                  :value="opt.value"
                >
                  {{ opt.label }}
                </el-radio>
              </el-radio-group>
              <!-- 多选 -->
              <el-checkbox-group
                v-else-if="cfg.valueType === 'checkbox'"
                :model-value="parseChecked(getVal(cfg))"
                @change="(v) => setVal(cfg.configKey, v.join(','))"
              >
                <el-checkbox
                  v-for="opt in parseOptions(cfg.options)"
                  :key="opt.value"
                  :value="opt.value"
                >
                  {{ opt.label }}
                </el-checkbox>
              </el-checkbox-group>
              <!-- 数值 -->
              <el-input-number
                v-else-if="cfg.valueType === 'number'"
                :model-value="Number(getVal(cfg))"
                :min="0"
                controls-position="right"
                @change="(v) => setVal(cfg.configKey, String(v ?? 0))"
              />
              <!-- 文本 -->
              <el-input
                v-else
                :model-value="getVal(cfg)"
                @blur="(e) => setVal(cfg.configKey, e.target.value)"
                @change="(v) => setVal(cfg.configKey, v)"
              />
            </div>
            <!-- 脏标记 -->
            <div v-if="isDirty(cfg.configKey)" class="dirty-dot">●</div>
          </div>

          <!-- 空状态 -->
          <el-empty
            v-if="!loading && filteredConfigs.length === 0"
            description="暂无匹配配置项"
            style="grid-column: 1 / -1"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getBizConfigList, batchUpdateBizConfig } from '@/api/system'

// ─── 模块定义 ───────────────────────────────────────
const modules = [
  { key: 'sales',       label: '销售', icon: 'ShoppingCart' },
  { key: 'purchase',    label: '采购', icon: 'ShoppingBag' },
  { key: 'production',  label: '生产', icon: 'Tools' },
  { key: 'workreport',  label: '报工', icon: 'Clock' },
  { key: 'warehouse',   label: '仓库', icon: 'Warehouse' },
  { key: 'quality',     label: '质检', icon: 'CircleCheck' },
]

const typeTagMap = {
  switch:   { label: '开关',   type: 'success' },
  radio:    { label: '单选',   type: 'primary' },
  checkbox: { label: '多选',   type: 'warning' },
  number:   { label: '数值',   type: 'info'    },
  text:     { label: '文本',   type: ''        },
}

// ─── 状态 ───────────────────────────────────────────
const activeModule = ref('sales')
const keyword = ref('')
const loading = ref(false)
const saving = ref(false)

/** 各模块配置缓存 { [moduleKey]: SysBizConfig[] } */
const moduleCache = ref({})
/** 未保存的修改 { [configKey]: newValue } */
const pendingChanges = ref({})

// ─── 计算属性 ────────────────────────────────────────
const currentConfigs = computed(() => moduleCache.value[activeModule.value] || [])

const filteredConfigs = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return currentConfigs.value
  return currentConfigs.value.filter(c =>
    c.configName.toLowerCase().includes(kw) ||
    c.configKey.toLowerCase().includes(kw) ||
    (c.description || '').toLowerCase().includes(kw)
  )
})

const pendingCount = computed(() => Object.keys(pendingChanges.value).length)

// ─── 工具函数 ────────────────────────────────────────
function getVal(cfg) {
  if (cfg.configKey in pendingChanges.value) return pendingChanges.value[cfg.configKey]
  return cfg.configValue ?? cfg.defaultValue ?? ''
}

function setVal(key, value) {
  // 找到原始配置
  const allConfigs = Object.values(moduleCache.value).flat()
  const cfg = allConfigs.find(c => c.configKey === key)
  if (!cfg) return
  const original = cfg.configValue ?? cfg.defaultValue ?? ''
  if (value === original) {
    // 恢复到原始值则移除脏标记
    const next = { ...pendingChanges.value }
    delete next[key]
    pendingChanges.value = next
  } else {
    pendingChanges.value = { ...pendingChanges.value, [key]: value }
  }
}

function isDirty(key) {
  return key in pendingChanges.value
}

function getModifiedCount(moduleKey) {
  const keys = (moduleCache.value[moduleKey] || []).map(c => c.configKey)
  return keys.filter(k => k in pendingChanges.value).length
}

function parseOptions(json) {
  if (!json) return []
  try { return JSON.parse(json) } catch { return [] }
}

function parseChecked(val) {
  if (!val) return []
  return val.split(',').map(s => s.trim()).filter(Boolean)
}

// ─── 数据加载 ─────────────────────────────────────────
async function loadModule(moduleKey) {
  if (moduleCache.value[moduleKey]) return
  loading.value = true
  try {
    const res = await getBizConfigList(moduleKey)
    moduleCache.value = {
      ...moduleCache.value,
      [moduleKey]: res.data || []
    }
  } finally {
    loading.value = false
  }
}

function switchModule(key) {
  activeModule.value = key
  keyword.value = ''
  loadModule(key)
}

// ─── 保存 ─────────────────────────────────────────────
async function saveAll() {
  if (pendingCount.value === 0) return
  saving.value = true
  try {
    await batchUpdateBizConfig({ ...pendingChanges.value })
    // 更新本地缓存
    const updated = { ...pendingChanges.value }
    for (const [modKey, cfgList] of Object.entries(moduleCache.value)) {
      moduleCache.value[modKey] = cfgList.map(c =>
        c.configKey in updated ? { ...c, configValue: updated[c.configKey] } : c
      )
    }
    pendingChanges.value = {}
    ElMessage.success('配置保存成功')
  } catch {
    ElMessage.error('保存失败，请重试')
  } finally {
    saving.value = false
  }
}

// ─── 初始化 ───────────────────────────────────────────
onMounted(() => loadModule('sales'))
</script>

<style scoped lang="scss">
.biz-config-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  background: #fff;
  border-radius: 8px;
  padding: 16px 20px;
  .page-title {
    font-size: 16px;
    font-weight: 600;
    color: #1a1a1a;
  }
  .page-desc {
    font-size: 13px;
    color: #888;
    margin-top: 4px;
  }
}

.config-layout {
  display: flex;
  gap: 16px;
  flex: 1;
  min-height: 0;
}

// ── 左侧导航 ──
.module-nav {
  width: 120px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.module-item {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 14px 8px;
  border-radius: 10px;
  background: #fff;
  cursor: pointer;
  transition: all 0.2s;
  .module-icon {
    font-size: 22px;
    color: #909399;
  }
  .module-name {
    font-size: 13px;
    color: #555;
  }
  .module-badge {
    position: absolute;
    top: 6px;
    right: 6px;
  }
  &:hover {
    background: #f0f5ff;
    .module-icon { color: #409eff; }
    .module-name { color: #409eff; }
  }
  &.active {
    background: #409eff;
    .module-icon { color: #fff; }
    .module-name { color: #fff; font-weight: 600; }
  }
}

// ── 右侧内容 ──
.config-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.content-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-radius: 8px;
  padding: 12px 16px;
  .save-count {
    font-size: 12px;
  }
}

.loading-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #888;
  background: #fff;
  border-radius: 8px;
}

.cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 12px;
  align-content: start;
  overflow-y: auto;
  min-height: 200px;
}

// ── 配置卡片 ──
.config-card {
  position: relative;
  background: #fff;
  border-radius: 10px;
  border: 1px solid #f0f0f0;
  padding: 16px;
  transition: border-color 0.2s, box-shadow 0.2s;
  &:hover {
    border-color: #d0e3ff;
    box-shadow: 0 2px 8px rgba(64, 158, 255, 0.1);
  }
  &.modified {
    border-color: #f0b429;
    background: #fffdf5;
  }
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
  gap: 8px;
}
.card-title {
  font-size: 14px;
  font-weight: 500;
  color: #1a1a1a;
  flex: 1;
}
.card-desc {
  font-size: 12px;
  color: #999;
  line-height: 1.5;
  margin-bottom: 12px;
}
.card-control {
  .el-radio-group,
  .el-checkbox-group {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }
  .el-input-number {
    width: 160px;
  }
}
.dirty-dot {
  position: absolute;
  top: 10px;
  right: 12px;
  font-size: 8px;
  color: #f0b429;
  pointer-events: none;
}
</style>
