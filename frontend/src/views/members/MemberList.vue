<script setup>
/**
 * MemberList.vue —— 会员管理（迁移自 admin.html renderMembers() 行 1126-1194）。
 *
 * 列表 + 前端搜索（用户名 / 昵称）；仅 ADMIN 可「禁用/启用」「切换角色」。
 * 角色升降级涉及高危操作，沿用旧页的二次确认文案（升级授予全部权限 / 降级立即失效）。
 * 样式全部走 admin-theme.css 全局类。
 */
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listUsers, setUserStatus, setUserRole } from '@/api/members'
import { fmtDateTime } from '@/utils/format'
import { usePermission } from '@/composables/usePermission'
import StatusTag from '@/components/common/StatusTag.vue'

const { hasRole } = usePermission()
const isAdmin = computed(() => hasRole('ADMIN'))

const ROLE_BADGE = {
  ADMIN: { label: '超级管理员', color: 'red' },
  OPS:   { label: '运营专员',   color: 'blue' },
  CS:    { label: '客服',       color: 'orange' },
  USER:  { label: '普通用户',   color: 'gray' }
}
const ROLE_LABEL = {
  ADMIN: '超级管理员', OPS: '运营专员', CS: '客服', USER: '普通用户'
}
const ROLE_OPTIONS = [
  { value: 'USER',  label: '普通用户' },
  { value: 'OPS',   label: '运营专员' },
  { value: 'CS',    label: '客服' },
  { value: 'ADMIN', label: '超级管理员' }
]

const loading = ref(false)
const users = ref([])
const keyword = ref('')

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return users.value
  return users.value.filter(u =>
    (u.username || '').toLowerCase().includes(kw) ||
    (u.nickname || '').toLowerCase().includes(kw)
  )
})

async function load() {
  loading.value = true
  try {
    users.value = await listUsers()
  } catch (e) {
    ElMessage.error(e.message || '加载用户失败')
  } finally {
    loading.value = false
  }
}

// ────────────── 启用 / 禁用 ──────────────
const togglingId = ref(null)

async function toggleStatus(u) {
  togglingId.value = u.id
  try {
    await setUserStatus(u.id, u.status === 1 ? 0 : 1)
    ElMessage.success('已更新')
    await load()
  } catch (e) {
    ElMessage.error(e.message || '更新失败')
  } finally {
    togglingId.value = null
  }
}

// ────────────── 切换角色 ──────────────
const roleDialog = ref(false)
const roleTarget = ref(null)
const pickedRole = ref('')

function openRole(u) {
  roleTarget.value = u
  pickedRole.value = u.role
  roleDialog.value = true
}

async function submitRole() {
  const u = roleTarget.value
  if (!u) return
  if (!pickedRole.value) return ElMessage.warning('请选择角色')

  const userLabel = u.username + (u.nickname ? `（${u.nickname}）` : '')

  if (pickedRole.value === 'ADMIN' && u.role !== 'ADMIN') {
    try {
      await ElMessageBox.confirm(
        `确认将「${userLabel}」升级为「超级管理员」？该操作将授予该账号全部管理权限（包括编辑/删除商品、修改其他用户角色等高危操作）。`,
        '⚠️ 高危操作',
        { type: 'warning', confirmButtonText: '确认升级', cancelButtonText: '取消' }
      )
    } catch (_) { return }
  } else if (pickedRole.value !== 'ADMIN' && u.role === 'ADMIN') {
    try {
      await ElMessageBox.confirm(
        `确认将「${userLabel}」从「超级管理员」降级为「${ROLE_LABEL[pickedRole.value]}」？该账号将立即失去全部管理权限（系统会校验至少保留 1 名管理员）。`,
        '⚠️ 高危操作',
        { type: 'warning', confirmButtonText: '确认降级', cancelButtonText: '取消' }
      )
    } catch (_) { return }
  }

  try {
    await setUserRole(u.id, pickedRole.value)
    ElMessage.success('角色已更新为 ' + ROLE_LABEL[pickedRole.value])
    roleDialog.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message || '角色更新失败')
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <div class="panel">
      <div class="panel-head">
        <h3>会员管理</h3>
      </div>

      <div class="panel-body">
        <div class="toolbar">
          <input v-model="keyword" class="member-search" placeholder="搜索用户名/昵称..." />
        </div>

        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>用户名</th>
                <th>昵称</th>
                <th>手机号</th>
                <th>角色</th>
                <th>状态</th>
                <th>注册时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="u in filtered" :key="u.id">
                <td>{{ u.id }}</td>
                <td>{{ u.username }}</td>
                <td>{{ u.nickname || '-' }}</td>
                <td>{{ u.phone || '-' }}</td>
                <td>
                  <StatusTag
                    :text="ROLE_BADGE[u.role]?.label || '未知'"
                    :color="ROLE_BADGE[u.role]?.color || 'gray'"
                  />
                </td>
                <td>
                  <StatusTag
                    :text="u.status === 1 ? '正常' : '禁用'"
                    :color="u.status === 1 ? 'green' : 'gray'"
                  />
                </td>
                <td>{{ fmtDateTime(u.createdAt) }}</td>
                <td>
                  <div class="ops">
                    <template v-if="isAdmin">
                      <button
                        class="btn ghost sm"
                        :disabled="togglingId === u.id"
                        @click="toggleStatus(u)"
                      >
                        {{ u.status === 1 ? '禁用' : '启用' }}
                      </button>
                      <button class="btn ghost sm" @click="openRole(u)">切换角色</button>
                    </template>
                    <span v-else class="text-muted member-readonly">（只读）</span>
                  </div>
                </td>
              </tr>

              <tr v-if="!filtered.length">
                <td colspan="8" class="empty">暂无用户</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 切换角色 -->
    <el-dialog v-model="roleDialog" title="切换用户角色" width="460px" :close-on-click-modal="false">
      <div class="hint role-hint">
        为用户
        <b>{{ roleTarget?.username }}<template v-if="roleTarget?.nickname">（{{ roleTarget.nickname }}）</template></b>
        选择新角色（立即生效，下次请求即按新角色鉴权）
      </div>

      <div class="role-options">
        <label v-for="r in ROLE_OPTIONS" :key="r.value" class="form-row inline role-option">
          <input v-model="pickedRole" type="radio" name="rolePick" :value="r.value" />
          <span>{{ r.label }}</span>
          <span v-if="roleTarget && r.value === roleTarget.role" class="text-muted role-current">（当前）</span>
        </label>
      </div>

      <template #footer>
        <button type="button" class="btn ghost" @click="roleDialog = false">取消</button>
        <button type="button" class="btn primary" @click="submitRole">保存</button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
/* 旧页搜索框宽度 240px（.toolbar input 默认宽度不够） */
.member-search { width: 240px; }
.member-readonly { font-size: 12px; }
.role-hint { margin-bottom: 12px; }
.role-options { display: flex; flex-direction: column; gap: 2px; }
.role-option { padding: 4px 0; }
.role-current { font-size: 12px; }
</style>
