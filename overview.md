# 大润发电商后台管理系统 - 工作概览

> 最近更新：2026-09-07 19:43 — 完成「评论情感分析与智能回复」（P0 功能 #3）。

---

## 当前快照

- 后端：Spring Boot 4 + JPA + MySQL + JWT，运行在 `localhost:8080`，已具备商品/订单/库存/会员/促销/支付/物流/评论/AI 总结/AI 助手/AI 评论分析 等 14 个业务模块
- 后台：`/admin.html` 单文件 SPA，覆盖 10 个管理 tab + AI 运营助手悬浮对话框 + 评论管理 AI 增强
- App：仿大润发 Android（`MyApplication5`），已与后端打通商品/购物车/订单/评论/AI 总结
- 数据库：MySQL `darunfa`（`root/123456`），自动建表（ddl-auto=update）

## 最近几次重要任务

### AI 运营问答助手（2026-09-07 19:10）
- 后端：`DeepSeekClient` 扩展 Function Calling；新增 `service/ai/` 包（`AiToolFunction`/`AiToolDefinitions`/`AiToolsRegistry`/`AiChatService`），注册 8 个查询工具；`AiAssistantController`（`POST /api/admin/ai/assistant`，`@RequireAdmin`）
- 前端：`admin.html` 右下角悬浮助手 + 红色主题对话框；含 6 个快速提问按钮、清空、Enter 发消息

### 评论情感分析与智能回复（2026-09-07 19:43）
- 后端：`AiReviewService.analyze / batchAnalyze / draftReply`，`AiReviewController`（`POST /api/admin/ai/reviews/{id}/analyze | /batch-analyze | /draft-reply`）；`Review` 实体加 `ai_sentiment / ai_urgency / ai_reason / ai_analyzed_at` 列；prompt 输出 JSON 正则解析
- 前端：评论管理模块重写，工具栏加情感筛选 + ⚡ 批量 AI 分析；表格新增情感/紧急度徽章 + AI 理由 + 4 操作按钮（🧠/✏️/👁/删除）；详情弹窗 + 回复草稿弹窗（含 📋 复制）
- 情感 4 档：正面/中性/负面/投诉；紧急度 3 档：普通/需关注/紧急
- 安全：system prompt 收紧「不承诺赔偿/免单/法律责任」

### 仪表盘数据可视化（2026-09-07 18:30）
- 后端新增 `GET /api/admin/dashboard/charts`：近 7 天销售趋势（柱+折线）、订单/分类/支付三组分布
- 前端纯 SVG 绘制 4 个图表，无外部依赖；适配离线环境

### 物流公司下拉 + 随机运单号（2026-09-07 18:50）
- `admin.html` 订单发货改用 9 家公司下拉，运单号按公司前缀自动随机生成

### 低库存自动预警弹窗（2026-09-07 18:55）
- 登录后自动检测低库存商品，弹窗显示商品/库存/差额，提供「前往补货 / 稍后处理」

### AI 功能增强方案（2026-09-07 18:55）
- 交付 `.workbuddy/artifacts/AI功能增强方案.md`，列出 8 个推荐 AI 功能（按 P0/P1/P2 优先级）

## 启动 / 重启

1. IntelliJ IDEA 打开 `backend/`，Run `BackendApplication`（端口 8080）
2. 确保 MySQL 已启动且 `darunfa` 库可访问（`ddl-auto=update` 自动加 `reviews` 表的 4 个 AI 列）
3. 配置 `DEEPSEEK_API_KEY` 环境变量（AI 助手 + 评论分析均需要）
4. 浏览器访问 `http://localhost:8080/` → 登录页 → `admin/admin123`
5. 入口：`/admin.html`（已登录自动跳转）

## 关键设计要点

- **App 与后台共用一套后端**：AdminController 与用户业务 Controller 严格分离，但表结构同一份
- **数据一致性**：促销/支付/物流在 OrderService 一个事务里关联写订单
- **AI 安全**：DeepSeek 客户端无敏感凭据直传；system prompt 收紧工具白名单；评论回复合规收紧不承诺赔偿/法律责任
- **离线友好**：`mvn -o compile` 即可验证；前端零外部依赖（图表纯 SVG、登录纯 HTML/CSS/JS、AI UI 单文件）

## 待办（依用户优先级可继续扩展）

- AI: 营销文案生成（P0 #2，2 天）—— AI 助手 + 评论分析已具备能力基础
- AI: 商品销售预测与智能补货（P2，时序预测，2 周）
- 业务: 优惠券模块（与 AI 会员分群联动使用）
- 前端: 仪表盘图表交互（悬浮 tooltip、按日期范围筛选）
- AI: 评论 AI 理由可点击展开/折叠（详情弹窗已支持，整行直接展开）


## 故障修复：AI 助手 / 评论页 / 中文乱码（2026-09-08 08:30）

### 1. AI 运营助手“请求失败：未知错误”
- 根因：`api()` 已解包返回 `data`，但 `sendAiMsg` 等仍读 `json.code/json.data` → 永远走 else。
- 修复：AI 助手发送逻辑 `/api/admin/ai/assistant` 改用 `data.answer`。

### 2. 评论管理页“object is not iterable”
- 根因：`SENTIMENT_BADGE/URGENCY_BADGE` 写成对象，与 `statusBadge()` 数组解构不匹配。
- 修复：改为数组 `['正面','green']` 形式。

### 3. 多处中文乱码
- 根因：pom 未声明 UTF-8 编译编码 + IDEA encodings.xml 只设了 main/java。
- 修复：pom 加 build.sourceEncoding=UTF-8；encodings.xml 补全 resources/项目根；application.properties 加 server.servlet.encoding（charset=UTF-8/enabled/force）。

### 验证
- 后端 `mvn -o -q -DskipTests compile` 通过（EXIT=0）。
- admin.html 内嵌 `<script>` 通过 Node 语法校验（52318 字符）。
- 编译后 class 内中文为合法 UTF-8，确认构建链路编码正确。
- src 与 target 的 admin.html / application.properties 已一致。

### 影响文件
- `backend/src/main/resources/static/admin.html`（AI 助手+评论页解包逻辑、BADGE 常量）
- `backend/pom.xml`（UTF-8 编译编码）
- `backend/.idea/encodings.xml`（项目编码）
- `backend/src/main/resources/application.properties`（HTTP 响应 UTF-8）

> 注意：若 IDEA 读取 application.properties 注释仍显示乱码，属打开编码显示问题；Settings→Editor→File Encodings 把 Project Encoding 设为 UTF-8 即可（文件本身是合法 UTF-8）。
