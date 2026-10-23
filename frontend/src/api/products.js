import { api } from '@/api/http'

// —— 商品 CRUD ——
export function listProducts() {
  return api('/admin/products')
}

/** 低库存商品（库存 ≤ LOW_STOCK_THRESHOLD），库存管理页专用 */
export function listLowStock() {
  return api('/admin/products/low-stock')
}
export function createProduct(body) {
  return api('/admin/products', 'POST', body)
}
export function updateProduct(id, body) {
  return api(`/admin/products/${id}`, 'PUT', body)
}
export function deleteProduct(id) {
  return api(`/admin/products/${id}`, 'DELETE')
}
export function adjustStock(id, delta) {
  return api(`/admin/products/${id}/stock`, 'PUT', { delta })
}

// —— 分类列表（编辑商品时下拉框）——
export function listCategories() {
  return api('/admin/categories')
}
