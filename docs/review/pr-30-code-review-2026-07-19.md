# PR #30 代码审查结果

- PR: https://github.com/88fantasy/datasophon/pull/30
- 标题: 五节点无 Hadoop Doris 集群部署 Epic：CLI 离线初始化修复 + 6 服务安装适配 + 监控看板迁移
- 分支: verify/cli-go-five-node-bootstrap vs main
- 审查范围: `git diff main...HEAD`(240 文件,+11960/-2567)
- 审查方式: `/code-review high`(3 正确性 + 3 清理类 + altitude + CLAUDE.md 合规,共 8 独立视角 → 逐条读源码验证)
- 审查时间: 2026-07-19

## 根因说明

多数正确性发现共享同一根因:commit `d46ce306`(合并 18 个服务 DDL 的 value/defaultValue 字段)把大量
`meta/datacluster-physical/*/service_ddl.json` 参数的 `"value"` 字段删除、只保留 `"defaultValue"`。
前端 `ConfigForm.tsx` 同步加了 `isEmptyValue(item.value) ? item.defaultValue : item.value` 兜底,
但后端 `ServiceConfig`(`datasophon-common/.../model/ServiceConfig.java`)和两条绕过 UI 表单、
直接从 DDL 取配置的自动化装机路径完全没有对应兜底,导致 value 为 null 时要么崩溃要么被静默写成
字面量字符串 `"null"`。

## 发现列表(按严重度排序)

### 1. ConfigureServiceHandler NPE — CONFIRMED

- **文件**: `datasophon-worker/src/main/java/com/datasophon/worker/handler/ConfigureServiceHandler.java:239`
- **问题**: `replacePlaceholder()` 对 `type=INPUT` 的参数直接 `tempVal.getClass()`,未判空。
- **触发场景**: `PhysicalClusterInitializationServiceImpl.start()` 触发 OTELCOLLECTOR 自动化装机时,
  `getServiceConfigFromDdl()` 用 fastjson2 直接反序列化 DDL parameters(无 value→defaultValue 兜底),
  `selfMetricsPort`/`hostname`/`dorisDatabase` 等参数 `getValue()==null`;下发到 worker 后
  `replacePlaceholder` 对 null 调用 `getClass()` 抛 NPE,导致 Collector 配置下发/安装崩溃。

### 2. 全局变量被写成字面量字符串 "null" — CONFIRMED

- **文件**: `datasophon-api/src/main/java/com/datasophon/api/service/impl/ServiceInstallServiceImpl.java:188`
- **问题**: `saveServiceConfig()` 用 `String.valueOf(serviceConfig.getValue())` 生成集群变量,
  value 为 null 时静默写入字符串 `"null"` 而非报错。
- **触发场景**: DORIS DDL 中 5 个 `register:true` 参数(s3AccessKey 等)同样丢失了 `"value"` 字段;
  若经 `getServiceConfigFromDdl` 这类非 UI 路径保存(如 `PhysicalProductInstallServiceImpl.java:207-209`
  的 ext-repo 装机路径,完全没有任何字段补偿),会把 "null" 写成全局变量,后续 `${...}` 占位符替换
  静默用上这个错误值,导致下游服务用错误凭证/端点启动。

### 3. DorisReadinessService 阻塞共享线程池 — CONFIRMED

- **文件**: `datasophon-api/src/main/java/com/datasophon/api/doris/DorisReadinessService.java:52`
- **问题**: `waitUntilClusterReady()` 用同步 `Thread.sleep` 轮询,最长可达 120s;调用方
  `DorisRootPasswordInitHook` 在 `DAGExecutor` 的 `@Async("masterExecutor")` 方法内同步调用它。
- **触发场景**: `masterExecutor` 是全集群共用的通用 `@Async` 线程池,一次 Doris 安装最多占住一个线程
  sleep 2 分钟,多集群/多服务并发装机时会挤占该池,拖慢其他无关 DAG 任务的调度与执行。

### 4. getStatus 逐节点同步阻塞 — PLAUSIBLE

- **文件**: `datasophon-api/src/main/java/com/datasophon/api/service/impl/PhysicalClusterInitializationServiceImpl.java:178`
- **问题**: `getStatus()` 对 hosts 列表用普通 for 循环逐个调用 `nodeStatus()`,内部包含阻塞式
  gRPC ping(`workerCommandClient.ping`)和 HTTP 自监控指标拉取(`metricsClient.fetch`),两者都在
  同一线程顺序执行。
- **触发场景**: 该接口被前端安装向导每 3 秒轮询一次;节点数增多或某节点网络慢/超时时,单次
  `getStatus` 总耗时随节点数线性增长,可能超过轮询间隔,造成状态展示滞后甚至请求堆积。

### 5. MySQL root 密码修复逻辑手工复制两份 — CONFIRMED

- **文件**: `datasophon-cli-go/internal/cli/create/mysql.go:184`(另一份 `internal/plan/mysql.go:179`)
- **问题**: 本 PR 修复的 MySQL root 密码引号转义 + `--connect-expired-password` + 错误返回值改动,
  在 `internal/cli/create/mysql.go` 和 `internal/plan/mysql.go` 两个几乎重复的 `mysqlTask` 实现里
  逐字复制了一遍。
- **影响**: `create cluster` 与 plan/apply 两条路径各自维护一份 `rootUserConf`,后续任何 MySQL
  初始化相关修复很容易只改一处,导致两条路径行为分叉;仓库此前(commit `81f16e33`)已经因同类
  重复代码踩过一次坑。

### 6. Nexus EULA 修复逻辑手工复制两份 — CONFIRMED

- **文件**: `datasophon-cli-go/internal/cli/create/registry.go:155`(另一份 `internal/plan/registry_task.go:149`)
- **问题**: 新增的 EULA disclaimer 获取/回显逻辑(`systemEula`)在两个独立 `registryTask` 实现中
  逐字复制,含相同中文注释。
- **影响**: 同上,后续对 EULA/Nexus 接口的任何调整都容易只落到一份实现上,造成两条初始化路径的
  Nexus 上传行为悄悄分叉。

### 7. shell 单引号转义重复实现 3 次 — CONFIRMED

- **文件**: `datasophon-cli-go/internal/cli/init/tar.go:106`(另两处 `internal/plan/builders_cluster.go:171`
  的 `shellQuote`、`internal/cli/create/rustfs.go:133` 的 `shellSingleQuote`)
- **问题**: 本 PR 新增/改动的 shell 单引号转义函数在三个文件里各自实现,编码方式还不完全一致。
- **影响**: 若某一处遗漏了某类特殊字符(如单引号本身、反斜杠)的转义,会在拼接远端 shell 命令时
  产生命令注入或执行失败,且修复一处不会同步到其余两处。

### 8. control.sh 把通用 env 加载改成硬编码白名单 — PLAUSIBLE

- **文件**: `package/raw/meta/datacluster-physical/OTELCOLLECTOR/script/control.sh:26`
- **问题**: `start()` 原来的 `set -a; . "$env_file"; set +a` 通用 source 方式被替换成仅识别
  `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`/`OTEL_DORIS_USER`/`OTEL_DORIS_PASSWORD` 四个硬编码
  key 的逐行解析循环。
- **影响**: 当前与 `otelcol-env.ftl` 的 4 个变量精确匹配,不会立即出错;但以后若在该模板里新增
  导出凭证(如新增 endpoint/token 变量),这里不会同步更新,新变量会被循环静默丢弃,只有在生产
  环境认证失败排障时才会被发现。

### 9. productPackagesPath 校验逻辑三处重复 — PLAUSIBLE

- **文件**: `datasophon-cli-go/internal/cli/create/initializer.go:57,116,165`
- **问题**: 新增的 `productPackagesPath` 校验代码被复制粘贴进同一文件内三个 `nodeInitializer`
  构造流程,而非提炼成共享函数。
- **影响**: 该校验逻辑（如路径存在性、格式检查）后续需要调整时,三处都要同步修改,容易遗漏其中
  一处导致某条初始化路径缺少必要校验。

### 10. ConfigModalPhysical.tsx 嵌套三元表达式 — PLAUSIBLE

- **文件**: `datasophon-ui-v2/src/pages/Colony/Manage/ConfigModalPhysical.tsx:256`
- **问题**: 向导弹窗底部按钮区域用 5 层嵌套三元表达式按当前步骤分支渲染。
- **影响**: 可读性和可扩展性差,新增/调整向导步骤时容易在深层嵌套三元里改错分支条件,且不易通过
  代码审查发现；改为按 step 索引的查找表会更不容易出错。

## 未发现问题的视角

- **跨文件调用链追踪**(签名/契约变更是否所有调用方都同步更新):未发现可坐实的破坏性遗漏。
- **CLAUDE.md 合规检查**:唯一疑似违规 —— 新增的 `InternalApiExceptionHandler` 未按
  `.claude/rules/springboot.md` 要求返回 `ProblemDetail`,但与仓库已有的 `ApiExceptionHandler`
  惯例一致,不算本 PR 引入的偏差,已排除。

## 建议处理顺序

正确性问题 #1-#3 建议合并前修复,尤其 #1/#2 直接命中本 PR 主打的"五节点自动化装机"核心流程；

# 4 视五节点场景的实际轮询体验决定是否本轮处理；#5-#10 为可延后处理的清理类问题。
