/**
 * auth store：登录态 + 用户信息 + 权限。
 * 对齐 admin.html 里 ME / hasPerm / hasRole / loadMe 的语义。
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { api, TOKEN_KEY, USER_KEY } from '@/api/http'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '')
  const user = ref(null) // { id, username, nickname, phone, email, role, permissions }

  const isLoggedIn = computed(() => !!token.value)
  const role = computed(() => user.value?.role || '')
  const permissions = computed(() => user.value?.permissions || [])

  function hasPerm(...codes) {
    if (!user.value) return false
    if (user.value.role === 'ADMIN') return true
    const set = user.value.permissions || []
    return codes.some(c => set.includes(c))
  }

  function hasRole(r) {
    return user.value?.role === r
  }

  async function login(username, password) {
    const data = await api('/auth/login', 'POST', { username, password })
    token.value = data.token || ''
    localStorage.setItem(TOKEN_KEY, token.value)
    await fetchMe()
  }

  async function fetchMe() {
    const me = await api('/auth/me')
    user.value = me
    localStorage.setItem(USER_KEY, JSON.stringify(me))
    return me
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  // 从 localStorage 恢复（刷新页面时用）
  function restore() {
    token.value = localStorage.getItem(TOKEN_KEY) || ''
    const raw = localStorage.getItem(USER_KEY)
    if (raw) {
      try { user.value = JSON.parse(raw) } catch (_) { /* ignore */ }
    }
  }

  return {
    token, user,
    isLoggedIn, role, permissions,
    hasPerm, hasRole,
    login, fetchMe, logout, restore
  }
})
