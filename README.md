# 小发智购 · 仿大润发全栈电商系统

一个从客户端到管理后台、从业务闭环到 AI 能力全链路打通的全栈练手项目。后端一套 REST 服务同时支撑 **Android 客户端**与 **Web 管理后台**，并在业务系统上落地了三个真实的大模型场景（运营助手 / 评论分析 / RAG 知识库）。

> 学习与作品展示用途，非商业项目，与高鑫零售（大润发）无任何关联。"大润发"仅作为界面风格与业务建模的参考。

---

## 在线演示

| 入口 | 地址 | 说明 |
|---|---|---|
| 管理后台（Vue 3） | `http://43.108.56.60/app/` | 主入口，面试演示用 |
| 管理后台（旧版单页） | `https://<域名>/admin.html` | 单文件 SPA，保留兼容 |

> 演示账号与部署步骤见 [docs/部署手册.md](docs/部署手册.md)。

---

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Spring Boot 4.1 / Java 17 / Spring Data JPA / Bean Validation |
| 数据库 | MySQL 8（utf8mb4，Hibernate `ddl-auto=update` 自维护表结构） |
| 鉴权 | JWT（jjwt 0.12）+ BCrypt + RBAC 权限码校验 |
| AI | LangChain4j 1.22 + DeepSeek（OpenAI 兼容协议）、BGE-small-zh 本地向量嵌入（ONNX） |
| 管理后台 | Vue 3.5 + Vite 8 + Element Plus + Pinia + Vue Router（哈希路由） |
| 客户端 | Android（Java，Retrofit + SQLite 仓储层） |
| 部署 | Docker 多阶段构建 + Docker Compose + Nginx + Let's Encrypt |

---

## 功能概览

**商品与营销**：商品 / 分类 / 库存（低库存预警）/ 促销 / 优惠券
**交易链路**：购物车 → 下单 → 支付 → 发货（物流）/ 退款 / 评价
**会员与风控**：会员 / 钱包与余额流水 / 风险评分
**运营后台**：仪表盘（近 7 天销售趋势 + 订单/分类/支付分布）、订单、退款、物流、支付、角色与权限（RBAC）
**客服**：会话与消息、后台客服工作台

### AI 能力（三个落点）

1. **运营问答助手** —— LangChain4j Function Calling 注册 8 个查询工具，自然语言直接查订单/库存/销售数据，回答里带真实数字而非编造
2. **评论情感分析与回复草稿** —— 输出情感（正面/中性/负面/投诉）与紧急度（普通/需关注/紧急）结构化标签，并按合规口径生成回复草稿（明确不承诺赔偿/免单）
3. **RAG 知识库** —— BGE 中文向量检索 + 标题词面加权混合排序；相似度阈值按「全量池 / 顾客池」分域实测校准

> 阈值校准的实测过程记录在 `backend/src/main/resources/application.properties` 的注释里——正例下限一度低于负例上限，根因是候选池混装，不是阈值选错。

---

## 仓库结构

```
backend/          Spring Boot 服务（23 个 REST 控制器 / 196 个类）
frontend/         Vue 3 管理后台（构建产物输出到 backend 的 static/app）
MyApplication5/   Android 客户端
deploy/           Dockerfile / compose / Nginx / 发布脚本
docs/             架构文档、API 清单、时序图、截图、部署手册
```

前后端最终合成**单个 jar**：`npm run build:backend` 把 Vue 产物打进 `backend/src/main/resources/static/app`，一次部署一个进程。

---

## 本地启动

```bash
# 1. 数据库（需本机 MySQL 8，默认库名 darunfa）
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS darunfa DEFAULT CHARSET utf8mb4"

# 2. 后端（需 Java 17）
#    backend/mvnw 在 Git 里是 100644，Linux/macOS 下直接 ./mvnw 会 Permission denied，统一用 bash 调用
cd backend && bash ./mvnw spring-boot:run

# 3. 管理后台（另开终端，需 Node >= 20.19）
cd frontend && npm ci && npm run dev
```

环境变量（缺了 AI 功能会 fail-fast，属于预期行为）：

```bash
DEEPSEEK_API_KEY=sk-xxx      # DeepSeek 平台申请
JWT_SECRET=<随机串>           # 生产必须替换
```

访问 `http://localhost:8080/` → 登录页，内置管理员见 `application.properties` 的 `app.admin.*`。

---

## 部署

完整步骤见 **[docs/部署手册.md](docs/部署手册.md)**，包含服务器初始化、容器编排、Nginx + HTTPS、验收清单、故障速查表。

```bash
cd deploy
cp .env.example .env     # 填入 MySQL 密码 / JWT 密钥 / 管理员密码 / DeepSeek Key
docker compose up -d --build
```

---

## 界面截图

`docs/screenshots/` 下有 27 张功能截图，覆盖后台各模块与 AI 场景。

---

## 文档

| 文档 | 内容 |
|---|---|
| [docs/部署手册.md](docs/部署手册.md) | 从零到上线的完整部署流程 |
| [AI架构设计说明.md](AI架构设计说明.md) | 智能体编排、工具定义、RAG 与阈值校准 |
| [系统架构设计文档.md](系统架构设计文档.md) | 分层、模块划分、事务与一致性设计 |
| [docs/api-inventory.md](docs/api-inventory.md) | 接口清单 |
| [docs/sequence-diagram.mermaid](docs/sequence-diagram.mermaid) | 核心下单时序 |
