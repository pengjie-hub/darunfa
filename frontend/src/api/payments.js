/**
 * 支付流水接口 —— 路径与 admin.html renderPayments() 逐字一致。
 *
 *   GET  /admin/payments              支付流水列表
 *   POST /admin/payments/refund/{id}  退款该笔支付
 */
import { api } from './http'

export function listPayments() {
  return api('/admin/payments')
}

export function refundPayment(id) {
  return api(`/admin/payments/refund/${id}`, 'POST')
}
