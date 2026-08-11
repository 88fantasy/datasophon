# Session Handoff — T16 Flink 流速可视化，端到端验证 + 根因修复（2026-08-07 续）

> 给下一个 Claude Code session 的交接文档。目标：不用重新读完整段对话就能接着干。
> 这份文档**完全取代**同名的上一版（那一版写于浏览器实机验证之前，本 session 已经把那份文档
> 列出的"下一步"全部做完，且中途发现推翻了几个假设）。

## 0. 结论先行

T16（Flink 前端流速可视化）**验收通过**：T6（`flink-cluster-cdc`，Prometheus scrape 路径）和 T9
（`flink-cluster-dwd`，原生 OTLP push 路径）在浏览器里都能看到"写入速率（近 1 小时）"图表的真实
非零数据。过程中发现并修复了 2 个真实 bug（§2），代码已提交（commit `72f0c564`，分支
`feat/flink-lineage`）。**还有 1 个已经在本地编译好、但还没推送到 ddh-01 的小修复**（tooltip
日期格式化，§5），是下一个 session 唯一还没收尾的动作项。

## 1. 这次 session 做了什么（时间顺序）

1. 用 ego-browser 打开 T9 job detail，确认写入速率图表已经有真实数据（上一版交接文档遗留的验收
   目标之一），发现 x 轴 tooltip 显示原始毫秒时间戳而非格式化时间——一个新 bug（后来在 §5 修复）。
2. 用户要求"模拟插入一些数据,让图表有数据"，用于验证 T6（当时仍是空图表）。没有直接怼生产
   Doris，而是走全真实链路：往 MySQL 源表 `lineage_flink_verify.pat_surgery` 插入 9 行测试数据
   （ID=`CDC_SIM_TEST_001~009`，字段内容复制自 `docs/lineage/data/05_raw_pat_surgery.sql` 里的
   真实样本行，只换 ID），让 Flink CDC 真实消费、Paimon 真实提交、OTel 真实采集。
3. 排查 T6 图表依然空的过程中，**逐层核实到 Doris**（用户提供了 Doris root 密码），发现真正根因：
   Flink 自带的 Prometheus Reporter 把所有 `Counter` 类型指标（含 `numRecordsOut`）在 `/metrics`
   暴露端点里都标成 `# TYPE ... gauge`（Flink 自身的已知限制，不是 OTel Collector 的 bug），
   Collector 据此把 Prometheus-scrape 路径（下划线命名，T6 用的那套）的指标存进了
   `otel_metrics_gauge` 表，而不是原生 OTLP push 路径（点号命名，T9 用的那套）所在的
   `otel_metrics_sum` 表。前后端代码此前对两种命名统一硬编码查 `sum` 表——这就是 T6"从未有数据"
   的真正原因，此前一直被误判为采集链路问题（详见 §2）。
4. 修复代码（后端 `LineageJobMetricsService.java` + 前端 `service.ts`），本地测试全绿，重新编译
   打包，推送到 ddh-01 替换实例。
5. **部署过程暴露了 3 个与本次代码改动无关、但阻塞服务启动的环境配置漂移**（§3）——全新解压
   部署包后，`conf/api.local.properties` 里好几个环境专属配置要么是过期值要么整行缺失，服务
   一度完全起不来（MySQL 1045 → Nexus 401 → Gravitino 503），逐层排查+用户提供密码逐一修复。
6. 服务恢复后浏览器确认 T6 图表有真实数据点。用户指出 tooltip 显示原始时间戳，修复
   `JobDetailDrawer.tsx`（§5），本地 `npm run dev` + 代理联调 ddh-01 后端验证通过。
7. 清理测试数据（`DELETE FROM pat_surgery WHERE ID LIKE 'CDC_SIM_TEST_%'`，已确认 0 行残留）。
8. 提交代码（commit `72f0c564`，见 §6），撰写本文档。

## 2. 真正的技术根因（下次直接复用，不要重新排查）

**同一个"记录数"指标概念，因为 Flink 版本/reporter 不同，不但指标名不同，连落在 Doris 哪张表
都不同：**

| 路径 | 集群 | 指标名（示例：numRecordsOut） | Doris 表 | 原因 |
|---|---|---|---|---|
| 原生 OTLP push（FLIP-385） | T9 `flink-cluster-dwd`，Flink 2.2.1 | `flink.taskmanager.job.task.operator.numRecordsOut`（点号） | `otel_metrics_sum` | OTel 原生导出器正确识别 Counter → Sum 语义 |
| Prometheus scrape 兜底 | T6 `flink-cluster-cdc`，Flink 1.20.4（无 FLIP-385 构建） | `flink_taskmanager_job_task_operator_numRecordsOut`（下划线） | `otel_metrics_gauge` | **Flink 自带 Prometheus Reporter 把所有 Counter 都标成 `# TYPE ... gauge`**，OTel Prometheus receiver 尊重这个错误标注 |

修复方式：`FLINK_RECORDS_OUT_METRICS`（前端 `service.ts`）和 `FLINK_RECORDS_OUT`/`FLINK_BYTES_OUT`
（后端 `LineageJobMetricsService.java`）从纯字符串数组改成 `{metric, table}` 配对数组，按命名法
分别查各自正确的表，查询结果依然按时间戳求和（一个 job_id 只会落在其中一套里，不会重复计数）。

**排查方法论（如果以后还有类似"图表空白但链路看起来都通"的问题，按这个顺序查，比瞎猜快）**：
1. 先查 Flink 自己的 REST API `/jobs/<id>`，看 `write-records` 有没有真实增长——证明 CDC/Flink
   处理层没问题。
2. 再直接 curl TaskManager 自己的 `/metrics`（Prometheus 文本端点，如 9251 端口），确认指标名和
   值都对——证明"该报的指标确实报出来了"。**顺便看一眼 `# TYPE` 那行**，这次就是这一步暴露的。
3. 最后才查 Doris 里到底落进了哪张表（`otel_metrics_gauge` vs `otel_metrics_sum`），不要预设
   查询代码用的表名就是数据实际落的表名。

## 3. 部署踩的坑：`conf/api.local.properties` 在全新解压后会丢配置

这是一个**系统性问题，值得后续专门花时间解决**，不只是这次运气不好：

`PropertyUtils`（`datasophon-common/.../utils/PropertyUtils.java`）加载顺序是
`conf/api.properties` → `conf/api.local.properties`（后者存在则覆盖前者同名 key）。
`api.local.properties` 是"开发者本地覆盖敏感配置、不进版本库"的设计，但在这个沙箱的实际运维
模式里，它被当成了"记录这台机器真实密码/token 的唯一位置"——而**每次全新 `tar xzf` 解压部署包
都会用打包产物里的模板覆盖掉它**，丢失掉之前手工patch 进去的真实值。

本次实测触发的 3 处（按暴露顺序）：
1. `mysql.password`——过期（值与当前 MySQL root 真实密码不一致，`errorCode 1045`）
2. `nexus.password`——过期（`LoadServiceMeta` 拉取 service DDL 时 401）
3. `datasophon.lineage.proxy.auth-token`——**整行缺失**（不是过期，是压根不存在），导致
   `GravitinoLineageClient` 抛 503 "auth-token is not configured"，血缘页面直接 503

修复方式：从重启前的最新一份 `.bak-t6-gauge-table-fix-20260807170745/conf/` 里把这 3 个 key
原样复制/更新到当前 `conf/api.properties`+`conf/api.local.properties`（`local.properties` 优先级
更高，两处密码都要改；token 只在 `local.properties` 出现过）。

**踩过的一个连带坑**：中途想"干脆把 backup 缺的 key 全量补齐"，用一个循环把 `mysql.ip` 也从
backup 复制过去了——backup 里 `mysql.ip=192.168.10.131`，把连接从能通的 `127.0.0.1`（socket/
loopback，MySQL 按来源 host 授权）换成了不通的外部 IP，导致又是一轮 1045。**教训：批量“补齐
缺失 key”前，先确认那个 key 是不是真的缺了会出问题，不要无脑对齐 backup**——`mysql.ip` 从
`api.properties` 自己的默认值 `127.0.0.1` 就是对的，根本不需要 `local.properties` 覆盖。

**建议下个 session（或找一个专门任务）做**：把这几个 key 固化进部署自动化（比如 CI/CD 脚本里
"解压后自动 patch 已知环境专属 key"的步骤，或者干脆把 `api.local.properties` 挪到部署目录外、
`tar xzf` 覆盖不到的路径），别再让下一次全新部署重复这轮排查。

## 4. 安全处理记录

- 本 session 中用户直接在对话里给过 3 个密码/密钥明文（Doris root、MySQL root、Nexus admin）。
  **全部只写入了 ddh-01 上受保护的 `.cnf`/`conf/api.*.properties` 文件（600 权限，root 专属），
  没有在任何工具输出里回显明文**——每次改配置后用字节数/哈希对比确认写入正确，不用 `cat`/`grep`
  验证内容。
- ddh-01 上现存两个受保护的 MySQL/Doris 探测用凭据文件，下次直接复用，不用重新问用户要密码：
  - `/root/.my_gravitino_probe.cnf`（`[client] user=root`，连的是 ddh-01 本机 MySQL app 库，
    默认 socket/localhost，**不要**加 `-h127.0.0.1`/`--protocol=TCP` 强行走 TCP，会连去不同的
    grant 记录）
  - `/root/.my_doris_probe.cnf`（`[client] host=192.168.10.131 port=9030 user=root`，本 session
    新建，连 Doris）
- 测试数据 `CDC_SIM_TEST_001~009`（MySQL `lineage_flink_verify.pat_surgery`）已清理，`SELECT
  COUNT(*)` 复核 0 行残留，golden/raw 33 行原样未受影响。

## 5. 唯一还没收尾的动作项：tooltip 修复还没部署

`JobDetailDrawer.tsx` 的 tooltip 日期格式化修复（`tooltip={{ title: (datum) =>
dayjs(datum.time).format('MM-DD HH:mm:ss') }}`）已经：
- 写完代码、`npx biome check`/`tsc --noEmit` 干净
- 本地 `npm run dev`（代理 ddh-01 后端）+ ego-browser 登录验证过，tooltip 正确显示
  `08-07 17:05:00` 这种格式，不再是裸时间戳
- **已经打进了本地 `datasophon-api/target/datasophon-manager-3.0-SNAPSHOT.tar.gz`**（第二次全量
  编译产物，`clean package -DskipTests -Dspotless.check.skip=true -pl datasophon-api -am`）
- 代码已提交（commit `72f0c564`）

**但这个 tar.gz 还没有推送到 ddh-01**——ddh-01 现在跑的是第一次编译产物（只含 T6 gauge/sum 修复，
不含 tooltip 修复），PID `954381`（2026-08-07 17:32:18 启动）。用户中途把话题切到"提交代码+写
交接文档"，本 session 判断不该在没被要求的情况下再发起一次重启（刚经历过 §3 那轮连环配置事故，
服务当前是健康的，没必要冒险）。

**下一步**（如果用户要求补上这次部署）：
```bash
scp datasophon-api/target/datasophon-manager-3.0-SNAPSHOT.tar.gz \
  root@192.168.10.131:/data/datasophon-api/datasophon-manager-3.0-SNAPSHOT-tooltip-fix.tar.gz
ssh root@192.168.10.131 "cd /data/datasophon-api && \
  ./datasophon-manager-3.0-SNAPSHOT/bin/datasophon-api.sh stop && \
  mv datasophon-manager-3.0-SNAPSHOT datasophon-manager-3.0-SNAPSHOT.bak-tooltip-fix-\$(date +%Y%m%d%H%M%S) && \
  tar xzf datasophon-manager-3.0-SNAPSHOT-tooltip-fix.tar.gz && \
  cp datasophon-manager-3.0-SNAPSHOT.bak-*/conf/api.properties datasophon-manager-3.0-SNAPSHOT/conf/api.properties && \
  cp datasophon-manager-3.0-SNAPSHOT.bak-*/conf/api.local.properties datasophon-manager-3.0-SNAPSHOT/conf/api.local.properties && \
  ./datasophon-manager-3.0-SNAPSHOT/bin/datasophon-api.sh start"
```
**关键**：这次直接把整份 `conf/` 从当前健康实例复制过去（不是从模板/backup 里挑 key），彻底
绕开 §3 那类"漏 key/错 key"问题——当前健康实例的 `conf/api.properties`+`conf/api.local.properties`
已经是验证过能正常工作的组合，直接原样复用即可，不用再逐个 key 排查。

## 6. Git 状态

- 分支 `feat/flink-lineage`，commit `72f0c564`："fix(lineage): T6 Flink 流速指标误查 sum 表，实为 gauge 表"
- 提交范围：`LineageJobMetricsService.java`/`OtelMetricsQueryService.java`/对应测试、
  `JobDetailDrawer.tsx`/`service.ts`/`contract.test.ts`、T16 全部技术方案+实施方案文档、
  `docs/lineage/`（DDL/样例数据/golden 比对脚本/lineage-emitter 探针工具源码）
- **故意排除**：`datasophon-ui-v2/config/proxy.ts`（本机联调专用，见 `CLAUDE.local.md`）、
  `deploy/deployment-standalone-doris.md`（与本次改动无关的既有修改，属于另一个 APISIX 相关
  epic 遗留在工作区的改动，不要碰）
- 尚未 push 到远程，尚未开 PR（用户没有要求）

## 7. 相关 memory

- `project_flink_realtime_lineage_verify`：整个 epic 的完整记录（T10-T15 详情）
- `feedback_secret_redaction_every_call`：涉密操作过滤纪律
- 建议本 session 结束后补一条新 memory，记录 §3 的"`api.local.properties` 全新部署会丢配置"
  这个系统性坑，避免下次任何人（包括其他 session）重新踩一遍三连坑
