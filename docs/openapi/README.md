# Datasophon REST API 契约总览

> 版本：3.0-SNAPSHOT　　最后更新：2026-06-06

本目录包含 Datasophon 后端（`datasophon-api`）所有 HTTP REST 接口的静态 OpenAPI 3.0 契约。
产物由人工从 Controller 源码读取生成，**无需启动进程即可查阅**，可直接供前端 codegen / mock 工具消费。

---

## 目录结构

```
docs/openapi/
├── README.md               # 本文件（L0 总览）
├── _common.yaml        # 公共组件（Result 壳、实体 schema 占位、securitySchemes）
├── auth.yaml           # 认证（登录/登出）
├── user-rbac.yaml      # 用户与权限
├── cluster.yaml        # 集群管理
├── host.yaml           # 主机管理与安装
├── frame-meta.yaml     # 服务框架元数据
├── service-instance.yaml  # 服务实例管理（最复杂，9 个 controller）
├── command.yaml        # 服务命令 DAG
├── alert.yaml          # 告警管理
├── yarn.yaml           # YARN / 节点标签 / 机架
├── k8s.yaml            # Kubernetes 管理
├── extrepo.yaml        # 扩展仓库与部署
└── log-misc.yaml       # 日志与临时文件
```

每个域 YAML 是自洽的独立 OpenAPI 3.0 文档，通过相对路径 `$ref` 引用 `_common.yaml` 中的公共组件。

---

## 基址与环境

| 环境 | 基址 |
|---|---|
| 生产/开发统一 | `/ddh/api`（`server.servlet.context-path=/ddh` + `path-prefix=/api`） |
| 前端开发代理 | `/dev-mock/api`（见 `datasophon-ui/src/api/baseUrl.ts`） |

所有 `paths` 均写在基址之下（如 `/cluster/list` 对应完整 URL `/ddh/api/cluster/list`）。

---

## 鉴权与 CSRF

### 登录

`POST /ddh/api/login`（form-data）→ 服务端写入两个 Cookie：

| Cookie | HttpOnly | 用途 |
|---|---|---|
| `sessionId` | ✅ | 会话标识，后续请求自动携带 |
| `XSRF-TOKEN` | ❌（JS 可读） | CSRF Token，登录后由前端读取并放入请求头 |

### 后续请求

- **GET/HEAD/OPTIONS**：仅需携带 `sessionId` Cookie，无需 CSRF 头。
- **POST/PUT/DELETE**：必须同时携带：
  - `sessionId` Cookie（浏览器自动携带）
  - 请求头 `X-XSRF-TOKEN: <XSRF-TOKEN Cookie 的值>`

> 实现参考：`datasophon-api/.../interceptor/CsrfTokenInterceptor.java`

---

## 统一返回结构（Result 壳）

所有接口返回 `Content-Type: application/json`，统一壳如下：

```json
{
  "code": 200,
  "msg": "success",
  "data": <业务数据，类型随接口而异>
}
```

> ⚠️ `Result extends HashMap<String, Object>`：除 `code/msg/data` 外，部分接口会附加额外键，
> 例如 `total`（分页列表）、`clusterServiceInstance`（某些 info 接口）等。
> 具体附加键请查阅各接口 `summary/description`。

### 分页响应

分页列表接口在 `data`（数组）之外额外返回 `total`：

```json
{
  "code": 200,
  "msg": "success",
  "data": [...],
  "total": 100
}
```

---

## 常用错误码

| code | 含义 |
|---|---|
| 200 | 成功 |
| 500 | 未知异常（默认服务端错误） |
| 10004 | 用户名为空 |
| 10013 | 用户名或密码错误 |
| 10014 | 创建 Session 失败 |
| 10003 | 用户名已存在 / 主机连接失败（业务码复用，需结合上下文判断） |
| 10009 | 组名重复 |
| 10022 | 存在运行中的角色实例，请先停止 |
| 10125 | IP 地址为空 |

> 完整错误码字典：`datasophon-api/src/main/java/com/datasophon/api/enums/Status.java`

---

## HTTP 方法约定

部分 Controller 使用裸 `@RequestMapping`（不限方法），在契约中按以下规则推断：

| 接口名特征 | 推断方法 | 说明 |
|---|---|---|
| `list / info / get* / query* / configVersionCompare / all / runningClusterList / engineInfo` | `GET` | 只读查询 |
| `save / update / delete / install / start / stop / deploy / generate* / check* / refresh* / cancel* / upload* / create* / merge* / restart* / decommission* / assign*` | `POST` | 写操作 |

> 如有疑问，以 Java 源码注解为准；后端实际不限方法时在 `description` 中已标注。

---

## 入参载体约定

| 方式 | 触发条件 | Content-Type | 前端代码 |
|---|---|---|---|
| query string | `@RequestParam` / 裸简单类型 | — | `axiosGet(url, params)` |
| form-data | `@RequestParam` 在 POST 时 | `application/x-www-form-urlencoded` 或 `multipart/form-data` | `handleParams(data)` 拼 FormData |
| JSON body | `@RequestBody <Entity>` | `application/json` | `JSON.stringify(data)` |
| multipart | `@RequestPart` 或 `MultipartFile` | `multipart/form-data` | FileInput |

---

## 域索引

| 域 YAML | 一句话用途 | 主要 Controllers |
|---|---|---|
| [auth.yaml](openapi/auth.yaml) | 登录、登出 | LoginController |
| [user-rbac.yaml](openapi/user-rbac.yaml) | 用户、角色、集群用户/用户组/管理员授权 | UserInfoController, RoleInfoController, ClusterUserController, ClusterUserGroupController, ClusterRoleUserController |
| [cluster.yaml](openapi/cluster.yaml) | 集群 CRUD、状态、分组、Kerberos | ClusterInfoController, ClusterGroupController, ClusterKerberosController |
| [host.yaml](openapi/host.yaml) | 主机列表及新节点接入安装流程 | ClusterHostController, HostInstallController |
| [frame-meta.yaml](openapi/frame-meta.yaml) | 服务框架与 DDL 元数据管理 | FrameInfoController, FrameServiceController, FrameServiceRoleController |
| [service-instance.yaml](openapi/service-instance.yaml) | 服务实例、角色、配置、安装向导（9 个 controller） | Cluster\*ServiceInstance\*Controller, ServiceInstallController |
| [command.yaml](openapi/command.yaml) | 服务操作命令（安装/启动/停止/重启）DAG | ClusterServiceCommandController, \*Host\*, \*HostCommand\* |
| [alert.yaml](openapi/alert.yaml) | 告警组、告警历史、告警指标、通知组 | AlertGroupController, ClusterAlert\*, NoticeGroupUserController |
| [yarn.yaml](openapi/yarn.yaml) | YARN 队列/调度器/容量、机架、节点标签 | ClusterYarnQueueController, ClusterQueueCapacityController, ClusterRackController, ClusterNodeLabelController |
| [k8s.yaml](openapi/k8s.yaml) | K8s 配置、命名空间、服务实例、Values、框架服务 | K8sCluster\*, K8sServiceInstance\*, FrameK8sService\*, ClusterK8sServiceCommand\* |
| [extrepo.yaml](openapi/extrepo.yaml) | 扩展仓库元数据校验、物理机/K8s/VOS 部署 | ExtRepo\*Controller |
| [log-misc.yaml](openapi/log-misc.yaml) | 服务日志、K8s 日志/事件、临时文件上传/下载 | ScheduleLogController, TempFileController |

---

## 如何消费

### 1. 浏览器预览（推荐）

```bash
# 安装 redocly CLI（仅需一次）
npm install -g @redocly/cli

# 预览单个域
redocly preview-docs docs/api/openapi/cluster.yaml

# 或指定端口
redocly preview-docs docs/api/openapi/service-instance.yaml --port 8088
```

### 2. 合并为单文件（供 codegen / Postman 导入）

```bash
# 合并指定域（处理跨文件 $ref）
redocly bundle docs/api/openapi/cluster.yaml -o /tmp/cluster-bundle.yaml

# 合并全部域（需先创建一个 all.yaml 汇总文件）
# 或逐域合并后用 Postman Collection Runner 分批导入
```

### 3. 生成 TypeScript 类型（前端 codegen）

```bash
# 安装
npm install -g openapi-typescript

# 生成（先 bundle 再生成）
redocly bundle docs/api/openapi/service-instance.yaml -o /tmp/service-instance.yaml
openapi-typescript /tmp/service-instance.yaml -o src/types/service-instance.d.ts
```

### 4. YAML 格式校验

```bash
# 校验所有域文件
npx @redocly/cli@latest lint docs/api/openapi/*.yaml

# 或单个
npx @redocly/cli@latest lint docs/api/openapi/k8s.yaml
```

---

## 关于实体 Schema

契约中的实体 Schema（如 `ClusterServiceInstanceEntity`）仅为**签名级占位**，未展开字段，
并附有 Java 源码路径注释（`additionalProperties: true`）。

**字段明细以 Java 源码为准**，路径统一在 `_common.yaml` 的 `description` 中标注，格式为：
```
datasophon-api/src/main/java/com/datasophon/dao/entity/<ClassName>.java
```

如需字段级别的 codegen，请在 IDE 中直接查阅对应实体类（含 Lombok `@Data`），
或使用 springdoc 开关（`-Dspringdoc.api-docs.enabled=true`）启动进程查看动态生成的完整 schema。
