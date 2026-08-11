# Session 交接：数据血缘·任务级流速可视化（2026-08-04）

> 本文只记「现在到哪了」和「本次 session 独有、不在方案文档里的信息」。
> **不重复方案内容** —— 决策、证据、任务清单、验收标准全在权威文档里。

## 0. 三份文档的分工（先搞清楚该读哪份）

|                       文档                        |                          作用                           |    状态    |
|-------------------------------------------------|-------------------------------------------------------|----------|
| `docs/data-lineage-任务级流速可视化-实施方案-2026-08-04.md` | **权威**：E1-E15 证据、D1-D11 决策、§3 契约、9 个任务、§6 进度表、§8 验收清单 | 未 commit |
| `docs/data-lineage-流速采集调研-2026-08-04.md`        | 前一轮调研，**§2 结论部分已被实测推翻**（见本文 §2）                       | 未 commit |
| 本文                                              | 状态快照 + 操作性经验                                          | 未 commit |

**接手第一步**：读方案文档 §6 进度表确认状态未过期，然后读本文 §1。不要重新规划——决策已闭环。

---

## 1. 状态快照（截至 2026-08-04 11:20）

### 1.1 ⚠️ Codex 已认领但尚无代码产出

方案文档 §6 进度表里 **T1 / T3 已被标为 `IN PROGRESS`**，但交接时刻实测：

|        仓库        |           分支           |    HEAD     |                   工作区                    |
|------------------|------------------------|-------------|------------------------------------------|
| `datasophon`     | `feat/data-lineage-l1` | `c55e1159`  | 只有 `proxy.ts`（本机联调，与本任务无关）+ 3 个未跟踪的 docs |
| `gravitino` fork | `feat/lineage`         | `60b4e0a93` | 干净，只有未跟踪的 `.claude/hooks/`（与本任务无关）       |

前端 `lineageGraphData.ts` 最后修改停在 **8/1**，`service.ts` 停在 **8/3** —— **T3 一行代码都还没落盘**。

**给接手者**：`IN PROGRESS` 只代表 Codex 认领了任务，**不代表有可验收的产出**。核对真实进度请看 git，不要只看进度表。

### 1.2 已完成的部分

本次 session 的交付物是**方案本身**，不含任何功能代码：

- 一轮完整 grilling，12 个决策点全部闭环（方案 §2）
- 沙箱实机验证 15 条证据 E1-E15（方案 §1），其中多条推翻既有结论
- 方案文档定稿，9 个任务、并行分组、契约、验收清单齐备

---

## 2. 本次 session 最重要的一件事：一次被实验纠正的错误判断

**这件事必须交接，因为它决定了整个方案的走向，而且我中途判断错过一次。**

时间线：

1. 调研文档 §2 原结论：「`OpenLineageSparkListener` 没有 `RUNNING` eventType，长任务过程中完全不可见」→ 据此定下 D1「运行中走 OTel」
2. 我在真实事件日志里发现**确实存在大量 `running` 事件**，且 `outputStatistics` 恰恰只出现在 running 事件里（complete 反而没有）→ **我据此判断「调研文档核心结论错了，OTel 必要性下降」**
3. 用户选择「先补做长作业实验再定方案」，没有采信我的判断
4. 长作业实验（1.2 亿行、7.5 分钟）结果：**3 分 40 秒内 OpenLineage 零数据点，同期 Graphite 4726 行** → **我的判断是错的**

**根因**：第 2 步的样本是一个只有 2 行数据的作业。那种作业里 RUNNING 事件密集且带统计，看起来像"过程中持续上报"；真实长作业里 RUNNING 的触发点是 **Spark job 边界（`onJobEnd`）而非时间**，单个长写入 job 执行期间一个事件都没有。

**准确的结论**（已写进方案 E11/E12/E13）：
- 调研文档「没有 RUNNING eventType」这句**措辞错误**
- 但它「长时间任务过程中不可见」的**核心结论正确**
- 所以 **OTel 是必需的，不是可选项**，D1 成立

**可迁移的教训**：用小样本推翻一个既有结论时，先问"这个样本的规模能不能体现被推翻结论所描述的场景"。2 行数据的作业根本没有"长时间运行"这个属性，用它去证伪"长任务不可见"是无效的。用户坚持补实验是对的。

---

## 3. 沙箱操作经验（下次直接复用，别重新试错）

环境：ddh-02 = `192.168.10.132`，root **免密 SSH 可用**（从本机直连）。

### 3.1 Spark 相关

- **`/etc/profile.d/` 里有 `SPARK_HOME=/data/install_datasophon/spark3`，指向一个不存在的目录**（阶段 B 未装 SPARK3 的遗留）。直接跑 `spark-sql` 会报 `spark-submit: No such file or directory`。**脚本里必须显式 `export SPARK_HOME=/data/spark-sample/spark-3.5.8-bin-hadoop3` 并 `unset HADOOP_HOME HIVE_HOME`**。
- 真实可用的 Spark：`/data/spark-sample/spark-3.5.8-bin-hadoop3`（手工装的，非 Datasophon 托管）。
- **用 `spark.openlineage.transport.type=file` + `transport.location=<路径>` 做实验**，事件落本地文件，**不发往 Gravitino、不需要 token、不污染生产血缘库**。本次全部实验都是这么做的，Gravitino 血缘库零污染。
- 每次实验用独立的 `spark.sql.warehouse.dir` + `-Dderby.system.home=`，避免抢 `/data/spark-sample/metastore_db` 的 derby 锁。

### 3.2 隔离 collector 实例

生产 collector（PID 常驻）占 `4317/4318/8888`，配置在 `/data/install_datasophon/otelcol-contrib_0.156.0/config/otelcol.yaml`。
测试实例用**同一个二进制** + 独立配置 + 独立端口（本次用 carbon `2003` + telemetry `18889`），全程未影响生产。

**已实测通过的 carbonreceiver 配置留在 ddh-02 `/tmp/carbon-test.yaml`** —— T8 的直接蓝本，别重写。

### 3.3 SSH 执行的三个坑

1. **`ssh 'nohup cmd &'` 会挂住**（ssh 等着子进程的 stdout 关闭）。可靠做法：把启动逻辑写进 `.sh` 脚本 `scp` 过去，再 `ssh 'bash /tmp/x.sh'`——脚本内部的 `nohup ... &` 正常 detach。`setsid` 单独用不管用。
2. **`pgrep -f "xxx"` 会匹配到 ssh 命令行自身**（因为命令行里含该字符串），导致"已清理"误判为"仍有残留"。用 `ps -eo pid,cmd --no-headers | grep xxx | grep -v grep` 更准。
3. 远端 `sleep` 是可行的等待方式（本地 Bash 工具禁止前台 sleep），例如 `ssh 'sleep 90; <检查命令>'`。

### 3.4 诊断 collector 的正确姿势

debug exporter 走 **info** 级别输出。`service.telemetry.logs.level: warn` 会让它完全静默，看起来像"数据没进来"。
**比读日志更可靠的是 collector 自身指标**：`curl -s http://127.0.0.1:<telemetry端口>/metrics | grep -E "receiver_accepted|receiver_refused|exporter_sent"` —— 一眼看出数据是被拒了还是根本没到。

---

## 4. 沙箱残留资产

|                              路径（ddh-02）                              |                 内容                 |            处置            |
|----------------------------------------------------------------------|------------------------------------|--------------------------|
| `/tmp/carbon-test.yaml`                                              | **已实测通过的 carbonreceiver 配置**，T8 蓝本 | **保留**                   |
| `/data/spark-sample/rate-probe2/graphite-raw.txt`                    | 13622 行真实 Graphite 报文              | **保留**（regex 调试输入）       |
| `/data/spark-sample/rate-probe{,2,3}/` 的 parquet + warehouse + derby | 实验数据约 1GB+                         | **可删，但需用户确认**（已问过，用户未表态） |
| `/tmp/{probe,probe2,probe3,start-col,replay,an*}.{sh,py}`            | 实验脚本                               | 可删                       |

实验期间**未清理任何生产资产**，未改动全局环境变量，生产 collector 全程存活。

---

## 5. 下一个 session 该做什么

### 5.1 如果 Codex 已交付期一（T1-T4）

按方案 §8 的 V1-V6 验收。**其中三项不接受"测试全绿"作为证据，必须亲自动手**：

- **V1**：构造「1 个 job 写 3 张表」的数据，亲自核对只生成 **1 个**任务节点、3 条出边。这是三元图消除多输出歧义的支点，错了整个 D2/D3 决策就白做。
- **V2**：审查 `outputStatistics` 是否**按 dst dataset 匹配**，而不是图省事取 `outputs[0]`。
  ⚠️ **这是最容易被绿色测试掩盖的缺陷**：单输出作业下两种写法结果完全一样，测试用例若只用单输出数据就永远发现不了，直到生产上多输出作业把行数张冠李戴。这个 epic 已经踩过两次同款模式（`service.ts` 少解一层信封、`ReflectionTestUtils` 字段名漂移）——**错误假设和错误实现互相自洽，全绿但没测到关键契约**。
- **V6**：用 `ego-browser` 在沙箱实机打开 L3 血缘页，拿真实历史数据核对。沙箱集群 1（`test`）下有 2026-08-01 留的 4 节点测试链路（表名带 `lineage_demo` 前缀），可直接用。

另外必查 **V4**：`raw_event` 是 MEDIUMTEXT，确认没被拉进 `loadCurrentEdges` 的主 JOIN（方案 T2 已写明禁止，但这类性能要求最容易被忽略）。

### 5.2 如果 Codex 卡住

看进度表的 `BLOCKED` 行和证据列。方案 §7 规则 3 要求 Codex 卡住时**继续做其他无依赖任务**，所以卡一个不该停整条线。

### 5.3 期二开工前

期二的 T5-T9 靠 §3 契约解耦，**但建议等期一验收通过再开**——契约的真实检验在期一，契约若有问题，期一暴露的返工代价远小于两期一起返工。这是本次 session 给用户的建议，用户当时选的是「自己驱动 Codex」，实际派发节奏由用户掌握。

---

## 6. 未决事项

| # |                    事项                    |                                                说明                                                 |
|---|------------------------------------------|---------------------------------------------------------------------------------------------------|
| 1 | 三份 docs 均**未 commit**                    | 方案、调研、本文都在工作区。是否提交由用户定                                                                            |
| 2 | 沙箱 parquet 实验数据是否清理                      | 已问用户，未表态                                                                                          |
| 3 | **YARN 模式下的指标实例名格式未验证**                  | local 模式恒为 `driver`；YARN 下应为 executor 编号，影响 `key_instance` 取值范围但不影响规则结构。阶段 B 装 YARN 后需复验（方案 §1.1） |
| 4 | Doris 侧 rate builder 对 Spark 指标的适配未实跑    | 复用 JuiceFS 模式，T6 实现时才会真正碰到                                                                        |
| 5 | gravitino fork 的 `60b4e0a93` 仍**未 push** | 是上一轮 session 的遗留，与本任务无关但会一起被推                                                                     |
| 6 | `proxy.ts` 长期处于 modified 状态              | 本机联调专用，**任何提交都不得包含它**（方案 §7 规则 6）                                                                 |

---

## 7. 明确排除项（防止下个 session 范围蔓延）

方案 §8 末尾已列，此处重申三条最容易被"顺手做了"的：

- **task 总数分母**：D9 已决策不做。Spark 指标里没有（E14），要拿只能去打 Spark UI REST API，那会把"拉取式"的复杂度请回来一部分，需另行评估。
- **YARN 适配**：阶段 B 立项后另说。
- **Flink / DS 引擎的同类能力**：本方案只覆盖 Spark。
