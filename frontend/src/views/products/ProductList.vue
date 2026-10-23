<script setup>
/**
 * ProductList.vue —— 商品管理（迁移自 admin.html renderProducts() 行 744-875）。
 *
 * 样式约束（与 OrderList.vue 一致）：
 *   .panel / .panel-head / .panel-body / .toolbar / .table-wrap / table / th / td /
 *   .cell-meta / .ops / .badge / .form-row 全部来自 admin-theme.css 全局，
 *   scoped 里只留 admin-theme 没有的少量扩展类。
 *
 * 与旧页对齐的取舍：
 *   - 旧页无分页（一次渲染全部商品，靠输入框前端过滤）→ 去掉 el-pagination
 *   - 旧页商品弹窗里的「✨ AI 帮我写」属于 P5 AI 模块 → 本轮不引入，留到 P5
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listProducts, createProduct, updateProduct, deleteProduct, adjustStock, listCategories
} from '@/api/products'
import { fmtMoney } from '@/utils/format'
import StatusTag from '@/components/common/StatusTag.vue'

const loading = ref(false)
const products = ref([])
const categories = ref([])
const search = ref('')

const catMap = computed(() => {
  const m = {}
  categories.value.forEach(c => { m[c.id] = c.name })
  return m
})

const filtered = computed(() => {
  const kw = search.value.trim().toLowerCase()
  if (!kw) return products.value
  return products.value.filter(p =>
    (p.name || '').toLowerCase().includes(kw) || (p.subtitle || '').toLowerCase().includes(kw)
  )
})

async function load() {
  loading.value = true
  try {
    const [cats, list] = await Promise.all([listCategories(), listProducts()])
    categories.value = cats
    products.value = list
  } catch (e) {
    ElMessage.error(e.message || '加载商品失败')
  } finally {
    loading.value = false
  }
}

// ────────────── 新增 / 编辑 ──────────────
const editVisible = ref(false)
const editId = ref(null)
const saving = ref(false)

const editForm = reactive({
  name: '', subtitle: '', categoryId: '', unit: '件',
  price: '', originalPrice: '', stock: 0, status: 1, imageUrl: '', description: ''
})

function resetForm(p) {
  editForm.name = p?.name || ''
  editForm.subtitle = p?.subtitle || ''
  editForm.categoryId = p?.categoryId || (categories.value[0]?.id ?? '')
  editForm.unit = p?.unit || '件'
  editForm.price = p?.price ?? ''
  editForm.originalPrice = p?.originalPrice ?? ''
  editForm.stock = p?.stock ?? 0
  editForm.status = p?.status ?? 1
  editForm.imageUrl = p?.imageUrl || ''
  editForm.description = p?.description || ''
}

function openCreate() {
  editId.value = null
  resetForm(null)
  editVisible.value = true
}

function openEdit(p) {
  editId.value = p.id
  resetForm(p)
  editVisible.value = true
}

async function submitEdit() {
  if (!editForm.name.trim()) return ElMessage.warning('请填写商品名称')
  if (!editForm.categoryId) return ElMessage.warning('请选择分类')
  if (!(Number(editForm.price) >= 0)) return ElMessage.warning('价格无效')

  const body = {
    categoryId: Number(editForm.categoryId),
    name: editForm.name.trim(),
    subtitle: editForm.subtitle.trim(),
    unit: editForm.unit.trim() || '件',
    price: Number(editForm.price),
    originalPrice: editForm.originalPrice === '' ? null : Number(editForm.originalPrice),
    stock: Number(editForm.stock),
    status: Number(editForm.status),
    imageUrl: editForm.imageUrl.trim(),
    description: editForm.description.trim()
  }

  saving.value = true
  try {
    if (editId.value) await updateProduct(editId.value, body)
    else await createProduct(body)
    ElMessage.success(editId.value ? '已更新' : '已新增')
    editVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function onDelete(p) {
  try {
    await ElMessageBox.confirm(`确定删除商品「${p.name}」？`, '提示', {
      confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
    })
    await deleteProduct(p.id)
    ElMessage.success('已删除')
    load()
  } catch (_) { /* 取消或失败 */ }
}

// ────────────── 调整库存 ──────────────
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

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <div class="panel">
      <div class="panel-head">
        <h3>商品管理</h3>
        <button class="btn primary sm" @click="openCreate">+ 新增商品</button>
      </div>

      <div class="panel-body">
        <div class="toolbar">
          <input v-model="search" placeholder="搜索商品名称..." style="width:240px" />
        </div>

        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>商品</th>
                <th>分类</th>
                <th>价格</th>
                <th>库存</th>
                <th>销量</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="p in filtered" :key="p.id">
                <td>{{ p.id }}</td>

                <td>
                  <b>{{ p.name }}</b>
                  <span class="cell-meta">{{ p.subtitle || '' }}</span>
                </td>

                <td>{{ catMap[p.categoryId] || p.categoryId }}</td>

                <td>
                  {{ fmtMoney(p.price) }}
                  <span v-if="p.originalPrice" class="cell-meta"><s>{{ fmtMoney(p.originalPrice) }}</s></span>
                </td>

                <td>{{ p.stock }}</td>
                <td>{{ p.sales }}</td>

                <td>
                  <StatusTag :text="p.status === 1 ? '上架' : '下架'" :color="p.status === 1 ? 'green' : 'gray'" />
                </td>

                <td>
                  <div class="ops">
                    <button class="btn ghost sm" @click="openEdit(p)">编辑</button>
                    <button class="btn ghost sm" @click="openStock(p)">库存</button>
                    <button class="btn danger sm" @click="onDelete(p)">删除</button>
                  </div>
                </td>
              </tr>

              <tr v-if="!filtered.length">
                <td colspan="8" class="empty">暂无商品</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 新增 / 编辑商品 -->
    <el-dialog v-model="editVisible" :title="editId ? '编辑商品' : '新增商品'" width="560px" :close-on-click-modal="false">
      <div class="form-row">
        <label>商品名称 *</label>
        <input v-model="editForm.name" placeholder="如：红富士苹果" />
      </div>

      <div class="form-row">
        <label>副标题/卖点</label>
        <input v-model="editForm.subtitle" placeholder="如：脆甜多汁 产地直供" />
      </div>

      <div class="form-row two">
        <div>
          <label>分类 *</label>
          <select v-model="editForm.categoryId">
            <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
        </div>
        <div>
          <label>计量单位</label>
          <input v-model="editForm.unit" />
        </div>
      </div>

      <div class="form-row two">
        <div>
          <label>现价（元）*</label>
          <input v-model="editForm.price" type="number" step="0.01" />
        </div>
        <div>
          <label>原价（元）</label>
          <input v-model="editForm.originalPrice" type="number" step="0.01" />
        </div>
      </div>

      <div class="form-row two">
        <div>
          <label>库存 *</label>
          <input v-model="editForm.stock" type="number" />
        </div>
        <div>
          <label>状态</label>
          <select v-model="editForm.status">
            <option :value="1">上架</option>
            <option :value="0">下架</option>
          </select>
        </div>
      </div>

      <div class="form-row">
        <label>图片资源名</label>
        <input v-model="editForm.imageUrl" placeholder="如 p002（对应 App 内置图）" />
      </div>

      <div class="form-row">
        <label>商品描述</label>
        <textarea v-model="editForm.description" placeholder="商品详情介绍"></textarea>
      </div>

      <template #footer>
        <button type="button" class="btn ghost" @click="editVisible = false">取消</button>
        <button type="button" class="btn primary" :disabled="saving" @click="submitEdit">{{ saving ? '保存中...' : '保存' }}</button>
      </template>
    </el-dialog>

    <!-- 调整库存 -->
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
        <button type="button" class="btn ghost" @click="stockVisible = false">取消</button>
        <button type="button" class="btn primary" @click="submitStock">确认调整</button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
/* admin-theme.css 没有「库存弹窗里的当前库存大字」这一处 */
.stock-now { font-size: 28px; font-weight: 600; color: var(--primary); }
</style>
