/**
 * v-permission 权限指令
 *
 * 用法示例:
 *   v-permission="'sys:user:add'"                     — 单个权限
 *   v-permission="['sys:user:add', 'sys:user:edit']"  — 多个权限（满足其一即可）
 *
 * 当用户不具备所需权限时，元素会从 DOM 中移除
 */
import { useUserStore } from '@/store/user'

export const permission = {
  mounted(el, binding) {
    const { value } = binding
    const userStore = useUserStore()
    const permissions = userStore.permissions || []

    if (value) {
      const requiredPermissions = Array.isArray(value) ? value : [value]
      const hasPermission = requiredPermissions.some(p => permissions.includes(p))

      if (!hasPermission) {
        el.parentNode && el.parentNode.removeChild(el)
      }
    }
  }
}

/**
 * 注册 v-permission 指令到 Vue 应用实例
 * @param {import('vue').App} app - Vue 应用实例
 */
export function setupPermissionDirective(app) {
  app.directive('permission', permission)
}
