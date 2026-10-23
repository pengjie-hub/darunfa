<script setup>
/**
 * DashboardView.vue —— 仪表盘（P2 消费公共层的验证版本）。
 *
 * 真正的 P3 迁移会把 admin.html renderDashboard() 全部逻辑搬过来；
 * 这里先用真实 API 把 4 个 StatCard + 4 张图（Donut / Trend / HBar）画出来，
 * 目的是验证：
 *   - axios http.js 能跑 /dashboard + /dashboard/charts 接口
 *   - D3 三个图表组件能正确消费数据（尤其是 trendChart 的日环比不能恒显示 +0.00%）
 *   - Element Plus + admin-theme.css 色彩在组件里一致
 */
import { ref, onMounted } from 'vue'
import { api } from '@/api/http'
import { fmtMoney } from '@/utils/format'
import StatCard from '@/components/common/StatCard.vue'
import DonutChart from '@/components/charts/DonutChart.vue'
import HBarChart from '@/components/charts/HBarChart.vue'
import TrendChart from '@/components/charts/TrendChart.vue'

const loading = ref(true)

// 统计卡（仪表盘顶部 4 张）
const stats = ref({
  memberCount: 0, productCount: 0, orderCount: 0, totalSales: 0,
  pendingPayment: 0, pendingShip: 0, pendingReceive: 0, completed: 0
})

// 图表数据
const donut1 = ref([])  // 订单状态分布
const donut2 = ref([])  // 支付方式分布
const hBar   = ref([])  // 分类商品分布
const trend  = ref([])  // 近 7 天销售趋势

async function load() {
  loading.value = true
  try {
    const [dashboard, charts] = await Promise.all([
      api('/admin/dashboard'),
      api('/admin/dashboard/charts')
    ])

    stats.value = {
      userCount:       dashboard.userCount ?? 0,
      productCount:    dashboard.productCount ?? 0,
      orderCount:      dashboard.orderCount ?? 0,
      totalSales:      dashboard.totalSales ?? 0,
      pendingPayment:  dashboard.pendingPaymentCount ?? 0,
      pendingShip:     dashboard.pendingShipCount ?? 0,
      pendingReceive:  dashboard.shippedCount ?? 0,
      completed:       dashboard.completedCount ?? 0
    }

    donut1.value = charts.orderStatusDist || []
    donut2.value = charts.paymentMethodDist || []
    hBar.value   = charts.categoryDist || []
    trend.value  = charts.salesTrend || []
  } catch (e) {
    console.warn('dashboard load fail:', e.message)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-wrap">
    <div class="page-loading" v-if="loading">加载中…</div>
    <template v-else>
      <!-- 4 张统计卡 -->
      <div class="stat-grid">
        <StatCard label="会员总数"  :value="stats.userCount" />
        <StatCard label="商品总数"  :value="stats.productCount" />
        <StatCard label="订单总数"  :value="stats.orderCount" />
        <StatCard label="累计销售额" :value="fmtMoney(stats.totalSales)" accent="green" />
      </div>

      <div class="stat-grid small-row">
        <StatCard label="待付款"   :value="stats.pendingPayment" accent="orange" />
        <StatCard label="待发货"   :value="stats.pendingShip"    accent="red" />
        <StatCard label="待收货"   :value="stats.pendingReceive" accent="blue" />
        <StatCard label="已完成"   :value="stats.completed"      accent="green" />
      </div>

      <!-- 销售趋势 -->
      <TrendChart :data="trend" title="近 7 天销售趋势" />

      <!-- 三个图并排 -->
      <div class="chart-grid">
        <DonutChart :data="donut1" title="订单状态分布" centerLabel="54 订单" />
        <DonutChart :data="donut2" title="支付方式分布" />
        <HBarChart  :data="hBar"   title="分类商品分布（按一级分类）" />
      </div>
    </template>
  </div>
</template>

<style scoped>
.page-wrap { background: #f5f6f8; }
.page-loading { padding: 60px; text-align: center; color: #aaa; }
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px; margin-bottom: 14px;
}
.stat-grid.small-row .stat-value { font-size: 22px; }
.chart-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px; margin-top: 14px;
}
.chart-grid > :last-child { grid-column: 1 / -1; }
</style>
