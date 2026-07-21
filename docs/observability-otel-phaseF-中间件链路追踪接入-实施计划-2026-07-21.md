# Phase F — 中间件链路追踪（Traces）接入

> **状态**：待实施（方案已与用户对齐，待批准细节）
> **日期**：2026-07-21
> **分支**：`fix/trace-board`（或另开 `feat/observability-otel-phaseF-middleware-traces`）
> **关联 epic**：可观测重构 OTel+Doris（详见 `docs/observability-otel-doris-设计-2026-06-19.md`；Roadmap A-E 已完成，本批为 Phase D 的延伸，暂记为 Phase F）
> **前置阅读**：`deploy/deployment-standalone-doris.md`（五节点 Doris Standalone 部署手册，本批的现场验证环境）

## Context（为什么做这件事）

`docs/observability-otel-phaseD-traces-agent-实施计划-2026-06-24.md` 落地后，`datasophon-api`/`datasophon-worker` 已默认挂 OTel Java Agent（`386a5ee5`），traces 能落 Doris `otel_traces`/`otel_traces_graph`。但五节点 Doris Standalone 部署手册明确记录：**阶段 A 引入的中间件（Doris、Nacos、Elasticsearch、Valkey、APISIX、DolphinScheduler）目前全部没有产生 span**，链路图上只有 datasophon-api/worker 这两个节点，看不到它们各自调用了哪些下游中间件、耗时多少。

本批目标：把 Traces 覆盖面从"datasophon 自身两个进程"扩到"阶段 A 全部中间件"，让 Traces Tab 和拓扑图（`fix/trace-board` 分支近期在做的 ip:port 服务识别）能看到一条完整的调用链。

## 现状核查（代码级，非猜测）

|                                            组件                                             |                                                        现状                                                        |                      结论                       |
|-------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|-----------------------------------------------|
| `otelcol.ftl` 的 `otlp` receiver（`grpc:4317`/`http:4318`）                                  | 已存在                                                                                                              | ✅ 采集侧不用改                                      |
| `otelcol.ftl` 的 `traces`/`logs` pipeline                                                  | 已存在，导出到 `dorisexporter`                                                                                          | ✅ 采集侧不用改                                      |
| 部署手册 §10.2 记录的现场 `otelcol.yaml`"只有 metrics pipeline"                                      | 是**渲染落盘的旧配置**，落后于当前模板                                                                                            | ⚠️ 需要对已跑的节点重新下发一次配置（见 Phase F5）               |
| `ServiceRoleRunner`（`datasophon-common`）                                                  | 只有 `program`/`args`/`timeout`，无环境变量透传能力                                                                          | 不需要碰——见下方"机制选型"                               |
| `hooks[].action=download` / `action=append_line`（`DownloadStrategy`/`AppendLineStrategy`） | **已存在且已在用**：Nacos/Doris FE 的 `service_ddl.json` 已经用 `append_line` 在厂商启动脚本第 2 行插入 `export JAVA_HOME=$JAVA_HOME17` | ✅ 这就是本批要复用的注入点，**Worker 核心代码不用改**             |
| `dolphinscheduler_env.ftl`                                                                | DataSophon 自己维护、被 `dolphinscheduler-daemon.sh` source 的 env 文件，已有按角色 `case $command in` 分支                       | ✅ DS 直接在这里加，不需要 hook                          |
| APISIX `opentelemetry` 插件                                                                 | 未启用                                                                                                              | 需要在 `apisix-config.ftl`/`apisix-routes.ftl` 加 |

**关键发现，改变了原方案**：最初设想给 `ServiceRoleRunner` 加 `env` 字段、改 `ServiceHandler`/`ShellUtils` 做环境变量透传（一次 Worker 核心代码改动）。但读代码后发现 Nacos/Doris FE 的 DDL 里已经在用 `hooks[].action=append_line` 直接改写厂商启动脚本本身（例如给 `bin/startup.sh` 插入一行 `export JAVA_HOME=...`）。**这是现成的、已被验证过的机制**，插入一行 `export JAVA_TOOL_OPTIONS=...` 和插入 `export JAVA_HOME=...` 没有本质区别。选它而不是新增核心代码路径，原因：

1. 零 Worker/Common 核心代码改动，改动面收敛到各服务的 `service_ddl.json`，符合仓库"新增能力≈写 DDL"的既定架构原则。
2. `JAVA_TOOL_OPTIONS` 是 JVM 启动器本身识别的环境变量（Java 规范行为），不依赖 Nacos/ES/Doris 各自厂商脚本"愿不愿意"转发 `-javaagent`，规避逐个厂商版本核实脚本内部逻辑的不确定性。
   3 与现有先例（`append_line` 插 `JAVA_HOME`）完全同构，评审时容易对齐已有约定。

## 决策（需要用户确认的取舍点）

1. **本批只开 Traces，不开 Metrics/Logs**：中间件的 metrics 已经各自走 Prometheus scrape 管道在采集（Nacos/ES/Doris 现有看板都基于此），javaagent 再开一路 `OTEL_METRICS_EXPORTER=otlp` 会重复计数、搞乱看板。与 Phase D 给 api/worker 定的口径（后来 `386a5ee5` 把 api/worker 的 metrics/logs 也打开了，但那是 datasophon 自身进程、没有旁路 Prometheus 抓取管道，情况不同）不完全一致，需要用户确认认可这个差异化处理。
2. **Doris BE / Valkey 不做任何改动**：C++/C 编写，没有内建 OTel SDK，官方也不提供 agent。它们在链路图里不会自己产生 span，而是靠调用方（如 datasophon-api 走 JDBC 连 Doris、DS WorkerServer 走 Jedis/Lettuce 连 Valkey）的 javaagent 自动生成的 CLIENT span 体现"调用了 Doris/Valkey"这一跳——这是 OTel 对不可插桩数据库/中间件的标准处理方式，本批验收时不能因为 Doris BE/Valkey 没有自己的 span 而判定为缺陷。
3. **javaagent jar 版本沿用 `2.29.0`**（跟 api/worker 一致，父 pom 已钉版本），分发路径统一为 `<INSTALL_PATH>/<service>/otel/opentelemetry-javaagent.jar`，每个服务各自下载一份（不做跨服务共享路径），换取实现简单、DDL 自包含；~20MB/服务 × 5 个 Java 服务，在 §5 容量预算里可接受，不需要为省这点磁盘去解决"跨服务共享一份文件"的路径设计问题。
4. **注入方式按服务类型分两条路径**（不是同一套模板）：
   - Nacos / Doris FE / Elasticsearch：`hooks[].action=append_line`，直接改厂商启动脚本本身。
   - DolphinScheduler：改 DataSophon 自己的 `dolphinscheduler_env.ftl`（已存在、已被 source，不需要碰厂商脚本）。
   - APISIX：完全不同机制，走它自带的 `opentelemetry` 插件配置（改 `apisix-config.ftl`），不涉及 javaagent。
5. **`service.name` 用服务角色名的 kebab 形式**，与现有 `datasophon-api`/`datasophon-worker` 命名风格对齐：`nacos`、`doris-fe`、`elasticsearch`、`dolphinscheduler-api-server`/`-master-server`/`-worker-server`/`-alert-server`、`apisix`。

## 阶段总览

| Phase |                     目标                     |                       涉及服务                        |        前置依赖         |                                                               状态                                                                |
|-------|--------------------------------------------|---------------------------------------------------|---------------------|---------------------------------------------------------------------------------------------------------------------------------|
| F0    | javaagent jar 上传 Nexus + DDL hook 写法验证（试点） | Nacos（单角色，最简单）                                    | 无                   | 待实施                                                                                                                             |
| F1    | 推广到 Elasticsearch                          | ElasticSearch 角色（`EsExporter` 不动）                 | F0 验证通过             | 待实施                                                                                                                             |
| F2    | 推广到 Doris FE                               | DorisFE 角色（DorisBE 不动）                            | F0 验证通过             | 待实施                                                                                                                             |
| F3    | DolphinScheduler 4 角色                      | ApiServer/MasterServer/WorkerServer×3/AlertServer | 无（独立路径，可与 F0-F2 并行） | **已实施 + 现场验证通过**（4 角色各自独立 `service_name`）；排查出 `$command` 变量恒为空的预置缺陷（同时影响本次改动和更早的 `SERVER_PORT` 逻辑），已改用 `*_HOME` 变量修复，见 F3 章节 |
| F4    | APISIX 原生 OTel 插件                          | APISIX                                            | 无（独立机制，可并行）         | **已实施 + 现场验证通过**（ddh-01，5 请求→5 span）；排查出 3 个 APISIX 插件机制坑（`plugins` 清单/`plugin_metadata`/`sampler` 默认值，见 F4 章节），另有 2 个测试预置缺陷未修复 |
| F5    | Collector 配置刷新 + 全链路验证 + 文档收尾              | 全部五节点 OTELCOLLECTOR                               | F0-F4 至少一个已完成       | 待实施                                                                                                                             |

F0-F2 共用同一套机制（`append_line` 注入 + `download` 拉 jar），做完 F0 就是把同一改动模式复制到另外两个服务的 DDL；F3/F4 各自独立机制，可以和 F0-F2 并行推进，不构成阻塞关系。

---

## Phase F0：Nacos 试点（验证机制）

### 改动点

**1. javaagent jar 上传 Nexus raw 仓库**（一次性，人工操作或脚本化）：把 `io.opentelemetry.javaagent:opentelemetry-javaagent:2.29.0` 上传到 Nexus raw 仓库下 Nacos 服务包对应的资源路径（具体路径规则需要实现时读 `NexusResourceStrategy`/`MetaStorage.downResource` 核实——现有 `download` hook 的 `from: script/xxx.sh` 是相对 Nacos 自己包资源树的路径，javaagent jar 大概率也要放在类似 `otel/opentelemetry-javaagent.jar` 相对路径下）。

**2. `package/raw/meta/datacluster-physical/NACOS/service_ddl.json`** 的 `NacosServer` 角色 `hooks[]` 新增两条：

```json
{
  "type": "POST_INSTALL",
  "action": "download",
  "params": {
    "from": "otel/opentelemetry-javaagent.jar",
    "to": "otel/opentelemetry-javaagent.jar",
    "md5": "<实现时用 sha256sum/md5sum 现算，取 2.29.0 官方发布件>"
  }
},
{
  "type": "POST_INSTALL",
  "action": "append_line",
  "params": {
    "line": 2,
    "text": "export JAVA_TOOL_OPTIONS=\"-javaagent:$(pwd)/otel/opentelemetry-javaagent.jar -Dotel.service.name=nacos -Dotel.exporter.otlp.endpoint=http://localhost:4317 -Dotel.exporter.otlp.protocol=grpc -Dotel.traces.exporter=otlp -Dotel.metrics.exporter=none -Dotel.logs.exporter=none\"",
    "source": "bin/startup.sh"
  }
}
```

> 与现有 `export JAVA_HOME=$JAVA_HOME17` 的 `append_line` hook 是同一个 `source: bin/startup.sh`、同一个 `line: 2`——两条 hook 谁先执行决定谁在第 2 行、谁被挤到第 3 行，**顺序不影响功能**（两条都是独立 `export`），但实现时要确认 `AppendLineStrategy` 对同文件多次 `append_line` 的行为是"依次都插到第 2 行"（导致顺序颠倒）还是"依次追加"，避免意外覆盖已有的 `JAVA_HOME` 那一行。

**3. `$(pwd)` 是否能正确展开为 Nacos 安装目录**需要实现时验证——`bin/startup.sh` 里这一行的执行上下文（脚本内 `cd` 到哪里、`$(pwd)` 求值时机）如果不可靠，退化为直接写死绝对路径（`<INSTALL_PATH>/nacos/otel/opentelemetry-javaagent.jar`，`INSTALL_PATH` 这个仓库已有的全局变量占位符在其他模板里广泛使用，DDL 渲染时能替换）。

### 验收

| # |                                                     验收项                                                     |                                检查方式                                |
|---|-------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------|
| 1 | Nacos 安装目录下存在 `otel/opentelemetry-javaagent.jar`，md5 匹配                                                     | 现场 `md5sum`                                                        |
| 2 | `bin/startup.sh` 含新增的 `export JAVA_TOOL_OPTIONS=...` 行，且原有 `JAVA_HOME` 行未被破坏                                | 现场 `cat`                                                           |
| 3 | Nacos 进程启动日志出现 `Picked up JAVA_TOOL_OPTIONS`（JVM 原生提示，证明确实生效）                                               | `logs/start.out`                                                   |
| 4 | 对 Nacos 发起一次真实调用（如 datasophon-api 的服务发现请求，或直接查 Nacos 控制台）后，`otel.otel_traces` 出现 `service_name='nacos'` 的记录 | `SELECT count(*) FROM otel.otel_traces WHERE service_name='nacos'` |
| 5 | Nacos 现有 Prometheus 指标看板数据不受影响（确认没有意外多出一路 metrics）                                                          | 对比开启前后面板                                                           |

F0 走通即证明"jar 分发 + append_line 注入"机制成立，F1/F2 直接照抄改 DDL，不需要重新设计。

---

## Phase F1：Elasticsearch

### 改动点

`package/raw/meta/datacluster-physical/ELASTICSEARCH/service_ddl.json` 的 `ElasticSearch` 角色（不动 `EsExporter`——那是指标导出器，不是 ES 本体）：

- `download` hook：同 F0，落 `otel/opentelemetry-javaagent.jar` 到 ES 安装目录。
- 注入点用 `append_line` 改 `bin/elasticsearch`，**或**优先探索 ES 官方 6.3+ 支持的 `config/jvm.options.d/*.options` drop-in 目录（写一个新文件 `config/jvm.options.d/otel-agent.options`，内容是逐行的 JVM 参数，无需碰 `bin/elasticsearch` 本身）——如果现场 ES 版本支持这个目录（需要实现时核实 `deploy/deployment-standalone-doris.md` 记录的实际 ES 版本），这条路径更干净、不用管 `append_line` 的行号维护问题，优先选用。

```
# config/jvm.options.d/otel-agent.options（若走 drop-in 路径）
-javaagent:${INSTALL_PATH}/elasticsearch/otel/opentelemetry-javaagent.jar
-Dotel.service.name=elasticsearch
-Dotel.exporter.otlp.endpoint=http://localhost:4317
-Dotel.exporter.otlp.protocol=grpc
-Dotel.traces.exporter=otlp
-Dotel.metrics.exporter=none
-Dotel.logs.exporter=none
```

**风险**：较早版本 Elasticsearch（7.x 及以前）内置 `SecurityManager`，理论上可能与字节码注入类 agent 有摩擦；需要现场装完后先看 ES 启动日志有没有 agent 相关异常，而不是假设一定兼容。这条风险在 F0（Nacos）不存在，是 F1 特有的，需要单独验证。

### 验收

同 F0 的 1/3/4/5 项，`service_name='elasticsearch'`；额外增加"ES 启动日志无 agent 相关异常/拒绝加载"这一项。

---

## Phase F2：Doris FE

### 改动点

`package/raw/meta/datacluster-physical/DORIS/service_ddl.json` 的 `DorisFE` 角色，`hooks[]` 现有两条（`download status_fe.sh` + `append_line` 插 `JAVA_HOME`）之外新增：

- `download` hook：落 `otel/opentelemetry-javaagent.jar` 到 `fe/` 目录下（或平级 `otel/`，与 FE 安装目录布局对齐即可）。
- `append_line` hook，`source: fe/bin/start_fe.sh`，插入方式与现有 `JAVA_HOME` 那条完全同构：

```json
{
  "type": "POST_INSTALL",
  "action": "append_line",
  "params": {
    "line": 3,
    "text": "export JAVA_TOOL_OPTIONS=\"-javaagent:$(pwd)/otel/opentelemetry-javaagent.jar -Dotel.service.name=doris-fe -Dotel.exporter.otlp.endpoint=http://localhost:4317 -Dotel.exporter.otlp.protocol=grpc -Dotel.traces.exporter=otlp -Dotel.metrics.exporter=none -Dotel.logs.exporter=none\"",
    "source": "fe/bin/start_fe.sh"
  }
}
```

**重要提示（面向验收，不算缺陷）**：Doris FE 走 MySQL 协议接收查询，不是 HTTP，javaagent 的自动插桩主要覆盖已知库（JDBC client、HTTP server、消息队列等），**不会**给"某条 SQL 在 FE 内部的执行过程"生成 span——它能捕获的是 FE 对外发起的 HTTP/JDBC 调用（如果有）。真正有价值的"查了一次 Doris"这一跳，落在**调用方**（datasophon-api 等）的 JDBC CLIENT span 上，Doris FE 这次挂 agent 更多是为了让 FE 自己对外的调用（如果未来有）也能串起来，不要期望装完之后能看到"SQL 内部执行瀑布图"。这一点必须写进验收说明，避免用户拿着这个预期去验收然后判定"没做对"。

### 验收

同 F0 的 1/2/3/5 项（`service_name='doris-fe'`），第 4 项调整为：**验证调用方（如 datasophon-api）产生的 Doris JDBC CLIENT span 能正确关联到本次改动后的 FE 环境**（而不是指望 FE 自己产生大量 span）。

---

## Phase F3：DolphinScheduler（4 角色）—— 已实施

> 实施中发现比草稿更简单的路径，与下方"改动点"记录的是**实际落地方案**，不再是 `download` 设想。

### 关键改动（相对草稿的修正）

`link` hook 的 `source` 字段支持 `${ROOT.VosManager.INSTALL_PATH}` 这种跨服务参数引用——DS 现有 DDL 的 mysql-connector-j 驱动就是这么处理的（`${ROOT.VosManager.INSTALL_PATH}/datasophon-worker/lib/mysql-connector-j-8.2.0.jar`，`link` 到本地）。而 `datasophon-worker` 自己的包**已经**内置了 `otel/opentelemetry-javaagent.jar`（Phase D 落地，`datasophon-worker/pom.xml` 的 `copy-otel-agent` execution + `assembly.xml` 的 `otel` fileSet），也就是说**每个节点上已经有一份现成的 agent jar**，不需要再上传 Nexus、不需要新的 `download` hook——直接 `link` 过去即可，和 mysql 驱动的处理方式完全一致。

### 改动点（已落地）

**1. `package/raw/meta/datacluster-physical/DS/service_ddl.json`**：4 个角色（`ApiServer`/`MasterServer`/`WorkerServer`/`AlertServer`）的 `hooks[]` 里，紧跟在各自已有的 mysql-connector-j `link` hook 之后，各新增一条：

```json
{
  "type": "POST_INSTALL",
  "action": "link",
  "params": {
    "source": "${ROOT.VosManager.INSTALL_PATH}/datasophon-worker/otel/opentelemetry-javaagent.jar",
    "target": "otel/opentelemetry-javaagent.jar"
  }
}
```

`target` 不带角色子目录前缀（不是 `api-server/otel/...`），落在 DS 安装根目录（`createDecompressDir: false`，4 个角色共享同一个根），这样 `dolphinscheduler_env.ftl` 里可以用同一个相对路径引用，不用按角色区分。

**2. `datasophon-worker/src/main/resources/templates/dolphinscheduler_env.ftl`**：在文件末尾既有的按角色覆盖 `SERVER_PORT` 逻辑之后追加 OTel 埋点（最终版本，见下方"现场排查"——最初用 `case "$command" in` 判断角色，现场验证证明 `$command` 恒为空，已改用 `*_HOME` 变量判断）：

```sh
if [ -n "$API_HOME" ]; then
  export DS_ROLE=api-server
  export SERVER_PORT=12345
elif [ -n "$MASTER_HOME" ]; then
  export DS_ROLE=master-server
  export SERVER_PORT=5679
elif [ -n "$WORKER_HOME" ]; then
  export DS_ROLE=worker-server
  export SERVER_PORT=1235
elif [ -n "$ALERT_HOME" ]; then
  export DS_ROLE=alert-server
  export SERVER_PORT=50053
fi

export OTEL_JAVAAGENT_ENABLED="${OTEL_JAVAAGENT_ENABLED:-true}"
if [ "$OTEL_JAVAAGENT_ENABLED" = "true" ]; then
  export JAVA_TOOL_OPTIONS="-javaagent:$DOLPHINSCHEDULER_HOME/otel/opentelemetry-javaagent.jar -Dotel.service.name=dolphinscheduler-$DS_ROLE -Dotel.exporter.otlp.endpoint=http://localhost:4317 -Dotel.exporter.otlp.protocol=grpc -Dotel.traces.exporter=otlp -Dotel.metrics.exporter=none -Dotel.logs.exporter=none"
fi
```

默认开启，`OTEL_JAVAAGENT_ENABLED=false` 可关闭，和 api/worker 的既有约定（`386a5ee5`）一致。

### 现场排查：`$command` 从未生效，是一个比本次改动更早存在的预置缺陷

草稿假设"`$command` 由 `dolphinscheduler-daemon.sh` 在 source 本文件前置好"，现场部署后用 `curl` 打真实流量验证，`otel.otel_traces` 里只出现一个 `service_name='dolphinscheduler-'`（4 个角色的 span 全部混在一起，`$command` 被替换成空字符串），而不是预期的 4 个独立 service_name。

根因（读 DS 官方脚本源码确认）：`dolphinscheduler-daemon.sh` 里 `command=$1` 只是一个**没有 `export` 的本地 shell 变量**；它通过 `nohup /bin/bash "$DOLPHINSCHEDULER_HOME/$command/bin/start.sh"` 派生一个**全新的** bash 进程去执行各角色的 `start.sh`，新进程不继承旧进程未 export 的本地变量。而 `start.sh` 是在这个新进程里才 `source "$XXX_HOME/conf/dolphinscheduler_env.sh"`——source 执行的那一刻，`$command` 从未被设置过。

这不是本次改动引入的新问题——文件里既有的、用同一个 `case "$command" in` 判断角色来设置 `SERVER_PORT` 的逻辑（早于本次改动）同样从未生效过（现场核实：运行中的 DS 进程 `SERVER_PORT` 环境变量压根不存在）。只是此前没有需要按角色区分 service_name 的场景，这个失效一直没被发现。修复用各角色 `start.sh` 在 source 前各自设置、互不重名的变量（`API_HOME`/`MASTER_HOME`/`WORKER_HOME`/`ALERT_HOME`，四个 `start.sh` 分别核实过）判断角色，这是运行时真正可靠的信号；顺带修好了 `SERVER_PORT` 这个更早的遗留问题。

`$DOLPHINSCHEDULER_HOME` 由 `start.sh` 自己 `DOLPHINSCHEDULER_HOME=$(cd ${BIN_DIR}/../..;pwd)` 计算并 export 后再 source env 文件，这个假设现场核实是对的，没有问题；`target` 落在 DS 安装根目录（不带角色子目录前缀）这个设计本身也没问题——之前一度怀疑是这个路径错了，实际排查发现是 `$command` 的问题，路径设计是对的。

### 验收

- [x] `datasophon-worker` 单测 `DolphinschedulerEnvTemplateTest` 绿：新增断言覆盖 `OTEL_JAVAAGENT_ENABLED` 默认值、`-javaagent` 路径、`service.name`、`traces/metrics/logs` 三个 exporter 开关、四个 `*_HOME` 判断分支。
- [x] `package/raw/meta/datacluster-physical/DS/service_ddl.json` JSON 合法，4 个角色各自新增 1 条 `link` hook。
- [x] 现场验收（ddh-01~05）：DS 4 个角色目录下 `otel/opentelemetry-javaagent.jar` 均为软链且指向存在的文件（落在 DS 安装根目录，非角色子目录，符合设计）；`otel.otel_traces` 出现 4 个独立 `service_name`（`dolphinscheduler-api-server`/`-master-server`/`-worker-server`/`-alert-server`），`SERVER_PORT` 环境变量同步核实生效（`5679`/`12345`/`1235`/`50053`）。
- [ ] WorkerServer 连 MySQL/Valkey 产生的 JDBC/Redis CLIENT span 可见——待验证（需要一次真实触发 WorkerServer 数据库/缓存访问的操作）。

---

## Phase F4：APISIX（独立机制，非 javaagent）—— 已实施 + 现场验证通过

### 改动点（最终落地版本，与最初设计有 3 处偏差——见下方"现场排查"）

**1. `datasophon-worker/src/main/resources/templates/apisix-config.ftl`**（对应生成 `conf/config.yaml`）：新增顶层 `plugins:` 清单（APISIX 官方默认插件全集 104 项 + `opentelemetry`），`plugin_attr.opentelemetry` 块**已移除**（现场验证证明它对这个插件完全不生效，留着是误导性死配置，见下）：

```yaml
plugins:
  - real-ip
  - ai
  # ...（完整 104 项默认插件，对齐 apisix/cli/config.lua 的内置清单）
  - opentelemetry

plugin_attr:
  prometheus:
    export_addr:
      ip: ${apisixPrometheusAddr?json_string}
      port: ${apisixPrometheusPort}
    enable_export_server: true
```

**2. `datasophon-worker/src/main/resources/templates/apisix-routes.ftl`**（对应生成 `conf/apisix.yaml`）：`global_rules` 追加 opentelemetry 插件（必须带 `sampler.name: always_on`），并新增顶层 `plugin_metadata` 段（真正提供 collector 地址等运行时参数的地方）：

```yaml
global_rules:
  - id: 1
    plugins:
      prometheus:
        prefer_name: true
  - id: 2
    plugins:
      opentelemetry:
        sampler:
          name: always_on

plugin_metadata:
  - id: opentelemetry
    resource:
      service.name: apisix
    collector:
      address: 127.0.0.1:4318
      request_timeout: 3
    batch_span_processor:
      drop_on_queue_full: false
      max_queue_size: 1024
      batch_timeout: 2
#END
```

不涉及 javaagent、不涉及新 DDL 参数——collector 地址直接写死 `127.0.0.1:4318`（本机 otelcol 的 `otlp/http` receiver），符合"每节点 otelcol 就近直写，无 gateway"的既定架构。

**为什么这一环特别重要**：APISIX 是当前拓扑（部署手册 §5）里唯一的网关入口，只要它正确生成/透传 `traceparent` header，后面挂了 javaagent 的 `datasophon-api` 等服务的 span 会自动和它拼接成一条链路——不需要额外配置，这是 W3C Trace Context 传播的标准行为。

### 现场排查：最初设计的 `plugin_attr.opentelemetry: {}` 方案完全不生效，三个连环坑

代码改完、单元测试通过后现场部署（ddh-01），实测**零 span 落地**，排查出三个环环相扣的问题，全部是 APISIX 插件机制本身的坑，代码逻辑没有其他问题：

1. **`opentelemetry` 不在 APISIX 官方默认插件清单里**：`apisix/cli/config.lua` 硬编码了一份 104 项默认插件数组（`zipkin` 在其中，但 `opentelemetry` 不在），且 Standalone 模式下用户在 `config.yaml` 声明顶层 `plugins:` 字段是**整体覆盖**而非追加合并。最初方案完全没碰 `plugins:` 字段，导致插件从未被加载，请求直接报 `unknown plugin [opentelemetry]`。修复：显式列出全部 104 项默认插件（已用脚本从 `config.lua` 源码精确提取，排除了其中一行已被 `--` 注释掉、实际不启用的 `server-info`）+ 追加 `opentelemetry`。
2. **`opentelemetry` 插件的运行时配置走 `plugin_metadata`，不是 `plugin_attr`**：这是最初设计判断错误的地方——`plugin_attr` 是 APISIX CLI/静态配置层（进程启动时读一次），而 `collector`/`resource`/`batch_span_processor` 这些字段实际由插件的 `metadata_schema` 定义，只能通过独立的 `plugin_metadata`（Admin API 或 Standalone yaml 顶层字段）提供。缺失时插件不报错、不崩溃，只在 `error.log` 打一行 `warn`："`plugin_metadata is required for opentelemetry plugin to working properly`"，然后**静默跳过**、不产生任何 span——现场排查这一步耗时最长，因为进程正常运行、日志级别是 warn 不是 error，很容易被忽略。
3. **`sampler` 默认 `always_off`**：即使插件加载成功、`plugin_metadata` 配置齐全，`opentelemetry` 插件 schema 里 `sampler.default = "always_off"`——写 `opentelemetry: {}` 会完全落到这个默认值，出于性能考虑不采样任何请求。必须显式在插件配置（不是 `plugin_metadata`，是 route/global_rule 级别的 `plugins.opentelemetry.sampler`）里写 `name: always_on`。

三个问题按顺序修复、每次都现场重启 APISIX 验证，最终用 `curl http://127.0.0.1:9080/get` 打真实流量，在 Doris `otel.otel_traces` 里确认 `service_name='apisix'` 的 span 按请求数量精确落地（5 次请求 → 5 条 span）。

### 顺带发现的两个测试预置缺陷（无关本次改动，未修复）

1. **`apisix-config.ftl`/`apisix-routes.ftl` 的端口类字段在数字型 locale 下会被 FreeMarker 加千分位分隔符**：`${apisixPort}`（Integer 9080）在默认 `NumberFormat` 支持千分位分组的 locale 下渲染成 `9,080`，是非法端口值。真实生产隐患，修法是改用 `${apisixPort?c}`。
2. **`ApisixStandaloneTemplateTest.standaloneDdlUsesCustomTemplatesAndMapParameters` 的字符串邻接断言过期**：DDL 后续在 `apisixPort`/`key` 之间多插入了一行 `"port": true`，测试没跟着更新。

按"看到不相关问题先提出、不顺手改"的原则未修复，仅记录，供后续单独立项处理。

### 验收

| # |                                              验收项                                              |      检查方式      |                            结果                            |
|---|-----------------------------------------------------------------------------------------------|----------------|----------------------------------------------------------|
| 1 | `conf/config.yaml` 含完整 `plugins:` 清单（含 `opentelemetry`）                                       | 现场 `cat`       | ✅ 已验证                                                    |
| 2 | `conf/apisix.yaml` 含 `global_rules` opentelemetry（`sampler: always_on`）+ 顶层 `plugin_metadata` | 现场 `cat`       | ✅ 已验证                                                    |
| 3 | 经 APISIX 代理的一次真实请求，`otel.otel_traces` 出现 `service_name='apisix'` 的 span                       | 查 Doris        | ✅ 已验证（5 请求 → 5 span）                                     |
| 4 | 与下游 `datasophon-api` 的 span 共享同一个 `trace_id`（W3C traceparent 透传）                              | Traces Tab 瀑布图 | 待验证（需一次经 APISIX 代理的真实业务请求，当前只验证了 APISIX 自身产生 span，未验证透传） |

---

## Phase F5：Collector 配置刷新 + 全链路验证 + 收尾

### 改动点

无代码改动。对五节点的 OTELCOLLECTOR 角色执行一次 `RESTART_WITH_CONFIG`（前端已有此操作入口，Phase E 已验证过这条触发链路：`ServiceCommandService` → `GenerateServiceConfigCommand` → Worker 重新渲染 `otelcol.yaml` → 重启），确保线上实际运行的配置和当前 `otelcol.ftl`（已含 traces/logs pipeline）对齐，不再是部署手册 §10.2 记录的旧渲染结果。

### 全链路验证

在已完成 F0-F4（至少完成 F0/F4，因为 APISIX 是入口、Nacos 是被 datasophon-api 依赖的服务发现，这两个连起来最能体现"一条链路"）的环境上，从前端触发一次会经过 `前端 → APISIX → datasophon-api → Nacos/Doris` 的真实操作（比如集群列表页触发一次会查 Nacos 服务发现 + 查 Doris 监控数据的请求），在 Traces Tab 里应该能看到：

- 一个 `trace_id` 下有 `apisix` → `datasophon-api` → `nacos`（CLIENT span）→ `doris`（JDBC CLIENT span）的完整父子链路。
- 拓扑图（`fix/trace-board` 分支在做的 ip:port 服务识别功能）应该能画出这几个节点之间的调用边。

### 文档收尾

- 更新本文件状态为"已实施"，补充实测的 `md5`/jar 上传路径等真实值。
- 在 `deploy/deployment-standalone-doris.md` 补一条 Phase（如 §13）记录本批在五节点现场的落地结果，或视五节点 Epic 当前状态（Phase 11 已归档，Phase 12 阻塞）决定是否需要新开一个独立验收记录，不直接改已经 PASSED 的历史 Phase 记录。
- `docs/monitoring/` 下如果 Doris/Nacos/ES 各自的 Monitor 看板文档有"Traces �covered=否"之类的表述，一并更新。

---

## 风险与边界

- **`append_line` 对同一脚本多次插入的顺序行为未经验证**：F0/F2 都会往已经有一条 `append_line`（插 `JAVA_HOME`）的脚本里再插一条，需要现场验证不会互相挤位置、不会把 `JAVA_HOME` 那行覆盖掉。
- **`$(pwd)` 在厂商脚本执行上下文里的求值时机不确定**：如果不可靠，统一退化为 `INSTALL_PATH` 变量占位符写死绝对路径。
- **ES 的 `SecurityManager` 兼容性未验证**：F1 特有风险，装完先看启动日志，不要假设兼容。
- **Doris FE 挂 agent 后不会产生"SQL 执行瀑布图"**：这是 MySQL 协议 + 自动插桩能力边界决定的，不是配置错误，需要提前对齐验收预期。
- **javaagent jar 上传 Nexus 的具体路径规则未经代码确认**：`download` hook 的 `from` 字段解析逻辑在 `NexusResourceStrategy`/`MetaStorage`，F0 实现时第一步就要读这段代码，不能照抄 `script/xxx.sh` 的相对路径习惯就假设一定适用于任意文件名/任意目录层级。
- **Doris BE / Valkey 没有自己的 span，是设计如此，不是遗漏**——已在"决策"一节写明，验收时不能按这个标准判定失败。
- **本批只开 Traces**：如果后续想统一到 api/worker 那样 traces/metrics/logs 三路全开，需要先解决"javaagent metrics 和现有 Prometheus scrape 管道重复"的问题（比如把对应中间件的 Prometheus scrape 配置摘掉，全部改走 javaagent metrics），这是一个不小的独立决策，本批不做，只标注。

## 验收标准总表

|        Phase        |                                 核心验收项                                 | 状态  |
|---------------------|-----------------------------------------------------------------------|-----|
| F0 Nacos            | `otel_traces` 出现 `service_name='nacos'`，指标看板不受影响                      | 待实施 |
| F1 Elasticsearch    | `otel_traces` 出现 `service_name='elasticsearch'`，启动日志无异常               | 待实施 |
| F2 Doris FE         | `otel_traces` 出现 `service_name='doris-fe'`，调用方 JDBC CLIENT span 可关联   | 待实施 |
| F3 DolphinScheduler | 4 个角色各自产生独立 `service_name`，WorkerServer 的 MySQL/Valkey CLIENT span 可见 | 待实施 |
| F4 APISIX           | `otel_traces` 出现 `service_name='apisix'`，与下游共享 `trace_id`             | 待实施 |
| F5 收尾               | 五节点 otelcol 配置刷新，端到端瀑布图验证通过，文档更新                                      | 待实施 |

## 端到端验证命令（现场，五节点 Doris Standalone 环境）

```bash
# 1. 逐服务确认 javaagent jar 落地 + 启动脚本改写生效（以 Nacos 为例，其余服务同理换路径）
ssh ddh-02 "md5sum /data/install_datasophon/nacos/otel/opentelemetry-javaagent.jar"
ssh ddh-02 "sed -n '1,5p' /data/install_datasophon/nacos/bin/startup.sh"
ssh ddh-02 "grep -i 'Picked up JAVA_TOOL_OPTIONS' /data/install_datasophon/nacos/logs/start.out"

# 2. 查 Doris 确认各服务 span 落库（MySQL 协议）
mysql -h127.0.0.1 -P9030 -uroot -e "
  SELECT service_name, count(*) AS span_count, max(timestamp) AS last_seen
  FROM otel.otel_traces
  WHERE service_name IN ('nacos','elasticsearch','doris-fe','apisix',
    'dolphinscheduler-api-server','dolphinscheduler-master-server',
    'dolphinscheduler-worker-server','dolphinscheduler-alert-server')
  GROUP BY service_name;
"

# 3. 抽一条 trace_id 验证跨服务父子关系（找一条经过 apisix 的 trace）
mysql -h127.0.0.1 -P9030 -uroot -e "
  SELECT trace_id FROM otel.otel_traces WHERE service_name='apisix' ORDER BY timestamp DESC LIMIT 1;
"
# 拿到 trace_id 后去前端 Traces Tab 搜索，肉眼核对瀑布图父子链路
```

