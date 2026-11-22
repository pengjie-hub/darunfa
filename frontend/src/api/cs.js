/**
 * 客服管理接口 —— 路径与 admin.html renderCs() / CsAdminController 逐字一致。
 *
 *   GET  /admin/cs/conversations                会话列表
 *   GET  /admin/cs/conversations/{id}/messages  某会话消息流
 *   POST /admin/cs/conversations/{id}/reply     人工回复 { content }
 *   POST /admin/cs/conversations/{id}/handback  交还 AI 自动应答
 *   GET  /admin/cs/stats                        侧栏未读角标
 */
import { api } from './http'

export function listConversations() {
  return api('/admin/cs/conversations')
}

export function listMessages(id) {
  return api(`/admin/cs/conversations/${id}/messages`)
}

export function sendCsReply(id, content) {
  return api(`/admin/cs/conversations/${id}/reply`, 'POST', { content })
}

export function handBackToAi(id) {
  return api(`/admin/cs/conversations/${id}/handback`, 'POST')
}

export function csStats() {
  return api('/admin/cs/stats')
}
