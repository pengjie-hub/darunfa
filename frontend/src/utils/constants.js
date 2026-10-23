/**
 * 全局常量 —— 来自 admin.html 顶部模块级常量，迁移时保证数值一致。
 * 改值前先 grep admin.html 确认两处同步。
 */

// 低库存阈值 —— 与 admin.html LOW_STOCK_THRESHOLD 保持一致
export const LOW_STOCK_THRESHOLD = 10

// 发货 SLA（天）—— 与后端 AdminService.SLA_SHIP_DAYS 保持一致
export const SLA_SHIP_DAYS = 2

// 风险等级（仪表盘/风险监控模块用）
export const RISK_LEVEL = {
  HIGH:   { label: '高风险', className: 'risk-badge HIGH' },
  MEDIUM: { label: '中风险', className: 'risk-badge MEDIUM' },
  LOW:    { label: '低风险', className: 'risk-badge LOW' }
}

// 订单状态枚举（与后端 OrderStatus + admin.html ORDER_STATUS 对齐）
export const ORDER_STATUS = {
  PENDING_PAYMENT: { label: '待付款',     color: 'orange' },
  PAID:            { label: '待发货',     color: 'blue' },
  SHIPPED:         { label: '待收货',     color: 'blue' },
  COMPLETED:       { label: '已完成',     color: 'green' },
  CANCELLED:       { label: '已取消',     color: 'gray' },
  REFUNDING:       { label: '退款中',     color: 'orange' },
  REFUNDED:        { label: '已退款',     color: 'gray' }
}

// 支付状态
export const PAY_STATUS = {
  SUCCESS:   { label: '成功', color: 'green' },
  REFUNDED:  { label: '已退款', color: 'orange' },
  FAILED:    { label: '失败', color: 'red' }
}

// 支付方式（PAYMENT_METHOD）
export const PAY_METHOD = {
  WECHAT:  '微信支付',
  ALIPAY:  '支付宝',
  BANK:    '银行卡',
  CASH:    '现金',
  BALANCE: '余额支付'
}

// 物流状态
export const SHIP_STATUS = {
  SHIPPED:    { label: '已发货',  color: 'blue' },
  IN_TRANSIT: { label: '运输中',  color: 'orange' },
  DELIVERED:  { label: '已送达',  color: 'green' }
}

// 促销类型
export const PROMO_TYPE = {
  FULL_REDUCTION: { label: '满减' },
  DISCOUNT:       { label: '折扣' }
}

// 风险订单扫描规则（风险监控模块 tooltip 用）
export const RISK_RULES = [
  { code: 'AMOUNT_OUTLIER',   label: '金额异常' },
  { code: 'MULTI_ORDER',      label: '短时多单' },
  { code: 'SENSITIVE_ADDRESS',label: '敏感地址' },
  { code: 'FIRST_BIG_ORDER',  label: '首单大额' },
  { code: 'LATE_NIGHT',       label: '凌晨下单' }
]

// 快递公司 + 物流单号前缀（admin.html genTrackingNo 依赖）
export const SHIP_COMPANIES = [
  { name: '顺丰速运', prefix: 'SF' },
  { name: '中通快递', prefix: 'ZT' },
  { name: '圆通速递', prefix: 'YT' },
  { name: '韵达快递', prefix: 'YD' },
  { name: '申通快递', prefix: 'ST' },
  { name: '京东物流', prefix: 'JD' },
  { name: '邮政EMS',  prefix: 'EMS' },
  { name: '极兔速递', prefix: 'JT' },
  { name: '德邦物流', prefix: 'DB' }
]

// D3 图表色板（CSS 变量引用，运行时通过 cssVar 解析）
export const CHART_COLOR_VARS = [
  'var(--primary)',
  'var(--chart-2)',
  'var(--success-500)',
  'var(--warning)',
  'var(--info)',
  'var(--chart-4)',
  'var(--chart-5)',
  'var(--text-400)'
]

/** 根据 SHIP_COMPANIES 生成物流单号（与 admin.html genTrackingNo 行为一致） */
export function genTrackingNo(company) {
  const found = SHIP_COMPANIES.find(c => c.name === company)
  const prefix = found ? found.prefix : 'SF'
  const len = prefix === 'EMS' ? 13 : 12
  let num = ''
  for (let i = 0; i < len; i++) num += Math.floor(Math.random() * 10)
  return prefix + num
}
