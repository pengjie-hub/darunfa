/**
 * 退款管理接口 —— 路径与 admin.html renderRefunds() 逐字一致。
 *
 *   GET  /admin/refunds?status=          退款申请列表（status 可选：PENDING/APPROVED/REJECTED）
 *   GET  /admin/refunds/pending-count    侧栏未读角标
 *   POST /admin/refunds/{id}/approve     同意退款（回补库存 / 返券 / 支付置已退款）
 *   POST /admin/refunds/{id}/reject      拒绝退款（订单恢复到 previousStatus）
 */
import { api } from './http'

export function listRefunds(status) {
  return api('/admin/refunds' + (status ? '?status=' + status : ''))
}

export function pendingRefundCount() {
  return api('/admin/refunds/pending-count')
}

export function approveRefund(id) {
  return api(`/admin/refunds/${id}/approve`, 'POST')
}

export function rejectRefund(id, reason) {
  return api(`/admin/refunds/${id}/reject`, 'POST', { reason })
}
