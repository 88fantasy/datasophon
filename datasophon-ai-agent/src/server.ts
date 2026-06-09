import express, { Request, Response } from "express";
import Anthropic from "@anthropic-ai/sdk";
import { runAgentLoop } from "./agent.js";

const app = express();
app.use(express.json({ limit: "4mb" }));

const PORT = parseInt(process.env.PORT ?? "18090", 10);
const AGENT_INTERNAL_TOKEN = process.env.AGENT_INTERNAL_TOKEN ?? "change-me";

app.post("/agent/chat", async (req: Request, res: Response) => {
  const token = req.headers["x-agent-token"];
  if (token !== AGENT_INTERNAL_TOKEN) {
    res.status(401).json({ error: "unauthorized" });
    return;
  }

  const { messages } = req.body as {
    messages: Anthropic.MessageParam[];
    conversationId?: number;
    clusterId?: number;
    userId?: number;
  };

  if (!Array.isArray(messages) || messages.length === 0) {
    res.status(400).json({ error: "messages required" });
    return;
  }

  res.setHeader("Content-Type", "text/event-stream");
  res.setHeader("Cache-Control", "no-cache");
  res.setHeader("Connection", "keep-alive");
  res.flushHeaders();

  try {
    await runAgentLoop(messages, res);
  } catch (err) {
    console.error("agent loop error:", err);
    res.write("data: [DONE]\n\n");
    res.end();
  }
});

app.get("/health", (_req, res) => {
  res.json({ status: "ok" });
});

app.listen(PORT, () => {
  console.log(`datasophon-ai-agent listening on :${PORT}`);
});
