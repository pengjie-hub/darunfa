<script setup>
/**
 * PermissionView.vue —— 权限管理（迁移自 admin.html renderPermissions() 行 1324-1398）。
 *
 * 表格列：ID / 编码 / 名称 / 模块 / 类型 / 排序 / 状态 / 时间 / 操作
 * 类型映射：MENU=菜单、BUTTON=按钮、API=接口
 * 编辑时编码不可改（后端同样以 code 为不可变标识）。
 *
 * 样式全部走 admin-theme.css 全局类，无 scoped 自绘。
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listPermissions, createPermission, updatePermission, deletePermission } from '@/api/rbac'
import { fmtDateTime } from '@/utils/format'

// 权限类型中文映射
const PERM_TYPE = { MENU: '菜单', BUTTON: '按钮', API: '接口' }

const loading = ref(false)
const perms = ref([])

async function load() {
  loading.value = true
  try {
    perms.value = await listPermissions()
  } catch (e) {
    ElMessage.error(e.message || '加载权限失败')
  } finally {
    loading.value = false
  }
}

// ────────────── 新增 / 编辑 ──────────────
const dialogVisible = ref(false)
const editId = ref(null)
const saving = ref(false)

const form = reactive({
  code: '', name: '', module: '', type: 'MENU', sortOrder: 0, status: 1
})

function openCreate() {
  editId.value = null
  Object.assign(form, { code: '', name: '', module: '', type: 'MENU', sortOrder: 0, status: 1 })
  dialogVisible.value = true
}

function openEdit(p) {
  editId.value = p.id
  Object.assign(form, {
    code: p.code || '',
    name: p.name || '',
    module: p.module || '',
    type: p.type || 'MENU',
    sortOrder: p.sortOrder ?? 0,
    status: p.status ?? 1
  })
  dialogVisible.value = true
}

async function submit() {
  const name = form.name.trim()
  if (!name) return ElMessage.warning('请输入名称')

  const body = {
    code: form.code.trim(),
    name,
    module: form.module.trim(),
    type: form.type,
    sortOrder: Number(form.sortOrder) || 0,
    status: Number(form.status)
  }

  saving.value = true
  try {
    if (editId.value) await updatePermission(editId.value, body)
    else await createPermission(body)
    ElMessage.success('已保存')
    dialogVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function onDelete(p) {
  try {
    await ElMessageBox.confirm(
      '确认删除权限「' + p.code + '」？将同时解除所有角色对该权限的关联。',
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    await deletePermission(p.id)
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
        <h3>权限管理</h3>
        <button class="btn primary sm" @click="openCreate">+ 新增权限</button>
      </div>

      <div class="panel-body">
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>编码</th>
                <th>名称</th>
                <th>模块</th>
                <th>类型</th>
                <th>排序</th>
                <th>状态</th>
                <th>时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="p in perms" :key="p.id">
                <td>{{ p.id }}</td>
                <td><b>{{ p.code }}</b></td>
                <td>{{ p.name }}</td>
                <td>
                  <span v-if="p.module" class="badge blue">{{ p.module }}</span>
                  <template v-else>-</template>
                </td>
                <td>{{ PERM_TYPE[p.type] || p.type }}</td>
                <td>{{ p.sortOrder }}</td>
                <td>
                  <span class="badge" :class="p.status === 1 ? 'green' : 'gray'">
                    {{ p.status === 1 ? '启用' : '禁用' }}
                  </span>
                </td>
                <td>{{ fmtDateTime(p.createdAt) }}</td>
                <td>
                  <div class="ops">
                    <button type="button" class="btn ghost sm" @click="openEdit(p)">编辑</button>
                    <button type="button" class="btn danger sm" @click="onDelete(p)">删除</button>
                  </div>
                </td>
              </tr>

              <tr v-if="!perms.length">
                <td colspan="9" class="empty">暂无权限</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 新增 / 编辑权限 -->
    <el-dialog v-model="dialogVisible" :title="editId ? '编辑权限' : '新增权限'" width="520px" :close-on-click-modal="false">
      <div class="form-row">
        <label>编码</label>
        <input v-model="form.code" :disabled="!!editId" placeholder="如 product:view" />
      </div>

      <div class="form-row">
        <label>名称</label>
        <input v-model="form.name" placeholder="中文展示名" />
      </div>

      <div class="form-row">
        <label>模块</label>
        <input v-model="form.module" placeholder="如 商品 / 订单 / 评论" />
      </div>

      <div class="form-row two">
        <div>
          <label>类型</label>
          <select v-model="form.type">
            <option value="MENU">菜单</option>
            <option value="BUTTON">按钮</option>
            <option value="API">接口</option>
          </select>
        </div>
        <div>
          <label>排序</label>
          <input v-model="form.sortOrder" type="number" />
        </div>
      </div>

      <div class="form-row">
        <label>状态</label>
        <select v-model="form.status">
          <option :value="1">启用</option>
          <option :value="0">禁用</option>
        </select>
      </div>

      <template #footer>
        <button type="button" class="btn ghost" @click="dialogVisible = false">取消</button>
        <button type="button" class="btn primary" :disabled="saving" @click="submit">{{ saving ? '保存中...' : '保存' }}</button>
      </template>
    </el-dialog>
  </div>
</template>
