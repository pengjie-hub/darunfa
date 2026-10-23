<script setup>
/**
 * PromotionView.vue —— 促销管理（迁移自 admin.html renderPromotions() 行 2069-2157）。
 *
 * 两种类型：
 *   FULL_REDUCTION 满减 → 优惠列「减 ¥X」，discount 单位元，校验 ≥ 0
 *   DISCOUNT       折扣 → 优惠列「打 N 折」，discount 单位是小数（0~1，如 0.9 即 9 折），校验 ∈ (0,1]
 * 类型切换时弹窗里「优惠」字段的 label 与提示会联动变化。
 *
 * 样式全部走 admin-theme.css 全局类，无 scoped 自绘。
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listPromotions, createPromotion, updatePromotion, deletePromotion } from '@/api/promotions'
import { PROMO_TYPE } from '@/utils/constants'
import { fmtMoney, fmtDateTime } from '@/utils/format'

const loading = ref(false)
const promos = ref([])

async function load() {
  loading.value = true
  try {
    promos.value = await listPromotions()
  } catch (e) {
    ElMessage.error(e.message || '加载促销失败')
  } finally {
    loading.value = false
  }
}

// 门槛文案：0 或空 → 无门槛
function thresholdText(p) {
  return Number(p.threshold) > 0 ? '满 ' + fmtMoney(p.threshold) : '无门槛'
}

// 优惠文案：满减按金额，折扣按「几折」
function discountText(p) {
  return p.type === 'FULL_REDUCTION'
    ? '减 ' + fmtMoney(p.discount)
    : '打 ' + (Number(p.discount) * 10).toFixed(1) + ' 折'
}

// ────────────── 新增 / 编辑 ──────────────
const dialogVisible = ref(false)
const editId = ref(null)
const saving = ref(false)

const form = reactive({
  name: '', type: 'FULL_REDUCTION', threshold: 0, discount: '',
  startTime: '', endTime: '', imageUrl: '', description: '', link: '', status: 1
})

// datetime-local 只认 yyyy-MM-ddTHH:mm，后端返回的字符串要截前 16 位
function toLocalInput(v) {
  return v ? String(v).substring(0, 16) : ''
}

function openCreate() {
  editId.value = null
  Object.assign(form, {
    name: '', type: 'FULL_REDUCTION', threshold: 0, discount: '',
    startTime: '', endTime: '', imageUrl: '', description: '', link: '', status: 1
  })
  dialogVisible.value = true
}

function openEdit(p) {
  editId.value = p.id
  Object.assign(form, {
    name: p.name || '',
    type: p.type || 'FULL_REDUCTION',
    threshold: p.threshold ?? 0,
    discount: p.discount ?? '',
    startTime: toLocalInput(p.startTime),
    endTime: toLocalInput(p.endTime),
    imageUrl: p.imageUrl || '',
    description: p.description || '',
    link: p.link || '',
    status: p.status ?? 1
  })
  dialogVisible.value = true
}

const discountLabel = computed(() =>
  form.type === 'DISCOUNT' ? '折扣率（0~1，如 0.9）' : '减免金额（元）'
)

async function submit() {
  const name = form.name.trim()
  if (!name) return ElMessage.warning('请填写活动名称')

  const discount = Number(form.discount)
  if (form.type === 'DISCOUNT') {
    if (!(discount > 0 && discount <= 1)) {
      return ElMessage.warning('折扣率需在 (0,1] 之间，如 0.9 表示 9 折')
    }
  } else if (discount < 0) {
    return ElMessage.warning('满减金额不能为负')
  }

  const body = {
    name,
    type: form.type,
    threshold: Number(form.threshold) || 0,
    discount,
    startTime: form.startTime ? form.startTime + ':00' : null,
    endTime: form.endTime ? form.endTime + ':00' : null,
    imageUrl: form.imageUrl.trim() || null,
    description: form.description.trim() || null,
    link: form.link.trim() || null,
    status: Number(form.status)
  }

  saving.value = true
  try {
    if (editId.value) await updatePromotion(editId.value, body)
    else await createPromotion(body)
    ElMessage.success(editId.value ? '已更新' : '已新增')
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
    await ElMessageBox.confirm('删除该促销？', '提示', {
      confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
    })
    await deletePromotion(p.id)
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
        <h3>促销管理</h3>
        <button class="btn primary sm" @click="openCreate">+ 新增促销</button>
      </div>

      <div class="panel-body">
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>活动名称</th>
                <th>类型</th>
                <th>门槛</th>
                <th>优惠</th>
                <th>有效期</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="p in promos" :key="p.id">
                <td>{{ p.id }}</td>
                <td><b>{{ p.name }}</b></td>
                <td><span class="badge blue">{{ PROMO_TYPE[p.type]?.label || p.type }}</span></td>
                <td>{{ thresholdText(p) }}</td>
                <td>{{ discountText(p) }}</td>
                <td>
                  {{ fmtDateTime(p.startTime) }}<br />至 {{ fmtDateTime(p.endTime) }}
                </td>
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

              <tr v-if="!promos.length">
                <td colspan="8" class="empty">暂无促销活动</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 新增 / 编辑促销 -->
    <el-dialog v-model="dialogVisible" :title="editId ? '编辑促销' : '新增促销'" width="600px" :close-on-click-modal="false">
      <div class="form-row">
        <label>活动名称 *</label>
        <input v-model="form.name" placeholder="如：满100减20" />
      </div>

      <div class="form-row">
        <label>促销类型</label>
        <select v-model="form.type">
          <option value="FULL_REDUCTION">满减（满 X 减 Y）</option>
          <option value="DISCOUNT">折扣（打 N 折）</option>
        </select>
      </div>

      <div class="form-row two">
        <div>
          <label>门槛金额（元）</label>
          <input v-model="form.threshold" type="number" step="0.01" />
          <div class="hint">0 表示无门槛</div>
        </div>
        <div>
          <label>{{ discountLabel }}</label>
          <input v-model="form.discount" type="number" step="0.01" />
        </div>
      </div>

      <div class="form-row two">
        <div>
          <label>开始时间</label>
          <input v-model="form.startTime" type="datetime-local" />
        </div>
        <div>
          <label>结束时间</label>
          <input v-model="form.endTime" type="datetime-local" />
        </div>
      </div>

      <div class="form-row">
        <label>活动配图 URL</label>
        <input v-model="form.imageUrl" placeholder="https://...  留空则前台使用内置素材占位" />
        <div class="hint">前台卡片顶部配图</div>
      </div>

      <div class="form-row">
        <label>活动描述</label>
        <input v-model="form.description" placeholder="留空则按满减/折扣规则自动生成" />
        <div class="hint">显示在前台卡片上的简短文案</div>
      </div>

      <div class="form-row">
        <label>跳转链接</label>
        <input v-model="form.link" placeholder="https://...  或纯数字商品ID，留空默认跳促销列表页" />
        <div class="hint">点击「查看详情」的跳转目标</div>
      </div>

      <div class="form-row">
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
