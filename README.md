# 知序学堂

知序学堂是一个可在本机运行的在线教育微服务系统。它把课程浏览、学习、交易、个人中心和课程文档问答串成一条可操作的演示链路，适合作为后端微服务与 AI 应用的项目展示。

![知序学堂本地微服务架构](./assets/readme/architecture.svg)

> 当前仓库只提供本地 Docker Compose 启动方式。目录和服务注册名中的 `tj-*` / `tianji-*` 标识是运行时契约，保持不变以确保现有编排和 Nacos 配置可以直接工作。

## 当前能力

- 账号登录、手机号本地 Mock 注册、个人资料与本地头像预览
- 首页分类、课程检索、自动补全、搜索历史、课程详情和课程目录
- 免费课程报名、收费课程加入购物车、创建待支付订单
- 笔记、课程评价、收藏、优惠券、积分签到、积分榜和个人中心
- LangChain4j + DeepSeek 的课程文档问答：支持 Markdown/TXT 上传、本地文件持久化、文本检索、普通请求问答、会话创建与历史恢复
- 本地 MySQL、Redis、RabbitMQ、Nacos、Elasticsearch 及 Java 服务的 Compose 编排

### AI 功能是否足够展示

足够作为当前简历项目的 AI 功能亮点：已经形成“上传课程资料 → 建立本地知识上下文 → 提问 → 返回基于资料的答案 → 恢复会话”的完整闭环，能直观展示 RAG 应用的核心流程。当前实现定位是本地演示版，使用文本检索和普通请求，不等同于生产级向量数据库、权限隔离、流式输出或高并发服务。

## 技术栈

Java 11 · Spring Boot 2.7 · Spring Cloud 2021 · Spring Cloud Alibaba · MyBatis-Plus · Vue 3 · Vite · MySQL 8 · Redis · RabbitMQ · Nacos · Elasticsearch · LangChain4j · DeepSeek · Docker Compose

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

AI 服务读取以下环境变量，未配置 `DEEPSEEK_API_KEY` 时仍可使用本地检索回退回答：

```text
DEEPSEEK_API_KEY=your-key
DEEPSEEK_MODEL=deepseek-v4-flash
DEEPSEEK_BASE_URL=https://api.deepseek.com
TJ_AI_DATA_DIR=/data/ai
```

知识库文件保存于仓库挂载目录 `data/ai/documents/`，不会上传到云存储。

## 已知限制与后续计划

- 仅支持本地部署，没有公网在线 Demo；GitHub 访问者不能直接访问开发机的 `localhost`。
- AI 当前为普通请求，不提供流式输出；检索为本地文本检索，尚未接入生产级向量数据库、重排和多租户权限。
- 微信/支付宝支付、真实短信、云媒资、对象存储等第三方服务只保留配置占位，不作为本地验收依赖。
- 消息/WebSocket、考试题库和部分非核心互动功能提供空状态或后续扩展入口。
- 下一步：录制真实页面截图/GIF，补充未完成业务模块，再评估公网服务器上的在线 Demo；最后再迭代 AI 流式输出、向量检索和文档权限管理。

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
