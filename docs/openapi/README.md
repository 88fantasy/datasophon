# Datasophon REST API 契约说明

本目录保存 12 个按业务域拆分的静态 OpenAPI 3.0 文档和 1 个公共组件文件。它们记录 `3.0-SNAPSHOT` 中传统 `/ddh/api/**` Controller 的核心契约，可在不启动后端时用于查阅、mock 和局部 codegen。

这些 YAML 是人工维护的**静态子集**，不是当前程序全部接口的自动快照：新前端使用的大量 `/ddh/api/v2/**`、OpenTelemetry、血缘、K8s 接管和内部接口未完整收录。需要当前完整接口时，应启用 springdoc 并读取运行中应用生成的动态契约。

## 文件结构

```text
docs/openapi/
├── _common.yaml            # Result、分页、占位实体和安全定义
├── auth.yaml               # 登录与登出
├── user-rbac.yaml          # 用户、角色和授权
├── cluster.yaml            # 集群管理
├── host.yaml               # 主机管理和接入
├── frame-meta.yaml         # 服务框架元数据
├── service-instance.yaml   # 服务、角色、配置和安装
├── command.yaml            # 服务命令与 DAG
├── alert.yaml              # 告警与通知
├── yarn.yaml               # 队列、机架和节点标签
├── k8s.yaml                # 传统 K8s 管理 API
├── extrepo.yaml            # 扩展仓库与部署
└── log-misc.yaml           # 日志和临时文件
```

各域 YAML 通过相对 `$ref` 复用 `_common.yaml`。

## 静态契约范围

| 文档 | 主要内容 | 代表 Controller |
|---|---|---|
| [auth.yaml](./auth.yaml) | 登录、登出 | `LoginController` |
| [user-rbac.yaml](./user-rbac.yaml) | 用户、角色、集群用户与管理员 | `UserInfoController`、`RoleInfoController`、`ClusterUserController` |
| [cluster.yaml](./cluster.yaml) | 集群 CRUD、状态、分组、Kerberos | `ClusterInfoController`、`ClusterGroupController` |
| [host.yaml](./host.yaml) | 主机列表和接入流程 | `ClusterHostController`、`HostInstallController` |
| [frame-meta.yaml](./frame-meta.yaml) | 框架、服务和角色元数据 | `FrameInfoController`、`FrameServiceController` |
| [service-instance.yaml](./service-instance.yaml) | 服务实例、角色、配置和安装向导 | `ClusterServiceInstance*Controller`、`ServiceInstallController` |
| [command.yaml](./command.yaml) | 安装、启动、停止、重启命令 DAG | `ClusterServiceCommand*Controller` |
| [alert.yaml](./alert.yaml) | 告警组、历史、指标和通知组 | `ClusterAlert*Controller`、`NoticeGroupUserController` |
| [yarn.yaml](./yarn.yaml) | YARN 队列、调度、机架和节点标签 | `ClusterYarn*Controller`、`ClusterRackController` |
| [k8s.yaml](./k8s.yaml) | K8s 配置、命名空间、实例和 Values | `K8sCluster*Controller`、`K8sServiceInstance*Controller` |
| [extrepo.yaml](./extrepo.yaml) | 元数据校验及物理/K8s 部署 | `ExtRepo*Controller` |
| [log-misc.yaml](./log-misc.yaml) | 运行日志和分片上传 | `ScheduleLogController`、`TempFileController` |

静态 `paths` 都相对于 `/ddh/api`，例如 `cluster.yaml` 的 `/cluster/list` 对应 `/ddh/api/cluster/list`。

## 当前动态契约

`datasophon-api/src/main/resources/application.yml` 默认关闭 springdoc。启动时显式开启：

```bash
export JAVA_HOME=/path/to/jdk-21
./mvnw -pl datasophon-api spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Dspringdoc.api-docs.enabled=true"
```

应用启动后：

| 地址 | 内容 |
|---|---|
| `http://127.0.0.1:8080/ddh/v3/api-docs` | 默认动态 OpenAPI JSON |
| `http://127.0.0.1:8080/ddh/v3/api-docs/internal` | `/internal/**` 分组 |
| `http://127.0.0.1:8080/ddh/doc.html` | Knife4j 页面 |

动态文档扫描 `com.datasophon.api.controller`，因此包含传统 API、v2 API、可观测、血缘和内部 Controller。实际可见内容仍取决于应用是否成功启动及相关条件 Bean。

## 鉴权与 CSRF

传统静态契约中的业务接口使用 Cookie 会话：

1. `POST /ddh/api/login` 登录后获得 `sessionId` 和可由 JavaScript 读取的 `XSRF-TOKEN` Cookie。
2. GET/HEAD/OPTIONS 请求携带 `sessionId`。
3. POST/PUT/DELETE 等写请求同时携带 `sessionId` 和 `X-XSRF-TOKEN` 请求头。

v2 登录和内部接口可能使用不同的请求/响应结构；应以对应 Controller 和动态 springdoc 为准。内部接口还使用 `X-Internal-Token` 或 `X-Agent-Token`，详见 [内部 API 文档](../internal-api/README.md)。

## 通用响应

传统 API 大多返回 `Result`：

```json
{
  "code": 200,
  "msg": "success",
  "data": {}
}
```

`Result` 继承 `HashMap<String, Object>`，分页或部分详情接口还会增加 `total` 等顶层字段。内部 API 不统一使用该结构。

静态契约中的实体 Schema 多为签名级占位，`additionalProperties: true`。字段事实源是 `datasophon-api` 的 Java DTO、VO 和 Entity；需要字段级 codegen 时优先使用动态契约。

## 预览与校验

以下命令从仓库根目录执行：

```bash
# 预览单个静态域
npx @redocly/cli@latest preview-docs docs/openapi/cluster.yaml

# 校验全部静态 YAML
npx @redocly/cli@latest lint docs/openapi/*.yaml

# 处理相对 $ref，生成单文件
npx @redocly/cli@latest bundle \
  docs/openapi/service-instance.yaml \
  -o /tmp/service-instance.yaml
```

生成 TypeScript 类型示例：

```bash
npx openapi-typescript /tmp/service-instance.yaml \
  -o /tmp/service-instance.d.ts
```

静态 YAML 之间没有 `all.yaml` 聚合入口；如需完整客户端，请直接对运行中的 `/ddh/v3/api-docs` 做 codegen，或明确选择所需业务域逐个生成。

## 维护约定

- 修改 Controller 路径、HTTP 方法、参数或返回结构时，若该接口已在静态 YAML 中收录，应同步对应域文件。
- 新增 v2 接口不代表静态域自动更新；不要把本目录描述为“全部 REST API”。
- `_common.yaml` 的共享 Schema 或 security scheme 变更后，应校验全部域的相对引用。
- API 的最终运行行为以当前源码、配置和动态 springdoc 为准。
