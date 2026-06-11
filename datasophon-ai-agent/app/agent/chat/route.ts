import { runAgentLoop } from "@/lib/agent";
import type { ChatMessage } from "@/lib/types";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

const AGENT_INTERNAL_TOKEN = process.env.AGENT_INTERNAL_TOKEN ?? "change-me";

export async function POST(req: Request) {
  const token = req.headers.get("x-agent-token");
  if (token !== AGENT_INTERNAL_TOKEN) {
    return Response.json({ error: "unauthorized" }, { status: 401 });
  }

  let body: { messages: ChatMessage[]; conversationId?: number; clusterId?: number; userId?: number };
  try {
    body = await req.json();
  } catch {
    return Response.json({ error: "invalid JSON body" }, { status: 400 });
  }

  const { messages } = body;

  if (!Array.isArray(messages) || messages.length === 0) {
    return Response.json({ error: "messages required" }, { status: 400 });
  }

  const { readable, writable } = new TransformStream();
  const writer = writable.getWriter();
  const encoder = new TextEncoder();

  const send = (data: string) => {
    writer.write(encoder.encode(data));
  };

  // Start agent loop in background — Response returns immediately,
  // headers are sent before the loop produces any data.
  (async () => {
    try {
      await runAgentLoop(messages, send);
    } catch (err) {
      console.error("agent loop error:", err);
      send("data: [DONE]\n\n");
    } finally {
      await writer.close();
    }
  })();

  return new Response(readable, {
    headers: {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache, no-transform",
      Connection: "keep-alive",
      "X-Accel-Buffering": "no",
    },
  });
}
