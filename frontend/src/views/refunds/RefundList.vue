<script setup>
/**
 * RefundList.vue —— 退款管理（迁移自 admin.html renderRefunds() 行 1040-1104）。
 *
 * 「同意退款」会同时回补库存、返还优惠券、把支付流水置为已退款，不可撤销，必须二次确认；
 * 「拒绝」需要填写拒绝理由，订单恢复到申请前状态（previousStatus）。
 * 每次操作后刷新列表 + 侧栏待处理角标。
 */
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listRefunds, approveRefund, rejectRefund } from '@/api/refunds'
import { useAppStore } from '@/stores/app'
import { fmtMoney, fmtDateTime } from '@/utils/format'
import StatusTag from '@/components/common/StatusTag.vue'

const REFUND_STATUS = {
  PENDING:  { label: '待处理', color: 'orange' },
  APPROVED: { label: '已同意', color: 'green' },
  REJECTED: { label: '已拒绝', color: 'gray' }
}

const app = useAppStore()

const loading = ref(false)
const refunds = ref([])
const statusFilter = ref('')
const actingId = ref(null)

async function load() {
  loading.value = true
  try {
    refunds.value = await listRefunds(statusFilter.value)
  } catch (e) {
    ElMessage.error(e.message || '加载退款申请失败')
  } finally {
    loading.value = false
  }
}

async function doApprove(r) {
  try {
    await ElMessageBox.confirm(
      `确认同意退款 ${fmtMoney(r.amount)}？\n\n同意后将回补库存、返还优惠券，并把支付流水置为「已退款」，操作不可撤销。`,
      '同意退款',
      { confirmButtonText: '确认退款', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (_) {
    return
  }

  actingId.value = r.id
  try {
    await approveRefund(r.id)
    ElMessage.success('已同意退款，款项已退回')
    await load()
    app.refreshBadges()
  } catch (e) {
    ElMessage.error(e.message || '退款失败')
  } finally {
    actingId.value = null
  }
}

// ────────────── 拒绝 ──────────────
const rejectDialog = ref(false)
const rejectTarget = ref(null)
const rejectReason = ref('')

function openReject(r) {
  rejectTarget.value = r
  rejectReason.value = ''
  rejectDialog.value = true
}

async function submitReject() {
  const reason = rejectReason.value.trim()
  if (!reason) return ElMessage.warning('请填写拒绝理由')
  actingId.value = rejectTarget.value.id
  try {
    await rejectRefund(rejectTarget.value.id, reason)
    ElMessage.success('已拒绝该退款申请')
    rejectDialog.value = false
    await load()
    app.refreshBadges()
  } catch (e) {
    ElMessage.error(e.message || '拒绝失败')
  } finally {
    actingId.value = null
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <div class="panel">
      <div class="panel-head">
        <h3>退款管理</h3>
      </div>

      <div class="panel-body">
        <div class="toolbar">
          <select v-model="statusFilter" @change="load">
            <option value="">全部状态</option>
            <option v-for="(cfg, code) in REFUND_STATUS" :key="code" :value="code">{{ cfg.label }}</option>
          </select>
          <span class="toolbar-note">同意退款会同时回补库存、返还优惠券，并把支付流水置为「已退款」</span>
        </div>

        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>订单号</th>
                <th>申请人</th>
                <th>退款金额</th>
                <th>商品</th>
                <th>退款原因</th>
                <th>申请时间</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in refunds" :key="r.id">
                <td>{{ r.orderNo || '-' }}</td>
                <td>
                  {{ r.userName || '-' }}
                  <span class="cell-meta">{{ r.userPhone || '' }}</span>
                </td>
                <td><b>{{ fmtMoney(r.amount) }}</b></td>
                <td>{{ r.products || '-' }}</td>
                <td>
                  {{ r.reason || '-' }}
                  <span v-if="r.rejectReason" class="cell-meta reject-reason">拒绝理由：{{ r.rejectReason }}</span>
                </td>
                <td>{{ fmtDateTime(r.createdAt) }}</td>
                <td>
                  <StatusTag
                    :text="REFUND_STATUS[r.status]?.label || r.status || '未知'"
                    :color="REFUND_STATUS[r.status]?.color || 'gray'"
                  />
                  <span v-if="r.status === 'PENDING' && r.previousStatusLabel" class="cell-meta">
                    申请前：{{ r.previousStatusLabel }}
                  </span>
                </td>
                <td>
                  <div class="ops">
                    <template v-if="r.status === 'PENDING'">
                      <button class="btn primary sm" :disabled="actingId === r.id" @click="doApprove(r)">
                        同意退款
                      </button>
                      <button class="btn danger sm" :disabled="actingId === r.id" @click="openReject(r)">
                        拒绝
                      </button>
                    </template>
                    <span v-else class="cell-meta">已处理</span>
                  </div>
                </td>
              </tr>

              <tr v-if="!refunds.length">
                <td colspan="8" class="empty">暂无退款申请</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 拒绝退款 -->
    <el-dialog v-model="rejectDialog" title="拒绝退款申请" width="520px" :close-on-click-modal="false">
      <div class="form-row">
        <label>拒绝理由 *</label>
        <input v-model="rejectReason" placeholder="如：商品已发出，请收到后申请退货" />
        <div class="hint">拒绝后订单会恢复到申请前的状态，用户可继续正常收货 / 评价。</div>
      </div>

      <template #footer>
        <button type="button" class="btn ghost" @click="rejectDialog = false">取消</button>
        <button type="button" class="btn primary" @click="submitReject">确认拒绝</button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
/* 工具栏里的说明文字（admin-theme 无对应类） */
.toolbar-note { align-self: center; font-size: 12px; color: var(--muted-foreground); }
/* 拒绝理由红色小字 */
.reject-reason { color: var(--destructive); }
</style>
