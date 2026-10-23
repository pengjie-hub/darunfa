/**
 * 通用格式化 / 工具函数 —— 从 admin.html 提取。
 */

/** 金额格式化：保留 2 位小数，带 ¥ 前缀 */
export function fmtMoney(v) {
  if (v === null || v === undefined || Number.isNaN(v)) return '¥0.00'
  return '¥' + Number(v).toFixed(2)
}

/** 千/万缩写（图表数字紧凑显示） */
export function fmtK(v) {
  if (v >= 10000) return (v / 10000).toFixed(1) + '万'
  if (v >= 1000) return (v / 1000).toFixed(1) + 'k'
  return Number(v).toFixed(0)
}

/** 时间格式化：后端返回的 LocalDateTime / Date → 'yyyy-MM-dd HH:mm:ss' */
export function fmtDateTime(raw) {
  if (!raw) return '-'
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return String(raw)
  const pad = n => n < 10 ? '0' + n : n
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/** 短日期：'MM-dd' */
export function fmtShortDate(raw) {
  if (!raw) return '-'
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return String(raw).slice(5, 10)
  const pad = n => n < 10 ? '0' + n : n
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** 把 status 枚举值映射到 {label, color}（从 constants 的 * 映射表里查） */
export function statusOf(map, status) {
  return map[status] || { label: '未知', color: 'gray' }
}

/** 转义 HTML（v-html 时用，双保险；Vue 模板里自动转义不用） */
export function esc(s) {
  if (s === null || s === undefined) return ''
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/** 读 CSS 变量（D3 图表用，因为 d3.scaleOrdinal 里不能直接写 'var(--xx)'，需要 resolve 成实际色值） */
export function cssVar(name) {
  if (typeof getComputedStyle === 'undefined') return name
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || name
}

/** themeColor —— 如果是 CSS var(...) 引用则 resolve 成实际值，否则原样返回 */
export function themeColor(raw) {
  const s = String(raw)
  return s.startsWith('var(') ? cssVar(s) : s
}
