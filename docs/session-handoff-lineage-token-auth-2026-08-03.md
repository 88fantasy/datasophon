# Session Handoff — Gravitino 血缘/Spark catalog 联邦静态 Token 认证改造（2026-08-03）

> 给下一个 Claude Code session 的交接文档。目标：不用重新读完整段对话就能接着干。
> 本文只写**这次 session 做了什么、现在什么状态、下一步做什么**——不复述已经写进仓库的
> 决策内容，全部用路径引用。用户在本 session 结尾去睡觉了，全程无人值守完成实现 + 沙箱
> 真实验证；过程中发现并修复了一个真实 bug（见 §3）。

## 仓库、分支与仓库外改动

- **datasophon**：`/Users/pro/IdeaProjects/datasophon`，分支 `feat/data-lineage-l1`，**全部改动未 commit、未 push**（`git status` 能看到 7 个文件被改）。
- **gravitino fork**：`/Users/pro/IdeaProjects/gravitino`，分支 `feat/lineage`，**全部改动未 commit**（`git status` 能看到 4 处改动：3 个文件修改 + 1 个新目录）。
- **沙箱**：ddh-01（`192.168.10.131`，Datasophon）+ ddh-02（`192.168.10.132`，Gravitino + 手工 Spark）已实机改配置、重启、跑过真实 Spark 作业。**这是真实环境变更，不是本地实验**。

## 0. 背景：为什么会做这次改动

`/Users/pro/.claude/plans/memoized-squishing-hartmanis.md` 是本 session 完整的审查报告 + 实施方案，**权威来源，优先读它**。摘要：

1. 对 PR #37（`feat/data-lineage-l1` → `main`，血缘 Gravitino 迁移）做代码审查，找到 3 个高危 + 4 个中危 + 3 个低危问题（§1-§4）。
2. 核查沙箱 `deploy/deployment-standalone-doris.md`，发现沙箱当时处于**完全无认证**状态（§5.5），且文档 §7.16 记录的"oauth+basic 双认证器"方案**从未落地**。
3. 讨论 Spark 能否换成 oauth 认证以避免 Gravitino 账密暴露，确认 `spark.sql.gravitino.authType` 支持 `token`，但 spark-connector 没实现——决定给 Gravitino fork 加一个 `authType=token` 分支，用同一枚静态 JWT 统一 catalog 联邦认证与 OpenLineage 血缘上报，替换掉原来的 `oauth,basic` 双认证器方案（§9 是最终实施方案）。
4. 用户批准方案后（ExitPlanMode），指示"完整执行验证后编写交接文档，我去睡觉了"——本文档就是这个交付物。

## 1. 代码改动清单

### 1.1 gravitino fork（`/Users/pro/IdeaProjects/gravitino`）

|                                          文件                                           |                                                               改动                                                                |
|---------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| `catalogs/catalog-common/src/main/java/org/apache/gravitino/auth/AuthProperties.java` | 新增 `TOKEN_AUTH_TYPE="token"`、`GRAVITINO_TOKEN_VALUE="token.value"`、`isToken(String)`                                            |
| `spark-connector/spark-common/.../GravitinoSparkConfig.java`                          | 新增 `GRAVITINO_TOKEN_VALUE = GRAVITINO_PREFIX + AuthProperties.GRAVITINO_TOKEN_VALUE`                                            |
| `spark-connector/spark-common/.../plugin/GravitinoDriverPlugin.java`                  | `createGravitinoClient()` 在 `isBasic`/`isOAuth2` 之间插入 `isToken` 分支，读 `spark.sql.gravitino.token.value` 构造 `StaticTokenProvider` |
| `spark-connector/spark-common/.../auth/StaticTokenProvider.java`（**新文件**）             | `extends CustomTokenProvider`，`getCustomTokenInfo()` 返回静态 token；`Builder` 复用 `CustomTokenProviderBuilder`                       |

**未 commit**。改完之后跑过：
- `./gradlew :spark-connector:spark-common:check -x test -x integrationTest`（编译 + Spotless，PASS）
- `./gradlew :spark-connector:spark-runtime-3.5:build -x test -x integrationTest`（打出最终 shaded jar，PASS）

### 1.2 datasophon（`/Users/pro/IdeaProjects/datasophon`）

|                                      文件                                       |                                                                                                                            改动                                                                                                                            |
|-------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `package/raw/meta/datacluster-physical/GRAVITINO/service_ddl.json`            | `gravitino.authenticators` 默认值 `oauth,basic` → `oauth`；删除 `gravitino.authorization.serviceAdmins`/`gravitino.server.rest.extensionPackages`/`gravitinoIdpServiceAdminPassword` 三个字段及其 `includeParams` 引用；`defaultSignKey` 的 description 补充说明它同时给 Spark 用 |
| `package/raw/meta/datacluster-physical/GRAVITINO/templates/gravitino-env.ftl` | 删除 `export GRAVITINO_INITIAL_ADMIN_PASSWORD=...` 行及注释                                                                                                                                                                                                    |
| `package/raw/meta/datacluster-physical/SPARK3/service_ddl.json`               | 新增 `gravitinoLineageToken` 字段（installer 手填）；`spark.sql.gravitino.basic.username/.password` 两行替换为 `authType=token` + `token.value=${SPARK3.gravitinoLineageToken}`；`spark.openlineage.transport.auth.apiKey` 从空串改为 `${SPARK3.gravitinoLineageToken}`      |
| `deploy/deployment-standalone-doris.md`                                       | §7.16 整体重写：标题加"S3 复审"、说明为什么放弃 `basic` 认证器、配置字段表从 4 项收窄到 2 项、升级步骤同步调整、新增"已知限制"段落（JWT 无 `exp`、H3 多集群未解）                                                                                                                                                    |

**未 commit**。改完之后跑过：

```bash
JAVA_HOME=$JH21 ./mvnw -pl datasophon-common,datasophon-grpc-api,datasophon-ui-v2,datasophon-api \
  -Dskip.installnodenpm -Dskip.npm \
  -Dtest='LineageV2ControllerTest,GravitinoLineageClientTest,GravitinoLineageEndpointResolverTest,GravitinoDdlLoadTest,Spark3DdlLoadTest' \
  -DfailIfNoTests=false -s ~/.m2/setting.xml test
```

15 个测试全绿。**但这两个 DDL 测试（`GravitinoDdlLoadTest`/`Spark3DdlLoadTest`）不覆盖本次改动的 `authType=token`/`gravitinoLineageToken` 自引用**——它们测的是 lineage storage 和 OpenLineage listener 配置，跟本次改动的字段无关。真正验证走的是沙箱手工搭建（见 §2），不是靠 Datasophon DDL 渲染管线（原因见下）。

## 2. 沙箱验证做了什么（真实环境，已改动）

### 2.1 关键前提：SPARK3 在这个沙箱里不是 Datasophon 托管服务

ddh-02 上的 Spark 是 `/data/spark-sample/spark-3.5.8-bin-hadoop3`，手工装的，供 §7.14/§7.15 的真实事件测试用。**这意味着 SPARK3 DDL 里 `${SPARK3.gravitinoLineageToken}` 这种同服务自引用语法，本次验证没有走 Datasophon 的真实配置渲染管线**——只做了 JSON 结构测试（§1.2 那 15 个测试），没有实机验证"占位符替换 + FreeMarker 渲染"这条链路。下一个 session 如果要补这一环，需要先在这个集群装一个真正的 SPARK3 服务实例。

### 2.2 意外发现：沙箱已有未记录的旧方案痕迹

进 ddh-02 后发现 Gravitino 已经在跑 `authenticators=oauth,basic`（旧的 S2 方案，`deploy/deployment-standalone-doris.md` §7.16 原文档记的是"尚未在现场执行"，但实际已经有人做了），目录里有 `gravitino-1.3.1-SNAPSHOT-bin-new.bak-verify-fix-20260803212452` 等今天下午的痕迹，SQL 脚本里有 `codex_ol_*` 命名（`/data/spark-sample/gravitino-e2e-20260731/`）。**这不是本 session 做的**，本 session 的任务清单从头到尾都是从零开始。判断是另一个并行的 Codex/人工 session 按旧方案（`oauth,basic`）做的，且没同步更新文档。已经在改配置前完整备份（见 §2.3），没有破坏性覆盖。

### 2.3 已做的备份（回滚用）

|                    位置                     |                                           备份路径                                            |
|-------------------------------------------|-------------------------------------------------------------------------------------------|
| ddh-01 `datasophon-manager-3.0-SNAPSHOT`  | `/data/datasophon-api/datasophon-manager-3.0-SNAPSHOT.bak-token-auth-20260803224500`      |
| ddh-02 `gravitino-1.3.1-SNAPSHOT-bin-new` | `/data/install_datasophon/gravitino-1.3.1-SNAPSHOT-bin-new.bak-token-auth-20260803224500` |

回滚步骤：停止对应进程 → 用上面的备份目录整体替换回原路径 → 重启。**旧的 `oauth,basic` 配置（含旧 `defaultSignKey`）保留在这份备份里**，如果要恢复到"旧 S2 方案"而不是"完全无认证"，直接用这份备份即可。

### 2.4 实际执行的变更

1. 本地生成全新 HMAC 签名密钥（32 字节随机数）+ 一枚 HS256 JWT（`sub=datasophon-lineage-proxy`，`aud=GravitinoServer`，**没有 `exp`**），文件在 `/private/tmp/claude-502/-Users-pro-IdeaProjects-datasophon/465227d9-ed82-42a6-b42e-cf23af7bca41/scratchpad/new_signkey.b64` 和 `new_jwt.txt`（**这个 scratchpad 目录是会话临时目录，session 结束后可能被清理，下次要用得重新生成一枚**）。
2. ddh-02：`gravitino.conf` 的 `authenticators` 从 `oauth,basic` 改成 `oauth`，`defaultSignKey` 换成新密钥，删掉 `serviceAdmins`/`extensionPackages` 两行；`gravitino-env.sh` 删掉 `GRAVITINO_INITIAL_ADMIN_PASSWORD` 行和相关注释；重启 Gravitino。
3. ddh-01：`api.local.properties` 的 `datasophon.lineage.proxy.auth-token` 换成新 JWT；重启 `datasophon-api`。
4. 用新 jar（含 `authType=token` 支持）在 ddh-02 手工跑真实 `spark-sql` 作业，替代原本该由 SPARK3 DDL 渲染的配置。

### 2.5 验证结果（有真实证据，不是猜测）

**服务端 oauth 认证器**（curl 直连测试）：
- 匿名 GET `/api/version`、GET `/api/lineage/tables`、POST `/api/lineage` 全部 **401**（覆盖了原审查报告 §5.5 提到的"要测写入端点，不能只测 GET"）。
- 合法 JWT 全部 **200**，包括建 schema（POST，写操作）——`creator` 字段正确显示为 JWT 的 `sub` 声明 `datasophon-lineage-proxy`，**证明 oauth 认证后的 principal 不需要额外 RBAC/serviceAdmin 授权就能写**，回答了原计划 §9.2 里悬而未决的问题。

**Datasophon L3 代理端到端**：登录 Datasophon → `GET /ddh/api/v2/lineage/overview?clusterId=1` 返回 `200`，真实数据（`generation=27`，历史 9 节点 5 边），证明新 token 全链路生效，旧血缘数据完整保留。

**Spark catalog 联邦（`authType=token`，本次改动的核心目标）**：
- 第一次真实跑遇到 `[SCHEMA_NOT_FOUND]`——`GravitinoDriverPlugin.init()` 是 catch-all 静默失败的，日志里翻出真实异常是 `UnauthorizedException: The provided credentials did not support`。
- **诊断出一个真实 bug（见 §3），修复后重新验证：`DESCRIBE TABLE EXTENDED` 显示真实 Paimon 表 `datasophon_verify.paimon_fs.lineage_probe_tokenauth.tokenauth_ctas_20260803` 建表成功，`SparkContext is stopping with exitCode 0`**，服务端日志确认 `Current user: datasophon-lineage-proxy`（来自 JWT 的 `sub`）。这是本次改动第一次在真实 Spark 会话里跑通 `authType=token`。

**OpenLineage 血缘上报（独立路径，未完全解决，见 §4）**：同一次作业里，POST `/api/lineage` 返回 **404**（不是预期的 401/200）。已排除"apiKey 值没传对"（catalog federation 那条路径证明了同一个 bash 变量传递没问题）和"endpoint 拼接错误"（显式设 `spark.openlineage.transport.endpoint=` 为空仍是 404）两种假设，根因未定。**这个环节这次没跑通，留给下一个 session**。

## 3. 真实 bug：`StaticTokenProvider` 没有设置 `schemeName`

**这是本次实现里我自己代码的 bug，被真实测试抓到的**，值得记录方法论：

`CustomTokenProviderBuilder.build()`（父类）校验 `schemeName` 非空后调用 `internalBuild()`，但**不会**自动把 `schemeName` 传给构造出来的实例——这是子类 `internalBuild()` 自己的责任。我最初的实现：

```java
// 错的：private StaticTokenProvider(String token) { this.token = token; }
// internalBuild() 里: return new StaticTokenProvider(token);
```

从没把 `schemeName`（builder 上 `.withSchemeName("Bearer")` 设的值）转移到实例的 `this.schemeName`（继承自 `CustomTokenProvider`）上，导致它是 `null`。`getTokenData()` 返回 `schemeName + " " + token`，`null` 字符串拼接后变成字面量 **`"null <token>"`**，不是 `"Bearer <token>"`。这解释了为什么无论 token 内容对不对，服务端都报同一个"凭据不受支持"——它压根没识别出这是个 Bearer 请求。

**诊断方法**：直接 curl 测同一个 endpoint 用同一个 token 是成功的（排除了"密钥不对"）；用 `LOG.warn` 打印 `authType` 的实际读取值确认配置读取正常（排除了"配置没传到"）；用一个明显错误的 garbage token 做判别性实验——如果代码真的走到了 `StaticTokenProvider`，garbage token 应该报"JWT parse error"而不是"did not support"；实测发现即使 garbage token 也是同一个"did not support"错误，才把怀疑范围收窄到"header 根本没被识别成 Bearer scheme"，进而查到 `schemeName` 未传递。

**修复**：构造函数加 `schemeName` 参数，`internalBuild()` 传入 `this.schemeName`（builder 继承的字段）。修复后判别性实验的错误从"did not support"变成"JWT parse error"，证明修复生效；真实 JWT 测试建表成功，最终确认。

## 4. 明确未完成 / 未验证的部分（下一步从这里开始）

1. ~~**OpenLineage 血缘上报 404 未解决**~~ **2026-08-04 已解决**，见 §7。
2. **SPARK3 DDL 的自引用占位符从未走真实 Datasophon 渲染管线验证**（§2.1）。需要在这个集群装一个真正的 SPARK3 服务实例，或者另建一个更小的验证环境，确认 `${SPARK3.gravitinoLineageToken}` 真的被正确渲染进 `spark-defaults.conf`。
3. **原审查报告 H3（Datasophon 代理 token 全局单例 vs 每集群独立 `defaultSignKey`）完全没碰**。本次改动的范围明确是 H2 + H1 第 4 点，H3 需要单独立项（见计划 §2 H3 的两条修复路径）。
4. **`gravitino-spark-connector-runtime` jar 没有正式上传 Nexus**，只是 scp 到了 ddh-02 的 `/root/token-auth-material/` 目录直接用于验证。`package/manifest.json` 里的 GRAVITINO 条目也没更新说明这次 fork 改动。如果要让这次改动在"正式走 Datasophon 安装流程"的场景下生效，需要补这一步。
5. **JWT 没有 `exp`（永不过期）**，铸造脚本和轮换流程都还是计划里标注的"已知限制，未在本次修复范围内"。
6. **两处密钥意外打印进了对话记录**（`grep -n` 查现有配置时不小心带出了明文），具体是旧的 `defaultSignKey` 和旧的 `GRAVITINO_INITIAL_ADMIN_PASSWORD`。**这两个值已经在本次改动里被替换/删除，不再是活跃凭据**，但如果这个对话 transcript 会被存档或分享，这一点需要留意。
7. **datasophon 和 gravitino fork 里的改动都还没 commit**。是否要 commit、commit 到哪个分支/如何拆分提交，留给用户决定——本次任务范围是"实现+验证+交接"，不包含提交决策。

## 5. 关键路径速查

- 审查报告 + 实施方案：`/Users/pro/.claude/plans/memoized-squishing-hartmanis.md`
- gravitino fork 新文件：`/Users/pro/IdeaProjects/gravitino/spark-connector/spark-common/src/main/java/org/apache/gravitino/spark/connector/auth/StaticTokenProvider.java`
- 沙箱备份：见 §2.3 表格
- 本次会话临时文件（密钥/JWT/测试脚本/SQL）：`/private/tmp/claude-502/-Users-pro-IdeaProjects-datasophon/465227d9-ed82-42a6-b42e-cf23af7bca41/scratchpad/`（session 隔离目录，可能被清理）
- ddh-02 上的验证材料：`/root/token-auth-material/`（jar、JWT、脚本，人工现场留存）
- ddh-02 上的探针 SQL 与日志：`/data/spark-sample/gravitino-e2e-20260731/probe_tokenauth_20260803.sql`、`run_tokenauth_20260803.log`

## 6. 关键环境命令

```bash
export JH21=/Users/pro/Library/Java/JavaVirtualMachines/graalvm-jdk-21.0.7/Contents/Home

# datasophon 测试
JAVA_HOME=$JH21 ./mvnw -pl datasophon-common,datasophon-grpc-api,datasophon-ui-v2,datasophon-api \
  -Dskip.installnodenpm -Dskip.npm \
  -Dtest='LineageV2ControllerTest,GravitinoLineageClientTest,GravitinoLineageEndpointResolverTest,GravitinoDdlLoadTest,Spark3DdlLoadTest' \
  -DfailIfNoTests=false -s ~/.m2/setting.xml test

# gravitino fork 编译 + 打包
cd /Users/pro/IdeaProjects/gravitino
./gradlew :spark-connector:spark-common:check -x test -x integrationTest
./gradlew :spark-connector:spark-runtime-3.5:build -x test -x integrationTest
# 产物: spark-connector/v3.5/spark-runtime/build/libs/gravitino-spark-connector-runtime-3.5_2.12-1.3.1-SNAPSHOT.jar

# 沙箱 SSH（两把不同的密钥，ddh-01 用第一把，ddh-02 用第二把）
ssh -i ~/.ssh/id_ed25519_datasophon root@192.168.10.131
ssh -i ~/.ssh/id_rsa root@192.168.10.132
```

## 7. 2026-08-04 追加：OpenLineage 404 根因定位 + 修复 + 生产 DDL 同步修复

**根因**（反编译 `openlineage-spark_2.12-1.29.0.jar` 里的 `HttpTransport.getUri()` 字节码确认，非猜测）：

```
url = httpConfig.getUrl()                         // 即 spark.openlineage.transport.url
if (url.getPath() 非空 && endpoint 非空) throw "不能同时传 url 和 endpoint"
if (endpoint 非空) path = endpoint
else path = "/api/v1/lineage"                     // 硬编码默认值
builder.setPath(path)                             // URIBuilder.setPath() 整体替换,不是拼接
```

只要没显式设置 `spark.openlineage.transport.endpoint`，无论 `transport.url` 里写没写路径，最终路径永远
被替换成硬编码的 `/api/v1/lineage`——`url` 里带的路径段会被直接丢弃，不参与拼接。§2.5 记的
"显式设 `endpoint=` 为空仍是 404"是伪阴性：`StringUtils.isBlank("")` 与 `isBlank(null)` 同为
`true`，那次实验根本没有真正测试到"设置非空 `endpoint`"这个分支。

真实请求此前打到了 `http://<gravitino>:8090/api/v1/lineage`，而 Gravitino 的真实血缘端点是
`/api/lineage`（`gravitino.lineage.source=http` 决定），多出的 `/v1` 导致 404。

**修复**：`transport.url` 只放 base origin（scheme+host+port），路径单独由 `transport.endpoint` 声明。

```
spark.openlineage.transport.url = http://<gravitino-host>:<port>
spark.openlineage.transport.endpoint = /api/lineage
```

**沙箱端到端复验（ddh-02，2026-08-04 06:17，脚本
`/root/token-auth-material/run_probe_fixed.sh`）**：`authType=token` catalog 联邦 + 修正后的
OpenLineage 配置一次跑通，`run_tokenauth_fixed_20260804.log` 里 30 次 `EventEmitter: Emitting
lineage completed successfully`、零个 `HttpTransportResponseException`/404；Gravitino 侧
`gravitino_lineage.log` 用 `grep -c 019fc9b4`（本次 run 前缀）确认收到全部 30 条事件，CTAS 事件
带完整列级血缘（`columnLineage.fields.*.transformations=[DIRECT/IDENTITY]`），`namespace=
datasophon_verify`（metalake 名）、`name=paimon_fs.lineage_probe_tokenauth.<table>`——与已确认的
Paimon 命名法（见 memory `project-data-lineage-platform`）完全一致。

**复现路上的一个弯路（记录以防下次重犯）**：第一次重跑时套用了 `spark.plugins=org.apache.gravitino.
spark.connector.plugin.GravitinoDriverPlugin`，报 `GravitinoDriverPlugin is not a subclass of
SparkPlugin`——这不是环境问题，是笔误：`GravitinoDriverPlugin` 实现的是 `DriverPlugin` 接口，真正
要配给 `spark.plugins` 的入口类是 `GravitinoSparkPlugin`（`implements SparkPlugin`，内部
`driverPlugin()` 方法返回 `GravitinoDriverPlugin` 实例）。核对 8 月 3 日成功日志里的
`DriverPluginContainer: Initialized driver component for plugin ...GravitinoSparkPlugin` 一行
才发现这个笔误。另外沙箱在两次会话之间被别的会话装了真实 Hadoop，全局 `HADOOP_HOME`/
`HADOOP_CONF_DIR`、`SPARK_HOME=/data/install_datasophon/spark3`（该目录尚不存在）已生效，探针脚本
必须在自己的 shell 内部覆盖这三个变量，不能依赖全局环境，也不要去改全局环境（不是本次任务范围，
且该沙箱状态是另一个并行会话在准备真正的 SPARK3 服务安装）。

**生产同步修复**：`package/raw/meta/datacluster-physical/SPARK3/service_ddl.json` 的
`custom.spark.defaults.conf` 原样复刻了这个 bug（`transport.url` 直接拼了 `/api/lineage`、从未设置
`transport.endpoint`）——**这不是待验证风险，是部署后必然 100% 复现的确定性缺陷**，已同步修复：
`transport.url` 改回 base origin，新增 `transport.endpoint=/api/lineage`。`Spark3DdlLoadTest` 里
原本断言"`transport.url` 必须等于完整 `/api/lineage` URL"的用例已随之更新为断言 base origin +
`endpoint` 分离，15/15 测试全绿（含 `GravitinoDdlLoadTest`/`LineageV2ControllerTest`/
`GravitinoLineageClientTest`/`GravitinoLineageEndpointResolverTest`）。

**仍未做**：这个修复目前只改了 `service_ddl.json`（结构测试覆盖），**依然没有解决 §4 第 2 条"SPARK3
自引用占位符从未走真实 Datasophon 渲染管线"**——本次复验用的是手工拼接的 `spark-sql` 命令行参数，
不是通过 Datasophon 渲染 `spark-defaults.conf` 产出的。
