# 用 Spark 批实现《实时湖仓技术方案》§7.1，验证 DS 工作流 Tab 的批链路

> **2026-08-26 架构调整**：原计划用 Paimon + S3 复现"湖仓"形态，但 Phase 0 探针证实 openlineage-spark（含最新 1.52.0）从未支持 Paimon 的输出数据集识别，属上游结构性缺口（详见下方进度表 Phase 0-b 与偏差记录）。**用户决策：本轮 ODS/DWD/DWM/DWS 全部改用 Spark JDBC 直写 Doris**，不再经 Paimon/S3；Paimon 的 OpenLineage 支持另行 fork 实现，不在本方案范围。文档标题与 Context 保留原始立项背景，Phase 1 起的实施细节已按新架构改写。

## Context

**为什么做这件事。** DS 工作流可视化（`feat/ds-workflow-tab`）的批链路目前只被**一条单节点合成工作流**验证过——`codex_w2_batch_1787641233`，内容是 `SELECT id FROM range(0,700)`，DAG 里只有 1 个节点、0 条边。今天（2026-08-26）的实机走查确认了它能正确显示 700/234 行，但也暴露出**一整类判据从未被真实数据触及**：

- 多节点 DAG 的渲染与布局
- `preTaskCode == 0` 哑元边过滤（单节点无边可过滤）
- 扇入（多上游汇聚）与扇出（一个上游分叉）
- 每个节点独立绑定各自的血缘 run
- 节点输出超过 2 张表时的 `+N` 折叠

同时，`docs/ds-workflow-tab-执行任务清单-2026-08-25.md` 的 G3 门禁仍是 ⛔。

**这次要产出什么。** 把《实时湖仓技术方案》§7.1（电商流式湖仓：orders / orders_pay / product_catalog → ODS → DWD 宽表 → DWM → DWS）**改写为 Spark 批实现**，在实验环境的 DolphinScheduler 里配成一条 **7 节点 DAG**，用它替代玩具级合成工作流，端到端验证「DS 任务 → OpenLineage → Gravitino → 平台页面逐节点行数」。

**为什么这个载体合适。** §7.1 的样例数据是手算得出的小数据集（7 + 7 + 5 行），每一层的输出行数都能在跑之前精确推导，这正是「逐值一致」判据需要的。而它天然是一条分层 DAG，扇入扇出俱全。

> **红线**：本方案只用 §7.1（电商 demo，公开样例数据）。**绝不触碰 §7.2「协和场景」**——那是真实医疗场景，表名、库名、数据一律不得出现在任何提交物、截图或日志中。

---

## 进度跟踪表（**每完成一个 Phase 立刻更新对应行，不要攒**）

> 状态取值：`⬜ 未开始` / `🔵 进行中` / `✅ 完成` / `🟡 部分完成` / `⛔ 阻塞`
> 「证据」列必须写**可复核的具体值**（行数、md5、HTTP 状态、截图文件名），不接受「已验证」「OK」这类空话。

| Phase | 内容 | 状态 | 完成判据 | 证据 |
|---|---|---|---|---|
| **0-a** | Spark 侧 Paimon 1.2.0 → 1.4.1（三台） | ✅ | 三台 `jars/` 均为 `paimon-spark-3.5_2.12-1.4.1.jar`（md5 `71718a07a6ac9aa6d1bde6a3892d1f88`）+ `paimon-s3-1.4.1.jar`（md5 `25901c86b680a929e598084bdc3133c6`），旧 1.2.0 jar 已移至 `jars-bak-20260826/`；另补 `mysql-connector-j-8.2.0.jar`（md5 `a331817ab5c572777e25539a70b51bb6`），三台一致 | |
| **0-b** | 写入 rowCount 探针 | ✅ | Paimon 路径不通（原因见下方偏差记录）。**改用 Spark JDBC 直写 Doris**：`ds-1-probe-doris0826b` 查回 `outputs=[{namespace: mysql://192.168.10.131:9030, name: ds71.ds71.probe_100, rowCount: 100, size: 0, jobName: ...execute_save_into_data_source_command.ds71_probe_100}]`，rowCount 精确等于写入的 100 行，Spark→Doris(JDBC)→OpenLineage→Gravitino 全链路打通 | |
| **1-a** | mysql 驱动三台核对一致 + Doris 建 `ds71` 库/表/`ds71_batch` 账号 | ✅ | 三台 `mysql-connector-j` md5 一致（`a331817ab5c572777e25539a70b51bb6`）；`ds71_batch`@'%' 已建并授权；探针账号 `ds71_probe` 已删除；7 张 Doris 表已建 | |
| **1-b** | ddh-01 MySQL 建 `order_dw` + 3 表 + 灌数（样例数据为本方案反推构造，见 Context 顶部架构调整说明） | ✅ | `SELECT COUNT(*)` 三表分别为 **7 / 7 / 5**（实测输出） | |
| **2** | 7 段 Spark SQL 写完并单条手工跑通 | ✅ | 7 段全部跑通，行数**逐值匹配真值表**：ods_orders=7/ods_orders_pay=7/ods_product_catalog=5/dwd_orders=7/dwm_users_shops=6/dws_users=3/dws_shops=4；交叉校验 shop 12347 → `uv=2,pv=3,payed_buy_fee_sum=7000.00` 精确匹配 | |
| **3** | DS 建 `wf_ds71_batch_spark` 并上线 | ✅ | 工作流 code=182561385251872，含 7 个 task + 9 条 relation（6 条真实边 + 3 条 preTaskCode=0 起点标记，见偏差记录"7 条边"笔误订正）；上线状态「已上线」；手工触发实例 id=12，`state=SUCCESS` | |
| **4-①** | 逐节点行数 vs 真值 | ✅ | 工作流真实跑完后复核 Doris 7 张表：**7/7/5/7/6/3/4，与真值表逐值一致**；`dws_shops` 里 shop `12347` 实测 `uv=2,pv=3,payed_buy_fee_sum=7000.00`，与真值精确匹配 | |
| **4-②** | 平台页面走查（ego-browser） | ✅ | DAG 渲染 **7 节点、6 条边**（3→1 扇入 + 1→2 扇出，文档原写"7 条边"是笔误，见偏差记录）；**无幽灵起点节点**；每个节点卡片直接显示行数，与真值逐一核对无误；工作流列表 → 展开定义 → 点击实例 → Drawer 弹出 DAG 的交互路径正常。截图：`.scratch/ds-workflow-tab/shots/ds71-7node-dag-rowcounts-2026-08-26.png`、`ds71-workflow-list-2026-08-26.png` | |
| **4-③** | Doris 原生表三类查询（原计划的 Paimon catalog 联邦查询已作废，见架构调整说明） | ✅ | 排名查询（top3 商户）：12347(7000)/12348(2100)/12345(1500)；明细查询（user_001 支付宝订单）：命中 O001（1500）；报表查询（按品类聚合）：服装 2 单 5000 / 电子产品 4 单 4400 / 家居 1 单 2000，三类结果均与手算数据交叉核对一致 | |
| **4-④** | 插入新数据后重跑，历史隔离 | ✅ | 追加 3 笔订单+支付后（orders 7→10）重跑：新实例（13）显示 **10/10/5/10/9/3/4**（新真值，dwm 因新增 3 个未出现过的 (user,shop) 组合从 6→9，dws_users/dws_shops 因未引入新用户/商户而维持 3/4，均与手算预期一致）；**旧实例（12）截图复核仍精确显示 7/7/5/7/6/3/4**，未被 Doris 底层表覆盖影响——历史隔离确认成立。截图：`ds71-instance13-rerun-newvalues-2026-08-26.png`、`ds71-instance12-frozen-oldvalues-2026-08-26.png` | |
| **5** | 回写文档 + 沙箱改动清单 | ✅ | `docs/ds-workflow-tab-执行任务清单-2026-08-25.md` W3-2 行已补充真实多节点证据 + §6 新增 DS 时区缺陷偏差记录；新建 `docs/ds71-spark-batch-湖仓批链路验证-2026-08-26.md` 记录 SQL 全文/真值表/语义替换对照；本文件进度表已填满，改动清单已标注完成状态 | |

### 中断恢复

从进度表第一行非 `✅` 的 Phase 继续。**不要重跑已完成的 Phase**——
Phase 0-a 重跑会重复搬 jar，Phase 1-b 重跑会重复灌数（`orders` 会变 14 行，把真值表全部打乱）。
若不确定某行是否真做完，用「证据」列里的判据去**实测复核**，不要凭状态列的标记推断。

### 偏差记录（**发现即追加，不要攒**）

> 格式：`日期 · Phase · 现象 · 影响 · 处置`

| 日期 | Phase | 现象 | 影响 | 处置 |
|---|---|---|---|---|
| 2026-08-26 | 4-④ | DS 工作流实例列表显示的"开始时间"比实测的真实当前时间超前约 8 小时（沙箱节点系统时钟本身经核实是准的：UTC 03:11 = 节点本地 CST 11:10，与真实时间吻合）。根因定位：`ps aux` 确认 DS master/api 进程启动参数为 `-Duser.timezone=${SPRING_JACKSON_TIME_ZONE}`，但该环境变量在整个 DS 安装目录（含 `bin/env/`）里从未被赋值，JVM 拿到的是未展开的字面量占位符，时区解析失败退回 UTC，叠加 MySQL 会话时区（CST）读写时间戳时的换算，导致显示值多偏移了一个时区量级 | **不影响本次 rowCount/DAG 判据**（Phase 4①②③④ 的行数与隔离性验证均以数据内容为准，不依赖显示的时间戳），但是一个真实的平台/部署缺陷 | 按方案约定"验证暴露出平台缺陷，另开条目，不夹带进本次"处理——只记录根因线索（`SPRING_JACKSON_TIME_ZONE` 未赋值），不在本方案内修复 |
| 2026-08-26 | 0-b | §1 给出的 catalog 配置片段缺 `spark.sql.extensions=org.apache.paimon.spark.extensions.PaimonSparkSessionExtensions`，不加此项连 `CREATE DATABASE` 都在分析阶段直接报错 | 阻断，已现场补齐 | 探针配置文件已加此行；Phase 1 正式写入 `spark-defaults.conf` 时须一并写入这一行 |
| 2026-08-26 | 0-b | `spark-submit`/`spark-sql` 的 `--properties-file` 是**替换**默认 `spark-defaults.conf`，不是叠加；首次探针用只含 Paimon catalog 的 properties 文件，把 `spark.extraListeners`（OpenLineage 监听器注册）整个顶掉，导致监听器从未加载，探针查回 404 | 已发现并绕过（探针文件改为"现有 spark-defaults.conf 内容 + 追加 Paimon 配置"），不影响 Phase 1（直接改 spark-defaults.conf 本身不存在这个问题） | 记录此坑供 Phase 1 执行者参考，不要用 `--properties-file` 单独传 Paimon 配置去验证任何依赖 spark-defaults.conf 现有配置的功能 |
| 2026-08-26 | 0-b | 最初怀疑根因是 Paimon 1.4.1 隔离类加载器 `ComponentClassLoader` 与 openlineage-spark 反射加载 `OpenLineageExtensionProvider` 冲突（ERROR 日志）。**已用两路证据推翻**：①读 openlineage-spark 1.29.0 源码（`SparkOpenLineageExtensionVisitorWrapper.init()`/`loadProviderToAvailableClassloaders()`）确认该异常逐个 classloader 捕获、不外抛，且注释明确"只要 JVM 里有多个 classloader 就必现此日志，与具体 connector 是否需要该扩展点无关"——纯噪音；②把 `spark.openlineage.transport.type` 临时切 `console` 并把 `io.openlineage.client.transports.ConsoleTransport` 提到 INFO，直接抓到 Spark 侧发出的原始事件 JSON：`COMPLETE ds71_probe_console2.overwrite_by_expression_exec_v1.ds71_probe_100` 事件本身 `outputs=[]`——**问题出在 Spark 发送端，事件从源头就没带输出数据集，不是 Gravitino 摄取丢的**。GitHub 代码搜索 `paimon repo:OpenLineage/OpenLineage` 命中 0 条，确认 openlineage-spark 1.29.0 没有任何 Paimon 专门支持代码 | **阻断 Phase 0 判据**，且是结构性缺口而非可调参数问题：openlineage-spark 的 `DataSourceV2RelationOutputDatasetBuilder`/write-command builder 对 Paimon 的 `SparkTable`/`SparkCatalog` 没有输出数据集识别逻辑，禁用 classloader 隔离等配置项**不可能修复**，因为根因不在那一层 | 进一步核实：openlineage-spark 最新正式版 1.52.0（2026-07-23）代码库搜索 `paimon` 仍 0 命中，官方 issue #3870「Support Paimon Catalog」至今 open 无人认领，PR #4430「Add Paimon dataset naming support」未合并——升级版本号救不了。**用户决策（2026-08-26）**：本轮暂不用 Paimon，ODS/DWD/DWM/DWS 全部改用 **Spark JDBC 直写 Doris**；Paimon 的 OpenLineage 支持另行 fork 实现，不在本方案范围 |
| 2026-08-26 | 2 | **关键坑（二）**：Spark 3.x 对 JDBC 读回的 VARCHAR 列按 ANSI `CHAR` 写入侧填充语义处理，`INSERT OVERWRITE` 写入 Doris 时把每个字符串值右侧补空格补到**声明宽度**（如 `VARCHAR(32)` 的 `order_id='O001'` 被写成 `'O001'+28个空格`），`LENGTH()` 验证发现实际存的是 32 字节而非 4 字节；这是静默数据错误，`SELECT COUNT(*)` 这类判据完全看不出来，但下游所有基于字符串等值的 JOIN/GROUP BY 都会受影响（padding 不一致会导致匹配失败或不同分组） | **阻断且高风险**（比 truncate 那个坑更隐蔽，是数据正确性问题不是报错） | 每个 Spark 任务的 `--conf` 必须加 `spark.sql.legacy.charVarcharAsString=true`；已验证加此参数后 `LENGTH(order_id)=4`，恢复正常。**Phase 2 每段 SQL 的 `others` 模板必须固定带这一条，不能只加在个别任务上** |
| 2026-08-26 | 2 | **关键坑**：Spark `INSERT OVERWRITE TABLE <jdbc注册表> SELECT ...` 默认对目标表做 **DROP+CREATE**（不是清空数据），用 Spark 自己推断的通用 JDBC 类型建表（`VARCHAR`→`LONGTEXT`），Doris SQL 解析器不认 `LONGTEXT` 直接报 `no viable alternative at input`；且失败后表处于已删未建的中间态，下次查询变成诡异的"表不存在"（一度误判为 Doris 元数据 flaky，实为这个根因的副作用） | 阻断，已定位并修复 | 目标表的 `CREATE TABLE ... USING jdbc OPTIONS(...)` **必须加 `truncate "true"`**，让 Spark 改用 `TRUNCATE TABLE` 而不是 DROP+CREATE，保留 Doris 侧手工定义的 DISTRIBUTED BY/类型；ods_orders 段加上后一次验证通过（COUNT=7） |
| 2026-08-26 | 0-b | Doris-JDBC 路线的两个实操坑：①Spark `CREATE TABLE ... USING jdbc` **不会**在远端建表（`createTableOptions` 只在 `DataFrameWriter.jdbc()` 建表场景生效，DDL 语法下 Spark 会立刻反查 schema，表不存在直接报错），必须先在 Doris 侧建好表再让 Spark `CREATE TABLE ... USING jdbc` 注册引用；②OpenLineage 捕获到的 `outputs[].name` 有库名重复（`ds71.ds71.probe_100`，因为 `dbtable` 选项已含库名前缀，JDBC url 的 `/ds71` 路径又被当作 namespace 的一部分拼了一次），`size` 恒为 0（JDBC sink 不产生字节数统计，只有 rowCount） | 不阻断：rowCount 判据已满足；`name` 重复前缀与 `size=0` 是 cosmetic，DsBatchMetricsProvider 目前只读 `rowCount`/`namespace`/`name`/`size`，多余前缀不影响页面已知逻辑，但记录以防后续按 `name` 做精确匹配时踩到 | Phase 2 正式实现的 7 段 SQL：①每段先用 Doris JDBC（或 mysql 客户端）建好目标表，Spark 侧只用 `CREATE TABLE ... USING jdbc` 注册已存在的表；②不依赖 `size` 字段做任何判断 |

---

## 已核实的环境事实（2026-08-26 实测）

| 项 | 实际值 | 与既有笔记的出入 |
|---|---|---|
| Spark | 3.5.8，装在 ddh-03/04/05，`local` 模式 | — |
| Paimon（Spark 侧） | 现为 `paimon-spark-3.5-1.2.0.jar`，**本方案升到 1.4.1** | 票 13 D9 记的就是 1.4.1，实机装成了 1.2.0 |
| Paimon（Flink 侧） | `paimon-flink-2.2-1.4.1.jar`（ddh-02） | 升级后两侧对齐 |
| OpenLineage | `openlineage-spark_2.12-1.29.0.jar`，三台 `spark-defaults.conf` md5 一致 `839884ee6786…` | — |
| DS worker 分组 | `default` = ddh-04 + ddh-05；`flink` = ddh-03（单台） | — |
| MySQL | 仅 ddh-01:3306 | — |
| Doris | **4.1.3** 在跑（ddh-01:9030），FE lib 的 Paimon 读取端是 **`paimon-core-1.3.1`** | 文档写的是 2.1.7 |
| RustFS | ddh-01:9040，已有桶 `paimon-warehouse`、`lineage-paimon-warehouse` | — |
| Spark 缺的依赖 | **无 `paimon-s3`、无 mysql 驱动** | — |
| Paimon 1.4.x 制品改名 | Spark 制品从 `paimon-spark-3.5` 改为 **`paimon-spark-3.5_2.12`**（带 Scala 后缀）。旧名止于 1.3.2，新名起于 1.4.1 | 直接按旧名找 1.4.1 会拿到 **404** |
| 节点外网 | **不通**（Maven Central 只能本机下载后 scp） | — |
| 血缘 key | `ds-<clusterId>-<taskInstanceId>`，本集群 `clusterId=1` | `DsBatchMetricsProvider:74` |

**现成的可抄样板**：`codex_w2_batch_1787641233` 的 `taskParams`——`taskType=SPARK`、`programType=SQL`、`deployMode=local`、`sparkVersion=SPARK2`、`others` 里带 `--conf spark.datasophon.dsTaskInstanceId=ds-1-${system.task.instance.id}`。**`ds-1-` 前缀不能丢**，裸数字查不到。

---

## 最大风险：写入能否产出 rowCount（**已在 Phase 0 解决，此节保留为历史记录**）

已验证的批链路用的是 `CREATE TABLE … USING parquet AS SELECT`，走 Spark 的文件写入路径，OpenLineage 从中稳定拿到 `outputStatistics.rowCount`。**Paimon 走的是 DataSourceV2 写入路径，OpenLineage 1.29.0 能否同样产出 rowCount 没有任何现成证据**——票 12 只证明了「Spark 集成实现了这个 facet」，没证明「对 Paimon sink 也成立」。

而 rowCount 恰恰是本次验证的核心判据。所以**第 0 阶段必须先探针，探针不过就走回退路径**，不要把 7 个任务都建完才发现拿不到行数。

升到 Paimon 1.4.1 **不会降低这个风险**——OpenLineage 那侧仍是 1.29.0，而 Paimon 换了一个大版本，
写入路径的实现也可能变。所以探针必须跑在**升级之后**的环境上，不能拿 1.2.0 的探针结果外推。

**实测结论（2026-08-26）**：这个风险评估是对的——Paimon 探针确认 openlineage-spark 对 Paimon 的 DataSourceV2 写入路径完全没有输出数据集识别逻辑（不是缺 rowCount 一项，是整个 output dataset 都不产出），且升级到 openlineage-spark 最新 1.52.0 也无济于事（官方 Paimon 支持 issue #3870 至今未认领）。**已改用 Spark JDBC 直写 Doris 的路径完成探针，rowCount 精确采集通过**，详见进度表 Phase 0-b 与偏差记录。

---

## 实施阶段

### Phase 0 — Paimon 升级到 1.4.1 + 行数探针（**阻断性，先做**）

**先把 Spark 侧 Paimon 从 1.2.0 升到 1.4.1，与 Flink 侧对齐**，再在升级后的环境上探针——
这样探针验证的就是最终形态，不会出现「探针用 1.2.0 过了、换 1.4.1 又不过」。

制品（均已确认可从 Maven Central 下载，节点无外网，本机下载后 scp）：

| jar | 大小 | 说明 |
|---|---|---|
| `paimon-spark-3.5_2.12-1.4.1.jar` | 42.7 MB | **注意新命名带 `_2.12`**；Scala 2.12 与 Spark 3.5.8 匹配 |
| `paimon-s3-1.4.1.jar` | 91.9 MB | Paimon 自带的 shaded S3 支持，不需要 hadoop-aws |

步骤：

1. 三台 ddh-03/04/05：把现有 `paimon-spark-3.5-1.2.0.jar` **移出** `$SPARK_HOME/jars/`
   到备份目录（**必须移走，不能与 1.4.1 并存**，同包名两份会导致类加载不确定）。
2. 放入上述两个 1.4.1 jar，三台 `md5sum` 核对一致。
3. 用 `--conf` 全参数手传（**此时先不改 `spark-defaults.conf`**，探针阶段不留痕）跑一条：
   建 Paimon catalog 指向 `s3://paimon-warehouse/ds71/`，`INSERT INTO` 一张 100 行的表，
   带 `--conf spark.datasophon.dsTaskInstanceId=ds-1-probe0826`。
4. 查 Gravitino：`GET /lineage/run/by-external-key/ds-1-probe0826`。

**判据**：返回的 `outputs[]` 里该表的 `rowCount` = 100 且 `size` 非空。

- ✅ 通过 → 进 Phase 1。
- ❌ 不通过 → **回退**：ODS/DWD/DWS 全部改用 `USING parquet` + 本机 `file://` warehouse，
  7 个任务全部指定 `workerGroup=flink`（ddh-03 单台，保证同一块盘）。
  代价：不是湖仓形态，Phase 4 的 Doris 查询做不了；收益：行数采集已实测精确，主目标不受影响。
  **这个取舍要当场告知，不要自行降级后继续。**

> 探针用完即清：删掉探针表与 Gravitino 里那条 run 不必强求，但要在文档里记明 `ds-1-probe0826` 是探针产物。

> **实际结果（2026-08-26）**：❌ 不通过，且用户否决了原计划的 parquet+file:// 回退路径（会丢失"落到真实存储引擎"的验证价值），改为**第三条路径**——Spark JDBC 直写 Doris。已用同样的 `ds-1-probe-doris0826b` 探针流程验证通过（rowCount=100 精确匹配），详见进度表 Phase 0-b 与偏差记录。Paimon 1.4.1 的升级成果（jar、md5）继续保留在三台节点上，但**本方案后续 Phase 不再使用**，纯粹是这次升级操作留下的既成事实，不需要回滚。Phase 1 起的步骤已按 Doris 路径重写。

### Phase 1 — 环境准备

**依赖 jar**（三台 ddh-03/04/05 完全一致，`md5sum` 核对后再往下走）：
- `mysql-connector-j-8.2.0.jar` → `$SPARK_HOME/jars/`，
  **节点本地就有，直接 `cp`，不用下载**：`/data/install_datasophon/datasophon-worker/lib/mysql-connector-j-8.2.0.jar`（Phase 0 探针时已放好，三台需核对一致）
- Paimon 1.4.1 两个 jar 留在原地即可（Phase 0 已升级），本方案不再使用，不需要额外处理

**`spark-defaults.conf` 不需要新增任何 catalog 配置**——Doris 走标准 JDBC（`jdbc:mysql://...:9030`），Spark 内置 JDBC 数据源不需要 catalog 注册，只需 mysql 驱动 jar 在 classpath 上（上一步已放）。三台 `spark-defaults.conf` 应保持原样，只需再次核对三台 md5 一致（防止 Phase 0 探针时手误改动）。

**Doris 目标库与写入账号**（ddh-01:9030，参照 Phase 0-b 探针里已验证可行的模式）：
```sql
CREATE DATABASE IF NOT EXISTS ds71;
CREATE USER IF NOT EXISTS 'ds71_batch'@'%' IDENTIFIED BY '<新密码，不进本地命令行文本>';
GRANT SELECT_PRIV,LOAD_PRIV,CREATE_PRIV,DROP_PRIV,ALTER_PRIV ON internal.ds71.* TO 'ds71_batch'@'%';
```
> 复用 Phase 0-b 探针里 `ds71_probe` 账号的权限收窄思路，但新建一个专用账号（`ds71_batch`），探针账号用完可删：
> `DROP USER 'ds71_probe'@'%';`
> **踩坑提醒**：Doris 4.1.3 没有 `INSERT_PRIV` 这个权限类型（探针阶段已验证），`LOAD_PRIV` 已覆盖写入需要；`root`
> 通过局域网 IP 连接会被拒（只有 `127.0.0.1`/`localhost` 特例放行），新建的 `ds71_batch`/`'%'` 账号不受此限制，
> Spark 从 ddh-03/04/05 直接用它连 `192.168.10.131:9030` 即可。
> **7 张目标表要在 Doris 里提前建好**（`ods_orders`/`ods_orders_pay`/`ods_product_catalog`/`dwd_orders`/
> `dwm_users_shops`/`dws_users`/`dws_shops`），DDL 随 Phase 2 每段 SQL 一起给出——
> Spark 的 `CREATE TABLE ... USING jdbc` 只能**注册**已存在的远端表，不能建表（Phase 0-b 偏差记录已验证）。

**MySQL 源库**（ddh-01:3306，新建 `order_dw` 库，纯追加，不碰 DS 自己的元数据库）：
照 §7.1.1 原样建 `orders` / `orders_pay` / `product_catalog` 三张表并灌入样例数据（7 / 7 / 5 行）。

### Phase 2 — 7 段 Spark SQL

每段是一个独立的 DS SPARK 任务，`programType=SQL`、`deployMode=local`。
批实现对原方案的**语义替换**（要在文档里交代清楚，不能悄悄改）：

| 原方案（Flink 流） | 批实现 | 理由 |
|---|---|---|
| `mysql-cdc` connector | Spark JDBC 批读 | 批语义下没有 CDC，一次性快照即 ODS |
| `merge-engine=partial-update` + `UNION ALL` | 直接 `LEFT JOIN` orders ⋈ product_catalog ⋈ orders_pay | partial-update 是为流式乱序到达设计的；批场景一次性就能 join 齐 |
| `merge-engine=aggregation` | `GROUP BY` + `SUM` / `COUNT` | 同上，预聚合合并机制在批下退化为普通聚合 |
| `changelog-producer=lookup` | 不需要 | 无下游流式消费 |

节点与依赖：

```
ods_orders ─┐
ods_orders_pay ─┼─→ dwd_orders ─→ dwm_users_shops ─┬─→ dws_users
ods_product_catalog ─┘                              └─→ dws_shops
```

7 个节点、6 条边（3→1 扇入 + 1→2 扇出，逐条数：3+1+2=6）。

每个任务的 `others` 统一形如（**逐字段照抄现成样板，只改表名与作业名**）：
```
--conf spark.datasophon.dsTaskInstanceId=ds-1-${system.task.instance.id}
--conf spark.sql.legacy.charVarcharAsString=true
--name ds71-<层名>-${system.task.instance.id}
```
> `spark.sql.legacy.charVarcharAsString=true` **必须每段都加**——不加会导致所有 VARCHAR 列被右侧空格填充到声明宽度，
> `SELECT COUNT(*)` 看不出问题，但下游 JOIN/GROUP BY 会因填充不一致而静默出错（详见偏差记录"关键坑（二）"）。
> Doris 是共享网络服务，**不存在"哪个 Worker 节点能读到上游输出"的问题**——
> 上游写完 Doris 表，下游任务从任意节点用 JDBC 都能读到，因此 `workerGroup` 用默认的 `default` 即可，不必钉单节点
> （这一点跟原计划的 S3 warehouse 效果一致，Doris 甚至更简单，不需要额外配置 catalog）。

**每段 SQL 的固定套路**（对照 Phase 0-b 探针已验证的模式）：
1. 若目标表在 Doris 里还不存在，先用 Doris JDBC/mysql 客户端建表（`CREATE TABLE ... DISTRIBUTED BY HASH(...) BUCKETS 1 PROPERTIES('replication_num'='1')`，沙箱单副本，`BUCKETS` 数按表基数给 1 即可，数据量太小不需要分桶）；
2. Spark 侧 `CREATE TABLE <本地别名> USING jdbc OPTIONS(url="jdbc:mysql://192.168.10.131:9030/ds71", dbtable="ds71.<目标表>", user="ds71_batch", password="...", driver="com.mysql.cj.jdbc.Driver", truncate="true")` 注册已存在的表——**`truncate="true"` 必须加**，否则 `INSERT OVERWRITE` 会 DROP+CREATE 目标表并用 Spark 通用类型（`LONGTEXT`）建表，Doris 解析不了直接报错（详见偏差记录）；
3. 源表（MySQL `order_dw` 或上一层 Doris 表）同样以 `USING jdbc` 注册后 `SELECT`/`JOIN`；
4. `INSERT OVERWRITE TABLE <本地别名> SELECT ...`。

> `dbtable` 只写 `<db>.<table>`（不要再拼 catalog 前缀），JDBC url 的 `/ds71` 路径本身已经是目标库；
> Phase 0-b 已确认 OpenLineage 采集到的 `outputs[].name` 会出现 `ds71.ds71.xxx` 这种库名重复（cosmetic，不影响
> rowCount 判据），保留现状即可，不需要额外处理。

### Phase 3 — 建 DS 工作流

走 DS OpenAPI（票 06 已验证参数名，token 在 `ddh-02:/root/.ds-api.token`）：
`POST /projects/182468234670784/workflow-definition`，参数为
`name / description / globalParams / locations / timeout / taskRelationJson / taskDefinitionJson / otherParamsJson / executionType`（**没有 `tenantCode`**）。

工作流名 `wf_ds71_batch_spark`，上线后**手工触发一次**。

### Phase 4 — 验证

**① 逐节点行数（主判据）**。真值在跑之前就能手算出来：

| 节点 | 输出表 | 期望行数 | 推导 |
|---|---|---|---|
| ods_orders | `ods_orders` | **7** | 源表 7 行 |
| ods_orders_pay | `ods_orders_pay` | **7** | 源表 7 行 |
| ods_product_catalog | `ods_product_catalog` | **5** | 源表 5 行 |
| dwd_orders | `dwd_orders` | **7** | 每笔订单恰有 1 笔支付，join 后仍 7 行 |
| dwm_users_shops | `dwm_users_shops` | **6** | (user,shop) 去重：(001,12345)(002,12346)(003,12347)(001,12347)(002,12348)(001,12348) |
| dws_users | `dws_users` | **3** | user_001 / 002 / 003 |
| dws_shops | `dws_shops` | **4** | 12345 / 12346 / 12347 / 12348 |

抽查一个交叉口径：shop `12347` 应为 `uv=2, pv=3, payed_buy_fee_sum=7000`
（user_003 两单 3000+2000，user_001 一单 2000）。

核对方式两路都要走，**不能只看页面**：
- 后端：`GET /v2/ds/instances/{id}/dag`，逐节点读 `metrics.outputs[].rowCount`
- 真值：Spark SQL 直接 `SELECT COUNT(*)` 各表

**② 平台页面走查**（`ego-browser`，截图存 `.scratch/ds-workflow-tab/shots/`）——
这是本次相对既有验证真正**新增覆盖**的部分：
- DAG 渲染出 **7 个节点、6 条边**（原文写 7 条边是笔误，实际拓扑 3→1 扇入 + 1→2 扇出 = 6 条），扇入扇出形状正确
- **无幽灵起点节点**（`preTaskCode == 0` 过滤真正被触发，单节点工作流验不到这条）
- 每个节点显示各自的行数，互不串台
- 树表展开该定义 → 实例行 → Drawer，15 秒轮询启停仍正常

**③ §7.1.7 的三类查询**（改用 Doris 原生表，不再涉及 Paimon catalog，无跨引擎版本兼容问题）：
排名查询（交易额 top3 商户）、明细查询（user_001 特定支付平台订单）、报表查询（按品类聚合）。
直接对 `dws_shops`/`dwd_orders` 等 Phase 2 写入的 Doris 表跑 SQL 即可，`ddh-01:9030` 用 `ds71_batch`
或只读账号连接均可。

> 原计划这里是"Doris 建 Paimon catalog 联邦查询 S3 上的湖仓表"，需要处理 Spark(1.4.1)/Doris(1.3.1) 的
> Paimon 版本兼容风险。**已随 Phase 0 的架构调整整体作废**——数据现在原生落在 Doris 里，本项退化为
> 普通的业务 SQL 验证，不再是一个需要"记录为已知限制"的高风险项。

**④ §7.1.6「捕捉业务数据库的变化」的批等价**：
往 MySQL 插入 3 笔新订单 + 3 笔支付后**重跑一次**工作流，验证
（a）新实例的行数按新真值更新（orders 10 → dwd 10 → dws_users 3 / dws_shops 4）；
（b）**旧实例仍显示它自己那一次的量**——这是 W3-2 已验证过的历史隔离，用真实多节点场景再确认一次。

### Phase 5 — 回写

- `docs/ds-workflow-tab-执行任务清单-2026-08-25.md`：门禁记录表加一行；
  偏差表记录 Paimon 版本与笔记不符、Paimon rowCount 探针结论等本轮新事实
- 新建 `docs/ds71-spark-batch-湖仓批链路验证-2026-08-26.md`：记录 SQL 全文、真值表、语义替换对照
- 沙箱改动清单（jar、conf、MySQL 新库、DS 新工作流）连同备份路径一并记录

---

## 需要改动的位置

**仓库内**：只加文档，**不改任何代码**。本次是验证，不是开发。
（若验证暴露出平台缺陷，另开条目，不夹带进本次。）

**沙箱环境**（全部可回滚，改前留 `.bak-<日期>`）：

| 位置 | 改动 | 状态 |
|---|---|---|
| `ddh-03/04/05: $SPARK_HOME/jars/` | **Paimon 1.2.0 → 1.4.1**（移出 `paimon-spark-3.5-1.2.0.jar`，放入 `paimon-spark-3.5_2.12-1.4.1.jar` + `paimon-s3-1.4.1.jar`，旧 jar 备份于 `jars-bak-20260826/`）——**本方案后续不再使用，属既成事实，不回滚**；+`mysql-connector-j-8.2.0.jar`（**本方案实际依赖**，三台 md5 `a331817ab5c572777e25539a70b51bb6` 一致） | ✅ 已完成 |
| `ddh-03/04/05: $SPARK_HOME/conf/spark-defaults.conf` | **未改动**——架构调整后不再需要 Paimon catalog 配置，标准 JDBC 不需要 catalog | ✅ 确认无需改动 |
| `ddh-01: MySQL` | 新建 `order_dw` 库 + 3 表（orders/orders_pay/product_catalog）+ 样例数据（7/7/5 行，Phase 4④ 追加 3 行至 10 行） | ✅ 已完成 |
| `ddh-01: Doris` | 新建 `ds71` 库 + 7 张目标表 + 专用写入账号 `ds71_batch`（`GRANT SELECT_PRIV,LOAD_PRIV,CREATE_PRIV,DROP_PRIV,ALTER_PRIV ON internal.ds71.*`）；探针账号 `ds71_probe` 已删除 | ✅ 已完成 |
| DS 项目 `ds_lineage_verify` | 新增工作流 `wf_ds71_batch_spark`（code=182561385251872，已上线，累计运行 2 次，实例 12/13 均 SUCCESS） | ✅ 已完成 |

> ~~`ddh-01: RustFS` 新增 `ds71/` 前缀~~、~~`ddh-01: Doris` 新建 Paimon catalog~~：随架构调整作废，不再需要，未执行。

---

## 已知会踩的坑（动手前扫一眼）

| 会踩在哪 | 坑 |
|---|---|
| Phase 0 | Paimon 写入的 rowCount 无先例，**必须先探针**，别建完 7 个任务才发现拿不到 |
| Phase 0 | Paimon 1.4.x 的 Spark 制品**改名带了 `_2.12`**，按旧名 `paimon-spark-3.5` 找 1.4.1 会 404 |
| Phase 0 | 升级时旧的 `paimon-spark-3.5-1.2.0.jar` **必须移出 `jars/`**，两份同包名并存会导致类加载不确定 |
| Phase 1 | `spark-defaults.conf` 三台不一致 → 任务落到不同节点会出现「有时有行数、有时没有」的诡异现象（架构调整后本方案不再改这个文件，此坑仅供参考） |
| Phase 1 | ~~跨机传密钥用 `ssh A 'cat 密钥' \| ssh B 'bash -s' <<'EOF'` 会被 heredoc 抢走 stdin~~：架构调整后不再需要传 RustFS S3 密钥，此坑随之作废；Doris 账号密码同样不要走这种双跳 heredoc |
| Phase 1 | Doris 4.1.3 没有 `INSERT_PRIV` 权限类型，`GRANT` 语句里写了会直接报 `Unknown privilege type`；`LOAD_PRIV` 已覆盖写入需求 |
| Phase 1 | `root` 用户通过局域网 IP（如 `192.168.10.131`）连 Doris 会被拒（`Access denied ... using password: NO`），只有 `127.0.0.1`/`localhost` 特例放行；Spark 从其他节点写入必须用专门建的 `xxx@'%'` 账号，不要指望用 root |
| Phase 2 | Spark `CREATE TABLE ... USING jdbc` **不会**在 Doris 侧建表（`IF NOT EXISTS` 也一样会因为反查 schema 而报"表不存在"），必须先用 Doris 自己的 DDL 建好表 |
| Phase 2 | `dsTaskInstanceId` 必须带 `ds-1-` 前缀，裸数字后端查不到 |
| Phase 2 | Spark 3.5.x 不支持 JDK 21，三台默认 JDK 21，靠 `spark-env.sh` 里的 `JAVA_HOME=jdk-17` 兜底——别覆盖掉它 |
| Phase 3 | DS 老路径 `/process-definition` 返回 **200 + HTML** 而非 404，按状态码判活会全绿逃逸，必须校验 Content-Type |
| Phase 3 | `dolphinscheduler-daemon.sh start` 会用 `bin/env/dolphinscheduler_env.sh` 覆盖各服务 `conf/` 下的同名文件 |
| Phase 3 | DS API 正确路径是 `http://<ddh-02>:12345/dolphinscheduler/projects/<projectCode>/workflow-definition`（**必须带 `/dolphinscheduler` 前缀**，`/projects/...` 裸路径 404，`/dolphinscheduler/api/...`/`/dolphinscheduler/v2/...` 这类猜测前缀返回 200+HTML 静默走到 SPA fallback，只有 Content-Type 能分辨） |
| Phase 3 | 创建任务节点前必须先调 `GET .../task-definition/gen-task-codes?genNum=N` **预生成 code**，不能自己瞎编数字当 code |
| Phase 3 | 触发执行的正确端点是 `/executors/start-workflow-instance`，**不是**（本版本已废弃的）`/executors/start-process-instance`（后者直接 405，其余参数名如 `workflowDefinitionCode`/`workflowInstancePriority` 也已从 `processDefinitionCode`/`processInstancePriority` 更名，通过 `/dolphinscheduler/v3/api-docs` 现查最准） |
| Phase 3 | 平台前端登录接口 `POST /ddh/login`（含 `/ddh/login/account`）用 curl 直接调返回 `{"code":10000,"msg":"Request method 'POST' is not supported"}`，疑似经过 APISIX 网关的路由/方法限制，未深究；**验证前端行为一律走 `ego-browser` 走完整登录表单**，不要试图绕过网关直接打后端登录 API |
| Phase 4 | 部署/配置类验证不能只看入口返回码——本仓库多处失败伪装成 HTTP 200 |
| 全程 | 只用 §7.1 电商 demo 数据；§7.2 协和场景的任何内容不得出现 |

---

## 与当前分支状态的关系

`feat/ds-workflow-tab` 上有 10 个未推送提交，W1-B2 仍 ⛔（缺「DDL 变更如何进入元数据存储」的交付定义），
G3 门禁 ⛔。本方案**不修**这两条，只把 G3 的第二个缺口（缺真实多节点批链路证据）补上。
另外今天走查发现的「已结束流作业被误报为未按约定接入」属流侧，也不在本方案范围内。
