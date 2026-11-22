<script setup>
/**
 * AdminLayout.vue —— 后台外壳。
 *
 * 关键约束：**样式全部交给 admin-theme.css 的全局类**，本文件不写任何 scoped 样式。
 * DOM 结构逐字复刻 admin.html 行 14-55：
 *   .app > .sidebar(.brand>.mark+.title+.subtitle, nav>a>.ico+文本, .foot)
 *        + .main(.topbar>.page-title + .user-area>.user-name+.role-badge.r-XXX+.btn.ghost.sm, .content)
 *
 * 自绘样式（.admin-app/.logo/.nav-item/.breadcrumb/.role-tag）已全部删除 ——
 * 它们规避了 admin-theme 的 sidebar 248px / topbar 64px / radius / 字体变量，
 * 是此前仪表盘「跟旧页不一样」的主要原因之一。
 */
import { computed, nextTick, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useAppStore } from '@/stores/app'
import { usePolling } from '@/composables/usePolling'
import { ElMessageBox } from 'element-plus'
import AiAssistant from '@/components/AiAssistant.vue'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const app = useAppStore()

// 侧栏未读角标：退款 / 风险 / 客服，30s 一轮（Tab 隐藏自动暂停，离开布局自动清理）
usePolling(() => app.refreshBadges(), { interval: 30000 })

const BADGES = {
  refunds: () => app.refundUnread,
  risk:    () => app.riskUnread,
  cs:      () => app.csUnread
}

function badgeCount(path) {
  return BADGES[path] ? BADGES[path]() : 0
}

function badgeText(n) {
  return n > 99 ? '99+' : String(n)
}

// 顶栏角色徽章文案与 admin.html roleLabel() 保持一致（徽章显示角色名而非权限码）
const ROLE_LABELS = { ADMIN: '超级管理员', OPS: '运营专员', CS: '客服', USER: '普通用户' }
function roleLabel(role) {
  return ROLE_LABELS[role] || role || ''
}

// 侧栏图标：与 admin.html 行 24-39 逐项对应（router meta 里没存 icon，集中维护在这里）
const ICONS = {
  dashboard:   '📊',
  products:    '🛒',
  orders:      '📦',
  refunds:     '💰',
  risk:        '🛡️',
  stock:       '📉',
  members:     '👥',
  roles:       '🛡️',
  permissions: '🔑',
  reviews:     '💬',
  cs:          '🎧',
  categories:  '🗂️',
  promotions:  '🏷️',
  payments:    '💳',
  shipments:   '🚚',
  knowledge:   '📚'
}

const navItems = computed(() =>
  router.options.routes.find(r => r.path === '/')?.children || []
)

// 权限过滤：adminOnly 只对 ADMIN 可见；permission 逗号分隔为 OR 语义
const visibleNav = computed(() =>
  navItems.value.filter(item => {
    if (!item.meta) return false
    if (item.meta.adminOnly) return auth.role === 'ADMIN'
    if (item.meta.permission) {
      return item.meta.permission.split(',').map(s => s.trim()).some(c => auth.hasPerm(c))
    }
    return true
  })
)

function isActive(item) { return route.path === `/${item.path}` }

// 窄视口下侧栏会溢出（旧页 nav scrollHeight 939 > 可见 726）：
// 旧页点击导航会把当前项滚入视口，这里保持同样的行为，否则直接用 URL 进入时当前项在视野外
const navEl = ref(null)
watch(() => route.path, async () => {
  await nextTick()
  navEl.value?.querySelector('a.active')?.scrollIntoView({ block: 'nearest' })
})

async function onLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
    })
    auth.logout()
    router.replace('/login')
  } catch (_) { /* cancelled */ }
}
</script>

<template>
  <div class="app">
    <aside class="sidebar">
      <div class="brand">
        <div class="mark">润</div>
        <div>
          <div class="title">电商后台管理</div>
          <div class="subtitle">大润发风格 v2.0</div>
        </div>
      </div>

      <nav ref="navEl">
        <router-link
          v-for="item in visibleNav"
          :key="item.path"
          :to="`/${item.path}`"
          :class="{ active: isActive(item) }"
        >
          <span class="ico">{{ ICONS[item.path] || '•' }}</span>
          <span>{{ item.meta?.title }}</span>
          <span
            v-if="badgeCount(item.path) > 0"
            class="nav-badge"
            style="display: inline-block"
          >{{ badgeText(badgeCount(item.path)) }}</span>
        </router-link>
      </nav>

      <div class="foot">小发智购运营后台</div>
    </aside>

    <div class="main">
      <div class="topbar">
        <div class="page-title">{{ route.meta.title }}</div>
        <div class="user-area">
          <span v-if="auth.role" class="role-badge" :class="`r-${auth.role}`">{{ roleLabel(auth.role) }}</span>
          <span class="user-name">{{ auth.user?.nickname || auth.user?.username || '管理员' }}</span>
          <button class="btn ghost sm" @click="onLogout">退出</button>
        </div>
      </div>

      <div class="content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </div>

    <!-- 小运悬浮球（仅 ADMIN 渲染，组件内部自行判定角色） -->
    <AiAssistant />
  </div>
</template>

<style>
/* 仅保留页面切换淡入淡出（admin-theme.css 没有，且必须是全局非 scoped 才能作用于 transition 类） */
.fade-enter-active, .fade-leave-active { transition: opacity .15s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
