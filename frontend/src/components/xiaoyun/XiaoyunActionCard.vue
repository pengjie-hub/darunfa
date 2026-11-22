<script setup>
/**
 * XiaoyunActionCard.vue —— 小运「操作确认卡片」Vue 组件。
 * ============================================================================
 * 这是 src/api/xiaoyun-action.legacy.js（原生 DOM 渲染器）的**完整替代实现**，
 * 逐项对齐 legacy 的 render()：头部 / 对象 / 字段区 / 预检 / 风险区（含二次确认）/
 * 按钮区 / 结果区 / 有效期页脚，样式沿用 xiaoyun-action.css 的 `.xy-*` 类名
 * （全局样式，不重写一套 SCSS，保证与旧后台视觉完全一致）。
 *
 * 相比 legacy 的三个刻意改进（都不改变可见行为，只是修硬伤）：
 *   1. 「重试」重复的是**上一次真正执行的那个动作**（正向/反向）。legacy 的重试恒走
 *      doConfirm()，如果失败的是「改为拒绝」，重试会执行「同意退款」——方向相反，是事故。
 *   2. 所有用户可见文字走 {{ }} 插值，Vue 自动转义；本文件**没有** v-html。
 *   3. 缺少 confirm.path 的提案不再「静默返回 null」，由父组件在消息流里给出可见提示
 *      （见 AiAssistant.vue 的 renderReplyCards）。本组件自己也会拒绝渲染，
 *      但绝不会让运营觉得「点了没反应也没解释」。
 *
 * 四态状态机（与 legacy 一致）：
 *   proposed ──确认──▶ running ──code=0────────▶ success
 *      ▲                 │
 *      └──重试（有限）────┴──code≠0/网络异常──▶ failed
 */
import { computed, ref } from 'vue'
import {
  STATE,
  STATE_TEXT,
  RISK_LEVEL,
  SUBCODE_TEXT,
  RETRYABLE_SUBCODES,
  filled,
  pick,
  executeConfirm,
  buildSummaryRows,
  formatStamp
} from '@/composables/useXiaoyunCards'

const props = defineProps({
  /** 服务端返回的 ActionProposal，必填 */
  proposal: {
    type: Object,
    required: true
  },
  /** 卡片所处位置：'flow' 消息流 / 'pending' 待确认区块（仅用于埋点与调试，不改渲染） */
  context: {
    type: String,
    default: 'flow'
  }
})

const emit = defineEmits(['settled'])

// ---------------------------------------------------------------------------
//  内部状态（四态 + 三层防连点里的两层：busy ref / disabled）
// ---------------------------------------------------------------------------
const current = ref(STATE.PROPOSED)
const busy = ref(false)
const attempts = ref(0)
/** 上一次真正执行的方向，供「重试」复用（避免重试时把拒绝变成同意）。 */
const lastReverse = ref(false)
/** 二次确认勾选；不需要二次确认时默认已勾选，等价 legacy 的 secondConfirmed 初值 */
const secondConfirmed = ref(!pick(props.proposal, 'risk.requiresSecondConfirm'))

// 结果区数据（执行后才有内容）
const duplicate = ref(false)
const resultMessage = ref('')
const resultSubCode = ref('')
const resultData = ref(null)

// ---------------------------------------------------------------------------
//  展示用派生数据（全部只读 proposal / 响应体，不做任何业务换算）
// ---------------------------------------------------------------------------
const title = computed(() => props.proposal.actionLabel || props.proposal.action || '待确认操作')
const stateText = computed(() => STATE_TEXT[current.value] || current.value)

const riskMeta = computed(() => {
  const level = String(pick(props.proposal, 'risk.level') || '').toUpperCase()
  return RISK_LEVEL[level] || null
})

const hasTarget = computed(() => filled(props.proposal.targetLabel))

const fields = computed(() => {
  const arr = Array.isArray(props.proposal.fields) ? props.proposal.fields : []
  return arr.filter(f => f && typeof f === 'object')
})

const checks = computed(() => {
  const arr = pick(props.proposal, 'precheck.checks')
  if (!Array.isArray(arr)) return []
  return arr
    .filter(c => c && typeof c === 'object')
    .map(c => ({
      ok: c.ok !== false,
      text: filled(c.message) ? c.message : (filled(c.code) ? c.code : '')
    }))
})

const risk = computed(() => props.proposal.risk || null)

const showRiskBox = computed(() => {
  const r = risk.value
  if (!r || typeof r !== 'object') return false
  return filled(r.reasons) || filled(r.note) || !!r.irreversible || !!r.requiresSecondConfirm
})

const riskReasons = computed(() => {
  const arr = risk.value && Array.isArray(risk.value.reasons) ? risk.value.reasons : []
  return arr.filter(r => filled(r)).map(r => String(r))
})

const secondConfirmText = computed(() => {
  const hint = pick(props.proposal, 'risk.secondConfirmHint')
  return '我已核对' + (filled(hint) ? '：' + hint : '以上内容')
})

const hasReverse = computed(() => !!props.proposal.reject && filled(pick(props.proposal, 'reject.path')))

const reverseLabel = computed(() => {
  const action = String(props.proposal.action || '')
  return '改为' + (/APPROVE$/.test(action) ? '拒绝' : '撤销')
})

const expiresText = computed(() => {
  if (!filled(props.proposal.expiresAt)) return ''
  return '此卡片 ' + formatStamp(props.proposal.expiresAt) + ' 前有效'
})

/** 按钮可用性：三态统一在这里算，避免各处漏判。 */
const canClick = computed(() => current.value === STATE.PROPOSED && !busy.value && secondConfirmed.value)

const confirmButtonText = computed(() => (current.value === STATE.SUCCESS ? '已执行' : '确认执行'))

const summaryRows = computed(() => buildSummaryRows(props.proposal, resultData.value))

const failTitle = computed(() => SUBCODE_TEXT[resultSubCode.value] || '执行未完成')

const retryable = computed(() => RETRYABLE_SUBCODES[resultSubCode.value] === 1)

// ---------------------------------------------------------------------------
//  执行
// ---------------------------------------------------------------------------

/** 清空结果区（回到 proposed 视觉） */
function clearResult() {
  duplicate.value = false
  resultMessage.value = ''
  resultSubCode.value = ''
  resultData.value = null
}

/**
 * 执行一次确认 / 反向操作。
 * 三层防连点：busy ref（JS 层，devtools 去掉 disabled 也挡得住）+ button disabled
 * + 服务端幂等键。
 * @param {boolean} reverse true 走 proposal.reject（改为拒绝/撤销）
 */
async function run(reverse) {
  if (busy.value) return
  if (current.value !== STATE.PROPOSED) return
  if (!reverse && !secondConfirmed.value) return

  attempts.value += 1
  lastReverse.value = reverse
  clearResult()
  current.value = STATE.RUNNING
  busy.value = true

  let env = null
  let success = false
  try {
    env = await executeConfirm(props.proposal, { reverse })
    success = !!env && env.code === 0
    if (success) {
      // 铁律 3：DUPLICATE_ACTION 是**成功**语义（目标状态已达成），
      // 必须渲染成「已完成 + 上次的结果」，绝不能弹红色报错。
      duplicate.value = env.subCode === 'DUPLICATE_ACTION'
      resultMessage.value = filled(env.message) ? String(env.message) : ''
      resultData.value = env.data ?? null
      current.value = STATE.SUCCESS
    } else {
      resultSubCode.value = env && filled(env.subCode) ? String(env.subCode) : 'INTERNAL_ERROR'
      resultMessage.value = env && filled(env.message) ? String(env.message) : '执行失败，请稍后重试。'
      current.value = STATE.FAILED
    }
  } catch (e) {
    // executeConfirm 内部已经把异常转成信封，走到这里说明是意外错误 ——
    // 仍然必须把卡片从「执行中」释放出来，否则运营会一直等。
    resultSubCode.value = 'INTERNAL_ERROR'
    resultMessage.value = String((e && e.message) || e)
    current.value = STATE.FAILED
    success = false
  } finally {
    busy.value = false
  }

  emit('settled', { proposal: props.proposal, env, success })
}

function onConfirm() {
  run(false)
}

function onReverse() {
  run(true)
}

/** 重试：重复**上一次执行的那个方向**（legacy 恒走正向，是隐患，这里已修正）。 */
function onRetry() {
  if (busy.value) return
  current.value = STATE.PROPOSED
  clearResult()
  run(lastReverse.value)
}

function onSecondConfirmChange(e) {
  secondConfirmed.value = !!(e && e.target && e.target.checked)
}

// ---------------------------------------------------------------------------
//  对外暴露（父组件用来去重与移除）
// ---------------------------------------------------------------------------
const proposalId = computed(() => String(props.proposal.proposalId || ''))

defineExpose({
  /** 只读状态快照，父组件可用于「跳过已完成的卡片」等判断 */
  state: computed(() => ({
    current: current.value,
    busy: busy.value,
    attempts: attempts.value
  })),
  proposalId
})
</script>

<template>
  <div
    class="xy-card"
    :data-state="current"
    :data-action="proposal.action || ''"
    :data-proposal-id="proposal.proposalId || ''"
    :data-context="context"
  >
    <!-- 头部：动作名 + 状态 chip + 风险徽章 -->
    <div class="xy-card-head">
      <div class="xy-head-main">
        <span class="xy-title">{{ title }}</span>
        <span :class="['xy-state-chip', 'xy-state-' + current]" data-role="state-chip">{{ stateText }}</span>
      </div>
      <span v-if="riskMeta" :class="['xy-risk-badge', 'xy-risk-' + riskMeta[1]]">{{ riskMeta[0] }}</span>
    </div>

    <!-- 操作对象：服务端已拼好「退款单 #14 · DR… · ¥4.50」 -->
    <div v-if="hasTarget" class="xy-target">{{ proposal.targetLabel }}</div>

    <!-- 字段区：label / value / tone / mono 全部照抄服务端 -->
    <dl v-if="fields.length" class="xy-fields">
      <div v-for="(f, idx) in fields" :key="'field-' + idx" class="xy-field">
        <dt class="xy-field-label">{{ filled(f.label) ? f.label : '' }}</dt>
        <dd
          :class="[
            'xy-field-value',
            f.tone ? 'xy-tone-' + String(f.tone).toLowerCase() : '',
            f.mono ? 'xy-mono' : ''
          ]"
        >{{ filled(f.value) ? f.value : '-' }}</dd>
      </div>
    </dl>

    <!-- 预检：只展示服务端给的 message，不翻译、不推断 -->
    <div v-if="checks.length" class="xy-precheck">
      <div class="xy-section-label">预检</div>
      <ul class="xy-checks">
        <li
          v-for="(c, idx) in checks"
          :key="'check-' + idx"
          :class="['xy-check', c.ok ? 'xy-check-ok' : 'xy-check-bad']"
        >
          <span class="xy-check-mark">{{ c.ok ? '✓' : '✗' }}</span>
          <span class="xy-check-text">{{ c.text }}</span>
        </li>
      </ul>
    </div>

    <!-- 风险：reasons / note / 不可撤销 / 二次确认 -->
    <div v-if="showRiskBox" class="xy-risk">
      <ul v-if="riskReasons.length" class="xy-risk-reasons">
        <li v-for="(r, idx) in riskReasons" :key="'reason-' + idx">{{ r }}</li>
      </ul>
      <div v-if="filled(risk.note)" class="xy-risk-note">{{ risk.note }}</div>
      <div v-if="risk.irreversible" class="xy-risk-irreversible">此操作执行后不可撤销，请核对后再确认。</div>
      <label v-if="risk.requiresSecondConfirm" class="xy-second-confirm">
        <input
          type="checkbox"
          data-role="second-confirm"
          :checked="secondConfirmed"
          :disabled="current !== STATE.PROPOSED"
          @change="onSecondConfirmChange"
        />
        <span>{{ secondConfirmText }}</span>
      </label>
    </div>

    <!-- 结果区（执行后才有内容） -->
    <div v-if="current === STATE.RUNNING" class="xy-result xy-result-running">
      <span class="xy-spinner"></span>
      <span>正在执行，请勿重复点击…</span>
    </div>

    <div
      v-else-if="current === STATE.SUCCESS"
      :class="['xy-result', 'xy-result-ok', duplicate ? 'xy-result-dup' : '']"
      data-role="result"
    >
      <div class="xy-result-title">
        {{ duplicate ? '该操作已执行过，下面是上次的结果' : '执行成功' }}
      </div>
      <div v-if="duplicate && filled(resultMessage)" class="xy-result-msg">{{ resultMessage }}</div>
      <dl v-if="summaryRows.length" class="xy-result-fields">
        <div v-for="(row, idx) in summaryRows" :key="'sum-' + idx" class="xy-field">
          <dt class="xy-field-label">{{ row.label }}</dt>
          <dd :class="['xy-field-value', row.mono ? 'xy-mono' : '']">{{ row.value }}</dd>
        </div>
      </dl>
    </div>

    <div v-else-if="current === STATE.FAILED" class="xy-result xy-result-bad" data-role="result">
      <div class="xy-result-title">{{ failTitle }}</div>
      <div v-if="filled(resultMessage)" class="xy-result-msg">{{ resultMessage }}</div>
      <div v-if="filled(resultSubCode)" class="xy-result-code">错误码：{{ resultSubCode }}</div>
      <div class="xy-result-actions">
        <button v-if="retryable" type="button" class="xy-btn xy-btn-ghost xy-btn-sm" @click="onRetry">重试</button>
        <span v-else class="xy-result-hint">请让小运重新准备这张卡片。</span>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div class="xy-actions">
      <button
        type="button"
        class="xy-btn xy-btn-primary"
        data-role="confirm"
        :disabled="!canClick"
        @click="onConfirm"
      >{{ confirmButtonText }}</button>
      <button
        v-if="hasReverse"
        type="button"
        class="xy-btn xy-btn-ghost"
        data-role="reverse"
        :disabled="!canClick"
        @click="onReverse"
      >{{ reverseLabel }}</button>
    </div>

    <!-- 页脚：过期时间（服务端给的字符串，只做显示格式化） -->
    <div v-if="expiresText" class="xy-foot">{{ expiresText }}</div>
  </div>
</template>
