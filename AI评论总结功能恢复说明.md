# AI 评论总结功能恢复说明（后端代理方案）

执行日期：2026-09-04

---

## 一、背景：功能为什么"不见了"

上一轮「移除后端代码」任务中，商品详情页的 AI 评论总结被从「调用 DeepSeek 远程 AI 生成总结」
**降级**成了「本地统计摘要」：

- 删除了 `app/src/main/java/com/example/myapplication/api/AiSummaryHelper.java`（DeepSeek 远程客户端）
- 删除了 `gradle.properties` 中的明文 `DEEPSEEK_API_KEY`
- 删除了 `app/build.gradle.kts` 中的 `buildConfigField("String", "DEEPSEEK_API_KEY", ...)`
- `ProductDetailsActivity.triggerAiSummary()` 被改写为直接展示本地评分统计

按钮、界面、触发逻辑都还在，只是"总结"不再由 AI 生成。

## 二、本次恢复：走后端代理

按你的选择，采用**后端代理**方式恢复真正的 AI 总结——AI 能力由后端承载，DeepSeek API Key
藏在服务端，前端只调用本地后端接口，不再在前端暴露 Key。

```
Android 前端 (ProductDetailsActivity)
        │  POST /api/ai/summary  (JSON: 商品名 + 评论文本列表)
        ▼
Spring Boot 后端 (backend/)
        │  Java HttpClient 调用 DeepSeek Chat Completions API
        ▼
DeepSeek 服务  →  返回中文总结
```

## 三、改动文件清单

### 后端（backend/，Spring Boot）

| 文件 | 改动 |
|---|---|
| `pom.xml` | 新增 `spring-boot-starter-web` 依赖（提供 REST + Jackson） |
| `src/main/resources/application.properties` | 新增 DeepSeek 配置项（url / key / model） |
| `.../dto/AiSummaryRequest.java` | 新增：请求体 `{productName, reviews}` |
| `.../service/AiSummaryService.java` | 新增：拼 Prompt + 调 DeepSeek，返回总结文本 |
| `.../controller/AiSummaryController.java` | 新增：`POST /api/ai/summary` 接口 |

### 前端（MyApplication5/）

| 文件 | 改动 |
|---|---|
| `activity/ProductDetailsActivity.java` | `triggerAiSummary()` 改为异步调后端接口；新增 `requestAiSummary()`；失败时降级为本地统计摘要；新增相关 import 与 `AI_SUMMARY_URL` 常量 |
| `AndroidManifest.xml` | `application` 增加 `android:usesCleartextTraffic="true"`（允许本地明文 HTTP） |

## 四、接口协议

**POST** `http://<后端地址>:8080/api/ai/summary`

请求体：
```json
{
  "productName": "某商品名称",
  "reviews": ["评论内容1", "评论内容2", "..."]
}
```

成功响应：
```json
{ "summary": "AI 生成的中文总结文本" }
```

失败响应（HTTP 500）：
```json
{ "error": "错误描述" }
```

## 五、使用步骤

### 1. 配置 DeepSeek API Key

在 `backend/src/main/resources/application.properties` 中，或通过环境变量注入：

```properties
# 方式一：环境变量（推荐）
DEEPSEEK_API_KEY=sk-你的新key

# 方式二：直接写进配置文件（仅本地调试，不要提交）
deepseek.api.key=sk-你的新key
```

> ⚠️ 之前 `gradle.properties` 里的旧 Key 已在「后端代码移除报告」中明文暴露过，
> 建议去 DeepSeek 后台生成一个**新 Key**，避免继续使用已泄露的旧 Key。

### 2. 启动后端

```bash
cd backend
bash ./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

默认监听 `8080` 端口。

### 3. 配置前端后端地址

`ProductDetailsActivity.java` 中的常量（默认已配好）：

```java
private static final String AI_SUMMARY_URL = "http://10.0.2.2:8080/api/ai/summary";
```

- **Android 模拟器**：`10.0.2.2` 指向宿主机，无需修改。
- **真机**：改为电脑的局域网 IP，例如 `http://192.168.1.100:8080/api/ai/summary`，
  并确保手机与电脑在同一网络、后端防火墙放行 8080。

### 4. 运行 APP

进入任意商品详情页 → 点击「AI 总结」按钮 → 加载后展示 AI 生成的中文总结。
若后端未启动或调用失败，会自动降级为本地统计摘要，不影响使用。

## 六、注意事项

1. **Key 安全**：Key 仅存在于后端，前端不再持有；生产环境务必用环境变量/密钥管理注入，切勿提交到仓库。
2. **明文 HTTP**：`usesCleartextTraffic="true"` 仅为本地 Demo 方便；上线应改用 HTTPS 并收敛该配置。
3. **网络依赖**：AI 总结需要后端可达 DeepSeek 服务；离线或 Key 无效时会走本地降级。
4. **降级逻辑**：`buildLocalSummary()`（本地评分统计）仍保留，作为 AI 不可用时的兜底。

---

## 七、后续验证与调整（2026-09-04）

1. **模型名修正**：DeepSeek 实测返回 400，正确模型名是 `deepseek-v4-pro`（全小写、大小写敏感）。
   官方支持：`deepseek-v4-pro` / `deepseek-v4-flash` / `deepseek-v4-flash-vision-exp`。
   已在 `application.properties` 改好并重测通过。

   > ⚠️ **2026-10-08 更新（本条已过时，以上仅为当日历史记录）**：DeepSeek 的模型目录后来变更过两次
   > （`deepseek-v4-pro` → `deepseek-v4-flash` → 现名 `deepseek-flash`），**模型名不可凭记忆沿用**。
   > 当前权威列表以 `GET https://api.deepseek.com/v1/models` 为准，实测只有两个：
   >
   > | 模型名 | 对应产品名 | 模态 |
   > |---|---|---|
   > | `deepseek-flash` | DeepSeek-V4.1-Flash | 文本 + 图片 |
   > | `deepseek-v4-pro` | DeepSeek-V4-Pro | 仅文本 |
   >
   > 全站（6 个 AI 入口）现统一使用 **`deepseek-flash`（DeepSeek-V4.1-Flash）**，
   > 由 `deepseek.model` 一处决定，见 `AI架构设计说明.md` §模型配置。
2. **新增 `/api/chat` 接口**：通用对话接口（`POST /api/chat`）+ 健康检查（`GET /api/ping`）。
   公共 HTTP 调用抽取到 `client/DeepSeekClient`，总结 / 对话共用。
3. **端到端实测通过**：后端实际启动后，`/api/ping`、`/api/chat`、`/api/ai/summary` 均成功
   调用 DeepSeek 返回中文结果（key、地址、模型名均有效）。
4. **前端改为手动触发**：移除了打开商品页时的自动 AI 总结调用，改为仅点击「AI 总结」按钮时才
   调用 DeepSeek，避免每次进页面都消耗额度；前端读取超时调整为 60s，与后端一致。

报告完成。
