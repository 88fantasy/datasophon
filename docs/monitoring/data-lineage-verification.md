# 数据血缘 L0 现场核查报告

> **对应** `docs/data-lineage-平台级血缘架构-2026-07-29.md` 的 **L0 — 现场核查 spike**（9 项）
> **环境**：五节点沙箱，Gravitino 1.3.0 运行于 ddh-02（192.168.10.132:8090），
> commit `40fdf6ab`，编译日期 2026-06-24
> **日期**：2026-07-30
> **状态**：**部分完成**。第 1 / 3 / 9(部分) 项已有结论；**第 2 项推翻了架构文档的一个核心前提**；
> 第 4 / 5 / 6 / 7 / 8 项未执行（依赖 Spark 作业与 ≥24h 采集周期）

---

## 0. 一句话结论

**Gravitino 不做 identifier 规范化** —— 服务端只有 `NoopProcessor`，Spark 插件与 OpenLineage
完全无关。架构文档 §2.1 与 §3.3 中「经 Gravitino / `GravitinoSparkPlugin` 转换成
`metalake.catalog.schema.table`」的陈述**不成立**，需要回写。

---

## 1. 核查结果总表

| # |                    待核实                    |    状态    |                                   结论                                   |
|---|-------------------------------------------|----------|------------------------------------------------------------------------|
| 1 | `/api/lineage` 在 1.3.0 部署上是否可用            | ✅ 已确认    | **可用**，返回 `201`，空响应体；事件落 `gravitino_lineage.log`                       |
| 2 | Gravitino 转换后 dataset 的确切拼写               | ⚠️ 前提被推翻 | **Gravitino 根本不转换**（见 §3）；真正的命名方由 `openlineage-spark` 决定，仍需 Spark 实机采样 |
| 3 | 沙箱是否有 Spark 作业可用于产出真实血缘                   | ✅ 已确认    | **没有**。ddh-02 上 `find /opt /data -iname '*spark*'` 零结果                 |
| 4 | HTTP sink 的重试/超时行为                        | ⬜ 未执行    | 已知 sink 支持 `authType` ∈ {`apiKey`, `none`}（见 §5）                       |
| 5 | DS 3.4.1 能否拉到 SQL 任务定义文本                  | ⬜ 未执行    | —                                                                      |
| 6 | 事件量与结构变化率实测（≥24h）                         | ⬜ 未执行    | 需要真实 Spark 作业持续运行                                                      |
| 7 | structural hash 的稳定性                      | ⬜ 未执行    | 需要同一作业的连续多次运行                                                          |
| 8 | 事件能否提供可靠的单调顺序                             | ⬜ 未执行    | 需要真实事件样本；当前实现已按「拿不到则降级 `received_at` 并写 `parse_log`」处理                 |
| 9 | 重复投递时 `(producer, runId, eventType)` 是否稳定 | 🟡 部分    | **Gravitino 不做任何去重**：同一事件两次投递 → 日志两条（见 §4）。Gravitino 自身重试行为未测          |

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
存在版本差异与回退分支）。前置条件：沙箱需要一个可运行的 Spark（L0 #3 结论为「没有」）。

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
