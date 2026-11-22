/**
 * 物流发货接口 —— 路径与 admin.html renderShipments() 逐字一致。
 *
 *   GET /admin/shipments               物流记录列表
 *   PUT /admin/shipments/{id}/status   更新物流状态 { status: SHIPPED/IN_TRANSIT/DELIVERED }
 */
import { api } from './http'

export function listShipments() {
  return api('/admin/shipments')
}

export function updateShipmentStatus(id, status) {
  return api(`/admin/shipments/${id}/status`, 'PUT', { status })
}
