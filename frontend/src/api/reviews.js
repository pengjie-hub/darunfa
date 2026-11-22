/**
 * 评论管理接口 —— 路径与 admin.html renderReviews() / AiReviewController 逐字一致。
 *
 *   GET    /admin/reviews                     评论列表
 *   GET    /admin/reviews/replied-ids         已回复评论 ID 集合
 *   GET    /admin/reviews/{id}/replies        回复记录（商家回复 + 用户追评，靠 replyType 区分）
 *   POST   /admin/reviews/{id}/reply          商家回复 { content }
 *   DELETE /admin/reviews/{id}                删除评论及其追评
 *   POST   /admin/ai/reviews/{id}/analyze     AI 情感/紧急度分析
 *   POST   /admin/ai/reviews/batch-analyze    批量分析（最多 30 条未分析）
 *   POST   /admin/ai/reviews/{id}/draft-reply 生成回复草稿 { drafts: [] }
 */
import { api } from './http'

export function listReviews() {
  return api('/admin/reviews')
}

export function listRepliedIds() {
  return api('/admin/reviews/replied-ids')
}

export function listReplies(id) {
  return api(`/admin/reviews/${id}/replies`)
}

export function replyReview(id, content) {
  return api(`/admin/reviews/${id}/reply`, 'POST', { content })
}

export function deleteReview(id) {
  return api(`/admin/reviews/${id}`, 'DELETE')
}

export function analyzeReview(id) {
  return api(`/admin/ai/reviews/${id}/analyze`, 'POST')
}

export function batchAnalyzeReviews() {
  return api('/admin/ai/reviews/batch-analyze', 'POST')
}

export function draftReviewReply(id) {
  return api(`/admin/ai/reviews/${id}/draft-reply`, 'POST')
}
