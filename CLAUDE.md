# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 1. 仓库概览

Datasophon 是大数据 / 云原生平台自动化部署与运维管理系统,目标是在一组裸机或虚拟机上,以"控制面 + 工作面"的方式自动完成节点初始化(防火墙、JDK、Docker、K8s 基础环境、镜像仓库、MySQL/NTP 等基础设施)、大数据/云原生服务部署(Hadoop、Spark、Flink、Hive、Doris、Kafka、Zookeeper、Kubernetes 工作负载等 27+ 个内置服务)、服务的启停、配置下发、健康巡检、告警、日志聚合与集群级 DAG 编排变更管理。它通过一组声明式服务元数据(`meta/datacluster/<SERVICE>/service_ddl.json`)驱动安装策略,做到「新增一种服务 ≈ 写一份 DDL + 一个策略类」。

技术栈摘要:Java 17 + Spring Boot 3.4.5、Go 1.21、React 19、gRPC 1.68.1 / Protobuf 3.25.5、MyBatis-Plus 3.5.9、Druid、Flyway 9、fabric8 kubernetes-client。详细见 [README.md](./README.md) 与 [docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md)。

## 2. 模块矩阵

| 模块 | 路径 | 角色 | 运行形态 / 产物 | 端口 |
|---|---|---|---|---|
| `datasophon-api` | `datasophon-api/` | Master 主服务,集群编排核心 | Spring Boot 进程;`target/datasophon-manager-<version>.tar.gz`(assembly 内嵌前端 `dist/`) | HTTP `8081`(`/ddh`)、gRPC `18081` |
| `datasophon-worker` | `datasophon-worker/` | Worker 节点进程,本地执行安装/启动/停止等命令 | 每节点 1 个 `main()` 进程(`WorkerApplicationServer`),非 Spring Boot;`target/datasophon-worker.tar.gz` | gRPC `18082`、JMX exporter `8585` |
| `datasophon-grpc-api` | `datasophon-grpc-api/` | gRPC proto 契约 + 共享 stub | 纯库,无进程;stub 已 checked-in | — |
| `datasophon-common` | `datasophon-common/` | 公共库(K8s 客户端、命令模型、Nexus 客户端、序列化) | 纯库,无进程;不引入 Spring Web | — |
| `datasophon-cli-go` | `datasophon-cli-go/` | 节点初始化 CLI(Go 重写,替代旧 Java CLI) | Go 单二进制 `datasophon-cli`;`dist/datasophon-cli-{linux,darwin}-{amd64,arm64}` | — |
| `datasophon-k8s-agent` | `datasophon-k8s-agent/` | K8s 内 Agent,RSA 签名鉴权远端执行 | Spring Boot Web Pod;Helm Chart + Docker 镜像 | HTTP `12552`(可由 Helm 配 NodePort `32552`) |
| `datasophon-ui` | `datasophon-ui/` | 前端(React 19 + Antd 6 + Antd Pro 2.8) | 静态资源;`pnpm build` 产出 `dist/`,由 Maven `frontend-maven-plugin` 内嵌 | 开发服务器 `5180` |
| `datasophon-assembly` | `datasophon-assembly/` | Maven 顶层 assembly 打包模块 | 纯 `pom` 模块,无 Java 源码;`target/datasophon-<version>-package.tar.gz` | — |

> 顶层 `pom.xml` 当前模块列表:`datasophon-common` → `datasophon-grpc-api` → `datasophon-worker` → `datasophon-cli-go` → `datasophon-ui` → `datasophon-api` → `datasophon-k8s-agent` → `datasophon-assembly`(后者 `package` 依赖前面所有 `tar.gz` 产物)。

## 3. 常用命令

所有 Maven 命令均从仓库根目录用 `./mvnw`(Maven Wrapper 3.8.4,见 `.mvn/wrapper/`)。`JAVA_HOME` 必须指向 JDK 17。

### 3.1 全量 / 后端

```bash
# 全量构建(编译 + 打包 + 内嵌前端)
./mvnw clean package -DskipTests

# 仅后端(前端不参与)
./mvnw clean package -DskipTests -pl datasophon-api -am

# 单元测试
./mvnw test

# 代码格式化(Spotless,2.27.2)
./mvnw spotless:apply

# 国内网络加速
./mvnw -Pgoogle-mirror <phase>
```

### 3.2 单模块构建(节省时间)

```bash
# 仅本模块 + 依赖联编
./mvnw -pl <module> -am <phase>

# 例:仅 worker
./mvnw -pl datasophon-worker -am clean package -DskipTests

# 仅 api(会顺带构建 datasophon-ui 并内嵌 dist)
./mvnw -pl datasophon-api -am clean package -DskipTests

# 仅 grpc stub(无 protoc,直接 compile 即可)
./mvnw -pl datasophon-grpc-api -am compile

# proto 改动后重新生成 stub
./mvnw -pl datasophon-grpc-api -am generate-sources -Pgenerate-proto
```

### 3.3 前端(`datasophon-ui`)

包管理器固定为 `pnpm@10.11.0`,不要使用 npm / yarn / bun。

```bash
cd datasophon-ui
pnpm install         # 安装依赖
pnpm dev             # 启动 Vite 开发服务器(端口 5180,base = /ddh)
pnpm build           # 生产构建,产物输出到 dist/(assetsDir=static)
pnpm lint            # ESLint
pnpm test            # Vitest
pnpm test run        # 单次跑全部测试
pnpm test:coverage   # v8 覆盖率
```

> Maven 链路上:仓库根 `./mvnw -pl datasophon-ui -am package` 会让 `frontend-maven-plugin` 自动下载 Node `v20.19.2` + npm `10.8.2` 并跑 `npm run build`,把 `dist/` 同步到父项目 `static/`。本机已装 pnpm 时可忽略该行为,但 CI 上依赖它。

### 3.4 CLI(`datasophon-cli-go`)

模块自带 `Makefile`,也可直接 `go build`。Go 版本要求 `1.21+`。

```bash
cd datasophon-cli-go
make build           # 当前平台二进制 → dist/datasophon-cli
make release         # 交叉编译 4 个目标到 dist/(linux/darwin × amd64/arm64)
make test            # go test ./...
make vet             # go vet ./...
go test -cover ./... # 测试 + 覆盖率

# 手工交叉编译
GOOS=linux  GOARCH=amd64 go build -o dist/datasophon-cli-linux-amd64  ./cmd/datasophon-cli
GOOS=linux  GOARCH=arm64 go build -o dist/datasophon-cli-linux-arm64  ./cmd/datasophon-cli
```

> CLI 命令名前置环境变量 `DDH_HOME`,为空直接 `Exit(1)`。子命令 `create cluster [plan|apply]` 支持 plan/apply 两阶段与断点续跑;全局 `--dry-run` 只打印不执行。

### 3.5 跑起来(默认账号 / 端口)

| 项 | 值 |
|---|---|
| 默认账号 | `admin` / `admin123` |
| API HTTP | `8081`,上下文 `/ddh` |
| Master gRPC | `18081` |
| Worker gRPC | `18082` |
| MySQL | `3306`(`application.yml` 中 `${mysql.ip:localhost}:${mysql.port:3306}` 占位) |

最便捷:`docker build -t datasophon/datasophon:dev . && docker run -d -p 8081:8081 --name datasophon datasophon/datasophon:dev`,浏览器访问 `http://host:8081/ddh`。详见 README 4.4 节与 [deploy/Deployment.md](./deploy/Deployment.md)。

## 4. 顶层架构鸟瞰

Datasophon 是典型的「控制面 + 工作面」结构:

- **Master 控制面** = `datasophon-api`(Spring Boot 进程,同时跑 HTTP `8081/ddh` 与 gRPC `18081`)。包含 `master/DAGExecutor`(物理集群 DAG 调度)、`master/K8SDAGExecutor`(K8s 集群 DAG 调度)、`master/MasterScheduledService`(`@Scheduled` 巡检,30s/300s 节点存活、15s/30s 角色状态、30s/60s 集群状态)、`grpc/WorkerRegistry`(内存 `hostname → WorkerEndpoint` 注册表 + 90s 心跳超时)、`grpc/WorkerCommandClient`(按 hostname 懒建/缓存 gRPC Channel,监听 `WorkerOfflineEvent` 主动 shutdown 避免句柄泄漏)、`load/LoadServiceMeta`(`ApplicationRunner` 把 `meta/datacluster/**/service_ddl.json` 解析入库)。
- **Worker 工作面** = `datasophon-worker`(每节点 1 个非 Spring Boot 的 `main()` 进程,监听 gRPC `18082`)。包含 `grpc/WorkerGrpcServer`(有界线程池 `max(8, 2 * cores)`)、`grpc/WorkerCommandGrpcService`(分发到 `strategy/ServiceRoleStrategyContext`)、`grpc/MasterRegistryClient`(30s 心跳 + 失败重注册)、`grpc/MasterCallbackClient`(OLAP 反向回调静态单例)、`strategy/*HandlerStrategy`(约 30 个服务角色策略,实现 `ServiceRoleStrategy.handler(ServiceRoleOperateCommand)` 统一接口)。
- **CLI 工具** = `datasophon-cli-go`(Go 1.21 + Cobra),通过 SSH/sftp 初始化裸机节点,采用 plan/apply 两阶段与持久化 `state/initALL.plan.json`,33 个 Step 声明式定义,`clusterHash` 校验配置一致性。
- **K8s Agent** = `datasophon-k8s-agent`(Spring Boot Web Pod),Master 持 RSA 私钥,Agent 持公钥;`timestamp + nonce` + `RSA-SHA256` 签名,`replay window` 防重放;`/api/v1/health`、`/api/v1/ready` 不走签名。
- **元数据驱动**:每种服务 = 一份 `meta/datacluster/<SERVICE>/service_ddl.json`(参数、模板、角色拓扑、告警) + 一个 Worker 端 `*HandlerStrategy` 类;DDL 决定 UI 表单与 DAG 依赖,策略类承担运行期动作。
- **跨进程通信契约** 在 `datasophon-grpc-api`(4 个 `.proto`:`common` / `registry` / `worker` / `master`),stub 已 checked-in,日常 `./mvnw compile` 不必重跑 protoc。

详细架构图、时序图与关键文件速查见 [docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md) 第 2-9 章。

## 5. 模块索引(深入前必读)

| 模块 | 模块级 CLAUDE.md |
|---|---|
| `datasophon-api` | [datasophon-api/CLAUDE.md](./datasophon-api/CLAUDE.md) |
| `datasophon-worker` | [datasophon-worker/CLAUDE.md](./datasophon-worker/CLAUDE.md) |
| `datasophon-grpc-api` | [datasophon-grpc-api/CLAUDE.md](./datasophon-grpc-api/CLAUDE.md) |
| `datasophon-common` | [datasophon-common/CLAUDE.md](./datasophon-common/CLAUDE.md) |
| `datasophon-cli-go` | [datasophon-cli-go/CLAUDE.md](./datasophon-cli-go/CLAUDE.md) |
| `datasophon-k8s-agent` | [datasophon-k8s-agent/CLAUDE.md](./datasophon-k8s-agent/CLAUDE.md) |
| `datasophon-ui` | [datasophon-ui/CLAUDE.md](./datasophon-ui/CLAUDE.md) |
| `datasophon-assembly` | [datasophon-assembly/CLAUDE.md](./datasophon-assembly/CLAUDE.md) |

> 路径以仓库根为基址(本机绝对路径示例:`/Users/huzekang/opensource/datasophon/datasophon-api/CLAUDE.md`)。涉及具体模块的内部包结构、命令流程、测试策略、命令清单都写在对应模块级文档中,**本根文档不再重复**。

## 6. 跨模块关键约定(踩坑高发区)

- **Pekko/Actor 已于 2026-05-23 完全移除**(commit `d0b93b09`)。Master↔Worker 跨进程通信一律走 gRPC;Master 内部本地并发/周期任务一律走 Spring `@Async` / `@Scheduled`。**禁止新增** `ActorRef` / `ActorSystem` 概念。`datasophon-common` 中仍存在历史命名残留(`AkkaRemoteReply` / `AkkaRemoteException` 等),删除前必须 ripgrep 跨模块确认无引用。
- **gRPC 端口/心跳 SSOT** 在 `datasophon-grpc-api/.../GrpcConstants`:`MASTER_GRPC_PORT=18081`、`WORKER_GRPC_PORT=18082`、`HEARTBEAT_INTERVAL_SECONDS=30`、`HEARTBEAT_TIMEOUT_SECONDS=90`。两端引用,禁止各自硬编码。
- **元数据驱动新增服务** 工作流:① `datasophon-api/src/main/resources/meta/datacluster/<SERVICE>/service_ddl.json` 写完整字段(parameters / configWriter / roles / dependencies / prometheus / alert);② `datasophon-worker/strategy/` 实现 `*HandlerStrategy`(必要时 `@Override` `handler(...)` 走 `command.getCommandType()` 分支);③ UI 通过 `LoadServiceMeta` 自动渲染表单,无需改 controller。
- **二进制名 `datasophon-cli` 不重命名**:跨语言重写不改命令名,Java CLI 兼容路径、`/usr/local/bin/` 软链、systemd 单元、文档中的命令示例都依赖此名。
- **Java 17 强制**(父 pom `maven-compiler-plugin` `<release>17</release>`);Lombok 强依赖(`<scope>provided</scope>`,IDE 需装插件);数据库迁移自 1.1.0 演进到 2.1.0,新增 DDL/DML 放 `db/migration/<version>/V<version>__DDL.sql` + `V<version>__DML.sql`(注意:此迁移不是 Flyway,而是 `datasophon-api/.../migration/DatabaseMigration` 自研执行器;`flyway-core` 不在依赖中,集成测试配置里残留的 "Flyway" 字样是历史命名)。
- **前端构建链路**:`datasophon-ui` 的 `pnpm build` 产出 `dist/` → Maven `frontend-maven-plugin` 自动跑 `npm run build` → `maven-resources-plugin` 把 `dist/` 同步到父项目 `static/` → `datasophon-api` 的 `maven-assembly-plugin` 将整个 `static/` 打入 `datasophon-manager-<version>.tar.gz` 的 `front/` 目录。
- **`datasophon-ui` 三者对应**:Maven `artifactId = datasophon-ui`、`package.json` 中 `name = "ddh"`(被 `vite.config.ts` 取作 `base = /ddh`)、后端 `server.servlet.context-path=/ddh`。改任一处都要同步另两处,否则静态资源 404 或拦截链失效。
- **Master 启动预热**:`WorkerRegistryPrewarmer` 在 `@PostConstruct` 从 DB 加载已知主机预填注册表,给 Worker 90s 窗口完成真实注册。**新加任何"Master 重启后立刻向 Worker 派命令"的逻辑前,先确认预热窗口覆盖到对应 hostname**,否则会出现"Master 重启 → 命令全部失败"。
- **Worker 启动顺序**(`WorkerApplicationServer.main`):`startNodeExporter` → `createDefaultUser` → `WorkerGrpcServer.start()` → `MasterCallbackClient.init()` → `MasterRegistryClient.register()` → `awaitTermination`。顺序不可换;OLAP 策略的 `MasterCallbackClient.getInstance()` 必须在 register 前 init。
- **Worker 离线事件**:任何"移除 Worker"的路径都必须最终触发 `WorkerOfflineEvent`(`registry.unregister` / `registry.remove`),由 `WorkerCommandClient` 监听并 `channel.shutdown()`,否则会泄漏 gRPC Channel 句柄。
- **测试默认行为**:根 `pom.xml` surefire `skipTests=${skipTests}`,**不传参时执行测试**;父 pom 不再硬编码跳过。`-DskipTests=true` 跳过。`datasophon-api` 强排除 `MetaUtilsTaskTest` / `NexusUtilsTaskTest`(本机路径依赖,CI 上等同于不存在)。
- **Maven Central 国内镜像**:根 pom 配 `google-mirror` profile(`-Pgoogle-mirror`),直连 Maven Central 失败时优先切到 GCS 镜像。

## 7. MCP 工具

仓库根 `.mcp.json` 声明了 `codegraph` stdio MCP 服务;当前会话还接入了以下 MCP:

- **codegraph**(`.mcp.json`):本地代码图谱查询,跨文件引用、调用链、模块依赖分析时使用,优于手写 ripgrep 多次扫描。
- **context7**:遇到 React、Vite、Spring Boot、gRPC、fabric8、Cobra 等库的 API/版本/迁移问题时优先调用,训练数据可能滞后于最新版本。
- **fetch**:访问外网/抓取 URL 内容(README、issue、文档站)。
- **paddleocr / PP-OCRv5 / PP-StructureV3**:对图片或 PDF(部署手册截图、PDF 文档)做 OCR 或结构化抽取;截图里出现表格/公式时用 PP-StructureV3。

## 8. 贡献流程(摘自 README 第九节)

1. **Fork** 仓库,基于 `dev`(或当前活跃分支)新建 feature 分支。
2. 提交前本地跑:
   ```bash
   ./mvnw spotless:apply         # 后端格式化
   ./mvnw test                    # 单元测试
   cd datasophon-ui && pnpm lint # 前端 lint
   ```
3. **提交信息遵循 [Conventional Commits](https://www.conventionalcommits.org/)**(本仓库历史 commit 也按此风格,如 `fix(package): 修复 ...`、`docs(package): 添加 ...`)。
4. 创建 PR 并描述动机、改动范围、测试方式。

## 9. 顶层目录速记

```
datasophon/
├── pom.xml                       # 父 pom(模块列表、依赖管理、版本 ${revision})
├── mvnw / .mvn/                  # Maven Wrapper(3.8.4)
├── style/                        # Spotless + Checkstyle 配置(license_header / formatter / importorder)
├── docs/ARCHITECTURE.md          # 架构与关键文件速查
├── datasophon-api/               # Master 主服务
├── datasophon-worker/            # Worker 节点进程
├── datasophon-grpc-api/          # gRPC proto + stub
├── datasophon-common/            # 公共库
├── datasophon-cli-go/            # Go CLI(替代旧 datasophon-cli)
├── datasophon-cli/               # 旧 Java CLI(历史遗留,逐步淘汰)
├── datasophon-k8s-agent/         # K8s 内 Agent
├── datasophon-ui/                # 前端
├── datasophon-assembly/          # 顶层 assembly 打包
├── deploy/                       # compose / docker / k8s 部署资产
├── .mcp.json                     # MCP 服务声明
└── CLAUDE.md                     # 本文件
```
