# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## datasophon-common

Datasophon 的公共依赖库,沉淀 K8s 客户端封装、跨进程命令模型、Nexus 仓库客户端、序列化工具、枚举与常量等可在多模块复用的内容。本模块本身不打包成可运行进程,只作为 Maven 依赖被上层模块引用。

### 常用命令

只构建本模块(跳过测试):

```bash
./mvnw -pl datasophon-common -am clean install -DskipTests
```

仅编译验证:

```bash
./mvnw -pl datasophon-common compile
```

清理本模块产物:

```bash
./mvnw -pl datasophon-common clean
```

> 模块通过父 pom 继承版本与依赖管理(`${revision}`),不要在本模块 pom 中直接声明版本号。

### 职责范围

- **运行形态**:纯库,无 `main`、无 Spring Boot 进程、不监听端口。
- **被谁依赖**:`datasophon-api`(Master)、`datasophon-worker`(Worker)、`datasophon-k8s-agent`(K8s Agent)共同依赖本模块,作为它们之间共享数据结构与工具的 SSOT。
- **不依赖什么**:不引入 Spring Web / Spring Boot Starter / MyBatis-Plus 等上层框架(仅可使用 `jakarta.validation-api` 之类的注解 API)。Hadoop / Kerberos / Bouncycastle 等重型依赖只服务于工具类,不应渗透到调用方的运行时假设。

### 主要子包职责

- `com.datasophon.common`(根包) — `Constants` 与 `K8sAgentAuthConstants`,集中全局常量、路径前缀、K8s Agent RSA 鉴权头名等。
- `cache` — `CacheUtils` 进程内 Guava 缓存封装与 `Namespace` 命名空间常量。
- `command` — 跨节点命令模型与结果模型(`BaseCommand` / `ClusterCommand` / `ExecuteServiceRoleCommand` / `InstallServiceRoleCommand` 等),与 `ClusterServiceCommand` 业务体系对齐;子包 `command.dag` 承载 DAG 调度命令,`command.remote` 承载 Unix 用户/组、Keytab 等远程操作命令。
- `enums` — 枚举常量(`CommandType` / `ServiceRoleType` / `InstallState` / `OsType` / `ArchType` / `SSHAuthType` 等),供 API、Worker、Agent 序列化时共享同一份取值。
- `model` — 跨进程数据模型(`Host` / `DAG` / `ServiceConfig` / `ServiceRoleInfo` 等),`model.k8s` 为 K8s 工件模型,`model.uni` 为统一数据源/包/仓库描述模型。
- `k8s` — 基于 fabric8 / kubectl / helm CLI 的客户端封装:`client/` 下 `DockerClientWrapper`、`HelmClient`、`KubectlClient` 等;配套 `config`、`spec`、`vo`、`dto`、`exception` 子包描述渲染与异常。
- `utils` — 通用工具集合(`ShellUtils` / `JschUtils` / `FileUtils` / `TarUtils` / `JacksonUtils` / `PlaceholderUtils` 等);子包 `utils.nexus` 提供 `NexusFacade` 与底层 `CommonNexusClient`、`HelmRepoClient`、`RawRepoClient`,封装外部 Nexus / Raw 仓库的 HTTP 操作。
- `storage` — 离线包/镜像/Helm/元数据的抽象存储接口(`PackageStorage` / `ImageStorage` / `HelmStorage` / `MetaStorage`),`storage.impl` 提供基于 Nexus 的实现。
- `lifecycle` — `ServerLifeCycleManager` 与 `ServerStatus`,描述被依赖方的服务生命周期状态机。
- `function` — `ThrowableConsumer/Mapper/Supplier`,允许在 Stream / Lambda 中传播受检异常。
- `lang` — 轻量注解(`@NoUsed`、`@VisibleForTesting`)。
- `exception` — 通用异常类型。

### 关键约定

1. **保持无状态**:作为多个进程共享的公共库,工具类应优先使用静态方法或纯函数,不要在本模块持有全局可变状态(`CacheUtils` 是显式的进程级缓存,使用时由调用方负责生命周期)。
2. **不引入 Spring Web 依赖**:不要在本模块 import `spring-web` / `spring-boot-starter-*` 任何类。如需依赖注入,请放在调用方模块完成装配。本模块只暴露 POJO 与静态工具。
3. **Jackson 序列化扩展统一放在 `common/jackson`**:跨模块的 Jackson `Module` / `Serializer` / `Deserializer` 应集中在 `datasophon-api` 的 `common/jackson` 包中(见 ARCHITECTURE.md 3.2),公共数据模型仅承载注解(`@JsonIgnore`、`@JsonProperty` 等),不要在本模块自定义 ObjectMapper。`fastjson2` 与 Jackson 并存时,新代码优先 Jackson;若已使用 fastjson2 的模型保持现状,不要混用注解。
4. **命令模型变更须同步消费方**:`command` 与 `model` 包中的字段直接影响 Master ↔ Worker ↔ K8s-Agent 的 gRPC / JSON 协议,新增或删除字段前先确认所有依赖方都已适配。
5. **枚举值不可重排序**:`enums` 包中的枚举若被序列化为 ordinal 或在 DB 中持久化,严禁调整顺序或删除中间项,只允许在末尾追加。
6. **K8s 客户端命令外部依赖**:`k8s.client` 下的 `KubectlClient` / `HelmClient` 通过本地 `kubectl` / `helm` 二进制运行,调用方部署环境需保证二进制可达;Java 内进程操作使用 fabric8 客户端。

### 注意事项

- **Pekko/Akka 遗留**:仓库已在 2026-05-23 移除 Pekko,但本模块仍存在历史命名残留(如 `model/AkkaRemoteReply.java`、`exception/AkkaRemoteException.java`、`model/StartWorkerMessage.java` 等"消息体"风格的类)。删除或重命名前必须先用 ripgrep 跨模块搜索引用:

  ```bash
  rg "AkkaRemoteReply|AkkaRemoteException" --type java
  ```

  确认无 API/Worker/Agent 调用后再清理,避免与现网 JSON 协议字段产生不兼容。

- **Hadoop 依赖体积较大**:`hadoop-common` / `hadoop-auth` 在 pom 中显式排除了 `slf4j-log4j12`、`reload4j`、`guava`、`apacheds-kerberos-codec` 等冲突项,新增 Hadoop 相关依赖时务必沿用同样的 exclusions 模式,避免引入回 `log4j 1.x` 或重复 Guava。

- **utils 添加新工具前先搜索**:`utils/` 已有 30+ 个工具类(`FileUtils`、`ShellUtils`、`OsUtils`、`PathUtils` 等),新增工具方法前先 ripgrep 检查是否已有同名/同义实现,优先扩展现有类。
