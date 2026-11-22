<script setup>
/**
 * XiaoyunPendingPanel.vue —— 常驻「待确认操作」区块。
 * ============================================================================
 * 对应旧后台 #aiPendingBlock：小运消息流顶部的一块常驻列表，数据来自
 * GET /admin/ai/actions/pending（当前登录人未过期 · 待确认的提案）。
 *
 * 存在的意义（缺陷 C）：卡片在消息流里「丢了」（渲染失败、被清空、翻页滚走），
 * 运营还能在这里把它找回来 —— 这是最后一道兜底，必须每轮对话结束后刷新。
 *
 * 与消息流的分工（去重约定见 composables/useXiaoyunCards.js 顶部注释）：
 *   · 本区块**从不**调用 markRendered —— 一旦登记就会把「找回入口」自己藏起来；
 *   · 它只做「已在消息流渲染过的跳过」（isRendered），因此同一张卡不会同屏两份。
 *
 * 计数规则：以真正渲染出来的卡片为准（结构不可用 / 已过期的提案不计数）。
 * 归零时整块消失（v-if），不留一个「待确认操作 0」的空壳。
 */
import { ref, watch } from 'vue'
import XiaoyunActionCard from './XiaoyunActionCard.vue'
import { isRendered, isRenderable, isExpired, proposalKey } from '@/composables/useXiaoyunCards'

const props = defineProps({
  /** 待确认提案数组（服务端原始返回，本组件负责过滤） */
  list: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['countChange', 'refresh', 'settled'])

/** 本地已结算（执行成功）的卡片键：即使父组件还没刷完列表也不会闪回来 */
const removed = new Set()

const items = ref([])

function recompute() {
  const arr = Array.isArray(props.list) ? props.list : []
  items.value = arr.filter(p => {
    if (!p || typeof p !== 'object') return false
    const key = proposalKey(p)
    if (!key) return false
    if (removed.has(key)) return false
    if (!isRenderable(p)) return false // 没有执行入口的卡片渲染出来只会误导运营
    if (isExpired(p)) return false // 过期提案点了必然报 PROPOSAL_EXPIRED
    if (isRendered(key)) return false // 已在消息流出现过 → 不重复
    return true
  })
  emit('countChange', items.value.length)
}

// 父组件每轮对话结束后会换掉 list 引用，这里跟着重算
watch(() => props.list, recompute, { immediate: true })

function onCardSettled(payload) {
  const ok = !!(payload && payload.success)
  const key = proposalKey(payload && payload.proposal)
  if (ok && key) {
    // 执行成功 → 从区块移除并降计数（失败保留卡片，让运营看到原因）
    removed.add(key)
    recompute()
    emit('refresh')
  }
  // 无论成功失败都把事件透传给父组件（父组件负责刷新侧栏角标）
  emit('settled', payload)
}
</script>

<template>
  <div v-if="items.length" class="xy-pending">
    <div class="xy-pending-head">
      <span>待确认操作</span>
      <span class="xy-pending-count">{{ items.length }}</span>
    </div>
    <div class="xy-pending-list">
      <XiaoyunActionCard
        v-for="p in items"
        :key="proposalKey(p)"
        :proposal="p"
        context="pending"
        @settled="onCardSettled"
      />
    </div>
  </div>
</template>
