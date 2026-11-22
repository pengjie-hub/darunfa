/**
 * app store —— 侧栏未读角标（改造计划 3.5 第六条）。
 *
 * 旧页三个红点各自 setInterval 拉接口（refreshRefundUnread / refreshRiskUnread / refreshCsUnread），
 * 这里统一收口：30 秒一轮，按权限逐个拉，失败静默（红点不该打扰使用）。
 * 轮询由 AdminLayout 通过 usePolling 启动，Tab 隐藏时自动暂停。
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { useAuthStore } from './auth'
import { pendingRefundCount } from '@/api/refunds'
import { riskSummary } from '@/api/risk'
import { csStats } from '@/api/cs'

export const useAppStore = defineStore('app', () => {
  const refundUnread = ref(0)
  const riskUnread = ref(0)
  const csUnread = ref(0)

  async function refreshBadges() {
    const auth = useAuthStore()
    if (!auth.user) return

    const tasks = []

    if (auth.hasPerm('order:view', 'cs:manage')) {
      tasks.push(
        pendingRefundCount()
          .then(s => { refundUnread.value = (s && s.pending) || 0 })
          .catch(() => {})
      )
    }
    if (auth.hasPerm('risk:view')) {
      tasks.push(
        riskSummary()
          .then(s => { riskUnread.value = (s && s.unread) || 0 })
          .catch(() => {})
      )
    }
    if (auth.hasPerm('cs:manage')) {
      tasks.push(
        csStats()
          .then(s => { csUnread.value = (s && s.unread) || 0 })
          .catch(() => {})
      )
    }

    await Promise.all(tasks)
  }

  return { refundUnread, riskUnread, csUnread, refreshBadges }
})
