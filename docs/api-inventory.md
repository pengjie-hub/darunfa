# admin.html 接口清单（2026-10-23 基线）

来源：`backend/src/main/resources/static/admin.html`（2961 行）中 **89 处** `api('...')` 调用，按 **{path, method}** 去重后共 **69 个接口**。
同一路径多 HTTP 方法拆为独立行（计划里"48 条"是合并同路径不同方法的口径）。

| # | Method | Path | 调用位置（admin.html 行号） | 功能 |
|---|---|---|---|---|
| 1 | GET | `/auth/me` | 117 | 登录后获取当前用户信息（role + permissions） |
| 2 | GET | `/admin/dashboard` | 548 | 仪表盘统计 |
| 3 | GET | `/admin/dashboard/charts` | 549 | 仪表盘图表数据 |
| 4 | GET | `/admin/categories` | 745, 2014 | 分类列表 |
| 5 | POST | `/admin/categories` | 2063 | 新增分类 |
| 6 | PUT | `/admin/categories/{id}` | 2062 | 编辑分类 |
| 7 | DELETE | `/admin/categories/{id}` | 2040 | 删除分类 |
| 8 | GET | `/admin/products` | 746, 1109 | 商品列表 |
| 9 | GET | `/admin/products/low-stock` | 1108, 2639 | 低库存商品 |
| 10 | POST | `/admin/products` | 844 | 新增商品 |
| 11 | PUT | `/admin/products/{id}` | 843 | 编辑商品 |
| 12 | DELETE | `/admin/products/{id}` | 784 | 删除商品 |
| 13 | PUT | `/admin/products/{id}/stock` | 872 | 调整库存 |
| 14 | GET | `/admin/orders` | 910, 935, 955, 967 | 订单列表（可带 ?status=） |
| 15 | PUT | `/admin/orders/{id}/ship` | 990 | 订单发货 |
| 16 | POST | `/admin/risk/orders/{id}/scan` | 953 | 单订单风险扫描 |
| 17 | POST | `/admin/risk/orders/batch-scan` | 965, 1480 | 批量风险扫描（?limit=） |
| 18 | GET | `/admin/risk/summary` | 551, 1406, 1418, 1522, 1533 | 风险概览 |
| 19 | GET | `/admin/risk/scores` | 1451, 1472, 1483, 1509, 1524, 1544, 1593 | 风险评分列表（可带 ?level=） |
| 20 | PUT | `/admin/risk/scores/{id}/read` | 1576, 1590 | 标记单条已读 |
| 21 | PUT | `/admin/risk/scores/read-all` | 1504 | 全部标记已读 |
| 22 | GET | `/admin/refunds` | 550, 1055 | 退款列表（可带 ?status=） |
| 23 | GET | `/admin/refunds/pending-count` | 1031 | 待处理退款数量 |
| 24 | POST | `/admin/refunds/{id}/approve` | 1082 | 同意退款 |
| 25 | POST | `/admin/refunds/{id}/reject` | 1098 | 拒绝退款 |
| 26 | GET | `/admin/users` | 1127 | 会员列表 |
| 27 | PUT | `/admin/users/{id}/status` | 1157 | 启用/禁用用户 |
| 28 | PUT | `/admin/users/{id}/role` | 1187 | 修改用户角色 |
| 29 | GET | `/admin/rbac/roles` | 1199 | 角色列表 |
| 30 | POST | `/admin/rbac/roles` | 1279 | 新增角色 |
| 31 | PUT | `/admin/rbac/roles/{id}` | 1278 | 编辑角色 |
| 32 | DELETE | `/admin/rbac/roles/{id}` | 1241 | 删除角色 |
| 33 | GET | `/admin/rbac/roles/{id}/permissions` | 1204, 1287 | 角色已分配权限 |
| 34 | PUT | `/admin/rbac/roles/{id}/permissions` | 1314 | 保存角色权限分配 |
| 35 | GET | `/admin/rbac/permissions` | 1200, 1326 | 权限码列表 |
| 36 | POST | `/admin/rbac/permissions` | 1393 | 新增权限 |
| 37 | PUT | `/admin/rbac/permissions/{id}` | 1392 | 编辑权限 |
| 38 | DELETE | `/admin/rbac/permissions/{id}` | 1355 | 删除权限 |
| 39 | GET | `/admin/reviews` | 1632 | 评论列表 |
| 40 | GET | `/admin/reviews/replied-ids` | 1634 | 已回复的评论 ID 集合 |
| 41 | GET | `/admin/reviews/{id}/replies` | 1782 | 评论的回复列表 |
| 42 | POST | `/admin/reviews/{id}/reply` | 1817 | 人工回复评论 |
| 43 | DELETE | `/admin/reviews/{id}` | 1824 | 删除评论 |
| 44 | POST | `/admin/ai/reviews/{id}/analyze` | 1713 | AI 分析单条评论 |
| 45 | POST | `/admin/ai/reviews/batch-analyze` | 1722 | AI 批量分析评论 |
| 46 | POST | `/admin/ai/reviews/{id}/draft-reply` | 1731 | AI 生成回复草稿 |
| 47 | GET | `/admin/cs/stats` | 1842 | 客服会话统计 |
| 48 | GET | `/admin/cs/conversations` | 1885 | 客户会话列表 |
| 49 | GET | `/admin/cs/conversations/{id}/messages` | 1911 | 会话消息历史 |
| 50 | POST | `/admin/cs/conversations/{id}/reply` | 1988 | 人工客服回复客户 |
| 51 | POST | `/admin/cs/conversations/{id}/handback` | 2004 | 交还 AI 自动应答 |
| 52 | GET | `/admin/promotions` | 2070 | 促销活动列表 |
| 53 | POST | `/admin/promotions` | 2150 | 新增促销 |
| 54 | PUT | `/admin/promotions/{id}` | 2149 | 编辑促销 |
| 55 | DELETE | `/admin/promotions/{id}` | 2089 | 删除促销 |
| 56 | GET | `/admin/payments` | 2175 | 支付流水列表 |
| 57 | POST | `/admin/payments/refund/{id}` | 2186 | 退款 |
| 58 | GET | `/admin/shipments` | 2191 | 物流列表 |
| 59 | PUT | `/admin/shipments/{id}/status` | 2204 | 更新物流状态 |
| 60 | GET | `/admin/ai/knowledge/status` | 2253 | AI 知识库状态 |
| 61 | GET | `/admin/ai/knowledge/source` | 2355 | 知识库源文本 |
| 62 | POST | `/admin/ai/knowledge/rebuild` | 2343 | 重建知识库 |
| 63 | GET | `/admin/ai/knowledge/search` | 2378 | 知识库检索（?scope=） |
| 64 | GET | `/admin/ai/marketing/scenes` | 2493 | AI 营销文案场景 |
| 65 | POST | `/admin/ai/marketing/copy` | 2567 | AI 生成营销文案 |
| 66 | POST | `/admin/ai/assistant` | 2778 | AI 运营助手问答 |
| 67 | GET | `/admin/ai/history` | 2797 | AI 助手历史记录 |
| 68 | GET | `/admin/ai/actions/pending` | 2830 | 待执行的 AI 提案（小运卡片数据） |
| 69 | DELETE | `/admin/ai/memory` | 2902 | 清空 AI 助手记忆 |

## 接口调用分布（按模块）

| 模块 | 接口数 | 行号范围 |
|---|---|---|
| auth/me | 1 | 117 |
| 仪表盘 | 4 | 548-551 |
| 商品/库存 | 8 | 745-872, 1108-1109, 2639 |
| 订单/发货 | 3 | 910-990 |
| 风险 | 5 | 953-1593 |
| 退款 | 4 | 550-1098 |
| 会员 | 3 | 1127-1187 |
| RBAC 角色 | 6 | 1199-1287 |
| RBAC 权限 | 4 | 1200-1393 |
| 评论 | 9 | 1632-1731, 1782-1824 |
| 客服 | 5 | 1842-2004 |
| 分类 | 4 | 745, 2014-2063 |
| 促销 | 4 | 2070-2150 |
| 支付 | 2 | 2175-2186 |
| 物流 | 2 | 2191-2204 |
| AI 知识库 | 4 | 2253-2378 |
| AI 营销文案 | 2 | 2493-2567 |
| AI 运营助手（含小运卡片） | 4 | 2778-2902 |
| **合计** | **69** | |

## 特殊约束接口

| 特征 | 接口 | 说明 |
|---|---|---|
| 需 raw 模式返回 subCode | `/admin/ai/actions/pending` 及其后续执行（通过 `X-Proposal-Id` + `Idempotency-Key` 请求头确认） | 小运卡片四态状态机 + 幂等键，`DUPLICATE_ACTION` 是成功语义 |
| 需 extraHeaders（幂等键 / 提案 ID） | AdminController 中多处业务接口会从 header 读 `Idempotency-Key`、`X-Proposal-Id`、`X-Operator-Id`、`X-Operator-Role` | 前端请求层需透传这些头，且跳过空值（admin.html:171-177 的处理逻辑必须保留） |
| GET 带查询参数 | `/admin/orders?status=`、`/admin/refunds?status=`、`/admin/risk/scores?level=`、`/admin/risk/orders/batch-scan?limit=`、`/admin/ai/knowledge/search?scope=` | 前端拼 URL 或用 axios 的 params |
| 60s+ 长耗时 | `/admin/ai/assistant`、`/admin/ai/knowledge/rebuild`、`/admin/risk/orders/batch-scan`、`/admin/ai/reviews/batch-analyze`、`/admin/ai/marketing/copy` | Axios timeout 必须 ≥ 90000ms，否则会被截断 |
| 轮询驱动 | 客服页 `/admin/cs/conversations` + `/admin/cs/conversations/{id}/messages` 每 5s | 离开页面时必须清理定时器 |
