<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listProducts, createProduct, updateProduct, deleteProduct,
  adjustStock, listCategories
} from '@/api/products'

// —— 数据 ——
const loading = ref(false)
const products = ref([])
const categories = ref([])
const search = ref('')

// 编辑弹窗
const editVisible = ref(false)
const editing = ref(null)   // null = 新增；Product 对象 = 编辑
const editForm = ref({
  name: '', categoryId: null, price: null, originalPrice: null,
  stock: 0, subtitle: '', description: '', unit: '件',
  imageUrl: '', status: 1
})
const editLoading = ref(false)

// 库存调整弹窗
const stockVisible = ref(false)
const stockTarget = ref(null)
const stockDelta = ref(1)

// 分页（前端分页，后端暂无分页接口）
const page = ref(1)
const pageSize = ref(10)

// —— 方法 ——
async function load() {
  loading.value = true
  try {
    const [ps, cs] = await Promise.all([listProducts(), listCategories()])
    products.value = ps || []
    categories.value = cs || []
  } catch (e) {
    ElMessage.error(e.message || '加载商品失败')
  } finally {
    loading.value = false
  }
}

const filtered = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return products.value
  return products.value.filter(p =>
    p.name.toLowerCase().includes(q) ||
    String(p.id).includes(q)
  )
})
const total = computed(() => filtered.value.length)

function catNameOf(id) {
  return categories.value.find(c => c.id === id)?.name || '-'
}
function statusLabel(s) {
  return s === 1 ? '在售' : '下架'
}

// —— 新增 / 编辑 ——
function openCreate() {
  editing.value = null
  editForm.value = {
    name: '', categoryId: categories.value[0]?.id ?? null,
    price: null, originalPrice: null, stock: 0,
    subtitle: '', description: '', unit: '件',
    imageUrl: '', status: 1
  }
  editVisible.value = true
}
function openEdit(p) {
  editing.value = p
  editForm.value = {
    name: p.name, categoryId: p.categoryId,
    price: p.price, originalPrice: p.originalPrice, stock: p.stock,
    subtitle: p.subtitle || '', description: p.description || '',
    unit: p.unit || '件', imageUrl: p.imageUrl || '',
    status: p.status ?? 1
  }
  editVisible.value = true
}

async function submitEdit() {
  // 基础校验
  if (!editForm.value.name?.trim()) { ElMessage.warning('请填写商品名称'); return }
  if (!editForm.value.categoryId) { ElMessage.warning('请选择分类'); return }
  if (editForm.value.price === null || editForm.value.price === '' || Number(editForm.value.price) < 0) {
    ElMessage.warning('价格不能为负'); return
  }
  if (editForm.value.stock === null || editForm.value.stock === '' || Number(editForm.value.stock) < 0) {
    ElMessage.warning('库存不能为负'); return
  }

  editLoading.value = true
  try {
    // BigDecimal / Integer 后端 DTO，统一用 Number 发送
    const body = {
      ...editForm.value,
      price: Number(editForm.value.price),
      originalPrice: editForm.value.originalPrice ? Number(editForm.value.originalPrice) : null,
      stock: Number(editForm.value.stock),
      status: Number(editForm.value.status)
    }
    if (editing.value) {
      await updateProduct(editing.value.id, body)
      ElMessage.success('已更新')
    } else {
      await createProduct(body)
      ElMessage.success('已新增')
    }
    editVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    editLoading.value = false
  }
}

async function onDelete(id) {
  try {
    await ElMessageBox.confirm('删除后无法恢复，确定删除该商品？', '删除商品', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
  } catch { return }
  try {
    await deleteProduct(id)
    ElMessage.success('已删除')
    await load()
  } catch (e) { ElMessage.error(e.message || '删除失败') }
}

function openStock(p) {
  stockTarget.value = p
  stockDelta.value = 1
  stockVisible.value = true
}
async function submitStock() {
  try {
    await adjustStock(stockTarget.value.id, Number(stockDelta.value))
    ElMessage.success(`库存调整 ${stockDelta.value > 0 ? '+' : ''}${stockDelta.value}`)
    stockVisible.value = false
    await load()
  } catch (e) { ElMessage.error(e.message || '调整失败') }
}

onMounted(load)
</script>

<template>
  <div class="product-page">
    <div class="page-head">
      <h2>商品管理</h2>
      <div class="page-actions">
        <el-input v-model="search" placeholder="搜索商品名称或 ID" clearable style="width:240px;margin-right:12px" />
        <el-button type="primary" @click="openCreate">+ 新增商品</el-button>
      </div>
    </div>

    <el-table :data="filtered.slice((page-1)*pageSize, page*pageSize)" v-loading="loading" stripe border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="商品" min-width="200">
        <template #default="{ row }">
          <div class="p-name">{{ row.name }}</div>
          <div v-if="row.subtitle" class="p-sub">{{ row.subtitle }}</div>
        </template>
      </el-table-column>
      <el-table-column label="分类" width="140">
        <template #default="{ row }">{{ catNameOf(row.categoryId) }}</template>
      </el-table-column>
      <el-table-column label="价格" width="120">
        <template #default="{ row }">
          <span>¥{{ Number(row.price).toFixed(2) }}</span>
          <span v-if="row.originalPrice && Number(row.originalPrice) > Number(row.price)"
                class="p-original">¥{{ Number(row.originalPrice).toFixed(2) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="库存" width="100">
        <template #default="{ row }">
          <span :class="['stock-cell', { low: row.stock <= 5 }]">{{ row.stock }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="sales" label="销量" width="80" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link type="warning" size="small" @click="openStock(row)">调库存</el-button>
          <el-button link type="danger" size="small" @click="onDelete(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="page-foot">
      <el-pagination
        v-model:current-page="page" v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
      />
    </div>

    <!-- 新增 / 编辑 弹窗 -->
    <el-dialog v-model="editVisible" :title="editing ? '编辑商品' : '新增商品'" width="560px">
      <el-form label-width="100px">
        <el-form-item label="商品名称" required>
          <el-input v-model="editForm.name" placeholder="如：红富士苹果" />
        </el-form-item>
        <el-form-item label="副标题">
          <el-input v-model="editForm.subtitle" />
        </el-form-item>
        <el-form-item label="分类" required>
          <el-select v-model="editForm.categoryId" placeholder="请选择分类" style="width:100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="价格" required>
          <el-input-number v-model="editForm.price" :min="0" :precision="2" :step="0.1" style="width:100%" />
        </el-form-item>
        <el-form-item label="原价">
          <el-input-number v-model="editForm.originalPrice" :min="0" :precision="2" :step="0.1" style="width:100%" />
        </el-form-item>
        <el-form-item label="库存" required>
          <el-input-number v-model="editForm.stock" :min="0" :precision="0" style="width:100%" />
        </el-form-item>
        <el-form-item label="单位">
          <el-input v-model="editForm.unit" placeholder="件 / kg / 盒" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="editForm.status" :active-value="1" :inactive-value="0" active-text="在售" inactive-text="下架" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editLoading" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 库存调整 弹窗 -->
    <el-dialog v-model="stockVisible" :title="`调整库存 — ${stockTarget?.name ?? ''}`" width="380px">
      <p>当前库存：<b>{{ stockTarget?.stock }}</b></p>
      <el-form label-width="100px">
        <el-form-item label="调整量（正入库 / 负出库）">
          <el-input-number v-model="stockDelta" :step="1" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stockVisible = false">取消</el-button>
        <el-button type="primary" @click="submitStock">确认调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.product-page { background: #fff; border-radius: 4px; padding: 20px; }
.page-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-head h2 { margin: 0; font-size: 18px; color: #222; }
.page-actions { display: flex; align-items: center; }
.p-name { font-weight: 600; color: #222; }
.p-sub { font-size: 12px; color: #999; margin-top: 2px; }
.p-original { color: #bbb; text-decoration: line-through; margin-left: 8px; font-size: 12px; }
.stock-cell { font-weight: 600; }
.stock-cell.low { color: var(--brand-500, #DE1313); }
.page-foot { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
