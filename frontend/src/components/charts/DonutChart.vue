<script setup>
/**
 * DonutChart.vue —— 环形饼图（仪表盘：订单状态分布 / 支付方式分布 / 分类商品分布）。
 *
 * 移植自 admin.html donutChart()（行 274-311），视觉零偏差。
 * props:
 *   data        : Array<{name: string, value: number}>  传入 {value > 0} 的项，内部自动过滤
 *   centerLabel : string  圆心下方小字（默认 "总数"）
 *   title       : string  图表标题（可选，显示在容器上方）
 */
import { ref, onMounted, watch, onBeforeUnmount } from 'vue'
import * as d3 from 'd3'
import { CHART_COLOR_VARS } from '@/utils/constants'
import { cssVar, themeColor, esc } from '@/utils/format'

const props = defineProps({
  data:        { type: Array,  default: () => [] },
  centerLabel:  { type: String, default: '总数' },
  title:        { type: String, default: '' }
})

const wrapRef = ref(null)

function draw() {
  const wrap = wrapRef.value
  if (!wrap) return
  // 清空旧图
  d3.select(wrap).selectAll('*').remove()

  const data = (props.data || []).filter(x => (x.value || 0) > 0)
  if (!data.length) {
    wrap.innerHTML = `<div class="chart-empty">暂无数据</div>`
    return
  }

  const total = d3.sum(data, d => d.value)
  const W = 420, H = 220, radius = 82, cx = 110, cy = H / 2
  const color = d3.scaleOrdinal(data.map(d => d.name)).range(CHART_COLOR_VARS.map(themeColor))

  const div = d3.select(wrap)
  const svg = div.append('svg')
    .attr('viewBox', `0 0 ${W} ${H}`)
    .attr('preserveAspectRatio', 'xMidYMid meet')
    .style('width', '100%').style('height', 'auto').style('display', 'block')

  const g = svg.append('g').attr('transform', `translate(${cx},${cy})`)
  const pie = d3.pie().value(d => d.value).sort(null).padAngle(0.03)
  const arc = d3.arc().innerRadius(radius * 0.62).outerRadius(radius)
  const arcs = g.selectAll('.arc').data(pie(data)).join('g').attr('class', 'arc')
  arcs.append('path')
    .attr('d', arc)
    .attr('fill', d => color(d.data.name))
    .attr('stroke', cssVar('--card'))
    .attr('stroke-width', 2)
    .append('title').text(d => `${esc(d.data.name)}：${d.data.value}（${((d.endAngle - d.startAngle) / (2 * Math.PI) * 100).toFixed(1)}%）`)

  g.append('text').attr('text-anchor', 'middle').attr('dy', '-0.15em')
    .attr('font-size', 22).attr('font-weight', 600).attr('fill', cssVar('--foreground')).text(total)
  g.append('text').attr('text-anchor', 'middle').attr('dy', '1.4em')
    .attr('font-size', 11).attr('fill', cssVar('--muted-foreground')).text(props.centerLabel)

  const legend = svg.append('g').attr('transform', `translate(${cx + radius + 34}, ${cy - data.length * 13})`)
  const rows = legend.selectAll('.lg').data(data).join('g').attr('transform', (d, i) => `translate(0,${i * 26})`)
  rows.append('circle').attr('r', 5).attr('fill', d => color(d.name))
  rows.append('text').attr('x', 12).attr('y', 4).attr('font-size', 12).attr('fill', cssVar('--foreground')).text(d => esc(d.name))
  rows.append('text').attr('x', 12).attr('y', 16).attr('font-size', 11).attr('fill', cssVar('--muted-foreground'))
    .text(d => { const pct = (d.value / total * 100).toFixed(1); return `${d.value} · ${pct}%`; })
}

let ro = null
onMounted(() => {
  draw()
  if (wrapRef.value?.parentElement) {
    ro = new ResizeObserver(() => draw())
    ro.observe(wrapRef.value.parentElement)
  }
})
watch(() => [props.data, props.centerLabel], () => draw(), { deep: true })
onBeforeUnmount(() => { ro?.disconnect() })
</script>

<template>
  <div class="chart-card">
    <div v-if="title" class="chart-title">{{ title }}</div>
    <div ref="wrapRef" class="chart-wrap"></div>
  </div>
</template>

<style scoped>
.chart-card { background: #fff; border-radius: 4px; padding: 16px; }
.chart-title { font-size: 14px; font-weight: 600; color: #222; margin-bottom: 12px; }
.chart-wrap { width: 100%; }
.chart-empty { padding: 40px; text-align: center; color: #aaa; font-size: 13px; }
</style>
