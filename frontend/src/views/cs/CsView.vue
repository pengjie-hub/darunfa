<script setup>
/**
 * CsView.vue —— 客服管理（迁移自 admin.html renderCs() / csRefresh() 行 1828-2010）。
 *
 * 两栏：左侧客户会话列表，右侧「客户提问 / AI 自动回复 / 人工回复」完整对话 + 输入框。
 * 关键语义：
 *   - 5 秒轮询新消息，离开页面必须停止（usePolling 已在 onBeforeUnmount / onBeforeRouteLeave 清理）；
 *   - 轮询只刷新两栏内容，不动输入框（用户可能正在打字）；
 *   - 只有当视图贴在底部时才自动滚到最新，用户上翻查看历史时不强拉回底部；
 *   - 发送用 Ctrl/⌘ + Enter，交还 AI 前二次确认。
 */
import { ref, computed, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listConversations, listMessages, sendCsReply, handBackToAi } from '@/api/cs'
import { usePolling } from '@/composables/usePolling'
import { useAppStore } from '@/stores/app'
import { fmtDateTime } from '@/utils/format'
import StatusTag from '@/components/common/StatusTag.vue'

const CS_STATUS = {
  AI:     { label: 'AI 应答',   color: 'blue' },
  HUMAN:  { label: '人工跟进', color: 'orange' },
  CLOSED: { label: '已结束',   color: 'gray' }
}

const app = useAppStore()

const convs = ref([])
const activeId = ref(null)
const msgs = ref([])
const msgError = ref('')
const replyText = ref('')
const sending = ref(false)
const bodyEl = ref(null)

const activeConv = computed(() => convs.value.find(c => c.id === activeId.value) || null)

async function refresh(keepScroll = true) {
  let list
  try {
    list = await listConversations()
  } catch (e) {
    // 轮询失败静默，避免每 5 秒弹一次错误
    return
  }

  convs.value = list || []
  if (!convs.value.some(c => c.id === activeId.value)) {
    activeId.value = convs.value.length ? convs.value[0].id : null
    keepScroll = false
  }

  if (!activeId.value) {
    msgs.value = []
    return
  }

  const box = bodyEl.value
  const prevScroll = box ? box.scrollTop : 0
  const nearBottom = box ? prevScroll + box.clientHeight >= box.scrollHeight - 60 : true

  try {
    msgs.value = (await listMessages(activeId.value)) || []
    msgError.value = ''
  } catch (e) {
    msgError.value = e.message || '加载失败'
    return
  }

  await nextTick()
  if (bodyEl.value) {
    bodyEl.value.scrollTop = keepScroll
      ? (nearBottom ? bodyEl.value.scrollHeight : prevScroll)
      : bodyEl.value.scrollHeight
  }
}

async function selectConv(c) {
  if (activeId.value === c.id) return
  activeId.value = c.id
  await refresh(false)
}

// 5 秒轮询；首次加载由 onMounted 负责（immediate:false 避免重复请求）
usePolling(() => refresh(true), { interval: 5000, immediate: false })

async function send() {
  const text = replyText.value.trim()
  if (!text) return ElMessage.warning('请输入回复内容')
  if (!activeId.value) return ElMessage.warning('请先选择一个客户会话')

  sending.value = true
  try {
    await sendCsReply(activeId.value, text)
    replyText.value = ''
    ElMessage.success('已发送给客户')
    await refresh(false)
    app.refreshBadges()
  } catch (e) {
    ElMessage.error(e.message || '发送失败')
  } finally {
    sending.value = false
  }
}

function onKeydown(e) {
  if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
    e.preventDefault()
    send()
  }
}

async function handBack() {
  if (!activeId.value) return
  try {
    await ElMessageBox.confirm('把该会话交还给 AI 自动应答？', '提示', {
      confirmButtonText: '交还 AI', cancelButtonText: '取消', type: 'warning'
    })
  } catch (_) {
    return
  }
  try {
    await handBackToAi(activeId.value)
    ElMessage.success('已交还给 AI')
    await refresh(false)
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  }
}

function msgMeta(m) {
  const mine = !!(m.fromCustomer || m.sender === 'USER')
  const isAgent = m.sender === 'AGENT'
  return {
    mine,
    cls: mine ? 'user' : isAgent ? 'agent' : 'ai',
    label: mine ? (m.senderName || '客户') : isAgent ? (m.senderName || '人工客服') : 'AI 客服',
    avatar: mine ? '客' : isAgent ? '人' : 'AI'
  }
}

function convTime(raw) {
  return raw ? String(raw).substring(5, 16) : ''
}

onMounted(() => refresh(false))
</script>

<template>
  <div>
    <div class="panel cs-intro">
      <div class="panel-body">
        <div class="hint">
          左侧选择客户，可查看该客户与 <b>AI 客服</b> 的完整对话；在下方输入框回复即以 <b>人工客服</b> 身份发送给客户。
        </div>
      </div>
    </div>

    <div class="cs-layout">
      <!-- 会话列表 -->
      <div class="panel">
        <div class="panel-head">
          <h3>客户会话</h3>
        </div>
        <div class="panel-body">
          <div class="cs-conv-list">
            <div
              v-for="c in convs"
              :key="c.id"
              class="cs-conv"
              :class="{ active: c.id === activeId }"
              @click="selectConv(c)"
            >
              <div class="cs-conv-top">
                <span class="cs-conv-name">{{ c.userName || ('用户 ' + c.userId) }}</span>
                <StatusTag
                  :text="CS_STATUS[c.status]?.label || c.status || '未知'"
                  :color="CS_STATUS[c.status]?.color || 'gray'"
                />
                <span v-if="(c.unread || 0) > 0" class="cs-unread">{{ c.unread }}</span>
                <span class="cs-conv-time">{{ convTime(c.lastMessageAt) }}</span>
              </div>
              <div class="cs-conv-last">{{ c.lastMessage || '（暂无消息）' }}</div>
            </div>

            <div v-if="!convs.length" class="cs-empty">还没有客户发起会话</div>
          </div>
        </div>
      </div>

      <!-- 对话区 -->
      <div class="cs-chat">
        <div class="cs-chat-head">
          <template v-if="activeConv">
            <span class="cs-conv-name">{{ activeConv.userName || ('用户 ' + activeConv.userId) }}</span>
            <StatusTag
              :text="CS_STATUS[activeConv.status]?.label || activeConv.status || '未知'"
              :color="CS_STATUS[activeConv.status]?.color || 'gray'"
            />
            <span class="hint">会话 #{{ activeConv.id }} · 共 {{ msgs.length }} 条消息</span>
            <div class="spacer"></div>
            <button class="btn ghost sm" title="交还给 AI 自动应答" @click="handBack">🤖 交还 AI</button>
          </template>
          <span v-else class="hint">从左侧选择一个客户会话</span>
        </div>

        <div ref="bodyEl" class="cs-chat-body">
          <template v-if="msgError">
            <div class="cs-empty">加载失败：{{ msgError }}</div>
          </template>
          <template v-else-if="msgs.length">
            <div
              v-for="(m, idx) in msgs"
              :key="idx"
              class="cs-row"
              :class="{ me: msgMeta(m).mine }"
            >
              <div class="cs-avatar" :class="msgMeta(m).cls">{{ msgMeta(m).avatar }}</div>
              <div class="cs-bubble-wrap">
                <div class="cs-meta">{{ msgMeta(m).label }} · {{ fmtDateTime(m.createdAt) }}</div>
                <div class="cs-bubble">{{ m.content || '' }}</div>
              </div>
            </div>
          </template>
          <div v-else class="cs-empty">
            {{ activeId ? '该客户还没有发过消息' : '暂无会话' }}
          </div>
        </div>

        <div class="cs-chat-foot">
          <textarea
            v-model="replyText"
            rows="2"
            placeholder="以人工客服身份回复该客户…（Ctrl/⌘ + Enter 发送）"
            @keydown="onKeydown"
          ></textarea>
          <button class="btn primary" :disabled="sending" @click="send">
            {{ sending ? '发送中…' : '发送' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 顶部说明面板（旧页 margin-bottom:16px） */
.cs-intro { margin-bottom: 16px; }
</style>
