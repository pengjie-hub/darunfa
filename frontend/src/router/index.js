/**
 * 路由 + 权限守卫。
 * 路由表来自改造计划 3.3 节，与 admin.html 侧栏 data-mod 一一对应。
 * - meta.permission：逗号分隔的 OR 语义权限码；或 adminOnly: true 仅超级管理员
 * - 路由历史：createWebHashHistory（Spring Boot 托管下刷新子路由不会 404）
 */
import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  { path: '/login', component: () => import('@/views/login/LoginView.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard',    component: () => import('@/views/dashboard/DashboardView.vue'),  meta: { title: '仪表盘' } },
      { path: 'products',     component: () => import('@/views/products/ProductList.vue'),      meta: { title: '商品管理', permission: 'product:view' } },
      { path: 'orders',       component: () => import('@/views/orders/OrderList.vue'),          meta: { title: '订单管理', permission: 'order:view' } },
      { path: 'refunds',      component: () => import('@/views/refunds/RefundList.vue'),       meta: { title: '退款管理', permission: 'order:view,cs:manage' } },
      { path: 'risk',         component: () => import('@/views/risk/RiskView.vue'),           meta: { title: '风险监控', permission: 'risk:view' } },
      { path: 'stock',        component: () => import('@/views/stock/StockView.vue'),         meta: { title: '库存管理', permission: 'product:view' } },
      { path: 'members',      component: () => import('@/views/members/MemberList.vue'),     meta: { title: '会员管理', permission: 'member:view' } },
      { path: 'roles',        component: () => import('@/views/roles/RoleView.vue'),         meta: { title: '角色管理', adminOnly: true } },
      { path: 'permissions',  component: () => import('@/views/permissions/PermissionView.vue'), meta: { title: '权限管理', adminOnly: true } },
      { path: 'reviews',      component: () => import('@/views/reviews/ReviewView.vue'),     meta: { title: '评论管理', permission: 'review:reply' } },
      { path: 'cs',           component: () => import('@/views/cs/CsView.vue'),              meta: { title: '客服管理', permission: 'cs:manage' } },
      { path: 'categories',   component: () => import('@/views/categories/CategoryView.vue'),meta: { title: '分类管理', permission: 'product:view' } },
      { path: 'promotions',   component: () => import('@/views/promotions/PromotionView.vue'),meta: { title: '促销管理', permission: 'promotion:manage' } },
      { path: 'payments',     component: () => import('@/views/payments/PaymentList.vue'),   meta: { title: '支付管理', permission: 'order:view' } },
      { path: 'shipments',    component: () => import('@/views/shipments/ShipmentList.vue'), meta: { title: '物流管理', permission: 'order:view' } },
      { path: 'knowledge',    component: () => import('@/views/knowledge/KnowledgeView.vue'), meta: { title: 'AI 知识库', adminOnly: true } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior() { return { top: 0 } }
})

// —— 守卫 ——
router.beforeEach(async (to, from) => {
  const auth = useAuthStore()

  // 1. 公共页放行
  if (to.meta.public) {
    // 已登录还访问 login → 去 dashboard
    if (auth.isLoggedIn && to.path === '/login') return { path: '/dashboard' }
    return true
  }

  // 2. 没 token → 登录
  if (!auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 3. 有 token 但没 user → 尝试拉一次（刷新场景）
  if (!auth.user) {
    try {
      await auth.fetchMe()
    } catch (e) {
      // token 无效，清掉并跳登录
      auth.logout()
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }

  // 4. adminOnly 校验
  if (to.meta.adminOnly && auth.role !== 'ADMIN') {
    return { path: '/dashboard' }
  }

  // 5. permission 校验（OR 语义，逗号分隔）
  if (to.meta.permission) {
    const codes = to.meta.permission.split(',').map(s => s.trim()).filter(Boolean)
    if (codes.length && !auth.hasPerm(...codes)) {
      return { path: '/dashboard' }
    }
  }

  return true
})

export default router
