/**
 * 订单相关接口 —— 与 admin.html renderOrders() 里调用的路径逐字一致。
 *
 *   GET  /admin/orders?status=            订单列表（status 可选）
 *   PUT  /admin/orders/{id}/ship          发货（需 order:ship）
 *   POST /admin/risk/orders/{id}/scan     单条风险扫描（需 risk:scan）
 *   POST /admin/risk/orders/batch-scan    批量风险扫描（需 risk:scan，limit 默认 30）
 */
import { api } from './http'

export function listOrders(status) {
  return api('/admin/orders' + (status ? '?status=' + status : ''))
}

export function shipOrder(id, { company, trackingNo, remark }) {
  return api(`/admin/orders/${id}/ship`, 'PUT', { company, trackingNo, remark })
}

export function scanOrder(id) {
  return api(`/admin/risk/orders/${id}/scan`, 'POST')
}

export function batchScanOrders(limit = 30) {
  return api(`/admin/risk/orders/batch-scan?limit=${limit}`, 'POST')
}
