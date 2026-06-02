# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## datasophon-worker

每个被纳管节点上各跑 1 个的 Worker 进程。**纯 `main()` 启动，不是 Spring Boot**，依赖刻意保持轻量（见 `pom.xml`：只有 `spring-expression`、`logback`、`grpc-netty-shaded`、`jackson-databind`、`freemarker`、`hadoop-common`、`nacos-client`、AWS S3 等，没有 `spring-boot-starter`）。

### 常用命令

```bash
# 只编译本模块（含依赖模块联编，根目录执行）
mvn -pl datasophon-worker -am clean package -DskipTests

# 只跑本模块测试（surefire 限定 includes：**/grpc/**Test.java，其他遗留测试已排除）
mvn -pl datasophon-worker test

# 产物：target/datasophon-worker.tar.gz + .md5（由 maven-assembly-plugin 按
# src/main/resources/assembly.xml 装配，含 conf/、lib/、bin/、jmx/、node/、templates/、script/）
```

启动脚本 `src/main/resources/datasophon-worker.sh` 由 assembly 拷贝到 `bin/`，由 Master 通过 SSH 在受管节点上以 `worker start` 调起，最终执行 `java ... com.datasophon.worker.WorkerApplicationServer`。JMX exporter 监听 8585。

### 高层架构

启动顺序见 `WorkerApplicationServer.java:59`（main 方法），顺序**不可随意调换**：

1. `startNodeExporter` —— 拉起 Prometheus node_exporter（x86/arm 自动选择）。
2. `createDefaultUser` —— 用 `UnixUtils` 创建 `hdfs/yarn/hive/...` 系统用户。
3. `new WorkerGrpcServer().start()` —— 监听 **gRPC 端口 18082**，注册 `WorkerCommandGrpcService`（一元 RPC，分发到策略类）。线程池由 `WorkerGrpcServer.java:53` 显式 bound：`max(8, 2 * availableProcessors())`（gRPC-Java 默认是 CachedThreadPool 无界，批量 install 会创建数百线程）。
4. `MasterCallbackClient.init(masterHost)` —— **必须在 register 之前** 初始化反向回调单例，否则 OLAP（StarRocks/Doris FE/BE）相关策略会因取不到 `getInstance()` 而降级。
5. `MasterRegistryClient.register()` —— 走 `WorkerRegistryServiceGrpc` 向 Master `18081` 端口注册节点；注册成功（或失败）后都会启动每 **30s** 一次的心跳（`GrpcConstants.HEARTBEAT_INTERVAL_SECONDS`）。心跳收到 `success=false` 时主动重新 `register()`，所以注册失败也不致命。
6. `workerGrpcServer.awaitTermination()` —— 主线程在此阻塞，防止 JVM 因无非守护线程而退出。

`grpc/` 四个文件分工：

- `WorkerGrpcServer.java` —— gRPC server（被动接受 Master 指令）。
- `WorkerCommandGrpcService.java` —— 一元命令分发入口，反序列化 `ServiceRoleRequest.json_payload`（Jackson）→ 查 `ServiceRoleStrategyContext.getServiceRoleHandler(roleName)` → 调用策略。
- `MasterRegistryClient.java` —— 注册 + 心跳 client（Worker→Master）。
- `MasterCallbackClient.java` —— **反向回调** client（Worker→Master），静态单例。策略类（如 FE/BE）需要让 Master 异步执行某些动作（例如把当前节点加入 OLAP 集群）时，调用 `MasterCallbackClient.getInstance().registerOlapNode(...)`。

### 服务角色策略：新增一种 role

所有角色的 install/start/stop/restart/configure 行为都由 `strategy/` 下的 `*HandlerStrategy` 类承担。`ServiceRoleStrategyContext.java:33` 是**静态注册表**（`ConcurrentHashMap<roleName, strategy>`），key 必须与 meta datacluster DDL 中 `roles[].name` 一致；不一致就会取到 `null`，`WorkerCommandGrpcService.java:260` 的分发就会失败。

新增角色步骤：

1. 在 `strategy/` 下创建 `XxxHandlerStrategy.java`，继承 `AbstractHandlerStrategy`（自动按 `serviceName/serviceRoleName` 创建独立 logger，配合 `log/TaskLogDiscriminator` 把日志切分到独立文件供 Master 拉取），实现 `ServiceRoleStrategy`。
2. `ServiceRoleStrategy.java:32` 提供了默认 `handler(ServiceRoleOperateCommand)`：直接转交 `ServiceHandler.start(...)`。如果角色需要前置动作（如 NameNode 的 `-format`/`-bootstrapStandby`、HDFS 启用 Ranger plugin、下载 Kerberos keytab、回调 Master），就 `@Override` 它，参考 `NameNodeHandlerStrategy.java`。
3. 在 `ServiceRoleStrategyContext` static 块里 `map.put("XxxRoleName", new XxxHandlerStrategy("SERVICE", "XxxRoleName"))`。两个字符串都要和 meta DDL 对齐。

策略接口本身只有 `handler(ServiceRoleOperateCommand)` 这一个方法 —— 没有独立的 `handlerConfig/handlerInstall/handlerStart/handlerStop/getLog`；安装、启动、状态检查的分支判断通过 `command.getCommandType()`（`CommandType.INSTALL_SERVICE` 等）在 `handler` 内部展开，启动/状态命令字符串由 Master 传入的 `command.getStartRunner()/getStatusRunner()` 提供，最终交给 `handler/ServiceHandler.java` 执行 shell。

### 关键约定与陷阱

- **不是 Spring Boot 进程**：不要在 worker 模块加 `@Autowired/@Component/@Service`，没有容器扫描。`MasterCallbackClient` 用静态单例（`init()` + `getInstance()`）就是因为这个；策略类用 `new` 直接构造。
- **启动顺序敏感**：`WorkerGrpcServer.start()` → `MasterCallbackClient.init()` → `MasterRegistryClient.register()` 顺序不可换。Master 收到 register 后随时可能下发指令，gRPC server 必须先就绪；OLAP 角色的策略可能在 install 中就要回调 Master，所以 callback client 必须在 register 前 init。
- **gRPC 线程池有界**：`max(8, 2 * cores)`（`WorkerGrpcServer.java:53`）。批量 install 时请求会排队而非创建新线程；新增长耗时同步阻塞型 RPC 时要警惕饿死。
- **心跳即重注册**：心跳失败不会自动注销节点；Master 一旦返回 `success=false`（典型场景：Master 重启后丢失节点缓存）就会触发 `register()` 重新登记。`startHeartbeat()` 已做幂等保护（先 cancel 旧任务）。
- **`logger` 是实例字段不是 static**：`AbstractHandlerStrategy.java:46` 在构造器里按 `serviceName/serviceRoleName` 创建 logger，配合 `logback.xml` 里的 `TaskLogDiscriminator/TaskLogFilter` 把单次任务日志写到独立文件，Master 通过 gRPC 拉日志展示。子类要打日志必须用继承来的 `logger`，**不要新建** `LoggerFactory.getLogger(getClass())`。
- **明文 gRPC**：`usePlaintext()`，Phase 0 与原 Pekko 当前安全模型一致；如需 TLS 要同时改 Master/Worker 两侧。

### 资源处理（hook/resource/）

服务安装包/前端包/Shell 脚本/配置替换的"动作语义"在 `src/main/java/com/datasophon/worker/hook/resource/`（注意：是 `hook/resource/`，不是 `strategy/resource/`）：

- `DownloadStrategy` / `NexusResourceStrategy` —— 从 Master HTTP / Nexus 拉包。
- `ExecShellStrategy` / `ShellStrategy` —— 执行远端下发的 shell。
- `LinkStrategy` —— 创建软链（典型场景：版本切换、`/opt/datasophon/xxx -> xxx-3.3.4`）。
- `ReplaceStrategy` / `AppendLineStrategy` —— 配置文件原地修改。
- `FrontendStrategy` —— 前端静态资源同步。

这些策略由 `WorkerCommandGrpcService` 在收到资源类指令时分发；不要把它们和 `strategy/` 下的服务角色策略混为一谈，二者解决的是不同维度（角色生命周期 vs 资源动作）的问题。

### 关键文件速查

- `WorkerApplicationServer.java:59` —— main 入口，启动顺序的唯一权威。
- `grpc/WorkerCommandGrpcService.java:260` —— roleName → 策略 分发点。
- `grpc/WorkerGrpcServer.java:53` —— gRPC 线程池上界定义。
- `grpc/MasterRegistryClient.java:168` —— `startHeartbeat()` 幂等保证。
- `grpc/MasterCallbackClient.java:71` —— 静态单例 `init()`，OLAP 反向回调入口。
- `strategy/ServiceRoleStrategyContext.java:33` —— 全部角色注册表。
- `strategy/AbstractHandlerStrategy.java:42` —— 按角色名生成 logger，对接日志切分。
- `src/main/resources/datasophon-worker.sh` —— 启动脚本（JVM 参数、JMX agent、classpath）。
- `src/main/resources/logback.xml` —— 任务日志按角色切分的关键配置。
- `src/main/resources/assembly.xml` —— `tar.gz` 装配描述。
