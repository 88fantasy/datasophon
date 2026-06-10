# datasophon-ai-agent

Datasophon AI 助手 sidecar 服务。基于 Node 22 + TypeScript + `@anthropic-ai/claude-agent-sdk`，
通过 in-process MCP 工具调用集群/主机/服务实例状态，以及内置 Read/Glob/Grep/Bash 文件与命令工具，
通过 OpenAI 兼容 SSE 与 `datasophon-api` 通信。

## 快速启动

```bash
npm install
npm run build
npm start
```

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `ANTHROPIC_BASE_URL` | `https://api.anthropic.com` | 自建 Anthropic 兼容网关地址（**不要带 /v1 后缀**） |
| `ANTHROPIC_AUTH_TOKEN` | （无） | 网关 Bearer token（`Authorization: Bearer <token>`）。**替代原 `ANTHROPIC_API_KEY`** |
| `ANTHROPIC_MODEL` | `claude-sonnet-4-6` | 模型 ID，原样透传网关 |
| `AGENT_WORKDIR` | `/tmp/ddh-agent` | Read / Bash 工具的沙箱根目录，限制文件操作范围 |
| `DATASOPHON_API_URL` | `http://localhost:8080/ddh` | Java 服务根地址（**不含 /api**，内部端点在 `/ddh/internal/agent/**`） |
| `AGENT_INTERNAL_TOKEN` | `change-me` | 与 `DDH_AI_INTERNAL_TOKEN` 保持一致 |
| `PORT` | `18090` | sidecar 监听端口 |

> ⚠️ 与旧版的区别：`ANTHROPIC_API_KEY` 已不再使用，改用 `ANTHROPIC_AUTH_TOKEN`。
> claude-agent-sdk spawn 的子进程走 `Authorization: Bearer` 而非 `x-api-key`。

## 接口

`POST /agent/chat` — 接收消息列表，返回 OpenAI 兼容 SSE。
需携带 `X-Agent-Token: <AGENT_INTERNAL_TOKEN>` 头。

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

`GET /health` — 健康检查。

`GET /debug` — 网关连通性测试（直接 HTTP，无需鉴权，不含敏感数据）。

## 工具列表

### 自定义运维工具（in-process MCP，server 名 `datasophon`）

| 工具名（完整）| 说明 |
|---|---|
| `mcp__datasophon__list_clusters` | 列出所有集群 |
| `mcp__datasophon__list_hosts` | 列出指定集群的主机（需 `cluster_id`）|
| `mcp__datasophon__list_services` | 列出指定集群的服务实例（需 `cluster_id`）|

### 内置工具（已在白名单）

| 工具 | 能力 |
|---|---|
| `Read` | 读取 `AGENT_WORKDIR` 内的文件 |
| `Glob` | 按 glob 模式查找文件 |
| `Grep` | 正则搜索文件内容 |
| `Bash` | 执行 shell 命令（已禁止 `rm *` 和 `sudo *`）|

## Docker

```bash
# 先构建 TypeScript
npm run build

# 构建镜像（Node 22 镜像中 claude 二进制会自动作为可选依赖安装）
docker build -t datasophon/ai-agent:latest .

# 运行
docker run \
  -e ANTHROPIC_AUTH_TOKEN=your-token \
  -e ANTHROPIC_BASE_URL=http://your-gateway:port \
  -e ANTHROPIC_MODEL=claude-sonnet-4-6 \
  -e DATASOPHON_API_URL=http://api:8080/ddh \
  -e AGENT_INTERNAL_TOKEN=your-token \
  -e AGENT_WORKDIR=/var/ddh-agent \
  -p 18090:18090 \
  datasophon/ai-agent:latest
```

## 架构说明

```
前端 chatbot (Ant Design X, OpenAI SSE)
       │
       ▼
datasophon-api: POST /ddh/api/v2/chat/completions (SseEmitter)
       │  Java 反代（落库 + 转发 SSE）
       ▼
datasophon-ai-agent: POST /agent/chat (Express + claude-agent-sdk)
       │  query() — SDK 自动跑 agent loop（spawn claude 子进程）
       ├──── in-process MCP tools (list_clusters / list_hosts / list_services)
       │         │ fetch
       │         ▼
       │     datasophon-api: GET /ddh/internal/agent/** (X-Agent-Token)
       │
       └──── 内置工具 (Read / Glob / Grep / Bash)
                  在 AGENT_WORKDIR 范围内操作本机文件
       │
       ▼
  Anthropic 兼容网关 (ANTHROPIC_BASE_URL) → 模型 (ANTHROPIC_MODEL)
```

## 安全注意事项

- `Bash` 工具仅在 `AGENT_WORKDIR` 目录内执行，生产环境应设为独立低权限目录。
- `rm *` 和 `sudo *` 已通过 `disallowedTools` 作用域规则屏蔽。
- `AGENT_INTERNAL_TOKEN` 生产环境必须使用强随机值（建议 32+ 字节）。
- sidecar 进程建议以非 root 用户运行。
