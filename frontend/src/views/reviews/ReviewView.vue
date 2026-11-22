<script setup>
/**
 * ReviewView.vue —— 评论管理（迁移自 admin.html renderReviews() 行 1630-1826）。
 *
 * 列表 + 7 个筛选页签（全部 / 未分析 / 待商家回复 / 正面 / 中性 / 负面 / 投诉）
 * + AI 分析（单条 / 批量，仅 ADMIN）+ AI 回复草稿（草稿会预填到「回复」弹窗）
 * + 商家回复 / 评论详情（用户追评与商家回复按 replyType 区分）/ 删除。
 *
 * 「已回复」不在评论表里，必须额外拉 /admin/reviews/replied-ids 得到集合。
 */
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listReviews, listRepliedIds, listReplies,
  replyReview, deleteReview,
  analyzeReview, batchAnalyzeReviews, draftReviewReply
} from '@/api/reviews'
import { fmtDateTime } from '@/utils/format'
import { usePermission } from '@/composables/usePermission'
import StatusTag from '@/components/common/StatusTag.vue'

const { hasRole } = usePermission()
const isAdmin = computed(() => hasRole('ADMIN'))

const SENTIMENT_BADGE = {
  '正面': { label: '正面', color: 'green' },
  '中性': { label: '中性', color: 'gray' },
  '负面': { label: '负面', color: 'orange' },
  '投诉': { label: '投诉', color: 'red' }
}
const URGENCY_BADGE = {
  '普通':   { label: '普通',   color: 'gray' },
  '需关注': { label: '需关注', color: 'orange' },
  '紧急':   { label: '紧急',   color: 'red' }
}
const FILTERS = [
  { value: 'all',       label: '全部' },
  { value: 'pending',   label: '未分析' },
  { value: 'unreplied', label: '待商家回复' },
  { value: '正面',      label: '正面' },
  { value: '中性',      label: '中性' },
  { value: '负面',      label: '负面' },
  { value: '投诉',      label: '投诉' }
]

const loading = ref(false)
const reviews = ref([])
const repliedSet = ref(new Set())
const filter = ref('all')
const analyzingId = ref(null)
const batchAnalyzing = ref(false)

const filtered = computed(() => {
  const f = filter.value
  if (f === 'all') return reviews.value
  if (f === 'pending') return reviews.value.filter(r => !r.aiSentiment)
  if (f === 'unreplied') return reviews.value.filter(r => !repliedSet.value.has(r.id))
  return reviews.value.filter(r => r.aiSentiment === f)
})

const summary = computed(() => {
  const s = { total: reviews.value.length, urgent: 0, unanalyzed: 0 }
  for (const r of reviews.value) {
    if (!r.aiSentiment) s.unanalyzed++
    if (r.aiUrgency === '紧急') s.urgent++
  }
  return s
})

function ratingColor(rating) {
  return rating >= 4 ? 'green' : rating >= 3 ? 'orange' : 'red'
}

function splitTime(raw) {
  const s = fmtDateTime(raw)
  const i = s.indexOf(' ')
  return i > 0 ? { date: s.substring(0, i), clock: s.substring(i + 1) } : { date: s, clock: '' }
}

async function load() {
  loading.value = true
  try {
    const [list, ids] = await Promise.all([
      listReviews(),
      // 已回复集合单独拉，失败不影响主列表
      listRepliedIds().catch(() => [])
    ])
    reviews.value = list || []
    repliedSet.value = new Set(ids || [])
  } catch (e) {
    ElMessage.error(e.message || '加载评论失败')
  } finally {
    loading.value = false
  }
}

// ────────────── AI 分析 ──────────────
async function doAnalyze(r) {
  analyzingId.value = r.id
  try {
    const data = await analyzeReview(r.id)
    ElMessage.success('分析完成：' + data.sentiment + ' / ' + data.urgency)
    await load()
  } catch (e) {
    ElMessage.error(e.message || '分析失败')
  } finally {
    analyzingId.value = null
  }
}

async function doBatchAnalyze() {
  try {
    await ElMessageBox.confirm('对所有「未分析」评论执行 AI 情感分析（最多 30 条）？', '批量 AI 分析', {
      confirmButtonText: '开始分析', cancelButtonText: '取消', type: 'info'
    })
  } catch (_) {
    return
  }
  batchAnalyzing.value = true
  try {
    const data = await batchAnalyzeReviews()
    ElMessage.success('本次已分析 ' + data.count + ' 条')
    await load()
  } catch (e) {
    ElMessage.error(e.message || '批量分析失败')
  } finally {
    batchAnalyzing.value = false
  }
}

// ────────────── AI 回复草稿 ──────────────
const draftsByReview = ref({})
const draftDialog = ref(false)
const draftList = ref([])

async function doDraft(r) {
  try {
    ElMessage.info('正在生成回复草稿…')
    const data = await draftReviewReply(r.id)
    const drafts = data.drafts || []
    draftsByReview.value[r.id] = drafts
    draftList.value = drafts
    draftDialog.value = true
  } catch (e) {
    ElMessage.error(e.message || '生成草稿失败')
  }
}

async function copyDraft(text) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch (_) {
    ElMessage.error('复制失败，请手动选取')
  }
}

// ────────────── 商家回复 ──────────────
const replyDialog = ref(false)
const replyTarget = ref(null)
const replyText = ref('')
const replying = ref(false)

function openReply(r) {
  replyTarget.value = r
  const drafts = draftsByReview.value[r.id] || []
  replyText.value = drafts.length ? drafts[0] : ''
  replyDialog.value = true
}

async function submitReply() {
  const text = replyText.value.trim()
  if (!text) return ElMessage.warning('回复内容不能为空')
  replying.value = true
  try {
    await replyReview(replyTarget.value.id, text)
    ElMessage.success('商家回复已发布')
    replyDialog.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message || '回复失败')
  } finally {
    replying.value = false
  }
}

// ────────────── 详情 ──────────────
const detailDialog = ref(false)
const detail = ref(null)
const detailReplies = ref([])
const repliesLoading = ref(false)

async function openDetail(r) {
  detail.value = r
  detailReplies.value = []
  detailDialog.value = true
  repliesLoading.value = true
  try {
    detailReplies.value = await listReplies(r.id)
  } catch (e) {
    ElMessage.error(e.message || '加载回复记录失败')
  } finally {
    repliesLoading.value = false
  }
}

async function doDelete(r) {
  try {
    await ElMessageBox.confirm('删除该评论及其追评？', '提示', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning'
    })
  } catch (_) {
    return
  }
  try {
    await deleteReview(r.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    ElMessage.error(e.message || '删除失败')
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <div class="toolbar">
      <div class="filter-group">
        <button
          v-for="f in FILTERS"
          :key="f.value"
          class="btn sm"
          :class="filter === f.value ? 'primary' : 'ghost'"
          @click="filter = f.value"
        >
          {{ f.label }}
        </button>
      </div>

      <button v-if="isAdmin" class="btn primary" :disabled="batchAnalyzing" @click="doBatchAnalyze">
        {{ batchAnalyzing ? '⏳ 分析中…' : '⚡ 批量 AI 分析' }}
      </button>

      <div class="spacer"></div>
      <div class="review-summary">
        共 {{ summary.total }} 条 · 紧急 {{ summary.urgent }} 条 · 未分析 {{ summary.unanalyzed }} 条
      </div>
    </div>

    <div class="panel">
      <div class="panel-head">
        <h3>评论管理</h3>
      </div>

      <div class="panel-body">
        <div class="table-wrap">
          <table class="reviews-table">
            <colgroup>
              <col style="width:4%" /><col style="width:6%" /><col style="width:6.5%" />
              <col style="width:6%" /><col style="width:6%" /><col style="width:6%" />
              <col style="width:17%" /><col style="width:15.5%" /><col style="width:7%" />
              <col style="width:8%" /><col style="width:18%" />
            </colgroup>
            <thead>
              <tr>
                <th>ID</th>
                <th>商品</th>
                <th>用户</th>
                <th>评分</th>
                <th>情感</th>
                <th>紧急度</th>
                <th>内容</th>
                <th>AI 理由</th>
                <th>商家回复</th>
                <th>时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in filtered" :key="r.id">
                <td>{{ r.id }}</td>
                <td>{{ r.productId }}</td>
                <td>{{ r.userName || '-' }}</td>
                <td>
                  <StatusTag :text="r.rating + ' 星'" :color="ratingColor(r.rating)" />
                </td>
                <td>
                  <StatusTag
                    v-if="r.aiSentiment"
                    :text="SENTIMENT_BADGE[r.aiSentiment]?.label || r.aiSentiment"
                    :color="SENTIMENT_BADGE[r.aiSentiment]?.color || 'gray'"
                  />
                  <span v-else class="badge gray">未分析</span>
                </td>
                <td>
                  <StatusTag
                    v-if="r.aiUrgency"
                    :text="URGENCY_BADGE[r.aiUrgency]?.label || r.aiUrgency"
                    :color="URGENCY_BADGE[r.aiUrgency]?.color || 'gray'"
                  />
                  <span v-else class="badge gray">—</span>
                </td>
                <td class="wraptext">
                  <div class="clamp-2" :title="r.content || ''">{{ r.content || '—' }}</div>
                </td>
                <td class="cell-reason">
                  <div class="clamp-2" :title="r.aiReason || ''">{{ r.aiReason || '—' }}</div>
                </td>
                <td>
                  <StatusTag
                    :text="repliedSet.has(r.id) ? '已回复' : '待回复'"
                    :color="repliedSet.has(r.id) ? 'green' : 'gray'"
                  />
                </td>
                <td>
                  <div>{{ splitTime(r.createdAt).date }}</div>
                  <span v-if="splitTime(r.createdAt).clock" class="cell-meta">{{ splitTime(r.createdAt).clock }}</span>
                </td>
                <td>
                  <div class="ops">
                    <button
                      class="btn sm"
                      :class="repliedSet.has(r.id) ? 'ghost' : 'primary'"
                      title="以商家身份回复"
                      @click="openReply(r)"
                    >
                      {{ repliedSet.has(r.id) ? '再回复' : '回复' }}
                    </button>
                    <button
                      v-if="isAdmin"
                      class="btn ghost sm"
                      title="AI 分析"
                      :disabled="analyzingId === r.id"
                      @click="doAnalyze(r)"
                    >
                      🧠
                    </button>
                    <button v-if="isAdmin" class="btn ghost sm" title="AI 起回复" @click="doDraft(r)">✏️</button>
                    <button class="btn ghost sm" title="查看详情" @click="openDetail(r)">👁</button>
                    <button class="btn danger sm" @click="doDelete(r)">删除</button>
                  </div>
                </td>
              </tr>

              <tr v-if="!filtered.length">
                <td colspan="11" class="empty">暂无评论</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 商家回复 -->
    <el-dialog v-model="replyDialog" :title="`回复评论 #${replyTarget?.id || ''}`" width="600px" :close-on-click-modal="false">
      <template v-if="replyTarget">
        <div class="form-row">
          <label>用户</label>
          <div>{{ replyTarget.userName || '-' }} · 评论ID {{ replyTarget.id }}</div>
        </div>
        <div class="form-row">
          <label>用户评价</label>
          <div class="review-quote">{{ replyTarget.content || '' }}</div>
        </div>
        <div class="form-row">
          <label>商家回复</label>
          <textarea
            v-model="replyText"
            class="reply-textarea"
            placeholder="以商家身份回复该评价，会显示在商品详情页评论区…"
          ></textarea>
          <div class="hint">
            {{ (draftsByReview[replyTarget.id] || []).length
              ? '已自动填入刚才生成的 AI 草稿，可直接修改。'
              : '可先用列表里的「✏️ AI 起回复」生成草稿，再回到这里发布。' }}
          </div>
        </div>
      </template>

      <template #footer>
        <button type="button" class="btn ghost" @click="replyDialog = false">取消</button>
        <button type="button" class="btn primary" :disabled="replying" @click="submitReply">发布回复</button>
      </template>
    </el-dialog>

    <!-- AI 回复草稿 -->
    <el-dialog v-model="draftDialog" title="✏️ AI 客服回复草稿" width="600px">
      <template v-if="draftList.length">
        <p class="hint draft-hint">关闭本窗口后点列表里的「回复」，草稿会自动填入回复框，微调即可发布。</p>
        <div v-for="(d, i) in draftList" :key="i" class="form-row draft-row">
          <label>草稿 {{ i + 1 }}</label>
          <div class="draft-inner">
            <textarea readonly class="draft-textarea">{{ d }}</textarea>
            <button type="button" class="btn ghost sm" @click="copyDraft(d)">📋 复制</button>
          </div>
        </div>
      </template>
      <div v-else class="empty">（无草稿）</div>

      <template #footer>
        <button type="button" class="btn ghost" @click="draftDialog = false">关闭</button>
      </template>
    </el-dialog>

    <!-- 评论详情 -->
    <el-dialog v-model="detailDialog" :title="`评论详情 #${detail?.id || ''}`" width="640px">
      <template v-if="detail">
        <div class="form-row">
          <label>用户</label>
          <div>{{ detail.userName || '-' }} · 评论ID {{ detail.id }}</div>
        </div>
        <div class="form-row">
          <label>评分</label>
          <div><StatusTag :text="detail.rating + ' 星'" :color="ratingColor(detail.rating)" /></div>
        </div>
        <div class="form-row">
          <label>评论</label>
          <div class="review-quote">{{ detail.content || '' }}</div>
        </div>
        <div class="form-row">
          <label>情感分析</label>
          <div>
            <StatusTag
              v-if="detail.aiSentiment"
              :text="SENTIMENT_BADGE[detail.aiSentiment]?.label || detail.aiSentiment"
              :color="SENTIMENT_BADGE[detail.aiSentiment]?.color || 'gray'"
            />
            <span v-else class="badge gray">未分析</span>
            {{ detail.aiReason || '' }}
          </div>
        </div>
        <div class="form-row">
          <label>紧急度</label>
          <div>
            <StatusTag
              v-if="detail.aiUrgency"
              :text="URGENCY_BADGE[detail.aiUrgency]?.label || detail.aiUrgency"
              :color="URGENCY_BADGE[detail.aiUrgency]?.color || 'gray'"
            />
            <span v-else class="badge gray">—</span>
          </div>
        </div>
        <div class="form-row">
          <label>分析时间</label>
          <div class="text-muted">{{ fmtDateTime(detail.aiAnalyzedAt) || '—' }}</div>
        </div>
        <div class="form-row detail-replies-row">
          <label>回复记录</label>
          <div class="detail-replies">
            <template v-if="repliesLoading">加载中…</template>
            <template v-else-if="detailReplies.length">
              <div
                v-for="(x, idx) in detailReplies"
                :key="idx"
                class="rv-reply"
                :class="{ merchant: x.replyType === 'MERCHANT' }"
              >
                <div class="rv-reply-head">
                  <StatusTag :text="x.replyType === 'MERCHANT' ? '商家回复' : '用户追评'" :color="x.replyType === 'MERCHANT' ? 'red' : 'gray'" />
                  {{ x.userName || '-' }} · {{ fmtDateTime(x.createdAt) }}
                </div>
                <div class="rv-reply-body">{{ x.content || '' }}</div>
              </div>
            </template>
            <template v-else>
              <span class="text-muted">暂无回复</span>
            </template>
          </div>
        </div>
      </template>

      <template #footer>
        <button type="button" class="btn ghost" @click="detailDialog = false">关闭</button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
/* 筛选按钮组 + 右侧统计（旧页内联样式） */
.filter-group { display: flex; gap: 8px; flex-wrap: wrap; }
.review-summary { color: var(--muted-foreground); font-size: 12px; }

/* 弹窗里的用户评价引用 */
.review-quote { white-space: normal; }

/* 回复框（旧页 textarea 内联样式） */
.reply-textarea {
  width: 100%;
  min-height: 110px;
  padding: 10px 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--bg-50);
  font-family: inherit;
  font-size: 13px;
  color: var(--foreground);
  line-height: 1.5;
  resize: vertical;
}

/* AI 草稿 */
.draft-hint { margin-bottom: 14px; }
.draft-row { align-items: flex-start; }
.draft-inner { flex: 1; display: flex; gap: 10px; align-items: flex-start; }
.draft-textarea {
  flex: 1;
  min-height: 90px;
  padding: 10px 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--bg-100);
  font-family: inherit;
  font-size: 13px;
  color: var(--foreground);
  line-height: 1.5;
  resize: vertical;
}

/* 详情里的回复记录 */
.detail-replies-row { align-items: flex-start; }
.detail-replies { flex: 1; white-space: normal; }
.rv-reply {
  margin-bottom: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--bg-100);
  border-left: 3px solid var(--border-400);
}
.rv-reply.merchant { background: var(--bg-200); border-left-color: var(--primary); }
.rv-reply-head { font-size: 12px; color: var(--muted-foreground); margin-bottom: 3px; }
.rv-reply-body { white-space: pre-wrap; }
</style>
