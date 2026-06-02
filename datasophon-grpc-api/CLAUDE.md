# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## datasophon-grpc-api

gRPC proto 定义 + 共享 stub 库。Master 端（`datasophon-api`）和 Worker 端（`datasophon-worker`）共同依赖此模块，作为 gRPC 契约的单一事实来源（SSOT）。本模块不包含可执行进程。

---

### 常用命令

所有命令都从仓库根目录执行（使用根目录的 `./mvnw`）。

```bash
# 日常编译（stub 已 checked-in，不需要 protoc）
./mvnw -pl datasophon-grpc-api -am compile

# 仅本模块 install
./mvnw -pl datasophon-grpc-api install -DskipTests

# 修改 .proto 后重新生成 Java stub（需要 protoc 工具链）
./mvnw -pl datasophon-grpc-api -am generate-sources -Pgenerate-proto

# 清理 + 重新编译
./mvnw -pl datasophon-grpc-api -am clean compile
```

`generate-sources -Pgenerate-proto` 前置条件：
- `protoc` 可执行文件（macOS：`brew install protobuf`）
- `~/.m2/setting.xml` 中已配置 grpc-java 插件的 Maven 镜像（参考父 `pom.xml` 中的 `${grpc.version}`）

---

### 目录结构

```
src/main/proto/                                  # 4 个 .proto 源文件
src/main/java/com/datasophon/grpc/api/
├── GrpcConstants.java                           # 端口/心跳 SSOT
└── *.java / *Proto.java                         # protoc 生成的 stub（已 checked-in）
```

本模块**没有** `src/test` 目录——stub 是纯生成代码，行为正确性由消费方（`datasophon-api`、`datasophon-worker`）的端到端测试覆盖。

---

### RPC 服务一览

| proto | package | service | RPC |
|---|---|---|---|
| `common.proto` | `com.datasophon.grpc` | — | （公共消息 `ExecResultPb`） |
| `registry.proto` | `com.datasophon.grpc` | `WorkerRegistryService` | `Register` / `Heartbeat` / `Unregister` |
| `worker.proto` | `com.datasophon.grpc` | `WorkerCommandService` | `Ping` / `ExecuteCmd` / `GetLog` / `InstallServiceRole` / `ConfigureServiceRole` / `StartServiceRole` / `StopServiceRole` / `RestartServiceRole` / `ServiceRoleStatus` / `CreateUnixGroup` / `DeleteUnixGroup` / `CreateUnixUser` / `DeleteUnixUser` / `OperateFile` / `GenerateAlertConfig` |
| `master.proto` | `com.datasophon.grpc` | `MasterCallbackService` | `RegisterOlapNode` |

要点：
- **方向**：Worker 主动调 `WorkerRegistryService`（注册 + 心跳）和 `WorkerCommandService` 不对——`WorkerCommandService` 实际是 Master 调 Worker；`MasterCallbackService` 才是 Worker 反向调 Master。
- **公共返回**：所有 `WorkerCommandService` 的 RPC 都返回 `ExecResultPb`（`{exec_result, exec_out, exec_err_out}`）。
- **消息复用约束**（来自 `worker.proto` 注释）：
  - `ExecuteCmdRequest.commands` 与 `command_line` 互斥，服务端优先判断 `command_line`。
  - `ServiceRoleRequest` 中 `json_payload`（Jackson 序列化的命令 POJO，`cofigFileMap` 置 null）与 `config_map_json`（仅 `Configure/Install` 用）共存。
  - `FileOperateRequest.lines` 与 `content` 互斥，优先判断 `lines`。
- **OLAP 反向回调**：`RegisterOlapNode` 采用 fire-and-forget 语义，Master 立即返回 success，异步执行 Doris/StarRocks 集群注册 SQL。

---

### `GrpcConstants` 关键值（端口/心跳 SSOT）

文件：`src/main/java/com/datasophon/grpc/api/GrpcConstants.java`

```java
public static final int MASTER_GRPC_PORT           = 18081;  // Master 端 gRPC 监听端口
public static final int WORKER_GRPC_PORT           = 18082;  // Worker 端 gRPC 监听端口
public static final int HEARTBEAT_INTERVAL_SECONDS = 30;     // Worker 心跳发送间隔
public static final int HEARTBEAT_TIMEOUT_SECONDS  = HEARTBEAT_INTERVAL_SECONDS * 3;  // = 90
```

消费方：
- `MASTER_GRPC_PORT` ← `datasophon-api`（`grpc.server.port`）+ `datasophon-worker`（连 Master 用）
- `WORKER_GRPC_PORT` ← `datasophon-worker`（自己监听）+ `datasophon-api`（连 Worker 用）
- `HEARTBEAT_INTERVAL_SECONDS` ← `datasophon-worker.MasterRegistryClient` 的 `scheduleWithFixedDelay`
- `HEARTBEAT_TIMEOUT_SECONDS` ← `datasophon-api.WorkerRegistry`（连续 3 次心跳缺失即标记 Worker 离线）

---

### 关键约定

1. **stub 已是 checked-in**——日常 `./mvnw compile` / `install` / `test` **不需要** `-Pgenerate-proto`，也不会运行 protoc。只有修改 `src/main/proto/*.proto` 后才需要重新生成。
2. **proto 改动流程**：
   - 修改 `src/main/proto/*.proto`
   - 本地执行 `generate-sources -Pgenerate-proto` 重新生成 Java stub
   - **必须把生成的 `.java` 文件和 `.proto` 改动一起 commit**——下游模块只依赖 stub 编译
3. **端口/心跳常量只在 `GrpcConstants` 改**——`datasophon-api` 和 `datasophon-worker` 都引用此处的常量，禁止两端各自硬编码。
4. **生成目录固定为 `src/main/java`**（见 `pom.xml` 中 `outputDirectory` + `clearOutputDirectory=false`）——不要改成 `target/`，否则 stub 不会进入版本控制。
5. **依赖**：`io.grpc:grpc-stub`、`io.grpc:grpc-protobuf`、`jakarta.annotation:jakarta.annotation-api`（Spring Boot 3 用的 `@Generated`）、`javax.annotation:javax.annotation-api:1.3.2`（grpc-java 生成代码用 `javax.annotation.Generated`）——同时引入两个 annotation 包以兼容两套生成器。

---

### 依赖关系

```
datasophon-grpc-api
        ↑
        ├── datasophon-api      (gRPC server: WorkerRegistryGrpcService + MasterCallbackGrpcService)
        └── datasophon-worker   (gRPC client: WorkerCommandService + MasterRegistryClient)
```

本模块**不依赖** `datasophon-api` 或 `datasophon-worker`，确保 stub 可以被两端无环引用。
