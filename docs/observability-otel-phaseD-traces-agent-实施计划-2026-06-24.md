# Phase D — Traces：datasophon-api / worker 挂 OTel Java Agent

> **状态**：待实施（设计已确认）
> **日期**：2026-06-24
> **分支**：`refactor/observability-otel`
> **关联 epic**：可观测重构 OTel+Doris（[[project_observability_otel_doris]]）

## Context（为什么做这件事）

可观测重构 epic 的 Phase D = **Traces 链路追踪**。设计文档（`docs/observability-otel-doris-设计-2026-06-19.md:99-101`）把它拆成两步：

- **D1. traces 管道**：otlp receiver(agent) → Doris `otel_traces`
- **D2. datasophon-api / worker 挂 OTel Java Agent 自动埋点，导出 OTLP**

**关键发现：D1 在 Phase A 已实质完工**——`datasophon-worker/src/main/resources/templates/otelcol.ftl` 的 traces pipeline（`otlp` receiver `0.0.0.0:4317`(grpc)/`:4318`(http) → `batch` → doris/awss3 exporter）已就绪，Doris `otel_traces` / `otel_traces_graph` 表已建（`observability/doris/V1__otel_tables.sql:341,403`）。**模板侧、表结构侧无需改动**。

所以 **Phase D 本批的真正工作是 D2**：给 datasophon-api（Spring Boot Master 进程）和 datasophon-worker（非 Spring Boot 的 `main()` 进程）挂 OpenTelemetry Java Agent，让两个进程自动埋点（HTTP / gRPC / JDBC 等），通过 OTLP 把 traces 发到**本节点** OTel Collector 的 `localhost:4317`，最终落 Doris `otel_traces`。

全仓库此前**没有任何 OTel Java Agent 痕迹**（grep `opentelemetry-javaagent` / `io.opentelemetry.javaagent` 零命中），是从零引入，无既有约定可破坏。

## 决策（已与用户确认）

1. **范围 = api + worker 都挂 agent**。`traces_graph_job` 的 `CREATE JOB` 幂等（A3 遗留）**继续 defer**——它是 `observability/doris/apply-verify.md §5` 标注的「待真实 Doris」卡点（Doris `CREATE JOB` 无 `IF NOT EXISTS`、`DROP JOB` 无 `IF EXISTS`，需真机实测 DROP 不存在 job 的错误码才能写幂等），开发机无法验收，**不纳入本批**。
2. **agent jar 分发 = Maven 依赖 + 构建期 copy**。父 pom 钉版本，`maven-dependency-plugin:copy` 在 `prepare-package` 落 `opentelemetry-javaagent.jar` 到模块 `target/otel/`，再由 assembly fileSet 打进 tar.gz。**不 checked-in 二进制**，构建可复现。
3. **默认关闭，开关显式开启**。启动脚本读环境变量 `OTEL_JAVAAGENT_ENABLED`，默认不挂 `-javaagent`；显式 `=true` 才挂。避免**未装 collector 的节点**挂了 agent 后连不上 `localhost:4317` 持续刷错误日志（符合设计文档「初期管道优先 / 渐进开启」）。
4. **agent 只发 traces**。metrics 已由 collector 的 prometheus receiver 抓取，logs 走既有链路；agent 设 `OTEL_METRICS_EXPORTER=none` / `OTEL_LOGS_EXPORTER=none`，仅 `OTEL_TRACES_EXPORTER=otlp`，避免与现有 metrics/logs 链路重复。

## 现状盘点

|                     组件                      |           现状            |        本批是否改        |
|---------------------------------------------|-------------------------|---------------------|
| `otelcol.ftl` traces pipeline               | ✅ otlp→batch→doris 已就绪  | ❌ 不改                |
| Doris `otel_traces` / `otel_traces_graph` 表 | ✅ 已建（`IF NOT EXISTS`）   | ❌ 不改                |
| `otel_traces_graph_job`（CREATE JOB）         | ⏳ 幂等待真实 Doris（A3 defer） | ❌ 本批不做，仅标注          |
| OTel Java Agent jar 引入                      | ❌ 全仓零痕迹                 | ✅ 新增（Maven 依赖 copy） |
| `bin/datasophon-api.sh` agent 注入            | ❌ 仅有 `$JMX`             | ✅ 新增 `$OTEL` 开关     |
| `datasophon-worker.sh` agent 注入             | ❌ 仅有 `$JMX`             | ✅ 新增 `$OTEL` 开关     |
| 两个 assembly 打包 agent jar                    | ❌ 无                     | ✅ 新增 fileSet        |

**OTLP 落点验证**：OTELCOLLECTOR 角色 `cardinality: "1+"` 是 per-node sidecar（`package/raw/meta/datacluster-physical/OTELCOLLECTOR/service_ddl.json`），`otlp` receiver 监听 `0.0.0.0:4317` 含 loopback，本机 api/worker 进程可直发 `localhost:4317`。**前置约束**：开启 agent 的节点必须已装 OTELCOLLECTOR 角色（开关默认关正是为此兜底）。

## 实施步骤

### 1. 父 pom 钉 agent 版本（`pom.xml`，根）

`<properties>` 加 `<opentelemetry-javaagent.version>2.x.x</opentelemetry-javaagent.version>`（取当前最新稳定 release，实现时核对 Maven Central）。不必进 `dependencyManagement`（agent 不作为编译依赖，仅由 dependency-plugin `copy` 用 property 版本拉取）。

### 2. worker：copy agent jar + 打包 + 脚本注入

**2a. `datasophon-worker/pom.xml`** 新增 `maven-dependency-plugin`（worker 当前无此插件）：

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-dependency-plugin</artifactId>
  <executions>
    <execution>
      <id>copy-otel-agent</id>
      <phase>prepare-package</phase>
      <goals><goal>copy</goal></goals>
      <configuration>
        <artifactItems>
          <artifactItem>
            <groupId>io.opentelemetry.javaagent</groupId>
            <artifactId>opentelemetry-javaagent</artifactId>
            <version>${opentelemetry-javaagent.version}</version>
            <outputDirectory>${project.build.directory}/otel</outputDirectory>
            <destFileName>opentelemetry-javaagent.jar</destFileName>
          </artifactItem>
        </artifactItems>
      </configuration>
    </execution>
  </executions>
</plugin>
```

**2b. `datasophon-worker/src/main/resources/assembly.xml`** 仿照现有 `jmx` fileSet（第 124-127 行）新增，把 `target/otel/` 打进 tar.gz 的 `otel/`：

```xml
<fileSet>
    <directory>${project.build.directory}/otel</directory>
    <outputDirectory>otel</outputDirectory>
</fileSet>
```

**2c. `datasophon-worker/src/main/resources/datasophon-worker.sh`** 在 `if [ "$command" = "worker" ]` 块（现有 `JMX=...` 旁）加开关，追加进该块的 `DDH_OPTS`（start / restart 两处 `exec_command` 自动继承）：

```sh
OTEL=""
if [ "$OTEL_JAVAAGENT_ENABLED" = "true" ]; then
  OTEL="-javaagent:$DDH_HOME/otel/opentelemetry-javaagent.jar"
  export OTEL_SERVICE_NAME="${OTEL_SERVICE_NAME:-datasophon-worker}"
  export OTEL_EXPORTER_OTLP_ENDPOINT="${OTEL_EXPORTER_OTLP_ENDPOINT:-http://localhost:4317}"
  export OTEL_EXPORTER_OTLP_PROTOCOL="${OTEL_EXPORTER_OTLP_PROTOCOL:-grpc}"
  export OTEL_TRACES_EXPORTER=otlp
  export OTEL_METRICS_EXPORTER=none
  export OTEL_LOGS_EXPORTER=none
fi
export DDH_OPTS="$HEAP_OPTS $DDH_OPTS $JAVA_OPTS $OTEL"
```

（现块末尾 `export DDH_OPTS="$HEAP_OPTS $DDH_OPTS $JAVA_OPTS"` 追加 `$OTEL`。`$JMX` 仍在两处 `exec_command` 原样保留。）

### 3. api：copy agent jar + 打包 + 脚本注入

**3a. `datasophon-assembly/pom.xml`** 新增同样的 `maven-dependency-plugin:copy`（落 `datasophon-assembly/target/otel/opentelemetry-javaagent.jar`）。manager tar.gz 由 `datasophon-assembly/assembly/assembly.xml` 装配，故 copy 放此模块。

**3b. `datasophon-assembly/assembly/assembly.xml`** 仿照现有根 `../jmx` fileSet（第 103-108 行）新增：

```xml
<fileSet>
    <directory>${basedir}/target/otel</directory>
    <outputDirectory>/otel</outputDirectory>
    <directoryMode>0755</directoryMode>
    <fileMode>0755</fileMode>
</fileSet>
```

**3c. `bin/datasophon-api.sh`** 在 `if [ "$command" = "api" ]` 块（现有 `JMX=...` 第 86 行旁）加开关，把 `$OTEL` 追加进第 89 行的 `DDH_OPTS`（一处覆盖 start+restart）：

```sh
OTEL=""
if [ "$OTEL_JAVAAGENT_ENABLED" = "true" ]; then
  OTEL="-javaagent:$DDH_HOME/otel/opentelemetry-javaagent.jar"
  export OTEL_SERVICE_NAME="${OTEL_SERVICE_NAME:-datasophon-api}"
  export OTEL_EXPORTER_OTLP_ENDPOINT="${OTEL_EXPORTER_OTLP_ENDPOINT:-http://localhost:4317}"
  export OTEL_EXPORTER_OTLP_PROTOCOL="${OTEL_EXPORTER_OTLP_PROTOCOL:-grpc}"
  export OTEL_TRACES_EXPORTER=otlp
  export OTEL_METRICS_EXPORTER=none
  export OTEL_LOGS_EXPORTER=none
fi
export DDH_OPTS="$HEAP_OPTS $OPENS_OPTS $DDH_OPTS $JMX $OTEL"
```

### 4. 文档：开关说明 + graph_job defer 标注

- 在 `deploy/observability/otelcol/README.md` 或同目录补充：`OTEL_JAVAAGENT_ENABLED=true` 的前置条件（本节点须装 OTELCOLLECTOR）、可调环境变量（`OTEL_SERVICE_NAME` / `OTEL_EXPORTER_OTLP_ENDPOINT`）、默认关闭语义。建议在节点 `/etc/profile`（脚本已 `source /etc/profile`）或 systemd unit `Environment=` 注入开关。
- 在 `observability/doris/apply-verify.md §5` 旁标注：`otel_traces_graph_job` 幂等仍为 Phase D 遗留（A3），待真实 Doris 4.0.5 实测后单独处理，**不在本批验收范围**。

## 关键复用点（避免造轮子）

|             复用目标              |                                         位置                                          |
|-------------------------------|-------------------------------------------------------------------------------------|
| `$JMX` 变量 + `DDH_OPTS` 拼法     | `bin/datasophon-api.sh:86,89` / `datasophon-worker.sh`（worker 块）                    |
| jmx jar assembly fileSet 打包范式 | worker `assembly.xml:124-127` / `datasophon-assembly/assembly/assembly.xml:103-108` |
| traces pipeline（otlp→doris）   | `otelcol.ftl`（已就绪，**不改**）                                                           |
| Doris `otel_traces` 表         | `observability/doris/V1__otel_tables.sql:341`（已建，**不改**）                            |

## 风险与边界

- **无 collector 节点开启 agent → 连接失败刷日志**：默认关 + 文档前置条件兜底。
- **agent 与 JMX javaagent 并存**：可共存（职责不重叠）；`OTEL_METRICS_EXPORTER=none` 防与 JMX/prometheus 链路冲突。
- **agent 体积（~20MB+）**：构建期 copy 不进 git，可接受。
- **worker 非 Spring Boot**：agent 走字节码注入，不依赖 Spring，照常 instrument gRPC/JDBC/HTTP client，无特殊适配。
- **graph_job 幂等仍 defer**：本批不碰，标注清楚即可。
- **大数据服务默认不发 OTLP**：本批仅动 api/worker 两个 datasophon 自身进程，符合「首批埋点」边界。

## 验收标准

> 本批以**构建产物 + 启动脚本开关**为主，Java 单测不适用（无新增 Java 逻辑）；端到端 traces 落库依赖真实 collector+Doris，开发机受限，以「产物含 jar + 脚本 dry-run echo」为开发机硬证据。

| # |                                                      验收项                                                      |                                         检查方式                                         |
|---|---------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------|
| 1 | 父 pom 钉了 `opentelemetry-javaagent.version`，worker / assembly pom 各有 `dependency-plugin:copy`                  | 读 pom                                                                                |
| 2 | 全量编译通过                                                                                                        | `./mvnw -pl datasophon-worker,datasophon-api -am compile -DskipTests`                |
| 3 | worker tar.gz 含 `otel/opentelemetry-javaagent.jar`                                                            | `package` 后 `tar tzf datasophon-worker/target/datasophon-worker.tar.gz \| grep otel` |
| 4 | manager tar.gz 含 `otel/opentelemetry-javaagent.jar`                                                           | 打 assembly 后 `tar tzf .../datasophon-*-package.tar.gz \| grep otel`                  |
| 5 | 脚本默认关：未设 `OTEL_JAVAAGENT_ENABLED` 时 exec_command **不含** `opentelemetry-javaagent`                             | `sh -n` 语法 + dry-run echo 肉眼核对                                                       |
| 6 | 脚本开启：`OTEL_JAVAAGENT_ENABLED=true` 时含 `-javaagent:.../otel/opentelemetry-javaagent.jar`，start / restart 两路径一致 | 同上对比开/关两次 echo                                                                       |
| 7 | agent 只发 traces：脚本含 `OTEL_TRACES_EXPORTER=otlp` + `OTEL_METRICS_EXPORTER=none` + `OTEL_LOGS_EXPORTER=none`    | 读脚本                                                                                  |
| 8 | Spotless 不破坏                                                                                                  | `./mvnw spotless:check`                                                              |
| 9 | `otel_traces_graph_job` 幂等**明确标注 defer**，未混入本批                                                                | 文档/`apply-verify.md` 核对                                                              |

## 端到端验证命令

```bash
export JH17=/Users/pro/Library/Java/JavaVirtualMachines/jbr-17.0.12-1/Contents/Home

# 1. 编译
JAVA_HOME=$JH17 ./mvnw -pl datasophon-worker,datasophon-api -am compile -DskipTests -s ~/.m2/setting.xml

# 2. 打 worker 包 + 验证含 agent jar
JAVA_HOME=$JH17 ./mvnw -pl datasophon-worker -am package -DskipTests -s ~/.m2/setting.xml
tar tzf datasophon-worker/target/datasophon-worker.tar.gz | grep 'otel/opentelemetry-javaagent.jar'

# 3. 打 manager 包 + 验证含 agent jar
JAVA_HOME=$JH17 ./mvnw -pl datasophon-assembly -am package -DskipTests -s ~/.m2/setting.xml
tar tzf datasophon-assembly/target/datasophon-*-package.tar.gz | grep 'otel/opentelemetry-javaagent.jar'

# 4. 脚本开关 dry-run
#   关：sh bin/datasophon-api.sh start api
#     → echo 不含 opentelemetry-javaagent
#   开：OTEL_JAVAAGENT_ENABLED=true sh bin/datasophon-api.sh start api
#     → echo 含 -javaagent:.../otel/opentelemetry-javaagent.jar
#   （仅看 "nohup $JAVA ..." 那行）

# 5. Spotless
JAVA_HOME=$JH17 ./mvnw spotless:check -s ~/.m2/setting.xml

# 6. 真实环境（受限，待真机）：
#   在已装 OTELCOLLECTOR 的节点 export OTEL_JAVAAGENT_ENABLED=true 重启 api/worker
#   触发若干 HTTP/gRPC 请求 → 查 Doris：
#     SELECT count(*) FROM otel.otel_traces
#     WHERE service_name IN ('datasophon-api','datasophon-worker');
#     期望 > 0
```

> 真机端到端依赖 Phase A 接入的 collector + Doris 落库；本机受限时以验收 #2–#8 为准。

