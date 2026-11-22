/**
 * 小运（AI 运营助手）接口 —— 路径与 admin.html 中 api() 调用逐字一致。
 *
 *   POST   /admin/ai/assistant        单次问答（{ message } → { answer, proposals?, needsClarification? }）
 *   GET    /admin/ai/history          当前登录人最近 20 条对话（时间正序）
 *   GET    /admin/ai/actions/pending  当前登录人未过期 · 待确认的提案
 *   DELETE /admin/ai/memory           清空当前登录人的对话历史 + 长期记忆
 *   GET    /admin/ai/memories         当前登录人的长期记忆
 */
import { api } from './http'

export function askAssistant(message) {
  return api('/admin/ai/assistant', 'POST', { message })
}

export function listHistory() {
  return api('/admin/ai/history')
}

export function listPendingActions() {
  return api('/admin/ai/actions/pending')
}

export function clearMemory() {
  return api('/admin/ai/memory', 'DELETE')
}

export function listMemories() {
  return api('/admin/ai/memories')
}
