/**
 * usePermission composable —— 让模板里直接调用 hasPerm/hasRole。
 *
 *   在 <script setup> 里：const { hasPerm, hasRole } = usePermission()
 *   在模板里：          v-if="hasPerm('product:edit')"
 *
 * 实现上只是对 auth store 的薄包装，因为 router 守卫、layout 菜单过滤
 * 和页面级按钮显示都要用到同一套权限逻辑。
 */
import { useAuthStore } from '@/stores/auth'

export function usePermission() {
  const auth = useAuthStore()
  return {
    hasPerm: (...codes) => auth.hasPerm(...codes),
    hasRole: (r) => auth.hasRole(r),
    isAdmin: () => auth.role === 'ADMIN',
    user: auth.user
  }
}

/**
 * is 守卫：role 枚举（'ADMIN' | 'OPS' | 'CS'）的短路径。
 * 在 router/index.js 里已经有 role 级别的判断；这里给组件用。
 */
export const roles = {
  ADMIN: 'ADMIN',
  OPS:   'OPS',
  CS:    'CS'
}
