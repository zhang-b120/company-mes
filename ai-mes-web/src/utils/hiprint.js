/**
 * vue-plugin-hiprint 0.0.60 工具封装 (按官方推荐用法)
 *
 * 关键点 (从文档):
 * - import { hiprint, defaultElementTypeProvider } from 'vue-plugin-hiprint'
 * - 注册默认元素:    hiprint.init({ providers: [new defaultElementTypeProvider()] })
 * - 渲染拖拽元素:    hiprint.PrintElementTypeManager.buildByHtml($('.ep-draggable-item'))
 *   (HTML 里写一组带 class="ep-draggable-item" data-tid="defaultModule.text" 的节点)
 * - 创建模板:        new hiprint.PrintTemplate({ template, settingContainer, paginationContainer })
 * - 设计器入口:      template.design('#hiprint-printTemplate')
 */
// 必须在 vue-plugin-hiprint 之前 import: boot-jquery 通过 side effect 把 jQuery 挂到 window,
// 否则 hiprint 加载时 $.fn.hidraggable 不会注册.
import './boot-jquery'
import { hiprint, defaultElementTypeProvider, disAutoConnect } from 'vue-plugin-hiprint'

let _initialized = false

export function ensureHiprintInit() {
  if (_initialized) return hiprint
  // boot-jquery 已经把 jQuery 挂 window 了
  try { disAutoConnect && disAutoConnect() } catch {}

  // 用官方推荐的 providers 方式注册默认元素 (内置: 文本/长文本/图片/表格/HTML/横线/竖线/矩形/二维码/条形码 等)
  try {
    hiprint.init({ providers: [new defaultElementTypeProvider()] })
  } catch (e) {
    console.error('[hiprint] init 失败', e)
  }

  _initialized = true
  return hiprint
}

export { hiprint }

/**
 * defaultElementTypeProvider 内置的 tid 列表 (按官方源码), 给左侧拖拽 panel 用
 * 用户可以按需精简
 */
export const DEFAULT_TIDS = [
  { group: '常规', items: [
    { tid: 'defaultModule.text',       title: '文本' },
    { tid: 'defaultModule.longText',   title: '长文本' },
    { tid: 'defaultModule.image',      title: '图片' },
    { tid: 'defaultModule.tableCustom',title: '表格' },
    { tid: 'defaultModule.html',       title: 'HTML' }
  ]},
  { group: '辅助', items: [
    { tid: 'defaultModule.hline', title: '横线' },
    { tid: 'defaultModule.vline', title: '竖线' },
    { tid: 'defaultModule.rect',  title: '矩形' },
    { tid: 'defaultModule.oval',  title: '椭圆' }
  ]}
]
