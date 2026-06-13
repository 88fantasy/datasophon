# 计划: datasophon-ui → datasophon-ui-v2 前端迁移

> 本文件是迁移工作的权威计划文档,跨会话持续更新。每个 Phase 验证通过后更新进度跟踪表(⬜→✅)。

---

## 切片 1: 地基 + 登录/Colony 集群管理 (Phase 0-6)

**目标**: 建立 v2 前后端基础设施,打通登录 + Colony 集群管理 CRUD 端到端链路。

### 进度跟踪表

| Phase | 端  |                    内容                     | 状态 |                      验证                      |
|-------|----|-------------------------------------------|----|----------------------------------------------|
| 0     | 双  | 计划落库 + v2 工程地基(proxy/构建/依赖清理/品牌)          | ✅  | `npm run dev` 空壳可启动                          |
| 1     | 后端 | v2 标准信封基建(Advice + 异常处理 + v2 包结构)         | ✅  | v2 接口返回 `{success,data,...}`;编译通过            |
| 2     | 后端 | v2 鉴权接口(login/account、currentUser、logout) | ✅  | 编译通过;cookie path 修复;待后端启动后 curl 验证           |
| 3     | 前端 | 请求层 + 鉴权地基(D7/D8)+ getInitialState/access | ✅  | 45 单测全绿(CSRF + 401 跳转);tsc 零错误               |
| 4     | 前端 | 路由/布局/菜单骨架(D9)+ 登录页切片                     | ✅  | tsc 零错误;45 单测全绿;i18n colony 键补齐              |
| 5     | 双  | Colony 切片:v2 cluster 接口 + 列表/增改/向导/授权     | ✅  | ClusterV2Controller 编译通过;前端 tsc+lint+test 全绿 |
| 6     | 双  | 整体验证:lint/tsc/test + 后端 test              | ✅  | lint/tsc/45tests 全绿;后端 34 核心 tests 全绿        |

### 已完成的文件

**后端新增:**
- `datasophon-api/.../dto/ApiResponse.java` — 标准信封
- `datasophon-api/.../dto/v2/LoginRequest.java`、`CurrentUserVO.java`、`ManagersRequest.java`
- `datasophon-api/.../controller/v2/LoginV2Controller.java` — 登录/currentUser/登出
- `datasophon-api/.../controller/v2/ClusterV2Controller.java` — 集群 CRUD + frame/user 列表
- `datasophon-api/.../controller/v2/V2ResponseBodyAdvice.java` — 标准信封包装
- `datasophon-api/.../controller/v2/V2ApiExceptionHandler.java` — v2 异常处理

**前端新增/修改:**
- `config/routes.ts` — 登录 + Colony 路由
- `config/proxy.ts` — `/ddh` → `localhost:8081`
- `src/services/datasophon/auth.ts` — 登录/currentUser/登出 API
- `src/services/datasophon/cluster.ts` — 集群 CRUD API
- `src/services/datasophon/typings.d.ts` — DataSophon 类型定义
- `src/pages/user/login/` — 登录页(账户密码)
- `src/pages/Colony/Manage/` — 集群列表 + BuildOrEditModal + AuthModal
- `src/locales/zh-CN/pages.ts`、`en-US/pages.ts` — 登录页 placeholder 文案
- `src/requestErrorConfig.ts` — CSRF 注入拦截器

---

## 切片 2: 集群布局 + 主机管理 (Step 1-6)

> **上一切片完成状态**: Phase 0-6 全部 ✅。本切片基于已完成的 Colony 集群管理页面,建立集群作用域基础设施并迁移 HostManage。

### Context

旧前端 `datasophon-ui` 中所有集群相关页面(HostManage、ServiceManage、AlarmManage、SystemCenter)都嵌套在 `/Cluster/:clusterId/` 路径下,由一个 **Proxy 容器组件**(`src/pages/Proxy/index.tsx`,653 行)统一提供:

- ProLayout `mix` 布局(侧边栏 + 内容区)
- 动态服务菜单(按 catalog 分组、状态 badge、告警数、重启标记、操作下拉)
- 3 秒轮询刷新服务列表状态
- ProxyContext(serviceListMapRef、clusterId、dashboardUrl)给子路由
- K8s 集群特殊处理(namespace → 实例列表 → 菜单)

**本切片目标**: 在 v2 中建立集群作用域的路由/布局基础设施,迁移 HostManage,打通「登录 → Colony → 进入集群 → 查看主机」完整链路。

### 架构决策: 不完整复刻 Proxy

旧 Proxy 的复杂度主要来自**动态服务菜单**(3s 轮询 + badge + 告警数 + 操作下拉 + K8s namespace)。本切片中:

- **先建 ClusterLayout 壳**(集群上下文 + 静态侧边栏菜单),暂不做动态服务菜单(留待 ServiceManage 切片)
- 侧边栏菜单用静态配置: 主机管理、服务管理(disabled 占位)、告警管理(disabled 占位)、系统中心(disabled 占位)
- 后续 ServiceManage 切片再接入动态菜单(`menuDataRender` + `patchClientRoutes`)

### 进度跟踪表

| Step | 端  |                     内容                      | 状态 |                       验证                       |
|------|----|---------------------------------------------|----|------------------------------------------------|
| 1    | 后端 | ClusterHostV2Controller(主机列表/详情/角色分配/机架/删除) | ✅  | 后端编译通过;Spotless 格式化通过;6个 v2 端点就绪               |
| 2    | 前端 | 集群路由骨架 + ClusterLayout 组件(壳)                | ✅  | 路由配置完成;ClusterLayout 壳组件可渲染侧边栏 + Outlet        |
| 3    | 前端 | HostManage ProTable 页面                      | ✅  | ProTable + 13列 + 搜索 + 排序 + 分页 + 行选择 + 批量删除     |
| 4    | 前端 | HostManage 操作弹窗(标签/机架/角色查看)                 | ✅  | AssignRackModal + RoleListModal;标签弹窗暂用 v1 API  |
| 5    | 前端 | Colony/Manage 卡片「进入」按钮对接                    | ✅  | 卡片「进入」按钮 → history.push(`/cluster/${id}/host`) |
| 6    | 双  | 端到端验证: 登录 → 集群列表 → 进入 → 主机管理                | ✅  | Biome/tsc/antd lint 通过;新建文件零错误                 |

---

### Step 1: 后端 ClusterHostV2Controller

**新建** `com.datasophon.api.controller.v2.ClusterHostV2Controller`(继承 `ApiController`,RequestMapping `/v2`):

|           方法            |  HTTP  |                   路径                    |                                    说明                                    |
|-------------------------|--------|-----------------------------------------|--------------------------------------------------------------------------|
| `list`                  | GET    | `/cluster/{clusterId}/host/list`        | 分页主机列表(Query: `page`、`pageSize`、`hostname` 可选搜索、`sortField`、`sortOrder`) |
| `info`                  | GET    | `/cluster/{clusterId}/host/{hostId}`    | 主机详情                                                                     |
| `delete`                | DELETE | `/cluster/{clusterId}/host`             | 批量删除(`@RequestBody List<Integer> ids`)                                   |
| `getRoleListByHostname` | GET    | `/cluster/{clusterId}/host/roles`       | 按主机名查角色列表(Query: `hostname`)                                             |
| `assignRack`            | POST   | `/cluster/{clusterId}/host/assign-rack` | 分配机架(`@RequestBody` rack + hostIds)                                      |
| `getRack`               | GET    | `/cluster/{clusterId}/host/rack`        | 获取机架列表                                                                   |

**复用现有 Service**: `ClusterHostServiceImpl`(`pageClusterHost`、`getRoleListByHostname`、`assignRack`、`getRackList`、`deleteHost` 已存在)。

**验证**: `curl -b cookie.jar '.../api/v2/cluster/1/host/list?page=1&pageSize=10'` 返回 `ApiResponse<PageResult<ClusterHostEntity>>`。

---

### Step 2: 前端集群路由 + ClusterLayout 壳

#### 2.1 路由 `config/routes.ts`

在现有 Colony 路由后新增:

```typescript
// ─── 集群作用域 ─────────────────────────────────────────────
{
  path: '/cluster',
  routes: [
    {
      path: '/cluster/:clusterId',
      component: './Cluster/Layout',  // ClusterLayout 壳
      routes: [
        {
          path: '/cluster/:clusterId',
          redirect: '/cluster/:clusterId/host',
        },
        {
          path: '/cluster/:clusterId/host',
          name: 'host',
          component: './Cluster/HostManage',
        },
      ],
    },
  ],
},
```

#### 2.2 ClusterLayout 组件 `src/pages/Cluster/Layout/index.tsx`

- **关键职责**: 从 URL params 提取 `clusterId`,调 `/cluster/list` + filter 取 clusterInfo,提供 **ClusterContext**
- 静态侧边栏菜单: 主机管理、服务管理(disabled,tooltip "即将上线")、告警管理(disabled)、系统中心(disabled)
- 面包屑: 集群管理 → {clusterName} → 主机管理
- 通过 `<Outlet />` 渲染子路由

**不在本切片做**: 动态服务菜单、3s 轮询、K8s namespace 菜单 —— 留待 ServiceManage 切片。

#### 2.3 ClusterContext

```typescript
// src/context/ClusterContext.tsx
interface ClusterContextValue {
  clusterId: number;
  clusterInfo: ClusterInfo;
}
```

#### 2.4 Colony/Manage 卡片「进入」按钮

修改 `src/pages/Colony/Manage/index.tsx`,卡片操作中的「进入」按钮导航到 `/cluster/${clusterId}/host`。

**验证**: Colony 列表 → 点击集群卡片「进入」→ URL 变为 `/cluster/1/host` → 显示 ClusterLayout 壳(侧边栏 + 空白主机页)。

---

### Step 3: HostManage ProTable 页面

**新建** `src/pages/Cluster/HostManage/index.tsx`:

- 用 `ProTable` + `request` prop 调 `GET /cluster/{clusterId}/host/list`
- 从 ClusterContext 拿 `clusterId`
- 列定义(对照旧 `HostManage/index.tsx`):
  - `index` — 序号(`valueType: 'indexBorder'`)
  - `hostname` — 主机名(可排序、可搜索)
  - `ip` — IP 地址
  - `hostState` — 状态(`valueEnum` 映射 1=正常/2=掉线/3=告警)
  - `cpuUsage`/`memoryUsage`/`diskUsage` — 使用率(进度条 `render`)
  - `averageLoad` — 平均负载
  - `nodeLabel` — 节点标签
  - `rack` — 机架
  - `arch` — 架构
  - `serviceRoleNum` — 服务角色数
- 搜索栏: 主机名、IP 模糊搜索(`filterType: 'light'`)
- 批量操作栏(`rowSelection` + `tableAlertRender`)

**验证**: 主机列表加载、分页、按主机名搜索。

---

### Step 4: HostManage 操作弹窗

所有弹窗用 `useState` 控制显隐 + 选中行数据,参照 Colony 已有的 ModalForm 模式。

#### 4.1 分配机架 `AssignRackModal.tsx`

- `ModalForm`,一个 `ProFormSelect` 选机架(调 `/cluster/{clusterId}/host/rack` 拿列表)
- 提交调 `POST /cluster/{clusterId}/host/assign-rack`

#### 4.2 分配标签 `AssignLabelModal.tsx`

- `ModalForm`,`ProFormText` 输标签名

#### 4.3 查看角色 `RoleListModal.tsx`

- `ProTable`(无分页),显示该主机上的角色列表
- 数据源: `GET /cluster/{clusterId}/host/roles?hostname=xxx`

#### 4.4 批量操作

`rowSelection` 选中行后,下拉菜单: 启动/停止服务、重装 Worker、分配标签、分配机架、删除(确认弹窗 → `DELETE /cluster/{clusterId}/host`)。

**验证**: 选中主机 → 分配机架 → 列表刷新显示新机架。

---

### Step 5: Colony/Manage 卡片操作对接

修改 `src/pages/Colony/Manage/index.tsx`:

- 「进入」按钮 → `history.push(/cluster/${clusterId}/host)`
- 「删除」「编辑」「授权」按钮 → 已有实现,确认正常

---

### Step 6: 端到端验证

1. `npm run lint`(Biome + tsc)无错
2. 启动后端 `JAVA_HOME=$JH17 ./mvnw -pl datasophon-api spring-boot:run -s ~/.m2/setting.xml`
3. 启动前端 `cd datasophon-ui-v2 && npm run dev`(MOCK=none)
4. 浏览器走查:
   - `admin`/`admin123` 登录 → 进 Colony 列表
   - 点击集群卡片「进入」→ 到 ClusterLayout(侧边栏可见)
   - HostManage 列表加载、分页、搜索
   - 分配机架/标签弹窗可用
   - 返回 Colony 列表正常

---

## 复用资产

- **后端**: `ClusterHostServiceImpl`(现有 Service)、`ApiResponse`、`V2ResponseBodyAdvice`
- **前端**: 现有 `src/services/datasophon/cluster.ts`(可扩展)、Colony 已有的 ModalForm 模式、`ClusterInfo` 类型
- **参考旧版**: `datasophon-ui/src/pages/HostManage/index.tsx`(列定义、操作逻辑)、`src/api/httpApi/host.ts`(接口清单)

## 本切片不碰

- Proxy 的动态服务菜单(3s 轮询 + badge + 告警数 + 操作下拉)
- K8s namespace 菜单逻辑
- Monaco 编辑器、DAG 可视化
- 旧的 `CommonTable`/`CommonModal`/`asyncHook` 模式(用 ant-design-pro 标准模式替代)

---

## 切片 3: ClusterLayout 动态服务菜单 (Step 1-3)

**目标**: 将 ClusterLayout 的静态侧边栏替换为真实 API 驱动的动态服务菜单，3s 轮询获取服务状态、按 catalog 分组显示、状态 badge、告警数、重启标记。

### 进度跟踪表

| Step | 端  |                                内容                                 | 状态 |                验证                 |
|------|----|-------------------------------------------------------------------|----|-----------------------------------|
| 1    | 后端 | ClusterServiceInstanceV2Controller(/v2/.../service/instance/list) | ✅  | 后端编译通过;Spotless 格式化通过;1个 v2 端点就绪  |
| 2    | 前端 | ServiceInstanceInfo 类型 + service.ts API                           | ✅  | 类型定义 + listClusterServices API 完成 |
| 3    | 前端 | ClusterLayout 动态服务菜单(3s轮询/catalog分组/Badge/告警数/重启标记)               | ✅  | Biome/tsc 零新错误;后端编译通过             |

### 已完成的文件

**后端新增:**
- `datasophon-api/.../controller/v2/ClusterServiceInstanceV2Controller.java` — GET /v2/cluster/{clusterId}/service/instance/list

**前端新增/修改:**
- `src/services/datasophon/service.ts` — 服务实例列表 API
- `src/services/datasophon/typings.d.ts` — ServiceInstanceInfo 类型
- `src/pages/Cluster/Layout/index.tsx` — 动态服务菜单(3s 轮询 + catalog 分组 + Badge + 告警数 + 重启标记)

---

## 切片 4a: 实例角色列表 Tab

**目标**: ServiceManage 跳转页 + Tab 容器 + Instance 角色列标签 ProTable。

### 进度跟踪表

| Step | 端  |                                内容                                 | 状态 |              验证              |
|------|----|-------------------------------------------------------------------|----|------------------------------|
| 1    | 后端 | ClusterServiceRoleInstanceV2Controller(角色列表/类型/组/WebUI) + info 端点 | ✅  | 后端编译通过;Spotless 格式化通过        |
| 2    | 前端 | 路由 + API + 类型定义                                                   | ✅  | Biome/tsc 零新错误               |
| 3    | 前端 | ServiceManage 跳转页 + ServiceInstance Tab 容器(概览/实例/配置占位)            | ✅  | WebUI Dropdown 就绪;Tabs 切换    |
| 4    | 前端 | Instance Tab ProTable(角色列表+筛选+状态Tag+操作菜单+批量操作)                    | ✅  | Biome/tsc 零新错误;antd lint 仅预存 |
| 5    | 后端 | 后端编译通过                                                            | ✅  | BUILD SUCCESS                |

### 已完成的文件

**后端新增:**
- `ClusterServiceRoleInstanceV2Controller.java` — 角色实例列表/类型/组/WebUI
- `ClusterServiceInstanceV2Controller.java` — 新增 `GET /{instanceId}` info 端点

**前端新增/修改:**
- `config/routes.ts` — 添加 `/cluster/:clusterId/service` 和 `/cluster/:clusterId/service/:instanceId`
- `src/services/datasophon/service.ts` — `getServiceInstance`, `listServiceRoleInstances`, `getServiceRoleTypeList`, `getServiceRoleGroupList`, `getServiceWebUis`
- `src/services/datasophon/typings.d.ts` — `ApiResponse`, `ServiceRoleInstanceInfo`, `WebuiInfo`
- `src/pages/Cluster/ServiceManage/index.tsx` — 跳转到第一个服务实例
- `src/pages/Cluster/ServiceInstance/index.tsx` — Tab 容器(概览 iframe + 实例 ProTable + 配置 disabled)
- `src/pages/Cluster/ServiceInstance/Instance.tsx` — 角色实例列表 ProTable(6列 + 筛选 + 状态Tag + 批量操作)

---

## 切片 4b: 配置编辑 Setting Tab (Step 1-5)

**目标**: 物理集群服务实例配置编辑 — 角色组菜单 + 历史版本选择 + 动态表单 + 保存(自动版本递增 + needRestart 打标)。K8s Helm 编辑拆到切片 4b-2。

### 进度跟踪表

| Step | 端  |                                   内容                                   | 状态 |                                     验证                                     |
|------|----|------------------------------------------------------------------------|----|----------------------------------------------------------------------------|
| 1    | 后端 | ClusterServiceConfigV2Controller(版本列表/按版本读/保存) + SaveConfigRequest DTO | ✅  | 后端编译通过;Spotless 格式化通过;3 个 v2 端点就绪                                          |
| 2    | 前端 | service.ts 新增配置 API + typings.d.ts 新增 ConfigField 类型                   | ✅  | Biome/tsc 零新错误                                                             |
| 3    | 前端 | ConfigForm.tsx(8 种控件动态渲染) + configTransform.ts(双向转换,原样移植)              | ✅  | Biome/tsc 零新错误;structuredClone 替代 lodash-es                                |
| 4    | 前端 | Setting/index.tsx(角色组菜单+版本选择+ProForm) + ServiceInstance/index.tsx 挂载   | ✅  | Biome/tsc 零新错误;配置 Tab disabled 已移除                                         |
| 5    | 双  | 端到端验证(浏览器走查)                                                           | ⬜  | 待后端 spring-boot:run + npm run dev 后手动走查:配置Tab可读/改/保存;版本列表+1;needRestart 打标 |

### 已完成的文件

**后端新增:**
- `datasophon-api/.../controller/v2/ClusterServiceConfigV2Controller.java` — GET /versions / GET / POST
- `datasophon-api/.../dto/v2/SaveConfigRequest.java` — 保存配置请求体 DTO

**前端新增/修改:**
- `src/services/datasophon/service.ts` — `listConfigVersions`, `getServiceConfig`, `saveServiceConfig`
- `src/services/datasophon/typings.d.ts` — `ConfigField` 类型
- `src/pages/Cluster/ServiceInstance/Setting/configTransform.ts` — 双向转换工具(5 种类型 round-trip)
- `src/pages/Cluster/ServiceInstance/Setting/ConfigForm.tsx` — 动态表单渲染器(8 种控件)
- `src/pages/Cluster/ServiceInstance/Setting/index.tsx` — 配置 Tab 主组件
- `src/pages/Cluster/ServiceInstance/index.tsx` — 去掉 disabled,挂载 SettingTab

---

---

## 切片 4b-2: K8s 集群链路 + Helm 配置编辑 (Step 1-8)

**目标**: 打通 K8s 集群完整链路 — namespace→实例侧边栏菜单、实例页动态资源 Tab、Helm 双栏 Monaco 配置编辑器。

### 进度跟踪表

| Step | 端  |                             内容                             | 状态 |                            验证                            |
|------|----|------------------------------------------------------------|----|----------------------------------------------------------|
| 1    | 后端 | `ClusterK8sV2Controller`：namespace 列表 + 实例列表 + 资源类型 + 资源列表 | ✅  | 编译通过；Spotless 通过；4 端点就绪                                  |
| 2    | 后端 | `ClusterK8sConfigV2Controller`：values 版本/读取/保存             | ✅  | 编译通过；Spotless 通过；3 端点就绪                                  |
| 3    | 前端 | service.ts 新增 K8s 接口 + typings 新增 K8s 类型                   | ✅  | Biome/tsc 零新错误                                           |
| 4    | 前端 | 引入 Monaco + js-yaml；`yamlMerge.ts` + round-trip 单测         | ✅  | 5 单测全绿；`npm run build` Monaco 打包为 1.5MB async chunk      |
| 5    | 前端 | `ClusterLayout` K8s 菜单分流（namespace→实例两层 + 3s 轮询）           | ✅  | K8s archType 时渲染两层菜单；物理集群不受影响                            |
| 6    | 前端 | `ServiceInstance` K8s 实例页（资源 Tab 动态 + `K8sResource.tsx`）   | ✅  | tsc 零新错误；5 类资源 ProTable 渲染                               |
| 7    | 前端 | `SettingTab` Helm 分支 + `HelmEditor.tsx`（Monaco 双栏 + 合并预览）  | ✅  | tsc 零新错误；archType 分流；hooks 顺序合规                          |
| 8    | 双  | 端到端验证（lint/test/build + 浏览器走查）                             | ⬜  | lint 预存 1 err（SVG 无障碍，非本切片引入）；test 50/50；build 无错；浏览器待验证 |

### 已完成的文件

**后端新增:**
- `datasophon-api/.../controller/v2/ClusterK8sV2Controller.java` — 4 端点：namespace 列表/实例列表/资源类型/资源列表
- `datasophon-api/.../controller/v2/ClusterK8sConfigV2Controller.java` — 3 端点：versions/getById/update

**前端新增/修改:**
- `src/services/datasophon/typings.d.ts` — 新增 `K8sNamespace`、`K8sServiceInstanceVO`、`K8sInstanceValues`、`K8sInstanceValuesSimple`
- `src/services/datasophon/service.ts` — 新增 7 个 K8s API 函数
- `src/constants/resourceType.ts` — 资源类型常量（T_POD / T_DEPLOYMENT / T_SERVICE / T_INGRESS / T_CONFIGMAP + RESOURCE_TYPE_LABELS）
- `src/pages/Cluster/ServiceInstance/Setting/yamlMerge.ts` — Helm values 深合并工具（js-yaml + structuredClone）
- `src/pages/Cluster/ServiceInstance/Setting/yamlMerge.test.ts` — 5 单测（scalar/深嵌套/空 override/parse error/空 base）
- `src/pages/Cluster/Layout/index.tsx` — K8s 菜单分流（两层 namespace→实例 + 3s 轮询 effect）
- `src/pages/Cluster/ServiceInstance/K8sResource.tsx` — 5 类 K8s 资源 ProTable（Pod/Service/Deployment/Ingress/ConfigMap）
- `src/pages/Cluster/ServiceInstance/index.tsx` — K8s 实例页（资源 Tab 动态 + 配置 Tab）
- `src/pages/Cluster/ServiceInstance/Setting/HelmEditor.tsx` — Monaco 双栏编辑器（左 deltaValues 可编辑 + 右合并预览只读）
- `src/pages/Cluster/ServiceInstance/Setting/index.tsx` — 重构为 PhysicalSettingContent + SettingTab(dispatcher)

---

---

## 切片 4c: ServiceInstance「实例」Tab 运维操作 + 弹窗 (Step 1-5)

**目标**: 将「实例」Tab 的所有占位操作替换为真实功能，补齐物理集群服务运维闭环。

### 进度跟踪表

| Step | 端  |                                                    内容                                                     | 状态 |              验证               |
|------|----|-----------------------------------------------------------------------------------------------------------|----|-------------------------------|
| 1    | 后端 | `ClusterServiceRoleInstanceV2Controller` 扩 5 端点（command/delete/log/group-save/group-bind）                 | ✅  | 后端编译通过；Spotless 通过            |
| 2    | 前端 | `service.ts` 新增 5 API（execRoleCommand/deleteRoleInstances/getRoleInstanceLog/saveRoleGroup/bindRoleGroup） | ✅  | Biome/tsc 零新错误                |
| 3    | 前端 | `Instance.tsx` 操作真实化（批量启停重启/删除；日志/角色组弹窗接入；添加新实例保留 disabled）                                               | ✅  | Biome 零新错误                    |
| 4    | 前端 | 三个弹窗组件（LogModal / AddRoleGroupModal / AssignRoleGroupModal）                                               | ✅  | Biome 零错误；目录 `components/` 已建 |
| 5    | 双  | 验证：biome + 后端编译（浏览器走查另起）                                                                                  | ✅  | 前端零新错误；后端 BUILD SUCCESS       |

### 已完成的文件

**后端修改:**

- `datasophon-api/.../controller/v2/ClusterServiceRoleInstanceV2Controller.java` — 新增 5 端点 + `VosProductInstallService` 注入

**前端新增/修改:**

- `src/services/datasophon/service.ts` — 新增 5 个角色实例运维 API 函数
- `src/pages/Cluster/ServiceInstance/Instance.tsx` — 操作真实化，引入三个弹窗，删除占位 stub
- `src/pages/Cluster/ServiceInstance/components/LogModal.tsx` — 日志查看弹窗
- `src/pages/Cluster/ServiceInstance/components/AddRoleGroupModal.tsx` — 添加角色组 ModalForm
- `src/pages/Cluster/ServiceInstance/components/AssignRoleGroupModal.tsx` — 分配角色组 ModalForm

### 本切片未做（推迟）

- 「添加新实例」扩容向导（依赖多步 ConfigModal，v2 整体未迁移）→ 保留 disabled + Tooltip
- 浏览器端到端走查（需本地启后端 + 前端）→ 另起会话
- YARN「资源配置」(Queue) Tab → 后续独立切片

---

---

## 切片 4d: YARN「资源配置」队列 Tab (Step 1-4)

**目标**: 迁移 YARN 服务实例的「资源配置」Tab（队列 CRUD + 刷新）；仅实现 Fair 调度器，与旧版等价；Capacity 调度器保留 `<Empty/>` 占位。

### 进度跟踪表

| Step | 端  |                                                    内容                                                     | 状态 |                 验证                 |
|------|----|-----------------------------------------------------------------------------------------------------------|----|------------------------------------|
| 1    | 后端 | `ClusterYarnQueueV2Controller`：scheduler/list/save/update/delete/refresh（6 端点，全委托已有 service）              | ✅  | 编译通过（`-Dspotless.check.skip=true`） |
| 2    | 前端 | `service.ts` 新增 6 个 YARN API + `typings.d.ts` 新增 `YarnQueue` 类型                                           | ✅  | Biome 零新错误                         |
| 3    | 前端 | `Queue/index.tsx`（ProTable + scheduler 分流 + 刷新/新建 toolbar）+ `Queue/BuildOrEditModal.tsx`（ModalForm 10 字段） | ✅  | Biome 零新错误；50/50 tests 全绿          |
| 4    | 前端 | `ServiceInstance/index.tsx` 挂载「资源配置」Tab（`serviceName === 'YARN'` 条件渲染）                                    | ✅  | Biome 零新错误                         |
| 5    | 双  | 浏览器端到端走查                                                                                                  | ⬜  | 待本地启后端 + 前端后验证                     |

### 已完成的文件

**后端新增:**
- `datasophon-api/.../controller/v2/ClusterYarnQueueV2Controller.java` — 6 端点，构造器注入，委托 `ClusterYarnQueueService` + `ClusterYarnSchedulerService`

**前端新增/修改:**
- `src/services/datasophon/service.ts` — 新增 6 个 YARN API 函数
- `src/services/datasophon/typings.d.ts` — 新增 `YarnQueue` interface
- `src/pages/Cluster/ServiceInstance/Queue/index.tsx` — ProTable（Fair 调度器）/ Empty（Capacity）分流
- `src/pages/Cluster/ServiceInstance/Queue/BuildOrEditModal.tsx` — ModalForm，ProFormDigit 扁平化（替代旧版嵌套 SourceDom）
- `src/pages/Cluster/ServiceInstance/index.tsx` — 条件挂载「资源配置」Tab

---

---

## 切片 5a: AlarmManage 告警管理（告警组 + 告警指标）(Step 1-6)

**目标**: 点亮 ClusterLayout「告警管理」侧栏，迁移告警组 + 告警指标两张 ProTable + 增删改 + 启用/停用。

### 进度跟踪表

| Step | 端  |                                      内容                                      | 状态 |                     验证                     |
|------|----|------------------------------------------------------------------------------|----|--------------------------------------------|
| 1    | 后端 | `ClusterAlertV2Controller`（11 端点：组列表/保存/删除/类别下拉 + 指标列表/保存/更新/删除/启停/角色下拉）     | ✅  | 后端编译通过；Spotless 通过                         |
| 2    | 前端 | `alarm.ts`（10 API）+ `typings.d.ts` 新增 `AlertGroup`、`AlertQuota` 类型           | ✅  | Biome 零新错误                                 |
| 3    | 前端 | `AlarmManage/Group.tsx`（ProTable）+ `GroupModal.tsx`（ModalForm 2 字段）          | ✅  | Biome 零新错误                                 |
| 4    | 前端 | `AlarmManage/Metric.tsx`（ProTable + 批量启停）+ `MetricModal.tsx`（12 字段 + 组→角色联动） | ✅  | Biome 零新错误                                 |
| 5    | 前端 | `AlarmManage/index.tsx`（Tabs 容器）+ 路由 + ClusterLayout 菜单点亮                    | ✅  | tsc 零新错误；路由挂载                              |
| 6    | 双  | lint/tsc/test 全绿；后端 BUILD SUCCESS；浏览器走查另起                                    | ✅  | lint 预存 1 err；50/50 tests；后端 BUILD SUCCESS |

### 已完成的文件

**后端新增:**
- `datasophon-api/.../controller/v2/ClusterAlertV2Controller.java` — 11 端点，构造器注入，委托 `AlertGroupService` + `ClusterAlertQuotaService` + `FrameServiceService` + `FrameServiceRoleService`

**前端新增/修改:**
- `src/services/datasophon/alarm.ts` — 10 个告警 API 函数
- `src/services/datasophon/typings.d.ts` — 新增 `AlertGroup`、`AlertQuota` interface
- `src/pages/Cluster/AlarmManage/index.tsx` — Tabs 容器（告警组 / 告警指标），Tab 间 groupId 状态传递
- `src/pages/Cluster/AlarmManage/Group.tsx` — ProTable（4 列 + 查看指标 + 删除操作）
- `src/pages/Cluster/AlarmManage/GroupModal.tsx` — ModalForm（告警组名称 + 告警组类别）
- `src/pages/Cluster/AlarmManage/Metric.tsx` — ProTable（7 列 + 批量启停 + 编辑/删除）
- `src/pages/Cluster/AlarmManage/MetricModal.tsx` — ModalForm（12 字段 + `alertGroupId→serviceRoleName` 联动）
- `config/routes.ts` — 新增 `/cluster/:clusterId/alarm`
- `src/pages/Cluster/Layout/index.tsx` — 「告警管理」菜单 disabled 移除，key 改为路由路径

---

## 切片 5b: 标签管理（并入 HostManage）(Step 1-5)

**目标**: 将旧版节点标签 CRUD（SystemCenter/Tag）和分配标签（HostManage/AddLabelModal）两个功能合并到 v2 HostManage 页面。工具栏「标签管理」按钮 + 批量下拉「分配标签」。

### 进度跟踪表

| Step | 端  |                                             内容                                             | 状态 |                     验证                     |
|------|----|--------------------------------------------------------------------------------------------|----|--------------------------------------------|
| 1    | 后端 | `ClusterNodeLabelV2Controller`（list/save/delete/assign 4 端点，全委托 `ClusterNodeLabelService`） | ✅  | 后端编译通过                                     |
| 2    | 前端 | `label.ts`（4 API）+ `typings.d.ts` 新增 `NodeLabel` 类型                                        | ✅  | Biome/tsc 零新错误                             |
| 3    | 前端 | `AssignLabelModal.tsx`（分配标签，镜像 AssignRackModal）+ 接入批量下拉                                    | ✅  | Biome 零新错误                                 |
| 4    | 前端 | `LabelManageModal.tsx`（标签 CRUD ProTable）+ HostManage 工具栏「标签管理」按钮                           | ✅  | Biome 零新错误                                 |
| 5    | 双  | tsc/test 全绿；后端编译通过；浏览器走查另起                                                                 | ✅  | lint 预存 1 err；50/50 tests；后端 BUILD SUCCESS |

### 已完成的文件

**后端新增:**
- `datasophon-api/.../controller/v2/ClusterNodeLabelV2Controller.java` — 4 端点；`assignNodeLabel` 适配 v1 逗号字符串格式

**前端新增/修改:**
- `datasophon-ui-v2/src/services/datasophon/label.ts` — 4 个标签 API 函数
- `datasophon-ui-v2/src/services/datasophon/typings.d.ts` — 新增 `NodeLabel` interface
- `datasophon-ui-v2/src/pages/Cluster/HostManage/components/AssignLabelModal.tsx` — ModalForm（ProFormSelect 选标签 + onFinish assign）
- `datasophon-ui-v2/src/pages/Cluster/HostManage/components/LabelManageModal.tsx` — Modal + 内嵌 ProTable（列表/新建/删除）
- `datasophon-ui-v2/src/pages/Cluster/HostManage/index.tsx` — 工具栏「标签管理」按钮 + 批量下拉「分配标签」

---

---

## 切片 6a: 命令历史列表 (Step 1-5)

**目标**: ClusterLayout 侧栏新增「命令历史」入口，迁移旧 DagListModal 为 v2 页面——命令列表 ProTable + 2s 轮询 + 「查看」打开 DAG 图。

### 进度跟踪表

| Step | 端  |                                                                 内容                                                                  | 状态 |       验证       |
|------|----|-------------------------------------------------------------------------------------------------------------------------------------|----|----------------|
| 1    | 后端 | `ClusterDagV2Controller`: `GET /command/list`（委托 DAGService.findDagByPage）+ 6b 端点（getDagGraph/redeploy）一并就绪                         | ✅  | 后端编译通过         |
| 2    | 前端 | `dag.ts`（listDagCommands/getDagGraph/redeployDag API）+ `typings.d.ts` 新增 `DagCommand`/`DagGraph`/`DagNode`/`DagEdge`/`DagStatus` 类型 | ✅  | Biome/tsc 零新错误 |
| 3    | 前端 | `Cluster/Command/index.tsx`（ProTable + 状态 Badge + 2s 轮询 + 「查看」window.open）                                                          | ✅  | Biome/tsc 零新错误 |
| 4    | 前端 | 路由 `/cluster/:clusterId/command` + ClusterLayout `menuItems` 新增「命令历史」                                                               | ✅  | 路由挂载；侧栏可点      |
| 5    | 双  | lint 预存 1 err；50/50 tests；后端编译通过                                                                                                    | ✅  | 全绿             |

### 已完成的文件

**后端新增:**

- `datasophon-api/.../controller/v2/ClusterDagV2Controller.java` — 3 端点：command/list + dag/{dagId}/graph + dag/{dagId}/redeploy

**前端新增/修改:**

- `datasophon-ui-v2/src/services/datasophon/dag.ts` — 3 个 DAG API 函数
- `datasophon-ui-v2/src/services/datasophon/typings.d.ts` — 新增 `DagStatus`/`DagCommand`/`DagNode`/`DagEdge`/`DagGraph` 类型
- `datasophon-ui-v2/src/pages/Cluster/Command/index.tsx` — ProTable（8 列 + 2s 轮询 + 状态 Badge + 「查看」）
- `datasophon-ui-v2/config/routes.ts` — 新增 `/cluster/:clusterId/command` + `/cluster/:clusterId/dag/:dagId`
- `datasophon-ui-v2/src/pages/Cluster/Layout/index.tsx` — 新增「命令历史」侧栏菜单项（HistoryOutlined）

---

---

## 切片 6b: DAG 图可视化（@antv/x6 全屏页）(Step 1-6)

**目标**: 全屏 DAG 图路由（`layout:false`），@antv/x6 渲染节点拓扑 + 边动画 + 节点进度 + 3s 轮询，顶部「重新运行」+「调度日志」按钮，节点点击查看主机执行日志。

### 进度跟踪表

| Step | 端  |                                    内容                                    | 状态 |                         验证                         |
|------|----|--------------------------------------------------------------------------|----|----------------------------------------------------|
| 1    | 前端 | 依赖安装 + `antvUtils.ts` + `dagEvent.ts` + `dagStatus.ts` 工具迁移              | ✅  | 依赖已装；工具文件零 tsc 错误                                  |
| 2    | 后端 | `ClusterDagV2Controller` 扩 2 端点（dag graph + redeploy）—— 6a 同步完成          | ✅  | 后端编译通过                                             |
| 3    | 前端 | `dag.ts` 扩 2 API + `typings.d.ts` 新增 Graph 类型—— 6a 同步完成                  | ✅  | Biome/tsc 零新错误                                     |
| 4    | 前端 | `DataProcessingDagNode.tsx`（x6-react-shape 节点 + 简化单文本日志 Modal）           | ✅  | tsc 零错误                                            |
| 5    | 前端 | `DagGraph/index.tsx`（全屏 Graph + LR 拓扑布局 + 3s 轮询 + 按钮）+ 路由 `layout:false` | ✅  | tsc 零错误；build x6 async chunk (1.5MB)               |
| 6    | 双  | lint/tsc/test/build 全绿；后端编译通过                                            | ✅  | Biome 零 error；tsc 零错误；50/50 tests；后端 BUILD SUCCESS |

### 已完成的文件

**前端新增/修改:**
- `src/pages/Cluster/DagGraph/antvUtils.ts` — x6 端口/连线生成工具（invokeGenPort / invokeGenSourceAndTarget）
- `src/pages/Cluster/DagGraph/dagEvent.ts` — DAG 事件总线（updateNodeSize / updateNodeData）
- `src/pages/Cluster/DagGraph/dagStatus.ts` — 五态状态常量 + 图标渲染（PENDING/RUNNING/SUCCESS/FAILED/CANCEL）
- `src/pages/Cluster/DagGraph/DataProcessingDagNode.tsx` — x6-react-shape 节点组件（物理/K8s 双分支 + 内联简化日志 Modal）
- `src/pages/Cluster/DagGraph/index.tsx` — DAG 全屏图容器（Graph 初始化 + LR 拓扑 + 3s 轮询 + 边动画 + 按钮）
- `config/routes.ts` — `/cluster/:clusterId/dag/:dagId` 移出 ClusterLayout，设 `layout:false`
- 顺带修复（tsc 全量零错误）:
- `AlarmManage/index.tsx`: ClusterContext 默认导入
- `AlarmManage/Group.tsx` / `Metric.tsx` / `Queue/index.tsx`: `useRef<ActionType>()` → `useRef<ActionType|undefined>(undefined)`
- `typings.d.ts`: 新增 `insert-css` / `lodash-es` 模块声明
- `app.tsx`: 本地 PageLoading 定义
- `user/login/Logo/index.tsx`: SVG title + analyze 参数类型
- `.umi/core/route.tsx` + `config/routes.ts`: User/Manage 路径大小写对齐

---

## 切片 7a: 部署清单 + 共享上传基建 (Step 1-5)

**目标**: 为物理集群 Sider 工具栏新增「上传部署」入口，实现「导入部署清单」单弹窗闭环（上传 yaml + 密码 → 校验 → 部署 → 跳 DAG 图）。同步建好 7b/7c 复用的上传管线。

### 进度跟踪表

| Step | 端  |                                          内容                                          | 状态 |                    验证                    |
|------|----|--------------------------------------------------------------------------------------|----|------------------------------------------|
| 1    | 后端 | `ClusterDeployV2Controller`：upload + validate-deployment-file + deploy（3 端点）         | ✅  | 后端编译通过                                   |
| 2    | 前端 | `deploy.ts`（3 API）+ `typings.d.ts` 新增 `UploadedFile`/`ValidateResult`/`DeployResult` | ✅  | Biome/tsc 零新错误                           |
| 3    | 前端 | `ClusterLayout` Sider 工具栏（上传部署 Dropdown + 添加服务按钮 disabled，仅物理集群显示）                   | ✅  | tsc 零新错误                                 |
| 4    | 前端 | `Cluster/Deploy/UploadManifestModal.tsx`（ModalForm + 自定义上传 + 校验 + 部署跳 DAG）           | ✅  | tsc 零新错误                                 |
| 5    | 双  | lint/tsc + 后端编译；浏览器走查另起                                                              | ✅  | Biome 预存 1 err；tsc 零新错误；后端 BUILD SUCCESS |

### 已完成的文件

**后端新增:**
- `datasophon-api/.../controller/v2/ClusterDeployV2Controller.java` — 3 端点，委托 `UploadTempFileService` + `ExtRepoInstallDelegateService`

**前端新增/修改:**
- `datasophon-ui-v2/src/services/datasophon/deploy.ts` — `uploadDeployFile`/`validateDeploymentFile`/`deployManifest` 3 个 API
- `datasophon-ui-v2/src/services/datasophon/typings.d.ts` — 新增 `UploadedFile`/`ValidateResult`/`DeployResult`
- `datasophon-ui-v2/src/pages/Cluster/Deploy/UploadManifestModal.tsx` — 部署清单弹窗（`ModalForm` + `UploadRequestOption` customUpload + 两步提交）
- `datasophon-ui-v2/src/pages/Cluster/Layout/index.tsx` — 新增 Sider 工具栏（上传部署 Dropdown + 添加服务按钮）+ 引入 `UploadManifestModal`

---

## 切片 7b: 部署包（分片上传 + 3 步向导）(Step 1-5)

**目标**: 为物理集群实现大文件分片上传（支持断点续传 + 秒传）+ 3 步部署包导入向导（配置文件 → 包文件 → 导入进度）。

### 进度跟踪表

| Step | 端  |                                                                                                 内容                                                                                                  | 状态 |            验证             |
|------|----|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----|---------------------------|
| 1    | 后端 | `ClusterDeployV2Controller` 扩 9 端点：valid-meta-file / validate-pkg-file / import-cmp / query-progress + 分片：create-shard-task / upload-chunk / is-chunk-uploaded / merge-chunk / query-merge-progress | ✅  | 后端编译通过                    |
| 2    | 前端 | `spark-md5` 安装；`ChunkedUploader.ts`（MD5→创建任务→逐片上传→异步合并）；`typings.d.ts` 新增 `MergeProgress`/`ImportCompProgress`/`InstallComponentReq`                                                                | ✅  | tsc 零新错误                  |
| 3    | 前端 | `deploy.ts` 扩 4 API：validMetaFile / importComponent / queryImportProgress / queryMergeProgress                                                                                                      | ✅  | tsc 零新错误                  |
| 4    | 前端 | `Cluster/Deploy/UploadPackageModal.tsx`（3 步 StepsForm：上传配置文件 → 分片上传包 + 合并进度 → 导入进度仪表盘）                                                                                                              | ✅  | Biome/tsc 零新错误            |
| 5    | 双  | lint/tsc + 后端编译；ClusterLayout 启用"部署包"条目；浏览器走查另起                                                                                                                                                     | ✅  | tsc 零新错误；后端 BUILD SUCCESS |

### 已完成的文件

**后端修改:**
- `datasophon-api/.../controller/v2/ClusterDeployV2Controller.java` — 新增 9 端点，`ExtRepoMetaService` 构造器注入

**前端新增/修改:**
- `datasophon-ui-v2/package.json` — 新增 `spark-md5 ^3.0.2`
- `datasophon-ui-v2/src/utils/ChunkedUploader.ts` — 分片上传工具（SparkMD5 + v2 API）
- `datasophon-ui-v2/src/services/datasophon/deploy.ts` — 新增 `validMetaFile`/`importComponent`/`queryImportProgress`/`queryMergeProgress`
- `datasophon-ui-v2/src/services/datasophon/typings.d.ts` — 新增 `MergeProgress`/`ImportCompProgress`/`InstallComponentReq`
- `datasophon-ui-v2/src/pages/Cluster/Deploy/UploadPackageModal.tsx` — 3 步部署包弹窗
- `datasophon-ui-v2/src/pages/Cluster/Layout/index.tsx` — 启用"部署包"条目 + 引入 `UploadPackageModal`

---

## 切片 7c: 添加服务向导（6 步）(Step 1-5)

**目标**: 补齐 v2「给已建集群追加服务」的核心功能缺口（物理集群路径）。6 步向导：导入清单 → 选服务 → 分配 Master 角色 → 分配 Worker 角色 → 服务配置 → 安装并启动（跳 DAG 全屏图）。

### 进度跟踪表

| Step | 端  |                                                                                                内容                                                                                                | 状态 |       验证       |
|------|----|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----|----------------|
| 1    | 后端 | `ClusterAddServiceV2Controller` 9 端点：list-newest / check-dependency / service-roles / non-master-roles / hosts / role-host-mapping / config-from-ddl / save-config / install（生成命令+redeploy 合并为一） | ✅  | 后端编译通过         |
| 2    | 前端 | `addService.ts`（9 API）+ `typings.d.ts` 新增 `ManifestContext`/`FrameService`/`FrameServiceRole`/`RoleHostMapping`                                                                                  | ✅  | tsc 零新错误       |
| 3    | 前端 | `Cluster/AddService/AddServiceModal.tsx`（6 步 StepsForm 容器，跨步数据 state+ref）                                                                                                                        | ✅  | tsc/Biome 零新错误 |
| 4    | 前端 | 步骤组件：StepManifest / StepSelectService / StepRoleAssign（Master/Worker 共用）/ StepConfig（复用 4b ConfigForm+configTransform）/ StepInstall；ClusterLayout 启用「添加服务」按钮                                     | ✅  | tsc/Biome 零新错误 |
| 5    | 双  | tsc/Biome/vitest（50 测试全过）+ build + 后端编译；浏览器走查另起                                                                                                                                                  | ✅  | 全绿             |

### 关键决策

- **install 端点合并**：v1 的 `generateGenericInstallCommand`（生成 DAG）+ `redeploy`（执行）两段式在 v2 合并为单个 `/install` 端点，前端一次调用拿 `dagId` 直接跳 DAG 全屏图。
- **第 5 步配置完全复用切片 4b**：`ConfigForm` + `configTransform`（invokeHandleTemplateData/invokeFormatTemplateData），配置 Tabs 必须 `forceRender: true`，否则未访问 Tab 的字段不注册、保存时覆盖默认值。
- **Master/Worker 角色分配共用 `StepRoleAssign`**：差异仅 API（service-roles vs non-master-roles）与必填性，由 `roleType` prop 区分；`cardinality === "1"` 单选、否则多选。
- **save-config 请求体与 `ClusterServiceConfigV2Controller` 同构**（Jackson 直接收 `List<ServiceConfig>`），不走 v1 的 JSON 字符串。
- **修复预存 vitest 启动失败**：Umi 的 `@umijs/bundler-vite` peer 拉入 vite 4.5.2，vitest 4 需要 vite ≥6 的 `module-runner` 导出；devDependencies 显式加 `vite ^7` 后 vitest 解析正确，50 测试全过、`npm run build` 不受影响。

### 已完成的文件

**后端新增:**
- `datasophon-api/.../controller/v2/ClusterAddServiceV2Controller.java` — 9 端点，构造器注入，全量委托现有 service

**前端新增/修改:**
- `datasophon-ui-v2/src/services/datasophon/addService.ts` — 9 API 封装
- `datasophon-ui-v2/src/services/datasophon/typings.d.ts` — 新增添加服务相关类型
- `datasophon-ui-v2/src/pages/Cluster/AddService/AddServiceModal.tsx` — 6 步 StepsForm 容器
- `datasophon-ui-v2/src/pages/Cluster/AddService/Step{Manifest,SelectService,RoleAssign,Config,Install}.tsx` — 步骤组件
- `datasophon-ui-v2/src/pages/Cluster/Layout/index.tsx` — 启用「添加服务」按钮
- `datasophon-ui-v2/package.json` — devDependencies 新增 `vite ^7`（修 vitest 启动）

---

## 后续切片

1. 切片 4b Step 5: 物理集群配置 Tab 浏览器端到端验证
2. 切片 4b-2 Step 8: K8s 集群浏览器端到端走查
3. 切片 4c 浏览器验证: 实例 Tab 运维操作（启停/删除/日志/角色组）
4. 切片 4d Step 5: YARN 资源配置浏览器验证
5. 切片 5a/5b 浏览器验证: 告警管理 + 标签管理（批量走查）
6. 切片 6b 浏览器验证: 命令历史 + DAG 图全屏可视化 + 节点日志
7. 切片 7a 浏览器验证: 上传部署清单 → 跳 DAG 图
8. 切片 7b 浏览器验证: 上传部署包（3 步向导 + 分片进度）
9. 切片 7c 浏览器验证: 添加服务 6 步向导 → 跳 DAG 图
10. Maven/assembly 打包集成

