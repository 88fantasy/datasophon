# 可观测重构 Phase A — A1(OTELCOLLECTOR 数据面)实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增一个每节点安装的 `OTELCOLLECTOR` meta 服务,运行 otelcol-contrib v0.154.0,默认以 S3(bootstrap)模式把 metrics/logs/traces 经 `file_storage` 持久化队列写入 Rustfs,并暴露 `:8888` self-metrics。

**Architecture:** 复用 datasophon 现有"声明式服务"机制——`service_ddl.json`(自动经 `LoadServiceMeta`→`loadServicePhysicalDdl` 注册进 DB,无需 SQL 迁移)+ `control.sh`(通用 start/stop/status/restart runner)+ `otelcol.ftl`(worker 侧 freemarker 生成 otelcol 配置)。A1 只产出"可独立安装、落 S3"的数据面;切到 Doris、控制台、告警器、回灌属于 A3。

**Tech Stack:** Java 17 / Spring Boot 3.4.5(api+worker)、Freemarker(配置模板)、otelcol-contrib v0.154.0(Go 二进制)、Rustfs(S3 兼容,bootstrap sink)、JUnit(渲染/加载测试)。

## Global Constraints

- 设计真相之源:`docs/observability-otel-doris-设计-2026-06-19.md`(决策表 §1.3、Phase A 详细设计 §4、验收 §5、风险/审查追溯 §6/§8)。
- 分支:`refactor/observability-otel`。
- otelcol 发行版固定 **otelcol-contrib v0.154.0**,必须含组件:`awss3exporter`、`dorisexporter`、`awss3receiver`、`filestorage` extension、`filelogreceiver`、`prometheusreceiver`、`otlpreceiver`、`memorylimiterprocessor`、`batchprocessor`。
- 服务角色:`OTELCOLLECTOR` 单角色 `OtelCollector`,`roleType=worker`,`cardinality=N+`(每节点一个),与 Promtail 同构。
- meta 服务真相之源:`package/raw/meta/datacluster-physical/OTELCOLLECTOR/`;**禁止**为新增服务写 `db/migration` DML——`LoadServiceMeta` 启动自动注册。
- 配置模板放 `datasophon-worker/src/main/resources/templates/`;`${ip}` 由 worker 上下文注入,其余占位来自 `service_ddl.json` 的 `parameters`。
- A1 **不**引入 dorisexporter 到运行配置(staged 切换在 A3);A1 默认 exporter = `awss3` → Rustfs。
- `memory_limiter` 必须是 pipeline 第一个 processor;`file_storage` extension 必须在 `service.extensions` 列出且被 `sending_queue.storage` 引用。
- 提交粒度:每个 Task 结束一次 commit;Conventional Commits 中文描述。

---

## Phase A 全局进度跟踪表

> A1 本计划详述;A2/A3 为后续独立 sub-plan(各自展开时同样 bite-sized)。每个里程碑验收通过后更新本行状态。

| 里程碑 | 子系统 | 交付物 | 计划 | 状态 |
|---|---|---|---|---|
| A1 | 数据面 | OTELCOLLECTOR 服务,落 S3(Rustfs),self-metrics :8888 | **本计划** | 🟩 代码完成(配置 validate 实测通过/组件实测齐全);端到端落 S3 待真实 Rustfs 环境 |
| A2 | 存储 | otel database + 独立资源组 + create_schema=false 自管版本化 DDL + 契约测试 | 待出(A2 sub-plan) | ⬜ 未开始 |
| A3 | 控制面 | 控制台(配置 tab+监控 tab)+ @Scheduled 告警器 + 逐节点 staged 切换 S3→Doris + 逐节点 ack 边界回灌 | 待出(A3 sub-plan) | ⬜ 未开始 |

### 验收/整改追溯(§5 十一条 + §8 两轮七条 → 子系统归属)

| 来源条目 | 内容摘要 | 归属 | 本计划(A1)覆盖 |
|---|---|---|---|
| §5.1 | 改限流参数→落 S3(Rustfs) | A1+A3 | ✅ A1 落 S3 链路(Task 2/5);旋钮下发改动归 A3 |
| §5.2 | 装 Doris→自动切 dorisexporter→落 otel 表 | A2+A3 | ➖ |
| §5.3 | canary(HDFS)指标经 prometheusreceiver 进 Doris | A2+A3+C | ➖ |
| §5.4 | 监控 tab 显示健康/吞吐/队列/落盘量 | A3 | ➖(A1 仅保证 :8888 暴露这些 metric) |
| §5.5 / §5.5b | 逐节点回灌 / 切换非原子验收 | A3 | ➖ |
| §5.6 / §5.7 | 持续过载无人值守告警 / Doris 宕机落盘重放 | A1(落盘)+A3(告警) | ✅ A1 验证 file_storage 落盘不丢(Task 5);告警归 A3 |
| §5.8 | schema 契约测试 | A2 | ➖ |
| §5.9 / §5.10 | 凭据最小权限/隔离 / TLS | A2+A3 | ➖ |
| §5.11 | 投毒残余风险如实记录 | 文档(已记) | ➖ |
| §8 F1 凭据 | 按集群隔离 INSERT-only | A2+A3 | ➖ |
| §8 F2 回灌 | awss3receiver 时间窗回灌 | A3 | ➖ |
| §8 F3 背压 | file_storage 持久化队列 | **A1** | ✅ Task 2/5 |
| §8 F4 schema | create_schema=false 自管 DDL | A2 | ➖ |
| §8 F5 切换 | 逐节点 ack 边界 | A3 | ➖ |
| §8 F6 告警器 | 最小 @Scheduled 进 Phase A | A3 | ➖ |
| §8 F7 投毒 | 措辞修正 | 文档(已记) | ➖ |

> A1 直接闭环的整改项:**F3(持久化队列)**;并为 §5.1/§5.6/§5.4 打地基(S3 落库、落盘不丢、self-metrics 暴露)。其余条目由 A2/A3 sub-plan 承接,追溯列保证不丢项。

---

## File Structure(A1 改动地图)

- Create: `package/raw/meta/datacluster-physical/OTELCOLLECTOR/service_ddl.json` — 服务声明(arch/role/runner/hook/configWriter/parameters)
- Create: `package/raw/meta/datacluster-physical/OTELCOLLECTOR/script/control.sh` — 启停脚本
- Create: `datasophon-worker/src/main/resources/templates/otelcol.ftl` — otelcol 配置模板(S3/bootstrap 模式)
- Create: `datasophon-worker/src/test/java/com/datasophon/worker/test/OtelcolTemplateTest.java` — 模板渲染测试
- Create: `datasophon-api/src/test/java/com/datasophon/api/load/OtelCollectorDdlLoadTest.java` — DDL 解析/注册测试
- Create: `deploy/observability/otelcol/README.md` — 二进制 vendoring 与上传说明(版本/组件/校验)
- Reference(只读,不改):`package/raw/meta/datacluster-physical/PROMTAIL/`(同构样板)、`datasophon-worker/src/main/resources/templates/promtail.ftl`、`datasophon-api/.../load/LoadServiceMeta.java`

---

## Task 1: Vendoring otelcol-contrib v0.154.0 二进制与上传清单

A1 的运行物是 Go 二进制,不经 Maven 编译。本任务把二进制纳入 raw 包目录并文档化校验,使 `datasophon-cli upload registry` 能把它发到 Nexus raw,worker 安装时下载。

**Files:**
- Create: `deploy/observability/otelcol/README.md`
- 产物(不入 git,放本地 `package/` 待上传):`package/otelcol-contrib_0.154.0_linux_amd64.tar.gz`、`..._linux_arm64.tar.gz`

**Interfaces:**
- Produces:`packageName`(x86_64=`otelcol-contrib_0.154.0_linux_amd64.tar.gz`,aarch64=`..._arm64.tar.gz`)与 `decompressPackageName=otelcol-contrib_0.154.0`,供 Task 4 的 `service_ddl.json` 引用;二进制内可执行文件名 `otelcol-contrib`,供 Task 3 的 control.sh 引用。

- [ ] **Step 1: 下载两架构发行包并校验组件**

```bash
cd /tmp
VER=0.154.0
for ARCH in amd64 arm64; do
  curl -fL -o otelcol-contrib_${VER}_linux_${ARCH}.tar.gz \
    https://github.com/open-telemetry/opentelemetry-collector-releases/releases/download/v${VER}/otelcol-contrib_${VER}_linux_${ARCH}.tar.gz
done
mkdir -p check && tar -xzf otelcol-contrib_${VER}_linux_amd64.tar.gz -C check
./check/otelcol-contrib components | grep -E "awss3|doris|filestorage|filelog|prometheus|otlp|memory_limiter|batch"
```

Expected: 输出含 `awss3`(exporter+receiver)、`doris`、`filestorage`、`filelog`、`prometheus`、`otlp`、`memory_limiter`、`batch` 全部条目。任一缺失即此发行版不可用,停止并上报。

- [ ] **Step 2: 记录 md5,放入待上传目录**

```bash
md5sum /tmp/otelcol-contrib_0.154.0_linux_*.tar.gz
cp /tmp/otelcol-contrib_0.154.0_linux_*.tar.gz /Users/pro/IdeaProjects/datasophon/package/
```

记下两个 md5,Task 4 的 hook 用不到包级 md5(hook 只校验 control.sh),但写进 README 备查。

- [ ] **Step 3: 写 vendoring 说明**

`deploy/observability/otelcol/README.md`:

```markdown
# otelcol-contrib v0.154.0 vendoring

Phase A 数据面运行物。非 Maven 产物,手动 vendoring 后经 `datasophon-cli upload registry` 上传到 Nexus raw。

## 发行包
| arch | packageName | decompressPackageName | md5 |
|---|---|---|---|
| x86_64 | otelcol-contrib_0.154.0_linux_amd64.tar.gz | otelcol-contrib_0.154.0 | <填 Step 2 amd64 md5> |
| aarch64 | otelcol-contrib_0.154.0_linux_arm64.tar.gz | otelcol-contrib_0.154.0 | <填 Step 2 arm64 md5> |

## 必含组件(Step 1 校验)
awss3 exporter/receiver、doris exporter、filestorage extension、filelog/prometheus/otlp receiver、memory_limiter/batch processor。

## 上传
见 CLAUDE.local.md「upload registry 完整命令」,raw 仓库。
```

- [ ] **Step 4: 提交**

```bash
cd /Users/pro/IdeaProjects/datasophon
git add deploy/observability/otelcol/README.md
git commit -m "feat(observability): vendoring otelcol-contrib v0.154.0 说明与组件校验"
```

---

## Task 2: otelcol 配置模板(S3 模式)+ 渲染测试

otelcol 配置由 worker 侧 freemarker 生成(与 promtail.ftl 同机制)。先写渲染测试断言关键结构,再写模板让其通过。

**Files:**
- Create: `datasophon-worker/src/main/resources/templates/otelcol.ftl`
- Test: `datasophon-worker/src/test/java/com/datasophon/worker/test/OtelcolTemplateTest.java`
- Reference: `datasophon-worker/src/test/java/com/datasophon/worker/test/FreemarkerTest.java`(同款渲染测试写法)、`datasophon-worker/src/main/java/com/datasophon/worker/utils/FreemakerUtils.java`

**Interfaces:**
- Consumes:模板占位 `${ip}`(worker 注入)、`${s3Endpoint}`、`${s3Bucket}`、`${s3Prefix}`、`${s3Region}`、`${memLimitMiB}`、`${batchSize}`、`${queueStorageDir}`(均来自 Task 4 parameters)。
- Produces:生成文件名 `otelcol.yaml`、输出目录 `config`、templateName `otelcol.ftl`,供 Task 4 的 `configWriter.generators` 引用。

- [ ] **Step 1: 写失败的渲染测试**

`OtelcolTemplateTest.java`:

```java
package com.datasophon.worker.test;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import org.junit.Test;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertTrue;

public class OtelcolTemplateTest {

    private String render() throws Exception {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_31);
        cfg.setClassForTemplateLoading(OtelcolTemplateTest.class, "/templates");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        Template tpl = cfg.getTemplate("otelcol.ftl");
        Map<String, Object> data = new HashMap<>();
        data.put("ip", "10.0.0.11");
        data.put("s3Endpoint", "http://mw1:9040");
        data.put("s3Bucket", "otel-bootstrap");
        data.put("s3Prefix", "node");
        data.put("s3Region", "us-east-1");
        data.put("memLimitMiB", "512");
        data.put("batchSize", "8192");
        data.put("queueStorageDir", "/data/otelcol/storage");
        StringWriter out = new StringWriter();
        tpl.process(data, out);
        return out.toString();
    }

    @Test
    public void renders_s3_mode_with_persistent_queue() throws Exception {
        String yaml = render();
        // 持久化队列(F3)
        assertTrue(yaml.contains("file_storage/queue"));
        assertTrue(yaml.contains("directory: /data/otelcol/storage"));
        assertTrue(yaml.contains("storage: file_storage/queue"));
        // S3 bootstrap sink → Rustfs
        assertTrue(yaml.contains("endpoint: http://mw1:9040"));
        assertTrue(yaml.contains("s3_bucket: otel-bootstrap"));
        assertTrue(yaml.contains("s3_force_path_style: true"));
        // 限流/批量
        assertTrue(yaml.contains("memory_limiter"));
        assertTrue(yaml.contains("limit_mib: 512"));
        assertTrue(yaml.contains("send_batch_size: 8192"));
        // self-metrics(A3 监控 tab 依赖)
        assertTrue(yaml.contains("address: 0.0.0.0:8888"));
        // 三信号 pipeline
        assertTrue(yaml.contains("metrics:"));
        assertTrue(yaml.contains("logs:"));
        assertTrue(yaml.contains("traces:"));
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `JAVA_HOME=$JH17 ./mvnw -pl datasophon-worker -Dtest=OtelcolTemplateTest test -s ~/.m2/setting.xml`
Expected: FAIL —— `TemplateNotFoundException: otelcol.ftl`。

- [ ] **Step 3: 写模板令其通过**

`datasophon-worker/src/main/resources/templates/otelcol.ftl`:

```yaml
extensions:
  file_storage/queue:
    directory: ${queueStorageDir}
    timeout: 10s

receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318
  prometheus/self:
    config:
      scrape_configs:
        - job_name: otelcol-self
          scrape_interval: 30s
          static_configs:
            - targets: ['127.0.0.1:8888']
              labels:
                host: ${ip}

processors:
  memory_limiter:
    check_interval: 5s
    limit_mib: ${memLimitMiB}
  batch:
    send_batch_size: ${batchSize}
    timeout: 5s

exporters:
  awss3:
    s3uploader:
      region: ${s3Region}
      s3_bucket: ${s3Bucket}
      s3_prefix: ${s3Prefix}
      endpoint: ${s3Endpoint}
      s3_force_path_style: true
    marshaler: otlp_json
    sending_queue:
      enabled: true
      storage: file_storage/queue
    retry_on_failure:
      enabled: true
      initial_interval: 5s
      max_interval: 30s
      max_elapsed_time: 300s

service:
  extensions: [file_storage/queue]
  telemetry:
    metrics:
      address: 0.0.0.0:8888
  pipelines:
    metrics:
      receivers: [otlp, prometheus/self]
      processors: [memory_limiter, batch]
      exporters: [awss3]
    logs:
      receivers: [otlp]
      processors: [memory_limiter, batch]
      exporters: [awss3]
    traces:
      receivers: [otlp]
      processors: [memory_limiter, batch]
      exporters: [awss3]
```

- [ ] **Step 4: 运行测试验证通过**

Run: `JAVA_HOME=$JH17 ./mvnw -pl datasophon-worker -Dtest=OtelcolTemplateTest test -s ~/.m2/setting.xml`
Expected: PASS。

- [ ] **Step 5: 用真二进制校验渲染产物是合法 otelcol 配置**

```bash
# 用 Task 1 解出的二进制验证渲染出的样例 YAML(把测试渲染结果落到 /tmp/otelcol.yaml 或手填占位)
/tmp/check/otelcol-contrib validate --config /tmp/otelcol.yaml
```

Expected: 无 error 退出 0(`s3uploader`/`file_storage`/pipelines 均被接受)。若报字段名不符,以 `validate` 为准回改模板再跑 Step 4。

- [ ] **Step 6: 提交**

```bash
git add datasophon-worker/src/main/resources/templates/otelcol.ftl \
        datasophon-worker/src/test/java/com/datasophon/worker/test/OtelcolTemplateTest.java
git commit -m "feat(observability): otelcol S3 模式配置模板 + 渲染测试(file_storage 持久化队列)"
```

---

## Task 3: control.sh 启停脚本

**Files:**
- Create: `package/raw/meta/datacluster-physical/OTELCOLLECTOR/script/control.sh`
- Reference: `package/raw/meta/datacluster-physical/PROMTAIL/script/control.sh`(同款骨架)

**Interfaces:**
- Consumes:同目录解压出的二进制 `otelcol-contrib`(Task 1)、`config/otelcol.yaml`(Task 2 生成)。
- Produces:被 Task 4 的 `startRunner/stopRunner/statusRunner/restartRunner` 以 `control.sh (start|stop|status|restart)` 调用。

- [ ] **Step 1: 写 control.sh**

`package/raw/meta/datacluster-physical/OTELCOLLECTOR/script/control.sh`:

```sh
#!/bin/sh
usage="Usage: control.sh (start|stop|restart|status)"
if [ $# -le 0 ]; then echo $usage; exit 1; fi

current_path=$(cd `dirname $0`;pwd)
startStop=$1; shift
echo "Begin $startStop ......"

export PID_DIR=$current_path/pid
export LOG_DIR=$current_path/logs
export STOP_TIMEOUT=10
[ -d "$LOG_DIR" ] || mkdir -p "$LOG_DIR"

log=$LOG_DIR/otelcol.out
pid=$PID_DIR/otelcol.pid
bin=$current_path/otelcol-contrib
conf=$current_path/config/otelcol.yaml

start() {
  [ -w "$PID_DIR" ] || mkdir -p "$PID_DIR"
  if [ -f $pid ] && kill -0 `cat $pid` >/dev/null 2>&1; then
    echo "otelcol running as `cat $pid`. Stop it first."; exit 1
  fi
  echo "starting otelcol, logging to $log"
  nohup $bin --config=$conf > $log 2>&1 &
  echo $! > $pid
}

stop() {
  if [ -f $pid ]; then
    TARGET_PID=`cat $pid`
    if kill -0 $TARGET_PID >/dev/null 2>&1; then
      echo "stopping otelcol"; kill $TARGET_PID; sleep $STOP_TIMEOUT
      kill -0 $TARGET_PID >/dev/null 2>&1 && { echo "force kill"; kill -9 $TARGET_PID; }
    else echo "no otelcol to stop"; fi
    rm -f $pid
  else echo "no otelcol to stop"; fi
}

status() {
  if [ -f $pid ] && kill -0 `cat $pid` >/dev/null 2>&1; then
    echo "otelcol is running"
  else echo "otelcol is not running"; exit 1; fi
}

case $startStop in
  (start) start ;;
  (stop) stop ;;
  (restart) stop; sleep 2s; start ;;
  (status) status ;;
  (*) echo $usage; exit 1 ;;
esac
echo "End $startStop otelcol."
```

- [ ] **Step 2: 校验语法 + 记录 md5**

```bash
sh -n package/raw/meta/datacluster-physical/OTELCOLLECTOR/script/control.sh && echo "syntax ok"
md5sum package/raw/meta/datacluster-physical/OTELCOLLECTOR/script/control.sh
```

Expected: `syntax ok`;记下 md5 供 Task 4 的 POST_INSTALL hook 填入。

- [ ] **Step 3: 提交**

```bash
git add package/raw/meta/datacluster-physical/OTELCOLLECTOR/script/control.sh
git commit -m "feat(observability): OTELCOLLECTOR control.sh 启停脚本"
```

---

## Task 4: OTELCOLLECTOR service_ddl.json + 加载注册测试

**Files:**
- Create: `package/raw/meta/datacluster-physical/OTELCOLLECTOR/service_ddl.json`
- Test: `datasophon-api/src/test/java/com/datasophon/api/load/OtelCollectorDdlLoadTest.java`
- Reference: `datasophon-api/.../service/impl/DdlMetaServiceImpl`(`loadServicePhysicalDdl` 实现)、PROMTAIL/service_ddl.json

**Interfaces:**
- Consumes:Task 1 的 packageName/decompressPackageName、Task 2 的 templateName/参数名、Task 3 的 control.sh md5。
- Produces:服务名 `OTELCOLLECTOR`、角色名 `OtelCollector`,启动后注册进 `ServiceInfoMap`/DB,供 A3 控制台与安装流程引用。

- [ ] **Step 1: 写 service_ddl.json**

`package/raw/meta/datacluster-physical/OTELCOLLECTOR/service_ddl.json`:

```json
{
  "name": "OTELCOLLECTOR",
  "label": "OpenTelemetry Collector",
  "description": "统一可观测采集器(metrics/logs/traces)",
  "version": "0.154.0",
  "sortNum": 18,
  "dependencies": [],
  "createDecompressDir": true,
  "arch": {
    "x86_64": {
      "packageName": "otelcol-contrib_0.154.0_linux_amd64.tar.gz",
      "decompressPackageName": "otelcol-contrib_0.154.0"
    },
    "aarch64": {
      "packageName": "otelcol-contrib_0.154.0_linux_arm64.tar.gz",
      "decompressPackageName": "otelcol-contrib_0.154.0"
    }
  },
  "roles": [
    {
      "name": "OtelCollector",
      "label": "OtelCollector",
      "roleType": "worker",
      "cardinality": "1+",
      "logFile": "logs/otelcol.out",
      "jmxPort": 8888,
      "startRunner":   { "timeout": "60",  "program": "control.sh", "args": ["start"] },
      "stopRunner":    { "timeout": "600", "program": "control.sh", "args": ["stop"] },
      "statusRunner":  { "timeout": "60",  "program": "control.sh", "args": ["status"] },
      "restartRunner": { "timeout": "60",  "program": "control.sh", "args": ["restart"] },
      "hooks": [
        {
          "type": "POST_INSTALL",
          "action": "download",
          "params": {
            "from": "script/control.sh",
            "to": "control.sh",
            "md5": "<填 Task3 Step2 的 md5>"
          }
        }
      ]
    }
  ],
  "configWriter": {
    "generators": [
      {
        "filename": "otelcol.yaml",
        "configFormat": "custom",
        "outputDirectory": "config",
        "templateName": "otelcol.ftl",
        "includeParams": [
          "s3Endpoint", "s3Bucket", "s3Prefix", "s3Region",
          "memLimitMiB", "batchSize", "queueStorageDir"
        ]
      }
    ]
  },
  "parameters": [
    { "name": "s3Endpoint", "label": "S3 端点(Rustfs)", "required": true, "configType": "map", "type": "input", "value": "http://mw1:9040", "defaultValue": "http://mw1:9040", "configurableInWizard": true, "hidden": false },
    { "name": "s3Bucket", "label": "S3 Bucket", "required": true, "configType": "map", "type": "input", "value": "otel-bootstrap", "defaultValue": "otel-bootstrap", "configurableInWizard": true, "hidden": false },
    { "name": "s3Prefix", "label": "S3 前缀", "required": true, "configType": "map", "type": "input", "value": "node", "defaultValue": "node", "configurableInWizard": false, "hidden": false },
    { "name": "s3Region", "label": "S3 Region", "required": true, "configType": "map", "type": "input", "value": "us-east-1", "defaultValue": "us-east-1", "configurableInWizard": false, "hidden": true },
    { "name": "memLimitMiB", "label": "内存限制(MiB)", "required": true, "configType": "map", "type": "input", "value": "512", "defaultValue": "512", "configurableInWizard": true, "hidden": false },
    { "name": "batchSize", "label": "批量大小", "required": true, "configType": "map", "type": "input", "value": "8192", "defaultValue": "8192", "configurableInWizard": true, "hidden": false },
    { "name": "queueStorageDir", "label": "持久化队列目录", "required": true, "configType": "map", "type": "input", "value": "/data/otelcol/storage", "defaultValue": "/data/otelcol/storage", "configurableInWizard": false, "hidden": false }
  ]
}
```

- [ ] **Step 2: 写失败的加载测试**

`OtelCollectorDdlLoadTest.java`:

```java
package com.datasophon.api.load;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSONObject;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OtelCollectorDdlLoadTest {

    private static final String DDL =
        "package/raw/meta/datacluster-physical/OTELCOLLECTOR/service_ddl.json";

    @Test
    public void ddl_is_valid_and_declares_per_node_worker_role() {
        File f = new File(System.getProperty("user.dir"))
                .toPath().resolveSibling("datasophon").resolve(DDL).toFile();
        // 兼容从仓库根或模块目录运行
        File ddl = f.exists() ? f : new File(DDL).getAbsoluteFile().exists()
                ? new File(DDL).getAbsoluteFile()
                : new File("../" + DDL);
        assertTrue("service_ddl.json 必须存在: " + ddl, ddl.exists());

        JSONObject json = JSONObject.parseObject(FileUtil.readUtf8String(ddl));
        assertEquals("OTELCOLLECTOR", json.getString("name"));
        JSONObject role = json.getJSONArray("roles").getJSONObject(0);
        assertEquals("OtelCollector", role.getString("name"));
        assertEquals("worker", role.getString("roleType"));
        assertEquals("1+", role.getString("cardinality"));
        assertEquals(Integer.valueOf(8888), role.getInteger("jmxPort"));
        // configWriter 指向 otelcol.ftl
        assertEquals("otelcol.ftl",
            json.getJSONObject("configWriter").getJSONArray("generators")
                .getJSONObject(0).getString("templateName"));
        // POST_INSTALL 下载 control.sh
        assertEquals("download",
            role.getJSONArray("hooks").getJSONObject(0).getString("action"));
    }
}
```

> 注:路径解析按本仓库测试运行目录调整;若项目已有读取 meta 的测试工具类(参考被排除的 `MetaUtilsTaskTest`),优先复用其加载方式而非手拼路径。

- [ ] **Step 3: 运行测试验证失败**

Run: `JAVA_HOME=$JH17 ./mvnw -pl datasophon-api -Dtest=OtelCollectorDdlLoadTest test -s ~/.m2/setting.xml`
Expected: FAIL —— 文件不存在 或 JSON 未创建前断言失败。

- [ ] **Step 4: 确认 json 已就位,运行测试验证通过**

Run: `JAVA_HOME=$JH17 ./mvnw -pl datasophon-api -Dtest=OtelCollectorDdlLoadTest test -s ~/.m2/setting.xml`
Expected: PASS。

- [ ] **Step 5: 启动 api 验证自动注册(不写 DML)**

```bash
JAVA_HOME=$JH17 ./mvnw -pl datasophon-api spring-boot:run -s ~/.m2/setting.xml
# 另开终端,确认日志无 "invalid service ddl file: ... OTELCOLLECTOR"
grep -i "OTELCOLLECTOR" <api 启动日志>
```

Expected: 无 invalid 报错;`LoadServiceMeta`→`loadServicePhysicalDdl` 正常解析,OTELCOLLECTOR 进入服务列表(UI 服务安装向导可见)。

- [ ] **Step 6: 提交**

```bash
git add package/raw/meta/datacluster-physical/OTELCOLLECTOR/service_ddl.json \
        datasophon-api/src/test/java/com/datasophon/api/load/OtelCollectorDdlLoadTest.java
git commit -m "feat(observability): OTELCOLLECTOR service_ddl.json(每节点 worker 角色)+ 加载注册测试"
```

---

## Task 5: 端到端冒烟验收(落 S3 + 落盘不丢)

把 A1 串起来:在本地 compose 拓扑装上 OTELCOLLECTOR,灌合成 OTLP,验证对象落 Rustfs;停 sink 验证 file_storage 落盘不丢。对应 §5.1(S3 段)与 §5.6(落盘不丢段)。

**Files:**
- Modify: `deploy/compose/docker-compose.standalone.yml`(加一个 otelcol agent 容器,复用已 vendoring 的二进制 + Task 2 渲染配置)
- Create: `deploy/observability/otelcol/smoke-test.md`(冒烟步骤与期望)

**Interfaces:**
- Consumes:Task 1 二进制、Task 2 模板渲染出的 `otelcol.yaml`、Rustfs(compose 内或 mw1 :9040)。

- [ ] **Step 1: 渲染一份节点配置 + 启 otelcol(S3 指向 Rustfs)**

```bash
# 用 Task2 测试同参数渲染 /tmp/otelcol.yaml(s3Endpoint 指向可达 Rustfs,bucket 预先创建)
/tmp/check/otelcol-contrib --config /tmp/otelcol.yaml &
sleep 3
curl -s localhost:8888/metrics | grep otelcol_process_uptime  # self-metrics 在线
```

Expected: `:8888/metrics` 返回含 `otelcol_process_uptime`(§5.4 地基)。

- [ ] **Step 2: 灌合成 OTLP,验证落 Rustfs**

```bash
# 用 otelcol 的 telemetrygen 或 curl 向 :4318 发一条 OTLP/HTTP traces
curl -s -X POST localhost:4318/v1/traces -H 'Content-Type: application/json' \
  -d '{"resourceSpans":[{"scopeSpans":[{"spans":[{"traceId":"00000000000000000000000000000001","spanId":"0000000000000001","name":"smoke"}]}]}]}'
sleep 10
# 列 Rustfs bucket,确认有对象
aws --endpoint-url http://mw1:9040 s3 ls s3://otel-bootstrap/ --recursive | grep node
```

Expected: bucket 下出现 `node/...` 前缀对象(awss3exporter 已落 S3)。

- [ ] **Step 3: 验证 file_storage 落盘不丢(§5.6 段)**

```bash
# 把 s3Endpoint 改为不可达,重启 otelcol,再灌数据,观察队列落盘
ls -l /data/otelcol/storage   # file_storage 目录有数据文件
curl -s localhost:8888/metrics | grep -E "otelcol_exporter_queue_size|otelcol_exporter_send_failed"
# 恢复 s3Endpoint 可达,重启,确认队列重放、对象最终出现在 Rustfs
```

Expected: sink 不可达期间 `/data/otelcol/storage` 有持久化文件、`queue_size>0` 且无数据丢弃;恢复后对象补齐到 Rustfs。

- [ ] **Step 4: 记录冒烟步骤**

把 Step 1-3 的命令与期望写入 `deploy/observability/otelcol/smoke-test.md`(供 CI/复测引用)。

- [ ] **Step 5: 更新 Phase A 进度表 A1 行 + 提交**

把本计划顶部进度表 A1 状态改为 `✅ 完成(落 S3+落盘不丢冒烟通过)`。

```bash
git add deploy/observability/otelcol/smoke-test.md deploy/compose/docker-compose.standalone.yml \
        docs/observability-otel-phaseA1-实施计划-2026-06-19.md
git commit -m "test(observability): OTELCOLLECTOR A1 端到端冒烟(落 S3 + file_storage 落盘不丢)"
```

---

## A1 完成定义(Definition of Done)

- [ ] otelcol-contrib v0.154.0 二进制 vendoring 且组件齐全(Task 1)
- [ ] otelcol.ftl 渲染测试通过 + `otelcol validate` 接受(Task 2)
- [ ] control.sh 语法校验通过(Task 3)
- [ ] service_ddl.json 加载测试通过 + api 启动自动注册无报错(Task 4)
- [ ] 冒烟:合成 OTLP 落 Rustfs + sink 不可达时 file_storage 落盘不丢、恢复重放(Task 5)
- [ ] 追溯:§8 F3(持久化队列)闭环;§5.1/§5.6/§5.4 地基就位

## 衔接 A2 / A3(后续独立 sub-plan)

- **A2(存储)**:`otel` database + 独立资源组;在沙箱用 `create_schema=true` 导出 exporter 期望 DDL → 版本化(参考 `migration/DatabaseMigration` 自研执行器)→ 契约测试。交付后 A1 的 exporter 才有 Doris 表可写。
- **A3(控制面)**:控制台配置 tab(改 Task 4 parameters 并经 `ServiceConfigureHandler` 下发 + restart)/ 监控 tab(查 A1 的 :8888)/ 最小 `@Scheduled` 告警器(F6)/ 逐节点 staged 切换 awss3→doris(F5,ack 边界)/ 逐节点 awss3receiver 回灌(F2/F5)。
