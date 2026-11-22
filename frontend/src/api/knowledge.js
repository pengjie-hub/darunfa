/**
 * AI 知识库接口 —— 与 admin.html renderKnowledge()（行 2252-2470）一致。
 *
 *   GET  /admin/ai/knowledge/status                    片段数/维度/门槛/闸门规则等
 *   GET  /admin/ai/knowledge/search?q=&scope=          检索测试（scope: all | customer | ops）
 *   POST /admin/ai/knowledge/rebuild                   重建（返回 segmentCount / dimension / elapsedMs）
 *   GET  /admin/ai/knowledge/source                    知识库原文（Markdown 字符串）
 */
import { api } from './http'

export function knowledgeStatus() {
  return api('/admin/ai/knowledge/status')
}

export function searchKnowledge(q, scope = 'customer') {
  return api(`/admin/ai/knowledge/search?scope=${encodeURIComponent(scope)}&q=${encodeURIComponent(q)}`)
}

export function rebuildKnowledge() {
  return api('/admin/ai/knowledge/rebuild', 'POST')
}

export function knowledgeSource() {
  return api('/admin/ai/knowledge/source')
}
