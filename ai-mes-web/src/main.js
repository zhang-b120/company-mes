// 重要: 必须在 vue-plugin-hiprint 任何 import 之前先把 jQuery 挂 window, 否则 hidraggable 不注册
import './utils/boot-jquery'
import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { createPinia } from 'pinia'
import './assets/styles/global.scss'
import { formatDateTime, formatDate, formatMoney, formatSpec } from './utils/format'
import { setupPermissionDirective } from './utils/permission'

// 提前关闭 hiprint 默认 socket 连接 (没用云端打印, 避免控制台 WebSocket 报错)
import { disAutoConnect as _hiprintDisAutoConnect } from 'vue-plugin-hiprint'
try { _hiprintDisAutoConnect() } catch {}

const app = createApp(App)

// 注册全局格式化方法，模板中可直接使用 $fmtDate()、$fmtMoney() 等
app.config.globalProperties.$fmtDate = formatDateTime
app.config.globalProperties.$fmtDateOnly = formatDate
app.config.globalProperties.$fmtMoney = formatMoney
app.config.globalProperties.$fmtSpec = formatSpec

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
setupPermissionDirective(app)
app.mount('#app')
