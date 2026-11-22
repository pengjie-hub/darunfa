<script setup>
/**
 * ShipmentList.vue —— 物流管理（迁移自 admin.html renderShipments() 行 2190-2205）。
 *
 * 单表 + 状态流转：非「运输中」显示「运输中」按钮，非「已送达」显示「已送达」按钮。
 * 样式全部走 admin-theme.css 全局类。
 */
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listShipments, updateShipmentStatus } from '@/api/shipments'
import { SHIP_STATUS } from '@/utils/constants'
import { fmtDateTime } from '@/utils/format'
import StatusTag from '@/components/common/StatusTag.vue'

const loading = ref(false)
const ships = ref([])
const updatingId = ref(null)

async function load() {
  loading.value = true
  try {
    ships.value = await listShipments()
  } catch (e) {
    ElMessage.error(e.message || '加载物流记录失败')
  } finally {
    loading.value = false
  }
}

async function setStatus(s, status) {
  updatingId.value = s.id
  try {
    await updateShipmentStatus(s.id, status)
    ElMessage.success('已更新')
    await load()
  } catch (e) {
    ElMessage.error(e.message || '更新失败')
  } finally {
    updatingId.value = null
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <div class="panel">
      <div class="panel-head">
        <h3>物流发货</h3>
      </div>

      <div class="panel-body">
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>订单号</th>
                <th>物流公司</th>
                <th>运单号</th>
                <th>状态</th>
                <th>发货时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="s in ships" :key="s.id">
                <td>{{ s.id }}</td>
                <td>{{ s.orderNo || '-' }}</td>
                <td>{{ s.company || '-' }}</td>
                <td>{{ s.trackingNo || '-' }}</td>
                <td>
                  <StatusTag
                    :text="SHIP_STATUS[s.status]?.label || s.status || '未知'"
                    :color="SHIP_STATUS[s.status]?.color || 'gray'"
                  />
                </td>
                <td>{{ fmtDateTime(s.shippedAt) }}</td>
                <td>
                  <div class="ops">
                    <button
                      v-if="s.status !== 'IN_TRANSIT'"
                      class="btn ghost sm"
                      :disabled="updatingId === s.id"
                      @click="setStatus(s, 'IN_TRANSIT')"
                    >
                      运输中
                    </button>
                    <button
                      v-if="s.status !== 'DELIVERED'"
                      class="btn primary sm"
                      :disabled="updatingId === s.id"
                      @click="setStatus(s, 'DELIVERED')"
                    >
                      已送达
                    </button>
                  </div>
                </td>
              </tr>

              <tr v-if="!ships.length">
                <td colspan="7" class="empty">暂无物流记录</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>
