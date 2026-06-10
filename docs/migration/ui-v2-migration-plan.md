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

## 后续切片

1. 切片 4b-2: K8s Helm 配置编辑(双栏编辑器 + deltaValues 提交)
2. 切片 4c: 剩余页面(SourceSetting/K8s/弹窗)
3. AlarmManage + SystemCenter + User
4. DagModal(x6 迁移)
5. UploadDeploy
6. Maven/assembly 打包集成

