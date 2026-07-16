# 内部系统对接 API

`/internal/**` 用于 Datasophon 内部系统、运维脚本和 CI 的服务端对接。控制器不继承
`ApiController`，因此实际路径为 `/ddh/internal/**`，而不是 `/ddh/api/**`。

当前约定：

- 登录与 CSRF 拦截器仅覆盖 `/ddh/api/**`，`BasicValidRequestInterceptor` 也显式放行
  `/internal/**`。
- 当前端点不做认证。调用方必须限制在可信网络内；后续可参照 `AgentToolController` 的
  `X-Agent-Token` 模式增加 `X-Internal-Token` 校验。
- 响应统一为 `success`、`code`、`message`、`data` 四字段的 `InternalResponse` 信封。

## 端点

|   方法   |              路径              |                   说明                   |
|--------|------------------------------|----------------------------------------|
| `POST` | `/ddh/internal/meta/refresh` | 从已启用的 MetaStorage 全量重新加载物理与 K8s 服务元数据。 |

刷新成功时，`data` 为 `MetaReloadResult`：`physicalTotal`、`physicalLoaded`、`k8sTotal`、
`k8sLoaded`、`errors` 和 `metaStorageAvailable`。其中 `metaStorageAvailable=false` 表示本次因
没有启用的元数据存储而跳过，不是端点失败；单个服务 DDL 加载失败会记录在 `errors`，其余服务
仍会继续加载。
