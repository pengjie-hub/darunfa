<script setup>
/**
 * DashboardView.vue —— 仪表盘完整实现。
 *
 * 移植自 admin.html renderDashboard()（行 540-741），全部 11 张 stat-card + 5 块面板。
 * 后端接口：
 *   GET /admin/dashboard        → DashboardDTO
 *   GET /admin/dashboard/charts → DashboardChartsDTO
 *   GET /admin/refunds          → 按权限，前端聚合 PENDING/APPROVED/REJECTED 计数
 *   GET /admin/risk/summary     → {high, medium, low, unread}
 *
 * 注意：退款和风险接口各自需要 order:view|cs:manage / risk:view 权限，
 * 无权限时 catch 兜底返回空，保证仪表盘不 403 整体垮掉。
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { fmtMoney, fmtDateTime } from '@/utils/format'
import { SLA_SHIP_DAYS, LOW_STOCK_THRESHOLD } from '@/utils/constants'

import StatCard from '@/components/common/StatCard.vue'
import DonutChart from '@/components/charts/DonutChart.vue'
import HBarChart from '@/components/charts/HBarChart.vue'
import TrendChart from '@/components/charts/TrendChart.vue'

const router = useRouter()
const auth = useAuthStore()

const loading = ref(true)

// —— 权限门（与 admin.html:545-546 一致）——
const canRefund = computed(() => auth.hasPerm('order:view', 'cs:manage'))
const canRisk   = computed(() => auth.hasPerm('risk:view'))

// —— 原始数据 ——
const dash  = ref(null)  // DashboardDTO
const charts = ref(null) // DashboardChartsDTO
const refunds = ref([])  // 原始退款列表（需聚合）
const riskSum = ref(null) // {high, medium, low, unread}

// —— 聚合结果（computed，防止模板里做大量 map）——
const statCards = computed(() => {
  const d = dash.value
  if (!d) return []
  return [
    { label: '会员总数',     value: d.userCount,         accent: 'blue' },
    { label: '商品总数',     value: d.productCount,      accent: '' },
    { label: '订单总数',     value: d.orderCount,        accent: '' },
    { label: '累计销售额',   value: fmtMoney(d.totalSales ?? 0), accent: 'green' },
    { label: '待付款',       value: d.pendingPaymentCount ?? 0, accent: 'orange' },
    { label: '待发货',       value: d.pendingShipCount ?? 0,     accent: 'orange' },
    {
      label: '超时未发货',
      value: d.overdueShipCount ?? 0,
      accent: (d.overdueShipCount ?? 0) > 0 ? 'red' : 'green',
      hint: (d.overdueShipCount ?? 0) > 0
        ? `最久 ${d.oldestOverdueDays ?? 0} 天 · ${fmtMoney(d.overdueShipAmount ?? 0)}`
        : '时效正常'
    },
    { label: '待收货',       value: d.shippedCount ?? 0,    accent: 'blue' },
    { label: '已完成',       value: d.completedCount ?? 0,  accent: 'green' },
    { label: '评论总数',     value: d.reviewCount ?? 0,     accent: '' },
    {
      label: '低库存商品',
      value: d.lowStockCount ?? 0,
      accent: (d.lowStockCount ?? 0) > 0 ? 'red' : 'green'
    }
  ]
})

const overdueShips = computed(() => dash.value?.overdueShips || [])
const overdueCount = computed(() => dash.value?.overdueShipCount ?? 0)
const overdueAmount = computed(() => dash.value?.overdueShipAmount ?? 0)
const overdueOldest = computed(() => dash.value?.oldestOverdueDays ?? 0)

const refundByStatus = computed(() => {
  const map = { PENDING: 0, APPROVED: 0, REJECTED: 0 }
  let pendingAmount = 0
  for (const r of refunds.value || []) {
    if (map[r.status] !== undefined) map[r.status]++
    if (r.status === 'PENDING') pendingAmount += Number(r.amount || 0)
  }
  return { ...map, pendingAmount }
})

const riskSummary = computed(() => ({
  high:   riskSum.value?.high   ?? 0,
  medium: riskSum.value?.medium ?? 0,
  low:    riskSum.value?.low    ?? 0,
  unread: riskSum.value?.unread ?? 0
}))

// —— 运营提示列表（与 admin.html:649-670 逐字一致）——
const opTips = computed(() => {
  const tips = []
  const d = dash.value
  if (!d) return tips

  // 待发货
  if (d.pendingShipCount > 0) {
    tips.push({
      type: 'warn',
      html: `有 <b>${d.pendingShipCount}</b> 笔订单待发货，请及时处理。`
    })
  } else {
    tips.push({ type: 'ok', html: '暂无待发货订单' })
  }
  // 超时未发货
  if (overdueCount.value > 0) {
    tips.push({
      type: 'danger',
      html: `其中 <b>${overdueCount.value}</b> 笔已超时未发货（付款超过 ${SLA_SHIP_DAYS} 天，最久 <b>${overdueOldest.value}</b> 天，合计 ${fmtMoney(overdueAmount.value)}），请尽快发货。`
    })
  } else {
    tips.push({
      type: 'ok',
      html: `待发货订单均在 ${SLA_SHIP_DAYS} 天时效内，暂无超时未发货`
    })
  }
  // 低库存
  if (d.lowStockCount > 0) {
    tips.push({
      type: 'danger',
      html: `有 <b>${d.lowStockCount}</b> 件商品库存偏低（≤${LOW_STOCK_THRESHOLD}），请到「库存管理」及时补货。`
    })
  } else {
    tips.push({ type: 'ok', html: '库存健康，无低库存商品' })
  }
  // 退款（按权限）
  if (canRefund.value) {
    if (refundByStatus.value.PENDING > 0) {
      tips.push({
        type: 'warn',
        html: `有 <b>${refundByStatus.value.PENDING}</b> 笔退款申请待处理（合计 <b>${fmtMoney(refundByStatus.value.pendingAmount)}</b>），请到「退款管理」及时审核。`
      })
    } else {
      tips.push({ type: 'ok', html: '暂无待处理退款申请' })
    }
  }
  // 风险（按权限）
  if (canRisk.value) {
    if (riskSummary.value.unread > 0) {
      tips.push({
        type: 'danger',
        html: `有 <b>${riskSummary.value.unread}</b> 条风险订单提醒未读，其中高风险 <b>${riskSummary.value.high}</b> 条，请到「风险监控」核查并标记已读。`
      })
    } else {
      tips.push({ type: 'ok', html: '风险提醒均已读，暂无待核查风险订单' })
    }
  }
  tips.push({ type: 'info', html: '促销、物流等完整运营能力已就绪，可通过左侧菜单操作。' })
  return tips
})

// —— 快捷操作按钮（与 admin.html:704-716 一致）——
const quickBtns = computed(() => {
  const d = dash.value
  if (!d) return []
  const list = [
    { label: `📦 处理待发货 (${d.pendingShipCount})`, path: '/orders', type: 'primary' }
  ]
  if (overdueCount.value > 0) {
    list.push({ label: `⏰ 超时未发货 (${overdueCount.value})`, path: '/orders', type: 'danger' })
  }
  list.push({ label: `📉 库存预警 (${d.lowStockCount})`, path: '/stock', type: 'secondary' })
  list.push({ label: '🛡️ 风险监控',     path: '/risk',       type: 'secondary' })
  list.push({ label: '🏷️ 促销活动',     path: '/promotions', type: 'secondary' })
  list.push({ label: '💬 评论管理',     path: '/reviews',    type: 'ghost' })
  return list
})

async function load() {
  loading.value = true
  try {
    const [d, c] = await Promise.all([
      api('/admin/dashboard'),
      api('/admin/dashboard/charts')
    ])
    dash.value = d
    charts.value = c

    // 退款 & 风险 —— 403 兜底，不能让一个无权限的并行请求把整个仪表盘拖垮
    const extras = await Promise.allSettled([
      canRefund.value ? api('/admin/refunds') : Promise.resolve(null),
      canRisk.value   ? api('/admin/risk/summary') : Promise.resolve(null)
    ])
    refunds.value = extras[0].status === 'fulfilled' && extras[0].value ? extras[0].value : []
    riskSum.value = extras[1].status === 'fulfilled' && extras[1].value ? extras[1].value : null
  } catch (e) {
    ElMessage.error(e.message || '加载仪表盘失败')
  } finally {
    loading.value = false
  }
}

function go(path) { router.push(path) }

onMounted(load)
</script>

<template>
  <div class="dash-wrap" v-loading="loading">

    <!-- 11 张统计卡 -->
    <div class="stat-grid">
      <StatCard
        v-for="s in statCards"
        :key="s.label"
        :label="s.label"
        :value="s.value"
        :hint="s.hint || ''"
        :accent="s.accent"
      />
    </div>

    <!-- 超时未发货（只有真有超时才渲染 —— 空占版面是 admin.html 刻意回避的） -->
    <div v-if="overdueCount > 0" class="panel panel-danger">
      <div class="panel-head">
        <h3>⏰ 超时未发货（付款后超过 {{ SLA_SHIP_DAYS }} 天仍未发货）</h3>
        <el-button link type="danger" @click="go('/orders')">前往发货 →</el-button>
      </div>
      <div class="panel-body">
        <el-table :data="overdueShips" size="small" stripe>
          <el-table-column prop="orderNo" label="订单号" width="220">
            <template #default="{ row }">{{ row.orderNo || '-' }}</template>
          </el-table-column>
          <el-table-column prop="userName" label="下单用户" width="140">
            <template #default="{ row }">{{ row.userName || '-' }}</template>
          </el-table-column>
          <el-table-column label="订单金额" width="130">
            <template #default="{ row }"><b>{{ fmtMoney(row.payAmount) }}</b></template>
          </el-table-column>
          <el-table-column label="付款时间" width="190">
            <template #default="{ row }">{{ fmtDateTime(row.paidAt) }}</template>
          </el-table-column>
          <el-table-column label="已等待" width="100">
            <template #default="{ row }">
              <span :class="['days-badge', row.waitingDays >= 3 ? 'danger' : 'warn']">{{ row.waitingDays }} 天</span>
            </template>
          </el-table-column>
        </el-table>
        <div class="hint-text">
          共 <b>{{ overdueCount }}</b> 笔超时订单，合计 <b>{{ fmtMoney(overdueAmount) }}</b>
          <template v-if="overdueCount > overdueShips.length">
            ；上表仅列出等待最久的 {{ overdueShips.length }} 笔，完整列表请到「订单管理」查看。
          </template>
          <template v-else>。</template>
        </div>
      </div>
    </div>

    <!-- 退款信息 + 风险订单 —— 按权限显示；并排 / 独占整行 -->
    <div class="panel-grid" v-if="canRefund || canRisk">
      <div v-if="canRefund" class="panel">
        <div class="panel-head">
          <h3>退款信息</h3>
          <el-button link type="primary" @click="go('/refunds')">前往处理 →</el-button>
        </div>
        <div class="panel-body">
          <div class="stat-grid tight">
            <StatCard label="待处理退款" :value="refundByStatus.PENDING"
                      :accent="refundByStatus.PENDING > 0 ? 'orange' : 'green'" />
            <StatCard label="待退金额" :value="fmtMoney(refundByStatus.pendingAmount)" accent="green" />
            <StatCard label="已同意"   :value="refundByStatus.APPROVED" accent="green" />
            <StatCard label="已拒绝"   :value="refundByStatus.REJECTED" accent="" />
          </div>
          <div class="hint-text">
            累计申请 {{ refunds.length }} 笔。
            <template v-if="refundByStatus.PENDING > 0">
              待处理申请已计入上方「待退金额」；同意退款后才会真正退回款项、回补库存并返还优惠券。
            </template>
            <template v-else>
              当前没有待处理的退款申请。
            </template>
          </div>
        </div>
      </div>

      <div v-if="canRisk" class="panel">
        <div class="panel-head">
          <h3>风险订单</h3>
          <el-button link type="primary" @click="go('/risk')">前往核查 →</el-button>
        </div>
        <div class="panel-body">
          <div class="stat-grid tight">
            <StatCard label="高风险"   :value="riskSummary.high"   :accent="riskSummary.high > 0 ? 'red' : 'green'" />
            <StatCard label="中风险"   :value="riskSummary.medium" accent="orange" />
            <StatCard label="低风险"   :value="riskSummary.low"    accent="blue" />
            <StatCard label="未读提醒" :value="riskSummary.unread"
                      :accent="riskSummary.unread > 0 ? '' : 'green'" />
          </div>
          <div class="hint-text">
            风险评分由规则引擎（金额异常 / 短时多单 / 敏感地址 / 首单大额 / 凌晨下单）+ AI 综合判断自动生成。
          </div>
        </div>
      </div>
    </div>

    <!-- 运营提示 -->
    <div class="panel">
      <div class="panel-head"><h3>运营提示</h3></div>
      <div class="panel-body">
        <ul class="tips-list">
          <li v-for="(tip, i) in opTips" :key="i" :class="['tip', tip.type]">
            <span v-html="tip.html"></span>
          </li>
        </ul>
      </div>
    </div>

    <!-- 快捷操作 -->
    <div class="panel">
      <div class="panel-head"><h3>快捷操作</h3></div>
      <div class="panel-body">
        <div class="quick-btns">
          <el-button
            v-for="b in quickBtns" :key="b.label"
            :type="b.type === 'primary' ? 'primary'
                 : b.type === 'danger'  ? 'danger'
                 : b.type === 'ghost'  ? 'info'
                 : 'default'"
            :plain="b.type !== 'primary' && b.type !== 'danger'"
            @click="go(b.path)"
          >{{ b.label }}</el-button>
        </div>
      </div>
    </div>

    <!-- 图表区 -->
    <div class="chart-grid">
      <!-- 近 7 天销售趋势（全宽） -->
      <div class="panel panel-wide">
        <div class="panel-head"><h3>近 7 天销售趋势</h3></div>
        <div class="panel-body">
          <TrendChart :data="charts?.salesTrend || []" />
        </div>
      </div>

      <!-- 订单状态分布 -->
      <div class="panel">
        <div class="panel-head"><h3>订单状态分布</h3></div>
        <div class="panel-body">
          <DonutChart :data="charts?.orderStatusDist || []" centerLabel="订单" />
        </div>
      </div>

      <!-- 支付方式分布 -->
      <div class="panel">
        <div class="panel-head"><h3>支付方式分布</h3></div>
        <div class="panel-body">
          <DonutChart :data="charts?.paymentDist || []" centerLabel="笔数" />
        </div>
      </div>

      <!-- 分类商品分布（全宽） -->
      <div class="panel panel-wide">
        <div class="panel-head">
          <h3>分类商品分布（按一级分类）</h3>
          <span class="hint-small">
            口径：全部商品（含下架）按所属<b>一级分类</b>上卷统计，各条之和 = 上方「商品总数」
          </span>
        </div>
        <div class="panel-body">
          <HBarChart :data="charts?.categoryDist || []" />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dash-wrap { padding: 0; }

/* —— 11 张统计卡 —— */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 10px;
  margin-bottom: 18px;
}
.stat-grid .stat-card { min-width: 100px; }

/* —— 面板容器（超时/退款/风险/运营提示/快捷操作 —— admin.html .panel 是 .panel { background:#fff; border-radius:...; margin:22px 0 }）—— */
.panel {
  background: #fff;
  border-radius: 4px;
  margin-bottom: 18px;
  border-left: 3px solid #fff;
}
.panel-head {
  display: flex; justify-content: space-between; align-items: center;
  padding: 14px 18px 10px;
  border-bottom: 1px solid #f0f0f0;
}
.panel-head h3 { margin: 0; font-size: 15px; font-weight: 600; color: #222; }
.panel-body { padding: 16px 18px; }

.panel-danger { border-left-color: var(--brand-500, #DE1313); }
.panel-wide { grid-column: 1 / -1; }

/* —— 并排面板 —— */
.panel-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  margin-bottom: 18px;
}

/* —— 图表区 grid —— */
.chart-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

/* —— 运营提示 —— */
.tips-list {
  list-style: disc; padding-left: 20px;
  line-height: 2.2; color: var(--text-600, #555); font-size: 14px; margin: 0;
}
.tips-list .tip.warn b   { color: var(--warning, #FF7A1A); }
.tips-list .tip.danger b { color: var(--brand-500, #DE1313); }
.tips-list .tip.ok       { color: #2e7d32; }
.tips-list .tip.info     { color: #666; }

/* —— 快捷操作 —— */
.quick-btns { display: flex; gap: 10px; flex-wrap: wrap; }
.quick-btns .el-button { font-size: 14px; }

/* —— 辅助 —— */
.hint-text { margin-top: 12px; font-size: 13px; color: #888; }
.hint-small { font-size: 12px; color: #999; }
.days-badge { padding: 2px 8px; border-radius: 3px; font-size: 12px; font-weight: 600; }
.days-badge.warn   { background: #fff3e0; color: #ef6c00; }
.days-badge.danger { background: #ffebee; color: #c62828; }

/* —— 适配小屏 —— */
@media (max-width: 1200px) { .stat-grid { grid-template-columns: repeat(4, 1fr); } }
@media (max-width: 900px)  { .panel-grid, .chart-grid { grid-template-columns: 1fr; } }
</style>
