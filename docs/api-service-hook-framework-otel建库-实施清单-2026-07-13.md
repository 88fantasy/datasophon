# 服务级(API 侧)Hook 框架 + Doris otel 建库改造 —— Codex 实施清单

## Context(为什么做)

Datasophon 现有的 hook 机制**只作用在角色级、在 Worker 端执行**:`service_ddl.json` 的每个 `role` 里声明 `hooks: [{type, condition, action, params}]`(`ServiceRoleInfo.hooks`),命令下发后由 Worker 的 `HookAction` SPI + `HookUtils`(`HOOK_MAP` action→类、SpEL `condition`)在角色安装时执行(如 `download` / `append_line` / `nacosSync` / `initDb`)。

但有一类动作**只能在 Master(API)进程完成**——典型是 OTel→Doris 建库:otel_collector/otel_reader 的随机密码由 API 进程的 `OtelCredentialService`(`SecureRandom`)持有并存 `ClusterVariable`,Worker 不是 Spring 进程、拿不到。当前(未提交的工作树)是在 `DAGExecutor.createMultiServiceDAG` 的匿名 `DAGListener.onNodeSuccess` 里**硬编码** `if "DORIS".equals(serviceName) && INSTALL_SERVICE` 就调 `OtelSchemaOrchestrator.applyIfReady(clusterId)`。硬编码不可扩展。

**目标**:新增一套**服务级、在 API 端执行**的 hook 框架,作为 Worker 角色级 hook 的对称镜像;把 Doris 建库改造成该框架的一个声明式 hook。要求:① 复用 `HookType` 枚举;② 声明方式镜像 Worker——在 `service_ddl.json` **顶层**声明;③ 本次只接入 `POST_INSTALL` 阶段(够用即可,其余阶段留作已文档化的扩展点)。

> 交付物:本清单交 Codex 编码;Claude 负责审核。

## 现有关键事实(Codex 必读,勿臆测)

- Worker 角色 hook 声明范式(镜像对象):`package/raw/meta/datacluster-physical/NACOS/service_ddl.json` 的 role 内 `"hooks":[{"type":"POST_INSTALL","action":"download","params":{...}}]`。
- `HookConfig`(`datasophon-common/.../model/HookConfig.java`)= `{HookType type, String condition, String action, Map<String,Object> params}`,**直接复用,不改**。
- `HookType`(`datasophon-common/.../enums/HookType.java`)= `PRE_INSTALL/POST_INSTALL/PRE_START/POST_START/PRE_STOP/POST_STOP`,**复用,不改**;枚举值不可重排(CLAUDE.md 约定 5)。
- Worker 侧过滤+SpEL 参考实现:`HookUtils.getMatchedHooks(hooks,type)`、`HookUtils.isHookEnable(condition,params)`(`datasophon-worker/.../hook/HookUtils.java`);`ServiceRoleInfo.getMatchedHooks(HookType...)`。**API 不能依赖 datasophon-worker**,SpEL 过滤需在 API 侧自带一小段等价实现。
- API 侧建库现成入口(复用,不改逻辑):`OtelSchemaOrchestrator.applyIfReady(Integer clusterId)`(`datasophon-api/.../observability/OtelSchemaOrchestrator.java`)——内部已判 `isDorisReady`(FE/BE 未 RUNNING 则安全空转)、取随机密码、`MetaStorage` 从 Nexus 读 SQL、JDBC 幂等应用。前端 `OtelCollectorController.push()` 继续作为手动兜底入口。
- DAG 节点载体:`ServiceNode`(`datasophon-common/.../model/ServiceNode.java`)= serviceName / masterRoles / workerRoles / clientRoles / commandId / commandType。**无 clusterId 字段**——clusterId 从任一角色 `ServiceRoleInfo.getClusterId()` 取。
- `ServiceNode` 构造点:`PhysicalProductInstallServiceImpl`(`datasophon-api/.../service/extrepo/impl/`)约第 322–377 行,`ServiceInfo serviceInfo = JSONObject.parseObject(serviceEntity.getServiceJson(), ServiceInfo.class)`——即 DDL 顶层字段会被解析进 `ServiceInfo`;随后 `node.setNodeConfig(JSONObject.toJSONString(serviceNode))` 序列化。
- DAG listener 分发点:`DAGListener.onNodeCompleted` 按 `NodeStatus` 分派到 `onNodeSuccess/onNodeFail/onNodeCancel`;`DAGExecutor.createMultiServiceDAG` 注册匿名 listener(约第 166 行)。已确认 DORIS 的 FE+BE 属**同一 service DAG 节点**内的不同角色,`onNodeSuccess(INSTALL_SERVICE)` 触发时二者必已装完。
- 范围:**仅物理集群**(`DAGExecutor` / `ServiceNode`)。K8s 侧(`K8SDAGExecutor` / `K8sServiceNode`)不在本次范围。

## 起始状态(工作树,未提交)

本任务**建立在上一批未提交改动之上**:SQL 已从 `datasophon-api/src/main/resources/observability/doris/` 迁到 `package/raw/meta/datacluster-physical/DORIS/sql/`,`OtelSchemaApplier` 已改用 `MetaStorage` 读取,`DAGExecutor` 里已有**硬编码** DORIS 分支。**本任务要把该硬编码删掉,替换成通用框架分发。** SQL 迁移与 `OtelSchemaApplier` 的 `MetaStorage` 改动保留不动。

## 设计(API 服务级 hook 框架)

对称镜像,但适配 Spring:Worker 用反射 `HOOK_MAP`,API 用 **Spring bean 自动收集**。

|  维度  |                  Worker 角色级(现有)                  |                          API 服务级(新增)                           |
|------|--------------------------------------------------|----------------------------------------------------------------|
| 声明位置 | `service_ddl.json` → role → `hooks`              | `service_ddl.json` → **顶层** → `serviceHooks`                   |
| 内存模型 | `ServiceRoleInfo.hooks`                          | `ServiceInfo.serviceHooks` → `ServiceNode.serviceHooks`        |
| SPI  | `HookAction{getType, invoke(HookContext)}` 反射实例化 | `ServiceHook{getType, invoke(ServiceHookContext)}` Spring bean |
| 注册表  | `HookUtils.HOOK_MAP`                             | 分发器注入 `List<ServiceHook>` 建 `Map<action,bean>`                 |
| 触发   | Worker 角色安装时                                     | Master `DAGExecutor` listener `onNodeSuccess`                  |
| 本次阶段 | 多阶段                                              | **仅 `POST_INSTALL`**                                           |

## 任务清单(逐文件)

### T1 — `ServiceInfo` 增字段(datasophon-common)

`datasophon-common/.../model/ServiceInfo.java`:新增 `private List<HookConfig> serviceHooks;`(Lombok `@Data` 自动 getter/setter)。import `HookConfig`。

> CLAUDE.md 约定 4:common 模型变更须同步消费方——此字段仅新增、可空,旧 DDL 无 `serviceHooks` 时解析为 null,向后兼容。

### T2 — `ServiceNode` 增字段(datasophon-common)

`datasophon-common/.../model/ServiceNode.java`:新增 `private List<HookConfig> serviceHooks;`。可加便捷方法 `getMatchedServiceHooks(HookType type)`(仿 `ServiceRoleInfo.getMatchedHooks`,`type` 过滤 + null 安全),供分发器用。

### T3 — 构造点填充(datasophon-api)

`PhysicalProductInstallServiceImpl.java`,在设置 `masterRoles/workerRoles/clientRoles` 之后(约第 373 行 `serviceNode.setClientRoles(...)` 之后、`setNodeConfig` 之前):

```java
serviceNode.setServiceHooks(serviceInfo.getServiceHooks());
```

### T4 — API 侧 SPI 与上下文(datasophon-api,新包 `com.datasophon.api.hook`)

- `ServiceHook.java`(接口):

  ```java
  public interface ServiceHook {
      String getType();                         // 对应 HookConfig.action
      void invoke(ServiceHookContext ctx) throws Exception;
  }
  ```
- `ServiceHookContext.java`(POJO,`@Data`):`String serviceName; Integer clusterId; CommandType commandType; String commandId; Map<String,Object> params;` + `getParamsAs(Class)`(仿 `HookContext.getParamsAs`,用 hutool `BeanUtil.toBean`)与 `Map<String,Object> toConditionMap()`(serviceName/commandType/clusterId + params 展开,供 SpEL)。

### T5 — 分发器(datasophon-api `com.datasophon.api.hook`)

`ServiceHookDispatcher.java`,`@Component`,构造器注入 `List<ServiceHook> hooks`,`@PostConstruct` 建 `Map<String,ServiceHook> byAction`(按 `getType()`;重复 action 抛错快速失败)。方法:

```java
public void dispatch(ServiceNode node, HookType type) {
    List<HookConfig> matched = node.getMatchedServiceHooks(type); // null 安全
    if (matched.isEmpty()) return;
    Integer clusterId = firstClusterId(node);                     // masterRoles→workerRoles→clientRoles 第一个非空角色的 getClusterId()
    for (HookConfig cfg : matched) {
        try {
            if (!isEnabled(cfg.getCondition(), conditionMap)) continue; // SpEL,空 condition→true(仿 HookUtils.isHookEnable,API 侧自带实现)
            ServiceHook hook = byAction.get(cfg.getAction());
            if (hook == null) { log.warn("未知 serviceHook action: {}", cfg.getAction()); continue; }
            hook.invoke(buildContext(node, clusterId, cfg));
        } catch (Exception e) {
            log.warn("服务级 hook 执行失败 service={} action={} type={}，不影响 DAG 状态", node.getServiceName(), cfg.getAction(), type, e);
        }
    }
}
```

`firstClusterId` 从 `DAGExecutor` 迁入本类(逻辑不变)。`isEnabled` 用 Spring `SpelExpressionParser` + `StandardEvaluationContext`(与 `HookUtils.isHookEnable` 等价,API 侧不依赖 worker,自带 ~8 行)。**任何 hook 异常只 warn 不外抛**——服务安装本身已成功。

### T6 — Doris 建库 hook 实现(datasophon-api `com.datasophon.api.observability`)

`OtelSchemaInitHook.java`,`@Component implements ServiceHook`,构造器注入 `OtelSchemaOrchestrator`:

```java
@Override public String getType() { return "otelSchemaInit"; }
@Override public void invoke(ServiceHookContext ctx) {
    if (ctx.getClusterId() == null) return;
    orchestrator.applyIfReady(ctx.getClusterId());   // 复用:isDorisReady 空转 + 随机密码 + Nexus 读 SQL + JDBC 幂等
}
```

### T7 — DAGExecutor 去硬编码、接分发器(datasophon-api)

`DAGExecutor.java`:
- **删除**:`applyOtelSchemaIfDoris(...)`、`firstClusterId(...)` 两个私有方法;字段 `private final OtelSchemaOrchestrator otelSchemaOrchestrator;` 及其 import。
- **新增**:字段 `private final ServiceHookDispatcher serviceHookDispatcher;`(`@RequiredArgsConstructor` 自动注入)。
- `onNodeSuccess` 改为通用分发:

```java
@Override public void onNodeSuccess(NodeDefinition node, String result) {
    ServiceNode serviceNode = JSONObject.parseObject((String) node.getNodeConfig(), ServiceNode.class);
    if (INSTALL_SERVICE.equals(serviceNode.getCommandType())) {
        serviceHookDispatcher.dispatch(serviceNode, HookType.POST_INSTALL);
    }
}
```

import `HookType`。`onNodeFail/onNodeCancel` 不动。

### T8 — DORIS DDL 顶层声明(package)

`package/raw/meta/datacluster-physical/DORIS/service_ddl.json`,顶层(与 `roles` 同级)新增:

```json
"serviceHooks": [
  { "type": "POST_INSTALL", "action": "otelSchemaInit" }
]
```

> 无 `condition`/`params`——Doris 建库无条件、无参数,clusterId 由分发器从节点角色推出。

### T9 — 测试(datasophon-api)

新增 `ServiceHookDispatcherTest`(纯单测,Mockito):
1. `ServiceNode` 含 `serviceHooks=[{POST_INSTALL, otelSchemaInit}]` + 一个 mock `ServiceHook`(getType="otelSchemaInit"),`dispatch(node, POST_INSTALL)` → mock.invoke 被调 1 次,且 ctx.clusterId 来自角色。
2. `dispatch(node, POST_START)`(不匹配)→ 不调用。
3. 未知 action → 只 warn 不抛。
4. hook.invoke 抛异常 → 被吞、不外抛。
5. `condition="false"` → 跳过;`condition` 为空 → 执行。

> `OtelSchemaContractTest`/`OtelSchemaApplierTest`/`OtelSchemaOrchestratorTest` **不改**(SQL 读取路径已在上一批迁移,本任务不触碰建库内部逻辑)。

## 需要明确记录的权衡

- **DDL 变更的生效前提**:`serviceHooks` 随 `service_ddl.json` 经 `datasophon-cli upload registry` 上传 Nexus、`LoadServiceMeta` 解析入库。已装集群需重新加载 DDL 才带上新 hook,与任何 DDL 变更一致。
- **建库的运行期依赖**:延续上一批——`applyIfReady` 经 `MetaStorage` 从 Nexus 读 SQL,**Nexus 须启用且可达**;不可达时 hook warn 记录,前端 push 接口兜底。
- **仅 POST_INSTALL**:分发器按 `HookType` 通用匹配,但只在 `onNodeSuccess+INSTALL_SERVICE` 接了 `POST_INSTALL`。要加 `POST_START/POST_STOP` 只需在 `onNodeSuccess` 按 `commandType` 多映射几条;`PRE_*` 需在 `onNodeStarted` 接入。留作扩展点,本次不实现。
- **不改动**:`OtelCredentialService`、`OtelSchemaOrchestrator.applyIfReady` 逻辑、`OtelCollectorController.push()`、Worker 角色级 hook 全链路。

## 关键文件

- 新增:`datasophon-api/.../hook/{ServiceHook,ServiceHookContext,ServiceHookDispatcher}.java`、`datasophon-api/.../observability/OtelSchemaInitHook.java`、`datasophon-api/src/test/.../hook/ServiceHookDispatcherTest.java`
- 修改:`datasophon-common/.../model/{ServiceInfo,ServiceNode}.java`、`datasophon-api/.../master/DAGExecutor.java`、`datasophon-api/.../service/extrepo/impl/PhysicalProductInstallServiceImpl.java`、`package/raw/meta/datacluster-physical/DORIS/service_ddl.json`
- 只读参考:`datasophon-worker/.../hook/{HookAction,HookUtils,HookContext}.java`、`datasophon-common/.../model/{HookConfig,ServiceRoleInfo}.java`、`datasophon-api/.../observability/OtelSchemaOrchestrator.java`、`datasophon-api/.../dag/DAGListener.java`

## 验证(Codex 编码后自测,Claude 复核)

1. `./mvnw -pl datasophon-common -am compile`:common 两个模型新字段编译通过。
2. `./mvnw -pl datasophon-api -am compile`:`ServiceHookDispatcher` bean 收集、`DAGExecutor` 去字段/加字段的构造器注入通过;确认无残留 `otelSchemaOrchestrator`/`applyOtelSchemaIfDoris` 引用。
3. `python3 -m json.tool package/raw/meta/datacluster-physical/DORIS/service_ddl.json`:DDL 仍是合法 JSON。
4. `./mvnw -pl datasophon-api -Dtest=ServiceHookDispatcherTest test`:新单测全绿。
5. `./mvnw -pl datasophon-api -Dtest='OtelSchema*Test' test`:既有 otel 契约测试无回归。
6. `./mvnw -pl datasophon-api spotless:apply` 后再跑一次编译(本仓库 Edit 常留行尾空白触发 Spotless/Checkstyle 失败)。
7. **已知验证缺口(如实告知,不假装完成)**:DORIS 真实安装 → `onNodeSuccess` → `ServiceHookDispatcher` → `otelSchemaInit` → Nexus 拉 SQL → JDBC 真实建库这条端到端链路,本地无真实 Nexus + Doris 集群,不可验证;须记入 `apply-verify.md`,不在提交里声称已验证。

完成以上核验后，再进入真实环境验收。
