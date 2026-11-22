/**
 * useXiaoyunCards —— 小运「操作确认卡片」的共享逻辑层。
 * ============================================================================
 * 本文件是 src/api/xiaoyun-action.legacy.js（原生 DOM 渲染器）的**逻辑移植版本**：
 *   · 常量表（STATE / STATE_TEXT / RISK_LEVEL / SUBCODE_TEXT / RETRYABLE_SUBCODES /
 *     SUMMARY_KEYS）逐字照搬，保证文案与旧后台一致；
 *   · 请求通道 executeConfirm() 复刻 legacy 的 request()+stripApiPrefix()，
 *     但改用全站 http 的 raw 模式（api(path, method, body, headers, true)），
 *     因此 token / 401 / 403 / 超时行为与其它页面完全一致；
 *   · 结果摘要 buildSummaryRows() 复刻 legacy 的 buildSummary()；
 *   · 渲染层（DOM 操作）全部丢弃，交给 XiaoyunActionCard.vue / XiaoyunPendingPanel.vue。
 *
 * ---------------------------------------------------------------------------
 * 【铁律 1】卡片上所有业务文字只能来自 proposal / 响应体，前端一个字都不许自己算
 * ---------------------------------------------------------------------------
 * 展示金额 → 只打印 proposal.fields[].value（服务端已格式化成「¥4.50」）
 * 展示风险 → 只打印 risk.level / risk.reasons / risk.note
 * 展示预检 → 只打印 precheck.checks[].message
 * 唯一的界面文案是「chrome」（按钮名、状态名、字段名兜底），不是业务数据。
 *
 * 【铁律 2】四态状态机，执行中必须锁死按钮（由组件内的 busy ref + disabled 双保险）
 *
 * 【铁律 3】DUPLICATE_ACTION 不是错误 —— 见 SUBCODE_TEXT 与卡片组件的成功分支
 *
 * ---------------------------------------------------------------------------
 * 【去重登记表】同一 proposalId 全页只渲染一次
 * ---------------------------------------------------------------------------
 * 消息流（AiAssistant.vue）与待确认区块（XiaoyunPendingPanel.vue）都可能拿到同一份
 * proposal（服务端 pending 列表与当轮 proposals 是同一批数据），不去重就会同屏出现两张
 * 一模一样的卡片，运营不知道点哪个、点了一个另一个还显示「待确认」。
 *
 * 约定（唯一事实来源在这里，两边都只调用这几个函数）：
 *   · markRendered(key)：卡片已插入消息流时登记；
 *   · isRendered(key)：待确认区块过滤时查询（已在消息流出现的不再重复）；
 *   · resetRendered()：清空会话（resetDialog）时重置，让下一轮重新可用。
 * 待确认区块本身**从不登记**——它的职责是「卡片丢了还能在这里找回」，一旦登记就会把
 * 找回入口自己藏起来；它只做「已流内渲染的跳过」这一件事。
 */
import dayjs from 'dayjs'
import { api } from '@/api/http'

/** 卡片四态（与 legacy STATE 逐字一致）。 */
export const STATE = {
  PROPOSED: 'proposed', // 待确认
  RUNNING: 'running', // 执行中
  SUCCESS: 'success', // 成功
  FAILED: 'failed' // 失败
}

/** 状态 → 界面文案（chrome，非业务数据）。 */
export const STATE_TEXT = {
  proposed: '待确认',
  running: '执行中',
  success: '已完成',
  failed: '未完成'
}

/** 风险等级 → [界面文案, 样式修饰]。 */
export const RISK_LEVEL = {
  LOW: ['低风险', 'low'],
  MEDIUM: ['中风险', 'medium'],
  HIGH: ['高风险', 'high']
}

/**
 * 已知 subCode → 中文短语。
 * 服务端 message 本来就是给运营看的中文，这里只补一句「一句话结论」，
 * 让运营不用读完整段 message 就知道发生了什么。两者都会显示，不做取舍。
 */
export const SUBCODE_TEXT = {
  DUPLICATE_ACTION: '该操作已执行过',
  ACTION_IN_PROGRESS: '另一个请求正在执行',
  PROPOSAL_EXPIRED: '卡片已过期',
  PROPOSAL_MISMATCH: '卡片与实际操作不一致',
  CONFLICT_STOCK_CHANGED: '库存已经变了',
  REFUND_ALREADY_HANDLED: '退款单已经处理过',
  TARGET_NOT_FOUND: '找不到对应的数据',
  PERM_DENIED: '没有该操作权限',
  SELF_APPROVAL_FORBIDDEN: '不能审批自己名下的退款',
  ORDER_STATE_INVALID: '订单当前状态不允许该操作',
  TRACKING_NO_CONFLICT: '运单号已被占用',
  AMBIGUOUS_TARGET: '匹配到多个对象',
  PARAM_MISSING: '缺少必要参数',
  PARAM_INVALID: '参数不合法',
  OPERATOR_MISMATCH: '操作人与卡片不一致',
  CONTEXT_MISSING: '登录信息已失效'
}

/**
 * 这些失败「原样重试」有意义（数据变了、并发争抢、网络抖了）。
 * 其余（过期 / 不一致 / 已处理 / 权限）重试必然还是同样的结果，
 * 直接把运营往「让小运重新准备」上引，别让他反复点。
 */
export const RETRYABLE_SUBCODES = {
  CONFLICT_STOCK_CHANGED: 1,
  ACTION_IN_PROGRESS: 1,
  NETWORK_ERROR: 1
}

/**
 * 执行结果摘要里要展示的字段。
 * 只认「本身就是给人看的字符串」或「原样数字」的键，键名 → 中文标题是固定界面文案。
 * 不做任何格式化（不 toFixed、不算百分比、不拼时间差），避免口径漂移。
 */
export const SUMMARY_KEYS = [
  ['statusLabel', '当前状态'],
  ['previousStatusLabel', '原状态'],
  ['orderNo', '订单号'],
  ['trackingNo', '运单号'],
  ['company', '快递公司'],
  ['stock', '当前库存'],
  ['amount', '金额'],
  ['name', '名称'],
  // 风险消息一键已读的结果（服务端原样返回的整数，前端不做任何换算）
  ['affected', '已读条数'],
  ['remainingUnread', '剩余未读'],
  ['id', '编号']
]

// ============================================================================
//  纯函数小工具（与 legacy 完全等价）
// ============================================================================

/** 值是否「有内容」（0 和 false 都算有内容，空串/null/undefined/空数组不算）。 */
export function filled(v) {
  if (v === null || v === undefined) return false
  if (typeof v === 'string') return v.trim() !== ''
  if (Array.isArray(v)) return v.length > 0
  return true
}

/** 安全取对象路径，任何一层缺失都返回 undefined，绝不抛错（卡片渲染不许炸整页）。 */
export function pick(obj, path) {
  let cur = obj
  const parts = String(path).split('.')
  for (let i = 0; i < parts.length; i += 1) {
    if (cur === null || cur === undefined) return undefined
    cur = cur[parts[i]]
  }
  return cur
}

/** 提案里的 path 是 /api/admin/...；api() 自己会补 /api，所以要削掉这一层。 */
export function stripApiPrefix(p) {
  return String(p || '').replace(/^\/api(?=\/)/, '')
}

/**
 * 提案唯一键。
 * 优先用服务端 proposalId（幂等键，稳定）；极端情况下服务端没给 id 时，
 * 用 action + confirm.path + targetLabel + body 拼一个「够用」的合成键，
 * 保证同一张卡不会被重复渲染两次（宁可少画一张，也不要同屏两张）。
 */
export function proposalKey(proposal) {
  const p = proposal || {}
  if (filled(p.proposalId)) return String(p.proposalId)
  const path = stripApiPrefix(pick(p, 'confirm.path'))
  const action = String(p.action || '')
  const target = String(p.targetLabel || '')
  if (!action && !path && !target) return ''
  let body = ''
  try {
    body = JSON.stringify(pick(p, 'confirm.body') ?? null)
  } catch (e) {
    body = ''
  }
  return ['noid', action, path, target, body].join('|')
}

/** 这张卡片是否「有可执行的入口」——没有 confirm.path 就是结构不可用。 */
export function isRenderable(proposal) {
  return filled(pick(proposal, 'confirm.path'))
}

/** 卡片是否已过期（服务端 pending 接口已过滤，这里只做兜底防御）。 */
export function isExpired(proposal, now = dayjs()) {
  const raw = pick(proposal, 'expiresAt')
  if (!filled(raw)) return false
  const t = dayjs(raw)
  if (!t.isValid()) return false
  return t.isBefore(now)
}

/**
 * 把 ISO 时间串显示成「10-22 15:03」。
 * 这是**界面文案**（chrome），不是业务数据；服务端口径变了也只是显示格式问题。
 */
export function formatStamp(iso) {
  if (!filled(iso)) return ''
  const d = dayjs(iso)
  return d.isValid() ? d.format('MM-DD HH:mm') : String(iso)
}

// ============================================================================
//  请求通道
// ============================================================================

/**
 * 归一化返回信封，保证调用方永远拿到 {code, subCode, message, data}。
 * @param {any} env 服务端响应体
 * @returns {{code:number, subCode:string, message:string, data:any}}
 */
function normalizeEnvelope(env) {
  if (!env || typeof env !== 'object') {
    return {
      code: -1,
      subCode: 'INTERNAL_ERROR',
      message: '服务端返回格式异常，请稍后重试。',
      data: null
    }
  }
  return {
    code: typeof env.code === 'number' ? env.code : -1,
    subCode: filled(env.subCode) ? String(env.subCode) : '',
    message: filled(env.message) ? String(env.message) : '',
    data: env.data ?? null
  }
}

/**
 * 执行一次确认（或反向操作），返回**完整信封**。
 *
 * 为什么必须 raw 模式：api() 默认模式在 code !== 0 时抛异常、成功时只返回 data，
 * 把 subCode 吞掉了。而卡片必须区分「成功」「已执行过（DUPLICATE_ACTION）」「失败」，
 * subCode 是唯一判据，丢了就分不清。
 *
 * @param {Object}  proposal 服务端返回的 ActionProposal
 * @param {Object}  [options] { reverse: boolean } reverse=true 走 proposal.reject
 * @returns {Promise<{code:number, subCode:string, message:string, data:any}>} 永不 reject
 */
export async function executeConfirm(proposal, options = {}) {
  const reverse = options.reverse === true
  const p = proposal || {}
  const spec = reverse ? (p.reject || {}) : (p.confirm || {})
  const path = stripApiPrefix(spec.path)

  if (!filled(path)) {
    // 理论上组件层已经拦住了（isRenderable），这里再兜一次，绝不让卡片卡在「执行中」。
    return {
      code: -1,
      subCode: 'PARAM_MISSING',
      message: '这张卡片缺少执行入口（confirm.path），无法执行，请让小运重新准备。',
      data: null
    }
  }

  const headers = Object.assign({}, spec.requiresHeader || {})
  if (reverse) {
    // 注意**故意不带 X-Proposal-Id**：提案行的 action 是 REFUND_APPROVE，
    // 而这里要执行 REFUND_REJECT，服务端 verifyProposal 会因「动作对不上」返回
    // PROPOSAL_MISMATCH。不带提案 ID 时服务端按「直接调接口」处理（entry=API）：
    // 幂等键照样生效、审计照样落库，只是不认这张卡片。
    delete headers['X-Proposal-Id']
  } else if (filled(p.proposalId)) {
    // 提案 ID 必须带上：服务端靠它找回 PROPOSED 行做「动作/对象/操作人/参数」四项校验，
    // 找不到提案行就会退化成页面入口（只写审计、不做幂等）—— 连点就真的会执行两次。
    headers['X-Proposal-Id'] = p.proposalId
  }

  try {
    const env = await api(path, spec.method || 'POST', spec.body ?? null, headers, true)
    return normalizeEnvelope(env)
  } catch (e) {
    // 网络异常 / 超时 / 401 / 403：统一转成信封，卡片按「失败」渲染并给出可读原因。
    return {
      code: -1,
      subCode: 'NETWORK_ERROR',
      message: (e && e.message) ? e.message : '网络异常，请稍后重试。',
      data: null
    }
  }
}

// ============================================================================
//  结果摘要（只读服务端给的字段，不做任何换算）
// ============================================================================

/**
 * 生成结果摘要行，供模板 v-for 渲染（替代 legacy 的 DOM buildSummary）。
 *
 * @param {Object} proposal 提案
 * @param {any}    data     执行响应体的 data
 * @returns {Array<{label:string, value:string, mono:boolean}>}
 */
export function buildSummaryRows(proposal, data) {
  const rows = []
  const p = proposal || {}

  if (!data || typeof data !== 'object') {
    // 没有结果体：至少让运营知道作用在哪个对象上（来自 proposal，不是猜的）
    if (filled(p.targetLabel)) {
      rows.push({ label: '操作对象', value: String(p.targetLabel), mono: false })
    }
    return rows
  }

  SUMMARY_KEYS.forEach(pair => {
    const key = pair[0]
    const label = pair[1]
    const v = data[key]
    // 跳过 null/undefined/空串；0 和 false 是有效值，必须显示
    if (v === null || v === undefined || v === '') return
    if (typeof v === 'object') return
    if (key === 'id' && !filled(p.targetLabel)) {
      // 只有拿不到 targetLabel 时才退回显示裸 ID，否则重复
      rows.push({ label, value: String(v), mono: false })
      return
    }
    if (key === 'id') return
    rows.push({ label, value: String(v), mono: key === 'orderNo' || key === 'trackingNo' })
  })

  return rows
}

// ============================================================================
//  去重登记表（模块级单例，全页共享）
// ============================================================================

/** @type {Set<string>} 已在消息流里渲染过的提案键 */
const renderedKeys = new Set()

/** 登记「这张卡已经在消息流出现过了」。 */
export function markRendered(key) {
  if (filled(key)) renderedKeys.add(String(key))
}

/** 查询「这张卡是否已经在消息流出现过」。 */
export function isRendered(key) {
  if (!filled(key)) return false
  return renderedKeys.has(String(key))
}

/** 清空会话时重置（resetDialog），让下一轮重新可用。 */
export function resetRendered() {
  renderedKeys.clear()
}
