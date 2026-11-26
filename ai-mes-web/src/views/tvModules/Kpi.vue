<template>
  <div class="tvm-kpi" :class="k">
    <div class="tvm-kpi-label"><span class="ldot" :style="{background:dotColor}"></span>{{ label }}</div>
    <div class="tvm-kpi-value">
      <span v-if="prefix" class="prefix">{{ prefix }}</span>
      <span class="num" :data-k="dataK" data-t="0">0</span>
      <span v-if="unit" class="unit">{{ unit }}</span>
    </div>
    <div :class="['tvm-kpi-trend', trendType]" v-if="trend">{{ trend }}</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  label: String,
  value: { type: [Number, String], default: 0 },
  unit: String,
  prefix: String,
  k: { type: String, default: 'k1' },
  dataK: String,
  trend: String,
  trendType: { type: String, default: 'up' }
})

const DOTS = { k1: '#00d4ff', k2: '#00ff9d', k3: '#ff8c28', k4: '#a855f7', k5: '#ff4466', k6: '#ffd700' }
const dotColor = computed(() => DOTS[props.k] || DOTS.k1)
</script>
