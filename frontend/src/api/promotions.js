/**
 * 促销接口 —— 与 admin.html renderPromotions() 行 2069-2157 一致。
 *
 *   GET    /admin/promotions         促销列表
 *   POST   /admin/promotions         { name, type, threshold, discount, startTime, endTime, imageUrl, description, link, status }
 *   PUT    /admin/promotions/{id}    同上
 *   DELETE /admin/promotions/{id}
 */
import { api } from './http'

export function listPromotions() {
  return api('/admin/promotions')
}

export function createPromotion(body) {
  return api('/admin/promotions', 'POST', body)
}

export function updatePromotion(id, body) {
  return api(`/admin/promotions/${id}`, 'PUT', body)
}

export function deletePromotion(id) {
  return api(`/admin/promotions/${id}`, 'DELETE')
}
