/**
 * 附件相关工具函数
 */

const IMAGE_EXT_RE = /\.(png|jpe?g|gif|bmp|webp|svg|tiff?|ico)(\?|$)/i

/** 判断文件/URL 是否为图片 */
export function isImage(nameOrUrl) {
  return IMAGE_EXT_RE.test(nameOrUrl || '')
}

/** 从 URL 提取文件名（兜底） */
export function extractFileName(url) {
  if (!url) return ''
  try {
    const clean = url.split('?')[0]
    const parts = clean.split('/')
    return decodeURIComponent(parts[parts.length - 1] || url)
  } catch {
    return url
  }
}

/**
 * 解析附件数据为统一格式 [{name, url}]
 * 支持输入:
 *  - JSON 字符串: '[{"name":"a.png","url":"..."}]'
 *  - JSON 字符串(纯 URL 数组): '["url1","url2"]'
 *  - 数组: [{name,url}] 或 ['url1','url2']
 *  - 逗号分隔字符串: 'url1,url2'
 *  - 单个 URL 字符串
 */
export function parseAttachments(val) {
  if (!val) return []
  if (Array.isArray(val)) {
    return val.map(v => typeof v === 'string'
      ? { name: extractFileName(v), url: v }
      : { name: v.name || extractFileName(v.url), url: v.url })
      .filter(a => a.url)
  }
  if (typeof val === 'string') {
    const s = val.trim()
    if (!s) return []
    // 尝试 JSON 解析
    if (s.startsWith('[') || s.startsWith('{')) {
      try {
        const parsed = JSON.parse(s)
        return parseAttachments(parsed)
      } catch { /* fall through */ }
    }
    // 逗号分隔或单 URL
    return s.split(/[,，]/).map(u => u.trim()).filter(Boolean)
      .map(u => ({ name: extractFileName(u), url: u }))
  }
  return []
}
