<script setup>
/**
 * HBarChart.vue —— 水平条形图（仪表盘：分类商品分布）。
 *
 * 移植自 admin.html hbarChart()（行 313-356），视觉零偏差。
 * props:
 *   data  : Array<{name: string, value: number}>  按 value 降序、去 0 值、最多 14 条（同原函数）
 *   color : string  条形颜色（可选，默认 --primary）
 *   title : string
 */
import { ref, onMounted, watch, onBeforeUnmount } from 'vue'
import * as d3 from 'd3'
import { cssVar, themeColor, esc } from '@/utils/format'

const props = defineProps({
  data:  { type: Array,  default: () => [] },
  color: { type: String, default: '' },
  title: { type: String, default: '' }
})

const wrapRef = ref(null)

function draw() {
  const wrap = wrapRef.value
  if (!wrap) return
  d3.select(wrap).selectAll('*').remove()

  const data = (props.data || []).filter(x => (x.value || 0) > 0).sort((a, b) => b.value - a.value).slice(0, 14)
  if (!data.length) {
    wrap.innerHTML = `<div class="chart-empty">暂无数据</div>`
    return
  }

  const W = 760, rowH = 30, H = data.length * rowH + 60
  const margin = { top: 18, right: 70, bottom: 30, left: 140 }
  const width = W - margin.left - margin.right, height = H - margin.top - margin.bottom
  const barColor = props.color ? themeColor(props.color) : cssVar('--primary')

  const div = d3.select(wrap)
  const svg = div.append('svg')
    .attr('viewBox', `0 0 ${W} ${H}`)
    .attr('preserveAspectRatio', 'xMidYMid meet')
    .style('width', '100%').style('height', 'auto').style('display', 'block')
  const g = svg.append('g').attr('transform', `translate(${margin.left},${margin.top})`)

  const x = d3.scaleLinear().domain([0, d3.max(data, d => d.value)]).nice().range([0, width])
  const y = d3.scaleBand().domain(data.map(d => d.name)).range([0, height]).padding(0.22)

  g.append('g').attr('class', 'grid')
    .attr('transform', `translate(0,${height})`)
    .call(d3.axisBottom(x).tickSize(-height).tickFormat(''))
    .call(g => g.select('.domain').remove())
    .attr('stroke', cssVar('--border')).attr('stroke-opacity', 0.6)

  g.selectAll('.bar').data(data).join('rect')
    .attr('class', 'bar')
    .attr('x', 0).attr('y', d => y(d.name))
    .attr('width', d => x(d.value)).attr('height', y.bandwidth())
    .attr('rx', 5).attr('fill', barColor)
    .append('title').text(d => `${esc(d.name)}：${d.value}`)

  g.selectAll('.label').data(data).join('text')
    .attr('x', -10).attr('y', d => y(d.name) + y.bandwidth() / 2)
    .attr('dy', '0.35em').attr('text-anchor', 'end')
    .attr('font-size', 12).attr('fill', cssVar('--foreground'))
    .text(d => esc(d.name))

  g.selectAll('.val').data(data).join('text')
    .attr('x', d => x(d.value) + 8).attr('y', d => y(d.name) + y.bandwidth() / 2)
    .attr('dy', '0.35em')
    .attr('font-size', 12).attr('fill', cssVar('--muted-foreground')).text(d => d.value)
}

let ro = null
onMounted(() => {
  draw()
  if (wrapRef.value?.parentElement) {
    ro = new ResizeObserver(() => draw())
    ro.observe(wrapRef.value.parentElement)
  }
})
watch(() => [props.data, props.color], () => draw(), { deep: true })
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
