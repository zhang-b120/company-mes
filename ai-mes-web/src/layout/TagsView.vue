<template>
  <div class="tags-view-container" @click.self="closeMenu">
    <!-- 标签滚动区 -->
    <el-scrollbar ref="scrollbarRef" class="tags-scrollbar">
      <div class="tags-wrap">
        <div
          v-for="tag in tagsStore.visitedViews"
          :key="tag.path"
          class="tag-item"
          :class="{ 'is-active': isActive(tag) }"
          @click="handleClick(tag)"
          @contextmenu.prevent="openMenu(tag, $event)"
        >
          <span class="tag-title">{{ tag.title }}</span>
          <el-icon
            v-if="!tag.affix"
            class="tag-close"
            @click.stop="handleClose(tag)"
          >
            <Close />
          </el-icon>
        </div>
      </div>
    </el-scrollbar>

    <!-- 右键菜单 -->
    <ul
      v-show="menuVisible"
      class="context-menu"
      :style="{ left: menuLeft + 'px', top: menuTop + 'px' }"
    >
      <li @click="handleCloseTag">
        <el-icon><Close /></el-icon> 关闭当前
      </li>
      <li @click="handleCloseOthers">
        <el-icon><Remove /></el-icon> 关闭其他
      </li>
      <li @click="handleCloseRight">
        <el-icon><ArrowRight /></el-icon> 关闭右侧
      </li>
      <li @click="handleCloseAll">
        <el-icon><CircleClose /></el-icon> 关闭全部
      </li>
    </ul>
  </div>
</template>

<script setup>
import { ref, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Close, Remove, ArrowRight, CircleClose } from '@element-plus/icons-vue'
import { useTagsViewStore } from '@/store/tagsView'

const route = useRoute()
const router = useRouter()
const tagsStore = useTagsViewStore()

const scrollbarRef = ref(null)

// ===== 标签操作 =====
function isActive(tag) {
  return tag.path === route.path
}

function handleClick(tag) {
  router.push(tag.fullPath || tag.path)
}

function handleClose(tag) {
  const nextPath = tagsStore.removeView(tag.path)
  // 如果关闭的是当前激活页，跳转到紧邻页
  if (isActive(tag) && nextPath) {
    router.push(nextPath)
  }
  closeMenu()
}

// ===== 路由监听：自动添加/更新标签（基于 fullPath，确保 query 变化也触发更新） =====
watch(
  () => route.fullPath,
  () => {
    if (route.meta?.title) {
      tagsStore.addView(route)
    }
    nextTick(scrollToActive)
  },
  { immediate: true }
)

/** 滚动到当前激活的标签 */
function scrollToActive() {
  const container = scrollbarRef.value?.wrapRef
  if (!container) return
  const activeEl = container.querySelector('.tag-item.is-active')
  if (activeEl) {
    activeEl.scrollIntoView({ behavior: 'smooth', inline: 'nearest', block: 'nearest' })
  }
}

// ===== 右键菜单 =====
const menuVisible = ref(false)
const menuLeft = ref(0)
const menuTop = ref(0)
const selectedTag = ref(null)

function openMenu(tag, e) {
  selectedTag.value = tag
  menuLeft.value = e.clientX
  menuTop.value = e.clientY
  menuVisible.value = true
}

function closeMenu() {
  menuVisible.value = false
}

function handleCloseTag() {
  if (selectedTag.value) handleClose(selectedTag.value)
  closeMenu()
}

function handleCloseOthers() {
  if (selectedTag.value) {
    tagsStore.closeOthers(selectedTag.value.path)
    router.push(selectedTag.value.fullPath || selectedTag.value.path)
  }
  closeMenu()
}

function handleCloseRight() {
  if (selectedTag.value) {
    const currentPath = route.path
    tagsStore.closeRight(selectedTag.value.path)
    // 如果当前页被关闭，跳转到选中标签
    if (!tagsStore.visitedViews.some(v => v.path === currentPath)) {
      router.push(selectedTag.value.fullPath || selectedTag.value.path)
    }
  }
  closeMenu()
}

function handleCloseAll() {
  tagsStore.closeAll()
  router.push('/dashboard')
  closeMenu()
}

// 点击页面其他区域关闭右键菜单
function onDocClick() {
  closeMenu()
}

onMounted(() => document.addEventListener('click', onDocClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocClick))
</script>

<style scoped lang="scss">
.tags-view-container {
  position: relative;
  height: 36px;
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}

.tags-scrollbar {
  height: 100%;
  :deep(.el-scrollbar__wrap) {
    overflow-y: hidden;
  }
}

.tags-wrap {
  display: flex;
  align-items: center;
  height: 36px;
  padding: 0 8px;
  gap: 4px;
  white-space: nowrap;
}

.tag-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 26px;
  padding: 0 10px;
  border-radius: 3px;
  font-size: 12px;
  cursor: pointer;
  border: 1px solid #e4e7ed;
  background: #f4f4f5;
  color: #606266;
  transition: all 0.15s;
  flex-shrink: 0;
  user-select: none;

  &:hover {
    color: #409eff;
    border-color: #c6e2ff;
    background: #ecf5ff;
  }

  &.is-active {
    background: #409eff;
    color: #fff;
    border-color: #409eff;

    &::before {
      content: '';
      display: inline-block;
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: #fff;
      margin-right: 2px;
    }
  }
}

.tag-title {
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tag-close {
  font-size: 11px;
  border-radius: 50%;
  transition: background 0.15s;
  padding: 1px;

  &:hover {
    background: rgba(0, 0, 0, 0.2);
    color: #fff;
  }
}

.is-active .tag-close:hover {
  background: rgba(255, 255, 255, 0.3);
}

/* 右键菜单 */
.context-menu {
  position: fixed;
  z-index: 3000;
  list-style: none;
  margin: 0;
  padding: 4px 0;
  background: #fff;
  border-radius: 4px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.15);
  min-width: 130px;

  li {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 7px 16px;
    font-size: 13px;
    color: #333;
    cursor: pointer;
    white-space: nowrap;

    &:hover {
      background: #f5f7fa;
      color: #409eff;
    }
  }
}
</style>
