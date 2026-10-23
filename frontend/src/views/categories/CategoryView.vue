<script setup>
/**
 * CategoryView.vue —— 分类管理（迁移自 admin.html renderCategories() 行 2013-2066）。
 *
 * 层级：parentId === 0 为一级分类，其余挂在一级下面，渲染时二级行缩进 32px 并加「↳」前缀。
 * 样式全部走 admin-theme.css 全局类，无 scoped 自绘。
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listCategories, createCategory, updateCategory, deleteCategory } from '@/api/categories'

const loading = ref(false)
const cats = ref([])

const parents = computed(() => cats.value.filter(c => c.parentId === 0))
const childrenOf = id => cats.value.filter(c => c.parentId === id)

async function load() {
  loading.value = true
  try {
    cats.value = await listCategories()
  } catch (e) {
    ElMessage.error(e.message || '加载分类失败')
  } finally {
    loading.value = false
  }
}

// ────────────── 新增 / 编辑 ──────────────
const dialogVisible = ref(false)
const editId = ref(null)
const saving = ref(false)

const form = reactive({ name: '', parentId: 0, sortOrder: 0, status: 1 })

function openCreate() {
  editId.value = null
  Object.assign(form, { name: '', parentId: 0, sortOrder: 0, status: 1 })
  dialogVisible.value = true
}

function openEdit(c) {
  editId.value = c.id
  Object.assign(form, {
    name: c.name || '',
    parentId: c.parentId ?? 0,
    sortOrder: c.sortOrder ?? 0,
    status: c.status ?? 1
  })
  dialogVisible.value = true
}

async function submit() {
  const name = form.name.trim()
  if (!name) return ElMessage.warning('请填写分类名称')

  const body = {
    name,
    parentId: Number(form.parentId) || 0,
    sortOrder: Number(form.sortOrder) || 0
  }
  if (editId.value) body.status = Number(form.status)

  saving.value = true
  try {
    if (editId.value) await updateCategory(editId.value, body)
    else await createCategory(body)
    ElMessage.success(editId.value ? '已更新' : '已新增')
    dialogVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function onDelete(c) {
  try {
    await ElMessageBox.confirm('删除该分类？', '提示', {
      confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
    })
    await deleteCategory(c.id)
    ElMessage.success('已删除')
    load()
  } catch (_) { /* 取消或失败 */ }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <div class="panel">
      <div class="panel-head">
        <h3>分类管理</h3>
        <button class="btn primary sm" @click="openCreate">+ 新增分类</button>
      </div>

      <div class="panel-body">
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>名称</th>
                <th>类型</th>
                <th>排序</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="p in parents" :key="p.id">
                <!-- 一级分类 -->
                <tr>
                  <td>{{ p.id }}</td>
                  <td><b>{{ p.name }}</b></td>
                  <td><span class="badge blue">一级</span></td>
                  <td>{{ p.sortOrder }}</td>
                  <td>
                    <span class="badge" :class="p.status === 1 ? 'green' : 'gray'">
                      {{ p.status === 1 ? '启用' : '停用' }}
                    </span>
                  </td>
                  <td>
                    <div class="ops">
                      <button type="button" class="btn ghost sm" @click="openEdit(p)">编辑</button>
                      <button type="button" class="btn danger sm" @click="onDelete(p)">删除</button>
                    </div>
                  </td>
                </tr>

                <!-- 二级分类 -->
                <tr v-for="x in childrenOf(p.id)" :key="x.id">
                  <td>{{ x.id }}</td>
                  <td style="padding-left:32px">↳ {{ x.name }}</td>
                  <td><span class="badge gray">二级</span></td>
                  <td>{{ x.sortOrder }}</td>
                  <td>
                    <span class="badge" :class="x.status === 1 ? 'green' : 'gray'">
                      {{ x.status === 1 ? '启用' : '停用' }}
                    </span>
                  </td>
                  <td>
                    <div class="ops">
                      <button type="button" class="btn ghost sm" @click="openEdit(x)">编辑</button>
                      <button type="button" class="btn danger sm" @click="onDelete(x)">删除</button>
                    </div>
                  </td>
                </tr>
              </template>

              <tr v-if="!cats.length">
                <td colspan="6" class="empty">暂无分类</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 新增 / 编辑分类 -->
    <el-dialog v-model="dialogVisible" :title="editId ? '编辑分类' : '新增分类'" width="520px" :close-on-click-modal="false">
      <div class="form-row">
        <label>分类名称 *</label>
        <input v-model="form.name" placeholder="如：水果类" />
      </div>

      <div class="form-row two">
        <div>
          <label>上级分类</label>
          <select v-model="form.parentId">
            <option :value="0">（一级分类）</option>
            <option v-for="p in parents" :key="p.id" :value="p.id">{{ p.name }}</option>
          </select>
        </div>
        <div>
          <label>排序权重</label>
          <input v-model="form.sortOrder" type="number" />
        </div>
      </div>

      <div v-if="editId" class="form-row">
        <label>状态</label>
        <select v-model="form.status">
          <option :value="1">启用</option>
          <option :value="0">停用</option>
        </select>
      </div>

      <template #footer>
        <button type="button" class="btn ghost" @click="dialogVisible = false">取消</button>
        <button type="button" class="btn primary" :disabled="saving" @click="submit">{{ saving ? '保存中...' : '保存' }}</button>
      </template>
    </el-dialog>
  </div>
</template>
