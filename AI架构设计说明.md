# 大润发风格电商管理系统 — AI 架构设计说明

> 本文说明本次把「手写 DeepSeek HTTP 调用」重构为「Spring Boot 4 + LangChain4j 声明式 AI 框架」的分层设计、包结构、组件职责、交互流程与配置方式，并附 RAG 向量检索层与实测验证结论。
>
> 后端：`backend/`（Spring Boot 4.1.1 + Java 17 + Spring Data JPA + MySQL）　AI 框架：LangChain4j 1.22.0

---

## 0. 一句话总览

| 维度 | 改造前（手写） | 改造后（LangChain4j） |
|---|---|---|
| 模型调用 | 手拼 JSON → 手写 HTTP POST → 手解析响应体 | `interface` 上写注解，框架生成实现 |
| 工具调用 | 手写 `for (turn<5)` 主循环，手拼 `tool_call_id` 回传 | 框架自带 Function Calling 主循环 |
| 工具定义 | 约 **1300 行**手写 JSON Schema（`AiToolDefinitions` + `UserAiToolsRegistry`） | `@Tool` / `@P` 注解方法，Schema 由反射生成 |
| 提示词 | 散落在 6 个 Service 里的字符串拼接 | 集中到 `AiPrompts`，Mustache 变量占位 |
| 会话记忆 | 手写 `loadRecentHistory(userId, 5)` + `take = turns*2` | `ChatMemoryProvider` + `ChatMemoryStore` 框架托管 |
| 工具身份校验 | 手动 `executeWithUser(userId, argsJson)` 传参 | `@ToolMemoryId`，模型看不到也伪造不了 userId |
| 知识问答 | 无（模型只能凭印象答平台规则） | RAG 向量检索，按语义召回平台规则原文 |
| 新增 AI 能力的成本 | 写一个 Service + 若干 Schema + 一轮主循环 | **写一个带注解的接口方法** |

规模：后端 Java 源文件 **176 个**，其中 AI 相关 **25 个**（`com.example.backend.ai` 包）。

---

## 1. 重构目标与三项关键决策

| 决策点 | 选择 | 理由 |
|---|---|---|
| 交付形态 | **直接替换旧实现，删掉手写部分** | 保留双实现会长期存在「两条路走同一个模型」的维护负担；接口契约不变即可保证前端零改动 |
| 模型接入 | **继续用 DeepSeek** | DeepSeek 提供 OpenAI 兼容的 `/v1/chat/completions`，直接复用 `OpenAiChatModel`，只改 `baseUrl / apiKey / modelName` 三项 |
| 向量检索 | **上 RAG** | 平台规则（退款流程、发货时效、优惠叠加）不在任何数据表里，工具查不到；不注入资料时模型会编 |

**接口契约零改动**：改造前后所有 AI 端点的路径、请求体、响应体完全一致，因此 `admin.html` 后台页面与 Android App **一行都不用改**。

---

## 2. 技术栈与依赖清单

### 2.1 Spring Boot 4 的 starter 命名（易踩）

LangChain4j 为 Spring Boot 4 单独发布了并行系列的 starter，**名字里多一个 `boot4`**，与 Boot 3 版本同步发布、共用同一版本号：

| Spring Boot 版本 | starter 命名 |
|---|---|
| Boot 3 | `langchain4j-{name}-spring-boot-starter` |
| **Boot 4** | `langchain4j-{name}-spring-boot4-starter` |

本项目的 Boot 版本是 **4.1.1**，因此只能用后者。

### 2.2 依赖清单

```xml
<properties>
    <langchain4j.version>1.22.0</langchain4j.version>
</properties>

<dependencyManagement>
    <dependencies>
        <!-- 一个 BOM 同时编排 stable 与 beta 两组构件 -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-bom</artifactId>
            <version>${langchain4j.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- ① 声明式 AI 服务：扫描 @AiService，装配 @SystemMessage/@UserMessage/@Tool/@MemoryId -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-spring-boot4-starter</artifactId>
    </dependency>

    <!-- ② OpenAI 兼容协议模型（DeepSeek 走 /v1 兼容端点） -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-open-ai</artifactId>
    </dependency>

    <!-- ③ RAG 嵌入模型：BGE-small-zh-v1.5，中文语义，ONNX 本地推理，无需额外 API Key -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-embeddings-bge-small-zh-v15</artifactId>
    </dependency>
</dependencies>
```

**BOM 的双轨版本管理**：`langchain4j-bom` 内部用两个属性分别管理两组构件 ——

```xml
<langchain4j.stable.version>1.22.0</langchain4j.stable.version>
<langchain4j.beta.version>1.22.0-beta32</langchain4j.beta.version>
```

`langchain4j`（核心）、`langchain4j-core`、`langchain4j-open-ai` 属 stable 轨；`langchain4j-spring-boot4-starter`、`langchain4j-embeddings-*` 属 beta 轨。**导入 BOM 后三个依赖都不需要手写 `<version>`**，也不会出现版本错配。

**依赖体积**：`langchain4j-embeddings-bge-small-zh-v15` 携带 95MB 的 ONNX 权重 + 0.4MB tokenizer，另需 `onnxruntime` 93MB、`djl tokenizers` 17MB —— **首次下载约 160MB，之后完全本地离线**。这是「不依赖外部嵌入 API」的代价，换来的是零配额、零延迟、断网可用。

### 2.3 为什么模型 Bean 不用 starter 的自动装配

`langchain4j-open-ai-spring-boot4-starter` 能按属性自动装配一个 `ChatModel`，本项目**刻意不用**，理由有三：

1. 系统需要**两个温度档**的模型（严谨档 0.3 / 口语档 0.6），自动装配只会产生一个 Bean；
2. 模型属于「基础设施」，显式声明才能用 `@AiService(chatMemory = "...")` 做**按名精确装配**；
3. 避免与第三方 starter 的条件化自动装配产生 Bean 数量歧义。

---

## 3. 分层与包结构

```
com.example.backend
├── ai/                                   ← AI 能力全部收敛在此包内
│   ├── config/                           【配置层】基础设施 Bean 装配
│   │   ├── DeepSeekProperties.java           deepseek.* 配置绑定（含 API Key fail-fast）
│   │   ├── AiModelConfig.java                两个 ChatModel Bean（严谨档 / 口语档）
│   │   ├── AiMemoryProperties.java            ai.memory.* 配置绑定
│   │   └── AiMemoryConfig.java                ChatMemoryProvider Bean
│   ├── prompt/                           【提示词层】
│   │   └── AiPrompts.java                    8 份系统提示常量（Mustache 变量占位）
│   ├── assistant/                        【声明式 AI 服务层】只声明「输入什么、输出什么」
│   │   ├── AdminAssistant.java                小运（后台运营助手）· 严谨档 + 记忆 + RAG + 后台工具
│   │   ├── ShopAssistant.java                 小发（前台购物助手）· 口语档 + 记忆 + RAG + 前台工具
│   │   ├── CsAgent.java                       AI 在线客服 · 口语档 + RAG（刻意不挂记忆）
│   │   ├── ReviewAgent.java                   评论分析 / 草稿 / 总结 · 严谨档
│   │   ├── RiskAnalyst.java                   订单风控评分 · 严谨档
│   │   └── SimpleAssistant.java               最小接入样例（无记忆无工具）
│   ├── tools/                            【工具层】@Tool 注解方法 = 模型的「手」
│   │   ├── AdminDataTools.java                8 个后台数据工具
│   │   └── ShopTools.java                     6 个前台用户工具
│   ├── memory/                           【记忆层】
│   │   ├── JpaChatMemoryStore.java            ChatMemoryStore → ai_chat_messages 表
│   │   ├── JpaBackedChatMemory.java           请求内驻留 + 跨请求持久化的 ChatMemory
│   │   └── LongTermMemoryStore.java           长期记忆（偏好/上下文）→ ai_memories 表
│   ├── rag/                              【向量检索层】
│   │   ├── RagProperties.java                 ai.rag.* 配置绑定（两个门槛 + 词面加权参数）
│   │   ├── PlatformKnowledgeBase.java         平台规则知识文本（23 小节，标题行末尾声明受众域）
│   │   ├── RagScope.java                      受众域词汇表与 ops 标记约定
│   │   ├── RagConfig.java                     嵌入模型 / 向量库 / 两个分域检索器；retrieverFor(scope) 唯一构造入口
│   │   ├── HybridContentRetriever.java        分域过滤 + 向量分 + 词面加权 + 门槛 + 截尾
│   │   ├── LexicalTitleBooster.java           P3 词面打分（滤虚词的内容二元组 × 小节标题）
│   │   ├── IntentGuard.java                   P2 意图闸门规则（顾客侧「不可能有答案」的问法）
│   │   ├── GuardedContentRetriever.java       闸门装饰器（顾客侧检索器外层）
│   │   └── RagKnowledgeService.java           播种、重建、状态、分域检索测试
│   └── service/                          【编排层】业务语义 + 结果整形
│       ├── AdminAssistantService.java         小运编排（日期、记忆上下文、memoryKey）
│       ├── ShopAssistantService.java          小发编排（工具结果 → 前端 attachments 卡片）
│       ├── ReviewAiService.java               评论分析与草稿（含容错 JSON 解析）
│       ├── SummaryAiService.java              评论总结（剔除商家回复）
│       └── SimpleChatService.java             最简对话
├── controller/                           【接口层】仅做参数校验与响应包装
│   ├── AiAssistantController.java             /api/admin/ai/assistant|history|memory|memories
│   ├── AiKnowledgeController.java             /api/admin/ai/knowledge/status|search|rebuild|source
│   ├── AiReviewController.java                /api/admin/ai/reviews/**
│   ├── AiSummaryController.java               /api/ai/summary
│   ├── AssistantChatController.java           /api/assistant/chat|history
│   ├── ChatController.java                    /api/chat
│   └── CsController.java                      /api/cs/session/**
├── service/CsService.java                    在线客服编排（局部改造：DeepSeekClient → CsAgent）
└── service/RiskDetectionService.java         风控编排（局部改造：DeepSeekClient → RiskAnalyst）
```

### 分层依赖方向

```
        ┌───────────────────────────────────────────────┐
        │  Controller 层（HTTP 契约，零业务逻辑）          │
        └───────────────────────┬───────────────────────┘
                                │
        ┌───────────────────────▼───────────────────────┐
        │  Service 编排层（业务语义、上下文拼装、结果整形）  │
        └───────┬───────────────────────────┬───────────┘
                │                           │
   ┌────────────▼────────────┐   ┌──────────▼────────────┐
   │  Assistant 声明式 AI 层   │   │  Memory 记忆层         │
   │  （接口 + 注解，无实现）   │   │  （ChatMemory Store）  │
   └────────┬───────────┬────┘   └──────────┬────────────┘
            │           │                   │
   ┌────────▼───┐  ┌────▼──────┐  ┌─────────▼──────────┐
   │ Tools 工具层│  │ Prompt 层 │  │  RAG 向量检索层     │
   │ @Tool      │  │ 常量      │  │  嵌入/向量库/检索器  │
   └────────────┘  └───────────┘  └─────────┬──────────┘
                                            │
   ┌────────────────────────────────────────▼──────────┐
   │  Config 配置层（ChatModel / EmbeddingModel 等 Bean）│
   └───────────────────────────────────────────────────┘
                                │
                        MySQL（darunfa）
```

**依赖方向恒为「上层依赖下层」**，`assistant` 包只依赖 `config`/`prompt`/`rag` 的 Bean **名常量**，不依赖任何实现类 —— 这是它能保持「接口 + 注解」形态的前提。

---

## 4. 核心组件职责划分

### 4.1 模型配置层

**`DeepSeekProperties`** —— 绑定 `deepseek.*`，集中 `baseUrl / apiKey / model / temperature / chattyTemperature / maxTokens / timeoutSeconds / maxRetries / logRequests / logResponses`。

> ⚠️ **命名陷阱（本次实测踩到并修复）**：若前缀写成 `@ConfigurationProperties(prefix = "deepseek.api")` 而字段叫 `apiKey`，松绑定会先去掉前缀、剩余段是 `key`，与 accessor `apiKey` **匹配不上 → apiKey 恒为 null**，表现为启动时抛「API Key 未配置」。正确做法是**前缀只到 `deepseek`，属性写 `deepseek.api-key`**（点分隔的 `api-key` 才能绑定 `apiKey`）。

**`AiModelConfig`** —— 产出两个 `ChatModel` Bean，除温度外完全一致：

| Bean 名 | 常量 | 温度 | 用在哪些场景 |
|---|---|---|---|
| `aiChatModel` | `AiModelConfig.STRICT_MODEL` | 0.3 | 小运问答、评论分析、评论草稿、评论总结、风控评分 |
| `aiChattyChatModel` | `AiModelConfig.CHATTY_MODEL` | 0.6 | 小发购物助手、AI 在线客服 |

分档依据：**要求「输出可被程序解析」或「数字口径稳定」的场景必须低温**（JSON、枚举、金额），否则模型会发散、格式跑偏；**要求自然亲切的场景用中温**，但再高就会开始编商品和物流。

API Key 为空时构造 Bean 直接 `throw new IllegalStateException(...)` **fail-fast** —— 配置错误应该在启动瞬间暴露，而不是等到用户提问才 502。

### 4.2 提示词层 `AiPrompts`

8 份系统提示集中为 `public static final String` 常量，用 Java 文本块（`"""`）书写，变量用 Mustache `{{var}}` 占位：

| 常量 | 使用方 | 变量 | 要点 |
|---|---|---|---|
| `ADMIN_ASSISTANT` | 小运 | `{{today}}` `{{memory}}` | 10 条规则，含 GMV 口径、自然日口径、与仪表盘一致性、首次回复带日期 |
| `SHOP_ASSISTANT` | 小发 | `{{today}}` `{{greeting}}` `{{memory}}` `{{history}}` | 7 条规则，含「不编造商品/物流」 |
| `SHOP_GREETING_FIRST` / `_NORMAL` | 小发 | — | 首轮 / 非首轮的问候语片段 |
| `CS_AGENT` | AI 客服 | `{{today}}` `{{customer}}` `{{history}}` | 5 条要求，含降级话术与转人工时机 |
| `REVIEW_ANALYZE` | 评论分析 | — | 强制输出严格 JSON（sentiment/urgency/reason） |
| `REVIEW_DRAFT_REPLY` | 评论草稿 | — | 输出 JSON 数组 2 条 + 4 条合规要求 |
| `REVIEW_SUMMARY` | 评论总结 | — | 200 字以内 + 只总结用户评价、忽略商家回复 |
| `RISK_ANALYST` / `SIMPLE_CHAT` | 风控 / 最简对话 | — | — |

集中管理的好处是**提示词变成可评审的资产**：改口径只需改一处，且改动会过编译、过 code review。

### 4.3 声明式 AI 服务层

这是整个重构的**核心**：`assistant` 包里**没有一个实现类**，全是接口 + 注解。

```java
@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = AiModelConfig.STRICT_MODEL,
        chatMemoryProvider = AiMemoryConfig.MEMORY_PROVIDER,
        contentRetriever = RagConfig.CONSOLE_RETRIEVER,   // 全量：小运是唯一该看到后台口径的助手
        tools = {"adminDataTools"}
)
public interface AdminAssistant {

    @SystemMessage(AiPrompts.ADMIN_ASSISTANT)
    String chat(@MemoryId String memoryKey,
                @UserMessage String message,
                @V("today") String today,
                @V("memory") String memory);
}
```

**这一个接口替代了什么**：改造前「小运」是这样跑的 ——

```java
for (turn = 0; turn < 5; turn++) {
    手工拼 body → 手工 POST → 手工解析 choices[0].message.tool_calls
    → 手工 JSON.parse 参数 → 手工查注册表执行工具
    → 手工拼 role:"tool" + tool_call_id 回传 → 再来一轮
}
```

现在**整个 Function Calling 主循环由框架承担**：模型决定调哪些工具、调几次、拿到结果后怎么组织语言，全部交给 LangChain4j。

**为什么装配必须是 `EXPLICIT`**：容器里有两个 `ChatModel` 和两套工具。若用 `AUTOMATIC`，框架会把**所有**工具都塞进来 —— 小运就会看到「查我的购物车」这类前台工具，小发则可能去查全站用户统计。这既是能力错配，**也是权限风险**。因此逐一指名。

**六个助手的装配矩阵**：

| 助手 | 模型 | 记忆 | RAG（受众域） | 工具 |
|---|---|---|---|---|
| `AdminAssistant` 小运 | 严谨档 | ✅ | ✅ 全量（含后台口径） | `adminDataTools`（8 个） |
| `ShopAssistant` 小发 | 口语档 | ✅ | ✅ 顾客侧 + 意图闸门 | `shopTools`（6 个） |
| `CsAgent` AI 客服 | 口语档 | ❌ **刻意不挂** | ✅ 顾客侧 + 意图闸门 | ❌ |
| `ReviewAgent` 评论三件套 | 严谨档 | ❌ | ❌ | ❌ |
| `RiskAnalyst` 风控 | 严谨档 | ❌ | ❌ | ❌ |
| `SimpleAssistant` 样例 | 口语档 | ❌ | ❌ | ❌ |

> **为什么小运挂「全量」而小发/客服挂「顾客侧」**：GMV 怎么算、按天统计的自然日边界、客服接待量怎么统计、后台角色有哪几种权限 —— 这些正是运营每天要问的，所以小运是**唯一**该看到后台口径的助手，而且不挂意图闸门（后台问「今天有多少订单」是本职，拦掉反而坏事）。小发与 AI 客服对的是顾客，挂「顾客侧」不是「少给点资料」，而是**防止把内部口径当成对外答复说出去**。

> **`CsAgent` 为什么不挂记忆**：客服会话的权威历史是 `cs_messages` 表（后台客服要能查看、人工客服的消息也要算作上下文），而不是 AI 记忆表。因此由 `CsService` 从 `cs_messages` 读出最近 12 条拼成文本、通过 `{{history}}` 注入 —— 这样「AI 回复 / 人工客服回复」都能作为 assistant 轮次参与上下文，语气得以延续。
>
> 这也说明 `@AiService` 的装配是**按需组合**的：需要记忆就挂，不需要就不挂，而不是被迫接受一套固定配置。

> **`ShopAssistant` 为什么返回 `Result<String>` 而不是 `String`**：小发不只要回一句话，App 还要渲染「商品横滑卡 / 参数对比表 / 物流时间线 / 订单预览卡」。`Result` 是 LangChain4j 的「带副产品的返回值」—— `content()` 是自然语言答复，`toolExecutions()` 是本轮**真实发生过**的每一次工具调用（含工具名与原始 JSON 结果）。编排层据此映射成前端 `attachments`，于是卡片不再需要手写解析 `finish_reason == "tool_calls"` 的响应体，也不会出现「模型说调用过、其实没调用」的错位。

### 4.4 工具层

方法是普通 Spring Bean 的 `@Tool` 方法，Schema 由框架反射 `@Tool` / `@P` 生成。

**`AdminDataTools`（8 个）**

| 工具 | 作用 |
|---|---|
| `queryOrderStats(scope, date)` | 订单量与销售额，支持 TODAY / YESTERDAY / LAST_7_DAYS / LAST_30_DAYS / ALL / LAST_N_DAYS |
| `listRecentOrders(limit)` | 最近订单 |
| `searchProducts(keyword)` | 商品搜索 |
| `listLowStockProducts()` | 低库存商品 |
| `getProductStock(productId)` | 单品库存 |
| `getUserStats()` | 用户统计 |
| `getReviewStats()` | 评论统计 |
| `listActivePromotions()` | 进行中的活动 |

**`ShopTools`（6 个）**：`searchUserProducts` / `compareUserProducts` / `queryUserLogistics` / `listUserCart` / `previewUserOrder` / `rememberUserFact`。

**统一口径内聚于工具**：`queryOrderStats` 直接引用 `AdminService.EFFECTIVE_SALES_STATUSES`，与后台 `dashboard()`、`charts()` 用的是**同一份常量**，因此 AI 报出的销售额与仪表盘天然一致 —— 这是把口径固化在代码层，而不是写在提示词里靠模型自觉。

**身份透传用 `@ToolMemoryId` 而不是参数**：

```java
@Tool("查询当前用户的订单物流")
public String queryUserLogistics(@ToolMemoryId String memoryKey,
                                 @P("订单号") String orderNo) { ... }
```

`@ToolMemoryId` 标注的参数**不出现在给模型的 Schema 里** —— 模型既看不到 userId，也无从伪造。工具内部再经 `JpaChatMemoryStore.resolveUserId(memoryKey)` 取真实用户。相比之下，改造前把 userId 放在 argsJson 里手工透传，一旦模型被诱导就可能越权查询他人数据。

越权防护同样落在工具内：`loadCartItems` 命中他人购物车项时抛 `SecurityException`（整单拒绝），`queryUserLogistics` 校验 `order.getUserId().equals(userId)`。

### 4.5 记忆层

三个组件，两层记忆：

```
                    ┌─────────────────────────────────────────┐
   @MemoryId ──────▶ │ ChatMemoryProvider                      │
   "u:1"            │  → new JpaBackedChatMemory(id, window,   │
                    │                          store)         │
                    └───────────────┬─────────────────────────┘
                                    │
                    ┌───────────────▼─────────────────────────┐
                    │ JpaBackedChatMemory                     │
                    │  · 首次访问：从 store 一次性载入 + 裁剪窗口 │
                    │  · 请求内：纯内存列表（工具循环完整）      │
                    │  · 每次 add：把新增尾部落库（只追加）      │
                    └───────────────┬─────────────────────────┘
                                    │
                    ┌───────────────▼─────────────────────────┐
                    │ JpaChatMemoryStore → ai_chat_messages    │
                    │ （只存 user / assistant 的对话正文）       │
                    └─────────────────────────────────────────┘

   长期记忆：LongTermMemoryStore → ai_memories（key/value 偏好与上下文，注入 system 提示）
```

| 组件 | 职责 | 表 |
|---|---|---|
| `JpaChatMemoryStore` | `ChatMemoryStore` 实现：读历史、**追加**落库、清空 | `ai_chat_messages` |
| `JpaBackedChatMemory` | 请求内驻留 + 跨请求持久化（详见 §9.2） | — |
| `LongTermMemoryStore` | 长期记忆的读写与上下文渲染（`[记忆中的用户偏好与关键上下文]`） | `ai_memories` |

**复用既有表，不新建数据库对象**：`ai_chat_messages` 与 `ai_memories` 都是改造前就有的表，把它们分别实现成 `ChatMemoryStore` 与长期记忆载体，等于在不改表结构、不改接口契约的前提下把「手写读写」换成「框架托管」。两个 `/history` 接口（后台 + App）读的仍是同一份数据，因此**页面回放与框架记忆天然一致**。

**memoryId 约定**：`u:<userId>` = 登录用户（小运与小发共用），`anon:<uuid>` = 匿名会话（`resolveUserId` 返回 null → 不落库，不污染登录用户历史）。

**`updateMessages` 是「追加」而非「覆盖」**：先求「传入列表前缀」与「库中尾部」的最大重叠长度，只把真正新增的尾部落库。这保证了

- 窗口裁剪过的传入列表不会抹掉库里的老历史（`/history` 回放不缺段）；
- 一次请求内多次调用天然幂等，不会写重复行。

**三类消息刻意不落库**：`SystemMessage`（系统提示每次现算，落库等于存一份过期副本）、`ToolExecutionResultMessage`（推理中间过程，用户不该看到原始 JSON）、**空文本且带工具请求的 `AiMessage`**（那是「模型决定调工具」的中间回合，落库会在历史里混进一串「（未生成答复）」的假回复）。

### 4.6 向量检索层（RAG）

```
  PlatformKnowledgeBase（23 小节平台规则文本，标题行末尾用 <!-- ops --> 声明受众域）
            │  ① 先按 "## " 粗切：保证每个片段只讲一条规则（同时剥掉域标记）
            │  ② 再按 chunkSize/chunkOverlap 细切：保证片段长度可控
            │  ③ 每片打上 {section: 标题, scope: 顾客侧|运营侧} 元数据 + 标题写回正文
            ▼
  EmbeddingModel（BGE-small-zh-v1.5，本地 ONNX，512 维）
            │  ④ 批量向量化
            ▼
  InMemoryEmbeddingStore（进程内向量库，启动时播种：顾客侧 17 + 运营侧 6）
            │  ⑤ HybridContentRetriever：按 scope 过滤 → 向量分 + 词面加权 → 过门槛 → 截 top-k
            │     （顾客侧再套一层 GuardedContentRetriever 意图闸门）
            ▼
  小运           ← aiConsoleContentRetriever（全量：顾客规则 + 后台口径）
  小发 / AI 客服  ← aiCustomerContentRetriever（仅对外规则 + 意图闸门）
```

| 组件 | 职责 |
|---|---|
| `PlatformKnowledgeBase` | 平台规则正文；23 个 `##` 小节，覆盖 GMV 口径、发货时效、退款两步、七天无理由、运费、优惠叠加、领券、会员、评论、风控、库存、转人工、角色权限等 |
| `RagScope` | 受众域词汇表与 `<!-- ops -->` 标记约定 —— 分域检索的唯一口径 |
| `RagConfig` | 三个 Bean：`aiEmbeddingModel` / `aiEmbeddingStore` / 两个检索器；`retrieverFor(scope)` 是**唯一**的检索器构造入口（Bean 与管理页检索测试共用，保证「页面上看到的」＝「助手实际用的」） |
| `HybridContentRetriever` | 分域过滤 + 向量分 + 词面加权 + 门槛 + 截尾；把纯向量分写回片段元数据供管理页观测 |
| `LexicalTitleBooster` | P3 词面打分：内容二元组（滤掉虚词）与小节标题的字面重合度 |
| `IntentGuard` / `GuardedContentRetriever` | P2 意图闸门：命中的问法直接跳过检索 |
| `RagKnowledgeService` | 启动播种、手动重建、状态查询、分域检索测试 |
| `AiKnowledgeController` | `/status` `/search` `/rebuild` `/source` 四个管理端点 |

**为什么分成两个检索器（2026-10-09 实测推翻了旧结论）**：原先三个助手**共用同一个检索器**，而 23 个小节里混着 6 条后台运营规则。实测（BGE-small-zh-v1.5，20 个问句 × 3 个域，走真实的检索接口）得到：

| | 正例最高分带 | 负例最高分带 | 结论 |
|---|---|---|---|
| 分域前 · 全量池 | 0.769 ~ 0.900（最低「怎么转人工？」） | 0.636 ~ 0.777（最高「今天天气怎么样」） | **分数带交错**，不存在能兼顾的单一阈值 |
| 分域后 · 顾客池 | 0.780 ~ 0.935 | 0.632 ~ 0.724 | 空隙 +0.056，`0.75` 落在空隙里 |
| 分域后 · 全量池 | 业务口径类 ≥ 0.802 | 闲聊最高 0.777 | 空隙 +0.025，`0.79` 落在空隙里 |

「今天天气怎么样」那 0.777 究竟从哪来？来自「按天统计的时间范围怎么算」这条**后台**小节（两边都有「今天」）。它一旦离开顾客池，这条问句在顾客池只剩 0.689 —— **顾客池分数带之所以能拉开，正是因为混入的后台小节被移走了**。这也解释了为什么两个 `min-score` 必须各自校准、不能互相照搬：**候选池的成分不同，分数分布就不同**。

**词面加权（P3）为什么只按标题加权**：向量检索对口语短问句整体偏低（「怎么转人工？」只有 0.769，而字面几乎逐字命中标题）。补一路「问句实词与小节标题的字面重合度」（上限 +0.05）后它升到 0.819，**全部负例一分未动**。正文也加权则是有反例的：给正文加权后「今天天气怎么样」因为命中「按天统计…」正文里的「今天」而 +0.007，恰好越过全量池门槛 —— 同一个机制既奖励「生鲜」命中退货规则正文，也奖励「今天」命中统计口径正文，**加性权重无法区分这两者**。因此 `hybrid-body-boost` 默认 0，保留为可调参数。

**意图闸门（P2）的判据只有一条**：这句话在当前域里**不可能有答案**。答案是查库得来的数字（今天有多少订单、销售额是多少），或是只存在于运营侧的内部概念（后台角色权限、客服接待量、低库存补货）—— 那么召回任何顾客侧片段都只是往上下文里塞噪声，不如干净地交给工具查库或坦白不知道。它**不会**拦「生鲜能退吗？」这类规则问题：哪怕向量分只有 0.780、贴着门槛，也是知识库里真有答案的。用确定性正则而非再调一次模型判断意图：零额外延迟、零额外成本、行为可复现，而且每条规则都能在管理页里解释「为什么这句被拦了」。

**为什么用本地嵌入模型而不是调 API**：嵌入发生在启动时与每次提问时，调用量比对话大得多；本地 ONNX 换来零成本、零配额、断网可用。BGE-small-zh 是中文语义检索里同体积表现最好的小模型之一，知识库只有 23 个片段，用大模型属于杀鸡用牛刀。

**为什么向量库不落库**：知识库是**静态规则**，23 个片段、4546 字符，启动重新播种只需约 1 秒。为此引入向量表、建表迁移与清理流程，收益远小于维护成本。`InMemoryEmbeddingStore` 与「复用现有表」的既定原则一致。

**两级切分是关键**：只用固定长度硬切，会把「退款要走哪两步」和「七天无理由退货」拦腰拼进同一个片段 —— 检索命中后模型读到半条规则加另半条无关规则，比不给资料还糟。先按 `##` 切、再在小节内部按长度切，并给每片打上 `section` 元数据，检索结果就能告诉运营「这条答案来自哪条规则」。

**知识文本的写作约定**（直接决定检索命中率）：

1. **一篇一事**，且把小节标题写成**用户会问的话**（「发货要多久」而不是「物流模块说明」）—— 标题里的口语词既是最省事的同义扩展，也是词面加权（P3）的主要依据，标题写得像用户的话两路证据都会变强；
2. **结论前置**，让被切分后的片段即使掐头去尾也自洽；
3. **数值写全**（2 天、7 天、88 元），否则模型只能模糊表述；
4. **明确边界**（生鲜不支持无理由退货），否则模型会过度承诺；
5. **声明受众域**：只给后台运营看的内部口径（GMV 口径、按天统计、后台角色权限、客服接待量、低库存补货、评论风控机制），必须在标题行末尾写 `<!-- ops -->`。解析时标记会被剥掉（**不进正文、不影响向量**），但检索时会据此把该小节排除在顾客侧助手之外。**不写标记 = 默认顾客可见**，这是刻意的兜底方向：漏标最多让顾客多看到一条内部口径，而反过来把顾客该知道的规则标成内部，会让助手直接答不出正常问题。判断标准很简单 —— *这句话印在 App 的「帮助中心」里，会不会让顾客觉得奇怪？* 会，就是 ops。

### 4.7 编排服务层

Service 层承担「框架不负责的业务语义」，各服务职责单一：

| 服务 | 关键职责 |
|---|---|
| `AdminAssistantService` | 现算 `today` 文本 → 取长期记忆上下文 → 组装 memoryKey → 调 `AdminAssistant.chat`；`clearMemory` 同时清会话记忆与长期记忆 |
| `ShopAssistantService` | **`buildAttachments(List<ToolExecution>)`**：把工具名映射成卡片类型（`search_user_products→products`、`compare_user_products→comparison`、`query_user_logistics→logistics`、`list_user_cart`/`preview_user_order→order_preview`），同类型多次调用合并进一个数组，跳过 `{"error":...}` 结果 |
| `ReviewAiService` | 严格 JSON 的**容错解析**：4 个正则兜底 + 枚举校验（正面/中性/负面/投诉、普通/需关注/紧急），避免模型偶发 ```json 围栏导致 502 |
| `SummaryAiService` | `stripNonUserLines` 剔除商家回复后才送模型，`NON_USER_MARKERS` 判定；空列表返回 400 |
| `SimpleChatService` | 参数校验 + 委托 `SimpleAssistant` |

**日期现算而非缓存**：`today` 每次请求用 `LocalDateTime.now()` 格式化（`yyyy年MM月dd日 EEEE`，`Locale.CHINA`），保证跨零点后「今天」永远正确。

### 4.8 接口层

Controller 只做三件事：**参数校验 → 调 Service → 包 `ApiResponse`**，不含任何 AI 逻辑。

新增的 `AiKnowledgeController`（`/api/admin/ai/knowledge`，`@RequireAdmin`）解决了 RAG 的**可观测性**问题 —— RAG 对使用者是透明的，但回答不准时必须能定位到具体环节：

| 端点 | 作用 |
|---|---|
| `GET /status` | 知识库装了多少片段（**分顾客侧/运营侧**）、向量维度、topK、两个相似度阈值、词面加权参数、闸门规则清单 |
| `GET /search?q=&scope=all\|customer\|ops` | **手动执行一次检索**，看召回片段、所属域、综合分与纯向量分，以及是否被闸门拦下 |
| `POST /rebuild` | 改完知识文本重建，无需重启后端 |
| `GET /source` | 知识库原文，便于核对规则表述 |

排查逻辑很直接：**同一句话在三个域里各跑一遍** ——
顾客侧找不到 → 该规则要么没写进知识库、要么被标成了运营侧（查标题行的 `<!-- ops -->`）；
全量找得到、顾客侧找不到 → 正是分域在起作用（后台口径没漏给顾客）；
`blocked=true` → 命中了意图闸门，这句话问的是数字/统计而不是规则；
召回了但答错 → 知识没问题，去改提示词。几种情况的修法完全不同，不能靠猜。
（三个域里有两个直接复用助手在用的那两个 Bean，所以这里看到的就是助手的真实行为。）

---

## 5. 关键交互流程

### 5.1 小运一次问答（含工具调用 + RAG + 记忆）

```
前端 admin.html          Controller          Service            Assistant        框架内部
   │                       │                  │                   │                 │
   ├─ POST /api/admin/ai/assistant ─────────▶ │                   │                 │
   │                       ├─ 校验长度/登录 ─▶ │                   │                 │
   │                       │                  ├─ today 现算        │                 │
   │                       │                  ├─ 长期记忆上下文 ──▶│                 │
   │                       │                  ├─ memoryKey="u:1"  │                 │
   │                       │                  ├─ chat(...) ──────▶│                 │
   │                       │                  │                   │ ① 载入历史(1次)  │
   │                       │                  │                   │ ② 加系统提示     │
   │                       │                  │                   │ ③ RAG 检索注入   │
   │                       │                  │                   ├─ 第1轮模型调用 ─▶│
   │                       │                  │                   │◀─ tool_calls ───┤
   │                       │                  │                   │ ④ 执行 @Tool    │
   │                       │                  │                   ├─ 第2轮(带工具) ─▶│
   │                       │                  │                   │◀─ 自然语言答复 ──┤
   │                       │                  │◀─ String ─────────┤                 │
   │◀─ {code:0,data:{answer}} ────────────────┤                   │                 │
```

实测请求体（`log-requests=true` + DEBUG）确认第 1 轮已包含：完整系统提示（含当天日期与 GMV 口径 10 条规则）+ `Answer using the following information:` 注入的检索片段。第 2 轮携带 `tool_calls` 与工具结果。

### 5.2 小发一次问答（工具结果 → 前端卡片）

```
App ── POST /api/assistant/chat {message, sessionFirst} ──▶ AssistantChatController
                                                                    │
                        ShopAssistantService.chat(userId, req)      │
                          ├─ greeting = sessionFirst ? FIRST : NORMAL
                          ├─ 登录用户 history=""（走数据库记忆）
                          │  匿名用户 history=renderHistory(...)
                          └─ ShopAssistant.chat(...) → Result<String>
                                    │
                        buildAttachments(result.toolExecutions())
                          ├─ 按工具名映射卡片类型
                          ├─ 同类型多次调用合并进一个数组
                          └─ 跳过 {"error":...}
                                    │
      ◀── {reply, attachments:[{type, data}]} ── App 渲染横滑卡/对比表/物流时间线
```

**匿名与登录的分岔**：登录用户的 history 传空串（走 `u:<userId>` 数据库记忆），只有匿名场景才用前端临时上传的历史 —— 避免同一份上下文被注入两次。

### 5.3 AI 在线客服（记忆来自业务表）

```
App ── POST /api/cs/session/messages ──▶ CsController ──▶ CsService
                                                          ├─ 落库用户消息到 cs_messages
                                                          ├─ buildHistoryText(c)：最近 12 条
                                                          │   渲染成「客户：…」「客服：…」
                                                          ├─ CsAgent.reply(today, customer, history, message)
                                                          │   （RAG 自动注入平台规则）
                                                          ├─ 失败重试 → 再失败走降级话术
                                                          └─ 命中转人工规则 → 置会话状态为人工
```

### 5.4 评论 AI 三处

| 入口 | 链路 | 特点 |
|---|---|---|
| 后台「AI 评论分析」 | `POST /api/admin/ai/reviews/{id}/analyze` → `ReviewAiService.analyze` → `ReviewAgent.analyze` | 返回严格 JSON，写回 `Review.aiSentiment/aiUrgency/aiReason` |
| 后台「AI 生成评论」 | `.../{id}/draft-reply` → `ReviewAgent.draftReply` | 返回 JSON 数组 2 条草稿，**不写回** |
| 后台「批量分析」 | `.../batch-analyze?limit=` → 循环 `analyze` | 返回处理过的 id 列表 |
| App「AI 总结评论」 | `POST /api/ai/summary` → `SummaryAiService.summarize` → `ReviewAgent.summarize` | 先 `stripNonUserLines` 剔除商家回复，空则 400 |

**为什么 `ReviewAgent` 返回 `String` 而不是 POJO**：保留容错解析。模型偶发输出 ```json 围栏、或把「负面」写成「差评」时，POJO 反序列化会直接抛异常导致 502；而正则 + 枚举校验能把这些情况救回来 —— 这是**面向真实模型输出的健壮性设计**，不是为了少写代码。

### 5.5 RAG 播种与检索

```
启动 ─▶ ApplicationReadyEvent ─▶ RagKnowledgeService.seedOnStartup()
                                    ├─ enabled? 否 → 记 WARN 并跳过
                                    ├─ 尝试 rebuild()
                                    │    ├─ 清空向量库
                                    │    ├─ 切分（23 小节 → 23 片段，剥域标记 + 打 scope 元数据）
                                    │    ├─ 批量向量化（512 维）
                                    │    └─ 写入向量库（顾客侧 17 + 运营侧 6）
                                    └─ 异常 → 记 ERROR，助手退化为「记忆 + 工具」
```

**播种失败只记日志、绝不让启动失败**：RAG 是「增强」不是「必需」。若因为嵌入模型加载失败就让整个后端起不来，等于把一个可选功能升级成了单点故障 —— 助手退化成「会话记忆 + 工具调用」仍然可用，此时应照常提供服务并留下告警。

**`ai.rag.enabled` 开关只控播种、不控 Bean 装配**：`@AiService(contentRetriever = RagConfig.CONSOLE_RETRIEVER)` 是**编译期固定引用**，Bean 一旦缺席所有助手都会装配失败。让 Bean 恒在、知识库可空，关掉开关后检索自然返回 0 条，助手安静退化 —— 既满足「可关闭」，又不会把整个应用拖下水。

---

## 6. 配置方式

### 6.1 模型与记忆（`application.properties`）

```properties
# ---- DeepSeek（OpenAI 兼容端点）----
deepseek.base-url=https://api.deepseek.com/v1
deepseek.api-key=sk-****
deepseek.model=deepseek-flash       # DeepSeek-V4.1-Flash；可选 deepseek-v4-pro（仅文本）
deepseek.temperature=0.3          # 严谨档
deepseek.chatty-temperature=0.6   # 口语档
deepseek.max-tokens=1024
deepseek.timeout-seconds=60
deepseek.max-retries=1
deepseek.log-requests=false       # 排障开关，见 §6.3
deepseek.log-responses=false

# ---- 会话记忆 ----
ai.memory.max-messages=10              # 窗口大小（条）
ai.memory.max-content-chars=500        # 单条注入前截断
ai.memory.long-term-max-items=20       # 长期记忆注入条数上限
ai.memory.long-term-max-value-chars=300

# ---- 向量检索 ----
ai.rag.enabled=true
ai.rag.top-k=3
ai.rag.min-score=0.79              # 全量/后台（小运）门槛：实测空隙 (0.777, 0.802] 的中点
ai.rag.min-score-customer=0.75     # 顾客侧（小发/AI 客服）门槛：实测空隙 (0.724, 0.780] 的中点
ai.rag.chunk-size=400
ai.rag.chunk-overlap=60
ai.rag.hybrid-enabled=true         # 词面加权（P3）；false 即完全退化为纯向量检索（用于 A/B 对照）
ai.rag.hybrid-candidate-k=10       # 加权前先取这么多条候选，再重排/过滤/截尾
ai.rag.hybrid-title-boost=0.05     # 问句实词在「小节标题」里的覆盖度对应的加项上限
ai.rag.hybrid-body-boost=0.0       # 正文加项默认 0：实测会给「今天天气怎么样」这类伪相关加分（§9.4）
```

### 6.2 环境变量覆盖

**不必写 `${ENV:default}` 占位符**：Spring Boot 的环境变量优先级本就高于 `application.properties`，设置 `DEEPSEEK_API_KEY` / `DEEPSEEK_BASE_URL` / `DEEPSEEK_MODEL` 即可自动覆盖（下划线自动映射为点/横线）。反过来，若把环境变量设成**空串**，`${ENV:default}` 会解析成空值而**不是**默认值，反而更脆 —— 这是刻意不写占位符的原因。

### 6.3 排障：怎么看发出去的请求体

`deepseek.log-requests=true` **必须配合 DEBUG 日志级别**才有效，因为底层用 `log.debug`：

```bash
java -cp ... com.example.backend.BackendApplication \
     --server.port=8099 \
     --deepseek.log-requests=true \
     --logging.level.dev.langchain4j.model.openai=DEBUG
```

实际输出 logger 为 `d.l.http.client.log.LoggingHttpClient`，打印 `HTTP request:` 后跟 method / url / headers / body。

### 6.4 配置项调参指引

| 参数 | 调大 | 调小 |
|---|---|---|
| `deepseek.temperature` | 表达更活泼，但易发散、可能编数字 | 输出更稳、更可解析，但语言偏刻板 |
| `ai.memory.max-messages` | 上下文更连贯，token 成本上升 | 省钱，但多轮指代会断 |
| `ai.rag.top-k` | 召回更全 | 少噪声，可能漏规则 |
| `ai.rag.min-score` | 只注入高置信知识，宁缺毋滥 | 更容易召回，但会塞进无关条款 |
| `ai.rag.chunk-size` | 片段更完整，检索精度下降 | 精度高，但规则可能被切断 |

---

## 7. 改造前后代码对照

| 项目 | 改造前 | 改造后 |
|---|---|---|
| 手写工具 Schema | `AiToolDefinitions` + `UserAiToolsRegistry` 约 **1300 行** | 14 个 `@Tool` 方法，Schema 反射生成 |
| Function Calling 主循环 | `AiChatService` 手写 for 循环 + 手工拼 `tool_call_id` | 框架承担，接口只声明签名 |
| HTTP 客户端 | `DeepSeekClient` 手写请求/响应/错误处理 | `OpenAiChatModel` |
| 会话记忆 | `AiMemoryService` 手写窗口切片 | `ChatMemoryProvider` + `ChatMemoryStore` |
| AI 服务实现 | 6 个 Service 各自拼 messages | 6 个接口 + 注解 |
| 平台规则问答 | 无 | RAG 向量检索 |

**已删除（11 个文件，备份于 `.workbuddy/tmp/ai-refactor-backup/`）**：

```
client/DeepSeekClient.java
service/ai/  （整包：AiChatService / AiMemoryService / AiToolDefinitions
              / AiToolFunction / AiToolsRegistry / UserAiToolsRegistry）
service/AiReviewService.java
service/AiSummaryService.java
service/AssistantChatService.java
service/ChatService.java
```

**重写（5 个 Controller + 配置）**：`AiAssistantController` / `AiReviewController` / `AiSummaryController` / `AssistantChatController` / `ChatController` / `application.properties`。

**局部改造（2 个）**：`CsService`（`DeepSeekClient` → `CsAgent`，新增 `buildHistoryText`）、`RiskDetectionService`（`DeepSeekClient` → `RiskAnalyst`）—— 两者的重试机制、转人工、规则引擎、降级话术、`parseAssessment` 合并策略**全部保留未动**。

**顺带修掉的旧缺陷**：原 `ReviewStatsTool` 的 `reviewRepository` 恒为 null（构造函数收参却赋 null），导致 `getReviewStats` 永远返回 `{"total":0,...,"note":"评论仓储未注入(简化版)"}`；改造后由容器正常注入，并补齐「最近 7 天新增 + 5 条示例」。

---

## 8. 实测验证结论

验证方式：编译通过后在 **8099** 端口另起实例（不占用 IDE 的 8080），用真实 DeepSeek 端点逐接口实测。

| # | 接口 | 结果 |
|---|---|---|
| 1 | `GET /api/ping` | ✅ `{"status":"ok"}` |
| 2 | `POST /api/chat` | ✅ 正常回复 |
| 3 | `POST /api/admin/ai/assistant`（工具类问「今天有多少订单」） | ✅ 返回 0 单 / ¥0.00，并附 GMV 口径说明 |
| 4 | `POST /api/admin/ai/assistant`（制度类问「满多少免运费」） | ✅ 准确复述 88 元免运费 / 6 元 / 4 元冷链费 |
| 5 | `POST /api/admin/ai/assistant`（多轮「那昨天呢」） | ✅ 正确延续指标，跨请求记忆生效 |
| 6 | `POST /api/assistant/chat`（小发找牛奶） | ✅ 返回 `attachments[0].type=products` 商品卡片 |
| 7 | `POST /api/admin/ai/reviews/1/analyze` | ✅ `正面 / 普通 / 脆甜好吃且已多次回购` |
| 8 | `POST /api/admin/ai/reviews/2/draft-reply` | ✅ 2 条草稿，内容贴合评论原文 |
| 9 | `POST /api/admin/ai/reviews/batch-analyze?limit=3` | ✅ `{"count":3,"ids":[12,11,10]}` |
| 10 | `POST /api/ai/summary` | ✅ 总结正确，且**未提及**被剔除的商家回复 |
| 11 | `POST /api/ai/summary`（只含商家回复） | ✅ 返回 400 并说明原因 |
| 12 | `POST /api/cs/session/messages` | ✅ AI 客服答复「质量问题可退换货」并触发转人工 |
| 13 | `GET /api/admin/ai/knowledge/status` | ✅ `segmentCount=23（顾客侧 17 / 运营侧 6）, dimension=512, topK=3, minScore=0.79, minScoreCustomer=0.75, hybridEnabled=true` |
| 14 | `GET /api/admin/ai/knowledge/search?q=` | ✅ 见 §9.5 相似度实测 |
| 15 | 历史表落库 | ✅ 仅 user/assistant 对话正文，无「（未生成答复）」、无工具 JSON、无 RAG 脚手架 |

**启动日志（关键 3 行）**

```
Tomcat started on port 8099 (http) with context path '/'
Started BackendApplication in 12.085 seconds
[RAG] 知识库播种完成：23 个片段（顾客侧 17 / 运营侧 6），维度 512，门槛 全量=0.79 顾客侧=0.75
```

---

## 9. 关键设计决策与踩坑记录

### 9.1 `@ConfigurationProperties` 的松绑定陷阱

前缀 `deepseek.api` + 字段 `apiKey`：松绑定去掉前缀后剩余段是 `key`，与 accessor `apiKey` 匹配不上 → **apiKey 恒为 null**，启动报「DeepSeek API Key 未配置」。

**修法**：前缀只写 `deepseek`，属性写 `deepseek.api-key`。

> 附带正面发现：该失败堆栈恰好**证明了改造的正确性** —— 报错是
> `Error creating bean with name 'adminAssistant': Cannot resolve reference to bean 'aiChatModel'`，
> 说明 `@AiService` 接口已被成功扫描、Bean 已实例化、`chatModel` 与 `chatMemoryProvider` 引用均已解析，唯一失败点只是配置绑定。

### 9.2 【最重要】`ChatMemoryStore` 是纯文本表时，不能配 `MessageWindowChatMemory`

这是本次最隐蔽、后果最严重的问题。

`MessageWindowChatMemory` 把 `ChatMemoryStore` 当作**唯一事实来源**：它的 `add()` 每次都先 `messages()` **重新从 store 读一遍整个列表**，追加后再回写（字节码已确认）。这个设计对「store 存的就是完整会话」成立，但我们的 store 是一张**纯文本表**（`ai_chat_messages` 只有 `role` + `content` 两列），装不下：

| 内容 | 回读后 |
|---|---|
| 系统提示 | **没有了** —— 模型根本收不到角色设定 |
| 助手回合里的 `tool_calls` | 变成一句空文本 |
| `ToolExecutionResultMessage` | 没有对应列，干脆丢失 |

**后果**在工具调用上最致命：第 1 轮模型要求调工具，框架执行后把「助手回合 + 工具结果」写进记忆；一旦回读，工具请求就消失了，第 2 轮请求里只剩「一句空话 + 一条无主的工具结果」。

实测报错为 DeepSeek 抛出的：

```
The `reasoning_content` in the thinking mode must be passed back to the API.
```

**这个报错信息具有严重误导性** —— 它指向「thinking 模式」，让人以为是模型/协议不兼容。实际上裸的两轮工具循环（不经记忆存储）在 `deepseek-v4-pro` 上**完全正常**，四种参数变体全部成功：

| 变体 | 第 1 轮 | 第 2 轮 |
|---|---|---|
| A 原始（默认 thinking） | tool_calls ✅ | ✅ 成功 |
| B `customParameters({"thinking":{"type":"disabled"}})` | tool_calls ✅ | ✅ 成功 |
| C `returnThinking(true).sendThinking(true)` | tool_calls ✅（thinking 93 字符） | ✅ 成功 |
| D `deepseek-flash` + thinking disabled | tool_calls ✅ | ✅ 成功 |

**真正的修法是自定义 `ChatMemory`**（`JpaBackedChatMemory`）：

```
请求进入 → ChatMemoryProvider 造一个新实例
        → 首次访问 messages() 时「一次性」从 store 载入历史并裁剪到窗口大小
        → 之后本次请求内所有读写都发生在内存列表上（工具循环因此完整）
        → 每次 add() 顺便把「新增的尾部落库」（store 只追加）
```

关键在**「只载入一次」**：数据库是「跨请求的记忆」，内存列表是「本次请求的真相」，二者职责分离。

**窗口只在载入时裁剪**：窗口的作用是「别把几百轮历史都塞给模型」，针对的是**历史**。若在请求中途也裁剪，就可能把某次工具调用请求裁掉、只留下它的结果 —— 制造出非法消息序列。因此请求中途只增不减；一次请求的轮数由框架的工具循环上限约束，不会失控。裁剪时系统提示永远保留，若窗口首条是「无主的工具结果」也一并丢弃。

> 修复后同一问题（「今天有多少订单？销售额多少？」）由 502 变为正常回答 0 单 / ¥0.00，并附带正确的 GMV 口径说明。

### 9.3 RAG 注入的脚手架会经 `ChatMemory` 落库

LangChain4j 的检索增强把召回的知识片段**并入用户消息本身**：

```
今天有多少订单？销售额多少？

Answer using the following information:
【客服接待量统计】每位客服的接待量按…
```

这份「合并稿」是给模型看的**内部格式**，但凡是经 `ChatMemory` 落库的内容都会原样出现在 `/history` 接口与 App 的历史气泡里 —— 实测确实落库了（500 字符截断上限也被这段脚手架占满）。不剥掉的话，用户翻聊天记录会看到一句中文提问后面跟着英文指令和别人的规则文本。

**修法**：落库前按 `Answer using the following information:` 分隔符剥离。附带好处是历史里存的是用户真正问的那句话，不会被召回片段挤占截断额度。实测修复后历史表内容为 `user: "满多少免运费？"`（7 字符）。

### 9.4 `min-score` 必须实测校准，而且阈值救不了混装的候选池

**第一轮（旧结论，后来被推翻）**：早先按经验写的 `0.55` 实际上等于不过滤 —— BGE-small-zh 的分数分布被压缩在很窄的区间里。当时用 16 个问题实测得到正例 0.804~0.909、负例 0.636~0.735，于是写下「0.78 正好落在空隙里」。

**第二轮（2026-10-09，20 个问句 × 3 个域，走真实的检索接口）**：换一批问句后分数带整体移动，出现了**正例最低 0.769（怎么转人工？）< 负例最高 0.777（今天天气怎么样）** 的交错 —— 也就是说**不存在任何一个单一阈值**能同时「放过全部真问题」与「挡住全部无关问题」。0.78 既放过了跨域误召（今天有多少订单 = 0.802），又拦掉了真问题（生鲜能退吗 = 0.7796、怎么转人工 = 0.769）。

**根因不是阈值选错，而是候选池混装**：23 个小节里有 6 条后台运营规则，三个助手却共用同一个检索器。按受众域把池子分开之后（§4.6）：

| 域 | 正例 | 负例 / 后台口径 | 可用门槛区间 |
|---|---|---|---|
| 顾客侧（小发 / AI 客服） | 0.780 ~ 0.935 | 0.632 ~ 0.724 | (0.724, 0.780] → 取 **0.75** |
| 全量（小运） | 业务口径类 ≥ 0.802 | 闲聊最高 0.777 | (0.777, 0.802] → 取 **0.79** |

「今天天气怎么样」在顾客池从 0.777 掉到 0.689 —— 它的高分本就来自「按天统计的时间范围怎么算」这条**后台**小节。池子一分开，分数带自然就拉开了。

**剩下的两条缝由另外两个机制补**：

- **词面加权（P3）**：短问句的向量分天然偏低。给「问句实词与小节标题的字面重合度」设上限 0.05 后，正例下限从 0.769 升到 0.780（怎么转人工 0.769 → 0.819），**全部负例一分未动**。
  但**正文不能同样加权**：给正文也加权后，「今天天气怎么样」因为命中「按天统计…」正文里的「今天」而 +0.007（0.777 → 0.784），恰好越过全量池门槛 —— 同一个机制既奖励「生鲜」命中退货规则正文，也奖励「今天」命中统计口径正文，**加性权重无法区分「正文确实在讲这件事」与「正文只是顺带提了一句」**，所以 `hybrid-body-boost` 默认 0。
- **意图闸门（P2）**：问「平台业务数字 / 后台口径概念」的问法在顾客池里不可能有答案，直接跳过检索（今天有多少订单、销售额是多少、客服接待量怎么看）。阈值拦不住它们 —— 它们的分数本来就不低。

⚠️ **换嵌入模型或大幅增删知识文本后必须重新校准** —— 不同嵌入模型的分数分布不可通用；同一模型下知识文本一变，分数带也会整体平移，这两轮实测的差值就是证据。

### 9.5 `@AiService` 的装配必须是 `EXPLICIT`

容器里有两个 `ChatModel`、两套工具。`AUTOMATIC` 会把所有工具塞给所有助手，导致能力错配与**权限风险**（小发可能去查全站用户统计）。因此每个 `@AiService` 都逐一指名 `chatModel` / `chatMemoryProvider` / `contentRetriever` / `tools`。

### 9.6 工具内部用 `@ToolMemoryId` 而非模型传 userId

`@ToolMemoryId` 参数不出现在给模型的 Schema 里，模型既看不到也无从伪造。改造前把 userId 放在 argsJson 里手工透传，一旦模型被诱导就可能越权。越权防护同时落在工具内部（购物车归属校验、订单归属校验）。

### 9.7 `ReviewAgent` 返回 `String` 留容错解析

POJO 反序列化遇到 ```json 围栏或中文枚举不精确时会直接抛异常导致 502。保留正则 + 枚举校验能把这些情况救回来 —— 面向真实模型输出的健壮性设计。

### 9.8 播种失败不能拖垮启动

RAG 是增强不是必需，`seedOnStartup` 捕获所有异常只记日志，助手退化为「记忆 + 工具调用」。

---

## 10. 演进路径

| 方向 | 做法 | 触发条件 |
|---|---|---|
| 知识库外置 | 把 `PlatformKnowledgeBase.document()` 换成从 `knowledge_articles` 表读同格式文本 | 规则要交给运营自助维护 |
| 向量库持久化 | 把 `RagConfig` 的 `InMemoryEmbeddingStore` 换成基于表的 `EmbeddingStore` 实现 | 知识量增长到启动播种耗时明显 |
| 检索重排序 | 在 `contentRetriever` 后加 Reranker（`RetrievalAugmentor` 支持组合） | 召回精度成为瓶颈 |
| 流式输出 | 用 `StreamingChatModel` + `TokenStream` 接口 | App 需要打字机效果 |
| 会话记忆持久化选择 | `ChatMemoryStore` 换 Redis / 多实例共享 | 后端需要水平扩展 |
| 模型热切换 | 已是配置驱动（`deepseek.base-url` / `model`），换成 OpenAI / 通义 / Kimi 只改配置 | — |

**新增一个 AI 能力的标准动作**（重构后）：

1. 在 `AiPrompts` 加一份提示词常量；
2. 在 `assistant` 包加一个接口方法（需要工具就在 `tools` 包加 `@Tool` 方法）；
3. 在 `service` 包加编排逻辑；
4. 在 `controller` 暴露端点。

**不需要**：写 HTTP 客户端、写 Function Calling 主循环、写 JSON Schema、写记忆读写、写向量检索 —— 这些都是框架职责。

---

## 附：常见问题速查

| 现象 | 排查方向 |
|---|---|
| 启动报「API Key 未配置」 | 检查 `@ConfigurationProperties` 前缀与字段名的松绑定（§9.1） |
| 工具调用报 `reasoning_content` 相关错误 | 记忆层是否回读丢失了 `tool_calls`（§9.2） |
| 历史气泡里出现英文指令 | RAG 脚手架未剥离（§9.3） |
| 无关提问也被注入知识 | 先在管理页切到「顾客侧」域看它召回什么：命中了后台小节 → 该小节标题行漏标 `<!-- ops -->`；命中的是数字/统计类问法 → 意图闸门规则没覆盖到；都不是 → 阈值需重新实测校准（§9.4） |
| 顾客问规则却答不上来 | 同一句话在管理页切「全量」再试：全量有、顾客侧没有 → 该小节被标成了运营侧，或分数低于顾客侧门槛 0.75（短问句写全条件分数会明显上升） |
| 助手不按角色设定回答 | 系统提示是否被记忆回读丢掉（§9.2） |
| 关掉 RAG 后启动失败 | `enabled` 只控播种不控 Bean，别把 Bean 改成条件装配（§5.5） |
| 想看发出去的请求体 | `deepseek.log-requests=true` + `logging.level.dev.langchain4j.model.openai=DEBUG`（§6.3） |
