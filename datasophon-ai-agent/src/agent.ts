import { query } from "@anthropic-ai/claude-agent-sdk";
import { Response } from "express";
import { datasophonMcpServer } from "./tools.js";
import type { ChatMessage } from "./types.js";

const MODEL = process.env.ANTHROPIC_MODEL ?? "qwen3.7-plus";
const WORKDIR = process.env.AGENT_WORKDIR ?? "/tmp/ddh-agent";

const RAW_BASE_URL = process.env.ANTHROPIC_BASE_URL;
if (RAW_BASE_URL?.endsWith("/v1")) {
  console.warn(
    `[agent] WARNING: ANTHROPIC_BASE_URL ends with "/v1" (${RAW_BASE_URL}). ` +
      "The SDK appends /v1/messages automatically — this creates a double /v1/v1/messages path. " +
      "Remove the /v1 suffix from ANTHROPIC_BASE_URL."
  );
}
console.log(
  `[agent] baseURL=${RAW_BASE_URL ?? "(default api.anthropic.com)"}  ` +
    `authToken=${process.env.ANTHROPIC_AUTH_TOKEN ? "set" : "NOT SET"}  ` +
    `model=${MODEL}  workdir=${WORKDIR}`
);

const SYSTEM_PROMPT =
  "你是 Datasophon 的 AI 运维助手。可调用工具查询集群/主机/服务实例状态，" +
  "也可读取本机文件、执行命令排查问题。用中文简洁回答运维相关问题。";

// Build shared SDK env override — passes gateway config into the spawned claude subprocess.
// claude-agent-sdk uses ANTHROPIC_AUTH_TOKEN (Authorization: Bearer) for custom gateways,
// which differs from ANTHROPIC_API_KEY (x-api-key) used by the bare @anthropic-ai/sdk.
function sdkEnv(): Record<string, string | undefined> {
  return {
    ...process.env,
    ...(process.env.ANTHROPIC_BASE_URL
      ? { ANTHROPIC_BASE_URL: process.env.ANTHROPIC_BASE_URL }
      : {}),
    ...(process.env.ANTHROPIC_AUTH_TOKEN
      ? { ANTHROPIC_AUTH_TOKEN: process.env.ANTHROPIC_AUTH_TOKEN }
      : {}),
  };
}

function contentToText(content: ChatMessage["content"]): string {
  if (typeof content === "string") return content;
  return content
    .filter((b) => b.type === "text" && b.text)
    .map((b) => b.text ?? "")
    .join("");
}

// Merge the full message history into a single prompt string.
// query() only accepts a string or AsyncIterable, not a MessageParam[].
function buildPrompt(messages: ChatMessage[]): string {
  const hist = messages
    .map(
      (m) =>
        `${m.role === "user" ? "用户" : "助手"}：${contentToText(m.content)}`
    )
    .join("\n\n");
  return `${hist}\n\n请作为运维助手回应用户最新的问题。`;
}

function sendChunk(res: Response, content: string): void {
  const chunk = JSON.stringify({
    choices: [{ delta: { content }, finish_reason: null }],
  });
  res.write(`data: ${chunk}\n\n`);
}

/**
 * Minimal HTTP call to validate gateway connectivity.
 * Uses a direct fetch (not the full SDK subprocess) to keep /debug lightweight.
 */
export async function testConnectivity(): Promise<{
  ok: boolean;
  status?: number;
  body?: string;
  error?: string;
}> {
  const baseUrl = process.env.ANTHROPIC_BASE_URL ?? "https://api.anthropic.com";
  const url = `${baseUrl}/v1/messages`;
  try {
    const res = await fetch(url, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        authorization: `Bearer ${process.env.ANTHROPIC_AUTH_TOKEN ?? ""}`,
        "anthropic-version": "2023-06-01",
      },
      body: JSON.stringify({
        model: MODEL,
        max_tokens: 16,
        messages: [{ role: "user", content: "hi" }],
      }),
    });
    const body = await res.text();
    return { ok: res.ok, status: res.status, body: body.slice(0, 300) };
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : String(err);
    return { ok: false, error: msg };
  }
}

export async function runAgentLoop(
  messages: ChatMessage[],
  res: Response
): Promise<void> {
  try {
    for await (const message of query({
      prompt: buildPrompt(messages),
      options: {
        model: MODEL,
        cwd: WORKDIR,
        // settingSources: [] — do not load ~/.claude or project CLAUDE.md settings;
        // keeps sidecar behaviour predictable across environments.
        settingSources: [],
        systemPrompt: SYSTEM_PROMPT,
        mcpServers: { datasophon: datasophonMcpServer },
        // allowedTools: auto-approve these tools so the agent loop runs unattended.
        // permissionMode "dontAsk" rejects everything NOT in this list — safe for headless servers.
        allowedTools: [
          "Read",
          "Glob",
          "Grep",
          "Bash",
          "Edit",
          "WebSearch",
        ],
        // Scoped disallow rules: keep Bash available but block high-risk patterns.
        disallowedTools: ["Bash(rm *)", "Bash(sudo *)"],
        permissionMode: "dontAsk",
        // includePartialMessages: true streams text deltas so we can forward
        // them as OpenAI-compatible SSE chunks without waiting for the full response.
        includePartialMessages: true,
        env: sdkEnv(),
      },
    })) {
      if (message.type === "stream_event") {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const ev = (message as any).event;
        if (
          ev?.type === "content_block_delta" &&
          ev.delta?.type === "text_delta"
        ) {
          sendChunk(res, ev.delta.text as string);
        }
      }
    }
  } catch (err) {
    console.error("[agent] runAgentLoop error:", err);
  }

  res.write("data: [DONE]\n\n");
  res.end();
}
