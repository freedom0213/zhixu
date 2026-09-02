# 知序学堂（Zhixu）

知序学堂是一个面向在线教育场景的 Spring Cloud 微服务项目，包含认证、用户、课程、学习、考试、交易、支付、营销、搜索和媒资等领域服务。项目内部 Java 包名、模块名和基础设施标识继续沿用教程中的 `tianji`/`tj` 前缀，以避免破坏已有依赖。

![Zhixu local microservice architecture](./assets/readme/architecture.svg)

> 当前仓库处于本地恢复阶段。登录、课程浏览、购物车、预下单、待支付订单和免费课程报名已经可以在本地 Docker 环境中演示；原虚拟机中的历史业务数据、云存储和支付凭据无法恢复，详见“已知边界”。

## 技术栈

Java 11 · Spring Boot 2.7 · Spring Cloud 2021 · Spring Cloud Alibaba · MyBatis-Plus · Nacos · MySQL 8 · Redis · RabbitMQ · Elasticsearch · Seata · XXL-JOB

## 本地启动

前置条件：Docker Desktop（启用 Linux containers）、Git、Node.js 16+ 和 JDK 11/Maven（构建 Java 服务时需要）。用户端前端位于 `tj-front/tj-protal`，当前先以 Vite 开发服务运行。

```powershell
Copy-Item deploy/.env.example deploy/.env
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d
docker compose --env-file deploy/.env -f deploy/docker-compose.yml ps
```

首次启动或数据库数据卷为空时，`deploy/mysql/init/` 下的数据库、表和演示数据会自动导入。已有数据卷升级时手动执行一次迁移：

```powershell
docker cp deploy/mysql/init/20-demo-mvp.sql tianji-local-mysql-1:/tmp/20-demo-mvp.sql
docker exec tianji-local-mysql-1 sh -c "mysql -uroot -p1234 --default-character-set=utf8mb4 < /tmp/20-demo-mvp.sql"
```

启动后可访问：

| 组件 | 地址 |
| --- | --- |
| Nacos 控制台 | http://localhost:8848/nacos |
| RabbitMQ 管理台 | http://localhost:15672 |
| Elasticsearch | http://localhost:19200 |
| Nginx 入口 | http://localhost/healthz |
| 用户端前端 | http://localhost:18082/ |
| MySQL | localhost:13306 |
| Redis | localhost:6379 |

Compose 会自动创建 `tj_*` 业务数据库，并把 `deploy/nacos/configs` 下的共享配置导入 Nacos。停止环境：

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.yml down
```

## 启动 Java 服务

先完成上面的基础设施启动，再构建并启动本地 Java 服务：

```powershell
mvn -DskipTests package
```

网关端口为 `10010`，业务服务端口范围为 `8081` 至 `8093`。完整端口和模块说明见 [docs/services.md](docs/services.md)。

如需把已构建的 Java 服务也放入 Docker 网络，先执行 Maven 打包，再使用服务编排文件：

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.yml -f deploy/docker-compose.services.yml up -d --build
```

服务编排保留原有 `tj-*` 模块和服务名，仅把容器内的 Nacos、MySQL、Redis、RabbitMQ、Elasticsearch 地址切换为 Compose 服务名。

只验证当前本地演示闭环时，可以只构建认证、用户、课程、考试、学习、交易和网关服务：

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.yml -f deploy/docker-compose.services.yml up -d --build auth-service user-service course-service exam-service learning-service trade-service gateway-service
```

媒资、短信消息、搜索、支付、营销、数据和评论服务目前不需要启动；它们依赖尚未配置的云服务或未完成的业务功能。

## 启动用户端前端

在基础设施和 Java 服务启动后，另开一个终端运行：

```powershell
Set-Location tj-front/tj-protal
npm install
npm run dev
```

浏览器打开 http://localhost:18082/，使用演示账号 `demo / 123456` 登录。前端 API 默认请求本机网关 `http://localhost:10010`，可通过 `tj-front/tj-protal/.env` 中的 `VITE_API_BASE_URL` 覆盖。

## 本地 smoke test

使用登录接口返回的 `data` 作为原始 `Authorization` 请求头（项目网关不需要添加 `Bearer` 前缀），可验证：

- `POST /as/accounts/admin/login`
- `GET /cs/courses/simpleInfo/list`
- `GET /cs/courses/1001/catalogs`
- `POST /ts/carts`、`GET /ts/carts`
- `GET /ts/orders/prePlaceOrder?courseIds=1001`
- `POST /ts/orders/placeOrder`
- `POST /ts/orders/freeCourse/1002`、`GET /ls/lessons/1002`

## 截图与演示

真实业务页面截图/GIF 可在本地前端稳定后录制，建议至少展示：登录、课程检索、课程详情、免费报名和购物车。截图放在 `docs/screenshots/`，GIF 放在 `assets/readme/`，再把它们嵌入本 README。当前架构图是可发布的静态 SVG，不冒充业务页面。

## 已知边界与下一步

- `deploy/mysql/init/00-databases.sql` 只创建数据库，未伪造已删除虚拟机中的表结构和历史数据。
- 支付、短信、腾讯云媒资配置已改为环境变量占位符；需要使用时在 `deploy/.env` 注入，并建议轮换旧凭据。
- 本地演示数据是为恢复后的空数据库准备的最小数据集，不代表原虚拟机中的历史数据。
- 支付、短信、云媒资、搜索和点赞等功能暂不作为本地 MVP 的必需链路。
- 在线 Demo 需要公网服务器或公网隧道，不能直接把开发机的 `localhost` 提供给 GitHub 访问者；因此安排在前端和后端闭环稳定之后。

## 阶段记录

### 第一阶段（本次）

- 项目对外名称统一为“知序学堂”，保留原有 `tianji`/`tj` 内部技术标识。
- 将本地 profile 从失效虚拟机地址切换为可配置的本地 Nacos/Elasticsearch 地址。
- 新增 MySQL、Redis、RabbitMQ、Elasticsearch、Nacos 的 Docker Compose 基础设施和健康检查。
- 新增 Java 微服务 Docker 编排文件，支持 Maven 打包后加入同一 Compose 网络。
- 新增 Nacos 共享配置自动导入脚本与 MySQL 数据库初始化脚本。
- 新增课程、考试、学习、交易模块的幂等最小表结构和演示数据。
- 修复未购买课程查看详情时学习服务空指针问题。
- 为 RabbitMQ 增加 `rabbitmq_delayed_message_exchange` 插件，恢复交易服务监听队列。
- 完成登录、课程、购物车、预下单、待支付订单和免费报名接口 smoke test。
- 清理仓库中的云服务、支付、短信和旧环境账号明文。
- 新增架构展示图和可复现启动文档。

### 第二阶段（前端恢复）

- 恢复用户端前端到 `tj-front/tj-protal`，串联登录、课程详情、免费报名、购物车和订单预演流程。
- 修复前端课程列表、兴趣保存和 Header 事件清理问题。
- 增加 Elasticsearch 课程索引初始化、搜索服务推荐数量配置和 `demo / 123456` 学生账号。
- 修复本地演示数据的 MySQL UTF-8 中文写入问题。

### 第三阶段（待办）

- 录制真实页面截图/GIF，完善简历项目说明。
- 根据前后端闭环结果选择公网服务器或隧道部署在线 Demo。
- 后续再评估课程文档问答 AI 功能，不影响当前 MVP。

## License

仅用于学习和求职作品展示。
