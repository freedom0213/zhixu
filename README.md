# 知序学堂（Zhixu）

知序学堂是一个面向在线教育场景的 Spring Cloud 微服务项目，包含认证、用户、课程、学习、考试、交易、支付、营销、搜索和媒资等领域服务。项目内部 Java 包名、模块名和基础设施标识继续沿用教程中的 `tianji`/`tj` 前缀，以避免破坏已有依赖。

![Zhixu local microservice architecture](./assets/readme/architecture.svg)

> 当前仓库处于本地恢复阶段。第一阶段交付了可复现的基础设施和 Nacos 配置导入；原虚拟机中的业务表数据、云存储/支付凭据无法从 Git 历史恢复，详见“已知边界”。

## 技术栈

Java 11 · Spring Boot 2.7 · Spring Cloud 2021 · Spring Cloud Alibaba · MyBatis-Plus · Nacos · MySQL 8 · Redis · RabbitMQ · Elasticsearch · Seata · XXL-JOB

## 本地启动

前置条件：Docker Desktop（启用 Linux containers）和 Git。当前项目的前端仍在规划中，接口可通过 Swagger/Knife4j 查看。

```powershell
Copy-Item deploy/.env.example deploy/.env
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d
docker compose --env-file deploy/.env -f deploy/docker-compose.yml ps
```

启动后可访问：

| 组件 | 地址 |
| --- | --- |
| Nacos 控制台 | http://localhost:8848/nacos |
| RabbitMQ 管理台 | http://localhost:15672 |
| Elasticsearch | http://localhost:9200 |
| Nginx 入口 | http://localhost/healthz |
| MySQL | localhost:3306 |
| Redis | localhost:6379 |

Compose 会自动创建 `tj_*` 业务数据库，并把 `deploy/nacos/configs` 下的共享配置导入 Nacos。停止环境：

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.yml down
```

## 启动 Java 服务

先完成上面的基础设施启动，再在 IDE 中启动目标服务，并设置 `SPRING_PROFILES_ACTIVE=local`。本地 profile 默认访问 `localhost:8848`；如果 Java 服务也放进 Compose 网络，设置 `NACOS_SERVER_ADDR=nacos:8848`。

```powershell
mvn -DskipTests package
```

网关端口为 `10010`，业务服务端口范围为 `8081` 至 `8093`。完整端口和模块说明见 [docs/services.md](docs/services.md)。

如需把已构建的 Java 服务也放入 Docker 网络，先执行 Maven 打包，再使用服务编排文件：

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.yml -f deploy/docker-compose.services.yml up -d --build
```

服务编排保留原有 `tj-*` 模块和服务名，仅把容器内的 Nacos、MySQL、Redis、RabbitMQ、Elasticsearch 地址切换为 Compose 服务名。

## 截图与演示

真实业务页面截图/GIF 应在业务表结构恢复、核心服务可用后录制，建议至少展示：登录、课程检索、课程详情和学习记录。截图放在 `docs/screenshots/`，GIF 放在 `assets/readme/`，再把它们嵌入本 README。当前架构图是可发布的静态 SVG，不冒充业务页面。

## 已知边界与下一步

- `deploy/mysql/init/00-databases.sql` 只创建数据库，未伪造已删除虚拟机中的表结构和历史数据。
- 支付、短信、腾讯云媒资配置已改为环境变量占位符；需要使用时在 `deploy/.env` 注入，并建议轮换旧凭据。
- 当前机器未检测到 Docker CLI，因此本次只能完成 Compose 文件和配置静态验证，不能在此环境实际拉取镜像启动。
- 下一步按优先级恢复数据库 DDL，启动认证/用户/课程/学习/网关最小链路，补接口 smoke test，再录制截图/GIF；核心链路稳定后再部署在线 Demo。

## 阶段记录

### 第一阶段（本次）

- 项目对外名称统一为“知序学堂”，保留原有 `tianji`/`tj` 内部技术标识。
- 将本地 profile 从失效虚拟机地址切换为可配置的本地 Nacos/Elasticsearch 地址。
- 新增 MySQL、Redis、RabbitMQ、Elasticsearch、Nacos 的 Docker Compose 基础设施和健康检查。
- 新增 Java 微服务 Docker 编排文件，支持 Maven 打包后加入同一 Compose 网络。
- 新增 Nacos 共享配置自动导入脚本与 MySQL 数据库初始化脚本。
- 清理仓库中的云服务、支付、短信和旧环境账号明文。
- 新增架构展示图和可复现启动文档。

### 第二阶段（待办）

- 恢复并验证业务表结构与演示数据。
- 启动最小核心服务链路，补健康检查和 API smoke test。
- 录制真实页面截图/GIF，完善简历项目说明。
- 选择部署平台并提供在线 Demo，增加演示账号和数据重置策略。

## License

仅用于学习和求职作品展示。
