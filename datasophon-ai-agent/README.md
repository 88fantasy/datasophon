# datasophon-ai-agent

Datasophon AI 助手 sidecar 服务。基于 Node 22 + TypeScript + `@anthropic-ai/sdk`，
实现带工具调用（读取集群/主机/服务实例状态）的 Anthropic agent loop，
通过 OpenAI 兼容 SSE 与 `datasophon-api` 通信。

## 快速启动

```bash
npm install
npm run build
npm start
```

## 环境变量

| 变量 | 说明 | 示例 |
|------|------|------|
| `ANTHROPIC_BASE_URL` | 自建 Anthropic 兼容网关地址 | `http://proxy:8000` |
| `ANTHROPIC_API_KEY` | API Key | `sk-xxx` |
| `DATASOPHON_API_URL` | Java API 地址 | `http://localhost:8080/ddh/api` |
| `AGENT_INTERNAL_TOKEN` | 与 `DDH_AI_INTERNAL_TOKEN` 保持一致 | `change-me` |
| `PORT` | 监听端口 | `18090`（默认） |
| `ANTHROPIC_MODEL` | 模型 ID | `claude-opus-4-8`（默认） |

## 接口

`POST /agent/chat` — 接收消息列表，返回 OpenAI 兼容 SSE。
需携带 `X-Agent-Token` 头。

`GET /health` — 健康检查。

## Docker

```bash
# 先构建 TypeScript
npm run build

# 构建镜像
docker build -t datasophon/ai-agent:latest .

# 运行
docker run \
  -e ANTHROPIC_API_KEY=sk-xxx \
  -e ANTHROPIC_BASE_URL=http://your-gateway:port \
  -e DATASOPHON_API_URL=http://api:8080/ddh/api \
  -e AGENT_INTERNAL_TOKEN=your-token \
  -p 18090:18090 \
  datasophon/ai-agent:latest
```

## 架构说明

```
前端 chatbot (Ant Design X, SSE)
       │
       ▼
datasophon-api: POST /ddh/api/v2/chat/completions (SseEmitter)
       │  Java 反代 + 落库
       ▼
datasophon-ai-agent: POST /agent/chat (Express + Anthropic SDK)
       │  tool-calling loop
       ▼
datasophon-api: GET /ddh/api/internal/agent/* (X-Agent-Token)
       │  集群/主机/服务数据
       ▼
  Anthropic 兼容网关 → 模型
```
