# 数据血缘 L0 现场核查报告

> **对应** `docs/data-lineage-平台级血缘架构-2026-07-29.md` 的 **L0 — 现场核查 spike**（9 项）
> **环境**：五节点沙箱，Gravitino 1.3.0 运行于 ddh-02（192.168.10.132:8090），
> commit `40fdf6ab`，编译日期 2026-06-24
> **日期**：2026-07-30
> **状态**：**部分完成**。第 1 / 3 / 9(部分) 项已有结论；**第 2 项推翻了架构文档的一个核心前提，
> 且 JDBC 子项已实机采样，发现现有 `CanonicalNameResolver` 会拒绝 100% 真实 JDBC 事件**；
> 第 4 / 5 / 6 / 7 / 8 项未执行（依赖 Spark 作业与 ≥24h 采集周期）；Hive/Paimon/Iceberg 三种
> catalog 类型仍缺前置条件（阶段 B 未部署）

---

## 0. 一句话结论

**Gravitino 不做 identifier 规范化** —— 服务端只有 `NoopProcessor`，Spark 插件与 OpenLineage
完全无关。架构文档 §2.1 与 §3.3 中「经 Gravitino / `GravitinoSparkPlugin` 转换成
`metalake.catalog.schema.table`」的陈述**不成立**，需要回写。

---

## 1. 核查结果总表

| # |                    待核实                    |        状态         |                                                                                         结论                                                                                          |
|---|-------------------------------------------|-------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 | `/api/lineage` 在 1.3.0 部署上是否可用            | ✅ 已确认             | **可用**，返回 `201`，空响应体；事件落 `gravitino_lineage.log`                                                                                                                                    |
| 2 | Gravitino 转换后 dataset 的确切拼写               | 🟡 前提被推翻，JDBC 已采样 | **Gravitino 根本不转换**（§3.1-3.4）；真正的命名方由 `openlineage-spark` 决定。**JDBC 已实机采样**（§3.5）：`mysql://host:port` + `db.table`，与架构文档假设不符，且证实现有 resolver 拒绝全部真实事件；Hive/Paimon/Iceberg 仍未采样（§3.6） |
| 3 | 沙箱是否有 Spark 作业可用于产出真实血缘                   | ✅ 已确认             | **没有**。ddh-02 上 `find /opt /data -iname '*spark*'` 零结果                                                                                                                              |
| 4 | HTTP sink 的重试/超时行为                        | ⬜ 未执行             | 已知 sink 支持 `authType` ∈ {`apiKey`, `none`}（见 §5）                                                                                                                                    |
| 5 | DS 3.4.1 能否拉到 SQL 任务定义文本                  | ⬜ 未执行             | —                                                                                                                                                                                   |
| 6 | 事件量与结构变化率实测（≥24h）                         | ⬜ 未执行             | 需要真实 Spark 作业持续运行                                                                                                                                                                   |
| 7 | structural hash 的稳定性                      | ⬜ 未执行             | 需要同一作业的连续多次运行                                                                                                                                                                       |
| 8 | 事件能否提供可靠的单调顺序                             | ⬜ 未执行             | 需要真实事件样本；当前实现已按「拿不到则降级 `received_at` 并写 `parse_log`」处理                                                                                                                              |
| 9 | 重复投递时 `(producer, runId, eventType)` 是否稳定 | 🟡 部分             | **Gravitino 不做任何去重**：同一事件两次投递 → 日志两条（见 §4）。Gravitino 自身重试行为未测                                                                                                                       |

---

## 2. L0 #1 —— `/api/lineage` 可用（已确认）

请求（`sinks` 走默认值 `log`，`gravitino.conf` 中无任何 `lineage.*` 配置）：

```bash
curl -X POST http://192.168.10.132:8090/api/lineage \
  -H 'Content-Type: application/json' --data-binary @probe.json
# → HTTP 201，响应体为空
```

`runId` 必须是**合法 UUID**，否则 `400 / code 1001`：

```json
{"code":1001,"type":"IllegalArgumentException","message":"Malformed json request",
 "stack":["com.fasterxml.jackson.databind.exc.InvalidFormatException:
   Cannot deserialize value of type `java.util.UUID` ... Non-hex character 'l'"]}
```

日志落盘位置由 `conf/log4j2.properties` 的 `appender.lineage_file.fileName` 决定：
`${basePath}/gravitino_lineage.log`，按天滚动为 `gravitino_lineage_%d{yyyyMMdd}.log.gz`。
沙箱实际路径 `/data/install_datasophon/gravitino-1.3.0-bin/logs/gravitino_lineage.log`。

---

## 3. L0 #2 —— Gravitino 不做 identifier 规范化（**推翻架构前提**）

### 3.1 三条独立证据

|    证据    |                                                                                      内容                                                                                      |
|----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 服务端 jar  | `libs/gravitino-lineage-1.3.0.jar` 的 `processor` 包**只有** `NoopProcessor` 与接口 `LineageProcessor`，没有任何规范化实现；`processorClass` 默认值即 `NoopProcessor`                              |
| 实机透传     | 发送 `namespace=probe-ns` / `name=probe_in`，log sink 输出**逐字未变**                                                                                                                |
| Spark 插件 | `gravitino-spark-connector-runtime-3.5_2.12:1.3.0` 整个 jar **零个 openlineage/lineage 条目**；`GravitinoDriverPlugin` 只注册 Paimon/Iceberg SessionExtensions 与 `spark.sql.catalog.*` |

实机透传的原始日志（两次投递，逐字相同）：

```text
[2026-07-30 10:59:50] {"eventTime":"2026-07-30T02:30:00Z","producer":"https://datasophon.local/l0-probe",
"schemaURL":"https://openlineage.io/spec/2-0-2/OpenLineage.json#/$defs/RunEvent","eventType":"complete",
"run":{"runId":"0195f2c0-0000-7000-8000-000000001001"},"job":{"namespace":"l0-probe","name":"canonical-name-probe"},
"inputs":[{"namespace":"probe-ns","name":"probe_in"}],"outputs":[{"namespace":"probe-ns","name":"probe_out"}]}
```

### 3.2 需要回写的两处架构陈述

|  位置  |                                                        原文                                                        |                              事实                              |
|------|------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------|
| §2.1 | 图示标注「Gravitino :8090 /api/lineage（identifier 规范化 → metalake.catalog.schema.table）」                               | Gravitino 只接收 + 转发，`NoopProcessor` 不改写任何字段                   |
| §3.3 | 「Spark 经 `GravitinoSparkPlugin` 上报的 dataset identifier **已被转换成** Gravitino 的 `metalake.catalog.schema.table` 格式」 | `GravitinoSparkPlugin` 是 **catalog 插件**，与 OpenLineage 事件生成无关 |

### 3.3 对 D1 决策的影响

D1 的理由陈述「留 Gravitino 在链路里的**唯一理由**是 identifier 规范化」**不成立**。
它剩余的实际价值：现成的 HTTP 收集端点 + `sinkQueueCapacity` 队列 + apiKey 认证 + 转发。
不为零，但显著小于原判断 —— **是否仍值得让血缘链路依赖 GRAVITINO RUNNING，建议在 L2 前重新评估**。

反向的好消息：规范化责任本就落在我们自己的 `CanonicalNameResolver` 上，不必逆向 Gravitino 的
拼写；而 `openlineage-spark` 的 naming 规范是公开且稳定的。

### 3.4 #2 的剩余部分（仍需实机采样）

问题形态已改变：

> ~~Gravitino 转换后 dataset 的 namespace/name 拼写是什么~~
> → **`openlineage-spark` 对我们实际使用的 catalog 类型（Hive / Paimon / Iceberg / JDBC）
> 产出什么 `namespace` / `name`**

这仍是**整个 epic 的生死点**，且**必须实机采样**（openlineage-spark 对各 catalog 的解析
存在版本差异与回退分支）。**2026-07-30 补测：JDBC 一项已完成采样，见 §3.5**；
Hive / Paimon / Iceberg 仍缺前置条件（阶段 B 未部署，沙箱无 Hadoop/Hive metastore/Paimon
warehouse，见 §3.6）。

### 3.5 JDBC 的真实采样（已完成，`openlineage-spark` 1.29.0 + Spark 3.5.3）

> **环境**：借用 `deploy/deployment-standalone-doris.md` 的五节点沙箱，用户已确认可用于此项验证。
> 未对该沙箱做任何持久变更——所建的临时表/库/文件在采样后已全部清理（见 §3.5.4）。

#### 3.5.1 方法

沙箱本身没有 Spark（L0 #3），本机下载 `spark-3.5.3-bin-hadoop3.tgz`（Apache Archive）与
`openlineage-spark_2.12:1.29.0`（Maven Central）后 scp 到 ddh-02，`local[1]` 模式跑
`spark-shell`，`spark.openlineage.transport.type=console`（直接从 driver 日志读原始事件
JSON，不经过 Gravitino，避免引入额外变量）。JDBC 驱动复用节点上已有的
`mysql-connector-j-8.2.0.jar`（`datasophon-worker` 自带）。

两组独立样本：

1. **MySQL → MySQL**：读 `datasophon.t_ddh_frame_service`（平台自身的真实表），
   写入临时表 `datasophon.l0_probe_output`
2. **MySQL → Doris**：读同一张源表，写入 Doris（`192.168.10.131:9030`，MySQL 协议）
   的临时表 `l0_probe.doris_output` —— 验证架构文档举例的 `doris://` scheme 是否成立

#### 3.5.2 结果：真实格式与架构文档假设不一致

在 START / RUNNING / COMPLETE 三个事件阶段完全一致，逐字节采样：

|      场景       |            `namespace`            |              `name`              |
|---------------|-----------------------------------|----------------------------------|
| MySQL 源表      | `mysql://192.168.10.131:3306`     | `datasophon.t_ddh_frame_service` |
| MySQL 目标表     | `mysql://192.168.10.131:3306`     | `datasophon.l0_probe_output`     |
| **Doris 目标表** | **`mysql://192.168.10.131:9030`** | `l0_probe.doris_output`          |

原始事件（MySQL→MySQL 写入 job 的 COMPLETE 事件，节选）：

```json
{"eventType":"COMPLETE","job":{"namespace":"l0-jdbc-probe",
 "name":"spark_shell.execute_save_into_data_source_command.l0_probe_output"},
 "inputs":[{"namespace":"mysql://192.168.10.131:3306","name":"datasophon.t_ddh_frame_service"}],
 "outputs":[{"namespace":"mysql://192.168.10.131:3306","name":"datasophon.l0_probe_output"}]}
```

**两条结论**：

1. **`namespace` 只到 `scheme://host:port`，不含 database**；`name` = `<database>.<table>`
   （**点号**分隔，不是斜杠）。架构文档 §3.3 举的例子
   `mysql-cdc://10.0.0.5:3306/app_db/orders`（scheme 用 `mysql-cdc`、db/table 用斜杠塞进
   namespace）**两处都与实测不符**。
2. **Doris 经标准 JDBC 访问时，scheme 是 `mysql`，不是 `doris`**——`openlineage-spark` 的
   JDBC facet 完全由 JDBC 驱动类/连接串决定，不识别后端产品身份。架构文档 §3.3 举的例子
   `doris://ddh/ads/ads_gmv` 是**设想的目标格式，不是 `openlineage-spark` 会产出的格式**；
   若要让 Doris 输出边呈现为 `doris://`，必须在 `CanonicalNameResolver` 里加一条基于
   host:port 白名单（或其他侧信道）的改写规则，不能指望上游自己标注。

#### 3.5.3 现有 `CanonicalNameResolver.Default` 的实测结论：**100% 拒绝真实 JDBC 事件**

`CanonicalNameResolver.Default`（`datasophon-api/.../lineage/CanonicalNameResolver.java`）
假设 `namespace` 形如 `connector://catalog/database`（`://` 后按 `/` 切分需恰好 2 段），
`name` 是表名：

```java
String[] path = namespace.substring(schemeSeparator + 3).split("/", -1);
if (path.length != 2 || ...) { return Optional.empty(); }
```

代入实测值 `namespace="mysql://192.168.10.131:3306"`：`://` 后是
`"192.168.10.131:3306"`，按 `/` 切分只有 **1 段**，`path.length != 2` 恒真 →
**每一条真实 Spark JDBC 血缘事件都会被判定为 `UNRESOLVED_DATASET`，写入 `parse_log` 后整条
丢弃**。这不是边界情况，是 JDBC 这一整个 provider 类型的必现路径。

> **这是一个已被真实数据证实的实现缺陷，不是推测。** 是否现在修、怎么修（JDBC 专属解析分支？
> 还是重新设计 `namespace`/`name` 的通用切分规则？）留给实现决策——采样只负责把事实钉死，
> 不擅自改代码。

#### 3.5.4 清理记录

采样后已删除：MySQL `datasophon.l0_probe_output` 表、Doris `l0_probe` 库、
ddh-02 `/tmp` 下的 Spark 解压目录/tarball/jar/日志（`/tmp` 用量已确认从 855M 降回 14M）。
未对沙箱做任何其他改动。

### 3.6 仍未采样的部分：Hive / Paimon / Iceberg

沙箱阶段 B（Hadoop 扩展）为 `BLOCKED`（`deployment-standalone-doris.md` Phase 12），
无 Hive metastore、无 HDFS、无 Paimon warehouse、Gravitino 的 `iceberg-rest-server`
未启用（`ss -lntp` 未见监听端口，`gravitino.conf` 无相关配置）。
`openlineage-spark` 对这三类 catalog 的 facet 构建逻辑与 JDBC 完全不同（走 Spark
`TableCatalog`/`CatalogPlugin` 反射而非 JDBC URL 解析），**JDBC 的采样结果不能外推到它们**。
需部署最小 Hive metastore 或 Paimon filesystem catalog 才能继续，这部分仍是
**待办**，不建议现在凭空实现。

---

## 4. L0 #9 —— Gravitino 不做去重（部分确认）

同一 `(producer, runId, eventType)` 连续投递两次 → `gravitino_lineage.log` **两条记录**，
两次均返回 `201`。

**结论**：投递幂等**只能**由接收端承担，`t_ddh_lineage_event` 的
`UNIQUE KEY (producer, run_id, event_type)` 是必需的，不是冗余设计。

> Gravitino **自身** 在 sink 失败时的重试行为（是否复用同一三元组）属于 L0 #4，未测。

---

## 5. 顺带确定的三件事

|             事项             |                                                                               内容                                                                                |                                           影响                                            |
|----------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| **`eventType` 被小写化**       | 发送 `"COMPLETE"`，日志中为 `"complete"`                                                                                                                               | 我方 `DecodedLineageEvent` 构造时已 `toUpperCase(Locale.ROOT)` 归一，幂等键与 `FAIL`/`ABORT` 判定均不受影响 |
| **`eventTime` 被规范化**       | 发送 `2026-07-30T02:30:00.000Z`，日志中为 `2026-07-30T02:30:00Z`                                                                                                       | `Instant.parse` 两种都能解析，watermark 提取不受影响                                                 |
| **http sink 支持 apiKey 认证** | `LineageHttpSink` 有 `authType` 配置，`AuthenticationFactory` 支持 `apiKey` / `none`；`ApiKeyAuthStrategy` 走 OpenLineage 官方 `TokenProvider`（即 `Authorization: Bearer`） | **L1 写路径里 `TODO(L2)` 的鉴权有了明确落点**：ingest 端点校验 Bearer token，sink 侧配 `authType=apiKey`     |

---

## 6. 下一步

| 优先级 |                            事项                             |
|-----|-----------------------------------------------------------|
| 高   | 回写架构文档 §2.1 / §3.3 的两处错误陈述，并重新评估 D1（是否仍让血缘链路依赖 GRAVITINO） |
| 高   | 部署 Spark（沙箱当前没有）以完成 #2 的实机采样 —— 它同时解锁 #6 / #7 / #8        |
| 中   | #4 HTTP sink 重试/超时；#9 的 Gravitino 侧重试三元组稳定性               |
| 低   | #5 DS API 拉 SQL 文本（L5 才需要）                                |

**在 #2 剩余部分出结论前，`CanonicalNameResolver.Default` 保持现状**（单一直白切分 +
`TODO L0-#2`），不要按本报告的推断去改实现 —— 推断可以缩小搜索范围，但不能代替采样。
