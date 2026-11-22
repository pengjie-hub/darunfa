<script setup>
/**
 * AiAssistant.vue —— 「小运」AI 运营助手悬浮球 + 对话面板。
 * ============================================================================
 * 行为逐项对齐 admin.html（AI_SUGGESTIONS / resetAiDialog / sendAiMsg / loadAiHistory /
 * loadAiPending / initAiAssistant），只对 ADMIN 渲染（旧版 applyNavVisibility 里的
 * `aiFab.style.display = hasRole('ADMIN') ? '' : 'none'`）。
 * 视觉全部沿用 admin-theme.css（.ai-*）与 xiaoyun-action.css（.xy-*），不新增样式。
 *
 * ---------------------------------------------------------------------------
 * 【本版核心变更：卡片从 legacy 原生 DOM 渲染改为真正的 Vue 组件】
 * ---------------------------------------------------------------------------
 * 旧实现（已删除）：
 *   import '@/api/xiaoyun-action.legacy'   // 模块加载时挂 window.XiaoyunAction
 *   window.api = api                       // legacy 靠它发请求
 *   const slots = new Map(); bindSlot(...)  // :ref 挂载点机制
 *   msg.hasSlot = true; await nextTick(); const slot = slots.get(msg.id); if (!slot) return;
 *   XA.renderAll(slot, proposals, {...})
 *
 * 那个 `if (!slot) return` 就是 Vue 版的「静默丢卡」点：卡片该出现时什么都没发生，
 * 控制台也不报错，运营只能反复问、反复失败。现在改为：
 *   消息对象上直接挂 `cards: []`，模板里 v-for 渲染 <XiaoyunActionCard>，
 *   没有挂载点、没有 nextTick、没有 window.* 依赖，Vue 的响应式就是唯一事实来源。
 *
 * ---------------------------------------------------------------------------
 * 【三个必须补齐的缺陷】
 * ---------------------------------------------------------------------------
 * A. 绝不再静默：提案因结构问题无法渲染（缺 confirm.path / 已过期）时，
 *    在消息流里给一条**可见**提示，而不是什么都不显示。见 renderReplyCards()。
 * B. 0 卡兜底：本轮 proposals 为空但答复文本在讲卡片时，自动拉一次
 *    listPendingActions()：有未过期提案就补渲染进消息流 + 提示「已为你补加载 N 张」；
 *    没有就显示橙色提示，引导用户说「重新准备一次」。普通只读问答不出任何提示条。
 *    见 fallbackLoadPending() 与 mentionsCardIntent()。
 * C. 每轮结束刷新待确认区块：保证「卡片丢了还能在区块里找回」，并用去重表
 *    （composables/useXiaoyunCards.js）避免同一张卡在消息流与区块里各出现一次。
 *    见 refreshPending() 与 <XiaoyunPendingPanel>。
 */
import { computed, nextTick, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useAppStore } from '@/stores/app'
import { askAssistant, listHistory, listPendingActions, clearMemory } from '@/api/ai'
import XiaoyunActionCard from '@/components/xiaoyun/XiaoyunActionCard.vue'
import XiaoyunPendingPanel from '@/components/xiaoyun/XiaoyunPendingPanel.vue'
import {
  filled,
  formatStamp,
  isExpired,
  isRenderable,
  isRendered,
  markRendered,
  proposalKey,
  resetRendered
} from '@/composables/useXiaoyunCards'

const auth = useAuthStore()
const app = useAppStore()

const isAdmin = computed(() => auth.role === 'ADMIN')

// 与 admin.html AI_SUGGESTIONS 原文一致
const SUGGESTIONS = [
  '最近 5 单待发货有哪些？',
  '这周 GMV 多少？',
  '库存预警有哪些商品？',
  '搜索含"苹果"的商品',
  '现在有什么促销活动？',
  '总共有多少注册用户？'
]

const open = ref(false)
const busy = ref(false)
const input = ref('')
const showWelcome = ref(true)
const messages = ref([])
const inputEl = ref(null)
const messagesEl = ref(null)

// 待确认操作区块（对应旧版 #aiPendingBlock）：服务端原始列表交给子组件过滤/渲染
const pendingList = ref([])
// 子组件广播的「真正渲染出来的卡片数」，父组件保留可观测性（面板自己画头部计数）
const pendingCount = ref(0)

// 消息 id 自增，用作 v-for key
let seq = 0

/**
 * 追加一条消息。
 * 注意这里是「先 push 再取回」——拿到的是 Vue 的响应式代理而不是原始对象，
 * 后续对 placeholder.kind / .text 的修改才能可靠触发重渲染
 * （旧版直接持有原始对象，靠 busy 变化顺带刷新，属于隐患）。
 *
 * @param {string} kind 'user' | 'bot' | 'bot thinking' | 'bot error'
 * @param {string} text 正文
 * @returns {Object} 响应式消息对象
 */
function pushMessage(kind, text) {
  messages.value.push({
    id: ++seq,
    kind,
    text,
    cards: [], // 本轮确认卡片（Vue 组件，不再走 DOM 挂载点）
    options: [], // 追问选项（needsClarification.options）
    notice: null // 可见提示条：{ tone: 'warn' | 'info', text: string }
  })
  return messages.value[messages.value.length - 1]
}

async function scrollToBottom() {
  await nextTick()
  const el = messagesEl.value
  if (el) el.scrollTop = el.scrollHeight
}

function focusInput() {
  inputEl.value?.focus()
}

/** 旧版 resetAiDialog：欢迎语 + 快捷提问（同时清掉待确认区块，因为旧版是 innerHTML=''） */
function resetDialog() {
  messages.value = []
  pendingList.value = []
  pendingCount.value = 0
  showWelcome.value = true
  busy.value = false
  // 新会话重新开始去重：上一轮登记过的卡片键全部作废，否则区块里的卡会被永久藏起来
  resetRendered()
}

/**
 * 缺陷 B 的触发词：**只**认「小运明确在讲可执行卡片」的说法。
 * 故意不收「待确认」（运营可能只是在问「有多少待确认的退款」这类只读统计），
 * 也绝不因为答复里出现「订单 / 库存」这类业务词就提示，避免正常的只读问答被污染。
 */
const CARD_INTENT_RE = /卡片|点击确认|确认按钮|确认执行/

function mentionsCardIntent(text) {
  return CARD_INTENT_RE.test(String(text || ''))
}

/**
 * 缺陷 B：0 卡兜底。
 * 本轮服务端没给 proposals，但答复在讲卡片（说明它本该给），就去待确认列表捞一次：
 *   · 捞到未过期提案 → 补渲染进消息流 + info 提示「已为你补加载 N 张」；
 *   · 没捞到 → warn 提示，把运营往「重新准备一次」上引，而不是让他对着空白干瞪眼。
 * @param {Object} msg 本轮 bot 消息
 */
async function fallbackLoadPending(msg) {
  let list = []
  try {
    list = await listPendingActions()
  } catch (e) {
    msg.notice = {
      tone: 'warn',
      text: '本轮没有生成可执行卡片，也暂时取不到待确认列表（' + ((e && e.message) || '网络异常') +
        '）。如果确实要执行，请说「重新准备一次」。'
    }
    return
  }

  const arr = Array.isArray(list) ? list : []
  const usable = arr.filter(p => {
    if (!p || typeof p !== 'object') return false
    const key = proposalKey(p)
    if (!key) return false
    if (!isRenderable(p) || isExpired(p)) return false
    return !isRendered(key) // 已经在消息流出现过的就不再补一遍
  })

  if (usable.length) {
    usable.forEach(p => markRendered(proposalKey(p)))
    msg.cards = usable
    msg.notice = { tone: 'info', text: '已为你补加载 ' + usable.length + ' 张待确认卡片' }
  } else {
    msg.notice = {
      tone: 'warn',
      text: '本轮没有生成可执行卡片，当前也没有未过期的待确认操作。如果确实要执行，请说「重新准备一次」。'
    }
  }
}

/**
 * 旧版 renderAiProposals 的 Vue 版：渲染本轮答复里的追问选项与待确认提案卡片。
 * @param {Object} msg  本轮 bot 消息（响应式代理）
 * @param {Object} data 服务端答复体
 */
async function renderReplyCards(msg, data) {
  if (!data || typeof data !== 'object') return

  // ---- 追问选项（兼容 needsClarification === true 与 {options:[...]} 两种形态）----
  const nc = data.needsClarification
  const rawOptions = (nc && typeof nc === 'object' && Array.isArray(nc.options)) ? nc.options : []
  msg.options = rawOptions
    .map(o => (o === null || o === undefined ? '' : String(o)))
    .filter(o => o.trim() !== '')

  // ---- 提案卡片 ----
  const rawProposals = Array.isArray(data.proposals) ? data.proposals : []
  const proposals = rawProposals.filter(p => p && typeof p === 'object')

  const cards = []
  let unusable = 0
  let deduped = 0
  proposals.forEach(p => {
    const key = proposalKey(p)
    if (!key) {
      unusable += 1
      return
    }
    if (isRendered(key)) {
      // 去重：待确认区块 / 上一轮已经渲染过的同一张卡。
      // 计数是为了「整轮都是重复卡」时给一句解释（见下方 P2-1），否则运营会以为卡片丢了。
      deduped += 1
      return
    }
    if (!isRenderable(p)) {
      // 缺陷 A：缺 confirm.path —— 绝不能静默跳过，必须计数并提示（见下）
      unusable += 1
      return
    }
    if (isExpired(p)) {
      // 过期卡点了必然 PROPOSAL_EXPIRED，不如直接说清楚
      unusable += 1
      return
    }
    markRendered(key)
    cards.push(p)
  })
  msg.cards = cards

  // 缺陷 A：结构不可用的卡片必须在消息流里说一句，不允许「什么都不显示」
  if (unusable > 0) {
    msg.notice = {
      tone: 'warn',
      text: '有 ' + unusable + ' 张卡片缺少执行入口或已过期，无法确认，请让小运重新准备一次。'
    }
  } else if (proposals.length && deduped > 0 && !cards.length) {
    // P2-1：本轮 proposals 非空、但**全部**命中去重（连一张新卡都没渲染出来）时，
    // 界面否则会只剩一段文字，运营无法判断「卡片是不是丢了」。
    // 这里只加 info（这不是异常，是正常去重），且「能渲染出 ≥1 张新卡」时绝不加，避免正常出卡多噪。
    msg.notice = {
      tone: 'info',
      text: deduped === 1
        ? '这张卡片已经显示过了（在上方消息或「待确认操作」里），直接点它的「确认执行」即可，无需重复生成。'
        : '这 ' + deduped + ' 张卡片已经显示过了（在上方消息或「待确认操作」里），直接点它们的「确认执行」即可，无需重复生成。'
    }
  }

  // 缺陷 B：0 卡兜底（追问轮次不算丢卡，跳过）
  if (!proposals.length && !msg.options.length && mentionsCardIntent(msg.text)) {
    await fallbackLoadPending(msg)
  }

  await scrollToBottom()
}

/**
 * 缺陷 C：刷新常驻「待确认操作」区块。
 * 每轮对话结束都跑一次，保证卡片在消息流里丢了还能在区块里找回。
 * 失败静默 —— 区块是兜底设施，不该因为它自己出错打断对话。
 */
async function refreshPending() {
  let list = []
  try {
    list = await listPendingActions()
  } catch (e) {
    return
  }
  const arr = Array.isArray(list) ? list : []
  pendingList.value = arr.filter(p => p && typeof p === 'object' && isRenderable(p) && !isExpired(p))
  pendingCount.value = pendingList.value.length
}

/** 消息流里的卡片执行完成 → 刷新侧栏未读角标（退款/客服/风险待办数可能变了）+ 同步区块 */
async function onCardSettled(payload) {
  if (payload && payload.success) {
    app.refreshBadges().catch(() => {})
  }
  await refreshPending()
}

/** 待确认区块里的卡片执行完成 → 只负责角标；列表刷新由子组件的 refresh 事件驱动 */
function onPendingSettled(payload) {
  if (payload && payload.success) {
    app.refreshBadges().catch(() => {})
  }
}

function onPendingCountChange(count) {
  pendingCount.value = Number(count) || 0
}

/**
 * 旧版 loadAiHistory —— 并且在历史回放之后**补回卡片**。
 *
 * ---------------------------------------------------------------------------
 * 为什么光回放历史不够（这次线上问题的真正根因）
 * ---------------------------------------------------------------------------
 * `GET /admin/ai/history` 只回放**文字**（内容来自 `ai_chat_messages`），
 * 而卡片是「当轮对话响应里才带」的 `proposals`。于是刷新页面后：
 *
 *   · 历史里每一句「确认卡片就在本消息下方」都指向一片空白；
 *   · 屏幕上既没有卡片、也没有任何一句解释；
 *   · 运营的第一反应必然是「卡片丢了 / 界面坏了」。
 *
 * 现在按两种情况分别收口，绝不允许「什么都没有」：
 *   1. 服务端还留着**未过期**的 PROPOSED 提案 → 在消息流末尾追加一条
 *      「上次会话还有 N 张卡片没确认」，把卡片挂上去（并登记去重，
 *      「待确认操作」区块就不会再重复画同一张）；
 *   2. 一张都恢复不出来（都已过期）→ 在**最后一条**讲卡片的历史消息下挂一条可见提示，
 *      明确写「已失效、点不了、要怎么重新拿到」。
 */
async function loadHistory() {
  try {
    const history = await listHistory()
    if (Array.isArray(history) && history.length) {
      showWelcome.value = false
      messages.value = history.map(m => ({
        id: ++seq,
        kind: m.role === 'user' ? 'user' : 'bot',
        text: m.content || '',
        cards: [],
        options: [],
        notice: null
      }))
    }
  } catch (e) {
    // 拉取失败保持默认欢迎语，不打断使用
  }
  await restorePendingCards()
  await scrollToBottom()
}

/**
 * 刷新页面后把「上次会话遗留、仍然有效」的卡片找回来。
 * 取不到列表时**静默返回** —— 这不是错误页，不该因为它弹提示打断打开面板。
 */
async function restorePendingCards() {
  let list = []
  try {
    list = await listPendingActions()
  } catch (e) {
    return
  }

  const usable = (Array.isArray(list) ? list : []).filter(p => {
    if (!p || typeof p !== 'object') return false
    const key = proposalKey(p)
    if (!key) return false
    if (!isRenderable(p) || isExpired(p)) return false
    return !isRendered(key) // 已在本会话消息流里出现过的，不重复挂一次
  })

  if (usable.length) {
    const msg = pushMessage(
      'bot',
      '上次会话里还有 ' + usable.length + ' 张操作卡片没有确认，我已经放在下面了。'
    )
    usable.forEach(p => markRendered(proposalKey(p)))
    msg.cards = usable
    // 有效期只用卡片自己带来的 expiresAt 显示（不在这里写死分钟数，避免和后端 TTL 漂移）
    const stamps = usable.map(p => p.expiresAt).filter(filled).sort()
    msg.notice = {
      tone: 'info',
      text: stamps.length
        ? '最早一张将在 ' + formatStamp(stamps[0]) + ' 过期，过期后需要让我重新准备一次。'
        : '卡片只在有效期内可以确认，过期后需要让我重新准备一次。'
    }
    return
  }

  // 没有任何可恢复的卡片：至少把「历史里那句『卡片就在下方』已经作废」讲明白
  const lastBot = [...messages.value]
    .reverse()
    .find(m => m.kind === 'bot' && mentionsCardIntent(m.text))
  if (lastBot) {
    lastBot.notice = {
      tone: 'warn',
      text: '这条消息里的确认卡片已经失效（未确认的提案只在有限时间内有效），现在点不了。需要执行的话，请对我说「重新准备一次」。'
    }
  }
}

async function openPanel() {
  open.value = true
  resetDialog()
  // 先回放历史（可能替换消息列表），再插入待确认区块，否则区块会被历史覆盖
  await loadHistory()
  await refreshPending()
  focusInput()
}

function closePanel() {
  open.value = false
}

/** 旧版 sendAiMsg */
async function send() {
  if (busy.value) return
  const text = input.value.trim()
  if (!text) return

  pushMessage('user', text)
  input.value = ''
  if (inputEl.value) inputEl.value.style.height = '42px'

  busy.value = true
  const placeholder = pushMessage('bot thinking', '正在查询数据并整理答复…')
  await scrollToBottom()

  try {
    const data = await askAssistant(text)
    placeholder.kind = 'bot'
    placeholder.text = String((data && data.answer) || '').trim() || '（无答复）'
    await renderReplyCards(placeholder, data)
  } catch (e) {
    placeholder.kind = 'bot error'
    placeholder.text = '网络异常：' + ((e && e.message) || e)
  } finally {
    busy.value = false
    // 缺陷 C：每轮结束（成功或失败）都刷新待确认区块
    await refreshPending()
    await scrollToBottom()
    focusInput()
  }
}

function quickAsk(text) {
  if (busy.value) return
  input.value = text
  focusInput()
  send()
}

/**
 * 极简富文本：把答复里的 `**粗体**` / `` `代码` `` / 换行渲染出来。
 *
 * 为什么需要：模型答复是 Markdown 风格（「**待确认内容**」「**只有点击卡片确认后…**」），
 * 直接 `{{ text }}` 会把星号原样打在屏幕上 —— 一屏的字面 `**` 会让运营以为页面坏了，
 * 而这恰恰是「卡片在哪里」这类投诉里最先被看到的东西。
 *
 * 安全：**先整体转义，再做替换**，顺序不能反。转义后正文里不存在任何 `<`，
 * 因此后续拼接的 `<strong>/<code>/<br>` 是这一串 HTML 里唯一的标签来源，
 * 模型输出 `<img onerror=…>` 之类只会变成可见文本。
 * 故意只支持这三样 —— 不做链接、不做标题、不做表格，越复杂越容易出 XSS 缝隙。
 */
function renderLight(text) {
  const raw = String(text == null ? '' : text)
  const safe = raw
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
  return safe
    .replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`\n]+)`/g, '<code>$1</code>')
    .replace(/\n/g, '<br>')
}

/** 追问选项被点击 → 填进输入框并聚焦（等价 legacy renderOptions 的 onPick） */
function pickOption(text) {
  if (!filled(text)) return
  input.value = String(text)
  focusInput()
}

/** 旧版 aiClear：二次确认 → 清空记忆；无论后端结果如何都重置本地会话作为兜底 */
async function clearAll() {
  try {
    await ElMessageBox.confirm('确定清空本次会话及 AI 的长期记忆吗？', '提示', {
      confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
    })
  } catch (_) {
    return // cancelled
  }
  try {
    await clearMemory()
    ElMessage.success('已清空 AI 会话与长期记忆')
  } catch (e) {
    ElMessage.error('清空失败：' + ((e && e.message) || e))
  } finally {
    resetDialog()
  }
}

function onKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

/** 输入框随内容自适应高度（上限 130px，与旧版一致） */
function onInput() {
  const el = inputEl.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 130) + 'px'
}
</script>

<template>
  <template v-if="isAdmin">
    <div
      class="ai-fab"
      id="aiFab"
      :title="pendingCount ? 'AI 运营助手（' + pendingCount + ' 项待确认）' : 'AI 运营助手'"
      @click="openPanel"
    >💬</div>

    <div class="ai-dialog-mask" id="aiMask" :class="{ show: open }">
      <div class="ai-dialog">
        <div class="ai-dialog-head">
          <h3>🤖 AI 运营助手 · 小运</h3>
          <div class="ai-head-side">
            <button id="aiClear" @click="clearAll">清空</button>
            <button class="close" id="aiClose" @click="closePanel">&times;</button>
          </div>
        </div>

        <div class="ai-messages" id="aiMessages" ref="messagesEl">
          <!-- 常驻待确认区块（自带计数、为空时整体消失） -->
          <XiaoyunPendingPanel
            :list="pendingList"
            @count-change="onPendingCountChange"
            @settled="onPendingSettled"
            @refresh="refreshPending"
          />

          <template v-if="showWelcome">
            <div class="ai-msg-tips">你好！我是 AI 运营助手「小运」，可以帮你查询订单/商品/用户/库存/促销等数据。试试下方快速提问：</div>
            <div class="ai-msg-suggest">
              <button v-for="s in SUGGESTIONS" :key="s" @click="quickAsk(s)">{{ s }}</button>
            </div>
          </template>

          <template v-for="m in messages" :key="m.id">
            <!-- 用户原话一律走文本插值（Vue 自动转义，绝不解析）；小运答复走极简富文本 -->
            <div v-if="m.kind === 'user'" :class="['ai-msg', m.kind]">{{ m.text }}</div>
            <div v-else :class="['ai-msg', m.kind]" v-html="renderLight(m.text)"></div>

            <!-- 可见提示条（缺陷 A：结构不可用 / 缺陷 B：0 卡兜底） -->
            <div v-if="m.notice" :class="['xy-notice', 'xy-notice-' + m.notice.tone]">{{ m.notice.text }}</div>

            <!-- 追问选项 -->
            <div v-if="m.options && m.options.length" class="xy-options">
              <div class="xy-options-label">小运需要你补充：</div>
              <div class="xy-options-bar">
                <button
                  v-for="(opt, idx) in m.options"
                  :key="'opt-' + idx"
                  type="button"
                  class="xy-option"
                  @click="pickOption(opt)"
                >{{ opt }}</button>
              </div>
            </div>

            <!-- 本轮确认卡片（真正的 Vue 组件，不再走 DOM 挂载点） -->
            <template v-if="m.cards && m.cards.length">
              <XiaoyunActionCard
                v-for="p in m.cards"
                :key="proposalKey(p)"
                :proposal="p"
                context="flow"
                @settled="onCardSettled"
              />
            </template>
          </template>
        </div>

        <div class="ai-input-bar">
          <textarea
            id="aiInput"
            ref="inputEl"
            v-model="input"
            rows="1"
            maxlength="500"
            placeholder="试试输入：最近 5 单待发货有哪些？红富士苹果还有多少库存？"
            @keydown="onKeydown"
            @input="onInput"
          ></textarea>
          <button id="aiSend" :disabled="busy" @click="send">发送</button>
        </div>
      </div>
    </div>
  </template>
</template>
