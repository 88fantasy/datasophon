# 集成 Apache Gravitino 1.3.0 作为内置元数据管理服务

> 分支：`feat/gravitino-metadata-service`（基于 `main`）
> 本文档用于跨 session 续接实施——阶段一（本地静态改动 + 验证）已在 2026-07-28 完成并提交，阶段二/三需要连接 `deploy/deployment-standalone-doris.md` 描述的五节点沙箱集群，留给后续 session 执行。

## 当前进度快照（2026-07-28）

分支相对 `main` 已有 3 个提交，按逻辑单元拆分：

```
713a94c5 fix(package): 修正 GRAVITINO V1.3.0__DDL.sql 的异常可执行权限位
e4edaa46 feat(package): 新增 Apache Gravitino 元数据服务
6a055db4 fix(package): 修复 verify_decompress.py 路径失效导致的校验失能
```

**已完成**：改动清单 1-6 全部落地并通过阶段一静态验证（见下方「验证 / 阶段一」，已用真实下载的 1.3.0 官方发行包做过实测核对，非纯静态推断）。阶段二（上传 Nexus + `/internal/meta/refresh`）已于 2026-07-29 在 ddh-01 实机跑通并验证（详见下方「验证 / 阶段二」的实际执行记录）。

**未完成**：阶段三（ddh-02 实机 11 步端到端验证）。用户已确认下次 session 用 claude-in-chrome 走前端安装向导继续，当前所有前置条件（appDb 账号、Nexus 资源、meta 已刷新）均已就绪。

**顺带修复的相关 bug**（`6a055db4`，与 Gravitino 本身无直接关系，但是在核实 `package/verify_decompress.py` 是否可用时发现并修复的）：
1. `META_BASE` 曾指向已废弃路径 `datasophon-api/src/main/resources/meta/datacluster`（模板早已迁移到 `package/raw/meta/datacluster-physical/`），运行即 `FileNotFoundError`。
2. 包路径解析 `PKG_DIR / pkg_name` 未跟随 `download.sh` 的 `repoType` 路由（`raw/packages/` / `base/` / `yum/<arch>/<os>/` 等），导致已下载的包也被误报 MISSING。已补齐与 `download.sh` 一致的 `repo_types_for`/`dest_dir_for` 路由逻辑，并用仓库里现存的 DS/valkey/mysqld_exporter 包实跑验证通过。

后续 session 如果要跑 `python3 package/verify_decompress.py`，现在可以直接信任其输出。

---

## Context

**需求**：把 Apache Gravitino（统一元数据湖 / 开放数据目录）接入 Datasophon，作为可一键部署、启停、监控的内置服务。

**为什么是现在**：仓库当前**完全没有数据资产侧的元数据能力**——`atlas` 关键字只在 `db/migration/1.1.0/V1.1.0__DML.sql` 里留有 4 条告警种子（对应的 ATLAS service_ddl 早已不存在，是死数据）。集群里已部署 DORIS、ELASTICSEARCH、NACOS、DS 等服务，但没有统一的元数据入口。Gravitino 正是补这一层。

**与 Phase G 血缘计划的关系**：`docs/observability-otel-phaseG-flink-血缘与监控-实施计划-2026-07-27.md`（未提交，0% 实施）规划了自研 Flink SQL 静态解析 → MySQL 五表的血缘方案。Gravitino 的 catalog/table/tag 模型与之存在概念重叠，但**本次不触碰 Phase G**：只把 Gravitino 装进来跑起来，不做平台侧 REST 对接、不做前端页面、不改血缘计划。两者的收敛留到后续单独决策。

**用户已确认的决策**：

|        决策项        |                            选择                             |
|-------------------|-----------------------------------------------------------|
| 集成深度              | **仅作为可部署服务纳管**（DDL + 模板 + hooks），不做平台元数据管理 UI             |
| entity store      | **复用平台 MySQL**（新建 `gravitino` 库），不用默认 H2                  |
| Iceberg REST 辅助服务 | **不启用**（只跑 8090 核心 metadata server）                       |
| status 退出码修正方式    | **`append_line` 改官方 `bin/gravitino.sh`**，不引入自带 control 脚本 |
| 验证环境              | **`deploy/deployment-standalone-doris.md` 的五节点集群**        |

**目标产出**：用户在 Datasophon Web 上能像装 NACOS 一样选中 GRAVITINO、填参数、部署、启停、看状态、点快捷链接进 Gravitino Web UI，指标自动进 OTel → Doris。

---

## 调研结论：六个必须绕开的坑

1. **`bin/gravitino.sh status` 退出码恒为 0** — 官方 `check_process_status()`（第 44-53 行）只 `echo` 文本，进程不存在时也返回 0。Datasophon 靠 statusRunner 退出码判存活，直接用会导致「服务已死但平台永远显示 RUNNING」。DS 服务用 `append_line` 补 `exit 1` 解决过同样问题（`DS/service_ddl.json` 的第 130/139 行注入）→ 本次沿用该做法。

2. **`append_line` 的全文去重会静默吃掉这次注入**（本次最隐蔽的坑）— `AppendLineStrategy.invoke()` 的守卫是 `!lines.contains(text)`，做的是**整个文件的精确行匹配**，不是"该行位置是否已存在"。而 `gravitino.sh` **第 30 行已经有一模一样的 `    exit 1`**（`--config` 参数校验里）。若直接注入 `    exit 1`，条件为 false → **跳过插入且返回 success**，安装看起来全绿但 status 依旧恒 0。
   → 对策：注入文本必须全文唯一，用 `    exit 1 # datasophon: propagate status exit code`。
   → **已用真实下载的 1.3.0 发行包核实**：第 30 行确认存在一模一样的 `    exit 1`；注入文本在全文出现次数为 0。见下方「验证 / 阶段一」的实测记录。

3. **强制 Java 17+** — `bin/common.sh` 的 `check_java_version()` 检测到 `< 17` 直接 `exit 1`。节点默认 `JAVA_HOME` 常指向 JDK8 → 必须 `export JAVA_HOME=$JAVA_HOME17`（该变量由 `datasophon-worker/src/main/resources/script/datasophon-env.sh:2` 定义为 `/usr/local/jdk17`，NACOS 已在目标环境实测可用）。本次采用 KYUUBI 的做法：DDL 加 `javaHome` 参数（`defaultValue: "$JAVA_HOME17"`），模板里 `export JAVA_HOME=${javaHome}`，而不是像 NACOS 那样硬编码进模板——这样该值可在 UI 上按需覆盖。

4. **MySQL JDBC 驱动不随包分发**（GPL 协议）— 必须手动放进 `libs/`。DS 已有现成范式：`link` hook 指向 `${ROOT.VosManager.INSTALL_PATH}/datasophon-worker/lib/mysql-connector-j-8.2.0.jar`。

5. **`scripts/mysql/` 里有 11 个 `schema-*.sql`**（0.5.0 → 2.0.0）+ 一个 `iceberg-metrics-schema-1.1.0-mysql.sql`。`initDb` hook 的 `DatabaseMigration.getAllMigrations()` 会**递归扫全目录并按版本顺序全部执行**，且 `iceberg-metrics-schema-1.1.0` 会与 `schema-1.1.0` 撞版本号抛 `Duplicate version` — **绝不能把 scriptPath 指向包内 `scripts/mysql`**。
   → 对策：把 `schema-1.3.0-mysql.sql` 重命名为 `V1.3.0__DDL.sql` 放进 meta 目录，`download` 到安装目录的 `db/migration/`，让 initDb 走默认 scriptPath + 默认 ddlPattern。
   → **已用真实下载的 1.3.0 发行包核实**：`scripts/mysql/` 目录下确实有 11 个版本文件，坑属实。已提取 `schema-1.3.0-mysql.sql`（620 行）重命名落地。

6. **Prometheus 端点是 `/prometheus/metrics`**，不是默认的 `/metrics` → 需在 `OtelScrapeConfigBuilder.PATH_OVERRIDES` 加一条。

---

## 改动清单（已全部完成，见对应提交）

### 1. `package/manifest.json` — 新增下载条目 ✅

```json
{ "service": "GRAVITINO", "arch": "common",
  "packageName": "gravitino-1.3.0-bin.tar.gz",
  "decompressPackageName": "gravitino-1.3.0-bin",
  "downloadUrl": "https://downloads.apache.org/gravitino/1.3.0/gravitino-1.3.0-bin.tar.gz",
  "status": "public" }
```

> 官方只发布标准 `-bin` 包（`-bin-all` 含 catalogs-contrib，需自行构建）。标准包已含 hive / jdbc / iceberg / kafka / fileset / model 等核心 catalog，满足本次需求。

### 2. `datasophon-cli-go/internal/config/configs/cluster-config.yml` — appDbs 加一条 ✅

在 `mysql.appDbs` 数组末尾（现有 datasophon / hive / dolphinscheduler / nacos / datart 等 9 条之后）追加：

```yaml
- account: "gravitino"
  password: "fI5sQ4yQ4fP5"
  dbName: "gravitino"
```

**库和账号由集群初始化阶段建好，服务安装期只建表**——这是仓库既有的职责划分（NACOS/DS 都如此）。**目标沙箱环境已完成初始化，这条 appDbs 改动不会自动生效到已存在的集群**，需在 `ddh-01` 手工补一次（见验证阶段三第 0 步）。

### 3. `package/raw/meta/datacluster-physical/GRAVITINO/service_ddl.json` — 新建（核心） ✅

以 `NACOS/service_ddl.json` 为骨架。

**顶层**：`name: "GRAVITINO"`（必须与目录名一致，`DdlMetaServiceImpl` 强校验）、`type: "MIDDLEWARE"`、`version: "1.3.0"`、`dependencies: []`、`arch.common.{packageName, decompressPackageName}`（`packageName` 在 `arch` 里，**不是顶层字段**）。

**单角色 `GravitinoServer`**（`roleType: master`, `cardinality: "1"`）：

```json
{
  "name": "GravitinoServer",
  "label": "GravitinoServer",
  "roleType": "master",
  "cardinality": "1",
  "logFile": "logs/gravitino-server.log",
  "jmxPortParam": "gravitino.server.webserver.httpPort",
  "portParams": ["gravitino.server.webserver.httpPort"],
  "startRunner":   { "timeout": "120", "program": "bin/gravitino.sh", "args": ["start"] },
  "stopRunner":    { "timeout": "600", "program": "bin/gravitino.sh", "args": ["stop"] },
  "statusRunner":  { "timeout": "60",  "program": "bin/gravitino.sh", "args": ["status"] },
  "restartRunner": { "timeout": "120", "program": "bin/gravitino.sh", "args": ["restart"] },
  "externalLink": {
    "name": "Gravitino UI", "label": "Gravitino UI",
    "url": "http://${host}:${GRAVITINO.gravitino.server.webserver.httpPort}/"
  }
}
```

四个 runner 全部直接用官方脚本——`gravitino.sh` 的 `case` 分支（第 211-230 行）原生支持 `start|run|stop|restart|status`，`restart` 就是 `stop` + `start`，且 `stop` 内有 `wait_for_gravitino_server_to_die`（10s 超时 + force kill）。**只有 `status` 的退出码需要靠 hook 补**。

**`hooks`（POST_INSTALL，按数组顺序执行，顺序不能乱）**：

| # |    action     |                                                 作用                                                 |
|---|---------------|----------------------------------------------------------------------------------------------------|
| 1 | `append_line` | 给 `bin/gravitino.sh` 注入 status 退出码（**已实测核对，见下方阶段一**）                                               |
| 2 | `download`    | `script/V1.3.0__DDL.sql` → `db/migration/V1.3.0__DDL.sql`（md5: `c2b983facc83a01b522c4ec585213125`） |
| 3 | `link`        | worker 的 `lib/mysql-connector-j-8.2.0.jar` → `libs/mysql-connector-j-8.2.0.jar`                    |
| 4 | `link`        | worker 的 `otel/opentelemetry-javaagent.jar` → `otel/opentelemetry-javaagent.jar`                   |
| 5 | `initDb`      | 建表（见下）                                                                                             |

第 3、4 条的 `source` 直接照抄 `DS/service_ddl.json:74,82` 的 `${ROOT.VosManager.INSTALL_PATH}/datasophon-worker/...` 写法。

**append_line hook（第 1 条，实际落地内容）**：

```json
{ "type": "POST_INSTALL", "action": "append_line",
  "params": {
    "line": 49,
    "text": "    exit 1 # datasophon: propagate status exit code",
    "source": "bin/gravitino.sh"
  } }
```

语义核对（`AppendLineStrategy.invoke()`）：`lines.add(line - 1, text)` = **在原第 N 行之前插入**。官方脚本第 48 行是 `    echo "Gravitino Server is not running"`，第 49 行是 `  else` → 传 `line: 49` 正好把 `exit 1` 插在 echo 之后、`else` 之前，落在 `if [[ -z "${pid}" ]]` 分支内。

**initDb hook params（实际落地内容，全部走默认路径，不传 `scriptPath` / `ddlPattern`）**：

```json
{ "type": "POST_INSTALL", "action": "initDb",
  "params": {
    "resourceKey": "GRAVITINO",
    "metaStorage": "datasophon",
    "driver": "com.mysql.cj.jdbc.Driver",
    "url": "jdbc:mysql://${ROOT.Mysql.mysqlHostPort}/gravitino?useUnicode=true&characterEncoding=utf-8&useSSL=false&allowPublicKeyRetrieval=true",
    "username": "gravitino",
    "password": "fI5sQ4yQ4fP5"
  } }
```

- `metaStorage: "datasophon"` → 迁移历史写 datasophon 库的 `t_ddh_srv_db_migration_history`（该表由 `V2.0.0__DDL.sql:80` 建，已存在）。Worker 侧连接信息取自 `conf/worker.properties` 的 `mysql.*`（已确认存在，见 `deploy/compose/conf/worker.properties:12-16`）。
- 不传 `scriptPath` → 默认 `<安装目录>/db/migration`；不传 `ddlPattern` → 默认 `.*[Vv](?<version>\d+(\.\d+)*)__DDL\.sql$`，正好匹配下发的 `V1.3.0__DDL.sql`，且不会误匹配任何其他文件。

**`configWriter.generators`（实际落地，两个）**：

```json
{ "filename": "gravitino.conf", "configFormat": "properties", "outputDirectory": "conf",
  "includeParams": [
    "gravitino.server.webserver.host",
    "gravitino.server.webserver.httpPort",
    "gravitino.entity.store",
    "gravitino.entity.store.relational",
    "gravitino.entity.store.relational.jdbcUrl",
    "gravitino.entity.store.relational.jdbcDriver",
    "gravitino.entity.store.relational.jdbcUser",
    "gravitino.entity.store.relational.jdbcPassword",
    "gravitino.audit.enabled"
  ] },
{ "filename": "gravitino-env.sh", "configFormat": "custom", "outputDirectory": "conf",
  "templateName": "gravitino-env.ftl", "includeParams": ["javaHome", "gravitinoMem"] }
```

`gravitino.conf` 是标准 `key = value` properties 格式，worker 内置的 `properties.ftl`（输出 `name=value`）直接可用，**无需自定义模板**。

**`parameters`（实际落地，11 项）**：

|                       name                       |                       defaultValue                       |                 备注                 |
|--------------------------------------------------|----------------------------------------------------------|------------------------------------|
| `gravitino.server.webserver.host`                | `0.0.0.0`                                                |                                    |
| `gravitino.server.webserver.httpPort`            | `8090`                                                   | `port: true`, `register: true`     |
| `gravitino.entity.store`                         | `relational`                                             | `hidden: true`                     |
| `gravitino.entity.store.relational`              | `JDBCBackend`                                            | `hidden: true`                     |
| `gravitino.entity.store.relational.jdbcUrl`      | `jdbc:mysql://${ROOT.Mysql.mysqlHostPort}/gravitino?...` | 照抄 NACOS `nacosDbUrl:148` 的占位符写法   |
| `gravitino.entity.store.relational.jdbcDriver`   | `com.mysql.cj.jdbc.Driver`                               | 8.x 驱动类名，非 `com.mysql.jdbc.Driver` |
| `gravitino.entity.store.relational.jdbcUser`     | `gravitino`                                              |                                    |
| `gravitino.entity.store.relational.jdbcPassword` | `fI5sQ4yQ4fP5`                                           | 与 appDbs 一致                        |
| `gravitino.audit.enabled`                        | `false`                                                  | `type: switch`                     |
| `javaHome`                                       | `$JAVA_HOME17`                                           | KYUUBI 同款写法，只喂给 env 模板             |
| `gravitinoMem`                                   | `-Xms1024m -Xmx2048m -XX:MaxMetaspaceSize=512m`          | 只喂给 env 模板，不进 gravitino.conf       |

> `gravitino.conf.template` 里还有 shutdown.timeout / minThreads / maxThreads / cache.* 等项。生成的文件会**整体覆盖** `conf/gravitino.conf`，未写进 `includeParams` 的键将不复存在、退回代码内默认值。以「暴露上表 9 个 gravitino.* 项 + 其余走代码内默认」为准；若阶段三实测发现某项代码默认值与模板文件不符且影响运行，再补进 parameters。

### 4. `.../GRAVITINO/templates/gravitino-env.ftl` — 新建 ✅

生成的 `conf/gravitino-env.sh` 实际内容：

```bash
export JAVA_HOME=${javaHome}
export GRAVITINO_MEM="${gravitinoMem}"
export JAVA_TOOL_OPTIONS="-javaagent:$(pwd)/otel/opentelemetry-javaagent.jar -Dotel.service.name=gravitino -Dotel.exporter.otlp.endpoint=http://localhost:4317 -Dotel.exporter.otlp.protocol=grpc -Dotel.traces.exporter=otlp -Dotel.metrics.exporter=none -Dotel.logs.exporter=none"
```

`otel.metrics.exporter=none` 是刻意的——指标走 Prometheus scrape（见改动 6），与 NACOS 的处理一致（`NACOS/service_ddl.json:84`）。

用自定义模板整体生成、而不是用 `append_line` 改 `gravitino-env.sh`，是因为该文件本就是给用户改的配置文件（官方模板里全是注释掉的 export），整体生成语义更清晰；而 `bin/gravitino.sh` 是可执行逻辑，只能局部注入。

> 官方 `gravitino-env.sh.template` 里有 `export GRAVITINO_USE_WEB_V2="${GRAVITINO_USE_WEB_V2:-true}"`（控制 Web UI v1/v2）。整体覆盖后该行消失，UI 版本回落到 `gravitino.sh` 里 `WAR_V2_PATH`/`WAR_V1_PATH` 的探测逻辑（第 190-209 行，默认优先 v2）。行为一致，不必特意保留；**若阶段三实测 UI 打不开，先检查这个**，再把这行加回模板。

### 5. `.../GRAVITINO/script/V1.3.0__DDL.sql` — 新建 ✅

Gravitino 官方 `scripts/mysql/schema-1.3.0-mysql.sql`（v1.3.0 tag，620 行）的**逐字副本，仅重命名**，文件头附加了 4 行来源说明注释。权限已修正为 644（首次 `cp` 时从源 tar 包带入了异常的可执行位，`713a94c5` 已修）。

### 6. `datasophon-api/src/main/java/com/datasophon/api/observability/OtelScrapeConfigBuilder.java` — 一行改动 ✅

`PATH_OVERRIDES` 静态块末尾追加：

```java
PATH_OVERRIDES.put("GravitinoServer", "/prometheus/metrics");
```

这是本次**唯一的 Java 改动**。collector 侧无需动：`otelcol.ftl` 是黑名单（只 drop 特定指标），不是白名单，新服务指标自动流入 Doris。查询侧的 attribute 白名单（`OtelMetricsQueryService`）只在做前端看板时才需要，本次不涉及。

---

## 明确不做的事

- ❌ Worker 侧 `GravitinoHandlerStrategy` — runner + hooks 已能完整表达启停，**不写策略类就不用重新分发 worker 到每个节点**。这在目标环境尤其重要：§11.2 #9 已记录「Worker jar 版本漂移可能在未同步节点复发」，五节点中 ddh-01/03/04/05 的 worker 曾落后于 ddh-02。
- ❌ Master 侧 `strategy/` 注册 — 无需编排期前置逻辑。
- ❌ 任何 `datasophon-ui-v2` 改动 — 服务列表、安装向导、参数表单、快捷链接全部由 DDL 驱动。
- ❌ Gravitino 监控看板页面 — 通用 OTel 抓取已覆盖，专属面板另议。
- ❌ Iceberg REST server / catalog 自动注册 / 血缘对接 — 超出本次范围。

---

## 验证

### 阶段一：静态（已完成，2026-07-28）

已执行并全部通过：

1. **DDL 可解析**：`python3 -c "import json;json.load(open('package/raw/meta/datacluster-physical/GRAVITINO/service_ddl.json'))"` → OK
2. **真实下载 1.3.0 发行包做实测核对**（非纯静态推断）：
   - `tar -xzf gravitino-1.3.0-bin.tar.gz gravitino-1.3.0-bin/bin/gravitino.sh` → 230 行，与 GitHub template 完全一致
   - `sed -n '28,32p'` 确认第 30 行为 `    exit 1`（`--config` 校验分支）
   - `grep -c -F "exit 1 # datasophon: propagate status exit code" bin/gravitino.sh` → 0（唯一性确认）
   - `tar -tzf ... | grep scripts/mysql` 确认确实有 11 个历史 schema 文件
   - 提取 `schema-1.3.0-mysql.sql`（620 行）用作 `V1.3.0__DDL.sql`
   - 已清理本地下载的 960MB 临时文件（不提交进仓库，`package/.gitignore` 已排除 `raw/packages/`）
3. **`datasophon-cli-go`**：`go build ./...` 通过；`make test` 全绿（appDbs 数量无测试硬编码依赖）
4. **`datasophon-api`**：`-pl datasophon-api -am compile` 通过；`OtelScrapeConfigBuilderTest` 12/12 通过；Checkstyle 0 违规
5. **`spotless:check` 全仓扫描**：**唯一违规是与本任务无关的、会话开始前就存在的未提交 Phase G 文档**（`docs/observability-otel-phaseG-flink-血缘与监控-实施计划-2026-07-27.md`），本次全部改动（含 Java 一行编辑）均无格式违规 → 未跑全局 `spotless:apply`，避免误改该无关文件
6. **`package/verify_decompress.py`**：修复两个 pre-existing bug 后实跑，能正确识别已下载包（DS/valkey/mysqld_exporter → OK；otelcol-contrib → BINARY-ONLY；GRAVITINO 因本地未保留下载包 → MISSING，属预期）

### 阶段二：元数据入库（ddh-01，**已完成，2026-07-29**）

实际执行记录：
1. 阶段三第 0 步的建库账号提前挪到本阶段执行（`initDb` hook 依赖它先存在）：在 ddh-01 用与 `mysql_app_db` 等价的 SQL 序列（`CREATE DATABASE` / `CREATE USER` / 两条 `ALTER USER` / `GRANT ALL PRIVILEGES ON *.*` / `FLUSH PRIVILEGES`）建好 `gravitino` 库与账号，`mysql -ugravitino -p... -e 'SELECT 1;'` 验证可登录。
2. 本地 `package/raw/packages/gravitino-1.3.0-bin.tar.gz`（960MB）此前会话其实未被清理，仍在磁盘上；`python3 package/verify_decompress.py` 校验 `[OK]`，未重新下载。
3. `datasophon-cli upload registry --files ...`（service_ddl.json + gravitino-env.ftl + V1.3.0__DDL.sql + gravitino-1.3.0-bin.tar.gz）四个文件上传 ddh-01 Nexus，`success=4 fail=0`。
4. `POST /ddh/internal/meta/refresh` 返回 `{"success":true,"physicalTotal":19,"physicalLoaded":19,"errors":[]}`。
5. 直接查库核对：`t_ddh_frame_service`（id=19,service_name=GRAVITINO）、`t_ddh_frame_service_role`（service_role_name=GravitinoServer, cardinality=1, log_file=logs/gravitino-server.log）均已入库。

以下是原计划的执行命令模板（供参考，已用真实凭据跑通,此处仍保留占位符供后续 session 或其它环境复用）：

```bash
# 若沙箱环境的 gravitino appDb 库账号尚未建，先在 ddh-01 补建（见阶段三第 0 步）

# 上传 DDL/模板/脚本 + 安装包到 ddh-01 的 Nexus
datasophon-cli upload registry --productPackagesPath ./package \
  --webHost 192.168.10.131 --webPort 8081 -u admin -p <NEXUS_PASSWORD> \
  --dockerHttpPort 8083 --enableRegistry \
  --files raw/meta/datacluster-physical/GRAVITINO/service_ddl.json,\
raw/meta/datacluster-physical/GRAVITINO/templates/gravitino-env.ftl,\
raw/meta/datacluster-physical/GRAVITINO/script/V1.3.0__DDL.sql

# 单独确保安装包已下载到 package/raw/packages/gravitino-1.3.0-bin.tar.gz 再一并上传
# （本地临时文件已清理，需重新 bash package/download.sh 或手动下载）

# 热刷新，errors[] 必须为空
curl -XPOST -H "X-Internal-Token: <INTERNAL_TOKEN>" \
  http://192.168.10.131:8080/ddh/internal/meta/refresh
```

验收：`frame_service` / `frame_service_role` 出现 GRAVITINO / GravitinoServer；前端服务列表可见。

> 这一步在目标环境已有成熟先例：`deploy/deployment-standalone-doris.md` §7.5「NACOS ddl 加载失败根因与修复」、§7.6「`/internal/meta/refresh` 现场部署」。若刷新报错，先照 §7.5 的排查路径走。

### 阶段三：端到端（五节点集群，Gravitino 装 `ddh-02`，**尚未执行**）

**为什么是 ddh-02**：§1.1 定义它为「阶段 A 中间件主要承载节点，不部署 Doris」，NACOS/DS/ELASTICSEARCH/APISIX 都在这里；MySQL 与 Nexus 在 ddh-01。

按顺序验证，每步都是下一步的前提：

| #  |                                                                          步骤                                                                          |                                                                                                  验收标准                                                                                                   |
|----|------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 0  | 在 **ddh-01** 建库账号：`datasophon-cli init mysql_app_db --rootPassword <MYSQL_ROOT_PASSWORD> -a gravitino -p fI5sQ4yQ4fP5 -d gravitino --mysqlPort 3306` | `mysql -e "SHOW DATABASES"` 可见 `gravitino`                                                                                                                                                              |
| 1  | 前端安装向导选 GRAVITINO，角色分配到 ddh-02                                                                                                                       | 5 个 hook 全部成功（Worker 日志）                                                                                                                                                                                |
| 2  | **核对 append_line 真的生效**（坑 #2 的验证点，本地已静态核实，仍需在真实安装产物上复核一次）                                                                                            | ddh-02 上 `sed -n '44,55p' <安装目录>/bin/gravitino.sh` 能看到注入的 `exit 1` 行                                                                                                                                    |
| 3  | **initDb 建表结果**（本仓库**首次实际使用**该 hook，最大不确定项）                                                                                                          | `mysql gravitino -e "SHOW TABLES"` 有 `metalake_meta`/`catalog_meta`/`schema_meta`/`table_meta` 等；datasophon 库 `t_ddh_srv_db_migration_history` 有 `resource_key='GRAVITINO', version='1.3.0', success=1` |
| 4  | 配置文件与软链                                                                                                                                              | `conf/gravitino.conf` jdbcUrl 指向 192.168.10.131；`conf/gravitino-env.sh` 含 `JAVA_HOME17` 与 javaagent；`ls -l libs/mysql-connector-j-8.2.0.jar` 软链有效                                                       |
| 5  | 启动                                                                                                                                                   | 服务变 RUNNING；`curl -H "Accept: application/vnd.gravitino.v1+json" http://192.168.10.132:8090/api/version` 返回 1.3.0                                                                                       |
| 6  | **status 退出码**（本次专门修的坑）                                                                                                                              | 手工 `kill` 掉进程，平台在下一轮巡检（15s/30s）把角色标为**非 RUNNING**。仍显示 RUNNING = 第 2 步的注入没生效                                                                                                                             |
| 7  | 停止 / 重启                                                                                                                                              | 前端操作，状态正确翻转                                                                                                                                                                                             |
| 8  | 快捷链接                                                                                                                                                 | 点 "Gravitino UI" 打开 Web UI，**创建一个 metalake**（验证 MySQL 后端真正可写，比只看首页有意义）                                                                                                                                  |
| 9  | 指标                                                                                                                                                   | `curl http://192.168.10.132:8090/prometheus/metrics` 有输出；ddh-02 的 `otelcol.yaml` 出现 `job_name: 'GravitinoServer'` + `metrics_path: '/prometheus/metrics'`；等一个抓取周期后查 Doris（见下）                           |
| 10 | Trace                                                                                                                                                | 链路跟踪工作台能看到 `otel.service.name=gravitino` 的调用                                                                                                                                                            |

第 9 步的 Doris 查询（在 ddh-01）：

```sql
-- mysql -h127.0.0.1 -P9030 -uroot
SELECT metric_name, count(*) FROM otel.otel_metrics_gauge
WHERE resource_attributes['service.name']='GravitinoServer'
  AND timestamp > now() - INTERVAL 10 MINUTE
GROUP BY metric_name LIMIT 20;
```

> 查不到数据时先确认 Doris 动态分区已覆盖当天——该环境曾出现「8 天旧卷动态分区未跟上当前日期」导致误判为断链。

### 目标环境的已知噪音（不要误判为 Gravitino 的问题）

- **装 Gravitino 会触发 ddh-02 的 OTELCOLLECTOR restart**：`deploy/deployment-standalone-doris.md` §11.4 记录，平台在某节点每装一个新中间件角色都会 restart 该节点共享的 OtelCollector；而 `control.sh` 无并发保护，pid 文件可能与真实进程错位，导致 OTELCOLLECTOR 显示 `STOP` / `EXISTS_EXCEPTION`，但数据链路其实健康。修复已提交到 `OTELCOLLECTOR/script/control.sh`（加 `flock`）但**尚未分发到已装节点**。
  → 处置：先按 §11.4 的方法确认 `otelcol-contrib` 进程真实存活 + `8888` 端口 `send_failed=0`，再改写 pid 文件止血。**不要把它算作 Gravitino 的缺陷。** 也可以在装 Gravitino 前先把带 flock 的 `control.sh` 分发到 ddh-02，顺带清掉这个遗留（属于额外收益，不是本次必须）。
- **告警数噪音**：§9.2 记录服务删除/安装期间关联告警未及时清空，下一巡检周期自愈。

### 阶段三：实际执行记录（2026-07-29，已完成）

用 ego-browser 走前端安装向导（清单模式，`deploy/gravitino-deploy.yaml` 指定 GRAVITINO → ddh-02），11 步全部验证通过，过程中发现并修复 4 个真实 bug（全部是"仅真实安装才能暴露"的类型，静态审查 DDL/JSON 看不出来）：

1. **Nexus 上 `gravitino-1.3.0-bin.tar.gz.md5` 缺失**：阶段二上传时 `--files` 精确模式只列了主体 tar.gz，未连带 `.md5` sidecar，导致 Worker 下载前 md5 校验直接报 "does not exists"。顺手把 `datasophon-cli-go` 的 `upload registry` 改成自动检测：raw/packages/ 下的安装包若本地缺 `.md5` 就自动计算生成并补传（`internal/cli/upload/registry.go` 新增 `ensureLocalMD5Sidecar`/`needsRawMD5Sidecar`，`uploadSpecificFiles` 与 `repositoryUploadBatch` 两条路径都接入，新增 2 个单测）。
2. **`worker.properties` 里 `mysql.ip=127.0.0.1`/`mysql.password` 为空，从未被正确渲染**：这是独立于 Gravitino 的平台级 bug——`conf/worker.properties` 是 Maven assembly 从仓库根静态拷贝的模板，全部节点用同一份文件；`datasophon-cli-go` 全仓找不到任何一处把 `cluster.yml` 的真实 `mysql.node`/`mysql.password` 渲染进各节点 `worker.local.properties`（覆盖层）的逻辑。GRAVITINO 是本仓库第一个真正走到 `InitDbHookAction` 连接逻辑的服务，此前从未暴露。已完成两部分修复：① 环境热修复——手工在 ddh-02 补写 `worker.local.properties`；② 代码修复——`datasophon-cli-go` 新增 Step `init-worker-mysql-conf`（`internal/cli/init/worker_local_properties.go` + `internal/plan/builders_cluster.go` 的 `buildWorkerMysqlConf`），initALL DAG 从 36 步变为 37 步，对全部节点写入真实 MySQL IP/密码，新增 4 个单测，`docs/reference/init-all-dag.md` 等文档已同步。
3. **`gravitino-env.ftl` 用 `$(pwd)` 定位 javaagent 路径**：Datasophon 对"正式任务命令"（安装/启动）和"周期性巡检"用两条不同的 Worker 执行路径——前者会先 `cd` 进服务安装目录，后者不会（直接从上级目录拼相对路径执行）。`$(pwd)` 在巡检路径下解析到错误目录，javaagent 加载失败拖累 `java -version` 探测崩溃，导致 `bin/gravitino.sh status` 恒判定失败（`GravitinoServer Survive` 告警每 30s 触发一次）。改用 `${GRAVITINO_HOME}`（`bin/common.sh` 用 cd+pwd 稳定算出，不受调用方 cwd 影响）。
4. **沙箱运行的 `datasophon-api` 是编译自本次 DDL 集成之前的旧 jar**：不含 `OtelScrapeConfigBuilder.java` 里 `GravitinoServer → /prometheus/metrics` 这行改动，导致 otelcol.yaml 的 `metrics_path` 落到默认的 `/metrics`（Gravitino 该路径返回纯 JSON，不是 Prometheus 文本格式，采集会解析失败）。重新编译打包（`-Dspotless.check.skip=true` 绕开与本任务无关的 Phase G 文档格式违规）并替换 ddh-01 的 jar、重启 Master，修复生效。

11 步逐条验证结果：
- 第 1 步（安装向导）：清单预勾选 GRAVITINO、Master 角色自动分配 ddh-02、"无 Worker 角色"提示均与 DDL 一致。
- 第 2 步（append_line 生效）：`sed -n '44,55p' bin/gravitino.sh` 确认 `exit 1 # datasophon: propagate status exit code` 正确插入 if 分支内。
- 第 3 步（initDb 建表）：`SHOW TABLES` 见 `metalake_meta`/`catalog_meta`/`fileset_meta` 等；`t_ddh_srv_db_migration_history` 有 `resource_key=GRAVITINO,version=1.3.0,success=1`。
- 第 4 步（配置文件）：`gravitino.conf` jdbcUrl 正确指向 `192.168.10.131:3306`；`gravitino-env.sh` 含 `JAVA_HOME17` 与修复后的 javaagent 路径；`libs/mysql-connector-j-8.2.0.jar` 软链有效。
- 第 5 步（启动）：`curl .../api/version` 返回 `{"version":{"version":"1.3.0",...}}`。
- 第 6 步（status 退出码）：手工 `pkill` 后巡检立即（30s 内）探测到异常并告警；前端角色状态同步从"正在运行"翻转为"停止"。
- 第 7 步（停止/重启）：前端"选择操作 → 重启"后状态正确翻转回"正在运行"。
- 第 8 步（快捷链接 + 创建 metalake）：Web UI 自动跳转 `/ui/metalakes`（UI v2）；创建 `datasophon_verify` metalake，`mysql -ugravitino gravitino` 直查 `metalake_meta` 表确认真实落库，MySQL 后端可写验证通过。
- 第 9 步（指标）：`curl .../prometheus/metrics` 返回 1843 行 Prometheus 格式数据；Doris `otel.otel_metrics_gauge` 查到 `up=35`、`gravitino_server_http_server_queued_request_num` 等真实业务指标，全链路（Gravitino → scrape → OTel Collector → Doris）打通。
- 第 10 步（Trace）：Doris `otel.otel_traces` 查到 `service_name='gravitino'` 下的真实 span，含 SQL（`SELECT gravitino.entity_change_log`）与 HTTP（`GET /api/metalakes`）两类，与实际操作一一对应。

### 若 initDb hook 在真实环境失败的退路

按优先级依次尝试，不要一上来就写 Java：

1. 看 Worker 任务日志的具体异常——大概率是连接串或权限问题，而非机制问题（`initDb` 的三个前提已静态确认：迁移历史表存在、`worker.properties` 有 `mysql.*`、`Migration.isMigrationFile` 只按 `.sql` 后缀过滤）。
2. 退路 A：改用 `execShell` hook 直连 `mysql` 客户端执行（ddh-01 装了 MySQL 必有客户端；ddh-02 需确认）。
3. 退路 B：仿 `NacosMasterHandlerStrategy`（`datasophon-worker/.../strategy/NacosMasterHandlerStrategy.java:25-96`，已有「读配置 → 连 MySQL → 判表存在 → 批量执行 SQL」的成熟范式）写 `GravitinoHandlerStrategy`。代价是要重新分发 worker 到所有节点，且踩上 §11.2 #9 的版本漂移风险——**最后手段**。

---

## 进度跟踪

|  #  |  阶段  |                     内容                      |                                    验证方式                                     |                 状态                 |
|-----|------|---------------------------------------------|-----------------------------------------------------------------------------|------------------------------------|
| 1   | 准备   | 建分支 `feat/gravitino-metadata-service`       | `git branch`                                                                | ✅                                  |
| 2   | 包    | manifest.json 加条目 + 真实下载核实                  | 实测行号/唯一性/schema 目录                                                          | ✅                                  |
| 3   | 库    | cluster-config.yml appDbs 加 gravitino       | `make test` 通过                                                              | ✅                                  |
| 4   | 行号核对 | 解压真实包核对 gravitino.sh 第 48/49 行 + 文本唯一性      | `sed` + `grep -c` 结果为 0                                                     | ✅                                  |
| 5   | 资源   | V1.3.0__DDL.sql + gravitino-env.ftl         | 文件就位，md5 已记入 DDL                                                            | ✅                                  |
| 6   | DDL  | service_ddl.json 全字段（含 5 个 hook）            | JSON 可解析                                                                    | ✅                                  |
| 7   | 监控   | OtelScrapeConfigBuilder PATH_OVERRIDES      | `mvnw test` 12/12 绿 + Checkstyle 0 违规                                       | ✅                                  |
| 7.5 | 顺带修复 | verify_decompress.py 两处路径 bug               | 实跑，DS/valkey/mysqld_exporter 核验通过                                           | ✅                                  |
| 8   | 入库   | 上传 Nexus + meta 热刷新                         | frame_service 出现 GRAVITINO                                                  | ✅(2026-07-29)                      |
| 9   | 端到端  | ddh-02 实机 11 步（重点 #2 注入生效、#3 initDb、#6 退出码） | 浏览器实机 + Doris 查询                                                            | ✅(2026-07-29，详见下方「验证 / 阶段三」实际执行记录) |
| 10  | 收尾   | PR + 回写手册                                   | `spotless:apply` 已跑（本任务范围内文件无违规）；`deployment-standalone-doris.md` §7.13 已回写 | ✅(2026-07-29)                      |

第 10 步的「回写手册」：在 `deploy/deployment-standalone-doris.md` 追加一节现场记录（该文档的既有惯例，如 §7.7 NACOS、§7.12 VALKEY），凭据一律用占位符。

---

## 已知限制（需在 PR 描述里写明）

- **单实例**：`cardinality: "1"`，不做 Gravitino HA（1.3.0 的 HA 需额外配置，超出本次范围）。
- **catalog 需手工创建**：平台不自动把已部署的 DORIS/KAFKA 注册成 Gravitino catalog——那属于已否决的范围 B。用户需在 Gravitino UI 里自行创建 metalake 与 catalog。
- **不含 contrib catalog**：用的是官方标准 `-bin` 包。
- **密码明文**：`jdbcPassword` 与现有 NACOS/DS 一样明文放在 DDL 默认值里。这是既有约定，但注意 `deploy/deployment-standalone-doris.md` §11.2 #6 已把「Nacos 登录密码明文提交」列为待处理遗留问题——本次是在**复制一个已记录的问题**，PR 里应点明，避免日后整改时漏掉 GRAVITINO。
- **依赖 `bin/gravitino.sh` 的行号**：`append_line` 用绝对行号定位，Gravitino 升级到 1.4/2.0 时**必须重新核对第 48/49 行**（用 `tar -xzf` 解出实际发行包的 `bin/gravitino.sh` 手工核对，不要只信 GitHub template）。升级检查清单里要写上这条。
- **首次使用 initDb hook**：该 hook 在本仓库此前零调用点，已于 2026-07-29 在 ddh-02 实机验证通过（`GRAVITINO Migration success! version: 1.3.0`，迁移历史表与 metalake_meta 等业务表均已核实）。

---

## 后续 session 的执行入口

1. `git checkout feat/gravitino-metadata-service`，确认提交都在（`git log --oneline main..HEAD`）。
2. 阶段二（2026-07-29）与阶段三（2026-07-29）均已完成，详见「验证 / 阶段二」「验证 / 阶段三：实际执行记录」两节。阶段三过程中额外修复了 3 个独立于 Gravitino 本身的平台级 bug（Nexus md5 sidecar 自动生成、worker.local.properties 渲染缺失、Master jar 需重新编译部署），代码改动已落在当前分支，`datasophon-cli-go` 全部测试通过。
3. 直接从「进度跟踪」表第 10 步开始：
   - PR 描述写清楚「已知限制」章节内容 + 本次阶段三额外修复的 3 个平台级 bug（尤其是 worker.local.properties 这条，影响所有服务而不只是 Gravitino，PR 描述要点明这是顺带修复的独立问题）。
   - `deploy/deployment-standalone-doris.md` 追加一节现场记录（参照既有 §7.7 NACOS、§7.12 VALKEY 惯例），凭据用占位符。
   - 沙箱 ddh-01 的 `datasophon-api` 已手动替换为最新 jar 并重启，属于会话内的环境热修复，不需要额外操作；但 `datasophon-cli-go` 新增的 `init-worker-mysql-conf` Step 需要**新建集群**才会自动执行，本次沙箱是历史初始化产物，未走到这一步（已用环境热修复方式绕过，验证充分）。

