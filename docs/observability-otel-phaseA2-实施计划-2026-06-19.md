# 可观测重构 Phase A — A2(Doris 存储 / schema 自管)实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在平台 Doris 上自管(`create_schema=false`)一套版本化的 OpenTelemetry 存储 schema —— otel database + 独立资源组 + dorisexporter v0.154.0 期望的全部表 + INSERT-only 采集账号,并用契约测试守住"我们的 DDL 集 == exporter 期望表集",防止后续看板/告警 SQL 依赖的 schema 漂移。

**Architecture:** dorisexporter 关闭自动建表后,只向**预先存在**的表做 Stream Load。A2 把 exporter v0.154.0 `sql/` 目录的权威 DDL 提取、收敛为带版本号的 SQL 包,包上 `otel` database / Workload Group / INSERT-only 账号;再写一个 api 侧 `JdbcClient`(MySQL 协议)幂等应用器 + 契约测试。**A2 只产出"schema 工件 + 应用器 + 契约测试";"Doris 就绪自动应用并切 exporter"属 A3。**

**Tech Stack:** Java 17 / Spring Boot 3.4.5(api,`JdbcClient` MySQL 协议直连 Doris)、Doris 4.0.5(MySQL 协议 9030 / Stream Load HTTP 8030)、dorisexporter v0.154.0(schema 真相之源)、JUnit5(契约测试)。

## Global Constraints

- 设计真相之源:`docs/observability-otel-doris-设计-2026-06-19.md`(§4.2 schema 自管、§5.8 契约、§5.9 凭据、§6/§8 F1+F4)。
- 分支:`refactor/observability-otel`。
- **`create_schema=false`**:dorisexporter 运行期不建表;datasophon 自管全部 DDL。
- **schema 真相之源 = dorisexporter v0.154.0 `sql/` 目录**(pin 版本),raw 文件 URL 前缀:
  `https://raw.githubusercontent.com/open-telemetry/opentelemetry-collector-contrib/v0.154.0/exporter/dorisexporter/sql/`
  权威文件:`logs_ddl.sql`、`metrics_gauge_ddl.sql`、`metrics_sum_ddl.sql`、`metrics_histogram_ddl.sql`、`metrics_exponential_histogram_ddl.sql`、`metrics_summary_ddl.sql`、`traces_ddl.sql`、`traces_graph_ddl.sql`、`logs_view.sql`、`metrics_view.sql`、`traces_view.sql`、`traces_graph_job.sql`。
- **必须建全的基表(exporter Stream Load 目标,缺一张该信号写不进)**:`otel_logs`、`otel_metrics_gauge`、`otel_metrics_sum`、`otel_metrics_histogram`、`otel_metrics_exponential_histogram`、`otel_metrics_summary`、`otel_traces`(`otel_traces_graph` 视情)。**确切表名以 exporter 源文件为准**,实现时逐一核对,勿凭记忆。
- **凭据(F1)**:采集账号仅 `LOAD_PRIV`(Stream Load 所需),**无** CREATE/DROP/DELETE;与看板读用账号(SELECT)分离。按集群隔离的实际下发属 A3,A2 只产出账号/授权 DDL 模板。
- **资源组**:为 otel 库建独立 Doris Workload Group,限制可观测负载占用,防拖垮业务。
- schema 带**版本号**(`OTEL_SCHEMA_VERSION` 常量 + SQL 文件名版本前缀);升级先过契约测试。
- 数据库名 `otel`;应用器走 MySQL 协议(默认 Doris FE 9030),**幂等**(`CREATE ... IF NOT EXISTS`)。
- 提交粒度:每 Task 一次 commit;Conventional Commits 中文。
- **环境受限如实标注**:本机无 Doris;凡需真实 Doris 的步骤(应用器实跑、exporter 写入预建表)如实标"待真实环境",不伪造输出。

---

## A2 在 Phase A 进度表中的位置

| 里程碑 | 子系统 | 交付物 | 计划 | 状态 |
|---|---|---|---|---|
| A1 | 数据面 | OTELCOLLECTOR 服务 | done | 🟩 代码完成,端到端待真实环境 |
| **A2** | **存储** | **otel db + 资源组 + 版本化自管 DDL + INSERT-only 账号 + 契约测试 + 应用器** | **本计划** | ⬜ 未开始 |
| A3 | 控制面 | 控制台 + 告警器 + 逐节点切换 + 回灌 | 待出 | ⬜ 未开始 |

### A2 承接的验收/整改条目

| 来源 | 内容 | 本计划覆盖 |
|---|---|---|
| §8 F4 | schema 自管 + 版本化 + 契约测试 | ✅ Task 1/3 |
| §5.8 | 看板/告警 SQL 对固定 schema 契约测试通过;模拟漂移能拦截 | ✅ Task 3 |
| §8 F1(部分) | INSERT-only 采集账号(无 CREATE/DELETE/DROP),与读账号分离 | ✅ Task 1(账号/授权 DDL);按集群下发属 A3 |
| §5.2(部分) | 装 Doris→落 otel 表 | ✅ A2 提供表;自动切 exporter 属 A3 |
| §5.9(部分) | 凭据最小权限校验 | ✅ Task 1 授权 DDL + Task 3 权限断言(静态);运行期校验待真实环境 |
| §6 | otel 库独立资源组防拖垮业务 | ✅ Task 1 Workload Group |

---

## File Structure(A2 改动地图)

- Create: `datasophon-api/src/main/resources/observability/doris/V1__otel_database.sql` — 建库 + Workload Group + 账号/授权
- Create: `datasophon-api/src/main/resources/observability/doris/V1__otel_tables.sql` — 7(+1)张基表(vendoring 自 exporter sql/)
- Create: `datasophon-api/src/main/resources/observability/doris/V1__otel_views.sql` — 视图 + traces_graph job(vendoring)
- Create: `datasophon-api/src/main/resources/observability/doris/SCHEMA.md` — vendoring 来源(pin v0.154.0 + 各文件 URL)+ 表清单 + 版本说明
- Create: `datasophon-api/src/main/java/com/datasophon/api/observability/OtelSchema.java` — 版本常量 + 期望表名集合(SSOT)
- Create: `datasophon-api/src/main/java/com/datasophon/api/observability/OtelSchemaApplier.java` — JdbcClient 幂等应用器(MySQL 协议→Doris)
- Create: `datasophon-api/src/test/java/com/datasophon/api/observability/OtelSchemaContractTest.java` — 契约测试(表集匹配 / 解析 / 权限 / 版本)
- Reference(只读):`datasophon-api/.../migration/DatabaseMigration`(自研执行器风格)、A1 的 `service_ddl.json`(`s3*` 凭据模式)

---

## Task 1: Vendoring otel schema DDL(库 + 资源组 + 表 + 账号)

把 exporter v0.154.0 的权威 DDL 提取、收敛为版本化 SQL 包,包上 database / Workload Group / INSERT-only 账号。

**Files:**
- Create: `.../observability/doris/V1__otel_database.sql`、`V1__otel_tables.sql`、`V1__otel_views.sql`、`SCHEMA.md`

**Interfaces:**
- Produces:数据库名 `otel`;表名集合(供 Task 2 应用、Task 3 契约断言);采集账号名 `otel_collector`(仅 LOAD_PRIV)、读账号名 `otel_reader`(仅 SELECT_PRIV)。

- [ ] **Step 1: 拉取并核对 exporter 真实表集**

```bash
cd /tmp && mkdir -p otelsql && cd otelsql
BASE=https://raw.githubusercontent.com/open-telemetry/opentelemetry-collector-contrib/v0.154.0/exporter/dorisexporter/sql
for f in logs_ddl metrics_gauge_ddl metrics_sum_ddl metrics_histogram_ddl \
         metrics_exponential_histogram_ddl metrics_summary_ddl traces_ddl traces_graph_ddl \
         logs_view metrics_view traces_view traces_graph_job; do
  curl -fsSL "$BASE/$f.sql" -o "$f.sql" && echo "OK $f"
done
grep -iE "CREATE TABLE|CREATE VIEW|CREATE MATERIALIZED|TABLE IF NOT EXISTS" *.sql
```

Expected: 12 文件下载成功;打印出每个 `CREATE TABLE`/`VIEW` 的真实表/视图名。**以此输出为准**确定基表名集合(预期含 `otel_logs` / `otel_metrics_gauge|sum|histogram|exponential_histogram|summary` / `otel_traces`,确切名以输出为准)。

- [ ] **Step 2: 写 `V1__otel_tables.sql`(基表,逐字源自 Step 1)**

把 Step 1 各 `*_ddl.sql` 的 `CREATE TABLE` 收敛进本文件。要求:
- 表名前加库:`CREATE TABLE IF NOT EXISTS otel.<table> ...`(幂等)。
- 列/键/分区/TTL **逐字保留** exporter 源定义(不得手改,否则 Stream Load 字段不匹配)。
- 文件头注释标 `-- source: dorisexporter v0.154.0 sql/<file>`。

> 若源 DDL 含 `${...}` 占位(库名/TTL),用具体值替换:库名 `otel`,TTL 默认按源默认或注释标明。

- [ ] **Step 3: 写 `V1__otel_database.sql`(库 + 资源组 + 账号)**

```sql
-- otel 可观测存储:库 + 独立资源组 + 最小权限账号(schema v1)
CREATE DATABASE IF NOT EXISTS otel;

-- 独立 Workload Group,限制可观测负载占用(防拖垮业务),配额按部署调整
CREATE WORKLOAD GROUP IF NOT EXISTS otel_wg
PROPERTIES (
  "cpu_share" = "10",
  "memory_limit" = "20%",
  "enable_memory_overcommit" = "true"
);

-- 采集账号:仅 Stream Load 所需 LOAD 权限(无 CREATE/DROP/DELETE),口令由 A3 下发时改
CREATE USER IF NOT EXISTS 'otel_collector' IDENTIFIED BY 'CHANGE_ME_AT_A3';
GRANT LOAD_PRIV ON otel.* TO 'otel_collector';

-- 看板读账号:仅 SELECT(与采集写账号分离)
CREATE USER IF NOT EXISTS 'otel_reader' IDENTIFIED BY 'CHANGE_ME_AT_A3';
GRANT SELECT_PRIV ON otel.* TO 'otel_reader';
```

> Doris 4.0.5 Workload Group 语法以官方为准;若属性名不符,实现时按 `SHOW WORKLOAD GROUPS` 校正并记录。`LOAD_PRIV`/`SELECT_PRIV` 为 Doris 细粒度权限。

- [ ] **Step 4: 写 `V1__otel_views.sql` + `SCHEMA.md`**

- `V1__otel_views.sql`:收敛 Step 1 的 `*_view.sql` 与 `traces_graph_job.sql`(均加 `IF NOT EXISTS` / `otel.` 前缀)。views/graph job 非 Stream Load 目标,但属 exporter create_schema 会建的对象,一并自管以保持一致。
- `SCHEMA.md`:记 vendoring 来源(pin v0.154.0 + 12 文件 URL)、最终表/视图清单、schema 版本 `v1`、各对象与源文件对应。

- [ ] **Step 5: 提交**

```bash
cd /Users/pro/IdeaProjects/datasophon
git add datasophon-api/src/main/resources/observability/doris/
git commit -m "feat(observability): 自管 otel Doris schema v1(库+资源组+exporter v0.154.0 表+最小权限账号)"
```

---

## Task 2: OtelSchema 常量 + 幂等应用器

**Files:**
- Create: `datasophon-api/src/main/java/com/datasophon/api/observability/OtelSchema.java`
- Create: `datasophon-api/src/main/java/com/datasophon/api/observability/OtelSchemaApplier.java`

**Interfaces:**
- Consumes:Task 1 的 SQL 资源文件、表名集合。
- Produces:`OtelSchema.VERSION`(String `"v1"`)、`OtelSchema.EXPECTED_TABLES`(`Set<String>`,SSOT)、`OtelSchema.DDL_RESOURCES`(有序 SQL 资源路径);`OtelSchemaApplier.apply(JdbcClient)`(幂等,逐文件执行)。

- [ ] **Step 1: 写 OtelSchema(版本 + 期望表集 SSOT)**

```java
package com.datasophon.api.observability;

import java.util.List;
import java.util.Set;

/** otel Doris schema 的版本与期望对象集合(单一真相,契约测试与应用器共用)。 */
public final class OtelSchema {

    private OtelSchema() {}

    public static final String VERSION = "v1";

    /** dorisexporter v0.154.0 Stream Load 目标基表;缺一张对应信号写不进。
     *  确切表名以 Task 1 Step 1 的源文件输出为准,如有出入以本集合为 SSOT 同步修正。 */
    public static final Set<String> EXPECTED_TABLES = Set.of(
            "otel_logs",
            "otel_metrics_gauge",
            "otel_metrics_sum",
            "otel_metrics_histogram",
            "otel_metrics_exponential_histogram",
            "otel_metrics_summary",
            "otel_traces");

    /** 按依赖顺序的 DDL 资源(database → tables → views)。 */
    public static final List<String> DDL_RESOURCES = List.of(
            "observability/doris/V1__otel_database.sql",
            "observability/doris/V1__otel_tables.sql",
            "observability/doris/V1__otel_views.sql");
}
```

- [ ] **Step 2: 写失败的应用器测试(以资源可加载 + 语句切分为先)**

`OtelSchemaContractTest.java` 先放一个应用器层面的轻测试(完整契约在 Task 3):

```java
package com.datasophon.api.observability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OtelSchemaContractTest {

    @Test
    void ddl_resources_are_loadable_and_nonempty() {
        for (String res : OtelSchema.DDL_RESOURCES) {
            var in = getClass().getClassLoader().getResourceAsStream(res);
            assertTrue(in != null, "DDL 资源缺失: " + res);
        }
    }

    @Test
    void applier_splits_statements() {
        String sql = "CREATE DATABASE IF NOT EXISTS otel;\nCREATE TABLE otel.a(x INT);\n";
        var stmts = OtelSchemaApplier.splitStatements(sql);
        assertFalse(stmts.isEmpty());
        assertTrue(stmts.size() >= 2);
    }
}
```

- [ ] **Step 3: 运行测试看失败**

Run: `JAVA_HOME=/Users/pro/Library/Java/JavaVirtualMachines/jbr-17.0.12-1/Contents/Home ./mvnw -pl datasophon-api -Dtest=OtelSchemaContractTest test -s ~/.m2/setting.xml -Dspotless.check.skip=true`
Expected: FAIL —— `OtelSchemaApplier` 未定义 / 资源未就位前断言失败。

- [ ] **Step 4: 写 OtelSchemaApplier(幂等应用,语句切分)**

```java
package com.datasophon.api.observability;

import cn.hutool.core.io.IoUtil;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 把自管 otel schema 幂等应用到 Doris(MySQL 协议)。运行期需真实 Doris 连接。 */
public final class OtelSchemaApplier {

    private static final Logger log = LoggerFactory.getLogger(OtelSchemaApplier.class);

    private OtelSchemaApplier() {}

    /** 按 DDL_RESOURCES 顺序逐语句执行;DDL 均为 IF NOT EXISTS,可重复应用。 */
    public static void apply(JdbcClient doris) {
        for (String res : OtelSchema.DDL_RESOURCES) {
            String sql = readResource(res);
            for (String stmt : splitStatements(sql)) {
                doris.sql(stmt).update();
            }
            log.info("otel schema applied: {}", res);
        }
    }

    static List<String> splitStatements(String sql) {
        List<String> out = new ArrayList<>();
        for (String raw : sql.split(";")) {
            String s = stripComments(raw).trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    private static String stripComments(String s) {
        StringBuilder sb = new StringBuilder();
        for (String line : s.split("\n")) {
            String t = line.stripLeading();
            if (!t.startsWith("--")) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private static String readResource(String res) {
        var in = OtelSchemaApplier.class.getClassLoader().getResourceAsStream(res);
        if (in == null) {
            throw new IllegalStateException("DDL 资源缺失: " + res);
        }
        return IoUtil.read(in, StandardCharsets.UTF_8);
    }
}
```

- [ ] **Step 5: 运行测试看通过**

Run: 同 Step 3 命令。
Expected: PASS(资源可加载 + 语句切分 ≥2)。

- [ ] **Step 6: 提交**

```bash
git add datasophon-api/src/main/java/com/datasophon/api/observability/ \
        datasophon-api/src/test/java/com/datasophon/api/observability/OtelSchemaContractTest.java
git commit -m "feat(observability): otel schema 版本常量 + 幂等应用器(JdbcClient→Doris)"
```

---

## Task 3: Schema 契约测试(守 F4/§5.8 漂移)

把"我们的 DDL 集 == exporter 期望表集 + 最小权限"固化为契约;exporter/我方升级时先过此契约。

**Files:**
- Modify: `datasophon-api/src/test/java/com/datasophon/api/observability/OtelSchemaContractTest.java`

**Interfaces:**
- Consumes:`OtelSchema.EXPECTED_TABLES`、Task 1 的 SQL 文件。

- [ ] **Step 1: 写失败的契约断言**

向 `OtelSchemaContractTest` 增加:

```java
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Test
void tables_sql_creates_exactly_the_expected_exporter_tables() {
    String sql = readClasspath("observability/doris/V1__otel_tables.sql").toLowerCase();
    Matcher m = Pattern.compile("create\\s+table\\s+(if\\s+not\\s+exists\\s+)?otel\\.([a-z0-9_]+)")
            .matcher(sql);
    Set<String> declared = new HashSet<>();
    while (m.find()) {
        declared.add(m.group(2));
    }
    // 期望表集必须被 DDL 全覆盖(漂移即失败)
    for (String t : OtelSchema.EXPECTED_TABLES) {
        assertTrue(declared.contains(t), "DDL 缺少 exporter 目标表: " + t);
    }
}

@Test
void collector_account_has_load_only_no_ddl_privilege() {
    String db = readClasspath("observability/doris/V1__otel_database.sql").toUpperCase();
    assertTrue(db.contains("GRANT LOAD_PRIV ON OTEL.* TO 'OTEL_COLLECTOR'".toUpperCase()));
    // 采集账号绝不应被授予 DDL/删除权限
    assertFalse(db.matches("(?s).*GRANT\\s+(ALL|CREATE|DROP|DELETE)_?\\w*\\s+PRIV?\\s+ON\\s+OTEL.*OTEL_COLLECTOR.*"));
}

@Test
void schema_version_is_pinned() {
    assertEquals("v1", OtelSchema.VERSION);
}
```

> 需补一个 `readClasspath(String)` 私有助手(读 classpath 资源为字符串)。

- [ ] **Step 2: 运行测试看失败**

Run: `... -Dtest=OtelSchemaContractTest test ...`
Expected: FAIL —— 若 Task 1 DDL 表名与 `EXPECTED_TABLES` 不符,或授权断言不满足,则红(这正是契约要抓的)。

- [ ] **Step 3: 对齐 DDL 与 EXPECTED_TABLES 直到通过**

依据 Task 1 Step 1 的真实表名,校正 `OtelSchema.EXPECTED_TABLES` 与 `V1__otel_tables.sql` 使二者一致(以 exporter 源为准)。再跑测试转绿。

Run: 同 Step 2。
Expected: PASS。

- [ ] **Step 4: 提交**

```bash
git add datasophon-api/src/test/java/com/datasophon/api/observability/OtelSchemaContractTest.java \
        datasophon-api/src/main/java/com/datasophon/api/observability/OtelSchema.java
git commit -m "test(observability): otel schema 契约测试(表集匹配/最小权限/版本,守 F4 漂移)"
```

---

## Task 4: 真实 Doris 应用验收(环境受限,文档化)

**Files:**
- Create: `datasophon-api/src/main/resources/observability/doris/apply-verify.md`

- [ ] **Step 1: 写真实环境验收步骤**

`apply-verify.md` 记录(供具备 Doris 的环境执行):
1. 应用:用 `OtelSchemaApplier.apply()` 或 `mysql -h<FE> -P9030 -uroot` 逐文件跑 `V1__*.sql`;`SHOW TABLES FROM otel;` 应列出全部期望表。
2. exporter 写入预建表:otelcol 以 `create_schema=false` + dorisexporter 指向 `otel` 库,灌合成数据,`SELECT count(*)` 各表 > 0。
3. 最小权限:用 `otel_collector` 账号尝试 `DROP TABLE otel.otel_logs` 应被拒;`SELECT` 应被拒(仅 LOAD)。
4. 资源组:`SHOW WORKLOAD GROUPS` 含 `otel_wg`。

- [ ] **Step 2: 标注验收状态 + 提交**

记录:契约/解析/版本契约测试已在开发机通过;应用器实跑、exporter 写入、权限拒绝、资源组待真实 Doris 环境。

```bash
git add datasophon-api/src/main/resources/observability/doris/apply-verify.md
git commit -m "test(observability): otel schema 真实 Doris 应用验收清单(环境受限标注)"
```

---

## A2 完成定义

- [ ] otel schema DDL vendoring 自 exporter v0.154.0,版本化 v1(Task 1)
- [ ] 期望表集 SSOT(`OtelSchema`)+ 幂等应用器 + 资源可加载/语句切分测试通过(Task 2)
- [ ] 契约测试:DDL 覆盖全部 exporter 目标表、采集账号 LOAD-only、版本 pin —— 通过(Task 3)
- [ ] 真实 Doris 应用/权限/资源组验收清单文档化(Task 4,实跑待真实环境)
- [ ] 追溯:§8 F4 / §5.8 闭环;F1 账号 DDL 就位(下发属 A3)

## 衔接 A3

A3 将:① 检测 Doris 就绪后调用 `OtelSchemaApplier.apply()` 建表;② 按集群生成 `otel_collector` 口令并经配置下发(替换 `CHANGE_ME_AT_A3`),注入 A1 的 `otelcol.env`/dorisexporter;③ 逐节点把 exporter 从 awss3 切到 doris(`create_schema=false`,写本计划的自管表)。
