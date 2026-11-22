/**
 * 会员管理接口 —— 路径与 admin.html renderMembers() 逐字一致。
 *
 *   GET /admin/users                用户列表
 *   PUT /admin/users/{id}/status    启用/禁用 { status: 1 | 0 }
 *   PUT /admin/users/{id}/role      切换角色 { role: USER/OPS/CS/ADMIN }
 */
import { api } from './http'

export function listUsers() {
  return api('/admin/users')
}

export function setUserStatus(id, status) {
  return api(`/admin/users/${id}/status`, 'PUT', { status })
}

export function setUserRole(id, role) {
  return api(`/admin/users/${id}/role`, 'PUT', { role })
}
