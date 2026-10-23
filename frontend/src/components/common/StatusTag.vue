<script setup>
/**
 * StatusTag.vue —— 状态 / 风险 角标。
 *
 * 样式全部由 admin-theme.css 全局接管，本组件**不写任何 scoped 样式**：
 *   普通状态 → .badge.{green|red|orange|blue|gray|primary}   （admin-theme.css 446-459 行）
 *   风险等级 → .risk-badge.{HIGH|MEDIUM|LOW|NONE}            （admin-theme.css 669-673 行）
 *
 * 与 admin.html 的 `statusBadge()` / `riskBadgeOf()` 输出完全一致，避免自绘色值漂移。
 */
import { computed } from 'vue'

const props = defineProps({
  text:  { type: String, required: true },
  color: { type: String, default: 'gray' },  // badge 颜色，或风险等级 HIGH/MEDIUM/LOW/NONE
  risk:  { type: Boolean, default: false }
})

const classes = computed(() =>
  props.risk
    ? ['risk-badge', String(props.color).toUpperCase()]
    : ['badge', props.color]
)
</script>

<template>
  <span :class="classes">{{ text }}</span>
</template>
