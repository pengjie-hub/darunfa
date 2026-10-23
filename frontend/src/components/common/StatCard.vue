<script setup>
/**
 * StatCard.vue —— 统计卡，DOM 结构严格对齐 admin-theme.css 的选择器：
 *   .stat-card > .label + .value[.red|.green|.orange|.blue|.primary] + .trend
 *
 * 样式完全由 admin-theme.css 全局接管（24px 圆角 / hover 上浮 / 阴影 / 强调色色值）。
 * accent 参数映射到 .value 的强调色类，与 admin-theme.css 第 303-307 行逐字对应：
 *   'red'    → color: var(--destructive)
 *   'green'  → color: var(--success-500)
 *   'orange' → color: var(--warning-strong)
 *   'blue'   → color: var(--info)
 *   默认      → color: var(--foreground)
 */
import { computed } from 'vue'

const props = defineProps({
  label:  { type: String,  required: true },
  value:  { type: [String, Number], required: true },
  trend:  { type: String,  default: '' },   // 底部小字（admin-theme 里叫 .trend）
  accent: { type: String,  default: '' }    // red / green / orange / blue / primary / ''
})

const valueClass = computed(() => {
  const valid = ['red', 'green', 'orange', 'blue', 'primary']
  return valid.includes(props.accent) ? props.accent : ''
})
</script>

<template>
  <!-- 裸 div，不加任何自定义 scoped class，让 admin-theme.css 的 .stat-card / .label / .value / .trend 全部命中 -->
  <div class="stat-card">
    <div class="label">{{ label }}</div>
    <div class="value" :class="valueClass">{{ value }}</div>
    <div v-if="trend" class="trend">{{ trend }}</div>
  </div>
</template>
