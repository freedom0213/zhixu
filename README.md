# 知序学堂

知序学堂是一个可在本机运行的在线教育微服务系统。它把课程浏览、学习、交易、个人中心和课程文档问答串成一条可操作的演示链路，适合作为后端微服务与 AI 应用的项目展示。

![知序学堂本地微服务架构](./assets/readme/architecture.svg)

> 当前仓库只提供本地 Docker Compose 启动方式。目录和服务注册名中的 `tj-*` / `tianji-*` 标识是运行时契约，保持不变以确保现有编排和 Nacos 配置可以直接工作。

## 当前能力

- 账号登录、手机号本地 Mock 注册、个人资料与本地头像预览
- 首页分类、课程检索、自动补全、搜索历史、课程详情和课程目录
- 免费课程报名、收费课程加入购物车、创建待支付订单
- 笔记、课程评价、收藏、优惠券、积分签到、积分榜和个人中心
- LangChain4j + DeepSeek 的课程文档问答（RAG）：Markdown/TXT 上传、结构化分块、BGE-M3 向量化、pgvector 持久化、BGE-reranker 重排、流式输出（SSE）、会话创建与历史恢复
- 知识库多用户隔离：文件、向量 chunk、会话与聊天记录均按用户 ID 隔离，未登录不可见、不可传、不可删；同一用户同名文件判重
- 本地 MySQL、Redis、RabbitMQ、Nacos、Elasticsearch、pgvector 及 Java 服务的 Compose 编排

### AI 功能是否足够展示

已经形成完整的 RAG 工程链路：**上传资料 → 结构化分块 → Embedding 向量化 → pgvector 持久化 → 向量召回（粗排 topK）→ BGE 重排（精排 topN）→ DeepSeek 生成 → SSE 流式输出**，并叠加多用户数据隔离、失败降级（向量库不可用时回退关键词检索，rerank 失败时回退原排序）等工程化设计。定位为本地演示版：单实例部署、无分布式向量库与鉴权审计，这些是生产化差异而非功能缺失。

## 技术栈

Java 11 · Spring Boot 2.7 · Spring Cloud 2021 · Spring Cloud Alibaba · MyBatis-Plus · Vue 3 · Vite · MySQL 8 · Redis · RabbitMQ · Nacos · Elasticsearch · pgvector · LangChain4j · DeepSeek · SiliconFlow（BGE-M3 / BGE-reranker）· Docker Compose

## 本地启动

前置条件：Docker Desktop（Linux containers）、Git、JDK 11 和 Maven。仅运行已构建镜像时不需要 Node.js；修改前端时再安装 Node.js 16+。

1. 创建本地环境文件：

```powershell
Copy-Item deploy/.env.example deploy/.env
```

2. 启动基础设施（MySQL、Redis、RabbitMQ、Nacos、Nginx）：

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d
docker compose --env-file deploy/.env -f deploy/docker-compose.yml ps
```

3. 构建 Java 服务并启动用户端所需服务：

```powershell
mvn -DskipTests package
docker compose --env-file deploy/.env -f deploy/docker-compose.yml -f deploy/docker-compose.services.yml up -d --build auth-service user-service course-service exam-service learning-service trade-service remark-service promotion-service ai-service gateway-service frontend
```

4. 如需启用搜索，追加 `search` profile（会启动 Elasticsearch、索引初始化和 search-service）：

```powershell
docker compose --env-file deploy/.env --profile search -f deploy/docker-compose.yml -f deploy/docker-compose.services.yml up -d --build search-service
```

停止本地环境：

```powershell
docker compose --env-file deploy/.env --profile search -f deploy/docker-compose.yml -f deploy/docker-compose.services.yml down
```

首次创建 MySQL 数据卷时，`deploy/mysql/init/` 会自动导入数据库、表和演示数据。已有数据卷升级时，可重复执行迁移脚本：

```powershell
docker cp deploy/mysql/init/20-demo-mvp.sql tianji-local-mysql-1:/tmp/20-demo-mvp.sql
docker exec tianji-local-mysql-1 sh -c "mysql -uroot -p1234 --default-character-set=utf8mb4 < /tmp/20-demo-mvp.sql"
```

## 访问与验收

| 入口 | 地址 |
| --- | --- |
| 用户端前端 | [http://localhost:18082/](http://localhost:18082/) |
| Nginx 健康检查 | [http://localhost/healthz](http://localhost/healthz) |
| API Gateway | http://localhost:10010 |
| Nacos 控制台 | [http://localhost:8848/nacos](http://localhost:8848/nacos) |
| RabbitMQ 管理台 | [http://localhost:15672](http://localhost:15672) |
| Elasticsearch | http://localhost:19200 |
| MySQL 宿主机端口 | localhost:13306（容器内部为 3306） |
| Redis | localhost:6379 |

演示账号：`demo / 123456`。建议按“登录 → 搜索课程 → 查看目录 → 收藏或加入购物车 → 个人中心 → AI 知识库”顺序验收。直接访问 Gateway 根路径返回 `404 NOT_FOUND` 是正常现象，前端应访问 `18082`。

## AI 配置

AI 服务读取以下环境变量。当 `DEEPSEEK_API_KEY` 缺省时聊天模型不可用；`TJ_AI_EMBEDDING_MODEL` 留空时关闭向量检索，退化为本地关键词召回（`chunks.json`）；`TJ_AI_RERANK_MODEL` 留空时关闭重排，向量召回结果直接送 LLM。填齐后自动启用完整 RAG 链路：Embedding 向量化 → pgvector 持久化 → 向量召回 → 重排 → LLM 生成。

```text
# 聊天模型（DeepSeek，兼容 OpenAI 协议）
DEEPSEEK_API_KEY=your-key
DEEPSEEK_MODEL=deepseek-v4-flash
DEEPSEEK_BASE_URL=https://api.deepseek.com
TJ_AI_DATA_DIR=/data/ai

# Embedding（示例：硅基流动 BGE-M3，1024 维；留空则关闭向量检索）
TJ_AI_EMBEDDING_MODEL=BAAI/bge-m3              # 留空则关闭 embedding
TJ_AI_EMBEDDING_BASE_URL=https://api.siliconflow.cn   # 默认与 chat 同 endpoint
TJ_AI_EMBEDDING_API_KEY=sk-xxx                 # 独立 key；留空复用 DEEPSEEK_API_KEY
TJ_AI_EMBEDDING_DIMENSION=1024                 # 与模型输出一致即可

# pgvector 持久化（容器内已编排 pgvector 服务）
TJ_AI_VECTOR_STORE_ENABLED=true
TJ_PG_HOST=pgvector        # 容器内用服务名；宿主机直连改 localhost
TJ_PG_PORT=5432            # 容器内端口；宿主机映射默认 5433
TJ_PG_DATABASE=tianji
TJ_PG_USER=tianji
TJ_PG_PASSWORD=tianji123

# Rerank 重排（可选；留空则关闭，向量召回 topK 直接喂 LLM）
TJ_AI_RERANK_MODEL=BAAI/bge-reranker-v2-m3
TJ_AI_RERANK_BASE_URL=https://api.siliconflow.cn
TJ_AI_RERANK_API_KEY=sk-xxx
TJ_AI_RERANK_CANDIDATE_K=10   # 向量召回送重排的候选条数
TJ_AI_RERANK_TOP_N=3          # 重排后真正喂给 LLM 的 chunk 数
```

知识库文件按用户隔离保存于仓库挂载目录 `data/ai/documents/{userId}/`，不会上传到云存储；向量块同步落盘到 `pgvector` 容器对应的 `knowledge_chunks` 表（首次启动由 LangChain4j 自动建表，metadata 记录 `userId` 供检索过滤）。若不填 embedding 模型，向量库容器仍会启动但不会写入任何向量，检索回退为本地 `chunks.json` 的关键词召回；rerank 调用失败时自动退化为原排序 topK。

### 知识库问答的设计取舍（已确认）

`KnowledgeService.buildPrompt` 当前允许模型在「**参考资料涉及相关概念**」时用简洁的基础知识做补充，仅在「资料确实没有涉及时」才明确说明。这意味着：未上传任何知识库文件时，模型可能基于通用知识回答"孔子是谁"这类常识问题——这并不算 bug，而是当前的产品定位：

- **若产品定位是「知识库 + 常识兜底」**：保留现状即可，模型既能给到参考资料中的精准答案，也能回答开放性常识问题。
- **若产品定位是「严格知识库助手」**：应将 prompt 收紧为"仅当参考资料相关时才回答；无资料时一律回复『未找到相关资料，请先上传知识库文件』"。这是设计选择，不是缺陷。

本次已与产品方确认：暂维持当前行为，但明确记入文档，避免后续误解。

## 已知限制与后续计划

- 仅支持本地部署，没有公网在线 Demo；GitHub 访问者不能直接访问开发机的 `localhost`。
- AI 已形成完整 RAG 链路：结构化分块 + BGE-M3 Embedding + pgvector 持久化（`pgvector/pgvector:pg16` 容器，端口 5433）+ BGE-reranker 重排 + SSE 流式输出 + 多用户数据隔离（文件/向量/会话/聊天记录按 userId 隔离）与同名文件判重；多轮对话的会话数据目前存内存，重启即失效（持久化是后续方向）。
- 微信/支付宝支付、真实短信、云媒资、对象存储等第三方服务只保留配置占位，不作为本地验收依赖。
- 消息/WebSocket、考试题库和部分非核心互动功能提供空状态或后续扩展入口。
- 下一步：在 README 接入 AI 文档截图与 pgvector 表内数据的可视证据；评估公网服务器上的在线 Demo；评估检索质量评估集（hit rate）与会话持久化。

## 目录结构

```text
deploy/              Docker Compose、Nacos、MySQL 和搜索初始化
tj-*/                Java 微服务模块
tj-front/tj-protal/  Vue 3 用户端
tj-ai/               LangChain4j 知识库问答服务
assets/readme/       README 架构图
```

## License

仅用于学习与求职作品展示。
