/**
 * RBAC 接口 —— 与 admin.html renderRoles()/renderPermissions()（行 1196-1398）一致。
 *
 *   GET    /admin/rbac/roles
 *   POST   /admin/rbac/roles                      { code, name, description, dataScope, sortOrder, status }
 *   PUT    /admin/rbac/roles/{id}                 同上（code 不可改）
 *   DELETE /admin/rbac/roles/{id}
 *   GET    /admin/rbac/roles/{id}/permissions     返回权限 id 数组
 *   PUT    /admin/rbac/roles/{id}/permissions     { permissionIds: number[] }
 *   GET    /admin/rbac/permissions
 *   POST   /admin/rbac/permissions                { code, name, module, type, sortOrder, status }
 *   PUT    /admin/rbac/permissions/{id}           同上（code 不可改）
 *   DELETE /admin/rbac/permissions/{id}
 */
import { api } from './http'

export function listRoles() {
  return api('/admin/rbac/roles')
}

export function createRole(body) {
  return api('/admin/rbac/roles', 'POST', body)
}

export function updateRole(id, body) {
  return api(`/admin/rbac/roles/${id}`, 'PUT', body)
}

export function deleteRole(id) {
  return api(`/admin/rbac/roles/${id}`, 'DELETE')
}

export function getRolePermissions(id) {
  return api(`/admin/rbac/roles/${id}/permissions`)
}

export function assignRolePermissions(id, permissionIds) {
  return api(`/admin/rbac/roles/${id}/permissions`, 'PUT', { permissionIds })
}

export function listPermissions() {
  return api('/admin/rbac/permissions')
}

export function createPermission(body) {
  return api('/admin/rbac/permissions', 'POST', body)
}

export function updatePermission(id, body) {
  return api(`/admin/rbac/permissions/${id}`, 'PUT', body)
}

export function deletePermission(id) {
  return api(`/admin/rbac/permissions/${id}`, 'DELETE')
}
