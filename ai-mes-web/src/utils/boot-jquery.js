// 必须在任何 vue-plugin-hiprint import 之前求值, 把 jQuery 挂到 window,
// 否则 hiprint 模块 evaluation 时拿不到 window.jQuery, $.fn.hidraggable 不会注册.
import $ from 'jquery'
window.jQuery = $
window.$ = $
export default $
