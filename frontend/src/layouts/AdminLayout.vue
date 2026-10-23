<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessageBox } from 'element-plus'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

// 与 router/index.js 的 children 保持一致 —— 仅渲染可见项
const navItems = computed(() => {
  return router.options.routes
    .find(r => r.path === '/')?.children || []
})

function isVisible(item) {
  if (!item.meta) return false
  if (item.meta.adminOnly && auth.role !== 'ADMIN') return false
  if (item.meta.permission) {
    const codes = item.meta.permission.split(',').map(s => s.trim())
    return codes.some(c => auth.hasPerm(c))
  }
  return true
}

function isActive(item) {
  return route.path === `/${item.path}`
}

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
  <div class="admin-app">
    <aside class="sidebar">
      <div class="logo">
        <span class="logo-icon">润</span>
        <span class="logo-text">电商后台管理</span>
        <span class="logo-sub">大润发风格 v2.0</span>
      </div>
      <nav class="nav">
        <router-link
          v-for="item in navItems.filter(isVisible)"
          :key="item.path"
          :to="`/${item.path}`"
          class="nav-item"
          :class="{ active: isActive(item) }"
        >
          {{ item.meta?.title }}
        </router-link>
      </nav>
    </aside>
    <div class="main">
      <header class="topbar">
        <div class="topbar-left">
          <span class="breadcrumb">{{ route.meta.title }}</span>
        </div>
        <div class="topbar-right">
          <span class="user-info">
            {{ auth.user?.nickname || auth.user?.username || '管理员' }}
            <span v-if="auth.role" class="role-tag">{{ auth.role }}</span>
          </span>
          <button class="logout-btn" @click="onLogout">退出</button>
        </div>
      </header>
      <main class="content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-app {
  display: flex;
  min-height: 100vh;
  background: #f5f6f8;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  font-size: 14px;
  color: #333;
}
.sidebar {
  width: 220px;
  background: #fff;
  border-right: 1px solid #eee;
  padding: 16px 0;
  flex-shrink: 0;
}
.logo {
  padding: 0 20px 20px;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 6px;
}
.logo-icon {
  display: inline-block;
  width: 24px; height: 24px; line-height: 24px;
  background: var(--brand-500, #DE1313);
  color: #fff; border-radius: 4px;
  text-align: center; font-size: 13px; font-weight: 600;
  margin-right: 6px;
}
.logo-text { font-size: 14px; font-weight: 600; color: var(--brand-700, #931512); }
.logo-sub { font-size: 11px; color: #999; }
.nav { padding: 12px 0; }
.nav-item {
  display: block;
  padding: 10px 20px;
  color: #555;
  text-decoration: none;
  font-size: 14px;
  border-left: 3px solid transparent;
}
.nav-item:hover { background: #fef3f3; color: var(--brand-500, #DE1313); }
.nav-item.active {
  background: #fde8e8;
  color: var(--brand-500, #DE1313);
  border-left-color: var(--brand-500, #DE1313);
  font-weight: 600;
}
.main { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.topbar {
  height: 56px;
  background: #fff;
  border-bottom: 1px solid #eee;
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 24px;
}
.breadcrumb { font-size: 15px; font-weight: 600; color: #222; }
.topbar-right { display: flex; align-items: center; gap: 16px; }
.user-info { color: #555; font-size: 14px; }
.role-tag {
  display: inline-block;
  padding: 1px 8px;
  margin-left: 6px;
  font-size: 11px;
  background: #eef3ff;
  color: #1565C0;
  border-radius: 3px;
}
.logout-btn {
  padding: 5px 14px;
  background: #fff;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  color: #666;
}
.logout-btn:hover { border-color: var(--brand-500, #DE1313); color: var(--brand-500, #DE1313); }
.content { flex: 1; padding: 24px; overflow: auto; }
.fade-enter-active, .fade-leave-active { transition: opacity .15s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
