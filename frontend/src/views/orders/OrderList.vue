<script setup>
/**
 * OrderList.vue —— 订单管理（迁移自 admin.html renderOrders() 行 890-1015）。
 *
 * 样式约束：全部走 admin-theme.css 全局类（.panel / .toolbar / .table-wrap / table / th / td /
 * .ops / .badge / .form-row），本文件 scoped 样式只写 admin-theme 没有的一点点补充。
 *
 * 弹窗用 el-dialog（负责遮罩 / 层级 / ESC），但**弹窗内部表单用原生 input/select + .form-row**，
 * 这样 admin-theme.css 的表单样式才能命中，视觉与旧页一致。
 */
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listOrders, shipOrder, scanOrder, batchScanOrders } from '@/api/orders'
import { ORDER_STATUS, SHIP_COMPANIES, genTrackingNo } from '@/utils/constants'
import { fmtMoney, fmtDateTime } from '@/utils/format'
import { usePermission } from '@/composables/usePermission'
import StatusTag from '@/components/common/StatusTag.vue'

const { hasPerm } = usePermission()
const canScan = computed(() => hasPerm('risk:scan'))

const loading = ref(false)
const orders = ref([])
const statusFilter = ref('')
const riskFilter = ref('')
const scanning = ref(false)

const RISK_FILTERS = [
  { value: '',         label: '全部风险' },
  { value: 'HIGH',     label: '🔴 高风险' },
  { value: 'MEDIUM',   label: '🟠 中风险' },
  { value: 'LOW',      label: '🟢 低风险' },
  { value: 'UNSCANNED', label: '⚪ 未扫描' }
]

const visibleOrders = computed(() => {
  const r = riskFilter.value
  if (!r) return orders.value
  if (r === 'UNSCANNED') return orders.value.filter(o => !o.riskLevel)
  return orders.value.filter(o => o.riskLevel === r)
})

async function load() {
  loading.value = true
  try {
    orders.value = await listOrders(statusFilter.value)
  } catch (e) {
    ElMessage.error(e.message || '加载订单失败')
  } finally {
    loading.value = false
  }
}

// ────────────── 发货 ──────────────
const shipDialog = ref(false)
const shipTarget = ref(null)
const shipForm = ref({ company: '', trackingNo: '', remark: '' })

function openShip(o) {
  shipTarget.value = o
  shipForm.value = { company: SHIP_COMPANIES[0]?.name || '', trackingNo: '', remark: '' }
  regenTracking()
  shipDialog.value = true
}

function regenTracking() {
  shipForm.value.trackingNo = genTrackingNo(shipForm.value.company)
}

async function submitShip() {
  const { company, trackingNo, remark } = shipForm.value
  if (!trackingNo.trim()) return ElMessage.warning('请生成运单号')
  try {
    await shipOrder(shipTarget.value.id, { company, trackingNo: trackingNo.trim(), remark: remark.trim() })
    ElMessage.success('已发货')
    shipDialog.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message || '发货失败')
  }
}

// ────────────── 详情 ──────────────
const detailDialog = ref(false)
const detail = ref(null)

function openDetail(o) {
  detail.value = o
  detailDialog.value = true
}

// ────────────── 风险扫描 ──────────────
async function doScan(o) {
  if (!canScan.value) return ElMessage.warning('无 risk:scan 权限')
  try {
    const dto = await scanOrder(o.id)
    ElMessage.success(`扫描完成：${dto.level}（${dto.score} 分）`)
    await load()
  } catch (e) {
    ElMessage.error(e.message || '扫描失败')
  }
}

async function doBatchScan() {
  if (!canScan.value) return ElMessage.warning('无 risk:scan 权限')
  scanning.value = true
  try {
    const data = await batchScanOrders(30)
    ElMessage.success(`已扫描 ${data.scanned} 条订单`)
    await load()
  } catch (e) {
    ElMessage.error(e.message || '批量扫描失败')
  } finally {
    scanning.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <div class="panel">
      <div class="panel-head">
        <h3>订单管理</h3>
      </div>
      <div class="panel-body">

        <!-- 工具栏：状态下拉 / 风险下拉 / 批量扫描 -->
        <div class="toolbar">
          <select v-model="statusFilter" @change="load">
            <option value="">全部状态</option>
            <option v-for="(cfg, code) in ORDER_STATUS" :key="code" :value="code">{{ cfg.label }}</option>
          </select>

          <select v-model="riskFilter">
            <option v-for="f in RISK_FILTERS" :key="f.value" :value="f.value">{{ f.label }}</option>
          </select>

          <button class="btn ghost sm" :disabled="scanning" @click="doBatchScan">
            {{ scanning ? '⏳ 扫描中...' : '🔍 批量风险扫描' }}
          </button>
        </div>

        <!-- 订单表格（原生 table，admin-theme.css 的 th/td 样式生效） -->
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>订单号</th>
                <th>金额</th>
                <th>状态</th>
                <th>风险</th>
                <th>收货人</th>
                <th>下单时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="o in visibleOrders" :key="o.id">
                <td>{{ o.orderNo }}</td>

                <td>
                  {{ fmtMoney(o.payAmount) }}
                  <span v-if="o.discountAmount > 0" class="cell-meta discount-meta">
                    优惠 -{{ fmtMoney(o.discountAmount) }}
                  </span>
                </td>

                <td>
                  <StatusTag
                    :text="ORDER_STATUS[o.status]?.label || o.status || '未知'"
                    :color="ORDER_STATUS[o.status]?.color || 'gray'"
                  />
                </td>

                <td>
                  <StatusTag
                    :text="o.riskLevel ? (o.riskLevel === 'HIGH' ? '高风险' : o.riskLevel === 'MEDIUM' ? '中风险' : '低风险') : '未扫描'"
                    :color="o.riskLevel || 'NONE'"
                    risk
                  />
                  <span v-if="o.riskScore != null" class="cell-meta">{{ o.riskScore }} 分</span>
                </td>

                <td>
                  {{ o.receiverName || '-' }}
                  <span class="cell-meta">{{ o.receiverPhone || '' }}</span>
                </td>

                <td>{{ fmtDateTime(o.createdAt) }}</td>

                <td>
                  <div class="ops">
                    <button class="btn ghost sm" @click="openDetail(o)">详情</button>
                    <button v-if="o.status === 'PAID'" class="btn primary sm" @click="openShip(o)">发货</button>
                    <button class="btn ghost sm" title="对该订单重新跑风险扫描" @click="doScan(o)">🔍 扫描</button>
                  </div>
                </td>
              </tr>

              <tr v-if="!visibleOrders.length">
                <td colspan="7" class="empty">暂无订单</td>
              </tr>
            </tbody>
          </table>
        </div>

      </div>
    </div>

    <!-- 发货弹窗 -->
    <el-dialog v-model="shipDialog" title="订单发货" width="560px" :close-on-click-modal="false">
      <div class="form-row">
        <label>物流公司 *</label>
        <select v-model="shipForm.company" @change="regenTracking">
          <option v-for="c in SHIP_COMPANIES" :key="c.name" :value="c.name">{{ c.name }}</option>
        </select>
      </div>

      <div class="form-row">
        <label>运单号 *</label>
        <div class="tracking-row">
          <input v-model="shipForm.trackingNo" readonly class="tracking-input" />
          <button type="button" class="btn ghost sm" @click="regenTracking">换一个</button>
        </div>
        <div class="hint">运单号已按所选物流公司随机生成，可点击「换一个」重新生成</div>
      </div>

      <div class="form-row">
        <label>备注</label>
        <input v-model="shipForm.remark" placeholder="可选" />
      </div>

      <template #footer>
        <button type="button" class="btn ghost" @click="shipDialog = false">取消</button>
        <button type="button" class="btn primary" @click="submitShip">确定发货</button>
      </template>
    </el-dialog>

    <!-- 订单详情弹窗 -->
    <el-dialog v-model="detailDialog" :title="`订单详情 ${detail?.orderNo || ''}`" width="720px">
      <template v-if="detail">
        <div class="detail-lines">
          <div>
            状态：<StatusTag
              :text="ORDER_STATUS[detail.status]?.label || detail.status || '未知'"
              :color="ORDER_STATUS[detail.status]?.color || 'gray'"
            />
          </div>
          <div>
            商品总额：{{ fmtMoney(detail.totalAmount) }}
            　优惠：-{{ fmtMoney(detail.discountAmount) }}
            　<b class="detail-pay">实付：{{ fmtMoney(detail.payAmount) }}</b>
          </div>
          <div>收货人：{{ detail.receiverName || '-' }}　{{ detail.receiverPhone || '' }}</div>
          <div>收货地址：{{ detail.receiverAddress || '-' }}</div>
          <div v-if="detail.remark">备注：{{ detail.remark }}</div>
          <div>
            下单：{{ fmtDateTime(detail.createdAt) }}
            　支付：{{ fmtDateTime(detail.paidAt) }}
            　发货：{{ fmtDateTime(detail.shippedAt) }}
            　完成：{{ fmtDateTime(detail.completedAt) }}
          </div>
        </div>

        <div class="table-wrap">
          <table>
            <thead>
              <tr><th>商品</th><th>单价</th><th>数量</th><th>小计</th></tr>
            </thead>
            <tbody>
              <tr v-for="(i, idx) in (detail.items || [])" :key="idx">
                <td>{{ i.productName }}</td>
                <td>{{ fmtMoney(i.price) }}</td>
                <td>{{ i.quantity }}</td>
                <td>{{ fmtMoney(i.subtotal) }}</td>
              </tr>
              <tr v-if="!detail.items || !detail.items.length">
                <td colspan="4" class="empty">无商品明细</td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
/* 仅补齐 admin-theme.css 没有的少量排版；绝不重写 .panel / .toolbar / table / .btn 等全局类 */

/* 优惠金额小字（admin-theme 只有中性灰的 .cell-meta，这里是红色强调） */
.discount-meta { color: var(--destructive); }

/* 运单号输入 + 「换一个」按钮并排 */
.tracking-row { display: flex; gap: 10px; align-items: center; }
.tracking-input { flex: 1; background: var(--bg-100); color: var(--muted-foreground); }

/* 详情弹窗的信息行（admin-theme 无对应类） */
.detail-lines { font-size: 13px; line-height: 2.2; color: var(--text-600); margin-bottom: 16px; }
.detail-pay { color: var(--foreground); }
</style>
