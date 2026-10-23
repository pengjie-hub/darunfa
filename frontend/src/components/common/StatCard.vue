<script setup>
/**
 * StatCard.vue —— 仪表盘统计卡（4 张大卡：会员总数 / 商品总数 / 订单总数 / 累计销售额）。
 * 移植自 admin.html 里多处 `stat-card` / `stat-grid` 结构。
 *
 * props:
 *   label  : string  卡标题
 *   value  : string | number  卡值（已经 fmt 过的字符串 / 或原始数字）
 *   hint   : string  底部小字（可选）
 *   accent : string  强调色方向 'red' / 'green' / 'blue' / 默认
 */
import { computed } from 'vue'

const props = defineProps({
  label:  { type: String,  required: true },
  value:  { type: [String, Number], required: true },
  hint:   { type: String,  default: '' },
  accent: { type: String,  default: '' }
})

const accentClass = computed(() => props.accent ? `stat-${props.accent}` : '')
</script>

<template>
  <div class="stat-card" :class="accentClass">
    <div class="stat-label">{{ label }}</div>
    <div class="stat-value">{{ value }}</div>
    <div v-if="hint" class="stat-hint">{{ hint }}</div>
  </div>
</template>

<style scoped>
.stat-card {
  background: #fff;
  border-radius: 4px;
  padding: 18px 20px;
  border-left: 3px solid #eee;
  box-shadow: 0 1px 2px rgba(0,0,0,.03);
}
.stat-label { font-size: 13px; color: #888; margin-bottom: 6px; }
.stat-value { font-size: 26px; font-weight: 700; color: #222; line-height: 1.1; }
.stat-hint  { font-size: 12px; color: #aaa; margin-top: 6px; }
.stat-red   { border-left-color: var(--brand-500, #DE1313); }
.stat-green { border-left-color: #2e7d32; }
.stat-blue  { border-left-color: #1565C0; }
</style>
