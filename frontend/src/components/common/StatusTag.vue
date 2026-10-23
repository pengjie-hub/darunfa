<script setup>
/**
 * StatusTag.vue —— 状态/风险/支付/物流的彩色角标。
 *
 * 替代 admin.html 里 `statusBadge(status, ORDER_STATUS)` 这类模板字符串拼接。
 *
 * props:
 *   text  : string  显示文本
 *   color : 'green' | 'red' | 'orange' | 'blue' | 'gray' | string（hex/css var）
 *   size  : 'sm' | 'md'  默认 'sm'
 *   risk  : boolean  如果是风险角标，应用 .risk-badge / HIGH|MEDIUM|LOW 颜色
 */
import { computed } from 'vue'

const props = defineProps({
  text:  { type: String, required: true },
  color: { type: String, default: 'gray' },
  size:  { type: String, default: 'sm' },
  risk:  { type: Boolean, default: false }
})

const classes = computed(() => {
  const c = ['status-tag', `tag-${props.color}`, `size-${props.size}`]
  if (props.risk) c.push('risk-badge', props.color.toUpperCase())
  return c
})
</script>

<template>
  <span :class="classes">{{ text }}</span>
</template>

<style scoped>
.status-tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 3px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.6;
}
.status-tag.size-sm { padding: 1px 8px; font-size: 11px; line-height: 1.5; }
.tag-green  { background: #e8f5e9; color: #2e7d32; }
.tag-red    { background: #ffebee; color: #c62828; }
.tag-orange { background: #fff3e0; color: #ef6c00; }
.tag-blue   { background: #e3f2fd; color: #1565C0; }
.tag-gray   { background: #f0f0f0; color: #666; }

/* 风险角标样式（对齐 admin.css .risk-badge） */
.risk-badge.HIGH   { background: #ffebee; color: #c62828; border: 1px solid #ef9a9a; }
.risk-badge.MEDIUM { background: #fff3e0; color: #ef6c00; border: 1px solid #ffcc80; }
.risk-badge.LOW    { background: #fffde7; color: #f9a825; border: 1px solid #fff59d; }
</style>
