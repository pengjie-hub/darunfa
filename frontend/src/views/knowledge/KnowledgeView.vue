<script setup>
/**
 * KnowledgeView.vue —— AI 知识库（迁移自 admin.html renderKnowledge() 行 2252-2470）。
 *
 * 页面四块：概览四格、粉色口径说明、检索参数（含原文 / 闸门规则弹窗）、检索测试。
 * 检索测试的结果按旧版 renderSearchResult 渲染：先交代域与门槛，再列片段。
 * 样式全部走 admin-theme.css 全局类，深色面板细节沿用旧版行内样式。
 */
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { knowledgeStatus, searchKnowledge, rebuildKnowledge, knowledgeSource } from '@/api/knowledge'

// 快捷示例（文案、tip、检索词均与旧版一致）
const KNOWLEDGE_SAMPLES = [
  { q: '满多少免运费？', tip: '应命中「运费怎么算」（顾客侧实测 0.833）' },
  { q: '生鲜可以七天无理由退货吗？', tip: '应命中「七天无理由退货」（顾客侧实测 0.935，本例写全了条件所以分很高）' },
  { q: '券怎么没叠加？', tip: '应命中「优惠券与活动为什么有时候不叠加」（顾客侧实测 0.930）' },
  { q: '发货几天算超时？', tip: '应命中「发货要多久（发货时效承诺）」（顾客侧实测 0.909）' },
  { q: '怎么转人工？', tip: '★ 词面加权的价值：向量分只有 0.769（低于门槛），标题逐字命中后综合分 0.819 通过' },
  { q: '生鲜能退吗？', tip: '★ 贴近门槛的短问句：顾客侧实测 0.780，刚过 0.75 —— 再省略一点就会被拦' },
  { q: '今天有多少订单？', tip: '★ 闸门对照组：平台业务数字，知识库里没有答案，顾客侧会直接跳过检索' },
  { q: '有没有卖洗衣机？', tip: '对照组：实测 0.724，低于顾客侧门槛 0.75 —— 商品查询该由工具查库，不该走知识库' }
]

const SCOPE_META = {
  all: ['全量', '不过滤；小运（后台助手）用的就是这个域'],
  customer: ['顾客侧', '只含对外规则；小发与 AI 客服用的就是这个域'],
  ops: ['运营侧', '只含后台内部口径；没有助手单独用它，仅供排查']
}

// ────────────── 状态概览 ──────────────
const loading = ref(false)
const st = ref({ enabled: true })

async function loadStatus() {
  loading.value = true
  try {
    st.value = (await knowledgeStatus()) || {}
  } catch (e) {
    ElMessage.error(e.message || '加载知识库状态失败')
  } finally {
    loading.value = false
  }
}

const stat = computed(() => ({
  ready: !!st.value.ready,
  enabled: st.value.enabled !== false,
  segmentCount: st.value.segmentCount ?? 0,
  customerSegments: st.value.customerSegments ?? 0,
  opsSegments: st.value.opsSegments ?? 0,
  dimension: st.value.dimension || '-',
  embeddingModel: st.value.embeddingModel || '',
  minScore: st.value.minScore ?? '-',
  minScoreCustomer: st.value.minScoreCustomer ?? '-'
}))

const readyBadge = computed(() => {
  if (stat.value.ready) return { cls: 'green', text: '已就绪' }
  return stat.value.enabled
    ? { cls: 'orange', text: '未播种' }
    : { cls: 'gray', text: '已关闭' }
})

// 检索参数表：列 参数 / 当前值 / 说明，行内容与旧版一一对应
const params = computed(() => {
  const s = st.value
  return [
    {
      k: '嵌入模型',
      v: s.embeddingModel || '-',
      note: '本地 ONNX 推理，不调用外部 API，无配额与延迟成本'
    },
    {
      k: '受众域',
      v: `顾客侧 ${s.customerSegments ?? 0} / 运营侧 ${s.opsSegments ?? 0}`,
      note: '小节标题行末尾带 <!-- ops --> 即归运营侧，不写默认顾客侧；漏标只是让顾客多看到一条内部口径，反方向才是真事故'
    },
    {
      k: 'top-k',
      v: s.topK ?? '-',
      note: '每次最终注入的知识片段数'
    },
    {
      k: 'min-score（全量）',
      v: s.minScore ?? '-',
      note: '小运与「全量」域用。这个池子本就是顾客规则 + 后台口径混装，顾客规则类的分数带天然交错，它在这里的职责只是把闲聊挡在外面'
    },
    {
      k: 'min-score（顾客侧）',
      v: s.minScoreCustomer ?? '-',
      note: '小发与 AI 客服用的门槛。候选池成分不同，分数分布就不同，必须与全量分开校准'
    },
    {
      k: '词面加权',
      v: s.hybridEnabled
        ? `开（标题 +${s.hybridTitleBoost} / 正文 +${s.hybridBodyBoost}，候选 ${s.hybridCandidateK} 条）`
        : '关（纯向量）',
      note: '把问句实词与小节标题的字面重合度加成到向量分上，用来救「短问句向量分偏低但其实逐字命中标题」的真问题；关掉即完全退化为纯向量检索'
    },
    {
      k: '意图闸门',
      v: `${(s.guardRules || []).length} 条规则`,
      note: '命中的问法直接跳过检索 —— 这些问法在当前域里不可能有答案，召回任何片段都只是噪声'
    },
    {
      k: 'chunk-size / overlap',
      v: `${s.chunkSize ?? '-'} / ${s.chunkOverlap ?? '-'}`,
      note: '片段切分粒度与相邻重叠长度'
    },
    {
      k: '知识原文',
      v: `${s.docChars ?? '-'} 字`,
      note: `切分后共 ${s.segmentCount ?? 0} 个片段`
    }
  ]
})

const guardRules = computed(() => st.value.guardRules || [])

// ────────────── 重建 ──────────────
const rebuilding = ref(false)

async function onRebuild() {
  try {
    await ElMessageBox.confirm(
      '将清空现有向量，并按当前知识文本重新切分、重新向量化，通常 1~3 秒。',
      '确认重建知识库？',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (_) {
    return
  }

  rebuilding.value = true
  try {
    const r = await rebuildKnowledge()
    ElMessage.success(`重建完成：${r.segmentCount} 个片段 / ${r.dimension} 维 / 耗时 ${r.elapsedMs}ms`)
    await loadStatus()
  } catch (e) {
    ElMessage.error('重建失败：' + (e.message || e))
  } finally {
    rebuilding.value = false
  }
}

// ────────────── 知识原文 / 闸门规则弹窗 ──────────────
const sourceVisible = ref(false)
const sourceLoading = ref(false)
const sourceText = ref('')

async function openSource() {
  sourceVisible.value = true
  sourceLoading.value = true
  sourceText.value = ''
  try {
    const text = await knowledgeSource()
    sourceText.value = typeof text === 'string' ? text : (text == null ? '' : String(text))
  } catch (e) {
    ElMessage.error('读取失败：' + (e.message || e))
  } finally {
    sourceLoading.value = false
  }
}

const guardVisible = ref(false)

// ────────────── 检索测试 ──────────────
const query = ref('')
const scope = ref('customer')
const searching = ref(false)
const searchError = ref('')
const result = ref(null)

const items = computed(() => result.value?.items || [])

async function runSearch() {
  const q = (query.value || '').trim()
  if (!q) {
    ElMessage.warning('请输入要检索的问题')
    return
  }

  searching.value = true
  searchError.value = ''
  result.value = null
  try {
    result.value = await searchKnowledge(q, scope.value)
  } catch (e) {
    searchError.value = e.message || String(e)
  } finally {
    searching.value = false
  }
}

function fillSample(i) {
  const s = KNOWLEDGE_SAMPLES[i]
  if (!s) return
  query.value = s.q
  runSearch()
}

// 综合分色调：≥0.85 绿；过门槛 蓝；其余 灰
function toneOf(it) {
  const score = Number(it.score) || 0
  const min = Number(result.value?.minScore) || 0
  return score >= 0.85 ? 'green' : (score >= min ? 'blue' : 'gray')
}

function vecOf(it) {
  return (it.vectorScore === null || it.vectorScore === undefined) ? null : Number(it.vectorScore)
}

function boostOf(it) {
  const vec = vecOf(it)
  if (vec === null) return null
  const diff = (Number(it.score) || 0) - vec
  return diff <= 0.0005 ? null : diff
}

onMounted(loadStatus)
</script>

<template>
  <div v-loading="loading">
    <!-- 四格概览 -->
    <div class="stat-grid tight">
      <div class="stat-card">
        <div class="label">知识库状态</div>
        <div class="value" style="font-size:18px">
          <span class="badge" :class="readyBadge.cls">{{ readyBadge.text }}</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="label">知识片段</div>
        <div class="value primary">{{ stat.segmentCount }}</div>
        <div class="cell-meta">顾客侧 {{ stat.customerSegments }} / 运营侧 {{ stat.opsSegments }}</div>
      </div>
      <div class="stat-card">
        <div class="label">向量维度</div>
        <div class="value">{{ stat.dimension }}</div>
        <div class="cell-meta">{{ stat.embeddingModel }}</div>
      </div>
      <div class="stat-card">
        <div class="label">相似度门槛</div>
        <div class="value" style="font-size:18px">{{ stat.minScoreCustomer }} / {{ stat.minScore }}</div>
        <div class="cell-meta">顾客侧 / 全量</div>
      </div>
    </div>

    <!-- 粉色口径说明 -->
    <div class="hint" style="margin-bottom:20px;padding:14px 16px;background:var(--brand-50);border-radius:var(--radius-md);border-left:4px solid var(--brand-500);color:var(--text-700);line-height:1.8">
      助手回答<b>平台规则类</b>问题（运费、退款、发货时效、优惠叠加…）时，会先用向量检索从知识库里召回最相关的片段，
      再连同提问一起交给大模型。所以<b>这里的知识库内容 = 助手的对外口径</b>，改这里就是改助手怎么答。
      向量库建在内存中，因此每次重启后端都会按下面这份知识文本重新播种。<br />
      知识库按<b>受众域</b>分成两份：<b>顾客侧 {{ stat.customerSegments }} 个片段</b>（小发与 AI 客服可见，只含对外规则）、
      <b>运营侧 {{ stat.opsSegments }} 个片段</b>（GMV 口径、后台角色权限等内部口径，只有小运与这一页的「全量」能看到）。
      小节标题行末尾带 <code>&lt;!-- ops --&gt;</code> 即归入运营侧，不写则默认顾客侧。
    </div>

    <!-- 检索参数 -->
    <div class="panel" style="margin-bottom:22px">
      <div class="panel-head">
        <h3>检索参数</h3>
        <button class="btn ghost sm" :disabled="rebuilding" @click="onRebuild">
          {{ rebuilding ? '重建中…' : '🔄 重建知识库' }}
        </button>
      </div>
      <div class="panel-body">
        <div class="table-wrap">
          <table>
            <thead>
              <tr><th>参数</th><th>当前值</th><th>说明</th></tr>
            </thead>
            <tbody>
              <tr v-for="row in params" :key="row.k">
                <td><b>{{ row.k }}</b></td>
                <td>{{ row.v }}</td>
                <td class="cell-meta">{{ row.note }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div style="margin-top:16px;display:flex;gap:8px;flex-wrap:wrap">
          <button class="btn secondary sm" @click="openSource">📖 查看知识库原文</button>
          <button class="btn secondary sm" @click="guardVisible = true">🚧 查看意图闸门规则</button>
        </div>
      </div>
    </div>

    <!-- 检索测试 -->
    <div class="panel">
      <div class="panel-head">
        <h3>检索测试</h3>
        <span class="cell-meta">同一句话换个域，结果可能完全不同 —— 这是排查「顾客为什么答不上来」最快的一步</span>
      </div>
      <div class="panel-body">
        <div class="toolbar">
          <select v-model="scope" title="在哪个受众域里检索" style="max-width:210px">
            <option value="all">全量（小运用的域）</option>
            <option value="customer">顾客侧（小发 / AI 客服）</option>
            <option value="ops">运营侧（仅内部口径）</option>
          </select>
          <input
            v-model="query"
            maxlength="120"
            placeholder="输入一句用户会问的话，例如：满多少免运费？"
            @keyup.enter="runSearch"
          />
          <button class="btn primary" :disabled="searching" @click="runSearch">
            {{ searching ? '检索中…' : '检索' }}
          </button>
        </div>
        <div style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:18px">
          <button
            v-for="(s, i) in KNOWLEDGE_SAMPLES"
            :key="i"
            class="btn ghost sm"
            :title="s.tip"
            @click="fillSample(i)"
          >{{ s.q }}</button>
        </div>

        <!-- 检索结果 -->
        <div v-if="searching" class="empty">检索中…</div>
        <div v-else-if="searchError" class="empty">检索失败：{{ searchError }}</div>
        <div v-else-if="!result" class="empty">输入问题后点「检索」，这里会列出被召回的规则片段、相似度，以及是否被意图闸门拦下。</div>
        <template v-else>
          <div class="hint" style="margin-bottom:12px">
            域 <b>{{ (SCOPE_META[result.scope] || ['未知', ''])[0] }}</b>（{{ (SCOPE_META[result.scope] || ['未知', ''])[1] }}）；
            本域门槛 min-score=<b>{{ result.minScore }}</b>；
            词面加权 <b>{{ result.hybridEnabled ? '开' : '关' }}</b>。
          </div>

          <div v-if="result.blocked" style="border:1px solid var(--border);border-left:4px solid var(--warning);border-radius:var(--radius-md);padding:16px;background:var(--card);line-height:1.9">
            🚧 <b>被意图闸门拦下，本次没有检索知识库。</b><br />
            {{ result.blockReason }}<br />
            <span class="cell-meta">这类问题应交由工具查库或直接回答；硬塞平台规则进上下文只会污染模型判断。
            把上面的域切到别的域再试，可以确认答案是否存在于另一侧的池子里。</span>
          </div>

          <div v-else-if="!items.length" class="empty" style="text-align:left;line-height:1.9">
            这个域里没有命中任何片段 —— 最高相似度低于本域门槛 {{ result.minScore }}。<br />
            可能是：① 知识库里确实没有这条规则；② 有但被切分或表述影响、没被召回；
            ③ 该小节被标成了另一侧（切换上面的域试试）。<br />
            <span class="cell-meta">另外，「生鲜能退吗？」这类过短的问句向量分天然偏低，写全条件后分数会明显上升。</span>
          </div>

          <template v-else>
            <div class="hint" style="margin-bottom:12px">
              共召回 <b>{{ items.length }}</b> 个片段（按综合分降序，已通过 min-score={{ result.minScore }} 过滤；「综合分」= 向量分 + 词面加权）
            </div>
            <div
              v-for="(it, i) in items"
              :key="i"
              style="border:1px solid var(--border);border-radius:var(--radius-md);padding:14px 16px;margin-bottom:12px;background:var(--card)"
            >
              <div style="display:flex;align-items:center;gap:10px;margin-bottom:9px;flex-wrap:wrap">
                <span class="badge primary">#{{ i + 1 }}</span>
                <b style="font-size:13px">{{ it.section || '未标注小节' }}</b>
                <span class="badge" :class="toneOf(it)">综合分 {{ (Number(it.score) || 0).toFixed(3) }}</span>
                <span v-if="vecOf(it) !== null" class="badge gray">向量 {{ vecOf(it).toFixed(3) }}</span>
                <span v-if="boostOf(it) !== null" class="badge orange" title="词面加权加上去的分">词面 +{{ boostOf(it).toFixed(3) }}</span>
              </div>
              <div style="font-size:13px;line-height:1.8;color:var(--text-700);white-space:pre-wrap;word-break:break-word">{{ it.text }}</div>
            </div>
          </template>
        </template>
      </div>
    </div>

    <!-- 知识库原文 -->
    <el-dialog v-model="sourceVisible" title="📖 AI 知识库原文" width="820px">
      <div v-loading="sourceLoading">
        <div class="hint" style="margin-bottom:12px">
          共 {{ (sourceText || '').length }} 字。这份文本就是助手回答平台规则类问题时的唯一依据；改动后建议点一次「重建知识库」。
        </div>
        <pre style="max-height:56vh;overflow:auto;background:var(--bg-100);border:1px solid var(--border);border-radius:var(--radius-md);padding:14px;font-family:var(--font-mono);font-size:12px;line-height:1.75;white-space:pre-wrap;word-break:break-word">{{ sourceText }}</pre>
      </div>
    </el-dialog>

    <!-- 意图闸门规则 -->
    <el-dialog v-model="guardVisible" title="🚧 意图闸门规则（顾客侧检索专用）" width="760px">
      <div class="hint" style="margin-bottom:14px;line-height:1.8">
        这些规则命中的问法会<b>直接跳过知识库检索</b>，而不是靠相似度阈值去拦。<br />
        判据只有一条：<b>这句话在顾客侧知识库里不可能有答案。</b>
        答案是查库得来的数字（今天有多少订单、销售额是多少），或是只存在于运营侧的内部概念
        （后台角色权限、客服接待量、低库存补货），那么召回任何顾客侧片段都只是往上下文里塞噪声 ——
        不如干净地交给工具查库，或者让助手坦白不知道。<br />
        它<b>不会</b>拦「生鲜能退吗？」这类规则问题：哪怕向量分只有 0.780、贴着门槛，也是知识库里真有答案的。
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr><th>规则</th><th>正则</th><th>拦的是什么</th></tr>
          </thead>
          <tbody>
            <tr v-for="r in guardRules" :key="r.name">
              <td><b>{{ r.name }}</b></td>
              <td><code style="font-size:12px">{{ r.pattern }}</code></td>
              <td class="cell-meta">{{ r.why }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </el-dialog>
  </div>
</template>
