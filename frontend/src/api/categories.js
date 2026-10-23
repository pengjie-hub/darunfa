/**
 * 分类接口 —— 与 admin.html renderCategories() 行 2013-2066 一致。
 *
 *   GET    /admin/categories           全部分类（一级 parentId=0，二级 parentId=一级 id）
 *   POST   /admin/categories           { name, parentId, sortOrder }
 *   PUT    /admin/categories/{id}      { name, parentId, sortOrder, status }
 *   DELETE /admin/categories/{id}
 */
import { api } from './http'

export function listCategories() {
  return api('/admin/categories')
}

export function createCategory(body) {
  return api('/admin/categories', 'POST', body)
}

export function updateCategory(id, body) {
  return api(`/admin/categories/${id}`, 'PUT', body)
}

export function deleteCategory(id) {
  return api(`/admin/categories/${id}`, 'DELETE')
}
