<template>
  <!--
    侧边栏菜单组件
    使用 Element Plus 的 el-menu 组件，配合 router 模式实现菜单点击自动路由跳转
    菜单数据来自后端，支持目录（D）和菜单（M）两种类型渲染，按钮（B）类型不显示
  -->
  <el-menu
    :default-active="activeMenu"
    :collapse="collapse"
    background-color="#001529"
    text-color="#ffffffa6"
    active-text-color="#fff"
    router
    :collapse-transition="false"
  >
    <template v-for="menu in menus" :key="menu.id">
      <!-- 目录类型且有子菜单：渲染为可展开的子菜单组件 -->
      <SubMenu v-if="menu.children && menu.children.length > 0 && menu.menuType === 'D'" :menu="menu" :base-path="menu.path" />
      <!-- 菜单类型：渲染为可点击的菜单项（过滤掉按钮类型） -->
      <el-menu-item v-else-if="menu.menuType !== 'B'" :index="resolvePath('', menu.path)">
        <el-icon v-if="menu.icon"><component :is="menu.icon" /></el-icon>
        <template #title>{{ menu.menuName }}</template>
      </el-menu-item>
    </template>
  </el-menu>
</template>

<script setup>
/**
 * 侧边栏菜单组件
 * 接收菜单树数据和折叠状态，渲染多级菜单
 * 使用 el-menu 的 router 模式，index 对应路由路径
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import SubMenu from './SubMenu.vue'

defineProps({
  /** 菜单树数据（后端返回） */
  menus: { type: Array, default: () => [] },
  /** 侧边栏是否折叠 */
  collapse: { type: Boolean, default: false }
})

/**
 * 拼接父路径和子路径，生成完整路由路径
 * @param {string} basePath - 父级路径
 * @param {string} childPath - 子路径
 * @returns {string} 拼接后的完整路径
 */
function resolvePath(basePath, childPath) {
  // 无子路径时返回父路径
  if (!childPath) return basePath || '/'
  // 绝对路径直接返回
  if (childPath.startsWith('/')) return childPath
  // 拼接相对路径
  const base = basePath ? basePath.replace(/\/$/, '') : ''
  return `${base}/${childPath}`
}

const route = useRoute()
/** 当前激活的菜单项，根据当前路由路径自动高亮 */
const activeMenu = computed(() => route.path)
</script>

<style scoped>
.el-menu {
  border-right: none;
}
</style>
