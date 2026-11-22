/**
 * 风险监控接口 —— 路径与 admin.html renderRisk() 逐字一致。
 *
 *   GET  /admin/risk/summary               概览（high / medium / low / unread）
 *   GET  /admin/risk/scores?level=         评分列表（level 可选 HIGH/MEDIUM/LOW）
 *   PUT  /admin/risk/scores/{id}/read      单条标已读
 *   PUT  /admin/risk/scores/read-all       全部标已读（返回 {affected, remainingUnread, message}）
 *
 * 订单扫描（POST /admin/risk/orders/{id}/scan、batch-scan）在 orders.js 里，与订单页共用。
 */
import { api } from './http'

export function riskSummary() {
  return api('/admin/risk/summary')
}

export function listRiskScores(level) {
  return api('/admin/risk/scores' + (level && level !== 'ALL' ? '?level=' + level : ''))
}

export function markRiskRead(id) {
  return api(`/admin/risk/scores/${id}/read`, 'PUT')
}

export function markAllRiskRead(level = 'ALL') {
  return api('/admin/risk/scores/read-all', 'PUT', { level })
}
