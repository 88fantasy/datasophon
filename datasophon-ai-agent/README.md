# datasophon-ai-agent

Datasophon 的可选 AI 运维 sidecar。该模块是独立的 Node.js 22 + TypeScript 工程，不在根 Maven reactor 中；它使用 `@anthropic-ai/claude-agent-sdk` 运行 agent loop，通过 in-process MCP 工具读取 Datasophon 集群状态，并以 OpenAI 兼容 SSE 向 `datasophon-api` 返回文本和工具调用事件。

## 数据流

```text
datasophon-ui-v2 /chatbot
        │ POST /ddh/api/v2/chat/completions
        ▼
datasophon-api（会话落库、鉴权、SSE 反向代理）
        │ POST /agent/chat + X-Agent-Token
        ▼
datasophon-ai-agent :18090
        ├── Claude Agent SDK → Anthropic 兼容网关
        ├── in-process MCP → /ddh/internal/agent/**
        └── Read/Glob/Grep/Bash/Edit/WebSearch/WebFetch
```

## 环境要求

- Node.js `>=22`。
- 自建网关使用 Anthropic Messages API 兼容协议。
- `ANTHROPIC_BASE_URL` 不要带 `/v1`；SDK 会自行追加 `/v1/messages`。

## 安装与运行

```bash
cd datasophon-ai-agent
npm install

# 开发模式
npm run dev

# 编译并启动
npm run build
npm start
```

## 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `ANTHROPIC_BASE_URL` | `https://api.anthropic.com` | Anthropic 兼容网关根地址，不含 `/v1` |
| `ANTHROPIC_AUTH_TOKEN` | 无 | 网关 Bearer token；不使用 `ANTHROPIC_API_KEY` |
| `ANTHROPIC_MODEL` | `claude-sonnet-4-6` | 模型 ID，同时由 API 用于记录会话模型 |
| `AGENT_WORKDIR` | `/tmp/ddh-agent` | SDK 子进程的工作目录，不是操作系统级沙箱 |
| `DATASOPHON_API_URL` | `http://localhost:8080/ddh` | Datasophon 根地址，不含 `/api` |
| `AGENT_INTERNAL_TOKEN` | `change-me` | sidecar 入站鉴权及回调 API 鉴权；须与 `DDH_AI_INTERNAL_TOKEN` 一致 |
| `PORT` | `18090` | sidecar HTTP 端口 |

生产环境必须覆盖 `AGENT_INTERNAL_TOKEN` / `DDH_AI_INTERNAL_TOKEN` 的默认值，并通过 Secret 或进程环境注入模型网关凭据。

## HTTP 接口

### `POST /agent/chat`

接收消息并返回 `text/event-stream`。请求必须携带：

```http
X-Agent-Token: <AGENT_INTERNAL_TOKEN>
```

请求体：

```json
{
  "messages": [
    { "role": "user", "content": "列出所有集群" }
  ],
  "conversationId": 123,
  "clusterId": 1,
  "userId": 42
}
```

`messages` 必须是非空数组；其余字段由 API 链路传入，sidecar 当前不直接消费。文本以 OpenAI `choices[].delta.content` 结构发送，工具事件编码在 `<tool-call>...</tool-call>` 中，结束帧为 `data: [DONE]`。

### `GET /health`

返回 `{"status":"ok"}`，不鉴权。

### `GET /debug`

向网关发送一次最小 Messages API 请求，返回连通性状态和最多 300 字符的响应摘要；该接口当前不鉴权，仅适合受限网络中的诊断使用，不应暴露到公网。

## Datasophon MCP 工具

in-process MCP server 名为 `datasophon`，当前 3 个工具都声明为只读：

| 完整工具名 | 后端接口 | 说明 |
|---|---|---|
| `mcp__datasophon__list_clusters` | `GET /ddh/internal/agent/clusters` | 列出集群 |
| `mcp__datasophon__list_hosts` | `GET /ddh/internal/agent/clusters/{id}/hosts` | 列出指定集群主机 |
| `mcp__datasophon__list_services` | `GET /ddh/internal/agent/clusters/{id}/services` | 列出指定集群服务实例 |

回调请求使用 `X-Agent-Token: <AGENT_INTERNAL_TOKEN>`。对应 Java 端配置是 `DDH_AI_INTERNAL_TOKEN`。

## SDK 内置工具

sidecar 以 `permissionMode: "dontAsk"` 无人值守运行，只自动允许以下工具：

| 工具 | 能力 |
|---|---|
| `Read` | 读取文件 |
| `Glob` | 按 glob 查找文件 |
| `Grep` | 搜索文件内容 |
| `Bash` | 执行命令 |
| `Edit` | 修改文件 |
| `WebSearch` | 搜索互联网 |
| `WebFetch` | 读取网页 |

`Bash(rm *)` 和 `Bash(sudo *)` 由 `disallowedTools` 显式拒绝。此规则不是完整命令沙箱，`AGENT_WORKDIR` 也只设置当前工作目录；需要更强隔离时，应结合非 root 用户、容器只读文件系统、网络策略和独立挂载目录。

SDK 的 `settingSources` 为空，不会加载宿主机 `~/.claude` 或项目级 Claude 设置，避免不同机器上的隐式行为差异。

## Docker

镜像只复制编译后的 `dist/`，构建镜像前必须先编译 TypeScript：

```bash
cd datasophon-ai-agent
npm ci
npm run build
docker build -t datasophon/ai-agent:latest .

docker run --rm \
  -e ANTHROPIC_AUTH_TOKEN=your-token \
  -e ANTHROPIC_BASE_URL=http://your-gateway:port \
  -e ANTHROPIC_MODEL=claude-sonnet-4-6 \
  -e DATASOPHON_API_URL=http://api:8080/ddh \
  -e AGENT_INTERNAL_TOKEN=replace-with-random-token \
  -e AGENT_WORKDIR=/var/ddh-agent \
  -p 18090:18090 \
  datasophon/ai-agent:latest
```

生产运行建议使用非 root 用户，仅挂载必要目录，并在 API 与 sidecar 之间限制网络访问。内部 API 细节见 [内部 API 文档](../docs/internal-api/README.md)。
