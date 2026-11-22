<script setup>
/**
 * RiskView.vue —— 风险监控（迁移自 admin.html renderRisk() 行 1417-1598）。
 *
 * 结构：4 格概览（高/中/低/未读）+ 工具栏（等级筛选 / 批量扫描 / 全部标已读）+ 评分表 + 详情弹窗。
 * 三条旧页语义必须保留：
 *   1. 未读行有底色，等级旁有红点 —— 已读状态由服务端返回，前端不自己猜；
 *   2. 「全部标已读」请求失败时也要把按钮恢复可用（结果未知用 summary 兜底，避免卡「处理中」）；
 *   3. 详情弹窗对未读记录提供「标为已读并关闭」，已读记录只给「关闭」。
 */
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { riskSummary, listRiskScores, markRiskRead, markAllRiskRead } from '@/api/risk'
import { batchScanOrders } from '@/api/orders'
import { useAppStore } from '@/stores/app'
import { usePermission } from '@/composables/usePermission'
import { fmtDateTime } from '@/utils/format'
import StatusTag from '@/components/common/StatusTag.vue'

const { hasPerm } = usePermission()
const app = useAppStore()

const loading = ref(false)
const summary = ref({ high: 0, medium: 0, low: 0, unread: 0 })
const scores = ref([])
const level = ref('ALL')
const scanning = ref(false)
const markingAll = ref(false)

function scoreColor(lvl) {
  return lvl === 'HIGH' ? 'var(--destructive)'
    : lvl === 'MEDIUM' ? 'var(--warning-strong)'
    : 'var(--success-500)'
}

async function loadSummary() {
  try {
    summary.value = await riskSummary()
  } catch (e) {
    // 概览失败不阻断列表
  }
}

async function loadScores() {
  loading.value = true
  try {
    scores.value = await listRiskScores(level.value)
  } catch (e) {
    ElMessage.error(e.message || '加载风险评分失败')
  } finally {
    loading.value = false
  }
}

async function refreshAll() {
  await Promise.all([loadSummary(), loadScores()])
  app.refreshBadges()
}

async function doBatchScan() {
  if (!hasPerm('risk:scan')) return ElMessage.warning('无 risk:scan 权限')
  scanning.value = true
  try {
    const data = await batchScanOrders(30)
    ElMessage.success('已扫描 ' + data.scanned + ' 条订单')
    await refreshAll()
  } catch (e) {
    ElMessage.error(e.message || '批量扫描失败')
  } finally {
    scanning.value = false
  }
}

async function doMarkAllRead() {
  if (!hasPerm('risk:view')) return ElMessage.warning('无 risk:view 权限')
  if (markingAll.value || summary.value.unread <= 0) return

  markingAll.value = true
  try {
    const data = await markAllRiskRead('ALL')
    const affected = (data && typeof data.affected === 'number') ? data.affected : 0
    ElMessage.success((data && data.message) ? data.message : ('已把 ' + affected + ' 条标记为已读'))
    await refreshAll()
  } catch (e) {
    ElMessage.error(e.message || '标记失败')
  } finally {
    markingAll.value = false
  }
}

// ────────────── 详情 ──────────────
const detailDialog = ref(false)
const detail = ref(null)

function openDetail(r) {
  detail.value = r
  detailDialog.value = true
}

async function markDetailRead() {
  if (!detail.value) return
  try {
    await markRiskRead(detail.value.id)
    detailDialog.value = false
    await refreshAll()
  } catch (e) {
    ElMessage.error(e.message || '标记失败')
  }
}

async function markOneRead(r) {
  try {
    await markRiskRead(r.id)
    ElMessage.success('已标记为已读')
    await refreshAll()
  } catch (e) {
    ElMessage.error(e.message || '标记失败')
  }
}

onMounted(refreshAll)
</script>

<template>
  <div v-loading="loading">
    <div class="panel">
      <div class="panel-head">
        <h3>风险监控</h3>
      </div>

      <div class="panel-body">
        <div class="hint risk-hint">
          自动对新订单跑风险评分：规则引擎（金额异常/短时多单/地址敏感词/首单大额/凌晨下单）+ DeepSeek 综合判断。
          失败的扫描会回退到规则结果，不影响核心风控。
        </div>

        <div class="toolbar">
          <select v-model="level" @change="loadScores">
            <option value="ALL">全部等级</option>
            <option value="HIGH">🔴 高风险</option>
            <option value="MEDIUM">🟠 中风险</option>
            <option value="LOW">🟢 低风险</option>
          </select>

          <button class="btn primary sm" :disabled="scanning" @click="doBatchScan">
            {{ scanning ? '⏳ 扫描中...' : '🔍 批量扫描最近订单' }}
          </button>

          <button
            class="btn ghost sm"
            :disabled="summary.unread <= 0 || markingAll"
            :title="summary.unread > 0 ? '把当前所有未读风险消息标记为已读（不改分数与等级）' : '当前没有未读风险消息'"
            @click="doMarkAllRead"
          >
            {{ markingAll ? '⏳ 处理中...' : '✅ 全部标为已读' }}
          </button>
        </div>

        <div class="risk-overview">
          <div class="stat high"><div class="num">{{ summary.high || 0 }}</div><div class="lbl">🔴 高风险</div></div>
          <div class="stat medium"><div class="num">{{ summary.medium || 0 }}</div><div class="lbl">🟠 中风险</div></div>
          <div class="stat low"><div class="num">{{ summary.low || 0 }}</div><div class="lbl">🟢 低风险</div></div>
          <div class="stat unread"><div class="num">{{ summary.unread || 0 }}</div><div class="lbl">📩 未读</div></div>
        </div>

        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>评分</th>
                <th>等级</th>
                <th>建议</th>
                <th>来源</th>
                <th>订单编号</th>
                <th>扫描时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in scores" :key="r.id" :style="r.isRead ? '' : 'background:var(--brand-50)'">
                <td><b :style="{ color: scoreColor(r.level) }">{{ r.score }}</b></td>
                <td>
                  <StatusTag :text="r.level === 'HIGH' ? '高风险' : r.level === 'MEDIUM' ? '中风险' : '低风险'" :color="r.level" risk />
                  <span v-if="!r.isRead" class="risk-unread-dot">●</span>
                </td>
                <td>{{ r.suggestedAction || '-' }}</td>
                <td><span class="cell-meta">{{ r.source }}</span></td>
                <td>{{ r.orderNo || ('#' + r.orderId) }}</td>
                <td>{{ fmtDateTime(r.createdAt) }}</td>
                <td>
                  <div class="ops">
                    <button class="btn ghost sm" @click="openDetail(r)">详情</button>
                    <button v-if="!r.isRead" class="btn ghost sm" @click="markOneRead(r)">标已读</button>
                  </div>
                </td>
              </tr>

              <tr v-if="!scores.length">
                <td colspan="7" class="empty">暂无风险评分，点击「批量扫描」即可对最近 30 天订单跑一轮</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 风险评分详情 -->
    <el-dialog v-model="detailDialog" title="风险评分详情" width="640px">
      <template v-if="detail">
        <div class="risk-detail">
          <div class="row">
            <span class="lbl">订单编号</span>
            <span>{{ detail.orderNo || ('#' + detail.orderId) }}</span>
          </div>
          <div class="row">
            <span class="lbl">风险评分</span>
            <span class="detail-score">
              <b :style="{ color: scoreColor(detail.level) }">{{ detail.score }}</b> / 100
              <StatusTag :text="detail.level === 'HIGH' ? '高风险' : detail.level === 'MEDIUM' ? '中风险' : '低风险'" :color="detail.level" risk />
            </span>
          </div>
          <div class="row">
            <span class="lbl">建议处置</span>
            <span><b>{{ detail.suggestedAction || '-' }}</b></span>
          </div>
          <div class="row">
            <span class="lbl">评分来源</span>
            <span>{{ detail.source }}</span>
          </div>
          <div class="row">
            <span class="lbl">规则引擎分</span>
            <span>{{ detail.ruleScore || 0 }}</span>
          </div>

          <div>
            <div class="lbl block-label">命中规则</div>
            <ul class="signal-list">
              <li v-for="(s, idx) in (detail.signals || [])" :key="idx">
                <span class="code">{{ s.code }}</span>
                {{ s.message }}
                <span class="signal-weight">权重 {{ s.weight }}</span>
              </li>
              <li v-if="!detail.signals || !detail.signals.length" class="signal-none">
                ✓ 未命中任何规则信号（建议依赖 LLM 判断）
              </li>
            </ul>
          </div>

          <div v-if="detail.llmSummary" class="llm-box">
            <b>🤖 AI 综合判断</b>（动作：{{ detail.llmAction || '-' }}）<br />
            {{ detail.llmSummary }}
          </div>
        </div>
      </template>

      <template #footer>
        <button type="button" class="btn ghost" @click="detailDialog = false">关闭</button>
        <button v-if="detail && !detail.isRead" type="button" class="btn primary" @click="markDetailRead">
          标为已读并关闭
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
/* 说明文字与概览间距（旧页 hint margin-bottom:14px） */
.risk-hint { margin-bottom: 14px; }

/* 未读红点 */
.risk-unread-dot { color: var(--destructive); font-size: 11px; margin-left: 4px; }

/* 详情弹窗补充（admin-theme 的 .risk-detail 没有的几处） */
.detail-score { display: inline-flex; align-items: center; gap: 8px; }
.block-label { display: block; margin-bottom: 8px; }
.signal-weight { color: var(--muted-foreground); font-size: 11px; margin-left: 6px; }
.signal-none { background: var(--success-50); border-left-color: var(--success-500); color: var(--success-700); }
</style>
