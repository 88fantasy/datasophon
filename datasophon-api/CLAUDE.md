# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## datasophon-api

Datasophon 的 Master 主服务进程，Spring Boot 3.4.5。本模块既是 HTTP REST 后端（`/ddh/api/**`），也是 gRPC Server（端口 18081）。`docs/ARCHITECTURE.md` 第 3.2 节、第 4 章描述了本模块与 Worker / K8s Agent 的协议面。

### 常用命令

在仓库根目录（`/Users/huzekang/opensource/datasophon`）下用 `./mvnw`：

| 目的 | 命令 |
|---|---|
| 仅编译本模块及其依赖 | `./mvnw -pl datasophon-api -am compile` |
| 仅打包本模块（产出 `target/datasophon-manager-*.tar.gz`） | `./mvnw -pl datasophon-api -am package -DskipTests` |
| 仅运行本模块的单元测试 | `./mvnw -pl datasophon-api test` |
| 运行指定测试类 | `./mvnw -pl datasophon-api -Dtest=WorkerRegistryTest test` |
| 跳过测试 | `./mvnw -pl datasophon-api -DskipTests=true <phase>` |
| 启动本地进程（直连 MySQL） | `./mvnw -pl datasophon-api spring-boot:run` |
| 生成 gRPC stub（依赖 proto 改动时） | `./mvnw -pl datasophon-api -Pgenerate-proto generate-sources` |

`application.yml` 默认 HTTP 端口 8080、上下文 `/ddh`；gRPC 端口 18081；MySQL 走 `${mysql.ip:localhost}:${mysql.port:3306}` 占位符，本地启动前请准备 datasophon 库。

### 高层架构

**进程入口**：`DataSophonApplicationServer`（`@SpringBootApplication` + `@EnableScheduling` + `@EnableAsync`）。`@PostConstruct` 把本机 hostname 写入 `CacheUtils` 供 Worker 回调查找本机用。

**协议面**：
- HTTP REST：`/ddh/api/**`，由 `controller/` 各 `@RestController` 暴露，鉴权链在 `interceptor/`（`LoginHandlerInterceptor` / `UserPermissionHandler` / `BasicValidRequestInterceptor`），密码登录在 `security/PasswordAuthenticator`，免密 LDAP 可在 `security.authentication.type` 切换。
- gRPC Server：端口 18081（`grpc-server-spring-boot-starter`），Netty 内嵌，与 Jetty 8080 独立。两类服务挂在同一端口：
  - `WorkerRegistryGrpcService` 接收 Worker `Register / Heartbeat / Unregister`。
  - `MasterCallbackGrpcService` 接收 OLAP 反向注册（`MasterCallbackService.RegisterOlapNode`）。
  - stub 与消息类全部来自 `datasophon-grpc-api` 依赖，本模块不重新生成。

**编排核心**（`master/`，Pekko 移除后的本地编排）：

- `master/DAGExecutor`：物理机集群 DAG 调度入口，依赖 `dag/repo/SimpleDAGRepository` 维护拓扑。
- `master/K8SDAGExecutor`：K8s 集群 DAG 调度入口。
- `master/handler/{host,service,k8s}/`：步骤化 Handler 链。Host 侧含 `DispatcherWorkerHandlerChain`（MD5 → Decompress → InstallJDK → Upload → Start）；Service 侧含 `ServiceInstallHandler` / `ServiceConfigureHandler` / `ServiceStartHandler` / `ServiceStopHandler` / `ServiceStatusHandler` / `ServiceUpgradeHandler`；K8s 侧含 Helm apply / agent install-uninstall / restart / stop。
- `master/service/DispatcherWorkerService`：从 gRPC 拿到 WorkerEndpoint 后构造命令并经 `master/transport/GrpcWorkerCallAdapter` 下发。
- `master/MasterScheduledService`：原 Pekko `actorSystem.scheduler().scheduleWithFixedDelay()` 替换为 Spring `@Scheduled`，三个巡检周期——节点 30s/300s、服务角色 15s/30s、集群状态 30s/60s。
- `master/service/{HostCheckService,ClusterStatusService,ServiceCommandService,WorkerStartService,MasterNodeProcessingService,HdfsECService,ClusterDeleteService,RackService,AlertService,HostConnectService,DispatcherK8sAgentService}`：领域服务。

**gRPC 客户端**（`grpc/`）：
- `WorkerRegistry`：内存 `hostname → WorkerEndpoint` 映射；心跳 > 90s（= 3×30s 间隔）发布 `WorkerOfflineEvent`。
- `WorkerRegistryPrewarmer`：`@PostConstruct` 从 DB 加载已知主机列表预填注册表，给 Worker 90s 窗口完成真实注册（防 Master 重启窗口期）。
- `WorkerCommandClient`：按 hostname 懒建并缓存 gRPC Channel；监听 `WorkerOfflineEvent` 主动 `channel.shutdown()`，避免句柄泄漏。
- `MasterCallbackGrpcService`：接收 Worker 上报 OLAP 节点并路由到 `MasterNodeProcessingService`。

**DAG 模型**（`dag/`）：`NodeTask` / `AsyncNodeTask` / `DAGListener` / `NodeExecutionCallback` 是执行抽象；`model/{DagDefinition,NodeDefinition,EdgeDefinition}` 是拓扑定义；`repo/{DAGRepository,SimpleDAGRepository}` 是拓扑仓库。`DAGListener` 串起执行器与回调。

**安全**（`security/`）：`SecurityConfig` 装配过滤器链，`AuthenticationType` 切换 PASSWORD / LDAP，`UserPermission` 处理 RBAC，`UserPermissionHandler` 拦截菜单级权限。

**配置**（`configuration/`）：`AppConfiguration` 注册拦截器 + 静态资源；`DatabaseMigrationAware`（实现 `Ordered`）作为最早期的 Bean 触发 `migration/DatabaseMigration` 跑 SQL 脚本；`GrafanaProxyConfiguration` 用 Jetty 代理转发 Grafana Web UI；`MasterAsyncConfig` 配置 `@Async` 线程池；`OpenApiConfiguration` 暴露 knife4j 文档。

**策略**（`strategy/`）：`ServiceRoleStrategy` 接口 + `ServiceRoleStrategyContext` 按角色名分派；具体策略如 `NameNodeHandlerStrategy` / `RMHandlerStrategy` / `KafkaHandlerStrategy` / `KAdminHandlerStrategy` 等约 30 个，对应 `Worker` 侧的同名策略类。

**元数据加载**（`load/`）：`Application` 持有 `ApplicationContext` 静态引用（替代历史 Pekko 反射取 Bean）；`LoadServiceMeta` 是 `ApplicationRunner`，启动时把 `meta/datacluster/**/service_ddl.json` 解析到内存（`ServiceInfoMap` / `ServiceConfigMap` / `ServiceRoleMap` / `ServiceConfigFileMap` / `ServiceRoleJmxMap`），`GlobalVariables` 暴露运行时变量。

**异常/日志**：`exceptions/ApiExceptionHandler` 是全局 `@RestControllerAdvice`；`log/{TaskLogFilter,TaskLogDiscriminator}` 区分任务日志 MDC。

### 元数据驱动（service_ddl.json）

`src/main/resources/meta/datacluster/<SERVICE>/service_ddl.json` 是 Datasophon "声明式服务" 的核心。同名服务还有 `script/` 子目录放安装/启动/停止/状态脚本。

JSON 字段决定：
- UI 安装向导：根 `name / label / description / version`、各 `role.cardinality`（`1+` / `1` / `N+`）、`configFields` 列表生成前端表单。
- 调度依赖：`dependencies` 数组决定安装顺序，调度器在 `master/DAGExecutor` 中据此建图。
- 启停命令：每个 `role` 的 `startRunner / stopRunner / statusRunner.program` + `args`，运行时由 `strategy/ServiceRoleStrategyContext` 派发到具体策略类。
- 校验与占位符：`configFields` 中 `${VAR}` 表达式在生成配置文件时由 `load/LoadServiceMeta` 解析。

新增一种服务的工作流（参考 `HDFS` / `KAFKA` 的 `service_ddl.json`）：

1. `src/main/resources/meta/datacluster/<SERVICE>/service_ddl.json` 写完整字段。
2. `script/` 放控制脚本（`control_*.sh`），被 JSON 的 `program` 引用。
3. `strategy/` 下新增对应 `*HandlerStrategy.java`（仅当该角色需要特殊逻辑；通用角色可复用 `ServiceHandlerAbstract`）。
4. `controller/FrameServiceController` 暴露给前端：服务列表由 `LoadServiceMeta` 自动注入，无需改 controller；如需新增操作类，参考 `controller/cluster/ClusterServiceCommandController`。
5. `db/migration/<next-version>/` 加 DDL/DML 增量（命名见下）。
6. worker 侧同步：`datasophon-worker` 也要有同名策略类，因为实际执行在 Worker 端。

### 数据库迁移（自研，非 Flyway）

`db/migration/<version>/` 按版本号分子目录，**不是 Flyway 的 `db/migration/V__*.sql` 平面结构**，也没有 `flyway-core` 依赖。执行器是 `migration/DatabaseMigration`（由 `configuration/DatabaseMigrationAware` 触发），脚本执行顺序与 `ScriptType` 枚举一致。

- 命名约定：`<version>/V<version>__DDL.sql` + `V<version>__DML.sql`。
- 已有目录：`1.1.0` / `1.1.1` / `1.1.2` / `1.2.0` / `1.2.1` / `1.2.2` / `2.0.0` / `2.1.0`。
- `pom.xml` 配 `maven-jar-plugin` 排除 `db/**`（避免打到 jar 里重复）。
- 开关：`application.yml` 的 `datasophon.migration.enable`，生产默认 `true`。
- 注意：测试配置注释里出现 "Flyway" 字样（`src/test/resources/application-integration.yml`）是历史命名残留，实际跑的是 `DatabaseMigration`。

### 关键约定 / 陷阱

- **Pekko 已完全移除**（2026-05-23 commit `d0b93b09`）。不要新增 `ActorRef` / `ActorSystem` 概念，所有跨节点通信走 gRPC（`grpc/`），所有本地并发/周期任务走 Spring `@Async` / `@Scheduled`。
- **gRPC stub 来自 `datasophon-grpc-api`**（已 checked-in），不要在本模块 `protoc` 生成。proto 改动只在 `datasophon-grpc-api`，本模块 `compile` 不必 `-Pgenerate-proto`。
- **REST 入口分层**：`controller/<topic>/<Entity>Controller.java` 暴露 REST，**接口**在 `service/<Entity>Service.java`，**实现**统一放 `service/impl/<Entity>ServiceImpl.java`（不要在 `service/` 直接放实现类）。子包按业务域切分（`cluster/`、`cmd/`、`extrepo/`、`frame/`、`instance/`、`log/`）。
- **DAG 步骤实现**：新加编排步骤时优先在 `master/handler/{host,service,k8s}/` 加 Handler 链节点；不要绕过 `DispatcherWorkerHandlerChain` 直接发命令。
- **`Application.getBean`**（`load/Application`）是历史 Pekko 时代静态取 Bean 的等价物，新代码优先用构造器注入；只在框架尚未启动完、Bean 不可见时才用。
- **`WorkerRegistryPrewarmer`** 是 `@PostConstruct` 预填——新加任何"Master 重启后立刻向 Worker 派命令"的逻辑前，先确认预热窗口覆盖到对应 hostname，否则会出现"Master 重启 → 命令全部失败"。
- **`@Transactional` 与 `@Async` 冲突**：`master/service/` 下凡是 `@Async` 方法都不要再叠加事务注解；如需事务，挪到被 `@Async` 方法同步调用的内部方法上。
- **i18n**：`i18n/messages*.properties` 三语种，新增 UI 文案时三个文件都要加。
- **打包**：`assembly.xml` + `maven-assembly-plugin` 产 `target/datasophon-manager-<version>.tar.gz`，最终 `tar` 包含 `lib/`、`conf/`、`bin/`、`LICENSE`、`README.md`、前端 `front/`。`maven-jar-plugin` 排除 `db/**`，别把 SQL 打进 jar。
- **不要改 `server.servlet.context-path`**：前端硬编码 `/ddh`，`security` 拦截链也基于此路径前缀。
- **gRPC 心跳 SSOT** 在 `datasophon-grpc-api/.../GrpcConstants`（`HEARTBEAT_INTERVAL_SECONDS=30`、`HEARTBEAT_TIMEOUT_SECONDS=90`、`MASTER_GRPC_PORT=18081`）；本模块不重复定义，引用即可。
- **Worker 离线事件** `WorkerOfflineEvent` 必须被 `WorkerCommandClient` 监听到并 shutdown Channel；新加任何"移除 Worker"的路径都要确认它最终会触发该事件（直接 `registry.unregister` 或 `registry.remove`），否则会泄漏 gRPC Channel 句柄。

### 测试

- 默认行为：`pom.xml` 中 surefire `skipTests=${skipTests}`，**不传参时执行测试**，传 `-DskipTests=true` 跳过。父 pom 不再硬编码跳过。
- 强制排除（pom 中已配 `<excludes>`）：
  - `MetaUtilsTaskTest`：本机路径依赖。
  - `NexusUtilsTaskTest`：本机 Nexus 仓库路径依赖。
  任何对这两个文件的修改都不会被 surefire 跑——CI 上等同于不存在。
- 集成测试：`@ActiveProfiles("integration")` + `src/test/resources/application-integration.yml`，连真实 MySQL（`127.0.0.1:3306/datasophon`，账号 `root/localmysql`），同时跑 `datasophon.migration.enable=true`。CI 本地无 MySQL 时请用 `-Dtest=...` 只跑纯单元测试。
- 测试文件位置：
  - `src/test/java/com/datasophon/api/grpc/WorkerRegistryTest.java`：纯 Java 单元测试，覆盖注册/心跳/注销/超时与 `WorkerOfflineEvent` 时机。**新增 gRPC 行为改动时请同步此测试。**
  - `src/test/java/com/datasophon/api/grpc/WorkerCommandClientTest.java`：用 `grpc-inprocess` 走 in-process Channel，无需真实 TCP。
  - `src/test/java/com/datasophon/api/DataSophonApplicationServerTest.java`、`DataSophonMySQLStartupTest.java`：Spring 上下文启动测试。
  - `src/test/java/com/datasophon/api/utils/task/`：上面两个被排除的遗留测试 + `PropertiesPathUtils` 工具类。
- 目录内**没有** `.http` 接口测试文件，新增 REST 接口时不要找这种位置存放请求样例；如需样例，放到 `datasophon-api/src/test/resources/` 下新建 `*.http`（当前不存在，先例）即可。
