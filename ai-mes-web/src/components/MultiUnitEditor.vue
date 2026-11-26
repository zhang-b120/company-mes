<template>
  <div class="multi-unit-editor">
    <el-radio-group v-model="mode" size="small" @change="handleModeChange">
      <el-radio-button value="single">单一单位</el-radio-button>
      <el-radio-button value="multi">多单位换算</el-radio-button>
    </el-radio-group>

    <!-- 单一单位 -->
    <template v-if="mode === 'single'">
      <el-select v-model="baseUnit" placeholder="请选择单位" filterable allow-create default-first-option
        style="width: 100%; margin-top: 8px" @change="emitSingle">
        <el-option v-for="u in unitOptions" :key="u.id || u" :label="u.unitName || u" :value="u.unitName || u" />
      </el-select>
    </template>

    <!-- 多单位 -->
    <template v-else>
      <div class="unit-display" @click="dialogVisible = true">
        <span class="base-label">基本单位: {{ baseUnit || '未选择' }}</span>
        <span v-if="conversions.length > 0" class="conv-summary">
          (1{{ baseUnit }}={{ conversions[0].ratio }}{{ conversions[0].unit }})
        </span>
        <el-button type="primary" link size="small" style="margin-left: 8px">编辑</el-button>
      </div>

      <!-- 编辑弹窗 -->
      <el-dialog v-model="dialogVisible" title="多单位换算配置" width="550px" append-to-body destroy-on-close>
        <el-form-item label="基本单位">
          <el-select v-model="editBaseUnit" placeholder="请选择基本单位" filterable allow-create
            default-first-option style="width: 200px">
            <el-option v-for="u in unitOptions" :key="u.id || u" :label="u.unitName || u" :value="u.unitName || u" />
          </el-select>
        </el-form-item>
        <el-divider content-position="left">换算关系</el-divider>
        <div v-if="editConversions.length > 0" class="conv-row">
          <span>1</span>
          <span style="margin: 0 8px; font-weight: 500">{{ editBaseUnit || '基本单位' }}</span>
          <span>＝</span>
          <el-input-number v-model="editConversions[0].ratio" :min="0.0001" :precision="4" controls-position="right"
            style="width: 130px; margin: 0 8px" />
          <el-select v-model="editConversions[0].unit" placeholder="换算单位" filterable allow-create default-first-option
            style="width: 120px; margin: 0 8px">
            <el-option v-for="u in unitOptions" :key="u.id || u" :label="u.unitName || u" :value="u.unitName || u" />
          </el-select>
        </div>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="confirmConfig">确定</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  unitConfig: { type: String, default: '' },
  unitOptions: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue', 'update:unitConfig'])

const mode = ref('single')
const baseUnit = ref('')
const conversions = ref([])
const dialogVisible = ref(false)
const editBaseUnit = ref('')
const editConversions = ref([])

function parseValue() {
  baseUnit.value = props.modelValue || ''
  if (props.unitConfig) {
    try {
      const cfg = JSON.parse(props.unitConfig)
      if (cfg.baseUnit && cfg.conversions?.length > 0) {
        mode.value = 'multi'
        baseUnit.value = cfg.baseUnit
        conversions.value = [cfg.conversions[0]]
        return
      }
    } catch {}
  }
  mode.value = 'single'
  conversions.value = []
}

onMounted(() => parseValue())
watch(() => [props.modelValue, props.unitConfig], () => parseValue())

function handleModeChange(m) {
  if (m === 'single') {
    conversions.value = []
    emit('update:unitConfig', '')
    emit('update:modelValue', baseUnit.value)
  } else {
    editBaseUnit.value = baseUnit.value
    editConversions.value = conversions.value.length > 0
      ? [{ ...conversions.value[0] }]
      : [{ unit: '', ratio: 1 }]
  }
}

function emitSingle(val) {
  baseUnit.value = val
  emit('update:modelValue', val)
  emit('update:unitConfig', '')
}

function confirmConfig() {
  baseUnit.value = editBaseUnit.value
  const conv = editConversions.value[0]
  const validConversions = (conv && conv.unit && conv.ratio > 0) ? [conv] : []
  conversions.value = validConversions
  emit('update:modelValue', editBaseUnit.value)
  if (validConversions.length > 0) {
    emit('update:unitConfig', JSON.stringify({ baseUnit: editBaseUnit.value, conversions: validConversions }))
  } else {
    emit('update:unitConfig', '')
  }
  dialogVisible.value = false
}

watch(() => dialogVisible.value, (v) => {
  if (v) {
    editBaseUnit.value = baseUnit.value
    editConversions.value = conversions.value.length > 0
      ? [{ ...conversions.value[0] }]
      : [{ unit: '', ratio: 1 }]
  }
})
</script>

<style scoped>
.unit-display {
  margin-top: 8px;
  padding: 6px 8px;
  border: 1px dashed #dcdfe6;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
}
.unit-display:hover {
  border-color: #409eff;
}
.base-label {
  font-weight: 500;
}
.conv-summary {
  color: #909399;
  margin-left: 8px;
}
.conv-row {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
  font-size: 13px;
}
</style>
