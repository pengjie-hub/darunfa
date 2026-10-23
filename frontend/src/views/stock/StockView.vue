<script setup>
/**
 * StockView.vue —— 库存管理（迁移自 admin.html renderStock() 行 1107-1123）。
 *
 * 结构：
 *   2 张统计卡（低库存商品数 / 商品总数）
 *   + 低库存预警面板（5 列原生 table：ID / 商品 / 当前库存 / 销量 / 操作-补货）
 *   + 补货弹窗（复用 PUT /admin/products/{id}/stock { delta }，与商品管理同一套接口）
 *
 * 样式全部走 admin-theme.css 全局类，scoped 只留统计卡栅格宽度这一处。
 */
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listLowStock, listProducts, adjustStock } from '@/api/products'
import { LOW_STOCK_THRESHOLD } from '@/utils/constants'
import StatCard from '@/components/common/StatCard.vue'

const loading = ref(false)
const low = ref([])
const products = ref([])

async function load() {
  loading.value = true
  try {
    const [lowList, all] = await Promise.all([listLowStock(), listProducts()])
    low.value = lowList
    products.value = all
  } catch (e) {
    ElMessage.error(e.message || '加载库存数据失败')
  } finally {
    loading.value = false
  }
}

// ────────────── 补货 ──────────────
const stockVisible = ref(false)
const stockTarget = ref(null)
const stockDelta = ref(null)

function openStock(p) {
  stockTarget.value = p
  stockDelta.value = null
  stockVisible.value = true
}

async function submitStock() {
  const delta = Number(stockDelta.value)
  if (!delta) return ElMessage.warning('请输入调整数量')
  try {
    await adjustStock(stockTarget.value.id, delta)
    ElMessage.success('库存已调整')
    stockVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message || '调整失败')
  }
}

const lowCountAccent = computed(() => (low.value.length > 0 ? 'red' : 'green'))

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <div class="stat-grid stock-grid">
      <StatCard label="低库存商品数" :value="low.length" :accent="lowCountAccent" />
      <StatCard label="商品总数" :value="products.length" />
    </div>

    <div class="panel">
      <div class="panel-head">
        <h3>低库存预警（库存 ≤ {{ LOW_STOCK_THRESHOLD }}）</h3>
      </div>

      <div class="panel-body">
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>商品</th>
                <th>当前库存</th>
                <th>销量</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="p in low" :key="p.id">
                <td>{{ p.id }}</td>
                <td><b>{{ p.name }}</b></td>
                <td><span class="badge red">{{ p.stock }}</span></td>
                <td>{{ p.sales }}</td>
                <td>
                  <div class="ops">
                    <button class="btn primary sm" @click="openStock(p)">补货</button>
                  </div>
                </td>
              </tr>

              <tr v-if="!low.length">
                <td colspan="5" class="empty">
                  无低库存商品 <span class="badge green">正常</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 补货 / 调整库存 -->
    <el-dialog v-model="stockVisible" :title="`调整库存 — ${stockTarget?.name || ''}`" width="380px" :close-on-click-modal="false">
      <div class="form-row">
        <label>当前库存</label>
        <div class="stock-now">{{ stockTarget?.stock ?? 0 }}</div>
      </div>
      <div class="form-row">
        <label>调整数量（正=入库，负=出库）</label>
        <input v-model="stockDelta" type="number" placeholder="如 50 或 -20" />
      </div>

      <template #footer>
        <button class="btn ghost" @click="stockVisible = false">取消</button>
        <button class="btn primary" @click="submitStock">确认调整</button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
/* 旧页统计卡栅格用 220px 最小宽度（admin-theme 的 .stat-grid 默认 200px），这里 1:1 对齐 */
.stock-grid { grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); }

/* 弹窗里的当前库存大字（admin-theme 无对应类） */
.stock-now { font-size: 28px; font-weight: 600; color: var(--primary); }
</style>
