# Datasophon 内部 API

`/ddh/internal/**` 用于 Datasophon 内部组件、运维脚本和 CI 对接，不属于浏览器业务 API `/ddh/api/**`。这些 Controller 不继承 `ApiController`，因此不会自动添加 `/api` 前缀，也不经过普通登录和 CSRF 拦截器。

当前内部 API 分为两组，鉴权头和响应格式不同，不能混用 Token。

## 元数据管理 API

配置：

```text
datasophon.internal-api.token=${DDH_INTERNAL_API_TOKEN:}
```

请求头：

```http
X-Internal-Token: <DDH_INTERNAL_API_TOKEN>
```

未配置 `DDH_INTERNAL_API_TOKEN` 时默认拒绝所有请求。Token 使用常量时间字节比较。

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/ddh/internal/meta/refresh` | 从当前 `MetaStorage` 全量重载物理和 K8s 元数据 |

示例：

```bash
curl -fsS -X POST \
  -H "X-Internal-Token: ${DDH_INTERNAL_API_TOKEN}" \
  http://127.0.0.1:8080/ddh/internal/meta/refresh
```

响应使用 `InternalResponse`：

```json
{
  "success": true,
  "code": 200,
  "message": null,
  "data": {
    "physicalTotal": 19,
    "physicalLoaded": 19,
    "k8sTotal": 17,
    "k8sLoaded": 17,
    "errors": [],
    "metaStorageAvailable": true
  }
}
```

计数取决于当前启用的 MetaStorage 内容，上例只展示仓库当前元数据规模。`metaStorageAvailable=false` 表示没有可用元数据存储而跳过，不代表 HTTP 调用失败；单个产品失败会写入 `errors`，其余产品继续加载。

## AI Agent 只读 API

配置：

```text
datasophon.ai.internal-token=${DDH_AI_INTERNAL_TOKEN:change-me}
```

请求头：

```http
X-Agent-Token: <DDH_AI_INTERNAL_TOKEN>
```

`datasophon-ai-agent` 使用相同值配置 `AGENT_INTERNAL_TOKEN`。默认值 `change-me` 仅便于本地启动，生产环境必须替换。

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/ddh/internal/agent/clusters` | 列出所有集群 |
| `GET` | `/ddh/internal/agent/clusters/{id}/hosts` | 列出指定集群主机 |
| `GET` | `/ddh/internal/agent/clusters/{id}/services` | 列出指定集群服务实例 |

示例：

```bash
curl -fsS \
  -H "X-Agent-Token: ${DDH_AI_INTERNAL_TOKEN}" \
  http://127.0.0.1:8080/ddh/internal/agent/clusters
```

这 3 个端点当前直接返回实体数组，不使用 `InternalResponse` 信封；鉴权失败时返回 HTTP `401` 和空数组。

## 安全边界

- 内部路径绕过普通 Web 登录和 CSRF，因此部署层必须限制调用来源，不应直接暴露到公网。
- 两类 Token 都应通过 Secret/环境变量注入，不能写入仓库或前端代码。
- 元数据刷新属于写侧管理操作；AI Agent 接口只提供查询，不暴露集群变更能力。
- 开启 springdoc 后，`internal` 分组会收集 `/internal/**`，可通过 `/ddh/v3/api-docs/internal` 查看动态契约。

AI sidecar 的 HTTP 和工具协议见 [datasophon-ai-agent README](../../datasophon-ai-agent/README.md)。
