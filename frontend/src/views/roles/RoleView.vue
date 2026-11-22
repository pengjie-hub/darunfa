<script setup>
/**
 * RoleView.vue —— 角色管理（迁移自 admin.html renderRoles() 行 1196-1322）。
 *
 * 表格列、状态徽章、新增/编辑字段、删除确认文案、分配权限弹窗均与旧版对齐。
 * 样式全部走 admin-theme.css 全局类，无 scoped 自绘。
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listRoles, createRole, updateRole, deleteRole,
  getRolePermissions, assignRolePermissions, listPermissions
} from '@/api/rbac'
import { fmtDateTime } from '@/utils/format'

const loading = ref(false)
const roles = ref([])
const permissions = ref([])
const permCounts = ref({})

// 旧版并发调用 getRolePermissions 统计每个角色的已分配权限数，失败按 0
async function load() {
  loading.value = true
  try {
    const [roleList, permList] = await Promise.all([listRoles(), listPermissions()])
    roles.value = roleList || []
    permissions.value = permList || []

    const counts = {}
    await Promise.all(roles.value.map(async r => {
      try {
        const arr = await getRolePermissions(r.id)
        counts[r.id] = Array.isArray(arr) ? arr.length : 0
      } catch (_) {
        counts[r.id] = 0
      }
    }))
    permCounts.value = counts
  } catch (e) {
    ElMessage.error(e.message || '加载角色失败')
  } finally {
    loading.value = false
  }
}

// ────────────── 新增 / 编辑 ──────────────
const dialogVisible = ref(false)
const editId = ref(null)
const saving = ref(false)

const form = reactive({
  code: '', name: '', description: '', sortOrder: 0, dataScope: 'ALL', status: 1
})

function openCreate() {
  editId.value = null
  Object.assign(form, {
    code: '', name: '', description: '', sortOrder: 0, dataScope: 'ALL', status: 1
  })
  dialogVisible.value = true
}

function openEdit(r) {
  editId.value = r.id
  Object.assign(form, {
    code: r.code || '',
    name: r.name || '',
    description: r.description || '',
    sortOrder: r.sortOrder ?? 0,
    dataScope: r.dataScope || 'ALL',
    status: r.status ?? 1
  })
  dialogVisible.value = true
}

async function submit() {
  const name = form.name.trim()
  if (!name) return ElMessage.warning('请输入名称')

  const body = {
    code: form.code.trim(),
    name,
    description: form.description.trim(),
    sortOrder: Number(form.sortOrder) || 0,
    dataScope: form.dataScope,
    status: Number(form.status)
  }

  saving.value = true
  try {
    if (editId.value) await updateRole(editId.value, body)
    else await createRole(body)
    ElMessage.success('已保存')
    dialogVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function onDelete(r) {
  try {
    await ElMessageBox.confirm(
      `确认删除角色「${r.code}」？将同时解除其全部权限关联（请确保已无用户使用该角色）。`,
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (_) {
    return // 取消
  }
  try {
    await deleteRole(r.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    ElMessage.error(e.message || '删除失败')
  }
}

// ────────────── 分配权限 ──────────────
const permVisible = ref(false)
const permSaving = ref(false)
const currentRole = ref(null)
const selectedIds = ref([])

// 按 p.module 分组（无 module 归入「其他」），保持接口返回顺序
const permGroups = computed(() => {
  const map = new Map()
  for (const p of permissions.value) {
    const key = p.module || '其他'
    if (!map.has(key)) map.set(key, [])
    map.get(key).push(p)
  }
  return Array.from(map, ([module, list]) => ({ module, list }))
})

async function openPerms(r) {
  try {
    const ids = await getRolePermissions(r.id)
    selectedIds.value = Array.isArray(ids) ? ids.map(Number) : []
  } catch (e) {
    ElMessage.error('获取已分配权限失败：' + (e.message || ''))
    return
  }
  currentRole.value = r
  permVisible.value = true
}

function selectAllPerms() {
  selectedIds.value = permissions.value.map(p => p.id)
}

function clearPerms() {
  selectedIds.value = []
}

async function savePerms() {
  if (!currentRole.value) return
  const ids = selectedIds.value.map(Number)
  permSaving.value = true
  try {
    await assignRolePermissions(currentRole.value.id, ids)
    ElMessage.success(`已保存「${currentRole.value.name}」共 ${ids.length} 项权限`)
    permVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    permSaving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <div class="panel">
      <div class="panel-head">
        <h3>角色管理</h3>
        <button class="btn primary sm" @click="openCreate">+ 新增角色</button>
      </div>

      <div class="panel-body">
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>编码</th>
                <th>名称</th>
                <th>描述</th>
                <th>已分配权限</th>
                <th>数据范围</th>
                <th>排序</th>
                <th>状态</th>
                <th>时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in roles" :key="r.id">
                <td>{{ r.id }}</td>
                <td><b>{{ r.code }}</b></td>
                <td>{{ r.name }}</td>
                <td class="text-muted" style="max-width:240px">{{ r.description || '-' }}</td>
                <td><span class="badge blue">{{ permCounts[r.id] || 0 }} 项</span></td>
                <td>{{ r.dataScope || 'ALL' }}</td>
                <td>{{ r.sortOrder }}</td>
                <td>
                  <span class="badge" :class="r.status === 1 ? 'green' : 'gray'">
                    {{ r.status === 1 ? '启用' : '禁用' }}
                  </span>
                </td>
                <td>{{ fmtDateTime(r.createdAt) }}</td>
                <td>
                  <div class="ops">
                    <button type="button" class="btn ghost sm" @click="openPerms(r)">分配权限</button>
                    <button type="button" class="btn ghost sm" @click="openEdit(r)">编辑</button>
                    <button type="button" class="btn danger sm" @click="onDelete(r)">删除</button>
                  </div>
                </td>
              </tr>

              <tr v-if="!roles.length">
                <td colspan="10" class="empty">暂无角色</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 新增 / 编辑角色 -->
    <el-dialog v-model="dialogVisible" :title="editId ? '编辑角色' : '新增角色'" width="560px" :close-on-click-modal="false">
      <div class="form-row">
        <label>编码</label>
        <input v-model="form.code" :disabled="!!editId" :placeholder="editId ? '编码不可修改' : '如 ADMIN / OPS / CS / USER'" />
      </div>

      <div class="form-row">
        <label>名称</label>
        <input v-model="form.name" placeholder="中文展示名" />
      </div>

      <div class="form-row">
        <label>描述</label>
        <input v-model="form.description" placeholder="角色用途说明" />
      </div>

      <div class="form-row two">
        <div>
          <label>排序</label>
          <input v-model.number="form.sortOrder" type="number" />
        </div>
        <div>
          <label>数据范围</label>
          <select v-model="form.dataScope">
            <option value="ALL">全部 (ALL)</option>
            <option value="DEPT">本部门 (DEPT)</option>
            <option value="SELF">仅本人 (SELF)</option>
          </select>
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

    <!-- 分配权限 -->
    <el-dialog v-model="permVisible" :title="'分配权限 · ' + (currentRole?.name || '')" width="640px" :close-on-click-modal="false">
      <div class="hint" style="margin-bottom:12px">
        为角色 <b>{{ currentRole?.name }}</b> 分配权限（勾选即生效，覆盖现有全部授权）
      </div>

      <fieldset
        v-for="g in permGroups"
        :key="g.module"
        style="border:1px solid var(--border);border-radius:var(--radius-md);padding:10px 14px;margin-bottom:12px"
      >
        <legend style="padding:0 8px;color:var(--muted-foreground);font-size:12px;font-weight:600">{{ g.module }}</legend>
        <label
          v-for="p in g.list"
          :key="p.id"
          style="display:inline-flex;align-items:center;gap:8px;margin:4px 16px 4px 0;font-size:13px;cursor:pointer"
        >
          <input v-model="selectedIds" type="checkbox" :value="p.id" />
          <span>{{ p.name }}</span>
          <span class="text-muted" style="font-size:11px">{{ p.code }}</span>
        </label>
      </fieldset>

      <div v-if="!permGroups.length" class="empty">暂无可分配权限</div>

      <div style="margin-top:10px">
        <button type="button" class="btn ghost sm" @click="selectAllPerms">全选</button>
        <button type="button" class="btn ghost sm" @click="clearPerms">清空</button>
      </div>

      <template #footer>
        <button type="button" class="btn ghost" @click="permVisible = false">取消</button>
        <button type="button" class="btn primary" :disabled="permSaving" @click="savePerms">{{ permSaving ? '保存中...' : '保存' }}</button>
      </template>
    </el-dialog>
  </div>
</template>
