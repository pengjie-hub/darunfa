<script setup>
/**
 * PaymentList.vue —— 支付管理（迁移自 admin.html renderPayments() 行 2174-2187）。
 *
 * 单表 + 单个动作：SUCCESS 的流水可退款，退款后整表刷新。
 * 样式全部走 admin-theme.css 全局类（.panel / .table-wrap / table / .badge / .ops）。
 */
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listPayments, refundPayment } from '@/api/payments'
import { PAY_STATUS, PAY_METHOD } from '@/utils/constants'
import { fmtMoney, fmtDateTime } from '@/utils/format'
import StatusTag from '@/components/common/StatusTag.vue'

const loading = ref(false)
const pays = ref([])
const refundingId = ref(null)

async function load() {
  loading.value = true
  try {
    pays.value = await listPayments()
  } catch (e) {
    ElMessage.error(e.message || '加载支付流水失败')
  } finally {
    loading.value = false
  }
}

async function doRefund(p) {
  try {
    await ElMessageBox.confirm('确认退款该笔支付？', '提示', {
      confirmButtonText: '确认退款', cancelButtonText: '取消', type: 'warning'
    })
  } catch (_) {
    return
  }
  refundingId.value = p.id
  try {
    await refundPayment(p.id)
    ElMessage.success('已退款')
    await load()
  } catch (e) {
    ElMessage.error(e.message || '退款失败')
  } finally {
    refundingId.value = null
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <div class="panel">
      <div class="panel-head">
        <h3>支付流水</h3>
      </div>

      <div class="panel-body">
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>订单号</th>
                <th>用户ID</th>
                <th>金额</th>
                <th>方式</th>
                <th>状态</th>
                <th>支付时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="p in pays" :key="p.id">
                <td>{{ p.id }}</td>
                <td>{{ p.orderNo || '-' }}</td>
                <td>{{ p.userId }}</td>
                <td><b>{{ fmtMoney(p.amount) }}</b></td>
                <td>{{ PAY_METHOD[p.method] || p.method }}</td>
                <td>
                  <StatusTag
                    :text="PAY_STATUS[p.status]?.label || p.status || '未知'"
                    :color="PAY_STATUS[p.status]?.color || 'gray'"
                  />
                </td>
                <td>{{ fmtDateTime(p.paidAt) }}</td>
                <td>
                  <div class="ops">
                    <button
                      v-if="p.status === 'SUCCESS'"
                      class="btn danger sm"
                      :disabled="refundingId === p.id"
                      @click="doRefund(p)"
                    >
                      {{ refundingId === p.id ? '退款中…' : '退款' }}
                    </button>
                  </div>
                </td>
              </tr>

              <tr v-if="!pays.length">
                <td colspan="8" class="empty">暂无支付记录</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>
