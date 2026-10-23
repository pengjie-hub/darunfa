<script>
// 模块作用域计数器：跨组件实例共享，用于渐变 defs 的唯一 id
let trendUidSeq = 0
</script>

<script setup>
/**
 * TrendChart.vue —— 销售趋势折线 + 面积图（仪表盘：近 7 天销售趋势）。
 *
 * 移植自 admin.html trendChart()（行 358-486），视觉零偏差。
 * 注意原函数返回的是 summary HTML + SVG HTML 拼接字符串。
 * 这里把 summary（最新日销售额 / 日环比 / 7 天累计）拆到 Vue 模板，
 * 折线/面积部分仍由 D3 生成 SVG。
 *
 * props:
 *   data  : Array<{date: string, salesAmount: number|string}>  近 N 个自然日
 *   title : string
 */
import { ref, computed, onMounted, watch, onBeforeUnmount } from 'vue'
import * as d3 from 'd3'
import { fmtMoney, fmtK, cssVar } from '@/utils/format'

const props = defineProps({
  data:  { type: Array,  default: () => [] },
  title: { type: String, default: '' }
})

const wrapRef = ref(null)

// —— 日环比计算（原 trendChart 核心逻辑，不能错）——
const summary = computed(() => {
  const data = props.data || []
  if (!data.length) return { total: 0, latest: 0, prev: null, badgeClass: 'gray', badgeText: '无对比基准', badgeTip: '窗口内不足 2 天', badgeCaption: '较前一日' }
  const total = d3.sum(data, d => Number(d.salesAmount) || 0)
  const latestPoint = data[data.length - 1]
  const latest = Number(latestPoint && latestPoint.salesAmount) || 0
  const prevPoint = data.length >= 2 ? data[data.length - 2] : null
  const prev = prevPoint ? (Number(prevPoint.salesAmount) || 0) : null

  let badgeClass, badgeText, badgeTip, badgeCaption = '较前一日'
  if (prev === null) {
    badgeClass = 'gray'; badgeText = '无对比基准'; badgeTip = '窗口内仅有 1 天数据，无法计算日环比'
  } else if (prev === 0) {
    if (latest === 0) {
      badgeClass = 'gray'; badgeText = '0.00%'; badgeTip = `昨日(${prevPoint.date})与今日销售额均为 ¥0.00，环比持平`
    } else {
      badgeClass = 'gray'; badgeText = '环比不可比'; badgeTip = `昨日(${prevPoint.date})销售额为 ¥0.00，不能作分母；今日 ¥${latest.toFixed(2)}，环比无法计算`
    }
  } else {
    const change = ((latest - prev) / prev) * 100
    if (change === 0) {
      badgeClass = 'gray'; badgeText = '0.00%'; badgeTip = `与前一日 ${prevPoint.date} 持平：均为 ¥${latest.toFixed(2)}`
    } else {
      const up = change > 0
      badgeClass = up ? 'green' : 'red'
      badgeText = `${up ? '+' : ''}${change.toFixed(2)}%`
      badgeTip = `较前一日 ${prevPoint.date}：¥${prev.toFixed(2)} → ¥${latest.toFixed(2)}（环比 ${badgeText}）`
    }
  }
  if (prevPoint) badgeCaption = `较前一日 ${prevPoint.date}`

  return { total, latest, prevPoint, badgeClass, badgeText, badgeTip, badgeCaption }
})

function draw() {
  const wrap = wrapRef.value
  if (!wrap) return
  d3.select(wrap).selectAll('*').remove()

  const data = (props.data || []).filter(d => d && d.date)
  if (!data.length) {
    wrap.innerHTML = `<div class="chart-empty">暂无数据</div>`
    return
  }

  // trendChart 内部计数器 —— 生成渐变 defs 的唯一 id
  // 注意：不能在 <script setup> 里写 TrendChart._id 引用组件自身（未声明标识符，会抛 ReferenceError），
  // 改用模块作用域的计数器（见文末普通 <script> 块）
  const uid = 'trend-' + (++trendUidSeq)

  const W = 960, H = 300
  const margin = { top: 18, right: 32, bottom: 48, left: 74 }
  const width = W - margin.left - margin.right, height = H - margin.top - margin.bottom
  const primary = cssVar('--primary')
  const border = cssVar('--border')
  const muted = cssVar('--muted-foreground')

  const svg = d3.select(wrap).append('svg')
    .attr('viewBox', `0 0 ${W} ${H}`)
    .attr('preserveAspectRatio', 'xMidYMid meet')
    .style('width', '100%').style('height', 'auto').style('display', 'block')
  const g = svg.append('g').attr('transform', `translate(${margin.left},${margin.top})`)

  const defs = svg.append('defs')
  const areaGrad = defs.append('linearGradient').attr('id', `${uid}-area`).attr('x1', 0).attr('y1', 0).attr('x2', 0).attr('y2', 1)
  areaGrad.append('stop').attr('offset', '0%').attr('stop-color', primary).attr('stop-opacity', 0.28)
  areaGrad.append('stop').attr('offset', '100%').attr('stop-color', primary).attr('stop-opacity', 0.02)

  const x = d3.scalePoint().domain(data.map(d => d.date)).range([0, width]).padding(0.06)
  const maxSales = d3.max(data, d => Number(d.salesAmount) || 0) || 0
  const ySales = d3.scaleLinear().domain([0, maxSales * 1.15]).nice().range([height, 0])

  // 横向网格
  g.append('g').attr('class', 'grid')
    .call(d3.axisLeft(ySales).tickSize(-width).tickFormat(''))
    .call(g => g.select('.domain').remove())
    .attr('stroke', border).attr('stroke-opacity', 0.35).attr('shape-rendering', 'crispEdges')

  // 坐标轴
  const xAxis = g.append('g').attr('transform', `translate(0,${height})`)
    .call(d3.axisBottom(x).tickSize(0).tickPadding(12))
  xAxis.select('.domain').attr('stroke', border)
  xAxis.selectAll('text').attr('fill', muted).attr('font-size', 11).attr('font-weight', 500)

  const yLeft = g.append('g').call(d3.axisLeft(ySales).ticks(5).tickFormat(d => '¥' + fmtK(d)).tickSize(0).tickPadding(10))
  yLeft.select('.domain').remove()
  yLeft.selectAll('text').attr('fill', muted).attr('font-size', 11)

  // 面积
  const area = d3.area()
    .x(d => x(d.date))
    .y0(height)
    .y1(d => ySales(Number(d.salesAmount) || 0))
    .curve(d3.curveMonotoneX)
  g.append('path').datum(data).attr('fill', `url(#${uid}-area)`).attr('d', area)

  // 折线
  const line = d3.line()
    .x(d => x(d.date))
    .y(d => ySales(Number(d.salesAmount) || 0))
    .curve(d3.curveMonotoneX)
  g.append('path').datum(data).attr('fill', 'none')
    .attr('stroke', primary).attr('stroke-width', 2.5).attr('stroke-linecap', 'round').attr('d', line)

  // 点 + tooltip
  g.selectAll('.dot').data(data).join('circle')
    .attr('cx', d => x(d.date))
    .attr('cy', d => ySales(Number(d.salesAmount) || 0))
    .attr('r', 4).attr('fill', '#fff').attr('stroke', primary).attr('stroke-width', 2)
    .append('title').text(d => `${d.date} 销售额 ¥${Number(d.salesAmount).toFixed(2)}`)
}

let ro = null
onMounted(() => {
  draw()
  if (wrapRef.value?.parentElement) {
    ro = new ResizeObserver(() => draw())
    ro.observe(wrapRef.value.parentElement)
  }
})
watch(() => props.data, () => draw(), { deep: true })
onBeforeUnmount(() => { ro?.disconnect() })
</script>

<template>
  <div class="chart-card">
    <div v-if="title" class="chart-title">{{ title }}</div>
    <!-- summary（Vue 模板，替换原 trendChart 返回的 HTML 字符串） -->
    <div v-if="summary" class="trend-summary">
      <div class="summary-item">
        <div class="summary-label">最新日销售额</div>
        <div class="summary-value">{{ fmtMoney(summary.latest) }}</div>
      </div>
      <span class="badge" :class="summary.badgeClass" :title="summary.badgeTip">{{ summary.badgeText }}</span>
      <span class="summary-caption">{{ summary.badgeCaption }}</span>
      <div class="summary-right">
        <div class="summary-label">7 天累计</div>
        <div class="summary-value sm">{{ fmtMoney(summary.total) }}</div>
      </div>
    </div>
    <div ref="wrapRef" class="chart-wrap"></div>
  </div>
</template>

<style scoped>
.chart-card { background: #fff; border-radius: 4px; padding: 16px; }
.chart-title { font-size: 14px; font-weight: 600; color: #222; margin-bottom: 12px; }
.chart-wrap { width: 100%; }
.chart-empty { padding: 40px; text-align: center; color: #aaa; font-size: 13px; }

.trend-summary {
  display: flex; align-items: flex-end; gap: 14px;
  margin-bottom: 14px; flex-wrap: wrap;
}
.summary-label { font-size: 12px; color: var(--muted-foreground, #888); margin-bottom: 2px; }
.summary-value { font-size: 28px; font-weight: 700; color: var(--foreground, #222); line-height: 1.05; }
.summary-value.sm { font-size: 16px; font-weight: 600; }
.summary-caption { font-size: 12px; color: var(--muted-foreground, #888); margin-bottom: 8px; }
.summary-right { margin-left: auto; text-align: right; }

/* 临时 badge 颜色定义（会被 admin-theme.css / element-overrides 覆盖，此处是兜底） */
.badge { padding: 2px 10px; border-radius: 999px; font-size: 12px; font-weight: 600; margin-bottom: 6px; }
.badge.green { background: #e8f5e9; color: #2e7d32; }
.badge.red   { background: #ffebee; color: #c62828; }
.badge.gray  { background: #f0f0f0; color: #666; }
</style>
