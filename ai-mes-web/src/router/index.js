/**
 * 路由配置
 * 包含静态路由定义、动态路由生成、路由守卫和路由重置逻辑
 */
import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'
import Layout from '@/layout/index.vue'

/**
 * 静态路由列表
 * 不需要登录即可访问的页面
 */
const constantRoutes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/tv',
    name: 'TvDashboard',
    component: () => import('@/views/tv/index.vue'),
    meta: { title: 'TV大屏' }
  },
  {
    path: '/tv2',
    name: 'TvDashboard2',
    component: () => import('@/views/tv2/index.vue'),
    meta: { title: 'TV大屏 V2' }
  },
  // 模块独立大屏(全部免登录,各自独立全屏样式,统一 /tv/{module} 命名空间)
  { path: '/tv/hub', name: 'TvHub', component: () => import('@/views/tvModules/Hub.vue'), meta: { title: '大屏导航中心' } },
  { path: '/tv/production', name: 'TvProduction', component: () => import('@/views/tvModules/production.vue'), meta: { title: '生产指挥中心' } },
  { path: '/tv/sales', name: 'TvSales', component: () => import('@/views/tvModules/sales.vue'), meta: { title: '销售战情中心' } },
  { path: '/tv/inventory', name: 'TvInventory', component: () => import('@/views/tvModules/inventory.vue'), meta: { title: '库存调度中心' } },
  { path: '/tv/purchase', name: 'TvPurchase', component: () => import('@/views/tvModules/purchase.vue'), meta: { title: '采购作战中心' } },
  { path: '/tv/quality', name: 'TvQuality', component: () => import('@/views/tvModules/quality.vue'), meta: { title: '质量管控中心' } },
  { path: '/tv/finance', name: 'TvFinance', component: () => import('@/views/tvModules/finance.vue'), meta: { title: '财务驾驶舱' } },
  { path: '/tv/outsource', name: 'TvOutsource', component: () => import('@/views/tvModules/outsource.vue'), meta: { title: '外协协同中心' } },
  { path: '/tv/mrp', name: 'TvMrp', component: () => import('@/views/tvModules/mrp.vue'), meta: { title: 'MRP 智能运算中心' } },
  { path: '/tv/workreport', name: 'TvWorkReport', component: () => import('@/views/tvModules/workreport.vue'), meta: { title: '班组报工战报' } },
  { path: '/tv/eam', name: 'TvEam', component: () => import('@/views/tvModules/eam.vue'), meta: { title: 'EAM 设备资产大屏' } },
  // 工位机报工独立页面（不走 Layout，有自己的全屏样式）
  {
    path: '/reportLogin',
    name: 'ReportLogin',
    component: () => import('@/views/reportLogin/index.vue'),
    meta: { title: '工位报工登录' }
  },
  {
    path: '/workstation/task-list',
    name: 'WorkstationTaskList',
    component: () => import('@/views/workstation/task-list/index.vue'),
    meta: { title: '报工任务列表' }
  },
  {
    path: '/workstation/report',
    name: 'WorkstationReport',
    component: () => import('@/views/workstation/report/index.vue'),
    meta: { title: '工位报工' }
  },
  {
    path: '/404',
    component: () => import('@/views/404.vue'),
    meta: { title: '404' }
  }
]

/**
 * 动态路由映射表
 * 使用 Vite 的 import.meta.glob 预加载 views 目录下所有 .vue 文件
 * 用于根据后端返回的 component 路径动态匹配对应组件
 */
const viewModules = import.meta.glob('../views/**/*.vue')

/**
 * 根据后端菜单配置的组件路径，加载对应的 Vue 组件
 * @param {string} component - 组件路径，如 'system/user/index'
 * @returns {Function|undefined} 组件懒加载函数
 */
function loadView(component) {
  const path = `../views/${component}.vue`
  return viewModules[path]
}

/**
 * 根据后端菜单数据递归生成路由配置
 * @param {Array} menus - 后端返回的菜单树
 * @returns {Array} 路由配置数组
 */
function generateRoutes(menus) {
  const routes = []
  for (const menu of menus) {
    // 按钮类型（B）和 H5 专属菜单不需要生成 Web 路由
    if (menu.menuType === 'B') continue
    if (menu.platform === 'h5') continue

    const route = {
      path: menu.path || '',
      // 用路由路径生成唯一的路由名称（去掉前导斜杠，将斜杠替换为短横线）
      name: menu.path ? menu.path.replace(/\//g, '-').replace(/^-/, '') : `menu-${menu.id}`,
      meta: {
        title: menu.menuName,
        icon: menu.icon,
        id: menu.id
      }
    }

    // 菜单类型（M）需要配置组件
    if (menu.component) {
      route.component = loadView(menu.component)
    }

    // 递归处理子菜单
    if (menu.children && menu.children.length > 0) {
      route.children = generateRoutes(menu.children)
      // 目录类型（D）设置重定向到第一个子菜单
      if (menu.menuType === 'D' && route.children.length > 0) {
        route.redirect = route.children[0].path
      }
    }

    routes.push(route)
  }
  return routes
}

/**
 * 将嵌套的路由树展平为一维数组
 * Layout 的 children 只支持一层，所以需要将深层嵌套的路由展平
 * @param {Array} routes - 嵌套的路由配置
 * @param {string} parentPath - 父级路径，用于路径拼接
 * @returns {Array} 展平后的路由数组
 */
function flattenRoutes(routes, parentPath = '') {
  const result = []
  for (const route of routes) {
    // 处理路径拼接：绝对路径直接使用，相对路径拼接父级路径
    let fullPath
    if (route.path.startsWith('/')) {
      fullPath = route.path
    } else {
      fullPath = parentPath ? `${parentPath.replace(/^\//, '')}/${route.path}` : route.path
    }

    // 只有有组件的路由（菜单页面）才添加到 Layout children 中
    if (route.component) {
      // 使用完整路径生成唯一的路由名称，避免不同模块的同名子路径互相覆盖
      // 例如：team/list → "team-list"，order/list → "order-list"
      const uniqueName = fullPath.replace(/^\//, '').replace(/\//g, '-')
      result.push({
        ...route,
        path: fullPath.replace(/^\//, ''), // 去掉前导斜杠（Layout children 使用相对路径）
        name: uniqueName, // 覆盖原始 name，确保路由名称唯一
        children: undefined // Layout children 不需要嵌套子路由
      })
    }
    // 递归展平子路由
    if (route.children) {
      result.push(...flattenRoutes(route.children, fullPath))
    }
  }
  return result
}

/** 创建路由实例 */
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: constantRoutes
})

/** 标记动态路由是否已添加，避免重复添加 */
let dynamicRoutesAdded = false
/** 记录动态添加的路由名称，用于 resetRouter 时移除 */
let addedRouteNames = []

/**
 * 全局前置路由守卫
 * 负责权限验证、动态路由加载
 */
// 无需主系统认证即可直接访问的精确路径
const NO_AUTH_PATHS = ['/login', '/tv', '/tv2', '/reportLogin', '/404']
// 无需主系统认证即可直接访问的路径前缀(模块大屏 /tv/{module} 全部免登录)
const NO_AUTH_PREFIXES = ['/tv/', '/tv-']
// 工位机独立页面（有自己的认证流程，未登录跳报工登录页）
const WORKSTATION_PATHS = ['/workstation/task-list', '/workstation/report']

router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore()

  // 无需认证的页面直接放行
  const isNoAuth = NO_AUTH_PATHS.includes(to.path)
    || NO_AUTH_PREFIXES.some(p => to.path.startsWith(p))
  if (isNoAuth) {
    // 已登录用户访问主登录页时重定向到首页
    if (to.path === '/login' && userStore.token) {
      next('/')
      return
    }
    next()
    return
  }

  // 工位机页面：有 token 则放行，无 token 跳到报工登录页
  if (WORKSTATION_PATHS.some(p => to.path.startsWith(p))) {
    if (!userStore.token) {
      next('/reportLogin')
      return
    }
    next()
    return
  }

  // 主系统：未登录则跳转到登录页
  if (!userStore.token) {
    next(`/login?redirect=${to.fullPath}`)
    return
  }

  // 动态路由尚未加载时，获取用户信息并生成路由
  if (!dynamicRoutesAdded) {
    try {
      // 获取用户信息（包含菜单和权限数据）
      const data = await userStore.fetchUserInfo()
      const menus = data.menus || []
      // 根据后端菜单生成路由配置
      const dynamicRoutes = generateRoutes(menus)
      // 展平路由树以适配 Layout 的 children 结构
      const flatRoutes = flattenRoutes(dynamicRoutes)

      // 构建布局路由，将所有动态页面作为 Layout 的子路由
      // 不在菜单中显示但需要路由的详情页（通过父页面编程式导航访问）
      const hiddenRoutes = [
        {
          path: 'order/create',
          name: 'OrderCreate',
          component: () => import('@/views/order/create/index.vue'),
          meta: { title: '新建订单' }
        },
        {
          path: 'order/edit',
          name: 'OrderEdit',
          component: () => import('@/views/order/edit/index.vue'),
          meta: { title: '编辑订单' }
        },
        {
          path: 'order/detail',
          name: 'OrderDetail',
          component: () => import('@/views/order/detail/index.vue'),
          meta: { title: '订单详情' }
        },
        {
          path: 'order/customer/detail',
          name: 'CustomerDetail',
          component: () => import('@/views/order/customer/detail/index.vue'),
          meta: { title: '客户详情' }
        },
        {
          path: 'team/member',
          name: 'TeamMember',
          component: () => import('@/views/team/member/index.vue'),
          meta: { title: '班组成员' }
        },
        {
          path: 'bom/version',
          name: 'BomVersion',
          component: () => import('@/views/bom/version/index.vue'),
          meta: { title: 'BOM版本对比' }
        },
        {
          path: 'notification/list',
          name: 'NotificationList',
          component: () => import('@/views/notification/list/index.vue'),
          meta: { title: '消息通知' }
        },
        {
          path: 'system/print-template',
          name: 'PrintTemplate',
          component: () => import('@/views/system/printTemplate/index.vue'),
          meta: { title: '打印模板' }
        },
        {
          path: 'aftersale/order',
          name: 'AftersaleOrder',
          component: () => import('@/views/aftersale/order/index.vue'),
          meta: { title: '售后订单' }
        },
        {
          path: 'aftersale/statistics',
          name: 'AftersaleStatistics',
          component: () => import('@/views/aftersale/statistics/index.vue'),
          meta: { title: '售后客户统计' }
        },
        {
          path: 'inventory/request',
          name: 'InventoryRequest',
          component: () => import('@/views/inventory/request/index.vue'),
          meta: { title: '入库申请' }
        },
        {
          path: 'inventory/out-request',
          name: 'InventoryOutRequest',
          component: () => import('@/views/inventory/out-request/index.vue'),
          meta: { title: '出库申请' }
        },
        {
          path: 'order/return',
          name: 'ReturnOrder',
          component: () => import('@/views/order/return/index.vue'),
          meta: { title: '退货管理' }
        },
        // 注意：workstation/* 已作为独立静态路由注册，此处不再重复添加
      ]

      const layoutRoute = {
        path: '/',
        name: 'LayoutRoot',
        component: Layout,
        redirect: '/dashboard',
        children: [
          {
            path: 'dashboard',
            name: 'Dashboard',
            component: () => import('@/views/dashboard/index.vue'),
            meta: { title: '工作台', icon: 'Odometer' }
          },
          ...hiddenRoutes,
          ...flatRoutes
        ]
      }

      // 添加布局路由和 404 兜底路由
      router.addRoute(layoutRoute)
      router.addRoute({ path: '/:pathMatch(.*)*', name: 'CatchAll', redirect: '/404' })
      addedRouteNames = ['LayoutRoot', 'CatchAll']
      dynamicRoutesAdded = true
      // 重新导航到目标路由（因为路由表刚更新，需要重新匹配）
      next({ ...to, replace: true })
    } catch (error) {
      console.error('获取用户信息或生成路由失败:', error)
      // 出错则清除登录状态并跳转到登录页
      userStore.logout()
      next(`/login?redirect=${to.fullPath}`)
    }
  } else {
    next()
  }
})

/**
 * 重置路由
 * 在用户登出时调用，移除所有动态添加的路由
 */
export function resetRouter() {
  addedRouteNames.forEach(name => {
    if (router.hasRoute(name)) {
      router.removeRoute(name)
    }
  })
  addedRouteNames = []
  dynamicRoutesAdded = false
}

export default router
