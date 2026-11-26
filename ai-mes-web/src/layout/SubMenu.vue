<template>
  <el-sub-menu :index="'menu-' + menu.id">
    <template #title>
      <el-icon v-if="menu.icon"><component :is="menu.icon" /></el-icon>
      <span>{{ menu.menuName }}</span>
    </template>
    <template v-for="child in visibleChildren" :key="child.id">
      <SubMenu v-if="child.children && child.children.length > 0 && child.menuType === 'D'" :menu="child" :base-path="resolvePath(basePath, child.path)" />
      <el-menu-item v-else-if="child.menuType !== 'B'" :index="resolvePath(basePath, child.path)">
        <el-icon v-if="child.icon"><component :is="child.icon" /></el-icon>
        <template #title>{{ child.menuName }}</template>
      </el-menu-item>
    </template>
  </el-sub-menu>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  menu: { type: Object, required: true },
  basePath: { type: String, default: '' }
})

function resolvePath(basePath, childPath) {
  if (!childPath) return basePath || '/'
  if (childPath.startsWith('/')) return childPath
  const base = basePath ? basePath.replace(/\/$/, '') : ''
  return `${base}/${childPath}`
}

const visibleChildren = computed(() => {
  return (props.menu.children || []).filter(c => c.menuType !== 'B' && c.visible !== 0)
})
</script>
