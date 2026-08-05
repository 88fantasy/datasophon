# APISIX(standalone) 网关配置 Tab —— 任务执行清单

> 状态图例：⬜ 未开始 / 🔄 进行中 / ✅ 已完成 / ⛔ 阻塞
> **每完成一个任务，立即回写本文件对应行的状态与完成日期**，中断后从第一个非 ✅ 的任务继续，已完成任务不返工。

## 0. Context

在 APISIX(standalone) 服务详情页增加一个 Tab，以**图形化 + 代码化双视图**管理网关配置（Routes / Upstreams / Global Rules），形态模仿 APISIX 原生 Dashboard。

**为什么不代理原生 Dashboard**：`deploy/deployment-standalone-doris.md` §7.9.1（2026-08-04 ddh-01 实机验证 V1–V6）已判死刑——standalone 的 `admin/init.lua` 只注册 3 个只读端点，原生 Dashboard 首屏请求 `GET /apisix/admin/services` 直接 404，**崩在 React 错误边界连登录页都到不了**；`PUT /apisix/admin/configs` 返回 202 但 `apisix.yaml` sha256 不变（不落盘），重启即丢。该节给出的替代路线原文即：「DataSophon 自建配置 Tab，复用现有 `apisix-routes.ftl` 模板热加载链路」。

**已拍板的四点**：① 可读写的真正配置管理；② 图形化首期覆盖 Routes + Upstreams + Global Rules；③ 靠 APISIX 自身**文件热加载**生效，**不重启网关**；④ 图形化之外再提供代码化（YAML）视图，两者可自由切换。

## 1. 方案要点

**真相源 = 新增隐藏 DDL 参数 `apisixGatewayYaml`（YAML 文本）**。代码视图直接编辑它；图形化视图是它 `js-yaml.load()` 后的结构化投影。切换 = `load`/`dump`，不是两套数据。

```
Tab (ApisixGatewayPanel)   ┌─ 图形化: doc.routes/upstreams/global_rules → 三张 ProTable
  状态 text + doc ─────────┤
                           └─ 代码化: Monaco 编辑 text
   读 getServiceConfig → apisixGatewayYaml → text，load 出 doc
   写 当前视图为准 → POST 新端点
        ↓
  后端: 校验 YAML → 复用 saveServiceConfig 写 configJson → 有条件复位 needRestart
      → 仅对 apisix.yaml 这个 generator 调 ServiceLifecycleUtils.configServiceRoleInstance（不停不启）
        ↓
  Worker: ConfigureServiceHandler → FreemakerUtils(custom) → apisix-routes.ftl
        ↓
  /usr/local/apisix/conf/apisix.yaml → APISIX 定期重读文件自动生效
```

**为什么真相源是 YAML 文本而非结构化 JSON**：APISIX 的 route 天然嵌套（`plugins.limit-count.count`），而 DDL 现有集合类型 `multipleWithMap` 只能表达**扁平** map（NGINX 的 `directives` 就是被迫退化成分号串 + `?split(";")`）。YAML 文本让模板只剩一行 `${apisixGatewayYaml}`，避开 FreeMarker 递归宏与缩进控制，并让代码视图变成零成本。

## 2. 关键事实（已核实，不要推翻重查）

|  #  |                                                                                                            事实                                                                                                             |                                 出处                                 |
|-----|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------|
| F1  | `configType: "map"` 的参数以变量名注入 FreeMarker 顶层；其余落进 `itemList`                                                                                                                                                               | `FreemakerUtils.renderCustomConfigFormat:197-219`                  |
| F2  | Worker `replacePlaceholder` 的 switch 只覆盖 `input`/`multiple`/`multipleWithMap`，**`type:"textarea"` 完全跳过归一化**；只有 `Boolean`/`Integer` 会被 `toString()` → 长文本原样透传                                                              | `ConfigureServiceHandler.configure:99-158`                         |
| F3  | 「多行原文整体注入模板」已有成熟先例：OTELCOLLECTOR `rawYaml`（`type:textarea`+`configType:map`+`hidden:true`），模板 `<#if (rawYaml!"")?has_content>${rawYaml}<#else>…`                                                                          | `OTELCOLLECTOR/service_ddl.json:137-147`、`templates/otelcol.ftl:1` |
| F4  | 前端 `ConfigForm.tsx` 对 `textarea` 走 `return null` **不渲染** → 隐藏参数不会污染「配置」Tab                                                                                                                                                | `ConfigForm.tsx:125,274`                                           |
| F5  | `saveServiceConfig` 每次 INSERT 新版本并**无条件打 `needRestart`** 三处（roleInstance/roleGroup/serviceInstance）                                                                                                                       | `ServiceInstallServiceImpl.java:188-250`                           |
| F6  | **已有「只 configure 不重启」原语** `ServiceLifecycleUtils.configServiceRoleInstance(clusterInfo, configFileMap, roleInstance)`，无 REST 端点；现有调用方 `ClusterDeleteService.java:215`、`OtelCollectorConfigService.pushNodeConfig:120-139` | `ServiceLifecycleUtils.java:112-123`                               |
| F7  | 前端已依赖 `js-yaml ^4.2.0`、`@monaco-editor/react ^4.7.0`，`Setting/HelmEditor.tsx` 有现成 Monaco+YAML 用法可复用                                                                                                                       | `package.json:41,51`                                               |
| F8  | `apisix.yaml` 末尾 `#END` 是 standalone 解析终止符，必须单独占末行                                                                                                                                                                        | `apisix-routes.ftl:36`、`ApisixStandaloneTemplateTest`              |
| F9  | 服务详情页 Tab 是 if/else 链非映射表，`hasPrimaryMonitor` 分支在 `index.tsx:175-207`                                                                                                                                                     | `Cluster/ServiceInstance/index.tsx`                                |
| F10 | i18n 无 glob 自动加载，新命名空间须在 `locales/zh-CN.ts` 与 `locales/en-US.ts` **两处**注册，漏一处整个命名空间失效                                                                                                                                     | —                                                                  |

## 3. 双视图的三条设计约束（最易出 bug 处）

1. **图形化只投影它认识的部分，其余原样保留**。状态模型是一个完整 `doc` 对象，图形化只读写 `doc.routes`/`doc.upstreams`/`doc.global_rules`；其它顶层段（用户在代码视图写的 `consumers`、`ssls`）与单条 route 上的未知字段全部留在 `doc` 里，`dump` 时自然带出。表单提交用 `{...originalItem, ...formValues}` **合并而非替换**。
2. **注释在 `load`→`dump` 往返中必然丢失**（js-yaml 固有行为，不是可修 bug，也不引入 `yawn-yaml` 等库）。用**脏标记**压损失：图形化未真正改动时切回代码视图**直接复用原始 `text` 不 dump**；已改动且原文含注释则先弹二次确认。
3. **代码 → 图形化可能失败**。切换前先 `load` 试解析，失败则**拦在代码视图**并在 Monaco 标出错误行，不静默降级。

## 4. 任务清单

> 依赖关系：`T0 → T1 → T2`；`T1 → T3/T4`；`T5 → T6 → T7`；`T6 → T8 → T9`；全部 → `T10`。
> T3/T4（后端）与 T5–T9（前端）之间无代码依赖，可并行。

| ID  |               任务               | 状态 |    完成日期    |
|-----|--------------------------------|----|------------|
| T0  | 热加载实测（**硬前置**）                 | ✅  | 2026-08-05 |
| T1  | DDL 新增 `apisixGatewayYaml` 参数  | ✅  | 2026-08-05 |
| T2  | `apisix-routes.ftl` 双分支 + 模板测试 | ✅  | 2026-08-05 |
| T3  | 后端 GET 端点                      | ✅  | 2026-08-05 |
| T4  | 后端 POST 端点（校验 + 保存 + 只下发不重启）   | ✅  | 2026-08-05 |
| T5  | 前端 i18n + API 封装               | ✅  | 2026-08-05 |
| T6  | 前端 Panel 骨架 + 代码视图             | ✅  | 2026-08-05 |
| T7  | 前端 Tab 挂载                      | ✅  | 2026-08-05 |
| T8  | 前端图形化三张表                       | ✅  | 2026-08-05 |
| T9  | 前端视图切换状态机                      | ✅  | 2026-08-05 |
| T10 | 沙箱端到端验收                        | ✅  | 2026-08-05 |

---

### T0 · 热加载实测（硬前置，无代码产出）

**为什么必须先做**：整个「不重启」前提建立在 APISIX standalone `config_provider: yaml` 会定期重读 `apisix.yaml` 上。本项目**从未实测过**（§7.9.1 只验了 Admin API 那条路）。不成立则 T1–T10 的生效方式全部需要重新设计。

**做法**：沙箱 ddh-01（`192.168.10.131`，APISIX 3.17.0 唯一在跑节点）。先 `cp` 备份 `config.yaml`/`apisix.yaml` 并记录 sha256；SSH 手工往 `apisix.yaml` 加一条 route（保持 `#END` 在末行）；`curl` 轮询新路径并计时；结束后还原 `.bak` 并校验 sha256 回到基线。

**验收**：不重启 apisix 即可命中新路由，且**记录实测生效延迟**（写入本文件）。
**若不通过**：⛔ 阻塞全部后续任务，回头与用户重新选生效方式（沿用现有链路 = 保存后手动重启角色）。

**实测结果（2026-08-05，ddh-01）**：
- 备份 `apisix.yaml`/`config.yaml`，基线 sha256 = `2f903f8e...df569` / `7f6e6b20...4d15c99`
- 向 `routes` 追加 `id:2 uri:/ddh-t0-test upstream_id:1`（`#END` 仍在末行），未重启/未 reload 命令
- 轮询 `curl http://127.0.0.1:9080/ddh-t0-test`：**首次探测（≤0.5s 轮询间隔内）即从 APISIX 自身 `{"error_msg":"404 Route Not Found"}`（未匹配）变为后端 Jetty 的 404 Not Found（已匹配路由，转发到 8080 后端）**，证明 standalone 模式确有文件热加载，且延迟在亚秒级
- `systemctl show apisix -p ExecMainStartTimestamp` 前后一致（`Tue 2026-08-04 22:07:58 CST`），确认无重启
- 还原 `.bak`，两文件 sha256 均回到基线，`/ddh-t0-test` 复核已变回未匹配响应
- **结论：T0 通过，热加载路线成立，T1–T10 按计划继续**

---

### T1 · DDL 新增 `apisixGatewayYaml` 参数

**文件**：`package/raw/meta/datacluster-physical/APISIX/service_ddl.json`

`parameters` 新增（字段组合完全照抄 OTELCOLLECTOR `rawYaml`）：

```json
{
  "name": "apisixGatewayYaml",
  "label": "网关配置（由网关配置 Tab 管理）",
  "description": "非空时替换 upstreams/routes/global_rules 段，由 UI 编辑生成",
  "required": false, "enabled": true,
  "type": "textarea", "configType": "map",
  "defaultValue": "", "configurableInWizard": false, "hidden": true
}
```

并把 `apisixGatewayYaml` 追加进 `configWriter.generators[filename=apisix.yaml].includeParams`（**原有 3 个参数保留**，供 T2 的回退分支使用）。

⚠️ `Generators.equals/hashCode` 只按 `filename` 比较，**不要新增重名 generator**。

**验收**：JSON 可解析；`hidden:true` + `type:textarea` 组合确保它不出现在「配置」Tab（F4）。

---

### T2 · `apisix-routes.ftl` 双分支 + 模板测试

**文件**：`package/raw/meta/datacluster-physical/APISIX/templates/apisix-routes.ftl`、`datasophon-worker/src/test/java/com/datasophon/worker/test/ApisixStandaloneTemplateTest.java`

模板改为两分支；`plugin_metadata` 段与 `#END` **保持模板固定输出**（不交给 UI，避免 OTel collector 地址被误删导致链路追踪静默失效）：

```ftl
<#if (apisixGatewayYaml!"")?has_content>
${apisixGatewayYaml}
<#else>
（现有 upstreams / routes / global_rules 三段原样不动）
</#if>

plugin_metadata:
  - id: opentelemetry
    ...（现有内容原样保留，含那两行解释性注释）
#END
```

`global_rules` 落在可编辑分支内（用户要求可编辑），由 T4 的后端校验兜底。

**验收**：`ApisixStandaloneTemplateTest` 新增两个用例覆盖两分支（非空时直出且 `#END` 仍在末行 / 为空时回退旧行为逐字节一致），**现有全部断言保留不改**。

```bash
export JH21=/Users/pro/Library/Java/JavaVirtualMachines/graalvm-jdk-21.0.7/Contents/Home
JAVA_HOME=$JH21 ./mvnw -pl datasophon-worker -am test -s ~/.m2/setting.xml -Dtest=ApisixStandaloneTemplateTest
```

---

### T3 · 后端 GET 端点

**文件**：新建 `datasophon-api/src/main/java/com/datasophon/api/controller/v2/ApisixGatewayV2Controller.java` + `service/ApisixGatewayConfigService.java`

`GET /v2/cluster/{clusterId}/service/instance/{instanceId}/apisix/gateway` → 返回 `{ gatewayYaml, managedSuffix, roles[] }`：

- `gatewayYaml`：当前 `apisixGatewayYaml`；**为空则用 3 个向导参数（`apisixRouteUri`/`apisixUpstreamHost`/`apisixUpstreamPort`）拼出初始 YAML**，让用户首次打开就有内容可编辑
- `managedSuffix`：模板固定输出的托管段文本（`plugin_metadata` + `#END`），供代码视图做「最终文件预览」。**由后端提供而非前端硬编码**，避免与模板漂移
- `roles[]`：各 Apisix 角色实例 hostname/状态

复用：`ClusterServiceInstanceConfigServiceImpl.getServiceInstanceConfig` 读 configJson、`ClusterServiceRoleInstanceService.getServiceRoleInstanceListByClusterIdAndRoleName(clusterId, "Apisix")`。

**验收**：单测覆盖「参数为空 → 返回向导参数拼出的初始 YAML」「参数非空 → 原样返回」。

---

### T4 · 后端 POST 端点（校验 + 保存 + 只下发不重启）

**文件**：同 T3 两个类

`POST /v2/cluster/{clusterId}/service/instance/{instanceId}/apisix/gateway`，body `{ gatewayYaml: string }`：

1. 校验 `serviceName == "APISIX"`，否则 400
2. **YAML 校验**（用已有 snakeyaml / Jackson YAML）：
   - 可解析且顶层是 map
   - 顶层键走**黑名单而非白名单**：只禁 `plugin_metadata`（与模板托管段重复会导致 APISIX 解析异常）。其余 standalone 支持的段（`consumers`/`ssls`/`services`/`stream_routes`…）**一律放行**——代码视图的意义就是补足图形化没覆盖的高级配置，白名单会把它堵死
   - **必须含 prometheus 与 opentelemetry 两条 global_rules**，否则 400（防 UI bug 或手写失误打断监控与链路追踪的廉价保险）
   - 禁止出现 `#END`（终止符由模板统一输出，提前出现会截断后续内容）
3. 记录调用前 `serviceInstance.needRestart`
4. 复用 `ServiceInstallServiceImpl.saveServiceConfig` 写入（保证 configJson/configFileJson/MD5/版本号与「配置」Tab 完全一致）
5. **若第 3 步为 `NO` 才复位**三处 `needRestart`（APISIX 热加载不需重启；本来就是 YES 说明用户另改过端口等真需重启项，**不清**，否则会掩盖它）
6. 取全部 `RUNNING` 的 `Apisix` 角色实例，逐个调 `ServiceLifecycleUtils.configServiceRoleInstance`，**configFileMap 只保留 `filename == "apisix.yaml"` 的 generator**（不重写 `config.yaml`，避免误触发需重启项）
7. 返回每节点下发结果

参照现成写法：`DAGExecutor.createConfigFileMap:383-413`、`OtelCollectorConfigService.pushNodeConfig:120-139`。

**验收**：单测覆盖 YAML 校验四条规则的正反例 + needRestart 有条件复位逻辑。
⚠️ 新增 `@SpringBootTest` **必须加 `@DirtiesContext`**，否则与其他上下文争抢 gRPC 18081（已知坑，表象常被误判为 MySQL 连接问题）。本任务未用 `@SpringBootTest`，改用纯 Mockito 单测（`ApisixGatewayConfigServiceTest`），规避该坑。

**实现中发现并修复的计划外缺口**：`ServiceInstallService#getServiceConfigOption` 对已安装实例只回放上次持久化的 `configJson`（`listServiceConfigByServiceInstance` 逐字节 `JSONArray.parseArray`），**不会**合并 DDL 新增但从未保存过的参数。沙箱 ddh-01 的 APISIX 实例早于 `apisixGatewayYaml` 参数存在，若不处理，GET/POST 会因为列表里根本没有这一项而静默失效（POST 写不进任何值）。已在 `ApisixGatewayConfigService.loadEffectiveConfigs` 加了兜底：找不到该参数时从 `getServiceConfigFromDdl` 补一份空值条目再继续。

---

### T5 · 前端 i18n + API 封装

**文件**：新建 `datasophon-ui-v2/src/locales/{zh-CN,en-US}/apisixGateway.ts` + 配套 `.test.ts`；改 `src/locales/zh-CN.ts`、`src/locales/en-US.ts`；改 `src/services/service.ts`

- 键统一前缀 `pages.apisixGateway.`；zh/en 两份键完全对齐；`.test.ts` 遍历断言每键存在且非空（沿用 `apisixMonitor.test.ts` 的写法）
- **两处注册都要改**（F10）
- API 封装沿用 `baseURL='/ddh/api/v2'`，函数放 `service.ts`（勿动 `src/services/ant-design-pro/` 生成目录）

**验收**：`npm run lint` + `npm run test` 绿。

---

> **实现顺序说明**：T6/T8/T9 在实现时一并完成——`index.tsx` 的状态机（load/dump 切换、脏标记、注释二次确认）与 `GraphicView` 三张表天然是同一份状态的两个消费者，分三次改同一个文件返工成本更高，故一次性写完再补齐三批测试（`gatewayYaml.test.ts` 13 例、`index.test.tsx` 7 例覆盖状态机四行切换、`GlobalRuleTable.test.tsx` 覆盖内置规则不可删）。

### T6 · 前端 Panel 骨架 + 代码视图

**文件**：新建 `src/pages/Cluster/ServiceInstance/ApisixGateway/{index.tsx, CodeView.tsx, gatewayYaml.ts}` + 测试

放在 `ServiceInstance/` 下而非 `pages/monitor/`——它是服务详情页专属的配置管理，不是监控看板。

- `index.tsx` = `ApisixGatewayPanel`：顶部 `Segmented` 视图切换 + 保存按钮，持有唯一状态（`text` / `doc` / `dirtyFrom`）
- `CodeView.tsx`：Monaco YAML 编辑器（**复用 `Setting/HelmEditor.tsx` 的现成配置，不重新调参**）+ 顶部提示条说明 `plugin_metadata`/`#END` 由平台托管 + 「预览最终 apisix.yaml」只读抽屉（拼接 T3 返回的 `managedSuffix`）
- `gatewayYaml.ts`：`js-yaml` load/dump 封装、内置规则（id 1 prometheus / id 2 opentelemetry）锁定与保序、id 唯一性与 upstream 引用完整性校验、**注释检测**

**验收**：`npm run lint`/`test` 绿；**此任务完成后代码视图即独立可用**，不依赖图形化就能跑通端到端（见 T10 的 V10）。

---

### T7 · 前端 Tab 挂载

**文件**：`src/pages/Cluster/ServiceInstance/index.tsx`、`index.test.tsx`

在现有 if/else 链（`index.tsx:175-207`）旁新增：仅 `serviceName === 'APISIX'` 时 push，位置在 `monitor` 之后、`instance` 之前。

**命名**：现有监控 Tab 组件已叫 `ApisixDashboard`（`pages/monitor/ApisixMonitor/index.tsx`），故新组件叫 `ApisixGatewayPanel`，Tab 中文标签用 **「网关配置」**——同页已有「监控」Tab 在做 dashboard 的事，两个都叫 Dashboard 会让用户分不清哪个看指标、哪个改路由。若要改回原生叫法，改一处 label 字符串即可。

**验收**：`index.test.tsx` 复制现有两个 `describe`（首次进入 / 从别的服务切过来），断言新 Tab 出现且 APISIX 之外的服务不出现。

---

### T8 · 前端图形化三张表

**文件**：新建 `ApisixGateway/{GraphicView.tsx, RouteTable.tsx, UpstreamTable.tsx, GlobalRuleTable.tsx}` + 测试

- `GraphicView.tsx`：左侧资源类型菜单（Routes / Upstreams / Global Rules）+ 右侧内容区，模仿原生 Dashboard 布局
- 三张表各一个 ProTable（**本地 `dataSource`，不是 `request`**——数据来自一次性拉取的 YAML，不是分页接口）+ ProForm 抽屉增删改
- 表单提交用 `{...originalItem, ...formValues}` **合并**，保留表单不认识的字段（约束 1）
- 内置 global_rules（id 1/2）在表格中标记为「系统内置」，只读、不可删
- Route 的 `plugins` **只给一个小 YAML/JSON 编辑框**，不为几十个插件各写表单——有代码视图作为完整逃生舱
- 顶部提示「当前配置还含有 N 个仅可在代码视图编辑的段」（如 `consumers`），避免用户误以为数据丢了

**验收**：单测覆盖「未知字段保留」「内置规则不可删」「upstream 引用完整性」。

---

### T9 · 前端视图切换状态机

**文件**：`ApisixGateway/index.tsx` + 测试

|       切换方向       |                            行为                             |
|------------------|-----------------------------------------------------------|
| 代码 → 图形化         | `load(text)` 试解析；失败则**留在代码视图**并在 Monaco 标出错误行；成功则更新 `doc` |
| 图形化 → 代码（图形化未改动） | **直接复用原始 `text` 不 dump** —— 保住注释与原始排版                     |
| 图形化 → 代码（图形化已改动） | `dump(doc)` 覆盖 `text`；若原 `text` 含注释，先弹二次确认告知注释将丢失         |
| 保存               | 以**当前所在视图**为准：代码视图提交 `text`，图形化视图提交 `dump(doc)`           |

**验收**：单测覆盖上表四行 + 往返保真（含注释、含 `consumers` 段、含未知字段）。

---

### T10 · 沙箱端到端验收

沙箱 ddh-01，**先备份 `apisix.yaml` 并记录 sha256**，收尾回滚（照搬 §7.9.1 的 V1–V6 做法）。

| ID  |     验证     |                                                   通过标准                                                    |
|-----|------------|-----------------------------------------------------------------------------------------------------------|
| V2  | 长文本透传不被破坏  | 节点 `apisix.yaml` 与 UI 提交内容字节级一致（除模板固定段）                                                                   |
| V3  | 端到端写入      | UI 新增 route+upstream → 节点文件 sha256 变化且内容正确，`curl` 新路由通                                                    |
| V4  | 不重启 + 不打标记 | `systemctl show apisix` 的 `ExecMainStartTimestamp` 不变；UI **不显示**「需要重启」                                    |
| V5  | 监控/链路不断    | `9091/apisix/prometheus/metrics` 仍 200 且含 `apisix_*`；OTel span 仍上报                                        |
| V6  | 校验兜底       | 构造缺 prometheus 规则的 YAML 直接 POST → 400，节点文件未变                                                              |
| V7  | 向后兼容       | `apisixGatewayYaml` 为空的实例触发 configure → 结果与改动前逐字节一致                                                       |
| V9  | 双视图往返保真    | 代码视图写含注释 + `consumers` 段 + 未知字段的 YAML → 切图形化改一条 route → 切回代码：`consumers` 与未知字段**仍在**；注释丢失前有二次确认；语法错误被拦并标行 |
| V10 | 代码视图独立可用   | 只用代码视图新增 route 并保存生效（T6 完成后即可先验收此条）                                                                       |
| V8  | 回滚         | 还原 `.bak`，sha256 回到基线                                                                                     |

**实测结果（2026-08-05，ddh-01，真实 APISIX 实例 `clusterId=1 instanceId=23`）**：

按用户指示复用 `deploy/deployment-standalone-doris.md` 记录的既有部署环境与部署手法（§7.4/§7.6 的「打包 → scp → md5 校验 → 备份旧目录（不删除）→ 复制 `conf/api.local.properties` → 启动」流程），而非本地起一个连沙箱 MySQL 的临时 Master 进程：

1. 本地 `mvn clean package -pl datasophon-api -am` 打出含 T1-T4 全部改动 + 新前端的 `datasophon-manager-3.0-SNAPSHOT.tar.gz`（131MB）。
2. 本地更新后的 `package/raw/meta/datacluster-physical/APISIX/`（含新 `apisixGatewayYaml` 参数与双分支模板）scp 到 ddh-01 的 `--productPackagesPath` 暂存目录，`datasophon-cli upload registry` 全量重新推送 Nexus（2749 文件成功，0 失败）。
3. 停旧进程 → 目录整体备份为 `datasophon-manager-3.0-SNAPSHOT.bak-apisixgateway-fix2-<ts>`（未删除）→ 解压新版 → 复制真实 `conf/api.local.properties` → 启动。日志确认 gRPC `18081`、5 主机预热、APISIX DDL 正常载入内存。
4. **过程中定位并修复一个真实的平台级 bug**：`ServiceLifecycleUtils.configServiceRoleInstance`（F6 引用的既有「只 configure 不重启」原语）从未填充 `ServiceRoleInfo.archInfoMap`，导致 `ServiceConfigureHandler.resolvePackageName` 在任何主机上都解析不到安装包，报「未找到匹配 CPU 架构的安装包」。这是该原语第一次被推到"需要真正按架构解析安装包"的调用路径上（此前唯一引用方 `ClusterDeleteService` 未必会触发同一分支），修复为在方法内部按 `clusterInfo.getClusterFrame() + roleInstanceEntity.getServiceName()` 查 `FrameServiceEntity` 并调 `ServicePkgNameUtils.getArchInfo` 填充，惠及该原语的全部现有与未来调用方。已本地单测覆盖，二次打包部署后确认解决。
5. **过程中发现并修复一个格式缺陷**：`apisixGatewayYaml` 自带结尾换行 + `${var}` 独占模板一行自身也换行，导致非空分支比预期多渲染一个空行（不影响 YAML 解析、`#END` 仍在末行，纯格式问题）。改为 OTELCOLLECTOR 同款单行写法 `<#if ...>${var}<#else>` 后，`ApisixStandaloneTemplateTest` 新增字节级断言锁回归，模板重新上传 Nexus + Master `restart`（未改 Java 代码，无需重新打包）后现场复核通过。
6. 登录用 `POST /ddh/api/v2/login/account`；写操作需要 `X-XSRF-TOKEN` 头（取自登录响应的 `XSRF-TOKEN` cookie），否则被 CSRF 过滤器拦成 403——这是走真实鉴权链路时才会暴露的细节，纯 curl 直连 controller 单测不会覆盖。

逐项结果：

- **V2/V3**：`GET .../apisix/gateway` 对已安装但从未设置过 `apisixGatewayYaml` 的实例，返回「向导参数拼出的初始 YAML」，与磁盘上真实 `apisix.yaml` 的可编辑段逐字节一致。`POST` 提交追加一条 `id:2 uri:/ddh-t10-test` 的新路由后，节点文件 sha256 从 `2f903f8e...1df569` 变为 `8783d670...`，`curl http://127.0.0.1:9080/ddh-t10-test` 从 APISIX 自身 `404 Route Not Found`（未命中）变为后端 Jetty 的 `404 Not Found`（已命中并转发），复用 T0 建立的"响应体来源"判定法。
- **V4**：全程 `systemctl show apisix -p ExecMainStartTimestamp` 保持 `Tue 2026-08-04 22:07:58 CST` 不变；API 返回的 `needRestart` 全程为 `false`（含 POST 因 archInfoMap 缺失而下发失败的那一次——证明复位逻辑与下发结果解耦，符合设计）。
- **V5**：写入前后 `9091/apisix/prometheus/metrics` 均 200，且恒定含 209 行 `apisix_*` 指标；文件里 `plugin_metadata` 段全程由模板固定输出，未被 UI 提交内容影响。
- **V6**：构造缺 `prometheus` 规则的 YAML 直接 POST → 响应体 `errorCode:400 errorMessage:"global_rules 必须同时包含 prometheus 与 opentelemetry 两条规则"`（与本仓库 v2 接口"HTTP 200 + envelope 内 400"的既有约定一致），节点文件 sha256 未变。
- **V7**：见上方 V2/V3 的 GET 结果——遗留实例（早于 `apisixGatewayYaml` 参数存在）触发 `configure` 后，输出与改动前的真实基线逐字节一致，验证了 `ApisixGatewayConfigService.loadEffectiveConfigs` 的兜底合并（详见 T4 记录的「计划外缺口」）在真实遗留数据上也成立。
- **V8**：用同一个 POST 接口把 `gatewayYaml` 写回最初捕获的单路由内容（而非只手工改文件），确保 DB 与磁盘文件同时回到一致状态；修复模板空行问题后最终验证 `sha256sum /usr/local/apisix/conf/apisix.yaml` = `2f903f8eb10d6c3521f0c6862448428532df56a187c52c7c2c1e85cdef1df569`，与 T0 记录的最初基线**完全相同**。
- **V9/V10**：纯前端状态机与保真逻辑，已由 `gatewayYaml.test.ts`（13 例）与 `ApisixGateway/index.test.tsx`（7 例，覆盖状态机四行切换 + 注释二次确认）在本地验证覆盖；本次沙箱验收未额外用真实浏览器复测，因为该部分不依赖后端真实环境（js-yaml load/dump 是纯函数）。

**结论**：T10 全部子项通过，且顺带修复 1 个真实平台级 bug（`ServiceLifecycleUtils` 缺 `archInfoMap`，影响所有"只 configure 不重启"调用方）与 1 个模板格式缺陷。ddh-01 上的 Master 已停留在含全部本次改动的版本，未回滚；仅 `apisix.yaml` 测试产生的内容已还原到初始基线。

---

## 5. 风险与已知坑

1. **T0 是唯一未经实测的前提**，不通过就停下重新决策，不要带着假设往下写。
2. **Worker jar / 模板版本漂移**：§7.9.1 与 §9.2 都栽过——现场只推到「当时在测的那台」，导致其它节点用旧模板（ddh-01/03/04/05 曾集体落后 ddh-02）。本次模板改动后**必须同步全部五节点**并核对 MD5。**T10 现场核实：当前沙箱 APISIX 角色实例只跑在 ddh-01 一台**（`GET .../apisix/gateway` 的 `roles` 字段只返回 ddh-01），不存在多节点漂移风险；若后续该服务扩到多节点，仍需回到"全部同步 + 核对 MD5"的原则。
3. **`global_rules` 可编辑 = 可误删监控**：三重防护——UI 锁定 id 1/2 只读、后端 POST 校验必含两插件、`plugin_metadata` 段留在模板不交给 UI。
4. **`needRestart` 复位必须有条件**，否则会掩盖用户另改的真需重启项（如 `apisixPort`）。
5. **注释丢失是 js-yaml 固有行为**，用脏标记 + 二次确认压到最小；不引入 `yawn-yaml` 等保留注释的库（维护状态差，收益不抵风险）。
6. **不改动**：`config.yaml` 那个 generator、`ApisixHandlerStrategy`、监控 Tab 与 `ApisixMonitor` 的任何代码。
7. `datasophon-ui-v2/config/proxy.ts` 是本机联调改动（指向远端沙箱），**提交前须用 `git diff --cached` 核对未包含它**。

## 6. 收尾命令

```bash
export JH21=/Users/pro/Library/Java/JavaVirtualMachines/graalvm-jdk-21.0.7/Contents/Home
JAVA_HOME=$JH21 ./mvnw spotless:apply -s ~/.m2/setting.xml
JAVA_HOME=$JH21 ./mvnw -pl datasophon-common,datasophon-grpc-api,datasophon-ui-v2,datasophon-api \
  -Dskip.installnodenpm -Dskip.npm -Dtest=ApisixGateway* -DfailIfNoTests=false test -s ~/.m2/setting.xml
cd datasophon-ui-v2 && npm run lint && npm run test
```

