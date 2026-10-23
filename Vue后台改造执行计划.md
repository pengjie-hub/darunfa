# Vue 3 后台改造执行计划

> 背景：现有后台为 Spring Boot 托管的单文件 SPA，实际规模为 `admin.html` **2914 行 / 164.6 KB**（含内联 CSS 与 JS）+ `admin-theme.css`（1027 行）+ `xiaoyun-action.js`（647 行）+ `xiaoyun-action.css`；采用原生 HTML/JS + D3 CDN 实现；后端 Spring Boot 4.1.1 + MySQL，端口 8080。本次将其改造为基于 Vue 3 + Vite 的标准前端工程，通过 `npm run dev` 在 VS Code 终端直接启动运行。
>
> 接口规模实测：`admin.html` 中 `api('...')` 调用按路径去重共 **48 条**（47 个 `/admin/*` + 1 个 `/auth/me`）。

---

## 0. 现状基线（改造约束）

| 项 | 现状 | 对改造的约束 |
|---|---|---|
| 页面载体 | `src/main/resources/static/admin.html`（HTML + 内联 CSS/JS） | 迁移期间**保留原文件不动**，作为回滚基准 |
| 样式 | `admin-theme.css` 自定义设计 token，主色 `#DE1313` | 必须整包复用，Element Plus 需覆盖主题色 |
| 图表 | D3 v7（CDN），donut / hbar / trend 三类 | 改为 npm 依赖，封装成 3 个 Vue 组件 |
| 交互 | `innerHTML` 拼接 + 全局 `window.*` 函数 + `<script>` 内 `onclick` | 全部改为响应式状态 + 组件方法 |
| 鉴权 | `localStorage.token` + `Authorization: Bearer`，`/api/auth/me` 拿 role/permissions | 新的项目必须沿用同一 key，保证新旧页面互认登录态 |
| 后端 | Spring Boot 4.1.1，端口 8080，静态资源由后端托管 | 开发期用 Vite 代理，生产期构建回后端静态目录 |
| **版本控制** | **当前不是 git 仓库**（`fatal: not a git repository`，父目录也没有） | **回滚基准目前为零**，P0 第一步必须 `git init` 并完成首次提交 |
| **小运卡片** | `xiaoyun-action.js` 挂了 `window.XiaoyunAction`，被 `admin.html` 调用 5 处，含四态状态机与幂等语义 | 必须单独迁移成一等公民，见 3.5 第 10 类；不受"禁止 window.*"约束 |

> 迁移窗口风险：`admin.html`、`xiaoyun-action.css` 的 mtime 为 2026-10-22 16:12，即**改造计划制定时旧页面仍在迭代**。迁移期间必须约定冻结策略（见 5.1），否则"迁完发现旧页又多了功能"。

---

## 1. 技术栈选型与版本规范

| 类别 | 选型 | 版本要求 | 说明 |
|---|---|---|---|
| 运行时 | Node.js | **22.x LTS（最低 20.19 / 22.12）；本机已验证 24.21.0 可用** | Vite 7 的硬性要求，低版本直接启动失败 |
| 包管理 | npm | ≥ 10.9，随 Node 安装 | 用 `package-lock.json` + `npm ci` 保证一致性 |
| 核心框架 | Vue | `^3.5.x`（组合式 API + `<script setup>`） | 不使用 Options API，不引入 TS（现有代码是 JS，降低迁移成本） |
| 构建工具 | **Vite** | `^7.x` + `@vitejs/plugin-vue ^6.x` | 不选 Webpack：冷启动、HMR 均优于 Webpack 5，且是 Vue 官方推荐 |
| 路由 | Vue Router | `^4.5.x`，**hash 模式** | 生产构建由 Spring Boot 托管，hash 模式无需后端配置 fallback |
| 状态管理 | Pinia | `^3.0.x` | 只建 2 个 store：`auth`（登录态/权限）、`app`（侧栏折叠/未读角标） |
| UI 组件库 | **Element Plus** | `^2.11.x` + `@element-plus/icons-vue ^2.3.x` | 表格/表单/弹窗/消息/分页全覆盖；中文 locale 必须显式配置 |
| HTTP | Axios | `^1.12.x` | 统一拦截 `ApiResponse{code,message,data}` |
| 图表 | D3 | `^7.9.x` | 复用现有绘图代码，视觉零偏差（ECharts 作为二期可选升级，不进本期） |
| 日期 | dayjs | `^1.11.x` | Element Plus 已依赖，显式声明避免幽灵依赖 |
| 规范 | ESLint `^9.x`(flat config) + eslint-plugin-vue `^10.x` + Prettier `^3.x` | 提交前 `npm run lint` 必须 0 error |

版本策略：上表用 caret 范围安装，**安装完成后把 `package-lock.json` 提交入库**，团队统一 `npm ci`；在 `package.json` 写明 `"engines": { "node": ">=20.19" }`，并放 `.nvmrc`。

---

## 2. 项目初始化步骤（逐条可执行）

```powershell
# 2.0 版本控制基线（已于 2026-10-23 完成，见下方状态说明）
cd C:\Users\25136\Desktop\darunfa
git init -b main
git add -A
git commit -m "chore: 基线提交（Vue 后台改造前存档）"
git checkout -b feature/vue-admin
```

> **2.0 已完成（2026-10-23）**：`.gitignore` 已建立（排除 `node_modules/` `dist/` `target/` `build/` `.gradle/` `.workbuddy/` `.idea/` `.vscode/` `*.zip`），基线提交 `ca4796d` 已落库，共 658 个文件，`main` 与 `feature/vue-admin` 两个分支均指向该提交，工作区干净。当前处于 **`feature/vue-admin`** 分支，后续改造均在此分支进行；出问题 `git checkout main` 即回到改造前状态。
> 已知未入库项：`*.zip`（3 个 104M 备份包）按 `.gitignore` 排除在外 —— 若需一并归档，应改用 Git LFS 或外部网盘，不要直接 `git add`。

```powershell
# 2.1 环境确认（PowerShell 5.1 不支持 &&，命令逐行执行）
node -v      # 必须 >= 20.19，推荐 22.x（本机 24.21.0 已验证）
npm -v

# 2.2 创建项目（在仓库根目录）
cd C:\Users\25136\Desktop\darunfa
npm create vite@latest frontend -- --template vue
cd frontend

# 2.3 安装基础依赖
npm install
npm install vue-router@4 pinia element-plus @element-plus/icons-vue axios d3 dayjs
npm install -D eslint eslint-plugin-vue @vue/eslint-config-prettier prettier

# 2.4 首次启动验证
npm run dev
```

初始化后必须补齐的 5 个配置文件：

### ① `vite.config.js`

```js
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  server: {
    host: 'localhost',
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true }
    }
  }
})
```

### ② `.env.development` / `.env.production`

```
VITE_API_BASE=/api
```

### ③ `src/api/http.js`

对齐原 `api()` 函数语义。原实现签名（`admin.html:167`）：

```js
async function api(path, method = 'GET', body = null, extraHeaders = null, raw = false)
```

新封装必须**逐个参数对齐**，不能只做「baseURL + token + code 判断」三件套：

- `baseURL = import.meta.env.VITE_API_BASE`
- 请求拦截：从 `localStorage.token` 注入 `Authorization: Bearer ...`
- **`extraHeaders` 透传**：供小运卡片传 `Idempotency-Key`（防连点）与 `X-Proposal-Id`。必须照原逻辑**跳过空值**（`admin.html:186-188`）—— fetch 会把 `null` 序列化成字符串 `"null"`，服务端会当成有效键，导致幂等键失效。
- **`raw` 信封模式（关键，漏了就是回归）**：`raw=true` 时返回完整 `{code, subCode, message, data}`，且 **`code !== 0` 不抛异常**（`admin.html:190-193`）。默认模式才做 `code !== 0 → reject`。
  > 为什么必须有：小运卡片确认第二次，服务端返回 `code=0 + subCode=DUPLICATE_ACTION + data=上次结果`，这是**成功**语义。若统一在拦截器里按 `code !== 0` 抛错、丢掉 `subCode`，前端就分不清「成功」「已执行过」「失败」三种状态 —— 这是 `xiaoyun-action.js` 头部注释里点名的「最贵的一类 bug」。
- 响应拦截：
  - `code !== 0` → reject `message`（**仅默认模式**）
  - HTTP 401 → 清 token + 跳 `/login`（**加当前路由判断防循环**）；注意 401/403 处理必须在 raw 分支之前，两种模式行为一致
  - HTTP 403 → 只抛错，不登出
- **超时设为 90s**：AI 接口后端 `deepseek.timeout-seconds=60`，Axios 默认超时会截断 AI 回复

建议同时给 `src/api/` 下每个函数补 JSDoc `@returns` 类型注释 —— 48 个接口的返回结构若全靠记忆，16 个 view 并行开发必然出错。

### ④ `src/main.js`

```js
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'   // 必须先于自定义主题
import '@/styles/admin-theme.css'
import '@/styles/element-overrides.css'
```

**引入方式取舍（必须显式决定，不要留默认）**：本期采用**全量引入**（`app.use(ElementPlus)` + 全量 CSS），预期产物 ~500 KB（gzip 前）。理由是内部后台、首屏不敏感、按需引入要额外引 `unplugin-vue-components` + `unplugin-auto-import` 并处理样式自动导入，收益不足以抵成本。如果后续要压包，再切按需 —— 届时需同步验证 `.el-*` 样式是否完整，避免组件渲染成裸 DOM。

### ⑤ `src/styles/element-overrides.css`

把 `--el-color-primary` 等映射到现有 token（`#DE1313`），保证 Element Plus 组件与旧页面同色系。

**验收**：`npm run dev` 输出 `VITE v7.x ready`，浏览器打开 `http://localhost:5173` 显示默认页且控制台无报错。

---

## 3. 现有页面迁移方案

### 3.1 目录结构

```
frontend/src/
├── api/                 # 每个业务域一个文件（见 3.4）
├── components/
│   ├── charts/          # DonutChart.vue / HBarChart.vue / TrendChart.vue
│   ├── common/          # PagePanel.vue / StatCard.vue / StatusTag.vue
│   └── ai/              # AiAssistant.vue / MarketingCopyPanel.vue / XiaoyunCard.vue
├── composables/         # usePermission.js / usePolling.js / useLowStockAlert.js / useXiaoyunCard.js
├── layouts/AdminLayout.vue
├── router/index.js
├── stores/              # auth.js / app.js
├── styles/              # admin-theme.css / element-overrides.css
├── utils/               # format.js / constants.js
└── views/               # 16 个路由页
```

### 3.2 组件拆分规则

1. **一个 nav 模块 = 一个路由级 view**，与左侧菜单 `data-mod` 一一对应，禁止把多个模块塞进一个文件。
2. **单个 `.vue` 超过 300 行**，或某段模板被 2 处以上复用时，抽成子组件。
3. **API 调用只允许出现在 `src/api/`**，组件内不写 URL 字符串。
4. **纯函数（金额/时间/状态映射）进 `utils/`**，不放组件内。
5. 禁止使用 `window.*` 挂载函数、禁止 `document.getElementById`、禁止字符串 `innerHTML` 拼模板。
   > **唯一例外：`xiaoyun-action.js` 的 `window.XiaoyunAction`。** 它不是"随手挂的全局函数"，而是被 `admin.html` 调用 5 处的独立渲染器（见 3.5 第 10 类）。迁移方式是改成 ES module `export` 后 `import`，**不是删掉重写**，且必须保留其四态状态机与幂等语义。
6. 每个页面必须显式实现 `loading / empty / error` 三态。

### 3.3 路由配置规范

`router/index.js` 用 `createWebHashHistory()`，路由表与权限映射：

| path | 组件 | meta.title | meta.permission |
|---|---|---|---|
| `/login` | LoginView | 登录 | `public: true` |
| `/dashboard` | DashboardView | 仪表盘 | — |
| `/products` | ProductList | 商品管理 | `product:view` |
| `/orders` | OrderList | 订单管理 | `order:view` |
| `/refunds` | RefundList | 退款管理 | `order:view, cs:manage`（OR 语义） |
| `/risk` | RiskView | 风险监控 | `risk:view` |
| `/stock` | StockView | 库存管理 | `product:view` |
| `/members` | MemberList | 会员管理 | `member:view` |
| `/roles` | RoleView | 角色管理 | `adminOnly: true` |
| `/permissions` | PermissionView | 权限管理 | `adminOnly: true` |
| `/reviews` | ReviewView | 评论管理 | `review:reply` |
| `/cs` | CsView | 客服管理 | `cs:manage` |
| `/categories` | CategoryView | 分类管理 | `product:view` |
| `/promotions` | PromotionView | 促销管理 | `promotion:manage` |
| `/payments` | PaymentList | 支付管理 | `order:view` |
| `/shipments` | ShipmentList | 物流管理 | `order:view` |
| `/knowledge` | KnowledgeView | AI 知识库 | `adminOnly: true` |
| `/:pathMatch(.*)*` | 重定向 `/dashboard` | — | — |

守卫逻辑：
- 无 token → `/login`
- 有 token 但 store 无用户 → `await fetchMe()`
- `adminOnly` 且 `role !== 'ADMIN'` → `/dashboard`
- 有 `permission` 则按原页面 `hasPerm(...codes)` 的 **OR 语义**校验

侧边栏菜单同样按这份 meta 过滤，保证与旧页 `applyNavVisibility()` 行为一致。

### 3.4 静态资源迁移

| 原位置 | 新位置 | 处理方式 |
|---|---|---|
| `static/admin-theme.css` | `src/styles/admin-theme.css` | 原样复制；**删除 Google Fonts `@import`**（已核实位于 `admin-theme.css:5`，离线环境会阻塞首屏，字体回退到系统栈） |
| D3 CDN `<script>` | `npm i d3` | `import * as d3 from 'd3'` |
| `static/login.html` | `views/login/LoginView.vue` | 用现有 `login-card` 结构重写，样式沿用同一 CSS |
| **`static/xiaoyun-action.js`（647 行）** | `composables/useXiaoyunCard.js` + `components/ai/XiaoyunCard.vue` | **单独一整块，见 3.5 第 10 类**；改成 ES module 导出，不要删 |
| **`static/xiaoyun-action.css`（15 KB）** | `src/styles/xiaoyun-action.css` | 原样复制，随 `XiaoyunCard.vue` 引入 |
| 图片/favicon | `frontend/public/` | 按原相对路径引用 |

CSS 冲突处理：现有 `admin-theme.css` 含全局 `body/table/.btn` 规则，会污染 Element Plus。方案是把布局根节点包一层 `.admin-app`，将全局选择器限定在 `.admin-app` 内；`element-overrides.css` 在其后引入。

### 3.5 原有业务逻辑适配（核心工作量）

按以下 **10** 类逐一转换，每类给出固定改法：

1. **模板拼接 → 模板语法**：`innerHTML + esc()` 改为 `v-for` + `{{ }}`（Vue 自动转义）；仅 AI 回复允许 `v-html`，且必须先做白名单净化。
2. **全局函数 → 组件方法**：`window.toggleUser / delCat / refundPay / shipStatus / csSelect / csSendReply / csHandBack` 全部改为 setup 内函数，模板用 `@click`。
3. **`switchMod` → 路由**：侧栏 `router-link`，`<router-view>` 承载内容，标题取 `route.meta.title`。
4. **`openModal/closeModal` 回调式弹窗 → `ElDialog`**：改为 `visible` 响应式 + `async/await` 提交流程（商品、库存、分类、促销、角色、权限、发货、退款拒绝等 8 处）。
5. **`toast()` → `ElMessage`**；`confirm()` → `ElMessageBox.confirm`（异步，需 `try/catch` 捕获取消）。
6. **轮询收口（分两种，不要混）**：
   - **页内轮询**：客服页 5 秒消息轮询（原 `csTimer`，`admin.html:1927`）改 `usePolling`，在 `onUnmounted` / `onBeforeRouteLeave` 清理。生命周期归客服页，**不上升为全局**。
   - **全局未读角标**：`refreshRefundUnread()`（`admin.html:1026`）、`refreshRiskUnread()`（`admin.html:1401`）、`refreshCsUnread()`（`admin.html:1794`）收进 `app` store 统一轮询，周期 **30s**，且 `document.visibilityState !== 'visible'` 时暂停（避免后台标签页持续打接口）。
7. **AI 运营助手**：`AiAssistant.vue` 挂在 `AdminLayout`，仅 ADMIN 渲染悬浮球；保留 6 个快捷提问、历史加载（`/admin/ai/history`）、清空记忆（`DELETE /admin/ai/memory`）、Enter 发送、滚动到底。
8. **AI 营销文案面板**：原 `aiCopyMount(panelId, cfg)` 依赖 DOM id 的方案改为 `MarketingCopyPanel.vue`，用 props 传场景、emit 回填字段（商品/促销表单各引一次）。
9. **图表**：3 个 D3 函数改成 props 驱动组件，`onMounted` 绘制 + `watch(data)` 重绘 + `ResizeObserver` 自适应；`<title>` 悬浮提示保留。
10. **小运操作确认卡片（`xiaoyun-action.js`，647 行）—— 原计划遗漏项，最容易造成回归的一块**

    现状：`xiaoyun-action.js` 末尾挂到 `window.XiaoyunAction`，`admin.html` 有 **5 处调用**——`admin.html:2689`、`2696`（`renderOptions`）、`2704`、`2779`、`2811`（`renderAll`）。它不是纯展示组件，而是一套带状态机的业务渲染器，文件头部注释写死了 3 条铁律：

    | 铁律 | 内容 | 迁移时必须保住什么 |
    |---|---|---|
    | 1 | 卡片上所有业务文字只能来自服务端 `proposal` / 响应体，前端**一个字都不许自己算** | 金额只打印 `proposal.fields[].value`（服务端已格式化）；状态变化只打印 `fields[]` 字符串；风险只打印 `risk.level/reasons/note`；预检只打印 `precheck.checks[].message`。前端只能决定"界面文案"（按钮名、待确认/执行中/成功/失败、字段名兜底） |
    | 2 | 四态状态机，执行中必须锁死按钮 | `proposed → running → success/failed`；`running` 时按钮 `disabled` **并且** 置 `state.busy = true`（防止 devtools 去掉 disabled 后连点）；服务端幂等键兜底是第三层，三层缺一不可 |
    | 3 | `DUPLICATE_ACTION` 不是错误 | 服务端对同一张卡片第二次确认返回 `code=0 + subCode=DUPLICATE_ACTION + data=上次结果`，这是**成功**语义，必须渲染成"已完成 + 上次的结果" |

    **改法**：
    - 保留 `xiaoyun-action.js` 的渲染逻辑本体，改成 ES module（`export function renderAll / renderOptions / …`），去掉 `window.XiaoyunAction` 挂载。**不要删掉重写成新组件**——那 3 条铁律是踩过坑写下来的。
    - 外层包 `components/ai/XiaoyunCard.vue`，`props` 传 `proposals` / `clarifyOptions`，`emit` 确认事件；或用 `composables/useXiaoyunCard.js` 暴露 `renderAll`。
    - 它依赖 `api()` 的 **`raw` 模式 + `extraHeaders`**（见 2.③），迁移时先确认 `http.js` 已支持，否则卡片拿不到 `subCode`。
    - 验收铁律（照抄原注释）：**把网络断开，卡片依然能完整显示** —— 因为它从不发二次请求去补数据。

### 3.6 生产构建与切换

```json
"scripts": {
  "dev": "vite",
  "build": "vite build",
  "build:backend": "vite build --base=/app/ --outDir=../backend/src/main/resources/static/app",
  "preview": "vite preview",
  "lint": "eslint . --fix"
}
```

- 开发期：前端独立运行在 5173，`/api` 代理到 8080，**不改后端一行代码**。
- 验收通过后：`npm run build:backend`，访问 `http://localhost:8080/app/#/dashboard`；旧 `admin.html` 暂不删除，保留一个版本周期回滚。
- 稳定后再决定是否让 `/` 直接指向新应用（届时可改 history 模式 + 后端加 fallback 映射）。

**`--base=/app/` 硬规则**：dev 是 `/`、prod 是 `/app/`，任何写死的绝对路径（`/logo.png`、`src="/x.js"`）在 prod 下都会 404。所有资源引用一律走 `import` 或 `new URL('./x.png', import.meta.url)`。仅"不要手改 index.html"这一条不够，这条是运行时最容易翻车的地方。

---

## 4. 开发环境验证标准

### 启动与工程化

- [ ] `npm install` 无 ERESOLVE 冲突，`npm ci` 可复现安装
- [ ] `npm run dev` 无 error/warn 级报错，`http://localhost:5173` 可访问
- [ ] `npm run build` 成功，`dist` 产物无缺失资源警告
- [ ] `npm run lint` 0 error

### 功能一致性（逐路由验收，与旧页面对照）

- [ ] 未登录访问任意路由 → `/login`；`admin/admin123` 登录成功；`localStorage.token` 与旧页 key 一致
- [ ] 16 个模块全部可进入，标题与侧栏高亮正确
- [ ] 仪表盘 4 张统计卡 + 4 张图表数据与旧页一致
- [ ] 商品新增/编辑/删除/库存调整；订单发货（9 家物流 + 随机运单号）；退款同意/拒绝；风险扫描与已读；会员状态/角色；角色权限勾选保存；评论 AI 分析/草稿回复/回复；客服收发消息与交还 AI；分类、促销、支付退款、物流状态；AI 知识库检索与重建
- [ ] AI 助手问答返回正常（≥60s 长请求不超时）、历史与清空可用
- [ ] 低库存登录弹窗触发一次，跳转补货链接正确
- [ ] **小运卡片**：确认 → `running` 时按钮锁死（连点只发 1 个请求）；第二次确认显示"已完成 + 上次结果"而非报错；**断网后卡片仍能完整显示**（不依赖二次请求）

### 交互与运行时

- [ ] 修改任意 `.vue` 文件，HMR 1 秒内生效且页面状态不丢
- [ ] 浏览器控制台无 Vue warning、无未捕获 Promise、无 CORS 报错
- [ ] 网络面板所有请求均为 `localhost:5173/api/**`（经代理转发 8080）
- [ ] 权限矩阵：ADMIN 全可见；OPS 无角色/权限/知识库；CS 仅见客服/退款/评论相关
- [ ] 401 过期后自动跳登录且不出现刷新循环；403 只提示不登出
- [ ] 离开客服页后无残留轮询请求（Network 面板静默）

### 部署与回滚（P1.5 就要跑通，不要留到 P7）

- [ ] `npm run build:backend` 产物落在 `backend/src/main/resources/static/app/`
- [ ] `http://localhost:8080/app/#/dashboard` 与 `#/products` 可访问，刷新不白屏
- [ ] `--base=/app/` 下所有静态资源 200（Network 面板无 404）
- [ ] 新页面登录后，旧页 `http://localhost:8080/admin.html` 仍是登录态；反之亦然（同一个 `token` key，互不踢出）
- [ ] 回滚演练：删掉 `static/app/` 后旧页完全不受影响

---

## 5. 阶段划分与时间节点

### 5.1 开工前置条件（不满足不要进 P1）

1. **`git init` + 首次提交完成**——当前目录不是 git 仓库，P0 承诺的"可一键回滚"在完成这一步之前是不成立的。
2. **数据库种子快照**：`mysqldump` 导出一份当前数据。原因：旧页面 mtime 为 2026-10-22 16:12，数据天天在动；只靠"截图基线"逐页对照，第二天就因为新订单/新退款对不上，P6 会变成每天追移动靶子。
3. **旧页面冻结约定**：迁移期间 `static/admin.html` 只允许修后端引起的必要改动，功能新增一律先记入待办、迁移完成后统一补；或约定每周合并一次。否则会出现"迁完发现旧页又多了功能"。

### 5.2 阶段表

| 阶段 | 内容 | 交付物 | 验收标准 | 参考工期 |
|---|---|---|---|---|
| P0 基线准备 | `git init` + 首次提交 + `.gitignore`、建 `feature/vue-admin` 分支、梳理 16 模块/**48** 接口清单、旧页面全模块截图、`mysqldump` 种子数据 | 基线 commit + 接口清单 + 截图 + 种子 SQL | `git log` 有基线提交；`node -v` ≥20.19；后端 8080 可跑；清单覆盖全部 `api('/admin/...')` 调用 | 0.5 人日 |
| P1 脚手架 | Vite 工程、路由、Pinia、Element Plus、Axios 封装（含 raw 模式）、AdminLayout、LoginView、权限守卫 | 可登录的空壳后台 | `npm run dev` 无报错；登录/登出/守卫/菜单过滤全通 | 1 人日 |
| **P1.5 垂直切片（新增，关键）** | **只做「壳 + 路由 + 守卫 + 商品管理一个真实业务页」，立刻 `build:backend` 部署到 `/app/`** | 一个能部署的真页面 | 8080 `/app/#/dashboard` 与 `/app/#/products` 可访问；`--base=/app/` 下资源无 404；新旧页面共享 `localStorage.token` 互不踢登录；旧页仍在、可回滚 | 1 人日 |
| P2 公共层 | 主题 CSS 迁移与 Element Plus 覆盖、constants/format、3 个图表组件、AI 助手、**小运卡片**、低库存弹窗、未读角标 | 公共组件库 | 布局与旧页视觉一致；助手可问答；小运卡片四态正确；ADMIN 才见悬浮球 | 1.5 人日 |
| P3 核心业务 | 仪表盘、商品、库存、分类、订单、促销 | 6 个 view | 各页面 CRUD/筛选/弹窗操作与旧页等价 | 3–4 人日 |
| P4 其余业务 | 退款、风险、评论、客服、会员、支付、物流 | 7 个 view | 含客服 5 秒轮询与离开清理 | 3–4 人日 |
| P5 权限与 AI | 角色、权限、AI 知识库、营销文案面板 | 4 个 view + 1 组件 | RBAC 保存生效；知识库检索/重建正常 | 1.5–2 人日 |
| P6 联调回归 | 第 4 节全部验收项、权限矩阵、**回灌种子数据后逐页对照**、`npm run build`、lint | 验收报告 | 全部勾选通过 | 1.5–2 人日 |
| P7 上线切换 | `build:backend` 产物部署 `/app/`，旧页保留 | 可访问的生产版 | `8080/app/#/dashboard` 全功能可用，可一键回滚 | 0.5 人日 |

合计约 **13–17 人日**（单人）。P3/P4 可按模块并行，两人协作可压缩约 30%，落在 **10–13 人日**。

> **工期为什么比原估的 9–13 高**：原估基于"2660 行 JS + 85 接口"，实测是 2914 行 + 48 接口，但**多出了小运卡片这一整块（647 行、含状态机）**，且 P2 公共层要同时吃下 AI 助手 + 小运 + 图表 + 角标。P3/P4 每个 view 按 3–4 小时（含联调对照）更现实。

> **为什么插 P1.5**：原计划在 P7 才第一次验证部署，而真正的高风险点不是写第 16 个 view，而是"`--base=/app/` + hash 路由 + Spring Boot 静态目录 + 新旧共享 token + 回滚是否真的一键"。这些问题若在 P7 才暴露，返工面是整个工程。商品管理是最合适的切片——同时覆盖表格/表单/弹窗/筛选/分页，改造模式可复用到 80% 的页面。

---

## 6. 常见问题规避与排查方向

| 风险 | 现象 | 前置规避 / 排查 |
|---|---|---|
| Node 版本过低 | `Vite requires Node.js version 20.19+` | 本机已是 24.21.0，无需装 22；`package.json engines` + `.nvmrc` 双保险 |
| **PowerShell 5.1 不支持 `&&`** | `标记"&&"不是此版本中的有效语句分隔符` | 命令逐行执行或用 `;`；或改用 PowerShell 7 / Git Bash（本机默认 shell 是 PS5） |
| npm 脚本被执行策略拦截 | `无法加载文件 ...\npm.ps1` | `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`，或直接 `npm.cmd run dev` |
| 后端未启动 | 代理报 `ECONNREFUSED 127.0.0.1:8080`、所有接口 500 | 先跑 `BackendApplication`，`curl http://localhost:8080/api/ping` 验证 |
| 直连 8080 触发跨域 | 控制台 CORS 报错 | 一律走 `/api` 相对路径 + Vite 代理，不在代码里写 `http://localhost:8080` |
| 401 死循环刷新 | 登录页不断重载 | 响应拦截里判断 `route.path !== '/login'` 再跳转；登录接口不走 401 登出分支 |
| Element Plus 样式错乱 | 自定义 `.btn/table` 覆盖组件 | 引入顺序 `element-plus.css` → `admin-theme.css` → `element-overrides.css`；全局选择器限定在 `.admin-app` 下 |
| 组件英文文案 | 分页/日期控件显示英文 | `app.use(ElementPlus, { locale: zhCn })` |
| Axios 默认超时截断 AI | AI 回复 502/超时 | `timeout: 90000`，AI 请求单独放宽到 120s |
| D3 与 Vue 抢 DOM | 图表空白/重复渲染 | D3 只在 `onMounted` 操作 `ref` 容器，`watch` 重绘前先 `select(...).selectAll('*').remove()`；禁止对同一节点用 `v-html` |
| 生产 hash 路由 404 | 刷新子路由白屏 | 用 `createWebHashHistory`；若改 history 模式，必须给后端加 SPA fallback |
| 构建产物路径错 | `/app/` 下静态资源 404 | `vite build --base=/app/`，不要手改 index.html |
| 新旧页面登录态不通 | 新页面跳登录 | token/user 的 localStorage key 必须保持 `token` / `user` |
| 大文件一次性重写 | 功能大面积回归、无法定位 | 严格按 P2–P5 模块化提交，每完成一个 view 即对照旧页验收；整页保留可回滚 |
| 轮询泄漏 | 切页后仍在发请求、内存增长 | 所有 `setInterval` 必须在 `onUnmounted`/`onBeforeRouteLeave` 清理，统一用 `usePolling` |
| 依赖冲突 | `ERESOLVE unable to resolve dependency tree` | 用 `npm ci`；新增依赖先 `npm view <pkg> peerDependencies` 核对 Vue 版本；不手工改 lock 文件 |
| 中文字体/乱码 | 中文显示异常 | 所有文件 UTF-8；去掉 Google Fonts `@import`，使用系统字体栈 |
| **小运卡片被"顺手重构"** | 连点重复执行、第二次确认报错、卡片文字自己算 | 铁律 1/2/3 见 3.5 第 10 类；验收必须过"断网后卡片仍完整显示" |
| **`--base=/app/` 绝对路径** | prod 下图片/字体 404 | 资源一律 `import` 或 `new URL(..., import.meta.url)`，见 3.6 |
| **改造期间旧页仍在改** | 迁完发现旧页多了功能 | 走 5.1 第 3 条冻结约定 |
| **数据在动导致对照失真** | P6 每天对不上图表/列表 | P0 导 `mysqldump` 种子，P6 回灌后再逐页对照 |
| **没有 git 就开工** | 出问题无法回滚 | 2.0 步骤先 `git init` + 基线提交 |
