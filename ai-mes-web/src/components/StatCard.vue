<!--
  统计卡片组件
  用于工作台、概览页等场景展示统计数字
  用法:
    <StatCard title="今日订单" :value="128" icon="Document" color="#409eff" clickable @click="goToOrders" />
-->
<template>
  <el-card
    shadow="hover"
    :class="['stat-card', { 'stat-card--clickable': clickable }]"
    @click="handleClick"
  >
    <div class="stat-card__body">
      <!-- 左侧圆形图标区域 -->
      <div class="stat-card__icon" :style="{ background: color }">
        <el-icon :size="24"><component :is="icon" /></el-icon>
      </div>
      <!-- 右侧标题 + 数值 -->
      <div class="stat-card__content">
        <div class="stat-card__title">{{ title }}</div>
        <div class="stat-card__value">{{ value }}</div>
      </div>
    </div>
  </el-card>
</template>

<script setup>
/**
 * @prop {string}         title     - 卡片标题
 * @prop {string|number}  value     - 统计数值
 * @prop {string}         icon      - Element Plus 图标组件名称
 * @prop {string}         color     - 图标背景色
 * @prop {boolean}        clickable - 是否可点击（鼠标变手型）
 */
defineProps({
  title: {
    type: String,
    default: ''
  },
  value: {
    type: [String, Number],
    default: 0
  },
  icon: {
    type: String,
    default: 'Document'
  },
  color: {
    type: String,
    default: '#0066FF'
  },
  clickable: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['click'])

/** 卡片点击事件 */
function handleClick() {
  emit('click')
}
</script>

<style scoped lang="scss">
.stat-card {
  &--clickable {
    cursor: pointer;
    transition: transform 0.2s;
    &:hover {
      transform: translateY(-2px);
    }
  }

  :deep(.el-card__body) {
    padding: 20px;
  }
}

.stat-card__body {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-card__icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.stat-card__content {
  flex: 1;
  min-width: 0;
}

.stat-card__title {
  font-size: 13px;
  color: #909399;
  margin-bottom: 4px;
}

.stat-card__value {
  font-size: 28px;
  font-weight: bold;
  color: #333;
  line-height: 1.2;
}
</style>
