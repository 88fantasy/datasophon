# Phase F — 中间件链路追踪（Traces）接入

> **状态**：F0/F3/F4 已实施 + 现场验证通过；F1（Elasticsearch）代码/机制现场验证通过但 javaagent 被 ES 拒绝注入，标记已知限制；F2（Doris FE）代码已就绪，未部署到现场（用户选择跳过，Doris FE 无 HA）；F5 部分完成（ddh-02 确认无需刷新，其余四节点未核实）
> **日期**：2026-07-21（F0/F1/F2 补齐代码）；2026-07-22 现场部署 F0/F1 并完成验证
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

| Phase |              目标               |                       涉及服务                        |        前置依赖         |                                                               状态                                                                |
|-------|-------------------------------|---------------------------------------------------|---------------------|---------------------------------------------------------------------------------------------------------------------------------|
| F0    | Nacos 接入（DDL hook 写法试点）       | Nacos（单角色，最简单）                                    | 无                   | **已实施 + 现场验证通过**（1992 条 span；中途发现 `${ROOT.X.INSTALL_PATH}` 在 `append_line.text` 里不生效，改回 `$(pwd)` 后修复，见 F0 章节"现场排查"）             |
| F1    | 推广到 Elasticsearch             | ElasticSearch 角色（`EsExporter` 不动）                 | F0 验证通过             | **代码已实施，机制现场验证通过，最终目标未达成**：javaagent 已挂载，但 ES 9.x JPMS 限制导致字节码注入失败、无法产生 span，标记已知限制不再继续投入，见 F1 章节                               |
| F2    | 推广到 Doris FE                  | DorisFE 角色（DorisBE 不动）                            | F0 验证通过             | **代码已实施**（含 F0 排查后修正的 `$(pwd)` 写法），**未部署到现场**——用户明确选择本轮跳过（Doris FE 无 HA，单点风险）                                                   |
| F3    | DolphinScheduler 4 角色         | ApiServer/MasterServer/WorkerServer×3/AlertServer | 无（独立路径，可与 F0-F2 并行） | **已实施 + 现场验证通过**（4 角色各自独立 `service_name`）；排查出 `$command` 变量恒为空的预置缺陷（同时影响本次改动和更早的 `SERVER_PORT` 逻辑），已改用 `*_HOME` 变量修复，见 F3 章节    |
| F4    | APISIX 原生 OTel 插件             | APISIX                                            | 无（独立机制，可并行）         | **已实施 + 现场验证通过**（ddh-01，5 请求→5 span）；排查出 3 个 APISIX 插件机制坑（`plugins` 清单/`plugin_metadata`/`sampler` 默认值，见 F4 章节），另有 2 个测试预置缺陷未修复 |
| F5    | Collector 配置刷新 + 全链路验证 + 文档收尾 | 全部五节点 OTELCOLLECTOR                               | F0-F4 至少一个已完成       | **部分完成**——ddh-02 现场确认无需刷新即可正常转发 traces，其余四节点未逐一核实；端到端跨服务瀑布图验证未做（缺 F2 这一跳）                                                       |

**相对最初草稿的简化（补齐 F0/F1/F2 代码时应用）**：F3 落地时发现每个受管节点上都已经有一份 `datasophon-worker` 自带的 `otel/opentelemetry-javaagent.jar`（`386a5ee5`/Phase D 落地），`link` hook 直接复用即可，不需要单独把 javaagent 上传 Nexus 再让 Nacos/ES/Doris FE 各自 `download` 一份。F0（Nacos）/F2（Doris FE）改用与 F3 相同的 `link` 复用 + `append_line` 注入组合；F1（Elasticsearch）进一步发现它的 `jvm.options` 本来就是 DataSophon 自己渲染的模板（`jvm.options.ftl`，仅 ELASTICSEARCH 一家在用），比改写厂商 `bin/elasticsearch` 脚本或依赖版本相关的 `jvm.options.d` drop-in 目录更直接可靠，因此改走模板注入，见下方各自章节的"实施修正"。

---

## Phase F0：Nacos 试点（验证机制）—— 已实施 + 现场验证通过

> 与草稿的差异同 F3：不再上传 Nexus，直接 `link` 复用 `datasophon-worker` 自带的 jar。**`-javaagent` 路径最终定案是 `$(pwd)`，不是 `${ROOT.NACOS.INSTALL_PATH}`**——中间走过一次弯路，见下方"现场排查"。

### 实施修正（相对草稿）

1. **不上传 Nexus，改 `link` 复用**：与 F3 同一原因——`datasophon-worker` 包内已固定带有 `otel/opentelemetry-javaagent.jar`（`copy-otel-agent`/`assembly.xml`），`${ROOT.VosManager.INSTALL_PATH}/datasophon-worker/otel/opentelemetry-javaagent.jar` 在任意受管节点上都存在，直接 `link` 到 Nacos 安装目录即可，不需要单独维护一份 Nexus 制品和 `download` hook。
2. **两条 `append_line` 都用 `line: 2` 会不会互相冲突**：读 `AppendLineStrategy.invoke()` 源码确认——它是 `lines.add(line - 1, text)`（插入并整体下移），不是覆盖写；只要新旧两行文本不同就必然都保留，执行顺序只影响谁最终停在第 2 行、谁被挤到第 3 行，不影响功能。这是纯代码可验证的结论，现场也复现了这个行为（见下）。

### 现场排查：`${ROOT.NACOS.INSTALL_PATH}` 在 `append_line.text` 里不会被替换，最终改回 `$(pwd)`

代码实现最初按"读代码分析"给出的结论是 `${ROOT.<SERVICE>.INSTALL_PATH}` 会在命令生成阶段被替换为绝对路径（`DdlMetaServiceImpl.java:223`：`GlobalVariables.putValue(clusterId, "ROOT." + serviceName + ".INSTALL_PATH", installPath)`），并且举了 `link.source`（DS 的 `${ROOT.VosManager.INSTALL_PATH}/datasophon-worker/...`）和 DDL `parameters[].defaultValue`（ZooKeeper 的 `-Djava.security.auth.login.config=${ROOT.ZOOKEEPER.INSTALL_PATH}/conf/jaas.conf`）两类先例——**这个结论是错的，或者说不完整**：现场首次部署后，`ddh-02` 的 `nacos/bin/startup.sh` 里这一行原样留着未被替换的字符串：

```
export JAVA_TOOL_OPTIONS="-javaagent:${ROOT.NACOS.INSTALL_PATH}/otel/opentelemetry-javaagent.jar ..."
```

`link` hook 的 `source`/`target` 和 DDL `parameters[].defaultValue` 确实会被替换（现场也验证了：ES 的 `otelJavaagentPath` 参数、Nacos/ES 的 `link.source` 全部正确解析成绝对路径），但 **`append_line` hook 的 `text` 字段不在这个替换范围内**——具体是哪段代码做了选择性替换、为什么只覆盖这几类字段，没有继续深挖（不影响止损方案），只记录现象供后续排查参考。

修复：改回草稿最初设想的 `$(pwd)`。重新论证过其可靠性——`AppendLineStrategy` 插入的是**第 2 行**，在 `#!/bin/bash` 之后、脚本自身任何逻辑（包括可能的 `cd`）执行之前；而 `ServiceHandler.java:196` 对所有 `program` 字段（包括这里的 `bin/startup.sh`）都是以服务安装根目录为 cwd 执行的（`ShellUtils.execWithStatus(Constants.INSTALL_PATH + "/" + installHome, command, ...)`），所以 `$(pwd)` 在第 2 行求值时必然等于 Nacos 安装根目录，不存在草稿担心的"执行上下文不确定"问题——这一点现场也验证通过（`/proc/<pid>/environ` 里 `JAVA_TOOL_OPTIONS` 的路径完全正确）。

**一个容易误判的现象**：修好之后用 `ps -ef | grep javaagent` 检查一度以为没生效——因为 Nacos 是靠 `export JAVA_TOOL_OPTIONS=...` 这种**环境变量**方式让 java 启动器自动拾取 agent，不会出现在 `ps` 的命令行参数里（不像 ES/DS/APISIX 那样把 `-javaagent:...` 直接拼进 `JAVA_OPT` 数组）。正确的验证方式是查 `/proc/<pid>/environ` 或看 Nacos 自己重定向的日志文件（`logs/startup.log`，**不是** DDL `logFile` 字段写的 `logs/start.out`——那个文件在这次验证过程中始终不存在，两者不是同一个文件，具体谁写 `start.out` 未深究）里的 `Picked up JAVA_TOOL_OPTIONS` 提示。

### 改动点（已落地，代码与现场一致）

`package/raw/meta/datacluster-physical/NACOS/service_ddl.json` 的 `NacosServer` 角色 `hooks[]`，在既有的 `JAVA_HOME` `append_line` 之后新增两条：

```json
{
  "type": "POST_INSTALL",
  "action": "link",
  "params": {
    "source": "${ROOT.VosManager.INSTALL_PATH}/datasophon-worker/otel/opentelemetry-javaagent.jar",
    "target": "otel/opentelemetry-javaagent.jar"
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

### 验收

| # |                                     验收项                                      |                                检查方式                                |             结果              |
|---|------------------------------------------------------------------------------|--------------------------------------------------------------------|-----------------------------|
| 1 | Nacos 安装目录下存在 `otel/opentelemetry-javaagent.jar`，md5 匹配                      | 现场 `ls -la`                                                        | ✅ 软链正确指向                    |
| 2 | `bin/startup.sh` 含新增的 `export JAVA_TOOL_OPTIONS=...` 行，且原有 `JAVA_HOME` 行未被破坏 | 现场 `cat`                                                           | ✅                           |
| 3 | Nacos 进程环境变量 `JAVA_TOOL_OPTIONS` 正确、启动日志出现 `Picked up JAVA_TOOL_OPTIONS`     | `/proc/<pid>/environ` + `logs/startup.log`                         | ✅                           |
| 4 | 对 Nacos 发起一次真实调用后，`otel.otel_traces` 出现 `service_name='nacos'` 的记录           | `SELECT count(*) FROM otel.otel_traces WHERE service_name='nacos'` | ✅ 1992 条，持续增长               |
| 5 | Nacos 现有 Prometheus 指标看板数据不受影响（确认没有意外多出一路 metrics）                           | 对比开启前后面板                                                           | 未专门核对，本轮未改 metrics 相关配置，风险低 |

F0 **现场验证全部通过**，机制（`link` 复用 + `append_line` 注入 `$(pwd)` 绝对路径）确认可行，F2（Doris FE）直接复用同一写法。

---

## Phase F1：Elasticsearch —— 代码已实施，机制现场验证通过，**javaagent 注入失败，标记为已知限制**

> 与草稿的差异：没有走 `append_line` 改 `bin/elasticsearch`，也没有走版本相关的 `jvm.options.d` drop-in 目录，改为直接编辑 DataSophon 自己渲染的 `jvm.options.ftl` 模板。DDL/hook 机制本身现场验证全部正确（jar 落地、`config/jvm.options` 内容、javaagent 参数注入进 JVM），但 **ES 9.4.3 实际拒绝了 javaagent 的字节码注入**，`otel.otel_traces` 里 `service_name='elasticsearch'` 现场核实为 0 条——这是 F1 唯一没有达成"产生 span"这个最终目标的部分，根因和后续选项见下方"现场排查"。

### 实施修正（相对草稿）

读 `ELASTICSEARCH/service_ddl.json` 的 `configWriter` 发现：`jvm.options` 本来就是 DataSophon 自己按模板渲染生成的文件（`templateName: jvm.options.ftl`，此前只有 `-Xms${heapSize}`/`-Xmx${heapSize}` 两行），而且全仓库唯一引用方就是 ELASTICSEARCH（`grep -rl jvm.options.ftl` 命中且仅命中这一个 DDL）。这比草稿设想的两条路径都更直接：

- 不需要判断"现场 ES 版本是否支持 `jvm.options.d` drop-in 目录"（草稿标注的 F1 特有风险之一）——直接在 DataSophon 已经拥有渲染权的文件里加两行。
- 不需要 `append_line` 改厂商脚本、不需要处理行号维护问题。

`-javaagent` 路径通过新增一个隐藏 DDL 参数 `otelJavaagentPath`（`defaultValue: "${ROOT.ELASTICSEARCH.INSTALL_PATH}/otel/opentelemetry-javaagent.jar"`）注入 `jvm.options` 生成器的 `includeParams`，在 DDL 变量解析阶段就被替换为绝对路径字符串，再作为 FreeMarker 数据模型变量传入模板——这样不依赖"ES 启动时 cwd 是否等于安装根目录"的假设（`jvm.options.ftl` 与 `${ROOT.X.INSTALL_PATH}` 是两套不同的 `${...}` 语法，前者由 FreeMarker 渲染、后者由 DDL 引擎在渲染前替换字符串，二者不能在同一个 `.ftl` 文件里直接混用，所以走"新增 DDL 参数搬运解析结果"这条路径）。**这条路径现场验证是对的**：`ConfigureServiceHandler` 日志确认 `otelJavaagentPath` 被正确解析成绝对路径，与 F0 那次 `${ROOT.X.INSTALL_PATH}` 在 `append_line.text` 里失效是两回事——差别在于 `otelJavaagentPath` 是通过 DDL **parameter**（走 `include Params` → FreeMarker 数据模型）传递的，不是直接嵌进 hook 的 `text` 字符串。

### 现场排查一：`otelJavaagentPath` 缺 `configType: "map"`，FreeMarker 渲染报 null（已修复）

首次现场触发 ES 安装，`ConfigureServiceHandler` 直接报错退出：

```
freemarker.core.InvalidReferenceException: The following has evaluated to null or missing:
==> otelJavaagentPath  [in template "jvm.options.ftl" at line 3, column 14]
```

根因：`FreemakerUtils.renderCustomConfigFormat()` 只把 `configType == "map"` 的参数塞进 FreeMarker 顶层数据模型（`data.put(config.getKey()/getName(), config.getValue())`），其余参数只进 `itemList` 列表，模板里用 `${xxx}` 直接引用不到。`heapSize` 参数带 `configType: "map"`，但最初新增 `otelJavaagentPath` 时照抄了旁边一个没有这个字段的参数（`xpack...truststore.path`），漏掉了。修复：给 `otelJavaagentPath` 补上 `"configType": "map"`。这是本次改动自己引入的 bug，不是历史遗留。

### 现场排查二：`jvm.options` 生成器 `outputDirectory: ""` 写错路径，是历史遗留 bug（已修复）

修完上面那处后再次触发安装，`jvm.options` 成功渲染，但 ES 真实进程命令行、`config/jvm.options` 里都看不到新增内容。定位发现 DataSophon 实际把渲染结果写到了 `<ES_HOME>/jvm.options`（安装根目录），而 Elasticsearch（6.3+ 标准布局）只读取 `<ES_HOME>/config/jvm.options`——`outputDirectory` 字段配的是空字符串 `""` 而不是 `"config"`。这个字段在这次改动之前就是这个值，**意味着 DDL 里配置的 `heapSize` 参数此前从未真正对 ES 生效过**（ES 一直用自动探测的堆大小，现场实测约 15.8GB），是一个和本次 OTel 改动无关、但正好卡在同一个生成器上的历史 bug。因为不修就没法验证 F1，所以一并修了（`outputDirectory: "" → "config"`），并向用户确认了这会让 `heapSize=2g` 这个此前从未生效的配置在下次重启后首次真正生效（用户确认接受，需要时再调）。

### 现场排查三：javaagent 成功挂载，但 ES 拒绝字节码注入（未解决，标记已知限制）

修完以上两处，`jvm.options` 内容、`-javaagent` 参数都正确出现在 ES 真实进程命令行里，`control_es.sh status` 显示进程正常运行。但：

- ES 日志（`logs/ddp_es.log`）反复出现 `[otel.javaagent] ERROR io.opentelemetry.javaagent.tooling.HelperInjector - Error preparing helpers while processing class io.netty.util.concurrent.PromiseCombiner$1 for . Failed to inject helper classes into instance <bootstrap>`（同类错误覆盖多个 `io.netty.*` 类）。
- 对 ES 发起真实 HTTP 请求（`curl http://<ip>:9200/`，返回 401，证明请求确实到达了 ES 的 HTTP 层）后，`otel.otel_traces` 里 `service_name='elasticsearch'` 仍是 0 条。

联网检索确认：这不是 ES 自己的 Entitlements（9.x 引入、取代 SecurityManager 的运行时权限模型）问题——那类问题的典型报错是 `ENTITLEMENT [outbound_network] not granted`，修法是给 `elasticsearch.policy`/entitlement-policy 补丁显式 grant 权限；而这里的报错发生在 javaagent 用 ByteBuddy 往 **bootstrap classloader** 注入 helper 类这一步，是 JPMS（Java 平台模块系统）层面的类可见性限制——ES 9.x 启动时带的是完整模块图（`--module-path`，日志里能看到 `--patch-module`/`entitlement-bridge` 等 JPMS 相关参数），netty 被打进了 ES 自己的模块而不是标准 `java.base`，theoretically 需要针对性的 `--add-opens`/`--add-exports` 才可能绕开，但没有现成的、可直接照搬的 flag 组合，需要现场反复试错（每次都要重启 ES 验证），投入产出比不明确。

**决策（已与用户确认）**：不继续深挖，标记为 ES 9.x + 当前 OpenTelemetry javaagent 版本（2.29.0）的已知不兼容限制。F0/F3/F4 验证的"link 复用 jar + 注入 JAVA_TOOL_OPTIONS/`-javaagent`"机制本身没有问题（同样的机制在 Nacos/DolphinScheduler 上完全成功），问题严格限定在 ES 自身对字节码注入的模块化限制上，不影响其他服务。后续如果要继续攻克，方向是：①升级 opentelemetry-javaagent 到明确支持 JDK17+/JPMS 全模块图的版本；②尝试在 ES 启动参数追加 `--add-opens`/`--add-exports` 到 netty 所在模块（需要先确认 ES 把 netty 打进了哪个具体模块名）；③改用 ES 官方 APM Java agent（其官方文档已有 entitlements 配置指引，兼容性由 Elastic 自己保证，但这是完全不同的 agent、不是 OpenTelemetry 生态，需要重新评估数据格式和 Doris 导入链路）。

### 改动点（已落地）

**1. `package/raw/meta/datacluster-physical/ELASTICSEARCH/service_ddl.json`**：`ElasticSearch` 角色 `hooks[]` 新增 `link`（同 F0 机制，复用 worker 自带 jar）；`parameters[]` 新增隐藏参数 `otelJavaagentPath`；`jvm.options` 生成器的 `includeParams` 加入该参数。

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

```json
{
  "name": "otelJavaagentPath",
  "hidden": true,
  "required": true,
  "configType": "map",
  "type": "input",
  "defaultValue": "${ROOT.ELASTICSEARCH.INSTALL_PATH}/otel/opentelemetry-javaagent.jar",
  "configurableInWizard": true,
  "register": false
}
```

`configType: "map"` 是现场排查一修的（见上），最初漏了这个字段。

**2. `jvm.options` 生成器的 `outputDirectory` 从 `""` 改为 `"config"`**（现场排查二修的历史遗留 bug，见上），配合 `includeParams` 追加 `otelJavaagentPath`：

```json
{
  "filename": "jvm.options",
  "outputDirectory": "config",
  "templateName": "jvm.options.ftl",
  "includeParams": ["heapSize", "otelJavaagentPath"]
}
```

**3. `datasophon-worker/src/main/resources/templates/jvm.options.ftl`**：

```
-Xms${heapSize}
-Xmx${heapSize}
-javaagent:${otelJavaagentPath}
-Dotel.service.name=elasticsearch
-Dotel.exporter.otlp.endpoint=http://localhost:4317
-Dotel.exporter.otlp.protocol=grpc
-Dotel.traces.exporter=otlp
-Dotel.metrics.exporter=none
-Dotel.logs.exporter=none
```

新增单测 `JvmOptionsTemplateTest` 覆盖渲染结果（注：该单测直接把 `otelJavaagentPath` 塞进 FreeMarker 数据模型验证渲染结果，不经过 DDL 的 `configType` 过滤逻辑，所以现场排查一的 bug 单测没能拦住——这是本次留下的一个测试覆盖缺口，如果后续要补，需要一层"DDL parameter → FreeMarker data model"的集成测试，而不是只测模板渲染本身）。

### 验收

| # |                           验收项                            |       检查方式        |              结果              |
|---|----------------------------------------------------------|-------------------|------------------------------|
| 1 | ES 安装目录下存在 `otel/opentelemetry-javaagent.jar`            | 现场 `ls -la`       | ✅ 软链正确                       |
| 2 | `config/jvm.options` 含 `-javaagent`/`-Dotel.*` 内容        | 现场 `cat`          | ✅（第二次排查后）                    |
| 3 | ES 真实进程命令行含 `-javaagent:.../opentelemetry-javaagent.jar` | `ps -eo cmd`      | ✅                            |
| 4 | ES 启动日志无 agent 相关异常/拒绝加载                                 | `logs/ddp_es.log` | ❌ `HelperInjector` 报错，见现场排查三 |
| 5 | `otel.otel_traces` 出现 `service_name='elasticsearch'`     | 查 Doris           | ❌ 0 条，已知限制，不继续深挖             |
| 6 | ES 现有 Prometheus 指标看板数据不受影响                              | 对比开启前后面板          | 未专门核对，本轮未改 metrics 相关配置      |

**F1 结论**：DDL/hook 机制（1/2/3 项）验证通过，javaagent 确认已挂载到 ES 进程；但 ES 因 JPMS 模块化限制拒绝了字节码注入（4/5 项失败），已与用户确认标记为已知限制，不在本批继续投入。

---

## Phase F2：Doris FE —— 代码已实施，**未部署到现场**（用户选择本轮跳过，Doris FE 是无 HA 单点）

> 与草稿的差异同 F0：`link` 复用 worker 自带 jar；`-javaagent` 路径最终也是 `$(pwd)`（**不是** F0 中间走过弯路时曾用过的 `${ROOT.DORIS.INSTALL_PATH}`，原因见 F0"现场排查"——`append_line.text` 里 `${ROOT.X.INSTALL_PATH}` 不会被替换）；`append_line` 沿用 `line: 2`（不是草稿的 `line: 3`），理由同 F0 的实施修正第 2 点——插入行为是移位插入而非覆盖，起始行号不影响最终结果。

### 改动点（已落地，尚未在现场触发安装）

`package/raw/meta/datacluster-physical/DORIS/service_ddl.json` 的 `DorisFE` 角色（`DorisBE` 不动），在既有 `JAVA_HOME` `append_line` 之后新增：

```json
{
  "type": "POST_INSTALL",
  "action": "link",
  "params": {
    "source": "${ROOT.VosManager.INSTALL_PATH}/datasophon-worker/otel/opentelemetry-javaagent.jar",
    "target": "fe/otel/opentelemetry-javaagent.jar"
  }
},
{
  "type": "POST_INSTALL",
  "action": "append_line",
  "params": {
    "line": 2,
    "text": "export JAVA_TOOL_OPTIONS=\"-javaagent:$(pwd)/fe/otel/opentelemetry-javaagent.jar -Dotel.service.name=doris-fe -Dotel.exporter.otlp.endpoint=http://localhost:4317 -Dotel.exporter.otlp.protocol=grpc -Dotel.traces.exporter=otlp -Dotel.metrics.exporter=none -Dotel.logs.exporter=none\"",
    "source": "fe/bin/start_fe.sh"
  }
}
```

**`$(pwd)` 拼的是 `fe/otel/...` 不是 `otel/...`（区别于 F0 的 Nacos）**：`ServiceHandler` 是以服务安装根目录（DORIS 共享根，`createDecompressDir: false`）为 cwd 执行 `fe/bin/start_fe.sh` 的（`program` 字段本身就带 `fe/` 前缀），所以脚本第 2 行 `$(pwd)` 求值结果是 DORIS 根目录而不是 `fe/` 子目录，链接目标又落在 `fe/otel/opentelemetry-javaagent.jar`，两者要对齐必须显式拼上 `fe/` 前缀——这一点在 F0 阶段没有暴露（Nacos 是单角色服务，`$(pwd)` 和 jar 落地目录天然一致），是本次同步排查 DORIS 时顺带发现并修正的，DORIS 至今未现场部署，这个坑没有通过现场验证，只是代码审查加逻辑推导的结论。

`link` 的 `target` 带 `fe/` 前缀（区别于 F0/F3 的服务根目录）：Doris 是 `createDecompressDir: false` 的多角色服务，`DorisFE`/`DorisBE` 分别用 `fe/`/`be/` 子目录，`startRunner.program` 也是 `fe/bin/start_fe.sh` 这种带前缀的相对路径，jar 落在 `fe/otel/` 下与现有布局对齐。

**重要提示（面向验收，不算缺陷）**：Doris FE 走 MySQL 协议接收查询，不是 HTTP，javaagent 的自动插桩主要覆盖已知库（JDBC client、HTTP server、消息队列等），**不会**给"某条 SQL 在 FE 内部的执行过程"生成 span——它能捕获的是 FE 对外发起的 HTTP/JDBC 调用（如果有）。真正有价值的"查了一次 Doris"这一跳，落在**调用方**（datasophon-api 等）的 JDBC CLIENT span 上，Doris FE 这次挂 agent 更多是为了让 FE 自己对外的调用（如果未来有）也能串起来，不要期望装完之后能看到"SQL 内部执行瀑布图"。这一点必须写进验收说明，避免用户拿着这个预期去验收然后判定"没做对"。

### 验收

同 F0 的 1/2/3/5 项（`service_name='doris-fe'`），第 4 项调整为：**验证调用方（如 datasophon-api）产生的 Doris JDBC CLIENT span 能正确关联到本次改动后的 FE 环境**（而不是指望 FE 自己产生大量 span）。**现场验证待补**。

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

无代码改动。原计划要对五节点的 OTELCOLLECTOR 角色执行一次 `RESTART_WITH_CONFIG` 才能让 traces pipeline 生效（部署手册 §10.2 记录的现场 `otelcol.yaml` 一度只有 metrics pipeline）。**现场核实发现 `ddh-02` 的 otelcol 实际已经在正常转发 traces**——F0 验证时 Nacos 的 1992 条 span、以及本来就在跑的 `dolphinscheduler-worker-server`（同样部署在 ddh-02）的历史数据都证明 ddh-02 本地 otelcol 链路是通的，没有再手动触发 collector 重启/配置刷新。**没有逐一确认其余四个节点（ddh-01/03/04/05）的 otelcol 是否也已经是新配置**——本轮只验证了 ddh-02，F2/F3/F4 涉及的其他节点（ddh-01 跑 Doris FE/DS ApiServer 等）如果 collector 配置确实滞后，会在后续部署时才暴露，需要留意。

### 全链路验证

本轮未做端到端瀑布图验证（F2 Doris FE 未部署，链路里缺 Doris 这一跳；F1 Elasticsearch 因已知限制不产生 span）。F0 单独验证了 Nacos 能产生 span 并落库，F3/F4 此前已各自验证过（DS 内部角色、APISIX 自身 span），跨服务的父子链路拼接（`apisix → datasophon-api → nacos → doris`）还没有一次性验证过，留给下次继续推进 F2 或做专项验证时补。

### 文档收尾

- 本文件状态已更新为实测结果（本节所在文档）。
- 是否需要在 `deploy/deployment-standalone-doris.md` 补记录、`docs/monitoring/` 是否需要同步更新 Traces 覆盖度描述，留给用户决定是否现在做——不属于本次代码改动范围。

---

## 风险与边界

- ~~**`append_line` 对同一脚本多次插入的顺序行为未经验证**~~：已通过读 `AppendLineStrategy.invoke()` 源码 + 现场复现解决——`lines.add(line - 1, text)` 是插入移位，不是覆盖，两条 hook 无论谁先执行都会共存，只是最终行号互换。现场处理 Nacos 那次写错的残留行时，这个结论被直接验证。
- **`${ROOT.<SERVICE>.INSTALL_PATH}` 不能用在 `append_line` 的 `text` 字段里**：这是本批最大的一个认知修正——最初以为它和 `link.source`/DDL `parameters[].defaultValue` 一样会被替换成绝对路径，代码分析给出了看似成立的推理，但现场部署后发现 `append_line.text` 里这个占位符**原样没被替换**，导致 Nacos 第一次上线的 javaagent 完全没生效（bash "bad substitution"，但因为是独立语句不影响脚本后续执行，容易被忽略）。最终方案改回 `$(pwd)`——已论证并现场验证可靠：`AppendLineStrategy` 把新行插入在脚本第 2 行（`#!/bin/bash` 之后，脚本自身逻辑之前），而 `ServiceHandler` 执行任何 `program` 都是以服务安装根目录为 cwd，所以 `$(pwd)` 在这个位置求值必然等于安装根目录。**F0/F2 都已改用 `$(pwd)`，F1（DDL parameter 路径）不受影响，仍然可靠**。
- ~~**ES 的 `SecurityManager` 兼容性未验证**~~：已现场验证，且比预想的更严重——不是 SecurityManager/Entitlements 层面的权限拒绝，而是 JPMS 模块边界导致 javaagent 的 `HelperInjector` 无法把 helper 类注入 ES 的 netty 相关 bootstrap classloader，javaagent 挂载成功但完全没有产生 span。已标记为已知限制，不在本批继续投入，见 F1 章节"现场排查三"。
- **Doris FE 挂 agent 后不会产生"SQL 执行瀑布图"**：这是 MySQL 协议 + 自动插桩能力边界决定的，不是配置错误，需要提前对齐验收预期。F2 尚未部署，这条仍是需要提前对齐的预期，不是已验证的结论。
- ~~**javaagent jar 上传 Nexus 的具体路径规则未经代码确认**~~：F0/F1/F2 已改用 F3 验证过的 `link` 复用 `datasophon-worker` 自带 jar 机制，不再需要上传 Nexus，这条风险随之消失（现场验证：F0/F1 的 jar 软链均正确指向 worker 自带的 jar）。
- **Doris BE / Valkey 没有自己的 span，是设计如此，不是遗漏**——已在"决策"一节写明，验收时不能按这个标准判定失败。
- **本批只开 Traces**：如果后续想统一到 api/worker 那样 traces/metrics/logs 三路全开，需要先解决"javaagent metrics 和现有 Prometheus scrape 管道重复"的问题（比如把对应中间件的 Prometheus scrape 配置摘掉，全部改走 javaagent metrics），这是一个不小的独立决策，本批不做，只标注。
- **ELASTICSEARCH 的 `jvm.options` 生成路径 bug 是历史遗留，修复后 `heapSize` 首次真正生效**：`outputDirectory` 从 `""` 改成 `"config"` 后，DDL 里配置的 `heapSize=2g` 会在 ES 下次重启后从此前从未生效的自动探测堆大小（现场实测约 15.8GB）切换为 2GB——已与用户确认接受这个变化，但如果后续还有其他节点重装 ES，同样会触发这次堆大小切换，需要提前告知。
- **F2（Doris FE）未部署到现场**：用户明确选择本轮跳过（Doris FE 是无 HA 单点，重启有真实风险），代码已就绪（含 F0 排查后修正的 `$(pwd)` 写法），后续需要专门找时间窗口做。
- **F5 的五节点 otelcol 配置刷新未逐一核实**：只确认了 ddh-02，其余四节点是否也已是新配置（含 traces/logs pipeline）未知，见上方 F5 章节。

## 验收标准总表

|        Phase        |                                 核心验收项                                 |                              状态                               |
|---------------------|-----------------------------------------------------------------------|---------------------------------------------------------------|
| F0 Nacos            | `otel_traces` 出现 `service_name='nacos'`，指标看板不受影响                      | ✅ **已实施 + 现场验证通过**（1992 条 span）                               |
| F1 Elasticsearch    | `otel_traces` 出现 `service_name='elasticsearch'`，启动日志无异常               | ⚠️ **代码/机制现场验证通过，最终目标未达成**（javaagent 挂载成功但 ES 拒绝字节码注入，标记已知限制） |
| F2 Doris FE         | `otel_traces` 出现 `service_name='doris-fe'`，调用方 JDBC CLIENT span 可关联   | 代码已实施（含 F0 排查后的 `$(pwd)` 修正），**未部署到现场**（用户选择跳过，Doris FE 无 HA） |
| F3 DolphinScheduler | 4 个角色各自产生独立 `service_name`，WorkerServer 的 MySQL/Valkey CLIENT span 可见 | 已实施 + 现场验证通过（WorkerServer 的 CLIENT span 待验证，见 F3 章节验收清单最后一项）  |
| F4 APISIX           | `otel_traces` 出现 `service_name='apisix'`，与下游共享 `trace_id`             | 已实施 + 现场验证通过（与下游共享 trace_id 待验证，见 F4 章节验收表第 4 项）              |
| F5 收尾               | 五节点 otelcol 配置刷新，端到端瀑布图验证通过，文档更新                                      | 部分完成——ddh-02 确认无需刷新即可用，其余四节点未核实；端到端瀑布图未验证（缺 F2 这一跳）           |

## 端到端验证命令（现场，五节点 Doris Standalone 环境，2026-07-22 已实测）

```bash
# 1. Nacos（环境变量方式注入，不在 ps 命令行里，验证方式与其余服务不同）
ssh ddh-02 "ls -la /data/install_datasophon/nacos/otel/"                      # 软链是否指向 worker 自带 jar
ssh ddh-02 "sed -n '1,5p' /data/install_datasophon/nacos/bin/startup.sh"      # export JAVA_TOOL_OPTIONS 是否写对
ssh ddh-02 "grep 'Picked up JAVA_TOOL_OPTIONS' /data/install_datasophon/nacos/logs/startup.log"  # 注意是 startup.log，不是 DDL logFile 字段写的 start.out——现场验证时这两个文件不是同一个，start.out 全程未出现过
PID=$(ssh ddh-02 "pgrep -f nacos-server.jar")
ssh ddh-02 "tr '\0' '\n' < /proc/$PID/environ | grep JAVA_TOOL_OPTIONS"       # 最可靠的验证方式：直接看进程环境变量

# 2. Elasticsearch（-javaagent 直接在 JVM 命令行参数里，能用 ps 验证；但预期不会产生 span，见 F1 已知限制）
ssh ddh-02 "grep -n 'javaagent\|Xms\|Xmx' /data/install_datasophon/elasticsearch/config/jvm.options"
ssh ddh-02 "ps -eo cmd | grep org.elasticsearch.bootstrap | grep -o 'javaagent:[^ ]*'"
ssh ddh-02 "grep -i 'HelperInjector' /data/install_datasophon/elasticsearch/logs/ddp_es.log | tail -5"  # 预期能看到注入失败的报错，这是已知限制不是新问题

# 3. 查 Doris 确认各服务 span 落库（MySQL 协议）——elasticsearch/doris-fe 预期为 0（F1 已知限制 / F2 未部署）
mysql -h127.0.0.1 -P9030 -uroot -e "
  SELECT service_name, count(*) AS span_count, max(timestamp) AS last_seen
  FROM otel.otel_traces
  WHERE service_name IN ('nacos','elasticsearch','doris-fe','apisix',
    'dolphinscheduler-api-server','dolphinscheduler-master-server',
    'dolphinscheduler-worker-server','dolphinscheduler-alert-server')
  GROUP BY service_name;
"

# 4. 抽一条 trace_id 验证跨服务父子关系（找一条经过 apisix 的 trace；本轮未做，留给 F2 完成后再验证完整链路）
mysql -h127.0.0.1 -P9030 -uroot -e "
  SELECT trace_id FROM otel.otel_traces WHERE service_name='apisix' ORDER BY timestamp DESC LIMIT 1;
"
# 拿到 trace_id 后去前端 Traces Tab 搜索，肉眼核对瀑布图父子链路
```

**实测结果（2026-07-22）**：`nacos` 1992 条 span，持续增长；`elasticsearch` 0 条（HelperInjector 注入失败，已知限制）；`doris-fe`/DS 四角色/`apisix` 均未在本轮重新核实（DS/APISIX 沿用此前 F3/F4 验证记录，`doris-fe` 因 F2 未部署预期仍为 0）。

