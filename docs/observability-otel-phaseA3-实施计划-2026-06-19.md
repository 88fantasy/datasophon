# 可观测重构 Phase A — A3(控制面)实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立 OTELCOLLECTOR 的集中控制面——配置下发(重生成节点 otelcol 配置 + gRPC 推送 + 重启)、监控(self-metrics)、最小告警器、逐节点 staged 切换 S3→Doris 与 ack 边界回灌、凭据/schema 编排。

**Architecture:** A3 是控制面,数据面(A1)与存储(A2)的"指挥层"。所有节点配置变更走同一条主干:master 按当前参数为某节点重生成 otelcol 配置 → `WorkerCallAdapter.configureServiceRole`(gRPC)推送 → `restartServiceRole`(otelcol 无热加载,走重启)。staged 切换、凭据注入、控制台改配置都复用这条主干。A3 体量大,**拆为 5 个 sub-plan**;本文件详述依赖根 **A3a(配置下发主干)**,其余(A3b 监控 / A3c 告警器 / A3d 切换+回灌 / A3e UI 控制台)各自后续展开。

**Tech Stack:** Java 17 / Spring Boot 3.4.5(api,`@Async`/`@Scheduled`、gRPC client)、gRPC(WorkerCommandClient)、React 19 + Antd Pro(控制台,A3e)、A1 的 service_ddl/模板、A2 的 OtelSchemaApplier。

## Global Constraints

- 设计真相之源:`docs/observability-otel-doris-设计-2026-06-19.md`(§4.3、§5.4-5.7、§8 F1/F2/F5/F6)。
- 分支:`refactor/observability-otel`。
- 配置下发主干:`master/transport/WorkerCallAdapter.configureServiceRole(hostname, GenerateServiceConfigCommand)` + `restartServiceRole(hostname, ServiceRoleOperateCommand)`。**不得**为 otelcol 引入热加载假设——无 reload,改配置=重启。
- otelcol 服务名 `OTELCOLLECTOR`、角色名 `OtelCollector`(A1 既定)。
- **DORIS 就绪判据**:其服务角色实例达 `ServiceRoleState.RUNNING`(`MasterScheduledService` 15s/30s 巡检维护);**禁止**假设 Doris 在 collector 之前就绪。
- **逐节点(非全局原子,F5)**:切换/回灌按节点各自的 ack 边界(节点产生首条 Doris 写入才记其切换点),不依赖单一全局切换点。
- **告警器独立于 Doris/Phase B(F6)**:查 collector self-metrics(`:8888`),不查 Doris。
- **凭据(F1)**:按集群生成 `otel_collector` 口令,经配置下发链路注入 A1 的 `otelcol.env`,替换 A2 的 `CHANGE_ME_AT_A3`;不硬编码、不进静态脚本。
- `@Async` 方法不叠加 `@Transactional`(本仓库约定)。
- 提交粒度:每 Task 一次 commit;Conventional Commits 中文。
- 环境受限如实标注:本机无 Worker/Doris/Rustfs 运行实例,凡需真实下发/切换的步骤标"待真实环境",不伪造输出。

---

## A3 分解与进度跟踪

| sub-plan |                                    内容                                     |   依赖    |     计划      | 状态 |
|----------|---------------------------------------------------------------------------|---------|-------------|----|
| **A3a**  | **配置下发主干**:按节点重生成 otelcol 配置 + configureServiceRole + restart;REST 触发     | A1      | **本文件详述**   | ⬜  |
| A3b      | 监控:self-metrics(:8888)轮询 + 监控数据 API                                       | A3a     | 后续 sub-plan | ⬜  |
| A3c      | 最小 @Scheduled 告警器(F6):查 self-metrics,队列/丢弃超阈通知                            | A3b     | 后续 sub-plan | ⬜  |
| A3d      | staged 切换(F5)+ ack 边界回灌(F2):exporterMode s3→doris、awss3receiver 时间窗       | A3a,A2  | 后续 sub-plan | ⬜  |
| A3e      | UI 控制台页面:配置 tab(旋钮+YAML 兜底)+ 监控 tab                                       | A3a,A3b | 后续 sub-plan | ⬜  |
| A3f      | 凭据/schema 编排:Doris 就绪→OtelSchemaApplier.apply + 按集群生成 otel_collector 口令注入 | A3a,A2  | 并入 A3d      | ⬜  |

### A3 承接的验收/整改条目(→ sub-plan 归属)

|        来源        |                 内容                 |   归属    |    A3a 覆盖     |
|------------------|------------------------------------|---------|---------------|
| §4.3 配置下发        | 改配置→重生成→gRPC→restart               | **A3a** | ✅ 全部          |
| §5.4             | 监控 tab 显示健康/吞吐/队列/落盘               | A3b/A3e | ➖             |
| §8 F6 / §5.6/5.7 | 持续告警器(无人值守)                        | A3c     | ➖             |
| §8 F5 / §5.5b    | 逐节点 ack 切换(非原子)                    | A3d     | A3a 提供按节点下发原语 |
| §8 F2 / §5.5     | 逐节点 ack 边界回灌                       | A3d     | ➖             |
| §5.2             | 装 Doris→自动切 dorisexporter→落 otel 表 | A3d+A3f | ➖             |
| §8 F1(下发)        | 按集群 otel_collector 口令注入            | A3f     | ➖             |

> A3a 是其余全部的执行原语:任何"改某节点 otelcol 配置并生效"都经 A3a。先把它做扎实、可单测,A3d 的切换才有按节点下发的地基。

---

## File Structure(A3a 改动地图)

- Create: `datasophon-api/src/main/java/com/datasophon/api/observability/OtelCollectorConfigService.java` — 配置下发主干(build 命令 + push + restart)
- Create: `datasophon-api/src/main/java/com/datasophon/api/controller/observability/OtelCollectorController.java` — REST 触发(thin)
- Create: `datasophon-api/src/test/java/com/datasophon/api/observability/OtelCollectorConfigServiceTest.java` — mock WorkerCallAdapter 的下发顺序/失败短路测试
- Reference(只读):`master/service/PrometheusService.java`(同款"重生成配置"模式)、`master/transport/WorkerCallAdapter.java`、`common/command/{GenerateServiceConfigCommand,ServiceRoleOperateCommand}.java`、A1 `service_ddl.json`

---

## Task 1: OtelCollectorConfigService — 构建节点配置命令

把"为某节点构建 OTELCOLLECTOR 的 GenerateServiceConfigCommand"独立成可单测方法。参数取自传入的 paramMap(实际取数由 A3e/A3d 提供,本任务只负责把 params → 命令)。

**Files:**
- Create: `.../observability/OtelCollectorConfigService.java`
- Test: `.../observability/OtelCollectorConfigServiceTest.java`

**Interfaces:**
- Produces:`GenerateServiceConfigCommand buildConfigCommand(Integer clusterId, String hostname, Map<String,String> params)`——serviceName=`OTELCOLLECTOR`、serviceRoleName=`OtelCollector`、cofigFileMap 含两个 generator(`otelcol.yaml`/模板 otelcol.ftl、`otelcol.env`/模板 otelcol-env.ftl)。

- [ ] **Step 1: 写失败测试(命令字段与双 generator)**

```java
package com.datasophon.api.observability;

import com.datasophon.common.command.GenerateServiceConfigCommand;
import com.datasophon.common.model.Generators;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OtelCollectorConfigServiceTest {

    private final OtelCollectorConfigService svc = new OtelCollectorConfigService(null);

    @Test
    void builds_command_with_two_generators() {
        Map<String, String> params = new HashMap<>();
        params.put("s3Endpoint", "http://mw1:9040");
        GenerateServiceConfigCommand cmd = svc.buildConfigCommand(1, "app1", params);

        assertEquals("OTELCOLLECTOR", cmd.getServiceName());
        assertEquals("OtelCollector", cmd.getServiceRoleName());
        assertEquals(Integer.valueOf(1), cmd.getClusterId());

        Set<String> files = cmd.getCofigFileMap().keySet().stream()
                .map(Generators::getFilename).collect(Collectors.toSet());
        assertTrue(files.contains("otelcol.yaml"), "缺 otelcol.yaml generator");
        assertTrue(files.contains("otelcol.env"), "缺 otelcol.env generator(凭据)");
    }
}
```

- [ ] **Step 2: 运行看失败**

Run: `JAVA_HOME=/Users/pro/Library/Java/JavaVirtualMachines/jbr-17.0.12-1/Contents/Home ./mvnw -pl datasophon-api -Dtest=OtelCollectorConfigServiceTest test -s ~/.m2/setting.xml -Dspotless.check.skip=true`
Expected: FAIL —— `OtelCollectorConfigService` 未定义。

- [ ] **Step 3: 写实现(buildConfigCommand)**

```java
package com.datasophon.api.observability;

import com.datasophon.api.master.transport.WorkerCallAdapter;
import com.datasophon.common.command.GenerateServiceConfigCommand;
import com.datasophon.common.command.ServiceRoleOperateCommand;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.model.Generators;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.utils.ExecResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** OTELCOLLECTOR 配置下发主干:按节点重生成配置(otelcol.yaml + otelcol.env)→ 推送 → 重启。 */
@Service
public class OtelCollectorConfigService {

    private static final Logger log = LoggerFactory.getLogger(OtelCollectorConfigService.class);

    static final String SERVICE_NAME = "OTELCOLLECTOR";
    static final String ROLE_NAME = "OtelCollector";

    private final WorkerCallAdapter workerCallAdapter;

    public OtelCollectorConfigService(WorkerCallAdapter workerCallAdapter) {
        this.workerCallAdapter = workerCallAdapter;
    }

    public GenerateServiceConfigCommand buildConfigCommand(Integer clusterId, String hostname,
                                                           Map<String, String> params) {
        Map<Generators, List<ServiceConfig>> fileMap = new HashMap<>();
        fileMap.put(generator("otelcol.yaml", "otelcol.ftl"), toConfigs(params));
        fileMap.put(generator("otelcol.env", "otelcol-env.ftl"), toConfigs(params));

        GenerateServiceConfigCommand cmd = new GenerateServiceConfigCommand();
        cmd.setClusterId(clusterId);
        cmd.setServiceName(SERVICE_NAME);
        cmd.setServiceRoleName(ROLE_NAME);
        cmd.setCofigFileMap(fileMap);
        return cmd;
    }

    private static Generators generator(String filename, String template) {
        Generators g = new Generators();
        g.setFilename(filename);
        g.setOutputDirectory("config");
        g.setConfigFormat("custom");
        g.setTemplateName(template);
        return g;
    }

    private static List<ServiceConfig> toConfigs(Map<String, String> params) {
        List<ServiceConfig> list = new ArrayList<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            ServiceConfig sc = new ServiceConfig();
            sc.setName(e.getKey());
            sc.setValue(e.getValue());
            sc.setConfigType("map");
            sc.setRequired(true);
            sc.setEnabled(true);
            list.add(sc);
        }
        return list;
    }
}
```

- [ ] **Step 4: 运行看通过**

Run: 同 Step 2。
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add datasophon-api/src/main/java/com/datasophon/api/observability/OtelCollectorConfigService.java \
        datasophon-api/src/test/java/com/datasophon/api/observability/OtelCollectorConfigServiceTest.java
git commit -m "feat(observability): OTELCOLLECTOR 配置命令构建(otelcol.yaml + otelcol.env 双 generator)"
```

---

## Task 2: pushNodeConfig — 下发 + 重启(顺序与失败短路)

**Files:**
- Modify: `.../observability/OtelCollectorConfigService.java`
- Modify: `.../observability/OtelCollectorConfigServiceTest.java`

**Interfaces:**
- Produces:`ExecResult pushNodeConfig(Integer clusterId, String hostname, Map<String,String> params)`——先 `configureServiceRole`,成功后才 `restartServiceRole`;configure 失败则不重启、返回失败。

- [ ] **Step 1: 写失败测试(mock adapter 验证顺序 + 短路)**

向测试类加(用 Mockito,本项目已具备):

```java
import com.datasophon.api.master.transport.WorkerCallAdapter;
import com.datasophon.common.command.ServiceRoleOperateCommand;
import com.datasophon.common.utils.ExecResult;
import org.mockito.InOrder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Test
void push_configures_then_restarts_in_order() {
    WorkerCallAdapter adapter = mock(WorkerCallAdapter.class);
    when(adapter.configureServiceRole(eq("app1"), any())).thenReturn(ok());
    when(adapter.restartServiceRole(eq("app1"), any())).thenReturn(ok());

    OtelCollectorConfigService s = new OtelCollectorConfigService(adapter);
    ExecResult r = s.pushNodeConfig(1, "app1", new HashMap<>());

    assertTrue(r.getExecResult());
    InOrder o = inOrder(adapter);
    o.verify(adapter).configureServiceRole(eq("app1"), any());
    o.verify(adapter).restartServiceRole(eq("app1"), any());
}

@Test
void push_does_not_restart_when_configure_fails() {
    WorkerCallAdapter adapter = mock(WorkerCallAdapter.class);
    when(adapter.configureServiceRole(eq("app1"), any())).thenReturn(fail());

    OtelCollectorConfigService s = new OtelCollectorConfigService(adapter);
    ExecResult r = s.pushNodeConfig(1, "app1", new HashMap<>());

    assertTrue(!r.getExecResult());
    verify(adapter, never()).restartServiceRole(any(), any());
}

private static ExecResult ok() { ExecResult e = new ExecResult(); e.setExecResult(true); return e; }
private static ExecResult fail() { ExecResult e = new ExecResult(); e.setExecResult(false); return e; }
```

- [ ] **Step 2: 运行看失败**

Run: 同 Task 1 Step 2 命令。
Expected: FAIL —— `pushNodeConfig` 未定义。

- [ ] **Step 3: 写实现**

向 `OtelCollectorConfigService` 加:

```java
public ExecResult pushNodeConfig(Integer clusterId, String hostname, Map<String, String> params) {
    GenerateServiceConfigCommand cfg = buildConfigCommand(clusterId, hostname, params);
    ExecResult configured = workerCallAdapter.configureServiceRole(hostname, cfg);
    if (configured == null || !Boolean.TRUE.equals(configured.getExecResult())) {
        log.warn("otelcol configure failed on {}, skip restart", hostname);
        return configured != null ? configured : fail();
    }
    ServiceRoleOperateCommand op = new ServiceRoleOperateCommand();
    op.setCommandType(CommandType.RESTART_SERVICE);
    return workerCallAdapter.restartServiceRole(hostname, op);
}

private static ExecResult fail() {
    ExecResult e = new ExecResult();
    e.setExecResult(false);
    return e;
}
```

> `CommandType.RESTART_SERVICE` 以实际枚举为准(实现时 `grep CommandType` 取重启项);若名称不同,按枚举改正。

- [ ] **Step 4: 运行看通过**

Run: 同 Step 2。
Expected: PASS(两测试均绿)。

- [ ] **Step 5: 提交**

```bash
git add datasophon-api/src/main/java/com/datasophon/api/observability/OtelCollectorConfigService.java \
        datasophon-api/src/test/java/com/datasophon/api/observability/OtelCollectorConfigServiceTest.java
git commit -m "feat(observability): OTELCOLLECTOR 配置下发+重启主干(顺序保证+失败短路)"
```

---

## Task 3: REST 触发端点(thin)

**Files:**
- Create: `.../controller/observability/OtelCollectorController.java`
- Test: `.../observability/OtelCollectorControllerTest.java`

**Interfaces:**
- Produces:`POST /api/observability/otelcol/push?clusterId=&hostname=` → 调 `pushNodeConfig`,返回统一 `Result`。

- [ ] **Step 1: 写失败测试(thin controller 委托)**

```java
package com.datasophon.api.observability;

import com.datasophon.common.utils.ExecResult;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OtelCollectorControllerTest {

    @Test
    void push_delegates_to_service() {
        OtelCollectorConfigService svc = mock(OtelCollectorConfigService.class);
        ExecResult ok = new ExecResult(); ok.setExecResult(true);
        when(svc.pushNodeConfig(eq(1), eq("app1"), any())).thenReturn(ok);

        OtelCollectorController c = new OtelCollectorController(svc);
        var result = c.push(1, "app1");

        assertTrue(result.getCode() == 200 || Boolean.TRUE.equals(result.getData()));
        verify(svc).pushNodeConfig(eq(1), eq("app1"), any());
    }
}
```

> 断言按本项目 `Result` 实际成功判定字段调整(`getCode()`/`isSuccess()` 等),实现时对齐既有 controller 返回风格。

- [ ] **Step 2: 运行看失败 → Step 3 写 thin controller → Step 4 看通过**

```java
package com.datasophon.api.controller.observability;

import com.datasophon.api.observability.OtelCollectorConfigService;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.Result;

import java.util.HashMap;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/observability/otelcol")
public class OtelCollectorController {

    private final OtelCollectorConfigService configService;

    public OtelCollectorController(OtelCollectorConfigService configService) {
        this.configService = configService;
    }

    @PostMapping("push")
    public Result push(@RequestParam Integer clusterId, @RequestParam String hostname) {
        ExecResult r = configService.pushNodeConfig(clusterId, hostname, new HashMap<>());
        return Boolean.TRUE.equals(r.getExecResult()) ? Result.success() : Result.error("otelcol 配置下发失败");
    }
}
```

> 测试类的 import 路径随 controller 实际包名调整。Run 同上命令(`-Dtest=OtelCollectorControllerTest`),先红后绿。

- [ ] **Step 5: 提交**

```bash
git add datasophon-api/src/main/java/com/datasophon/api/controller/observability/OtelCollectorController.java \
        datasophon-api/src/test/java/com/datasophon/api/observability/OtelCollectorControllerTest.java
git commit -m "feat(observability): OTELCOLLECTOR 配置下发 REST 触发端点(thin)"
```

---

## A3a 完成定义

- [ ] `buildConfigCommand` 产出含 otelcol.yaml + otelcol.env 双 generator 的命令(Task 1)
- [ ] `pushNodeConfig` 保证 configure→restart 顺序、configure 失败不重启(Task 2)
- [ ] REST 端点 thin 委托(Task 3)
- [ ] 追溯:§4.3 配置下发主干闭环;为 A3d 切换提供"按节点下发"原语
- [ ] 真实下发(gRPC 到节点 + 重启)待真实 Worker 环境

## 衔接后续 sub-plan

- **A3b 监控**:轮询各节点 `:8888` self-metrics → 健康/吞吐/队列/落盘量 API(§5.4 地基,不依赖 Doris)。
- **A3c 告警器(F6)**:`@Scheduled` 拉 self-metrics,队列水位/丢弃超阈走 `ClusterAlertHistory`+通知通道;独立于 Doris/Phase B。
- **A3d 切换+回灌(F5/F2/F1/§5.2)**:exporterMode 参数(s3|doris)经 A3a 主干逐节点下发;DORIS 角色达 RUNNING 触发 `OtelSchemaApplier.apply`(A2)+ 按集群生成 otel_collector 口令注入 otelcol.env;按节点产生首条 Doris 写入记 ack 边界;`awss3receiver` 时间窗 [节点起点, 节点ack) 回灌。
- **A3e UI**:控制台配置 tab(基础旋钮由 service_ddl configFields 自动渲染 + YAML 兜底)+ 监控 tab(消费 A3b API);ProTable/ProForm,遵循 antd-pro 规则。

