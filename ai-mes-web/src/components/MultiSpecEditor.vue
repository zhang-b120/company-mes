<template>
  <div class="multi-spec-editor">
    <el-radio-group v-model="mode" size="small" @change="handleModeChange">
      <el-radio-button value="single">单规格</el-radio-button>
      <el-radio-button value="multi">多规格</el-radio-button>
    </el-radio-group>

    <!-- 单规格 -->
    <template v-if="mode === 'single'">
      <el-input v-model="singleSpec" placeholder="请输入规格型号" style="margin-top: 8px" @input="emitSingle" />
    </template>

    <!-- 多规格 -->
    <template v-else>
      <div class="spec-display" v-if="dimensions.length > 0" @click="dialogVisible = true">
        <el-tag v-for="(dim, idx) in dimensions" :key="idx" size="small" type="info" style="margin: 2px 4px 2px 0">
          {{ dim.name }}: {{ dim.values.join('/') }}
        </el-tag>
      </div>
      <el-button v-else type="primary" link size="small" style="margin-top: 8px"
        @click="dialogVisible = true">点击配置多规格</el-button>

      <!-- 编辑弹窗 -->
      <el-dialog v-model="dialogVisible" title="多规格编辑" width="600px" append-to-body destroy-on-close>
        <div v-for="(dim, idx) in editDimensions" :key="idx" class="dim-row">
          <el-input v-model="dim.name" placeholder="维度名称（如：颜色）" style="width: 140px" />
          <el-input v-model="dim.valuesStr" placeholder="多个值用逗号分隔（如：红,蓝,绿）" style="flex: 1; margin: 0 8px" />
          <el-button type="danger" :icon="Delete" circle size="small" @click="editDimensions.splice(idx, 1)" />
        </div>
        <el-button type="primary" link size="small" style="margin-top: 8px"
          @click="editDimensions.push({ name: '', valuesStr: '' })">
          + 添加维度
        </el-button>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="confirmDimensions">确定</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { Delete } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])

const mode = ref('single')
const singleSpec = ref('')
const dimensions = ref([])
const dialogVisible = ref(false)
const editDimensions = ref([])

function parseValue(val) {
  if (!val) { mode.value = 'single'; singleSpec.value = ''; dimensions.value = []; return }
  try {
    const parsed = JSON.parse(val)
    if (Array.isArray(parsed) && parsed.length > 0 && parsed[0].name) {
      mode.value = 'multi'
      // 兼容两种格式: {name, values:[...]} 和 {name, value:"..."}
      dimensions.value = parsed.map(d => ({
        name: d.name,
        values: d.values || (d.value ? [d.value] : [])
      }))
      singleSpec.value = ''
      return
    }
  } catch {}
  mode.value = 'single'
  singleSpec.value = val
  dimensions.value = []
}

onMounted(() => parseValue(props.modelValue))
watch(() => props.modelValue, (v) => parseValue(v))

function handleModeChange(m) {
  if (m === 'single') {
    emit('update:modelValue', singleSpec.value)
  } else {
    editDimensions.value = dimensions.value.length > 0
      ? dimensions.value.map(d => ({ name: d.name, valuesStr: (d.values || [d.value]).filter(Boolean).join(',') }))
      : [{ name: '', valuesStr: '' }]
  }
}

function emitSingle() {
  emit('update:modelValue', singleSpec.value)
}

function confirmDimensions() {
  const result = editDimensions.value
    .filter(d => d.name.trim() && d.valuesStr.trim())
    .map(d => ({ name: d.name.trim(), values: d.valuesStr.split(',').map(v => v.trim()).filter(Boolean) }))
  dimensions.value = result
  emit('update:modelValue', result.length > 0 ? JSON.stringify(result) : '')
  dialogVisible.value = false
}

watch(() => dialogVisible.value, (v) => {
  if (v) {
    editDimensions.value = dimensions.value.length > 0
      ? dimensions.value.map(d => ({ name: d.name, valuesStr: (d.values || [d.value]).filter(Boolean).join(',') }))
      : [{ name: '', valuesStr: '' }]
  }
})
</script>

<style scoped>
.spec-display {
  margin-top: 8px;
  cursor: pointer;
  padding: 6px 8px;
  border: 1px dashed #dcdfe6;
  border-radius: 4px;
  min-height: 32px;
}
.spec-display:hover {
  border-color: #409eff;
}
.dim-row {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}
</style>
