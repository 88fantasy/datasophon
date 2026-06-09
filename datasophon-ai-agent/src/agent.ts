import Anthropic from "@anthropic-ai/sdk";
import { Response } from "express";
import { TOOLS, runTool } from "./tools.js";

const MODEL = process.env.ANTHROPIC_MODEL ?? "claude-opus-4-8";
const MAX_TOOL_ROUNDS = 10;

function sendChunk(res: Response, content: string): void {
  const chunk = JSON.stringify({
    choices: [{ delta: { content }, finish_reason: null }],
  });
  res.write(`data: ${chunk}\n\n`);
}

export async function runAgentLoop(
  messages: Anthropic.MessageParam[],
  res: Response
): Promise<void> {
  const client = new Anthropic({
    baseURL: process.env.ANTHROPIC_BASE_URL,
  });

  const currentMessages: Anthropic.MessageParam[] = [...messages];

  for (let round = 0; round < MAX_TOOL_ROUNDS; round++) {
    const stream = client.messages.stream({
      model: MODEL,
      max_tokens: 4096,
      tools: TOOLS,
      messages: currentMessages,
      system:
        "你是 Datasophon 的 AI 运维助手。可以调用工具查询集群、主机、服务实例状态，" +
        "用中文简洁地回答运维相关问题。",
    });

    const response = await stream.finalMessage();

    for (const block of response.content) {
      if (block.type === "text" && block.text) {
        const text = block.text;
        for (let i = 0; i < text.length; i += 50) {
          sendChunk(res, text.slice(i, i + 50));
        }
      }
    }

    currentMessages.push({ role: "assistant", content: response.content });

    const toolUseBlocks = response.content.filter(
      (b): b is Anthropic.ToolUseBlock => b.type === "tool_use"
    );

    if (toolUseBlocks.length === 0 || response.stop_reason === "end_turn") {
      break;
    }

    const toolResults: Anthropic.ToolResultBlockParam[] = await Promise.all(
      toolUseBlocks.map(async (tu) => ({
        type: "tool_result" as const,
        tool_use_id: tu.id,
        content: await runTool(tu.name, tu.input as Record<string, unknown>),
      }))
    );

    currentMessages.push({ role: "user", content: toolResults });
  }

  res.write("data: [DONE]\n\n");
  res.end();
}
